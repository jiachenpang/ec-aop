package com.ailk.ecaop.biz.res;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.NumCenterUtils;
import com.alibaba.fastjson.JSON;

/**
 * 成卡预配套包卡号配对接口
 * @author maly
 * @create 2017_02_22
 *
 */
@EcRocTag("QueryRelationInfo")
public class QueryRelationInfoProcessor extends BaseAopProcessor {

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
        //查询信息
        qryRelationInfo(exchange, msg);

    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void qryRelationInfo(Exchange exchange, Map msg) throws Exception {
        Map preDataMap = NumFaceHeadHelper.creatHead(exchange.getAppkey());
        Map qryRelationInfoReq = new HashMap();
        String sysCode = new NumCenterUtils().changeSysCode(exchange);
        qryRelationInfoReq.put("STAFF_ID", msg.get("operatorId"));
        qryRelationInfoReq.put("PROVINCE_CODE", msg.get("province"));
        qryRelationInfoReq.put("CITY_CODE", msg.get("city"));
        qryRelationInfoReq.put("DISTRICT_CODE", msg.get("district"));
        putNoEmptyValue(qryRelationInfoReq, "channelId", msg, "CHANNEL_ID");
        putNoEmptyValue(qryRelationInfoReq, "channelType", msg, "CHANNEL_TYPE");
        qryRelationInfoReq.put("OPER_TYPE", msg.get("operType"));
        qryRelationInfoReq.put("SYS_CODE", sysCode);// 操作系统编码
        putNoEmptyValue(qryRelationInfoReq, "reqNo", msg, "REQ_NO");
        qryRelationInfoReq.put("ICCID_START", msg.get("iccidStart"));
        putNoEmptyValue(qryRelationInfoReq, "serialNumberStart", msg, "SERIAL_NUMBER_START");
        putNoEmptyValue(qryRelationInfoReq, "serialNumberEnd", msg, "SERIAL_NUMBER_END");
        putNoEmptyValue(qryRelationInfoReq, "containNum", msg, "CONTAIN_NUM");
        putNoEmptyValue(qryRelationInfoReq, "notcontainNum", msg, "NOT_CONTAIN_NUM");
        qryRelationInfoReq.put("COUNT", msg.get("count"));
        qryRelationInfoReq.put("FLAG", msg.get("flag"));
        qryRelationInfoReq.put("CARD_FLAG", msg.get("cardFlag"));
        qryRelationInfoReq.put("END_TIME", msg.get("endTime"));

        preDataMap.put("UNI_BSS_BODY", MapUtils.asMap("QUERY_RELATION_INFO_REQ", qryRelationInfoReq));
        Message message = new DefaultMessage();
        message.setBody(JSON.toJSON(preDataMap));
        exchange.setIn(message);
        // exchange = ExchangeUtils.ofCopy(preDataMap, exchange);
        CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.cardCenter.queryRelationInfo");
        Map retRealtionInfo = (Map) JSON.parse(exchange.getOut().getBody().toString());

        //        CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.cardCenter.queryRelationInfo");
        //        Map retRealtionInfo = (Map) JSON.parse(exchange.getOut().getBody().toString());
        dealReturn(exchange, retRealtionInfo, msg);

    }

    /**
     * 处理返回报文
     * 
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void dealReturn(Exchange exchange, Map retRealtionInfo, Map msg) {
        if (null == retRealtionInfo || retRealtionInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "号卡中心未返回卡信息");
        }
        Map uniBssBody = (Map) retRealtionInfo.get("UNI_BSS_BODY");
        Map qryCardInfoRsp = (Map) uniBssBody.get("QUERY_RELATION_INFO_RSP");
        Map retMap = new HashMap();
        if ("0000".equals(qryCardInfoRsp.get("RESP_CODE")) && "01".equals(msg.get("cardFlag"))) {//返回成功时，需要返回号卡信息
            //封装号卡信息
            List outlist = new ArrayList<Map<String, String>>();
            List<Map> reInfo = (List<Map>) qryCardInfoRsp.get("INFO");
            if (null != reInfo && reInfo.size() > 0) {
                for (Map cardInfo : reInfo) {
                    Map info = new HashMap();
                    putNoEmptyValue(info, "iccid", cardInfo, "ICCID");
                    putNoEmptyValue(info, "imsi", cardInfo, "IMSI");
                    putNoEmptyValue(info, "ki", cardInfo, "KI");
                    putNoEmptyValue(info, "materialCode", cardInfo, "MATERIAL_CODE");
                    putNoEmptyValue(info, "cardBigType", cardInfo, "CARD_BIG_TYPE");
                    putNoEmptyValue(info, "cardType", cardInfo, "CARD_TYPE");
                    putNoEmptyValue(info, "cardName", cardInfo, "CARD_NAME");
                    info.put("serialNumber", cardInfo.get("SERIAL_NUMBER"));
                    outlist.add(info);
                }
                System.out.println("号卡信息为" + outlist);
                retMap.put("resultNum", qryCardInfoRsp.get("RESULT_NUM"));
                retMap.put("info", outlist);
                System.out.println("返回为" + retMap);
                exchange.getOut().setBody(retMap);
                System.out.println("12332" + exchange.getOut().getBody());
                Message out = new DefaultMessage();
                out.setBody(retMap);
                exchange.setOut(out);
            }
        }
        //失败时返回可配对数量
        else {
            retMap.put("resultNum", qryCardInfoRsp.get("RESULT_NUM"));
            exchange.getOut().setBody(retMap);
        }
    }

    private boolean stringIsEmpty(String str) {
        if (null == str || "".equals(str)) {
            return true;
        }
        return false;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void putNoEmptyValue(Map toMap, String toKey, Map fromMap, String fromKey) {
        String value = (String) fromMap.get(fromKey);
        if (!stringIsEmpty(value))
            toMap.put(toKey, value);
    }

}
