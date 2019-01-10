package com.ailk.ecaop.biz.sub.lan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.product.ChangeProduct4GReplaceN6Processor;
import com.ailk.ecaop.biz.product.DealNewCbssProduct;
import com.ailk.ecaop.biz.sub.gdjk.OpenApply4GProcessor;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.GetThreePartInfoUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;

@EcRocTag("CBFixProductChg")
public class CBFixProductChgProcessor extends BaseAopProcessor {

    private DealNewCbssProduct n25Dao = new DealNewCbssProduct();
    static String[] COPYARRAY = { "operatorId", "province", "city", "district", "channelId", "channelType",
            "serialNumber", "areaCode" };

    @Override
    public void process(Exchange exchange) throws Exception {
        // 保存请求msg信息
        Map inputInfo = exchange.getIn().getBody(Map.class);
        String methodCode = exchange.getMethodCode();

        boolean isString = inputInfo.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) inputInfo.get("msg"));
        }
        else {
            msg = (Map) inputInfo.get("msg");
        }
        Map tempMap = new HashMap();
        MapUtils.arrayPut(tempMap, msg, COPYARRAY);
        Exchange exchangeThreePart = ExchangeUtils.ofCopy(exchange, tempMap);
        // 生成CB序列
        String tradeId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_trade_id");
        String itemId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_item_id");
        // 地市转化
        msg.put("city", ChangeCodeUtils.changeEparchy(msg));
        // 获取请求产品信息
        List<Map> newUserInfoList = (List<Map>) msg.get("newUserInfo");
        List<Map> productInfoList = new ArrayList<Map>();
        List<Map> boradDiscntInfoList = new ArrayList<Map>();
        if (null != newUserInfoList && !newUserInfoList.isEmpty()) {
            productInfoList = (List<Map>) newUserInfoList.get(0).get("productInfo");
            boradDiscntInfoList = (List<Map>) newUserInfoList.get(0).get("boradDiscntInfo");
        }
        // 宽带所需的资费和生效方式
        String delayType = null;
        String delayDiscntId = null;
        String delayDiscntType = null;
        String boradDiscntId = null;
        if (null != boradDiscntInfoList && !boradDiscntInfoList.isEmpty()) {
            boradDiscntId = (String) boradDiscntInfoList.get(0).get("boradDiscntId");
            Map boradDiscntAttr = (Map) boradDiscntInfoList.get(0).get("boradDiscntAttr");
            if (null != boradDiscntAttr && !boradDiscntAttr.isEmpty()) {
                delayType = (String) boradDiscntAttr.get("delayType");
                delayDiscntId = (String) boradDiscntAttr.get("delayDiscntId");
                delayDiscntType = (String) boradDiscntAttr.get("delayDiscntType");
            }
        }
        // 获取订购的主产品和退订的主产品
        String newProductId = null;
        String oldProductId = null;
        for (Map productInfoMap : productInfoList) {
            if ("0".equals(productInfoMap.get("optType")) && "0".equals(productInfoMap.get("productMode"))) {
                newProductId = (String) productInfoMap.get("oldProductId");
            }
            if ("1".equals(productInfoMap.get("optType")) && "0".equals(productInfoMap.get("productMode"))) {
                oldProductId = (String) productInfoMap.get("oldProductId");
            }
        }

        // TODO:预提交准备参数
        Map preDateMap = new HashMap();
        preDateMap = MapUtils.asMap("eparchyCode", msg.get("city"), "boradDiscntId", boradDiscntId, "delayType",
                delayType, "delayDiscntId", delayDiscntId, "delayDiscntType", delayDiscntType, "itemId", itemId,
                "tradeId", tradeId, "methodCode", methodCode, "productInfo", productInfoList, "newProductId",
                newProductId, "oldProductId", oldProductId, "boradDiscntInfo", boradDiscntInfoList, "contactPerson",
                msg.get("contactPerson"), "contactPhone", msg.get("contactPhone"), "provinceCode",
                "00" + msg.get("province"), "netType", msg.get("netType"), "receMessPhone", msg.get("receMessPhone"));
        // 调用三户接口
        exchange.getIn().setBody(inputInfo);
        Map threePartInfoMap = threePartInfo(exchangeThreePart, (String) preDateMap.get("oldProductId"));
        preDateMap.putAll(threePartInfoMap);
        MapUtils.arrayPut(preDateMap, msg, COPYARRAY);
        // 冰淇淋/王卡融合套餐绑定信息
        List<Map> bindInfo = (List) msg.get("bindInfo");
        if (bindInfo != null && bindInfo.size() > 0) {
            for (Map bind : bindInfo) {
                preDateMap.put("bindInfo", bind.get("bindSerialNumber"));
                preDateMap.put("bindType", bind.get("bindType"));
                preDateMap.put("bindSrc", bind.get("bindSrc"));
                preDateMap.put("bindUserId", bind.get("bindUserId"));
            }
        }
        preData4CbPre(preDateMap, exchange.getAppCode(), inputInfo);
        // 调CB统一预提交接口
        Exchange exchange4CB = ExchangeUtils.ofCopy(exchange, preDateMap);
        Map retPreSubMap = Maps.newHashMap();
        try {
            new LanUtils().preData("ecaop.trades.sccc.cancelPre.paramtersmapping", exchange4CB);
            CallEngine.wsCall(exchange4CB, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
            new LanUtils().xml2Json("ecaop.trades.sccc.cancelPre.template", exchange4CB);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用预提交失败！" + e.getMessage());
        }
        // 对预提交返回做处理
        Map PreSubOut = exchange4CB.getOut().getBody(Map.class);
        List<Map> rspInfo = (List<Map>) PreSubOut.get("rspInfo");
        if (null == rspInfo || rspInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "cBSS统一预提交未返回应答信息");
        }
        retPreSubMap.put("provOrderId", rspInfo.get(0).get("provOrderId"));
        // TODO:固话账户名称
        retPreSubMap.put("userName", "");
        Integer totalFee = 0;
        for (Map rspMap : rspInfo) {
            List<Map> provinceOrderInfo = (List<Map>) rspMap.get("provinceOrderInfo");
            if (null == provinceOrderInfo || provinceOrderInfo.isEmpty()) {
                continue;
            }
            for (Map provinceOrder : provinceOrderInfo) {
                totalFee = totalFee + Integer.valueOf(provinceOrder.get("totalFee").toString());
                List<Map> preFeeInfoRsp = (List<Map>) provinceOrder.get("preFeeInfoRsp");
                if (null == preFeeInfoRsp || preFeeInfoRsp.isEmpty()) {
                    continue;
                }
                List<Map> feeList = dealFee(preFeeInfoRsp);
                retPreSubMap.put("feeInfo", feeList);
            }
            retPreSubMap.put("totalFee", totalFee);
        }
        exchange.getOut().setBody(retPreSubMap);
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

    /**
     * cBSS预提交准备参数
     * @param preDateMap
     * @throws Exception
     */
    private void preData4CbPre(Map inputMap, String appCode, Map body) throws Exception {
        inputMap.put("ordersId", inputMap.get("tradeId"));
        inputMap.put("operTypeCode", "0");
        Map base = preMixBase(inputMap, appCode);

        // 判断是否有主产品和附加产品处理
        Map ext = new HashMap();

        // 判断是否有主产品和附加产品处理
        Map subOrMain = preSubOrMainInfo(inputMap);
        // 处理主产品的订购和退订
        if ((Boolean) subOrMain.get("isMAIN")) {
            ext = preMixExt(inputMap);
        }
        // 处理附加产品的订购和退订
        if ((Boolean) subOrMain.get("isSUB")) {
            ext = preSubInfo(inputMap, body, ext);
        }

        // 支持存费送电子券业务需求
        preForTradeSubItemInfo(ext, inputMap);
        inputMap.put("ext", ext);
        inputMap.put("base", base);

    }

    /**
     * 支持存费送电子券业务需求 zhaok
     * @param ext
     * @param inputMap
     */
    private void preForTradeSubItemInfo(Map ext, Map inputMap) {
        String receMessPhone = (String) inputMap.get("receMessPhone");
        if (IsEmptyUtils.isEmpty(receMessPhone)) {
            return;
        }

        LanUtils lan = new LanUtils();
        Map tradeSubItemMap = new HashMap();
        Map tradeSubItem = (Map) ext.get("tradeSubItem");
        Map discntMap = (Map) ext.get("tradeDiscnt");
        List<Map> discntList = (List<Map>) discntMap.get("item");
        String elementId = "";
        String itemId = ""; // 获取存费送电子券的资费ID,得到itemId在tradeSubItem节点下发

        List<Map> items = new ArrayList<Map>();

        if (!IsEmptyUtils.isEmpty(tradeSubItem)) {
            items = (List<Map>) tradeSubItem.get("item");
        }

        List<Map> productInfoList = (List<Map>) inputMap.get("productInfo");
        for (Map productInfo : productInfoList) {
            List<Map> packageElementList = (List<Map>) productInfo.get("packageElement");
            if (!IsEmptyUtils.isEmpty(packageElementList)) {
                for (Map packageElement : packageElementList) {
                    String electProductTag = (String) packageElement.get("electProductTag");
                    if (IsEmptyUtils.isEmpty(electProductTag) || "1".equals(electProductTag)) {
                        continue;
                    }
                    elementId = (String) packageElement.get("elementId");
                }
            }
        }
        if (IsEmptyUtils.isEmpty(elementId)) {
            return;
        }
        if (!IsEmptyUtils.isEmpty(discntList)) {
            for (Map discnt : discntList) {
                if (elementId.equals(String.valueOf(discnt.get("discntCode")))) {
                    itemId = String.valueOf(discnt.get("itemId"));
                    break;
                }
            }
        }
        // RTJ2018011200043-关于AOP接口支持存费送电子券业务的需求 create by zhaok Date 18/03/05

        // 次月月初
        String startDate = GetDateUtils.getNextMonthFirstDayFormat();
        // 结束时间为生效时间两年后
        String endDateForTwoyear = GetDateUtils.getSpecifyDateTime(1, 4, GetDateUtils.getMonthLastDay(), 2);
        if (!IsEmptyUtils.isEmpty(itemId)) {
            items.add(lan.createTradeSubItemC(itemId, "dzjNbr", receMessPhone, startDate,
                    GetDateUtils.TransDate(endDateForTwoyear, "yyyy-MM-dd HH:mm:ss")));
            tradeSubItemMap.put("item", items);
            ext.put("tradeSubItem", tradeSubItemMap);
        }
    }

    /**
     * 取出主产品和附加产品的节点List
     * @param ext
     * @param subInfo
     * @param keyList
     * @return
     */
    private List preGetListUtil(Map ext, Map subInfo, String keyList) {
        List<Map> dataList = new ArrayList<Map>();
        if (!IsEmptyUtils.isEmpty(ext) && ext.containsKey(keyList)) {
            dataList = (List<Map>) ((Map) ext.get(keyList)).get("item");
        }
        if (!IsEmptyUtils.isEmpty(subInfo) && subInfo.containsKey(keyList)) {
            dataList.addAll((List<Map>) ((Map) subInfo.get(keyList)).get("item"));
        }
        return dataList;
    }

    private Map preDataUtil(List<Map> dataList) {
        Map dataMap = new HashMap();
        dataMap.put("item", dataList);
        return dataMap;
    }

    /**
     * 固网变更附加产品的订购和退订
     * 将主产品和附加产品的节点进行封装
     * @param inputMap
     * @param body
     * @param ext
     * @return
     * @throws Exception
     */
    private Map preSubInfo(Map inputMap, Map body, Map ext) throws Exception {

        Map subInfo = new HashMap();
        List<Map> unProductTypeList = new ArrayList<Map>();
        List<Map> unProductList = new ArrayList<Map>();
        List<Map> discntList = new ArrayList<Map>();
        List<Map> svcList = new ArrayList<Map>();
        List<Map> spList = new ArrayList<Map>();
        List<Map> tradeOtherList = new ArrayList<Map>();

        List<Map> productList = (List<Map>) inputMap.get("productInfo");
        List<Map> oldProductList = new ArrayList<Map>();
        String userId = String.valueOf(inputMap.get("userId"));
        String firstMonBillMode = "02"; // 订购时默认首月全量全价
        List<Map> bookProductInfo = new ArrayList<Map>();
        for (int i = 0; i < productList.size(); i++) {
            String optType = String.valueOf(productList.get(i).get("optType"));
            String oldProductId = String.valueOf(productList.get(i).get("oldProductId"));
            String productMode = String.valueOf(productList.get(i).get("productMode"));
            List<Map> packageElement = (List<Map>) productList.get(i).get("packageElement");
            // 只处理附加产品的信息
            if ("0".equals(productMode)) {
                continue;
            }
            productMode = "1".equals(productMode) ? "01" : "00";// 1附加产品,0主产品
            String provinceCode = (String) inputMap.get("provinceCode");
            String eparchyCode = (String) inputMap.get("eparchyCode");
            if ("0".equals(optType)) {

                productList.get(i).put("firstMonBillMode", "02");
                // 订购的附加产品
                productList.get(i).put("productId", oldProductId);
                bookProductInfo.add(productList.get(i));
                continue;
            }
            // 处理退订的产品和附加包
            if ("1".equals(optType)) {

                Map inMap = new HashMap();
                inMap.put("PRODUCT_ID", oldProductId);
                inMap.put("PRODUCT_MODE", productMode);
                inMap.put("PROVINCE_CODE", provinceCode);
                inMap.put("EPARCHY_CODE", eparchyCode);
                Map productInfoResult = n25Dao.queryProductInfoByProductId(inMap);

                String unBookProductId = String.valueOf(productInfoResult.get("PRODUCT_ID")); // 数据库值为数字类型
                String unBookProductMode = String.valueOf(productInfoResult.get("PRODUCT_MODE"));
                String unBookBrandCode = String.valueOf(productInfoResult.get("BRAND_CODE"));
                String unBookProductTypeCode = String.valueOf(productInfoResult.get("PRODUCT_TYPE_CODE"));

                String productStartDate = GetDateUtils.getSysdateFormat();
                String productEndDate = GetDateUtils.getMonthLastDay();

                List<Map> oldProducts = (List<Map>) inputMap.get("oldProductInfo");
                for (Map oldProduct : oldProducts) {
                    if (oldProductId.equals(oldProduct.get("productId"))) {
                        productStartDate = (String) oldProduct.get("productActiveTime");
                        productStartDate = GetDateUtils.transDate(productStartDate, 19);
                    }
                }

                Map paraMap = new HashMap();
                paraMap.put("productMode", unBookProductMode);
                paraMap.put("productId", unBookProductId);
                paraMap.put("productTypeCode", unBookProductTypeCode);
                paraMap.put("brandCode", unBookBrandCode);
                paraMap.put("modifyTag", "1");
                paraMap.put("productStartDate", productStartDate);
                paraMap.put("productEndDate", productEndDate);
                paraMap.put("eparchyCode", eparchyCode);
                paraMap.put("userId", userId);
                // 拼装退订的产品节点
                preProductItem(unProductList, unProductTypeList, paraMap);

                Map tradeOther = new HashMap();
                tradeOther.put("xDatatype", "NULL");
                tradeOther.put("modifyTag", "1");
                tradeOther.put("rsrvStr1", unBookProductId);
                tradeOther.put("rsrvStr2", unBookProductMode);
                tradeOther.put("rsrvStr3", "-9");
                tradeOther.put("rsrvStr4", unBookProductTypeCode);
                tradeOther.put("rsrvStr5", unBookProductTypeCode);
                tradeOther.put("rsrvStr6", "-1");
                tradeOther.put("rsrvStr7", "0");
                tradeOther.put("rsrvStr8", "");
                tradeOther.put("rsrvStr9", unBookBrandCode);
                tradeOther.put("rsrvStr10", inputMap.get("serialNumber"));
                tradeOther.put("rsrvValueCode", "NEXP");
                tradeOther.put("rsrvValue", userId);
                tradeOther.put("startDate", productStartDate);
                tradeOther.put("endDate", productEndDate);
                tradeOtherList.add(tradeOther);

                // 处理退订的附加包
                if (!IsEmptyUtils.isEmpty(packageElement)) {
                    List<String> deleteEle = new ArrayList<String>();
                    for (Map elementMap : packageElement) {
                        String modType = (String) elementMap.get("modType");
                        if ("1".equals(modType)) { // 退订
                            deleteEle.add((String) elementMap.get("elementId"));
                        }
                    }
                    // 处理退订的资费
                    preDeleteInfo(deleteEle, discntList, svcList, spList, inputMap);
                }
            }
            // 产品内变更
            if ("2".equals(optType)) {
                // 如果有附加包
                if (!IsEmptyUtils.isEmpty(packageElement)) {
                    List<String> deleteEle = new ArrayList<String>();
                    List<Map> addElementInfo = new ArrayList<Map>();
                    for (Map elementMap : packageElement) {
                        String modType = (String) elementMap.get("modType");
                        if ("1".equals(modType)) { // 退订
                            deleteEle.add((String) elementMap.get("elementId"));
                        }
                        else if ("0".equals(modType)) {// 订购
                            Map peparam = new HashMap();
                            peparam.put("PROVINCE_CODE", provinceCode);
                            peparam.put("PRODUCT_ID", oldProductId);
                            peparam.put("EPARCHY_CODE", eparchyCode);
                            peparam.put("PACKAGE_ID", elementMap.get("packageId"));
                            peparam.put("ELEMENT_ID", elementMap.get("elementId"));
                            List<Map> packageElementInfo = n25Dao.queryPackageElementInfo(peparam);
                            if (!IsEmptyUtils.isEmpty(packageElementInfo)) {
                                addElementInfo.addAll(packageElementInfo);
                            }
                            else {
                                throw new EcAopServerBizException("9999", "订购的元素[" + elementMap.get("elementId")
                                        + "]在产品[" + oldProductId + "]下未查询到,请重试");
                            }
                        }
                    }
                    // 处理退订的资费
                    preDeleteInfo(deleteEle, discntList, svcList, spList, inputMap);
                    // 处理订购的资费
                    String modifyTag = "0";
                    String isFinalCode = "N";// 订购的元素默认都要计算偏移
                    preProductInfo(addElementInfo, discntList, svcList, spList, inputMap, modifyTag,
                            MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                }
            }
        }
        if (null != bookProductInfo && !bookProductInfo.isEmpty()) {
            Map tempData = new HashMap();
            tempData.put("eparchyCode", inputMap.get("eparchyCode"));
            tempData.put("userId", inputMap.get("userId"));
            tempData.put("serialNumber", inputMap.get("serialNumber"));
            subInfo.putAll(TradeManagerUtils.newPreProductInfo(bookProductInfo, "00" + inputMap.get("province"),
                    tempData));
        }

        // 处理主产品和附加产品的svcList，spList，discntList，productList，productTypeList，tradeOtherList
        discntList.addAll(preGetListUtil(ext, subInfo, "tradeDiscnt"));
        svcList.addAll(preGetListUtil(ext, subInfo, "tradeSvc"));
        spList.addAll(preGetListUtil(ext, subInfo, "tradeSp"));
        unProductList.addAll(preGetListUtil(ext, subInfo, "tradeProduct"));
        unProductTypeList.addAll(preGetListUtil(ext, subInfo, "tradeProductType"));
        tradeOtherList.addAll(preGetListUtil(ext, subInfo, "tradeOther"));

        ext.put("tradeDiscnt", preDataUtil(discntList));
        ext.put("tradeSvc", preDataUtil(svcList));
        ext.put("tradeSp", preDataUtil(spList));
        ext.put("tradeProduct", preDataUtil(unProductList));
        ext.put("tradeProductType", preDataUtil(unProductTypeList));
        ext.put("tradeOther", preDataUtil(tradeOtherList));
        // RHQ2018080600048-CBSS收费明细报表-蜂行动
        List<Map> tradeItemList = (List<Map>) inputMap.get("tradeItem");
        if (!IsEmptyUtils.isEmpty(tradeItemList)) {
            Map TradeItem = new HashMap();
            List<Map> items = new ArrayList<Map>();
            for (Map tradeItem : tradeItemList) {
                items.add(LanUtils.createTradeItem((String) tradeItem.get("attrCode"), tradeItem.get("attrValue")));
            }
            TradeItem.put("item", items);
            ext.put("tradeItem", TradeItem);
        }
        return ext;
    }

    /**
     * 处理订购的资费
     * @param addElementInfo
     * @param discntList
     * @param svcList
     * @param spList
     * @param inputMap
     * @throws Exception
     */
    private void preProductInfo(List<Map> productInfoResult, List<Map> discntList, List<Map> svcList, List<Map> spList,
            Map inputMap, String modifyTag, String endDate) throws Exception {
        String eparchyCode = (String) inputMap.get("eparchyCode");
        String userId = (String) inputMap.get("userId");
        String startDate = GetDateUtils.transDate(GetDateUtils.getDate(), 19);
        for (int j = 0; j < productInfoResult.size(); j++) {
            if ("D".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                String elementId = String.valueOf(productInfoResult.get(j).get("ELEMENT_ID"));
                Map dis = new HashMap();
                dis.put("id", userId);
                dis.put("idType", "1");
                dis.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                dis.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                dis.put("discntCode", elementId);
                dis.put("specTag", "1");
                dis.put("relationTypeCode", "");
                dis.put("itemId", TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID"));
                dis.put("modifyTag", modifyTag);
                dis.put("startDate", startDate);
                dis.put("endDate", endDate);
                dis.put("userIdA", "-1");
                dis.put("xDatatype", "NULL");
                discntList.add(dis);
            }
            if ("S".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                Map svc = new HashMap();
                svc.put("userId", userId);
                svc.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                svc.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                svc.put("serviceId", productInfoResult.get(j).get("ELEMENT_ID"));
                svc.put("itemId", TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID"));
                svc.put("modifyTag", modifyTag);
                svc.put("startDate", startDate);
                svc.put("endDate", endDate);
                svc.put("userIdA", "-1");
                svc.put("xDatatype", "NULL");
                svcList.add(svc);
            }
            if ("X".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                String spId = "-1";
                String partyId = "-1";
                String spProductId = "-1";
                Map spItemParam = new HashMap();
                spItemParam.put("PTYPE", "X");
                spItemParam.put("PROVINCE_CODE", inputMap.get("provinceCode"));
                spItemParam.put("SPSERVICEID", productInfoResult.get(j).get("ELEMENT_ID"));
                List<Map> spItemInfoResult = n25Dao.newQuerySPServiceAttr(spItemParam);
                if (IsEmptyUtils.isEmpty(spItemInfoResult)) {
                    throw new EcAopServerBizException("9999", "在SP表中未查询到【" + productInfoResult.get(j).get("ELEMENT_ID")
                            + "】的元素属性信息");
                }
                for (int l = 0; l < spItemInfoResult.size(); l++) {
                    Map spItemInfo = spItemInfoResult.get(l);
                    spId = (String) spItemInfo.get("SP_ID");
                    partyId = (String) spItemInfo.get("PARTY_ID");
                    spProductId = (String) spItemInfo.get("SP_PRODUCT_ID");
                }
                Map sp = new HashMap();
                sp.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                sp.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                sp.put("partyId", partyId);
                sp.put("spId", spId);
                sp.put("spProductId", spProductId);
                sp.put("spServiceId", productInfoResult.get(j).get("ELEMENT_ID"));
                sp.put("userId", userId);
                sp.put("serialNumber", inputMap.get("serialNumber"));
                sp.put("firstBuyTime", startDate);
                sp.put("paySerialNumber", inputMap.get("serialNumber"));
                sp.put("startDate", startDate);
                sp.put("endDate", endDate);
                sp.put("updateTime", startDate);
                sp.put("remark", "");
                sp.put("modifyTag", modifyTag);
                sp.put("payUserId", userId);
                sp.put("itemId", TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID"));
                sp.put("userIdA", "-1");
                sp.put("xDatatype", "NULL");
                spList.add(sp);
            }
        }
    }

    /**
     * 处理退订资费的节点
     * @param deleteEle
     * @param discntList
     * @param svcList
     * @param spList
     * @param object
     * @param inputMap
     * @throws Exception
     */
    private void preDeleteInfo(List<String> deleteEle, List<Map> discntList, List<Map> svcList, List<Map> spList,
            Map inputMap) throws Exception {

        Map userInfo = (Map) inputMap.get("oldUserInfo");
        List<Map> discntInfo = (List<Map>) userInfo.get("discntInfo");
        List<Map> spInfo = (List<Map>) userInfo.get("spInfo");
        List<Map> svcInfo = (List<Map>) userInfo.get("svcInfo");
        String acceptDate = GetDateUtils.transDate(GetDateUtils.getDate(), 19);// 当前时间
        String endDate = GetDateUtils.getMonthLastDay();
        String modifyTag = "1";// 退订
        String isFinalCode = "";
        String userId = (String) inputMap.get("userId");
        String eparchyCode = (String) inputMap.get("eparchyCode");

        for (String delEle : deleteEle) {

            // 退订discntInfo
            if (!IsEmptyUtils.isEmpty(discntInfo)) {
                for (Map dis : discntInfo) {
                    if (delEle.equals(dis.get("discntCode") + "")) {
                        Map discnt = n25Dao.qryDiscntAttr(MapUtils.asMap("DISCNT_CODE", dis.get("discntCode")));
                        String endmode = "" + discnt.get("END_MODE");
                        if ("0".equals(endmode)) {// 立即结束
                            endDate = acceptDate;
                        }
                        else if ("2".equals(endmode)) { // 2的时候取当天23:59:59
                            endDate = GetDateUtils.getSpecifyDateTime(1, 1, acceptDate, 1);
                            endDate = GetDateUtils.getSpecifyDateTime(1, 6, endDate, -1);
                        }
                        Map unDis = new HashMap();
                        unDis.put("id", userId);
                        unDis.put("idType", "1");
                        unDis.put("productId", dis.get("productId"));
                        unDis.put("packageId", dis.get("packageId"));
                        unDis.put("discntCode", dis.get("discntCode"));
                        unDis.put("specTag", "1");
                        unDis.put("relationTypeCode", "");
                        unDis.put("modifyTag", modifyTag);
                        unDis.put("startDate", GetDateUtils.transDate("" + dis.get("startDate"), 19));
                        unDis.put("endDate", endDate);
                        unDis.put("userIdA", "-1");
                        unDis.put("xDatatype", "NULL");
                        discntList.add(unDis);
                    }
                }
            }

            // 退订svcInfo
            if (!IsEmptyUtils.isEmpty(svcInfo)) {
                for (Map sv : svcInfo) {
                    if (delEle.equals(sv.get("serviceId") + "")) {
                        Map svc = new HashMap();
                        svc.put("userId", userId);
                        svc.put("packageId", sv.get("productId"));
                        svc.put("productId", sv.get("packageId"));
                        svc.put("serviceId", sv.get("serviceId"));
                        svc.put("modifyTag", modifyTag);
                        svc.put("startDate", sv.get("startDate"));
                        svc.put("endDate", endDate);
                        svc.put("userIdA", "-1");
                        svc.put("xDatatype", "NULL");
                        svcList.add(svc);
                    }
                }
            }

            // 退订spInfo
            if (!IsEmptyUtils.isEmpty(spInfo)) {
                for (Map p : spInfo) {
                    if (delEle.equals(p.get("spServiceId") + "")) {
                        String spId = "-1";
                        String partyId = "-1";
                        String spProductId = "-1";
                        Map spItemParam = new HashMap();
                        spItemParam.put("PTYPE", "X");
                        spItemParam.put("PROVINCE_CODE", inputMap.get("provinceCode"));
                        spItemParam.put("SPSERVICEID", p.get("spServiceId"));
                        List<Map> spItemInfoResult = n25Dao.newQuerySPServiceAttr(spItemParam);
                        if (IsEmptyUtils.isEmpty(spItemInfoResult)) {
                            throw new EcAopServerBizException("9999", "在SP表中未查询到【" + p.get("spServiceId") + "】的元素属性信息");
                        }
                        for (int l = 0; l < spItemInfoResult.size(); l++) {
                            Map spItemInfo = spItemInfoResult.get(l);
                            spId = (String) spItemInfo.get("SP_ID");
                            partyId = (String) spItemInfo.get("PARTY_ID");
                            spProductId = (String) spItemInfo.get("SP_PRODUCT_ID");
                        }
                        Map sp = new HashMap();
                        sp.put("productId", p.get("productId"));
                        sp.put("packageId", p.get("packageId"));
                        sp.put("partyId", partyId);
                        sp.put("spId", spId);
                        sp.put("spProductId", spProductId);
                        sp.put("spServiceId", p.get("spServiceId"));
                        sp.put("userId", userId);
                        sp.put("serialNumber", inputMap.get("serialNumber"));
                        sp.put("firstBuyTime", p.get("startDate"));
                        sp.put("paySerialNumber", inputMap.get("serialNumber"));
                        sp.put("startDate", p.get("startDate"));
                        sp.put("endDate", endDate);
                        sp.put("updateTime", acceptDate);
                        sp.put("remark", "");
                        sp.put("modifyTag", modifyTag);
                        sp.put("payUserId", userId);
                        sp.put("itemId", TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID"));
                        sp.put("userIdA", "-1");
                        sp.put("xDatatype", "NULL");
                        spList.add(sp);

                    }
                }
            }
        }

    }

    /**
     * 拼装product和productType节点
     * @param productList
     * @param productTypeList
     * @param paraMap
     */
    private void preProductItem(List<Map> productList, List<Map> productTypeList, Map<String, String> paraMap) {
        // 拼装产品节点
        Map productItem = new HashMap();
        productItem.put("userId", paraMap.get("userId"));
        productItem.put("productMode", paraMap.get("productMode"));
        productItem.put("productId", paraMap.get("productId"));
        productItem.put("productTypeCode", paraMap.get("productTypeCode"));
        productItem.put("brandCode", paraMap.get("brandCode"));
        productItem.put("itemId", TradeManagerUtils.getSequence(paraMap.get("eparchyCode"), "SEQ_ITEM_ID"));
        productItem.put("modifyTag", paraMap.get("modifyTag"));
        productItem.put("startDate", paraMap.get("productStartDate"));
        productItem.put("endDate", paraMap.get("productEndDate"));
        productItem.put("userIdA", "-1");
        productItem.put("xDatatype", "NULL");
        productList.add(productItem);

        Map productTypeItem = new HashMap();
        productTypeItem.put("userId", paraMap.get("userId"));
        productTypeItem.put("productMode", paraMap.get("productMode"));
        productTypeItem.put("productId", paraMap.get("productId"));
        productTypeItem.put("productTypeCode", paraMap.get("productTypeCode"));
        productTypeItem.put("modifyTag", paraMap.get("modifyTag"));
        productTypeItem.put("startDate", paraMap.get("productStartDate"));
        productTypeItem.put("endDate", paraMap.get("productEndDate"));
        productTypeItem.put("xDatatype", "NULL");
        productTypeList.add(productTypeItem);

    }

    /**
     * 判断是否有主产品和附加产品处理
     * @param inputMap
     * @param isMAIN
     * @param isSUB
     * @return
     */
    private Map preSubOrMainInfo(Map inputMap) {

        Map subOrMain = new HashMap();
        boolean isMAIN = false;
        boolean isSUB = false;
        List<Map> productInfo = (List<Map>) inputMap.get("productInfo");
        for (Map pro : productInfo) {
            String productMode = (String) pro.get("productMode");
            if (!IsEmptyUtils.isEmpty(productMode) && "0".equals(productMode)) {
                isMAIN = true;
                continue;
            }
            else if (!IsEmptyUtils.isEmpty(productMode) && "1".equals(productMode)) {
                isSUB = true;
            }
        }
        subOrMain.put("isMAIN", isMAIN);
        subOrMain.put("isSUB", isSUB);
        return subOrMain;
    }

    private Map preMixExt(Map inputMap) {
        Map ext = new HashMap();
        ext.put("tradeOther", preTradeOtherItem(inputMap));
        ext.put("tradeItem", preTradeItem(inputMap));
        // TODO:请求参数转换。暂时只做主产品变更,不处理活动
        List<Map> productList = (List<Map>) inputMap.get("productInfo");
        List<Map> boradDiscntInfoList = (List<Map>) inputMap.get("boradDiscntInfo");
        List<Map> oldProductList = new ArrayList<Map>();
        for (int i = 0; i < productList.size(); i++) {
            if ("0".equals(productList.get(i).get("optType"))) {
                productList.get(i).put("firstMonBillMode", "02");// 给外围主产品默认02
                // 订购的新产品
                productList.get(i).put("productId", productList.get(i).get("oldProductId"));
            }
            else {
                oldProductList.add(productList.get(i));
            }
        }
        if (null != oldProductList || !oldProductList.isEmpty()) {
            productList.removeAll(oldProductList);
        }
        if (null != productList && !productList.isEmpty()) {
            ext.putAll(TradeManagerUtils.newPreProductInfo(productList, "00" + inputMap.get("province"), inputMap));
        }
        // 宽带需要传入资费节点,重新塞入discnt节点,作为新增资费下发
        List<Map> discntList = new ArrayList<Map>();
        if (null != boradDiscntInfoList && !boradDiscntInfoList.isEmpty()) {
            for (int j = 0; j < boradDiscntInfoList.size(); j++) {
                Map disMap = new HashMap();
                String boradDiscntId = (String) boradDiscntInfoList.get(j).get("boradDiscntId");
                inputMap.put("ELEMENT_ID", boradDiscntId);
                inputMap.put("PROVINCE_CODE", "00" + inputMap.get("province"));
                inputMap.put("PRODUCT_ID", inputMap.get("newProductId"));
                disMap = TradeManagerUtils.preProductInfoByElementIdProductId4CB(inputMap);
                discntList.add(disMap);
            }

            if (null != discntList && !discntList.isEmpty()) {
                inputMap.put("discntList", discntList);
            }
            ext.put("tradeDiscnt", new OpenApply4GProcessor().preDiscntData(inputMap));
        }
        ext.put("tradeSubItem", preTradeSubItem(inputMap));
        // 传入主产品的品牌
        ext.put("tradeUser", preTradeUserItem(inputMap));

        return ext;
    }

    private Map preTradeSubItem(Map inputMap) {
        Map TradeSubItem = new HashMap();
        String itemId = (String) inputMap.get("itemId");
        String userId = (String) inputMap.get("userId");
        String startDate = GetDateUtils.getNextMonthFirstDayFormat();
        String endDate = "20501231235959";
        LanUtils lan = new LanUtils();
        List<Map> items = new ArrayList<Map>();
        List<Map> boradDiscntInfo = (List<Map>) inputMap.get("boradDiscntInfo");
        if (null != boradDiscntInfo && !boradDiscntInfo.isEmpty()) {
            // 默认不传按续约标准资费b
            String expireDealMode = "1".equals(inputMap.get("delayType").toString()) ? "a" : "3".equals(inputMap.get(
                    "delayType").toString()) ? "t" : "b";
            String aDiscntCode = (String) inputMap.get("delayDiscntId");
            String bDiscntCode = (String) inputMap.get("boradDiscntId");
            String delayDiscntType = (String) inputMap.get("delayDiscntType");
            items.add(lan.createTradeSubItemC(itemId, "adEnd", "", startDate, endDate));
            items.add(lan.createTradeSubItemC(itemId, "expireDealMode", expireDealMode, startDate, endDate));
            items.add(lan.createTradeSubItemC(itemId, "aDiscntCode", aDiscntCode, startDate, endDate));
            if ("a".equals(expireDealMode)) {
                items.add(lan.createTradeSubItemC(itemId, "bDiscntCode", bDiscntCode, startDate, endDate));
            }
            else {
                items.add(lan.createTradeSubItemC(itemId, "bDiscntCode", "", startDate, endDate));
            }
            items.add(lan.createTradeSubItemC(itemId, "effectMode", delayDiscntType, startDate, endDate));
            items.add(lan.createTradeSubItemC(itemId, "adStart", "0", startDate, endDate));
            items.add(lan.createTradeSubItemC(itemId, "cycleNum", "1", startDate, endDate));
            items.add(lan.createTradeSubItemC(itemId, "cycle", "24", startDate, endDate));
            items.add(lan.createTradeSubItemC(itemId, "fixedHire", "24", startDate, endDate));
            items.add(lan.createTradeSubItemC(itemId, "cycleFee", "1800", startDate, endDate));
            items.add(lan.createTradeSubItemC(itemId, "recharge", "", startDate, endDate));
            items.add(lan.createTradeSubItemC(itemId, "callBack", "0", startDate, endDate));
        }
        if (!IsEmptyUtils.isEmpty(inputMap.get("bindType"))) {
            items.add(lan.createTradeSubItem("BINDING_TYPE", inputMap.get("bindType"), userId));
        }
        if (!IsEmptyUtils.isEmpty(inputMap.get("bindSerialNumber"))) {
            items.add(lan.createTradeSubItem("BINDING_SERIALNUM", inputMap.get("bindSerialNumber"), userId));
        }
        if (!IsEmptyUtils.isEmpty(inputMap.get("bindUserId"))) {
            items.add(lan.createTradeSubItem("BINDING_USERID", inputMap.get("bindUserId"), userId));
        }
        items.add(lan.createTradeSubItem("SHARE_NBR", "", userId));
        items.add(lan.createTradeSubItem("USETYPE", "1", userId));
        items.add(lan.createTradeSubItem("WOPAY_MONEY", "", userId));
        items.add(lan.createTradeSubItem("ACCESS_TYPE", "B61", userId));//
        items.add(lan.createTradeSubItem("TIME_LIMIT_ID", "2", userId));
        items.add(lan.createTradeSubItem("CONNECTNETMODE", "1", userId));
        items.add(lan.createTradeSubItem("ISWAIL", "0", userId));
        items.add(lan.createTradeSubItem("TERMINAL_SN", "", userId));
        items.add(lan.createTradeSubItem("TERMINAL_MAC", "", userId));
        items.add(lan.createTradeSubItem("ISWOPAY", "0", userId));
        items.add(lan.createTradeSubItem("KDLX_2061", "N", userId));
        items.add(lan.createTradeSubItem("TERMINALSRC_MODE", "A001", userId));
        items.add(lan.createTradeSubItem("USER_TYPE_CODE", "0", userId));
        items.add(lan.createTradeSubItem("CB_ACCESS_TYPE", "A13", userId));
        items.add(lan.createTradeSubItem("ACCT_NBR", "", userId));//
        items.add(lan.createTradeSubItem("COMMPANY_NBR", "", userId));
        items.add(lan.createTradeSubItem("TOWN_FLAG", "C", userId));
        items.add(lan.createTradeSubItem("TERMINAL_MODEL", "", userId));
        items.add(lan.createTradeSubItem("SPEED", "10", userId));//
        items.add(lan.createTradeSubItem("HZGS_0000", "", userId));
        items.add(lan.createTradeSubItem("EXPECT_RATE", "", userId));
        items.add(lan.createTradeSubItem("TERMINAL_TYPE", "2", userId));
        items.add(lan.createTradeSubItem("TERMINAL_BRAND", "", userId));
        items.add(lan.createTradeSubItem("COLLINEAR_TYPE", "X3", userId));
        items.add(lan.createTradeSubItem("ACCT_PASSWD", inputMap.get("userPasswd"), userId));//
        items.add(lan.createTradeSubItem("LINK_NAME", inputMap.get("contactPerson"), userId));
        items.add(lan.createTradeSubItem("OTHERCONTACT", "", userId));
        items.add(lan.createTradeSubItem("LINK_PHONE", inputMap.get("contactPhone"), userId));//

        TradeSubItem.put("item", items);
        return TradeSubItem;
    }

    private Map preTradeItem(Map inputMap) {
        Map TradeItem = new HashMap();
        LanUtils lan = new LanUtils();
        List<Map> items = new ArrayList<Map>();
        items.add(lan.createAttrInfoNoTime("PH_NUM", ""));
        items.add(lan.createAttrInfoNoTime("SFGX_2060", "N"));
        items.add(lan.createAttrInfoNoTime("GXLX_TANGSZ", ""));
        items.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", inputMap.get("city")));
        items.add(LanUtils.createTradeItem("DEVELOP_DEPART_ID", ""));
        items.add(LanUtils.createTradeItem("MARKETING_MODE", "1"));
        items.add(LanUtils.createTradeItem("DEVELOP_STAFF_ID", ""));
        items.add(LanUtils.createTradeItem("IMMEDIACY_INFO", "0"));
        items.add(LanUtils.createTradeItem("USER_ACCPDATE", ""));
        items.add(LanUtils.createTradeItem("SUB_TYPE", "0"));
        if (!IsEmptyUtils.isEmpty(inputMap.get("bindSrc"))) {
            items.add(LanUtils.createTradeItem("BINDING_SRC", inputMap.get("bindSrc")));
        }
        // RHQ2018080600048-CBSS收费明细报表-蜂行动
        dealTradeItemFromMsg(inputMap, items);

        TradeItem.put("item", items);
        return TradeItem;
    }

    /**
     * 用于处理msg里面的tradeitem
     */
    private void dealTradeItemFromMsg(Map msg, List<Map> tradeinfoList) {
        List<Map> tradeItemList = (List<Map>) msg.get("tradeItem");
        if (!IsEmptyUtils.isEmpty(tradeItemList)) {
            for (Map tradeItem : tradeItemList) {
                tradeinfoList.add(LanUtils.createTradeItem((String) tradeItem.get("attrCode"),
                        tradeItem.get("attrValue")));
            }
        }
    }

    private Map preTradeUserItem(Map inputMap) {
        Map TradeUser = new HashMap();
        List<Map> item = new ArrayList<Map>();
        List<Map> productList = (List<Map>) inputMap.get("productInfo");
        List<Map> boradDiscntInfo = (List<Map>) inputMap.get("boradDiscntInfo");
        String netTypeCode = null;
        if (null == boradDiscntInfo || boradDiscntInfo.isEmpty()) {
            netTypeCode = "0030";
        }
        else {
            netTypeCode = "0040";
        }
        for (Map productMap : productList) {
            if ("0".equals(productMap.get("optType")) && "0".equals(productMap.get("productMode"))) {
                Map itemMap = MapUtils.asMap("userId", inputMap.get("userId"), "productId",
                        productMap.get("oldProductId"), "xDatatype", "NULL", "brandCode", inputMap.get("brandCode"),
                        "netTypeCode", netTypeCode, "productTypeCode", inputMap.get("productTypeCode"));
                item.add(itemMap);
            }
        }
        TradeUser.put("item", item);
        return TradeUser;
    }

    private Map preTradeOtherItem(Map inputMap) {
        Map TradeOther = new HashMap();
        List<Map> item = new ArrayList<Map>();
        String monLastTime = GetDateUtils.getMonthLastDayFormat();
        // 调用移网套餐变更的方法
        List<Map> oldProductList = new ChangeProduct4GReplaceN6Processor().queryProductInfoWithLimit(
                "00" + inputMap.get("province"), (String) inputMap.get("oldProductId"), (String) inputMap.get("city"),
                "02", "00");
        if (null == oldProductList || oldProductList.isEmpty()) {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + inputMap.get("oldProductId") + "】的产品信息");
        }
        String subId = String.valueOf(oldProductList.get(0).get("PRODUCT_ID"));
        // 调用移网套餐变更的方法
        List<Map> subOldProductItem = new ChangeProduct4GReplaceN6Processor().queryProductItemInfoNoPtype(subId);
        if (null == subOldProductItem || subOldProductItem.isEmpty()) {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品属性表【" + inputMap.get("oldProductId") + "】的产品信息");
        }
        String subProductTypeCode = TradeManagerUtils.getValueFromItem("PRODUCT_TYPE_CODE", subOldProductItem);
        String subBrandCode = TradeManagerUtils.getValueFromItem("BRAND_CODE", subOldProductItem);

        Map itemMap = MapUtils.asMap("xDatatype", null, "rsrvValueCode", "NEXP", "rsrvValue", inputMap.get("userId"),
                "rsrvStr1", inputMap.get("oldProductId"), "rsrvStr2", "00", "rsrvStr3", "-9", "rsrvStr4",
                subProductTypeCode, "rsrvStr5", subProductTypeCode, "rsrvStr6", "-1", "rsrvStr7", "0", "rsrvStr8", "",
                "rsrvStr9", subBrandCode, "rsrvStr10", inputMap.get("serialNumber"), "modifyTag", "1", "startDate",
                inputMap.get("startDate"), "endDate", monLastTime);
        item.add(itemMap);

        TradeOther.put("item", item);
        return TradeOther;
    }

    private Map preMixBase(Map inputMap, String appCode) {
        Map base = new HashMap();
        // 获取订购的主产品
        List<Map> productInfo = (List<Map>) inputMap.get("productInfo");
        String netType = (String) inputMap.get("netType");
        for (Map productInfoMap : productInfo) {
            if ("0".equals(productInfoMap.get("optType")) && "0".equals(productInfoMap.get("productMode"))) {
                base.put("productId", productInfoMap.get("oldProductId"));
            }
        }
        if (IsEmptyUtils.isEmpty(base.get("productId"))) {
            base.put("productId", inputMap.get("oldProductId"));
        }
        // 获取宽带资费节点
        List<Map> boradDiscntInfo = (List<Map>) inputMap.get("boradDiscntInfo");
        base.put("advancePay", "0");
        base.put("userDiffCode", "00");
        base.put("acceptDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("inModeCode", new ChangeCodeUtils().getInModeCode(appCode));
        // 固网号码新增区号 by wangmc 20181023
        base.put("serinalNamber", "" + inputMap.get("areaCode") + inputMap.get("serialNumber"));
        base.put("usecustId", inputMap.get("custId"));
        base.put("actorCertNum", "");
        base.put("remark", "");
        base.put("feeState", "");
        base.put("contactPhone", inputMap.get("contactPhone"));
        base.put("nextDealTag", "Z");
        base.put("contactAddress", inputMap.get("contactAddress"));
        base.put("olcomTag", "0");
        base.put("custId", inputMap.get("custId"));
        base.put("acctId", inputMap.get("acctId"));
        base.put("userId", inputMap.get("userId"));
        base.put("custName", inputMap.get("custName"));
        base.put("foregift", "0");
        base.put("execTime", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("termIp", "132.35.87.221");
        base.put("actorCertTypeId", "");
        base.put("chkTag", "0");
        base.put("tradeId", inputMap.get("tradeId"));
        base.put("actorPhone", "");
        base.put("operFee", "0");
        base.put("cancelTag", "0");
        base.put("tradeTypeCode", "0127");
        base.put("cityCode", inputMap.get("district"));
        base.put("eparchyCode", inputMap.get("city"));
        // 当外围传入网别就下发，没有就走老逻辑 create zhaok 18/01/07
        if (!IsEmptyUtils.isEmpty(netType)) {
            if ("03".equals(netType)) {
                base.put("netTypeCode", "0030");
            }
            else if ("04".equals(netType)) {
                base.put("netTypeCode", "0040");
            }
        }
        else if (null == boradDiscntInfo || boradDiscntInfo.isEmpty()) {
            base.put("netTypeCode", "0030");
        }
        else {
            base.put("netTypeCode", "0040");
        }
        base.put("contact", "");
        base.put("feeStaffId", "");
        base.put("checktypeCode", "0");
        base.put("subscribeId", inputMap.get("tradeId"));
        base.put("brandCode", inputMap.get("brandCode"));
        base.put("actorName", "");
        return base;
    }

    /**
     * 调cBSS三户接口
     * @param exchange
     * @param oldProductId
     * @return
     * @throws Exception
     */
    private Map threePartInfo(Exchange exchange, String oldProductId) throws Exception {
        Map retMap = new GetThreePartInfoUtils().process(exchange);
        Map preDateMap = new HashMap();
        List<Map> retAcctList = (ArrayList<Map>) retMap.get("acctInfo");
        if (null == retAcctList || 0 == retAcctList.size()) {
            throw new EcAopServerBizException("9999", "三户信息校验接口:核心系统未返回账户信息");
        }
        List<Map> retUserList = (ArrayList<Map>) retMap.get("userInfo");
        if (null == retUserList || 0 == retUserList.size()) {
            throw new EcAopServerBizException("9999", "三户信息校验接口:核心系统未返回用户信息");
        }
        List<Map> reCusttList = (ArrayList<Map>) retMap.get("custInfo");
        if (null == reCusttList || 0 == reCusttList.size()) {
            throw new EcAopServerBizException("9999", "三户信息校验接口:核心系统未返回客户信息");
        }
        // 需要通过userInfo的返回,拼装老产品信息
        Map userInfoMap = retUserList.get(0);
        Object oldProduct = userInfoMap.get("productId");
        List<Map> discntList = new ArrayList<Map>();
        List<Map> discntInfo = (List<Map>) userInfoMap.get("discntInfo");
        for (Map discnt : discntInfo) {
            if (oldProduct.equals(discnt.get("productId"))) {
                Map element = new HashMap();
                element.put("productId", oldProduct);
                element.put("packageId", discnt.get("packageId"));
                element.put("discntCode", discnt.get("discntCode"));
                element.put("itemId", discnt.get("itemId"));
                discntList.add(element);
            }
        }
        preDateMap.put("oldProDiscnt", discntList);
        preDateMap.put("acctId", retAcctList.get(0).get("acctId"));
        preDateMap.put("userId", retUserList.get(0).get("userId"));
        preDateMap.put("custId", reCusttList.get(0).get("custId"));
        preDateMap.put("custName", reCusttList.get(0).get("custName"));
        preDateMap.put("userPasswd", retUserList.get(0).get("userPasswd"));
        preDateMap.put("oldProductId", retUserList.get(0).get("productId"));
        // 老产品相关信息
        preDateMap.put("productTypeCode", retUserList.get(0).get("productTypeCode"));
        preDateMap.put("brandCode", retUserList.get(0).get("brandCode"));
        List<Map> threePartProductList = (List<Map>) retUserList.get(0).get("productInfo");
        preDateMap.put("oldProductInfo", threePartProductList);
        preDateMap.put("oldUserInfo", userInfoMap);
        String startDate = null;
        for (int i = 0; i < threePartProductList.size(); i++) {
            if (oldProduct.equals(threePartProductList.get(i).get("productId"))) {
                startDate = (String) threePartProductList.get(i).get("productActiveTime");
                preDateMap.put("startDate", startDate);
            }
        }
        if (null == startDate || "".equals(startDate)) {
            throw new EcAopServerBizException("9999", "三户信息校验接口未返回老产品生效时间");
        }
        return preDateMap;
    }

}
