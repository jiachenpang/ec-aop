package org.n3r.bytes;

import com.google.common.collect.Lists;
import org.junit.Test;
import org.n3r.core.lang.RByte;
import org.n3r.core.lang.RObject;

import javax.xml.bind.DatatypeConverter;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ByterTest {
    @Test
    public void testSeariable() throws Exception {
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 100; ++i) {
            sb.append((char) random.nextInt());
        }

        String str = sb.toString();
        byte[] bytes = RObject.objectToBytes(str);
        System.out.println(bytes.length);

        byte[] bytes1 = Byter.encode(str, null);
        System.out.println(bytes1.length);

        Object objs = new Object[]{new Integer(10), new String("abc")};
        bytes = RObject.objectToBytes(objs);
        System.out.println(bytes.length);

        Object o = RObject.bytesToObject(bytes);
        assertArrayEquals((Object[]) objs, (Object[]) o);
        bytes1 = Byter.encode(objs, new StringBuilder());
        System.out.println(bytes1.length);
    }

    @Test
    public void testNull() {
        Object objs = new Object[]{null, new Integer(10), new String("abc")};
        StringBuilder meta = new StringBuilder();
        byte[] bytes = Byter.encode(objs, meta);
        assertEquals("00030000000A0003616263", toHex(bytes));

        assertEquals("array(null,int,string)", meta.toString());

        Object[] parsed = (Object[]) Byter.decode(bytes, meta.toString());
        assertArrayEquals((Object[]) objs, parsed);
    }

    @Test
    public void testPrimitives() throws Exception {
        int a = 10;
        StringBuilder meta = new StringBuilder();
        byte[] bytes = Byter.encode(a, meta);

        assertEquals("0000000A", toHex(bytes));

        assertEquals("int", meta.toString());

        Object objs = new Object[]{new Integer(10), new String("abc")};

        meta = new StringBuilder();
        bytes = Byter.encode(objs, meta);

        assertEquals("00020000000A0003616263", toHex(bytes));

        assertEquals("array(int,string)", meta.toString());

        Object[] parsed = (Object[]) Byter.decode(bytes, meta.toString());
        assertArrayEquals((Object[]) objs, parsed);

        long longValue = 1L;
        meta = new StringBuilder();
        bytes = Byter.encode(longValue, meta);
        assertEquals("0000000000000001", toHex(bytes));

        assertEquals("long", meta.toString());

        Object parsedLong = Byter.decode(bytes, meta.toString());
        assertEquals(longValue, parsedLong);
    }



    private String toHex(byte[] bytes) {
        return DatatypeConverter.printHexBinary(bytes);
    }

    @Test
    public void testList() throws Exception {
        List<String> list = Lists.newArrayList();
        list.add("abc");
        list.add("efg");

        StringBuilder meta = new StringBuilder();
        byte[] bytes = Byter.encode(list, meta);

        assertEquals("000200036162630003656667", toHex(bytes));
        assertEquals("list(string,string)", meta.toString());

        List<String> parsed = (List<String>) Byter.decode(bytes, meta.toString());
        assertEquals(list, parsed);
    }

    @Test
    public void testJSON() throws Exception {
        Person person = new Person();
        person.setAge(111);
        person.setName("bbbb");

        StringBuilder meta = new StringBuilder();
        byte[] bytes = Byter.encode(person, meta);
        assertEquals("{age:111,name:bbbb}", RByte.toStr(RByte.subBytes(bytes, 2)));
        assertEquals("eson(" + Person.class.getName()  + ")", meta.toString());

        Person o = (Person) Byter.decode(bytes, meta.toString());
        assertEquals(111, o.getAge());
        assertEquals("bbbb", o.getName());

    }

    public static class Person {
        private int age;
        private String name;

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
