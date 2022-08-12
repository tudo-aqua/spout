package tools.aqua.witnesses;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import tools.aqua.spout.SPouT;
import tools.aqua.spout.Trace;

public class GWIT {

    private final Trace trace;

    public GWIT(Trace trace) {
        this.trace = trace;
    }

    @CompilerDirectives.TruffleBoundary
    public void trackLocationForWitness(String value) {
        FrameInstance callerFrame = Truffle.getRuntime().iterateFrames(
                new FrameInstanceVisitor<FrameInstance>() {
                    private int n;

                    @Override
                    public FrameInstance visitFrame(FrameInstance frameInstance) {
                        //System.out.println(frameInstance.toString());
                        Node n = frameInstance.getCallNode();
                        if (n != null) {
                            return frameInstance;
                        }
                        return null;
                    }
                });

        if (callerFrame != null) {
            Node n = callerFrame.getCallNode();
            RootNode rn = callerFrame.getCallNode().getRootNode();
            for ( Node c : rn.getChildren()) {
                if (c instanceof BytecodeNode) {
                    BytecodeNode bn = (BytecodeNode) c;
                    int bci = bn.getBci(callerFrame.getFrame(FrameInstance.FrameAccess.READ_ONLY));
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
