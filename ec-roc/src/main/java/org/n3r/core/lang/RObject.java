package org.n3r.core.lang;

import com.google.common.base.Throwables;
import org.apache.commons.io.IOUtils;

import java.io.*;

public class RObject {

    /**
     * 判断两个对象是否相等。
     *
     * @param a1 Object
     * @param a2 Object
     * @return true 两个对象相等。
     */
    public static boolean equals(Object a1, Object a2) {
        return a1 != null ? a1.equals(a2) : a2 == null;
    }

    /**
     * get the bytes size of an object.
     *
     * @param obj
     * @return
     * @throws IOException
     */
    public static int sizeOf(Object obj) {
        return obj == null ? 0 : objectToBytes(obj).length;
    }

    /**
     * 转换对象到输入流。
     *
     * @param object
     * @return
     * @throws IOException
     */
    public static InputStream objectToStream(Object object)  {
        return new ByteArrayInputStream(objectToBytes(object));
    }

    /**
     * 转换对象到字节数组。
     *
     * @param object
     * @return
     * @throws IOException
     */
    public static byte[] objectToBytes(Object object)  {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            objectToStream(object, baos);
            return baos.toByteArray();
        } finally {
            IOUtils.closeQuietly(baos);
        }
    }

    /**
     * 对象转化为输出流。
     *
     * @param object
     * @param os
     * @throws IOException
     */
    public static void objectToStream(Object object, OutputStream os) {
        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(os);
            oos.writeObject(object);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            IOUtils.closeQuietly(oos);
        }
    }

    public static Object bytesToObject(byte[] bytes) {
        return streamToObject(new ByteArrayInputStream(bytes));
    }

    public static Object streamToObject(InputStream is) {
        ObjectInputStream ois = null;
        try {
            ois = new ObjectInputStream(is);
            return ois.readObject();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            IOUtils.closeQuietly(ois);
        }
    }
}
