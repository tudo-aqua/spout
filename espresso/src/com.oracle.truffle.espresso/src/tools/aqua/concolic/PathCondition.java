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

import tools.aqua.smt.Expression;
import tools.aqua.spout.TraceElement;

public class PathCondition extends TraceElement {

    private final Expression condition;

    private final int branchCount;

    private final int branchId;

    public PathCondition(Expression condition, int branchId, int branchCount) {
        this.condition = condition;
        this.branchId = branchId;
        this.branchCount = branchCount;
    }

    public Expression getCondition() {
        return condition;
    }

    @Override
    public String toString() {
        return "[DECISION] (assert " + condition + ")" +
                " // branchCount=" + branchCount +
                ", branchId=" + branchId;
    }
}
