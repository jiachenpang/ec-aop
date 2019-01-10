package org.n3r.acc.compare.impl;

import com.alibaba.fastjson.JSONObject;
import org.n3r.acc.compare.FieldReference;
import org.n3r.acc.compare.Record;
import org.n3r.fastjson.EjsonEncoder;

import java.util.Arrays;

public class FieldsRecord implements Record {
    private final Object[] fieldsValue;
    private final String[] fieldsName;
    private String key;

    public FieldsRecord(Object[] fieldsValue, String[] fieldsName, FieldReference keyReference) {
        this.fieldsValue = fieldsValue;
        this.fieldsName = fieldsName;
        createKey(keyReference);
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public Object getValue(FieldReference fieldReference) {
        return fieldReference.getValue(fieldsValue, fieldsName);
    }

    @Override
    public String toString() {
        JSONObject json = new JSONObject();
        for (int i = 0, ii = fieldsName.length; i < ii; ++i) {
            json.put(fieldsName[i], fieldsValue[i]);
        }

        return new EjsonEncoder().bare().encode(json);
    }

    public void createKey(FieldReference keyReference) {
        if (keyReference == null) return;

        for (int i = 0, ii = fieldsName.length; i < ii; ++i) {
            if (fieldsName[i].equals(keyReference.getName())) {
                key = "" + fieldsValue[i];
                break;
            }
        }
    }

    public void setKey(String key) {
        this.key = key;
    }
}
