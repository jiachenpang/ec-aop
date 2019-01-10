package com.ailk.ecaop.biz.sub.lan;

import java.util.ArrayList;
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
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;

/**
 * 固网光改移机提交
 * 
 * @author Yangzg
 *
 */
@EcRocTag("BroadChangeSub")
@SuppressWarnings(value = { "unchecked", "rawtypes", "static-access" })
public class BroadChangeSubProcessor extends BaseAopProcessor implements ParamsAppliable {
    private static final String[] PARAM_ARRAY = { "ecaop.trades.mecs.sub.paramtersmapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];

    @Override
    public void process(Exchange exchange) throws Exception {

        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");

        msg.putAll(preBaseData(exchange));
        if(IsEmptyUtils.isEmpty((Map)msg.get("payInfo"))){
            throw new EcAopServerBizException("9999", "必传节点payInfo未传！");
        }
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        LanUtils lan = new LanUtils();
        try {
            lan.preData(pmp[0], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
            lan.xml2Json("ecaop.trades.mecs.sub.template", exchange);
        } catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用正式提交失败！" + e.getMessage());
        }
        Map ordersId = new HashMap();
        ordersId.put("provOrderId", msg.get("ordersId"));
        exchange.getOut().setBody(ordersId);
    }

    private Map preBaseData(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");

        List<Map> feeInfolist = (List<Map>) msg.get("feeInfo");
        Map subOrderSubReq = new HashMap();
        List<Map> subOrderSubReqlist = new ArrayList<Map>();

        // 将多缴的预存和[预存]营业厅收入(营业缴费)_普通预存款合并
        int addMoney = 0;
        int oldAddMoney = 0;
        if (!IsEmptyUtils.isEmpty(feeInfolist)) {
            for (Map feeInfo : feeInfolist) {
                // 在提交的时候预写的预存
                if ("100000".equals(feeInfo.get("feeId"))) {
                    addMoney += Integer.valueOf(feeInfo.get("realFee") + "");
                    oldAddMoney += Integer.valueOf(feeInfo.get("origFee") + "");
                }
            }
            for (Map feeInfo : feeInfolist) {
                feeInfo.put("operateType", "1");
                if (!"100000".equals(feeInfo.get("feeId"))) {
                    String feeId = feeInfo.get("feeId") + "";
                    String feeMod = feeInfo.get("feeCategory") + "";

                    if ("99".equals(feeId)) {
                        feeId = "100000";
                    }
                    if ("4".equals(feeMod)) {
                        feeMod = "2";
                    }

                    feeInfo.put("feeCategory", feeMod);
                    if ("100000".equals(feeId))// 如果有预存就加，不是预存的话，就取原来的值
                    {
                        feeInfo.put("origFee", Integer.valueOf(feeInfo.get("origFee") + "") / 10 + oldAddMoney);
                        feeInfo.put("reliefFee", Integer.valueOf(feeInfo.get("reliefFee") + "") / 10 + addMoney);
                        feeInfo.put("realFee", Integer.valueOf(feeInfo.get("realFee") + "") / 10 + addMoney);
                    } else {
                        feeInfo.put("origFee", Integer.valueOf(feeInfo.get("origFee") + "") / 10);
                        feeInfo.put("reliefFee", Integer.valueOf(feeInfo.get("reliefFee") + "") / 10);
                        feeInfo.put("realFee", Integer.valueOf(feeInfo.get("realFee") + "") / 10);
                    }

                    feeInfo.put("isPay", "1");
                    feeInfo.put("payTag", "1");
                    Exchange tempExchange = ExchangeUtils.ofCopy(exchange, msg);
                    feeInfo.put("calculateId", GetSeqUtil.getSeqFromCb(tempExchange, "seq_trade_id"));
                    feeInfo.put("calculateTag", "N");
                }

            }
        }

        Object payType = new ChangeCodeUtils().changePayType(((Map) msg.get("payInfo")).get("payType"));
        ((Map) msg.get("payInfo")).put("payType", payType);
        ((Map) msg.get("payInfo")).put("subProvinceOrderId", msg.get("provOrderId"));

        msg.remove("feeInfo");

        subOrderSubReq.put("subProvinceOrderId", msg.get("ordersId"));
        subOrderSubReq.put("subProvinceOrderId", msg.get("provOrderId"));
        subOrderSubReq.put("feeInfo", feeInfolist);
        subOrderSubReqlist.add(subOrderSubReq);
        msg.put("provOrderId", msg.get("provOrderId"));
        msg.put("orderNo", msg.get("provOrderId"));
        msg.put("operationType", "01");
        msg.put("sendTypeCode", "0");
        msg.put("noteNo", null != msg.get("invoiceNo") ? msg.get("invoiceNo") : "11111111111111");
        msg.put("noteType", "1");
        msg.put("noteFlag", "1");
        msg.put("subOrderSubReq", subOrderSubReqlist);
        msg.put("origTotalFee", "0");
        msg.put("cancleTotalFee", "0");
        return msg;

    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
