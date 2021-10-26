package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import java.io.PrintWriter;

@EspressoSubstitutions
public class Target_java_io_PrintWriter {

  @Substitution(hasReceiver = true, methodName = "print")
  @TruffleBoundary
  public static void print_string(@Host(PrintWriter.class) StaticObject self, @Host(String.class) StaticObject param, @InjectMeta Meta meta){
    System.out.println("print_string called");
  }
}
