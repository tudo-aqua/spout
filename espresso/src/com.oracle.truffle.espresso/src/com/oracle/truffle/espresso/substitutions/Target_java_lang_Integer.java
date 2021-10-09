package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.concolic.Concolic;

@EspressoSubstitutions
public class Target_java_lang_Integer {

    @Substitution
    public static @Host(Integer.class) StaticObject valueOf(@Host(typeName = "I")  Object unboxed, @InjectMeta Meta meta) {
        StaticObject boxed = meta.java_lang_Integer.allocateInstance();
        return Concolic.boxInteger(unboxed, meta);
    }

    @Substitution(hasReceiver = true)
    public static @Host(typeName = "I") Object valueOf(@Host(Integer.class) StaticObject self, @InjectMeta Meta meta) {
        return Concolic.unboxInteger(self, meta);
    }
}
