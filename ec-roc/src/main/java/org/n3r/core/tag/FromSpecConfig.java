package org.n3r.core.tag;

import org.n3r.config.Configable;

import java.util.Map;

public interface FromSpecConfig  <T> {
    T fromSpec(Configable config, Map<String, String> context);
}
