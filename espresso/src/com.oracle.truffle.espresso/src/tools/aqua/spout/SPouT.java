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
import tools.aqua.concolic.SymTaint;
import tools.aqua.concolic.SymbolDeclaration;
import tools.aqua.smt.ComplexExpression;
import tools.aqua.smt.Constant;
import tools.aqua.smt.Expression;
import tools.aqua.smt.OperatorComparator;
import tools.aqua.smt.Types;
import tools.aqua.smt.Variable;
import tools.aqua.taint.ColorUtil;
import tools.aqua.taint.PostDominatorAnalysis;
import tools.aqua.taint.Taint;
import tools.aqua.witnesses.GWIT;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static com.oracle.truffle.espresso.bytecode.Bytecodes.*;
import static com.oracle.truffle.espresso.nodes.BytecodeNode.*;
import static com.oracle.truffle.espresso.runtime.dispatch.EspressoInterop.getMeta;

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
    public static void stopRecordingWithoutMeta(String s) {
        log(s);
        trace.addElement(new ExceptionalEvent(s));
        stopAnalysis();
        Meta m = getMeta();
        throw m.throwExceptionWithMessage(m.java_lang_RuntimeException, s);
    }


    @CompilerDirectives.TruffleBoundary
    public static void assume(Object condition, Meta meta) {
        if (!analyze || !config.hasConcolicAnalysis()) {
            // FIXME: dont care about assumptions outside of concolic analysis?
            return;
        }

        if(condition instanceof AnnotatedValue && config.hasConcolicAnalysis()){
            config.getConcolicAnalysis().assume(AnnotatedValue.value(condition),
                    Annotations.annotation(AnnotatedValue.svalue(condition),config.getConcolicIdx()));
        }
        if (!((boolean) AnnotatedValue.value(condition))) {
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

    public static void taintObject(StaticObject o, int color) {
        if (!analyze || !config.hasTaintAnalysis()) return;
        config.getTaintAnalysis().taintObject(o, color);
    }

    @CompilerDirectives.TruffleBoundary
    public static void checkTaint(Object o, int color) {
        if (analyze && config.hasConcolicAnalysis()) {
            Object conc = AnnotatedValue.value(o);
            Expression symb = Annotations.annotation(AnnotatedValue.svalue(o), config.getConcolicIdx());
            Expression e = null;
            Variable v = null;
            if (conc instanceof Boolean) {
                v = new Variable(Types.BOOL, -color);
                e = new ComplexExpression(OperatorComparator.BEQUIV, v, symb != null ?
                        symb : Constant.fromConcreteValue((boolean) o));
            } else if (conc instanceof Byte) {
                v = new Variable(Types.BYTE, -color);
                e = new ComplexExpression(OperatorComparator.BVEQ, v, symb != null ?
                        symb : Constant.fromConcreteValue((byte) o));
            } else if (conc instanceof Short) {
                v = new Variable(Types.SHORT, -color);
                e = new ComplexExpression(OperatorComparator.BVEQ, v, symb != null ?
                        symb : Constant.fromConcreteValue((short) o));
            } else if (conc instanceof Character) {
                v = new Variable(Types.CHAR, -color);
                e = new ComplexExpression(OperatorComparator.BVEQ, v, symb != null ?
                        symb : Constant.fromConcreteValue((char) o));
            } else if (conc instanceof Integer) {
                v = new Variable(Types.INT, -color);
                e = new ComplexExpression(OperatorComparator.BVEQ, v, symb != null ?
                        symb : Constant.fromConcreteValue((int) o));
            } else if (conc instanceof Float) {
                v = new Variable(Types.FLOAT, -color);
                e = new ComplexExpression(OperatorComparator.BVEQ, v, symb != null ?
                        symb : Constant.fromConcreteValue((float) o));
            } else if (conc instanceof Long) {
                v = new Variable(Types.LONG, -color);
                e = new ComplexExpression(OperatorComparator.BVEQ, v, symb != null ?
                        symb : Constant.fromConcreteValue((long) o));
            } else if (conc instanceof Double) {
                v = new Variable(Types.DOUBLE,-color);
                e = new ComplexExpression(OperatorComparator.FPEQ, v, symb != null ?
                        symb : Constant.fromConcreteValue((double) o));
            } else {
                log("unsupported symbolic taint check!");
            };
            if (e != null) {
                addToTrace(new SymbolDeclaration(v));
                addToTrace(new SymTaint(e));
            }
        }
        if (analyze && config.hasTaintAnalysis()) {
            config.getTaintAnalysis().checkTaint(o instanceof AnnotatedValue ? (AnnotatedValue) o : null, color);
        }
    }

    @CompilerDirectives.TruffleBoundary
    public static void checkTaintObject(StaticObject o, int color, Meta meta) {
        if (analyze && config.hasConcolicAnalysis()) {
            Expression symb = Annotations.annotation(Annotations.objectAnnotation(o), config.getConcolicIdx());
            Expression e = null;
            Variable v = null;
            if (o.isString()) {
                v = new Variable(Types.STRING, -color);
                e = new ComplexExpression(OperatorComparator.STRINGEQ, v, symb != null ?
                        symb : Constant.fromConcreteValue( meta.toHostString(o)));
            } else {
                log("unsupported symbolic taint check!");
            };
            if (e != null) {
                addToTrace(new SymbolDeclaration(v));
                addToTrace(new SymTaint(e));
            }
        }
        if (analyze && config.hasTaintAnalysis()) {
            config.getTaintAnalysis().checkTaintObject(o, color);
        }
    }

    public static void nextBytecode(VirtualFrame frame, BytecodeNode bcn, int bci) {
        if (!analyze || !config.analyzeControlFlowTaint()) return;
        config.getTaintAnalysis().informationFlowNextBytecode(frame, bcn, bci);
    }

    public static void informationFlowMethodReturn(VirtualFrame frame) {
        if (!analyze || !config.analyzeControlFlowTaint()) return;
        config.getTaintAnalysis().informationFlowMethodReturn(frame);
    }

    public static void informationFlowEnterBlockWithHandler(VirtualFrame frame, BytecodeNode bcn, int bci) {
        if (!analyze || !config.analyzeControlFlowTaint()) return;
        config.getTaintAnalysis().informationFlowEnterBlockWithHandler(frame, bcn, bci);
    }

    public static void iflowRegisterException() {
        // TODO: should we check for type and log all assertion violations (even when caught?)
        if (!analyze || !config.analyzeControlFlowTaint()) return;
        config.getTaintAnalysis().iflowRegisterException();
    }

    public static void iflowUnregisterException(VirtualFrame frame, BytecodeNode bcn, int bci) {
        if (!analyze || !config.analyzeControlFlowTaint()) return;
        config.getTaintAnalysis().iflowUnregisterException(frame, bcn, bci);
    }

    public static int iflowGetIpdBCI() {
        if (!analyze || !config.analyzeControlFlowTaint()) return -1;
        return config.getTaintAnalysis().iflowGetIpdBCI();
    }

    public static void markWithIFTaint(VirtualFrame frame, int top){
        if(!analyze || !config.analyzeControlFlowTaint()) return;
        Taint t = config.getTaintAnalysis().getIfTaint();
        if (t == null) return;
        Annotations a = AnnotatedVM.popAnnotations(frame, top);
        if (a == null) {
            a = new Annotations();
        }
        a.set(config.getTaintIdx(), ColorUtil.joinColors(t, Annotations.annotation(a, config.getTaintIdx())));
        AnnotatedVM.putAnnotations(frame, top, a);
    }

    public static void markObjectWithIFTaint(StaticObject obj) {
        if(!analyze || !config.analyzeControlFlowTaint()) return;
        if (obj == StaticObject.NULL) return;
        Taint t = config.getTaintAnalysis().getIfTaint();
        if (t == null) return;

        if (obj.isArray()) {
            log("Warning: currently not marking array in markObjectWithIFTaint.");
            return;
        }
        else if (!obj.hasAnnotations()) {
            Annotations.initObjectAnnotations(obj);
            Annotations a = new Annotations();
            a.set(config.getTaintIdx(), t);
            Annotations.setObjectAnnotation(obj, a);
        }
        else {
            Annotations a = Annotations.objectAnnotation(obj);
            if (a == null) {
                a = new Annotations();
                Annotations.setObjectAnnotation(obj, a);
            }
            a.set(config.getTaintIdx(), ColorUtil.joinColors(t, Annotations.annotation(a, config.getTaintIdx())));
        }
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

    public static void idiv(VirtualFrame frame, int top, BytecodeNode bn, int bci) {
        int c1 = popInt(frame, top - 1);
        Annotations a1 = null;
        Annotations a2 = null;
        // annotations must be cleared before exception!
        if (analyze) {
            a1 = AnnotatedVM.popAnnotations(frame, top - 1);
            a2 = AnnotatedVM.popAnnotations(frame, top - 2);
        }
        checkNotZero(frame, c1, a1, bn, bci);
        int c2 = popInt(frame, top - 2);
        putInt(frame, top - 2, c2 / c1);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 2, analysis.idiv(c1, c2, a1, a2));
    }

    public static void ldiv(VirtualFrame frame, int top, BytecodeNode bn, int bci) {
        long c1 = popLong(frame, top - 1);
        Annotations a1 = null;
        Annotations a2 = null;
        // annotations must be cleared before exception!
        if (analyze) {
            a1 = AnnotatedVM.popAnnotations(frame, top - 1);
            a2 = AnnotatedVM.popAnnotations(frame, top - 3);
        }
        checkNotZero(frame, c1, a1, bn, bci);
        long c2 = popLong(frame, top - 3);
        putLong(frame, top - 4, c2 / c1);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 3, analysis.ldiv(c1, c2, a1, a2));
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


    private static void checkNotZero(VirtualFrame frame, int c1, Annotations a, BytecodeNode bcn, int bci) {
        if (analyze) {
            analysis.checkNotZeroInt(frame, bcn, bci, c1 == 0, a);
        }
        if (c1 == 0) {
            bcn.enterImplicitExceptionProfile();
            Meta meta = bcn.getMeta();
            StaticObject message = meta.toGuestString("/ by zero");
            if (analyze && config.hasTaintAnalysis()) {
                Annotations.initObjectAnnotations(message);
                Annotations aNew = new Annotations();
                aNew.set(config.getTaintIdx(), config.getTaintAnalysis().getIfTaint());
                Annotations.setObjectAnnotation(message, aNew);
            }
            SPouT.iflowRegisterException();
            throw meta.throwExceptionWithMessage(meta.java_lang_ArithmeticException, message);
        }
    }

    private static void checkNotZero(VirtualFrame frame, long c1, Annotations a, BytecodeNode bcn, int bci) {
        if (analyze) {
            analysis.checkNotZeroInt(frame, bcn, bci, c1 == 0L, a);
        }
        if (c1 == 0L) {
            bcn.enterImplicitExceptionProfile();
            Meta meta = bcn.getMeta();
            StaticObject message = meta.toGuestString("/ by zero");
            if (analyze && config.hasTaintAnalysis()) {
                Annotations.initObjectAnnotations(message);
                Annotations aNew = new Annotations();
                aNew.set(config.getTaintIdx(), config.getTaintAnalysis().getIfTaint());
                Annotations.setObjectAnnotation(message, aNew);
            }
            SPouT.iflowRegisterException();
            throw meta.throwExceptionWithMessage(meta.java_lang_ArithmeticException, message);
        }
    }

    public static void irem(VirtualFrame frame, int top, BytecodeNode bn, int bci) {
        int c1 = popInt(frame, top - 1);
        checkNotZero(frame, c1, AnnotatedVM.peekAnnotations(frame, top - 1), bn, bci);
        int c2 = popInt(frame, top - 2);
        putInt(frame, top - 2, c2 % c1);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 2, analysis.irem(c2, c1,
                AnnotatedVM.popAnnotations(frame, top - 2),
                AnnotatedVM.popAnnotations(frame, top - 1)));
    }

    public static void lrem(VirtualFrame frame, int top, BytecodeNode bn, int bci) {
        long c1 = popLong(frame, top - 1);
        checkNotZero(frame, c1, AnnotatedVM.peekAnnotations(frame, top - 1), bn, bci);
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

                // TODO: implement new array iflow
            }
        }
        if (array == null) {
            array = bcn.newPrimitiveArray(jvmPrimitiveType, length);
        }
        BytecodeNode.putObject(frame, top - 1, array);
    }

    public static void anewArray(VirtualFrame frame, Klass klassArrayType, int top, BytecodeNode bcn) {
        int length = popInt(frame, top - 1);
        StaticObject array = null;
        if (analyze) {
            Annotations a = AnnotatedVM.popAnnotations(frame, top - 1);
            if (a != null) {
                if (config.hasConcolicAnalysis()) {
                    config.getConcolicAnalysis().newArrayPathConstraint(
                            length, Annotations.annotation(a, config.getConcolicIdx()));
                }
                array = bcn.newReferenceArray(klassArrayType, length);
                Annotations[] annotations = new Annotations[length + 1];
                annotations[length] = a;
                array.setAnnotations(annotations);
            }

            // TODO: implement new array iflow
        }
        if (array == null) {
            array = bcn.newReferenceArray(klassArrayType, length);
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

    public static void getArrayAnnotations(VirtualFrame frame, BytecodeNode bcn, int bci, StaticObject array,
                                           int cIndex, int fromIndexSlot, int toSlot, EspressoLanguage lang) {
        if (!analyze) return;

        Annotations aIndex = AnnotatedVM.popAnnotations(frame, fromIndexSlot);
        if (analyze && config.hasConcolicAnalysis()) {
            config.getConcolicAnalysis().checkArrayAccessPathConstraint(
                    array, cIndex, Annotations.annotation(aIndex, config.getConcolicIdx()), lang);
        }

        if (analyze && config.hasTaintAnalysis() && array.hasAnnotations()) {
            boolean fails = cIndex < 0 || cIndex >= array.length(lang);
            Annotations[] annotations = array.getAnnotations();
            Annotations aLength = annotations[annotations.length - 1];
            config.getTaintAnalysis().setArrayAccessInformationFlow(
                    frame, bcn, bci, fails,
                    Annotations.annotation(aIndex, config.getTaintIdx()),
                    Annotations.annotation(aLength, config.getTaintIdx())
            );
        }

        Annotations a = AnnotatedVM.getArrayAnnotations(array, cIndex);
        AnnotatedVM.putAnnotations(frame, toSlot, a);
    }

    public static void setArrayAnnotations(VirtualFrame frame, BytecodeNode bcn, int bci, StaticObject array,
                                           int cIndex, int fromValueSlot, int fromIndexSlot, EspressoLanguage lang) {
        if (!analyze) return;

        Annotations aIndex = AnnotatedVM.popAnnotations(frame, fromIndexSlot);
        if (analyze && config.hasConcolicAnalysis()) {
            config.getConcolicAnalysis().checkArrayAccessPathConstraint(
                    array, cIndex, Annotations.annotation(aIndex, config.getConcolicIdx()), lang);
        }

        if (analyze && config.hasTaintAnalysis() && array.hasAnnotations()) {
            boolean fails = cIndex < 0 || cIndex >= array.length(lang);
            Annotations[] annotations = array.getAnnotations();
            Annotations aLength = annotations[annotations.length - 1];
            config.getTaintAnalysis().setArrayAccessInformationFlow(
                    frame, bcn, bci, fails,
                    Annotations.annotation(aIndex, config.getTaintIdx()),
                    Annotations.annotation(aLength, config.getTaintIdx())
            );
        }

        Annotations aValue = AnnotatedVM.popAnnotations(frame, fromValueSlot);
        AnnotatedVM.setArrayAnnotations(array, cIndex, aValue, lang);
    }

    // branching

    public static void checkcast(VirtualFrame frame, StaticObject obj,BytecodeNode bcn, int bci, boolean cast) {
        if (!analyze) return;
        Annotations a = Annotations.objectAnnotation(obj);
        analysis.checkcast(frame, bcn, bci, cast, a);
    }

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

    public static boolean takeBranchRef2(VirtualFrame frame, BytecodeNode bcn, int bci, StaticObject operand1, StaticObject operand2, int opcode) {
        assert IF_ACMPEQ <= opcode && opcode <= IF_ACMPNE;
        boolean result;
        // @formatter:off
        switch (opcode) {
            case IF_ACMPEQ : result =  operand1 == operand2; break;
            case IF_ACMPNE : result =  operand1 != operand2; break;
            default        :
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("expecting IF_ACMPEQ,IF_ACMPNE");
        }
        // @formatter:on

        if (analyze) {
            analysis.takeBranchRef2(frame, bcn, bci, opcode, result, operand1, operand2,
                    Annotations.objectAnnotation(operand1), Annotations.objectAnnotation(operand2));
        }

        return result;
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
    // Objects

    public static void instanceOf(VirtualFrame frame, StaticObject object, boolean isInstance, int top) {
        if (!analyze || !object.hasAnnotations()) return;
        Annotations a = analysis.instanceOf(object, Annotations.objectAnnotation(object), isInstance);
        AnnotatedVM.putAnnotations(frame, top, a);
    }

    public static void isNull(VirtualFrame frame, StaticObject object, boolean isNull, int top) {
        if (!analyze || !object.hasAnnotations()) return;
        Annotations a = analysis.isNull(object, Annotations.objectAnnotation(object), isNull);
        AnnotatedVM.putAnnotations(frame, top, a);
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
        Annotations a = analysis.stringEquals(cSelf,
                cOther,
                getStringAnnotations(self),
                getStringAnnotations(other));
        if(a != null) return new AnnotatedValue(areEqual, a);
        else return areEqual;
    }

    @CompilerDirectives.TruffleBoundary
    public static Object stringCharAt(StaticObject self, Object oIndex, Meta meta) {
        String cString = meta.toHostString(self);
        int index = AnnotatedValue.value(oIndex);
        Annotations sIndex = AnnotatedValue.svalue(oIndex);

        if (getStringAnnotations(self) == null && sIndex == null) {
            return (int) cString.charAt(index);
        }
        boolean sat1 = (0 <= index);
        boolean sat2 = (index < cString.length());

        if (!sat1 && sIndex == null) {
            // index is negative and cannot be influenced by a SMT solver
            self.setAnnotations(null);
            SPouT.iflowRegisterException();
            throw meta.throwExceptionWithMessage(meta.java_lang_StringIndexOutOfBoundsException, "Index is less than zero");
        }
        if (analyze) analysis.charAtPCCheck(cString, index, getStringAnnotations(self), sIndex);

        if (!sat2) {
            // index is greater than string length
            self.setAnnotations(null);
            SPouT.iflowRegisterException();
            throw meta.throwExceptionWithMessage(meta.java_lang_StringIndexOutOfBoundsException, "Index must be less than string length");
        }

        if (analyze) return new AnnotatedValue((int) cString.charAt(index), analysis.charAt(cString, index, getStringAnnotations(self), sIndex));
        else return (int) cString.charAt(index);
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject substring(StaticObject self, Object begin, Meta meta) {
        int cbegin = AnnotatedValue.value(begin);
        Annotations sbegin = AnnotatedValue.svalue(begin);
        String cSelf = meta.toHostString(self);
        Annotations a3 = null;
        if (analyze && config.hasConcolicAnalysis()) {
            Annotations aself = getStringAnnotations(self);
            Expression sself = Annotations.annotation(aself, config.getConcolicIdx());
            if (sself != null){
                a3 = Annotations.emptyArray();
                ComplexExpression sLength = new ComplexExpression(OperatorComparator.SLENGTH, sself);
                Expression tmpBegin = Annotations.annotation(sbegin, config.getConcolicIdx());
                if(tmpBegin == null){
                    tmpBegin = Constant.createNatConstant(cbegin);
                }else{
                    tmpBegin = new ComplexExpression(OperatorComparator.BV2NAT, tmpBegin);
                }
                a3.set(config.getConcolicIdx(), new ComplexExpression(OperatorComparator.NATMINUS, sLength, tmpBegin));
            }
        }
        try {
            String res = cSelf.substring(cbegin);
            StaticObject result = meta.toGuestString(res);
            if(analyze){
                Annotations a = analysis.substring(true, cSelf, cbegin, cSelf.length(), getStringAnnotations(self), sbegin, a3);
                setStringAnnotations(result, a);
            }
            return result;
        }catch (IndexOutOfBoundsException e){
            if(analyze ){
                analysis.substring(false, cSelf, cbegin, cSelf.length(), getStringAnnotations(self), sbegin, a3);
            }
            SPouT.iflowRegisterException();
            meta.throwException(meta.java_lang_StringIndexOutOfBoundsException);
            throw e;
        }
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject substring(StaticObject self, Object begin, Object end, Meta meta) {
        int cBegin  = AnnotatedValue.value(begin), cEnd = AnnotatedValue.value(end);
        Annotations sBegin = AnnotatedValue.svalue(begin), sEnd = AnnotatedValue.svalue(end);
        String cSelf = meta.toHostString(self);
        try {
            String res = cSelf.substring(cBegin, cEnd);
            StaticObject result = meta.toGuestString(res);
            if(analyze){
                Annotations a = analysis.substring(true, cSelf, cBegin, cEnd, getStringAnnotations(self), sBegin, sEnd);
                setStringAnnotations(result, a);
            }
            return result;
        }catch (IndexOutOfBoundsException e){
            if(analyze){
                analysis.substring(false, cSelf, cBegin, cEnd, getStringAnnotations(self), sBegin, sEnd);
            }
            SPouT.iflowRegisterException();
            meta.throwException(meta.java_lang_StringIndexOutOfBoundsException);
            throw e;
        }
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject stringToUpperCase(StaticObject self, Meta meta) {
        String host = meta.toHostString(self);
        StaticObject result = meta.toGuestString(host.toUpperCase());
        if (analyze) {
            setStringAnnotations(result, analysis.stringToUpperCase(host, getStringAnnotations(self)));
        }
        return result;
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject stringToLowerCase(StaticObject self, Meta meta) {
        String host = meta.toHostString(self);
        StaticObject result = meta.toGuestString(host.toLowerCase());
        if (analyze) {
            setStringAnnotations(result, analysis.stringToLowerCase(host, getStringAnnotations(self)));
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
        boolean isSelfSymbolic = self.hasAnnotations() && self.getAnnotations()[self.getAnnotations().length - 1] != null
                && self.getAnnotations()[self.getAnnotations().length - 1].getAnnotations()[config.getConcolicIdx()] != null;
        boolean isOtherSymbolic = other.hasAnnotations() && other.getAnnotations()[self.getAnnotations().length - 1] != null
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

    public static StaticObject stringBuXXAppendString(StaticObject self, Object string, Meta meta) {
        char c = AnnotatedValue.value(string);
        StaticObject guestString = meta.toGuestString(String.valueOf(c));

        Annotations a = AnnotatedValue.svalue(string);
        if(analyze && config.hasConcolicAnalysis() && a != null){
            a.set(config.getConcolicIdx(), config.getConcolicAnalysis().convertIntToString((Expression) a.getAnnotations()[config.getConcolicIdx()]));
        }
        setStringAnnotations(guestString, a);
        return SPouT.stringBuXXAppendString(self, guestString, meta);
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject stringBuXXAppendString(StaticObject self, StaticObject chars, Object offset, Object length, Meta meta) {
        if(offset instanceof AnnotatedValue || length instanceof AnnotatedValue){
            SPouT.stopRecording("SPouT does not support append from char array with symbolic indicies yet!", meta);
        }
        Annotations[] a = chars.getAnnotations();
        char[] hChars = chars.unwrap(meta.getLanguage());
        int cOffset = AnnotatedValue.value(offset);
        int cLength = AnnotatedValue.value(length);
        if(cOffset < 0 || cLength < 0 || cOffset + cLength > hChars.length) meta.throwException(meta.java_lang_IndexOutOfBoundsException);
        StaticObject other = meta.toGuestString(new String(hChars, cOffset, cLength));
        if(analyze && a != null) {
            Annotations aNew = Annotations.emptyArray();
            if(config.hasConcolicAnalysis()) {
                Expression stringExpr = a[cOffset] == null ? Constant.fromConcreteValue(hChars[cOffset]) : Annotations.annotation(a[cOffset], config.getConcolicIdx());
                for (int i = 1; i < cLength; i++) {
                    int index = cOffset + i;
                    Expression nextChar = a[index] == null ? Constant.fromConcreteValue(hChars[index]) : Annotations.annotation(a[index], config.getConcolicIdx());
                    stringExpr = new ComplexExpression(OperatorComparator.SCONCAT, stringExpr, nextChar);
                }
                aNew.set(config.getConcolicIdx(), stringExpr);
            }
            if(config.hasTaintAnalysis()) {
                Taint color = Annotations.annotation(a[cOffset], config.getTaintIdx());
                for (int i = 1; i < cLength; i++) {
                    int index = cOffset + i;
                    color = ColorUtil.joinColors(color, Annotations.annotation(a[index], config.getTaintIdx()));
                }
                aNew.set(config.getTaintIdx(), color);
            }
            setStringAnnotations(other, aNew);
        }
        return stringBuXXAppendString(self, other, meta);
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject stringBuXXAppendString(StaticObject self, StaticObject string, Meta meta) {
        Annotations[] selfAnnotations = self.getAnnotations();
        if (analyze) {
            Annotations a = analysis.stringBuilderAppend(meta.toHostString(self),
                    meta.toHostString(string),
                    getStringAnnotations(self),
                    getStringAnnotations(string));
            if(selfAnnotations == null){
                initStringAnnotations(self);
                selfAnnotations = self.getAnnotations();
            }
            selfAnnotations[selfAnnotations.length - 1] = a;
        }
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
        Annotations [] a = self.getAnnotations();
        self.setAnnotations(null);
        Object invokeResult = m.invokeDirect(self);
        self.setAnnotations(a);
        int cresult = (int) invokeResult;
        if (analyze) {
            Annotations ana = analysis.stringBuxxLength(meta.toHostString(self), getStringAnnotations(self));
            if(ana != null) return new AnnotatedValue(cresult, ana);
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
        int ilength = AnnotatedValue.value(xlength);
        StaticObject result =
                // FIXME: Not sure if annotations should be able to reach here?
                (boolean) AnnotatedValue.value(isLatin.invokeDirect(self))
                        ?
                        (StaticObject) meta.java_lang_StringLatin1_newString.invokeDirect(self, bytes, 0, ilength)
                        :
                        (StaticObject) meta.java_lang_StringUTF16_newString.invokeDirect(self, bytes, 0, ilength);
        if (analyze && config.hasConcolicAnalysis()) {
            setStringAnnotations(result,
                    analysis.stringBuxxToString(meta.toHostString(result), getStringAnnotations(self)));
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
        if(analyze){
            Annotations a = analysis.stringBuilderAppend("",
                    meta.toHostString(other),
                    null, //getStringAnnotations(self),
                    getStringAnnotations(other));
            if(selfAnnotations == null){
                initStringAnnotations(self);
                selfAnnotations = self.getAnnotations();
            }
            selfAnnotations[selfAnnotations.length - 1] = a;
        }
        Annotations[] stringAnnotations = other.getAnnotations();
        self.setAnnotations(null);
        other.setAnnotations(null);
        m.invokeDirect(self, other);
        self.setAnnotations(selfAnnotations);
        other.setAnnotations(stringAnnotations);
    }


    @CompilerDirectives.TruffleBoundary
    public static StaticObject stringBuxxInsert(StaticObject self, Object offset, StaticObject toInsert, Meta meta) {
        if (offset instanceof AnnotatedValue) {
            SPouT.stopRecording("Cannot handle symbolic offset values for insert into StringBu* yet.", meta);
        }
        int concreteOffset = (int) offset;
        if (analyze) {
            Annotations a = analysis.stringBuxxInsert(meta.toHostString(self),
                    meta.toHostString(toInsert),
                    concreteOffset,
                    getStringAnnotations(self),
                    getStringAnnotations(toInsert),
                    null);
            setStringAnnotations(self, a);
        }
        Method m = self.getKlass().getSuperKlass().lookupMethod(meta.getNames().getOrCreate("insert"), Signature.AbstractStringBuilder_int_String);
        return (StaticObject) m.invokeDirect(self, concreteOffset, toInsert);
    }

    @CompilerDirectives.TruffleBoundary
    public static void stringBuxxGetChars(StaticObject self, Object srcBegin, Object srcEnd, StaticObject dst, Object dstBegin, Meta meta) {
        if (analyze && config.hasConcolicAnalysis() && AnnotatedValue.annotation(AnnotatedValue.svalue(srcBegin), config.getConcolicIdx()) != null
                || AnnotatedValue.annotation(AnnotatedValue.svalue(srcEnd), config.getConcolicIdx()) != null
                || config.hasConcolicAnalysis() && config.getConcolicAnalysis().hasConcolicStringAnnotations(dst)
                || AnnotatedValue.annotation(AnnotatedValue.svalue(dstBegin), config.getConcolicIdx()) != null
                || config.hasConcolicAnalysis() && config.getConcolicAnalysis().hasConcolicStringAnnotations(self)) {
            SPouT.stopRecording("symbolic getChars is not supported", meta);
        }
        Method m = self.getKlass().getSuperKlass().lookupMethod(meta.getNames().getOrCreate("getChars"), Signature._void_int_int_char_array_int);
        m.invokeDirect(self, srcBegin, srcEnd, dst, dstBegin);
    }

    public static void setBuxxCharAt(StaticObject self, Object i, Object ch, Meta meta) {
        if(analyze && config.hasConcolicAnalysis() && (AnnotatedValue.annotation(AnnotatedValue.svalue(i), config.getConcolicIdx()) != null ||
                AnnotatedValue.annotation(AnnotatedValue.svalue(ch), config.getConcolicIdx()) != null)){
            stopRecording("Symbolic index and symbolic chars are not supported", meta);
        }
        int index = (int) i;
        char cha = (char) ch;
        String val = String.valueOf(cha);
        Method m = self.getKlass().getSuperKlass().lookupMethod(meta.getNames().getOrCreate("setCharAt"), Signature._void_int_char);
        Annotations[] a = self.getAnnotations();
        if (analyze && config.hasConcolicAnalysis() && self.hasAnnotations()) {
            self.setAnnotations(null);
            Method toString = self.getKlass().lookupMethod(meta.getNames().getOrCreate("toString"), Signature.String);
            StaticObject buxxStr = (StaticObject) toString.invokeDirect(self);
            String buxx = meta.toHostString(buxxStr);
            analysis.charAtPCCheck(buxx, index, Annotations.annotation(a, -1), AnnotatedValue.svalue(i) );
        }
        self.setAnnotations(null);
        m.invokeDirect(self, i, ch);
        self.setAnnotations(a);
        if (analyze){
            setStringAnnotations(self,
                    analysis.stringBuxxCharAt(meta.toHostString(self), val, index, getStringAnnotations(self), null, null));
        }
    }


    public static Object characterToUpperCase(Object c, Meta meta) {
        char cChar = AnnotatedValue.value(c);
        Annotations sChar = AnnotatedValue.svalue(c);
        char cRes = Character.toUpperCase(cChar);
        if(analyze){
            Annotations a = analysis.characterToUpperCase(cChar, sChar);
            if (a != null) return new AnnotatedValue(cRes, a);
        }
        return cRes;
    }

    public static Object characterToLowerCase(Object c, Meta meta) {
        char cChar = AnnotatedValue.value(c);
        Annotations sChar = AnnotatedValue.svalue(c);
        char cRes = Character.toUpperCase(cChar);
        if(analyze){
            Annotations a = analysis.characterToLowerCase(cChar, sChar);
            if (a != null) return new AnnotatedValue(cRes, a);
        }
        return cRes;
    }

    public static Object isCharDefined(Object c, Meta meta) {
        char cChar = AnnotatedValue.value(c);
        Annotations sChar = AnnotatedValue.svalue(c);
        boolean res  = Character.isDefined(cChar);
        if(analyze){
            Annotations a = analysis.isCharDefined(cChar, sChar);
            if(a != null) return new AnnotatedValue(res, a);
        }
        return res;
    }

    public static Object characterEquals(StaticObject self, StaticObject other, Meta meta) {
        Method m = self.getKlass().lookupMethod(meta.getNames().getOrCreate("charValue"), Signature._char);
        Annotations[] aSelf = self.getAnnotations();
        Annotations[] aOther = other.getAnnotations();
        self.setAnnotations(null);
        other.setAnnotations(null);
        char cself = (char) m.invokeDirect(self);
        char cother = (char) m.invokeDirect(other);
        self.setAnnotations(aSelf);
        other.setAnnotations(aOther);
        boolean cres = cself == cother;
        if(analyze){
            Annotations a = analysis.characterEquals(cself, cother, getStringAnnotations(self), getStringAnnotations(other));
            if(a!= null) return new AnnotatedValue(cres, a);
        }
        return cres;
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject characterValueOf(Object cIn, Meta meta) {
        char ccIn = AnnotatedValue.value(cIn);
        StaticObject result;
        //We do not cache the Character Objects to avoid spreading annotations
        Method m = meta.java_lang_Character.lookupMethod(Symbol.Name._init_, Signature._void_char);
        result = meta.java_lang_Character.allocateInstance(meta.getContext());
        m.invokeDirect(result, ccIn);
        if (analyze) setStringAnnotations(result, AnnotatedValue.svalue(cIn));
        return result;
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
        if (v instanceof AnnotatedValue && config.hasConcolicAnalysis() && Annotations.annotation((Annotations) v, config.getConcolicIdx()) != null) {
            stopRecording("concolic type conversion from boolean to string not supported, yet.", meta);
        }
        String ret = "" + (boolean) AnnotatedValue.value(v);
        return meta.toGuestString(ret);
    }

    public static StaticObject valueOf_byte(Object v, Meta meta) {
        if (v instanceof AnnotatedValue && config.hasConcolicAnalysis() && Annotations.annotation((Annotations) v, config.getConcolicIdx()) != null) {
            stopRecording("concolic type conversion from byte to string not supported, yet.", meta);
        }
        String ret = "" + (byte) AnnotatedValue.value(v);
        return meta.toGuestString(ret);
    }

    public static StaticObject valueOf_char(Object v, Meta meta) {
        if (v instanceof AnnotatedValue && config.hasConcolicAnalysis() && Annotations.annotation((Annotations) v, config.getConcolicIdx()) != null) {
            stopRecording("concolic type char conversion to string not supported, yet.", meta);
        }
        String ret = "" + (char) AnnotatedValue.value(v);
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
        if (v instanceof AnnotatedValue && config.hasConcolicAnalysis() && Annotations.annotation((Annotations) v, config.getConcolicIdx()) != null) {
            stopRecording("concolic type conversion from short to string not supported, yet.", meta);
        }
        String ret = "" + (short) AnnotatedValue.value(v);
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
        if (v instanceof AnnotatedValue && config.hasConcolicAnalysis() && Annotations.annotation((Annotations) v, config.getConcolicIdx()) != null) {
            stopRecording("concolic type conversion from long to string not supported, yet.", meta);
        }
        String ret = "" + (long) AnnotatedValue.value(v);
        return meta.toGuestString(ret);
    }

    public static StaticObject valueOf_float(Object v, Meta meta) {
        if (v instanceof AnnotatedValue && config.hasConcolicAnalysis() && Annotations.annotation((Annotations) v, config.getConcolicIdx()) != null) {
            stopRecording("concolic type conversion from float to string not supported, yet.", meta);
        }
        String ret = "" + (float) AnnotatedValue.value(v);
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

    @CompilerDirectives.TruffleBoundary
    public static int parseInt(StaticObject s, Meta meta) {
        if (analyze && config.hasConcolicAnalysis() && config.getConcolicAnalysis().hasConcolicStringAnnotations(s)) {
            stopRecording("Concolic type conversion from string to int is not supported", meta);
        }
        return Integer.parseInt(meta.toHostString(s));
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
        if(a != null) {
            Annotations[] stringAnnotation = self.getAnnotations();
            if (stringAnnotation == null) {
                SPouT.initStringAnnotations(self);
                stringAnnotation = self.getAnnotations();
            }
            stringAnnotation[stringAnnotation.length - 1] = a;
            self.setAnnotations(stringAnnotation);
        }
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
        if(analyze && config.hasTaintAnalysis()) {
            config.getTaintAnalysis().makeConcatWithConstants((StaticObject) result, args);
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
