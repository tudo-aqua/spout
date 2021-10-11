package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.concolic.Concolic;

@EspressoSubstitutions
public class Target_java_lang_Long {

    @Substitution
    public static @Host(Long.class) StaticObject valueOf(@Host(typeName = "J")  Object unboxed, @InjectMeta Meta meta) {
        return Concolic.boxLong(unboxed, meta);
    }
    
}
