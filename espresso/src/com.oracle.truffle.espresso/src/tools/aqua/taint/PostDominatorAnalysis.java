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

import com.oracle.truffle.espresso.analysis.GraphBuilder;
import com.oracle.truffle.espresso.analysis.graph.EspressoBlock;
import com.oracle.truffle.espresso.analysis.graph.EspressoExecutionGraph;
import com.oracle.truffle.espresso.impl.Method;
import tools.aqua.spout.SPouT;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/*
 *  implements the algorithm sketched in:
 *  Cooper et al., A Simple, Fast Dominance Algorithm
 *  Technical Report TR-0633870, Rice University, 2006
 */
public class PostDominatorAnalysis {

    private EspressoExecutionGraph graph;

    private List<Integer> leafs;

    private int[] ipdoms;

    public PostDominatorAnalysis(Method m) {
        graph = (EspressoExecutionGraph) GraphBuilder.build(m);
        leafs = leafsOf(graph);
        ipdoms = new int[graph.totalBlocks()];
        immediatePostDominators();
    }

    public int immediatePostDominatorStartForBCI(int bci) {
        int b = getBlockForBCI(bci);
        int ipdom = ipdoms[b];
        return (ipdom != -1) ? graph.get(ipdom).start() : -1;
    }

    private int getBlockForBCI(int bci) {
        LinkedList<Integer> workList = new LinkedList<>();
        boolean[] visited = new boolean[graph.totalBlocks()];
        workList.add(graph.entryBlock().id());
        visited[graph.entryBlock().id()] = true;
        EspressoBlock start = null;
        while (!workList.isEmpty()) {
            int idx = workList.poll();
            EspressoBlock b = graph.get(idx);
            if (bci >= b.start() && bci <= b.lastBCI()) {
                start = b;
                break;
            }
            for (int sIdx : b.successorsID()) {
                if (!visited[sIdx]) {
                    visited[sIdx] = true;
                    workList.offer(sIdx);
                }
            }
        }
        assert start != null;
        return start.id();
    }

    private List<Integer> leafsOf(EspressoExecutionGraph graph) {
        List<Integer> leafs = new LinkedList<>();
        for (int idx=0; idx<graph.totalBlocks(); idx++) {
            if (graph.get(idx).isLeaf()) {
                leafs.add(idx);
            }
        }
        return leafs;
    }

    private List<Integer> reversePostOrderForReversedCFG() {
        boolean[] visited = new boolean[graph.totalBlocks()];
        boolean[] direction = new boolean[graph.totalBlocks()];
        LinkedList<Integer> workList = new LinkedList<>();
        LinkedList<Integer> order = new LinkedList<>();

        for (int leaf : leafs) {
            visited[leaf] = true;
            workList.offerFirst(leaf);
        }

        while (!workList.isEmpty()) {
            int idx = workList.pollFirst();
            if (direction[idx]) {
                order.add(idx);
            }
            else {
                direction[idx] = true;
                workList.offerFirst(idx);
                for (int p : graph.get(idx).predecessorsID()) {
                    if (!visited[p]) {
                        visited[p] = true;
                        workList.offerFirst(p);
                    }
                }
            }
        }
        Collections.reverse(order);
        return order;
    }

    private void immediatePostDominators() {
        List<Integer> reversePostOrderForReversedCFG = reversePostOrderForReversedCFG();

        final int sink = graph.totalBlocks();
        int[] pdoms = new int[sink+1];
        Arrays.fill(pdoms, -1);
        pdoms[sink] = sink;

        boolean changed = true;
        while (changed) {
            changed = false;
            for (int bId : reversePostOrderForReversedCFG) {
                int new_ipdom = -1;
                EspressoBlock block =  graph.get(bId);
                for (int sId : block.successorsID()) {
                    if (pdoms[sId] != -1) {
                        new_ipdom = (new_ipdom == -1) ? sId : intersect(new_ipdom, sId, pdoms);
                    }
                }
                if (block.isLeaf()) {
                    new_ipdom = (new_ipdom == -1) ? sink : intersect(new_ipdom, sink, pdoms);
                }
                if (new_ipdom != pdoms[bId]) {
                    pdoms[bId] = new_ipdom;
                    changed = true;
                }
            }
        }

        for (int i=0; i<sink; i++) {
            ipdoms[i] = (pdoms[i] != sink) ? pdoms[i] : -1;
        }
    }

    private static int intersect(int a, int b, int[] pdoms) {
        int f1 = a;
        int f2 = b;
        while (f1 != f2) {
            while (f1 < f2) {
                f1 = pdoms[f1];
            }
            while (f2 < f1)
                f2 = pdoms[f2];
        }
        return f1;
    }
}
