/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.SPouT;
import tools.aqua.spout.SPouTNumeric;

/**
 * These substitutions are just for performance. Directly uses the optimized host intrinsics
 * avoiding expensive guest native calls.
 */
@EspressoSubstitutions
public final class Target_java_lang_Double {

    @Substitution(passAnnotations = true)
    public static @JavaType(internalName = "D") Object longBitsToDouble(@JavaType(internalName = "J") Object bits,@Inject Meta meta) {
        return SPouTNumeric.doubleLongBitsToDouble(bits, meta);
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(internalName = "D") Object doubleToRawLongBits(@JavaType(internalName = "D") Object value, @Inject Meta meta) {
        return SPouTNumeric.doubleToRawLongBits(value, meta);
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(internalName = "J") Object doubleToLongBits(@JavaType(internalName = "D") Object value, @Inject Meta meta) {
        return SPouTNumeric.doubleToRawLongBits(value, meta);
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(internalName = "D") Object parseDouble(@JavaType(String.class) StaticObject s, @Inject Meta meta) {
        return SPouT.parseDouble(s, meta);
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(String.class) StaticObject toString(@JavaType(internalName = "D") Object value,
                                                                @Inject Meta meta) {
        return SPouTNumeric.doubleToString(value, meta);
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(String.class) StaticObject toHexString(@JavaType(internalName = "D") Object value, @Inject Meta meta) {
        return SPouTNumeric.doubleToHexString(value, meta);
    }

    @Substitution(methodName = "valueOf", passAnnotations = true)
    public static @JavaType(Double.class) StaticObject valueOfDouble(@JavaType(internalName = "D") Object value, @Inject Meta meta){
        return SPouTNumeric.doubleValueOfDouble(value, meta);
    }

    @Substitution(hasReceiver = true, passAnnotations = true)
    public static @JavaType(internalName = "S") Object shortValue(@JavaType(Double.class) StaticObject d,  @Inject Meta meta){
        return SPouTNumeric.doubleToShort(d, meta);
    }
}
