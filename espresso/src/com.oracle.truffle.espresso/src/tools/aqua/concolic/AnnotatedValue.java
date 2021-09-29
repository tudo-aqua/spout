package tools.aqua.concolic;

import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.StaticObject;

public class AnnotatedValue {

    AnnotatedValue(Object c, Expression s) {
        this.concrete = c;
        this.symbolic = s;
    }

    static AnnotatedValue fromConstant(PrimitiveTypes type, Object value) {
        switch (type) {
            case INT: return new AnnotatedValue(value, Constant.fromConcreteValue( (int) value));
            case LONG: return new AnnotatedValue(value, Constant.fromConcreteValue( (long) value));
            case FLOAT: return new AnnotatedValue(value, Constant.fromConcreteValue( (float) value));
            case DOUBLE: return new AnnotatedValue(value, Constant.fromConcreteValue( (double) value));
            default:
                throw EspressoError.shouldNotReachHere("unsupported constant type");
        }
    }

    private Object concrete;

    private Expression symbolic = null;

    public long asLong() {
        return (long) concrete;
    }

    public int asInt() {
        if (concrete instanceof Boolean) {
            // FIXME: should never happen!
            return ((Boolean)concrete) ? 1 : 0;
        }
        if (concrete instanceof Byte) {
            return ((Byte)concrete);
        }
        return (int) concrete;
    }

    public float asFloat() {
        return (float) concrete;
    }

    public double asDouble() {
        return (double) concrete;
    }

    public StaticObject asRef() {
        return (StaticObject) concrete;
    }

    public boolean isSymbolic() {
        return symbolic == null;
    }

    public Expression symbolic() {
        return symbolic;
    }

    public Object asRaw() {
        return concrete;
    }

    public boolean asBoolean() {
        return (boolean) concrete;
    }
}