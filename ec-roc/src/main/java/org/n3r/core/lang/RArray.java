package org.n3r.core.lang;

public class RArray {
    public static <T> T first(T[] arr) {
        return arr != null && arr.length > 0 ? arr[0] : null;
    }

    /**
     * 判断一个数组内是否包括某一个对象。 它的比较将通过 equals(Object,Object) 方法。
     *
     * @param <T> 对象类型。
     * @param array
     *            数组
     * @param ele
     *            对象
     * @return true 包含 false 不包含
     */
    public static <T> boolean in(T ele, T... array) {
        if (null == array) {
            return false;
        }

        for (T e : array) {
            if (RObject.equals(e, ele)) {
                return true;
            }
        }
        return false;
    }
}
