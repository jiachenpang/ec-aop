package com.ailk.ecaop.biz.res;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.processor.Xml2JsonMappingProcessor;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.alibaba.fastjson.JSON;

public class ActivityInfoProcessor {

    public Map process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        // 获取msg信息
        Object msgObject = body.get("msg");
        Map msg = null;
        if (msgObject instanceof String) {
            msg = JSON.parseObject(msgObject.toString());
        }
        else {
            msg = (Map) msgObject;
        }
        List<Map> productTypeList = (List<Map>) msg.get("productTypeList");
        List<Map> productList = (List<Map>) msg.get("productList");
        List<Map> discntList = (List<Map>) msg.get("discntList");
        List<Map> svcList = (List<Map>) msg.get("svcList");
        List<Map> spList = (List<Map>) msg.get("spList");
        String resourceFee = "";// 终端费用,现在规范中没有，需要加进来
        String resourcesCode = "";
        String acceptDate = GetDateUtils.getDate();
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        // DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        // 生成cb订单号
        String cbOrderid = GetSeqUtil.getSeqFromCb(exchange, "seq_trade_id");
        msg.put("tradeId", cbOrderid);
        // 转换地市编码
        String provinceCode = "00" + (String) (msg.get("province"));
        msg.put("provinceCode", provinceCode);
        // Map param = new HashMap();
        // param.put("province", (msg.get("province")));
        // param.put("city", (msg.get("city")));
        // param.put("provinceCode", provinceCode);
        // List<Map> eparchyCoderesult = dao.queryEparchyCode(param);
        // eparchyCode不从库中获取
        String eparchyCode = ChangeCodeUtils.changeEparchy(msg);
        msg.put("eparchyCode", eparchyCode);
        // 获取员工信息
        String tradeStaffId = (String) (msg.get("operatorId"));
        msg.put("tradeStaffId", tradeStaffId);
        msg.put("checkMode", "1");
        // 调员工信息校验接口
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.param.mapping.sfck", exchange);
        CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");// 员工信息校验接口
        Xml2JsonMappingProcessor proc = new Xml2JsonMappingProcessor();
        proc.applyParams(new String[] { "ecaop.template.3g.staffcheck" });
        proc.process(exchange);
        Map retStaffinfo = exchange.getOut().getBody(Map.class);
        String departId = (String) (retStaffinfo.get("departId"));
        String cityCode = (String) (retStaffinfo.get("district"));
        msg.put("departId", departId);

        List<Map> activityInfo = (List<Map>) ((Map) msg.get("newUserInfo")).get("activityInfo");
        List<Map> resourcesInfo = (List<Map>) msg.get("machineInfo");
        // String firstMonBillMode = (String) (((List<Map>) ((Map) msgcontainer.get("mixTemplate")).get("productInfo"))
        // .get(0).get("firstMonBillMode")); 这个excel有就传，没有就不传

        body.put("msg", msg);
        exchange.getIn().setBody(body);
        String userId = GetSeqUtil.getSeqFromCb(exchange, "SEQ_USER_ID");

        msg.put("userId", userId);
        // 产品信息
        String productId = (String) ((List<Map>) ((Map) msg.get("newUserInfo")).get("productInfo")).get(0).get(
                "productId");
        String commodityId = productId;
        Map proparam = new HashMap();
        proparam.put("PROVINCE_CODE", provinceCode);
        proparam.put("COMMODITY_ID", commodityId);
        proparam.put("EPARCHY_CODE", eparchyCode);
        List<Map> productInfoResult = dao.queryProductInfo(proparam);
        // TODO:改为新库
        if (productInfoResult == null || productInfoResult.size() == 0) {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品信息");
        }
        productId = String.valueOf(productInfoResult.get(0).get("PRODUCT_ID"));
        if ("-1".equals(productId)) {
            productId = String.valueOf(productInfoResult.get(1).get("PRODUCT_ID"));
        }

        if (activityInfo != null && activityInfo.size() > 0) {
            for (int i = 0; i < activityInfo.size(); i++) {
                Map activityMap = activityInfo.get(i);
                if (activityMap.isEmpty()) {
                    continue;
                }
                String actPlanId = (String) (activityMap.get("actPlanId"));
                if (activityMap.containsKey("resourcesCode")) {
                    resourcesCode = (String) (activityMap.get("resourcesCode"));
                }
                resourceFee = (String) (activityMap.get("resourcesFee"));
                String monthFirstDay = GetDateUtils.getDate();
                String monthLasttDay = "20501231235959";
                Map actparam = new HashMap();
                actparam.put("PROVINCE_CODE", provinceCode);
                actparam.put("COMMODITY_ID", actPlanId);
                actparam.put("EPARCHY_CODE", eparchyCode);
                List<Map> actInfoResult = dao.queryProductInfo(actparam);
                // TODO:改为新库
                if (actInfoResult == null || actInfoResult.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + actPlanId + "】的产品信息");
                }
                String actProductId = String.valueOf(actInfoResult.get(0).get("PRODUCT_ID"));

                for (int j = 0; j < actInfoResult.size(); j++) {

                    if ("D".equals(actInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                        Map dis = new HashMap();
                        dis.put("productId", actInfoResult.get(j).get("PRODUCT_ID"));
                        dis.put("packageId", actInfoResult.get(j).get("PACKAGE_ID"));
                        dis.put("discntCode", actInfoResult.get(j).get("ELEMENT_ID"));
                        discntList.add(dis);
                    }
                    if ("S".equals(actInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                        Map svc = new HashMap();
                        svc.put("serviceId", actInfoResult.get(j).get("ELEMENT_ID"));
                        svc.put("productId", actInfoResult.get(j).get("PRODUCT_ID"));
                        svc.put("packageId", actInfoResult.get(j).get("PACKAGE_ID"));
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
                        // TODO:改为新库
                        if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                            throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                    + actInfoResult.get(j).get("ELEMENT_ID") + "】的产品属性信息");
                        }
                        for (int l = 0; l < spItemInfoResult.size(); l++) {
                            Map spItemInfo = spItemInfoResult.get(l);
                            if ("SP_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                spId = (String) (spItemInfo.get("ATTR_VALUE"));
                            }
                            if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                partyId = (String) (spItemInfo.get("ATTR_VALUE"));
                            }
                            if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                spProductId = (String) (spItemInfo.get("ATTR_VALUE"));
                            }
                        }
                        Map sp = new HashMap();
                        sp.put("productId", actInfoResult.get(j).get("PRODUCT_ID"));
                        sp.put("packageId", actInfoResult.get(j).get("PACKAGE_ID"));
                        sp.put("partyId", partyId);
                        sp.put("spId", spId);
                        sp.put("spProductId", spProductId);
                        sp.put("spServiceId", actInfoResult.get(j).get("ELEMENT_ID"));
                        spList.add(sp);
                    }

                }

                Map actProParam = new HashMap();
                actProParam.put("PRODUCT_ID", actProductId);
                List<Map> actProductInfo = dao.queryActProductInfo(actProParam);
                // TODO:改为新库
                if (actProductInfo == null || actProductInfo.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品表或者产品属性表中未查询到产品ID【" + actProductId + "】的产品信息");
                }
                Map detailProduct = actProductInfo.get(0);
                String resActivityper = String.valueOf(detailProduct.get("END_OFFSET"));
                String subProductMode = (String) (detailProduct.get("PRODUCT_MODE"));
                String subProductTypeCode = (String) (detailProduct.get("PRODUCT_TYPE_CODE"));
                String enableTag = (String) (detailProduct.get("ENABLE_TAG"));// 0-绝对时间（看开始时间） 1-相对时间
                String strStartUnit = (String) (detailProduct.get("START_UNIT"));// '0:天 1:自然天 2:月 3:自然月 4:年 5:自然年';
                String endUnit = (String) (null == detailProduct.get("END_UNIT") ? "0" : detailProduct.get("END_UNIT"));
                String startOffset = String.valueOf(detailProduct.get("START_OFFSET"));// 生效偏移时间
                String strBrandcode = (String) (detailProduct.get("BRAND_CODE"));

                if (StringUtils.isEmpty(enableTag) || StringUtils.isEmpty(startOffset)
                        || StringUtils.isEmpty(strStartUnit)) {
                    enableTag = "0";
                }

                String newAcceptDate = GetDateUtils.transDate(acceptDate, 19);
                monthFirstDay = GetDateUtils.getSpecifyDateTime(Integer.parseInt(enableTag),
                        Integer.parseInt(strStartUnit), newAcceptDate, Integer.parseInt(startOffset));
                monthFirstDay = GetDateUtils.TransDate(monthFirstDay, "yyyy-MM-dd HH:mm:ss");
                monthLasttDay = GetDateUtils.getSpecifyDateTime(Integer.parseInt(enableTag), Integer.parseInt(endUnit),
                        newAcceptDate, Integer.parseInt(resActivityper) + Integer.parseInt(startOffset));
                monthLasttDay = GetDateUtils.getSpecifyDateTime(1, 6, monthLasttDay, -1); // 结束月最后一天
                monthLasttDay = GetDateUtils.TransDate(monthLasttDay, "yyyy-MM-dd HH:mm:ss");
                Map productTpye = new HashMap();
                Map product = new HashMap();
                if (!"0".equals(actPlanId)) {
                    productTpye.put("productMode", subProductMode);
                    productTpye.put("productId", actProductId);
                    productTpye.put("productTypeCode", subProductTypeCode);
                    product.put("brandCode", strBrandcode);
                    product.put("productId", actProductId);
                    product.put("productMode", subProductMode);

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
                            // TODO:改为新库
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
                                    spItemParam.put("PTYPE", "X");
                                    spItemParam.put("COMMODITY_ID", actPlanId);
                                    spItemParam.put("PROVINCE_CODE", provinceCode);
                                    spItemParam.put("PID", packageElementInfo.get(0).get("ELEMENT_ID"));
                                    List<Map> spItemInfoResult = dao.queryProductItemInfo(spItemParam);
                                    // TODO:改为新库
                                    if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                        throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                                + packageElementInfo.get(0).get("ELEMENT_ID") + "】的产品属性信息");
                                    }
                                    for (int j = 0; j < spItemInfoResult.size(); j++) {
                                        Map spItemInfo = spItemInfoResult.get(j);
                                        if ("SP_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                            spId = (String) (spItemInfo.get("ATTR_VALUE"));
                                        }

                                        if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                            partyId = (String) (spItemInfo.get("ATTR_VALUE"));
                                        }
                                        if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                            spProductId = (String) (spItemInfo.get("ATTR_VALUE"));
                                        }
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

                    productTypeList.add(productTpye);
                    productList.add(product);

                }

                for (int j = 0; j < actInfoResult.size(); j++) {
                    Map subProductInfo = actInfoResult.get(j);
                    if ("A".equals(subProductInfo.get("ELEMENT_TYPE_CODE"))) {
                        String packageId = String.valueOf(subProductInfo.get("PACKAGE_ID"));
                        String elementDiscntId = String.valueOf(subProductInfo.get("ELEMENT_ID"));

                        // 对终端信息进行预占
                        if (!"0".equals(actPlanId)) {
                            if (resourcesInfo != null && resourcesInfo.size() > 0) {
                                Map rescMap = resourcesInfo.get(0);
                                String salePrice = ""; // 销售价格（单位：厘）
                                if (StringUtils.isNotEmpty((String) rescMap.get("salePrice")))// isNotEmpty(非空时为true)
                                {
                                    salePrice = (String) (rescMap.get("salePrice"));
                                    salePrice = String.valueOf(Integer.parseInt(salePrice) / 10);
                                }
                                String resCode = resourcesCode;// 资源唯一标识
                                String resBrandCode = (String) (rescMap.get("machineBrandCode")); // 品牌
                                String resBrandName = ""; // 终端品牌名称
                                if (StringUtils.isEmpty((String) (rescMap.get("resourcesBrandName")))) {
                                    resBrandName = "无说明";
                                }
                                else {
                                    resBrandName = (String) (rescMap.get("resourcesBrandName"));
                                }
                                String resModelCode = (String) (rescMap.get("machineModelCode")); // 型号
                                String resModeName = ""; // 终端型号名称
                                if (StringUtils.isEmpty((String) (rescMap.get("resourcesModelName")))) {
                                    resModeName = "无说明";
                                }
                                else {
                                    resModeName = (String) (rescMap.get("resourcesModelName"));
                                }
                                String machineTypeCode = "";// 终端机型编码
                                if (StringUtils.isEmpty((String) (rescMap.get("machineTypeCode")))) {
                                    machineTypeCode = "无说明";
                                }
                                else {
                                    machineTypeCode = (String) (rescMap.get("machineTypeCode"));
                                }
                                String orgdeviceBrandCode = "";
                                if (StringUtils.isNotEmpty((String) rescMap.get("orgDeviceBrandCode"))) {
                                    orgdeviceBrandCode = (String) (rescMap.get("orgDeviceBrandCode"));// 3GESS维护品牌，当iphone时品牌与上面的一致
                                }
                                String cost = ""; // 成本价格（单位：厘）
                                if (StringUtils.isNotEmpty((String) (rescMap.get("cost")))) {
                                    cost = (String) (rescMap.get("cost"));
                                    cost = String.valueOf(Integer.parseInt(cost) / 10);
                                }
                                String machineTypeName = ""; // 终端机型名称
                                if (StringUtils.isEmpty((String) (rescMap.get("machineTypeName")))) {
                                    machineTypeName = "无说明";
                                }
                                else {
                                    machineTypeName = (String) (rescMap.get("machineTypeName"));
                                }
                                String terminalSubtype = "";
                                if (StringUtils.isNotEmpty((String) (rescMap.get("terminalTSubType")))) {
                                    terminalSubtype = (String) (rescMap.get("terminalTSubType"));
                                }
                                String terminalType = (String) (rescMap.get("machineType"));// 终端类别编码
                                if (!"0".equals(actPlanId)) {
                                    Map elemntItem1 = new HashMap();
                                    elemntItem1.put("userId", userId);
                                    elemntItem1.put("productId", actProductId);
                                    elemntItem1.put("packageId", packageId);
                                    elemntItem1.put("idType", "C");
                                    elemntItem1.put("id", elementDiscntId);
                                    elemntItem1.put("modifyTag", "0");
                                    elemntItem1.put("startDate", monthFirstDay);
                                    elemntItem1.put("endDate", monthLasttDay);
                                    elemntItem1.put("modifyTag", "0");
                                    elemntItem1
                                            .put("itemId", TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID"));
                                    elemntItem1.put("userIdA", "-1");
                                    elemntItem1.put("xDatatype", "NULL");
                                    msg.put("elementMap", elemntItem1);
                                }
                                String mProductId = (String) ((Map) msg.get("ext")).get("mProductId");
                                body.put("msg", msg);
                                exchange.getIn().setBody(body);
                                String purchaseItemId = GetSeqUtil.getSeqFromCb(exchange, "SEQ_ITEM_ID");

                                List<Map> tradeItem = new ArrayList<Map>();
                                tradeItem.add(LanUtils.createTradeItem("deviceType", decodeTerminalType(terminalType)));
                                tradeItem.add(LanUtils.createTradeItem("deviceno", machineTypeCode));
                                tradeItem.add(LanUtils.createTradeItem("devicebrand", orgdeviceBrandCode));
                                tradeItem.add(LanUtils.createTradeItem("deviceintag", resBrandName));
                                tradeItem.add(LanUtils.createTradeItem("mobilecost",
                                        String.valueOf(Integer.parseInt(cost) / 100)));
                                tradeItem.add(LanUtils.createTradeItem("mobileinfo", machineTypeName));
                                tradeItem.add(LanUtils.createTradeItem("mobilesaleprice",
                                        String.valueOf(Integer.parseInt(salePrice) / 100)));
                                tradeItem.add(LanUtils.createTradeItem("resActivityper", resActivityper));
                                tradeItem.add(LanUtils.createTradeItem("partActiveProduct", mProductId));
                                tradeItem.add(LanUtils.createTradeItem("partActiveProduct", mProductId));
                                tradeItem.add(LanUtils.createTradeItem("resourcesBrandCode", resBrandCode));
                                tradeItem.add(LanUtils.createTradeItem("resourcesBrandName", resBrandName));
                                tradeItem.add(LanUtils.createTradeItem("resourcesModelCode", resModelCode));
                                tradeItem.add(LanUtils.createTradeItem("resourcesModelName", resModeName));

                                if (StringUtils.isNotEmpty(terminalSubtype)) // 有时为空
                                {
                                    tradeItem.add(LanUtils.createTradeItem("terminalTSubType", terminalSubtype));
                                }
                                tradeItem.add(LanUtils.createTradeItem("terminalType", terminalType));
                                tradeItem.add(LanUtils.createTradeItem("isOwnerPhone", "0"));
                                tradeItem.add(LanUtils.createTradeItem("isPartActive", "0"));
                                tradeItem.add(LanUtils.createTradeItem("holdUnitType", "01"));
                                tradeItem.add(LanUtils.createTradeItem("resourcesType", "07"));
                                tradeItem.add(LanUtils.createTradeItem("packageType", "10"));
                                tradeItem.add(LanUtils.createTradeItem("itemid", purchaseItemId));

                                msg.put("tradeItem", tradeItem);
                                // /拼装SUB_ITEM结束
                                String purFeeTypeCode = "4310";
                                // tf_b_trade_purchase表的台账
                                Map tradePurchase = new HashMap();
                                tradePurchase.put("userId", userId);// ?
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
                                tradePurchase.put("staffId", tradeStaffId);
                                tradePurchase.put("departId", departId);
                                tradePurchase.put("startDate", monthFirstDay);
                                tradePurchase.put("endDate", monthLasttDay);
                                tradePurchase.put("remark", "");
                                tradePurchase.put("itemId", purchaseItemId);
                                tradePurchase.put("xDatatype", "NULL");
                                // msg.put("tradePurchase", tradePurchase);
                            }
                        } // end
                    } // END IF A
                }
            }
            msg.put("discntList", discntList);
            msg.put("svcList", svcList);
            msg.put("spList", spList);
            msg.put("productTypeList", productTypeList);
            msg.put("productList", productList);
            List<Map> tradeFeeItemList = new ArrayList<Map>();
            if (StringUtils.isNotEmpty(resourceFee) && !"0".equals(resourceFee)) {
                // 传入单位为厘
                int fee = Integer.parseInt(resourceFee);
                Map tradeFeeItem = new HashMap();
                tradeFeeItem.put("feeMode", "0");
                tradeFeeItem.put("feeTypeCode", "4310");
                tradeFeeItem.put("oldFee", String.valueOf(fee));
                tradeFeeItem.put("fee", String.valueOf(fee));
                tradeFeeItem.put("chargeSourceCode", "1");
                tradeFeeItem.put("apprStaffId", tradeStaffId);
                tradeFeeItem.put("calculateId", cbOrderid);// 算费流水号
                tradeFeeItem.put("calculateDate", acceptDate);
                tradeFeeItem.put("staffId", tradeStaffId);
                tradeFeeItem.put("calculateTag", "0");
                tradeFeeItem.put("payTag", "0");
                tradeFeeItem.put("xDatatype", "NULL");
                tradeFeeItemList.add(tradeFeeItem);
            }
        }
        Map ext = preExtDataforItem(msg);
        return ext;
    }

    /**
     * 封装节点
     * @param msg
     * @return
     */
    private Map preExtDataforItem(Map msg) {
        Map ext = new HashMap();
        ext.put("tradeProductType", preProductTpyeListData(msg));// productTypeList 模板约束为问号(下面一样)
        ext.put("tradeProduct", preProductData(msg));// productList
        ext.put("tradeDiscnt", preDiscntData(msg));// discntList
        ext.put("tradeSvc", preTradeSvcData(msg));// svcList
        ext.put("tradeSp", preTradeSpData(msg));// spList
        ext.put("tradePurchase", preTradePurchase(msg));// tradePurchase
        ext.put("tradeItem", preTradeItem(msg));// itemList
        ext.put("tradefeeSub", preTradeFeeSub(msg));// tradeFeeItemList
        if ("1".equals(getPayModeCode(msg)) || "3".equals(getPayModeCode(msg))) {
            ext.put("tradeAcctConsign", preTradeAcctConsign(msg));
        }
        return ext;
    }

    private Map preTradeFeeSub(Map msg) {
        List<Map> tradeFeeItemList = (List<Map>) msg.get("tradeFeeItemList");
        Map tradeFeeSubList = new HashMap();
        List tradeFeeList = new ArrayList();
        if (tradeFeeItemList != null) {
            for (int i = 0; i < tradeFeeItemList.size(); i++) {
                if (tradeFeeItemList != null && tradeFeeItemList.size() > 0) {
                    Map tradeFeeMap = tradeFeeItemList.get(i);
                    Map item = new HashMap();
                    item.putAll(tradeFeeMap);
                    tradeFeeList.add(item);
                }

            }
        }
        tradeFeeSubList.put("item", tradeFeeList);
        return tradeFeeSubList;

    }

    private Map preTradeAcctConsign(Map msg) {
        Map tradeAcctCon = new HashMap();
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("acctId", msg.get("acctId"));
        item.put("payModeCode", getPayModeCode(msg));
        item.put("consignMode", "1");
        item.put("assistantTag", "0");
        item.put("bankCode", "");
        item.put("bankAcctNo", "");
        item.put("bankAcctName", "");
        item.put("startCycleId", msg.get("cycId"));
        item.put("endCycleId", "203712");
        tradeAcctCon.put("item", item);
        return tradeAcctCon;

    }

    private Map preTradeItem(Map msg) {
        List<Map> itemList = (List<Map>) msg.get("itemList");
        Map tradeItem = new HashMap();
        tradeItem.put("item", itemList);
        return tradeItem;
    }

    private Map preTradePurchase(Map msg) {
        Map tradePurchaseMap = (Map) msg.get("tradePurchase");
        Map tradePurchase = new HashMap();
        if (tradePurchaseMap != null && tradePurchaseMap.size() > 0) {
            Map item = new HashMap();
            item.putAll(tradePurchaseMap);
            tradePurchase.put("item", item);
        }
        return tradePurchase;
    }

    public Map preTradeSpData(Map msg) {
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
            if ("SS-GG-002".equals(msg.get("bizKey"))) {
                item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                item.put("itemId", msg.get("itemId"));
            }
            else {
                item.put("startDate", GetDateUtils.getDate());
                item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
            }
            item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            item.put("updateTime", GetDateUtils.getDate());
            item.put("remark", "");
            item.put("modifyTag", "0");
            item.put("payUserId", msg.get("userId"));
            item.put("spServiceId", sp.get(i).get("spServiceId"));
            item.put("userIdA", msg.get("userId"));
            item.put("xDatatype", "NULL");
            itemList.add(item);
        }
        tardeSp.put("item", itemList);
        return tardeSp;
    }

    public Map preTradeSvcData(Map msg) {
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
            if ("SS-GG-002".equals(msg.get("bizKey"))) {
                item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                item.put("itemId", msg.get("itemId"));
            }
            else {
                item.put("startDate", GetDateUtils.getDate());
                item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
            }
            item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            item.put("productId", svc.get(i).get("productId"));
            item.put("packageId", svc.get(i).get("packageId"));
            item.put("userIdA", "-1");
            svList.add(item);
        }
        svcList.put("item", svList);
        return svcList;
    }

    public Map preDiscntData(Map msg) {
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
            if ("SS-GG-002".equals(msg.get("bizKey"))) {
                item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                item.put("itemId", msg.get("itemId"));
            }
            else {
                item.put("startDate", GetDateUtils.getDate());
                item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
            }
            item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            item.put("modifyTag", "0");
            itemList.add(item);

        }
        Map numberDiscnt = (Map) msg.get("numberDiscnt");
        if (null != numberDiscnt && !numberDiscnt.isEmpty()) {
            Map item = new HashMap();
            item.putAll(numberDiscnt);
            itemList.add(item);
        }
        Map numberDis = (Map) msg.get("numberDis");
        if (null != numberDis && !numberDis.isEmpty()) {
            Map item1 = new HashMap();
            item1.putAll(numberDis);
            itemList.add(item1);

        }
        tradeDis.put("item", itemList);
        return tradeDis;
    }

    public Map preProductData(Map msg) {
        Map tradeProduct = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> product = (List<Map>) msg.get("productList");
        // 针对老用户存费送费业务
        if (!"".equals(msg.get("oldProductId")) && null != msg.get("oldProductId")) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", msg.get("userId"));
            item.put("productMode", "00");
            item.put("productId", msg.get("oldProductId"));
            item.put("brandCode", msg.get("brandCode"));
            item.put("itemId", msg.get("itemId"));
            item.put("modifyTag", "1");
            item.put("startDate", msg.get("startDate"));
            item.put("endDate", GetDateUtils.getMonthLastDayFormat());
            item.put("userIdA", MagicNumber.DEFAULT_NO_VALUE);
            itemList.add(item);
        }
        if (product != null) {

            for (int i = 0; i < product.size(); i++) {
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("userId", msg.get("userId"));
                item.put("productMode", product.get(i).get("productMode"));
                item.put("productId", product.get(i).get("productId"));
                item.put("brandCode", product.get(i).get("brandCode"));
                item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
                item.put("modifyTag", "0");
                // 存费送费新产品生效时间下月初
                if ("SS-GG-002".equals(msg.get("bizKey"))) {
                    item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                    if ("50".equals(product.get(i).get("productMode"))) {
                        item.put("endDate", msg.get("activityTime"));
                    }
                    else {
                        item.put("endDate", "20501231000000");
                    }
                }
                else {
                    item.put("startDate", GetDateUtils.getDate());
                    item.put("endDate", "20501231000000");
                }
                item.put("userIdA", MagicNumber.DEFAULT_NO_VALUE);
                itemList.add(item);
            }
        }

        tradeProduct.put("item", itemList);
        return tradeProduct;
    }

    public Map preProductTpyeListData(Map msg) {
        Map tradeProductType = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> productTpye = (List<Map>) msg.get("productTypeList");
        // 针对老用户存费送费业务
        if (!"".equals(msg.get("oldProductId")) && null != msg.get("oldProductId")) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            // item.put("userId", msg.get("userId"));
            item.put("productMode", "00");
            item.put("productId", msg.get("oldProductId"));
            item.put("productTypeCode", msg.get("productTypeCode"));
            item.put("modifyTag", "1");
            item.put("startDate", msg.get("startDate"));
            item.put("endDate", GetDateUtils.getMonthLastDayFormat());
            itemList.add(item);

        }
        if (productTpye != null) {
            for (int i = 0; i < productTpye.size(); i++) {
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("userId", msg.get("userId"));
                item.put("productMode", productTpye.get(i).get("productMode"));
                item.put("productId", productTpye.get(i).get("productId"));
                item.put("productTypeCode", productTpye.get(i).get("productTypeCode"));
                item.put("modifyTag", "0");
                if ("SS-GG-002".equals(msg.get("bizKey"))) {
                    item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                    if ("50".equals(productTpye.get(i).get("productMode"))) {
                        item.put("endDate", msg.get("activityTime"));
                    }
                    else {
                        item.put("endDate", "20501231000000");
                    }
                }
                else {
                    item.put("startDate", GetDateUtils.getDate());
                    item.put("endDate", "20501231000000");
                }
                itemList.add(item);
            }
        }
        tradeProductType.put("item", itemList);
        return tradeProductType;

    }

    // 获取账户付费方式编码
    private String getPayModeCode(Map msg) {
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        Map param = new HashMap();
        param.put("EPARCHY_CODE", msg.get("eparchyCode"));
        param.put("PARAM_CODE", msg.get("accountPayType"));
        param.put("PROVINCE_CODE", msg.get("provinceCode"));
        List<Map> payModeCoderesult = dao.queryPayModeCode(param);
        if (payModeCoderesult.size() > 0) {
            return payModeCoderesult.get(0).get("PARA_CODE1").toString();
        }
        return "0";
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
        if ("04".equals(terminalType))
            return "05";
        if ("05".equals(terminalType))
            return "06";
        return "01";
    }

}
