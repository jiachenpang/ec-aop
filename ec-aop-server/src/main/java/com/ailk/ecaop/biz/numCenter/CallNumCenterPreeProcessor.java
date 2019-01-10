package com.ailk.ecaop.biz.numCenter;

/**
 * Created by Liu JiaDi on 2016/8/24.
 */

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.extractor.DealSysCodeExtractor;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.NumCenterUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("callNumCenterPreeProcessor")
public class CallNumCenterPreeProcessor extends BaseAopProcessor {

    LanUtils lan = new LanUtils();
    NumCenterUtils nc = new NumCenterUtils();

    @Override
    public void process(Exchange exchange) throws Exception {
        if (!"200".equals(String.valueOf(exchange.getProperty("HTTP_STATUSCODE")))) {
            return;
        }
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        String numSwitch = (String) msg.get("numSwitch");
        if ("1".equals(numSwitch)) {
            return;
        }
        Exchange exchangeNum = ExchangeUtils.ofCopy(exchange, msg);
        callNumCenterPreemtedNum(exchangeNum);

    }

    // 调号卡中心号码预占接口
    private void callNumCenterPreemtedNum(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        String numSwitch = (String) msg.get("numSwitch");

        if (!"0".equals(numSwitch)) {
            return;
        }
        List<Map> numIds = (List) msg.get("numId");
        msg.put("serialNumber", numIds.get(0).get("serialNumber"));
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

        // 判断号卡报文体是否有错 by wangmc 20170904
        // nc.dealReturnHead(exchange);
        nc.dealReturnHead(exchange, "PREEMPTED_NUM_RSP");
        try {
            msg.put("numState", "2");
            nc.InsertTradeInfo(msg);
        }
        catch (Exception e) {
            System.out.println("插表失败" + e.getMessage());
        }
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

}
