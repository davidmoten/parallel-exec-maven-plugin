package com.github.davidmoten.parallel;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
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
    int poolSize;

    @Parameter(name = "timeoutSeconds", defaultValue = "30")
    long timeoutSeconds;

    @Parameter(name = "separateLogs", defaultValue = "false")
    boolean separateLogs;

    @Parameter(name = "commands", required = true)
    List<Command> commands;

    @Parameter(name = "showOutput", defaultValue = "true")
    boolean showOutput;

    @Parameter(name = "failOnError", defaultValue = "true")
    boolean failOnError;

    // executable if not set in command
    // if no executable set anywhere then fails
    @Parameter(name = "executable")
    String executable;

    // working directory if not set in command
    // if not set anywhere then uses ${project.basedir}
    @Parameter(name = "workingDirectory")
    File workingDirectory;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        long t = System.currentTimeMillis();
        poolSize = poolSize == 0 ? Runtime.getRuntime().availableProcessors() : poolSize;
        ExecutorService executor = Executors.newWorkStealingPool(poolSize);
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        for (int i = 1; i <= commands.size(); i++) {
            int index = i;
            executor.submit(() -> {
                try {
                    File output = new File(targetDirectory(), "process-" + index + ".log");
                    start(commands.get(index - 1), getLog(), executor, output);
                } catch (Throwable e) {
                    errors.add(e);
                }
            });
        }
        getLog().info("awaiting finish of " + commands.size() + " commands");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                errors.add(new TimeoutException("task group execution timed out after " + timeoutSeconds + "s"));
            };
        } catch (InterruptedException e) {
            getLog().info("interrupted");
        }
        errors.forEach(e -> getLog().error(e));
        if (!errors.isEmpty()) {
            throw new MojoExecutionException(errors.get(0));
        }
        getLog().info("mojo finished execution after "
                + new DecimalFormat("0.000").format((System.currentTimeMillis() - t) / 1000.0) + "s");
    }

    private File targetDirectory() {
        if (project == null) {
            return new File("target");
        } else {
            return new File(project.getBuild().getDirectory());
        }
    }

    void start(Command command, Log log, ExecutorService executor, File outputFile)
            throws InvalidExitValueException, IOException, TimeoutException {
        long t = System.currentTimeMillis();
        List<String> list = new ArrayList<>();
        if (command.executable != null) {
            list.add(command.executable);
        } else {
            list.add(Optional.ofNullable(executable)
                    .orElseThrow(() -> new IllegalArgumentException("must specify `executable` parameter")));
        }
        list.addAll(command.arguments == null ? Collections.emptyList() : command.arguments);
        final File workingDir;
        if (command.workingDirectory != null) {
            workingDir = command.workingDirectory;
        } else if (this.workingDirectory != null) {
            workingDir = this.workingDirectory;
        } else {
            workingDir = project.getBasedir();
        }
        outputFile.delete();
        outputFile.getParentFile().mkdirs();
        outputFile.createNewFile();
        boolean shutdown = true;
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile))) {
            ProcessExecutor b = new ProcessExecutor().command(list) //
                    .directory(workingDir) //
                    .timeout(timeoutSeconds, TimeUnit.SECONDS) //
                    .redirectOutput(out);
            if (showOutput && !separateLogs) {
                b = b //
                        .redirectOutputAlsoTo(System.out) //
                        .redirectErrorAlsoTo(System.err);
            }
            getLog().info("starting command: " + list);
            ProcessResult result = b.execute();
            if (result.hasOutput()) {
                String logs = result.outputUTF8();
                Files.write(outputFile.toPath(), logs.getBytes(StandardCharsets.UTF_8));
            }
            if (separateLogs && (showOutput || result.getExitValue() != 0)) {
                out.close();
                // ensure that process logs don't interleave
                synchronized (this) {
                    log.info("result of command: " + list + ":\n");
                    Files.lines(outputFile.toPath()) //
                            .forEach(log::info);
                }
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
            log.info("interrupted");
        } finally {
            if (shutdown) {
                // stop any queued tasks and interrupt all running tasks
                executor.shutdownNow();
            }
            DecimalFormat df = new DecimalFormat("0.000");
            log.info("finished command: " + list + " in " + df.format((System.currentTimeMillis() - t) / 1000.0) + "s");
        }
    }

    public static final class Command {

        @Parameter(name = "executable")
        String executable;

        @Parameter(name = "arguments")
        List<String> arguments;

        @Parameter(name = "workingDirectory")
        File workingDirectory;

    }

}
