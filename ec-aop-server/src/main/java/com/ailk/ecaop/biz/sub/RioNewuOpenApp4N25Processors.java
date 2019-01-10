package com.ailk.ecaop.biz.sub;

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
import com.ailk.ecaop.common.extractor.DealSysCodeExtractor;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.NumCenterUtils;
import com.alibaba.fastjson.JSON;

/**
 * 一号多卡，主附卡开户处理申请
 * 
 * @author shenzy 20170109
 */
@EcRocTag("rioNewuOpenApp4N25")
public class RioNewuOpenApp4N25Processors extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.trades.rnoa.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];
    NumCenterUtils nc = new NumCenterUtils();

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = (Map) exchange.getIn().getBody();
        Map msg = (Map) body.get("msg");
        // 号卡中心做号码预占操作
        Exchange exchangeNum = ExchangeUtils.ofCopy(exchange, msg);
        String ordersId = (String) msg.get("ordersId");

        preCommitParam(msg);
        LanUtils lan = new LanUtils();
        try {
            lan.preData(pmp[0], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.RioOrderSer");
            lan.xml2Json("ecaop.trades.rnoa.template", exchange);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "调用预提交失败！" + e.getMessage());
        }
        body = (Map) exchange.getOut().getBody();
        body.put("provOrderId", body.get("orderId"));
        body.put("orderId", ordersId);
        callNumCenterPreemtedNum(exchangeNum);

    }

    /**
     * 准备预提交参数
     * 
     * @param msg
     * @return
     */
    public Map preCommitParam(Map msg) {
        msg.put("orderId", msg.get("ordersId"));
        msg.put("tradeTypeCode", "0160");
        msg.put("msisdnA", msg.get("serialNumA"));
        msg.put("msisdnB", msg.get("serialNumB"));
        msg.put("msisdnName", msg.get("attachName"));
        // 处理附卡信息
        List<Map> simCardData = (List<Map>) msg.get("simCardData");
        if (!IsEmptyUtils.isEmpty(simCardData)) {
            for (Map cardData : simCardData) {
                cardData.put("iccid", cardData.get("eiccid"));
                cardData.put("imsi", cardData.get("eimsi"));
            }
        }
        // 处理发展人信息
        List<Map> recomInfos = (List<Map>) msg.get("recomInfo");
        if (null != recomInfos && recomInfos.size() > 0) {
            for (Map recomInfo : recomInfos) {
                recomInfo.put("developId", recomInfo.get("recomPersonId"));
                recomInfo.put("developName", recomInfo.get("recomPersonName"));
                recomInfo.put("developCity", recomInfo.get("recomCity"));
                recomInfo.put("developDepart", recomInfo.get("recomDepartId"));
            }
        }
        msg.put("developInfo", recomInfos);
        return msg;
    }

    // 调号卡中心号码预占接口
    private void callNumCenterPreemtedNum(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        String numSwitch = (String) msg.get("numSwitch");
        if (!"0".equals(numSwitch)) {
            return;
        }
        msg.put("serialNumber", msg.get("serialNumB"));
        // 获取sysCode
        new DealSysCodeExtractor().extract(exchange);
        // 准备参数
        dealReqPreemted(exchange);
        try {
            CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.numbercenter.preemptedNum");
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用号卡中心预占接口失败！" + e.getMessage());
        }

        nc.dealReturnHead(exchange);

    }

    private void dealReqPreemted(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map requestBody = new HashMap();
        Map requestTempBody = new HashMap();
        requestTempBody.putAll(nc.changeCommonImplement(msg));
        try {
            requestBody.putAll(NumFaceHeadHelper.creatHead());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        requestTempBody.put("SYS_CODE", nc.changeSysCode(exchange));
        requestTempBody.put("SERIAL_NUMBER", msg.get("serialNumber"));
        Map resultMap = new HashMap();
        resultMap.put("PREEMPTED_NUM_REQ", requestTempBody);
        requestBody.put("UNI_BSS_BODY", resultMap);
        exchange.getIn().setBody(JSON.toJSON(requestBody));
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
