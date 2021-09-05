package tools.aqua.concolic;

public class ErrorEvent extends TraceElement {

    private final String className;

    ErrorEvent(String className) {
        this.className = className;
    }

    @Override
    public String toString() {
        return "[ERROR] " + className;
    }
}
