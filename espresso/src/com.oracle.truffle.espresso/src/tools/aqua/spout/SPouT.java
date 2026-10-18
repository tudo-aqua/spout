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
import com.oracle.truffle.espresso.descriptors.EspressoSymbols;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.EspressoFrame;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import tools.aqua.concolic.SymbolDeclaration;
import tools.aqua.smt.AuxiliaryVariable;
import tools.aqua.smt.ComplexExpression;
import tools.aqua.smt.Constant;
import tools.aqua.smt.Expression;
import tools.aqua.smt.OperatorComparator;
import tools.aqua.smt.Types;
import tools.aqua.taint.ColorUtil;
import tools.aqua.taint.PostDominatorAnalysis;
import tools.aqua.taint.Taint;
import tools.aqua.witnesses.GWIT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.*;
import static com.oracle.truffle.espresso.nodes.BytecodeNode.*;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.*;
import static com.oracle.truffle.espresso.runtime.dispatch.staticobject.EspressoInterop.getMeta;


public class SPouT {

    public static final boolean DEBUG = true;

    protected static boolean analyze = false, oldAnalyze = analyze;

    protected static MetaAnalysis analysis = null;


    private static Config config = null;

    private static Trace trace = null;

    private static GWIT gwit;

    // --------------------------------------------------------------------------
    //
    // start and stop

    @CompilerDirectives.TruffleBoundary
    public static void newPath(String options) {
        System.out.println("======================== START PATH [BEGIN].");
        config = new Config();
        reportedPrecisionLosses.clear();
        config.parseAnalysesConfig(options, getMeta());
        analysis = new MetaAnalysis(config);
        trace = config.getTrace();
        gwit = new GWIT(trace);
        // TODO: should be deferred to latest possible point in time
        analysisThread = Thread.currentThread();
        pauseDepth = 0;
        analyze = true;
        oldAnalyze = true;
        SPouTNumeric.newPath(config, true);
        if (config.hasConcolicAnalysis()) {
            AuxiliaryVariable nv = new AuxiliaryVariable("null", Types.OBJECT);
            SymbolDeclaration decl = new SymbolDeclaration(nv, true);
            trace.addElement(decl);
        }
        config.parseConcolicValues(options, getMeta());
        config.printAnalysisConfig();
        System.out.println("======================== START PATH [END].");
    }

    @CompilerDirectives.TruffleBoundary
    public static void endPath() {
        System.out.println("======================== END PATH [BEGIN].");

        stopAnalysis();
        if (trace != null) {
            /*
            if (config.hasConcolicAnalysis()) {
                System.out.println("[AUXILIARY] (declare-sort Object 0)");
            }
            */
            trace.printTrace();
        }
        System.out.println("======================== END PATH [END].");
        System.out.println("[META_INFOS] object_count: "+config.getCountObjectSeeds());
        System.out.println("[ENDOFTRACE]");
        System.out.flush();
    }

    private static void stopAnalysis() {
        if (analyze) {
            analyze = false;
            SPouTNumeric.stopAnalysis();
            //FIXME: analysis.terminate();
        }
    }

    // --------------------------------------------------------------------------
    //
    // analysis entry points

    // FIXME: move part of these methods into the different analyses?

    @CompilerDirectives.TruffleBoundary
    public static void fail(String message, Meta meta) {
        stopRecording(message, meta);
    }

    @CompilerDirectives.TruffleBoundary
    public static void losePrecision(String message, Meta meta) {
        // TODO: configure stopping the analysis
        // TODO: sometimes called with meta == null
        SPouT.log(message);
        // the path is not explored completely: reported in the trace (once per reason)
        if (analyze && trace != null && reportedPrecisionLosses.add(message)) {
            trace.addElement(new PrecisionLoss(message));
        }
        //stopRecording(message, meta);
    }

    /** reasons of precision losses reported in the trace of the current path */
    private static final java.util.Set<String> reportedPrecisionLosses = new java.util.HashSet<>();

    @CompilerDirectives.TruffleBoundary
    public static void notImplementedYet(String message, Meta meta) {
        stopRecording(message, meta);
    }

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

        if (condition instanceof AnnotatedValue && config.hasConcolicAnalysis()) {
            config.getConcolicAnalysis().assume(AnnotatedValue.value(condition),
                    Annotations.annotation(AnnotatedValue.svalue(condition), config.getConcolicIdx()));
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
        if (!analyze || !config.hasConcolicAnalysis()) return 0l;
        Object av = config.getConcolicAnalysis().nextSymbolicLong();
        gwit.trackLocationForWitness("" + (long) ((AnnotatedValue) av).getValue() + "L");
        return av;
    }

    @CompilerDirectives.TruffleBoundary
    public static Object nextSymbolicFloat() {
        if (!analyze || !config.hasConcolicAnalysis()) return 0f;
        Object av = config.getConcolicAnalysis().nextSymbolicFloat();
        gwit.trackLocationForWitness("Float.parseFloat(\"" +
                (float) ((AnnotatedValue) av).getValue() + "\")");
        return av;
    }

    @CompilerDirectives.TruffleBoundary
    public static Object nextSymbolicDouble() {
        if (!analyze || !config.hasConcolicAnalysis()) return 0d;
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

    /**
     * These methods mark the barrier between host and guest world (see @CompilerDirectives.TruffleBoundary).
     * Trys to create in the guest world a new Object according to the next element of -Dconcolic.constructors.
     * Stops the analysis if the creation fails.
     *
     * @param meta introspection API to get information of the guest system during runtime
     * @return object created in the guest world
     */
    @CompilerDirectives.TruffleBoundary
    public static StaticObject nextSymbolicObject(Meta meta, Klass typeBound) {
        if (!analyze || !config.hasConcolicAnalysis()) return StaticObject.NULL;
        StaticObject staticObject = config.getConcolicAnalysis().nextSymbolicObject(meta, typeBound);
        if (staticObject == null) {
            fail("Error Creating Symbolic Object", meta);
        }
        return staticObject;
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
        if (!analyze || !config.hasTaintAnalysis()) return;
        config.getTaintAnalysis().checkTaint(o instanceof AnnotatedValue ? (AnnotatedValue) o : null, color);
    }

    @CompilerDirectives.TruffleBoundary
    public static void checkTaintObject(StaticObject o, int color) {
        if (!analyze || !config.hasTaintAnalysis()) return;
        config.getTaintAnalysis().checkTaintObject(o, color);
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

    public static void markWithIFTaint(VirtualFrame frame, int top) {
        if (!analyze || !config.analyzeControlFlowTaint()) return;
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
        if (!analyze || !config.analyzeControlFlowTaint()) return;
        if (obj == StaticObject.NULL) return;
        Taint t = config.getTaintAnalysis().getIfTaint();
        if (t == null) return;

        if (obj.isArray()) {
            log("Warning: currently not marking array in markObjectWithIFTaint.");
            return;
        } else if (!obj.hasAnnotations()) {
            Annotations.initObjectAnnotations(obj);
            Annotations a = new Annotations();
            a.set(config.getTaintIdx(), t);
            Annotations.setObjectAnnotation(obj, a);
        } else {
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
        int c1 = EspressoFrame.getLocalInt(frame, index);
        int concResult = c1 + incr;
        EspressoFrame.setLocalInt(frame, index, concResult);
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

    public static void checkNull(StaticObject staticObject, boolean isNull) {
        checkNull(staticObject, isNull, null);
    }

    /** @param a the annotation of the slot of the reference */
    public static void checkNull(StaticObject staticObject, boolean isNull, Annotations a) {
        if (analyze) {
            analysis.checkNull(staticObject, isNull, refAnnotations(staticObject, a));
        }
    }

    // --------------------------------------------------------------------------
    //
    // heap model: references carry the terms of the objects in the annotations of their slots (see HeapModel)

    /**
     * Annotation of a reference: the annotation of its slot if it has a term, the object annotation otherwise (the
     * symbolic value of a string, the length of an array, the identity of a nondet object, or the first term by
     * which the object was read).
     */
    @CompilerDirectives.TruffleBoundary
    public static Annotations refAnnotations(StaticObject o, Annotations slot) {
        Annotations oa = Annotations.objectAnnotation(o);
        if (slot == null || config == null || !config.hasConcolicAnalysis()
                || Annotations.annotation(slot, config.getConcolicIdx()) == null) {
            return oa;
        }
        if (oa == null) {
            return slot;
        }
        Object[] values = oa.getAnnotations().clone();
        values[config.getConcolicIdx()] = Annotations.annotation(slot, config.getConcolicIdx());
        return Annotations.create(values);
    }

    private static boolean hasHeapModel() {
        return analyze && config.hasConcolicAnalysis();
    }

    /** annotation of a field read through a reference with annotation {@code receiverA} */
    @CompilerDirectives.TruffleBoundary
    public static Annotations getFieldAnnotation(StaticObject receiver, Annotations receiverA, Field f) {
        Annotations slot = AnnotatedVM.getFieldAnnotation(receiver, f);
        if (analyze && !f.isStatic() && tools.aqua.concolic.HeapModel.isBuilder(receiver)
                && isSymbolicBuilder(receiver, receiverA)) {
            // a method without symbolic implementation runs on the concrete characters of a symbolic builder
            losePrecision("internal field " + f.getNameAsString() + " of a builder with symbolic content is read, "
                    + "its symbolic content is concretized", getMeta());
        }
        if (analyze && !f.isStatic() && receiver.getKlass() == getMeta().java_lang_String && isSymbolicString(receiver, receiverA)) {
            // a method without symbolic implementation runs on the concrete characters of a symbolic string
            losePrecision("internal field String." + f.getNameAsString() + " of a symbolic string is read, its "
                    + "symbolic content is concretized", getMeta());
        }
        if (!hasHeapModel() || f.isStatic()) {
            return slot;
        }
        return config.getConcolicAnalysis().getHeap().readField(receiver, refAnnotations(receiver, receiverA), f, slot,
                getMeta());
    }

    @CompilerDirectives.TruffleBoundary
    public static void setFieldAnnotation(StaticObject receiver, Annotations receiverA, Field f, Annotations value) {
        if (analyze && !f.isStatic() && tools.aqua.concolic.HeapModel.isBuilder(receiver)
                && isSymbolicBuilder(receiver, receiverA)) {
            // a mutation without symbolic implementation (e.g. reverse, delete): the symbolic content is stale
            losePrecision("internal field " + f.getNameAsString() + " of a builder with symbolic content is written, "
                    + "its symbolic content is dropped", getMeta());
            Annotations.setObjectAnnotation(receiver, null);
        }
        if (hasHeapModel() && !f.isStatic()) {
            config.getConcolicAnalysis().getHeap().writeField(receiver, refAnnotations(receiver, receiverA), f, value);
        }
        AnnotatedVM.setFieldAnnotation(receiver, f, value);
    }

    /** annotations of the reference arguments of calls, from popping them until the callee initializes its frame */
    private static final Map<Object[], Annotations[]> argumentAnnotations = new IdentityHashMap<>();

    @CompilerDirectives.TruffleBoundary
    public static void setArgumentAnnotations(Object[] args, Annotations[] annotations) {
        if (!analyze) return;
        synchronized (argumentAnnotations) {
            if (argumentAnnotations.size() > 10_000) {
                // left by calls of native or substituted methods
                argumentAnnotations.clear();
            }
            argumentAnnotations.put(args, annotations);
        }
    }

    @CompilerDirectives.TruffleBoundary
    public static Annotations[] takeArgumentAnnotations(Object[] args) {
        synchronized (argumentAnnotations) {
            return argumentAnnotations.isEmpty() ? null : argumentAnnotations.remove(args);
        }
    }

    @CompilerDirectives.TruffleBoundary
    public static Annotations argumentAnnotation(Object[] args, int i) {
        synchronized (argumentAnnotations) {
            Annotations[] a = argumentAnnotations.isEmpty() ? null : argumentAnnotations.get(args);
            return a != null ? a[i] : null;
        }
    }

    /**
     * Arguments of the substituted methods being executed (innermost last) with the annotations of their slots: the
     * substitutions of string methods find the terms of their string arguments here (see {@link #stringContent}).
     */
    private static final ThreadLocal<java.util.ArrayDeque<Object[]>> calls =
            ThreadLocal.withInitial(java.util.ArrayDeque::new);

    /** @return a token for {@link #exitCall}, {@code null} if the arguments carry no annotations */
    @CompilerDirectives.TruffleBoundary
    public static Object enterCall(Object[] args) {
        if (!analyze) return null;
        return enterCall(args, takeArgumentAnnotations(args));
    }

    @CompilerDirectives.TruffleBoundary
    public static Object enterCall(Object[] args, Annotations[] annotations) {
        if (annotations == null) return null;
        Object[] call = {args, annotations};
        calls.get().push(call);
        return call;
    }

    @CompilerDirectives.TruffleBoundary
    public static void exitCall(Object token) {
        if (token != null) {
            calls.get().remove(token);
        }
    }

    /**
     * The annotations of the content of a string: {@code (str.val t)} if the string is an argument of the current
     * substituted method and its reference has the term {@code t}, the object annotation otherwise (the content of a
     * string computed by the program, or of a nondet string).
     */
    @CompilerDirectives.TruffleBoundary
    public static Annotations stringContent(StaticObject s) {
        Annotations oa = Annotations.annotation(s.getAnnotations(), -1);
        if (!analyze || config == null || !config.hasConcolicAnalysis() || StaticObject.isNull(s)) {
            return oa;
        }
        if (tools.aqua.concolic.HeapModel.isBuilder(s)) {
            // a builder read through a term: its content in the heap model (initial content and mutations)
            Annotations heapContent = config.getConcolicAnalysis().getHeap().readBuilderContent(s, callAnnotation(s));
            if (heapContent == null) {
                return oa;
            }
            Object[] values = oa != null ? oa.getAnnotations().clone() : Annotations.create().getAnnotations();
            values[config.getConcolicIdx()] = Annotations.annotation(heapContent, config.getConcolicIdx());
            return Annotations.create(values);
        }
        if (s.getKlass() != getMeta().java_lang_String) {
            return oa;
        }
        Object[] call = calls.get().peek();
        if (call == null) {
            return oa;
        }
        Object[] args = (Object[]) call[0];
        Annotations[] annotations = (Annotations[]) call[1];
        for (int i = 0; i < args.length && i < annotations.length; i++) {
            if (args[i] == s) {
                Object t = Annotations.annotation(annotations[i], config.getConcolicIdx());
                if (tools.aqua.concolic.HeapModel.isObjectTerm(t)) {
                    Object[] values = oa != null ? oa.getAnnotations().clone() : Annotations.create().getAnnotations();
                    values[config.getConcolicIdx()] = config.getConcolicAnalysis().getHeap().contentTerm((Expression) t);
                    return Annotations.create(values);
                }
            }
        }
        return oa;
    }

    /** @return whether the content of a builder, referenced with slot annotation {@code a}, is symbolic */
    private static boolean isSymbolicBuilder(StaticObject b, Annotations a) {
        if (config == null || !config.hasConcolicAnalysis()) return false;
        Object[] call = calls.get().peek();
        if (call != null && ((Object[]) call[0]).length > 0 && ((Object[]) call[0])[0] == b) {
            // inside a substituted method of the builder: its implementation uses the internal fields on purpose
            return false;
        }
        if (Annotations.annotation(Annotations.objectAnnotation(b), config.getConcolicIdx()) != null) return true;
        return tools.aqua.concolic.HeapModel.isObjectTerm(Annotations.annotation(a, config.getConcolicIdx()));
    }

    /** @return whether the content of a string, referenced with slot annotation {@code a}, is symbolic */
    private static boolean isSymbolicString(StaticObject s, Annotations a) {
        if (config == null || !config.hasConcolicAnalysis()) return false;
        if (Annotations.annotation(Annotations.objectAnnotation(s), config.getConcolicIdx()) != null) return true;
        return config.isSymbolicStringFields()
                && tools.aqua.concolic.HeapModel.isObjectTerm(Annotations.annotation(a, config.getConcolicIdx()));
    }

    /** @return whether the string has annotations (of any analysis), also by the term of its reference */
    public static boolean hasStringAnnotations(StaticObject s) {
        return s.hasAnnotations() || hasSymbolicContent(s);
    }

    /** the annotation of the slot of {@code o} as argument of the current substituted method, or null */
    private static Annotations callAnnotation(StaticObject o) {
        Object[] call = calls.get().peek();
        if (call == null) {
            return null;
        }
        Object[] args = (Object[]) call[0];
        Annotations[] annotations = (Annotations[]) call[1];
        for (int i = 0; i < args.length && i < annotations.length; i++) {
            if (args[i] == o) {
                return annotations[i];
            }
        }
        return null;
    }

    /**
     * Sets the content of a builder after a mutation: its object annotation, and, if the builder is referenced
     * through a term of the heap model, a write of its content (later reads through aliases see it).
     */
    private static void setBuilderContent(StaticObject self, Annotations content) {
        setStringAnnotations(self, content);
        if (analyze && config.hasConcolicAnalysis() && content != null) {
            config.getConcolicAnalysis().getHeap().writeBuilderContent(self, callAnnotation(self), content);
        }
    }

    /** the concrete content of a builder (toString without analysis and annotations) */
    @CompilerDirectives.TruffleBoundary
    public static String builderToHostString(StaticObject builder, Meta meta) {
        return meta.toHostString(concreteBuilderString(builder, meta));
    }

    /** @return whether the content of the string is symbolic */
    public static boolean hasSymbolicContent(StaticObject s) {
        return config != null && config.hasConcolicAnalysis()
                && Annotations.annotation(stringContent(s), config.getConcolicIdx()) != null;
    }

    /** the reference returned last and its annotation, until the caller pushes it */
    private static final ThreadLocal<Object[]> returned = new ThreadLocal<>();

    @CompilerDirectives.TruffleBoundary
    public static void setReturned(Object value, Annotations a) {
        if (!analyze) return;
        returned.set(a == null ? null : new Object[]{value, a});
    }

    @CompilerDirectives.TruffleBoundary
    public static Annotations takeReturned(Object value) {
        if (!analyze) return null;
        Object[] r = returned.get();
        if (r == null) return null;
        returned.set(null);
        return r[0] == value ? (Annotations) r[1] : null;
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
            analysis.checkNotZeroLong(frame, bcn, bci, c1 == 0L, a);
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
        putObject(frame, top - 1, array);
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
        putObject(frame, top - 1, array);
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

        putObject(frame, top - allocatedDimensions, value);
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
        if (!analyze) return;
        if (config.hasConcolicAnalysis()) {
            Expression length = config.getConcolicAnalysis().getHeap().arrayLength(arr,
                    AnnotatedVM.peekAnnotations(frame, top - 1));
            if (length != null) {
                Annotations a = Annotations.create();
                a.set(config.getConcolicIdx(), length);
                AnnotatedVM.putAnnotations(frame, top - 1, a);
                return;
            }
        }
        if (!arr.hasAnnotations()) {
            AnnotatedVM.putAnnotations(frame, top - 1, null);
            return;
        }
        Annotations[] annotations = arr.getAnnotations();
        Annotations aLength = annotations[annotations.length - 1];
        AnnotatedVM.putAnnotations(frame, top - 1, aLength);
    }

    public static void getArrayAnnotations(VirtualFrame frame, BytecodeNode bcn, int bci, StaticObject array,
                                           int cIndex, int fromIndexSlot, int toSlot, EspressoLanguage lang) {
        if (!analyze) return;

        Annotations aIndex = AnnotatedVM.popAnnotations(frame, fromIndexSlot);
        // the slot of the array reference: the value of a two-slot load (long, double) is annotated in its upper
        // slot, a stale annotation in the lower slot would be read by later instructions. The array reference is
        // directly below the index, and the value replaces both (one-slot value: toSlot is the array reference's
        // slot, two-slot value: toSlot is the index's slot).
        assert toSlot == fromIndexSlot - 1 || toSlot == fromIndexSlot : "unexpected slots of an array load";
        Annotations aArray = refAnnotations(array, AnnotatedVM.popAnnotations(frame, fromIndexSlot - 1));
        Expression sIndex = null;
        if (analyze && config.hasConcolicAnalysis()) {
            sIndex = Annotations.annotation(aIndex, config.getConcolicIdx());
            if (sIndex != null) {
                sIndex = config.getConcolicAnalysis().convertCharToInt(sIndex);
            }
            config.getConcolicAnalysis().checkArrayAccessPathConstraint(array, cIndex, sIndex,
                    config.getConcolicAnalysis().getHeap().arrayLength(array, aArray), lang);
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
        if (analyze && config.hasConcolicAnalysis()) {
            tools.aqua.concolic.HeapModel heap = config.getConcolicAnalysis().getHeap();
            boolean inBounds = 0 <= cIndex && cIndex < array.length(lang);
            if (inBounds && !heap.hasElementTerms(array, aArray)
                    && (heap.hasContentTerm(array) || (sIndex != null && heap.ensureContentTerm(array, getMeta())))) {
                // an allocated array with a content term (theory of arrays)
                AnnotatedVM.putAnnotations(frame, toSlot, heap.readContent(array, cIndex, sIndex, a));
                return;
            }
            if (sIndex != null && !heap.hasElementTerms(array, aArray) && inBounds) {
                // a large array: the element at the concrete index is read, only for this index
                config.getConcolicAnalysis().concretizeIndex(sIndex, cIndex, array.length(lang));
            }
            a = heap.readElement(array, aArray, cIndex, sIndex, a, getMeta());
        }
        AnnotatedVM.putAnnotations(frame, toSlot, a);
    }

    /** a concrete value (e.g. a byte for a boolean array) as a value of the element kind of the array */
    @CompilerDirectives.TruffleBoundary
    private static Object asElementValue(StaticObject array, Object v) {
        if (v == null || !array.isArray()) {
            return v;
        }
        com.oracle.truffle.espresso.classfile.JavaKind kind =
                ((com.oracle.truffle.espresso.impl.ArrayKlass) array.getKlass()).getComponentType().getJavaKind();
        long l = v instanceof Boolean ? ((Boolean) v ? 1 : 0) : v instanceof Character ? (Character) v
                : v instanceof Number && !(v instanceof Float) && !(v instanceof Double) ? ((Number) v).longValue() : 0;
        return switch (kind) {
            case Boolean -> v instanceof Boolean ? v : l != 0;
            case Byte -> (byte) l;
            case Short -> (short) l;
            case Char -> (char) l;
            case Int -> (int) l;
            case Long -> v instanceof Long ? v : l;
            default -> v;
        };
    }

    /** the concrete value of an array store (still on the operand stack), null for references */
    private static Object concreteElementValue(VirtualFrame frame, StaticObject array, int slot) {
        com.oracle.truffle.espresso.classfile.JavaKind kind =
                ((com.oracle.truffle.espresso.impl.ArrayKlass) array.getKlass()).getComponentType().getJavaKind();
        return switch (kind) {
            case Boolean -> frame.getIntStatic(slot) != 0;
            case Byte -> (byte) frame.getIntStatic(slot);
            case Short -> (short) frame.getIntStatic(slot);
            case Char -> (char) frame.getIntStatic(slot);
            case Int -> frame.getIntStatic(slot);
            case Long -> frame.getLongStatic(slot);
            case Float -> frame.getFloatStatic(slot);
            case Double -> frame.getDoubleStatic(slot);
            default -> null;
        };
    }

    public static void setArrayAnnotations(VirtualFrame frame, BytecodeNode bcn, int bci, StaticObject array,
                                           int cIndex, int fromValueSlot, int fromIndexSlot, EspressoLanguage lang) {
        if (!analyze) return;
        // the value is still on the operand stack
        setArrayAnnotations(frame, bcn, bci, array, cIndex, fromValueSlot, fromIndexSlot, lang,
                concreteElementValue(frame, array, fromValueSlot));
    }

    /** an array store with the concrete value (e.g. already popped from the operand stack) */
    public static void setArrayAnnotations(VirtualFrame frame, BytecodeNode bcn, int bci, StaticObject array,
                                           int cIndex, int fromValueSlot, int fromIndexSlot, EspressoLanguage lang,
                                           Object concreteValue) {
        if (!analyze) return;
        concreteValue = asElementValue(array, concreteValue);

        Annotations aIndex = AnnotatedVM.popAnnotations(frame, fromIndexSlot);
        Annotations aArray = refAnnotations(array, AnnotatedVM.popAnnotations(frame, fromIndexSlot - 1));
        Expression sIndex = null;
        if (analyze && config.hasConcolicAnalysis()) {
            sIndex = Annotations.annotation(aIndex, config.getConcolicIdx());
            if (sIndex != null) {
                sIndex = config.getConcolicAnalysis().convertCharToInt(sIndex);
            }
            config.getConcolicAnalysis().checkArrayAccessPathConstraint(array, cIndex, sIndex,
                    config.getConcolicAnalysis().getHeap().arrayLength(array, aArray), lang);
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
        if (analyze && config.hasConcolicAnalysis()) {
            tools.aqua.concolic.HeapModel heap = config.getConcolicAnalysis().getHeap();
            boolean inBounds = 0 <= cIndex && cIndex < array.length(lang);
            if (inBounds && !heap.hasElementTerms(array, aArray)
                    && (heap.hasContentTerm(array) || (sIndex != null && heap.ensureContentTerm(array, getMeta())))) {
                // an allocated array with a content term (theory of arrays)
                heap.writeContent(array, cIndex, sIndex, aValue, concreteValue);
            } else {
                if (sIndex != null && !heap.hasElementTerms(array, aArray) && inBounds) {
                    // a large array: the element at the concrete index is written, only for this index
                    config.getConcolicAnalysis().concretizeIndex(sIndex, cIndex, array.length(lang));
                }
                heap.writeElement(array, aArray, cIndex, sIndex, aValue, concreteValue);
            }
        }
        AnnotatedVM.setArrayAnnotations(array, cIndex, aValue, lang);
    }

    // branching

    public static void checkcast(VirtualFrame frame,
                                 StaticObject obj,
                                 Klass typeToCast,
                                 int top,
                                 BytecodeNode bcn,
                                 int bci,
                                 boolean isInstance) {
        if (!analyze) return;
        Annotations a = refAnnotations(obj, AnnotatedVM.peekAnnotations(frame, top));
        if (a == null) return;
        analysis.checkcast(frame, bcn, bci, obj, a, typeToCast, isInstance);
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


    /**
     * This method is an extension of {{@link BytecodeNode#takeBranchRef1(StaticObject, int)}} which is the native
     * implementation of the espresso vm for the following java bytecodes:
     * <ul>
     *     <li>IFNULL</li>
     *     <li>IFNONNULL</li>
     * </ul>
     *
     * In this method, only meta information, especially the result of the evaluation of the Java byte code,
     * is passed on in order to log the decisions during execution.
     * See the implementations of
     * {{@link Analysis#takeBranchRef1(VirtualFrame, BytecodeNode, int, int, boolean, StaticObject, Object)}}
     * Implementations of Analysis are
     * {{@link tools.aqua.concolic.ConcolicAnalysis#takeBranchRef1(VirtualFrame, BytecodeNode, int, int, boolean, StaticObject, Expression)}}
     * and
     * {{@link tools.aqua.taint.TaintAnalysis#takeBranchRef1(VirtualFrame, BytecodeNode, int, int, boolean, StaticObject, Object)}}
     *
     * @param frame         Virtual Frame of espresso. Storing the current execution state of the method (e.g. local
     *                      variables, stacke values, runtime information)
     * @param bcn           internal structure of the espresso vm for resolving bytecodes
     * @param bci           ByteCodeIndex: position of the java bytecode within the method
     * @param operand       operand to which the bytecode is applied (in this case always an object type)
     * @param opcode        represents a JVM operation (IFNULL, IFNONULL)
     * @return              evaluation of the javabyte code according to the given operand
     */
    public static boolean takeBranchRef1(VirtualFrame frame,
                                         BytecodeNode bcn,
                                         int bci,
                                         StaticObject operand,
                                         int opcode) {
        return takeBranchRef1(frame, bcn, bci, null, operand, opcode);
    }

    /** @param a the annotation of the slot of the operand */
    public static boolean takeBranchRef1(VirtualFrame frame,
                                         BytecodeNode bcn,
                                         int bci,
                                         Annotations a,
                                         StaticObject operand,
                                         int opcode) {
        assert IFNULL <= opcode && opcode <= IFNONNULL;
        // @formatter:off
        boolean result;
        switch (opcode) {
            case IFNULL    : result = StaticObject.isNull(operand);break;
            case IFNONNULL : result = StaticObject.notNull(operand);break;
            default        :
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("expected IFNULL or IFNONNULL bytecode");
        }
        // @formatter:on
        if (analyze) {
            analysis.takeBranchRef1(frame, bcn, bci, opcode, result, operand, refAnnotations(operand, a));
        }
        return result;
    }

    /**
     * This method is an extension of {{@link BytecodeNode#takeBranchRef2( StaticObject, StaticObject, int)} which is
     * the native implementation of the espresso vm for the following java bytecodes:
     * <ul>
     *     <li>IF_ACMPEQ (==)</li>
     *     <li>IF_ACMPNE (!=)</li>
     * </ul>
     *
     * In this method, only meta information, especially the result of the evaluation of the Java byte code,
     * is passed on in order to log the decisions during execution.
     *
     * See the implementations of
     * {{@link Analysis#takeBranchRef2( VirtualFrame, BytecodeNode, int, int, boolean, StaticObject, StaticObject, Object, Object)}
     * Implementations of Analysis are
     * {{@link tools.aqua.concolic.ConcolicAnalysis#takeBranchRef2( VirtualFrame, BytecodeNode, int, int, boolean, StaticObject, StaticObject, Expression, Expression)}
     * and
     *  {{@link tools.aqua.taint.TaintAnalysis#takeBranchRef2( VirtualFrame, BytecodeNode, int, int, boolean, StaticObject, StaticObject, Taint, Taint)}
     *
     * @param frame         Virtual Frame of espresso. Storing the current execution state of the method (e.g. local
     *                      variables, stacke values, runtime information)
     * @param bcn           internal structure of the espresso vm for resolving bytecodes
     * @param bci           ByteCodeIndex: position of the java bytecode within the method
     * @param operand1      first operand to which the bytecode is applied (in this case always an object type)
     * @param operand2      second operand to which the bytecode is applied (in this case always an object type)
     * @param opcode        represents a JVM operation (IF_ACMPEQ, IF_ACMPNE)
     * @return              evaluation of the javabyte code according to the two given operands
     */
    public static boolean takeBranchRef2(VirtualFrame frame,
                                         BytecodeNode bcn,
                                         int bci,
                                         StaticObject operand1,
                                         StaticObject operand2,
                                         int opcode) {
        return takeBranchRef2(frame, bcn, bci, null, null, operand1, operand2, opcode);
    }

    /** @param a1 the annotation of the slot of operand1, a2 of operand2 */
    public static boolean takeBranchRef2(VirtualFrame frame,
                                         BytecodeNode bcn,
                                         int bci,
                                         Annotations a1,
                                         Annotations a2,
                                         StaticObject operand1,
                                         StaticObject operand2,
                                         int opcode) {
        assert IF_ACMPEQ <= opcode && opcode <= IF_ACMPNE;
        boolean result;
        // @formatter:off
        switch (opcode) {
            case IF_ACMPEQ : result =  operand1 == operand2 || (StaticObject.isNull(operand1) && StaticObject.isNull(operand2)); break;
            case IF_ACMPNE : result =  operand1 != operand2 && !(StaticObject.isNull(operand1) && StaticObject.isNull(operand2)); break;
            default        :
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("expecting IF_ACMPEQ,IF_ACMPNE");
        }

        // @formatter:on

        if (analyze) {
            analysis.takeBranchRef2(frame, bcn, bci, opcode, result, operand1, operand2,
                    refAnnotations(operand1, a1), refAnnotations(operand2, a2));

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

    public static void instanceOf(VirtualFrame frame, StaticObject object, boolean isInstance, int top, Klass typeToCheck) {
        if (!analyze) return;
        // the slot of the object, replaced by the result
        Annotations ao = refAnnotations(object, AnnotatedVM.peekAnnotations(frame, top));
        if (ao == null) {
            AnnotatedVM.putAnnotations(frame, top, null);
            return;
        }
        Annotations a = analysis.instanceOf(object, ao, typeToCheck, isInstance);
        AnnotatedVM.putAnnotations(frame, top, a);
    }

    public static void isNull(VirtualFrame frame, StaticObject object, boolean isNull, int top) {
        if (!analyze || !object.hasAnnotations()) return;
        Annotations a = analysis.isNull(object, Annotations.objectAnnotation(object), isNull);
        AnnotatedVM.putAnnotations(frame, top, a);
    }

    public static StaticObject objectGetClass(StaticObject self) {
        if (!analyze || !self.hasAnnotations()) return self.getKlass().mirror();
        Annotations aObj = analysis.objectGetClass(self, Annotations.objectAnnotation(self));
        StaticObject cObj = self.getKlass().mirror();
        if (aObj != null) {
            Annotations.setObjectAnnotation(cObj, aObj);
        }
        return cObj;
    }

    public static void polymorphicMethodAccess(StaticObject object, Method m, int i) {
        if (!analyze || !object.hasAnnotations()) return;
        //if (object.hasAnnotations()) logi(i);
        analysis.polymorphicMethodAccess(object, m, Annotations.objectAnnotation(object));
    }

    /** dispatch on the receiver {@code args[0]} of a call */
    @CompilerDirectives.TruffleBoundary
    public static void polymorphicMethodAccess(Object[] args, Method m, int i) {
        if (!analyze) return;
        StaticObject object = (StaticObject) args[0];
        Annotations a = refAnnotations(object, argumentAnnotation(args, 0));
        if (a == null) return;
        analysis.polymorphicMethodAccess(object, m, a);
    }

    @CompilerDirectives.TruffleBoundary
    private static void logi(int i) {
        SPouT.log("case  " + i);
    }

    public static void nullCheckForException(StaticObject object) {
        if (!analyze || !object.hasAnnotations()) return;
    }
    // ---------------------------------------------------------------------------
    //
    // Annotations required for java.lang.Math

    //    @CompilerDirectives.TruffleBoundary
    public static Object mathSin(Object a, Meta meta) {
//        double concreteValue = AnnotatedValue.value(pDouble);
//        Annotations symPDouble = AnnotatedValue.svalue(pDouble);
//        double concreteResult = Math.sin(concreteValue);
//        if(analyze && symPDouble != null){
//            return new AnnotatedValue(concreteResult, analysis.mathSin(concreteValue, symPDouble));
//        }
//        else return concreteResult;
        if (AnnotatedValue.svalue(a) != null) {
            losePrecision("Math.sin is not symbolically implemented yet", meta);
            a = AnnotatedValue.value(a);
        }
        return Math.sin((double) a);
    }

    //    @CompilerDirectives.TruffleBoundary
    public static Object mathCos(Object a, Meta meta) {
        if (AnnotatedValue.svalue(a) != null) {
            losePrecision("Math.cos is not symbolically implemented yet", meta);
            a = AnnotatedValue.value(a);
        }
        return Math.cos((double) a);
    }

    public static Object mathAbsInt(Object a) {
        int cRes = Math.abs(AnnotatedValue.value(a));
        if (AnnotatedValue.svalue(a) != null) {
            return new AnnotatedValue(cRes, analysis.mathAbs(AnnotatedValue.value(a), AnnotatedValue.svalue(a)));
        } else {
            return cRes;
        }
    }

    public static Object mathAbsLong(Object a) {
        long cRes = Math.abs((long) AnnotatedValue.value(a));
        if (AnnotatedValue.svalue(a) != null) {
            return new AnnotatedValue(cRes, analysis.mathAbs((long) AnnotatedValue.value(a), AnnotatedValue.svalue(a)));
        } else {
            return cRes;
        }
    }

    public static Object mathAbsFloat(Object a) {
        float cRes = Math.abs((float) AnnotatedValue.value(a));
        if (AnnotatedValue.svalue(a) != null) {
            return new AnnotatedValue(cRes, analysis.mathAbs((float) AnnotatedValue.value(a), AnnotatedValue.svalue(a)));
        } else {
            return cRes;
        }
    }

    public static Object mathAbsDouble(Object a) {
        SPouT.debug("MathAbsDouble1", a.getClass());
        double cRes = Math.abs((double) AnnotatedValue.value(a));
        if (AnnotatedValue.svalue(a) != null) {
            return new AnnotatedValue(cRes, analysis.mathAbs((double) AnnotatedValue.value(a), AnnotatedValue.svalue(a)));
        } else {
            SPouT.debug("MathAbsDouble", cRes);
            return cRes;
        }
    }

    //    @CompilerDirectives.TruffleBoundary
    public static Object mathArcCos(Object a, Meta meta) {
        if (AnnotatedValue.svalue(a) != null) {
            losePrecision("Math.acos is not symbolically implemented yet", meta);
            a = AnnotatedValue.value(a);
        }
        return Math.acos((double) a);
    }

    //    @CompilerDirectives.TruffleBoundary
    public static Object mathArcSin(Object a, Meta meta) {
        if (AnnotatedValue.svalue(a) != null) {
            losePrecision("Math.asin is not symbolically implemented yet", meta);
            a = AnnotatedValue.value(a);
        }
        return Math.asin((double) a);
    }

    //    @CompilerDirectives.TruffleBoundary
    public static Object mathTan(Object a, Meta meta) {
        if (AnnotatedValue.svalue(a) != null) {
            losePrecision("Math.tan is not symbolically implemented yet", meta);
            a = AnnotatedValue.value(a);
        }
        return Math.tan((double) a);
    }

    //    @CompilerDirectives.TruffleBoundary
    public static Object mathATan(Object a, Meta meta) {
        if (AnnotatedValue.svalue(a) != null) {
            losePrecision("Math.atan is not symbolically implemented yet", meta);
            a = AnnotatedValue.value(a);
        }
        return Math.atan((double) a);
    }

    //    @CompilerDirectives.TruffleBoundary
    public static Object mathSqrt(Object a, Meta meta) {
        if (AnnotatedValue.svalue(a) != null) {
            losePrecision("Math.sqrt is not symbolically implemented yet", meta);
            a = AnnotatedValue.value(a);
        }
        return Math.sqrt((double) a);
    }

    //    @CompilerDirectives.TruffleBoundary
    public static Object mathExp(Object a, Meta meta) {
        if (AnnotatedValue.svalue(a) != null) {
            losePrecision("Math.exp is not symbolically implemented yet", meta);
            a = AnnotatedValue.value(a);
        }
        return Math.exp((double) a);
    }

    //    @CompilerDirectives.TruffleBoundary
    public static Object mathRoundF(Object a, Meta meta) {
        if (AnnotatedValue.svalue(a) != null) {
            losePrecision("Math.round is not symbolically implemented yet", meta);
            a = AnnotatedValue.value(a);
        }
        return Math.round((float) a);
    }

    //    @CompilerDirectives.TruffleBoundary
    public static Object mathRoundD(Object a, Meta meta) {
        if (AnnotatedValue.svalue(a) != null) {
            losePrecision("Math.round is not symbolically implemented yet", meta);
            a = AnnotatedValue.value(a);
        }
        return Math.round((double) a);
    }

    //    @CompilerDirectives.TruffleBoundary
    public static double mathLog10(Object a, Meta meta) {
        if (AnnotatedValue.svalue(a) != null) {
            losePrecision("Math.log10 is not symbolically implemented yet", meta);
            a = AnnotatedValue.value(a);
        }
        return Math.log10((double) a);
    }

    //@CompilerDirectives.TruffleBoundary
    public static double mathLog(Object a, Meta meta) {
        if (AnnotatedValue.svalue(a) != null) {
            losePrecision("Math.log is not symbolically implemented yet", meta);
            a = AnnotatedValue.value(a);
        }        return Math.log((double) a);
    }

    public static double mathPow(Object a, Object b, Meta meta) {
        if (AnnotatedValue.svalue(a) != null) {
            losePrecision("Math.pow is not symbolically implemented yet", meta);
            a = AnnotatedValue.value(a);
        }        return Math.pow((double) a, (double) b);
    }

    public static double mathATan2(Object a, Object b, Meta meta) {
        if (AnnotatedValue.svalue(a) != null) {
            losePrecision("Math.atan2 is not symbolically implemented yet", meta);
            a = AnnotatedValue.value(a);
        }
        return Math.atan2((double) a, (double) b);
    }

    // --------------------------------------------------------------------------
    //
    // The String Library

    @CompilerDirectives.TruffleBoundary
    public static Object stringContains(StaticObject self, StaticObject s, Meta meta) {
        String concreteSelf = meta.toHostString(self);
        String other = meta.toHostString(s);
        boolean concreteRes = concreteSelf.contains(other);
        if (!hasStringAnnotations(self) && !hasStringAnnotations(s) || !analyze) {
            return concreteRes;
        }
        Annotations a = analysis.stringContains(concreteSelf,
                other,
                stringContent(self),
                stringContent(s));
        return new AnnotatedValue(concreteRes, a);
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
        Annotations content = stringContent(other);
        if (content != null) {
            setStringAnnotations(self, content);
        }
    }

    @CompilerDirectives.TruffleBoundary
    public static Object stringCompareTo(StaticObject self, StaticObject other, Meta meta) {
        String cSelf = meta.toHostString(self);
        String cOther = meta.toHostString(other);
        int concreteResult = cSelf.compareTo(cOther);
        if (!analyze || !hasStringAnnotations(self) && !hasStringAnnotations(other)) {
            return concreteResult;
        }
        // records the decision on the sign of the result (less, equal, greater)
        analysis.stringCompareTo(cSelf, cOther,
                stringContent(self),
                stringContent(other));
        // the value itself (difference of the first different characters, or of the lengths) is not encoded
        losePrecision("the value of String.compareTo is concretized (its sign is decided)", meta);
        return concreteResult;
    }


    public static @JavaType(internalName = "I") Object stringCompareToIgnoreCase(@JavaType(String.class) StaticObject self, @JavaType(String.class) StaticObject other, @Inject Meta meta) {
        boolean isSelfSymbolic = hasSymbolicContent(self);
        boolean isOtherSymbolic = hasSymbolicContent(other);
        if (isSelfSymbolic || isOtherSymbolic) {losePrecision("StringCompareToIgnoreCase is not yet symobolically implemented", meta);
        return 0;}
        return meta.toHostString(self).compareToIgnoreCase(meta.toHostString(other));
    }

    @CompilerDirectives.TruffleBoundary
    public static Object stringLength(StaticObject self, Meta meta) {
        int length = meta.toHostString(self).length();
        if (!analyze || !hasStringAnnotations(self)) {
            return length;
        }
        Annotations aLength = analysis.stringLength(length, stringContent(self));
        return aLength != null ? new AnnotatedValue(length, aLength) : length;
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject stringConcat(StaticObject self, StaticObject other, Meta meta) {
        String concreteSelf = meta.toHostString(self);
        String concreteOther = meta.toHostString(other);
        StaticObject result = meta.toGuestString(concreteSelf.concat(concreteOther));
        if (!analyze || !hasStringAnnotations(self) && !hasStringAnnotations(other)) {
            return result;
        }
        Annotations a = analysis.stringConcat(concreteSelf,
                concreteOther,
                stringContent(self),
                stringContent(other));
        return setStringAnnotations(result, a);

    }

    @CompilerDirectives.TruffleBoundary
    public static Object stringEquals(StaticObject self, StaticObject other, Meta meta) {
        String cSelf = meta.toHostString(self);
        String cOther = meta.toHostString(other);
        boolean areEqual = cSelf.equals(cOther);
        if (!analyze || !hasStringAnnotations(self) && !hasStringAnnotations(other)) {
            return areEqual;
        }
        Annotations a = analysis.stringEquals(cSelf,
                cOther,
                getStringAnnotations(self),
                getStringAnnotations(other));
        if (a != null) return new AnnotatedValue(areEqual, a);
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

        if (analyze)
            return new AnnotatedValue((int) cString.charAt(index), analysis.charAt(cString, index, getStringAnnotations(self), sIndex));
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
            if (sself != null) {
                a3 = Annotations.emptyArray();
                ComplexExpression sLength = new ComplexExpression(OperatorComparator.SLENGTH, sself);
                Expression tmpBegin = Annotations.annotation(sbegin, config.getConcolicIdx());
                if (tmpBegin == null) {
                    tmpBegin = Constant.createNatConstant(cbegin);
                } else {
                    tmpBegin = new ComplexExpression(OperatorComparator.BV2NAT, tmpBegin);
                }
                a3.set(config.getConcolicIdx(), new ComplexExpression(OperatorComparator.NATMINUS, sLength, tmpBegin));
            }
        }
        try {
            String res = cSelf.substring(cbegin);
            StaticObject result = meta.toGuestString(res);
            if (analyze) {
                Annotations a = analysis.substring(true, cSelf, cbegin, cSelf.length(), getStringAnnotations(self), sbegin, a3);
                setStringAnnotations(result, a);
            }
            return result;
        } catch (IndexOutOfBoundsException e) {
            if (analyze) {
                analysis.substring(false, cSelf, cbegin, cSelf.length(), getStringAnnotations(self), sbegin, a3);
            }
            SPouT.iflowRegisterException();
            meta.throwException(meta.java_lang_StringIndexOutOfBoundsException);
            throw e;
        }
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject substring(StaticObject self, Object begin, Object end, Meta meta) {
        int cBegin = AnnotatedValue.value(begin), cEnd = AnnotatedValue.value(end);
        Annotations sBegin = AnnotatedValue.svalue(begin), sEnd = AnnotatedValue.svalue(end);
        String cSelf = meta.toHostString(self);
        try {
            String res = cSelf.substring(cBegin, cEnd);
            StaticObject result = meta.toGuestString(res);
            if (analyze) {
                Annotations a = analysis.substring(true, cSelf, cBegin, cEnd, getStringAnnotations(self), sBegin, sEnd);
                setStringAnnotations(result, a);
            }
            return result;
        } catch (IndexOutOfBoundsException e) {
            if (analyze) {
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
            losePrecision("Cannot deal with symbolic ignore case for regionMatches yet", meta);
        }
        ignore = (boolean) AnnotatedValue.value(ignoreCase);
        int ctoffset = -1, cooffset = -1, clen = -1;
        if (toffset instanceof AnnotatedValue) {
            losePrecision("Cannot deal with symbolic toffset for regionMatches yet", meta);
        }
        ctoffset = (int) AnnotatedValue.value(toffset);
        if (ooffset instanceof AnnotatedValue) {
            losePrecision("Cannot deal with symbolic ooffset for regionMatches yet", meta);
        }
        cooffset = (int) AnnotatedValue.value(ooffset);
        if (len instanceof AnnotatedValue) {
            losePrecision("Cannot deal with symbolic len for regionMatches yet", meta);
        }
        clen = (int) AnnotatedValue.value(len);
        boolean cres = meta.toHostString(self).regionMatches(ignore, ctoffset, meta.toHostString(other), cooffset, clen);
        if (analyze && config.hasConcolicAnalysis()) {
            boolean isSelfSymbolic = hasSymbolicContent(self);
            boolean isOtherSymbolic = hasSymbolicContent(other);
            if ((isSelfSymbolic || isOtherSymbolic) && analyze && config.hasConcolicAnalysis()) {
                config.getConcolicAnalysis().regionMatches(self, other, ignore, ctoffset, cooffset, clen, cres, meta);
            }
        }
        if (analyze && config.hasTaintAnalysis()) {
            return config.getTaintAnalysis().regionMatches(self, other, cres);
        }
        return cres;
    }

    @CompilerDirectives.TruffleBoundary
    public static Object stringBuxxCharAt(StaticObject self, Object index, Meta meta) {
        Method m = self.getKlass().lookupMethod(meta.getNames().getOrCreate("toString"),
                meta.getLanguage().getSignatures().lookupValidSignature("()Ljava/lang/String;"));
        StaticObject stringValue = (StaticObject) m.invokeDirect(self);
        Annotations content = getStringAnnotations(self);
        if (content != null) {
            setStringAnnotations(stringValue, content);
        }
        return stringCharAt(stringValue, index, meta);
    }

    public static StaticObject stringBuXXAppendString(StaticObject self, Object string, Meta meta) {
        char c = AnnotatedValue.value(string);
        StaticObject guestString = meta.toGuestString(String.valueOf(c));

        Annotations a = AnnotatedValue.svalue(string);
        if (analyze && config.hasConcolicAnalysis() && a != null) {
            a.set(config.getConcolicIdx(), config.getConcolicAnalysis().convertIntToString((Expression) a.getAnnotations()[config.getConcolicIdx()]));
        }
        setStringAnnotations(guestString, a);
        return SPouT.stringBuXXAppendString(self, guestString, meta);
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject stringBuXXAppendString(StaticObject self, StaticObject chars, Object offset, Object length, Meta meta) {
        if (offset instanceof AnnotatedValue || length instanceof AnnotatedValue) {
            SPouT.losePrecision("SPouT does not support append from char array with symbolic indicies yet!", meta);
            offset = AnnotatedValue.value(offset);
            length = AnnotatedValue.value(length);
        }
        Annotations[] a = chars.getAnnotations();
        char[] hChars = chars.unwrap(meta.getLanguage());
        int cOffset = AnnotatedValue.value(offset);
        int cLength = AnnotatedValue.value(length);
        if (cOffset < 0 || cLength < 0 || cOffset + cLength > hChars.length)
            meta.throwException(meta.java_lang_IndexOutOfBoundsException);
        StaticObject other = meta.toGuestString(new String(hChars, cOffset, cLength));
        if (analyze && a != null) {
            Annotations aNew = Annotations.emptyArray();
            if (config.hasConcolicAnalysis()) {
                Expression stringExpr = a[cOffset] == null ? Constant.fromConcreteValue(hChars[cOffset]) : Annotations.annotation(a[cOffset], config.getConcolicIdx());
                for (int i = 1; i < cLength; i++) {
                    int index = cOffset + i;
                    Expression nextChar = a[index] == null ? Constant.fromConcreteValue(hChars[index]) : Annotations.annotation(a[index], config.getConcolicIdx());
                    stringExpr = new ComplexExpression(OperatorComparator.SCONCAT, stringExpr, nextChar);
                }
                aNew.set(config.getConcolicIdx(), stringExpr);
            }
            if (config.hasTaintAnalysis()) {
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
            if (selfAnnotations == null) {
                initStringAnnotations(self);
                selfAnnotations = self.getAnnotations();
            }
            selfAnnotations[selfAnnotations.length - 1] = a;
        }
        Annotations[] stringAnnotations = string.getAnnotations();
        self.setAnnotations(null);
        string.setAnnotations(null);
        Method m = self.getKlass().getSuperKlass().lookupMethod(meta.getNames().getOrCreate("append"), EspressoSymbols.Signatures.java_lang_AbstractStringBuilder_java_lang_String);
        m.invokeDirect(self, string);
        self.setAnnotations(selfAnnotations);
        string.setAnnotations(stringAnnotations);
        if (analyze && selfAnnotations != null) {
            setBuilderContent(self, selfAnnotations[selfAnnotations.length - 1]);
        }
        return self;
    }

    @CompilerDirectives.TruffleBoundary
    public static Object stringBuxxLength(StaticObject self, Meta meta) {
        Method m = self.getKlass().getSuperKlass().lookupMethod(meta.getNames().getOrCreate("length"), EspressoSymbols.Signatures._int);
        Annotations[] a = self.getAnnotations();
        self.setAnnotations(null);
        Object invokeResult = m.invokeDirect(self);
        self.setAnnotations(a);
        int cresult = (int) invokeResult;
        if (analyze) {
            Annotations ana = analysis.stringBuxxLength(meta.toHostString(self), getStringAnnotations(self));
            if (ana != null) return new AnnotatedValue(cresult, ana);
        }
        return cresult;
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject stringBuxxToString(StaticObject self, Meta meta) {
        StaticObject result = concreteBuilderString(self, meta);
        if (analyze && config.hasConcolicAnalysis()) {
            setStringAnnotations(result,
                    analysis.stringBuxxToString(meta.toHostString(result), getStringAnnotations(self)));
        }
        return result;
    }

    /**
     * A new string with the concrete characters of a builder, from its internal fields, without analysis (not via
     * toString, which may be substituted).
     */
    @CompilerDirectives.TruffleBoundary
    private static StaticObject concreteBuilderString(StaticObject self, Meta meta) {
        Klass abstractSB = self.getKlass().getSuperKlass();
        Method getValue = abstractSB.lookupMethod(meta.getNames().getOrCreate("getValue"), EspressoSymbols.Signatures._byte_array);
        Method length = abstractSB.lookupMethod(meta.getNames().getOrCreate("length"), EspressoSymbols.Signatures._int);
        Method isLatin = abstractSB.lookupMethod(meta.getNames().getOrCreate("isLatin1"), EspressoSymbols.Signatures._boolean);
        Annotations[] a = self.getAnnotations();
        self.setAnnotations(null);
        pauseAnalyze();
        try {
            StaticObject bytes = (StaticObject) getValue.invokeDirect(self);
            int ilength = AnnotatedValue.value(length.invokeDirect(self));
            return (boolean) AnnotatedValue.value(isLatin.invokeDirect(self))
                    ? (StaticObject) meta.java_lang_StringLatin1_newString.invokeDirect(bytes, 0, ilength)
                    : (StaticObject) meta.java_lang_StringUTF16_newString.invokeDirect(bytes, 0, ilength);
        } finally {
            resumeAnalyze();
            self.setAnnotations(a);
        }
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject stringBuxxInsert(StaticObject self, Object offset, Object toInsert, Meta meta) {
        if (toInsert instanceof AnnotatedValue) {
            losePrecision("Cannot insert symbolic chars to StringBuffer", meta);
            toInsert = AnnotatedValue.value(toInsert);
        }
        StaticObject toInsertCasted = meta.toGuestString(String.valueOf((char) toInsert));
        return stringBuxxInsert(self, offset, toInsertCasted, meta);
    }

    public static void initStringBuxxString(StaticObject self, StaticObject other, Meta meta) {
        Method m = self.getKlass().getSuperKlass().lookupMethod(EspressoSymbols.Names._init_, EspressoSymbols.Signatures._void_String);
        Annotations[] selfAnnotations = self.getAnnotations();
        if (analyze) {
            Annotations a = analysis.stringBuilderAppend("",
                    meta.toHostString(other),
                    null, //getStringAnnotations(self),
                    getStringAnnotations(other));
            if (selfAnnotations == null) {
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
            SPouT.losePrecision("Cannot handle symbolic offset values for insert into StringBu* yet.", meta);
        }
        int concreteOffset = (int) AnnotatedValue.value(offset);
        if (analyze) {
            Annotations a = analysis.stringBuxxInsert(meta.toHostString(self),
                    meta.toHostString(toInsert),
                    concreteOffset,
                    getStringAnnotations(self),
                    getStringAnnotations(toInsert),
                    null);
            setBuilderContent(self, a);
        }
        Method m = self.getKlass().getSuperKlass().lookupMethod(meta.getNames().getOrCreate("insert"), EspressoSymbols.Signatures.java_lang_AbstractStringBuilder_int_java_lang_String);
        return (StaticObject) m.invokeDirect(self, concreteOffset, toInsert);
    }

    @CompilerDirectives.TruffleBoundary
    public static void stringBuxxGetChars(StaticObject self, Object srcBegin, Object srcEnd, StaticObject dst, Object dstBegin, Meta meta) {
        boolean symbolicIndex = analyze && config.hasConcolicAnalysis()
                && (isSymbolic(srcBegin) || isSymbolic(srcEnd) || isSymbolic(dstBegin));
        if (symbolicIndex) {
            SPouT.losePrecision("getChars with symbolic indices is not supported", meta);
        }
        int cSrcBegin = AnnotatedValue.value(srcBegin);
        int cSrcEnd = AnnotatedValue.value(srcEnd);
        int cDstBegin = AnnotatedValue.value(dstBegin);
        // the content of the builder (before the concrete call, which does not change it)
        Annotations content = analyze && config.hasConcolicAnalysis() ? getStringAnnotations(self) : null;
        String cContent = meta.toHostString(self);
        Method m = self.getKlass().getSuperKlass().lookupMethod(meta.getNames().getOrCreate("getChars"), EspressoSymbols.Signatures._void_int_int_char_array_int);
        Annotations[] selfAnnotations = self.getAnnotations();
        self.setAnnotations(null);
        try {
            m.invokeDirect(self, cSrcBegin, cSrcEnd, dst, cDstBegin);
        } finally {
            self.setAnnotations(selfAnnotations);
        }
        Expression e = Annotations.annotation(content, config.getConcolicIdx());
        if (e != null && !symbolicIndex) {
            copyCharsSymbolically(cContent, e, cSrcBegin, cSrcEnd, dst, cDstBegin, true, meta);
        }
    }

    /**
     * The characters {@code srcBegin .. srcEnd - 1} of a string with symbolic content, copied to {@code dst} from
     * {@code dstBegin} (getChars, toCharArray): one element annotation per character (concrete range; a symbolic
     * range would need array theory). The concrete copy has already succeeded, so the range is within the string:
     * recorded as a decision on the symbolic length.
     */
    private static void copyCharsSymbolically(String cContent, Expression content, int srcBegin, int srcEnd,
                                              StaticObject dst, int dstBegin, boolean decideBounds, Meta meta) {
        tools.aqua.concolic.ConcolicAnalysis ca = config.getConcolicAnalysis();
        if (decideBounds) {
            // in integers: srcEnd <= length (the decision is recorded for every call, at the same position)
            ca.decide(new ComplexExpression(OperatorComparator.LE, Constant.createNatConstant(srcEnd),
                    new ComplexExpression(OperatorComparator.SLENGTH, content)), srcEnd <= cContent.length());
        }
        int bound = config.getUnrollBound();
        if (bound > 0 && srcEnd - srcBegin > bound) {
            losePrecision("copying more than " + bound + " characters of a symbolic string (concolic.unroll.bound)", meta);
            return;
        }
        if (ca.getHeap().hasContentTerm(dst)) {
            losePrecision("copying characters into an array with symbolic elements is not symbolically implemented",
                    meta);
            return;
        }
        for (int k = 0; k < srcEnd - srcBegin; k++) {
            Annotations a = Annotations.create();
            a.set(config.getConcolicIdx(), ca.charAt(cContent, srcBegin + k, content, null));
            AnnotatedVM.setArrayAnnotations(dst, dstBegin + k, a, meta.getLanguage());
        }
    }

    /** {@code self.getChars(srcBegin, srcEnd, dst, dstBegin)} of a String */
    @CompilerDirectives.TruffleBoundary
    public static void stringGetChars(StaticObject self, Object srcBegin, Object srcEnd, StaticObject dst, Object dstBegin, Meta meta) {
        int cSrcBegin = AnnotatedValue.value(srcBegin);
        int cSrcEnd = AnnotatedValue.value(srcEnd);
        int cDstBegin = AnnotatedValue.value(dstBegin);
        String cContent = meta.toHostString(self);
        if (StaticObject.isNull(dst)) {
            throw meta.throwNullPointerException();
        }
        char[] target = dst.unwrap(meta.getLanguage());
        // throws the same exceptions as String.getChars (host exceptions are translated by the caller)
        if (cSrcBegin < 0 || cSrcBegin > cSrcEnd || cSrcEnd > cContent.length()
                || cDstBegin < 0 || cDstBegin + (cSrcEnd - cSrcBegin) > target.length) {
            throw meta.throwExceptionWithMessage(meta.java_lang_StringIndexOutOfBoundsException,
                    "begin " + cSrcBegin + ", end " + cSrcEnd + ", length " + cContent.length());
        }
        cContent.getChars(cSrcBegin, cSrcEnd, target, cDstBegin);
        if (!analyze || !config.hasConcolicAnalysis() || !hasSymbolicContent(self)) {
            return;
        }
        if (isSymbolic(srcBegin) || isSymbolic(srcEnd) || isSymbolic(dstBegin)) {
            losePrecision("getChars with symbolic indices is not supported", meta);
            return;
        }
        copyCharsSymbolically(cContent, Annotations.annotation(stringContent(self), config.getConcolicIdx()),
                cSrcBegin, cSrcEnd, dst, cDstBegin, true, meta);
    }

    /** {@code self.toCharArray()}: the length of the array is the (symbolic) length of the string */
    @CompilerDirectives.TruffleBoundary
    public static StaticObject stringToCharArray(StaticObject self, Meta meta) {
        String cContent = meta.toHostString(self);
        StaticObject result = StaticObject.wrap(cContent.toCharArray(), meta);
        if (!analyze || !config.hasConcolicAnalysis() || !hasSymbolicContent(self)) {
            return result;
        }
        Expression content = Annotations.annotation(stringContent(self), config.getConcolicIdx());
        // the whole string: the range needs no decision
        copyCharsSymbolically(cContent, content, 0, cContent.length(), result, 0, false, meta);
        Annotations length = Annotations.create();
        length.set(config.getConcolicIdx(), config.getConcolicAnalysis().stringLengthExpression(content));
        Annotations[] annotations = result.getAnnotations();
        if (annotations == null) {
            annotations = new Annotations[cContent.length() + 1];
            result.setAnnotations(annotations);
        }
        annotations[cContent.length()] = length;
        return result;
    }

    public static void setBuxxCharAt(StaticObject self, Object i, Object ch, Meta meta) {
        if (analyze && config.hasConcolicAnalysis() && (AnnotatedValue.annotation(AnnotatedValue.svalue(i), config.getConcolicIdx()) != null ||
                AnnotatedValue.annotation(AnnotatedValue.svalue(ch), config.getConcolicIdx()) != null)) {
            losePrecision("Symbolic index and symbolic chars are not supported", meta);
        }
        int index = (int) AnnotatedValue.value(i);
        char cha = (char) AnnotatedValue.value(ch);
        String val = String.valueOf(cha);
        Method m = self.getKlass().getSuperKlass().lookupMethod(meta.getNames().getOrCreate("setCharAt"), EspressoSymbols.Signatures._void_int_char);
        Annotations[] a = self.getAnnotations();
        if (analyze && config.hasConcolicAnalysis() && self.hasAnnotations()) {
            self.setAnnotations(null);
            Method toString = self.getKlass().lookupMethod(meta.getNames().getOrCreate("toString"), EspressoSymbols.Signatures.String);
            StaticObject buxxStr = (StaticObject) toString.invokeDirect(self);
            String buxx = meta.toHostString(buxxStr);
            analysis.charAtPCCheck(buxx, index, Annotations.annotation(a, -1), AnnotatedValue.svalue(i));
        }
        self.setAnnotations(null);
        m.invokeDirect(self, i, ch);
        self.setAnnotations(a);
        if (analyze) {
            setBuilderContent(self,
                    analysis.stringBuxxCharAt(meta.toHostString(self), val, index, getStringAnnotations(self), null, null));
        }
    }


    public static Object characterToUpperCase(Object c, Meta meta) {
        char cChar = AnnotatedValue.value(c);
        Annotations sChar = AnnotatedValue.svalue(c);
        char cRes = Character.toUpperCase(cChar);
        if (analyze) {
            Annotations a = analysis.characterToUpperCase(cChar, sChar);
            if (a != null) return new AnnotatedValue(cRes, a);
        }
        return cRes;
    }

    public static Object characterToLowerCase(Object c, Meta meta) {
        char cChar = AnnotatedValue.value(c);
        Annotations sChar = AnnotatedValue.svalue(c);
        char cRes = Character.toLowerCase(cChar);
        if (analyze) {
            Annotations a = analysis.characterToLowerCase(cChar, sChar);
            if (a != null) return new AnnotatedValue(cRes, a);
        }
        return cRes;
    }

    public static Object isCharDefined(Object c, Meta meta) {
        char cChar = AnnotatedValue.value(c);
        Annotations sChar = AnnotatedValue.svalue(c);
        boolean res = Character.isDefined(cChar);
        if (analyze) {
            Annotations a = analysis.isCharDefined(cChar, sChar);
            if (a != null) return new AnnotatedValue(res, a);
        }
        return res;
    }

    public static Object characterIsAlphabetic(Object codePoint, Meta meta) {
        if (AnnotatedValue.svalue(codePoint) != null)
            losePrecision("Character.isAlphabetic is not symbolically implemented yet", meta);
        return Character.isAlphabetic((int)AnnotatedValue.value(codePoint));
    }

    public static Object characterIsJavaIdentifierStart(Object codePoint, Meta meta) {
        if (AnnotatedValue.svalue(codePoint) != null)
            losePrecision("Character.isJavaIdentifierStart is not symbolically implemented yet", meta);
        return Character.isJavaIdentifierStart((int)AnnotatedValue.value(codePoint));
    }

    public static Object characterIsJavaIdentifierPart(Object codePoint, Meta meta) {
        if (AnnotatedValue.svalue(codePoint) != null)
            losePrecision("Character.isJavaIdentifierPart is not symbolically implemented yet", meta);
        return Character.isJavaIdentifierPart((int)AnnotatedValue.value(codePoint));
    }

    public static Object characterIsUnicodeIdentifierPart(Object codePoint, Meta meta) {
        if (AnnotatedValue.svalue(codePoint) != null)
            losePrecision("Character.isUnicodeIdentifiertPart is not symbolically implemented yet", meta);
        return Character.isUnicodeIdentifierPart((int)AnnotatedValue.value(codePoint));
    }

    public static Object characterIsUnicodeIdentifierStart(Object codePoint, Meta meta) {
        if (AnnotatedValue.svalue(codePoint) != null)
            losePrecision("Character.isUnicodeIdentifiertStart is not symbolically implemented yet", meta);
        return Character.isUnicodeIdentifierStart((int)AnnotatedValue.value(codePoint));
    }

    public static Object characterIsIdentiferIgnorable(Object codePoint, Meta meta) {
        if (AnnotatedValue.svalue(codePoint) != null)
            losePrecision("Character.isIdentifierIgnorable is not symbolically implemented yet", meta);
        return Character.isIdentifierIgnorable((int)AnnotatedValue.value(codePoint));
    }

    public static Object characterIsLetterOrDigit(Object codePoint, Meta meta) {
        if (AnnotatedValue.svalue(codePoint) != null)
            losePrecision("Character.isLetterOrDigit is not symbolically implemented yet", meta);
        return Character.isLetterOrDigit((int)AnnotatedValue.value(codePoint));
    }

    public static Object characterIsLetter(Object codePoint, Meta meta) {
        if (AnnotatedValue.svalue(codePoint) != null)
            losePrecision("Character.isLetter is not symbolically implemented yet", meta);
        return Character.isLetter((int)AnnotatedValue.value(codePoint));
    }

    public static Object characterIsLowerCase(Object codePoint, Meta meta) {
        if (AnnotatedValue.svalue(codePoint) != null)
            losePrecision("Character.isLowerCase is not symbolically implemented yet", meta);
        return Character.isLowerCase((int)AnnotatedValue.value(codePoint));
    }

    public static Object characterIsMirrored(Object codePoint, Meta meta) {
        if (AnnotatedValue.svalue(codePoint) != null)
            losePrecision("Character.isMirrored is not symbolically implemented yet", meta);
        return Character.isMirrored((int)AnnotatedValue.value(codePoint));
    }

    public static Object characterIsSpaceChar(Object codePoint, Meta meta) {
        if (AnnotatedValue.svalue(codePoint) != null)
            losePrecision("Character.isSpaceChar is not symbolically implemented yet", meta);
        return Character.isSpaceChar((int)AnnotatedValue.value(codePoint));
    }

    public static Object characterIsSpace(Object codePoint, Meta meta) {
        if (AnnotatedValue.svalue(codePoint) != null)
            losePrecision("Character.isSpace is not symbolically implemented yet", meta);
        return Character.isSpace(AnnotatedValue.value(codePoint));
    }

    public static Object characterIsTitleCase(Object codePoint, Meta meta) {
        if (AnnotatedValue.svalue(codePoint) != null)
            losePrecision("Character.isTitleCase is not symbolically implemented yet", meta);
        return Character.isTitleCase((int)AnnotatedValue.value(codePoint));
    }

    public static Object characterIsUpperCase(Object codePoint, Meta meta) {
        if (AnnotatedValue.svalue(codePoint) != null)
            losePrecision("Character.isUpperCase is not symbolically implemented yet", meta);
        return Character.isUpperCase((int)AnnotatedValue.value(codePoint));
    }

    public static Object characterIsWhitespace(Object codePoint, Meta meta) {
        if (AnnotatedValue.svalue(codePoint) != null)
            losePrecision("Character.isWhitespace is not symbolically implemented yet", meta);
        return Character.isWhitespace((int)AnnotatedValue.value(codePoint));
    }

    public static Object characterGetDirectionality(Object codePoint, Meta meta) {
        if (AnnotatedValue.svalue(codePoint) != null)
            losePrecision("Character.getDirectionality is not symbolically implemented yet", meta);
        return Character.getDirectionality((int)AnnotatedValue.value(codePoint));
    }

    public static Object characterDigit(Object codePoint, Object radix, Meta meta) {
        if (AnnotatedValue.svalue(codePoint) != null)
            losePrecision("Character.digit is not symbolically implemented yet", meta);
        if (AnnotatedValue.svalue(radix) != null)
            losePrecision("Character.digit is not symbolically implemented yet", meta);
        return Character.digit((int) AnnotatedValue.value(codePoint), AnnotatedValue.value(radix));
    }

    public static Object characterGetNumericValue(Object codePoint, Meta meta) {
        if (AnnotatedValue.svalue(codePoint) != null)
            losePrecision("Character.getNumericValue is not symbolically implemented yet", meta);
        return Character.getNumericValue((int)AnnotatedValue.value(codePoint));
    }

    public static Object characterGetType(Object codePoint, Meta meta) {
        if (AnnotatedValue.svalue(codePoint) != null)
            losePrecision("Character.getType is not symbolically implemented yet", meta);
        return Character.getType((int) AnnotatedValue.value(codePoint));
    }

    public static Object characterEquals(StaticObject self, StaticObject other, Meta meta) {
        Method m = self.getKlass().lookupMethod(meta.getNames().getOrCreate("charValue"), EspressoSymbols.Signatures._char);
        Annotations[] aSelf = self.getAnnotations();
        Annotations[] aOther = other.getAnnotations();
        self.setAnnotations(null);
        other.setAnnotations(null);
        char cself = (char) m.invokeDirect(self);
        char cother = (char) m.invokeDirect(other);
        self.setAnnotations(aSelf);
        other.setAnnotations(aOther);
        boolean cres = cself == cother;
        if (analyze) {
            Annotations a = analysis.characterEquals(cself, cother, getStringAnnotations(self), getStringAnnotations(other));
            if (a != null) return new AnnotatedValue(cres, a);
        }
        return cres;
    }

    @CompilerDirectives.TruffleBoundary
    public static Object stringIsEmpty(@JavaType(String.class) StaticObject self, @Inject Meta meta) {
        boolean cRes = meta.toHostString(self).isEmpty();
        if (!analyze || !config.hasConcolicAnalysis() || !hasSymbolicContent(self)) {
            return cRes;
        }
        return symbolicResult(cRes, config.getConcolicAnalysis().stringIsEmpty(stringExpression(self, meta)));
    }

    /** the symbolic content of a string, or its concrete content */
    private static Expression stringExpression(StaticObject s, Meta meta) {
        Expression e = Annotations.annotation(stringContent(s), config.getConcolicIdx());
        return e != null ? e : Constant.fromConcreteValue(meta.toHostString(s));
    }

    /** the symbolic value of an int argument (characters as ints), or its concrete value */
    private static Expression intExpression(Object v) {
        Expression e = Annotations.annotation(AnnotatedValue.svalue(v), config.getConcolicIdx());
        return e != null ? config.getConcolicAnalysis().convertCharToInt(e)
                : Constant.fromConcreteValue((int) AnnotatedValue.<Integer>value(v));
    }

    /**
     * whether a value has a concolic (symbolic) annotation, or is an array whose elements are a content term
     * (a substituted or native method would see its concrete elements only)
     */
    @CompilerDirectives.TruffleBoundary
    public static boolean isConcolic(Object v) {
        if (config == null || !config.hasConcolicAnalysis()) {
            return false;
        }
        if (v instanceof StaticObject && !StaticObject.isNull((StaticObject) v) && ((StaticObject) v).isArray()) {
            // also called before the analysis is set up (e.g. when threads are created)
            tools.aqua.concolic.ConcolicAnalysis ca = config.getConcolicAnalysis();
            return ca != null && ca.getHeap() != null && ca.getHeap().hasContentTerm((StaticObject) v);
        }
        return v instanceof AnnotatedValue && isSymbolic(v);
    }

    private static boolean isSymbolic(Object v) {
        return Annotations.annotation(AnnotatedValue.svalue(v), config.getConcolicIdx()) != null;
    }

    private static Object symbolicResult(Object concrete, Expression e) {
        Annotations a = Annotations.create();
        a.set(config.getConcolicIdx(), e);
        return new AnnotatedValue(concrete, a);
    }

    /** {@code self.indexOf(cp, start)} for a character code {@code cp} */
    @CompilerDirectives.TruffleBoundary
    public static Object stringIndexOfIntInt(StaticObject self, Object cp, Object start, Meta meta) {
        int cCp = AnnotatedValue.value(cp);
        int cRes = meta.toHostString(self).indexOf(cCp, (int) AnnotatedValue.<Integer>value(start));
        if (!analyze || !config.hasConcolicAnalysis()
                || !(hasSymbolicContent(self) || isSymbolic(cp) || isSymbolic(start))) {
            return cRes;
        }
        Expression ch = intExpression(cp);
        if (isSymbolic(cp)) {
            config.getConcolicAnalysis().decideBasicPlane(ch, cCp);
        }
        if (cCp > 0xFFFF) {
            losePrecision("String.indexOf(int) for a character beyond the basic multilingual plane is not "
                    + "symbolically implemented", meta);
            return cRes;
        }
        String cSelf = meta.toHostString(self);
        int cStart = Math.max(0, (int) AnnotatedValue.<Integer>value(start));
        if (!hasSymbolicContent(self) && !isSymbolic(start) && cSelf.length() - cStart <= 256) {
            // a concrete string (e.g. the delimiters of a StringTokenizer): comparisons of the character with each
            // character of the string, without string theory
            Expression chain = Constant.fromConcreteValue(-1);
            for (int k = cSelf.length() - 1; k >= cStart; k--) {
                chain = new ComplexExpression(OperatorComparator.ITE,
                        new ComplexExpression(OperatorComparator.BVEQ, ch, Constant.fromConcreteValue((int) cSelf.charAt(k))),
                        Constant.fromConcreteValue(k), chain);
            }
            return symbolicResult(cRes, chain);
        }
        return symbolicResult(cRes, config.getConcolicAnalysis().stringIndexOfChar(stringExpression(self, meta), ch,
                intExpression(start)));
    }

    @CompilerDirectives.TruffleBoundary
    public static Object stringIndexOf(StaticObject self, StaticObject other, Meta meta){
        return stringIndexOfWithStart(self, other, 0, meta);
    }

    /** {@code self.indexOf(other, start)} */
    @CompilerDirectives.TruffleBoundary
    public static Object stringIndexOfWithStart(StaticObject self, StaticObject other, Object start, Meta meta){
        int cRes = meta.toHostString(self).indexOf(meta.toHostString(other), (int) AnnotatedValue.<Integer>value(start));
        if (!analyze || !config.hasConcolicAnalysis()
                || !(hasSymbolicContent(self) || hasSymbolicContent(other) || isSymbolic(start))) {
            return cRes;
        }
        return symbolicResult(cRes, config.getConcolicAnalysis().stringIndexOf(stringExpression(self, meta),
                stringExpression(other, meta), intExpression(start)));
    }

    @CompilerDirectives.TruffleBoundary
    public static Object split(StaticObject self, StaticObject regex, Meta meta) {
        return split(self, regex, 0, meta);
    }

    /**
     * {@code self.split(regex, limit)}. For a literal separator (Java's fast path: a character that is not a
     * metacharacter of regular expressions, or an escaped one; or a string without metacharacters), the split is
     * unrolled like a loop over {@code indexOf}: one decision per separator ({@code concolic.unroll.bound} bounds
     * their number), the parts are substrings of the symbolic string. With limit 0, trailing empty strings are
     * removed (one decision each). Other regular expressions lose precision.
     */
    @CompilerDirectives.TruffleBoundary
    public static Object split(StaticObject self, StaticObject regex, int limit, Meta meta) {
        String s = meta.toHostString(self);
        String r = meta.toHostString(regex);
        String[] res = s.split(r, limit);
        StaticObject[] resSO = new StaticObject[res.length];
        for (int i = 0; i < res.length; i++) {
            resSO[i] = meta.toGuestString(res[i]);
        }
        StaticObject result = StaticObject.createArray(self.getKlass().array(), resSO, meta.getContext());
        if (!analyze || !config.hasConcolicAnalysis() || !(hasSymbolicContent(self) || hasSymbolicContent(regex))) {
            return result;
        }
        String separator = literalSeparator(r);
        if (separator == null || hasSymbolicContent(regex)) {
            losePrecision("String.split with a regular expression (or a symbolic one) is not symbolically implemented", meta);
            return result;
        }
        tools.aqua.concolic.ConcolicAnalysis ca = config.getConcolicAnalysis();
        // positions in the integer domain of the string theory (no conversions to bit-vectors, which are hard for
        // the solver): the start position of the search is always within the string
        Expression str = stringExpression(self, meta);
        Expression sep = Constant.fromConcreteValue(separator);
        Expression sepLength = Constant.createNatConstant(separator.length());
        Expression zero = Constant.createNatConstant(0);
        List<Expression> parts = new ArrayList<>();
        List<String> cParts = new ArrayList<>();
        Expression from = zero;
        int cFrom = 0;
        int bound = config.getUnrollBound();
        while (true) {
            if (limit > 0 && parts.size() == limit - 1) {
                break;   // the last part is the rest of the string
            }
            if (bound > 0 && parts.size() >= bound) {
                losePrecision("String.split: more than " + bound + " separators (concolic.unroll.bound)", meta);
                return result;
            }
            Expression index = new ComplexExpression(OperatorComparator.SINDEXOF, str, sep, from);
            int cIndex = s.indexOf(separator, cFrom);
            ca.decide(new ComplexExpression(OperatorComparator.GE, index, zero), cIndex >= 0);
            if (cIndex < 0) {
                break;
            }
            parts.add(new ComplexExpression(OperatorComparator.SSUBSTR, str, from,
                    new ComplexExpression(OperatorComparator.NATMINUS, index, from)));
            cParts.add(s.substring(cFrom, cIndex));
            from = new ComplexExpression(OperatorComparator.NATADD, index, sepLength);
            cFrom = cIndex + separator.length();
        }
        if (parts.isEmpty()) {
            // no separator: the result is the string itself
            return annotateParts(result, List.of(str), meta);
        }
        parts.add(new ComplexExpression(OperatorComparator.SSUBSTR, str, from,
                new ComplexExpression(OperatorComparator.NATMINUS,
                        new ComplexExpression(OperatorComparator.SLENGTH, str), from)));
        cParts.add(s.substring(cFrom));
        if (limit == 0) {
            // trailing empty strings are removed
            while (!parts.isEmpty()) {
                boolean empty = cParts.get(cParts.size() - 1).isEmpty();
                ca.decide(new ComplexExpression(OperatorComparator.BVEQ,
                        new ComplexExpression(OperatorComparator.SLENGTH, parts.get(parts.size() - 1)), zero), empty);
                if (!empty) {
                    break;
                }
                parts.remove(parts.size() - 1);
                cParts.remove(cParts.size() - 1);
            }
        }
        if (!cParts.equals(Arrays.asList(res))) {
            losePrecision("String.split: unexpected result of the symbolic split", meta);
            return result;
        }
        return annotateParts(result, parts, meta);
    }

    /** the state of a {@code java.util.StringTokenizer} (concrete, and the symbolic content of its string) */
    private static final class Tokenizer {
        final String str;
        final String delimiters;
        final int maxPosition;
        final boolean retDelims;
        final boolean hasSurrogates;
        /** the symbolic content of the string, or null */
        final Expression symbolicStr;
        final boolean symbolicDelimiters;

        Tokenizer(StaticObject self, Meta meta) {
            Field fStr = tokenizerField(self, "str", EspressoSymbols.Types.java_lang_String, meta);
            Field fDelims = tokenizerField(self, "delimiters", EspressoSymbols.Types.java_lang_String, meta);
            StaticObject s = fStr.getObject(self);
            StaticObject d = fDelims.getObject(self);
            if (StaticObject.isNull(d)) {
                throw meta.throwNullPointerException();
            }
            str = meta.toHostString(s);
            delimiters = meta.toHostString(d);
            maxPosition = tokenizerField(self, "maxPosition", EspressoSymbols.Types._int, meta).getInt(self);
            retDelims = tokenizerField(self, "retDelims", EspressoSymbols.Types._boolean, meta).getBoolean(self);
            hasSurrogates = tokenizerField(self, "hasSurrogates", EspressoSymbols.Types._boolean, meta).getBoolean(self);
            boolean concolic = analyze && config.hasConcolicAnalysis();
            symbolicStr = concolic ? fieldStringContent(self, fStr, s) : null;
            symbolicDelimiters = concolic && fieldStringContent(self, fDelims, d) != null;
        }

        boolean isDelimiter(int c) {
            return delimiters.indexOf(c) >= 0;
        }

        int codePointAt(int pos) {
            return hasSurrogates ? str.codePointAt(pos) : str.charAt(pos);
        }

        int next(int pos, int c) {
            return hasSurrogates ? pos + Character.charCount(c) : pos + 1;
        }

        /** whether positions can be symbolic: one char per code unit, no tokens of delimiters */
        boolean symbolic(Object startPos, String method, Meta meta) {
            if (!analyze || !config.hasConcolicAnalysis() || (symbolicStr == null && !isSymbolic(startPos))) {
                return false;
            }
            if (retDelims || hasSurrogates || symbolicDelimiters) {
                losePrecision("StringTokenizer." + method + " with returned delimiters, surrogates, or symbolic "
                        + "delimiters is not symbolically implemented", meta);
                return false;
            }
            return true;
        }

        Expression strExpression() {
            return symbolicStr != null ? symbolicStr : Constant.fromConcreteValue(str);
        }

        /** the distinct delimiter characters */
        String distinctDelimiters() {
            StringBuilder sb = new StringBuilder();
            for (char c : delimiters.toCharArray()) {
                if (sb.indexOf(String.valueOf(c)) < 0) {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
    }

    private static Field tokenizerField(StaticObject self, String name,
                                        com.oracle.truffle.espresso.classfile.descriptors.Symbol<
                                                com.oracle.truffle.espresso.classfile.descriptors.Type> type,
                                        Meta meta) {
        return self.getKlass().lookupField(meta.getNames().getOrCreate(name), type);
    }

    /** the symbolic content of the string in field {@code f} of {@code holder}, or null */
    private static Expression fieldStringContent(StaticObject holder, Field f, StaticObject s) {
        if (StaticObject.isNull(s)) {
            return null;
        }
        Expression e = (Expression) Annotations.annotation(Annotations.objectAnnotation(s), config.getConcolicIdx());
        if (e != null) {
            return e;
        }
        Object t = Annotations.annotation(AnnotatedVM.getFieldAnnotation(holder, f), config.getConcolicIdx());
        if (config.isSymbolicStringFields() && tools.aqua.concolic.HeapModel.isObjectTerm(t)) {
            return config.getConcolicAnalysis().getHeap().contentTerm((Expression) t);
        }
        return null;
    }

    /**
     * {@code StringTokenizer.skipDelimiters(startPos)}: the position of the first character that is no delimiter.
     * Symbolically two decisions per position (within the string, a delimiter).
     */
    @CompilerDirectives.TruffleBoundary
    public static Object tokenizerSkipDelimiters(StaticObject self, Object startPos, Meta meta) {
        Tokenizer t = new Tokenizer(self, meta);
        int start = AnnotatedValue.value(startPos);
        int pos = start;
        while (!t.retDelims && pos < t.maxPosition) {
            int c = t.codePointAt(pos);
            if (!t.isDelimiter(c)) {
                break;
            }
            pos = t.next(pos, c);
        }
        if (!t.symbolic(startPos, "skipDelimiters", meta)) {
            return pos;
        }
        tools.aqua.concolic.ConcolicAnalysis ca = config.getConcolicAnalysis();
        Expression s = t.strExpression();
        Expression len = ca.stringLengthExpression(s);
        Expression p = intExpression(startPos);
        String delims = t.distinctDelimiters();
        for (int c = start; ; c++) {
            boolean inBounds = c < t.maxPosition;
            ca.decide(new ComplexExpression(OperatorComparator.BVLT, p, len), inBounds);
            if (!inBounds) {
                break;
            }
            boolean delimiter = t.isDelimiter(t.str.charAt(c));
            Expression at = new ComplexExpression(OperatorComparator.SAT, s,
                    new ComplexExpression(OperatorComparator.BV2NAT, p));
            Expression isDelimiter = null;
            for (char d : delims.toCharArray()) {
                Expression eq = new ComplexExpression(OperatorComparator.STRINGEQ, at,
                        Constant.fromConcreteValue(String.valueOf(d)));
                isDelimiter = isDelimiter == null ? eq : new ComplexExpression(OperatorComparator.BOR, isDelimiter, eq);
            }
            if (isDelimiter == null) {
                // no delimiters: the loop stops at once
                break;
            }
            ca.decide(isDelimiter, delimiter);
            if (!delimiter) {
                break;
            }
            p = new ComplexExpression(OperatorComparator.IADD, p, Constant.fromConcreteValue(1));
        }
        return symbolicResult(pos, p);
    }

    /**
     * {@code StringTokenizer.scanToken(startPos)}: the position of the next delimiter, or the end of the string.
     * Symbolically the minimum of {@code indexOf} over the delimiters, without decisions.
     */
    @CompilerDirectives.TruffleBoundary
    public static Object tokenizerScanToken(StaticObject self, Object startPos, Meta meta) {
        Tokenizer t = new Tokenizer(self, meta);
        int start = AnnotatedValue.value(startPos);
        int pos = start;
        while (pos < t.maxPosition) {
            int c = t.codePointAt(pos);
            if (t.isDelimiter(c)) {
                break;
            }
            pos = t.next(pos, c);
        }
        if (t.retDelims && start == pos && pos < t.maxPosition) {
            int c = t.codePointAt(pos);
            if (t.isDelimiter(c)) {
                pos = t.next(pos, c);
            }
        }
        if (!t.symbolic(startPos, "scanToken", meta)) {
            return pos;
        }
        tools.aqua.concolic.ConcolicAnalysis ca = config.getConcolicAnalysis();
        Expression s = t.strExpression();
        Expression p = intExpression(startPos);
        Expression end = ca.stringLengthExpression(s);
        Expression zero = Constant.fromConcreteValue(0);
        for (char d : t.distinctDelimiters().toCharArray()) {
            Expression delimiter = Constant.fromConcreteValue(String.valueOf(d));
            Expression natIndex = new ComplexExpression(OperatorComparator.SINDEXOF, s, delimiter,
                    new ComplexExpression(OperatorComparator.BV2NAT, p));
            // the delimiter is at the position found (which the solver does not derive easily)
            ca.lemma(new ComplexExpression(OperatorComparator.BOR, new ComplexExpression(OperatorComparator.BNEG,
                    new ComplexExpression(OperatorComparator.GE, natIndex, Constant.createNatConstant(0))),
                    new ComplexExpression(OperatorComparator.STRINGEQ,
                            new ComplexExpression(OperatorComparator.SAT, s, natIndex), delimiter)));
            Expression index = new ComplexExpression(OperatorComparator.NAT2BV32, natIndex);
            end = new ComplexExpression(OperatorComparator.ITE,
                    new ComplexExpression(OperatorComparator.BAND,
                            new ComplexExpression(OperatorComparator.BVGE, index, zero),
                            new ComplexExpression(OperatorComparator.BVLT, index, end)),
                    index, end);
        }
        return symbolicResult(pos, end);
    }

    /** {@code self.split(regex, limit)}, the limit possibly symbolic */
    @CompilerDirectives.TruffleBoundary
    public static Object splitWithLimit(StaticObject self, StaticObject regex, Object limit, Meta meta) {
        if (analyze && config.hasConcolicAnalysis() && isSymbolic(limit)) {
            losePrecision("String.split with a symbolic limit is not symbolically implemented", meta);
        }
        return split(self, regex, (int) AnnotatedValue.<Integer>value(limit), meta);
    }

    /** the parts of a split with their symbolic contents */
    private static StaticObject annotateParts(StaticObject array, List<Expression> parts, Meta meta) {
        StaticObject[] elements = array.unwrap(meta.getLanguage());
        for (int i = 0; i < elements.length && i < parts.size(); i++) {
            Annotations a = Annotations.create();
            a.set(config.getConcolicIdx(), parts.get(i));
            setStringAnnotations(elements[i], a);
        }
        return array;
    }

    /**
     * The separator of a regular expression that matches exactly one literal string (Java's fast path of split: one
     * character that is no metacharacter, or an escaped character that is no letter or digit; or a string without
     * metacharacters), {@code null} otherwise.
     */
    static String literalSeparator(String regex) {
        String meta = ".$|()[{^?*+\\";
        if (regex.length() == 2 && regex.charAt(0) == '\\' && !Character.isLetterOrDigit(regex.charAt(1))) {
            return regex.substring(1);
        }
        if (regex.isEmpty()) {
            return null;
        }
        for (char c : regex.toCharArray()) {
            if (meta.indexOf(c) >= 0 || Character.isSurrogate(c)) {
                return null;
            }
        }
        return regex;
    }

    public static StaticObject valueOf_bool(Object v, Meta meta) {
        if (v instanceof AnnotatedValue && config.hasConcolicAnalysis() && Annotations.annotation((Annotations) v, config.getConcolicIdx()) != null) {
            losePrecision("concolic type conversion from boolean to string not supported, yet.", meta);
        }
        String ret = "" + (boolean) AnnotatedValue.value(v);
        return meta.toGuestString(ret);
    }

    public static StaticObject valueOf_byte(Object v, Meta meta) {
        if (v instanceof AnnotatedValue && config.hasConcolicAnalysis() && Annotations.annotation((Annotations) v, config.getConcolicIdx()) != null) {
            losePrecision("concolic type conversion from byte to string not supported, yet.", meta);
        }
        String ret = "" + (byte) AnnotatedValue.value(v);
        return meta.toGuestString(ret);
    }

    public static StaticObject valueOf_char(Object v, Meta meta) {
        if (v instanceof AnnotatedValue && config.hasConcolicAnalysis() && Annotations.annotation((Annotations) v, config.getConcolicIdx()) != null) {
            losePrecision("concolic type char conversion to string not supported, yet.", meta);
        }
        String ret = "" + (char) AnnotatedValue.value(v);
        return meta.toGuestString(ret);
    }

    public static StaticObject valueOf_char_array(StaticObject v, Meta meta) {
        if (hasConcolicAnnotations(v)) {
            losePrecision("concolic type char array conversion to string not supported, yet.", meta);
        }
        char[] value = v.unwrap(meta.getLanguage());
        return meta.toGuestString(new String(value));
    }

    public static StaticObject valueOf_char_array(StaticObject v, Object offset, Object count, Meta meta) {
        if (hasConcolicAnnotations(v) || offset instanceof AnnotatedValue || count instanceof AnnotatedValue) {
            losePrecision("concolic type char array conversion to string not supported, yet.", meta);
        }
        int coffset = (int) offset;
        int ccount = (int) count;
        char[] value = v.unwrap(meta.getLanguage());
        return meta.toGuestString(new String(Arrays.copyOfRange(value, coffset, coffset + ccount)));
    }

    public static StaticObject valueOf_short(Object v, Meta meta) {
        if (v instanceof AnnotatedValue && config.hasConcolicAnalysis() && Annotations.annotation((Annotations) v, config.getConcolicIdx()) != null) {
            losePrecision("concolic type conversion from short to string not supported, yet.", meta);
        }
        String ret = "" + (short) AnnotatedValue.value(v);
        return meta.toGuestString(ret);
    }

    public static StaticObject string_valueOf_int(Object v, Meta meta) {
        if (v instanceof AnnotatedValue && config.hasConcolicAnalysis() && Annotations.annotation((Annotations) v, config.getConcolicIdx()) != null) {
            losePrecision("concolic type conversion from int to string not supported, yet.", meta);
        }

        String ret = "" + (int) AnnotatedValue.value(v);
        return meta.toGuestString(ret);
    }


    public static StaticObject valueOf_long(Object v, Meta meta) {
        if (v instanceof AnnotatedValue && config.hasConcolicAnalysis() && Annotations.annotation((Annotations) v, config.getConcolicIdx()) != null) {
            losePrecision("concolic type conversion from long to string not supported, yet.", meta);
        }
        String ret = "" + (long) AnnotatedValue.value(v);
        return meta.toGuestString(ret);
    }

    public static StaticObject valueOf_float(Object v, Meta meta) {
        if (v instanceof AnnotatedValue && config.hasConcolicAnalysis() && Annotations.annotation((Annotations) v, config.getConcolicIdx()) != null) {
            losePrecision("concolic type conversion from float to string not supported, yet.", meta);
        }
        String ret = "" + (float) AnnotatedValue.value(v);
        return meta.toGuestString(ret);
    }

    public static StaticObject valueOf_double(Object v, Meta meta) {
        if (v instanceof AnnotatedValue && config.hasConcolicAnalysis() && Annotations.annotation((Annotations) v, config.getConcolicIdx()) != null) {
            losePrecision("concolic type conversion from double to string not supported, yet.", meta);
        }
        String ret = "" + (double) AnnotatedValue.value(v);
        return meta.toGuestString(ret);
    }

    // Numeric Classes

    @CompilerDirectives.TruffleBoundary
    public static double parseDouble(StaticObject s, Meta meta) {
        if (analyze && config.hasConcolicAnalysis() && hasSymbolicContent(s)) {
            losePrecision("Concolic type conversion from string to double is not supported", meta);
        }
        return Double.parseDouble(meta.toHostString(s));
    }

    @CompilerDirectives.TruffleBoundary
    public static float parseFloat(StaticObject s, Meta meta) {
        if (analyze && config.hasConcolicAnalysis() && hasSymbolicContent(s)) {
            losePrecision("Concolic type conversion from string to float is not supported", meta);
        }
        return Float.parseFloat(meta.toHostString(s));
    }

    @CompilerDirectives.TruffleBoundary
    public static int parseInt(StaticObject s, Meta meta) {
        if (analyze && config.hasConcolicAnalysis() && hasSymbolicContent(s)) {
            losePrecision("Concolic type conversion from string to int is not supported", meta);
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
        if (a != null) {
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

    private static Annotations getStringAnnotations(StaticObject self) {
        return stringContent(self);
    }


    private static boolean hasConcolicAnnotations(StaticObject v) {
        return hasSymbolicContent(v);
    }

    public static void makeConcatWithConstantsSymbolically(Object result, Object[] args, Meta meta) {
        if (analyze && config.hasConcolicAnalysis()) {
            config.getConcolicAnalysis().makeConcatWithConstants((StaticObject) result, args, meta);
        }
        if (analyze && config.hasTaintAnalysis()) {
            config.getTaintAnalysis().makeConcatWithConstants((StaticObject) result, args);
        }
    }

    /** the thread that runs the analyzed program (it starts the path) */
    private static volatile Thread analysisThread;
    /** nested pauses of the analysis (by the analysis thread) */
    private static int pauseDepth = 0;

    /**
     * Pauses the analysis (e.g. while a call site is bootstrapped), nested. Only the analysis thread pauses: other
     * threads of the VM (e.g. when threads are created) would switch the analysis off for the program.
     */
    @CompilerDirectives.TruffleBoundary
    public static void pauseAnalyze() {
        if (analysisThread != null && Thread.currentThread() != analysisThread) {
            return;
        }
        if (pauseDepth++ == 0) {
            oldAnalyze = analyze;
            analyze = false;
        }
    }

    @CompilerDirectives.TruffleBoundary
    public static void resumeAnalyze() {
        if (analysisThread != null && Thread.currentThread() != analysisThread) {
            return;
        }
        if (pauseDepth > 0 && --pauseDepth == 0) {
            analyze = oldAnalyze;
        }
    }

    public class NullCheckResult {
        private StaticObject object;
        public boolean result;

        public boolean getResult() {
            return result;
        }

        public void setResult(boolean result) {
            this.result = result;
        }

        public StaticObject getObject() {
            return object;
        }

        public void setObject(StaticObject object) {
            this.object = object;
        }

        public NullCheckResult(StaticObject object, boolean result) {
            this.object = object;
            this.result = result;
        }
    }

    public static boolean hasAnalysis() {
        return analyze;
    }

    public static boolean useObjectFactories() {
        return analyze && config.useObjectFactories();
    }

}
