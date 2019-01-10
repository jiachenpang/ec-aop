package org.n3r.acc.process.field;


import java.util.List;

public class ListDataField implements DataField<Object, Object> {
    private final DataField[] fieldFilters;

    public ListDataField(DataField... fieldFilters) {
        this.fieldFilters = fieldFilters;
    }

    @Override
    public Object filter(Object field) {
        Object result = field;
        for (DataField fieldFilter : fieldFilters) {
            result = fieldFilter.filter(result);
        }

        return result;
    }

    @Override
    public Class getFieldClass() {
        return List.class;
    }
}
