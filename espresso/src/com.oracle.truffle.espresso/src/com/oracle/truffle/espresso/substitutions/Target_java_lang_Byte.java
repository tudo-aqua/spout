package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import tools.aqua.spout.SPouTNumeric;

@EspressoSubstitutions
public final class Target_java_lang_Byte {

    @Substitution(methodName = "toString", passAnnotations = true)
    public static @JavaType(String.class) StaticObject byteToString(@JavaType(internalName = "B") Object i, @Inject Meta meta){
        return SPouTNumeric.intToString(i, meta);
    }

    @Substitution(hasReceiver = true, passAnnotations = true)
    public static @JavaType(internalName = "B") Object byteValue(@JavaType(Byte.class) StaticObject self,  @Inject Meta meta){
        return SPouTNumeric.byteByteValue(self, meta);
    }

    @Substitution(hasReceiver = true, passAnnotations = true)
    public static @JavaType(internalName = "S") Object shortValue(@JavaType(Byte.class) StaticObject self,  @Inject Meta meta){
        return SPouTNumeric.byteToShort(self, meta);
    }

    @Substitution(hasReceiver = true, passAnnotations = true)
    public static @JavaType(internalName = "J") Object longValue(@JavaType(Byte.class) StaticObject self, @Inject Meta meta){
        return SPouTNumeric.byteToLong(self, meta);
    }

    @Substitution(methodName = "valueOf", passAnnotations = true)
    public static @JavaType(Byte.class) StaticObject valueOfByte(@JavaType(internalName = "B") Object b, @Inject Meta meta){
        return SPouTNumeric.byteValueOf(b, meta);
    }
}
