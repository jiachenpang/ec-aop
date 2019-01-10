package com.ailk.ecaop.biz.sub.gdjk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.request.EcAopRequestParamException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.numCenter.ChangeNumberStatusForNumberCenterProcessor;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.NumCenterUtils;
import com.ailk.ecaop.common.utils.ReleaseResUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("GDOpenAccountNumberCenter")
public class GDOpenAccountNumberCenter extends BaseAopProcessor implements ParamsAppliable {
    private static final String[] PARAM_ARRAY = { "ecaop.trades.core.simpleCheckUserInfo.ParametersMapping",
            "ecaop.gdjk.opnc.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[2];
    ChangeNumberStatusForNumberCenterProcessor cs = new ChangeNumberStatusForNumberCenterProcessor();
    private String sysCode = "";

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void process(Exchange exchange) throws Exception {
        System.out.println("GDOpenAccountNumberCenter进入。。。");
        Map body = exchange.getIn().getBody(Map.class);
        Map tempBody = new HashMap(body);
        sysCode = new NumCenterUtils().changeSysCode(exchange);
        //选占及号码状态验证
        selectAndCheckNumber(exchange);
        try {
            //卡号中心资源预占
            exchange.getIn().setBody(tempBody);
            preemptedNumberCenter(exchange);
        } catch (Exception e) {
            //预占失败，号码释放
            exchange.getIn().setBody(tempBody);
            releaseNumber(exchange);
            throw e;
        }
        //终端预占
        LanUtils lan = new LanUtils();
        ReleaseResUtils rr = new ReleaseResUtils();
        try {
            // 进行终端预占
            CheckResProcessor crp = new CheckResProcessor();
            exchange.getIn().setBody(tempBody);
            crp.process(exchange);
            // 产品类型转化
            boolean isString = body.get("msg") instanceof String;
            Map msgMap = isString ? JSON.parseObject((String) body.get("msg")) : (Map) body.get("msg");
            changeProdType(msgMap);
            lan.preData(pmp[1], exchange);//ecaop.gdjk.opnc.ParametersMapping
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
            lan.xml2Json4ONS("ecaop.gdjk.opnc.template", exchange);
        } catch (Exception e) {
            //开户失败，号码释放
            exchange.getIn().setBody(tempBody);
            releaseNumber(exchange);
            throw e;
        } finally {// 释放终端信息
            Map result = exchange.getOut().getBody(Map.class);
            exchange.getIn().setBody(tempBody);
            rr.releaseTerminal(exchange);
            exchange.getOut().setBody(result);
        }
    }

    /**
     * 选占及号码状态验证，选占-》简单版查询-》CB查询
     * */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void selectAndCheckNumber(Exchange exchange) throws Exception {
        System.out.println("GDOpenAccountNumberCenter-selectAndCheckNumber");
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        if (null == msg.get("numId")) {
            return;
        }
        if (!"opnc".equals(exchange.getMethodCode())) {
            return;
        }
        checkInputParam(msg);
        List<Map> numIds = (List<Map>) msg.get("numId");
        List<Map> userInfo = (List<Map>) msg.get("userInfo");
        for (int i = 0; i < numIds.size(); i++) {
            Exchange tempExchange = ExchangeUtils.ofCopy(exchange, msg);
            Map temp = new HashMap(body);
            Map msg2 = (Map) temp.get("msg");
            String serialNumber = (String) numIds.get(i).get("serialNumber");
            String groupFlag = (String) userInfo.get(i).get("groupFlag");
            /**
             * businessType  serialNumber sysCode proKey occupiedTime(有默认30)
             * businessType业务分类01：公众02：集团
             * groupFlag(userInfo)集团标识0：非集团用户1：集团用户
             * */
            msg2.put("serialNumber", serialNumber);
            msg2.put("businessType", "1".equals(groupFlag + "") ? "02" : "01");//设默认为01公众
            msg2.put("proKey", numIds.get(i).get("proKey"));
            msg2.put("occupiedTime", "30");
            msg2.put("sysCode", sysCode);
            temp.put("msg", msg2);
            //号卡中心选占
            Map req = preDataForSelectedNum(exchange, msg2);
            exchange.getIn().setBody(req);
            System.out.println("GDOpenAccountNumberCenter-selectAndCheckNumber:req:" + req);
            CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.numbercenter.selectedNum");
            //处理结果
            cs.dealNumberCenterReturn(exchange, "SELECTED_NUM_RSP", "选占");
           /* //bss号码状态查询BssSimpleCheckUserInfoProCessor BSS简单用户查询接口
            System.out.println("GDOpenAccountNumberCenter-BSS简单用户查询接口");
            tempExchange.getIn().setBody(temp);
            LanUtils lan = new LanUtils();
            lan.preData(pmp[0], tempExchange);//ecaop.trades.core.simpleCheckUserInfo.ParametersMapping
            CallEngine.wsCall(tempExchange, "ecaop.comm.conf.url.osn.services.usrser");
            lan.xml2JsonNoError("ecaop.trades.core.simpleCheckUserInfo.template", tempExchange);
           // cs.dealBssReturn(tempExchange, serialNumber);//处理结果------

            System.out.println("GDOpenAccountNumberCenter-CB客户资料查询");*/
            //根据号码调CB客户资料查询--公共方法
            msg2.put("operateType", "8");//客户信息认证类型：, 1，证件类型+证件号码；, 2，用户号码+服务密码；, 3，卡类型（包括俱乐部会员卡、星级客户卡）、卡号；, 
            //4，SIM卡号（预付费用户）+ 用户密码, 5，账户合同号, 6，银行托收账号, 7，客户编号, 8，用户号码, 9，证件类型+证件号码+服务号码, 10，集团编码+服务密码, 11，详单认证, 12，客户名称, 13，集团证件类型+集团证件方式, 
            //14，集团编号(GROUP_ID), 15，集团编号(GROUP_ID)+密码方式, 16,集团客户编码(CUTS_ID)
            temp.put("msg", msg2);
            tempExchange.getIn().setBody(temp);
            //cs.dealCBQryReturn(TradeManagerUtils.custCheck4G(tempExchange), serialNumber);//处理结果
            System.out.println("GDOpenAccountNumberCenter-CB客户资料查询结束");
            cs.insertCallNumberCenterRecord(msg2, req, "选占", "调号卡中心选占接口");
        }
    }

    /**
     * 检查输入参数
     * */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void checkInputParam(Map map) {
        if (null == map.get("customerInfo")) {
            return;
        }
        List<Map> customerInfo = (List<Map>) map.get("customerInfo");
        for (Map m : customerInfo) {// 仅当是老客户时才强检验custId
            if ("1".equals(m.get("custType")) && null == m.get("custId")) {
                throw new EcAopRequestParamException("老客户的custId是必传的！");
            }
        }
        List<Map> numId = (List<Map>) map.get("numId");
        List<Map> userInfo = (List<Map>) map.get("userInfo");
        if (numId.size() != userInfo.size()) {
            throw new EcAopRequestParamException("用户信息和号码无法匹配！");
        }
    }

    /**
     * 号卡中心预占ecaop.trades.opnc.numCenter.occupy.template
     * @throws Exception 
     * */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void preemptedNumberCenter(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        if (null == msg.get("numId")) {
            return;
        }
        if (!"opnc".equals(exchange.getMethodCode())) {
            return;
        }
        List<Map> numIds = (List<Map>) msg.get("numId");
        for (int i = 0; i < numIds.size(); i++) {
            //Map temp = new HashMap(body);
            //Map msg2 = (Map) temp.get("msg");
            String serialNumber = (String) numIds.get(i).get("serialNumber");
            msg.put("serialNumber", serialNumber);
            msg.put("sysCode", sysCode);
            body.put("msg", msg);
            Map req = preDataForPreemptedNum(exchange, msg);
            exchange.getIn().setBody(req);
            CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.numbercenter.preemptedNum");
            //处理结果
            cs.dealNumberCenterReturn(exchange, "PREEMPTED_NUM_RSP", "预占");
            cs.insertCallNumberCenterRecord(msg, req, "预占", "调号卡中心预占接口");
        }
    }

    /**号卡中心号码释放
     * @throws Exception */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void releaseNumber(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map temp = new HashMap(body);
        Map msg = (Map) temp.get("msg");
        if (null == msg.get("numId")) {
            return;
        }
        msg.put("sysCode", sysCode);
        temp.put("msg", msg);
        //号卡中心释放
        Map req = preDataForReleaseNumber(exchange, msg);//参数名未知，暂为req
        exchange.getIn().setBody(req);
        CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.numbercenter.relSelectionNum");
        //处理结果
        cs.dealNumberCenterReturn(exchange, "REL_SELECTION_NUM _RSP", "释放");
        cs.insertCallNumberCenterRecord(msg, req, "释放", "调号卡中心手工释放接口");
    }

    /**
     * 产品类型转换
     * 
     * @param inMap
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void changeProdType(Map inMap) {
        List<Map> userInfolList = (List) inMap.get("userInfo");
        for (Map userMap : userInfolList) {
            List<Map> productList = (List) userMap.get("product");
            for (Map productMap : productList) {
                String prodMode = productMap.get("productMode").toString();
                if ("2".equals(prodMode)) {
                    productMap.put("productMode", "0");
                }
            }
        }
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

    /**
     * @throws Exception 
     * */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Map preDataForSelectedNum(Exchange exchange, Map msg) throws Exception {
        Map SELECTED_NUM_REQ = cs.createREQ(msg);
        SELECTED_NUM_REQ.put("SERIAL_NUMBER", msg.get("serialNumber"));
        SELECTED_NUM_REQ.put("PRO_KEY", msg.get("proKey"));
        Map req = cs.createHeadAndAttached(exchange.getAppkey());
        req.put("UNI_BSS_BODY", MapUtils.asMap("SELECTED_NUM_REQ", SELECTED_NUM_REQ));
        return req;
    }

    /**
     * 合并PREEMPTED_NUM_REQ请求JSON
     * @throws Exception 
     * */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Map preDataForPreemptedNum(Exchange exchange, Map msg) throws Exception {
        Map PREEMPTED_NUM_REQ = cs.createREQ(msg);
        PREEMPTED_NUM_REQ.put("SERIAL_NUMBER", msg.get("serialNumber"));
        Map req = cs.createHeadAndAttached(exchange.getAppkey());
        req.put("UNI_BSS_BODY", MapUtils.asMap("PREEMPTED_NUM_REQ", PREEMPTED_NUM_REQ));
        return req;
    }

    /**
     * 合并REL_SELECTION_NUM_REQ请求JSON
     * @throws Exception 
     * */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Map preDataForReleaseNumber(Exchange exchange, Map msg) throws Exception {
        Map REL_SELECTION_NUM_REQ = cs.createREQ(msg);
        List SELNUM_LIST = new ArrayList<Map>();
        Map list = MapUtils.asMap("SERIAL_NUMBER", msg.get("serialNumber"), "PRO_KEY", msg.get("proKey"));
        SELNUM_LIST.add(list);
        REL_SELECTION_NUM_REQ.put("SELNUM_LIST", SELNUM_LIST);
        Map req = cs.createHeadAndAttached(exchange.getAppkey());
        req.put("UNI_BSS_BODY", MapUtils.asMap("REL_SELECTION_NUM_REQ", REL_SELECTION_NUM_REQ));
        return req;
    }

    /**
     * 准备REQ中常用参数
     * */
    /*
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Map createREQ(Exchange exchange, Map msg) {
    Map REQ = new HashMap();
    REQ.put("STAFF_ID", msg.get("operatorId"));
    REQ.put("PROVINCE_CODE", msg.get("province"));
    REQ.put("CITY_CODE", msg.get("city"));
    REQ.put("DISTRICT_CODE", msg.get("district"));
    REQ.put("CHANNEL_ID", msg.get("channelId"));
    REQ.put("CHANNEL_TYPE", msg.get("channelType"));
    REQ.put("SYS_CODE", new NumCenterUtils().changeSysCode(exchange));
    return REQ;
    }*/
}
