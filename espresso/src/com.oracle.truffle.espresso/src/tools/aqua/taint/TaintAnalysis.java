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
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import tools.aqua.concolic.SymbolDeclaration;
import tools.aqua.spout.*;

import java.util.Arrays;
import java.util.TreeMap;

import static com.oracle.truffle.espresso.bytecode.Bytecodes.*;
import static tools.aqua.spout.Config.TaintType.CONTROL;
import static tools.aqua.spout.Config.TaintType.INFORMATION;

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
        AnnotatedValue av = new AnnotatedValue(o);
        av.set(config.getTaintIdx(), new Taint(color));
        SPouT.debug("Tainting with color " + color);
        tainted = true;
        return av;
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

    // --------------------------------------------------------------------------
    //
    // byte codes

    @Override
    public Taint iadd(int c1, int c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2, ifTaint);
    }

    @Override
    public Taint iinc(int incr, Taint a1) {
        return ColorUtil.joinColors(a1, ifTaint);
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

}