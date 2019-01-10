package com.ailk.ecaop.biz.sim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.request.EcAopRequestParamException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.google.common.collect.Maps;

/**
 * 补换卡收费正式提交
 * @auther Zhao Zhengchang
 * @create 2016_03_23
 */
@EcRocTag("oldCardChangeSub")
public class OldCardChangeSubProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        // 参数处理
        LanUtils lan = new LanUtils();
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Object provOrderId = msg.get("essSubscribeId");
        msg.put("provOrderId", provOrderId);
        msg.put("essSubscribeId", provOrderId);
        msg.put("orderNo", provOrderId);
        msg.put("operationType", "01");
        msg.put("noteType", msg.get("invoiceNo"));
        // 调正式提交接口
        if ("2".equals(msg.get("opeSysType"))) { // 办理业务系统： 1：ESS 2：CBSS
            setFeeParams(msg);
            body.put("msg", msg);
            exchange.getIn().setBody(body);
            lan.preData("ecaop.trades.sccc.cancel.paramtersmapping", exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
            // lan.xml2Json("ecaop.trades.sccc.cancel.template", exchange);
        }
        else {
            setFeeParamsFor3G(msg);
            body.put("msg", msg);
            exchange.getIn().setBody(body);
            String province = msg.get("province").toString();
            if ("18|17|76|11|97|91".contains(province)) {
                // 调用正式提交接口
                String[] copyArray = { "operatorId", "province", "channelId", "city", "district", "channelType" };
                Map submitMap = Maps.newHashMap();
                MapUtils.arrayPut(submitMap, msg, copyArray);
                msg.put("changePayType", "0");
                msg.putAll(submitMap);
                body.put("msg", msg);
                exchange.getIn().setBody(body);
                lan.preData("ecaop.trades.sccc.cancel.paramtersmapping", exchange);
                CallEngine.wsCall(exchange, "ecaop.comm.conf.url.OrderSub" + "." + msg.get("province"));
                lan.xml2Json("ecaop.trades.sccc.cancel.template", exchange);
            }
            else {
                lan.preData("ecaop.param.mapping.smoss", exchange);
                CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
                lan.xml2Json4ONS("ecaop.template.3g.OldChangeCardAopSub", exchange);
            }
        }
        // 处理返回信息
        Map retMap = exchange.getOut().getBody(Map.class);
        Message out = new DefaultMessage();
        out.setBody(MapUtils.asMap("provOrderId", provOrderId, "para", retMap.get("para")));
        exchange.setOut(out);
    }

    private void setFeeParamsFor3G(Map msg) throws Exception {
        // feeInfo信息
        List<Map> feeInfos = (List<Map>) msg.get("feeInfo");
        String origTotalFee = (String) msg.get("origTotalFee");
        List<Map> subOrderSubReq = new ArrayList<Map>();
        List<Map> fees = new ArrayList<Map>();
        Map fee = new HashMap();
        if (feeInfos != null) {
            for (Map feeInfo1 : feeInfos) {
                Map feeInfo = new HashMap();
                String feeId = (String) feeInfo1.get("feeId");
                String feeDes = (String) feeInfo1.get("feeDes");
                String origFee = (String) feeInfo1.get("origFee");
                String reliefFee = (String) feeInfo1.get("reliefFee");
                String realFee = (String) feeInfo1.get("realFee");

                feeInfo.put("feeId", feeId);
                feeInfo.put("feeCategory", feeInfo1.get("feeCategory"));
                feeInfo.put("calculateTag", "N");
                feeInfo.put("feeDes", feeDes);
                feeInfo.put("origFee", origFee);
                feeInfo.put("reliefFee", reliefFee);
                feeInfo.put("reliefResult", feeInfo1.get("reliefResult"));
                feeInfo.put("realFee", realFee);
                feeInfo.put("operateType", "1");
                feeInfo.put("payTag", "1");
                feeInfo.put("calculateId", GetSeqUtil.getSeqFromCb());
                String acceptDate = GetDateUtils.getDate();
                feeInfo.put("calculateDate", GetDateUtils.transDate(acceptDate, 14));
                feeInfo.put("calculateStaffId", "");
                // feeInfo.put("payId", "1915121948114082");
                fees.add(feeInfo);
            }
            fee.put("feeInfo", fees);
            subOrderSubReq.add(fee);
            msg.put("subOrderSubReq", subOrderSubReq);
        }

        // 另外两个费用
        msg.put("origTotalFee", origTotalFee);
        // msg.put("cancleTotalFee", origTotalFee);
        // payInfo信息
        Object provOrderId = msg.get("essSubscribeId");
        Map payInfo = (Map) msg.get("payInfo");
        Map payInfoNew = new HashMap();
        payInfoNew.put("subProvinceOrderId", provOrderId);
        if ("2".equals(msg.get("opeSysType"))) {
            payInfoNew.put("payType", ChangeCodeUtils.changePayType(payInfo.get("payType")));
        }
        else {
            String province = msg.get("province").toString();
            if ("18|17|76|11|97|91".contains(province)) {
                payInfoNew.put("payType", ChangeCodeUtils.changePayType(payInfo.get("payType")));
            }
            else {
                payInfoNew.put("payType", payInfo.get("payType"));
            }
        }
        payInfoNew.put("payMoney", origTotalFee);
        payInfoNew.put("payOrg", payInfo.get("payOrg"));// 支付机构名称
        payInfoNew.put("payNum", payInfo.get("payNum"));// 支付账号
        payInfoNew.put("remark", "");
        msg.put("payInfo", payInfoNew);
    }

    private void setFeeParams(Map msg) throws Exception {
        // feeInfo信息
        List<Map> feeInfos = (List<Map>) msg.get("feeInfo");
        String origTotalFee = (String) msg.get("origTotalFee");
        origTotalFee = feeExchang(origTotalFee);
        List<Map> subOrderSubReq = new ArrayList<Map>();
        List<Map> fees = new ArrayList<Map>();
        Map fee = new HashMap();
        if (feeInfos != null) {
            for (Map feeInfo1 : feeInfos) {
                Map feeInfo = new HashMap();
                String feeId = (String) feeInfo1.get("feeId");
                String feeDes = (String) feeInfo1.get("feeDes");
                String origFee = (String) feeInfo1.get("origFee");
                String reliefFee = (String) feeInfo1.get("reliefFee");
                String realFee = (String) feeInfo1.get("realFee");

                feeInfo.put("feeId", feeId);
                feeInfo.put("feeCategory", feeInfo1.get("feeCategory"));
                feeInfo.put("calculateTag", "N");
                feeInfo.put("feeDes", feeDes);
                feeInfo.put("origFee", feeExchang(origFee));
                feeInfo.put("reliefFee", feeExchang(reliefFee));
                feeInfo.put("reliefResult", feeInfo1.get("reliefResult"));
                feeInfo.put("realFee", feeExchang(realFee));
                feeInfo.put("operateType", "1");
                feeInfo.put("payTag", "1");
                feeInfo.put("calculateId", GetSeqUtil.getSeqFromCb());
                String acceptDate = GetDateUtils.getDate();
                feeInfo.put("calculateDate", GetDateUtils.transDate(acceptDate, 14));
                feeInfo.put("calculateStaffId", "");
                // feeInfo.put("payId", "1915121948114082");
                fees.add(feeInfo);
            }
            fee.put("feeInfo", fees);
            subOrderSubReq.add(fee);
            msg.put("subOrderSubReq", subOrderSubReq);
        }

        // 另外两个费用
        msg.put("origTotalFee", origTotalFee);
        // msg.put("cancleTotalFee", origTotalFee);
        // payInfo信息
        Object provOrderId = msg.get("essSubscribeId");
        Map payInfo = (Map) msg.get("payInfo");
        Map payInfoNew = new HashMap();
        payInfoNew.put("subProvinceOrderId", provOrderId);
        if ("2".equals(msg.get("opeSysType"))) {
            payInfoNew.put("payType", ChangeCodeUtils.changePayType(payInfo.get("payType")));
        }
        else {
            String province = msg.get("province").toString();
            if ("18|17|76|11|97|91".contains(province)) {
                payInfoNew.put("payType", ChangeCodeUtils.changePayType(payInfo.get("payType")));
            }
            else {
                payInfoNew.put("payType", payInfo.get("payType"));
            }
        }
        payInfoNew.put("payMoney", origTotalFee);
        payInfoNew.put("payOrg", payInfo.get("payOrg"));// 支付机构名称
        payInfoNew.put("payNum", payInfo.get("payNum"));// 支付账号
        payInfoNew.put("remark", "");
        msg.put("payInfo", payInfoNew);
    }

    private String feeExchang(String str) {
        String value1 = null;
        try {
            int v1 = Integer.parseInt(str);
            if (v1 % 10 != 0) {
                throw new EcAopRequestParamException("费用从厘转换为分失败");
            }
            v1 = v1 / 10;
            value1 = v1 + "";
            return value1;
        }
        catch (Exception e) {
            throw new EcAopRequestParamException(e.getMessage());
        }
    }
}
