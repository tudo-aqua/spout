package tools.aqua.concolic;

public class WitnessAssumption extends TraceElement {

    private final String scope;
    private final String value;
    private final String sourcefile;
    private final int line;

    public WitnessAssumption(String scope, String value, String sourcefile, int line) {
        this.scope = scope;
        this.value = value;
        this.sourcefile = sourcefile;
        this.line = line;
    }

    @Override
    public String toString() {
        return "[WITNESS] " + sourcefile + " : " + scope + " : " + line + " : " + value;
    }
}
