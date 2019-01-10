package com.ailk.ecaop.biz.res;

import com.ailk.ecaop.common.utils.IsEmptyUtils;
import org.apache.commons.lang.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import java.util.List;
import java.util.Map;

/**
 * Created by Liu JiaDi on 2017/7/7.
 * 该方法专门用来对终端下沉接口返回的状态码进行转码
 */
@EcRocTag("changeStatusCode")
public class ChangeStatusCodeProcessor extends BaseAopProcessor {
    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getOut().getBody(Map.class);
        List<Map> resourcesRspList = (List<Map>) body.get("resourcesRsp");
        if (IsEmptyUtils.isEmpty(resourcesRspList)) {
            return;
        }
        for (Map resourceMap : resourcesRspList) {
            String rscStateDesc = (String) resourceMap.get("rscStateDesc");
            if (StringUtils.isNotEmpty(rscStateDesc)) {
                if (rscStateDesc.contains("已售")) {
                    resourceMap.put("rscStateCode", "0003");
                } else if ("预占,预售".contains(rscStateDesc)) {
                    resourceMap.put("rscStateCode", "0001");
                } else if ("损坏,故障机已调拨,已打包".contains(rscStateDesc)) {
                    resourceMap.put("rscStateCode", "0004");
                }
            }
        }
    }
}
