package tools.aqua.concolic;

public class ExceptionEvent extends TraceElement {

    private final String className;

    ExceptionEvent(String className) {
        this.className = className;
    }

    @Override
    public String toString() {
        return "ExceptionEvent{" +
                "className='" + className + '\'' +
                '}';
    }
}
