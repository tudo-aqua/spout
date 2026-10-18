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

package tools.aqua.concolic;

import tools.aqua.smt.Types;
import tools.aqua.spout.TraceElement;

/** Declaration of an uninterpreted function of the heap model, e.g. {@code (declare-fun fld.Pair.a (Int) Int)}. */
public class FunctionDeclaration extends TraceElement {

    private final String name;
    private final Types[] argumentTypes;
    private final Types resultType;

    /** the sort of a constant, if given explicitly (e.g. array sorts), null otherwise */
    private final String rawResultSort;

    public FunctionDeclaration(String name, Types resultType, Types... argumentTypes) {
        this.name = name;
        this.argumentTypes = argumentTypes;
        this.resultType = resultType;
        this.rawResultSort = null;
    }

    /** a constant of the given sort, e.g. {@code (Array Int (Array Int (_ BitVec 32)))} */
    public FunctionDeclaration(String name, String sort) {
        this.name = name;
        this.argumentTypes = new Types[0];
        this.resultType = null;
        this.rawResultSort = sort;
    }

    @Override
    public String toString() {
        if (rawResultSort != null) {
            return "[DECLARE] (declare-fun " + name + " () " + rawResultSort + ")";
        }
        StringBuilder sb = new StringBuilder("[DECLARE] (declare-fun ").append(name).append(" (");
        for (int i = 0; i < argumentTypes.length; i++) {
            sb.append(i > 0 ? " " : "").append(argumentTypes[i]);
        }
        return sb.append(") ").append(resultType).append(')').toString();
    }
}
