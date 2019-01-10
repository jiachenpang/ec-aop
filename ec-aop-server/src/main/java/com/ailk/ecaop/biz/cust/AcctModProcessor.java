package com.ailk.ecaop.biz.cust;

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
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.GetThreePartInfoUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("AcctModProcessor")
public class AcctModProcessor extends BaseAopProcessor implements ParamsAppliable{

    private final static String endDate = "2050-12-31 23:59:59";
    private static final String[] PARAM_ARRAY = { "ecaop.masb.sbac.sglUniTradeParametersMapping",
    "ecaop.masb.chph.gifa.ParametersMapping","ecaop.trade.cbssNumberRoute.checkUserParametersMapping"};
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[3];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        String appCode = exchange.getAppCode();
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        // 地市转换
        ChangeCodeUtils ChangeCodeUtils = new ChangeCodeUtils();
        msg.put("eparchyCode", ChangeCodeUtils.changeEparchyUnStatic(msg));
        System.out.println("############测试打桩1#######" + msg);
        // 三户接口
        Map threePartMap = getThreePartInfo(ExchangeUtils.ofCopy(exchange, msg));
        msg.putAll(threePartMap);
        msg.put("appCode", appCode);
        System.out.println("############测试打桩2#######" + msg);
        // 准备预提交参数
        //生成itemId
        String itemId = (String) GetSeqUtil.getSeqFromCb(pmp[1], ExchangeUtils.ofCopy(exchange, msg), "seq_item_id", 1).get(0);
        msg.put("itemId", itemId);
        preData4CB(msg);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        try {
            new LanUtils().preData(pmp[0], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
            new LanUtils().xml2Json("ecaop.trades.sccc.cancelPre.template", exchange);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用预提交失败！" + e.getMessage());
        }
        Map preSubOut = exchange.getOut().getBody(Map.class);
        List<Map> rspInfo = (List<Map>) preSubOut.get("rspInfo");
        if (null == rspInfo || rspInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "cBSS统一预提交未返回应答信息");
        }
        Map retMap = new HashMap();
        retMap.put("orderId", rspInfo.get(0).get("provOrderId"));
        exchange.getOut().setBody(retMap);

    }

    private void preData4CB(Map msg) {
        msg.put("ordersId", msg.get("orderId"));
        msg.put("operTypeCode", "0");
        Map base = preBase(msg);
        Map ext = preExt(msg);
        msg.put("ext", ext);
        msg.put("base", base);

    }

    private Map preExt(Map msg) {
        Map ext = new HashMap();
        ext.put("tradeItem", preTradeItem(msg));
        ext.put("tradeAcct", preTradeAcct(msg));
        ext.put("tradeSubItem", preTradeSubItem(msg));
        return ext;
    }

    private Map preTradeSubItem(Map msg) {
    	LanUtils lan = new LanUtils();
    	List<Map> items = new ArrayList<Map>();
		items.add(lan.createTradeSubItemE("LINK_PHONE", msg.get("contactPhone"), (String)msg.get("itemId")));
		items.add(lan.createTradeSubItemE("LINK_NAME", msg.get("contactPerson"), (String)msg.get("itemId")));
		Map tradeSubItem = new HashMap();
		tradeSubItem.put("item", items);
		return tradeSubItem;
	}

	private Map preTradeAcct(Map msg) {
        Map tradeAcct= new HashMap();
        List<Map> items = new ArrayList<Map>();
        Map itemMap = new HashMap();
        //拼装acct节点
        itemMap.put("debutyUserId", msg.get("userId"));
        itemMap.put("payName", msg.get("payName"));
        itemMap.put("removeTag", "0");
        itemMap.put("acctId", msg.get("acctId"));
        itemMap.put("debutyCode", msg.get("debutyCode"));
        itemMap.put("xDatatype", "NULL");
        itemMap.put("payModeCode", msg.get("payModeCode"));
        itemMap.put("acctPasswd", "");
        items.add(itemMap);
        tradeAcct.put("item", items);
        System.out.println("############测试打桩4#######" + tradeAcct);
        return tradeAcct;
    }

    private Map preTradeItem(Map msg) {
        Map TradeItem = new HashMap();
        List<Map> items = new ArrayList<Map>();
        items.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", (String)msg.get("eparchyCode")));
        TradeItem.put("item", items);
        return TradeItem;
    }

    private Map preBase(Map msg) {
        ChangeCodeUtils util = new ChangeCodeUtils();
        Map base = new HashMap();
        base.put("advancePay", "0");
        base.put("userDiffCode", "00");
        base.put("acceptDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("inModeCode", util.getInModeCode((String) msg.get("appCode")));
        base.put("serinalNamber", msg.get("serialNumber"));
        base.put("usecustId", msg.get("custId"));
        base.put("actorCertNum", "");
        base.put("remark", "");
        base.put("feeState", "");
        base.put("contactPhone", "");
        base.put("nextDealTag", "Z");
        base.put("contactAddress", "");
        base.put("olcomTag", "0");
        base.put("custId", msg.get("custId"));
        base.put("acctId", msg.get("acctId"));
        base.put("userId", msg.get("userId"));
        base.put("custName", msg.get("custName"));
        base.put("foregift", "0");
        base.put("execTime", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("termIp", "10.124.0.7");
        base.put("actorCertTypeId", "");
        base.put("chkTag", "0");
        base.put("tradeId", msg.get("orderId"));
        base.put("actorPhone", "");
        base.put("operFee", "0");
        base.put("cancelTag", "0");
        base.put("tradeTypeCode", "0080");
        base.put("cityCode", msg.get("district"));
        base.put("eparchyCode", msg.get("eparchyCode"));
        base.put("netTypeCode", "50");
        base.put("contact", "");
        base.put("feeStaffId", "");
        base.put("checktypeCode", "0");
        base.put("subscribeId", msg.get("orderId"));
        base.put("brandCode", "4G00");
        base.put("productId", msg.get("productId"));
        System.out.println("############测试打桩3#######" + base);
        return base;
    }

    private Map getThreePartInfo(Exchange exchange) throws Exception {
    	Map body = (Map) exchange.getIn().getBody();
    	Map msg = (Map) body.get("msg");
    	LanUtils lan = new LanUtils();
    	String[] copyArray = { "operatorId", "province", "channelId", "city", "district", "channelType" };
        Map threePartMap = MapUtils.asMap("getMode", "101001101010001001", "serialNumber",
                msg.get("serialNumber"),
                "tradeTypeCode", "9999");
        MapUtils.arrayPut(threePartMap, msg, copyArray);     
        lan.preData(pmp[2], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
        lan.xml2Json("ecaop.trades.cbss.threePart.template", exchange);
        Map threePartInfo = (Map) exchange.getOut().getBody();
        List<Map> retAcctList = (ArrayList<Map>) threePartInfo.get("acctInfo");
        if (null == retAcctList || 0 == retAcctList.size()) {
            throw new EcAopServerBizException("9999", "三户信息校验接口:核心系统未返回账户信息");
        }
        List<Map> retUserList = (ArrayList<Map>) threePartInfo.get("userInfo");
        if (null == retUserList || 0 == retUserList.size()) {
            throw new EcAopServerBizException("9999", "三户信息校验接口:核心系统未返回用户信息");
        }
        List<Map> reCusttList = (ArrayList<Map>) threePartInfo.get("custInfo");
        if (null == reCusttList || 0 == reCusttList.size()) {
            throw new EcAopServerBizException("9999", "三户信息校验接口:核心系统未返回客户信息");
        }
        Map retMap = new HashMap();
        retMap.put("acctId", retAcctList.get(0).get("acctId"));
        retMap.put("userId", retUserList.get(0).get("userId"));
        retMap.put("custName", reCusttList.get(0).get("custName"));
        retMap.put("custId", reCusttList.get(0).get("custId"));
        // 老产品相关信息
        retMap.put("productId", retUserList.get(0).get("productId"));
        retMap.put("productTypeCode", retUserList.get(0).get("productTypeCode"));
        retMap.put("brandCode", retUserList.get(0).get("brandCode"));
        return retMap;
    }
    
    public void applyParams(String[] params) {
        // TODO Auto-generated method stub
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
