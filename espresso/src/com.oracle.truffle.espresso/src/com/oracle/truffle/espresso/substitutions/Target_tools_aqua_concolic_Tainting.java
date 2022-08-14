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
import tools.aqua.spout.SPouT;

@EspressoSubstitutions
public final class Target_tools_aqua_concolic_Tainting {

    // taint o with color
    @Substitution(hasReceiver = false, methodName = "taint")
    public static @JavaType(internalName = "I") Object taint_int(@JavaType(internalName = "I") Object o, int color) {
        return SPouT.taint(o, color);
    }
/*
    // clean o from taint of color
    @Substitution(hasReceiver = false, methodName = "clean")
    public static @Host(typeName = "I") Object clean_int(@Host(typeName = "I") Object o, @Host(typeName = "I") Object color) {
        return TaintAnalysis.clean(o, color, JavaKind.Int);
    }
*/
    // check if o is tainted with color
    @Substitution(hasReceiver = false, methodName = "check")
    public static void check_int(@JavaType(internalName = "I") Object o, int color) {
        SPouT.checkTaint(o, color);
    }
/*
    @Substitution(hasReceiver = false)
    public static void stopAnalysis() {
        TaintAnalysis.stopTaintAnalysis();
    }
 */
}
