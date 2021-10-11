package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.concolic.Concolic;

@EspressoSubstitutions
public class Target_java_lang_StringBuilder {

  @Substitution(methodName = "append", hasReceiver = true)
  @TruffleBoundary
  public static @Host(StringBuilder.class) Object append_string(
      @Host(StringBuilder.class) StaticObject self,
      @Host(String.class) StaticObject string,
      @GuestCall(target = "java_lang_AbstractStringBuilder_append_string")
          DirectCallNode originalAppend,
      @GuestCall(target = "java_lang_StringBuilder_toString")
          DirectCallNode originalToString,
      @InjectMeta Meta meta) {
    Concolic.stringBuilderAppendString(self, string, originalToString, meta);
    Object o = originalAppend.call(self, string);
    return o;
  }

  @Substitution(hasReceiver = true)
  public static @Host(String.class) Object toString(
      @Host(StringBuilder.class) StaticObject self,
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
}
