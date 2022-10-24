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

package tools.aqua.concolic;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.constantpool.InvokeDynamicConstant;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.smt.*;
import tools.aqua.spout.*;

import java.lang.reflect.AnnotatedParameterizedType;
import java.util.Arrays;

import static com.oracle.truffle.espresso.bytecode.Bytecodes.*;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPLE;
import static tools.aqua.concolic.PathCondition.*;
import static tools.aqua.smt.Constant.*;
import static tools.aqua.smt.OperatorComparator.*;
import static tools.aqua.smt.OperatorComparator.D2F;
import static tools.aqua.smt.OperatorComparator.F2D;
import static tools.aqua.smt.OperatorComparator.I2L;
import static tools.aqua.smt.OperatorComparator.L2D;
import static tools.aqua.smt.OperatorComparator.L2F;
import static tools.aqua.smt.Types.LONG;

public class ConcolicAnalysis implements Analysis<Expression> {

    private final Config config;

    private final Trace trace;

    public ConcolicAnalysis(Config config) {
        this.config = config;
        this.trace = config.getTrace();
    }

    public Object nextSymbolicInt() {
        AnnotatedValue av = config.nextSymbolicInt();
        trace.addElement(new SymbolDeclaration(Annotations.annotation(av, config.getConcolicIdx())));
        return av;
    }

    public Object nextSymbolicLong() {
        AnnotatedValue av = config.nextSymbolicLong();
        trace.addElement(new SymbolDeclaration(Annotations.annotation(av, config.getConcolicIdx())));
        return av;
    }


    public Object nextSymbolicFloat() {
        AnnotatedValue av = config.nextSymbolicFloat();
        trace.addElement(new SymbolDeclaration(Annotations.annotation(av, config.getConcolicIdx())));
        return av;
    }

    public Object nextSymbolicDouble() {
        AnnotatedValue av = config.nextSymbolicDouble();
        trace.addElement(new SymbolDeclaration(Annotations.annotation(av, config.getConcolicIdx())));
        return av;
    }

    public Object nextSymbolicBoolean() {
        AnnotatedValue av = config.nextSymbolicBoolean();
        trace.addElement(new SymbolDeclaration(Annotations.annotation(av, config.getConcolicIdx())));
        return av;
    }

    public Object nextSymbolicByte() {
        AnnotatedValue av = config.nextSymbolicByte();
        trace.addElement(new SymbolDeclaration(Annotations.annotation(av, config.getConcolicIdx())));
        av.set(config.getConcolicIdx(), new ComplexExpression(B2I, (Expression) Annotations.annotation(av, config.getConcolicIdx())));
        return av;
    }

    public Object nextSymbolicChar() {
        AnnotatedValue av = config.nextSymbolicChar();
        trace.addElement(new SymbolDeclaration(Annotations.annotation(av, config.getConcolicIdx())));
        av.set(config.getConcolicIdx(), new ComplexExpression(C2I, (Expression) Annotations.annotation(av, config.getConcolicIdx())));
        return av;
    }

    public Object nextSymbolicShort() {
        AnnotatedValue av = config.nextSymbolicShort();
        trace.addElement(new SymbolDeclaration(Annotations.annotation(av, config.getConcolicIdx())));
        av.set(config.getConcolicIdx(), new ComplexExpression(S2I, (Expression) Annotations.annotation(av, config.getConcolicIdx())));
        return av;
    }

    public StaticObject nextSymbolicString(Meta meta) {
        Config.SymbolicStringValue ssv = config.nextSymbolicString();
        StaticObject guestString = meta.toGuestString(ssv.concrete);
        int lengthAnnotations = ((ObjectKlass) guestString.getKlass()).getFieldTable().length + 1;
        Annotations[] annotations = new Annotations[lengthAnnotations];
        Annotations stringDescription = Annotations.emptyArray();
        stringDescription.set(config.getConcolicIdx(), ssv.symbolic);
        annotations[annotations.length - 1] = stringDescription;
        guestString.setAnnotations(annotations);
        trace.addElement(new SymbolDeclaration(ssv.symbolic));
        return guestString;
    }

    private Expression binarySymbolicOp(OperatorComparator op, Types typeLeft, Types typeRight,
                                        Object cLeft, Object cRight, Expression sLeft, Expression sRight) {

        Expression ret = null;
        if (sLeft != null || sRight != null) {
            if (sLeft == null) sLeft = Expression.fromConstant(typeLeft, cLeft);
            if (sRight == null) sRight = Expression.fromConstant(typeRight, cRight);
            ret = new ComplexExpression(op, sLeft, sRight);
        }
        return ret;
    }

    private Expression binarySymbolicOp(OperatorComparator op, Types type,
                                        Object cLeft, Object cRight, Expression sLeft, Expression sRight) {

        return binarySymbolicOp(op, type, type, cLeft, cRight, sLeft, sRight);
    }

    private Expression unarySymbolicOp(OperatorComparator op, Expression s1) {
        if (s1 == null) {
            return null;
        }
        return new ComplexExpression(op, s1);
    }

    // primitive operations

    @Override
    public Expression iadd(int c1, int c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.IADD, Types.INT, c1, c2, a1, a2);
    }

    @Override
    public Expression ladd(long c1, long c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.LADD, LONG, c1, c2, a1, a2);
    }

    @Override
    public Expression fadd(float c1, float c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.FADD, Types.FLOAT, c1, c2, a1, a2);
    }

    @Override
    public Expression dadd(double c1, double c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.DADD, Types.DOUBLE, c1, c2, a1, a2);
    }

    @Override
    public Expression isub(int c1, int c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.ISUB, Types.INT, c2, c1, a2, a1);
    }

    @Override
    public Expression lsub(long c1, long c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.LSUB, LONG, c2, c1, a2, a1);
    }

    @Override
    public Expression fsub(float c1, float c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.FSUB, Types.FLOAT, c2, c1, a2, a1);
    }

    @Override
    public Expression dsub(double c1, double c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.DSUB, Types.DOUBLE, c2, c1, a2, a1);
    }

    @Override
    public Expression iinc(int incr, Expression s1) {
        Expression sym = null;
        if (s1 != null) {
            Constant symbIncr = Constant.fromConcreteValue(incr);
            sym = new ComplexExpression(OperatorComparator.IADD, s1, symbIncr);
        }
        return sym;
    }

    @Override
    public Expression lcmp(long c1, long c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.LCMP, LONG, c2, c1, a2, a1);
    }

    @Override
    public Expression fcmpl(float c1, float c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.FCMPL, Types.FLOAT, c2, c1, a2, a1);
    }

    @Override
    public Expression fcmpg(float c1, float c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.FCMPG, Types.FLOAT, c2, c1, a2, a1);
    }

    @Override
    public Expression dcmpl(double c1, double c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.DCMPL, Types.DOUBLE, c2, c1, a2, a1);
    }

    @Override
    public Expression dcmpg(double c1, double c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.DCMPG, Types.DOUBLE, c2, c1, a2, a1);
    }

    @Override
    public Expression irem(int c1, int c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.IREM, Types.INT, c1, c2, a1, a2);
    }

    @Override
    public Expression lrem(long c1, long c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.LREM, LONG, c2, c1, a2, a1);
    }

    @Override
    public Expression frem(float c1, float c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.FREM, Types.FLOAT, c2, c1, a2, a1);
    }

    @Override
    public Expression drem(double c1, double c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.DREM, Types.DOUBLE, c2, c1, a2, a1);
    }

    @Override
    public Expression ishl(int c1, int c2, Expression a1, Expression a2) {
        if (a1 != null || a2 != null) {
            a1 = new ComplexExpression(OperatorComparator.IAND, a1 != null ? a1 : Constant.fromConcreteValue(c1), INT_0x1F);
        }
        return binarySymbolicOp(OperatorComparator.ISHL, Types.INT, c2, c1, a2, a1);
    }

    @Override
    public Expression lshl(int c1, long c2, Expression a1, Expression a2) {
        if (a1 != null || a2 != null) {
            a1 = new ComplexExpression(I2L, new ComplexExpression(OperatorComparator.IAND, a1 != null ? a1 : Constant.fromConcreteValue(c1), INT_0x3F));
        }
        return binarySymbolicOp(OperatorComparator.LSHL, LONG, Types.INT, c2, c1, a2, a1);
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    public Expression lshr(int c1, long c2, Expression a1, Expression a2) {
        if (a1 != null || a2 != null) {
            a1 = new ComplexExpression(I2L, new ComplexExpression(OperatorComparator.IAND, a1 != null ? a1 : Constant.fromConcreteValue(c1), INT_0x3F));
        }
        return binarySymbolicOp(OperatorComparator.LSHR, LONG, Types.INT, c2, c1, a2, a1);
    }


    @Override
    @CompilerDirectives.TruffleBoundary
    public Expression lushr(int c1, long c2, Expression a1, Expression a2) {
        if (a1 != null || a2 != null) {
            a1 = new ComplexExpression(I2L, new ComplexExpression(OperatorComparator.IAND, a1 != null ? a1 : Constant.fromConcreteValue(c1), INT_0x3F));
        }
        return binarySymbolicOp(OperatorComparator.LUSHR, LONG, Types.INT, c2, c1, a2, a1);
    }

    @Override
    public Expression ishr(int c1, int c2, Expression a1, Expression a2) {
        if (a1 != null || a2 != null) {
            a1 = new ComplexExpression(OperatorComparator.IAND, a1 != null ? a1 : Constant.fromConcreteValue(c1), INT_0x1F);
        }
        return binarySymbolicOp(OperatorComparator.ISHR, Types.INT, c1, c2, a1, a2);
    }

    @Override
    public Expression iushr(int c1, int c2, Expression a1, Expression a2) {
        if (a1 != null || a2 != null) {
            a1 = new ComplexExpression(OperatorComparator.IAND, a1 != null ? a1 : Constant.fromConcreteValue(c1), INT_0x1F);
        }
        return binarySymbolicOp(OperatorComparator.IUSHR, Types.INT, c1, c2, a1, a2);
    }

    @Override
    public Expression idiv(int c1, int c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.IDIV, Types.INT, c2, c1, a2, a1);
    }

    @Override
    public Expression ldiv(long c1, long c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.LDIV, LONG, c2, c1, a2, a1);
    }

    @Override
    public Expression fdiv(float c1, float c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.FDIV, Types.FLOAT, c2, c1, a2, a1);
    }

    @Override
    public Expression ddiv(double c1, double c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.DDIV, Types.DOUBLE, c2, c1, a2, a1);
    }

    @Override
    public Expression imul(int c1, int c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.IMUL, Types.INT, c1, c2, a1, a2);
    }

    @Override
    public Expression lmul(long c1, long c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.LMUL, LONG, c1, c2, a1, a2);
    }

    @Override
    public Expression fmul(float c1, float c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.FMUL, Types.FLOAT, c1, c2, a1, a2);
    }

    @Override
    public Expression dmul(double c1, double c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.DMUL, Types.DOUBLE, c1, c2, a1, a2);
    }

    @Override
    public Expression ineg(int c1, Expression a1) {
        return unarySymbolicOp(OperatorComparator.INEG, a1);
    }

    @Override
    public Expression lneg(long c1, Expression a1) {
        return unarySymbolicOp(OperatorComparator.LNEG, a1);
    }

    @Override
    public Expression fneg(float c1, Expression a1) {
        return unarySymbolicOp(OperatorComparator.FNEG, a1);
    }

    @Override
    public Expression dneg(double c1, Expression a1) {
        return unarySymbolicOp(OperatorComparator.DNEG, a1);
    }

    @Override
    public Expression i2b(byte c1, Expression a1) {
        return unarySymbolicOp(OperatorComparator.B2I, unarySymbolicOp(OperatorComparator.I2B, a1));
    }

    @Override
    public Expression i2c(char c1, Expression a1) {
        return unarySymbolicOp(OperatorComparator.C2I, unarySymbolicOp(OperatorComparator.I2C, a1));
    }

    @Override
    public Expression i2s(short c1, Expression a1) {
        return unarySymbolicOp(OperatorComparator.S2I, unarySymbolicOp(OperatorComparator.I2S, a1));
    }

    @Override
    public Expression i2l(long c1, Expression a1) {
        Expression sym = null;
        if (a1 != null) {
            sym = new ComplexExpression(I2L, a1);
        }
        return sym;
    }

    @Override
    public Expression i2f(float c1, Expression a1) {
        Expression sym = null;
        if (a1 != null) {
            sym = new ComplexExpression(OperatorComparator.I2F, a1);
        }
        return sym;
    }

    @Override
    public Expression i2d(double c1, Expression a1) {
        Expression sym = null;
        if (a1 != null) {
            sym = new ComplexExpression(OperatorComparator.I2F, a1);
        }
        return sym;
    }

    @Override
    public Expression l2i(int c1, Expression a1) {
        return unarySymbolicOp(OperatorComparator.L2I, a1);
    }

    @Override
    public Expression l2f(float c1, Expression a1) {
        return unarySymbolicOp(L2F, a1);
    }

    @Override
    public Expression l2d(double c1, Expression a1) {
        return unarySymbolicOp(L2D, a1);
    }

    @Override
    public Expression f2i(int c1, Expression a1) {
        ComplexExpression intMinAsFloat =
                new ComplexExpression(OperatorComparator.I2F_RTZ, Constant.INT_MIN);
        ComplexExpression intMaxAsFloat =
                new ComplexExpression(OperatorComparator.I2F_RTZ, Constant.INT_MAX);
        ComplexExpression rtz = new ComplexExpression(OperatorComparator.F2I_RTZ, a1);

        return getExpressionInt(a1, intMinAsFloat, intMaxAsFloat, rtz);
    }

    @Override
    public Expression f2l(long c1, Expression a1) {
        ComplexExpression longMinAsFloat =
                new ComplexExpression(OperatorComparator.L2F_RTZ, Constant.LONG_MIN);
        ComplexExpression longMaxAsFloat =
                new ComplexExpression(OperatorComparator.L2F_RTZ, Constant.LONG_MAX);
        ComplexExpression rtz = new ComplexExpression(OperatorComparator.F2L_RTZ, a1);

        return getExpressionLong(a1, longMinAsFloat, longMaxAsFloat, rtz);
    }

    private Expression getExpressionLong(Expression a1, ComplexExpression longMinAsFloat, ComplexExpression longMaxAsFloat, ComplexExpression rtz) {
        if (a1 == null) {
            return null;
        }
        return new ComplexExpression(
                OperatorComparator.ITE,
                /* cond */ new ComplexExpression(OperatorComparator.FP_ISNAN, a1),
                /* then */ Constant.LONG_ZERO,
                /* else */ new ComplexExpression(
                OperatorComparator.ITE,
                /* cond */ new ComplexExpression(OperatorComparator.FP_ISNEG, a1),
                /* then */ new ComplexExpression(
                OperatorComparator.ITE,
                /* cond */ new ComplexExpression(
                OperatorComparator.FPLT, a1, longMinAsFloat),
                /* then */ Constant.LONG_MIN,
                /* else */ rtz),
                /* else */ new ComplexExpression(
                OperatorComparator.ITE,
                /* cond */ new ComplexExpression(
                OperatorComparator.FPGT, a1, longMaxAsFloat),
                /* then */ Constant.LONG_MAX,
                /* else */ rtz)));
    }

    @Override
    public Expression f2d(double c1, Expression a1) {
        return unarySymbolicOp(F2D, a1);
    }

    @Override
    public Expression d2l(long c1, Expression a1) {
        ComplexExpression longMinAsFloat =
                new ComplexExpression(OperatorComparator.L2D_RTZ, Constant.LONG_MIN);
        ComplexExpression longMaxAsFloat =
                new ComplexExpression(OperatorComparator.L2D_RTZ, Constant.LONG_MAX);
        ComplexExpression rtz = new ComplexExpression(OperatorComparator.D2L_RTZ, a1);
        return getExpressionLong(a1, longMinAsFloat, longMaxAsFloat, rtz);
    }

    @Override
    public Expression d2i(int c1, Expression a1) {
        ComplexExpression intMinAsFloat =
                new ComplexExpression(OperatorComparator.I2D_RTZ, Constant.INT_MIN);
        ComplexExpression intMaxAsFloat =
                new ComplexExpression(OperatorComparator.I2D_RTZ, Constant.INT_MAX);
        ComplexExpression rtz = new ComplexExpression(OperatorComparator.D2I_RTZ, a1);

        return getExpressionInt(a1, intMinAsFloat, intMaxAsFloat, rtz);
    }

    private Expression getExpressionInt(Expression a1, ComplexExpression intMinAsFloat, ComplexExpression intMaxAsFloat, ComplexExpression rtz) {
        if (a1 == null) {
            return null;
        }
        return new ComplexExpression(
                OperatorComparator.ITE,
                /* cond */ new ComplexExpression(OperatorComparator.FP_ISNAN, a1),
                /* then */ INT_ZERO,
                /* else */ new ComplexExpression(
                OperatorComparator.ITE,
                /* cond */ new ComplexExpression(OperatorComparator.FP_ISNEG, a1),
                /* then */ new ComplexExpression(
                OperatorComparator.ITE,
                /* cond */ new ComplexExpression(
                OperatorComparator.FPLT, a1, intMinAsFloat),
                /* then */ Constant.INT_MIN,
                /* else */ rtz),
                /* else */ new ComplexExpression(
                OperatorComparator.ITE,
                /* cond */ new ComplexExpression(
                OperatorComparator.FPGT, a1, intMaxAsFloat),
                /* then */ Constant.INT_MAX,
                /* else */ rtz)));
    }

    @Override
    public Expression d2f(float c1, Expression a1) {
        return unarySymbolicOp(D2F, a1);
    }

    @Override
    public Expression iand(int c1, int c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.IAND, Types.INT, c1, c2, a1, a2);
    }

    @Override
    public Expression ior(int c1, int c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.IOR, Types.INT, c1, c2, a1, a2);
    }

    @Override
    public Expression ixor(int c1, int c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.IXOR, Types.INT, c1, c2, a1, a2);
    }

    @Override
    public Expression land(long c1, long c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.LAND, LONG, c1, c2, a1, a2);
    }

    @Override
    public Expression lor(long c1, long c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.LOR, LONG, c1, c2, a1, a2);
    }

    @Override
    public Expression lxor(long c1, long c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.LXOR, LONG, c1, c2, a1, a2);
    }
// arrays

    public void newArrayPathConstraint(int cLength, Expression sLength) {
        if (sLength == null) return;

        boolean legal = (0 <= cLength);
        Expression lengthConstraint = new ComplexExpression(BVLE, INT_ZERO, sLength);
        trace.addElement(new PathCondition(
                legal ? lengthConstraint : new ComplexExpression(BNEG, lengthConstraint),
                legal ? 1 : 0,
                2));
    }

    public boolean checkArrayAccessPathConstraint(StaticObject array, int cIndex, Expression sIndex, EspressoLanguage lang) {
        assert array.isArray();
        int cLen = array.length(lang);
        boolean safe = 0 <= cIndex && cIndex < cLen;
        if ((array.hasAnnotations() && array.getAnnotations()[cLen] != null) || sIndex != null) {

            if (sIndex == null) {
                sIndex = Constant.fromConcreteValue(cIndex);
            }

            Expression sLen = null;
            if (array.hasAnnotations()) {
                sLen = Annotations.annotation(array.getAnnotations()[cLen], config.getConcolicIdx());
            }
            if (sLen == null) {
                sLen = Constant.fromConcreteValue(cLen);
            }

            Expression arrayBound = new ComplexExpression(BAND,
                    new ComplexExpression(BVLE, INT_ZERO, sIndex),
                    new ComplexExpression(BVLT, sIndex, sLen));

            trace.addElement(new PathCondition(
                    safe ? arrayBound : new ComplexExpression(BNEG, arrayBound), safe ? FAILURE : SUCCESS, BINARY_SPLIT));
        }
        return safe;
    }

    // branching helpers ...
    @Override
    public void takeBranchPrimitive1(VirtualFrame frame, BytecodeNode bcn, int bci, int opcode, boolean takeBranch, Expression a) {
        if (a == null || a instanceof Constant) {
            return;
        }

        Expression expr = null;
        if (Expression.isBoolean(a)) {
            switch (opcode) {
                case IFEQ:
                    expr = !takeBranch ? a : new ComplexExpression(BNEG, a);
                    break;
                case IFNE:
                    expr = takeBranch ? a : new ComplexExpression(BNEG, a);
                    break;
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw EspressoError.shouldNotReachHere("only defined for IFEQ and IFNE so far");
            }
        } else if (Expression.isCmpExpression(a)) {
            ComplexExpression ce = (ComplexExpression) a;
            OperatorComparator op = null;
            switch (ce.getOperator()) {
                case LCMP:
                    // 0 if x == y; less than 0 if x < y; greater than 0 if x > y
                    switch (opcode) {
                        case IFEQ:
                            op = takeBranch ? OperatorComparator.BVEQ : BVNE;
                            break;
                        case IFNE:
                            op = takeBranch ? BVNE : OperatorComparator.BVEQ;
                            break;
                        case IFLT:
                            op = takeBranch ? BVLT : BVGE;
                            break;
                        case IFGE:
                            op = takeBranch ? BVGE : BVLT;
                            break;
                        case IFGT:
                            op = takeBranch ? BVGT : BVLE;
                            break;
                        case IFLE:
                            op = takeBranch ? BVLE : BVGT;
                            break;
                    }
                    break;
                case FCMPL:
                case FCMPG:
                case DCMPL:
                case DCMPG:
                    // 0 if x == y; less than 0 if x < y; greater than 0 if x > y
                    switch (opcode) {
                        case IFEQ:
                        case IFNE:
                            op = OperatorComparator.FPEQ;
                            break;
                        case IFLT:
                            op = takeBranch ? OperatorComparator.FPLT : OperatorComparator.FPGE;
                            break;
                        case IFGE:
                            op = takeBranch ? OperatorComparator.FPGE : OperatorComparator.FPLT;
                            break;
                        case IFGT:
                            op = takeBranch ? OperatorComparator.FPGT : OperatorComparator.FPLE;
                            break;
                        case IFLE:
                            op = takeBranch ? OperatorComparator.FPLE : OperatorComparator.FPGT;
                            break;
                    }
                    break;
            }
            expr = new ComplexExpression(op, ce.getSubExpressions());
            if (!ce.getOperator().equals(OperatorComparator.LCMP)
                    && ((opcode == IFEQ && !takeBranch) || (opcode == IFNE && takeBranch))) {
                expr = new ComplexExpression(BNEG, expr);
            }
        } else {
            switch (opcode) {
                case IFEQ:
                    expr = new ComplexExpression(BVEQ, a, INT_ZERO);
                    break;
                case IFNE:
                    expr = new ComplexExpression(BVNE, a, INT_ZERO);
                    break;
                case IFLT:
                    expr = new ComplexExpression(BVLT, a, INT_ZERO);
                    break;
                case IFGE:
                    expr = new ComplexExpression(BVGE, a, INT_ZERO);
                    break;
                case IFGT:
                    expr = new ComplexExpression(BVGT, a, INT_ZERO);
                    break;
                case IFLE:
                    expr = new ComplexExpression(BVLE, a, INT_ZERO);
                    break;
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw EspressoError.shouldNotReachHere("expecting IFEQ,IFNE,IFLT,IFGE,IFGT,IFLE");
            }
            expr = takeBranch ? expr : new ComplexExpression(BNEG, expr);
        }
        trace.addElement(new PathCondition(expr, takeBranch ? FAILURE : SUCCESS, BINARY_SPLIT));
    }

    @Override
    public void takeBranchPrimitive2(VirtualFrame frame, BytecodeNode bcn, int bci, int opcode, boolean takeBranch, int c1, int c2, Expression a1, Expression a2) {
        if ((a1 == null || a1 instanceof Constant) && (a2 == null || a2 instanceof Constant)) {
            return;
        }

        Expression expr = null;

        // boolean
        if ((a1 == null || Expression.isBoolean(a1)) && (a2 == null || Expression.isBoolean(a2))) {
            // assume that one is a constant.
            if (a1 != null && a2 != null) {
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere("non-branching bytecode");
            }

            expr = a1 != null ? a1 : a2;
            int c = (a1 != null) ? c2 : c1;

            switch (opcode) {
                case IF_ICMPEQ:
                    expr = (c != 0) ? new ComplexExpression(BNEG, expr) : expr;
                    break;
                case IF_ICMPNE:
                    expr = (c == 0) ? new ComplexExpression(BNEG, expr) : expr;
                    break;
                default:
                    CompilerDirectives.transferToInterpreter();
                    // FIXME: replace EspressoError.shouldNotReachHere calls with stoprecording(...) to make analysis shut down properly
                    throw EspressoError.shouldNotReachHere("non-branching bytecode");
            }

            if (takeBranch) {
                expr = new ComplexExpression(BNEG, expr);
            }

        }
        // numeric
        else {

            if (a1 == null) a1 = Expression.fromConstant(Types.INT, c1);
            if (a2 == null) a2 = Expression.fromConstant(Types.INT, c2);

            switch (opcode) {
                case IF_ICMPEQ:
                    expr = new ComplexExpression(takeBranch ? BVEQ : BVNE, a1, a2);
                    break;
                case IF_ICMPNE:
                    expr = new ComplexExpression(takeBranch ? BVNE : BVEQ, a1, a2);
                    break;
                case IF_ICMPLT:
                    expr = new ComplexExpression(takeBranch ? BVGT : BVLE, a1, a2);
                    break;
                case IF_ICMPGE:
                    expr = new ComplexExpression(takeBranch ? BVLE : BVGT, a1, a2);
                    break;
                case IF_ICMPGT:
                    expr = new ComplexExpression(takeBranch ? BVLT : BVGE, a1, a2);
                    break;
                case IF_ICMPLE:
                    expr = new ComplexExpression(takeBranch ? BVGE : BVLT, a1, a2);
                    break;
                default:
                    CompilerDirectives.transferToInterpreter();
                    // FIXME: replace EspressoError.shouldNotReachHere calls with stoprecording(...) to make analysis shut down properly
                    throw EspressoError.shouldNotReachHere("non-branching bytecode");
            }
        }

        PathCondition pc = new PathCondition(expr, takeBranch ? FAILURE : SUCCESS, BINARY_SPLIT);
        trace.addElement(pc);
    }

    @Override
    public void tableSwitch(VirtualFrame frame, BytecodeNode bcn, int bci, int low, int high, int concIndex, Expression a1) {
        if (a1 == null) {
            return;
        }
        Expression expr;
        int bId;
        if (low <= concIndex && concIndex <= high) {
            Constant idx = Constant.fromConcreteValue(concIndex);
            expr = new ComplexExpression(OperatorComparator.BVEQ, a1, idx);
            bId = concIndex - low;
        } else {
            Constant sLow = Constant.fromConcreteValue(low);
            Constant sHigh = Constant.fromConcreteValue(high);
            expr = new ComplexExpression(BOR, new ComplexExpression(BVLT, a1, sLow), new ComplexExpression(BVGT, a1, sHigh));
            bId = high - low + 1;
        }

        PathCondition pc = new PathCondition(expr, bId, high - low + 2);
        trace.addElement(pc);
    }

    @Override
    public void lookupSwitch(VirtualFrame frame, BytecodeNode bcn, int bci, int[] vals, int key, Expression a1) {
        if (a1 == null) {
            return;
        }

        for (int i = 0; i < vals.length; i++) {
            if (vals[i] == key) {
                Constant idxVal = Constant.fromConcreteValue(key);
                trace.addElement(new PathCondition(
                        new ComplexExpression(OperatorComparator.BVEQ, a1, idxVal), i, vals.length + 1));
                return;
            }
        }

        ComplexExpression[] subExpr = new ComplexExpression[vals.length];
        for (int i = 0; i < vals.length; i++) {
            Constant idxVal = Constant.fromConcreteValue(vals[i]);
            subExpr[i] = new ComplexExpression(BVNE, a1, idxVal);
        }

        trace.addElement(new PathCondition(
                new ComplexExpression(OperatorComparator.BAND, subExpr), vals.length, vals.length + 1));
    }

    //__________________________________________________________________
    // Concolic String library
    //__________________________________________________________________


    @Override
    public Expression stringContains(String self, String other, Expression a1, Expression a2) {
        if(a1 == null) a1 = Constant.fromConcreteValue(self);
        if(a2 == null) a2 = Constant.fromConcreteValue(other);
        return new ComplexExpression(SCONTAINS, a1, a2);
    }

    @Override
    public Expression stringCompareTo(String self, String other, Expression a1, Expression a2) {
        if(a1 == null) a1 = Constant.fromConcreteValue(self);
        if(a2 == null) a2 = Constant.fromConcreteValue(other);
        int res = self.compareTo(other);
        PathCondition pc;
        Expression expr;
        if (res > 0) {
            expr = new ComplexExpression(OperatorComparator.SLT, a2, a1);
            pc = new PathCondition(expr, 0, 3);
        } else if (res < 0) {
            expr = new ComplexExpression(OperatorComparator.SLT, a1, a2);
            pc = new PathCondition(expr, 1, 3);
        } else {
            expr = new ComplexExpression(STRINGEQ, a1, a2);
            pc = new PathCondition(expr, 2, 3);
        }
        trace.addElement(pc);
        return expr;
    }

    @Override
    public Expression stringEquals(String self, String other, Expression a1, Expression a2) {
        if(a1 == null) a1 = Constant.fromConcreteValue(self);
        if(a2 == null) a2 = Constant.fromConcreteValue(other);
        return new ComplexExpression(STRINGEQ, a1, a2);
    }


    @Override
    public Expression charAtPCCheck(String concreteString, int cIndex, Expression sString, Expression sIndex) {
        boolean concolicIndex = sIndex != null;
        if (sIndex == null) {
            sIndex = Constant.fromConcreteValue(cIndex);
        }
        Expression symbolicString = sString != null ? sString: Constant.fromConcreteValue(concreteString);
        Expression intIndexExpr = new ComplexExpression(BV2NAT, sIndex);
        Expression symbolicStrLen = new ComplexExpression(SLENGTH, symbolicString);
        Expression bvSymbolicStrLen = new ComplexExpression(NAT2BV32, symbolicStrLen);

        boolean sat1 = (0 <= cIndex);
        boolean sat2 = (cIndex < concreteString.length());
        if (!sat1 && concolicIndex) {
            Expression indexGreaterEqualsZero =
                    new ComplexExpression(GT, INT_ZERO, sIndex);
            trace.addElement(new PathCondition(indexGreaterEqualsZero, FAILURE, BINARY_SPLIT));
        } else if (sat1 && concolicIndex) {
            Expression indexGreaterEqualsZero =
                    new ComplexExpression(LE, INT_ZERO, sIndex);
            trace.addElement(new PathCondition(indexGreaterEqualsZero, SUCCESS, BINARY_SPLIT));
        }

        if (!sat2) {
            Expression indexLTSymbolicStringLength = new ComplexExpression(GE, sIndex, bvSymbolicStrLen);
            trace.addElement(new PathCondition(indexLTSymbolicStringLength, FAILURE, BINARY_SPLIT));
        } else {
            Expression indexLTSymbolicStringLength = new ComplexExpression(LT, sIndex, bvSymbolicStrLen);
            trace.addElement(new PathCondition(indexLTSymbolicStringLength, SUCCESS, BINARY_SPLIT));
        }
        return null;
    }

    @Override
    public Expression charAt(String self, int index, Expression a1, Expression a2) {
        if (a1 == null) a1 = Constant.fromConcreteValue(self);
        if(a2 == null) a2 = Constant.fromConcreteValue(index);
        return new ComplexExpression(SAT, a1, new ComplexExpression(BV2NAT, a2));
    }

    @Override
    public Expression stringLength(int c, Expression s) {
        return s != null ? new ComplexExpression(NAT2BV32, new ComplexExpression(SLENGTH, s)) : null;
    }

    @Override
    public Expression stringConcat(String self, String other, Expression a1, Expression a2) {
        if(a1 == null) a1 = Constant.fromConcreteValue(self);
        if(a2 == null) a2 = Constant.fromConcreteValue(other);
        return new ComplexExpression(SCONCAT, a1, a2);
    }

    public StaticObject stringToUpper(StaticObject result, StaticObject self, Meta meta) {
        Expression value = makeStringToExpr(self, meta);
        return annotateStringWithExpression(result, new ComplexExpression(STOUPPER, value));
    }

    public StaticObject stringToLower(StaticObject result, StaticObject self, Meta meta) {
        Expression value = makeStringToExpr(self, meta);
        return annotateStringWithExpression(result, new ComplexExpression(STOLOWER, value));
    }

    public StaticObject stringSubstring(StaticObject result, StaticObject self, int cBegin, Annotations sBegin, Meta meta) {
        if (sBegin != null || hasConcolicStringAnnotations(self)) {
            Expression sself = makeStringToExpr(self, meta);
            Expression startIndex = sBegin == null ?
                    Constant.createNatConstant(cBegin) :
                    new ComplexExpression(BV2NAT, Annotations.annotation(sBegin, config.getConcolicIdx()));
            Expression maxLength = new ComplexExpression(SLENGTH, sself);

            Expression indexWithinBound = new ComplexExpression(BAND,
                    new ComplexExpression(LE, NAT_ZERO, startIndex),
                    new ComplexExpression(LT, startIndex, maxLength));

            if (result == null) {
                trace.addElement(new PathCondition(new ComplexExpression(BNEG, indexWithinBound), FAILURE, BINARY_SPLIT));
            } else {
                trace.addElement(new PathCondition(indexWithinBound, SUCCESS, BINARY_SPLIT));
                Expression substring = new ComplexExpression(SSUBSTR, sself, startIndex, maxLength);
                annotateStringWithExpression(result, substring);
            }
        }
        return result;
    }

    public StaticObject stringSubstring(StaticObject result, StaticObject self, int cBegin, Annotations sBegin, int cEnd, Annotations sEnd, Meta meta) {
        if (sEnd != null || sBegin != null || hasConcolicStringAnnotations(self)) {
            Expression sself = makeStringToExpr(self, meta);
            Expression startIndex = sBegin == null ?
                    Constant.createNatConstant(cBegin) :
                    new ComplexExpression(BV2NAT, (Expression) Annotations.annotation(sBegin, config.getConcolicIdx()));
            Expression endIndex = sEnd == null ?
                    Constant.createNatConstant(cEnd) :
                    new ComplexExpression(BV2NAT, (Expression) Annotations.annotation(sEnd, config.getConcolicIdx()));
            Expression maxLength = new ComplexExpression(NATMINUS, endIndex, startIndex);

            Expression indexWithinBound = new ComplexExpression(BAND,
                    new ComplexExpression(LE, NAT_ZERO, startIndex),
                    new ComplexExpression(LT, endIndex, new ComplexExpression(SLENGTH, sself)));
            indexWithinBound = new ComplexExpression(BAND, indexWithinBound,
                    new ComplexExpression(LE, startIndex, endIndex));

            if (result == null) {
                trace.addElement(new PathCondition(new ComplexExpression(BNEG, indexWithinBound), FAILURE, BINARY_SPLIT));
            } else {
                trace.addElement(new PathCondition(indexWithinBound, SUCCESS, BINARY_SPLIT));
                Expression substring = new ComplexExpression(SSUBSTR, sself, startIndex, new ComplexExpression(SLENGTH, sself));
                annotateStringWithExpression(result, substring);
            }
        }
        return result;
    }

    public StaticObject stringBuilderAppend(StaticObject self, StaticObject string, Meta meta) {
        if (!(hasConcolicStringAnnotations(self) || hasConcolicStringAnnotations(string))) {
            return self;
        }
        Expression sself = makeStringToExpr(self, meta);
        Expression sother = makeStringToExpr(string, meta);
        Expression resExpr = new ComplexExpression(SCONCAT, sself, sother);
        if (!hasConcolicStringAnnotations(self)) {
            SPouT.initStringAnnotations(self);
            resExpr = sother;
        }
        self = annotateStringWithExpression(self, resExpr);
        return self;
    }

    public void stringBuilderCharAt(StaticObject self, int index, String val, Meta meta) {
        if(!hasConcolicStringAnnotations(self)){
            return;
        }
        String cSelf = meta.toHostString(self);
        Expression sself = makeStringToExpr(self, meta);
        Expression symbolicIndex = Constant.createNatConstant(index);
        Expression sval = Constant.fromConcreteValue(val);

        // As index might not be symbolic, we cannot do somehting, if it is less than zero. Just check the other bound.
        Expression stringBound = new ComplexExpression(GE, symbolicIndex, new ComplexExpression(SLENGTH, sself));
        if(index >= cSelf.length()) {
            trace.addElement(new PathCondition(stringBound, FAILURE, BINARY_SPLIT));
            return;
        }
        trace.addElement(new PathCondition(new ComplexExpression(BNEG, stringBound), SUCCESS, BINARY_SPLIT));

        Expression left = new ComplexExpression(SSUBSTR, sself, NAT_ZERO, symbolicIndex);
        Expression right = new ComplexExpression(SSUBSTR, sself, new ComplexExpression(NATADD,symbolicIndex, NAT_ONE), new ComplexExpression(SLENGTH, sself));
        Expression res = new ComplexExpression(SCONCAT, new ComplexExpression(SCONCAT, left, sval), right);
        annotateStringWithExpression(self, res);
    }


    private final class ConvRes {
        public Expression expr;
        public boolean fromSymbolic;

        ConvRes(Expression e, boolean b) {
            expr = e;
            fromSymbolic = b;
        }
    }

    @CompilerDirectives.TruffleBoundary
    private ConvRes convertArgToExpression(Object arg) {
        if (arg instanceof StaticObject) {
            StaticObject so = (StaticObject) arg;
            Annotations[] a = so.getAnnotations();
            return a != null && a[a.length - 1] != null && a[a.length - 1].getAnnotations()[config.getConcolicIdx()] != null ?
                    new ConvRes((Expression) a[a.length - 1].getAnnotations()[config.getConcolicIdx()], true) :
                    new ConvRes(Constant.fromConcreteValue(so.toString()), false);
        } else if (arg instanceof AnnotatedValue) {
            Object[] a = ((AnnotatedValue) arg).getAnnotations();
            return a != null && a[config.getConcolicIdx()] != null ?
                    new ConvRes((Expression) a[config.getConcolicIdx()], true) :
                    //FIXME: replaced the fromatted call here. Not sure if this works in general
                    new ConvRes(Constant.fromConcreteValue("" + AnnotatedValue.value(arg)), false);
        } else {
            return new ConvRes(Constant.fromConcreteValue("%s".formatted(arg)), false);
        }
    }

    public void makeConcatWithConstants(StaticObject result, Object[] args, Meta meta) {
        ConvRes cr = convertArgToExpression(args[0]);
        Expression expr = cr.expr;
        boolean anySymbolic = cr.fromSymbolic;
        int i = 1;
        while (args[i] != null) {
            cr = convertArgToExpression(args[i]);
            anySymbolic = anySymbolic ? true : cr.fromSymbolic;
            Expression expr2 = cr.expr;
            expr = new ComplexExpression(SCONCAT, expr, expr2);
            ++i;
        }
        if (anySymbolic) {
            annotateStringWithExpression(result, expr);
        }
    }

    public AnnotatedValue stringBufferLength(AnnotatedValue result, StaticObject self, Meta meta) {
        Expression sLength = makeStringLengthExpr(self, meta);
        result.set(config.getConcolicIdx(), sLength);
        return result;
    }

    public StaticObject stringBuilderToString(StaticObject result, StaticObject self, Meta meta) {
        if (hasConcolicStringAnnotations(self)) {
            result = SPouT.initStringAnnotations(result);
            annotateStringWithExpression(result, makeStringToExpr(self, meta));
        }
        return result;
    }

    public void stringBuilderInsert(
            StaticObject self,
            int offset,
            StaticObject toInsert,
            Meta meta) {
        Expression symbolicOffset = Constant.createNatConstant(offset);
        if (hasConcolicStringAnnotations(self) || hasConcolicStringAnnotations(toInsert)) {
            Method m = self.getKlass().lookupMethod(meta.getNames().getOrCreate("toString"), Symbol.Signature.java_lang_String);
            StaticObject stringValue = (StaticObject) m.invokeDirect(self);
            String concreteSelf = meta.toHostString(stringValue);

            Expression symbolicSelf = makeStringToExpr(self, meta);
            Expression symbolicToInsert = makeStringToExpr(toInsert, meta);

            Expression symbolicSelfLength = new ComplexExpression(SLENGTH, makeStringToExpr(self, meta));
            Expression symbolicToInsertLength = new ComplexExpression(SLENGTH, makeStringToExpr(toInsert, meta));

            boolean validOffset = 0 <= offset && offset <= concreteSelf.length();
            if (!validOffset) {
                Expression lengthCheck =
                        new ComplexExpression(
                                BNEG,
                                new ComplexExpression(
                                        BAND,
                                        new ComplexExpression(LE, NAT_ZERO, symbolicOffset),
                                        new ComplexExpression(LE, symbolicOffset, symbolicSelfLength)));
                PathCondition pc = new PathCondition(lengthCheck, 1, 2);
                trace.addElement(pc);
                return;
            } else {
                Expression lengthCheck =
                        new ComplexExpression(
                                BAND,
                                new ComplexExpression(LE, NAT_ZERO, symbolicOffset),
                                new ComplexExpression(LE, symbolicOffset, symbolicSelfLength));
                PathCondition pc = new PathCondition(lengthCheck, 0, 2);
                trace.addElement(pc);
            }
            Expression resultingSymbolicValue;
            if (concreteSelf.isEmpty()) {
                resultingSymbolicValue = symbolicToInsert;
            } else {
                if (offset != 0) {
                    Expression symbolicLeft =
                            new ComplexExpression(SSUBSTR, symbolicSelf, NAT_ZERO, symbolicOffset);
                    Expression symbolicRight =
                            new ComplexExpression(
                                    SSUBSTR,
                                    symbolicSelf,
                                    new ComplexExpression(NATADD, symbolicOffset, symbolicToInsertLength),
                                    symbolicSelfLength);
                    resultingSymbolicValue =
                            new ComplexExpression(
                                    SCONCAT,
                                    symbolicLeft,
                                    new ComplexExpression(SCONCAT, symbolicToInsert, symbolicRight));
                } else {
                    resultingSymbolicValue =
                            new ComplexExpression(
                                    SCONCAT,
                                    symbolicToInsert,
                                    symbolicSelf);
                }
            }
            annotateStringWithExpression(self, resultingSymbolicValue);
        }
    }

    private Object makeChar(char cRes, Annotations sChar, OperatorComparator oc){
        if(sChar != null && Annotations.annotation(sChar, config.getConcolicIdx()) != null){
            Object[] annotations = sChar.getAnnotations();
            Annotations newAnnotation = Annotations.create(annotations);
            newAnnotation.set(config.getConcolicIdx(),
                    new ComplexExpression(oc, (Expression) Annotations.annotation(sChar, config.getConcolicIdx())));
            return new AnnotatedValue(cRes, newAnnotation);
        }
        return cRes;
    }

    public Object characterToUpperCase(char cRes, Annotations sChar) {
        return makeChar(cRes, sChar, STOUPPER);
    }

    public Object characterToLowerCase(char cRes, Annotations sChar) {
        return makeChar(cRes, sChar, STOLOWER);
    }

    public Object characterIsDefined(boolean res, char cChar,  Annotations sChar, Meta meta){
        int CODEPOINT_BOUND = 1000;
        if (sChar != null) {
            Expression cAsInt = new ComplexExpression(NAT2BV32, new ComplexExpression(STOCODE, Annotations.annotation(sChar, config.getConcolicIdx())));
            Expression codepointLT5000 =
                    new ComplexExpression(BVLE, cAsInt, Constant.fromConcreteValue(CODEPOINT_BOUND));
            if (cChar > CODEPOINT_BOUND) {
                PathCondition pc =
                        new PathCondition(
                                new ComplexExpression(BVGT, cAsInt, Constant.fromConcreteValue(CODEPOINT_BOUND)),
                                FAILURE,
                                BINARY_SPLIT);
                trace.addElement(pc);
                SPouT.stopRecording("Analysis of defined code points over 1000 is not supported.", meta);
            }
            PathCondition pc = new PathCondition(codepointLT5000, SUCCESS, BINARY_SPLIT);
            trace.addElement(pc);
            Expression undefined = Constant.fromConcreteValue(false);
            for (int i = 0; i <= CODEPOINT_BOUND; i++) {
                if (!Character.isDefined(i)) {
                    undefined =
                            new ComplexExpression(
                                    BOR,
                                    undefined,
                                    new ComplexExpression(BVEQ, cAsInt, Constant.fromConcreteValue(i)));
                }
            }
            if (res) {
                undefined = new ComplexExpression(BNEG, undefined);
            }
            Annotations newAnn = Annotations.create(sChar.getAnnotations());
            newAnn.set(config.getConcolicIdx(), undefined);
            return new AnnotatedValue(res, newAnn);
        }
        return res;
    }

    public Object regionMatches(StaticObject self, StaticObject other, boolean ignore, int ctoffset, int cooffset, int clen, Meta meta) {
        String cSelf = meta.toHostString(self);
        String cOther = meta.toHostString(other);
        boolean isSelfSymbolic = self.hasAnnotations()
                && self.getAnnotations()[self.getAnnotations().length - 1].getAnnotations()[config.getConcolicIdx()] != null;
        boolean isOtherSymbolic = other.hasAnnotations()
                && other.getAnnotations()[self.getAnnotations().length - 1].getAnnotations()[config.getConcolicIdx()] != null;
        boolean boundsCheck =
                evaluateBoundRegionMatches(
                        cooffset,
                        ctoffset,
                        clen,
                        cOther.length(),
                        cSelf.length(),
                        makeStringLengthExpr(other, meta),
                        makeStringLengthExpr(self, meta));
        if (!boundsCheck) {
            return false;
        }
        boolean cRes = cSelf.regionMatches(ignore, ctoffset, cOther, cooffset, clen);
        if (isSelfSymbolic && !isOtherSymbolic) {
            return regionMatchesSymbolic(
                    makeStringToExpr(self, meta),
                    Constant.fromConcreteValue(cOther),
                    ctoffset,
                    cooffset,
                    clen,
                    ignore,
                    cRes);
        } else if (!isSelfSymbolic && isOtherSymbolic) {
            return regionMatchesSymbolic(
                    makeStringToExpr(other, meta),
                    Constant.fromConcreteValue(cSelf),
                    cooffset,
                    ctoffset,
                    clen,
                    ignore,
                    cRes);
        } else {
            return regionMatchesSymbolic(
                    makeStringToExpr(self, meta),
                    makeStringToExpr(other, meta),
                    ctoffset,
                    cooffset,
                    clen,
                    ignore,
                    cRes);
        }
    }

    private Object regionMatchesSymbolic(
            Expression symbolicSelf,
            Expression symbolicOther,
            int ctoffset,
            int cooffset,
            int clen,
            boolean ignore,
            boolean cRes) {
        Expression symbolicSubSelf =
                new ComplexExpression(
                        SSUBSTR,
                        symbolicSelf,
                        Constant.createNatConstant(ctoffset),
                        Constant.createNatConstant(clen));
        Expression symbolicSubOther =
                new ComplexExpression(
                        SSUBSTR,
                        symbolicOther,
                        Constant.createNatConstant(cooffset),
                        Constant.createNatConstant(clen));
        if (ignore) {
            symbolicSubSelf = new ComplexExpression(STOLOWER, symbolicSubSelf);
            symbolicSubOther = new ComplexExpression(STOLOWER, symbolicSubOther);
        }
        PathCondition pc;
        if (cRes) {
            pc =
                    new PathCondition(
                            new ComplexExpression(STRINGEQ, symbolicSubSelf, symbolicSubOther), 0, 2);
        } else {
            pc =
                    new PathCondition(
                            new ComplexExpression(
                                    BNEG, new ComplexExpression(STRINGEQ, symbolicSubSelf, symbolicSubOther)),
                            1,
                            2);
        }
        trace.addElement(pc);
        return cRes;
    }

    private boolean evaluateBoundRegionMatches(
            int ooffset,
            int toffset,
            int len,
            int olen,
            int tlen,
            Expression otherSymLen,
            Expression tSymLen) {
        boolean upperOBound = (ooffset + len) > olen;
        Expression upperOBoundE =
                new ComplexExpression(LT, otherSymLen, Constant.fromConcreteValue(ooffset + len));

        boolean lowerOBound = (ooffset + len) < 0;

        boolean lowerTBound = (toffset + len) < 0;

        boolean upperTBound = (toffset + len) > tlen;
        Expression upperTBoundE =
                new ComplexExpression(LT, tSymLen, Constant.fromConcreteValue(toffset + len));
        Expression check0 = new ComplexExpression(BAND, upperOBoundE, upperTBoundE);
        Expression check1 =
                new ComplexExpression(BAND, upperOBoundE, new ComplexExpression(BNEG, upperTBoundE));
        Expression check2 =
                new ComplexExpression(BAND, new ComplexExpression(BNEG, upperOBoundE), upperTBoundE);
        Expression check3 =
                new ComplexExpression(
                        BAND,
                        new ComplexExpression(BNEG, upperOBoundE),
                        new ComplexExpression(BNEG, upperTBoundE));
        Expression effective = null;
        int branchIdx = -1;
        if (upperOBound) {
            if (upperTBound) {
                effective = check0;
                branchIdx = 0;

            } else {
                effective = check1;
                branchIdx = 1;
            }
        } else {
            if (upperTBound) {
                effective = check2;
                branchIdx = 2;
            } else {
                effective = check3;
                branchIdx = 3;
            }
        }
        PathCondition pc = new PathCondition(effective, branchIdx, 4);
        trace.addElement(pc);
        return !(upperOBound || lowerOBound || lowerTBound || upperTBound);
    }

    private Expression makeStringToExpr(StaticObject self, Meta meta) {
        if (self.hasAnnotations()) {
            Annotations[] fields = self.getAnnotations();
            return (Expression) Annotations.annotation(fields[fields.length - 1], config.getConcolicIdx());
        }
        return Constant.fromConcreteValue(meta.toHostString(self));
    }

    private Expression makeStringLengthExpr(StaticObject self, Meta meta) {
        return new ComplexExpression(NAT2BV32, new ComplexExpression(SLENGTH, makeStringToExpr(self, meta)));
    }

    private StaticObject annotateStringWithExpression(StaticObject self, Expression value) {
        Annotations[] stringAnnotation = self.getAnnotations();
        if (stringAnnotation == null) {
            SPouT.initStringAnnotations(self);
            stringAnnotation = self.getAnnotations();
        }
        stringAnnotation[stringAnnotation.length - 1].set(config.getConcolicIdx(),
                value);
        self.setAnnotations(stringAnnotation);
        return self;
    }

    public boolean hasConcolicStringAnnotations(StaticObject self) {
        //return stringAnnotation != null && stringAnnotation[stringAnnotation.length - 1].getAnnotations()[config.getConcolicIdx()] != null;
        Annotations[] stringAnnotation = self.getAnnotations();
        if (stringAnnotation != null) {
            Annotations annotations = stringAnnotation[stringAnnotation.length - 1];
            if (annotations != null) {
                return annotations.getAnnotations()[config.getConcolicIdx()] != null;
            }
            //FIXME: it can happen that no length annotation is present. Not sure if this is expected?
        }
        return false;
    }

    public void addNotZeroToTrace(Annotations a, Expression zero) {
        trace.addElement(
                new PathCondition(
                        new ComplexExpression(OperatorComparator.BVNE, (Expression) a.getAnnotations()[config.getConcolicIdx()], zero), 0, 2));
    }

    public void addZeroToTrace(Annotations a, Expression zero) {
        trace.addElement(
                new PathCondition(
                        new ComplexExpression(OperatorComparator.BVEQ, (Expression) a.getAnnotations()[config.getConcolicIdx()], zero), 1, 2));
    }

}