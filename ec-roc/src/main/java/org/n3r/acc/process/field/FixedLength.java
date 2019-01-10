package org.n3r.acc.process.field;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.core.util.ParamsUtils;

@EcRocTag("FixedLength")
public class FixedLength implements DataField<String, String>, ParamsAppliable {
    private int length;

    @Override
    public String filter(String field) {
        if (field.length() == length) return field;

        throw new RuntimeException(field + "'s length " + field.length() + " does not fix to " + length);
    }

    @Override
    public Class getFieldClass() {
        return String.class;
    }

    @Override
    public void applyParams(String[] params) {
        length = ParamsUtils.getInt(params, 0, 0);
        if (length <= 0) throw new RuntimeException("@FixedLength is invalid");
    }
}
