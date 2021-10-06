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

import java.util.Iterator;

public class ComplexExpression implements Expression {

    private final OperatorComparator operator;

    private final Expression[] subExpressions;

    public ComplexExpression(OperatorComparator operator, Expression ... subExpressions) {
        this.operator = operator;
        this.subExpressions = subExpressions;
    }

    public OperatorComparator getOperator() {
        return operator;
    }

    public Expression[] getSubExpressions() {
        return subExpressions;
    }

    @Override
    public String toString() {
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
