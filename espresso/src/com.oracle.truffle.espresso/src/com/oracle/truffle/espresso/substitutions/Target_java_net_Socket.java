package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.meta.Meta;
import tools.aqua.spout.SPouT;

@EspressoSubstitutions
public final class Target_java_net_Socket {
    //@Substitution(methodName = "<init>")
    public static void __init(@JavaType(String.class) Object server, @JavaType(internalName = "I") Object port, @Inject Meta meta) {
        SPouT.stopRecording("Networking not supported yet.", meta);
    }
}
