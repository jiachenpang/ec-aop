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

@EcRocTag("QueryResiEsim")
public class QueryResiEsimProcessors extends BaseAopProcessor {

    NumCenterUtils nc = new NumCenterUtils();

    @Override
    public void process(Exchange exchange) throws Exception {

        dealReqData(exchange);
        CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.numbercenter.qryMulRes");
        nc.dealReturnHead(exchange);
        dealNumberCenterReturn(exchange);
    }

    private void dealReqData(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");

        Map QRY_MUL_RES_REQ = new HashMap();
        String sysCode = new NumCenterUtils().changeSysCode(exchange);
        QRY_MUL_RES_REQ.put("STAFF_ID", msg.get("operatorId"));
        QRY_MUL_RES_REQ.put("PROVINCE_CODE", msg.get("province"));
        QRY_MUL_RES_REQ.put("CITY_CODE", msg.get("city"));
        QRY_MUL_RES_REQ.put("CHANNEL_ID", msg.get("channelId"));
        QRY_MUL_RES_REQ.put("QUERY_TYPE", msg.get("queryType"));
        QRY_MUL_RES_REQ.put("SYS_CODE", sysCode);// 操作系统编码
        QRY_MUL_RES_REQ.put("TRADE_TYPE", msg.get("tradeType"));

        Map req = NumFaceHeadHelper.creatHead(exchange.getAppkey());
        req.put("UNI_BSS_BODY", MapUtils.asMap("QRY_MUL_RES_REQ", QRY_MUL_RES_REQ));
        exchange.getIn().setBody(JSON.toJSON(req));

    }

    private void dealNumberCenterReturn(Exchange exchange) {

        Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
        Map bodyMap = (Map) outMap.get("UNI_BSS_BODY");
        Map resultMap = (Map) bodyMap.get("QRY_MUL_RES_RSP");

        if (IsEmptyUtils.isEmpty(bodyMap) || IsEmptyUtils.isEmpty(resultMap)) {
            throw new EcAopServerBizException("9999", "号码状态查询接口号卡中心返回结果为空");
        }
        if ("0000".equals(resultMap.get("RESP_CODE"))) {

            Map reMap = new HashMap();
            reMap.put("numInfo", getNumInfo(resultMap));
            reMap.put("cardDataInfo", getCardData(resultMap));
            reMap.put("respCode", resultMap.get("RESP_CODE"));
            reMap.put("respDesc", resultMap.get("RESP_DESC"));
            exchange.getOut().setBody(reMap);
        }
        else {
            throw new EcAopServerBizException("9999", "号卡中心返回其他错误," + resultMap.get("RESP_DESC") + "");
        }
    }

    private Object getCardData(Map resultMap) {
        List<Map> cardDataInfoList = new ArrayList<Map>();

        Map cardDataInfo = (Map) resultMap.get("CARD_DATA_INFO");
        if (null != cardDataInfo && cardDataInfo.size() > 0) {
            Map cardData = new HashMap();
            cardData.put("iccid", cardDataInfo.get("ICCID"));
            cardData.put("imsi", cardDataInfo.get("IMSI"));
            if (!IsEmptyUtils.isEmpty(cardDataInfo.get("KI"))) {
                cardData.put("ki", cardDataInfo.get("KI"));
            }
            if (!IsEmptyUtils.isEmpty(cardDataInfo.get("MATERIAL_CODE"))) {
                cardData.put("materialCode", cardDataInfo.get("MATERIAL_CODE"));
            }
            if (!IsEmptyUtils.isEmpty(cardDataInfo.get("CARD_BIG_TYPE"))) {
                cardData.put("cardBigType", cardDataInfo.get("CARD_BIG_TYPE"));
            }
            if (!IsEmptyUtils.isEmpty(cardDataInfo.get("CARD_TYPE"))) {
                cardData.put("cardType", cardDataInfo.get("CARD_TYPE"));
            }
            if (!IsEmptyUtils.isEmpty(cardDataInfo.get("CARD_NAME"))) {
                cardData.put("cardName", cardDataInfo.get("CARD_NAME"));
            }
            cardDataInfoList.add(cardData);
        }
        else {
            throw new EcAopServerBizException("9999", "号卡中心未返回附卡号码信息");
        }

        return cardDataInfoList;
    }

    private Object getNumInfo(Map resultMap) {
        List<Map> numInfoList = new ArrayList<Map>();

        Map numInfo = (Map) resultMap.get("NUM_INFO");
        if (null != numInfo && numInfo.size() > 0) {
            Map send = new HashMap();
            send.put("serialNumber", numInfo.get("SERIAL_NUMBER"));
            if (!IsEmptyUtils.isEmpty(numInfo.get("PROVINCE_CODE"))) {
                send.put("provinceCode", numInfo.get("PROVINCE_CODE"));
            }
            if (!IsEmptyUtils.isEmpty(numInfo.get("CITY_CODE"))) {
                send.put("cityCode", numInfo.get("CITY_CODE"));
            }
            numInfoList.add(send);
        }
        else {
            throw new EcAopServerBizException("9999", "号卡中心未返回附卡卡信息");
        }
        return numInfoList;
    }

}
