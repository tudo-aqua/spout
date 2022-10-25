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
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.*;

import java.util.TreeMap;

import static tools.aqua.spout.Config.TaintType.*;

public class TaintAnalysis implements Analysis<Taint> {

    private final TreeMap<Integer, String> ifColorNames = new TreeMap<>();

    private static Taint ifTaint = null;

    private InformationFlowScope iflowScopes = new InformationFlowScope(null, null, 0, null, -1);

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

    public void taintObject(StaticObject o, int color) {
        // TODO: maybe most of this code should move to SPouT?
        if (!o.hasAnnotations()) {
            int lengthAnnotations = ((ObjectKlass) o.getKlass()).getFieldTable().length + 1;
            Annotations[] annotations = new Annotations[lengthAnnotations];
            o.setAnnotations(annotations);
        }

        Annotations a = Annotations.annotation(o.getAnnotations(), -1);
        a.set(config.getTaintIdx(), ColorUtil.joinColors(
                (Taint) a.getAnnotations()[config.getTaintIdx()], new Taint(color)));
    }

    public void checkTaint(AnnotatedValue o, int color) {
        Taint taint = Annotations.annotation( o, config.getTaintIdx());
        if (type.equals(INFORMATION)) {
            trace.addElement(new TaintCheck(color, taint, ifColorNames));
        }
        else if (ColorUtil.hasColor(taint, color)) {
            trace.addElement(new TaintViolation(color));
        }
        else {
            SPouT.debug("Checking for taint with color " + color);
        }
    }

    public void checkTaintObject(StaticObject o, int color) {
        Annotations a = Annotations.annotation(o.getAnnotations(), -1);
        Taint taint = (Taint) Annotations.annotation(a, config.getTaintIdx());
        if (type.equals(INFORMATION)) {
            trace.addElement(new TaintCheck(color, taint, ifColorNames));
        }
        else if (ColorUtil.hasColor(taint, color)) {
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
        //SPouT.log("new scope at bci=" + bci + ", ipdStart=" + ipdBCI);

        // new taint
        Taint decisionTaint = ColorUtil.joinColors(ifTaint, a1, a2);
        String decisionName = iflowScopes.nextDecisionName();
        Taint scopeTaint = ColorUtil.addColor(decisionTaint, ++nextFreeColor);

        iflowScopes = new InformationFlowScope(iflowScopes, frame, branch, scopeTaint, ipdBCI);
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

    public void informationFlowNextBytecode(VirtualFrame frame, int bci) {
        if (!tainted || ifExceptionThrown) return;
        while (iflowScopes.isEnd(frame, bci)) {
            //SPouT.debug("pop iflow scope at ipd=" + bci);
            iflowScopes = iflowScopes.parent;
            ifTaint = iflowScopes.taint;
        }
    }


    public void informationFlowMethodReturn(VirtualFrame frame) {
        if (!tainted || ifExceptionThrown) return;
        while (iflowScopes.frame == frame) {
            //SPouT.debug("pop iflow scope at return");
            iflowScopes = iflowScopes.parent;
            ifTaint = iflowScopes.taint;
        }
    }

    public void setArrayAccessInformationFlow(Taint idx, Taint arraySize) {
        if (!tainted || type == DATA || (arraySize == null && idx == null)) return;
        // TODO: check if this is the proper way of doing this ...
        // reasoning: array access is a control flow branch influenced
        // by symbolic index and symbolic array size
        ifTaint = ColorUtil.joinColors(ifTaint, arraySize, idx);
        iflowScopes.taint = ifTaint;
    }

    private boolean outofscope(Method m) {
        return m.getDeclaringKlass().getNameAsString().startsWith("java/") ||
                m.getDeclaringKlass().getNameAsString().startsWith("jdk/") ||
                m.getDeclaringKlass().getNameAsString().startsWith("sun/");
    }

    public void iflowRegisterException() {
        if (!tainted) return;
        ifExceptionThrown = true;
    }

    public void iflowUnregisterException(VirtualFrame frame, Method method, int bci) {
        CompilerDirectives.transferToInterpreter();
        if (!tainted || !ifExceptionThrown)  return;

        ifExceptionThrown = false;

        Taint scopeTaint = iflowScopes.taint;
        while (iflowScopes.frame != frame && iflowScopes.parent != null) {
            iflowScopes = iflowScopes.parent;
        }
        iflowScopes.setTaint(scopeTaint);
        ifTaint = scopeTaint;
    }

    public int iflowGetIpdBCI() {
        return iflowScopes.endOfScope;
    }

    public Taint getIfTaint() {
        if (!tainted || ifExceptionThrown) return null;
        return ifTaint;}


    // --------------------------------------------------------------------------
    //
    // byte codes

    @Override
    public Taint iadd(int c1, int c2, Taint a1, Taint a2) {
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
    public Taint iinc(int incr, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
    }

    @Override
    public Taint imul(int c1, int c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint idiv(int c1, int c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint irem(int c1, int c2, Taint a1, Taint a2) {
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
    public Taint ineg(int c1, Taint a1) {
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
    public Taint lcmp(long c1, long c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
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
}