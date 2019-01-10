package org.n3r.acc.process.filter;

import org.n3r.core.tag.EcRocTag;

@EcRocTag("TotalLinesStat")
public class TotalLinesStatAccumulator implements StatAccumulator {
    private int totalLines = 0;
    private int target  = 0;

    @Override
    public void init(Object filterValue) {
        target = (Integer)filterValue;
    }

    @Override
    public void validate() {
        if (target == totalLines) return;

        throw new RuntimeException("total lines is invalid, expect " + target + ", but real " + totalLines);
    }

    @Override
    public void accumulate(Object filterValue) {
        ++totalLines;
    }
}
