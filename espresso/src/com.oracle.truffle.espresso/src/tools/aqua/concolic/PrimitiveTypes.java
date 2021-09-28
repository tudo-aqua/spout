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

public enum PrimitiveTypes {
    INT, BOOL, CHAR, BYTE, SHORT, LONG, FLOAT, DOUBLE, STRING, NAT;

    @Override
    public String toString() {
        switch(this) {
            case BOOL:
                return "Bool";
            case BYTE:
                return "(_ BitVec 8)";
            case CHAR:
            case SHORT:
                return "(_ BitVec 16)";
            case INT:
                return "(_ BitVec 32)";
            case LONG:
                return "(_ BitVec 64)";
            case FLOAT:
                return "(_ FloatingPoint 8 24)";
            case DOUBLE:
                return "(_ FloatingPoint 11 53)";
            case STRING:
                return "String";
            // Strictly the NAT type is no primitive type for Java,
            // but sometimes we need Variables that are Integer in the SMT-Lib theory to encode string constraints.
            // I call them NAT as this is sometimes used in the SMT-Lib context to distinguish Integer and BV representations.
            case NAT:
                return "Integer";
            default:
                return super.toString();
        }
    }
}
