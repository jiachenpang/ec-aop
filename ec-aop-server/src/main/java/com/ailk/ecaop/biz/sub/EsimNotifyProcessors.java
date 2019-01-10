package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.NumCenterUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("EsimNotify")
public class EsimNotifyProcessors extends BaseAopProcessor {

    NumCenterUtils nc = new NumCenterUtils();

    @Override
    public void process(Exchange exchange) throws Exception {

        dealReqData(exchange);
        CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.numbercenter.unPreemptedEsimCard");
        nc.dealReturnHead(exchange);
        dealNumberCenterReturn(exchange);
    }

    private void dealReqData(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");

        Map UNPREEMPTED_ESIM_CARD_REQ = new HashMap();
        String sysCode = new NumCenterUtils().changeSysCode(exchange);
        UNPREEMPTED_ESIM_CARD_REQ.put("STAFF_ID", msg.get("operatorId"));
        UNPREEMPTED_ESIM_CARD_REQ.put("PROVINCE_CODE", msg.get("province"));
        UNPREEMPTED_ESIM_CARD_REQ.put("CITY_CODE", msg.get("city"));
        UNPREEMPTED_ESIM_CARD_REQ.put("DISTRICT_CODE", msg.get("district"));
        UNPREEMPTED_ESIM_CARD_REQ.put("CHANNEL_ID", msg.get("channelId"));
        UNPREEMPTED_ESIM_CARD_REQ.put("CHANNEL_TYPE", msg.get("channelType"));
        UNPREEMPTED_ESIM_CARD_REQ.put("DEPART_ID", msg.get("departId"));
        UNPREEMPTED_ESIM_CARD_REQ.put("TRADE_TYPE", msg.get("tradeType"));
        UNPREEMPTED_ESIM_CARD_REQ.put("SYS_CODE", sysCode);// 操作系统编码
        UNPREEMPTED_ESIM_CARD_REQ.put("ICCID", msg.get("iccid"));

        Map req = NumFaceHeadHelper.creatHead(exchange.getAppkey());
        req.put("UNI_BSS_BODY", MapUtils.asMap("UNPREEMPTED_ESIM_CARD_REQ", UNPREEMPTED_ESIM_CARD_REQ));
        exchange.getIn().setBody(JSON.toJSON(req));

    }

    private void dealNumberCenterReturn(Exchange exchange) {

        Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
        Map bodyMap = (Map) outMap.get("UNI_BSS_BODY");
        Map resultMap = (Map) bodyMap.get("UNPREEMPTED_ESIM_CARD_RSP");

        if (IsEmptyUtils.isEmpty(bodyMap) || IsEmptyUtils.isEmpty(resultMap)) {
            throw new EcAopServerBizException("9999", "eSIM卡解预占通知接口号卡中心返回结果为空");
        }
        if ("0000".equals(resultMap.get("RESP_CODE"))) {
            Map rspMap = new HashMap();
            List retList = new ArrayList<Map<String, String>>();
            Map reMap = new HashMap();
            reMap.put("respCode", resultMap.get("RESP_CODE"));
            reMap.put("respDesc", resultMap.get("RESP_DESC"));
            retList.add(reMap);
            rspMap.put("unpreemptedEsimCardRsp", retList);
            exchange.getOut().setBody(rspMap);
        }
        else {
            throw new EcAopServerBizException("9999", "号卡中心返回其他错误," + resultMap.get("RESP_DESC") + "");
        }
    }
}
