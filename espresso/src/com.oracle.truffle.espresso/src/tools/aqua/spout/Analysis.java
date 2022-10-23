/*
 * Copyright (c) 2021 Automated Quality Assurance Group, TU Dortmund University.
 * All rights reserved. DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE
 * HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General default License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General default License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General default License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact the Automated Quality Assurance Group, TU Dortmund University
 * or visit https://aqua.engineering if you need additional information or have any
 * questions.
 */

package tools.aqua.spout;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.smt.Expression;

public interface Analysis<T> {

    //default void terminate();

    default T iconst(int c1) {
        return null;
    }

    default T iadd(int c1, int c2, T a1, T a2) {
        return null;
    }
    
    default T ladd(long c1, long c2, T a1, T a2) {
        return null;
    }

    default T fadd(float c1, float c2, T a1, T a2) {
        return null;
    }

    default T dadd(double c1, double c2, T a1, T a2) {
        return null;
    }

    default T isub(int c1, int c2, T a1, T a2) {
        return null;
    }

    default T lsub(long c1, long c2, T a1, T a2) {
        return null;
    }

    default T fsub(float c1, float c2, T a1, T a2) {
        return null;
    }

    default T dsub(double c1, double c2, T a1, T a2) {
        return null;
    }

    default T imul(int c1, int c2, T a1, T a2) {
        return null;
    }

    default T lmul(long c1, long c2, T a1, T a2) {
        return null;
    }

    default T fmul(float c1, float c2, T a1, T a2) {
        return null;
    }

    default T dmul(double c1, double c2, T a1, T a2) {
        return null;
    }

    default void checkNonZero(int value, T taint) {
    }

    default void checkNonZero(long value, T taint) {
    }

    default T idiv(int c1, int c2, T a1, T a2) {
        return null;
    }

    default T ldiv(long c1, long c2, T a1, T a2) {
        return null;
    }

    default T fdiv(float c1, float c2, T a1, T a2) {
        return null;
    }

    default T ddiv(double c1, double c2, T a1, T a2) {
        return null;
    }

    default T irem(int c1, int c2, T a1, T a2) {
        return null;
    }

    default T lrem(long c1, long c2, T a1, T a2) {
        return null;
    }

    default T frem(float c1, float c2, T a1, T a2) {
        return null;
    }

    default T drem(double c1, double c2, T a1, T a2) {
        return null;
    }

    default T ineg(int c1, T a1) {
        return null;
    }

    default T lneg(long c1, T a1) {
        return null;
    }

    default T fneg(float c1, T a1) {
        return null;
    }

    default T dneg(double c1, T a1) {
        return null;
    }

    default T ishl(int c1, int c2, T a1, T a2) {
        return null;
    }

    default T lshl(int c1, long c2, T a1, T a2) {
        return null;
    }

    default T ishr(int c1, int c2, T a1, T a2) {
        return null;
    }

    default T lshr(int c1, long c2, T a1, T a2) {
        return null;
    }

    default T iushr(int c1, int c2, T a1, T a2) {
        return null;
    }

    default T lushr(int c1, long c2, T a1, T a2) {
        return null;
    }

    default T iand(int c1, int c2, T a1, T a2) {
        return null;
    }

    default T land(long c1, long c2, T a1, T a2) {
        return null;
    }

    default T ior(int c1, int c2, T a1, T a2) {
        return null;
    }

    default T lor(long c1, long c2, T a1, T a2) {
        return null;
    }

    default T ixor(int c1, int c2, T a1, T a2) {
        return null;
    }

    default T lxor(long c1, long c2, T a1, T a2) {
        return null;
    }

    default T iinc(int incr, T a1) {
        return null;
    }

    default T i2l(long c1, T a1) {
        return null;
    }

    default T i2f(float c1, T a1) {
        return null;
    }

    default T i2d(double c1, T a1) {
        return null;
    }

    default T l2i(int c1, T a1) {
        return null;
    }

    default T l2f(float c1, T a1) {
        return null;
    }

    default T l2d(double c1, T a1) {
        return null;
    }

    default T f2i(int c1, T a1) {
        return null;
    }

    default T f2l(long c1, T a1) {
        return null;
    }

    default T f2d(double c1, T a1) {
        return null;
    }

    default T d2i(int c1, T a1) {
        return null;
    }

    default T d2l(long c1, T a1) {
        return null;
    }

    default T d2f(float c1, T a1) {
        return null;
    }

    default T i2b(byte c1, T a1) {
        return null;
    }

    default T i2c(char c1, T a1) {
        return null;
    }

    default T i2s(short c1, T a1) {
        return null;
    }

    default T lcmp(long c1, long c2, T a1, T a2) {
        return null;
    }

    default T fcmpl(float c1, float c2, T a1, T a2) {
        return null;
    }

    default T fcmpg(float c1, float c2, T a1, T a2) {
        return null;
    }

    default T dcmpl(double c1, double c2, T a1, T a2) {
        return null;
    }

    default T dcmpg(double c1, double c2, T a1, T a2) {
        return null;
    }

    default void takeBranchPrimitive1(VirtualFrame frame, BytecodeNode bcn, int bci,
                                      int opcode, boolean takeBranch, T a1) {
    }

    default void takeBranchPrimitive2(VirtualFrame frame, BytecodeNode bcn, int bci,
                                      int opcode, boolean takeBranch, int c1, int c2, T a1, T a2) {
    }

    default void tableSwitch(VirtualFrame frame, BytecodeNode bcn, int bci,
                             int low, int high, int concIndex, T a1) {
    }

    default void lookupSwitch(VirtualFrame frame, BytecodeNode bcn, int bci,
                              int[] vals, int key, T a1) {
    }

    // Strings
    default T stringLength(int c, T s) { return null; }
}