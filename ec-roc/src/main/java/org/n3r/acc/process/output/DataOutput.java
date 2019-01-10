package org.n3r.acc.process.output;


import com.google.common.collect.BiMap;

public interface DataOutput {
    void setDataFieldNameIndex(BiMap<String, Integer> dataFieldNameIndex);

    void outputLine(Object[] fields);

    void finishOutput();

}
