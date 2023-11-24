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
        poolSize = poolSize == 0 ? Runtime.getRuntime().availableProcessors() : poolSize;
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        List<File> logs = new ArrayList<>();
        for (int i = 0; i < commands.size(); i++) {
            int index = i;
            logs.add(new File("target" + File.separator + "command" + index + ".log"));
            executor.execute(() -> {
                try {
                    start(commands.get(index), getLog(), logs.get(index));
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

    void start(Command command, Log log, File output) {
        List<String> list = new ArrayList<>();
        if (command.executable != null) {
            list.add(command.executable);
        } else {
            list.add(Optional.ofNullable(executable)
                    .orElseThrow(() -> new IllegalArgumentException("must specify `executable` parameter")));
        }
        list.addAll(command.arguments);
        final File workingDir;
        if (command.workingDirectory == null) {
            workingDir = project.getBasedir();
        } else {
            workingDir = new File(command.workingDirectory);
        }
        ProcessBuilder b = new ProcessBuilder(list) //
                .directory(workingDir);
        if (separateLogs) {
            b = b.redirectErrorStream(true) //
                    .redirectOutput(output);
        } else {
            b = b.inheritIO();
        }
        try {
            Process process = b.start();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new RuntimeException("process timed out, process was sent destroy, command: " + list);
            }
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

    public static final class Command {

        @Parameter(name = "executable")
        String executable;

        @Parameter(name = "arguments")
        List<String> arguments;

        @Parameter(name = "workingDirectory")
        String workingDirectory;

    }

}
