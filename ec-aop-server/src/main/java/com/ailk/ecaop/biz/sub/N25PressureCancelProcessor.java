package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.config.Config;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.extractor.TransIdFromRedisValueExtractor;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.dao.essbusiness.EssProvinceDao;
import com.alibaba.fastjson.JSON;

@EcRocTag("N25PressureCancel")
public class N25PressureCancelProcessor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.trades.sccc.cancelTml.paramtersmapping",
            "ecaop.trades.scccs.cancel.crm.paramtersmapping",
            "ecaop.trades.scccs.orderActivateOrCancel.ParametersMapping",
            "ecaop.trades.scccs.cancelNum.paramtersmapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[4];
    private static String provinceList = Config.getStr("ecaop.trades.sell.mob.opap4.province", "|");
    private static LanUtils lan = new LanUtils();
    private static EssProvinceDao n25Dao = new EssProvinceDao();

    @Override
    public void process(Exchange exchange) throws Exception {
        cancelTml(exchange);
        cancelCard(exchange);
        cancelOrder(exchange);
        cancelNumber(exchange);
        dealReturn(exchange);

    }

    public void dealReturn(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }

        Map retMap = exchange.getOut().getBody(Map.class);
        if (retMap == null) {
            Map outMap = new HashMap();
            outMap.put("orderId", msg.get("orderId"));
            exchange.getOut().setBody(outMap);
        }
        else {
            retMap.put("orderId", msg.get("orderId"));
            exchange.getOut().setBody(retMap);
        }

    }

    public void cancelNumber(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        // 获取msg信息
        Object msgObject = body.get("msg");
        Map msg = null;
        if (msgObject instanceof String) {
            msg = JSON.parseObject(msgObject.toString());
        }
        else {
            msg = (Map) msgObject;
        }
        List<Map> subscribeInfo = n25Dao.qrySubscribeInfo(msg);
        if (null == subscribeInfo || subscribeInfo.isEmpty()) {//省份2g开户无号码信息
            return;
        }
        // 调号码释放接口之前准备参数
        paraNumDataNew(msg);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        try {

            lan.preData(pmp[3], exchange);
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.cbss");
            lan.xml2Json4ONS("ecaop.trades.scccs.cancelNum.template", exchange);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "号码释放失败" + e.getMessage());
        }
        exchange.getIn().setBody(body);

    }

    /**
     * 调号码状态变更接口前准备参数
     * 
     * @param
     */
    private void paraNumDataNew(Map msg) {
        List resourcesInfos = new ArrayList();
        Map resourcesInfo = new HashMap();
        resourcesInfo.put("keyChangeTag", "0");
        resourcesInfo.put("proKeyMode", "0");
        resourcesInfo.put("proKey", GetSeqUtil.getSeqFromCb());
        resourcesInfo.put("resourcesType", "0".equals(msg.get("resourcesType")) ? "02" : "01");// 0跟02表示后付费
        resourcesInfo.put("resourcesCode", msg.get("serialNumber"));
        resourcesInfo.put("occupiedFlag", "4");
        resourcesInfo.put("identifyingCode", "notifyCB");// 此值用于在T4000017接口中做判断，如果是notifyCB时表示要通知CB做号码释放操作
        resourcesInfo.put("snChangeTag", "0");
        resourcesInfos.add(resourcesInfo);
        msg.put("resourcesInfo", resourcesInfos);
    }

    public void cancelOrder(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        msg.put("operType", "1");
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        System.out.println("#########msg###########" + msg);
        try {
            lan.preData(pmp[2], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.RealNameCardSer");//TODO:地址
            lan.xml2Json("ecaop.trades.orderActivateOrCancel.template", exchange);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "省份订单返销失败" + e.getMessage());
        }
        exchange.getIn().setBody(body);

    }

    public void cancelCard(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        List<Map> subscribeInfo = n25Dao.qrySubscribeInfo(msg);
        if (null == subscribeInfo || subscribeInfo.isEmpty()) {
            return;
        }
        String bipCode = (String) subscribeInfo.get(0).get("BIP_CODE");// 成卡 BIP2F064
        if (!"BIP2F064".equals(bipCode)) {//白卡去crm做返销
            //割接省份走号卡中心返销卡资源
            if (provinceList.contains((String) msg.get("province"))) {
                //TODO:

            }
            else {
                String serialNumber = (String) msg.get("serialNumber");
                // 白卡返销调用crm处理接口
                if ("1".equals(msg.get("cardType")) && StringUtils.isNotEmpty(serialNumber)) {
                    String simCard = (String) msg.get("iccid");
                    if (StringUtils.isNotEmpty(simCard)) {
                        msg.put("numID", serialNumber);
                        msg.put("iccid", simCard);
                        body.put("msg", msg);
                        exchange.getIn().setBody(body);
                        try {
                            lan.preData(pmp[1], exchange);
                            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.osn.syncreceive.9900");
                            lan.xml2Json1ONS("ecaop.trades.sccc.cancel.crm.template", exchange);
                        }
                        catch (Exception e) {
                            throw new EcAopServerBizException("9999", "卡资源返销失败" + e.getMessage());
                        }
                    }
                }

            }
        }
        exchange.getIn().setBody(body);
    }

    public void cancelTml(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        // Map msg = (Map) body.get("msg");
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        msg.put("tradeId", creatTransIDO(exchange));
        msg.put("ordersId", msg.get("tradeId"));
        msg.put("essOrigOrderId", msg.get("orderId"));
        body.put("msg", msg);
        String resourceCode = (String) msg.get("imei");
        if (StringUtils.isNotEmpty(resourceCode)) {
            // 调用3GESS为cBSS提供的返销提交接口
            exchange.getIn().setBody(body);
            try {
                lan.preData(pmp[0], exchange);
                CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.cbss");
                lan.xml2Json4ONS("ecaop.trades.sccc.cancelTml.template", exchange);
            }
            catch (Exception e) {
                throw new EcAopServerBizException("9999", "终端返销失败" + e.getMessage());
            }
        }
        exchange.getIn().setBody(body);

    }

    private Object creatTransIDO(Exchange exchange) {
        String str[] = { "@50"
        };
        TransIdFromRedisValueExtractor transId = new TransIdFromRedisValueExtractor();
        transId.applyParams(str);
        return transId.extract(exchange);
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }

    }

}
