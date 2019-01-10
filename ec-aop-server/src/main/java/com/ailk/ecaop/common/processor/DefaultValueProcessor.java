package com.ailk.ecaop.common.processor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.n3r.config.Config;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.EcAopConfigException;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

@EcRocTag("defaultValue")
public class DefaultValueProcessor extends BaseAopProcessor implements ParamsAppliable {

    String defaultStr;

    @Override
    public void process(Exchange exchange) throws Exception {

        Map out = exchange.getOut().getBody(Map.class);
        try {
            JSONObject json = JSON.parseObject(defaultStr);
            Set<Entry<String, Object>> en = json.entrySet();
            for (Entry el : en) {
                putNoEmptValue(out, String.valueOf(el.getKey()), el.getValue());
            }
        }
        catch (Exception e) {
            throw new EcAopConfigException("default value config error: " + defaultStr, e);
        }
        List parMap = (List) out.get("para");
        if (parMap != null && parMap.size() == 0) {
            out.remove("para");
        }

        List<Map> arrearageMessList = (List) out.get("arrearageMess");
        Object arrearageFlag = out.get("arrearageFlag");
        if (null == arrearageFlag || "".equals(arrearageFlag)) {
            arrearageFlag = "0";
        }
        if ("1".equals(arrearageFlag)) {
            Map temp = new HashMap();
            if (null == arrearageMessList || 0 == arrearageMessList.size()) {
                throw new EcAopServerBizException("9999", "Sorry,arrearageMess is null");
            }
            for (int i = 0; i < arrearageMessList.size(); i++) {
                temp = arrearageMessList.get(i);
                String feeString = (String) temp.get("arrearageFee");
                feeString = String.valueOf(Integer.parseInt(feeString) / 10);
                temp.put("arrearageFee", feeString);
            }
        }
        out.put("arrearageFlag", arrearageFlag);
    }

    private void putNoEmptValue(Map out, String key, Object defaultValue) {

        if (!out.containsKey(key)) {
            out.put(key, defaultValue);
            return;
        }

        Object value = out.get(key);
        if (value != null && "".equals(value)) {
            out.put(key, defaultValue);
            return;
        }

    }

    @Override
    public void applyParams(String[] params) {
        defaultStr = Config.getStr(params[0]);
    }

}
