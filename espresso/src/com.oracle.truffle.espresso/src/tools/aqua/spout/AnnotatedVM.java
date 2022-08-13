package tools.aqua.spout;

import com.oracle.truffle.api.frame.VirtualFrame;

public class AnnotatedVM {

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

    public static void initAnnotations(VirtualFrame frame) {
        Annotations[] annotations = new Annotations[ frame.getFrameDescriptor().getNumberOfSlots() - VALUES_START ];
        frame.setObject(ANNOTATION_SLOT, annotations);
    }
}
