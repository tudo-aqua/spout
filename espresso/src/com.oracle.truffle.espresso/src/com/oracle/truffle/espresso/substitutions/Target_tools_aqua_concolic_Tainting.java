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


import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.AnnotatedValue;
import tools.aqua.spout.SPouT;

@EspressoSubstitutions
public final class Target_tools_aqua_concolic_Tainting {

    // taint o with color
    @Substitution(passAnnotations = true, methodName = "taint")
    public static @JavaType(internalName = "I") Object taint_int(@JavaType(internalName = "I") Object o, @JavaType(internalName = "I") Object color) {
        return SPouT.taint(o, AnnotatedValue.value(color));
    }

    @Substitution(passAnnotations = true, methodName = "taint")
    public static @JavaType(internalName = "Z") Object taint_bool(@JavaType(internalName = "Z") Object o, @JavaType(internalName = "I") Object color) {
        return SPouT.taint(o, AnnotatedValue.value(color));
    }

    @Substitution(passAnnotations = true, methodName = "taint")
    public static @JavaType(internalName = "D") Object taint_double(@JavaType(internalName = "D") Object o, @JavaType(internalName = "I") Object color) {
        return SPouT.taint(o, AnnotatedValue.value(color));
    }

    @Substitution(passAnnotations = false, methodName = "taint")
    public static @JavaType(String.class) StaticObject taint_string(@JavaType(String.class) StaticObject o, int color) {
        SPouT.taintObject(o, color);
        return o;
    }

    // TODO: implement missing taint and check methods!
    // TODO: remove stop analysis everywhere
    
    /*
    // clean o from taint of color
    @Substitution(hasReceiver = false, methodName = "clean")
    public static @Host(typeName = "I") Object clean_int(@Host(typeName = "I") Object o, @Host(typeName = "I") Object color) {
        return TaintAnalysis.clean(o, color, JavaKind.Int);
    }
*/
    // check if o is tainted with color
    @Substitution(passAnnotations = true, methodName = "check")
    public static void check_int(@JavaType(internalName = "I") Object o, @JavaType(internalName = "I") Object color) {
        SPouT.checkTaint(o, AnnotatedValue.value(color));
    }

    @Substitution(passAnnotations = true, methodName = "check")
    public static void check_bool(@JavaType(internalName = "Z") Object o, @JavaType(internalName = "I") Object color) {
        SPouT.checkTaint(o, AnnotatedValue.value(color));
    }

    @Substitution(passAnnotations = false, methodName = "check")
    public static void check_string(@JavaType(String.class) StaticObject o, int color, @Inject Meta meta) {
        SPouT.checkTaintObject(o, color, meta);
    }

    @Substitution(passAnnotations = false, methodName = "check")
    public static void check_object(@JavaType(Object.class) StaticObject o, int color, @Inject Meta meta) {
        SPouT.checkTaintObject(o, color, meta);
    }


    @Substitution(hasReceiver = false)
    public static void stopAnalysis() {
        SPouT.stopAnalysis();
    }
}
