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
import tools.aqua.taint.PostDominatorAnalysis;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.lang.ArithmeticException;

import static com.oracle.truffle.espresso.bytecode.Bytecodes.*;
import static com.oracle.truffle.espresso.nodes.BytecodeNode.popInt;
import static com.oracle.truffle.espresso.nodes.BytecodeNode.putInt;
import static com.oracle.truffle.espresso.nodes.BytecodeNode.popLong;
import static com.oracle.truffle.espresso.nodes.BytecodeNode.putLong;
import static com.oracle.truffle.espresso.nodes.BytecodeNode.popFloat;
import static com.oracle.truffle.espresso.nodes.BytecodeNode.putFloat;

public class SPouT {

    public static final boolean DEBUG = true;

    private static boolean analyze = false;

    private static MetaAnalysis analysis = null;

    private static Config config = null;

    private static Trace trace = null;

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
        System.out.println("======================== START PATH [END].");
        // TODO: should be deferred to latest possible point in time
        analyze = true;
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
        meta.throwException(meta.java_lang_RuntimeException);
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

    @CompilerDirectives.TruffleBoundary
    public void assume(Object condition, Meta meta) {
        if (!analyze || !config.hasConcolicAnalysis()) {
            // FIXME: dont care about assumptions outside of concolic analysis?
            return;
        }
        /*
        boolean cont = concolicAnalysis.assume(condition, meta);
        if (!cont) {
            stopRecording("assumption violation", meta);
        }
        */
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

    @CompilerDirectives.TruffleBoundary
    public static Object nextSymbolicInt() {
        if (!analyze || !config.hasConcolicAnalysis()) return 0;
        Object av = config.getConcolicAnalysis().nextSymbolicInt();
        //GWIT.trackLocationForWitness("" + concrete);
        return av;
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject nextSymbolicString(Meta meta) {
        if (!analyze || !config.hasConcolicAnalysis()) return meta.toGuestString("");
        StaticObject annotatedObject = config.getConcolicAnalysis().nextSymbolicString(meta);
        //GWIT.trackLocationForWitness("" + concrete);
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
        AnnotatedVM.putAnnotations(frame, top - 4, analysis.lsub(c1, c2,
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
        int concResult;
        if (c1 > c2) {
            concResult = 1;
        } else if (c1 == c2) {
            concResult = 0;
        } else concResult = -1;
        putInt(frame, top - 4, concResult);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 4, analysis.lcmp(c1, c2,
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

    public static void idiv(VirtualFrame frame, int top, BytecodeNode bn) {
        int c1 = popInt(frame, top - 1);
        checkNotNull(c1, AnnotatedVM.peekAnnotations(frame, top - 1), bn);
        int c2 = popInt(frame, top - 2);
            int concResult = c2 / c1;
            putInt(frame, top - 2, concResult);
            if (!analyze) return;
            AnnotatedVM.putAnnotations(frame, top - 2, analysis.idiv(c2, c1,
                    AnnotatedVM.popAnnotations(frame, top - 2),
                    AnnotatedVM.popAnnotations(frame, top - 1)));

    }

    private static void checkNotNull(int c1, Annotations a, BytecodeNode bn) {
        if (c1 == 0) {
            if(a != null && analyze && config.hasConcolicAnalysis()){
                config.getConcolicAnalysis().addZeroToTrace(a);
            }
            bn.enterImplicitExceptionProfile();
            Meta meta = bn.getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_ArithmeticException, "/ by zero");
        }
        else{
            if(a != null && analyze && config.hasConcolicAnalysis()){
                config.getConcolicAnalysis().addNotZeroToTrace(a);
            }
        }
    }

    public static void irem(VirtualFrame frame, int top) {
        int c1 = popInt(frame, top - 1);
        int c2 = popInt(frame, top - 2);
        if (c2 != 0) {
            int concResult = c1 - (c1 / c2) * c2;
            putInt(frame, top - 2, concResult);
            if (!analyze) return;
            AnnotatedVM.putAnnotations(frame, top - 2, analysis.irem(c1, c2,
                    AnnotatedVM.popAnnotations(frame, top - 1),
                    AnnotatedVM.popAnnotations(frame, top - 2)));
        } else {
            throw new ArithmeticException();
        }

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

    public static void ineg(VirtualFrame frame, int top) {
        int c1 = popInt(frame, top - 1);
        int concResult = 0 - c1;
        putInt(frame, top - 1, concResult);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 1, analysis.ineg(c1,
                AnnotatedVM.popAnnotations(frame, top - 1)));
    }

    public static void i2l(VirtualFrame frame, int top) {
        int c1 = popInt(frame, top - 1);
        putLong(frame, top - 1, c1);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 1, analysis.i2l(c1,
                AnnotatedVM.popAnnotations(frame, top - 1)));
    }

    public static void i2f (VirtualFrame frame, int top) {
        float c1 = popInt(frame, top - 1);
        putFloat(frame, top-1, c1);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 1, analysis.i2f(c1,
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
        AnnotatedValue result = new AnnotatedValue(concreteRes, Annotations.emptyArray());
        if (config.hasConcolicAnalysis()) {
            result = config.getConcolicAnalysis().stringContains(result, self, s, meta);
        }
        return result;
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
        int conreteResult = meta.toHostString(self).compareTo(meta.toHostString(other));
        if (!analyze) {
            return conreteResult;
        }
        AnnotatedValue av = new AnnotatedValue(conreteResult, Annotations.emptyArray());
        if (config.hasConcolicAnalysis() && (self.hasAnnotations() || other.hasAnnotations())) {
            av = config.getConcolicAnalysis().stringCompareTo(av, self, other, meta);
        }
        return av;
    }

    @CompilerDirectives.TruffleBoundary
    public static Object stringLength(StaticObject self, Meta meta) {
        int length = meta.toHostString(self).length();
        if (!analyze || !config.hasConcolicAnalysis() || !self.hasAnnotations()) {
            return length;
        }
        return config.getConcolicAnalysis().stringLength(new AnnotatedValue(length, Annotations.emptyArray()), self, meta);
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject stringConcat(StaticObject self, StaticObject other, Meta meta) {
        String concreteSelf = meta.toHostString(self);
        String concreteOther = meta.toHostString(other);
        StaticObject result = meta.toGuestString(concreteSelf.concat(concreteOther));
        if (!analyze || !config.hasConcolicAnalysis() || !self.hasAnnotations() && !other.hasAnnotations()) {
            return result;
        }
        initStringAnnotations(result);
        return config.getConcolicAnalysis().stringConcat(result, self, other, meta);
    }

    @CompilerDirectives.TruffleBoundary
    public static Object stringEquals(StaticObject self, StaticObject other, Meta meta) {
        boolean areEqual = meta.toHostString(self).equals(meta.toHostString(other));
        if (!analyze) {
            return areEqual;
        }
        AnnotatedValue av = new AnnotatedValue(areEqual, Annotations.emptyArray());
        if (config.hasConcolicAnalysis()) {
            av = config.getConcolicAnalysis().stringEqual(av, self, other, meta);
        }
        return av;
    }

    @CompilerDirectives.TruffleBoundary
    public static Object stringCharAt(StaticObject self, Object index, Meta meta) {
        String concreteString = meta.toHostString(self);
        int concreteIndex = 0;
        if (index instanceof AnnotatedValue) {
            AnnotatedValue a = (AnnotatedValue) index;
            concreteIndex = a.<Integer>getValue();
        } else {
            concreteIndex = (int) index;
        }

        if (!self.hasAnnotations() && !(index instanceof AnnotatedValue)) {
            return concreteString.charAt(concreteIndex);
        }
        boolean sat1 = (0 <= concreteIndex);
        boolean sat2 = (concreteIndex < concreteString.length());

        if (!sat1 && !(index instanceof AnnotatedValue)) {
            // index is negative
            meta.throwException(meta.java_lang_StringIndexOutOfBoundsException);
            return null;
        }
        if (analyze && config.hasConcolicAnalysis()) {
            config.getConcolicAnalysis().charAtPCCheck(self, index, meta);
        }

        if (!sat2) {
            // index is greater than string length
            meta.throwException(meta.java_lang_StringIndexOutOfBoundsException);
            return null;
        }
        AnnotatedValue av = new AnnotatedValue(concreteString.charAt(concreteIndex), Annotations.emptyArray());
        if (analyze && config.hasConcolicAnalysis()) {
            config.getConcolicAnalysis().charAtContent(av, self, index, meta);
        }
        return av;
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
        StaticObject result = meta.toGuestString(host.toUpperCase());
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
        boolean isAnalyze = analyze;
        if (analyze && config.hasConcolicAnalysis()) {
            self = config.getConcolicAnalysis().stringBuilderAppend(self, string, meta);
        }
        analyze = false;
        Method m = self.getKlass().getSuperKlass().lookupMethod(meta.getNames().getOrCreate("append"), Signature.java_lang_AbstractStringBuilder_java_lang_String);
        m.invokeDirect(self, string);
        analyze = isAnalyze;
        return self;
    }

    @CompilerDirectives.TruffleBoundary
    public static Object stringBuxxLength(StaticObject self, Meta meta) {
        Method m = self.getKlass().getSuperKlass().lookupMethod(meta.getNames().getOrCreate("length"), Symbol.Signature._int);
        int cresult = (int) m.invokeDirect(self);
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
        int ilength = (int) length.invokeDirect(self);
        StaticObject result =
                (boolean) isLatin.invokeDirect(self)
                        ?
                        (StaticObject) meta.java_lang_StringLatin1_newString.invokeDirect(self, bytes, 0, ilength)
                        :
                        (StaticObject) meta.java_lang_StringUTF16_newString.invokeDirect(self, bytes, 0, ilength);
        if(analyze && config.hasConcolicAnalysis()){
            result = config.getConcolicAnalysis().stringBuilderToString(result, self, meta);
        }
        return result;
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject stringBuxxInsert(StaticObject self, Object offset, Object toInsert, Meta meta) {
        if (toInsert instanceof AnnotatedValue){
            stopRecording("Cannot insert symbolic chars to StringBuffer", meta);
        }
        StaticObject toInsertCasted = meta.toGuestString(String.valueOf((char) toInsert));
        return stringBuxxInsert(self, offset, toInsertCasted, meta);
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject stringBuxxInsert(StaticObject self, Object offset, StaticObject toInsert, Meta meta) {
        if (offset instanceof AnnotatedValue) {
            SPouT.stopRecording("Cannot handle symbolic offset values for insert into StringBu* yet.", meta);
        }
        int concreteOffset = (int) offset;
        if(analyze && config.hasConcolicAnalysis()){
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

    // --------------------------------------------------------------------------
    //
    // helpers

    @CompilerDirectives.TruffleBoundary
    public static void debug(String message) {
        if (DEBUG) System.out.println("[debug] " + message);
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
}
