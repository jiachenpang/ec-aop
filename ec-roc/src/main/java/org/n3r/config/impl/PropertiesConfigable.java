package org.n3r.config.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.springframework.core.io.Resource;

public class PropertiesConfigable extends DefaultConfigable {

    public PropertiesConfigable(Resource res) {
        super(buildProperties(res));
    }

    private static Properties buildProperties(Resource res) {
        Properties properties = new Properties();
        InputStream inputStream = null;;
        try {
            inputStream = res.getInputStream();
            properties.load(inputStream);
        }
        catch (IOException e) {
            IOUtils.closeQuietly(inputStream);
        }
        
        return properties;
    }

}
