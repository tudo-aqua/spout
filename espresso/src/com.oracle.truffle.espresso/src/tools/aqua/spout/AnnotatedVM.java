package tools.aqua.spout;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.runtime.StaticObject;

public class AnnotatedVM {

    // --------------------------------------------------------------------------
    //
    // stack

    private static final int ANNOTATION_SLOT = 1;
    private static final int VALUES_START = 2;

    private static Annotations[] getAnnotations(VirtualFrame frame) {
        return (Annotations[]) frame.getObject(ANNOTATION_SLOT);
    }

    public static Annotations popAnnotations(VirtualFrame frame, int slot) {
        Annotations[] annotations = getAnnotations(frame);
        Annotations result = annotations[slot - VALUES_START];
        annotations[slot - VALUES_START] = null;
        return result;
    }

    public static void putAnnotations(VirtualFrame frame, int slot, Annotations value) {
        Annotations[] annotations = getAnnotations(frame);
        annotations[slot - VALUES_START] = value;
    }

    public static Annotations getLocalAnnotations(VirtualFrame frame, int slot) {
        Annotations[] annotations = getAnnotations(frame);
        Annotations result = annotations[slot];
        return result;
    }

    public static void setLocalAnnotations(VirtualFrame frame, int slot, Annotations value) {
        Annotations[] annotations = getAnnotations(frame);
        annotations[slot] = value;
    }

    public static void initAnnotations(VirtualFrame frame) {
        Annotations[] annotations = new Annotations[ frame.getFrameDescriptor().getNumberOfSlots() - VALUES_START ];
        frame.setObject(ANNOTATION_SLOT, annotations);
    }

    // --------------------------------------------------------------------------
    //
    // fields and arrays

    public static void setFieldAnnotation(StaticObject obj, Field f, Annotations a) {
        if (f.isStatic()) {
            obj = f.getDeclaringKlass().getStatics();
        }

        if (!obj.hasAnnotations() && a == null) {
            return;
        }

        Annotations[] annotations = obj.getAnnotations();
        if (annotations == null) {
           annotations = new Annotations[f.isStatic()
                            ? f.getDeclaringKlass().getStaticFieldTable().length
                            : ((ObjectKlass) obj.getKlass()).getFieldTable().length];
           obj.setAnnotations(annotations);
        }

        annotations[f.getSlot()] = a;
    }

    public static Annotations getFieldAnnotation(StaticObject obj, Field f) {
        if (f.isStatic()) {
            obj = f.getDeclaringKlass().getStatics();
        }

        if (!obj.hasAnnotations()) {
            return null;
        }
        Annotations[] annotations = obj.getAnnotations();
        Annotations a = annotations[f.getSlot()];
        return a;
    }
}
