package org.n3r.acc.process.field;

import org.n3r.core.tag.EcRocTag;

@EcRocTag("Int")
public class IntField implements DataField<String, Integer> {
    @Override
    public Integer filter(String  field) {
        return Integer.parseInt(field, 10);
    }

    @Override
    public Class getFieldClass() {
        return Integer.class;
    }
}
