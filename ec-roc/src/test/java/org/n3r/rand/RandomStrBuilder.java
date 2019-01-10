package org.n3r.rand;

/**
 * 随机字符串生成器，Builder模式设定参数.
 * User: Bingoo
 * Date: 13-4-29
 */
public class RandomStrBuilder {
    private int expectLen = 10; // 随机字符串长度
    private String noneOf = null;  // 随机字符串去除字符集
    private int total = 10000; // 生成总数
    private String toFile = "random.txt"; // 生成文件
    private RandomStrProcessor processor = null; // 随机字符串处理器
    private boolean onlyDigits; // 是否只生成数字
    private boolean caseSensitive; // 是否大小写敏感

    public RandomStrBuilder expectLen(int expectLen) {
        this.expectLen = expectLen;
        return this;
    }

    public RandomStrBuilder noneOf(String noneOf) {
        this.noneOf = noneOf;
        return this;
    }

    public RandomStrBuilder total(int total) {
        this.total = total;
        return this;
    }


    public RandomStrBuilder toFile(String toFile) {
        this.toFile = toFile;
        return this;
    }

    public RandomStrBuilder processor(RandomStrProcessor processor) {
        this.processor = processor;
        return this;
    }

    public RandomStr build() {
        return new RandomStr(this);
    }

    public int total() {
        return total;
    }

    public String noneOf() {
        return noneOf;
    }

    public int expectLen() {
        return expectLen;
    }

    public String toFile() {
        return toFile;
    }


    public RandomStrProcessor processor() {
        return processor;
    }

    public RandomStrBuilder onlyDigits() {
        this.onlyDigits = true;
        return this;
    }

    public boolean isOnlyDigits() {
        return onlyDigits;
    }

    public RandomStrBuilder caseSensitive() {
        this.caseSensitive = true;
        return this;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }
}
