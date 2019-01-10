package com.ailk.ecaop.common.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.product.DealNewCbssProduct;
import com.ailk.ecaop.biz.sub.gdjk.OpenApply4GProcessor;
import com.ailk.ecaop.common.processor.Xml2Json4FbsNoErrorMappingProcessor;
import com.ailk.ecaop.common.processor.Xml2JsonMappingProcessor;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.google.common.collect.Maps;

/**
 * 乱七八糟的各种公用方法^.^
 * isFinalCode这个值针对附加产品而言的，如果是Y附加产品的时间需要和合约保持一致
 * 如果是N或者不存在 需要去产品原始表计算产品的生失效时间
 * 对于附加产品的资费，需要去资费表里面计算，如果生失效时间标识是固定值 则和附加产品保持一致
 * 否则按计算结果为准
 * @author GaoLei
 */
public class TradeManagerUtils {

    /**
     * msg中包含province,SUBSCRIBE_ID
     * @param msg
     */
    public static void insert2CBSSTrade(Map msg) {
        try {
            LanOpenApp4GDao dao = new LanOpenApp4GDao();
            Map tradeParam = new HashMap();
            tradeParam.put("SUBSCRIBE_ID", msg.get("tradeId"));
            tradeParam.put("SUBSCRIBE_STATE", "0");
            tradeParam.put("NEXT_DEAL_TAG", "9");
            tradeParam.put("TRADE_TYPE_CODE", "10");
            tradeParam.put("OUT_ORDER_ID", msg.get("ordersId"));
            tradeParam.put("PROVINCE_CODE", "00" + msg.get("province"));
            tradeParam.put("SUBSYS_CODE", "HBPRE");
            tradeParam.put("IS_REMOTE", "2".equals(msg.get("IS_REMOTE")) ? "2" : "1");// 1:成卡、2：白卡
            tradeParam.put("NET_TYPE_CODE", "50");
            tradeParam.put("CANCEL_TAG", "0");
            tradeParam.put("BUSI_SPECIAL_FLAG", "0");
            if (null == msg.get("eModeCode") || "".equals(msg.get("eModeCode"))) {
                tradeParam.put("IN_MODE_CODE", "E");
            }
            else {
                tradeParam.put("IN_MODE_CODE", msg.get("eModeCode"));
            }
            tradeParam.put("SOP_STATE", "0");
            tradeParam.put("PRODUCT_ID", msg.get("mProductId"));
            tradeParam.put("BRAND_CODE", msg.get("mBrandCode"));
            tradeParam.put("USER_ID", msg.get("userId"));
            tradeParam.put("ACCT_ID", msg.get("acctId"));
            tradeParam.put("CUST_ID", msg.get("custId"));
            tradeParam.put("ACCEPT_DATE", GetDateUtils.getDate());
            tradeParam.put("TRADE_STAFF_ID", msg.get("operatorId"));
            tradeParam.put("TRADE_DEPART_ID", msg.get("channelId"));
            tradeParam.put("TRADE_CITY_CODE", msg.get("district"));
            tradeParam.put("CITY_CODE", msg.get("district"));
            tradeParam.put("TRADE_EPARCHY_CODE", msg.get("eparchyCode"));
            tradeParam.put("EPARCHY_CODE", msg.get("eparchyCode"));
            tradeParam.put("SERIAL_NUMBER", msg.get("serialNumber"));
            tradeParam.put("OPER_FEE", "0");
            tradeParam.put("FOREGIFT", "0");
            tradeParam.put("ADVANCE_PAY", "0");
            tradeParam.put("OLCOM_TAG", "0");
            tradeParam.put("REMARK", msg.get("remark"));
            tradeParam.put("RESOURCE_CODE", msg.get("resourcesCode"));
            tradeParam.put("ACTIVITY_TYPE", msg.get("ACTIVITY_TYPE"));
            dao.insertTrade(tradeParam);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "预提交完成后记录订单信息失败" + e.getMessage());
        }
    }

    /**
     * msg中包含province,SUBSCRIBE_ID
     * 融合接口专用
     * @param msg
     */
    public static void insert2MixTrade(Map msg) {
        try {
            LanOpenApp4GDao dao = new LanOpenApp4GDao();
            Map tradeParam = new HashMap();
            tradeParam.put("SUBSCRIBE_ID", msg.get("tradeId"));
            tradeParam.put("SUBSCRIBE_STATE", "0");
            tradeParam.put("NEXT_DEAL_TAG", "9");
            tradeParam.put("TRADE_TYPE_CODE", "10");
            tradeParam.put("OUT_ORDER_ID", msg.get("ordersId"));
            tradeParam.put("PROVINCE_CODE", "00" + msg.get("province"));
            tradeParam.put("SUBSYS_CODE", "HBPRE");
            tradeParam.put("IS_REMOTE", msg.get("IS_REMOTE"));// 1:成卡、2：白卡
            tradeParam.put("NET_TYPE_CODE", "50");
            tradeParam.put("CANCEL_TAG", "0");
            tradeParam.put("BUSI_SPECIAL_FLAG", "0");
            if (null == msg.get("eModeCode") || "".equals(msg.get("eModeCode"))) {
                tradeParam.put("IN_MODE_CODE", "E");
            }
            else {
                tradeParam.put("IN_MODE_CODE", msg.get("eModeCode"));
            }
            tradeParam.put("SOP_STATE", "0");
            tradeParam.put("PRODUCT_ID", msg.get("mProductId"));
            tradeParam.put("BRAND_CODE", msg.get("mBrandCode"));
            tradeParam.put("USER_ID", msg.get("userId"));
            tradeParam.put("ACCT_ID", msg.get("acctId"));
            tradeParam.put("CUST_ID", msg.get("custId"));
            tradeParam.put("ACCEPT_DATE", GetDateUtils.getDate());
            tradeParam.put("TRADE_STAFF_ID", msg.get("operatorId"));
            tradeParam.put("TRADE_DEPART_ID", msg.get("channelId"));
            tradeParam.put("TRADE_CITY_CODE", msg.get("district"));
            tradeParam.put("CITY_CODE", msg.get("district"));
            tradeParam.put("TRADE_EPARCHY_CODE", msg.get("eparchyCode"));
            tradeParam.put("EPARCHY_CODE", msg.get("eparchyCode"));
            tradeParam.put("SERIAL_NUMBER", msg.get("serialNumber"));
            tradeParam.put("OPER_FEE", "0");
            tradeParam.put("FOREGIFT", "0");
            tradeParam.put("ADVANCE_PAY", "0");
            tradeParam.put("OLCOM_TAG", "0");
            tradeParam.put("REMARK", msg.get("remark"));
            tradeParam.put("RESOURCE_CODE", msg.get("simId"));
            tradeParam.put("ACTIVITY_TYPE", msg.get("ACTIVITY_TYPE"));
            dao.insertTrade(tradeParam);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "预提交完成后记录订单信息失败" + e.getMessage());
        }
    }

    /**
     * msg中包含province,SUBSCRIBE_ID
     * @param msg
     */
    public static void insert2CBSSTrade2324(Map msg) {
        try {
            LanOpenApp4GDao dao = new LanOpenApp4GDao();
            Map tradeParam = new HashMap();
            tradeParam.put("SUBSCRIBE_ID", msg.get("tradeId"));
            tradeParam.put("SUBSCRIBE_STATE", "0");
            tradeParam.put("NEXT_DEAL_TAG", "9");
            tradeParam.put("TRADE_TYPE_CODE", "440");
            tradeParam.put("OUT_ORDER_ID", msg.get("ordersId"));
            tradeParam.put("PROVINCE_CODE", "00" + msg.get("province"));
            tradeParam.put("SUBSYS_CODE", "HBPRE");
            tradeParam.put("IS_REMOTE", msg.get("IS_REMOTE"));// 1:成卡、2：白卡
            tradeParam.put("NET_TYPE_CODE", "50");
            tradeParam.put("CANCEL_TAG", "0");
            tradeParam.put("BUSI_SPECIAL_FLAG", "0");
            if (null == msg.get("eModeCode") || "".equals(msg.get("eModeCode"))) {
                tradeParam.put("IN_MODE_CODE", "E");
            }
            else {
                tradeParam.put("IN_MODE_CODE", msg.get("eModeCode"));
            }
            tradeParam.put("SOP_STATE", "0");
            tradeParam.put("PRODUCT_ID", msg.get("mProductId"));
            tradeParam.put("BRAND_CODE", msg.get("mBrandCode"));
            tradeParam.put("USER_ID", msg.get("userId"));
            tradeParam.put("ACCT_ID", msg.get("acctId"));
            tradeParam.put("CUST_ID", msg.get("custId"));
            tradeParam.put("ACCEPT_DATE", GetDateUtils.getDate());
            tradeParam.put("TRADE_STAFF_ID", msg.get("operatorId"));
            tradeParam.put("TRADE_DEPART_ID", msg.get("channelId"));
            // 数据库中该字段为非空,23转4接口规范中区县为非必传,不传时插表会报错,增加默认值处理 by wangmc see 20170510版本技术文档
            tradeParam.put("TRADE_CITY_CODE",
                    IsEmptyUtils.isEmpty(msg.get("district")) ? "000000" : msg.get("district"));
            tradeParam.put("CITY_CODE", msg.get("district"));
            tradeParam.put("TRADE_EPARCHY_CODE", msg.get("eparchyCode"));
            tradeParam.put("EPARCHY_CODE", msg.get("eparchyCode"));
            tradeParam.put("SERIAL_NUMBER", msg.get("serialNumber"));
            tradeParam.put("OPER_FEE", "0");
            tradeParam.put("FOREGIFT", "0");
            tradeParam.put("ADVANCE_PAY", "0");
            tradeParam.put("OLCOM_TAG", "0");
            tradeParam.put("REMARK", msg.get("remark"));
            tradeParam.put("RESOURCE_CODE", msg.get("resourcesCode"));
            tradeParam.put("ACTIVITY_TYPE", msg.get("ACTIVITY_TYPE"));
            dao.insertTrade(tradeParam);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "预提交完成后记录订单信息失败" + e.getMessage());
        }
    }

    /**
     * @param msg
     */
    public void insert2TradeSop(Map msg) {
        try {
            LanOpenApp4GDao dao = new LanOpenApp4GDao();
            Map tradeParam = new HashMap();
            tradeParam.put("SUBSCRIBE_ID", msg.get("tradeId"));
            tradeParam.put("SUBSCRIBE_STATE", "0");
            tradeParam.put("NEXT_DEAL_TAG", "9");
            tradeParam.put("TRADE_TYPE_CODE", "10");
            tradeParam.put("OUT_ORDER_ID", msg.get("ordersId"));
            tradeParam.put("PROVINCE_CODE", "00" + msg.get("province"));
            tradeParam.put("SUBSYS_CODE", "HBPRE");
            tradeParam.put("IS_REMOTE", "2".equals(msg.get("IS_REMOTE")) ? "2" : "1");// 1:成卡、2：白卡
            tradeParam.put("NET_TYPE_CODE", "50");
            tradeParam.put("CANCEL_TAG", "0");
            tradeParam.put("BUSI_SPECIAL_FLAG", "0");
            if (null == msg.get("eModeCode") || "".equals(msg.get("eModeCode"))) {
                tradeParam.put("IN_MODE_CODE", "E");
            }
            else {
                tradeParam.put("IN_MODE_CODE", msg.get("eModeCode"));
            }
            tradeParam.put("SOP_STATE", "0");
            tradeParam.put("PRODUCT_ID", msg.get("mProductId"));
            tradeParam.put("BRAND_CODE", msg.get("mBrandCode"));
            tradeParam.put("USER_ID", msg.get("userId"));
            tradeParam.put("ACCT_ID", msg.get("acctId"));
            tradeParam.put("CUST_ID", msg.get("custId"));
            tradeParam.put("ACCEPT_DATE", GetDateUtils.getDate());
            tradeParam.put("TRADE_STAFF_ID", msg.get("operatorId"));
            tradeParam.put("TRADE_DEPART_ID", msg.get("channelId"));
            tradeParam.put("TRADE_CITY_CODE", msg.get("district"));
            tradeParam.put("CITY_CODE", msg.get("district"));
            tradeParam.put("TRADE_EPARCHY_CODE", msg.get("eparchyCode"));
            tradeParam.put("EPARCHY_CODE", msg.get("eparchyCode"));
            tradeParam.put("SERIAL_NUMBER", msg.get("serialNumber"));
            tradeParam.put("OPER_FEE", "0");
            tradeParam.put("FOREGIFT", "0");
            tradeParam.put("ADVANCE_PAY", "0");
            tradeParam.put("OLCOM_TAG", "0");
            tradeParam.put("REMARK", msg.get("remark"));
            tradeParam.put("RESOURCE_CODE", msg.get("resourcesCode"));
            tradeParam.put("ACTIVITY_TYPE", msg.get("ACTIVITY_TYPE"));
            dao.insertTrade(tradeParam);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "预提交完成后记录订单信息失败" + e.getMessage());
        }
    }

    /**
     * @param tradeId
     * @return
     * @throws Exception
     */
    public static String getPrintData4G(Exchange exchange, String tradeId) throws Exception {
        Map msg = new HashMap();
        msg.put("serviceProvideDomain", "0");
        msg.put("qrySysType", "0");
        List<Map> paraList = new ArrayList<Map>();
        Map para = new HashMap();
        para.put("paraId", "QUERYTYPE");
        para.put("paraValue", "1");
        paraList.add(para);

        Map para1 = new HashMap();
        para1.put("paraId", "IN_TAG");
        para1.put("paraValue", "1");
        paraList.add(para1);

        Map para2 = new HashMap();
        para2.put("paraId", "TRADE_ID");
        para2.put("paraValue", tradeId);
        paraList.add(para2);

        Map para3 = new HashMap();
        para3.put("paraId", "LCU_NAME");
        para3.put("paraValue", "TCS_GeneTradeReceiptInfoOnly");
        paraList.add(para3);

        msg.put("para", paraList);
        Map body = exchange.getIn().getBody(Map.class);
        body.put("msg", msg);
        exchange.getIn().setBody(body);

        LanUtils lan = new LanUtils();
        lan.preData("ecaop.param.mapping.sfck", exchange);
        CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
        Xml2JsonMappingProcessor proc = new Xml2JsonMappingProcessor();
        proc.applyParams(new String[] { "ecaop.template.3g.staffcheck" });
        proc.process(exchange);
        Map retPrintData = exchange.getOut().getBody(Map.class);

        List<Map> paraItem = (List<Map>) retPrintData.get("para");
        Map itemMap = new HashMap();
        for (int i = 0; i < paraItem.size(); i++) {
            itemMap.put(paraItem.get(i).get("paraId"), paraItem.get(i).get("paraValue"));
        }
        return itemMap.toString();
    }

    /**
     * @param commodityId
     * @return
     */
    public static String getMixProductTag(String commodityId) {
        Map inMap = new HashMap();
        inMap.put("commodity_id", commodityId);
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        List<Map> result = dao.queryMixProductTag(inMap);
        if (null == result || result.size() == 0) {
            throw new EcAopServerBizException("9999", "融合产品属性表未配置产品编码为【" + commodityId + "】的产品属性信息");
        }
        return String.valueOf(result.get(0).get("product_tag"));
    }

    /**
     * 根据资费属性获取产品信息(老库) 修改为调用没有产品ID的sql by wangmc 20170324
     */
    public static Map preProductInfoByElementId(Map inputMap) {
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        List<Map> productInfoResult = dao.queryProductInfoByElementId(inputMap);
        System.out.println("执行结果:" + productInfoResult.size() + "内容：" + productInfoResult);
        if (productInfoResult == null || productInfoResult.size() == 0) {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到元素ID【" + inputMap.get("elementId") + "】的产品信息");
        }
        Map dis = new HashMap();

        dis.put("productId", productInfoResult.get(0).get("PRODUCT_ID"));
        dis.put("packageId", productInfoResult.get(0).get("PACKAGE_ID"));
        dis.put("discntCode", productInfoResult.get(0).get("ELEMENT_ID"));
        Object itemId = inputMap.get("itemId");
        if (null != itemId && !"".equals(itemId)) {
            dis.put("itemId", itemId);
        }
        return dis;
    }

    /**
     * 根据资费属性获取产品信息(新库)
     * 传入元素的key为ELEMENT_ID
     */
    public static Map preProductInfoByElementId4CB(Map inputMap) {
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        List<Map> productInfoResult = n25Dao.queryDistProductInfoByElementId(inputMap);
        System.out.println("执行结果:" + productInfoResult.size() + "内容：" + productInfoResult);
        if (productInfoResult == null || productInfoResult.size() == 0) {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到元素ID【" + inputMap.get("ELEMENT_ID") + "】的产品信息");
        }
        Map dis = new HashMap();

        dis.put("productId", productInfoResult.get(0).get("PRODUCT_ID"));
        dis.put("packageId", productInfoResult.get(0).get("PACKAGE_ID"));
        dis.put("discntCode", productInfoResult.get(0).get("ELEMENT_ID"));
        Object itemId = inputMap.get("itemId");
        if (null != itemId && !"".equals(itemId)) {
            dis.put("itemId", itemId);
        }
        return dis;
    }

    /**
     * 根据资费属性获取产品信息(新库)
     * 传入元素的key为Iptv对应的ELEMENT_ID
     * @param iptvDelayDiscntType
     */
    public static Map preProductInfoByIpTvElementId4CB(Map inputMap, String iptvProductId, String iptvDelayDiscntType) {
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        List<Map> productInfoResult = n25Dao.queryDistProductInfoByElementId(inputMap);
        System.out.println("执行结果:" + productInfoResult.size() + "内容：" + productInfoResult);
        if (productInfoResult == null || productInfoResult.size() == 0) {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到元素ID【" + inputMap.get("ELEMENT_ID") + "】的产品信息");
        }
        Map dis = new HashMap();
        for (Map result : productInfoResult) {
            if (iptvProductId.equals(result.get("PRODUCT_ID").toString())) {
                dis.put("productId", result.get("PRODUCT_ID"));
                dis.put("packageId", result.get("PACKAGE_ID"));
                dis.put("discntCode", result.get("ELEMENT_ID"));

            }
        }
        if (IsEmptyUtils.isEmpty(dis)) {
            throw new EcAopServerBizException("9999", "根据产品ID【" + iptvProductId + "】未查询到资费【"
                    + inputMap.get("ELEMENT_ID") + "】的信息");
        }
        Object itemId = inputMap.get("itemId");
        if (null != itemId && !"".equals(itemId)) {
            dis.put("itemId", itemId);
        }
        // 判断iptv的生效时间，0-立即，1-次月
        if (!IsEmptyUtils.isEmpty(iptvDelayDiscntType)) {
            if ("0".equals(iptvDelayDiscntType)) {
                dis.put("startDate", GetDateUtils.getDate());
            }
            else if ("1".equals(iptvDelayDiscntType)) {
                dis.put("startDate", GetDateUtils.getNextMonthDate());
            }
        }

        return dis;
    }

    /**
     * 根据资费属性获取产品信息(新库)
     * 传入元素的key为ELEMENT_ID By zhousq
     */
    public static Map preProductInfoByBroadDiscntId4CB(Map inputMap) {
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        List<Map> productInfoResult = n25Dao.queryDistProductInfoByBroadDiscntId(inputMap);
        if (productInfoResult == null || productInfoResult.size() == 0) {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到元素ID【" + inputMap.get("ELEMENT_ID") + "】的产品信息");
        }
        Map dis = new HashMap();

        dis.put("productId", productInfoResult.get(0).get("PRODUCT_ID"));
        dis.put("packageId", productInfoResult.get(0).get("PACKAGE_ID"));
        dis.put("discntCode", productInfoResult.get(0).get("ELEMENT_ID"));
        Object itemId = inputMap.get("itemId");
        if (null != itemId && !"".equals(itemId)) {
            dis.put("itemId", itemId);
        }
        return dis;
    }

    /**
     * 根据资费ID获取宽带属性信息(新库)
     * 传入元素的key为broadDiscntId By zhousq
     */
    public static Map selBroadByBroadDiscntId4CB(Map inputMap) {
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        List<Map> productInfoResult = n25Dao.selBroadItemByBroadDiscntId4CB(inputMap);
        if (productInfoResult == null || productInfoResult.size() == 0) {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到资费ID【" + inputMap.get("broadDiscntId") + "】的属性信息");
        }
        Map dis = new HashMap();
        dis.put("cycleNum", productInfoResult.get(0).get("CYCLE_NUM"));
        dis.put("cycle", productInfoResult.get(0).get("PROD_CYCLE"));
        dis.put("cycleFee", productInfoResult.get(0).get("CYCLE_FEE"));
        return dis;
    }

    /**
     * 根据资费属性获取产品信息(老库) 修改为调用有产品ID的sql by zhousq 20171215
     */
    public static Map preProductInfoByBroadDiscntId(Map inputMap) {
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        List<Map> productInfoResult = dao.queryProductInfoByBroadDiscntId(inputMap);
        if (productInfoResult == null || productInfoResult.size() == 0) {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到元素ID【" + inputMap.get("PRODUCT_ID") + "】的产品信息");
        }
        Map dis = new HashMap();

        dis.put("productId", productInfoResult.get(0).get("PRODUCT_ID"));
        dis.put("packageId", productInfoResult.get(0).get("PACKAGE_ID"));
        dis.put("discntCode", productInfoResult.get(0).get("ELEMENT_ID"));
        Object itemId = inputMap.get("itemId");
        if (null != itemId && !"".equals(itemId)) {
            dis.put("itemId", itemId);
        }
        return dis;
    }

    /**
     * 根据资费属性获取产品信息,加上产品ID(老库)
     */
    public static Map preProductInfoByElementIdProductId(Map inputMap) {
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        List<Map> productInfoResult = dao.queryProductInfoByElementIdProductId(inputMap);
        System.out.println("执行结果:" + productInfoResult.size() + "内容：" + productInfoResult);
        if (productInfoResult == null || productInfoResult.size() == 0) {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到元素ID【" + inputMap.get("elementId") + "】的产品信息");
        }
        Map dis = new HashMap();

        dis.put("productId", productInfoResult.get(0).get("PRODUCT_ID"));
        dis.put("packageId", productInfoResult.get(0).get("PACKAGE_ID"));
        dis.put("discntCode", productInfoResult.get(0).get("ELEMENT_ID"));
        Object itemId = inputMap.get("itemId");
        if (null != itemId && !"".equals(itemId)) {
            dis.put("itemId", itemId);
        }
        return dis;
    }

    /**
     * 根据 资费ID和产品ID获取产品信息,不查询默认非默认(新库) by wangmc 20170414
     * 目前仅支持服务和资费
     * 参数:PRODUCT_ID,EPARCHY_CODE,ELEMENT_ID,ELEMENT_TYPE_CODE
     */
    public static Map preProductInfoNoDefaultTag4CB(Map inputMap) {
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        System.out.println("查询的请求参数为:" + inputMap);
        List<Map> productInfoResult = n25Dao.queryProdInfoByElementIdAndProdId(inputMap);
        System.out.println("执行结果:" + productInfoResult.size() + "内容：" + productInfoResult);
        if (IsEmptyUtils.isEmpty(productInfoResult)) {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到元素ID【" + inputMap.get("ELEMENT_ID") + "】的产品信息");
        }
        Map element = new HashMap();

        element.put("productId", productInfoResult.get(0).get("PRODUCT_ID"));
        element.put("packageId", productInfoResult.get(0).get("PACKAGE_ID"));
        if ("S".equals(inputMap.get("ELEMENT_TYPE_CODE"))) {
            element.put("serviceId", productInfoResult.get(0).get("ELEMENT_ID"));
        }
        else {
            element.put("discntCode", productInfoResult.get(0).get("ELEMENT_ID"));
        }
        Object itemId = inputMap.get("itemId");
        if (null != itemId && !"".equals(itemId)) {
            element.put("itemId", itemId);
        }
        return element;
    }

    /**
     * 根据资费属性获取产品信息,加上产品ID(新库)
     */
    public static Map preProductInfoByElementIdProductId4CB(Map inputMap) {
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        List<Map> productInfoResult = n25Dao.queryProductInfoByElementIdProductId(inputMap);
        System.out.println("执行结果:" + productInfoResult.size() + "内容：" + productInfoResult + "参数:" + inputMap);
        if (IsEmptyUtils.isEmpty(productInfoResult)) {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到元素ID【" + inputMap.get("ELEMENT_ID") + "】的产品信息");
        }
        Map dis = new HashMap();

        dis.put("productId", productInfoResult.get(0).get("PRODUCT_ID"));
        dis.put("packageId", productInfoResult.get(0).get("PACKAGE_ID"));
        dis.put("discntCode", productInfoResult.get(0).get("ELEMENT_ID"));
        Object itemId = inputMap.get("itemId");
        System.out.println("shishishi2" + itemId);
        if (null != itemId && !"".equals(itemId)) {
            dis.put("itemId", itemId);
        }
        return dis;
    }

    /**
     * 根据的服务ID和产品ID获取服务的属性信息(老库)
     */
    public static Map queryProductInfoSvcByElementIdProductId(Map inputMap) {
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        List<Map> productInfoResult = dao.queryProductSvcInfoByElementIdProductId(inputMap);
        System.out.println("执行结果:" + productInfoResult.size() + "内容：" + productInfoResult);
        if (productInfoResult == null || productInfoResult.size() == 0) {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到元素ID【" + inputMap.get("elementId") + "】的产品信息");
        }
        Map dis = new HashMap();

        dis.put("productId", productInfoResult.get(0).get("PRODUCT_ID"));
        dis.put("packageId", productInfoResult.get(0).get("PACKAGE_ID"));
        dis.put("discntCode", productInfoResult.get(0).get("ELEMENT_ID"));
        Object itemId = inputMap.get("itemId");
        if (null != itemId && !"".equals(itemId)) {
            dis.put("itemId", itemId);
        }
        return dis;
    }

    /**
     * 根据资费属性获取产品信息[去重,去新库]
     * 0是主产品
     */
    public static List<Map> preDistProductInfoByElementIdNew(Map msg, List<Map> productInfo) {
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        List<Map> disList = new ArrayList<Map>();
        List<Map> tempProductInfoResult = new ArrayList<Map>();
        List<Map> productInfoResult = n25Dao.queryDistProductInfoByElementId(msg);
        System.out.println("执行结果:" + productInfoResult.size() + "内容：" + productInfoResult);
        if (productInfoResult == null || productInfoResult.size() == 0) {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到元素ID【" + msg.get("elementId") + "】的产品信息");
        }
        for (int n = 0; n < productInfo.size(); n++) {
            if ("0".equals(productInfo.get(n).get("productMode"))) {
                for (int j = 0; j < productInfoResult.size(); j++) {
                    if (!productInfo.get(n).get("productId")
                            .equals(String.valueOf(productInfoResult.get(j).get("PRODUCT_ID")))) {
                        tempProductInfoResult.add(productInfoResult.get(j));
                        continue;
                    }
                }
            }
        }
        if (null != tempProductInfoResult && tempProductInfoResult.size() > 0) {
            productInfoResult.removeAll(tempProductInfoResult);
        }
        System.out.println("ssptesttempP" + tempProductInfoResult);
        // Map tradeMap = new HashMap();
        // MapUtils.arrayPut(tradeMap, msg, MagicNumber.COPYARRAY);
        // int size = productInfoResult.size();
        // // 生成ItemId号
        // List<String> boradDiscntItem = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, tradeMap),
        // "seq_item_id", size);
        for (int i = 0; i < productInfoResult.size(); i++) {
            Map dis = new HashMap();
            dis.put("productId", productInfoResult.get(i).get("PRODUCT_ID"));
            dis.put("packageId", productInfoResult.get(i).get("PACKAGE_ID"));
            dis.put("discntCode", productInfoResult.get(i).get("ELEMENT_ID"));
            // dis.put("itemId", boradDiscntItem.get(i));
            disList.add(dis);
        }

        return disList;
    }

    /**
     * 根据资费属性获取产品信息[去重]
     * 0是主产品
     */
    public static List<Map> preDistProductInfoByElementId(Map msg, List<Map> productInfo) {
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        List<Map> disList = new ArrayList<Map>();
        List<Map> tempProductInfoResult = new ArrayList<Map>();
        List<Map> productInfoResult = dao.queryDistProductInfoByElementId(msg);
        System.out.println("执行结果:" + productInfoResult.size() + "内容：" + productInfoResult);
        if (productInfoResult == null || productInfoResult.size() == 0) {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到元素ID【" + msg.get("elementId") + "】的产品信息");
        }
        for (int n = 0; n < productInfo.size(); n++) {
            if ("0".equals(productInfo.get(n).get("productMode"))) {
                for (int j = 0; j < productInfoResult.size(); j++) {
                    if (!productInfo.get(n).get("productId")
                            .equals(String.valueOf(productInfoResult.get(j).get("PRODUCT_ID")))) {
                        tempProductInfoResult.add(productInfoResult.get(j));
                        continue;
                    }
                }
            }
        }
        if (null != tempProductInfoResult && tempProductInfoResult.size() > 0) {
            productInfoResult.removeAll(tempProductInfoResult);
        }
        for (int i = 0; i < productInfoResult.size(); i++) {
            Map dis = new HashMap();
            dis.put("productId", productInfoResult.get(i).get("PRODUCT_ID"));
            dis.put("packageId", productInfoResult.get(i).get("PACKAGE_ID"));
            dis.put("discntCode", productInfoResult.get(i).get("ELEMENT_ID"));
            disList.add(dis);
        }
        return disList;
    }

    /**
     * 获取iptv信息(老库)
     */
    public static Map preProductInfoByServiceId(Map inputMap) {
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        List<Map> productInfoResult = dao.queryProductIdByserviceId(inputMap);
        System.out.println("iptvInfo:" + productInfoResult);
        if (productInfoResult == null || productInfoResult.size() == 0) {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到元素ID【" + inputMap.get("serviceId") + "】的产品信息");
        }
        Map svc = new HashMap();
        svc.put("productId", productInfoResult.get(0).get("PRODUCT_ID"));
        svc.put("packageId", productInfoResult.get(0).get("PACKAGE_ID"));
        svc.put("serviceId", productInfoResult.get(0).get("ELEMENT_ID"));
        Object itemId = inputMap.get("itemId");
        if (null != itemId && !"".equals(itemId)) {
            svc.put("itemId", itemId);
        }
        return svc;
    }

    /**
     * 获取iptv信息(新库)
     */
    public static Map preProductInfoByServiceId4CB(Map inputMap) {
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        List<Map> productInfoResult = n25Dao.qryProductInfoByElementId(inputMap);
        System.out.println("iptvInfo:" + productInfoResult);
        if (productInfoResult == null || productInfoResult.size() == 0) {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到元素ID【" + inputMap.get("ELEMENT_ID") + "】的产品信息");
        }
        Map svc = new HashMap();
        svc.put("productId", productInfoResult.get(0).get("PRODUCT_ID"));
        svc.put("packageId", productInfoResult.get(0).get("PACKAGE_ID"));
        svc.put("serviceId", productInfoResult.get(0).get("ELEMENT_ID"));
        Object itemId = inputMap.get("itemId");
        if (null != itemId && !"".equals(itemId)) {
            svc.put("itemId", itemId);
        }
        return svc;
    }

    /**
     * 获取iptv信息(新库)
     */
    public static Map preProductInfoByIpsServiceId4CB(Map inputMap, String iptvProductId) {
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        List<Map> productInfoResult = n25Dao.qryProductInfoByElementId(inputMap);
        if (productInfoResult == null || productInfoResult.size() == 0) {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到元素ID【" + inputMap.get("ELEMENT_ID") + "】的产品信息");
        }
        Map svc = new HashMap();
        for (Map result : productInfoResult) {
            if (iptvProductId.equals(result.get("PRODUCT_ID").toString())) {
                svc.put("productId", result.get("PRODUCT_ID"));
                svc.put("packageId", result.get("PACKAGE_ID"));
                svc.put("serviceId", result.get("ELEMENT_ID"));
            }
        }
        Object itemId = inputMap.get("itemId");
        if (null != itemId && !"".equals(itemId)) {
            svc.put("itemId", itemId);
        }

        return svc;
    }

    /**
     * 该方法处理新产品逻辑
     * @param productId
     * @param productMode
     * @param provinceCode
     * @param firstMonBillMode
     * @param msg msg中需要包含userId,serialNumber
     * @throws Exception
     */
    public static Map newPreProductInfo(List<Map> productInfo, Object provinceCode, Map msg) {

        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
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
                List<Map> packageElementInfo = new ArrayList<Map>();
                proparam.put("PROVINCE_CODE", provinceCode);
                proparam.put("PRODUCT_ID", actPlanId);
                String newActPlanId = n25Dao.queryActivityByCommodityId(proparam).toString();
                String actMonthFirstDay = "";
                String actMonthLasttDay = "";
                if ("mags".equals(msg.get("methodCode"))) {
                    actMonthFirstDay = (String) activityInfo.get(i).get("startDate");
                    actMonthLasttDay = (String) activityInfo.get(i).get("endDate");
                    msg.put("magsStartDate", actMonthFirstDay);
                    msg.put("magsEndDate", actMonthLasttDay);
                    Map actProParam = new HashMap();
                    actProParam.put("PRODUCT_ID", actPlanId);
                    List<Map> actProductInfo = n25Dao.queryProductAndPtypeProduct(actProParam);
                    if (actProductInfo == null || actProductInfo.size() == 0) {
                        throw new EcAopServerBizException("9999", "在产品表或者产品属性表中未查询到产品ID【" + actPlanId + "】的产品信息");
                    }
                    Map detailProduct = actProductInfo.get(0);
                    String subProductTypeCode = (String) detailProduct.get("PRODUCT_TYPE_CODE");
                    // 要入订单表，然后正式提交的时候需要
                    msg.put("ACTIVITY_TYPE", subProductTypeCode);
                }
                else {
                    activityTimeMap = getEffectTime(newActPlanId, monthFirstDay, monthLasttDay);
                    actMonthFirstDay = (String) activityTimeMap.get("monthFirstDay");
                    actMonthLasttDay = (String) activityTimeMap.get("monthLasttDay");
                }
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
                        /* peparam.put("PROVINCE_CODE", provinceCode);
                         * peparam.put("COMMODITY_ID", actPlanId);
                         * peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                         * peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                         * peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                         * List<Map> packageElementInfo = dao.queryPackageElementInfo(peparam); */
                        peparam.put("PROVINCE_CODE", provinceCode);
                        peparam.put("PRODUCT_ID", actPlanId);
                        peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                        peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                        peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                        packageElementInfo = n25Dao.queryPackageElementInfo(peparam);
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

                String addProductId = "";
                String strBrandCode = "";
                String strProductTypeCode = "";
                String strProductMode = "";

                Map addproparam = new HashMap();
                /* addproparam.put("PROVINCE_CODE", provinceCode);
                 * addproparam.put("COMMODITY_ID", commodityId);
                 * // 原始表查询活动用 productid
                 * addproparam.put("PRODUCT_ID", commodityId);
                 * addproparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                 * addproparam.put("FIRST_MON_BILL_MODE", null);
                 * List<Map> addproductInfoResult = dao.queryAddProductInfo(addproparam); */
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
                /* Map actProParam = new HashMap();
                 * actProParam.put("PRODUCT_ID", newActPlanId);
                 * List<Map> actProductInfo = dao.queryActProductInfo(actProParam);
                 * if (actProductInfo == null || actProductInfo.size() == 0) {
                 * throw new EcAopServerBizException("9999", "在产品表或者产品属性表中未查询到产品ID【" + newActPlanId + "】的产品信息");
                 * } */
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
            List<Map> packageElement = (List<Map>) productInfo.get(i).get("packageElement");
            String productMode = String.valueOf(productInfo.get(i).get("productMode"));
            String firstMonBillMode = String.valueOf(productInfo.get(i).get("firstMonBillMode"));
            String productId = String.valueOf(productInfo.get(i).get("productId"));
            String isIpOrInterTv = "";
            if (null != productInfo.get(i).get("isIpOrInterTv")) {
                isIpOrInterTv = (String) productInfo.get(i).get("isIpOrInterTv");
            }
            if ("0".equals(productMode)) {
                System.out.println("===========主产品产品处理");
                Map productTpye = new HashMap();
                Map product = new HashMap();

                String commodityId = productId;
                String strBrandCode = "";
                String strProductTypeCode = "";
                String strProductMode = "";

                Map proparam = new HashMap();
                /* proparam.put("PROVINCE_CODE", provinceCode);
                 * proparam.put("COMMODITY_ID", commodityId);
                 * proparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                 * proparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                 * List<Map> productInfoResult = dao.queryProductInfo(proparam); */
                proparam.put("PROVINCE_CODE", provinceCode);
                proparam.put("PRODUCT_ID", productId);
                proparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                proparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                proparam.put("PRODUCT_MODE", "00");
                List<Map> productInfoResult;

                if ("spec".equals(msg.get("method"))) {
                    productInfoResult = n25Dao.qryDefaultPackageElementEcs(proparam);
                }
                else {
                    productInfoResult = n25Dao.qryDefaultPackageElement(proparam);
                }
                if (productInfoResult == null || productInfoResult.size() == 0) {
                    // 不能报错，可能这个产品下的属性全是可选的
                    throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品信息");
                }
                if ("mvoa".equals(methodCode) || "mofc".equals(methodCode) || "234s".equals(methodCode)) {
                    // productInfoResult = chooseSpeed(productInfoResult, msg.get("phoneSpeedLevel") + "");
                    productInfoResult = new NewProductUtils().chooseSpeed(productInfoResult, msg.get("phoneSpeedLevel")
                            + "");// 处理速率
                }
                productId = String.valueOf(productInfoResult.get(0).get("PRODUCT_ID"));
                if (productId.equals("-1")) {
                    productId = String.valueOf(productInfoResult.get(1).get("PRODUCT_ID"));
                }
                // 需要按偏移处理时间的资费 by wangmc 20181102
                NewProductUtils utils = new NewProductUtils();
                List<String> specialDiscnt = utils.qrySpealDealDiscnt4NewPreProductInfo(msg);
                for (int j = 0; j < productInfoResult.size(); j++) {

                    if ("D".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                        Map dis = new HashMap();
                        String discntCode = String.valueOf(productInfoResult.get(j).get("ELEMENT_ID"));
                        dis.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                        dis.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                        dis.put("discntCode", discntCode);
                        dis.put("productMode", productInfoResult.get(j).get("PRODUCT_MODE"));
                        // 根据配置判断是否处理主资费的开始时间和结束时间by wangmc 20181102
                        if (utils.dealMainProDiscntDate(dis, specialDiscnt, productId, "N25")) {
                            continue;
                        }
                        Map expTimeMap = TradeManagerUtils
                                .getDiscntEffectTime(discntCode, monthFirstDay, monthLasttDay);
                        dis.put("activityStarTime", expTimeMap.get("monthFirstDay"));
                        dis.put("activityTime", expTimeMap.get("monthLasttDay"));
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
                        spItemParam.put("SPSERVICEID", productInfoResult.get(j).get("ELEMENT_ID"));
                        List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                        if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                            throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                    + productInfoResult.get(j).get("ELEMENT_ID") + "】的产品属性信息");
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

                strProductMode = "00";
                strBrandCode = (String) productInfoResult.get(0).get("BRAND_CODE");
                strProductTypeCode = (String) productInfoResult.get(0).get("PRODUCT_TYPE_CODE");
                /* strBrandCode = getValueFromItem("BRAND_CODE", productItemInfoResult);
                 * strProductTypeCode = getValueFromItem("PRODUCT_TYPE_CODE", productItemInfoResult); */

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
                System.out.println("20170320packageElement" + packageElement);
                if (packageElement != null && packageElement.size() > 0) {
                    for (int n = 0; n < packageElement.size(); n++) {
                        Map peparam = new HashMap();
                        peparam.put("PROVINCE_CODE", provinceCode);
                        // peparam.put("COMMODITY_ID", productId); 去新库查询要用PRODUCT_ID
                        peparam.put("PRODUCT_ID", productId);
                        peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                        peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                        peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                        // List<Map> packageElementInfo = dao.queryPackageElementInfo(peparam);
                        // 主产品下附加包处理也去新库查询 by wangmc 20170303
                        List<Map> packageElementInfo = n25Dao.queryPackageElementInfo(peparam);
                        System.out.println("20170320packageElementInfo" + packageElementInfo);
                        if (packageElementInfo != null && packageElementInfo.size() > 0) {
                            for (Map packageMap : packageElementInfo) {
                                if ("D".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    String elementId = String.valueOf(packageMap.get("ELEMENT_ID"));
                                    Map dis = new HashMap();
                                    dis.put("productId", packageMap.get("PRODUCT_ID"));
                                    dis.put("packageId", packageMap.get("PACKAGE_ID"));
                                    dis.put("discntCode", elementId);
                                    dis.put("productMode", packageMap.get("PRODUCT_MODE"));
                                    // 根据配置判断是否处理主资费的开始时间和结束时间by wangmc 20181102
                                    if (utils.dealMainProDiscntDate(dis, specialDiscnt, productId, "N25")) {
                                        continue;
                                    }
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
                                    /* spItemParam.put("PTYPE", "X");
                                     * spItemParam.put("COMMODITY_ID", productId);
                                     * spItemParam.put("PROVINCE_CODE", provinceCode);
                                     * spItemParam.put("PID", packageMap.get("ELEMENT_ID"));
                                     * List<Map> spItemInfoResult = dao.queryProductItemInfo(spItemParam); */
                                    spItemParam.put("SPSERVICEID", packageMap.get("ELEMENT_ID"));
                                    List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                                    if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                        throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                                + packageMap.get("ELEMENT_ID") + "】的产品属性信息");
                                    }
                                    for (int j = 0; j < spItemInfoResult.size(); j++) {
                                        Map spItemInfo = spItemInfoResult.get(j);
                                        spId = (String) spItemInfo.get("SP_ID");
                                        partyId = (String) spItemInfo.get("PARTY_ID");
                                        spProductId = (String) spItemInfo.get("SP_PRODUCT_ID");
                                        /* if ("SP_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                         * spId = (String) (spItemInfo.get("ATTR_VALUE"));
                                         * } else if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                         * partyId = (String) (spItemInfo.get("ATTR_VALUE"));
                                         * } else if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                         * spProductId = (String) (spItemInfo.get("ATTR_VALUE"));
                                         * } */
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
                addproparam.put("PRODUCT_ID", productId);
                addproparam.put("PRODUCT_MODE", "01");
                addproparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                addproparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                List<Map> addproductInfoResult = new ArrayList<Map>();
                if (!StringUtils.isEmpty(isIpOrInterTv)) {
                    addproductInfoResult = n25Dao.queryIptvOrIntertvProductInfo(addproparam);
                }
                else {

                    addproductInfoResult = n25Dao.qryDefaultPackageElement(addproparam);
                    // addproductInfoResult = dao.queryAddProductInfo(addproparam);
                }
                if (addproductInfoResult == null || addproductInfoResult.size() == 0) {
                    // 未查询到附加产品的默认资费或服务,不报错,去TD_B_PRODUCT表查询,产品不存在就抛错,存在继续执行 by wangmc 20170302
                    List<Map> addproductInfoList = n25Dao.qryProductInfoByProductTable(addproparam);
                    if (!IsEmptyUtils.isEmpty(addproductInfoList)) {
                        addProductId = String.valueOf(addproductInfoList.get(0).get("PRODUCT_ID"));
                        strProductMode = String.valueOf(addproductInfoList.get(0).get("PRODUCT_MODE"));
                    }
                    else {
                        throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + commodityId + "】的产品信息");
                    }
                }
                else {
                    addProductId = String.valueOf(addproductInfoResult.get(0).get("PRODUCT_ID"));
                    strProductMode = String.valueOf(addproductInfoResult.get(0).get("PRODUCT_MODE"));
                }
                if ("mvoa".equals(methodCode) || "mofc".equals(methodCode)) {
                    // addproductInfoResult = chooseSpeed(addproductInfoResult, msg.get("phoneSpeedLevel") + "");
                    addproductInfoResult = new NewProductUtils().chooseSpeed(addproductInfoResult,
                            msg.get("phoneSpeedLevel") + "");// 处理速率
                }
                isFinalCode = NewProductUtils.getEndDateType(addProductId);
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
                            /* spItemParam.put("PTYPE", "X");
                             * spItemParam.put("COMMODITY_ID", commodityId);
                             * spItemParam.put("PROVINCE_CODE", provinceCode);
                             * spItemParam.put("PID", addproductInfoResult.get(j).get("ELEMENT_ID"));
                             * List<Map> spItemInfoResult = dao.queryProductItemInfo(spItemParam); */
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
                                /* if ("SP_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                 * spId = (String) (spItemInfo.get("ATTR_VALUE"));
                                 * } else if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                 * partyId = (String) (spItemInfo.get("ATTR_VALUE"));
                                 * } else if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                 * spProductId = (String) (spItemInfo.get("ATTR_VALUE"));
                                 * } */
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
                /* Map additparam = new HashMap(); *//* additparam.put("PROVINCE_CODE", provinceCode);
                                                      * additparam.put("PID", addProductId);
                                                      * additparam.put("COMMODITY_ID", commodityId);
                                                      * additparam.put("PTYPE", "U");
                                                      * List<Map> addProductItemInfoResult =
                                                      * dao.queryProductItemInfo(additparam); *//* additparam.put(
                                                                                                 * "PROVINCE_CODE",
                                                                                                 * provinceCode);
                                                                                                 * additparam.put(
                                                                                                 * "PRODUCT_ID",
                                                                                                 * productId);
                                                                                                 * List<Map>
                                                                                                 * addProductItemInfoResult
                                                                                                 * = n25Dao.
                                                                                                 * queryProductAndPtypeProduct
                                                                                                 * (additparam);
                                                                                                 * if
                                                                                                 * (addProductItemInfoResult
                                                                                                 * == null ||
                                                                                                 * addProductItemInfoResult
                                                                                                 * .size() == 0) {
                                                                                                 * throw new
                                                                                                 * EcAopServerBizException
                                                                                                 * ("9999",
                                                                                                 * "在产品映射表中未查询到产品ID【" +
                                                                                                 * commodityId +
                                                                                                 * "】的产品属性信息");
                                                                                                 * } */

                strProductMode = "01";
                Map additparam = new HashMap();
                additparam.put("PRODUCT_ID", commodityId);
                List<Map> addProductItemInfoResult = n25Dao.queryProductAndPtypeProduct(additparam);
                if (addProductItemInfoResult == null || addProductItemInfoResult.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + productId + "】的产品属性信息");
                }
                strBrandCode = (String) addProductItemInfoResult.get(0).get("BRAND_CODE");
                strProductTypeCode = (String) addProductItemInfoResult.get(0).get("PRODUCT_TYPE_CODE");

                /* strBrandCode = getValueFromItem("BRAND_CODE", productItemInfoResult);
                 * strProductTypeCode = getValueFromItem("PRODUCT_TYPE_CODE", productItemInfoResult); */

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
                        /* peparam.put("PROVINCE_CODE", provinceCode);
                         * peparam.put("COMMODITY_ID", productId);
                         * peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                         * peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                         * peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                         * List<Map> packageElementInfo = dao.queryPackageElementInfo(peparam); */
                        peparam.put("PROVINCE_CODE", provinceCode);
                        peparam.put("PRODUCT_ID", productId);
                        peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                        peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                        peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                        List<Map> packageElementInfo = n25Dao.queryPackageElementInfo(peparam);
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
                                    /* spItemParam.put("PTYPE", "X");
                                     * spItemParam.put("COMMODITY_ID", productId);
                                     * spItemParam.put("PROVINCE_CODE", provinceCode);
                                     * spItemParam.put("PID", packageMap.get("ELEMENT_ID"));
                                     * List<Map> spItemInfoResult = dao.queryProductItemInfo(spItemParam); */
                                    spItemParam.put("SPSERVICEID", packageElementInfo.get(0).get("ELEMENT_ID"));
                                    List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                                    if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                        throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                                + packageMap.get("ELEMENT_ID") + "】的产品属性信息");
                                    }
                                    for (int j = 0; j < spItemInfoResult.size(); j++) {
                                        Map spItemInfo = spItemInfoResult.get(j);
                                        /* if ("SP_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                         * spId = (String) (spItemInfo.get("ATTR_VALUE"));
                                         * } else if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                         * partyId = (String) (spItemInfo.get("ATTR_VALUE"));
                                         * } else if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                         * spProductId = (String) (spItemInfo.get("ATTR_VALUE"));
                                         * } */
                                        spId = (String) spItemInfo.get("SP_ID");
                                        partyId = (String) spItemInfo.get("PARTY_ID");
                                        spProductId = (String) spItemInfo.get("SP_PRODUCT_ID");
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

        // 使用新的去重方法 by wangmc 20170410
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
        ext.put("tradeProduct", openApplyPro.preProductData(msg));// TODO 2018-01-29产品时间问题
        ext.put("tradeDiscnt", openApplyPro.preDiscntData(msg));
        ext.put("tradeSvc", openApplyPro.preTradeSvcData(msg));
        ext.put("tradeSp", openApplyPro.preTradeSpData(msg));
        return ext;
    }

    /**
     * 该方法处理老产品逻辑
     * @param productId
     * @param productMode
     * @param provinceCode
     * @param firstMonBillMode
     * @param msg msg中需要包含userId,serialNumber
     * @throws Exception
     */
    public static Map preProductInfo(List<Map> productInfo, Object provinceCode, Map msg) {
        System.out.println("liujiadi==========3:" + productInfo);
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
        String appCode = (String) msg.get("appCode");
        System.out.println("zsqtest888" + appCode);
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
                activityTimeMap = getEffectTimeForMainPro(newActPlanId, monthFirstDay, monthLasttDay);
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
                if ("mofc".equals(methodCode) && "mpln".equals(appCode)) {
                    try {
                        actMonthFirstDay = GetDateUtils.transDate(actMonthFirstDay, 14);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                        throw new EcAopServerBizException("9999", "时间格式转换失败");
                    }
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
        }

        for (int i = 0; i < productInfo.size(); i++) {
            List<Map> packageElement = (List<Map>) productInfo.get(i).get("packageElement");
            String productMode = String.valueOf(productInfo.get(i).get("productMode"));
            String firstMonBillMode = String.valueOf(productInfo.get(i).get("firstMonBillMode"));
            String productId = String.valueOf(productInfo.get(i).get("productId"));
            String isIpOrInterTv = "";
            if (null != productInfo.get(i).get("isIpOrInterTv")) {
                isIpOrInterTv = (String) productInfo.get(i).get("isIpOrInterTv");
            }
            if ("0".equals(productMode)) {
                System.out.println("===========主产品产品处理");
                Map productTpye = new HashMap();
                Map product = new HashMap();

                String commodityId = productId;
                String strBrandCode = "";
                String strProductTypeCode = "";
                String strProductMode = "";
                String mainProMonthFirstDay = "";
                String mainProMonthLasttDay = "";

                Map proparam = new HashMap();
                proparam.put("PROVINCE_CODE", provinceCode);
                proparam.put("COMMODITY_ID", commodityId);
                proparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                proparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                List<Map> productInfoResult = dao.queryProductInfo(proparam);
                System.out.println("liujiadi==========4:" + productInfoResult);
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
                // 范学民
                isFinalCode = getEndDate(productId);
                if ("N".equals(isFinalCode) || "X".equals(isFinalCode)) {
                    Map productDate = getEffectTimeForMainPro(productId, monthFirstDay, monthLasttDay);
                    mainProMonthFirstDay = (String) productDate.get("monthFirstDay");
                    mainProMonthLasttDay = (String) productDate.get("monthLasttDay");
                }
                else {
                    String activityTime = (String) activityTimeMap.get("monthLasttDay");
                    if (StringUtils.isEmpty(activityTime)) {
                        throw new EcAopServerBizException("9999", "所选主产品" + commodityId + "生失效时间需要和合约保持一致，"
                                + "请检查合约信息是否已传或更换主产品信息");
                    }
                    mainProMonthFirstDay = (String) activityTimeMap.get("monthFirstDay");
                    mainProMonthLasttDay = (String) activityTimeMap.get("monthLasttDay");
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
                        if (!"Y".equals(isFinalCode)) {
                            Map discntDateMap = getDiscntEffectTimeForMainPro(
                                    String.valueOf(productInfoResult.get(j).get("ELEMENT_ID")), mainProMonthFirstDay,
                                    mainProMonthLasttDay);
                            dis.put("activityStarTime", discntDateMap.get("monthFirstDay"));
                            dis.put("activityTime", discntDateMap.get("monthLasttDay"));
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
                        List<Map> packageElementInfo = null;
                        if ("sdprbsop3G".equals(msg.get("appCodeAndMethodCode") + "")) {
                            packageElementInfo = dao.queryPackageElementInfoForSD(peparam);
                        }
                        else {
                            packageElementInfo = dao.queryPackageElementInfo(peparam);
                        }

                        System.out.println("走进来了么？？？？？？？" + packageElementInfo);
                        if (packageElementInfo != null && packageElementInfo.size() > 0) {
                            for (Map packageMap : packageElementInfo) {
                                if ("D".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    String elementId = String.valueOf(packageMap.get("ELEMENT_ID"));
                                    Map dis = new HashMap();
                                    dis.put("productId", packageMap.get("PRODUCT_ID"));
                                    dis.put("packageId", packageMap.get("PACKAGE_ID"));
                                    dis.put("discntCode", elementId);
                                    dis.put("productMode", packageMap.get("PRODUCT_MODE"));
                                    Map discntDateMap = getDiscntEffectTimeFor3G(elementId, monthFirstDay,
                                            monthLasttDay);
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
                                            spId = (String) (spItemInfo.get("ATTR_VALUE"));
                                        }
                                        else if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                            partyId = (String) (spItemInfo.get("ATTR_VALUE"));
                                        }
                                        else if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                            spProductId = (String) (spItemInfo.get("ATTR_VALUE"));
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
                if (!StringUtils.isEmpty(isIpOrInterTv)) {
                    addproductInfoResult = dao.queryIptvOrIntertvProductInfo(addproparam);
                }
                else {
                    addproductInfoResult = dao.queryAddProductInfo(addproparam);
                }
                if (addproductInfoResult == null || addproductInfoResult.size() == 0) {
                    // throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品信息");
                    // 附加产品下没有默认元素不抛错
                    List<Map> addProductInfo = dao.queryProductAndPtypeProduct(MapUtils
                            .asMap("PRODUCT_ID", commodityId));
                    if (IsEmptyUtils.isEmpty(addProductInfo)) {
                        throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品信息");
                    }
                    // 修改字段取值位置
                    addProductId = String.valueOf(addProductInfo.get(0).get("PRODUCT_ID"));
                    strProductMode = String.valueOf(addProductInfo.get(0).get("PRODUCT_MODE"));
                }
                else {
                    addProductId = String.valueOf(addproductInfoResult.get(0).get("PRODUCT_ID"));
                    strProductMode = String.valueOf(addproductInfoResult.get(0).get("PRODUCT_MODE"));
                }
                if ("mvoa".equals(methodCode) || "mofc".equals(methodCode)) {
                    addproductInfoResult = chooseSpeed(addproductInfoResult, msg.get("phoneSpeedLevel") + "");
                }
                isFinalCode = getEndDate(addProductId);
                if ("N".equals(isFinalCode) || "X".equals(isFinalCode)) {
                    if ("0017".equals(provinceCode)) {// 山东处理附加产品或者活动的失效时间查老库
                        Map productDate = getEffectTimeForMainPro(addProductId, monthFirstDay, monthLasttDay);
                        addProMonthFirstDay = (String) productDate.get("monthFirstDay");
                        addProMonthLasttDay = (String) productDate.get("monthLasttDay");
                        // 全业务要求转成14位 addby sss
                        try {
                            addProMonthLasttDay = GetDateUtils.transDate(addProMonthLasttDay, 14);
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                            throw new EcAopServerBizException("9999", "时间格式转换失败");
                        }
                    }
                    else {
                        Map productDate = getEffectTime(addProductId, monthFirstDay, monthLasttDay);
                        addProMonthFirstDay = (String) productDate.get("monthFirstDay");
                        addProMonthLasttDay = (String) productDate.get("monthLasttDay");
                    }
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
                                Map discntDateMap = getDiscntEffectTimeFor3G(elementId, addProMonthFirstDay,
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
                                        Map discntDateMap = getDiscntEffectTimeFor3G(elementId, addProMonthFirstDay,
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
                                            spId = (String) (spItemInfo.get("ATTR_VALUE"));
                                        }
                                        else if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                            partyId = (String) (spItemInfo.get("ATTR_VALUE"));
                                        }
                                        else if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                            spProductId = (String) (spItemInfo.get("ATTR_VALUE"));
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

        // 使用新的去重方法 by wangmc 20170410
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
        System.out.println("liujiadi==========5:" + discntList);
        // 增加活动结束时间
        // msg.put("activityTime", monthLasttDay);

        OpenApply4GProcessor openApplyPro = new OpenApply4GProcessor();
        ext.put("tradeProductType", openApplyPro.preProductTpyeListData(msg));
        ext.put("tradeProduct", openApplyPro.preProductData(msg));
        ext.put("tradeDiscnt", openApplyPro.preDiscntData(msg));
        ext.put("tradeSvc", openApplyPro.preTradeSvcData(msg));
        ext.put("tradeSp", openApplyPro.preTradeSpData(msg));
        System.out.println("liujiadi==========6:" + ext);
        return ext;
    }

    /**
     * 该方法仅用于处理融合固网产品(新库) by wangmc 20170324
     * @param productId
     * @param productMode
     * @param provinceCode
     * @param firstMonBillMode
     * @param msg msg中需要包含userId,serialNumber
     */
    public static Map preProductInfo4Mix(List<Map> productInfo, Object provinceCode, Map msg) {

        Object apptx = msg.get("apptx");
        System.out.println(apptx + "请求进来的产品信息为:" + productInfo);
        // LanOpenApp4GDao dao = new LanOpenApp4GDao();
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();

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
                proparam.put("PRODUCT_MODE", "50");
                proparam.put("PRODUCT_ID", actPlanId);
                proparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                proparam.put("FIRST_MON_BILL_MODE", null);
                List<Map> productInfoResult = n25Dao.qryDefaultPackageElement(proparam);
                if (productInfoResult == null || productInfoResult.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品映射表中未查询到活动ID【" + actPlanId + "】的信息");
                }
                String newActPlanId = productInfoResult.get(0).get("PRODUCT_ID") + "";
                activityTimeMap = getEffectTime(newActPlanId, monthFirstDay, monthLasttDay);
                String actMonthFirstDay = (String) activityTimeMap.get("monthFirstDay");
                String actMonthLasttDay = (String) activityTimeMap.get("monthLasttDay");
                msg.put("resActivityper", activityTimeMap.get("resActivityper"));
                if (null != productInfoResult && productInfoResult.size() > 0) {
                    for (int j = 0; j < productInfoResult.size(); j++) {

                        if ("D".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map dis = new HashMap();
                            dis.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                            dis.put("productMode", productInfoResult.get(j).get("PRODUCT_MODE"));
                            dis.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                            dis.put("discntCode", productInfoResult.get(j).get("ELEMENT_ID"));
                            // 算出活动的开始结束时间，预提交下发
                            dis.put("activityStarTime", actMonthFirstDay);
                            dis.put("activityTime", actMonthLasttDay);
                            discntList.add(dis);
                        }
                        if ("S".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map svc = new HashMap();
                            svc.put("serviceId", productInfoResult.get(j).get("ELEMENT_ID"));
                            svc.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                            svc.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                            // 算出活动的开始结束时间，预提交下发
                            svc.put("activityStarTime", actMonthFirstDay);
                            svc.put("activityTime", actMonthLasttDay);
                            svcList.add(svc);
                        }
                        if ("X".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            // 暂不处理活动下的sp;
                        }

                    }
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
                        List<Map> packageElementInfo = n25Dao.queryPackageElementInfo(peparam);
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
                                    dis.put("activityTime", actMonthLasttDay);
                                    discntList.add(dis);
                                }
                                if ("S".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    Map svc = new HashMap();
                                    svc.put("serviceId", packageMap.get("ELEMENT_ID"));
                                    svc.put("productId", packageMap.get("PRODUCT_ID"));
                                    svc.put("packageId", packageMap.get("PACKAGE_ID"));
                                    svc.put("activityStarTime", actMonthFirstDay);
                                    svc.put("activityTime", actMonthLasttDay);
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

                // Map actProParam = new HashMap();
                // actProParam.put("PRODUCT_ID", newActPlanId);
                // List<Map> actProductInfo = dao.queryActProductInfo(actProParam);
                // if (actProductInfo == null || actProductInfo.size() == 0) {
                // throw new EcAopServerBizException("9999", "在产品表或者产品属性表中未查询到产品ID【" + newActPlanId + "】的产品信息");
                // }
                strProductMode = String.valueOf(productInfoResult.get(0).get("PRODUCT_MODE"));
                strBrandCode = String.valueOf(productInfoResult.get(0).get("BRAND_CODE"));
                strProductTypeCode = String.valueOf(productInfoResult.get(0).get("PRODUCT_TYPE_CODE"));

                productTpye.put("productMode", strProductMode);
                productTpye.put("productId", newActPlanId);
                productTpye.put("productTypeCode", strProductTypeCode);
                // 算出活动的开始结束时间，预提交下发
                productTpye.put("activityStarTime", actMonthFirstDay);
                productTpye.put("activityTime", actMonthLasttDay);

                product.put("brandCode", strBrandCode);
                product.put("productId", newActPlanId);
                product.put("productMode", strProductMode);
                // 算出活动的开始结束时间，预提交下发
                product.put("activityStarTime", actMonthFirstDay);
                product.put("activityTime", actMonthLasttDay);

                productTypeList.add(productTpye);
                productList.add(product);

            }
        }
        // 记录默认包年资费
        List<Map> defaultEmelemtts = new ArrayList<Map>();
        for (int i = 0; i < productInfo.size(); i++) {
            List<Map> packageElement = (List<Map>) productInfo.get(i).get("packageElement");
            String productMode = String.valueOf(productInfo.get(i).get("productMode"));
            String firstMonBillMode = String.valueOf(productInfo.get(i).get("firstMonBillMode"));
            String productId = String.valueOf(productInfo.get(i).get("productId"));
            String isIpOrInterTv = "";
            if (null != productInfo.get(i).get("isIpOrInterTv")) {
                isIpOrInterTv = (String) productInfo.get(i).get("isIpOrInterTv");
            }

            System.out.println(apptx + "产品:" + productId + "处理,productMode:" + productMode + ",isIpOrInterTv:"
                    + isIpOrInterTv);
            if ("0".equals(productMode)) {
                System.out.println("===========主产品产品处理");
                Map productTpye = new HashMap();
                Map product = new HashMap();

                // String commodityId = productId;
                String strBrandCode = "";
                String strProductTypeCode = "";
                String strProductMode = "";

                Map proparam = new HashMap();
                proparam.put("PROVINCE_CODE", provinceCode);
                proparam.put("PRODUCT_ID", productId);
                proparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                proparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                proparam.put("PRODUCT_MODE", "00");
                List<Map> productInfoResult = n25Dao.qryDefaultPackageElement(proparam);
                System.out.println(apptx + "产品:" + productId + "查询到的内容为:" + productInfoResult);
                if (productInfoResult == null || productInfoResult.size() == 0) {
                    // throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + productId + "】的产品信息");
                    // 未查询到产品的默认资费或服务,不报错,去TD_B_PRODUCT表查询,产品不存在就抛错,存在继续执行 by wangmc 20170321
                    List<Map> productInfoList = n25Dao.qryProductInfoByProductTable(proparam);
                    if (!IsEmptyUtils.isEmpty(productInfoList)) {
                        productId = String.valueOf(productInfoList.get(0).get("PRODUCT_ID"));
                        strProductMode = (String) productInfoList.get(0).get("PRODUCT_MODE");
                        // SQL中新增了查询BRAND_CODE字段,需要上传生产 by wangmc 20171222
                        strBrandCode = String.valueOf(productInfoList.get(0).get("BRAND_CODE"));
                        strProductTypeCode = String.valueOf(productInfoList.get(0).get("PRODUCT_TYPE_CODE"));
                    }
                    else {
                        throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + productId + "】的产品信息");
                    }
                }
                else {
                    productId = String.valueOf(productInfoResult.get(0).get("PRODUCT_ID"));
                    strProductMode = String.valueOf(productInfoResult.get(0).get("PRODUCT_MODE"));
                    strBrandCode = String.valueOf(productInfoResult.get(0).get("BRAND_CODE"));
                    strProductTypeCode = String.valueOf(productInfoResult.get(0).get("PRODUCT_TYPE_CODE"));
                }
                if ("mvoa".equals(methodCode) || "mofc".equals(methodCode) || "234s".equals(methodCode)) {
                    productInfoResult = new NewProductUtils().chooseSpeed(productInfoResult, msg.get("phoneSpeedLevel")
                            + "");
                }
                if (productId.equals("-1")) {
                    productId = String.valueOf(productInfoResult.get(1).get("PRODUCT_ID"));
                }

                // 需要按偏移处理时间的资费 by wangmc 20180903
                NewProductUtils utils = new NewProductUtils();
                List<String> specialDiscnt = n25Dao.querySpealDealDiscnt(MapUtils.asMap("SELECT_FLAG",
                        "SPACIL_DISCNT_MIXOPEN_FIX%"));
                for (int j = 0; j < productInfoResult.size(); j++) {

                    if ("D".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                        Map dis = new HashMap();
                        Map defaultEmelemtt = new HashMap();
                        dis.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                        dis.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                        dis.put("discntCode", productInfoResult.get(j).get("ELEMENT_ID"));
                        dis.put("productMode", productInfoResult.get(j).get("PRODUCT_MODE"));
                        defaultEmelemtt.put("defaultEmelemtt",
                                String.valueOf(productInfoResult.get(j).get("ELEMENT_ID")));
                        defaultEmelemtts.add(defaultEmelemtt);
                        // 根据配置判断是否处理主资费的开始时间和结束时间by wangmc 20180903
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
                        peparam.put("PRODUCT_ID", productId);
                        peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                        peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                        peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                        List<Map> packageElementInfo = n25Dao.queryPackageElementInfo(peparam);
                        System.out.println("走进来了么？？？？？？？" + packageElementInfo);
                        if (packageElementInfo != null && packageElementInfo.size() > 0) {
                            for (Map packageMap : packageElementInfo) {
                                if ("D".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    String elementId = String.valueOf(packageMap.get("ELEMENT_ID"));
                                    Map dis = new HashMap();
                                    dis.put("productId", packageMap.get("PRODUCT_ID"));
                                    dis.put("packageId", packageMap.get("PACKAGE_ID"));
                                    dis.put("discntCode", elementId);
                                    dis.put("productMode", packageMap.get("PRODUCT_MODE"));
                                    // 根据配置判断是否处理主资费的开始时间和结束时间by wangmc 20180903
                                    if (utils.dealMainProDiscntDate(dis, specialDiscnt, productId, "N25")) {
                                        continue;
                                    }
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
                                    spItemParam.put("PRODUCT_ID", productId);
                                    spItemParam.put("PROVINCE_CODE", provinceCode);
                                    spItemParam.put("SPSERVICEID", packageMap.get("ELEMENT_ID"));
                                    List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                                    if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                        throw new EcAopServerBizException("9999", "在SP表中未查询到【"
                                                + packageMap.get("ELEMENT_ID") + "】的元素属性信息");
                                    }
                                    for (int j = 0; j < spItemInfoResult.size(); j++) {
                                        Map spItemInfo = spItemInfoResult.get(j);
                                        spId = (String) spItemInfo.get("SP_ID");
                                        partyId = (String) spItemInfo.get("PARTY_ID");
                                        spProductId = (String) spItemInfo.get("SP_PRODUCT_ID");
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
                addproparam.put("PRODUCT_ID", productId);
                addproparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                addproparam.put("PRODUCT_MODE", "01");
                addproparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                List<Map> addproductInfoResult = new ArrayList<Map>();
                if (!StringUtils.isEmpty(isIpOrInterTv)) {
                    addproductInfoResult = n25Dao.queryIptvOrIntertvProductInfo(addproparam);
                }
                else {
                    addproductInfoResult = n25Dao.qryDefaultPackageElement(addproparam);
                }
                if (addproductInfoResult == null || addproductInfoResult.size() == 0) {
                    List<Map> productInfoList = n25Dao.qryProductInfoByProductTable(addproparam);
                    if (!IsEmptyUtils.isEmpty(productInfoList)) {
                        addProductId = String.valueOf(productInfoList.get(0).get("PRODUCT_ID"));
                        strProductMode = (String) productInfoList.get(0).get("PRODUCT_MODE");
                        strBrandCode = String.valueOf(productInfoList.get(0).get("BRAND_CODE"));
                        // FIXME 放入PRODUCT_TYPE_CODE字段,防止下发时没有值 by wangmc 20180630
                        strProductTypeCode = String.valueOf(productInfoList.get(0).get("PRODUCT_TYPE_CODE"));
                    }
                    else {
                        throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + addProductId + "】的产品信息");
                    }
                }
                else {
                    strProductMode = String.valueOf(addproductInfoResult.get(0).get("PRODUCT_MODE"));
                    strBrandCode = String.valueOf(addproductInfoResult.get(0).get("BRAND_CODE"));
                    strProductTypeCode = String.valueOf(addproductInfoResult.get(0).get("PRODUCT_TYPE_CODE"));
                    addProductId = String.valueOf(addproductInfoResult.get(0).get("PRODUCT_ID"));
                }
                System.out.println("test123" + strProductMode + "+++" + strBrandCode + "+++" + strProductTypeCode);
                if ("mvoa".equals(methodCode) || "mofc".equals(methodCode)) {
                    addproductInfoResult = new NewProductUtils().chooseSpeed(addproductInfoResult,
                            msg.get("phoneSpeedLevel") + "");
                }

                isFinalCode = NewProductUtils.getEndDateType(addProductId);
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
                            sp.put("productMode", addproductInfoResult.get(j).get("PRODUCT_MODE"));
                            sp.put("activityTime", addProMonthLasttDay);
                            sp.put("activityStarTime", addProMonthFirstDay);
                            spList.add(sp);
                        }

                    }
                    System.out.println("svcList1230" + svcList);
                }
                // Map additparam = new HashMap();
                // additparam.put("PROVINCE_CODE", provinceCode);
                // additparam.put("PID", addProductId);
                // additparam.put("COMMODITY_ID", commodityId);
                // additparam.put("PTYPE", "U");
                // List<Map> addProductItemInfoResult = dao.queryProductItemInfo(additparam);
                // if (addProductItemInfoResult == null || addProductItemInfoResult.size() == 0) {
                // throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品属性信息");
                // }

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
                System.out.println("testproductTpye" + productTpye);
                System.out.println("test123123product" + product);
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
                                    spItemParam.put("PROVINCE_CODE", provinceCode);
                                    spItemParam.put("SPSERVICEID", packageMap.get("ELEMENT_ID"));
                                    List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                                    if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                        throw new EcAopServerBizException("9999", "在SP产品表中未查询到【"
                                                + packageMap.get("ELEMENT_ID") + "】的元素属性信息");
                                    }

                                    for (int j = 0; j < spItemInfoResult.size(); j++) {
                                        Map spItemInfo = spItemInfoResult.get(j);
                                        spId = (String) spItemInfo.get("SP_ID");
                                        partyId = (String) spItemInfo.get("PARTY_ID");
                                        spProductId = (String) spItemInfo.get("SP_PRODUCT_ID");
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
                System.out.println("svcList" + svcList);
            }
        }
        // dealRepeat(discntList);
        // dealRepeat(svcList);
        // dealRepeat(spList);
        // dealRepeat(productTypeList);
        // dealRepeat(productList);

        // 使用新的去重方法 by wangmc 20170410
        discntList = NewProductUtils.newDealRepeat(discntList, "discntList");
        svcList = NewProductUtils.newDealRepeat(svcList, "svcList");
        spList = NewProductUtils.newDealRepeat(spList, "spList");
        productTypeList = NewProductUtils.newDealRepeat(productTypeList, "productTypeList");
        productList = NewProductUtils.newDealRepeat(productList, "productList");
        msg.put("discntList", discntList);
        System.out.println("ssptest3discntList" + discntList);

        msg.put("svcList", svcList);
        msg.put("spList", spList);
        msg.put("productTypeList", productTypeList);
        msg.put("productList", productList);
        // 增加活动结束时间
        // msg.put("activityTime", monthLasttDay);
        System.out.println(msg);

        OpenApply4GProcessor openApplyPro = new OpenApply4GProcessor();
        ext.put("defaultEmelemtts", defaultEmelemtts);
        ext.put("tradeProductType", openApplyPro.preProductTpyeListData(msg));
        System.out.println(ext.get("tradeProductType") + "12132132321");
        ext.put("tradeProduct", openApplyPro.preProductData(msg));
        ext.put("tradeDiscnt", openApplyPro.preDiscntData(msg));
        ext.put("tradeSvc", openApplyPro.preTradeSvcData(msg));
        ext.put("tradeSp", openApplyPro.preTradeSpData(msg));
        return ext;
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
     * 根据日期获取cycleId（类似201501这种东东）
     * @param acceptDate19 19位日期 'YYYY-MM-DD HH24:MI:SS'
     * @return
     */
    public static String getCycleId(String acceptDate19) {
        Map inMap = new HashMap();
        inMap.put("date", acceptDate19);
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        return dao.getCycId(inMap);
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

    public static void dealItemValue(String ruleCode, String attrTypeCode, String attrCode, String attrValue,
            Map appendMap, Map baseObject, Map extObject) throws Exception {
        if (ruleCode.equals("0")) {
            addItemTab(attrTypeCode, attrCode, (String) baseObject.get("userId"), attrValue, extObject);
        }
        else if (ruleCode.equals("1")) {
            boolean flag = false;
            Map<String, Object> tempMap = copyMap(appendMap);
            // 取key_value
            if (attrValue.contains("|") && attrValue.contains(",") && !attrValue.endsWith(",")) {
                String[] str = attrValue.split("\\|")[0].split("/");
                String key = attrValue.split("\\|")[1].split(",")[0];
                String valueId = attrValue.split("\\|")[1].split(",")[1];
                for (int i = 0; i < str.length; i++) {
                    Object obj = tempMap.get(str[i]);
                    if (obj instanceof List) {
                        if (i != str.length - 2) {
                            tempMap = (Map) ((List) obj).get(0);
                        }
                        else {
                            for (int j = 0; j < ((List) obj).size(); j++) {
                                Map kvMap = (Map) ((List) obj).get(j);
                                if (kvMap.get(str[i + 1]).equals(key)) {
                                    flag = true;
                                    attrValue = kvMap.get(valueId) != null ? (String) kvMap.get(valueId) : "";
                                    break;
                                }
                            }
                        }
                    }
                    else if (obj instanceof Map) {
                        tempMap = (Map) obj;
                    }
                }
            }
            else {
                String[] str = attrValue.split("/");
                for (int i = 0; i < str.length; i++) {
                    Object obj = tempMap.get(str[i]);
                    if (obj instanceof List) {
                        tempMap = (Map) ((List) obj).get(0);
                    }
                    else if (obj instanceof Map) {
                        tempMap = (Map) obj;
                    }
                    else if (obj instanceof String) {
                        flag = true;
                        attrValue = (String) obj;
                    }
                }
            }
            if (flag) {
                addItemTab(attrTypeCode, attrCode, (String) baseObject.get("userId"), attrValue, extObject);
            }

        }

    }

    public static void addItemTab(String attrTypeCode, String attrCode, String itemId, String attrValue, Map extObject)
            throws Exception {
        Map item = new HashMap();
        if (attrTypeCode.equals("I")) {
            item.put("attrCode", attrCode);
            item.put("attrValue", attrValue);
            item.put("xDatatype", "NULL");
            if (extObject.containsKey("itemStartDate")
                    && StringUtils.isNotBlank((String) extObject.get("itemStartDate"))) {
                item.put("startDate", extObject.get("itemStartDate"));
            }
            if (extObject.containsKey("itemEndDate") && StringUtils.isNotBlank((String) extObject.get("itemEndDate"))) {
                item.put("endDate", extObject.get("itemEndDate"));
            }

            addTab("tradeItem", extObject, item);
        }
        else {
            item.put("attrTypeCode", attrTypeCode);
            item.put("attrCode", attrCode);
            item.put("itemId", itemId);
            item.put("attrValue", attrValue);
            item.put("xDatatype", "NULL");
            if (extObject.containsKey("itemStartDate")
                    && StringUtils.isNotBlank((String) extObject.get("itemStartDate"))) {
                item.put("startDate", extObject.get("itemStartDate"));
            }
            if (extObject.containsKey("itemEndDate") && StringUtils.isNotBlank((String) extObject.get("itemEndDate"))) {
                item.put("endDate", extObject.get("itemEndDate"));
            }
            addTab("tradeSubItem", extObject, item);
        }
    }

    public static List<Map> getAppendParam(Map baseObject) {
        Map param = new HashMap();
        param.put("TRADE_TYPE_CODE", baseObject.get("tradeTypeCode"));
        param.put("NET_TYPE_CODE", baseObject.get("netTypeCode"));// 默认ZZ
        param.put("BRAND_CODE", baseObject.get("brandCode"));// 默认ZZZZ
        param.put("PRODUCT_ID", baseObject.get("productId"));// 默认-1
        param.put("EPARCHY_CODE", baseObject.get("eparchyCode"));// 默认ZZZZ
        param.put("PROVINCE_CODE", baseObject.get("provinceCode"));// 默认ZZZZ
        // param.put("SUBSYS_CODE", baseObject.get("subSysCode"));//SQL语句中没这个条件
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        return dao.queryAppendParam(param);
    }

    public static Map addTab(String tabName, Map extObject, Map item) {
        List<Map> itemList = new ArrayList<Map>();
        if (extObject.containsKey(tabName)) {
            if (extObject.get(tabName) instanceof Map) {
                itemList.add((Map) extObject.get(tabName));
            }
            else {
                itemList = (List<Map>) extObject.get(tabName);
            }
        }
        Map itemMap = Maps.newHashMap();
        itemMap.put("item", item);
        itemList.add(item);
        extObject.put(tabName, itemList);
        return extObject;
    }

    private static Map<String, Object> copyMap(Map map) {
        Map<String, Object> newMap = new HashMap<String, Object>();
        for (Iterator iter = map.keySet().iterator(); iter.hasNext();) {
            String key = (String) iter.next();
            Object value = map.get(key);
            newMap.put(key, value);
        }
        return newMap;
    }

    /**
     * 处理非产品默认属性
     * @param map 封装了外围请求的map参数
     * @param busiMap 业务相关参数:TRADE_TYPE_CODE,NET_TYPE_CODE,BRAND_CODE,PRODUCT_ID,EPARCHY_CODE,PROVINCE_CODE,USER_ID,
     *            SUBSYS_CODE
     * @param extObject
     * @throws Exception
     */
    public static void dealItemInfo(Map appendMap, Map baseObject, Map extObject) throws Exception {

        List result = getAppendParam(baseObject);
        System.out.println("111111111111111111111111111" + result);
        for (int i = 0; i < result.size(); i++) {
            Map resultMap = (Map) result.get(i);
            String ruleCode = "";
            String attrTypeCode = "";
            String attrCode = "";
            String attrValue = "";
            if (resultMap.get("RULE_CODE") != null) {
                ruleCode = (String) resultMap.get("RULE_CODE");
            }
            if (resultMap.get("ATTR_TYPE_CODE") != null) {
                attrTypeCode = (String) resultMap.get("ATTR_TYPE_CODE");
            }
            if (resultMap.get("ATTR_CODE") != null) {
                attrCode = (String) resultMap.get("ATTR_CODE");
            }
            if (resultMap.get("ATTR_VALUE") != null) {
                attrValue = (String) resultMap.get("ATTR_VALUE");
            }
            if (!ruleCode.equals("4") && !ruleCode.equals("")) {
                dealItemValue(ruleCode, attrTypeCode, attrCode, attrValue, appendMap, baseObject, extObject);
            }
        }

    }

    /**
     * 获取员工信息
     * @param exchange
     * @return
     * @throws Exception
     */
    public static Map getStaffInfo(Exchange exchange) throws Exception {
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.param.mapping.sfck", exchange);
        CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
        lan.xml2Json4ONS("ecaop.template.3g.staffcheck", exchange);
        Map retStaffinfo = exchange.getOut().getBody(Map.class);
        return retStaffinfo;
    }

    /**
     * 循环插台账 tf_b_trade_sub_item在终端的时候使用
     * @param itemMap
     * @param ItemId
     * @param extobject
     * @throws Exception
     */
    public static void dealTerminalItem(Map itemMap, String ItemId, Map extobject) throws Exception {
        Iterator<String> it = itemMap.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            String value = (String) itemMap.get(key);
            Map userSubItem = new HashMap();
            userSubItem.put("attrTypeCode", "6");
            userSubItem.put("attrCode", key);
            userSubItem.put("itemId", ItemId);
            userSubItem.put("attrValue", value);
            userSubItem.put("xDatatype", "NULL");
            TradeManagerUtils.addTab("tradeSubItem", extobject, userSubItem);
        }
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
        String seqId = dao.genSequence(inMap);
        if (StringUtils.isEmpty(seqId)) {
            inMap.put("EPARCHY_CODE", "0018");
            inMap.put("SEQUENCE", seqName);
            seqId = dao.genSequence(inMap);

        }
        return seqId;
    }

    /**
     * @param seqName
     * @param province
     * @return
     */
    public static String get3GeSequence(String seqName, String province) {
        Map inMap = Maps.newHashMap();
        inMap.put("PROVINCE_CODE", province);
        inMap.put("SEQUENCE", seqName);
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        return dao.query3GeSeqId(inMap).get(0).toString();
    }

    /**
     * 转换信用等级
     * @param creditClass
     * @return
     */
    public static String decodeCreditClass(String creditClass) {
        // Auto-generated method stub
        String credit = "67";
        if ("A".equals(creditClass))
            credit = "65";
        else if ("B".equals(creditClass))
            credit = "66";
        else if ("C".equals(creditClass))
            credit = "67";

        return credit;
    }

    /**
     * 获取LTE速率
     * @param dao
     * @param eparchyCode
     * @return
     */
    public static String getSpeedParam(String eparchyCode) {
        String speed = "42";
        Map param = Maps.newHashMap();
        param.put("EPARCHY_CODE", eparchyCode);
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        List<Map> resultList = dao.selectEparchyCodeOnly(param);
        if (!CollectionUtils.isEmpty(resultList)) {
            speed = "100";
        }
        return speed;
    }

    /**
     * 地市编码3位-->4位
     * @param province
     * @param provinceCode
     * @param inEparchy
     * @return
     */
    public static String getExEparchyCode(String province, String provinceCode, String inEparchy) {
        Map inMap = Maps.newHashMap();
        // inMap.put("SUBSYS_CODE", "CSM"); //SQL已默认
        // inMap.put("PARAM_ATTR", 8540); //SQL已默认
        inMap.put("province", province);
        inMap.put("city", inEparchy);
        inMap.put("provinceCode", provinceCode);
        // inMap.put("EPARCHY_CODE", inEparchy); //SQL中默认ZZZZ
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        List<Map> result = dao.queryEparchyCode(inMap);
        if (!CollectionUtils.isEmpty(result)) {
            Map m = result.get(0);
            return (String) m.get("PARAM_CODE");
        }
        return "";
    }

    /**
     * 取出Map中对应key的值，防空指针异常
     * @param map
     * @param key
     * @return
     */
    public static String getStringValue(Map map, String key) {
        String result = "";
        if (StringUtils.isBlank(key)) {
            return result;
        }
        else if (null == map.get(key) || StringUtils.isBlank((String) map.get(key))) {
            return result;
        }
        else {
            return (String) map.get(key);
        }
    }

    /**
     * 获取TD_S_COMMPARA_SOP表数据
     * @param subSysCode
     * @param paramAttr
     * @param paraCode1
     * @param paraCode2
     * @param provinceCode
     * @param eparchyCode
     * @return
     */
    public static List<Map> getCommParamSop(String subSysCode, String paramAttr, String paraCode1, String paraCode2,
            String provinceCode, String eparchyCode) {
        Map<String, Object> param = new HashMap<String, Object>();
        param.put("SUBSYS_CODE", subSysCode);
        param.put("PARAM_ATTR", paramAttr);
        param.put("PARA_CODE1", paraCode1);
        param.put("PARA_CODE2", paraCode2);
        param.put("PROVINCE_CODE", provinceCode);
        param.put("EPARCHY_CODE", eparchyCode);
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        return dao.queryCommParaSop(param);
    }

    /**
     * 调用CB客户资料校验接口
     * @param exchange
     * @return
     * @throws Exception
     */
    public static Map custCheck4G(Exchange exchange) throws Exception {
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.cust.cbss.check.ParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.CustSer");
        Xml2Json4FbsNoErrorMappingProcessor proc = new Xml2Json4FbsNoErrorMappingProcessor();
        proc.applyParams(new String[] { "ecaop.cust.cbss.check.template" });
        proc.process(exchange);
        return exchange.getOut().getBody(Map.class);
    }

    public static List<Map> chooseSpeed(List<Map> productInfo, String speed) {
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
        for (int m = 0; m < productInfo.size(); m++) {
            if (kick1st.equals(String.valueOf(productInfo.get(m).get("ELEMENT_ID")))
                    || kick2nd.equals(String.valueOf(productInfo.get(m).get("ELEMENT_ID")))) {
                newProductInfo.add(productInfo.get(m));
            }
        }
        productInfo.removeAll(newProductInfo);
        return productInfo;
    }

    public static List<Map> dealProduction(List<Map> productInfo, String elementId) {
        List<Map> newProductionInfo = new ArrayList<Map>();
        for (int m = 0; m < productInfo.size(); m++) {
            if (elementId.equals(String.valueOf(productInfo.get(m).get("ELEMENT_ID")))) {
                newProductionInfo.add(productInfo.get(m));
            }
        }
        productInfo.removeAll(newProductionInfo);
        return productInfo;
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
     * 处理附加产品或者活动的失效时间
     * 可以手动传入产品或活动的生失效时间
     */
    public static Map getEffectTime(String actPlanId, String monthFirstDay, String monthLasttDay) {
        String monthLastDay = "";
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        Map actiPeparam = new HashMap();
        actiPeparam.put("PRODUCT_ID", actPlanId);
        List<Map> activityList = n25Dao.queryProductAndPtypeProduct(actiPeparam);
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

    /**
     * 处理附加产品或者活动的失效时间
     * 可以手动传入产品或活动的生失效时间
     * 针对产品必须是次月生效的
     */
    public static Map getEffectTimeNextMouthStart(String actPlanId, String monthLasttDay) throws Exception {
        String monthFirstDay = GetDateUtils.transDate(GetDateUtils.getNextMonthFirstDayFormat(), 19);
        String monthLastDay = "";
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        Map actiPeparam = new HashMap();
        actiPeparam.put("PRODUCT_ID", actPlanId);
        List<Map> activityList = n25Dao.queryProductAndPtypeProduct(actiPeparam);
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
            // 5:自然年';
            String endUnit = String.valueOf(activityListMap.get("END_UNIT"));

            // 如果值为空则进行默认处理
            if (!"null".equals(endEnableTag) && "0".equals(endEnableTag) && !"null".equals(endDate)) {
                monthLasttDay = endDate;
            }
            if (!"null".equals(resActivityper) && !"null".equals(endUnit) && !"null".equals(endEnableTag)
                    && "1".equals(endEnableTag)) {
                monthLastDay = GetDateUtils.getSpecifyDateTime(Integer.parseInt(endEnableTag),
                        Integer.parseInt(endUnit), monthFirstDay, Integer.parseInt(resActivityper));
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

    /**
     * 处理附加产品或者活动的失效时间
     * 可以手动传入产品或活动的生失效时间
     */
    public static Map getEffectTimeForMainPro(String actPlanId, String monthFirstDay, String monthLasttDay) {
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
            String startOffset = "0";// 生效偏移时间

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

    /**
     * 专门处理活动信息,新库
     */
    public static void preActivityInfo(Map msg) {

        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        List<Map> productTypeList = new ArrayList<Map>();
        List<Map> productList = new ArrayList<Map>();
        String monthLasttDay = "20501231235959";
        String monthFirstDay = GetDateUtils.getDate();
        List<Map> discntList = new ArrayList<Map>();
        List<Map> svcList = new ArrayList<Map>();

        Map activityTimeMap = new HashMap();

        String provinceCode = "00" + msg.get("province");
        // 处理活动
        List<Map> activityInfo = (List<Map>) msg.get("activityInfo");
        if (null != activityInfo && activityInfo.size() > 0) {
            for (int i = 0; i < activityInfo.size(); i++) {
                List<Map> packageElement = (List<Map>) activityInfo.get(i).get("packageElement");
                String actPlanId = String.valueOf(activityInfo.get(i).get("actPlanId"));
                Map proparam = new HashMap();
                proparam.put("PROVINCE_CODE", provinceCode);
                proparam.put("PRODUCT_ID", actPlanId);
                String newActPlanId = n25Dao.queryActivityByCommodityId(proparam).toString();
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
                        /* peparam.put("PROVINCE_CODE", provinceCode);
                         * peparam.put("COMMODITY_ID", actPlanId);
                         * peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                         * peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                         * peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                         * List<Map> packageElementInfo = dao.queryPackageElementInfo(peparam); */
                        peparam.put("PROVINCE_CODE", provinceCode);
                        peparam.put("PRODUCT_ID", actPlanId);
                        peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                        peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                        peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                        List<Map> packageElementInfo = n25Dao.queryPackageElementInfo(peparam);
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

                String strBrandCode = "";
                String strProductTypeCode = "";
                String strProductMode = "";

                Map addproparam = new HashMap();
                /* addproparam.put("PROVINCE_CODE", provinceCode);
                 * addproparam.put("COMMODITY_ID", commodityId);
                 * // 原始表查询活动用 productid
                 * addproparam.put("PRODUCT_ID", commodityId);
                 * addproparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                 * addproparam.put("FIRST_MON_BILL_MODE", null);
                 * List<Map> addproductInfoResult = dao.queryAddProductInfo(addproparam); */
                addproparam.put("PROVINCE_CODE", provinceCode);
                addproparam.put("PRODUCT_MODE", "50");
                addproparam.put("PRODUCT_ID", actPlanId);
                addproparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                addproparam.put("FIRST_MON_BILL_MODE", null);
                List<Map> addproductInfoResult = n25Dao.qryDefaultPackageElement(addproparam);
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
                    strProductMode = "50";
                    strBrandCode = (String) addproductInfoResult.get(0).get("BRAND_CODE");
                    strProductTypeCode = (String) addproductInfoResult.get(0).get("PRODUCT_TYPE_CODE");

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
                /* Map actProParam = new HashMap();
                 * actProParam.put("PRODUCT_ID", newActPlanId);
                 * List<Map> actProductInfo = dao.queryActProductInfo(actProParam);
                 * if (actProductInfo == null || actProductInfo.size() == 0) {
                 * throw new EcAopServerBizException("9999", "在产品表或者产品属性表中未查询到产品ID【" + newActPlanId + "】的产品信息");
                 * } */

            }
        }
        msg.put("discntList", discntList);
        msg.put("svcList", svcList);
        msg.put("productList", productList);
        msg.put("productTypeList", productTypeList);
    }

    /**
     * 处理附加产品资费的生失效时间 修改为从新库获取 by wangmc 20170302
     * 可以手动传入产品或活动的生失效时间
     */
    public static Map getDiscntEffectTime(String discntId, String monthFirstDay, String monthLasttDay) {
        String monthLastDay = "";
        // LanOpenApp4GDao dao = new LanOpenApp4GDao();
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        Map actiPeparam = new HashMap();
        actiPeparam.put("DISCNT_CODE", discntId);
        // List<Map> activityList = dao.queryDiscntDate(actiPeparam);
        List<Map> activityList = n25Dao.queryDiscntData(actiPeparam);
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
     * 处理主产品资费的生失效时间 by wangmc 20170223
     * 可以手动传入产品或活动的生失效时间
     */
    public static Map getMainProDiscntEffectTime(String discntId, String monthFirstDay, String monthLasttDay) {
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
            // 如果值为空则进行默认处理
            if (!"null".equals(endEnableTag) && "0".equals(endEnableTag) && !"null".equals(endDate)) {
                // 先判断该资费是否已生效 by wangmc 20170223
                if (GetDateUtils.getDate().compareTo(endDate) > 0) {
                    actiPeparam.put("discntEndTag", "true");// 已失效
                    return actiPeparam;
                }
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
            actiPeparam.put("discntEndTag", "false");// 未失效
            return actiPeparam;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "获取资费信息生失效时间失败，资费是：" + discntId);
        }

    }

    /**
     * 处理附加产品资费的生失效时间
     * 可以手动传入产品或活动的生失效时间
     * 针对次月生效的资费
     */
    public static Map getDiscntEffectTimeNextMouthStart(String discntId, String monthLasttDay) throws Exception {
        String monthFirstDay = GetDateUtils.transDate(GetDateUtils.getNextMonthFirstDayFormat(), 19);
        String monthLastDay = "";
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        Map actiPeparam = new HashMap();
        actiPeparam.put("DISCNT_CODE", discntId);
        List<Map> activityList = n25Dao.queryDiscntData(actiPeparam);
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
            // 如果值为空则进行默认处理
            if (!"null".equals(endEnableTag) && "0".equals(endEnableTag) && !"null".equals(endDate)) {
                monthLasttDay = endDate;
            }
            if (!"null".equals(resActivityper) && !"null".equals(endUnit) && !"null".equals(endEnableTag)
                    && "1".equals(endEnableTag)) {
                monthLastDay = GetDateUtils.getSpecifyDateTime(Integer.parseInt(endEnableTag),
                        Integer.parseInt(endUnit), monthFirstDay, Integer.parseInt(resActivityper));
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
     * 处理附加产品资费的生失效时间
     * 可以手动传入产品或活动的生失效时间
     */
    public static Map getDiscntEffectTimeForMainPro(String discntId, String monthFirstDay, String monthLasttDay) {
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
            String startOffset = "0";// 生效偏移时间
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
     * 根据资费信息获取产品信息
     */
    public static Map preProductInfoByElementInfo(Map inputMap) {
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        List<Map> productInfoResult = dao.queryProductInfoByElementInfo(inputMap);
        System.out.println("执行结果:" + productInfoResult.size() + "内容：" + productInfoResult);
        if (productInfoResult == null || productInfoResult.size() == 0) {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到元素ID【" + inputMap.get("elementId") + "】的产品信息");
        }
        Map dis = new HashMap();
        dis.put("productId", productInfoResult.get(0).get("PRODUCT_ID"));
        dis.put("packageId", productInfoResult.get(0).get("PACKAGE_ID"));
        dis.put("discntCode", productInfoResult.get(0).get("ELEMENT_ID"));

        return dis;
    }

    /**
     * 接入方式转换
     * PROVINCE_CODE、NET_TYPE_CODE、ACCESS_TYPE、CB_ACCESS_TYPE请传进来
     */
    public static Map checkAccessCode(Map inMap) throws Exception {
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        String accessMode = (String) inMap.get("ACCESS_TYPE");
        String cbssAccessMode = (String) inMap.get("CB_ACCESS_TYPE");
        Map accesMap = new HashMap();
        if (StringUtils.isEmpty(accessMode) && StringUtils.isEmpty(cbssAccessMode)) {
            throw new EcAopServerBizException("9999", "请添加接入方式编码");
        }
        else if (StringUtils.isNotEmpty(accessMode) && StringUtils.isNotEmpty(cbssAccessMode)) {
            List resultSet = dao.queryCbAccessTypeByPro(inMap);
            if (resultSet != null && resultSet.size() > 0) {
                String cbAccessType = (String) resultSet.get(0);
                if (!cbssAccessMode.equals(cbAccessType)) {
                    throw new EcAopServerBizException("9999", "accessMode与cbssAccessMode对应关系错误，请检查");
                }
            }
            else {
                throw new EcAopServerBizException("9999", "无此accessMode对应接入方式编码，请检查");
            }
        }
        else if (StringUtils.isNotEmpty(accessMode)) {
            List resultSet = dao.queryCbAccessTypeByPro(inMap);
            if (resultSet != null && resultSet.size() > 0) {
                cbssAccessMode = (String) resultSet.get(0);
            }
            else {
                throw new EcAopServerBizException("9999", "无此accessMode对应cbssAccessMode接入方式编码，请检查");
            }
        }
        else if (StringUtils.isNotEmpty(cbssAccessMode)) {
            List resultSet = dao.queryCbAccessType(inMap);
            if (resultSet != null && resultSet.size() > 0) {
                if (resultSet.size() > 1) {
                    accessMode = "B00";
                }
                else {
                    accessMode = (String) resultSet.get(0);
                }
            }
            else {
                throw new EcAopServerBizException("9999", "无此cbssAccessMode对应accessMode接入方式编码，请检查");
            }
        }
        accesMap.put("accessMode", accessMode);
        accesMap.put("cbssAccessMode", cbssAccessMode);
        return accesMap;
    }

    /**
     * 该方法用于对宽带或固话终端进行转码
     * 转化为cb对应码
     */
    public static String changeTerminalCode(String machineType) {
        if ("1".equals(machineType)) {
            machineType = "A001";
        }
        else if ("2".equals(machineType)) {
            machineType = "A002";
        }
        else if ("3".equals(machineType)) {
            machineType = "A003";
        }
        else if ("4".equals(machineType)) {
            machineType = "A006";
        }
        else if ("5".equals(machineType)) {
            machineType = "A009";
        }
        return machineType;
    }

    /**
     * 该方法用于对宽带或固话终端提供方式进行转码
     * 目前合约销售按照终端销售类型处理
     */
    public static String changeMachineProvide(String machineProvide) {
        if ("1".equals(machineProvide) || "4".equals(machineProvide) || "3".equals(machineProvide)) {
            machineProvide = "2";
        }
        else if ("2".equals(machineProvide)) {
            machineProvide = "0";
        }
        else if ("5".equals(machineProvide) || "7".equals(machineProvide)) {
            machineProvide = "7";
        }
        else if ("6".equals(machineProvide) || "8".equals(machineProvide)) {
            machineProvide = "3";
        }
        else if ("9".equals(machineProvide)) {
            machineProvide = "6";
        }
        else if ("10".equals(machineProvide)) {
            machineProvide = "5";
        }
        else if ("11".equals(machineProvide)) {
            machineProvide = "1";
        }

        return machineProvide;
    }

    /**
     * 处理附加产品资费的生失效时间 zzc 20170323
     * 可以手动传入产品或活动的生失效时间
     */
    public static Map getDiscntEffectTimeFor3G(String discntId, String monthFirstDay, String monthLasttDay) {
        System.out.println("===zzc0323");
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
            String startOffset = "0";// 生效偏移时间
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
            monthFirstDay = GetDateUtils.transDate(monthFirstDay, 14);
            actiPeparam.put("monthLasttDay", monthLasttDay);
            actiPeparam.put("monthFirstDay", monthFirstDay);
            return actiPeparam;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "获取资费信息生失效时间失败，资费是：" + discntId);
        }

    }
}
