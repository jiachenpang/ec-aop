package com.ailk.ecaop.biz.sub.olduser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("n6OldUserCheck")
public class N6OldUserCheckProcesor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        LanUtils lan = new LanUtils();
        Map body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        String province = (String) msg.get("province");
        if (!"11|17|18|76|91|97".contains(province)) {
            throw new EcAopServerBizException("9999", "路由分发失败");
        }
        // 获取产品id活动id
        String productId = null;
        String actPlanId = null;
        List<Map> productList = (List<Map>) msg.get("productInfo");
        List<Map> activityList = (List<Map>) msg.get("activityInfo");
        if (null != productList && !productList.isEmpty()) {
            productId = (String) productList.get(0).get("productId");
        }
        if (null != activityList && !activityList.isEmpty()) {
            actPlanId = (String) activityList.get(0).get("actPlanId");
        }

        // 调北六三户
        String[] copyArray = { "operatorId", "province", "channelId", "city", "district", "channelType" };
        Map threePartMap = MapUtils.asMap("getMode", "00000", "serialNumber", msg.get("numId"),
                "tradeTypeCode", "0120");
        MapUtils.arrayPut(threePartMap, msg, copyArray);
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
        try {
            lan.preData("ecaop.trade.n6.checkUserParametersMapping", threePartExchange);
            CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.UsrForNorthSer" + "." + province);
            lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        }
        catch (EcAopServerBizException e) {
            throw new EcAopServerBizException("9999", e.getMessage());
        }
        Map threePartRet = new HashMap();
        Object threePartBody = threePartExchange.getOut().getBody();
        if (threePartBody instanceof String) {
            threePartRet = JSON.parseObject((String) threePartBody);
        }
        else {
            threePartRet = threePartExchange.getOut().getBody(Map.class);
        }
        List<Map> custInfoList = (ArrayList<Map>) threePartRet.get("custInfo");
        List<Map> userInfoList = (ArrayList<Map>) threePartRet.get("userInfo");
        Map custInfoMap = custInfoList.get(0);
        Map userInfoMap = userInfoList.get(0);
        String custId = String.valueOf(custInfoMap.get("custId"));
        Map retMap = new HashMap();
        if (null != productId && null != actPlanId) {

            msg.put("custId", custId);
            msg.put("serialNumber", msg.get("numId"));
            // 老用户业务校验产品默认传主产品
            if (null == productList || productList.isEmpty()) {
                throw new EcAopServerBizException("9999", "老用户业务校验请输入产品信息");
            }

            Map newProductMap = MapUtils.asMap("productId", productId, "productMode", "1");
            productList.clear();
            productList.add(newProductMap);
            // 重新塞入产品信息，为存费送费准备数据
            msg.put("productInfo", productList);
            body.put("msg", msg);
            // 胥总要求，调北六存费送费接口
            exchange.setMethodCode("mofc");
            exchange.getIn().setBody(body);
            N6OldOpenProcessor n6oldFeeChg = new N6OldOpenProcessor();
            Map n6RetMap = new HashMap();
            try {

                n6oldFeeChg.process(exchange);
                Message message = exchange.getOut();
                n6RetMap = (Map) message.getBody();
            }
            catch (EcAopServerBizException e) {
                throw new EcAopServerBizException("9999", e.getMessage());
                // retMap.put("checkCode", "9999");

            }
        }

        retMap.put("checkCode", "0000");
        retMap.put("certType", CertTypeChangeUtils.certTypeCbss2Mall((String) custInfoMap.get("certTypeCode")));
        retMap.put("certNum", custInfoMap.get("certCode"));
        retMap.put("customerName", custInfoMap.get("custName"));
        retMap.put("certAdress", custInfoMap.get("certAddr"));
        retMap.put("changeType", "4");
        retMap.put("sysType", "1");
        retMap.put("subscrbType", userInfoMap.get("subscrbType"));
        exchange.getOut().setBody(retMap);

    }
}
