package org.n3r.acc.process.field;

import org.n3r.core.tag.EcRocTag;

@EcRocTag("Str")
public class StrField implements DataField<String, String>{
    @Override
    public String filter(String field) {
        return field;
    }

    @Override
    public Class getFieldClass() {
        return String.class;
    }
}
