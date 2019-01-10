package com.ailk.ecaop.biz.sub.lan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;

@EcRocTag("CbssIPTVPreOrderProcessor")
public class CBIPTVPreOrderProcessor extends BaseAopProcessor {
	@Override
	public void process(Exchange exchange) throws Exception {
		Map body = exchange.getIn().getBody(Map.class);
        Map preMap = dealInparams(exchange);
        exchange.getIn().setBody(preMap);
        Map msg = (Map) body.get("msg");

        Map newUserInfo = (Map) msg.get("newUserInfo");
        List<Map> productInfo = (List<Map>) newUserInfo.get("productInfo");
        String province = (String) msg.get("province");
        String provinceCode = "00" + province;
        Map ext = TradeManagerUtils.preProductInfo(productInfo, provinceCode, msg);
        Map tradeProductType = (Map) ext.get("tradeProductType");
        String productTypeCode = (String) tradeProductType.get("productTypeCode");
        Map tradeUser = (Map) preMap.get("tradeUser");
        String productTypeCod = (String) tradeUser.get("productTypeCode");
        productTypeCod = productTypeCode;
        tradeUser.put("productTypeCode", productTypeCod);
        preMap.put("tradeUser", tradeUser);
        msg.putAll(preMap);
        body.putAll(msg);


        // 调用预提交接口
        LanUtils lan = new LanUtils();
        String ordersId = (String) msg.get("orderNo");
        String operTypeCode = "0";
        Map base = (Map) preMap.get("base");
        Map ext1 = (Map) preMap.get("ext");
        msg.put("base", base);
        msg.put("ext", ext1);
        msg.put("ordersId", ordersId);
        msg.put("operTypeCode", operTypeCode);

        body.put("msg", msg);
        exchange.getIn().setBody(body);
        lan.preData("ecaop.masb.sbac.sglUniTradeParametersMapping", exchange);
        CallEngine.wsCall(exchange,
                "ecaop.comm.conf.url.osn.services.ordCleSer");
        lan.xml2Json("ecaop.masb.sbac.sglUniTradeTemplate", exchange);
        body = (Map) exchange.getOut().getBody();
        // 整理返回报文
        Map returnMap = dealResult(exchange);
        exchange.getOut().setBody(returnMap);

    }


    // 处理返回结果
    private Map dealResult(Exchange exchange) {
        Map body = (Map) exchange.getOut().getBody();
        Map msg = (Map) ((Map) exchange.getIn().getBody()).get("msg");
        // 整理返回报文
        String code = (String) body.get("code");
        String detail = (String) body.get("detail");

        Map rspInfo = new HashMap();
        String orderId = (String) msg.get("orderNo");
        String provinceOrderId = (String) msg.get("orderNo");
        Map provinceOrderInfo1 = new HashMap();
        String subOrderId = (String) msg.get("orderNo");
        String subProvinceOrderId = (String) msg.get("orderNo");

        provinceOrderInfo1.put("subOrderId", subOrderId);
        provinceOrderInfo1.put("subProvinceOrderId", subProvinceOrderId);
        provinceOrderInfo1.put("totalFee", "0");

        Map provinceOrderInfo2 = new HashMap();
        provinceOrderInfo2.put("subOrderId", subOrderId);
        provinceOrderInfo2.put("subProvinceOrderId", subProvinceOrderId);
        provinceOrderInfo2.put("totalFee", "0");

        List<Map> provinceOrderInfo3 = new ArrayList<Map>();

        Map preFeeInfoRsp1 = new HashMap();
        preFeeInfoRsp1.put("operateType", "1");
        preFeeInfoRsp1.put("feeMode", "0");
        preFeeInfoRsp1.put("feeTypeCode", "8006");
        preFeeInfoRsp1.put("feeTypeName", null);
        preFeeInfoRsp1.put("payTag", "0");
        preFeeInfoRsp1.put("maxDerateFee", "0");
        preFeeInfoRsp1.put("oldFee", "45000");
        preFeeInfoRsp1.put("fee", "45000");

        Map preFeeInfoRsp2 = new HashMap();
        preFeeInfoRsp2.put("operateType", "1");
        preFeeInfoRsp2.put("feeMode", "0");
        preFeeInfoRsp2.put("feeTypeCode", "8202");
        preFeeInfoRsp2.put("feeTypeName", null);
        preFeeInfoRsp2.put("payTag", "0");
        preFeeInfoRsp2.put("maxDerateFee", "0");
        preFeeInfoRsp2.put("oldFee", "30000");
        preFeeInfoRsp2.put("fee", "30000");

        provinceOrderInfo3.add(preFeeInfoRsp1);
        provinceOrderInfo3.add(preFeeInfoRsp2);
        Map totalFee = new HashMap();
        totalFee.put("totalFee", "75000");

        provinceOrderInfo3.add(totalFee);

        rspInfo.put("orderId", orderId);
        rspInfo.put("provinceOrderId", provinceOrderId);
        rspInfo.put("provinceOrderInfo", provinceOrderInfo1);
        rspInfo.put("provinceOrderInfo", provinceOrderInfo2);
        rspInfo.put("provinceOrderInfo", provinceOrderInfo3);

        Map returnMap = new HashMap();
        returnMap.put("respCode", code);
        returnMap.put("respDesc", detail);
        returnMap.put("rspInfo", rspInfo);

        return returnMap;

    }

	private Map dealInparams(Exchange exchange) {
		Map body = exchange.getIn().getBody(Map.class);
		Map msg = (Map) body.get("msg");
        LanUtils lan = new LanUtils();
		Map userInfo = (Map) exchange.getOut().getBody(Map.class)
				.get("userInfo");
		Map newUserInfo = (Map) msg.get("newUserInfo");
		List<Map> productInfos = (List) newUserInfo.get("productInfo");
		List<Map> activityInfos = (List) newUserInfo.get("activityInfo");

		String orderId = (String) msg.get("ordersId");
		String operTypeCode = "0";
        String xDataType = "NULL";
        Map TRADE = new HashMap();
        Map EXT = new HashMap();
		// 整理参数
        // base
        String subscribeId = null;
        String tradeId = GetSeqUtil.getSeqFromCb(exchange, "seq_trade_id");
        String itemId = GetSeqUtil.getSeqFromCb(exchange, "seq_item_id");
        String userId = GetSeqUtil.getSeqFromCb(exchange, "seq_user_id");
        String custId = GetSeqUtil.getSeqFromCb(exchange, "seq_cust_id");
        String acctId = GetSeqUtil.getSeqFromCb(exchange, "seq_acct_id");
        String startDate = GetDateUtils.getDate();
        String endDate = "20501230000000";
        String acceptDate = GetDateUtils.getDate();
        String nextDealTag = null;
        String productId = null;
        for (Map productInfo : productInfos) {
            productId = (String) productInfo.get("productId");
        }
        String brandCode = (String) userInfo.get("brandCode");
        String usecustId = null;

        String serialNumber = (String) msg.get("serialNumber");
        String custName = (String) newUserInfo.get("certName");
        String cityCode = (String) msg.get("district");
        String mainDiscintCode = null;// ?
        ;
		Map base = new HashMap();
		base.put("subscribeId", subscribeId);
		base.put("tradeId", tradeId);
		base.put("startDate", startDate);
		base.put("endDate", endDate);
        base.put("acceptDate", acceptDate);
		base.put("nextDealTag", nextDealTag);
        base.put("olcomTag", "0");
        base.put("inModeCode", "0");
        base.put("tradeTypeCode", "0010");
		base.put("productId", productId);
		base.put("brandCode", brandCode);
        base.put("userId", userId);
        base.put("custId", custId);
        base.put("usecustId", custId);
        base.put("acctId", acctId);
        base.put("userDiffCode", "00");
        base.put("netTypeCode", "0040");
        base.put("serialNumber", serialNumber);
        base.put("custName", custName);
        base.put("termIp", "127.0.0.1");
        base.put("eparchyCode", "0010");
        base.put("cityCode", cityCode);
        base.put("execTime", GetDateUtils.getDate());
        base.put("operFee", "0");
        base.put("foregift", "0");
        base.put("advancePay", "0");
        base.put("cancelTag", "0");
        base.put("checkTypeCode", "0");
        base.put("chkTag", "0");
        base.put("mainDiscintCode", mainDiscintCode);

        // tradeUser
        String userPasswd = (String) msg.get("userPasswd");
        String inDate = GetDateUtils.getDate();
        String openDepartId = (String) msg.get("channelId");
        String openStaffId = (String) msg.get("orderNo");
        String inDepartId = (String) msg.get("channelId");
        String inStaffId = (String) msg.get("orderNo");
        String developStaffId = (String) msg.get("recomPersonId");
        String developDate = GetDateUtils.getDate();
        String developDepartId = (String) msg.get("recomPersonChannelId");// 发展人渠道
        String productTypeCode = null;

        List<Map> TRADE_USER = new ArrayList<Map>();
        Map item1 = new HashMap();
        item1.put("xDataType", xDataType);
        item1.put("usecustId", usecustId);
        item1.put("userPasswd", userPasswd);
        item1.put("userTypeCode", "0");
        item1.put("scoreValue", "0");
        item1.put("creditClass", "0");
        item1.put("basicCreditValue", "0");
        item1.put("creditValue", "0");
        item1.put("acctTag", "0");
        item1.put("prepayTag", "0");
        item1.put("inDate", inDate);
        item1.put("openDate", GetDateUtils.getDate());
        item1.put("openMode", "0");
        item1.put("openDepartId", openDepartId);
        item1.put("openStaffId", openStaffId);
        item1.put("inDepartId", inDepartId);
        item1.put("inStaffId", inStaffId);
        item1.put("removeTag", "0");
        item1.put("userStateCodeset", "0");
        item1.put("mputeMonthFee", "0");
        item1.put("developStaffId", developStaffId);
        item1.put("developDate", developDate);
        item1.put("developDepartId", developDepartId);
        item1.put("inNetMode", "0");
        item1.put("productTypeCode", productTypeCode);
        TRADE_USER.add(item1);
        EXT.put("tradeUser", TRADE_USER);


        // tradeRes
        String resTypeCode = "0";

        String resCode = null;// 电话号码
        for (Map activityInfo : activityInfos) {
            String activityR = (String) activityInfo.get("resourcesCode");
            if (activityR != null) {
                resCode = activityR;
            }
        }

        List<Map> TRADE_RES = new ArrayList<Map>();

        Map item9 = new HashMap();
        item9.put("xDataType", xDataType);
        item9.put("resTypeCode", resTypeCode);
        item9.put("resCode", resCode);
        item9.put("modifyTag", "0");
        item9.put("startDate", startDate);
        item9.put("endDate", endDate);
        TRADE_RES.add(item9);
        EXT.put("tradeRes", TRADE_RES);

        // tradePayrelation

        String payitemCode = null;// -1?
        String payrelationId = null;

        List<Map> TRADE_PAYRELATION = new ArrayList<Map>();

        Map item10 = new HashMap();
        item10.put("userId", userId);
        item10.put("acctId", acctId);
        item10.put("payitemCode", payitemCode);
        item10.put("acctPriority", "0");
        item10.put("userPriority", "0");
        item10.put("bindType", "1");
        item10.put("defaultTag", "1");
        item10.put("limitType", "0");
        item10.put("limit", "0");
        item10.put("complementTag", "0");
        item10.put("addupMonths", "0");
        item10.put("addupMethod", "0");
        item10.put("payrelationId", payrelationId);
        item10.put("actTag", "1");
        TRADE_PAYRELATION.add(item10);
        EXT.put("tradePayrelation", TRADE_PAYRELATION);

     // tradeItem
        Map TRADE_ITEM = new HashMap();
        List<Map> items = new ArrayList<Map>();
        // 将每一组参数放入
        items.add(lan.createAttrInfoNoTime("MAIN_USER_TAG", null));
        items.add(lan.createAttrInfoNoTime("IMMEDIACY_INFO", "0"));
        items.add(lan.createAttrInfoNoTime("SUB_TYPE", "0"));
        items.add(lan.createAttrInfoNoTime("COMP_DEAL_STATE", "0"));
        items.add(lan.createAttrInfoNoTime("ROLE_CODE_B", "g"));
        items.add(lan.createAttrInfoNoTime("ISBJTAG", "1"));
        items.add(lan.createAttrInfoNoTime("RIGHTCODE", "csCreatecompuser"));
        items.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", "1010300"));
        items.add(LanUtils.createTradeItem("OPER_CODE", "1"));
        items.add(LanUtils.createTradeItem("IPTV_OTT_ZDLW", null));
        items.add(LanUtils.createTradeItem("GXLX_2060", "PTDH:0:30"));
        items.add(LanUtils.createTradeItem("WOPAY", null));
        items.add(LanUtils.createTradeItem("IPTV_OTT_MAC", null));
        items.add(LanUtils.createTradeItem("NO_BOOK_REASON", null));
        items.add(LanUtils.createTradeItem("BOOK_FLAG", "0"));
        items.add(LanUtils.createTradeItem("WOPAYMONEY", null));
        items.add(LanUtils.createTradeItem("USER_PASSWD", "014530"));
        items.add(LanUtils.createTradeItem("IPTV_OTT_YARDS", null));
        items.add(LanUtils.createTradeItem("EXISTS_ACCT", "1"));
        items.add(LanUtils.createTradeItem("RES_PRE_ORDER", "0"));
        items.add(LanUtils.createTradeItem("IFBAT", "0"));
        items.add(LanUtils.createTradeItem("CSBDBZFJD", "0"));
        items.add(LanUtils.createTradeItem("PRE_END_TIME", GetDateUtils.getDate()));
        items.add(LanUtils.createTradeItem("REOPEN_TAG", "2"));
        items.add(LanUtils.createTradeItem("NEW_PASSWD", "014530"));
        items.add(LanUtils.createTradeItem("PRE_START_TIME", null));
        items.add(LanUtils.createTradeItem("SFGX_2060", "Y"));
        items.add(LanUtils.createTradeItem("SCHE_ID", "0"));
        items.add(lan.createAttrInfoNoTime("ALONE_TCS_COMP_INDEX", "1"));
        TRADE_ITEM.put("item", items);
        EXT.put("tradeItem", TRADE_ITEM);


		// tradeSubItem
        Map TRADE_SUB_ITEM = new HashMap();
        List<Map> item = new ArrayList<Map>();
        item.add(lan.createTradeSubItemB(itemId, "iptvnbr", "01-01010014530-01", startDate, endDate));
        item.add(lan.createTradeSubItemB(itemId, "iptvstbid", "00BJ04000004441015010023B8E4F640", startDate, endDate));
        item.add(lan.createTradeSubItemB(itemId, "iptvmac", "00:23:B8:E4:F6:40", startDate, endDate));
        item.add(lan.createTradeSubItemB(itemId, "cellphone", "34324523", startDate, endDate));
        item.add(lan.createTradeSubItem("addressId", null, itemId));
        item.add(lan.createTradeSubItem("portSpeed", "7", itemId));
        item.add(lan.createTradeSubItem("isWait", "N", itemId));
        item.add(lan.createTradeSubItem("iomreturnremark", "null", itemId));
        item.add(lan.createTradeSubItem("iomreturniswork", "null", itemId));
        item.add(lan.createTradeSubItem("shareNbr", "01088820700", itemId));
        item.add(lan.createTradeSubItem("custManagerId", null, itemId));
        item.add(lan.createTradeSubItem("serviceusertypekey", "A0", itemId));
        item.add(lan.createTradeSubItem("areaCode", "212", itemId));
        item.add(lan.createTradeSubItem("serialNumber", serialNumber, itemId));
        item.add(lan.createTradeSubItem("userPaawd", "014530", itemId));
        item.add(lan.createTradeSubItem("pcSocket", "1", itemId));
        item.add(lan.createTradeSubItem("mofficeId", null, itemId));
        item.add(lan.createTradeSubItem("xdsltypekey", "2", itemId));
        item.add(lan.createTradeSubItem("subArea", null, itemId));
        item.add(lan.createTradeSubItem("communitId", "0", itemId));
        item.add(lan.createTradeSubItem("servicetimelimitkey", "2", itemId));
        item.add(lan.createTradeSubItem("townId", "C", itemId));
        item.add(lan.createTradeSubItem("adslIsfrontinputstl", "1", itemId));
        item.add(lan.createTradeSubItem("custManagerName", null, itemId));
        item.add(lan.createTradeSubItem("adsltypekey", "1", itemId));
        item.add(lan.createTradeSubItem("acctNbr", "132111177789", itemId));
        item.add(lan.createTradeSubItem("detailInstallAddress", "二二二二二二", itemId));
        item.add(lan.createTradeSubItem("expectRate", null, itemId));
        item.add(lan.createTradeSubItem("orderSource", null, itemId));
        item.add(lan.createTradeSubItem("busiCode", "5100", itemId));
        item.add(lan.createTradeSubItem("resionSsp", null, itemId));
        item.add(lan.createTradeSubItem("rivalcommunity", null, itemId));
        item.add(lan.createTradeSubItem("localNetCode", null, itemId));
        item.add(lan.createTradeSubItem("connectnetmodekey", "1", itemId));
        item.add(lan.createTradeSubItem("installAddress", null, itemId));
        item.add(lan.createTradeSubItem("linkPhone", "12231234543", itemId));
        item.add(lan.createTradeSubItem("userLvl", "5", itemId));
        item.add(lan.createTradeSubItem("separatorcount", "1", itemId));
        item.add(lan.createTradeSubItem("servicefeetypekey", "A0", itemId));
        item.add(lan.createTradeSubItem("serialNumberId", "0", itemId));
        item.add(lan.createTradeSubItem("communitName", null, itemId));
        item.add(lan.createTradeSubItem("initPasswd", "0", itemId));
        item.add(lan.createTradeSubItem("regionArea", null, itemId));
        item.add(lan.createTradeSubItem("linkName", "二", itemId));
        TRADE_SUB_ITEM.put("item", item);
        EXT.put("tradeSubItem", TRADE_SUB_ITEM);

		Map retMap = new HashMap();
		retMap.put("orderId", orderId);
		retMap.put("operTypeCode", operTypeCode);
        retMap.put("serviceClassCode", newUserInfo.get("serviceClassCode"));
        TRADE.put("base", base);
        TRADE.put("ext", EXT);
        retMap.put("trade", TRADE);

		return retMap;
	}

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
}
