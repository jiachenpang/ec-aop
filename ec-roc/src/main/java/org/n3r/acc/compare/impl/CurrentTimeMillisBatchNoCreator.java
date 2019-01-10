package org.n3r.acc.compare.impl;

import org.n3r.acc.compare.BatchNoCreator;
import org.n3r.acc.compare.CompareContextAware;
import static org.n3r.acc.compare.CompareContext.*;
import org.n3r.core.tag.EcRocTag;

import java.util.Map;

@EcRocTag("currentTimeMillis")
public class CurrentTimeMillisBatchNoCreator implements BatchNoCreator, CompareContextAware {
    private Map<String, String> context;

    @Override
    public String createBatchNo() {
        return context.get(ACCOUNT_TYPE) + System.currentTimeMillis();
    }

    @Override
    public void setCompareContext(Map<String, String> context) {
        this.context = context;
    }
}
