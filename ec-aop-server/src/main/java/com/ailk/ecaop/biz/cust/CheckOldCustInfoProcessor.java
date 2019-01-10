package com.ailk.ecaop.biz.cust;

import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

@EcRocTag("CheckOldCustInfo")
public class CheckOldCustInfoProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map dataMap = exchange.getOut().getBody(Map.class);

        if ("0001".equals(dataMap.get("code"))) {
            exchange.setProperty(Exchange.HTTP_STATUSCODE, 200);
            return;
        }
        addExtraErrorDeal(dataMap);
    }

    /**
     * 客户资料校验的时候返回一些商城需要但是枢纽只做一个字段返回的情况 blackListFlag
     * arrearageFlag 这三个字段如果值为1需要当作业务异常 对应商城需要的code 分别为：0203 0204 0205
     */
    private void addExtraErrorDeal(Map dataMap) {

        if ("1".equals(dataMap.get("blackListFlag"))) {
            throw new EcAopServerBizException("0203", "黑名单用户");

        }

        // 最大用户数
        if ("1".equals(dataMap.get("maxUserFlag"))) {
            throw new EcAopServerBizException("0204", "已达到最大用户数");
        }
        // 客户Id超过16位，剔除
        Object custId = dataMap.get("custId");
        if (null != custId && custId.toString().length() > 16) {
            dataMap.remove("custId");
        }

        // 欠费用户
        // if ("1".equals(dataMap.get("arrearageFlag"))) {
        // throw new EcAopServerBizCodeDetailException("0205", "客户欠费");
        // }
    }

}
