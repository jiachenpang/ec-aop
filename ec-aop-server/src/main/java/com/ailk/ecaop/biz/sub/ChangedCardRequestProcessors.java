package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;

@EcRocTag("cbssChangedCardRequest")
public class ChangedCardRequestProcessors extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        LanUtils lan = new LanUtils();
        msg.putAll(preCheckData(msg));
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        // 调用三户接口
        exchange.setMethodCode("qctc");
        lan.preData("ecaop.trade.cbss.checkUserParametersMapping", exchange);
        CallEngine.wsCall(exchange,
                "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
        lan.xml2Json("ecaop.trades.cbss.threePart.template", exchange);
        Map threepartMap = exchange.getOut().getBody(Map.class);
        List<Map> threeList = new ArrayList<Map>();
        List<Map> userInfoList = (ArrayList<Map>) threepartMap.get("userInfo");
        List<Map> custInfoList = (ArrayList<Map>) threepartMap.get("custInfo");
        List<Map> acctInfoList = (ArrayList<Map>) threepartMap.get("acctInfo");
        if (userInfoList.size() != 0) {
            threeList.add(userInfoList.get(0));
        }
        if (custInfoList.size() != 0) {
            threeList.add(custInfoList.get(0));
        }
        if (acctInfoList.size() != 0) {
            threeList.add(acctInfoList.get(0));
        }
        // 调用成卡换卡检验接口
        exchange.setMethodCode("smoca");
        msg.putAll(preCheckData(msg));
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        lan.preData("ecaop.trades.smoca.changedcardReq.ParametersMapping",
                exchange);
        CallEngine.wsCall(exchange,
                "ecaop.comm.conf.url.osn.services.changedCardRequest");
        lan.xml2Json("ecaop.trades.smoca.changedcardReq.Template", exchange);
        Map retMap = exchange.getOut().getBody(Map.class);
        List<Map> rspInfoList = (ArrayList<Map>) retMap.get("cardNumberInfo");
        if (null == rspInfoList || 0 == rspInfoList.size()) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE,
                    "核心系统未返回卡号信息.");
        }
        Map rspInfo = rspInfoList.get(0);
        if (!"1702".equals(rspInfo.get("resultCode").toString())) {
            throw new EcAopServerBizException(rspInfo.get("resultCode")
                    .toString(), rspInfo.get("resultDesc").toString());
        }
        // 调用成卡换卡预提交
        msg.put("operTypeCode", "0");
        msg.put("ordersId", msg.get("ordersId"));
        msg.put("base", preSubBaseData(msg, threeList, exchange.getAppCode()));
        msg.put("ext", preSubExtData(msg, rspInfoList, userInfoList, exchange.getAppCode()));
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        lan.preData("ecaop.trades.smoca.changedcardPre.paramtersmapping",
                exchange);
        CallEngine.wsCall(exchange,
                "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.trades.smoca.changedcardPre.template", exchange);
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
        msg.put("tradeType", "1");
        msg.put("tradeTypeCode", "0141");
        msg.put("serviceClassCode", "0000");
        Map<String, Object> cardNumberInfo = new HashMap();
        cardNumberInfo.put("simCardNo", msg.get("newResourcesCode"));
        cardNumberInfo.put("serialNumber", msg.get("serialNumber"));
        msg.put("cardNumberInfo", cardNumberInfo);
        return msg;
    }

    private Map preSubBaseData(Map msg, List<Map> threeList, String appCode) {
        Map base = new HashMap();
        base.putAll(threeList.get(0));
        base.putAll(threeList.get(1));
        base.putAll(threeList.get(2));
        base.put("areaCode", ChangeCodeUtils.changeEparchy(MapUtils.asMap("province",
                msg.get("province"), "city", msg.get("city"))));
        base.put("subscribeId", msg.get("ordersId"));
        base.put("tradeLcuName", "TCS_ChangeResourceReg");
        base.put("infoTag", "11111100001000000000100000                ");
        base.put("inModeCode", new ChangeCodeUtils().getInModeCode(appCode));
        base.put("tradeId", msg.get("ordersId"));
        base.put("tradeTypeCode", "0141");
        base.put("execTime", "20141117151815");// ////////////
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        base.put("foregift", "0");
        base.put("rightCode", "csBuysimcardHKTrade");
        base.put("chkTag", "0");
        base.put("cancelTag", "0");
        base.put("operFee", "0");
        base.put("tradeTagSet", "00120000000000000000");
        base.put("blackUserTag", "0");
        base.put("netTypeCode", "0050");
        base.put("execTime", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("tradeDepartId", msg.get("channelId"));
        base.put("acceptDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("acceptMonth", RDate.currentTimeStr("MM"));
        base.put("startAcycId", RDate.currentTimeStr("yyyyMM"));
        base.put("termIp", RDate.currentTimeStr("127.0.0.1"));
        base.put("tradeJudgeOweTag", "2");
        base.put("tradeStatus", "2");
        base.put("attrTypeCode", "0");
        base.put("tradeAttr", "1");
        base.put("tradeStaffId", msg.get("operatorId"));
        base.put("serinalNamber", msg.get("serialNumber"));
        base.put("routeEparchy", threeList.get(0).get("xEparchyCode"));
        return base;
    }

    private Map preSubExtData(Map msg, List<Map> rspInfoList, List<Map> userInfoList, String appCode) {
        Map ext = new HashMap();
        Map trade = new HashMap();
        Map tradeRes = new HashMap();
        List<Map> tradeitem = new ArrayList<Map>();
        List<Map> tradeResitem = new ArrayList<Map>();
        Map temp = new HashMap();
        temp.put("attrCode", "OLD_SIM");
        temp.put("attrValue", msg.get("oldResourcesCode"));
        temp.put("xDatatype", "null");
        tradeitem.add(temp);
        temp = new HashMap();
        temp.put("attrCode", "SIM_CARD");
        temp.put("attrValue", msg.get("newResourcesCode"));
        temp.put("xDatatype", "null");
        tradeitem.add(temp);
        // temp = new HashMap();
        // temp.put("attrCode", "STANDARD_KIND_CODE");
        // temp.put("attrValue", "1010400");
        // temp.put("xDatatype", "null");
        // tradeitem.add(temp);
        temp = new HashMap();
        temp.put("attrCode", "X_EXISTFLAG");
        temp.put("attrValue", "1");
        temp.put("xDatatype", "null");
        tradeitem.add(temp);
        temp = new HashMap();
        temp.put("attrCode", "RSRV_STR1");
        temp.put("attrValue", "");
        temp.put("xDatatype", "null");
        tradeitem.add(temp);
        temp = new HashMap();
        temp.put("attrCode", "E_IN_MODE");
        temp.put("attrValue", "A");
        tradeitem.add(temp);
        temp = new HashMap();
        temp.put("attrCode", "REASON_CODE_ID");
        temp.put("attrValue", "202");
        temp.put("xDatatype", "null");
        tradeitem.add(temp);
        if (new ChangeCodeUtils().isWOPre(appCode) && !IsEmptyUtils.isEmpty(msg.get("orderNo"))) {
            temp = new HashMap();
            temp.put("attrCode", "WORK_TRADE_ID");
            temp.put("attrValue", msg.get("orderNo"));
            temp.put("xDatatype", "null");
            tradeitem.add(temp);
        }
        // 补卡的时候，传1，换卡不传
        // temp = new HashMap();
        // temp.put("attrCode", "IS_BK");
        // temp.put("attrValue", "0");
        // temp.put("xDatatype", "null");
        // tradeitem.add(temp);
        temp = new HashMap();
        temp.put("attrCode", "REASON_CODE");
        temp.put("attrValue", "卡损坏");
        temp.put("xDatatype", "null");
        tradeitem.add(temp);
        temp = new HashMap();
        temp.put("attrCode", "NOT_REMOTE");
        temp.put("attrValue", "1");
        temp.put("xDatatype", "null");
        tradeitem.add(temp);
        trade.put("item", tradeitem);
        ext.put("tradeItem", trade);
        // 集成代码
        String date = RDate.currentTimeStr("yyyyMMddHHmmss");
        for (int i = 0; i < rspInfoList.size(); i++) {
            temp = new HashMap();
            Map cardInfo = (Map) rspInfoList.get(i).get("cardInfo");
            Map cardNumberInfo = rspInfoList.get(i);
            if (cardNumberInfo.get("simCardNo").equals(
                    msg.get("newResourcesCode"))) {
                temp.put("startDate", date);
                temp.put("endDate", "20501231235959");
                temp.put("resInfo2", msg.get("newResourcesCode"));
                temp.put("resCode", msg.get("newResourcesCode"));
                temp.put("modifyTag", "0");
            }
            else {
                break;
            }
            temp.put("resInfo1", cardInfo.get("imsi"));
            temp.put("reTypeCode", "1");
            temp.put("resInfo2", "notRemote");
            temp.put("resInfo3", "");
            temp.put("resInfo4", "1000101");
            temp.put("resInfo7", cardInfo.get("ki"));
            temp.put("resInfo8",
                    cardInfo.get("pin2") + "-" + cardInfo.get("puk2"));
            temp.put("resInfo5", cardInfo.get("cardType"));
            temp.put("resInfo6",
                    cardInfo.get("pin") + "-" + cardInfo.get("puk"));
            tradeResitem.add(temp);
        }
        temp = new HashMap();
        List<Map> resInfoList = new ArrayList<Map>();
        if (userInfoList.size() != 0) {
            resInfoList = (ArrayList<Map>) userInfoList.get(0).get("resInfo");
        }
        for (Map resInfo : resInfoList) {
            if (resInfo.get("resTypeCode").equals("1")) {
                temp.put("resInfo1", resInfo.get("resInfo1"));
                temp.put("resInfo2", resInfo.get("resCode"));
                temp.put("resCode", resInfo.get("resCode"));
                temp.put("startDate", resInfo.get("startDate"));
                break;
            }
        }
        temp.put("endDate", date);
        temp.put("modifyTag", "1");
        temp.put("reTypeCode", "1");
        tradeResitem.add(temp);
        tradeRes.put("item", tradeResitem);
        ext.put("tradeRes", tradeRes);
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
}
