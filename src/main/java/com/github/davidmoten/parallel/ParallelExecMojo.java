package com.github.davidmoten.parallel;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
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

    @Parameter(name = "failOnError", defaultValue = "true")
    private boolean failOnError;

    // executable if not set in command
    // if no executable set anywhere then fails
    @Parameter(name = "executable")
    private String executable;

    // working directory if not set in command
    // if not set anywhere then uses ${project.basedir}
    @Parameter(name = "workingDirectory")
    private File workingDirectory;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        poolSize = poolSize == 0 ? Runtime.getRuntime().availableProcessors() : poolSize;
        ExecutorService executor = Executors.newWorkStealingPool(poolSize);
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        for (int i = 0; i < commands.size(); i++) {
            int index = i;
            executor.execute(() -> {
                try {
                    start(commands.get(index), getLog(), executor);
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
            getLog().info("interrupted");
        }
        errors.forEach(e -> getLog().error(e));
        if (!errors.isEmpty()) {
            throw new MojoExecutionException(errors.get(0));
        }
    }

    void start(Command command, Log log, ExecutorService executor) {
        long t = System.currentTimeMillis();
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
        boolean shutdown = true;
        try {
            getLog().info("starting command: " + list);
            ProcessResult result = b.execute();
            if (separateLogs && (showOutput || result.getExitValue() != 0)) {
                log.info("result of command: " + list + ":\n" + result.outputUTF8());
            }
            if (result.getExitValue() != 0) {
                if (failOnError) {
                    throw new RuntimeException(
                            "process failed with code=" + result.getExitValue() + ", command: " + list);
                } else {
                    log.info("process failed with code=" + result.getExitValue() + ", command: " + list);
                }
            }
            shutdown = false;
        } catch (InterruptedException e) {
            // ignore
        } catch (InvalidExitValueException e) {
            throw new RuntimeException("process failed with code=" + e.getExitValue());
        } catch (IOException | TimeoutException e) {
            throw new RuntimeException(e);
        } finally {
            if (shutdown) {
                // stop any queued tasks and interrupt all running tasks
                executor.shutdownNow();
            }
            DecimalFormat df = new DecimalFormat("0.000");
            log.info("finished command: " + list + " in " + df.format((System.currentTimeMillis() - t)/1000.0) + "s");
        }
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
