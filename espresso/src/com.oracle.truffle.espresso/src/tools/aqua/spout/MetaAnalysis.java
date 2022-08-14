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

import com.oracle.truffle.api.frame.VirtualFrame;
import tools.aqua.concolic.ConcolicAnalysis;
import tools.aqua.taint.TaintAnalysis;

public class MetaAnalysis implements Analysis<Annotations> {

    private Config config;

    private Analysis<?>[] analyses = new Analysis<?>[] {};

    MetaAnalysis(Config config) {
        this.config = config;
        initialize();
    }

    private void initialize() {
        Annotations.configure(2);
        this.analyses = new Analysis[] {
          new ConcolicAnalysis(),
          new TaintAnalysis()
        };
    }

    @Override
    public Annotations iadd(int c1, int c2, Annotations a1, Annotations a2) {
        return execute(c1, c2, a1, a2, (analysis, c11, c21, s1, s2) -> analysis.iadd(c11, c21, s1, s2));
    }

    private Annotations execute(int c1, int c2, Annotations a1, Annotations a2, Executor executor) {
        int i = 0;
        boolean hasResult = false;
        Object[] annotations = new Object[analyses.length];
        for (Analysis<?> analysis : analyses) {
            Object result = executor.execute(analysis, c1, c2, AnnotatedValue.annotation(a1, i), AnnotatedValue.annotation(a2, i));
            if (result != null) {
                annotations[i] = result;
                hasResult = true;
            }
            i++;
        }
        return hasResult ? new Annotations(annotations) : null;
    }

    interface Executor {
        Object execute(Analysis analysis, int c1, int c2, Object s1, Object s2);
    }
}
