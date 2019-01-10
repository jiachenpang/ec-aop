package com.ailk.ecaop.biz.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.product.DateUtils;
import com.ailk.ecaop.common.processor.ChangeCertTypeProcessor;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("checkCustInfo")
public class CheckCustInfo4AddUserCheckProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        LanUtils lan = new LanUtils();
        Map inputInfo = exchange.getIn().getBody(Map.class);
        boolean isString = inputInfo.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) inputInfo.get("msg"));
        }
        else {
            msg = (Map) inputInfo.get("msg");
        }
        String addType = (String) msg.get("addType");
        String province = (String) msg.get("province");
        String serialNumber = (String) msg.get("serialNumber");
        try {
            preParam4ThreePart(exchange.getIn().getBody(Map.class));
            lan.preData("ecaop.trade.cbss.checkUserParametersMapping", exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
            lan.xml2JsonNoError("ecaop.trades.cbss.threePart.template", exchange);
        }
        catch (EcAopServerBizException e) {
            throw new EcAopServerBizException("9999", e.getMessage());
        }
        Map out = exchange.getOut().getBody(Map.class);
        String code = (String) out.get("code");
        if (!"3".equals(addType) && !"0000".equals(code)) {
            throw new EcAopServerBizException(code, (String) out.get("detail"));
        }
        if ("0000".equals(code) || "8888".equals(code)) {
            out.remove("code");
            out.remove("detail");
        }
        else {
            throw new EcAopServerBizException(code, (String) out.get("detail"));
        }
        Map retCustInfoMap = getCustInfo(exchange);
        Map retUserInfoMap = getUserInfo(exchange, addType, serialNumber);
        String certTypeCode = ChangeCertTypeProcessor.certTypeCbss2Mall((String) retCustInfoMap.get("certTypeCode"));
        Map retMap = new HashMap();
        retMap.put("custId", retCustInfoMap.get("custId"));
        retMap.put("custName", retCustInfoMap.get("custName"));
        if ("74".equals(province))
        {
            retMap.put("certTypeCode", certTypeCode);
        }
        else
        {
            retMap.put("certTypeCode", retCustInfoMap.get("certTypeCode"));
        }
        retMap.put("certCode", retCustInfoMap.get("certCode"));
        retMap.put("productDesc", retUserInfoMap.get("productName"));
        exchange.getOut().setBody(retMap);
    }

    private Map getUserInfo(Exchange exchange, String addType, String serialNumber) {
        // 主卡标志
        boolean isMainCard = false;
        Map retThreePartMap = new HashMap();
        Object body = exchange.getOut().getBody();
        if (body instanceof String) {
            retThreePartMap = JSON.parseObject((String) body);
        }
        else {
            retThreePartMap = exchange.getOut().getBody(Map.class);
        }
        List<Map> retList = (ArrayList<Map>) retThreePartMap.get("userInfo");
        if (null == retList || 0 == retList.size()) {
            throw new EcAopServerBizException("9999", "三户信息校验接口:核心系统未返回用户信息");
        }
        List<Map> uuInfo = (ArrayList<Map>) retList.get(0).get("uuInfo");
        // 主卡判断
        if (null != uuInfo && !uuInfo.isEmpty()) {
            for (Map tempMap : uuInfo) {
                if ("1".equals(tempMap.get("roleCodeB")) && serialNumber.equals(tempMap.get("serialNumberB") + "")) {
                    isMainCard = true;
                }
            }
        }
        if (null != uuInfo && !uuInfo.isEmpty() && !"3".equals(addType)) {
            for (Map temp : uuInfo) {
                if ("ZF".equals(temp.get("relationTypeCode"))) {
                    if (temp.get("endDate").toString().compareTo(DateUtils.getMonthFirstDay()) > 0) {
                        throw new EcAopServerBizException("0002", "存在主副卡业务，不允许纳入融合产品");
                    }
                }
            }
        }
        List<Map> productInfo = (ArrayList<Map>) retList.get(0).get("productInfo");
        for (Map product : productInfo) {
            if (!"50".equals(product.get("productMode"))) {
                continue;
            }
            String productInactiveTime = String.valueOf(product.get("productInactiveTime"));
            String productActiveTime = String.valueOf(product.get("productActiveTime"));
            if (Long.valueOf(GetDateUtils.getNextMonthFirstDayFormat()) < Long.valueOf(productInactiveTime)
                    && "2".equals(addType)) {
                throw new EcAopServerBizException("0001", "存在合约计划，不能办理共享版");
            }
            if (Long.valueOf(GetDateUtils.getNextMonthFirstDayFormat()) <= Long.valueOf(productActiveTime)
                    && !isMainCard && "2".equals(addType)) {
                //针对组合和组合优化版，不返回0003的错误码
                throw new EcAopServerBizException("0003", "该用户存在预约的基本产品不能加入融合");
            }
        }

        return retList.get(0);
    }

    /**
     * 获取三户返回客户信息
     * 
     * @param exchange
     * @return Map
     */
    private Map getCustInfo(Exchange exchange) {
        Map retThreePartMap = new HashMap();
        Object body = exchange.getOut().getBody();
        if (body instanceof String) {
            retThreePartMap = JSON.parseObject((String) body);
        }
        else {
            retThreePartMap = exchange.getOut().getBody(Map.class);
        }
        List<Map> retList = (ArrayList<Map>) retThreePartMap.get("custInfo");
        if (null == retList || 0 == retList.size()) {
            throw new EcAopServerBizException("9999", "三户信息校验接口:核心系统未返回客户信息");
        }
        return retList.get(0);
    }

    private void preParam4ThreePart(Map body) {
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        List<Map> para = new ArrayList<Map>();
        para.add(LanUtils.createPara("RELATION_TYPE_CODE", "8800"));
        para.add(LanUtils.createPara("CHK_TYPE", "1"));
        para.add(LanUtils.createPara("OPER_TYPE", "1"));
        // COMP_PRODUCT_ID纳入的产品编码，目前cb条件可以写死，但是不能不填，这个字段他们sql里面没用到
        // 邱小海 瞎填不要紧但是一定要填。代码取了。瞎用了
        para.add(LanUtils.createPara("COMP_PRODUCT_ID", "89017299"));
        para.add(LanUtils.createPara("ROLE_CODE_B", ""));
        msg.put("para", para);
        // 海经理让去掉areaCode
        msg.remove("areaCode");
        msg.put("tradeTypeCode", "0340");
        msg.put("getMode", "101000000000000000000000000000");
        body.put("msg", msg);
    }
}
