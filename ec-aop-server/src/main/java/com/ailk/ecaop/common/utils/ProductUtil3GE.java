package com.ailk.ecaop.common.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;

import com.ailk.ecaop.biz.product.DealNewCbssProduct;

public class ProductUtil3GE {

    private static String MAIN_PRODUCT = "1";
    private static String ADD_PRODUCT = "2";
    // 查询新产品库时有的需要关联中间表,中间表需要用到appkey by wangmc 20170318 FIXME
    private static String appkey;

    public static void preProductInfo(List<Map> productInfo, List<Map> activityInfo, Object provinceCode, Map msg) {

        appkey = msg.get("appkey") + "";

        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        List<Map> paraList = (List<Map>) msg.get("para");// 处理速率和remark
        List<Map> productTypeList = new ArrayList<Map>();
        List<Map> productList = new ArrayList<Map>();

        List<Map> discntList = new ArrayList<Map>();
        List<Map> svcList = new ArrayList<Map>();
        List<Map> spList = new ArrayList<Map>();
        String monthLasttDay = "20501231235959";
        String monthFirstDay = GetDateUtils.getDate();
        String isFinalCode = "";
        String mBrandCode = "";
        String mProductTypeCode = "";
        String mProductId = "";
        String activityStartTime = "";
        String activityEndTime = "";
        if (null != activityInfo && !activityInfo.isEmpty()) {

            for (int i = 0; i < activityInfo.size(); i++) {
                Map activityMap = activityInfo.get(i);
                if (activityMap.isEmpty()) {
                    continue;
                }
                String actPlanId = (String) (activityMap.get("actPlanId"));
                Map actparam = new HashMap();
                actparam.put("PROVINCE_CODE", provinceCode);
                actparam.put("PRODUCT_MODE", "50");
                actparam.put("PRODUCT_ID", actPlanId);
                actparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                actparam.put("FIRST_MON_BILL_MODE", null);
                List<Map> actInfoResult = n25Dao.qryDefaultPackageElement(actparam);
                if (actInfoResult == null || actInfoResult.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + actPlanId + "】的产品信息");
                }
                // 活动时间处理
                List<Map> activityList = n25Dao.queryProductAndPtypeProduct(actparam);
                Map activityListMap = null;
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
                        Integer.parseInt(strStartUnit), GetDateUtils.getSysdateFormat(), Integer.parseInt(startOffset));
                activityStartTime = GetDateUtils.TransDate(activityStartTime, "yyyy-MM-dd HH:mm:ss");
                activityEndTime = GetDateUtils.getSpecifyDateTime(Integer.parseInt(enableTag),
                        Integer.parseInt(endUnit), GetDateUtils.getSysdateFormat(), Integer.parseInt(resActivityper)
                                + Integer.parseInt(startOffset));
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
                // appendMap.put("PART_ACTIVE_PRODUCT", actProductId);

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

                    List<Map> packageElement = (List<Map>) activityInfo.get(0).get("packageElement");
                    // 如果有附加包
                    if (packageElement != null && packageElement.size() > 0) {
                        for (int n = 0; n < packageElement.size(); n++) {
                            Map peparam = new HashMap();
                            peparam.put("PROVINCE_CODE", provinceCode);
                            peparam.put("PRODUCT_ID", actPlanId);
                            peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                            peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                            peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                            List<Map> packageElementInfo = n25Dao.queryPackageElementInfo(peparam);
                            if (packageElementInfo != null && packageElementInfo.size() > 0) {
                                if ("D".equals(packageElementInfo.get(0).get("ELEMENT_TYPE_CODE"))) {
                                    Map dis = new HashMap();
                                    dis.put("productId", packageElementInfo.get(0).get("PRODUCT_ID"));
                                    dis.put("packageId", packageElementInfo.get(0).get("PACKAGE_ID"));
                                    dis.put("discntCode", packageElementInfo.get(0).get("ELEMENT_ID"));
                                    // 算出活动的开始结束时间，预提交下发
                                    dis.put("activityStarTime", activityStartTime);
                                    dis.put("activityTime", activityEndTime);
                                    discntList.add(dis);
                                }
                                if ("S".equals(packageElementInfo.get(0).get("ELEMENT_TYPE_CODE"))) {
                                    Map svc = new HashMap();
                                    svc.put("serviceId", packageElementInfo.get(0).get("ELEMENT_ID"));
                                    svc.put("productId", packageElementInfo.get(0).get("PRODUCT_ID"));
                                    svc.put("packageId", packageElementInfo.get(0).get("PACKAGE_ID"));
                                    // 算出活动的开始结束时间，预提交下发
                                    svc.put("activityStarTime", activityStartTime);
                                    svc.put("activityTime", activityEndTime);
                                    svcList.add(svc);
                                }
                                if ("X".equals(packageElementInfo.get(0).get("ELEMENT_TYPE_CODE"))) {
                                    String spId = "-1";
                                    String partyId = "-1";
                                    String spProductId = "-1";
                                    Map spItemParam = new HashMap();
                                    spItemParam.put("PTYPE", "X");
                                    spItemParam.put("PRODUCT_ID", actPlanId);
                                    spItemParam.put("PROVINCE_CODE", provinceCode);
                                    spItemParam.put("SPSERVICEID", packageElementInfo.get(0).get("ELEMENT_ID"));
                                    List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                                    if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                        throw new EcAopServerBizException("9999", "在SP表中未查询到【"
                                                + packageElementInfo.get(0).get("ELEMENT_ID") + "】的元素属性信息");
                                    }
                                    for (int j = 0; j < spItemInfoResult.size(); j++) {
                                        Map spItemInfo = spItemInfoResult.get(j);
                                        spId = (String) spItemInfo.get("SP_ID");
                                        partyId = (String) spItemInfo.get("PARTY_ID");
                                        spProductId = (String) spItemInfo.get("SP_PRODUCT_ID");
                                    }
                                    Map sp = new HashMap();
                                    sp.put("productId", packageElementInfo.get(0).get("PRODUCT_ID"));
                                    sp.put("packageId", packageElementInfo.get(0).get("PACKAGE_ID"));
                                    sp.put("partyId", partyId);
                                    sp.put("spId", spId);
                                    sp.put("spProductId", spProductId);
                                    sp.put("spServiceId", packageElementInfo.get(0).get("ELEMENT_ID"));
                                    sp.put("activityStarTime", activityStartTime);
                                    sp.put("activityTime", activityEndTime);
                                    spList.add(sp);
                                }

                            }

                        }

                    }

                    productTypeList.add(productTpye);
                    productList.add(product);

                }

            }

        }
        // 产品处理
        if (productInfo != null && productInfo.size() > 0) {
            for (int i = 0; i < productInfo.size(); i++) {
                String firstMonBillMode = null == productInfo.get(i).get("firstMonBillMode") ? "02"
                        : (String) productInfo.get(i).get("firstMonBillMode");
                String addProStartDay = "";
                String addProEndDay = "";
                String productId = (String) (productInfo.get(i).get("productId"));
                String productMode = (String) (productInfo.get(i).get("productMode"));
                List<Map> packageElement = (List<Map>) productInfo.get(i).get("packageElement");
                if (MAIN_PRODUCT.equals(productMode)) {

                    Map productTpye = new HashMap();
                    Map product = new HashMap();

                    String strBrandCode = "";
                    String strProductTypeCode = "";
                    String strProductMode = "";

                    Map proparam = new HashMap();
                    proparam.put("PROVINCE_CODE", provinceCode);
                    proparam.put("PRODUCT_ID", productId);
                    proparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                    proparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                    proparam.put("PRODUCT_MODE", "00");
                    // 新产品逻辑对中间表的处理需要增加appkey by wangmc 20170319
                    proparam.put("APPKEY", appkey);
                    List<Map> productInfoResult = n25Dao.qryDefaultPackageElement(proparam);
                    if (productInfoResult == null || productInfoResult.size() == 0) {
                        throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + productId + "】的产品信息");
                    }
                    productId = String.valueOf(productInfoResult.get(0).get("PRODUCT_ID"));
                    mProductId = productId;
                    Map itparam = new HashMap();
                    itparam.put("PROVINCE_CODE", provinceCode);
                    itparam.put("PRODUCT_ID", productId);
                    List<Map> productItemInfoResult = n25Dao.queryProductAndPtypeProduct(itparam);
                    if (productItemInfoResult == null || productItemInfoResult.size() == 0) {
                        throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + productId + "】的产品属性信息");
                    }

                    // 选速率,默认42M
                    String speed = "42";
                    if (null != paraList && !paraList.isEmpty()) {
                        for (Map para : paraList) {
                            if ("SPEED".equals(para.get("paraId"))) {
                                speed = (String) para.get("paraValue");
                            }
                        }
                    }
                    // 选速率
                    productInfoResult = chooseSpeed(productInfoResult, speed);
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

                    strProductMode = (String) (productInfoResult.get(0).get("PRODUCT_MODE"));
                    mProductTypeCode = strProductMode;
                    strBrandCode = (String) productItemInfoResult.get(0).get("BRAND_CODE");
                    mBrandCode = strBrandCode;
                    strProductTypeCode = (String) productItemInfoResult.get(0).get("PRODUCT_TYPE_CODE");

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

                    // 附加包
                    if (packageElement != null && packageElement.size() > 0) {
                        for (int n = 0; n < packageElement.size(); n++) {
                            Map peparam = new HashMap();
                            peparam.put("PROVINCE_CODE", provinceCode);
                            peparam.put("PRODUCT_ID", productId);
                            peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                            peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                            peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                            List<Map> packageElementInfo = n25Dao.queryPackageElementInfo(peparam);
                            if (packageElementInfo != null && packageElementInfo.size() > 0) {
                                if ("D".equals(packageElementInfo.get(0).get("ELEMENT_TYPE_CODE"))) {
                                    Map dis = new HashMap();
                                    dis.put("productId", packageElementInfo.get(0).get("PRODUCT_ID"));
                                    dis.put("packageId", packageElementInfo.get(0).get("PACKAGE_ID"));
                                    dis.put("discntCode", packageElementInfo.get(0).get("ELEMENT_ID"));
                                    discntList.add(dis);
                                }
                                if ("S".equals(packageElementInfo.get(0).get("ELEMENT_TYPE_CODE"))) {
                                    Map svc = new HashMap();
                                    svc.put("serviceId", packageElementInfo.get(0).get("ELEMENT_ID"));
                                    svc.put("productId", packageElementInfo.get(0).get("PRODUCT_ID"));
                                    svc.put("packageId", packageElementInfo.get(0).get("PACKAGE_ID"));
                                    svcList.add(svc);
                                }
                                if ("X".equals(packageElementInfo.get(0).get("ELEMENT_TYPE_CODE"))) {
                                    String spId = "-1";
                                    String partyId = "-1";
                                    String spProductId = "-1";
                                    Map spItemParam = new HashMap();
                                    spItemParam.put("PRODUCT_ID", productId);
                                    spItemParam.put("PROVINCE_CODE", provinceCode);
                                    spItemParam.put("SPSERVICEID", packageElementInfo.get(0).get("ELEMENT_ID"));
                                    List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                                    if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                        throw new EcAopServerBizException("9999", "在SP表中未查询到【"
                                                + packageElementInfo.get(0).get("ELEMENT_ID") + "】的元素属性信息");
                                    }
                                    for (int j = 0; j < spItemInfoResult.size(); j++) {
                                        Map spItemInfo = spItemInfoResult.get(j);
                                        spId = (String) spItemInfo.get("SP_ID");
                                        partyId = (String) spItemInfo.get("PARTY_ID");
                                        spProductId = (String) spItemInfo.get("SP_PRODUCT_ID");
                                    }
                                    Map sp = new HashMap();
                                    sp.put("productId", packageElementInfo.get(0).get("PRODUCT_ID"));
                                    sp.put("packageId", packageElementInfo.get(0).get("PACKAGE_ID"));
                                    sp.put("partyId", partyId);
                                    sp.put("spId", spId);
                                    sp.put("spProductId", spProductId);
                                    sp.put("spServiceId", packageElementInfo.get(0).get("ELEMENT_ID"));
                                    spList.add(sp);
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

                    Map addproparam = new HashMap();
                    addproparam.put("PROVINCE_CODE", provinceCode);
                    addproparam.put("PRODUCT_ID", productId);
                    addproparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                    addproparam.put("PRODUCT_MODE", "01");
                    addproparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                    // 新产品逻辑对中间表的处理需要增加appkey by wangmc 20170319
                    addproparam.put("APPKEY", appkey);
                    List<Map> addproductInfoResult = n25Dao.qryDefaultPackageElement(addproparam);
                    if (addproductInfoResult == null || addproductInfoResult.size() == 0) {
                        throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + productId + "】的产品信息");
                    }
                    addProductId = String.valueOf(addproductInfoResult.get(0).get("PRODUCT_ID"));
                    isFinalCode = TradeManagerUtils.getEndDate(addProductId);
                    if ("N".equals(isFinalCode) || "X".equals(isFinalCode)) {
                        Map productDate = TradeManagerUtils.getEffectTime(addProductId, monthFirstDay, monthLasttDay);
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
                                Map discntDateMap = TradeManagerUtils.getDiscntEffectTime(elementId, addProStartDay,
                                        addProEndDay);
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
                                throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
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

                    strProductMode = (String) (addproductInfoResult.get(0).get("PRODUCT_MODE"));
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
                            peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                            peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                            peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                            List<Map> packageElementInfo = n25Dao.queryPackageElementInfo(peparam);
                            if (packageElementInfo != null && packageElementInfo.size() > 0) {
                                if ("D".equals(packageElementInfo.get(0).get("ELEMENT_TYPE_CODE"))) {
                                    String elementId = String.valueOf(packageElementInfo.get(0).get("ELEMENT_ID"));
                                    Map dis = new HashMap();
                                    dis.put("productId", packageElementInfo.get(0).get("PRODUCT_ID"));
                                    dis.put("packageId", packageElementInfo.get(0).get("PACKAGE_ID"));
                                    dis.put("discntCode", elementId);
                                    if (ADD_PRODUCT.equals(productMode)) {
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
                                    }
                                    discntList.add(dis);
                                }
                                if ("S".equals(packageElementInfo.get(0).get("ELEMENT_TYPE_CODE"))) {
                                    Map svc = new HashMap();
                                    svc.put("serviceId", packageElementInfo.get(0).get("ELEMENT_ID"));
                                    svc.put("productId", packageElementInfo.get(0).get("PRODUCT_ID"));
                                    svc.put("packageId", packageElementInfo.get(0).get("PACKAGE_ID"));
                                    svcList.add(svc);
                                }
                                if ("X".equals(packageElementInfo.get(0).get("ELEMENT_TYPE_CODE"))) {
                                    String spId = "-1";
                                    String partyId = "-1";
                                    String spProductId = "-1";
                                    Map spItemParam = new HashMap();
                                    spItemParam.put("PROVINCE_CODE", provinceCode);
                                    spItemParam.put("SPSERVICEID", packageElementInfo.get(0).get("ELEMENT_ID"));
                                    List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                                    if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                        throw new EcAopServerBizException("9999", "在SP产品表中未查询到【"
                                                + packageElementInfo.get(0).get("ELEMENT_ID") + "】的元素属性信息");
                                    }

                                    for (int j = 0; j < spItemInfoResult.size(); j++) {
                                        Map spItemInfo = spItemInfoResult.get(j);
                                        spId = (String) spItemInfo.get("SP_ID");
                                        partyId = (String) spItemInfo.get("PARTY_ID");
                                        spProductId = (String) spItemInfo.get("SP_PRODUCT_ID");
                                    }

                                    Map sp = new HashMap();
                                    sp.put("productId", packageElementInfo.get(0).get("PRODUCT_ID"));
                                    sp.put("packageId", packageElementInfo.get(0).get("PACKAGE_ID"));
                                    sp.put("partyId", partyId);
                                    sp.put("spId", spId);
                                    sp.put("spProductId", spProductId);
                                    sp.put("spServiceId", packageElementInfo.get(0).get("ELEMENT_ID"));
                                    spList.add(sp);
                                }
                            }
                        }
                    }

                }
            }

        }
        // dealRepeat(discntList);
        // dealRepeat(svcList);
        // dealRepeat(spList);
        // dealRepeat(productTypeList);
        // dealRepeat(productList);

        // 使用新的去重方法 by wangmc 20170410 FIXME
        NewProductUtils.newDealRepeat(discntList, "discntList");
        NewProductUtils.newDealRepeat(svcList, "svcList");
        NewProductUtils.newDealRepeat(spList, "spList");
        NewProductUtils.newDealRepeat(productTypeList, "productTypeList");
        NewProductUtils.newDealRepeat(productList, "productList");

        msg.put("discntList", discntList);
        msg.put("svcList", svcList);
        msg.put("spList", spList);
        msg.put("productTypeList", productTypeList);
        msg.put("productList", productList);

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

    /**
     * 根据商城传入的带宽选 择速率
     * @param productInfoResult
     * @param speed
     * @return
     */
    public static List<Map> chooseSpeed(List<Map> productInfoResult, String speed) {
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

        for (int i = 0; i < productInfoResult.size(); i++) {
            Map data = productInfoResult.get(i);
            if (kick1st.equals(data.get("ELEMENT_ID")) || kick2nd.equals(data.get("ELEMENT_ID"))) {
                productInfoResult.remove(i);
                i--;
            }
        }
        return productInfoResult;
    }

}
