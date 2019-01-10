package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;

@EcRocTag("N6ChangedCardRequest")
public class N6ChangedCardRequestProcessors extends BaseAopProcessor implements ParamsAppliable {
    private String apptx;
    private final LanUtils lan = new LanUtils();
    private static final String[] PARAM_ARRAY = { "ecaop.trade.n6.checkUserParametersMapping",
            "ecaop.masb.smbca.ParametersMapping",
            "ecaop.masb.sbac.N6.sglUniTradeParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[5];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        apptx = (String) body.get("apptx");
        msg.put("apptx", apptx);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        // 获取三户信息
        Map threePartRet = getThreePartInfo(exchange, msg);
        List<Map> threeList = new ArrayList<Map>();
        List<Map> userInfoList = (ArrayList<Map>) threePartRet.get("userInfo");
        List<Map> custInfoList = (ArrayList<Map>) threePartRet.get("custInfo");
        List<Map> acctInfoList = (ArrayList<Map>) threePartRet.get("acctInfo");
        if (userInfoList.size() != 0) {
            threeList.add(userInfoList.get(0));
        }
        if (custInfoList.size() != 0) {
            threeList.add(custInfoList.get(0));
        }
        if (acctInfoList.size() != 0) {
            threeList.add(acctInfoList.get(0));
        }
        // 调用北六资源预占接口
        msg.putAll(preCheckData(msg));
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        lan.preData(pmp[1], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.bss.services.numberser");// 地址不确定
        lan.xml2Json("ecaop.trades.smbca.checkres.template", exchange);
        Map retMap = exchange.getOut().getBody(Map.class);
        System.out.println("malytest" + retMap);
        List<Map> rspInfoList = (ArrayList<Map>) retMap.get("resourcesInfo");// 资源信息
        if (null == rspInfoList || 0 == rspInfoList.size()) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE,
                    "核心系统未返回卡号信息.");
        }
        // 调用预提交
        msg.put("operTypeCode", "0");
        msg.put("ordersId", msg.get("ordersId"));
        System.out.println("===============" + msg.get("apptx") + "   " + msg);
        msg.put("base", preSubBaseData(msg, threeList));
        System.out.println("===============" + msg.get("apptx") + "   " + msg.get("base"));
        msg.put("ext", preSubExtData(msg, rspInfoList, threeList));
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        try {
            lan.preData(pmp[2], exchange);
            CallEngine.wsCall(exchange,
                    "ecaop.comm.conf.url.n6ess.services.AOPOrdForNorthSer" + "." + msg.get("province"));
            lan.xml2Json("ecaop.masb.sbac.sglUniTradeTemplate", exchange);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用预提交失败" + e.getMessage());
        }
        Map retMapSub = exchange.getOut().getBody(Map.class);
        List<Map> rspInfoListSub = (ArrayList<Map>) retMapSub.get("rspInfo");
        if (null == rspInfoListSub || 0 == rspInfoListSub.size()) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE,
                    "核心系统未返回订单信息.");
        }
        // 返回结果给上游
        exchange.getOut().setBody(preBackData(retMapSub));
    }

    private Map preCheckData(Map msg) {
        msg.put("resNo", msg.get("simCard"));
        msg.put("tradeTypeCode", "0142");
        msg.put("resTypeCode", "2");
        msg.put("occupiedFlag", "1");
        return msg;
    }

    private Map preSubBaseData(Map msg, List<Map> threePartRet) {
        Map base = new HashMap();
        base.put("userId", threePartRet.get(0).get("userId"));
        base.put("custId", threePartRet.get(0).get("custId"));
        base.put("usecustId", base.get("custId"));
        base.put("contact", "");
        base.put("eparchyCode", ChangeCodeUtils.changeEparchy(msg));
        base.put("subscribeId", msg.get("ordersId"));
        base.put("checkTypeCode", "");
        base.put("netTypeCode", threePartRet.get(0).get("serviceClassCode").toString().substring(2, 4));
        base.put("inModeCode", "E");
        base.put("tradeId", msg.get("ordersId"));
        base.put("tradeTypeCode", "0141");
        base.put("advancePay", "0");
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        base.put("cancelTag", "0");
        base.put("operFee", "0");
        base.put("remark", "");
        base.put("brandCode", threePartRet.get(0).get("brandCode"));
        base.put("cityCode", msg.get("district"));
        base.put("execTime", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("acceptDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("feeStaffId", "");
        base.put("operFee", "0");
        base.put("termIp", "132.35.87.198");
        base.put("actorPhone", "");
        base.put("chkTag", "0");
        base.put("contactPhone", "");
        base.put("custName", threePartRet.get(1).get("custName"));
        base.put("productId", threePartRet.get(0).get("productId"));
        base.put("actorName", "");
        base.put("serinalNamber", msg.get("serialNumber"));
        base.put("acctId", threePartRet.get(2).get("acctId"));
        base.put("actorCertTypeId", "1");
        base.put("userDiffCode", "00");
        base.put("foregift", "0");
        base.put("mainDiscntCode", "");// ?
        return base;
    }

    private Map preSubExtData(Map msg, List<Map> rspInfoList, List<Map> threePartRet) {
        Map ext = new HashMap();
        Map tradeItem = new HashMap();
        Map tradeRes = new HashMap();
        List<Map> tradeItemList = new ArrayList<Map>();
        List<Map> tradeResList = new ArrayList<Map>();
        Map temp = new HashMap();
        // 拼接tradeRes开始
        String date = RDate.currentTimeStr("yyyyMMddHHmmss");
        for (int i = 0; i < rspInfoList.size(); i++) {
            temp = new HashMap();
            Map resourcesNumberInfo = rspInfoList.get(i);
            if (resourcesNumberInfo.get("resNo").equals(msg.get("simCard"))) {// 卡校验结果同请求卡号比对
                String imsi = (String) resourcesNumberInfo.get("imsi");
                temp.put("startDate", date);
                temp.put("endDate", "20501231235959");
                temp.put("resInfo2", msg.get("simCard"));
                temp.put("resCode", msg.get("simCard"));
                temp.put("modifyTag", "0");
                temp.put("resInfo1", imsi);
            }
            else {
                break;
            }
            temp.put("reTypeCode", "1");
            tradeResList.add(temp);
        }
        temp = new HashMap();
        List<Map> resInfoList = new ArrayList<Map>();
        if (threePartRet.size() != 0) {
            resInfoList = (List<Map>) (threePartRet.get(0).get("resInfo"));
        }
        for (Map resInfo : resInfoList) {
            if (resInfo.get("resTypeCode").equals("1")) {
                temp.put("resInfo1", resInfo.get("resInfo1"));
                temp.put("resInfo2", resInfo.get("resCode"));
                temp.put("resCode", resInfo.get("resCode"));
                temp.put("startDate", resInfo.get("startDate"));
                msg.put("oldSim", resInfo.get("resCode"));// 将三户返回的旧卡卡号放入msg中
                break;
            }
        }
        temp.put("endDate", date);
        temp.put("modifyTag", "1");
        temp.put("reTypeCode", "1");
        tradeResList.add(temp);
        tradeRes.put("item", tradeResList);
        ext.put("tradeRes", tradeRes);
        // 拼接tradeItem开始
        tradeItemList.add(LanUtils.createTradeItem("OLD_SIM", msg.get("oldSim")));
        tradeItemList.add(LanUtils.createTradeItem("SIM_CARD", msg.get("simCard")));
        tradeItemList.add(LanUtils.createTradeItem("SIMCARD_OPERATE_FLAG", msg.get("simcardOperateFlag")));
        tradeItemList.add(LanUtils.createTradeItem("C_1024", ""));
        tradeItemList.add(LanUtils.createTradeItem("OTHER_REASON", ""));
        tradeItemList.add(LanUtils.createTradeItem("RSRV_STR1", ""));
        tradeItemList.add(LanUtils.createTradeItem("REASON_DESCRIPTION", "1111"));
        tradeItemList.add(LanUtils.createTradeItem("CONTACT_NAME", "1"));
        tradeItemList.add(LanUtils.createTradeItem("PRIORITY", "1"));
        tradeItemList.add(LanUtils.createTradeItem("APPL_REASON", ""));
        tradeItemList.add(LanUtils.createTradeItem("CONTACT_INFO", "1"));
        tradeItemList.add(LanUtils.createTradeItem("REASON_CODE_NO", "06"));
        tradeItemList.add(LanUtils.createTradeItem("IMMEDIACY_INFO", "0"));
        tradeItemList.add(LanUtils.createTradeItem("REASON_CODE", "其他原因"));
        tradeItemList.add(LanUtils.createTradeItem("X_EXISTFLAG", "0"));
        tradeItemList.add(LanUtils.createTradeItem("IS_IMMEDIACY", "0"));
        tradeItemList.add(LanUtils.createTradeItem("PRINT_REMARKS", ""));
        tradeItem.put("item", tradeItemList);
        ext.put("tradeItem", tradeItem);
        return ext;
    }

    private Map preBackData(Map retMapSub) {
        Map outMap = new HashMap();
        Map rspInfoSub = ((ArrayList<Map>) retMapSub.get("rspInfo")).get(0);
        List<Map> provinceOrderInfoList = (ArrayList<Map>) rspInfoSub
                .get("provinceOrderInfo");
        Map provinceOrderInfo = provinceOrderInfoList.get(0);
        List<Map> preFeeInfoRspList = (ArrayList<Map>) provinceOrderInfo
                .get("preFeeInfoRsp");
        List<Map> feeInfoList = new ArrayList<Map>();
        for (int i = 0; i < preFeeInfoRspList.size(); i++) {
            Map feeInfo = new HashMap();
            Map preFeeInfoRsp = preFeeInfoRspList.get(i);
            feeInfo.put("feeId", preFeeInfoRsp.get("feeTypeCode"));
            feeInfo.put("feeCategory", preFeeInfoRsp.get("feeMode"));
            feeInfo.put("feeDes", preFeeInfoRsp.get("feeTypeName"));
            feeInfo.put("maxRelief", preFeeInfoRsp.get("maxDerateFee"));
            feeInfo.put("origFee", preFeeInfoRsp.get("oldFee"));
            feeInfoList.add(feeInfo);
        }
        outMap.put("essSubscribeId", rspInfoSub.get("provOrderId"));
        outMap.put("feeInfo", feeInfoList);
        outMap.put("totalFee", provinceOrderInfo.get("totalFee"));
        outMap.put("para", retMapSub.get("para"));
        return outMap;
    }

    /**
     * 调三户获取信息
     * 
     * @param exchange
     * @param msg
     * @return
     * @throws Exception
     */
    public Map getThreePartInfo(Exchange exchange, Map msg) throws Exception {
        // 调用三户接口,为预提交接口准备信息
        // 调北六三户
        String[] copyArray = { "operatorId", "province", "channelId", "city", "district", "channelType" };
        Map threePartMap = MapUtils.asMap("getMode", "00000", "serialNumber", msg.get("serialNumber"),
                "tradeTypeCode", "0120");
        MapUtils.arrayPut(threePartMap, msg, copyArray);
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);

        try {
            lan.preData(pmp[0], threePartExchange);
            CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.UsrForNorthSer" + "." + msg.get("province"));
            lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        }
        catch (EcAopServerBizException e) {
            throw new EcAopServerBizException("9999", e.getMessage());
        }
        Map threePartRet = threePartExchange.getOut().getBody(Map.class);
        // 校验是否没有返回三户信息
        String[] infoKeys = new String[] { "userInfo", "custInfo", "acctInfo" };
        Map errorDetail = MapUtils.asMap("userInfo", "用户信息", "custInfo", "客户信息", "acctInfo", "账户信息");
        for (String infoKey : infoKeys) {
            if (IsEmptyUtils.isEmpty(threePartRet.get(infoKey))) {
                throw new EcAopServerBizException("9999", "调三户未返回" + errorDetail.get(infoKey));
            }

        }
        return threePartRet;
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
