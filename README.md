# parallel-exec-maven-plugin
Runs external processes in parallel using a work-stealing pool.

Here's a kitchen sink example:

```xml
<plugin>
    <groupId>com.github.davidmoten</groupId>
    <artifactId>parallel-exec-maven-plugin</artifactId>
    <version>VERSION_HERE</version>
    <executions>
        <execution>
            <id>gen</id>
            <phase>generate-sources</phase>
            <goals>
                <goal>exec</goal>
            </goals>
            <configuration>
                <!-- optional, default is 0 which means use all available processors -->
                <!-- set to 1 to get serialized execution of commands -->
                <poolSize>0</poolSize> 
                
                <!-- optional, all commands have this timeout applied, default is 120s -->
                <timeoutSeconds>120</timeoutSeconds>
                
                <!-- optional, this executable will be used for all commands that do -->
                <!-- not have an executable specified -->
                <executable>${project.basedir}/generate.sh</executable>
                
                <!-- logs are always written to target/process-#.log for each process -->
                <!-- if showOutput is true then output is written to stdout and stderr -->
                <!-- if showOutput is true and separateLogs is true then output is not -->
                <!-- interleaved with other process output but is only written to the -->
                <!-- console when a process finishes -->
                <!-- if separateLogs is true and an error occurs then the log is written -->
                <!-- to the console regardless of the value of showOutput -->

                <!-- optional, default value is false -->
                <separateLogs>false</separateLogs>
             
                <!-- optional, default value is false. -->
                <showOutput>false</showOutput>
                
                <!-- optional, this working directory will be used for all commands that -->
                <!-- do not have a working directory specified. If no working directory -->
                <!-- specified then ${project.basedir} is used. -->
                <workingDirectory>${project.basedir}</workingDirectory>

                <!-- optional, if any command returns a non-zero exit code then the maven build -->
                <!-- fails. Any running tasks will be sent an interrupt and queued tasks -->
                <!-- will be removed from the queue. Default is true -->
                <failOnError>true</failOnError>

                <!-- these commands will be run in parallel according to poolSize -->                
                <commands>
                    <command>
                        <executable>${project.basedir}/generate.sh</executable>
                        <workingDirectory>${project.basedir}</workingDirectory>                        
                        <arguments>
                            <argument>src/main/openapi/openapi.yaml</argument>
                            <argument>1</argument>
                        </arguments>
                    </command>
                    <command>
                        <!-- shorthand for arguments is comma delimited -->
                        <arguments>src/main/openapi/openapi2.yaml,2</arguments>
                    </command>
                </commands>
            </configuration>
        <execution>
    </executions>
</plugin>
```


