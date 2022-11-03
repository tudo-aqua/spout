package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.AnnotatedValue;
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

    @Substitution(methodName = "isDefined", passAnnotations = true)
    public static @JavaType(internalName = "Z") Object isDefined_char(@JavaType(internalName = "C") Object c,
                                                              @Inject Meta meta){
        return SPouT.isCharDefined(c, meta);
    }

    @Substitution(passAnnotations = true, hasReceiver = true)
    public static @JavaType(internalName = "Z") Object equals(@JavaType(Character.class) StaticObject self, @JavaType(Object.class) Object obj, @Inject Meta meta) {
        if(obj instanceof StaticObject){
            StaticObject other = (StaticObject) obj;
            return SPouT.characterEquals(self, other, meta);
        }else{
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException,
                    "I don't now what to do, if not both objects in character equal are static objects, yet");
        }
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(Character.class) StaticObject valueOf(@JavaType(internalName = "C") Object cIn, @Inject Meta meta){
        return SPouT.characterValueOf(cIn, meta);
    }
}
