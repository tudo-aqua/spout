
# SPouT

SPouT (Symbolic Path Recording During Testing) is a concolic execution engine
for the Espresso JVM implementation.

In an espresso maker, the spout directs the flow of the outpouring espresso, 
which was the inspiration for the name of the tool as well as for the logo.
The image used in the logo is taken from [here](https://www.flickr.com/photos/john_mcclumpha/4158407875/in/photolist-o1Tksq-nLrps7-oe9B3a-pEAxnm-nY8MUm-CecoHg-nwkFMh-oceZVB-rggX2c-o3NPui-HNLwMF-od4zL4-o3PF81-2jdww3n-q7wA1g-Cecm2K-qoN6br-9fnNMP-q7pzBo-5TsCsJ-CMGuhP-psdon4-kK4b19-q7yk58-nTtKGq-2kFipfF-GqnAAF-7ksWfZ-7kwQjS-7kwQvJ-7ksVTR-7ksVoV-7ksVMv-7kwQAJ-7ksVJx-cfEqN5-7pVcap-qmFWnd-qBpdJf-Ed3Liw-67ZdSn-7UMuv-9RaDD4-2fwfTAc-GvkSyf-5yMhh4-E7mqpg-DEeWHN-E7mgux-ceMe6f)
and is work by [john_mcclumpha](https://www.flickr.com/photos/john_mcclumpha/),
licensed under the [CC BY 2.0](https://creativecommons.org/licenses/by/2.0/) license.

This project is a fork of [graal/espresso](https://github.com/oracle/graal/)

## Installation

- You need [mx](https://github.com/graalvm/mx) on the path
- You need [GraalVM](https://github.com/graalvm/graalvm-ce-builds/releases). Tested to work with 21.1.0 and 21.2.0
- Runs on Linux and MACOS currently (for MACOS use GRaalVM 21.2.0)

## Building

- After cloning this project, build an Espresso native image:
    ```bash
    $ cd graal/espresso 
    $ export JAVA_HOME=[PATH-TO-GRAALVM]
    $ mx --env native-ce build 
    ```
- Espresso native image is built as ```java```with truffle extension into a folder in ```graal/sdk/mxbuild``` e.g. ```graal/sdk/mxbuild/linux-amd64/GRAALVM_ESPRESSO_NATIVE_CE_JAVA11/graalvm-espresso-native-ce-java11-21.2.0``` 


## Running

- Concolic execution will record symbolic constraints for concrete executions.
- Symbolic constraints are recorded for symbolically annotated data values.
- Return values of the methods of the class ```tools.aqua.concolic.Verifier```
  (from here: https://github.com/tudo-aqua/verifier-stub)
  are annotated symbolically.
- Assumptions can be placed in analyzed code by using the ```Verifier.assume(boolean condition)``` method
- You have to run ```java``` with the ```-truffle``` option to enable concolic 
  execution.
- You can set concrete values for concolic values through Java arguments: 
    ```
    -Dconcolic.bools=[comma separated list of Boolean values]
    -Dconcolic.bytes=[comma separated list of byte values]
    -Dconcolic.chars=[comma separated list of char values]
    -Dconcolic.shorts=[comma separated list of short values]
    -Dconcolic.ints=[comma separated list of int values]
    -Dconcolic.longs=[comma separated list of long values]
    -Dconcolic.floats=[comma separated list of float values]
    -Dconcolic.doubles=[comma separated list of double values]
    -Dconcolic.strings=[comma separated list of string values]
    ```
- Comma separated values may be base64-encloded individually. This has to indicated 
  by prepending a list of values with [64]. E.g. ```-Dconcolic-ints=[b64]...```


## Output

SpouT records symbolic constraints during execution and outputs the constraints 
after the execution of the analyzed program ends in the following format:

```
trace ::= (decision|declaration|error|abort|assumption)* 
    "[ENDOFTRACE]\n"

declaration ::= "[DECLARE] " <SMTLib variable declaration>  "\n"

decision ::= "[DECISION] "  <SMTLib assertion> 
    " // branchCount=" <int> ", branchId=" <int> "\n"

error ::= "[ERROR] " <cause, e.g., exception class>  "\n"

abort ::= "[ABORT] " <causen>  "\n"

assumption ::= "[ASSUMPTION] " <SMTLib assertion> 
    " // sat=" <true|false>  "\n"
```

## Example

Assume the following Java class:

```java
import tools.aqua.concolic.Verifier;

public class Main {

    public static void main(String[] args) {
        int i = Verifier.nondetInt();
        int[] arr = new int[10];
        arr[2] = i;
        if (40 > arr[2]) {
            assert false;
        }
    }
}

```

We execute this class concolically like this: 

```bash
$ [path-to-native-espresso]/bin/java -truffle -Dconcolic.ints=10 -ea -cp [classpath] Main
```

SPouT produces the following output:

```
======================== START PATH [BEGIN].
Seeded Bool Values: []
Seeded Byte Values: []
Seeded Char Values: []
Seeded Short Values: []
Seeded Int Values: [10]
Seeded Long Values: []
Seeded Float Values: []
Seeded Double Values: []
Seeded String Values: []
======================== START PATH [END].
Exception in thread "main" java.lang.AssertionError
	at Main.main(Main.java:11)
======================== END PATH [BEGIN].
[DECLARE] (declare-fun __int_0 () (_ BitVec 32))
[DECISION] (assert (bvslt __int_0 #x00000028)) // branchCount=2, branchId=0
[ERROR] java/lang/AssertionError
======================== END PATH [END].
[ENDOFTRACE]
```




