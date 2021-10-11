package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.concolic.Concolic;

@EspressoSubstitutions
public class Target_java_lang_Short {

    @Substitution
    public static @Host(Short.class) StaticObject valueOf(@Host(typeName = "S")  Object unboxed, @InjectMeta Meta meta) {
        return Concolic.boxShort(unboxed, meta);
    }
    
}
