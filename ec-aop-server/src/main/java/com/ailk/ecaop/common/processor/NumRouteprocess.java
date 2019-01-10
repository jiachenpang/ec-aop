package com.ailk.ecaop.common.processor;

/**
 * Created by Liu JiaDi on 2016/8/18.
 */

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.log.elk.ELKAopLogger;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.dao.essbusiness.EssBusinessDao;
import com.alibaba.fastjson.JSONObject;

/**
 * 该方法用于根据号码区分走原有流程还是走号卡中心流程
 * 返回参数  numSwitch 0 号卡中心流程 1原有流程
 * 号码状态变更简单版时 号码位于resourcesInfo节点下处理 resourcesType 01预付费走原有流程
 * 预提交时 号码位于numId下面的情况处理
 * 未处理部分 融合业务、选号接口、开户提交（没条件）
 * mini厅只有2G号码直接走号卡中心
 * 号码状态变更标准版存在变更号码状态时走不同分枝
 * 1 新号码在号卡，老号码在3ge numSwitch=2
 * 2 新号码在3ge或北六e，老号码在号卡 numSwitch=3
 * 3 老号码是北六省份号码，新号码是号卡中心时直接报错不支持
 * 
 * @auther Liu JiaDi
 * @create 2016_08_18_10:41
 */
@EcRocTag("NumRoute")
public class NumRouteprocess extends BaseAopProcessor {
    public EssBusinessDao essDao = new EssBusinessDao();

    @Override
    public void process(Exchange exchange) throws Exception {
        long start = System.currentTimeMillis();
        Map body = (Map) exchange.getIn().getBody();
        Object msgObject = body.get("msg");
        Object apptx = body.get("apptx");
        Map map;
        if (msgObject instanceof String) {
            map = JSONObject.parseObject(msgObject.toString());
        }
        else {
            map = (Map) msgObject;
        }
        map.put("method", exchange.getMethodCode());
        map.put("appCode", exchange.getAppCode());
        exchange.setProperty("numSwitch", "0");
        // 塞numSwitch进msg，后面去n6aop需要用到
        ((Map) body.get("msg")).put("numSwitch", "0");
        exchange.setProperty("ifAllNumCenter", "ok");
        exchange.setProperty("checkSerType", "1");
        exchange.setProperty("selCard", "0");
        exchange.getIn().setBody(body);
        ELKAopLogger.logStr("NumRouteprocess in:" + GetDateUtils.getDate() + ",cost:"
                + (System.currentTimeMillis() - start) + "ms,apptx:" + body.get("apptx"));
        return;
       /* String privinceCode = EcAopConfigLoader.getStr("ecaop.global.param.num.aop.province");
        if (privinceCode.contains((String) map.get("province"))) {
            map.put("method", exchange.getMethodCode());
            map.put("appCode", exchange.getAppCode());
            exchange.setProperty("numSwitch", "1");
            // 塞numSwitch进msg，后面去n6aop需要用到
            ((Map) body.get("msg")).put("numSwitch", "1");
            exchange.setProperty("ifAllNumCenter", "no");
            exchange.setProperty("checkSerType", "1");
            exchange.setProperty("selCard", "1");
            exchange.getIn().setBody(body);
            return;
        }
        map.put("method", exchange.getMethodCode());
        map.put("appCode", exchange.getAppCode());
        //默认走原有流程
        List<Map> routeInfo = essDao.selRouteInfoByProvince(map);
        String numSwitch = selRouteInfoByProvince(map, routeInfo);
        exchange.setProperty("numSwitch", numSwitch);
        // 塞numSwitch进msg，后面去n6aop需要用到
        ((Map) body.get("msg")).put("numSwitch", numSwitch);

        String ifAllNumCenter = selRouteByProvince(routeInfo);
        String selCard = selCardByProvince(routeInfo);
        String checkSerType = checkSerType(routeInfo, map.get("serType"));
        exchange.setProperty("ifAllNumCenter", ifAllNumCenter);
        exchange.setProperty("selCard", selCard);
        exchange.setProperty("checkSerType", checkSerType);
        exchange.getIn().setBody(body);
        ELKAopLogger.logStr("NumRouteprocess in:" + GetDateUtils.getDate() + ",cost:"
                + (System.currentTimeMillis() - start) + "ms,apptx:" + body.get("apptx"));*/
    }

    // 路由修改，预付费且部分割接的时候走原流程
    private String checkSerType(List<Map> routeInfo, Object serType) {
        String checkSerType;
        if (routeInfo != null && (routeInfo.size()) > 0) {
            String PARA_CODE1 = ((String) routeInfo.get(0).get("PARA_CODE1"));
            String PARA_CODE2 = ((String) routeInfo.get(0).get("PARA_CODE2"));
            if ("2".equals(serType) && "1".equals(PARA_CODE1) && "0".equals(PARA_CODE2)) {
                checkSerType = "0";
            }
            else {
                checkSerType = "1";
            }
            return checkSerType;
        }
        return "1";
    }

    /**
     * 用于判断是否割接以及是否是全量割接
     * 以及部分割接根据号段判断流程
     */
    private String selRouteInfoByProvince(Map map, List<Map> routeInfo) {

        // 调整为先判断开关
        if (null != routeInfo && routeInfo.size() > 0) {
            // 全量割接直接走号卡
            if ("1".equals(routeInfo.get(0).get("PARA_CODE1")) && "1".equals(routeInfo.get(0).get("PARA_CODE2"))) {
                return "0";
            }
            // PARA_CODE1 0 不对接号卡中心 1 对接
            if ("0".equals(routeInfo.get(0).get("PARA_CODE1"))) {
                return "1";
            }
        }
        // 号码变更
        if ("mnsb".equals(map.get("appCode")) && "nboc".equals(map.get("method"))) {
            List<Map> resourcesInfo = (List<Map>) map.get("resourcesInfo");
            if (null != resourcesInfo && resourcesInfo.size() > 0) {
                // 针对mini厅单独处理
                String occupiedFlag = (String) resourcesInfo.get(0).get("occupiedFlag");
                if ("2".equals(occupiedFlag)) {
                    return "0";
                }

            }
        }
        /*
         * String serType = String.valueOf(map.get("serType"));
         * //针对选号、主副卡时预付费号码走原有流程
         * if (StringUtils.isNotEmpty(serType) && "2,3".contains(serType)) {
         * return "1";
         * }
         * //开户申请时预付费逻辑
         * List<Map> userInfo = (List<Map>) map.get("userInfo");
         * if (null != userInfo && userInfo.size() > 0) {
         * serType = String.valueOf(userInfo.get(0).get("serType"));
         * if (StringUtils.isNotEmpty(serType) && "2,3".contains(serType)) {
         * return "1";
         * }
         * }
         */
        if (null != routeInfo && routeInfo.size() > 0) {
            // PARA_CODE2 1 全号段割接 0 部分号段
            if ("1".equals(routeInfo.get(0).get("PARA_CODE2"))) {
                return "0";
            }
            return selRouteInfoByNum(map);
        }
        return "1";
    }

    // 判断是否走号卡中心
    private String selCardByProvince(List<Map> routeInfo) {
        return null != routeInfo && routeInfo.size() > 0 && "1".equals(routeInfo.get(0).get("PARA_CODE1")) ? "0" : "1";
    }

    /**
     * 仅用于判断是否割接以及是否是全量割接,
     */
    private String selRouteByProvince(List<Map> routeInfo) {
        if (null != routeInfo && routeInfo.size() > 0) {
            // PARA_CODE1 0 不对接号卡中心 1 对接
            if ("0".equals(routeInfo.get(0).get("PARA_CODE1"))) {
                return "no";
            }
            // PARA_CODE2 1 全号段割接 0 部分号段
            if ("1".equals(routeInfo.get(0).get("PARA_CODE2"))) {
                return "OK";
            }
            return "no";
        }
        return "no";
    }

    /**
     * 部分割接字段判断流程
     * 入参是请求msg，需要获取里面的号码信息
     * 包括各种层级关系 1 号码位于msg节点下
     * 2 号码位于resourcesInfo节点下
     */

    private String selRouteInfoByNum(Map msg) {
        Map tempMap = new HashMap();
        String province = (String) msg.get("province");
        tempMap.put("province", province);

        String serialNumber = (String) msg.get("serialNumber");
        if (StringUtils.isNotEmpty(serialNumber)) {
            serialNumber = serialNumber.substring(0, 7);
            tempMap.put("serialNumber", serialNumber);
            if (String.valueOf(essDao.selRouteInfoByNum(tempMap).get(0)).equals("1")) {
                return "0";
            }
        }
        else {
            if ("cdsn,snos".contains((String) msg.get("method"))) {
                // 卡数据同步时
                String numId = (String) msg.get("numId");
                if (StringUtils.isNotEmpty(numId)) {
                    numId = numId.substring(0, 7);
                    tempMap.put("serialNumber", numId);
                    if (String.valueOf(essDao.selRouteInfoByNum(tempMap).get(0)).equals("1")) {
                        return "0";
                    }
                }
            }
            if ("opap|opnc".contains((String) msg.get("method"))) {
                // 开户申请时
                List<Map> numIdList = (List<Map>) msg.get("numId");
                if (null != numIdList && numIdList.size() > 0) {
                    serialNumber = (String) numIdList.get(0).get("serialNumber");
                    serialNumber = serialNumber.substring(0, 7);
                    tempMap.put("serialNumber", serialNumber);
                    if (String.valueOf(essDao.selRouteInfoByNum(tempMap).get(0)).equals("1")) {
                        return "0";
                    }
                }
            }
            // 号码变更
            List<Map> resourcesInfo = (List<Map>) msg.get("resourcesInfo");
            if (null != resourcesInfo && resourcesInfo.size() > 0) {
                String snChangeTag = (String) resourcesInfo.get(0).get("snChangeTag");
                String resourcesCode = (String) resourcesInfo.get(0).get("resourcesCode");
                // 原号码
                String oldResourcesCode = (String) resourcesInfo.get(0).get("oldResourcesCode");
                // 需要变更号码时
                if (StringUtils.isNotEmpty(snChangeTag) && "1".equals(snChangeTag)) {
                    if (StringUtils.isEmpty(oldResourcesCode)) {
                        throw new EcAopServerBizException("9999", "变更号码时原号码必传");
                    }
                    serialNumber = oldResourcesCode.substring(0, 7);
                    tempMap.put("serialNumber", serialNumber);
                    if (String.valueOf(essDao.selRouteInfoByNum(tempMap).get(0)).equals("1")) {
                        // 老号码在号卡中心
                        serialNumber = resourcesCode.substring(0, 7);
                        tempMap.put("serialNumber", serialNumber);
                        if (String.valueOf(essDao.selRouteInfoByNum(tempMap).get(0)).equals("1")) {
                            // 新号码在号卡中心
                            return "0";
                        }
                        // 新号码在ESS
                        return "3";
                    }
                    // 老号码不在号卡中心
                    serialNumber = resourcesCode.substring(0, 7);
                    tempMap.put("serialNumber", serialNumber);
                    if (String.valueOf(essDao.selRouteInfoByNum(tempMap).get(0)).equals("1")) {
                        if ("11,17,18,76,91,97".contains((String) msg.get("province"))) {
                            throw new EcAopServerBizException("9999", "北六省份不支持变更号码");
                        }
                        // 新号码在号卡中心
                        return "2";
                    }
                    // 新号码在ESS
                    return "1";
                }
/*                if ("qcsc".equals(msg.get("method"))) {
                    String resourcesType = (String) resourcesInfo.get(0).get("resourcesType");
                    if (StringUtils.isNotEmpty(resourcesType) && "01".equals(resourcesType)) {
                        return "1";
                    }
                }*/

                if (StringUtils.isNotEmpty(resourcesCode)) {
                    serialNumber = resourcesCode.substring(0, 7);
                    tempMap.put("serialNumber", serialNumber);
                    if (String.valueOf(essDao.selRouteInfoByNum(tempMap).get(0)).equals("1")) {
                        return "0";
                    }
                }

            }
        }
        return "1";
    }
}
