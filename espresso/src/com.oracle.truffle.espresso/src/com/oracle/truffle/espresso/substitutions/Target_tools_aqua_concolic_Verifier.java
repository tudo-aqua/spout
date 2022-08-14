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

import tools.aqua.spout.AnnotatedValue;
import tools.aqua.spout.SPouT;

@EspressoSubstitutions
public final class Target_tools_aqua_concolic_Verifier {
/*
    //    public static void assume(boolean condition)
    @Substitution(hasReceiver = false)
    @CompilerDirectives.TruffleBoundary
    public static void assume(@Host(typeName = "Z") Object condition, @InjectMeta Meta meta) {
        Analysis.getInstance().assume( condition, meta );
    }

    //    public static boolean nondetBoolean()
    @Substitution(hasReceiver = false)
    public static @Host(typeName = "Z") Object nondetBoolean(@InjectMeta Meta meta) {
        return Analysis.getInstance().getConfig().nextSymbolicBoolean();
    }

    //    public static byte nondetByte()
    @Substitution(hasReceiver = false)
    public static @Host(typeName = "B") Object nondetByte(@InjectMeta Meta meta) {
        return Analysis.getInstance().getConfig().nextSymbolicByte();
    }

    //    public static char nondetChar()
    @Substitution(hasReceiver = false)
    public static @Host(typeName = "C") Object nondetChar(@InjectMeta Meta meta) {
        return Analysis.getInstance().getConfig().nextSymbolicChar();
    }

    //    public static short nondetShort()
    @Substitution(hasReceiver = false)
    public static @Host(typeName = "S") Object nondetShort(@InjectMeta Meta meta) {
        return Analysis.getInstance().getConfig().nextSymbolicShort();
    }
*/
    //    public static int nondetInt()
    @Substitution(hasReceiver = false)
    public static @JavaType(internalName = "I") Object nondetInt() {
        return SPouT.nextSymbolicInt();
    }
/*
    //    public static long nondetLong()
    @Substitution(hasReceiver = false)
    public static @Host(typeName = "J") Object nondetLong(@InjectMeta Meta meta) {
        return Analysis.getInstance().getConfig().nextSymbolicLong();
    }

    //    public static float nondetFloat()
    @Substitution(hasReceiver = false)
    public static @Host(typeName = "F") Object nondetFloat(@InjectMeta Meta meta) {
        return Analysis.getInstance().getConfig().nextSymbolicFloat();
    }

    //    public static double nondetDouble()
    @Substitution(hasReceiver = false)
    public static @Host(typeName = "D") Object nondetDouble(@InjectMeta Meta meta) {
        return Analysis.getInstance().getConfig().nextSymbolicDouble();
    }

    //    public static String nondetString()
    @Substitution(hasReceiver = false)
    public static @Host(String.class) StaticObject nondetString(@InjectMeta Meta meta) {
        return Analysis.getInstance().getConfig().nextSymbolicString(meta);
    }
*/
}
