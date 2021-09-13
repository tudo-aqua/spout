package tools.aqua.concolic;

public abstract class Constant extends Atom {

    public final static IntConstant INT_ZERO = new IntConstant(0);

    public final static LongConstant LONG_ZERO = new LongConstant(0L);

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
            // FIXME: correct format for number
            return "" + getValue();
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
            // FIXME: correct format for number
            return "" + getValue();
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
            // FIXME: correct format for number
            return "" + getValue();
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

}
