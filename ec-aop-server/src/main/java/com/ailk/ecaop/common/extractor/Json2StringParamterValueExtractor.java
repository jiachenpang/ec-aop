package com.ailk.ecaop.common.extractor;

import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.core.util.ParamsUtils;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.ParameterValueExtractor;

import com.alibaba.fastjson.JSON;

@EcRocTag("json2String")
public class Json2StringParamterValueExtractor implements ParameterValueExtractor, ParamsAppliable {

    private String jsonString;

    @Override
    public Object extract(Exchange exchange) {
        Map<String, Object> root = exchange.getIn().getBody(Map.class);
        return JSON.toJSONString(root.get(jsonString));
    }

    @Override
    public void applyParams(String[] params) {
        jsonString = ParamsUtils.getStr(params, 0, null);
    }
}
