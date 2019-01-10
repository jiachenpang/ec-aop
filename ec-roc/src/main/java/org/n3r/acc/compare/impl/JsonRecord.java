package org.n3r.acc.compare.impl;

import com.alibaba.fastjson.JSONObject;
import org.n3r.acc.compare.FieldReference;
import org.n3r.acc.compare.Record;
import org.n3r.fastjson.EjsonDecoder;
import org.n3r.fastjson.EjsonEncoder;

public class JsonRecord  implements Record {
    private final String key;
    private final String json;
    private final JSONObject jsonObject;

    public JsonRecord(String key, String json) {
        this.key = key;
        this.json = json;
        this.jsonObject = new EjsonDecoder().decode(json);
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public Object getValue(FieldReference compareField) {
        return jsonObject.get(compareField.getName());
    }

    @Override
    public String toString() {
        return new EjsonEncoder().bare().encode(jsonObject);
    }
}
