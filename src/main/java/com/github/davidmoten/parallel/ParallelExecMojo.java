package com.github.davidmoten.parallel;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.zeroturnaround.exec.InvalidExitValueException;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

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

    @Parameter(name = "showOutput", defaultValue = "true")
    private boolean showOutput;

    // default for executable if not set in command
    @Parameter(name = "executable")
    String executable;

    // default for working directory if not set in command
    @Parameter(name = "workingDirectory")
    File workingDirectory;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        poolSize = poolSize == 0 ? Runtime.getRuntime().availableProcessors() : poolSize;
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        for (int i = 0; i < commands.size(); i++) {
            int index = i;
            executor.execute(() -> {
                try {
                    start(commands.get(index), getLog());
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
        errors.forEach(e -> getLog().error(e));
        if (!errors.isEmpty()) {
            throw new MojoExecutionException(errors.get(0));
        }
    }

    void start(Command command, Log log) {
        List<String> list = new ArrayList<>();
        if (command.executable != null) {
            list.add(command.executable);
        } else {
            list.add(Optional.ofNullable(executable)
                    .orElseThrow(() -> new IllegalArgumentException("must specify `executable` parameter")));
        }
        list.addAll(command.arguments);
        final File workingDir;
        if (command.workingDirectory != null) {
            workingDir = new File(command.workingDirectory);
        } else if (this.workingDirectory != null) {
            workingDir = this.workingDirectory;
        } else {
            workingDir = project.getBasedir();
        }
        ProcessExecutor b = new ProcessExecutor().command(list) //
                .directory(workingDir);

        if (separateLogs) {
            b = b //
                    .readOutput(true) //
                    .timeout(timeoutSeconds, TimeUnit.SECONDS);
        } else if (showOutput) {
            b = b //
                    .redirectOutput(System.out) //
                    .redirectError(System.err);
        }
        try {
            ProcessResult result = b.execute();
            if (separateLogs && (showOutput || result.getExitValue() != 0)) {
                log.info("result of command: " + list + ":\n" + result.outputUTF8());
            }
            if (result.getExitValue() != 0) {
                throw new RuntimeException("process failed with code=" + result.getExitValue() + ", command: " + list);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            // ignore
        } catch (InvalidExitValueException e) {
            throw new RuntimeException("process failed with code=" + e.getExitValue());
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
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
