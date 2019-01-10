/**********************************************************************************************************************
 * 说明:此工具类用于存放宽带开户预提交的一些公用方法
 * 方法一:preDate、用于准备数据
 * 方法二:xml2Json、用于xml的转换,适用于全业务
 * 方法三:dealCityCode、用于处理cityCode,此值放在了而形成Info下
 * 方法四:dealDevelopInfo、用于处理发展人信息,用户新和新客户信息都会用到
 * 方法五:createAttrInfo、根据一个key和value生产一个Map,并添加开始结束时间
 **********************************************************************************************************************/
package com.ailk.ecaop.common.utils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.common.processor.Xml2Json1RespMappingProcessor;
import com.ailk.ecaop.common.processor.Xml2Json4CustMappingProcessor;
import com.ailk.ecaop.common.processor.Xml2Json4FbsMappingProcessor;
import com.ailk.ecaop.common.processor.Xml2Json4FbsNoErrorMappingProcessor;
import com.ailk.ecaop.common.processor.Xml2Json4QryCardDataMappingProcessor;
import com.ailk.ecaop.common.processor.Xml2Json4ResMappingProcessor;
import com.ailk.ecaop.common.processor.Xml2JsonMappingProcessor;
import com.ailk.ecaop.common.processor.Xml2JsonNoBodyMappingProcessor;
import com.alibaba.fastjson.JSON;

public class LanUtils {

    private final String endDate = "20491231235959";

    /**
     * Ready to parameters in preparation for the incoming parameters,
     * the necessary parameters of the packet header and packet header.
     * 
     * @param paramPath
     * @param exchange
     */
    public void preData(String paramPath, Exchange exchange) {
        System.out.println("进入LanUtils.preData时间:" + new Date());
        Long startTime = System.currentTimeMillis();
        Map body = exchange.getIn().getBody(Map.class);
        if (body.get("msg") instanceof String) {
            Map msg = JSON.parseObject((String) body.get("msg"));
            body.put("msg", msg);
        }
        ParametersMappingProcessor pmp = new ParametersMappingProcessor();
        String[] parametersMapper = { paramPath };
        pmp.applyParams(parametersMapper);
        pmp.process(exchange);
        System.out.println("退出LanUtils.preData时间:" + new Date() + ";cost:" + (System.currentTimeMillis() - startTime)
                + "ms");
    }

    public void preData(ParametersMappingProcessor pmp, Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        if (body.get("msg") instanceof String) {
            Map msg = JSON.parseObject((String) body.get("msg"));
            body.put("msg", msg);
        }
        pmp.process(exchange);
    }

    /**
     * Full-service returns the XML to JSON.
     * 
     * @throws Exception
     */
    public void xml2Json(String xmlPath, Exchange exchange) throws Exception {
        Xml2Json4FbsMappingProcessor X2J = new Xml2Json4FbsMappingProcessor();
        String[] chkUsrStr = { xmlPath };
        X2J.applyParams(chkUsrStr);
        X2J.process(exchange);
    }

    /**
     * Full-service returns the XML to JSON.
     * 
     * @throws Exception
     */
    public void xml2Json4ONS(String xmlPath, Exchange exchange) throws Exception {
        Xml2JsonMappingProcessor X2J = new Xml2JsonMappingProcessor();
        String[] chkUsrStr = { xmlPath };
        X2J.applyParams(chkUsrStr);
        X2J.process(exchange);
    }

    /**
     * 调用枢纽平台返回应答编码为1位处理
     * 
     * @param xmlPath
     * @param exchange
     * @throws Exception
     */
    public void xml2Json1ONS(String xmlPath, Exchange exchange) throws Exception {
        Xml2Json1RespMappingProcessor X2J = new Xml2Json1RespMappingProcessor();
        String[] chkUsrStr = { xmlPath };
        X2J.applyParams(chkUsrStr);
        X2J.process(exchange);
    }

    public void Xml2Json4Cust(String xmlPath, Exchange exchange) throws Exception {
        Xml2Json4CustMappingProcessor X2J = new Xml2Json4CustMappingProcessor();
        String[] chkUsrStr = { xmlPath };
        X2J.applyParams(chkUsrStr);
        X2J.process(exchange);
    }

    public void xml2Json4qryCardData(String xmlPath, Exchange exchange) throws Exception {
        Xml2Json4QryCardDataMappingProcessor X2J = new Xml2Json4QryCardDataMappingProcessor();
        String[] chkUsrStr = { xmlPath };
        X2J.applyParams(chkUsrStr);
        X2J.process(exchange);
    }

    public void xml2Json4Res(String xmlPath, Exchange exchange) throws Exception {
        Xml2Json4ResMappingProcessor X2J = new Xml2Json4ResMappingProcessor();
        String[] chkUsrStr = { xmlPath };
        X2J.applyParams(chkUsrStr);
        X2J.process(exchange);
    }

    public void xml2JsonNoError(String xmlPath, Exchange exchange) throws Exception {
        Xml2Json4FbsNoErrorMappingProcessor X2J = new Xml2Json4FbsNoErrorMappingProcessor();
        String[] chkUsrStr = { xmlPath };
        X2J.applyParams(chkUsrStr);
        X2J.process(exchange);
    }

    /**
     * 解析没有报文体的返回报文
     * 
     * @param xmlPath
     * @param exchange
     * @throws Exception
     */
    public void xml2JsonNoBody(String xmlPath, Exchange exchange) throws Exception {
        Xml2JsonNoBodyMappingProcessor X2J = new Xml2JsonNoBodyMappingProcessor();
        String[] chkUsrStr = { xmlPath };
        X2J.applyParams(chkUsrStr);
        X2J.process(exchange);
    }

    /**
     * deal cityCode
     * 
     * @param msg
     * @return
     */
    public String dealCityCode(Map msg) {
        String cityCode = "";
        Map newUserInfo = (Map) msg.get("newUserInfo");
        List<Map> exchInfo = (List<Map>) newUserInfo.get("exchInfo");
        for (Map exch : exchInfo) {
            if ("CITY_CODE".equals(exch.get("key"))) {
                cityCode = exch.get("value").toString();
            }
        }
        if ("".equals(cityCode)) {
            throw new EcAopServerBizException("9999", "业务区编码CITY_CODE为空");
        }
        return cityCode;
    }

    /**
     * deal developer information
     * 
     * @param inputMap
     * @param msg
     */
    public void dealDevelopInfo(Map inputMap, Map msg) {
        if (null != msg.get("recomPersonId")) {
            inputMap.put("developStaffId", msg.get("recomPersonId"));
        }
        if (null != msg.get("recomPersonCityCode")) {
            inputMap.put("developEparchyCode", msg.get("recomPersonCityCode"));
        }
        if (null != msg.get("recomPersonDistrict")) {
            inputMap.put("developCityCode", msg.get("recomPersonDistrict"));
        }
        if (null != msg.get("recomPersonChannelId")) {
            inputMap.put("developDepartId", msg.get("recomPersonChannelId"));
        }
    }

    /**
     * create a Map by key and value
     * 
     * @param key
     * @param Value
     * @return
     */
    public Map createAttrInfo(String key, String value) {
        Map item = createAttrInfoNoTime(key, value);
        item.put("startDate", GetDateUtils.getDate());
        item.put("endDate", endDate);
        return item;
    }

    /**
     * create a Map by key,value and itemId
     * 
     * @param key
     * @param value
     * @param itemId
     * @return
     */
    public Map createAttrInfoWithId(String key, Object value, String itemId) {
        return MapUtils.asMap("attrCode", key, "attrValue", value, "itemId", itemId);
    }

    public Map createTradeSubItem(String key, Object value, String itemId) {
        return MapUtils.asMap("attrTypeCode", "0", "attrCode", key, "attrValue", value, "itemId", itemId);
    }
    public Map createTradeSubItem2NoDate(String key, Object value, String itemId) {
        return MapUtils.asMap("attrTypeCode", "2", "attrCode", key, "attrValue", value, "itemId", itemId);
    }

    public Map createTradeSubItem(String key, Object value) {
        return MapUtils.asMap("xDatatype", "NULL", "attrCode", key, "attrValue", value);
    }


    public static Map createTradeSubItem4(String key, String str, Object value, String itemId) {
        return MapUtils.asMap("xDatatype", "NULL", "attrTypeCode", str, "attrCode", key, "attrValue", value,
                "startDate", GetDateUtils.getDate(), "endDate", "20501231235959", "itemId", itemId);
    }

    public Map createTradeSubItem2(String key, Object object, String itemId) {
        return MapUtils.asMap("xDatatype", "NULL", "attrTypeCode", "2", "attrCode", key, "attrValue", object,
                "startDate", GetDateUtils.getDate(), "endDate", "20501231235959", "itemId", itemId);
    }

    public Map createTradeSubItem3(String key, Object value, String itemId) {
        return MapUtils.asMap("attrTypeCode", "3", "attrCode", key, "attrValue", value, "itemId", itemId);
    }

    public Map createTradeSubItem3AndDateTime(String key, Object value, String itemId, String startDate, String endDate) {
        return MapUtils.asMap("xDatatype", "NULL", "attrTypeCode", "3", "attrCode", key, "attrValue", value, "itemId",
                itemId, "startDate", startDate, "endDate", endDate);
    }

    public Map createTradeSubItem4(String key, Object value, String itemId) {
        return MapUtils.asMap("attrTypeCode", "4", "attrCode", key, "attrValue", value, "itemId", itemId);
    }

    public Map createTradeSubItemBroad(String key, Object value, String itemId) {
        return MapUtils.asMap("attrTypeCode", "0", "attrCode", key, "attrValue", value, "itemId", itemId, "startDate",
                GetDateUtils.getNextMonthFirstDayFormat(), "endDate", "20501231235959");
    }

    public Map createTradeSubItemBroad1(String key, Object value, String itemId, String startDate) {
        return MapUtils.asMap("attrTypeCode", "0", "attrCode", key, "attrValue", value, "itemId", itemId, "startDate",
                startDate, "endDate", "20501231235959");
    }

    public Map createTradeSubItemFive(String key, Object value, String itemId) {
        return MapUtils.asMap("attrTypeCode", "5", "attrCode", key, "attrValue", value, "itemId", itemId);
    }

    public List<Map> createTradeSubsItemList(String[] key, Object[] value, String itemId) {
        List<Map> retList = new ArrayList<Map>();
        if (key.length != value.length) {
            throw new EcAopServerBizException("9999", "长度不一致");
        }
        for (int i = 0; i < key.length; i++) {
            retList.add(createTradeSubItem(key[i], value[i], itemId));
        }
        return retList;
    }

    public Map createAttrInfoNoTime(String key, Object value) {
        return MapUtils.asMap("attrCode", key, "attrValue", value);
    }

    public static Map createTradeItem(String key, Object value) {
        return MapUtils.asMap("xDatatype", "NULL", "attrCode", key, "attrValue", value);
    }

    public static Map createTradeItemTime(String key, Object value) {
        return MapUtils.asMap("xDatatype", "NULL", "attrCode", key, "attrValue", value, "startDate",
                GetDateUtils.getDate(), "endDate", "20501231235959");
    }

    public static Map createTradeItemTime1(String key, Object value, String startDate, String endDate) {
        return MapUtils.asMap("xDatatype", "NULL", "attrCode", key, "attrValue", value, "startDate",
                startDate, "endDate", endDate);
    }

    public Map createTradeSubItemB(String itemId, String key, Object value, String startDate, String endDate) {
        return MapUtils.asMap("attrTypeCode", "2", "attrCode", key, "attrValue", value, "itemId", itemId, "startDate",
                startDate, "endDate", endDate);
    }

    public Map createTradeSubItem(String itemId, String key, Object value, String endDate) {
        return createTradeSubItem(itemId, key, value, GetDateUtils.getDate(), endDate);
    }

    public Map createTradeSubItemS(String itemId, String key, Object value, String endDate) {
        return MapUtils.asMap("attrTypeCode", "S", "attrCode", key, "attrValue", value, "itemId", itemId, "startDate",
                GetDateUtils.getDate(), "endDate", endDate);
    }

    public Map createTradeSubItem(String itemId, String key, Object value, String startDate, String endDate) {
        return MapUtils.asMap("attrTypeCode", "2", "attrCode", key, "attrValue", value, "itemId", itemId, "startDate",
                startDate, "endDate", endDate);
    }

    public Map createTradeSubItem1(String itemId, String key, Object value, String startDate) {
        return MapUtils.asMap("attrTypeCode", "1", "attrCode", key, "attrValue", value, "itemId", itemId, "startDate",
                startDate, "endDate", endDate);
    }

    public Map createTradeSubItem3(String itemId, String key, Object value, String endDate, String startDate) {
        return MapUtils.asMap("attrTypeCode", "3", "attrCode", key, "attrValue", value, "itemId", itemId, "startDate",
                startDate, "endDate", endDate);
    }

    public Map createTradeSubItemB1(String itemId, String key, Object value, String endDate) {
        return MapUtils.asMap("attrTypeCode", "2", "attrCode", key, "attrValue", value, "itemId", itemId, "startDate",
                GetDateUtils.getDate(), "endDate", endDate);
    }

    public Map createTradeSubItemB2(String itemId, String key, Object value) {
        return MapUtils.asMap("attrTypeCode", "0", "attrCode", key, "attrValue", value, "itemId", itemId);
    }

    public Map createTradeSubItemC(String itemId, String key, Object value, String startDate, String endDate) {
        return MapUtils.asMap("xDatatype", "NULL", "attrTypeCode", "3", "attrCode", key, "attrValue", value, "itemId",
                itemId, "startDate",
                startDate, "endDate", endDate);
    }

    public Map createTradeSubItemD(String itemId, String key, Object value, String startDate, String endDate) {
        return MapUtils.asMap("attrTypeCode", "2", "attrCode", key, "attrValue", value, "itemId", itemId, "startDate",
                startDate, "endDate", endDate);
    }

    public Map createTradeSubItemE(String key, Object value, String itemId) {
        return MapUtils.asMap("xDatatype", "NULL", "attrTypeCode", "0", "attrCode", key, "attrValue", value, "itemId",
                itemId);
    }

    public Map createTradeSubItem4D(String attrCode, Object attrValue, String itemId) {
        return MapUtils.asMap("xDatatype", "NULL", "attrTypeCode", "4", "attrCode", attrCode, "attrValue", attrValue, "itemId", itemId);
    }

    public Map createTradeSubItemF(String key, String itemId) {
        return MapUtils.asMap("xDatatype", "NULL", "attrTypeCode", "7", "attrCode", key, "itemId", itemId);
    }

    public static Map createTradeSubItemG(String key, String value, String itemId) {
        return MapUtils.asMap("xDatatype", "NULL", "attrTypeCode", "7", "attrCode", key, "attrValue", value, "itemId",
                itemId);
    }

    public static Map createTradeSubItemH(String key, String value, String itemId) {
        return MapUtils.asMap("xDatatype", "NULL", "attrTypeCode", "6", "attrCode", key, "attrValue", value, "itemId",
                itemId);
    }

    public Map createTradeSubItemJ(String key, Object object, String itemId) {
        return MapUtils.asMap("xDatatype", "NULL", "attrTypeCode", "0", "attrCode", key, "attrValue", object,
                "startDate", GetDateUtils.getDate(), "endDate", "20501231235959", "itemId", itemId);
    }

    public static Map createTradeSubItemK(String key, String value, String itemId) {
        return MapUtils.asMap("xDatatype", "NULL", "attrTypeCode", "6", "attrCode", key, "attrValue", value,
                "startDate", GetDateUtils.getNextMonthFirstDayFormat(), "endDate", "20501231235959", "itemId", itemId);
    }

    public static Map createTradeSubItemAll(Object attrTypeCode, Object key, Object value, Object itemId,
            Object startDate,
            Object endDate) {
        return MapUtils.asMap("attrTypeCode", attrTypeCode, "attrCode", key, "attrValue", value, "itemId", itemId,
                "startDate", startDate, "endDate", endDate);
    }

    public static Map createPara(String key, String value) {
        return MapUtils.asMap("paraId", key, "paraValue", value);
    }
}
