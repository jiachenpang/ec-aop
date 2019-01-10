package org.n3r.config.impl;

import java.util.Properties;

import org.n3r.config.Configable;

public class ConfigBuilder implements DefConfigSetter {
    private Properties properties;
    @Override
    public void setDefConfig(Configable defConfig) {
        properties = new Properties(defConfig != null ? defConfig.getProperties() : null);
    }

    public void addConfig(Configable config) {
        properties.putAll(config.getProperties());
    }

    public Configable buildConfig() {
        return new DefaultConfigable(properties);
    }
}
