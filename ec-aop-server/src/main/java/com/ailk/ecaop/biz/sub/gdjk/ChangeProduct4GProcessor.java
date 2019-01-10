package com.ailk.ecaop.biz.sub.gdjk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.log.elk.ELKAopLogger;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.ProductManagerUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.google.common.collect.Maps;

/**
 * 产品（套餐）变更处理接口
 * @author GaoLei
 */
@EcRocTag("chgProduct4G")
public class ChangeProduct4GProcessor {

    // 00：订购；01：退订
    private static String BOOK = "00";
    private static String UNBOOK = "01";
    // 0：可选产品；1：主产品
    private static String MAIN = "1";
    private static String SUB = "2";

    private static String END_OF_WORLD = "2050-12-31 23:59:59";

    public Map chgProdPreDeal4G(Exchange exchange, ParametersMappingProcessor[] pmp) throws Exception {
        Map<String, String> appendMap = Maps.newHashMap();
        Map body = exchange.getIn().getBody(Map.class);
        // Map temp = new HashMap(body);
        Map msg = (Map) body.get("msg");

        Map msgContainer = Maps.newHashMap();
        msgContainer.putAll(msg);

        Map mixTemplate = (Map) msg.get("mixTemplate");
        Map phoneTemplate = (Map) msg.get("phoneTemplate");
        String combAdvance = null;
        String addType = (String) mixTemplate.get("addType");
        String mainNumberTag = (String) phoneTemplate.get("mainNumberTag");
        msg.put("addType", mixTemplate.get("addType"));
        if ("3".equals(addType) && "0".equals(mainNumberTag)) {
            combAdvance = "1";
        }
        LanOpenApp4GDao dao = new LanOpenApp4GDao();

        String provinceCode = "00" + msg.get("province");
        String serialNumber = (String) phoneTemplate.get("serialNumber");
        String tradeStaffId = (String) msg.get("operatorId");
        // 三户查询
        Map userRsp = getInfoByNum4G(exchange, serialNumber, pmp);

        List<Map> custInfoList = (List<Map>) userRsp.get("custInfo");
        List<Map> userInfoList = (List<Map>) userRsp.get("userInfo");
        List<Map> acctInfoList = (List<Map>) userRsp.get("acctInfo");
        // 需要通过userInfo的返回,拼装老产品信息
        Map custInfo = custInfoList.get(0);
        Map userInfo = userInfoList.get(0);
        Map acctInfo = acctInfoList.get(0);
        Object oldProduct = userInfo.get("productId");
        List<Map> oldProducts = (List<Map>) userInfo.get("productInfo");
        List<Map> packageElement = new ArrayList<Map>();
        List<Map> discntInfo = (List<Map>) userInfo.get("discntInfo");
        for (Map discnt : discntInfo) {
            if (oldProduct.equals(discnt.get("productId"))) {
                Map element = new HashMap();
                element.put("packageId", discnt.get("packageId"));
                element.put("elementId", discnt.get("discntCode"));
                element.put("elementType", "D");
                element.put("modType", "1");
                packageElement.add(element);
            }
        }
        String mainCardTag = (String) phoneTemplate.get("mainCardTag");
        if (StringUtils.isEmpty(mainCardTag)) {
            mainCardTag = "1";// 非加入主卡 0加入主卡
            msg.put("mainCardTag", mainCardTag);
        }
        else {
            msg.put("mainCardTag", mainCardTag);
        }
        List<Map> uuInfo = (ArrayList<Map>) userInfo.get("uuInfo");
        if (!IsEmptyUtils.isEmpty(uuInfo)) {
            for (Map temp : uuInfo) {
                if ("ZF".equals(temp.get("relationTypeCode"))) {
                    if ("0".equals(mainCardTag) && "3".equals(addType)) {
                        throw new EcAopServerBizException("9999", "号码" + serialNumber + "已经存在主副卡关系，不允许重复办理");
                    }
                    if (temp.get("serialNumberA").equals(temp.get("serialNumberB"))) {
                        msg.put("combAdvance", "1");
                    }
                }
            }
        }
        if ("0".equals(mainCardTag) && "3".equals(addType)) {
            combAdvance = "1";
        }
        String groupFlag = (String) phoneTemplate.get("groupFlag");
        msg.put("groupFlag", groupFlag);
        if ("1".equals(groupFlag)) {// FIXME 收入集团归集
            Map jtcpMap = new HashMap();
            jtcpMap.put("province", "00" + msg.get("province"));
            jtcpMap.put("groupId", phoneTemplate.get("groupId"));
            jtcpMap.put("operatorId", msg.get("operatorId"));
            jtcpMap.put("city", ChangeCodeUtils.changeEparchy(msg));
            jtcpMap.put("district", msg.get("district"));
            jtcpMap.put("channelId", msg.get("channelId"));
            jtcpMap.put("channelType", msg.get("channelType"));
            msg = dealJtcp(exchange, jtcpMap, msg);
        }
        Map oldProdMap = new HashMap();
        oldProdMap.put("productId", oldProduct);
        oldProdMap.put("productMode", "1");
        oldProdMap.put("optType", "01");
        oldProdMap.put("packageElement", packageElement);
        List<Map> oldProductInfo = new ArrayList<Map>();
        oldProductInfo.add(oldProdMap);
        // 账户资料校验 begin
        Map baseObject = new HashMap();
        // BASE数据组织开始
        exchange.getIn().setBody(body);
        String payRelId = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, msg),
                "SEQ_PAYRELA_ID", 1).get(0);
        msg.put("payRelId", payRelId);

        // 获取员工信息
        // msg.put("tradeStaffId", tradeStaffId);
        // msg.put("checkMode", "1");
        // body.put("msg", msg);
        // exchange.getIn().setBody(body);
        // Map staffInfo = TradeManagerUtils.getStaffInfo(exchange);
        // String departId = (String) staffInfo.get("departId");
        String departId = (String) msg.get("channelId");
        String acceptDate = GetDateUtils.transDate(GetDateUtils.getDate(), 19);
        // String acceptDate = GetDateUtils.getDate();
        String nextMon1stDay = GetDateUtils.getNextMonthDate();
        String userId = (String) userInfo.get("userId");
        String custId = (String) custInfo.get("custId");
        String acctId = (String) msg.get("acctId");
        String useCustId = (String) userInfo.get("usecustId");
        String cycleId = TradeManagerUtils.getCycleId(GetDateUtils.transDate(acceptDate, 19));

        String brandCode = (String) userInfo.get("brandCode");
        String userDiffCode = (String) userInfo.get("userDiffCode");
        String custName = (String) custInfo.get("custName");
        // 联系人
        String linkName = (null == custInfo.get("contact") || "".equals(custInfo.get("contact")) ? custName
                : (String) custInfo.get("contact"));
        String linkAddress = (String) custInfo.get("certAddr");
        String linkPhone = (String) custInfo.get("contactPhone");
        String eparchyCode = (String) userInfo.get("eparchyCode");
        String cityCode = (String) userInfo.get("cityCode");
        String newProductId = (String) userInfo.get("productId");

        List<Map> productInfoList = (List<Map>) ((Map) msg.get("phoneTemplate")).get("phoneProduct");

        if (CollectionUtils.isEmpty(productInfoList)) {
            throw new EcAopServerBizException("9999", "产品信息不能为空");
        }
        Object newProduct = "";
        for (Map product : productInfoList) {
            product.put("optType", "00");// 订购新产品
            if (MAIN.equals(product.get("productMode"))) {
                newProduct = product.get("productId");
            }
        }

        List<Map> tradeProductList = new ArrayList<Map>();
        List<Map> tradeProductTypeList = new ArrayList<Map>();
        List<Map> tradeOtherList = new ArrayList<Map>();
        Map tradeUser = Maps.newHashMap();
        List<Map> discntList = new ArrayList<Map>();
        List<Map> svcList = new ArrayList<Map>();
        List<Map> spList = new ArrayList<Map>();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> subItemList = new ArrayList<Map>();
        Map tradePayrelation = Maps.newHashMap();
        productInfoList.addAll(oldProductInfo);
        // 是否变更产品 true-变更,false-不变更
        boolean isChgProduct = !oldProduct.equals(newProduct);

        // // 组合版使用新的产品逻辑 by wangmc 20170309 isShare=false:组合版 FIXME
        // if (!(Boolean) msg.get("isShare")) {
        // Map tempMap = MapUtils.asMap("provinceCode", provinceCode, "serialNumber", serialNumber, "tradeStaffId",
        // tradeStaffId, "departId", departId, "acceptDate", acceptDate, "nextMon1stDay", nextMon1stDay,
        // "userId", userId, "custId", custId, "acctId", acctId, "useCustId", useCustId, "cycleId", cycleId,
        // "brandCode", brandCode, "userDiffCode", userDiffCode, "custName", custName, "linkName", linkName,
        // "linkAddress", linkAddress, "linkPhone", linkPhone, "eparchyCode", eparchyCode, "cityCode",
        // cityCode, "newProductId", newProductId, "combAdvance", combAdvance);
        //
        // Map phoneRecomInfo = (Map) phoneTemplate.get("phoneRecomInfo");
        // tempMap.put("phoneRecomInfo", phoneRecomInfo);
        // System.out.println("=====融合组合版纳入新产品处理逻辑调用前msg:" + msg);
        // msgContainer = new MixChangeProduct4GProcessor().newDealProduct(msgContainer, msg, tempMap, userRsp,
        // productInfoList, tradeProductList, tradeProductTypeList, tradeOtherList, tradeUser, discntList,
        // svcList, spList, itemList, subItemList, tradePayrelation, oldProducts);
        // baseObject = (Map) msgContainer.get("base");
        // System.out.println("=====融合组合版纳入新产品处理逻辑调用后msg:" + msg);
        // }
        // else if ((Boolean) msg.get("isShare")) {
        System.out.println("zzcmixtesta1:");

        // 处理移网纳入场景订购活动 RBJ2018072600033 zhaok
        List<Map> activityInfo = (List<Map>) phoneTemplate.get("activityInfo");
        if (!IsEmptyUtils.isEmpty(activityInfo)) {
            Map activeMsg = MapUtils.asMap("activityInfo", activityInfo, "eparchyCode", eparchyCode);
            Map extactInfo = TradeManagerUtils.newPreProductInfo(new ArrayList<Map>(), provinceCode, activeMsg);
            // 将活动信息放入对应的节点中
            preActiveInfoData(extactInfo, discntList, svcList, spList, tradeProductTypeList, tradeProductList,
                    activeMsg);
            ELKAopLogger.logStr("test001处理移网纳入订购活动的信息： ext " + extactInfo + ", msg:" + activeMsg);
        }
        for (int i = 0; i < productInfoList.size(); i++) {
            Map productInfo = productInfoList.get(i);
            String strBrandCode = "";
            String strProductTypeCode = "";
            String strProductMode = "";
            String commodityId = "";
            Object productId = "";
            String firsMonBillMode = "02";
            String lengthType = "N";
            String optType = (String) productInfo.get("optType");
            String oldProductId = (String) productInfo.get("productId");
            String monthFirstDay = "";
            String monthLasttDay = "";
            if (UNBOOK.equals(optType) && isChgProduct) // 退订老产品
            {
                // --------------------------------------产品信息--------------------------------------
                List<Map> productInfoSet = null;
                commodityId = oldProductId; // 用老的产品id
                if (MAIN.equals(productInfo.get("productMode"))) {
                    productInfoSet = ProductManagerUtils.getProductInfoWithLimit(commodityId, "00", provinceCode,
                            eparchyCode, firsMonBillMode, msg.get("combAdvance") + "");
                    if (CollectionUtils.isEmpty(productInfoSet)) {
                        throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品信息");
                    }
                }

                String subId = String.valueOf(productInfoSet.get(0).get("COMMODITY_ID"));// 防止产品是attr表导致productId为-1
                String subProductMode = (String) productInfoSet.get(0).get("PRODUCT_MODE");
                List<Map> subproductItemSet = ProductManagerUtils.getProductItemWithoutPtype(subId, commodityId,
                        provinceCode);
                if (CollectionUtils.isEmpty(subproductItemSet)) {
                    throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品属性表【" + commodityId + "】的产品信息");
                }
                String subProductTypeCode = TradeManagerUtils.getValueFromItem("PRODUCT_TYPE_CODE", subproductItemSet);
                String subBrandCode = TradeManagerUtils.getValueFromItem("BRAND_CODE", subproductItemSet);
                monthFirstDay = acceptDate;
                brandCode = subBrandCode;
                monthLasttDay = GetDateUtils.getMonthLastDay().substring(0, 10) + " 23:59:59";
                // String monthLasttDay14 = Utility.transDate(monthFirstDay, 14);
                // String realStartDate14 = Utility.transDate(realStartDate, 14);
                // 取原产品的生效时间
                for (int m = 0; m < oldProducts.size(); m++) {
                    Map pInfo = oldProducts.get(m);
                    if (subId.equals(pInfo.get("productId"))) {
                        monthFirstDay = (String) pInfo.get("productActiveTime");
                        monthFirstDay = GetDateUtils.transDate(monthFirstDay, 19);
                    }
                }

                // 附属产品台帐
                Map subProductItem = new HashMap();
                subProductItem.put("userId", userId);
                subProductItem.put("productMode", subProductMode);
                subProductItem.put("productId", subId);
                // subProductItem.put("PRODUCT_TYPE_CODE", subProductTypeCode);
                subProductItem.put("brandCode", subBrandCode);
                subProductItem.put("itemId", TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID"));
                subProductItem.put("modifyTag", "1");
                subProductItem.put("startDate", monthFirstDay);
                subProductItem.put("endDate", monthLasttDay);
                subProductItem.put("userIdA", "-1");
                subProductItem.put("xDatatype", "NULL");
                tradeProductList.add(subProductItem);

                // 活动台帐

                Map productTypeItem = new HashMap();
                productTypeItem.put("userId", userId);
                productTypeItem.put("productMode", subProductMode);
                productTypeItem.put("productId", subId);
                productTypeItem.put("productTypeCode", subProductTypeCode);
                productTypeItem.put("modifyTag", "1");
                productTypeItem.put("startDate", monthFirstDay);
                productTypeItem.put("endDate", monthLasttDay);
                productTypeItem.put("xDatatype", "NULL");

                tradeProductTypeList.add(productTypeItem);

                // TRADE_OTHER
                Map tradeOtherTemp = new HashMap();
                tradeOtherTemp.put("xDatatype", "NULL");
                tradeOtherTemp.put("modifyTag", "1");
                tradeOtherTemp.put("rsrvStr1", subId);// 原产品ID
                tradeOtherTemp.put("rsrvStr2", subProductMode);// 原产品类型 00 主产品
                tradeOtherTemp.put("rsrvStr3", "-9");
                tradeOtherTemp.put("rsrvStr4", subProductTypeCode);
                tradeOtherTemp.put("rsrvStr5", subProductTypeCode);
                tradeOtherTemp.put("rsrvStr6", "-1");
                tradeOtherTemp.put("rsrvStr7", "0");
                tradeOtherTemp.put("rsrvStr8", "");
                tradeOtherTemp.put("rsrvStr9", subBrandCode);// BRAND code
                tradeOtherTemp.put("rsrvStr10", serialNumber);// 号码
                tradeOtherTemp.put("rsrvValueCode", "NEXP"); // 这个字段是啥
                tradeOtherTemp.put("rsrvValue", userId); // USER_ID
                tradeOtherTemp.put("startDate", monthFirstDay);// 原产品生效时间
                tradeOtherTemp.put("endDate", monthLasttDay);// 本月最后一天最后一秒
                tradeOtherList.add(tradeOtherTemp);
            }
            if (BOOK.equals(optType)) // 新增产品
            {
                String startDate = nextMon1stDay;
                commodityId = (String) productInfo.get("productId");
                List<Map> productInfoSet = null;
                if (MAIN.equals(productInfo.get("productMode")) && isChgProduct) {
                    startDate = nextMon1stDay; // 主产品下月生效
                    if ("1".equals(combAdvance)) {
                        productInfoSet = ProductManagerUtils.getProductInfoWithLimit(commodityId, "00", provinceCode,
                                eparchyCode, firsMonBillMode, combAdvance);
                    }
                    else {

                        productInfoSet = ProductManagerUtils.getProductInfoWithLimit(commodityId, "00", provinceCode,
                                eparchyCode, firsMonBillMode);
                    }
                    if (productInfoSet == null || productInfoSet.size() == 0) {
                        throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品信息");
                    }
                    newProductId = commodityId;
                    strProductMode = (String) productInfoSet.get(0).get("PRODUCT_MODE");
                    productId = productInfoSet.get(0).get("COMMODITY_ID");// 组合优化版资费cbss对应产品id是-1，改用commodity_id
                }
                if (SUB.equals(productInfo.get("productMode"))) {
                    startDate = acceptDate; // 附家产品立即效
                    monthLasttDay = "2050-12-31 23:59:59";
                    // 修改附加产品失效时间 by fxm
                    lengthType = TradeManagerUtils.getEndDate(commodityId);
                    if ("N,X".contains(lengthType)) {
                        Map productDate = TradeManagerUtils.getEffectTime(commodityId, startDate, monthLasttDay);
                        monthFirstDay = (String) productDate.get("monthFirstDay");
                        monthLasttDay = (String) productDate.get("monthLasttDay");
                    }
                    productInfoSet = ProductManagerUtils.getProductAndPtypeProuct(commodityId);
                    if (productInfoSet == null || productInfoSet.size() == 0) {
                        throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + commodityId + "】的产品信息");
                    }
                    strProductMode = (String) productInfoSet.get(0).get("PRODUCT_MODE");
                    productId = productInfoSet.get(0).get("PRODUCT_ID");
                    productInfoSet = ProductManagerUtils.getProductInfoWithLimit(commodityId, "01", provinceCode,
                            eparchyCode, firsMonBillMode);
                }
                if (null != productInfoSet && productInfoSet.size() > 0) {
                    // 100M 与42M选择
                    // List<Map> svcInfoList = (List<Map>) userInfo.get("svcInfo");
                    // productInfoSet = chooseSpeedByUser(productInfoSet, svcInfoList);
                    productInfoSet = ProductManagerUtils.chooseSpeed(productInfoSet, msg.get("speed").toString());
                    // 预处理一部分产品信息
                    productInfoSet = preDealProductInfo(productInfoSet, productId, provinceCode, userRsp);
                }
                // 获取Td_b_Commodity_Trans_Item产品属性信息
                if (IsEmptyUtils.isEmpty(productId)) {
                    productId = newProduct;
                }
                List<Map> productItemSet = ProductManagerUtils.getItemByPid(productId, commodityId, provinceCode, "U");

                if (productItemSet == null || productItemSet.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品映射属性表中未查询到产品ID【" + productId + "】的产品属性信息");
                }

                strBrandCode = TradeManagerUtils.getValueFromItem("BRAND_CODE", productItemSet);
                strProductTypeCode = TradeManagerUtils.getValueFromItem("PRODUCT_TYPE_CODE", productItemSet);
                if (strBrandCode == "" || strProductTypeCode == "") {
                    throw new EcAopServerBizException("9999", "在产品映射属性表中未查询到产品ID【" + productId + "】的产品品牌或者活动类型信息");
                }
                brandCode = strBrandCode;
                // 如果有附加包
                List<Map> packageElementList = (List<Map>) productInfo.get("packageElement");
                if (CollectionUtils.isNotEmpty(packageElementList)) {
                    ArrayList<String> eleList = new ArrayList<String>();
                    for (int k = 0; k < packageElementList.size(); k++) {
                        Map peBuf = packageElementList.get(k);
                        String elmId = (String) peBuf.get("elementId");
                        eleList.add(elmId);
                    }
                    List<Map> appendProduct = ProductManagerUtils.getAppendProductByEleList(commodityId, "01",
                            eparchyCode, provinceCode, eleList);
                    if (appendProduct != null && appendProduct.size() > 0) {
                        for (int j = 0; j < appendProduct.size(); j++) {
                            if ("D".equals(appendProduct.get(j).get("ELEMENT_TYPE_CODE"))) {
                                Map dis = new HashMap();
                                dis.put("productId", appendProduct.get(j).get("PRODUCT_ID"));
                                dis.put("packageId", appendProduct.get(j).get("PACKAGE_ID"));
                                dis.put("discntCode", appendProduct.get(j).get("ELEMENT_ID"));
                                discntList.add(dis);
                            }
                            if ("S".equals(appendProduct.get(j).get("ELEMENT_TYPE_CODE"))) {
                                Map svc = new HashMap();
                                svc.put("serviceId", appendProduct.get(j).get("ELEMENT_ID"));
                                svc.put("productId", appendProduct.get(j).get("PRODUCT_ID"));
                                svc.put("packageId", appendProduct.get(j).get("PACKAGE_ID"));
                                svcList.add(svc);
                            }
                            if ("X".equals(appendProduct.get(j).get("ELEMENT_TYPE_CODE"))) {
                                String spId = "-1";
                                String partyId = "-1";
                                String spProductId = "-1";
                                Map spItemParam = new HashMap();
                                spItemParam.put("PTYPE", "X");
                                spItemParam.put("COMMODITY_ID", commodityId);
                                spItemParam.put("PROVINCE_CODE", provinceCode);
                                spItemParam.put("PID", appendProduct.get(j).get("ELEMENT_ID"));
                                List<Map> spItemInfoResult = dao.queryProductItemInfo(spItemParam);
                                if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                    throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                            + appendProduct.get(j).get("ELEMENT_ID") + "】的产品属性信息");
                                }
                                for (int l = 0; l < spItemInfoResult.size(); l++) {
                                    Map spItemInfo = spItemInfoResult.get(l);
                                    if ("SP_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                        spId = (String) (spItemInfo.get("ATTR_VALUE"));
                                    }
                                    else if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                        partyId = (String) (spItemInfo.get("ATTR_VALUE"));
                                    }
                                    else if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                        spProductId = (String) (spItemInfo.get("ATTR_VALUE"));
                                    }
                                }
                                Map sp = new HashMap();
                                sp.put("productId", appendProduct.get(j).get("PRODUCT_ID"));
                                sp.put("packageId", appendProduct.get(j).get("PACKAGE_ID"));
                                sp.put("partyId", partyId);
                                sp.put("spId", spId);
                                sp.put("spProductId", spProductId);
                                sp.put("spServiceId", appendProduct.get(j).get("ELEMENT_ID"));
                                spList.add(sp);
                            }
                        }
                    }
                }

                // 产品台帐
                // 如果是主产品变更产品或者是附加产品,需要拼装产品节点 by wangmc 20170412
                if ((MAIN.equals(productInfo.get("productMode")) && isChgProduct)
                        || SUB.equals(productInfo.get("productMode"))) {
                    Map productItem = new HashMap();
                    productItem.put("userId", userId);
                    productItem.put("productMode", strProductMode);
                    productItem.put("productId", productId);
                    // productItem.put("PRODUCT_TYPE_CODE", strProductTypeCode);
                    productItem.put("brandCode", strBrandCode);
                    productItem.put("itemId", TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID"));
                    productItem.put("modifyTag", "0");
                    productItem.put("startDate", startDate);
                    productItem.put("endDate", "2050-12-31 23:59:59");
                    productItem.put("userIdA", "-1");
                    productItem.put("xDatatype", "NULL");
                    tradeProductList.add(productItem);

                    if (MAIN.equals(productInfo.get("productMode"))) {
                        Map userItem = new HashMap();
                        userItem.put("userId", userId);
                        userItem.put("productId", productId);
                        userItem.put("brandCode", strBrandCode);
                        userItem.put("netTypeCode", "0050");
                        userItem.put("xDatatype", "NULL");
                        tradeUser.put("item", userItem);
                    }

                    // 活动台帐
                    Map productTypeItem = new HashMap();
                    productTypeItem.put("userId", userId);
                    productTypeItem.put("productMode", strProductMode);
                    productTypeItem.put("productId", productId);
                    productTypeItem.put("productTypeCode", strProductTypeCode);
                    productTypeItem.put("modifyTag", "0");
                    productTypeItem.put("startDate", startDate);
                    productTypeItem.put("endDate", "2050-12-31 23:59:59");
                    productTypeItem.put("xDatatype", "NULL");
                    tradeProductTypeList.add(productTypeItem);
                }
                // 拼装产品默认属性
                if (!IsEmptyUtils.isEmpty(productInfoSet)) {
                    for (int j = 0; j < productInfoSet.size(); j++) {
                        if ("D".equals(productInfoSet.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map dis = new HashMap();
                            dis.put("productId", productInfoSet.get(j).get("PRODUCT_ID"));
                            dis.put("packageId", productInfoSet.get(j).get("PACKAGE_ID"));
                            dis.put("discntCode", productInfoSet.get(j).get("ELEMENT_ID"));
                            discntList.add(dis);
                        }
                        if ("S".equals(productInfoSet.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map svc = new HashMap();
                            svc.put("serviceId", productInfoSet.get(j).get("ELEMENT_ID"));
                            svc.put("productId", productInfoSet.get(j).get("PRODUCT_ID"));
                            svc.put("packageId", productInfoSet.get(j).get("PACKAGE_ID"));
                            svcList.add(svc);
                        }
                        if ("X".equals(productInfoSet.get(j).get("ELEMENT_TYPE_CODE"))) {
                            String spId = "-1";
                            String partyId = "-1";
                            String spProductId = "-1";
                            Map spItemParam = new HashMap();
                            spItemParam.put("PTYPE", "X");
                            spItemParam.put("COMMODITY_ID", commodityId);
                            spItemParam.put("PROVINCE_CODE", provinceCode);
                            spItemParam.put("PID", productInfoSet.get(j).get("ELEMENT_ID"));
                            List<Map> spItemInfoResult = dao.queryProductItemInfo(spItemParam);
                            if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                        + productInfoSet.get(j).get("ELEMENT_ID") + "】的产品属性信息");
                            }
                            for (int l = 0; l < spItemInfoResult.size(); l++) {
                                Map spItemInfo = spItemInfoResult.get(l);
                                if ("SP_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                    spId = (String) (spItemInfo.get("ATTR_VALUE"));
                                }
                                else if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                    partyId = (String) (spItemInfo.get("ATTR_VALUE"));
                                }
                                else if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                    spProductId = (String) (spItemInfo.get("ATTR_VALUE"));
                                }
                            }
                            Map sp = new HashMap();
                            sp.put("productId", productInfoSet.get(j).get("PRODUCT_ID"));
                            sp.put("packageId", productInfoSet.get(j).get("PACKAGE_ID"));
                            sp.put("partyId", partyId);
                            sp.put("spId", spId);
                            sp.put("spProductId", spProductId);
                            sp.put("spServiceId", productInfoSet.get(j).get("ELEMENT_ID"));
                            spList.add(sp);
                        }
                    }// END of for
                }
            }
        }

        msg.put("acceptDate", acceptDate);
        msg.put("nextMon1stDay", nextMon1stDay);
        msg.put("userId", userId);
        msg.put("custId", custId);
        msg.put("acctId", acctId);
        msg.put("useCustId", useCustId);
        msg.put("cycleId", cycleId);
        msg.put("brandCode", brandCode);
        msg.put("userDiffCode", userDiffCode);
        msg.put("custName", custName);
        msg.put("linkName", linkName);
        msg.put("linkPhone", linkPhone);
        msg.put("linkAddress", linkAddress);
        msg.put("eparchyCode", eparchyCode);
        msg.put("cityCode", cityCode);
        msg.put("newProductId", newProductId);
        msg.put("provinceCode", provinceCode);
        msg.put("tradeStaffId", tradeStaffId);
        msg.put("departId", departId);
        msg.put("serialNumber", serialNumber);

        msgContainer.putAll(msg);

        // 拼装Base
        baseObject.putAll(dealBase(msgContainer));

        // appendMap相关
        appendMap.put("E_IN_MODE", (String) msg.get("eModeCode"));
        appendMap.put("ALONE_TCS_COMP_INDEX", String.valueOf(msg.get("aloneTcsCompIndex")));
        appendMap.put("COMP_DEAL_STATE", String.valueOf(msg.get("compDealState")));
        Map phoneRecomInfo = (Map) phoneTemplate.get("phoneRecomInfo");
        if (null != phoneRecomInfo && phoneRecomInfo.size() > 0) {
            appendMap.put("recomPerName", (String) phoneRecomInfo.get("recomPersonName"));
            appendMap.put("DEVELOP_STAFF_ID", (String) phoneRecomInfo.get("recomPersonId"));
            appendMap.put("DEVELOP_DEPART_ID", (String) phoneRecomInfo.get("recomDepartId"));
        }
        // 行销标示处理
        if ("1".equals(msg.get("markingTag"))) {
            appendMap.put("MARKING_APP", "1");
        }

        Map appParam = new HashMap();
        appParam.put("TRADE_TYPE_CODE", "10");// 融合业务，纳入
        appParam.put("NET_TYPE_CODE", "ZZ");// 默认ZZ
        appParam.put("BRAND_CODE", "ZZZZ");// 默认ZZZZ
        appParam.put("PRODUCT_ID", "-1");// 默认-1
        appParam.put("EPARCHY_CODE", "ZZZZ");// 默认ZZZZ
        appParam.put("PROVINCE_CODE", "ZZZZ");// 默认ZZZZ

        List<Map> appendMapResult = dao.queryAppendParam(appParam);
        for (int n = 0; n < appendMapResult.size(); n++) {
            String attrTypeCode = String.valueOf(appendMapResult.get(n).get("ATTR_TYPE_CODE"));
            String attrValue = String.valueOf(appendMapResult.get(n).get("ATTR_VALUE"));
            String attrcode = String.valueOf(appendMapResult.get(n).get("ATTR_CODE"));
            for (String key : appendMap.keySet()) {
                if (attrValue.endsWith(key) && "I".equals(attrTypeCode)) {
                    Map item = new HashMap();
                    item.put("xDatatype", "NULL");
                    item.put("attrValue", appendMap.get(key));
                    item.put("attrCode", attrcode);
                    itemList.add(item);
                }
                if (attrValue.endsWith(key) && !"I".equals(attrTypeCode)) {
                    Map item = new HashMap();
                    item.put("attrTypeCode", attrTypeCode);
                    item.put("xDatatype", "NULL");
                    item.put("itemId", msgContainer.get("userId"));
                    item.put("attrValue", appendMap.get(key));
                    item.put("attrCode", attrcode);
                    subItemList.add(item);
                }
            }
        }
        msg.put("subItemList", subItemList);
        msg.put("discntList", discntList);
        msg.put("svcList", svcList);
        msg.put("spList", spList);
        msg.put("itemList", itemList);
        msg.put("tradeUser", tradeUser);
        msg.put("productList", tradeProductList);
        msg.put("productTypeList", tradeProductTypeList);
        msg.put("tradeOtherList", tradeOtherList);
        msg.put("tradePayrelation", tradePayrelation);
        msgContainer.putAll(msg);

        // Map tradeMap = Maps.newHashMap();
        msg.put("base", baseObject);
        msg.put("smnoMethodCode", exchange.getMethodCode());// 移网周转金纳入
        msg.put("ext", preExtDataforItem(msgContainer));
        ELKAopLogger.logStr("test002 处理完所有参数后的报文为: " + msg);
        System.out.println("打桩测试001" + msg + "msgContainer=" + msgContainer);
        // }
        // msg.put("trade", tradeMap);
        msg.put("ordersId", msg.get("subscribeId"));
        msg.put("serinalNamber", serialNumber);
        msg.put("serviceClassCode", "00CP");
        msg.put("operTypeCode", "0");
        return msg;
    }

    /**
     * 将活动信息放入对应的节点中
     * @param extactInfo
     * @param discntList
     * @param svcList
     * @param spList
     * @param productTypeList
     * @param productList
     * @param activeMsg
     */
    private void preActiveInfoData(Map extactInfo, List<Map> discntList, List<Map> svcList, List<Map> spList,
            List<Map> productTypeList, List<Map> productList, Map activeMsg) {
        if (activeMsg.containsKey("discntList")) {
            discntList.addAll((List<Map>) activeMsg.get("discntList"));
        }
        if (activeMsg.containsKey("svcList")) {
            svcList.addAll((List<Map>) activeMsg.get("svcList"));
        }
        if (activeMsg.containsKey("spList")) {
            spList.addAll((List<Map>) activeMsg.get("spList"));
        }
        if (extactInfo.containsKey("productTypeList")) {
            productTypeList.addAll((List<Map>) ((Map) extactInfo.get("productTypeList")).get("item"));
        }
        if (extactInfo.containsKey("productList")) {
            productList.addAll((List<Map>) ((Map) extactInfo.get("productList")).get("item"));
        }

    }

    private Map dealJtcp(Exchange exchange, Map jtcpMap, Map msg) {
        Exchange jtcpExchange = ExchangeUtils.ofCopy(exchange, jtcpMap);
        LanUtils lan = new LanUtils();
        try {
            lan.preData("ecaop.trades.sell.mob.jtcp.ParametersMapping", jtcpExchange);
            CallEngine.wsCall(jtcpExchange, "ecaop.comm.conf.url.cbss.services.OrdForAopthSer");// TODO:地址
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
        msg.put("userIdA", userIdA);
        msg.put("serialNumberA", serialNumberA);
        return msg;
    }

    /**
     * 拼装Base节点
     * @param msg
     * @return
     */
    private Map dealBase(Map msg) {
        // /--------------组织base数据--------
        Map baseObject = Maps.newHashMap();
        baseObject.put("subscribeId", msg.get("subscribeId"));
        baseObject.put("tradeId", msg.get("tradeId"));
        baseObject.put("acceptDate", msg.get("acceptDate"));
        baseObject.put("startDate", msg.get("acceptDate"));
        baseObject.put("endDate", "20151231235959");
        baseObject.put("tradeDepartId", msg.get("departId"));
        baseObject.put("areaCode", msg.get("eparchyCode"));
        baseObject.put("nextDealTag", "Z");
        baseObject.put("olcomTag", "0");
        baseObject.put("inModeCode", msg.get("eModeCode"));
        baseObject.put("tradeStaffId", msg.get("tradeStaffId"));
        baseObject.put("tradeTypeCode", "0340");
        baseObject.put("productId", msg.get("newProductId"));
        baseObject.put("brandCode", msg.get("brandCode"));
        baseObject.put("userId", msg.get("userId"));
        baseObject.put("custId", msg.get("custId"));
        baseObject.put("usecustId", msg.get("useCustId"));
        baseObject.put("acctId", msg.get("acctId"));
        baseObject.put("userDiffCode", "00");
        baseObject.put("netTypeCode", "0050");
        baseObject.put("serinalNamber", msg.get("serialNumber"));
        baseObject.put("custName", msg.get("custName"));
        baseObject.put("termIp", "0.0.0.0");
        baseObject.put("eparchyCode", msg.get("eparchyCode"));
        baseObject.put("tradeCityCode", msg.get("district"));
        baseObject.put("cityCode", msg.get("cityCode"));
        baseObject.put("execTime", msg.get("acceptDate"));
        baseObject.put("operFee", "0"); // 营业费用
        baseObject.put("foregift", "0"); // 押金
        baseObject.put("advancePay", "0"); // 预存
        baseObject.put("feeState", "");
        baseObject.put("feeStaffId", "");
        baseObject.put("cancelTag", "0");
        baseObject.put("checktypeCode", "0");// 身份验证方式
        baseObject.put("chkTag", "0");
        baseObject.put("actorName", "");
        baseObject.put("actorCertTypeId", "");
        baseObject.put("actorPhone", "");
        baseObject.put("actorCertNum", "");
        baseObject.put("contact", msg.get("custName"));
        baseObject.put("contactAddress", msg.get("linkAddress"));
        baseObject.put("contactPhone", msg.get("linkPhone"));
        if (null != msg.get("remark")) {
            baseObject.put("remark", msg.get("remark"));
        }
        else {
            baseObject.put("remark", "");
        }
        return baseObject;
    }

    // 拼装ext节点
    /**
     * @param msg
     * @return
     */
    private Map preExtDataforItem(Map msg) {
        Map ext = new HashMap();
        // ext.put("tradeUser", msg.get("tradeUser")); 海经理提供的报文样例，没有此节点
        ext.put("tradeProductType", preProductTypeListData(msg));
        ext.put("tradeProduct", preProductData(msg));
        ext.put("tradeDiscnt", preDiscntData(msg));
        ext.put("tradeSvc", preTradeSvcData(msg));
        ext.put("tradeSp", preTradeSpData(msg));
        ext.put("tradePayrelation", preTradePayRelData(msg));
        ext.put("tradeItem", preTradeItem(msg));
        ext.put("tradeSubItem", preTradeSubItem(msg));
        ext.put("tradeOther", preTradeOther(msg));

        if ("1".equals(msg.get("groupFlag"))) {
            ext.put("tradeRelation", preJtcpForTradeRelation(msg));
        }
        if ("0".equals(msg.get("mainCardTag")) && "3".equals(msg.get("addType"))) {
            ext.put("tradeRelation", preDataForTradeRelation(msg, ext));
        }
        return ext;
    }

    private Object preJtcpForTradeRelation(Map msg) {
        List<Map> relaItem = new ArrayList<Map>();
        Map item = new HashMap();
        Map tradeRelation = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("relationAttr", "");
        item.put("relationTypeCode", "2222");
        item.put("idA", msg.get("userIdA"));
        item.put("idB", msg.get("userId"));
        item.put("roleCodeA", "0");
        item.put("roleCodeB", "0");
        item.put("orderno", "");
        item.put("shortCode", "");
        item.put("startDate", GetDateUtils.getDate());
        item.put("endDate", "2050-12-31 23:59:59");
        item.put("modifyTag", "0");
        item.put("remark", "");
        item.put("serialNumberA", msg.get("serialNumberA"));
        item.put("serialNumberB", msg.get("serialNumber"));
        item.put("itemId", TradeManagerUtils.getSequence((String) msg.get("eparchyCode"), "SEQ_ITEM_ID"));
        relaItem.add(item);
        tradeRelation.put("tradeRelation", relaItem);
        return tradeRelation;
    }

    // 拼装TRADE_RELATION
    private Map preDataForTradeRelation(Map msg, Map ext) {
        List<Map> tradeRelationOld = new ArrayList<Map>();
        if (ext.containsKey("tradeRelation")) {
            tradeRelationOld = (List<Map>) ((Map) ext.get("tradeRelation")).get("tradeRelation");
        }
        try {
            Map tradeRelation = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            Map item = new HashMap();
            // 主卡开户流程准备参数
            item.put("idA", msg.get("userId"));
            item.put("serialNumberA", msg.get("serialNumber"));
            item.put("roleCodeB", "1");
            item.put("relationAttr", "0");
            item.put("xDatatype", "NULL");
            item.put("relationTypeCode", "ZF");
            item.put("serialNumberB", msg.get("serialNumber"));
            item.put("idB", msg.get("userId"));
            item.put("roleCodeA", "0");
            item.put("modifyTag", "0");
            item.put("startDate", GetDateUtils.getNextMonthDate());
            item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            itemList.add(item);

            Map item2 = new HashMap();
            // 主卡开户流程准备参数
            item2.put("idA", msg.get("XNUserId"));
            item2.put("serialNumberA", msg.get("virtualNumber"));
            item2.put("roleCodeB", "1");
            // item2.put("relationAttr", "0");
            item2.put("xDatatype", "NULL");
            item2.put("relationTypeCode", "8900");
            item2.put("serialNumberB", msg.get("serialNumber"));
            item2.put("idB", msg.get("userId"));
            item2.put("roleCodeA", "0");
            item2.put("modifyTag", "0");
            item2.put("startDate", GetDateUtils.getNextMonthDate());
            item2.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            itemList.add(item2);
            if (!IsEmptyUtils.isEmpty(tradeRelationOld)) {
                itemList.addAll(tradeRelationOld);
            }
            tradeRelation.put("item", itemList);
            return tradeRelation;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_RELATION节点报错" + e.getMessage());
        }

    }

    private Map preTradeOther(Map msg) {
        List<Map> tradeOtherList = (List<Map>) msg.get("tradeOtherList");
        Map tradeOtherItem = new HashMap();
        tradeOtherItem.put("item", tradeOtherList);
        return tradeOtherItem;
    }

    private Map preTradeSubItem(Map msg) {
        List<Map> subItemList = (List<Map>) msg.get("subItemList");
        Map tradeSubItem = new HashMap();
        if ("0".equals(msg.get("mainCardTag")) && "3".equals(msg.get("addType"))) {
            subItemList.add(MapUtils.asMap("xDatatype", "NULL", "attrTypeCode", "0", "attrCode", "MAIN_CARD_TAG",
                    "attrValue", "0", "itemId", msg.get("userId"), "startDate", msg.get("nextMon1stDay"), "endDate",
                    MagicNumber.CBSS_DEFAULT_EXPIRE_TIME));
        }
        tradeSubItem.put("item", subItemList);
        return tradeSubItem;
    }

    private Map preTradeItem(Map msg) {
        List<Map> itemList = (List<Map>) msg.get("itemList");
        if (null == itemList) {
            itemList = new ArrayList<Map>();
        }
        if ("0".equals(msg.get("mainCardTag")) && "3".equals(msg.get("addType"))) {
            itemList.add(MapUtils.asMap("xDatatype", "NULL", "attrCode", "MAIN_CARD_TAG", "attrValue", "0"));
        }
        // RHA2017102300039-关于支撑通过标记控制代理商办理业务时是否扣周转金的需求（移网纳入周转金）
        if ("0".equals(msg.get("deductionTag"))) {
            itemList.add(LanUtils.createTradeItem("FEE_TYPE", "1"));
        }
        // RHA2018010500038-融合业务后激活需求
        // 压单标志为1且为写卡前置接口才生效
        if ("1".equals(msg.get("delayTag")) && "smno".equals(msg.get("smnoMethodCode"))) {
            itemList.add(LanUtils.createTradeItem("E_DELAY_TIME_CEL", "1"));
        }
        // 订单是否同步到电子渠道激活
        if ("1".equals(msg.get("isAfterActivation")) && "smno".equals(msg.get("smnoMethodCode"))) {
            itemList.add(LanUtils.createTradeItem("IS_AFTER_ACTIVATION", "1"));
        }
        Map tradeItem = new HashMap();
        tradeItem.put("item", itemList);
        return tradeItem;
    }

    private Map preTradePayRelData(Map msg) {
        Map payRelItem = new HashMap();
        payRelItem.put("acctId", msg.get("acctId"));
        payRelItem.put("userId", msg.get("userId"));
        payRelItem.put("payrelationId", msg.get("payRelId"));
        payRelItem.put("payitemCode", "-1");
        payRelItem.put("acctPriority", "0");
        payRelItem.put("userPriority", "0");
        payRelItem.put("bindType", "1");
        payRelItem.put("defaultTag", "1");
        payRelItem.put("startAcycId", msg.get("cycleId"));
        payRelItem.put("endAcycId", "205001");
        payRelItem.put("actTag", "1");
        payRelItem.put("limitType", "0");
        payRelItem.put("limit", "0");
        payRelItem.put("complementTag", "0");
        payRelItem.put("addupMethod", "0");
        payRelItem.put("addupMonths", "0");
        payRelItem.put("xDatatype", "NULL");
        Map tradePayrelation = Maps.newHashMap();
        tradePayrelation.put("item", payRelItem);
        return tradePayrelation;
    }

    private Map preProductTypeListData(Map msg) {
        Map tradeProductType = new HashMap();
        List<Map> itemList = (List<Map>) msg.get("productTypeList");
        tradeProductType.put("item", itemList);
        return tradeProductType;

    }

    private Map preProductData(Map msg) {
        Map tradeProduct = new HashMap();
        List<Map> itemList = (List<Map>) msg.get("productList");
        tradeProduct.put("item", itemList);
        return tradeProduct;
    }

    private Map preDiscntData(Map msg) {
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
            item.put("specTag", "1");// FIXME
            item.put("relationTypeCode", "");
            item.put("startDate", msg.get("nextMon1stDay"));
            item.put("endDate", END_OF_WORLD);
            item.put("modifyTag", "0");
            item.put("itemId", TradeManagerUtils.getSequence((String) msg.get("eparchyCode"), "SEQ_ITEM_ID"));
            itemList.add(item);
        }
        tradeDis.put("item", itemList);
        return tradeDis;
    }

    private Map preTradeSvcData(Map msg) {
        Map svcList = new HashMap();
        List<Map> svc = new ArrayList<Map>();
        svc = (List<Map>) msg.get("svcList");
        List svList = new ArrayList();
        for (int i = 0; i < svc.size(); i++) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", msg.get("userId"));
            item.put("serviceId", svc.get(i).get("serviceId"));
            item.put("modifyTag", "0");
            item.put("startDate", msg.get("nextMon1stDay"));
            item.put("endDate", END_OF_WORLD);
            item.put("productId", svc.get(i).get("productId"));
            item.put("packageId", svc.get(i).get("packageId"));
            item.put("itemId", TradeManagerUtils.getSequence((String) msg.get("eparchyCode"), "SEQ_ITEM_ID"));
            item.put("userIdA", "-1");
            svList.add(item);
        }
        svcList.put("item", svList);
        return svcList;
    }

    private Map preTradeSpData(Map msg) {
        Map tardeSp = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> sp = (List<Map>) msg.get("spList");
        for (int i = 0; i < sp.size(); i++) {
            Map item = new HashMap();
            item.put("userId", msg.get("userId"));
            item.put("serialNumber", msg.get("serialNumber"));
            item.put("productId", sp.get(i).get("productId"));
            item.put("packageId", sp.get(i).get("packageId"));
            item.put("partyId", sp.get(i).get("partyId"));
            item.put("spId", sp.get(i).get("spId"));
            item.put("spProductId", sp.get(i).get("spProductId"));
            item.put("firstBuyTime", GetDateUtils.getDate());
            item.put("paySerialNumber", msg.get("serialNumber"));
            item.put("startDate", msg.get("nextMon1stDay"));
            item.put("endDate", END_OF_WORLD);
            item.put("updateTime", GetDateUtils.getDate());
            item.put("remark", "");
            item.put("modifyTag", "0");
            item.put("payUserId", msg.get("userId"));
            item.put("spServiceId", sp.get(i).get("spServiceId"));
            item.put("itemId", TradeManagerUtils.getSequence((String) msg.get("eparchyCode"), "SEQ_ITEM_ID"));
            item.put("userIdA", msg.get("userId"));
            item.put("xDatatype", "NULL");
            itemList.add(item);
        }
        tardeSp.put("item", itemList);
        return tardeSp;
    }

    /***
     * 将数据库里查出短信网龄的默认下移一个月去掉
     * 处理 国际业务
     * @param productInfoSet
     * @param mainProductId
     * @param provinceCode
     * @param userRsp
     * @return
     * @throws Exception
     */
    private List<Map> preDealProductInfo(List<Map> productInfoSet, Object mainProductId, String provinceCode,
            Map userRsp) throws Exception {
        // 将数据库里查出短信网龄的默认下移一个月去掉
        for (int i = 0; i < productInfoSet.size(); i++) {
            Map info = productInfoSet.get(i);
            if ("5702000".equals(info.get("ELEMENT_ID"))) {
                info.put("EFFECTIVE_MODE", "");
                info.put("EFFECTIVE_EXCURSION", "");
            }
        }
        List<Map> svcInfo = (List<Map>) ((List<Map>) userRsp.get("userInfo")).get(0).get("svcInfo");
        List<Map> discntInfo = (List<Map>) ((List<Map>) userRsp.get("userInfo")).get(0).get("discntInfo");

        // 下面特殊处理 国际业务
        // 特服属性信息继承 下面代码主要继承 国际业务
        if (svcInfo != null && svcInfo.size() > 0) {
            for (int k = 0; k < svcInfo.size(); k++) {
                Map sv = svcInfo.get(k);
                String serviceId = (String) sv.get("serviceId");
                if ("50015".equals(serviceId))// 50015国际漫游
                {
                    List<Map> svcInfoSet = ProductManagerUtils.getPkgIdBySvcid(mainProductId, mainProductId,
                            provinceCode, serviceId);// 根据product_id、service_id查找package_id
                    if (svcInfoSet != null && svcInfoSet.size() > 0) {
                        productInfoSet.add(svcInfoSet.get(0));
                        // 剔除国内漫游50014
                        Iterator<Map> it = productInfoSet.iterator();
                        while (it.hasNext()) {
                            Map d = it.next();
                            if ("50014".equals(d.get("ELEMENT_ID"))) {
                                it.remove();
                            }
                        }
                    }
                }

                if ("50011".equals(serviceId))// 50011国际长途
                {
                    List<Map> svcInfoSet = ProductManagerUtils.getPkgIdBySvcid(mainProductId, mainProductId,
                            provinceCode, serviceId);
                    if (svcInfoSet != null && svcInfoSet.size() > 0) {
                        productInfoSet.add(svcInfoSet.get(0));
                        // 剔除国内长途 50010
                        Iterator<Map> it = productInfoSet.iterator();
                        while (it.hasNext()) {
                            Map d = it.next();
                            if ("50010".equals(d.get("ELEMENT_ID"))) {
                                it.remove();
                            }
                        }
                    }
                }
            }
        }
        // end 特殊处理 国际业务

        // 继承部分资费
        // 获得继承资费列表（配表里）
        List<Map> keepDiscnt = TradeManagerUtils.getCommParamSop("CSM", "8541", "ZZZZ", "ZZZZ", "ZZZZ", "ZZZZ");
        if (discntInfo != null && keepDiscnt != null) {
            for (int l = 0; l < keepDiscnt.size(); l++) {
                String keepDis = (String) keepDiscnt.get(l).get("PARA_CODE3");
                for (int k = 0; k < discntInfo.size(); k++) {
                    if (discntInfo.get(k).get("discntCode").equals(keepDis)) {
                        List<Map> keepDisSet = ProductManagerUtils.getPkgIdBySvcid(mainProductId, mainProductId,
                                provinceCode, keepDis);
                        if (keepDisSet != null && keepDisSet.size() > 0) {
                            productInfoSet.add(keepDisSet.get(0));
                        }
                    }
                }
            }
        }

        return productInfoSet;
    }

    // private List<Map> chooseSpeedByUser(List<Map> productInfoSet, List<Map> svcInfoList) {
    // String WCDMAService = "50105"; // CBSS 3G 42M seviceId
    // String LTEService = "50103";// CBSS LTE 100 seviceId
    // boolean flag = true;
    // for (int i = 0; i < svcInfoList.size(); i++)
    // {
    // Map svc = svcInfoList.get(i);
    // if (LTEService.equals(svc.get("serviceId")))
    // {
    // flag = false;
    // return ProductManagerUtils.chooseSpeed(productInfoSet, "100");
    // }
    // if (WCDMAService.equals(svc.get("serviceId")))
    // {
    // flag = false;
    // return ProductManagerUtils.chooseSpeed(productInfoSet, "42");
    // }
    // }
    // if (flag)// 默认选LTE
    // {
    // return ProductManagerUtils.chooseSpeed(productInfoSet, "100");
    // }
    //
    // return productInfoSet;
    // }

    /**
     * 根据号码获取CBSS处三户信息
     * @param exchange
     * @param serialNumber 号码
     * @return
     * @throws Exception
     */
    private Map getInfoByNum4G(Exchange exchange, String serialNumber, ParametersMappingProcessor[] pmp)
            throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        msg.put("serialNumber", serialNumber);
        msg.put("tradeTypeCode", "9999");
        msg.put("serviceClassCode", "0000");
        msg.put("getMode", "1111111111100013010000000100001");
        body.put("msg", msg);
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, msg);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[1], threePartExchange);
        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        Map result = threePartExchange.getOut().getBody(Map.class);
        return result;
    }
}
