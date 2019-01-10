package com.ailk.ecaop.common.utils;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unchecked")
public class MapUtils {

    public static <T> Map<T, T> asMap2(T... objects) {
        Map m = new HashMap(objects.length / 2 + 1);
        int i = 0;
        for (; i < objects.length - 1; i += 2) {
            m.put(objects[i], objects[i + 1]);
        }

        if (i < objects.length) {
            m.put(objects[i], null);
        }

        return m;
    }

    public static Map asMap(Object... objects) {
        Map m = new HashMap(objects.length / 2 + 1);
        int i = 0;
        for (; i < objects.length - 1; i += 2) {
            m.put(objects[i], objects[i + 1]);
        }

        if (i < objects.length) {
            m.put(objects[i], null);
        }

        return m;
    }

    public static String getDefault(Map inMap, String key, String defaultValue) {
        if (null == inMap || inMap.isEmpty()) {
            return defaultValue;
        }
        Object value = inMap.get(key);
        value = null != value && !"".equals(value) ? value : defaultValue;
        return value.toString();
    }

    public static void emptyPut(Map tarMap, Map orgMap, Object key) {
        if (IsEmptyUtils.isEmpty(orgMap)) {
            return;
        }
        Object value = orgMap.get(key);
        if (!IsEmptyUtils.isEmpty(value)) {
            tarMap.put(key, value);
        }
    }

    /**
     * 将一组key进行迁移
     * @param tarMap
     * @param orgMap
     * @param arrStr
     */
    public static void arrayPut(Map tarMap, Map orgMap, String[] arrStr) {
        if (0 == arrStr.length || IsEmptyUtils.isEmpty(orgMap)) {
            return;
        }
        for (String str : arrStr) {
            emptyPut(tarMap, orgMap, str);
        }
    }

    /**
     * 向一个Map的指定key中塞统一的值
     * @param o
     * @param tarMap
     * @param arrStr
     */
    public static void arrayPutFix(Object o, Map tarMap, String... arrStr) {
        if (0 == arrStr.length || IsEmptyUtils.isEmpty(tarMap)) {
            return;
        }
        for (String str : arrStr) {
            tarMap.put(str, o);
        }
    }

    public static void arrayPutFix(Map tarMap, String... arrStr) {
        if (0 == arrStr.length || IsEmptyUtils.isEmpty(tarMap)) {
            return;
        }
        for (String str : arrStr) {
            tarMap.put(str, "0");
        }
    }

}
