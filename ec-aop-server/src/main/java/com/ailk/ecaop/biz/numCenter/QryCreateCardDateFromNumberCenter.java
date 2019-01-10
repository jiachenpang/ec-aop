package com.ailk.ecaop.biz.numCenter;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.NumCenterUtils;
import com.alibaba.fastjson.JSON;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EcRocTag("qryCreateCardDateFromNumberCenter")
public class QryCreateCardDateFromNumberCenter extends BaseAopProcessor {

    @SuppressWarnings({ "rawtypes" })
    @Override
    public void process(Exchange exchange) throws Exception {
        Map inBody = exchange.getIn().getBody(Map.class);
        Map msg = inBody.get("msg") instanceof String ? JSON.parseObject((String) inBody.get("msg")) : (Map) inBody
                .get("msg");
        Map req = NumFaceHeadHelper.creatHead(exchange.getAppkey());
        preData(exchange, msg, req);
        exchange.getIn().setBody(req);
        CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.cardCenter.checkCreateCardResult");
        dealReturn(exchange, "CHECK_CREATE_CARD_RESULT_RSP", "成卡开户补换卡校验");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void preData(Exchange exchange, Map msg, Map req) {
        //必传参数校验
        String cardUseType = (String) msg.get("cardUseType");
        if (stringIsEmpty(cardUseType))
            throw new EcAopServerBizException("9999", "写卡目的cardUseType为空。");
        String iccid = (String) msg.get("iccid");
        if (stringIsEmpty(iccid))
            throw new EcAopServerBizException("9999", "大卡卡号iccid为空。");
        String numId = (String) msg.get("numId");
        if (stringIsEmpty(numId))
            throw new EcAopServerBizException("9999", "手机号码numId为空。");

        //请求参数准备
        NumCenterUtils nc = new NumCenterUtils();
        Map REQ = new HashMap();
        REQ.put("STAFF_ID", msg.get("operatorId"));
        REQ.put("PROVINCE_CODE", msg.get("province"));
        REQ.put("CITY_CODE", msg.get("city"));
        putNoEmptyValue(REQ, "DISTRICT_CODE", msg, "district");
        REQ.put("CHANNEL_ID", msg.get("channelId"));
        putNoEmptyValue(REQ, "CHANNEL_TYPE", msg, "channelType");
        REQ.put("SYS_CODE", nc.changeSysCode(exchange));
        /**
         * REQ_NO         ?   V50   调用方订单编号或流水号。
         * ICCID          1   V22   卡号
         * SERIAL_NUMBER  1   V20   号码
         * TRADE_TYPE     1   F2    业务类型 13：开户校验 08：补换卡校验
         * -----------------------
         * cardUseType    Y   F1    写卡目的：0：新开户；1：补换卡
         * activeId       N   V30   交易流水
         * iccid          Y   V22   大卡卡号
         * numId          Y   V20   手机号码
         * -----------------------
         * */
        putNoEmptyValue(REQ, "REQ_NO", msg, "activeId");// 调用方订单编号或流水号。
        REQ.put("ICCID", iccid);// 卡号
        REQ.put("SERIAL_NUMBER", numId);// 号码
        REQ.put("TRADE_TYPE", "1".equals(cardUseType) ? "08" : "13");// 业务类型 13：开户校验 08：补换卡校验
        req.put("UNI_BSS_BODY", MapUtils.asMap("CHECK_CREATE_CARD_RESULT_REQ", REQ));
    }

    /**
     * 初步处理智能卡中心返回报文
     * 
     * @param exchange
     * @param rspKey XXX_XXX_RSP
     * @param kind 接口类型（中文名）
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void dealReturn(Exchange exchange, String rspKey, String kind) {
        String rsp = exchange.getOut().getBody().toString();
        System.out.println("智能卡中心返回toString：" + rsp);
        Map out = (Map) JSON.parse(rsp);
        //判断返回报文头信息
        Map UNI_BSS_HEAD = (Map) out.get("UNI_BSS_HEAD");
        if (null != UNI_BSS_HEAD)
        {
            String code = (String) UNI_BSS_HEAD.get("RESP_CODE");
            if (!"0000".equals(code) && !"00000".equals(code))
            {
                throw new EcAopServerBizException("9999", "智能卡中心" + kind + "接口返回：" + UNI_BSS_HEAD.get("RESP_DESC"));
            }
        }
        else
        {
            throw new EcAopServerBizException("9999", "调智能卡中心" + kind + "接口返回异常!");
        }
        Map UNI_BSS_BODY = (Map) out.get("UNI_BSS_BODY");
        Map rspMap = null;

        //判断返回报文体信息
        if (null != UNI_BSS_BODY)
        {
            rspMap = (Map) UNI_BSS_BODY.get(rspKey);
            String code = (String) rspMap.get("RESP_CODE");
            if (!"0000".equals(code))
            {
                throw new EcAopServerBizException("9999", "智能卡中心" + kind + "接口返回：" + rspMap.get("RESP_DESC"));
            }
        }
        else
        {
            throw new EcAopServerBizException("9999", "调智能卡中心" + kind + "接口返回异常!");
        }

        //封装返回
        Map newOut = new HashMap();
        List<Map> CARD_DATA_INFO = (List<Map>) rspMap.get("CARD_DATA_INFO");
        if (null != CARD_DATA_INFO && CARD_DATA_INFO.size() > 0)
        {
            List cardDateInfo = new ArrayList<Map>();
            for (Map cardInfo : CARD_DATA_INFO)
            {
                /**
                 * ICCID         ?   String  V22 智能卡卡号
                 * IMSI          ?   String  V20     IMSI码
                 * KI            ?   String  V500    KI值(加密)
                 * MATERIAL_CODE ?   String  V16 物料编码
                 * CARD_BIG_TYPE ?   String  V20     卡大类
                 * CARD_TYPE     ?   String  V20     卡小类
                 * CARD_NAME     ?   String  V500    卡名称*
                 * ------------------------------
                 * iccid           cardDateInfo    N   String(22)  大卡卡号
                 * imsi            cardDateInfo    N   String(20)  IMSI号
                 * KI              cardDateInfo    N   String(500) KI值(加密)
                 * materialCode    cardDateInfo    N   String(16)  物料编码
                 * cardBigType     cardDateInfo    N   String(20)  卡大类
                 * cardType        cardDateInfo    N   String(20)  卡小类
                 * cardName        cardDateInfo    N   String(500) 卡名称
                 * */
                Map info = new HashMap();
                putNoEmptyValue(info, "iccid", cardInfo, "ICCID");
                putNoEmptyValue(info, "imsi", cardInfo, "IMSI");
                putNoEmptyValue(info, "KI", cardInfo, "KI");
                putNoEmptyValue(info, "materialCode", cardInfo, "MATERIAL_CODE");
                putNoEmptyValue(info, "cardBigType", cardInfo, "CARD_BIG_TYPE");
                putNoEmptyValue(info, "cardType", cardInfo, "CARD_TYPE");
                putNoEmptyValue(info, "cardName", cardInfo, "CARD_NAME");
                cardDateInfo.add(info);
            }
            //if (null != cardDateInfo && cardDateInfo.size() > 0)
            newOut.put("cardDateInfo", cardDateInfo);
        }
        /**
         * PACKAGE_ID  CHECK_CREATE_CARD_RESULT_RSP    ?   String  V30 套餐ID  (预配套包卡开户时返回)
         * PACKAGE_DES CHECK_CREATE_CARD_RESULT_RSP    ?   String  V400    套餐说明 (预配套包卡开户时返回)
         * PRE_MONEY   CHECK_CREATE_CARD_RESULT_RSP    ?   String  V10 预置金额（单位：分） (预配套包卡开户时返回)
         * LAST_DATE   CHECK_CREATE_CARD_RESULT_RSP    ?   String  F8  最晚启用日期 格式：YYYYMMDD(预配套包卡开户时返回)
         * ---------------------------------
         * packageId       N   String(30)  "套餐ID(预配套包卡开户时返回)"
         * packageDes      N   String(400) "套餐说明(预配套包卡开户时返回)"
         * preMoney        N   String(10)  "预置金额（单位：分）(预配套包卡开户时返回)"
         * lastDate        N   String(8)   "最晚启用日期格式：YYYYMMDD(预配套包卡开户时返回)"
         * 
         * */
        putNoEmptyValue(newOut, "packageId", rspMap, "PACKAGE_ID");
        putNoEmptyValue(newOut, "packageDes", rspMap, "PACKAGE_DES");
        putNoEmptyValue(newOut, "preMoney", rspMap, "PRE_MONEY");
        putNoEmptyValue(newOut, "lastDate", rspMap, "LAST_DATE");
        exchange.getOut().setBody(newOut);
    }

    private boolean stringIsEmpty(String str) {
        if (null == str || "".equals(str))
        {
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
