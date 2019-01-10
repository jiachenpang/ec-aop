package org.n3r.acc.compare.right;

import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import org.apache.commons.io.IOUtils;
import org.n3r.acc.compare.FieldReference;
import org.n3r.acc.compare.Record;
import org.n3r.acc.compare.impl.FieldsRecord;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.core.util.ParamsUtils;

import java.io.*;

@EcRocTag("File")
public class FileRightData implements RightData, ParamsAppliable {
    private BufferedReader reader;
    private FileInputStream fis;

    @Override
    public Record readRecord(FieldReference keyReference) {
        String line = readLine();
        if (line == null) {
            close();
            return null;
        }

        Iterable<String> split = Splitter.on(',').trimResults().split(line);
        String[] values = Iterables.toArray(split, String.class);
        return new FieldsRecord(values, null, keyReference);
    }

    private String readLine() {
        try {
            return reader.readLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        IOUtils.closeQuietly(fis);
        IOUtils.closeQuietly(reader);
        fis = null;
        reader = null;
    }


    @Override
    public void applyParams(String[] params) {
        String fileName = ParamsUtils.getStr(params, 0, null);
        try {
            fis = new FileInputStream(fileName);
            reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void start() {

    }
}
