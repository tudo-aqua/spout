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

package tools.aqua.concolic;

public class Variable extends Atom {

    private final int id;

    public Variable(PrimitiveTypes type, int id) {
        super(type);
        this.id = id;
    }

    @Override
    public String toString() {
        switch (this.getType()) {
            case BOOL:
                return "__bool_" + this.id;
            case BYTE:
                return "__byte_" + this.id;
            case CHAR:
                return "__char_" + this.id;
            case SHORT:
                return "__short_" + this.id;
            case INT:
                return "__int_" + this.id;
            case LONG:
                return "__long_" + this.id;
            case FLOAT:
                return "__float_" + this.id;
            case DOUBLE:
                return "__double_" + this.id;
            case STRING:
                return "__string_" + this.id;
            default:
                return "Variable{" +
                        "id=" + id +
                        "id=" + id +
                        '}';
        }

    }
}
