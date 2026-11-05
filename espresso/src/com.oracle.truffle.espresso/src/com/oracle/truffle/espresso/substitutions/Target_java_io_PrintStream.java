package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import tools.aqua.spout.SPouT;

import com.oracle.truffle.espresso.descriptors.EspressoSymbols;
import com.oracle.truffle.espresso.impl.Method;

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

    @Substitution(hasReceiver = true, methodName = "println", passAnnotations = true)
    public static void println_boolean(@JavaType(PrintStream.class) StaticObject receiver, @JavaType(internalName = "Z") Object v, @Inject Meta meta) {
        printValue(receiver, v, 'Z', true, meta);
    }

    @Substitution(hasReceiver = true, methodName = "print", passAnnotations = true)
    public static void print_boolean(@JavaType(PrintStream.class) StaticObject receiver, @JavaType(internalName = "Z") Object v, @Inject Meta meta) {
        printValue(receiver, v, 'Z', false, meta);
    }

    @Substitution(hasReceiver = true, methodName = "println", passAnnotations = true)
    public static void println_char(@JavaType(PrintStream.class) StaticObject receiver, @JavaType(internalName = "C") Object v, @Inject Meta meta) {
        printValue(receiver, v, 'C', true, meta);
    }

    @Substitution(hasReceiver = true, methodName = "print", passAnnotations = true)
    public static void print_char(@JavaType(PrintStream.class) StaticObject receiver, @JavaType(internalName = "C") Object v, @Inject Meta meta) {
        printValue(receiver, v, 'C', false, meta);
    }

    @Substitution(hasReceiver = true, methodName = "println", passAnnotations = true)
    public static void println_int(@JavaType(PrintStream.class) StaticObject receiver, @JavaType(internalName = "I") Object v, @Inject Meta meta) {
        printValue(receiver, v, 'I', true, meta);
    }

    @Substitution(hasReceiver = true, methodName = "print", passAnnotations = true)
    public static void print_int(@JavaType(PrintStream.class) StaticObject receiver, @JavaType(internalName = "I") Object v, @Inject Meta meta) {
        printValue(receiver, v, 'I', false, meta);
    }

    @Substitution(hasReceiver = true, methodName = "println", passAnnotations = true)
    public static void println_long(@JavaType(PrintStream.class) StaticObject receiver, @JavaType(internalName = "J") Object v, @Inject Meta meta) {
        printValue(receiver, v, 'J', true, meta);
    }

    @Substitution(hasReceiver = true, methodName = "print", passAnnotations = true)
    public static void print_long(@JavaType(PrintStream.class) StaticObject receiver, @JavaType(internalName = "J") Object v, @Inject Meta meta) {
        printValue(receiver, v, 'J', false, meta);
    }

    @Substitution(hasReceiver = true, methodName = "println", passAnnotations = true)
    public static void println_float(@JavaType(PrintStream.class) StaticObject receiver, @JavaType(internalName = "F") Object v, @Inject Meta meta) {
        printValue(receiver, v, 'F', true, meta);
    }

    @Substitution(hasReceiver = true, methodName = "print", passAnnotations = true)
    public static void print_float(@JavaType(PrintStream.class) StaticObject receiver, @JavaType(internalName = "F") Object v, @Inject Meta meta) {
        printValue(receiver, v, 'F', false, meta);
    }

    @Substitution(hasReceiver = true, methodName = "println", passAnnotations = true)
    public static void println_double(@JavaType(PrintStream.class) StaticObject receiver, @JavaType(internalName = "D") Object v, @Inject Meta meta) {
        printValue(receiver, v, 'D', true, meta);
    }

    @Substitution(hasReceiver = true, methodName = "print", passAnnotations = true)
    public static void print_double(@JavaType(PrintStream.class) StaticObject receiver, @JavaType(internalName = "D") Object v, @Inject Meta meta) {
        printValue(receiver, v, 'D', false, meta);
    }

    @Substitution(hasReceiver = true, methodName = "print")
    public static void print_string(@JavaType(PrintStream.class) StaticObject receiver, @JavaType(String.class) StaticObject string, @Inject Meta meta) {
        print(receiver, string, false, meta);
    }

    /**
     * print / println of a primitive value: on System.out and System.err its concrete string (the output does not
     * influence the program: no symbolic string is built), on other streams its (symbolic) String.valueOf.
     */
    @CompilerDirectives.TruffleBoundary
    private static void printValue(StaticObject receiver, Object v, char kind, boolean newline, Meta meta) {
        StaticObject string;
        if (isStandardStream(receiver, meta)) {
            string = meta.toGuestString(SPouT.concreteString(v, kind));
        } else {
            string = switch (kind) {
                case 'Z' -> SPouT.valueOf_bool(v, meta);
                case 'C' -> SPouT.valueOf_char(v, meta);
                case 'I' -> SPouT.string_valueOf_int(v, meta);
                case 'J' -> SPouT.valueOf_long(v, meta);
                case 'F' -> SPouT.valueOf_float(v, meta);
                default -> SPouT.valueOf_double(v, meta);
            };
        }
        print(receiver, string, newline, meta);
    }

    private static boolean isStandardStream(StaticObject receiver, Meta meta) {
        return receiver == meta.java_lang_System_out.getAsObject(meta, meta.java_lang_System.getStatics())
                || receiver == meta.java_lang_System_err.getAsObject(meta, meta.java_lang_System.getStatics());
    }

    /** prints a string through print(Object) / println(Object), without annotations on System.out and System.err */
    @CompilerDirectives.TruffleBoundary
    private static void print(StaticObject receiver, StaticObject string, boolean newline, Meta meta) {
        if (isStandardStream(receiver, meta) && !StaticObject.isNull(string)) {
            string = meta.toGuestString(meta.toHostString(string));
        }
        Method m = newline ? meta.java_io_PrintStream_println_obj
                : meta.java_io_PrintStream.lookupDeclaredMethod(EspressoSymbols.Names.print, EspressoSymbols.Signatures._void_Object);
        m.invokeMethodVirtual(receiver, string);
    }

    // TODO: write(char[] buf)
    // TODO: write(char[] buf, int off, int len)
    // TODO: write(int c)

}
