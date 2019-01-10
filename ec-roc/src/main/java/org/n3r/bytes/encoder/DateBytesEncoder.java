package org.n3r.bytes.encoder;

import org.n3r.bytes.BytesEncoder;
import org.n3r.core.lang.RByte;
import org.n3r.core.tag.EcRocTag;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

@EcRocTag
public class DateBytesEncoder implements BytesEncoder<Date> {
    @Override
    public Date decode(byte[] bytes, int offset, String meta, AtomicInteger usedBytesSize) {
        usedBytesSize.set(8);

        return new Date(RByte.toLong(bytes, offset));
    }

    @Override
    public byte[] encode(Date object, StringBuilder meta) {
        if (meta != null) meta.append(getMetaPrefix());

        return RByte.toBytes(object.getTime());
    }

    @Override
    public Class<Date> clazz() {
        return Date.class;
    }

    @Override
    public String getMetaPrefix() {
        return "date";
    }
}
