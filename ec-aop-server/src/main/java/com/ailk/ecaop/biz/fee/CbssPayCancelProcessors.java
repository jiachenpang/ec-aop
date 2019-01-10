package com.ailk.ecaop.biz.fee;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;

@EcRocTag("cbssPayCancel")
public class CbssPayCancelProcessors extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.pfdb.jffx.ParametersMapping",
            "ecaop.trades.jffx.ParametersMapping", "ecaop.masb.chph.gifa.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[3];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
        Object ordersId = msg.get("ordersId");
        String channelType = (String) msg.get("channelType");
        if (channelType.startsWith("10")) {
            // 自有渠道直接调用返销接口
            Map body = new HashMap((Map) exchange.getIn().getBody());
            body.put("msg", putContractFlag(msg, "0", "", ""));
            exchange.getIn().setBody(body);
            LanUtils lan = new LanUtils();
            lan.preData(pmp[1], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.PayFeeKCSer");
            lan.xml2Json("ecaop.trades.jffx.payKcCanInfo.template", exchange);
            Map out = exchange.getOut().getBody(Map.class);
            out.put("provOrderId", ordersId);
            exchange.getOut().setBody(out);
        }
        else if (channelType.startsWith("20")) {
            // 社会渠道先调保证金返销，成功的话调返销接口，失败调用保证金扣除
            SocialChannel(exchange);
        }
    }

    private void SocialChannel(Exchange exchange) throws Exception {
        // 调用保证金返销接口
        Map msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
        Map body = new HashMap((Map) exchange.getIn().getBody());
        Object ordersId = msg.get("ordersId");
        // by wangmc 20170413 FIXME
        Object orgPayBankId = msg.get("orgPayBankId");// 省份订单-扣除保证金的订单号
        Object cbCanclePayId = msg.get("cbCanclePayId");// cbss订单-缴费用时的订单号
        if (IsEmptyUtils.isEmpty(orgPayBankId)) {
            throw new EcAopServerBizException("9999", "代理商返销未传入省份订单号,请确认!");
        }
        String backTradeId = GetSeqUtil.getSeqFromCb(pmp[2], exchange, "seq_trade_id", 1).get(0) + "";
        exchange.getIn().setBody(body);
        Object deductTradeId = GetSeqUtil.getSeqFromCb(pmp[2], exchange, "seq_trade_id", 1).get(0);
        msg.put("tradeId", backTradeId);
        msg = preData(msg, "1");

        Map acctRefundInfo = new HashMap(); // 原订单信息
        if ("36|70|74".contains(msg.get("province") + "")) {// 浙江,青海,湖南特殊处理
            acctRefundInfo.put("orgOrderId", orgPayBankId);
            acctRefundInfo.put("orgProvinceOrderId", cbCanclePayId);
        }
        else {
            acctRefundInfo.put("orgOrderId", cbCanclePayId);
            acctRefundInfo.put("orgProvinceOrderId", orgPayBankId);
        }

        // 返销保证金时的订单号,为了防止CB订单重复,延后50年 by wangmc
        byte[] array = backTradeId.getBytes();
        array[2] += 5;
        backTradeId = new String(array);
        msg.put("orderId", backTradeId);

        msg.put("acctRefundInfo", acctRefundInfo);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[0], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.AgencyAcctPaySer");
        lan.xml2Json("ecaop.pfdb.jffx.template", exchange);
        Map retMap = exchange.getOut().getBody(Map.class);
        try {
            // 获取contractFlag
            List<Map> respInfo = (List<Map>) retMap.get("respInfo");
            String contractFlag = null;
            if (respInfo != null && respInfo.size() > 0) {
                contractFlag = String.valueOf(respInfo.get(0).get("contractFlag"));
            }
            // 调用缴费返销
            body.put("msg", putContractFlag(msg, "1", backTradeId, contractFlag));
            exchange.getIn().setBody(body);
            lan.preData(pmp[1], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.PayFeeKCSer");
            lan.xml2Json("ecaop.trades.jffx.payKcCanInfo.template", exchange);
        }
        catch (Exception e) {
            e.printStackTrace();
            // 调保证金扣除接口
            msg.put("tradeId", deductTradeId);
            // msg.put("orderId", deductTradeId);
            // 返销保证金成功,返销费用失败时,重新扣除保证金应使用缴费时扣除保证金的订单号继续扣除保证金,以保证下次继续调用返销接口时,该订单可以返销
            // by wangmc 20170413 FIXME
            msg.put("orderId", orgPayBankId);
            msg = preData(msg, "0");

            body.put("msg", msg);
            exchange.getIn().setBody(body);

            lan.preData(pmp[0], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.AgencyAcctPaySer");
            lan.xml2Json("ecaop.pfdb.jffx.template", exchange);

            throw new EcAopServerBizException("9999", "调用缴费返销接口异常，未返销成功" + e.getMessage());
        }
        Map out = exchange.getOut().getBody(Map.class);
        out.put("provOrderId", ordersId);
        exchange.getOut().setBody(out);
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
        msg.put("tradeTime", getFullDate());
        msg.put("channelId", msg.get("channelId"));
        msg.put("eparchyCode", msg.get("city"));
        msg.put("cityCode", msg.get("district"));
        msg.put("payFee", msg.get("tradeFee"));
        msg.put("activityType", "1");// 营销活动类型
        // 暂时添加两个定值
        msg.put("moduleFlag", "0");
        msg.put("serviceClassCode", "0050");
        // // 返销的值
        // if ("1".equals(tradeType)) {
        // Map acctRefundInfo = new HashMap();
        // acctRefundInfo.put("orgOrderId", msg.get("orderId"));
        // msg.put("acctRefundInfo", acctRefundInfo);
        // }
        return msg;
    }

    /**
     * 在para中下发字段来标识是否为代理商返销 ,并下发返销保证金字段
     * by wangmc 20170321
     * @param msg
     * @param agrntflag 0-非代理商返销,1-代理商渠道返销
     * @param cancelTradeId 返销保证金时的流水
     * @param contractFlag 0代表扣款,1代表不扣款
     * @return
     */
    private Map putContractFlag(Map msg, String agrntflag, String cancelTradeId, String contractFlag) {
        List<Map> paraList = new ArrayList<Map>();
        String[] paraIds = new String[] { "AGRNT_FLAG", "CANCEL_TRADE_ID", "CONTRACT_FLAG" };
        contractFlag = IsEmptyUtils.isEmpty(contractFlag) ? "0" : contractFlag;
        String[] paraValues = new String[] { agrntflag, cancelTradeId, contractFlag };

        for (int i = 0; i < paraIds.length; i++) {
            if ("0".equals(agrntflag) && i > 0) { // 自有渠道返销,只放入AGRNT_FLAG字段
                break;
            }
            Map para = new HashMap();
            para.put("paraId", paraIds[i]);
            para.put("paraValue", paraValues[i]);
            paraList.add(para);
        }

        msg.put("para", paraList);
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
     * 获取格式为YYYYMMDD24MISS的当前时间
     */
    public String getFullDate() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
        return format.format(new Date());
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
