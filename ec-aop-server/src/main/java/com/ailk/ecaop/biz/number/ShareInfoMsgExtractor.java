package com.ailk.ecaop.biz.number;

import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.core.util.ParamsUtils;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.ParameterValueExtractor;

import com.alibaba.fastjson.JSONObject;

@EcRocTag("shareInfo")
public class ShareInfoMsgExtractor implements ParameterValueExtractor, ParamsAppliable {

    private String jsonString;

    @Override
    public Object extract(Exchange exchange) {
        Map<String, Object> root = exchange.getIn().getBody(Map.class);

        JSONObject jsonObj = (JSONObject) root.get(jsonString);
        String jsonStr = "shareNbrInfo:{serviceClassCode:\"0200\", brandCode:\"2000\", areaCode:\""
                + jsonObj.getString("areaCode") + "\", shareNumber:\"" + jsonObj.getString("shareNumber") + "\"";
        return jsonStr;
    }

    @Override
    public void applyParams(String[] params) {
        jsonString = ParamsUtils.getStr(params, 0, null);

    }

}
