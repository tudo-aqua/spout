package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import tools.aqua.concolic.Concolic;

@EspressoSubstitutions
public class Target_tools_aqua_concolic_Verifier {

    @Substitution(hasReceiver = false)
    public static @Host(typeName = "I") Object nondetInt() {
        return Concolic.nextSymbolicInt();
    }

    @Substitution(hasReceiver = false)
    public static @Host(String.class) StaticObject nondetString(@InjectMeta Meta meta) {
        return Concolic.nextSymbolicString(meta);
    }
}
