package com.ailk.ecaop.common.processor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.dao.essbusiness.EssBusinessDao;
import com.alibaba.fastjson.JSONObject;

/**
 * 该方法用于根据号码判断是'1'非资源中心 或'2'资源中心
 * 返回参数 numberSrc '1'非资源中心 或'2'资源中心
 * 
 * @auther ZHANGMENG
 * @create 2016_11_03
 */
@EcRocTag("NumberSrc")
public class NumberSrcprocess extends BaseAopProcessor {

    EssBusinessDao essDao = new EssBusinessDao();

    @Override
    public void process(Exchange exchange) throws Exception {

        Map body = (Map) exchange.getIn().getBody();
        Object msgObject = body.get("msg");
        Map map;
        if (msgObject instanceof String) {
            map = JSONObject.parseObject(msgObject.toString());
        }
        else {
            map = (Map) msgObject;
        }

        String methodCode = exchange.getMethodCode();
        if ("tsps".equals(methodCode)) {
            map.put("serialNumber", map.get("prePhone"));
        }
        else if ("spoo".equals(methodCode)) {
            map.put("serialNumber", map.get("phone"));
        }
        String numberSrc = selNumAttrByNum(map);
        map.put("numberSrc", numberSrc);
        exchange.setProperty("numberSrc", numberSrc);
        exchange.getIn().setBody(body);
    }

    /**
     * 根据号码以及省份编码判断号码归属
     */
    private String selNumAttrByNum(Map map) {
        List<Map> routeInfo = essDao.selRouteInfoByProvince(map);
        if (null != routeInfo && routeInfo.size() > 0) {
            // PARA_CODE1 0 不对接号卡中心 1 对接
            if ("0".equals(routeInfo.get(0).get("PARA_CODE1"))) {
                return "1";
            }
            // PARA_CODE2 1 全号段割接 0 部分号段
            if ("1".equals(routeInfo.get(0).get("PARA_CODE2"))) {
                return "2";
            }
            else {
                return selRouteInfoByNum(map);
            }
        }
        return "1";

    }

    /**
     * 部分割接字段判断流程
     * 入参是请求map，需要获取里面的号码信息
     */

    private String selRouteInfoByNum(Map map) {
        Map tempMap = new HashMap();
        String province = (String) map.get("province");
        tempMap.put("province", province);
        String serialNumber = (String) map.get("serialNumber");
        if (StringUtils.isNotEmpty(serialNumber)) {
            serialNumber = serialNumber.substring(0, 7);
            tempMap.put("serialNumber", serialNumber);
            if ("1".equals(essDao.selRouteInfoByNum(tempMap).get(0))) {
                return "2";
            }
            return "1";
        }
        return "1";
    }

}
