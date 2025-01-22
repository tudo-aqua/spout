package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.AnnotatedVM;
import tools.aqua.spout.AnnotatedValue;
import tools.aqua.spout.Annotations;
import tools.aqua.spout.SPouT;

@EspressoSubstitutions
public final class Target_java_lang_Integer {

    @Substitution
    public static int parseInt(@JavaType(String.class) StaticObject s, @Inject Meta meta){
        return SPouT.parseInt(s, meta);
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(Integer.class) StaticObject valueOf(@JavaType(internalName = "I") Object i, @Inject Meta meta) {
        StaticObject o = meta.java_lang_Integer.allocateInstance();
        meta.java_lang_Integer_value.set(o, AnnotatedValue.value(i));
        AnnotatedVM.setFieldAnnotation(o, meta.java_lang_Integer_value,
                AnnotatedValue.svalue(i));
        return o;
    }
}
