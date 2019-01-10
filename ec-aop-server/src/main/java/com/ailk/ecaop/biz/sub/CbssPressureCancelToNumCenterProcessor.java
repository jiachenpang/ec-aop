package com.ailk.ecaop.biz.sub;

import java.util.HashMap;
import java.util.Map;

import org.n3r.config.Config;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.NumCenterUtils;
import com.alibaba.fastjson.JSON;

/**
 * CBSS压单订单撤单-号码走号卡中心
 * 
 * @auther Zhao Zhengchang
 * @create 2016_11_10
 */
@EcRocTag("CbssPressureCancelToNumCenter")
public class CbssPressureCancelToNumCenterProcessor extends BaseAopProcessor implements ParamsAppliable {

    NumCenterUtils nc = new NumCenterUtils();
    CbssPressureCancelProcessor cbssCancel = new CbssPressureCancelProcessor();

    @Override
    public void process(Exchange exchange) throws Exception {
        // 返销卡跟终端
        cbssCancel.cancelCardAndTerminal(exchange);

        // 调CBSS压单撤单接口
        cbssCancel.cbssPressureCancel(exchange);

        // 调号卡返销号码
        cancelNumber(exchange);

        // 返回处理
        cbssCancel.dealReturn(exchange);
    }

    /**
     * 调号卡中心号码返销接口
     * 
     * @throws Exception
     */
    public void cancelNumber(Exchange exchange) throws Exception {
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
        if ("1".equals(msg.get("opeSysType"))) {
            // List<Map> subscribeInfo = new EssProvinceDao().qrySubscribeInfo(msg);
            // if (null == subscribeInfo || subscribeInfo.isEmpty()) {//省份2g开户无号码信息
            return;
            // }
        }
        // 调号卡中心新接口改造1：增加开关
        String numSwitch = Config.getStr("ecaop.global.param.num.cancel.cbss.switch");
        if (!"1".equals(numSwitch)) {// 非1-调老接口
            Map REQ = createREQ(msg, exchange);
            REQ.put("SERIAL_NUMBER", msg.get("serialNumber"));
            REQ.put("REQ_NO", msg.get("orderId"));
            Map req = createHeadAndAttached(exchange.getAppkey());
            req.put("UNI_BSS_BODY", MapUtils.asMap("CANCEL_LOGIN_NUM_REQ", REQ));
            exchange.getIn().setBody(req);
            CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.numbercenter.cancelLoginNum");

            // 校验返回
            Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
            Map UNI_BSS_BODY = (Map) outMap.get("UNI_BSS_BODY");
            Map resultMap = (Map) UNI_BSS_BODY.get("CANCEL_LOGIN_NUM_RSP");
            String code = resultMap.get("RESP_CODE") + "";
            String detail = (resultMap.get("RESP_DESC") + "").length() > 0 ? (resultMap.get("RESP_DESC") + "")
                    : "号卡中心返销号码失败！";
            if (!"0000".equals(code)) {
                throw new EcAopServerBizException(code, detail);
            }
            exchange.getIn().setBody(body);
        }
        // 调号卡中心新接口改造2：新接口逻辑
        else {// 1时调新接口
            Map REQ = createREQ(msg, exchange);
            REQ.put("PROVINCE_CODE", msg.get("province"));
            REQ.put("CITY_CODE", msg.get("city"));
            REQ.put("DISTRICT_CODE", msg.get("district"));
            REQ.put("CHANNEL_ID", msg.get("channelId"));
            REQ.put("CHANNEL_TYPE", msg.get("channelType"));
            REQ.put("STAFF_ID", msg.get("operatorId"));
            REQ.put("SYS_CODE", nc.changeSysCode(exchange));
            REQ.put("SERIAL_NUMBER", msg.get("serialNumber"));
            REQ.put("REQ_NO", msg.get("orderId"));
            REQ.put("PARA", msg.get("para"));

            Map req = createHeadAndAttached(exchange.getAppkey());
            req.put("UNI_BSS_BODY", MapUtils.asMap("CANCEL_ORDER_NUM_REQ", REQ));
            exchange.getIn().setBody(req);
            CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.numbercenter.cancelOrderNum");

            // 校验返回
            Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
            Map UNI_BSS_BODY = (Map) outMap.get("UNI_BSS_BODY");
            Map resultMap = (Map) UNI_BSS_BODY.get("CANCEL_ORDER_NUM_RSP");
            String code = resultMap.get("RESP_CODE") + "";
            String detail = (resultMap.get("RESP_DESC") + "").length() > 0 ? (resultMap.get("RESP_DESC") + "")
                    : "调号卡中心占用未激活接口失败！";
            if (!"0000".equals(code)) {
                throw new EcAopServerBizException(code, detail);
            }
            exchange.getIn().setBody(body);
        }
    }

    /**
     * 准备REQ中常用参数
     */
    private Map createREQ(Map msg, Exchange exchange) {
        Map REQ = new HashMap();
        REQ.put("STAFF_ID", msg.get("operatorId"));
        REQ.put("PROVINCE_CODE", msg.get("province"));
        REQ.put("CITY_CODE", msg.get("city"));
        REQ.put("DISTRICT_CODE", msg.get("district"));
        REQ.put("CHANNEL_ID", msg.get("channelId"));
        REQ.put("CHANNEL_TYPE", msg.get("channelType"));
        REQ.put("SYS_CODE", nc.changeSysCode(exchange));
        return REQ;
    }

    /**
     * @throws Exception
     * @return Map 含UNI_BSS_HEAD
     */
    @SuppressWarnings("rawtypes")
    private Map createHeadAndAttached(String appKey) throws Exception {
        return NumFaceHeadHelper.creatHead(appKey);
    }

    @Override
    public void applyParams(String[] params) {
        // TODO Auto-generated method stub
        cbssCancel.applyParams(params);
    }

}
