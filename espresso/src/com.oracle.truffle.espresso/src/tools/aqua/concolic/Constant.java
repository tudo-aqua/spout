package tools.aqua.concolic;

public abstract class Constant extends Atom {

    public final static IntConstant INT_ZERO = new IntConstant(0);

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

    public static Constant fromConcreteValue(int i) {
        return new IntConstant(i);
    }

    public static Constant fromConcreteValue(String s) {
        return new StringConstant(s);
    }

}
