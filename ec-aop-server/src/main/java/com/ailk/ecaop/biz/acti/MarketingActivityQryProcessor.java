package com.ailk.ecaop.biz.acti;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;

@EcRocTag("MarketingActivityQry")
public class MarketingActivityQryProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.masb.quacq.ParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ActivityCancleInfo");
        lan.xml2Json("ecaop.trade.quacq.template", exchange);

        Map rspMap = exchange.getOut().getBody(Map.class);
        if (!IsEmptyUtils.isEmpty(rspMap)) {
            List<Map> info = new ArrayList();
            Map rspInfo = new HashMap();

            List<Map> productList = (List<Map>) rspMap.get("productInfo");
            if (!IsEmptyUtils.isEmpty(productList)) {
                List<Map> disList = new ArrayList();
                Map realOut = new HashMap();
                for (Map product : productList) {
                    realOut.put("productId", product.get("productId"));
                    realOut.put("productName", product.get("productName"));
                    realOut.put("provinceCode", product.get("provinceCode"));
                    realOut.put("productActiveTime", product.get("productActiveTime"));
                    realOut.put("productInactiveTime", product.get("productInactiveTime"));
                    realOut.put("activityId", product.get("activityId"));
                    realOut.put("activityName", product.get("activityName"));
                    realOut.put("activityActiveTime", product.get("activityActiveTime"));
                    realOut.put("activityInactiveTime", product.get("activityInactiveTime"));
                    List<Map> discntInfo = (List) product.get("discntInfo");

                    if (null != discntInfo && discntInfo.size() > 0) {
                        for (Map discnt : discntInfo) {
                            Map dis = new HashMap();
                            dis.put("packageId", discnt.get("packageId"));
                            dis.put("discntCode", discnt.get("discntCode"));
                            dis.put("discntName", discnt.get("discntName"));
                            dis.put("startDate", discnt.get("startDate"));
                            dis.put("endDate", discnt.get("endDate"));
                            dis.put("itemId", discnt.get("itemId"));

                            List<Map> attrInfo = (List<Map>) discnt.get("attrInfo");
                            for (Map attr : attrInfo) {
                                if (null == attr || attr.isEmpty()) {
                                    continue;
                                }
                                dis.put("tradeId", attr.get("attrValue"));
                            }
                            disList.add(dis);
                        }
                        realOut.put("discntInfo", disList);
                    }
                    info.add(realOut);
                }
            }
            List<Map> cancleFeeInfo = (List<Map>) rspMap.get("cancleFeeInfo");
            if (!IsEmptyUtils.isEmpty(cancleFeeInfo)) {
                List<Map> cancleList = new ArrayList();
                for (Map cancle : cancleFeeInfo) {
                    Map can = new HashMap();
                    can.put("cancleFeeType", cancle.get("cancleFeeType"));
                    can.put("cancleFee", cancle.get("cancleFee"));
                    cancleList.add(can);
                }
                rspInfo.put("cancleFeeInfo", cancleList);
            }
            rspInfo.put("productInfo", info);
            exchange.getOut().setBody(rspInfo);

        }
        else {
            throw new EcAopServerBizException("9999", "返回应答信息为空");
        }
    }

}
