package tools.aqua.taint;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

import java.util.Arrays;

public class InformationFlowScope {

    /*
     * frame to which this scope belongs
     */
    public VirtualFrame frame;

    /*
     * parent scope
     */
    InformationFlowScope parent = null;

    /*
     * essentially: left or right
     */
    int branchId;

    /*
     * color of this scope
     */
    Taint taint;

    /*
     * bci's that leave this scope
     */
    int[] endOfScope;

    /*
     * distinguish multiple subsequent independent branches
     */
    int nameCount = 0;

    public InformationFlowScope(InformationFlowScope parent, VirtualFrame frame, int branchId, Taint taint, int[] end) {
        this.frame = frame;
        this.endOfScope = end;
        this.parent = parent;
        this.taint = taint;
        this.branchId = branchId;
    }

    public boolean isEnd(VirtualFrame frame, int bci) {
        if (frame != this.frame) {
            return false;
        }
        for (int i : endOfScope) {
            if (i == bci) {
                return true;
            }
        }
        return false;
    }

    public String nextDecisionName() {
        nameCount++;
        return (parent == null ? "if" : parent.currentDecisionName()) + "_" + branchId + "n" + nameCount;
    }

    public String currentDecisionName() {
        return (parent == null ? "if" : parent.currentDecisionName()) + "_" + branchId + "n" + nameCount;
    }

    @CompilerDirectives.TruffleBoundary
    public String endBcis() {
        return Arrays.toString(endOfScope);
    }
}
