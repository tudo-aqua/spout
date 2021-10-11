package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.concolic.Concolic;

@EspressoSubstitutions
public class Target_java_lang_Byte {

    @Substitution
    public static @Host(Integer.class) StaticObject valueOf(@Host(typeName = "B")  Object unboxed, @InjectMeta Meta meta) {
        return Concolic.boxByte(unboxed, meta);
    }
    
}
