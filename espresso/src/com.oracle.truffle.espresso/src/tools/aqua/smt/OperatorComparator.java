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

package tools.aqua.smt;

import java.util.EnumSet;

public enum OperatorComparator {
    // Integer theory
    LE,
    LT,
    GT,
    GE,
    // int
    IADD,
    ISUB,
    IMUL,
    IDIV,
    IREM,
    INEG,
    ISHR,
    ISHL,
    IUSHR,
    IAND,
    IOR,
    IXOR,
    // long
    LADD,
    LSUB,
    LMUL,
    LDIV,
    LNEG,
    LREM,
    LSHR,
    LSHL,
    LUSHR,
    LAND,
    LOR,
    LXOR,
    // float
    FADD,
    FSUB,
    FMUL,
    FDIV,
    FREM,
    FNEG,
    // double
    DADD,
    DSUB,
    DMUL,
    DDIV,
    DREM,
    DNEG,
    // Comp
    BVGT,
    BVLT,
    BVGE,
    BVLE,
    BVEQ,
    BVNE,
    // Fp. Comp
    FPGT,
    FPLT,
    FPGE,
    FPLE,
    FPEQ,
    // Boolean
    BNEG,
    BAND,
    BOR,
    BXOR,
    BEQUIV,
    BIMPLIES,
    // Casting
    I2B,
    I2S,
    I2C,
    I2L,
    I2F,
    I2D,
    L2I,
    L2F,
    L2D,
    F2I,
    F2L,
    F2D,
    D2I,
    D2L,
    D2F,
    // upcast (not in JVM -- for constraint solving)
    B2I,
    S2I,
    C2I,
    // casting helpers
    FP_ISNAN,
    FP_ISNEG,
    FP_MIN,
    FP_MAX,
    F2I_RTZ,
    F2L_RTZ,
    D2I_RTZ,
    D2L_RTZ,
    I2F_RTZ,
    L2F_RTZ,
    I2D_RTZ,
    L2D_RTZ,
    ITE,
    // CMP (In JVM -- eliminated before export)
    LCMP,
    FCMPL,
    FCMPG,
    DCMPL,
    DCMPG,
    // String
    STRINGEQ,
    STRINGNE,
    SCONCAT,
    SCONTAINS,
    SSUBSTR,
    SAT,
    STOSTR,
    SREPLACE,
    SREPLACEALL,
    STOLOWER,
    STOUPPER,
    SLENGTH,
    SINDEXOF,
    STOINT,
    STOCODE,
    SFROMCODE,
    NAT2BV32,
    BV2NAT,
    NATADD,
    NATMINUS,
    SLT,
    NAT2BV16,
    MATHSIN,
    MATHCOS,
    MATHTAN,
    MATHATAN,
    MATHEXP,
    MATHSQRT,
    MATHARCSIN,
    MATHARCCOS;




    @Override
    public String toString() {
        //TODO: someone needs to check these!
        switch (this) {
            // int and long
            case IADD:
            case LADD:
                return "bvadd";
            case ISUB:
            case LSUB:
                return "bvsub";
            case IMUL:
            case LMUL:
                return "bvmul";
            case IDIV:
            case LDIV:
                return "bvsdiv";
            case IREM:
            case LREM:
                return "bvsrem";
            case ISHL:
            case LSHL:
                return "bvshl";
            case ISHR:
            case LSHR:
                return "bvashr";
            case IUSHR:
            case LUSHR:
                return "bvlshr";
            case IAND:
            case LAND:
                return "bvand";
            case IOR:
            case LOR:
                return "bvor";
            case IXOR:
            case LXOR:
                return "bvxor";
            case INEG:
            case LNEG:
                return "-";

            // float and double
            case FADD:
            case DADD:
                return "fp.add (RNE RoundingMode)";
            case FSUB:
            case DSUB:
                return "fp.sub (RNE RoundingMode)";
            case FMUL:
            case DMUL:
                return "fp.mul (RNE RoundingMode)";
            case FDIV:
            case DDIV:
                return "fp.div (RNE RoundingMode)";
            case FREM:
            case DREM:
                return "fp.rem (RNE RoundingMode)";
            case FNEG:
            case DNEG:
                return "fp.neg";

            case BNEG:
                return "not";
            case BAND:
                return "and";
            case BOR:
                return "or";
            case BXOR:
                return "xor";
            case FPEQ:
                return "fp.eq";
            case FPLT:
                return "fp.lt";
            case FPLE:
                return "fp.leq";
            case FPGT:
                return "fp.gt";
            case FPGE:
                return "fp.geq";
            case BEQUIV:
            case BVEQ:
            case STRINGEQ:
                return "=";
            case BVNE:
            case STRINGNE:
                return "!=";
            case BVGT:
                return "bvsgt";
            case BVGE:
                return "bvsge";
            case BVLT:
                return "bvslt";
            case BVLE:
                return "bvsle";
            case I2B:
                return "(_ extract 7 0)";
            case I2C:
            case I2S:
                return "(_ extract 15 0)";
            case I2L:
                return "(_ sign_extend 32)";
            case I2F:
            case L2F:
                return "(_ to_fp 8 24) (RNE RoundingMode)";
            case I2D:
            case L2D:
                return "(_ to_fp 11 53) (RNE RoundingMode)";
            case L2I:
                return "(_ extract 31 0)";
            case F2I:
            case D2I:
                return "(_ fp.to_sbv 32) (RNE RoundingMode)";
            case F2L:
            case D2L:
                return "(_ fp.to_sbv 64) (RNE RoundingMode)";
            case F2D:
                return "(_ to_fp 11 53) (RNE RoundingMode)";
            case D2F:
                return "(_ to_fp 8 24) (RNE RoundingMode)";

            case FP_ISNAN:
                return "fp.isNaN";
            case FP_ISNEG:
                return "fp.isNegative";
            case FP_MIN:
                return "fp.min";
            case FP_MAX:
                return "fp.max";
            case F2I_RTZ:
            case D2I_RTZ:
                return "(_ fp.to_sbv 32) (RTZ RoundingMode)";
            case F2L_RTZ:
            case D2L_RTZ:
                return "(_ fp.to_sbv 64) (RTZ RoundingMode)";
            case I2F_RTZ:
            case L2F_RTZ:
                return "(_ to_fp 8 24) (RTZ RoundingMode)";
            case I2D_RTZ:
            case L2D_RTZ:
                return "(_ to_fp 11 53) (RTZ RoundingMode)";
            case ITE:
                return "ite";
            case B2I:
                return "(_ sign_extend 24)";
            case S2I:
                return "(_ sign_extend 16)";
            case C2I:
                return "(_ zero_extend 16)";
            case SLENGTH:
                return "str.len";
            case SINDEXOF:
                return "str.indexof";
            case STOINT:
                return "str.to.int";
            case STOLOWER:
                return "str.lower";
            case STOUPPER:
                return "str.upper";
            case SAT:
                return "str.at";
            case SCONCAT:
                return "str.++";
            case SCONTAINS:
                return "str.contains";
            case SLT:
                return "str.<";
            case STOCODE:
                return "str.to_code";
            case SFROMCODE:
                return "str.from_code";
            case SSUBSTR:
                return "str.substr";
            case NAT2BV32:
                return "(_ int2bv 32)";
            case NAT2BV16:
                return "(_ int2bv 16)";
            case BV2NAT:
                return "bv2int";
            case LT:
                return "<";
            case GT:
                return ">";
            case LE:
                return "<=";
            case GE:
                return ">=";
            case NATADD:
                return "+";
            case NATMINUS:
                return "-";
            case MATHTAN:
                return "TAN";
            case MATHCOS:
                return "COS";
            case MATHEXP:
                return "EXP";
            case MATHSIN:
                return "SIN";
            case MATHATAN:
                return "ATAN";
            case MATHSQRT:
                return "SQRT";
            case MATHARCCOS:
                return "ARCCOS";
            case MATHARCSIN:
                return "ARCSIN";

            default:
                return super.toString();
        }
    }

    // RTZ RoundingMode

    static EnumSet<OperatorComparator> stringOps = null;
    static EnumSet<OperatorComparator> boolOps = null;
    static EnumSet<OperatorComparator> cmpOps = null;

    public static void initialize() {
        // FIXME: complete set
        stringOps = EnumSet.of(SSUBSTR, SREPLACE, SREPLACEALL, STOLOWER, STOUPPER, SCONCAT, SAT);
        boolOps = EnumSet.of(BVEQ, STRINGEQ, BVNE, STRINGNE, BVLT, BVLE, BVGT, BVGE, BNEG, BAND, BOR, BXOR, BEQUIV, BIMPLIES, SCONTAINS);
        cmpOps = EnumSet.of(LCMP, FCMPL, FCMPG, DCMPL, DCMPG);
    }

    public boolean isBoolean() {
        return boolOps.contains(this);
    }

    public boolean isCmp() {
        return cmpOps.contains(this);
    }

    public boolean isString() {
        return stringOps.contains(this);
    }
}
