package com.ailk.ecaop.biz.cust;

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
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("PostModProcessor")
public class PostModProcessor extends BaseAopProcessor {

    private final static String endDate = "2050-12-31 23:59:59";

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
        // 三户接口
        Map threePartMap = getThreePartInfo(ExchangeUtils.ofCopy(exchange, msg));
        msg.putAll(threePartMap);
        msg.put("appCode", appCode);
        // 准备预提交参数
        preData4CB(msg, exchange.getAppCode());
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        try {
            new LanUtils().preData("ecaop.trades.sccc.cancelPre.paramtersmapping", exchange);
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

    private void preData4CB(Map msg, String appCode) {
        msg.put("ordersId", msg.get("orderId"));
        msg.put("operTypeCode", "0");
        Map base = preBase(msg);
        Map ext = preExt(msg, appCode);
        msg.put("ext", ext);
        msg.put("base", base);

    }

    private Map preExt(Map msg, String appCode) {
        Map ext = new HashMap();
        ext.put("tradeItem", preTradeItem(msg, appCode));
        ext.put("tradePost", preTradePost(msg));
        return ext;
    }

    private Map preTradePost(Map msg) {
        Map tradePost = new HashMap();
        List<Map> items = new ArrayList<Map>();
        Map itemMap = new HashMap();
        String postTag = (String) msg.get("postTag");// 邮寄标志
        String postCyc = (String) msg.get("postCyc");// 邮寄周期
        String postContent = (String) msg.get("postContent");// 邮寄内容(账单，发票，其他,可多选)
        String[] postContentArry = postContent.replace("|", ",").split(",");
        List<Map> postInfoList = (List<Map>) msg.get("postInfo");
        String postContentNew = null;
        String postTypeset = null;
        String postName = null;
        String faxNbr = null;
        String email = null;
        String postAddress = null;
        String postCode = null;
        // 邮寄内容转换
        for (int i = 0; i < postContentArry.length; i++) {
            if ("0".equals(postContentArry[i])) {// 邮寄内容账单
                postContentNew = "0";
            }
            else if ("1".equals(postContentArry[i])) {// 邮寄内容发票
                postContentNew = "2";
            }
        }
        // 获取邮寄信息内容
        if (null != postInfoList && !postInfoList.isEmpty()) {
            postTypeset = (String) postInfoList.get(0).get("postTypeset");
            postName = (String) postInfoList.get(0).get("postName");
            faxNbr = (String) postInfoList.get(0).get("faxNbr");
            email = (String) postInfoList.get(0).get("email");
            postAddress = (String) postInfoList.get(0).get("postAddress");
            postCode = (String) postInfoList.get(0).get("postCode");
            if ("0".equals(postTypeset) && (null == postAddress || "".equals(postAddress))
                    && (null == postCode || "".equals(postCode))) {
                throw new EcAopServerBizException("9999", "邮政方式时邮寄地址和邮寄编码必填");
            }
            if ("1".equals(postTypeset) && (null == faxNbr || "".equals(faxNbr))) {
                throw new EcAopServerBizException("9999", "传真方式时传真电话必填");
            }
            if ("2".equals(postTypeset) && (null == email || "".equals(email))) {
                throw new EcAopServerBizException("9999", "传真方式时传真电话必填");
            }
        }
        itemMap.put("id", msg.get("userId"));
        itemMap.put("idType", "1");
        itemMap.put("postName", postName);
        itemMap.put("postTag", postTag);
        itemMap.put("postContent", postContentNew);// 暂时只穿一位，CB支持传数组,需要CB确认怎么传
        itemMap.put("postTypeset", postTypeset);
        itemMap.put("postCyc", postCyc);
        itemMap.put("postAddress", postAddress);
        itemMap.put("postCode", postCode);
        itemMap.put("email", email);
        itemMap.put("faxNbr", faxNbr);
        itemMap.put("startDate", GetDateUtils.getSysdateFormat());
        itemMap.put("endDate", endDate);
        items.add(itemMap);
        tradePost.put("item", items);
        return tradePost;
    }

    private Map preTradeItem(Map msg, String appCode) {
        Map TradeItem = new HashMap();
        List<Map> items = new ArrayList<Map>();
        if (new ChangeCodeUtils().isWOPre(appCode)) {
            items.add(LanUtils.createTradeItem("WORK_TRADE_ID", msg.get("orderId")));
        }
        items.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", "0371"));
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
        base.put("tradeTypeCode", "0090");
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
        Map body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        msg.put("tradeTypeCode", "9999");
        msg.put("getMode", "111001000000101311110000000000");
        LanUtils lan = new LanUtils();
        try {
            lan.preData("ecaop.trade.cbss.checkUserParametersMapping", exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
            lan.xml2Json("ecaop.trades.cbss.threePart.template", exchange);
        }
        catch (EcAopServerBizException e) {
            throw new EcAopServerBizException("9999", e.getMessage());
        }

        Map threePartInfo = exchange.getOut().getBody(Map.class);
        Map retMap = new HashMap();
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

}
