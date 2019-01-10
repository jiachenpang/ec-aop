package com.ailk.ecaop.biz.bank;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.utils.CallProcessorUtils;

@EcRocTag("BankRealpayQry")
public class BankdeductRealpayQryProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        msg.put("qryType", "02");
        dealTime(msg);
        CallProcessorUtils call = CallProcessorUtils.newCallIFaceUtils(exchange)
                .preData("ecaop.ecsb.qbcq.ParametersMapping")
                .aopCall("ecaop.comm.conf.url.bank.deductRealpay");
        try {
            call.xml2Json4ONS("ecaop.ecsb.qbcq.template");
        }
        catch (EcAopServerBizException e) {
            throw e;
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", e.getMessage() + "");
        }
        Map retMap = exchange.getOut().getBody(Map.class);
        Map outMap = new HashMap();
        outMap.put("respInfo", dealReturn(retMap));
        exchange.getOut().setBody(outMap);

    }

    private List<Map> dealReturn(Map retMap) {
        if (null == retMap) {
            return null;
        }
        List<Map> tradeInfo = (List<Map>) retMap.get("tradeInfo");
        if (null == tradeInfo || 0 == tradeInfo.size()) {
            throw new EcAopServerBizException("1801", "无对应记录");
        }
        List<Map> respInfo = new ArrayList<Map>();
        for (Map tradeMap : tradeInfo) {
            Map respMap = new HashMap();
            String tradeType = tradeMap.get("txType").toString();
            if (StringUtils.isEmpty(tradeType)) {
                throw new EcAopServerBizException("9999", "交易类型返回为空");
            }
            if ("01".equals(tradeType)) {
                tradeType = "0";
            }
            else if ("02".equals(tradeType)) {
                tradeType = "1";
            }
            respMap.put("tradeType", tradeType);
            respMap.put("payResult", tradeMap.get("code"));
            respMap.put("payMode", tradeMap.get("payMode"));
            respMap.put("payFee", tradeMap.get("payFee"));
            String orderId = "";
            if (null != tradeMap.get("orderId")) {
                orderId = tradeMap.get("orderId").toString();
            }
            respMap.put("orderId", orderId);
            String payMode = "";
            if (null != tradeMap.get("payMode")) {
                payMode = tradeMap.get("payMode").toString();
            }
            respMap.put("payMode", payMode);
            respMap.put("tradeDateTime", tradeMap.get("tradeTime"));
            respInfo.add(respMap);
        }
        return respInfo;
    }

    private void dealTime(Map msg) {
        String startTime = msg.get("startTime").toString();
        String endTime = msg.get("endTime").toString();
        if (startTime.length() < 8 || endTime.length() < 8) {
            throw new EcAopServerBizException("9999", "时间格式不合法!");
        }
        msg.put("startTime", startTime.substring(0, 8));
        msg.put("endTime", endTime.substring(0, 8));
    }
}
