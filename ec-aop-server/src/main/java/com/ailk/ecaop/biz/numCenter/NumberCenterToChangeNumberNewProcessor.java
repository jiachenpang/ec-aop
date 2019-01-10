package com.ailk.ecaop.biz.numCenter;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.log.domain.EcAopReqLog;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.TransReqParamsMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.NumCenterUtils;
import com.alibaba.fastjson.JSON;

/**
 * 对接号卡中心--号码变更场景
 * 新号在ESS，老号在号卡
 * 
 * @auther Zhao ZhengChang
 * @create 2016_10_02_15:25
 */
@EcRocTag("numberCenterToChangeNumberNew")
public class NumberCenterToChangeNumberNewProcessor extends BaseAopProcessor {

    LanUtils lan = new LanUtils();
    NumCenterUtils nc = new NumCenterUtils();
    ChangeNumberStatusForNumberCenterProcessor cs = new ChangeNumberStatusForNumberCenterProcessor();

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        List<Map> resourcesInfo = (List<Map>) msg.get("resourcesInfo");
        for (Map res : resourcesInfo) {
            msg.put("serialNumber", res.get("oldResourcesCode"));
            msg.put("proKey", res.get("proKey"));
            msg.put("sysCode", nc.changeSysCode(exchange));
            body.put("msg", msg);

     /*       // 调号卡中心查老号码信息
            Map oldNumInfo = cs.qryOldNumInfo(exchange, msg);

            // 校验信息
            cs.checkInfo(res, oldNumInfo);*/

            // 根据预占时间判断号码在3GE的状态--改为直接取请求参数里的状态
           // String stateOld = getOccupyState(oldNumInfo);
            String state = (String) ((Map) ((List) msg.get("resourcesInfo")).get(0)).get("occupiedFlag");
            //System.out.println("stateOld" + stateOld + ";;;" + state);

            // 调ESS改新号码状态
            exchange.getIn().setBody(body);
            callESS(exchange, state, res);

            // 调号卡中心释放老号码
            String oldSerialNumber = res.get("oldResourcesCode") + "";
            exchange.getIn().setBody(body);
            cs.callRelSelectionNum(exchange, msg, oldSerialNumber, res.get("proKey") + "");

            // 返回处理
            dealReturn(exchange, msg);
        }
    }

    /**
     * 调3GE查新号码状态并校验
     * 
     * @param exchange
     * @throws Exception
     */
    public void selNumStateAndCheck(Exchange exchange, boolean n6) throws Exception {
        LanUtils lan = new LanUtils();
        Map body = exchange.getIn().getBody(Map.class);
        // 获取msg信息
        Object msgObject = body.get("msg");
        Map msg = null;
        if (msgObject instanceof String) {
            msg = JSON.parseObject(msgObject.toString());
        }
        else {
            msg = (Map) msgObject;
        }
        if (n6) {

        }
        else {
            ((Map) (((List) (msg.get("resourcesInfo"))).get(0))).put("occupiedFlag", "0");
            body.put("msg", msg);
            exchange.getIn().setBody(body);
            lan.preData("ecaop.gdjk.numcheck.ParametersMapping", exchange);
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
            lan.xml2Json4ONS("ecaop.gdjk.numcheck.template", exchange);
            exchange.getIn().setBody(body);
            Map retMap = exchange.getOut().getBody(Map.class);
            String numState = retMap.get("rscStateDesc") + "";
            if (!"空闲".equals(numState)) { // 此处待定，不知是否仅返回空闲时才可用
                throw new EcAopServerBizException("9999", "新号码状态不为上架状态，不可换号！");
            }
        }
    }

    /**
     * 校验新老号码信息
     * 
     * @param msg
     * @param oldNumInfo
     * @throws Exception
     */
    public void checkInfo(Map msg, Map oldNumInfo) throws Exception {
        // 工号不用校验了?
        // 校验预占关键字，证件号码
        if (!((msg.get("proKey") + "").equals(oldNumInfo.get("PRO_KEY") + "")
        && (msg.get("certNum") + "").equals(oldNumInfo.get("CERT_CODE") + ""))) {
            throw new EcAopServerBizException("9999", "新老号码预占关键字或证件号码不一致，不可换号！");
        }

    }

    /**
     * 计算号码状态
     * 
     * @param oldNumInfo
     * @throws Exception
     */
    public String getOccupyState(Map oldNumInfo) throws Exception {
        String occupyTime = (String) oldNumInfo.get("OCCUPIED_FLAG");
        if (occupyTime == null || occupyTime.length() == 0) {
            return "没返回预占时间--测试用的";
        }
        String nowTime = (new SimpleDateFormat("yyyyMMddHHmmss")).format(Calendar.getInstance().getTime());
        DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
        Date d1 = df.parse(occupyTime);
        Date d2 = df.parse(nowTime);
        int hours = (int) ((d1.getTime() - d2.getTime()) / (60 * 60 * 1000));// 7天为168小时
        return hours > 168 ? "3" : "2";
    }

    /**
     * 调ESS改号码状态
     * 
     * @param oldNumInfo
     * @throws Exception
     */
    public void callESS(Exchange exchange, String state, Map res) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        // 获取msg信息
        Object msgObject = body.get("msg");
        Map msg = null;
        if (msgObject instanceof String) {
            msg = JSON.parseObject(msgObject.toString());
        }
        else {
            msg = (Map) msgObject;
        }
        LanUtils lan = new LanUtils();
        String province = (String) msg.get("province");
        String serialNumber = res.get("resourcesCode") + "";
        String num = serialNumber.substring(0, 3);
        // 调号码释放接口之前准备参数
        paraNumDataNew(res, state);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        boolean n6 = "17|18|11|76|91|97".contains(province);
        if ("185".equals(num) || !n6) {
            System.out.println(";';3GE了" + msg);
            lan.preData("ecaop.gdjk.numcheck.ParametersMapping", exchange);
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
            lan.xml2Json4ONS("ecaop.gdjk.numcheck.template", exchange);
            exchange.getIn().setBody(body);
        }
        if (n6) {
            // 调北六号码状态变更接口
            ((Map) ((List) msg.get("resourcesInfo")).get(0)).put("occupiedFlag", state);
            body.put("msg", msg);
            exchange.getIn().setBody(body);
            System.out.println(";';调北六了" + msg);
            exchange.setMethod("ecaop.trades.query.comm.snres.chg");// 号码状态变更method
            EcAopReqLog reqLog = (EcAopReqLog) exchange.getProperty(Exchange.REQLOG);
            reqLog.setMethodCode("qcsc");
            preParam(exchange.getIn().getBody(Map.class));
            TransReqParamsMappingProcessor trpmp = new TransReqParamsMappingProcessor();
            trpmp.process(exchange);
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.ec-aop.rest");
            exchange.getIn().setBody(body);
        }
        // 校验返回
        Map retMap = exchange.getOut().getBody(Map.class);
        System.out.println("预定后的Map：" + retMap);
        Map resourcesRsp = (Map) ((List) retMap.get("resourcesRsp")).get(0);
        String rscStateCode = resourcesRsp.get("rscStateCode") + "";
        String rscStateDesc = (resourcesRsp.get("rscStateDesc") + "").length() > 0 ? (resourcesRsp.get("rscStateDesc") + "")
                : "号码状态变更异常！";
        if (!"0000".equals(rscStateCode)) {
            throw new EcAopServerBizException(rscStateCode, rscStateDesc);
        }
    }

    /**
     * 调号码状态变更接口前准备参数
     * 
     * @param
     */
    private void paraNumDataNew(Map msg, String state) {
        List resourcesInfos = new ArrayList();
        Map resourcesInfo = new HashMap();
        resourcesInfo.put("keyChangeTag", "0");
        resourcesInfo.put("proKeyMode", "0");
        resourcesInfo.put("proKey", GetSeqUtil.getSeqFromCb());
        resourcesInfo.put("resourcesType", "0".equals(msg.get("resourcesType")) ? "02" : "01");// 0跟02表示后付费
        resourcesInfo.put("resourcesCode", msg.get("resourcesCode"));
        resourcesInfo.put("occupiedFlag", state);
        resourcesInfo.put("snChangeTag", "0");// 此值待定
        resourcesInfos.add(resourcesInfo);
        msg.put("resourcesInfo", resourcesInfos);
    }

    /**
     * 客户资料校验准备参数
     * 
     * @param body
     */
    private void preParam(Map body) {
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        msg.put("checkType", "0");// 默认用证件校验
        // TODO:证件外围默认传18位身份证
        msg.put("certType", "02");
        msg.put("opeSysType", "1");
        body.put("msg", msg);
    }

    // 准备下发参数
    private void paraData(Exchange exchange) {
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
        requestTempBody.put("SYS_CODE", msg.get("serialNumber"));
        requestTempBody.put("SERIAL_NUMBER", msg.get("serialNumber"));
        requestTempBody.put("SELECTION_TIME", msg.get("occupiedTime"));
        Map resultMap = new HashMap();
        resultMap.put("SELECTED_NUM_REQ", requestTempBody);
        requestBody.put("UNI_BSS_BODY", resultMap);
        exchange.getIn().setBody(JSON.toJSON(requestBody));
    }

    // 处理返回
    public void dealReturn(Exchange exchange, Map msg) {
        Map out = new HashMap();
        Map temp = new HashMap();
        temp.put("resourcesType", "02");
        temp.put("resourcesCode", msg.get("resourcesCode"));
        temp.put("rscStateCode", "0000");
        temp.put("rscStateDesc", "号码变更成功！");
        temp.put("numId", msg.get("resourcesCode"));
        List<Map> resourcesRsp = new ArrayList();
        resourcesRsp.add(temp);
        out.put("resourcesRsp", resourcesRsp);
        exchange.getOut().setBody(out);
    }
}
