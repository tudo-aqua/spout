package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.concolic.Concolic;

@EspressoSubstitutions
public class Target_java_lang_Integer {

    @Substitution
    public static @Host(Integer.class) StaticObject valueOf(@Host(typeName = "I")  Object unboxed, @InjectMeta Meta meta) {
        // FIXME: better solution might be to simply call constructor Integer(int), which will not use cached values
        return Concolic.boxInteger(unboxed, meta);
    }
    
}
