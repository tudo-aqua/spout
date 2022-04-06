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
package org.graalvm.bisect.parser.args;

/**
 * Represents a program argument with a value that may be a default value or parsed from the program arguments. The
 * purpose of this class is reduced code duplication while all argument types still have the common ancestor
 * {@link Argument}.
 * @param <T> the type of the value
 */
abstract class ValuedArgument<T> extends Argument {
    /**
     * Gets the value of the argument.
     * @return the value of the argument
     */
    public T getValue() {
        return value;
    }

    /**
     * The value of the argument.
     */
    protected T value;

    /**
     * Constructs a required argument with a value.
     * @param name the name of the argument
     * @param help the help message
     */
    ValuedArgument(String name, String help) {
        super(name, true, help);
        value = null;
    }

    /**
     * Constructs an optional argument with a default value.
     * @param name the name of the argument
     * @param defaultValue the default value
     * @param help the help message
     */
    ValuedArgument(String name, T defaultValue, String help) {
        super(name, false, help);
        set = true;
        value = defaultValue;
    }
}
