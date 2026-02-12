#!/bin/sh

echo "Working directory: $(pwd)"
# compile classes
#./mxbuild/darwin-aarch64/ESPRESSO_NATIVE_STANDALONE/bin/javac -cp ../../verifier-stub/target/verifier-stub-1.0.jar:../../dse/src/test/resources/examples ../../dse/src/test/resources/examples/A.java # Normale Java25 zum kompilieren kann auch verwendet werden

# perform concrete execution
# execution with null
./mxbuild/darwin-aarch64/ESPRESSO_NATIVE_STANDALONE/bin/java -truffle -ea -Dconcolic.execution=true -cp ../../dse/src/test/resources/examples:../../verifier-stub/target/verifier-stub-1.0.jar Example1
# execution with a given constructor with no arguments
./mxbuild/darwin-aarch64/ESPRESSO_NATIVE_STANDALONE/bin/java -truffle -ea -Dconcolic.execution=true -Dconcolic.constructors="Ljava/lang/Integer;|(I)V|1|11" -Dconcolic.constructorCounts=11 -Dconcolic.constructorIds=1 -cp ../../dse/src/test/resources/examples:../../verifier-stub/target/verifier-stub-1.0.jar Example1
# execution with a given constructor with arguments
./mxbuild/darwin-aarch64/ESPRESSO_NATIVE_STANDALONE/bin/java -truffle -ea -Dconcolic.execution=true -Dconcolic.constructors="LA;|(IILSub;)V|9|11" -Dconcolic.constructorCounts=11 -Dconcolic.constructorIds=9 -cp ../../dse/src/test/resources/examples:../../verifier-stub/target/verifier-stub-1.0.jar Example1

