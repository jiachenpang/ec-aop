package com.ailk.ecaop.common.utils;

import java.util.List;
import java.util.Map;

public class IsEmptyUtils {

    public static boolean isEmpty(List list) {
        return null == list || 0 == list.size();
    }

    public static boolean isEmpty(Map map) {
        return null == map || map.isEmpty();
    }

    public static boolean isEmpty(Object o) {
        return null == o || "".equals(o);
    }
}
