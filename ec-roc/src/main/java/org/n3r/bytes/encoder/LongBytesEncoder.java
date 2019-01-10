package org.n3r.bytes.encoder;

import org.n3r.bytes.BytesEncoder;
import org.n3r.core.lang.RByte;
import org.n3r.core.tag.EcRocTag;

import java.util.concurrent.atomic.AtomicInteger;

@EcRocTag
public class LongBytesEncoder implements BytesEncoder<Long> {

    @Override
    public Long decode(byte[] bytes, int offset, String meta, AtomicInteger usedBytesSize) {
        usedBytesSize.set(8);
        return RByte.toLong(bytes, offset);
    }

    @Override
    public byte[] encode(Long object, StringBuilder meta) {
        if (meta != null) meta.append(getMetaPrefix());
        return RByte.toBytes(object);
    }


    @Override
    public Class<Long> clazz() {
        return Long.class;
    }

    @Override
    public String getMetaPrefix() {
        return "long";
    }
}
