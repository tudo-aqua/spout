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

package tools.aqua.spout;

import com.oracle.truffle.api.CompilerDirectives;
import tools.aqua.concolic.PathCondition;
import tools.aqua.smt.Expression;

/**
 * functions for recording traces of executions
 */
public class Trace {

    private TraceElement traceHead = null;
    private TraceElement traceTail = null;

    Trace() {
    }

    @CompilerDirectives.TruffleBoundary
    public void addElement(TraceElement tNew) {
        // TODO: maybe this can be caught earlier?
        if (tNew instanceof PathCondition) {
            if (!Expression.isFormula( ((PathCondition) tNew).getCondition() )) {
                return;
            }
        }

        //ifLog(tNew.toString());
        if (traceHead == null) {
            traceHead = tNew;
        } else {
            traceTail.setNext(tNew);
        }
        traceTail = tNew;
    }

    /*
     * print trace to shell.
     */
    public void printTrace() {
        TraceElement cur = traceHead;
        while (cur != null) {
            System.out.println(cur.toString());
            cur = cur.getNext();
        }
    }

}
