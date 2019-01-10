package org.n3r.core.lang;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

/**
 * Convert String to primitives.
 * @author BingooHuang
 *
 */
public class RConvert {
    @SuppressWarnings("rawtypes")
    private static Map<Class, Class> primitiveMap = new HashMap<Class, Class>();
    static {
        primitiveMap.put(boolean.class, Boolean.class);
        primitiveMap.put(byte.class, Byte.class);
        primitiveMap.put(char.class, Character.class);
        primitiveMap.put(short.class, Short.class);
        primitiveMap.put(int.class, Integer.class);
        primitiveMap.put(long.class, Long.class);
        primitiveMap.put(float.class, Float.class);
        primitiveMap.put(double.class, Double.class);
    }

    public static Class<?> parseType(String type) {

        Class<?> destClass = null;
        if (StringUtils.isEmpty(type)) {
            destClass = String.class;
        }
        else if ("boolean".equalsIgnoreCase(type)) {
            destClass = Boolean.class;
        }
        else if ("char".equalsIgnoreCase(type)) {
            destClass = char.class;
        }
        else if ("Character".equalsIgnoreCase(type)) {
            destClass = Character.class;
        }
        else if ("short".equalsIgnoreCase(type)) {
            destClass = Short.class;
        }
        else if ("int".equalsIgnoreCase(type)) {
            destClass = int.class;
        }
        else if ("Integer".equalsIgnoreCase(type)) {
            destClass = Integer.class;
        }
        else if ("long".equalsIgnoreCase(type)) {
            destClass = Long.class;
        }
        else if ("float".equalsIgnoreCase(type)) {
            destClass = Float.class;
        }
        else if ("double".equalsIgnoreCase(type)) {
            destClass = Double.class;
        }
        else if ("string".equalsIgnoreCase(type)) {
            destClass = String.class;
        }
        else if ("Decimal".equalsIgnoreCase(type)) {
            destClass = BigDecimal.class;
        }
        else if ("date".equalsIgnoreCase(type)) {
            destClass = Date.class;
        }
        else {
            destClass = RClass.loadClass(type);
            if(destClass == null) {
                destClass = String.class;
            }
        }

        return destClass;
    }

    /**
     * Best try to convert string to destination class.
     * If destClass is one of
     * the supported primitive classes, an object of that type is returned.
     * Otherwise, the original string is returned.
     * @param value String value
     * @param destClass Destination Class
     * @return converted value
     */
    public static <T> T convert(String value, Class<? extends T> destClass, T defaultValue) {
        T convertedValue = convert(value, destClass);
        return convertedValue == null ? defaultValue : convertedValue;
    }

    @SuppressWarnings("unchecked")
    public static <T> T convert(String value, Class<? extends T> destClass) {
        if (value == null) {
            return null;
        }

        if (destClass == String.class) {
            return (T) value;
        }

        if ("".equals(value)) {
            return null;
        }

        if (destClass.isPrimitive()) {
            destClass = primitiveMap.get(destClass);
        }

        try {
            Method m = destClass.getMethod("valueOf", String.class);
            int mods = m.getModifiers();
            if (Modifier.isStatic(mods) && Modifier.isPublic(mods)) {
                return (T) m.invoke(null, value);
            }
        }
        catch (NoSuchMethodException e) {
            if (destClass == Character.class) {
                return (T) Character.valueOf(value.charAt(0));
            }
        }
        catch (IllegalAccessException e) {
            // this won't happen
        }
        catch (InvocationTargetException e) {
            // when this happens, the string cannot be converted to the intended type
            // we are ignoring it here - the original string will be returned.
            // But it can be re-thrown if desired!
        }
        catch (Throwable e) {
            // Silently.
        }
        return null;
    }


}
