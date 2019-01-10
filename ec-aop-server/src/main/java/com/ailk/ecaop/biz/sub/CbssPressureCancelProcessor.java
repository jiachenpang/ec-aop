package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ailk.ecaop.biz.res.ResourceStateChangeUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.extractor.TransIdFromRedisValueExtractor;
import org.n3r.ecaop.core.log.domain.EcAopReqLog;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;
import org.n3r.ecaop.core.processor.TransReqParamsMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.alibaba.fastjson.JSON;

/**
 * CBSS压单订单撤单
 * 
 * @auther Zhao Zhengchang
 * @create 2016_03_31
 */
@EcRocTag("CbssPressureCancel")
public class CbssPressureCancelProcessor extends BaseAopProcessor implements ParamsAppliable {

    // 代码优化
    private static final String[] PARAM_ARRAY = { "ecaop.trades.sccc.cancelTml.paramtersmapping",
            "ecaop.trades.scccs.cancel.crm.paramtersmapping", "ecaop.trades.scccs.ParametersMapping",
            "ecaop.trades.scccs.cancelNum.paramtersmapping",
            "ecaop.trades.scccs.orderActivateOrCancel.ParametersMapping",
            "ecaop.trades.scccs.orderActivateOrCancelForN6.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[6];

    @Override
    public void process(Exchange exchange) throws Exception {
        // 返销卡跟终端
        cancelCardAndTerminal(exchange);

        // 调CBSS压单撤单接口
        cbssPressureCancel(exchange);

        // 返销号码
        cancelNumber(exchange);

        // 返回处理
        dealReturn(exchange);
    }

    /**
     * 返回处理
     * 
     * @param exchange
     */
    public void dealReturn(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }

        Map retMap = exchange.getOut().getBody(Map.class);
        if (retMap == null) {
            Map outMap = new HashMap();
            outMap.put("orderId", msg.get("orderId"));
            exchange.getOut().setBody(outMap);
        }
        else {
            retMap.put("orderId", msg.get("orderId"));
            exchange.getOut().setBody(retMap);
        }

    }

    /**
     * 号码释放
     * 
     * @param exchange
     * @throws Exception
     */
    private void cancelNumber(Exchange exchange) throws Exception {
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
        String serialNumber = (String) msg.get("serialNumber");
        String province = (String) msg.get("province");
        String num = serialNumber.substring(0, 3);
        // 调号码释放接口之前准备参数
        paraNumDataNew(msg);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        boolean n6 = "17|18|11|76|91|97".contains(province);
        if ("185".equals(num) || !n6) {// 175,176ccd
            lan.preData(pmp[3], exchange);
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.cbss");
            lan.xml2Json4ONS("ecaop.trades.scccs.cancelNum.template", exchange);
            exchange.getIn().setBody(body);
        }
        if (n6) {
            // 调北六号码状态变更接口-
            ((Map) ((List) msg.get("resourcesInfo")).get(0)).put("occupiedFlag", "5");// 5表示返销
            body.put("msg", msg);
            exchange.getIn().setBody(body);
            exchange.setMethod("ecaop.trades.query.comm.snres.chg");// 号码状态变更method
            EcAopReqLog reqLog = (EcAopReqLog) exchange.getProperty(Exchange.REQLOG);
            reqLog.setMethodCode("qcsc");
            preParam(exchange.getIn().getBody(Map.class));
            TransReqParamsMappingProcessor trpmp = new TransReqParamsMappingProcessor();
            trpmp.process(exchange);
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.ec-aop.rest");
            exchange.getIn().setBody(body);
        }
    }

    /**
     * 调CBSS压单撤单接口
     * 
     * @param exchange
     * @throws Exception
     */
    public void cbssPressureCancel(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Object msgObject = body.get("msg");
        Map msg = null;
        if (msgObject instanceof String) {
            msg = JSON.parseObject(msgObject.toString());
        }
        else {
            msg = (Map) msgObject;
        }
        String appCode = exchange.getAppCode();
        String province = (String) msg.get("province");
        LanUtils lan = new LanUtils();
        if ("2".equals(msg.get("opeSysType"))) {
            try {
                lan.preData(pmp[2], exchange);
                CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.cbssOrderCleSer");
                lan.xml2Json("ecaop.trades.scccs.template", exchange);
            }
            catch (EcAopServerBizException e) {
                throw new EcAopServerBizException(null == e.getCode() ? "9999" : e.getCode(), "cBSS订单返销失败："
                        + e.getMessage());
            }
            catch (Exception e) {
                throw new EcAopServerBizException("9999", "cBSS订单返销失败：" + e.getMessage());
            }
        }
        else {
            msg.put("operType", "1");
            body.put("msg", msg);
            exchange.getIn().setBody(body);

            try {
                if ("97".equals(province) && "hlpr".equals(appCode)) {
                    lan.preData(pmp[5], exchange);
					CallEngine.wsCall(exchange, "ecaop.comm.conf.url.RealNameCardSer." + province);// TODO:地址
                    lan.xml2Json("ecaop.trades.orderActivateOrCancel.template", exchange);
                }
                else {
                    lan.preData(pmp[4], exchange);
                    CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.RealNameCardSer");// TODO:地址
                    lan.xml2Json("ecaop.trades.orderActivateOrCancel.template", exchange);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                throw new EcAopServerBizException("9999", "省份订单返销失败：" + e.getMessage());
            }
        }

        exchange.getIn().setBody(body);
    }

    private Object creatTransIDO(Exchange exchange) {
        String str[] = { "@50"
        };
        TransIdFromRedisValueExtractor transId = new TransIdFromRedisValueExtractor();
        transId.applyParams(str);
        return transId.extract(exchange);
    }

    /**
     * 调号码状态变更接口前准备参数
     * 
     * @param
     */
    private void paraNumDataNew(Map msg) {
        List resourcesInfos = new ArrayList();
        Map resourcesInfo = new HashMap();
        resourcesInfo.put("keyChangeTag", "0");
        resourcesInfo.put("proKeyMode", "0");
        resourcesInfo.put("proKey", GetSeqUtil.getSeqFromCb());
        resourcesInfo.put("resourcesType", "0".equals(msg.get("resourcesType")) ? "02" : "01");// 0跟02表示后付费
        resourcesInfo.put("resourcesCode", msg.get("serialNumber"));
        resourcesInfo.put("occupiedFlag", "4");
        resourcesInfo.put("identifyingCode", "notifyCB");// 此值用于在T4000017接口中做判断，如果是notifyCB时表示要通知CB做号码释放操作
        resourcesInfo.put("snChangeTag", "0");
        resourcesInfos.add(resourcesInfo);
        msg.put("resourcesInfo", resourcesInfos);
    }

    private Map preData(Map msg) {

        msg.put("areaCode", new ChangeCodeUtils().changeEparchyUnStatic(msg));
        Map ext = new HashMap();

        ext.put("tradeOther", preTradeOtherData(msg));
        ext.put("tradeItem", preTradeItemData());
        msg.put("operTypeCode", "0");
        msg.put("ext", ext);
        return msg;
    }

    private Map preBaseData(Map msg, String appCode) {
        Map base = new HashMap();
        base.put("subscribeId", msg.get("ordersId"));
        base.put("tradeId", msg.get("tradeId"));
        base.put("startDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("tradeTypeCode", "0616");
        base.put("nextDealTag", "9");
        base.put("olcomTag", "0");
        base.put("areaCode", msg.get("areaCode"));
        base.put("foregift", "0");
        base.put("execTime", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("acceptDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("chkTag", "0");
        base.put("operFee", "0");
        base.put("cancelTag", "0");
        base.put("endAcycId", "203701");
        base.put("startAcycId", RDate.currentTimeStr("yyyyMM"));
        base.put("acceptMonth", RDate.currentTimeStr("MM"));
        base.put("netTypeCode", "00ON");
        base.put("advancePay", "0");
        base.put("inModeCode", new ChangeCodeUtils().getInModeCode(appCode));
        base.put("custId", "-1");
        base.put("acctId", "-1");
        base.put("serinalNamber", "-1");
        base.put("productId", "-1");
        base.put("tradeStaffId", msg.get("operatorId"));
        base.put("userDiffCode", "-1");
        base.put("custName", "-1");
        base.put("brandCode", "-1");
        base.put("usecustId", "-1");
        base.put("userId", "-1");
        base.put("termIp", "132.35.81.217");
        base.put("eparchyCode", msg.get("areaCode"));
        base.put("cityCode", msg.get("district"));
        return base;
    }

    private Map preTradeOtherData(Map msg) {
        Map item = new HashMap();
        Map tradeOther = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("rsrvValue", msg.get("essOrigOrderId"));
        item.put("rsrvStr1", "track");
        item.put("modifyTag", "0");
        item.put("rsrvStr3", "返销");
        item.put("rsrvValueCode", "CLOR");
        item.put("rsrvStr6", msg.get("city"));
        item.put("rsrvStr5", "1");
        item.put("rsrvStr7", "DECIDE_IOM");
        item.put("rsrvStr9", msg.get("essOrigOrderId"));
        if (StringUtils.isNotEmpty((String) msg.get("serialNumber")))
        {
            item.put("rsrvStr13", msg.get("serialNumber"));
        }
        else
        {
            throw new EcAopServerBizException("9999", "外围返销号码未传,请检查！");
        }
        tradeOther.put("item", item);
        return tradeOther;
    }

    private Map preTradeItemData() {
        List<Map> item = new ArrayList<Map>();
        Map tradeItem = new HashMap();
        Map tempMap1 = new HashMap();
        Map tempMap2 = new HashMap();
        tempMap1.put("xDatatype", "NULL");
        tempMap1.put("attrCode", "STANDARD_KIND_CODE");
        tempMap1.put("attrValue", "2010300");
        tempMap2.put("attrCode", "E_IN_MODE");
        tempMap2.put("attrValue", "A");
        item.add(tempMap1);
        item.add(tempMap2);
        tradeItem.put("item", item);
        return tradeItem;
    }

    public void cancelCardAndTerminal(Exchange exchange) throws Exception {

        Map body = exchange.getIn().getBody(Map.class);
        // Map msg = (Map) body.get("msg");
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        msg.put("tradeId", creatTransIDO(exchange));
        msg.put("ordersId", msg.get("tradeId"));
        msg.put("essOrigOrderId", msg.get("orderId"));
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        LanUtils lan = new LanUtils();
        String resourceCode = (String) msg.get("imei");

        // 调用cBSS的返销预判接口
        msg.putAll(preData(msg));// 需要同步区号
        msg.put("base", preBaseData(msg, exchange.getAppCode()));
        body.put("msg", msg);
        exchange.getIn().setBody(body);

        if (StringUtils.isNotEmpty(resourceCode)) {
            // 调用3GESS为cBSS提供的返销提交接口
            exchange.getIn().setBody(body);
            //割接省份需要调用新零售中心
            if (EcAopConfigLoader.getStr("ecaop.global.param.resources.aop.province").contains((String) msg.get("province"))) {
                ResourceStateChangeUtils resourceStateChangeUtils = new ResourceStateChangeUtils();
                resourceStateChangeUtils.resCancelSalePre(exchange);
                exchange.getIn().setBody(body);
                resourceStateChangeUtils.resCancelSale(exchange);
            } else {
                lan.preData(pmp[0], exchange);
                CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.cbss");
                lan.xml2Json4ONS("ecaop.trades.sccc.cancelTml.template", exchange);
            }
        }
        String serialNumber = (String) msg.get("serialNumber");
        // 白卡返销调用crm处理接口
        if ("1".equals(msg.get("cardType")) && StringUtils.isNotEmpty(serialNumber)) {
            // 调用三户接口查询simcard
            // String simCard = threepartCheck(exchange, body, msg, serialNumber);
            String simCard = (String) msg.get("iccid");
            if (StringUtils.isNotEmpty(simCard)) {
                msg.put("numID", serialNumber);
                msg.put("iccid", simCard);
                body.put("msg", msg);
                exchange.getIn().setBody(body);
                lan.preData(pmp[1], exchange);
                CallEngine.aopCall(exchange, "ecaop.comm.conf.url.osn.syncreceive.9900");
                // 判断是否强制撤单 by create zhaok Date 18/01/03
                String isCompCancel = String.valueOf(msg.get("isCompCancel"));
                if (!IsEmptyUtils.isEmpty(isCompCancel) && "1".equals(isCompCancel)) {
                    try {
                        lan.xml2Json1ONS("ecaop.trades.sccc.cancel.crm.template", exchange);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                else {
                    lan.xml2Json1ONS("ecaop.trades.sccc.cancel.crm.template", exchange);
                }
                // 判断是否强制撤单 END
            }
        }

        exchange.getIn().setBody(body);
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

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
}
