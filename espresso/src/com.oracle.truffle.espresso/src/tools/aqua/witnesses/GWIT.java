package tools.aqua.witnesses;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.jni.JniEnv;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import tools.aqua.spout.SPouT;
import tools.aqua.spout.Trace;

public class GWIT {

    private final Trace trace;

    public GWIT(Trace trace) {
        this.trace = trace;
    }

    @CompilerDirectives.TruffleBoundary
    public void trackLocationForWitness(String value, RootNode rn) {
        FrameInstance callerFrame = Truffle.getRuntime().iterateFrames(
                new FrameInstanceVisitor<FrameInstance>() {
                    @Override
                    public FrameInstance visitFrame(FrameInstance frameInstance) {
                        String callTarget = frameInstance.getCallTarget().toString();
                        if (!callTarget.startsWith("Ltools/aqua/concolic/Verifier;")) {
                            return frameInstance;
                        }
                        return null;
                    }
                });

        if (callerFrame != null) {
            Frame frame = callerFrame.getFrame(FrameInstance.FrameAccess.READ_ONLY);
            if (callerFrame.getCallNode() != null) {
                rn = callerFrame.getCallNode().getRootNode();
            }
            if (rn == null) {
                SPouT.log("Could not find root node for tracking witness.");
                return;
            }
            for ( Node c : rn.getChildren()) {
                if (c instanceof BytecodeNode) {
                    BytecodeNode bn = (BytecodeNode) c;
                    int bci = bn.getBci(frame);
                    SourceSection sourceSection = bn.getSourceSectionAtBCI(bci);
                    assert sourceSection != null;
                    Method m = bn.getMethod();
                    String scope = m.getDeclaringKlass().getType() + "." + m.getName() + m.getRawSignature();
                    trace.addElement(new WitnessAssumption(scope, value, sourceSection.getSource().getPath(), sourceSection.getStartLine()));
                    return;
                }
            }
        }
    }

}
