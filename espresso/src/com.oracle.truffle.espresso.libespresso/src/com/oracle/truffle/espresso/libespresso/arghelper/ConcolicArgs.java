package com.oracle.truffle.espresso.libespresso.arghelper;

import org.graalvm.options.OptionCategory;

public class ConcolicArgs {

    private ArgumentsHandler handler;

    ConcolicArgs(ArgumentsHandler handler) {
        this.handler = handler;
    }

    void printConcolicHelp(OptionCategory optionCategory) {
        handler.printRaw("");
        handler.printRaw("Concolic Execution Usage:");
        handler.printRaw("");
        handler.printRaw("Concolic execution will record symbolic constraints for concrete executions.");
        handler.printRaw("Symbolic constraints are recorded for symbolically annotated data values.");
        handler.printRaw("Return values of the methods of the class tools.aqua.concolic.Verifier ");
        handler.printRaw("are annotated symbolically.");
        handler.printRaw("");
        handler.printRaw("Concolic Values:");
        handler.printRaw("");
        handler.printLauncherOption("-Dconcolic.bools", "comma separated list of return values for Verifier.nondetBoolean()");
        handler.printLauncherOption("-Dconcolic.bytes", "comma separated list of return values for Verifier.nondetByte()");
        handler.printLauncherOption("-Dconcolic.chars", "comma separated list of return values for Verifier.nondetChar()");
        handler.printLauncherOption("-Dconcolic.shorts", "comma separated list of return values for Verifier.nondetShort()");
        handler.printLauncherOption("-Dconcolic.ints", "comma separated list of return values for Verifier.nondetInt()");
        handler.printLauncherOption("-Dconcolic.longs", "comma separated list of return values for Verifier.nondetLong()");
        handler.printLauncherOption("-Dconcolic.floats", "comma separated list of return values for Verifier.nondetFloat()");
        handler.printLauncherOption("-Dconcolic.doubles", "comma separated list of return values for Verifier.nondetDouble()");
        handler.printLauncherOption("-Dconcolic.strings", "comma separated list of return values for Verifier.nondetString()");
        handler.printRaw("");
        handler.printRaw("comma separated values may be base64-encloded individually. This has to indicated ");
        handler.printRaw("by prepending a list of values with [64]. E.g. -Dconcolic-ints=[b64]...");
        handler.printRaw("");
        handler.printRaw("Assumptions can be placed in analyzed code by using the Verifier.assume(booleab condition) method");
        handler.printRaw("");
    }

}
