package com.ailk.ecaop.biz.res;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.NumCenterUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("getCardDataInfo")
public class GetCardDataInfoProcessor extends BaseAopProcessor {

    NumCenterUtils nc = new NumCenterUtils();

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        List<Map> simCardNoList = (List<Map>) msg.get("simCardNo");
        if (null == simCardNoList || simCardNoList.isEmpty()) {// 白卡不处理
            return;
        }

        String toBSS = EcAopConfigLoader.getStr("ecaop.global.param.toBSS.aop.province");
        String toCard = EcAopConfigLoader.getStr("ecaop.global.param.N63GtoCard.aop.province");// 割接后的还去BSS的省份
        if (!toCard.contains((String) msg.get("province"))) {
            msg.put("provinceList", toCard);
            msg.put("provinceToESS", toBSS);
            return;
        }
        // 成卡走号卡中心获取卡数据流程
        Map cardCenterRet = qryCardInfo(msg, simCardNoList, exchange);
        if (null == cardCenterRet || cardCenterRet.isEmpty()) {
            throw new EcAopServerBizException("9999", "号卡中心未返回卡信息");
        }
        Map uniBssBody = (Map) cardCenterRet.get("UNI_BSS_BODY");
        Map qryCardInfoRsp = (Map) uniBssBody.get("QRY_CARD_INFO_RSP");
        if (!"0000".equals(qryCardInfoRsp.get("RESP_CODE"))) {
            throw new EcAopServerBizException("9999", qryCardInfoRsp.get("RESP_DESC").toString());
        }
        List<Map> cardInfoList = (List<Map>) qryCardInfoRsp.get("INFO");
        if (null == cardInfoList || cardInfoList.isEmpty()) {
            throw new EcAopServerBizException("9999", "号卡中心卡信息未返回");
        }

        msg.put("cardInfo", cardInfoList.get(0));
        msg.put("provinceList", EcAopConfigLoader.getStr("ecaop.global.param.card.aop.province"));

    }

    private Map qryCardInfo(Map msg, List<Map> simCardNoList, Exchange exchange) throws Exception {
        Map simCard = simCardNoList.get(0);
        Map preDataMap = NumFaceHeadHelper.creatHead(exchange.getAppkey());
        Map qryCardInfoReq = new HashMap();
        String sysCode = new NumCenterUtils().changeSysCode(exchange);
        qryCardInfoReq.put("STAFF_ID", msg.get("operatorId"));
        qryCardInfoReq.put("PROVINCE_CODE", msg.get("province"));
        qryCardInfoReq.put("CITY_CODE", msg.get("city"));
        qryCardInfoReq.put("DISTRICT_CODE", msg.get("district"));
        qryCardInfoReq.put("CHANNEL_ID", msg.get("channelId"));
        qryCardInfoReq.put("CHANNEL_TYPE", msg.get("channelType"));
        qryCardInfoReq.put("SYS_CODE", sysCode);// 操作系统编码
        // qryCardInfoReq.put("CARD_STATUS", "01");//空闲 号卡中心要求不下发卡状态
        qryCardInfoReq.put("CARD_TYPE", "");
        qryCardInfoReq.put("ICCID_START", simCard.get("simId"));
        qryCardInfoReq.put("ICCID_END", simCard.get("simId"));
        preDataMap.put("UNI_BSS_BODY", MapUtils.asMap("QRY_CARD_INFO_REQ", qryCardInfoReq));
        Exchange qryExchange = ExchangeUtils.ofCopy(preDataMap, exchange);
        CallEngine.numCenterCall(qryExchange, "ecaop.comm.conf.url.numbercenter.qryCardInfo");
        Map retCardInfo = (Map) JSON.parse(qryExchange.getOut().getBody().toString());
        return retCardInfo;
    }

}
