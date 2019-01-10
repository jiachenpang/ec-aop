package org.n3r.asm;

import java.util.Map;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;

public class AsmToBeanBuilder  {

    private static final LoadingCache<Class, ResultToBeanCreator> ResultToBeanCache = CacheBuilder.newBuilder().build(
            new ResultToBeanCacheLoader());

    public static <T> T resultToBean(Class<T> clazz, Map in) {
        ResultToBeanCreator resultToBean = ResultToBeanCache.getUnchecked(clazz);
        return (T) resultToBean.resultToBean(in);
    }


}
