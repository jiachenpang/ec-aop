package com.ailk.ecaop.biz.brd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.YearPayUtils;

/**
 * 南25省宽带趸交校验
 * 
 * @author Steven Zhang
 */
@EcRocTag("brdYearlyPayCheckN25")
public class BrdYearlyPayCheckN25Processor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        YearPayUtils ypu = new YearPayUtils();
        ypu.callGetBroadbandAcctInfo(exchange);
        Map threePartMap = ypu.callCheckUserInfo(exchange, "0090");
        Map userProduct = ypu.callQryUserProInfo(exchange);
        String province = exchange.getProvince();
        Map preSubmitReqMap = preData4PreSubmit(province, userProduct, threePartMap);
        Map preSubmitRetMap = ypu.callSProductChg(exchange, preSubmitReqMap);
        Object orderNo = ((Map) exchange.getIn().getBody(Map.class).get("msg")).get("orderNo");
        ypu.callOrder(exchange, preOrderSubMap(preSubmitRetMap, orderNo));
        Map outMap = dealReturn(threePartMap, userProduct, preSubmitRetMap, preSubmitReqMap);
        Message out = new DefaultMessage();
        out.setBody(outMap);
        exchange.setOut(out);
    }

    // 方法重载,用于宽带包年,因获取宽带账号接口、三户接口已经调过
    public void process(Exchange exchange, Map threePartMap, Map userProduct, Object province) throws Exception {
        YearPayUtils ypu = new YearPayUtils();
        Map preSubmitReqMap = preData4PreSubmit(province, userProduct, threePartMap);
        Map preSubmitRetMap = ypu.callSProductChg(exchange, preSubmitReqMap);
        Object orderNo = ((Map) exchange.getIn().getBody(Map.class).get("msg")).get("orderNo");
        ypu.callOrder(exchange, preOrderSubMap(preSubmitRetMap, orderNo));
        Map outMap = dealReturn(threePartMap, userProduct, preSubmitRetMap, preSubmitReqMap);
        Message out = new DefaultMessage();
        out.setBody(outMap);
        exchange.setOut(out);
    }

    public Map preData4PreSubmit(Object province, Map userProduct, Map threePartMap) throws Exception {
        List<Map> userInfoList = (List<Map>) userProduct.get("userInfo");
        if (IsEmptyUtils.isEmpty(userInfoList)) {
            throw new EcAopServerBizException("9999", "省份未返回USER_INFO信息");
        }
        Map user = (Map) threePartMap.get("userInfo");
        Map preSubmitMap = new HashMap();
        YearPayUtils ypu = new YearPayUtils();
        int brandNumber = 0;
        for (Map userInfo : userInfoList) {
            Map brandInfo = (Map) userInfo.get("brandInfo");
            if (!IsEmptyUtils.isEmpty(brandInfo)) {
                brandNumber = Integer.valueOf(brandInfo.get("brandNumber").toString());
            }
            preSubmitMap.put("userId", userInfo.get("userId"));
            preSubmitMap.put("serviceClassCode", userInfo.get("serviceClassCode"));
            List<Map> productInfo = (List<Map>) userInfo.get("productInfo");
            if (IsEmptyUtils.isEmpty(productInfo)) {
                throw new EcAopServerBizException("9999", "省份未返回PRODUCT_INFO信息");
            }
            preSubmitMap.put("tradeTypeCode", "0090");
            List<Map> userMktCamp = (List<Map>) userInfo.get("userMktCamp");
            if (IsEmptyUtils.isEmpty(userMktCamp)) {
                throw new EcAopServerBizException("9999", "省份未返当前用户的活动信息");
            }
            ypu.dealMktCamp(userMktCamp, brandNumber, province);
            if (!"13".equals(province)) {
                preSubmitMap.put("userMktCamp", userMktCamp);
            }
            preSubmitMap.put("userMktCamp1", userMktCamp);
            preSubmitMap.put("product", dealProduct(userMktCamp, user, province));
        }
        return preSubmitMap;
    }

    public List<Map> dealProduct(List<Map> userMktCamp, Map user, Object province) {
        List<Map> productList = new ArrayList<Map>();
        Map product = new HashMap();
        Map mkt = userMktCamp.get(0);
        product.put("defaultTag", "1");
        product.put("startEnable", "1");
        if (!"13".equals(province)) {
            product.put("modifyTag", "3");
            product.put("mktCampId", mkt.get("mktCampId"));
            product.put("productId", user.get("productId"));
            product.put("productMode", "01");// 01 主产品
        }
        else {
            product.put("modifyTag", "0");
            product.put("productMode", "03");// 03 附加产品
            product.put("productId", mkt.get("mktCampId"));
        }
        product.put("startDate", mkt.get("startDate"));
        product.put("endDate", mkt.get("endDate"));
        productList.add(product);
        return productList;
    }

    public Map preOrderSubMap(Map preSubmitRetMap, Object orderNo) {
        Map orderSubMap = new HashMap();
        orderSubMap.put("operationType", "02");// 02-订单取消
        Map rspInfo = (Map) preSubmitRetMap.get("rspInfo");
        orderSubMap.put("provOrderId", rspInfo.get("provOrderId"));
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
        subOrderSub.put("subProvinceOrderId", rspInfo.get("provOrderId"));
        subOrderSubReq.add(subOrderSub);
        orderSubMap.put("subOrderSubReq", subOrderSubReq);
        orderSubMap.put("cancleTotalFee", rspInfo.get("totalFee"));
        orderSubMap.put("origTotalFee", "0");
        return orderSubMap;
    }

    public Map dealReturn(Map threePartMap, Map userProduct, Map preSubmitRetMap, Map preSubmitReqMap) {
        Map retMap = new HashMap();
        retMap.put("code", "0000");
        retMap.put("detail", "TradeOK");
        Map custInfo = (Map) threePartMap.get("custInfo");
        if (IsEmptyUtils.isEmpty(custInfo)) {
            throw new EcAopServerBizException("9999", "省份三户接口未返回CUST_INFO信息");
        }
        retMap.put("custName", custInfo.get("custName"));
        List<Map> userInfoList = (List<Map>) userProduct.get("userInfo");
        if (IsEmptyUtils.isEmpty(userInfoList)) {
            throw new EcAopServerBizException("9999", "省份未返回USER_INFO信息");
        }
        for (Map userInfo : userInfoList) {
            List<Map> productInfo = (List<Map>) userInfo.get("productInfo");
            if (IsEmptyUtils.isEmpty(productInfo)) {
                throw new EcAopServerBizException("9999", "省份未返回PRODUCT_INFO信息");
            }
            Map mktMap = ((List<Map>) preSubmitReqMap.get("userMktCamp1")).get(0);
            String startDate = mktMap.get("startDate").toString();
            String endDate = mktMap.get("endDate").toString();
            for (Map product : productInfo) {
                if (!"01".equals(product.get("productMode"))) {
                    continue;
                }
                // Object brandSpeed = product.get("brandSpeed");
                // if (IsEmptyUtils.isEmpty(brandSpeed)) {
                // throw new EcAopServerBizException("9999", "省份未返回速率信息");
                // }
                // brandSpeed = new ChangeCodeUtils().changeSpeed(brandSpeed);
                // retMap.put("speed", brandSpeed);
                retMap.put("productName", product.get("productName"));
                retMap.put("discntName", mktMap.get("mktCampName"));
                retMap.put("startDate", startDate);
                retMap.put("endDate", endDate);
                // retMap.put("cycle", brandNumber);
            }
        }
        Map rspInfo = (Map) preSubmitRetMap.get("rspInfo");
        retMap.put("provOrderId", rspInfo.get("provOrderId"));
        List<Map> feeList = (List<Map>) rspInfo.get("feeInfo");
        if (!IsEmptyUtils.isEmpty(feeList)) {
            for (Map fee : feeList) {
                fee.put("operateType", "1");
            }
            retMap.put("feeInfo", feeList);
        }
        retMap.put("totalFee", rspInfo.get("totalFee"));
        retMap.put("productType", "1");
        return retMap;
    }
}
