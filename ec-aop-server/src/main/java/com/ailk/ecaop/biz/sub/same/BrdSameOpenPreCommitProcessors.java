package com.ailk.ecaop.biz.sub.same;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.user.CheckUserInfoProcessor;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("brdSameOpenPreCommit")
public class BrdSameOpenPreCommitProcessors extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {

        // 调用客户信息校验之前，请求参数的保留备份
        Map body = exchange.getIn().getBody(Map.class);

        // 调用三户查询接口
        Map msg = (Map) body.get("msg");
        Map infoList = new HashMap();
        infoList.put("areaCode", msg.get("shareAreaCode"));
        infoList.put("serialNumber", msg.get("serialNumber"));
        msg.put("infoList", infoList);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        CheckUserInfoProcessor checkUser = new CheckUserInfoProcessor();
        checkUser.process(exchange);
        exchange.getIn().setBody(body);
        // 处理参数
        Map preParams = dealInparams(exchange);
        Map base = (Map) preParams.get("base");
        Map ext = (Map) preParams.get("ext");
        Map newUserInfo = (Map) msg.get("newUserInfo");

        msg.put("ordersId", msg.get("orderNo"));
        msg.put("operTypeCode", "0");
        msg.put("serviceClassCode", newUserInfo.get("serviceClassCode"));
        msg.put("base", base);
        msg.put("ext", ext);

        body.put("msg", msg);
        exchange.getIn().setBody(body);
        // 调用预提交接口
        exchange.setMethodCode("bsoa");
        LanUtils lan = new LanUtils();
        String province = (String) msg.get("province");
        String opeSysType = (String) msg.get("opeSysType");
        lan.preData("ecaop.masb.bsoa.sUniTradeParametersMapping", exchange);
        if ("2".equals(opeSysType)) {
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        }
        else {
            if ("17|18|11|76|91|97".contains(province)) {
                CallEngine.wsCall(exchange, "ecaop.comm.conf.url.OrdForNorthSer" + "." + msg.get("province"));
            }
            else {
                CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
            }
        }
        lan.xml2Json("ecaop.trades.bsoa.sUniTrade.template", exchange);
        Map message = exchange.getOut().getBody(Map.class);
        if (null != message.get("rspInfo")) {
            Map rspInfo = (Map) message.get("rspInfo");

            // 处理返回参数
            Map result = dealReturn(rspInfo);
            if (null == result.get("userName")) {
                result.put("userPasswd", msg.get("userPasswd"));
                result.put("userName", msg.get("areaCode").toString() + msg.get("number"));
            }
            exchange.getOut().setBody(result);
        }

    }

    private Map dealReturn(Map rspInfo) {
        Map ret = new HashMap();
        ret.put("provOrderId", rspInfo.get("provOrderId"));
        if (null != rspInfo.get("preFeeInfoRsp")) {
            List<Map> feeInfo = (List<Map>) rspInfo.get("preFeeInfoRsp");
            if (0 != feeInfo.size()) {
                for (Map fee : feeInfo) {
                    dealFee(fee);
                }
                ret.put("feeInfo", feeInfo);
            }
        }
        if (rspInfo.get("totalFee").toString().startsWith("0")) {
            ret.put("totalFee", rspInfo.get("totalFee"));
        }
        else {
            ret.put("totalFee", rspInfo.get("totalFee") + "0");
        }
        return ret;
    }

    private void dealFee(Map fee) {
        fee.put("maxRelief", fee.get("maxRelief").toString() + "0");
        fee.put("origFee", fee.get("origFee").toString() + "0");
        fee.remove("operatorType");
    }

    /*
     * 处理数据
     */
    private Map dealInparams(Exchange exchange) {
        LanUtils lan = new LanUtils();
        Map body = exchange.getIn().getBody(Map.class);
        Object msgObject = body.get("msg");
        Map msg = null;
        if (msgObject instanceof String) {
            msg = JSON.parseObject(msgObject.toString());
        }
        else {
            msg = (Map) msgObject;
        }

        // 获取三户返回的结果
        Map userInfo = (Map) exchange.getOut().getBody(Map.class).get("userInfo");
        Map custInfo = (Map) exchange.getOut().getBody(Map.class).get("custInfo");
        Map acctInfo = (Map) exchange.getOut().getBody(Map.class).get("acctInfo");
        Map newUserInfo = (Map) msg.get("newUserInfo");

        String xDatatype = null;

        // 准备活动信息
        List<Map> activityInfos = new ArrayList<Map>();
        Map newActivityInfo = (Map) newUserInfo.get("activityInfo");
        activityInfos.add(newActivityInfo);

        // 准备产品信息
        List<Map> productInfos = new ArrayList<Map>();
        Map productInfo = (Map) newUserInfo.get("productInfo");
        String productId = (String) productInfo.get("productId");
        String productMode = (String) productInfo.get("productMode");
        if ("0".equals(productMode)) {
            productInfo.put("productMode", "1");
        }
        if ("1".equals(productMode)) {
            productInfo.put("productMode", "0");
        }
        productInfos.add(productInfo);
        String provinceCode = "00" + (String) msg.get("province");
        // 准备用户标示
        String id = (String) userInfo.get("userId");
        msg.put("userId", id);
        Map EXT = new HashMap();
        EXT = TradeManagerUtils.preProductInfo(productInfos, provinceCode, msg);

        // base
        String subscribeId = GetSeqUtil.getSeqFromCb();
        String tradeId = GetSeqUtil.getSeqFromCb();
        String usecustId = (String) custInfo.get("custId");
        String userId = (String) userInfo.get("userId");
        String custId = (String) custInfo.get("custId");
        String acctId = (String) acctInfo.get("acctId");
        String itemId = GetSeqUtil.getSeqFromCb();
        String startDate = GetDateUtils.getDate();
        String endDate = MagicNumber.CBSS_DEFAULT_EXPIRE_TIME;
        String acceptDate = GetDateUtils.getDate();
        String serialNumber = (String) msg.get("serialNumber");
        String custName = (String) newUserInfo.get("certName");
        String termIp = "127.0.0.1"; // 受理终端IP？
        String execTime = GetDateUtils.getDate();
        String productSpec = null;// ?

        Map base = new HashMap();
        base.put("subscribeId", subscribeId);
        base.put("tradeId", tradeId);
        base.put("userId", userId);
        base.put("custId", custId);
        base.put("usecustId", usecustId);
        base.put("acctId", acctId);
        base.put("startDate", startDate);
        base.put("endDate", endDate);
        base.put("acceptDate", acceptDate);
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        base.put("inModeCode", new ChangeCodeUtils().getInModeCode(exchange.getAppCode()));
        base.put("tradeTypeCode", "0010");
        base.put("productId", productId);
        base.put("brandCode", "ADSL");
        base.put("userDiffCode", "00");// 用户类型用户类型00:普通用户01:亲友电话虚用户02:对讲机虚用户03:两地同虚用户04:两地通虚用户05:组合产品用户
        base.put("netTypeCode", "0030");
        base.put("serinalNamber", serialNumber);
        base.put("custName", custName);
        base.put("termIp", termIp);
        base.put("eparchyCode", custInfo.get("eparchyCode"));
        base.put("cityCode", userInfo.get("cityCode"));
        base.put("execTime", execTime);
        base.put("operFee", "0");
        base.put("foregift", "0");
        base.put("advancePay", "0");
        base.put("cancelTag", "0");
        base.put("checkTypeCode", "0");
        base.put("chkTag", "0");
        base.put("productSpec", productSpec);

        // tradeAcct
        String payName = (String) acctInfo.get("payName");
        Map TRADE_ACCT = new HashMap();
        Map item0 = new HashMap();
        item0.put("xDataType", xDatatype);
        item0.put("acctId", acctId);
        item0.put("payName", payName);
        item0.put("payModeCode", acctInfo.get("payModeCode"));
        item0.put("scoreValue", "0");
        item0.put("creditClassId", "0");
        item0.put("basicCreditValue", "0");
        item0.put("creditValue", "0");
        item0.put("creditControlId", "0");
        item0.put("debutyUserId", acctInfo.get("debutyUserId"));
        item0.put("debutyCode", acctInfo.get("debutyCode"));
        item0.put("removeTag", "0");
        item0.put("openDate", GetDateUtils.getDate());
        TRADE_ACCT.put("item", item0);
        EXT.put("tradeAcct", TRADE_ACCT);

        // tradeUser
        String userPasswd = (String) msg.get("userPasswd");
        String developStaffId = (String) msg.get("recomPersonId");
        String developDate = GetDateUtils.getDate();
        String developDepartId = (String) msg.get("recomPersonChannelId");// 发展人渠道
        String developCityCode = (String) msg.get("developCityCode");

        Map TRADE_USER = new HashMap();
        Map item1 = new HashMap();
        item1.put("xDataType", xDatatype);
        item1.put("usecustId", usecustId);
        item1.put("userPasswd", userPasswd);
        item1.put("userTypeCode", "0");
        item1.put("scoreValue", "0");
        item1.put("creditClass", "0");
        item1.put("basicCreditValue", "0");
        item1.put("creditValue", "0");
        item1.put("acctTag", "0");
        item1.put("prepayTag", "0");
        item1.put("inDate", GetDateUtils.getDate());
        item1.put("openDate", GetDateUtils.getDate());
        item1.put("openMode", "0");
        item1.put("openDepartId", "9110253");
        item1.put("openStaffId", "BSS_DL001");
        item1.put("inDepartId", "9110253");
        item1.put("inStaffId", "BSS_DL001");
        item1.put("removeTag", "0");
        item1.put("userStateCodeset", "0");
        item1.put("mputeMonthFee", "0");
        item1.put("developStaffId", developStaffId);
        item1.put("developDate", developDate);
        item1.put("developEparchyCode", "0411");
        item1.put("developCityCode", developCityCode);
        item1.put("developDepartId", developDepartId);
        item1.put("inNetMode", "0");
        item1.put("productTypeCode", "ADSLHD");
        TRADE_USER.put("item", item1);
        EXT.put("tradeUser", TRADE_USER);

        // tradeRes
        String reTypeCode = null;
        for (Map activityInfo : activityInfos) {
            String activityR = (String) activityInfo.get("resourcesType");
            if (activityR != null) {
                reTypeCode = activityR;
            }
            else {
                reTypeCode = "0";
            }
        }
        String resCode = null;
        for (Map activityInfo : activityInfos) {
            String activityR = (String) activityInfo.get("resourcesCode");
            if (activityR != null) {
                resCode = activityR;
            }
            else {
                resCode = "041105027245";
            }
        }

        Map TRADE_RES = new HashMap();

        Map item2 = new HashMap();
        item2.put("xDataType", xDatatype);
        item2.put("reTypeCode", reTypeCode);
        item2.put("resCode", resCode);
        item2.put("modifyTag", "0");
        item2.put("startDate", startDate);
        item2.put("endDate", endDate);
        TRADE_RES.put("item", item2);
        EXT.put("tradeRes", TRADE_RES);

        // tradePayrelation

        Map TRADE_PAYRELATION = new HashMap();

        Map item3 = new HashMap();
        item3.put("userId", userId);
        item3.put("acctId", acctId);
        item3.put("payitemCode", "-1");
        item3.put("acctPriority", "0");
        item3.put("userPriority", "0");
        item3.put("bindType", "1");
        item3.put("defaultTag", "1");
        item3.put("limitType", "0");
        item3.put("limit", "0");
        item3.put("complementTag", "0");
        item3.put("addupMonths", "0");
        item3.put("addupMethod", "0");
        item3.put("payrelationId", GetSeqUtil.getSeqFromCb());
        item3.put("actTag", "1");
        TRADE_PAYRELATION.put("item", item3);
        EXT.put("tradePayrelation", TRADE_PAYRELATION);

        // tradeItem
        Map TRADE_ITEM = new HashMap();
        List<Map> items = new ArrayList<Map>();
        // 将每一组参数放入
        items.add(lan.createAttrInfoNoTime("MARKETING_MODE", null));
        items.add(lan.createAttrInfoNoTime("DEVELOP_STAFF_ID", developStaffId));
        items.add(lan.createAttrInfoNoTime("DEVELOP_DEPART_ID", developDepartId));
        items.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", "2010200"));
        items.add(LanUtils.createTradeItem("ADSL_END_DATE", null));
        items.add(LanUtils.createTradeItem("GXLX_2060", "PTDH:1:30"));
        items.add(LanUtils.createTradeItem("NO_BOOK_REASON", null));
        items.add(LanUtils.createTradeItem("USER_PASSWD", "027245"));
        items.add(LanUtils.createTradeItem("EXISTS_ACCT", "0"));
        items.add(LanUtils.createTradeItem("SUB_TYPE", "0"));
        items.add(LanUtils.createTradeItem("RES_PRE_ORDER", "0"));
        items.add(LanUtils.createTradeItem("IMMEDIACY_INFO", "0"));
        items.add(LanUtils.createTradeItem("USER_ACCPDATE", null));
        items.add(LanUtils.createTradeItem("REOPEN_TAG", "2"));
        items.add(LanUtils.createTradeItem("PRE_END_TIME", "2015-12-19 16:12:37"));
        items.add(LanUtils.createTradeItem("NEW_PASSWD", "027245"));
        items.add(LanUtils.createTradeItem("SFGX_2060", "Y"));
        items.add(LanUtils.createTradeItem("SCHE_ID", null));
        TRADE_ITEM.put("item", items);
        EXT.put("tradeItem", TRADE_ITEM);

        // tradeSubItem
        Map TRADE_SUB_ITEM = new HashMap();
        List<Map> subitems = new ArrayList<Map>();
        subitems.add(lan.createTradeSubItem("CUST_MANAGER_ID", null, itemId));
        subitems.add(lan.createTradeSubItem("SUPPORTBUSINESSTYPEKEY", "1", itemId));
        subitems.add(lan.createTradeSubItem("AREA_CODE", "910570", itemId));
        subitems.add(lan.createTradeSubItem("CHARGE_TYPE", "1", itemId));
        subitems.add(lan.createTradeSubItem("MONITORING", "0", itemId));
        subitems.add(lan.createTradeSubItem("SERVICEUSAGEKEY", "610001", itemId));
        subitems.add(lan.createTradeSubItem("NGN_MARK", "0", itemId));
        subitems.add(lan.createTradeSubItem("BILLINGRATEKEY", "110006", itemId));
        subitems.add(lan.createTradeSubItem("CUST_MANAGER_NAME", null, itemId));
        subitems.add(lan.createTradeSubItem("ORIGINALCOMPANY", null, itemId));
        subitems.add(lan.createTradeSubItem("PRE_START_TIME", null, itemId));
        subitems.add(lan.createTradeSubItem("CONTENTTYPE", "0", itemId));
        subitems.add(lan.createTradeSubItem("NAME", null, itemId));
        subitems.add(lan.createTradeSubItem("expect_rate", null, itemId));
        subitems.add(lan.createTradeSubItem("TIME_SHARING", "0", itemId));
        subitems.add(lan.createTradeSubItem("REGION_SSP", "210202", itemId));
        subitems.add(lan.createTradeSubItem("TRADE_NUMBER", null, itemId));
        subitems.add(lan.createTradeSubItem("COUNTRYFLAG", null, itemId));
        subitems.add(lan.createTradeSubItem("LOCAL_NET_CODE", "0411", itemId));
        subitems.add(lan.createTradeSubItem("INSTALL_ADDRESS", "10", itemId));
        subitems.add(lan.createTradeSubItem("ISLIGHTCHANGE", "0", itemId));
        subitems.add(lan.createTradeSubItem("XDSL_PTYPE", null, itemId));
        subitems.add(lan.createTradeSubItem("CUSTOMER_GROUP", null, itemId));
        subitems.add(lan.createTradeSubItem("COMMUNIT_NAME", null, itemId));
        subitems.add(lan.createTradeSubItem("INIT_PASSWD", "0", itemId));
        subitems.add(lan.createTradeSubItem("LINK_NAME", "测试", itemId));
        subitems.add(lan.createTradeSubItem("IPNUMBER", "1", itemId));
        subitems.add(lan.createTradeSubItem("PREPAY_TAG", "0", itemId));
        subitems.add(lan.createTradeSubItem("ADDRESS_ID", "1573316740", itemId));
        subitems.add(lan.createTradeSubItem("PORT_SPEED", "110005", itemId));
        subitems.add(lan.createTradeSubItem("iswait", "N", itemId));
        subitems.add(lan.createTradeSubItem("RESOURCEPIECEID", null, itemId));
        subitems.add(lan.createTradeSubItem("SHARE_NBR", "041182219541", itemId));
        subitems.add(lan.createTradeSubItem("ADSL_EQUIPMENTMODELKEY", null, itemId));
        subitems.add(lan.createTradeSubItem("SERVICEUSERTYPEKEY", "610001", itemId));
        subitems.add(lan.createTradeSubItem("ADSL_IPTYPEKEY", "1", itemId));
        subitems.add(lan.createTradeSubItem("BOOK_FLAG", "0", itemId));
        subitems.add(lan.createTradeSubItem("USER_PASSWD", "027245", itemId));
        subitems.add(lan.createTradeSubItem("ISRENTSERVICE", "0", itemId));
        subitems.add(lan.createTradeSubItem("CLIENTNUM", "1", itemId));
        subitems.add(lan.createTradeSubItem("XDSLTYPEKEY", "1", itemId));
        subitems.add(lan.createTradeSubItem("MOFFICE_ID", "SS21", itemId));
        subitems.add(lan.createTradeSubItem("EQUIPMENTSTATEKEY", "1", itemId));
        subitems.add(lan.createTradeSubItem("COMMUNIT_ID", "0", itemId));
        subitems.add(lan.createTradeSubItem("EQUIPMENTYPEKEY", "110001", itemId));
        subitems.add(lan.createTradeSubItem("USER_CALLING_AREA", "0411", itemId));
        subitems.add(lan.createTradeSubItem("ADSL_ISFRONTINPUTSTL", "1", itemId));
        subitems.add(lan.createTradeSubItem("PROMISE_AREA_FLAG", "0", itemId));
        subitems.add(lan.createTradeSubItem("ADSLTYPEKEY", "1", itemId));
        subitems.add(lan.createTradeSubItem("IPADDRESS", null, itemId));
        subitems.add(lan.createTradeSubItem("ACCT_NBR", "041105027245", itemId));
        subitems.add(lan.createTradeSubItem("DETAIL_INSTALL_ADDRESS", "测试地址", itemId));
        subitems.add(lan.createTradeSubItem("BUSI_CODE", "910571", itemId));
        subitems.add(lan.createTradeSubItem("SHOW_ORDER", null, itemId));
        subitems.add(lan.createTradeSubItem("CONNECTNETMODEKEY", "2", itemId));
        subitems.add(lan.createTradeSubItem("LINK_PHONE", "13213213212", itemId));
        subitems.add(lan.createTradeSubItem("SPECIALLINEMODEKEY", "2", itemId));
        subitems.add(lan.createTradeSubItem("CONTACTADDRESS", null, itemId));
        subitems.add(lan.createTradeSubItem("SEPARATORCOUNT", "1", itemId));
        subitems.add(lan.createTradeSubItem("ADSL_EQUIPMENTSOURCEKEY", "1", itemId));
        subitems.add(lan.createTradeSubItem("BILLINGMODE", "999999", itemId));
        subitems.add(lan.createTradeSubItem("SERIAL_NUMBER_ID", "0", itemId));
        subitems.add(lan.createTradeSubItem("ACCT_PASSWD", "223344", itemId));
        subitems.add(lan.createTradeSubItem("BUSI_CODE", "910571", itemId));
        TRADE_SUB_ITEM.put("item", subitems);
        EXT.put("tradeSubItem", TRADE_SUB_ITEM);

        Map TRADE = new HashMap();
        TRADE.put("base", base);
        TRADE.put("ext", EXT);

        return TRADE;
    }
}
