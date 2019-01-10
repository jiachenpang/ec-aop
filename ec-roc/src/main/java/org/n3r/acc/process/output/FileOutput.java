package org.n3r.acc.process.output;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.core.util.ParamsUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

@EcRocTag("File")
public class FileOutput extends DirectOutput implements ParamsAppliable {
    private File file;
    private FileOutputStream fos;
    private PrintStream ps;

    @Override
    public void outputLine(Object[] fields) {
        byte[] valueBytes;

        Object[] outputFields = fields;
        if (outputIndices.length > 0) {
            outputFields = new Object[outputIndices.length];
            int outputIndex = 0;
            for (int index : outputIndices) outputFields[outputIndex++] = fields[index];
        }

        boolean firstField = true;
        for (Object field : outputFields) {
            if (!firstField) ps.print(',');
            else firstField = false;

            ps.print(field);
        }
        ps.println();
    }

    @Override
    public void finishOutput() {
        IOUtils.closeQuietly(ps);
        IOUtils.closeQuietly(fos);
    }

    @Override
    public void applyParams(String[] params) {
        String path = ParamsUtils.getStr(params, 0, null);
        if (StringUtils.isEmpty(path))
            throw new RuntimeException("file path required!");

        file = new File(path);

        try {
            fos = new FileOutputStream(file);
            ps = new PrintStream(fos);
        } catch (FileNotFoundException e) {
            // ingore
        }
    }
}
