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

public class Variable extends Atom {

    private final int id;

    public Variable(Types type, int id) {
        super(type);
        this.id = id;
    }

    private String wrap(int id) {
            return (id >= 0 ? "" + this.id : "taint" + -this.id);
    }

    @Override
    public String toString() {
        switch (this.getType()) {
            case BOOL:
                return "__bool_" + wrap(this.id);
            case BYTE:
                return "__byte_" + wrap(this.id);
            case CHAR:
                return "__char_" + wrap(this.id);
            case SHORT:
                return "__short_" + wrap(this.id);
            case INT:
                return "__int_" + wrap(this.id);
            case LONG:
                return "__long_" + wrap(this.id);
            case FLOAT:
                return "__float_" + wrap(this.id);
            case DOUBLE:
                return "__double_" + wrap(this.id);
            case STRING:
                return "__string_" + wrap(this.id);
            default:
                return "Variable{" +
                        "id=" + wrap(this.id) +
                        "id=" + wrap(this.id) +
                        '}';
        }

    }
}
