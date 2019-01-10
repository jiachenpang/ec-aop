package com.ailk.ecaop.biz.res;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.AopHandler;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;
import org.n3r.ecaop.core.processor.WsCall;

import com.ailk.ecaop.common.processor.Xml2Json4FbsMappingProcessor;
import com.ailk.ecaop.dao.product.lan.LanProductInfoQuery;

@EcRocTag("CheckLanRes")
public class CheckLanResProcessor extends BaseAopProcessor implements ParamsAppliable {

    WsCall call = new WsCall();
    Xml2Json4FbsMappingProcessor resx2j = new Xml2Json4FbsMappingProcessor();
    ParametersMappingProcessor pmp = new ParametersMappingProcessor();

    LanProductInfoQuery prdDao = new LanProductInfoQuery();

    @Override
    public void process(Exchange exchange) throws Exception {

        Map inParam = (Map) exchange.getProperty("resCheckInParam");
        Map msg = (Map) inParam.get("msg");

        Map resCheckOut = exchange.getOut().getBody(Map.class);
        String isAdd = String.valueOf(resCheckOut.get("isAdd"));

        Map retMap = new HashMap();
        retMap.put("isAdd", isAdd);

        retMap.put("arrearageFlag", 0);

        // 可单装时, 不需要做客户资料校验, 不需要校验接入方式
        if ("2".equals(isAdd)) {
            retMap.put("addressType", "");
            exchange.getOut().setBody(retMap);
            return;
        }

        getRetMap(resCheckOut, retMap);

        // 根据产品校验接入方式
        checkPrd(msg, retMap);

        // 省份返回接入方式编码
        exchange.getIn().setBody(inParam);
        // 调用xml to json
        String[] custParamKey = { "ecaop.masb.adnu.cuck.processors" };
        AopHandler handler = new AopHandler();
        handler.applyParams(custParamKey);
        handler.process(exchange);

        if (!"200".equals(String.valueOf(exchange.getProperty(Exchange.HTTP_STATUSCODE)))) {
            return;
        }

        Map custMap = exchange.getOut().getBody(Map.class);

        custInfoDeal(retMap, custMap);

        exchange.getOut().setBody(retMap);
    }

    private void checkPrd(Map inMsg, Map retMap) {

        // 当商城没有传入接入方式的时候并且传入产品编码的时候
        inMsg.put("addressType", retMap.get("addressType"));
        List<Map> productList = prdDao.queryPrdAccMod(inMsg);

        if (productList == null || productList.size() == 0) {
            throw new EcAopServerBizException("0005", "没有查询到符合省份返回接入方式的商品配置");
        }

        retMap.put("productCode", productList.get(0).get("COMMODITY_ID"));

    }

    private Map getRetMap(Map inMap, Map retMap) {

        String installAddr = String.valueOf(inMap.get("installAddress"));
        if (StringUtils.isEmpty(installAddr)) {
            throw new EcAopServerBizException("0008", "可加装时省份返回装机地址为空");
        }

        Map<String, List> brand = (Map<String, List>) inMap.get("brandList");

        if (brand == null) {
            throw new EcAopServerBizException("0004", "省份返回接入方式列表为空");
        }

        List<Map> accessList = brand.get("accessList");

        if (accessList == null) {
            throw new EcAopServerBizException("0004", "省份返回接入方式列表为空");
        }

        if (accessList.size() > 1) {
            throw new EcAopServerBizException("0004", "省份返回接入方式列表数据异常");
        }

        Map<String, String> accessMap = accessList.get(0);

        retMap.put("exchCode", inMap.get("exchCode"));
        retMap.put("installAddress", installAddr);
        retMap.put("serviceCode", inMap.get("serviceCode"));
        retMap.put("addressCode", inMap.get("addressCode"));
        retMap.put("addressName", inMap.get("addressName"));

        retMap.put("addressType", accessMap.get("accessType"));
        return retMap;
    }

    private String checkCust(String tem, String msg) {

        if (StringUtils.isEmpty(tem)) {
            throw new EcAopServerBizException("0007", "省份返回:" + msg + "为空.");
        }
        return tem;
    }

    private void custInfoDeal(Map retMap, Map custMap) {

        if ("1".equals(custMap.get("blackListFlag"))) {
            throw new EcAopServerBizException("0203", "黑名单用户");
        }
        // 最大用户数
        if ("1".equals(custMap.get("maxUserFlag"))) {
            throw new EcAopServerBizException("0204", "已达到最大用户数");
        }

        retMap.put("certType", checkCust((String) custMap.get("certType"), "证件类型"));
        retMap.put("certNum", checkCust((String) custMap.get("certNum"), "证件号码"));
        retMap.put("customerName", checkCust((String) custMap.get("customerName"), "客户名称"));
        retMap.put("certAddress", checkCust((String) custMap.get("certAdress"), "证件地址"));

        // 欠费用户信息
        if ("1".equals(custMap.get("arrearageFlag"))) {
            retMap.put("arrearageFlag", 1);

            List<Map> arreaList = (List<Map>) custMap.get("arrearageMess");

            if (arreaList == null || arreaList.size() == 0) {
                return;
            }

            for (Map arrMap : arreaList) {
                arrMap.put("serialNumber", arrMap.get("serialNumber"));
                arrMap.remove("arrearageNum");

                int fee = Integer.parseInt((String) arrMap.get("arrearageFee")) / 10;
                arrMap.put("arrearageFee", fee);
            }

            retMap.put("arrearageMess", arreaList);
        }
    }

    @Override
    public void applyParams(String[] params) {

    }

}
