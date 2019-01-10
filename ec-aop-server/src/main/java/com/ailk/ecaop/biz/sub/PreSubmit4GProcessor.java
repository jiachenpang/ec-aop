package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.extractor.DealSysCodeExtractor;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.DealCertTypeUtils;
import com.ailk.ecaop.common.utils.DiscountSaleUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.GetThreePartInfoUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.N25ProductInfo;
import com.ailk.ecaop.common.utils.NumCenterUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.dao.essbusiness.EssBusinessDao;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;

@EcRocTag("PreSubmit4GProcessor")
@SuppressWarnings({ "unchecked", "rawtypes", "static-access" })
public class PreSubmit4GProcessor extends BaseAopProcessor implements ParamsAppliable {

    NumCenterUtils nc = new NumCenterUtils();
    private final LanUtils lan = new LanUtils();
    LanOpenApp4GDao dao = new LanOpenApp4GDao();
    private String apptx;
    EssBusinessDao essDao = new EssBusinessDao();
    private final Logger log = LoggerFactory.getLogger(PreSubmit4GProcessor.class);
    private static final String[] PARAM_ARRAY = { "ecaop.masb.chph.gifa.ParametersMapping",
            "ecaop.trades.smoca.changedcardReq.ParametersMapping", "ecaop.trades.sell.mob.jtcp.ParametersMapping",
            "ecaop.trades.sccc.cancelPre.paramtersmapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[4];
    // 31省IN_MODE_CODE赋值公用数组
    private static ArrayList<String> modecodes = new ArrayList<String>(Arrays.asList("nmpr", "bjsb", "tjpr", "sdpr",
            "hpsb", "sxpr", "ahpre", "shpre", "jspr", "zjpre", "fjpre", "hipre", "gdps", "ussb", "qhpr", "hupr",
            "hnpr", "jxpre", "hapr", "xzpre", "scpr", "cqps", "snpre", "gzpre", "ynpre", "gspr", "nxpr", "xjpr",
            "jlpr", "mppln", "hlpr", "saip", "cpsb"));

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        apptx = (String) body.get("apptx");
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        // 20180615针对新零售修改inModeCode和E_IN_MODE为N-------------------start-------------------------
        List<Map> paraList = new ArrayList<Map>();
        paraList = (List<Map>) msg.get("para");
        boolean newRetailFlag = false;
        if (!IsEmptyUtils.isEmpty(paraList)) {
            for (Map paraMap : paraList) {
                if ("NEWRETAIL".equals(paraMap.get("paraId")) && "N".equals(paraMap.get("paraValue"))) {
                    newRetailFlag = true;
                }
            }
        }
        msg.put("newRetailFlag", newRetailFlag);
        // 20180615针对新零售修改inModeCode和E_IN_MODE为N--------------------end-------------------------
        // esim成卡需求有特定的bizkey,对bizkey进行判断 by wangmc 20171206
        msg.put("esimBizkey", exchange.getBizkey());
        // 调号卡中心号码预占接口
        String newApptx = (String) body.get("apptx");
        if (!"0".equals(msg.get("numPort"))) {// 20180932-CBSS提供携号转网需求(因为转网为异网号码，所以号卡中心没有这个号码)
            Exchange exchangeNum = ExchangeUtils.ofCopy(exchange, msg);
            callNumCenterPreemtedNum(exchangeNum, newApptx);
        }
        msg.put("eparchyCode", new ChangeCodeUtils().changeEparchyUnStatic(msg));// 转换成cb地市
        msg.remove("areaCode");
        msg.put("baseRemark", msg.get("remark"));
        String appCode = exchange.getAppCode();
        String serialNumber = "";
        Map niceInfo = new HashMap();// 靓号信息
        List<Map> numId = (List<Map>) msg.get("numId");
        if (null != numId && !numId.isEmpty()) {
            serialNumber = (String) numId.get(0).get("serialNumber");
            niceInfo = (Map) numId.get(0).get("niceInfo");
        }

        // 限制只有商城可销售靓号
        // if (null != niceInfo && !niceInfo.isEmpty()) {
        // String classId = (String) niceInfo.get("classId");
        // if (!"9".equals(classId) && !"masb".equals(appCode))
        // {
        // throw new EcAopServerBizException("9999", "号码"
        // + serialNumber + "为靓号，只允许商城销售！");
        // }
        // }
        // 获取客户信息
        List<Map> newCustInfoList = new ArrayList<Map>();
        List<Map> customerInfo = (List<Map>) msg.get("customerInfo");
        List<Map> userInfoList = (List<Map>) msg.get("userInfo");
        String custType = (String) customerInfo.get(0).get("custType");// 0 新客户 1老客户
        if ("1".equals(custType) && IsEmptyUtils.isEmpty(customerInfo.get(0).get("custId"))) {
            throw new EcAopServerBizException("9999", "未下发老客户标识!");
        }
        if (null != customerInfo && !customerInfo.isEmpty()) {
            if (null == customerInfo.get(0).get("newCustomerInfo")) {
                throw new EcAopServerBizException("9999", "请传入客户详细资料");// 优化：新老用户都需要传入newCustomerInfo节点。cuij
            }
            newCustInfoList = (List<Map>) customerInfo.get(0).get("newCustomerInfo");
        }
        // RTJ2018103100005-AOP接口支持携号转网业务
        if (!IsEmptyUtils.isEmpty((List<Map>) msg.get("numPortInfo"))) {
            Map numPortInfo = ((List<Map>) msg.get("numPortInfo")).get(0);
            numPortInfo.put("certType", newCustInfoList.get(0).get("certType"));
            numPortInfo.put("certNum", newCustInfoList.get(0).get("certNum"));
            msg.put("numInfoMap", numPortInfo);
        }
        // 新建的客户信息塞入msg
        msg.putAll(newCustInfoList.get(0));
        msg.put("custId", customerInfo.get(0).get("custId"));

        // 获取客户类型，若没传客户类型，默认为01个人客户。
        String customerType = (String) newCustInfoList.get(0).get("custType");
        if ("".equals(customerType) || null == customerType) {
            msg.put("customerType", "01");
        }
        else {
            msg.put("customerType", customerType);
        }
        Map custInfo4CB = new HashMap();
        // 当为集团客户时且为老客户时，走原流程
        if ("02".equals(customerType) && "1".equals(custType)) {
            custInfo4CB = getCustInfo4CB(newCustInfoList, exchange, msg, custType);
        }
        else {
            custInfo4CB = dealCustInfo4CB(newCustInfoList, msg, custType);
        }

        custType = custInfo4CB.get("custType").toString();
        Map tradeMap = new HashMap();
        MapUtils.arrayPut(tradeMap, msg, MagicNumber.COPYARRAY);
        // 处理账户信息,并将账户信息塞入msg
        Map acctInfoMap = getAcctInfo(msg, exchange, tradeMap);
        msg.putAll(acctInfoMap);
        // 从号卡中心获取成卡信息
        List<Map> simCardNoList = (List<Map>) msg.get("simCardNo");
        msg.put("IS_REMOTE", "2");// 白卡
        String simId = "";
        if (null != simCardNoList && !simCardNoList.isEmpty()) {
            simId = (String) simCardNoList.get(0).get("simId");
        }
        if (StringUtils.isNotEmpty(simId)) {

            if (EcAopConfigLoader.getStr("ecaop.global.param.card.aop.province").contains((String) msg.get("province"))) {// 号卡中心
                Map cardCenterRet = qryCardInfo(msg, simId, exchange);
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

            }
            else {// 调省份走原流程
                Map cardInfoRet = qryProvinceCardInfo(msg, simId, exchange, serialNumber);
                msg.put("cardInfo", cardInfoRet);
            }
            msg.put("IS_REMOTE", "1");// 成卡

        }
        String tradeId = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, tradeMap),
                "seq_trade_id", 1).get(0);
        String payRelId = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, tradeMap),
                "SEQ_PAYRELA_ID", 1).get(0);
        String custId = "1".equals(custType) ? (String) custInfo4CB.get("custId") : (String) GetSeqUtil.getSeqFromCb(
                pmp[0], ExchangeUtils.ofCopy(exchange, tradeMap), "seq_cust_id", 1).get(0);
        String custName = "1".equals(custType) ? (String) custInfo4CB.get("custName") : (String) newCustInfoList.get(0)
                .get("customerName");
        String userId = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, tradeMap),
                "seq_user_id", 1).get(0);
        String purchaseItemId = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, tradeMap),
                "SEQ_ITEM_ID", 1).get(0);
        // 直接截取当前时间的前六位
        // Map inMap = new HashMap();
        // inMap.put("date", GetDateUtils.getDate());
        // List<Map> cycIdresult = dao.querycycId(inMap);
        // String cycId = (String) cycIdresult.get(0).get("CYCLE_ID");
        String cycId = GetDateUtils.getDate().substring(0, 6);
        // 整合各种id和参数
        Map idMap = MapUtils.asMap("tradeId", tradeId, "payRelId", payRelId, "custId", custId, "custName", custName,
                "userId", userId, "cycId", cycId, "serialNumber", serialNumber, "purchaseItemId", purchaseItemId,
                "certType", custInfo4CB.get("certTypeCode"), "certCode", custInfo4CB.get("certCode"), "niceInfo",
                niceInfo, "appCode", appCode, "custType", custType, "simId", simId);
        msg.putAll(idMap);
        // 集团用户校验
        if ("1".equals(userInfoList.get(0).get("groupFlag"))) {
            Map jtcpMap = new HashMap();
            jtcpMap.put("province", "00" + msg.get("province"));
            jtcpMap.put("groupId", userInfoList.get(0).get("groupId"));
            jtcpMap.put("operatorId", msg.get("operatorId"));
            jtcpMap.put("city", msg.get("eparchyCode"));
            jtcpMap.put("district", msg.get("district"));
            jtcpMap.put("channelId", msg.get("channelId"));
            jtcpMap.put("channelType", msg.get("channelType"));
            dealJtcp(exchange, jtcpMap, msg);

        }
        msg.put("apptx", body.get("apptx"));
        // RHQ2018081400032-社会电商实名新流程改造
        List<Map> para2CB = new ArrayList<Map>();
        Map<String, Object> para = new HashMap<String, Object>();
        if (!IsEmptyUtils.isEmpty(paraList)) {
            for (Map paraMap : paraList) {
                if ("DevAgencyAcct".equals(paraMap.get("paraId"))) {
                    para.put("paraId", "DevAgencyAcct");// 标记是否为社会电商渠道
                    para.put("paraValue", paraMap.get("paraValue"));
                    para2CB.add(para);
                }
            }
        }
        Map ext = preData4Ext(msg);
        // RHQ2018082800066-增加“港澳台居民居住证”证件类型的需求 by wangmc 20180914
        msg.put("apptx", body.get("appxt"));
        DealCertTypeUtils.dealCertType(msg, newCustInfoList.get(0), ext);
        Map base = preData4Base(msg, appCode);
        Map preDataMap = preData4PreSub(ext, base, msg);
        preDataMap.put("para", para2CB);
        MapUtils.arrayPut(preDataMap, msg, MagicNumber.COPYARRAY);
        preDataMap.put("city", msg.get("eparchyCode"));
        Exchange preSubExchange = ExchangeUtils.ofCopy(exchange, preDataMap);
        Map retMap = Maps.newHashMap();
        Map preSubRetMap = new HashMap();
        try {
            new LanUtils().preData(pmp[3], preSubExchange);
            CallEngine.wsCall(preSubExchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
            new LanUtils().xml2Json("ecaop.trades.sccc.cancelPre.template", preSubExchange);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用预提交失败！" + e.getMessage());
        }
        preSubRetMap = preSubExchange.getOut().getBody(Map.class);
        retMap = dealRetInfo(preSubRetMap, msg);
        exchange.getOut().setBody(retMap);
    }

    private Map dealCustInfo4CB(List<Map> newCustInfoList, Map msg, String custType) {
        Map custInfo = new HashMap();
        Map custInfoMap = new HashMap();
        String certType = (String) newCustInfoList.get(0).get("certType");
        // 当证件类型为集团时,operateType传13,与客户资料校验保持一致
        if ("07|13|14|15|17|18|21|33".contains(certType)) {
            custInfo.put("operateType", "13");
        }
        else {
            custInfo.put("operateType", "1");
        }
        certType = CertTypeChangeUtils.certTypeMall2Cbss(certType);
        custInfo.put("certCode", newCustInfoList.get(0).get("certNum"));
        custInfo.put("certTypeCode", certType);
        custInfo.put("custId", msg.get("custId"));
        custInfo.put("custName", newCustInfoList.get(0).get("customerName"));
        if ("0".equals(custType)) {// 测试环境cb返回报文头有问题（报文头返回编码非0000和9999），生产看情况上
            custInfoMap.put("custType", custType);
            custInfoMap.putAll(custInfo);
            return custInfoMap;

        }
        if ((null == msg.get("custId") || "".equals(msg.get("custId")))) {
            custType = "0";
        }
        custInfoMap.putAll(custInfo);
        custInfoMap.put("custType", custType);
        return custInfoMap;
    }

    private void dealJtcp(Exchange exchange, Map jtcpMap, Map msg) {
        List<Map> userInfoList = (List<Map>) msg.get("userInfo");
        String cBSSGroupId = (String) userInfoList.get(0).get("cBSSGroupId");
        Exchange jtcpExchange = ExchangeUtils.ofCopy(exchange, jtcpMap);
        try {

            lan.preData(pmp[2], jtcpExchange);
            CallEngine.wsCall(jtcpExchange, "ecaop.comm.conf.url.cbss.services.OrdForAopthSer");// 地址
            lan.xml2Json("ecaop.trades.sell.mob.jtcp.template", jtcpExchange);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "集团信息查询失败" + e.getMessage());
        }
        Map retMap = jtcpExchange.getOut().getBody(Map.class);
        List<Map> userList = (List<Map>) retMap.get("useList");
        if (null == userList || userList.isEmpty()) {
            throw new EcAopServerBizException("9999", "未获取到CBSS集团客户下的用户信息");
        }
        String serialNumberA = (String) userList.get(0).get("serialNumber");
        String userIdA = (String) userList.get(0).get("userId");
        // 20170509 若外围传了cBSSGroupId 则取用groupId查询出来的结果中与cBSSGroupId一致的信息
        // 否则默认取第一条
        if (null != cBSSGroupId && !"".equals(cBSSGroupId)) {
            for (Map item : userList) {
                if (cBSSGroupId.equals(item.get("userId"))) {
                    userIdA = cBSSGroupId;
                    serialNumberA = (String) item.get("serialNumber");
                }
            }
        }
        if (StringUtils.isEmpty(serialNumberA) || StringUtils.isEmpty(userIdA)) {
            throw new EcAopServerBizException("9999", "未获取到CBSS集团客户下的用户信息");
        }
        Map relaItem = new HashMap();
        List<Map> tradeRelation = new ArrayList<Map>();
        relaItem.put("xDatatype", "NULL");
        relaItem.put("relationAttr", "");
        relaItem.put("relationTypeCode", "2222");
        relaItem.put("idA", userIdA);
        relaItem.put("idB", msg.get("userId"));
        relaItem.put("roleCodeA", "0");
        relaItem.put("roleCodeB", "0");
        relaItem.put("orderno", "");
        relaItem.put("shortCode", "");
        relaItem.put("startDate", GetDateUtils.getDate());
        relaItem.put("endDate", "2050-12-31 23:59:59");
        relaItem.put("modifyTag", "0");
        relaItem.put("remark", "");
        relaItem.put("serialNumberA", serialNumberA);
        relaItem.put("serialNumberB", msg.get("serialNumber"));
        relaItem.put("itemId", TradeManagerUtils.getSequence((String) msg.get("eparchyCode"), "SEQ_ITEM_ID"));
        tradeRelation.add(relaItem);
        msg.put("tradeRelation", tradeRelation);
    }

    /**
     * 成卡卡数据查询
     * @param msg
     * @param simCardNoList
     * @param exchange
     * @param serialNumber
     * @return
     */
    private Map qryProvinceCardInfo(Map msg, String simId, Exchange exchange, String serialNumber) {
        LanUtils lan = new LanUtils();
        Map preDataMap = new HashMap();
        List<Map> cardNumberInfo = new ArrayList<Map>();
        Map cardNumber = new HashMap();
        cardNumber.put("simCardNo", simId);
        cardNumber.put("serialNumber", serialNumber);
        cardNumberInfo.add(cardNumber);
        preDataMap.put("cardNumberInfo", cardNumberInfo);
        preDataMap.put("province", msg.get("province"));
        preDataMap.put("city", msg.get("city"));
        preDataMap.put("channelType", msg.get("channelType"));
        preDataMap.put("channelId", msg.get("channelId"));
        preDataMap.put("operatorId", msg.get("operatorId"));
        preDataMap.put("tradeType", "1");
        Exchange qryCardExchange = ExchangeUtils.ofCopy(exchange, preDataMap);
        try {

            lan.preData(pmp[1], qryCardExchange);
            CallEngine.wsCall(qryCardExchange, "ecaop.comm.conf.url.osn.services.changedCardRequest");
            lan.xml2Json("ecaop.trades.smoca.changedcardReq.Template", qryCardExchange);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "成卡卡数据查询失败" + e.getMessage());
        }
        Map retMap = qryCardExchange.getOut().getBody(Map.class);
        List<Map> cardNumberInfoList = (List<Map>) retMap.get("cardNumberInfo");
        if (null == cardNumberInfoList || cardNumberInfoList.isEmpty()) {
            throw new EcAopServerBizException("9999", "省份未返回卡信息");
        }
        Map cardInfo = (Map) cardNumberInfoList.get(0).get("cardInfo");
        if (null == cardInfo || cardInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "省份未返回卡信息");
        }

        return cardInfo;
    }

    /**
     * 从号卡中心获取成卡信息
     * @param msg
     * @param simCardNoList
     * @param exchange
     * @return
     * @throws Exception
     */
    private Map qryCardInfo(Map msg, String simId, Exchange exchange) throws Exception {
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
        // qryCardInfoReq.put("CARD_STATUS", "01");// 空闲 号卡中心要求不下发卡状态
        qryCardInfoReq.put("CARD_TYPE", "");
        qryCardInfoReq.put("ICCID_START", simId);
        qryCardInfoReq.put("ICCID_END", simId);
        preDataMap.put("UNI_BSS_BODY", MapUtils.asMap("QRY_CARD_INFO_REQ", qryCardInfoReq));
        Exchange qryExchange = ExchangeUtils.ofCopy(preDataMap, exchange);
        CallEngine.numCenterCall(qryExchange, "ecaop.comm.conf.url.numbercenter.qryCardInfo");
        Map retCardInfo = (Map) JSON.parse(qryExchange.getOut().getBody().toString());
        return retCardInfo;
    }

    private Map dealRetInfo(Map preSubRetMap, Map msg) {
        Map retMap = new HashMap();
        List<Map> rspInfo = (List<Map>) preSubRetMap.get("rspInfo");
        if (null == rspInfo || rspInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "cBSS统一预提交未返回应答信息");
        }
        // 记录trade表
        msg.put("tradeId", rspInfo.get(0).get("provOrderId"));
        new TradeManagerUtils().insert2TradeSop(msg);
        retMap.put("provOrderId", rspInfo.get(0).get("provOrderId"));
        retMap.put("bssOrderId", rspInfo.get(0).get("bssOrderId"));
        Integer totalFee = 0;
        for (Map rspMap : rspInfo) {
            List<Map> provinceOrderInfo = (List<Map>) rspMap.get("provinceOrderInfo");
            if (null == provinceOrderInfo || provinceOrderInfo.isEmpty()) {
                continue;
            }
            // 费用计算
            for (Map provinceOrder : provinceOrderInfo) {
                totalFee = totalFee + Integer.valueOf(provinceOrder.get("totalFee").toString()) * 10;
                List<Map> preFeeInfoRsp = (List<Map>) provinceOrder.get("preFeeInfoRsp");
                if (null == preFeeInfoRsp || preFeeInfoRsp.isEmpty()) {
                    continue;
                }
                List<Map> feeList = dealFee(preFeeInfoRsp);
                retMap.put("feeInfo", feeList);
            }
            retMap.put("totalFee", totalFee + "");
        }
        return retMap;
    }

    private List<Map> dealFee(List<Map> feeList) {
        if (null == feeList || 0 == feeList.size()) {
            return new ArrayList<Map>();
        }
        List<Map> retFeeList = new ArrayList<Map>();
        for (Map fee : feeList) {
            Map retFee = new HashMap();
            retFee.put("feeId", fee.get("feeTypeCode"));
            retFee.put("feeCategory", fee.get("feeMode"));
            retFee.put("feeDes", fee.get("feeTypeName"));
            retFee.put("maxRelief", Integer.valueOf((String) fee.get("maxDerateFee")) * 10 + "");
            retFee.put("origFee", Integer.valueOf((String) fee.get("fee")) * 10 + "");
            retFeeList.add(retFee);
        }
        return retFeeList;

    }

    private Map preData4PreSub(Map ext, Map base, Map msg) {
        Map preDataMap = new HashMap();
        preDataMap.put("base", base);
        preDataMap.put("ext", ext);
        preDataMap.put("ordersId", msg.get("tradeId"));
        preDataMap.put("serinalNamber", msg.get("serinalNamber"));
        preDataMap.put("operTypeCode", "0");
        return preDataMap;
    }

    private Map preData4Ext(Map msg) throws Exception {
        List<Map> userInfo = (List<Map>) msg.get("userInfo");
        List<Map> activityInfo = new ArrayList<Map>();
        if (null != userInfo && !userInfo.isEmpty()) {
            activityInfo = (List<Map>) userInfo.get(0).get("activityInfo");
        }

        new N25ProductInfo().getProductInfo(msg);
        // 担保人节点
        preGuarator(msg, userInfo);

        // 账户节点
        preAcct(msg);

        // 处理靓号、终端费用以及相关属性
        dealResourceAndNumFee(msg);

        Map ext = preData4Item(msg);
        // esim增加开关配置 0-关闭,1-打开
        String esimSwitch = EcAopConfigLoader.getStr("ecaop.global.param.esim.switch");
        if ("1".equals(esimSwitch)) {
            preEsimCardInfo(msg, ext);
        }
        return ext;
    }

    private Map preData4Item(Map msg) throws Exception {
        Map ext = new HashMap();
        ext.put("tradeAcct", preDataUtil(msg, "tradeAcctList"));
        ext.put("tradeAssure", preDataUtil(msg, "tradeAssure"));
        // 回归到老流程，新客户下发tradecustomer和tradecustperson by:cuij 20170327. 覆盖ZZC钉钉
        if ("0".equals(msg.get("custType"))) {
            ext.put("tradeCustomer", preCustomerData(msg));
            ext.put("tradeCustPerson", preCustPerData(msg));
        }
        if ("1".equals(getPayModeCode(msg)) || "3".equals(getPayModeCode(msg))) {
            ext.put("tradeAcctConsign", preTradeAcctConsign(msg));
        }
        ext.put("tradeRelation", preDataUtil(msg, "tradeRelation"));
        ext.put("tradeUser", preUserData(msg));
        ext.put("tradeProductType", preProductTpyeListData(msg));
        ext.put("tradeProduct", preProductData(msg));
        ext.put("tradeDiscnt", preDiscntData(msg));
        ext.put("tradeSvc", preTradeSvcData(msg));
        ext.put("tradeSp", preTradeSpData(msg));
        ext.put("tradeRes", preTradeResData(msg));
        ext.put("tradePayrelation", preTradePayRelData(msg));
        ext.put("tradeElement", preDataUtil(msg, "elementList"));
        ext.put("tradePurchase", preDataUtil(msg, "tradePurchaseList"));
        ext.put("tradefeeSub", preDataUtil(msg, "tradeFeeItemList"));
        ext.put("tradeItem", preDataUtil(msg, "itemList"));
        ext.put("tradeSubItem", preDataUtil(msg, "subItemList"));
        ext.put("tradeOther", preTradeOtherItem(msg));// 增加入参新增产品企业编码以及产品别名
        ext.put("tradePost", preTradePostItem(msg));// 增加邮寄节点 by wangmc 2016-10-25
        return ext;
    }

    private List<Map> preTradeOtherItem(Map msg) {
        List<Map> userInfo = (List<Map>) msg.get("userInfo");
        List<Map> productInfo = (List<Map>) userInfo.get(0).get("product");
        List<Map> tradeOthers = new ArrayList<Map>();
        if (productInfo != null && productInfo.size() > 0) {
            for (int i = 0; i < productInfo.size(); i++) {
                String companyId = (String) productInfo.get(i).get("companyId");
                String productNameX = (String) productInfo.get(i).get("productNameX");
                String productId = (String) productInfo.get(i).get("productId");
                if (StringUtils.isNotEmpty(companyId) && StringUtils.isNotEmpty(productNameX)) {
                    String startDate = GetDateUtils.getDate();
                    String endDate = "20501231235959";
                    Map tradeOther = new HashMap();
                    tradeOther.put("rsrvValueCode", "TIBM");
                    tradeOther.put("rsrvValue", companyId);
                    tradeOther.put("rsrvStr1", productNameX);
                    tradeOther.put("rsrvStr2", productId);
                    tradeOther.put("startDate", startDate);
                    tradeOther.put("endDate", endDate);
                    tradeOther.put("modifyTag", "0");
                    tradeOthers.add(MapUtils.asMap("item", tradeOther));
                }
            }
        }
        return tradeOthers;
    }

    /**
     * 准备邮寄信息节点节点 TRADE_POST
     * @return
     */
    private List<Map> preTradePostItem(Map msg) {
        List<Map> tradePost = new ArrayList<Map>();
        List<Map> postInfo = (List<Map>) msg.get("postInfo");
        if (IsEmptyUtils.isEmpty(postInfo)) {
            return tradePost;
        }
        for (Map post : postInfo) {
            Object postIdType = post.get("postIdType");
            Object postId = "0".equals(postIdType) ? msg.get("custId") : "1".equals(postIdType) ? msg.get("userId")
                    : msg.get("acctId");
            post.put("xDatatype", "NULL");
            post.put("id", postId);
            post.put("idType", postIdType);
            if (IsEmptyUtils.isEmpty(post.get("startDate"))) {
                post.put("startDate", GetDateUtils.getDate());
            }
            if (IsEmptyUtils.isEmpty(post.get("endDate"))) {
                post.put("endDate", "20501231235959");
            }
            tradePost.add(MapUtils.asMap("item", post));
        }
        return tradePost;
    }

    private Map preTradeAcctConsign(Map msg) {
        Map tradeAcctCon = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("acctId", msg.get("acctId"));
        item.put("payModeCode", getPayModeCode(msg));
        item.put("consignMode", "1");
        item.put("assistantTag", "0");
        item.put("bankCode", "");
        item.put("bankAcctNo", "");
        item.put("bankAcctName", "");
        item.put("startCycleId", msg.get("cycId"));
        item.put("endCycleId", "203712");
        itemList.add(item);
        tradeAcctCon.put("item", itemList);
        return tradeAcctCon;

    }

    public Map preTradeSpData(Map msg) {
        Map tardeSp = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> sp = (List<Map>) msg.get("spList");
        for (int i = 0; i < sp.size(); i++) {
            Map item = new HashMap();
            item.put("userId", msg.get("userId"));
            item.put("serialNumber", msg.get("serialNumber"));
            item.put("productId", sp.get(i).get("productId"));
            item.put("packageId", sp.get(i).get("packageId"));
            item.put("partyId", sp.get(i).get("partyId"));
            item.put("spId", sp.get(i).get("spId"));
            item.put("spProductId", sp.get(i).get("spProductId"));
            item.put("firstBuyTime", GetDateUtils.getDate());
            item.put("paySerialNumber", msg.get("serialNumber"));
            item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);

            if (null != sp.get(i).get("activityStarTime")) {
                item.put("startDate", sp.get(i).get("activityStarTime"));
            }
            else {
                item.put("startDate", GetDateUtils.getDate());
            }
            if (null != sp.get(i).get("activityTime")) {
                item.put("endDate", sp.get(i).get("activityTime"));
            }
            else {
                item.put("endDate", "20501231235959");
            }
            item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));

            item.put("updateTime", GetDateUtils.getDate());
            item.put("remark", "");
            item.put("modifyTag", "0");
            item.put("payUserId", msg.get("userId"));
            item.put("spServiceId", sp.get(i).get("spServiceId"));
            item.put("userIdA", msg.get("userId"));
            item.put("xDatatype", "NULL");
            itemList.add(item);
        }
        tardeSp.put("item", itemList);
        return tardeSp;
    }

    public Map preTradeSvcData(Map msg) {
        Map svcList = new HashMap();
        List<Map> svc = new ArrayList<Map>();
        svc = (List<Map>) msg.get("svcList");
        List<Map> svList = new ArrayList();
        log.info("test001=" + svc);
        if (!IsEmptyUtils.isEmpty(svc)) {
            for (int i = 0; i < svc.size(); i++) {
                Map item = new HashMap();
                // if (!"50015|50011".contains(svc.get(i).get("serviceId") + "")) {//
                // RSH2017042800038-关于申请AOP侧开放CBSS用户开户支撑国际业务开通接口能力需求
                item.put("xDatatype", "NULL");
                item.put("userId", msg.get("userId"));
                item.put("serviceId", svc.get(i).get("serviceId"));
                item.put("modifyTag", "0");

                if (null != svc.get(i).get("activityStarTime")) {
                    item.put("startDate", svc.get(i).get("activityStarTime"));
                }
                else {
                    item.put("startDate", GetDateUtils.getDate());
                }
                if (null != svc.get(i).get("activityTime")) {
                    item.put("endDate", svc.get(i).get("activityTime"));
                }
                else {
                    item.put("endDate", "20501231235959");
                }
                item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));

                item.put("productId", svc.get(i).get("productId"));
                item.put("packageId", svc.get(i).get("packageId"));
                item.put("userIdA", "-1");
                svList.add(item);
                // }
            }
            svcList.put("item", svList);
        }
        return svcList;
    }

    public Map preDiscntData(Map msg) {
        Map tradeDis = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> discnt = (List<Map>) msg.get("discntList");
        String sdcellcountId = "";
        List<Map> cellCountList = new ArrayList<Map>();
        Map<String, Object> cellCount = new HashMap<String, Object>();
        for (int i = 0; i < discnt.size(); i++) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("id", msg.get("userId"));
            item.put("idType", "1");
            item.put("userIdA", "-1");
            item.put("productId", discnt.get(i).get("productId"));
            item.put("packageId", discnt.get(i).get("packageId"));
            item.put("discntCode", discnt.get(i).get("discntCode"));
            item.put("specTag", "0");
            item.put("relationTypeCode", "");
            if (null != discnt.get(i).get("activityStarTime")) {
                item.put("startDate", discnt.get(i).get("activityStarTime"));
            }
            else {
                item.put("startDate", GetDateUtils.getDate());
            }
            if (null != discnt.get(i).get("activityTime")) {
                item.put("endDate", discnt.get(i).get("activityTime"));
            }
            else {
                item.put("endDate", "20501231235959");
            }
            item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
            List<Map> userInfoList = (List<Map>) msg.get("userInfo");// RSD2018061400051-山东关于CBSS产品开户支撑优惠校园区域需求
            if (!IsEmptyUtils.isEmpty(userInfoList)) {
                Map userInfo = userInfoList.get(0);
                List<Map> productList = (List<Map>) userInfo.get("product");// 必传
                for (Map productMap : productList) {
                    List<Map> attrInfoList = (List<Map>) productMap.get("attrInfo");
                    if (!IsEmptyUtils.isEmpty(attrInfoList)) {
                        for (Map attr : attrInfoList) {
                            if ("2".equals(attr.get("attrType"))) {
                                if ((attr.get("elementId") + "").equals(discnt.get(i).get("discntCode") + "")) {
                                    cellCount.put("itemId", item.get("itemId"));// RSD201808200001-AOP移网开户处理申请接口支持CBSS送资费属性的需求
                                    cellCount.put("attrCode", attr.get("attrCode"));
                                    cellCount.put("attrValue", attr.get("attrValue"));
                                    cellCountList.add(cellCount);
                                }
                            }
                        }
                    }

                    List<Map> packageElement = (List<Map>) productMap.get("packageElement");
                    if (!IsEmptyUtils.isEmpty(packageElement)) {
                        for (Map packageElementMap : packageElement) {
                            if ((packageElementMap.get("packageId") + "").equals(discnt.get(i).get("packageId") + "")) {
                                sdcellcountId = (String) item.get("itemId");
                            }
                        }
                    }
                }
            }
            msg.put("sdcellcountId", sdcellcountId);// 因为要求这个属性的itemId要和资费的itemId一样，所以要过来赋值一下
            msg.put("cellCountList", cellCountList);
            // item.put("itemId", msg.get("subscribeId"));
            item.put("modifyTag", "0");
            itemList.add(item);
        }
        // 靓号资费
        Map numberDiscnt = (Map) msg.get("numberDiscnt");
        if (null != numberDiscnt && !numberDiscnt.isEmpty()) {
            Map item1 = new HashMap();
            item1.putAll(numberDiscnt);
            itemList.add(item1);
        }
        tradeDis.put("item", itemList);
        return tradeDis;
    }

    public Map preProductData(Map msg) throws Exception {
        Map tradeProduct = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> product = (List<Map>) msg.get("productList");
        for (int i = 0; i < product.size(); i++) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", msg.get("userId"));
            item.put("productMode", product.get(i).get("productMode"));
            item.put("productId", product.get(i).get("productId"));
            item.put("brandCode", product.get(i).get("brandCode"));
            item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
            // item.put("itemId", "");
            item.put("modifyTag", "0");
            if ("50".equals(product.get(i).get("productMode"))) {
                if (null != product.get(i).get("activityStarTime")) {
                    item.put("startDate", GetDateUtils.transDate((String) product.get(i).get("activityStarTime"), 14));
                }
                else {
                    item.put("startDate", GetDateUtils.getDate());
                }
                if (null != product.get(i).get("activityTime")) {
                    item.put("endDate", GetDateUtils.transDate((String) product.get(i).get("activityTime"), 14));
                }
                else {
                    item.put("endDate", "20501231235959");
                }
            }
            else if ("01".equals(product.get(i).get("productMode"))) {
                if (null != product.get(i).get("activityStarTime")) {
                    item.put("startDate", GetDateUtils.transDate((String) product.get(i).get("activityStarTime"), 14));
                }
                else {
                    item.put("startDate", GetDateUtils.getDate());
                }
                if (null != product.get(i).get("activityTime")) {
                    item.put("endDate", GetDateUtils.transDate((String) product.get(i).get("activityTime"), 14));
                }
                else {
                    item.put("endDate", "20501231235959");
                }
            }
            else {
                item.put("startDate", GetDateUtils.getDate());
                item.put("endDate", "20501231235959");
            }
            item.put("userIdA", MagicNumber.DEFAULT_NO_VALUE);
            itemList.add(item);
        }

        tradeProduct.put("item", itemList);
        return tradeProduct;
    }

    public Map preProductTpyeListData(Map msg) throws Exception {
        Map tradeProductType = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> productTpye = (List<Map>) msg.get("productTypeList");
        for (int i = 0; i < productTpye.size(); i++) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", msg.get("userId"));
            item.put("productMode", productTpye.get(i).get("productMode"));
            item.put("productId", productTpye.get(i).get("productId"));
            item.put("productTypeCode", productTpye.get(i).get("productTypeCode"));
            item.put("modifyTag", "0");
            if ("50".equals(productTpye.get(i).get("productMode"))) {
                if (null != productTpye.get(i).get("activityStarTime")) {
                    item.put("startDate",
                            GetDateUtils.transDate((String) productTpye.get(i).get("activityStarTime"), 14));
                }
                else {
                    item.put("startDate", GetDateUtils.getDate());
                }
                if (null != productTpye.get(i).get("activityTime")) {
                    item.put("endDate", GetDateUtils.transDate((String) productTpye.get(i).get("activityTime"), 14));
                }
                else {
                    item.put("endDate", "20501231235959");
                }
            }
            else if ("01".equals(productTpye.get(i).get("productMode"))) {
                if (null != productTpye.get(i).get("activityStarTime")) {
                    item.put("startDate",
                            GetDateUtils.transDate((String) productTpye.get(i).get("activityStarTime"), 14));
                }
                else {
                    item.put("startDate", GetDateUtils.getDate());
                }
                if (null != productTpye.get(i).get("activityTime")) {
                    item.put("endDate", GetDateUtils.transDate((String) productTpye.get(i).get("activityTime"), 14));
                }
                else {
                    item.put("endDate", "20501231235959");
                }
            }
            else {
                item.put("startDate", GetDateUtils.getDate());
                item.put("endDate", "20501231235959");
            }
            itemList.add(item);
        }
        tradeProductType.put("item", itemList);
        return tradeProductType;

    }

    private Map preUserData(Map msg) {
        String cityCode = (String) msg.get("district");
        if (null != cityCode && cityCode.length() > 6) {
            cityCode = cityCode.substring(0, 6);
        }
        List<Map> userInfo = (List<Map>) msg.get("userInfo");
        String serType = "";
        if (null != userInfo && !userInfo.isEmpty()) {
            serType = (String) userInfo.get(0).get("serType");
        }
        // 发展人地市
        String recomCity = (String) msg.get("recomCity");
        String recomPersonId = (String) msg.get("recomPersonId");
        Map tradeUser = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("userId", msg.get("userId"));
        item.put("custId", msg.get("custId"));
        item.put("usecustId", msg.get("custId"));
        item.put("brandCode", msg.get("mBrandCode"));
        item.put("productId", msg.get("mProductId"));
        item.put("eparchyCode", msg.get("eparchyCode"));
        item.put("cityCode", cityCode);
        item.put("userPasswd",
                msg.get("serialNumber").toString().substring(msg.get("serialNumber").toString().length() - 6));
        item.put("userTypeCode", "0");
        item.put("serialNumber", msg.get("serialNumber"));
        item.put("netTypeCode", "50");
        item.put("scoreValue", "0");
        item.put("creditClass", "0");
        item.put("basicCreditValue", "0");
        item.put("creditValue", "0");
        item.put("acctTag", "0");
        // 预付费标志
        item.put("prepayTag", ChangeCodeUtils.changeSerType(serType));
        item.put("inDate", GetDateUtils.getDate());
        item.put("openDate", GetDateUtils.getDate());
        item.put("openMode", "0");
        item.put("openDepartId", msg.get("channelId"));
        item.put("openStaffId", msg.get("operatorId"));
        item.put("inDepartId", msg.get("channelId"));
        item.put("removeTag", "0");
        item.put("userStateCodeset", "0");
        item.put("userDiffCode", "00");
        item.put("mputeMonthFee", "0");
        item.put("assureDate", GetDateUtils.getDate());
        item.put("developDate", GetDateUtils.getDate());
        item.put("developDepartId", msg.get("recomDepartId"));
        item.put("productTypeCode", msg.get("tradeUserProductTypeCode"));
        item.put("inNetMode", "0");
        // TRADE_USER下根据外围传值增加发展人地市，改变CITY_CODE取值
        if (StringUtils.isNotEmpty(recomCity)) {
            // item.put("cityCode", recomCity); 去掉防止覆盖CITY_CODE。cuij 2017-05-17
            item.put("developCityCode", recomCity);
        }
        // 推荐人编码
        // 有些省份会校验发展人是否为空，所以默认将发展人填写为推荐人，
        // 若推荐人也没有填写，则填写受理人
        if (StringUtils.isNotEmpty(recomPersonId)) {
            item.put("developStaffId", recomPersonId);
            item.put("inStaffId", recomPersonId);
        }
        else {
            item.put("developStaffId", msg.get("operatorId"));
            item.put("inStaffId", msg.get("operatorId"));
        }
        itemList.add(item);
        tradeUser.put("item", itemList);
        return tradeUser;
    }

    private Map preCustPerData(Map msg) {
        String cityCode = (String) msg.get("district");
        if (null != cityCode && cityCode.length() > 6) {
            cityCode = cityCode.substring(0, 6);
        }
        Map tradeCustPerson = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("custId", msg.get("custId"));
        item.put("psptTypeCode", msg.get("certType"));
        item.put("psptId", msg.get("certNum"));
        item.put("psptEndDate", msg.get("certExpireDate"));
        item.put("psptAddr", msg.get("certAdress"));
        item.put("custName", msg.get("custName"));
        item.put("sex", msg.get("sex"));
        item.put("eparchyCode", msg.get("eparchyCode"));
        item.put("cityCode", cityCode);
        item.put("postAddress", msg.get("contactAddress"));
        item.put("phone", msg.get("contactPhone"));
        item.put("contact", msg.get("contactPerson"));
        item.put("contactPhone", msg.get("contactPhone"));
        item.put("removeTag", "0");
        item.put("postCode", "");
        item.put("homeAddress", "");
        item.put("email", "");
        item.put("birthday", "");
        item.put("job", "");
        item.put("workName", "");
        item.put("marriage", "");
        itemList.add(item);
        tradeCustPerson.put("item", itemList);
        return tradeCustPerson;
    }

    private Map preCustomerData(Map msg) {
        String cityCode = (String) msg.get("district");
        if (null != cityCode && cityCode.length() > 6) {
            cityCode = cityCode.substring(0, 6);
        }
        // 个人客户与集团客户转换，2位转为1位.by：cuij
        String customerType = (String) msg.get("customerType");
        if ("01,02".contains(customerType)) {
            customerType = "01".equals(customerType) ? "0" : "1";
        }
        Object checkType = ((List<Map>) msg.get("userInfo")).get(0).get("checkType");
        Map tradeCustomer = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("sustId", msg.get("custId"));
        item.put("psptTypeCode", msg.get("certType"));
        item.put("psptId", msg.get("certNum"));
        item.put("custName", msg.get("custName"));
        item.put("custType", customerType);
        item.put("custState", "0");
        item.put("openLimit", "0");
        item.put("eparchyCode", msg.get("eparchyCode"));
        item.put("cityCode", cityCode);
        item.put("developDepartId", msg.get("recomDepartId"));
        item.put("inDate", GetDateUtils.getDate());
        item.put("removeTag", "0");
        item.put("rsrvTag1", decodeCheckType4G(checkType));
        item.put("custPasswd", "");
        itemList.add(item);
        tradeCustomer.put("item", itemList);
        return tradeCustomer;
    }

    // 转换cbcheckType
    private String decodeCheckType4G(Object checkType) {
        if ("01".equals(checkType) || "02".equals(checkType)) {
            return "3";
        }
        return "03".equals(checkType) ? "4" : "2";
    }

    // 获取账户付费方式编码
    private String getPayModeCode(Map msg) {
        Map param = new HashMap();
        param.put("EPARCHY_CODE", msg.get("eparchyCode"));
        param.put("PARAM_CODE", msg.get("accountPayType"));
        param.put("PROVINCE_CODE", "00" + msg.get("province"));
        List<Map> payModeCoderesult = dao.queryPayModeCode(param);
        if (payModeCoderesult.size() > 0) {
            return payModeCoderesult.get(0).get("PARA_CODE1").toString();
        }
        return "0";
    }

    private Map preTradeResData(Map msg) {
        Map tradeRes = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("reTypeCode", "0");
        item.put("resCode", msg.get("serialNumber"));
        item.put("modifyTag", "0");
        item.put("startDate", GetDateUtils.getDate());
        item.put("endDate", "20501231235959");
        itemList.add(item);
        String simId = (String) msg.get("simId");
        if (StringUtils.isNotEmpty(simId)) {
            Map cardInfo = (Map) msg.get("cardInfo");
            Map item2 = new HashMap();
            if (EcAopConfigLoader.getStr("ecaop.global.param.card.aop.province").contains((String) msg.get("province"))) {// 走号卡中心

                item2.put("xDatatype", "NULL");
                item2.put("modifyTag", "0");
                item2.put("reTypeCode", "1");
                item2.put("resCode", simId);
                item2.put("resInfo1", cardInfo.get("IMSI"));
                item2.put("resInfo2", "notRemote");
                item2.put("resInfo4", "1000101");
                item2.put("resInfo5", ChangeCodeUtils.changeCardType(cardInfo.get("MATERIAL_CODE")));// 号卡中心卡类型转换
                item2.put("resInfo7", "");
                item2.put("resInfo8", "");// -PUK2
                item2.put("resInfo6", "1234-12345678");// CB说写死
                item2.put("startDate", GetDateUtils.getDate());
                item2.put("endDate", "20501231235959");
                itemList.add(item2);

            }
            else {// 走原流程
                item2.put("xDatatype", "NULL");
                item2.put("modifyTag", "0");
                item2.put("reTypeCode", "1");
                item2.put("resCode", simId);
                item2.put("resInfo1", cardInfo.get("imsi"));
                item2.put("resInfo2", "notRemote");
                item2.put("resInfo4", "1000101");
                item2.put("resInfo5", cardInfo.get("cardType"));
                item2.put("resInfo7", cardInfo.get("ki"));
                item2.put("resInfo8", cardInfo.get("pin2") + "-" + cardInfo.get("puk2"));
                item2.put("resInfo6", cardInfo.get("pin") + "-" + cardInfo.get("puk"));
                item2.put("startDate", GetDateUtils.getDate());
                item2.put("endDate", "20501231235959");
                itemList.add(item2);

            }

        }
        else {// 插一条RES_TYPE_CODE为1 的数据
            Map item3 = new HashMap();
            item3.put("xDatatype", "NULL");
            item3.put("reTypeCode", "1");
            item3.put("resCode", "89860");
            item3.put("modifyTag", "0");
            item3.put("startDate", GetDateUtils.getDate());
            item3.put("endDate", "20501231235959");
            itemList.add(item3);
        }
        tradeRes.put("item", itemList);
        return tradeRes;

    }

    private Map preTradePayRelData(Map msg) {
        Map tradePayRel = new HashMap();
        Map item = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        item.put("xDatatype", "NULL");
        item.put("userId", msg.get("userId"));
        item.put("acctId", msg.get("acctId"));
        item.put("payitemCode", "-1");
        item.put("acctPriority", "0");
        item.put("userPriority", "0");
        item.put("bindType", "1");
        item.put("startAcycId", msg.get("cycId"));
        item.put("endAcycId", "203712");
        item.put("defaultTag", "1");
        item.put("limitType", "0");
        item.put("limit", "0");
        item.put("complementTag", "0");
        item.put("addupMonths", "0");
        item.put("addupMethod", "0");
        item.put("payrelationId", msg.get("payRelId"));
        item.put("actTag", "1");
        itemList.add(item);
        tradePayRel.put("item", itemList);
        return tradePayRel;
    }

    private Map preDataUtil(Map msg, String tradeinfo) {
        List<Map> tradeinfoList = (List<Map>) msg.get(tradeinfo);
        Object checkType = ((List<Map>) msg.get("userInfo")).get(0).get("checkType");
        // 北分卖场
        if ("mabc|gxws".contains(String.valueOf(msg.get("appCode"))) && "itemList".equals(tradeinfo)) {
            String netType = (String) msg.get("netType");
            String eInMode = "";
            if (null == netType || "".equals(netType)) {
                eInMode = "JTOTO";
            }
            else {
                Map netTypes = MapUtils.asMap("00", "JTOTO", "01", "HTOTO", "02", "BDOTO", "03", "LSOTO", "04",
                        "HWOTO", "05", "XMOTO", "06", "BJMC", "07", "WYG");
                eInMode = (String) netTypes.get(netType);
            }
            if (!"BJMC".equals(eInMode)) {
                tradeinfoList.add(LanUtils.createTradeItem("E_IN_MODE", eInMode));
                tradeinfoList.add(LanUtils.createTradeItem("OTO_ORDER_ID", msg.get("ordersId")));
            }
            tradeinfoList.add(LanUtils.createTradeItem("ORDER_SOURCE", "OTO11"));
        }
        // 王者会员改造4 start
        if ("subItemList".equals(tradeinfo)) {// 针对trade_sub_item节点做特殊处理
            String kingVipMonth = getFromPara(msg, "NEWKSVIP");
            if (!"".equals(kingVipMonth)) {
                msg.put("kingVip", changeCodeForKingVip(kingVipMonth));// 放到msg中后边准备base节点用，此处转码为1位
                // 腾讯大王卡业务需求办理 by wangmc 20171113
                String haveTradeSubItemKey = EcAopConfigLoader
                        .getStr("ecaop.global.param.NEWKSVIP.haveTradeSubItem.key");
                if (haveTradeSubItemKey.contains(kingVipMonth)) {
                    String startDate = GetDateUtils.getSysdateFormat();
                    String endDate = GetDateUtils.rollDateStr(startDate, "yyyy-MM-dd HH:mm:ss", Calendar.DATE,
                            Integer.valueOf(kingVipMonth) * 31);
                    if (endDate != null) {// 将时分秒写死为23:59:59
                        endDate = endDate.substring(0, 11) + "23:59:59";
                    }
                    tradeinfoList.add(LanUtils.createTradeSubItemAll("0", "NEWKSVIP", kingVipMonth, msg.get("userId"),
                            startDate, endDate));
                }
            }

            // 新增非必传项realPersonTag,实人认证标识
            if (StringUtils.isNotEmpty((String) msg.get("realPersonTag"))) {
                /* 集客证件类型
                 * 07    营业执照
                 * 13    组织机构代码证
                 * 14    事业单位法人证书
                 * 15    介绍信
                 * 17    社会团体法人登记证书
                 * 18    照会
                 * 21    民办非企业单位登记证书
                 * 20    小微企业客户证件
                 * 33    统一社会信用代码证书
                 * 除了上面以外的证件类型是个人证件类型 */
                List<Map> customerInfo = (List<Map>) msg.get("customerInfo");
                List<Map> newCustInfoList = (List<Map>) customerInfo.get(0).get("newCustomerInfo");
                String certType = (String) newCustInfoList.get(0).get("certType");
                if ("07,13,14,15,17,18,21,20,33".contains(certType)) {
                    tradeinfoList.add(LanUtils.createTradeSubItem4("REAL_PERSON_TAG", "0", msg.get("realPersonTag"),
                            (String) msg.get("userId")));
                }
                else {
                    tradeinfoList.add(LanUtils.createTradeSubItem4("REAL_PERSON_TAG", "4", msg.get("realPersonTag"),
                            (String) msg.get("userId")));
                    tradeinfoList.add(LanUtils.createTradeSubItem4("REAL_PERSON_TAG", "0", msg.get("realPersonTag"),
                            (String) msg.get("userId")));
                }
            }
            // RSD2018061400051-山东关于CBSS产品开户支撑优惠校园区域需求
            if (!IsEmptyUtils.isEmpty(msg.get("subItem"))) {
                List<Map> msgSubTtem = (List<Map>) msg.get("subItem");
                if ("".equals(msg.get("sdcellcountId"))) {
                    throw new EcAopServerBizException("9999", "subItem节点传值时候packageElement节点必须有值！");
                }
                for (Map item : msgSubTtem) {// 这个节点如果传了，那么userInfo下面的packageId必传
                    tradeinfoList.add(lan.createTradeSubItem3((String) msg.get("sdcellcountId"),
                            (String) item.get("attrCode"), item.get("attrValue"), "20501231235959",
                            GetDateUtils.getDate()));
                }
            }
            // RSD201808200001-AOP移网开户处理申请接口支持CBSS送资费属性的需求
            List<Map> cellCountList = (List<Map>) msg.get("cellCountList");
            if (!IsEmptyUtils.isEmpty(cellCountList)) {
                for (Map cellCount : cellCountList) {
                    tradeinfoList.add(lan.createTradeSubItem3((String) cellCount.get("itemId"),
                            (String) cellCount.get("attrCode"), cellCount.get("attrValue"), "20501231235959",
                            GetDateUtils.getDate()));
                }
            }

        }
        // 王者会员改造4 end
        // zzc 钉钉需求 20170317
        if ("itemList".equals(tradeinfo)) {// 针对trade_item节点做特殊处理
            if (tradeinfoList == null) {// tradeinfoList为空时重新new一个
                tradeinfoList = new ArrayList();
            }
            // 接口新增字段grpPurchaseId（团购id）和grpPurchaseCnt（团购人数）
            if (StringUtils.isNotEmpty((String) msg.get("grpPurchaseId"))) {
                tradeinfoList.add(LanUtils.createTradeItem("GROUP_PURCHASE_ID", msg.get("grpPurchaseId")));
            }
            if (StringUtils.isNotEmpty((String) msg.get("grpPurchaseCnt"))) {
                tradeinfoList.add(LanUtils.createTradeItem("GROUP_PURCHASE_COUNT", msg.get("grpPurchaseCnt")));
            }
            // 新增非必传项photoTag,照片标识
            if (StringUtils.isNotEmpty((String) msg.get("photoTag"))) {
                tradeinfoList.add(LanUtils.createTradeItem("PHOTO_TAG", msg.get("photoTag")));
            }
            List<Map> tradeItemList = (List<Map>) msg.get("tradeItem");
            if (!IsEmptyUtils.isEmpty(tradeItemList)) {
                for (Map tradeItem : tradeItemList) {
                    tradeinfoList.add(LanUtils.createTradeItem((String) tradeItem.get("attrCode"),
                            tradeItem.get("attrValue")));
                }
            }
            // RGD2018080700047 最大担保次数
            tradeinfoList.add(LanUtils.createTradeItem("TOTAL_COUNT_PRODUCTASSURE", ((List<Map>) msg.get("userInfo"))
                    .get(0).get("totalCount")));
            // 新增必传字段ordersId,外围系统订单id
            tradeinfoList.add(LanUtils.createTradeItem("ONLINE_REAL_PERSON_TAG", msg.get("ordersId")));

            // 2017-4-14 -start AOP开户处理申请接口新增字段    非必填项devAgenAcct by Zeng
            // 费劲地从msg->userInfo->product下取出devAgenAcct参数，为1时TRADE_ITEM下发attr_code="DevAgencyAcct", attr_value="1"节点
            List<Map> userInfoList = (List<Map>) msg.get("userInfo");
            if (null != userInfoList && userInfoList.size() > 0) {
                checkDevAgenAcct: for (Map userTemp : userInfoList) {
                    List<Map> productList = null;
                    if (userTemp.get("product") instanceof Map) {
                        productList = new ArrayList();
                        productList.add((Map) userTemp.get("product"));
                    }
                    else {
                        productList = (List<Map>) userTemp.get("product");
                    }
                    for (Map pro : productList) {
                        // 第一个条件没必要，但还是加上，多点代码没事：）
                        if (null != pro.get("devAgenAcct") && "1".equals(pro.get("devAgenAcct"))) {
                            tradeinfoList.add(LanUtils.createTradeItem("DevAgencyAcct", "1"));
                            break checkDevAgenAcct;
                        }
                    }
                }
            }
            // 2017-4-14 -end
            boolean flag = false;
            try {
                List<Map> para = (List<Map>) msg.get("para");
                String id = null;
                String value = null;
                if (para != null && para.size() > 0) {
                    for (Map temp : para) {
                        String str1 = temp.get("paraId") + "";
                        if ("DDINGBAT01".equals(str1) || "DDINGBAT02".equals(str1)) {
                            flag = true;
                            id = str1;
                            value = temp.get("paraValue") + "";
                        }
                    }
                    if (id != null && value != null)
                        tradeinfoList.add(LanUtils.createTradeItem(id, value));
                }
                // 新增代办人信息 zzc 20170317
                List<Map> actorInfoList = (List) msg.get("actorInfo");
                Map actorInfo = null;
                if (actorInfoList != null && actorInfoList.size() > 0) {
                    flag = true;
                    actorInfo = actorInfoList.get(0);
                    /* tradeinfoList.add(LanUtils.createTradeItem("ACTOR_CERTTYPEID",
                     * actorInfo.get("actorCertTypeId")));// 代办人类型
                     * tradeinfoList.add(LanUtils.createTradeItem("ACTOR_NAME", actorInfo.get("actorName")));// 代办人姓名
                     * tradeinfoList.add(LanUtils.createTradeItem("ACTOR_PHONE", actorInfo.get("actorPhone")));// 代办人电话
                     * tradeinfoList.add(LanUtils.createTradeItem("ACTOR_CERTNUM", actorInfo.get("actorCertNum")));//
                     * 代办人证件 */
                    tradeinfoList.add(LanUtils.createTradeItem("ACTOR_ADDRESS", actorInfo.get("actorAddress")));// 代办人地址
                }
            }
            catch (Exception e) {
                if (flag) {
                    throw new EcAopServerBizException("9999", "钉钉异常：" + e.getMessage());
                }
            }
            // 新增实名制标示覆盖 by:cuij 20170327
            tradeinfoList.add(LanUtils.createTradeItem("CUSTOMER_TAGCHANGE", decodeCheckType4G(checkType)));
        }
        Map tradeMap = new HashMap();
        tradeMap.put("item", tradeinfoList);
        return tradeMap;
    }

    private void dealResourceAndNumFee(Map msg) {
        String cityCode = (String) msg.get("district");
        if (null != cityCode && cityCode.length() > 6) {
            cityCode = cityCode.substring(0, 6);
        }
        String resourceFee = (String) msg.get("resourceFee");
        String isOwnerPhone = (String) msg.get("isOwnerPhone");
        List<Map> resourceItemInfo = (List<Map>) msg.get("subItemList");
        Map niceinfo = (Map) msg.get("niceInfo");
        Object staffId = msg.get("operatorId");
        Object tradeId = msg.get("tradeId");
        List<Map> useCustInfo = (List<Map>) msg.get("useCustInfo");// 责任人使用人属性下发
        Object delayTag = null == msg.get("delayTag") ? "0" : msg.get("delayTag");// 压单标志
        String acceptDate = GetDateUtils.getDate();
        List<Map> tradeFeeItemList = new ArrayList<Map>();
        if (StringUtils.isNotEmpty(resourceFee) && !"0".equals(resourceFee)) {
            // 传入单位为厘
            int fee = Integer.parseInt(resourceFee) / 10;
            Map tradeFeeItem = new HashMap();
            tradeFeeItem.put("feeMode", "0");
            tradeFeeItem.put("feeTypeCode", "4310");
            tradeFeeItem.put("oldFee", String.valueOf(fee));
            tradeFeeItem.put("fee", String.valueOf(fee));
            tradeFeeItem.put("chargeSourceCode", "1");
            tradeFeeItem.put("apprStaffId", staffId);
            tradeFeeItem.put("calculateId", tradeId);
            tradeFeeItem.put("calculateDate", acceptDate);
            tradeFeeItem.put("staffId", staffId);
            tradeFeeItem.put("calculateTag", "0");
            tradeFeeItem.put("payTag", "0");
            tradeFeeItem.put("xDatatype", "NULL");
            tradeFeeItemList.add(tradeFeeItem);
        }
        List<Map> subItemList = new ArrayList<Map>();
        // 带终端的合约开户时，处理终端属性
        if (null != resourceItemInfo && !resourceItemInfo.isEmpty()) {
            subItemList.addAll(resourceItemInfo);
        }
        List<Map> itemList = new ArrayList<Map>();
        // 压单标志处理
        if ("1".equals(delayTag)) {
            Map item = new HashMap();
            item.put("attrCode", "E_DELAY_TIME_CEL");
            item.put("attrValue", "1");
            item.put("xDatatype", "NULL");
            itemList.add(item);
        }
        String niceItemId = TradeManagerUtils.getSequence(msg.get("eparchyCode") + "", "SEQ_ITEM_ID");
        if ("1".equals(isOwnerPhone)) {
            Map item = new HashMap();
            item.put("attrTypeCode", "6");
            item.put("attrCode", "isOwnerPhone");
            item.put("itemId", msg.get("userId"));
            item.put("attrValue", "1");
            item.put("xDatatype", "NULL");
            item.put("startDate", acceptDate);
            item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            subItemList.add(item);
        }
        // RHQ2017052500016- 空中入网配合资源中心割接空中入网卡的改造
        if ("1".equals(msg.get("airCardTag"))) {
            Map item = new HashMap();
            item.put("attrTypeCode", "0");
            item.put("attrCode", "airCardTag");
            item.put("attrValue", "1");
            item.put("startDate", acceptDate);
            item.put("itemId", msg.get("userId"));
            item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            subItemList.add(item);
        }
        // 20180932-CBSS提供携号转网需求
        if ("0".equals(msg.get("numPort"))) {
            subItemList.add(lan.createTradeSubItemJ("IS_NPUSER_IN", "1", (String) msg.get("userId")));
            // RTJ2018103100005-AOP接口支持携号转网业务
            Map numInfoMap = (Map) msg.get("numInfoMap");
            if (!IsEmptyUtils.isEmpty(numInfoMap)) {// 不为空时，四项全部必传
                itemList.add(LanUtils.createTradeItem("AuthCode", numInfoMap.get("authCode")));
                itemList.add(LanUtils.createTradeItem("Expired", numInfoMap.get("expired")));
                itemList.add(LanUtils.createTradeItem("PSPT_ID", numInfoMap.get("certNum")));
                itemList.add(LanUtils.createTradeItem("PSPT_TYPE_CODE",
                        CertTypeChangeUtils.certTypeMall2Cbss((String) numInfoMap.get("certType"))));
            }
        }

        // RHQ2018040200021-SMA合作项目沃飞翔卡产品配置需求
        List<Map> paraList = (List<Map>) msg.get("para");
        String isCreateBatType = "";
        if (!IsEmptyUtils.isEmpty(paraList)) {
            for (Map para : paraList) {
                if ("IS_CREATE_BAT_TYPE".equals(para.get("paraId"))) {
                    isCreateBatType = (String) para.get("paraValue");
                }
            }
            if (!"".equals(isCreateBatType)) {
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("attrTypeCode", "0");
                item.put("itemId", msg.get("userId"));
                item.put("attrCode", "IS_CREATE_BAT_TYPE");
                item.put("attrValue", isCreateBatType);
                item.put("startDate", acceptDate);
                item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                subItemList.add(item);
            }
        }
        // RHA2017060600026-河南联通关于cBSS、北六ESS支持按照工单判断后激活的需求
        if ("1".equals(msg.get("isAfterActivation"))) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("attrCode", "IS_AFTER_ACTIVATION");
            item.put("attrValue", "1");
            item.put("attrTypeCode", "0");
            item.put("itemId", msg.get("userId"));
            // item.put("startDate", acceptDate);
            // item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            itemList.add(item);
        }

        // RHQ2017022000003-关于新增中国联通联泰卡业务的需求 by maly
        String cardCategory = (String) msg.get("cardCategory");
        if (StringUtils.isNotEmpty(cardCategory)) {// 业务卡类型 0:联泰卡 1:工匠卡
            Map item = new HashMap();
            item.put("attrCode", "2b2c");
            item.put("attrValue", "0".equals(cardCategory) ? "LT" : "GJ");
            item.put("xDatatype", "NULL");
            itemList.add(item);
        }
        // RHQ2017022000003-关于新增中国联通联泰卡业务的需求

        /**
         * RHQ2017060900036-沃小号业务开发支撑需求 ,START
         * 新增非必填项serialNumberA群组号码，relationTypeCode关系类型编码
         * create by zhaok 2017-10-13
         */
        String serialNumberA = (String) msg.get("serialNumberA");
        String relationTypeCode = (String) msg.get("relationTypeCode");
        String useridA = (String) msg.get("useridA");

        if (StringUtils.isNotEmpty(serialNumberA) && StringUtils.isNotEmpty(relationTypeCode)
                && StringUtils.isNotEmpty(useridA)) {
            if ("wxh".equals(relationTypeCode)) {
                Map item = new HashMap();
                item.put("attrCode", "wxhStart");
                item.put("attrValue", "0");
                itemList.add(item);
            }
            String msgStartDate = (String) msg.get("startDate");
            String startDate = StringUtils.isNotEmpty(msgStartDate) ? msgStartDate : GetDateUtils.getDate();
            List<Map> tradeRelation = new ArrayList<Map>();
            Map relaItem = new HashMap();
            relaItem.put("startDate", startDate);
            relaItem.put("modifyTag", "0");
            relaItem.put("serialNumberA", serialNumberA);
            relaItem.put("serialNumberB", msg.get("serialNumber"));
            relaItem.put("endDate", "2050-12-31 23:59:59");
            relaItem.put("relationTypeCode", relationTypeCode);
            relaItem.put("xDatatype", "NULL");
            relaItem.put("roleCodeA", "0");
            relaItem.put("roleCodeB", "0");
            relaItem.put("itemId", msg.get("userId"));
            relaItem.put("idA", useridA);
            relaItem.put("idB", msg.get("userId"));
            tradeRelation.add(relaItem);

            // 如果msg有tradeRelation的key，就把原来的数据取出来重新放入
            boolean resultRela = msg.containsKey("tradeRelation");
            if (resultRela) {
                List<Map> oldResult = (List<Map>) msg.get("tradeRelation");
                tradeRelation.addAll(oldResult);
            }
            msg.put("tradeRelation", tradeRelation);
        }
        // RHQ2017060900036-沃小号业务开发支撑需求 ,END

        // 处理靓号信息
        if (niceinfo != null && niceinfo.size() > 0) {
            String classId = (String) niceinfo.get("classId");
            String lowCostPro = (String) niceinfo.get("lowCostPro");
            String feeTypeCode = "101117"; // 普号预存话费
            String endDate = "2050-12-31 23:59:59";
            String months = (String) niceinfo.get("timeDurPro");
            // 2017-3-9 生产靓号有效期为24时却下发120的BUG处理 by Zeng-start
            // 不传默认为最长120个月？
            // 靓号有效期最多120个月
            try {
                months = null == months || "".equals(months) || "null".equals(months) || "00000".equals(months)
                        || "9999".equals(months) ? "120" : months;
                months = Integer.parseInt(months) > 120 ? "120" : months;
            }
            catch (Exception e) {
                throw new EcAopServerBizException("9999", "协议时长timeDurPro校验时转换为Int异常：" + e.getMessage());
            }
            // 2017-3-9 生产靓号有效期为24时却下发120的BUG处理 by Zeng-end
            endDate = GetDateUtils.getSpecifyDateTime(1, 2, GetDateUtils.getMonthLastDay(), Integer.valueOf(months));

            // 2017-6-1 RHQ2017051200006 - 移动靓号减免规则优化需求 by Zeng -start 1/2
            /**
             * 靓号规则条件修改：
             * a）靓号等级，即classId 为1-6时；
             * b）靓号协议期，即protocolTime为0时；
             * 以上两个条件同时满足时，给cb下发的逻辑做以下修改
             * （1）资费属性值，leaseLength 协议期（在网时长）落0；
             * （2）88888888资费的开始时间落：次月1日 0：00：00；失效时间落：次月1日 0：10：00；yyyyMMddHHmmss
             * （3）88888888资费属性值的开始时间落：次月1日 0：00：00；失效时间落：次月1日 0：10：00；yyyyMMddHHmmss
             */
            if ("1|2|3|4|5|6".contains((String) niceinfo.get("classId")) && "0".equals(niceinfo.get("timeDurPro"))) {
                months = "0";
                endDate = GetDateUtils.getNextMonthFirstDayFormat().substring(0, 10) + "1000";

            }
            // 2017-6-1 RHQ2017051200006 - 移动靓号减免规则优化需求 by Zeng -end 1/2

            // 如果是靓号，费用项编码为123457
            if (StringUtils.isNotEmpty((String) niceinfo.get("classId")) && !"9".equals(niceinfo.get("classId"))) {
                feeTypeCode = "123457";
            }
            // 号码台账费用
            String numFee = (String) niceinfo.get("advancePay");
            if (StringUtils.isNotEmpty(numFee) && !"0".equals(numFee)) {
                int fee = Integer.parseInt(numFee) / 10;// 传入单位为里
                Map tradeFeeItem1 = new HashMap();
                tradeFeeItem1.put("feeMode", "2");
                tradeFeeItem1.put("feeTypeCode", feeTypeCode);
                tradeFeeItem1.put("oldFee", String.valueOf(fee));
                tradeFeeItem1.put("fee", String.valueOf(fee));
                tradeFeeItem1.put("chargeSourceCode", "1");
                tradeFeeItem1.put("apprStaffId", staffId);
                tradeFeeItem1.put("calculateId", tradeId);
                tradeFeeItem1.put("calculateDate", GetDateUtils.getNextMonthFirstDayFormat());
                tradeFeeItem1.put("staffId", staffId);
                tradeFeeItem1.put("calculateTag", "0");
                tradeFeeItem1.put("payTag", "0");
                tradeFeeItem1.put("xDatatype", "NULL");
                tradeFeeItemList.add(tradeFeeItem1);

            }

            //
            if ("1|2|3|4|5".contains(classId)
                    || "6".equals(classId)
                    && (StringUtils.isNotBlank(numFee) && !"0".equals(numFee) || StringUtils.isNotBlank(lowCostPro)
                            && !"0".equals(lowCostPro) || StringUtils.isNotBlank(months) && !"0".equals(months))) { // 如果是靓号，需要传88888888
                                                                                                                    // 资费

                Map discntItem = new HashMap();
                discntItem.put("id", msg.get("userId"));
                discntItem.put("idType", "1");
                discntItem.put("productId", "-1");
                discntItem.put("packageId", "-1");
                discntItem.put("discntCode", "88888888");
                discntItem.put("specTag", "0");
                discntItem.put("relationTypeCode", "");
                discntItem.put("itemId", niceItemId);
                discntItem.put("modifyTag", "0");
                discntItem.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                discntItem.put("endDate", endDate);
                discntItem.put("userIdA", "-1");
                discntItem.put("xDatatype", "NULL");
                msg.put("numberDiscnt", discntItem);

                // 低消
                if (StringUtils.isNotEmpty(lowCostPro) && !"0".equals(lowCostPro)) {
                    Map item = new HashMap();
                    item.put("attrTypeCode", "3");
                    item.put("attrCode", "lowCost");
                    item.put("itemId", niceItemId);
                    item.put("attrValue", Integer.parseInt(lowCostPro) / 10);
                    item.put("xDatatype", "NULL");
                    item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                    item.put("endDate", endDate);
                    subItemList.add(item);
                }
                // 在网时长
                if (StringUtils.isNotBlank(months)) {
                    Map item = new HashMap();
                    item.put("attrTypeCode", "3");
                    item.put("attrCode", "leaseLength");
                    item.put("itemId", niceItemId);
                    item.put("attrValue", months);
                    item.put("xDatatype", "NULL");
                    item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                    item.put("endDate", endDate);
                    subItemList.add(item);
                }
                // 靓号号码标识,值为1（默认值） --标识是新靓号
                Map item = new HashMap();
                item.put("attrTypeCode", "3");
                item.put("attrCode", "NewNumTag");
                item.put("itemId", niceItemId);
                item.put("attrValue", "1");
                item.put("xDatatype", "NULL");
                item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                item.put("endDate", endDate);
                subItemList.add(item);
            }
        }
        // 处理tradeitem、tradesubitem公用信息
        Map appParam = new HashMap();
        String serialNumber = (String) msg.get("serialNumber");
        String provinceCode = "00" + msg.get("province");
        Map<String, Object> appendMap = new HashMap<String, Object>();
        if (new ChangeCodeUtils().isWOPre((String) msg.get("appCode"))) {
            appParam.put("WORK_TRADE_ID", msg.get("ordersId"));
        }
        appParam.put("TRADE_TYPE_CODE", "10");
        appParam.put("NET_TYPE_CODE", "ZZ");// 默认ZZ
        appParam.put("BRAND_CODE", "ZZZZ");// 默认ZZZZ
        appParam.put("PRODUCT_ID", "-1");// 默认-1
        appParam.put("EPARCHY_CODE", "ZZZZ");// 默认ZZZZ
        appParam.put("PROVINCE_CODE", "ZZZZ");// 默认ZZZZ

        // 电子发票属性下发
        List<Map> electricInfo = (List<Map>) msg.get("electricInfo");
        if ("masb".equals(msg.get("appCode")) && null != electricInfo && !electricInfo.isEmpty()) {
            appendMap.put("BUYER_PHONE", electricInfo.get(0).get("electricPhone"));
            String electricEmail = (String) electricInfo.get(0).get("electricEmail");
            if (null != electricEmail && !"".equals(electricEmail)) {
                appendMap.put("ELECTRONIC_BUYER_EMAIL", electricEmail);
            }
            appendMap.put("BUYER_NAME", electricInfo.get(0).get("buyerName"));
            String buyerTaxpayerId = (String) electricInfo.get(0).get("buyerTaxpayerId");
            if (null != buyerTaxpayerId && !"".equals(buyerTaxpayerId)) {
                appendMap.put("BUYER_TAXPAYER_ID", buyerTaxpayerId);
            }
            String buyerAdd = (String) electricInfo.get(0).get("buyerAdd");
            if (null != buyerAdd && !"".equals(buyerAdd)) {
                appendMap.put("BUYER_ADD", buyerAdd);
            }
            String buyerBankAccount = (String) electricInfo.get(0).get("buyerBankAccount");
            if (null != buyerBankAccount && !"".equals(buyerBankAccount)) {
                appendMap.put("BUYER_BANK_ACCOUNT", buyerBankAccount);
            }
            appendMap.put("WRITE_MANAGER", electricInfo.get(0).get("writeManager"));
            appendMap.put("INVOICE_COMPANY_NATURE", electricInfo.get(0).get("invoiceCompanyNature"));

        }
        appendMap.put("CITY_CODE", cityCode);
        appendMap.put("EPARCHY_CODE", msg.get("eparchyCode"));
        appendMap.put("localNetCode", msg.get("eparchyCode"));// 暂无
        appendMap.put("USER_PASSWD", serialNumber.substring(serialNumber.length() - 6));
        appendMap.put("serialNumber", serialNumber);
        appendMap.put("INIT_PASSWD", serialNumber.substring(serialNumber.length() - 6));
        // 2017-03-21 解决钉钉卡开户竣短信中密码为空的问题 by Zeng -start
        appendMap.put("NEW_PASSWD", serialNumber.substring(serialNumber.length() - 6));
        // 2017-03-21 解决钉钉卡开户竣短信中密码为空的问题 by Zeng -end
        appendMap.put("USER_TYPE_CODE", "0");
        String nbrSrc = getNumberSrc(provinceCode, serialNumber);
        appendMap.put("nbrSrc", nbrSrc); // nbrSrc
        appendMap.put("tradeId", msg.get("ordersId"));
        appendMap.put("NUMBER_TYPE", serialNumber.substring(0, 3)); // NUMBER_TYPE
        appendMap.put("MAIN_PRODUCT_ID", msg.get("mProductId")); // 主产品
        appendMap.put("PART_ACTIVE_PRODUCT", msg.get("PART_ACTIVE_PRODUCT"));// 活动
        appendMap.put("PROVINCE_CODE", provinceCode);
        appendMap.put("SUBSYS_CODE", "MALL");
        appendMap.put("initpwd", "1");
        appendMap.put("groupFlag", "0");
        appendMap.put("custgroup", "0");
        appendMap.put("TRADE_TYPE_CODE", "0010".substring(1));
        appendMap.put("ROLE_CODE_B", "1");
        appendMap.put("STANDARD_KIND_CODE", "1");
        appendMap.put("OPER_CODE", "1");
        appendMap.put("PRODUCT_TYPE_CODE", msg.get("mProductTypeCode"));// z主产品
        appendMap.put("NET_TYPE_CODE", "50");
        appendMap.put("IS_SAME_CUST", msg.get("custId"));// custId
        appendMap.put("IS_CHANGE_NET", "1");

        Map cardInfo = (Map) msg.get("cardInfo");
        if ("1".equals(msg.get("IS_REMOTE"))) {// 成卡
            appendMap.put("NOT_REMOTE", "1");
        }
        if (null != cardInfo
                && !cardInfo.isEmpty()
                && EcAopConfigLoader.getStr("ecaop.global.param.card.aop.province").contains(
                        (String) msg.get("province"))) {
            appendMap.put("NEW_CARD_DATA", cardInfo.get("KI"));
        }
        if ("0".equals(msg.get("numSwitch"))) {// 号卡中心标志
            appendMap.put("NUMERICAL_SELECTION", "2");
        }
        else {
            appendMap.put("NUMERICAL_SELECTION", "1");
        }
        if ("apsb".equals(msg.get("appCode"))) {
            appendMap.put("E_IN_MODE", "A");
        }
        if ("mnsb".equals(msg.get("appCode"))) {
            appendMap.put("E_IN_MODE", "M");
        }
        if ("0".equals(msg.get("deductionTag"))) {
            appendMap.put("FEE_TYPE", "1");
            appendMap.put("PURCHASE_FEE_TYPE ", "1");
        }
        if (StringUtils.isNotEmpty((String) msg.get("eModeCode"))) {// 不再校验长度 by wangmc 20180517
            appendMap.put("E_IN_MODE", msg.get("eModeCode"));
        }
        if ((Boolean) msg.get("newRetailFlag")) {// 20180615针对新零售修改inModeCode和E_IN_MODE为N
            appendMap.put("E_IN_MODE", "N");
        }
        if ("1".equals(msg.get("delayDealTag"))) {
            appendMap.put("delayDealTag", "1");
        }
        if ("1".equals(msg.get("jdbtTag"))) {
            appendMap.put("JDBT", msg.get("jdbtTag"));
        }
        if ("0".equals(msg.get("isTest"))) {
            appendMap.put("GDTEST", "1");
        }
        // 行销标示处理
        if ("1".equals(msg.get("markingTag"))) {
            appendMap.put("MARKING_APP", "1");
        }

        // RZJ2017040600023 - 浙江沃受理APP补充需求
        String saleModType = (String) msg.get("saleModType");
        if (StringUtils.isNotEmpty(saleModType)) {// 销售模式类型
            String marketingChannelType = "0".equals(saleModType) ? "01" : "02";
            appendMap.put("MarketingChannelType", marketingChannelType);
        }
        // RZJ2017040600023 - 浙江沃受理APP补充需求

        if (StringUtils.isNotEmpty((String) msg.get("recomPersonId"))) {

            appendMap.put("DEVELOP_STAFF_ID", msg.get("recomPersonId"));// 发展人
        }
        if (StringUtils.isNotEmpty((String) msg.get("recomDepartId"))) {

            appendMap.put("DEVELOP_DEPART_ID", msg.get("recomDepartId"));// 发展人渠道
        }
        // 商城订单号放入ECS_ORDER_ID
        if ("masb".equals(msg.get("appCode"))) {
            appendMap.put("ECS_ORDER_ID", msg.get("ordersId"));
        }
        if (null != useCustInfo && !useCustInfo.isEmpty()) {// 责任人使用人属性下发
            String useCustName = (String) useCustInfo.get(0).get("useCustName");
            String useCustPsptType = CertTypeChangeUtils.certTypeMall2Cbss((String) useCustInfo.get(0).get(
                    "useCustPsptType"));
            String useCustPsptCode = (String) useCustInfo.get(0).get("useCustPsptCode");
            String useCustPsptAddress = (String) useCustInfo.get(0).get("useCustPsptAddress");
            String itmPrdGroupType = (String) useCustInfo.get(0).get("itmPrdGroupType");
            String itmPrdRespobsible = (String) useCustInfo.get(0).get("itmPrdRespobsible");
            String useCustMark = (String) useCustInfo.get(0).get("useCustMark");
            appendMap.put("USE_CUST_NAME", useCustName);
            appendMap.put("USE_CUST_PSPT_TYPE", useCustPsptType);
            appendMap.put("USE_CUST_PSPT_CODE", useCustPsptCode);
            appendMap.put("USE_CUST_ADDRESS", useCustPsptAddress);
            if (!StringUtils.isEmpty(itmPrdRespobsible) && "1".equals(itmPrdRespobsible)) {// 责任人时，该标识是1，使用人时不传该字段
                appendMap.put("ITM_PRD_RESPONSIBLE", itmPrdRespobsible);
                appendMap.put("ITM_PRD_GROUP_TYPE", itmPrdGroupType);
            }
            // 针对使用人打标字段进行处理，by maly 171107
            if (!IsEmptyUtils.isEmpty(useCustMark)) {
                Map item = new HashMap();
                item.put("attrTypeCode", "0");
                item.put("xDatatype", "NULL");
                item.put("itemId", msg.get("userId"));
                item.put("attrValue", useCustMark);
                item.put("attrCode", "USE_CUST_MARK");
                subItemList.add(item);
            }
        }
        String discountSell = (String) msg.get("discountSell");
        if ("mnsb".equals(msg.get("appCode")) && StringUtils.isNotEmpty(discountSell)) {
            // attrTypeCode为0 使用userId
            Map item = new HashMap();
            item.put("attrTypeCode", "3");
            item.put("xDatatype", "NULL");
            item.put("itemId", msg.get("userId"));
            item.put("attrValue", msg.get("discountSell"));
            item.put("attrCode", "DISCNT_DISCOUNT_SELL");
            subItemList.add(item);
        }
        List<Map> appendMapResult = dao.queryAppendParam(appParam);// :成卡走号卡中心库里新增字段
        for (int n = 0; n < appendMapResult.size(); n++) {
            String attrTypeCode = String.valueOf(appendMapResult.get(n).get("ATTR_TYPE_CODE"));
            String attrValue = String.valueOf(appendMapResult.get(n).get("ATTR_VALUE"));
            String attrcode = String.valueOf(appendMapResult.get(n).get("ATTR_CODE"));
            for (String key : appendMap.keySet()) {
                if (attrValue.endsWith(key) && "I".equals(attrTypeCode)) {
                    Map item = new HashMap();
                    item.put("xDatatype", "NULL");
                    item.put("attrValue", appendMap.get(key));
                    item.put("attrCode", attrcode);
                    itemList.add(item);
                }
                if (attrValue.endsWith(key) && "0".equals(attrTypeCode)) {// attrTypeCode为0 使用userId

                    Map item = new HashMap();
                    item.put("attrTypeCode", attrTypeCode);
                    item.put("xDatatype", "NULL");
                    item.put("itemId", msg.get("userId"));
                    item.put("attrValue", appendMap.get(key));
                    item.put("attrCode", attrcode);
                    subItemList.add(item);
                }
            }
        }
        // 判断是否有终端
        boolean haveTerminal = false;
        Map userInfo = ((List<Map>) msg.get("userInfo")).get(0);
        List<Map> activityInfo = (List<Map>) userInfo.get("activityInfo");
        if (!IsEmptyUtils.isEmpty(activityInfo)) {
            if (!IsEmptyUtils.isEmpty(activityInfo.get(0).get("resourcesCode"))) {
                haveTerminal = true;
            }
        }
        boolean havePurchaseFeeType = false;
        if (!IsEmptyUtils.isEmpty(subItemList)) {
            for (Map item : subItemList) {
                if ("PURCHASE_FEE_TYPE".equals(item.get("attrCode"))) {
                    havePurchaseFeeType = true;
                    break;
                }
            }
        }
        if (haveTerminal && "2".equals(userInfo.get("bipType")) && !havePurchaseFeeType
                && "0".equals(msg.get("deductionTag"))) {
            // 有终端，是合约开户、未拼接PURCHASE_FEE_TYPE，且不收款时，手动拼装
            Map item = new HashMap();
            item.put("attrTypeCode", "6");
            item.put("xDatatype", "NULL");
            item.put("itemId", msg.get("userId"));
            item.put("attrValue", "1");
            item.put("attrCode", "PURCHASE_FEE_TYPE");
            subItemList.add(item);
        }

        // 新增openCreditvalue字段用于CB落地用户信用度
        if (StringUtils.isNotEmpty((String) msg.get("openCreditvalue"))) {
            Map item = new HashMap();
            item.put("attrTypeCode", "0");
            item.put("xDatatype", "NULL");
            item.put("itemId", msg.get("userId"));
            item.put("attrValue", msg.get("openCreditvalue"));
            item.put("attrCode", "OPEN_CREDITVALUE");
            subItemList.add(item);
        }

        msg.put("subItemList", subItemList);
        msg.put("itemList", itemList);
        msg.put("tradeFeeItemList", tradeFeeItemList);
        DiscountSaleUtils.preDiscountFeeForSubItem(msg, acceptDate);// 代理商折扣销售 by wangmc 20170110

    }

    private String getNumberSrc(String provinceCode, String serialNumber) {
        // 1 -> 176号段为 总部号码
        if (serialNumber.startsWith("175") || serialNumber.startsWith("176") || serialNumber.startsWith("185")) {
            return "1";
        }
        // 如果是北六省份号码 NUMBER_SRC 为3
        if ("0011|0076|0017|0018|0097|0091".contains(provinceCode)) {
            return "3";
        }
        return "2";
    }

    private void preAcct(Map msg) {
        String cityCode = (String) msg.get("district");
        if (null != cityCode && cityCode.length() > 6) {
            cityCode = cityCode.substring(0, 6);
        }
        msg.put("district", cityCode);
        Map tradeAcct = new HashMap();
        List<Map> tradeAcctList = new ArrayList<Map>();
        tradeAcct.put("xDatatype", "NULL");
        tradeAcct.put("acctId", msg.get("acctId"));
        tradeAcct.put("eparchyCode", msg.get("eparchyCode"));
        tradeAcct.put("cityCode", cityCode);
        tradeAcct.put("custId", msg.get("custId"));
        tradeAcct.put("payName", msg.get("custName"));
        tradeAcct.put("payModeCode", getPayModeCode(msg));
        tradeAcct.put("scoreValue", "0");
        tradeAcct.put("creditClassId", "0");
        tradeAcct.put("basicCreditValue", "0");
        tradeAcct.put("creditValue", "0");
        tradeAcct.put("creditControlId", "0");
        tradeAcct.put("removeTag", "0");
        tradeAcct.put("debutyUserId", msg.get("userId"));
        tradeAcct.put("debutyCode", msg.get("serialNumber"));
        // item.put("removeDate", GetDateUtils.getDate());
        tradeAcct.put("acctPasswd", "");
        tradeAcct.put("contractNo", "0");
        tradeAcct.put("openDate", GetDateUtils.getDate());
        tradeAcctList.add(tradeAcct);
        msg.put("tradeAcctList", tradeAcctList);

    }

    private void preGuarator(Map msg, List<Map> userInfo) {
        Map userInfoMap = userInfo.get(0); // RGD2018080700047 担保人9项信息
        if (IsEmptyUtils.isEmpty(userInfoMap.get("assureIdType")) || IsEmptyUtils.isEmpty(userInfoMap.get("assureId"))) {
            return;
        }
        List<Map> assureList = new ArrayList<Map>();
        Map assureItem = new HashMap();
        assureItem.put("userId", msg.get("userId"));
        assureItem.put("assureIdType", userInfoMap.get("assureIdType"));
        assureItem.put("assureId", userInfoMap.get("assureId"));
        assureItem.put("assureTypeCode", userInfoMap.get("assureTypeCode"));
        assureItem.put("assureDate", userInfoMap.get("assureDate"));
        assureItem.put("assureCustId", userInfoMap.get("assureCustId"));
        assureItem.put("assureNo", userInfoMap.get("assureNo"));
        assureItem.put("assurePsptTypeCode",
                CertTypeChangeUtils.certTypeMall2Cbss((String) userInfoMap.get("assurepsptTypeCode")));
        assureItem.put("assurePsptId", userInfoMap.get("assurepsptId"));
        assureItem.put("assureName", userInfoMap.get("assureName"));
        assureItem.put("xDatatype", "NULL");
        assureList.add(assureItem);
        msg.put("tradeAssure", assureList);
    }

    /*private String decodeGurarntorType(String type) {
     * // 0 预存担保
     * if ("01".equals(type))
     * return "1"; // 1 担保人
     * if ("02".equals(type))
     * return "2"; // 2 担保金
     * if ("03".equals(type))
     * return "3"; // 3 零信用度
     * if ("04".equals(type))
     * return "4"; // 4 无担保
     * if ("05".equals(type))
     * return "5"; // 5 社保卡担保
     * if ("06".equals(type))
     * return "6"; // 6 单位担保
     * if ("07".equals(type))
     * return "7"; // 7 银行信贷模式担保
     * if ("08".equals(type))
     * return "8"; // 8 银行/保险公司担保
     * if ("09".equals(type))
     * return "9"; // 9 在网电话担保
     * if ("10".equals(type))
     * return "A"; // A 内部职工担保
     * if ("11".equals(type))
     * return "B"; // B 政府担保
     * if ("12".equals(type))
     * return "C"; // C 信用卡担保
     * // D 银行存单担保
     * // E 在网固网电话担保
     * // K 宽带预存担保
     * // W 企业固网担保
     * return "0";
     * } */

    /**
     * 根据商城传入的带宽选 择速率
     * @param productInfoResult
     * @param speed
     * @return
     */
    private List<Map> chooseSpeed(List<Map> productInfoResult, String speed) {
        String LTE = "100";
        String plus4G = "300";

        // 默认下发42M速率，支持传100M和300M两种速率 2016-4-14 lixl
        // 50103(LTE100M) 50105(42M) 50107(4G+ 300M)
        String kick1st = "50103";
        String kick2nd = "50107";
        if (LTE.equals(speed)) {
            kick1st = "50105";
            kick2nd = "50107";
        }
        else if (plus4G.equals(speed)) {
            kick1st = "50103";
            kick2nd = "50105";
        }

        List<Map> newProductInfo = new ArrayList<Map>();
        for (int m = 0; m < productInfoResult.size(); m++) {
            if (kick1st.equals(String.valueOf(productInfoResult.get(m).get("ELEMENT_ID")))
                    || kick2nd.equals(String.valueOf(productInfoResult.get(m).get("ELEMENT_ID")))) {
                newProductInfo.add(productInfoResult.get(m));
            }
        }
        productInfoResult.removeAll(newProductInfo);
        return productInfoResult;
    }

    /**
     * 资源类型
     * 输出到北六编码 描述
     * 01 3G手机终端
     * 02 2G手机终端
     * 03 固网话终端（有串号）
     * 04 宽带终端（有串号）
     * 05 上网终端(卡)
     * 06 上网终端(本)
     * 07 其它
     * 08 固话终端（无串号）
     * 09 宽带终端（无串号）
     * 13 互联网增值终端(无串码)
     * 14 互联网增值终端
     * 开户申请传入终端类别编码：
     * Iphone：IP
     * 乐phone：LP
     * 智能终端：PP
     * 普通定制终端：01
     * 上网卡：04
     * 上网本：05
     * @param terminalType
     * @return
     */
    private String decodeTerminalType(String terminalType) {
        if ("04".equals(terminalType))
            return "05";
        if ("05".equals(terminalType))
            return "06";
        return "01";
    }

    private Map preData4Base(Map msg, String appCode) {
        Map base = new HashMap();
        String inModeCode = "";
        // 区县不为空且大于6位时截取。
        String cityCode = (String) msg.get("district");
        if (null != cityCode && cityCode.length() > 6) {
            cityCode = cityCode.substring(0, 6);
        }
        if (modecodes.contains(appCode)) {
            inModeCode = "X";
        }
        else {
            inModeCode = "E";
        }
        if ((Boolean) msg.get("newRetailFlag")) {// 20180615针对新零售修改inModeCode和E_IN_MODE为N
            inModeCode = "N";
        }
        base.put("tradeStaffId", msg.get("operatorId"));
        base.put("tradeDepartId", msg.get("channelId"));
        base.put("subscribeId", msg.get("tradeId"));
        base.put("tradeId", msg.get("tradeId"));
        base.put("acceptDate", GetDateUtils.getDate());
        base.put("startDate", GetDateUtils.getDate());
        base.put("endDate", "20501231122359");
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        base.put("inModeCode", inModeCode);
        base.put("tradeTypeCode", "0".equals(msg.get("numPort")) ? "0592" : "0010");// 20180932-CBSS提供携号转网需求//新的携号转网没说改，就不改了
        // base.put("tradeTypeCode", "0010");
        base.put("productId", msg.get("mProductId"));
        base.put("brandCode", msg.get("mBrandCode"));
        base.put("userId", msg.get("userId"));
        base.put("custId", msg.get("custId"));
        base.put("usecustId", msg.get("custId"));
        base.put("acctId", msg.get("acctId"));
        base.put("userDiffCode", "00");
        base.put("netTypeCode", "50");
        base.put("serinalNamber", msg.get("serialNumber"));
        base.put("custName", msg.get("custName"));
        base.put("termIp", "0.0.0.0");
        base.put("eparchyCode", msg.get("eparchyCode"));
        base.put("cityCode", cityCode);
        base.put("execTime", GetDateUtils.getDate());
        base.put("operFee", "0");
        base.put("foregift", "0");
        base.put("advancePay", "0");
        base.put("feeStaffId", "");
        base.put("cancelTag", "0");
        base.put("checktypeCode", "0");
        List<Map> actorInfos = (List<Map>) msg.get("actorInfo");
        if (!IsEmptyUtils.isEmpty(actorInfos)) {
            Map actorInfoMap = actorInfos.get(0);
            base.put("actorName", actorInfoMap.get("actorName"));
            String actorCertTypeId = (String) actorInfoMap.get("actorCertTypeId");
            if (IsEmptyUtils.isEmpty(actorCertTypeId)) {
                throw new EcAopServerBizException("9999", "代办人证件类型不能为空！");
            }
            base.put("actorCertTypeId", CertTypeChangeUtils.certTypeMall2Cbss(actorCertTypeId));
            base.put("actorPhone", actorInfoMap.get("actorPhone"));
            base.put("actorCertNum", actorInfoMap.get("actorCertNum"));
        }
        else {
            base.put("actorName", "");
            base.put("actorCertTypeId", "");
            base.put("actorPhone", "");
            base.put("actorCertNum", "");
        }

        base.put("remark", msg.get("baseRemark"));
        base.put("feeState", "0");
        base.put("chkTag", "0");
        base.put("contact", msg.get("contactPerson"));
        base.put("contactPhone", msg.get("contactPhone"));
        base.put("contactAddress", msg.get("contactAddress"));
        // 王者会员改造1 start
        base.put("auditTag", msg.get("kingVip"));// 此处下发一位
        // 王者会员改造1 end
        List<Map> simCardNoList = (List<Map>) msg.get("simCardNo");
        // 成卡 存1 白卡存2
        if (null == simCardNoList || simCardNoList.isEmpty()) {

            base.put("IS_REMOTE", "2");
        }
        else {
            base.put("IS_REMOTE", "1");
        }

        return base;
    }

    private Map getAcctInfo(Map msg, Exchange exchange, Map tradeMap) throws Exception {
        boolean isString = msg.get("acctInfo") instanceof String;
        List<Map> acctInfoList = new ArrayList<Map>();
        if (isString) {
            acctInfoList = (List<Map>) JSON.parseObject((String) msg.get("acctInfo"));
        }
        else {
            acctInfoList = (List<Map>) msg.get("acctInfo");
        }
        // List<Map> acctInfoList = (ArrayList<Map>) msg.get("acctInfo");
        Map retAcct = Maps.newHashMap();
        retAcct.put("createOrExtendsAcct", "0");
        if (null == acctInfoList || 0 == acctInfoList.size()) {
            retAcct.put("acctId", GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, tradeMap), "seq_acct_id"));
            return retAcct;
        }
        Map acctInfo = acctInfoList.get(0);
        Object createOrExtendsAcct = acctInfo.get("createOrExtendsAcct");
        if ("0".equals(createOrExtendsAcct)) {
            retAcct.put("acctId", GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, tradeMap), "seq_acct_id"));
            retAcct.put("accountPayType", acctInfo.get("accountPayType"));
            return retAcct;
        }
        String acctid = (String) acctInfo.get("accId");
        if (StringUtils.isNotEmpty(acctid)) {
            retAcct.put("acctId", acctid);
        }
        else {
            Object debutySn = acctInfo.get("debutySn");
            if (null == debutySn || "".equals(debutySn)) {
                throw new EcAopServerBizException("9999", "选择继承老账户,请输入合账号码");
            }
            tradeMap.put("getMode", "101001101010001001");
            tradeMap.put("serialNumber", debutySn);
            tradeMap.put("tradeTypeCode", "9999");
            Map retMap = new GetThreePartInfoUtils().process(ExchangeUtils.ofCopy(exchange, tradeMap));
            retAcct.put("acctId", ((List<Map>) retMap.get("acctInfo")).get(0).get("acctId"));
        }

        retAcct.put("createOrExtendsAcct", "1");
        retAcct.put("accountPayType", acctInfo.get("accountPayType"));
        return retAcct;
    }

    /**
     * 调用CBSS客户资料校验接口查询客户信息
     * @param newCustInfoList
     * @param exchange
     * @param msg
     * @return
     */
    private Map getCustInfo4CB(List<Map> newCustInfoList, Exchange exchange, Map msg, String custType) {
        Map custInfo = new HashMap();
        Map custInfoMap = new HashMap();
        String certType = (String) newCustInfoList.get(0).get("certType");
        // 当证件类型为集团时,operateType传13,与客户资料校验保持一致
        if ("07|13|14|15|17|18|21|33".contains(certType)) {
            custInfo.put("operateType", "13");
        }
        else {
            custInfo.put("operateType", "1");
        }
        certType = CertTypeChangeUtils.certTypeMall2Cbss(certType);
        custInfo.put("certCode", newCustInfoList.get(0).get("certNum"));
        custInfo.put("certTypeCode", certType);
        if ("0".equals(custType)) {// 测试环境cb返回报文头有问题（报文头返回编码非0000和9999），生产看情况上
            custInfoMap.put("custType", custType);
            custInfoMap.putAll(custInfo);
            return custInfoMap;

        }
        MapUtils.arrayPut(custInfo, msg, MagicNumber.COPYARRAY);
        Exchange exchange4CustInfo = ExchangeUtils.ofCopy(exchange, custInfo);
        try {
            lan.preData("ecaop.cust.cbss.check.ParametersMapping", exchange4CustInfo);
            CallEngine.wsCall(exchange4CustInfo, "ecaop.comm.conf.url.cbss.services.CustSer");
            lan.xml2JsonNoError("ecaop.cust.cbss.check.template", exchange4CustInfo);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用CBSS客户资料检验失败！" + e.getMessage());
        }
        Map retCustInfo = exchange4CustInfo.getOut().getBody(Map.class);
        List<Map> custInfoList = (List<Map>) retCustInfo.get("custInfo");
        // 客户资料黑名单校验
        String chkBlcTag = (String) msg.get("chkBlcTag");
        boolean isCheckRealName = "18".equals(msg.get("province"));
        if (!"1".equals(chkBlcTag) || null == chkBlcTag) {
            for (Map retcustinfo : custInfoList) {
                String partyIsBwl = (String) retcustinfo.get("partyIsBwl");
                if ("1".equals(partyIsBwl)) {
                    throw new EcAopServerBizException("0203", "黑名单用户！");
                }
            }
        }
        if ((null == custInfoList || custInfoList.isEmpty()) && "1".equals(custType)) {
            throw new EcAopServerBizException("9999", "客户资料校验未返回信息");
        }
        // 集团客户--客户资料校验报错，抛出异常。 个人客户如果返回报错则创建新客户。
        if (!"0000".equals(retCustInfo.get("code")) && "13".equals(custInfo.get("operateType"))) {
            throw new EcAopServerBizException("9999", "集团客户--客户资料校验返回报错：" + retCustInfo.get("detail"));
        }
        if ("0000".equals(retCustInfo.get("code")) && custInfoList.size() > 0) {
            custType = "1";
            custInfoMap = custInfoList.get(0);
        }
        Map oldCustInfo = null;
        for (Map cust : custInfoList) {
            if (IsEmptyUtils.isEmpty(msg.get("custId"))) {
                if (isCheckRealName && ("1".equals(cust.get("rsrvTag1")) || "2".equals(cust.get("rsrvTag1")))) {
                    continue;
                }
                oldCustInfo = cust;
            }
            else {
                if (cust.get("custId").equals(msg.get("custId"))) {
                    if (isCheckRealName && ("1".equals(cust.get("rsrvTag1")) || "2".equals(cust.get("rsrvTag1")))) {
                        throw new EcAopServerBizException("9999", "接口下发的客户标识不可用,未进行实名");
                    }
                    oldCustInfo = cust;
                }
            }
        }
        if ((null == msg.get("custId") || "".equals(msg.get("custId"))) && null == oldCustInfo) {
            custType = "0";
        }
        if (!IsEmptyUtils.isEmpty(msg.get("custId")) && null == oldCustInfo) {
            throw new EcAopServerBizException("9999", "接口下发的客户标识不合法,请检查");
        }
        if (null == oldCustInfo && "1".equals(custType) && isCheckRealName) {
            throw new EcAopServerBizException("9999", "接口下发客户标识不合法或未实名");
        }
        custInfoMap.putAll(custInfo);
        if (!IsEmptyUtils.isEmpty(oldCustInfo)) {
            custInfoMap.putAll(oldCustInfo);
        }
        custInfoMap.put("custType", custType);
        return custInfoMap;
    }

    /**
     * 调号卡中心号码预占接口
     * 广东自动化测试会调用两遍预提交 广东第一次调用AOP开户处理申请接口时，para节点下paraId传“isOccupy”，paraValue传“1”（必传）
     * 如果广东传了isOccupy为1，AOP则不会去调用号卡做预占
     **/
    private void callNumCenterPreemtedNum(Exchange exchange, String newApptx) {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        if ("07".equals(msg.get("netType"))) {
            return;
        }
        if ("1".equals(getFromPara(msg, "isOccupy"))) {
            return;
        }
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
        nc.dealReturnHead(exchange);
        dealReturnPreemptedNum(exchange, msg, newApptx);
    }

    private void dealReturnPreemptedNum(Exchange exchange, Map msg, String newApptx) {
        Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
        Map bodyMap = (Map) outMap.get("UNI_BSS_BODY");
        Map resultMap = (Map) bodyMap.get("PREEMPTED_NUM_RSP");
        Map headMap = (Map) outMap.get("UNI_BSS_HEAD");
        if (IsEmptyUtils.isEmpty(bodyMap) || IsEmptyUtils.isEmpty(resultMap)) {
            throw new EcAopServerBizException("9999", "号码状态查询接口号卡中心返回结果为空");
        }
        if ("0000".equals(resultMap.get("RESP_CODE"))) {
            try {
                msg.put("numState", "2");
                msg.put("sysCode", exchange.getAppCode());
                nc.InsertTradeInfo(msg);
            }
            catch (Exception e) {
                return;
            }
        }
        else {
            throw new EcAopServerBizException("9999", "号卡中心返回其他错误," + resultMap.get("RESP_DESC") + "");
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
        if ("masb".equals(exchange.getAppCode())) {
            requestTempBody.put("ORDER_CODE", msg.get("ordersId"));
        }

        Map resultMap = new HashMap();
        resultMap.put("PREEMPTED_NUM_REQ", requestTempBody);
        requestBody.put("UNI_BSS_BODY", resultMap);
        // requestBody.put("UNI_BSS_HEAD", requestBody);
        exchange.getIn().setBody(requestBody);

    }

    /**
     * 处理esim成卡开户需求,将外围传入的esim成卡信息下发的trade_sub_item和trade_item节点中 by wangmc 20171201
     * @param msg
     * @param ext
     */
    private void preEsimCardInfo(Map msg, Map ext) {
        if ("TS-4G-008".equals(msg.get("esimBizkey"))) {
            // 卡类型：1：eSIM成卡，2：eSIM白卡
            String cardType = IsEmptyUtils.isEmpty(msg.get("cardType")) ? "1" : (String) msg.get("cardType");

            if (IsEmptyUtils.isEmpty(msg.get("simCardData"))) {
                throw new EcAopServerBizException("9999", "esim卡开户,卡数据信息节点[simCardData]必传!");
            }
            // 将获取到esim卡信息放入到tradeSubitem节点中
            List<Map> tradeSubItemList = new ArrayList<Map>();
            Map tradeSubItem = (Map) ext.get("tradeSubItem");
            if (!IsEmptyUtils.isEmpty(tradeSubItem) && !IsEmptyUtils.isEmpty(tradeSubItem.get("item"))) {
                tradeSubItemList = (List<Map>) tradeSubItem.get("item");
            }
            LanUtils lan = new LanUtils();
            String itemId = (String) msg.get("userId");
            // 默认attrTypeCode为0的方法
            tradeSubItemList.add(lan.createTradeSubItem("CARD_TYPE", cardType, itemId));

            String[] cardDataKey = new String[] { "eid", "imei", "terminalType" };// , "ki" };
            String[] subItemKey = new String[] { "EID", "IMEI", "TERMINAL_TYPE" };// , "NEW_CARD_DATA" };
            Map simCardData = ((List<Map>) msg.get("simCardData")).get(0);
            for (int i = 0; i < cardDataKey.length; i++) {
                tradeSubItemList.add(lan.createTradeSubItem(subItemKey[i], simCardData.get(cardDataKey[i]), itemId));
            }
            ext.put("tradeSubItem", MapUtils.asMap("item", tradeSubItemList));
        }
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

    /**
     * 用于获取para中paraId对应的value值
     * 查找失败，返回""
     * @param msg
     * @param paraId
     * @return
     */
    // 王者会员改造2 start-end
    public String getFromPara(Map msg, String paraId) {
        if (paraId == null || paraId.length() == 0) {
            return "";
        }
        boolean p1 = (msg.get("para") instanceof List);
        List<Map> para = (List<Map>) msg.get("para");
        String value = "";
        if (para != null && para.size() > 0) {// 循环查找，没有则返回""
            for (Map temp : para) {
                String str1 = temp.get("paraId") + "";
                if (paraId.equals(str1)) {
                    value = temp.get("paraValue") + "";
                }
            }
        }
        return value;
    }

    /**
     * 将开通月数转为一位的，规范里是V1
     * @param kingVip
     * @return
     */
    // 王者会员改造3 start-end
    private String changeCodeForKingVip(String kingVip) {
        String month = "";
        if ("10".equals(kingVip)) {
            month = "A";
        }
        else if ("11".equals(kingVip)) {
            month = "B";
        }
        else if ("12".equals(kingVip)) {
            month = "C";
        }
        else {
            month = kingVip;
        }
        return month;
    }
}
