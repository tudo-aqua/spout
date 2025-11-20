package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.AnnotatedValue;
import tools.aqua.spout.SPouT;
import tools.aqua.spout.SPouTInteger;

@EspressoSubstitutions
public final class Target_java_lang_Byte {

    @Substitution(methodName = "toString", passAnnotations = true)
    public static @JavaType(String.class) StaticObject byteToString(@JavaType(internalName = "B") Object i, @Inject Meta meta){
        SPouT.debug("hello from byteToString", AnnotatedValue.svalue(i));
        SPouT.debug("hello from byteToString", i);
        return SPouTInteger.intToString(i, meta);
    }

    @Substitution(hasReceiver = true, passAnnotations = true)
    public static @JavaType(internalName = "B") Object byteValue(@JavaType(Byte.class) StaticObject self,  @Inject Meta meta){
        SPouT.debug("hello from byteValue", self.getKlass());
        return SPouTInteger.byteByteValue(self, meta);
    }

    @Substitution(methodName = "valueOf", passAnnotations = true)
    public static @JavaType(Byte.class) StaticObject valueOfByte(@JavaType(internalName = "B") Object b, @Inject Meta meta){
        SPouT.debug("hello from valueOfByte", AnnotatedValue.svalue(b));
        SPouT.debug("hello from valueOfByte", b);
        return SPouTInteger.byteValueOf(b, meta);
    }
}
