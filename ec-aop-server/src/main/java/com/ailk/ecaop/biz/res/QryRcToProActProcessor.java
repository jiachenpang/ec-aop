package com.ailk.ecaop.biz.res;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.extractor.DealSysCodeExtractor;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.NumCenterUtils;
import com.alibaba.fastjson.JSON;

/**
 * 南25省3G开户时,调用资源中心获取终端、产品、活动信息
 * 1.若不含有活动(即也没有终端),则只校验产品信息,获取产品的信息中,产品构成为非必反
 * 2.若含有活动没有终端,则校验产品和活动的关系
 * 3.含有终端,则根据具体的业务类型,判断校验类型
 * @author wangmc 20180530
 */
public class QryRcToProActProcessor {

    public static NumCenterUtils nc = new NumCenterUtils();

    /**
     * 调用资源中心,获取终端产品和活动的信息 by wangmc 20180527
     * @param exchange
     * @param reqMsg
     */
    public static void checkRcToProAct(Exchange exchange, Map reqMsg) {
        // 转换sysCode
        Object msgObj = new DealSysCodeExtractor().extract(exchange);
        boolean isString = msgObj instanceof String;
        Map msg = isString ? JSON.parseObject((String) msgObj) : (Map) msgObj;
        String toResCenterProvince = EcAopConfigLoader.getStr("ecaop.global.param.resources.aop.province");
        // 判断是否需要获取资源产品活动的信息,只有割接省份才会调用
        if (!toResCenterProvince.contains(String.valueOf(reqMsg.get("province")))) {
            return;
        }
        // 获取请求中传入的主产品ID、活动ID(若含有)、终端串号(若含有)
        getResActProInfo(msg);
        // 拼装请求报文
        Map request = preResProActReqData(msg);
        Exchange exchangeResCenter = ExchangeUtils.ofCopy(exchange, msg);
        exchangeResCenter.getIn().setBody(JSON.toJSON(request));
        // 调用资源中心获取数据
        try {
            CallEngine.numCenterCall(exchangeResCenter, "ecaop.comm.conf.url.api.qryRcToProAct");
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用资源中心获取终端信息失败!" + e.getMessage());
        }
        Map rcToProAct = dealReturn(exchangeResCenter);
        reqMsg.put("resCenterSwitch", "1");
        reqMsg.put("resProActData", rcToProAct.toString());
    }

    /**
     * 拼装调用产商接口的请求报文
     * @param msg
     * @return
     */
    private static Map preResProActReqData(Map msg) {
        // 拼装调用资源中心的参数
        Map request = new HashMap();
        // 创建报文头
        try {
            request.putAll(NumFaceHeadHelper.creatHead());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        // 创建报文体
        Map requestTempBody = new HashMap();
        // 报文体公共参数
        requestTempBody.putAll(preRequstBodyHead(msg));
        // 报文体其他参数
        preRequestBody(requestTempBody, msg);
        Map resultMap = new HashMap();
        resultMap.put("QRYRCTOPROACT_REQ", requestTempBody);
        request.put("UNI_BSS_BODY", resultMap);
        return request;
    }

    /**
     * 调用产商接口拼装报文头 by wangmc 20180518
     */
    public static Map preRequstBodyHead(Map msg) {
        Map commonMap = new HashMap();
        commonMap.put("OPERATOR_ID", msg.get("operatorId"));
        commonMap.put("PROVINCE", msg.get("province"));
        commonMap.put("EPARCHY_CODE", msg.get("city"));
        commonMap.put("CITY_CODE", msg.get("district"));
        commonMap.put("CHANNEL_ID", msg.get("channelId"));
        commonMap.put("CHANNEL_TYPE", msg.get("channelType"));
        /*List<Map> para = (List<Map>) msg.get("para");
        if (null != para && para.size() > 0) {
            List<Map> paraList = new ArrayList<Map>();
            for (Map paraMap : para) {
                Map map = new HashMap();
                map.put("PARA_ID", paraMap.get("paraId"));
                map.put("PARA_VALUE", paraMap.get("paraValue"));
                paraList.add(map);
            }
            commonMap.put("PARA", paraList);
        }*/
        return commonMap;
    }

    /**
     * 准备调用资源中心的报文体 by wangmc 20180530
     * @param requestTempBody
     * @param msg
     */
    private static void preRequestBody(Map requestTempBody, Map msg) {
        // 串码
        if (!IsEmptyUtils.isEmpty(msg.get("checkResourceCode"))) {
            requestTempBody.put("RS_IMEI", msg.get("checkResourceCode"));
        }
        if (!IsEmptyUtils.isEmpty(msg.get("checkActPlanId"))) {
            requestTempBody.put("ACT_PLAN_ID", msg.get("checkActPlanId"));
        }
        if (!IsEmptyUtils.isEmpty(msg.get("checkProductId"))) {
            requestTempBody.put("PRODUCT_ID", msg.get("checkProductId"));
        }

        // 客户类型：01：个人客户 02：集团客户
        List<Map> customerInfo = (List<Map>) msg.get("customerInfo");
        if (null != customerInfo && !customerInfo.isEmpty()) {
            if (!IsEmptyUtils.isEmpty(customerInfo.get(0).get("newCustomerInfo"))) {
                List<Map> newCustInfoList = (List<Map>) customerInfo.get(0).get("newCustomerInfo");
                if (!IsEmptyUtils.isEmpty(newCustInfoList.get(0).get("custType"))) {
                    String custType = (String) newCustInfoList.get(0).get("custType");
                    if ("01".equals(custType) || "02".equals(custType)) {
                        requestTempBody.put("CUST_TYPE", custType);
                    }
                }
            }
        }
        // groupFlag为1表示集团用户,此时下发集团id
        // 是否集团用户
        boolean isGroup = false;
        Map userInfo = ((List<Map>) msg.get("userInfo")).get(0);
        if ("1".equals(userInfo.get("groupFlag")) && !IsEmptyUtils.isEmpty(userInfo.get("groupId"))) {
            requestTempBody.put("GROUP_ID", userInfo.get("groupId"));
            isGroup = true;
        }
        // 校验的类型
        preCheckType(requestTempBody, msg, userInfo, isGroup);
    }

    /**
     * 处理校验类型 by wangmc 20180530
     * @param requestTempBody
     * @param msg
     * @param userInfo
     * @param isGroup
     * @return
     */
    private static void preCheckType(Map requestTempBody, Map msg, Map userInfo, boolean isGroup) {
        // 受理类型
        String serType = (String) userInfo.get("serType");
        // 业务类型
        String bipType = (String) userInfo.get("bipType");

        // checkType默认值 08-校验产品,因为开户肯定会有产品,产品不一定会有产品构成节点,需要在3GE那里特殊处理
        requestTempBody.put("CHCK_TYPE", "08");

        // 有活动
        if (!IsEmptyUtils.isEmpty(requestTempBody.get("ACT_PLAN_ID"))) {
            // 后付费手机类
            if ("1".equals(serType) && "1,2".contains(bipType)) {
                // 非号卡类业务(合约类)
                if (!"1".equals(bipType)) {
                    // 集团用户-校验集团产品关系&&校验合约与终端关系-04;非集团用户-校验终端与活动的关系-01
                    requestTempBody.put("CHCK_TYPE", isGroup ? "04" : "01");
                }
                else if (isGroup) { // 号卡类且为集团用户时,校验集团与产品关系-03
                    requestTempBody.put("CHCK_TYPE", "03");
                }
                else {
                    // FIXME 非上述流程时,则表示没有终端,暂时传单产品试试
                    requestTempBody.put("CHCK_TYPE", "08");
                }
            }
            else if ("2".equals(serType) && "3".equals(bipType)) { // 预付费上网卡
                // FIXME 只校验终端,不校验关系 (先修改为校验终端和活动的关系)
                requestTempBody.put("CHCK_TYPE", "01");
            }
            else if ("1".equals(serType) && "3".equals(bipType)) { // 后付费上网卡
                // 校验终端与活动的关系-01
                requestTempBody.put("CHCK_TYPE", "01");
            }
            else if ("2".equals(serType) && "1".equals(bipType)) {// 36元预付费号卡类(存费送费)
                // 校验产品和活动关系 - 02
                requestTempBody.put("CHCK_TYPE", "02");
            }
        }
        else if (isGroup) { // 预付费后付费手机类,只有存在集团的情况下才会校验集团与产品的关系
            if (("1".equals(serType) && "1,2".contains(bipType)) || ("2".equals(serType) && "1".equals(bipType))) {
                requestTempBody.put("CHCK_TYPE", "03");
            }
        }
    }

    /**
     * 处理资源中心的返回 by wangmc 20180527
     * @param exchange
     */
    private static Map dealReturn(Exchange exchange) {
        nc.dealReturnHead(exchange);
        Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
        Map bodyMap = (Map) outMap.get("UNI_BSS_BODY");
        if (IsEmptyUtils.isEmpty(bodyMap)) {
            throw new EcAopServerBizException("9999", "资源中心未返回报文体");
        }
        Map rspMap = (Map) bodyMap.get("QRYRCTOPROACT_RSP");
        if (IsEmptyUtils.isEmpty(rspMap)) {
            throw new EcAopServerBizException("9999", "资源中心未返回信息");
        }
        if (!"0000".equals(rspMap.get("CODE"))) {
            throw new EcAopServerBizException("9999", "资源中心接口返回:" + rspMap.get("DESC"));
        }
        Map resData = (Map) rspMap.get("DATA");
        if (IsEmptyUtils.isEmpty(resData)) {
            throw new EcAopServerBizException("9999", "资源中心未返回产品/活动/终端信息");
        }
        return resData;
    }

    /**
     * 获取活动,终端以及产品信息 by wangmc 20180527
     * 开户时产品必传,活动非必传,有活动时终端非必传,需要根据业务类型:bipType判断
     * @param msg
     * @return
     */
    private static void getResActProInfo(Map msg) {
        if (null == msg.get("userInfo")) {
            return;
        }
        List<Map> userInfo = (List<Map>) msg.get("userInfo");
        for (Map userInfoMap : userInfo) {
            // 校验是否有终端信息
            List<Map> activityInfo = (List<Map>) userInfoMap.get("activityInfo");
            if (!IsEmptyUtils.isEmpty(activityInfo)) {
                for (Map activityMap : activityInfo) {
                    msg.put("checkActPlanId", activityMap.get("actPlanId"));
                    if (!IsEmptyUtils.isEmpty(activityMap.get("resourcesCode"))
                            && !IsEmptyUtils.isEmpty(activityMap.get("resourcesType"))) {
                        // 获取终端和活动,调用资源中心时使用
                        msg.put("checkResourceCode", activityMap.get("resourcesCode"));
                    }
                }
            }
            // 获取产品ID
            List<Map> productInfo = (List<Map>) userInfoMap.get("product");
            if (!IsEmptyUtils.isEmpty(productInfo)) {
                for (Map productMap : productInfo) {
                    if ("1".equals(productMap.get("productMode")) && !IsEmptyUtils.isEmpty(productMap.get("productId"))) {
                        // 获取产品ID,调用资源中心时使用
                        msg.put("checkProductId", productMap.get("productId"));
                        break;
                    }
                }
            }
        }
    }
}
