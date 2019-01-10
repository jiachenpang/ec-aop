package com.ailk.ecaop.biz.product;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.TransFeeUtils;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;

/**
 * 产品变更流程正式提交步骤,调用cbss正式提交接口
 * @author Administrator
 */
@EcRocTag("preOrderSubProcessor")
public class PreOrderSubProcessor extends BaseAopProcessor implements ParamsAppliable {

    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];
    // 正式提交
    private static final String[] PARAM_ARRAY = { "ecaop.trades.sccc.cancel.paramtersmapping" };
    LanUtils lan = new LanUtils();

    @Override
    public void process(Exchange exchange) throws Exception {
        // 获取预提交之前外围传进来的请求参数
        Map headers = exchange.getIn().getHeader("strParams", Map.class);
        Map msg = JSON.parseObject(String.valueOf(headers.get("msg")));

        Object apptx = headers.get("apptx");
        System.out.println(apptx + ",产品变更流程---> 正式提交流程=======");
        // 获取预提交的返回
        Map preSubmitRet = exchange.getOut().getBody(Map.class);
        try {
            Map submitMsg = Maps.newHashMap();
            MapUtils.arrayPut(submitMsg, msg, MagicNumber.COPYARRAY);

            List<Map> subOrderSubReq = new ArrayList<Map>();

            // 23转4返回provOrderId为正式提交用的,bssOrderId是ESS订单号,但是两个值一样,totalFee一般为0
            submitMsg.put("provOrderId", preSubmitRet.get("bssOrderId"));
            // 套餐变更接口不返回provOrderId字段,默认取返回的bssOrderId字段
            Object orderNo = preSubmitRet.get("provOrderId");
            submitMsg.put("orderNo", IsEmptyUtils.isEmpty(orderNo) ? preSubmitRet.get("bssOrderId") : orderNo);
            Map subOrderSubMap = new HashMap();

            // 处理预提交返回的费用信息
            int totalFee = 0;
            if (!IsEmptyUtils.isEmpty(preSubmitRet.get("totalFee"))) {
                totalFee = (Integer) TransFeeUtils.transFee(preSubmitRet.get("totalFee"), 1);
            }
            List<Map> feeInfo = (ArrayList<Map>) preSubmitRet.get("feeInfo");
            if (!IsEmptyUtils.isEmpty(feeInfo)) {
                for (Map fee : feeInfo) {
                    fee.put("feeCategory", fee.get("feeCategory"));
                    fee.put("feeId", fee.get("feeId"));
                    fee.put("feeDes", fee.get("feeDes"));
                    // fee.put("feeCategory", fee.get("feeMode"));暂时注掉，应该是取错了。
                    // fee.put("feeId", fee.get("feeTypeCode"));
                    // fee.put("feeDes", fee.get("feeTypeName"));
                    fee.put("origFee", fee.get("oldFee"));
                    fee.put("isPay", "0");
                    fee.put("calculateTag", "N");
                    fee.put("payTag", "1");
                    fee.put("calculateId", GetSeqUtil.getSeqFromCb());
                    fee.put("calculateDate", DateUtils.getDate());
                }
                subOrderSubMap.put("feeInfo", feeInfo);
            }

            submitMsg.put("origTotalFee", totalFee);
            subOrderSubMap.put("subProvinceOrderId", submitMsg.get("provOrderId"));
            subOrderSubMap.put("subOrderId", submitMsg.get("orderNo"));
            subOrderSubReq.add(subOrderSubMap);
            submitMsg.put("subOrderSubReq", subOrderSubReq);

            long start = System.currentTimeMillis();
            Exchange submitExchange = ExchangeUtils.ofCopy(exchange, submitMsg);
            lan.preData(pmp[0], submitExchange);
            CallEngine.wsCall(submitExchange, "ecaop.comm.conf.url.cbss.services.orderSub");
            try {
                lan.xml2Json("ecaop.trades.sccc.cancel.template", submitExchange);
            }
            catch (Exception e) {
                throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "CBSS正式提交返回:" + e.getMessage());
            }
            System.out.println(apptx + "time,调用cbss正式提交用时:" + (System.currentTimeMillis() - start));
            // 处理正式提交的返回,取预提交返回的订单号返回
            Message out = new DefaultMessage();
            out.setBody(MapUtils.asMap("provOrderId", submitMsg.get("provOrderId")));
            exchange.setOut(out);
        }
        catch (Exception e1) {
            e1.printStackTrace();
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "调用CBSS正式提交接口报错:" + e1.getMessage());
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
