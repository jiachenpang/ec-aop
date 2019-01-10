package org.n3r.mock;

import org.n3r.config.Config;
import org.n3r.core.util.ParamsApplyUtils;

public class Mock {

    private static Mockable impl;

    static {
        loadMockImplementation();
    }

    private static void loadMockImplementation() {
        String mockImplementation = Config.getStr("mockImpl", "org.n3r.mock.impl.RmdbMockImpl(n3_mock, mockConnection)");
        impl = ParamsApplyUtils.createObject(mockImplementation,
                Mockable.class);
    }

public static String mock(String key, Object in) {
    return impl.mock(key, in);
}

}
