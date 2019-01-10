package org.n3r.acc.compare;

import java.util.Map;

public interface CompareContextAware {
    void setCompareContext(Map<String, String> context);
}
