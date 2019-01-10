package com.ailk.ecaop.common.extractor;

import java.util.Map;

import org.n3r.core.lang.RBean;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.ParameterValueExtractor;

@EcRocTag("propGet2")
public class PropGetValueWithDefaultExtractor implements ParameterValueExtractor, ParamsAppliable {

    private String property;

    private String defaultVal;

    @Override
    public Object extract(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        Object retObject = RBean.getPropertyQuietly(body, property);
        return retObject == null ? defaultVal
                : property.equals("msg.district") && retObject.toString().length() > 6 ? retObject.toString()
                        .substring(retObject.toString().length() - 6) : retObject;
    }

    @Override
    public void applyParams(String[] params) {
        String[] args = params[0].split(",");
        property = args[0].trim();
        defaultVal = args[1].trim();
    }
}
