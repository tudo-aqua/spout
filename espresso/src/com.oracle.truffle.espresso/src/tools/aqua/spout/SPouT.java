/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

package tools.aqua.spout;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.nodes.BytecodeNode;

public class SPouT {

    public static final boolean DEBUG = true;

    private static boolean analyze = false;

    private static MetaAnalysis analysis = null;

    private static Config config = null;

    // --------------------------------------------------------------------------
    //
    // start and stop

    @CompilerDirectives.TruffleBoundary
    public static void newPath(String options) {
        System.out.println("======================== START PATH [BEGIN].");
        config = new Config(options);
        analysis = new MetaAnalysis(config);
        // TODO: should be deferred to latest possible point in time
        analyze = true;
        System.out.println("======================== START PATH [END].");
    }

    @CompilerDirectives.TruffleBoundary
    public static void endPath() {
        System.out.println("======================== END PATH [BEGIN].");
        System.out.println("======================== END PATH [END].");
        System.out.println("[ENDOFTRACE]");
        System.out.flush();
    }

    // --------------------------------------------------------------------------
    //
    // analysis entry points

    @CompilerDirectives.TruffleBoundary
    public static AnnotatedValue nextSymbolicInt() {
        if (!analyze) return null;
        AnnotatedValue av = config.nextSymbolicInt();
        //Analysis.getInstance().getTrace().addElement(new SymbolDeclaration(symbolic));
        //GWIT.trackLocationForWitness("" + concrete);
        return av;
    }

    // case IADD: putInt(frame, top - 2, popInt(frame, top - 1) + popInt(frame, top - 2)); break;
    public static void iadd(VirtualFrame frame, int top) {
        // concrete
        int c1 = BytecodeNode.popInt(frame, top - 1);
        int c2 = BytecodeNode.popInt(frame, top - 2);
        int concResult = c1 + c2;
        BytecodeNode.putInt(frame, top - 2, concResult);
        if (!analyze) return;
        AnnotatedVM.putAnnotations(frame, top - 2, analysis.iadd(c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top - 2)));
    }

    // --------------------------------------------------------------------------
    //
    // helpers

    @CompilerDirectives.TruffleBoundary
    public static void debug(String message) {
        if (DEBUG) System.out.println(message);
    }

    @CompilerDirectives.TruffleBoundary
    public static void log(String message) {
        System.out.println(message);
    }
}
