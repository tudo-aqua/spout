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

import java.util.function.UnaryOperator;


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

    interface BinaryLongShiftOperation {
        <T> T execute(Analysis<T> analysis, int c1, long c2, T s1, T s2);
    }

    interface BinaryFloatOperation {
        <T> T execute(Analysis<T> analysis, float c1, float c2, T s1, T s2);
    }

    interface BinaryDoubleOperation {
        <T> T execute(Analysis<T> analysis, double c1, double c2, T s1, T s2);
    }

    interface UnaryByteOperation{
        <T> T execute(Analysis<T> analysis, byte c1, T s1);
    }

    interface UnaryCharOperation{
        <T> T execute(Analysis<T> analysis, char c1, T s1);
    }

    interface UnaryShortOperation{
        <T> T execute(Analysis<T> analysis, short c1, T s1);
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

    interface UnaryDoubleOperation {
        <T> T execute(Analysis<T> analysis, double c1, T s1);
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
    private Annotations lShiftExecute(int c1, long c2, Annotations a1, Annotations a2, BinaryLongShiftOperation executor) {
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

    private Annotations dexecute(double c1, double c2, Annotations a1, Annotations a2, BinaryDoubleOperation executor) {
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

    private Annotations bexecute(byte c1, Annotations a1, UnaryByteOperation executor) {
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

    private Annotations cexecute(char c1, Annotations a1, UnaryCharOperation executor) {
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

    private Annotations sexecute(short c1, Annotations a1, UnaryShortOperation executor) {
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

    private Annotations dexecute(double c1, Annotations a1, UnaryDoubleOperation executor) {
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
        return dexecute(c1, c2, a1, a2, Analysis::dsub);
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
    public Annotations ladd(long c1, long c2, Annotations a1, Annotations a2) {
        return lexecute(c1, c2, a1, a2, Analysis::ladd);
    }

    @Override
    public Annotations fadd(float c1, float c2, Annotations a1, Annotations a2) {
        return fexecute(c1, c2, a1, a2, Analysis::fadd);
    }

    @Override
    public Annotations dadd(double c1, double c2, Annotations a1, Annotations a2) {
        return dexecute(c1, c2, a1, a2, Analysis::dadd);
    }

    @Override
    public Annotations lmul(long c1, long c2, Annotations a1, Annotations a2) {
        return lexecute(c1, c2, a1, a2, Analysis::lmul);
    }

    @Override
    public Annotations fmul(float c1, float c2, Annotations a1, Annotations a2) {
        return fexecute(c1, c2, a1, a2, Analysis::fmul);
    }

    @Override
    public Annotations dmul(double c1, double c2, Annotations a1, Annotations a2) {
        return dexecute(c1, c2, a1, a2, Analysis::dmul);
    }

    @Override
    public void checkNonZero(int value, Annotations taint) {
        Analysis.super.checkNonZero(value, taint);
    }

    @Override
    public void checkNonZero(long value, Annotations taint) {
        Analysis.super.checkNonZero(value, taint);
    }

    @Override
    public Annotations ldiv(long c1, long c2, Annotations a1, Annotations a2) {
        return lexecute(c1, c2, a1, a2, Analysis::ldiv);
    }

    @Override
    public Annotations fdiv(float c1, float c2, Annotations a1, Annotations a2) {
        return fexecute(c1, c2, a1, a2, Analysis::fdiv);
    }

    @Override
    public Annotations ddiv(double c1, double c2, Annotations a1, Annotations a2) {
        return dexecute(c1, c2, a1, a2, Analysis::ddiv);
    }

    @Override
    public Annotations lrem(long c1, long c2, Annotations a1, Annotations a2) {
        return lexecute(c1, c2, a1, a2, Analysis::lrem);
    }

    @Override
    public Annotations frem(float c1, float c2, Annotations a1, Annotations a2) {
        return fexecute(c1, c2, a1, a2, Analysis::frem);
    }

    @Override
    public Annotations drem(double c1, double c2, Annotations a1, Annotations a2) {
        return dexecute(c1, c2, a1, a2, Analysis::drem);
    }

    @Override
    public Annotations lneg(long c1, Annotations a1) {
        return lexecute(c1, a1, Analysis::lneg);
    }

    @Override
    public Annotations fneg(float c1, Annotations a1) {
        return fexecute(c1, a1, Analysis::fneg);
    }

    @Override
    public Annotations dneg(double c1, Annotations a1) {
        return dexecute(c1, a1, Analysis::dneg);
    }

    @Override
    public Annotations lshl(int c1, long c2, Annotations a1, Annotations a2) {
        return lShiftExecute(c1, c2, a1, a2, Analysis::lshl);
    }

    @Override
    public Annotations lshr(int c1, long c2, Annotations a1, Annotations a2) {
        return lShiftExecute(c1, c2, a1, a2, Analysis::lshr);
    }

    @Override
    public Annotations lushr(int c1, long c2, Annotations a1, Annotations a2) {
        return lShiftExecute(c1, c2, a1, a2, Analysis::lushr);
    }

    @Override
    public Annotations land(long c1, long c2, Annotations a1, Annotations a2) {
        return lexecute(c1, c2, a1, a2, Analysis::land);
    }

    @Override
    public Annotations lor(long c1, long c2, Annotations a1, Annotations a2) {
        return lexecute(c1, c2, a1, a2, Analysis::lor);
    }

    @Override
    public Annotations lxor(long c1, long c2, Annotations a1, Annotations a2) {
        return lexecute(c1, c2, a1, a2, Analysis::lxor);
    }

    @Override
    public Annotations i2d(double c1, Annotations a1) {
        return dexecute(c1, a1, Analysis::i2d);
    }

    @Override
    public Annotations l2i(int c1, Annotations a1) {
        return execute(c1, a1, Analysis::l2i);
    }

    @Override
    public Annotations l2f(float c1, Annotations a1) {
        return fexecute(c1, a1, Analysis::l2f);
    }

    @Override
    public Annotations l2d(double c1, Annotations a1) {
        return dexecute(c1, a1, Analysis::l2d);
    }

    @Override
    public Annotations f2i(int c1, Annotations a1) {
        return execute(c1, a1, Analysis::f2i);
    }

    @Override
    public Annotations f2l(long c1, Annotations a1) {
        return lexecute(c1, a1, Analysis::f2l);
    }

    @Override
    public Annotations f2d(double c1, Annotations a1) {
        return dexecute(c1, a1, Analysis::f2d);
    }

    @Override
    public Annotations d2i(int c1, Annotations a1) {
        return execute(c1, a1, Analysis::d2i);
    }

    @Override
    public Annotations d2l(long c1, Annotations a1) {
        return lexecute(c1, a1, Analysis::d2l);
    }

    @Override
    public Annotations d2f(float c1, Annotations a1) {
        return fexecute(c1, a1, Analysis::d2f);
    }

    @Override
    public Annotations i2b(byte c1, Annotations a1) {
        return bexecute(c1, a1, Analysis::i2b);
    }

    @Override
    public Annotations i2c(char c1, Annotations a1) {
        return cexecute(c1, a1, Analysis::i2c);
    }

    @Override
    public Annotations i2s(short c1, Annotations a1) {
        return sexecute(c1, a1, Analysis::i2s);
    }

    @Override
    public Annotations fcmpl(float c1, float c2, Annotations a1, Annotations a2) {
        return fexecute(c1, c2, a1, a2, Analysis::fcmpl);
    }

    @Override
    public Annotations fcmpg(float c1, float c2, Annotations a1, Annotations a2) {
        return fexecute(c1, c2, a1, a2, Analysis::fcmpg);
    }

    @Override
    public Annotations dcmpl(double c1, double c2, Annotations a1, Annotations a2) {
        return dexecute(c1, c2, a1, a2, Analysis::dcmpl);
    }

    @Override
    public Annotations dcmpg(double c1, double c2, Annotations a1, Annotations a2) {
        return dexecute(c1, c2, a1, a2, Analysis::dcmpg);
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
