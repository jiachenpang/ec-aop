package org.n3r.core.util;

import org.junit.Test;
import org.n3r.config.Configable;

public class ParamsApplyUtilsTest {
    @Test
    public void testParam() {
        String str = "org.n3r.config.impl.RedisConfigable(127.0.0.1,6379)";
        ParamsApplyUtils.createObject(str, Configable.class);
    }
}
