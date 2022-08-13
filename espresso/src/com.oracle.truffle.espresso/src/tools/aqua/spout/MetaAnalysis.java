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

package tools.aqua.spout;

import com.oracle.truffle.api.frame.VirtualFrame;

public class MetaAnalysis implements Analysis<Annotations> {

    private Config config;

    private Analysis<?>[] analyes = new Analysis<?>[] {};

    MetaAnalysis(Config config) {
        this.config = config;
        initialize();
    }

    private void initialize() {

    }

    @Override
    public Annotations iadd(int c1, int c2, Annotations a1, Annotations a2) {
        //SPouT.debug("running analysis.");

        for (Analysis<?> analysis : analyes) {
            analysis.iadd(c1, c2, AnnotatedValue.annotation(null, 0), AnnotatedValue.annotation(null, 0));
        }
        return null;
    }

}
