package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.concolic.Concolic;

@EspressoSubstitutions
public class Target_java_lang_Character {

    @Substitution
    public static @Host(Short.class) StaticObject valueOf(@Host(typeName = "C")  Object unboxed, @InjectMeta Meta meta) {
        return Concolic.boxChar(unboxed, meta);
    }
    
}
