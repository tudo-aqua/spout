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

public interface Expression {

    static boolean isBoolean(Expression e) {
        if (e instanceof Atom) {
            Atom a = (Atom) e;
            return a.getType().equals(PrimitiveTypes.BOOL);
        }
        else if (e instanceof ComplexExpression) {
            ComplexExpression c = (ComplexExpression) e;
            return c.getOperator().isBoolean();
        }
        else {
            return false;
        }
    }

    static boolean isCmpExpression(Expression e) {
        if (e instanceof ComplexExpression) {
            ComplexExpression c = (ComplexExpression) e;
            return c.getOperator().isCmp();
        }
        else {
            return false;
        }
    }

}
