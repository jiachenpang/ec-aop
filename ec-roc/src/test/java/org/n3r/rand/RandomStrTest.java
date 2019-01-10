package org.n3r.rand;

import org.junit.Test;
import org.n3r.rand.RandomStrBuilder;
import org.n3r.rand.RandomStrProcessor;

import java.io.IOException;

/**
 * 40秒可以生成1千万不重复的10位随机数字母数字, 写入文件大约1分钟即可。
 */
public class RandomStrTest {
    @Test
    public void test1() {
        new RandomStrBuilder().expectLen(10).noneOf("0O=").total(10000)
                .processor(
                        new RandomStrProcessor() {
                            @Override
                            public String process(String randomStr, int seqNo) {
                                return "101" + randomStr;
                            }
                        }
                ).toFile("random1.txt").build().create();

        new RandomStrBuilder().expectLen(10).onlyDigits()
                .total(10000).toFile("random2.txt")
                .build().create();

        new RandomStrBuilder().expectLen(10).caseSensitive()
                .total(10000).toFile("random3.txt")
                .build().create();

    }

    @Test
    public void test2() throws IOException {
        new RandomStrBuilder().expectLen(13)
                .total(10000).toFile("random4.txt")
                .build().create();
    }
}

