/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.asm.amd64.test;

import static org.junit.Assume.assumeTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import jdk.internal.jvmci.amd64.AMD64;
import jdk.internal.jvmci.code.CallingConvention;
import jdk.internal.jvmci.code.CompilationResult;
import jdk.internal.jvmci.code.CompilationResult.DataSectionReference;
import jdk.internal.jvmci.code.DataSection.Data;
import jdk.internal.jvmci.code.DataSection.DataBuilder;
import jdk.internal.jvmci.code.Register;
import jdk.internal.jvmci.code.RegisterConfig;
import jdk.internal.jvmci.code.TargetDescription;
import jdk.internal.jvmci.meta.JavaConstant;
import jdk.internal.jvmci.meta.JavaKind;

import org.junit.Before;
import org.junit.Test;

import com.oracle.graal.asm.amd64.AMD64Assembler;
import com.oracle.graal.asm.amd64.AMD64MacroAssembler;
import com.oracle.graal.asm.test.AssemblerTest;

public class SimpleAssemblerTest extends AssemblerTest {

    @Before
    public void checkAMD64() {
        assumeTrue("skipping AMD64 specific test", codeCache.getTarget().arch instanceof AMD64);
    }

    @Test
    public void intTest() {
        CodeGenTest test = new CodeGenTest() {

            @Override
            public byte[] generateCode(CompilationResult compResult, TargetDescription target, RegisterConfig registerConfig, CallingConvention cc) {
                AMD64Assembler asm = new AMD64Assembler(target, registerConfig);
                Register ret = registerConfig.getReturnRegister(JavaKind.Int);
                asm.movl(ret, 8472);
                asm.ret(0);
                return asm.close(true);
            }
        };
        assertReturn("intStub", test, 8472);
    }

    @Test
    public void doubleTest() {
        CodeGenTest test = new CodeGenTest() {

            @Override
            public byte[] generateCode(CompilationResult compResult, TargetDescription target, RegisterConfig registerConfig, CallingConvention cc) {
                AMD64MacroAssembler asm = new AMD64MacroAssembler(target, registerConfig);
                Register ret = registerConfig.getReturnRegister(JavaKind.Double);
                Data data = new Data(8, 8, DataBuilder.serializable(JavaConstant.forDouble(84.72)));
                DataSectionReference ref = compResult.getDataSection().insertData(data);
                compResult.recordDataPatch(asm.position(), ref);
                asm.movdbl(ret, asm.getPlaceholder());
                asm.ret(0);
                return asm.close(true);
            }
        };
        assertReturn("doubleStub", test, 84.72);
    }

    @Test
    public void rawDoubleTest() {
        CodeGenTest test = new CodeGenTest() {

            @Override
            public byte[] generateCode(CompilationResult compResult, TargetDescription target, RegisterConfig registerConfig, CallingConvention cc) {
                AMD64MacroAssembler asm = new AMD64MacroAssembler(target, registerConfig);
                Register ret = registerConfig.getReturnRegister(JavaKind.Double);

                byte[] rawBytes = new byte[8];
                ByteBuffer.wrap(rawBytes).order(ByteOrder.nativeOrder()).putDouble(84.72);
                Data data = new Data(8, 8, DataBuilder.raw(rawBytes));
                DataSectionReference ref = compResult.getDataSection().insertData(data);
                compResult.recordDataPatch(asm.position(), ref);
                asm.movdbl(ret, asm.getPlaceholder());
                asm.ret(0);
                return asm.close(true);
            }
        };
        assertReturn("doubleStub", test, 84.72);
    }

    public static int intStub() {
        return 0;
    }

    public static double doubleStub() {
        return 0.0;
    }
}
