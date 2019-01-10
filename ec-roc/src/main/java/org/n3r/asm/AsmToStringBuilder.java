package org.n3r.asm;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;

public class AsmToStringBuilder {

    private static final LoadingCache<Class, ToStringCreator> ToStringCreatorCache = CacheBuilder.newBuilder().build(
            new ToStringCreatorCacheLoader());

    public static String toStr(Object bean) throws Exception {
        ToStringCreator toStringCreator = ToStringCreatorCache.getUnchecked(bean.getClass());
        return toStringCreator.toStr(bean);
    }

}
