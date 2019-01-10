package com.ailk.ecaop.biz.cust;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.TransFeeUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("PayMentFeeProcessor")
@SuppressWarnings({ "unchecked", "rawtypes", "static-access" })
public class PayMentFeeProcessor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.param.mapping.sfck", "ecaop.cbpm.spfs.ParametersMapping",
            "ecaop.pfdb.spfs.ParametersMapping", "ecaop.masb.chph.gifa.ParametersMapping",
            "ecaop.cbpm.spfs1.ParametersMapping" }; // 员工校验,缴费,保证金扣除返销,生成流水
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[5];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        // 准备参数
        String feeTime = (String) msg.get("feeTime");
        if (null != feeTime && 17 == feeTime.length()) {
            feeTime = feeTime.replaceAll("[ :]", "");
            msg.put("feeTime", feeTime);
        }
        // 员工校验 不再做员工校验 by wangmc 20180601
        LanUtils lan = new LanUtils();
        // lan.preData(pmp[0], exchange);
        // CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
        // lan.xml2Json4ONS("ecaop.template.3g.staffcheck", exchange);
        // exchange.getIn().setBody(body);
        // 判断渠道类型
        String channelType = (String) msg.get("channelType");

        if (channelType.startsWith("10") || "1".equals(msg.get("markingTag"))) { // 自有渠道
            // 调用CB缴费接口
            msg.put("custId", msg.get("serialNumber"));
            // msg.put("fee", null == msg.get("fee") ? "0" : msg.get("fee") + "0");
            msg.put("fee", TransFeeUtils.transFee(msg.get("fee"), 1));
            msg.put("time", msg.get("feeTime"));
            // 新增方法代码优化
            OwnChannel(exchange, msg);
            
			// RGD2018073100003-增加峰行动处理 by wangmc 20180912
            dealFengType(msg);
            // 增加下发province_code eparchy_code city_code 161027
            msg.put("provinceCode", msg.get("province"));
            msg.put("eparchyCode", msg.get("city"));
            msg.put("cityCode", msg.get("district"));
            body.put("msg", msg);
            exchange.getIn().setBody(body);
            if (IsEmptyUtils.isEmpty(msg.get("transIdo"))) {
                lan.preData(pmp[1], exchange);
            }
            else {
                lan.preData(pmp[4], exchange);
            }
            body = (Map) exchange.getIn().getBody();
            String transIDO = body.get("transIDO") + "";// 取transIDO用
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.cbss.services.PayMentFee");
            lan.xml2JsonNoBody("ecaop.cbpm.spfs.template", exchange);
            Map retMsg = new HashMap();
            retMsg.put("traId", transIDO);
            exchange.getOut().setBody(retMsg);
        }
        else if (channelType.startsWith("20")) { // 社会渠道
            // 社会渠道缴费流程
            SocialChannel(exchange);
        }
    }

    private void OwnChannel(Exchange exchange, Map msg) {
        String saleModType = (String) msg.get("saleModType");
        if ("WOO".equals(msg.get("chargeParty"))) { // 支付机构编码增加WOO：WO+能力平台 by maly 180130
            msg.put("chanType", "80");
            msg.put("accessType", "80");
        }
        // 如果是行销渠道单独处理 by:cuij
        else if ("1".equals(msg.get("markingTag"))) {
            // 销售模式类型为0时，代表自营厅，1为行销渠道直销模式
            msg.put("chanType", "0".equals(saleModType) ? "61" : "60");
            msg.put("accessType", "0".equals(saleModType) ? "61" : "60");
        }
        else {
            if ("zjpre".equals(exchange.getAppCode()) && "0".equals(saleModType)) { // 针对浙江沃受理zjpre.sub做分支
                msg.put("chanType", "61");
                msg.put("accessType", "61");
            }
            else {
                msg.put("chanType", "10"); // 沃受理
                msg.put("agenpayFlag", "0");// 代理商扣款标识 0-非代理商 1-代理商
            }
        }

    }

    /**
     * 社会渠道缴费流程
     * @param msg
     */
    private void SocialChannel(Exchange exchange) throws Exception {
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
        if (!"0".equals(msg.get("AgenTag"))) {// RCQ2018060100039-关于沃受理行销CBSS缴费AOP接口改造需求
            lan.preData(pmp[2], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.AgencyAcctPaySer");
            lan.xml2Json("ecaop.pfdb.spfs.template", exchange);
        }
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
        msg.put("custId", msg.get("serialNumber"));
        msg.put("fee", TransFeeUtils.transFee(msg.get("fee"), 1));
        msg.put("time", msg.get("feeTime"));
        // 支付机构编码增加WOO：WO+能力平台 by maly 180130
        String chargeParty = (String) msg.get("chargeParty");
        msg.put("chanType", "WOO".equals(chargeParty) ? "80" : "10");

        // RGD2018073100003-增加峰行动处理 by wangmc 20180912
        dealFengType(msg);

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
        // 取到transIDO
        body = (Map) exchange.getIn().getBody();
        transIDO = (String) body.get("transIDO");// 先取到transIDO
        CallEngine.aopCall(exchange, "ecaop.comm.conf.url.cbss.services.PayMentFee");
        lan.xml2JsonNoBody("ecaop.cbpm.spfs.template", exchange);
        // 代理商缴费成功
        retMap = new HashMap();
        retMap.put("traId", transIDO);
        retMap.put("orderId", deductTradeId);// 代理商保证金扣除的订单
        exchange.getOut().setBody(retMap);
    }

    /**
     * 保证金扣除/返销接口公共参数准备
     * @param msg
     * @param tradeType
     * @return
     */
    private Map preData(Map msg, String tradeType) {
        msg.put("tradeType", tradeType); // 0 扣除 ,1 返销
        msg.put("tradeDate", getDate());
        msg.put("tradeTime", msg.get("feeTime"));
        msg.put("channelId", msg.get("channelId"));
        msg.put("eparchyCode", msg.get("city"));
        msg.put("cityCode", msg.get("district"));
        msg.put("payFee", msg.get("fee"));
        msg.put("activityType", "1");// 营销活动类型
        // 暂时添加两个定值
        msg.put("moduleFlag", "0");
        msg.put("serviceClassCode", "0050");
        return msg;
    }

    /**
     * 获取格式为yyyyMMdd格式的当前时间
     * @return
     */
    public String getDate() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        return format.format(new Date());
    }

    /**
     * 获取含有毫秒的当前时间
     * @return
     */
    public String getSysDate() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmssSSS");
        return format.format(new Date());
    }

    /**
     * RGD2018073100003-处理峰行动字段 wangmc 20180912
     * @param msg
     */
    public void dealFengType(Map msg) {
        // 1:峰行动
        if ("1".equals(msg.get("fengTag"))) {
            msg.put("chanType", "90");
            msg.put("accessType", "90");
            // msg.put("accessType", "90");
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
