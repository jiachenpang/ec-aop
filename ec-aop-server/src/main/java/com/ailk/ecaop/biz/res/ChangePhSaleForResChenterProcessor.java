package com.ailk.ecaop.biz.res;

import java.util.HashMap;
import java.util.Map;

import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.alibaba.fastjson.JSON;

/**
 * 3G换机接口调用3GE成功之后,调用资源中心做换机的终端实占
 * @author wangmc
 * @date 2018/06/25
 */
public class ChangePhSaleForResChenterProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        // 调用3GE失败时不执行
        if (!"200".equals(String.valueOf(exchange.getProperty("HTTP_STATUSCODE")))) {
            return;
        }
        // 获取外围传进来的请求参数
        Map headers = exchange.getIn().getHeader("strParams", Map.class);
        Map msg = JSON.parseObject(String.valueOf(headers.get("msg")));
        // 复制请求信息用于调用资源中心终端换机接口
        Map saleMsg = new HashMap(msg);
        saleMsg.put("resourcesCode", saleMsg.get("newResourcesCode"));
        saleMsg.put("RES_OPER_TYPE_FLAG", "03");
        Exchange saleExchange = ExchangeUtils.ofCopy(exchange, saleMsg);
        // 调用资源中心终端销售接口
        new ResourceStateChangeUtils().getResourceInfo(saleExchange);
    }

}
