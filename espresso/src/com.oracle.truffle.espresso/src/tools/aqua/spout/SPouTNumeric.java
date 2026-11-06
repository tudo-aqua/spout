package tools.aqua.spout;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import tools.aqua.smt.Expression;
import tools.aqua.spout.analyses.MetaNumericAnalysis;

import static com.oracle.truffle.espresso.runtime.dispatch.staticobject.EspressoInterop.getMeta;

public class SPouTNumeric {
    private static Config config = null;
    private static boolean analyze = false;
    private static MetaNumericAnalysis analysis = null;

    public static void newPath(Config config, boolean analyze) {
        analysis = new MetaNumericAnalysis(config);
        SPouTNumeric.config = config;
        SPouTNumeric.analyze = analyze;
    }

    public static void stopAnalysis() {
        analyze = false;
    }

    /**
     * Implementing the Byte Cache here, as it somehow is an integer
     * */

    private static StaticObject[] byteCache;
    private static void initByteCache() {
        if (byteCache == null) {}
            final int size = -(-128)+127+1;
            byteCache = new StaticObject[size];
            Meta meta = getMeta();
            for(int i = -128; i < 127; i++){
                StaticObject value = meta.java_lang_Byte.allocateInstance();
                meta.java_lang_Byte_value.set(value, AnnotatedValue.value((byte)i));
                byteCache[128 + i] = value;
            }
    };



    @CompilerDirectives.TruffleBoundary
    public static StaticObject byteValueOf(Object b, Meta meta) {
        StaticObject o;
        byte v = AnnotatedValue.value(b);
        if (analyze){
            o = meta.java_lang_Byte.allocateInstance();
            meta.java_lang_Byte_value.set(o, AnnotatedValue.value(b));
            AnnotatedVM.setFieldAnnotation(o, meta.java_lang_Byte_value, AnnotatedValue.svalue(b));
        }else{
            if(byteCache == null){
                initByteCache();
            }
            o = byteCache[128+v];
        }
        return o;
    }

    @CompilerDirectives.TruffleBoundary
    public static Object byteByteValue(StaticObject self, Meta meta){
        byte cVal = meta.java_lang_Byte_value.getAsByte(meta, self, true);
        Annotations sVal = AnnotatedVM.getFieldAnnotation(self, meta.java_lang_Byte_value);
        return new AnnotatedValue(cVal, sVal);
    }

    @CompilerDirectives.TruffleBoundary
    public static Object byteToShort(StaticObject self, Meta meta){
        byte cVal = meta.java_lang_Byte_value.getAsByte(meta, self, true);
        Annotations sVal = AnnotatedVM.getFieldAnnotation(self, meta.java_lang_Byte_value);
        if(analyze && sVal != null) {
            return new AnnotatedValue((long) cVal, analysis.byteToShort(cVal, sVal));
        }
        return (short) cVal;
    }

    public static Object byteToLong(StaticObject self, Meta meta) {
        byte cVal = meta.java_lang_Byte_value.getAsByte(meta, self, true);
        Annotations sVal = AnnotatedVM.getFieldAnnotation(self, meta.java_lang_Byte_value);
        if(analyze && sVal != null) {
            return new AnnotatedValue((long) cVal, analysis.byteToLong(cVal, sVal));
        }
        return (long) cVal;
    }

    /**
     * Implementing the Short Cache here
     */

    private static StaticObject[] shortCache;
    private static void initShortCache() {
        Meta meta = getMeta();
        shortCache = new StaticObject[256];
        for (int i=-128; i <= 127; i++) {
            StaticObject o = meta.java_lang_Short.allocateInstance();
            meta.java_lang_Short_value.set(o, AnnotatedValue.value((short) i));
            intCache[128 + i] = o;
        }
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject shortValueOfShort(Object i, Meta meta) {
        StaticObject o;
        SPouT.debug("ShortValueOfShort", i);
        short v =  AnnotatedValue.value(i);
        if (analyze || v < -128 || 127 < v)  {
            o = meta.java_lang_Short.allocateInstance();
            meta.java_lang_Short_value.set(o, AnnotatedValue.value(i));
            AnnotatedVM.setFieldAnnotation(o, meta.java_lang_Short_value,
                    AnnotatedValue.svalue(i));
        } else {
            if (intCache == null){
                initIntCache();
            }
            o = intCache[128 + (int) v];
        }
        return o;
    }

    public static Object shortShortValue(StaticObject self, Meta meta) {
        short cVal = meta.java_lang_Short_value.getAsShort(meta, self, true);
        Annotations sVal = AnnotatedVM.getFieldAnnotation(self, meta.java_lang_Short_value);
        SPouT.debug("ShortValueOfShort", cVal, sVal);
        if (sVal != null) {return new AnnotatedValue(cVal, sVal);}
        else {return cVal;}
    }


    /**
     * Implementing the Integer Cache here
     */

    private static StaticObject[] intCache;
    private static void initIntCache() {
        Meta meta = getMeta();
        intCache = new StaticObject[256];
        for (int i=-128; i <= 127; i++) {
            StaticObject o = meta.java_lang_Integer.allocateInstance();
            meta.java_lang_Integer_value.set(o, AnnotatedValue.value(i));
            intCache[128 + i] = o;
        }
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject integerValueOfInt(Object i, Meta meta) {
        StaticObject o;
        int v =  AnnotatedValue.value(i);
        if (analyze || v < -128 || 127 < v)  {
            o = meta.java_lang_Integer.allocateInstance();
            meta.java_lang_Integer_value.set(o, AnnotatedValue.value(i));
            AnnotatedVM.setFieldAnnotation(o, meta.java_lang_Integer_value,
                    AnnotatedValue.svalue(i));
        } else {
            if (intCache == null){
                initIntCache();
            }
            o = intCache[128 + v];
        }
        return o;
    }

    /**
     * Long Cache
     */

    private static StaticObject[] longCache;
    private static void initLongCache() {
        Meta meta = getMeta();
        intCache = new StaticObject[256];
        for (int i=-128; i <= 127; i++) {
            StaticObject o = meta.java_lang_Long.allocateInstance();
            meta.java_lang_Long_value.set(o, AnnotatedValue.value(i));
            intCache[128 + i] = o;
        }
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject longValueOfLong(Object l, Meta meta) {
        StaticObject o;
        long v =  AnnotatedValue.value(l);
        if (analyze || v < -128 || 127 < v)  {
            o = meta.java_lang_Long.allocateInstance();
            meta.java_lang_Long_value.set(o, AnnotatedValue.value(l));
            AnnotatedVM.setFieldAnnotation(o, meta.java_lang_Long_value,
                    AnnotatedValue.svalue(l));
        } else {
            if (longCache == null){
                initLongCache();
            }
            o = longCache[128 + (int) v];
        }
        return o;
    }

    public static Object longToDoubleValue(StaticObject self, Meta meta) {
        long cVal = meta.java_lang_Long_value.getAsLong(meta, self, true);
        Annotations sVal = AnnotatedVM.getFieldAnnotation(self, meta.java_lang_Long_value);
        if (sVal != null) {
            return new AnnotatedValue( (double) cVal, analysis.longToDouble(cVal, sVal));
        }else{
            return (double) cVal;
        }
    }

    public static Object longToFloatValue(StaticObject self, Meta meta) {
        long cVal = meta.java_lang_Long_value.getAsLong(meta, self, true);
        Annotations sVal = AnnotatedVM.getFieldAnnotation(self, meta.java_lang_Long_value);
        if (sVal != null) {
            return new AnnotatedValue( (float) cVal, analysis.longToFloat(cVal, sVal));
        }else{
            return (float) cVal;
        }
    }

    public static Object longLongValue(StaticObject self, Meta meta) {
        long cVal = meta.java_lang_Long_value.getAsLong(meta, self, true);
        Annotations sVal = AnnotatedVM.getFieldAnnotation(self, meta.java_lang_Long_value);
        if (sVal != null) {
            return new AnnotatedValue(cVal, sVal);
        }else{
            return cVal;
        }
    }

    /**
     * Implementing the Character Cache
     */

    private static StaticObject[] characterCache;
    private static void initCharCache() {
        Meta meta = getMeta();
        characterCache = new StaticObject[128];
        for (int i=0; i < 128; i++) {
            StaticObject o = meta.java_lang_Character.allocateInstance();
            meta.java_lang_Character_value.set(o, AnnotatedValue.value(i));
            characterCache[128 + i] = o;
        }
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject charValueOfChar(Object c, Meta meta) {
        StaticObject o;
        char v =  AnnotatedValue.value(c);
        if (analyze || 127 < v)  {
            o = meta.java_lang_Character.allocateInstance();
            meta.java_lang_Character_value.set(o, AnnotatedValue.value(c));
            AnnotatedVM.setFieldAnnotation(o, meta.java_lang_Character_value,
                    AnnotatedValue.svalue(c));
        } else {
            if (characterCache == null){
                initCharCache();
            }
            o = characterCache[v];
        }
        return o;
    }

    public static Object charCharValue(StaticObject self, Meta meta) {
        char cVal = meta.java_lang_Character_value.getAsChar(meta, self, true);
        Annotations sVal = AnnotatedVM.getFieldAnnotation(self, meta.java_lang_Character_value);
        return new AnnotatedValue(cVal, sVal);
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject byteToString(Object b, Meta meta){
        if(AnnotatedValue.svalue(b) != null) SPouT.losePrecision("Byte.toString is not symbolically implemented yet", meta);
        return meta.toGuestString(Byte.toString((AnnotatedValue.value(b))));
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject booleanToString(Object b, Meta meta){
        if(AnnotatedValue.svalue(b) != null) SPouT.losePrecision("Boolean.toString is not symbolically implemented yet", meta);
        Object v = AnnotatedValue.value(b);
        // booleans may be ints (0 / 1) on the operand stack
        boolean value = v instanceof Boolean ? (Boolean) v : ((Number) v).intValue() != 0;
        return meta.toGuestString(Boolean.toString(value));
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject intToString(Object b, Meta meta){
        // also the string of bytes and shorts (boxed as such)
        StaticObject result = meta.toGuestString(Integer.toString(((Number) AnnotatedValue.value(b)).intValue()));
        if (AnnotatedValue.svalue(b) != null && SPouT.hasAnalysis() && config.hasConcolicAnalysis()) {
            config.getConcolicAnalysis().intToString(result, AnnotatedValue.svalue(b));
        }
        return result;
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject doubleToString(Object b, Meta meta){
        if(AnnotatedValue.svalue(b) != null) SPouT.losePrecision("Double.toString is not symbolically implemented yet", meta);
        return meta.toGuestString(Double.toString((AnnotatedValue.value(b))));
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject doubleToHexString(Object b, Meta meta){
        if(AnnotatedValue.svalue(b) != null) SPouT.losePrecision("Double.toHexString is not symbolically implemented yet", meta);
        return meta.toGuestString(Double.toHexString((AnnotatedValue.value(b))));
    }


    @CompilerDirectives.TruffleBoundary
    public static Object doubleToShort(StaticObject d, Meta meta) {
        double dd = meta.java_lang_Double_value.getAsDouble(meta, d, true);
        Annotations sVal = AnnotatedVM.getFieldAnnotation(d, meta.java_lang_Double_value);
        if(analyze && sVal != null){
            sVal = analysis.doubleToShort(dd, sVal);
            AnnotatedValue newAVal = new AnnotatedValue((short) dd, sVal);
            return newAVal;
        }
        return (short) dd;
    }

    public static StaticObject doubleValueOfDouble(Object d, Meta meta) {
        StaticObject dValue = meta.java_lang_Double.allocateInstance();
        meta.java_lang_Double_value.set(dValue, AnnotatedValue.value(d));
        AnnotatedVM.setFieldAnnotation(dValue, meta.java_lang_Double_value,
                AnnotatedValue.svalue(d));
        return dValue;
    }
    @CompilerDirectives.TruffleBoundary
    public static Object doubleToRawLongBits(Object value, Meta meta) {
        double v = AnnotatedValue.value(value);
        return fpToBits(value, Double.doubleToRawLongBits(v), Double.isNaN(v), true, true);
    }

    @CompilerDirectives.TruffleBoundary
    public static Object doubleToLongBits(Object value, Meta meta) {
        double v = AnnotatedValue.value(value);
        return fpToBits(value, Double.doubleToLongBits(v), Double.isNaN(v), true, false);
    }

    /** the bits of a float or double (concrete {@code bits}), symbolic if the value is symbolic */
    private static Object fpToBits(Object value, Object bits, boolean nan, boolean isDouble, boolean raw) {
        Expression x = symbolic(value);
        if (x == null) {
            return bits;
        }
        Expression e = config.getConcolicAnalysis().fpToBits(x, isDouble, raw, nan);
        return e != null ? result(bits, e) : bits;
    }

    private static Expression symbolic(Object value) {
        if (!analyze || config == null || !config.hasConcolicAnalysis()) {
            return null;
        }
        return (Expression) Annotations.annotation(AnnotatedValue.svalue(value), config.getConcolicIdx());
    }

    private static Object result(Object concrete, Expression e) {
        Annotations a = Annotations.create();
        a.set(config.getConcolicIdx(), e);
        return new AnnotatedValue(concrete, a);
    }

    @CompilerDirectives.TruffleBoundary
    public static Object doubleLongBitsToDouble(Object bits, Meta meta) {
        double v = Double.longBitsToDouble(AnnotatedValue.value(bits));
        Expression b = symbolic(bits);
        return b != null ? result(v, config.getConcolicAnalysis().bitsToFp(b, true)) : v;
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject doubleToString(StaticObject f, Meta meta) {
        Annotations sVal = AnnotatedVM.getFieldAnnotation(f, meta.java_lang_Double_value);
        if(sVal != null) SPouT.losePrecision("Double.toString is not symbolically implemented yet", meta);
        return meta.toGuestString(Double.toString(meta.java_lang_Double_value.getAsDouble(meta, f, true)));
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject floatFloatToString(Object f, Meta meta) {
        Annotations sVal = AnnotatedValue.svalue(f);
        SPouT.debug("floatFloatToString is not symbolically implemented yet", sVal);
        if(sVal != null) SPouT.losePrecision("Float.toString is not symbolically implemented yet", meta);
        return meta.toGuestString(Float.toString((AnnotatedValue.value(f))));
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject floatToString(StaticObject f,  Meta meta) {
        Annotations sVal = AnnotatedVM.getFieldAnnotation(f, meta.java_lang_Float_value);
        if(sVal != null) SPouT.losePrecision("Float.toString is not symbolically implemented yet", meta);
        return meta.toGuestString(Float.toString(meta.java_lang_Float_value.getAsFloat(meta, f, true)));
    }

    @CompilerDirectives.TruffleBoundary
    public static  StaticObject floatToHexString( Object f,Meta meta) {
        if(AnnotatedValue.svalue(f) != null) SPouT.losePrecision("Float.toHexString is not symbolically implemented yet", meta);
        return meta.toGuestString(Float.toHexString((AnnotatedValue.value(f))));
    }

    @CompilerDirectives.TruffleBoundary
    public static Object floatToIntBits(Object f, Meta meta) {
        float v = AnnotatedValue.value(f);
        return fpToBits(f, Float.floatToIntBits(v), Float.isNaN(v), false, false);
    }

    @CompilerDirectives.TruffleBoundary
    public static Object floatToRawIntBits(Object value, Meta meta) {
        float v = AnnotatedValue.value(value);
        return fpToBits(value, Float.floatToRawIntBits(v), Float.isNaN(v), false, true);
    }

    @CompilerDirectives.TruffleBoundary
    public static Object intBitsToFloat(Object bits, Meta meta) {
        float v = Float.intBitsToFloat(AnnotatedValue.value(bits));
        Expression b = symbolic(bits);
        return b != null ? result(v, config.getConcolicAnalysis().bitsToFp(b, false)) : v;
    }

    @CompilerDirectives.TruffleBoundary
    public static Object integerReverseBytes(Object i, Meta meta) {
        int v = Integer.reverseBytes(AnnotatedValue.value(i));
        Expression x = symbolic(i);
        return x != null ? result(v, config.getConcolicAnalysis().intReverseBytes(x)) : v;
    }

    @CompilerDirectives.TruffleBoundary
    public static Object longReverse(Object in, Meta meta) {
        // Long.reverse (was: Long.reverseBytes, also concretely)
        long v = Long.reverse(AnnotatedValue.value(in));
        Expression x = symbolic(in);
        return x != null ? result(v, config.getConcolicAnalysis().longReverse(x)) : v;
    }

    @CompilerDirectives.TruffleBoundary
    public static Object mathGetExponentDouble(Object f, Meta meta) {
        int v = Math.getExponent((double) AnnotatedValue.value(f));
        Expression x = symbolic(f);
        return x != null ? result(v, config.getConcolicAnalysis().fpGetExponent(x, true)) : v;
    }
    @CompilerDirectives.TruffleBoundary
    public static Object mathGetExponentFloat(Object f, Meta meta) {
        int v = Math.getExponent((float) AnnotatedValue.value(f));
        Expression x = symbolic(f);
        return x != null ? result(v, config.getConcolicAnalysis().fpGetExponent(x, false)) : v;
    }
}
