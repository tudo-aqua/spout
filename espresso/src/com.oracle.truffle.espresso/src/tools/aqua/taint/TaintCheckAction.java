package tools.aqua.taint;

import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.AnnotatedVM;
import tools.aqua.spout.Annotations;
import tools.aqua.spout.Config;
import tools.aqua.spout.HeapWalkAction;
import tools.aqua.spout.SPouT;

/**
 * This action is called on objects that are checked for
 * taint to check for taint on the portion of the heap
 * that is reachable from this object
 */
public class TaintCheckAction implements HeapWalkAction {

    private Config config;

    private Taint taint;

    private TaintAnalysis taintAnalysis;

    public TaintCheckAction(Config config, Taint taint) {
        this.config = config;
        this.taint = taint;
        this.taintAnalysis = config.getTaintAnalysis();
    }

    @Override
    public void applyToPrimitiveField(StaticObject obj, Field f) {
        if (!config.hasTaintAnalysis()) return;
        for (int color : ColorUtil.colorsIn(taint)) {
            SPouT.log("checking " + color + " taint on " +
                    f.getKind().toString() + "-field " +
                    f.getNameAsString() + " of " +
                    obj.getKlass().getNameAsString() + " [" +
                    obj.toString() + "]");
            Annotations a = AnnotatedVM.getFieldAnnotation(obj, f);
        taintAnalysis.checkTaint(a, color);
        }
    }

    @Override
    public void applyToPrimitiveArrayElement(StaticObject obj, int i) {
        SPouT.log("currently not checking taint on array elements on heap");
    }

    @Override
    public void applyToString(StaticObject obj) {
        SPouT.log("currently not checking taint on strings s on heap");
    }

    @Override
    public void applyToArray(StaticObject obj) {
        SPouT.log("currently not checking taint on  arrays on heap");
    }

    @Override
    public void applyToObject(StaticObject obj) {
        SPouT.log("currently not checking taint on  objects on heap");
    }
}
