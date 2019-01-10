package com.ailk.ecaop.common.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.util.IsEmptyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.product.DealNewCbssProduct;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class MixOpenUtils {

    private static Logger logger = LoggerFactory.getLogger(MixOpenUtils.class);

    /**
     * 准备BASE节点,目前支持新装、套餐变更以及23转4业务
     * 
     * @param inMap
     * @param ext
     * @return
     */
    public Map preBase(Map inMap, Map ext) {
        Map base = new HashMap();
        base.put("subscribeId", inMap.get("subscribeId"));
        base.put("tradeId", inMap.get("tradeId"));
        base.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
        base.put("nextDealTag", "Z");
        Object eModeCode = inMap.get("eModeCode");
        base.put("inModeCode", null == eModeCode || "".equals(eModeCode) ? "E" : eModeCode);
        base.put("tradeTypeCode", getTradeTypeCode2InstallMode(inMap.get("installMode")));
		// 使用公共方法处理过的主产品的产品id和品牌编码 by wangmc 20181128
        base.put("productId", ext.get("mproductId"));
        base.put("brandCode", ext.get("mbrandCode"));
        base.put("userId", inMap.get("userId"));
        base.put("custId", inMap.get("custId"));
        base.put("usecustId", inMap.get("custId"));
        if (null != inMap.get("remark")) {
            base.put("remark", inMap.get("remark"));
        }
        else {
            base.put("remark", "");
        }
        base.put("acctId", inMap.get("acctId"));
        base.put("userDiffCode", "00");
        Map broadBandTemplate = (Map) inMap.get("broadBandTemplate");
        Map broadBandCustInfo = (Map) broadBandTemplate.get("broadBandCustInfo");
        base.put("netTypeCode", CommodityType2NetTypeCode(broadBandTemplate.get("broadBandType")));
        String acctSerialNumber = (String) broadBandTemplate.get("acctSerialNumber");
        if (StringUtils.isEmpty(acctSerialNumber)) {
            acctSerialNumber = (String) broadBandTemplate.get("accountCode");
        }
        base.put("serinalNamber", acctSerialNumber);
        base.put("custName", broadBandCustInfo.get("customerName"));
        ChangeCodeUtils ChangeCodeUtils = new ChangeCodeUtils();
        base.put("eparchyCode", ChangeCodeUtils.changeEparchyUnStatic(inMap));
        base.put("tradeCityCode", inMap.get("district"));
        base.put("cityCode", null == inMap.get("cityCode") ? inMap.get("district") : inMap.get("cityCode"));
        MapUtils.arrayPutFix(GetDateUtils.getDate(), base, new String[] { "startDate", "acceptDate", "execTime" });
        MapUtils.arrayPutFix(base, new String[] { "olcomTag", "operFee", "foregift", "advancePay", "cancelTag",
                "checktypeCode", "chkTag" });// checktypeCode 需要进行转码
        MapUtils.arrayPutFix("", base, new String[] { "feeState", "feeStaffId" });

        base.put("contact", broadBandCustInfo.get("contactPerson"));
        base.put("contactPhone", broadBandCustInfo.get("contactPhone"));
        base.put("contactAddress", broadBandCustInfo.get("contactAddress"));
        //Map broadBandTemplate = (Map) inMap.get("broadBandTemplate");
        List<Map> actorInfos = (List<Map>) broadBandTemplate.get("actorInfo");
        if (!IsEmptyUtils.isEmpty(actorInfos)) {
            Map actorInfoMap = actorInfos.get(0);
            base.put("actorName", actorInfoMap.get("actorName"));
            String actorCertTypeId = (String) actorInfoMap.get("actorCertTypeId");
            if (com.ailk.ecaop.common.utils.IsEmptyUtils.isEmpty(actorCertTypeId)) {
                throw new EcAopServerBizException("9999", "代办人证件类型不能为空！");
            }
            base.put("actorCertTypeId", CertTypeChangeUtils.certTypeMall2Cbss(actorCertTypeId));
            base.put("actorPhone", actorInfoMap.get("actorPhone"));
            base.put("actorCertNum", actorInfoMap.get("actorCertNum"));
        }
        return base;
    }

    /**
     * 根据融合里的installMode来生成业务类型
     * 
     * @param installMode
     * @return
     */
    private String getTradeTypeCode2InstallMode(Object installMode) {
        return "0".equals(installMode) ? "0010" : "1".equals(installMode) ? "0340" : "0448";
    }

    public Map preExt(Map inMap, Exchange exchange) {
        Map ext = new HashMap();
        inMap.put("isAppCode", exchange.getAppCode());
        // CHANGE_1 融合业务写卡前置放入北分沃易售的appkey 共7处,可使用 'CHANGE_' 搜索 by wangmc 20180630
        if ("smno".equals(exchange.getMethodCode()) && "mabc|bjaop".contains(exchange.getAppCode())) {
            inMap.put("BFAppCode", exchange.getAppCode());
        }
        Map broadBandTemplate = (Map) inMap.get("broadBandTemplate");
        List<Map> broadBandProduct = (List<Map>) broadBandTemplate.get("broadBandProduct");
        List<Map> productInfo = new ArrayList<Map>();
        String provinceCode = "00" + inMap.get("province");
        String nowTime = GetDateUtils.getDate();
        String endDate = MagicNumber.CBSS_DEFAULT_EXPIRE_TIME;
        LanUtils lan = new LanUtils();
        Map tradeSubItem = new HashMap();
        List<Map> tradeSubItemList = new ArrayList<Map>();
        boolean isNoChange = false;
        String mainProductId = "";

        // 为查询iptv或互联网电视的sql增加产品id做准备
        String iptvProductId = "";
        String interTvProductId = "";
        Object apptx = exchange.getIn().getBody(Map.class).get("apptx");
        for (Map prod : broadBandProduct) {
            // 0代表主产品，有人在这之前转码了，过分╭(╯^╰)╮
            if ("0".equals(prod.get("productMode"))) {
                mainProductId = (String) prod.get("productId");
            }
            if (prod.get("productId").equals(inMap.get("oldProduct"))) {
                isNoChange = true;
                continue;
            }
            if (null == prod.get("firstMonBillMode")) {
                prod.put("firstMonBillMode", inMap.get("firstMonBillMode"));
            }
            productInfo.add(prod);
            // 下发iptv和互联网电视的产品节点tradeprouduct
            if (null != prod.get("iptvProductId")) {
                iptvProductId = (String) prod.get("iptvProductId");
            }

            if (null != prod.get("interTvProductId")) {
                interTvProductId = (String) prod.get("interTvProductId");
            }
            if (null != iptvProductId && !"".equals(iptvProductId))
            {
                Map iptvProduct = new HashMap();
                iptvProduct.put("productId", iptvProductId);
                iptvProduct.put("productMode", "1");
                iptvProduct.put("isIpOrInterTv", "1");
                productInfo.add(iptvProduct);
            }
            if (null != interTvProductId && !"".equals(interTvProductId))
            {
                Map interTvProduct = new HashMap();
                interTvProduct.put("productId", interTvProductId);
                interTvProduct.put("productMode", "1");
                interTvProduct.put("isIpOrInterTv", "1");
                productInfo.add(interTvProduct);
            }
        }
        String startDate = GetDateUtils.getDate();
        if ("1".equals(inMap.get("installMode")) || "2".equals(inMap.get("installMode"))) {
            inMap.put("methodCode", "smnp");
            startDate = GetDateUtils.getNextMonthFirstDayFormat();
        }
        if ("1".equals(inMap.get("installMode")) || "2".equals(inMap.get("installMode"))) {
            inMap.put("iptvCode", "ipnr");
        }
        // 改为调用融合专用的处理产品方法 by wangmc 20170324
        inMap.put("apptx", exchange.getIn().getBody(Map.class).get("apptx"));
        if (EcAopConfigLoader.getStr("ecaop.global.param.product.mix.appcode").contains(exchange.getAppCode())) {
            ext = TradeManagerUtils.preProductInfo(productInfo, provinceCode, inMap);
        }
        else {
            ext = TradeManagerUtils.preProductInfo4Mix(productInfo, provinceCode, inMap);
        }
        List<Map> boradDiscntInfos = preBoradDiscntInfo(productInfo, "boradDiscntInfo");
        Map tradeDiscnt = (Map) ext.get("tradeDiscnt");
        Map tradeSvc = (Map) ext.get("tradeSvc");
        List<Map> defaultEmelemtts = null == (List<Map>) ext.get("defaultEmelemtts") ? new ArrayList<Map>() : (List<Map>) ext.get("defaultEmelemtts");
        if (null == tradeDiscnt) {
            tradeDiscnt = new HashMap();
        }
        Object discntItem = tradeDiscnt.get("item");

        Object svcItem = tradeSvc.get("item");
        List<Map> discntList = null == discntItem ? new ArrayList<Map>() : (List<Map>) discntItem;
        List<Map> svcList = null == svcItem ? new ArrayList<Map>() : (List<Map>) svcItem;

        if (null != boradDiscntInfos && 0 != boradDiscntInfos.size()) {
            for (Map map : boradDiscntInfos) {
                inMap.put("productId", mainProductId);
                inMap.put("PROVINCE_CODE", provinceCode);
                Map paraMap = MapUtils.asMap("PROVINCE_CODE", provinceCode, "EPARCHY_CODE", inMap.get("eparchyCode"),
                        "PRODUCT_ID", map.get("productId"));
                List<Map> DiscntInfoList = (List<Map>) map.get("DiscntInfoList");
                for (Map temp : DiscntInfoList) {
                    inMap.put("elementId", temp.get("boradDiscntId"));
                    paraMap.put("ELEMENT_ID", temp.get("boradDiscntId"));
                    String itemId = TradeManagerUtils.getSequence((String) (inMap.get("eparchyCode")),
                            "SEQ_ITEM_ID");
                    for (Map defaultEmelemtt : defaultEmelemtts) {
                        String defaultBoradDiscntId = (String) defaultEmelemtt.get("defaultEmelemtt");
                        if (null != discntList && 0 != discntList.size()) {
                            for (Map dicnt : discntList) {
                                String discntDiscntCode = String.valueOf(dicnt.get("discntCode"));
                                if (defaultBoradDiscntId.equals(discntDiscntCode)) {
                                    itemId = (String) dicnt.get("itemId");
                                    break;
                                }
                            }
                        }
                    }
                    Map discntMap = TradeManagerUtils.preProductInfoByElementIdProductId4CB(paraMap);
                    discntMap.put("xDatatype", "NULL");
                    discntMap.put("id", inMap.get("userId"));
                    discntMap.put("idType", "1");
                    discntMap.put("userIdA", "-1");
                    discntMap.put("specTag", "1");
                    // CHANGE_6 修改specTag为0,0正常、1特殊、2关联（用户加集团后的优惠），通常为0 by wangmc 20180630
                    if (inMap.containsKey("BFAppCode")) {
                        discntMap.put("specTag", "0");
                    }
                    discntMap.put("relationTypeCode", "");
                    discntMap.put("startDate", startDate);
                    discntMap.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                    discntMap.put("modifyTag", "0");
                    discntMap.put("itemId", itemId);
                    discntList.add(discntMap);
                    inMap.put("itemId", itemId);
                    inMap.put("productId", map.get("productId"));
                    tradeSubItemList.addAll(preTradeSubItemCommonInfo4Board(inMap));

                }
            }
        }
        // List<Map> iptvInfos = collectBoradDiscntInfo(productInfo, "iptvInfo");
        // if (iptvInfos != null) {
        // for (Map map : iptvInfos) {
        // inMap.put("elementId", map.get("IptvDiscnt"));
        // discntList.add(TradeManagerUtils.preProductInfoByElementId(inMap));
        // }
        // }
        // 处理IPTV信息和互联网信息
        String iptvItemIdSvc = "";
        String interTvItemIdSvc = "";
        List<Map> iptvInfos = collectBoradDiscntInfo(productInfo, "iptvInfo");
        List<Map> interTvInfos = collectBoradDiscntInfo(productInfo, "interTvInfo");
        Map tempMap = MapUtils.asMap("PROVINCE_CODE", provinceCode, "EPARCHY_CODE", inMap.get("eparchyCode"));
        if (iptvInfos != null) {

            // 处理退订iptv by wangmc 20181029
            dealUnBookIptv(iptvInfos, discntList, svcList, ext, iptvProductId, inMap);

            tempMap.put("PRODUCT_ID", iptvProductId);// 放入iptv产品id by wangmc
            for (Map map : iptvInfos) {

                List<Map> iptvDiscntInfos = (List) map.get("IptvDiscntInfo");
                String serviceId = (String) map.get("IptvService");
                if (iptvDiscntInfos != null) {
                    Map discntMap = new HashMap();
                    for (Map iptvDiscntInfo : iptvDiscntInfos) {
                        String iptvItemId = TradeManagerUtils.getSequence((String) (inMap.get("eparchyCode")),
                                "SEQ_ITEM_ID");
                        Map iptvDiscntAttrMap = (Map) iptvDiscntInfo.get("iptvDiscntAttr");
                        inMap.put("elementId", iptvDiscntInfo.get("IptvDiscntId"));
                        tempMap.put("ELEMENT_ID", iptvDiscntInfo.get("IptvDiscntId"));
                        tempMap.put("ELEMENT_TYPE_CODE", "D");
                        if (IsEmptyUtils.isEmpty(iptvProductId)) {
                            discntMap = TradeManagerUtils.preProductInfoByElementId4CB(tempMap);
                        }
                        else {// 使用带产品ID的sql
                            discntMap = TradeManagerUtils.preProductInfoNoDefaultTag4CB(tempMap);
                        }
                        discntMap.put("xDatatype", "NULL");
                        discntMap.put("id", inMap.get("userId"));
                        discntMap.put("idType", "1");
                        discntMap.put("userIdA", "-1");
                        discntMap.put("specTag", "1");
                        // CHANGE_5 修改specTag为0,0正常、1特殊、2关联（用户加集团后的优惠），通常为0 by wangmc 20180630
                        if (inMap.containsKey("BFAppCode")) {
                            discntMap.put("specTag", "0");
                        }
                        discntMap.put("relationTypeCode", "");
                        discntMap.put("startDate", startDate);
                        discntMap.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                        discntMap.put("modifyTag", "0");
                        discntMap.put("itemId", iptvItemId);
                        discntList.add(discntMap);
                        // 如果有iptv的资费属性，则下发tradeSubItem节点
                        if (null != iptvDiscntAttrMap && !iptvDiscntAttrMap.isEmpty()) {
                            inMap.put("iptvItemId", iptvItemId);
                            tradeSubItemList.addAll(preTradeSubItemCommonInfo4Iptv(inMap, iptvDiscntAttrMap));
                        }

                    }
                }
                if (!IsEmptyUtils.isEmpty(serviceId)) {
                    List<Map> IptvServiceAttrs = (List<Map>) map.get("IptvServiceAttr");
                    Map svcMap = new HashMap();
                    iptvItemIdSvc = TradeManagerUtils.getSequence((String) (inMap.get("eparchyCode")), "SEQ_ITEM_ID");
                    tempMap.put("ELEMENT_ID", serviceId);
                    tempMap.put("ELEMENT_TYPE_CODE", "S");
                    if (IsEmptyUtils.isEmpty(iptvProductId)) {
                        svcMap = TradeManagerUtils.preProductInfoByServiceId4CB(tempMap);
                    }
                    else {
                        svcMap = TradeManagerUtils.preProductInfoNoDefaultTag4CB(tempMap);
                    }
                    svcMap.put("xDatatype", "NULL");
                    svcMap.put("userId", inMap.get("userId"));
                    svcMap.put("userIdA", "-1");
                    svcMap.put("specTag", "1");
                    svcMap.put("startDate", startDate);
                    svcMap.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                    svcMap.put("modifyTag", "0");
                    svcMap.put("itemId", iptvItemIdSvc);
                    svcList.add(svcMap);
                    if (IptvServiceAttrs != null && IptvServiceAttrs.size() != 0) {
                        for (Map IptvServiceAttr : IptvServiceAttrs) {
                            tradeSubItemList.add(lan.createTradeSubItemD(iptvItemIdSvc,
                                    (String) IptvServiceAttr.get("code"), IptvServiceAttr.get("value"), nowTime,
                                    endDate));// 赵伟斌要求iptv时间改成当前时间
                        }
                    }
                }
            }
        }
        if (interTvInfos != null) {
            for (Map map : interTvInfos) {
                List<Map> interTvDiscntInfos = (List) map.get("interTvDiscntInfo");
                String interTvInfoService = map.get("interTvInfoService") + "";
                tempMap.put("PRODUCT_ID", interTvProductId);// 放入iptv产品id by wangmc
                if (interTvDiscntInfos != null) {
                    Map discntMap = new HashMap();
                    for (Map interTvDiscntInfo : interTvDiscntInfos) {
                        String interTvItemId = TradeManagerUtils.getSequence((String) (inMap.get("eparchyCode")),
                                "SEQ_ITEM_ID");
                        String interTvDiscntId = (String) interTvDiscntInfo.get("interTvDiscntId");
                        if ("0".equals(interTvDiscntId)) {
                            continue;
                        }
                        tempMap.put("ELEMENT_ID", interTvDiscntId);
                        tempMap.put("ELEMENT_TYPE_CODE", "D");
                        if (IsEmptyUtils.isEmpty(interTvProductId)) {
                            discntMap = TradeManagerUtils.preProductInfoByElementId4CB(tempMap);
                        }
                        else {// 使用带产品ID的sql
                            discntMap = TradeManagerUtils.preProductInfoNoDefaultTag4CB(tempMap);
                        }
                        discntMap.put("xDatatype", "NULL");
                        discntMap.put("id", inMap.get("userId"));
                        discntMap.put("idType", "1");
                        discntMap.put("userIdA", "-1");
                        discntMap.put("specTag", "1");
                        discntMap.put("relationTypeCode", "");
                        discntMap.put("startDate", startDate);
                        discntMap.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                        discntMap.put("modifyTag", "0");
                        discntMap.put("itemId", interTvItemId);
                        discntList.add(discntMap);
                    }
                }
                if (null != interTvInfoService) {
                    List<Map> iptvServiceAttrs = (List<Map>) map.get("interTvServiceAttr");
                    Map svcMap = new HashMap();
                    tempMap.put("ELEMENT_ID", interTvInfoService);
                    tempMap.put("ELEMENT_TYPE_CODE", "S");
                    interTvItemIdSvc = TradeManagerUtils
                            .getSequence((String) (inMap.get("eparchyCode")), "SEQ_ITEM_ID");
                    if (IsEmptyUtils.isEmpty(interTvProductId)) {
                        svcMap = TradeManagerUtils.preProductInfoByServiceId4CB(inMap);
                    }
                    else {
                        svcMap = TradeManagerUtils.preProductInfoNoDefaultTag4CB(tempMap);
                    }
                    svcMap.put("xDatatype", "NULL");
                    svcMap.put("userId", inMap.get("userId"));
                    svcMap.put("userIdA", "-1");
                    svcMap.put("specTag", "1");
                    svcMap.put("startDate", startDate);
                    svcMap.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                    svcMap.put("modifyTag", "0");
                    svcMap.put("itemId", interTvItemIdSvc);
                    svcList.add(svcMap);
                    if (iptvServiceAttrs != null && iptvServiceAttrs.size() != 0) {
                        for (Map interTvServiceAttr : iptvServiceAttrs) {
                            tradeSubItemList.add(lan.createTradeSubItemD(interTvItemIdSvc,
                                    (String) interTvServiceAttr.get("code"), interTvServiceAttr.get("value"), nowTime,
                                    endDate));
                        }
                    }

                }
            }
        }
        inMap.put("iptvItemId", iptvItemIdSvc);
        inMap.put("interTvItemId", interTvItemIdSvc);
        // 上面处理宽带资费的时候调用的sql没有查询非默认的,可能会重复,调用去重方法
        discntList = NewProductUtils.newDealRepeat(discntList, "discntList");
        svcList = NewProductUtils.newDealRepeat(svcList, "svcList");
        tradeDiscnt.put("item", discntList);
        tradeSvc.put("item", svcList);
        ext.put("tradeDiscnt", tradeDiscnt);
        ext.put("tradeSvc", tradeSvc);
        dealOldProductInfo(inMap, ext);
        Map tradeProduct = (Map) ext.get("tradeProduct");
        List<Map> item = (List) tradeProduct.get("item");
        String productId = getSomeValueFromProduct(item, "productId");
        String brandCode = getSomeValueFromProduct(item, "brandCode");
        inMap.put("productId", productId);
        inMap.put("brandCode", brandCode);
        Map tradeProductType = (Map) ext.get("tradeProductType");
        List<Map> itemT = (List) tradeProductType.get("item");
        String productTypeCode = getSomeValueFromProduct(itemT, "productTypeCode");
        // 收入集团归集处理
        Map tradeRelation = new HashMap();
        if ("1".equals(broadBandTemplate.get("groupFlag"))) {
            Map jtcpMap = new HashMap();
            jtcpMap.put("province", "00" + inMap.get("province"));
            jtcpMap.put("groupId", broadBandTemplate.get("groupId"));
            jtcpMap.put("operatorId", inMap.get("operatorId"));
            jtcpMap.put("city", ChangeCodeUtils.changeEparchy(inMap));
            jtcpMap.put("district", inMap.get("district"));
            jtcpMap.put("channelId", inMap.get("channelId"));
            jtcpMap.put("channelType", inMap.get("channelType"));
            tradeRelation = dealJtcp(exchange, jtcpMap, inMap);
            ext.put("tradeRelation", tradeRelation);
        }
        if ("0".equals(inMap.get("isNewAcct")) && !"1".equals(inMap.get("installMode"))) {
            ext.put("tradeAcct", preTradeAcct(inMap));
        }
        // 非纳入时,才需要创建用户账户和下发号码资源信息、支付关联信息
        if (!"1".equals(inMap.get("installMode"))) {
            ext.put("tradeUser", preTradeUser(inMap));
            ext.put("tradeRes", preTradeRes(inMap));
            ext.put("tradePayrelation", preTradePayrelation(inMap));
        }
        if ("1".equals(inMap.get("installMode")) && !isNoChange) {
            ext.put("tradeOther", preTradeOther(inMap));
        }
        if ("0".equals(inMap.get("custType"))) {
            ext.put("tradeCustomer", preTradeCustomer(inMap));
            ext.put("tradeCustPerson", preTradePerson(inMap));
        }
        ext.put("tradeItem", preTradeItem(inMap));
        Map tradeSubItemUtil = preTradeSubItem(inMap);
        List<Map> product = (List<Map>) broadBandTemplate.get("broadBandProduct");
        Object machineInfoObject = getSomeProduct(product, "machineInfo");
        String itemId = inMap.get("userId").toString();
        if (!IsEmptyUtils.isEmpty(machineInfoObject)) {
            Map machineInfo = ((List<Map>) machineInfoObject).get(0);

            if (!IsEmptyUtils.isEmpty(machineInfo)) {
                if (!IsEmptyUtils.isEmpty(machineInfo.get("terminalLyfs"))) {
                    tradeSubItemList.add(lan.createTradeSubItem("TERMINAL_LYFS", machineInfo.get("terminalLyfs"),
                            itemId));
                }
            }
        }

        List<Map> tradeSubItem1 = (List<Map>) tradeSubItemUtil.get("item");
        //内蒙古申请在融合新装业务受理，省分沃受理通过AOP接口传送社区经理、倒装机标示
        List<Map> removeList = new ArrayList<Map>();
        if (!IsEmptyUtils.isEmpty(inMap.get("gwQuickOpen"))) {
            for (Map trsubitem : tradeSubItem1) {
                if ("GW_QUICK_OPEN".equals(trsubitem.get("attrCode"))) {
                    removeList.add(trsubitem);
                }
            }
            tradeSubItem1.removeAll(removeList);
            String endDate1 = "";
            try {
                endDate1 = GetDateUtils.transDate(
                        GetDateUtils.getSpecifyDateTime(1, 1, GetDateUtils.transDate(GetDateUtils.getDate(), 19), 7),
                        14);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            tradeSubItemList.add(lan.createTradeSubItemAll("0", "GW_QUICK_OPEN", inMap.get("gwQuickOpen"), itemId,
                    GetDateUtils.getDate(), endDate1));
        }
        if (!IsEmptyUtils.isEmpty(inMap.get("communitId"))) {
            for (Map trsubitem : tradeSubItem1) {
                if ("COMMUNIT_ID".equals(trsubitem.get("attrCode"))) {
                    removeList.add(trsubitem);
                }
            }
            tradeSubItem1.removeAll(removeList);
            tradeSubItemList.add(lan.createTradeSubItem("COMMUNIT_ID", inMap.get("communitId"), itemId));
        }
        if (!IsEmptyUtils.isEmpty(inMap.get("communitName"))) {
            for (Map trsubitem : tradeSubItem1) {
                if ("COMMUNIT_NAME".equals(trsubitem.get("attrCode"))) {
                    removeList.add(trsubitem);
                }
            }
            tradeSubItem1.removeAll(removeList);
            tradeSubItemList.add(lan.createTradeSubItem("COMMUNIT_NAME", inMap.get("communitName"), itemId));
        }
        //关于支撑批量受理校园融合套餐的需求工单
		if("hnpr".equals(exchange.getAppCode())){
			List<Map> subItemList = (List<Map>) inMap.get("subItem");
	        if (!IsEmptyUtils.isEmpty(subItemList)) {
	            for (Map subItem : subItemList) {
	                tradeSubItemList.add(lan.createTradeSubItem((String) subItem.get("attrCode"), subItem.get("attrValue"),
	                        itemId));
	            }
	        }
		}
        tradeSubItemList.addAll(tradeSubItem1);
        // 通过附加产品获取产品节点的itemId,下面电子券要关联itemId
        preGetItemIdForProductList(ext, inMap, tradeSubItemList, lan, apptx, product);
        // CHANGE_9 by wangmc 20180719
        preTradeSubItem(broadBandTemplate, tradeSubItemList, inMap);
        tradeSubItem.put("item", tradeSubItemList);
        ext.put("tradeSubItem", tradeSubItem);
        // 取公共方法处理获得的主产品品牌和产品类别,防止覆盖;原来的取法有问题,不知道放这俩ID是干啥的,先不去掉 chg by wangmc 20181128
        ext.put("mproductTypeCode", productTypeCode);
        ext.put("mbrandCode", ext.get("brandCode"));

        ext.put("productTypeCode", productTypeCode);
        ext.put("productId", productId);
        ext.put("brandCode", brandCode);
        return ext;
    }

    /**
     * 处理退订IPTV的服务和资费节点
     * @param iptvInfos
     * @param discntList
     * @param svcList
     * @param inMap
     */
    private void dealUnBookIptv(List<Map> iptvInfos, List<Map> discntList, List<Map> svcList, Map ext,
            String iptvProductId, Map inMap) {
        List<Map> unBookIptvs = new ArrayList<Map>();
        // 做变更的iptv产品ID,在公共方法中已经处理了TRADE_PRODUCT和TRADE_PRODUCT_TYPE节点,在最后边移除这里要移除
        boolean isNeedRemove = false;
        for (Map iptv : iptvInfos) {
            // 获取变更类型，如果是退订就取出来和三户对比，然后再从iptv节点中移除退订节点，防止下面处理
            Object optType = iptv.get("optType");
            if ("1".equals(String.valueOf(optType))) {
                isNeedRemove = true;
                String IptvService = (String) iptv.get("IptvService");
                // 处理退订IPTV的服务和资费节点
                dealUnBookDisAndSvc(iptv, IptvService, discntList, svcList, inMap);
                unBookIptvs.add(iptv);
            }
        }
        // 移除要做变更的iptv
        iptvInfos.removeAll(unBookIptvs);
        if (isNeedRemove) {
            removeUnBookIptvProductInfos(ext, iptvProductId, "tradeProduct");
            removeUnBookIptvProductInfos(ext, iptvProductId, "tradeProductType");
        }
    }

    /**
     * 做变更的iptv产品ID,在公共方法中已经处理了TRADE_PRODUCT和TRADE_PRODUCT_TYPE节点,在这里移除
     * @param ext
     * @param iptvProductId
     * @param dealTypeKey
     */
    private void removeUnBookIptvProductInfos(Map ext, String iptvProductId, String dealTypeKey) {
        Map tradeProductTypes = (Map) ext.get(dealTypeKey);
        if (!IsEmptyUtils.isEmpty(tradeProductTypes)
                && !IsEmptyUtils.isEmpty((List<Map>) tradeProductTypes.get("item"))) {
            List<Map> tradeProductTypeItem = (List<Map>) tradeProductTypes.get("item");
            for (Map tradeProductType : tradeProductTypeItem) {
                if (tradeProductType.get("productId").equals(iptvProductId)) {
                    tradeProductTypeItem.remove(tradeProductType);
                    break;
                }
            }
        }
    }

    /**
     * 处理退订IPTV的服务和资费节点
     * @param unBookDiscntInfo
     * @param iptvService
     * @param discntList
     * @param svcList
     * @param inMap
     */
    private void dealUnBookDisAndSvc(Map unBookDiscntInfo, String iptvService, List<Map> discntList, List<Map> svcList,
            Map inMap) {
        List<Map> discntInfo = (List<Map>) inMap.get("OldDiscntInfo");
        List<Map> oldServiceInfo = (List<Map>) inMap.get("oldServiceInfo");
        if (!IsEmptyUtils.isEmpty(unBookDiscntInfo)) {
            List<Map> IptvDiscntInfoList = (List<Map>) unBookDiscntInfo.get("IptvDiscntInfo");
            for (Map discntInfoList : IptvDiscntInfoList) {
                String IptvDiscntId = (String) discntInfoList.get("IptvDiscntId");
                if (!IsEmptyUtils.isEmpty(IptvDiscntId)) {

                    for (Map discnt : discntInfo) {
                        String discntCode = (String) discnt.get("discntCode");
                        if (IptvDiscntId.equals(discntCode)) {
                            String startDate = (String) discnt.get("startDate");
                            String unProductId = (String) discnt.get("productId");
                            String unPackageId = (String) discnt.get("packageId");
                            Map discntMap = new HashMap();

                            discntMap.put("productId", unProductId);
                            discntMap.put("packageId", unPackageId);
                            discntMap.put("discntCode", discntCode);
                            discntMap.put("xDatatype", "NULL");
                            discntMap.put("id", inMap.get("userId"));
                            discntMap.put("idType", "1");
                            discntMap.put("userIdA", "-1");
                            discntMap.put("specTag", "1");
                            discntMap.put("relationTypeCode", "");
                            discntMap.put("startDate", startDate);
                            discntMap.put("endDate", GetDateUtils.getMonthLastDay());
                            discntMap.put("modifyTag", "1");
                            discntMap.put("itemId", TradeManagerUtils.getSequence((String) inMap.get("eparchyCode"),
                                    "SEQ_ITEM_ID"));
                            discntList.add(discntMap);
                            continue;
                        }
                    }
                }
            }

            if (!IsEmptyUtils.isEmpty(iptvService)) {
                for (Map serMap : oldServiceInfo) {
                    String serviceId = (String) serMap.get("serMap");
                    if (iptvService.equals(serviceId)) {
                        String startDate = (String) serMap.get("startDate");
                        String unProductId = (String) serMap.get("productId");
                        String unPackageId = (String) serMap.get("packageId");
                        Map svcMap = new HashMap();

                        svcMap.put("productId", unProductId);
                        svcMap.put("packageId", unPackageId);
                        svcMap.put("serviceId", serviceId);
                        svcMap.put("xDatatype", "NULL");
                        svcMap.put("userId", inMap.get("userId"));
                        svcMap.put("userIdA", "-1");
                        svcMap.put("specTag", "1");
                        svcMap.put("startDate", startDate);
                        svcMap.put("endDate", GetDateUtils.getMonthLastDay());
                        svcMap.put("modifyTag", "1");
                        svcMap.put("itemId", TradeManagerUtils.getSequence((String) inMap.get("eparchyCode"),
                                "SEQ_ITEM_ID"));
                        svcList.add(svcMap);
                        continue;
                    }
                }
            }
        }

    }

    /**
     * CHANGE_9 处理固网节点新增的tradeSubItem节点的属性值 by wangmc 20180719
     * 
     * @param broadBandTemplate
     * @param tradeSubItemList
     * @param inMap
     */
    private void preTradeSubItem(Map broadBandTemplate, List<Map> tradeSubItemList, Map inMap) {
        List<Map> subItemList = (List<Map>) broadBandTemplate.get("subItem");
        if (IsEmptyUtils.isEmpty(subItemList)) {
            return;
        }
        
        LanUtils lan = new LanUtils();
        for (Map subItem : subItemList) {
            tradeSubItemList.add(lan.createTradeSubItemB2(String.valueOf(inMap.get("userId")),
                    String.valueOf(subItem.get("code")), subItem.get("value")));
        }
    }

    /**
     * 通过附加产品获取产品节点的itemId,下面电子券要关联itemId
     * @param ext
     * @param inMap
     * @param tradeSubItemList
     * @param lan
     * @param apptx
     * @param product 
     */
    private void preGetItemIdForProductList(Map ext, Map inMap, List<Map> tradeSubItemList, LanUtils lan, Object apptx,
            List<Map> product) {
        //RTJ2018011200043-关于AOP接口支持存费送电子券业务的需求 create by zhaok Date 18/03/05
        String receMessPhone = (String) inMap.get("receMessPhone");
        if (IsEmptyUtils.isEmpty(receMessPhone)) {
            return;
        }
        List<Map> discntList = (List<Map>) ((Map) ext.get("tradeDiscnt")).get("item");

        String electronItemId = "";
        String elementId = ""; // 获取存费送电子券的资费ID,得到itemId在tradeSubItem节点下发
        if (!IsEmptyUtils.isEmpty(product)) {
            for (Map pro : product) {
                List<Map> packageElement = (List<Map>) pro.get("packageElement");
                if (!IsEmptyUtils.isEmpty(packageElement)) {
                    for (Map pe : packageElement) {
                        String electProductTag = String.valueOf(pe.get("electProductTag"));//是否存费送电子券产品标示
                        if (IsEmptyUtils.isEmpty(electProductTag) || "1".equals(electProductTag)) {
                            continue;
                        }
                        elementId = String.valueOf(pe.get("elementId"));
                    }

                }
            }
        }
        if (IsEmptyUtils.isEmpty(elementId)) {
            return;
        }
        if (!IsEmptyUtils.isEmpty(discntList)) {
            for (Map discnt : discntList) {
                if (elementId.equals(String.valueOf(discnt.get("discntCode")))) {
                    electronItemId = String.valueOf(discnt.get("itemId"));
                    break;
                }
            }
        }
        if (!IsEmptyUtils.isEmpty(electronItemId)) {
            // 次月月初
            String startDate = GetDateUtils.getNextMonthFirstDayFormat();
            // 结束时间为生效时间两年后
            String endDate = GetDateUtils.getSpecifyDateTime(1, 4, GetDateUtils.getMonthLastDay(), 2);
            tradeSubItemList.add(lan.createTradeSubItemC(electronItemId, "dzjNbr", receMessPhone,
                    startDate, GetDateUtils.TransDate(endDate, "yyyy-MM-dd HH:mm:ss")));
        }

    }

    private Map dealJtcp(Exchange exchange, Map jtcpMap, Map msg) {
        Exchange jtcpExchange = ExchangeUtils.ofCopy(exchange, jtcpMap);
        LanUtils lan = new LanUtils();
        try {

            lan.preData("ecaop.trades.sell.mob.jtcp.ParametersMapping", jtcpExchange);
            CallEngine.wsCall(jtcpExchange, "ecaop.comm.conf.url.cbss.services.OrdForAopthSer");// 地址
            lan.xml2Json("ecaop.trades.sell.mob.jtcp.template", jtcpExchange);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "集团信息查询失败" + e.getMessage());
        }
        Map retMap = jtcpExchange.getOut().getBody(Map.class);
        List<Map> userList = (List<Map>) retMap.get("useList");
        if (null == userList || userList.isEmpty()) {
            throw new EcAopServerBizException("9999", "未获取到CBSS集团客户下的用户信息");
        }
        String serialNumberA = (String) userList.get(0).get("serialNumber");
        String userIdA = (String) userList.get(0).get("userId");
        if (StringUtils.isEmpty(serialNumberA) || StringUtils.isEmpty(userIdA)) {
            throw new EcAopServerBizException("9999", "未获取到CBSS集团客户下的用户信息");
        }
        List<Map> relaItem = new ArrayList<Map>();
        Map item = new HashMap();
        Map tradeRelation = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("relationAttr", "");
        item.put("relationTypeCode", "2222");
        item.put("idA", userIdA);
        item.put("idB", msg.get("userId"));
        item.put("roleCodeA", "0");
        item.put("roleCodeB", "0");
        item.put("orderno", "");
        item.put("shortCode", "");
        item.put("startDate", GetDateUtils.getDate());
        item.put("endDate", "2050-12-31 23:59:59");
        item.put("modifyTag", "0");
        item.put("remark", "");
        item.put("serialNumberA", serialNumberA);
        item.put("serialNumberB", msg.get("serialNumber"));
        item.put("itemId", TradeManagerUtils.getSequence((String) msg.get("eparchyCode"), "SEQ_ITEM_ID"));
        relaItem.add(item);
        tradeRelation.put("tradeRelation", relaItem);
        return tradeRelation;
    }

    // iptv资费属性处理 addBy sss
    private List<Map> preTradeSubItemCommonInfo4Iptv(Map inMap, Map iptvDiscntAttrMap) {
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        List<Map> retList = new ArrayList<Map>();
        String itemId = (String) inMap.get("iptvItemId");
        LanUtils lan = new LanUtils();
        Object delayType = iptvDiscntAttrMap.get("iptvDelayType");
        Object delayDiscntId = iptvDiscntAttrMap.get("iptvDelayDiscntId");
        Object delayDiscntType = iptvDiscntAttrMap.get("iptvDelayDiscntType");
        Object iptvDelayYearDiscntId = iptvDiscntAttrMap.get("iptvDelayYearDiscntId");
        if (null == iptvDelayYearDiscntId && "1".equals(delayType)) {
            throw new EcAopServerBizException("9999", "续约包年时续包年资费必传");
        }

        // 修改资费开始时间和属性开始时间不一致问题，add by tanzb
        String startDate = GetDateUtils.getDate();
        if ("1".equals(inMap.get("installMode")) || "2".equals(inMap.get("installMode"))) {
            inMap.put("methodCode", "smnp");
            startDate = GetDateUtils.getNextMonthFirstDayFormat();
        }
        // String startDate = GetDateUtils.getNextMonthFirstDayFormat();
        String endDate = MagicNumber.CBSS_DEFAULT_EXPIRE_TIME;
        if ("1".equals(delayType)) {
            retList.add(lan.createTradeSubItemC(itemId, "iptvExpireDealMode", "a", startDate, endDate));
            retList.add(lan.createTradeSubItemC(itemId, "aDiscntCode", delayDiscntId, startDate, endDate));
            retList.add(lan.createTradeSubItemC(itemId, "bDiscntCode", iptvDelayYearDiscntId, startDate, endDate));
        }
        if ("2".equals(delayType)) {
            retList.add(lan.createTradeSubItemC(itemId, "iptvExpireDealMode", "b", startDate, endDate));
            retList.add(lan.createTradeSubItemC(itemId, "aDiscntCode", delayDiscntId, startDate, endDate));
            retList.add(lan.createTradeSubItemC(itemId, "bDiscntCode", "", startDate, endDate));
        }
        if ("3".equals(delayType)) {
            retList.add(lan.createTradeSubItemC(itemId, "iptvExpireDealMode", "t", startDate, endDate));
            retList.add(lan.createTradeSubItemC(itemId, "aDiscntCode", delayDiscntId, startDate, endDate));
            retList.add(lan.createTradeSubItemC(itemId, "bDiscntCode", "", startDate, endDate));
        }
        Map discntMap = new HashMap();
        discntMap.put("DISCNT_CODE", inMap.get("elementId") + "");
        List<Map> discntList = n25Dao.queryBrdOrLineDiscntInfo(discntMap);
        Map discntListMap = null;
        if (discntList != null && discntList.size() > 0) {
            discntListMap = discntList.get(0);

        }
        else {
            throw new EcAopServerBizException("9999", "在资费信息表中未查询到ID为【" + inMap.get("elementId") + "】的资费信息");

        }
        String cycleNum = "";
        int cycleFee = 0;
        String resActivityper = String.valueOf(discntListMap.get("END_OFFSET"));// 失效效偏移时间
        String endTag = String.valueOf(discntListMap.get("END_ENABLE_TAG"));// 是否是绝对时间
        String disnctFee = String.valueOf(discntListMap.get("DISNCT_SALEFEE"));// 资费销售金额 单位分
        String endUnit = String.valueOf(discntListMap.get("END_UNIT"));// 0:天 1:自然天 2:月 3:自然月 4:年 5:自然年 ;
        if (!"null".equals(endTag) && "1".equals(endTag) && !"null".equals(resActivityper) && !"null".equals(endUnit)
                && "3".equals(endUnit)) {
            cycleNum = resActivityper;
        }
        else {
            cycleNum = "12";
        }
        if ("null".equals(disnctFee)) {
            throw new EcAopServerBizException("9999", "在资费信息表中未查询到销售费用DISNCT_SALEFEE信息");
        }
        cycleFee = Integer.parseInt(disnctFee) / 100;
        retList.add(lan.createTradeSubItemC(itemId, "endStart", delayDiscntType, startDate, endDate));
        retList.add(lan.createTradeSubItemC(itemId, "effectMode", delayDiscntType, startDate, endDate));
        retList.add(lan.createTradeSubItemC(itemId, "adStart", "0", startDate, endDate));
        retList.add(lan.createTradeSubItemC(itemId, "cycleNum", "1", startDate, endDate));
        retList.add(lan.createTradeSubItemC(itemId, "cycle", cycleNum, startDate, endDate));
        retList.add(lan.createTradeSubItemC(itemId, "iptvFixedHire", cycleNum, startDate, endDate));
        retList.add(lan.createTradeSubItemC(itemId, "cycleFee", cycleFee, startDate, endDate));
        retList.add(lan.createTradeSubItemC(itemId, "recharge", "", startDate, endDate));
        retList.add(lan.createTradeSubItemC(itemId, "callBack", "0", startDate, endDate));
        return retList;
    }

    void dealOldProductInfo(Map inMap, Map ext) {
        if (!"1".equals(inMap.get("installMode"))) {
            return;
        }
        Map tradeProductType = (Map) ext.get("tradeProductType");
        List<Map> tradeProductTypeItem = (List<Map>) tradeProductType.get("item");
        // inMap.put("productTypeCode", tradeProductTypeItem.get(0).get("productTypeCode"));productTypeCode有啥用？
        Map itemProductType = new HashMap();
        String endDate = GetDateUtils.TransDate(GetDateUtils.getMonthLastDay(), "yyyy-MM-dd HH:mm:ss");
        itemProductType.put("xDatatype", MagicNumber.STRING_OF_NULL);
        itemProductType.put("userId", inMap.get("userId"));
        itemProductType.put("productMode", "00");
        itemProductType.put("productId", inMap.get("oldProduct"));
        itemProductType.put("productTypeCode", inMap.get("oldProductTypeCode"));
        itemProductType.put("modifyTag", "1");
        itemProductType.put("startDate", inMap.get("oldStartDate"));
        itemProductType.put("endDate", endDate);
        tradeProductTypeItem.add(itemProductType);
        tradeProductType.put("item", tradeProductTypeItem);
        ext.put("tradeProductType", tradeProductType);
        Map tradeProduct = (Map) ext.get("tradeProduct");
        List<Map> tradeProductItem = (List<Map>) tradeProduct.get("item");
        Map itemProduct = new HashMap();
        itemProduct.put("xDatatype", MagicNumber.STRING_OF_NULL);
        itemProduct.put("userId", inMap.get("userId"));
        itemProduct.put("productMode", "00");
        itemProduct.put("productId", inMap.get("oldProduct"));
        itemProduct.put("productTypeCode", inMap.get("oldProductTypeCode"));
        itemProduct.put("modifyTag", "1");
        itemProduct.put("startDate", inMap.get("oldStartDate"));
        itemProduct.put("endDate", endDate);
        itemProduct.put("userIdA", "-1");
        itemProduct.put("itemId", inMap.get("oldItemId"));
        itemProduct.put("brandCode", inMap.get("oldBrandCode"));
        tradeProductItem.add(itemProduct);
        tradeProduct.put("item", tradeProductItem);
        ext.put("tradeProduct", tradeProduct);
    }

    private String getSomeValueFromProduct(List<Map> inList, Object key) {
        for (Map m : inList) {
            if (null != m.get(key)) {
                return m.get(key).toString();
            }
        }
        return null;
    }

    /**
     * 整理产品信息节点
     * 
     * @param product
     * @param key
     * @return
     */
    private List<Map> collectBoradDiscntInfo(List<Map> product, String key) {
        List<Map> retList = new ArrayList<Map>();
        for (Map prod : product) {
            if (null != prod.get(key)) {
                retList.addAll((List<Map>) prod.get(key));
            }
        }
        return retList;
    }

    /**
     * 整理产品信息节点修改
     * 
     * @param product
     * @param key
     * @return
     */
    private List<Map> preBoradDiscntInfo(List<Map> product, String key) {
        List<Map> retList = new ArrayList<Map>();

        for (Map prod : product) {
            if (null != prod.get(key)) {
                Map temp = new HashMap();
                temp.put("productId", prod.get("productId"));
                temp.put("DiscntInfoList", prod.get(key));
                retList.add(temp);
            }
        }
        return retList;
    }

    private Map preTradeAcct(Map inMap) {
        Map tradeAcct = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("xDatatype", MagicNumber.STRING_OF_NULL);
        item.put("acctId", inMap.get("acctId"));
        // 补custId字段 add by wangrj3
        item.put("custId", inMap.get("custId"));
        item.put("payName", inMap.get("custName"));// 同BASE下的CUSTNAME
        item.put("debutyUserId", inMap.get("userId"));
        item.put("openDate", GetDateUtils.getDate());
        MapUtils.arrayPutFix("0", item, new String[] { "payModeCode", "scoreValue", "creditClassId",
                "basicCreditValue", "creditValue", "creditControlId", "debutyCode", "removeTag" });
        itemList.add(item);
        tradeAcct.put("item", itemList);
        return tradeAcct;
    }

    private Map preTradeUser(Map inMap) {
        Map tradeUser = new HashMap();
        Map item = new HashMap();
        item.put("xDatatype", MagicNumber.STRING_OF_NULL);
        item.put("usecustId", inMap.get("custId"));
        item.put("userPasswd", inMap.get("userPasswd"));
        // CHANGE_2 北分沃易售系统,做宽带新装时,下发用户密码,默认为宽带号码后六位 by wangmc 20180630
        if (inMap.containsKey("BFAppCode") && "0".equals(inMap.get("installMode"))) {
            Map broadBandTemplate = (Map) inMap.get("broadBandTemplate");
            if (!IsEmptyUtils.isEmpty(broadBandTemplate)
                    && !IsEmptyUtils.isEmpty(broadBandTemplate.get("acctSerialNumber"))) {
                String acctSerialNumber = (String) broadBandTemplate.get("acctSerialNumber");
                item.put("userPasswd", getPassWd(acctSerialNumber, 6));
            }
        }
        item.put("userTypeCode", "0");
        MapUtils.arrayPutFix(item, "scoreValue", "creditClass", "basicCreditValue", "creditValue", "removeTag",
                "acctTag", "prepayTag", "openMode", "userStateCodeset", "mputeMonthFee", "inNetMode");
        item.put("brandCode", inMap.get("brandCode"));
        item.put("productId", inMap.get("productId"));
        item.put("productTypeCode", inMap.get("productTypeCode"));
        item.put("inDate", GetDateUtils.getDate());
        item.put("openDate", GetDateUtils.getDate());
        item.put("openDepartId", inMap.get("channelId"));
        item.put("openStaffId", inMap.get("operatorId"));
        item.put("inDepartId", inMap.get("channelId"));
        item.put("inStaffId", inMap.get("operatorId"));
        String installMode = (String) inMap.get("installMode");
        // RTJ2018030200048-aop支持融合新装业务成员迁转时在cbss开户时间记录为bss原始开户时间的需求
        if ("2".equals(installMode)) {// 迁转场景才下发
            if (!IsEmptyUtils.isEmpty(inMap.get("userInfo"))) {
                Map oldUser = ((List<Map>) inMap.get("userInfo")).get(0);
                item.put("inDate", oldUser.get("inDate"));
                item.put("openDate", oldUser.get("openDate"));
            }
        }
        Map broadBandTemplate = (Map) inMap.get("broadBandTemplate");
        if (null != broadBandTemplate.get("broadBandRecomInfo")) {
            Map broadBandRecomInfo = (Map) broadBandTemplate.get("broadBandRecomInfo");
            item.put("developStaffId", broadBandRecomInfo.get("recomPersonId"));
            item.put("developDate", GetDateUtils.getDate());
            item.put("developEparchyCode", broadBandRecomInfo.get("recomCity"));
            item.put("developCityCode", broadBandRecomInfo.get("recomCity"));
            item.put("developDepartId", broadBandRecomInfo.get("recomDepartId"));
        }
        item.put("netTypeCode", CommodityType2NetTypeCode(broadBandTemplate.get("broadBandType")));
        List<Map> product = (List<Map>) broadBandTemplate.get("broadBandProduct");
        String serviceArea = (String) getSomeProduct(product, "serviceArea", "1");
        if (!"1".equals(serviceArea)) {
            item.put("cityCode", serviceArea);
        }
        tradeUser.put("item", item);
        return tradeUser;
    }

    /**
     * 准备资源信息节点,暂时未考虑卡资源信息和终端信息节点
     * 
     * @param inMap
     * @return
     */
    private Map preTradeRes(Map inMap) {
        Map item = new HashMap();
        item.put("xDatatype", MagicNumber.STRING_OF_NULL);
        item.put("reTypeCode", "0");
        Map broadBandTemplate = (Map) inMap.get("broadBandTemplate");
        String broadBandType = (String) inMap.get("broadBandType");
        String acctSerialNumber = (String) broadBandTemplate.get("acctSerialNumber");
        if (StringUtils.isEmpty(acctSerialNumber)) {
            acctSerialNumber = (String) broadBandTemplate.get("accountCode");

        }
        item.put("resCode", acctSerialNumber);
        item.put("modifyTag", "0");
        item.put("startDate", GetDateUtils.getDate());
        item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
        // 根据河南要求，固话融合新装要下发两个res节点，一个物理号码，一个逻辑号码

        Map item2 = new HashMap();
        List<Map> tradeResList = new ArrayList<Map>();
        if ("2".equals(broadBandType)) {
            item2.put("xDatatype", MagicNumber.STRING_OF_NULL);
            item2.put("reTypeCode", "1");
            item2.put("resCode", acctSerialNumber);
            item2.put("resInfo1", acctSerialNumber);
            item2.put("modifyTag", "0");
            item2.put("startDate", GetDateUtils.getDate());
            item2.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            tradeResList.add(item2);
        }
        tradeResList.add(item);
        Map tradeRes = new HashMap();
        tradeRes.put("item", tradeResList);
        return tradeRes;
    }

    private Map preTradePayrelation(Map inMap) {
        Map tradePayrelation = new HashMap();
        Map item = new HashMap();
        item.put("userId", inMap.get("userId"));
        item.put("acctId", inMap.get("acctId"));
        item.put("payitemCode", "-1");
        MapUtils.arrayPutFix("1", item, "bindType", "defaultTag", "actTag");
        MapUtils.arrayPutFix(item, "acctPriority", "userPriority", "limitType", "limit", "complementTag",
                "addupMonths", "addupMethod");
        item.put("payrelationId", inMap.get("payRelationId"));
        tradePayrelation.put("item", item);
        return tradePayrelation;
    }

    /**
     * TRADEOTHER节点只有在套餐变更时才下发
     * 封装老产品节点信息
     * rsrv_value对应useri_id
     * Rsrv_Str1对应老产品id
     * Rsrv_Str2写死00就行 Rsrv_Str3写死-9
     * rsrvStr4、rsrvStr5 PRODUCT_TYPE_CODE
     * rsrvStr6、rsrvStr7写死
     * rsrvStr9 品牌编码
     * rsrvStr10号码
     * 
     * @param inMap
     * @return
     */
    private Map preTradeOther(Map inMap) {
        Map tradeOther = new HashMap();
        Map item = new HashMap();
        item.put("rsrvValueCode", "NEXP");
        item.put("rsrvValue", inMap.get("userId"));
        item.put("rsrvStr1", inMap.get("oldProduct"));
        item.put("rsrvStr2", "00");
        item.put("rsrvStr3", "-9");
        item.put("rsrvStr4", inMap.get("oldProductTypeCode"));
        item.put("rsrvStr5", inMap.get("oldProductTypeCode"));
        item.put("rsrvStr6", "-1");
        item.put("rsrvStr7", "0");
        item.put("rsrvStr9", inMap.get("oldBrandCode"));
        item.put("rsrvStr10", inMap.get("serialNumber"));
        item.put("startDate", inMap.get("oldStartDate"));
        item.put("endDate", GetDateUtils.getMonthLastDayFormat());
        item.put("modifyTag", "1");
        tradeOther.put("item", item);
        return tradeOther;
    }

    private Map preTradeItem(Map inMap) {
        Map tradeItem = new HashMap();
        List<Map> tradeItemList = new ArrayList<Map>();
        LanUtils lan = new LanUtils();
        Map broadBandTemplate = (Map) inMap.get("broadBandTemplate");
        Object broadBandType = broadBandTemplate.get("broadBandType");
        String shareSerialNumber = (String) broadBandTemplate.get("shareSerialNumber");
        inMap.put("broadBandType", broadBandType);
        Map broadBandRecomInfo = (Map) broadBandTemplate.get("broadBandRecomInfo");
        if (null != broadBandRecomInfo) {
            tradeItemList.add(lan.createAttrInfoNoTime("DEVELOP_STAFF_ID", broadBandRecomInfo.get("recomPersonId")));
            tradeItemList.add(lan.createAttrInfoNoTime("DEVELOP_DEPART_ID", broadBandRecomInfo.get("recomDepartId")));
        }
        //目前只有固话+宽带都新装时才会有共线关系，其他情况下不要传共线
        if ((StringUtils.isNotEmpty(shareSerialNumber) || "1".equals(inMap.get("isSerial"))) && "0".equals(inMap.get
                ("installMode"))) {
            tradeItemList.add(LanUtils.createTradeItem("SFGX_2060", "Y"));
            if ("1".equals(broadBandType)) {
                //宽带 这个属性先写死 GZDH固网产品品牌 GZKD宽带产品品牌
                tradeItemList.add(LanUtils.createTradeItem("GXLX_TANGSZ", "GZDH:1:30"));
            }
            else if ("2".equals(broadBandType)) {
                tradeItemList.add(LanUtils.createTradeItem("GXLX_TANGSZ", "GZKD:1:40"));
            }

        }
        else {
            tradeItemList.add(LanUtils.createTradeItem("SFGX_2060", "N"));
            tradeItemList.add(LanUtils.createTradeItem("GXLX_TANGSZ", ""));
        }

        if ("2".equals(broadBandType)) {
            tradeItemList.add(LanUtils.createTradeItem("REL_COMP_PROD_ID", "89017299"));// 暂且写死
            tradeItemList.add(LanUtils.createTradeItem("ROLE_CODE_B", "3"));
            tradeItemList.add(LanUtils.createTradeItem("OPER_CODE", "1"));
        }// 宽带业务 by wangmc 20180706
        else if (inMap.containsKey("BFAppCode") && "1".equals(broadBandType) && "0".equals(inMap.get("installMode"))) {
            // CHANGE_4 北分系统宽带业务时,下发OPER_CODE记录用户操作状态 OPER_CODE 1:新增， 2：纳入 3：变更 4:拆分 5:拆机
            tradeItemList.add(LanUtils.createTradeItem("OPER_CODE", "1"));
            // 也加上这个,修改为从MixOpenAppProcessor中传进来 20180717
            tradeItemList.add(LanUtils.createTradeItem("REL_COMP_PROD_ID", inMap.get("mixVmproductId")));
            // 宽带业务是否新建客户标识
            if ("0|1".contains(String.valueOf(inMap.get("custType")))) {
                tradeItemList.add(LanUtils.createTradeItem("EXISTS_CUST", inMap.get("custType")));
            }
            // 这个字段也加上,宽带默认是4 20180717
            tradeItemList.add(LanUtils.createTradeItem("ROLE_CODE_B", "4"));
        }
        String acctSerialNumber = (String) broadBandTemplate.get("acctSerialNumber");
        if (StringUtils.isEmpty(acctSerialNumber)) {
            acctSerialNumber = (String) broadBandTemplate.get("accountCode");
        }
        tradeItemList.add(LanUtils.createTradeItem("PH_NUM", broadBandTemplate.get("acctSerialNumber")));
        tradeItemList.add(LanUtils.createTradeItem("COMP_DEAL_STATE", "0"));

        // 下标索引,及其重要
        ChangeCodeUtils ChangeCodeUtils = new ChangeCodeUtils();
        tradeItemList.add(LanUtils.createTradeItem("ALONE_TCS_COMP_INDEX", inMap.get("aloneTcsCompIndex").toString()));
        // CHANGE_8 北分宽带业务时,下发该字段为1 by wangmc 20180717
        if (inMap.containsKey("BFAppCode") && "1".equals(broadBandType) && "0".equals(inMap.get("installMode"))) {
            tradeItemList.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", "1"));
        }
        else {
            tradeItemList.add(LanUtils.createTradeItem("STANDARD_KIND_CODE",
                    ChangeCodeUtils.changeEparchyUnStatic(inMap)));
        }
        // 应浙江要求下发光猫信息
        List<Map> paralist = (List<Map>) inMap.get("para");
        if (paralist != null && paralist.size() != 0)
        {
            for (Map para : paralist)
            {
                if ("extraInfo".equals(para.get("paraId")))
                {
                    tradeItemList.add(LanUtils.createTradeItem("EXTRA_INFO", para.get("paraValue")));
                }
            }
        }
        else
        {
            tradeItemList.add(LanUtils.createTradeItem("EXTRA_INFO", ""));
        }
        tradeItemList.add(LanUtils.createTradeItem("WORK_STAFF_ID", ""));
        tradeItemList.add(LanUtils.createTradeItem("WORK_TRADE_ID", ""));
        tradeItemList.add(LanUtils.createTradeItem("NO_BOOK_REASON", ""));
        tradeItemList.add(LanUtils.createTradeItem("EXISTS_ACCT", "0"));
        tradeItemList.add(LanUtils.createTradeItem("SUB_TYPE", "0"));
        tradeItemList.add(LanUtils.createTradeItem("WORK_DEPART_ID", ""));
        tradeItemList.add(LanUtils.createTradeItem("REOPEN_TAG", "2"));
        try {

            if (null != broadBandTemplate.get("bookingDate")) {
                tradeItemList.add(LanUtils.createTradeItem("BOOK_FLAG", "1"));// 1预约 0非预约
                tradeItemList.add(LanUtils.createTradeItem("PRE_START_TIME",
                        GetDateUtils.transDate((String) broadBandTemplate.get("bookingDate"), 19)));
            }
            else {
                tradeItemList.add(LanUtils.createTradeItem("BOOK_FLAG", "0"));// 1预约 0非预约
                tradeItemList.add(LanUtils.createTradeItem("PRE_START_TIME", ""));
            }
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", e.getMessage());
        }

        // String acctSerialNumber = broadBandTemplate.get("acctSerialNumber").toString();
        // String acctSerialNumber = (String) broadBandTemplate.get("acctSerialNumber");
        if (StringUtils.isEmpty(acctSerialNumber)) {
            acctSerialNumber = (String) broadBandTemplate.get("accountCode");
        }
        String passWd = getPassWd(acctSerialNumber);
        tradeItemList.add(LanUtils.createTradeItem("USER_PASSWD", passWd));
        tradeItemList.add(LanUtils.createTradeItem("NEW_PASSWD", passWd));
        inMap.put("passWd", passWd);
        inMap.put("serialNumber", acctSerialNumber);
        if ("1".equals(inMap.get("installMode"))) {
            tradeItemList.add(LanUtils.createTradeItem("MARKETING_MODE", "1"));
            tradeItemList.add(LanUtils.createTradeItem("IMMEDIACY_INFO", "0"));
            tradeItemList.add(LanUtils.createTradeItem("USER_ACCPDATE", ""));
        }
        if ("1".equals(inMap.get("markingTag"))) {
            tradeItemList.add(LanUtils.createTradeItem("MARKING_APP", "1"));
        }
        // RHA2017102300039-关于支撑通过标记控制代理商办理业务时是否扣周转金的需求
        if ("0".equals(inMap.get("deductionTag"))) {
            tradeItemList.add(LanUtils.createTradeItem("FEE_TYPE", "1"));
        }
        // RHA2018010500038-融合业务后激活需求
        // 压单标志为1且为写卡前置接口才生效
        if ("1".equals(inMap.get("delayTag")) && "smno".equals(inMap.get("METHODCODE"))) {
            tradeItemList.add(LanUtils.createTradeItem("E_DELAY_TIME_CEL", "1"));
        }

        List<Map> actorInfoList = (List<Map>) broadBandTemplate.get("actorInfo");
        Map actorInfo = null;
        if (actorInfoList != null && actorInfoList.size() > 0) {
            actorInfo = actorInfoList.get(0);
            tradeItemList.add(LanUtils.createTradeItem("ACTOR_ADDRESS", actorInfo.get("actorAddress")));
        }

        tradeItem.put("item", tradeItemList);
        return tradeItem;
    }

    /**
     * 根据号码截取密码,默认取后6位
     * 
     * @param serialNumber
     * @return
     */
    private String getPassWd(String serialNumber) {
        return getPassWd(serialNumber, 6);
    }

    /**
     * 根据号码截取密码
     * 
     * @param serialNumber
     * @param length
     * @return
     */
    private String getPassWd(String serialNumber, int length) {
        if (length >= serialNumber.length()) {
            return serialNumber;
        }
        return serialNumber.substring(serialNumber.length() - length, serialNumber.length());
    }

    private Map preTradeSubItem(Map inMap) {
        Object installMode = inMap.get("installMode");
        if ("0".equals(installMode)) {
            return preTradeSubItemOpen(inMap);
        }
        else if ("1".equals(installMode)) {
            return preTradeSubItmChange(inMap);
        }
        return preTradeSubItemTrans(inMap);
    }

    private List<Map> preTradeSubItemCommonInfo4Board(Map inMap) {
        String boradDiscntId = "";
        String boradDiscntCycle = "";
        String delayType = "";
        String delayDiscntId = "";// 到期资费
        String delayYearDiscntId = "";// 续包年资费
        String delayDiscntType = "";
        List<Map> retList = new ArrayList<Map>();
        Map broadBandTemplate = (Map) inMap.get("broadBandTemplate");
        if ("2".equals(broadBandTemplate.get("broadBandType"))) {
            return retList;
        }
        String itemId = (String) inMap.get("itemId");
        LanUtils lan = new LanUtils();
        List<Map> product = (List<Map>) broadBandTemplate.get("broadBandProduct");
        Map boradDiscntInfo = getBoradDiscntInfo(product);
        Map boardDiscntAttr = getBoardDiscntArrt(product);
        if (boardDiscntAttr != null && !boardDiscntAttr.isEmpty()) {
            delayType = (String) boardDiscntAttr.get("delayType");
            if (org.apache.commons.lang.StringUtils.isEmpty(delayType)) {
                throw new EcAopServerBizException("9999", "到期资费方式delayType为空，请检查");
            }
            delayYearDiscntId = (String) boardDiscntAttr.get("delayYearDiscntId");
            if (org.apache.commons.lang.StringUtils.isEmpty(delayYearDiscntId) && "1".equals(delayType)) {
                throw new EcAopServerBizException("9999", "续约包年时续包年资费必传");
            }
            delayDiscntId = (String) boardDiscntAttr.get("delayDiscntId");
            delayDiscntType = (String) boardDiscntAttr.get("delayDiscntType");
            if (org.apache.commons.lang.StringUtils.isEmpty(delayDiscntType)) {
                throw new EcAopServerBizException("9999", "生效方式delayDiscntType为空，请检查");
            }
        }
        if (boradDiscntInfo != null && !boradDiscntInfo.isEmpty()) {
            boradDiscntId = (String) boradDiscntInfo.get("boradDiscntId");
            boradDiscntCycle = (String) boradDiscntInfo.get("boradDiscntCycle");
        }
        if (StringUtils.isEmpty(boradDiscntId)) {
            throw new EcAopServerBizException("9999", "固网成员开户请选择开户资费");
        }
        // 修改资费开始时间和属性开始时间不一致问题，add by tanzb
        String startDate = GetDateUtils.getDate();
        if ("1".equals(inMap.get("installMode")) || "2".equals(inMap.get("installMode"))) {
            inMap.put("methodCode", "smnp");
            startDate = GetDateUtils.getNextMonthFirstDayFormat();
        }
        String endDate = MagicNumber.CBSS_DEFAULT_EXPIRE_TIME;
        String nextDate = GetDateUtils.getNextMonthFirstDayFormat();

        // aDiscntCode 到期资费 bDiscntCode续包年资费
        if ("1".equals(delayType)) {
            retList.add(lan.createTradeSubItemC(itemId, "expireDealMode", "a", startDate, endDate));
            retList.add(lan.createTradeSubItemC(itemId, "aDiscntCode", delayDiscntId, startDate, endDate));
            retList.add(lan.createTradeSubItemC(itemId, "bDiscntCode", delayYearDiscntId, startDate, endDate));
        }
        if ("2".equals(delayType)) {
            retList.add(lan.createTradeSubItemC(itemId, "expireDealMode", "b", startDate, endDate));
            retList.add(lan.createTradeSubItemC(itemId, "aDiscntCode", delayDiscntId, startDate, endDate));
            retList.add(lan.createTradeSubItemC(itemId, "bDiscntCode", "", startDate, endDate));
        }
        if ("3".equals(delayType)) {
            retList.add(lan.createTradeSubItemC(itemId, "expireDealMode", "t", startDate, endDate));
            retList.add(lan.createTradeSubItemC(itemId, "aDiscntCode", delayDiscntId, startDate, endDate));
            retList.add(lan.createTradeSubItemC(itemId, "bDiscntCode", "", startDate, endDate));
        }
        String cycleNum = "";
        int cycleFee = 0;
        String diacntName;
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        Map actiPeparam = new HashMap();
        actiPeparam.put("DISCNT_CODE", boradDiscntId);
        List<Map> activityList = n25Dao.queryBrdOrLineDiscntInfo(actiPeparam);
        Map activityListMap = null;
        if (activityList != null && activityList.size() > 0) {
            activityListMap = activityList.get(0);

        }
        else {
            throw new EcAopServerBizException("9999", "在资费信息表中未查询到ID为【" + boradDiscntId + "】的资费信息");

        }
        try {
            String resActivityper = String.valueOf(activityListMap.get("END_OFFSET"));// 失效效偏移时间
            String endTag = String.valueOf(activityListMap.get("END_ENABLE_TAG"));// 是否是绝对时间
            String disnctFee = String.valueOf(activityListMap.get("DISNCT_SALEFEE"));// 资费销售金额 单位分
            String endUnit = String.valueOf(activityListMap.get("END_UNIT"));// '0:天 1:自然天 2:月 3:自然月 4:年
            diacntName = String.valueOf(activityListMap.get("DISCNT_NAME"));// 资费名称
            // 5:自然年';
            if (!"null".equals(endTag) && "1".equals(endTag) && !"null".equals(resActivityper)
                    && !"null".equals(endUnit) && "3".equals(endUnit)) {
                cycleNum = resActivityper;
            }
            else {
                cycleNum = "12";
            }
            if ("null".equals(disnctFee)) {
                throw new EcAopServerBizException("9999", "在资费信息表中未查询到销售费用DISNCT_SALEFEE信息");
            }
            cycleFee = Integer.parseInt(disnctFee) / 100;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", e.getMessage() + "获取资费信息失败，资费是：" + boradDiscntId);
        }
        // 这些属性只有包年时候有
        if (!"null".equals(diacntName) && diacntName.contains("年")) {
            if (StringUtils.isNotEmpty(boradDiscntCycle)) {
                cycleNum = boradDiscntCycle;
            }
            // 山东要求宽带资费为总费用,叶总说不用加分支 by wangmc 20170911
            cycleFee *= Integer.parseInt(cycleNum);
            if ("0".equals(delayDiscntType)) {
                retList.add(lan.createTradeSubItemC(itemId, "effectMode", delayDiscntType, startDate, endDate));
                retList.add(lan.createTradeSubItemC(itemId, "adStart", startDate, startDate, endDate));
                retList.add(lan.createTradeSubItemC(itemId, "cycleNum", "1", startDate, endDate));
                retList.add(lan.createTradeSubItemC(itemId, "cycle", cycleNum, startDate, endDate));
                retList.add(lan.createTradeSubItemC(itemId, "fixedHire", cycleNum, startDate, endDate));
                retList.add(lan.createTradeSubItemC(itemId, "cycleFee", cycleFee, startDate, endDate));
            }
            else {
                retList.add(lan.createTradeSubItemC(itemId, "effectMode", delayDiscntType, nextDate, endDate));
                retList.add(lan.createTradeSubItemC(itemId, "adStart", nextDate, nextDate, endDate));
                retList.add(lan.createTradeSubItemC(itemId, "cycleNum", "1", nextDate, endDate));
                retList.add(lan.createTradeSubItemC(itemId, "cycle", cycleNum, nextDate, endDate));
                retList.add(lan.createTradeSubItemC(itemId, "fixedHire", cycleNum, nextDate, endDate));
                retList.add(lan.createTradeSubItemC(itemId, "cycleFee", cycleFee, nextDate, endDate));
            }
        }
        retList.add(lan.createTradeSubItemC(itemId, "recharge", "", startDate, endDate));
        retList.add(lan.createTradeSubItemC(itemId, "callBack", "0", startDate, endDate));
        return retList;
    }

    private Map getBoradDiscntInfo(List<Map> product) {
        Map retMap = new HashMap();
        for (Map prod : product) {
            List<Map> boradDiscntInfo = (List<Map>) prod.get("boradDiscntInfo");
            if (null == boradDiscntInfo || 0 == boradDiscntInfo.size()) {
                continue;
            }
            for (Map discnt : boradDiscntInfo) {
                if (null != discnt.get("boradDiscntId")) {
                    retMap.put("boradDiscntId", discnt.get("boradDiscntId"));
                }
                if (null != discnt.get("boradDiscntCycle")) {
                    retMap.put("boradDiscntCycle", discnt.get("boradDiscntCycle"));
                }
            }
        }
        return retMap;
    }

    private Map preTradeSubItemTrans(Map inMap) {
        List<Map> item = new ArrayList<Map>();
        Map tradeItem = new HashMap();
        LanUtils lan = new LanUtils();
        ChangeCodeUtils ChangeCodeUtils = new ChangeCodeUtils();
        String itemId1 = (String) inMap.get("itemId1");
        String itemId2 = (String) inMap.get("itemId2");
        String itemId3 = (String) inMap.get("itemId3");
        String itemId = (String) inMap.get("userId");
        item.add(lan.createTradeSubItemBroad("isHerited", "1", itemId1));
        item.add(lan.createTradeSubItemBroad("isHerited", "1", itemId2));
        item.add(lan.createTradeSubItemBroad("isHerited", "1", itemId3));
        Map broadBandTemplate = (Map) inMap.get("broadBandTemplate");
        List<Map> product = (List<Map>) broadBandTemplate.get("broadBandProduct");
        // 共线号码原来是写死河北的"031005582124"，导致省份无法竣工，暂时改为空
        String[] keyArray = {"ADDRESS_ID", "SHARE_NBR", "USETYPE", "MODULE_EXCH_ID", "serialNumber", "AREA_CODE",
                "POINT_EXCH_ID", "SWITCH_EXCH_ID"};
        Object[] valueArray = {broadBandTemplate.get("addressCode"), broadBandTemplate.get("shareSerialNumber"), "1",
                "", inMap.get("serialNumber"), inMap.get("district"), getSomeProduct(product, "pointExchId"),""};
        Map oldCust = ((List<Map>) inMap.get("custInfo")).get(0);
        Map oldUser = ((List<Map>) inMap.get("userInfo")).get(0);
        Map oldAcct = ((List<Map>) inMap.get("acctInfo")).get(0);
        // 修复迁转后账号密码没有被继承 by cuij
        String acctNbr = "";
        String acctPasswd = "";
        Map mainProdInfo = (Map) oldUser.get("mainProdInfo");
        Map prodprptyInfo = (Map) mainProdInfo.get("prodprptyInfos");
        if (!IsEmptyUtils.isEmpty(prodprptyInfo)) {
            List<Map> prodprptyInfos = (List<Map>) prodprptyInfo.get("prodprptyInfo");
            for (int i = 0; i < prodprptyInfos.size(); i++) {
                if ("ACCT_NBR".equals(prodprptyInfos.get(i).get("prptyId"))) {
                    acctNbr = (String) prodprptyInfos.get(i).get("prptyValue");
                }
                if ("ACCT_PASSWD".equals(prodprptyInfos.get(i).get("prptyId"))) {
                    acctPasswd = (String) prodprptyInfos.get(i).get("prptyValue");
                }
            }
        }
        item.add(lan.createTradeSubItemBroad("ACCT_NBR", acctNbr, itemId));
        item.add(lan.createTradeSubItemBroad("ACCT_PASSWD", acctPasswd, itemId));
        item.addAll(lan.createTradeSubsItemList(keyArray, valueArray, itemId));
        item.add(lan.createTradeSubItemBroad("AREA_EXCH_ID", getSomeProduct(product, "areaExchId"), itemId));
        item.add(lan.createTradeSubItemBroad("MOFFICE_ID", broadBandTemplate.get("exchCode"), itemId));
        item.add(lan.createTradeSubItemBroad("OLD_NET_TYPE_CODE_SF", "30", itemId));
        item.add(lan.createTradeSubItemBroad("COMMUNIT_ID", "-1", itemId));
        item.add(lan.createTradeSubItemBroad("USER_PASSWD", "123456", itemId));
        item.add(lan.createTradeSubItemBroad("OLD_CUST_ID_SF", oldCust.get("custId"), itemId));
        item.add(lan.createTradeSubItemBroad("OLD_USER_ID_SF", oldUser.get("userId"), itemId));
        item.add(lan.createTradeSubItemBroad("DIRECFLAG", "0", itemId));
        if ("1".equals(inMap.get("isSerial"))) {// 共线号码时候才下发 20181205,不确定inMap里面是否有，但是以前有人用了，先这样写
            item.add(lan.createTradeSubItemBroad("isBssGroup", "X3", itemId));
            item.add(lan.createTradeSubItemBroad("COLLINEAR_TYPE", "X3", itemId));
        }
        item.add(lan.createTradeSubItemBroad("ACCESS_TYPE", broadBandTemplate.get("accessMode"), itemId));
        item.add(lan.createTradeSubItemBroad("CB_ACCESS_TYPE", changeAccessCode(inMap), itemId));
        item.add(lan.createTradeSubItemBroad("WorkId", "0", itemId));
        item.add(lan.createTradeSubItemBroad("COMMPANY_NBR", "", itemId));
        Object installAddress = broadBandTemplate.get("installAddress");
        item.add(lan.createTradeSubItemBroad("DETAIL_INSTALL_ADDRESS", installAddress, itemId));
        item.add(lan.createTradeSubItemBroad("TRANS_TYPE_B", "1", itemId));
        item.add(lan.createTradeSubItemBroad("TOWN_FLAG", "C", itemId));
        item.add(lan.createTradeSubItemBroad("IS_TRANSMIT", "1", itemId));
        item.add(lan.createTradeSubItemBroad("LOCAL_NET_CODE", ChangeCodeUtils.changeEparchyUnStatic(inMap), itemId));// 改成地市编码
        item.add(lan.createTradeSubItemBroad("INSTALL_ADDRESS", installAddress, itemId));
        item.add(lan.createTradeSubItemBroad("BSS_GROUP_ID", "1210000506264058", itemId));
        item.add(lan.createTradeSubItemBroad("PROJECGT_ID", "", itemId));
        item.add(lan.createTradeSubItemBroad("COMMUNIT_NAME", "-1", itemId));
        item.add(lan.createTradeSubItemBroad("INIT_PASSWD", "1", itemId));
        item.add(lan.createTradeSubItemBroad("OLD_ACCT_ID_SF", oldAcct.get("acctId"), itemId));
        if ("1".equals(inMap.get("broadBandType"))) {
            item.add(lan.createTradeSubItemBroad("SPEED", inMap.get("speedLevel"), itemId));
        }
        // 增加属性传值ATTR_TYPE_CODE和TRANSE_ID add by wangrj3
        item.add(lan.createTradeSubItemBroad("ATTR_TYPE_CODE", "0", itemId));
        item.add(lan.createTradeSubItemBroad("transeId", "2", itemId));// 2-IOM报竣月末迁转

        // 宽带开户增加服务渠道和网格信息，用于省份佣金分摊 by maly 20170419 迁转
        item.add(lan.createTradeSubItemBroad("CHNL_CODE", broadBandTemplate.get("chnlCode"), itemId));
        item.add(lan.createTradeSubItemBroad("GRID_NAME", broadBandTemplate.get("gridName"), itemId));
        item.add(lan.createTradeSubItemBroad("GRID_CODE", broadBandTemplate.get("gridCode"), itemId));
        item.add(lan.createTradeSubItemBroad("CHNL_NAME", broadBandTemplate.get("chnlName"), itemId));

        //当宽带为迁转类型时，入网方式没有下发给cbss， 需要增加入网方式的下发，默认为1
        item.add(lan.createTradeSubItemBroad("CONNECTNETMODE",
                IsEmptyUtils.isEmpty(broadBandTemplate.get("netMode")) ? "1"
                        : broadBandTemplate.get("netMode"), itemId));

        // RTJ2018012500067-CBSS支持天津行销APP受理的迁转业务下发ONU相关字段的需求
        preBroadTransferForTradeSubItem(broadBandTemplate, item, itemId, inMap, lan);

        tradeItem.put("item", item);
        return tradeItem;
    }

    private Map preTradeSubItemOpen(Map inMap) {
        Map tradeSubItem = new HashMap();
        // tradeSubItem
        List<Map> tradeSubItemList = new ArrayList<Map>();
        // 固话独有item
        String itemId = inMap.get("userId").toString();
        LanUtils lan = new LanUtils();
        String startDate = GetDateUtils.getDate();
        ChangeCodeUtils ChangeCodeUtils = new ChangeCodeUtils();
        String endDate = MagicNumber.CBSS_DEFAULT_EXPIRE_TIME;
        tradeSubItemList.add(lan.createTradeSubItemC(itemId, "adEnd", "", startDate, endDate));
        Map broadBandTemplate = (Map) inMap.get("broadBandTemplate");
        List<Map> product = (List<Map>) broadBandTemplate.get("broadBandProduct");
        tradeSubItemList.add(lan.createTradeSubItem("PREPAY_TAG", "0", itemId));
        tradeSubItemList.add(lan.createTradeSubItem("WOPAY_MONEY", "", itemId));
        tradeSubItemList.add(lan.createTradeSubItem("TIME_LIMIT_ID", "2", itemId));
        tradeSubItemList.add(lan.createTradeSubItem("CONNECTNETMODE", getSomeProduct(product, "netMode", "1"), itemId));
        tradeSubItemList.add(lan.createTradeSubItem("ISWAIL", MixBroadBandAndLandLineToolUtils.changeAcceptModeCode(
                (String) broadBandTemplate.get("acceptMode")), itemId));
        // tradeSubItemList.add(lan.createTradeSubItem("SPEED",inMap.get("speedLevel"),itemId));
        Object machineInfoObject = getSomeProduct(product, "machineInfo");
        if (null != machineInfoObject) {
            Map machineInfo = ((List<Map>) machineInfoObject).get(0);
            // terminalsn等属性要挂到互联网电视那个服务的itemid下。
            String iptvItemId = (String) inMap.get("iptvItemId");
            String interTvItemId = (String) inMap.get("interTvItemId");
            if ((!StringUtils.isEmpty(interTvItemId) || !StringUtils.isEmpty(iptvItemId)) && "2".equals(machineInfo
                    .get("machineAttr"))) {
                String iptvOrInterItemId = "".equals(iptvItemId) ? interTvItemId : iptvItemId;
                tradeSubItemList.add(lan.createTradeSubItem2NoDate("terminalsn", machineInfo.get("machineBrandCode"),
                        iptvOrInterItemId));
                tradeSubItemList.add(lan.createTradeSubItem2NoDate("terminalmac", machineInfo.get("machineMac"),
                        iptvOrInterItemId));
                tradeSubItemList.add(lan.createTradeSubItem2NoDate("terminalsrcmode",
                        MixBroadBandAndLandLineToolUtils.changeTerminalCode((String) machineInfo.get("machineType")),
                        iptvOrInterItemId));
                tradeSubItemList.add(lan.createTradeSubItem2NoDate("terminalmodel", machineInfo.get
                        ("machineModelCode"), iptvOrInterItemId));
                tradeSubItemList.add(lan.createTradeSubItem2NoDate("terminalbrand", machineInfo.get
                        ("machineBrandCode"), iptvOrInterItemId));
                tradeSubItemList.add(lan.createTradeSubItem2NoDate("terminaltype1", ChangeCodeUtils
                        .changeMachineProvideToCB(machineInfo.get("machineProvide")), iptvOrInterItemId));// zzc
                // 20170309 下发CB的终端提供方式转换
            }
            else {
                tradeSubItemList.add(lan.createTradeSubItem("TERMINAL_SN", machineInfo.get("machineBrandCode"),
                        itemId));
                tradeSubItemList.add(lan.createTradeSubItem("TERMINAL_MAC", machineInfo.get("machineMac"), itemId));
                tradeSubItemList.add(lan.createTradeSubItem("TERMINALSRC_MODE", MixBroadBandAndLandLineToolUtils
                        .changeTerminalCode((String) machineInfo.get("machineType")), itemId));
                tradeSubItemList.add(lan.createTradeSubItem("TERMINAL_MODEL", machineInfo.get("machineModelCode"),
                        itemId));
                tradeSubItemList.add(lan.createTradeSubItem("TERMINAL_BRAND", machineInfo.get("machineBrandCode"),
                        itemId));
                tradeSubItemList.add(lan.createTradeSubItem("TERMINAL_TYPE", ChangeCodeUtils
                        .changeMachineProvideToCB(machineInfo.get("machineProvide")), itemId));// zzc 20170309
                // 下发CB的终端提供方式转换

            }
        }
        if ("2".equals(inMap.get("broadBandType"))) {
            tradeSubItemList.add(lan.createTradeSubItemE("serialNumber", inMap.get("serialNumber"), itemId));
            tradeSubItemList.add(lan.createTradeSubItemE("ISFLAG114", "0", itemId));
            tradeSubItemList.add(lan.createTradeSubItemE("DIRECFLAG", "0", itemId));
            tradeSubItemList.add(lan.createTradeSubItemE("PROJECGT_ID", "", itemId));
        }
        else {
            tradeSubItemList.add(lan.createTradeSubItem("ISWOPAY", "0", itemId));
            tradeSubItemList.add(lan.createTradeSubItem("KDLX_2061", "N", itemId));
            tradeSubItemList.add(lan.createTradeSubItem("USER_CALLING_AREA", inMap.get("district"), itemId));
            // 宽带新装,校验固网账号必传 by wangmc 20180605
            if (IsEmptyUtils.isEmpty(broadBandTemplate.get("accountCode"))) {
                throw new EcAopServerBizException("9999", "宽带新装业务,固网账号字段必传!");
            }
            tradeSubItemList.add(lan.createTradeSubItem("ACCT_NBR", broadBandTemplate.get("accountCode"), itemId));
            tradeSubItemList.add(lan.createTradeSubItem("POSITION_XY", "", itemId));
            tradeSubItemList.add(lan.createTradeSubItem("SPEED", inMap.get("speedLevel"), itemId));
            tradeSubItemList.add(lan.createTradeSubItem("HZGS_0000", "", itemId));
            tradeSubItemList.add(lan.createTradeSubItem("EXPECT_RATE", "", itemId));
            // CHANGE_3 北分沃易售系统,处理宽带密码,由省份传入 by wangmc 20180630
            if (inMap.containsKey("BFAppCode") && !IsEmptyUtils.isEmpty(broadBandTemplate.get("userPasswd"))) {
                tradeSubItemList
                        .add(lan.createTradeSubItem("ACCT_PASSWD", broadBandTemplate.get("userPasswd"), itemId));
                tradeSubItemList.add(lan.createTradeSubItemE("serialNumber", inMap.get("serialNumber"), itemId));
            }
            else {
                tradeSubItemList.add(lan.createTradeSubItem("ACCT_PASSWD", "123456", itemId));
            }
            Map broadBandCustInfo = (Map) broadBandTemplate.get("broadBandCustInfo");
            tradeSubItemList.add(lan.createTradeSubItemE("LINK_NAME", broadBandCustInfo.get("contactPerson"), itemId));
            tradeSubItemList.add(lan.createTradeSubItemE("LINK_PHONE", broadBandCustInfo.get("contactPhone"), itemId));
            tradeSubItemList.add(lan.createTradeSubItemE("OTHERCONTACT", "", itemId));
        }
        Object installAddress = broadBandTemplate.get("installAddress");
        String shareSerialNumber = (String) broadBandTemplate.get("shareSerialNumber");
        tradeSubItemList.add(lan.createTradeSubItem("ADDRESS_ID", broadBandTemplate.get("addressCode"), itemId));//
        // 标准地址编码
        if (StringUtils.isNotEmpty(shareSerialNumber) && "1".equals(broadBandTemplate.get("broadBandType"))) {
            tradeSubItemList.add(lan.createTradeSubItem("SHARE_NBR", shareSerialNumber, itemId));
        }
        else {
            tradeSubItemList.add(lan.createTradeSubItem("SHARE_NBR", "", itemId));
        }
        tradeSubItemList.add(lan.createTradeSubItem("MODULE_EXCH_ID", inMap.get("moduleExchId"), itemId));
        String serviceArea= (String) getSomeProduct(product, "serviceArea", "1");
        if(!"1".equals(serviceArea)){
            tradeSubItemList.add(lan.createTradeSubItem("AREA_CODE", serviceArea, itemId));
        }else{
            tradeSubItemList.add(lan.createTradeSubItem("AREA_CODE", inMap.get("district"), itemId));
        }
        tradeSubItemList.add(lan.createTradeSubItem("ACCESS_TYPE", broadBandTemplate.get("accessMode"), itemId));
        tradeSubItemList.add(lan.createTradeSubItem("USETYPE", MixBroadBandAndLandLineToolUtils
                .changeUserPropertyCode((String) broadBandTemplate.get("userProperty")), itemId));
        tradeSubItemList.add(lan.createTradeSubItem("POINT_EXCH_ID", getSomeProduct(product, "pointExchId"), itemId));
        tradeSubItemList.add(lan.createTradeSubItem("USER_PASSWD", inMap.get("passWd"), itemId));
        tradeSubItemList.add(lan.createTradeSubItem("USER_TYPE_CODE", broadBandTemplate.get("userType"), itemId));
        // CHANGE_7 SWITCH_EXCH_ID 下发exchCode字段 by wangmc 20180706
        if ("13".equals(inMap.get("province")) || inMap.containsKey("BFAppCode")) {
            tradeSubItemList.add(lan.createTradeSubItem("SWITCH_EXCH_ID", broadBandTemplate.get("exchCode"), itemId));
        }
        else {
            tradeSubItemList.add(lan.createTradeSubItem("SWITCH_EXCH_ID", "", itemId));
        }
        tradeSubItemList.add(lan.createTradeSubItem("AREA_EXCH_ID", getSomeProduct(product, "areaExchId"), itemId));
        tradeSubItemList.add(lan.createTradeSubItem("MOFFICE_ID", broadBandTemplate.get("exchCode"), itemId));
        tradeSubItemList.add(lan.createTradeSubItem("COMMUNIT_ID", "0", itemId));
        tradeSubItemList.add(lan.createTradeSubItem("CB_ACCESS_TYPE", changeAccessCode(inMap), itemId));
        tradeSubItemList.add(lan.createTradeSubItem("COMMPANY_NBR", "", itemId));
        tradeSubItemList.add(lan.createTradeSubItem("DETAIL_INSTALL_ADDRESS", installAddress, itemId));
        tradeSubItemList.add(lan.createTradeSubItem("TOWN_FLAG", MixBroadBandAndLandLineToolUtils.changeCityMarkCode(
                (String) broadBandTemplate.get("cityMark")), itemId));
        tradeSubItemList.add(lan.createTradeSubItem("LOCAL_NET_CODE", ChangeCodeUtils.changeEparchyUnStatic(inMap),
                itemId));
        tradeSubItemList.add(lan.createTradeSubItem("INSTALL_ADDRESS", installAddress, itemId));
        tradeSubItemList.add(lan.createTradeSubItem("COMMUNIT_NAME", "", itemId));
        tradeSubItemList.add(lan.createTradeSubItem("INIT_PASSWD", "0", itemId));
        if ("1".equals(inMap.get("isSerial"))) {// 共线号码时候才下发 20181205,不确定inMap里面是否有，但是以前有人用了，先这样写
            tradeSubItemList.add(lan.createTradeSubItem("COLLINEAR_TYPE", "X3", itemId));
        }

        // 宽带开户增加服务渠道和网格信息，用于省份佣金分摊 by maly 20170419 新装
        tradeSubItemList.add(lan.createTradeSubItem("CHNL_CODE", broadBandTemplate.get("chnlCode"), itemId));
        tradeSubItemList.add(lan.createTradeSubItem("GRID_NAME", broadBandTemplate.get("gridName"), itemId));
        tradeSubItemList.add(lan.createTradeSubItem("GRID_CODE", broadBandTemplate.get("gridCode"), itemId));
        tradeSubItemList.add(lan.createTradeSubItem("CHNL_NAME", broadBandTemplate.get("chnlName"), itemId));

        tradeSubItemList = TradeManagerUtils.dealRepeat(tradeSubItemList);
        tradeSubItem.put("item", tradeSubItemList);
        return tradeSubItem;
    }

    private Map preTradeSubItmChange(Map inMap) {
        Map TradeSubItem = new HashMap();
        String userId = (String) inMap.get("userId");
        //获取联系人信息 change by wangmc 20181115
        Map broadBandTemplate = (Map) inMap.get("broadBandTemplate");
        Map broadBandCustInfo = (Map) broadBandTemplate.get("broadBandCustInfo");
        LanUtils lan = new LanUtils();
        List<Map> items = new ArrayList<Map>();
        items.add(lan.createTradeSubItem("DIRECTION", "1", userId));
        items.add(lan.createTradeSubItem("CABLEACCESSTYPE", "2", userId));
        items.add(lan.createTradeSubItem("RESOURCEPIECEID", "", userId));
        items.add(lan.createTradeSubItem("BILLPRIVILEDGE", "0", userId));
        items.add(lan.createTradeSubItem("ISREVERSEPOLE", "0", userId));
        items.add(lan.createTradeSubItem("SHARE_NBR", "", userId));
        items.add(lan.createTradeSubItem("PTDH_ORIGINALCOMPANYKEY", "", userId));
        items.add(lan.createTradeSubItem("PTDH_SHOWNAME", "", userId));
        items.add(lan.createTradeSubItem("PTDH_DIRECTNUMBER", "", userId));
        items.add(lan.createTradeSubItem("RENTDAYS", "", userId));
        items.add(lan.createTradeSubItem("SERVICEUSERTYPEKEY", "110001", userId));
        items.add(lan.createTradeSubItem("PTDH_STOCKCODE", "", userId));
        items.add(lan.createTradeSubItem("ISFLAG114", "1", userId));
        items.add(lan.createTradeSubItem("CHARGE_TYPE", "1", userId));
        items.add(lan.createTradeSubItem("IS_CHOOSE_SN", "0", userId));
        items.add(lan.createTradeSubItem("SERVICEUSAGEKEY", "110011", userId));
        items.add(lan.createTradeSubItem("DIRECFLAG", "0", userId));
        items.add(lan.createTradeSubItem("PHONECATEGORYKEY", "-1", userId));
        items.add(lan.createTradeSubItem("PHONEMODELKEY", "-1", userId));
        items.add(lan.createTradeSubItem("PTDH_ORIGINALCOMPANYNB", "", userId));
        items.add(lan.createTradeSubItem("NAME", "", userId));
        items.add(lan.createTradeSubItem("DEVICESOURCEDESC", "1", userId));
        items.add(lan.createTradeSubItem("SHOW_ORDER", "", userId));
        items.add(lan.createTradeSubItem("COUNTRYFLAG", "3010", userId));
        items.add(lan.createTradeSubItem("SECRECYTYPE", "", userId));
        items.add(lan.createTradeSubItem("PTDH_ISNETVIEWPHONE", "", userId));
        //items.add(lan.createTradeSubItem("LINK_PHONE", inMap.get("contactPhone"), userId));
        items.add(lan.createTradeSubItem("LINK_PHONE", broadBandCustInfo.get("contactPhone"), userId));
        items.add(lan.createTradeSubItem("RELAYBRANCHNUMBER", "", userId));
        items.add(lan.createTradeSubItem("PTDH_BOOKCODE2", "", userId));
        items.add(lan.createTradeSubItem("PTDH_BOOKCODE1", "", userId));
        items.add(lan.createTradeSubItem("ENSNUM", "", userId));
        items.add(lan.createTradeSubItem("PTDH_HOMELINEPROJECT", "", userId));
        items.add(lan.createTradeSubItem("BILLINGMODE", "999999", userId));
        items.add(lan.createTradeSubItem("SHARE_PHONE", "", userId));
        items.add(lan.createTradeSubItem("CUSTOMER_GROUP", "0", userId));
        //items.add(lan.createTradeSubItem("LINK_NAME", inMap.get("contactPerson"), userId));
        items.add(lan.createTradeSubItem("LINK_NAME", broadBandCustInfo.get("contactPerson"), userId));
        items.add(lan.createTradeSubItem("PHONESOURCEKEY", "3", userId));
        items.add(lan.createTradeSubItem("PTDH_ISWAITMAINTAIN", "", userId));
        items.add(lan.createTradeSubItem("PHONETYPEKEY", "1", userId));
        items.add(lan.createTradeSubItem("PTDH_ISPRIMARYINDEX", "0", userId));
        if ("1".equals(inMap.get("broadBandType")))
        {
            items.add(lan.createTradeSubItem("SPEED", inMap.get("speedLevel"), userId));
        }

        // 宽带开户增加服务渠道和网格信息，用于省份佣金分摊 by maly 20170419 纳入
        items.add(lan.createTradeSubItem("CHNL_CODE", broadBandTemplate.get("chnlCode"), userId));
        items.add(lan.createTradeSubItem("GRID_NAME", broadBandTemplate.get("gridName"), userId));
        items.add(lan.createTradeSubItem("GRID_CODE", broadBandTemplate.get("gridCode"), userId));
        items.add(lan.createTradeSubItem("CHNL_NAME", broadBandTemplate.get("chnlName"), userId));

        TradeSubItem.put("item", items);
        return TradeSubItem;
    }

    /**
     * 
     *CBSS支持天津行销APP受理的迁转业务下发ONU相关字段的需求
     *TERMINAL_SN,TERMINAL_MAC,TERMINAL_TYPE,TERMINAL_MODEL,TERMINAL_BRAND
     *这几个字段当终端信息不传时和终端属性为2时，走老流程；当终端属性为1时，如果终端信息传了就下发，没有就走老流程
     * @param broadBandTemplate
     * @param tradeSubItemList
     * @param itemId
     * @param inMap
     * @param lan
     */
    private void preBroadTransferForTradeSubItem(Map broadBandTemplate, List<Map> tradeSubItemList, String itemId,
            Map inMap, LanUtils lan) {
        List<Map> product = (List<Map>) broadBandTemplate.get("broadBandProduct");
        Object machineInfoObject = getSomeProduct(product, "machineInfo");
        if ("tjpr".equals(inMap.get("isAppCode")) && null != machineInfoObject) {
            inMap.remove("isAppCode");
            Map machineInfo = ((List<Map>) machineInfoObject).get(0);
            // terminalsn等属性要挂到互联网电视那个服务的itemid下。
            String iptvItemId = (String) inMap.get("iptvItemId");
            String interTvItemId = (String) inMap.get("interTvItemId");
            if ((!StringUtils.isEmpty(interTvItemId) || !StringUtils.isEmpty(iptvItemId)) && "2".equals(machineInfo
                    .get("machineAttr"))) {
                String iptvOrInterItemId = "".equals(iptvItemId) ? interTvItemId : iptvItemId;
                tradeSubItemList.add(lan.createTradeSubItem2NoDate("terminalsn", machineInfo.get("machineBrandCode"),
                        iptvOrInterItemId));
                tradeSubItemList.add(lan.createTradeSubItem2NoDate("terminalmac", machineInfo.get("machineMac"),
                        iptvOrInterItemId));
                tradeSubItemList.add(lan.createTradeSubItem2NoDate("terminalsrcmode",
                        MixBroadBandAndLandLineToolUtils.changeTerminalCode((String) machineInfo.get("machineType")),
                        iptvOrInterItemId));
                tradeSubItemList.add(lan.createTradeSubItem2NoDate("terminalmodel", machineInfo.get
                        ("machineModelCode"), iptvOrInterItemId));
                tradeSubItemList.add(lan.createTradeSubItem2NoDate("terminalbrand", machineInfo.get
                        ("machineBrandCode"), iptvOrInterItemId));
                tradeSubItemList.add(lan.createTradeSubItem2NoDate("terminaltype1", ChangeCodeUtils
                        .changeMachineProvideToCB(machineInfo.get("machineProvide")), iptvOrInterItemId));
                // 默认老流程
                preDefaultForMachineInfo(tradeSubItemList, itemId, lan);

            }
            else {
                String sysDate = GetDateUtils.getSysdateFormat();
                if (!IsEmptyUtils.isEmpty(machineInfo.get("machineBrandCode"))) {
                    tradeSubItemList.add(lan.createTradeSubItemBroad1("TERMINAL_SN",
                            machineInfo.get("machineBrandCode"), itemId, sysDate));
                }
                else {
                    tradeSubItemList.add(lan.createTradeSubItemBroad1("TERMINAL_SN", "", itemId, sysDate));
                }
                if (!IsEmptyUtils.isEmpty(machineInfo.get("machineMac"))) {
                    tradeSubItemList.add(lan.createTradeSubItemBroad1("TERMINAL_MAC", machineInfo.get("machineMac"),
                            itemId, sysDate));
                }
                else {
                    tradeSubItemList.add(lan.createTradeSubItemBroad1("TERMINAL_MAC", "0", itemId, sysDate));
                }
                if (!IsEmptyUtils.isEmpty(machineInfo.get("machineProvide"))) {
                    tradeSubItemList.add(lan.createTradeSubItemBroad1("TERMINAL_TYPE", ChangeCodeUtils
                            .changeMachineProvideToCB(machineInfo.get("machineProvide")), itemId, sysDate));
                }
                else {
                    tradeSubItemList.add(lan.createTradeSubItemBroad1("TERMINAL_TYPE", "0", itemId, sysDate));
                }
                if (!IsEmptyUtils.isEmpty(machineInfo.get("machineModelCode"))) {
                    tradeSubItemList.add(lan.createTradeSubItemBroad1("TERMINAL_MODEL",
                            machineInfo.get("machineModelCode"), itemId, sysDate));
                }
                else {
                    tradeSubItemList.add(lan.createTradeSubItemBroad1("TERMINAL_MODEL", "", itemId, sysDate));
                }
                if (!IsEmptyUtils.isEmpty(machineInfo.get("machineBrandCode"))) {
                    tradeSubItemList.add(lan.createTradeSubItemBroad1("TERMINAL_BRAND",
                            machineInfo.get("machineBrandCode"), itemId, sysDate));
                }
                else {
                    tradeSubItemList.add(lan.createTradeSubItemBroad1("TERMINAL_BRAND", "", itemId, sysDate));
                }
                tradeSubItemList.add(lan.createTradeSubItemBroad1("TERMINALSRC_MODE", MixBroadBandAndLandLineToolUtils
                        .changeTerminalCode((String) machineInfo.get("machineType")), itemId, sysDate));
            }
        }
        else {
            // 终端信息不传时，默认老流程
            preDefaultForMachineInfo(tradeSubItemList, itemId, lan);
        }
    }

    private void preDefaultForMachineInfo(List<Map> tradeSubItemList, String itemId, LanUtils lan) {
        tradeSubItemList.add(lan.createTradeSubItem("TERMINAL_SN", "", itemId));
        tradeSubItemList.add(lan.createTradeSubItem("TERMINAL_MAC", "0", itemId));
        tradeSubItemList.add(lan.createTradeSubItem("TERMINAL_TYPE", "0", itemId));
        tradeSubItemList.add(lan.createTradeSubItem("TERMINAL_MODEL", "", itemId));
        tradeSubItemList.add(lan.createTradeSubItem("TERMINAL_BRAND", "", itemId));

    }

    private Map getBoardDiscntArrt(List<Map> product) {
        for (Map prod : product) {
            List<Map> boradDiscntInfo = (List<Map>) prod.get("boradDiscntInfo");
            if (null == boradDiscntInfo || 0 == boradDiscntInfo.size()) {
                continue;
            }
            for (Map discnt : boradDiscntInfo) {
                if (null != discnt.get("boradDiscntAttr")) {
                    return (Map) discnt.get("boradDiscntAttr");
                }
            }
        }
        return new HashMap();
    }

    private Object getSomeProduct(List<Map> product, String key) {
        return getSomeProduct(product, key, null);
    }

    private Object getSomeProduct(List<Map> product, String key, Object value) {
        for (Map prod : product) {
            if (null != prod.get(key)) {
                return prod.get(key);
            }
        }
        return value;
    }

    /**
     * 接入方式转换
     */
    private Object changeAccessCode(Map inMap) {
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        Map in = new HashMap();
        Map broadBandTemplate = (Map) inMap.get("broadBandTemplate");
        in.put("ACCESS_TYPE", broadBandTemplate.get("accessMode"));
        in.put("EPARCHY_CODE", inMap.get("city"));
        in.put("PROVINCE_CODE", inMap.get("province"));
        String broadBandType = (String) broadBandTemplate.get("broadBandType");
        if (StringUtils.isNotEmpty(broadBandType)) {
            if ("1".equals(broadBandType)) {
                // 宽带
                in.put("NET_TYPE_CODE", "40");
            }
            else if ("2".equals(broadBandType)) {
                // 固话
                in.put("NET_TYPE_CODE", "30");
            }
        }
        // 如果传了cbss接入类型，去校验此接入类型是否存在，如果有1到多条，则下发传进来的cbss接入方式 2016-06-01 lixl
        Object cbAccessType = broadBandTemplate.get("cbssAccessMode");
        if (null != cbAccessType && !"".equals(cbAccessType)) {
            in.put("CBSS_ACCESS_TYPE", broadBandTemplate.get("cbssAccessMode"));
            List isExist = dao.queryCbAccessTypeIsExist(in);
            if (null == isExist || isExist.isEmpty()) {
                throw new EcAopServerBizException("9999", "没有此CBSS接入方式编码！");
            }
            return broadBandTemplate.get("cbssAccessMode");
        }
        List resultSet = dao.queryCbAccessTypeByPro(in);
        // String cbAccessType = "";
        try {
            if (resultSet == null || 0 == resultSet.size()) {
                throw new EcAopServerBizException("9999", "没有此接入方式编码");
            }
            cbAccessType = resultSet.get(0);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "接入方式转换失败:" + e.getMessage());
        }
        return cbAccessType;
    }

    /**
     * 接入方式转换
     * PROVINCE_CODE、NET_TYPE_CODE、ACCESS_TYPE、CB_ACCESS_TYPE请传进来
     */
    public Map checkAccessCode(Map inMap) throws Exception {
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        String accessMode = (String) inMap.get("ACCESS_TYPE");
        String cbssAccessMode = (String) inMap.get("CB_ACCESS_TYPE");
        Map accesMap = new HashMap();
        if (StringUtils.isEmpty(accessMode) && StringUtils.isEmpty(cbssAccessMode)) {
            throw new EcAopServerBizException("9999", "请添加接入方式编码");
        }
        else if (StringUtils.isNotEmpty(accessMode) && StringUtils.isNotEmpty(cbssAccessMode)) {
            List resultSet = dao.queryCbAccessTypeByPro(inMap);
            if (resultSet != null && resultSet.size() > 0) {
                String cbAccessType = (String) resultSet.get(0);
                if (!cbssAccessMode.equals(cbAccessType)) {
                    throw new EcAopServerBizException("9999", "accessMode与cbssAccessMode对应关系错误，请检查");
                }
            }
            else {
                throw new EcAopServerBizException("9999", "无此accessMode对应接入方式编码，请检查");
            }
        }
        else if (StringUtils.isNotEmpty(accessMode)) {
            List resultSet = dao.queryCbAccessTypeByPro(inMap);
            if (resultSet != null && resultSet.size() > 0) {
                cbssAccessMode = (String) resultSet.get(0);
            }
            else {
                throw new EcAopServerBizException("9999", "无此accessMode对应cbssAccessMode接入方式编码，请检查");
            }
        }
        else if (StringUtils.isNotEmpty(cbssAccessMode)) {
            List resultSet = dao.queryCbAccessType(inMap);
            if (resultSet != null && resultSet.size() > 0) {
                if (resultSet.size() > 1) {
                    accessMode = "B00";
                }
                else {
                    accessMode = (String) resultSet.get(0);
                }
            }
            else {
                throw new EcAopServerBizException("9999", "无此cbssAccessMode对应accessMode接入方式编码，请检查");
            }
        }
        accesMap.put("accessMode", accessMode);
        accesMap.put("cbssAccessMode", cbssAccessMode);
        return accesMap;
    }

    private Map preTradeCustomer(Map inMap) {
        Map tradeCustomer = new HashMap();
        Map item = new HashMap();
        Map broadMap = (Map) inMap.get("broadBandTemplate");
        Map custInfo = (Map) broadMap.get("broadBandCustInfo");
        item.put("sustId", inMap.get("custId"));
        item.put("custName", custInfo.get("customerName"));
        item.put("custType", "0");
        item.put("custPasswd", custInfo.get("custPwd"));
        item.put("developDepartId", custInfo.get("developDepartId"));
        item.put("custState", "0");
        item.put("psptTypeCode", CertTypeChangeUtils.certTypeMall2Cbss((String) custInfo.get("certType")));
        item.put("psptId", custInfo.get("certNum"));
        item.put("openLimit", "0");
        item.put("eparchyCode", custInfo.get("eparchyCode"));
        item.put("cityCode", null == inMap.get("cityCodeNew") ? inMap.get("cityCodeNew") : inMap.get("district"));
        item.put("inDate", GetDateUtils.getDate());
        item.put("creditValue", "0");
        item.put("removeTag", "0");
        item.put("xDatatype", "NULL");
        tradeCustomer.put("item", item);
        return tradeCustomer;
    }

    private Map preTradePerson(Map inMap) {
        Map tradeCustomer = new HashMap();
        Map item = new HashMap();
        Map broadMap = (Map) inMap.get("broadBandTemplate");
        Map custInfo = (Map) broadMap.get("broadBandCustInfo");
        item.put("custId", inMap.get("custId"));
        item.put("custName", custInfo.get("customerName"));
        // zzc 固网新装，纳入，签转的psptTypeCode转为CB的 20170310
        item.put("psptTypeCode", CertTypeChangeUtils.certTypeMall2Cbss((String) custInfo.get("certType")));
        // item.put("psptTypeCode", custInfo.get("certType"));zzc 3
        item.put("psptId", custInfo.get("certNum"));
        item.put("psptEndDate", custInfo.get("certExpireDate"));
        item.put("postCode", "");
        item.put("psptAddr", custInfo.get("certAdress"));
        item.put("postAddress", custInfo.get("certAdress"));
        item.put("phone", custInfo.get("phone"));
        item.put("homeAddress", "certAdress");
        item.put("email", "");
        item.put("sex", custInfo.get("sex"));
        item.put("contact", custInfo.get("contactPerson"));
        item.put("contactPhone", custInfo.get("contactPhone"));
        item.put("birthday", "");
        item.put("job", "");
        item.put("workName", "");
        item.put("marriage", "");
        item.put("eparchyCode", custInfo.get("eparchyCode"));
        item.put("cityCode", null == inMap.get("cityCodeNew") ? inMap.get("cityCodeNew") : inMap.get("district"));
        item.put("removeTag", "0");
        item.put("xDatatype", "NULL");
        tradeCustomer.put("item", item);
        return tradeCustomer;
    }

    /**
     * 根据COMMODITY_TYPE_CODE转NET_TYPE_CODE
     * 默认是固话
     * 宽带 1：固话 2
     * 0040 宽带 0030 固话
     * 
     * @param commodityType
     * @return
     */
    public Object CommodityType2NetTypeCode(Object commodityType) {
        return "1".equals(commodityType) ? "0040" : "0030";
    }

    public void dealException(Exchange exchange, Exception e, List<Map> numberInbfo) {
        Message out = new DefaultMessage();
        out.setBody(dealExceptionReturn(e.getMessage(), numberInbfo));
        exchange.setProperty(Exchange.HTTP_STATUSCODE, 560);
        exchange.setOut(out);
    }

    public void dealException(Exchange exchange, EcAopServerBizException e, List<Map> numberInbfo) {
        Message out = new DefaultMessage();
        exchange.setProperty(Exchange.HTTP_STATUSCODE, 560);
        out.setBody(dealExceptionReturn(e.getCode(), e.getDetail(), numberInbfo));
        exchange.setOut(out);
    }

    private Map dealExceptionReturn(Object code, Object detail, List<Map> numberInbfo) {
        Map retMap = new HashMap();
        retMap.put("code", code);
        retMap.put("detail", detail);
        retMap.put("numberInfo", numberInbfo);
        return retMap;
    }

    private Map dealExceptionReturn(Object detail, List<Map> numberInbfo) {
        return dealExceptionReturn("9999", detail, numberInbfo);
    }
}
