package org.n3r.bytes.encoder;

import org.n3r.bytes.BytesEncoder;
import org.n3r.core.lang.RByte;
import org.n3r.core.tag.EcRocTag;

import java.util.concurrent.atomic.AtomicInteger;

@EcRocTag
public class StringBytesEncoder implements BytesEncoder<String> {
    @Override
    public String decode(byte[] bytes, int offset, String meta, AtomicInteger usedBytesSize) {
        short size = RByte.toShort(bytes, offset);
        usedBytesSize.set(size + 2);
        return RByte.toStr(bytes, offset + 2, size);
    }

    @Override
    public byte[] encode(String object, StringBuilder meta) {
        if (meta != null) meta.append(getMetaPrefix());

        return RByte.add(RByte.toBytes((short) object.length()), RByte.toBytes(object));
    }

    @Override
    public Class<String> clazz() {
        return String.class;
    }

    @Override
    public String getMetaPrefix() {
        return "string";
    }
}
