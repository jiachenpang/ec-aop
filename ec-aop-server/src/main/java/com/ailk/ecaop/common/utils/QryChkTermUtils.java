package com.ailk.ecaop.common.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.LanUtils;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class QryChkTermUtils {
    protected final static String ERROR_CODE = "9999";
    private final static String CLASS_NAME = "QryChkTermUtils";

    /**
     * 根据串码去ESS查询终端信息
     * exchange最好用单独的
     * @param  List<Map> activityInfoList
     * @author Lixl  2016-07-21
     *
     */
    public List<Map> qryChkTerm(Exchange exchange, List<Map> activityInfoList)  throws Exception {
        Map msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
        Map tradeMap = new HashMap();
        MapUtils.arrayPut(tradeMap, msg, MagicNumber.COPYARRAY);
        //copy Exchange
        Exchange qryTermExchange = ExchangeUtils.ofCopy(exchange,tradeMap);
        Map qryTermBody = qryTermExchange.getIn().getBody(Map.class);
        Map qryTerMsg =(Map)qryTermBody.get("msg");
        if (null == activityInfoList || 0 == activityInfoList.size()) {
            return new ArrayList<Map>();
            //throw new EcAopServerBizException(ERROR_CODE, "调用" + CLASS_NAME+ "失败：activityInfoList为空！");
        }
        //为调用ESS终端查询接口组织信息
        List<Map> resourcesInfoList = new ArrayList<Map>();
        for (Map activityInfo : activityInfoList) {
            Map resourceInfo = new HashMap();
            Object resourceCode = activityInfo.get("resourcesCode");
            if (null == resourceCode || "".equals(resourceCode)) {
                continue;
            }
            Object resourcesType = activityInfo.get("resourcesType");
            if (null == resourcesType || "".equals(resourcesType)) {
                //默认为03：移动电话
                resourcesType = "03";
            }
            resourceInfo.put("resourcesType",decodeTerminalType(resourcesType.toString()));
            resourceInfo.put("resourcesCode", resourceCode);
            // res.put("isTest", activity.get("isTest"));
            resourceInfo.put("operType", "0");
            resourceInfo.put("useType", "0");
            resourceInfo.put("activeType", "03");
            resourceInfo.put("occupiedFlag", "0");
            resourceInfo.put("isSelf", "2");
            resourcesInfoList.add(resourceInfo);
        }
        if (null == resourcesInfoList || 0 == resourcesInfoList.size()) {
            return new ArrayList<Map>();
        }
        
        qryTerMsg.put("accessType", "01");
        qryTerMsg.put("resourcesInfo", resourcesInfoList);
        qryTermBody.put("msg", qryTerMsg);
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.gdjk.checkres.ParametersMapping", qryTermExchange);
        CallEngine.aopCall(qryTermExchange, "ecaop.comm.conf.url.esshttp.newsub");
        lan.xml2Json4Res("ecaop.gdjk.checkres.template", qryTermExchange);
        Map retMap = qryTermExchange.getOut().getBody(Map.class);
        if (200 != (Integer) qryTermExchange.getProperty(Exchange.HTTP_STATUSCODE)) {
            throw new EcAopServerBizException(retMap.get("code").toString(),retMap.get("detail").toString());
        }
        List<Map> resourcesRsp = (List<Map>) retMap.get("resourcesRsp");
        if (null != resourcesRsp && !resourcesRsp.isEmpty()) {
            for (Map resMap : resourcesRsp) {
                String rscStateCode = resMap.get("rscStateCode").toString();
                if (!"0000".equals(rscStateCode)) {
                    throw new EcAopServerBizException(rscStateCode, resMap.get("rscStateDesc").toString());
                }
                Object cost = resMap.get("cost");
                if (null == cost || "".equals(cost)) {
                    resMap.put("cost", "0");
                }
                Object salePrice = resMap.get("salePrice");
                if (null == salePrice || "".equals(salePrice)) {
                    resMap.put("salePrice", "0");
                }
                Object cardPrice = resMap.get("cardPrice");
                if (null == cardPrice || "".equals(cardPrice)) {
                    resMap.put("cardPrice", "0");
                }
                Object reservaPrice = resMap.get("reservaPrice");
                if (null == reservaPrice || "".equals(reservaPrice)) {
                    resMap.put("reservaPrice", "0");
                }
            }
        }
        return resourcesRsp;
    }

    /**
     * 終端類型轉碼
     * 
     * @param resourcesType
     * @return
     */
    private String decodeTerminalType(String resourcesType) {
        if ("04".equals(resourcesType)) {
            return "02";
        }
        else if ("05".equals(resourcesType)) {
            return "03";
        }
        return "01";
    }
}
