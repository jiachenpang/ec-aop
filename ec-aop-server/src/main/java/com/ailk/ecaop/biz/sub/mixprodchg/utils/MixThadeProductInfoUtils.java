package com.ailk.ecaop.biz.sub.mixprodchg.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;

import com.ailk.ecaop.biz.product.DealNewCbssProduct;
import com.ailk.ecaop.biz.sub.mixprodchg.entry.MixProductChgSuperUser;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.NewProductUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;

/**
 * 拼装融合产品、资费、服务订购和退订等参数
 * @author zhaok
 *
 */
public class MixThadeProductInfoUtils {

    private DealNewCbssProduct n25Dao = new DealNewCbssProduct();
    private String BOOK = "00";// 订购
    private String UNBOOK = "01";// 退订

    private String MAIN = "00";// 主产品
    private String SUB = "01";// 附加产品

    /**
     *拼装产品信息  
     * @param threePartRet 三户返回信息
     * @param msg
     * @param ext
     * @param properties 需要在下面参数中用到的放在properties，不放在msg中
     * @param productInfo 
     * @param serialNumber 
     * @param userType 
     * @throws Exception 
     */
    public void preProductInfo(MixProductChgSuperUser user) throws Exception {
        List<Map> productInfo = user.getProductInfo();
        Map msg = user.getMsg();
        Map properties = user.getProperties();
        String userType = user.getUserType();
        String serialNumber = user.getSerialNumber();
        Map ext = user.getExt();
        // 获取三户返回的老产品信息
        List<Map> oldProducts = (List<Map>) user.getThreePartRet().get("productInfo");
        Map userInfo = (Map) user.getThreePartRet().get("userInfo");
        String userId = String.valueOf(userInfo.get("userId"));
        // 地市从三户中取的
        String eparchyCode = String.valueOf(userInfo.get("eparchyCode"));
        String newProductId = String.valueOf(userInfo.get("productId"));
        // 在下面拼元素时要用
        properties.put("userId", userId);
        properties.put("eparchyCode", eparchyCode);

        if (IsEmptyUtils.isEmpty(user.getProductInfo())) {
            throw new EcAopServerBizException("9", "业务异常:发起方未下发产品信息");
        }
        String provinceCode = "00" + msg.get("province");
        String acceptDate = GetDateUtils.transDate(GetDateUtils.getDate(), 19);
        String nextMon1stDay = GetDateUtils.getSpecifyDateTime(1, 3, acceptDate, 1);
        String monthLasttDay = "20501231235959";
        String monthFirstDay = GetDateUtils.getDate();
        Map activityTimeMap = new HashMap();

        List<Map> productTypeList = new ArrayList<Map>();
        List<Map> productList = new ArrayList<Map>();
        List<Map> discntList = new ArrayList<Map>();
        List<Map> svcList = new ArrayList<Map>();
        List<Map> spList = new ArrayList<Map>();

        List<Map> tradeOtherList = new ArrayList<Map>();

        // 处理活动
        List<Map> activityInfo = (List<Map>) msg.get("activityInfo");
        if (null != activityInfo && activityInfo.size() > 0) {
            for (int i = 0; i < activityInfo.size(); i++) {
                List<Map> packageElement = (List<Map>) activityInfo.get(i).get("packageElement");
                String actPlanId = String.valueOf(activityInfo.get(i).get("actPlanId"));
                Map proparam = new HashMap();
                List<Map> packageElementInfo = new ArrayList<Map>();
                proparam.put("PROVINCE_CODE", provinceCode);
                proparam.put("PRODUCT_ID", actPlanId);
                String newActPlanId = n25Dao.queryActivityByCommodityId(proparam).toString();
                activityTimeMap = new TradeManagerUtils().getEffectTime(newActPlanId, monthFirstDay, monthLasttDay);
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
                // 附加包处理
                if (packageElement != null && packageElement.size() > 0) {
                    for (int n = 0; n < packageElement.size(); n++) {
                        Map peparam = new HashMap();
                        peparam.put("PROVINCE_CODE", provinceCode);
                        peparam.put("PRODUCT_ID", actPlanId);
                        peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                        peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                        peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                        packageElementInfo = n25Dao.queryPackageElementInfo(peparam);
                        if (packageElementInfo != null && packageElementInfo.size() > 0) {
                            for (Map packageMap : packageElementInfo) {

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

                String addProductId = "";
                String strBrandCode = "";
                String strProductTypeCode = "";
                String strProductMode = "";

                Map addproparam = new HashMap();
                addproparam.put("PROVINCE_CODE", provinceCode);
                addproparam.put("PRODUCT_MODE", "50");
                addproparam.put("PRODUCT_ID", actPlanId);
                addproparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                addproparam.put("FIRST_MON_BILL_MODE", null);
                List<Map> addproductInfoResult = n25Dao.qryDefaultPackageElement(addproparam);

                // 原有方法是从查询默认产品的结果中取的，如果该产品不存在默认的包和元素（即addproductInfoResult为空）则会出现数组越界，
                // 现在改为优先取默认查询的结果，如果不存在默认的包和元素则去取附加元素处理时查询的结果。
                if (addproductInfoResult == null || addproductInfoResult.size() == 0) {
                    List<Map> activityInfoList = n25Dao.qryProductInfoByProductTable(addproparam);
                    if (!IsEmptyUtils.isEmpty(activityInfoList)) {
                        if (packageElementInfo != null && packageElementInfo.size() > 0) {
                            strBrandCode = String.valueOf(packageElementInfo.get(0).get("BRAND_CODE"));
                            strProductTypeCode = String.valueOf(packageElementInfo.get(0).get("PRODUCT_TYPE_CODE"));
                        }
                        else {
                            throw new EcAopServerBizException("9999", "未查询到附加包信息");
                        }
                    }
                    else {
                        throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + actPlanId + "】的产品信息");
                    }
                }
                else {
                    strBrandCode = (String) addproductInfoResult.get(0).get("BRAND_CODE");
                    strProductTypeCode = (String) addproductInfoResult.get(0).get("PRODUCT_TYPE_CODE");
                }
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
                strProductMode = "50";

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
            Map productInfoMap = productInfo.get(i);
            String optType = (String) productInfoMap.get("optType");// 00-订购,01-退订,
            String productId = (String) productInfoMap.get("productId");
            if ("00|01".contains(optType)) {
                throw new EcAopServerBizException("9999", "产品[" + productId + "]的操作标识[" + optType
                        + "]不在取值范围[00,01]中,请确认");
            }
            String firstMonBillMode = "02";// 订购时默认首月全量全价
            String productMode = (String) productInfoMap.get("productMode");
            List<Map> packageElement = (List<Map>) productInfoMap.get("packageElement");
            if (!"0,1".contains(productMode)) {
                throw new EcAopServerBizException("9999", "发起方输入的产品 [" + productId + "] 的产品编码有误,产品编码:["
                        + productMode + "]");
            }
            productMode = "0".equals(productMode) ? "00" : "01";// 0-主产品,1- 可选产品
            if (SUB.equals(productMode)) {
                Map tempMap = MapUtils.asMap("PRODUCT_ID", productId, "PROVINCE_CODE", provinceCode);
                productMode = (String) n25Dao.queryProductModeByCommodityId(tempMap);
            }
            if ("50".equals(productMode)) {
                // 如果是移网报错提示传到活动节点下面
                if ("1".equals(userType)) {
                    throw new EcAopServerBizException("9999", "请把活动信息传到活动节点下面!");
                }
                else {
                    throw new EcAopServerBizException("9999", "发起方输入的产品 [" + productId + "] 的产品编码有误,产品编码:["
                            + productMode + "]");
                }
            }

            // 退订
            if (UNBOOK.equals(optType)) {
                Map inMap = new HashMap();
                inMap.put("PRODUCT_ID", productId);
                inMap.put("PRODUCT_MODE", productMode);
                inMap.put("PROVINCE_CODE", provinceCode);
                inMap.put("EPARCHY_CODE", eparchyCode);
                Map productInfoResult = n25Dao.queryProductInfoByProductId(inMap);

                String unProductId = String.valueOf(productInfoResult.get("PRODUCT_ID"));
                String unProductMode = (String) productInfoResult.get("PRODUCT_MODE");
                String unBrandCode = (String) productInfoResult.get("BRAND_CODE");
                String unProductTypeCode = (String) productInfoResult.get("PRODUCT_TYPE_CODE");
                // 退订的产品生效时间取原生效时间,失效时间为本月底
                String productStartDate = acceptDate;
                String productEndDate = GetDateUtils.getMonthLastDay();
                for (int m = 0; m < oldProducts.size(); m++) {
                    Map pInfo = oldProducts.get(m);
                    if (unProductId.equals(pInfo.get("productId"))) {
                        productStartDate = (String) pInfo.get("productActiveTime");
                        productStartDate = GetDateUtils.transDate(productStartDate, 19);
                    }
                }
                Map paraMap = new HashMap();
                paraMap.put("productMode", unProductMode);
                paraMap.put("productId", unProductId);
                paraMap.put("productTypeCode", unProductTypeCode);
                paraMap.put("brandCode", unBrandCode);
                paraMap.put("modifyTag", "1");
                paraMap.put("productStartDate", productStartDate);
                paraMap.put("productEndDate", productEndDate);
                paraMap.put("eparchyCode", eparchyCode);
                paraMap.put("userId", userId);
                // 拼装退订的产品节点
                preProductItem(productList, productTypeList, paraMap);

                Map tradeOther = new HashMap();
                tradeOther.put("xDatatype", "NULL");
                tradeOther.put("modifyTag", "1");
                tradeOther.put("rsrvStr1", unProductId);
                tradeOther.put("rsrvStr2", unProductMode);
                tradeOther.put("rsrvStr3", "-9");
                tradeOther.put("rsrvStr4", unProductTypeCode);
                tradeOther.put("rsrvStr5", unProductTypeCode);
                tradeOther.put("rsrvStr6", "-1");
                tradeOther.put("rsrvStr7", "0");
                tradeOther.put("rsrvStr8", "H007"); // 退订统一写H007
                tradeOther.put("rsrvStr9", unBrandCode);
                tradeOther.put("rsrvStr10", serialNumber);
                tradeOther.put("rsrvValueCode", "NEXP");
                tradeOther.put("rsrvValue", userId);
                tradeOther.put("startDate", productStartDate);
                tradeOther.put("endDate", productEndDate);
                tradeOtherList.add(tradeOther);
            }
            // 订购
            if (BOOK.equals(optType)) {
                String isFinalCode = "";
                String productStartDate = GetDateUtils.getNextMonthFirstDayFormat();
                String productEndDate = "2050-12-31 23:59:59";
                if (!MAIN.equals(productMode)) { // 附加产品处理生效失效时间
                    productStartDate = acceptDate;// 附加产品默认立即生效
                    isFinalCode = "N";
                    String endDateType = NewProductUtils.getEndDateType(productId);
                    if (IsEmptyUtils.isEmpty(endDateType)) {
                        isFinalCode = "X";
                    }
                    else {
                        isFinalCode = endDateType;
                    }
                    if ("N,X".contains(isFinalCode)) {// 附加产品有效期按偏移计算
                        Map dateMap = TradeManagerUtils.getEffectTime(productId, productStartDate, productEndDate);
                        productStartDate = (String) dateMap.get("monthFirstDay");// 附加产品的生效时间
                        productEndDate = (String) dateMap.get("monthLasttDay");// 附加产品的失效时间
                    }
                }
                String bookProductId = "";
                String bookProductMode = "";
                String bookBrandCode = "";
                String bookProductTypeCode = "";
                // 查询产品的默认属性信息
                Map temp = new HashMap();
                temp.put("PROVINCE_CODE", provinceCode);
                temp.put("PRODUCT_ID", productId);
                temp.put("EPARCHY_CODE", eparchyCode);
                temp.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                temp.put("PRODUCT_MODE", productMode);
                List<Map> productInfoResult = n25Dao.qryDefaultPackageElement(temp);

                // 未查到产品下的默认元素
                if (IsEmptyUtils.isEmpty(productInfoResult)) {
                    if (MAIN.equals(productMode)) {
                        throw new EcAopServerBizException("9999", "根据产品Id [" + productId + "] 未查询到产品信息");
                    }
                    Map productInfos = n25Dao.queryProductInfoByProductId(temp);
                    if (!IsEmptyUtils.isEmpty(productInfos)) {
                        bookProductId = String.valueOf(productInfos.get("PRODUCT_ID"));
                        bookProductMode = (String) productInfos.get("PRODUCT_MODE");
                        bookBrandCode = (String) productInfos.get("BRAND_CODE");
                        bookProductTypeCode = (String) productInfos.get("PRODUCT_TYPE_CODE");
                    }
                    else {
                        throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + productId + "】的产品信息");
                    }
                }
                else {
                    bookProductId = String.valueOf(productInfoResult.get(0).get("PRODUCT_ID"));
                    bookProductMode = (String) productInfoResult.get(0).get("PRODUCT_MODE");
                    bookBrandCode = (String) productInfoResult.get(0).get("BRAND_CODE");
                    bookProductTypeCode = (String) productInfoResult.get(0).get("PRODUCT_TYPE_CODE");
                }
                if (MAIN.equals(productMode)) { // 订购的是主产品需要修改用户使用的主套餐
                    newProductId = bookProductId;
                }
                List<Map> allProductInfo = new ArrayList<Map>();
                // 有附加包
                if (!IsEmptyUtils.isEmpty(packageElement)) {
                    List<Map> packageElementInfo = new ArrayList<Map>();
                    for (Map elementMap : packageElement) {
                        Map peparam = new HashMap();
                        peparam.put("PROVINCE_CODE", provinceCode);
                        peparam.put("PRODUCT_ID", productId);
                        peparam.put("EPARCHY_CODE", eparchyCode);
                        peparam.put("PACKAGE_ID", elementMap.get("packageId"));
                        peparam.put("ELEMENT_ID", elementMap.get("elementId"));
                        List<Map> packEleInfo = n25Dao.queryPackageElementInfo(peparam);
                        if (!IsEmptyUtils.isEmpty(packEleInfo)) {
                            packageElementInfo.addAll(packEleInfo);
                        }
                        else {
                            throw new EcAopServerBizException("9999", "订购的元素[" + elementMap.get("elementId")
                                    + "]在产品[" + bookProductId + "]下未查询到,请重试");
                        }
                    }
                    if (!IsEmptyUtils.isEmpty(packageElementInfo)) {
                        // 拼装附加包的属性信息
                        allProductInfo.addAll(packageElementInfo);
                    }
                }
                if (!IsEmptyUtils.isEmpty(allProductInfo)) {
                    preProductInfo(allProductInfo, discntList, svcList, spList, "0", isFinalCode, productStartDate,
                            productEndDate, properties, serialNumber);
                }
                Map paraMap = new HashMap();
                paraMap.put("productMode", bookProductMode);
                paraMap.put("productId", bookProductId);
                paraMap.put("productTypeCode", bookProductTypeCode);
                paraMap.put("brandCode", bookBrandCode);
                paraMap.put("modifyTag", "0"); // 订购
                paraMap.put("productStartDate", productStartDate);
                paraMap.put("productEndDate", productEndDate);
                paraMap.put("userId", userId);
                paraMap.put("eparchyCode", eparchyCode);
                // 后面拼base是用
                properties.put("bookBrandCode", bookBrandCode);
                // 拼装订购产品节点
                preProductItem(productList, productTypeList, paraMap);
            }
        }

        // 去重
        discntList = NewProductUtils.newDealRepeat(discntList, new String[]{"productId", "packageId", "discntCode",
                "modifyTag"});
        svcList = NewProductUtils.newDealRepeat(svcList, new String[]{"productId", "packageId", "serviceId",
                "modifyTag"});
        spList = NewProductUtils.newDealRepeat(spList, new String[]{"productId", "packageId", "spServiceId",
                "modifyTag"});
        productTypeList = NewProductUtils.newDealRepeat(productTypeList, new String[]{"productId", "productMode",
                "productTypeCode", "modifyTag"});
        productList = NewProductUtils.newDealRepeat(productList, new String[]{"productId", "productMode",
                "brandCode", "modifyTag"});

        ext.put("tradeProductType", preDataUtil(productTypeList));
        ext.put("tradeProduct", preDataUtil(productList));
        ext.put("tradeDiscnt", preDataUtil(discntList));
        ext.put("tradeSvc", preDataUtil(svcList));
        ext.put("tradeSp", preDataUtil(spList));
        ext.put("tradeOther", preDataUtil(tradeOtherList));

        properties.put("newProductId", newProductId);

    }

    /**
     * 将节点放入item中返回
     * @param dataList
     * @param dataKey
     * @return
     */
    private Map preDataUtil(List<Map> dataList) {
        Map dataMap = new HashMap();
        dataMap.put("item", dataList);
        return dataMap;
    }

    private void preProductItem(List<Map> productList, List<Map> productTypeList, Map paraMap) {
        // 拼装产品节点
        Map productItem = new HashMap();
        productItem.put("userId", paraMap.get("userId"));
        productItem.put("productMode", paraMap.get("productMode"));
        productItem.put("productId", paraMap.get("productId"));
        productItem.put("productTypeCode", paraMap.get("productTypeCode"));
        productItem.put("brandCode", paraMap.get("brandCode"));
        productItem.put("itemId", TradeManagerUtils.getSequence((String) paraMap.get("eparchyCode"), "SEQ_ITEM_ID"));
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
     * 根据查询到的元素属性,拼装节点信息 discntList,svcList,spList
     * @param productInfoResult 元素
     * @param discntList
     * @param svcList
     * @param spList
     * @param modifyTag 0-订购,1-退订
     * @param isFinalCode N,X-生效失效时间按配置计算 Y-用传进来的时间(主产品的时候无值)
     * @param startDate 产品的生效时间
     * @param endDate 产品的失效时间
     * @param properties 需要传递的参数，都放在properties中
     * @param serialNumber 
     */
    private void preProductInfo(List<Map> productInfoResult, List<Map> discntList, List<Map> svcList, List<Map> spList,
            String modifyTag, String isFinalCode, String startDate, String endDate, Map<String, Object> properties,
            String serialNumber) {

        String eparchyCode = (String) properties.get("eparchyCode");
        String userId = (String) properties.get("userId");

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
                // 订购主产品下资费全部按照偏移计算
                String productMode = String.valueOf(productInfoResult.get(j).get("PRODUCT_MODE"));
                if ("00".equals(productMode) && "0".equals(modifyTag) || StringUtils.isNotEmpty(isFinalCode)
                        && !"Y".equals(isFinalCode)) {
                    Map discntDateMap = NewProductUtils.getMainProDiscntEffectTime4ChangePro(elementId, startDate,
                            endDate);
                    if ("false".equals(discntDateMap.get("discntEndTag"))) {// 如果资费没失效,则按照偏移计算
                        dis.put("startDate", discntDateMap.get("monthFirstDay"));
                        dis.put("endDate", discntDateMap.get("monthLasttDay"));
                    }
                }
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
                spItemParam.put("PROVINCE_CODE", properties.get("provinceCode"));
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
                sp.put("serialNumber", serialNumber);
                sp.put("firstBuyTime", startDate);
                sp.put("paySerialNumber", serialNumber);
                sp.put("startDate", startDate);
                sp.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
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
}
