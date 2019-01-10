package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.extractor.TransIdFromRedisValueExtractor;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;
import org.n3r.ecaop.dao.base.DaoEngine;
import org.n3r.esql.Esql;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.user.CheckUserInfoProcessor;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.LanUtils;

@EcRocTag("cbssCancel")
public class CancelCbssProcessors extends BaseAopProcessor implements ParamsAppliable {
    private static final String[] PARAM_ARRAY = {"ecaop.trades.sccc.cancelTmlPre.paramtersmapping",
            "ecaop.trades.sccc.cancelPre.paramtersmapping",
            "ecaop.trades.sccc.cancelTml.paramtersmapping",
            "ecaop.trades.sccc.cancel.paramtersmapping",
            "ecaop.trades.sccc.cancel.crm.paramtersmapping"};
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[5];

    @Override
    public void process(Exchange exchange) throws Exception {

        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        msg.put("tradeId", creatTransIDO(exchange));

        LanUtils lan = new LanUtils();
        String resourceCode = (String) msg.get("imei");
        if (StringUtils.isNotEmpty(resourceCode)) {
            // 调用3GESS为cBSS提供的返销预判接口
            lan.preData(pmp[0], exchange);
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.cbss");
            lan.xml2Json4ONS("ecaop.trades.sccc.cancelTmlPre.template", exchange);
        }

        // 调用cBSS的返销预判接口
        msg.putAll(preData(msg));// 需要同步区号
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
            lan.preData(pmp[2], exchange);
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.cbss");
            lan.xml2Json4ONS("ecaop.trades.sccc.cancelTml.template", exchange);
        }
        String serialNumber = MapUtils.getString(rspInfo, "serialNumber");
        // 白卡返销调用crm处理接口
        if ("1".equals(msg.get("cardType")) && StringUtils.isNotEmpty(serialNumber)) {
            // 调用三户接口查询simcard
            String simCard = threepartCheck(exchange, body, msg, serialNumber);
            if (StringUtils.isNotEmpty(simCard)) {
                msg.put("numID", serialNumber);
                msg.put("iccid", simCard);
                body.put("msg", msg);
                exchange.getIn().setBody(body);
                lan.preData(pmp[4], exchange);
                CallEngine.aopCall(exchange, "ecaop.comm.conf.url.osn.syncreceive.9900");
                lan.xml2Json1ONS("ecaop.trades.sccc.cancel.crm.template", exchange);
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
        exchange.getOut().setBody(outMap);
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
    private String threepartCheck(Exchange exchange, Map body, Map msg, String serialNumber) throws Exception {
        msg.put("serialNumber", serialNumber);
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
        return MapUtils.getString(checkUserMap, "simCard");
    }

    private Map preData(Map msg) {
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

    private Object creatTransIDO(Exchange exchange) {
        String str[] = { "@50"
        };
        TransIdFromRedisValueExtractor transId = new TransIdFromRedisValueExtractor();
        transId.applyParams(str);
        return transId.extract(exchange);
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
            pmp[i].applyParams(new String[]{PARAM_ARRAY[i]});
        }
    }
}
