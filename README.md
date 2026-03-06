# SPouT

This repository is the home of SPouT[^1].


SPouT is implemented as an extension to the espesso bytecode interpreter part of the [GraalVM](https://github.com/oracle/graal) project.

As a result, this repository is a fork of the original GraalVM. Please visit: https://github.com/oracle/graal to find the main GraalVM source code.



## Repository Structure

This source repository is the main repository for GraalVM and includes the following components:

Directory | Description
------------ | -------------
[`.github/`](.github/) | Configuration files for GitHub issues, workflows, ….
[`compiler/`](compiler/) | [Graal compiler][reference-compiler], a modern, versatile compiler written in Java.
[`espresso/`](espresso/) | [Espresso][java-on-truffle], a meta-circular Java bytecode interpreter for the GraalVM. SPouT is based on espresso and all our changes are in this folder.
[`java-benchmarks/`](java-benchmarks/) | Java benchmarks.
[`regex/`](regex/) | TRegex, a regular expression engine for other GraalVM languages.
[`sdk/`](sdk/) | [GraalVM SDK][graalvm-sdk], long-term supported APIs of GraalVM.
[`substratevm/`](substratevm/) | Framework for ahead-of-time (AOT) compilation with [Native Image][native-image].
[`sulong/`](sulong/) | [Sulong][reference-sulong], an engine for running LLVM bitcode on GraalVM.
[`tools/`](tools/) | Tools for GraalVM languages implemented with the instrumentation framework.
[`truffle/`](truffle/) | GraalVM's [language implementation framework][truffle] for creating languages and tools.
[`vm/`](vm/) | Components for building GraalVM distributions.
[`wasm/`](wasm/) | [GraalWasm][reference-graalwasm], an engine for running WebAssembly programs on GraalVM.


## License

GraalVM Community Edition is open source and distributed under [version 2 of the GNU General Public License with the “Classpath” Exception](LICENSE), which are the same terms as for Java. The licenses of the individual GraalVM components are generally derivative of the license of a particular language (see the table below). GraalVM Community is free to use for any purpose - no strings attached.

Component(s) | License
------------ | -------------
[Espresso](espresso/LICENSE) | GPL 2
[GraalVM Compiler](compiler/LICENSE.md), [SubstrateVM](substratevm/LICENSE), [Tools](tools/LICENSE), [VM](vm/LICENSE_GRAALVM_CE) | GPL 2 with Classpath Exception
[GraalVM SDK](sdk/LICENSE.md), [GraalWasm](wasm/LICENSE), [Truffle Framework](truffle/LICENSE.md), [TRegex](regex/LICENSE.md) | Universal Permissive License
[Sulong](sulong/LICENSE) | 3-clause BSD

## Paper
[^1]:
	Mues, Malte, Falk Howar, and Simon Dierl. "SPouT: symbolic path recording during testing-a concolic executor for the JVM." International Conference on Software Engineering and Formal Methods. Cham: Springer International Publishing, 2022. doi: 10.1007/978-3-031-17108-6_6
