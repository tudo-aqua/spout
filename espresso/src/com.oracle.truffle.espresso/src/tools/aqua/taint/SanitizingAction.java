package tools.aqua.taint;

import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.runtime.StaticObject;
import tools.aqua.spout.Config;
import tools.aqua.spout.HeapWalkAction;

public class SanitizingAction implements HeapWalkAction {

    private final Config config;

    private final Taint taint;

    public SanitizingAction(Config config, Taint taint) {
        this.config = config;
        this.taint = taint;
    }

    @Override
    public void applyToPrimitiveField(StaticObject obj, Field f) {
        HeapWalkAction.super.applyToPrimitiveField(obj, f);
    }

    @Override
    public void applyToPrimitiveArrayElement(StaticObject obj, int i) {
        HeapWalkAction.super.applyToPrimitiveArrayElement(obj, i);
    }

    @Override
    public void applyToString(StaticObject obj) {
    }

    @Override
    public void applyToArray(StaticObject obj) {
        HeapWalkAction.super.applyToArray(obj);
    }

    @Override
    public void applyToObject(StaticObject obj) {
        HeapWalkAction.super.applyToObject(obj);
    }
}
