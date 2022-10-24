/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package tools.aqua.spout;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.concolic.ConcolicAnalysis;
import tools.aqua.smt.Expression;
import tools.aqua.taint.PostDominatorAnalysis;
import tools.aqua.taint.Taint;
import tools.aqua.witnesses.GWIT;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static com.oracle.truffle.espresso.bytecode.Bytecodes.*;
import static com.oracle.truffle.espresso.nodes.BytecodeNode.*;
import static tools.aqua.smt.Constant.INT_ZERO;
import static tools.aqua.smt.Constant.LONG_ZERO;

public class SPouT {

    public static final boolean DEBUG = true;

    private static boolean analyze = false, oldAnalyze = analyze;

    private static MetaAnalysis analysis = null;

    private static Config config = null;

    private static Trace trace = null;

    private static GWIT gwit;

    // --------------------------------------------------------------------------
    //
    // start and stop

    @CompilerDirectives.TruffleBoundary
    public static void newPath(String options) {
        System.out.println("======================== START PATH [BEGIN].");
        config = new Config(options);
        config.configureAnalysis();
        analysis = new MetaAnalysis(config);
        trace = config.getTrace();
        gwit = new GWIT(trace);
        System.out.println("======================== START PATH [END].");
        // TODO: should be deferred to latest possible point in time
        analyze = true;
        oldAnalyze = true;
    }

    @CompilerDirectives.TruffleBoundary
    public static void endPath() {
        System.out.println("======================== END PATH [BEGIN].");
        stopAnalysis();
        if (trace != null) {
            trace.printTrace();
        }
        System.out.println("======================== END PATH [END].");
        System.out.println("[ENDOFTRACE]");
        System.out.flush();
    }

    private static void stopAnalysis() {
        if (analyze) {
            analyze = false;
            //FIXME: analysis.terminate();
        }
    }

    // --------------------------------------------------------------------------
    //
    // analysis entry points

    // FIXME: move part of these methods into the different analyses?

    @CompilerDirectives.TruffleBoundary
    public static void stopRecording(String message, Meta meta) {
        log(message);
        trace.addElement(new ExceptionalEvent(message));
        stopAnalysis();
        throw meta.throwException(meta.java_lang_RuntimeException);
    }


    @CompilerDirectives.TruffleBoundary
    public static void assume(Object condition, Meta meta) {
        if (!analyze || !config.hasConcolicAnalysis()) {
            // FIXME: dont care about assumptions outside of concolic analysis?
            return;
        }

        if(condition instanceof AnnotatedValue){
            stopRecording("Unexpected annotated Value observed", meta);
        }
        if (!((boolean) condition)) {
            stopRecording("assumption violation", meta);
        }
    }

    @CompilerDirectives.TruffleBoundary
    public static void uncaughtException(StaticObject pendingException) {
        if (analyze) {
            String errorMessage = pendingException.getKlass().getNameAsString();
            trace.addElement(new ErrorEvent(errorMessage));
        }
    }

    @CompilerDirectives.TruffleBoundary
    public static void uncaughtException(String pendingExceptionName) {
        if (analyze) {
            String errorMessage = pendingExceptionName.
                    trim().
                    replaceAll("\\.", "/");

            trace.addElement(new ErrorEvent(errorMessage));
        }
    }

    // --------------------------------------------------------------------------
    //
    // concolic values

    @CompilerDirectives.TruffleBoundary
    public static Object nextSymbolicInt() {
        if (!analyze || !config.hasConcolicAnalysis()) return 0;
        Object av = config.getConcolicAnalysis().nextSymbolicInt();
        gwit.trackLocationForWitness("" + (int) ((AnnotatedValue) av).getValue());
        return av;
    }

    @CompilerDirectives.TruffleBoundary
    public static Object nextSymbolicLong() {
        if (!analyze || !config.hasConcolicAnalysis()) return 0;
        Object av = config.getConcolicAnalysis().nextSymbolicLong();
        gwit.trackLocationForWitness("" + (long) ((AnnotatedValue) av).getValue() + "L");
        return av;
    }

    @CompilerDirectives.TruffleBoundary
    public static Object nextSymbolicFloat() {
        if (!analyze || !config.hasConcolicAnalysis()) return 0;
        Object av = config.getConcolicAnalysis().nextSymbolicFloat();
        gwit.trackLocationForWitness("Float.parseFloat(\"" +
                (float) ((AnnotatedValue) av).getValue() + "\")");
        return av;
    }

    @CompilerDirectives.TruffleBoundary
    public static Object nextSymbolicDouble() {
        if (!analyze || !config.hasConcolicAnalysis()) return 0;
        Object av = config.getConcolicAnalysis().nextSymbolicDouble();
        gwit.trackLocationForWitness("Double.parseDouble(\"" +
                (double) ((AnnotatedValue) av).getValue() + "\")");
        return av;
    }

    @CompilerDirectives.TruffleBoundary
    public static Object nextSymbolicBoolean() {
        if (!analyze || !config.hasConcolicAnalysis()) return 0;
        Object av = config.getConcolicAnalysis().nextSymbolicBoolean();
        gwit.trackLocationForWitness("" + (boolean) ((AnnotatedValue) av).getValue());
        return av;
    }

    @CompilerDirectives.TruffleBoundary
    public static Object nextSymbolicByte() {
        if (!analyze || !config.hasConcolicAnalysis()) return 0;
        Object av = config.getConcolicAnalysis().nextSymbolicByte();
        gwit.trackLocationForWitness("" + (int) ((AnnotatedValue) av).getValue());
        return av;
    }

    @CompilerDirectives.TruffleBoundary
    public static Object nextSymbolicShort() {
        if (!analyze || !config.hasConcolicAnalysis()) return 0;
        Object av = config.getConcolicAnalysis().nextSymbolicShort();
        gwit.trackLocationForWitness("" + (int) ((AnnotatedValue) av).getValue());
        return av;
    }

    @CompilerDirectives.TruffleBoundary
    public static Object nextSymbolicChar() {
        if (!analyze || !config.hasConcolicAnalysis()) return 0;
        Object av = config.getConcolicAnalysis().nextSymbolicChar();
        gwit.trackLocationForWitness("" + (int) ((AnnotatedValue) av).getValue());
        return av;
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject nextSymbolicString(Meta meta) {
        if (!analyze || !config.hasConcolicAnalysis()) return meta.toGuestString("");
        StaticObject annotatedObject = config.getConcolicAnalysis().nextSymbolicString(meta);
        gwit.trackLocationForWitness("\"" + annotatedObject + "\"");
        return annotatedObject;
    }

    @CompilerDirectives.TruffleBoundary
    public static Object taint(Object o, int color) {
        if (!analyze || !config.hasTaintAnalysis()) return o;
        return config.getTaintAnalysis().taint(o, color);
    }

    @CompilerDirectives.TruffleBoundary
    public static void checkTaint(Object o, int color) {
        if (!analyze || !config.hasTaintAnalysis()) return;
        config.getTaintAnalysis().checkTaint(o instanceof AnnotatedValue ? (AnnotatedValue) o : null, color);
    }

    public static void nextBytecode(VirtualFrame frame, int bci) {
        if (!analyze || !config.analyzeControlFlowTaint()) return;
        config.getTaintAnalysis().informationFlowNextBytecode(frame, bci);
    }

    public static void informationFlowMethodReturn(VirtualFrame frame) {
        if (!analyze || !config.analyzeControlFlowTaint()) return;
        config.getTaintAnalysis().informationFlowMethodReturn(frame);
    }

    public static void iflowRegisterException() {
        // TODO: should we check for type and log all assertion violations (even when caught?)
        if (!analyze || !config.analyzeControlFlowTaint()) return;
        config.getTaintAnalysis().iflowRegisterException();
    }

    public static void iflowUnregisterException(VirtualFrame frame, Method method, int bci) {
        if (!analyze || !config.analyzeControlFlowTaint()) return;
        config.getTaintAnalysis().iflowUnregisterException(frame, method, bci);
    }

    public static int iflowGetIpdBCI() {
        if (!analyze || !config.analyzeControlFlowTaint()) return -1;
        return config.getTaintAnalysis().iflowGetIpdBCI();
    }

    public static void markWithIFTaint(VirtualFrame frame, int top){
        if(!analyze || !config.analyzeControlFlowTaint()) return;
        Taint t = config.getTaintAnalysis().getIfTaint();
        if (t == null) return;
        Annotations a = new Annotations();
        a.set(config.getTaintIdx(), t);
        AnnotatedVM.putAnnotations(frame, top, a);
    }

    public static PostDominatorAnalysis iflowGetPDA(Method method) {
        if (!analyze || !config.analyzeControlFlowTaint()) return null;
        return new PostDominatorAnalysis(method);
    }

    // --------------------------------------------------------------------------
    //
    // byte codes

    // case IADD: putInt(frame, top - 2, popInt(frame, top - 1) + popInt(frame, top - 2)); break;
    public static void iadd(VirtualFrame frame, int top) {
        // concrete
        int c1 = popInt(frame, top - 1);
        int c2 = popInt(frame, top - 2);
        int concResult = c1 + c2;
        putInt(frame, top - 2, concResult);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 2, analysis.iadd(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 2)));
    }

    public static void ladd(VirtualFrame frame, int top) {
        long c1 = popLong(frame, top - 1);
        long c2 = popLong(frame, top - 3);
        putLong(frame, top - 4, c1 + c2);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 3, analysis.ladd(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 3)));
    }

    public static void fadd(VirtualFrame frame, int top) {
        float c1 = popFloat(frame, top - 1);
        float c2 = popFloat(frame, top - 2);
        putFloat(frame, top - 2, c1 + c2);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 2, analysis.fadd(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 2)));
    }

    public static void dadd(VirtualFrame frame, int top) {
        double c1 = popDouble(frame, top - 1);
        double c2 = popDouble(frame, top - 3);
        putDouble(frame, top - 4, c1 + c2);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 3, analysis.dadd(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 3)));
    }


    //putInt(frame, top - 2, popInt(frame, top - 2) - popInt(frame, top - 1)); break;
    public static void isub(VirtualFrame frame, int top) {
        int c1 = popInt(frame, top - 1);
        int c2 = popInt(frame, top - 2);
        int concResult = c2 - c1;
        putInt(frame, top - 2, concResult);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 2, analysis.isub(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 2)));
    }

    //case LSUB: putLong(frame, top - 4, popLong(frame, top - 3) - popLong(frame, top - 1)); break;
    public static void lsub(VirtualFrame frame, int top) {
        long c1 = popLong(frame, top - 1);
        long c2 = popLong(frame, top - 3);
        long concResult = c2 - c1;
        putLong(frame, top - 4, concResult);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 3, analysis.lsub(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 3)));
    }

    //case FSUB: putFloat(frame, top - 2, popFloat(frame, top - 2) - popFloat(frame, top - 1)); break;
    public static void fsub(VirtualFrame frame, int top) {
        float c1 = popFloat(frame, top - 1);
        float c2 = popFloat(frame, top - 2);
        float concResult = c2 - c1;
        putFloat(frame, top - 2, concResult);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 2, analysis.fsub(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 2)));
    }

    public static void dsub(VirtualFrame frame, int top) {
        double c1 = popDouble(frame, top - 1);
        double c2 = popDouble(frame, top - 3);
        putDouble(frame, top - 4, c2 - c1);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 3, analysis.dsub(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 3)));
    }

    // setLocalInt(frame, bs.readLocalIndex1(curBCI), getLocalInt(frame, bs.readLocalIndex1(curBCI)) + bs.readIncrement1(curBCI));
    public static void iinc(VirtualFrame frame, int index, int incr) {
        // concrete
        int c1 = BytecodeNode.getLocalInt(frame, index);
        int concResult = c1 + incr;
        BytecodeNode.setLocalInt(frame, index, concResult);
        if (!analyze) return;
        AnnotatedVM.setLocalAnnotations(frame, index, analysis.iinc(incr, AnnotatedVM.getLocalAnnotations(frame, index)));
    }

    public static void lcmp(VirtualFrame frame, int top) {
        long c1 = popLong(frame, top - 1);
        long c2 = popLong(frame, top - 3);
        putInt(frame, top - 4, Long.compare(c2, c1));
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 4, analysis.lcmp(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 3)));
    }


    public static void fcmpl(VirtualFrame frame, int top) {
        float c1 = popFloat(frame, top - 1);
        float c2 = popFloat(frame, top - 2);
        putInt(frame, top - 2, compareFloatLess(c1, c2));
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 2, analysis.fcmpl(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 2)));
    }

    public static void fcmpg(VirtualFrame frame, int top) {
        float c1 = popFloat(frame, top - 1);
        float c2 = popFloat(frame, top - 2);
        putInt(frame, top - 2, compareFloatGreater(c1, c2));
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 2, analysis.fcmpg(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 2)));
    }

    public static void dcmpl(VirtualFrame frame, int top) {
        double c1 = popDouble(frame, top - 1);
        double c2 = popDouble(frame, top - 3);
        putInt(frame, top - 4, compareDoubleLess(c1, c2));
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 4, analysis.dcmpl(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 3)));
    }

    public static void dcmpg(VirtualFrame frame, int top) {
        double c1 = popDouble(frame, top - 1);
        double c2 = popDouble(frame, top - 3);
        putInt(frame, top - 4, compareDoubleGreater(c1, c2));
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 4, analysis.dcmpg(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 3)));
    }


    public static void imul(VirtualFrame frame, int top) {
        int c1 = popInt(frame, top - 1);
        int c2 = popInt(frame, top - 2);
        int concResult = c1 * c2;
        putInt(frame, top - 2, concResult);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 2, analysis.imul(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 2)));
    }

    public static void lmul(VirtualFrame frame, int top) {
        long c1 = popLong(frame, top - 1);
        long c2 = popLong(frame, top - 3);
        putLong(frame, top - 4, c1 * c2);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 3, analysis.lmul(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 3)));
    }

    public static void fmul(VirtualFrame frame, int top) {
        float c1 = popFloat(frame, top - 1);
        float c2 = popFloat(frame, top - 2);
        putFloat(frame, top - 2, c1 * c2);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 2, analysis.fmul(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 2)));
    }

    public static void dmul(VirtualFrame frame, int top) {
        double c1 = popDouble(frame, top - 1);
        double c2 = popDouble(frame, top - 3);
        putDouble(frame, top - 4, c1 * c2);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 3, analysis.dmul(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 3)));
    }

    public static void idiv(VirtualFrame frame, int top, BytecodeNode bn) {
        int c1 = popInt(frame, top - 1);
        checkNotZero(c1, AnnotatedVM.peekAnnotations(frame, top - 1), bn);
        int c2 = popInt(frame, top - 2);
        putInt(frame, top - 2, c2 / c1);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 2, analysis.idiv(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 2)));
    }

    public static void ldiv(VirtualFrame frame, int top, BytecodeNode bn) {
        long c1 = popLong(frame, top - 1);
        checkNotZero(c1, AnnotatedVM.peekAnnotations(frame, top - 1), bn);
        long c2 = popLong(frame, top - 3);
        putLong(frame, top - 4, c2 / c1);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 3, analysis.ldiv(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 3)));
    }

    public static void fdiv(VirtualFrame frame, int top) {
        float c1 = popFloat(frame, top - 1);
        float c2 = popFloat(frame, top - 2);
        putFloat(frame, top - 2, c2 / c1);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 2, analysis.fdiv(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 2)));
    }

    public static void ddiv(VirtualFrame frame, int top) {
        double c1 = popDouble(frame, top - 1);
        double c2 = popDouble(frame, top - 3);
        putDouble(frame, top - 4, c2 / c1);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 3, analysis.ddiv(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 3)));
    }


    private static void checkNotZero(int c1, Annotations a, BytecodeNode bn) {
        if (c1 == 0) {
            if (a != null && analyze && config.hasConcolicAnalysis()) {
                config.getConcolicAnalysis().addZeroToTrace(a, INT_ZERO);
            }
            bn.enterImplicitExceptionProfile();
            Meta meta = bn.getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_ArithmeticException, "/ by zero");
        } else {
            if (a != null && analyze && config.hasConcolicAnalysis()) {
                config.getConcolicAnalysis().addNotZeroToTrace(a, INT_ZERO);
            }
        }
    }

    private static void checkNotZero(long c1, Annotations a, BytecodeNode bn) {
        if (c1 == 0l) {
            if (a != null && analyze && config.hasConcolicAnalysis()) {
                config.getConcolicAnalysis().addZeroToTrace(a, LONG_ZERO);
            }
            bn.enterImplicitExceptionProfile();
            Meta meta = bn.getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_ArithmeticException, "/ by zero");
        } else {
            if (a != null && analyze && config.hasConcolicAnalysis()) {
                config.getConcolicAnalysis().addNotZeroToTrace(a, LONG_ZERO);
            }
        }
    }

    public static void irem(VirtualFrame frame, int top, BytecodeNode bn) {
        int c1 = popInt(frame, top - 1);
        checkNotZero(c1, AnnotatedVM.peekAnnotations(frame, top - 1), bn);
        int c2 = popInt(frame, top - 2);
        putInt(frame, top - 2, c2 % c1);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 2, analysis.irem(c2, c1,
                AnnotatedVM.popAnnotations(frame, top - 2),
                AnnotatedVM.popAnnotations(frame, top - 1)));
    }

    public static void lrem(VirtualFrame frame, int top, BytecodeNode bn) {
        long c1 = popLong(frame, top - 1);
        checkNotZero(c1, AnnotatedVM.peekAnnotations(frame, top - 1), bn);
        long c2 = popLong(frame, top - 3);
        putLong(frame, top - 4, c2 % c1);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 3, analysis.lrem(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 3)));
    }

    public static void frem(VirtualFrame frame, int top) {
        float c1 = popFloat(frame, top - 1);
        float c2 = popFloat(frame, top - 2);
        putFloat(frame, top - 2, c2 % c1);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 2, analysis.frem(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 2)));
    }

    public static void drem(VirtualFrame frame, int top) {
        double c1 = popDouble(frame, top - 1);
        double c2 = popDouble(frame, top - 3);
        putDouble(frame, top - 4, c2 % c1);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 3, analysis.drem(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 3)));
    }


    public static void ishl(VirtualFrame frame, int top) {
        int c1 = popInt(frame, top - 1);
        int c2 = popInt(frame, top - 2);
        int concResult = c2 << c1;
        putInt(frame, top - 2, concResult);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 2, analysis.ishl(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 2)));
    }

    public static void lshl(VirtualFrame frame, int top) {
        int c1 = popInt(frame, top - 1);
        long c2 = popLong(frame, top - 2);
        putLong(frame, top - 3, c2 << c1);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 2, analysis.lshl(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 2)));
    }

    public static void ishr(VirtualFrame frame, int top) {
        int c1 = popInt(frame, top - 1);
        int c2 = popInt(frame, top - 2);
        int concResult = c2 >> c1;
        putInt(frame, top - 2, concResult);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 2, analysis.ishr(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 2)));
    }

    public static void lshr(VirtualFrame frame, int top) {
        int c1 = popInt(frame, top - 1);
        long c2 = popLong(frame, top - 2);
        putLong(frame, top - 3, c2 >> c1);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 2, analysis.lshr(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 2)));
    }

    public static void ineg(VirtualFrame frame, int top) {
        int c1 = popInt(frame, top - 1);
        int concResult = 0 - c1;
        putInt(frame, top - 1, concResult);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 1, analysis.ineg(c1,
                AnnotatedVM.popAnnotations(frame, top - 1)));
    }

    public static void lneg(VirtualFrame frame, int top) {
        long c1 = popLong(frame, top - 1);
        putLong(frame, top - 2, -c1);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 1, analysis.lneg(c1,
                AnnotatedVM.popAnnotations(frame, top - 1)));
    }

    public static void fneg(VirtualFrame frame, int top) {
        float c1 = popFloat(frame, top - 1);
        putFloat(frame, top - 1, -c1);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 1, analysis.fneg(c1,
                AnnotatedVM.popAnnotations(frame, top - 1)));
    }

    public static void dneg(VirtualFrame frame, int top) {
        double c1 = popDouble(frame, top - 1);
        putDouble(frame, top - 2, -c1);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 1, analysis.dneg(c1,
                AnnotatedVM.popAnnotations(frame, top - 1)));
    }

    public static void i2l(VirtualFrame frame, int top) {
        int c1 = popInt(frame, top - 1);
        putLong(frame, top - 1, c1);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top, analysis.i2l(c1,
                AnnotatedVM.popAnnotations(frame, top - 1)));
    }

    public static void i2f(VirtualFrame frame, int top) {
        float c1 = popInt(frame, top - 1);
        putFloat(frame, top - 1, c1);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 1, analysis.i2f(c1,
                AnnotatedVM.popAnnotations(frame, top - 1)));
    }

    public static void i2d(VirtualFrame frame, int top) {
        double c1 = popInt(frame, top - 1);
        putDouble(frame, top - 1, c1);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top, analysis.i2d(c1,
                AnnotatedVM.popAnnotations(frame, top - 1)));
    }

    public static void l2i(VirtualFrame frame, int top) {
        int c1 = (int) popLong(frame, top - 1);
        putInt(frame, top - 2, c1);
        if (analyze)
            AnnotatedVM.putAnnotations(frame, top - 2, analysis.l2i(c1,
                    AnnotatedVM.popAnnotations(frame, top - 1)));
    }

    public static void l2f(VirtualFrame frame, int top) {
        float c1 = (float) popLong(frame, top - 1);
        putFloat(frame, top - 2, c1);
        if (analyze)
            AnnotatedVM.putAnnotations(frame, top - 2, analysis.l2f(c1,
                    AnnotatedVM.popAnnotations(frame, top - 2)));
    }

    public static void l2d(VirtualFrame frame, int top) {
        double c1 = (double) popLong(frame, top - 1);
        putDouble(frame, top - 2, c1);
        if (analyze)
            AnnotatedVM.putAnnotations(frame, top - 1, analysis.l2d(c1,
                    AnnotatedVM.popAnnotations(frame, top - 2)));
    }

    public static void f2i(VirtualFrame frame, int top) {
        int c1 = (int) popFloat(frame, top - 1);
        putInt(frame, top - 1, c1);
        if (analyze)
            AnnotatedVM.putAnnotations(frame, top - 1, analysis.f2i(c1,
                    AnnotatedVM.popAnnotations(frame, top - 1)));

    }

    public static void f2l(VirtualFrame frame, int top) {
        long c1 = (long) popFloat(frame, top - 1);
        putLong(frame, top - 1, c1);
        if (analyze)
            AnnotatedVM.putAnnotations(frame, top, analysis.f2l(c1,
                    AnnotatedVM.popAnnotations(frame, top - 1)));
    }

    public static void f2d(VirtualFrame frame, int top) {
        double c1 = (double) popFloat(frame, top - 1);
        putDouble(frame, top - 1, c1);
        if (analyze)
            AnnotatedVM.putAnnotations(frame, top, analysis.f2d(c1,
                    AnnotatedVM.popAnnotations(frame, top - 1)));
    }

    public static void d2i(VirtualFrame frame, int top) {
        int c1 = (int) popDouble(frame, top - 1);
        putInt(frame, top - 2, c1);
        if (analyze)
            AnnotatedVM.putAnnotations(frame, top - 2, analysis.d2i(c1,
                    AnnotatedVM.popAnnotations(frame, top - 1)));
    }

    public static void d2l(VirtualFrame frame, int top) {
        long c1 = (long) popDouble(frame, top - 1);
        putLong(frame, top - 2, c1);
        if (analyze)
            AnnotatedVM.putAnnotations(frame, top - 1, analysis.d2l(c1,
                    AnnotatedVM.popAnnotations(frame, top - 1)));

    }

    public static void d2f(VirtualFrame frame, int top) {
        float c1 = (float) popDouble(frame, top - 1);
        putFloat(frame, top - 2, c1);
        if (analyze)
            AnnotatedVM.putAnnotations(frame, top - 2, analysis.d2f(c1,
                    AnnotatedVM.popAnnotations(frame, top - 1)));
    }

    public static void i2b(VirtualFrame frame, int top) {
        byte c1 = (byte) popInt(frame, top - 1);
        putInt(frame, top - 1, c1);
        if (analyze)
            AnnotatedVM.putAnnotations(frame, top - 1, analysis.i2b(c1,
                    AnnotatedVM.popAnnotations(frame, top - 1)));
    }

    public static void i2c(VirtualFrame frame, int top) {
        char c1 = (char) popInt(frame, top - 1);
        putInt(frame, top - 1, c1);
        if (analyze)
            AnnotatedVM.putAnnotations(frame, top - 1, analysis.i2c(c1,
                    AnnotatedVM.popAnnotations(frame, top - 1)));
    }

    public static void i2s(VirtualFrame frame, int top) {
        short c1 = (short) popInt(frame, top - 1);
        putInt(frame, top - 1, c1);
        if (analyze)
            AnnotatedVM.putAnnotations(frame, top - 1, analysis.i2s(c1,
                    AnnotatedVM.popAnnotations(frame, top - 1)));
    }


    public static void iushr(VirtualFrame frame, int top) {
        int c1 = popInt(frame, top - 1);
        int c2 = popInt(frame, top - 2);
        int concResult = c2 >>> c1;
        putInt(frame, top - 2, concResult);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 2, analysis.iushr(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 2)));
    }

    public static void lushr(VirtualFrame frame, int top) {
        int c1 = popInt(frame, top - 1);
        long c2 = popLong(frame, top - 2);
        putLong(frame, top - 3, c2 >>> c1);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 2, analysis.lushr(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 2)));
    }

    public static void iand(VirtualFrame frame, int top) {
        int c1 = popInt(frame, top - 1);
        int c2 = popInt(frame, top - 2);
        int concResult = c1 & c2;
        putInt(frame, top - 2, concResult);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 2, analysis.iand(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 2)));
    }

    public static void land(VirtualFrame frame, int top) {
        long c1 = popLong(frame, top - 1);
        long c2 = popLong(frame, top - 3);
        putLong(frame, top - 4, c1 & c2);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 3, analysis.land(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 3)));
    }

    public static void ior(VirtualFrame frame, int top) {
        int c1 = popInt(frame, top - 1);
        int c2 = popInt(frame, top - 2);
        int concResult = c1 | c2;
        putInt(frame, top - 2, concResult);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 2, analysis.ior(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 2)));
    }

    public static void lor(VirtualFrame frame, int top) {
        long c1 = popLong(frame, top - 1);
        long c2 = popLong(frame, top - 3);
        putLong(frame, top - 4, c1 | c2);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 3, analysis.lor(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 3)));
    }

    public static void ixor(VirtualFrame frame, int top) {
        int c1 = popInt(frame, top - 1);
        int c2 = popInt(frame, top - 2);
        int concResult = c1 ^ c2;
        putInt(frame, top - 2, concResult);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 2, analysis.ixor(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 2)));
    }

    public static void lxor(VirtualFrame frame, int top) {
        long c1 = popLong(frame, top - 1);
        long c2 = popLong(frame, top - 3);
        putLong(frame, top - 4, c1 ^ c2);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 3, analysis.lxor(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 3)));
    }

    // arrays

    public static void newArray(VirtualFrame frame, byte jvmPrimitiveType, int top, BytecodeNode bcn) {
        int length = popInt(frame, top - 1);
        StaticObject array = null;
        if (analyze) {
            Annotations a = AnnotatedVM.popAnnotations(frame, top - 1);
            if (a != null) {
                if (config.hasConcolicAnalysis()) {
                    config.getConcolicAnalysis().newArrayPathConstraint(
                            length, Annotations.annotation(a, config.getConcolicIdx()));
                }
                array = bcn.newPrimitiveArray(jvmPrimitiveType, length);
                Annotations[] annotations = new Annotations[length + 1];
                annotations[length] = a;
                array.setAnnotations(annotations);
            }
        }
        if (array == null) {
            array = bcn.newPrimitiveArray(jvmPrimitiveType, length);
        }
        BytecodeNode.putObject(frame, top - 1, array);
    }

    public static int newMultiArray(VirtualFrame frame, int top, Klass klass, int allocatedDimensions, BytecodeNode bcn) {
        assert klass.isArray();
        CompilerAsserts.partialEvaluationConstant(allocatedDimensions);
        CompilerAsserts.partialEvaluationConstant(klass);
        int[] dimensions = new int[allocatedDimensions];
        Annotations[] symDim = new Annotations[allocatedDimensions];
        int zeroDimOrError = allocatedDimensions;
        for (int i = 0; i < allocatedDimensions; ++i) {
            dimensions[i] = popInt(frame, top - allocatedDimensions + i);
            symDim[i] = AnnotatedVM.popAnnotations(frame, top - allocatedDimensions + i);
            if (analyze && config.hasConcolicAnalysis() && i < zeroDimOrError) {
                config.getConcolicAnalysis().newArrayPathConstraint(
                        dimensions[i], Annotations.annotation(symDim[i], config.getConcolicIdx()));
                if (dimensions[i] <= 0) {
                    zeroDimOrError = i;
                }
            }
        }

        StaticObject value = bcn.allocateMultiArray(frame, klass, dimensions);

        if (analyze) {
            annotateMultiArray(value, dimensions, symDim, zeroDimOrError, bcn);
        }

        BytecodeNode.putObject(frame, top - allocatedDimensions, value);
        return -allocatedDimensions; // Does not include the created (pushed) array.
    }

    @CompilerDirectives.TruffleBoundary
    private static void annotateMultiArray(StaticObject rootArray, int[] cDim, Annotations[] sDim, int bound, BytecodeNode bcn) {
        for (int i = 0; i < bound; ++i) {
            if (sDim[i] != null) {
                List<StaticObject> arrays = getAllArraysAtDepth(rootArray, i, EspressoLanguage.get(bcn));
                for (StaticObject arr : arrays) {
                    int dLength = cDim[i];
                    Annotations[] aArray = new Annotations[dLength + 1];
                    aArray[dLength] = sDim[i];
                    arr.setAnnotations(aArray);
                }
            }
        }
    }

    private static List<StaticObject> getAllArraysAtDepth(StaticObject array, int depth, EspressoLanguage lang) {
        if (depth == 0) {
            return Collections.singletonList(array);
        }
        List<StaticObject> lower = getAllArraysAtDepth(array, depth - 1, lang);
        List<StaticObject> arrays = new LinkedList<>();
        for (StaticObject a : lower) {
            arrays.addAll(Arrays.asList(a.unwrap(lang)));
        }
        return arrays;
    }

    public static void arrayLength(VirtualFrame frame, int top, StaticObject arr) {
        if (!analyze || !arr.hasAnnotations()) return;
        Annotations[] annotations = arr.getAnnotations();
        Annotations aLength = annotations[annotations.length - 1];
        AnnotatedVM.putAnnotations(frame, top - 1, aLength);
    }

    public static void getArrayAnnotations(VirtualFrame frame, StaticObject array,
                                           int cIndex, int fromIndexSlot, int toSlot, EspressoLanguage lang) {
        if (!analyze) return;

        Annotations aIndex = AnnotatedVM.popAnnotations(frame, fromIndexSlot);
        if (analyze && config.hasConcolicAnalysis()) {
            config.getConcolicAnalysis().checkArrayAccessPathConstraint(
                    array, cIndex, Annotations.annotation(aIndex, config.getConcolicIdx()), lang);
        }

        if (analyze && config.hasTaintAnalysis() && array.hasAnnotations()) {
            Annotations[] annotations = array.getAnnotations();
            Annotations aLength = annotations[annotations.length - 1];
            config.getTaintAnalysis().setArrayAccessInformationFlow(
                    Annotations.annotation(aIndex, config.getTaintIdx()),
                    Annotations.annotation(aLength, config.getTaintIdx())
            );
        }

        Annotations a = AnnotatedVM.getArrayAnnotations(array, cIndex);
        AnnotatedVM.putAnnotations(frame, toSlot, a);
    }

    public static void setArrayAnnotations(VirtualFrame frame, StaticObject array,
                                           int cIndex, int fromValueSlot, int fromIndexSlot, EspressoLanguage lang) {
        if (!analyze) return;

        Annotations aIndex = AnnotatedVM.popAnnotations(frame, fromIndexSlot);
        if (analyze && config.hasConcolicAnalysis()) {
            config.getConcolicAnalysis().checkArrayAccessPathConstraint(
                    array, cIndex, Annotations.annotation(aIndex, config.getConcolicIdx()), lang);
        }

        Annotations aValue = AnnotatedVM.popAnnotations(frame, fromValueSlot);
        AnnotatedVM.setArrayAnnotations(array, cIndex, aValue, lang);
    }

    // branching

    public static boolean takeBranchPrimitive1(VirtualFrame frame, int top, int opcode, BytecodeNode bcn, int bci) {

        assert IFEQ <= opcode && opcode <= IFLE;
        int c = popInt(frame, top - 1);

        boolean takeBranch = true;

        // @formatter:off
        switch (opcode) {
            case IFEQ:
                takeBranch = (c == 0);
                break;
            case IFNE:
                takeBranch = (c != 0);
                break;
            case IFLT:
                takeBranch = (c < 0);
                break;
            case IFGE:
                takeBranch = (c >= 0);
                break;
            case IFGT:
                takeBranch = (c > 0);
                break;
            case IFLE:
                takeBranch = (c <= 0);
                break;
            default:
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere("expecting IFEQ,IFNE,IFLT,IFGE,IFGT,IFLE");
        }

        if (analyze)
            analysis.takeBranchPrimitive1(frame, bcn, bci, opcode, takeBranch, AnnotatedVM.popAnnotations(frame, top - 1));
        return takeBranch;
    }

    public static boolean takeBranchPrimitive2(VirtualFrame frame, int top, int opcode, BytecodeNode bcn, int bci) {

        assert IF_ICMPEQ <= opcode && opcode <= IF_ICMPLE;
        int c1 = popInt(frame, top - 1);
        int c2 = popInt(frame, top - 2);
        
        // concrete
        boolean takeBranch = true;

        switch (opcode) {
            case IF_ICMPEQ:
                takeBranch = (c1 == c2);
                break;
            case IF_ICMPNE:
                takeBranch = (c1 != c2);
                break;
            case IF_ICMPLT:
                takeBranch = (c1 > c2);
                break;
            case IF_ICMPGE:
                takeBranch = (c1 <= c2);
                break;
            case IF_ICMPGT:
                takeBranch = (c1 < c2);
                break;
            case IF_ICMPLE:
                takeBranch = (c1 >= c2);
                break;
            default:
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere("non-branching bytecode");
        }

        if (analyze) analysis.takeBranchPrimitive2(frame, bcn, bci, opcode, takeBranch, c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 2));

        return takeBranch;
    }

    public static void tableSwitch(int concIndex, Annotations annotatedIndex, int low, int high,
                                   VirtualFrame frame, BytecodeNode bcn, int bci) {
        if (analyze) {
            analysis.tableSwitch(frame, bcn, bci, low, high, concIndex, annotatedIndex);
        }
    }

    public static void lookupSwitch(int key, Annotations annotatedKey,
                                    VirtualFrame frame, BytecodeNode bcn, int bci, int... vals) {
        if (analyze) {
            analysis.lookupSwitch(frame, bcn, bci, vals, key, annotatedKey);
        }
    }

    // --------------------------------------------------------------------------
    //
    // The String Library

    @CompilerDirectives.TruffleBoundary
    public static Object stringContains(StaticObject self, StaticObject s, Meta meta) {
        String concreteSelf = meta.toHostString(self);
        String other = meta.toHostString(s);
        boolean concreteRes = concreteSelf.contains(other);
        if (!self.hasAnnotations() && !s.hasAnnotations() || !analyze) {
            return concreteRes;
        }
        Annotations a =  analysis.stringContains(concreteSelf,
                other,
                Annotations.annotation(self.getAnnotations(), -1),
                Annotations.annotation(s.getAnnotations(), -1));
        return  new AnnotatedValue(concreteRes, a);
    }

    @CompilerDirectives.TruffleBoundary
    public static void stringConstructor(StaticObject self, StaticObject other, Meta meta) {
        Field[] fields = other.getKlass().getDeclaredFields();
        Field value = null, coder = null, hash = null;
        for (Field f : fields) {
            if (f.getNameAsString().equals("value")) {
                value = f;
            } else if (f.getNameAsString().equals("coder")) {
                coder = f;
            } else if (f.getNameAsString().equals("hash")) {
                hash = f;
            }
        }
        for (Field f : self.getKlass().getDeclaredFields()) {
            if (f.getNameAsString().equals("value")) {
                f.setObject(self, value.getObject(other).copy(meta.getContext()));
            } else if (f.getNameAsString().equals("coder")) {
                f.setByte(self, coder.getByte(other));
            } else if (f.getNameAsString().equals("hash")) {
                f.setInt(self, hash.getInt(other));
            }
        }
        if (other.hasAnnotations()) {
            self.setAnnotations(other.getAnnotations());
        }
    }

    @CompilerDirectives.TruffleBoundary
    public static Object stringCompareTo(StaticObject self, StaticObject other, Meta meta) {
        String cSelf = meta.toHostString(self);
        String cOther = meta.toHostString(other);
        int concreteResult = cSelf.compareTo(cOther);
        if (!analyze || !self.hasAnnotations() && !other.hasAnnotations()) {
            return concreteResult;
        }
        Annotations a = analysis.stringCompareTo(cSelf, cOther,
                Annotations.annotation(self.getAnnotations(), -1),
                Annotations.annotation(other.getAnnotations(), -1));
        return new AnnotatedValue(concreteResult, a);
    }

    @CompilerDirectives.TruffleBoundary
    public static Object stringLength(StaticObject self, Meta meta) {
        int length = meta.toHostString(self).length();
        if (!analyze || !self.hasAnnotations()) {
            return length;
        }
        Annotations aLength = analysis.stringLength(length, Annotations.annotation(self.getAnnotations(), -1));
        return aLength != null ? new AnnotatedValue(length, aLength) : length;
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject stringConcat(StaticObject self, StaticObject other, Meta meta) {
        String concreteSelf = meta.toHostString(self);
        String concreteOther = meta.toHostString(other);
        StaticObject result = meta.toGuestString(concreteSelf.concat(concreteOther));
        if (!analyze || !self.hasAnnotations() && !other.hasAnnotations()) {
            return result;
        }
        Annotations a = analysis.stringConcat(concreteSelf,
                concreteOther,
                Annotations.annotation(self.getAnnotations(), -1),
                Annotations.annotation(other.getAnnotations(), -1));
        return setStringAnnotations(result, a);

    }

    @CompilerDirectives.TruffleBoundary
    public static Object stringEquals(StaticObject self, StaticObject other, Meta meta) {
        String cSelf = meta.toHostString(self);
        String cOther = meta.toHostString(other);
        boolean areEqual = cSelf.equals(cOther);
        if (!analyze || !self.hasAnnotations() && !other.hasAnnotations()) {
            return areEqual;
        }
        AnnotatedValue av = new AnnotatedValue(areEqual, analysis.stringEquals(cSelf,
                cOther,
                getStringAnnotations(self),
                getStringAnnotations(other)));
        return av;
    }

    @CompilerDirectives.TruffleBoundary
    public static Object stringCharAt(StaticObject self, Object oIndex, Meta meta) {
        String cString = meta.toHostString(self);
        int index = AnnotatedValue.value(oIndex);
        Annotations sIndex = AnnotatedValue.svalue(oIndex);

        if (!self.hasAnnotations() && sIndex == null) {
            return (int) cString.charAt(index);
        }
        boolean sat1 = (0 <= index);
        boolean sat2 = (index < cString.length());

        if (!sat1 && sIndex == null) {
            // index is negative and cannot be influenced by a SMT solver
            self.setAnnotations(null);
            throw meta.throwExceptionWithMessage(meta.java_lang_StringIndexOutOfBoundsException, "Index is less than zero");
        }
        if (analyze) analysis.charAtPCCheck(cString, index, getStringAnnotations(self), sIndex);

        if (!sat2) {
            // index is greater than string length
            self.setAnnotations(null);
            throw meta.throwExceptionWithMessage(meta.java_lang_StringIndexOutOfBoundsException, "Index must be less than string length");
        }
        AnnotatedValue av = new AnnotatedValue((int) cString.charAt(index), Annotations.emptyArray());
        if (analyze) analysis.charAt(cString, index,getStringAnnotations(self), sIndex);
        return av;
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject substring(StaticObject self, Object begin, Meta meta) {
        int cbegin;
        Annotations sbegin = null;
        if(begin instanceof AnnotatedValue){
            cbegin = ((AnnotatedValue) begin).getValue();
            sbegin = (AnnotatedValue) begin;
        }else{
            cbegin = (int) begin;
        }
        try {
            String res = meta.toHostString(self).substring(cbegin);
            StaticObject result = meta.toGuestString(res);
            if(analyze && config.hasConcolicAnalysis()){
                config.getConcolicAnalysis().stringSubstring(result, self, cbegin, sbegin, meta);
            }
            return result;
        }catch (IndexOutOfBoundsException e){
            if(analyze && config.hasConcolicAnalysis()){
                config.getConcolicAnalysis().stringSubstring(null, self, cbegin, sbegin, meta);
            }
            meta.throwException(meta.java_lang_StringIndexOutOfBoundsException);
            throw e;
        }
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject substring(StaticObject self, Object begin, Object end, Meta meta) {
        int cBegin, cEnd;
        Annotations sBegin = null, sEnd = null;
        if(begin instanceof AnnotatedValue){
            cBegin = ((AnnotatedValue) begin).getValue();
            sBegin = (AnnotatedValue) begin;
        }else{
            cBegin = (int) begin;
        }
        if(end instanceof AnnotatedValue){
            cEnd = ((AnnotatedValue) end).getValue();
            sEnd = (AnnotatedValue) end;
        }else{
            cEnd = (int) end;
        }
        try {
            String res = meta.toHostString(self).substring(cBegin, cEnd);
            StaticObject result = meta.toGuestString(res);
            if(analyze && config.hasConcolicAnalysis()){
                config.getConcolicAnalysis().stringSubstring(result, self, cBegin, sBegin, cEnd, sEnd, meta);
            }
            return result;
        }catch (IndexOutOfBoundsException e){
            if(analyze && config.hasConcolicAnalysis()){
                config.getConcolicAnalysis().stringSubstring(null, self, cBegin, sBegin, cEnd, sEnd, meta);
            }
            meta.throwException(meta.java_lang_StringIndexOutOfBoundsException);
            throw e;
        }
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject stringToUpperCase(StaticObject self, Meta meta) {
        String host = meta.toHostString(self);
        StaticObject result = meta.toGuestString(host.toUpperCase());
        if (analyze && config.hasConcolicAnalysis() && self.hasAnnotations()) {
            initStringAnnotations(result);
            result = config.getConcolicAnalysis().stringToUpper(result, self, meta);
        }
        return result;
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject stringToLowerCase(StaticObject self, Meta meta) {
        String host = meta.toHostString(self);
        StaticObject result = meta.toGuestString(host.toLowerCase());
        if (analyze && config.hasConcolicAnalysis() && self.hasAnnotations()) {
            initStringAnnotations(result);
            result = config.getConcolicAnalysis().stringToLower(result, self, meta);
        }
        return result;
    }

    @CompilerDirectives.TruffleBoundary
    public static Object stringRegionMatches_ignoreCase(StaticObject self, Object ignoreCase, Object toffset, StaticObject other, Object ooffset, Object len, Meta meta) {
        boolean ignore = false;
        if (ignoreCase instanceof AnnotatedValue) {
            stopRecording("Cannot deal with symbolic ignore case for regionMatches yet", meta);
        } else {
            ignore = (boolean) ignoreCase;
        }
        int ctoffset = -1, cooffset = -1, clen = -1;
        if (toffset instanceof AnnotatedValue) {
            stopRecording("Cannot deal with symbolic toffset for regionMatches yet", meta);
        } else {
            ctoffset = (int) toffset;
        }
        if (ooffset instanceof AnnotatedValue) {
            stopRecording("Cannot deal with symbolic ooffset for regionMatches yet", meta);
        } else {
            cooffset = (int) ooffset;
        }
        if (len instanceof AnnotatedValue) {
            stopRecording("Cannot deal with symbolic len for regionMatches yet", meta);
        } else {
            clen = (int) len;
        }
        boolean isSelfSymbolic = self.hasAnnotations()
                && self.getAnnotations()[self.getAnnotations().length - 1].getAnnotations()[config.getConcolicIdx()] != null;
        boolean isOtherSymbolic = other.hasAnnotations()
                && other.getAnnotations()[self.getAnnotations().length - 1].getAnnotations()[config.getConcolicIdx()] != null;
        if ((isSelfSymbolic || isOtherSymbolic) && analyze && config.hasConcolicAnalysis()) {
            return config.getConcolicAnalysis().regionMatches(self, other, ignore, ctoffset, cooffset, clen, meta);
        } else {
            return meta.toHostString(self).regionMatches(ignore, ctoffset, meta.toHostString(other), cooffset, clen);
        }
    }

    @CompilerDirectives.TruffleBoundary
    public static Object stringBuxxCharAt(StaticObject self, Object index, Meta meta) {
        Method m = self.getKlass().lookupMethod(meta.getNames().getOrCreate("toString"), Symbol.Signature.java_lang_String);
        StaticObject stringValue = (StaticObject) m.invokeDirect(self);
        if (self.hasAnnotations()) {
            stringValue.setAnnotations(self.getAnnotations());
        }
        return stringCharAt(stringValue, index, meta);
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject stringBuXXAppendString(StaticObject self, StaticObject string, Meta meta) {
        if (analyze && config.hasConcolicAnalysis()) {
            self = config.getConcolicAnalysis().stringBuilderAppend(self, string, meta);
        }
        Annotations[] selfAnnotations = self.getAnnotations();
        Annotations[] stringAnnotations = string.getAnnotations();
        self.setAnnotations(null);
        string.setAnnotations(null);
        Method m = self.getKlass().getSuperKlass().lookupMethod(meta.getNames().getOrCreate("append"), Signature.java_lang_AbstractStringBuilder_java_lang_String);
        m.invokeDirect(self, string);
        self.setAnnotations(selfAnnotations);
        string.setAnnotations(stringAnnotations);
        return self;
    }

    @CompilerDirectives.TruffleBoundary
    public static Object stringBuxxLength(StaticObject self, Meta meta) {
        Method m = self.getKlass().getSuperKlass().lookupMethod(meta.getNames().getOrCreate("length"), Symbol.Signature._int);
        Object[] o = self.getAnnotations();
        Annotations [] a = self.getAnnotations();
        self.setAnnotations(null);
        Object invokeResult = m.invokeDirect(self);
        self.setAnnotations(a);
        int cresult = (int) invokeResult;
        if (analyze && self.hasAnnotations() && config.hasConcolicAnalysis()) {
            AnnotatedValue av = new AnnotatedValue(cresult, Annotations.emptyArray());
            return config.getConcolicAnalysis().stringBufferLength(av, self, meta);
        }
        return cresult;
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject stringBuxxToString(StaticObject self, Meta meta) {
        Klass abstractSB = self.getKlass().getSuperKlass();
        Method getValue = abstractSB.lookupMethod(meta.getNames().getOrCreate("getValue"), Signature._byte_array);
        Method length = abstractSB.lookupMethod(meta.getNames().getOrCreate("length"), Signature._int);
        Method isLatin = abstractSB.lookupMethod(meta.getNames().getOrCreate("isLatin1"), Signature._boolean);
        StaticObject bytes = (StaticObject) getValue.invokeDirect(self);
        Object xlength = length.invokeDirect(self);
        int ilength;
        if (xlength instanceof AnnotatedValue) {
            ilength = ((AnnotatedValue) xlength).getValue();
        } else {
            ilength = (int) xlength;
        }
        StaticObject result =
                // FIXME: Not sure if annotations should be able to reach here?
                (boolean) AnnotatedValue.value(isLatin.invokeDirect(self))
                        ?
                        (StaticObject) meta.java_lang_StringLatin1_newString.invokeDirect(self, bytes, 0, ilength)
                        :
                        (StaticObject) meta.java_lang_StringUTF16_newString.invokeDirect(self, bytes, 0, ilength);
        if (analyze && config.hasConcolicAnalysis()) {
            result = config.getConcolicAnalysis().stringBuilderToString(result, self, meta);
        }
        return result;
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject stringBuxxInsert(StaticObject self, Object offset, Object toInsert, Meta meta) {
        if (toInsert instanceof AnnotatedValue) {
            stopRecording("Cannot insert symbolic chars to StringBuffer", meta);
        }
        StaticObject toInsertCasted = meta.toGuestString(String.valueOf((char) toInsert));
        return stringBuxxInsert(self, offset, toInsertCasted, meta);
    }

    public static void initStringBuxxString(StaticObject self, StaticObject other, Meta meta) {
        Method m = self.getKlass().getSuperKlass().lookupMethod(Symbol.Name._init_, Signature._void_String);
        Annotations[] selfAnnotations = self.getAnnotations();
        Annotations[] stringAnnotations = other.getAnnotations();
        self.setAnnotations(null);
        other.setAnnotations(null);
        m.invokeDirect(self, other);
        self.setAnnotations(selfAnnotations);
        other.setAnnotations(stringAnnotations);
        if(analyze && config.hasConcolicAnalysis()){
            config.getConcolicAnalysis().stringBuilderAppend(self, other, meta);
        }
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject stringBuxxInsert(StaticObject self, Object offset, StaticObject toInsert, Meta meta) {
        if (offset instanceof AnnotatedValue) {
            SPouT.stopRecording("Cannot handle symbolic offset values for insert into StringBu* yet.", meta);
        }
        int concreteOffset = (int) offset;
        if (analyze && config.hasConcolicAnalysis()) {
            config.getConcolicAnalysis().stringBuilderInsert(self, concreteOffset, toInsert, meta);
        }
        Method m = self.getKlass().getSuperKlass().lookupMethod(meta.getNames().getOrCreate("insert"), Signature.AbstractStringBuilder_int_String);
        return (StaticObject) m.invokeDirect(self, concreteOffset, toInsert);
    }

    @CompilerDirectives.TruffleBoundary
    public static void stringBuxxGetChars(StaticObject self, Object srcBegin, Object srcEnd, StaticObject dst, Object dstBegin, Meta meta) {
        if (srcBegin instanceof AnnotatedValue
                || srcEnd instanceof AnnotatedValue
                || config.hasConcolicAnalysis() && config.getConcolicAnalysis().hasConcolicStringAnnotations(dst)
                || dstBegin instanceof AnnotatedValue
                || config.hasConcolicAnalysis() && config.getConcolicAnalysis().hasConcolicStringAnnotations(self)) {
            SPouT.stopRecording("symbolic getChars is not supported", meta);
        }
        Method m = self.getKlass().getSuperKlass().lookupMethod(meta.getNames().getOrCreate("getChars"), Signature._void_int_int_char_array_int);
        m.invokeDirect(self, srcBegin, srcEnd, dst, dstBegin);
    }

    public static void setBuxxCharAt(StaticObject self, Object i, Object ch, Meta meta) {
        if(i instanceof AnnotatedValue ||
        ch instanceof AnnotatedValue){
            stopRecording("Symbolic index and symbolic chars are not supported", meta);
        }
        int index = (int) i;
        char cha = (char) ch;
        String val = String.valueOf(cha);
        Method m = self.getKlass().getSuperKlass().lookupMethod(meta.getNames().getOrCreate("setCharAt"), Signature._void_int_char);
        if (analyze && config.hasConcolicAnalysis() && index >= 0) {
            config.getConcolicAnalysis().stringBuilderCharAt(self, index, val, meta);
        }
        Annotations[] a = self.getAnnotations();
        self.setAnnotations(null);
        m.invokeDirect(self, i, ch);
        self.setAnnotations(a);
    }

    public static Object characterToUpperCase(Object c, Meta meta) {
        char cChar;
        Annotations sChar = null;
        if(c instanceof AnnotatedValue){
            cChar = ((AnnotatedValue) c).getValue();
            sChar = (AnnotatedValue) c;
        }else{
            cChar = (char) c;
        }

        char cRes = Character.toUpperCase(cChar);
        if(analyze && config.hasConcolicAnalysis()){
            return config.getConcolicAnalysis().characterToUpperCase(cRes, sChar);
        }
        return cRes;
    }

    public static Object characterToLowerCase(Object c, Meta meta) {
        char cChar;
        Annotations sChar = null;
        if(c instanceof AnnotatedValue){
            cChar = ((AnnotatedValue) c).getValue();
            sChar = (AnnotatedValue) c;
        }else{
            cChar = (char) c;
        }

        char cRes = Character.toLowerCase(cChar);
        if(analyze && config.hasConcolicAnalysis()){
            return config.getConcolicAnalysis().characterToLowerCase(cRes, sChar);
        }
        return cRes;
    }

    public static Object isCharDefined(Object c, Meta meta) {
        char cChar;
        Annotations sChar = null;
        if(c instanceof AnnotatedValue){
            cChar = ((AnnotatedValue) c).getValue();
            sChar = (AnnotatedValue) c;
        }else{
            cChar = (char) c;
        }
        Object res  = Character.isDefined(cChar);
        if(analyze && config.hasConcolicAnalysis()){
            res = config.getConcolicAnalysis().characterIsDefined((boolean) res, cChar, sChar, meta);
        }
        return res;
    }

    @CompilerDirectives.TruffleBoundary
    public static Object split(StaticObject self, StaticObject regex, Meta meta) {
        boolean isSelfSymbolic = self.hasAnnotations()
                && self.getAnnotations()[self.getAnnotations().length - 1].getAnnotations()[config.getConcolicIdx()] != null;
        boolean isRegexSymbolic = regex.hasAnnotations()
                && regex.getAnnotations()[self.getAnnotations().length - 1].getAnnotations()[config.getConcolicIdx()] != null;

        if (isSelfSymbolic || isRegexSymbolic) {
            stopRecording("Cannot split symbolic strings yet", meta);
        }
        String s = meta.toHostString(self);
        String r = meta.toHostString(regex);
        String[] res = s.split(r);
        StaticObject[] resSO = new StaticObject[res.length];
        for (int i = 0; i < res.length; i++) {
            resSO[i] = meta.toGuestString(res[i]);
        }
        return StaticObject.createArray(self.getKlass().getArrayClass(), resSO, meta.getContext());
    }

    public static StaticObject valueOf_bool(Object v, Meta meta) {
        if (v instanceof AnnotatedValue) {
            stopRecording("concolic type conversion from boolean to string not supported, yet.", meta);
        }
        String ret = "" + (boolean) v;
        return meta.toGuestString(ret);
    }

    public static StaticObject valueOf_byte(Object v, Meta meta) {
        if (v instanceof AnnotatedValue) {
            stopRecording("concolic type conversion from byte to string not supported, yet.", meta);
        }
        String ret = "" + (byte) v;
        return meta.toGuestString(ret);
    }

    public static StaticObject valueOf_char(Object v, Meta meta) {
        if (v instanceof AnnotatedValue) {
            stopRecording("concolic type char conversion to string not supported, yet.", meta);
        }
        String ret = "" + (char) v;
        return meta.toGuestString(ret);
    }

    public static StaticObject valueOf_char_array(StaticObject v, Meta meta) {
        if (hasConcolicAnnotations(v)) {
            stopRecording("concolic type char array conversion to string not supported, yet.", meta);
        }
        char[] value = v.unwrap(meta.getLanguage());
        return meta.toGuestString(new String(value));
    }

    public static StaticObject valueOf_char_array(StaticObject v, Object offset, Object count, Meta meta) {
        if (hasConcolicAnnotations(v) || offset instanceof AnnotatedValue || count instanceof AnnotatedValue) {
            stopRecording("concolic type char array conversion to string not supported, yet.", meta);
        }
        int coffset = (int) offset;
        int ccount = (int) count;
        char[] value = v.unwrap(meta.getLanguage());
        return meta.toGuestString(new String(Arrays.copyOfRange(value, coffset, coffset + ccount)));
    }

    public static StaticObject valueOf_short(Object v, Meta meta) {
        if (v instanceof AnnotatedValue) {
            stopRecording("concolic type conversion from short to string not supported, yet.", meta);
        }
        String ret = "" + (short) v;
        return meta.toGuestString(ret);
    }

    public static StaticObject valueOf_int(Object v, Meta meta) {
        if (v instanceof AnnotatedValue && config.hasConcolicAnalysis() && Annotations.annotation((Annotations) v, config.getConcolicIdx()) != null) {
            stopRecording("concolic type conversion from int to string not supported, yet.", meta);
        }

        String ret = "" + (int) AnnotatedValue.value(v);
        return meta.toGuestString(ret);
    }

    public static StaticObject valueOf_long(Object v, Meta meta) {
        if (v instanceof AnnotatedValue) {
            stopRecording("concolic type conversion from long to string not supported, yet.", meta);
        }
        String ret = "" + (long) v;
        return meta.toGuestString(ret);
    }

    public static StaticObject valueOf_float(Object v, Meta meta) {
        if (v instanceof AnnotatedValue) {
            stopRecording("concolic type conversion from float to string not supported, yet.", meta);
        }
        String ret = "" + (float) v;
        return meta.toGuestString(ret);
    }

    public static StaticObject valueOf_double(Object v, Meta meta) {
        if (v instanceof AnnotatedValue && config.hasConcolicAnalysis() && Annotations.annotation((Annotations) v, config.getConcolicIdx()) != null) {
            stopRecording("concolic type conversion from double to string not supported, yet.", meta);
        }
        String ret = "" + (double) AnnotatedValue.value(v);
        return meta.toGuestString(ret);
    }

    // Numeric Classes

    @CompilerDirectives.TruffleBoundary
    public static double parseDouble(StaticObject s, Meta meta) {
        if (analyze && config.hasConcolicAnalysis() && config.getConcolicAnalysis().hasConcolicStringAnnotations(s)) {
            stopRecording("Concolic type conversion from string to double is not supported", meta);
        }
        return Double.parseDouble(meta.toHostString(s));
    }

    @CompilerDirectives.TruffleBoundary
    public static float parseFloat(StaticObject s, Meta meta) {
        if (analyze && config.hasConcolicAnalysis() && config.getConcolicAnalysis().hasConcolicStringAnnotations(s)) {
            stopRecording("Concolic type conversion from string to float is not supported", meta);
        }
        return Float.parseFloat(meta.toHostString(s));
    }
    // --------------------------------------------------------------------------
    //
    // helpers

    @CompilerDirectives.TruffleBoundary
    public static void debug(String message) {
        if (DEBUG) System.out.println("[debug] " + message);
    }

    @CompilerDirectives.TruffleBoundary
    public static void debug(String message, Object o) {
        if (DEBUG) System.out.println("[debug] %s with Object %s".formatted(message, o));
    }

    @CompilerDirectives.TruffleBoundary
    public static void debug(String method, Expression a1, Expression a2) {
        if (DEBUG) System.out.println("[debug] bytecode: %s a1: %s a2: %s".formatted(method, a1, a2));
    }

    @CompilerDirectives.TruffleBoundary
    public static void debug(String method, int slot, Annotations a) {
        if (DEBUG) System.out.println("[debug] bytecode: %s slot: %s a1: %s ".formatted(method, slot, a));
    }

    @CompilerDirectives.TruffleBoundary
    public static void debug(String method, int slot, int slot2, Annotations a) {
        if (DEBUG)
            System.out.println("[debug] bytecode: %s slot: %s  slot2: %s a1: %s ".formatted(method, slot, slot2, a));
    }

    @CompilerDirectives.TruffleBoundary
    public static void log(String message) {
        System.out.println(message);
    }

    public static void addToTrace(TraceElement element) {
        if (analyze) trace.addElement(element);
    }

    public static StaticObject initStringAnnotations(StaticObject target) {
        int lengthAnnotations = ((ObjectKlass) target.getKlass()).getFieldTable().length + 1;
        Annotations[] annotations = new Annotations[lengthAnnotations];
        for (int i = 0; i < annotations.length; i++) {
            annotations[i] = Annotations.emptyArray();
        }
        target.setAnnotations(annotations);
        return target;
    }

    private static StaticObject setStringAnnotations(StaticObject self, Annotations a) {
        Annotations[] stringAnnotation = self.getAnnotations();
        if (stringAnnotation == null) {
            SPouT.initStringAnnotations(self);
            stringAnnotation = self.getAnnotations();
        }
        stringAnnotation[stringAnnotation.length - 1] = a;
        self.setAnnotations(stringAnnotation);
        return self;
    }

    private static Annotations getStringAnnotations(StaticObject self){
        return  Annotations.annotation(self.getAnnotations(), -1);
    }


    private static boolean hasConcolicAnnotations(StaticObject v) {
        return v.hasAnnotations() && config.hasConcolicAnalysis() && v.getAnnotations()[config.getConcolicIdx()] != null;
    }

    public static void makeConcatWithConstantsSymbolically(Object result, Object[] args, Meta meta) {
        if (analyze && config.hasConcolicAnalysis()) {
            config.getConcolicAnalysis().makeConcatWithConstants((StaticObject) result, args, meta);
        }
    }

    public static void pauseAnalyze() {
        oldAnalyze = analyze;
        analyze = false;
    }

    public static void resumeAnalyze() {
        analyze = oldAnalyze;
    }
}
