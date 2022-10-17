package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.SPouT;

@EspressoSubstitutions
public final class Target_java_lang_StringBuffer {


    /*
     * If we do not intercept the constructor, we get side effects from the length comparison in the trace that are
     * irrelevant for the symbolic encoding.
     */
    @Substitution(hasReceiver = true, methodName = "<init>")
    public static void init_string(
            @JavaType(StringBuilder.class) StaticObject self,
            @JavaType(String.class) StaticObject other,
            @Inject Meta meta) {
        SPouT.initStringBuxxString(self, other, meta);
    }

    @Substitution(methodName = "append", hasReceiver = true)
    public static @JavaType(StringBuffer.class) StaticObject append_string(
            @JavaType(StringBuffer.class) StaticObject self,
            @JavaType(String.class) StaticObject string,
            @Inject Meta meta) {
        return SPouT.stringBuXXAppendString(self, string, meta);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject toString(
            @JavaType(StringBuffer.class) StaticObject self,
            @Inject Meta meta) {
        return SPouT.stringBuxxToString(self, meta);
    }

    @Substitution(hasReceiver = true, methodName = "insert")
    public static @JavaType(StringBuffer.class) StaticObject insert_string(@JavaType(StringBuffer.class) StaticObject self,
                                                                     @JavaType(internalName = "I") Object offset,
                                                                     @JavaType(String.class) StaticObject toInsert,
                                                                     @Inject Meta meta){
        return SPouT.stringBuxxInsert(self, offset, toInsert, meta);
    }

    @Substitution(hasReceiver = true, methodName = "insert")
    public static @JavaType(StringBuffer.class) StaticObject insert_char(@JavaType(StringBuffer.class) StaticObject self,
                                                                         @JavaType(internalName = "I") Object offset,
                                                                         @JavaType(internalName = "C") Object toInsert,
                                                                         @Inject Meta meta){
        return SPouT.stringBuxxInsert(self, offset, toInsert, meta);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(internalName = "C") Object charAt(
            @JavaType(StringBuffer.class) StaticObject self,
            @JavaType(internalName = "I") Object index,
            @Inject Meta meta) {
        return SPouT.stringBuxxCharAt(self, index, meta);
    }

    @Substitution(hasReceiver = true)
    public static void getChars(@JavaType(StringBuffer.class) StaticObject self,
                                @JavaType(internalName = "I") Object srcBegin,
                                @JavaType(internalName = "I") Object srcEnd,
                                @JavaType(char[].class) StaticObject dst,
                                @JavaType(internalName = "I") Object dstBegin,
                                @Inject Meta meta){
        SPouT.stringBuxxGetChars(self, srcBegin, srcEnd, dst, dstBegin, meta);
    }
}
