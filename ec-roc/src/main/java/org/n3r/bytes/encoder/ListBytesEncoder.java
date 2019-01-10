package org.n3r.bytes.encoder;

import com.google.common.collect.Lists;
import org.n3r.bytes.Byter;
import org.n3r.bytes.BytesEncoder;
import org.n3r.core.lang.RByte;
import org.n3r.core.tag.EcRocTag;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;

@EcRocTag
public class ListBytesEncoder implements BytesEncoder<List> {

    @Override
    public List decode(byte[] bytes, int offset, String meta, AtomicInteger usedBytesSize) {
        String subMetas = meta.substring(5/*list(*/, meta.length() - 1 /*)*/);
        Matcher matcher = Byter.WORD.matcher(subMetas);

        int usedSize = 2;
        short size = RByte.toShort(bytes);
        List lst = Lists.newArrayList();

        int pos = 0;
        while (pos < subMetas.length() && matcher.find(pos)) {
            BytesEncoder parser = Byter.getEncoder(matcher.group());

            String subMeta = Byter.extractSubMeta(subMetas, matcher.start());
            pos = matcher.start() + subMeta.length() + 1;
            AtomicInteger subSize = new AtomicInteger(0);
            lst.add(parser.decode(bytes, offset + usedSize, subMeta, subSize));
            usedSize += subSize.get();
        }

        usedBytesSize.set(usedSize);

        return lst;
    }

    @Override
    public byte[] encode(List object, StringBuilder meta) {
        if (meta != null) meta.append(getMetaPrefix()).append('(');
        byte[] result = RByte.toBytes((short) object.size());
        for (int i = 0, ii = object.size(); i < ii; ++i) {
            if (meta != null && i > 0) meta.append(',');
            result = RByte.add(result, Byter.encode(object.get(i), meta));
        }

        if (meta != null) meta.append(')');


        return result;
    }

    @Override
    public Class<List> clazz() {
        return List.class;
    }

    @Override
    public String getMetaPrefix() {
        return "list";
    }
}
