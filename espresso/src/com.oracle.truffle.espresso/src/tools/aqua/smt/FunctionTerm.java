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

package tools.aqua.smt;

/**
 * Application of an uninterpreted function, e.g. the value of field {@code a} of the object denoted by
 * {@code __object_0}: {@code (fld.Pair.a __object_0)}. Objects are integers (their identities); every field is a
 * function of the object, so aliasing is equality of identities and congruence is part of the theory.
 */
public class FunctionTerm extends Atom {

    private final String name;
    private final Expression[] arguments;

    public FunctionTerm(String name, Types type, Expression... arguments) {
        super(type);
        this.name = name;
        this.arguments = arguments;
        for (Expression e : arguments) {
            assert e != null;
        }
    }

    public String getName() {
        return name;
    }

    public Expression[] getArguments() {
        return arguments;
    }

    @Override
    public String toString() {
        if (arguments.length == 0) {
            // a declared constant
            return name;
        }
        StringBuilder sb = new StringBuilder("(").append(name);
        for (Expression a : arguments) {
            sb.append(' ').append(a);
        }
        return sb.append(')').toString();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FunctionTerm && o.toString().equals(toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
