package com.ailk.ecaop.biz.res;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.extractor.TransIdFromRedisValueExtractor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.NumCenterUtils;
import com.alibaba.fastjson.JSON;

/**
 * 新资源中心终端处理共用类
 */

public class ResourceStateChangeUtils {

    LanUtils lan = new LanUtils();
    NumCenterUtils nc = new NumCenterUtils();

    /**
     * 共用参数封装新零售给CBSS提供规范
     */
    public Map preCommonImplementCbss(Map msg) {
        Map commonMap = new HashMap();
        commonMap.put("OPERATOR_ID", msg.get("operatorId"));
        commonMap.put("PROVINCE", msg.get("province"));
        commonMap.put("CITY", msg.get("city"));
        commonMap.put("DISTRICT", null == msg.get("district") ? "000000" : msg.get("district"));
        commonMap.put("CHANNEL_ID", msg.get("channelId"));
        commonMap.put("CHANNEL_TYPE", msg.get("channelType"));
        commonMap.put("ACCESS_TYPE", "01");
        List<Map> para = (List<Map>) msg.get("para");
        List<Map> paraList = new ArrayList<Map>();
        if (null != para && para.size() > 0) {
            for (Map paraMap : para) {
                Map map = new HashMap();
                map.put("PARA_ID", paraMap.get("paraId"));
                map.put("PARA_VALUE", paraMap.get("paraValue"));
                paraList.add(map);
            }
            commonMap.put("PARA", paraList);
        }
        return commonMap;
    }

    /**
     * 终端信息校验
     * 获取终端信息
     * 终端信息预占
     * 非TAC码
     * @param exchange
     */
    public void getResourceInfo(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        // 首先判断能否调拨
        Exchange copeExchange = ExchangeUtils.ofCopy(exchange, msg);
        termAutoTransfer(copeExchange);

        Map requestBody = new HashMap();
        Map requestTempBody = new HashMap();
        requestTempBody.putAll(preCommonImplementCbss(msg));
        try {
            requestBody.putAll(NumFaceHeadHelper.creatHead());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        List<Map> resourceInfoList = new ArrayList<Map>();
        boolean isOccupy = false;
        List<Map> resourceInfo = (List<Map>) msg.get("resourcesInfo");
        for (Map resMap : resourceInfo) {
            Map resTempMap = new HashMap();
            resTempMap.put("RESOURCES_TYPE", resMap.get("resourcesType"));// 可能需要转码 先不下发
            resTempMap.put("RESOURCES_CODE", resMap.get("resourcesCode"));
            resTempMap.put("OPER_TYPE", "0");
            if ("1".equals(resMap.get("occupiedFlag"))) {
                isOccupy = true;
                resTempMap.put("OPER_TYPE", "1");
            }
            resTempMap.put("OCCUPIED_FLAG", resMap.get("occupiedFlag"));

            resTempMap.put("USE_TYPE", "1");
            resourceInfoList.add(resTempMap);

        }

        Map resultMap = new HashMap();
        Map checkResMap = new HashMap();
        requestTempBody.put("RESOURCES_INFO", resourceInfoList);
        checkResMap.put("CHECK_RES_REQ", requestTempBody);
        resultMap.put("QRYCHKTERM_REQ", checkResMap);
        requestBody.put("UNI_BSS_BODY", resultMap);
        exchange.getIn().setBody(JSON.toJSON(requestBody));
        CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.nrResCenter.qryAndOccupyRes");
        nc.dealReturnHead(exchange);
        dealReturnGetResourceInfo(exchange, isOccupy);
    }

    /**
     * 处理预占或者终端查询的返回结果
     */
    private void dealReturnGetResourceInfo(Exchange exchange, boolean isOccupy) {
        Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
        Map bodyMap = (Map) outMap.get("UNI_BSS_BODY");
        if (IsEmptyUtils.isEmpty(bodyMap)) {
            throw new EcAopServerBizException("9999", "新资源中心qryChkTerm服务返回结果为空");
        }
        Map resultMap = (Map) bodyMap.get("QRYCHKTERM_RSP");
        if (IsEmptyUtils.isEmpty(resultMap)) {
            throw new EcAopServerBizException("9999", "新资源中心qryChkTerm服务返回结果为空");
        }
        Map resMap = (Map) resultMap.get("CHECK_RES_RSP");
        if (IsEmptyUtils.isEmpty(resMap)) {
            throw new EcAopServerBizException("9999", "新资源中心qryChkTerm服务返回结果为空");
        }
        List<Map> resList = (List<Map>) resMap.get("RESOURCES_RSP");
        if (IsEmptyUtils.isEmpty(resList)) {
            throw new EcAopServerBizException("9999", "新资源中心qryChkTerm服务返回结果为空");
        }
        List<Map> resourcesRsp = new ArrayList<Map>();
        for (Map dataInfo : resList) {
            Map resourceInfoMap = new HashMap();
            resourceInfoMap.put("resourcesType", dataInfo.get("RESOURCES_TYPE"));// 资源类型
            resourceInfoMap.put("resourcesCode", dataInfo.get("RESOURCES_CODE"));// 串码
            String resourcesCode2 = (String) dataInfo.get("RESOURCES_CODE2");
            if (StringUtils.isNotEmpty(resourcesCode2)) {
                resourceInfoMap.put("resourcesCode2", resourcesCode2);// 串码2
            }
            String rscStateCode = (String) dataInfo.get("RSC_STATE_CODE");
            if (StringUtils.isEmpty(rscStateCode)) {
                throw new EcAopServerBizException("9999", "新资源中心qryChkTerm服务返回RSC_STATE_CODE为空");
            }
            resourceInfoMap.put("rscStateCode", rscStateCode);// 资源变更结果编码
            String stateDesc = "";// 资源状态描述
            String state = "00";
            if ("0000".equals(rscStateCode)) {
                if (isOccupy) {
                    state = "02";
                    // stateDesc = "预占";
                }
                else {
                    state = "01";
                    stateDesc = "空闲";
                }
            }
            else if ("0001".equals(rscStateCode)) {
                state = "02";
                stateDesc = "预占";
            }
            else if ("0002".equals(rscStateCode)) {
                stateDesc = (String) dataInfo.get("RSC_STATE_DESC");
            }
            else if ("0003".equals(rscStateCode)) {
                state = "03";
                stateDesc = "已售";
            }
            else if ("0004".equals(rscStateCode)) {
                state = "04";
                stateDesc = "损坏";
            }// 其他状态也返回描述 by wangmc 20180607
             //
            if (!"00".equals(state)) {
                resourceInfoMap.put("rscStateType", state);// 资源状态
            }
            if ("".equals(stateDesc)) {
                stateDesc = (String) dataInfo.get("RSC_STATE_DESC");// 资源状态描述
            }
            resourceInfoMap.put("rscStateDesc", stateDesc);// 资源状态描述
            // if (!isOccupy) {
            resourceInfoMap.put("resourcesBrandCode", dataInfo.get("RESOURCES_BRAND_CODE"));// 品牌
            // 资源中心只会返回一个字段,所以获取同一个
            // resourceInfoMap.put("orgDeviceBrandCode", dataInfo.get("RESOURCES_BRAND_CODE"));// 品牌
            resourceInfoMap.put("orgDeviceBrandCode", dataInfo.get("ORG_DEVICE_BRAND_CODE"));// 品牌
            resourceInfoMap.put("resourcesBrandName", dataInfo.get("RESOURCES_BRAND_NAME"));// 品牌名称
            resourceInfoMap.put("resourcesModelCode", dataInfo.get("RESOURCES_MODEL_CODE"));// 型号
            resourceInfoMap.put("resourcesModelName", dataInfo.get("RESOURCES_MODEL_NAME"));// 型号名称
            resourceInfoMap.put("machineTypeCode", dataInfo.get("MACHINE_TYPE_CODE"));// 终端机型编码
            resourceInfoMap.put("machineTypeName", dataInfo.get("MACHINE_TYPE_NAME"));// 终端机型名称
            resourceInfoMap.put("salePrice", dataInfo.get("SALE_PRICES"));// 销售价格 单位厘
            resourceInfoMap.put("cost", dataInfo.get("COST"));// 成本价格 单位厘
            resourceInfoMap.put("cardPrice", dataInfo.get("CARD_PRICE"));// 卡费 单位厘
            resourceInfoMap.put("reservaPrice", dataInfo.get("RESERVA_PRICE"));// 预存话费 单位厘
            resourceInfoMap.put("resourcesSrcCode", dataInfo.get("RESOURCES_SRC_CODE"));// 终端来源编码
            resourceInfoMap.put("resourcesSrcName", dataInfo.get("RESOURCES_SRC_NAME"));// 终端来源名称
            resourceInfoMap.put("resourcesSupplyCorp", dataInfo.get("RESOURCES_SUPPLY_CORP"));// 终端供货商名称
            resourceInfoMap.put("resourcesServiceCorp", dataInfo.get("RESOURCES_SERVICE_CORP"));// 终端服务商名称
            resourceInfoMap.put("resourcesColor", dataInfo.get("RESOURCES_COLOR"));// 终端颜色
            resourceInfoMap.put("distributionTag", dataInfo.get("DISTRIBUTION_TAG"));// 铺货标志
            resourceInfoMap.put("resRele", dataInfo.get("RES_RELE"));
            resourceInfoMap.put("terminalType", dataInfo.get("TERMINAL_TYPE"));
            resourceInfoMap.put("terminalTSubType", dataInfo.get("TERMINAL_TSUB_TYPE"));
            resourceInfoMap.put("serviceNumber", dataInfo.get("SERVICE_NUMBER"));
            List<Map> activityList = (List<Map>) dataInfo.get("PRODUCT_AVTIVITY_INFO");// 套包对应的产品活动 暂时没有这种类型
            List<Map> productActivityInfo = new ArrayList<Map>();
            if (!IsEmptyUtils.isEmpty(activityList)) {
                for (Map actMap : activityList) {
                    Map productActMap = new HashMap();
                    productActMap.put("productID", actMap.get("PRODUCT_ID"));
                    productActMap.put("resourcesActivityCode", actMap.get("RESOURCES_ACTIVITY_CODE"));
                    productActMap.put("resourcesActivityPer", actMap.get("RESOURCES_ACTIVITY_PER"));
                    productActivityInfo.add(productActMap);
                }
            }
            resourceInfoMap.put("productActivityInfo", productActivityInfo);
            resourcesRsp.add(resourceInfoMap);
        }
        // }

        Map reMap = new HashMap();
        List<Map> paraList = (List<Map>) resMap.get("PARA");
        if (!IsEmptyUtils.isEmpty(paraList)) {
            List<Map> para = new ArrayList<Map>();
            for (Map paraMap : paraList) {
                Map paraTempMap = new HashMap();
                paraTempMap.put("paraId", paraMap.get("PARA_ID"));
                paraTempMap.put("paraValue", paraMap.get("PARA_VALUE"));
                para.add(paraTempMap);
            }
            reMap.put("para", para);
        }

        reMap.put("resourcesRsp", resourcesRsp);
        exchange.getOut().setBody(reMap);
    }

    /**
     * 自备机终端信息校验
     */
    public void getSelfResourceInfo(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map requestBody = new HashMap();
        Map requestTempBody = new HashMap();
        requestTempBody.putAll(preCommonImplementCbss(msg));
        try {
            requestBody.putAll(NumFaceHeadHelper.creatHead());
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        Map resourceInfo = ((List<Map>) msg.get("resourcesInfo")).get(0);
        Map resTempMap = new HashMap();
        // resTempMap.put("RESOURCES_TYPE",resMap.get("resourcesType"));//可能需要转码 先不下发
        resTempMap.put("RESOURCES_CODE", resourceInfo.get("resourcesCode"));
        resTempMap.put("SUBSCRIBE_ID", new TransIdFromRedisValueExtractor().extract(exchange));
        resTempMap.put("TERMINAL_TYPE", "LP");// 暂时写死
        resTempMap.put("ACTIVITY_TAG", "0");// 叶腾说改成0，后面不校验，06281951
        // resTempMap.put("ACTIVITY_TYPE", "1");
        requestTempBody.remove("DISTRICT");

        Map resultMap = new HashMap();
        requestTempBody.putAll(resTempMap);
        resultMap.put("QRY_SELF_TERM_REQ", requestTempBody);
        requestBody.put("UNI_BSS_BODY", resultMap);
        exchange.getIn().setBody(JSON.toJSON(requestBody));
        CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.nrResCenter.qrySelfTerm");
        nc.dealReturnHead(exchange);
        dealSelfResourceReturn(exchange);
    }

    /**
     * 处理自备机查询的返回结果
     */
    private void dealSelfResourceReturn(Exchange exchange) {
        Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
        Map bodyMap = (Map) outMap.get("UNI_BSS_BODY");
        if (IsEmptyUtils.isEmpty(bodyMap)) {
            throw new EcAopServerBizException("9999", "新资源中心qrySelfTerm服务返回结果为空");
        }
        Map resultMap = (Map) bodyMap.get("QRY_SELF_TERM_RSP");
        if (IsEmptyUtils.isEmpty(resultMap)) {
            throw new EcAopServerBizException("9999", "新资源中心qrySelfTerm服务返回结果为空");
        }
        Map dataInfo = (Map) resultMap.get("RESOURCES_RSP");
        if (IsEmptyUtils.isEmpty(dataInfo)) {
            throw new EcAopServerBizException("9999", "新资源中心qrySelfTerm服务返回结果为空");
        }
        List<Map> resourcesRsp = new ArrayList<Map>();
        Map resourceInfoMap = new HashMap();
        resourceInfoMap.put("resourcesType", dataInfo.get("RESOURCES_TYPE"));// 资源类型
        resourceInfoMap.put("terminalType", dataInfo.get("RESOURCES_TYPE"));// 资源类型(终端下沉接口使用)
        resourceInfoMap.put("resourcesCode", dataInfo.get("RESOURCES_CODE"));// 串码
        resourceInfoMap.put("rscStateCode", dataInfo.get("RSC_STATE_CODE"));// 资源变更结果编码
        if ("0001".equals(dataInfo.get("RSC_STATE_CODE"))) {
            resourceInfoMap.put("rscStateCode", "0002");// 资源变更结果编码
        }
        resourceInfoMap.put("rscStateDesc", dataInfo.get("RSC_STATE_DESC"));// 资源状态描述
        // resourceInfoMap.put("rscStateType", dataInfo.get("STATE"));//资源状态
        resourceInfoMap.put("resourcesBrandCode", dataInfo.get("RESOURCES_BRAND_CODE"));// 品牌
        resourceInfoMap.put("orgDeviceBrandCode", dataInfo.get("RESOURCES_BRAND_CODE"));// 品牌
        resourceInfoMap.put("resourcesBrandName", dataInfo.get("RESOURCES_BRAND_NAME"));// 品牌名称
        resourceInfoMap.put("resourcesModelCode", dataInfo.get("RESOURCES_MODEL_CODE"));// 型号
        resourceInfoMap.put("resourcesModelName", dataInfo.get("RESOURCES_MODEL_NAME"));// 型号名称
        resourceInfoMap.put("machineTypeCode", dataInfo.get("MACHINE_TYPE_CODE"));// 终端机型编码
        resourceInfoMap.put("machineTypeName", dataInfo.get("MACHINE_TYPE_NAME"));// 终端机型名称
        resourceInfoMap.put("salePrice", dataInfo.get("SALE_PRICE"));// 销售价格 单位厘
        resourceInfoMap.put("cost", dataInfo.get("COST"));// 成本价格 单位厘
        // resourceInfoMap.put("cardPrice", dataInfo.get("CARD_PRICE"));//卡费 单位厘
        // resourceInfoMap.put("reservaPrice", dataInfo.get("RESERVA_PRICE"));//预存话费 单位厘
        resourceInfoMap.put("resourcesSrcCode", dataInfo.get("RESOURCES_SRC_CODE"));// 终端来源编码
        resourceInfoMap.put("resourcesSrcName", dataInfo.get("RESOURCES_SRC_NAME"));// 终端来源名称
        resourceInfoMap.put("resourcesSupplyCorp", dataInfo.get("RESOURCES_SUPPLY_CORP"));// 终端供货商名称
        resourceInfoMap.put("resourcesServiceCorp", dataInfo.get("RESOURCES_SERVICE_CORP"));// 终端服务商名称
        resourceInfoMap.put("resourcesColor", dataInfo.get("RESOURCES_COLOR"));// 终端颜色
        /* //resourceInfoMap.put("distributionTag", dataInfo.get("DISTRIBUTION_TAG"));//铺货标志
         * // resourceInfoMap.put("resRele", dataInfo.get("RES_RELE"));
         * //resourceInfoMap.put("terminalType", dataInfo.get("TERMINAL_TYPE"));
         * //resourceInfoMap.put("terminalTSubType", dataInfo.get("TERMINAL_TSUB_TYPE"));
         * //resourceInfoMap.put("serviceNumber", dataInfo.get("SERVICE_NUMBER"));
         * List<Map> activityList = (List<Map>) dataInfo.get("PRODUCT_AVTIVITY_INFO");//套包对应的产品活动 暂时没有这种类型
         * if (!IsEmptyUtils.isEmpty(activityList)) {
         * List<Map> productActivityInfo = new ArrayList<Map>();
         * for (Map actMap : activityList) {
         * Map productActMap = new HashMap();
         * productActMap.put("productID", actMap.get("PRODUCT_ID"));
         * productActMap.put("resourcesActivityCode", actMap.get("RESOURCES_ACTIVITY_CODE"));
         * productActMap.put("resourcesActivityPer", actMap.get("RESOURCES_ACTIVITY_PER"));
         * productActivityInfo.add(productActMap);
         * }
         * resourceInfoMap.put("productActivityInfo", productActivityInfo);
         * } */
        resourcesRsp.add(resourceInfoMap);

        Map reMap = new HashMap();
        List<Map> paraList = (List<Map>) resultMap.get("PARA");
        if (!IsEmptyUtils.isEmpty(paraList)) {
            List<Map> para = new ArrayList<Map>();
            for (Map paraMap : paraList) {
                Map paraTempMap = new HashMap();
                paraTempMap.put("paraId", paraMap.get("PARA_ID"));
                paraTempMap.put("paraValue", paraMap.get("PARA_VALUE"));
                para.add(paraTempMap);
            }
            reMap.put("para", para);
        }

        reMap.put("resourcesRsp", resourcesRsp);
        exchange.getOut().setBody(reMap);
    }

    /**
     * 终端销售信息校验
     */
    public void saleTerminfoCheck(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map requestBody = new HashMap();
        Map requestTempBody = new HashMap();
        requestTempBody.putAll(preCommonImplementCbss(msg));
        try {
            requestBody.putAll(NumFaceHeadHelper.creatHead());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        requestTempBody.put("RESOURCES_CODE", msg.get("resourcesCode"));
        requestTempBody.remove("ACCESS_TYPE");
        Map resultMap = new HashMap();
        resultMap.put("CHK_SALE_TERM_REQ", requestTempBody);
        requestBody.put("UNI_BSS_BODY", resultMap);
        exchange.getIn().setBody(JSON.toJSON(requestBody));
        CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.nrResCenter.chkSaleTerm");
        nc.dealReturnHead(exchange);
        Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
        Map bodyMap = (Map) outMap.get("UNI_BSS_BODY");
        if (IsEmptyUtils.isEmpty(bodyMap)) {
            throw new EcAopServerBizException("9999", "新资源中心chkSaleTerm服务返回结果为空");
        }
        Map termSaleMap = (Map) bodyMap.get("CHK_SALE_TERM_RSP");
        if (IsEmptyUtils.isEmpty(termSaleMap)) {
            throw new EcAopServerBizException("9999", "新资源中心chkSaleTerm服务返回结果为空");
        }
        Map resourcesRsp = new HashMap();
        resourcesRsp.put("resourcesCode", termSaleMap.get("RESOURCES_CODE"));
        resourcesRsp.put("rscStateCode", termSaleMap.get("RSC_STATE_CODE"));
        resourcesRsp.put("rscStateDesc", termSaleMap.get("RSC_STATE_DESC"));
        resourcesRsp.put("machineTypeCode", termSaleMap.get("MACHINE_TYPE_CODE"));
        resourcesRsp.put("machineTypeName", termSaleMap.get("MACHINE_TYPE_NAME"));
        Map reMap = new HashMap();
        reMap.put("resourcesRsp", resourcesRsp);
        exchange.getOut().setBody(reMap);

    }

    /**
     * 终端信息实占
     */
    public void realOccupiedResourceInfo(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map requestBody = new HashMap();
        Map resTempMap = new HashMap();
        resTempMap.putAll(preCommonImplementCbss(msg));
        try {
            requestBody.putAll(NumFaceHeadHelper.creatHead());
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        resTempMap.put("RESOURCES_CODE", msg.get("resourcesCode"));
        resTempMap.put("SUBSCRIBE_ID", String.valueOf(msg.get("ordersId")));
        String number = (String) msg.get("number");
        if (StringUtils.isNotEmpty(number)) {
            resTempMap.put("SERIAL_NUMBER", number);
        }
        resTempMap.remove("DISTRICT");
        // 01-存费送机
        // 02-购机送费
        // 03-合约惠机-默认
        // 04-无
        // 05-订业务送手机
        String activeType = (String) msg.get("activeType");
        if (StringUtils.isNotEmpty(activeType)) {
            // 00: 顺价销售 01：合约销售 02：退机 03：换机 04：无线上网卡社会渠道批销
            resTempMap.put("OPER_TYPE", "01");// 业务类型
            resTempMap.put("ACTIVE_TYPE", activeType);
            if ("05".equals(activeType)) {
                activeType = "04";
                resTempMap.put("ACTIVE_TYPE", activeType);
            }
            else if ("04".equals(activeType)) {
                resTempMap.put("OPER_TYPE", "00");
                resTempMap.remove("ACTIVE_TYPE");
            }

        }
        else {
            resTempMap.put("OPER_TYPE", "00");
        }
        // 终端退换机业务时,转换操作类型
        if (!IsEmptyUtils.isEmpty(msg.get("RES_OPER_TYPE_FLAG"))) {
            resTempMap.put("OPER_TYPE", msg.get("RES_OPER_TYPE_FLAG"));
            // 换机业务需要下发原终端串号和原订单信息,号码信息等
            if ("03".equals(resTempMap.get("OPER_TYPE"))) {
                resTempMap.put("OLD_SUBSCRIBE_ID", msg.get("checkId"));// 原订单ID
                resTempMap.put("OLD_RESOURCES_CODE", msg.get("oldResourcesCode"));// 旧资源标识
            }
        }
        // 业务子类型(合约销售子类型10：自备机、11：非自备机,退机子类型20：Phone退机、21：维修点退机,换机子类型30：Phone换机、31：维修点换机 、32：开机损换机
        resTempMap.put("TRADE_TYPE", msg.get("tradeType"));

        Map resultMap = new HashMap();
        Map resulTemptMap = new HashMap();
        resulTemptMap.put("TERMINAL_SALE_REQ", resTempMap);
        resultMap.put("TERM_SALE_REQ", resulTemptMap);
        requestBody.put("UNI_BSS_BODY", resultMap);
        exchange.getIn().setBody(JSON.toJSON(requestBody));
        CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.nrResCenter.saleRes");
        nc.dealReturnHead(exchange);
        Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
        Map bodyMap = (Map) outMap.get("UNI_BSS_BODY");
        if (IsEmptyUtils.isEmpty(bodyMap)) {
            throw new EcAopServerBizException("9999", "新资源中心termSale服务返回结果为空");
        }
        Map termSaleMap = (Map) bodyMap.get("TERM_SALE_RSP");
        if (IsEmptyUtils.isEmpty(termSaleMap)) {
            throw new EcAopServerBizException("9999", "新资源中心termSale服务返回结果为空");
        }
        Map terminalSaleMap = (Map) termSaleMap.get("TERMINAL_SALE_RSP");
        if (IsEmptyUtils.isEmpty(terminalSaleMap)) {
            throw new EcAopServerBizException("9999", "新资源中心termSale服务返回结果为空");
        }
        String code = (String) terminalSaleMap.get("RESP_CODE");
        if (!"0000".equals(code)) {
            throw new EcAopServerBizException(code, "新资源中心termSale服务返回" + terminalSaleMap.get("RESP_DESC"));
        }
        else if ("qrtb".equals(exchange.getMethodCode())) {
            List<Map> resourcesRspList = new ArrayList<Map>();
            Map resourcesRsp = new HashMap();
            resourcesRsp.put("rscStateCode", "0000");
            resourcesRsp.put("rscStateDesc", "资源可用");
            resourcesRsp.put("rscStateType", "01");
            resourcesRsp.put("resourcesType", msg.get("resourcesType"));
            resourcesRspList.add(resourcesRsp);
            Map reMap = new HashMap();
            reMap.put("resourcesRsp", resourcesRspList);
            exchange.getOut().setBody(reMap);
        }
    }

    /**
     * 终端信息批量释放
     */
    public void bathReleaseResourceInfo(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map requestBody = new HashMap();
        Map requestTempBody = new HashMap();
        requestTempBody.putAll(preCommonImplementCbss(msg));
        try {
            requestBody.putAll(NumFaceHeadHelper.creatHead());
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        List<Map> resourceInfo = (List<Map>) msg.get("resourcesInfo");
        List<Map> resourceInfoList = new ArrayList<Map>();
        // Map resMap = resourceInfo.get(0);
        for (Map resMap : resourceInfo) {
            Map map = new HashMap();
            map.put("RESOURCES_TYPE", resMap.get("resourcesType"));
            map.put("RESOURCES_CODE", resMap.get("resourcesCode"));
            resourceInfoList.add(map);
        }
        requestTempBody.put("RESOURCES_INFO", resourceInfoList);
        Map resTempMap = new HashMap();
        requestTempBody.put("OPER_TYPE", "01");
        String transido = (String) new TransIdFromRedisValueExtractor().extract(exchange);
        requestTempBody.put("SUBSCRIBE_ID", null == transido ? "1234567890" : transido);
        requestTempBody.put("OLD_SUBSCRIBE_ID", null == transido ? "1234567890" : transido);
        requestTempBody.remove("DISTRICT");
        resTempMap.put("TERMINAL_STATE_CHG_BTACH_REQ", requestTempBody);
        requestBody.put("UNI_BSS_BODY", resTempMap);
        exchange.getIn().setBody(JSON.toJSON(requestBody));
        CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.nrResCenter.releaseBtachSer");
        nc.dealReturnHead(exchange);
        Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
        Map bodyMap = (Map) outMap.get("UNI_BSS_BODY");
        if (IsEmptyUtils.isEmpty(bodyMap)) {
            throw new EcAopServerBizException("9999", "新资源中心releaseBtachSer服务返回结果为空");
        }
        Map termSaleMap = (Map) bodyMap.get("TERMINAL_STATE_CHG_BATCH_RSP");
        if (IsEmptyUtils.isEmpty(termSaleMap)) {
            throw new EcAopServerBizException("9999", "新资源中心releaseBtachSer服务返回结果为空");
        }
        List<Map> resultList = (List<Map>) termSaleMap.get("RESOURCES_INFO");
        if (IsEmptyUtils.isEmpty(resultList)) {
            throw new EcAopServerBizException("9999", "新资源中心releaseBtachSer服务返回RESOURCES_INFO结果为空");
        }
        List<Map> resourcesRspList = new ArrayList<Map>();
        for (Map resMap : resultList) {
            Map resourcesRsp = new HashMap();
            resourcesRsp.put("resourcesCode", resMap.get("RESOURCE_CODE"));
            resourcesRsp.put("resourcesType", resMap.get("RESOURCES_TYPE"));
            if ("0000".equals(resMap.get("RESP_CODE"))) {
                resourcesRsp.put("rscStateCode", "0000");
                resourcesRsp.put("rscStateDesc", "资源可用");
                resourcesRsp.put("rscStateType", "01");
            }
            else {
                resourcesRsp.put("rscStateCode", "9999");
                resourcesRsp.put("rscStateDesc", null != resMap.get("RESP_DESC") ? resMap.get("RESP_DESC") : "其它失败原因");
            }
            resourcesRspList.add(resourcesRsp);
        }
        Map reMap = new HashMap();
        reMap.put("resourcesRsp", resourcesRspList);
        exchange.getOut().setBody(reMap);
    }

    /**
     * 终端信息释放
     */
    public void releaseResourceInfo(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map requestBody = new HashMap();
        Map requestTempBody = new HashMap();
        requestTempBody.putAll(preCommonImplementCbss(msg));
        try {
            requestBody.putAll(NumFaceHeadHelper.creatHead());
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        List<Map> resourceInfo = (List<Map>) msg.get("resourcesInfo");
        Map resMap = resourceInfo.get(0);
        Map resTempMap = new HashMap();
        Map resTempMap2 = new HashMap();
        requestTempBody.put("RESOURCES_TYPE", resMap.get("resourcesType"));
        requestTempBody.put("RESOURCES_CODE", resMap.get("resourcesCode"));
        requestTempBody.put("OPER_TYPE", "01");
        String transido = (String) new TransIdFromRedisValueExtractor().extract(exchange);
        requestTempBody.put("SUBSCIBE_ID", null == transido ? "1234567890" : transido);
        requestTempBody.put("OLD_SUBSCRIBE_ID", null == transido ? "1234567890" : transido);
        requestTempBody.remove("DISTRICT");
        // requestTempBody.put("USE_TYPE", "1");
        resTempMap.put("TERMINAL_STATE_CHG_REQ", requestTempBody);
        resTempMap2.put("TERM_STATE_CHG_REQ", resTempMap);
        requestBody.put("UNI_BSS_BODY", resTempMap2);
        exchange.getIn().setBody(JSON.toJSON(requestBody));
        CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.nrResCenter.relaseRes");
        nc.dealReturnHead(exchange);
        Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
        Map bodyMap = (Map) outMap.get("UNI_BSS_BODY");
        if (IsEmptyUtils.isEmpty(bodyMap)) {
            throw new EcAopServerBizException("9999", "新资源中心termStateChg服务返回结果为空");
        }
        Map termSaleMap = (Map) bodyMap.get("TERM_STATE_CHG_RSP");
        if (IsEmptyUtils.isEmpty(termSaleMap)) {
            throw new EcAopServerBizException("9999", "新资源中心termStateChg服务返回结果为空");
        }
        Map terminal_state_chg_rspMap = (Map) termSaleMap.get("TERMINAL_STATE_CHG_RSP");
        if (IsEmptyUtils.isEmpty(terminal_state_chg_rspMap)) {
            throw new EcAopServerBizException("9999", "新资源中心termStateChg服务返回结果为空");
        }
        List<Map> resourcesRspList = new ArrayList<Map>();
        Map resourcesRsp = new HashMap();
        resourcesRsp.put("resourcesCode", resMap.get("resourcesCode"));
        resourcesRsp.put("resourcesType", resMap.get("resourcesType"));
        if ("0000".equals(terminal_state_chg_rspMap.get("RESP_CODE"))) {
            resourcesRsp.put("rscStateCode", "0000");
            resourcesRsp.put("rscStateDesc", "资源可用");
            resourcesRsp.put("rscStateType", "01");
        }
        else {
            resourcesRsp.put("rscStateCode", "9999");
            resourcesRsp.put("rscStateDesc",
                    null != terminal_state_chg_rspMap.get("RESP_DESC") ? terminal_state_chg_rspMap.get("RESP_DESC")
                            : "其它失败原因");
        }
        resourcesRspList.add(resourcesRsp);
        Map reMap = new HashMap();
        reMap.put("resourcesRsp", resourcesRspList);
        exchange.getOut().setBody(reMap);
    }

    /**
     * 终端返销有串码版
     */
    public void resCancelSale(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map requestBody = new HashMap();
        Map resTempMap = new HashMap();
        resTempMap.putAll(preCommonImplementCbss(msg));
        resTempMap.remove("DISTRICT");
        try {
            requestBody.putAll(NumFaceHeadHelper.creatHead());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        Map tempMap = new HashMap();
        Map resTempMap2 = new HashMap();
        resTempMap.put("SUBSCRIBE_ID", msg.get("ordersId"));
        resTempMap.put("OLD_SUBSCRIBE_ID", msg.get("essOrigOrderId"));
        resTempMap.put("RESOURCES_TYPE", "07");
        resTempMap.put("RESOURCES_CODE", msg.get("imei"));
        tempMap.put("TERMINAL_ROLL_BACK_REQ", resTempMap);
        resTempMap2.put("TERM_CLE_SALE_REQ", tempMap);
        requestBody.put("UNI_BSS_BODY", resTempMap2);
        exchange.getIn().setBody(JSON.toJSON(requestBody));
        CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.nrResCenter.termCleSale");
        nc.dealReturnHead(exchange);
        Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
        Map bodyMap = (Map) outMap.get("UNI_BSS_BODY");
        if (IsEmptyUtils.isEmpty(bodyMap)) {
            throw new EcAopServerBizException("9999", "新资源中心termCleSale服务返回结果为空");
        }
        Map termSaleMap = (Map) bodyMap.get("TERM_CLE_SALE_RSP");
        if (IsEmptyUtils.isEmpty(termSaleMap)) {
            throw new EcAopServerBizException("9999", "新资源中心termCleSale服务返回结果为空");
        }
        Map termRollBackMap = (Map) termSaleMap.get("TERMINAL_ROLL_BACK_RSP");
        if (IsEmptyUtils.isEmpty(termRollBackMap)) {
            throw new EcAopServerBizException("9999", "新资源中心termCleSale服务返回结果为空");
        }
        if (!"0000".equals(termRollBackMap.get("RESP_CODE"))) {
            throw new EcAopServerBizException("9999",
                    null == termRollBackMap.get("RESP_DESC") ? "调用新资源中心termCleSale服务返销终端失败"
                            : (String) termRollBackMap.get("RESP_DESC"));
        }
    }

    /**
     * 终端返销预判有串码版
     */
    public void resCancelSalePre(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map requestBody = new HashMap();
        Map resTempMap = new HashMap();
        resTempMap.putAll(preCommonImplementCbss(msg));
        resTempMap.remove("DISTRICT");
        try {
            requestBody.putAll(NumFaceHeadHelper.creatHead());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        Map tempMap = new HashMap();
        Map tempMap2 = new HashMap();
        resTempMap.put("SUBSCRIBE_ID", msg.get("ordersId"));
        resTempMap.put("OLD_SUBSCRIBE_ID", msg.get("essOrigOrderId"));
        resTempMap.put("RESOURCES_TYPE", "07");
        resTempMap.put("RESOURCES_CODE", msg.get("imei"));
        tempMap.put("TERMINAL_ROLL_BACK_PRE_REQ", resTempMap);
        tempMap2.put("TERM_CLE_SALE_PRE_REQ", tempMap);
        requestBody.put("UNI_BSS_BODY", tempMap2);
        exchange.getIn().setBody(JSON.toJSON(requestBody));
        CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.nrResCenter.termCleSalePre");
        nc.dealReturnHead(exchange);
        Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
        Map bodyMap = (Map) outMap.get("UNI_BSS_BODY");
        if (IsEmptyUtils.isEmpty(bodyMap)) {
            throw new EcAopServerBizException("9999", "新资源中心termCleSalePre服务返回结果为空");
        }
        Map termSaleMap = (Map) bodyMap.get("TERM_CLE_SALE_PRE_RSP");
        if (IsEmptyUtils.isEmpty(termSaleMap)) {
            throw new EcAopServerBizException("9999", "新资源中心termCleSalePre服务返回结果为空");
        }
        Map termRollBackMap = (Map) termSaleMap.get("TERMINAL_ROLL_BACK_PRE_RSP");
        if (IsEmptyUtils.isEmpty(termRollBackMap)) {
            throw new EcAopServerBizException("9999", "新资源中心termCleSalePre服务返回结果为空");
        }
        if (!"0000".equals(termRollBackMap.get("RESP_CODE"))) {
            throw new EcAopServerBizException("9999",
                    null == termRollBackMap.get("RESP_DESC") ? "调用新资源中心termCleSalePre服务返销终端失败"
                            : (String) termRollBackMap.get("RESP_DESC"));
        }
    }

    /**
     * 资源验证/查询接口(无串码)
     */
    public Map qryChkSinTerm(Exchange exchange, Map msg) throws Exception {
        List<Map> paraList = new ArrayList<Map>();
        Map preDataMap = NumFaceHeadHelper.creatHead();
        Map QRY_CHK_SIN_TERM_REQ = new HashMap();
        Map checkResReq = new HashMap();
        List<Map> resourceInfo = new ArrayList<Map>();
        Map resourceInfoMap = new HashMap();
        checkResReq.putAll(preCommonImplementCbss(msg));
        checkResReq.put("SUBSCRIBE_ID", msg.get("otoOrderId"));// 外围系统订单ID
        resourceInfoMap.put("RESOURCES_CODE", msg.get("machineTypeCode"));
        resourceInfoMap.put("USE_TYPE", "0");
        resourceInfoMap.put("OPER_TYPE", "0");
        resourceInfoMap.put("OCCUPIED_FLAG", "0");
        resourceInfo.add(resourceInfoMap);
        checkResReq.put("RESOURCES_INFO", resourceInfo);
        QRY_CHK_SIN_TERM_REQ.put("CHK_RES_REQ", checkResReq);
        preDataMap.put("UNI_BSS_BODY", MapUtils.asMap("QRY_CHK_SIN_TERM_REQ", QRY_CHK_SIN_TERM_REQ));
        Exchange qryExchange = ExchangeUtils.ofCopy(preDataMap, exchange);
        CallEngine.numCenterCall(qryExchange, "ecaop.comm.conf.url.api.qryChkSinTerm");
        nc.dealReturnHead(qryExchange);
        Map outMap = (Map) JSON.parse(qryExchange.getOut().getBody().toString());
        Map bodyMap = (Map) outMap.get("UNI_BSS_BODY");
        if (IsEmptyUtils.isEmpty(bodyMap)) {
            throw new EcAopServerBizException("9999", "新资源中心qryChkSinTerm服务返回结果为空");
        }
        Map QRY_CHK_SIN_TERM_RS = (Map) bodyMap.get("QRY_CHK_SIN_TERM_RSP");
        if (IsEmptyUtils.isEmpty(QRY_CHK_SIN_TERM_RS)) {
            throw new EcAopServerBizException("9999", "新资源中心qryChkSinTerm服务返回结果为空");
        }
        Map checkResRsp = (Map) QRY_CHK_SIN_TERM_RS.get("CHECK_RES_RSP");
        if (IsEmptyUtils.isEmpty(checkResRsp)) {
            throw new EcAopServerBizException("9999", "新资源中心qryChkSinTerm服务返回结果为空");
        }
        List<Map> resourceRsp = (List<Map>) checkResRsp.get("RESOURCES_RSP");
        if (IsEmptyUtils.isEmpty(resourceRsp)) {
            throw new EcAopServerBizException("9999", "新资源中心qryChkSinTerm服务返回RESOURCES_RSP为空");
        }
        Map resourceRspMap = resourceRsp.get(0);
        if (!"0000".equals(resourceRspMap.get("RSC_STATE_CODE"))) {
            throw new EcAopServerBizException("9999",
                    null == resourceRspMap.get("RSC_STATE_DESC") ? "调用新资源中心qryChkSinTerm服务返销终端失败"
                            : (String) resourceRspMap.get("RSC_STATE_DESC"));
        }

        String[] paraIds = { "RESOURCES_BRAND_CODE", "RESOURCES_BRAND_NAME", "RESOURCES_MODEL_CODE",
                "RESOURCES_MODEL_NAME", "RESOURCES_SRC_CODE", "RESOURCES_SRC_NAME", "RESOURCES_SUPPLY_CORP",
                "RESOURCES_SERVICE_CROP" };
        String[] paraValues = { String.valueOf(resourceRspMap.get("RESOURCES_BRAND_CODE")),
                String.valueOf(resourceRspMap.get("RESOURCES_BRAND_NAME")),
                String.valueOf(resourceRspMap.get("RESOURCES_MODEL_CODE")),
                String.valueOf(resourceRspMap.get("RESOURCES_MODEL_NAME")),
                String.valueOf(resourceRspMap.get("RESOURCES_SRC_CODE")),
                String.valueOf(resourceRspMap.get("RESOURCES_SRC_NAME")),
                String.valueOf(resourceRspMap.get("RESOURCES_SUPPLY_CORP")),
                String.valueOf(resourceRspMap.get("RESOURCES_SERVICE_CROP")) };
        for (int i = 0; i < paraIds.length; i++) {
            Map para = new HashMap();
            para.put("paraId", paraIds[i]);
            para.put("paraValue", paraValues[i]);
            paraList.add(para);
        }
        msg.put("para", paraList);

        return msg;
    }

    /**
     * 终端销售（无串码）
     */
    public void newSinTerminalSale(Map msg, Exchange exchange) throws Exception {
        Map preDataMap = NumFaceHeadHelper.creatHead();
        Map SIN_TERM_SALE_REQ = new HashMap();
        Map terminalSaleReq = new HashMap();
        terminalSaleReq.putAll(preCommonImplementCbss(msg));
        terminalSaleReq.remove("DISTRICT");// 去掉区县
        terminalSaleReq.put("SUBSCRIBE_ID", msg.get("otoOrderId"));// 外围系统订单ID
        terminalSaleReq.put("RESOURCES_CODE", msg.get("machineTypeCode"));
        terminalSaleReq.put("RESOURCES_NUM", msg.get("saleNum"));
        terminalSaleReq.put("TRADE_TYPE", "3");// 查询销售
        SIN_TERM_SALE_REQ.put("TERMINAL_SALE_REQ", terminalSaleReq);
        preDataMap.put("UNI_BSS_BODY", MapUtils.asMap("SIN_TERM_SALE_REQ", SIN_TERM_SALE_REQ));
        Exchange qryExchange = ExchangeUtils.ofCopy(preDataMap, exchange);
        CallEngine.numCenterCall(qryExchange, "ecaop.comm.conf.url.api.sinTermSale");
        nc.dealReturnHead(qryExchange);
        Map rspInfo = (Map) JSON.parse(qryExchange.getOut().getBody().toString());
        Map rspBody = (Map) rspInfo.get("UNI_BSS_BODY");
        if (IsEmptyUtils.isEmpty(rspBody))
            throw new EcAopServerBizException("9999", "新资源中心未返回sinTermSale信息");
        Map SIN_TERM_SALE_RSP = (Map) rspBody.get("SIN_TERM_SALE_RSP");
        if (IsEmptyUtils.isEmpty(SIN_TERM_SALE_RSP)) {
            throw new EcAopServerBizException("9999", "新资源中心sinTermSale服务返回结果为空");
        }
        Map TERMINAL_SALE_RSP = (Map) SIN_TERM_SALE_RSP.get("TERMINAL_SALE_RSP");
        if (IsEmptyUtils.isEmpty(TERMINAL_SALE_RSP)) {
            throw new EcAopServerBizException("9999", "新资源中心sinTermSale服务返回结果为空");
        }
        if (!"0000".equals(TERMINAL_SALE_RSP.get("RESP_CODE"))) {
            throw new EcAopServerBizException("9999",
                    null == TERMINAL_SALE_RSP.get("RESP_DESC") ? "调用新资源中心sinTermSale服务返销终端失败"
                            : (String) TERMINAL_SALE_RSP.get("RESP_DESC"));
        }
    }

    /**
     * 返销预判（无串码）
     */
    public void newSinTermCleSalePre(Map msg, Exchange exchange) throws Exception {
        Map preDataMap = NumFaceHeadHelper.creatHead();
        Map SIN_TERM_CLE_SALE_PRE_REQ = new HashMap();
        Map rollBacePerReq = new HashMap();
        rollBacePerReq.putAll(preCommonImplementCbss(msg));
        rollBacePerReq.remove("DISTRICT");// 去掉区县
        rollBacePerReq.put("SUBSCRIBE_ID", msg.get("otoOrderId"));// 外围系统订单ID
        rollBacePerReq.put("OLD_SUBSCRIBE_ID", msg.get("subLogId"));// 原销售订单
        rollBacePerReq.put("RESOURCES_CODE", msg.get("machineTypeCode"));
        SIN_TERM_CLE_SALE_PRE_REQ.put("TERMINAL_ROLL_BACK_PER_REQ", rollBacePerReq);
        preDataMap.put("UNI_BSS_BODY", MapUtils.asMap("SIN_TERM_CLE_SALE_PRE_REQ", SIN_TERM_CLE_SALE_PRE_REQ));
        Exchange qryExchange = ExchangeUtils.ofCopy(preDataMap, exchange);
        CallEngine.numCenterCall(qryExchange, "ecaop.comm.conf.url.api.sinTermCleSalePre");
        nc.dealReturnHead(qryExchange);
        Map rspInfo = (Map) JSON.parse(qryExchange.getOut().getBody().toString());
        Map rspBody = (Map) rspInfo.get("UNI_BSS_BODY");

        if (IsEmptyUtils.isEmpty(rspBody))
            throw new EcAopServerBizException("9999", "新资源中心未返回sinTermCleSalePre信息");
        Map SIN_TERM_CLE_SALE_PRE_RSP = (Map) rspBody.get("SIN_TERM_CLE_SALE_PRE_RSP");
        if (IsEmptyUtils.isEmpty(SIN_TERM_CLE_SALE_PRE_RSP)) {
            throw new EcAopServerBizException("9999", "新资源中心sinTermCleSalePre服务返回结果为空");
        }
        Map TERMINAL_ROLL_BACK_PRE_RSP = (Map) SIN_TERM_CLE_SALE_PRE_RSP.get("TERMINAL_ROLL_BACK_PRE_RSP");
        if (IsEmptyUtils.isEmpty(TERMINAL_ROLL_BACK_PRE_RSP)) {
            throw new EcAopServerBizException("9999", "新资源中心sinTermCleSalePre服务返回结果为空");
        }
        if (!"0000".equals(TERMINAL_ROLL_BACK_PRE_RSP.get("RESP_CODE"))) {
            throw new EcAopServerBizException("9999",
                    null == TERMINAL_ROLL_BACK_PRE_RSP.get("RESP_DESC") ? "调用新资源中心sinTermCleSalePre服务返销预判失败"
                            : (String) TERMINAL_ROLL_BACK_PRE_RSP.get("RESP_DESC"));
        }
        msg.put("AGENT_CHANNEL_ID", TERMINAL_ROLL_BACK_PRE_RSP.get("AGENT_CHANNEL_ID"));// 返销接口用到（非必传）
    }

    /**
     * 终端返销（无串码）
     */
    public void newSinTerminalResale(Map msg, Exchange exchange) throws Exception {
        Map preDataMap = NumFaceHeadHelper.creatHead();
        Map SIN_TERM_CLE_SALE_REQ = new HashMap();
        Map cancelSaleReq = new HashMap();
        cancelSaleReq.putAll(preCommonImplementCbss(msg));
        cancelSaleReq.remove("DISTRICT");// 去掉区县
        cancelSaleReq.put("SUBSCRIBE_ID", msg.get("otoOrderId"));// 外围系统订单ID
        cancelSaleReq.put("OLD_SUBSCRIBE_ID", msg.get("subLogId"));// 原销售订单
        cancelSaleReq.put("RESOURCES_CODE", msg.get("machineTypeCode"));
        cancelSaleReq.put("TRADE_TYPE", "3");// 返销
        cancelSaleReq.put("RESOURCES_NUM", msg.get("saleNum"));
        cancelSaleReq.put("AGENT_CHANNEL_ID", msg.get("AGENT_CHANNEL_ID"));
        SIN_TERM_CLE_SALE_REQ.put("TERMINAL_ROLL_BACK_REQ", cancelSaleReq);
        preDataMap.put("UNI_BSS_BODY", MapUtils.asMap("SIN_TERM_CLE_SALE_REQ", SIN_TERM_CLE_SALE_REQ));
        Exchange qryExchange = ExchangeUtils.ofCopy(preDataMap, exchange);
        CallEngine.numCenterCall(qryExchange, "ecaop.comm.conf.url.api.sinTermCleSale");
        nc.dealReturnHead(qryExchange);
        Map rspInfo = (Map) JSON.parse(qryExchange.getOut().getBody().toString());
        Map rspBody = (Map) rspInfo.get("UNI_BSS_BODY");

        if (IsEmptyUtils.isEmpty(rspBody))
            throw new EcAopServerBizException("9999", "新资源中心未返回sinTermCleSale信息");
        Map SIN_TERM_CLE_SALE_RSP = (Map) rspBody.get("SIN_TERM_CLE_SALE_RSP");
        if (IsEmptyUtils.isEmpty(SIN_TERM_CLE_SALE_RSP)) {
            throw new EcAopServerBizException("9999", "新资源中心sinTermCleSale服务返回结果为空");
        }
        Map TERMINAL_ROLL_BACK_RSP = (Map) SIN_TERM_CLE_SALE_RSP.get("TERMINAL_ROLL_BACK_RSP");
        if (IsEmptyUtils.isEmpty(TERMINAL_ROLL_BACK_RSP)) {
            throw new EcAopServerBizException("9999", "新资源中心sinTermCleSale服务返回结果为空");
        }
        if (!"0000".equals(TERMINAL_ROLL_BACK_RSP.get("RESP_CODE"))) {
            throw new EcAopServerBizException("9999",
                    null == TERMINAL_ROLL_BACK_RSP.get("RESP_DESC") ? "调用新资源中心sinTermCleSale服务返销终端失败"
                            : (String) TERMINAL_ROLL_BACK_RSP.get("RESP_DESC"));
        }
    }

    /**
     * 终端库存查询接口
     */
    public void termStockQry(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map requestBody = new HashMap();
        Map resTempMap = new HashMap();
        resTempMap.putAll(preCommonImplementCbss(msg));
        try {
            requestBody.putAll(NumFaceHeadHelper.creatHead());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        resTempMap.remove("DISTRICT");
        List<Map> resourceInfo = new ArrayList<Map>();
        Map map = new HashMap();
        map.put("MACHINE_TYPE_CODE", msg.get("machineTypeCode"));
        resourceInfo.add(map);
        resTempMap.put("RESOURCES_INFO", resourceInfo);
        Map tempMap = new HashMap();
        tempMap.put("TERM_STOCK_QRY_REQ", resTempMap);
        requestBody.put("UNI_BSS_BODY", tempMap);
        exchange.getIn().setBody(JSON.toJSON(requestBody));
        CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.nrResCenter.termStockQry");
        nc.dealReturnHead(exchange);
        Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
        Map bodyMap = (Map) outMap.get("UNI_BSS_BODY");
        if (IsEmptyUtils.isEmpty(bodyMap)) {
            throw new EcAopServerBizException("9999", "新资源中心termStockQry服务返回结果为空");
        }
        Map term_stock_qry_rsp = (Map) bodyMap.get("TERM_STOCK_QRY_RSP");
        if (IsEmptyUtils.isEmpty(term_stock_qry_rsp)) {
            throw new EcAopServerBizException("9999", "新资源中心termStockQry服务返回结果为空");
        }
        if (!"0000".equals(term_stock_qry_rsp.get("RESP_CODE"))) {
            throw new EcAopServerBizException("9999",
                    null == term_stock_qry_rsp.get("RESP_CODE") ? "调用新资源中心termStockQry服务失败"
                            : (String) term_stock_qry_rsp.get("RESP_DESC"));
        }
        List<Map> resourcesInfoList = (List<Map>) term_stock_qry_rsp.get("RESOURCES_INFO");
        if (IsEmptyUtils.isEmpty(resourcesInfoList)) {
            throw new EcAopServerBizException("9999", "新资源中心termStockQry服务返回结果为空");
        }
        String resourcesCount = (String) resourcesInfoList.get(0).get("RESOURCES_COUNT");
        if (StringUtils.isEmpty(resourcesCount)) {
            throw new EcAopServerBizException("9999", "新资源中心termStockQry服务返回终端可用数量为空");
        }
        Map outNewMap = new HashMap();
        outNewMap.put("count", resourcesCount);
        exchange.getOut().setBody(outNewMap);

    }

    /**
     * 终端自动调拨接口
     */
    public void termAutoTransfer(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        List<Map> resourceInfo = (List<Map>) msg.get("resourcesInfo");
        for (Map resMap : resourceInfo) {
            if ("1".equals(resMap.get("allocationFlag"))) {
                Map requestBody = new HashMap();
                try {
                    requestBody.putAll(NumFaceHeadHelper.creatHead());
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                Map map = new HashMap();
                map.put("CHANNLE_ID", msg.get("channelId"));
                map.put("RS_IMEI", resMap.get("resourcesCode"));
                Map tempMap = new HashMap();
                tempMap.put("AUTO_TRANSFER_REQ", map);
                requestBody.put("UNI_BSS_BODY", tempMap);
                exchange.getIn().setBody(JSON.toJSON(requestBody));
                CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.nrResCenter.doTransfer");
                nc.dealReturnHead(exchange);
                Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
                Map bodyMap = (Map) outMap.get("UNI_BSS_BODY");
                if (IsEmptyUtils.isEmpty(bodyMap)) {
                    throw new EcAopServerBizException("9999", "新资源中心doTransfer服务返回结果为空");
                }
                Map term_stock_qry_rsp = (Map) bodyMap.get("AUTO_TRANSFER_RSP");
                if (IsEmptyUtils.isEmpty(term_stock_qry_rsp)) {
                    throw new EcAopServerBizException("9999", "新资源中心doTransfer服务返回结果为空");
                }
                if (!"0000".equals(term_stock_qry_rsp.get("CODE"))) {
                    throw new EcAopServerBizException("9999",
                            null == term_stock_qry_rsp.get("CODE") ? "调用新资源中心doTransfer服务调拨终端"
                                    + resMap.get("resourcesCode") + "失败" : (String) term_stock_qry_rsp.get("DESC"));
                }
                else {
                    Thread.sleep(1000);
                }
            }
        }

    }

    /**
     * 产商品校验接口
     */
    public Map checkRcToProAct(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        // 拼装请求报文
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
        requestTempBody.put("OPERATOR_ID", msg.get("operatorId"));
        requestTempBody.put("PROVINCE", msg.get("province"));
        requestTempBody.put("EPARCHY_CODE", msg.get("city"));
        requestTempBody.put("CITY_CODE", msg.get("district"));
        requestTempBody.put("CHANNEL_ID", msg.get("channelId"));
        requestTempBody.put("CHANNEL_TYPE", msg.get("channelType"));
        // 报文体其他参数
        List<Map> activityInfo = (List<Map>) msg.get("activityInfo");
        if (null == activityInfo || 0 == activityInfo.size()) {
            throw new EcAopServerBizException("9999", "老用户优惠购机活动信息必传");
        }
        List<Map> productInfoList = (List<Map>) msg.get("productInfo");
        if (null == productInfoList || 0 == productInfoList.size()) {
            throw new EcAopServerBizException("9999", "老用户优惠购机产品信息必传");
        }
        for (Map activity : activityInfo) {
            Object resourceCode = activity.get("resourcesCode");
            if (null == resourceCode || "".equals(resourceCode)) {
                continue;
            }
            requestTempBody.put("RS_IMEI", resourceCode.toString());
            requestTempBody.put("ACT_PLAN_ID", activity.get("actPlanId"));
        }
        for (Map productInfo : productInfoList) {
            if ("1".equals(productInfo.get("productMode"))) {
                requestTempBody.put("PRODUCT_ID", productInfo.get("productId"));
            }
        }
        // CHECK_TYPE:校验产品/活动/终端 资源中心-07,产商品-06.资源中心的07对应产商品的06,因此要传07
        // requestTempBody.put("CHCK_TYPE", "06");
        requestTempBody.put("CHCK_TYPE", "07");
        Map resultMap = new HashMap();
        resultMap.put("QRYRCTOPROACT_REQ", requestTempBody);
        request.put("UNI_BSS_BODY", resultMap);

        Exchange exchangeResCenter = ExchangeUtils.ofCopy(exchange, msg);
        exchangeResCenter.getIn().setBody(JSON.toJSON(request));
        // 调用资源中心获取数据
        try {
            CallEngine.numCenterCall(exchangeResCenter, "ecaop.comm.conf.url.api.qryRcToProAct");
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用资源中心获取终端信息失败!" + e.getMessage());
        }
        nc.dealReturnHead(exchangeResCenter);
        Map outMap = (Map) JSON.parse(exchangeResCenter.getOut().getBody().toString());
        Map bodyMap = (Map) outMap.get("UNI_BSS_BODY");
        if (IsEmptyUtils.isEmpty(bodyMap)) {
            throw new EcAopServerBizException("9999", "资源中心未返回终端信息");
        }
        Map rspMap = (Map) bodyMap.get("QRYRCTOPROACT_RSP");
        if (IsEmptyUtils.isEmpty(rspMap)) {
            throw new EcAopServerBizException("9999", "资源中心未返回终端信息");
        }
        if (!"0000".equals(rspMap.get("CODE"))) {
            throw new EcAopServerBizException("9999", "资源中心接口返回:" + rspMap.get("DESC"));
        }
        Map resData = (Map) rspMap.get("DATA");
        if (IsEmptyUtils.isEmpty(resData)) {
            throw new EcAopServerBizException("9999", "资源中心未返回产商品信息");
        }
        List<Map> productCatList = (List<Map>) resData.get("PRODUCT_ACT");
        if (IsEmptyUtils.isEmpty(resData)) {
            throw new EcAopServerBizException("9999", "资源中心未返回产品信息");
        }
        Map resOut = new HashMap();
        for (Map productMap : productCatList) {
            if (requestTempBody.get("PRODUCT_ID").equals(productMap.get("PRODUCT_ID"))) {
                resOut.put("resProActData", productMap);
                break;
            }
        }
        if (resOut.get("resProActData") == null) {
            throw new EcAopServerBizException("9999", "资源中心未返回产品" + requestTempBody.get("PRODUCT_ID") + "的详细信息");
        }
        return resOut;
    }

}
