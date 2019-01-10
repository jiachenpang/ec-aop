package org.n3r.acc.compare.diff;

import org.apache.commons.io.IOUtils;
import org.n3r.acc.compare.Record;
import org.n3r.acc.compare.impl.DiffStat;
import org.n3r.core.lang.RByte;
import org.n3r.core.tag.EcRocTag;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

@EcRocTag("FileDiff")
public class FileDiffOutput implements DiffOutput {
    File file;
    FileOutputStream fos;

    @Override
    public void onlyRight(Record record) {
        try {
            fos.write(RByte.toBytes("onlyRight:" + record + "\r\n"));

        } catch (IOException e) {
            throw new RuntimeException("to byte failed");
        }

    }

    @Override
    public Record onlyLeft(Record record) {
        try {
            fos.write(RByte.toBytes("onlyLeft:" + record + "\r\n"));
            System.out.println();
        } catch (IOException e) {
            throw new RuntimeException("to byte failed");
        }

        return null;
    }

    @Override
    public void diff(Record leftRecord, Record rightRecord, String diffs, String subDiffType) {
        try {
            fos.write(RByte.toBytes("diff:" + diffs + "\r\n" + "left:" + leftRecord + "\r\n" + "right:" + rightRecord + "\r\n"));
            System.out.println();
        } catch (IOException e) {
            throw new RuntimeException("to byte failed");
        }

    }

    @Override
    public void startCompare(String batchNo) {
        System.out.println("start comparing!");
        try {
            file = new File("C:\\Users\\xule1991\\Desktop\\6.20_对账数据\\diff.txt");
            fos = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("file not found");
        }
    }

    @Override
    public void endCompare(DiffStat diffStat) {
        System.out.println("end comparing!");
        IOUtils.closeQuietly(fos);
    }

    @Override
    public void balance(Record leftRecord, Record rightRecord) {
        try {
            fos.write(RByte.toBytes("balance:" + leftRecord + "\r\n"));

        } catch (IOException e) {
            throw new RuntimeException("to byte failed");
        }

    }

}
