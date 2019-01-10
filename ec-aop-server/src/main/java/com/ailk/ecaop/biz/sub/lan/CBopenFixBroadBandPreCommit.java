package com.ailk.ecaop.biz.sub.lan;

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
import com.ailk.ecaop.biz.product.DealNewCbssProduct;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.alibaba.fastjson.JSON;
import com.taobao.gecko.core.util.StringUtils;

@EcRocTag("CBopenFixBroadBandPreCommit")
public class CBopenFixBroadBandPreCommit extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.masb.sfso.sglUniTradeParametersMapping",
            "ecaop.masb.chph.gifa.ParametersMapping" };

    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[2];

    @Override
    public void process(Exchange exchange) throws Exception {

        // 调用客户信息校验之前，请求参数的保留备份

        Map body = exchange.getIn().getBody(Map.class);
        Object msgObject = body.get("msg");
        Map msg = null;
        if (msgObject instanceof String) {
            msg = JSON.parseObject(msgObject.toString());
        }
        else {
            msg = (Map) msgObject;
        }
        LanUtils lan = new LanUtils();

        // 调用预提交接口
        String operTypeCode = "0";
        msg.putAll(dealInparams(exchange));
        msg.put("operTypeCode", operTypeCode);
        String userName = (String) msg.get("userName");
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        // 宽带
        lan.preData(pmp[0], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.masb.sfso.sglUniTradeTemplate", exchange);
        body = (Map) exchange.getOut().getBody();
        Map returnMap = dealResult(exchange, userName);
        exchange.getOut().setBody(returnMap);
    }

    private Map dealResult(Exchange exchange, String userName) {
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
        // retMap.put("userPasswd", userPasswd);
        retMap.put("feeInfo", feeInfos);
        retMap.put("totalFee", totalFee);

        return retMap;
    }

    // 处理参数
    private Map dealInparams(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map newUserInfo = (Map) msg.get("newUserInfo");
        String custType = (String) msg.get("custType");
        String operatorId = (String) msg.get("operatorId");
        String channelId = (String) msg.get("channelId");
        // 处理号码信息
        String serialNumber = (String) msg.get("serialNumber");
        if (serialNumber.length() <= 8) {
            serialNumber = msg.get("areaCode") + serialNumber;
        }

        String itemId = (String) GetSeqUtil.getSeqFromCb(pmp[1], ExchangeUtils.ofCopy(exchange, msg), "seq_item_id", 1)
                .get(0);
        String userId = (String) GetSeqUtil.getSeqFromCb(pmp[1], ExchangeUtils.ofCopy(exchange, msg), "seq_user_id", 1)
                .get(0);
        String custId = "";
        if ("0".equals(custType)) {
            custId = (String) GetSeqUtil.getSeqFromCb(pmp[1], ExchangeUtils.ofCopy(exchange, msg), "seq_cust_id", 1)
                    .get(0);
        }
        else {
            Object custIdIn = msg.get("custId");
            if (null == custIdIn || "".equals(custIdIn)) {
                throw new EcAopServerBizException("9999", "老客户场景未下发custId");
            }
            custId = custIdIn.toString();
        }
        String tradeId = (String) GetSeqUtil.getSeqFromCb(pmp[1], ExchangeUtils.ofCopy(exchange, msg), "seq_trade_id",
                1).get(0);
        String acctId = "";
        msg.put("userId", userId);
        if (newUserInfo != null && newUserInfo.get("createOrExtendsAcct") == "1") {
            acctId = (String) newUserInfo.get("debutySerialNumber");
        }
        else {
            exchange.getIn().setBody(body);
            acctId = (String) GetSeqUtil.getSeqFromCb(pmp[1], exchange, "seq_acct_id", 1).get(0);
        }

        List<Map> productInfo = (List) newUserInfo.get("productInfo");
        List<Map> activityInfos = (List) newUserInfo.get("activityInfo");
        msg.put("activityInfo", activityInfos);
        String provinceCode = "00" + msg.get("province");
        for (int i = 0; i < productInfo.size(); i++) {
            if ("0".equals(productInfo.get(i).get("productMode"))) {
                productInfo.get(i).put("productMode", "1");
            }
            else {
                productInfo.get(i).put("productMode", "0");
            }
        }

        // 地市转化
        msg.put("eparchyCode", ChangeCodeUtils.changeEparchy(msg));
        // 调用产品方法
        Map ext = TradeManagerUtils.newPreProductInfo(productInfo, provinceCode,
                msg);
        List discntList = (List) msg.get("discntList");
        List<Map> boradDiscntInfos = (List<Map>) newUserInfo.get("discntInfo");
        if (null != boradDiscntInfos) {
            for (Map map : boradDiscntInfos) {
                for (Map productIn : productInfo) {
                    msg.put("productId", productIn.get("productId"));
                    msg.put("elementId", map.get("discntId"));
                    discntList.add(TradeManagerUtils.preProductInfoByElementIdProductId(msg));
                }

            }
        }

        /*
         * List svclist = (List) msg.get("svcList");
         * if (null != boradDiscntInfos) {
         * for (Map map : boradDiscntInfos) {
         * msg.put("productId", productInfo.get(0).get("productId"));
         * msg.put("elementId", map.get("discntId"));
         * Svclist.add(TradeManagerUtils.queryProductInfoSvcByElementIdProductId(msg));
         * }
         * }
         */
        // 处理可选包下面的资费和服务
        queryPackageAllElement(msg);

        Map tradeProduct = (Map) ext.get("tradeProduct");
        List<Map> item = (List) tradeProduct.get("item");
        String productId = "";
        String brandCode = "";
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

        String startDate = GetDateUtils.getDate();
        String endDate = "20501231000000";

        // 在北六库里查cbaccessType
        // LanOpenApp4GDao dao = new LanOpenApp4GDao();
        Map inMap = new HashMap();
        inMap.put("ACCESS_TYPE", newUserInfo.get("accessMode"));
        inMap.put("CB_ACCESS_TYPE", newUserInfo.get("cbssAccessMode"));
        inMap.put("EPARCHY_CODE", ChangeCodeUtils.changeEparchy(msg));
        inMap.put("PROVINCE_CODE", msg.get("province"));
        inMap.put("NET_TYPE_CODE", "30");
        Map accessType = TradeManagerUtils.checkAccessCode(inMap);
        String userType = (String) newUserInfo.get("userType");
        if (userType == null) {
            userType = "1";
        }
        String acceptMode = (String) newUserInfo.get("acceptMode");
        if (acceptMode == null) {
            acceptMode = "1";
        }
        String userProperty = (String) newUserInfo.get("userProperty");
        if (userProperty == null) {
            userProperty = "1";
        }
        String cityMark = (String) newUserInfo.get("cityMark");
        if (cityMark == null) {
            cityMark = "1";
        }
        String payMode = (String) newUserInfo.get("payMode");
        if (payMode == null) {
            payMode = "1";
        }
        String netMode = (String) newUserInfo.get("netMode");
        if (netMode == null) {
            netMode = "1";
        }

        String machineBrandCode = "";
        String machineModelCode = "";
        String machineTypeCode = "";
        String machineType = "";
        String machineProvide = "";
        String machineMac = "";
        List machineInfos = (List) msg.get("machineInfo");
        if (null != machineInfos) {
            Map machineInfo = (Map) machineInfos.get(0);
            machineBrandCode = (String) machineInfo.get("machineBrandCode");
            machineModelCode = (String) machineInfo.get("machineModelCode");
            machineTypeCode = (String) machineInfo.get("machineTypeCode");
            machineType = (String) machineInfo.get("machineType");
            if (machineType == null) {
                machineType = "1";
            }
            machineProvide = (String) machineInfo.get("machineProvide");
            if (machineProvide == null) {
                machineProvide = "1";
            }
            machineMac = (String) machineInfo.get("machineMac");
        }

        // 整理参数
        Map base = new HashMap();
        base.put("subscribeId", tradeId);
        base.put("tradeId", tradeId);
        base.put("startDate", startDate);
        base.put("endDate", endDate);
        base.put("acceptDate", GetDateUtils.getDate());
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        base.put("inModeCode", new ChangeCodeUtils().getInModeCode(exchange.getAppCode()));
        base.put("tradeTypeCode", "0010");
        base.put("productId", productId);
        base.put("brandCode", brandCode);
        base.put("userId", userId);
        base.put("custId", custId);
        base.put("usecustId", custId);
        base.put("acctId", acctId);
        base.put("userDiffCode", "00");
        base.put("netTypeCode", "0030");
        base.put("serinalNamber", serialNumber);
        base.put("custName", newUserInfo.get("certName"));
        base.put("termIp", "127.0.0.1");
        base.put("eparchyCode", ChangeCodeUtils.changeEparchy(msg));
        base.put("cityCode", msg.get("district"));
        base.put("execTime", GetDateUtils.getDate());
        base.put("operFee", "0");
        base.put("foregift", "0");
        base.put("advancePay", "0");
        base.put("feeState", "");
        base.put("feeStaffId", "");
        base.put("cancelTag", "0");
        base.put("checktypeCode", "0");
        base.put("chkTag", "0");
        base.put("actorName", "");
        base.put("actorCertTypeId", "");
        base.put("actorPhone", "");
        base.put("actorCertNum", "");
        base.put("contact", "");
        base.put("contactPhone", "");
        base.put("contactAddress", "");
        base.put("remark", "");
        base.put("tradeStaffId", operatorId);
        base.put("tradeDepartId", channelId);
        base.put("mainDiscntCode", "");
        base.put("productSpec", "");

        // tredeAcct 不明白为什么预提交账户台账节点是注释掉的，导致开户后没账户信息，放开注释 modify by wangrj3
        Map tradeAcct = new HashMap();
        Map acct = new HashMap();
        acct.put("xDatatype", "NULL");
        acct.put("acctId", acctId);
        acct.put("payName", newUserInfo.get("certName"));
        acct.put("payModeCode", "1");// 付费方式：现金、托收、代扣，写死塞“1-现金”
        acct.put("scoreValue", "0");
        acct.put("creditClassId", "0");
        acct.put("basicCreditValue", "0");
        acct.put("creditValue", "0");
        acct.put("creditControlId", "0");
        acct.put("removeTag", "0");
        acct.put("eparchyCode", ChangeCodeUtils.changeEparchy(msg));
        acct.put("cityCode", msg.get("district"));
        acct.put("openDate", GetDateUtils.getDate());
        acct.put("netTypeCode", "0030");
        acct.put("custId", custId);
        tradeAcct.put("item", acct);

        // tradeUser
        Map tradeUser = new HashMap();
        Map user = new HashMap();
        user.put("xDatatype", "NULL");
        user.put("usecustId", custId);
        user.put("userPasswd", "123465");// 规范没有
        user.put("userTypeCode", "0");
        user.put("scoreValue", "0");
        user.put("creditClass", "0");
        user.put("basicCreditValue", "0");
        user.put("creditValue", "0");
        user.put("acctTag", "0");
        user.put("prepayTag", "0");
        user.put("inDate", GetDateUtils.getDate());
        user.put("openDate", GetDateUtils.getDate());
        user.put("openMode", "0");
        user.put("openDepartId", msg.get("channelId"));
        user.put("openStaffId", msg.get("operatorId"));
        user.put("inDepartId", msg.get("channelId"));
        user.put("inStaffId", msg.get("operatorId"));
        user.put("removeTag", "0");
        user.put("userStateCodeset", "0");
        user.put("mputeMonthFee", "0");
        user.put("developStaffId", msg.get("recomPersonId"));
        user.put("developDate", GetDateUtils.getDate());
        user.put("developEparchyCode", msg.get("recomPersonCityCode"));
        user.put("developCityCode", msg.get("recomPersonDistrict"));
        user.put("developDepartId", msg.get("recomPersonChannelId"));
        user.put("inNetMode", "0");
        user.put("productTypeCode", productTypeCode);
        tradeUser.put("item", user);
        // tradeRes
        List<Map> tradeRes = new ArrayList<Map>();
        Map item3 = new HashMap();
        item3.put("xDatatype", "NULL");
        item3.put("reTypeCode", "0");
        item3.put("resCode", serialNumber);
        item3.put("modifyTag", "0");
        item3.put("startDate", startDate);
        item3.put("endDate", endDate);

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

        /*
         * Map item5 = new HashMap();
         * item5.put("xDatatype", "NULL");
         * item5.put("reTypeCode", "1");
         * item5.put("resCode", "" + msg.get("areaCode") + msg.get("serialNumber"));
         * item5.put("resInfo1", "" + msg.get("areaCode") + msg.get("serialNumber"));
         * item5.put("modifyTag", "0");
         */

        Map tradeRe1 = new HashMap();
        // Map tradeRe2 = new HashMap();
        tradeRe1.put("item", item3);
        // tradeRe2.put("item", item5);
        tradeRes.add(tradeRe1);
        // tradeRes.add(tradeRe2);

        // tradePayrelation
        Map tradePayrelation = new HashMap();
        Map item4 = new HashMap();
        item4.put("payitemCode", "-1");
        item4.put("acctPriority", "0");
        item4.put("userPriority", "0");
        item4.put("bindType", "1");
        item4.put("defaultTag", "1");
        item4.put("limitType", "0");
        item4.put("limit", "0");
        item4.put("complementTag", "0");
        item4.put("addupMonths", "0");
        item4.put("addupMethod", "0");
        item4.put("payrelationId",
                GetSeqUtil.getSeqFromCb(pmp[1], ExchangeUtils.ofCopy(exchange, msg), "seq_payrela_id", 1).get(0));
        item4.put("actTag", "1");
        tradePayrelation.put("item", item4);

        // tradeItem

        List<Map> tradeItem = new ArrayList<Map>();
        LanUtils lan = new LanUtils();

        // 固话单装不传这些属性：REL_COMP_PROD_ID、COMP_DEAL_STATE、ROLE_CODE_B、SFGX_2060、GXLX_TANGSZ、ALONE_TCS_COMP_INDEX，将代码注释掉
        // modify by wangrj3
        // tradeItem.add(lan.createAttrInfoNoTime("REL_COMP_PROD_ID", "89246288")); // 写死的
        // tradeItem.add(lan.createAttrInfoNoTime("COMP_DEAL_STATE", "0"));
        // tradeItem.add(lan.createAttrInfoNoTime("ROLE_CODE_B", "3"));
        if (new ChangeCodeUtils().isWOPre(exchange.getAppCode())) {
            tradeItem.add(lan.createAttrInfoNoTime("WORK_TRADE_ID", msg.get("orderNo")));
        }
        tradeItem.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", "1"));
        tradeItem.add(LanUtils.createTradeItem("PH_NUM", serialNumber));
        tradeItem.add(LanUtils.createTradeItem("EXTRA_INFO", ""));
        tradeItem.add(LanUtils.createTradeItem("OPER_CODE", "1"));
        tradeItem.add(LanUtils.createTradeItem("USER_PASSWD", ("" + serialNumber).substring(6)));// 写死的
        tradeItem.add(LanUtils.createTradeItem("REOPEN_TAG", "2"));
        tradeItem.add(LanUtils.createTradeItem("NEW_PASSWD", ("" + serialNumber).substring(6)));
        tradeItem.add(LanUtils.createTradeItem("EXISTS_ACCT", "1"));
        // tradeItem.add(LanUtils.createTradeItem("SFGX_2060", "Y"));
        // tradeItem.add(LanUtils.createTradeItem("GXLX_TANGSZ", "GZKD:1:40"));// 写死的
        // tradeItem.add(lan.createAttrInfoNoTime("ALONE_TCS_COMP_INDEX", "1"));
        // 增加用于工单送IOM施工的属性：BOOK_FLAG、PRE_START_TIME add by wangrj3
        // tradeItem.add(LanUtils.createTradeItem("BOOK_FLAG", "0"));// 预约施工标志，0-不预约施工，1-预约施工
        // tradeItem.add(LanUtils.createTradeItem("PRE_START_TIME", GetDateUtils.getDate()));// 预约史工时间，这边非预约塞当前时间

        if (null != newUserInfo.get("hopeDate")) {
            tradeItem.add(LanUtils.createTradeItem("BOOK_FLAG", "1"));// 1预约 0非预约
            tradeItem.add(LanUtils.createTradeItem("PRE_START_TIME",
                    GetDateUtils.transDate((String) newUserInfo.get("hopeDate"), 19)));
        }
        else {
            tradeItem.add(LanUtils.createTradeItem("BOOK_FLAG", "0"));// 1预约 0非预约
            tradeItem.add(LanUtils.createTradeItem("PRE_START_TIME", ""));
        }
        // tradeSubItem
        List<Map> tradeSubItem = new ArrayList<Map>();
        tradeSubItem.add(lan.createTradeSubItem("ADDRESS_ID", newUserInfo.get("addressCode"), itemId));// 标准地址编码
        tradeSubItem.add(lan.createTradeSubItem("SHARE_NBR", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("USETYPE", newUserInfo.get("userType"), itemId));
        tradeSubItem.add(lan.createTradeSubItem("MODULE_EXCH_ID", newUserInfo.get("moduleExchId"), itemId));
        tradeSubItem.add(lan.createTradeSubItem("ACCESS_TYPE", accessType.get("accessMode"), itemId));
        tradeSubItem.add(lan.createTradeSubItem("serialNumber", serialNumber,
                itemId));
        tradeSubItem.add(lan.createTradeSubItem("AREA_CODE", msg.get("district"), itemId));
        tradeSubItem.add(lan.createTradeSubItem("POINT_EXCH_ID", newUserInfo.get("pointExchId"), itemId));
        tradeSubItem.add(lan.createTradeSubItem("USER_PASSWD", ("" + serialNumber).substring(1), itemId));
        tradeSubItem.add(lan.createTradeSubItem("TERMINAL_SN", machineTypeCode, itemId));// 品牌编码
        tradeSubItem.add(lan.createTradeSubItem("ISFLAG114", "0", itemId));
        tradeSubItem.add(lan.createTradeSubItem("TERMINAL_MAC", machineMac, itemId));
        tradeSubItem.add(lan.createTradeSubItem("SWITCH_EXCH_ID", newUserInfo.get("exchCode"), itemId));
        tradeSubItem.add(lan.createTradeSubItem("TERMINALSRC_MODE", machineProvide, itemId));
        tradeSubItem.add(lan.createTradeSubItem("AREA_EXCH_ID", newUserInfo.get("areaExchId"), itemId));
        tradeSubItem.add(lan.createTradeSubItem("MOFFICE_ID", newUserInfo.get("exchCode"), itemId));// 局向编码
        tradeSubItem.add(lan.createTradeSubItem("COMMUNIT_ID", "0", itemId));
        tradeSubItem.add(lan.createTradeSubItem("USER_TYPE_CODE", "0", itemId));
        tradeSubItem.add(lan.createTradeSubItem("DIRECFLAG", "0", itemId));
        tradeSubItem.add(lan.createTradeSubItem("CB_ACCESS_TYPE", accessType.get("cbssAccessMode"), itemId));
        tradeSubItem.add(lan.createTradeSubItem("COMMPANY_NBR", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("DETAIL_INSTALL_ADDRESS", newUserInfo.get("installAddress"), itemId));
        tradeSubItem.add(lan.createTradeSubItem("TOWN_FLAG", "C", itemId));
        tradeSubItem.add(lan.createTradeSubItem("TERMINAL_MODEL", machineModelCode, itemId));
        tradeSubItem.add(lan.createTradeSubItem("LOCAL_NET_CODE", ChangeCodeUtils.changeEparchy(msg), itemId));
        tradeSubItem.add(lan.createTradeSubItem("INSTALL_ADDRESS", newUserInfo.get("installAddress"), itemId));
        tradeSubItem.add(lan.createTradeSubItem("PROJECGT_ID", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("TERMINAL_TYPE", machineType, itemId));
        tradeSubItem.add(lan.createTradeSubItem("TERMINAL_BRAND", machineBrandCode, itemId));
        tradeSubItem.add(lan.createTradeSubItem("COMMUNIT_NAME", "", itemId));
        tradeSubItem.add(lan.createTradeSubItem("INIT_PASSWD", "0", itemId));
        tradeSubItem.add(lan.createTradeSubItem("COLLINEAR_TYPE", "X3", itemId));// 写死的
        Map trade = new HashMap();

        // 不明白为什么预提交账户台账节点是注释掉的，导致开户后没账户信息，放开注释 modify by wangrj3
        ext.put("tradeAcct", tradeAcct);

        if ("0".equals(custType)) {
            ext.put("tradeCustomer", tradeCustomer);
        }
        ext.put("tradeUser", tradeUser);
        ext.put("tradeRes", tradeRes);
        ext.put("tradePayrelation", tradePayrelation);
        ext.put("tradeDiscnt", preDiscntData(msg, itemId));
        if (!StringUtils.isBlank((newUserInfo.get("packageId")) + "")) {
            ext.put("tradeSvc", pretradeSvc(msg, itemId));
        }
        Map tradeItems = new HashMap();
        tradeItems.put("item", tradeItem);
        ext.put("tradeItem", tradeItems);
        Map tradeSubItems = new HashMap();
        tradeSubItems.put("item", tradeSubItem);
        ext.put("tradeSubItem", tradeSubItems);
        trade.put("ext", ext);
        trade.put("base", base);
        trade.put("ordersId", tradeId);

        return trade;

    }

    /**
     * 处理开户时可选包元素
     */
    private void queryPackageAllElement(Map msg) {
        List<Map> discntList = (List<Map>) msg.get("discntList");
        List<Map> svcList = (List<Map>) msg.get("svcList");
        Map newUserInfo = (Map) msg.get("newUserInfo");
        String packageId = (String) newUserInfo.get("packageId");
        List<Map> productInfo = (List) newUserInfo.get("productInfo");
        String productId = (String) productInfo.get(0).get("productId");
        if (!StringUtils.isBlank(packageId)) {
            Map inMap = new HashMap();
            inMap.put("packageId", packageId);
            DealNewCbssProduct n25Dao = new DealNewCbssProduct();
            List<Map> queryResult = n25Dao.queryPackageAllElement(inMap);
            if (null == queryResult || queryResult.size() == 0) {
                throw new EcAopServerBizException("9999", "根据开户时可选包id[" + packageId + "]未获取到包元素信息!");
            }
            for (Map map : queryResult) {
                if ("D".equals(map.get("ELEMENT_TYPE_CODE"))) {
                    map.put("productId", productId);
                    map.put("packageId", map.get("PACKAGEID"));
                    map.put("discntCode", map.get("DISCNTCODE"));
                    discntList.add(map);
                }
                else if ("S".equals(map.get("ELEMENT_TYPE_CODE"))) {
                    map.put("productId", productId);
                    map.put("packageId", map.get("PACKAGEID"));
                    map.put("serviceId", map.get("DISCNTCODE"));
                    svcList.add(map);
                }
            }
            System.out.println("20161215msg" + msg);
        }
    }

    /**
     * 拼装tradeSvc节点
     */
    public Map pretradeSvc(Map msg, String itemId) {
        try {
            Map tradeDis = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            List<Map> discnt = (List<Map>) msg.get("svcList");
            System.out.println("20161215msg1" + msg);
            System.out.println("20161215discnt" + discnt);
            for (int i = 0; i < discnt.size(); i++) {
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("userId", msg.get("userId"));
                item.put("idType", "1");
                item.put("userIdA", "-1");
                item.put("productId", discnt.get(i).get("productId"));
                item.put("packageId", discnt.get(i).get("packageId"));
                item.put("serviceId", discnt.get(i).get("serviceId"));
                item.put("startDate", GetDateUtils.getDate());
                item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                item.put("modifyTag", "0");
                item.put("itemId", itemId);
                itemList.add(item);
            }
            tradeDis.put("item", itemList);
            return tradeDis;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_SVC节点报错" + e.getMessage());
        }
    }

    /**
     * 拼装tradediscnt节点
     */
    public Map preDiscntData(Map msg, String itemId) {
        try {
            Map tradeDis = new HashMap();
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
            tradeDis.put("item", itemList);
            return tradeDis;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_DISCNT节点报错" + e.getMessage());
        }
    }

    @Override
    public void applyParams(String[] params) {
        // TODO Auto-generated method stub
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }

    }
}
