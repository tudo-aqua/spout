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
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import tools.aqua.taint.PostDominatorAnalysis;
import tools.aqua.taint.TaintAnalysis;

import static com.oracle.truffle.espresso.bytecode.Bytecodes.*;
import static tools.aqua.spout.Config.TaintType.CONTROL;
import static tools.aqua.spout.Config.TaintType.INFORMATION;

public class SPouT {

    public static final boolean DEBUG = true;

    private static boolean analyze = false;

    private static MetaAnalysis analysis = null;

    private static Config config = null;

    private static Trace trace = null;

    // --------------------------------------------------------------------------
    //
    // start and stop

    @CompilerDirectives.TruffleBoundary
    public static void newPath(String options) {
        System.out.println("======================== START PATH [BEGIN].");
        config = new Config(options);
        config.configureAnalysis();
        analysis = new MetaAnalysis(config);
        trace = config.getTrace();
        System.out.println("======================== START PATH [END].");
        // TODO: should be deferred to latest possible point in time
        analyze = true;
    }

    @CompilerDirectives.TruffleBoundary
    public static void endPath() {
        System.out.println("======================== END PATH [BEGIN].");
        stopAnalysis();
        if (trace != null) {
            trace.printTrace();
        }
        System.out.println("======================== END PATH [END].");
        System.out.println("[ENDOFTRACE]");
        System.out.flush();
    }

    private static void stopAnalysis() {
        if (analyze) {
            analyze = false;
            //FIXME: analysis.terminate();
        }
    }

    // --------------------------------------------------------------------------
    //
    // analysis entry points

    // FIXME: move part of these methods into the different analyses?

    @CompilerDirectives.TruffleBoundary
    public static void stopRecording(String message, Meta meta) {
        log(message);
        trace.addElement(new ExceptionalEvent(message));
        stopAnalysis();
        meta.throwException(meta.java_lang_RuntimeException);
    }

    @CompilerDirectives.TruffleBoundary
    public void assume(Object condition, Meta meta) {
        if (!analyze || !config.hasConcolicAnalysis())  {
            // FIXME: dont care about assumptions outside of concolic analysis?
            return;
        }
        /*
        boolean cont = concolicAnalysis.assume(condition, meta);
        if (!cont) {
            stopRecording("assumption violation", meta);
        }
        */
    }

    @CompilerDirectives.TruffleBoundary
    public static Object nextSymbolicInt() {
        if (!analyze || !config.hasConcolicAnalysis()) return 0;
        Object av = config.getConcolicAnalysis().nextSymbolicInt();
        //GWIT.trackLocationForWitness("" + concrete);
        return av;
    }

    @CompilerDirectives.TruffleBoundary
    public static Object taint(Object o, int color) {
        if (!analyze || !config.hasTaintAnalysis()) return o;
        return config.getTaintAnalysis().taint(o, color);
    }

    @CompilerDirectives.TruffleBoundary
    public static void checkTaint(Object o, int color) {
        if (!analyze || !config.hasTaintAnalysis()) return;
        config.getTaintAnalysis().checkTaint( o instanceof AnnotatedValue ? (AnnotatedValue) o : null, color);
    }

    public static void nextBytecode(VirtualFrame frame, int bci) {
        if (!analyze || !config.analyzeControlFlowTaint()) return;
        config.getTaintAnalysis().informationFlowNextBytecode(frame, bci);
    }

    public static void informationFlowMethodReturn(VirtualFrame frame) {
        if (!analyze || !config.analyzeControlFlowTaint()) return;
        config.getTaintAnalysis().informationFlowMethodReturn(frame);
    }

    public static void iflowRegisterException() {
        if (!analyze || !config.analyzeControlFlowTaint()) return;
        config.getTaintAnalysis().iflowRegisterException();
    }

    public static void iflowUnregisterException(VirtualFrame frame, Method method, int bci) {
        if (!analyze || !config.analyzeControlFlowTaint()) return;
        config.getTaintAnalysis().iflowUnregisterException(frame, method, bci);
    }

    public static int iflowGetIpdBCI() {
        if (!analyze || !config.analyzeControlFlowTaint()) return -1;
        return config.getTaintAnalysis().iflowGetIpdBCI();
    }

    public static PostDominatorAnalysis iflowGetPDA(Method method) {
        if (!analyze || !config.analyzeControlFlowTaint()) return null;
        return new PostDominatorAnalysis(method);
    }

    // --------------------------------------------------------------------------
    //
    // byte codes

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

    public static boolean takeBranchPrimitive1(VirtualFrame frame, int top, int opcode, BytecodeNode bcn, int bci) {

        assert IFEQ <= opcode && opcode <= IFLE;
        int c = BytecodeNode.popInt(frame, top - 1);

        boolean takeBranch = true;

        // @formatter:off
        switch (opcode) {
            case IFEQ: takeBranch = (c == 0); break;
            case IFNE: takeBranch = (c != 0); break;
            case IFLT: takeBranch = (c < 0);  break;
            case IFGE: takeBranch = (c >= 0); break;
            case IFGT: takeBranch = (c > 0);  break;
            case IFLE: takeBranch = (c <= 0); break;
            default:
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere("expecting IFEQ,IFNE,IFLT,IFGE,IFGT,IFLE");
        }

        if (analyze) analysis.takeBranchPrimitive1(frame, bcn, bci, opcode, takeBranch, AnnotatedVM.popAnnotations(frame, top - 1));
        return takeBranch;
    }

    public static boolean takeBranchPrimitive2(VirtualFrame frame, int top, int opcode, BytecodeNode bcn, int bci) {

        assert IF_ICMPEQ <= opcode && opcode <= IF_ICMPLE;
        int c1 = BytecodeNode.popInt(frame, top - 1);
        int c2 = BytecodeNode.popInt(frame, top - 2);

        // concrete
        boolean takeBranch = true;

        switch (opcode) {
            case IF_ICMPEQ: takeBranch = (c1 == c2); break;
            case IF_ICMPNE: takeBranch = (c1 != c2); break;
            case IF_ICMPLT: takeBranch = (c1 > c2);  break;
            case IF_ICMPGE: takeBranch = (c1 <= c2); break;
            case IF_ICMPGT: takeBranch = (c1 < c2);  break;
            case IF_ICMPLE: takeBranch = (c1 >= c2); break;
            default:
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere("non-branching bytecode");
        }

        if (analyze) analysis.takeBranchPrimitive2(frame, bcn, bci, opcode, takeBranch, c1, c2,
                AnnotatedVM.popAnnotations(frame, top - 1),
                AnnotatedVM.popAnnotations(frame, top -2 ));

        return takeBranch;
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

    public static void addToTrace(TraceElement element) {
        if (analyze) trace.addElement(element);
    }
}
