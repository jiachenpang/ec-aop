package org.n3r.config;

import static org.junit.Assert.*;

import org.junit.Test;
import org.n3r.config.ex.ConfigNotFoundException;
import org.n3r.config.ex.ConfigValueFormatException;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

public class ConfigTest {
    @Test
    public void testStr() {
        assertEquals("value1", Config.getStr("key1"));
        // assertEquals(null, Config.getStr("keynotfound"));
        assertNull(Config.getStr("keynotfound"));
        assertEquals("value1", Config.getStr("key1", "defaultValue"));
        assertEquals("defaultValue",
                Config.getStr("keynotfound", "defaultValue"));

        assertEquals(null,
                Config.getStr("key2"));
        String str = Config.getStr("keynull");
        JSONObject parseObject = JSON.parseObject(str);
        System.out.println( parseObject);
        System.out.println(null == parseObject.get("key1"));
    }

    @Test(expected = NullPointerException.class)
    public void testExc() {
        Config.getStr(null);
    }

    @Test
    public void testInt() {
        assertEquals(120, Config.getInt("keyint"));
        assertEquals(120, Config.getInt("keyintxx", 120));
    }

    @Test(expected = NullPointerException.class)
    public void testExcInt() {
        Config.getInt(null);
    }

    @Test(expected = ConfigNotFoundException.class)
    public void testExcIntKeyNotFound() {
        Config.getInt("keynjdkjd");
    }

    @Test(expected = ConfigValueFormatException.class)
    public void testExcIntKeyNotMatch() {
        Config.getInt("keyintnotmatch");
    }

    @Test
    public void testLong() {
        assertEquals(120l, Config.getLong("keylong"));
        assertEquals(120l, Config.getLong("keylongxx", 120l));

    }

    @Test
    public void testFloat() {
        assertEquals(120.0f, Config.getFloat("keyfloat"), 0);
        assertEquals(120.0f, Config.getFloat("keyfloatxx", 120.0f), 0);

    }

    @Test
    public void testDouble() {
        assertEquals(120.0d, Config.getDouble("keydouble"), 0);
        assertEquals(120.0d, Config.getDouble("keydoublexx", 120.0d), 0);

    }

    @Test
    public void testBoolean() {
        assertEquals(true, Config.getBool("keyboolean"));
        assertEquals(false, Config.getBool("keybooleanxx", false));

    }
/*
    @Test
    public void testGetConfigTable() {
        ConfigTable students = Config.getTableRows("students");
        // System.out.println(students);
        assertEquals("students", students.getTableName());
        ConfigRow stdWang = students.getRow(0);
        assertEquals("001", stdWang.getRowKey());
        assertEquals("name", stdWang.getCell(1).getCellName());
        assertEquals("wang", stdWang.getCell(1).getCellText());
        assertEquals("wang", stdWang.getCell("name").getCellText());
        ConfigTable teachers = Config.getTableRows("teachers");
        // System.out.println(teachers);
        ConfigRow tzhang = teachers.getRow(0);
        assertEquals("T001zhang", tzhang.getRowKey());
        assertEquals("name", tzhang.getCell(2).getCellName());
        assertEquals("zhang", tzhang.getCell(2).getCellText());
        assertEquals("zhang", tzhang.getCell("name").getCellText());
    }

    @Test
    public void getTableObjects() {
        List<Student> students = Config.getTableRows("students",
                Student.class);
        // System.out.println(students);
        Student stdLi = students.get(1);
        assertEquals("002", stdLi.getId());
        assertEquals("li", stdLi.getName());
        assertEquals("02", stdLi.getSex());
        assertEquals("18", stdLi.getAge());
        List<Teacher> teachers = Config.getTableRows("teachers",
                Teacher.class);
        // System.out.println(teachers);
        Teacher tChen = teachers.get(1);
        assertEquals("T002", tChen.getId());
        assertEquals("chen", tChen.getName());
        assertEquals("02", tChen.getSex());
        assertEquals("72", tChen.getAge());
        assertEquals("1班", tChen.getClassName());
    }

    @Test
    public void getTableRow() {
        Student student = Config.getTableRow("students", "002", Student.class);
        // System.out.println(student);
        assertEquals("002", student.getId());
        assertEquals("li", student.getName());
        assertEquals("02", student.getSex());
        assertEquals("18", student.getAge());
        Teacher tChen = Config.getTableRow("teachers", "T002chen",
                Teacher.class);
        // System.out.println(teacher);
        assertEquals("T002", tChen.getId());
        assertEquals("chen", tChen.getName());
        assertEquals("02", tChen.getSex());
        assertEquals("72", tChen.getAge());
        assertEquals("1班", tChen.getClassName());
    }
    */
}
