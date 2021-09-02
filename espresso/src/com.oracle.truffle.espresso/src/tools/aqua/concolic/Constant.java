package tools.aqua.concolic;

import com.oracle.truffle.espresso.runtime.StaticObject;

public abstract class Constant extends PrimitiveExpression {

    public final static IntConstant INT_ZERO = new IntConstant(0);

    private final static class IntConstant extends Constant {

        IntConstant(int value) {
            super(PrimitiveTypes.INT, value);
        }

        @Override
        Integer getValue() {
            return (int) super.getValue();
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
}
