/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.test;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.oracle.graal.nodes.Invoke;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.DeadCodeEliminationPhase;
import com.oracle.graal.phases.common.inlining.InliningPhase;
import com.oracle.graal.phases.tiers.HighTierContext;

public class InvokeHintsTest extends GraalCompilerTest {

    private static final String REFERENCE_SNIPPET = "referenceSnippet";

    public static int const1() {
        return 1;
    }

    public static int const7() {
        return 7;
    }

    @SuppressWarnings("all")
    public static int referenceSnippet() {
        return 7;
    }

    @Test
    public void test1() {
        test("test1Snippet");
    }

    @SuppressWarnings("all")
    public static int test1Snippet() {
        return const7();
    }

    @Test
    public void test2() {
        test("test2Snippet");
    }

    @SuppressWarnings("all")
    public static int test2Snippet() {
        return const1() + const1() + const1() + const1() + const1() + const1() + const1();
    }

    private void test(String snippet) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.NO);
        Map<Invoke, Double> hints = new HashMap<>();
        for (Invoke invoke : graph.getInvokes()) {
            hints.put(invoke, 1000d);
        }

        HighTierContext context = getDefaultHighTierContext();
        new InliningPhase(hints, new CanonicalizerPhase()).apply(graph, context);
        new CanonicalizerPhase().apply(graph, context);
        new DeadCodeEliminationPhase().apply(graph);
        StructuredGraph referenceGraph = parseEager(REFERENCE_SNIPPET, AllowAssumptions.NO);
        assertEquals(referenceGraph, graph);
    }
}
