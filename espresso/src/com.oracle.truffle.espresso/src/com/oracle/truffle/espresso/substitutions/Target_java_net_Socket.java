package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.concolic.Concolic;

@EspressoSubstitutions
public class Target_java_net_Socket {

    @Substitution(methodName = "<init>")
    @CompilerDirectives.TruffleBoundary
    public static void __init(@Host(String.class) Object server, @Host(typeName = "I") Object port, @InjectMeta Meta meta) {
        Concolic.stopRecording("Networking not supported yet.", meta);
    }

}
