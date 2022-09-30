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
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.smt.*;
import tools.aqua.spout.*;

import static com.oracle.truffle.espresso.bytecode.Bytecodes.*;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPLE;
import static tools.aqua.smt.Constant.INT_ZERO;
import static tools.aqua.smt.Constant.NAT_ZERO;
import static tools.aqua.smt.OperatorComparator.*;

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

    public StaticObject nextSymbolicString(Meta meta) {
        Config.SymbolicStringValue ssv = config.nextSymbolicString();
        StaticObject guestString = meta.toGuestString(ssv.concrete);
        int lengthAnnotations = ((ObjectKlass) guestString.getKlass()).getFieldTable().length + 1;
        Annotations[] annotations = new Annotations[lengthAnnotations];
        Object[] stringDescription = {ssv.symbolic, null};
        annotations[annotations.length - 1] = Annotations.create(stringDescription);
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
    public Expression isub(int c1, int c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.ISUB, Types.INT, c2, c1, a2, a1);
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
        return binarySymbolicOp(OperatorComparator.LCMP, Types.LONG, c1, c2, a1, a2);
    }

    @Override
    public Expression irem(int c1, int c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.IREM, Types.INT, c1, c2, a1, a2);
    }

    @Override
    public Expression ishl(int c1, int c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.ISHL, Types.INT, c1, c2, a1, a2);
    }

    @Override
    public Expression ishr(int c1, int c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.ISHR, Types.INT, c1, c2, a1, a2);
    }

    @Override
    public Expression iushr(int c1, int c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.IUSHR, Types.INT, c1, c2, a1, a2);
    }

    @Override
    public Expression idiv(int c1, int c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.IDIV, Types.INT, c1, c2, a1, a2);
    }

    @Override
    public Expression imul(int c1, int c2, Expression a1, Expression a2) {
        return binarySymbolicOp(OperatorComparator.IMUL, Types.INT, c1, c2, a1, a2);
    }

   @Override
    public Expression ineg(int c1, Expression a1) {
        Expression sym = null;
        if (a1 != null) {
            Constant negation = Constant.fromConcreteValue(c1);
            sym = new ComplexExpression(OperatorComparator.INEG, a1, negation);
        }
        return sym;
    }

    @Override
    public Expression i2l(long c1, Expression a1) {
        Expression sym = null;
        if ( a1 != null){
            sym = new ComplexExpression(OperatorComparator.I2L, a1);
        }
        return sym;
    }

    @Override
    public Expression i2f(float c1, Expression a1) {
        Expression sym = null;
        if(a1 != null){
            sym = new ComplexExpression(OperatorComparator.I2F, a1);
        }
        return sym;
    }

    @Override
    public Expression i2d(double c1, Expression a1) {
        Expression sym = null;
        if(a1 != null){
            sym = new ComplexExpression(OperatorComparator.I2F, a1);
        }
        return sym;
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
                    safe ? arrayBound : new ComplexExpression(BNEG, arrayBound), safe ? 1 : 0, 2));
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
        trace.addElement(new PathCondition(expr, takeBranch ? 1 : 0, 2));
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

        PathCondition pc = new PathCondition(expr, takeBranch ? 1 : 0, 2);
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
    public AnnotatedValue stringContains(AnnotatedValue concreteRes, StaticObject self, StaticObject s, Meta meta) {
        Expression symbolicSelf = makeStringToExpr(self, meta);
        Expression symbolicOther = makeStringToExpr(s, meta);
        concreteRes.set(config.getConcolicIdx(), new ComplexExpression(SCONTAINS, symbolicSelf, symbolicOther));
        return concreteRes;
    }

    public AnnotatedValue stringCompareTo(AnnotatedValue av, StaticObject self, StaticObject other, Meta meta) {
        Expression symbolicSelf = makeStringToExpr(self, meta);
        Expression symbolicOther = makeStringToExpr(other, meta);
        PathCondition pc;
        if (av.<Integer>getValue() > 0) {
            pc = new PathCondition(new ComplexExpression(OperatorComparator.SLT, symbolicOther, symbolicSelf), 0, 3);
        } else if (av.<Integer>getValue() < 0) {
            pc = new PathCondition(new ComplexExpression(OperatorComparator.SLT, symbolicSelf, symbolicOther), 1, 3);
        } else {
            pc = new PathCondition(new ComplexExpression(
                    STRINGEQ, symbolicSelf, symbolicOther), 2, 3);
        }
        trace.addElement(pc);
        return av;

    }

    public AnnotatedValue stringEqual(AnnotatedValue av, StaticObject self, StaticObject other, Meta meta) {
        Expression sself = makeStringToExpr(self, meta);
        Expression sother = makeStringToExpr(other, meta);
        Expression symbolicResult = new ComplexExpression(STRINGEQ, sself, sother);
        av.set(config.getConcolicIdx(), symbolicResult);
        return av;
    }

    //FIXME
    public void charAtPCCheck(StaticObject self, Object index, Meta meta) {
        String concreteString = meta.toHostString(self);
        int concreteIndex = 0;
        Expression indexExpr;
        boolean concolicIndex = false;
        if (index instanceof AnnotatedValue) {
            AnnotatedValue a = (AnnotatedValue) index;
            concreteIndex = a.<Integer>getValue();
            indexExpr = (Expression) a.getAnnotations()[config.getConcolicIdx()];
            concolicIndex = true;
        } else {
            concreteIndex = (int) index;
            indexExpr = Constant.fromConcreteValue(concreteIndex);
        }
        Expression symbolicString = makeStringToExpr(self, meta);
        Expression intIndexExpr = new ComplexExpression(BV2NAT, indexExpr);
        Expression symbolicStrLen = new ComplexExpression(SLENGTH, symbolicString);
        Expression bvSymbolicStrLen = new ComplexExpression(NAT2BV32, symbolicStrLen);

        boolean sat1 = (0 <= concreteIndex);
        boolean sat2 = (concreteIndex < concreteString.length());
        if (!sat1 && concolicIndex) {
            Expression indexGreaterEqualsZero =
                    new ComplexExpression(GT, INT_ZERO, indexExpr);
            trace.addElement(new PathCondition(indexGreaterEqualsZero, 1, 2));
        } else if (sat1 && concolicIndex) {
            Expression indexGreaterEqualsZero =
                    new ComplexExpression(LE, INT_ZERO, indexExpr);
            trace.addElement(new PathCondition(indexGreaterEqualsZero, 0, 2));
        }

        if (!sat2) {
            Expression indexLTSymbolicStringLength = new ComplexExpression(GE, indexExpr, bvSymbolicStrLen);
            trace.addElement(new PathCondition(indexLTSymbolicStringLength, 1, 2));
        } else {
            Expression indexLTSymbolicStringLength = new ComplexExpression(LT, indexExpr, bvSymbolicStrLen);
            trace.addElement(new PathCondition(indexLTSymbolicStringLength, 0, 2));
        }
    }

    public AnnotatedValue charAtContent(AnnotatedValue av, StaticObject self, Object index, Meta meta) {
        String concreteString = meta.toHostString(self);
        int concreteIndex = 0;
        Expression indexExpr;
        boolean concolicIndex = false;
        if (index instanceof AnnotatedValue) {
            AnnotatedValue a = (AnnotatedValue) index;
            concreteIndex = a.<Integer>getValue();
            indexExpr = (Expression) a.getAnnotations()[config.getConcolicIdx()];
            concolicIndex = true;
        } else {
            concreteIndex = (int) index;
            indexExpr = Constant.fromConcreteValue(concreteIndex);
        }
        Expression symbolicString = makeStringToExpr(self, meta);
        Expression charAtExpr = new ComplexExpression(SAT, symbolicString, indexExpr);
        av.set(config.getConcolicIdx(), charAtExpr);
        return av;
    }


    public Object stringLength(AnnotatedValue annotatedValue, StaticObject self, Meta meta) {
        Expression expr = makeStringToExpr(self, meta);
        annotatedValue.set(config.getConcolicIdx(), new ComplexExpression(NAT2BV32, new ComplexExpression(SLENGTH, expr)));
        return annotatedValue;
    }

    public StaticObject stringConcat(StaticObject result, StaticObject self, StaticObject other, Meta meta) {
        Expression sself = makeStringToExpr(self, meta);
        Expression sother = makeStringToExpr(other, meta);
        annotateStringWithExpression(result, new ComplexExpression(SCONCAT, sself, sother));
        return result;
    }

    public StaticObject stringToUpper(StaticObject result, StaticObject self, Meta meta) {
        Expression value = makeStringToExpr(self, meta);
        return annotateStringWithExpression(result, new ComplexExpression(STOUPPER, value));
    }

    public StaticObject stringToLower(StaticObject result, StaticObject self, Meta meta) {
        Expression value = makeStringToExpr(self, meta);
        return annotateStringWithExpression(result, new ComplexExpression(STOLOWER, value));
    }

    public StaticObject stringBuilderAppend(StaticObject self, StaticObject string, Meta meta) {
        if (!self.hasAnnotations() && !string.hasAnnotations()) {
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
                                        new ComplexExpression(GE, NAT_ZERO, symbolicOffset),
                                        new ComplexExpression(LE, symbolicOffset, symbolicSelfLength)));
                PathCondition pc = new PathCondition(lengthCheck, 1, 2);
                trace.addElement(pc);
                return;
            } else {
                Expression lengthCheck =
                        new ComplexExpression(
                                BAND,
                                new ComplexExpression(GE, NAT_ZERO, symbolicOffset),
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


    public Object regionMatches(StaticObject self, StaticObject other, boolean ignore, int ctoffset, int cooffset, int clen, Meta meta) {
        String cSelf = meta.toHostString(self);
        String cOther = meta.toHostString(self);
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
            return (Expression) fields[fields.length - 1].getAnnotations()[config.getConcolicIdx()];
        }
        return Constant.fromConcreteValue(meta.toHostString(self));
    }

    private Expression makeStringLengthExpr(StaticObject self, Meta meta) {
        return new ComplexExpression(NAT2BV32, new ComplexExpression(SLENGTH, makeStringToExpr(self, meta)));
    }

    private StaticObject annotateStringWithExpression(StaticObject self, Expression value) {
        Annotations[] stringAnnotation = self.getAnnotations();
        stringAnnotation[stringAnnotation.length - 1].set(config.getConcolicIdx(),
                value);
        self.setAnnotations(stringAnnotation);
        return self;
    }

    public boolean hasConcolicStringAnnotations(StaticObject self) {
        Annotations[] stringAnnotation = self.getAnnotations();
        return stringAnnotation != null && stringAnnotation[stringAnnotation.length - 1].getAnnotations()[config.getConcolicIdx()] != null;
    }

    public void addNotZeroToTrace(Annotations a){
        trace.addElement(
                new PathCondition(
                        new ComplexExpression(OperatorComparator.BVNE, (Expression) a.getAnnotations()[config.getConcolicIdx()], INT_ZERO), 0, 2));
    }

    public void addZeroToTrace(Annotations a){
        trace.addElement(
                new PathCondition(
                        new ComplexExpression(OperatorComparator.BVEQ, (Expression) a.getAnnotations()[config.getConcolicIdx()], INT_ZERO), 1, 2));
    }
}