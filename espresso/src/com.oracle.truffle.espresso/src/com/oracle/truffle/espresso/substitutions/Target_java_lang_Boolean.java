package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import tools.aqua.spout.SPouT;
import tools.aqua.spout.SPouTNumeric;

@EspressoSubstitutions
public final class Target_java_lang_Boolean {

    @Substitution(methodName = "toString", passAnnotations = true)
    public static @JavaType(String.class) StaticObject booleanToString(@JavaType(internalName = "Z") Object i, @Inject Meta meta){
        return SPouTNumeric.booleanToString(i, meta);
    }

}
