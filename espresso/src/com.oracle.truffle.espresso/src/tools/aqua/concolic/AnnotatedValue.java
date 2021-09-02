package tools.aqua.concolic;

import com.oracle.truffle.espresso.runtime.StaticObject;

public class AnnotatedValue {

    //public static final AnnotatedValue NADA = new AnnotatedValue(null);

    //public static final AnnotatedValue NULL = new AnnotatedValue(StaticObject.NULL);
    //public static final AnnotatedValue ZERO = new AnnotatedValue( (int) 0);

    // FIXME: change public api!

    AnnotatedValue(Object c, Expression s) {
        this.concrete = c;
        this.symbolic = s;
    }

    static AnnotatedValue fromInt(int i) {
        return new AnnotatedValue(i, Constant.fromConcreteValue(i));
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
}