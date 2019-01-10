package com.ailk.ecaop.common.extractor;

import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;

/**
 * 参数是parent 是入参层级结构父节点, 支持多级节点
 * 
 * @author Lionel
 * 
 */
@EcRocTag("fixParamInto")
public class FixParamIntoValueExtractor extends ParamIntoValueExtractor {

    @Override
    public Object extract(Exchange exchange) {
        Map<String, Object> root = exchange.getIn().getBody(Map.class);
        Map par = getParent(root);
        par.put(outkey, in);
        return root.get(parent);
    }

}
