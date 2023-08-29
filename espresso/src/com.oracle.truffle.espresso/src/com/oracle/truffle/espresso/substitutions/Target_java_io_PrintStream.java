package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.SPouT;

import java.io.PrintStream;

@EspressoSubstitutions
public final class Target_java_io_PrintStream {

    @Substitution(hasReceiver = true, methodName = "println")
    public static void println_string(@JavaType(PrintStream.class) StaticObject receiver, @JavaType(String.class) StaticObject string, @Inject Meta meta) {
        StaticObject system_out = meta.java_lang_System_out.getAsObject(meta, meta.java_lang_System.getStatics());
        String hostString = meta.toHostString(string);
        if (receiver == system_out) {
            SPouT.logDuringAnalysis("Warning: removing annotations before writing to System.out.println() ...");
            StaticObject cleanString = meta.toGuestString(hostString);
            meta.java_io_PrintStream_println_obj.invokeMethod(receiver, new Object[] { cleanString });
        }
        else {
            meta.java_io_PrintStream_println_obj.invokeMethod(receiver, new Object[] { string });
        }
    }

    // TODO: write(char[] buf)
    // TODO: write(char[] buf, int off, int len)
    // TODO: write(int c)

}
