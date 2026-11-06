/*
 * Copyright (c) 2026 Automated Quality Assurance Group, TU Dortmund University.
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

/**
 * The analysis lost symbolic information on this path (e.g. a symbolic value was concretized): the path constraint
 * may not describe all inputs that take the path, so the exploration of the path is incomplete.
 */
public class PrecisionLoss extends TraceElement {

    private final String reason;

    PrecisionLoss(String reason) {
        this.reason = reason;
    }

    @Override
    public String toString() {
        return "[INCOMPLETE] " + reason.replace('\n', ' ');
    }
}
