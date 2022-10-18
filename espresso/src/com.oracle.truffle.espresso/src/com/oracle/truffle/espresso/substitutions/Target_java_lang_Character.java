package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.meta.Meta;
import tools.aqua.spout.SPouT;

@EspressoSubstitutions
public final class Target_java_lang_Character {
    @Substitution(methodName = "toUpperCase", passAnnotations = true)
    public static @JavaType(internalName = "C") Object toUpperCase_char(
            @JavaType(internalName = "C") Object c,
            @Inject Meta meta){
        return SPouT.characterToUpperCase(c, meta);
    }

    @Substitution(methodName = "toLowerCase", passAnnotations = true)
    public static @JavaType(internalName = "C") Object toLowerCase_char(
            @JavaType(internalName = "C") Object c,
            @Inject Meta meta){
        return SPouT.characterToLowerCase(c, meta);
    }

    @Substitution(methodName = "isDefined")
    public static @JavaType(internalName = "Z") Object isDefined_char(@JavaType(internalName = "C") Object c,
                                                              @Inject Meta meta){
        return SPouT.isCharDefined(c, meta);
    }
}
