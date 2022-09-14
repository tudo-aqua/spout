package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.SPouT;

@EspressoSubstitutions
public final class Target_java_lang_String {
    @Substitution(hasReceiver = true)
    @CompilerDirectives.TruffleBoundary
    public static @JavaType(internalName = "Z") Object contains(@JavaType(String.class) StaticObject self,
                                                                  @JavaType(CharSequence.class) StaticObject s,
                                                                  @Inject Meta meta){
        if(StaticObject.isNull(self) || StaticObject.isNull(s)){
            return false;
        }
        return SPouT.stringContains(self, s, meta);
    }
}
