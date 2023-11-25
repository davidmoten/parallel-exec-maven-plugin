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
                
                <!-- optional, if false then logs may be interleaved from the different parallel -->
                <!-- commands. If true then a command log will be kept in memory and -->
                <!-- displayed on completion of command if `showOutput` is true or if -->
                <!-- an error occurs. Default value is false. -->
                <separateLogs>true</separateLogs>
                
                <!-- optional, show process stdout and stderr output in console. If process has an -->
                <!-- error then will show logs but only if separateLogs is true. Default value is false. -->
                <showOutput>false</showOutput>
                
                <!-- optional, this working directory will be used for all commands that -->
                <!-- do not have a working directory specified. If no working directory -->
                <!-- specified then ${project.basedir} is used. -->
                <workingDirectory>${project.basedir}</workingDirectory>

                <!-- these commands will be run in parallel according to poolSize -->                
                <commands>
                    <command>
                        <executable>${project.basedir}/generate.sh</executable>
                        <workingDirectory>${project.basedir}</workingDirectory>                        
                        <arguments>
                            <argument>src/main/openapi/test_primary/openapi.yaml</argument>
                            <argument>1</argument>
                        </arguments>
                    </command>
                    <command>
                        <arguments>
                            <argument>src/main/openapi/simple/openapi.yaml</argument>
                            <argument>2</argument>
                        </arguments>
                    </command>
                </commands>
            </configuration>
        <execution>
    </executions>
```


