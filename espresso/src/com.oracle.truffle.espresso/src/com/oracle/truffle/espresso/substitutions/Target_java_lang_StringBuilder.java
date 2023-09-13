package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.AnnotatedValue;
import tools.aqua.spout.SPouT;

//@EspressoSubstitutions
public final class Target_java_lang_StringBuilder {


    /*
     * If we do not intercept the constructor, we get side effects from the length comparison in the trace that are
     * irrelevant for the symbolic encoding.
     */
    //todo: leads to segfault when starting springboot
    //@Substitution(hasReceiver = true, methodName = "<init>")
    public static void init_string(
            @JavaType(StringBuilder.class) StaticObject self,
            @JavaType(String.class) StaticObject other,
            @Inject Meta meta) {
        SPouT.initStringBuxxString(self, other, meta);
    }


    @Substitution(methodName = "append", hasReceiver = true)
    public static @JavaType(StringBuilder.class) StaticObject append_string(
            @JavaType(StringBuilder.class) StaticObject self,
            @JavaType(String.class) StaticObject string,
            @Inject Meta meta) {
        return SPouT.stringBuXXAppendString(self, string, meta);
    }

    @Substitution(methodName = "append", hasReceiver = true, passAnnotations = true)
    public static @JavaType(StringBuilder.class) StaticObject append_char_array_II(
            @JavaType(StringBuilder.class) StaticObject self,
            @JavaType(internalName = "[C") StaticObject chars,
            @JavaType(internalName = "I") Object offset,
            @JavaType(internalName = "I") Object length,
            @Inject Meta meta) {
        return SPouT.stringBuXXAppendString(self, chars, offset, length, meta);
    }

    @Substitution(methodName = "append", hasReceiver = true, passAnnotations = true)
    public static @JavaType(StringBuilder.class) StaticObject append_char(
            @JavaType(StringBuilder.class) StaticObject self,
            @JavaType(internalName = "C") Object string,
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
        Object res = SPouT.stringBuxxCharAt(self, index, meta);
        return res;
    }

    @Substitution(hasReceiver = true, methodName = "getChars", passAnnotations = true)
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

    @Substitution(hasReceiver = true, methodName = "setCharAt", passAnnotations = true)
    public static void setCharAt(
            @JavaType(StringBuilder.class) StaticObject self,
            @JavaType(internalName = "I") Object index,
            @JavaType(internalName = "C") Object ch,
            @Inject Meta meta) {
        SPouT.setBuxxCharAt(self, index, ch, meta);
    }

}
