package com.ailk.ecaop.biz.sub.olduser;

/**
 * Created by Liu JiaDi on 2016/7/27.
 */

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.res.ResourceStateChangeUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.alibaba.fastjson.JSON;
import org.apache.commons.lang.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 老用户优惠购机提交接口
 * 
 * @auther Liu JiaDi
 * @create 2016_07_27_11:46
 */
@EcRocTag("OldUserActivitySubCbss")
public class OldUserActivitySubCbss extends BaseAopProcessor {
    private LanUtils lan = new LanUtils();

    @Override
    public void process(Exchange exchange) throws Exception {
        // TODO Auto-generated method stub
        Map body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) exchange.getIn().getBody(Map.class).get("msg"));
        }
        else {
            msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
        }

        String provOrderId = String.valueOf(msg.get("provOrderId"));
        lan.preData("ecaop.trades.mpsb.ParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
        lan.xml2Json("ecaop.trades.mpsb.template", exchange);

        Map retMap = exchange.getOut().getBody(Map.class);
        retMap.put("provOrderId", provOrderId);
        terSale(exchange, msg);
        exchange.getOut().setBody(retMap);

    }

    private void terSale(Exchange exchange, Map msg) {
        Map body = exchange.getIn().getBody(Map.class);
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        List<Map> tradeInfo = dao.queryResInfoByTradeId(msg);
        if (null != tradeInfo && tradeInfo.size() > 0) {
            String resourceCode = (String) tradeInfo.get(0).get("RESOURCE_CODE");
            if (StringUtils.isEmpty(resourceCode)) {
                throw new EcAopServerBizException("9999", "根据订单获取终端信息失败，请检查输入的订单号");
            }
            String activityType = (String) tradeInfo.get(0).get("ACTIVITY_TYPE");
            if (StringUtils.isEmpty(activityType)) {
                throw new EcAopServerBizException("9999", "根据订单获取活动信息失败，请检查输入的订单号");
            }
            if (activityType.startsWith("ZBJM") || activityType.startsWith("ZBJA") || activityType.startsWith("ZBJB") || activityType.startsWith("ZBJC")) {
                return;
            }
            String SERIAL_NUMBER = (String) tradeInfo.get(0).get("SERIAL_NUMBER");
            if (StringUtils.isEmpty(SERIAL_NUMBER)) {
                throw new EcAopServerBizException("9999", "根据订单获取号码信息失败，请检查输入的订单号");
            }
            msg.put("resourcesCode", resourceCode);
            msg.put("number", SERIAL_NUMBER);
            msg.put("activeType", changeActiTypeCode(activityType));
            if (!IsEmptyUtils.isEmpty(tradeInfo.get(0).get("SUBSCRIBE_ID"))) {
                msg.put("ordersId", tradeInfo.get(0).get("SUBSCRIBE_ID"));
            }
        }
        else {
            throw new EcAopServerBizException("9999", "获取预受理订单信息失败");
        }

        msg.put("tradeType", "11");
        msg.put("operType", "06");
        body.put("msg", msg);
        exchange.getIn().setBody(body);

        try {
            //割接省份需要调用新零售中心
            if (EcAopConfigLoader.getStr("ecaop.global.param.resources.aop.province").contains((String) msg.get("province"))) {
                ResourceStateChangeUtils resourceStateChangeUtils = new ResourceStateChangeUtils();
                resourceStateChangeUtils.realOccupiedResourceInfo(exchange);
            } else {
                lan.preData("ecaop.masb.mold.sale.ParametersMapping", exchange);
                CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.cbss");
                lan.xml2Json4ONS("ecaop.trades.mold.sale.template", exchange);
            }
        } catch (Exception e) {
            throw new EcAopServerBizException("9999", e.getMessage());
        }
    }

    // 活动类型转换
    private String changeActiTypeCode(String activityType) {
        if (activityType.startsWith("YCMP")) {
            if (activityType.startsWith("YCMP003")) {
                activityType = "04";
            }
            else {
                activityType = "01";
            }
        }
        // 03合约购机 02 购手机送话费 01预存话费送手机
        else if (activityType.startsWith("GJRM")) {
            activityType = "02";
        } else if (activityType.startsWith("HYSJ") || activityType.startsWith("HYHJ") || activityType.startsWith("HYGJ")) {
            activityType = "03";
        } else {
            activityType = "";
        }
        return activityType;
    }

}
