package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.SPouT;

@EspressoSubstitutions
public final class Target_java_lang_StringBuilder {
    /*
     * It is not necessary to substitute the constructor,
     * as the super implementation calls append_string and this method
     * is substituted.
     */
    @Substitution(methodName = "append", hasReceiver = true)
    public static @JavaType(StringBuilder.class) StaticObject append_string(
            @JavaType(StringBuilder.class) StaticObject self,
            @JavaType(String.class) StaticObject string,
            @Inject Meta meta) {
        return SPouT.stringBuXXAppendString(self, string, meta);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject toString(
            @JavaType(StringBuilder.class) StaticObject self,
            @Inject Meta meta) {
        return SPouT.stringBuxxToString(self, meta);
    }

    @Substitution(hasReceiver = true, methodName = "insert")
    public static @JavaType(StringBuilder.class) StaticObject insert_string(
            @JavaType(StringBuilder.class) StaticObject self,
            @JavaType(internalName = "I") Object offset,
            @JavaType(String.class) StaticObject toInsert,
            @Inject Meta meta) {
        return SPouT.stringBuxxInsert(self, offset, toInsert, meta);
    }

    @Substitution(hasReceiver = true, methodName = "insert")
    public static @JavaType(StringBuilder.class) StaticObject insert_char(
            @JavaType(StringBuilder.class) StaticObject self,
            @JavaType(internalName = "I") Object offset,
            @JavaType(internalName = "C") Object toInsert,
            @Inject Meta meta) {
        return SPouT.stringBuxxInsert(self, offset, toInsert, meta);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(internalName = "C") Object charAt(
            @JavaType(StringBuilder.class) StaticObject self,
            @JavaType(internalName = "I") Object index,
            @Inject Meta meta) {
        return SPouT.stringBuxxCharAt(self, index, meta);
    }

    @Substitution(hasReceiver = true)
    public static void getChars(
            @JavaType(StringBuilder.class) StaticObject self,
            @JavaType(internalName = "I") Object srcBegin,
            @JavaType(internalName = "I") Object srcEnd,
            @JavaType(char[].class) StaticObject dst,
            @JavaType(internalName = "I") Object dstBegin,
            @Inject Meta meta) {
        SPouT.stringBuxxGetChars(self, srcBegin, srcEnd, dst, dstBegin, meta);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(internalName = "I") Object length(
            @JavaType(StringBuilder.class) StaticObject self,
            @Inject Meta meta) {
        return SPouT.stringBuxxLength(self, meta);
    }
}
