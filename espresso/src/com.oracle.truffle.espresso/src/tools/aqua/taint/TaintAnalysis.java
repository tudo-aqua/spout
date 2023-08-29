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

package tools.aqua.taint;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.*;

import java.util.TreeMap;

import static tools.aqua.spout.Config.TaintType.*;

public class TaintAnalysis implements Analysis<Taint> {

    private final TreeMap<Integer, String> ifColorNames = new TreeMap<>();

    private static Taint ifTaint = null;

    private InformationFlowScope iflowScopes = new InformationFlowScope(null, null, 0, null, -1, 0);

    private int nextFreeColor = 63;

    private boolean ifExceptionThrown = false;

    private boolean tainted = false;

    private final Config config;

    private final Trace trace;

    private final Config.TaintType type;

    public TaintAnalysis(Config config) {
        this.config = config;
        this.trace = config.getTrace();
        this.type = config.getTaintType();
    }

    // --------------------------------------------------------------------------
    //
    // taint

    public Object taint(Object o, int color) {
        AnnotatedValue av;
        if (o instanceof AnnotatedValue) {
            AnnotatedValue ao = (AnnotatedValue) o;
            av = new AnnotatedValue( ao.getValue(), ao);
            av.set(config.getTaintIdx(), ColorUtil.joinColors(
                    (Taint) ao.getAnnotations()[config.getTaintIdx()], new Taint(color)));
        }
        else {
            av = new AnnotatedValue(o);
            av.set(config.getTaintIdx(), new Taint(color));
        }
        SPouT.debug("Tainting with color " + color);
        tainted = true;
        return av;
    }

    public Object sanitize(Object o, int color) {
        if (!(o instanceof AnnotatedValue)) return o;
        AnnotatedValue av = (AnnotatedValue) o;
        Taint t = Annotations.annotation(av, config.getTaintIdx());
        if (t != null) {
            long[] sanitized = ColorUtil.removeColor(t.getColors(), color);
            av.set(config.getTaintIdx(), new Taint(sanitized));
        }
        return av;
    }

    public void taintObject(StaticObject o, int color) {
        // TODO: maybe most of this code should move to SPouT?
        if (!o.hasAnnotations()) {
            Annotations.initObjectAnnotations(o);
            Annotations a = Annotations.create();
            a.set(config.getTaintIdx(), new Taint(color));
            Annotations.setObjectAnnotation(o, a);
        }
        else {
            Annotations a = Annotations.objectAnnotation(o);
            a.set(config.getTaintIdx(), ColorUtil.joinColors(
                    (Taint) a.getAnnotations()[config.getTaintIdx()], new Taint(color)));
        }
    }

    public void checkTaint(Annotations o, int color) {
        Taint taint = Annotations.annotation( o, config.getTaintIdx());
        if (type.equals(INFORMATION)) {
            trace.addElement(new TaintCheck(color, taint, ifColorNames));
        }
        if (ColorUtil.hasColor(taint, color)) {
            trace.addElement(new TaintViolation(color));
        }
        else {
            SPouT.debug("Checking for taint with color " + color);
        }
    }

    public void checkTaintObject(StaticObject o, int color) {

        // TODO: walk heap?

        Annotations a = Annotations.objectAnnotation(o);
        Taint taint = (Taint) Annotations.annotation(a, config.getTaintIdx());
        if (type.equals(INFORMATION)) {
            trace.addElement(new TaintCheck(color, taint, ifColorNames));
        }
        if (ColorUtil.hasColor(taint, color)) {
            trace.addElement(new TaintViolation(color));
        }
        else {
            SPouT.debug("Checking for taint with color " + color);
        }
    }

    // --------------------------------------------------------------------------
    //
    // information flow
    private void informationFlowAddScope(VirtualFrame frame, BytecodeNode bcn, int bci, int branch, Taint a1, Taint a2) {
        CompilerDirectives.transferToInterpreter();
        if (!tainted || ifExceptionThrown) return;

        if (outofscope(bcn.getMethod())) return;

        int ipdBCI = bcn.getPostDominatorAnalysis().immediatePostDominatorStartForBCI(bci);

        // new taint
        Taint decisionTaint = ColorUtil.joinColors(ifTaint, a1, a2);
        String decisionName = iflowScopes.nextDecisionName();
        Taint scopeTaint = ColorUtil.addColor(decisionTaint, ++nextFreeColor);
        SPouT.debug("new scope [" + bcn.getMethod().getNameAsString() + "] at bci=" + bci + ", ipdStart=" + ipdBCI + ", " + decisionName);

        iflowScopes = new InformationFlowScope(iflowScopes, frame, branch, scopeTaint, ipdBCI, nextFreeColor);
        ifTaint = scopeTaint;
        informationFlowAddColor(decisionName);
        if (type == INFORMATION) {
            trace.addElement(new InformationFlow(decisionName, decisionTaint, ifColorNames));
        }
    }

    @CompilerDirectives.TruffleBoundary
    private void informationFlowAddColor(String decisionName) {
        ifColorNames.put(nextFreeColor, decisionName);
    }

    public void informationFlowNextBytecode(VirtualFrame frame, BytecodeNode bcn, int bci) {
        if (!tainted || ifExceptionThrown) return;
        while (iflowScopes.isEnd(frame, bci)) {
            SPouT.debug("pop iflow scope at ipd=" + bci);
            iflowScopes = iflowScopes.parent;
            ifTaint = iflowScopes.taint;
        }
    }

    public void informationFlowEnterBlockWithHandler(VirtualFrame frame, BytecodeNode bcn, int bci) {
        if (!tainted || ifExceptionThrown) return;
        informationFlowAddScope(frame, bcn, bci, 0, null, null);
        iflowScopes.setHandlers(bcn.getPostDominatorAnalysis().getHandlers(bci));
        //SPouT.log(iflowScopes.toString());
    }


    public void informationFlowMethodReturn(VirtualFrame frame) {
        if (!tainted || ifExceptionThrown) return;
        while (iflowScopes.frame == frame) {
            SPouT.debug("pop iflow scope at return");
            iflowScopes = iflowScopes.parent;
            ifTaint = iflowScopes.taint;
        }
    }

    private boolean outofscope(Method m) {
        String declaringClassName = m.getDeclaringKlass().getNameAsString();
        if (declaringClassName.startsWith("java/")) {
            return !declaringClassName.startsWith("java/util/") &&
                    !declaringClassName.startsWith("java/lang/reflect/");
        }
        if (declaringClassName.startsWith("sun/")) {
            return !declaringClassName.startsWith("sun/reflect/");
        }
        return declaringClassName.startsWith("jdk/");
    }

    public void iflowRegisterException() {
        if (!tainted && !ifExceptionThrown) return;
        SPouT.log("register exception");
        ifExceptionThrown = true;
    }

    public void iflowUnregisterException(VirtualFrame frame, BytecodeNode bcn, int bci) {
        CompilerDirectives.transferToInterpreter();
        if (!tainted || !ifExceptionThrown)  return;
        SPouT.log("unregister exception");
        ifExceptionThrown = false;

        // keep taint
        Taint scopeTaint = iflowScopes.taint;

        // pop scopes
        while (!iflowScopes.isHandledBy(frame, bcn, bci) && iflowScopes.parent != null) {
            SPouT.log("remove scope that is not handled at current bci=" + bci + " ...");
            iflowScopes = iflowScopes.parent;
        }

        // back-propagate taint
        InformationFlowScope cur = iflowScopes;
        while (cur != null) {
            if (cur.isHandledBy(frame, bcn, bci)) {
                SPouT.log("found scope that is handled at current bci");
                if (type == INFORMATION) {
                    trace.addElement(new InformationFlow(ifColorNames.get(iflowScopes.decisionColor), scopeTaint, ifColorNames));
                }
                iflowScopes.setTaint( ColorUtil.joinColors( iflowScopes.taint, scopeTaint));
            }
            cur = cur.parent;
        }
        ifTaint = iflowScopes.taint;
    }

    public int iflowGetIpdBCI() {
        return iflowScopes.endOfScope;
    }

    public Taint getIfTaint() {
        if (!tainted || ifExceptionThrown) return null;
        return ifTaint;
    }


    // --------------------------------------------------------------------------
    //
    // byte codes

    @Override
    public Taint iadd(int c1, int c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint ladd(long c1, long c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint fadd(float c1, float c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint dadd(double c1, double c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint isub(int c1, int c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint lsub(long c1, long c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint fsub(float c1, float c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint dsub(double c1, double c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint imul(int c1, int c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint lmul(long c1, long c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint fmul(float c1, float c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint dmul(double c1, double c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint idiv(int c1, int c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint ldiv(long c1, long c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint fdiv(float c1, float c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint ddiv(double c1, double c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint irem(int c1, int c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint lrem(long c1, long c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint frem(float c1, float c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint drem(double c1, double c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint ishl(int c1, int c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint ishr(int c1, int c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint iushr(int c1, int c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint lshl(int c1, long c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint lshr(int c1, long c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint lushr(int c1, long c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint iinc(int incr, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint ineg(int c1, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint lneg(long c1, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint fneg(float c1, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint dneg(double c1, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint i2l(long c1, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint i2f(float c1, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint i2b(byte c1, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint i2c(char c1, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint i2d(double c1, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint i2s(short c1, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint l2d(double c1, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint l2f(float c1, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint l2i(int c1, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint f2d(double c1, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint f2i(int c1, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint f2l(long c1, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint d2f(float c1, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint d2i(int c1, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint d2l(long c1, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }


    @Override
    public Taint iand(int c1, int c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint ior(int c1, int c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint ixor(int c1, int c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint land(long c1, long c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint lor(long c1, long c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint lxor(long c1, long c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint lcmp(long c1, long c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint fcmpg(float c1, float c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint fcmpl(float c1, float c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint dcmpg(double c1, double c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint dcmpl(double c1, double c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint instanceOf(StaticObject c, Taint a, boolean isInstance) {
        return ColorUtil.joinColors(a, ifTaint);
    }

    @Override
    public Taint isNull(StaticObject c, Taint a, boolean isInstance) {
        return ColorUtil.joinColors(a, ifTaint);
    }

    @Override
    public void checkcast(VirtualFrame frame, BytecodeNode bcn, int bci, boolean takeBranch, Taint a) {
        if (type == INFORMATION || (type == CONTROL && a != null)) {
            informationFlowAddScope(frame, bcn, bci, takeBranch ? 0 : 1, a, null);
        }
    }

    @Override
    public void checkNotZeroInt(VirtualFrame frame, BytecodeNode bcn, int bci, boolean isZero, Taint a) {
        if (type == INFORMATION || (type == CONTROL && a != null)) {
            informationFlowAddScope(frame, bcn, bci, isZero ? 0 : 1, a, null);
        }
    }

    @Override
    public void checkNotZeroLong(VirtualFrame frame, BytecodeNode bcn, int bci, boolean isZero, Taint a) {
        if (type == INFORMATION || (type == CONTROL && a != null)) {
            informationFlowAddScope(frame, bcn, bci, isZero ? 0 : 1, a, null);
        }
    }

    public void setArrayAccessInformationFlow(VirtualFrame frame, BytecodeNode bcn, int bci,
                                              boolean fails, Taint idx, Taint arraySize) {
        if (type == INFORMATION || (type == CONTROL && (arraySize != null || idx != null))) {
            informationFlowAddScope(frame, bcn, bci, fails ? 0 : 1, idx, arraySize);
        }
        if (fails) {
            iflowRegisterException();
        }
    }

    @Override
    public void takeBranchPrimitive1(VirtualFrame frame, BytecodeNode bcn, int bci, int opcode, boolean takeBranch, Taint a) {
        if (type == INFORMATION || (type == CONTROL && a != null)) {
            informationFlowAddScope(frame, bcn, bci, takeBranch ? 0 : 1, a, null);
        }
    }

    @Override
    public void takeBranchPrimitive2(VirtualFrame frame, BytecodeNode bcn, int bci, int opcode, boolean takeBranch, int c1, int c2, Taint a1, Taint a2) {
        if (type == INFORMATION || (type == CONTROL && (a1 != null || a2 != null))) {
            informationFlowAddScope(frame, bcn, bci, takeBranch ? 0 : 1, a1, a2);
        }
    }

    @Override
    public void takeBranchRef2(VirtualFrame frame, BytecodeNode bcn, int bci,
                                int opcode, boolean takeBranch, StaticObject c1, StaticObject c2, Taint a1, Taint a2) {

        if (type == INFORMATION || (type == CONTROL && (a1 != null || a2 != null))) {
            informationFlowAddScope(frame, bcn, bci, takeBranch ? 0 : 1, a1, a2);
        }
    }

    @Override
    public void lookupSwitch(VirtualFrame frame, BytecodeNode bcn, int bci, int[] vals, int key, Taint a1) {
        if (type == INFORMATION || (type == CONTROL && a1 != null)) {
            int idx = vals.length;
            for (int i = 0; i < vals.length; i++) {
                if (vals[i] == key) {
                    idx = i;
                    break;
                }
            }
            informationFlowAddScope(frame, bcn, bci, idx, a1, null);
        }
    }

    @Override
    public void tableSwitch(VirtualFrame frame, BytecodeNode bcn, int bci, int low, int high, int concIndex, Taint a1) {
        if (type == INFORMATION || (type == CONTROL && a1 != null)) {
            int bId = (low <= concIndex && concIndex <= high) ? concIndex - low :  high - low + 1;
            informationFlowAddScope(frame, bcn, bci,bId, a1, null);
        }
    }

    // Strings

    @Override
    public Taint stringLength(int c, Taint s) {
        return type != DATA ? s : null;
    }

    @Override
    public Taint stringContains(String self, String other, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint stringCompareTo(String self, String other, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2);
    }

    @Override
    public Taint stringConcat(String self, String other, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint stringEquals(String self, String other, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint substring(boolean success, String self, int start, int end, Taint a1, Taint a2, Taint a3) {
        return ColorUtil.joinColors(a1, a2, a3, ifTaint);
    }

    @Override
    public Taint stringToLowerCase(String self, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint stringToUpperCase(String self, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint stringBuilderAppend(String self, String other, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2);
    }

    @Override
    public Taint stringBuxxLength(String self, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint stringBuxxToString(String self, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint stringBuxxInsert(String self, String other, int i, Taint a1, Taint a2, Taint a3) {
        return ColorUtil.joinColors(a1, a2, a3, ifTaint);
    }

    @Override
    public Taint stringBuxxCharAt(String self, String val, int index, Taint a1, Taint a2, Taint a3) {
        return ColorUtil.joinColors(a1, a2, a3, ifTaint);
    }

    @Override
    public Taint characterToLowerCase(char self, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint characterToUpperCase(char self, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint isCharDefined(char self, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint characterEquals(char self, char other, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint charAtPCCheck(String self, int index, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint charAt(String self, int index, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @CompilerDirectives.TruffleBoundary
    private TaintAnalysis.ConvRes convertArgToExpression(Object arg) {
        if (arg instanceof StaticObject) {
            StaticObject so = (StaticObject) arg;
            Annotations[] a = so.getAnnotations();
            return a != null && a[a.length - 1] != null && a[a.length - 1].getAnnotations()[config.getTaintIdx()] != null ?
                    new TaintAnalysis.ConvRes((Taint) a[a.length - 1].getAnnotations()[config.getTaintIdx()], true) :
                    new TaintAnalysis.ConvRes(ifTaint, false);
        } else if (arg instanceof AnnotatedValue) {
            Object[] a = ((AnnotatedValue) arg).getAnnotations();
            return a != null && a[config.getTaintIdx()] != null ?
                    new TaintAnalysis.ConvRes((Taint) a[config.getTaintIdx()], true) :
                    //FIXME: replaced the fromatted call here. Not sure if this works in general
                    new TaintAnalysis.ConvRes(ifTaint, false);
        } else {
            return new TaintAnalysis.ConvRes(ifTaint, false);
        }
    }

    public void makeConcatWithConstants(StaticObject result, Object[] args) {
        TaintAnalysis.ConvRes cr = convertArgToExpression(args[0]);
        Taint taint = cr.taint;
        boolean anySymbolic = cr.fromSymbolic; // FIXME: or control flow analysis present
        int i = 1;
        while (args[i] != null) {
            cr = convertArgToExpression(args[i]);
            anySymbolic = anySymbolic ? true : cr.fromSymbolic;
            Taint expr2 = cr.taint;
            taint = ColorUtil.joinColors(taint, expr2);
            ++i;
        }
        taint = ColorUtil.joinColors(taint, ifTaint);
        if (anySymbolic) {
            annotateStringWithTaint(result, taint);
        }
    }

    private final class ConvRes {
        public Taint taint;
        public boolean fromSymbolic;

        ConvRes(Taint e, boolean b) {
            taint = e;
            fromSymbolic = b;
        }
    }

    private StaticObject annotateStringWithTaint(StaticObject self, Taint value) {
        Annotations[] stringAnnotation = self.getAnnotations();
        if (stringAnnotation == null) {
            SPouT.initStringAnnotations(self);
            stringAnnotation = self.getAnnotations();
        }
        if(stringAnnotation[stringAnnotation.length - 1] == null){
            stringAnnotation[stringAnnotation.length - 1] = Annotations.emptyArray();
        }
        stringAnnotation[stringAnnotation.length - 1].set(config.getTaintIdx(),
                value);
        self.setAnnotations(stringAnnotation);
        return self;
    }
}