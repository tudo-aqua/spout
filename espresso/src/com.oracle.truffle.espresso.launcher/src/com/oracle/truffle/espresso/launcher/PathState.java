package com.oracle.truffle.espresso.launcher;

public class PathState {

    public static enum State { OK, ERROR, ESPRESSO_ERROR };

    State state;

    Object error;

    PathState(State s) {
        this.state = s;
    }

    PathState(State s, Object error) {
        this(s);
        this.error = error;
    }

    @Override
    public String toString() {
        return "PathState{" +
                "state=" + state +
                ", error=" + error +
                '}';
    }
}
