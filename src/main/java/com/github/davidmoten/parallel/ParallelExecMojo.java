package com.github.davidmoten.parallel;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "exec", threadSafe = true)
public final class ParallelExecMojo extends AbstractMojo {

    @Parameter(name = "poolSize", defaultValue = "0")
    private int poolSize;

    @Parameter(name = "timeoutSeconds", defaultValue = "30")
    private long timeoutSeconds;

    @Parameter(name = "separateLogs", defaultValue = "false")
    private boolean separateLogs;

    @Parameter(name = "commands")
    private List<Command> commands;

    @Parameter(name = "executable")
    String executable;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        Optional<String> defaultExecutable = Optional.ofNullable(executable);
        poolSize = poolSize == 0 ? Runtime.getRuntime().availableProcessors() : poolSize;
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        List<File> logs = new ArrayList<>();
        for (int i = 0; i < commands.size(); i++) {
            int index = i;
            logs.add(new File("target" + File.separator + "command" + index + ".log"));
            executor.execute(() -> {
                try {
                    commands.get(index) //
                            .start(getLog(), TimeUnit.SECONDS.toMillis(timeoutSeconds), defaultExecutable,
                                    project.getBasedir(), logs.get(index));
                } catch (Throwable e) {
                    errors.add(e);
                }
            });
        }
        getLog().info("awaiting finish of " + commands.size() + " commands");
        executor.shutdown();
        try {
            executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (separateLogs) {
            for (File log : logs) {
                getLog().info("************** " + log.getPath());
                try {
                    Files.readAllLines(log.toPath()) //
                            .forEach(System.out::println);
                } catch (IOException e) {
                    throw new MojoExecutionException(e);
                }
            }
        }
        errors.forEach(e -> getLog().error(e));
        if (!errors.isEmpty()) {
            throw new MojoExecutionException(errors.get(0));
        }
    }

    public static final class Command {
        
        @Parameter(name = "executable")
        String executable;

        @Parameter(name = "arguments")
        List<String> arguments;

        @Parameter(name = "workingDirectory")
        String workingDirectory;

        void start(Log log, long timeoutMs, Optional<String> defaultExecutable, File defaultWorkingDirectory,
                File output) {
            List<String> list = new ArrayList<>();
            if (executable != null) {
                list.add(executable);
            } else {
                list.add(defaultExecutable
                        .orElseThrow(() -> new IllegalArgumentException("must specify `executable` parameter")));
            }
            list.addAll(arguments);
            final File workingDir;
            if (workingDirectory == null) {
                workingDir = defaultWorkingDirectory;
            } else {
                workingDir = new File(workingDirectory);
            }
            ProcessBuilder b = new ProcessBuilder(list) //
                    .directory(workingDir) //
                    .inheritIO();
            try {
                Process process = b.start();
                process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
                if (process.exitValue() != 0) {
                    throw new RuntimeException("process failed with code=" + process.exitValue());
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                // ignore
            }
            log.info("finished command: " + list);
        }
    }

}
