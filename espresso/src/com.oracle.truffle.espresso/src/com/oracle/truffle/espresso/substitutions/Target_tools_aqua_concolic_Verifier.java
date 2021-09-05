package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import tools.aqua.concolic.AnnotatedValue;
import tools.aqua.concolic.Concolic;

@EspressoSubstitutions
public class Target_tools_aqua_concolic_Verifier {

    //    public static void assume(boolean condition)
    @Substitution(hasReceiver = false)
    @CompilerDirectives.TruffleBoundary
    public static void assume(@Host(typeName = "Z") Object condition, @InjectMeta Meta meta) {
        boolean cont = (condition instanceof AnnotatedValue) ? ((AnnotatedValue)condition).asBoolean() : (boolean) condition;
        if (!cont) {
            Concolic.stopRecording("assumption violation", meta);
        }
    }

    //    public static boolean nondetBoolean()
    @Substitution(hasReceiver = false)
    @CompilerDirectives.TruffleBoundary
    public static @Host(typeName = "Z") Object nondetBoolean(@InjectMeta Meta meta) {
        Concolic.stopRecording("not supported", meta);
        // never reached ...
        return false;
    }

    //    public static byte nondetByte()

    //    public static char nondetChar()

    //    public static short nondetShort()

    //    public static int nondetInt()
    @Substitution(hasReceiver = false)
    public static @Host(typeName = "I") Object nondetInt() {
        return Concolic.nextSymbolicInt();
    }

    //    public static long nondetLong()

    //    public static float nondetFloat()

    //    public static double nondetDouble()

    //    public static String nondetString()
    @Substitution(hasReceiver = false)
    public static @Host(String.class) StaticObject nondetString(@InjectMeta Meta meta) {
        return Concolic.nextSymbolicString(meta);
    }


}
