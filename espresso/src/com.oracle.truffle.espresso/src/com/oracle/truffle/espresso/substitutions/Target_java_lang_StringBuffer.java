package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.SPouT;

@EspressoSubstitutions
public class Target_java_lang_StringBuffer {

    @Substitution(methodName = "append", hasReceiver = true)
    @CompilerDirectives.TruffleBoundary
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
