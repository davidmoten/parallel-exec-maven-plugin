package com.github.davidmoten.parallel;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.util.ArrayList;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

import com.github.davidmoten.guavamini.Lists;
import com.github.davidmoten.parallel.ParallelExecMojo.Command;

public class ParallelExecMojoTest {

    @Test
    public void testDoesNotThrow() throws MojoExecutionException {
        ParallelExecMojo p = new ParallelExecMojo();
        p.failOnError = true;
        p.showOutput = true;
        p.timeoutSeconds = 30;
        p.commands = new ArrayList<Command>();
        Command c = new Command();
        c.executable = "java";
        c.arguments = Lists.of("-version");
        c.workingDirectory = new File(".");
        p.commands.add(c);
        p.execute();
    }

    @Test
    public void testThrows() throws MojoExecutionException {
        assertThrows(MojoExecutionException.class, () -> {
            ParallelExecMojo p = new ParallelExecMojo();
            p.failOnError = true;
            p.showOutput = true;
            p.timeoutSeconds = 30;
            p.commands = new ArrayList<Command>();
            Command c = new Command();
            c.executable = "commanddoesnotexist";
            c.workingDirectory = new File(".");
            p.commands.add(c);
            p.execute();
        });
    }

}
