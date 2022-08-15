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

    private InformationFlowScope iflowScopes = new InformationFlowScope(null, null, 0, null, new int[]{});

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
    }

    // --------------------------------------------------------------------------
    //
    // information flow

    private void informationFlowAddScope(VirtualFrame frame, Method method, int bci, int branch, Taint a1, Taint a2) {
        CompilerDirectives.transferToInterpreter();
        if (!tainted || ifExceptionThrown) return;

        if (outofscope(method)) return;

        int[] endOfScope = GraphUtil.getEndOfScopeAfterBlock(method, bci);
        // new taint
        Taint decisionTaint = ColorUtil.joinColors(ifTaint, a1, a2);
        String decisionName = iflowScopes.nextDecisionName();
        Taint scopeTaint = ColorUtil.addColor(decisionTaint, ++nextFreeColor);

        iflowScopes = new InformationFlowScope(iflowScopes, frame, branch, scopeTaint, endOfScope);
        ifTaint = scopeTaint;
        informationFlowAddColor(decisionName, method);
        if (type == INFORMATION) {
            trace.addElement(new InformationFlow(decisionName, decisionTaint, ifColorNames));
        }
    }

    @CompilerDirectives.TruffleBoundary
    private void informationFlowAddColor(String decisionName, Method m) {
        ifColorNames.put(nextFreeColor, decisionName);
    }

    public void informationFlowNextBytecode(VirtualFrame frame, Method method, int bci, int opcode) {
        CompilerDirectives.transferToInterpreter();
        if (!tainted || ifExceptionThrown) return;

        if (outofscope(method)) return;

        if (opcode != ATHROW) {
            while (iflowScopes.isEnd(frame, bci)) {
                iflowScopes = iflowScopes.parent;
                ifTaint = iflowScopes.taint;
            }

            // TODO: sometimes in CFGs return statements are not in the leafs only ...
            if (opcode == RETURN || opcode == IRETURN || opcode == LRETURN ||
                    opcode == ARETURN || opcode == FRETURN || opcode == DRETURN) {

                while (iflowScopes.frame == frame) {
                    iflowScopes = iflowScopes.parent;
                    ifTaint = iflowScopes.taint;
                }
            }
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

        int[] endOfScope = GraphUtil.getEndOfScopeAfterPreviousBlock(method, bci);
        Taint scopeTaint = iflowScopes.taint;
        while (iflowScopes.frame != frame && iflowScopes.parent != null) {
            iflowScopes = iflowScopes.parent;
        }
        iflowScopes = new InformationFlowScope(iflowScopes, frame, 0, scopeTaint, endOfScope);
        ifTaint = scopeTaint;
    }

    // --------------------------------------------------------------------------
    //
    // byte codes

    @Override
    public Taint iadd(int c1, int c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2);
    }

    @Override
    public void takeBranchPrimitive1(VirtualFrame frame, Method method, int bci, int opcode, boolean takeBranch, Taint a) {
        if (type == INFORMATION || (type == CONTROL && a != null)) {
            informationFlowAddScope(frame, method, bci, takeBranch ? 0 : 1, a, null);
        }
    }

    @Override
    public void takeBranchPrimitive2(VirtualFrame frame, Method method, int bci, int opcode, boolean takeBranch, int c1, int c2, Taint a1, Taint a2) {
        if (type == INFORMATION || (type == CONTROL && (a1 != null || a2 != null))) {
            informationFlowAddScope(frame, method, bci, takeBranch ? 0 : 1, a1, a2);
        }
    }
}