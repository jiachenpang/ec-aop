package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import com.ailk.ecaop.biz.res.ResourceStateChangeUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.extractor.TransIdFromRedisValueExtractor;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;
import org.n3r.ecaop.core.util.IsEmptyUtils;
import org.n3r.ecaop.dao.base.DaoEngine;
import org.n3r.esql.Esql;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.NumCenterUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("cbssCancel")
public class CbssCancelProcessors extends BaseAopProcessor implements ParamsAppliable {
    private static final String[] PARAM_ARRAY = { "ecaop.trades.sccc.cancelTmlPre.paramtersmapping",
            "ecaop.trades.sccc.cancelPre.paramtersmapping", "ecaop.trades.sccc.cancelTml.paramtersmapping",
            "ecaop.trades.sccc.cancel.paramtersmapping", "ecaop.trades.sccc.cancel.crm.paramtersmapping",
            "ecaop.trade.cbss.checkUserParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[6];

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void process(Exchange exchange) throws Exception {

        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        // 设置默认类型为白卡cardType = "1";
        String cardType = (String) msg.get("cardType");
        if (null == cardType || "".equals(cardType)) {
            cardType = "1";
            msg.put("cardType", cardType);
        }
        msg.put("tradeId", creatTransIDO(exchange));
        msg.put("appCode", exchange.getAppCode());
        LanUtils lan = new LanUtils();
        String resourceCode = (String) msg.get("imei");
        boolean isNewResCenter = EcAopConfigLoader.getStr("ecaop.global.param.resources.aop.province").contains((String) msg.get("province"));
        if (StringUtils.isNotEmpty(resourceCode)) {
            //割接省份需要调用新零售中心
            if (isNewResCenter) {
                ResourceStateChangeUtils resourceStateChangeUtils = new ResourceStateChangeUtils();
                resourceStateChangeUtils.resCancelSalePre(exchange);
            } else {
                // 调用3GESS为cBSS提供的返销预判接口
                lan.preData(pmp[0], exchange);
                CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.cbss");
                lan.xml2Json4ONS("ecaop.trades.sccc.cancelTmlPre.template", exchange);
            }
        }

        // 调用cBSS的返销预判接口
        msg.putAll(preData(msg, exchange.getAppCode()));// 需要同步区号
        msg.put("base", preBaseData(msg, exchange.getAppCode()));
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        lan.preData(pmp[1], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.trades.sccc.cancelPre.template", exchange);

        Map retMap = exchange.getOut().getBody(Map.class);
        List<Map> rspInfoList = (ArrayList<Map>) retMap.get("rspInfo");
        if (null == rspInfoList || 0 == rspInfoList.size()) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "核心系统未返回订单信息.");
        }

        // 这两个信息要最终返回给接入系统，如不在此设置，可能会被后面的返回覆盖
        Map rspInfo = rspInfoList.get(0);
        String provOrderId = rspInfo.get("provOrderId").toString();
        String bssOrderId = rspInfo.get("bssOrderId").toString();
        if (StringUtils.isNotEmpty(resourceCode)) {
            // 调用3GESS为cBSS提供的返销提交接口
            exchange.getIn().setBody(body);
            //割接省份需要调用新零售中心
            if (isNewResCenter) {
                ResourceStateChangeUtils resourceStateChangeUtils = new ResourceStateChangeUtils();
                resourceStateChangeUtils.resCancelSale(exchange);
            } else {
                lan.preData(pmp[2], exchange);
                CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.cbss");
                lan.xml2Json4ONS("ecaop.trades.sccc.cancelTml.template", exchange);
            }
        }

        String serialNumber = (String) msg.get("serialNumber");
        // 白卡返销调用crm处理接口
        if ("1".equals(cardType) && StringUtils.isNotEmpty(serialNumber)) {
            // 调用三户接口查询simcard
            Map retThreeMap = threepartCheck(exchange, body, msg, serialNumber);
            String simCard = dealretMap(retThreeMap);
            if (StringUtils.isNotEmpty(simCard)) {
                msg.put("numID", serialNumber);
                msg.put("iccid", simCard);
                body.put("msg", msg);
                exchange.getIn().setBody(body);
                lan.preData(pmp[4], exchange);
                CallEngine.aopCall(exchange, "ecaop.comm.conf.url.osn.syncreceive.9900");
                //lan.xml2Json1ONS("ecaop.trades.sccc.cancel.crm.template", exchange);
                lan.xml2JsonNoBody("ecaop.trades.sccc.cancel.crm.template", exchange);
            }
        }

        // 调用cBSS的返销提交接口
        Map orderMap = preOrderSubParam(rspInfo);
        orderMap.put("orderNo", provOrderId);
        orderMap.put("provOrderId", bssOrderId);
        msg.putAll(orderMap);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        lan.preData(pmp[3], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
        lan.xml2Json("ecaop.trades.sccc.cancel.template", exchange);

        // 受理成功时，返回总部和省份订单
        Map outMap = new HashMap();
        outMap.put("provOrderId", provOrderId);
        outMap.put("bssOrderId", bssOrderId);

        // ----------------------
        /**
         * 成卡返销调号卡返销通知接口  TRADE_TYPE传02
         * 必须参数里有iccid并且是成卡并且配置了开关才会调
         * */
        String iccid = (String) msg.get("iccid");
        boolean isIccid = true;
        if (null == iccid || "".equals(iccid)) {
            isIccid = false;
        }
        //Config.getStr("ecaop.sccc.params.config.province", "");
        if (isIccid
                && "2".equals(cardType)
                && (EcAopConfigLoader.getStr("ecaop.global.param.sccc.config.province") + "").contains((String) msg
                        .get("province")))
        {
            //System.out.println("testABCD002_process6");
            Map REQ = new HashMap();
            REQ.put("STAFF_ID", msg.get("operatorId"));
            REQ.put("PROVINCE_CODE", msg.get("province"));
            REQ.put("CITY_CODE", msg.get("city"));
            REQ.put("CHANNEL_ID", msg.get("channelId"));
            REQ.put("SYS_CODE", new NumCenterUtils().changeSysCode(exchange));
            String district = (String) msg.get("district");
            String channelType = (String) msg.get("channelType");
            if (null != district && !"".equals(district)) REQ.put("DISTRICT_CODE", msg.get("district"));
            if (null != channelType && !"".equals(channelType)) REQ.put("CHANNEL_TYPE", msg.get("channelType"));
            REQ.put("REQ_NO", (String) msg.get("ordersId"));//调用方订单编号或流水号。            
            REQ.put("ICCID", iccid);//卡号
            String num = (String) msg.get("serialNumber");
            if (null == num || "".equals(num)) {
                throw new EcAopServerBizException("9999", "serialNumber未传。");
            }
            REQ.put("SERIAL_NUMBER", num);//号码
            REQ.put("TRADE_TYPE", "02");//业务类型            02：开户返销            06：空中入网卡开户返销
            Map req = NumFaceHeadHelper.creatHead(exchange.getAppkey());
            Map UNI_BSS_BODY = new HashMap();
            UNI_BSS_BODY.put("NOTIFY_RETURN_CARD_RESULT_REQ", REQ);
            req.put("UNI_BSS_BODY", UNI_BSS_BODY);
            exchange.getIn().setBody(req);
            CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.cardCenter.notifyReturnCardResult");
            //处理返回
            String rsp = exchange.getOut().getBody().toString();
            //System.out.println("号卡中心返回toString：" + rsp);
            Map out = (Map) JSON.parse(rsp);
            Map UNI_BSS_HEAD = (Map) out.get("UNI_BSS_HEAD");
            if (null != UNI_BSS_HEAD) {
                String code = (String) UNI_BSS_HEAD.get("RESP_CODE");
                if (!"0000".equals(code) && !"00000".equals(code)) {
                    throw new EcAopServerBizException("9999", (String) UNI_BSS_HEAD.get("RESP_DESC"));
                }
            }
            else {
                throw new EcAopServerBizException("9999", "调号卡中心成卡返销通知接口返回异常!");
            }
            Map returnBody = (Map) out.get("UNI_BSS_BODY");
            if (null != returnBody) {
                Map rspMap = (Map) returnBody.get("NOTIFY_RETURN_CARD_RESULT_RSP");
                String code = (String) rspMap.get("RESP_CODE");
                if (!"0000".equals(code)) {
                    throw new EcAopServerBizException("9999", (String) rspMap.get("RESP_DESC"));
                }
            }
            else {
                throw new EcAopServerBizException("9999", "调号卡中心成卡返销通知接口返回异常!");
            }
        }
        // ----------------------

        exchange.getOut().setBody(outMap);
    }

    private String dealretMap(Map retThreeMap) {
        // TODO Auto-generated method stub     
        Map userInfo = new HashMap();
        List<Map> user = (List<Map>) retThreeMap.get("userInfo");
        if (IsEmptyUtils.isEmpty(user)) {
            throw new EcAopServerBizException("9999", "用户信息未返回！");
        }
        List<Map> resInfo = (List<Map>) user.get(0).get("resInfo");
        if (null != resInfo && !resInfo.isEmpty()) {
            for (Map temp : resInfo) {
                if ("1".equals(temp.get("resTypeCode"))) {
                    userInfo.put("simCard", temp.get("resCode"));
                }
            }
        }
        else {
            if (null != user.get(0).get("sinCardNo")) {
                userInfo.put("simCard", user.get(0).get("sinCardNo"));
            }
        }
        String simCard = (String) userInfo.get("simCard");
        if (IsEmptyUtils.isEmpty(simCard)) {
            throw new EcAopServerBizException("9999", "未返回卡信息！");
        }
        return simCard;
    }

    /**
     * 调用三号接口查询simCard
     * 
     * @param exchange
     * @param body
     * @param msg
     * @param serialNumber
     * @throws Exception
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Map threepartCheck(Exchange exchange, Map body, Map msg, String serialNumber) throws Exception {
        /*msg.put("serialNumber", serialNumber);
        msg.put("tradeTypeCode", "0093");
        msg.put("serviceClassCode", "0000");
        msg.put("infoList", "USER|CUST|ACCOUNT");
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        CheckUserInfoProcessor checkUserInfo = new CheckUserInfoProcessor();
        String bizKey = exchange.getBizkey();
        exchange.setBizkey(MagicNumber.CHANGE_CARD_BIZKEY);
        checkUserInfo.process(exchange);
        exchange.setBizkey(bizKey);
        Map checkUserMap = exchange.getOut().getBody(Map.class);
        System.out.println("testABCD002_checkUserMap:" + checkUserMap);
        return MapUtils.getString(checkUserMap, "simCard");*/
        Map threePartMap = new HashMap();
        MapUtils.arrayPut(threePartMap, msg, MagicNumber.COPYARRAY);
        threePartMap.put("tradeTypeCode", "0093");
        threePartMap.put("serialNumber", serialNumber);
        threePartMap.put("getMode", "1111111111100013111100001100001");
        threePartMap.put("serviceClassCode", "0000");
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[5], threePartExchange);
        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        return (Map) threePartExchange.getOut().getBody();

    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Map preData(Map msg, String appCode) {
        try {
            Esql dao = DaoEngine.getMySqlDao("/com/ailk/ecaop/sql/cbss/CbssAreaChangeQuery.esql");
            List result = dao.id("selAreaCode").params(msg).execute();
            if (null == result || 0 == result.size()) {
                throw new EcAopServerBizException("9999", "地市信息转换失败");
            }
            msg.put("areaCode", result.get(0));
        }
        catch (Exception e) {
            // throw new EcAopServerBizException("9999", e.getMessage());
            msg.put("areaCode", msg.get("city"));
        }
        Map ext = new HashMap();

        ext.put("tradeOther", preTradeOtherData(msg));
        ext.put("tradeItem", preTradeItemData(msg, appCode));
        msg.put("operTypeCode", "0");
        msg.put("ext", ext);
        return msg;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
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

    private Object creatTransIDO(Exchange exchange) {
        String str[] = { "@50" };
        TransIdFromRedisValueExtractor transId = new TransIdFromRedisValueExtractor();
        transId.applyParams(str);
        return transId.extract(exchange);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
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
        if (StringUtils.isNotEmpty((String) msg.get("serialNumber"))) {
            item.put("rsrvStr13", msg.get("serialNumber"));
        }
        else {
            throw new EcAopServerBizException("9999", "外围返销号码未传,请检查！");
        }
        tradeOther.put("item", item);
        return tradeOther;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Map preTradeItemData(Map msg, String appCode) {
        List<Map> item = new ArrayList<Map>();
        Map tradeItem = new HashMap();
        if ("mabc".equals(msg.get("appCode"))) {
            String netType = changeNetType((String) msg.get("netType"));
            if (StringUtils.isNotBlank(netType) && !"BJMC".equals(netType)) {
                item.add(LanUtils.createTradeItem("E_IN_MODE", netType));
                item.add(LanUtils.createTradeItem("OTO_ORDER_ID", msg.get("ordersId")));
            }
            item.add(LanUtils.createTradeItem("ORDER_SOURCE", "OTO11"));// 目前只有北京用，所有OTO11
        }
        else {
            item.add(LanUtils.createTradeItem("E_IN_MODE", "A"));
        }
        if (new ChangeCodeUtils().isWOPre(appCode)) {
            Map tempMap3 = new HashMap();
            tempMap3.put("xDatatype", "NULL");
            tempMap3.put("attrCode", "WORK_TRADE_ID");
            tempMap3.put("attrValue", msg.get("ordersId"));
            item.add(tempMap3);
        }
        item.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", "2010300"));
        tradeItem.put("item", item);
        return tradeItem;
    }

    private String changeNetType(String netType) {
        if ("00".equals(netType) || "".equals(netType) || null == netType) {
            netType = "JTOTO";
        }
        else if ("01".equals(netType)) {
            netType = "HSOTO";
        }
        else if ("02".equals(netType)) {
            netType = "BDOTO";
        }
        else if ("03".equals(netType)) {
            netType = "LSOTO";
        }
        else if ("04".equals(netType)) {
            netType = "HWOTO";
        }
        else if ("05".equals(netType)) {
            netType = "XMOTO";
        }
        else if ("06".equals(netType)) {
            netType = "BJMC";
        }
        return netType;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Map preOrderSubParam(Map inMap) {
        Map outMap = new HashMap();
        List<Map> provinceOrderInfo = (ArrayList<Map>) inMap.get("provinceOrderInfo");
        if (null != provinceOrderInfo && 0 != provinceOrderInfo.size()) {
            outMap.put("subOrderSubReq", dealSubOrder(provinceOrderInfo));
        }
        outMap.put("origTotalFee", "0");
        outMap.put("operationType", "01");
        outMap.put("cancleTotalFee", provinceOrderInfo.get(0).get("totalFee"));
        return outMap;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Map dealSubOrder(List<Map> provinceOrderInfo) {
        Map retMap = new HashMap();
        for (Map tempMap : provinceOrderInfo) {
            retMap.put("subOrderId", tempMap.get("subOrderId"));
            retMap.put("subProvinceOrderId", tempMap.get("subProvinceOrderId"));
            List<Map> feeList = (ArrayList<Map>) tempMap.get("preFeeInfoRsp");
            List<Map> retFee = new ArrayList<Map>();
            if (null != feeList && 0 != feeList.size()) {
                for (Map fee : feeList) {
                    Map tempFee = dealFeeInfo(fee);
                    retFee.add(tempFee);
                }
                retMap.put("feeInfo", retFee);
            }
        }
        return retMap;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Map dealFeeInfo(Map inputMap) {
        Map retMap = new HashMap();
        retMap.put("feeCategory", inputMap.get("feeMode"));
        retMap.put("feeId", inputMap.get("feeTypeCode"));
        retMap.put("feeDes", inputMap.get("feeTypeName"));
        retMap.put("operateType", "1");
        retMap.put("origFee", inputMap.get("oldFee"));
        retMap.put("reliefFee", "0");
        retMap.put("realFee", inputMap.get("oldFee"));
        return retMap;
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
}
