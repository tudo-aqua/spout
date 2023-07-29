/*
 * Copyright (c) 2021 Automated Quality Assurance Group, TU Dortmund University.
 * All rights reserved. DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE
 * HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact the Automated Quality Assurance Group, TU Dortmund University
 * or visit https://aqua.engineering if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.SPouT;

@EspressoSubstitutions
public final class Target_tools_aqua_concolic_Verifier {

    //    public static void assume(boolean condition)
    @Substitution(hasReceiver = false, passAnnotations = true)
    @CompilerDirectives.TruffleBoundary
    public static void assume(@JavaType(internalName = "Z") Object condition, @Inject Meta meta) {
        SPouT.assume( condition, meta );
    }

    @Substitution(hasReceiver = false, passAnnotations = true)
    public static @JavaType(internalName = "Z") Object nondetBoolean() {
        return SPouT.nextSymbolicBoolean();
    }

    @Substitution(hasReceiver = false, passAnnotations = true)
    public static @JavaType(internalName = "B") Object nondetByte() {
        return SPouT.nextSymbolicByte();
    }

    @Substitution(hasReceiver = false, passAnnotations = true)
    public static @JavaType(internalName = "C") Object nondetChar() {
        return SPouT.nextSymbolicChar();
    }

    @Substitution(hasReceiver = false, passAnnotations = true)
    public static @JavaType(internalName = "S") Object nondetShort() {
        return SPouT.nextSymbolicShort();
    }

    //@Substitution(passAnnotations = true)
    public static @JavaType(internalName = "I") Object nondetInt() {
        return SPouT.nextSymbolicInt();
    }

    //    public static long nondetLong()
    @Substitution(hasReceiver = false, passAnnotations = true)
    public static @JavaType(internalName = "J") Object nondetLong() {
        return SPouT.nextSymbolicLong();
    }

    //    public static float nondetFloat()
    @Substitution(hasReceiver = false, passAnnotations = true)
    public static @JavaType(internalName = "F") Object nondetFloat() {
        return SPouT.nextSymbolicFloat();
    }

    //    public static double nondetDouble()
    @Substitution(hasReceiver = false, passAnnotations = true)
    public static @JavaType(internalName = "D") Object nondetDouble() {
        return SPouT.nextSymbolicDouble();
    }

    //    public static String nondetString()
    @Substitution(hasReceiver = false)
    public static @JavaType(String.class) StaticObject nondetString(@Inject Meta meta) {
        return SPouT.nextSymbolicString(meta);
    }

}
