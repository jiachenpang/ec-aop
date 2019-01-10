package org.n3r.rand;

/**
 * 随机数处理器.
 * 在生成随机数后，回掉该接口，以添加额外字段，或者添加前缀/后缀等其他处理。
 * User: Bingoo
 * Date: 13-4-29
 */
public interface RandomStrProcessor {
    /**
     * 随机数处理调用。
     * @param randomStr 随机数
     * @param seqNo 生成序号（从1开始）
     * @return 处理后的随机数
     */
    String process(String randomStr, int seqNo);
}
