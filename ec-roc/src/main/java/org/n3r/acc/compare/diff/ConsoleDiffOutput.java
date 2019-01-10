package org.n3r.acc.compare.diff;

import org.n3r.acc.compare.Record;
import org.n3r.acc.compare.impl.DiffStat;
import org.n3r.core.tag.EcRocTag;

@EcRocTag("Console")
public class ConsoleDiffOutput implements DiffOutput {
    @Override
    public void onlyRight(Record record) {
        System.out.println("onlyRight:" + record);
    }

    @Override
    public Record onlyLeft(Record record) {
        System.out.println("onlyLeft:" + record);
        return null;
    }

    @Override
    public void diff(Record leftRecord, Record rightRecord, String diffs, String subDiffType) {
        System.out.println("diff:" + diffs);
        System.out.println("left:" + leftRecord);
        System.out.println("right:" + rightRecord);
    }

    @Override
    public void startCompare(String batchNo) {
        System.out.println("start comparing!");
    }

    @Override
    public void endCompare(DiffStat diffStat) {
        System.out.println("end comparing!");
        System.out.println(diffStat);
    }

    @Override
    public void balance(Record leftRecord, Record rightRecord) {
        System.out.println("balance: " +    leftRecord);
    }

}
