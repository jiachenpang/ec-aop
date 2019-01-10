package com.ailk.ecaop.biz.sub.lan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.brd.BrdYearlyPaySubN25Processor;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.YearPayUtils;

public class YearPayEcs4South25Sub extends YearPayEcs4South25Check {

    public YearPayEcs4South25Sub(ParametersMappingProcessor[] pmp) {
        super(pmp);
    }

    @Override
    public Map yearPayEcsCheck(Exchange exchange, Map msg) throws Exception {
        new YearPayUtils().callGetBroadbandAcctInfo(exchange);
        Map threePartInfo = new YearPayUtils().callCheckUserInfo(exchange, "0160");
        // boolean isNoChangeProduct = isNoChangeMainProduct(threePartInfo, msg);
        String province = msg.get("province").toString();
        Map userProduct = new YearPayUtils().callQryUserProInfo(exchange);
        if ("1".equals(msg.get("changeTag"))) {
            new BrdYearlyPaySubN25Processor().process(exchange, threePartInfo, userProduct, province);
            Map result = exchange.getOut().getBody(Map.class);
            result.put("isNoChangeProduct", "0");
            return result;
        }
        Map preSubmitMap = preSubmit(threePartInfo, msg, userProduct, province);
        Exchange preSubmitExchange = ExchangeUtils.ofCopy(exchange, preSubmitMap);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[0], preSubmitExchange);// ecaop.masb.spec.sProductChgParametersMapping
        CallEngine.wsCall(preSubmitExchange, "ecaop.comm.conf.url.osn.services.productchgser");
        lan.xml2Json("ecaop.masb.spec.template", preSubmitExchange);
        return preSubmitExchange.getOut().getBody(Map.class);
    }

    private void dealSubmit(Map submitMap, Map msg, Object provOrderId) {
        List<Map> subOrderSubReq = new ArrayList<Map>();
        Map subOrderSub = new HashMap();
        subOrderSub.put("subOrderId", msg.get("orderNo"));
        List<Map> fee = new ArrayList<Map>();
        List<Map> feeInfo = (List<Map>) msg.get("feeInfo");
        for (Map feeMap : feeInfo) {
            Map temp = new HashMap();
            temp.put("feeCategory", feeMap.get("feeMode"));
            temp.put("feeId", feeMap.get("feeTypeCode"));
            temp.put("feeDes", feeMap.get("feeTypeName"));
            temp.put("operateType", "1");
            temp.put("origFee", feeMap.get("oldFee"));
            temp.put("reliefFee", feeMap.get("derateFee"));
            temp.put("reliefResult", feeMap.get("derateRemark"));
            temp.put("realFee", feeMap.get("fee"));
            temp.put("isPay", feeMap.get("isPay"));
            temp.put("payTag", feeMap.get("payTag"));
            fee.add(temp);
        }
        subOrderSub.put("feeInfo", fee);
        subOrderSub.put("subProvinceOrderId", provOrderId);
        subOrderSubReq.add(subOrderSub);
        submitMap.put("subOrderSubReq", subOrderSubReq);
        submitMap.put("provOrderId", provOrderId);
        submitMap.put("origTotalFee", msg.get("totalFee"));
    }

    @Override
    public Map yearPayEcsSub(Exchange exchange, Map preSubmit, Map msg) throws Exception {
        Map submitMap = new HashMap();
        MapUtils.arrayPut(submitMap, msg, MagicNumber.COPYARRAY);
        Object orderNo = msg.get("orderNo");
        submitMap.put("orderNo", orderNo);
        Map body = exchange.getIn().getBody(Map.class);
        String apptx = (String) body.get("apptx");
        Map rspInfo = (Map) preSubmit.get("rspInfo");
        if (null == rspInfo) {
            throw new EcAopServerBizException("8888", "省份sProductChg接口未返回订单编号");
        }
        Object provOrderId = rspInfo.get("provOrderId");
        dealSubmit(submitMap, msg, provOrderId);
        List<Map> payList = new ArrayList<Map>();
        Map pay = new HashMap();
        System.out.println("zsqtest4  " + apptx + msg.get("payInfo"));
        if (!IsEmptyUtils.isEmpty((List<Map>) msg.get("payInfo"))) {
            List<Map> payInfoList = (List<Map>) msg.get("payInfo");
            System.out.println("zsqtest5  " + apptx + "haaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            for (Map payInfo : payInfoList) {
                pay.put("payType", ChangeCodeUtils.changePayForN25(payInfo.get("payType")));
                pay.put("payMoney", submitMap.get("origTotalFee"));
                pay.put("subProvinceOrderId", submitMap.get("provOrderId"));
                System.out.println("zsqtest6  " + apptx + pay);
            }
            payList.add(pay);
            submitMap.put("payInfo", payList);
            System.out.println("zsqtest7  " + apptx + submitMap);
        }
        Exchange submitExchange = ExchangeUtils.ofCopy(exchange, submitMap);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[1], submitExchange);// ecaop.masb.odsb.ActivityAryParametersMapping
        CallEngine.wsCall(submitExchange, "ecaop.comm.conf.url.osn.services.ordser");
        lan.xml2Json("ecaop.masb.odsb.template", submitExchange);
        Map outMap = new HashMap();
        outMap.put("code", "0000");
        outMap.put("detail", "OK");
        outMap.put("orderNo", orderNo);
        outMap.put("provOrderId", provOrderId);
        return outMap;
    }
}
