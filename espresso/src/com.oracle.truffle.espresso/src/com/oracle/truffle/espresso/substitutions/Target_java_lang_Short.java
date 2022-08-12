package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import tools.aqua.spout.SPouTNumeric;

@EspressoSubstitutions
public final class Target_java_lang_Short {

    @Substitution(methodName = "valueOf", passAnnotations = true)
    public static @JavaType(Short.class) StaticObject valueOfShort(@JavaType(internalName = "S") Object s, @Inject Meta meta){
        return SPouTNumeric.shortValueOfShort(s, meta);
    }

    @Substitution(hasReceiver = true, passAnnotations = true)
    public static @JavaType(internalName = "S") Object shortValue(@JavaType(Short.class) StaticObject self, @Inject Meta meta){
        return SPouTNumeric.shortShortValue(self, meta);
    }
 }
