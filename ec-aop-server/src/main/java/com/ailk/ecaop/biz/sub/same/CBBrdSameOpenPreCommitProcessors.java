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
import com.ailk.ecaop.biz.sub.gdjk.OpenApply4GProcessor;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MixOpenUtils;
import com.ailk.ecaop.common.utils.ProductUtil3GE;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.alibaba.fastjson.JSON;

@EcRocTag("CBbrdSameOpenPreCommit")
public class CBBrdSameOpenPreCommitProcessors extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.masb.chph.gifa.ParametersMapping",
            "ecaop.masb.bsoa.sUniTradeParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[2];

    @Override
    public void process(Exchange exchange) throws Exception {

        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        LanOpenApp4GDao dao = new LanOpenApp4GDao();

        // 根据共线号码查询入库信息
        Map inMap = new HashMap();
        inMap.put("SERIALNUMBER", msg.get("shareSerialNumber"));
        List selResult = dao.queryThreePartInfo(inMap);
        Map outMap = new HashMap();
        if (selResult != null && selResult.size() > 0) {
            outMap = (Map) selResult.get(0);
        }
        else {
            throw new EcAopServerBizException("9999", "该共线号码下无相关信息");
        }
        // 处理参数
        Map preParams = dealInparams(exchange, outMap);
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

        lan.preData(pmp[1], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.trades.bsoa.sUniTrade.template", exchange);
        Map message = exchange.getOut().getBody(Map.class);
        // 处理返回参数
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
        // 返回流水
        // ret.put("provOrderId", isNull(rspInfo.get("bssOrderId").toString()));

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

    /**
     * 处理数据
     * @param exchange
     * @param outMap
     * @return
     * @throws Exception
     */
    private Map dealInparams(Exchange exchange, Map outMap) throws Exception {
        LanUtils lan = new LanUtils();
        Map ext = new HashMap();
        Map body = exchange.getIn().getBody(Map.class);
        Object msgObject = body.get("msg");
        Map msg = null;
        if (msgObject instanceof String) {
            msg = JSON.parseObject(msgObject.toString());
        }
        else {
            msg = (Map) msgObject;
        }
        // 获取查库结果
        String custType = (String) msg.get("custType");
        String userId = (String) outMap.get("USER_ID");
        String acctId = (String) outMap.get("ACCT_ID");
        String eparchyCode = (String) outMap.get("EPARCHY_CODE");
        String subscribeId = (String) outMap.get("SUBSCRIBE_ID");
        String custId = "";
        if ("0".equals(custType)) {
            custId = (String) outMap.get("CUST_ID");
        }
        if ("1".equals(custType)) {
            custId = (String) msg.get("custId");
        }
        Map newUserInfo = (Map) msg.get("newUserInfo");
        String provinceCode = "00" + msg.get("province");
        // 准备产品和活动信息
        List<Map> productInfos = (List<Map>) newUserInfo.get("productInfo");
        List<Map> activityInfos = (List<Map>) newUserInfo.get("activityInfo");
        String productId = "";
        if (null != productInfos && productInfos.size() > 0) {
            productId = (String) productInfos.get(0).get("productId");
        }
        msg.put("productId", productId);
        // 调用产品处理方法
        msg.put("userId", userId);
        msg.put("appkey", exchange.getAppkey()); // 放入appkey以供新产品逻辑使用 FIXME
        // EXT = TradeManagerUtils.preProductInfo(productInfos, provinceCode, msg);
        ProductUtil3GE.preProductInfo(productInfos, activityInfos, provinceCode, msg);

        // 处理开户选择的资费信息
        String delayType = "";
        String delayDiscntId = "";
        String delayDiscntType = "";
        List discntList = (List) msg.get("discntList");
        List<Map> boradDiscntInfos = (List<Map>) newUserInfo.get("boradDiscntInfo");
        if (null != boradDiscntInfos) {
            for (Map map : boradDiscntInfos) {
                msg.put("elementId", map.get("boradDiscntId"));
                // discntList.add(TradeManagerUtils.preProductInfoByElementId(msg));
                discntList.add(TradeManagerUtils.preProductInfoByElementIdProductId(msg));
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
                List<Map> iptvDiscntInfos = (List) map.get("IptvServiceAttr");
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
        /**
         * 准备所需的公共参数
         */
        // 获取产品信息(product和productType)
        Map tradeProduct = (Map) msg.get("productList");
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
        Map tradeProductType = (Map) msg.get("productTypeList");
        List<Map> itemT = (List) tradeProductType.get("item");
        String productTypeCode = null;
        for (Map m : itemT) {
            if (m.get("productTypeCode") != null) {
                productTypeCode = (String) m.get("productTypeCode");

            }
        }
        // 转换接入方式
        Map inMap = new HashMap();
        inMap.put("ACCESS_TYPE", newUserInfo.get("accessMode"));
        inMap.put("CB_ACCESS_TYPE", newUserInfo.get("cbssAccessMode"));
        inMap.put("PROVINCE_CODE", msg.get("province"));
        inMap.put("NET_TYPE_CODE", "40");
        MixOpenUtils mixOpenUtil = new MixOpenUtils();
        Map accesMap = mixOpenUtil.checkAccessCode(inMap);
        String accessMode = (String) accesMap.get("accessMode");
        String cbAccessType = (String) accesMap.get("cbssAccessMode");

        // 处理默认信息
        String userType = (String) newUserInfo.get("userType");
        if (userType == null) {
            userType = "1";
        }
        // 获取终端基本信息
        List machineInfos = (List) msg.get("machineInfo");
        String machineBrandCode = null;
        String machineModelCode = null;
        String machineTypeCode = null;
        String machineType = null;
        String machineMac = null;
        Object machineProvide = null;
        if (machineInfos != null) {
            Map machineInfo = (Map) machineInfos.get(0);
            machineBrandCode = (String) machineInfo.get("machineBrandCode");
            machineModelCode = (String) machineInfo.get("machineModelCode");
            machineTypeCode = (String) machineInfo.get("machineTypeCode");
            machineType = (String) machineInfo.get("machineType");
            machineMac = (String) machineInfo.get("machineMac");
            machineProvide = ChangeCodeUtils.changeMachineProvideToCB(machineInfo.get("machineProvide"));// zzc 20170309
        }

        if (machineType == null) {
            machineType = "1";
        }
        // 准备基本参数
        // String tradeId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_trade_id");
        Object tradeId = GetSeqUtil.getSeqFromCb(pmp[0], exchange, "seq_trade_id", 1).get(0);
        // userId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_user_id");
        userId = (String) GetSeqUtil.getSeqFromCb(pmp[0], exchange, "seq_user_id", 2).get(0);
        String usecustId = custId;
        String startDate = GetDateUtils.getDate();
        String acceptDate = GetDateUtils.getDate();
        String endDate = MagicNumber.CBSS_DEFAULT_EXPIRE_TIME;
        String serialNumber = (String) msg.get("serialNumber");
        String custName = (String) newUserInfo.get("certName");
        String termIp = "127.0.0.1"; // 受理终端IP？
        String execTime = GetDateUtils.getDate();
        String productSpec = null;// ?
        String xDatatype = "NULL";
        String userPasswd = serialNumber.substring(serialNumber.length() - 6, serialNumber.length());
        String cityCode = (String) msg.get("district");

        /**
         * 拼装base节点
         */
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
        base.put("feeState", "0");
        base.put("feeStaffId", "");
        base.put("actorName", "");
        base.put("actorCertTypeId", "");
        base.put("actorPhone", "");
        base.put("actorCertNum", "");
        base.put("contact", msg.get("contactPerson"));
        base.put("contactPhone", msg.get("contactPhone"));
        base.put("contactAddress", msg.get("contactAddress"));
        base.put("developEparchyCode", eparchyCode);
        base.put("developCityCode", cityCode);
        base.put("remark", "");
        base.put("productSpec", productSpec);

        /**
         * 拼装EXT节点
         */
        // 拼装产品相关节点信息
        OpenApply4GProcessor openApplyPro = new OpenApply4GProcessor();
        ext.put("tradeProductType", openApplyPro.preProductTpyeListData(msg));
        ext.put("tradeProduct", openApplyPro.preProductData(msg));
        ext.put("tradeDiscnt", openApplyPro.preDiscntData(msg));
        ext.put("tradeSvc", openApplyPro.preTradeSvcData(msg));
        ext.put("tradeSp", openApplyPro.preTradeSpData(msg));
        // 获取产品处理后的itemId
        Map tradeDiscnt = (Map) ext.get("tradeDiscnt");
        List<Map> discnt = (List<Map>) tradeDiscnt.get("item");
        String itemId = null;
        for (Map m : discnt) {
            if (m.get("itemId") != null) {
                itemId = (String) m.get("itemId");
            }
        }
        // 拼装tradeUser
        String operatorId = (String) msg.get("operatorId");
        String channelId = (String) msg.get("channelId");
        String developStaffId = (String) msg.get("recomPersonId");
        String developDate = GetDateUtils.getDate();
        String developDepartId = (String) msg.get("recomPersonChannelId");

        Map TRADE_USER = new HashMap();
        Map item1 = new HashMap();
        item1.put("xDatatype", xDatatype);
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
        ext.put("tradeUser", TRADE_USER);

        // tradeCustom
        Map TRADE_CUSTOMER = new HashMap();
        Map item0 = new HashMap();

        item0.put("xDatatype", "NULL");
        item0.put("sustId", custId);
        item0.put("custName", newUserInfo.get("certName"));
        item0.put("custType", "0"); // 0 个人用户 ,1 集团客户
        item0.put("custState", "0"); // 0 在网
        item0.put("psptTypeCode", CertTypeChangeUtils.certTypeMall2Cbss((String) newUserInfo.get("certType")));
        item0.put("psptId", newUserInfo.get("certNum"));
        item0.put("openLimit", "0"); // 不限制开户数
        item0.put("eparchyCode", eparchyCode);
        item0.put("cityCode", cityCode);
        item0.put("developDepartId", msg.get("recomPersonChannelId"));
        item0.put("inDate", startDate);
        item0.put("removeTag", "0");
        item0.put("rsrvTag1", "2");
        TRADE_CUSTOMER.put("item", item0);
        ext.put("tradeCustomer", TRADE_CUSTOMER);

        Map TRADE_RES = new HashMap();
        String resCode = (String) msg.get("serialNumber");
        Map item2 = new HashMap();
        item2.put("xDataType", "NULL");
        item2.put("reTypeCode", "0");
        item2.put("resCode", resCode);
        item2.put("modifyTag", "0");
        item2.put("startDate", startDate);
        item2.put("endDate", endDate);
        TRADE_RES.put("item", item2);
        ext.put("tradeRes", TRADE_RES);

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
        item3.put("payrelationId", GetSeqUtil.getSeqFromCb(pmp[0], exchange, "seq_payrela_id", 3).get(0));
        item3.put("actTag", "1");
        TRADE_PAYRELATION.put("item", item3);
        ext.put("tradePayrelation", TRADE_PAYRELATION);

        // tradeOther
        Map TRADE_OTHER = new HashMap();
        Map item4 = new HashMap();
        item4.put("xDatatype", xDatatype);
        item4.put("rsrvValueCode", "ZZFS");
        item4.put("rsrvValue", "2");
        item4.put("rsrvStr1", "131102198612051074");
        item4.put("rsrvStr2", "10");
        item4.put("rsrvStr3", "-9");
        item4.put("rsrvStr6", "1");
        item4.put("rsrvStr7", "0");
        item4.put("modifyTag", "0");
        item4.put("startDate", startDate);
        item4.put("endDate", endDate);
        TRADE_OTHER.put("item", item4);
        ext.put("tradeOther", TRADE_OTHER);
        // tradeItem
        Map TRADE_ITEM = new HashMap();
        List<Map> items = new ArrayList<Map>();
        if (new ChangeCodeUtils().isWOPre(exchange.getAppCode())) {
            items.add(lan.createAttrInfoNoTime("WORK_TRADE_ID", msg.get("orderNo")));
        }
        // 将每一组参数放入
        items.add(lan.createAttrInfoNoTime("MARKETING_MODE", null));
        items.add(lan.createAttrInfoNoTime("DEVELOP_STAFF_ID", developStaffId));
        items.add(lan.createAttrInfoNoTime("DEVELOP_DEPART_ID", developDepartId));
        items.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", "0371"));
        items.add(LanUtils.createTradeItem("EXTRA_INFO", null));
        items.add(LanUtils.createTradeItem("PH_NUM", null));
        items.add(LanUtils.createTradeItem("WORK_STAFF_ID", null));
        items.add(LanUtils.createTradeItem("WORK_TRADE_ID", null));
        items.add(LanUtils.createTradeItem("NO_BOOK_REASON", null));
        items.add(LanUtils.createTradeItem("BOOK_FLAG", "0"));
        items.add(LanUtils.createTradeItem("USER_PASSWD", "108884"));
        items.add(LanUtils.createTradeItem("EXISTS_ACCT", "1"));
        items.add(LanUtils.createTradeItem("SUB_TYPE", "0"));
        items.add(LanUtils.createTradeItem("WORK_DEPART_ID", null));
        items.add(LanUtils.createTradeItem("REOPEN_TAG", "2"));
        items.add(LanUtils.createTradeItem("NEW_PASSWD", "108884"));
        items.add(LanUtils.createTradeItem("SFGX_2060", "Y"));
        items.add(LanUtils.createTradeItem("PRE_START_TIME", startDate));
        items.add(LanUtils.createTradeItem("GXLX_TANGSZ", "GZDH:1:30"));
        TRADE_ITEM.put("item", items);
        ext.put("tradeItem", TRADE_ITEM);

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
        System.out.println("szy=itemId=" + itemId);
        subitems.add(lan.createTradeSubItemC(itemId, "effectMode", delayDiscntType, startDate, endDate));
        subitems.add(lan.createTradeSubItemC(itemId, "adStart", "0", startDate, endDate));
        subitems.add(lan.createTradeSubItemC(itemId, "cycleNum", "1", startDate, endDate));
        subitems.add(lan.createTradeSubItemC(itemId, "cycle", "12", startDate, endDate));
        subitems.add(lan.createTradeSubItemC(itemId, "fixedHire", "12", startDate, endDate));
        subitems.add(lan.createTradeSubItemC(itemId, "cycleFee", "960", startDate, endDate));
        subitems.add(lan.createTradeSubItemC(itemId, "recharge", "", startDate, endDate));
        subitems.add(lan.createTradeSubItemC(itemId, "callBack", "0", startDate, endDate));
        subitems.add(lan.createTradeSubItem("PREPAY_TAG", "0", itemId));
        subitems.add(lan.createTradeSubItem("ADDRESS_ID", "096", itemId));
        subitems.add(lan.createTradeSubItem("SHARE_NBR", msg.get("shareSerialNumber"), itemId));
        subitems.add(lan.createTradeSubItem("USETYPE", userType, itemId));
        subitems.add(lan.createTradeSubItem("MODULE_EXCH_ID", newUserInfo.get("moduleExchId"), itemId));
        subitems.add(lan.createTradeSubItem("WOPAY_MONEY", "", itemId));
        subitems.add(lan.createTradeSubItem("AREA_CODE", msg.get("district"), itemId));// ?
        subitems.add(lan.createTradeSubItem("ACCESS_TYPE", accessMode, itemId));
        subitems.add(lan.createTradeSubItem("TIME_LIMIT_ID", "2", itemId));
        subitems.add(lan.createTradeSubItem("POINT_EXCH_ID", newUserInfo.get("pointExchId"), itemId));
        subitems.add(lan.createTradeSubItem("USER_PASSWD", userPasswd, itemId));
        subitems.add(lan.createTradeSubItem("CONNECTNETMODE", "1", itemId));
        subitems.add(lan.createTradeSubItem("ISWAIL", "0", itemId));

        subitems.add(lan.createTradeSubItem("TERMINAL_SN", machineTypeCode, itemId));
        subitems.add(lan.createTradeSubItem("TERMINAL_MAC", machineMac, itemId));
        subitems.add(lan.createTradeSubItem("SWITCH_EXCH_ID", "", itemId));
        subitems.add(lan.createTradeSubItem("ISWOPAY", "0", itemId));
        subitems.add(lan.createTradeSubItem("KDLX_2061", "N", itemId));
        subitems.add(lan.createTradeSubItem("TERMINALSRC_MODE", "A002", itemId));
        subitems.add(lan.createTradeSubItem("AREA_EXCH_ID", newUserInfo.get("areaExchId"), itemId));
        subitems.add(lan.createTradeSubItem("MOFFICE_ID", newUserInfo.get("exchCode"), itemId));
        subitems.add(lan.createTradeSubItem("COMMUNIT_ID", "0", itemId));
        subitems.add(lan.createTradeSubItem("USER_CALLING_AREA", msg.get("areaCode"), itemId));
        subitems.add(lan.createTradeSubItem("USER_TYPE_CODE", "0", itemId));
        subitems.add(lan.createTradeSubItem("CB_ACCESS_TYPE", cbAccessType, itemId));
        subitems.add(lan.createTradeSubItem("ACCT_NBR", serialNumber, itemId));
        subitems.add(lan.createTradeSubItem("COMMPANY_NBR", "", itemId));
        subitems.add(lan.createTradeSubItem("DETAIL_INSTALL_ADDRESS", newUserInfo.get("installAddress"), itemId));
        subitems.add(lan.createTradeSubItem("TOWN_FLAG", "C", itemId));
        subitems.add(lan.createTradeSubItem("POSITION_XY", "", itemId));
        subitems.add(lan.createTradeSubItem("TERMINAL_MODEL", machineModelCode, itemId));
        subitems.add(lan.createTradeSubItem("SPEED", newUserInfo.get("speedLevel"), itemId));
        subitems.add(lan.createTradeSubItem("HZGS_0000", "", itemId));
        subitems.add(lan.createTradeSubItem("LOCAL_NET_CODE", "0311", itemId));
        subitems.add(lan.createTradeSubItem("EXPECT_RATE", "", itemId));
        subitems.add(lan.createTradeSubItem("INSTALL_ADDRESS", newUserInfo.get("installAddress"), itemId));
        subitems.add(lan.createTradeSubItem("TERMINAL_TYPE", machineProvide, itemId));// zzc 20170309
        subitems.add(lan.createTradeSubItem("TERMINAL_BRAND", machineBrandCode, itemId));
        subitems.add(lan.createTradeSubItem("COMMUNIT_NAME", "", itemId));
        subitems.add(lan.createTradeSubItem("INIT_PASSWD", "0", itemId));
        subitems.add(lan.createTradeSubItem("COLLINEAR_TYPE", "X3", itemId));
        subitems.add(lan.createTradeSubItem("ACCT_PASSWD", "123456", itemId));
        subitems.add(lan.createTradeSubItemE("LINK_NAME", "", itemId));
        subitems.add(lan.createTradeSubItemE("OTHERCONTACT", "", itemId));
        subitems.add(lan.createTradeSubItemE("LINK_PHONE", "", itemId));

        TRADE_SUB_ITEM.put("item", subitems);
        ext.put("tradeSubItem", TRADE_SUB_ITEM);

        Map TRADE = new HashMap();
        TRADE.put("base", base);
        TRADE.put("ext", ext);

        return TRADE;
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
