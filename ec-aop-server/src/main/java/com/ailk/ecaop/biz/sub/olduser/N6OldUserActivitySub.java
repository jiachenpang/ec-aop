package com.ailk.ecaop.biz.sub.olduser;

/**
 * Created by Liu JiaDi on 2016/7/11.
 */

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.axis.utils.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.alibaba.fastjson.JSON;

/**
 * 北六老用户优惠购机正式提交接口
 * 
 * @auther Liu JiaDi
 * @create 2016_07_11_11:47
 */
@EcRocTag("N6OldUserActivitySub")
public class N6OldUserActivitySub extends BaseAopProcessor {

    private final LanUtils lan = new LanUtils();

    @Override
    public void process(Exchange exchange) throws Exception {
        // TODO Auto-generated method stub
        Map body = exchange.getIn().getBody(Map.class);
        LanUtils lan = new LanUtils();
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) exchange.getIn().getBody(Map.class).get("msg"));
        }
        else {
            msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
        }
        String provinceCode = (String) msg.get("province");
        String provOrderId = String.valueOf(msg.get("provOrderId"));
        lan.preData("ecaop.trades.mpsb.ParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.OrderSub." + provinceCode);
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
            msg.put("resourcesCode", resourceCode);
            msg.put("activeType", changeActiTypeCode(activityType));
        }
        else {
            throw new EcAopServerBizException("9999", "获取预受理订单信息失败");
        }

        msg.put("tradeType", "11");
        msg.put("operType", "06");
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        try {
            lan.preData("ecaop.masb.mold.sale.ParametersMapping", exchange);
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.cbss");
            lan.xml2Json4ONS("ecaop.trades.mold.sale.template", exchange);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", e.getMessage());
        }
    }

    // 活动类型转换
    private String changeActiTypeCode(String activityType) {
        if (activityType.startsWith("YCMP")) {// 存费送机
            // if (activityType.startsWith("YCMP003")) {
            // activityType = "04";
            // }
            // else {
            activityType = "01";
            // }
        }
        // GJRM001 购手机入网送话费 02 购手机送话费//20150806 add GJRM002/GJRM003
        else if (activityType.startsWith("GJRM")) {// 购机送费
            activityType = "02";
        }
        // else if (activityType.startsWith("HYSJ") || activityType.startsWith("HYHJ") ||
        // activityType.startsWith("HYGJ")) {
        // activityType = "03";
        // }
        return activityType;
    }
}