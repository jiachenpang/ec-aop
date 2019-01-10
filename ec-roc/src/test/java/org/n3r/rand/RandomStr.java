package org.n3r.rand;

import com.google.common.base.CharMatcher;

import static com.google.common.base.CharMatcher.*;

import com.google.common.base.Throwables;
import com.google.common.hash.*;
import com.google.common.io.BaseEncoding;
import com.google.common.io.Closer;
import org.n3r.core.joou.ULong;

import java.io.*;
import java.security.SecureRandom;

import static com.google.common.io.BaseEncoding.base32;
import static com.google.common.io.BaseEncoding.base64;

/**
 * 随机字符串生成。
 * 使用bloom filter保证唯一性.
 * User: Bingoo
 * Date: 13-4-29
 */
public class RandomStr {
    private final RandomStrBuilder randomStrBuilder;
    private final BloomFilter<String> bloomFilter;
    // base32: A-Z 2-7 Human-readable; no possibility of mixing up 0/O or 1/I. Defaults to upper case.
    private final BaseEncoding base;
    private final CharMatcher containsCharMatcher;  // 保留字符匹配器
    private SecureRandom random = new SecureRandom();
    private HashFunction hf = Hashing.murmur3_128();
    // for output file
    private Closer closer = Closer.create();
    private PrintStream ps;

    /**
     * 构造函数，配合生成器使用。
     *
     * @param randomStrBuilder 随即字符串生成器
     */
    RandomStr(RandomStrBuilder randomStrBuilder) {
        this.randomStrBuilder = randomStrBuilder;

        CharMatcher baseMatcher = JAVA_LETTER_OR_DIGIT;
        final String noneOf = randomStrBuilder.noneOf();
        this.containsCharMatcher = noneOf == null ? baseMatcher : baseMatcher.and(noneOf(noneOf));

        bloomFilter = BloomFilter.create(new Funnel<String>() {
            @Override
            public void funnel(String string, PrimitiveSink primitiveSink) {
                primitiveSink.putString(string);
            }
        }, randomStrBuilder.total() * 2);

        base = randomStrBuilder.isCaseSensitive() ? base64() : base32();

        createOut(randomStrBuilder);
    }

    /*
     * 创建缓冲文件输出。
     */
    private void createOut(RandomStrBuilder randomStrBuilder) {
        try {
            FileOutputStream fos = closer.register(new FileOutputStream(randomStrBuilder.toFile()));
            BufferedOutputStream bos = closer.register(new BufferedOutputStream(fos));
            ps = closer.register(new PrintStream(bos));
        } catch (FileNotFoundException ex) {
            throw Throwables.propagate(ex);
        }

    }

    /**
     * 生成随机字符串文件。
     *
     * @return 生成文件所耗毫秒数。
     */
    public long create() {
        long startTime = System.currentTimeMillis();
        RandomStrProcessor processor = randomStrBuilder.processor();
        for (int i = 0, j = randomStrBuilder.total(); i < j; ++i) {
            String single = createSingle();
            if (processor != null) single = processor.process(single, i + 1);
            ps.println(single);
        }
        try {
            closer.close();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return System.currentTimeMillis() - startTime;
    }

    /**
     * 生成单个随机字符串。
     *
     * @return 随机字符串
     */
    public String createSingle() {
        String str = createStr();

        String randomStr = adjustLen(str, randomStrBuilder.expectLen());

        if (bloomFilter.mightContain(randomStr)) return createSingle();

        bloomFilter.put(randomStr);

        return randomStr;
    }

    private String createStr() {
        if (randomStrBuilder.isOnlyDigits()) {
            return "" + new ULong(random.nextLong());
        } else {
            String hash = base.encode(hf.hashLong(random.nextLong()).asBytes());  // 32位
            return containsCharMatcher.retainFrom(hash);
        }
    }

    /*
     * 调整字符串到固定长度。如果超长则尾部截断，否则尾部补充0123456789.
     * @param value
     * @param len
     * @return
     */
    private String adjustLen(String value, int len) {
        if (value.length() >= len) return value.substring(0, len);

        // pad 01234567890 at tail to required length.
        StringBuffer sb = new StringBuffer(value);

        for (int i = value.length(), num = 0; i < len; ++i, ++num) {
            if (num > 9) num = 0;
            sb.append(num);
        }

        return sb.toString();
    }

}
