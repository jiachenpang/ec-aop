package org.n3r.acc.process.filter;


import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.n3r.acc.process.field.DataField;
import org.n3r.acc.process.output.DataOutput;
import org.n3r.config.Configable;
import org.n3r.core.lang.RStr;
import org.n3r.core.tag.*;

import static org.n3r.acc.process.filter.StatLinePosition.*;

import java.io.*;
import java.util.Map;
import java.util.Properties;

@EcRocTag("Text")
public class TextFileFilter implements DataFileFilter, FromSpecConfig<TextFileFilter> {
    private final DataLineDefinition dataLineDefinition = new DataLineDefinition();
    private final DataLineDefinition statLineDefinition = new DataLineDefinition();
    private StatLinePosition statLinePosition = None;
    private Map<Integer, StatAccumulator> statAccumulators = Maps.newHashMap();

    public TextFileFilter dataFieldNames(String dataFieldNames) {
        dataLineDefinition.dataFieldNames(dataFieldNames);

        return this;
    }

    public TextFileFilter statFieldNames(String statFieldNames) {
        statLineDefinition.dataFieldNames(statFieldNames);

        return this;
    }

    public TextFileFilter dataField(String fieldName, DataField... fieldFilters) {
        dataLineDefinition.dataField(fieldName, fieldFilters);

        return this;
    }

    @Override
    public void filter(InputStream is, DataOutput dataOutput) {
        dataOutput.setDataFieldNameIndex(dataLineDefinition.getDataFieldNameIndex());

        BufferedReader reader = new BufferedReader(toReader(is));
        String line, lastLine = null;
        int lineNo = 1;

        for (; (line = readLine(reader)) != null; ++lineNo) {
            if (lineNo == 1 && statLinePosition == FirstLine) {
                parseStatLine(line, lineNo);
                continue;
            }

            if (lastLine != null) parseLine(dataOutput, lastLine, lineNo - 1);
            lastLine = line;
        }

        if (lastLine != null) {
            if (statLinePosition == LastLine) parseStatLine(lastLine, lineNo);
            else parseLine(dataOutput, lastLine, lineNo - 1);
        }

        if (statLinePosition != None) validateStat();
    }

    @Override
    public TextFileFilter fromSpec(Configable config, Map<String, String> context) {
        parseStatLineConfig(config);
        parseDataLineSeperator(config);
        parseDataFieldNames(config);
        return this;
    }

    private void parseStatLineConfig(Configable config) {
        String configStr = config.getStr("statLinePosition");
        if (StringUtils.isEmpty(configStr)) {
            statLinePosition = StatLinePosition.None;
            return;
        }

        try {
            statLinePosition = StatLinePosition.valueOf(configStr);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(configStr + " is invalid", e);
        }

        if (statLinePosition != StatLinePosition.None) {
            parseStatFields(config);
            parseStatAccumulator(config);
        }
    }

    private TextFileFilter parseStatAccumulator(Configable config) {
        // 检查以名称定义的字段过滤器
        for (String dataFiledName : statLineDefinition.getDataFieldNameIndex().keySet()) {
            String statAccumulatorSpec = config.getStr("statAccumulator." + dataFiledName);
            if (StringUtils.isEmpty(statAccumulatorSpec)) continue;

            int fieldIndex = statLineDefinition.findFieldIndex(dataFiledName);

            addStatAccumulator(statAccumulatorSpec, fieldIndex);
        }

        // 检查以序号定义的字段过滤器
        Configable dataFieldConfig = config.subset("statAccumulator");
        Properties dataFields = dataFieldConfig.getProperties();
        for (Object key : dataFields.keySet()) {
            String strKey = (String) key;
            if (!RStr.isInteger(strKey)) continue;

            String statAccumulatorSpec = dataFields.getProperty(strKey);
            if (StringUtils.isEmpty(statAccumulatorSpec)) continue;

            int fieldIndex = Integer.parseInt(strKey);

            addStatAccumulator(statAccumulatorSpec, fieldIndex);
        }

        return this;
    }

    private void addStatAccumulator(String statAccumulatorSpec, int fieldIndex) {
        Spec spec = SpecParser.parseSpec(statAccumulatorSpec);
        StatAccumulator statAccumulator = EcTagUtils.parseTag(StatAccumulator.class, spec);

        statAccumulators.put(fieldIndex, statAccumulator);
    }

    private TextFileFilter parseStatFields(Configable config) {
        statLineDefinition.parseDataFieldNames(config, "statFieldNames", "statField");

        return this;
    }

    private TextFileFilter parseDataLineSeperator(Configable config) {
        dataLineDefinition.parseDataLineSeperator(config, "dataLineSeperator");

        return this;
    }

    private TextFileFilter parseDataFieldNames(Configable config) {
        dataLineDefinition.parseDataFieldNames(config, "dataFieldNames", "dataField");

        return this;
    }

    private void parseLine(DataOutput dataOutput, String line, int lineNo) {
        Object[] filterValues = dataLineDefinition.parseLine(line, lineNo);
        dataOutput.outputLine(filterValues);

        accumuteStat(filterValues);
    }

    private void accumuteStat(Object[] filterValues) {
        for (Map.Entry<Integer, StatAccumulator> entry: statAccumulators.entrySet())
            entry.getValue().accumulate(filterValues[entry.getKey()]);
    }

    private void validateStat() {
        for (Map.Entry<Integer, StatAccumulator> entry: statAccumulators.entrySet())
            entry.getValue().validate();
    }

    private void parseStatLine(String line, int lineNo) {
        Object[] filterValues = statLineDefinition.parseLine(line, lineNo);
        initAccumulator(filterValues);
    }

    private void initAccumulator(Object[] filterValues) {
        for (Map.Entry<Integer, StatAccumulator> entry: statAccumulators.entrySet())
            entry.getValue().init(filterValues[entry.getKey()]);
    }

    private String readLine(BufferedReader reader) {
        try {
            return reader.readLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private InputStreamReader toReader(InputStream is) {
        try {
            return new InputStreamReader(is, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
