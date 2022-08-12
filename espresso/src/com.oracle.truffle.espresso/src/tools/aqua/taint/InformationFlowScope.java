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
import com.oracle.truffle.espresso.nodes.BytecodeNode;

import java.util.Arrays;

public class InformationFlowScope {

    /*
     * frame to which this scope belongs
     */
    public VirtualFrame frame;

    /*
     * parent scope
     */
    InformationFlowScope parent = null;

    /*
     * essentially: left or right
     */
    int branchId;

    /*
     * color of this scope
     */
    Taint taint;

    /*
     * bci's that leave this scope
     */
    int endOfScope;

    /*
     * handlers of the current scope
     */
    int[] handlers;

    /*
     * distinguish multiple subsequent independent branches
     */
    int nameCount = 0;

    /*
     * color for this decision
     */
    int decisionColor;

    public InformationFlowScope(InformationFlowScope parent, VirtualFrame frame, int branchId, Taint taint, int end, int decisionColor) {
        this.frame = frame;
        this.endOfScope = end;
        this.parent = parent;
        this.taint = taint;
        this.branchId = branchId;
        this.decisionColor = decisionColor;
        this.handlers = null;
    }

    public boolean isEnd(VirtualFrame frame, int bci) {
        return (endOfScope > 0) && (endOfScope == bci) && (frame == this.frame);
    }

    public String nextDecisionName() {
        nameCount++;
        return (parent == null ? "if" : parent.currentDecisionName()) + "_" + branchId + "n" + nameCount;
    }

    public String currentDecisionName() {
        return (parent == null ? "if" : parent.currentDecisionName()) + "_" + branchId + "n" + nameCount;
    }

    public void setTaint(Taint taint) {
        this.taint = taint;
    }

    public void setHandlers(int[] handlers) {
        this.handlers = handlers;
    }

    public boolean isHandledBy(VirtualFrame frame, BytecodeNode bcn, int bci) {
        if (this.handlers == null) return false;
        if (frame != this.frame) return false;
        int b = bcn.getPostDominatorAnalysis().getBlock(bci);
        for (int i=0; i<handlers.length; i++) {
            if (b == handlers[i])
                return true;
        }
        return false;
    }

    @CompilerDirectives.TruffleBoundary
    @Override
    public String toString() {
        return "InformationFlowScope{" +
                "parent=" + parent +
                ", branchId=" + branchId +
                ", taint=" + taint +
                ", endOfScope=" + endOfScope +
                ", handlers=" + Arrays.toString(handlers) +
                ", nameCount=" + nameCount +
                ", decisionColor=" + decisionColor +
                '}';
    }
}
