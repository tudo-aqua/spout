package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import tools.aqua.spout.SPouT;

import java.io.PrintStream;

@EspressoSubstitutions
public final class Target_java_io_PrintStream {

    @Substitution(hasReceiver = true, methodName = "println")
    public static void println_string(@JavaType(PrintStream.class) StaticObject receiver, @JavaType(String.class) StaticObject string, @Inject Meta meta) {
        original_println(receiver, string, meta);
    }

    // todo: maybe this is too aggresive and some path can be without a boundary?

    @CompilerDirectives.TruffleBoundary
    private static void original_println(StaticObject receiver, StaticObject string, @Inject Meta meta) {
        StaticObject system_out = meta.java_lang_System_out.getAsObject(meta, meta.java_lang_System.getStatics());
        String hostString = meta.toHostString(string);
        if (receiver == system_out) {
            SPouT.log("Warning: removing annotations before writing to System.out.println() ...");
            StaticObject cleanString = meta.toGuestString(hostString);
            meta.java_io_PrintStream_println_obj.invokeMethodVirtual(receiver, cleanString);
        }
        else {
            meta.java_io_PrintStream_println_obj.invokeMethodVirtual(receiver, string);
        }
    }

    // TODO: write(char[] buf)
    // TODO: write(char[] buf, int off, int len)
    // TODO: write(int c)

}
