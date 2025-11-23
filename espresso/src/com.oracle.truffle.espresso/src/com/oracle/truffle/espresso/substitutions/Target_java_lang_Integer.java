package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.AnnotatedValue;
import tools.aqua.spout.SPouT;
import tools.aqua.spout.SPouTNumeric;

@EspressoSubstitutions
public final class Target_java_lang_Integer {

    @Substitution
    public static int parseInt(@JavaType(String.class) StaticObject s, @Inject Meta meta){
        return SPouT.parseInt(s, meta);
    }

    @Substitution(methodName = "toString", passAnnotations = true)
    public static @JavaType(String.class) StaticObject intToString(@JavaType(internalName = "I") Object i, @Inject Meta meta){
        return SPouTNumeric.intToString(i, meta);
    }

    @Substitution(methodName = "valueOf", passAnnotations = true)
    public static @JavaType(Integer.class) StaticObject integerValueOfInt(@JavaType(internalName = "I") Object i, @Inject Meta meta) {
        return SPouTNumeric.integerValueOfInt(i, meta);
    }
}
