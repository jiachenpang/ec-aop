package org.n3r.acc.compare;

import org.apache.commons.lang3.StringUtils;
import org.n3r.core.lang.RStr;

public class FieldReference {
    private int index;
    private String name;

    public FieldReference(String reference) {
        this.index = RStr.isInteger(reference) ? Integer.parseInt(reference, 10) :  0;
        this.name = StringUtils.trim(reference);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Object getValue(Object[] fieldsValue, String[] fieldsName) {
        if (index > 0) return fieldsValue[index - 1];

        if (fieldsName != null)
            for (int i = 0, ii = fieldsName.length; i < ii; ++i)
                if (fieldsName[i].equals(name)) return fieldsValue[i];

        return null;
    }

    @Override
    public String toString() {
        return "FieldReference{" +
                "index=" + index +
                ", name='" + name + '\'' +
                '}';
    }
}
