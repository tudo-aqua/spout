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

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.concolic.Concolic;

@EspressoSubstitutions
public final class Target_java_lang_Character {

    @Substitution
    public static @Host(Short.class) StaticObject valueOf(@Host(typeName = "C")  Object unboxed, @InjectMeta Meta meta) {
        return Concolic.boxChar(unboxed, meta);
    }
    

  @Substitution(methodName = "toUpperCase")
  public static @Host(typeName = "C") Object toUpperCase_char(
      @Host(typeName = "C") Object c,
      @InjectMeta Meta meta){
    return Concolic.characterToUpperCase(c, meta);
  }

  @Substitution(methodName = "toLowerCase")
  public static @Host(typeName = "C") Object toLowerCase_char(
      @Host(typeName = "C") Object c,
      @InjectMeta Meta meta){
    return Concolic.characterToLowerCase(c, meta);
  }
}
