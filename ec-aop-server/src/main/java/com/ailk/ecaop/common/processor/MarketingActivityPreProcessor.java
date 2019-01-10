package com.ailk.ecaop.common.processor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.product.DealNewCbssProduct;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;

@EcRocTag("MarketingActivity")
public class MarketingActivityPreProcessor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = {
            "ecaop.trade.cbss.checkUserParametersMapping",
            "ecaop.trades.sccc.cancelPre.paramtersmapping",
            "ecaop.mvoa.preSub.ParametersMapping",
            "ecaop.trades.sccc.cancel.paramtersmapping",
            "ecaop.masb.chph.gifa.ParametersMapping"
    };
    LanUtils lan = new LanUtils();
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[5];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        // 准备参数信息
        Map preDataMap = preDataForPreCommit(exchange, msg);
        MapUtils.arrayPut(preDataMap, msg, MagicNumber.COPYARRAY);
        Exchange presubExchange = ExchangeUtils.ofCopy(exchange, preDataMap);
        // 调预提交接口
        lan.preData(pmp[1], presubExchange);
        CallEngine.wsCall(presubExchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.trades.sccc.cancelPre.template", presubExchange);

        // 调用正式提交
        Map preSubmitRetMap = presubExchange.getOut().getBody(Map.class);
        Map submitMap = Maps.newHashMap();
        MapUtils.arrayPut(submitMap, msg, MagicNumber.COPYARRAY);
        preOrderSubMessage(preSubmitRetMap, submitMap, msg);
        Exchange submitExchange = ExchangeUtils.ofCopy(exchange, submitMap);
        lan.preData(pmp[3], submitExchange);
        CallEngine.wsCall(submitExchange, "ecaop.comm.conf.url.cbss.services.orderSub");
        lan.xml2Json("ecaop.trades.sccc.cancel.template", submitExchange);

        // 拼装返回参数
        Object provOrderId = ((ArrayList<Map>) preSubmitRetMap.get("rspInfo")).get(0).get("bssOrderId");
        Message out = new DefaultMessage();
        out.setBody(MapUtils.asMap("provOrderId", provOrderId));
        exchange.setOut(out);
    }

    private void preOrderSubMessage(Map preSubmitRetMap, Map submitMap, Map msg) throws Exception {
        List<Map> rspInfo = (ArrayList<Map>) preSubmitRetMap.get("rspInfo");
        if (rspInfo == null || rspInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "cBSS统一预提交未返回应答信息");
        }
        // 准备正式提交参数
        List<Map> provinceOrderInfo = (ArrayList<Map>) rspInfo.get(0).get("provinceOrderInfo");
        System.out.println("zsqtest2" + provinceOrderInfo);
        List<Map> OrderSubReq = new ArrayList<Map>();
        // submitMap请求参数Map
        submitMap.put("provOrderId", rspInfo.get(0).get("bssOrderId"));
        submitMap.put("orderNo", rspInfo.get(0).get("provOrderId"));
        submitMap.put("operationType", "01");
        submitMap.put("noteType", "1");
        System.out.println("zsqtest1" + submitMap);
        //
        Map subOrderSubReq = new HashMap();
        subOrderSubReq.put("subProvinceOrderId", submitMap.get("provOrderId"));
        subOrderSubReq.put("subOrderId", submitMap.get("orderNo"));
        int totalFee = 0;
        if (null != provinceOrderInfo && provinceOrderInfo.size() > 0) {
            for (Map provinceOrder : provinceOrderInfo) {
                List<Map> feeInfos = (List<Map>) provinceOrder.get("preFeeInfoRsp");
                totalFee = totalFee + Integer.valueOf(provinceOrder.get("totalFee").toString());
                List<Map> fees = new ArrayList<Map>();
                if (null != feeInfos && 0 != feeInfos.size()) {
                    for (Map feeInfo1 : feeInfos) {
                        Map feeInfo = new HashMap();
                        String calculateId = GetSeqUtil.getSeqFromCb();
                        feeInfo.put("feeId", feeInfo1.get("feeTypeCode"));
                        feeInfo.put("feeCategory", feeInfo1.get("feeMode"));
                        feeInfo.put("feeDes", feeInfo1.get("feeTypeName"));
                        feeInfo.put("origFee", Integer.valueOf((String) feeInfo1.get("fee")));
                        feeInfo.put("realFee", Integer.valueOf((String) feeInfo1.get("oldFee")));
                        feeInfo.put("operateType", "1");
                        feeInfo.put("calculateTag", "N");
                        feeInfo.put("isPay", "1");
                        feeInfo.put("payTag", "1");
                        feeInfo.put("calculateId", calculateId);
                        feeInfo.put("calculateDate", GetDateUtils.getDate());
                        feeInfo.put("payId", calculateId);
                        fees.add(feeInfo);
                    }
                    subOrderSubReq.put("feeInfo", fees);
                }
            }

        }
        submitMap.put("origTotalFee", totalFee);
        submitMap.put("cancleTotalFee", 0);
        OrderSubReq.add(subOrderSubReq);
        List<Map> cancleFeeInfo = (List<Map>) msg.get("cancleFeeInfo");
        List<Map> pay = new ArrayList<Map>();
        for (Map feeIn : cancleFeeInfo) {
            String payType = (String) feeIn.get("cancleFeeType");
            String payMoney = "-" + Integer.valueOf(feeIn.get("cancleFee").toString());
            Map payInfo = new HashMap();
            if ("03".equals(payType)) {
                payInfo.put("payType", "W");
            }
            else {
                payInfo.put("payType", feeIn.get("cancleFeeType"));
            }
            payInfo.put("payMoney", payMoney);
            payInfo.put("subProvinceOrderId", submitMap.get("provOrderId"));
            pay.add(payInfo);
            submitMap.put("payInfo", pay);
        }
        submitMap.put("subOrderSubReq", OrderSubReq);

    }

    private Map preDataForPreCommit(Exchange exchange, Map msg) throws Exception {
        threepartCheck(ExchangeUtils.ofCopy(exchange, msg), msg);
        Map base = preBaseData(exchange, msg);
        Map ext = preExtData(msg);
        Map preDataMap = new HashMap();
        preDataMap.put("ordersId", msg.get("tradeId"));
        preDataMap.put("operTypeCode", "0");
        preDataMap.put("base", base);
        preDataMap.put("ext", ext);
        return preDataMap;
    }

    private Map preBaseData(Exchange exchange, Map msg) {
        String date = (String) msg.get("startDate");
        Map base = new HashMap();
        base.put("subscribeId", msg.get("tradeId"));
        base.put("tradeId", msg.get("tradeId"));
        base.put("tradeTypeCode", "0196");
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        base.put("foregift", "0");
        base.put("execTime", date);
        base.put("acceptDate", date);
        base.put("chkTag", "0");
        base.put("operFee", "0");
        base.put("cancelTag", "0");
        base.put("netTypeCode", "0050");
        base.put("advancePay", "0");
        String inModeCode = (String) new ChangeCodeUtils().getInModeCode(exchange.getAppCode());
        base.put("inModeCode", inModeCode);
        msg.put("inModeCode", inModeCode);
        base.put("custId", msg.get("custId"));
        base.put("custName", msg.get("custName"));
        base.put("acctId", msg.get("acctId"));
        base.put("serinalNamber", msg.get("serialNumber"));
        base.put("productId", msg.get("productId"));
        base.put("userDiffCode", "00");
        base.put("brandCode", msg.get("brandCode"));
        base.put("usecustId", msg.get("custId"));
        base.put("userId", msg.get("userId"));
        base.put("termIp", "10.124.0.48");
        base.put("cityCode", msg.get("district"));
        return base;
    }

    private Map preExtData(Map msg) throws Exception {
        Map ext = dealProduct(msg);
        ext.put("tradeItem", preDataForTradeItem(msg));
        ext.put("tradeOther", preTradeOther(msg));
        return ext;
    }

    private Map preTradeOther(Map msg) {
        Map tradeOther = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("rsrvValueCode", "FXFP");
        item.put("rsrvValue", msg.get("ordersId"));
        item.put("rsrvStr1", "track");
        item.put("rsrvStr2", "");
        item.put("rsrvStr3", "营销活动返销");
        item.put("rsrvStr4", "4G000001");
        item.put("rsrvStr5", "");
        item.put("rsrvStr6", "");
        item.put("rsrvStr7", "");
        item.put("rsrvStr8", "");
        item.put("rsrvStr9", "");
        item.put("modifyTag", "0");
        itemList.add(item);
        tradeOther.put("item", itemList);
        return tradeOther;
    }

    private Map preDataForTradeItem(Map msg) {
        List<Map> itemList = new ArrayList<Map>();
        Map tradeItem = new HashMap();
        itemList.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", msg.get("eparchyCode")));
        itemList.add(LanUtils.createTradeItem("tradeId", msg.get("ordersId")));
        tradeItem.put("item", itemList);
        return tradeItem;
    }

    private void threepartCheck(Exchange exchange, Map msg) {
        msg.put("serialNumber", msg.get("serialNumber"));
        msg.put("tradeTypeCode", "0196");
        msg.put("getMode", "1111111111100013010000000100001");
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, msg);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[0], threePartExchange);
        try {
            CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
            lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "调用cbss三户接口出错:" + e.getMessage());
        }

        Map result = threePartExchange.getOut().getBody(Map.class);
        List<Map> custInfoList = (List<Map>) result.get("custInfo");
        List<Map> userInfoList = (List<Map>) result.get("userInfo");
        List<Map> acctInfoList = (List<Map>) result.get("acctInfo");
        // 需要通过userInfo的返回,拼装老产品信息
        Map custInfo = custInfoList.get(0);
        Map userInfo = userInfoList.get(0);
        Map acctInfo = acctInfoList.get(0);
        String eparchyCode = ChangeCodeUtils.changeCityCode(msg);
        String itemId = (String) GetSeqUtil.getSeqFromCb(pmp[4], ExchangeUtils.ofCopy(exchange, msg),
                "seq_item_id", 1).get(0);
        String tradeId = (String) GetSeqUtil.getSeqFromCb(pmp[4], ExchangeUtils.ofCopy(exchange, msg),
                "seq_trade_id", 1).get(0);
        List<Map> avtivityInfos = new ArrayList<Map>();
        for (Map product : (List<Map>) userInfo.get("productInfo")) {
            if ("50".equals(product.get("productMode"))) {
                avtivityInfos.add(product);
            }
        }
        msg.putAll(MapUtils.asMap("custInfo", custInfo, "userInfo", userInfo, "acctInfo", acctInfo, "userId",
                userInfo.get("userId"),
                "custId", custInfo.get("custId"), "acctId", acctInfo.get("acctId"), "custName",
                custInfo.get("custName"), "brandCode",
                userInfo.get("brandCode"),
                "productId", userInfo.get("productId"), "productName",
                userInfo.get("productName"),
                "productTypeCode",
                userInfo.get("productTypeCode"), "itemId", itemId, "eparchyCode", eparchyCode,
                "tradeId", tradeId, "startDate", GetDateUtils.getDate(), "userDiffCode",
                userInfo.get("userDiffCode"),
                "contact", custInfo.get("contact"), "contactPhone", custInfo.get("contactPhone"), "avtivityInfos",
                avtivityInfos, "operTypeCode", "0"));

    }

    public Map dealProduct(Map msg) {
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        List<Map> productTypeList = new ArrayList<Map>();
        List<Map> productList = new ArrayList<Map>();

        List<Map> discntList = new ArrayList<Map>();
        List<Map> svcList = new ArrayList<Map>();
        String eparchyCode = ChangeCodeUtils.changeEparchy(msg);
        String provinceCode = "00" + msg.get("province");
        Map ext = new HashMap();

        Map productInfo = (Map) msg.get("productInfo");
        String productId = (String) productInfo.get("productId");
        String activityId = (String) productInfo.get("activityId");
        Map param = new HashMap();
        param.put("PRODUCT_ID", productId);
        param.put("ACTIVITY_ID", activityId);
        param.put("EPARCHY_CODE", eparchyCode);
        param.put("PROVINCE_CODE", provinceCode);
        n25Dao.qryProductInfoByProductType(param);// 通过活动编码与活动ID确认外围选择的参数一致性

        // 处理活动
        if (!IsEmptyUtils.isEmpty(productInfo)) {
            List<Map> discntInfo = (List<Map>) productInfo.get("discntInfo");
            List<Map> packageElementInfo = new ArrayList<Map>();
            // 附加包处理
            if (discntInfo != null && discntInfo.size() > 0) {
                for (int n = 0; n < discntInfo.size(); n++) {
                    Map peparam = new HashMap();
                    peparam.put("PROVINCE_CODE", provinceCode);
                    peparam.put("PRODUCT_ID", productId);
                    peparam.put("EPARCHY_CODE", eparchyCode);
                    peparam.put("ELEMENT_ID", discntInfo.get(n).get("discntCode"));
                    //
                    packageElementInfo = n25Dao.qryPackageElementByElement(peparam);
                    if (IsEmptyUtils.isEmpty(packageElementInfo)) {
                        throw new EcAopServerBizException("9999", "该元素与产品无对应关系");
                    }
                    if ("D".equals(packageElementInfo.get(0).get("ELEMENT_TYPE_CODE"))) {
                        Map dis = new HashMap();
                        dis.put("xDatatype", "NULL");
                        dis.put("id", msg.get("userId"));
                        dis.put("idType", "1");
                        dis.put("userIdA", "-1");
                        dis.put("productId", packageElementInfo.get(0).get("PRODUCT_ID"));
                        dis.put("productMode", packageElementInfo.get(0).get("PRODUCT_MODE"));
                        dis.put("packageId", discntInfo.get(n).get("packageId"));
                        dis.put("discntCode", discntInfo.get(n).get("discntCode"));
                        dis.put("specTag", "0");
                        dis.put("relationTypeCode", "");
                        dis.put("startDate", discntInfo.get(n).get("startDate"));
                        dis.put("endDate", GetDateUtils.getDate());
                        dis.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")),
                                "SEQ_ITEM_ID"));
                        dis.put("modifyTag", "1");
                        discntList.add(dis);
                    }
                    if ("S".equals(packageElementInfo.get(0).get("ELEMENT_TYPE_CODE"))) {
                        Map svc = new HashMap();
                        svc.put("serviceId", discntInfo.get(n).get("discntCode"));
                        svc.put("productId", packageElementInfo.get(0).get("PRODUCT_ID"));
                        svc.put("packageId", discntInfo.get(n).get("packageId"));
                        svc.put("activityStarTime", packageElementInfo.get(n).get("startDate"));
                        svc.put("activityTime", GetDateUtils.getDate());
                        svcList.add(svc);
                    }

                }
            }

            Map productTpye = new HashMap();
            Map product = new HashMap();

            String strBrandCode = "";
            String strProductTypeCode = "";
            String strProductMode = "";

            Map addproparam = new HashMap();
            addproparam.put("PROVINCE_CODE", provinceCode);
            addproparam.put("PRODUCT_MODE", "50");
            addproparam.put("PRODUCT_ID", productId);
            addproparam.put("EPARCHY_CODE", eparchyCode);
            addproparam.put("FIRST_MON_BILL_MODE", null);
            List<Map> addproductInfoResult = n25Dao.qryDefaultPackageElement(addproparam);

            // 原有方法是从查询默认产品的结果中取的，如果该产品不存在默认的包和元素（即addproductInfoResult为空）则会出现数组越界，
            // 现在改为优先取默认查询的结果，如果不存在默认的包和元素则去取附加元素处理时查询的结果。
            if (addproductInfoResult == null || addproductInfoResult.size() == 0) {
                List<Map> activityInfoList = n25Dao.qryProductInfoByProductTable(addproparam);
                if (!IsEmptyUtils.isEmpty(activityInfoList)) {
                    strBrandCode = String.valueOf(packageElementInfo.get(0).get("BRAND_CODE"));
                    strProductTypeCode = String.valueOf(packageElementInfo.get(0).get("PRODUCT_TYPE_CODE"));
                }
                else {
                    throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + activityId + "】的产品信息");
                }
            }
            else {
                strBrandCode = (String) addproductInfoResult.get(0).get("BRAND_CODE");
                strProductTypeCode = (String) addproductInfoResult.get(0).get("PRODUCT_TYPE_CODE");
            }
            strProductMode = "50";

            productTpye.put("productMode", strProductMode);
            productTpye.put("productId", productId);
            productTpye.put("productTypeCode", strProductTypeCode);
            productTpye.put("startDate", productInfo.get("productActiveTime"));
            productTpye.put("endDate", GetDateUtils.getDate());
            productTpye.put("xDatatype", "NULL");
            productTpye.put("userId", msg.get("userId"));
            productTpye.put("modifyTag", "1");
            product.put("xDatatype", "NULL");
            product.put("userId", msg.get("userId"));
            product.put("brandCode", strBrandCode);
            product.put("productId", productId);
            product.put("productMode", strProductMode);
            product.put("userIdA", MagicNumber.DEFAULT_NO_VALUE);
            product.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
            product.put("modifyTag", "1");
            // 算出活动的开始结束时间，预提交下发
            product.put("startDate", productInfo.get("productActiveTime"));
            product.put("endDate", GetDateUtils.getDate());

            productTypeList.add(productTpye);
            productList.add(product);

        }// 活动信息处理
        msg.put("tradeDiscnt", discntList);
        msg.put("tradeSvc", svcList);
        msg.put("tradeProductType", productTypeList);
        msg.put("tradeProduct", productList);

        ext.put("tradeDiscnt", preDiscntData(msg));
        ext.put("tradeProduct", preProductData(msg));
        ext.put("tradeProductType", preProductTpyeListData(msg));
        return ext;
    }

    public Map preDiscntData(Map msg) {
        Map tradeDis = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> discnt = (List<Map>) msg.get("tradeDiscnt");
        for (int i = 0; i < discnt.size(); i++) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("id", msg.get("userId"));
            item.put("idType", "1");
            item.put("userIdA", "-1");
            item.put("productId", discnt.get(i).get("productId"));
            item.put("packageId", discnt.get(i).get("packageId"));
            item.put("discntCode", discnt.get(i).get("discntCode"));
            item.put("startDate", discnt.get(i).get("startDate"));
            item.put("endDate", discnt.get(i).get("endDate"));
            item.put("specTag", "0");// FIXME
            item.put("relationTypeCode", "");
            item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
            item.put("modifyTag", "1");
            itemList.add(item);
        }
        tradeDis.put("item", itemList);
        return tradeDis;
    }

    public Map preProductData(Map msg) {
        Map tradeProduct = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> product = (List<Map>) msg.get("tradeProduct");
        for (int i = 0; i < product.size(); i++) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", msg.get("userId"));
            item.put("productMode", product.get(i).get("productMode"));
            item.put("productId", product.get(i).get("productId"));
            item.put("brandCode", product.get(i).get("brandCode"));
            item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
            item.put("modifyTag", "1");
            item.put("userIdA", MagicNumber.DEFAULT_NO_VALUE);
            item.put("startDate", product.get(i).get("startDate"));
            item.put("endDate", GetDateUtils.getDate());
            itemList.add(item);
        }

        tradeProduct.put("item", itemList);
        return tradeProduct;
    }

    public Map preProductTpyeListData(Map msg) {
        Map tradeProductType = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> productTpye = (List<Map>) msg.get("tradeProductType");
        for (int i = 0; i < productTpye.size(); i++) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", msg.get("userId"));
            item.put("productMode", productTpye.get(i).get("productMode"));
            item.put("productId", productTpye.get(i).get("productId"));
            item.put("productTypeCode", productTpye.get(i).get("productTypeCode"));
            item.put("modifyTag", "1");
            item.put("startDate", productTpye.get(i).get("startDate"));
            item.put("endDate", GetDateUtils.getDate());
            itemList.add(item);
        }
        tradeProductType.put("item", itemList);
        return tradeProductType;

    }

    @Override
    public void applyParams(String[] params) {
        // TODO Auto-generated method stub
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
