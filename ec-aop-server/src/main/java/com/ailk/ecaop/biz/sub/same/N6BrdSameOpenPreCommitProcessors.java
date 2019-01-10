package com.ailk.ecaop.biz.sub.same;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.res.ActivityInfoProcessor;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.alibaba.fastjson.JSON;

@EcRocTag("N6brdSameOpenPreCommit")
public class N6BrdSameOpenPreCommitProcessors extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");

        // 根据共线号码查询入库信息
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        Map inMap = new HashMap();
        inMap.put("SERIALNUMBER", msg.get("shareSerialNumber"));
        List resultSet = dao.queryThreePartInfo(inMap);
        String userId = "";
        String custId = "";
        String acctId = "";
        String eparchyCode = "";
        String subscribeId = "";
        if (resultSet != null && resultSet.size() > 0) {
            Map outMap = (Map) resultSet.get(0);
            String custType = (String) msg.get("custType");
            userId = (String) outMap.get("USER_ID");
            acctId = (String) outMap.get("ACCT_ID");
            eparchyCode = (String) outMap.get("EPARCHY_CODE");
            subscribeId = (String) outMap.get("SUBSCRIBE_ID");
            if ("0".equals(custType)) {
                custId = (String) outMap.get("CUST_ID");
            }
            if ("1".equals(custType)) {
                custId = (String) msg.get("custId");
            }

        }
        else {
            throw new EcAopServerBizException("9999", "该共线号码下无相关信息");
        }
        // 处理参数
        Map preParams = dealInparams(exchange, custId, acctId, userId, eparchyCode, subscribeId);
        String orderId = (String) msg.get("orderNo");
        Map base = (Map) preParams.get("base");
        Map ext = (Map) preParams.get("ext");
        msg.put("ordersId", orderId);
        msg.put("operTypeCode", "0");
        msg.put("base", base);
        msg.put("ext", ext);

        body.put("msg", msg);
        exchange.getIn().setBody(body);
        // 调用预提交接口
        exchange.setMethodCode("bsoa");
        LanUtils lan = new LanUtils();
        String province = (String) msg.get("province");
        lan.preData("ecaop.masb.bsoa.N6.sUniTradeParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.n6ess.services.AOPOrdForNorthSer" + "." + msg.get("province"));
        lan.xml2Json("ecaop.trades.bsoa.sUniTrade.template", exchange);
        // 处理返回参数
        Map message = exchange.getOut().getBody(Map.class);
        if (null != message.get("rspInfo")) {
            List<Map> rspInfo = (List<Map>) message.get("rspInfo");
            Map result = dealReturn(rspInfo.get(0), msg);
            if (null == result.get("userName")) {
                String userName = (String) ((Map) msg.get("newUserInfo")).get("certName");
                result.put("userName", userName);
            }
            exchange.getOut().setBody(result);
        }

    }

    private Map dealReturn(Map rspInfo, Map msg) {
        String serialNumber = (String) msg.get("serialNumber"); // 宽带统一编码
        String userPasswd = serialNumber.substring(serialNumber.length() - 6, serialNumber.length());
        Map ret = new HashMap();
        ret.put("userPasswd", userPasswd);
        if (null != rspInfo.get("provinceOrderInfo")) {
            List<Map> provinceOrderInfoList = (List<Map>) rspInfo.get("provinceOrderInfo");
            for (Map provinceOrderInfo : provinceOrderInfoList) {
                ret.put("provOrderId", provinceOrderInfo.get("subProvinceOrderId"));
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

    private Object isNull(String string) {
        return null == string ? "" : string;
    }

    private Map dealFee(Map fee) {
        Map feeInfo = new HashMap();
        feeInfo.put("feeId", isNull((String) fee.get("feeTypeCode")));
        feeInfo.put("feeCategory", isNull((String) fee.get("feeMode")));
        feeInfo.put("feeDes", isNull((String) fee.get("feeTypeName")));
        feeInfo.put("maxRelief", isNull((String) fee.get("maxDerateFee")));
        feeInfo.put("origFee", isNull(fee.get("fee").toString()));
        return feeInfo;
    }

    /* 处理数据 */
    private Map dealInparams(Exchange exchange, String custId, String acctId, String userId, String eparchyCode,
            String subscribeId) throws Exception {
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

        Map newUserInfo = (Map) msg.get("newUserInfo");
        String province = (String) msg.get("province");
        // 准备产品信息
        List<Map> productInfos = (List<Map>) newUserInfo.get("productInfo");
        Map productInfo = productInfos.get(0);
        String productId = (String) productInfo.get("productId");
        String productMode = (String) productInfo.get("productMode");
        if ("0".equals(productMode)) {
            productInfo.put("productMode", "1");
        }
        if ("1".equals(productMode)) {
            productInfo.put("productMode", "0");
        }
        productInfos.add(productInfo);
        String provinceCode = "00" + province;
        // 准备用户标示调用产品处理方法
        msg.put("userId", userId);
        Map EXT = new HashMap();
        EXT = TradeManagerUtils.preProductInfo(productInfos, provinceCode, msg);

        // 处理开户选择的资费信息
        String delayType = "";
        String delayDiscntId = "";
        String delayDiscntType = "";
        List discntList = (List) msg.get("discntList");
        List<Map> boradDiscntInfos = (List<Map>) newUserInfo.get("boradDiscntInfo");
        if (null != boradDiscntInfos) {
            for (Map map : boradDiscntInfos) {
                msg.put("elementId", map.get("boradDiscntId"));
                discntList.add(TradeManagerUtils.preProductInfoByElementId(msg));
                Map boradDiscntAttr = (Map) map.get("boradDiscntAttr");
                if (boradDiscntAttr != null) {
                    delayType = (String) boradDiscntAttr.get("delayType");
                    delayDiscntId = (String) boradDiscntAttr.get("delayDiscntId");
                    delayDiscntType = (String) boradDiscntAttr.get("delayDiscntType");
                }

            }
        }
        // 处理IPTV信息和互联网信息
        List<Map> iptvInfos = (List<Map>) newUserInfo.get("iptvInfo");
        List<Map> interTvInfos = (List<Map>) newUserInfo.get("interTvInfo");
        if (iptvInfos != null) {
            for (Map map : iptvInfos) {
                List<Map> iptvDiscntInfos = (List) map.get("IptvDiscntInfo");
                for (Map iptvDiscntInfo : iptvDiscntInfos) {
                    msg.put("elementId", iptvDiscntInfo.get("IptvDiscntId"));
                    discntList.add(TradeManagerUtils.preProductInfoByElementId(msg));
                }
            }
        }
        if (interTvInfos != null) {
            for (Map map : interTvInfos) {
                List<Map> interTvDiscntInfos = (List) map.get("interTvDiscntInfo");
                for (Map interTvDiscntInfo : interTvDiscntInfos) {
                    msg.put("elementId", interTvDiscntInfo.get("interTvDiscntId"));
                    discntList.add(TradeManagerUtils.preProductInfoByElementId(msg));
                }
            }
        }
        // 获取产品信息
        Map tradeProduct = (Map) EXT.get("tradeProduct");
        List<Map> item = (List) tradeProduct.get("item");
        String brandCode = null;
        for (Map m : item) {
            if (m.get("productId") != null) {
                productId = (String) m.get("productId");
            }
            if (m.get("brandCode") != null) {
                brandCode = (String) m.get("brandCode");
            }
        }
        Map tradeProductType = (Map) EXT.get("tradeProductType");
        List<Map> itemT = (List) tradeProductType.get("item");
        String productTypeCode = null;
        for (Map m : itemT) {
            if (m.get("productTypeCode") != null) {
                productTypeCode = (String) m.get("productTypeCode");

            }
        }

        // 处理活动信息
        ActivityInfoProcessor activiInfo = new ActivityInfoProcessor();
        EXT = activiInfo.process(exchange);
        exchange.getIn().setBody(body);
        // 取出itemId
        Map tradeDiscnt = (Map) EXT.get("tradeDiscnt");
        List<Map> discnt = (List<Map>) tradeDiscnt.get("item");
        String itemId = null;
        for (Map m : discnt) {
            if (m.get("itemId") != null) {
                itemId = (String) m.get("itemId");
            }
        }

        // 转换接入方式
        // Map inMap = new HashMap();
        // inMap.put("ACCESS_TYPE", newUserInfo.get("accessMode"));
        // inMap.put("CB_ACCESS_TYPE", newUserInfo.get("cbssAccessMode"));
        // inMap.put("PROVINCE_CODE", msg.get("province"));
        // inMap.put("NET_TYPE_CODE", "40");
        // MixOpenUtils mixOpenUtil = new MixOpenUtils();
        // Map accesMap = mixOpenUtil.checkAccessCode(inMap);
        // String accessMode = (String) accesMap.get("accessMode");
        // String cbAccessType = (String) accesMap.get("cbssAccessMode");

        // 处理默认信息
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
        // 处理终端信息
        List machineInfos = (List) msg.get("machineInfo");
        Map machineInfo = null;
        String machineType = null;
        String machineProvide = null;
        if (machineInfos != null && machineInfos.size() != 0) {
            machineInfo = (Map) machineInfos.get(0);
            machineType = (String) machineInfo.get("machineType");
            machineProvide = (String) machineInfo.get("machineProvide");
        }

        // String machineBrandCode = (String) machineInfo.get("machineBrandCode");
        // String machineModelCode = (String) machineInfo.get("machineModelCode");
        // String machineTypeCode = (String) machineInfo.get("machineTypeCode");

        // String machineMac = (String) machineInfo.get("machineMac");
        if (machineType == null) {
            machineType = "1";
        }

        if (machineProvide == null) {
            machineProvide = "1";
        }

        // 准备基本参数
        String tradeId = GetSeqUtil.getSeqFromN6ess(exchange, "TRADE_ID", eparchyCode);
        exchange.getIn().setBody(body);
        String startDate = GetDateUtils.getDate();
        String endDate = "20501231235959";
        String acceptDate = GetDateUtils.getDate();
        String serialNumber = (String) msg.get("serialNumber");
        String custName = (String) newUserInfo.get("certName");
        String termIp = "127.0.0.1";
        String execTime = GetDateUtils.getDate();
        String productSpec = null;
        String xDatatype = "NULL";
        String userPasswd = serialNumber.substring(serialNumber.length() - 6, serialNumber.length());
        String cityCode = (String) msg.get("district");

        // 拼装节点
        // base
        Map base = new HashMap();
        base.put("subscribeId", subscribeId);
        base.put("tradeId", tradeId);
        base.put("userId", userId);
        base.put("custId", custId);
        base.put("usecustId", custId);
        base.put("acctId", acctId);
        base.put("startDate", startDate);
        base.put("endDate", endDate);
        base.put("acceptDate", acceptDate);
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        base.put("inModeCode", "E");
        base.put("tradeTypeCode", "0010");
        base.put("productId", productId);
        base.put("brandCode", brandCode);
        base.put("userDiffCode", "00");// 用户类型用户类型00:普通用户01:亲友电话虚用户02:对讲机虚用户03:两地同虚用户04:两地通虚用户05:组合产品用户
        base.put("netTypeCode", "0040");
        base.put("serinalNamber", serialNumber);
        base.put("custName", custName);
        base.put("termIp", termIp);
        base.put("eparchyCode", eparchyCode);
        base.put("cityCode", cityCode);// ?客户归属业务区
        base.put("execTime", execTime);
        base.put("operFee", "0");
        base.put("foregift", "0");
        base.put("advancePay", "0");
        base.put("cancelTag", "0");
        base.put("checktypeCode", "0");
        base.put("chkTag", "0");
        base.put("productSpec", productSpec);

        // tradeAcct
        Map TRADE_ACCT = new HashMap();
        Map item0 = new HashMap();
        item0.put("xDatatype", xDatatype);
        item0.put("acctId", acctId);
        item0.put("payName", custName);
        item0.put("payModeCode", "0");
        item0.put("scoreValue", "0");
        item0.put("creditClassId", "0");
        item0.put("basicCreditValue", "0");
        item0.put("creditValue", "0");
        item0.put("creditControlId", "0");
        item0.put("debutyUserId", "");
        item0.put("debutyCode", "");
        item0.put("removeTag", "0");
        item0.put("openDate", GetDateUtils.getDate());
        TRADE_ACCT.put("item", item0);
        EXT.put("tradeAcct", TRADE_ACCT);

        // tradeUser
        String operatorId = (String) msg.get("operatorId");
        String channelId = (String) msg.get("channelId");
        String developStaffId = (String) msg.get("recomPersonId");
        String developDate = GetDateUtils.getDate();
        String developDepartId = (String) msg.get("recomPersonChannelId");

        Map TRADE_USER = new HashMap();
        Map item1 = new HashMap();
        item1.put("xDatatype", xDatatype);
        item1.put("usecustId", custId);
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
        item1.put("openDepartId", channelId);
        item1.put("openStaffId", operatorId);
        item1.put("inDepartId", channelId);
        item1.put("inStaffId", operatorId);
        item1.put("removeTag", "0");
        item1.put("userStateCodeset", "0");
        item1.put("mputeMonthFee", "0");
        item1.put("developStaffId", developStaffId);
        item1.put("developDate", developDate);
        item1.put("developEparchyCode", eparchyCode);
        item1.put("developCityCode", cityCode);
        item1.put("developDepartId", developDepartId);
        item1.put("inNetMode", "0");
        item1.put("productTypeCode", productTypeCode);
        TRADE_USER.put("item", item1);
        EXT.put("tradeUser", TRADE_USER);

        // tradeRes
        Map TRADE_RES = new HashMap();

        Map item2 = new HashMap();
        String resCode = (String) msg.get("serialNumber");
        item2.put("xDataType", xDatatype);
        item2.put("reTypeCode", "0");
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
        item3.put("payrelationId", GetSeqUtil.getSeqFromN6ess(exchange, "TRADE_ID", eparchyCode));
        exchange.getIn().setBody(body);
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
        items.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", msg.get("channelType")));
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
        items.add(LanUtils.createTradeItem("PRE_END_TIME", startDate));
        items.add(LanUtils.createTradeItem("NEW_PASSWD", "027245"));
        items.add(LanUtils.createTradeItem("SFGX_2060", "Y"));
        items.add(LanUtils.createTradeItem("SCHE_ID", null));
        TRADE_ITEM.put("item", items);
        EXT.put("tradeItem", TRADE_ITEM);

        // tradeSubItem
        Map TRADE_SUB_ITEM = new HashMap();
        List<Map> subitems = new ArrayList<Map>();
        // 下发IPTV信息
        if (iptvInfos != null && iptvInfos.size() <= 5 && iptvInfos.size() > 0) {
            for (Map iptvInfo : iptvInfos) {
                List<Map> IptvServiceAttrs = (List) iptvInfo.get("IptvServiceAttr");
                for (Map IptvServiceAttr : IptvServiceAttrs) {
                    subitems.add(lan.createTradeSubItemD(itemId, (String) IptvServiceAttr.get("code"),
                            IptvServiceAttr.get("value"), startDate, endDate));
                }
            }
        }
        // 下发互联网电视信息
        if (interTvInfos != null && interTvInfos.size() <= 5 && interTvInfos.size() > 0) {
            for (Map interTvInfo : interTvInfos) {
                List<Map> interTvServiceAttrs = (List) interTvInfo.get("interTvServiceAttr");
                for (Map interTvServiceAttr : interTvServiceAttrs) {
                    subitems.add(lan.createTradeSubItemD(itemId, (String) interTvServiceAttr.get("code"),
                            interTvServiceAttr.get("value"), startDate, endDate));
                }
            }
        }
        subitems.add(lan.createTradeSubItemC(itemId, "adEnd", "", startDate, endDate));
        if ("1".equals(delayType)) {
            subitems.add(lan.createTradeSubItemC(itemId, "expireDealMode", "a", startDate, endDate));
            subitems.add(lan.createTradeSubItemC(itemId, "aDiscntCode", delayDiscntId, startDate, endDate));
            subitems.add(lan.createTradeSubItemC(itemId, "bDiscntCode", delayDiscntId, startDate, endDate));
        }
        if ("2".equals(delayType)) {
            subitems.add(lan.createTradeSubItemC(itemId, "expireDealMode", "b", startDate, endDate));
            subitems.add(lan.createTradeSubItemC(itemId, "aDiscntCode", delayDiscntId, startDate, endDate));
            subitems.add(lan.createTradeSubItemC(itemId, "bDiscntCode", "", startDate, endDate));
        }
        if ("3".equals(delayType)) {
            subitems.add(lan.createTradeSubItemC(itemId, "expireDealMode", "t", startDate, endDate));
            subitems.add(lan.createTradeSubItemC(itemId, "aDiscntCode", delayDiscntId, startDate, endDate));
            subitems.add(lan.createTradeSubItemC(itemId, "bDiscntCode", "", startDate, endDate));
        }
        subitems.add(lan.createTradeSubItem("CUST_MANAGER_ID", null, itemId));
        subitems.add(lan.createTradeSubItem("SUPPORTBUSINESSTYPEKEY", "1", itemId));
        subitems.add(lan.createTradeSubItem("AREA_CODE", msg.get("district"), itemId));
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
        subitems.add(lan.createTradeSubItem("LOCAL_NET_CODE", ChangeCodeUtils.changeEparchy(msg), itemId));
        subitems.add(lan.createTradeSubItem("INSTALL_ADDRESS", newUserInfo.get("installAddress"), itemId));
        subitems.add(lan.createTradeSubItem("ISLIGHTCHANGE", "0", itemId));
        subitems.add(lan.createTradeSubItem("XDSL_PTYPE", null, itemId));
        subitems.add(lan.createTradeSubItem("CUSTOMER_GROUP", null, itemId));
        subitems.add(lan.createTradeSubItem("COMMUNIT_NAME", null, itemId));
        subitems.add(lan.createTradeSubItem("INIT_PASSWD", "0", itemId));
        subitems.add(lan.createTradeSubItem("LINK_NAME", msg.get("contactPerson"), itemId));
        subitems.add(lan.createTradeSubItem("IPNUMBER", "1", itemId));
        subitems.add(lan.createTradeSubItem("PREPAY_TAG", "0", itemId));
        subitems.add(lan.createTradeSubItem("ADDRESS_ID", newUserInfo.get("addressCode"), itemId));
        subitems.add(lan.createTradeSubItem("PORT_SPEED", "110005", itemId));
        subitems.add(lan.createTradeSubItem("iswait", "N", itemId));
        subitems.add(lan.createTradeSubItem("RESOURCEPIECEID", null, itemId));
        subitems.add(lan.createTradeSubItem("SHARE_NBR", msg.get("shareSerialNumber"), itemId));
        subitems.add(lan.createTradeSubItem("ADSL_EQUIPMENTMODELKEY", null, itemId));
        subitems.add(lan.createTradeSubItem("SERVICEUSERTYPEKEY", "610001", itemId));
        subitems.add(lan.createTradeSubItem("ADSL_IPTYPEKEY", "1", itemId));
        subitems.add(lan.createTradeSubItem("BOOK_FLAG", "0", itemId));
        subitems.add(lan.createTradeSubItem("USER_PASSWD", "027245", itemId));
        subitems.add(lan.createTradeSubItem("ISRENTSERVICE", "0", itemId));
        subitems.add(lan.createTradeSubItem("CLIENTNUM", "1", itemId));
        subitems.add(lan.createTradeSubItem("XDSLTYPEKEY", "1", itemId));
        subitems.add(lan.createTradeSubItem("MOFFICE_ID", newUserInfo.get("exchCode") + "", itemId));
        subitems.add(lan.createTradeSubItem("EQUIPMENTSTATEKEY", "1", itemId));
        subitems.add(lan.createTradeSubItem("COMMUNIT_ID", "0", itemId));
        subitems.add(lan.createTradeSubItem("EQUIPMENTYPEKEY", "110001", itemId));
        subitems.add(lan.createTradeSubItem("USER_CALLING_AREA", "0411", itemId));
        subitems.add(lan.createTradeSubItem("ADSL_ISFRONTINPUTSTL", "1", itemId));
        subitems.add(lan.createTradeSubItem("PROMISE_AREA_FLAG", "0", itemId));
        subitems.add(lan.createTradeSubItem("ADSLTYPEKEY", "1", itemId));
        subitems.add(lan.createTradeSubItem("IPADDRESS", null, itemId));
        subitems.add(lan.createTradeSubItem("ACCT_NBR", acctId, itemId));
        subitems.add(lan.createTradeSubItem("DETAIL_INSTALL_ADDRESS", newUserInfo.get("installAddress"), itemId));
        subitems.add(lan.createTradeSubItem("BUSI_CODE", "910571", itemId));
        subitems.add(lan.createTradeSubItem("SHOW_ORDER", null, itemId));
        subitems.add(lan.createTradeSubItem("CONNECTNETMODEKEY", "2", itemId));
        subitems.add(lan.createTradeSubItem("LINK_PHONE", newUserInfo.get("contactPhone"), itemId));
        subitems.add(lan.createTradeSubItem("SPECIALLINEMODEKEY", "2", itemId));
        subitems.add(lan.createTradeSubItem("CONTACTADDRESS", null, itemId));
        subitems.add(lan.createTradeSubItem("SEPARATORCOUNT", "1", itemId));
        subitems.add(lan.createTradeSubItem("ADSL_EQUIPMENTSOURCEKEY", "1", itemId));
        subitems.add(lan.createTradeSubItem("BILLINGMODE", "999999", itemId));
        subitems.add(lan.createTradeSubItem("SERIAL_NUMBER_ID", "0", itemId));
        subitems.add(lan.createTradeSubItem("ACCT_PASSWD", "223344", itemId));
        subitems.add(lan.createTradeSubItemFive("BUSI_CODE", "910571", itemId));
        subitems.add(lan.createTradeSubItem("PARENT_MOFFICE", "1174240", itemId));
        subitems.add(lan.createTradeSubItem("PROD_PROP", "I9", itemId));
        TRADE_SUB_ITEM.put("item", subitems);
        EXT.put("tradeSubItem", TRADE_SUB_ITEM);

        Map TRADE = new HashMap();
        TRADE.put("base", base);
        TRADE.put("ext", EXT);

        return TRADE;
    }
}
