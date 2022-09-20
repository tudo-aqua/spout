package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.SPouT;

@EspressoSubstitutions
public final class Target_java_lang_StringBuilder {
    @Substitution(hasReceiver = true, methodName = "<init>")
    public static void init_string(
            @JavaType(StringBuilder.class) StaticObject self,
            @JavaType(String.class) StaticObject other,
            @Inject Meta meta) {
        SPouT.stringBuilder_init_string(self, other, meta);
    }

    @Substitution(methodName = "append", hasReceiver = true)
    @TruffleBoundary
    public static @JavaType(StringBuilder.class) StaticObject append_string(
            @JavaType(StringBuilder.class) StaticObject self,
            @JavaType(String.class) StaticObject string,
            @Inject Meta meta) {
        return SPouT.stringBuilderAppendString(self, string, meta);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) Object toString(
            @JavaType(StringBuilder.class) StaticObject self,
            @Inject Meta meta) {
        return SPouT.stringBuilderToString(self, meta);
    }

    @Substitution(hasReceiver = true, methodName = "insert")
    public static @JavaType(StringBuilder.class) StaticObject insert_string(
            @JavaType(StringBuilder.class) StaticObject self,
            @JavaType(internalName = "I") Object offset,
            @JavaType(String.class) StaticObject toInsert,
            @Inject Meta meta) {
        return SPouT.stringBuilderInsert(self, offset, toInsert, meta);
    }

    @Substitution(hasReceiver = true)
    @TruffleBoundary
    public static @JavaType(internalName = "C") Object charAt(
            @JavaType(StringBuilder.class) StaticObject self,
            @JavaType(internalName = "I") Object index,
            @Inject Meta meta) {
        return SPouT.stringBuilderCharAt(self, index, meta);
    }

    @Substitution(hasReceiver = true)
    public static void getChars(
            @JavaType(StringBuilder.class) StaticObject self,
            @JavaType(internalName = "I") Object srcBegin,
            @JavaType(internalName = "I") Object srcEnd,
            @JavaType(char[].class) StaticObject dst,
            @JavaType(internalName = "I") Object dstBegin,
            @Inject Meta meta) {
        SPouT.stringBuilderGetChars(self, srcBegin, srcEnd, dst, dstBegin, meta);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(internalName = "I") Object length(
            @JavaType(StringBuilder.class) StaticObject self,
            @Inject Meta meta) {
        return SPouT.stringBuilderLength(self, meta);
    }
}
