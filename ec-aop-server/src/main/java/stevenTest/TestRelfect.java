package stevenTest;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class TestRelfect {

    public static void main(String[] str) {
        try {
            Class test = Class.forName("stevenTest.checkFault");
            Method methods = test.getMethod("test", String.class);
            methods.invoke(test.newInstance(), "HaHa");
            System.out.println(test.getConstructors().length);
            Field field = Inter.class.getDeclaredField("MYTEST");
            System.out.println(field.get(field.getName()));
        }
        catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
        catch (SecurityException e) {
            e.printStackTrace();
        }
        catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        catch (InstantiationException e) {
            e.printStackTrace();
        }
        catch (NoSuchFieldException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
