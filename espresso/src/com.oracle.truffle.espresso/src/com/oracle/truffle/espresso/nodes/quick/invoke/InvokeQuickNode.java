/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.quick.invoke;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.classfile.descriptors.SignatureSymbols;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.EspressoFrame;
import tools.aqua.spout.AnnotatedVM;
import tools.aqua.spout.SPouT;
import com.oracle.truffle.espresso.nodes.quick.QuickNode;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public abstract class InvokeQuickNode extends QuickNode {
    private static final Object[] EMPTY_ARGS = new Object[0];

    public final Method.MethodVersion method;

    // Helper information for easier arguments handling.
    protected final int resultAt;
    protected final int stackEffect;

    // Helps check for no foreign objects
    private final boolean returnsPrimitive;

    public InvokeQuickNode(Method m, int top, int callerBCI) {
        super(top, callerBCI);
        this.method = m.getMethodVersion();
        this.resultAt = top - (SignatureSymbols.slotsForParameters(m.getParsedSignature()) + (m.hasReceiver() ? 1 : 0));
        this.stackEffect = (resultAt - top) + m.getReturnKind().getSlotCount();
        this.returnsPrimitive = m.getReturnKind().isPrimitive();
    }

    public InvokeQuickNode(Method.MethodVersion version, int top, int callerBCI) {
        super(top, callerBCI);
        this.method = version;
        Method m = version.getMethod();
        this.resultAt = top - (SignatureSymbols.slotsForParameters(m.getParsedSignature()) + (m.hasReceiver() ? 1 : 0));
        this.stackEffect = (resultAt - top) + m.getReturnKind().getSlotCount();
        this.returnsPrimitive = m.getReturnKind().isPrimitive();
    }

    public final StaticObject peekReceiver(VirtualFrame frame) {
        return EspressoFrame.peekReceiver(frame, top, method.getMethod());
    }

    @Override
    public int execute(VirtualFrame frame, boolean isContinuationResume) {
        return 0;
    }

    protected Object[] getArguments(VirtualFrame frame) {
        /*
         * Method signature does not change across methods. Can safely use the constant signature
         * from `method` instead of the non-constant signature from the lookup.
         */
        if (method.isStatic() && method.getMethod().getParameterCount() == 0) {
            // Don't create an array for empty arguments.
            return EMPTY_ARGS;
        }
        return EspressoFrame.popArguments(frame, top, !method.isStatic(), method.getMethod().getParsedSignature());
    }

    public final int pushResult(VirtualFrame frame, int result) {
        clearArgumentAnnotations(frame);
        EspressoFrame.putInt(frame, resultAt, result);
        return stackEffect;
    }

    public final int pushResult(VirtualFrame frame, long result) {
        clearArgumentAnnotations(frame);
        EspressoFrame.putLong(frame, resultAt, result);
        return stackEffect;
    }

    public final int pushResult(VirtualFrame frame, float result) {
        clearArgumentAnnotations(frame);
        EspressoFrame.putFloat(frame, resultAt, result);
        return stackEffect;
    }

    public final int pushResult(VirtualFrame frame, double result) {
        clearArgumentAnnotations(frame);
        EspressoFrame.putDouble(frame, resultAt, result);
        return stackEffect;
    }

    /**
     * Removes the annotations of the argument slots (and of the slots of the result): nodes that do not pop the
     * arguments (e.g. substitutions inlined in the bytecode) would leave the term of a reference argument in the slot
     * of the result.
     */
    private void clearArgumentAnnotations(VirtualFrame frame) {
        int end = Math.max(top, resultAt + 2);
        for (int slot = resultAt; slot < end; slot++) {
            AnnotatedVM.clearAnnotations(frame, slot);
        }
    }

    public final int pushResult(VirtualFrame frame, StaticObject result) {
        clearArgumentAnnotations(frame);
        getBytecodeNode().checkNoForeignObjectAssumption(result);
        EspressoFrame.putObject(frame, resultAt, result);
        // the term of the returned reference (see SPouT.setReturned)
        AnnotatedVM.putAnnotations(frame, resultAt, SPouT.takeReturned(result));
        return stackEffect;
    }

    public final int pushResult(VirtualFrame frame, Object result) {
        clearArgumentAnnotations(frame);
        if (!returnsPrimitive) {
            getBytecodeNode().checkNoForeignObjectAssumption((StaticObject) result);
        }
        EspressoFrame.putKind(frame, resultAt, result, method.getMethod().getReturnKind());
        if (!returnsPrimitive && method.getMethod().getReturnKind().isObject()) {
            // the term of the returned reference (see SPouT.setReturned)
            AnnotatedVM.putAnnotations(frame, resultAt, SPouT.takeReturned(result));
        }
        return stackEffect;
    }

    @Override
    public final boolean removedByRedefinition() {
        if (method.getRedefineAssumption().isValid()) {
            return false;
        } else {
            return method.getMethod().isRemovedByRedefinition();
        }
    }

    @Override
    public final String toString() {
        return "INVOKE: " + method.getDeclaringKlass().getExternalName() + "." + method.getName() + ":" + method.getRawSignature();
    }
}
