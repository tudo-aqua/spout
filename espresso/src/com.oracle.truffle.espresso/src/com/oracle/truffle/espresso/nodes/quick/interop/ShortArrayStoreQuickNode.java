/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.nodes.quick.interop;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.bytecodes.ShortArrayStore;
import com.oracle.truffle.espresso.nodes.bytecodes.ShortArrayStoreNodeGen;
import com.oracle.truffle.espresso.nodes.quick.QuickNode;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.SPouT;

/**
 * @see ShortArrayStore
 */
public final class ShortArrayStoreQuickNode extends QuickNode {

    static final int stackEffectOf_SASTORE = Bytecodes.stackEffectOf(Bytecodes.SASTORE);

    @Child ShortArrayStore.WithoutNullCheck shortArrayStore;

    public ShortArrayStoreQuickNode(int top, int callerBCI) {
        super(top, callerBCI);
        this.shortArrayStore = ShortArrayStoreNodeGen.WithoutNullCheckNodeGen.create();
    }

    @Override
    public int execute(VirtualFrame frame) {
        short value = (short) BytecodeNode.popInt(frame, top - 1);
        int index = BytecodeNode.popInt(frame, top - 2);
        StaticObject array = nullCheck(BytecodeNode.popObject(frame, top - 3));
        SPouT.setArrayAnnotations(frame, getBytecodeNode(), getBci(frame), array, index, top -1, top - 2, EspressoLanguage.get(this));
        shortArrayStore.execute(array, index, value);
        return stackEffectOf_SASTORE;
    }
}
