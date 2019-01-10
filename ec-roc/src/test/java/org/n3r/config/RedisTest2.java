package org.n3r.config;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import redis.clients.jedis.Jedis;

public class RedisTest2 {
    @Test
    public void testRefreshConfigSet2() {
        Jedis master = new Jedis("192.168.93.134", 6379);
        master.set("bizconfig.org.n3r.add.nc4", "8888");
        master.set("bizconfig.timestamp", System.currentTimeMillis() + "");
        Config.getBool("keyboolean");
        master.set("bizconfig.org.n3r.add.nc4", "8008");
        Config.refreshConfigSet("org.n3r.add");
        assertEquals("8888", Config.getStr("org.n3r.add.nc4"));
    }
}
