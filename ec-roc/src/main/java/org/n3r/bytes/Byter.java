package org.n3r.bytes;

import org.n3r.bytes.encoder.JsonBytesEncoder;
import org.n3r.bytes.encoder.NullBytesEncoder;
import org.n3r.core.tag.EcTagUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class Byter {
    public static Pattern WORD = Pattern.compile("\\w+");
    private static List<BytesEncoder> bytesEncoders = EcTagUtils.getObjects(BytesEncoder.class);
    private static BytesEncoder defaultEncoder = new JsonBytesEncoder();
    private static BytesEncoder nullEncoder = new NullBytesEncoder();

    public static byte[] encode(Object obj, StringBuilder meta) {
        if (obj == null) return nullEncoder.encode(obj, meta);

        for (BytesEncoder bytesEncoder : bytesEncoders)
            if (bytesEncoder.clazz() != null && bytesEncoder.clazz().isInstance(obj))
                return bytesEncoder.encode(obj, meta);

        return defaultEncoder.encode(obj, meta);
    }

    public static BytesEncoder getEncoder(Class clazz) {
        for (BytesEncoder bytesEncoder : bytesEncoders)
            if (bytesEncoder.clazz() != null
                    && bytesEncoder.clazz().isAssignableFrom(clazz)) return bytesEncoder;

        return defaultEncoder;
    }

    public static BytesEncoder getEncoder(String group) {
        for (BytesEncoder bytesEncoder : bytesEncoders)
            if (bytesEncoder.getMetaPrefix().equals(group)) return bytesEncoder;

        return defaultEncoder;
    }

    public static Object decode(byte[] bytes, String meta) {
        Matcher matcher = WORD.matcher(meta);
        matcher.find();

        BytesEncoder parser = getEncoder(matcher.group());

        return parser.decode(bytes, 0, meta, new AtomicInteger(0));
    }

    public static String extractSubMeta(String meta, int start) {
        for (int startPos = 0, brackets = 0, i = start,
                     ii = meta.length(); i < ii; ++i) {
            char ch = meta.charAt(i);
            switch (ch) {
                case '(':
                    if (brackets++ == 0) startPos = i;
                    break;
                case ')':
                    if (--brackets == 0) return meta.substring(startPos + 1, i);
                    break;
                default:
                    if (!Character.isLetter(ch)) return meta.substring(start, i);
            }

        }

        return meta.substring(start);
    }
}
