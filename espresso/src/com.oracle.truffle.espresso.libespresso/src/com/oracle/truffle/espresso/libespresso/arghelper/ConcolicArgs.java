/*
 * Copyright (c) 2021 Automated Quality Assurance Group, TU Dortmund University.
 * All rights reserved. DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE
 * HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact the Automated Quality Assurance Group, TU Dortmund University
 * or visit https://aqua.engineering if you need additional information or have any
 * questions.
 */

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
