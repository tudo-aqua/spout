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
public final class Target_java_lang_Float {
    @Substitution(passAnnotations  = true)
    public static @JavaType(internalName = "I") Object floatToRawIntBits(@JavaType(internalName = "F") Object value, @Inject Meta meta) {
        return SPouTNumeric.floatToRawIntBits(value, meta);
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(internalName = "F") Object intBitsToFloat(@JavaType(internalName = "I") Object bits, @Inject Meta meta) {
        return SPouTNumeric.intBitsToFloat(bits, meta);
    }

    @Substitution(passAnnotations = true)
    public static @JavaType(internalName = "I") Object floatToIntBits(@JavaType(internalName = "F") Object f, @Inject Meta meta){
        return SPouTNumeric.floatToIntBits(f, meta);
    }


    @Substitution
    public static @JavaType(internalName = "F") Object parseFloat(@JavaType(String.class)StaticObject s, @Inject Meta meta){
        return SPouT.parseFloat(s, meta);
    }

    @Substitution(hasReceiver = true, methodName = "toString")
    public static @JavaType(String.class) StaticObject toStringSelf (@JavaType(Float.class) StaticObject f, @Inject Meta meta){
        return SPouTNumeric.floatToString(f, meta);
    }

    @Substitution(methodName = "toString")
    public static @JavaType(String.class) StaticObject toStringParam (@JavaType(internalName = "F") Object f, @Inject Meta meta){
        return SPouTNumeric.floatFloatToString(f, meta);
    }

    @Substitution(hasReceiver = true, methodName = "toHexString")
    public static @JavaType(String.class) StaticObject toHexString (@JavaType(Float.class) StaticObject f, @Inject Meta meta){
        return SPouTNumeric.floatToHexString(f, meta);
    }


}
