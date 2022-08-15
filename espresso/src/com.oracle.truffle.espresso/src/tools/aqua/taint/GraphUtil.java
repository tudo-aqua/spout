package tools.aqua.taint;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.analysis.GraphBuilder;
import com.oracle.truffle.espresso.analysis.graph.Graph;
import com.oracle.truffle.espresso.analysis.graph.LinkedBlock;
import com.oracle.truffle.espresso.impl.Method;

import java.util.Arrays;
import java.util.LinkedList;


public class GraphUtil {

    static int getBlockForBCI(Method method, int bci) {
        Graph<?> graph = GraphBuilder.build(method);
        LinkedBlock start = getBlockForBCI(graph, bci);
        return start != null ? start.id() : -1;
    }

    static LinkedBlock getBlockForBCI(Graph<?> graph, int bci) {

        LinkedList<Integer> q = new LinkedList<>();
        boolean[] v = new boolean[graph.totalBlocks()];

        q.add(graph.entryBlock().id());
        v[graph.entryBlock().id()] = true;
        LinkedBlock start = null;
        while (!q.isEmpty()) {
            int idx = q.poll();
            LinkedBlock b = graph.get(idx);
            if (bci >= b.start() && bci <= b.lastBCI()) {
                start = b;
                break;
            }
            for (int sIdx : b.successorsID()) {
                if (!v[sIdx]) {
                    v[sIdx] = true;
                    q.offer(sIdx);
                }
            }
        }

        return start;
    }

    @CompilerDirectives.TruffleBoundary
    static int[] getEndOfScopeAfterPreviousBlock(Method method, int bci) {
        Graph<?> graph = GraphBuilder.build(method);
        LinkedBlock b = getBlockForBCI(graph, bci);
        assert b.predecessorsID().length == 1;
        bci = graph.get(b.predecessorsID()[0]).start();
        return getEndOfScopeAfterBlock(graph, method, bci);
    }

    @CompilerDirectives.TruffleBoundary
    static int[] getEndOfScopeAfterBlock(Method method, int bci) {
        Graph<?> graph = GraphBuilder.build(method);
        return getEndOfScopeAfterBlock(graph, method, bci);
    }

    @CompilerDirectives.TruffleBoundary
    static int[] getEndOfScopeAfterBlock(Graph<?> graph, Method method, int bci) {

        for (int i = 0; i < graph.totalBlocks(); i++) {
            LinkedBlock b = graph.get(i);
            /*
            System.out.println("Block " + i +
                    (graph.entryBlock().equals(b) ? " [entry]" : "") +
                    (b.isLeaf() ? " [leaf]" : "") +
                    ", lines: [" + b.start() + "-" + b.end() +
                    "], successors: " + Arrays.toString(b.successorsID()) +
                    ", predecessors: " + Arrays.toString(b.predecessorsID()));
             */
        }

        LinkedBlock start = getBlockForBCI(graph, bci);
        //System.out.println("-- search from: bci=" + bci + ", blocK: " + start);

        // 1. forward search ...

        //boolean[][] reach = new boolean[start.successorsID().length][graph.totalBlocks()];
        boolean[] intersection = new boolean[graph.totalBlocks()];
        Arrays.fill(intersection, true);
        for (int i=0; i<start.successorsID().length; i++) {
            boolean[] reach = reachableFrom(graph, graph.get(start.successorsID()[i]), start);
            intersection = intersect(intersection, reach);
        }

        //System.out.println("  Reachable by all: " + Arrays.toString(intersection));
        LinkedList<Integer> bcis =  new LinkedList<>();

        // 2. backward search
        /*
        boolean[] exits = new boolean[graph.totalBlocks()];
        for (int i = 0; i < graph.totalBlocks(); i++) {
            LinkedBlock b = graph.get(i);
            if (!b.isLeaf()) {
                continue;
            }

            bcis.add(b.end()); // TODO: check assumption that end is a bci location for leafs

            if (!intersection[b.id()]) {
                continue;
            }

            boolean[] visited = new boolean[graph.totalBlocks()];
            LinkedList<LinkedBlock> wl = new LinkedList<>();
            visited[b.id()] = true;
            wl.offer(b);
            WORKLIST: while(!wl.isEmpty()) {
                b = wl.poll();
                for (int pid : b.predecessorsID()) {
                    if (!intersection[pid]) {
                        exits[b.id()] = true;
                        continue WORKLIST;
                    }
                }

                for (int pid : b.predecessorsID()) {
                    if (!visited[pid]) {
                        visited[pid] = true;
                        wl.offer(graph.get(pid));
                    }
                }
            }
        }
        */


        // 3. export data

        for (int i=0; i<intersection.length; i++) {
            if (intersection[i]) {
                bcis.add(graph.get(i).start());
            }
            if (graph.get(i).isLeaf()) {
                bcis.add(graph.get(i).end());
            }

        }

        int[] ret = new int[bcis.size()];
        int i = 0;
        for (int idx : bcis) {
            ret[i++] = idx;
        }

        return ret;
    }

    private static boolean[] reachableFrom(Graph<?> g, LinkedBlock b, LinkedBlock stop) {
        boolean[] v = new boolean[g.totalBlocks()];
        LinkedList<LinkedBlock> q = new LinkedList<>();
        v[b.id()] = true;
        q.offer(b);
        while (!q.isEmpty()) {
            LinkedBlock cur = q.poll();
            for (int sIdx : cur.successorsID()) {
                if (!v[sIdx]) {
                    v[sIdx] = true;
                    // TODO: dont search beyond original block ...
                    if (sIdx != stop.id()) {
                        q.offer(g.get(sIdx));
                    }
                }
            }
        }
        return v;
    }

    private static boolean[] setminus(boolean[] a, boolean[] b) {
        boolean[] ret = new boolean[a.length];
        for (int i=0; i<a.length; i++) {
            ret[i] = a[i] && !b[i];
        }
        return ret;
    }

    private static boolean[] intersect(boolean[] a, boolean[] b) {
        boolean[] ret = new boolean[a.length];
        for (int i=0; i<a.length; i++) {
            ret[i] = a[i] && b[i];
        }
        return ret;
    }

    private static boolean[] union(boolean[] a, boolean[] b) {
        boolean[] ret = new boolean[a.length];
        for (int i=0; i<a.length; i++) {
            ret[i] = a[i] || b[i];
        }
        return ret;
    }

    /*
        LinkedList<Integer> q = new LinkedList<>();
        boolean[] v = new boolean[graph.totalBlocks()];

        // find all successors
        int[] succs = new int[graph.totalBlocks()];
        int threshold = start.successorsID().length;
        for (int next : start.successorsID()) {
            v = new boolean[graph.totalBlocks()];
            q.clear();
            q.add(next);
            v[next] = true;
            while (!q.isEmpty()) {
                int idx = q.poll();
                LinkedBlock b = graph.get(idx);
                for (int sIdx : b.successorsID()) {
                    if (!v[sIdx]) {
                        v[sIdx] = true;
                        q.offer(sIdx);
                    }
                }
            }
            for (int i=0; i<v.length; i++) {
                if (v[i]) {
                    succs[i]++;
                }
            }
        }

        ArrayList<Integer> endOfScope = new ArrayList<>();
        for (int i=0; i<succs.length; i++) {
            if (succs[i] < threshold) {
                continue;
            }
            LinkedBlock b = graph.get(i);
            boolean root = true;
            for (int pIdx : b.predecessorsID()) {
                if (succs[pIdx] >= threshold) {
                    root = false;
                    break;
                }
            }
            if (root) {
                //System.out.println("current context ends with block " + b.id());
                endOfScope.add(b.start());
            }
        }

        if (endOfScope.isEmpty()) {
            for (int i=0; i<graph.totalBlocks(); i++) {
                LinkedBlock b = graph.get(i);
                if (b.isLeaf()) {
                    endOfScope.add(b.end());
                }
            }
        }

        int[] ret = new int[endOfScope.size()];
        int i = 0;
        for (int idx : endOfScope) {
            ret[i++] = idx;
        }
        return ret;
    }
     */
}
