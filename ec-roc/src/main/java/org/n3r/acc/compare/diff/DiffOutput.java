package org.n3r.acc.compare.diff;

import org.n3r.acc.compare.Record;
import org.n3r.acc.compare.impl.DiffStat;
import org.n3r.core.tag.FromSpecConfig;

public interface DiffOutput {
    void onlyRight(Record record);
    Record onlyLeft(Record record);
    void diff(Record leftRecord, Record rightRecord, String diffs, String subDiffType);
    void balance(Record leftRecord, Record rightRecord);

    void startCompare(String batchNo);
    void endCompare(DiffStat diffStat);
}
