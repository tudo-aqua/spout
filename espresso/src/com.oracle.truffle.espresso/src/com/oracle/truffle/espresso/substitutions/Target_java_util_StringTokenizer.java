package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import tools.aqua.spout.SPouT;

/*
 * The loops of StringTokenizer over the characters of the string: with symbolic positions (like String.split),
 * instead of decisions on every character. The public methods remain the JDK code.
 */
@EspressoSubstitutions
public final class Target_java_util_StringTokenizer {

    @Substitution(hasReceiver = true, passAnnotations = true, methodName = "skipDelimiters")
    public static @JavaType(internalName = "I") Object skipDelimiters(
            @JavaType(internalName = "Ljava/util/StringTokenizer;") StaticObject self,
            @JavaType(internalName = "I") Object startPos,
            @Inject Meta meta) {
        return SPouT.tokenizerSkipDelimiters(self, startPos, meta);
    }

    @Substitution(hasReceiver = true, passAnnotations = true, methodName = "scanToken")
    public static @JavaType(internalName = "I") Object scanToken(
            @JavaType(internalName = "Ljava/util/StringTokenizer;") StaticObject self,
            @JavaType(internalName = "I") Object startPos,
            @Inject Meta meta) {
        return SPouT.tokenizerScanToken(self, startPos, meta);
    }
}
