package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.SPouTNumeric;

@EspressoSubstitutions
public final class Target_java_lang_Long {

    @Substitution(methodName = "valueOf", passAnnotations = true)
    public static @JavaType(Long.class) StaticObject valueOfLong(@JavaType(internalName = "J") Object j, @Inject Meta meta) {
        return SPouTNumeric.longValueOfLong(j, meta);
    }

    @Substitution(hasReceiver = true, passAnnotations = true)
    public static @JavaType(internalName = "J") Object longValue(@JavaType(Long.class) StaticObject self,  @Inject Meta meta){
        return SPouTNumeric.longLongValue(self, meta);
    }
}
