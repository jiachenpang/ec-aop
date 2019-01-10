package com.ailk.ecaop.common.extractor;

import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.extractor.PropertyGetValueExtractor;


/**
 * 参数是parent 是入参层级结构父节点, 支持多级节点
 * 
 * @author Lionel
 * 
 */
@EcRocTag("propParamInto")
public class PropParamIntoValueExtractor extends ParamIntoValueExtractor {

    @Override
    public Object extract(Exchange exchange) {
        Map<String, Object> root = exchange.getIn().getBody(Map.class);
        Map par = getParent(root);
        PropertyGetValueExtractor pv = new PropertyGetValueExtractor();

        String[] params = { in };
        pv.applyParams(params);
        par.put(outkey, pv.extract(exchange) == null ? defaultValue : pv.extract(exchange));
        return root.get(parent);
    }

}
