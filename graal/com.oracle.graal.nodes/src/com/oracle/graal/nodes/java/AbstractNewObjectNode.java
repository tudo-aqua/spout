/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.java;

import java.util.List;

import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.Simplifiable;
import com.oracle.graal.graph.spi.SimplifierTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.DeoptimizingFixedWithNextNode;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.FrameState;
import com.oracle.graal.nodes.extended.FixedValueAnchorNode;
import com.oracle.graal.nodes.memory.WriteNode;
import com.oracle.graal.nodes.memory.address.OffsetAddressNode;
import com.oracle.graal.nodes.spi.Lowerable;
import com.oracle.graal.nodes.spi.LoweringTool;

/**
 * The {@code AbstractNewObjectNode} is the base class for the new instance and new array nodes.
 */
@NodeInfo
public abstract class AbstractNewObjectNode extends DeoptimizingFixedWithNextNode implements Simplifiable, Lowerable {

    public static final NodeClass<AbstractNewObjectNode> TYPE = NodeClass.create(AbstractNewObjectNode.class);
    protected final boolean fillContents;

    protected AbstractNewObjectNode(NodeClass<? extends AbstractNewObjectNode> c, Stamp stamp, boolean fillContents, FrameState stateBefore) {
        super(c, stamp, stateBefore);
        this.fillContents = fillContents;
    }

    /**
     * @return <code>true</code> if the object's contents should be initialized to zero/null.
     */
    public boolean fillContents() {
        return fillContents;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        // poor man's escape analysis: check if the object can be trivially removed
        for (Node usage : usages()) {
            if (usage instanceof FixedValueAnchorNode) {
                if (((FixedValueAnchorNode) usage).usages().isNotEmpty()) {
                    return;
                }
            } else if (usage instanceof OffsetAddressNode) {
                if (((OffsetAddressNode) usage).getBase() != this) {
                    return;
                }
                for (Node access : usage.usages()) {
                    if (access instanceof WriteNode) {
                        if (access.usages().isNotEmpty()) {
                            // we would need to fix up the memory graph if the write has usages
                            return;
                        }
                    } else {
                        return;
                    }
                }
            } else {
                return;
            }
        }
        for (Node usage : usages().distinct().snapshot()) {
            if (usage instanceof OffsetAddressNode) {
                for (Node access : usage.usages().snapshot()) {
                    removeUsage(tool, (FixedWithNextNode) access);
                }
            } else {
                removeUsage(tool, (FixedWithNextNode) usage);
            }
        }
        List<Node> snapshot = inputs().snapshot();
        graph().removeFixed(this);
        for (Node input : snapshot) {
            tool.removeIfUnused(input);
        }
    }

    private void removeUsage(SimplifierTool tool, FixedWithNextNode usage) {
        List<Node> snapshot = usage.inputs().snapshot();
        graph().removeFixed(usage);
        for (Node input : snapshot) {
            tool.removeIfUnused(input);
        }
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }
}
