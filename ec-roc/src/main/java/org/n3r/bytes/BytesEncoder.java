package org.n3r.bytes;

import java.util.concurrent.atomic.AtomicInteger;

public interface BytesEncoder<T> {
    T decode(byte[] bytes, int offset, String meta, AtomicInteger usedBytesSize);

    byte[] encode(T object, StringBuilder meta);

    Class<T> clazz();

    String getMetaPrefix();
}
