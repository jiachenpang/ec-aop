package org.n3r.acc.process.field;


import org.n3r.core.tag.EcRocTag;

import java.util.Date;

@EcRocTag("DateToLong")
public class DateToLongField implements DataField<Date, Long> {
    @Override
    public Long filter(Date field) {
        return field.getTime();
    }

    @Override
    public Class getFieldClass() {
        return Long.class;
    }
}
