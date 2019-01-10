package com.ailk.ecaop.common.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;

import com.ailk.ecaop.biz.product.DealNewCbssProduct;
import com.ailk.ecaop.biz.sub.gdjk.OpenApply4GProcessor;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.google.common.collect.Maps;

public class ProductManagerUtils {

    /**
     * 产品公共节点获取
     * @param productInfo
     * @param provinceCode
     * @param msg msg中需要包含userId,serialNumber
     * @return
     */
    public static Map preProductInfo(List<Map> productInfo, Object provinceCode, Map msg) {

        LanOpenApp4GDao dao = new LanOpenApp4GDao();

        List<Map> productTypeList = new ArrayList<Map>();
        List<Map> productList = new ArrayList<Map>();

        List<Map> discntList = new ArrayList<Map>();
        List<Map> svcList = new ArrayList<Map>();
        List<Map> spList = new ArrayList<Map>();
        String monthLasttDay = "20501231235959";
        String monthFirstDay = GetDateUtils.getDate();
        Map ext = new HashMap();
        String methodCode = msg.get("methodCode") + "";
        Map activityTimeMap = new HashMap();
        String isFinalCode = "";
        // 处理活动
        List<Map> activityInfo = (List<Map>) msg.get("activityInfo");
        if (null != activityInfo && activityInfo.size() > 0) {
            for (int i = 0; i < activityInfo.size(); i++) {
                List<Map> packageElement = (List<Map>) activityInfo.get(i).get("packageElement");
                String actPlanId = String.valueOf(activityInfo.get(i).get("actPlanId"));
                Map proparam = new HashMap();
                proparam.put("PROVINCE_CODE", provinceCode);
                proparam.put("COMMODITY_ID", actPlanId);
                proparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                proparam.put("FIRST_MON_BILL_MODE", null);
                List<Map> productInfoResult = dao.queryActivityProductInfo(proparam);
                if (productInfoResult == null || productInfoResult.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品映射表中未查询到活动ID【" + actPlanId + "】的信息");
                }
                String newActPlanId = productInfoResult.get(0).get("PRODUCT_ID") + "";
                activityTimeMap = getEffectTime(newActPlanId, monthFirstDay, monthLasttDay);
                String actMonthFirstDay = (String) activityTimeMap.get("monthFirstDay");
                String actMonthLasttDay = (String) activityTimeMap.get("monthLasttDay");
                // 全业务要求转成14位 addby sss
                String actMonthLasttDayForMat = "";
                try {
                    actMonthLasttDayForMat = GetDateUtils.transDate(actMonthLasttDay, 14);
                }
                catch (Exception e) {
                    e.printStackTrace();
                    throw new EcAopServerBizException("9999", "时间格式转换失败");
                }
                msg.put("resActivityper", activityTimeMap.get("resActivityper"));
                // 附加包处理
                if (packageElement != null && packageElement.size() > 0) {
                    for (int n = 0; n < packageElement.size(); n++) {
                        Map peparam = new HashMap();
                        peparam.put("PROVINCE_CODE", provinceCode);
                        peparam.put("COMMODITY_ID", actPlanId);
                        peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                        peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                        peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                        List<Map> packageElementInfo = dao.queryPackageElementInfo(peparam);
                        if (packageElementInfo != null && packageElementInfo.size() > 0) {
                            for (Map packageMap : packageElementInfo) {
                                // 处理活动的生效失效时间

                                if ("D".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    Map dis = new HashMap();
                                    dis.put("productId", packageMap.get("PRODUCT_ID"));
                                    dis.put("productMode", packageMap.get("PRODUCT_MODE"));
                                    dis.put("packageId", packageMap.get("PACKAGE_ID"));
                                    dis.put("discntCode", packageMap.get("ELEMENT_ID"));
                                    dis.put("activityStarTime", actMonthFirstDay);
                                    dis.put("activityTime", actMonthLasttDayForMat);
                                    discntList.add(dis);
                                }
                                if ("S".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    Map svc = new HashMap();
                                    svc.put("serviceId", packageMap.get("ELEMENT_ID"));
                                    svc.put("productId", packageMap.get("PRODUCT_ID"));
                                    svc.put("packageId", packageMap.get("PACKAGE_ID"));
                                    svc.put("activityStarTime", actMonthFirstDay);
                                    svc.put("activityTime", actMonthLasttDayForMat);
                                    svcList.add(svc);
                                }
                            }
                        }
                    }
                }

                Map productTpye = new HashMap();
                Map product = new HashMap();

                String commodityId = actPlanId;
                String addProductId = "";
                String strBrandCode = "";
                String strProductTypeCode = "";
                String strProductMode = "";

                Map addproparam = new HashMap();
                addproparam.put("PROVINCE_CODE", provinceCode);
                addproparam.put("COMMODITY_ID", commodityId);
                // 原始表查询活动用 productid
                addproparam.put("PRODUCT_ID", commodityId);
                addproparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                addproparam.put("FIRST_MON_BILL_MODE", null);

                List<Map> addproductInfoResult = dao.queryAddProductInfo(addproparam);
                // 用于主副卡处理元素编码是A的问题
                msg.put("activityproductInfoResult", addproductInfoResult);
                if (null != addproductInfoResult && addproductInfoResult.size() > 0) {
                    for (int j = 0; j < addproductInfoResult.size(); j++) {

                        if ("D".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map dis = new HashMap();
                            dis.put("productId", addproductInfoResult.get(j).get("PRODUCT_ID"));
                            dis.put("productMode", addproductInfoResult.get(j).get("PRODUCT_MODE"));
                            dis.put("packageId", addproductInfoResult.get(j).get("PACKAGE_ID"));
                            dis.put("discntCode", addproductInfoResult.get(j).get("ELEMENT_ID"));
                            // 算出活动的开始结束时间，预提交下发
                            dis.put("activityStarTime", actMonthFirstDay);
                            dis.put("activityTime", actMonthLasttDayForMat);
                            discntList.add(dis);
                        }
                        if ("S".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map svc = new HashMap();
                            svc.put("serviceId", addproductInfoResult.get(j).get("ELEMENT_ID"));
                            svc.put("productId", addproductInfoResult.get(j).get("PRODUCT_ID"));
                            svc.put("packageId", addproductInfoResult.get(j).get("PACKAGE_ID"));
                            // 算出活动的开始结束时间，预提交下发
                            svc.put("activityStarTime", actMonthFirstDay);
                            svc.put("activityTime", actMonthLasttDayForMat);
                            svcList.add(svc);
                        }
                        if ("X".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            // 暂不处理活动下的sp;
                        }

                    }
                }
                Map actProParam = new HashMap();
                actProParam.put("PRODUCT_ID", newActPlanId);
                List<Map> actProductInfo = dao.queryActProductInfo(actProParam);
                if (actProductInfo == null || actProductInfo.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品表或者产品属性表中未查询到产品ID【" + newActPlanId + "】的产品信息");
                }
                strProductMode = String.valueOf(actProductInfo.get(0).get("PRODUCT_MODE"));
                strBrandCode = (String) actProductInfo.get(0).get("BRAND_CODE");
                strProductTypeCode = (String) actProductInfo.get(0).get("PRODUCT_TYPE_CODE");

                productTpye.put("productMode", strProductMode);
                productTpye.put("productId", newActPlanId);
                productTpye.put("productTypeCode", strProductTypeCode);
                // 算出活动的开始结束时间，预提交下发
                productTpye.put("activityStarTime", actMonthFirstDay);
                productTpye.put("activityTime", actMonthLasttDayForMat);

                product.put("brandCode", strBrandCode);
                product.put("productId", newActPlanId);
                product.put("productMode", strProductMode);
                // 算出活动的开始结束时间，预提交下发
                product.put("activityStarTime", actMonthFirstDay);
                product.put("activityTime", actMonthLasttDayForMat);

                productTypeList.add(productTpye);
                productList.add(product);

            }
        }// 活动信息处理结束

        for (int i = 0; i < productInfo.size(); i++) {
            List<Map> packageElement = (List<Map>) productInfo.get(i).get("packageElement");
            String productMode = String.valueOf(productInfo.get(i).get("productMode"));
            String firstMonBillMode = String.valueOf(productInfo.get(i).get("firstMonBillMode"));
            String productId = String.valueOf(productInfo.get(i).get("productId"));
            String isIpOrInterTv = "";
            if (null != productInfo.get(i).get("isIpOrInterTv")) {
                isIpOrInterTv = (String) productInfo.get(i).get("isIpOrInterTv");
            }
            System.out.println("isIpOrInterTvzyan525" + isIpOrInterTv);
            if ("0".equals(productMode)) {
                System.out.println("===========主产品产品处理");
                Map productTpye = new HashMap();
                Map product = new HashMap();

                String commodityId = productId;
                String strBrandCode = "";
                String strProductTypeCode = "";
                String strProductMode = "";

                Map proparam = new HashMap();
                proparam.put("PROVINCE_CODE", provinceCode);
                proparam.put("COMMODITY_ID", commodityId);
                proparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                proparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                List<Map> productInfoResult = dao.queryProductInfo(proparam);
                if (productInfoResult == null || productInfoResult.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品信息");
                }
                if ("mvoa".equals(methodCode) || "mofc".equals(methodCode) || "234s".equals(methodCode)) {
                    productInfoResult = chooseSpeed(productInfoResult, msg.get("phoneSpeedLevel") + "");
                }
                productId = String.valueOf(productInfoResult.get(0).get("PRODUCT_ID"));
                if (productId.equals("-1")) {
                    productId = String.valueOf(productInfoResult.get(1).get("PRODUCT_ID"));
                }
                Map itparam = new HashMap();
                itparam.put("PROVINCE_CODE", provinceCode);
                itparam.put("PID", productId);
                itparam.put("COMMODITY_ID", commodityId);
                itparam.put("PTYPE", "U");
                List<Map> productItemInfoResult = dao.queryProductItemInfo(itparam);
                if (productItemInfoResult == null || productItemInfoResult.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品属性信息");
                }
                for (int j = 0; j < productInfoResult.size(); j++) {

                    if ("D".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                        Map dis = new HashMap();
                        dis.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                        dis.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                        dis.put("discntCode", productInfoResult.get(j).get("ELEMENT_ID"));
                        dis.put("productMode", productInfoResult.get(j).get("PRODUCT_MODE"));
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
                            if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                partyId = (String) spItemInfo.get("ATTR_VALUE");
                            }
                            if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
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

                strProductMode = String.valueOf(productInfoResult.get(0).get("PRODUCT_MODE"));
                strBrandCode = getValueFromItem("BRAND_CODE", productItemInfoResult);
                strProductTypeCode = getValueFromItem("PRODUCT_TYPE_CODE", productItemInfoResult);

                productTpye.put("productMode", strProductMode);
                productTpye.put("productId", productId);
                productTpye.put("productTypeCode", strProductTypeCode);
                // 用于trade_user下面添加产品类型
                msg.put("mainProductTypeCode", strProductTypeCode);
                product.put("brandCode", strBrandCode);
                product.put("productId", productId);
                product.put("productMode", strProductMode);

                productTypeList.add(productTpye);
                productList.add(product);
                ext.put("brandCode", strBrandCode);
                ext.put("productTypeCode", strProductTypeCode);
                ext.put("mproductId", productId);
                // 附加包
                if (packageElement != null && packageElement.size() > 0) {
                    for (int n = 0; n < packageElement.size(); n++) {
                        Map peparam = new HashMap();
                        peparam.put("PROVINCE_CODE", provinceCode);
                        peparam.put("COMMODITY_ID", productId);
                        peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                        peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                        peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                        List<Map> packageElementInfo = dao.queryPackageElementInfo(peparam);
                        if (packageElementInfo != null && packageElementInfo.size() > 0) {
                            for (Map packageMap : packageElementInfo) {
                                if ("D".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    String elementId = String.valueOf(packageMap.get("ELEMENT_ID"));
                                    Map dis = new HashMap();
                                    dis.put("productId", packageMap.get("PRODUCT_ID"));
                                    dis.put("packageId", packageMap.get("PACKAGE_ID"));
                                    dis.put("discntCode", elementId);
                                    dis.put("productMode", packageMap.get("PRODUCT_MODE"));
                                    Map discntDateMap = getDiscntEffectTime(elementId, monthFirstDay, monthLasttDay);
                                    dis.put("activityStarTime", discntDateMap.get("monthFirstDay"));
                                    dis.put("activityTime", discntDateMap.get("monthLasttDay"));
                                    discntList.add(dis);
                                }
                                if ("S".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    Map svc = new HashMap();
                                    svc.put("serviceId", packageMap.get("ELEMENT_ID"));
                                    svc.put("productId", packageMap.get("PRODUCT_ID"));
                                    svc.put("packageId", packageMap.get("PACKAGE_ID"));
                                    svcList.add(svc);
                                }
                                if ("X".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    String spId = "-1";
                                    String partyId = "-1";
                                    String spProductId = "-1";
                                    Map spItemParam = new HashMap();
                                    spItemParam.put("PTYPE", "X");
                                    spItemParam.put("COMMODITY_ID", productId);
                                    spItemParam.put("PROVINCE_CODE", provinceCode);
                                    spItemParam.put("PID", packageMap.get("ELEMENT_ID"));
                                    List<Map> spItemInfoResult = dao.queryProductItemInfo(spItemParam);
                                    if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                        throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                                + packageMap.get("ELEMENT_ID") + "】的产品属性信息");
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
                                    sp.put("productId", packageMap.get("PRODUCT_ID"));
                                    sp.put("packageId", packageMap.get("PACKAGE_ID"));
                                    sp.put("partyId", partyId);
                                    sp.put("spId", spId);
                                    sp.put("spProductId", spProductId);
                                    sp.put("spServiceId", packageMap.get("ELEMENT_ID"));
                                    spList.add(sp);
                                }
                            }
                        }
                    }
                }
            }
            if ("1".equals(productMode)) {
                Map productTpye = new HashMap();
                Map product = new HashMap();

                String commodityId = productId;
                String addProductId = "";
                String strBrandCode = "";
                String strProductTypeCode = "";
                String strProductMode = "";
                String addProMonthFirstDay = "";
                String addProMonthLasttDay = "";
                Map addproparam = new HashMap();
                addproparam.put("PROVINCE_CODE", provinceCode);
                addproparam.put("COMMODITY_ID", commodityId);
                addproparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                addproparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                List<Map> addproductInfoResult = new ArrayList<Map>();
                System.out.println("isIpOrInterTvzyan526" + isIpOrInterTv);
                if (!StringUtils.isEmpty(isIpOrInterTv)) {
                    addproductInfoResult = dao.queryIptvOrIntertvProductInfo(addproparam);
                }
                else {
                    addproductInfoResult = dao.queryAddProductInfo(addproparam);
                }
                if (addproductInfoResult == null || addproductInfoResult.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品信息");
                }
                if ("mvoa".equals(methodCode) || "mofc".equals(methodCode)) {
                    addproductInfoResult = chooseSpeed(addproductInfoResult, msg.get("phoneSpeedLevel") + "");
                }
                addProductId = String.valueOf(addproductInfoResult.get(0).get("PRODUCT_ID"));
                isFinalCode = getEndDate(addProductId);
                System.out.println("开始==============" + monthFirstDay + monthLasttDay);

                System.out.println("=============isFinalCode" + isFinalCode);
                if ("N".equals(isFinalCode) || "X".equals(isFinalCode)) {
                    Map productDate = getEffectTime(addProductId, monthFirstDay, monthLasttDay);
                    addProMonthFirstDay = (String) productDate.get("monthFirstDay");
                    addProMonthLasttDay = (String) productDate.get("monthLasttDay");
                }
                else {
                    String activityTime = (String) activityTimeMap.get("monthLasttDay");
                    if (StringUtils.isEmpty(activityTime)) {
                        throw new EcAopServerBizException("9999", "所选附加产品" + commodityId + "生失效时间需要和合约保持一致，"
                                + "请检查合约信息是否已传或更换附加产品信息");
                    }
                    addProMonthFirstDay = (String) activityTimeMap.get("monthFirstDay");
                    addProMonthLasttDay = (String) activityTimeMap.get("monthLasttDay");
                }
                if (!"1".equals(isIpOrInterTv))// isIpOrInterTv为1的时候表示是互联网电视产品或iptv
                {
                    for (int j = 0; j < addproductInfoResult.size(); j++) {
                        if ("D".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            String elementId = String.valueOf(addproductInfoResult.get(j).get("ELEMENT_ID"));
                            Map dis = new HashMap();
                            dis.put("productId", addproductInfoResult.get(j).get("PRODUCT_ID"));
                            dis.put("packageId", addproductInfoResult.get(j).get("PACKAGE_ID"));
                            dis.put("discntCode", addproductInfoResult.get(j).get("ELEMENT_ID"));
                            dis.put("productMode", addproductInfoResult.get(j).get("PRODUCT_MODE"));
                            dis.put("activityTime", addProMonthLasttDay);
                            dis.put("activityStarTime", addProMonthFirstDay);
                            if (!"Y".equals(isFinalCode)) {
                                Map discntDateMap = getDiscntEffectTime(elementId, addProMonthFirstDay,
                                        addProMonthLasttDay);
                                dis.put("activityStarTime", discntDateMap.get("monthFirstDay"));
                                dis.put("activityTime", discntDateMap.get("monthLasttDay"));
                            }
                            else {
                                dis.put("activityStarTime", addProMonthFirstDay);
                                dis.put("activityTime", addProMonthLasttDay);
                            }
                            discntList.add(dis);
                        }
                        if ("S".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map svc = new HashMap();
                            svc.put("serviceId", addproductInfoResult.get(j).get("ELEMENT_ID"));
                            svc.put("productId", addproductInfoResult.get(j).get("PRODUCT_ID"));
                            svc.put("packageId", addproductInfoResult.get(j).get("PACKAGE_ID"));
                            svc.put("productMode", addproductInfoResult.get(j).get("PRODUCT_MODE"));
                            svc.put("activityTime", addProMonthLasttDay);
                            svc.put("activityStarTime", addProMonthFirstDay);
                            svcList.add(svc);
                        }
                        if ("X".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
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
                            sp.put("productMode", addproductInfoResult.get(j).get("PRODUCT_MODE"));
                            sp.put("activityTime", addProMonthLasttDay);
                            sp.put("activityStarTime", addProMonthFirstDay);
                            spList.add(sp);
                        }

                    }
                }
                Map additparam = new HashMap();
                additparam.put("PROVINCE_CODE", provinceCode);
                additparam.put("PID", addProductId);
                additparam.put("COMMODITY_ID", commodityId);
                additparam.put("PTYPE", "U");
                List<Map> addProductItemInfoResult = dao.queryProductItemInfo(additparam);
                if (addProductItemInfoResult == null || addProductItemInfoResult.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品属性信息");
                }

                strProductMode = String.valueOf(addproductInfoResult.get(0).get("PRODUCT_MODE"));
                strBrandCode = getValueFromItem("BRAND_CODE", addProductItemInfoResult);
                strProductTypeCode = getValueFromItem("PRODUCT_TYPE_CODE", addProductItemInfoResult);

                productTpye.put("productMode", strProductMode);
                productTpye.put("productId", addProductId);
                productTpye.put("productTypeCode", strProductTypeCode);
                productTpye.put("activityTime", addProMonthLasttDay);
                productTpye.put("activityStarTime", addProMonthFirstDay);
                product.put("activityTime", addProMonthLasttDay);
                product.put("activityStarTime", addProMonthFirstDay);
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
                        peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                        peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                        peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                        List<Map> packageElementInfo = dao.queryPackageElementInfo(peparam);
                        if (packageElementInfo != null && packageElementInfo.size() > 0) {
                            for (Map packageMap : packageElementInfo) {
                                if ("D".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    String elementId = String.valueOf(packageMap.get("ELEMENT_ID"));
                                    Map dis = new HashMap();
                                    dis.put("productId", packageMap.get("PRODUCT_ID"));
                                    dis.put("packageId", packageMap.get("PACKAGE_ID"));
                                    dis.put("discntCode", elementId);
                                    dis.put("productMode", packageMap.get("PRODUCT_MODE"));
                                    dis.put("activityStarTime", addProMonthFirstDay);
                                    dis.put("activityTime", addProMonthLasttDay);
                                    if (!"Y".equals(isFinalCode)) {
                                        Map discntDateMap = getDiscntEffectTime(elementId, addProMonthFirstDay,
                                                addProMonthLasttDay);
                                        dis.put("activityStarTime", discntDateMap.get("monthFirstDay"));
                                        dis.put("activityTime", discntDateMap.get("monthLasttDay"));
                                    }
                                    else {
                                        dis.put("activityStarTime", addProMonthFirstDay);
                                        dis.put("activityTime", addProMonthLasttDay);
                                    }
                                    discntList.add(dis);
                                }
                                if ("S".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    Map svc = new HashMap();
                                    svc.put("serviceId", packageMap.get("ELEMENT_ID"));
                                    svc.put("productId", packageMap.get("PRODUCT_ID"));
                                    svc.put("packageId", packageMap.get("PACKAGE_ID"));
                                    svc.put("activityStarTime", addProMonthFirstDay);
                                    svc.put("activityTime", addProMonthLasttDay);
                                    svcList.add(svc);
                                }
                                if ("X".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    String spId = "-1";
                                    String partyId = "-1";
                                    String spProductId = "-1";
                                    Map spItemParam = new HashMap();
                                    spItemParam.put("PTYPE", "X");
                                    spItemParam.put("COMMODITY_ID", productId);
                                    spItemParam.put("PROVINCE_CODE", provinceCode);
                                    spItemParam.put("PID", packageMap.get("ELEMENT_ID"));
                                    List<Map> spItemInfoResult = dao.queryProductItemInfo(spItemParam);
                                    if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                        throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                                + packageMap.get("ELEMENT_ID") + "】的产品属性信息");
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
                                    sp.put("productId", packageMap.get("PRODUCT_ID"));
                                    sp.put("packageId", packageMap.get("PACKAGE_ID"));
                                    sp.put("partyId", partyId);
                                    sp.put("spId", spId);
                                    sp.put("spProductId", spProductId);
                                    sp.put("spServiceId", packageMap.get("ELEMENT_ID"));
                                    sp.put("activityStarTime", addProMonthFirstDay);
                                    sp.put("activityTime", addProMonthLasttDay);
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
        discntList = NewProductUtils.newDealRepeat(discntList, "discntList");
        svcList = NewProductUtils.newDealRepeat(svcList, "svcList");
        spList = NewProductUtils.newDealRepeat(spList, "spList");
        productTypeList = NewProductUtils.newDealRepeat(productTypeList, "productTypeList");
        productList = NewProductUtils.newDealRepeat(productList, "productList");

        msg.put("discntList", discntList);
        msg.put("svcList", svcList);
        msg.put("spList", spList);
        msg.put("productTypeList", productTypeList);
        msg.put("productList", productList);
        // 增加活动结束时间
        // msg.put("activityTime", monthLasttDay);

        OpenApply4GProcessor openApplyPro = new OpenApply4GProcessor();
        ext.put("tradeProductType", openApplyPro.preProductTpyeListData(msg));
        ext.put("tradeProduct", openApplyPro.preProductData(msg));
        ext.put("tradeDiscnt", openApplyPro.preDiscntData(msg));
        ext.put("tradeSvc", openApplyPro.preTradeSvcData(msg));
        ext.put("tradeSp", openApplyPro.preTradeSpData(msg));
        return ext;

    }

    /**
     * 该方法用于校验附加产品的失效时间
     * 当返回值是Y时表示 附加产品的失效时间应该和合约保持一致
     * 当返回值是N时表示附加产品的失效时间是固定值需要自己计算
     * 返回值写死是X时，写死默认时间
     * 针对融合产品是必须传入对应cb侧的产品编码
     */
    public static String getEndDate(String addProductId) {
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        Map actProParam = new HashMap();
        String lengthType = "";
        actProParam.put("PRODUCT_ID", addProductId);
        List actProductInfo = dao.queryAddProductEndDate(actProParam);
        if (actProductInfo == null || actProductInfo.size() == 0) {
            lengthType = "X";
            return lengthType;
        }
        lengthType = (String) actProductInfo.get(0);
        return lengthType;
    }

    /**
     * 处理附加产品资费的生失效时间
     * 可以手动传入产品或活动的生失效时间
     */
    public static Map getDiscntEffectTime(String discntId, String monthFirstDay, String monthLasttDay) {
        String monthLastDay = "";
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        Map actiPeparam = new HashMap();
        actiPeparam.put("DISCNT_CODE", discntId);
        List<Map> activityList = dao.queryDiscntDate(actiPeparam);
        Map activityListMap = null;
        if (activityList != null && activityList.size() > 0) {
            activityListMap = activityList.get(0);

        }
        else {
            throw new EcAopServerBizException("9999", "在资费信息表中未查询到ID为【" + discntId + "】的资费信息");

        }
        try {
            String resActivityper = String.valueOf(activityListMap.get("END_OFFSET"));// 失效效偏移时间
            String endUnit = String.valueOf(activityListMap.get("END_UNIT"));// '0:天 1:自然天 2:月 3:自然月 4:年
            // 5:自然年';
            String endEnableTag = String.valueOf(activityListMap.get("END_ENABLE_TAG"));// 针对结束时间 0-绝对时间（看开始时间） 1-相对时间
            System.out.println("endEnableTag=============" + endEnableTag);
            String endDate = String.valueOf(activityListMap.get("END_ABSOLUTE_DATE"));// END_ENABLE_TAG为1时，需要才发此结束时间

            String enableTag = String.valueOf(activityListMap.get("ENABLE_TAG"));// 针对开始时间 0-绝对时间（看开始时间） 1-相对时间
            String strStartUnit = String.valueOf(activityListMap.get("START_UNIT"));// '0:天 1:自然天 2:月 3:自然月 4:年
            // 5:自然年';
            String startOffset = String.valueOf(activityListMap.get("START_OFFSET"));// 生效偏移时间
            // 如果值为空则进行默认处理
            if (!"null".equals(enableTag) && "1".equals(enableTag) && !"null".equals(startOffset)
                    && !"null".equals(strStartUnit)) {
                monthFirstDay = GetDateUtils.getSpecifyDateTime(Integer.parseInt(enableTag),
                        Integer.parseInt(strStartUnit), GetDateUtils.getSysdateFormat(), Integer.parseInt(startOffset));
            }
            if (!"null".equals(enableTag) && "0".equals(enableTag)) {
                startOffset = "0";
            }
            if (!"null".equals(resActivityper) && !"null".equals(endUnit) && !"null".equals(endEnableTag)
                    && "1".equals(endEnableTag)) {
                monthLastDay = GetDateUtils.getSpecifyDateTime(Integer.parseInt(endEnableTag),
                        Integer.parseInt(endUnit), GetDateUtils.getSysdateFormat(), Integer.parseInt(resActivityper)
                                + Integer.parseInt(startOffset));
                // 结束月最后一天
                monthLasttDay = GetDateUtils.getSpecifyDateTime(1, 6, monthLastDay, -1);
                monthLasttDay = GetDateUtils.transDate(monthLasttDay, 14);
            }
            if (monthLasttDay.length() > 19) {
                monthLasttDay = monthLasttDay.substring(0, 19);
            }
            actiPeparam.put("monthLasttDay", monthLasttDay);
            actiPeparam.put("monthFirstDay", monthFirstDay);
            return actiPeparam;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "获取资费信息生失效时间失败，资费是：" + discntId);
        }

    }

    /**
     * 根据ATTR_CODE取出相应的ATTR_VALUE
     * @param attrCode
     * @param infoList
     * @return
     */
    public static String getValueFromItem(String attrCode, List<Map> infoList) {
        String attrValue = "";
        for (int i = 0; i < infoList.size(); i++) {
            Map productItemInfo = infoList.get(i);
            if ("U".equals(productItemInfo.get("PTYPE"))) {
                if (attrValue == "" && attrCode.equals(productItemInfo.get("ATTR_CODE"))) {
                    attrValue = (String) productItemInfo.get("ATTR_VALUE");
                }
            }
        }
        return attrValue;
    }

    /**
     * 处理附加产品或者活动的失效时间
     * 可以手动传入产品或活动的生失效时间
     */
    public static Map getEffectTime(String actPlanId, String monthFirstDay, String monthLasttDay) {
        String monthLastDay = "";
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        Map actiPeparam = new HashMap();
        actiPeparam.put("PRODUCT_ID", actPlanId);
        List<Map> activityList = dao.queryProductAndPtypeProduct(actiPeparam);
        Map activityListMap = null;
        if (activityList != null && activityList.size() > 0) {
            activityListMap = activityList.get(0);

        }
        else {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到ID为【" + actPlanId + "】的信息");

        }
        try {
            String resActivityper = String.valueOf(activityListMap.get("END_OFFSET"));
            String enableTag = String.valueOf(activityListMap.get("ENABLE_TAG"));// 0-绝对时间（看开始时间） 1-相对时间
            String endEnableTag = String.valueOf(activityListMap.get("END_ENABLE_TAG"));// 0-绝对时间（看开始时间） 1-相对时间
            System.out.println("endEnableTag=============" + endEnableTag);
            String endDate = String.valueOf(activityListMap.get("END_ABSOLUTE_DATE"));// END_ENABLE_TAG为1时，需要才发此结束时间
            String strStartUnit = String.valueOf(activityListMap.get("START_UNIT"));// '0:天 1:自然天 2:月 3:自然月 4:年
            // 5:自然年';
            String endUnit = String.valueOf(activityListMap.get("END_UNIT"));
            String startOffset = String.valueOf(activityListMap.get("START_OFFSET"));// 生效偏移时间

            // 如果值为空则进行默认处理
            if (!"null".equals(enableTag) && !"null".equals(startOffset) && !"null".equals(strStartUnit)) {
                monthFirstDay = GetDateUtils.getSpecifyDateTime(Integer.parseInt(enableTag),
                        Integer.parseInt(strStartUnit), GetDateUtils.getSysdateFormat(), Integer.parseInt(startOffset));
            }
            if (!"null".equals(enableTag) && "0".equals(enableTag)) {
                startOffset = "0";
            }
            // 如果值为空则进行默认处理
            if (!"null".equals(endEnableTag) && "0".equals(endEnableTag) && !"null".equals(endDate)) {
                monthLasttDay = endDate;
            }
            if (!"null".equals(resActivityper) && !"null".equals(endUnit) && !"null".equals(endEnableTag)
                    && "1".equals(endEnableTag)) {
                monthLastDay = GetDateUtils.getSpecifyDateTime(Integer.parseInt(endEnableTag),
                        Integer.parseInt(endUnit), GetDateUtils.getSysdateFormat(), Integer.parseInt(resActivityper)
                                + Integer.parseInt(startOffset));
                // 结束月最后一天
                monthLasttDay = GetDateUtils.getSpecifyDateTime(1, 6, monthLastDay, -1);
                monthLasttDay = GetDateUtils.transDate(monthLasttDay, 14);

            }
            if (monthLasttDay.length() > 19) {
                monthLasttDay = monthLasttDay.substring(0, 19);
            }
            actiPeparam.put("monthLasttDay", monthLasttDay);
            actiPeparam.put("monthFirstDay", monthFirstDay);
            actiPeparam.put("resActivityper", resActivityper);
            return actiPeparam;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "获取产品或者活动信息生失效时间失败，产品是：" + actPlanId);
        }
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
     * CBSS 处理产品默认属性Td_b_Commodity_Trans_Item ITEM_ID取值 方式修改
     * @param productInfoSet
     * @param eparchyCode
     * @param startDate
     * @param endDate
     * @param serialNumber
     * @param nonFetchStr
     * @param extObject
     * @throws Exception
     */
    public static void dealProductItemInfo4G(List<Map> productInfoSet, String eparchyCode, String provinceCode,
            String userId, String startDate, String endDate, Object serialNumber, String nonFetchStr, Map extObject)
            throws Exception {
        String commodityId = (String) productInfoSet.get(0).get("COMMODITY_ID");
        for (int i = 0; i < productInfoSet.size(); i++) {
            Map productInfo = productInfoSet.get(i);
            String realStartDate = startDate;
            String realEndDate = endDate;
            if (StringUtils.isNotEmpty((String) productInfo.get("EFFECTIVE_MODE"))
                    && StringUtils.isNotEmpty(String.valueOf(productInfo.get("EFFECTIVE_EXCURSION")))) {
                int mode = Integer.parseInt((String) productInfo.get("EFFECTIVE_MODE"));
                int amount = Integer.parseInt(String.valueOf(productInfo.get("EFFECTIVE_EXCURSION")));
                realStartDate = GetDateUtils.getSpecifyDateTime(1, mode, startDate, amount);
            }
            if (StringUtils.isNotEmpty((String) productInfo.get("FAIL_MODE"))
                    && StringUtils.isNotEmpty((String) productInfo.get("FAIL_EXCURSION"))) {
                int mode = Integer.parseInt((String) productInfo.get("FAIL_MODE"));
                int amount = Integer.parseInt((String) productInfo.get("FAIL_EXCURSION"));
                realEndDate = GetDateUtils.getSpecifyDateTime(1, mode, startDate, amount);
                realEndDate = GetDateUtils.getSpecifyDateTime(1, 6, realEndDate, -1);
            }
            Map param = Maps.newHashMap();
            param.put("provinceCode", provinceCode);
            param.put("eparchyCode", eparchyCode);
            param.put("userId", userId);
            param.put("realStartDate", realStartDate);
            param.put("realEndDate", realEndDate);
            param.put("startDate", startDate);
            param.put("commodityId", commodityId);
            param.put("serialNumber", serialNumber);
            param.put("nonFetchStr", nonFetchStr);

            dealProductItemInfoBody4G(productInfo, param, extObject);
        }

    }

    /**
     * 将处理的产品的主体信息集中进行处理
     * @param param
     * @param productInfo
     * @param extObject
     * @throws Exception
     */
    public static void dealProductItemInfoBody4G(Map productInfo, Map param, Map extObject) throws Exception {
        String provinceCode = (String) param.get("provinceCode");
        String eparchyCode = (String) param.get("eparchyCode");
        String userId = (String) param.get("userId");
        String realStartDate = (String) param.get("realStartDate");
        String realEndDate = (String) param.get("realEndDate");
        String startDate = (String) param.get("startDate");
        String commodityId = (String) param.get("commodityId");
        String serialNumber = (String) param.get("serialNumber");
        String nonFetchStr = (String) param.get("nonFetchStr");
        String productId = String.valueOf(productInfo.get("PRODUCT_ID"));
        try {
            // 服务属性
            if ("S".equals(productInfo.get("ELEMENT_TYPE_CODE"))) {
                Map serviceItem = new HashMap();
                String itemId = getSequence(eparchyCode, "SEQ_ITEM_ID");
                serviceItem.put("userId", userId);
                serviceItem.put("productId", productId);
                serviceItem.put("packageId", productInfo.get("PACKAGE_ID"));
                serviceItem.put("serviceId", productInfo.get("ELEMENT_ID"));
                serviceItem.put("itemId", itemId);
                serviceItem.put("modifyTag", "0");
                serviceItem.put("startDate", realStartDate);
                serviceItem.put("endDate", realEndDate);
                serviceItem.put("userIdA", "-1");
                serviceItem.put("xDatatype", "NULL");

                extObject = TradeManagerUtils.addTab("tradeSvc", extObject, serviceItem);

                List<Map> serviceItemSet = getItemByPid(productInfo.get("ELEMENT_ID"), commodityId, provinceCode, "S");
                if (!CollectionUtils.isEmpty(serviceItemSet)) {
                    for (int j = 0; j < serviceItemSet.size(); j++) {
                        Map serviceItemInfo = serviceItemSet.get(j);
                        expProductItemTrade(extObject, serviceItemInfo, itemId, "2");
                    }
                }
            }
            // 资费属性
            else if ("D".equals(productInfo.get("ELEMENT_TYPE_CODE"))) {
                Map discntItem = Maps.newHashMap();
                String itemId = getSequence(eparchyCode, "SEQ_ITEM_ID");
                discntItem.put("id", userId);
                discntItem.put("idType", "1");
                discntItem.put("productId", productId);
                discntItem.put("packageId", productInfo.get("PACKAGE_ID"));
                discntItem.put("discntCode", productInfo.get("ELEMENT_ID"));
                discntItem.put("specTag", "1");
                discntItem.put("relationTypeCode", "");
                discntItem.put("itemId", itemId);
                discntItem.put("modifyTag", "0");
                discntItem.put("startDate", realStartDate);
                discntItem.put("endDate", realEndDate);
                discntItem.put("userIdA", "-1");
                discntItem.put("xDatatype", "NULL");

                extObject = TradeManagerUtils.addTab("tradeDiscnt", extObject, discntItem);

                List<Map> discntItemSet = getItemByPid(productInfo.get("ELEMENT_ID"), commodityId, provinceCode, "D");
                if (!CollectionUtils.isEmpty(discntItemSet)) {
                    for (int j = 0; j < discntItemSet.size(); j++) {
                        Map discntItemInfo = discntItemSet.get(j);
                        expProductItemTrade(extObject, discntItemInfo, itemId, "3");
                    }
                }
            }
            // SP信息
            else if ("X".equals(productInfo.get("ELEMENT_TYPE_CODE"))) {
                String spId = "-1";
                String partyId = "-1";
                String spProductId = "-1";
                List<Map> spItemSet = getItemByPid(productInfo.get("ELEMENT_ID"), commodityId, provinceCode, "X");
                if (!CollectionUtils.isEmpty(spItemSet)) {
                    for (int j = 0; j < spItemSet.size(); j++) {
                        Map spItemInfo = spItemSet.get(j);
                        if (spItemInfo.get("ATTR_CODE").equals("SP_ID")) {
                            spId = (String) spItemInfo.get("ATTR_VALUE");
                        }
                        else if (spItemInfo.get("ATTR_CODE").equals("PARTY_ID")) {
                            partyId = (String) spItemInfo.get("ATTR_VALUE");
                        }
                        else if (spItemInfo.get("ATTR_CODE").equals("SP_PRODUCT_ID")) {
                            spProductId = (String) spItemInfo.get("ATTR_VALUE");
                        }
                    }
                }
                Map spItem = new HashMap();
                String itemId = getSequence(eparchyCode, "SEQ_ITEM_ID");
                spItem.put("userId", userId);
                spItem.put("serialNumber", serialNumber);
                spItem.put("productId", productId);
                spItem.put("packageId", productInfo.get("PACKAGE_ID"));
                spItem.put("partyId", partyId);
                spItem.put("spId", spId);
                spItem.put("spProductId", spProductId);
                spItem.put("firstBuyTime", startDate);
                spItem.put("paySerialNumber", serialNumber);
                spItem.put("startDate", startDate);
                spItem.put("endDate", "2050-12-30 23:59:59");
                spItem.put("updateTime", startDate);
                spItem.put("remark", "");
                spItem.put("modifyTag", "0");
                spItem.put("payUserId", userId);
                spItem.put("spServiceId", productInfo.get("ELEMENT_ID"));
                spItem.put("itemId", itemId);
                spItem.put("xDatatype", "NULL");

                extObject = TradeManagerUtils.addTab("tradeSp", extObject, spItem);
            }
            // 用户属性(固化在产品上的属性)
            else if ("P".equals(productInfo.get("ELEMENT_TYPE_CODE"))) {
                List<Map> userItemSet = getItemByPid(productId, commodityId, provinceCode, "P");
                if (!CollectionUtils.isEmpty(userItemSet)) {
                    for (int j = 0; j < userItemSet.size(); j++) {
                        Map userItemInfo = userItemSet.get(j);
                        if (nonFetchStr != null && nonFetchStr.contains("," + userItemInfo.get("ATTR_CODE") + ",")) {
                            continue;
                        }
                        expProductItemTrade(extObject, userItemInfo, userId, "0");
                    }
                }

            }
        }
        catch (Exception ex) {
            throw ex;
        }

    }

    /**
     * 根据元素ID、类型、省分和产品ID获取产品映射元素表信息
     * @param pid
     * @param commodityId
     * @param provinceCode
     * @param pType
     * @return
     */
    public static List<Map> getItemByPid(Object pid, Object commodityId, String provinceCode, String pType) {
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        Map inMap = Maps.newHashMap();
        inMap.put("PROVINCE_CODE", provinceCode);
        inMap.put("PID", pid);
        inMap.put("COMMODITY_ID", commodityId);
        inMap.put("PTYPE", pType);
        return dao.queryProductItemInfo(inMap);
    }

    /**
     * 处理产品默认属性Td_b_Commodity_Trans_Item
     * @param extObject
     * @param productItemInfo
     * @param userId
     * @throws Exception
     */
    public static void expProductItemTrade(Map extObject, Map productItemInfo, String userId, String attrTypeCode)
            throws Exception {
        Map userSubItem = Maps.newHashMap();
        userSubItem.put("attrTypeCode", attrTypeCode);
        userSubItem.put("attrCode", productItemInfo.get("ATTR_CODE"));
        userSubItem.put("itemId", userId);
        userSubItem.put("attrValue", productItemInfo.get("ATTR_VALUE"));
        userSubItem.put("xDatatype", "NULL");
        if (extObject.containsKey("itemStartDate") && StringUtils.isNotEmpty((String) extObject.get("itemStartDate"))) {
            userSubItem.put("startDate", extObject.get("itemStartDate"));
        }
        if (extObject.containsKey("itemEndDate") && StringUtils.isNotEmpty((String) extObject.get("itemEndDate"))) {
            userSubItem.put("endDate", extObject.get("itemEndDate"));
        }
        extObject = TradeManagerUtils.addTab("tradeSubItem", extObject, userSubItem);
    }

    /**
     * 根据地州编码和SEQ名获取相应Sequence
     * @param eparchyCode
     * @param seqName
     * @return
     */
    public static String getSequence(String eparchyCode, String seqName) {
        Map inMap = Maps.newHashMap();
        inMap.put("EPARCHY_CODE", eparchyCode);
        inMap.put("SEQUENCE", seqName);
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        return dao.genSequence(inMap);
    }

    /**
     * 根据产品Id获取该产品下ITEM表中的所有数据
     * @param productId
     * @param commodityId
     * @param provinceCode
     * @return
     */
    public static List<Map> getProductItemWithoutPtype(String productId, String commodityId, String provinceCode) {
        Map inMap = Maps.newHashMap();
        inMap.put("PRODUCT_ID", productId);
        inMap.put("COMMODITY_ID", commodityId);
        inMap.put("PROVINCE_CODE", provinceCode);
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        return dao.queryProductItemInfoNoPtype(inMap);
    }

    /**
     * 根据包Id和元素Id获取该条资费/服务/SP/活动信息
     * @param commodityId
     * @param provinceCode
     * @param strEparchyCode
     * @param pkgId
     * @param eleId
     * @return
     */
    public static List<Map> getAppendProductInfo(String commodityId, String provinceCode, String strEparchyCode,
            String pkgId, String eleId) {
        Map inMap = Maps.newHashMap();
        inMap.put("COMMODITY_ID", commodityId);
        inMap.put("PROVINCE_CODE", provinceCode);
        inMap.put("EPARCHY_CODE", strEparchyCode);
        inMap.put("PACKAGE_ID", pkgId);
        inMap.put("ELEMENT_ID", eleId);
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        return dao.queryPackageElementInfo(inMap);
    }

    /**
     * 获取包数目上限
     * @param commodityId
     * @return
     */
    public static List<Map> getPkgMaxNum(String commodityId) {
        Map inMap = Maps.newHashMap();
        inMap.put("COMMODITY_ID", commodityId);
        DealNewCbssProduct n25dao = new DealNewCbssProduct();
        return n25dao.selectPkgMaxNum(inMap);
    }

    /**
     * 根据PTYPE获取TD_B_COMMODITY_TRANS_ITEM表数据
     * @param productId
     * @param commodityId
     * @param provinceCode
     * @param ptype
     * @return
     */
    public static List<Map> getProductItemInfo(String productId, String commodityId, String provinceCode, String ptype) {
        Map inMap = Maps.newHashMap();
        inMap.put("PID", productId);
        inMap.put("COMMODITY_ID", commodityId);
        inMap.put("PROVINCE_CODE", provinceCode);
        inMap.put("PTYPE", ptype);
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        return dao.queryProductItemInfo(inMap);
    }

    /**
     * 获取主产品或附加产品信息
     * @param commodityId
     * @param productMode
     * @param provinceCode
     * @param eparchyCode
     * @param firstMonBillMode
     * @return
     */
    public static List<Map> getProductInfoWithLimit(String commodityId, String productMode, String provinceCode,
            String eparchyCode, String firstMonBillMode) {
        if (StringUtils.isEmpty(firstMonBillMode)) {
            firstMonBillMode = "02";
        }
        Map inMap = Maps.newHashMap();
        inMap.put("COMMODITY_ID", commodityId);
        inMap.put("PRODUCT_MODE", productMode);
        inMap.put("PROVINCE_CODE", provinceCode);
        inMap.put("EPARCHY_CODE", eparchyCode);
        inMap.put("FIRST_MON_BILL_MODE", firstMonBillMode);
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        return dao.queryProductInfo(inMap);
    }

    public static List<Map> getProductInfoWithLimit(String commodityId, String productMode, String provinceCode,
            String eparchyCode, String firstMonBillMode, String combAdvance) {
        if (StringUtils.isEmpty(firstMonBillMode)) {
            firstMonBillMode = "02";
        }
        Map inMap = Maps.newHashMap();
        inMap.put("COMMODITY_ID", commodityId);
        inMap.put("PRODUCT_MODE", productMode);
        inMap.put("PROVINCE_CODE", provinceCode);
        inMap.put("EPARCHY_CODE", eparchyCode);
        inMap.put("FIRST_MON_BILL_MODE", firstMonBillMode);
        inMap.put("isCombAdvance", combAdvance);
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        return dao.queryProductInfo(inMap);
    }

    /**
     * 根据ELEMENT_ID查PACKAGE
     * @param productId
     * @param commodityId
     * @param provinceCode
     * @param serviceId
     * @return
     */
    public static List<Map> getPkgIdBySvcid(Object productId, Object commodityId, String provinceCode, String serviceId) {
        Map inMap = Maps.newHashMap();
        inMap.put("PRODUCT_ID", productId);
        inMap.put("COMMODITY_ID", commodityId);
        inMap.put("ELEMENT_ID", serviceId);
        inMap.put("PROVINCE_CODE", provinceCode);
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        return dao.queryPackageBySvcId(inMap);
    }

    /**
     * 将数据库里查出短信网龄的默认下移一个月去掉
     * @param productInfoSet
     * @return
     */
    public static List<Map> preDealProductInfo(List<Map> proInfoList) {
        for (int i = 0; i < proInfoList.size(); i++) {
            Map info = proInfoList.get(i);
            if ("5702000".equals(info.get("ELEMENT_ID"))) {
                info.put("EFFECTIVE_MODE", "");
                info.put("EFFECTIVE_EXCURSION", "");
            }
        }
        return proInfoList;
    }

    public static void dealAppPackage(List<Map> pe, String actPlanId, String strEparchyCode, String provinceCode,
            String acceptDate, String userId, Map extObject, Object serialNumber, String mode, String monthFirstDay,
            String monthLasttDay) throws Exception {
        for (int m = 0; m < pe.size(); m++) {
            Map peBuf = pe.get(m);
            String pkgId = (String) peBuf.get("packageId");
            String elmId = (String) peBuf.get("elementId");
            List<Map> appendProduct = getAppendProductInfo(actPlanId, provinceCode, strEparchyCode, pkgId, elmId);
            if (CollectionUtils.isEmpty(appendProduct)) {
                List<Map> productItem = new ArrayList<Map>();
                String enableTag = "";
                String startOffset = "";
                String startunit = "";
                String endSet = "";
                String startDate = "";

                for (int n = 0; n < appendProduct.size(); n++) {
                    Map apPro = appendProduct.get(n);
                    String eId = (String) apPro.get("ELEMENT_ID");
                    // 根据commidity_id 999999 ,及ELELMENT_ID查出属性
                    // if 没有查到走下面appendProduct分支2 的截止时间
                    productItem = getProductItemWithoutPtype(eId, "99999", provinceCode);
                    List<Map> apSets = new ArrayList<Map>();
                    apSets.add(apPro);
                    // appendProduct分支1
                    if (productItem != null && productItem.size() > 0) // 实际循环应该在2次左右
                    {
                        startOffset = TradeManagerUtils.getValueFromItem("START_OFFSET", productItem);
                        enableTag = TradeManagerUtils.getValueFromItem("ENABLE_TAG", productItem);
                        startunit = TradeManagerUtils.getValueFromItem("START_UNIT", productItem);
                        endSet = TradeManagerUtils.getValueFromItem("END_SET", productItem);
                        startDate = GetDateUtils.getSpecifyDateTime(Integer.parseInt(enableTag),
                                Integer.parseInt(startunit), acceptDate, Integer.parseInt(startOffset));
                        if (StringUtils.isNotEmpty(endSet)) {
                            monthLasttDay = GetDateUtils.getSpecifyDateTime(Integer.parseInt(enableTag),
                                    Integer.parseInt(startunit), acceptDate,
                                    Integer.parseInt(endSet) + Integer.parseInt(startOffset));
                            monthLasttDay = GetDateUtils.getSpecifyDateTime(1, 6, monthLasttDay, -1);
                        }
                        ProductManagerUtils.dealProductItemInfo4G(apSets, strEparchyCode, provinceCode, userId,
                                startDate, monthLasttDay, serialNumber, null, extObject);
                    }
                    else {
                        // appendProduct分支2
                        ProductManagerUtils.dealProductItemInfo4G(apSets, strEparchyCode, provinceCode, userId,
                                monthFirstDay, monthLasttDay, serialNumber, null, extObject);
                    }
                } // END for(int n=0;
            }// END if (appendProduct!
        }// /for(int m=0 END

    }

    /**
     * 从TD_B_PRODUCT , TD_B_PTYPE_PRODUCT获取产品数据
     * @param subProductId
     * @return
     */
    public static List<Map> getProductAndPtypeProuct(String subProductId) {
        Map inMap = Maps.newHashMap();
        inMap.put("PRODUCT_ID", subProductId);
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        return dao.queryProductAndPtypeProduct(inMap);
    }

    /**
     * 根据商城传入的带宽选 择速率
     * @param productInfoSet
     * @return
     */
    public static List<Map> chooseSpeed(List<Map> productInfoList, Object speed) {
        String LTE = "100";
        String plus4G = "300";
        System.out.println("*****************speed*************************" + speed + "**************!!!!!!!!!!!");
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
        for (int m = 0; m < productInfoList.size(); m++) {
            if (kick1st.equals(String.valueOf(productInfoList.get(m).get("ELEMENT_ID")))
                    || kick2nd.equals(String.valueOf(productInfoList.get(m).get("ELEMENT_ID")))) {
                newProductInfo.add(productInfoList.get(m));
            }
        }
        productInfoList.removeAll(newProductInfo);
        return productInfoList;
    }

    public static List<Map> getAppendProductByEleList(String commodityId, String productMode, String strEparchyCode,
            String strProvinceCode, List<String> elementIdList) throws Exception {
        List<Map> packAndElementSet = new ArrayList();
        Map<String, Object> param = new HashMap<String, Object>();
        param.put("COMMODITY_ID", commodityId);
        param.put("PRODUCT_MODE", productMode);
        param.put("PROVINCE_CODE", strProvinceCode);
        param.put("EPARCHY_CODE", strEparchyCode);
        param.put("eles", elementIdList);
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        packAndElementSet = dao.queryAppProductByEleList(param);
        return packAndElementSet;
    }

}
