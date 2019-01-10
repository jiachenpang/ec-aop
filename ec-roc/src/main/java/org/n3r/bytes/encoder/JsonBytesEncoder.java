package org.n3r.bytes.encoder;

import com.alibaba.fastjson.JSON;
import org.n3r.bytes.BytesEncoder;
import org.n3r.core.joor.Reflect;
import org.n3r.core.lang.RByte;
import org.n3r.fastjson.EjsonDecoder;
import org.n3r.fastjson.EjsonEncoder;

import java.util.concurrent.atomic.AtomicInteger;

public class JsonBytesEncoder implements BytesEncoder<Object> {
    @Override
    public Object decode(byte[] bytes, int offset, String meta, AtomicInteger usedBytesSize) {
        String subMetas = meta.substring(5/*eson(*/, meta.length() - 1 /*)*/);
        short size = RByte.toShort(bytes, offset);
        usedBytesSize.set(size + 2);
        String json = RByte.toStr(bytes, offset + 2, size);
        Class<?> clazz = Reflect.on(subMetas).get();

        JSON jsonObject =  new EjsonDecoder().unbare().decode(json);
        return JSON.parseObject(jsonObject.toJSONString(), clazz);
    }

    @Override
    public byte[] encode(Object object, StringBuilder meta) {
        if (meta != null) meta.append(getMetaPrefix()).append('(').append(object.getClass().getName()).append(')');

        String json = new EjsonEncoder().bare().encode(object);

        return RByte.add(RByte.toBytes((short) json.length()), RByte.toBytes(json));
    }

    @Override
    public Class<Object> clazz() {
        return Object.class;
    }

    @Override
    public String getMetaPrefix() {
        return "eson";
    }
}
