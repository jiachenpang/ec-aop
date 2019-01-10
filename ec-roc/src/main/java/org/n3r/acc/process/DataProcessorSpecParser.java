package org.n3r.acc.process;


import org.apache.commons.lang3.StringUtils;
import org.n3r.acc.process.filter.DataFileFilter;
import org.n3r.acc.process.input.DataInput;
import org.n3r.acc.process.output.DataOutput;
import org.n3r.acc.utils.AccUtils;
import org.n3r.config.Config;
import org.n3r.config.Configable;
import org.n3r.core.tag.EcTagUtils;
import org.n3r.core.tag.FromSpecConfig;
import org.n3r.core.tag.Spec;
import org.n3r.core.tag.SpecParser;

import java.util.Map;


public class DataProcessorSpecParser {
    private final String specName;
    private final Map<String, String> context;

    public DataProcessorSpecParser(String specName, Map<String, String> context) {
        this.specName = specName;
        this.context = context;
    }

    public DataProcessor parse() {
        DataProcessor dataProcessor = new DataProcessor();
        Configable config = Config.subset("DataProcessor." + specName);
        parseInput(config, dataProcessor);
        parseFilter(config, dataProcessor);
        parseOutput(config, dataProcessor);

        return dataProcessor;
    }

    private DataProcessor parseOutput(Configable config, DataProcessor dataProcessor) {
        String output = config.getStr("output");
        if (StringUtils.isEmpty(output))
            throw new RuntimeException("output is required");

        DataOutput dataOutput = AccUtils.parseSpec(output, DataOutput.class);
        if (dataOutput == null) throw new RuntimeException("output is unkown");

        AccUtils.tryFromSpec(dataOutput, config, context);

        dataProcessor.output(dataOutput);

        return dataProcessor;
    }

    private DataProcessor parseFilter(Configable config, DataProcessor dataProcessor) {
        String filter = config.getStr("filter", "@Text");
        if (StringUtils.isEmpty(filter))
            throw new RuntimeException("filter is required");

        DataFileFilter dataFileFilter = AccUtils.parseSpec(filter, DataFileFilter.class);
        if (dataFileFilter == null) throw new RuntimeException(filter + " is unkown");

        AccUtils.tryFromSpec(dataFileFilter, config, context);

        dataProcessor.filter(dataFileFilter);
        return dataProcessor;
    }

    private void parseInput(Configable config, DataProcessor dataProcessor) {
        String input = config.getStr("input");
        if (StringUtils.isEmpty(input))
            throw new RuntimeException("input is required");

        DataInput dataInput = AccUtils.parseSpec(input, DataInput.class);
        if (dataInput == null) throw new RuntimeException(input + " is unkown!");

        AccUtils.tryFromSpec(dataInput, config, context);

        dataProcessor.input(dataInput);
    }


}
