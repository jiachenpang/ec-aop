package com.ailk.ecaop.common.utils;

import com.ailk.ecaop.biz.product.DealNewCbssProduct;
import com.ailk.ecaop.common.helper.MagicNumber;
import org.apache.commons.lang.StringUtils;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 移网23转4新产品流程
 * @author Administrator
 */
public class N25ChgToCbssProduct implements ProductInfoUtil {

    private static String MAIN_PRODUCT = "1";
    private static String ADD_PRODUCT = "2";
    DealNewCbssProduct n25Dao = new DealNewCbssProduct();
    // 去重时用来对比的key
    private static Map<String, String[]> keysMap;

    static {
        keysMap = new HashMap<String, String[]>();
        keysMap.put("discntList", new String[]{"productId", "packageId", "discntCode"});
        keysMap.put("svcList", new String[]{"productId", "packageId", "serviceId"});
        keysMap.put("spList", new String[]{"productId", "packageId", "spServiceId"});
        keysMap.put("productTypeList", new String[]{"productId", "productMode", "productTypeCode"});
        keysMap.put("productList", new String[]{"productId", "productMode", "brandCode"});
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
        String isTest = "1";
        // 处理产品信息开始
        if (productInfo != null && productInfo.size() > 0) {
            for (int i = 0; i < productInfo.size(); i++) {
                if ("1".equals(productInfo.get(i).get("productMode"))) {
                    mProductId = (String) productInfo.get(i).get("productId");
                }
            }
        }
        // 处理活动信息
        if (null != activityInfo && !activityInfo.isEmpty()) {

            for (int i = 0; i < activityInfo.size(); i++) {
                Map activityMap = activityInfo.get(i);
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
                List<Map> actInfoResult = n25Dao.qryDefaultPackageElement(actparam);
                if (actInfoResult == null || actInfoResult.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + actPlanId + "】的产品信息");
                }
                // 活动时间处理
                List<Map> activityList = n25Dao.queryProductAndPtypeProduct(actparam);
                Map activityListMap = null;
                if (null == activityList || activityList.isEmpty()) {
                    throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + actPlanId + "】的产品属性信息");
                }
                if (activityList != null && activityList.size() > 0) {
                    activityListMap = activityList.get(0);
                }

                String resActivityper = String.valueOf(activityListMap.get("END_OFFSET"));
                String enableTag = String.valueOf(activityListMap.get("ENABLE_TAG"));// 0-绝对时间（看开始时间） 1-相对时间
                String strStartUnit = String.valueOf(activityListMap.get("START_UNIT"));// '0:天 1:自然天 2:月 3:自然月 4:年
                // 5:自然年';
                String endUnit = String.valueOf(activityListMap.get("END_UNIT"));
                String startOffset = String.valueOf(activityListMap.get("START_OFFSET"));// 生效偏移时间
                // 如果值为空则进行默认处理
                if (StringUtils.isEmpty(enableTag) || StringUtils.isEmpty(startOffset)
                        || StringUtils.isEmpty(strStartUnit)) {
                    enableTag = "0";
                }

                activityStartTime = GetDateUtils.getSpecifyDateTime(Integer.parseInt(enableTag),
                        Integer.parseInt(strStartUnit), GetDateUtils.getSysdateFormat(),
                        Integer.parseInt(startOffset));
                activityStartTime = GetDateUtils.TransDate(activityStartTime, "yyyy-MM-dd HH:mm:ss");
                activityEndTime = GetDateUtils.getSpecifyDateTime(Integer.parseInt(enableTag),
                        Integer.parseInt(endUnit),
                        GetDateUtils.getSysdateFormat(),
                        Integer.parseInt(resActivityper) + Integer.parseInt(startOffset));
                activityEndTime = GetDateUtils.getSpecifyDateTime(1, 6, activityEndTime, -1); // 结束月最后一天
                activityEndTime = GetDateUtils.TransDate(activityEndTime, "yyyy-MM-dd HH:mm:ss");

                String actProductId = String.valueOf(actInfoResult.get(0).get("PRODUCT_ID"));

                for (int j = 0; j < actInfoResult.size(); j++) {

                    if ("D".equals(actInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                        Map dis = new HashMap();
                        dis.put("productId", actInfoResult.get(j).get("PRODUCT_ID"));
                        dis.put("packageId", actInfoResult.get(j).get("PACKAGE_ID"));
                        dis.put("discntCode", actInfoResult.get(j).get("ELEMENT_ID"));
                        // 算出活动的开始结束时间，预提交下发
                        dis.put("activityStarTime", activityStartTime);
                        dis.put("activityTime", activityEndTime);
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
                msg.put("PART_ACTIVE_PRODUCT", actProductId);

                Map actProParam = new HashMap();
                actProParam.put("PRODUCT_ID", actProductId);
                List<Map> actProductInfo = n25Dao.queryProductAndPtypeProduct(actProParam);
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

                    List<Map> packageElement = (List<Map>) activityInfo.get(0).get("packageElement");
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
                                        // 算出活动的开始结束时间，预提交下发
                                        dis.put("activityStarTime", activityStartTime);
                                        dis.put("activityTime", activityEndTime);
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
                        if ("2".equals(bipType)) {
                            //非自备机终端在正式提交的时候需要做终端实占
                            msg.put("resourcesCode", resourcesCode);
                            msg.put("ACTIVITY_TYPE", strProductTypeCode);
                        }
                        isOwnerPhone = bipType.equals("6") ? "1" : "0";
                        if (bipType.equals("2") || "0".equals(actPlanId)) {
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
                            } else {
                                resBrandName = (String) rescMap.get("resourcesBrandName");
                            }
                            String resModelCode = (String) rescMap.get("resourcesModelCode"); // 型号
                            String resModeName = ""; // 终端型号名称
                            if (StringUtils.isEmpty((String) rescMap.get("resourcesModelName"))) {
                                resModeName = "无说明";
                            } else {
                                resModeName = (String) rescMap.get("resourcesModelName");
                            }
                            String machineTypeCode = "";// 终端机型编码
                            if (StringUtils.isEmpty((String) rescMap.get("machineTypeCode"))) {
                                machineTypeCode = "无说明";
                            } else {
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
                            } else {
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
                                elemntItem1
                                        .put("itemId", TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID"));
                                elemntItem1.put("userIdA", "-1");
                                elemntItem1.put("xDatatype", "NULL");
                                elementList.add(elemntItem1);
                                msg.put("elementList", elementList);// 下发tradeElement节点
                            }
                            String purchaseItemId = TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID");
                            List<Map> tradeSubItem = new ArrayList<Map>();

                            if ("234o".equals(msg.get("23to4methodcode"))) {// 针对23转4开户申请接口修改：tradeSubItem节点传开始和结束时间

                                tradeSubItem.add(LanUtils.createTradeSubItemK("deviceType",
                                        decodeTerminalType(terminalType), purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemK("deviceno", machineTypeCode,
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemK("devicebrand", orgdeviceBrandCode,
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemK("deviceintag", resBrandName,
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemK("mobilecost",
                                        String.valueOf(Integer.parseInt(cost) / 100), purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemK("mobileinfo", machineTypeName,
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemK("mobilesaleprice",
                                        String.valueOf(Integer.parseInt(salePrice) / 100), purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemK("resActivityper", resActivityper,
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemK("partActiveProduct", mProductId,
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemK("resourcesBrandCode", resBrandCode,
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemK("resourcesBrandName", resBrandName,
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemK("resourcesModelCode", resModelCode,
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemK("resourcesModelName", resModeName,
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemK("SALE_PRODUCT_LIST", strProductTypeCode,
                                        purchaseItemId));

                                if (StringUtils.isNotEmpty(terminalSubtype)) // 有时为空
                                {
                                    tradeSubItem.add(LanUtils.createTradeSubItemK("terminalTSubType", terminalSubtype,
                                            purchaseItemId));
                                }
                                tradeSubItem.add(LanUtils.createTradeSubItemK("terminalType", terminalType,
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemK("isOwnerPhone", isOwnerPhone.toString(),
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemK("isPartActive", "0", purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemK("holdUnitType", "01", purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemK("resourcesType", "07", purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemK("packageType", "10", purchaseItemId));
                                tradeSubItem
                                        .add(LanUtils.createTradeSubItemK("itemid", purchaseItemId, purchaseItemId));

                            }
                            else {
                                tradeSubItem.add(LanUtils.createTradeSubItemH("deviceType",
                                        decodeTerminalType(terminalType), purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("deviceno", machineTypeCode,
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("devicebrand", orgdeviceBrandCode,
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("deviceintag", resBrandName,
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("mobilecost",
                                        String.valueOf(Integer.parseInt(cost) / 100), purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("mobileinfo", machineTypeName,
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("mobilesaleprice",
                                        String.valueOf(Integer.parseInt(salePrice) / 100), purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("resActivityper", resActivityper,
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("partActiveProduct", mProductId,
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
                            tradeSubItem.add(LanUtils.createTradeSubItemH("terminalType", terminalType,
                                    purchaseItemId));
                            tradeSubItem.add(LanUtils.createTradeSubItemH("isOwnerPhone", isOwnerPhone.toString(),
                                    purchaseItemId));
                            tradeSubItem.add(LanUtils.createTradeSubItemH("isPartActive", "0", purchaseItemId));
                            tradeSubItem.add(LanUtils.createTradeSubItemH("holdUnitType", "01", purchaseItemId));
                            tradeSubItem.add(LanUtils.createTradeSubItemH("resourcesType", "07", purchaseItemId));
                            tradeSubItem.add(LanUtils.createTradeSubItemH("packageType", "10", purchaseItemId));
                            tradeSubItem
                                    .add(LanUtils.createTradeSubItemH("itemid", purchaseItemId, purchaseItemId));

                            }
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
                // String firstMonBillMode = null == userInfo.get(0).get("firstMonBillMode") ? "02" : (String) userInfo
                // .get(0).get("firstMonBillMode");
                String firstMonBillMode = "02";// 北六老代码中默认02 by wangmc 20170420
                String addProStartDay = "";
                String addProEndDay = "";
                String productId = (String) productInfo.get(i).get("productId");
                String productMode = (String) productInfo.get(i).get("productMode");
                List<Map> packageElement = (List<Map>) productInfo.get(i).get("packageElement");
                if (MAIN_PRODUCT.equals(productMode)) {

                    Map productTpye = new HashMap();
                    Map product = new HashMap();
                    String commodityId = productId;

                    String strBrandCode = "";
                    String strProductTypeCode = "";
                    String strProductMode = "";

                    Map proparam = new HashMap();
                    proparam.put("PROVINCE_CODE", provinceCode);
                    proparam.put("PRODUCT_ID", commodityId);
                    proparam.put("EPARCHY_CODE", eparchyCode);
                    proparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                    proparam.put("PRODUCT_MODE", "00");
                    long start = System.currentTimeMillis();
                    List<Map> productInfoResult = n25Dao.qryDefaultPackageElement(proparam);
                    System.out.println(msg.get("apptx") + "producttime,查询主产品默认元素用时:"
                            + (System.currentTimeMillis() - start));
                    if (productInfoResult == null || productInfoResult.size() == 0) {
                        throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品信息");
                    }
                    start = System.currentTimeMillis();
                    List<Map> productFeeList = n25Dao.qryProductFee(proparam);
                    System.out.println(msg.get("apptx") + "producttime,查询主产品费用用时:"
                            + (System.currentTimeMillis() - start));
                    if (productFeeList == null || productFeeList.size() == 0) {
                        throw new EcAopServerBizException("9999", "在产品原始表中未查询到产品ID【" + commodityId + "】的产品费用信息");
                    }
                    String newProductFee = String.valueOf(productFeeList.get(0).get("NEWPRODUCTFEE"));
                    productId = String.valueOf(productInfoResult.get(0).get("PRODUCT_ID"));
                    mProductId = productId;
                    Map itparam = new HashMap();
                    itparam.put("PROVINCE_CODE", provinceCode);
                    itparam.put("PRODUCT_ID", productId);
                    // List<Map> productItemInfoResult = n25Dao.queryProductAndPtypeProduct(itparam);
                    // if (productItemInfoResult == null || productItemInfoResult.size() == 0) {
                    // throw new EcAopServerBizException("9999", "在产品属性表中未查询到产品ID【" + commodityId + "】的产品属性信息");
                    // }

                    // 选速率,默认42M
                    String speed = "";
                    if (null != paraList && !paraList.isEmpty()) {
                        for (Map para : paraList) {
                            if ("SPEED".equalsIgnoreCase((String) para.get("paraId"))) {
                                speed = (String) para.get("paraValue");
                            }
                        }
                    }
                    // 选速率
                    // productInfoResult = chooseSpeed(productInfoResult, speed);
                    // 新产品速率处理
                    start = System.currentTimeMillis();
                    productInfoResult = new NewProductUtils().chooseSpeed(productInfoResult, speed);// TODO 速率修改
                    System.out.println(msg.get("apptx") + "producttime,处理产品费用用时:"
                            + (System.currentTimeMillis() - start));
                    // 预处理短信网龄产品信息
                    productInfoResult = ProductManagerUtils.preDealProductInfo(productInfoResult);
                    // 预先处理附加包
                    if (packageElement != null && packageElement.size() > 0) {
                        for (int n = 0; n < packageElement.size(); n++) {
                            List<Map> maxList = ProductManagerUtils.getPkgMaxNum(commodityId);// TODO:sql修改
                            List<String> kickList = new ArrayList<String>();

                            for (int k = 0; k < packageElement.size(); k++) {
                                String pkgId = (String) packageElement.get(k).get("packageId");
                                if (null != maxList && !maxList.isEmpty()) {
                                    for (Map mx : maxList) {
                                        if (mx.get("PACKAGE_ID") != null
                                                && pkgId.equals(mx.get("PACKAGE_ID").toString())) {
                                            kickList.add(pkgId);
                                        }
                                    }
                                }
                            }
                            for (int q = 0; q < kickList.size(); q++) {
                                String kickPack = kickList.get(q);
                                Iterator<Map> it = productInfoResult.iterator();
                                while (it.hasNext()) {
                                    Map m = it.next();
                                    if (m.get("PACKAGE_ID") != null && kickPack.equals(m.get("PACKAGE_ID").toString())) {
                                        it.remove();
                                    }
                                }
                            }
                            // 特服属性信息继承 下面代码主要继承 国际业务
                            start = System.currentTimeMillis();
                            List<Map> subServInfos = (List<Map>) msg.get("subServInfos");
                            if (null != subServInfos && !subServInfos.isEmpty()) {
                                Map subServInfoMap = subServInfos.get(0);
                                List<Map> subServList = (List<Map>) subServInfoMap.get("subServInfo");
                                if (null != subServList && !subServList.isEmpty()) {
                                    for (Map subServMap : subServList) {
                                        String servInstid = (String) subServMap.get("servInstid");
                                        if ("50015".equals(servInstid)) {// 50015国际漫游
                                            Map param = new HashMap();
                                            param.put("PRODUCT_ID", productId);
                                            param.put("EPARCHY_CODE", eparchyCode);
                                            param.put("ELEMENT_ID", "50015");
                                            param.put("PROVINCE_CODE", provinceCode);
                                            List<Map> servInstidList = n25Dao.qryPackageElementByElement(param);
                                            productInfoResult.addAll(servInstidList);
                                            // 剔除国内漫游50014
                                            List<Map> removeList = new ArrayList<Map>();
                                            for (Map productInfoMap : productInfoResult) {
                                                // 库里查出来是number类型,得转成String才能比较,下同
                                                if ("50014".equals(productInfoMap.get("ELEMENT_ID") + "")) {
                                                    removeList.add(productInfoMap);
                                                }
                                            }
                                            productInfoResult.removeAll(removeList);
                                        }
                                        if ("50011".equals(servInstid)) {// 50011国际长途
                                            Map param = new HashMap();
                                            param.put("PRODUCT_ID", productId);
                                            param.put("EPARCHY_CODE", eparchyCode);
                                            param.put("ELEMENT_ID", "50011");
                                            param.put("PROVINCE_CODE", provinceCode);
                                            List<Map> servInstidList = n25Dao.qryPackageElementByElement(param);
                                            productInfoResult.addAll(servInstidList);
                                            // 剔除国内漫游50010
                                            List<Map> removeList = new ArrayList<Map>();
                                            for (Map productInfoMap : productInfoResult) {
                                                if ("50010".equals(productInfoMap.get("ELEMENT_ID") + "")) {
                                                    removeList.add(productInfoMap);
                                                }
                                            }
                                            productInfoResult.removeAll(removeList);
                                        }
                                    }
                                }
                            }
                            System.out.println(msg.get("apptx") + "producttime,处理特服继承用时:"
                                    + (System.currentTimeMillis() - start));

                            Map peparam = new HashMap();
                            peparam.put("PROVINCE_CODE", provinceCode);
                            peparam.put("PRODUCT_ID", commodityId);
                            peparam.put("EPARCHY_CODE", eparchyCode);
                            peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                            peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                            start = System.currentTimeMillis();
                            List<Map> packageElementInfo = n25Dao.queryPackageElementInfo(peparam);
                            System.out.println(msg.get("apptx") + "producttime,查询附加包信息用时:"
                                    + (System.currentTimeMillis() - start));
                            if (packageElementInfo != null && packageElementInfo.size() > 0) {
                                for (int a = 0; a < packageElementInfo.size(); a++) {

                                    if ("D".equals(packageElementInfo.get(a).get("ELEMENT_TYPE_CODE"))) {
                                        Map dis = new HashMap();
                                        dis.put("productId", packageElementInfo.get(a).get("PRODUCT_ID"));
                                        dis.put("packageId", packageElementInfo.get(a).get("PACKAGE_ID"));
                                        dis.put("discntCode", packageElementInfo.get(a).get("ELEMENT_ID"));
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
                                        // List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                                        // 修改为新的查询sp的sql
                                        start = System.currentTimeMillis();
                                        List<Map> spItemInfoResult = n25Dao.newQuerySPServiceAttr(spItemParam);
                                        System.out.println(msg.get("apptx") + "producttime,查询sp元素用时:"
                                                + (System.currentTimeMillis() - start));
                                        if (IsEmptyUtils.isEmpty(spItemInfoResult)) {
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

                    start = System.currentTimeMillis();
                    for (int j = 0; j < productInfoResult.size(); j++) {
                        if ("D".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map dis = new HashMap();
                            dis.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                            dis.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                            dis.put("discntCode", productInfoResult.get(j).get("ELEMENT_ID"));
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
                            // List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                            List<Map> spItemInfoResult = n25Dao.newQuerySPServiceAttr(spItemParam);
                            if (IsEmptyUtils.isEmpty(spItemInfoResult)) {
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
                    System.out.println(msg.get("apptx") + "producttime,处理默认元素用时:"
                            + (System.currentTimeMillis() - start));

                    strProductMode = (String) productInfoResult.get(0).get("PRODUCT_MODE");
                    mProductTypeCode = strProductMode;
                    // strBrandCode = (String) productItemInfoResult.get(0).get("BRAND_CODE");
                    strBrandCode = (String) productInfoResult.get(0).get("BRAND_CODE");
                    mBrandCode = strBrandCode;
                    // strProductTypeCode = (String) productItemInfoResult.get(0).get("PRODUCT_TYPE_CODE");
                    strProductTypeCode = (String) productInfoResult.get(0).get("PRODUCT_TYPE_CODE");
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
                    msg.put("newProductFee", newProductFee);// 主产品的费用,23转4调cbss合约查询接口要用到

                } else if (ADD_PRODUCT.equals(productMode)) {

                    Map productTpye = new HashMap();
                    Map product = new HashMap();
                    String commodityId = productId;

                    String addProductId = "";
                    String strBrandCode = "";
                    String strProductTypeCode = "";
                    String strProductMode = "";

                    Map addproparam = new HashMap();
                    addproparam.put("PROVINCE_CODE", provinceCode);
                    addproparam.put("PRODUCT_ID", productId);
                    addproparam.put("EPARCHY_CODE", eparchyCode);
                    addproparam.put("PRODUCT_MODE", "01");
                    addproparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                    List<Map> addproductInfoResult = n25Dao.qryDefaultPackageElement(addproparam);
                    if (addproductInfoResult == null || addproductInfoResult.size() == 0) {
                        throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + productId + "】的产品信息");
                    }
                    addProductId = String.valueOf(addproductInfoResult.get(0).get("PRODUCT_ID"));
                    // isFinalCode = TradeManagerUtils.getEndDate(addProductId);
                    isFinalCode = NewProductUtils.getEndDateType(addProductId);// FIXME
                    if ("N".equals(isFinalCode) || "X".equals(isFinalCode)) {
                        Map productDate = TradeManagerUtils.getEffectTime(addProductId, monthFirstDay,
                                monthLasttDay);
                        addProStartDay = (String) productDate.get("monthFirstDay");
                        addProEndDay = (String) productDate.get("monthLasttDay");
                    } else if ("Y".equals(isFinalCode)) {
                        if (StringUtils.isEmpty(activityEndTime)) {
                            throw new EcAopServerBizException("9999", "所选附加产品" + productId + "生失效时间需要和合约保持一致，" +
                                    "请检查合约信息是否已传或更换附加产品信息");
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
                                        addProStartDay,
                                        addProEndDay);
                                dis.put("activityStarTime", discntDateMap.get("monthFirstDay"));
                                dis.put("activityTime", discntDateMap.get("monthLasttDay"));
                            } else {
                                dis.put("activityStarTime", addProStartDay);
                                dis.put("activityTime", addProEndDay);
                            }
                            discntList.add(dis);
                        } else if ("S".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map svc = new HashMap();
                            svc.put("serviceId", addproductInfoResult.get(j).get("ELEMENT_ID"));
                            svc.put("productId", addproductInfoResult.get(j).get("PRODUCT_ID"));
                            svc.put("packageId", addproductInfoResult.get(j).get("PACKAGE_ID"));
                            svc.put("activityStarTime", addProStartDay);
                            svc.put("activityTime", addProEndDay);
                            svcList.add(svc);
                        } else if ("X".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
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

                    strProductMode = (String) addproductInfoResult.get(0).get("PRODUCT_MODE");
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
                                                        elementId,
                                                        addProStartDay, addProEndDay);
                                                dis.put("activityStarTime", discntDateMap.get("monthFirstDay"));
                                                dis.put("activityTime", discntDateMap.get("monthLasttDay"));
                                            } else {
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
        msg.put("isTest", isTest);
        msg.put("resourceFee", resourceFee);
        msg.put("isOwnerPhone", isOwnerPhone);
        msg.put("discntList", newDealRepeat(discntList, "discntList"));
        msg.put("svcList", newDealRepeat(svcList, "svcList"));
        msg.put("spList", newDealRepeat(spList, "spList"));
        msg.put("productTypeList", newDealRepeat(productTypeList, "productTypeList"));
        msg.put("productList", newDealRepeat(productList, "productList"));

    }

    /**
     * 融合迁转
     */
    public void getMixProductInfo(Map msg, Exchange qryTermExchange,Map extObject) throws Exception {
        Map phoneTemplate = (Map) msg.get("phoneTemplate");
        List<Map> activityInfo = (List<Map>) phoneTemplate.get("activityInfo");
        List<Map> productInfo = (List<Map>) phoneTemplate.get("phoneProduct");
        List<Map> paraList = (List<Map>) msg.get("para");// 处理速率和remark
        // 准备预提交EXT节点参数
        List<Map> productTypeList = new ArrayList<Map>();
        List<Map> productList = new ArrayList<Map>();
        List<Map> discntList = new ArrayList<Map>();
        List<Map> svcList = new ArrayList<Map>();
        List<Map> spList = new ArrayList<Map>();
        String appcode = qryTermExchange.getAppCode();
        String province = (String) msg.get("province");
        String provinceCode = "00" + msg.get("province");
        String eparchyCode = (String) msg.get("eparchyCode");
        String monthFirstDay = GetDateUtils.transDate(GetDateUtils.getNextMonthFirstDayFormat(), 19);
        String monthLasttDay = "20501231235959";
        String isFinalCode = "";
        String mProductId = "";
        // 处理产品信息开始
        if (productInfo != null && productInfo.size() > 0) {
            for (int i = 0; i < productInfo.size(); i++) {
                if ("1".equals(productInfo.get(i).get("productMode"))) {
                    mProductId = (String) productInfo.get(i).get("productId");
                }
            }
        }
        //zsq
        if ("81".contains(province) && "scpr".equals(appcode)) {
            try {
                monthFirstDay = GetDateUtils.transDate(monthFirstDay, 14);
            }
            catch (Exception e) {
                e.printStackTrace();
                throw new EcAopServerBizException("9999", "时间格式转换失败");
            }
        }

        //String activityStartTime = "";
        String activityEndTime = "";
        String mBrandCode = "";
        String mProductTypeCode = "";
        String tradeUserProductTypeCode = "";
        String purFeeTypeCode = "4310";
        String resourcesCode = "";
        String resourceFee = "";
        String isOwnerPhone = "0";
        String isTest = "1";

        // 处理活动信息
        if (null != activityInfo && !activityInfo.isEmpty()) {
            for (int i = 0; i < activityInfo.size(); i++) {
                Map activityMap = activityInfo.get(i);
                isOwnerPhone = null == activityMap.get("isOwnerPhone") ? "0" : "2".equals(activityMap.get("isOwnerPhone")) ? "0" : "1";
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
                List<Map> actInfoResult = n25Dao.qryDefaultPackageElement(actparam);
                if (actInfoResult == null || actInfoResult.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + actPlanId + "】的产品信息");
                }
                String actProductId = String.valueOf(actInfoResult.get(0).get("PRODUCT_ID"));
                Map productDate = TradeManagerUtils.getEffectTimeNextMouthStart(actProductId, monthLasttDay);
                activityEndTime = (String) productDate.get("monthLasttDay");
                String resActivityper = (String) productDate.get("resActivityper");
                for (int j = 0; j < actInfoResult.size(); j++) {
                    if ("D".equals(actInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                        Map dis = new HashMap();
                        dis.put("productId", actInfoResult.get(j).get("PRODUCT_ID"));
                        dis.put("packageId", actInfoResult.get(j).get("PACKAGE_ID"));
                        dis.put("discntCode", actInfoResult.get(j).get("ELEMENT_ID"));
                        // 算出活动的开始结束时间，预提交下发
                        Map discntDateMap = TradeManagerUtils.getDiscntEffectTimeNextMouthStart(String.valueOf(actInfoResult.get(j).get("ELEMENT_ID")),
                                activityEndTime);
                        dis.put("startDate", monthFirstDay);
                        dis.put("endDate", discntDateMap.get("monthLasttDay"));
                        discntList.add(dis);
                    }
                    if ("S".equals(actInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                        Map svc = new HashMap();
                        svc.put("serviceId", actInfoResult.get(j).get("ELEMENT_ID"));
                        svc.put("productId", actInfoResult.get(j).get("PRODUCT_ID"));
                        svc.put("packageId", actInfoResult.get(j).get("PACKAGE_ID"));
                        // 算出活动的开始结束时间，预提交下发
                        svc.put("startDate", monthFirstDay);
                        svc.put("endDate", activityEndTime);
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
                        sp.put("startDate", monthFirstDay);
                        sp.put("endDate", activityEndTime);
                        spList.add(sp);
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
                String bipType = "1";
                if (StringUtils.isNotEmpty(resourcesCode)) {
                    bipType = "2";
                }
                Map productTpye = new HashMap();
                Map product = new HashMap();
                if (!"0".equals(actPlanId)) {

                    productTpye.put("productMode", strProductMode);
                    productTpye.put("productId", actProductId);
                    productTpye.put("productTypeCode", strProductTypeCode);
                    // 算出活动的开始结束时间，预提交下发
                    productTpye.put("startDate", monthFirstDay);
                    productTpye.put("endDate", activityEndTime);

                    product.put("brandCode", strBrandCode);
                    product.put("productId", actProductId);
                    product.put("productMode", strProductMode);
                    // 算出活动的开始结束时间，预提交下发
                    product.put("startDate", monthFirstDay);
                    product.put("endDate", activityEndTime);

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
                                        Map discntDateMap = TradeManagerUtils.getDiscntEffectTimeNextMouthStart(String.valueOf(packageElementInfo.get(c).get("ELEMENT_ID")), activityEndTime);
                                        dis.put("startDate", discntDateMap.get("monthFirstDay"));
                                        dis.put("endDate", discntDateMap.get("monthLasttDay"));
                                        discntList.add(dis);
                                    }
                                    if ("S".equals(packageElementInfo.get(c).get("ELEMENT_TYPE_CODE"))) {
                                        Map svc = new HashMap();
                                        svc.put("serviceId", packageElementInfo.get(c).get("ELEMENT_ID"));
                                        svc.put("productId", packageElementInfo.get(c).get("PRODUCT_ID"));
                                        svc.put("packageId", packageElementInfo.get(c).get("PACKAGE_ID"));
                                        // 算出活动的开始结束时间，预提交下发
                                        svc.put("startDate", monthFirstDay);
                                        svc.put("endDate", activityEndTime);
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
                                        sp.put("startDate", monthFirstDay);
                                        sp.put("endDate", activityEndTime);
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
                        if (bipType.equals("2") || !"0".equals(actPlanId)) {
                            List<Map> resourcesInfo = new QryChkTermUtils().qryChkTerm(qryTermExchange, activityInfo);
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
                            } else {
                                resBrandName = (String) rescMap.get("resourcesBrandName");
                            }
                            String resModelCode = (String) rescMap.get("resourcesModelCode"); // 型号
                            String resModeName = ""; // 终端型号名称
                            if (StringUtils.isEmpty((String) rescMap.get("resourcesModelName"))) {
                                resModeName = "无说明";
                            } else {
                                resModeName = (String) rescMap.get("resourcesModelName");
                            }
                            String machineTypeCode = "";// 终端机型编码
                            if (StringUtils.isEmpty((String) rescMap.get("machineTypeCode"))) {
                                machineTypeCode = "无说明";
                            } else {
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
                            } else {
                                machineTypeName = (String) rescMap.get("machineTypeName");
                            }
                            String terminalSubtype = "";
                            if (StringUtils.isNotEmpty((String) rescMap.get("terminalTSubType"))) {
                                terminalSubtype = (String) rescMap.get("terminalTSubType");
                            }
                            String terminalType = (String) rescMap.get("terminalType");// 终端类别编码
                            if (!"0".equals(actPlanId)) {
                                List<Map> tradeFeeItemList = new ArrayList<Map>();
                                if (StringUtils.isNotEmpty(resourceFee) && !"0".equals(resourceFee)) {
                                    // 传入单位为厘
                                    int fee = Integer.parseInt(resourceFee) / 10;
                                    Map tradeFeeItem = new HashMap();
                                    tradeFeeItem.put("feeMode", "0");
                                    tradeFeeItem.put("feeTypeCode", "4310");
                                    tradeFeeItem.put("oldFee", String.valueOf(fee));
                                    tradeFeeItem.put("fee", String.valueOf(fee));
                                    tradeFeeItem.put("chargeSourceCode", "1");
                                    tradeFeeItem.put("apprStaffId", msg.get("operatorId"));
                                    tradeFeeItem.put("calculateId", msg.get("tradeId"));
                                    tradeFeeItem.put("calculateDate", GetDateUtils.getNextMonthFirstDayFormat());
                                    tradeFeeItem.put("staffId", msg.get("operatorId"));
                                    tradeFeeItem.put("calculateTag", "0");
                                    tradeFeeItem.put("payTag", "0");
                                    tradeFeeItem.put("xDatatype", "NULL");
                                    tradeFeeItemList.add(tradeFeeItem);
                                    extObject.put("tradefeeSub", tradeFeeItemList);
                                }
                                List<Map> elementList = new ArrayList<Map>();
                                Map elemntItem1 = new HashMap();
                                elemntItem1.put("userId", msg.get("userId"));
                                elemntItem1.put("productId", actProductId);
                                elemntItem1.put("packageId", packageId);
                                elemntItem1.put("idType", "C");
                                elemntItem1.put("id", elementDiscntId);
                                elemntItem1.put("modifyTag", "0");
                                elemntItem1.put("startDate", monthFirstDay);
                                elemntItem1.put("endDate", activityEndTime);
                                elemntItem1.put("modifyTag", "0");
                                elemntItem1.put("itemId", TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID"));
                                elemntItem1.put("userIdA", "-1");
                                elemntItem1.put("xDatatype", "NULL");
                                elementList.add(elemntItem1);
                                extObject.put("tradeElement", elementList);// 下发tradeElement节点
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
                            tradeSubItem.add(LanUtils.createTradeSubItemH("partActiveProduct", mProductId,
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

                            extObject.put("tradeSubItem", tradeSubItem);// 下发tradeSubItem节点

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
                            tradePurchase.put("startDate", monthFirstDay);
                            tradePurchase.put("endDate", activityEndTime);
                            tradePurchase.put("remark", "");
                            tradePurchase.put("itemId", purchaseItemId);
                            tradePurchase.put("xDatatype", "NULL");
                            tradePurchaseList.add(tradePurchase);
                            extObject.put("tradePurchase", tradePurchaseList);
                        }
                    } // END IF A
                }

            }

        }

        // 处理产品信息开始
        if (productInfo != null && productInfo.size() > 0) {
            for (int i = 0; i < productInfo.size(); i++) {
                String firstMonBillMode = null == productInfo.get(0).get("firstMonBillMode") ? "02" : (String) productInfo
                        .get(0).get("firstMonBillMode");
                String addProStartDay = "";
                String addProEndDay = "";
                String productId = (String) productInfo.get(i).get("productId");
                String productMode = (String) productInfo.get(i).get("productMode");
                List<Map> packageElement = (List<Map>) productInfo.get(i).get("packageElement");
                if (MAIN_PRODUCT.equals(productMode)) {

                    Map productTpye = new HashMap();
                    Map product = new HashMap();
                    String commodityId = productId;

                    String strBrandCode = "";
                    String strProductTypeCode = "";
                    String strProductMode = "";

                    Map proparam = new HashMap();
                    proparam.put("PROVINCE_CODE", provinceCode);
                    proparam.put("PRODUCT_ID", commodityId);
                    proparam.put("EPARCHY_CODE", eparchyCode);
                    proparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                    proparam.put("PRODUCT_MODE", "00");
                    List<Map> productInfoResult = n25Dao.qryDefaultPackageElement(proparam);
                    if (productInfoResult == null || productInfoResult.size() == 0) {
                        throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品信息");
                    }
                    List<Map> productFeeList = n25Dao.qryProductFee(proparam);
                    if (productFeeList == null || productFeeList.size() == 0) {
                        throw new EcAopServerBizException("9999", "在产品原始表中未查询到产品ID【" + commodityId + "】的产品费用信息");
                    }
                    String newProductFee = String.valueOf(productFeeList.get(0).get("NEWPRODUCTFEE"));
                    productId = String.valueOf(productInfoResult.get(0).get("PRODUCT_ID"));
                    mProductId = productId;
                    Map itparam = new HashMap();
                    itparam.put("PROVINCE_CODE", provinceCode);
                    itparam.put("PRODUCT_ID", productId);
                    List<Map> productItemInfoResult = n25Dao.queryProductAndPtypeProduct(itparam);
                    if (productItemInfoResult == null || productItemInfoResult.size() == 0) {
                        throw new EcAopServerBizException("9999", "在产品属性表中未查询到产品ID【" + commodityId + "】的产品属性信息");
                    }

                    // 选速率,默认42M
                    String speed = "";
                    if (null != paraList && !paraList.isEmpty()) {
                        for (Map para : paraList) {
                            if ("SPEED".equalsIgnoreCase((String) para.get("paraId"))) {
                                speed = (String) para.get("paraValue");
                            }
                        }
                    }
                    // 选速率
                    // productInfoResult = chooseSpeed(productInfoResult, speed);
                    // 新产品速率处理
                    productInfoResult = new NewProductUtils().chooseSpeed(productInfoResult, speed);// TODO 速率修改
                    // 预处理短信网龄产品信息
                    productInfoResult = ProductManagerUtils.preDealProductInfo(productInfoResult);
                    // 预先处理附加包
                    if (packageElement != null && packageElement.size() > 0) {
                        for (int n = 0; n < packageElement.size(); n++) {
                            List<Map> maxList = ProductManagerUtils.getPkgMaxNum(commodityId);// TODO:sql修改
                            List<String> kickList = new ArrayList<String>();

                            for (int k = 0; k < packageElement.size(); k++) {
                                String pkgId = (String) packageElement.get(k).get("packageId");
                                if (null != maxList && !maxList.isEmpty()) {
                                    for (Map mx : maxList) {
                                        if (mx.get("PACKAGE_ID") != null
                                                && pkgId.equals(mx.get("PACKAGE_ID").toString())) {
                                            kickList.add(pkgId);
                                        }
                                    }
                                }
                            }
                            for (int q = 0; q < kickList.size(); q++) {
                                String kickPack = kickList.get(q);
                                Iterator<Map> it = productInfoResult.iterator();
                                while (it.hasNext()) {
                                    Map m = it.next();
                                    if (m.get("PACKAGE_ID") != null && kickPack.equals(m.get("PACKAGE_ID").toString())) {
                                        it.remove();
                                    }
                                }
                            }
                            // 特服属性信息继承 下面代码主要继承 国际业务
                            List<Map> subServInfos = (List<Map>) msg.get("subServInfos");
                            if (null != subServInfos && !subServInfos.isEmpty()) {
                                System.out.println("===================subServInfos"+subServInfos);
                                Map subServInfoMap = subServInfos.get(0);
                                List<Map> subServList = (List<Map>) subServInfoMap.get("subServInfo");
                                if (null != subServList && !subServList.isEmpty()) {
                                    for (Map subServMap : subServList) {
                                        String servInstid = (String) subServMap.get("servInstid");
                                        if ("50015".equals(servInstid)) {// 50015国际漫游
                                            Map param = new HashMap();
                                            param.put("PRODUCT_ID", productId);
                                            param.put("EPARCHY_CODE", eparchyCode);
                                            param.put("ELEMENT_ID", "50015");
                                            param.put("PROVINCE_CODE", provinceCode);
                                            List<Map> servInstidList = n25Dao.qryPackageElementByElement(param);
                                            productInfoResult.addAll(servInstidList);
                                            // 剔除国内漫游50014
                                            List<Map> removeList = new ArrayList<Map>();
                                            for (Map productInfoMap : productInfoResult) {
                                                // 库里查出来是number类型,得转成String才能比较,下同
                                                if ("50014".equals(productInfoMap.get("ELEMENT_ID") + "")) {
                                                    removeList.add(productInfoMap);
                                                }
                                            }
                                            productInfoResult.removeAll(removeList);
                                        }
                                        if ("50011".equals(servInstid)) {
                                            // 50011国际长途
                                            Map param = new HashMap();
                                            param.put("PRODUCT_ID", productId);
                                            param.put("EPARCHY_CODE", eparchyCode);
                                            param.put("ELEMENT_ID", "50011");
                                            param.put("PROVINCE_CODE", provinceCode);
                                            List<Map> servInstidList = n25Dao.qryPackageElementByElement(param);
                                            productInfoResult.addAll(servInstidList);
                                            // 剔除国内漫游50010
                                            List<Map> removeList = new ArrayList<Map>();
                                            for (Map productInfoMap : productInfoResult) {
                                                if ("50010".equals(productInfoMap.get("ELEMENT_ID") + "")) {
                                                    removeList.add(productInfoMap);
                                                }
                                            }
                                            productInfoResult.removeAll(removeList);
                                        }
                                    }
                                }
                            }

                            Map peparam = new HashMap();
                            peparam.put("PROVINCE_CODE", provinceCode);
                            peparam.put("PRODUCT_ID", commodityId);
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
                                        // 算出活动的开始结束时间，预提交下发
                                        Map discntDateMap = TradeManagerUtils.getDiscntEffectTimeNextMouthStart(String.valueOf(packageElementInfo.get(a).get("ELEMENT_ID")),
                                                monthLasttDay);
                                        dis.put("startDate", monthFirstDay);
                                        dis.put("endDate", discntDateMap.get("monthLasttDay"));
                                        discntList.add(dis);
                                    }
                                    if ("S".equals(packageElementInfo.get(a).get("ELEMENT_TYPE_CODE"))) {
                                        Map svc = new HashMap();
                                        svc.put("serviceId", packageElementInfo.get(a).get("ELEMENT_ID"));
                                        svc.put("productId", packageElementInfo.get(a).get("PRODUCT_ID"));
                                        svc.put("packageId", packageElementInfo.get(a).get("PACKAGE_ID"));
                                        svc.put("startDate", monthFirstDay);
                                        svc.put("endDate", monthLasttDay);
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
                                        sp.put("startDate", monthFirstDay);
                                        sp.put("endDate", monthLasttDay);
                                        spList.add(sp);
                                    }

                                }
                            }
                        }
                    }

                    for (int j = 0; j < productInfoResult.size(); j++) {
                        if ("D".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map dis = new HashMap();
                            dis.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                            dis.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                            dis.put("discntCode", productInfoResult.get(j).get("ELEMENT_ID"));
                            dis.put("startDate", monthFirstDay);
                            dis.put("endDate", monthLasttDay);
                            discntList.add(dis);
                        }
                        if ("S".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map svc = new HashMap();
                            svc.put("serviceId", productInfoResult.get(j).get("ELEMENT_ID"));
                            svc.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                            svc.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                            svc.put("startDate", monthFirstDay);
                            svc.put("endDate", monthLasttDay);
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
                            sp.put("startDate", monthFirstDay);
                            sp.put("endDate", monthLasttDay);
                            spList.add(sp);
                        }
                    }

                    strProductMode = (String) productInfoResult.get(0).get("PRODUCT_MODE");
                    mProductTypeCode = strProductMode;
                    strBrandCode = (String) productItemInfoResult.get(0).get("BRAND_CODE");
                    mBrandCode = strBrandCode;
                    strProductTypeCode = (String) productItemInfoResult.get(0).get("PRODUCT_TYPE_CODE");
                    tradeUserProductTypeCode = strProductTypeCode;

                    productTpye.put("productMode", strProductMode);
                    productTpye.put("productId", productId);
                    productTpye.put("productTypeCode", strProductTypeCode);
                    productTpye.put("startDate", monthFirstDay);
                    productTpye.put("endDate", monthLasttDay);
                    product.put("startDate", monthFirstDay);
                    product.put("endDate", monthLasttDay);
                    product.put("brandCode", strBrandCode);
                    product.put("productId", productId);
                    product.put("productMode", strProductMode);

                    productTypeList.add(productTpye);
                    productList.add(product);
                    msg.put("mBrandCode", mBrandCode);
                    msg.put("mProductId", mProductId);
                    msg.put("mProductTypeCode", mProductTypeCode);
                    msg.put("tradeUserProductTypeCode", tradeUserProductTypeCode);
                    msg.put("newProductFee", newProductFee);// 主产品的费用,23转4调cbss合约查询接口要用到

                } else if (ADD_PRODUCT.equals(productMode)) {

                    Map productTpye = new HashMap();
                    Map product = new HashMap();
                    String commodityId = productId;

                    String addProductId = "";
                    String strBrandCode = "";
                    String strProductTypeCode = "";
                    String strProductMode = "";

                    Map addproparam = new HashMap();
                    addproparam.put("PROVINCE_CODE", provinceCode);
                    addproparam.put("PRODUCT_ID", productId);
                    addproparam.put("EPARCHY_CODE", eparchyCode);
                    addproparam.put("PRODUCT_MODE", "01");
                    addproparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                    // 新产品逻辑对中间表的处理需要增加appkey by wangmc 20170319
                    List<Map> addproductInfoResult = n25Dao.qryDefaultPackageElement(addproparam);
                    if (addproductInfoResult == null || addproductInfoResult.size() == 0) {
                        throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + productId + "】的产品信息");
                    }
                    addProductId = String.valueOf(addproductInfoResult.get(0).get("PRODUCT_ID"));
                    isFinalCode = TradeManagerUtils.getEndDate(addProductId);
                    if ("N".equals(isFinalCode) || "X".equals(isFinalCode)) {
                        Map productDate = TradeManagerUtils.getEffectTimeNextMouthStart(addProductId, monthLasttDay);
                        addProStartDay = monthFirstDay;
                        addProEndDay = (String) productDate.get("monthLasttDay");
                    } else if ("Y".equals(isFinalCode)) {
                        if (StringUtils.isEmpty(activityEndTime)) {
                            throw new EcAopServerBizException("9999", "所选附加产品" + productId + "生失效时间需要和合约保持一致，"
                                    + "请检查合约信息是否已传或更换附加产品信息");
                        }
                        addProStartDay = monthFirstDay;
                        addProEndDay = activityEndTime;
                    }
                    for (int j = 0; j < addproductInfoResult.size(); j++) {

                        if ("D".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            String elementId = String.valueOf(addproductInfoResult.get(j).get("ELEMENT_ID"));
                            Map dis = new HashMap();
                            dis.put("productId", addproductInfoResult.get(j).get("PRODUCT_ID"));
                            dis.put("packageId", addproductInfoResult.get(j).get("PACKAGE_ID"));
                            dis.put("discntCode", addproductInfoResult.get(j).get("ELEMENT_ID"));
                            dis.put("startDate", addProStartDay);
                            if (!"Y".equals(isFinalCode)) {
                                Map discntDateMap = TradeManagerUtils.getDiscntEffectTimeNextMouthStart(elementId,
                                        addProEndDay);
                                dis.put("endDate", discntDateMap.get("monthLasttDay"));
                            } else {
                                dis.put("endDate", addProEndDay);
                            }
                            discntList.add(dis);
                        } else if ("S".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map svc = new HashMap();
                            svc.put("serviceId", addproductInfoResult.get(j).get("ELEMENT_ID"));
                            svc.put("productId", addproductInfoResult.get(j).get("PRODUCT_ID"));
                            svc.put("packageId", addproductInfoResult.get(j).get("PACKAGE_ID"));
                            svc.put("startDate", addProStartDay);
                            svc.put("endDate", addProEndDay);
                            svcList.add(svc);
                        } else if ("X".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
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
                            sp.put("startDate", addProStartDay);
                            sp.put("endDate", addProEndDay);
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

                    strProductMode = (String) addproductInfoResult.get(0).get("PRODUCT_MODE");
                    strBrandCode = (String) addProductItemInfoResult.get(0).get("BRAND_CODE");
                    strProductTypeCode = (String) addProductItemInfoResult.get(0).get("PRODUCT_TYPE_CODE");
                    productTpye.put("productMode", strProductMode);
                    productTpye.put("productId", addProductId);
                    productTpye.put("productTypeCode", strProductTypeCode);
                    productTpye.put("startDate", addProStartDay);
                    productTpye.put("endDate", addProEndDay);
                    product.put("startDate", addProStartDay);
                    product.put("endDate", addProEndDay);
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
                                        dis.put("startDate", addProStartDay);
                                        if (ADD_PRODUCT.equals(productMode)) {
                                            if (!"Y".equals(isFinalCode)) {
                                                Map discntDateMap = TradeManagerUtils.getDiscntEffectTimeNextMouthStart(
                                                        elementId, addProEndDay);
                                                dis.put("endDate", discntDateMap.get("monthLasttDay"));
                                            } else {
                                                dis.put("endDate", addProEndDay);
                                            }
                                        }
                                        discntList.add(dis);
                                    }
                                    if ("S".equals(packageElementInfo.get(b).get("ELEMENT_TYPE_CODE"))) {
                                        Map svc = new HashMap();
                                        svc.put("serviceId", packageElementInfo.get(b).get("ELEMENT_ID"));
                                        svc.put("productId", packageElementInfo.get(b).get("PRODUCT_ID"));
                                        svc.put("packageId", packageElementInfo.get(b).get("PACKAGE_ID"));
                                        svc.put("startDate", addProStartDay);
                                        svc.put("endDate", addProEndDay);
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
                                        sp.put("startDate", addProStartDay);
                                        sp.put("endDate", addProEndDay);
                                        spList.add(sp);
                                    }

                                }
                            }
                        }
                    }

                }
            }

        }
        msg.put("isTest", isTest);
        msg.put("resourceFee", resourceFee);
        msg.put("isOwnerPhone", isOwnerPhone);
        extObject.put("tradeDiscnt", preDiscntData(newDealRepeat(discntList,"discntList"), msg));
        extObject.put("tradeSvc", preTradeSvcData(newDealRepeat(svcList, "svcList"),msg));
        extObject.put("tradeSp",preTradeSpData(newDealRepeat(spList, "spList"),msg) );
        extObject.put("tradeProductType",preProductTpyeListData(newDealRepeat(productTypeList, "productTypeList"),msg) );
        extObject.put("tradeProduct", preProductData(newDealRepeat(productList, "productList"),msg));

    }

    /**
     * 新的去重方法-wangmc 20170325
     *
     * @param listMap
     * @param keys-比较是否重复的字段名 discntList-资费节点, svcList-服务节点, spList-sp节点
     *                        productTypeList-产品类型节点, productList-产品节点
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
     * 用来拼装tradeProduct节点
     */
    public List<Map> preProductData(List<Map> product,Map msg) {
        try {
            List<Map> itemList = new ArrayList<Map>();
            for (int i = 0; i < product.size(); i++) {
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("userId", msg.get("userId"));
                item.put("productMode", product.get(i).get("productMode"));
                item.put("productId", product.get(i).get("productId"));
                item.put("brandCode", product.get(i).get("brandCode"));
                item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
                item.put("modifyTag", "0");
                item.put("startDate", product.get(i).get("startDate"));
                item.put("endDate", product.get(i).get("endDate"));
                item.put("userIdA", MagicNumber.DEFAULT_NO_VALUE);
                itemList.add(item);
            }
            return itemList;
        } catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_PRODUCT节点报错" + e.getMessage());
        }
    }
    /**
     * 拼装tradeSp节点
     */
    public List<Map> preTradeSpData(List<Map> sp,Map msg) {
        try {
            List<Map> itemList = new ArrayList<Map>();
            for (int i = 0; i < sp.size(); i++) {
                Map item = new HashMap();
                item.put("userId", msg.get("userId"));
                item.put("serialNumber", msg.get("serialNumber"));
                item.put("productId", sp.get(i).get("productId"));
                item.put("packageId", sp.get(i).get("packageId"));
                item.put("partyId", sp.get(i).get("partyId"));
                item.put("spId", sp.get(i).get("spId"));
                item.put("spProductId", sp.get(i).get("spProductId"));
                item.put("firstBuyTime", sp.get(i).get("startDate"));
                item.put("paySerialNumber", msg.get("serialNumber"));
                item.put("startDate", sp.get(i).get("startDate"));
                item.put("endDate", sp.get(i).get("endDate"));
                item.put("updateTime", sp.get(i).get("startDate"));
                item.put("remark", "");
                item.put("modifyTag", "0");
                item.put("payUserId", msg.get("userId"));
                item.put("spServiceId", sp.get(i).get("spServiceId"));
                item.put("itemId",TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
                item.put("userIdA", msg.get("userId"));
                item.put("xDatatype", "NULL");
                itemList.add(item);
            }
            return itemList;
        } catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_SP节点报错" + e.getMessage());
        }
    }
    /**
     * 拼装TRADE_PRODUCT_TYPE节点
     */
    public List<Map> preProductTpyeListData(List<Map> productTpye,Map msg) {
        try {
            List<Map> itemList = new ArrayList<Map>();
            for (int i = 0; i < productTpye.size(); i++) {
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("userId", msg.get("userId"));
                String productMode = (String) productTpye.get(i).get("productMode");
                item.put("productMode", productMode);
                item.put("productId", productTpye.get(i).get("productId"));
                item.put("productTypeCode", productTpye.get(i).get("productTypeCode"));
                item.put("modifyTag", "0");
                item.put("startDate", productTpye.get(i).get("startDate"));
                item.put("endDate", productTpye.get(i).get("endDate"));
                itemList.add(item);
            }
            return itemList;
        } catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_PRODUCT_TYPE节点报错" + e.getMessage());
        }
    }

    /**
     * 拼装TRADE_SVC节点
     */
    public List<Map> preTradeSvcData(List<Map> svc,Map msg) {
        try {
            List svList = new ArrayList();
            for (int i = 0; i < svc.size(); i++) {
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("userId", msg.get("userId"));
                item.put("serviceId", svc.get(i).get("serviceId"));
                item.put("modifyTag", "0");
                item.put("startDate", svc.get(i).get("startDate"));
                item.put("endDate", svc.get(i).get("endDate"));
                item.put("productId", svc.get(i).get("productId"));
                item.put("packageId", svc.get(i).get("packageId"));
                item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
                item.put("userIdA", "-1");
                svList.add(item);
            }
            return svList;
        } catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_SVC节点报错" + e.getMessage());
        }
    }

    /**
     * 拼装tradediscnt节点
     */
    public List<Map> preDiscntData(List<Map> discnt, Map msg) {
        try {
            List<Map> itemList = new ArrayList<Map>();
            for (int i = 0; i < discnt.size(); i++) {
                String packageId = String.valueOf(discnt.get(i).get("packageId"));
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("id", msg.get("userId"));
                item.put("idType", "1");
                item.put("userIdA", "-1");
                item.put("productId", discnt.get(i).get("productId"));
                item.put("packageId", packageId);
                item.put("discntCode", discnt.get(i).get("discntCode"));
                item.put("specTag", "1");// FIXME
                item.put("relationTypeCode", "");
                item.put("startDate", discnt.get(i).get("startDate"));
                item.put("endDate", discnt.get(i).get("endDate"));
                item.put("modifyTag", "0");
                item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
                itemList.add(item);

            }
            return itemList;
        } catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_DISCNT节点报错" + e.getMessage());
        }
    }

    /**
     * 根据商城传入的带宽选 择速率
     * 
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
     * 
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

    // 遍历trans_item表取得每条属性信息
    public String getValueFromItem(String attrCode, List<Map> productItemInfoResult) {
        String attrValue = "";
        for (int i = 0; i < productItemInfoResult.size(); i++) {
            Map productItemInfo = productItemInfoResult.get(i);
            if ("U".equals(productItemInfo.get("PTYPE"))) {
                if (attrValue == "" && attrCode.equals(productItemInfo.get("ATTR_CODE"))) {
                    attrValue = productItemInfo.get("ATTR_VALUE").toString();
                }
            }
        }
        return attrValue;
    }

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

}
