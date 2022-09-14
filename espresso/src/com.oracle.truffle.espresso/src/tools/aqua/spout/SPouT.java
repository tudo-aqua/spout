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
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.smt.ComplexExpression;
import tools.aqua.smt.Expression;
import tools.aqua.smt.Types;
import tools.aqua.smt.Variable;
import tools.aqua.taint.PostDominatorAnalysis;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static com.oracle.truffle.espresso.bytecode.Bytecodes.*;
import static com.oracle.truffle.espresso.nodes.BytecodeNode.popInt;
import static com.oracle.truffle.espresso.nodes.BytecodeNode.putInt;
import static tools.aqua.smt.OperatorComparator.SCONTAINS;

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
    public void assume(Object condition, Meta meta) {
        if (!analyze || !config.hasConcolicAnalysis())  {
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
        if(!analyze || !config.hasConcolicAnalysis()) return meta.toGuestString("");
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
        config.getTaintAnalysis().checkTaint( o instanceof AnnotatedValue ? (AnnotatedValue) o : null, color);
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

    // setLocalInt(frame, bs.readLocalIndex1(curBCI), getLocalInt(frame, bs.readLocalIndex1(curBCI)) + bs.readIncrement1(curBCI));
    public static void iinc(VirtualFrame frame, int index, int incr) {
        // concrete
        int c1 = BytecodeNode.getLocalInt(frame, index);
        int concResult = c1 + incr;
        BytecodeNode.setLocalInt(frame, index, concResult);
        if (!analyze) return;
        AnnotatedVM.setLocalAnnotations(frame, index, analysis.iinc(incr, AnnotatedVM.getLocalAnnotations(frame, index)));
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
                Annotations[] annotations = new Annotations[length+1];
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
        Annotations aLength = annotations[annotations.length-1];
        AnnotatedVM.putAnnotations(frame, top -1, aLength);
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
            case IFEQ: takeBranch = (c == 0); break;
            case IFNE: takeBranch = (c != 0); break;
            case IFLT: takeBranch = (c < 0);  break;
            case IFGE: takeBranch = (c >= 0); break;
            case IFGT: takeBranch = (c > 0);  break;
            case IFLE: takeBranch = (c <= 0); break;
            default:
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere("expecting IFEQ,IFNE,IFLT,IFGE,IFGT,IFLE");
        }

        if (analyze) analysis.takeBranchPrimitive1(frame, bcn, bci, opcode, takeBranch, AnnotatedVM.popAnnotations(frame, top - 1));
        return takeBranch;
    }

    public static boolean takeBranchPrimitive2(VirtualFrame frame, int top, int opcode, BytecodeNode bcn, int bci) {

        assert IF_ICMPEQ <= opcode && opcode <= IF_ICMPLE;
        int c1 = popInt(frame, top - 1);
        int c2 = popInt(frame, top - 2);

        // concrete
        boolean takeBranch = true;

        switch (opcode) {
            case IF_ICMPEQ: takeBranch = (c1 == c2); break;
            case IF_ICMPNE: takeBranch = (c1 != c2); break;
            case IF_ICMPLT: takeBranch = (c1 > c2);  break;
            case IF_ICMPGE: takeBranch = (c1 <= c2); break;
            case IF_ICMPGT: takeBranch = (c1 < c2);  break;
            case IF_ICMPLE: takeBranch = (c1 >= c2); break;
            default:
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere("non-branching bytecode");
        }

        if (analyze) analysis.takeBranchPrimitive2(frame, bcn, bci, opcode, takeBranch, c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top -2 ));

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
        if(config.hasConcolicAnalysis()){
            result = config.getConcolicAnalysis().stringContains(result, self, s, meta);
        }
        return result;
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
}
