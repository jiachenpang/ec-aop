package org.n3r.acc.process.output;

import com.google.common.collect.BiMap;
import org.n3r.core.tag.EcRocTag;

@EcRocTag("Noop")
public class NoopOutput implements DataOutput {
    @Override
    public void setDataFieldNameIndex(BiMap<String, Integer> dataFieldNameIndex) {

    }

    @Override
    public void outputLine(Object[] fields) {

    }


    @Override
    public void finishOutput() {

    }
}
