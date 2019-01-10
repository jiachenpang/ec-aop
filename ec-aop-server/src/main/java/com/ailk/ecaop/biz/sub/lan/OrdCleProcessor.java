package com.ailk.ecaop.biz.sub.lan;

import java.util.ArrayList;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.LanUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("ordCle")
public class OrdCleProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        String methodCode = exchange.getMethodCode();
        exchange.setMethodCode("ococ");
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.masb.ococ.ordCleParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.ordCleSer");
        lan.xml2Json("ecaop.masb.ococ.ordCleTemplate", exchange);
        exchange.setMethodCode(methodCode);

        Map retMap = exchange.getOut().getBody(Map.class);
        if (200 == (Integer) exchange.getProperty(Exchange.HTTP_STATUSCODE)) {
            dealReturn(body, retMap);
        }
    }

    /**
     * 处理返回的费用信息
     * 
     * @param body
     * @param retMap
     */
    private void dealReturn(Map body, Map retMap) {
        Map msg = JSON.parseObject((String) body.get("msg"));
        // boolean isEqualFee;
        // int cleFeeSize = 0;

        // payMode为1表示施工收费,此时不需要比对费用信息
        if ("1" == msg.get("payMode")) {
            return;
        }
        ArrayList<Map> cancelTradeInfo = (ArrayList<Map>) retMap.get("cancelTradeInfo");
        // List<Map> mallFeeInfo = (List<Map>) msg.get("feeInfo");
        ArrayList<Map> bssFeeInfo = null;
        for (Map cancelTrade : cancelTradeInfo) {
            if (msg.get("oldBssOrderId").equals(cancelTrade.get("orgProvinceOrderId"))) {
                bssFeeInfo = (ArrayList<Map>) cancelTrade.get("feeInfo");
                break;
            }
        }
        if (null == bssFeeInfo) {
            throw new EcAopServerBizException("9999", "省分'撤单预提交接口[ordCle]'未返回与商城提供订单[oldProvOrderId]:'"
                    + msg.get("oldBssOrderId") + "'匹配的数据");
        }
        // for (Map mallFee : mallFeeInfo) {
        // isEqualFee = false;
        // cleFeeSize = 0;
        // for (Map bssFee : bssFeeInfo) {
        // if ("2".equals(bssFee.get("operateType"))) {
        // cleFeeSize++;
        // isEqualFee = compareMall_BSSFeeInfo(mallFee, bssFee);
        // }
        // if (isEqualFee) {
        // break;
        // }
        // }
        // if (!isEqualFee) {
        // throw new EcAopServerBizCodeDetailException("0001", "省分返回撤单金额与商城在线支付时收取金额不一致,caused by:" +
        // "商城提供费用项[" + mallFee.get("feeId") + "]在省分接口[OrdCle]未返回");
        // }
        // }
        // if (mallFeeInfo.size() != cleFeeSize) {
        // throw new EcAopServerBizCodeDetailException("0001", "省分返回撤单金额与商城在线支付时收取金额不一致,caused by:" +
        // "省分接口[OrdCle]返回退费信息多于商城提供的退费信息");
        // }
    }

    /**
     * 判断商城提供的费用和省分返回的费用
     * 若不为退费,直接返回false
     * 退费情况下,需要判断费用项、费用类型和费用,完全一致时,才返回true
     * 
     * @param mallFee
     * @param bssFee
     * @return
     */
    // private boolean compareMall_BSSFeeInfo(Map mallFee, Map bssFee) {
    // if (mallFee.get("feeId").equals(bssFee.get("feeTypeCode"))
    // && mallFee.get("realFee").equals(bssFee.get("fee"))) {
    // return true;
    // }
    // return false;
    // }
}
