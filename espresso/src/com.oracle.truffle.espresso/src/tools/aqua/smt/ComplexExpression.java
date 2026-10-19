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

import java.util.Iterator;
import tools.aqua.spout.SPouT;

public class ComplexExpression implements Expression {

    private final OperatorComparator operator;

    private final Expression[] subExpressions;

    public ComplexExpression(OperatorComparator operator, Expression ... subExpressions) {
        this.operator = operator;
        this.subExpressions = subExpressions;
        for (Expression e : subExpressions) {
            assert e != null;
        }
    }

    public OperatorComparator getOperator() {
        return operator;
    }

    public Expression[] getSubExpressions() {
        return subExpressions;
    }

    @Override
    public String toString() {
        switch (operator) {
            case BVNE:
            case STRINGNE:
                return "(not " + application() + ")";
            case FREM:
            case DREM:
                return javaRemainder(subExpressions[0], subExpressions[1]);
            default:
                return application();
        }
    }

    /**
     * Java's {@code %} on floating-point numbers is the truncating remainder (C's {@code fmod}): the result has the
     * sign of the dividend. SMT-LIB's {@code fp.rem} is the IEEE remainder (quotient rounded to nearest even), so
     * its result is corrected by the divisor if the signs differ. The corrected value is the exact {@code fmod}
     * result, which is representable, so the addition does not round. NaN, infinities and zeros are handled by
     * {@code fp.rem} as in Java ({@code fp.rem} returns zeros with the sign of the dividend).
     */
    private static String javaRemainder(Expression dividend, Expression divisor) {
        return "(let ((__rem_x " + dividend + ") (__rem_y " + divisor + "))" +
                " (let ((__rem_r (fp.rem __rem_x __rem_y)))" +
                " (ite (and (not (fp.isZero __rem_r)) (not (= (fp.isNegative __rem_r) (fp.isNegative __rem_x))))" +
                " (ite (fp.isNegative __rem_x) (fp.sub RNE __rem_r (fp.abs __rem_y)) (fp.add RNE __rem_r (fp.abs __rem_y)))" +
                " __rem_r)))";
    }

    private String application() {
        return "(" + operator + " " + String.join(" ", new Iterable<CharSequence>() {
            @Override
            public Iterator<CharSequence> iterator() {
                return new Iterator<CharSequence>() {
                    int i = 0;
                    @Override
                    public boolean hasNext() {
                        return i < subExpressions.length;
                    }
                    @Override
                    public CharSequence next() {
                        return subExpressions[i++].toString();
                    }
                };
            }
        }) + ")";
    }

}
