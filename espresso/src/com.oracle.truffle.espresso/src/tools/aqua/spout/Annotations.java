package tools.aqua.spout;

import java.util.Arrays;

public class Annotations {

    private static int annotationLength = 0;

    private final Object[] annotations;

    Annotations() {
        this(new Object[annotationLength]);
    }

    Annotations(Annotations other) {
        this.annotations = new Object[other.annotations.length];
        System.arraycopy(other.annotations, 0, this.annotations, 0, this.annotations.length);
    }

    Annotations(Object[] annotations) {
        assert annotations.length == annotationLength;
        this.annotations = annotations;
    }

    public Object[] getAnnotations() {
        return annotations;
    }

    @SuppressWarnings("unchecked")
    public static <T> T annotation(Annotations a, int i) {
        return a != null ? (T) a.annotations[i] : null;
    }

    public <T> void set(int i, T annotation) {
        annotations[i] = annotation;
    }

    static void configure(int length) {
        annotationLength = length;
    }

    @Override
    public String toString() {
        return "Annotations{" +
                "annotations=" + Arrays.toString(annotations) +
                '}';
    }
}
