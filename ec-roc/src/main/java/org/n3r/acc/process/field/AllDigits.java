package org.n3r.acc.process.field;

import org.n3r.core.tag.EcRocTag;

@EcRocTag("AllDigits")
public class AllDigits implements DataField<String, String> {
    @Override
    public String filter(String field) {
        for (char ch: field.toCharArray())
            if (ch < '0' || ch > '9')
                throw new RuntimeException("value " + field + " is not all digits!");

        return field;
    }

    @Override
    public Class getFieldClass() {
        return String.class;
    }
}
