package com.ailk.ecaop.biz.sub.lan;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.lang.RDate;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.brd.BrdYearlyPayCheckN25Processor;
import com.ailk.ecaop.biz.product.DateUtils;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.YearPayEcsCheck;
import com.ailk.ecaop.common.utils.YearPayUtils;

public class YearPayEcs4South25Check implements YearPayEcsCheck {

    private final String ERROR_CODE = "8888";
    protected ParametersMappingProcessor[] pmp = null;

    public YearPayEcs4South25Check(ParametersMappingProcessor[] pmp) {
        this.pmp = pmp;
    }

    @Override
    public Map yearPayEcsCheck(Exchange exchange, Map msg) throws Exception {
        System.out.println("请求进来的msg: appTx:  msg= " + msg);
        new YearPayUtils().callGetBroadbandAcctInfo(exchange);
        Map threePartInfo = new YearPayUtils().callCheckUserInfo(exchange, "0160");
        // boolean isNoChangeProduct = isNoChangeMainProduct(threePartInfo, msg);
        String province = msg.get("province").toString();
        Map userProduct = new YearPayUtils().callQryUserProInfo(exchange);
        if ("1".equals(msg.get("changeTag")) || IsEmptyUtils.isEmpty(msg.get("changeTag"))) {
            new BrdYearlyPayCheckN25Processor().process(exchange, threePartInfo, userProduct, province);
            Map result = exchange.getOut().getBody(Map.class);
            result.put("isNoChangeProduct", "0");
            return result;
        }
        System.out.println("处理变更参数前的msg: appTx:msg= " + msg);
        Map preSubmitMap = preSubmit(threePartInfo, msg, userProduct, province);
        Exchange preSubmitExchange = ExchangeUtils.ofCopy(exchange, preSubmitMap);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[0], preSubmitExchange);// ecaop.masb.spec.sProductChgParametersMapping
        CallEngine.wsCall(preSubmitExchange, "ecaop.comm.conf.url.osn.services.productchgser");
        lan.xml2Json("ecaop.masb.spec.template", preSubmitExchange);
        Map result = preSubmitExchange.getOut().getBody(Map.class);
        dealProductInfo(result, threePartInfo, preSubmitMap);
        return result;

    }

    private void dealProductInfo(Map result, Map threePartInfo, Map preSubmitMap) {
        Map custInfo = (Map) threePartInfo.get("custInfo");
        result.put("custName", custInfo.get("custName"));
        result.put("productType", "1");
        result.put("productName", preSubmitMap.get("mainProductId"));
        result.put("discntName", preSubmitMap.get("discntName"));
        result.put("startDate", preSubmitMap.get("startDate"));
        result.put("endDate", preSubmitMap.get("endDate"));
    }

    @Override
    public Map yearPayEcsSub(Exchange exchange, Map preSubmit, Map msg) throws Exception {
        Map submitMap = new HashMap();
        MapUtils.arrayPut(submitMap, msg, MagicNumber.COPYARRAY);
        Object orderNo = msg.get("orderNo");
        submitMap.put("orderNo", orderNo);
        Map rspInfo = (Map) preSubmit.get("rspInfo");
        if (null == rspInfo) {
            throw new EcAopServerBizException("8888", "省份sProductChg接口未返回订单编号");
        }
        Object provOrderId = rspInfo.get("provOrderId");
        dealSubmit(submitMap, preSubmit, orderNo, provOrderId);
        Exchange submitExchange = ExchangeUtils.ofCopy(exchange, submitMap);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[1], submitExchange);// ecaop.masb.odsb.ActivityAryParametersMapping
        CallEngine.wsCall(submitExchange, "ecaop.comm.conf.url.osn.services.ordser");
        lan.xml2Json("ecaop.masb.odsb.template", submitExchange);
        Map outMap = new HashMap();
        outMap.put("code", "0000");
        outMap.put("detail", "OK");
        outMap.put("custName", preSubmit.get("custName"));
        outMap.put("productType", preSubmit.get("productType"));
        outMap.put("productName", preSubmit.get("productName"));
        outMap.put("discntName", preSubmit.get("discntName"));
        outMap.putAll(rspInfo);
        return outMap;
    }

    private void dealSubmit(Map submitMap, Map preSubmit, Object orderNo, Object provOrderId) {
        submitMap.put("operationType", "02");
        Map rspInfo = (Map) preSubmit.get("rspInfo");
        submitMap.put("provOrderId", provOrderId);
        List<Map> subOrderSubReq = new ArrayList<Map>();
        Map subOrderSub = new HashMap();
        subOrderSub.put("subProvinceOrderId", rspInfo.get("provOrderId"));
        subOrderSub.put("subOrderId", orderNo);
        List<Map> fee = new ArrayList<Map>();
        List<Map> feeInfo = (List<Map>) rspInfo.get("feeInfo");
        for (Map feeMap : feeInfo) {
            Map temp = new HashMap();
            temp.put("feeCategory", feeMap.get("feeMode"));
            temp.put("feeId", feeMap.get("feeTypeCode"));
            temp.put("feeDes", feeMap.get("feeTypeName"));
            temp.put("operateType", "2");
            temp.put("origFee", feeMap.get("fee"));
            temp.put("realFee", feeMap.get("fee"));
            fee.add(temp);
        }
        subOrderSub.put("feeInfo", fee);
        subOrderSub.put("subProvinceOrderId", provOrderId);
        subOrderSubReq.add(subOrderSub);
        submitMap.put("subOrderSubReq", subOrderSubReq);
        submitMap.put("origTotalFee", "0");
        submitMap.put("cancleTotalFee", rspInfo.get("totalFee"));
    }

    Map preSubmit(Map threePartInfo, Map msg, Map userProduct, String province) throws Exception {
        Object userInfo = threePartInfo.get("userInfo");
        if (null == userInfo) {
            throw new EcAopServerBizException("8888", "省份BSS三户接口未返回用户信息");
        }
        Map user = (Map) userInfo;
        Map preSubmitMap = MapUtils.asMap("orderId", msg.get("orderNo"), "tradeTypeCode",
                "36".equals(province) ? "0090" : "0160");
        preSubmitMap.put("serviceClassCode", user.get("serviceClassCode"));
        preSubmitMap.put("userId", user.get("userId"));
        preSubmitMap.put("serialNumber", msg.get("serialNumber"));
        MapUtils.arrayPut(preSubmitMap, msg, MagicNumber.COPYARRAY);
        List<Map> productList = (List<Map>) msg.get("productInfo");
        List<Map> product = new ArrayList<Map>();// 拼装请求报文参数
        Object delayDiscntType = "";
        List<Map> broadDiscntInfo = (List<Map>) msg.get("broadDiscntInfo");
        Object broadDiscntId = broadDiscntInfo.get(0).get("broadDiscntId");
        Map broadDiscntAttr = (Map) broadDiscntInfo.get(0).get("broadDiscntAttr");
        if (null != broadDiscntAttr && !broadDiscntAttr.isEmpty()) {
            delayDiscntType = broadDiscntAttr.get("delayDiscntType");
        }
        System.out.println("delayDiscntType: appTx:  delayDiscntType= " + delayDiscntType);
        int brandNumber = 0;
        Object mainProductId = "";
        Object oldUserMktendDate = getUserMktCamp(userProduct).get(0).get("endDate").toString();
        msg.put("oldUserMktendDate", oldUserMktendDate);
        for (Map productMap : productList) {
            if ("1".equals(productMap.get("optType"))) {// 天津省份立即生效也要下发
                if (!"36|19|90".contains(province) && (!"13".contains(province) || "1".equals(delayDiscntType))) {
                    productMap.put("startDate", user.get("openDate"));
                    product.addAll(dealRollBackProductInfo(productMap, delayDiscntType, province));
                }
            }
            else {
                Object brandNumberObject = productMap.get("brandNumber");
                if (IsEmptyUtils.isEmpty(brandNumberObject)) {
                    throw new EcAopServerBizException("8888", "brandNumber不能为空");
                }
                brandNumber = Integer.valueOf(productMap.get("brandNumber").toString());
                product.addAll(dealNewProductInfo(productMap, msg, delayDiscntType, brandNumber));
                if ("0".equals(productMap.get("productMode"))) {
                    mainProductId = productMap.get("oldProductId");
                }
            }
        }
        YearPayUtils ypu = new YearPayUtils();
        Map userMktCamp = ypu.dealMktCamp(broadDiscntId, getUserMktCamp(userProduct), brandNumber, delayDiscntType);
        if ("75".equals(province)) {// 江西要求时间特殊处理
            userMktCamp.put("endDate", DateUtils.getBeforeDayLastTime(userMktCamp.get("endDate").toString()));
        }
        if (!"13".equals(province)) {
            preSubmitMap.put("userMktCamp", userMktCamp);
        }
        Map para = MapUtils.asMap("paraId", "NEW_RATE", "paraValue", msg.get("speedLevel"));
        List<Map> paraList = new ArrayList<Map>();
        paraList.add(para);
        for (Map prod : product) {
            if ("1".equals(prod.get("modifyTag"))) {
                prod.put("endDate", DateUtils.addSeconds(userMktCamp.get("startDate").toString(), -1));
            }
        }
        preSubmitMap.put("product", product);
        preSubmitMap.put("discntName", broadDiscntId);
        preSubmitMap.put("startDate", userMktCamp.get("startDate"));
        preSubmitMap.put("endDate", userMktCamp.get("endDate"));
        preSubmitMap.put("mainProductId", mainProductId);
        preSubmitMap.put("para", paraList);
        return preSubmitMap;
    }

    private List<Map> getUserMktCamp(Map userProduct) {
        List<Map> userInfoList = (List<Map>) userProduct.get("userInfo");
        for (Map userMap : userInfoList) {
            List<Map> userMktCamp = (List<Map>) userMap.get("userMktCamp");
            if (IsEmptyUtils.isEmpty(userMktCamp)) {
                continue;
            }
            return userMktCamp;
        }
        return null;
    }

    private List<Map> dealNewProductInfo(Map inMap, Map msg, Object delayDiscntType, int brandNumber) throws Exception {
        List<Map> productList = new ArrayList<Map>();
        Map product = new HashMap();
        product.put("modifyTag", "0");
        product.put("productId", inMap.get("oldProductId"));
        product.put("defaultTag", "1");
        Map broadDiscnt = ((List<Map>) msg.get("broadDiscntInfo")).get(0);
        if (!"13".equals(msg.get("province"))) {
            product.put("mktCampId", broadDiscnt.get("broadDiscntId"));
        }
        Object productMode = inMap.get("productMode");
        product.put("productMode", "0".equals(productMode) ? "01" : "03");
        product.put("startEnable", "0".equals(delayDiscntType) ? "0" : "1");
        String startDate = "";
        String endDate = (String) msg.get("oldUserMktendDate");
        if ("0".equals(delayDiscntType) || null == endDate) {
            startDate = GetDateUtils.getDate();
        }
        else if ("1".equals(delayDiscntType)) {
            startDate = GetDateUtils.getNextMonthFirstDayFormat();
        }
        else {
            startDate = DateUtils.addSeconds(endDate, 1);
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        if (!"0".equals(productMode)) {// 附加产品的处理
            endDate = sdf.format(RDate.addMonths(sdf.parse(startDate), brandNumber));
            endDate = DateUtils.addSeconds(endDate, -1);
        }
        else {// 主产品的处理
            endDate = "20371231235959";
        }
        product.put("startDate", startDate);
        product.put("endDate", endDate);
        productList.add(product);
        return productList;
    }

    public static void main(String[] args) {
        String a = "20180622000000";
        try {
            System.out.println(DateUtils.addSeconds(a, 1));
        }
        catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * 处理退订产品信息
     *
     * @param inMap
     * @param delayDiscntType
     * @return
     */
    private List<Map> dealRollBackProductInfo(Map inMap, Object delayDiscntType, Object province) {
        List<Map> productList = new ArrayList<Map>();
        Map product = new HashMap();
        if ("13".equals(province)) {
            product.put("modifyTag", "0");
        }
        else {
            product.put("modifyTag", "1");
        }
        product.put("productId", inMap.get("oldProductId"));
        product.put("startEnable", "1");
        product.put("startDate", inMap.get("startDate"));
        product.put("defaultTag", "1");
        product.put("productMode", "0".equals(inMap.get("productMode")) ? "01" : "03");
        if ("0".equals(delayDiscntType)) {// 立即
            product.put("endDate", GetDateUtils.getDate());
        }
        else {
            product.put("endDate", GetDateUtils.getMonthLastDayFormat());
        }
        productList.add(product);
        return productList;
    }

    // boolean isNoChangeMainProduct(Map threePartMap, Map msg) {
    // if ("2".equals(msg.get("changeTag"))) {
    // return false;
    // }
    // Map userInfo = (Map) threePartMap.get("userInfo");
    // Object oldMainProduct = userInfo.get("productId");
    // String oldProduct = "";
    // List<Map> oldProductInfo = (List<Map>) userInfo.get("productInfo");
    // for (int i = 0; i < oldProductInfo.size(); i++) {
    // oldProduct += oldProductInfo.get(i).get("productId") + "|";
    // }
    // oldProduct += oldMainProduct;// 拼接老产品信息,上海不会在productInfo节点返回主产品信息
    // Object newMainProduct = "";
    // List<Map> productInfo = (List<Map>) msg.get("productInfo");
    // if (null == productInfo || 0 == productInfo.size()) {// 外围系统不下发产品信息时,认为是趸交业务
    // threePartMap.put("newMainProduct", oldMainProduct);
    // return true;
    // }
    // for (Map product : productInfo) {
    // if ("1".equals(product.get("optType")) && !oldProduct.contains(product.get("oldProductId").toString())) {
    // throw new EcAopServerBizException("8888", "产品[" + product.get("oldProductId") + "]未被订购,无法进行退订操作");
    // }
    // if ("0".equals(product.get("optType")) && "0".equals(product.get("productMode"))) {
    // newMainProduct = product.get("oldProductId");
    // }
    // }
    // if ("".equals(newMainProduct)) {// 如果请求报文中未下发订购的主产品信息,认为是趸交业务
    // threePartMap.put("newMainProduct", oldMainProduct);
    // return true;
    // }
    // if (oldMainProduct.equals(newMainProduct)) {
    // threePartMap.put("newMainProduct", oldMainProduct);
    // return true;
    // }
    // threePartMap.put("newMainProduct", newMainProduct);
    // return false;
    // }
}
