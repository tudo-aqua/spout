/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.bisect.test;

import org.graalvm.bisect.parser.args.ArgumentParser;
import org.graalvm.bisect.parser.args.DoubleArgument;
import org.graalvm.bisect.parser.args.IntegerArgument;
import org.graalvm.bisect.parser.args.InvalidArgumentException;
import org.graalvm.bisect.parser.args.MissingArgumentException;
import org.graalvm.bisect.parser.args.StringArgument;
import org.graalvm.bisect.parser.args.UnknownArgumentException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ArgumentParserTest {
    private final static double DELTA = 0.000001;
    private static class ProgramArguments {
        static final double DEFAULT_DOUBLE = 3.14;
        static final int DEFAULT_INT = 42;
        ArgumentParser argumentParser;
        DoubleArgument doubleArgument;
        IntegerArgument integerArgument;
        StringArgument stringArgument;

        ProgramArguments() {
            argumentParser = new ArgumentParser("program", "Program description.");
            doubleArgument = argumentParser.addDoubleArgument("--double", DEFAULT_DOUBLE, "A double argument.");
            integerArgument = argumentParser.addIntegerArgument("--int", DEFAULT_INT, "An integer argument.");
            stringArgument = argumentParser.addStringArgument("string", "A string argument.");
        }
    }

    @Test
    public void testDefaultValues() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArguments programArguments = new ProgramArguments();
        String[] args = new String[]{"foo"};
        programArguments.argumentParser.parse(args);
        assertEquals(args[0], programArguments.stringArgument.getValue());
        assertEquals(ProgramArguments.DEFAULT_DOUBLE, programArguments.doubleArgument.getValue(), DELTA);
        assertEquals(ProgramArguments.DEFAULT_INT, programArguments.integerArgument.getValue().intValue());
    }

    @Test
    public void testProvidedValues() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArguments programArguments = new ProgramArguments();
        String[] args = new String[]{"--int", "123", "foo", "--double", "1.23"};
        programArguments.argumentParser.parse(args);
        assertEquals(args[2], programArguments.stringArgument.getValue());
        assertEquals(1.23, programArguments.doubleArgument.getValue(), DELTA);
        assertEquals(123, programArguments.integerArgument.getValue().intValue());;
    }

    @Test(expected = MissingArgumentException.class)
    public void testMissingPositional() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArguments programArguments = new ProgramArguments();
        String[] args = new String[]{"--int", "123"};
        programArguments.argumentParser.parse(args);
    }

    @Test(expected = UnknownArgumentException.class)
    public void testUnknownArgument() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArguments programArguments = new ProgramArguments();
        String[] args = new String[]{"--bar", "123", "foo"};
        programArguments.argumentParser.parse(args);
    }

    @Test(expected = InvalidArgumentException.class)
    public void testArgumentValueMissing() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArguments programArguments = new ProgramArguments();
        String[] args = new String[]{"foo", "--int"};
        programArguments.argumentParser.parse(args);
    }
}
