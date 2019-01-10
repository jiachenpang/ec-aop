package org.n3r.acc.process.output;


import com.google.common.base.Splitter;
import com.google.common.collect.BiMap;
import com.google.common.collect.Iterables;
import org.apache.commons.lang3.StringUtils;
import org.n3r.config.Configable;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.tag.FromSpecConfig;

import java.util.ArrayList;
import java.util.Map;

@EcRocTag("Direct")
public class DirectOutput implements DataOutput, FromSpecConfig<DirectOutput> {
    protected int[] outputIndices = new int[0];
    protected String[] outputFields = new String[0];
    private StringBuilder buffer = new StringBuilder();
    protected Map<String, Integer> dataFieldNameIndex;

    @Override
    public void setDataFieldNameIndex(BiMap<String, Integer> dataFieldNameIndex) {
        ArrayList<Integer> outputIndices = new ArrayList<Integer>();

        computeOutputFields(dataFieldNameIndex);
        for (String outputField : outputFields) {
            Integer index = dataFieldNameIndex.get(outputField);
            if (index == null) throw new RuntimeException(outputField + " is unkown");

            outputIndices.add(index);
        }

        this.outputIndices = new int[outputIndices.size()];
        for (int i = 0, ii = this.outputIndices.length; i < ii; ++i)
            this.outputIndices[i] = outputIndices.get(i);

        this.dataFieldNameIndex = dataFieldNameIndex;
    }

    private void computeOutputFields(BiMap<String, Integer> dataFieldNameIndex) {
        if (outputFields.length > 0) return;

        outputFields = new String[dataFieldNameIndex.size()];
        BiMap<Integer, String> inverse = dataFieldNameIndex.inverse();
        for(int i = 0; ; ++i) {
            String fieldName = inverse.get(i);
            if (fieldName == null) break;
            outputFields[i] = fieldName;
        }
    }


    @Override
    public void outputLine(Object[] fields) {
        if (buffer.length() > 0) buffer.append("\r\n");

        if (outputIndices.length > 0)
            for (int index : outputIndices) buffer.append(fields[index]).append(',');
        else
            for (Object field : fields) buffer.append(field).append(',');
    }

    @Override
    public DirectOutput fromSpec(Configable config, Map<String, String> context) {
        String outputFields = config.getStr("output.fields");
        if (StringUtils.isEmpty(outputFields)) return this;

        Iterable<String> split = Splitter.on(',').trimResults().split(outputFields);
        outputFields(Iterables.toArray(split, String.class));

        return this;
    }

    @Override
    public void finishOutput() {
    }

    public DirectOutput outputFields(String... outputFields) {
        this.outputFields = outputFields;
        return this;
    }

    @Override
    public String toString() {
        return buffer.toString();
    }
}
