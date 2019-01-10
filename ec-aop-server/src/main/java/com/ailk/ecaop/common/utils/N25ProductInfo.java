package com.ailk.ecaop.common.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;

import com.ailk.ecaop.biz.product.DealNewCbssProduct;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;

public class N25ProductInfo implements ProductInfoUtil {

    private static String MAIN_PRODUCT = "1";
    private static String ADD_PRODUCT = "2";
    LanOpenApp4GDao dao = new LanOpenApp4GDao();
    DealNewCbssProduct n25Dao = new DealNewCbssProduct();
    // 去重时用来对比的key
    private static Map<String, String[]> keysMap;

    static {
        keysMap = new HashMap<String, String[]>();
        keysMap.put("discntList", new String[] { "productId", "packageId", "discntCode" });
        keysMap.put("svcList", new String[] { "productId", "packageId", "serviceId" });
        keysMap.put("spList", new String[] { "productId", "packageId", "spServiceId" });
        keysMap.put("productTypeList", new String[] { "productId", "productMode", "productTypeCode" });
        keysMap.put("productList", new String[] { "productId", "productMode", "brandCode" });
    }

    @Override
    public void getProductInfo(Map msg) {
        List<Map> productInfo = new ArrayList<Map>();
        List<Map> activityInfo = new ArrayList<Map>();
        List<Map> paraList = (List<Map>) msg.get("para");// 处理速率和remark
        List<Map> userInfo = (List<Map>) msg.get("userInfo");
        if (null != userInfo && !userInfo.isEmpty()) {
            productInfo = (List<Map>) userInfo.get(0).get("product");
            activityInfo = (List<Map>) userInfo.get(0).get("activityInfo");
        }
        List<Map> resourcesInfo = (List<Map>) msg.get("resourcesInfo");
        // 准备预提交EXT节点参数
        List<Map> productTypeList = new ArrayList<Map>();
        List<Map> productList = new ArrayList<Map>();
        List<Map> discntList = new ArrayList<Map>();
        List<Map> svcList = new ArrayList<Map>();
        List<Map> spList = new ArrayList<Map>();
        String provinceCode = "00" + msg.get("province");
        String eparchyCode = (String) msg.get("eparchyCode");
        String monthFirstDay = GetDateUtils.getDate();
        String monthLasttDay = "20501231235959";
        String isFinalCode = "";
        String mProductId = "";
        String activityStartTime = "";
        String activityEndTime = "";
        String mBrandCode = "";
        String mProductTypeCode = "";
        String tradeUserProductTypeCode = "";
        String purFeeTypeCode = "4310";
        String resourcesCode = "";
        String resourceFee = "";
        String isOwnerPhone = "0";
        String remark = "";
        String isTest = "1";
        // 处理活动信息
        if (null != activityInfo && !activityInfo.isEmpty()) {

            for (int i = 0; i < activityInfo.size(); i++) {
                Map activityMap = activityInfo.get(i);
                List<Map> packageElement = (List<Map>) activityInfo.get(i).get("packageElement");
                if (activityMap.isEmpty()) {
                    continue;
                }
                String actPlanId = (String) activityMap.get("actPlanId");
                if (activityMap.containsKey("resourcesCode")) {
                    resourcesCode = (String) activityMap.get("resourcesCode");
                }
                if (activityMap.containsKey("resourcesFee")) {
                    resourceFee = (String) activityMap.get("resourcesFee");
                }
                if (activityMap.containsKey("isTest")) {
                    isTest = (String) activityMap.get("isTest");
                }
                Map actparam = new HashMap();
                actparam.put("PROVINCE_CODE", provinceCode);
                actparam.put("PRODUCT_MODE", "50");
                actparam.put("PRODUCT_ID", actPlanId);
                actparam.put("EPARCHY_CODE", eparchyCode);
                actparam.put("FIRST_MON_BILL_MODE", null);
                boolean haveDefaultElement = false;
                List<Map> actInfoResult = n25Dao.qryDefaultPackageElement(actparam);
                if (IsEmptyUtils.isEmpty(actInfoResult)) {
                    actInfoResult = n25Dao.qryProductInfoByProductTable(actparam);
                    if (IsEmptyUtils.isEmpty(actInfoResult)) {
                        throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + actPlanId + "】的产品信息");
                    }
                }
                else {
                    haveDefaultElement = true;
                }
                String actProductId = String.valueOf(actInfoResult.get(0).get("PRODUCT_ID"));
                Map productDate = TradeManagerUtils.getEffectTime(actProductId, monthFirstDay, monthLasttDay);
                activityStartTime = (String) productDate.get("monthFirstDay");
                activityEndTime = (String) productDate.get("monthLasttDay");
                String resActivityper = (String) productDate.get("resActivityper");
                if (haveDefaultElement) {
                    for (int j = 0; j < actInfoResult.size(); j++) {
                        // 针对北分卖场，流量，短信，通话三选一，如果是北分卖场且传了附加产品则不处理该产品包的默认结果.by:cuij 2016-12-06
                        if (packageElement != null && packageElement.size() > 0) {
                            Object appCode = msg.get("appCode");
                            String PackageIdFromJSON = (String) packageElement.get(0).get("packageId");
                            String PackageIdFromDB = actInfoResult.get(j).get("PACKAGE_ID") + "";
                            boolean isTure = "mabc".equals(appCode);
                            boolean isSamePackage = PackageIdFromJSON.equals(PackageIdFromDB);
                            System.out.println("两个判断条件的结果分别是" + isTure + "," + isSamePackage);
                            if (isTure && isSamePackage) {
                                continue;
                            }
                        }
                        if ("D".equals(actInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map dis = new HashMap();
                            dis.put("productId", actInfoResult.get(j).get("PRODUCT_ID"));
                            dis.put("packageId", actInfoResult.get(j).get("PACKAGE_ID"));
                            dis.put("discntCode", actInfoResult.get(j).get("ELEMENT_ID"));
                            // 算出活动的开始结束时间，预提交下发
                            Map discntDateMap = TradeManagerUtils.getDiscntEffectTime(
                                    String.valueOf(actInfoResult.get(j).get("ELEMENT_ID")), activityStartTime,
                                    activityEndTime);
                            dis.put("activityStarTime", discntDateMap.get("monthFirstDay"));
                            dis.put("activityTime", discntDateMap.get("monthLasttDay"));
                            discntList.add(dis);
                        }
                        if ("S".equals(actInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map svc = new HashMap();
                            svc.put("serviceId", actInfoResult.get(j).get("ELEMENT_ID"));
                            svc.put("productId", actInfoResult.get(j).get("PRODUCT_ID"));
                            svc.put("packageId", actInfoResult.get(j).get("PACKAGE_ID"));
                            // 算出活动的开始结束时间，预提交下发
                            svc.put("activityStarTime", activityStartTime);
                            svc.put("activityTime", activityEndTime);
                            svcList.add(svc);
                        }
                        if ("X".equals(actInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            String spId = "-1";
                            String partyId = "-1";
                            String spProductId = "-1";
                            Map spItemParam = new HashMap();
                            spItemParam.put("PTYPE", "X");
                            spItemParam.put("PRODUCT_ID", actPlanId);
                            spItemParam.put("PROVINCE_CODE", provinceCode);
                            spItemParam.put("SPSERVICEID", actInfoResult.get(j).get("ELEMENT_ID"));
                            List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                            if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                throw new EcAopServerBizException("9999", "在SP表中未查询到【"
                                        + actInfoResult.get(j).get("ELEMENT_ID") + "】的元素属性信息");
                            }
                            for (int l = 0; l < spItemInfoResult.size(); l++) {
                                Map spItemInfo = spItemInfoResult.get(l);
                                spId = (String) spItemInfo.get("SP_ID");
                                partyId = (String) spItemInfo.get("PARTY_ID");
                                spProductId = (String) spItemInfo.get("SP_PRODUCT_ID");
                            }
                            Map sp = new HashMap();
                            sp.put("productId", actInfoResult.get(j).get("PRODUCT_ID"));
                            sp.put("packageId", actInfoResult.get(j).get("PACKAGE_ID"));
                            sp.put("partyId", partyId);
                            sp.put("spId", spId);
                            sp.put("spProductId", spProductId);
                            sp.put("spServiceId", actInfoResult.get(j).get("ELEMENT_ID"));
                            sp.put("activityStarTime", activityStartTime);
                            sp.put("activityTime", activityEndTime);
                            spList.add(sp);
                        }

                    }
                }

                msg.put("PART_ACTIVE_PRODUCT", actProductId);

                Map actProParam = new HashMap();
                actProParam.put("PRODUCT_ID", actProductId);
                List<Map> actProductInfo = n25Dao.queryProductAndPtypeProduct(actProParam);// 查询产品属性
                if (actProductInfo == null || actProductInfo.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品表或者产品属性表中未查询到产品ID【" + actProductId + "】的产品信息");
                }
                String strProductMode = String.valueOf(actInfoResult.get(0).get("PRODUCT_MODE"));
                String strBrandCode = (String) actProductInfo.get(0).get("BRAND_CODE");
                String strProductTypeCode = (String) actProductInfo.get(0).get("PRODUCT_TYPE_CODE");

                Map productTpye = new HashMap();
                Map product = new HashMap();
                if (!"0".equals(actPlanId)) {

                    productTpye.put("productMode", strProductMode);
                    productTpye.put("productId", actProductId);
                    productTpye.put("productTypeCode", strProductTypeCode);
                    // 算出活动的开始结束时间，预提交下发
                    productTpye.put("activityStarTime", activityStartTime);
                    productTpye.put("activityTime", activityEndTime);

                    product.put("brandCode", strBrandCode);
                    product.put("productId", actProductId);
                    product.put("productMode", strProductMode);
                    // 算出活动的开始结束时间，预提交下发
                    product.put("activityStarTime", activityStartTime);
                    product.put("activityTime", activityEndTime);

                    // 如果有附加包
                    if (packageElement != null && packageElement.size() > 0) {
                        for (int n = 0; n < packageElement.size(); n++) {
                            Map peparam = new HashMap();
                            peparam.put("PROVINCE_CODE", provinceCode);
                            peparam.put("PRODUCT_ID", actPlanId);
                            peparam.put("EPARCHY_CODE", eparchyCode);
                            peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                            peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                            List<Map> packageElementInfo = n25Dao.queryPackageElementInfo(peparam);
                            if (packageElementInfo != null && packageElementInfo.size() > 0) {
                                for (int c = 0; c < packageElementInfo.size(); c++) {

                                    if ("D".equals(packageElementInfo.get(c).get("ELEMENT_TYPE_CODE"))) {
                                        Map dis = new HashMap();
                                        dis.put("productId", packageElementInfo.get(c).get("PRODUCT_ID"));
                                        dis.put("packageId", packageElementInfo.get(c).get("PACKAGE_ID"));
                                        dis.put("discntCode", packageElementInfo.get(c).get("ELEMENT_ID"));
                                        Map discntDateMap = TradeManagerUtils.getDiscntEffectTime(
                                                String.valueOf(packageElementInfo.get(c).get("ELEMENT_ID")),
                                                activityStartTime, activityEndTime);
                                        dis.put("activityStarTime", discntDateMap.get("monthFirstDay"));
                                        dis.put("activityTime", discntDateMap.get("monthLasttDay"));
                                        discntList.add(dis);
                                    }
                                    if ("S".equals(packageElementInfo.get(c).get("ELEMENT_TYPE_CODE"))) {
                                        Map svc = new HashMap();
                                        svc.put("serviceId", packageElementInfo.get(c).get("ELEMENT_ID"));
                                        svc.put("productId", packageElementInfo.get(c).get("PRODUCT_ID"));
                                        svc.put("packageId", packageElementInfo.get(c).get("PACKAGE_ID"));
                                        // 算出活动的开始结束时间，预提交下发
                                        svc.put("activityStarTime", activityStartTime);
                                        svc.put("activityTime", activityEndTime);
                                        svcList.add(svc);
                                    }
                                    if ("X".equals(packageElementInfo.get(c).get("ELEMENT_TYPE_CODE"))) {
                                        String spId = "-1";
                                        String partyId = "-1";
                                        String spProductId = "-1";
                                        Map spItemParam = new HashMap();
                                        spItemParam.put("PTYPE", "X");
                                        spItemParam.put("PRODUCT_ID", actPlanId);
                                        spItemParam.put("PROVINCE_CODE", provinceCode);
                                        spItemParam.put("SPSERVICEID", packageElementInfo.get(c).get("ELEMENT_ID"));
                                        List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                                        if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                            throw new EcAopServerBizException("9999", "在SP表中未查询到【"
                                                    + packageElementInfo.get(c).get("ELEMENT_ID") + "】的元素属性信息");
                                        }
                                        for (int j = 0; j < spItemInfoResult.size(); j++) {
                                            Map spItemInfo = spItemInfoResult.get(j);
                                            spId = (String) spItemInfo.get("SP_ID");
                                            partyId = (String) spItemInfo.get("PARTY_ID");
                                            spProductId = (String) spItemInfo.get("SP_PRODUCT_ID");
                                        }
                                        Map sp = new HashMap();
                                        sp.put("productId", packageElementInfo.get(c).get("PRODUCT_ID"));
                                        sp.put("packageId", packageElementInfo.get(c).get("PACKAGE_ID"));
                                        sp.put("partyId", partyId);
                                        sp.put("spId", spId);
                                        sp.put("spProductId", spProductId);
                                        sp.put("spServiceId", packageElementInfo.get(c).get("ELEMENT_ID"));
                                        sp.put("activityStarTime", activityStartTime);
                                        sp.put("activityTime", activityEndTime);
                                        spList.add(sp);
                                    }

                                }
                            }

                        }

                    }

                    productTypeList.add(productTpye);
                    productList.add(product);

                }

                for (int j = 0; j < actInfoResult.size(); j++) {
                    Map subProductInfo = actInfoResult.get(j);
                    if ("A".equals(subProductInfo.get("ELEMENT_TYPE_CODE"))) {
                        String packageId = String.valueOf(subProductInfo.get("PACKAGE_ID"));
                        String elementDiscntId = String.valueOf(subProductInfo.get("ELEMENT_ID"));

                        // 对终端信息进行预占
                        Object bipType = userInfo.get(0).get("bipType");
                        isOwnerPhone = bipType.equals("6") ? "1" : "0";
                        if (bipType.equals("2") || "6".equals(bipType) || "0".equals(actPlanId)) {
                            Map rescMap = resourcesInfo.get(0);// 3ge终端接口返回信息
                            String salePrice = ""; // 销售价格（单位：厘）
                            if (StringUtils.isNotEmpty((String) rescMap.get("salePrice"))) {
                                salePrice = (String) rescMap.get("salePrice");
                                salePrice = String.valueOf(Integer.parseInt(salePrice) / 10);
                            }
                            String resCode = resourcesCode;// 资源唯一标识
                            String resBrandCode = (String) rescMap.get("resourcesBrandCode"); // 品牌
                            String resBrandName = ""; // 终端品牌名称
                            if (StringUtils.isEmpty((String) rescMap.get("resourcesBrandName"))) {
                                resBrandName = "无说明";
                            }
                            else {
                                resBrandName = (String) rescMap.get("resourcesBrandName");
                            }
                            String resModelCode = (String) rescMap.get("resourcesModelCode"); // 型号
                            String resModeName = ""; // 终端型号名称
                            if (StringUtils.isEmpty((String) rescMap.get("resourcesModelName"))) {
                                resModeName = "无说明";
                            }
                            else {
                                resModeName = (String) rescMap.get("resourcesModelName");
                            }
                            String machineTypeCode = "";// 终端机型编码
                            if (StringUtils.isEmpty((String) rescMap.get("machineTypeCode"))) {
                                machineTypeCode = "无说明";
                            }
                            else {
                                machineTypeCode = (String) rescMap.get("machineTypeCode");
                            }
                            String orgdeviceBrandCode = "";
                            if (StringUtils.isNotEmpty((String) rescMap.get("orgDeviceBrandCode"))) {
                                orgdeviceBrandCode = (String) rescMap.get("orgDeviceBrandCode");// 3GESS维护品牌，当iphone时品牌与上面的一致
                            }
                            String cost = ""; // 成本价格（单位：厘）
                            if (StringUtils.isNotEmpty((String) rescMap.get("cost"))) {
                                cost = (String) rescMap.get("cost");
                                cost = String.valueOf(Integer.parseInt(cost) / 10);
                            }
                            String machineTypeName = ""; // 终端机型名称
                            if (StringUtils.isEmpty((String) rescMap.get("machineTypeName"))) {
                                machineTypeName = "无说明";
                            }
                            else {
                                machineTypeName = (String) rescMap.get("machineTypeName");
                            }
                            String terminalSubtype = "";
                            if (StringUtils.isNotEmpty((String) rescMap.get("terminalTSubType"))) {
                                terminalSubtype = (String) rescMap.get("terminalTSubType");
                            }
                            String terminalType = (String) rescMap.get("terminalType");// 终端类别编码
                            if (!"0".equals(actPlanId)) {
                                List<Map> elementList = new ArrayList<Map>();
                                Map elemntItem1 = new HashMap();
                                elemntItem1.put("userId", msg.get("userId"));
                                elemntItem1.put("productId", actProductId);
                                elemntItem1.put("packageId", packageId);
                                elemntItem1.put("idType", "C");
                                elemntItem1.put("id", elementDiscntId);
                                elemntItem1.put("modifyTag", "0");
                                elemntItem1.put("startDate", activityStartTime);
                                elemntItem1.put("endDate", activityEndTime);
                                elemntItem1.put("modifyTag", "0");
                                elemntItem1.put("itemId", TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID"));
                                elemntItem1.put("userIdA", "-1");
                                elemntItem1.put("xDatatype", "NULL");
                                elementList.add(elemntItem1);
                                msg.put("elementList", elementList);// 下发tradeElement节点
                            }
                            String purchaseItemId = TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID");
                            List<Map> tradeSubItem = new ArrayList<Map>();
                            tradeSubItem.add(LanUtils.createTradeSubItemH("deviceType",
                                    decodeTerminalType(terminalType), purchaseItemId));
                            tradeSubItem.add(LanUtils.createTradeSubItemH("deviceno", machineTypeCode, purchaseItemId));
                            tradeSubItem.add(LanUtils.createTradeSubItemH("devicebrand", orgdeviceBrandCode,
                                    purchaseItemId));
                            tradeSubItem.add(LanUtils.createTradeSubItemH("deviceintag", resBrandName, purchaseItemId));
                            tradeSubItem.add(LanUtils.createTradeSubItemH("mobilecost",
                                    String.valueOf(Integer.parseInt(cost) / 100), purchaseItemId));
                            tradeSubItem.add(LanUtils
                                    .createTradeSubItemH("mobileinfo", machineTypeName, purchaseItemId));
                            tradeSubItem.add(LanUtils.createTradeSubItemH("mobilesaleprice",
                                    String.valueOf(Integer.parseInt(salePrice) / 100), purchaseItemId));
                            tradeSubItem.add(LanUtils.createTradeSubItemH("resActivityper", resActivityper,
                                    purchaseItemId));
                            // 因为活动处理提前，partActiveProduct为mProductId，但为空，现只从传参里取，先不查库了
                            String partActiveProduct = "";
                            if (productInfo != null && productInfo.size() > 0) {
                                for (int k = 0; k < productInfo.size(); k++) {
                                    String productMode = (String) productInfo.get(k).get("productMode");
                                    if (MAIN_PRODUCT.equals(productMode)) {
                                        partActiveProduct = (String) productInfo.get(k).get("productId");
                                    }
                                }
                            }
                            tradeSubItem.add(LanUtils.createTradeSubItemH("partActiveProduct", partActiveProduct,
                                    purchaseItemId));
                            tradeSubItem.add(LanUtils.createTradeSubItemH("resourcesBrandCode", resBrandCode,
                                    purchaseItemId));
                            tradeSubItem.add(LanUtils.createTradeSubItemH("resourcesBrandName", resBrandName,
                                    purchaseItemId));
                            tradeSubItem.add(LanUtils.createTradeSubItemH("resourcesModelCode", resModelCode,
                                    purchaseItemId));
                            tradeSubItem.add(LanUtils.createTradeSubItemH("resourcesModelName", resModeName,
                                    purchaseItemId));
                            tradeSubItem.add(LanUtils.createTradeSubItemH("SALE_PRODUCT_LIST", strProductTypeCode,
                                    purchaseItemId));

                            if (StringUtils.isNotEmpty(terminalSubtype)) // 有时为空
                            {
                                tradeSubItem.add(LanUtils.createTradeSubItemH("terminalTSubType", terminalSubtype,
                                        purchaseItemId));
                            }
                            tradeSubItem
                                    .add(LanUtils.createTradeSubItemH("terminalType", terminalType, purchaseItemId));
                            tradeSubItem.add(LanUtils.createTradeSubItemH("isOwnerPhone", isOwnerPhone.toString(),
                                    purchaseItemId));
                            tradeSubItem.add(LanUtils.createTradeSubItemH("isPartActive", "0", purchaseItemId));
                            tradeSubItem.add(LanUtils.createTradeSubItemH("holdUnitType", "01", purchaseItemId));
                            tradeSubItem.add(LanUtils.createTradeSubItemH("resourcesType", "07", purchaseItemId));
                            tradeSubItem.add(LanUtils.createTradeSubItemH("packageType", "10", purchaseItemId));
                            tradeSubItem.add(LanUtils.createTradeSubItemH("itemid", purchaseItemId, purchaseItemId));

                            msg.put("subItemList", tradeSubItem);// 下发tradeSubItem节点

                            // tf_b_trade_purchase表的台账
                            List<Map> tradePurchaseList = new ArrayList<Map>();
                            Map tradePurchase = new HashMap();
                            tradePurchase.put("userId", msg.get("userId"));
                            tradePurchase.put("bindsaleAttr", elementDiscntId);
                            tradePurchase.put("extraDevFee", "");
                            tradePurchase.put("mpfee", resourceFee);
                            tradePurchase.put("feeitemCode", purFeeTypeCode);
                            tradePurchase.put("foregift", "0");
                            tradePurchase.put("foregiftCode", "-1");
                            tradePurchase.put("foregiftBankmod", "");
                            tradePurchase.put("agreementMonths", resActivityper);
                            tradePurchase.put("endMode", "N");
                            tradePurchase.put("deviceType", machineTypeCode);
                            tradePurchase.put("moblieCost", cost);
                            tradePurchase.put("deviceName", resModeName);
                            tradePurchase.put("deviceBrand", orgdeviceBrandCode);
                            tradePurchase.put("imei", resCode);
                            tradePurchase.put("listBank", "");
                            tradePurchase.put("listFee", "");
                            tradePurchase.put("listCode", "");
                            tradePurchase.put("creditOrg", "");
                            tradePurchase.put("creditType", "");
                            tradePurchase.put("creditCardNum", "");
                            tradePurchase.put("agreement", "");
                            tradePurchase.put("productId", actProductId);
                            tradePurchase.put("packgaeId", packageId);
                            tradePurchase.put("staffId", msg.get("operatorId"));
                            tradePurchase.put("departId", msg.get("channelId"));
                            tradePurchase.put("startDate", activityStartTime);
                            tradePurchase.put("endDate", activityEndTime);
                            tradePurchase.put("remark", "");
                            tradePurchase.put("itemId", purchaseItemId);
                            tradePurchase.put("xDatatype", "NULL");
                            tradePurchaseList.add(tradePurchase);
                            msg.put("tradePurchaseList", tradePurchaseList);
                        }
                    } // END IF A
                }

            }

        }
        // 处理产品信息开始
        if (productInfo != null && productInfo.size() > 0) {
            for (int i = 0; i < productInfo.size(); i++) {
                String firstMonBillMode = null == userInfo.get(0).get("firstMonBillMode") ? "02" : (String) userInfo
                        .get(0).get("firstMonBillMode");
                String addProStartDay = "";
                String addProEndDay = "";
                String productId = (String) productInfo.get(i).get("productId");
                String productMode = (String) productInfo.get(i).get("productMode");
                List<Map> packageElement = (List<Map>) productInfo.get(i).get("packageElement");
                if (MAIN_PRODUCT.equals(productMode)) {

                    // 百度大小神卡需要下发首月付费模式为1的 by wangmc 20170301
                    String productIds = EcAopConfigLoader.getStr("ecaop.global.param.product.replace");
                    if (productIds != null && productIds.contains(productId)) {
                        firstMonBillMode = "01";
                    }

                    Map productTpye = new HashMap();
                    Map product = new HashMap();

                    String strBrandCode = "";
                    String strProductTypeCode = "";
                    String strProductMode = "";

                    Map proparam = new HashMap();
                    proparam.put("PROVINCE_CODE", provinceCode);
                    proparam.put("PRODUCT_ID", productId);
                    proparam.put("EPARCHY_CODE", eparchyCode);
                    proparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                    proparam.put("PRODUCT_MODE", "00");
                    List<Map> productInfoResult = n25Dao.qryDefaultPackageElement(proparam);
                    System.out.println("szyprod22" + productInfoResult);
                    if (productInfoResult == null || productInfoResult.size() == 0) {
                        // throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + productId + "】的产品信息");
                        // 未查询到产品的默认资费或服务,不报错,去TD_B_PRODUCT表查询,产品不存在就抛错,存在继续执行 by wangmc 20170321
                        List<Map> productInfoList = n25Dao.qryProductInfoByProductTable(proparam);
                        if (!IsEmptyUtils.isEmpty(productInfoList)) {
                            productId = String.valueOf(productInfoList.get(0).get("PRODUCT_ID"));
                            strProductMode = (String) productInfoList.get(0).get("PRODUCT_MODE");
                        }
                        else {
                            throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + productId + "】的产品信息");
                        }
                    }
                    else {
                        productId = String.valueOf(productInfoResult.get(0).get("PRODUCT_ID"));
                        strProductMode = (String) productInfoResult.get(0).get("PRODUCT_MODE");
                    }
                    mProductId = productId;
                    Map itparam = new HashMap();
                    itparam.put("PROVINCE_CODE", provinceCode);
                    itparam.put("PRODUCT_ID", productId);
                    List<Map> productItemInfoResult = n25Dao.queryProductAndPtypeProduct(itparam);
                    if (productItemInfoResult == null || productItemInfoResult.size() == 0) {
                        throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + productId + "】的产品属性信息");
                    }

                    // 选速率,默认42M
                    String speed = "";
                    if (null != paraList && !paraList.isEmpty()) {
                        for (Map para : paraList) {
                            if ("SPEED".equalsIgnoreCase((String) para.get("paraId"))) {
                                speed = (String) para.get("paraValue");
                            }
                            if ("REMARK".equalsIgnoreCase((String) para.get("paraId"))) {
                                remark = (String) para.get("paraValue");
                            }
                        }
                    }
                    // 选速率
                    // productInfoResult = chooseSpeed(productInfoResult, speed);
                    NewProductUtils utils = new NewProductUtils();
                    // 新的速率处理方法
                    productInfoResult = utils.chooseSpeed(productInfoResult, speed);// TODO 速率修改
                    // TODO:剔除特定服务
                    msg.put("remark", remark);

                    // 需要按偏移处理时间的资费
                    List<String> specialDiscnt = n25Dao.querySpealDealDiscnt(MapUtils.asMap("SELECT_FLAG",
                            "SPACIL_DISCNT_4GOPEN%"));
                    System.out.println("特殊处理的资费,specialProductDis:" + specialDiscnt);
                    for (int j = 0; j < productInfoResult.size(); j++) {
                        if ("D".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map dis = new HashMap();
                            dis.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                            dis.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                            dis.put("discntCode", productInfoResult.get(j).get("ELEMENT_ID"));
                            // 根据配置判断是否处理主资费的开始时间和结束时间by wangmc 20170310
                            if (utils.dealMainProDiscntDate(dis, specialDiscnt, productId, "N25")) {
                                continue;
                            }
                            discntList.add(dis);
                        }
                        if ("S".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map svc = new HashMap();
                            svc.put("serviceId", productInfoResult.get(j).get("ELEMENT_ID"));
                            svc.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                            svc.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                            svcList.add(svc);
                        }
                        if ("X".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            String spId = "-1";
                            String partyId = "-1";
                            String spProductId = "-1";
                            Map spItemParam = new HashMap();
                            spItemParam.put("PTYPE", "X");
                            spItemParam.put("COMMODITY_ID", productId);
                            spItemParam.put("PROVINCE_CODE", provinceCode);
                            spItemParam.put("SPSERVICEID", productInfoResult.get(j).get("ELEMENT_ID"));
                            List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                            if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                throw new EcAopServerBizException("9999", "在SP表中未查询到【"
                                        + productInfoResult.get(j).get("ELEMENT_ID") + "】的元素属性信息");
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
                            spList.add(sp);
                        }
                    }

                    mProductTypeCode = strProductMode;
                    strBrandCode = (String) productItemInfoResult.get(0).get("BRAND_CODE");
                    mBrandCode = strBrandCode;
                    strProductTypeCode = (String) productItemInfoResult.get(0).get("PRODUCT_TYPE_CODE");
                    tradeUserProductTypeCode = strProductTypeCode;

                    productTpye.put("productMode", strProductMode);
                    productTpye.put("productId", productId);
                    productTpye.put("productTypeCode", strProductTypeCode);
                    product.put("brandCode", strBrandCode);
                    product.put("productId", productId);
                    product.put("productMode", strProductMode);

                    productTypeList.add(productTpye);
                    productList.add(product);
                    msg.put("mBrandCode", mBrandCode);
                    msg.put("mProductId", mProductId);
                    msg.put("mProductTypeCode", mProductTypeCode);
                    msg.put("tradeUserProductTypeCode", tradeUserProductTypeCode);

                    // 附加包
                    if (packageElement != null && packageElement.size() > 0) {
                        for (int n = 0; n < packageElement.size(); n++) {
                            Map peparam = new HashMap();
                            peparam.put("PROVINCE_CODE", provinceCode);
                            peparam.put("PRODUCT_ID", productId);
                            peparam.put("EPARCHY_CODE", eparchyCode);
                            peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                            peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                            List<Map> packageElementInfo = n25Dao.queryPackageElementInfo(peparam);
                            if (packageElementInfo != null && packageElementInfo.size() > 0) {
                                for (int a = 0; a < packageElementInfo.size(); a++) {

                                    if ("D".equals(packageElementInfo.get(a).get("ELEMENT_TYPE_CODE"))) {
                                        Map dis = new HashMap();
                                        dis.put("productId", packageElementInfo.get(a).get("PRODUCT_ID"));
                                        dis.put("packageId", packageElementInfo.get(a).get("PACKAGE_ID"));
                                        dis.put("discntCode", packageElementInfo.get(a).get("ELEMENT_ID"));
                                        // 根据配置判断是否处理主资费的开始时间和结束时间by wangmc 20170310
                                        if (utils.dealMainProDiscntDate(dis, specialDiscnt, productId, "N25")) {
                                            continue;
                                        }
                                        discntList.add(dis);
                                    }
                                    if ("S".equals(packageElementInfo.get(a).get("ELEMENT_TYPE_CODE"))) {
                                        Map svc = new HashMap();
                                        svc.put("serviceId", packageElementInfo.get(a).get("ELEMENT_ID"));
                                        svc.put("productId", packageElementInfo.get(a).get("PRODUCT_ID"));
                                        svc.put("packageId", packageElementInfo.get(a).get("PACKAGE_ID"));
                                        svcList.add(svc);
                                    }
                                    if ("X".equals(packageElementInfo.get(a).get("ELEMENT_TYPE_CODE"))) {
                                        String spId = "-1";
                                        String partyId = "-1";
                                        String spProductId = "-1";
                                        Map spItemParam = new HashMap();
                                        spItemParam.put("PRODUCT_ID", productId);
                                        spItemParam.put("PROVINCE_CODE", provinceCode);
                                        spItemParam.put("SPSERVICEID", packageElementInfo.get(a).get("ELEMENT_ID"));
                                        List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                                        if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                            throw new EcAopServerBizException("9999", "在SP表中未查询到【"
                                                    + packageElementInfo.get(a).get("ELEMENT_ID") + "】的元素属性信息");
                                        }
                                        for (int j = 0; j < spItemInfoResult.size(); j++) {
                                            Map spItemInfo = spItemInfoResult.get(j);
                                            spId = (String) spItemInfo.get("SP_ID");
                                            partyId = (String) spItemInfo.get("PARTY_ID");
                                            spProductId = (String) spItemInfo.get("SP_PRODUCT_ID");
                                        }
                                        Map sp = new HashMap();
                                        sp.put("productId", packageElementInfo.get(a).get("PRODUCT_ID"));
                                        sp.put("packageId", packageElementInfo.get(a).get("PACKAGE_ID"));
                                        sp.put("partyId", partyId);
                                        sp.put("spId", spId);
                                        sp.put("spProductId", spProductId);
                                        sp.put("spServiceId", packageElementInfo.get(a).get("ELEMENT_ID"));
                                        spList.add(sp);
                                    }

                                }
                            }
                        }
                    }

                }
                else if (ADD_PRODUCT.equals(productMode)) {

                    Map productTpye = new HashMap();
                    Map product = new HashMap();

                    String addProductId = "";
                    String strBrandCode = "";
                    String strProductTypeCode = "";
                    String strProductMode = "";
                    // 针对融合新装主副卡，支撑语音翻倍赠送
                    if ("0".equals(msg.get("mainCardTag")) && "3".equals(msg.get("addType")) && "-1".equals(productId)) {
                        if (packageElement != null && packageElement.size() > 0) {
                            for (int n = 0; n < packageElement.size(); n++) {
                                if ("D".equals(packageElement.get(n).get("elementType"))
                                        && "-1".equals(packageElement.get(n).get("packageId"))) {
                                    Map dis = new HashMap();
                                    dis.put("productId", "-1");
                                    dis.put("packageId", "-1");
                                    dis.put("discntCode", String.valueOf(packageElement.get(n).get("elementId")));
                                    dis.put("activityStarTime", GetDateUtils.getDate());
                                    dis.put("activityTime", "2040-12-31 23:59:59");
                                    discntList.add(dis);
                                }
                            }
                        }
                    }
                    else {
                        Map addproparam = new HashMap();
                        addproparam.put("PROVINCE_CODE", provinceCode);
                        addproparam.put("PRODUCT_ID", productId);
                        addproparam.put("EPARCHY_CODE", eparchyCode);
                        addproparam.put("PRODUCT_MODE", "01");
                        addproparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                        List<Map> addproductInfoResult = n25Dao.qryDefaultPackageElement(addproparam);

                        if (addproductInfoResult == null || addproductInfoResult.size() == 0) {
                            // 未查询到附加产品的默认资费或服务,不报错,去TD_B_PRODUCT表查询,产品不存在就抛错,存在继续执行 by wangmc 20170302
                            List<Map> productInfoList = n25Dao.qryProductInfoByProductTable(addproparam);
                            if (!IsEmptyUtils.isEmpty(productInfoList)) {
                                addProductId = String.valueOf(productInfoList.get(0).get("PRODUCT_ID"));
                                strProductMode = (String) productInfoList.get(0).get("PRODUCT_MODE");
                            }
                            else {
                                throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + productId + "】的产品信息");
                            }
                        }
                        else {
                            addProductId = String.valueOf(addproductInfoResult.get(0).get("PRODUCT_ID"));
                            strProductMode = (String) addproductInfoResult.get(0).get("PRODUCT_MODE");
                        }

                        // isFinalCode = TradeManagerUtils.getEndDate(addProductId);
                        isFinalCode = NewProductUtils.getEndDateType(addProductId);// cuij FIXME
                        if ("N".equals(isFinalCode) || "X".equals(isFinalCode)) {
                            Map productDate = TradeManagerUtils.getEffectTime(addProductId, monthFirstDay,
                                    monthLasttDay);
                            addProStartDay = (String) productDate.get("monthFirstDay");
                            addProEndDay = (String) productDate.get("monthLasttDay");
                        }
                        else if ("Y".equals(isFinalCode)) {
                            if (StringUtils.isEmpty(activityEndTime)) {
                                throw new EcAopServerBizException("9999", "所选附加产品" + productId + "生失效时间需要和合约保持一致，"
                                        + "请检查合约信息是否已传或更换附加产品信息");
                            }
                            addProStartDay = activityStartTime;
                            addProEndDay = activityEndTime;
                        }
                        for (int j = 0; j < addproductInfoResult.size(); j++) {

                            if ("D".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                                String elementId = String.valueOf(addproductInfoResult.get(j).get("ELEMENT_ID"));
                                Map dis = new HashMap();
                                dis.put("productId", addproductInfoResult.get(j).get("PRODUCT_ID"));
                                dis.put("packageId", addproductInfoResult.get(j).get("PACKAGE_ID"));
                                dis.put("discntCode", addproductInfoResult.get(j).get("ELEMENT_ID"));
                                if (!"Y".equals(isFinalCode)) {
                                    Map discntDateMap = TradeManagerUtils.getDiscntEffectTime(elementId,
                                            addProStartDay, addProEndDay);
                                    dis.put("activityStarTime", discntDateMap.get("monthFirstDay"));
                                    dis.put("activityTime", discntDateMap.get("monthLasttDay"));
                                }
                                else {
                                    dis.put("activityStarTime", addProStartDay);
                                    dis.put("activityTime", addProEndDay);
                                }
                                discntList.add(dis);
                            }
                            else if ("S".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                                Map svc = new HashMap();
                                svc.put("serviceId", addproductInfoResult.get(j).get("ELEMENT_ID"));
                                svc.put("productId", addproductInfoResult.get(j).get("PRODUCT_ID"));
                                svc.put("packageId", addproductInfoResult.get(j).get("PACKAGE_ID"));
                                svc.put("activityStarTime", addProStartDay);
                                svc.put("activityTime", addProEndDay);
                                svcList.add(svc);
                            }
                            else if ("X".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                                String spId = "-1";
                                String partyId = "-1";
                                String spProductId = "-1";
                                Map spItemParam = new HashMap();
                                spItemParam.put("PROVINCE_CODE", provinceCode);
                                spItemParam.put("SPSERVICEID", addproductInfoResult.get(j).get("ELEMENT_ID"));
                                List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                                if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                    throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【"
                                            + addproductInfoResult.get(j).get("ELEMENT_ID") + "】的产品属性信息");
                                }
                                for (int l = 0; l < spItemInfoResult.size(); l++) {
                                    Map spItemInfo = spItemInfoResult.get(l);
                                    spId = (String) spItemInfo.get("SP_ID");
                                    partyId = (String) spItemInfo.get("PARTY_ID");
                                    spProductId = (String) spItemInfo.get("SP_PRODUCT_ID");
                                }
                                Map sp = new HashMap();
                                sp.put("productId", addproductInfoResult.get(j).get("PRODUCT_ID"));
                                sp.put("packageId", addproductInfoResult.get(j).get("PACKAGE_ID"));
                                sp.put("partyId", partyId);
                                sp.put("spId", spId);
                                sp.put("spProductId", spProductId);
                                sp.put("spServiceId", addproductInfoResult.get(j).get("ELEMENT_ID"));
                                sp.put("activityStarTime", addProStartDay);
                                sp.put("activityTime", addProEndDay);
                                spList.add(sp);
                            }
                        }

                        Map additparam = new HashMap();
                        additparam.put("PROVINCE_CODE", provinceCode);
                        additparam.put("PRODUCT_ID", productId);
                        List<Map> addProductItemInfoResult = n25Dao.queryProductAndPtypeProduct(additparam);
                        if (addProductItemInfoResult == null || addProductItemInfoResult.size() == 0) {
                            throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + productId + "】的产品属性信息");
                        }

                        strBrandCode = (String) addProductItemInfoResult.get(0).get("BRAND_CODE");
                        strProductTypeCode = (String) addProductItemInfoResult.get(0).get("PRODUCT_TYPE_CODE");
                        productTpye.put("productMode", strProductMode);
                        productTpye.put("productId", addProductId);
                        productTpye.put("productTypeCode", strProductTypeCode);
                        productTpye.put("activityStarTime", addProStartDay);
                        productTpye.put("activityTime", addProEndDay);
                        product.put("activityStarTime", addProStartDay);
                        product.put("activityTime", addProEndDay);
                        product.put("brandCode", strBrandCode);
                        product.put("productId", addProductId);
                        product.put("productMode", strProductMode);

                        productTypeList.add(productTpye);
                        productList.add(product);

                        // 附加包
                        if (packageElement != null && packageElement.size() > 0) {
                            for (int n = 0; n < packageElement.size(); n++) {
                                Map peparam = new HashMap();
                                peparam.put("PROVINCE_CODE", provinceCode);
                                peparam.put("PRODUCT_ID", productId);
                                peparam.put("EPARCHY_CODE", eparchyCode);
                                peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                                peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                                List<Map> packageElementInfo = n25Dao.queryPackageElementInfo(peparam);
                                if (packageElementInfo != null && packageElementInfo.size() > 0) {
                                    for (int b = 0; b < packageElementInfo.size(); b++) {

                                        if ("D".equals(packageElementInfo.get(b).get("ELEMENT_TYPE_CODE"))) {
                                            String elementId = String.valueOf(packageElementInfo.get(b).get(
                                                    "ELEMENT_ID"));
                                            Map dis = new HashMap();
                                            dis.put("productId", packageElementInfo.get(b).get("PRODUCT_ID"));
                                            dis.put("packageId", packageElementInfo.get(b).get("PACKAGE_ID"));
                                            dis.put("discntCode", elementId);
                                            if (ADD_PRODUCT.equals(productMode)) {
                                                if (!"Y".equals(isFinalCode)) {
                                                    Map discntDateMap = TradeManagerUtils.getDiscntEffectTime(
                                                            elementId, addProStartDay, addProEndDay);
                                                    dis.put("activityStarTime", discntDateMap.get("monthFirstDay"));
                                                    dis.put("activityTime", discntDateMap.get("monthLasttDay"));
                                                }
                                                else {
                                                    dis.put("activityStarTime", addProStartDay);
                                                    dis.put("activityTime", addProEndDay);
                                                }
                                            }
                                            discntList.add(dis);
                                        }
                                        if ("S".equals(packageElementInfo.get(b).get("ELEMENT_TYPE_CODE"))) {
                                            Map svc = new HashMap();
                                            svc.put("serviceId", packageElementInfo.get(b).get("ELEMENT_ID"));
                                            svc.put("productId", packageElementInfo.get(b).get("PRODUCT_ID"));
                                            svc.put("packageId", packageElementInfo.get(b).get("PACKAGE_ID"));
                                            svcList.add(svc);
                                        }
                                        if ("X".equals(packageElementInfo.get(b).get("ELEMENT_TYPE_CODE"))) {
                                            String spId = "-1";
                                            String partyId = "-1";
                                            String spProductId = "-1";
                                            Map spItemParam = new HashMap();
                                            spItemParam.put("PROVINCE_CODE", provinceCode);
                                            spItemParam.put("SPSERVICEID", packageElementInfo.get(b).get("ELEMENT_ID"));
                                            List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                                            if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                                throw new EcAopServerBizException("9999", "在SP产品表中未查询到【"
                                                        + packageElementInfo.get(b).get("ELEMENT_ID") + "】的元素属性信息");
                                            }

                                            for (int j = 0; j < spItemInfoResult.size(); j++) {
                                                Map spItemInfo = spItemInfoResult.get(j);
                                                spId = (String) spItemInfo.get("SP_ID");
                                                partyId = (String) spItemInfo.get("PARTY_ID");
                                                spProductId = (String) spItemInfo.get("SP_PRODUCT_ID");
                                            }

                                            Map sp = new HashMap();
                                            sp.put("productId", packageElementInfo.get(b).get("PRODUCT_ID"));
                                            sp.put("packageId", packageElementInfo.get(b).get("PACKAGE_ID"));
                                            sp.put("partyId", partyId);
                                            sp.put("spId", spId);
                                            sp.put("spProductId", spProductId);
                                            sp.put("spServiceId", packageElementInfo.get(b).get("ELEMENT_ID"));
                                            spList.add(sp);
                                        }

                                    }
                                }

                            }
                        }
                    }
                }
            }

        }

        // discntList = newDealRepeat(discntList, "discntList");
        // svcList = newDealRepeat(svcList, "svcList");
        // spList = newDealRepeat(spList, "spList");
        // productTypeList = newDealRepeat(productTypeList, "productTypeList");
        // productList = newDealRepeat(productList, "productList");
        System.out.println("新方法discntList去重之前的:" + discntList);
        msg.put("isTest", isTest);
        msg.put("resourceFee", resourceFee);
        msg.put("isOwnerPhone", isOwnerPhone);
        msg.put("discntList", newDealRepeat(discntList, "discntList"));
        msg.put("svcList", newDealRepeat(svcList, "svcList"));
        msg.put("spList", newDealRepeat(spList, "spList"));
        msg.put("productTypeList", newDealRepeat(productTypeList, "productTypeList"));
        msg.put("productList", newDealRepeat(productList, "productList"));
        System.out.println("新方法discntList去重之后的:" + msg.get("discntList"));
    }

    /**
     * 新的去重方法-wangmc 20170325
     * @param listMap
     * @param keys-比较是否重复的字段名
     *            discntList-资费节点, svcList-服务节点, spList-sp节点
     *            productTypeList-产品类型节点, productList-产品节点
     * @return
     */
    private static List<Map> newDealRepeat(List<Map> listMap, String keys) {
        List<Map> newList = new ArrayList<Map>();
        Map valueMap = new HashMap();
        for (int i = 0; i < listMap.size(); i++) {
            String values = "";
            for (String key : keysMap.get(keys)) { // 将需要对比的值拼起来,当做map的key
                values += listMap.get(i).get(key) + ",";
            }
            if (IsEmptyUtils.isEmpty(valueMap.get(values))) { // 该元素不重复
                valueMap.put(values, "true");
                newList.add(listMap.get(i));
            }
        }
        return newList;
    }

    /**
     * 老的去重方法
     * @param listMap
     * @return
     */
    public static List<Map> dealRepeat(List<Map> listMap) {
        List<Map> listTemp = new ArrayList<Map>();
        Iterator<Map> it = listMap.iterator();
        while (it.hasNext()) {
            Map a = it.next();
            if (listTemp.contains(a)) {
                it.remove();
            }
            else {
                listTemp.add(a);
            }
        }
        return listMap;
    }

    /**
     * 资源类型
     * 输出到北六编码 描述
     * 01 3G手机终端
     * 02 2G手机终端
     * 03 固网话终端（有串号）
     * 04 宽带终端（有串号）
     * 05 上网终端(卡)
     * 06 上网终端(本)
     * 07 其它
     * 08 固话终端（无串号）
     * 09 宽带终端（无串号）
     * 13 互联网增值终端(无串码)
     * 14 互联网增值终端
     * 开户申请传入终端类别编码：
     * Iphone：IP
     * 乐phone：LP
     * 智能终端：PP
     * 普通定制终端：01
     * 上网卡：04
     * 上网本：05
     * @param terminalType
     * @return
     */
    private String decodeTerminalType(String terminalType) {
        if ("04".equals(terminalType)) {
            return "05";
        }
        if ("05".equals(terminalType)) {
            return "06";
        }
        return "01";
    }

    /**
     * 根据商城传入的带宽选 择速率
     * @param productInfoResult
     * @param speed
     * @return
     */
    private List<Map> chooseSpeed(List<Map> productInfoResult, String speed) {
        String LTE = "100";
        String plus4G = "300";

        // 默认下发42M速率，支持传100M和300M两种速率 2016-4-14 lixl
        // 50103(LTE100M) 50105(42M) 50107(4G+ 300M)
        String kick1st = "50103";
        String kick2nd = "50107";
        if (LTE.equals(speed)) {
            kick1st = "50105";
            kick2nd = "50107";
        }
        else if (plus4G.equals(speed)) {
            kick1st = "50103";
            kick2nd = "50105";
        }

        List<Map> newProductInfo = new ArrayList<Map>();
        for (int m = 0; m < productInfoResult.size(); m++) {
            if (kick1st.equals(String.valueOf(productInfoResult.get(m).get("ELEMENT_ID")))
                    || kick2nd.equals(String.valueOf(productInfoResult.get(m).get("ELEMENT_ID")))) {
                newProductInfo.add(productInfoResult.get(m));
            }
        }
        productInfoResult.removeAll(newProductInfo);
        return productInfoResult;
    }

}
