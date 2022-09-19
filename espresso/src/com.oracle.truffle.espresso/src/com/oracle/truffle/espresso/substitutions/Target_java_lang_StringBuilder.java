package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.SPouT;

@EspressoSubstitutions
public final class Target_java_lang_StringBuilder {

//    /*
//     * This substitute is written following the implementation strategy in the JDK 11.
//     * The more modern JDK 17 has an init (java.lang.String)V; constructor in abstract string builder.
//     * Check for changing this implementation from time to time.
//     */
//    @Substitution(hasReceiver = true, methodName = "<init>")
//    public static void init_string(
//            @JavaType(StringBuilder.class) StaticObject self,
//            @JavaType(String.class) StaticObject other,
//            @GuestCall(target = "java_lang_AbstractStringBuilder_init_int") DirectCallNode superInit,
//            @GuestCall(target = "java_lang_StringBuilder_append") DirectCallNode appendCall,
//            @InjectMeta Meta meta) {
//        String hOther = meta.toHostString(other);
//        superInit.call(self, hOther.length() + 16);
//        appendCall.call(self, other);
//    }
//
//    @Substitution(methodName = "append", hasReceiver = true)
//    @TruffleBoundary
//    public static @Host(StringBuilder.class) Object append_string(
//            @Host(StringBuilder.class) StaticObject self,
//            @Host(String.class) StaticObject string,
//            @GuestCall(target = "java_lang_AbstractStringBuilder_append_string")
//            DirectCallNode originalAppend,
//            @GuestCall(target = "java_lang_StringBuilder_toString") DirectCallNode originalToString,
//            @InjectMeta Meta meta) {
//        Concolic.stringBuilderAppendString(self, string, originalToString, meta);
//        self.pauseConcolicRecording();
//        string.pauseConcolicRecording();
//        Object o = originalAppend.call(self, string);
//        self.continueConcolicRecording();
//        string.continueConcolicRecording();
//        return o;
//    }
//
//    @Substitution(hasReceiver = true)
//    public static @Host(String.class) Object toString(
//            @Host(StringBuilder.class) StaticObject self,
//            @GuestCall(target = "java_lang_AbstractStringBuilder_getValue") DirectCallNode getValue,
//            @GuestCall(target = "java_lang_AbstractStringBuilder_length") DirectCallNode length,
//            @GuestCall(target = "java_lang_AbstractStringBuilder_isLatin1") DirectCallNode isLatin,
//            @GuestCall(target = "java_lang_StringLatin1_newString") DirectCallNode newLatin1String,
//            @GuestCall(target = "java_lang_StringUTF16_newString") DirectCallNode newUTF16String,
//            @InjectMeta Meta meta) {
//        String concrete =
//                (boolean) isLatin.call(self)
//                        ? meta.toHostString(
//                        (StaticObject) newLatin1String.call(getValue.call(self), 0, length.call(self)))
//                        : meta.toHostString(
//                        (StaticObject) newUTF16String.call(getValue.call(self), 0, length.call(self)));
//        return Concolic.stringBuilderToString(self, concrete, meta);
//    }
//
//    @Substitution(hasReceiver = true, methodName = "insert")
//    public static @Host(StringBuilder.class) Object insert_string(
//            @Host(StringBuilder.class) StaticObject self,
//            @Host(typeName = "I") Object offset,
//            @Host(String.class) StaticObject toInsert,
//            @GuestCall(target = "java_lang_AbstractStringBuilder_insert_string") DirectCallNode insert,
//            @GuestCall(target = "java_lang_StringBuilder_toString") DirectCallNode originalToString,
//            @InjectMeta Meta meta) {
//        Concolic.stringBuilderInsert(self, offset, toInsert, insert, originalToString, meta);
//        return self;
//    }

    @Substitution(hasReceiver = true)
    public static @JavaType(internalName = "C") Object charAt(
            @JavaType(StringBuilder.class) StaticObject self,
            @JavaType(internalName = "I") Object index,
            @Inject Meta meta) {
        System.out.println("StringBuilder CharAt");
            return SPouT.stringBuilderCharAt(self, index, meta);
    }

//    @Substitution(hasReceiver = true)
//    public static void getChars(
//            @Host(StringBuilder.class) StaticObject self,
//            @Host(typeName = "I") Object srcBegin,
//            @Host(typeName = "I") Object srcEnd,
//            @Host(char[].class) StaticObject dst,
//            @Host(typeName = "I") Object dstBegin,
//            @GuestCall(target = "java_lang_AbstractStringBuilder_getChars") DirectCallNode superGetChars,
//            @InjectMeta Meta meta) {
//        if (srcBegin instanceof AnnotatedValue
//                || srcEnd instanceof AnnotatedValue
//                || dst.isConcolic()
//                || dstBegin instanceof AnnotatedValue) {
//            Concolic.stopRecording("symbolic getChars is not supported", meta);
//        }
//        superGetChars.call(self, srcBegin, srcEnd, dst, dstBegin);
//    }
//
//    @Substitution(hasReceiver = true)
//    public static @Host(typeName = "I") Object length(
//            @Host(StringBuilder.class) StaticObject self,
//            @GuestCall(target = "java_lang_AbstractStringBuilder_length") DirectCallNode superLength,
//            @InjectMeta Meta meta) {
//        int cresult = (int) superLength.call(self);
//        if(self.isConcolic()){
//            return Concolic.stringBufferLength(self, cresult, meta);
//        }
//        return cresult;
//    }
}
