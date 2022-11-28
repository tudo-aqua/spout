package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.AnnotatedValue;
import tools.aqua.spout.SPouT;

import java.util.Locale;

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
    public static @JavaType(String.class) StaticObject toLowerCase_plain(@JavaType(String.class) StaticObject self, @Inject Meta meta){
        return SPouT.stringToLowerCase(self, meta);
    }

    @Substitution(hasReceiver = true, methodName = "toLowerCase")
    public static @JavaType(String.class) StaticObject toLowerCase_local(@JavaType(String.class) StaticObject self, @JavaType(Locale.class) StaticObject locale,  @Inject Meta meta){
        SPouT.log("Warning: we do not support a precise model for toLowerCase with Locales. Using normal toLowerCase without Locale instead as approximation.");
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

    //Value Of Methods
    @Substitution(methodName = "valueOf", passAnnotations = true)
    @CompilerDirectives.TruffleBoundary
    public static @JavaType(String.class) StaticObject valueOf_bool(@JavaType(internalName = "Z") Object v, @Inject Meta meta) {
        return SPouT.valueOf_bool(v, meta);
    }

    @Substitution(methodName = "valueOf", passAnnotations = true)
    @CompilerDirectives.TruffleBoundary
    public static @JavaType(String.class) StaticObject valueOf_byte(@JavaType(internalName = "B") Object v, @Inject Meta meta) {
        return SPouT.valueOf_byte(v, meta);
    }

    @Substitution(methodName = "valueOf", passAnnotations = true)
    @CompilerDirectives.TruffleBoundary
    public static @JavaType(String.class) StaticObject valueOf_char(@JavaType(internalName = "C") Object v, @Inject Meta meta) {
        return SPouT.valueOf_char(v, meta);
    }

    @Substitution(methodName = "valueOf", passAnnotations = true)
    @CompilerDirectives.TruffleBoundary
    public static @JavaType(String.class) StaticObject valueOf_char_array(
            @JavaType(internalName = "[C") StaticObject v, @Inject Meta meta) {
        return SPouT.valueOf_char_array(v, meta);
    }

    @Substitution(methodName = "valueOf", passAnnotations = true)
    @CompilerDirectives.TruffleBoundary
    public static @JavaType(String.class) StaticObject valueOf_char_array(
            @JavaType(internalName = "[C") StaticObject v,
            @JavaType(internalName = "I") Object offset,
            @JavaType(internalName = "I") Object count,
            @Inject Meta meta) {
        return SPouT.valueOf_char_array(v, offset, count, meta);
    }

    @Substitution(methodName = "valueOf", passAnnotations = true)
    @CompilerDirectives.TruffleBoundary
    public static @JavaType(String.class) StaticObject valueOf_short(@JavaType(internalName = "S") Object v, @Inject Meta meta) {
        return SPouT.valueOf_short(v, meta);
    }

    @Substitution(methodName = "valueOf", passAnnotations = true)
    @CompilerDirectives.TruffleBoundary
    public static @JavaType(String.class) StaticObject valueOf_int(@JavaType(internalName = "I") Object v, @Inject Meta meta) {
        return SPouT.valueOf_int(v, meta);
    }

    @Substitution(methodName = "valueOf", passAnnotations = true)
    @CompilerDirectives.TruffleBoundary
    public static @JavaType(String.class) StaticObject valueOf_long(@JavaType(internalName = "J") Object v, @Inject Meta meta) {
        return SPouT.valueOf_long(v, meta);
    }

    @Substitution(methodName = "valueOf", passAnnotations = true)
    @CompilerDirectives.TruffleBoundary
    public static @JavaType(String.class) StaticObject valueOf_float(@JavaType(internalName = "F") Object v, @Inject Meta meta) {
        return SPouT.valueOf_float(v, meta);
    }

    @Substitution(methodName = "valueOf", passAnnotations = true)
    @CompilerDirectives.TruffleBoundary
    public static @JavaType(String.class) StaticObject valueOf_double(@JavaType(internalName = "D") Object v, @Inject Meta meta) {
        return SPouT.valueOf_double(v, meta);
    }

    @Substitution(hasReceiver = true, passAnnotations = true, methodName="substring")
    public static @JavaType(String.class) StaticObject substring(@JavaType(String.class) StaticObject self,@JavaType(internalName = "I") Object begin, @Inject Meta meta){
        return SPouT.substring(self, begin, meta);
    }

    @Substitution(hasReceiver = true, passAnnotations = true, methodName = "substring")
    public static @JavaType(String.class) StaticObject substring(@JavaType(String.class) StaticObject self,@JavaType(internalName = "I") Object begin, @JavaType(internalName = "I") Object end, @Inject Meta meta){
        return SPouT.substring(self, begin, end, meta);
    }
}
