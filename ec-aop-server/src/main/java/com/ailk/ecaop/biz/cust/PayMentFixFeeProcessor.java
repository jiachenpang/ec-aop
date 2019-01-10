package com.ailk.ecaop.biz.cust;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.TransFeeUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("PayMentFixFeeProcessor")
public class PayMentFixFeeProcessor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.gxzh.spff.ParametersMapping",
            "ecaop.cbpm.spff.ParametersMapping", "ecaop.pfdb.spfs.ParametersMapping",
            "ecaop.masb.chph.gifa.ParametersMapping", "ecaop.cbpm.spff1.ParametersMapping" };// iptv宽带转换,缴费,保证金扣除返销,生成流水
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[5];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        LanUtils lan = new LanUtils();
        String operateType = msg.get("operateType") + "";
        String code = "";
        Object serialNumber = "";
        Object routeNumber = "";
        Object routeType = "";
        // 判断号码类型
        if ("2,3".contains(operateType)) {
            code = (String) msg.get("code");
            serialNumber = changeNumber(exchange, operateType);
            if (null == serialNumber) {
                throw new EcAopServerBizException("IPTV或互联网电视账号转换异常");
            }
            routeNumber = serialNumber + "," + code;
            routeType = "06";
        }
        if ("0,1".contains(operateType)) {
            code = (String) msg.get("code");
            serialNumber = msg.get("serialNumber");
            if ("".equals(code) || null == code) {
                throw new EcAopServerBizException("固话或宽带缴费需传入区号！！");
            }
            routeNumber = "0".equals(operateType) ? code + serialNumber : serialNumber + "," + code;
            routeType = "0".equals(operateType) ? "04" : "06";
        }

        String channelType = (String) msg.get("channelType");
        String markingTag = (String) msg.get("markingTag");
        if (channelType.startsWith("10") || "1".equals(markingTag)) { // 自有渠道
            Map payMentReq = new HashMap();
            MapUtils.arrayPut(payMentReq, msg, MagicNumber.COPYARRAY);
            if ("WOO".equals(msg.get("chargeParty"))) { // 支付机构编码增加WOO：WO+能力平台 by maly 180130
                msg.put("accessType", "80");
            }
            else if ("1".equals(msg.get("markingTag"))) {
                payMentReq.put("accessType", "60");
            }
            else {
                payMentReq.put("accessType", "10");
                payMentReq.put("agenpayFlag", "0");// 代理商扣款标识 0-非代理商 1-代理商
            }
            // RGD2018073100003-增加峰行动处理 by wangmc 20180912
            payMentReq.put("fengTag", msg.get("fengTag"));
            dealFengType(payMentReq);
            payMentReq.put("payType", "0");
            payMentReq.put("payMentType", "01");
            payMentReq.put("procTime", msg.get("procTime"));
            payMentReq.put("payAgency", msg.get("chargeParty"));
            payMentReq.put("payTime", msg.get("payTime"));
            payMentReq.put("code", msg.get("code"));
            payMentReq.put("netType", msg.get("netType"));
            payMentReq.put("userNumber", serialNumber);
            payMentReq.put("routeNumber", routeNumber);
            payMentReq.put("routeType", routeType);
            payMentReq.put("fee", TransFeeUtils.transFee(msg.get("fee"), 1));
            payMentReq.put("transIdo", msg.get("transIdo"));
            body.put("msg", payMentReq);
            exchange.getIn().setBody(body);

            String transIDO = "";
            try {
                if (IsEmptyUtils.isEmpty(msg.get("transIdo"))) {
                    lan.preData(pmp[1], exchange);
                }
                else {
                    lan.preData(pmp[4], exchange);
                }
                body = (Map) exchange.getIn().getBody();
                transIDO = (String) body.get("transIDO");
                CallEngine.aopCall(exchange, "ecaop.comm.conf.url.cbss.services.PayMentFee");
                lan.xml2JsonNoBody("ecaop.cbpm.spff.template", exchange);
            }
            catch (Exception e) {
                e.printStackTrace();
                throw new EcAopServerBizException(e.getMessage());
            }
            // 处理返回信息
            // Map payRepMap = exchange.getOut().getBody(Map.class);
            Map retMap = new HashMap();
            retMap.put("traId", transIDO);
            retMap.put("serialNumber", serialNumber);// 修改为转换后的号码
            exchange.getOut().setBody(retMap);
        }
        else if (channelType.startsWith("20")) {// 社会渠道
            SocialChannel(exchange, serialNumber, routeNumber, routeType);
        }
    }

    /**
     * IPTV与宽带的关系转换
     * @param exchange
     * @param operateType
     * @return
     * @throws Exception
     */
    private Object changeNumber(Exchange exchange, Object operateType) throws Exception {
        LanUtils lan = new LanUtils();
        Map body = (Map) exchange.getIn().getBody();
        Map msg = (Map) body.get("msg");
        msg = MapUtils.asMap("province", msg.get("province"), "city", msg.get("city"), "district", msg.get("district"),
                "iptvNumber", msg.get("serialNumber"));
        if ("2".equals(operateType)) {
            msg.put("operateType", "1");
        }
        if ("3".equals(operateType)) {
            msg.put("operateType", "2");
        }
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        lan.preData(pmp[0], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.IptvToNumber");
        lan.xml2Json("ecaop.gxzh.spff.template", exchange);
        // 处理返回
        Map rspMap = (Map) exchange.getOut().getBody();

        // //如果号码类型operateType是2，取宽带统一编码serial_number字段 by maly 170504
        if ("2".equals(operateType)) {
            return rspMap.get("serialNumber");
        }

        return rspMap.get("acctNumber");
    }

    /**
     * 社会渠道缴费流程
     * @param msg
     */
    private void SocialChannel(Exchange exchange, Object serialNumber, Object routeNumber, Object routeType)
            throws Exception {
        // 保证金扣除
        Map body = exchange.getIn().getBody(Map.class);
        Object msgObject = body.get("msg");
        Map msg = null;
        if (msgObject instanceof String) {
            msg = JSON.parseObject(msgObject.toString());
        }
        else {
            msg = (Map) msgObject;
        }

        List tradeIds = GetSeqUtil.getSeqFromCb(pmp[3], exchange, "seq_trade_id", 2);
        String deductTradeId = tradeIds.get(0) + "";
        exchange.getIn().setBody(body);
        msg.put("tradeId", deductTradeId);
        // Object fee = msg.get("fee");// 保证金扣除的费用
        // 为了防止CB订单重复,延后50年
        byte[] array = deductTradeId.getBytes();
        array[2] += 5;
        deductTradeId = new String(array);
        msg.put("orderId", deductTradeId);
        msg = preData(msg, "0");

        body.put("msg", msg);
        exchange.getIn().setBody(body);

        LanUtils lan = new LanUtils();
        lan.preData(pmp[2], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.AgencyAcctPaySer");
        lan.xml2Json("ecaop.pfdb.spfs.template", exchange);
        // 如果扣除成功,调用代理商缴费接口
        String transIDO = "";
        // try {
        // 获取保证金扣除返销接口返回的是否扣款标识
        Map retMap = exchange.getOut().getBody(Map.class);
        List<Map> respInfo = (List<Map>) retMap.get("RESP_INFO");
        String contractFlag = "0";// 扣款标识:0 扣款 1无扣款
        if (!IsEmptyUtils.isEmpty(respInfo) && "1".equals(respInfo.get(0).get("CONTRACT_FLAG"))) {
            contractFlag = "1";
        }
        exchange.getIn().setBody(body);
        // payFeeTradeId = GetSeqUtil.getSeqFromCb(exchange, "seq_trade_id");
        exchange.getIn().setBody(body);
        // 支付机构编码增加WOO：WO+能力平台 chargeParty为 WOO时accessType传80 by maly 180130
        String chargeParty = (String) msg.get("chargeParty");
        msg.put("accessType", "WOO".equals(chargeParty) ? "80" : "10");
        // RGD2018073100003-增加峰行动处理 by wangmc 20180912
        dealFengType(msg);
        msg.put("payType", "0");
        msg.put("payMentType", "01");
        msg.put("procTime", msg.get("procTime"));
        msg.put("payAgency", msg.get("chargeParty"));
        msg.put("payTime", msg.get("payTime"));
        msg.put("netType", msg.get("netType"));
        msg.put("code", msg.get("code"));
        msg.put("userNumber", serialNumber);
        msg.put("routeNumber", routeNumber);
        msg.put("routeType", routeType);
        msg.put("fee", TransFeeUtils.transFee(msg.get("fee"), 1));
        msg.put("agenpayFlag", "1");// 代理商扣款标识 0-非代理商 1-代理商
        msg.put("chargeId", deductTradeId);// 保证金扣除流水
        msg.put("contractFlag", contractFlag);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        if (IsEmptyUtils.isEmpty(msg.get("transIdo"))) {
            lan.preData(pmp[1], exchange);
        }
        else {
            lan.preData(pmp[4], exchange);
        }
        body = (Map) exchange.getIn().getBody();
        transIDO = (String) body.get("transIDO");
        CallEngine.aopCall(exchange, "ecaop.comm.conf.url.cbss.services.PayMentFee");
        lan.xml2JsonNoBody("ecaop.cbpm.spff.template", exchange);
        // }
        // catch (Exception e) {
        // e.printStackTrace();
        // // 调用代理商缴费接口失败 ,调保证金返销
        // try {
        // exchange.getIn().setBody(body);
        // Object backTradeId = tradeIds.get(1);
        // exchange.getIn().setBody(body);
        // // msg = new HashMap((Map) body.get("msg"));
        // msg.put("tradeId", backTradeId);
        // msg.put("fee", fee);
        // msg = preData(msg, "1");
        //
        // Map acctRefundInfo = new HashMap(); // 原订单信息
        // acctRefundInfo.put("orgOrderId", deductTradeId);
        //
        // msg.put("orderId", backTradeId);
        // msg.put("acctRefundInfo", acctRefundInfo);
        // body.put("msg", msg);
        // exchange.getIn().setBody(body);
        // lan.preData(pmp[2], exchange);
        // CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.AgencyAcctPaySer");
        // lan.xml2Json("ecaop.pfdb.spfs.template", exchange);
        // }
        // catch (Exception e2) {
        // e2.printStackTrace();
        // // 保证金返销失败,返回信息,并返回缴费流水号
        // throw new EcAopServerBizException("2204", e2 + ",TransIDO:" + transIDO);
        // }
        // // 保证金返销成功,返回缴费失败
        // throw e;
        //
        // }

        // 代理商缴费成功
        retMap = new HashMap();
        retMap.put("traId", transIDO);
        exchange.getOut().setBody(retMap);
    }

    /**
     * 保证金扣除/返销接口公共参数准备
     * @param msg
     * @param tradeType
     * @return
     */
    private Map preData(Map msg, String tradeType) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        msg.put("tradeType", tradeType); // 0 扣除 ,1 返销
        msg.put("tradeDate", format.format(new Date()));
        msg.put("tradeTime", msg.get("procTime"));
        msg.put("channelId", msg.get("channelId"));
        msg.put("eparchyCode", msg.get("city"));
        msg.put("cityCode", msg.get("district"));
        msg.put("payFee", msg.get("fee"));
        msg.put("activityType", "1");// 营销活动类型
        // 暂时添加两个定值
        msg.put("moduleFlag", "0");
        msg.put("serviceClassCode", "0050");
        // 返销的值
        Map acctRefundInfo = new HashMap();
        acctRefundInfo.put("orgOrderId", msg.get("orderId"));
        msg.put("acctRefundInfo", acctRefundInfo);
        return msg;
    }

    /**
     * RGD2018073100003-处理峰行动字段 wangmc 20180912
     * @param msg
     */
    public void dealFengType(Map msg) {
        // 1:峰行动
        if ("1".equals(msg.get("fengTag"))) {
            msg.put("accessType", "90");
        }
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
}
