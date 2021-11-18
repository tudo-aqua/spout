package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import javax.management.OperationsException;
import javax.print.DocFlavor.STRING;
import tools.aqua.concolic.Concolic;

@EspressoSubstitutions
public final class Target_java_lang_StringBuffer {
    @Substitution(methodName = "append", hasReceiver = true)
    @TruffleBoundary
    public static @Host(StringBuffer.class) Object append_string(
        @Host(StringBuffer.class) StaticObject self,
        @Host(String.class) StaticObject string,
        @GuestCall(target = "java_lang_AbstractStringBuilder_append_string")
            DirectCallNode originalAppend,
        @GuestCall(target = "java_lang_StringBuffer_toString")
            DirectCallNode originalToString,
        @InjectMeta Meta meta) {
      System.out.println("Append called on StringBuffer with symbolic arg: " + string.getConcolicId());
      Concolic.stringBuilderAppendString(self, string, originalToString, meta);
      System.out.println("String Buffer after concolic: " + self.getConcolicId());
      Object o = originalAppend.call(self, string);
      return o;
    }

    @Substitution(hasReceiver = true)
    public static @Host(String.class) Object toString(
        @Host(StringBuffer.class) StaticObject self,
        @GuestCall(target = "java_lang_AbstractStringBuilder_getValue") DirectCallNode getValue,
        @GuestCall(target = "java_lang_AbstractStringBuilder_length") DirectCallNode length,
        @GuestCall(target = "java_lang_AbstractStringBuilder_isLatin1") DirectCallNode isLatin,
        @GuestCall(target = "java_lang_StringLatin1_newString") DirectCallNode newLatin1String,
        @GuestCall(target = "java_lang_StringUTF16_newString") DirectCallNode newUTF16String,
        @InjectMeta Meta meta) {
      String concrete =
          (boolean) isLatin.call(self)
              ? meta.toHostString(
              (StaticObject) newLatin1String.call(getValue.call(self), 0, length.call(self)))
              : meta.toHostString((StaticObject) newUTF16String.call(getValue.call(self), 0, length.call(self)));
      return Concolic.stringBuilderToString(self, concrete, meta);
    }

    @Substitution(hasReceiver = true, methodName = "insert")
    @TruffleBoundary
    public static @Host(StringBuffer.class) Object insert_string(@Host(StringBuffer.class) StaticObject self, @Host(typeName = "I") Object offset, @Host(String.class) StaticObject toInsert,
        @GuestCall(target="java_lang_AbstractStringBuilder_insert_string") DirectCallNode insert,
        @GuestCall(target = "java_lang_StringBuffer_toString")
            DirectCallNode originalToString, @InjectMeta Meta meta){
      System.out.println("stringBuffer insert called");
      Concolic.stringBuilderInsert(self, offset, toInsert, insert, originalToString, meta);
      return self;
    }

  @Substitution(hasReceiver = true, methodName = "insert")
  @TruffleBoundary
  public static @Host(StringBuffer.class) Object insert_char(@Host(StringBuffer.class) StaticObject self, @Host(typeName = "I") Object offset, @Host(typeName = "C") Object toInsert,
      @GuestCall(target="java_lang_AbstractStringBuilder_insert_string") DirectCallNode insert,
      @GuestCall(target = "java_lang_StringBuffer_toString")
          DirectCallNode originalToString, @InjectMeta Meta meta){
    System.out.println("stringBuffer insert called: " + self.isConcolic() +  "concolicID: " + self.getConcolicId());
    Concolic.stringBuilderInsert(self, offset, toInsert, insert, originalToString, meta);
    System.out.println("StringBuffer result: " + originalToString.call(self) + " with ID: " + self.getConcolicId());
    return self;
  }
}
