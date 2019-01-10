package com.ailk.ecaop.biz.sub.gdjk;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.res.QryRcToProActProcessor;
import com.ailk.ecaop.common.extractor.DealSysCodeExtractor;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.NumCenterUtils;
import com.ailk.ecaop.common.utils.ReleaseResUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("OpenApplyProcessor")
public class OpenApplyProcessor extends BaseAopProcessor implements ParamsAppliable {

    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];
    private static final String[] PARAM_ARRAY = { "ecaop.gdjk.opnc.ParametersMapping" }; // 3G预提交

    NumCenterUtils nc = new NumCenterUtils();

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map tempBody = new HashMap(body);
        // 调号卡中心号码预占接口
        Map msg = (Map) body.get("msg");
        Exchange exchangeNum = ExchangeUtils.ofCopy(exchange, msg);

        // 进行号码预占
        NumCheckProcessor ncp = new NumCheckProcessor();
        exchange.getIn().setBody(tempBody);
        ncp.process(exchange);

        LanUtils lan = new LanUtils();
        ReleaseResUtils rr = new ReleaseResUtils();
        try {
            // 进行终端预占
            CheckResProcessor crp = new CheckResProcessor();
            exchange.getIn().setBody(tempBody);
            crp.process(exchange);

            // 上一步的终端预占,预提交接口不会做预占,该接口为商城迷你厅使用,由他们预占
            // 在此调用资源中心获取终端信息放在para节点里发送到3GE处理 by wangmc 20180518
            QryRcToProActProcessor.checkRcToProAct(exchange, msg);
            // 产品类型转化
            boolean isString = body.get("msg") instanceof String;
            Map msgMap = isString ? JSON.parseObject((String) body.get("msg")) : (Map) body.get("msg");
            changeProdType(msgMap);
            lan.preData(pmp[0], exchange);
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
            lan.xml2Json4ONS("ecaop.gdjk.opnc.template", exchange);
        }
        catch (Exception e) {
            // 释放号码
            exchange.getIn().setBody(tempBody);
            rr.releaseNumber(exchange);
            throw e;
        }
        finally {// 释放终端信息
            callNumCenterPreemtedNum(exchangeNum);
            Map result = exchange.getOut().getBody(Map.class);
            exchange.getIn().setBody(tempBody);
            // 此处释放终端和上边的一样 该接口终端由外围预占 因此不用我们释放 by wangmc 20180521
            rr.releaseTerminal(exchange);
            exchange.getOut().setBody(result);
        }
    }

    // 调号卡中心号码预占接口
    private void callNumCenterPreemtedNum(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        String numSwitch = (String) msg.get("numSwitch");

        if (!"0".equals(numSwitch)) {
            return;
        }
        List<Map> numIds = (List) msg.get("numId");
        msg.put("serialNumber", numIds.get(0).get("serialNumber"));
        // 获取sysCode
        new DealSysCodeExtractor().extract(exchange);
        // 准备参数
        dealReqPreemted(exchange);
        try {
            CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.numbercenter.preemptedNum");
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用号卡中心预占接口失败！" + e.getMessage());
        }

        nc.dealReturnHead(exchange);
        try {
            msg.put("numState", "2");
            nc.InsertTradeInfo(msg);
        }
        catch (Exception e) {
            System.out.println("插表失败" + e.getMessage());
        }
    }

    private void dealReqPreemted(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map requestBody = new HashMap();
        Map requestTempBody = new HashMap();
        requestTempBody.putAll(nc.changeCommonImplement(msg));
        try {
            requestBody.putAll(NumFaceHeadHelper.creatHead());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        requestTempBody.put("SYS_CODE", nc.changeSysCode(exchange));
        requestTempBody.put("SERIAL_NUMBER", msg.get("serialNumber"));
        Map resultMap = new HashMap();
        resultMap.put("PREEMPTED_NUM_REQ", requestTempBody);
        requestBody.put("UNI_BSS_BODY", resultMap);
        exchange.getIn().setBody(JSON.toJSON(requestBody));
    }

    /**
     * 产品类型转换
     * @param inMap
     */
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
}
