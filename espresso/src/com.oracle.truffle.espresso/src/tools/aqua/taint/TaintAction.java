package tools.aqua.taint;

import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.AnnotatedVM;
import tools.aqua.spout.Annotations;
import tools.aqua.spout.Config;
import tools.aqua.spout.HeapWalkAction;
import tools.aqua.spout.SPouT;

public class TaintAction implements HeapWalkAction {

    private final Config config;

    private final Taint taint;

    public TaintAction(Config config, Taint taint) {
        this.config = config;
        this.taint = taint;
    }

    @Override
    public void applyToPrimitiveField(StaticObject obj, Field f) {
        if (!config.hasTaintAnalysis()) return;
        SPouT.log("tainting " + f.getKind().toString() + "-field " +
                f.getNameAsString() + " of " +
                obj.getKlass().getNameAsString() + " [" +
                obj.toString() + "] with color " + taint);
        Annotations a = AnnotatedVM.getFieldAnnotation(obj, f);
        if (a == null) {
            a = Annotations.create();
        }
        Taint t = Annotations.annotation(a, config.getTaintIdx());
        t = ColorUtil.joinColors(t, taint);
        a.set(config.getTaintIdx(), t);
        AnnotatedVM.setFieldAnnotation(obj, f, a);
    }

    @Override
    public void applyToPrimitiveArrayElement(StaticObject obj, int i) {
        SPouT.log("currently not tainting array elements on heap");
    }

    @Override
    public void applyToString(StaticObject obj) {
        SPouT.log("currently not tainting strings s on heap");
    }

    @Override
    public void applyToArray(StaticObject obj) {
        SPouT.log("currently not tainting arrays on heap");
    }

    @Override
    public void applyToObject(StaticObject obj) {
        SPouT.log("currently no tainting objects on heap");
    }
}
