package org.n3r.config;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import redis.clients.jedis.Jedis;

public class RedisTest {
    @Test
    public void testRedisStr() {
        //        Jedis master = new Jedis("192.168.93.134", 6379);
        // master.set("bizconfig.keystr", "hahaha");
        //        System.out.println(master.get("bizconfig.keystr"));
        assertEquals("String", Config.getStr("keystr"));
        assertEquals("defValue", Config.getStr("keystrxxx", "defValue"));
    }

    @Test
    public void testRedisInt() {
        //        Jedis master = new Jedis("192.168.93.134", 6379);
        // master.set(RByte.toBytes("bizconfig.keyint"), RByte.toBytes(123));
        // int i = RByte.toInt(master.get(RByte.toBytes("bizconfig.keyint")));
        // System.out.println(i);
        assertEquals(111111, Config.getInt("keyint"));
        assertEquals(123, Config.getInt("keyintxxx", 123));
    }

    @Test
    public void testRedisLong() {
        // Jedis master = new Jedis("192.168.93.134", 6379);
        // master.set(RByte.toBytes("bizconfig.keylong"), RByte.toBytes(123l));
        // long l =
        // RByte.toLong(master.get(RByte.toBytes("bizconfig.keylong")));
        // System.out.println(l);
        assertEquals(111111L, Config.getLong("keylong"));
        assertEquals(123l, Config.getLong("keylongxxx", 123l));
    }

    @Test
    public void testRedisDouble() {
        // Jedis master = new Jedis("192.168.93.134", 6379);
        // master.set(RByte.toBytes("bizconfig.keydouble"),
        // RByte.toBytes(123.2d));
        // double d = RByte.toDouble(master.get(RByte
        // .toBytes("bizconfig.keydouble")));
        // System.out.println(d);
        assertEquals(1111.11d, Config.getDouble("keydouble"), 1);
        assertEquals(123.2d, Config.getDouble("keydoublexxx", 123.2d), 1);
    }

    @Test
    public void testRedisFloat() {
        // Jedis master = new Jedis("192.168.93.134", 6379);
        // master.set(RByte.toBytes("bizconfig.keyfloat"),
        // RByte.toBytes(12.0f));
        // float d = RByte
        // .toFloat(master.get(RByte.toBytes("bizconfig.keyfloat")));
        // System.out.println(d);
        assertEquals(1111.11f, Config.getFloat("keyfloat"), 0);
        assertEquals(12.0f, Config.getFloat("keyfloatxxx", 12.0f), 0);
    }

    @Test
    public void testRedisBoolean() {
        // Jedis master = new Jedis("192.168.93.134", 6379);
        // master.set(RByte.toBytes("bizconfig.keyboolean"),
        // RByte.toBytes(true));
        // boolean b = RByte.toBoolean(master.get(RByte
        // .toBytes("bizconfig.keyboolean")));
        // System.out.println(b);
        assertEquals(true, Config.getBool("keyboolean"));
        assertEquals(false, Config.getBool("keybooleanxxx", false));
    }
/*
    @Test
    public void testGetConfigTable() {
        ConfigTable table = Config.getTableRows("students");
        // System.out.println(table);
        assertEquals("students", table.getTableName());
        ConfigRow stdWang = table.getRow(0);
        assertEquals("001", stdWang.getRowKey());
        assertEquals("name", stdWang.getCell("name").getCellName());
        assertEquals("wang", stdWang.getCell("name").getCellText());

        ConfigTable teachers = Config.getTableRows("teachers");
        ConfigRow row = teachers.getRow("001wang");
        // System.out.println(table);
        assertEquals("01", row.getCell("sex").getCellText());
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
    }


    @Test
    public void getTableRow() {
        Student student = Config.getTableRow("students", "002", Student.class);
        // System.out.println(student);
        assertEquals("002", student.getId());
        assertEquals("li", student.getName());
        assertEquals("02", student.getSex());
        assertEquals("18", student.getAge());
    }
*/
    @Test
    public void testRefreshConfigSet() {
        Jedis master = new Jedis("192.168.93.134", 6379);
        master.set("bizconfig.org.n3r.add.nc2", "8000");
        master.set("bizconfig.org.n3r.add.nc3", "8001");
        master.set("bizconfig.org.n3r.add.timestamp", System.currentTimeMillis() + "");
        try {
            Thread.sleep(1 * 1000);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        master.set("bizconfig.org.n3r.add.nc2", "8008");
        master.set("bizconfig.org.n3r.add.nc3", "8009");
        long timestamp = System.currentTimeMillis();
        master.set("bizconfig.org.n3r.add.timestamp", timestamp + "");
        long refreshTimestamp = Config.refreshConfigSet("org.n3r.add");
        assertEquals(timestamp, refreshTimestamp);
        assertEquals("8008", Config.getStr("org.n3r.add.nc2"));
        assertEquals("8009", Config.getStr("org.n3r.add.nc3"));

    }


//    public static void main(String[] args) {
//        System.out.println(Config.getFloat("org.n3r.add.nc1"));
//        Jedis master = new Jedis("192.168.93.134", 6379);
//        //        master.set("bizconfig.keyfloat", "2222.11f");
//        master.set("bizconfig.org.n3r.add.nc1", "9000");
//        master.set("bizconfig.org.n3r.add.nc2", "9999");
//        master.set("bizconfig.org.n3r.add.timestamp", System.currentTimeMillis() + "");
//        master.set("bizconfig.timestamp", System.currentTimeMillis() + "");
//        //        System.out.println(Config.getStr("org.n3r.add.timestamp"));
//        //        try {
//        //            Thread.sleep(7 * 1000);
//        //        }
//        //        catch (InterruptedException e) {
//        //        }
//        //        master.set("bizconfig.org.n3r.add.timestamp", System.currentTimeMillis() + "");
//        long timestamp = Config.refreshConfigSet("org.n3r.add");
//        System.out.println(timestamp);
//        long timestamp2 = Config.refreshConfigSet("");
//        System.out.println(timestamp2);
//        long timestamp3 = Config.refreshConfigSet(null);
//        System.out.println(timestamp3);
//    }
}
