package com.ailk.ecaop.common.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.lang.RDate;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.product.DateUtils;
import com.ailk.ecaop.common.helper.MagicNumber;

/**
 * 南25省宽带趸交、宽带包年相关公共方法
 * @author Steven
 */
public class YearPayUtils {

    public void callGetBroadbandAcctInfo(Exchange exchange) throws Exception {
        Map msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
        Map getBroadbandMap = new HashMap();
        if ("1".equals(msg.get("queryType"))) {
            getBroadbandMap.put("authAcctId", msg.get("serialNumber"));
        }
        else {
            getBroadbandMap.put("serialNumber", msg.get("serialNumber"));
        }
        getBroadbandMap.put("qryType", msg.get("queryType"));
        getBroadbandMap.put("serviceClassCode", "0200");
        getBroadbandMap.put("areaCode", msg.get("areaCode"));
        MapUtils.arrayPut(getBroadbandMap, msg, MagicNumber.COPYARRAY);
        Exchange getBroadbandExchange = ExchangeUtils.ofCopy(exchange, getBroadbandMap);
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.trade.cbpq.getBroadbandAcctInfoParametersMapping", getBroadbandExchange);
        CallEngine.wsCall(getBroadbandExchange, "ecaop.comm.conf.url.osn.services.numberser");
        lan.xml2Json("ecaop.trade.cbpq.getBroadbandAcctInfo.template", getBroadbandExchange);
        Map getBroadbandRet = getBroadbandExchange.getOut().getBody(Map.class);
        Map acctInfo = (Map) getBroadbandRet.get("acctInfo");
        Object serialNumber = msg.get("serialNumber");
        if (!IsEmptyUtils.isEmpty(acctInfo) && "19|86".contains(msg.get("province").toString())) {
            serialNumber = acctInfo.get("serialNumber");
            msg.put("serialNumber", serialNumber);
            exchange.getIn().getBody(Map.class).put("msg", msg);
        }
    }

    public Map callCheckUserInfo(Exchange exchange) throws Exception {
        return callCheckUserInfo(exchange, "0021");
    }

    public Map callCheckUserInfo(Exchange exchange, String tradeTypeCode) throws Exception {
        Map msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
        Map threePartMap = new HashMap();
        threePartMap.put("serviceClassCode", "0200");
        threePartMap.put("tradeTypeCode", tradeTypeCode);
        threePartMap.put("getMode", "101001101010001001000000000000");
        threePartMap.put("areaCode", msg.get("areaCode"));
        threePartMap.put("serialNumber", msg.get("serialNumber"));
        MapUtils.arrayPut(threePartMap, msg, MagicNumber.COPYARRAY);
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.masb.chku.checkUserParametersMapping", threePartExchange);
        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.osn.services.usrser");
        lan.xml2Json("ecaop.masb.chku.checkUserTemplate", threePartExchange);
        Map out = threePartExchange.getOut().getBody(Map.class);
        Map userInfo = (Map) out.get("userInfo");
        if (IsEmptyUtils.isEmpty(userInfo)) {
            throw new EcAopServerBizException("9999", "省份三户接口未返回用户信息");
        }
        Map arrearageFeeInfo = (Map) userInfo.get("arrearageFeeInfo");
        if (IsEmptyUtils.isEmpty(arrearageFeeInfo)) {
            return out;
        }
        if (arrearageFeeInfo.get("depositMoney").toString().startsWith("-")) {
            throw new EcAopServerBizException("0100", "欠费号码");
        }
        return out;
    }

    // 已订购产品查询
    public Map callQryUserProInfo(Exchange exchange) throws Exception {
        return callQryUserProInfo(exchange, "0090");
    }

    // 已订购产品查询
    public Map callQryUserProInfo(Exchange exchange, Object tradeTypeCode) throws Exception {
        Map msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
        Map qryUserProInfo = MapUtils.asMap("serviceClassCode", "0200", "areaCode", msg.get("areaCode"),
                "serialNumber", msg.get("serialNumber"), "tradeTypeCode", tradeTypeCode);
        // 目前省份需要区号，另：号码节点也需要区号
        MapUtils.arrayPut(qryUserProInfo, msg, MagicNumber.COPYARRAY);
        Exchange qryUserProInfoExchage = ExchangeUtils.ofCopy(exchange, qryUserProInfo);
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.trade.cbpq.QryUserProInfoParametersMapping", qryUserProInfoExchage);
        CallEngine.wsCall(qryUserProInfoExchage, "ecaop.comm.conf.url.osn.services.usrser");
        lan.xml2Json("ecaop.trade.cbpq.qryUserProInfo.template", qryUserProInfoExchage);
        return qryUserProInfoExchage.getOut().getBody(Map.class);
    }

    public Map callSProductChg(Exchange exchange, Map inMap) throws Exception {
        Map msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
        MapUtils.arrayPut(inMap, msg, MagicNumber.COPYARRAY);
        inMap.put("orderId", msg.get("orderNo"));
        inMap.put("areaCode", msg.get("areaCode"));
        inMap.put("serialNumber", msg.get("serialNumber"));
        Exchange sProductChgExchange = ExchangeUtils.ofCopy(exchange, inMap);
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.masb.spec.sProductChgParametersMapping", sProductChgExchange);
        CallEngine.wsCall(sProductChgExchange, "ecaop.comm.conf.url.osn.services.productchgser");
        lan.xml2Json("ecaop.masb.spec.template", sProductChgExchange);
        return sProductChgExchange.getOut().getBody(Map.class);
    }

    public Map callOrder(Exchange exchange, Map inMap) throws Exception {
        Map msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
        inMap.put("orderNo", msg.get("orderNo"));
        MapUtils.arrayPut(inMap, msg, MagicNumber.COPYARRAY);
        Exchange orderSubExchange = ExchangeUtils.ofCopy(exchange, inMap);
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.masb.odsb.ActivityAryParametersMapping", orderSubExchange);
        CallEngine.wsCall(orderSubExchange, "ecaop.comm.conf.url.osn.services.ordser");
        lan.xml2Json("ecaop.masb.odsb.template", orderSubExchange);
        return orderSubExchange.getOut().getBody(Map.class);
    }

    public void dealMktCamp(List<Map> userMktCamp, int brandNumber, Object province) throws Exception {
        dealMktCamp(userMktCamp, brandNumber, "0", province);
    }

    public Map dealMktCamp(Object mktCampId, List<Map> userMktCampList, int brandNumber, Object delayDiscntType)
            throws Exception {// 生效方式 0.立即 1.次月 2.到期
        Date endDate = null;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        if (!IsEmptyUtils.isEmpty(userMktCampList)) {
            Map userMktCamp = userMktCampList.get(0);
            endDate = sdf.parse(userMktCamp.get("endDate").toString());
        }
        Date now = new Date();
        String startDate = sdf.format(endDate);
        if (null == endDate || now.compareTo(endDate) > 0) {// 活动已失效
            startDate = "1".equals(delayDiscntType) ? DateUtils.addSeconds(GetDateUtils.getNextMonthFirstDayFormat(),
                    -1) : DateUtils.addSeconds(sdf.format(now), -1);
        }
        else {
            if ("1".equals(delayDiscntType)) {
                startDate = DateUtils.addSeconds(GetDateUtils.getNextMonthFirstDayFormat(), -1);
            }
            else if ("0".equals(delayDiscntType)) {
                startDate = DateUtils.addSeconds(DateUtils.getDate(), -1);
            }
            else {
                startDate = sdf.format(endDate);
            }
        }
        Map mktCamp = new HashMap();
        mktCamp.put("mktCampId", mktCampId);
        mktCamp.put("startDate", DateUtils.addSeconds(startDate, 1));
        if (0 == brandNumber) {
            brandNumber = DateUtils.diffMonths(userMktCampList.get(0).get("endDate").toString(), userMktCampList.get(0)
                    .get("startDate").toString());
        }
        mktCamp.put("endDate", sdf.format(RDate.addMonths(sdf.parse(startDate), brandNumber)));
        return mktCamp;
    }

    public void dealMktCamp(List<Map> userMktCamp, int brandNumber, Object delayDiscntType, Object province)
            throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        Date now = new Date();
        for (Map mkt : userMktCamp) {
            Date endDate = sdf.parse(mkt.get("endDate").toString());
            if (0 == brandNumber) {
                brandNumber = DateUtils.diffMonths(mkt.get("endDate").toString(), mkt.get("startDate").toString());
            }
            String startDate = sdf.format(endDate);
            if (now.compareTo(endDate) > 0) {// 活动已失效
                startDate = "1".equals(delayDiscntType) ? DateUtils.addSeconds(
                        GetDateUtils.getNextMonthFirstDayFormat(), -1) : DateUtils.addSeconds(sdf.format(now), -1);
            }
            mkt.put("startDate", DateUtils.addSeconds(startDate, 1));
            mkt.put("endDate", sdf.format(RDate.addMonths(sdf.parse(startDate), brandNumber)));
            if ("59".equals(province)) {
                mkt.put("startDate", DateUtils.getDate());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        Date now = new Date();
        Date endDate = sdf.parse("20180314000000");
        System.out.println(now.compareTo(endDate) > 0);

        String startDate = sdf.format(endDate);

        System.out.println(DateUtils.addSeconds(startDate, 1));
        System.out.println(sdf.format(RDate.addMonths(sdf.parse(startDate), 12)));
    }

}
