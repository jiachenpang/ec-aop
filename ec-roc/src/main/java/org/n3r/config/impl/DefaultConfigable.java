package org.n3r.config.impl;

import static org.apache.commons.lang3.StringUtils.*;

import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.text.StrSubstitutor;
import org.n3r.config.Configable;
import org.n3r.core.security.AesCryptor;

public class DefaultConfigable extends BaseConfigable {
    private Properties properties;

    public DefaultConfigable() {

    }

    public DefaultConfigable(Properties properties) {
        this.properties = properties;
    }

    @Override
    public boolean exists(String key) {
        return properties.containsKey(key);
    }

    @Override
    public Properties getProperties() {
        return properties;
    }

    public static void main(String[] args) {
        System.out.println(new AesCryptor("n3rconfigkey").encrypt("orcl"));
    }

    @Override
    public String getStr(String key) {
        String property = properties.getProperty(key);
        if (property == null) return null;

        // ${key}会在properties中定义了key时进行替换，否则保持原样
        property = StrSubstitutor.replace(property, properties);

        if (startsWith(property, "{AES}")) {
            property = new AesCryptor("n3rconfigkey").decrypt(property
                    .substring(5));
        }

        return trim(property);
    }

    @Override
    public Configable subset(String prefix) {
        if (isEmpty(prefix)) {
            return new DefaultConfigable( new Properties());
        }

        String prefixMatch = prefix.charAt(prefix.length() - 1) != '.' ? prefix + '.' : prefix;

        Properties subProps = subProperties(properties, prefixMatch);

        return new DefaultConfigable(subProps);
    }

    protected Properties subProperties(Properties properties, String prefixMatch) {
        Properties subProps = new Properties();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String key = (String) entry.getKey();

            if (!key.startsWith(prefixMatch)) continue;

            subProps.put(key.substring(prefixMatch.length()), entry.getValue());
        }

        return subProps;
    }

    @Override
    public long refreshConfigSet(String prefix) {
        return System.currentTimeMillis();
    }


    public void setProperties(Properties properties) {
        this.properties = properties;
    }


}
