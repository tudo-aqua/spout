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

public abstract class Constant extends Atom {

    public final static IntConstant INT_ZERO = new IntConstant(0);
    public final static IntConstant INT_0x1F = new IntConstant(0x1f);
    public final static IntConstant INT_0x3F = new IntConstant(0x3f);
    public final static IntConstant INT_BYTE_MAX = new IntConstant(127);
    public final static IntConstant INT_SHORT_MAX = new IntConstant(32767);
    public final static IntConstant INT_CHAR_MAX = new IntConstant(65535);

    public final static IntConstant INT_MIN = new IntConstant(Integer.MIN_VALUE);
    public final static IntConstant INT_MAX = new IntConstant(Integer.MAX_VALUE);
    public final static LongConstant LONG_MIN = new LongConstant(Long.MIN_VALUE);
    public final static LongConstant LONG_MAX = new LongConstant(Long.MAX_VALUE);


    public final static LongConstant LONG_ZERO = new LongConstant(0L);

    public final static NatConstant NAT_ZERO = new NatConstant(0);


    private final static class NatConstant extends Constant {
        NatConstant(int value){
            super(PrimitiveTypes.NAT, value);
        }
    }

    private final static class IntConstant extends Constant {

        IntConstant(int value) {
            super(PrimitiveTypes.INT, value);
        }

        @Override
        Integer getValue() {
            return (int) super.getValue();
        }

        @Override
        public String toString() {
            return "#x" + String.format("%1$08x", getValue());
        }
    }

    private final static class LongConstant extends Constant {

        LongConstant(long value) {
            super(PrimitiveTypes.LONG, value);
        }

        @Override
        Long getValue() {
            return (long) super.getValue();
        }

        @Override
        public String toString() {
            return "#x" + String.format("%1$016x", getValue());
        }
    }

    private final static class FloatConstant extends Constant {

        FloatConstant(float value) {
            super(PrimitiveTypes.FLOAT, value);
        }

        @Override
        Float getValue() {
            return (float) super.getValue();
        }

        @Override
        public String toString() {
            Float f = getValue();
            if (f.isNaN()) {
                return "(_ NaN 8 24)";
            }
            else if (f.isInfinite()) {
                return "(_ " + (Float.POSITIVE_INFINITY == f ? "+" : "-") + "oo 8 24)";
            }
            else {
                // rep. is (_ b1 b2 b3) three bitvectors of size 1 8 23
                int bits = Float.floatToIntBits(f);
                int sign =  (bits & 0x80000000) >>> 31;
                int expn =  (bits & 0x7f800000) >>> 23;
                int mtsa =  (bits & 0x007fffff);

                return "(fp" +
                        " #b" + String.format("%1s",  Long.toBinaryString(sign)) +
                        " #b" + String.format("%8s", Long.toBinaryString(expn)).replaceAll(" ", "0") +
                        " #b" + String.format("%23s", Long.toBinaryString(mtsa)).replaceAll(" ", "0") + ")";
            }
        }

    }

    private final static class DoubleConstant extends Constant {

        DoubleConstant(double value) {
            super(PrimitiveTypes.DOUBLE, value);
        }

        @Override
        Double getValue() {
            return (double) super.getValue();
        }

        @Override
        public String toString() {
            Double d = getValue();
            if (d.isNaN()) {
                return "(_ NaN 11 53)";
            }
            else if (d.isInfinite()) {
                return "(_ " + (Double.POSITIVE_INFINITY == d ? "+" : "-") + "oo 11 53)";
            }
            else {
                // rep. is (_ b1 b2 b3) three bitvectors of size 1 11 52
                long bits = Double.doubleToLongBits(d);
                long sign =  (bits & 0x8000000000000000L) >>> 63;
                long expn =  (bits & 0x7ff0000000000000L) >>> 52;
                long mtsa =  (bits & 0x000fffffffffffffL);

                return "(fp" +
                    " #b" + String.format("%1s",  Long.toBinaryString(sign)) +
                    " #b" + String.format("%11s", Long.toBinaryString(expn)).replaceAll(" ", "0") +
                    " #b" + String.format("%52s", Long.toBinaryString(mtsa)).replaceAll(" ", "0") + ")";
            }
        }
    }

    private final static class StringConstant extends Constant {

        StringConstant(String value) {
            super(PrimitiveTypes.STRING, value);
        }

        @Override
        String getValue() {
            return (String) super.getValue();
        }

        @Override
        public String toString() {
            return "\"" + getValue() + "\"";
        }
    }

    private Object value;

    Constant(PrimitiveTypes type, Object value) {
        super(type);
        this.value = value;
    }

    Object getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "" + value;
    }

    public static Constant fromConcreteValue(int v) {
        return new IntConstant(v);
    }

    public static Constant fromConcreteValue(long v) {
        return new LongConstant(v);
    }

    public static Constant fromConcreteValue(float v) {
        return new FloatConstant(v);
    }

    public static Constant fromConcreteValue(double v) {
        return new DoubleConstant(v);
    }

    public static Constant fromConcreteValue(String s) {
        return new StringConstant(s);
    }

    public static Constant createNatConstant(int value) { return new NatConstant(value);}

}
