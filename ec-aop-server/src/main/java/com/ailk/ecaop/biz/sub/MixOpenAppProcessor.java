package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.log.elk.ELKAopLogger;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.res.QryChkTermN6Processor;
import com.ailk.ecaop.biz.user.NaturalPersonCallprocessor;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.DealCertTypeUtils;
import com.ailk.ecaop.common.utils.DealMixBroadProductUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.MixOpenAppThread;
import com.ailk.ecaop.common.utils.MixOpenUtils;
import com.ailk.ecaop.common.utils.NumCenterUtils;
import com.ailk.ecaop.common.utils.PreParamUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.dao.essbusiness.EssBusinessDao;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@EcRocTag("mixOpenProcessor")
@SuppressWarnings(value = { "unchecked", "rawtypes" })
public class MixOpenAppProcessor extends BaseAopProcessor implements ParamsAppliable {

    // static ExecutorService pool = Executors.newFixedThreadPool(12);
    //start==2017-2-14 支持成卡改造1 共4--加入"ecaop.trades.smoca.changedcardReq.ParametersMapping"，[4]改为[5]
    static Map SPEED_LEVEL_MAP = MapUtils.asMap("0", "100", "1", "42", "2", "21", "3", "300");
    private static final String[] PARAM_ARRAY = { "ecaop.masb.chph.gifa.ParametersMapping",
            "ecaop.trade.cbss.checkUserParametersMapping", "ecaop.param.mapping.sfck",
            "ecaop.trades.sccc.cancelPre.paramtersmapping", "ecaop.trades.smoca.changedcardReq.ParametersMapping",
            "ecaop.23To4.check.ParametersMapping", "ecaop.trades.sell.mob.jtcp.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[7];

    //end==2017-2-14 支持成卡改造1 共4
    @Override
    public void process(Exchange exchange) throws Exception {

        Map body = exchange.getIn().getBody(Map.class);

        Object apptx = exchange.getProperty(Exchange.APPTX);

        Map msg = (Map) body.get("msg");
        Object orgCity = msg.get("city");
        ChangeCodeUtils ChangeCodeUtils = new ChangeCodeUtils();
        msg.put("EPARCHY_CODE", ChangeCodeUtils.changeEparchyUnStatic(msg));
        msg.put("city", ChangeCodeUtils.changeEparchyUnStatic(msg));
        Object eModeCode;
        eModeCode = new ChangeCodeUtils().getInModeCode(exchange.getAppCode());

        List<Map> numberList = Lists.newArrayList();
        // 号卡中心标志,默认走原有流程
        String isNumCentre = "1";
        String numCentreState = null;
        String sysCode = exchange.getAppCode();
        String remark = new PreDataProcessor().getRemark(msg);
        // 客户资料校验
        Map mixTemplate = (Map) msg.get("mixTemplate");
        Map mixCustInfo = (Map) mixTemplate.get("mixCustInfo");
        Object firstMonthBill = getFirstMonthBill((List<Map>) mixTemplate.get("productInfo"));
        checkInitParam(mixCustInfo);
        // 用户类型userDiffCode
        String addType = checkaddType(mixTemplate);
        List<Map> broadBandTemplate = (List<Map>) msg.get("broadBandTemplate");
        if (null == broadBandTemplate) {
            broadBandTemplate = new ArrayList<Map>();
        }
        Object mainNumber = "";
        List phoneTemplate = (List) msg.get("phoneTemplate");
        if (null == phoneTemplate || 0 == phoneTemplate.size()) {
            phoneTemplate = new ArrayList<Map>();
        }
        int phoneTemplateLength = phoneTemplate.size();
        for (int i = 0; i < phoneTemplate.size(); i++) {
            Object mainNumberTag = ((Map) phoneTemplate.get(i)).get("mainNumberTag");
            if ("0".equals(mainNumberTag)) {
                mainNumber = ((Map) phoneTemplate.get(i)).get("serialNumber");
            }
        }
        if ("".equals(mainNumber)) {
            throw new EcAopServerBizException("9999", "未下发主号码信息");
        }
        // 判断固网成员和移网成员收入集团归集类型是否一致
        Object phonegroupId = ((Map) phoneTemplate.get(0)).get("groupId");
        if (!IsEmptyUtils.isEmpty(broadBandTemplate)) {
            Object broadBandgroupId = (broadBandTemplate.get(0)).get("groupId");
            if (!IsEmptyUtils.isEmpty(phonegroupId) && !IsEmptyUtils.isEmpty(broadBandgroupId)
                    && !phonegroupId.equals(broadBandgroupId)) {
                throw new EcAopServerBizException("9999", "固网成员与移网成员均收入集团归集时，集团ID应一致！");
            }
        }
        int callCount = phoneTemplate.size() + broadBandTemplate.size() + 1;
        ArrayList result = new ArrayList();
        Exchange[] exchangeList = new Exchange[callCount];
        // Future[] futures = new Future[callCount];
        // Callable[] callables = new Callable[callCount];

        // 生成tradeId和itemId
        Map tradeMap = new HashMap();
        MapUtils.arrayPut(tradeMap, msg, MagicNumber.COPYARRAY);
        tradeMap.put("city", orgCity);
        Exchange getTradeIdExchange = ExchangeUtils.ofCopy(exchange, tradeMap);
        Exchange getTradeIdForAcctExchange = ExchangeUtils.ofCopy(exchange, tradeMap);
        Exchange getItemIdExchange = ExchangeUtils.ofCopy(exchange, tradeMap);
        Map[] phoneRetArray = new Map[phoneTemplate.size()];
        Map[] broadRetArray = new Map[broadBandTemplate.size()];
        List<String> tradeIdList = GetSeqUtil.getSeqFromCb(pmp[0], getTradeIdExchange, "seq_trade_id", callCount);
        String itemId = (String) GetSeqUtil.getSeqFromCb(pmp[0], getItemIdExchange, "seq_item_id", 1).get(0);
        String itemIdForAcct = (String) GetSeqUtil.getSeqFromCb(pmp[0], getTradeIdForAcctExchange, "seq_trade_id", 1)
                .get(0);
        String userId = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, tradeMap),
                "seq_user_id", 1).get(0);
        String custId = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, tradeMap),
                "seq_cust_id", 1).get(0);
        // 方法中使用的是mixTemplate节点,之前传成了mixCustInfo节点,因此全部创建了新客户,现修复 by wangmc 20180306
        Map acctInfo = getAcctInfo(mixTemplate, exchange, tradeMap);
        Object acctId = acctInfo.get("acctId");
        Object isNewAcct = acctInfo.get("createOrExtendsAcct");
        String payRelationId = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, tradeMap),
                "SEQ_PAYRELA_ID", 1).get(0);
        String virtualNumber = msg.get("city") + "XN" + userId.substring(6, 16);
        String tradeId = tradeIdList.get(0);
        numberList.add(MapUtils.asMap("number", virtualNumber, "tradeId", tradeId));
        Map custCheckRetMap = new HashMap();
        if ("1".equals(mixCustInfo.get("custType"))) {
            custCheckRetMap.put("custId", mixCustInfo.get("custId"));
            custId = (String) mixCustInfo.get("custId");
        }
        else {
            custCheckRetMap.put("custId", custId);
        }
        custCheckRetMap.put("custName", mixCustInfo.get("customerName"));
        custCheckRetMap.put("custId", "1".equals(mixCustInfo.get("custType")) ? mixCustInfo.get("custId") : custId);

        List<Map> activityInfos = (List<Map>) mixTemplate.get("activityInfo");
        // 获取orderRemarks
        if (null != msg.get("orderRemarks")) {
            remark = (String) msg.get("orderRemarks");
        }
        else {
            remark = "";
        }
        // 按照要求先调用虚拟用户预提交
        Map mixPreSubReq = MapUtils.asMap("mixTemplate", msg.get("mixTemplate"), "custCheckRetMap", custCheckRetMap,
                "tradeId", tradeId, "itemId", itemId, "ordersId", tradeId, "operTypeCode", "0", "userId", userId,
                "serialNumber", virtualNumber, "custId", custId, "acctId", acctId, "serviceClassCode", "00CP",
                "activityInfo", activityInfos, "remark", remark, "isNewAcct", isNewAcct, "eModeCode", eModeCode,
                "addType", addType, "eparchyCode", msg.get("city"), "markingTag", msg.get("markingTag"), "tradeItem",
                msg.get("tradeItem"), "subItem", msg.get("subItem"));
        mixPreSubReq.put("phoneRecomInfo", ((Map) phoneTemplate.get(0)).get("phoneRecomInfo"));
        mixPreSubReq.put("phoneTemplate", phoneTemplate);
        mixPreSubReq.put("payRelationId", payRelationId);
        mixPreSubReq.put("aloneTcsCompIndex", callCount);
        mixPreSubReq.put("mainNumber", mainNumber);
        mixPreSubReq.put("para", msg.get("para"));
		mixPreSubReq.put("deductionTag", msg.get("deductionTag"));// 虚拟预提交周转金
		mixPreSubReq.put("smnoMethodCode", exchange.getMethodCode());// method传值进去
        if ("0".equals(acctInfo.get("createOrExtendsAcct"))) {
            creatAcctInfo(mixPreSubReq, exchange, itemIdForAcct);
        }
        MapUtils.arrayPut(mixPreSubReq, msg, MagicNumber.COPYARRAY);
        new PreDataProcessor().preMixData(mixPreSubReq, exchange.getAppCode());

        // RHQ2018082800066-增加“港澳台居民居住证”证件类型的需求 by wangmc 20180914
        dealCertTypeForGAT(mixPreSubReq, mixTemplate, "mixCustInfo");
        exchangeList[0] = ExchangeUtils.ofCopy(exchange, mixPreSubReq);
        Map retMap = Maps.newHashMap();

        try {
            new LanUtils().preData(pmp[3], exchangeList[0]);
            ELKAopLogger.logStr("虚拟用户预提交: appTx: " + apptx + "-----------------------------------");
            CallEngine.wsCall(exchangeList[0], "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
            new LanUtils().xml2Json("ecaop.trades.sccc.cancelPre.template", exchangeList[0]);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用虚拟用户预提交失败！" + e.getMessage());
        }
        Map mixPreSubOut = exchangeList[0].getOut().getBody(Map.class);
        List<Map> rspInfo = (List<Map>) mixPreSubOut.get("rspInfo");
        retMap.put("provOrderId", rspInfo.get(0).get("bssOrderId"));
        retMap.put("virtualCustId", userId);
        mixPreSubOut.put("code", "0000");
        result.add(mixPreSubOut);
        List<Map> numberInfo = new ArrayList<Map>();
        Map virtualNumberInfo = new HashMap();
        virtualNumberInfo.put("subOrderId", rspInfo.get(0).get("bssOrderId"));
        virtualNumberInfo.put("number", virtualNumber);
        virtualNumberInfo.put("orderId", rspInfo.get(0).get("bssOrderId"));
        numberInfo.add(virtualNumberInfo);
        try {
            Object mixVmproductId = "XNHM";
            // 移网订单调用预提交
            for (int i = 1; i < phoneTemplate.size() + 1; i++) {
                Object installMode = ((Map) phoneTemplate.get(i - 1)).get("installMode");
                msg.put("city", orgCity);
                Map phoneTemplateMap = (Map) phoneTemplate.get(i - 1);
                Map phoneCustInfo = (Map) phoneTemplateMap.get("phoneCustInfo");
                checkInitParam(phoneCustInfo);

                //调用自然人接口校验客户数量
               // phoneCustInfo.put("province", msg.get("province"));
               // getUserNumInfo(exchange, phoneCustInfo);

                Object speedLevel = SPEED_LEVEL_MAP.get(phoneTemplateMap.get("phoneSpeedLevel"));
                Object speed = null == speedLevel ? "42" : speedLevel;
                List<Map> activityInfo = (List<Map>) phoneTemplateMap.get("activityInfo");
                // 处理活动信息
                if (null != activityInfo && 0 != activityInfo.size()) {
                    List<Map> actList = new ArrayList<Map>();
                    for (Map act : activityInfo) {
                        if (!act.isEmpty() && null != act.get("resourcesCode")) {
                            act.put("resourcesType", "01");
                            actList.add(act);
                        }
                    }
                    if (0 != actList.size()) {
                        Map userInfo = MapUtils.asMap("activityInfo", actList);
                        List<Map> userInfoList = Lists.newArrayList();
                        userInfoList.add(userInfo);
                        Map qryTermMap = MapUtils.asMap("userInfo", userInfoList);
                        MapUtils.arrayPut(qryTermMap, msg, MagicNumber.COPYARRAY);
                        Exchange qryTermExchange = ExchangeUtils.ofCopy(exchange, qryTermMap);
                        new QryChkTermN6Processor().process(qryTermExchange);
                        phoneTemplateMap.put("resourcesInfo",
                                qryTermExchange.getOut().getBody(Map.class).get("resourcesRsp"));
                    }
                }
                Object serialNumber = ((Map) phoneTemplate.get(i - 1)).get("serialNumber");
                String serType = ((Map) phoneTemplate.get(i - 1)).get("serType") + "";
                // 获取卡类型.cuij
                Object card_Type = ((Map) phoneTemplate.get(i - 1)).get("card_type");
                Object cardType = null == card_Type ? "0" : card_Type;
                if ("0".equals(installMode)) {// 新装
               /*     // 判断是否调用号卡中心
                    isNumCentre = numberCentre(serialNumber, serType, msg);
                    if ("0".equals(isNumCentre)) {*/
                        // 调用号卡中心号码状态查询接口
                        numCentreState = qryNumState(exchange, msg, serialNumber);
                   // }
                    Object simCardNo = phoneTemplateMap.get("simCardNo");
                    if ("smno".equals(exchange.getMethodCode()) && null == phoneTemplateMap.get("simCardNo")) {
                        throw new EcAopServerBizException("9999", "写卡前置业务,未下发卡信息节点");
                    }
                    // CHANGE_1 北分沃易售写卡前置接口,处理移网的REL_COMP_PROD_ID字段,需要下发虚拟产品 by wangmc 20180630
                    if ("smno".equals(exchange.getMethodCode()) && "mabc|bjaop".contains(exchange.getAppCode())) {
                        mixVmproductId = ((Map) mixPreSubReq.get("base")).get("productId");
                        mixVmproductId = IsEmptyUtils.isEmpty(mixVmproductId) ? "XNHM" : mixVmproductId;
                    }
                    Map phone = MapUtils.asMap("ordersId", msg.get("ordersId"), "phoneTemplate", phoneTemplateMap,
                            "mixTemplate", msg.get("mixTemplate"), "para", msg.get("para"), "subscribeId", tradeId,
                            "aloneTcsCompIndex", callCount - i, "mixVmproductId", mixVmproductId, "speed", speed, "custName",
                            phoneCustInfo.get("customerName"), "remark", remark, "isNewAcct", isNewAcct, "simCardNo",
                            simCardNo, "METHODCODE", exchange.getMethodCode(), "eModeCode", eModeCode, "isNumCentre",
                            isNumCentre, "markingTag", msg.get("markingTag"), "deductionTag", msg.get("deductionTag")
                            , "delayTag", msg.get("delayTag"), "isAfterActivation", msg.get("isAfterActivation"));
                    phone.put("custId", custId);
                    phone.put("acctId", acctId);
                    phone.put("cardType", cardType);

                    phone.put("apptx", apptx);

                    MapUtils.arrayPut(phone, msg, MagicNumber.COPYARRAY);
                    exchangeList[i] = ExchangeUtils.ofCopy(exchange, phone);
                    ELKAopLogger.logStr("移网新装: appTx: " + apptx + "-----------------------------------");
                    phoneRetArray[i - 1] = new PreDataProcessor().preMobileData(exchangeList[i], installMode, pmp);// 移网新装周转金
                    phoneRetArray[i - 1].put("serialNumber", serialNumber);
                    //start==2017-2-14 支持成卡改造之4 共4 加入IS_REMOTE为1成卡时的情况
                    //写卡前置接口需要区分出来白卡/成卡 1成卡 2白卡 这次改动只对写卡前置有影响
                    //phoneRetArray[i - 1].put("IS_REMOTE", "1".equals(msg.get("IS_REMOTE")) ? "1" : "2");
                    //end==2017-2-14 支持成卡改造4 共4
                    //phoneRetArray[i - 1].put("IS_REMOTE", "smno".equals(exchange.getMethodCode()) ? "1" : "2");
                    phoneRetArray[i - 1].put("remark", "50");
                    phoneRetArray[i - 1].put("numCentreState", numCentreState);// 新增号码中心号码状态
                    phoneRetArray[i - 1].put("installMode", "0");// 新增新装标志，调号码中心时要判断
                    phoneRetArray[i - 1].put("isNumCentre", isNumCentre);// 新增号码中心判断字段
                    phoneRetArray[i - 1].put("sysCode", sysCode);
                    ELKAopLogger.logStr("创建账户预提交: appTx: " + exchange.getProperty(Exchange.APPTX) + "-----------------------------------");
                    // 移网新装处理压单标识 by wangmc 20180612
                    dealDelayTag(phoneRetArray[i - 1], phoneTemplateMap, exchange.getMethodCode(), true);
                    // RHQ2018082800066-增加“港澳台居民居住证”证件类型的需求 by wangmc 20180914
                    dealCertTypeForGAT(phoneRetArray[i - 1], phoneTemplateMap, "phoneCustInfo");
                } else if ("1".equals(installMode)) {// 纳入
                    Map phone = MapUtils.asMap("ordersId", msg.get("ordersId"), "phoneTemplate", phoneTemplateMap,
                            "mixTemplate", msg.get("mixTemplate"), "speed", speed, "aloneTcsCompIndex", callCount - i,
                            "custName", phoneCustInfo.get("customerName"), "subscribeId", tradeId, "tradeId",
                            tradeIdList.get(i), "remark", remark, "acctId", acctId, "isNewAcct", isNewAcct,
                            "compDealState", "1", "eModeCode", eModeCode, "XNUserId", userId, "virtualNumber",
                            virtualNumber, "markingTag", msg.get("markingTag"), "deductionTag", msg.get("deductionTag"),
                            "apptx", apptx, "delayTag", msg.get("delayTag"), "isAfterActivation", msg.get("isAfterActivation"));
                    MapUtils.arrayPut(phone, msg, MagicNumber.COPYARRAY);
                    List<Map> newPhoneProduct = (List<Map>) (null == ((Map) phoneTemplate.get(i - 1))
                            .get("phoneProduct") ?
                            ((Map) phoneTemplate.get(i - 1)).get("phoneProduct") : mixTemplate.get("productInfo"));
                    Map preParam = new PreParamUtils().prePhoneTemplate(exchange, newPhoneProduct, phone);
                    phone.putAll(preParam);
                    exchangeList[i] = ExchangeUtils.ofCopy(exchange, phone);
                    ELKAopLogger.logStr("移网纳入: appTx: " + apptx + "-----------------------------------");
                    phoneRetArray[i - 1] = new PreDataProcessor().preMobileData(exchangeList[i], installMode, pmp);// 移网纳入周转金
                    phoneRetArray[i - 1].put("eparchyCode", orgCity);
                    phoneRetArray[i - 1].put("remark", "50");
                }
                else {// 迁转
                    Map phone = MapUtils.asMap("ordersId", msg.get("ordersId"), "phoneTemplate", phoneTemplateMap,
                            "mixTemplate", msg.get("mixTemplate"), "speed", speed, "subscribeId", tradeId, "tradeId",
                            tradeIdList.get(i), "aloneTcsCompIndex", callCount - i, "serClassCode",
                            phoneTemplateMap.get("serClassCode"), "custName", phoneCustInfo.get("customerName"),
                            "remark", remark, "acctId", acctId, "isNewAcct", isNewAcct, "eModeCode", eModeCode,
                            "XNUserId", userId, "virtualNumber", virtualNumber, "markingTag", msg.get("markingTag"),
                            "deductionTag", msg.get("deductionTag"), "delayTag", msg.get("delayTag"), "isAfterActivation", msg.get("isAfterActivation"));
                    MapUtils.arrayPut(phone, msg, MagicNumber.COPYARRAY);
                    List<Map> newPhoneProduct = (List<Map>) (null == ((Map) phoneTemplate.get(i - 1))
                            .get("phoneProduct") ?
                            ((Map) phoneTemplate.get(i - 1)).get("phoneProduct") : mixTemplate.get("productInfo"));
                    Map preParam = new PreParamUtils().prePhoneTemplate(exchange, newPhoneProduct, phone);
                    phone.putAll(preParam);
                    exchangeList[i] = ExchangeUtils.ofCopy(exchange, phone);
                    ELKAopLogger.logStr("移网迁转: appTx: " + apptx + "-----------------------------------");
                    phoneRetArray[i - 1] = new PreDataProcessor().preMobileData(exchangeList[i], installMode, pmp);// 移网迁转周转金
                    phoneRetArray[i - 1].put("eparchyCode", orgCity);
                    phoneRetArray[i - 1].put("remark", "50");
                }
                dealDelayTag(phoneRetArray[i - 1], phoneTemplateMap, exchange.getMethodCode(), false);
                // 河南融合腾讯王卡需求 by wangmc 20180918
                dealAuditTag(phoneRetArray[i - 1], phoneTemplateMap);
                ((Map) phoneRetArray[i - 1].get("ext")).remove("tradeAcct");
                numberList.add(MapUtils.asMap("number", serialNumber, "tradeId", phoneRetArray[i - 1].get("tradeId")));
                MapUtils.arrayPut(phoneRetArray[i - 1], msg, MagicNumber.COPYARRAY);
                exchangeList[i] = ExchangeUtils.ofCopy(exchange, phoneRetArray[i - 1]);
            }
            for (int i = 0; i < broadBandTemplate.size(); i++) {
                int offset = phoneTemplateLength + 1;
                Object installMode = broadBandTemplate.get(i).get("installMode");
                Object serialNumber = broadBandTemplate.get(i).get("acctSerialNumber");
                Map customer = (Map) broadBandTemplate.get(i).get("broadBandCustInfo");
                Object speedLevel = broadBandTemplate.get(i).get("speedLevel");
                String payRelationIdBroad = (String) GetSeqUtil.getSeqFromCb(pmp[0],
                        ExchangeUtils.ofCopy(exchange, tradeMap), "SEQ_PAYRELA_ID", 1).get(0);
                // String itemIdMixDiscnt = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, tradeMap),
                // "seq_item_id");
                String itemIdMixDiscnt = itemId;
                String gwQuickOpen = "";
                String communitId = "";
                String communitName = "";
                String broadBandType = (String) broadBandTemplate.get(i).get("broadBandType");
                if ((speedLevel == null || "".equals(speedLevel)) && "1".equals(broadBandType))
                {
                    speedLevel = "20";
                }
                if (!IsEmptyUtils.isEmpty(broadBandTemplate.get(i).get("gwQuickOpen"))) {
                    gwQuickOpen = (String) broadBandTemplate.get(i).get("gwQuickOpen");
                }
                if (!IsEmptyUtils.isEmpty(broadBandTemplate.get(i).get("communitId"))) {
                    communitId = (String) broadBandTemplate.get(i).get("communitId");
                }
                if (!IsEmptyUtils.isEmpty(broadBandTemplate.get(i).get("communitName"))) {
                    communitName = (String) broadBandTemplate.get(i).get("communitName");
                }

                checkInitParam(customer);
                Map broad = MapUtils.asMap("ordersId", msg.get("ordersId"), "broadBandTemplate",
                        broadBandTemplate.get(i), "mixTemplate", msg.get("mixTemplate"), "itemId", itemId, "tradeId",
                        tradeIdList.get(i + offset), "aloneTcsCompIndex", callCount - offset - i, "firstMonBillMode",
                        firstMonthBill, "subscribeId", tradeId, "payRelationId", payRelationIdBroad, "installMode",
                        installMode, "serialNumber", serialNumber, "custName", customer.get("customerName"), "custId",
                        customer.get("bandCustId"), "custType", customer.get("custType"), "certType",
                        customer.get("certType"), "certNum", customer.get("certNum"), "speedLevel", speedLevel,
                        "remark", remark, "itemIdMixDiscnt", itemIdMixDiscnt, "broadBandType", broadBandType,
                        "eparchyCode", ChangeCodeUtils.changeEparchyUnStatic(msg), "acctId", acctId, "isNewAcct",
                        isNewAcct, "eModeCode", eModeCode, "para", msg.get("para"), "markingTag",
                        msg.get("markingTag"), "METHODCODE", exchange.getMethodCode(), "deductionTag",
                        msg.get("deductionTag"), "gwQuickOpen", gwQuickOpen, "communitId", communitId, "communitName",
                        communitName, "receMessPhone", msg.get("receMessPhone"), "delayTag", msg.get("delayTag"),
                        "subItem", msg.get("subItem"));
                broad.put("tradeMap", tradeMap);
                // CHANGE_ 融合固网新装时,下发该接点在TRADE_SUB_ITEM节点中 by wangmc 20180717
                if ("smno".equals(exchange.getMethodCode()) && "mabc|bjaop".contains(exchange.getAppCode())) {
                    broad.put("mixVmproductId", mixVmproductId);
                }
                MapUtils.arrayPut(broad, msg, MagicNumber.COPYARRAY);
                ELKAopLogger.logStr("固网: appTx: " + apptx + "-----------------------------------");
				// 外围可以通过传入optType字段获取取消某个默认服务的下发标识 by zhaok Date 18/1/31
                getRemoveElement(broadBandTemplate.get(i), msg);
                broadRetArray[i] = new DealMixBroadProductUtils().preMixBroadProduct(broad, exchange, pmp);// 固网周转
                ((Map) broadRetArray[i].get("ext")).remove("tradeAcct");
                // 删除固网产品默认下发的资费和服务
                dealRemoveElementForBroadInfo((Map) broadRetArray[i].get("ext"), msg);
                MapUtils.arrayPut(broadRetArray[i], msg, MagicNumber.COPYARRAY);
                broadRetArray[i].put("tradeId", tradeIdList.get(i + offset));
                broadRetArray[i].put("eparchyCode", msg.get("city"));

                // 固网新装支持压单标识 by wangmc 20180612
                dealDelayTag(broadRetArray[i], broadBandTemplate.get(i), exchange.getMethodCode(), true);
                dealDelayTag(broadRetArray[i], broadBandTemplate.get(i), exchange.getMethodCode(), false);
                // RHQ2018082800066-增加“港澳台居民居住证”证件类型的需求 by wangmc 20180914
                dealCertTypeForGAT(broad, broadBandTemplate.get(i), "broadBandCustInfo");
                ELKAopLogger.logStr("固网属性: appTx: " + apptx + "-----------------------------------");
                // 处理ServiceAttr节点传进来的属性 by zhaok 20180809
                perServiceAttr(broadBandTemplate.get(i), (Map) broadRetArray[i].get("ext"));
                exchangeList[i + offset] = ExchangeUtils.ofCopy(exchange, broadRetArray[i]);
                numberList.add(MapUtils.asMap("number", serialNumber, "tradeId", broadRetArray[i].get("tradeId")));
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            new MixOpenUtils().dealException(exchange, e, numberInfo);
            return;
        }

        // 线程池下表从1开始,因虚拟用户预提交报文已经调用
        List<Map> memberList = new ArrayList<Map>();
        for (Map phone : phoneRetArray) {
            memberList.add(phone);
        }
        for (Map broad : broadRetArray) {
            memberList.add(broad);
        }
        try {
            for (int i = 1; i < callCount; i++) {
                Map tempbody = exchangeList[i].getIn().getBody(Map.class);
                Map ret = new MixOpenAppThread(exchangeList[i], numberList.get(i).get("number")).call();
                // 融合移网新装时调号码中心预占接口preemptedNum
                exchangeList[i].getIn().setBody(tempbody);
                dealPreemptedNum(exchangeList[i], ret);
                result.add(ret);
                TradeManagerUtils.insert2MixTrade(memberList.get(i - 1));
                Map inMap = new HashMap();
                inMap.put("province", msg.get("province"));
                inMap.put("subscribeId", numberList.get(i).get("tradeId"));
                inMap.put("sysCode", exchangeList[i].getAppCode());
                inMap.put("serialNumber", numberList.get(i).get("number"));
                // inMap.put("proKey", numId.get(0).get("proKey"));
                inMap.put("terminalId", numberList.get(i).get("resourcesCode"));
                inMap.put("methodCode", exchangeList[i].getMethodCode());
                new EssBusinessDao().insertResourceInfo(inMap);
                Map number = new HashMap();
                number.put("subOrderId", tradeIdList.get(i));
                number.put("number", numberList.get(i).get("number"));
                number.put("orderId", tradeId);
                numberInfo.add(number);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            new MixOpenUtils().dealException(exchange, e, numberInfo);
            return;
        }
        Message out = new DefaultMessage();
        Map feeInfo = dealCbssPreSubmitRet(numberList, result);
        retMap.put("numberInfo", feeInfo.get("retList"));
        retMap.put("totalFee", feeInfo.get("totalFee"));
        retMap.put("virtualCustId", custId);
        retMap.put("virtualUserId", userId);
        retMap.put("virtualAcctId", acctId);
        out.setBody(retMap);
        exchange.setOut(out);
    }

    private void perServiceAttr(Map broadBandTemplate, Map ext) {
        List<Map> productInfo = (List<Map>) broadBandTemplate.get("broadBandProduct");
        List<Map> tradeSubItemList = new ArrayList<Map>();

        if (ext.containsKey("tradeSubItem")) {
            Map tradeSubItem = (Map) ext.get("tradeSubItem");
            tradeSubItemList = (List<Map>) tradeSubItem.get("item");
        }
        // 根据元素类型获取到对应的节点
        Map dataKey = MapUtils.asMap("D", "tradeDiscnt", "S", "tradeSvc", "X", "tradeSp");
        Map codeKey = MapUtils.asMap("D", "discntCode", "S", "serviceId", "X", "spServiceId");
        for (Map product : productInfo) {
            List<Map> packageElement = (List<Map>) product.get("packageElement");
            if (IsEmptyUtils.isEmpty(packageElement)) {
                continue;
            }

            for (Map pacEle : packageElement) {
                // 若该元素存在属性节点,则获取该元素的itemId
                if (!IsEmptyUtils.isEmpty(pacEle.get("serviceAttr"))) {
                    Map tradeKeyData = (Map) ext.get(dataKey.get(pacEle.get("elementType")));
                    if (IsEmptyUtils.isEmpty(tradeKeyData) || IsEmptyUtils.isEmpty(tradeKeyData.get("item"))) {
                        continue;
                    }
                    String getCode = (String) codeKey.get(pacEle.get("elementType"));
                    List<Map> dataItem = (List<Map>) tradeKeyData.get("item");
                    for (Map item : dataItem) {
                        // 如果是该元素,则获取该元素的itemId拼装tradeSubItem节点
                        if (pacEle.get("elementId").equals(String.valueOf(item.get(getCode)))
                                && !IsEmptyUtils.isEmpty(item.get("itemId"))) {
                            Map dataMap = MapUtils.asMap("itemId", item.get("itemId"), "startDate",
                                    GetDateUtils.getDate(), "endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                            preTradeSubItemByServiceAttr((List<Map>) pacEle.get("serviceAttr"), tradeSubItemList,
                                    dataMap, (String) pacEle.get("elementType"));
                            break;
                        }
                    }
                }
            }
        }
        ext.put("tradeSubItem", MapUtils.asMap("item", tradeSubItemList));
    }

    /**
     * 拼装tradeSubItem节点
     *
     * @param serviceAttr
     * @param tradeSubItem
     * @param dataMap
     * @param elementType
     */
    private void preTradeSubItemByServiceAttr(List<Map> serviceAttr, List<Map> tradeSubItem, Map dataMap,
            String elementType) {
        LanUtils lan = new LanUtils();
        for (Map attr : serviceAttr) {
            if ("S".equalsIgnoreCase(elementType)) {
                tradeSubItem.add(lan.createTradeSubItemD((String) dataMap.get("itemId"), (String) attr.get("code"),
                        attr.get("value"), (String) dataMap.get("startDate"), (String) dataMap.get("endDate")));
            }
            else {
                tradeSubItem.add(lan.createTradeSubItemC((String) dataMap.get("itemId"), (String) attr.get("code"),
                        attr.get("value"), (String) dataMap.get("startDate"), (String) dataMap.get("endDate")));
            }
        }
    }

    /**
     * 删除固网产品默认下发的资费和服务
     * @param msg 
     * @param object
     */
    private void dealRemoveElementForBroadInfo(Map ext, Map msg) {
        if (IsEmptyUtils.isEmpty(msg.get("removeMap"))) {
            return;
        }
        Map tradeDiscnt = (Map) ext.get("tradeDiscnt");
        Map tradeSvc = (Map) ext.get("tradeSvc");
        removeElementInfo(tradeDiscnt, msg, "discntCode");
        removeElementInfo(tradeSvc, msg, "serviceId");
    }

    /**
     * 删除固网产品默认下发的资费和服务
     * @param dataInfo
     * @param msg
     * @param key 
     */
    private void removeElementInfo(Map dataInfo, Map msg, String key) {
        List<Map> dataInfoList = (List<Map>) dataInfo.get("item");
        if (!IsEmptyUtils.isEmpty(dataInfoList)) {
            for (int i = 0; i < dataInfoList.size(); i++) {
                String removeKey = "" + dataInfoList.get(i).get("productId") + dataInfoList.get(i).get("packageId")
                        + dataInfoList.get(i).get(key);
                if (!IsEmptyUtils.isEmpty(((Map) msg.get("removeMap")).get(removeKey))) {
                    dataInfoList.remove(i);
                }
            }
        }
    }

    /**
     * 外围可以通过传入optType字段获取取消某个默认服务的下发标识
     * @param broadBandTemplate
     * @param msg
     */
    private void getRemoveElement(Map broadBandTemplate, Map msg) {
        List<Map> productInfo = (List<Map>) broadBandTemplate.get("broadBandProduct");
        if (!IsEmptyUtils.isEmpty(productInfo)) {
            Map removeMap = new HashMap();
            for (Map product : productInfo) {
                if (!IsEmptyUtils.isEmpty(product.get("packageElement"))) {
                    List<Map> packageELement = new ArrayList<Map>();
                    if (product.get("packageElement") instanceof List) {
                        packageELement = (List<Map>) product.get("packageElement");
                    }
                    else if (product.get("packageElement") instanceof Map) {
                        packageELement.add((Map) product.get("packageElement"));
                    }
                    for (Map pacEle : packageELement) {
                        // 若包元素中有optType,且值为1,则该元素应不下发
                        if (!IsEmptyUtils.isEmpty(pacEle.get("optType"))
                                && "1".equals(pacEle.get("optType").toString())) {
                            removeMap.put(
                                    "" + product.get("productId") + pacEle.get("packageId") + pacEle.get("elementId"),
                                    "remove");
                        }
                    }
                }
            }
            // 将需要取消的资费放入msg中
            msg.put("removeMap", removeMap);
        }
    }

    /**
     * 自然人调用请求
     *
     * @param exchange,custMap
     */
    private void getUserNumInfo(Exchange exchange, Map custMap) throws Exception {
        Exchange tempExchange = ExchangeUtils.ofCopy(exchange, custMap);
        //自然人改造需求--start
        new NaturalPersonCallprocessor().process(tempExchange);
        String userAmount = tempExchange.getStrProperty("userAmount");
        if (!"00".equals(userAmount)) {
            if (Integer.parseInt(userAmount) > 4) {
                throw new EcAopServerBizException("9999", "同一证件" + custMap.get("certNum") + "下客户数量超过5个，不允许添加新客户");
            }
        }
    }

    /**
     * 号卡中心号码同步
     * 
     * @param exchange
     * @throws Exception
     */
    private void dealPreemptedNum(Exchange exchange, Map inMap) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        String appkey = exchange.getAppkey();
        String sysCode = new NumCenterUtils().changeSysCode(exchange);
        if ("0".equals(msg.get("installMode"))) {
            // 融合新装且号卡中心号码时
            Map preDataMap = new HashMap();
            MapUtils.arrayPut(preDataMap, msg, MagicNumber.COPYARRAY);
            Map preData4PreemptMap = preData4Preempt(preDataMap, (String) msg.get("serialNumber"), appkey, sysCode);
            Exchange exchangePreempt = ExchangeUtils.ofCopy(preData4PreemptMap, exchange);
            CallEngine.numCenterCall(exchangePreempt, "ecaop.comm.conf.url.numbercenter.preemptedNum");
            Map retMap = (Map) JSON.parse(exchangePreempt.getOut().getBody().toString());
            if (null == retMap || retMap.isEmpty()) {
                throw new EcAopServerBizException("9999", "号卡中心返回异常");
            }
            Map bodyMap = (Map) retMap.get("UNI_BSS_BODY");
            Map preemptedMap = (Map) bodyMap.get("PREEMPTED_NUM_RSP");
            String code = (String) preemptedMap.get("RESP_CODE");
            if (!"0000".equals(code)) {
                throw new EcAopServerBizException("9999", "号卡中心号码同步失败");
            }
            // 号卡中心号码入库
            List<Map> rspInfoList = (List<Map>) inMap.get("rspInfo");
            if (null == rspInfoList || rspInfoList.isEmpty()) {
                throw new EcAopServerBizException("9999", "预提交未返回信息");
            }
            String ordersId = (String) rspInfoList.get(0).get("provOrderId");
            msg.put("ordersId", ordersId);
            msg.put("numState", msg.get("numCentreState"));

        }
    }

    /**
     * 号卡中心号码预占准备数据
     * 
     * @param preDataMap
     * @param string
     * @throws Exception
     */
    private Map preData4Preempt(Map preDataMap, String serialNumber, String appkey, String sysCode) throws Exception {
        Map tempMap = new HashMap();
        tempMap.put("UNI_BSS_BODY", getBodyInfo4Preempt(preDataMap, serialNumber, sysCode));
        tempMap.putAll(NumFaceHeadHelper.creatHead(appkey));
        return tempMap;

    }

    private Map getBodyInfo4Preempt(Map preDataMap, String serialNumber, String sysCode) {
        Map bodyMap = new HashMap();
        List<Map> para = new ArrayList<Map>();// 暂时空
        Map qryNumReqMap = new HashMap();
        qryNumReqMap.put("STAFF_ID", preDataMap.get("operatorId"));
        qryNumReqMap.put("PROVINCE_CODE", preDataMap.get("province"));
        qryNumReqMap.put("CITY_CODE", preDataMap.get("city"));
        qryNumReqMap.put("DISTRICT_CODE", preDataMap.get("district"));
        qryNumReqMap.put("CHANNEL_TYPE", preDataMap.get("channelType"));
        qryNumReqMap.put("CHANNEL_ID", preDataMap.get("channelId"));
        qryNumReqMap.put("SYS_CODE", sysCode);
        qryNumReqMap.put("SERIAL_NUMBER", serialNumber);
        qryNumReqMap.put("PARA", para);
        bodyMap.put("PREEMPTED_NUM_REQ", qryNumReqMap);
        return bodyMap;
    }

    /**
     * 调用号码中心号码状态查询接口，判断号码是否可用
     * 
     * @param exchange
     * @param msg
     * @param serialNumber
     * @throws Exception
     */
    private String qryNumState(Exchange exchange, Map msg, Object serialNumber) throws Exception {
        Map qryNumMap = new HashMap();
        String appkey = exchange.getAppkey();
        String sysCode = new NumCenterUtils().changeSysCode(exchange);
        qryNumMap.put("sysCode", sysCode);
        MapUtils.arrayPut(qryNumMap, msg, MagicNumber.COPYARRAY);
        Map preDataMap = preData4QryNum(qryNumMap, serialNumber, appkey);// 准备参数
        Exchange qryNumExchange = ExchangeUtils.ofCopy(preDataMap, exchange);
        CallEngine.numCenterCall(qryNumExchange, "ecaop.comm.conf.url.numbercenter.qryNumInfo");
        return dealQryNumRet(qryNumExchange);

    }

    private String dealQryNumRet(Exchange qryNumExchange) {
        Map retMap = (Map) JSON.parse(qryNumExchange.getOut().getBody().toString());
        Map bodyMap = (Map) retMap.get("UNI_BSS_BODY");
        if (null == bodyMap || bodyMap.isEmpty()) {
            throw new EcAopServerBizException("9999", "号卡中心返回异常");
        }
        Map qryNumRsp = (Map) bodyMap.get("QRY_NUM_INFO_RSP");
        String code = (String) qryNumRsp.get("RESP_CODE");
        String detail = (String) qryNumRsp.get("RESP_DESC");
        if (!"0000".equals(code)) {
            throw new EcAopServerBizException(code, detail);
        }
        List<Map> resourceInfo = (List<Map>) qryNumRsp.get("RESOURCES_INFO");
        if (null == resourceInfo || resourceInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "号卡中心未返回号码信息");
        }
        String number = (String) resourceInfo.get(0).get("SERIAL_NUMBER");
        String status = (String) resourceInfo.get(0).get("NUM_STATUS");
        if (!"01|02|03|04|08".contains(status)) {
            throw new EcAopServerBizException("9999", "号码:" + number + "状态为:" + status + ",非可用状态");
        }
        return status;

    }

    private Map preData4QryNum(Map qryNumMap, Object serialNumber, String appkey) throws Exception {
        Map preDataMap = new HashMap();
        preDataMap.put("UNI_BSS_BODY", getBodyInfo(qryNumMap, serialNumber));
        preDataMap.putAll(NumFaceHeadHelper.creatHead(appkey));
        return preDataMap;
    }

    private Map getBodyInfo(Map qryNumMap, Object serialNumber) {
        Map bodyMap = new HashMap();
        List<Map> para = new ArrayList<Map>();// 暂时空
        List<Map> numList = new ArrayList<Map>();
        Map numMap = new HashMap();
        numMap.put("SERIAL_NUMBER", serialNumber);
        numList.add(numMap);
        Map qryNumReqMap = new HashMap();
        qryNumReqMap.put("STAFF_ID", qryNumMap.get("operatorId"));
        qryNumReqMap.put("PROVINCE_CODE", qryNumMap.get("province"));
        qryNumReqMap.put("CITY_CODE", qryNumMap.get("city"));
        qryNumReqMap.put("DISTRICT_CODE", qryNumMap.get("district"));
        qryNumReqMap.put("CHANNEL_TYPE", qryNumMap.get("channelType"));
        qryNumReqMap.put("CHANNEL_ID", qryNumMap.get("channelId"));
        qryNumReqMap.put("SYS_CODE", qryNumMap.get("sysCode"));
        qryNumReqMap.put("SEARCH_TYPE", "2");// 散号查询
        qryNumReqMap.put("SELNUM_LIST", numList);
        qryNumReqMap.put("POOL_CODE", "");// 待确认
        qryNumReqMap.put("SERIAL_NUM_START", serialNumber);
        qryNumReqMap.put("SERIAL_NUM_END", serialNumber);
        qryNumReqMap.put("PARA", para);
        bodyMap.put("QRY_NUM_INFO_REQ", qryNumReqMap);
        return bodyMap;
    }

  /*  *//**
     * 该方法用于根据号码区分走原有流程还是走号卡中心流程
     *
     * @param serialNumber
     * @param msg
     * @return 返回参数 0 号卡中心流程 1原有流程
     *//*
    private String numberCentre(Object serialNumber, String serType, Map msg) {
        EssBusinessDao essDao = new EssBusinessDao();
        Map inMap = new HashMap();
        inMap.put("province", msg.get("province"));
        inMap.put("serialNumber", serialNumber);
        // 预付费号码走原有流程
        //        if (StringUtils.isNotEmpty(serType) && "2,3".contains(serType)) {
        //            return "1";
        //        }
        List<Map> routeInfo = essDao.selRouteInfoByProvince(inMap);
        if (null != routeInfo && routeInfo.size() > 0) {
            // PARA_CODE1 0 不对接号卡中心 1 对接
            if ("0".equals(routeInfo.get(0).get("PARA_CODE1"))) {
                return "1";
            }
            // PARA_CODE2 1 全号段割接 0 部分号段
            if ("1".equals(routeInfo.get(0).get("PARA_CODE2"))) {
                return "0";
            }
            return selRouteInfoByNum(inMap);
        }
        return "1";
    }
*/
    /**
     * 部分割接字段判断流程
     *
     * @param inMap
     * @return 返回参数 0 号卡中心流程 1原有流程
     */
   /* private String selRouteInfoByNum(Map inMap) {
        EssBusinessDao essDao = new EssBusinessDao();
        String serialNumber = (String) inMap.get("serialNumber");
        inMap.put("serialNumber", serialNumber.substring(0, 7));
        if ("1".equals(String.valueOf(essDao.selRouteInfoByNum(inMap).get(0)))) {
            return "0";
        }
        return "1";
    }
*/
    private void checkInitParam(Map inMap) {
        if ("1".equals(inMap.get("custType")) && null == inMap.get("custId") && null == inMap.get("bandCustId")) {
            throw new EcAopServerBizException("0001", "客户验证异常:新老客户标识为老客户,未下发客户ID!");
        }
    }

    private String checkaddType(Map inMap) {
        String addType = inMap.get("addType") + "";
        Map addTypeMap = (Map) JSON.parse(EcAopConfigLoader.getStr("ecaop.global.param.mixopen.addType"));
        Object type = addTypeMap.get(addType);
        if (IsEmptyUtils.isEmpty(type)) {
            return "8810";
        }
        return (String) type;
    }

    private Object getFirstMonthBill(List<Map> product) {
        return product.get(0).get("firstMonBillMode");
    }

    private Map dealCbssPreSubmitRet(List<Map> numberList, List<Map> retMapList) {
        if (numberList.size() != retMapList.size()) {
            throw new EcAopServerBizException("9999", "参数数据有误");
        }
        // 整理费用信息
        Integer totalFee = 0;
        Map<Object, Map> numInfo = Maps.newHashMap();
        for (int i = 0; i < retMapList.size(); i++) {
            List<Map> rspInfo = (List<Map>) retMapList.get(i).get("rspInfo");
            if (null == rspInfo || 0 == rspInfo.size()) {
                continue;
            }
            for (Map rsp : rspInfo)
            {
                Object code = retMapList.get(i).get("code");
                Object numberState = "0000".equals(code) || null == code ? "0" : "1";
                Object detail = retMapList.get(i).get("detail");
                List<Map> provinceOrderInfo = (List<Map>) rsp.get("provinceOrderInfo");
                if (null == provinceOrderInfo || 0 == provinceOrderInfo.size()) {
                    continue;
                }
                for (Map preovinceOrder : provinceOrderInfo) {
                    List<Map> preFeeInfoRsp = (List<Map>) preovinceOrder.get("preFeeInfoRsp");
                    if (i < retMapList.size() - 1) {
                        preFeeInfoRsp = new ArrayList<Map>();
                    }
                    else {
                        totalFee = totalFee + Integer.valueOf(preovinceOrder.get("totalFee").toString());
                    }
                    Object subOrderId = preovinceOrder.get("subProvinceOrderId");
                    if (null == preFeeInfoRsp || 0 == preFeeInfoRsp.size()) {
                        Map num = new HashMap();
                        num.put("numberState", numberState);
                        num.put("numberStateDec", detail);
                        numInfo.put(subOrderId, num);
                        continue;
                    }
                    List<Map> feeList = dealFee(preFeeInfoRsp);
                    if (null == numInfo.get(subOrderId)) {
                        Map num = new HashMap();
                        num.put("numberState", numberState);
                        num.put("numberStateDec", detail);
                        num.put("feeInfo", feeList);
                        numInfo.put(subOrderId, num);
                    }
                    else {
                        Map num = numInfo.get(subOrderId);
                        List<Map> numFeeList = (List<Map>) num.get("feeInfo");
                        if (null != numFeeList && 0 != numFeeList.size()) {
                            numFeeList.addAll(feeList);
                            num.put("feeInfo", numFeeList);
                        }
                        else {
                            num.put("feeInfo", feeList);
                        }
                        numInfo.put(subOrderId, num);
                    }
                }
            }
        }
        List<Map> retList = new ArrayList<Map>();
        for (Map temp : numberList) {
            Map retMap = new HashMap();
            retMap.put("number", temp.get("number"));
            retMap.putAll(numInfo.get(temp.get("tradeId")));
            retMap.put("subOrderId", temp.get("tradeId"));
            retList.add(retMap);
        }
        return MapUtils.asMap("totalFee", totalFee, "retList", retList);
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
            retFee.put("maxRelief", fee.get("maxDerateFee"));
            retFee.put("origFee", fee.get("fee"));
            retFeeList.add(retFee);
        }
        return retFeeList;
    }

    /**
     * 获取三户信息
     * 
     * @param exchange
     * @return exchange
     */
    public Exchange getThreeInfo(Exchange exchange) {
        try {
            new LanUtils().preData(pmp[1], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
            new LanUtils().xml2Json("ecaop.trades.cbss.threePart.template", exchange);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", e.getMessage());
        }
        return exchange;
    }

    private Map getAcctInfo(Map mixTemplate, Exchange exchange, Map tradeMap) {
        List<Map> acctInfoList = (List<Map>) mixTemplate.get("acctInfo");
        Map retAcct = Maps.newHashMap();
        retAcct.put("createOrExtendsAcct", "0");
        if (null == acctInfoList || 0 == acctInfoList.size()) {
            retAcct.put("acctId",
                    GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, tradeMap), "seq_acct_id", 1).get(0));
            return retAcct;
        }
        Map acctInfo = acctInfoList.get(0);
        Object createOrExtendsAcct = acctInfo.get("createOrExtendsAcct");
        if ("0".equals(createOrExtendsAcct)) {
            retAcct.put("acctId",
                    GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, tradeMap), "seq_acct_id", 1).get(0));
            return retAcct;
        }
        Object debutySn = acctInfo.get("debutySn");
        if (null == debutySn || "".equals(debutySn)) {
            throw new EcAopServerBizException("9999", "选择继承老账户,请输入合账号码");
        }
        tradeMap.put("getMode", "101001101010001001");
        tradeMap.put("serialNumber", debutySn);
        tradeMap.put("tradeTypeCode", "9999");
        Map retMap = getThreeInfo(ExchangeUtils.ofCopy(exchange, tradeMap)).getOut().getBody(Map.class);
        retAcct.put("createOrExtendsAcct", "1");
        retAcct.put("acctId", ((List<Map>) retMap.get("acctInfo")).get(0).get("acctId"));
        return retAcct;
    }

    /**
     * 河南融合腾讯王卡需求-by wangmc 20180918
     * @param msg
     * @param templateMap
     */
    public void dealAuditTag(Map msg, Map templateMap) {
        if (IsEmptyUtils.isEmpty(msg) || IsEmptyUtils.isEmpty((Map) msg.get("base"))
                || IsEmptyUtils.isEmpty(templateMap) || IsEmptyUtils.isEmpty(templateMap.get("auditTag"))) {
            return;
        }
        ((Map) msg.get("base")).put("auditTag", templateMap.get("auditTag"));
    }

    /**
     * RHQ2018082800066-增加“港澳台居民居住证”证件类型的需求 by wangmc 20180914
     * @param msg
     * @param templateMap
     * @param type
     */
    public void dealCertTypeForGAT(Map msg, Map templateMap, String type) {
        // 只处理融合节点以及移网固网成员新装
        if (!"mixCustInfo".equals(type) && !"0".equals(templateMap.get("installMode"))) {
            return;
        }
        Map ext = (Map) msg.get("ext");
        Map custInfo = (Map) templateMap.get(type);
        DealCertTypeUtils.dealCertType(msg, custInfo, ext);
    }

    /**
     * 融合写卡前置 移网/固网 新装支持压单标志 by wangmc 20180612
     * @param msg
     * @param templateMap
     * @param methodCode
     */
    public void dealDelayTag(Map msg, Map templateMap, String methodCode, boolean isNewOpen) {
        // smno为写卡前置,0为新装
        if (!"smno".equals(methodCode)) {
            return;
        }
        if (isNewOpen && !"0".equals(templateMap.get("installMode"))) {
            return;

        }
        Map tradeItem = (Map) ((Map) msg.get("ext")).get("tradeItem");
        List<Map> itemList = new ArrayList<Map>();
        if (!IsEmptyUtils.isEmpty(tradeItem) && !IsEmptyUtils.isEmpty((List<Map>) tradeItem.get("item"))) {
            itemList = (List<Map>) tradeItem.get("item");
        }
        if (isNewOpen) {
            // delayTag为1表示压单
            if ("1".equals(templateMap.get("delayTag"))) {
                itemList.add(LanUtils.createTradeItem("E_DELAY_TIME_CEL", "1"));
            }
            // 订单是否同步到电子渠道激活
            if ("1".equals(templateMap.get("isAfterActivation"))) {
                itemList.add(LanUtils.createTradeItem("IS_AFTER_ACTIVATION", "1"));
            }

        } else {
            //RHQ2018080600048-CBSS收费明细报表-蜂行动
            List<Map> tradeItemList = (List<Map>) templateMap.get("tradeItem");
            if (!IsEmptyUtils.isEmpty(tradeItemList)) {
                for (Map tempMap : tradeItemList) {
                    itemList.add(LanUtils.createTradeItem((String) tempMap.get("attrCode"),
                            tempMap.get("attrValue")));
                }
            }
        }
        ((Map) msg.get("ext")).put("tradeItem", MapUtils.asMap("item", itemList));
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

    private void creatAcctInfo(Map inputMap, Exchange exchange, String itemIdForAcct)
    {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map preSubMap = new HashMap();
        Map base = new HashMap();
        base.put("subscribeId", itemIdForAcct);
        base.put("tradeId", itemIdForAcct);
        base.put("tradeTypeCode", "3000");
        base.put("productId", "");
        base.put("brandCode", "COMP");
        base.put("userId", "");
        base.put("acctId", inputMap.get("acctId"));
        base.put("custId", inputMap.get("custId"));
        base.put("usecustId", "");
        base.put("userDiffCode", "");
        base.put("netTypeCode", "00CP");
        base.put("custName", ((Map) inputMap.get("custCheckRetMap")).get("custName"));
        base.put("serinalNamber", inputMap.get("serialNumber"));
        base.put("eparchyCode", msg.get("city"));
        base.put("cityCode", msg.get("district"));
        base.put("execTime", GetDateUtils.getDate());
        base.put("cancelTag", "0");

        Map ext = new HashMap();
        Map tradeAcct = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("xDatatype", MagicNumber.STRING_OF_NULL);
        item.put("acctId", inputMap.get("acctId"));
        item.put("custId", inputMap.get("custId"));
        item.put("payName", ((Map) inputMap.get("custCheckRetMap")).get("custName"));
        MapUtils.arrayPutFix("0", item, new String[] { "payModeCode", "scoreValue",
                "basicCreditValue", "creditValue", "removeTag" });
        itemList.add(item);
        tradeAcct.put("item", itemList);
        ext.put("tradeAcct", tradeAcct);
        LanUtils lan = new LanUtils();
        List<Map> items = new ArrayList<Map>();
        // RHA2017102300039-关于支撑通过标记控制代理商办理业务时是否扣周转金的需求
        if ("1".equals((String) msg.get("markingTag")) && "smno".equals(exchange.getMethodCode())) {
            items.add(lan.createAttrInfoNoTime("MARKING_APP", "1"));
        }
        if ("0".equals((String) msg.get("deductionTag")) && "smno".equals(exchange.getMethodCode())) {
            items.add(lan.createAttrInfoNoTime("FEE_TYPE", "1"));
        }
        items.add(lan.createAttrInfoNoTime("STANDARD_KIND_CODE", "1010200"));
        ext.put("tradeItem", MapUtils.asMap("item", items));
        preSubMap.put("base", base);
        preSubMap.put("ext", ext);
        preSubMap.put("ordersId", itemIdForAcct);
        preSubMap.put("operTypeCode", "0");
        preSubMap.put("serialNumber", inputMap.get("serialNumber"));
        MapUtils.arrayPut(preSubMap, msg, MagicNumber.COPYARRAY);
        Exchange exchangeForAcct = ExchangeUtils.ofCopy(exchange, preSubMap);
        try {
            new LanUtils().preData(pmp[3], exchangeForAcct);
            ELKAopLogger.logStr("创建账户预提交: appTx: " + exchange.getProperty(Exchange.APPTX) + "-----------------------------------");
            CallEngine.wsCall(exchangeForAcct, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
            new LanUtils().xml2Json("ecaop.trades.sccc.cancelPre.template", exchangeForAcct);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "创建账户资料失败！" + e.getMessage());
        }
    }
}
