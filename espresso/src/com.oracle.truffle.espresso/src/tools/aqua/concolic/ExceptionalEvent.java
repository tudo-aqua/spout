package tools.aqua.concolic;

public class ExceptionalEvent extends TraceElement {

    private final String message;

    ExceptionalEvent(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "[ABORT] " + message;
    }
}
