package tools.aqua.taint;

import tools.aqua.spout.TraceElement;

public class TaintViolation extends TraceElement {

    private final int color;

    TaintViolation(int color) {
        this.color = color;
    }

    @Override
    public String toString() {
        return "[TAINT VIOLATION] " + color;
    }

}
