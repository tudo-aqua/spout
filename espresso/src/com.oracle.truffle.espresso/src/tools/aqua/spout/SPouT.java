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
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.concolic.ConcolicAnalysis;
import tools.aqua.smt.*;
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

    public static Object split(StaticObject self, StaticObject regex, Meta meta) {
        boolean isSelfSymbolic = self.hasAnnotations()
                && self.getAnnotations()[self.getAnnotations().length-1].getAnnotations()[config.getConcolicIdx()] != null;
        boolean isRegexSymbolic = regex.hasAnnotations()
                && regex.getAnnotations()[self.getAnnotations().length-1].getAnnotations()[config.getConcolicIdx()] != null;

        if(isSelfSymbolic|| isRegexSymbolic){
            stopRecording("Cannot split symbolic strings yet", meta);
        }
        String s = meta.toHostString(self);
        String r = meta.toHostString(regex);
        String[] res = s.split(r);
        StaticObject[] resSO = new StaticObject[res.length];
        for(int i = 0; i < res.length; i++){
            resSO[i] = meta.toGuestString(res[i]);
        }
        return StaticObject.createArray(self.getKlass().getArrayClass(), resSO, meta.getContext());
    }

    public static Object stringRegionMatches_ignoreCase(StaticObject self, Object ignoreCase, Object toffset, StaticObject other, Object ooffset, Object len, Meta meta) {
        boolean ignore = false;
        if(ignoreCase instanceof AnnotatedValue){
            stopRecording("Cannot deal with symbolic ignore case for regionMatches yet", meta);
        }else{
            ignore =(boolean) ignoreCase;
        }
        int ctoffset= -1, cooffset=-1, clen=-1;
        if(toffset instanceof AnnotatedValue){
            stopRecording("Cannot deal with symbolic toffset for regionMatches yet", meta);
        }
        else{
            ctoffset = (int) toffset;
        }
        if(ooffset instanceof AnnotatedValue){
            stopRecording("Cannot deal with symbolic ooffset for regionMatches yet", meta);
        }
        else{
            cooffset = (int) ooffset;
        }
        if(len instanceof AnnotatedValue){
            stopRecording("Cannot deal with symbolic len for regionMatches yet", meta);
        }
        else{
            clen = (int) len;
        }
        boolean isSelfSymbolic = self.hasAnnotations()
                && self.getAnnotations()[self.getAnnotations().length-1].getAnnotations()[config.getConcolicIdx()] != null;
        boolean isOtherSymbolic = other.hasAnnotations()
                && other.getAnnotations()[self.getAnnotations().length-1].getAnnotations()[config.getConcolicIdx()] != null;
        if((isSelfSymbolic || isOtherSymbolic)&& analyze && config.hasConcolicAnalysis()){
            return  config.getConcolicAnalysis().regionMatches(self, other, ignore, ctoffset, cooffset, clen, meta);
        }else{
            return meta.toHostString(self).regionMatches(ignore, ctoffset, meta.toHostString(other), cooffset, clen);
        }
    }

    public static Object stringBuilderCharAt(StaticObject self, Object index, Meta meta) {
        System.out.println("The invocation worked");
        Method m = findMethod(self.getKlass(), meta.getNames().getOrCreate("toString"), Symbol.Signature.java_lang_String_void);
        System.out.println(m);
        return stringCharAt((StaticObject) m.invokeDirect(self), index, meta);
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

    public static Object stringLength(StaticObject self, Meta meta) {
        int length = meta.toHostString(self).length();
        if (!analyze || !config.hasConcolicAnalysis() || !self.hasAnnotations()) {
            return length;
        }
        return config.getConcolicAnalysis().stringLength(new AnnotatedValue(length, Annotations.emptyArray()), self, meta);
    }

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

    public static Object stringCharAt(StaticObject self, Object index, Meta meta) {
        String concreteString = meta.toHostString(self);
        int concreteIndex = 0;
        if (index instanceof AnnotatedValue) {
            AnnotatedValue a = (AnnotatedValue) index;
            concreteIndex = a.<Integer>getValue();
        } else {
            concreteIndex = (int) index;
        }

        if (!self.hasAnnotations() && index instanceof AnnotatedValue) { // && !index.isConcolic()) {
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

    public static StaticObject stringToUpperCase(StaticObject self, Meta meta) {
        String host = meta.toHostString(self);
        StaticObject result = meta.toGuestString(host.toUpperCase());
        if(analyze && config.hasConcolicAnalysis() && self.hasAnnotations()){
            initStringAnnotations(result);
            result = config.getConcolicAnalysis().stringToUpper(result, self, meta);
        }
        return result;
    }

    public static StaticObject stringToLowerCase(StaticObject self, Meta meta) {
        String host = meta.toHostString(self);
        StaticObject result = meta.toGuestString(host.toUpperCase());
        if(analyze && config.hasConcolicAnalysis() && self.hasAnnotations()){
            initStringAnnotations(result);
            result = config.getConcolicAnalysis().stringToLower(result, self, meta);
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

    public static StaticObject initStringAnnotations(StaticObject target){
        int lengthAnnotations = ((ObjectKlass) target.getKlass()).getFieldTable().length + 1;
        Annotations[] annotations = new Annotations[lengthAnnotations];
        for (int i = 0; i < annotations.length; i++) {
            annotations[i] = Annotations.emptyArray();
        }
        target.setAnnotations(annotations);
        return target;
    }

    private static Method findMethod(Klass k, Symbol<Symbol.Name> name, Symbol<Symbol.Signature> signature) {
        Method m = k.lookupMethod(name, signature);
        if(m != null){
            return m;
        }
        ObjectKlass ok = k.getSuperKlass();
        System.out.println(ok);
        do{
            m = ok.lookupMethod(name, signature);
            if(m != null){
                return m;
            }
            ok = ok.getSuperKlass();
            System.out.println(ok);
        } while(ok != null);
        for(ObjectKlass o: k.getSuperInterfaces()){
            m = o.lookupMethod(name, signature);
            if (m != null){
                return m;
            }
        }
        throw new UnsatisfiedLinkError("Cannot find: %s with signature %s on %s".formatted(name, signature, k.getName()));

    }
}
