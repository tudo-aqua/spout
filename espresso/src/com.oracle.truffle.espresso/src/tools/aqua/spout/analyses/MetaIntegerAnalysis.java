package tools.aqua.spout.analyses;

import tools.aqua.spout.Annotations;
import tools.aqua.spout.Config;
import tools.aqua.spout.MetaAnalysis;

public class MetaIntegerAnalysis extends MetaAnalysis implements IntegerAnalysis<Annotations> {

    public MetaIntegerAnalysis(Config config) {
        super(config);
    }

    @Override
    public Annotations toString(byte b, Annotations a1) {
    return null;
    }
}
