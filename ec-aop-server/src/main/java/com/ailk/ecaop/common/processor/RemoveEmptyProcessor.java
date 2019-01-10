package com.ailk.ecaop.common.processor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

@EcRocTag("removeEmpty")
public class RemoveEmptyProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getOut().getBody(Map.class);
        Map msg = removeEmptyInMap(body);
        exchange.getOut().setBody(msg);
    }

    private Map removeEmptyInMap(Map<String, ?> map) {
        Map ret = new HashMap();
        for (String key : map.keySet()) {
            Object val = map.get(key);
            if (val == null) {
                continue;
            }
            if (val instanceof String) {
                if (!StringUtils.isEmpty((String) val)) {
                    ret.put(key, val);
                    continue;
                }
            }
            else if (val instanceof Map) {
                Map sub = removeEmptyInMap((Map) val);
                if (!isEmptyInMap(sub))
                    ret.put(key, sub);
                continue;
            }
            else if (val instanceof List) {
                List sub = removeEmptyInList((List) val);
                if (!isEmptyInList(sub))
                    ret.put(key, sub);
                continue;
            }
            else {
                continue;
            }
        }
        return ret;
    }

    private List removeEmptyInList(List list) {
        List ret = new ArrayList();
        for (int i = 0; i < list.size(); i++) {
            Object subL = list.get(i);
            if (subL == null) {
                continue;
            }
            if (subL instanceof String) {
                if (!StringUtils.isEmpty((String) subL)) {
                    ret.add(subL);
                    continue;
                }
            }
            else if (subL instanceof Map) {
                Map sub = removeEmptyInMap((Map) subL);
                if (!isEmptyInMap(sub))
                    ret.add(sub);
                continue;
            }
            else if (subL instanceof List) {
                List sub = removeEmptyInList((List) subL);
                if (!isEmptyInList(sub))
                    ret.add(sub);
                continue;
            }
            else {
                continue;
            }
        }
        return ret;
    }

    private boolean isEmptyInList(List list) {
        if (list == null || list.size() == 0) {
            return true;
        }
        return false;
    }

    private boolean isEmptyInMap(Map map) {
        if (map == null || map.size() == 0) {
            return true;
        }
        return false;
    }

}
