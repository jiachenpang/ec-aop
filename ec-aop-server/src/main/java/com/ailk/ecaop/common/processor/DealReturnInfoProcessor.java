package com.ailk.ecaop.common.processor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.n3r.config.Config;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.core.util.ParamsUtils;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParameterValueMappingBean;

import com.google.common.collect.Maps;

@EcRocTag("dealReturnInfo")
public class DealReturnInfoProcessor extends BaseAopProcessor implements ParamsAppliable {

    private List<ParameterValueMappingBean> beans;

    @Override
    public void process(Exchange exchange) throws Exception {
        if (CollectionUtils.isEmpty(beans)) {
            return;
        }

        Map<String, Object> body = exchange.getOut().getBody(Map.class);
        HashMap<Object, Object> map = Maps.newHashMap();
        for (ParameterValueMappingBean bean : beans) {
            Object newValue = bean.generateValue(exchange);
            body.put(bean.getParameterName(), newValue);

            if (bean.isTemp()) {
                continue;
            }
            map.put(bean.getParameterName(), newValue);
        }

        exchange.getOut().setBody(map);

    }

    @Override
    public void applyParams(String[] params) {
        String configKey = ParamsUtils.getStr(params, 0, "");
        beans = Config.getBeans(configKey, ParameterValueMappingBean.class);
    }

}
