package org.n3r.acc.process;


import com.jayway.jsonpath.internal.IOUtils;
import org.n3r.acc.compare.CompareContext;
import org.n3r.acc.process.filter.DataFileFilter;
import org.n3r.acc.process.input.DataInput;
import org.n3r.acc.process.output.DataOutput;

import java.io.InputStream;
import java.util.Map;

public class DataProcessor {

    private DataInput dataInput;
    private DataFileFilter dataFileFilter;
    private DataOutput dataOutput;


    public DataProcessor input(DataInput dataInput) {
        this.dataInput = dataInput;
        return this;
    }

    public DataProcessor filter(DataFileFilter dataFileFilter) {
        this.dataFileFilter = dataFileFilter;
        return this;
    }

    public DataProcessor output(DataOutput dataOutput) {
        this.dataOutput = dataOutput;
        return this;
    }

    public DataProcessor build() {
        return new DataProcessor();
    }

    public void process() {
        InputStream is = null;
        try {
            is = dataInput.getInputStream();
            dataFileFilter.filter(is, dataOutput);
            dataOutput.finishOutput();
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    public DataOutput getOutput() {
        return dataOutput;
    }

    public static DataProcessor fromSpec(String fileProcessorName, CompareContext compareContext) {
        return new DataProcessorSpecParser(fileProcessorName, compareContext.getContext()).parse();
    }
}
