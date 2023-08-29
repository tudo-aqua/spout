/*
 * Copyright (c) 2023 Automated Quality Assurance Group, TU Dortmund University.
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

package tools.aqua.spout;

import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * The action that a heap walker carries out for
 * different types
 */
public interface HeapWalkAction {

    /**
     * action on a primitive field
     *
     * @param obj
     * @param f
     */
    default void applyToPrimitiveField(StaticObject obj, Field f) {
    }

    /**
     * action on a primitive array element
     *
     * @param obj
     * @param i
     */
    default void applyToPrimitiveArrayElement(StaticObject obj, int i) {
    }
    /**
     * action on a string
     *
     * @param obj
     */
    default void applyToString(StaticObject obj) {
    }

    /**
     * action on an object (besides exploring it)
     * @param obj
     */
    default void applyToObject(StaticObject obj) {
    }

    /**
     * action on an array (besides exploring it)
     * @param obj
     */
    default void applyToArray(StaticObject obj) {
    }
}
