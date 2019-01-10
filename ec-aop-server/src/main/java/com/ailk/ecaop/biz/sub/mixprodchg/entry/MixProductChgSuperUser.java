package com.ailk.ecaop.biz.sub.mixprodchg.entry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.request.EcAopRequestParamException;
import org.n3r.ecaop.core.log.elk.ELKAopLogger;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.sub.mixprodchg.utils.MixThadeProductInfoUtils;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;

/**
 * 融合套餐变更接口预提交实体类的父类
 * @author wangmc
 * @date 2018-08-06
 */
public abstract class MixProductChgSuperUser {

    // 用户类型,0-虚拟,1-移网,2-宽带,3-固话
    protected String userType;

    // 初始的exchange,用于copy之后调用接口
    protected Exchange exchange;

    // 初始的msg,用于调用接口前的copy,调下游接口时建议新new一个msg调用,而不改变该msg的值
    protected Map msg;

    // 调接口准备的报文头
    protected ParametersMappingProcessor[] pmp;

    // 每个成员的号码,虚拟号码/移网成员号码/宽带成员号码/固话成员号码
    protected String serialNumber;

    // 虚拟预提交的订单号,因为在成员预提交时需要使用,提前定义好
    protected String mixTradeId;

    // 每个成员的产品信息 虚拟产品/移网成员产品/宽带成员产品/固话成员产品
    protected List<Map> productInfo = new ArrayList<Map>();

    // 成员三户的返回信息
    protected Map threePartRet;

    // 预提交base节点
    protected Map base = new HashMap();

    // 预提交ext节点
    protected Map ext = new HashMap();

    // 成员预提交的返回信息
    protected Map preSubRet;

    // 请求流水号
    protected Object apptx = exchange.getProperty(Exchange.APPTX);;

    // 用于存放一些多个方法可能使用到的属性信息
    protected Map<String, Object> properties = new HashMap<String, Object>();
    LanUtils lan = new LanUtils();

    public MixProductChgSuperUser(Exchange exchange, Map msg, ParametersMappingProcessor[] pmp, String serialNumber,
            String mixTradeId, String userType) {
        super();
        this.exchange = exchange;
        this.msg = msg;
        this.pmp = pmp;
        this.serialNumber = serialNumber;
        this.mixTradeId = mixTradeId;
        this.userType = userType;
        initProductInfo();
    }

    /**
     * 创建对象时,直接初始化产品信息
     */
    private void initProductInfo() {
        List<Map> productInfo = (List<Map>) msg.get("productInfo");
        if (IsEmptyUtils.isEmpty(productInfo)) {
            throw new EcAopRequestParamException("未传入产品信息,请确认参数");
        }
        for (Map product : productInfo) {
            if (userType.equals(product.get("productType"))) {
                this.productInfo.add(product);
            }
        }
        if (IsEmptyUtils.isEmpty(this.productInfo)) {
            throw new EcAopRequestParamException("未传入号码:" + serialNumber + " 的产品信息,请确认参数");
        }
    }

    public String getUserType() {
        return userType;
    }

    public Map getMsg() {
        return msg;
    }

    public Map getThreePartRet() {
        return threePartRet;
    }

    public void setThreePartRet(Map threePartRet) {
        this.threePartRet = threePartRet;
    }

    public String getMixTradeId() {
        return mixTradeId;
    }

    public List<Map> getProductInfo() {
        return productInfo;
    }

    public Map getExt() {
        return ext;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public LanUtils getLan() {
        return lan;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public Map getPreSubRet() {
        return preSubRet;
    }

    /**
     * 调用三户的方法
     * @return
     * @throws Exception 
     */
    public Map callThreePart() throws Exception {
        ELKAopLogger.logStr(apptx + "-----开始请求三户-----，查询的号码为 ： " + serialNumber);
        Map<String, String> threePartInfoMsg = new HashMap<String, String>();
        threePartInfoMsg.put("serialNumber", serialNumber);
        threePartInfoMsg.put("tradeTypeCode", "9999");
        threePartInfoMsg.put("getMode", "1111111111100013010000000100001");
        MapUtils.arrayPut(threePartInfoMsg, msg, MagicNumber.COPYARRAY);
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartInfoMsg);

        lan.preData(pmp[1], threePartExchange);
        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);

        Map out = threePartExchange.getOut().getBody(Map.class);

        // 校验是否没有返回三户信息
        String[] infoKeys = new String[]{"userInfo", "custInfo", "acctInfo"};
        Map errorDetail = MapUtils.asMap("userInfo", "用户信息", "custInfo", "客户信息", "acctInfo", "账户信息");
        Map threePartRet = new HashMap();
        for (String infoKey : infoKeys) {
            if (IsEmptyUtils.isEmpty(out.get(infoKey))) {
                throw new EcAopServerBizException("9999", "调三户未返回" + errorDetail.get(infoKey));
            }
            threePartRet.put(infoKey, ((List<Map>) out.get(infoKey)).get(0));
        }
        // 校验是否没返回老产品信息
        if (IsEmptyUtils.isEmpty(((Map) threePartRet.get("userInfo")).get("productInfo"))) {
            throw new EcAopServerBizException("9999", "调三户未返回产品信息");
        }
        threePartRet.put("productInfo", ((Map) threePartRet.get("userInfo")).get("productInfo"));
        this.threePartRet = threePartRet;
        if ("1".equals(userType)) {
            // 虚拟属性value需要用到移网userId
            List<Map> userInfo = (List<Map>) threePartRet.get("userInfo");
            msg.put("phoneUserId", userInfo.get(0).get("userId"));
        }
        ELKAopLogger.logStr(apptx + "查询的号码为 ： " + serialNumber + "-----请求三户返回结果 : " + threePartRet);
        return threePartRet;
    }

    /**
     * 处理产品的方法
     * @return
     * @throws Exception 
     */
    protected void preProductInfo() throws Exception {
        MixThadeProductInfoUtils mixpreData = new MixThadeProductInfoUtils();
        //        mixpreData.preProductInfo(threePartRet, msg, ext, properties, productInfo, serialNumber, userType);

        mixpreData.preProductInfo(this);
        ELKAopLogger.logStr(apptx + "拼装请求信息，号码为： " + serialNumber + ",场景为： |" + userType + "|，结果为ext:" + ext);
    }

    /**
     * 拼装ext节点的方法
     * @return
     */
    protected void preExt() {
        List<Map> tradePayRel = new ArrayList<Map>();
        Map item = new HashMap();
        Map tradeMap = new HashMap();
        MapUtils.arrayPut(tradeMap, msg, MagicNumber.COPYARRAY); // 获取payrelationId用

        item.put("payitemCode", "-1");
        item.put("acctPriority", "0");
        item.put("userPriority", "0");
        item.put("bindType", "1");
        // 只有虚拟的下发当前日期前六位，成员是下发下月日期前六位
        item.put("startAcycId", "0".equals(userType) ?
                GetDateUtils.getDate().substring(0, 6) : GetDateUtils.getNextMonthFirstDayFormat().substring(0, 6));
        item.put("endAcycId", "205001");
        item.put("defaultTag", "1");
        item.put("limitType", "0");
        item.put("limit", "0");
        item.put("complementTag", "0");
        item.put("addupMonths", "0");
        // 只有移网的下发P，其他下发0
        item.put("addupMethod", "1".equals(userType) ? "P" : "0");
        item.put("actTag", "1");
        String payRelId = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, tradeMap),
                "SEQ_PAYRELA_ID", 1).get(0);
        item.put("payrelationId", payRelId);
        tradePayRel.add(item);
        ext.put("tradePayrelation", tradePayRel);
    }

    /**
     * 拼装base节点的方法
     * @return
     */
    protected void preBase() {

        try {
            String startDate = GetDateUtils.getDate();
            // 获取三户信息拼base
            Map userInfo = (Map) threePartRet.get("userInfo");
            Map custInfo = (Map) threePartRet.get("custInfo");
            Map acctInfo = (Map) threePartRet.get("acctInfo");

            base.put("subscribeId", mixTradeId);
            base.put("tradeId", "0".equals(userType) ? mixTradeId : (String) GetSeqUtil.getSeqFromCb(pmp[0],
                    ExchangeUtils.ofCopy(exchange, msg), "seq_trade_id", 1).get(0));
            base.put("startDate", startDate);
            base.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            base.put("acceptDate", startDate);
            base.put("netTypeCode", "00CP");
            base.put("nextDealTag", "Z");
            base.put("olcomTag", "0");
            base.put("inModeCode", "0");
            // 虚拟的下发0110，成员下发0340
            base.put("tradeTypeCode", "0".equals(userType) ? "0110" : "0340");
            base.put("productId", properties.get("newProductId"));
            base.put("brandCode", properties.get("bookBrandCode"));
            base.put("userId", userInfo.get("userId"));
            base.put("custId", custInfo.get("custId"));
            base.put("usecustId", custInfo.get("custId"));
            base.put("acctId", acctInfo.get("acctId"));
            base.put("userDiffCode", "00");
            base.put("serinalNamber", serialNumber);
            base.put("custName", custInfo.get("custName"));
            base.put("termIp", "0:0:0:0:0:0:0:1");
            base.put("eparchyCode", userInfo.get("eparchyCode"));
            base.put("cityCode", userInfo.get("cityCode"));
            base.put("execTime", startDate);
            base.put("operFee", "0");
            base.put("foregift", "0");
            base.put("advancePay", "0");
            base.put("feeState", "");
            base.put("feeStaffId", "");
            base.put("cancelTag", "0");
            base.put("checktypeCode", "");
            base.put("chkTag", "0");
            base.put("actorName", "");
            base.put("actorCertTypeId", "");
            base.put("actorPhone", "");
            base.put("actorCertNum", "");
            base.put("contact", "");
            base.put("contactAddress", "");
            base.put("contactPhone", "");
            base.put("remark", "");

            if ("0".equals(userType)) {
                base.put("netTypeCode", "00CP");
                msg.put("mixUserId", userInfo.get("userId"));// 移网需要用到虚拟userId
            }
            else if ("1".equals(userType)) {
                base.put("netTypeCode", "0050");
            }
            else if ("2".equals(userType)) {
                base.put("netTypeCode", "0040");
            }
            else if ("3".equals(userType)) {
                base.put("netTypeCode", "0030");
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装" + userType + "场景，base节点报错：" + e.getMessage());
        }
    }

    /**
     * 调用预提交的方法
     * @return
     */
    protected void callPreSub() {
        // 封装预提交参数
        Map mixPreSubReq = MapUtils.asMap("operTypeCode", "0", "ordersId", mixTradeId, "serviceClassCode", "00CP",
                "base", base, "ext", ext);
        MapUtils.arrayPut(mixPreSubReq, msg, MagicNumber.COPYARRAY);
        Exchange mixExchange = ExchangeUtils.ofCopy(exchange, mixPreSubReq);
        try {
            new LanUtils().preData(pmp[3], mixExchange);
            ELKAopLogger.logStr("预提交: appTx: " + exchange.getProperty(Exchange.APPTX) + ", mixPreSubReq= "
                    + mixPreSubReq);
            CallEngine.wsCall(mixExchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
            new LanUtils().xml2Json("ecaop.trades.sccc.cancelPre.template", mixExchange);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "调用预提交失败！ 调用号码为：" + serialNumber + e.getMessage());
        }
        // 处理返回
        dealReturn(mixExchange);
    }

    private void dealReturn(Exchange mixExchange) {
        Map out = mixExchange.getOut().getBody(Map.class);

        List<Map> rspInfo = (List) out.get("rspInfo");
        if (null == rspInfo || rspInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "cBSS统一预提交未返回应答信息");
        }
        preSubRet = out;
    }

    /**
     * 流程控制的方法,该方法具体实现在成员中
     * @return
     * @throws Exception 
     */
    public abstract Map flowControl() throws Exception;
}
