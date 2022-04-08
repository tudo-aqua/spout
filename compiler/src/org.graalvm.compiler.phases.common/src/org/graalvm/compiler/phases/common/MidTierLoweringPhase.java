/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.phases.common;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.StageFlag;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;

/**
 * A {@link LoweringPhase} used to lower {@link Lowerable} nodes while being in
 * {@link org.graalvm.compiler.nodes.spi.LoweringTool.StandardLoweringStage#MID_TIER} stage.
 */
public class MidTierLoweringPhase extends LoweringPhase {

    private CanonicalizerPhase canonicalizer;
    private final boolean lowerOptimizableMacroNodes;

    public MidTierLoweringPhase(CanonicalizerPhase canonicalizer, boolean lowerOptimizableMacroNodes) {
        this.canonicalizer = canonicalizer;
        this.lowerOptimizableMacroNodes = lowerOptimizableMacroNodes;
    }

    public MidTierLoweringPhase(CanonicalizerPhase canonicalizer) {
        this(canonicalizer, false);
    }

    @Override
    protected boolean shouldDumpBeforeAtBasicLevel() {
        return false;
    }

    @Override
    protected void run(final StructuredGraph graph, CoreProviders context) {
        super.run(graph, context);
        graph.setAfterStage(StageFlag.MID_TIER_LOWERING);
    }

    @Override
    protected void lower(StructuredGraph graph, CoreProviders context, LoweringMode mode) {
        IncrementalCanonicalizerPhase<CoreProviders> incrementalCanonicalizer = new IncrementalCanonicalizerPhase<>(canonicalizer);
        incrementalCanonicalizer.appendPhase(new Round(context, mode, graph.getOptions(), LoweringTool.StandardLoweringStage.MID_TIER, lowerOptimizableMacroNodes));
        incrementalCanonicalizer.apply(graph, context);
        assert graph.verify();
    }

}
