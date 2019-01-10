package org.n3r.bytes.encoder;

import org.n3r.bytes.BytesEncoder;
import org.n3r.core.lang.RByte;
import org.n3r.core.tag.EcRocTag;

import java.util.concurrent.atomic.AtomicInteger;

@EcRocTag
public class IntBytesEncoder implements BytesEncoder<Integer> {

    @Override
    public Integer decode(byte[] bytes, int offset, String meta, AtomicInteger usedBytesSize) {
        usedBytesSize.set(4);
        return RByte.toInt(bytes, offset);
    }

    @Override
    public byte[] encode(Integer object, StringBuilder meta) {
        if (meta != null) meta.append(getMetaPrefix());
        return RByte.toBytes(object);
    }

    @Override
    public Class<Integer> clazz() {
        return Integer.class;
    }

    @Override
    public String getMetaPrefix() {
        return "int";
    }
}
