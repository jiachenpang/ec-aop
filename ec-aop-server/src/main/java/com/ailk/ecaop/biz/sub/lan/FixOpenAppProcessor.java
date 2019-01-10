package com.ailk.ecaop.biz.sub.lan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.user.CheckUserTransferFixedAllProcessor;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.google.common.collect.Maps;

// 23转4开户处理申请
@EcRocTag("FixOpenApp")
public class FixOpenAppProcessor extends BaseAopProcessor {

    LanUtils lan = new LanUtils();
    String custId;
    String userId;
    String acctId;
    String tradeId;

    @Override
    public void process(Exchange exchange) throws Exception {

        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");

        msg.put("operTypeCode", "0");
        preAllData(exchange, msg);
        Exchange exchange4CB = ExchangeUtils.ofCopy(exchange, msg);
        try {
            lan.preData("ecaop.trades.scmc.cancelPre.paramtersmapping", exchange4CB);
            CallEngine.wsCall(exchange4CB, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
            lan.xml2Json("ecaop.trades.scmc.cancelPre.template", exchange4CB);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", " FixOpenAppProcessor代码有异常：" + e.getMessage());
        }
        Map retMapa = Maps.newHashMap();
        Map retMap = exchange4CB.getOut().getBody(Map.class);
        List<Map> rspInfoList = (ArrayList<Map>) retMap.get("rspInfo");
        if (null == rspInfoList || 0 == rspInfoList.size()) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "核心系统未返回订单信息.");
        }

        retMapa.put("bssOrderId", rspInfoList.get(0).get("provOrderId"));
        retMapa.put("provOrderId", rspInfoList.get(0).get("bssOrderId"));

        Integer totalFee = 0;
        for (Map rspMap : rspInfoList) {
            List<Map> provinceOrderInfo = (List<Map>) rspMap.get("provinceOrderInfo");
            if (null == provinceOrderInfo || provinceOrderInfo.isEmpty()) {
                continue;
            }
            // TODO:费用计算
            for (Map provinceOrder : provinceOrderInfo) {
                totalFee = totalFee + Integer.valueOf(provinceOrder.get("totalFee").toString());
                List<Map> preFeeInfoRsp = (List<Map>) provinceOrder.get("preFeeInfoRsp");
                if (null == preFeeInfoRsp || preFeeInfoRsp.isEmpty()) {
                    continue;
                }
                List<Map> feeList = dealFee(preFeeInfoRsp);
                retMapa.put("feeInfo", feeList);
            }
            retMapa.put("totalFee", totalFee);
        }

        Message message = new DefaultMessage();
        message = exchange4CB.getOut();
        message.setBody(retMapa);
        exchange.setOut(message);

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

    private void preAllData(Exchange exchange, Map msg) {
        Map bodyBack = CheckUser(exchange);// 调用23转4校验接口
        Exchange exchange0 = ExchangeUtils.ofCopy(exchange, msg);
        userId = GetSeqUtil.getSeqFromCb(exchange0, "seq_user_id");
        Exchange exchange1 = ExchangeUtils.ofCopy(exchange0, msg);
        custId = GetSeqUtil.getSeqFromCb(exchange1, "seq_cust_id");
        Exchange exchange2 = ExchangeUtils.ofCopy(exchange1, msg);
        acctId = GetSeqUtil.getSeqFromCb(exchange2, "seq_acct_id");
        Exchange exchange3 = ExchangeUtils.ofCopy(exchange2, msg);
        tradeId = GetSeqUtil.getSeqFromCb(exchange3, "seq_trade_id");
        String orderId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange3, msg), "seq_order_id");

        List<Map> custInfo = (ArrayList<Map>) bodyBack.get("custInfo");
        List<Map> userInfo = (ArrayList<Map>) bodyBack.get("userInfo");

        List<Map> productInfos = (List<Map>) msg.get("productInfo");
        String province = "00" + (String) msg.get("province");
        String eparchyCode = ChangeCodeUtils.changeEparchy(msg);
        msg.put("eparchyCode", eparchyCode);
        msg.put("userId", userId);
        msg.put("ordersId", tradeId);
        // msg.put("ordersId", tradeId); msg本来就有ordersId

        msg.put("serviceClassCode", ((List<Map>) bodyBack.get("userInfo")).get(0).get("serviceClassCode").toString());
        msg.put("methodCode", exchange.getMethodCode());

        // 产品模式转码 2016-06-03 lixl
        for (Map productInfo : productInfos) {
            String productMode = "";
            if ("1".equals(productInfo.get("productMode"))) {
                productMode = "0";
            }
            else if ("0".equals(productInfo.get("productMode"))) {
                productMode = "1";
            }
            productInfo.put("productMode", productMode);
        }
        Map ext = TradeManagerUtils.newPreProductInfo(productInfos, province, msg);
        Map BigExt = new HashMap();
        Exchange exchangeA = ExchangeUtils.ofCopy(exchange, msg);
        msg.put("base", preBaseData(exchangeA, msg, bodyBack));

        BigExt.put("tradeUser", PreTradeUser(msg, bodyBack, ext));
        BigExt.put("tradeProductType", ext.get("tradeProductType"));
        BigExt.put("tradeProduct", ext.get("tradeProduct"));
        BigExt.put("tradeDiscnt", ext.get("tradeDiscnt"));
        BigExt.put("tradeSvc", ext.get("tradeSvc"));
        BigExt.put("tradeRes", preTradeRes(msg));
        BigExt.put("tradePayrelation", preTradePayrelation(msg, bodyBack));
        BigExt.put("tradeItem", preTradeItem(msg, exchange.getAppCode()));
        BigExt.put("tradeCustomer", pretradeCustomer(msg));
        BigExt.put("tradeCustPerson", preTradeCustPerson(msg, bodyBack));
        Exchange exchangeB = ExchangeUtils.ofCopy(exchangeA, msg);
        BigExt.put("tradeSubItem", preTradeSubItem(msg, exchangeB, bodyBack));
        msg.put("ext", BigExt);
    }

    private Map CheckUser(Exchange exchange) {
        CheckUserTransferFixedAllProcessor cu = new CheckUserTransferFixedAllProcessor();
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        List<Map> numIdList = (List<Map>) msg.get("numId");
        msg.put("tradeTypeCode", "0446");
        msg.put("numId", numIdList.get(0).get("serialNumber"));

        msg.put("serClassCode", "0040");
        List<Map> paraList = (List<Map>) msg.get("para");
        if (null != paraList && paraList.size() > 0) {
            for (Map para : paraList) {
                if ("serviceClassCode".equals(para.get("paraId"))) {
                    msg.put("serClassCode", para.get("paraValue"));
                }
            }
        }

        Exchange exchangePre = ExchangeUtils.ofCopy(exchange, msg);
        try {
            cu.applyParams(null);
            cu.process(exchangePre);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "调用23转4开户处理申请异常:" + e.getMessage());
        }
        Map bodyBack = exchangePre.getOut().getBody(Map.class);
        System.out.print("{{{}}{{}{{}}{}{}{}{" + bodyBack.toString());
        return bodyBack;
    }

    private Map preBaseData(Exchange exchange, Map msg, Map bodyBack) {

        // ext
        Map base = new HashMap();
        List<Map> customerInfo = (List<Map>) msg.get("customerInfo");
        List<Map> productInfo = (List<Map>) msg.get("productInfo");
        List<Map> productList = (List<Map>) msg.get("productList");

        List<Map> custInfo = (ArrayList<Map>) bodyBack.get("custInfo");
        List<Map> userInfo = (ArrayList<Map>) bodyBack.get("userInfo");

        base.put("tradeId", tradeId);
        base.put("subscribeId", msg.get("ordersId"));
        // base.put("subscribeId", tradeId);
        base.put("startDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("endDate", "20501231122359");
        base.put("acceptDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("nextDealTag", "Z");// 后续处理状态
        base.put("olcomTag", "0");// 指令标志
        base.put("inModeCode", new ChangeCodeUtils().getInModeCode(exchange.getAppCode()));// 接入方式
        base.put("tradeTypeCode", "0448");// 业务类型编码
        base.put("productId", productInfo.get(0).get("productId"));// 从上游获取的
        base.put("brandCode", productList.get(0).get("brandCode"));// 品牌编码
        base.put("userId", userId);
        base.put("custId", custId);
        base.put("usecustId", custId);
        base.put("acctId", acctId);
        base.put("userDiffCode", "00");
        base.put("netTypeCode", "0030");// 网别
        String serialNumber = msg.get("numId").toString();
        // String serialNumber = ((List<Map>) msg.get("numId")).get(0).get("serialNumber").toString();
        base.put("serinalNamber", serialNumber);
        base.put("custName", customerInfo == null ? null : customerInfo.get(0).get("customerName"));
        base.put("termIp", "110.248.25.88");// 受理终端IP地址
        base.put("eparchyCode", ChangeCodeUtils.changeEparchy(msg));
        base.put("cityCode", ((List<Map>) bodyBack.get("userInfo")).get(0).get("cityCode").toString());
        base.put("execTime", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("operFee", "0");// 营业费用
        base.put("foregift", "0");// 押金金额
        base.put("advancePay", "0");// 预付话费
        base.put("feeState", "");// 收费标志：0-未收费，1-已收费
        base.put("feeStaffId", "");
        base.put("cancelTag", "0");
        base.put("checktypeCode", "0");
        base.put("chkTag", "0");// <!--审核标志：0-未审核，1-审核通过，2-审核未通过。 -->
        base.put("actorName", "");
        base.put("actorCertTypeId", "");
        base.put("actorPhone", "");
        base.put("actorCertNum", "");
        base.put("contact", "");
        base.put("contactPhone", "");
        base.put("contactAddress", "");
        base.put("remark", "");

        return base;
    }

    private Map PreTradeUser(Map msg, Map bodyBack, Map ext) {
        Map tradeUser = new HashMap();
        List<Map> item = new ArrayList<Map>();
        Map sp = new HashMap();
        sp.put("xDatatype", "");
        sp.put("cityCode", ((List<Map>) bodyBack.get("userInfo")).get(0).get("cityCode").toString());
        sp.put("userPasswd", null == ((List<Map>) bodyBack.get("userInfo")).get(0).get("userCode") ? ""
                : ((List<Map>) bodyBack.get("userInfo")).get(0).get("userCode"));// 客户密码
        sp.put("userTypeCode", "0");// 客户类型
        sp.put("scoreValue", "0");// 客户积分
        sp.put("creditClass", "67");// 信用等级
        sp.put("basicCreditValue", "0");// 基本信用度
        sp.put("creditValue", "0");// 信用度
        sp.put("acctTag", "0");// 出帐标志：0-正常处理，1-定时激活，2-待激活用户，Z-不出帐
        sp.put("prepayTag", "0");// 预付费标志：0-后付费，1-预付费。（省内标准）
        sp.put("inDate", RDate.currentTimeStr("yyyyMMddHHmmss"));// 建档时间
        sp.put("openDate", RDate.currentTimeStr("yyyyMMddHHmmss"));// 开户时间
        sp.put("openMode", "0");// 开户方式：0-正常，1-预开未返单，2-预开已返单，3-过户新增，4-当日返单并过户
        sp.put("openDepartId", ((List<Map>) bodyBack.get("userInfo")).get(0).get("openDepartId").toString());// 开户渠道
        sp.put("openStaffId", ((List<Map>) bodyBack.get("userInfo")).get(0).get("openStaffId").toString());// 开户员工
        sp.put("inDepartId", ((List<Map>) bodyBack.get("userInfo")).get(0).get("inDepartId") + "");// 建档渠道
        sp.put("inStaffId", ((List<Map>) bodyBack.get("userInfo")).get(0).get("inStaffId") + "");// 建档员工
        sp.put("removeTag", "0");// 注销标志：0-正常、1-主动预销号、2-主动销号、3-欠费预销号、4-欠费销号、5-开户返销、6-过户注销
        sp.put("userStateCodeset", "0");// 主体服务状态集：见服务状态参数表
        sp.put("mputeMonthFee", "0");// -固定费用重算标志：0-不重算，1-重算，2-从月初开始重算
        sp.put("developStaffId", msg.get("recomPersonId"));
        sp.put("developEparchyCode", "");
        sp.put("developCityCode", "");
        sp.put("developDepartId", ((List<Map>) bodyBack.get("userInfo")).get(0).get("openDepartId").toString());

        sp.put("developDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        sp.put("inNetMode", "0");// 入网方式
        sp.put("productTypeCode", "4G000001");// 当前活动ggggggggggggggggggggg
        // sp.put("productId", "99999830");// 当前活动gggggggggggggggg
        // sp.put("productId", ((List<Map>) msg.get("productInfo")).get(0).get("productId").toString());
        // ((List<Map>) msg.get("productInfo")).get(0).get("productId").toString()
        item.add(sp);

        tradeUser.put("item", item);
        return tradeUser;
    }

    private Map preTradeRes(Map msg) {
        ArrayList item = new ArrayList();
        HashMap tradeItem = new HashMap();
        HashMap tempMap1 = new HashMap();
        HashMap tempMap2 = new HashMap();
        tempMap1.put("xDatatype", "");
        tempMap1.put("reTypeCode", "0");// 资源类型
        tempMap1.put("resCode", msg.get("numId").toString());// 资源类型编码
        tempMap1.put("modifyTag", "0");// 状态属性：0-增加，1-删除，2-变更
        tempMap1.put("startDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        tempMap1.put("endDate", "20501231235959");

        tempMap2.put("xDatatype", "");
        tempMap2.put("reTypeCode", "0");// 资源类型
        tempMap2.put("resCode", msg.get("numId").toString());// 资源类型编码
        tempMap2.put("resInfo1", "03105956359");// 资源类型编码
        tempMap2.put("modifyTag", "0");// 状态属性：0-增加，1-删除，2-变更
        item.add(tempMap1);
        // sitem.add(tempMap2);
        tradeItem.put("item", item);
        return tradeItem;
    }

    private Map preTradePayrelation(Map msg, Map bodyBack) {
        ArrayList item = new ArrayList();
        HashMap tradeItem = new HashMap();
        HashMap tempMap1 = new HashMap();
        tempMap1.put("acctId", ((List<Map>) bodyBack.get("acctInfo")).get(0).get("acctId").toString());// 帐户标识 TODO
        tempMap1.put("payitemCode", "-1");// 付费项编码
        tempMap1.put("acctPriority", "0");// 帐户优先级
        tempMap1.put("userPriority", "0");// 用户优先级
        tempMap1.put("bindType", "1");
        tempMap1.put("startAcycId", RDate.currentTimeStr("yyyyMM"));
        tempMap1.put("endAcycId", "205001");
        tempMap1.put("defaultTag", "1");
        tempMap1.put("limitType", "1");
        tempMap1.put("limit", "0");
        tempMap1.put("complementTag", "0");
        tempMap1.put("addupMonths", "0");
        tempMap1.put("addupMethod", "0");
        tempMap1.put("payrelationId", "");// TODO
        tempMap1.put("actTag", "1");
        item.add(tempMap1);
        tradeItem.put("item", item);
        return tradeItem;
    }

	private Map preTradeItem(Map msg, String appCode) {
		List<Map> item = new ArrayList<Map>();
		Map tradeItem = new HashMap();
		if (new ChangeCodeUtils().isWOPre(appCode)) {
			item.add(LanUtils.createTradeItem("WORK_TRADE_ID",
					msg.get("ordersId")));
		} else {
			item.add(LanUtils.createTradeItem("WORK_TRADE_ID", ""));
		}
		item.add(LanUtils.createTradeItem("BSS_GROUP_ID", "1210000506264058"));
		item.add(LanUtils.createTradeItem("isBssGroup", "X3"));
		item.add(LanUtils.createTradeItem("TRANS_TYPE_B", "1"));
		item.add(LanUtils.createTradeItem("REL_COMP_PROD_ID", "89230123"));
		item.add(LanUtils.createTradeItem("COMP_DEAL_STATE", "1"));
		item.add(LanUtils.createTradeItem("ROLE_CODE_B", "3"));
		item.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", "1"));
		String serialNumber = (String) msg.get("numId");
		item.add(LanUtils.createTradeItem("PH_NUM", serialNumber));
		item.add(LanUtils.createTradeItem("EXTRA_INFO", ""));
		item.add(LanUtils.createTradeItem("OPER_CODE", "1"));
		item.add(LanUtils.createTradeItem("WORK_DEPART_ID", ""));
		item.add(LanUtils.createTradeItem("WORK_STAFF_ID", ""));
		item.add(LanUtils.createTradeItem("USER_PASSWD", ""));
		item.add(LanUtils.createTradeItem("REOPEN_TAG", "2"));
		item.add(LanUtils.createTradeItem("NEW_PASSWD", ""));
		item.add(LanUtils.createTradeItem("EXISTS_ACCT", "1"));
		item.add(LanUtils.createTradeItem("SFGX_2060", "Y"));
		item.add(LanUtils.createTradeItem("GXLX_TANGSZ", "GZKD:1:40"));
		item.add(LanUtils.createTradeItem("ALONE_TCS_COMP_INDEX", "2"));
        List<Map> bindInfo = (List) msg.get("bindInfo");
        if (bindInfo != null && bindInfo.size() > 0) {
            item.add(LanUtils.createTradeItem("BINDING_SRC", bindInfo.get(0).get("bindSrc")));
        }
		tradeItem.put("item", item);
		return tradeItem;
	}

    private Map preTradeSubItem(Map msg, Exchange exchange, Map bodyBack) {
        List<Map> item = new ArrayList<Map>();
        Map tradeItem = new HashMap();
        String itemId1 = GetSeqUtil.getSeqFromCb(exchange, "seq_item_id");
        Exchange exchangeA = ExchangeUtils.ofCopy(exchange, msg);
        String itemId2 = GetSeqUtil.getSeqFromCb(exchangeA, "seq_item_id");
        Exchange exchangeB = ExchangeUtils.ofCopy(exchangeA, msg);
        String itemId3 = GetSeqUtil.getSeqFromCb(exchangeB, "seq_item_id");
        Exchange exchangeC = ExchangeUtils.ofCopy(exchangeB, msg);
        String itemId4 = GetSeqUtil.getSeqFromCb(exchangeC, "seq_item_id");
        item.add(lan.createTradeSubItemB1(itemId1, "isHerited", "1", "20501231000000"));
        item.add(lan.createTradeSubItemB1(itemId2, "isHerited", "1", "20501231000000"));
        item.add(lan.createTradeSubItemB1(itemId3, "isHerited", "1", "20501231000000"));
        item.add(lan.createTradeSubItemB2(itemId4, "ADDRESS_ID", "-1"));
        item.add(lan.createTradeSubItemB2(itemId4, "SHARE_NBR", "031005582124"));
        item.add(lan.createTradeSubItemB2(itemId4, "USETYPE", "1"));
        item.add(lan.createTradeSubItemB2(itemId4, "MODULE_EXCH_ID", ""));
        item.add(lan.createTradeSubItemB2(itemId4, "ACCESS_TYPE", "B00"));
        item.add(lan.createTradeSubItemB2(itemId4, "serialNumber", msg.get("numId").toString()));
        item.add(lan.createTradeSubItemB2(itemId4, "AREA_CODE",
                ((List<Map>) bodyBack.get("userInfo")).get(0).get("cityCode").toString()));
        item.add(lan.createTradeSubItemB2(itemId4, "transeId", "1"));
        item.add(lan.createTradeSubItemB2(itemId4, "POINT_EXCH_ID", "3362784"));
        item.add(lan.createTradeSubItemB2(itemId4, "TERMINAL_SN", ""));
        item.add(lan.createTradeSubItemB2(itemId4, "ISFLAG114", "0"));
        item.add(lan.createTradeSubItemB2(itemId4, "TERMINAL_MAC", ""));
        item.add(lan.createTradeSubItemB2(itemId4, "SWITCH_EXCH_ID", ""));
        item.add(lan.createTradeSubItemB2(itemId4, "AREA_EXCH_ID", "31003"));
        item.add(lan.createTradeSubItemB2(itemId4, "MOFFICE_ID", "3362784"));
        item.add(lan.createTradeSubItemB2(itemId4, "OLD_NET_TYPE_CODE_SF", "30"));
        item.add(lan.createTradeSubItemB2(itemId4, "COMMUNIT_ID", "-1"));
        item.add(lan.createTradeSubItemB2(itemId4, "USER_PASSWD", "123456"));
        item.add(lan.createTradeSubItemB2(itemId4, "OLD_CUST_ID_SF", "151007155589"));
        item.add(lan.createTradeSubItemB2(itemId4, "DIRECFLAG", "0"));
        item.add(lan.createTradeSubItemB2(itemId4, "isBssGroup", "X3"));
        item.add(lan.createTradeSubItemB2(itemId4, "CB_ACCESS_TYPE", "A03"));
        item.add(lan.createTradeSubItemB2(itemId4, "WorkId", "0"));
        item.add(lan.createTradeSubItemB2(itemId4, "COMMPANY_NBR", ""));
        item.add(lan.createTradeSubItemB2(itemId4, "DETAIL_INSTALL_ADDRESS", msg.get("installAddress")));// installAddress
        item.add(lan.createTradeSubItemB2(itemId4, "TRANS_TYPE_B", "1"));
        item.add(lan.createTradeSubItemB2(itemId4, "TOWN_FLAG", "C"));
        item.add(lan.createTradeSubItemB2(itemId4, "TERMINAL_MODEL", ""));
        item.add(lan.createTradeSubItemB2(itemId4, "IS_TRANSMIT", "1"));
        item.add(lan.createTradeSubItemB2(itemId4, "LOCAL_NET_CODE", "0310"));
        item.add(lan.createTradeSubItemB2(itemId4, "INSTALL_ADDRESS", msg.get("installAddress")));
        item.add(lan.createTradeSubItemB2(itemId4, "BSS_GROUP_ID", "1210000506264058"));
        item.add(lan.createTradeSubItemB2(itemId4, "PROJECGT_ID", ""));
        item.add(lan.createTradeSubItemB2(itemId4, "TERMINAL_TYPE", "0"));
        item.add(lan.createTradeSubItemB2(itemId4, "TERMINAL_BRAND", ""));
        item.add(lan.createTradeSubItemB2(itemId4, "COMMUNIT_NAME", "-1"));
        item.add(lan.createTradeSubItemB2(itemId4, "INIT_PASSWD", "1"));
        item.add(lan.createTradeSubItemB2(itemId4, "OLD_ACCT_ID_SF", "151108368411"));
        item.add(lan.createTradeSubItemB2(itemId4, "COLLINEAR_TYPE", "X3"));

        // 接口增加需要绑定的手机号码，通过属性，将手机号绑定在冰淇淋套餐的宽带上
        List<Map> bindInfo = (List) msg.get("bindInfo");
        if (bindInfo != null && bindInfo.size() > 0) {
            item.add(lan.createTradeSubItem("BINDING_TYPE", bindInfo.get(0).get("bindType"), itemId4));
            item.add(lan.createTradeSubItem("BINDING_SERIALNUM", bindInfo.get(0).get("bindSerialNumber"),
                    itemId4));
            item.add(lan.createTradeSubItem("BINDING_USERID", bindInfo.get(0).get("bindUserId"), itemId4));
        }
        tradeItem.put("item", item);
        return tradeItem;
    }

    // 准备 TRADE_CUSTOMER节点
    private Map pretradeCustomer(Map msg) {

        List<Map> newUserInfo = (List<Map>) msg.get("customerInfo");
        Map tradeCustomer = new HashMap();
        Map item = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        item.put("sustId", custId);
        item.put("xDatatype", "NULL");
        item.put("custName", newUserInfo.get(0).get("certName"));
        item.put("custType", "0"); // 0个人用户 1集团客户
        item.put("custState", "0"); // 0在网
        item.put("psptTypeCode", CertTypeChangeUtils.certTypeMall2Cbss((String) newUserInfo.get(0).get("certType")));
        item.put("psptId", newUserInfo.get(0).get("certNum"));
        item.put("openLimit", "0"); // 不限制开户数
        item.put("eparchyCode", ChangeCodeUtils.changeEparchy(msg));
        item.put("cityCode", ChangeCodeUtils.changeEparchy(msg));
        item.put("developDepartId", msg.get("recomPersonChannelId"));
        item.put("inDate", GetDateUtils.getDate());
        item.put("removeTag", "0");
        item.put("rsrvTag1", "2");

        itemList.add(item);
        tradeCustomer.put("item", itemList);
        return tradeCustomer;
    }

    private Map preTradeCustPerson(Map msg, Map bodyBack) {
        List<Map> customerInfoMsg = (List<Map>) msg.get("customerInfo");
        List<Map> customerInfoCheck = (List<Map>) bodyBack.get("custInfo");
        Map TradeCustPerson = new HashMap();
        Map item = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        item.put("xDatatype", "NULL");
        item.put("sustId", custId);
        item.put("psptTypeCode", CertTypeChangeUtils.certTypeMall2Cbss((String) customerInfoMsg.get(0).get("certType")));
        item.put("psptId", (customerInfoCheck.get(0).get("certCode")));
        item.put("psptEndDate", (customerInfoCheck.get(0).get("certEndDate")));
        item.put("psptAddr", (customerInfoCheck.get(0).get("certAddr")));
        item.put("custName", (customerInfoMsg.get(0).get("custName")));
        item.put("sex", (customerInfoCheck.get(0).get("sex")));
        item.put("eparchyCode", (customerInfoCheck.get(0).get("eparchyCode")));
        item.put("cityCode", (customerInfoCheck.get(0).get("cityCode")));
        item.put("birthday", (customerInfoCheck.get(0).get("birthday")));
        item.put("birthdayLunar", (customerInfoCheck.get(0).get("birthdayLunar")));
        item.put("birthdayFlag", (customerInfoCheck.get(0).get("birthdayFlag")));
        item.put("postAddress", (customerInfoCheck.get(0).get("postAddress")));
        item.put("postCode", (customerInfoCheck.get(0).get("postCode")));
        item.put("birthdayPerson", (customerInfoCheck.get(0).get("postPerson")));
        item.put("phone", (customerInfoCheck.get(0).get("phone")));
        item.put("faxNbr", (customerInfoCheck.get(0).get("faxNbr")));
        item.put("email", (customerInfoCheck.get(0).get("email")));
        item.put("homeAddress", (customerInfoCheck.get(0).get("homeAddress")));
        item.put("homePhone", (customerInfoCheck.get(0).get("homePhone")));
        item.put("contact", (customerInfoCheck.get(0).get("contact")));
        item.put("contactPhone", (customerInfoCheck.get(0).get("contactPhone")));
        item.put("contactTypeCode", "0");
        // item.put("nationlityCode", (customerInfoCheck.get(0).get("nationalityCode")));
        item.put("localNativeCode", (customerInfoCheck.get(0).get("localNativeCode")));
        item.put("folkCode", (customerInfoCheck.get(0).get("folkCode")));
        item.put("religionCode", (customerInfoCheck.get(0).get("religionCode")));
        item.put("revenumLevelCode", (customerInfoCheck.get(0).get("revenumLevelCode")));
        item.put("educateDegreeCode", (customerInfoCheck.get(0).get("educateGradeCode")));
        item.put("educateGradeCode", (customerInfoCheck.get(0).get("educateGradeCode")));
        item.put("graduateSchool", (customerInfoCheck.get(0).get("graduateSchool")));
        item.put("speciality", (customerInfoCheck.get(0).get("speciality")));
        item.put("characterTypeCode", (customerInfoCheck.get(0).get("characterTypeCode")));
        item.put("healthStateCode", (customerInfoCheck.get(0).get("healthStateCode")));
        item.put("removeTag", "0");
        itemList.add(item);
        TradeCustPerson.put("item", itemList);

        return TradeCustPerson;
    }
}
