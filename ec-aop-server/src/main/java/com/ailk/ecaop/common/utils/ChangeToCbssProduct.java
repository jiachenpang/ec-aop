package com.ailk.ecaop.common.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;

import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;

public class ChangeToCbssProduct implements ProductInfoUtil {

    private static String MAIN_PRODUCT = "1";
    private static String ADD_PRODUCT = "2";
    LanOpenApp4GDao dao = new LanOpenApp4GDao();

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
                String firstMonBillMode = null == userInfo.get(0).get("firstMonBillMode") ? "02" : (String) userInfo
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
                    proparam.put("COMMODITY_ID", commodityId);
                    proparam.put("EPARCHY_CODE", eparchyCode);
                    proparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                    proparam.put("PRODUCT_MODE", "00");
                    List<Map> productInfoResult = dao.queryProductInfo(proparam);
                    if (productInfoResult == null || productInfoResult.size() == 0) {
                        throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品信息");
                    }
                    List<Map> productFeeList = dao.qryProductFee(proparam);
                    if (productFeeList == null || productFeeList.size() == 0) {
                        throw new EcAopServerBizException("9999", "在产品原始表中未查询到产品ID【" + commodityId + "】的产品费用信息");
                    }
                    String newProductFee = (String) productFeeList.get(0).get("NEWPRODUCTFEE");
                    productId = String.valueOf(productInfoResult.get(0).get("PRODUCT_ID"));
                    mProductId = productId;
                    Map itparam = new HashMap();
                    itparam.put("PROVINCE_CODE", provinceCode);
                    itparam.put("PID", productId);
                    itparam.put("COMMODITY_ID", commodityId);
                    itparam.put("PTYPE", "U");
                    List<Map> productItemInfoResult = dao.queryProductItemInfo(itparam);
                    if (productItemInfoResult == null || productItemInfoResult.size() == 0) {
                        throw new EcAopServerBizException("9999", "在产品属性表中未查询到产品ID【" + commodityId + "】的产品属性信息");
                    }

                    // 选速率,默认42M
                    String speed = "42";
                    if (null != paraList && !paraList.isEmpty()) {
                        for (Map para : paraList) {
                            if ("SPEED".equalsIgnoreCase((String) para.get("paraId"))) {
                                speed = (String) para.get("paraValue");
                            }
                        }
                    }
                    // 选速率
                    productInfoResult = chooseSpeed(productInfoResult, speed);
                    // 预处理短信网龄产品信息
                    productInfoResult = ProductManagerUtils.preDealProductInfo(productInfoResult);
                    // 预先处理附加包
                    if (packageElement != null && packageElement.size() > 0) {
                        for (int n = 0; n < packageElement.size(); n++) {
                            List<Map> maxList = ProductManagerUtils.getPkgMaxNum(commodityId);
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
                                Map subServInfoMap = subServInfos.get(0);
                                List<Map> subServList = (List<Map>) subServInfoMap.get("subServInfo");
                                if (null != subServList && !subServList.isEmpty()) {
                                    for (Map subServMap : subServList) {
                                        String servInstid = (String) subServMap.get("servInstid");
                                        if ("50015".equals(servInstid)) {// 50015国际漫游
                                            Map param = new HashMap();
                                            param.put("PRODUCT_ID", productId);
                                            param.put("COMMODITY_ID", commodityId);
                                            param.put("ELEMENT_ID", "50015");
                                            param.put("PROVINCE_CODE", provinceCode);
                                            List<Map> servInstidList = dao.queryPackageBySvcId(param);
                                            productInfoResult.addAll(servInstidList);
                                            // 剔除国内漫游50014
                                            for (Map productInfoMap : productInfoResult) {
                                                if ("50014".equals(productInfoMap.get("ELEMENT_ID"))) {
                                                    productInfoResult.remove(productInfoMap);
                                                }
                                            }
                                        }
                                        if ("50011".equals(servInstid)) {// 50011国际长途
                                            Map param = new HashMap();
                                            param.put("PRODUCT_ID", productId);
                                            param.put("COMMODITY_ID", commodityId);
                                            param.put("ELEMENT_ID", "50011");
                                            param.put("PROVINCE_CODE", provinceCode);
                                            List<Map> servInstidList = dao.queryPackageBySvcId(param);
                                            productInfoResult.addAll(servInstidList);
                                            // 剔除国内漫游50010
                                            for (Map productInfoMap : productInfoResult) {
                                                if ("50010".equals(productInfoMap.get("ELEMENT_ID"))) {
                                                    productInfoResult.remove(productInfoMap);
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Map peparam = new HashMap();
                            peparam.put("PROVINCE_CODE", provinceCode);
                            peparam.put("COMMODITY_ID", commodityId);
                            peparam.put("EPARCHY_CODE", eparchyCode);
                            peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                            peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                            List<Map> packageElementInfo = dao.queryPackageElementInfo(peparam);
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
                                        spItemParam.put("PTYPE", "X");
                                        spItemParam.put("COMMODITY_ID", commodityId);
                                        spItemParam.put("PROVINCE_CODE", provinceCode);
                                        spItemParam.put("PID", packageElementInfo.get(a).get("ELEMENT_ID"));
                                        List<Map> spItemInfoResult = dao.queryProductItemInfo(spItemParam);
                                        if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                            throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                                    + packageElementInfo.get(a).get("ELEMENT_ID") + "】的产品属性信息");
                                        }
                                        for (int j = 0; j < spItemInfoResult.size(); j++) {
                                            Map spItemInfo = spItemInfoResult.get(j);
                                            if ("SP_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                                spId = (String) spItemInfo.get("ATTR_VALUE");
                                            }
                                            else if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                                partyId = (String) spItemInfo.get("ATTR_VALUE");
                                            }
                                            else if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                                spProductId = (String) spItemInfo.get("ATTR_VALUE");
                                            }
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
                            spItemParam.put("COMMODITY_ID", commodityId);
                            spItemParam.put("PROVINCE_CODE", provinceCode);
                            spItemParam.put("PID", productInfoResult.get(j).get("ELEMENT_ID"));
                            List<Map> spItemInfoResult = dao.queryProductItemInfo(spItemParam);
                            if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                        + productInfoResult.get(j).get("ELEMENT_ID") + "】的产品属性信息");
                            }
                            for (int l = 0; l < spItemInfoResult.size(); l++) {
                                Map spItemInfo = spItemInfoResult.get(l);
                                if ("SP_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                    spId = (String) spItemInfo.get("ATTR_VALUE");
                                }
                                else if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                    partyId = (String) spItemInfo.get("ATTR_VALUE");
                                }
                                else if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                    spProductId = (String) spItemInfo.get("ATTR_VALUE");
                                }
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

                    strProductMode = (String) productInfoResult.get(0).get("PRODUCT_MODE");
                    mProductTypeCode = strProductMode;
                    strBrandCode = getValueFromItem("BRAND_CODE", productItemInfoResult);
                    mBrandCode = strBrandCode;
                    strProductTypeCode = getValueFromItem("PRODUCT_TYPE_CODE", productItemInfoResult);
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

                }
                else if (ADD_PRODUCT.equals(productMode)) {

                    Map productTpye = new HashMap();
                    Map product = new HashMap();
                    String commodityId = productId;

                    String addProductId = "";
                    String strBrandCode = "";
                    String strProductTypeCode = "";
                    String strProductMode = "";

                    Map addproparam = new HashMap();
                    addproparam.put("PROVINCE_CODE", provinceCode);
                    addproparam.put("COMMODITY_ID", commodityId);
                    addproparam.put("EPARCHY_CODE", eparchyCode);
                    addproparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                    List<Map> addproductInfoResult = dao.queryAddProductInfo(addproparam);
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
                            spItemParam.put("PTYPE", "X");
                            spItemParam.put("COMMODITY_ID", commodityId);
                            spItemParam.put("PROVINCE_CODE", provinceCode);
                            spItemParam.put("PID", addproductInfoResult.get(j).get("ELEMENT_ID"));
                            List<Map> spItemInfoResult = dao.queryProductItemInfo(spItemParam);
                            if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                        + addproductInfoResult.get(j).get("ELEMENT_ID") + "】的产品属性信息");
                            }
                            for (int l = 0; l < spItemInfoResult.size(); l++) {
                                Map spItemInfo = spItemInfoResult.get(l);
                                if ("SP_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                    spId = (String) spItemInfo.get("ATTR_VALUE");
                                }
                                if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                    partyId = (String) spItemInfo.get("ATTR_VALUE");
                                }
                                if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                    spProductId = (String) spItemInfo.get("ATTR_VALUE");
                                }
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
                    additparam.put("PID", addProductId);
                    additparam.put("COMMODITY_ID", commodityId);
                    additparam.put("PTYPE", "U");
                    List<Map> addProductItemInfoResult = dao.queryProductItemInfo(additparam);
                    if (addProductItemInfoResult == null || addProductItemInfoResult.size() == 0) {
                        throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + productId + "】的产品属性信息");
                    }

                    strProductMode = (String) addproductInfoResult.get(0).get("PRODUCT_MODE");
                    strBrandCode = getValueFromItem("BRAND_CODE", addProductItemInfoResult);
                    strProductTypeCode = getValueFromItem("PRODUCT_TYPE_CODE", addProductItemInfoResult);
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
                            peparam.put("COMMODITY_ID", productId);
                            peparam.put("EPARCHY_CODE", eparchyCode);
                            peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                            peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                            List<Map> packageElementInfo = dao.queryPackageElementInfo(peparam);
                            if (packageElementInfo != null && packageElementInfo.size() > 0) {
                                for (int b = 0; b < packageElementInfo.size(); b++) {

                                    if ("D".equals(packageElementInfo.get(b).get("ELEMENT_TYPE_CODE"))) {
                                        String elementId = String.valueOf(packageElementInfo.get(b).get("ELEMENT_ID"));
                                        Map dis = new HashMap();
                                        dis.put("productId", packageElementInfo.get(b).get("PRODUCT_ID"));
                                        dis.put("packageId", packageElementInfo.get(b).get("PACKAGE_ID"));
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
                                        spItemParam.put("PTYPE", "X");
                                        spItemParam.put("COMMODITY_ID", productId);
                                        spItemParam.put("PROVINCE_CODE", provinceCode);
                                        spItemParam.put("PID", packageElementInfo.get(b).get("ELEMENT_ID"));
                                        List<Map> spItemInfoResult = dao.queryProductItemInfo(spItemParam);
                                        if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                            throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                                    + packageElementInfo.get(b).get("ELEMENT_ID") + "】的产品属性信息");
                                        }
                                        for (int j = 0; j < spItemInfoResult.size(); j++) {
                                            Map spItemInfo = spItemInfoResult.get(j);
                                            if ("SP_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                                spId = (String) spItemInfo.get("ATTR_VALUE");
                                            }
                                            if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                                partyId = (String) spItemInfo.get("ATTR_VALUE");
                                            }
                                            if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                                spProductId = (String) spItemInfo.get("ATTR_VALUE");
                                            }
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
                actparam.put("COMMODITY_ID", actPlanId);
                actparam.put("PRODUCT_ID", actPlanId);
                actparam.put("EPARCHY_CODE", eparchyCode);
                actparam.put("FIRST_MON_BILL_MODE", null);
                List<Map> actInfoResult = dao.queryAddProductInfo(actparam);
                if (actInfoResult == null || actInfoResult.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + actPlanId + "】的产品信息");
                }
                // 活动时间处理
                List<Map> activityList = dao.queryProductAndPtypeProduct(actparam);
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
                        spItemParam.put("COMMODITY_ID", actPlanId);
                        spItemParam.put("PROVINCE_CODE", provinceCode);
                        spItemParam.put("PID", actInfoResult.get(j).get("ELEMENT_ID"));
                        List<Map> spItemInfoResult = dao.queryProductItemInfo(spItemParam);
                        if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                            throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                    + actInfoResult.get(j).get("ELEMENT_ID") + "】的产品属性信息");
                        }
                        for (int l = 0; l < spItemInfoResult.size(); l++) {
                            Map spItemInfo = spItemInfoResult.get(l);
                            if ("SP_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                spId = (String) spItemInfo.get("ATTR_VALUE");
                            }
                            if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                partyId = (String) spItemInfo.get("ATTR_VALUE");
                            }
                            if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                spProductId = (String) spItemInfo.get("ATTR_VALUE");
                            }
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
                List<Map> actProductInfo = dao.queryActProductInfo(actProParam);
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
                            peparam.put("COMMODITY_ID", actPlanId);
                            peparam.put("EPARCHY_CODE", eparchyCode);
                            peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                            peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                            List<Map> packageElementInfo = dao.queryPackageElementInfo(peparam);
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
                                        spItemParam.put("COMMODITY_ID", actPlanId);
                                        spItemParam.put("PROVINCE_CODE", provinceCode);
                                        spItemParam.put("PID", packageElementInfo.get(c).get("ELEMENT_ID"));
                                        List<Map> spItemInfoResult = dao.queryProductItemInfo(spItemParam);
                                        if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                            throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                                    + packageElementInfo.get(c).get("ELEMENT_ID") + "】的产品属性信息");
                                        }
                                        for (int j = 0; j < spItemInfoResult.size(); j++) {
                                            Map spItemInfo = spItemInfoResult.get(j);
                                            if ("SP_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                                spId = (String) spItemInfo.get("ATTR_VALUE");
                                            }

                                            if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                                partyId = (String) spItemInfo.get("ATTR_VALUE");
                                            }
                                            if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                                spProductId = (String) spItemInfo.get("ATTR_VALUE");
                                            }
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
                            String purchaseItemId = (String) msg.get("purchaseItemId");
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
        // dealRepeat(discntList);
        // dealRepeat(svcList);
        // dealRepeat(spList);
        // dealRepeat(productTypeList);
        // dealRepeat(productList);

        // 使用新的去重方法 by wangmc 20170410 FIXME
        discntList = NewProductUtils.newDealRepeat(discntList, "discntList");
        svcList = NewProductUtils.newDealRepeat(svcList, "svcList");
        spList = NewProductUtils.newDealRepeat(spList, "spList");
        productTypeList = NewProductUtils.newDealRepeat(productTypeList, "productTypeList");
        productList = NewProductUtils.newDealRepeat(productList, "productList");
        msg.put("isTest", isTest);
        msg.put("resourceFee", resourceFee);
        msg.put("isOwnerPhone", isOwnerPhone);
        msg.put("discntList", discntList);
        msg.put("svcList", svcList);
        msg.put("spList", spList);
        msg.put("productTypeList", productTypeList);
        msg.put("productList", productList);

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
