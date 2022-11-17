package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.SPouT;

@EspressoSubstitutions
public final class Target_java_lang_Integer {

    @Substitution
    public static int parseInt(@JavaType(String.class) StaticObject s, @Inject Meta meta){
        return SPouT.parseInt(s, meta);
    }
}
