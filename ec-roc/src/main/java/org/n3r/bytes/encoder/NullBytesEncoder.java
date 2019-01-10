package org.n3r.bytes.encoder;

import org.n3r.bytes.BytesEncoder;
import org.n3r.core.tag.EcRocTag;

import java.util.concurrent.atomic.AtomicInteger;

@EcRocTag
public class NullBytesEncoder  implements BytesEncoder<Object> {
    @Override
    public Object decode(byte[] bytes, int offset, String meta, AtomicInteger usedBytesSize) {
        return null;
    }

    @Override
    public byte[] encode(Object object, StringBuilder meta) {
        if (meta != null) meta.append(getMetaPrefix());

        return new byte[0];
    }

    @Override
    public Class<Object> clazz() {
        return null;
    }

    @Override
    public String getMetaPrefix() {
        return "null";
    }
}
