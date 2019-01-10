package org.n3r.bytes.encoder;

import org.n3r.bytes.Byter;
import org.n3r.bytes.BytesEncoder;
import org.n3r.core.lang.RByte;
import org.n3r.core.tag.EcRocTag;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;

@EcRocTag
public class ArrayBytesEncoder implements BytesEncoder<Object[]> {
    @Override
    public Object[] decode(byte[] bytes, int offset, String meta, AtomicInteger usedBytesSize) {
        String subMetas = meta.substring(6/*array(*/, meta.length() - 1 /*)*/);
        Matcher matcher = Byter.WORD.matcher(subMetas);

        int usedSize = 2;
        short arrayLenght = RByte.toShort(bytes);
        Object[] objects = new Object[arrayLenght];

        int pos = 0;
        int index = 0;
        while (pos < subMetas.length() && matcher.find(pos)) {
            BytesEncoder parser = Byter.getEncoder(matcher.group());

            String subMeta = Byter.extractSubMeta(subMetas, matcher.start());
            pos = matcher.start() + subMeta.length() + 1;
            AtomicInteger subSize = new AtomicInteger(0);
            objects[index++] = parser.decode(bytes, offset + usedSize, subMeta, subSize);
            usedSize += subSize.get();
        }

        usedBytesSize.set(usedSize);

        return objects;
    }

    @Override
    public byte[] encode(Object[] object, StringBuilder meta) {
        if (meta != null) meta.append(getMetaPrefix()).append('(');
        Object[] arr = (Object[]) object;
        byte[] result = RByte.toBytes((short) arr.length);
        for (int i = 0, ii = arr.length; i < ii; ++i) {
            if (meta != null && i > 0) meta.append(',');
            result = RByte.add(result, Byter.encode(arr[i], meta));
        }

        if (meta != null) meta.append(')');


        return result;
    }

    @Override
    public Class<Object[]> clazz() {
        return Object[].class;
    }

    @Override
    public String getMetaPrefix() {
        return "array";
    }


}