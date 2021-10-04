
# SPouT

SPouT (Symbolic Path Recording During Testing) is a concolic execution engine
for the Espresso JVM implementation.

This project is a fork of [graal/espresso](https://github.com/oracle/graal/)

## Installation

- You need [mx](https://github.com/graalvm/mx) on the path
- You need [GraalVM](https://github.com/graalvm/graalvm-ce-builds/releases). Tested to work with 21.1.0 and 21.2.0
- Can only run on Linux currently

## Building

```bash
$ cd graal/espresso 
$ export JAVA_HOME=[PATH-TO-GRAALVM]
$ mx --env native-ce build 
```

## Running

- Concolic Values are seeded using ```-Dconcolic.ints=[comma separated list of ints]```
- Espresso native image is built as ```java```with truffle extension into a folder in ```graal/sdk/mxbuild``` e.g. ```graal/sdk/mxbuild/linux-amd64/GRAALVM_ESPRESSO_NATIVE_CE_JAVA11/graalvm-espresso-native-ce-java11-21.2.0``` 
- Concolic value are seeded as return values of calls to methods of the ```tools.aqua.concolic.Verifier``` in the SUT code.

```bash
$ [path-to-native-espresso]/bin/java -truffle ...
```


## Output

SpouT records symbolic constraints during execution

## Example

