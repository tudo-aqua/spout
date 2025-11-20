package tools.aqua.spout;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.analyses.MetaIntegerAnalysis;

import static com.oracle.truffle.espresso.runtime.dispatch.EspressoInterop.getMeta;

public class SPouTInteger {
    private static Config config = null;
    private static boolean analyze = false;
    private static MetaIntegerAnalysis analysis = null;

    public static void newPath(Config config, boolean analyze) {
        analysis = new MetaIntegerAnalysis(config);
        SPouTInteger.config = config;
        SPouTInteger.analyze = analyze;
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
            SPouT.debug("Settting field annotaiton byteValueOf", AnnotatedValue.svalue(b));
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
        SPouT.debug("Byte byteValue got Aannotaitons", sVal);
        return new AnnotatedValue(cVal, sVal);
    }

    /**
     * Implementing the Integer Chache here
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

    public static StaticObject byteToString(Object b, Meta meta){
        if(AnnotatedValue.svalue(b) != null) SPouT.stopRecording("Byte.toString is not symbolically implemented yet", meta);
        return meta.toGuestString(Byte.toString((AnnotatedValue.value(b))));
    }

    public static StaticObject intToString(Object b, Meta meta){
        if(AnnotatedValue.svalue(b) != null) SPouT.stopRecording("Byte.toString is not symbolically implemented yet", meta);
        return meta.toGuestString(Integer.toString((AnnotatedValue.value(b))));
    }
}
