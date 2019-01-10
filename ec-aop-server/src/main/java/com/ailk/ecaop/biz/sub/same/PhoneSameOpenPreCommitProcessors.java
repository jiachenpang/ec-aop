package com.ailk.ecaop.biz.sub.same;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MixOpenUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.alibaba.fastjson.JSON;

// 固话同装预提交
@EcRocTag("PhoneSameOpenPreCommit")
public class PhoneSameOpenPreCommitProcessors extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.masb.posa.N6.phoneSameOpenParametersMapping",
            "ecaop.masb.posa.phoneSameOpenParametersMapping", "ecaop.masb.chph.gifa.ParametersMapping", };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[3];

    private String userId;
    private String acctId;
    private String custId;
    private final String startDate = GetDateUtils.getDate();
    private boolean isN6;
    private boolean is3G;

    @Override
    public void process(Exchange exchange) throws Exception {
        // 请求前的参数备份
        Map body = (Map) exchange.getIn().getBody();
        Map msg = (Map) body.get("msg");

        String opeSysType = (String) msg.get("opeSysType");
        String province = (String) msg.get("province");
        is3G = "1".equals(opeSysType) || opeSysType == null;
        isN6 = is3G && "17|18|11|76|91|97".contains(province);

        // 参数处理
        dealInparams(exchange, isN6);

        // 调用预提交接口
        exchange.setMethodCode("posa");
        LanUtils lan = new LanUtils();
        if (isN6) {
            lan.preData(pmp[0], exchange);
            CallEngine.wsCall(exchange,
                    "ecaop.comm.conf.url.n6ess.services.AOPOrdForNorthSer" + "." + msg.get("province"));
        }
        else {
            lan.preData(pmp[1], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        }
        lan.xml2Json("ecaop.masb.psoa.phoneSameOpenTemplate", exchange);

        Map message = exchange.getOut().getBody(Map.class);
        String provOrderId = "";
        if (null != message.get("rspInfo")) {
            List<Map> rspInfo = (List<Map>) message.get("rspInfo");
            // 处理返回参数
            Map result = dealReturn(rspInfo.get(0), msg);
            if (null == result.get("userName")) {
                String userName = (String) ((Map) msg.get("newUserInfo")).get("certName");
                result.put("userName", userName);
                provOrderId = (String) result.get("provOrderId");
            }
            exchange.getOut().setBody(result);
        }

        // 信息入库
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        Map infoMap = new HashMap();
        infoMap.put("SERIALNUMBER", msg.get("serialNumber"));
        List result = dao.queryThreePartInfo(infoMap);
        if (null == result || result.size() <= 0) {
            infoMap.put("ACCT_ID", acctId);
            infoMap.put("CUST_ID", custId);
            infoMap.put("USER_ID", userId);
            infoMap.put("EPARCHY_CODE", ChangeCodeUtils.changeEparchy(msg));
            infoMap.put("PROVINCE_CODE", msg.get("province"));
            infoMap.put("SUBSCRIBE_ID", provOrderId);
            infoMap.put("REMARK", "");
            dao.insertThreePartInfo(infoMap);
        }
        else {
            throw new EcAopServerBizException("9999", "该号码不是新号码");
        }
    }

    /**
     * 处理返回信息
     */
    private Map dealReturn(Map rspInfo, Map msg) {
        String serialNumber = (String) msg.get("serialNumber"); // 固话号码
        String userPasswd = serialNumber.substring(serialNumber.length() - 6, serialNumber.length());

        Map ret = new HashMap();
        ret.put("provOrderId", isNull(rspInfo.get("bssOrderId").toString()));
        ret.put("userPasswd", userPasswd);
        if (null != rspInfo.get("provinceOrderInfo")) {
            List<Map> provinceOrderInfoList = (List<Map>) rspInfo.get("provinceOrderInfo");
            for (Map provinceOrderInfo : provinceOrderInfoList) {
                ret.put("debutyAreaCode", provinceOrderInfo.get("areaCode"));
                if (null != provinceOrderInfo.get("preFeeInfoRsp")) {
                    List<Map> feeInfo = (List<Map>) provinceOrderInfo.get("preFeeInfoRsp");
                    List<Map> newFeeInfo = new ArrayList<Map>();
                    if (0 != feeInfo.size()) {
                        for (Map fee : feeInfo) {
                            Map newFee = dealFee(fee);
                            newFeeInfo.add(newFee);
                        }
                        ret.put("feeInfo", newFeeInfo);
                    }
                }
                String totalFee = (String) provinceOrderInfo.get("totalFee");
                ret.put("totalFee", totalFee);
            }
        }

        return ret;
    }

    /**
     * 费用的处理
     */
    private Map dealFee(Map oldFeeInfo) {
        Map feeInfo = new HashMap();
        feeInfo.put("feeId", isNull((String) oldFeeInfo.get("feeTypeCode")));
        feeInfo.put("feeCategory", isNull((String) oldFeeInfo.get("feeMode")));
        feeInfo.put("feeDes", isNull((String) oldFeeInfo.get("feeTypeName")));
        feeInfo.put("maxRelief", isNull((String) oldFeeInfo.get("maxDerateFee")));
        feeInfo.put("origFee", isNull(oldFeeInfo.get("fee").toString()));
        return feeInfo;
    }

    /* 固话预提交参数准备 */
    private void dealInparams(Exchange exchange, boolean isN6) throws Exception {
        // 请求参数的备份
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = null;
        if (body.get("msg") instanceof Map) {
            msg = (Map) body.get("msg");
        }
        else {
            msg = JSON.parseObject(body.get("msg").toString());
        }
        msg.put("serialMode", "1");
        String eparchyCode = ChangeCodeUtils.changeEparchy(msg);
        String custType = (String) msg.get("custType");
        if ("0".equals(custType)) { // 新用户
            if (isN6) {
                custId = GetSeqUtil.getSeqFromN6ess(exchange, "CUST_ID", eparchyCode);
                exchange.getIn().setBody(body);
            }
            else {
                custId = (String) GetSeqUtil.getSeqFromCb(pmp[2], exchange, "seq_cust_id", 1).get(0);
                exchange.getIn().setBody(body);
            }
        }
        else if ("1".equals(custType)) {
            custId = (String) msg.get("custId");
        }
        String tradeId = null;
        String payrelationId = null;
        if (isN6) {
            userId = GetSeqUtil.getSeqFromN6ess(exchange, "USER_ID", eparchyCode);
            exchange.getIn().setBody(body);
            acctId = GetSeqUtil.getSeqFromN6ess(exchange, "ACCT_ID", eparchyCode);
            exchange.getIn().setBody(body);
            tradeId = GetSeqUtil.getSeqFromN6ess(exchange, "TRADE_ID", eparchyCode);
            exchange.getIn().setBody(body);
            payrelationId = GetSeqUtil.getSeqFromN6ess(exchange, "TRADE_ID", eparchyCode);
            exchange.getIn().setBody(body);
        }
        else {
            userId = (String) GetSeqUtil.getSeqFromCb(pmp[2], exchange, "seq_user_id", 1).get(0);
            exchange.getIn().setBody(body);
            acctId = (String) GetSeqUtil.getSeqFromCb(pmp[2], exchange, "seq_acct_id", 1).get(0);
            exchange.getIn().setBody(body);
            tradeId = (String) GetSeqUtil.getSeqFromCb(pmp[2], exchange, "seq_trade_id", 1).get(0);
            exchange.getIn().setBody(body);
            payrelationId = (String) GetSeqUtil.getSeqFromCb(pmp[2], exchange, "seq_payrela_id", 1).get(0);
            exchange.getIn().setBody(body);
        }

        Map newUserInfo = (Map) msg.get("newUserInfo");
        List<Map> activityInfos = (List<Map>) newUserInfo.get("activityInfo");
        List<Map> productInfos = (List<Map>) newUserInfo.get("productInfo");

        msg.put("ordersId", msg.get("orderNo"));
        msg.put("operTypeCode", "0");

        msg.put("userId", userId);

        // EXT
        String provinceCode = "00" + (String) msg.get("province");
        Map ext = new HashMap();
        String productId = "";
        msg.put("activityInfo", activityInfos);
        // 方法会返回 TRADE_PRODUCT_TYPE TRADE_PRODUCT TRADE_DISCNT TRADE_SVC
        for (Map product : productInfos) {
            if ("1".equals(product.get("productMode"))) {
                product.put("productMode", "0");
            }
            else {
                product.put("productMode", "1");
            }
            productId = (String) productInfos.get(0).get("productId");
        }
        // 北六E跟CB业务的产品处理调用不同的方法
        if (is3G && isN6) {
            ext = TradeManagerUtils.preProductInfo(productInfos, provinceCode, msg);
        }
        else {
            ext = TradeManagerUtils.preProductInfo(productInfos, provinceCode, msg);
        }

        ext.remove("tradeSp");
        String cbAccessType = null;
        String accessMode = null;
        if (!isN6) {
            // 转换接入方式
            Map inMap = new HashMap();
            inMap.put("ACCESS_TYPE", newUserInfo.get("accessMode"));
            inMap.put("CB_ACCESS_TYPE", newUserInfo.get("cbssAccessMode"));
            inMap.put("PROVINCE_CODE", msg.get("province"));
            inMap.put("NET_TYPE_CODE", "30");
            MixOpenUtils mixOpenUtil = new MixOpenUtils();
            Map accesMap = mixOpenUtil.checkAccessCode(inMap);
            cbAccessType = (String) accesMap.get("cbssAccessMode");
            accessMode = (String) accesMap.get("accessMode");
        }
        if (is3G) {
            msg.put("inModeCode", "E");
        }
        else {
            msg.put("inModeCode", new ChangeCodeUtils().getInModeCode(exchange.getAppCode()));
        }
        // base
        msg.put("base", preBase(msg, ext, userId, custId, acctId, tradeId));

        ext.put("tradeAcct", preTradeAcct(msg, acctId, userId));
        ext.put("tradeUser", preTradeUser(msg, ext, custId, productId));
        ext.put("tradeRes", preTradeRes(msg));
        ext.put("tradePayrelation", preTradePayreLation(userId, acctId, payrelationId));
        if (!isN6) {
            ext.put("tradeOther", preTradeOther(msg));
            ext.put("tradeCustomer", pretradeCustomer(msg));
        }

        // TRADE_ITEM
        Map tradeItem = new HashMap();

        List<Map> itemList = new ArrayList<Map>();
        String channelType = (String) msg.get("channelType");
        String serialNumber = (String) msg.get("serialNumber"); // 固话号码
        String userPasswd = serialNumber.substring(serialNumber.length() - 6, serialNumber.length());
        LanUtils lan = new LanUtils();

        String developStaffId = (String) msg.get("recomPersonId");
        itemList.add(lan.createAttrInfoNoTime("DEVELOP_STAFF_ID", isNull(developStaffId)));
        String developDepartId = (String) msg.get("recomPersonChannelId");
        itemList.add(lan.createAttrInfoNoTime("DEVELOP_DEPART_ID", isNull(developDepartId)));
        itemList.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", channelType));
        itemList.add(LanUtils.createTradeItem("PH_NUM", serialNumber));
        itemList.add(LanUtils.createTradeItem("NO_BOOK_REASON", ""));
        itemList.add(LanUtils.createTradeItem("USER_PASSWD", userPasswd));
        itemList.add(LanUtils.createTradeItem("EXISTS_ACCT", "0"));
        itemList.add(LanUtils.createTradeItem("SUB_TYPE", "0"));
        itemList.add(LanUtils.createTradeItem("REOPEN_TAG", "2"));
        itemList.add(LanUtils.createTradeItem("NEW_PASSWD", userPasswd));
        itemList.add(LanUtils.createTradeItem("SFGX_2060", "Y"));
        if (isN6) {
            // CB报文中没有
            itemList.add(LanUtils.createTradeItem("GXLX_TANGSZ", "ADSL:1")); // 值不同
            itemList.add(lan.createAttrInfoNoTime("MARKETING_MODE", ""));
            itemList.add(LanUtils.createTradeItem("SCHE_ID", ""));
            itemList.add(LanUtils.createTradeItem("RES_PRE_ORDER", "0"));
            itemList.add(LanUtils.createTradeItem("IMMEDIACY_INFO", "0"));
            itemList.add(LanUtils.createTradeItem("USER_ACCPDATE", ""));
            itemList.add(LanUtils.createTradeItem("PRE_END_TIME", GetDateUtils.getSysdateFormat()));
        }
        else {
            itemList.add(LanUtils.createTradeItem("GXLX_TANGSZ", "GZKD:1:40")); // 值不同
            itemList.add(LanUtils.createTradeItem("EXTRA_INFO", "")); // 修改
            itemList.add(LanUtils.createTradeItem("WORK_STAFF_ID", "")); // 修改
            itemList.add(LanUtils.createTradeItem("WORK_TRADE_ID", "")); // 修改
            itemList.add(LanUtils.createTradeItem("BOOK_FLAG", "0")); // 修改
            itemList.add(LanUtils.createTradeItem("WORK_DEPART_ID", "")); // 修改
            itemList.add(LanUtils.createTradeItem("PRE_START_TIME", "")); // 修改
        }
        if (new ChangeCodeUtils().isWOPre(exchange.getAppCode())) {
            itemList.add(LanUtils.createTradeItem("WORK_TRADE_ID", msg.get("orderNo")));
        }

        tradeItem.put("item", itemList);
        ext.put("tradeItem", tradeItem);

        // TRADE_SUB_ITEM
        String itemId = null;
        if (isN6) {
            itemId = GetSeqUtil.getSeqFromN6ess(exchange, "ITEM_ID", eparchyCode);
            exchange.getIn().setBody(body);
        }
        else {
            itemId = (String) GetSeqUtil.getSeqFromCb(pmp[2], exchange, "seq_trade_id", 1).get(0);
            exchange.getIn().setBody(body);
        }

        Map tradeSubItem = new HashMap();
        itemList = new ArrayList<Map>();
        if (isN6) {
            String endDate = "20501231235959";
            itemList.add(lan.createTradeSubItemC(itemId, "adEnd", "", startDate, endDate)); // 没有开始时间和结束时间
            itemList.add(lan.createTradeSubItemC(itemId, "expireDealMode", "a", startDate, endDate));
            itemList.add(lan.createTradeSubItemC(itemId, "aDiscntCode", "b", startDate, endDate));
            itemList.add(lan.createTradeSubItemC(itemId, "bDiscntCode", "", startDate, endDate)); //
            itemList.add(lan.createTradeSubItemC(itemId, "agrnotifynumber", "", startDate, endDate));
            itemList.add(lan.createTradeSubItemC(itemId, "callBack", "0", startDate, endDate));
            itemList.add(lan.createTradeSubItemC(itemId, "adStart", "0", startDate, endDate));
            itemList.add(lan.createTradeSubItemC(itemId, "recharge", "", startDate, endDate));
            itemList.add(lan.createTradeSubItemC(itemId, "cycleNum", "1", startDate, endDate));
            itemList.add(lan.createTradeSubItemC(itemId, "fixedHire", "12", startDate, endDate));
            itemList.add(lan.createTradeSubItemC(itemId, "bcafixedhireflag", "0", startDate, endDate));
            itemList.add(lan.createTradeSubItemC(itemId, "cycle", "12", startDate, endDate));
            itemList.add(lan.createTradeSubItemC(itemId, "cycleFee", "990", startDate, endDate));

            itemList.add(lan.createTradeSubItem("DIRECTION", "1", itemId));
            itemList.add(lan.createTradeSubItem("ISREVERSEPOLE", "0", itemId));
            itemList.add(lan.createTradeSubItem("CUST_MANAGER_ID", "", itemId));
            itemList.add(lan.createTradeSubItem("RENTDAYS", "", itemId));
            itemList.add(lan.createTradeSubItem("AREA_CODE", ChangeCodeUtils.changeEparchy(msg), itemId));
            itemList.add(lan.createTradeSubItem("CHARGE_TYPE", "1", itemId));
            itemList.add(lan.createTradeSubItem("ISFLAG114", "0", itemId));
            itemList.add(lan.createTradeSubItem("IS_CHOOSE_SN", "0", itemId));
            itemList.add(lan.createTradeSubItem("SERVICEUSAGEKEY", "110011", itemId)); // 不确定
            itemList.add(lan.createTradeSubItem("NGN_MARK", "0", itemId));
            itemList.add(lan.createTradeSubItem("DIRECFLAG", "0", itemId));
            itemList.add(lan.createTradeSubItem("CUST_MANAGER_NAME", "", itemId));
            itemList.add(lan.createTradeSubItem("PRE_START_TIME", "", itemId));
            itemList.add(lan.createTradeSubItem("PTDH_ORIGINALCOMPANYNB", "", itemId));
            itemList.add(lan.createTradeSubItem("NAME", "", itemId));
            itemList.add(lan.createTradeSubItem("REGION_SSP", "210202", itemId)); // 不确定
            itemList.add(lan.createTradeSubItem("COUNTRYFLAG", "", itemId));
            itemList.add(lan.createTradeSubItem("LOCAL_NET_CODE", ChangeCodeUtils.changeEparchy(msg), itemId));

            String installAddress = (String) msg.get("installAddress");
            itemList.add(lan.createTradeSubItem("INSTALL_ADDRESS", installAddress, itemId));
            itemList.add(lan.createTradeSubItem("RELAYBRANCHNUMBER", "", itemId));
            itemList.add(lan.createTradeSubItem("PTDH_HOMELINEPROJECT", "", itemId));
            itemList.add(lan.createTradeSubItem("SHARE_PHONE", "", itemId));
            itemList.add(lan.createTradeSubItem("CUSTOMER_GROUP", "", itemId));
            itemList.add(lan.createTradeSubItem("COMMUNIT_NAME", "", itemId));
            itemList.add(lan.createTradeSubItem("INIT_PASSWD", "0", itemId));
            itemList.add(lan.createTradeSubItem("LINK_NAME", "unknow", itemId)); // 不知道
            itemList.add(lan.createTradeSubItem("PHONESOURCEKEY", "3", itemId));

            itemList.add(lan.createTradeSubItem("PHONETYPEKEY", "1", itemId));
            itemList.add(lan.createTradeSubItem("PTDH_ISPRIMARYINDEX", "0", itemId));
            itemList.add(lan.createTradeSubItem("PREPAY_TAG", "0", itemId));

            String addressId = (String) msg.get("addressCode");
            itemList.add(lan.createTradeSubItem("ADDRESS_ID", addressId, itemId));
            itemList.add(lan.createTradeSubItem("RESOURCEPIECEID", "", itemId));
            itemList.add(lan.createTradeSubItem("CABLEACCESSTYPE", "1", itemId));
            itemList.add(lan.createTradeSubItem("BILLPRIVILEDGE", "0", itemId));
            itemList.add(lan.createTradeSubItem("SHARE_NBR", "", itemId));
            itemList.add(lan.createTradeSubItem("PTDH_ORIGINALCOMPANYKEY", "", itemId));
            itemList.add(lan.createTradeSubItem("PTDH_DIRECTNUMBER", "", itemId));
            itemList.add(lan.createTradeSubItem("PTDH_SHOWNAME", "", itemId));
            itemList.add(lan.createTradeSubItem("SERVICEUSERTYPEKEY", "110001", itemId)); // 不确定
            itemList.add(lan.createTradeSubItem("PTDH_STOCKCODE", "", itemId));
            itemList.add(lan.createTradeSubItem("BOOK_FLAG", "0", itemId));
            itemList.add(lan.createTradeSubItem("USER_PASSWD", userPasswd, itemId)); // 不确定
            itemList.add(lan.createTradeSubItem("MOFFICE_ID", newUserInfo.get("exchCode") + "", itemId));
            itemList.add(lan.createTradeSubItem("COMMUNIT_ID", "0", itemId));
            itemList.add(lan.createTradeSubItem("USER_CALLING_AREA", ChangeCodeUtils.changeEparchy(msg), itemId)); // 不确定
            itemList.add(lan.createTradeSubItem("PHONECATEGORYKEY", "-1", itemId));
            itemList.add(lan.createTradeSubItem("PHONEMODELKEY", "-1", itemId));
            itemList.add(lan.createTradeSubItem("DETAIL_INSTALL_ADDRESS", installAddress, itemId));// 不确定
            itemList.add(lan.createTradeSubItem("DEVICESOURCEDESC", "1", itemId));
            itemList.add(lan.createTradeSubItem("BUSI_CODE", "910571", itemId)); //
            itemList.add(lan.createTradeSubItem("SHOW_ORDER", "", itemId));
            itemList.add(lan.createTradeSubItem("SECRECYTYPE", "", itemId));
            itemList.add(lan.createTradeSubItem("PTDH_ISNETVIEWPHONE", "", itemId));
            itemList.add(lan.createTradeSubItem("LINK_PHONE", "unknow", itemId));
            itemList.add(lan.createTradeSubItem("PTDH_BOOKCODE2", "", itemId));
            itemList.add(lan.createTradeSubItem("PTDH_BOOKCODE1", "", itemId));
            itemList.add(lan.createTradeSubItem("BILLINGMODE", "999999", itemId));
            itemList.add(lan.createTradeSubItem("ENSNUM", "", itemId));
            itemList.add(lan.createTradeSubItem("SERIAL_NUMBER_ID", "0", itemId));
            itemList.add(lan.createTradeSubItem("PTDH_ISWAITMAINTAIN", "", itemId));
            itemList.add(lan.createTradeSubItemFive("BUSI_CODE", "910571", itemId)); //
            itemList.add(lan.createTradeSubItem("AREA_EXCH_ID", newUserInfo.get("areaExchId"), itemId));
            itemList.add(lan.createTradeSubItem("POINT_EXCH_ID", newUserInfo.get("pointExchId"), itemId));
            itemList.add(lan.createTradeSubItem("MODULE_EXCH_ID", newUserInfo.get("moduleExchId"), itemId));
            itemList.add(lan.createTradeSubItem("PARENT_MOFFICE", "1174240", itemId));
        }
        else {
            String installAddress = isNull((String) msg.get("installAddress"));
            String addressId = isNull((String) newUserInfo.get("addressCode"));

            String isTrue = "3110009"; // 不确定
            String linkName = isNull((String) msg.get("contactPerson"));
            String linkPhone = isNull((String) msg.get("contactPhone"));
            String[] key = { "PREPAY_TAG", "ADDRESS_ID", "SHARE_NBR", "USETYPE", "MODULE_EXCH_ID", "ACCESS_TYPE",
                    "AREA_CODE", "POINT_EXCH_ID", "USER_PASSWD", "TERMINAL_SN", "ISFLAG114", "TERMINAL_MAC",
                    "SWITCH_EXCH_ID", "TERMINALSRC_MODE", "AREA_EXCH_ID", "MOFFICE_ID", "COMMUNIT_ID",
                    "USER_CALLING_AREA", "USER_TYPE_CODE", "DIRECFLAG", "CB_ACCESS_TYPE", "COMMPANY_NBR",
                    "DETAIL_INSTALL_ADDRESS", "TOWN_FLAG", "POSITION_XY", "TERMINAL_MODEL", "LOCAL_NET_CODE",
                    "INSTALL_ADDRESS", "TERMINAL_TYPE", "TERMINAL_BRAND", "COMMUNIT_NAME", "INIT_PASSWD",
                    "COLLINEAR_TYPE", };
            String[] value = { "0", addressId, "", "1", "", accessMode, eparchyCode, isTrue, userPasswd, "", "0", "",
                    "", "A008", "31101", isTrue, "0", eparchyCode, "0", "0", cbAccessType, "", installAddress, "C", "",
                    "", eparchyCode, installAddress, "0", "", "", "0", "X3" };
            itemList = lan.createTradeSubsItemList(key, value, itemId);
            itemList.add(lan.createTradeSubItemE("LINK_NAME", linkName, itemId));
            itemList.add(lan.createTradeSubItemE("OTHERCONTACT", "", itemId));
            itemList.add(lan.createTradeSubItemE("LINK_PHONE", linkPhone, itemId));
        }

        tradeSubItem.put("item", itemList);
        ext.put("tradeSubItem", tradeSubItem);
        msg.put("ext", ext);

        body.put("msg", msg);
        exchange.getIn().setBody(body);

    }

    // BASE节点
    private Map preBase(Map msg, Map ext, String userId, String custId, String acctId, String tradeId) {
        Map newUserInfo = (Map) msg.get("newUserInfo");
        String brandCode = "";
        String productId = "";
        Map tradeProduct = (Map) ext.get("tradeProduct");
        List<Map> productItem = (List<Map>) tradeProduct.get("item");
        if (productItem != null) {
            productId = (String) productItem.get(0).get("productId");
            brandCode = (String) productItem.get(0).get("brandCode");
        }
        String eparchyCode = ChangeCodeUtils.changeEparchy(msg);
        Map base = new HashMap();
        base.put("subscribeId", tradeId);
        base.put("tradeId", tradeId);
        base.put("startDate", startDate);
        base.put("endDate", "20501231235959");
        base.put("acceptDate", startDate);
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        base.put("inModeCode", msg.get("inModeCode"));
        base.put("tradeTypeCode", "0010");
        base.put("productId", isNull(productId)); // 产品ID
        base.put("brandCode", isNull(brandCode)); // 产品中获取
        base.put("userId", userId);
        base.put("custId", custId);
        base.put("usecustId", custId);
        base.put("acctId", acctId);
        base.put("userDiffCode", "00");
        base.put("netTypeCode", "0030");
        base.put("serinalNamber", msg.get("serialNumber")); // 服务号码
        base.put("custName", newUserInfo.get("certName"));
        base.put("termIp", "127.0.0.1");
        base.put("eparchyCode", eparchyCode);
        base.put("execTime", startDate);
        base.put("operFee", "0");
        base.put("foregift", "0");
        base.put("advancePay", "0");
        base.put("cancelTag", "0");
        base.put("checktypeCode", "8");
        base.put("chkTag", "0");
        base.put("feeState", ""); // 新增
        base.put("feeStaffId", "");
        base.put("actorName", "");
        base.put("actorCertTypeId", "");
        base.put("actorPhone", "");
        base.put("actorCertNum", "");
        base.put("contact", "");
        base.put("contactPhone", "");
        base.put("contactAddress", "");
        base.put("remark", "");
        // base.put("cityCode", ChangeCodeUtils.changeEparchy(msg));
        if (isN6) {
            base.put("mainDiscntCode", "");// 主资费标识
            base.put("productSpec", "");// 产品规格
        }
        return base;
    }

    // TRADE_USER节点
    private Map preTradeUser(Map msg, Map ext, String custId, String productId) {
        Map tradeProductType = ((Map) ext.get("tradeProductType"));
        List<Map> items = (List<Map>) tradeProductType.get("item");
        String productTypeCode = "";
        if (items != null) {
            productTypeCode = (String) (items.get(0)).get("productTypeCode");
        }
        String brandCode = "";
        Map tradeProduct = (Map) ext.get("tradeProduct");
        List<Map> productItem = (List<Map>) tradeProduct.get("item");
        if (productItem != null) {
            brandCode = (String) productItem.get(0).get("brandCode");
        }

        String serialNumber = (String) msg.get("serialNumber"); // 固话号码
        String openDepartId = (String) msg.get("channelId"); // 渠道编码
        String openStaffId = (String) msg.get("operatorId"); // 操作员ID
        String userPasswd = serialNumber.substring(serialNumber.length() - 6, serialNumber.length());

        Map tradeUser = new HashMap();
        Map item = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        item.put("xDatatype", "NULL");
        item.put("usecustId", custId);
        item.put("userPasswd", userPasswd);
        item.put("userTypeCode", "0");
        item.put("scoreValue", "0");
        item.put("creditClass", "0");
        item.put("basicCreditValue", "0");
        item.put("creditValue", "0");
        item.put("acctTag", "0");
        item.put("prepayTag", "0");
        item.put("inDate", startDate); // 建档时间
        item.put("openDate", startDate); // 开户时间
        item.put("openMode", "0");
        item.put("cityCode", msg.get("city"));
        item.put("openDepartId", openDepartId);
        item.put("openStaffId", openStaffId);
        item.put("inDepartId", openDepartId);
        item.put("inStaffId", openStaffId);
        item.put("removeTag", "0");
        item.put("userStateCodeset", "0");
        item.put("mputeMonthFee", "0");
        item.put("developStaffId", msg.get("recomPersonId"));
        item.put("developDate", startDate); // 发展时间
        item.put("developEparchyCode", isNull((String) msg.get("recomPersonCityCode")));
        item.put("developCityCode", isNull((String) msg.get("recomPersonDistrict")));
        item.put("developDepartId", isNull((String) msg.get("recomPersonChannelId")));
        item.put("inNetMode", "0");
        item.put("productTypeCode", isNull(productTypeCode)); // 当前活动
        if (!isN6) {// 是CB
            item.put("productId", productId); // 主产品
            item.put("netTypeCode", "0030");
            item.put("brandCode", isNull(brandCode));
        }

        itemList.add(item);
        tradeUser.put("item", itemList);
        return tradeUser;
    }

    // TRADE_ACCT节点
    private Map preTradeAcct(Map msg, String acctId, String userId) {
        String xDatatype = "NULL";
        Map newUserInfo = (Map) msg.get("newUserInfo");
        String payName = (String) newUserInfo.get("certName"); // 证件名称

        Map tradeAcct = new HashMap();
        Map item = new HashMap();
        List<Map> itemList = new ArrayList<Map>();

        item.put("xDatatype", xDatatype);
        item.put("acctId", acctId);
        item.put("payName", payName);
        item.put("payModeCode", "0");
        item.put("scoreValue", "0");
        item.put("creditClassId", "0");
        item.put("basicCreditValue", "0");
        item.put("creditValue", "0");
        item.put("creditControlId", "0");
        item.put("debutyUserId", userId);
        item.put("debutyCode", msg.get("serialNumber"));
        item.put("removeTag", "0");
        item.put("cityCode", msg.get("city"));
        item.put("openDate", startDate);

        itemList.add(item);
        tradeAcct.put("item", itemList);
        return tradeAcct;
    }

    // 准备TRADE_RES节点
    private Map preTradeRes(Map msg) {
        String resCode = (String) msg.get("serialNumber");

        Map tradeRes = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item8 = new HashMap();
        Map item9 = new HashMap();

        item8.put("xDatatype", "NULL");
        item8.put("reTypeCode", "0");
        item8.put("resCode", resCode);
        item8.put("modifyTag", "0");
        item8.put("startDate", startDate);
        item8.put("endDate", "20501231235959");
        item9.put("xDatatype", "NULL");
        item9.put("reTypeCode", "1");
        item9.put("resCode", resCode);
        item9.put("modifyTag", "0");
        item9.put("resInfo1", resCode);

        itemList.add(item8);
        itemList.add(item9);
        tradeRes.put("item", itemList);
        return tradeRes;
    }

    // 准备 TRADE_PAYRELATION节点
    private Map preTradePayreLation(String userId, String acctId, String payrelationId) {
        Map tradePayrelation = new HashMap();
        Map item = new HashMap();
        List<Map> itemList = new ArrayList<Map>();

        item.put("userId", userId);
        item.put("acctId", acctId);
        item.put("payitemCode", "-1");
        item.put("acctPriority", "0");
        item.put("userPriority", "0");
        item.put("bindType", "1");
        item.put("defaultTag", "1");
        item.put("limitType", "0");
        item.put("limit", "0");
        item.put("complementTag", "0");
        item.put("addupMonths", "0");
        item.put("addupMethod", "0");
        item.put("payrelationId", payrelationId);
        item.put("actTag", "1");

        itemList.add(item);
        tradePayrelation.put("item", itemList);
        return tradePayrelation;
    }

    // 准备 TRADE_OTHER节点
    private Map preTradeOther(Map msg) {
        Map newUserInfo = (Map) msg.get("newUserInfo");
        Map tradeOther = new HashMap();
        Map item = new HashMap();
        List<Map> itemList = new ArrayList<Map>();

        item.put("xDatatype", "NULL");
        item.put("rsrvValueCode", "ZZFS");
        item.put("rsrvValue", "2");
        item.put("rsrvStr1", newUserInfo.get("certNum")); // 证件号码
        item.put("rsrvStr2", "10");
        item.put("rsrvStr3", "-9");
        item.put("rsrvStr6", "1");
        item.put("rsrvStr7", "0");
        item.put("modifyTag", "0");
        item.put("startDate", startDate);
        item.put("endDate", "20501231235959");

        itemList.add(item);
        tradeOther.put("item", itemList);
        return tradeOther;
    }

    // 准备 TRADE_CUSTOMER节点
    private Map pretradeCustomer(Map msg) {
        Map newUserInfo = (Map) msg.get("newUserInfo");
        Map tradeCustomer = new HashMap();
        Map item = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        item.put("sustId", custId);
        item.put("xDatatype", "NULL");
        item.put("custName", newUserInfo.get("certName"));
        item.put("custType", "0"); // 0个人用户 1集团客户
        item.put("custState", "0"); // 0在网
        item.put("psptTypeCode", CertTypeChangeUtils.certTypeMall2Cbss((String) newUserInfo.get("certType")));
        item.put("psptId", newUserInfo.get("certNum"));
        item.put("openLimit", "0"); // 不限制开户数
        item.put("eparchyCode", ChangeCodeUtils.changeEparchy(msg));
        item.put("cityCode", ChangeCodeUtils.changeEparchy(msg));
        item.put("developDepartId", msg.get("recomPersonChannelId"));
        item.put("inDate", startDate);
        item.put("removeTag", "0");
        item.put("rsrvTag1", "2");

        itemList.add(item);
        tradeCustomer.put("item", itemList);
        return tradeCustomer;
    }

    // 判断是否为空
    private String isNull(String string) {
        return null == string ? "" : string;
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
}
