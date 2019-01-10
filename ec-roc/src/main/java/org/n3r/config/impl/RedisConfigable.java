package org.n3r.config.impl;

import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.n3r.config.Configable;
import org.n3r.config.ex.ConfigException;
import org.n3r.core.lang.RConvert;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.core.util.ParamsUtils;
import org.n3r.jedis.EcRocJedis;

import com.google.common.collect.Lists;

public class RedisConfigable extends DefaultConfigable implements ParamsAppliable, DefConfigSetter {
    private String redisAddr;
    private int redisPort;
    private String keyPrefix;
    private static final String TIMESTAMP = "timestamp";

    private EcRocJedis rocJedis;
    private Configable defConfig;

    @Override
    public void setDefConfig(Configable defConfig) {
        this.defConfig = defConfig;
        this.keyPrefix = defConfig.getStr("bizconfigdir", "bizconfig") + ".";
        Properties properties = new Properties(defConfig.getProperties());

        rocJedis = new EcRocJedis(redisAddr, redisPort);
        Set<String> keys = rocJedis.keys(keyPrefix + "*");
        for (String key : keys) {
            String value = rocJedis.get(key);
            if (properties.containsKey(key))
                throw new ConfigException("duplicate key [" + key + "] in redis...");

            properties.put(key.substring(keyPrefix.length()), value);
        }

        super.setProperties(properties);
    }

    @Override
    public void applyParams(String[] params) {
        this.redisAddr = ParamsUtils.getStr(params, 0, "192.168.93.134");
        this.redisPort = ParamsUtils.getInt(params, 1, 6379);
    }

    @Override
    public long refreshConfigSet(String prefix) {
        boolean b = StringUtils.isEmpty(prefix);
        String fullKey = prefix + (prefix.endsWith(".") ? "" : ".") + TIMESTAMP;
        String localTimestamp = getStr(b ? TIMESTAMP : fullKey);
        String remoteTimestamp = rocJedis.get(b ? keyPrefix + TIMESTAMP : keyPrefix + fullKey);
        if (!StringUtils.equals(localTimestamp, remoteTimestamp)) {
            localTimestamp = remoteTimestamp;
            clearLocalKeys(prefix);
            Set<String> keys = rocJedis.keys(keyPrefix + prefix + "*");
            for (String key : keys) {
                String value = rocJedis.get(key);
                super.getProperties().put(key.substring(keyPrefix.length()), value);
            }
        }

        return RConvert.convert(remoteTimestamp, Long.class, 0L);
    }

    private void clearLocalKeys(String prefix) {
        ArrayList<String> removeKeys = Lists.newArrayList();
        for (Map.Entry<Object, Object> entry : super.getProperties().entrySet()) {
            String key = (String) entry.getKey();

            if (key.startsWith(prefix)) removeKeys.add(key);
        }

        for(String key : removeKeys)
            super.getProperties().remove(key);
    }

    @Override
    public Configable subset(String prefix) {
        Properties subProperties = subProperties(defConfig.getProperties(), prefix);
        Configable subset = super.subset(prefix);
        subProperties.putAll(subset.getProperties());
        ((DefaultConfigable) subset).setProperties(subProperties);

        return subset;
    }
}
