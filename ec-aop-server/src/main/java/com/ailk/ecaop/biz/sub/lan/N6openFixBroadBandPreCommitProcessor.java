package com.ailk.ecaop.biz.sub.lan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("N6openFixBroadBandPreCommit")
public class N6openFixBroadBandPreCommitProcessor extends BaseAopProcessor {

    private String provinceCode = "";
    String eparchyCode;
    String discntId;

    // zzc 固话单装北六E
    @Override
    public void process(Exchange exchange) throws Exception {

        // 初始化省份
        provinceCode = "00" + getMsg(exchange).get("province");

        // 处理参数
        Map preMap = dealParas(exchange);

        // 预提交
        callN6(exchange, preMap);

        // 处理返回
        dealResult(exchange);

    }

    /**
     * @param exchange
     */
    private Map getMsg(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        Object msgObject = body.get("msg");
        // 获取msg
        Map msg = null;
        if (msgObject instanceof String) {
            msg = JSON.parseObject(msgObject.toString());
        }
        else {
            msg = (Map) msgObject;
        }
        return msg;

    }

    private void callN6(Exchange exchange, Map preMap) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = getMsg(exchange);
        LanUtils lan = new LanUtils();
        msg.put("base", preMap.get("base"));
        msg.put("ext", preMap.get("ext"));
        msg.put("ordersId", msg.get("orderNo"));
        msg.put("operTypeCode", "0");
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        // 固话预提交
        lan.preData("ecaop.masb.sfso.N6.sglUniTradeParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.n6ess.services.AOPOrdForNorthSer" + "." + msg.get("province"));
        lan.xml2Json("ecaop.masb.sfso.sglUniTradeTemplate", exchange);
        exchange.getIn().setBody(body);
    }

    // 处理返回结果
    private void dealResult(Exchange exchange) {
        String methodCode = exchange.getMethodCode();
        Map msg = getMsg(exchange);
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
    private Map dealParas(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = getMsg(exchange);
        Map newUserInfo = (Map) msg.get("newUserInfo");
        List<Map> productInfo = new ArrayList<Map>();
        Map ext = new HashMap();
        String installAddress = "";
        String tradeId = "";
        String itemId = "";
        String custId = "";
        String userId = "";
        String acctId = "";

        // 赋值
        setParas(exchange, tradeId, custId, userId, acctId, itemId, installAddress, productInfo);

        // 调用处理产品方法
        ext = getProductInfo(productInfo, msg);

        // 这块逻辑有疑问
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

        // 主资费处理
        dealMainDis(newUserInfo, msg, productId);

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
        String olcomTag = "0";
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
        String payName = (String) msg.get("userName");
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
        String payrelationId = "132";
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
        // base.put("cityCode", cityCode);
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
        base.put("contact", "123456798");
        base.put("contactPhone", "");
        base.put("contactAddress", "");
        base.put("remark", "1346789745646");
        base.put("productSpec", "1"); // 不确定
        base.put("netTypeCode", "0030");
        // tredeAcct
        Map tradeAcct = new HashMap();
        Map item1 = new HashMap();
        item1.put("xDataType", xDataType);
        item1.put("acctId", acctId);
        item1.put("payName", payName);
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
        item2.put("cityCode", cityCodeNew);
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
        List<Map> tradeRe = new ArrayList<Map>();
        Map itemRes1 = new HashMap();
        itemRes1.put("xDataType", "NULL");
        itemRes1.put("reTypeCode", "0");
        itemRes1.put("resCode", resCode);
        itemRes1.put("modifyTag", "0");
        itemRes1.put("startDate", startDate);
        itemRes1.put("endDate", endDate);
        tradeRe.add(itemRes1);

        Map itemRes2 = new HashMap();
        itemRes2.put("xDataType", "NULL");
        itemRes2.put("reTypeCode", "1");
        itemRes2.put("resCode", resCode);
        itemRes2.put("resInfo1", resCode);
        itemRes2.put("modifyTag", "0");
        tradeRe.add(itemRes2);
        tradeRes.put("item", tradeRe);
        System.out.println(itemRes1 + "***" + itemRes2 + "***" + tradeRes);

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

        // tradeitem
        List<Map> tradeItem = new ArrayList<Map>();
        LanUtils lan = new LanUtils();
        tradeItem.add(lan.createTradeItem("DEVELOP_STAFF_ID", developStaffId));
        tradeItem.add(lan.createTradeItem("DEVELOP_DEPART_ID", developDepartId));
        tradeItem.add(lan.createTradeItem("STANDARD_KIND_CODE", channelType));
        tradeItem.add(lan.createTradeItem("PH_NUM", serialNumber));
        tradeItem.add(lan.createTradeItem("USER_PASSWD", userPassword));
        tradeItem.add(lan.createTradeItem("NEW_PASSWD", userPassword));

        // tradesubitem开始
        List<Map> tradeSubItem = new ArrayList<Map>();
        // 固话独有item
        tradeSubItem.add(lan.createTradeSubItem("IS_FATE", "0", userId));
        tradeSubItem.add(lan.createTradeSubItem("QZLX_PTDH", "", userId));
        tradeSubItem.add(lan.createTradeSubItem("C_2202", "", userId));
        tradeSubItem.add(lan.createTradeSubItem("ENTRY_ADDR", "", userId));
        tradeSubItem.add(lan.createTradeSubItem("PUBLISH_NAME", "", userId));

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
        tradeSubItem.add(lan.createTradeSubItem("AREA_CODE", msg.get("district"), userId));
        tradeSubItem
                .add(lan.createTradeSubItem("UserNature", isNULL(newUserInfo.get("userProperty") + "", ""), userId));
        tradeSubItem.add(lan.createTradeSubItem("USER_CONTACT_PHONE", msg.get("contactPhone"), userId));
        tradeSubItem.add(lan.createTradeSubItem("TerminalSource", machineProvide, userId));
        tradeSubItem
                .add(lan.createTradeSubItem("USER_TYPE_CODE", isNULL(newUserInfo.get("userType") + "", "1"), userId));
        tradeSubItem.add(lan.createTradeSubItem("JIE_RU_FANG_SHI", newUserInfo.get("accessMode"), userId));
        tradeSubItem.add(lan.createTradeSubItem("INSTALL_ADDRESS", newUserInfo.get("installAddress"), userId));
        tradeSubItem.add(lan.createTradeSubItem("ConformPassWord", isNULL(msg.get("userPasswd") + "", ""), userId));
        tradeSubItem.add(lan.createTradeSubItem("LINK_NAME", msg.get("contactPerson"), userId));
        tradeSubItem.add(lan.createTradeSubItem("ADDRESS_ID", isNULL(newUserInfo.get("addressCode") + "", ""), userId));
        tradeSubItem.add(lan.createTradeSubItem("USER_PASSWD", isNULL(msg.get("userPasswd") + "", ""), userId));
        tradeSubItem.add(lan.createTradeSubItem("MOFFICE_ID", isNULL(newUserInfo.get("exchCode") + "", ""), userId));
        tradeSubItem.add(lan.createTradeSubItem("PassWord", isNULL(msg.get("userPasswd") + "", ""), userId));
        tradeSubItem.add(lan.createTradeSubItem("USER_CALLING_AREA", eparchyCode, userId));
        tradeSubItem.add(lan.createTradeSubItem("ChargeType", isNULL(newUserInfo.get("acceptMode") + "", "1"), userId));
        tradeSubItem.add(lan.createTradeSubItem("DETAIL_INSTALL_ADDRESS", newUserInfo.get("installAddress"), userId));
        tradeSubItem.add(lan.createTradeSubItem("Speed", isNULL(newUserInfo.get("speedLevel") + "", ""), userId));
        tradeSubItem.add(lan.createTradeSubItem("USER_NAME", newUserInfo.get("certName"), userId));
        msg.put("tradeSubItem", tradeSubItem);
        // tradesubitem处理结束

        Map trade = new HashMap();
        // tradeDiscnt节点
        List<Map> discntList = (List<Map>) msg.get("discntList");
        Map tradeDiscnt = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
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
            itemNew.put("modifyTag", "0");
            itemNew.put("itemId", MapUtils.getDefault(disMap, "itemId", itemId));
            itemList.add(itemNew);
        }
        tradeDiscnt.put("item", itemList);
        if (itemList == null || itemList.size() == 0) {
            throw new EcAopServerBizException("9999", "tradeDiscnt节点为空");
        }
        Map tradeItems = new HashMap();
        Map tradeSubItems = new HashMap();
        tradeSubItems.put("item", msg.get("tradeSubItem"));

        // 拼装ext
        ext.put("tradeDiscnt", tradeDiscnt);
        ext.put("tradeAcct", tradeAcct);
        ext.put("tradeUser", tradeUser);
        ext.put("tradeRes", tradeRes);
        ext.put("tradePayrelation", tradePayrelation);
        ext.put("tradeOther", tradeOther);
        ext.put("tradeItem", tradeItems);
        ext.put("tradeSubItem", tradeSubItems);

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
                            newFee = dealFeeFix(fee);
                            newFeeInfo.add(newFee);
                        }
                        ret.put("feeInfo", newFeeInfo);
                    }
                }
                String totalFee = "";

                totalFee = (String) provinceOrderInfo.get("totalFee");

                ret.put("totalFee", totalFee);
            }
        }
        ret.put("userName", msg.get("userName"));
        return ret;
    }

    /**
     * 基础参数赋值
     * @param exchange
     * @param tradeId
     * @param custId
     * @param userId
     * @param acctId
     * @param itemId
     * @param installAddress
     * @param productInfo
     */
    private void setParas(Exchange exchange, String tradeId, String custId, String userId, String acctId,
            String itemId, String installAddress, List<Map> productInfo) {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = getMsg(exchange);
        Map newUserInfo = (Map) msg.get("newUserInfo");
        // 获取流水
        eparchyCode = ChangeCodeUtils.changeEparchy(msg);
        tradeId = GetSeqUtil.getSeqFromN6ess(exchange, "TRADE_ID", eparchyCode);
        exchange.getIn().setBody(body);
        itemId = GetSeqUtil.getSeqFromN6ess(exchange, "ITEM_ID", eparchyCode);
        exchange.getIn().setBody(body);
        userId = GetSeqUtil.getSeqFromN6ess(exchange, "USER_ID", eparchyCode);
        exchange.getIn().setBody(body);
        acctId = GetSeqUtil.getSeqFromN6ess(exchange, "ACCT_ID", eparchyCode);
        exchange.getIn().setBody(body);
        // 获取客户ID
        String custType = (String) msg.get("custType");
        if ("1".equals(custType)) {// 老客户
            custId = (String) msg.get("custId");
        }
        else {
            custId = GetSeqUtil.getSeqFromN6ess(exchange, "CUST_ID", eparchyCode);
        }
        exchange.getIn().setBody(body);
        // 装机地址
        if (newUserInfo != null) {
            installAddress = (String) newUserInfo.get("installAddress");
        }
        else {
            installAddress = "00";
        }

        // 产品类型转换 start
        if (newUserInfo != null) {
            productInfo = (List) newUserInfo.get("productInfo");
        }
        else {
            throw new EcAopServerBizException("9999", "人员信息不足");
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
        // end
        msg.put("eparchyCode", eparchyCode);
    }

    /**
     * 产品处理
     * @param productInfo
     * @param msg
     * @return
     * @throws Exception
     */
    private Map getProductInfo(List<Map> productInfo, Map msg) throws Exception {
        Map ext = new HashMap();
        List<Map> tradeSvcList;
        if (productInfo != null) {
            ext = TradeManagerUtils.preProductInfo(productInfo, provinceCode, msg);
            // endDate格式转换
            tradeSvcList = (List<Map>) ((Map) ext.get("tradeSvc")).get("item");
            for (Map item : tradeSvcList) {
                item.put("endDate", GetDateUtils.transDate((String) item.get("endDate"), 14));
                item.put("startDate", GetDateUtils.transDate((String) item.get("startDate"), 14));
            }
        }

        else {
            throw new EcAopServerBizException("9999", "产品信息不足");
        }
        return ext;
    }

    /**
     * 主资费处理
     * @param newUserInfo
     * @param msg
     * @param productId
     * @throws Exception
     */
    private void dealMainDis(Map newUserInfo, Map msg, String productId) throws Exception {
        if (!IsEmptyUtils.isEmpty(newUserInfo.get("discntInfo"))) {
            List<Map> discntInfo = (List<Map>) newUserInfo.get("discntInfo");
            if (!IsEmptyUtils.isEmpty(discntInfo)) {
                Map peparam = new HashMap();
                peparam.put("PROVINCE_CODE", provinceCode);
                peparam.put("productId", productId);
                peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                peparam.put("elementId", discntInfo.get(0).get("discntId"));
                Map packageElementInfo = TradeManagerUtils.preProductInfoByElementIdProductId(peparam);
                System.out.println("peparam=" + peparam + ",==" + packageElementInfo);
                if (!IsEmptyUtils.isEmpty(packageElementInfo)) {
                    List discntList = (List) msg.get("discntList");
                    // for (int i = 0; i < packageElementInfo.size(); i++) {
                    // if ("D".equals(packageElementInfo.get(i).get("ELEMENT_TYPE_CODE"))) {
                    Map dis = new HashMap();
                    dis.put("productId", packageElementInfo.get("productId"));
                    dis.put("packageId", packageElementInfo.get("packageId"));
                    dis.put("discntCode", packageElementInfo.get("discntCode"));
                    // dis.put("productMode", packageElementInfo.get("PRODUCT_MODE"));
                    discntList.add(dis);
                    // }
                    msg.put("discntList", discntList);
                }
            }
        }
    }

    /**
     * 判断是否为空
     */
    private String isNull(String string) {
        return null == string ? "" : string;
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
}
