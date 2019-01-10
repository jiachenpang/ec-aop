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
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;

/**
 * @author Administrator
 */
@EcRocTag("B6openBroadBandPreCommit")
public class B6OpenBroadBandPreCommitProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {

        Map body = (Map) exchange.getIn().getBody();
        Map msg = (Map) body.get("msg");
        Map retMap = dealParams(exchange);
        msg.putAll(retMap);
        LanUtils lan = new LanUtils();
        // 调用预提交接口
        String userName = (String) msg.get("userName");
        String userPasswd = (String) msg.get("userPasswd");
        String ordersId = (String) msg.get("orderNo");
        String operTypeCode = "0";
        System.out.println("+++++++++++++msg：" + msg + "+++++++++++++++");
        msg.put("ordersId", ordersId);
        msg.put("operTypeCode", operTypeCode);
        try {
            lan.preData("ecaop.masb.sbac.sglUniTradeParametersMapping",
                    exchange);
            CallEngine.wsCall(exchange,
                    "ecaop.comm.conf.url.OrdForNorthSer" + "." + msg.get("province"));
            lan.xml2Json("ecaop.masb.sbac.sglUniTradeTemplate", exchange);
        }
        catch (EcAopServerBizException e) {
            throw new EcAopServerBizException("9999", "调用预提交失败" + e.getMessage());
        }
        body = (Map) exchange.getOut().getBody();
        // 整理返回报文
        Map returnMap = dealResult(exchange, userName, userPasswd);
        exchange.getOut().setBody(returnMap);

    }

    // 处理返回结果
    private Map dealResult(Exchange exchange, String userName, String userPasswd) {
        Map body = (Map) exchange.getOut().getBody();
        List rspInfos = (List) body.get("rspInfo");
        Map rspInfo = (Map) rspInfos.get(0);
        String provOrderId = (String) rspInfo.get("bssOrderId");
        List<Map> provinceOrderInfos = (List) rspInfo.get("provinceOrderInfo");
        List<Map> feeInfos = new ArrayList();
        Map retMap = new HashMap();
        for (Map provinceOrderInfo : provinceOrderInfos) {
            List<Map> m = (List) provinceOrderInfo.get("preFeeInfoRsp");
            for (Map feeInfo : m) {
                Map m1 = new HashMap();
                m1.put("feeId", feeInfo.get("feeTypeCode"));
                m1.put("feeCategory", feeInfo.get("feeMode"));
                m1.put("feeDes", feeInfo.get("feeTypeName"));
                m1.put("maxRelief", feeInfo.get("maxDerateFee"));
                m1.put("origFee", feeInfo.get("fee"));
                feeInfos.add(m1);
            }

        }
        double totalFee = 0;
        for (Map feeInfo : feeInfos) {
            String origFee = (String) feeInfo.get("origFee");
            double origFee1 = Double.parseDouble(origFee);
            totalFee += origFee1;
        }
        retMap.put("provOrderId", provOrderId);
        retMap.put("userName", userName);
        retMap.put("userPasswd", userPasswd);
        retMap.put("feeInfo", feeInfos);
        retMap.put("totalFee", totalFee);

        return retMap;
    }

    public Map dealParams(Exchange exchange) throws Exception {
        Map body = (Map) exchange.getIn().getBody();
        Map msg = (Map) body.get("msg");
        Map newUserInfo = (Map) msg.get("newUserInfo");
        // 生成Id
        String tradeId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_trade_id");
        String itemId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_item_id");
        String userId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_user_id");
        String custType = (String) msg.get("custType");
        String custId = "";
        if ("0".equals(custType)) {// 判断新老客户
            custId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_cust_id");
        }
        else {
            custId = (String) msg.get("custId");
        }
        String acctId = "";
        if (newUserInfo.get("createOrExtendsAcct").equals("1")) {// 判断是否继承老账户
            acctId = (String) newUserInfo.get("debutySerialNumber");
        }
        else {
            acctId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_acct_id");
        }

        // 调用产品方法生成ext节点
        Map ext = dealPro(exchange);
        Map tradeProduct = (Map) ext.get("tradeProduct");
        Map tradeProductType = (Map) ext.get("tradeProductType");
        System.out.println("=================tradeProduct:" + tradeProduct + "=====================");

        // base节点
        Map base = new HashMap();
        base.put("subscribeId", tradeId);
        base.put("tradeId", tradeId);
        base.put("startDate", GetDateUtils.getDate());
        base.put("endDate", "20501231000000");
        base.put("acceptDate", GetDateUtils.getDate());
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        base.put("inModeCode", "0");
        base.put("tradeTypeCode", "0010");
        base.put("productId", tradeProduct.get("productId"));
        base.put("brandCode", tradeProduct.get("brandCode"));
        base.put("userId", userId);
        base.put("custId", custId);
        base.put("usecustId", custId);
        base.put("acctId", acctId);
        base.put("userDiffCode", "00");
        base.put("netTypeCode", "0040");
        base.put("serinalNamber", msg.get("serialNumber"));
        base.put("custName", newUserInfo.get("certName"));
        base.put("termIp", "127.0.0.1");
        base.put("eparchyCode", new ChangeCodeUtils().changeEparchy(msg));
        base.put("cityCode", msg.get("district"));
        base.put("execTime", GetDateUtils.getDate());
        base.put("operFee", "0");
        base.put("foregift", "0");
        base.put("advancePay", "0");
        base.put("feeState", "");
        base.put("feeStaffId", "");
        base.put("cancelTag", "0");
        base.put("checktypeCode", "");
        base.put("chkTag", "0");
        base.put("actorName", "");
        base.put("actorCertTypeId", "");
        base.put("actorPhone", "");
        base.put("actorCertNum", "");
        base.put("contact", "");
        base.put("contactPhone", "");
        base.put("contactAddress", "");
        base.put("remark", "");
        base.put("mainDiscntCode", "");// 暂时为空

        // tradeAcct节点
        Map tradeAcct = new HashMap();
        Map itemAcct = new HashMap();
        itemAcct.put("xDatatype", "NULL");
        itemAcct.put("acctId", acctId);
        itemAcct.put("payName", newUserInfo.get("certName"));
        itemAcct.put("payModeCode", "0");
        itemAcct.put("scoreValue", "0");
        itemAcct.put("creditClassId", "0");
        itemAcct.put("basicCreditValue", "0");
        itemAcct.put("creditValue", "0");
        itemAcct.put("creditControlId", "0");
        itemAcct.put("debutyUserId", userId);
        itemAcct.put("debutyCode", msg.get("serialNumber"));
        itemAcct.put("removeTag", "0");
        itemAcct.put("openDate", GetDateUtils.getDate());
        tradeAcct.put("item", itemAcct);

        // tradeUser节点
        Map tradeUser = new HashMap();
        Map itemUser = new HashMap();
        itemUser.put("xDatatype", "NULL");
        itemUser.put("usecustId", custId);
        itemUser.put("userPasswd", msg.get("userPasswd"));
        itemUser.put("userTypeCode", "2"); // 暂时传2
        itemUser.put("scoreValue", "0");
        itemUser.put("creditClass", "0");
        itemUser.put("basicCreditValue", "0");
        itemUser.put("creditValue", "0");
        itemUser.put("acctTag", "0");
        itemUser.put("prepayTag", "0");
        itemUser.put("inDate", GetDateUtils.getDate());
        itemUser.put("openDate", GetDateUtils.getDate());
        itemUser.put("openMode", "0");
        itemUser.put("openDepartId", msg.get("channelId"));
        itemUser.put("openStaffId", msg.get("operatorId"));
        itemUser.put("inDepartId", msg.get("channelId"));
        itemUser.put("inStaffId", msg.get("operatorId"));
        itemUser.put("removeTag", "0");
        itemUser.put("userStateCodeset", "0");
        itemUser.put("mputeMonthFee", "0");
        itemUser.put("developStaffId", msg.get("recomPersonId"));
        itemUser.put("developDate", GetDateUtils.getDate());
        itemUser.put("developEparchyCode", new ChangeCodeUtils().changeEparchy(msg));
        itemUser.put("developCityCode", msg.get("district"));
        itemUser.put("developDepartId", msg.get("recomPersonChannelId"));
        itemUser.put("inNetMode", "0");
        itemUser.put("productTypeCode", tradeProductType.get("productTypeCode"));
        tradeUser.put("item", itemUser);

        // tradeDiscnt节点
        List discntList = (List) msg.get("discntList");
        List<Map> boradDiscntInfos = (List<Map>) newUserInfo.get("boradDiscntInfo");
        if (null != boradDiscntInfos) {
            for (Map map : boradDiscntInfos) {
                msg.put("elementId", map.get("boradDiscntId"));
                discntList.add(TradeManagerUtils.preProductInfoByElementId(msg));
            }
        }
        Map tradeDiscnt = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> discnt = (List<Map>) msg.get("discntList");
        for (int i = 0; i < discnt.size(); i++) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("id", msg.get("userId"));
            item.put("idType", "1");
            item.put("userIdA", "-1");
            item.put("productId", discnt.get(i).get("productId"));
            item.put("packageId", discnt.get(i).get("packageId"));
            item.put("discntCode", discnt.get(i).get("discntCode"));
            item.put("specTag", "0");// FIXME
            item.put("relationTypeCode", "");
            item.put("startDate", GetDateUtils.getDate());
            item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            item.put("modifyTag", "0");
            item.put("itemId", itemId);
            itemList.add(item);
        }
        tradeDiscnt.put("item", itemList);
        if (itemList == null || itemList.size() == 0) {
            throw new EcAopServerBizException("9999", "tradeDiscnt节点为空");
        }

        // tradeRes节点
        Map tradeRes = new HashMap();
        Map itemRes = new HashMap();
        itemRes.put("xDatatype", "NULL");
        itemRes.put("reTypeCode", "0");
        itemRes.put("resCode", msg.get("serialNumber"));
        itemRes.put("modifyTag", "0");
        itemRes.put("startDate", GetDateUtils.getDate());
        itemRes.put("endDate", "20501231000000");
        tradeRes.put("item", itemRes);

        // tradePayrelation节点
        Map tradePayrelation = new HashMap();
        Map itemPay = new HashMap();
        itemPay.put("userId", userId);
        itemPay.put("acctId", acctId);
        itemPay.put("payitemCode", "-1");
        itemPay.put("acctPriority", "0");
        itemPay.put("userPriority", newUserInfo.get("userProperty"));
        itemPay.put("bindType", "1");
        itemPay.put("defaultTag", "1");
        itemPay.put("limitType", "0");
        itemPay.put("limit", "0");
        itemPay.put("complementTag", "0");
        itemPay.put("addupMonths", "0");
        itemPay.put("addupMethod", "0");
        itemPay.put("payrelationId", GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_payrela_id"));
        itemPay.put("actTag", "1");
        tradePayrelation.put("item", itemPay);

        // tradeCustomer
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
        itemCust.put("psptTypeCode", newUserInfo.get("certType"));
        itemCust.put("psptId", newUserInfo.get("certNum"));
        itemCust.put("openLimit", "0");
        itemCust.put("eparchyCode", new ChangeCodeUtils().changeEparchy(msg));
        itemCust.put("cityCode", msg.get("district"));
        itemCust.put("developDepartId", msg.get("recomPersonChannelId"));
        itemCust.put("inDate", GetDateUtils.getDate());
        itemCust.put("removeTag", "0");
        itemCust.put("rsrvTag1", "2");
        tradeCustomer.put("item", itemCust);

        // tradeOther节点
        Map tradeOther = new HashMap();
        Map itemOther = new HashMap();
        itemOther.put("xDatatype", "NULL");
        itemOther.put("rsrvValueCode", "ZZFS");
        itemOther.put("rsrvValue", "0");
        itemOther.put("rsrvStr1", newUserInfo.get("certNum"));
        itemOther.put("rsrvStr2", "10");
        itemOther.put("rsrvStr3", "-9");
        itemOther.put("rsrvStr6", "1");
        itemOther.put("rsrvStr7", "0");
        itemOther.put("modifyTag", "0");
        itemOther.put("startDate", GetDateUtils.getDate());
        itemOther.put("endDate", "20501231000000");
        tradeOther.put("item", itemOther);

        // tradeItem节点
        List<Map> tradeItem = new ArrayList<Map>();
        LanUtils lan = new LanUtils();
        tradeItem.add(lan.createAttrInfoNoTime("MARKETING_MODE", ""));
        tradeItem.add(lan.createAttrInfoNoTime("DEVELOP_STAFF_ID", msg.get("recomPersonId")));
        tradeItem.add(lan.createAttrInfoNoTime("DEVELOP_DEPART_ID", msg.get("recomPersonChannelId")));
        tradeItem.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", (String) msg.get("channelType")));
        tradeItem.add(LanUtils.createTradeItem("PH_NUM", (String) msg.get("serialNumber")));
        tradeItem.add(LanUtils.createTradeItem("PRINT_REMARKS", ""));
        tradeItem.add(LanUtils.createTradeItem("APPL_REASON", ""));
        tradeItem.add(LanUtils.createTradeItem("REF_INFO_NBR", ""));
        tradeItem.add(LanUtils.createTradeItem("PRIORITY", "1"));
        tradeItem.add(LanUtils.createTradeItem("OTHER_REASON", ""));
        tradeItem.add(LanUtils.createTradeItem("USER_PASSWD", (String) msg.get("userPasswd")));
        tradeItem.add(LanUtils.createTradeItem("IS_IMMEDIACY", "0"));
        tradeItem.add(LanUtils.createTradeItem("C_1024", ""));
        tradeItem.add(LanUtils.createTradeItem("EXISTS_ACCT", "0"));
        tradeItem.add(LanUtils.createTradeItem("PRE_TRADE_ID_NEW", ""));
        tradeItem.add(LanUtils.createTradeItem("IMMEDIACY_INFO", "0"));
        tradeItem.add(LanUtils.createTradeItem("WAIT_TYPE", "3"));
        tradeItem.add(LanUtils.createTradeItem("WAIT_60_TRADE", "0"));
        tradeItem.add(LanUtils.createTradeItem("REOPEN_TAG", "0"));
        tradeItem.add(LanUtils.createTradeItem("NEW_PASSWD", (String) msg.get("userPasswd")));

        // tradeSubItem节点
        List<Map> tradeSubItem = new ArrayList<Map>();
        // 增加到期资费方式处理.by：cuij
        String startDate = GetDateUtils.getDate();
        String endDate = MagicNumber.CBSS_DEFAULT_EXPIRE_TIME;
        String delayType = "";
        String delayDiscntId = "";
        for (Map discntInfos : boradDiscntInfos) {
            delayType = (String) discntInfos.get("delayType");
            delayDiscntId = (String) discntInfos.get("delayDiscntId");
        }
        // 当前针对辽宁省分处理
        if ("91".equals(msg.get("province"))) {
            if ("1".equals(delayType)) {
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "expireDealMode", "a", startDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "aDiscntCode", delayDiscntId, startDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "bDiscntCode", delayDiscntId, startDate, endDate));
            }
            if ("2".equals(delayType)) {
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "expireDealMode", "b", startDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "aDiscntCode", delayDiscntId, startDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "bDiscntCode", "", startDate, endDate));
            }
            if ("3".equals(delayType)) {
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "expireDealMode", "t", startDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "aDiscntCode", delayDiscntId, startDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "bDiscntCode", "", startDate, endDate));
            }
        }
        tradeSubItem.add(lan.createTradeSubItemC(itemId, "adslType", "11", GetDateUtils.getDate(),
                "20501231000000"));
        tradeSubItem.add(lan.createTradeSubItem("C_5129", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("C_5128", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("C_5127", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("C_5126", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("C_5125", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("C_5124", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("C_5123", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("CUST_MANAGER", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("USER_TYPE_CODE", "2", itemId));
        tradeSubItem.add(lan.createTradeSubItem("C_2332", "802910", itemId)); // 暂时写死
        tradeSubItem.add(lan.createTradeSubItem("MAINT_AREA_ID", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("PRE_START_TIME", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("expect_rate", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("C_2337", "802922", itemId));// 暂时写死
        tradeSubItem.add(lan.createTradeSubItem("GXLX_2060", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("C_2091", "801844", itemId));// 暂时写死
        tradeSubItem.add(lan.createTradeSubItem("C_2092", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("INSTALL_ADDRESS", newUserInfo.get("installAddress"), itemId));
        tradeSubItem.add(lan.createTradeSubItem("C_2093", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("C_90", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("INIT_PASSWD", "0", itemId));
        tradeSubItem.add(lan.createTradeSubItem("C_10", "10001", itemId));// 暂时写死
        tradeSubItem.add(lan.createTradeSubItem("PREPAY_TAG", "0", itemId));
        tradeSubItem.add(lan.createTradeSubItem("ADDRESS_ID", newUserInfo.get("addressCode"), itemId));
        tradeSubItem.add(lan.createTradeSubItem("iswait", "N", itemId));
        tradeSubItem.add(lan.createTradeSubItem("ZHHZ", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("C_145", "145001", itemId));// 暂时写死
        tradeSubItem.add(lan.createTradeSubItem("MS_AREA_ID", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("C_785", "785001", itemId));// 暂时写死
        tradeSubItem.add(lan.createTradeSubItem("SHARE_NBR", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("GLYY", "-1", itemId));
        tradeSubItem.add(lan.createTradeSubItem("C_2088", "801742", itemId));// 暂时写死
        tradeSubItem.add(lan.createTradeSubItem("BOOK_FLAG", "0", itemId));
        tradeSubItem.add(lan.createTradeSubItem("USER_PASSWD", msg.get("userPasswd"), itemId));
        tradeSubItem.add(lan.createTradeSubItem("C_281", "281001", itemId));// 暂时写死
        tradeSubItem.add(lan.createTradeSubItem("QZLX_ADS0", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("MOFFICE_ID", newUserInfo.get("exchCode"), itemId));
        tradeSubItem.add(lan.createTradeSubItem("MES_NOTICE_NUMBER", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("DEPT_MOFFICE", "182018", itemId));// 暂时写死
        tradeSubItem.add(lan.createTradeSubItem("NET_PASSWORD", "123456", itemId));// 暂时写死
        tradeSubItem.add(lan.createTradeSubItem("USER_CALLING_AREA", "182018", itemId)); // 暂时写死
        tradeSubItem.add(lan.createTradeSubItem("ACCOUNT_NET", msg.get("serialNumber"), itemId));
        tradeSubItem.add(lan.createTradeSubItem("KB_NUMBER", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("C_1", "1001", itemId));// 暂时写死
        tradeSubItem.add(lan.createTradeSubItem("C_2", "2001", itemId));
        tradeSubItem.add(lan.createTradeSubItem("CUST_MANAGER_PHONE", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("SCHE_ID", "1234567890", itemId));// 暂时写死
        tradeSubItem.add(lan.createTradeSubItem("ACCT_NBR", msg.get("serialNumber"), itemId));
        tradeSubItem.add(lan.createTradeSubItem("DETAIL_INSTALL_ADDRESS", newUserInfo.get("installAddress"), itemId));
        tradeSubItem.add(lan.createTradeSubItem("C_2079", "801693", itemId));// 暂时写死
        tradeSubItem.add(lan.createTradeSubItem("C_26", "26009", itemId));// 暂时写死
        tradeSubItem.add(lan.createTradeSubItem("BUSI_CODE", msg.get("district"), itemId));
        tradeSubItem.add(lan.createTradeSubItem("SERV_DEPT_ID", "182018", itemId));// 暂时写死
        tradeSubItem.add(lan.createTradeSubItem("NO_BOOK_REASON", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("C_773", "773001", itemId));// 暂时写死
        tradeSubItem.add(lan.createTradeSubItem("C_224", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("POST", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("C_72", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("SEL_LOCAL_NET_ID", new ChangeCodeUtils().changeEparchy(msg), itemId));
        tradeSubItem.add(lan.createTradeSubItem("GLHM_NBR", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("C_230", "230001", itemId));// 暂时写死
        tradeSubItem.add(lan.createTradeSubItem("C_2266", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("SFGX_2060", "N", itemId));
        tradeSubItem.add(lan.createTradeSubItem("BUSI_FLAG", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("C_222", "1", itemId));
        tradeSubItem.add(lan.createTradeSubItem("C_223", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("BUSI_CODE", msg.get("district"), itemId));
        tradeSubItem.add(lan.createTradeSubItemE("CONTACT_INFO", msg.get("contactPhone"), itemId));
        tradeSubItem.add(lan.createTradeSubItemE("CONTACT_NAME", msg.get("contactPerson"), itemId));

        // 整理参数
        ext.put("tradeAcct", tradeAcct);
        ext.put("tradeUser", tradeUser);
        ext.put("tradeDiscnt", tradeDiscnt);
        ext.put("tradeRes", tradeRes);
        ext.put("tradePayrelation", tradePayrelation);
        if ("0".equals(msg.get("custType"))) {
            ext.put("tradeCustomer", tradeCustomer);
        }
        ext.put("tradeOther", tradeOther);
        ext.put("tradeItem", tradeItem);
        ext.put("tradeSubItem", tradeSubItem);

        Map retMap = new HashMap();
        retMap.put("base", base);
        retMap.put("ext", ext);
        return ext;

    }

    public Map dealPro(Exchange exchange) {
        Map body = (Map) exchange.getIn().getBody();
        Map msg = (Map) body.get("msg");
        Map newUserInfo = (Map) msg.get("newUserInfo");
        List productInfo = (List) newUserInfo.get("productInfo");
        msg.put("eparchyCode", new ChangeCodeUtils().changeEparchy(msg));
        String provinceCode = "00" + msg.get("provinceCode");
        Map ext = TradeManagerUtils.preProductInfo(productInfo, provinceCode, msg);
        return ext;
    }
}
