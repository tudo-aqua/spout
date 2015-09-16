/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.sparc;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.CONST;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.REG;
import static com.oracle.graal.lir.LIRValueUtil.isJavaConstant;
import static jdk.internal.jvmci.code.ValueUtil.asRegister;
import static jdk.internal.jvmci.code.ValueUtil.isRegister;
import static jdk.internal.jvmci.sparc.SPARC.g0;
import jdk.internal.jvmci.common.JVMCIError;
import jdk.internal.jvmci.meta.JavaKind;
import jdk.internal.jvmci.meta.Value;

import com.oracle.graal.asm.sparc.SPARCMacroAssembler;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.asm.CompilationResultBuilder;

public class SPARCTestOp extends SPARCLIRInstruction {
    public static final LIRInstructionClass<SPARCTestOp> TYPE = LIRInstructionClass.create(SPARCTestOp.class);
    public static final SizeEstimate SIZE = SizeEstimate.create(1);

    @Use({REG}) protected Value x;
    @Use({REG, CONST}) protected Value y;

    public SPARCTestOp(Value x, Value y) {
        super(TYPE, SIZE);
        this.x = x;
        this.y = y;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        if (isRegister(y)) {
            switch ((JavaKind) x.getPlatformKind()) {
                case Short:
                case Byte:
                case Char:
                case Boolean:
                case Int:
                    masm.andcc(asRegister(x, JavaKind.Int), asRegister(y, JavaKind.Int), g0);
                    break;
                case Long:
                    masm.andcc(asRegister(x, JavaKind.Long), asRegister(y, JavaKind.Long), g0);
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere();
            }
        } else if (isJavaConstant(y)) {
            switch ((JavaKind) x.getPlatformKind()) {
                case Short:
                case Byte:
                case Char:
                case Boolean:
                case Int:
                    masm.andcc(asRegister(x, JavaKind.Int), crb.asIntConst(y), g0);
                    break;
                case Long:
                    masm.andcc(asRegister(x, JavaKind.Long), crb.asIntConst(y), g0);
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere();
            }
        } else {
            throw JVMCIError.shouldNotReachHere();
        }
    }

}
