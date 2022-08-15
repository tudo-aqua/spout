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

package tools.aqua.taint;

import tools.aqua.concolic.SymbolDeclaration;
import tools.aqua.spout.*;

import java.util.TreeMap;

public class TaintAnalysis implements Analysis<Taint> {

    private final TreeMap<Integer, String> ifColorNames = new TreeMap<>();

    private final Config config;

    private final Trace trace;

    private final Config.TaintType type;

    public TaintAnalysis(Config config) {
        this.config = config;
        this.trace = config.getTrace();
        this.type = config.getTaintType();
    }

    public Object taint(Object o, int color) {
        AnnotatedValue av = new AnnotatedValue(o);
        av.set(config.getTaintIdx(), new Taint(color));
        return av;
    }

    public void checkTaint(AnnotatedValue o, int color) {
        Taint taint = Annotations.annotation( o, config.getTaintIdx());
        if (type.equals(Config.TaintType.INFORMATION)) {
            trace.addElement(new TaintCheck(color, taint, ifColorNames));
        }
        else if (ColorUtil.hasColor(taint, color)) {
            trace.addElement(new TaintViolation(color));
        }
    }

    @Override
    public Taint iadd(int c1, int c2, Taint a1, Taint a2) {
        return ColorUtil.joinColors(a1, a2);
    }

}