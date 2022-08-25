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
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.smt.*;
import tools.aqua.spout.*;

import static com.oracle.truffle.espresso.bytecode.Bytecodes.*;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPLE;
import static tools.aqua.smt.Constant.INT_ZERO;
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
                sLen = Annotations.annotation( array.getAnnotations()[cLen], config.getConcolicIdx());
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
                case IFEQ: expr = !takeBranch ? a : new ComplexExpression(BNEG, a); break;
                case IFNE: expr =  takeBranch ? a : new ComplexExpression(BNEG, a); break;
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw EspressoError.shouldNotReachHere("only defined for IFEQ and IFNE so far");
            }
        }
        else if (Expression.isCmpExpression(a)) {
            ComplexExpression ce = (ComplexExpression) a;
            OperatorComparator op = null;
            switch (ce.getOperator()) {
                case LCMP:
                    // 0 if x == y; less than 0 if x < y; greater than 0 if x > y
                    switch (opcode) {
                        case IFEQ: op = takeBranch ? OperatorComparator.BVEQ : BVNE; break;
                        case IFNE: op = takeBranch ? BVNE : OperatorComparator.BVEQ; break;
                        case IFLT: op = takeBranch ? BVLT : BVGE; break;
                        case IFGE: op = takeBranch ? BVGE : BVLT; break;
                        case IFGT: op = takeBranch ? BVGT : BVLE; break;
                        case IFLE: op = takeBranch ? BVLE : BVGT; break;
                    }
                    break;
                case FCMPL:
                case FCMPG:
                case DCMPL:
                case DCMPG:
                    // 0 if x == y; less than 0 if x < y; greater than 0 if x > y
                    switch (opcode) {
                        case IFEQ:
                        case IFNE: op = OperatorComparator.FPEQ; break;
                        case IFLT: op = takeBranch ? OperatorComparator.FPLT : OperatorComparator.FPGE; break;
                        case IFGE: op = takeBranch ? OperatorComparator.FPGE : OperatorComparator.FPLT; break;
                        case IFGT: op = takeBranch ? OperatorComparator.FPGT : OperatorComparator.FPLE; break;
                        case IFLE: op = takeBranch ? OperatorComparator.FPLE : OperatorComparator.FPGT; break;
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
                case IFEQ: expr = new ComplexExpression(BVEQ, a, INT_ZERO); break;
                case IFNE: expr = new ComplexExpression(BVNE, a, INT_ZERO); break;
                case IFLT: expr = new ComplexExpression(BVLT, a, INT_ZERO); break;
                case IFGE: expr = new ComplexExpression(BVGE, a, INT_ZERO); break;
                case IFGT: expr = new ComplexExpression(BVGT, a, INT_ZERO); break;
                case IFLE: expr = new ComplexExpression(BVLE, a, INT_ZERO); break;
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
                case IF_ICMPEQ: expr = (c != 0) ? new ComplexExpression(BNEG, expr) : expr; break;
                case IF_ICMPNE: expr = (c == 0) ? new ComplexExpression(BNEG, expr) : expr; break;
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
                case IF_ICMPEQ: expr = new ComplexExpression(takeBranch ? BVEQ : BVNE, a1, a2); break;
                case IF_ICMPNE: expr = new ComplexExpression(takeBranch ? BVNE : BVEQ, a1, a2); break;
                case IF_ICMPLT: expr = new ComplexExpression(takeBranch ? BVGT : BVLE, a1, a2); break;
                case IF_ICMPGE: expr = new ComplexExpression(takeBranch ? BVLE : BVGT, a1, a2); break;
                case IF_ICMPGT: expr = new ComplexExpression(takeBranch ? BVLT : BVGE, a1, a2); break;
                case IF_ICMPLE: expr = new ComplexExpression(takeBranch ? BVGE : BVLT, a1, a2); break;
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

}