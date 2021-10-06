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

import java.util.EnumSet;

public enum OperatorComparator {
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
    // solved through bitmasks
    //I2B,
    //I2C,
    //I2S,
    // upcast (not in JVM -- for constraint solving)
    B2I,
    S2I,
    C2I,
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
    SSUBSTR,
    SAT,
    STOSTR,
    SREPLACE,
    SREPLACEALL,
    STOLOWER,
    STOUPPER,
    SLENGTH,
    SINDEXOF,
    STOINT
    ;

    @Override
    public String toString() {
        //TODO: someone needs to check these!
        switch(this) {
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
            case I2L:
                return "(_ sign_extend 32)";
            case I2F:
            case L2F:
                return "(_ to_fp 8 24)";
            case I2D:
            case L2D:
                return "(_ to_fp 11 53)";
            case L2I:
                return "(_ extract 32)"; // FIXME: signed?
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

            default:
                return super.toString();
        }
    }

    static EnumSet<OperatorComparator> boolOps = EnumSet.of(BVEQ, STRINGEQ, BVNE, STRINGNE, BVLT, BVLE, BVGT, BVGE, BNEG, BAND, BOR, BXOR, BEQUIV, BIMPLIES);

    static EnumSet<OperatorComparator> cmpOps = EnumSet.of(LCMP, FCMPL, FCMPG, DCMPL, DCMPG);

    public boolean isBoolean() {
        return boolOps.contains(this);
    }

    public boolean isCmp() {
        return  cmpOps.contains(this);
    }
}
