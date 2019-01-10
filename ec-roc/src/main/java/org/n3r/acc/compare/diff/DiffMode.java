package org.n3r.acc.compare.diff;

public enum DiffMode {
    OnlyLeft("L0"), Diff("LR"), Balance("00"), OnlyRight("0R");
    private final String name;

    DiffMode(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
