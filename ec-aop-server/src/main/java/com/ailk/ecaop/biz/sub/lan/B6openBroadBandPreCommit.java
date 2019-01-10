package com.ailk.ecaop.biz.sub.lan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.sub.gdjk.OpenApply4GProcessor;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.DealCertTypeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.MixBroadBandAndLandLineToolUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;

@EcRocTag("B6openBroadBandPreCommitProcessing")
@SuppressWarnings({ "unchecked", "rawtypes" })
public class B6openBroadBandPreCommit extends BaseAopProcessor implements ParamsAppliable {

    // private String provinceCode = "";
    // String eparchyCode;
    // String discntId;
    // 代码优化
    private static final String[] PARAM_ARRAY = { "ecaop.masb.sbac.N6.sglUniTradeParametersMapping",
            "ecaop.trades.seqid.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[2];

    // 宽带单装
    @Override
    public void process(Exchange exchange) throws Exception {
        // 调用客户信息校验之前，请求参数的保留备份
        Map body = exchange.getIn().getBody(Map.class);
        String methodCode = exchange.getMethodCode();
        LanUtils lan = new LanUtils();
        // 调用预提交接口
        Object msgObject = body.get("msg");
        Map msg = null;
        if (msgObject instanceof String) {
            msg = JSON.parseObject(msgObject.toString());
        }
        else {
            msg = (Map) msgObject;
        }

        Map preMap = dealInparams(exchange);
        String ordersId = (String) msg.get("orderNo");
        String operTypeCode = "0";
        Map base = (Map) preMap.get("base");
        Map ext = (Map) preMap.get("ext");
        msg.put("base", base);
        msg.put("ext", ext);
        msg.put("ordersId", ordersId);
        msg.put("operTypeCode", operTypeCode);

        body.put("msg", msg);
        exchange.getIn().setBody(body);
        // 调宽带预提交
        lan.preData(pmp[0], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.n6ess.services.AOPOrdForNorthSer" + "." + msg.get("province"));
        lan.xml2Json("ecaop.masb.sbac.sglUniTradeTemplate", exchange);
        exchange.getIn().setBody(body);
        // 整理返回报文
        dealResult(exchange);
    }

    // 处理返回结果
    private void dealResult(Exchange exchange) {
        String methodCode = exchange.getMethodCode();
        Map body = exchange.getIn().getBody(Map.class);
        // 获取msg信息
        Object msgObject = body.get("msg");
        Map msg = null;
        if (msgObject instanceof String) {
            msg = JSON.parseObject(msgObject.toString());
        }
        else {
            msg = (Map) msgObject;
        }
        // 整理返回报文
        Map message = exchange.getOut().getBody(Map.class);
        if (null != message.get("rspInfo")) {
            List<Map> rspInfoList = (List<Map>) message.get("rspInfo");
            Map rspInfoNew = rspInfoList.get(0);
            // 处理返回参数
            Map result = dealReturn(rspInfoNew, msg, methodCode);
            if (null == result.get("userName")) {
                String userName = (String) ((Map) msg.get("newUserInfo")).get("certName");
                result.put("userName", userName);
            }
            exchange.getOut().setBody(result);
        }
    }

    // 处理参数
    private Map dealInparams(Exchange exchange) throws Exception {
        List<Map> tradeSvcList;
        List<Map> tradeDisList;
        Map body = exchange.getIn().getBody(Map.class);
        // 获取msg信息
        Object msgObject = body.get("msg");
        Map msg = null;
        if (msgObject instanceof String) {
            msg = JSON.parseObject(msgObject.toString());
        }
        else {
            msg = (Map) msgObject;
        }

        String provinceCode = "00" + msg.get("province");
        String eparchyCode = ChangeCodeUtils.changeEparchy(msg);
        msg.put("eparchyCode", eparchyCode);
        String methodCode = exchange.getMethodCode();
        String tradeId = GetSeqUtil.getSeqFromN6ess(pmp[1], exchange, "TRADE_ID", eparchyCode);
        exchange.getIn().setBody(body);
        String itemId = GetSeqUtil.getSeqFromN6ess(pmp[1], exchange, "ITEM_ID", eparchyCode);
        exchange.getIn().setBody(body);
        String userId = GetSeqUtil.getSeqFromN6ess(pmp[1], exchange, "USER_ID", eparchyCode);
        exchange.getIn().setBody(body);
        String custType = (String) msg.get("custType");
        String custId = "";
        if ("1".equals(custType)) {
            custId = (String) msg.get("custId");
        }
        else {
            custId = GetSeqUtil.getSeqFromN6ess(pmp[1], exchange, "CUST_ID", eparchyCode);
        }
        exchange.getIn().setBody(body);
        Map ext = new HashMap(); // 产品返回
        Map newUserInfo = (Map) msg.get("newUserInfo");
        String installAddress = null;
        if (newUserInfo != null) {
            installAddress = (String) newUserInfo.get("installAddress");
        }
        else {
            installAddress = "00";
        }
        List<Map> productInfo = new ArrayList<Map>();

        if (newUserInfo != null) {
            productInfo = (List) newUserInfo.get("productInfo");
        }
        else {
            throw new EcAopServerBizException("9999", "人员信息不足");
        }
        List<Map> activityInfos = (List) newUserInfo.get("activityInfo");
        if (activityInfos != null) {
            msg.put("activityInfo", activityInfos);
        }
        Map productInfoMap = productInfo.get(0);
        String productMode = (String) productInfoMap.get("productMode");
        if ("0".equals(productMode)) {
            productInfoMap.put("productMode", "1");
        }
        if ("1".equals(productMode)) {
            productInfoMap.put("productMode", "0");
        }
        productInfo.add(productInfoMap);
        // 账户处理
        String acctId = "";
        if ("1".equals(newUserInfo.get("createOrExtendsAcct") + "")) {
            acctId = (String) newUserInfo.get("debutySerialNumber");
        }
        else {
            exchange.getIn().setBody(body);
            acctId = GetSeqUtil.getSeqFromN6ess(pmp[1], exchange, "ACCT_ID", eparchyCode);
            exchange.getIn().setBody(body);
        }

        // 调用处理产品方法
        if (productInfo != null) {
            msg.put("appCodeAndMethodCode", exchange.getAppCode() + exchange.getMethodCode() + "3G");
            if ("sdprbsop3G".equals(msg.get("appCodeAndMethodCode") + "")) {
                ext = preProductInfoForBroadBand(productInfo, provinceCode, msg);
            }
            else {
                ext = TradeManagerUtils.preProductInfo(productInfo, provinceCode, msg);
            }

            System.out.println(body.get("apptx") + "处理完产品之后的数据,ext:" + ext);
            tradeSvcList = (List<Map>) ((Map) ext.get("tradeSvc")).get("item");
            // tradeDisList = (List<Map>) ((Map) ext.get("tradeDiscnt")).get("item");

            // 服务去重
            uniqueService(tradeSvcList);
            // 服务endDate格式转换
            for (Map item : tradeSvcList) {
                item.put("endDate", GetDateUtils.transDate((String) item.get("endDate"), 14));
                item.put("startDate", GetDateUtils.transDate((String) item.get("startDate"), 14));
            }
        }

        else {
            throw new EcAopServerBizException("9999", "产品信息不足");
        }

        Map tradeProduct = (Map) ext.get("tradeProduct");
        List<Map> item = (List) tradeProduct.get("item");
        String productId = null;
        String brandCode = null;
        for (Map m : item) {
            if (m.get("productId") != null) {
                productId = (String) m.get("productId");
            }
            if (m.get("brandCode") != null) {
                brandCode = (String) m.get("brandCode");
            }
        }

        Map tradeProductType = (Map) ext.get("tradeProductType");
        List<Map> itemT = (List) tradeProductType.get("item");
        String productTypeCode = null;
        for (Map m : itemT) {
            if (m.get("productTypeCode") != null) {
                productTypeCode = (String) m.get("productTypeCode");

            }
        }

        String reTypeCode = "0";//
        String resCode = (String) msg.get("serialNumber");
        String channelType = (String) msg.get("channelType");
        String operatorId = (String) msg.get("operatorId");
        String channelId = (String) msg.get("channelId");

        String subscribeId = tradeId;
        String startDate = GetDateUtils.getDate();
        String endDate = "20501231122359";
        String acceptDate = GetDateUtils.getDate();
        String nextDealTag = "Z";
        String olcomTag = "1";
        String inModeCode = "E";
        String tradeTypeCode = "0010";
        String usecustId = custId;
        String userDiffCode = "00";
        String serialNumber = (String) msg.get("serialNumber");
        String custName = (String) newUserInfo.get("certName");
        String termIp = "127.0.0.1";

        String cityCodeNew = (String) msg.get("city");
        String execTime = GetDateUtils.getDate();
        String operFee = "0";
        String foregift = "0";
        String advancePay = "0";
        String cancelTag = "0";
        String chkTag = "0";
        String mainDiscntCode = "123";

        // acctTrade
        Object payName = msg.get("userName");
        String xDataType = "NULL"; // 默认值为null
        String payModeCode = "0";
        String scoreValue = "0";
        String creditClassId = "0";
        String basicCreditValue = "0";
        String creditValue = "0";
        String creditControlId = "0";
        String debutyUserId = userId;
        String removeTag = "0";
        String openDate = GetDateUtils.getDate();

        // tradeUser
        String userPassword = (String) msg.get("userPasswd");
        userPassword = (null == userPassword) ? (serialNumber.substring(serialNumber.length() - 6,
                serialNumber.length())) : userPassword;
        String userTypeCode = "2";
        String creditClass = "0";
        String acctTag = "0";
        String preTag = "0";
        String inDate = GetDateUtils.getDate();
        String openMode = "0";
        String userStateCodeset = "0";
        String mputeMonthFee = "0";
        String developStaffId = (String) msg.get("recomPersonId");
        String developDate = GetDateUtils.getDate();
        String developEparchyCode = (String) msg.get("recomPersonCityCode");
        String developCityCode = (String) msg.get("recomPersonDistrict");
        String developDepartId = (String) msg.get("recomPersonChannelId");
        String openDepartId = channelId;
        String openStaffId = operatorId;
        String inDepartId = channelId;
        String inStaffId = operatorId;
        String inNetMode = "0";
        // tradeProductType
        String modifyTag = "0";
        // tradePayrelation
        String payitemCode = "-1"; // 必传
        String acctPriority = "0";
        String userPriority = "0";
        String bindType = "1";
        String defaultTag = "1";
        String limitType = "0";
        String limit = "0";
        String complementTag = "0";
        String addupMonths = "0";
        String addupMethod = "0";
        String payrelationId = GetSeqUtil.getSeqFromN6ess(pmp[1], exchange, "TRADE_ID", eparchyCode);
        exchange.getIn().setBody(body);
        String actTag = "1";
        // tradeOther
        String rsrvValueCode = "ZZFS";
        String rsrvValue = "0";
        String rsrvStr1 = (String) newUserInfo.get("certNum");
        String rsrvStr2 = "10";
        String rsrvStr3 = "-9";
        String rsrvStr6 = "1";
        String rsrvStr7 = "0";
        // 整理参数
        Map base = new HashMap();
        base.put("subscribeId", subscribeId);
        base.put("tradeId", tradeId);
        base.put("startDate", startDate);
        base.put("endDate", endDate);
        base.put("userId", userId);
        base.put("custId", custId);
        base.put("acctId", acctId);
        base.put("acceptDate", acceptDate);
        base.put("nextDealTag", nextDealTag);
        base.put("olcomTag", olcomTag);
        base.put("inModeCode", inModeCode);
        base.put("tradeTypeCode", tradeTypeCode);
        base.put("productId", productId);
        base.put("brandCode", brandCode);
        base.put("usecustId", usecustId);
        base.put("userDiffCode", userDiffCode);
        base.put("serinalNamber", serialNumber);
        base.put("custName", custName);
        base.put("termIp", termIp);
        base.put("eparchyCode", eparchyCode);
        base.put("cityCode", msg.get("district"));
        base.put("execTime", execTime);
        base.put("operFee", operFee);
        base.put("foregift", foregift);
        base.put("advancePay", advancePay);
        base.put("cancelTag", cancelTag);
        base.put("chkTag", chkTag);
        base.put("mainDiscntCode", mainDiscntCode);
        base.put("feeState", "2");
        base.put("feeStaffId", "dsadsa");
        base.put("checkTypeCode", "123");
        base.put("actorName", "dsadsa");
        base.put("actorCertTypeId", "1");
        base.put("actorPhone", "12345678910");
        base.put("actorCertnum", "12346579845645646");
        base.put("contact", msg.get("contactPerson"));
        base.put("contactPhone", msg.get("contactPhone"));
        base.put("contactAddress", msg.get("contactAddress"));
        base.put("remark", msg.get("orderRemarks"));
        if ("sfso".equals(methodCode)) {// 固话单装
            base.put("productSpec", "1"); // 不确定
            base.put("netTypeCode", "0030");
        }
        else {
            base.put("netTypeCode", "0040");
        }
        msg.put("startDate", GetDateUtils.getDate());
        // 河南要求只有新建账户时才下发tradeAcct节点，其他系统暂不放开
        if (("0".equals(newUserInfo.get("createOrExtendsAcct") + "") && "hapr".equals(exchange.getAppCode()))
                || !"hapr".equals(exchange.getAppCode())) {
            // tredeAcct
            Map tradeAcct = new HashMap();
            Map item1 = new HashMap();
            item1.put("xDataType", xDataType);
            item1.put("acctId", acctId);
            item1.put("payName", isNull(payName) ? newUserInfo.get("certName") : payName);
            item1.put("payModeCode", payModeCode);
            item1.put("scoreValue", scoreValue);
            item1.put("creditClassId", creditClassId);
            item1.put("basicCreditValue", basicCreditValue);
            item1.put("creditValue", creditValue);
            item1.put("creditControlId", creditControlId);
            item1.put("debutyUserId", debutyUserId);
            item1.put("removeTag", removeTag);
            item1.put("openDate", openDate);
            item1.put("eparchyCode", eparchyCode);
            item1.put("cityCode", cityCodeNew);
            tradeAcct.put("item", item1);
            ext.put("tradeAcct", tradeAcct);
        }
        // tradeUser
        Map tradeUser = new HashMap();
        Map item2 = new HashMap();
        item2.put("xDataType", xDataType);
        item2.put("usecustId", usecustId);
        item2.put("userPasswd", userPassword);
        item2.put("userTypeCode", userTypeCode);
        item2.put("scoreValue", scoreValue);
        item2.put("creditClass", creditClass);
        item2.put("basicCreditValue", basicCreditValue);
        item2.put("creditValue", creditValue);
        item2.put("acctTag", acctTag);
        item2.put("cityCode", msg.get("district"));
        item2.put("prepayTag", preTag);
        item2.put("inDate", inDate);
        item2.put("openDate", openDate);
        item2.put("openMode", openMode);
        item2.put("openDepartId", openDepartId);
        item2.put("openStaffId", openStaffId);
        item2.put("inDepartId", inDepartId);
        item2.put("inStaffId", inStaffId);
        item2.put("removeTag", removeTag);
        item2.put("userStateCodeset", userStateCodeset);
        item2.put("mputeMonthFee", mputeMonthFee);
        item2.put("developStaffId", developStaffId);
        item2.put("developDate", developDate);
        item2.put("developEparchyCode", developEparchyCode);
        item2.put("developCityCode", developCityCode);
        item2.put("developDepartId", developDepartId);
        item2.put("inNetMode", inNetMode);
        item2.put("productTypeCode", productTypeCode);
        tradeUser.put("item", item2);
        // tradeRes
        Map tradeRes = new HashMap();
        if ("bsop".equals(methodCode)) {// 宽带单装预提交
            Map item7 = new HashMap();
            item7.put("xDataType", xDataType);
            item7.put("reTypeCode", reTypeCode);
            item7.put("resCode", resCode);
            item7.put("modifyTag", modifyTag);
            item7.put("startDate", startDate);
            item7.put("endDate", endDate);
            tradeRes.put("item", item7);
        }
        else {
            List<Map> tradeRe = new ArrayList<Map>();
            Map itemRes1 = new HashMap();
            itemRes1.put("xDataType", xDataType);
            itemRes1.put("reTypeCode", reTypeCode);
            itemRes1.put("resCode", resCode);
            itemRes1.put("modifyTag", modifyTag);
            itemRes1.put("startDate", startDate);
            itemRes1.put("endDate", endDate);
            Map itemRes2 = new HashMap();
            itemRes2.put("xDataType", xDataType);
            itemRes2.put("reTypeCode", reTypeCode);
            itemRes2.put("resCode", resCode);
            itemRes2.put("resInfo1", resCode); // 不确定
            itemRes2.put("modifyTag", modifyTag);
            tradeRe.add((Map) new HashMap().put("item", itemRes1));
            tradeRe.add((Map) new HashMap().put("item", itemRes2));
            tradeRes.put("tradeRes", tradeRe);
        }
        // tradePayrelation
        Map tradePayrelation = new HashMap();
        Map item8 = new HashMap();
        item8.put("userId", userId);
        item8.put("acctId", acctId);
        item8.put("payitemCode", payitemCode);
        item8.put("acctPriority", acctPriority);
        item8.put("userPriority", userPriority);
        item8.put("bindType", bindType);
        item8.put("defaultTag", defaultTag);
        item8.put("limitType", limitType);
        item8.put("limit", limit);
        item8.put("complementTag", complementTag);
        item8.put("addupMonths", addupMonths);
        item8.put("addupMethod", addupMethod);
        item8.put("payrelationId", payrelationId);
        item8.put("actTag", actTag);
        tradePayrelation.put("item", item8);
        // tradeOther
        Map tradeOther = new HashMap();
        Map item9 = new HashMap();

        item9.put("xDatatype", xDataType);
        item9.put("rsrvValueCode", rsrvValueCode);
        item9.put("rsrvValue", rsrvValue);
        item9.put("rsrvStr1", rsrvStr1);
        item9.put("rsrvStr2", rsrvStr2);
        item9.put("rsrvStr3", rsrvStr3);
        item9.put("rsrvStr6", rsrvStr6);
        item9.put("rsrvStr7", rsrvStr7);
        item9.put("modifyTag", modifyTag);
        item9.put("startDate", startDate);
        item9.put("endDate", endDate);
        tradeOther.put("item", item9);

        // trade_item

        List<Map> tradeItem = new ArrayList<Map>();
        LanUtils lan = new LanUtils();
        if ("76".contains((String) msg.get("province"))) {
            tradeItem.add(lan.createTradeItemTime("DEVELOP_STAFF_ID", developStaffId));

            tradeItem.add(lan.createTradeItemTime("DEVELOP_DEPART_ID", developDepartId));

            tradeItem.add(lan.createTradeItemTime("STANDARD_KIND_CODE", channelType));

            tradeItem.add(lan.createTradeItemTime("PH_NUM", serialNumber));

            tradeItem.add(lan.createTradeItemTime("USER_PASSWD", userPassword));

            tradeItem.add(lan.createTradeItemTime("NEW_PASSWD", userPassword));

            if ("0".equals(msg.get("deductionTag"))) {
                tradeItem.add(LanUtils.createTradeItemTime("FEE_TYPE", "1"));// RHA2017102300039-关于支撑通过标记控制代理商办理业务时是否扣周转金的需求
            }
            // 下发省份传入的工单属性
            List<Map> woExchInfo = (List<Map>) newUserInfo.get("woExchInfo");
            if (woExchInfo != null) {
                for (Map map : woExchInfo) {
                    tradeItem.add(lan.createTradeItemTime(map.get("key") + "", map.get("value") + ""));
                }
            }
        }
        else {
            tradeItem.add(lan.createTradeItem("DEVELOP_STAFF_ID", developStaffId));

            tradeItem.add(lan.createTradeItem("DEVELOP_DEPART_ID", developDepartId));

            tradeItem.add(lan.createTradeItem("STANDARD_KIND_CODE", channelType));

            tradeItem.add(lan.createTradeItem("PH_NUM", serialNumber));

            tradeItem.add(lan.createTradeItem("USER_PASSWD", userPassword));

            tradeItem.add(lan.createTradeItem("NEW_PASSWD", userPassword));

            if ("0".equals(msg.get("deductionTag"))) {
                tradeItem.add(LanUtils.createTradeItem("FEE_TYPE", "1"));// RHA2017102300039-关于支撑通过标记控制代理商办理业务时是否扣周转金的需求
            }
            // 下发省份传入的工单属性
            List<Map> woExchInfo = (List<Map>) newUserInfo.get("woExchInfo");
            if (woExchInfo != null) {
                for (Map map : woExchInfo) {
                    tradeItem.add(lan.createTradeItem(map.get("key") + "", map.get("value") + ""));
                }
            }
        }

        // TRADE_SUB_ITEM开始
        List<Map> tradeSubItem = new ArrayList<Map>();
        List machineInfos = (List) msg.get("machineInfo");
        String machineType = "";
        String machineProvide = "";
        if (null != machineInfos) {
            Map machineInfo = (Map) machineInfos.get(0);
            machineType = (String) machineInfo.get("machineType");
            if (machineType == null) {
                machineType = "1";
            }
            machineProvide = (String) machineInfo.get("machineProvide");
            if (machineProvide == null) {
                machineProvide = "1";
            }
        }

        // 通用属性

        // 针对辽宁下发的属性
        if ("0091".equals(provinceCode)) {
            tradeSubItem.add(lan.createTradeSubItemBroad1("LINK_PHONE", msg.get("contactPhone"), userId,
                    (String) msg.get("startDate")));
            tradeSubItem.add(lan.createTradeSubItemBroad1("ACCT_NBR", msg.get("authAcctId"), userId,
                    (String) msg.get("startDate")));
            tradeSubItem.add(lan.createTradeSubItemBroad1("ACCT_PASSWD", isNULL(msg.get("userPasswd") + "", ""),
                    userId, (String) msg.get("startDate")));
            tradeSubItem.add(lan.createTradeSubItemBroad1("AREA_CODE",
                    ((String) msg.get("serialNumber")).substring(0, 4), userId, (String) msg.get("startDate")));
            tradeSubItem.add(lan.createTradeSubItemBroad1("UserNature",
                    isNULL(newUserInfo.get("userProperty") + "", ""), userId, (String) msg.get("startDate")));
            tradeSubItem.add(lan.createTradeSubItemBroad1("USER_CONTACT_PHONE", msg.get("contactPhone"), userId,
                    (String) msg.get("startDate")));
            tradeSubItem.add(lan.createTradeSubItemBroad1("TerminalSource", machineProvide, userId,
                    (String) msg.get("startDate")));
            tradeSubItem.add(lan.createTradeSubItemBroad1("USER_TYPE_CODE",
                    isNULL(newUserInfo.get("userType") + "", "1"), userId, (String) msg.get("startDate")));
            tradeSubItem.add(lan.createTradeSubItemBroad1("JIE_RU_FANG_SHI", newUserInfo.get("accessMode"), userId,
                    (String) msg.get("startDate")));
            tradeSubItem.add(lan.createTradeSubItemBroad1("INSTALL_ADDRESS", newUserInfo.get("addressName"), userId,
                    (String) msg.get("startDate")));
            tradeSubItem.add(lan.createTradeSubItemBroad1("ConformPassWord", isNULL(msg.get("userPasswd") + "", ""),
                    userId, (String) msg.get("startDate")));
            tradeSubItem.add(lan.createTradeSubItemBroad1("LINK_NAME", msg.get("contactPerson"), userId,
                    (String) msg.get("startDate")));
            tradeSubItem.add(lan.createTradeSubItemBroad1("ADDRESS_ID",
                    isNULL(newUserInfo.get("addressCode") + "", ""), userId, (String) msg.get("startDate")));
            tradeSubItem.add(lan.createTradeSubItemBroad1("USER_PASSWD", isNULL(msg.get("userPasswd") + "", ""),
                    userId, (String) msg.get("startDate")));
            tradeSubItem.add(lan.createTradeSubItemBroad1("MOFFICE_ID", isNULL(newUserInfo.get("exchCode") + "", ""),
                    userId, (String) msg.get("startDate")));
            tradeSubItem.add(lan.createTradeSubItemBroad1("PassWord", isNULL(msg.get("userPasswd") + "", ""), userId,
                    (String) msg.get("startDate")));
            tradeSubItem.add(lan.createTradeSubItemBroad1("USER_CALLING_AREA", eparchyCode, userId,
                    (String) msg.get("startDate")));
            tradeSubItem.add(lan.createTradeSubItemBroad1("ChargeType",
                    isNULL(newUserInfo.get("acceptMode") + "", "1"), userId, (String) msg.get("startDate")));
            tradeSubItem.add(lan.createTradeSubItemBroad1("DETAIL_INSTALL_ADDRESS", newUserInfo.get("installAddress"),
                    userId, (String) msg.get("startDate")));
            tradeSubItem.add(lan.createTradeSubItemBroad1("Speed", isNULL(newUserInfo.get("speedLevel") + "", ""),
                    userId, (String) msg.get("startDate")));
            tradeSubItem.add(lan.createTradeSubItemBroad1("USER_NAME", newUserInfo.get("certName"), userId,
                    (String) msg.get("startDate")));
        }
        else if ("0076".equals(provinceCode)) {
            tradeSubItem.add(lan.createTradeSubItemBroad("AREA_CODE", msg.get("district"), userId));
            tradeSubItem.add(lan.createTradeSubItemBroad("UserNature",
                    isNULL(newUserInfo.get("userProperty") + "", ""), userId));
            tradeSubItem.add(lan.createTradeSubItemBroad("USER_CONTACT_PHONE", msg.get("contactPhone"), userId));
            tradeSubItem.add(lan.createTradeSubItemBroad("TerminalSource", machineProvide, userId));
            tradeSubItem.add(lan.createTradeSubItemBroad("USER_TYPE_CODE",
                    isNULL(newUserInfo.get("userType") + "", "1"), userId));
            tradeSubItem.add(lan.createTradeSubItemBroad("JIE_RU_FANG_SHI", newUserInfo.get("accessMode"), userId));
            tradeSubItem.add(lan.createTradeSubItemBroad("INSTALL_ADDRESS", newUserInfo.get("addressName"), userId));
            tradeSubItem.add(lan.createTradeSubItemBroad("ConformPassWord", isNULL(msg.get("userPasswd") + "", ""),
                    userId));
            tradeSubItem.add(lan.createTradeSubItemBroad("LINK_NAME", msg.get("contactPerson"), userId));
            tradeSubItem.add(lan.createTradeSubItemBroad("ADDRESS_ID", isNULL(newUserInfo.get("addressCode") + "", ""),
                    userId));
            tradeSubItem
                    .add(lan.createTradeSubItemBroad("USER_PASSWD", isNULL(msg.get("userPasswd") + "", ""), userId));
            tradeSubItem.add(lan.createTradeSubItemBroad("MOFFICE_ID", isNULL(newUserInfo.get("exchCode") + "", ""),
                    userId));
            tradeSubItem.add(lan.createTradeSubItemBroad("PassWord", isNULL(msg.get("userPasswd") + "", ""), userId));
            tradeSubItem.add(lan.createTradeSubItemBroad("USER_CALLING_AREA", eparchyCode, userId));
            tradeSubItem.add(lan.createTradeSubItemBroad("ChargeType", isNULL(newUserInfo.get("acceptMode") + "", "1"),
                    userId));
            tradeSubItem.add(lan.createTradeSubItemBroad("DETAIL_INSTALL_ADDRESS", newUserInfo.get("installAddress"),
                    userId));
            tradeSubItem.add(lan.createTradeSubItemBroad("Speed", isNULL(newUserInfo.get("speedLevel") + "", ""),
                    userId));
            tradeSubItem.add(lan.createTradeSubItemBroad("USER_NAME", newUserInfo.get("certName"), userId));
        }
        else {
            tradeSubItem.add(lan.createTradeSubItem("AREA_CODE", msg.get("district"), userId));
            tradeSubItem.add(lan.createTradeSubItem("UserNature", isNULL(newUserInfo.get("userProperty") + "", ""),
                    userId));
            tradeSubItem.add(lan.createTradeSubItem("USER_CONTACT_PHONE", msg.get("contactPhone"), userId));
            tradeSubItem.add(lan.createTradeSubItem("TerminalSource", machineProvide, userId));
            tradeSubItem.add(lan.createTradeSubItem("USER_TYPE_CODE", isNULL(newUserInfo.get("userType") + "", "1"),
                    userId));
            tradeSubItem.add(lan.createTradeSubItem("JIE_RU_FANG_SHI", newUserInfo.get("accessMode"), userId));
            tradeSubItem.add(lan.createTradeSubItem("INSTALL_ADDRESS", newUserInfo.get("addressName"), userId));
            tradeSubItem.add(lan.createTradeSubItem("ConformPassWord", isNULL(msg.get("userPasswd") + "", ""), userId));
            tradeSubItem.add(lan.createTradeSubItem("LINK_NAME", msg.get("contactPerson"), userId));
            tradeSubItem.add(lan.createTradeSubItem("ADDRESS_ID", isNULL(newUserInfo.get("addressCode") + "", ""),
                    userId));
            tradeSubItem.add(lan.createTradeSubItem("USER_PASSWD", isNULL(msg.get("userPasswd") + "", ""), userId));
            tradeSubItem
                    .add(lan.createTradeSubItem("MOFFICE_ID", isNULL(newUserInfo.get("exchCode") + "", ""), userId));
            tradeSubItem.add(lan.createTradeSubItem("PassWord", isNULL(msg.get("userPasswd") + "", ""), userId));
            tradeSubItem.add(lan.createTradeSubItem("USER_CALLING_AREA", eparchyCode, userId));
            tradeSubItem.add(lan.createTradeSubItem("ChargeType", isNULL(newUserInfo.get("acceptMode") + "", "1"),
                    userId));
            tradeSubItem
                    .add(lan.createTradeSubItem("DETAIL_INSTALL_ADDRESS", newUserInfo.get("installAddress"), userId));
            tradeSubItem.add(lan.createTradeSubItem("Speed", isNULL(newUserInfo.get("speedLevel") + "", ""), userId));
            tradeSubItem.add(lan.createTradeSubItem("USER_NAME", newUserInfo.get("certName"), userId));
        }

        // 处理传入的用户属性
        List<Map> userExchInfo = (List<Map>) newUserInfo.get("userExchInfo");
        if (userExchInfo != null) {
            for (Map map : userExchInfo) {
                // 针对山东userExchInfo下属性节点不下发时间
                if ("17".contains((String) msg.get("province"))) {
                    tradeSubItem.add(lan.createTradeSubItem(map.get("key") + "", map.get("value") + "", userId));
                }
                else {
                    tradeSubItem.add(lan.createTradeSubItemBroad(map.get("key") + "", map.get("value") + "", userId));
                }

            }
        }

        // 处理传入的服务属性
        JSONArray packageElement = (JSONArray) productInfo.get(0).get("packageElement");
        List<Map> packageElementList = JSONArray.toJavaObject(packageElement, List.class);
        if (packageElementList != null) {
            for (Map inMap : packageElementList) {
                String discntId = inMap.get("elementId") + "";
                if ("S".equals(inMap.get("elementType") + "")) {
                    for (Map service : tradeSvcList) {
                        if ((inMap.get("elementId") + "").equals(service.get("serviceId") + "")) {
                            // 取出请求参数中的服务的属性
                            JSONArray attrs = (JSONArray) inMap.get("serviceAttr");
                            List<Map> attrList = JSONArray.toJavaObject(attrs, List.class);
                            if (attrList != null && attrList.size() > 0) {
                                for (int i = 0; i < attrList.size(); i++) {
                                    // 服务与其对应的属性的itemid值保持一致
                                    tradeSubItem.add(createTradeSubItemFore(attrList.get(i).get("code"), attrList
                                            .get(i).get("value"), service.get("itemId")));
                                }
                            }
                        }
                    }
                }
            }
        }

        msg.put("tradeSubItem", tradeSubItem);
        // TRADE_SUB_ITEM处理结束（不包括包年资费属性的处理）

        // 包年资费处理
        msg.put("itemId", itemId);
        msg.put("EPARCHY_CODE", eparchyCode);
        msg.put("PROVINCE_CODE", "00" + msg.get("province"));
        msg.put("productId", productId);
        // 辽宁处理与CB一致
        if ("91".equals(msg.get("province"))) {
            MixBroadBandAndLandLineToolUtils.preStartOrEndDiscntInfo(msg);
        }
        else {
            MixBroadBandAndLandLineToolUtils.preStartOrEndDiscntInfoForN6(msg);
        }
        // 到期资费处理结束
        Map trade = new HashMap();
        // tradeDiscnt节点
        List<Map> discntList = (List<Map>) msg.get("discntList");
        List<Map> boradDiscntInfos = (List<Map>) newUserInfo.get("boradDiscntInfo");
        // 非包年资费（无属性的资费）使用
        if (null != boradDiscntInfos
                && !((boradDiscntInfos.get(0).get("boradDiscntId") + "").equals(msg.get("bnzf") + ""))) {
            for (Map map : boradDiscntInfos) {
                msg.put("elementId", map.get("boradDiscntId"));
                if ("0076".equals(provinceCode)) {
                    String id = GetSeqUtil.getSeqFromN6ess(ExchangeUtils.ofCopy(exchange, msg), "seq_item_id",
                            eparchyCode);
                    msg.put("itemId", id);
                }
                else {
                    msg.put("itemId", itemId);
                }

                discntList.add(TradeManagerUtils.preProductInfoByElementIdProductId(msg));
            }
        }
        Map tradeDiscnt = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map boradDiscntAttr = (Map) boradDiscntInfos.get(0).get("boradDiscntAttr");
        String delayDiscntId = null;
        if (boradDiscntAttr != null) {
            delayDiscntId = (String) boradDiscntAttr.get("delayDiscntId");
        }
        int size = discntList.size();
        for (Map disMap : discntList) {
            Map itemNew = new HashMap();
            itemNew.put("xDatatype", "NULL");
            itemNew.put("id", userId);
            itemNew.put("idType", "1");
            itemNew.put("userIdA", "-1");
            itemNew.put("productId", disMap.get("productId"));
            itemNew.put("packageId", disMap.get("packageId"));
            itemNew.put("discntCode", disMap.get("discntCode"));
            itemNew.put("specTag", "0");// FIXME
            itemNew.put("relationTypeCode", "");
            itemNew.put("startDate", MapUtils.getDefault(disMap, "activityStarTime", GetDateUtils.getDate()));
            itemNew.put("endDate", MapUtils.getDefault(disMap, "activityTime", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME));
            // if ("sdpr".equals(exchange.getAppCode()) && delayDiscntId != null
            // && delayDiscntId.equals(disMap.get("discntCode") + "")) {
            // itemNew.put("modifyTag", "S");// 山东要求：到期资费（标准资费）下发S，别的下发0 ,目前只改山东的（20170114 by zzc）
            // }
            // else {
            itemNew.put("modifyTag", "0");
            // }
            if ("0076".equals(provinceCode)) {
                String id = GetSeqUtil.getSeqFromN6ess(ExchangeUtils.ofCopy(exchange, msg), "seq_item_id", eparchyCode);
                itemNew.put("itemId", MapUtils.getDefault(disMap, "itemId", id));
            }
            else {
                itemNew.put("itemId", MapUtils.getDefault(disMap, "itemId", itemId));
            }

            itemList.add(itemNew);
        }
        // 资费endDate格式转换
        for (Map itemNew : itemList) {
            itemNew.put("endDate", GetDateUtils.transDate((String) itemNew.get("endDate"), 14));
            itemNew.put("startDate", GetDateUtils.transDate((String) itemNew.get("startDate"), 14));
        }
        tradeDiscnt.put("item", itemList);
        if (itemList == null || itemList.size() == 0) {
            throw new EcAopServerBizException("9999", "tradeDiscnt节点为空");
        }

        // 下发tradeCustomer节点 针对辽宁 20170114 by cuij
        if ("mpln".equals(exchange.getAppCode()) && "0".equals(custType)) {
            Map tradeCustomer = new HashMap();
            Map itemCust = new HashMap();
            itemCust.put("xDatatype", "NULL");
            itemCust.put("sustId", custId);
            itemCust.put("custName", newUserInfo.get("certName"));
            if ("07,13,14,15,17,18,21".contains((String) newUserInfo.get("certType"))) {
                itemCust.put("custType", "1");
            }
            else {
                itemCust.put("custType", "0");
            }
            itemCust.put("custState", "0");
            itemCust.put("psptTypeCode", CertTypeChangeUtils.certTypeMall2Cbss((String) newUserInfo.get("certType")));
            itemCust.put("psptId", newUserInfo.get("certNum"));
            itemCust.put("openLimit", "0");
            itemCust.put("eparchyCode", ChangeCodeUtils.changeEparchy(msg));
            itemCust.put("cityCode", msg.get("district"));
            itemCust.put("developDepartId", msg.get("recomPersonChannelId"));
            itemCust.put("inDate", startDate);
            itemCust.put("removeTag", "0");
            itemCust.put("rsrvTag1", "2");
            tradeCustomer.put("item", itemCust);
            ext.put("tradeCustomer", tradeCustomer);
        }

        ext.put("tradeDiscnt", tradeDiscnt);

        ext.put("tradeUser", tradeUser);
        ext.put("tradeRes", tradeRes);
        ext.put("tradePayrelation", tradePayrelation);
        ext.put("tradeOther", tradeOther);
        Map tradeItems = new HashMap();
        tradeItems.put("item", tradeItem);
        ext.put("tradeItem", tradeItems);
        Map tradeSubItems = new HashMap();
        tradeSubItems.put("item", tradeSubItem);
        Map tradeSubItemsNew = new HashMap();
        tradeSubItemsNew.put("item", msg.get("tradeSubItem"));
        ext.put("tradeSubItem", tradeSubItemsNew);

        // RHQ2018082800066-增加“港澳台居民居住证”证件类型的需求 by wangmc 20181017
        msg.put("custId", custId);
        DealCertTypeUtils.dealCertType(msg, newUserInfo, ext);

        trade.put("ext", ext);
        trade.put("base", base);
        return trade;
    }

    /**
     * 处理返回信息
     */
    private Map dealReturn(Map rspInfo, Map msg, String methodCode) {
        String serialNumber = (String) msg.get("serialNumber");// 固话号码
        String userPasswd = serialNumber.substring(serialNumber.length() - 6, serialNumber.length());
        Map ret = new HashMap();
        ret.put("provOrderId", isNull(rspInfo.get("bssOrderId").toString()));
        ret.put("userPasswd", userPasswd);
        if (null != rspInfo.get("provinceOrderInfo")) {
            List<Map> provinceOrderInfoList = (List<Map>) rspInfo.get("provinceOrderInfo");
            for (Map provinceOrderInfo : provinceOrderInfoList) {
                ret.put("debutyAreaCode", provinceOrderInfo.get("shareAreaCode"));
                if (null != provinceOrderInfo.get("preFeeInfoRsp")) {
                    List<Map> feeInfo = (List<Map>) provinceOrderInfo.get("preFeeInfoRsp");
                    List<Map> newFeeInfo = new ArrayList<Map>();
                    if (0 != feeInfo.size()) {
                        for (Map fee : feeInfo) {
                            Map newFee = null;
                            // 宽带
                            if ("bsop".equals(methodCode)) {
                                newFee = dealFeeBrd(fee);
                            }
                            else {
                                newFee = dealFeeFix(fee);
                            }
                            newFeeInfo.add(newFee);
                        }
                        ret.put("feeInfo", newFeeInfo);
                    }
                }
                String totalFee = "";
                // 宽带
                if ("bsop".equals(methodCode)) {
                    totalFee = null == provinceOrderInfo.get("totalFee")
                            || "0".equals(provinceOrderInfo.get("totalFee")) ? "0" : provinceOrderInfo.get("totalFee")
                            + "0";
                }
                else {
                    totalFee = (String) provinceOrderInfo.get("totalFee");
                }

                ret.put("totalFee", totalFee);
            }
        }
        ret.put("userName", msg.get("userName"));
        return ret;
    }

    /**
     * 判断是否为空
     */
    private String isNull(String string) {
        return null == string ? "" : string;
    }

    private boolean isNull(Object obj) {
        return null == obj || "".equals(obj);
    }

    /**
     * 宽带费用的处理
     */
    private Map dealFeeBrd(Map oldFeeInfo) {
        Map feeInfo = new HashMap();
        feeInfo.put("feeId", isNull((String) oldFeeInfo.get("feeTypeCode")));
        feeInfo.put("feeCategory", isNull((String) oldFeeInfo.get("feeMode")));
        feeInfo.put("feeDes", isNull((String) oldFeeInfo.get("feeTypeName")));
        feeInfo.put(
                "maxRelief",
                null == oldFeeInfo.get("maxDerateFee") || "0".equals(oldFeeInfo.get("maxDerateFee")) ? "0" : oldFeeInfo
                        .get("maxDerateFee") + "0");
        feeInfo.put("origFee",
                null == oldFeeInfo.get("fee") || "0".equals(oldFeeInfo.get("fee")) ? "0" : oldFeeInfo.get("fee") + "0");
        return feeInfo;
    }

    /**
     * 固话费用的处理
     */
    private Map dealFeeFix(Map oldFeeInfo) {
        Map feeInfo = new HashMap();
        feeInfo.put("feeId", isNull((String) oldFeeInfo.get("feeTypeCode")));
        feeInfo.put("feeCategory", isNull((String) oldFeeInfo.get("feeMode")));
        feeInfo.put("feeDes", isNull((String) oldFeeInfo.get("feeTypeName")));
        feeInfo.put("maxRelief", isNull((String) oldFeeInfo.get("maxDerateFee")));
        feeInfo.put("origFee", isNull(oldFeeInfo.get("fee").toString()));
        return feeInfo;
    }

    /**
     * 封装不带日期的服务属性
     */
    public Map createTradeSubItemFore(Object key, Object value, Object itemId) {
        return MapUtils.asMap("attrTypeCode", "2", "attrCode", key, "attrValue", value, "itemId", itemId);
    }

    /**
     * 判断是否为空或"null",并且为空时付默认值
     */
    private String isNULL(String string, String value) {
        if (string == null || "null".equals(string)) {
            return value;
        }
        else {
            return string;
        }
    }

    /**
     * 服务去重
     */
    private void uniqueService(List<Map> list) {
        List<String> temp = new ArrayList();// 用于存放重复的服务ID
        for (int index = 0; index < list.size(); index++) {
            boolean flag = false;
            for (int i = 0; i < temp.size(); i++) {
                if ((list.get(index).get("serviceId") + "").equals(temp.get(i) + "")) {
                    flag = true;
                }
            }
            if (flag) {
                list.remove(index);// 根据元素下标删除重复元素
                --index;
            }
            else {
                temp.add(list.get(index).get("serviceId") + "");// 不重复的，塞进用于对比的集合
            }
        }
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

    public static Map preProductInfoForBroadBand(List<Map> productInfo, Object provinceCode, Map msg) {

        LanOpenApp4GDao dao = new LanOpenApp4GDao();

        List<Map> productTypeList = new ArrayList<Map>();
        List<Map> productList = new ArrayList<Map>();

        List<Map> discntList = new ArrayList<Map>();
        List<Map> svcList = new ArrayList<Map>();
        List<Map> spList = new ArrayList<Map>();
        String monthLasttDay = "20501231235959";
        String monthFirstDay = GetDateUtils.getDate();
        Map ext = new HashMap();
        String methodCode = msg.get("methodCode") + "";
        Map activityTimeMap = new HashMap();
        String isFinalCode = "";

        // 处理产品
        for (int i = 0; i < productInfo.size(); i++) {
            List<Map> packageElement = (List<Map>) productInfo.get(i).get("packageElement");
            String productMode = String.valueOf(productInfo.get(i).get("productMode"));
            String firstMonBillMode = String.valueOf(productInfo.get(i).get("firstMonBillMode"));
            String productId = String.valueOf(productInfo.get(i).get("productId"));
            String isIpOrInterTv = "";
            if (null != productInfo.get(i).get("isIpOrInterTv")) {
                isIpOrInterTv = (String) productInfo.get(i).get("isIpOrInterTv");
            }
            if ("0".equals(productMode)) {
                System.out.println("===========主产品产品处理");
                Map productTpye = new HashMap();
                Map product = new HashMap();

                String commodityId = productId;
                String strBrandCode = "";
                String strProductTypeCode = "";
                String strProductMode = "";
                String mainProMonthFirstDay = "";
                String mainProMonthLasttDay = "";

                Map proparam = new HashMap();
                proparam.put("PROVINCE_CODE", provinceCode);
                proparam.put("COMMODITY_ID", commodityId);
                proparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                proparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                List<Map> productInfoResult = null;
                // 针对山东沃受理系统3G宽带单装，默认下发的服务全部改为不下发（秦聪，刘红军让改），原因是：北六E有超限限制
                System.out.println("zzc;;;;" + msg.get("appCodeAndMethodCode") + "    "
                        + "sdprbsop3G".equals(msg.get("appCodeAndMethodCode") + ""));
                if ("sdprbsop3G".equals(msg.get("appCodeAndMethodCode") + "")) {
                    productInfoResult = dao.queryProductInfoForSD(proparam);
                    System.out.println("zzc;;;产品集" + productInfoResult);
                }
                else {
                    productInfoResult = dao.queryProductInfo(proparam);
                    if (productInfoResult == null || productInfoResult.size() == 0) {
                        throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品信息");
                    }
                }
                if ("mvoa".equals(methodCode) || "mofc".equals(methodCode) || "234s".equals(methodCode)) {
                    if (productInfoResult != null && productInfoResult.size() > 0) {
                        productInfoResult = chooseSpeed(productInfoResult, msg.get("phoneSpeedLevel") + "");
                    }
                }
                if (productInfoResult != null && productInfoResult.size() > 0) {
                    productId = String.valueOf(productInfoResult.get(0).get("PRODUCT_ID"));
                    if (productId.equals("-1")) {
                        productId = String.valueOf(productInfoResult.get(1).get("PRODUCT_ID"));
                    }
                }
                // 范学民
                isFinalCode = getEndDate(productId);
                if ("N".equals(isFinalCode) || "X".equals(isFinalCode)) {
                    Map productDate = getEffectTimeForMainPro(productId, monthFirstDay, monthLasttDay);
                    mainProMonthFirstDay = (String) productDate.get("monthFirstDay");
                    mainProMonthLasttDay = (String) productDate.get("monthLasttDay");
                }
                else {
                    String activityTime = (String) activityTimeMap.get("monthLasttDay");
                    if (StringUtils.isEmpty(activityTime)) {
                        throw new EcAopServerBizException("9999", "所选主产品" + commodityId + "生失效时间需要和合约保持一致，"
                                + "请检查合约信息是否已传或更换主产品信息");
                    }
                    mainProMonthFirstDay = (String) activityTimeMap.get("monthFirstDay");
                    mainProMonthLasttDay = (String) activityTimeMap.get("monthLasttDay");
                }

                Map itparam = new HashMap();
                itparam.put("PROVINCE_CODE", provinceCode);
                itparam.put("PID", productId);
                itparam.put("COMMODITY_ID", commodityId);
                itparam.put("PTYPE", "U");
                List<Map> productItemInfoResult = dao.queryProductItemInfo(itparam);
                if (productItemInfoResult == null || productItemInfoResult.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品属性信息");
                }
                if (productInfoResult != null && productInfoResult.size() > 0) {
                    for (int j = 0; j < productInfoResult.size(); j++) {

                        if ("D".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map dis = new HashMap();
                            dis.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                            dis.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                            dis.put("discntCode", productInfoResult.get(j).get("ELEMENT_ID"));
                            dis.put("productMode", productInfoResult.get(j).get("PRODUCT_MODE"));
                            if (!"Y".equals(isFinalCode)) {
                                Map discntDateMap = getDiscntEffectTimeForMainPro(
                                        String.valueOf(productInfoResult.get(j).get("ELEMENT_ID")),
                                        mainProMonthFirstDay, mainProMonthLasttDay);
                                dis.put("activityStarTime", discntDateMap.get("monthFirstDay"));
                                dis.put("activityTime", discntDateMap.get("monthLasttDay"));
                            }
                            discntList.add(dis);
                        }
                        if ("S".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map svc = new HashMap();
                            svc.put("serviceId", productInfoResult.get(j).get("ELEMENT_ID"));
                            svc.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                            svc.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));

                            svcList.add(svc);
                        }
                        if ("X".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            String spId = "-1";
                            String partyId = "-1";
                            String spProductId = "-1";
                            Map spItemParam = new HashMap();
                            spItemParam.put("PTYPE", "X");
                            spItemParam.put("COMMODITY_ID", commodityId);
                            spItemParam.put("PROVINCE_CODE", provinceCode);
                            spItemParam.put("PID", productInfoResult.get(j).get("ELEMENT_ID"));
                            List<Map> spItemInfoResult = dao.queryProductItemInfo(spItemParam);
                            if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                        + productInfoResult.get(j).get("ELEMENT_ID") + "】的产品属性信息");
                            }

                            for (int l = 0; l < spItemInfoResult.size(); l++) {
                                Map spItemInfo = spItemInfoResult.get(l);
                                if ("SP_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                    spId = (String) (spItemInfo.get("ATTR_VALUE"));
                                }
                                if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                    partyId = (String) (spItemInfo.get("ATTR_VALUE"));
                                }
                                if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                    spProductId = (String) (spItemInfo.get("ATTR_VALUE"));
                                }
                            }

                            Map sp = new HashMap();
                            sp.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                            sp.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                            sp.put("partyId", partyId);
                            sp.put("spId", spId);
                            sp.put("spProductId", spProductId);
                            sp.put("spServiceId", productInfoResult.get(j).get("ELEMENT_ID"));
                            spList.add(sp);
                        }

                    }
                }
                if (productInfoResult != null && productInfoResult.size() > 0) {
                    strProductMode = String.valueOf(productInfoResult.get(0).get("PRODUCT_MODE"));
                }
                strBrandCode = getValueFromItem("BRAND_CODE", productItemInfoResult);
                strProductTypeCode = getValueFromItem("PRODUCT_TYPE_CODE", productItemInfoResult);

                productTpye.put("productMode", "00");// 山东北六E--这个目前都是00
                productTpye.put("productId", productId);
                productTpye.put("productTypeCode", strProductTypeCode);
                product.put("brandCode", strBrandCode);
                product.put("productId", productId);
                product.put("productMode", "00");// 山东北六E--这个目前都是00

                productTypeList.add(productTpye);
                productList.add(product);
                ext.put("brandCode", strBrandCode);
                ext.put("productTypeCode", strProductTypeCode);
                ext.put("mproductId", productId);
                // 附加包
                if (packageElement != null && packageElement.size() > 0) {
                    for (int n = 0; n < packageElement.size(); n++) {
                        Map peparam = new HashMap();
                        peparam.put("PROVINCE_CODE", provinceCode);
                        peparam.put("COMMODITY_ID", productId);
                        peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                        peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                        peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                        List<Map> packageElementInfo = null;
                        if ("sdprbsop3G".equals(msg.get("appCodeAndMethodCode") + "")) {
                            packageElementInfo = dao.queryPackageElementInfoForSD(peparam);
                        }
                        else {
                            packageElementInfo = dao.queryPackageElementInfo(peparam);
                        }

                        System.out.println("走进来了么？？？？？？？" + packageElementInfo);
                        if (packageElementInfo != null && packageElementInfo.size() > 0) {
                            for (Map packageMap : packageElementInfo) {
                                if ("D".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    String elementId = String.valueOf(packageMap.get("ELEMENT_ID"));
                                    Map dis = new HashMap();
                                    dis.put("productId", packageMap.get("PRODUCT_ID"));
                                    dis.put("packageId", packageMap.get("PACKAGE_ID"));
                                    dis.put("discntCode", elementId);
                                    dis.put("productMode", packageMap.get("PRODUCT_MODE"));
                                    Map discntDateMap = getDiscntEffectTime(elementId, monthFirstDay, monthLasttDay);
                                    dis.put("activityStarTime", discntDateMap.get("monthFirstDay"));
                                    dis.put("activityTime", discntDateMap.get("monthLasttDay"));
                                    discntList.add(dis);
                                }
                                if ("S".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    Map svc = new HashMap();
                                    svc.put("serviceId", packageMap.get("ELEMENT_ID"));
                                    svc.put("productId", packageMap.get("PRODUCT_ID"));
                                    svc.put("packageId", packageMap.get("PACKAGE_ID"));
                                    svcList.add(svc);
                                }
                                if ("X".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    String spId = "-1";
                                    String partyId = "-1";
                                    String spProductId = "-1";
                                    Map spItemParam = new HashMap();
                                    spItemParam.put("PTYPE", "X");
                                    spItemParam.put("COMMODITY_ID", productId);
                                    spItemParam.put("PROVINCE_CODE", provinceCode);
                                    spItemParam.put("PID", packageMap.get("ELEMENT_ID"));
                                    List<Map> spItemInfoResult = dao.queryProductItemInfo(spItemParam);
                                    if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                        throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                                + packageMap.get("ELEMENT_ID") + "】的产品属性信息");
                                    }
                                    for (int j = 0; j < spItemInfoResult.size(); j++) {
                                        Map spItemInfo = spItemInfoResult.get(j);
                                        if ("SP_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                            spId = (String) (spItemInfo.get("ATTR_VALUE"));
                                        }
                                        else if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                            partyId = (String) (spItemInfo.get("ATTR_VALUE"));
                                        }
                                        else if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                            spProductId = (String) (spItemInfo.get("ATTR_VALUE"));
                                        }
                                    }
                                    Map sp = new HashMap();
                                    sp.put("productId", packageMap.get("PRODUCT_ID"));
                                    sp.put("packageId", packageMap.get("PACKAGE_ID"));
                                    sp.put("partyId", partyId);
                                    sp.put("spId", spId);
                                    sp.put("spProductId", spProductId);
                                    sp.put("spServiceId", packageMap.get("ELEMENT_ID"));
                                    spList.add(sp);
                                }
                            }
                        }
                    }
                }
            }

        }
        dealRepeat(discntList);
        dealRepeat(svcList);
        dealRepeat(spList);
        dealRepeat(productTypeList);
        dealRepeat(productList);

        msg.put("discntList", discntList);
        msg.put("svcList", svcList);
        msg.put("spList", spList);
        msg.put("productTypeList", productTypeList);
        msg.put("productList", productList);
        // 增加活动结束时间
        // msg.put("activityTime", monthLasttDay);

        OpenApply4GProcessor openApplyPro = new OpenApply4GProcessor();
        ext.put("tradeProductType", openApplyPro.preProductTpyeListData(msg));
        ext.put("tradeProduct", openApplyPro.preProductData(msg));
        ext.put("tradeDiscnt", openApplyPro.preDiscntData(msg));
        ext.put("tradeSvc", openApplyPro.preTradeSvcData(msg));
        ext.put("tradeSp", openApplyPro.preTradeSpData(msg));
        return ext;
    }

    /**
     * 根据ATTR_CODE取出相应的ATTR_VALUE
     * @param attrCode
     * @param infoList
     * @return
     */
    public static String getValueFromItem(String attrCode, List<Map> infoList) {
        String attrValue = "";
        for (int i = 0; i < infoList.size(); i++) {
            Map productItemInfo = infoList.get(i);
            if ("U".equals(productItemInfo.get("PTYPE"))) {
                if (attrValue == "" && attrCode.equals(productItemInfo.get("ATTR_CODE"))) {
                    attrValue = (String) productItemInfo.get("ATTR_VALUE");
                }
            }
        }
        return attrValue;
    }

    public static List<Map> chooseSpeed(List<Map> productInfo, String speed) {
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
        for (int m = 0; m < productInfo.size(); m++) {
            if (kick1st.equals(String.valueOf(productInfo.get(m).get("ELEMENT_ID")))
                    || kick2nd.equals(String.valueOf(productInfo.get(m).get("ELEMENT_ID")))) {
                newProductInfo.add(productInfo.get(m));
            }
        }
        productInfo.removeAll(newProductInfo);
        return productInfo;
    }

    /**
     * 处理附加产品或者活动的失效时间
     * 可以手动传入产品或活动的生失效时间
     */
    public static Map getEffectTimeForMainPro(String actPlanId, String monthFirstDay, String monthLasttDay) {
        String monthLastDay = "";
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        Map actiPeparam = new HashMap();
        actiPeparam.put("PRODUCT_ID", actPlanId);
        List<Map> activityList = dao.queryProductAndPtypeProduct(actiPeparam);
        Map activityListMap = null;
        if (activityList != null && activityList.size() > 0) {
            activityListMap = activityList.get(0);

        }
        else {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到ID为【" + actPlanId + "】的信息");

        }
        try {
            String resActivityper = String.valueOf(activityListMap.get("END_OFFSET"));
            String enableTag = String.valueOf(activityListMap.get("ENABLE_TAG"));// 0-绝对时间（看开始时间） 1-相对时间
            String endEnableTag = String.valueOf(activityListMap.get("END_ENABLE_TAG"));// 0-绝对时间（看开始时间） 1-相对时间
            System.out.println("endEnableTag=============" + endEnableTag);
            String endDate = String.valueOf(activityListMap.get("END_ABSOLUTE_DATE"));// END_ENABLE_TAG为1时，需要才发此结束时间
            String strStartUnit = String.valueOf(activityListMap.get("START_UNIT"));// '0:天 1:自然天 2:月 3:自然月 4:年
            // 5:自然年';
            String endUnit = String.valueOf(activityListMap.get("END_UNIT"));
            String startOffset = "0";// 生效偏移时间

            // 如果值为空则进行默认处理
            if (!"null".equals(enableTag) && !"null".equals(startOffset) && !"null".equals(strStartUnit)) {
                monthFirstDay = GetDateUtils.getSpecifyDateTime(Integer.parseInt(enableTag),
                        Integer.parseInt(strStartUnit), GetDateUtils.getSysdateFormat(), Integer.parseInt(startOffset));
            }
            if (!"null".equals(enableTag) && "0".equals(enableTag)) {
                startOffset = "0";
            }
            // 如果值为空则进行默认处理
            if (!"null".equals(endEnableTag) && "0".equals(endEnableTag) && !"null".equals(endDate)) {
                monthLasttDay = endDate;
            }
            if (!"null".equals(resActivityper) && !"null".equals(endUnit) && !"null".equals(endEnableTag)
                    && "1".equals(endEnableTag)) {
                monthLastDay = GetDateUtils.getSpecifyDateTime(Integer.parseInt(endEnableTag),
                        Integer.parseInt(endUnit), GetDateUtils.getSysdateFormat(), Integer.parseInt(resActivityper)
                                + Integer.parseInt(startOffset));
                // 结束月最后一天
                monthLasttDay = GetDateUtils.getSpecifyDateTime(1, 6, monthLastDay, -1);
                monthLasttDay = GetDateUtils.transDate(monthLasttDay, 14);

            }
            if (monthLasttDay.length() > 19) {
                monthLasttDay = monthLasttDay.substring(0, 19);
            }
            actiPeparam.put("monthLasttDay", monthLasttDay);
            actiPeparam.put("monthFirstDay", monthFirstDay);
            actiPeparam.put("resActivityper", resActivityper);
            return actiPeparam;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "获取产品或者活动信息生失效时间失败，产品是：" + actPlanId);
        }
    }

    /**
     * 该方法用于校验附加产品的失效时间
     * 当返回值是Y时表示 附加产品的失效时间应该和合约保持一致
     * 当返回值是N时表示附加产品的失效时间是固定值需要自己计算
     * 返回值写死是X时，写死默认时间
     * 针对融合产品是必须传入对应cb侧的产品编码
     */
    public static String getEndDate(String addProductId) {
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        Map actProParam = new HashMap();
        String lengthType = "";
        actProParam.put("PRODUCT_ID", addProductId);
        List actProductInfo = dao.queryAddProductEndDate(actProParam);
        if (actProductInfo == null || actProductInfo.size() == 0) {
            lengthType = "X";
            return lengthType;
        }
        lengthType = (String) actProductInfo.get(0);
        return lengthType;
    }

    /**
     * 处理附加产品资费的生失效时间
     * 可以手动传入产品或活动的生失效时间
     */
    public static Map getDiscntEffectTimeForMainPro(String discntId, String monthFirstDay, String monthLasttDay) {
        String monthLastDay = "";
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        Map actiPeparam = new HashMap();
        actiPeparam.put("DISCNT_CODE", discntId);
        List<Map> activityList = dao.queryDiscntDate(actiPeparam);
        Map activityListMap = null;
        if (activityList != null && activityList.size() > 0) {
            activityListMap = activityList.get(0);

        }
        else {
            throw new EcAopServerBizException("9999", "在资费信息表中未查询到ID为【" + discntId + "】的资费信息");

        }
        try {
            String resActivityper = String.valueOf(activityListMap.get("END_OFFSET"));// 失效效偏移时间
            String endUnit = String.valueOf(activityListMap.get("END_UNIT"));// '0:天 1:自然天 2:月 3:自然月 4:年
            // 5:自然年';
            String endEnableTag = String.valueOf(activityListMap.get("END_ENABLE_TAG"));// 针对结束时间 0-绝对时间（看开始时间） 1-相对时间
            System.out.println("endEnableTag=============" + endEnableTag);
            String endDate = String.valueOf(activityListMap.get("END_ABSOLUTE_DATE"));// END_ENABLE_TAG为1时，需要才发此结束时间

            String enableTag = String.valueOf(activityListMap.get("ENABLE_TAG"));// 针对开始时间 0-绝对时间（看开始时间） 1-相对时间
            String strStartUnit = String.valueOf(activityListMap.get("START_UNIT"));// '0:天 1:自然天 2:月 3:自然月 4:年
            // 5:自然年';
            String startOffset = "0";// 生效偏移时间
            // 如果值为空则进行默认处理
            if (!"null".equals(enableTag) && "1".equals(enableTag) && !"null".equals(startOffset)
                    && !"null".equals(strStartUnit)) {
                monthFirstDay = GetDateUtils.getSpecifyDateTime(Integer.parseInt(enableTag),
                        Integer.parseInt(strStartUnit), GetDateUtils.getSysdateFormat(), Integer.parseInt(startOffset));
            }
            if (!"null".equals(enableTag) && "0".equals(enableTag)) {
                startOffset = "0";
            }
            if (!"null".equals(resActivityper) && !"null".equals(endUnit) && !"null".equals(endEnableTag)
                    && "1".equals(endEnableTag)) {
                monthLastDay = GetDateUtils.getSpecifyDateTime(Integer.parseInt(endEnableTag),
                        Integer.parseInt(endUnit), GetDateUtils.getSysdateFormat(), Integer.parseInt(resActivityper)
                                + Integer.parseInt(startOffset));
                // 结束月最后一天
                monthLasttDay = GetDateUtils.getSpecifyDateTime(1, 6, monthLastDay, -1);
                monthLasttDay = GetDateUtils.transDate(monthLasttDay, 14);
            }
            if (monthLasttDay.length() > 19) {
                monthLasttDay = monthLasttDay.substring(0, 19);
            }
            actiPeparam.put("monthLasttDay", monthLasttDay);
            actiPeparam.put("monthFirstDay", monthFirstDay);
            return actiPeparam;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "获取资费信息生失效时间失败，资费是：" + discntId);
        }

    }

    /**
     * 处理附加产品资费的生失效时间
     * 可以手动传入产品或活动的生失效时间
     */
    public static Map getDiscntEffectTime(String discntId, String monthFirstDay, String monthLasttDay) {
        String monthLastDay = "";
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        Map actiPeparam = new HashMap();
        actiPeparam.put("DISCNT_CODE", discntId);
        List<Map> activityList = dao.queryDiscntDate(actiPeparam);
        Map activityListMap = null;
        if (activityList != null && activityList.size() > 0) {
            activityListMap = activityList.get(0);

        }
        else {
            throw new EcAopServerBizException("9999", "在资费信息表中未查询到ID为【" + discntId + "】的资费信息");

        }
        try {
            String resActivityper = String.valueOf(activityListMap.get("END_OFFSET"));// 失效效偏移时间
            String endUnit = String.valueOf(activityListMap.get("END_UNIT"));// '0:天 1:自然天 2:月 3:自然月 4:年
            // 5:自然年';
            String endEnableTag = String.valueOf(activityListMap.get("END_ENABLE_TAG"));// 针对结束时间 0-绝对时间（看开始时间） 1-相对时间
            System.out.println("endEnableTag=============" + endEnableTag);
            String endDate = String.valueOf(activityListMap.get("END_ABSOLUTE_DATE"));// END_ENABLE_TAG为1时，需要才发此结束时间

            String enableTag = String.valueOf(activityListMap.get("ENABLE_TAG"));// 针对开始时间 0-绝对时间（看开始时间） 1-相对时间
            String strStartUnit = String.valueOf(activityListMap.get("START_UNIT"));// '0:天 1:自然天 2:月 3:自然月 4:年
            // 5:自然年';
            String startOffset = String.valueOf(activityListMap.get("START_OFFSET"));// 生效偏移时间
            // 如果值为空则进行默认处理
            if (!"null".equals(enableTag) && "1".equals(enableTag) && !"null".equals(startOffset)
                    && !"null".equals(strStartUnit)) {
                monthFirstDay = GetDateUtils.getSpecifyDateTime(Integer.parseInt(enableTag),
                        Integer.parseInt(strStartUnit), GetDateUtils.getSysdateFormat(), Integer.parseInt(startOffset));
            }
            if (!"null".equals(enableTag) && "0".equals(enableTag)) {
                startOffset = "0";
            }
            if (!"null".equals(resActivityper) && !"null".equals(endUnit) && !"null".equals(endEnableTag)
                    && "1".equals(endEnableTag)) {
                monthLastDay = GetDateUtils.getSpecifyDateTime(Integer.parseInt(endEnableTag),
                        Integer.parseInt(endUnit), GetDateUtils.getSysdateFormat(), Integer.parseInt(resActivityper)
                                + Integer.parseInt(startOffset));
                // 结束月最后一天
                monthLasttDay = GetDateUtils.getSpecifyDateTime(1, 6, monthLastDay, -1);
                monthLasttDay = GetDateUtils.transDate(monthLasttDay, 14);
            }
            if (monthLasttDay.length() > 19) {
                monthLasttDay = monthLasttDay.substring(0, 19);
            }
            actiPeparam.put("monthLasttDay", monthLasttDay);
            actiPeparam.put("monthFirstDay", monthFirstDay);
            return actiPeparam;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "获取资费信息生失效时间失败，资费是：" + discntId);
        }

    }

    public static List<Map> dealRepeat(List<Map> listMap) {
        List<Map> listTemp = new ArrayList<Map>();
        Iterator<Map> it = listMap.iterator();
        while (it.hasNext()) {
            Map a = it.next();
            if (listTemp.contains(a)) {
                it.remove();
            }
            else {
                listTemp.add(a);
            }
        }
        return listMap;

    }

}
