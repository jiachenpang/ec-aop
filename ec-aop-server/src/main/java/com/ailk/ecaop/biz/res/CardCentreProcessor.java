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
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.alibaba.fastjson.JSON;

/*
 * 号卡中心成卡资源实占
 */
@EcRocTag("CardCentreProcessor")
public class CardCentreProcessor extends BaseAopProcessor {

    LanOpenApp4GDao dao = new LanOpenApp4GDao();

    @Override
    public void process(Exchange exchange) throws Exception {
        System.out.println("#######卡实战进来了吗#######");
        Map body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }

        List<Map> tradeSopInfo = dao.qryResourceInfoFromSop(msg);
        if (null == tradeSopInfo || tradeSopInfo.isEmpty()) {
            return;
        }
        String serialNumber = (String) tradeSopInfo.get(0).get("SERIAL_NUMBER");
        String isRemote = (String) tradeSopInfo.get(0).get("IS_REMOTE");// 1:成卡、2：白卡
        String remark = (String) tradeSopInfo.get(0).get("REMARK");
        if (EcAopConfigLoader.getStr("ecaop.global.param.card.aop.province").contains((String) msg.get("province"))
                && "1".equals(isRemote)) {
            System.out.println("#######卡实战进来了吗111#######");
            dealCardCentre(msg, exchange, serialNumber, remark);// 成卡资源实占
        }

    }

    private void dealCardCentre(Map msg, Exchange exchange, String serialNumber, String remark) throws Exception {
        List<Map> simCardNoInfo = (List<Map>) msg.get("simCardNo");
        if (null == simCardNoInfo || simCardNoInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "成卡未传卡信息");
        }
        String simId = (String) simCardNoInfo.get(0).get("simId");
        Map preDataMap = NumFaceHeadHelper.creatHead(exchange.getAppkey());
        Map CardInfoReq = new HashMap();
        String sysCode = new NumCenterUtils().changeSysCode(exchange);
        CardInfoReq.put("STAFF_ID", msg.get("operatorId"));
        CardInfoReq.put("PROVINCE_CODE", msg.get("province"));
        CardInfoReq.put("CITY_CODE", msg.get("city"));
        CardInfoReq.put("DISTRICT_CODE", msg.get("district"));
        CardInfoReq.put("CHANNEL_ID", msg.get("channelId"));
        CardInfoReq.put("CHANNEL_TYPE", msg.get("channelType"));
        CardInfoReq.put("SYS_CODE", sysCode);// 操作系统编码
        CardInfoReq.put("REQ_NO", msg.get("ordersId"));
        CardInfoReq.put("ICCID", simId);
        CardInfoReq.put("SERIAL_NUMBER", serialNumber);
        CardInfoReq.put("ACTIVATION_TAG", "1");// 0：占用未激活1：占用
        CardInfoReq.put("TRADE_TYPE", "01");
        if ("CardRIO".equals(remark)) {
            CardInfoReq.put("TRADE_TYPE", "21"); // 代表一号多卡esim成卡开户业务类型
        }
        preDataMap.put("UNI_BSS_BODY", MapUtils.asMap("NOTIFY_CREATE_CARD_RESULT_REQ", CardInfoReq));
        Exchange qryExchange = ExchangeUtils.ofCopy(preDataMap, exchange);
        CallEngine.numCenterCall(qryExchange, "ecaop.comm.conf.url.cardCenter.notifyCreateCardResult");
        Map retCardInfo = (Map) JSON.parse(qryExchange.getOut().getBody().toString());
        Map UNI_BSS_BODY = (Map) retCardInfo.get("UNI_BSS_BODY");
        if (null == UNI_BSS_BODY || UNI_BSS_BODY.isEmpty()) {
            throw new EcAopServerBizException("9999", "号卡中心未返回卡信息");
        }
        Map NOTIFY_CREATE_CARD_RESULT_RSP = (Map) UNI_BSS_BODY.get("NOTIFY_CREATE_CARD_RESULT_RSP");
        if (!"0000".equals(NOTIFY_CREATE_CARD_RESULT_RSP.get("RESP_CODE"))) {
            throw new EcAopServerBizException("9999", NOTIFY_CREATE_CARD_RESULT_RSP.get("RESP_DESC").toString());
        }

    }

}
