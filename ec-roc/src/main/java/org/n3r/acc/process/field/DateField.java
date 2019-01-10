package org.n3r.acc.process.field;


import org.apache.commons.lang3.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.core.util.ParamsUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;


@EcRocTag("Date")
public class DateField implements DataField<String, Date>, ParamsAppliable {
    private SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public DateField() {
    }

    public DateField(String dateFormat) {
        this.format = new SimpleDateFormat(dateFormat);
    }

    @Override
    public Date filter(String field) {
        if (StringUtils.isEmpty(field)) return null;

        try {
            return format.parse(field);
        } catch (ParseException e) {
            throw new RuntimeException(field + " can not parsed as " + format);
        }
    }

    @Override
    public Class getFieldClass() {
        return Date.class;
    }

    @Override
    public void applyParams(String[] params) {
        String str = ParamsUtils.getStr(params, 0, null);
        if (StringUtils.isNotEmpty(str)) format = new SimpleDateFormat(str);
    }
}
