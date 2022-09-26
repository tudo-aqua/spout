package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.AnnotatedValue;
import tools.aqua.spout.SPouT;

@EspressoSubstitutions
public final class Target_java_lang_String {
    @Substitution(hasReceiver = true)
    public static @JavaType(internalName = "Z") Object contains(@JavaType(String.class) StaticObject self,
                                                                @JavaType(CharSequence.class) StaticObject s,
                                                                @Inject Meta meta) {
        if (StaticObject.isNull(self) || StaticObject.isNull(s)) {
            return false;
        }
        return SPouT.stringContains(self, s, meta);
    }

    @Substitution(hasReceiver = true, methodName = "<init>")
    public static void init(@JavaType(String.class) StaticObject self, @JavaType(String.class) StaticObject other, @Inject Meta meta) {
        SPouT.stringConstructor(self, other, meta);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(internalName = "I") Object compareTo(@JavaType(String.class) StaticObject self,
                                                                 @JavaType(String.class) StaticObject other,
                                                                 @Inject Meta meta) {
        return SPouT.stringCompareTo(self, other, meta);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(internalName = "Z") Object equals(
            @JavaType(String.class) StaticObject self,
            @JavaType(Object.class) StaticObject other,
            @Inject Meta meta) {
        return SPouT.stringEquals(self, other, meta);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(internalName = "C") Object charAt(@JavaType(String.class) StaticObject self,
                                                                    @JavaType(internalName = "I") Object index,
                                                                    @Inject Meta meta) {
        return SPouT.stringCharAt(self, index, meta);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(internalName = "I") Object length(
            @JavaType(String.class) StaticObject self,
            @Inject Meta meta) {
        return SPouT.stringLength(self, meta);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject concat(@JavaType(String.class) StaticObject self,
                                                              @JavaType(String.class) StaticObject s,
                                                              @Inject Meta meta){
        return SPouT.stringConcat(self, s, meta);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject toString(@JavaType(String.class) StaticObject self){
        return self;
    }

    @Substitution(hasReceiver = true, methodName = "toUpperCase")
    public static @JavaType(String.class) StaticObject toUpperCase(@JavaType(String.class) StaticObject self, @Inject Meta meta){
        return SPouT.stringToUpperCase(self, meta);
    }

    @Substitution(hasReceiver = true, methodName = "toLowerCase")
    public static @JavaType(String.class) StaticObject toLowerCase(@JavaType(String.class) StaticObject self, @Inject Meta meta){
        return SPouT.stringToLowerCase(self, meta);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String[].class) Object split(@JavaType(String.class) StaticObject self,
                                                         @JavaType(String.class) StaticObject regex,
                                                         @Inject Meta meta){
        return SPouT.split(self, regex, meta);
    }

    @Substitution(hasReceiver = true, methodName = "regionMatches")
    public static @JavaType(internalName = "Z") Object regionMatches_ignoreCase(@JavaType(String.class) StaticObject self,
                                                                                @JavaType(internalName = "Z") Object ignoreCase,
                                                                                @JavaType(internalName = "I") Object toffset,
                                                                                @JavaType(String.class) StaticObject other,
                                                                                @JavaType(internalName = "I") Object ooffset,
                                                                                @JavaType(internalName = "I") Object len,
                                                                                @Inject Meta meta){
        return SPouT.stringRegionMatches_ignoreCase(self, ignoreCase, toffset, other, ooffset, len, meta);
    }
}
