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
import com.oracle.truffle.espresso.nodes.BytecodeNode;


public class MetaAnalysis implements Analysis<Annotations> {

    private final Config config;

    private final Analysis<?>[] analyses;

    MetaAnalysis(Config config) {
        this.config = config;
        this.analyses = config.getAnalyses();
    }

    interface BinaryOperation {
        <T> T execute(Analysis<T> analysis, int c1, int c2, T s1, T s2);
    }

    interface BinaryLongOperation {
        <T> T execute(Analysis<T> analysis, long c1, long c2, T s1, T s2);
    }

    interface BinaryFloatOperation {
        <T> T execute(Analysis<T> analysis, float c1, float c2, T s1, T s2);
    }

    interface UnaryOperation {
        <T> T execute(Analysis<T> analysis, int c1, T s1);
    }

    interface UnaryLongOperation {
        <T> T execute(Analysis<T> analysis, long c1, T s1);
    }

    interface UnaryFloatOperation {
        <T> T execute(Analysis<T> analysis, float c1, T s1);
    }

    private Annotations execute(int c1, int c2, Annotations a1, Annotations a2, BinaryOperation executor) {
        int i = 0;
        boolean hasResult = false;
        Object[] annotations = new Object[analyses.length];
        for (Analysis<?> analysis : analyses) {
            Object result = executor.execute(analysis, c1, c2, Annotations.annotation(a1, i), Annotations.annotation(a2, i));
            if (result != null) {
                annotations[i] = result;
                hasResult = true;
            }
            i++;
        }
        return hasResult ? new Annotations(annotations) : null;
    }

    private Annotations lexecute(long c1, long c2, Annotations a1, Annotations a2, BinaryLongOperation executor) {
        int i = 0;
        boolean hasResult = false;
        Object[] annotations = new Object[analyses.length];
        for (Analysis<?> analysis : analyses) {
            Object result = executor.execute(analysis, c1, c2, Annotations.annotation(a1, i), Annotations.annotation(a2, i));
            if (result != null) {
                annotations[i] = result;
                hasResult = true;
            }
            i++;
        }
        return hasResult ? new Annotations(annotations) : null;
    }

    private Annotations fexecute(float c1, float c2, Annotations a1, Annotations a2, BinaryFloatOperation executor) {
        int i = 0;
        boolean hasResult = false;
        Object[] annotations = new Object[analyses.length];
        for (Analysis<?> analysis : analyses) {
            Object result = executor.execute(analysis, c1, c2, Annotations.annotation(a1, i), Annotations.annotation(a2, i));
            if (result != null) {
                annotations[i] = result;
                hasResult = true;
            }
            i++;
        }
        return hasResult ? new Annotations(annotations) : null;
    }

    private Annotations execute(int c1, Annotations a1, UnaryOperation executor) {
        int i = 0;
        boolean hasResult = false;
        Object[] annotations = new Object[analyses.length];
        for (Analysis<?> analysis : analyses) {
            Object result = executor.execute(analysis, c1, Annotations.annotation(a1, i));
            if (result != null) {
                annotations[i] = result;
                hasResult = true;
            }
            i++;
        }
        return hasResult ? new Annotations(annotations) : null;
    }

    private Annotations lexecute(long c1, Annotations a1, UnaryLongOperation executor) {
        int i = 0;
        boolean hasResult = false;
        Object[] annotations = new Object[analyses.length];
        for (Analysis<?> analysis : analyses) {
            Object result = executor.execute(analysis, c1, Annotations.annotation(a1, i));
            if (result != null) {
                annotations[i] = result;
                hasResult = true;
            }
            i++;
        }
        return hasResult ? new Annotations(annotations) : null;
    }
    private Annotations fexecute(float c1, Annotations a1, UnaryFloatOperation executor) {
        int i = 0;
        boolean hasResult = false;
        Object[] annotations = new Object[analyses.length];
        for (Analysis<?> analysis : analyses) {
            Object result = executor.execute(analysis, c1, Annotations.annotation(a1, i));
            if (result != null) {
                annotations[i] = result;
                hasResult = true;
            }
            i++;
        }
        return hasResult ? new Annotations(annotations) : null;
    }

    @Override
    public Annotations iadd(int c1, int c2, Annotations a1, Annotations a2) {
        return execute(c1, c2, a1, a2, Analysis::iadd);
    }

    @Override
    public Annotations isub(int c1, int c2, Annotations a1, Annotations a2) {
        return execute(c1, c2, a1, a2, Analysis::isub);
    }

    @Override
    public Annotations imul(int c1, int c2, Annotations a1, Annotations a2) {
        return execute(c1, c2, a1, a2, Analysis::imul);
    }

    @Override
    public Annotations idiv(int c1, int c2, Annotations a1, Annotations a2) {
        return execute(c1, c2, a1, a2, Analysis::idiv);
    }

    @Override
    public Annotations irem(int c1, int c2, Annotations a1, Annotations a2) {
        return execute(c1, c2, a1, a2, Analysis::irem);
    }

    @Override
    public Annotations ishl(int c1, int c2, Annotations a1, Annotations a2) {
        return execute(c1, c2, a1, a2, Analysis::ishl);
    }

    @Override
    public Annotations ishr(int c1, int c2, Annotations a1, Annotations a2) {
        return execute(c1, c2, a1, a2, Analysis::ishr);
    }

    @Override
    public Annotations iushr(int c1, int c2, Annotations a1, Annotations a2) {
        return execute(c1, c2, a1, a2, Analysis::iushr);
    }

    @Override
    public Annotations i2l(long c1, Annotations a1) {
        return lexecute(c1, a1, Analysis::i2l);
    }

    @Override
    public Annotations i2f(float c1, Annotations a1) {
        return fexecute(c1, a1, Analysis::i2f);
    }

    @Override
    public Annotations lsub(long c1, long c2, Annotations a1, Annotations a2) {
        return lexecute(c1, c2, a1, a2, Analysis::lsub);
    }

    @Override
    public Annotations fsub(float c1, float c2, Annotations a1, Annotations a2) {
        return fexecute(c1, c2, a1, a2, Analysis::fsub);
    }
    @Override
    public Annotations dsub(double c1, double c2, Annotations a1, Annotations a2) {
        return fexecute(c1, c2, a1, a2, Analysis::dsub);
    }

    @Override
    public Annotations iinc(int c1, Annotations a1) {
        return execute(c1, a1, Analysis::iinc);
    }

    public Annotations lcmp(long c1, long c2, Annotations a1, Annotations a2) {
        return lexecute(c1, c2, a1, a2, Analysis::lcmp);
    }

    @Override
    public Annotations ineg(int c1, Annotations a1) {
        return execute(c1, a1, Analysis::ineg);
    }

    @Override
    public Annotations iand(int c1, int c2, Annotations a1, Annotations a2) {
        return execute(c1, c2, a1, a2, Analysis::iand);
    }

    @Override
    public Annotations ior(int c1, int c2, Annotations a1, Annotations a2) {
        return execute(c1, c2, a1, a2, Analysis::ior);
    }

    @Override
    public Annotations ixor(int c1, int c2, Annotations a1, Annotations a2) {
        return execute(c1, c2, a1, a2, Analysis::ixor);
    }


    @Override
    public void takeBranchPrimitive1(VirtualFrame frame, BytecodeNode bcn, int bci,
                                     int opcode, boolean takeBranch, Annotations a) {
        int i = 0;
        for (Analysis<?> analysis : analyses) {
            analysis.takeBranchPrimitive1(frame, bcn, bci, opcode, takeBranch, Annotations.annotation(a, i++));
        }
    }

    @Override
    public void takeBranchPrimitive2(VirtualFrame frame, BytecodeNode bcn, int bci,
                                     int opcode, boolean takeBranch, int c1, int c2, Annotations a1, Annotations a2) {
        int i = 0;
        for (Analysis<?> analysis : analyses) {
            analysis.takeBranchPrimitive2(frame, bcn, bci, opcode, takeBranch, c1, c2, Annotations.annotation(a1, i), Annotations.annotation(a2, i));
            i++;
        }
    }

    @Override
    public void tableSwitch(VirtualFrame frame, BytecodeNode bcn, int bci,
                            int low, int high, int concIndex, Annotations a1) {
        int i = 0;
        for (Analysis<?> analysis : analyses) {
            analysis.tableSwitch(frame, bcn, bci, low, high, concIndex, Annotations.annotation(a1, i));
        }
    }

    @Override
    public void lookupSwitch(VirtualFrame frame, BytecodeNode bcn, int bci,
                             int[] vals, int key, Annotations a1) {
        int i = 0;
        for (Analysis<?> analysis : analyses) {
           analysis.lookupSwitch(frame, bcn, bci, vals, key, Annotations.annotation(a1, i));
        }
    }
}
