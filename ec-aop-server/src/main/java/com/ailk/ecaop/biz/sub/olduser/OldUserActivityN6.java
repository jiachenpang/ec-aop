package com.ailk.ecaop.biz.sub.olduser;

/**
 * Created by Liu JiaDi on 2016/7/8.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.google.common.collect.Maps;

/**
 * 该方法用于对3G北六省份老用户优惠购机进行处理
 * 
 * @auther Liu JiaDi
 * @create 2016_07_08_16:18
 *         需要外围系统把终端详细信息传下来即resourcesInfo
 *         产品不变更不下发产品信息，不能退订活动
 *         原有产品生效时间需要获取三户返回的生效时间
 *         不管用户合约是否到期，只要办理的目标套餐和原套装一致，optType就传03。
 *         不管用户合约是否到期，只要办理的目标套餐和原套装不一致，optType就新的传00旧的传01。
 *         主产品的生效时间写死下月初 失效时间 2050年
 *         附加产品和附加包的生效时间如同活动一样获取，如果库里面不存在END_OFFSET时，如同主产品时间写死
 */
@EcRocTag("oldUserActivityN6")
public class OldUserActivityN6 extends BaseAopProcessor {

    LanOpenApp4GDao dao = new LanOpenApp4GDao();
    LanUtils lan = new LanUtils();

    @Override
    public void process(Exchange exchange) throws Exception {

        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        String provinceCode = (String) msg.get("province");
        preDataForPreCommit(exchange);
        lan.preData("ecaop.mvoa.preSub.ParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.OrdForNorthSer." + provinceCode);
        lan.xml2Json("ecaop.trades.mvoa.preSub.template", exchange);

        Map retMap = exchange.getOut().getBody(Map.class);
        List<Map> rspInfoList = (ArrayList<Map>) retMap.get("rspInfo");
        if (null == rspInfoList || 0 == rspInfoList.size()) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "核心系统未返回订单信息.");
        }
        // 记录订单信息
        TradeManagerUtils.insert2CBSSTrade(msg);
        // 处理费用项
        Map feeInfo = dealCbssPreSubmitRet(rspInfoList);
        feeInfo.put("essSubscribeId", rspInfoList.get(0).get("bssOrderId"));
        exchange.getOut().setBody(feeInfo);

    }

    private Map dealCbssPreSubmitRet(List<Map> rspInfoList) {
        List<Map> retFeeList = new ArrayList<Map>();
        // 总费用,单位转化成厘
        Map feeInfo = Maps.newHashMap();
        Integer totalFee = 0;
        for (Map feeMap : rspInfoList) {
            List<Map> provinceOrderInfo = (List<Map>) feeMap.get("provinceOrderInfo");
            if (null == provinceOrderInfo || 0 == provinceOrderInfo.size()) {
                continue;
            }
            for (Map preovinceOrder : provinceOrderInfo) {
                totalFee = totalFee + Integer.valueOf(preovinceOrder.get("totalFee").toString());
                List<Map> preFeeInfoRsp = (List<Map>) preovinceOrder.get("preFeeInfoRsp");
                retFeeList.addAll(dealFee(preFeeInfoRsp));
            }
        }
        totalFee = totalFee * 10;
        feeInfo.put("totalFee", String.valueOf(totalFee));
        feeInfo.put("feeInfo", retFeeList);
        return feeInfo;
    }

    /**
     * 处理费用信息
     */
    private List<Map> dealFee(List<Map> feeList) {
        if (null == feeList || 0 == feeList.size()) {
            return new ArrayList<Map>();
        }
        List<Map> retFeeList = new ArrayList<Map>();
        for (Map fee : feeList) {
            String origFee = (String) fee.get("fee");
            String maxRelief = (String) fee.get("maxDerateFee");
            Map retFee = new HashMap();
            retFee.put("feeId", fee.get("feeTypeCode"));
            retFee.put("feeCategory", fee.get("feeMode"));
            retFee.put("feeDes", fee.get("feeTypeName"));
            retFee.put("maxRelief", Integer.parseInt(maxRelief) * 10);
            retFee.put("origFee", Integer.parseInt(origFee) * 10);
            retFeeList.add(retFee);
        }
        return retFeeList;
    }

    private void preDataForPreCommit(Exchange exchange) throws Exception {
        // try {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        msg.put("operTypeCode", "0");
        // msg.put("serviceClassCode", "00CP");// FIXME
        // 转换地市编码
        Map param = new HashMap();
        param.put("province", (msg.get("province")));
        param.put("city", msg.get("city"));
        String provinceCode = "00" + msg.get("province");
        msg.put("provinceCode", provinceCode);
        param.put("provinceCode", provinceCode);
        List<Map> eparchyCoderesult = dao.queryEparchyCode(param);
        String eparchyCode = (String) (eparchyCoderesult.get(0).get("PARAM_CODE"));
        msg.put("eparchyCode", eparchyCode);
        // 生成cb订单号
        String orderid = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_trade_id");
        msg.put("tradeId", orderid);
        msg.put("ordersId", orderid);
        // 生成ItemId号
        String ItemId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_item_id");
        msg.put("itemId", ItemId);
        // 地市编码转换
        msg.put("city", ChangeCodeUtils.changeEparchy(msg));
        List<Map> para = (List<Map>) msg.get("para");
        if (null != para && 0 != para.size()) {
            msg.put("phoneSpeedLevel", para.get(0).get("paraValue"));
        }
        // 获取主产品信息并寻找出用户原有产品
        List<Map> oldProductInfo = new ArrayList<Map>();
        List<Map> noChqangeProductInfo = new ArrayList<Map>();
        List<Map> productInfo = (List<Map>) msg.get("productInfo");
        if (null != productInfo && 0 != productInfo.size()) {
            for (Map product : productInfo) {
                String optType = (String) product.get("optType");
                if (StringUtils.isEmpty(optType)) {
                    throw new EcAopServerBizException("9999", "请填写产品操作信息，对应节点optType");
                }
                if ("01,03".contains(optType)) {
                    oldProductInfo.add(product);
                    if ("03".equals(optType)) {
                        noChqangeProductInfo.add(product);
                    }
                }
                if ("1".equals(product.get("productMode")) && "00,03".contains(optType)) {
                    msg.put("mainProduct", product.get("productId"));
                }
            }
            if (null != oldProductInfo) {
                productInfo.removeAll(oldProductInfo);
            }
            // 非变更产品信息
            msg.put("noChqangeProductInfo", noChqangeProductInfo);
            msg.put("noChange", "1");
        }
        else {
            // 用来区分是否存在套餐变更 0 不存在 1存在
            msg.put("noChange", "0");
        }
        // 获取三户信息
        getThreeInfo(exchange);
        // 获取产品活动信息
        preProductInfo(eparchyCode, provinceCode, productInfo, msg);
        // 准备base参数
        msg.put("base", preBaseData(msg));
        msg.put("ext", preExtDataforItem(msg));
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        // } catch (Exception e) {
        // e.printStackTrace();
        // throw new EcAopServerBizException("9999", e.getMessage());
        // }
    }

    // 拼装ext节点

    /**
     * @param msg
     * @return
     */
    private Map preExtDataforItem(Map msg) throws Exception {
        Map ext = new HashMap();
        ext.put("tradeProductType", changeStateDateTo14(preProductTpyeListData(msg)));
        ext.put("tradeProduct", changeStateDateTo14(preProductData(msg)));
        ext.put("tradeDiscnt", changeStateDateTo14(preDiscntData(msg)));
        ext.put("tradeSvc", changeStateDateTo14(preTradeSvcData(msg)));
        ext.put("tradeSp", changeStateDateTo14(preTradeSpData(msg)));
        ext.put("tradeElement", changeStateDateTo14(preTradeElementData(msg)));
        ext.put("tradePurchase", changeStateDateTo14(preTradePurchase(msg)));
        ext.put("tradefeeSub", changeStateDateTo14(preResFeeInfo(msg)));
        ext.put("tradeItem", changeStateDateTo14(preTradeItem(msg)));
        ext.put("tradeUser", changeStateDateTo14(preDataForTradeUser(msg)));
        ext.put("tradeSubItem", changeStateDateTo14(preDataForTradeSubItem(msg)));
        ext.put("tradeOther", changeStateDateTo14(preTradeOther(msg)));
        // ext.put("tradeSubItem", preTradeSubItem(msg));
        return ext;
    }

    // 准备base参数
    private Map preBaseData(Map msg) {
        try {
            Map base = new HashMap();
            base.put("custId", msg.get("custId"));
            base.put("contact", "");
            base.put("inModeCode", "E");
            base.put("actorCertNum", "");
            base.put("eparchyCode", msg.get("eparchyCode"));
            base.put("userId", msg.get("userId"));
            base.put("usecustId", msg.get("custId"));
            base.put("actorCertTypeId", "");
            base.put("acceptDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
            base.put("serinalNamber", msg.get("serialNumber"));
            base.put("contactAddress", "");
            base.put("feeState", "");
            base.put("userDiffCode", "00");
            base.put("brandCode", "3G00");
            base.put("foregift", "0");
            base.put("advancePay", "0");
            base.put("tradeId", msg.get("tradeId"));
            base.put("checktypeCode", "0");
            // base.put("cityCode", msg.get("district"));
            base.put("execTime", RDate.currentTimeStr("yyyyMMddHHmmss"));
            base.put("acctId", msg.get("acctId"));
            base.put("operFee", "0");
            base.put("feeStaffId", "");
            base.put("chkTag", "0");
            base.put("contactPhone", "");
            base.put("termIp", "132.35.81.217");
            base.put("olcomTag", "0");
            base.put("netTypeCode", "33");
            base.put("subscribeId", msg.get("tradeId"));
            base.put("cancelTag", "0");
            base.put("actorPhone", "0");
            base.put("tradeTypeCode", "0120");
            base.put("nextDealTag", "Z");

            // base.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
            // base.put("startAcycId", RDate.currentTimeStr("yyyyMM"));
            // base.put("areaCode", msg.get("eparchyCode"));
            // base.put("endAcycId", "203701");
            // base.put("tradeStaffId", msg.get("operatorId"));
            return base;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装base节点报错");
        }
    }

    /**
     * 三户信息接口
     */
    public void getThreeInfo(Exchange exchange) {
        String date = GetDateUtils.getDate();
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        String provinceCode = (String) msg.get("province");
        msg.put("tradeTypeCode", "9999");
        msg.put("getMode", "11111100100000141111");
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        try {
            lan.preData("ecaop.trade.n6.checkUserParametersMapping", exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.UsrForNorthSer" + "." + provinceCode);
            lan.xml2Json("ecaop.trades.cbss.threePart.template", exchange);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", e.getMessage());
        }

        Map checkUserMap = exchange.getOut().getBody(Map.class);
        List<Map> custInfo = (List<Map>) checkUserMap.get("custInfo");
        if (null == custInfo || 0 == custInfo.size()) {
            throw new EcAopServerBizException("9999", "客户信息未返回");
        }
        msg.put("custId", custInfo.get(0).get("custId"));
        msg.put("custName", custInfo.get(0).get("custName"));
        List<Map> userInfo = (List<Map>) checkUserMap.get("userInfo");
        if (null == userInfo || 0 == userInfo.size()) {
            throw new EcAopServerBizException("9999", "用户信息未返回");
        }
        msg.put("userId", userInfo.get(0).get("userId"));
        List<Map> acctInfo = (List<Map>) checkUserMap.get("acctInfo");
        if (null == acctInfo || 0 == acctInfo.size()) {
            throw new EcAopServerBizException("9999", "账户信息未返回");
        }
        msg.put("acctId", acctInfo.get(0).get("acctId"));
        List tempList = new ArrayList();
        List<Map> oldProductInfo = (List<Map>) userInfo.get(0).get("productInfo");
        if (null == oldProductInfo || 0 == oldProductInfo.size()) {
            throw new EcAopServerBizException("9999", "三户接口未返回产品信息");
        }
        for (int i = 0; i < oldProductInfo.size(); i++) {
            String productInactiveTime = (String) oldProductInfo.get(i).get("productInactiveTime");
            String productMode = (String) oldProductInfo.get(i).get("productMode");
            if (0 < date.compareTo(productInactiveTime) || "50".equals(productMode)) {
                tempList.add(oldProductInfo.get(i));
            }
            if (0 < productInactiveTime.compareTo(date) && "50".equals(productMode)) {
                throw new EcAopServerBizException("9999", "该用户存在生效合约，请使用续约接口进行操作");
            }
            if ("0".equals(msg.get("noChange")) && "00".equals(productMode) && 0 < productInactiveTime.compareTo(date)) {
                msg.put("mainProduct", oldProductInfo.get(i).get("productId"));
            }
        }
        if (null == msg.get("mainProduct")) {
            throw new EcAopServerBizException("9999", "用户原有套餐已经失效，请下发主套餐信息");
        }
        // 只有存在套餐变更的时候才会下发
        if ("1".equals(msg.get("noChange"))) {
            List<Map> noChqangeProductInfo = (List<Map>) msg.get("noChqangeProductInfo");
            if (null != noChqangeProductInfo && 0 != noChqangeProductInfo.size()) {
                for (int i = 0; i < oldProductInfo.size(); i++) {
                    for (int j = 0; j < noChqangeProductInfo.size(); j++) {
                        if (oldProductInfo.get(i).get("productId").equals(noChqangeProductInfo.get(j).get("productId"))) {
                            tempList.add(oldProductInfo.get(i));
                            continue;
                        }
                    }
                }
            }
            oldProductInfo.removeAll(tempList);
            // 老产品要求下发三户信息返回的生效时间
            System.out.println("===========oldProductInfo" + oldProductInfo);
            msg.put("oldProductInfo", oldProductInfo);
        }
        body.put("msg", msg);
        exchange.getIn().setBody(body);
    }

    // 拼装TRADE_SUB_ITEM
    private Map preDataForTradeSubItem(Map msg) {
        try {
            LanUtils lan = new LanUtils();
            List<Map> itemList = new ArrayList<Map>();
            String userId = (String) msg.get("userId");

            Map tradeSubItem = new HashMap();
            // itemList.add(lan.createTradeSubItemF("LINK_NAME", userId));
            // itemList.add(lan.createTradeSubItemF("LINK_PHONE", userId));
            itemList.add(lan.createTradeSubItemC((String) msg.get("discntItemid"), "tradeId", msg.get("tradeId"),
                    GetDateUtils.getNextMonthFirstDayFormat(),
                    "20501231235959"));
            tradeSubItem.put("item", itemList);
            return tradeSubItem;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TradeSubItem节点报错" + e.getMessage());
        }
    }

    // // 拼装TRADE_SUB_ITEM
    // private Map preDataForTradeSubItem(Map msg) {
    // // 处理开始
    // List<Map> itemList = new ArrayList();
    // Map tradeSubItem = new HashMap();
    // itemList.add(createTradeSubItemFor6("mobilecost", "3690", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("resourcesBrandName", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("packageType", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("resourcesBrandCode", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("userid", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("holdUnitType", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("partActiveProduct", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("isOwnerPhone", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("deviceType", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("devicebrand", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("deviceintag", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("deviceno", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("simCardNo", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("resourcesModelCode", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("bosscardprice", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("yuCunFee", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("mobilesaleprice", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("extraFee", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("frogitFee", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("imei", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("resourcesType", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("terminalType", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("distributionTag", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("itemId", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("resourcesModelName", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("months", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("feeitem", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("mobileinfo", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("tradetypecode", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("orgDeviceBrandCode", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("extrafeenx", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("deviceImei", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("discntItemId", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("isPartActive", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("SALE_PRODUCT_LIST", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("ASSURE_TYPE", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("", "", msg.get("itemId")));
    // itemList.add(createTradeSubItemFor6("", "", msg.get("itemId")));
    //
    // // 处理结束

    private Map preTradeItem(Map msg) {
        List<Map> itemList = new ArrayList<Map>();
        itemList.add(LanUtils.createTradeItem("IMSI",
                ((Map) ((List) msg.get("activityInfo")).get(0)).get("resourcesCode") + ""));
        itemList.add(LanUtils.createTradeItem("SIM", ""));
        itemList.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", msg.get("channelType") + ""));
        itemList.add(LanUtils.createTradeItem("IMMEDIACY_INFO", "0"));
        itemList.add(LanUtils.createTradeItem("BLACK_USER_TAG", "0"));
        Map tradeItem = new HashMap();
        tradeItem.put("item", itemList);
        return tradeItem;
    }

    private Map preTradeElementData(Map msg) {
        Map elementMap = (Map) msg.get("elementMap");
        Map tradeElement = new HashMap();
        if (elementMap != null && elementMap.size() > 0) {

            List<Map> item = new ArrayList();
            item.add(elementMap);
            tradeElement.put("item", item);
        }
        return tradeElement;
    }

    // 拼装TRADE_USER
    private Map preDataForTradeUser(Map msg) {
        try {
            Map tradeUser = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", msg.get("userId"));
            item.put("brandCode", "4G00");// FIXME
            item.put("productId", msg.get("mainProduct"));// FIXME
            item.put("netTypeCode", "0050");
            itemList.add(item);
            tradeUser.put("item", itemList);
            return tradeUser;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_USER节点报错" + e.getMessage());
        }
    }

    /**
     * 拼装TradeOther节点
     */
    private Map preTradeOther(Map msg) {
        try {
            Map tradeOther = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            List<Map> otherList = (List<Map>) msg.get("oldProductInfo");
            if (null != otherList && 0 != otherList.size()) {
                for (int i = 0; i < otherList.size(); i++) {
                    Map item = new HashMap();
                    item.put("xDatatype", "NULL");
                    item.put("rsrvValueCode", "NEXP");
                    item.put("rsrvValue", msg.get("userId"));
                    item.put("rsrvStr1", otherList.get(i).get("productId"));
                    item.put("modifyTag", "1");
                    item.put("rsrvStr2", otherList.get(i).get("productMode"));
                    item.put("rsrvStr3", "-9");
                    item.put("rsrvStr4", "4G000001");
                    item.put("rsrvStr5", "4G000001");
                    item.put("rsrvStr6", "-1");
                    item.put("rsrvStr7", "0");
                    item.put("rsrvStr9", "4G00");
                    item.put("rsrvStr10", msg.get("serialNumber"));
                    item.put("startDate", otherList.get(i).get("productActiveTime"));
                    item.put("endDate", GetDateUtils.getMonthLastDayFormat());
                    itemList.add(item);
                }
            }

            tradeOther.put("item", itemList);
            return tradeOther;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("", "拼装tradeOther节点错误" + e.getMessage());
        }
    }

    /**
     * 拼装tradeSp节点
     */
    public Map preTradeSpData(Map msg) {
        try {
            Map tardeSp = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            List<Map> sp = (List<Map>) msg.get("spList");
            for (int i = 0; i < sp.size(); i++) {
                Map item = new HashMap();
                item.put("userId", msg.get("userId"));
                item.put("serialNumber", msg.get("serialNumber"));
                item.put("productId", sp.get(i).get("productId"));
                item.put("packageId", sp.get(i).get("packageId"));
                item.put("partyId", sp.get(i).get("partyId"));
                item.put("spId", sp.get(i).get("spId"));
                item.put("spProductId", sp.get(i).get("spProductId"));
                item.put("firstBuyTime", GetDateUtils.getDate());
                item.put("paySerialNumber", msg.get("serialNumber"));
                if (null != sp.get(i).get("monthFirstDay")) {
                    item.put("startDate", sp.get(i).get("monthFirstDay"));
                }
                else {
                    item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                }
                if (null != sp.get(i).get("monthLasttDay")) {
                    item.put("endDate", sp.get(i).get("monthLasttDay"));
                }
                else {
                    item.put("endDate", "20501231235959");
                }
                item.put("updateTime", GetDateUtils.getDate());
                item.put("remark", "");
                item.put("modifyTag", "0");
                item.put("payUserId", msg.get("userId"));
                item.put("spServiceId", sp.get(i).get("spServiceId"));
                item.put("itemId", msg.get("itemId"));
                item.put("userIdA", msg.get("userId"));
                item.put("xDatatype", "NULL");
                itemList.add(item);
            }
            tardeSp.put("item", itemList);
            return tardeSp;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_SP节点报错" + e.getMessage());
        }
    }

    /**
     * 拼装tradediscnt节点
     */
    public Map preDiscntData(Map msg) {
        try {
            Map tradeDis = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            List<Map> discnt = (List<Map>) msg.get("discntList");
            for (int i = 0; i < discnt.size(); i++) {
                String itemid = TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID");
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("id", msg.get("userId"));
                item.put("idType", "1");
                item.put("userIdA", "-1");
                item.put("productId", discnt.get(i).get("productId"));
                item.put("packageId", discnt.get(i).get("packageId"));
                item.put("discntCode", discnt.get(i).get("discntCode"));
                item.put("specTag", "1");// FIXME
                item.put("relationTypeCode", "");
                String productMode = (String) discnt.get(i).get("productMode");
                if (StringUtils.isNotEmpty(productMode) && "50".equals(productMode)) {
                    msg.put("discntItemid", itemid);
                }
                if (null != discnt.get(i).get("monthFirstDay")) {
                    item.put("startDate", discnt.get(i).get("monthFirstDay"));
                }
                else {
                    item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                }
                if (null != discnt.get(i).get("monthLasttDay")) {
                    item.put("endDate", discnt.get(i).get("monthLasttDay"));
                }
                else {
                    item.put("endDate", "20501231235959");
                }
                item.put("modifyTag", "0");
                item.put("itemId", itemid);
                itemList.add(item);

            }
            tradeDis.put("item", itemList);
            return tradeDis;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_DISCNT节点报错" + e.getMessage());
        }
    }

    /**
     * 拼装TRADE_SVC节点
     */
    public Map preTradeSvcData(Map msg) {
        try {
            Map svcList = new HashMap();
            List<Map> svc = (List<Map>) msg.get("svcList");
            List svList = new ArrayList();
            for (int i = 0; i < svc.size(); i++) {
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("userId", msg.get("userId"));
                item.put("serviceId", svc.get(i).get("serviceId"));
                item.put("modifyTag", "0");
                if (null != svc.get(i).get("monthFirstDay")) {
                    item.put("startDate", svc.get(i).get("monthFirstDay"));
                }
                else {
                    item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                }
                if (null != svc.get(i).get("monthLasttDay")) {
                    item.put("endDate", svc.get(i).get("monthLasttDay"));
                }
                else {
                    item.put("endDate", "20501231235959");
                }
                item.put("productId", svc.get(i).get("productId"));
                item.put("packageId", svc.get(i).get("packageId"));
                item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
                item.put("userIdA", "-1");
                svList.add(item);
            }
            svcList.put("item", svList);
            return svcList;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_SVC节点报错" + e.getMessage());
        }
    }

    public Map preProductData(Map msg) {
        Map tradeProduct = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> product = (List<Map>) msg.get("productList");
        for (int i = 0; i < product.size(); i++) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", msg.get("userId"));
            item.put("productMode", product.get(i).get("productMode"));
            item.put("productId", product.get(i).get("productId"));
            item.put("brandCode", product.get(i).get("brandCode"));
            item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
            item.put("modifyTag", "0");
            if (null != product.get(i).get("monthFirstDay")) {
                item.put("startDate", product.get(i).get("monthFirstDay"));
            }
            else {
                item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
            }
            if (null != product.get(i).get("monthLasttDay")) {
                item.put("endDate", product.get(i).get("monthLasttDay"));
            }
            else {
                item.put("endDate", "20501231235959");
            }
            item.put("userIdA", MagicNumber.DEFAULT_NO_VALUE);
            itemList.add(item);
        }
        List<Map> oldProductInfo = (List<Map>) msg.get("oldProductInfo");
        if (null != oldProductInfo && 0 != oldProductInfo.size()) {
            for (int i = 0; i < oldProductInfo.size(); i++) {
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("userId", msg.get("userId"));
                item.put("productMode", oldProductInfo.get(i).get("productMode"));
                item.put("productId", oldProductInfo.get(i).get("productId"));
                item.put("brandCode", "4G00");
                item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
                item.put("modifyTag", "1");
                item.put("startDate", oldProductInfo.get(i).get("productActiveTime"));
                item.put("endDate", GetDateUtils.getMonthLastDayFormat());
                item.put("userIdA", MagicNumber.DEFAULT_NO_VALUE);
                itemList.add(item);
            }
        }
        tradeProduct.put("item", itemList);
        return tradeProduct;
    }

    public Map preProductTpyeListData(Map msg) {
        Map tradeProductType = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> productTpye = (List<Map>) msg.get("productTypeList");
        for (int i = 0; i < productTpye.size(); i++) {
            System.out.println("===========productTpye++++:" + productTpye.get(i));
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", msg.get("userId"));
            item.put("productMode", productTpye.get(i).get("productMode"));
            item.put("productId", productTpye.get(i).get("productId"));
            item.put("productTypeCode", productTpye.get(i).get("productTypeCode"));
            item.put("modifyTag", "0");
            if (null != productTpye.get(i).get("monthFirstDay")) {
                item.put("startDate", productTpye.get(i).get("monthFirstDay"));
            }
            else {
                item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
            }
            if (null != productTpye.get(i).get("monthLasttDay")) {
                item.put("endDate", productTpye.get(i).get("monthLasttDay"));
            }
            else {
                item.put("endDate", "20501231235959");
            }
            itemList.add(item);
        }
        List<Map> oldProductInfo = (List<Map>) msg.get("oldProductInfo");
        if (null != oldProductInfo && 0 != oldProductInfo.size()) {
            for (int i = 0; i < oldProductInfo.size(); i++) {
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("userId", msg.get("userId"));
                item.put("productMode", oldProductInfo.get(i).get("productMode"));
                item.put("productId", oldProductInfo.get(i).get("productId"));
                item.put("productTypeCode", "4G000001");
                item.put("modifyTag", "1");
                item.put("startDate", oldProductInfo.get(i).get("productActiveTime"));
                item.put("endDate", GetDateUtils.getMonthLastDayFormat());
                itemList.add(item);
            }
        }
        tradeProductType.put("item", itemList);
        return tradeProductType;

    }

    /**
     * 获取产品或者活动的有效期
     */
    public Map getproductDate(String actProductId) {
        String monthLasttDay = "20501231235959";
        String monthLastDay;
        String monthFirstDay = GetDateUtils.getNextMonthFirstDayFormat();
        Map actProParam = new HashMap();
        actProParam.put("PRODUCT_ID", actProductId);
        List<Map> actProductInfo = dao.queryProductAndPtypeProduct(actProParam);
        if (actProductInfo == null || actProductInfo.size() == 0) {
            throw new EcAopServerBizException("9999", "在产品表或者产品属性表中未查询到产品ID【" + actProductId + "】的产品信息");
        }
        try {
            Map detailProduct = actProductInfo.get(0);
            String subProductMode = (String) (detailProduct.get("PRODUCT_MODE"));
            String subProductTypeCode = (String) (detailProduct.get("PRODUCT_TYPE_CODE"));
            String strBrandcode = (String) (detailProduct.get("BRAND_CODE"));
            String endOffSet = String.valueOf(detailProduct.get("END_OFFSET"));
            String enableTag = (String) (detailProduct.get("ENABLE_TAG"));// 0-绝对时间（看开始时间） 1-相对时间
            String strStartUnit = (String) (detailProduct.get("START_UNIT"));// '0:天 1:自然天 2:月 3:自然月 4:年 5:自然年';
            String endUnit = (String) (null == detailProduct.get("END_UNIT") ? "0" : detailProduct.get("END_UNIT"));
            String startOffset = String.valueOf(detailProduct.get("START_OFFSET"));// 生效偏移时间
            String endEnableTag = String.valueOf(detailProduct.get("END_ENABLE_TAG"));// 0-绝对时间（看开始时间） 1-相对时间
            String endDate = String.valueOf(detailProduct.get("END_ABSOLUTE_DATE"));// END_ENABLE_TAG为1时，需要才发此结束时间

            // 如果值为空则进行默认处理
            if (!"null".equals(enableTag) && !"null".equals(startOffset)
                    && !"null".equals(strStartUnit)) {
                monthFirstDay = GetDateUtils.getSpecifyDateTime(
                        Integer.parseInt(enableTag),
                        Integer.parseInt(strStartUnit), GetDateUtils.getSysdateFormat(),
                        Integer.parseInt(startOffset));
            }
            if (!"null".equals(enableTag) && "0".equals(enableTag)) {
                startOffset = "0";
            }
            // 如果值为空则进行默认处理
            if (!"null".equals(endEnableTag) && "0".equals(endEnableTag) && !"null".equals(endDate)) {
                monthLasttDay = endDate;
            }
            if (!"null".equals(endOffSet) && !"null".equals(endUnit) && !"null".equals(endEnableTag)
                    && "1".equals(endEnableTag)) {
                monthLastDay = GetDateUtils.getSpecifyDateTime(
                        Integer.parseInt(endEnableTag),
                        Integer.parseInt(endUnit),
                        GetDateUtils.getSysdateFormat(),
                        Integer.parseInt(endOffSet)
                                + Integer.parseInt(startOffset));
                // 结束月最后一天
                monthLasttDay = GetDateUtils.getSpecifyDateTime(1, 6, monthLastDay,
                        -1);
                monthLasttDay = GetDateUtils.transDate(monthLasttDay, 14);

            }
            if (monthLasttDay.length() > 19) {
                monthLasttDay = monthLasttDay.substring(0, 19);
            }
            actProParam.put("PRODUCT_MODE", subProductMode);
            actProParam.put("PRODUCT_TYPE_CODE", subProductTypeCode);
            actProParam.put("BRAND_CODE", strBrandcode);
            actProParam.put("monthLasttDay", monthLasttDay);
            actProParam.put("monthFirstDay", monthFirstDay);
            actProParam.put("END_OFFSET", endOffSet);
            return actProParam;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "获取产品或者活动信息生失效时间失败，产品是：" + actProductId);
        }
    }

    /**
     * 处理产品、活动、终端、费用项信息
     */
    public void preProductInfo(String eparchyCode, String provinceCode, List<Map> productInfo, Map msg) {
        List<Map> productTypeList = new ArrayList<Map>();
        List<Map> productList = new ArrayList<Map>();
        List<Map> resourcesInfo = (List<Map>) msg.get("resourcesInfo");
        List<Map> discntList = new ArrayList<Map>();
        List<Map> svcList = new ArrayList<Map>();
        List<Map> spList = new ArrayList<Map>();
        String monthLasttDay = "20501231235959";
        String monthFirstDay = GetDateUtils.getNextMonthFirstDayFormat();
        String activityStartDay = "";
        String addProStartDay = "";
        String activityEndDay = "";
        String addProEndDay = "";
        String resourcesCode = null;
        String itemId = (String) msg.get("itemId");
        String userId = (String) msg.get("userId");
        Map ext = new HashMap();
        List<Map> activityInfo = (List<Map>) msg.get("activityInfo");
        if (activityInfo != null && activityInfo.size() > 0) {
            for (int i = 0; i < activityInfo.size(); i++) {
                Map activityMap = activityInfo.get(i);
                if (activityMap.isEmpty()) {
                    continue;
                }
                String actPlanId = (String) (activityMap.get("actPlanId"));
                if (activityMap.containsKey("resourcesCode")) {
                    resourcesCode = (String) (activityMap.get("resourcesCode"));
                    // 要入订单表，然后正式提交的时候需要
                    msg.put("resourcesCode", resourcesCode);
                }
                String resourceFee = (String) (activityMap.get("resourcesFee"));
                if (StringUtils.isNotEmpty(resourceFee)) {
                    msg.put("resourceFee", resourceFee);
                }
                Map actparam = new HashMap();
                actparam.put("PROVINCE_CODE", provinceCode);
                actparam.put("COMMODITY_ID", actPlanId);
                actparam.put("EPARCHY_CODE", eparchyCode);
                actparam.put("FIRST_MON_BILL_MODE", null);
                List<Map> actInfo = dao.queryActivityProductInfo(actparam);
                if (actInfo == null || actInfo.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + actPlanId + "】的产品信息");
                }
                String actProductId = String.valueOf(actInfo.get(0).get("PRODUCT_ID"));
                List<Map> actInfoResult = dao.queryProductInfo(actparam);
                // 获取产品或者活动的生效和失效时间
                Map productDate = getproductDate(actProductId);
                activityStartDay = (String) productDate.get("monthFirstDay");
                activityEndDay = (String) productDate.get("monthLasttDay");
                String subProductMode = (String) (productDate.get("PRODUCT_MODE"));
                String subProductTypeCode = (String) (productDate.get("PRODUCT_TYPE_CODE"));
                // 要入订单表，然后正式提交的时候需要
                msg.put("ACTIVITY_TYPE", subProductTypeCode);
                System.out.println(";';ACTIVITY_TYPE" + subProductTypeCode);
                String strBrandcode = (String) (productDate.get("BRAND_CODE"));
                String resActivityper = String.valueOf(productDate.get("END_OFFSET"));
                if (null != actInfoResult && actInfoResult.size() > 0) {
                    for (int j = 0; j < actInfoResult.size(); j++) {

                        if ("D".equals(actInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map dis = new HashMap();
                            dis.put("productId", actInfoResult.get(j).get("PRODUCT_ID"));
                            dis.put("packageId", actInfoResult.get(j).get("PACKAGE_ID"));
                            dis.put("discntCode", actInfoResult.get(j).get("ELEMENT_ID"));
                            dis.put("productMode", "50");
                            dis.put("monthFirstDay", activityStartDay);
                            dis.put("monthLasttDay", activityEndDay);
                            discntList.add(dis);
                        }
                        if ("S".equals(actInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map svc = new HashMap();
                            svc.put("serviceId", actInfoResult.get(j).get("ELEMENT_ID"));
                            svc.put("productId", actInfoResult.get(j).get("PRODUCT_ID"));
                            svc.put("packageId", actInfoResult.get(j).get("PACKAGE_ID"));
                            svc.put("productMode", "50");
                            svc.put("monthFirstDay", activityStartDay);
                            svc.put("monthLasttDay", activityEndDay);
                            svcList.add(svc);
                        }
                        if ("X".equals(actInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            String spId = "-1";
                            String partyId = "-1";
                            String spProductId = "-1";
                            Map spItemParam = new HashMap();
                            spItemParam.put("PTYPE", "X");
                            spItemParam.put("COMMODITY_ID", actPlanId);
                            spItemParam.put("PROVINCE_CODE", provinceCode);
                            spItemParam.put("PID", actInfoResult.get(j).get("ELEMENT_ID"));
                            List<Map> spItemInfoResult = dao.queryProductItemInfo(spItemParam);
                            if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                        + actInfoResult.get(j).get("ELEMENT_ID") + "】的产品属性信息");
                            }
                            for (int l = 0; l < spItemInfoResult.size(); l++) {
                                Map spItemInfo = spItemInfoResult.get(l);
                                if ("SP_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                    spId = (String) (spItemInfo.get("ATTR_VALUE"));
                                }
                                if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                    partyId = (String) (spItemInfo.get("ATTR_VALUE"));
                                }
                                if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                    spProductId = (String) (spItemInfo.get("ATTR_VALUE"));
                                }
                            }
                            Map sp = new HashMap();
                            sp.put("productId", actInfoResult.get(j).get("PRODUCT_ID"));
                            sp.put("packageId", actInfoResult.get(j).get("PACKAGE_ID"));
                            sp.put("partyId", partyId);
                            sp.put("spId", spId);
                            sp.put("spProductId", spProductId);
                            sp.put("spServiceId", actInfoResult.get(j).get("ELEMENT_ID"));
                            sp.put("productMode", "50");
                            sp.put("monthFirstDay", activityStartDay);
                            sp.put("monthLasttDay", activityEndDay);
                            spList.add(sp);
                        }
                    }
                }
                Map productTpye = new HashMap();
                Map product = new HashMap();
                if (!"0".equals(actPlanId)) {

                    productTpye.put("productMode", subProductMode);
                    productTpye.put("productId", actProductId);
                    productTpye.put("productTypeCode", subProductTypeCode);
                    productTpye.put("monthFirstDay", activityStartDay);
                    productTpye.put("monthLasttDay", activityEndDay);
                    product.put("brandCode", strBrandcode);
                    product.put("productId", actProductId);
                    product.put("productMode", subProductMode);
                    product.put("monthFirstDay", activityStartDay);
                    product.put("monthLasttDay", activityEndDay);
                    productTypeList.add(productTpye);
                    productList.add(product);

                }

                for (int j = 0; j < actInfoResult.size(); j++) {
                    Map subProductInfo = actInfoResult.get(j);
                    if ("A".equals(subProductInfo.get("ELEMENT_TYPE_CODE"))) {
                        String packageId = String.valueOf(subProductInfo.get("PACKAGE_ID"));
                        String elementDiscntId = String.valueOf(subProductInfo.get("ELEMENT_ID"));

                        // 对终端信息进行预占
                        if (!"0".equals(actPlanId)) {
                            if (resourcesInfo != null && resourcesInfo.size() > 0) {
                                Map rescMap = resourcesInfo.get(0);
                                String salePrice = ""; // 销售价格（单位：厘）
                                if (StringUtils.isNotEmpty((String) rescMap.get("salePrice"))) {
                                    salePrice = (String) (rescMap.get("salePrice"));
                                    salePrice = String.valueOf(Integer.parseInt(salePrice) / 10);
                                }
                                String resCode = resourcesCode;// 资源唯一标识
                                String resBrandCode = (String) (rescMap.get("resourcesBrandCode")); // 品牌
                                String resBrandName = ""; // 终端品牌名称
                                if (StringUtils.isEmpty((String) (rescMap.get("resourcesBrandName")))) {
                                    resBrandName = "无说明";
                                }
                                else {
                                    resBrandName = (String) (rescMap.get("resourcesBrandName"));
                                }
                                String resModelCode = (String) (rescMap.get("resourcesModelCode")); // 型号
                                String resModeName = ""; // 终端型号名称
                                if (StringUtils.isEmpty((String) (rescMap.get("resourcesModelName")))) {
                                    resModeName = "无说明";
                                }
                                else {
                                    resModeName = (String) (rescMap.get("resourcesModelName"));
                                }
                                String machineTypeCode = "";// 终端机型编码
                                if (StringUtils.isEmpty((String) (rescMap.get("machineTypeCode")))) {
                                    machineTypeCode = "无说明";
                                }
                                else {
                                    machineTypeCode = (String) (rescMap.get("machineTypeCode"));
                                }
                                String orgdeviceBrandCode = "";
                                if (StringUtils.isNotEmpty((String) rescMap.get("orgDeviceBrandCode"))) {
                                    orgdeviceBrandCode = (String) (rescMap.get("orgDeviceBrandCode"));// 3GESS维护品牌，当iphone时品牌与上面的一致
                                }
                                String cost = ""; // 成本价格（单位：厘）
                                if (StringUtils.isNotEmpty((String) (rescMap.get("cost")))) {
                                    cost = (String) (rescMap.get("cost"));
                                    cost = String.valueOf(Integer.parseInt(cost) / 10);
                                }
                                String machineTypeName = ""; // 终端机型名称
                                if (StringUtils.isEmpty((String) (rescMap.get("machineTypeName")))) {
                                    machineTypeName = "无说明";
                                }
                                else {
                                    machineTypeName = (String) (rescMap.get("machineTypeName"));
                                }
                                String terminalSubtype = "";
                                if (StringUtils.isNotEmpty((String) (rescMap.get("terminalTSubType")))) {
                                    terminalSubtype = (String) (rescMap.get("terminalTSubType"));
                                }
                                String terminalType = (String) (rescMap.get("terminalType"));// 终端类别编码
                                if (!"0".equals(actPlanId)) {
                                    Map elemntItem1 = new HashMap();
                                    elemntItem1.put("userId", userId);
                                    elemntItem1.put("productId", actProductId);
                                    elemntItem1.put("packageId", packageId);
                                    elemntItem1.put("idType", "C");
                                    elemntItem1.put("id", elementDiscntId);
                                    elemntItem1.put("modifyTag", "0");
                                    elemntItem1.put("startDate", activityStartDay);
                                    elemntItem1.put("endDate", activityEndDay);
                                    elemntItem1.put("modifyTag", "0");
                                    elemntItem1
                                            .put("itemId", TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID"));
                                    elemntItem1.put("userIdA", "-1");
                                    elemntItem1.put("xDatatype", "NULL");
                                    msg.put("elementMap", elemntItem1);
                                }
                                List<Map> tradeSubItem = new ArrayList<Map>();
                                tradeSubItem.add(createTradeSubItemG("deviceType",
                                        decodeTerminalType(terminalType), itemId));
                                tradeSubItem.add(createTradeSubItemG("deviceno", machineTypeCode, itemId));
                                tradeSubItem.add(createTradeSubItemG("devicebrand", orgdeviceBrandCode, itemId));
                                tradeSubItem.add(createTradeSubItemG("deviceintag", resBrandName, itemId));
                                tradeSubItem.add(createTradeSubItemG("mobilecost",
                                        String.valueOf(Integer.parseInt(cost) / 100), itemId));
                                tradeSubItem.add(createTradeSubItemG("mobileinfo", machineTypeName, itemId));
                                tradeSubItem.add(createTradeSubItemG("mobilesaleprice",
                                        String.valueOf(Integer.parseInt(salePrice) / 100), itemId));
                                tradeSubItem
                                        .add(createTradeSubItemG("resActivityper", resActivityper, itemId));
                                tradeSubItem.add(createTradeSubItemG("partActiveProduct",
                                        (String) msg.get("mainProduct"), itemId));
                                tradeSubItem.add(createTradeSubItemG("resourcesBrandCode", resBrandCode,
                                        itemId));
                                tradeSubItem.add(createTradeSubItemG("resourcesBrandName", resBrandName,
                                        itemId));
                                tradeSubItem.add(createTradeSubItemG("resourcesModelCode", resModelCode,
                                        itemId));
                                tradeSubItem.add(createTradeSubItemG("resourcesModelName", resModeName, itemId));
                                tradeSubItem.add(createTradeSubItemG("SALE_PRODUCT_LIST", subProductTypeCode,
                                        itemId));

                                if (StringUtils.isNotEmpty(terminalSubtype)) // 有时为空
                                {
                                    tradeSubItem.add(createTradeSubItemG("terminalTSubType", terminalSubtype,
                                            itemId));
                                }
                                tradeSubItem.add(createTradeSubItemG("terminalType", terminalType, itemId));
                                tradeSubItem.add(createTradeSubItemG("isOwnerPhone", "0", itemId));
                                tradeSubItem.add(createTradeSubItemG("isPartActive", "0", itemId));
                                tradeSubItem.add(createTradeSubItemG("holdUnitType", "01", itemId));
                                tradeSubItem.add(createTradeSubItemG("resourcesType", "07", itemId));
                                tradeSubItem.add(createTradeSubItemG("packageType", "10", itemId));
                                tradeSubItem.add(createTradeSubItemG("itemid", itemId, itemId));

                                msg.put("tradeSubItem", tradeSubItem);
                                // /拼装SUB_ITEM结束

                                // tf_b_trade_purchase表的台账
                                Map tradePurchase = new HashMap();
                                tradePurchase.put("userId", userId);
                                tradePurchase.put("bindsaleAttr", elementDiscntId);
                                tradePurchase.put("extraDevFee", "");
                                tradePurchase.put("mpfee", resourceFee);
                                tradePurchase.put("feeitemCode", "4310");
                                tradePurchase.put("foregift", "0");
                                tradePurchase.put("foregiftCode", "-1");
                                tradePurchase.put("foregiftBankmod", "");
                                tradePurchase.put("agreementMonths", resActivityper);
                                tradePurchase.put("endMode", "N");
                                tradePurchase.put("deviceType", machineTypeCode);
                                tradePurchase.put("moblieCost", cost);
                                tradePurchase.put("deviceName", resModeName);
                                tradePurchase.put("deviceBrand", orgdeviceBrandCode);
                                tradePurchase.put("imei", resCode);
                                tradePurchase.put("listBank", "");
                                tradePurchase.put("listFee", "");
                                tradePurchase.put("listCode", "");
                                tradePurchase.put("creditOrg", "");
                                tradePurchase.put("creditType", "");
                                tradePurchase.put("creditCardNum", "");
                                tradePurchase.put("agreement", "");
                                tradePurchase.put("productId", actProductId);
                                tradePurchase.put("packgaeId", packageId);
                                tradePurchase.put("staffId", msg.get("operatorId"));
                                tradePurchase.put("departId", msg.get("channelId"));
                                tradePurchase.put("startDate", activityStartDay);
                                tradePurchase.put("endDate", activityEndDay);
                                tradePurchase.put("remark", "");
                                tradePurchase.put("itemId", itemId);
                                tradePurchase.put("xDatatype", "NULL");
                                msg.put("tradePurchase", tradePurchase);
                            }
                            else {
                                throw new EcAopServerBizException("9999", "终端详细信息resourcesInfo为空，请核实请求");
                            }
                        } // end
                    } // END IF A
                }

            }
        }
        else {
            throw new EcAopServerBizException("9999", "优惠购机业务必须有合约，请选择合约");
        }
        // 处理产品信息
        if (productInfo != null && productInfo.size() > 0) {
            for (int i = 0; i < productInfo.size(); i++) {
                List<Map> packageElement = (List<Map>) productInfo.get(i).get("packageElement");
                String productMode = String.valueOf(productInfo.get(i).get("productMode"));
                String firstMonBillMode = "02";
                String productId = String.valueOf(productInfo.get(i).get("productId"));

                if ("1".equals(productMode)) {
                    System.out.println("===========主产品产品处理");
                    Map productTpye = new HashMap();
                    Map product = new HashMap();

                    String commodityId = productId;
                    String strBrandCode = "";
                    String strProductTypeCode = "";
                    String strProductMode = "";
                    Map proparam = new HashMap();
                    proparam.put("PROVINCE_CODE", provinceCode);
                    proparam.put("COMMODITY_ID", commodityId);
                    proparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                    proparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                    List<Map> productInfoResult = dao.queryProductInfo(proparam);
                    if (productInfoResult == null || productInfoResult.size() == 0) {
                        throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品信息");
                    }
                    // 速率校验
                    productInfoResult = TradeManagerUtils.chooseSpeed(productInfoResult, msg.get("phoneSpeedLevel")
                            + "");
                    productId = String.valueOf(productInfoResult.get(0).get("PRODUCT_ID"));
                    if (productId.equals("-1")) {
                        productId = String.valueOf(productInfoResult.get(1).get("PRODUCT_ID"));
                    }
                    Map itparam = new HashMap();
                    itparam.put("PROVINCE_CODE", provinceCode);
                    itparam.put("PID", productId);
                    itparam.put("COMMODITY_ID", commodityId);
                    itparam.put("PTYPE", "U");
                    List<Map> productItemInfoResult = dao.queryProductItemInfo(itparam);
                    if (productItemInfoResult == null || productItemInfoResult.size() == 0) {
                        throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品属性信息");
                    }
                    for (int j = 0; j < productInfoResult.size(); j++) {

                        if ("D".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map dis = new HashMap();
                            dis.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                            dis.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                            dis.put("discntCode", productInfoResult.get(j).get("ELEMENT_ID"));
                            dis.put("productMode", productInfoResult.get(j).get("PRODUCT_MODE"));
                            discntList.add(dis);

                        }
                        if ("S".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map svc = new HashMap();
                            svc.put("serviceId", productInfoResult.get(j).get("ELEMENT_ID"));
                            svc.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                            svc.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                            svcList.add(svc);
                        }
                        if ("X".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            String spId = "-1";
                            String partyId = "-1";
                            String spProductId = "-1";
                            Map spItemParam = new HashMap();
                            spItemParam.put("PTYPE", "X");
                            spItemParam.put("COMMODITY_ID", commodityId);
                            spItemParam.put("PROVINCE_CODE", provinceCode);
                            spItemParam.put("PID", productInfoResult.get(j).get("ELEMENT_ID"));
                            List<Map> spItemInfoResult = dao.queryProductItemInfo(spItemParam);
                            if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                        + productInfoResult.get(j).get("ELEMENT_ID") + "】的产品属性信息");
                            }

                            for (int l = 0; l < spItemInfoResult.size(); l++) {
                                Map spItemInfo = spItemInfoResult.get(l);
                                if ("SP_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                    spId = (String) (spItemInfo.get("ATTR_VALUE"));
                                }
                                if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                    partyId = (String) (spItemInfo.get("ATTR_VALUE"));
                                }
                                if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                    spProductId = (String) (spItemInfo.get("ATTR_VALUE"));
                                }
                            }

                            Map sp = new HashMap();
                            sp.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                            sp.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                            sp.put("partyId", partyId);
                            sp.put("spId", spId);
                            sp.put("spProductId", spProductId);
                            sp.put("spServiceId", productInfoResult.get(j).get("ELEMENT_ID"));
                            spList.add(sp);
                        }

                    }

                    strProductMode = String.valueOf(productInfoResult.get(0).get("PRODUCT_MODE"));
                    strBrandCode = getValueFromItem("BRAND_CODE", productItemInfoResult);
                    strProductTypeCode = getValueFromItem("PRODUCT_TYPE_CODE", productItemInfoResult);

                    productTpye.put("productMode", strProductMode);
                    productTpye.put("productId", productId);
                    productTpye.put("productTypeCode", strProductTypeCode);
                    product.put("brandCode", strBrandCode);
                    product.put("productId", productId);
                    product.put("productMode", strProductMode);

                    productTypeList.add(productTpye);
                    productList.add(product);
                    // 附加包
                    if (packageElement != null && packageElement.size() > 0) {
                        for (int n = 0; n < packageElement.size(); n++) {
                            Map peparam = new HashMap();
                            peparam.put("PROVINCE_CODE", provinceCode);
                            peparam.put("COMMODITY_ID", productId);
                            peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                            peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                            peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                            List<Map> packageElementInfo = dao.queryPackageElementInfo(peparam);
                            if (packageElementInfo != null && packageElementInfo.size() > 0) {
                                for (Map packageMap : packageElementInfo) {
                                    if ("D".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                        Map dis = new HashMap();
                                        String elementId = String.valueOf(packageMap.get("ELEMENT_ID"));
                                        dis.put("productId", packageMap.get("PRODUCT_ID"));
                                        dis.put("packageId", packageMap.get("PACKAGE_ID"));
                                        dis.put("discntCode", packageMap.get("ELEMENT_ID"));
                                        Map discntDateMap = TradeManagerUtils.getDiscntEffectTime(elementId,
                                                monthFirstDay, monthLasttDay);
                                        dis.put("monthFirstDay", discntDateMap.get("monthFirstDay"));
                                        dis.put("monthLasttDay", discntDateMap.get("monthLasttDay"));
                                        discntList.add(dis);
                                    }
                                    if ("S".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                        Map svc = new HashMap();
                                        svc.put("serviceId", packageMap.get("ELEMENT_ID"));
                                        svc.put("productId", packageMap.get("PRODUCT_ID"));
                                        svc.put("packageId", packageMap.get("PACKAGE_ID"));
                                        svcList.add(svc);
                                    }
                                    if ("X".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                        String spId = "-1";
                                        String partyId = "-1";
                                        String spProductId = "-1";
                                        Map spItemParam = new HashMap();
                                        spItemParam.put("PTYPE", "X");
                                        spItemParam.put("COMMODITY_ID", productId);
                                        spItemParam.put("PROVINCE_CODE", provinceCode);
                                        spItemParam.put("PID", packageMap.get("ELEMENT_ID"));
                                        List<Map> spItemInfoResult = dao.queryProductItemInfo(spItemParam);
                                        if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                            throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                                    + packageMap.get("ELEMENT_ID") + "】的产品属性信息");
                                        }
                                        for (int j = 0; j < spItemInfoResult.size(); j++) {
                                            Map spItemInfo = spItemInfoResult.get(j);
                                            if ("SP_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                                spId = (String) (spItemInfo.get("ATTR_VALUE"));
                                            }
                                            else if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                                partyId = (String) (spItemInfo.get("ATTR_VALUE"));
                                            }
                                            else if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                                spProductId = (String) (spItemInfo.get("ATTR_VALUE"));
                                            }
                                        }
                                        Map sp = new HashMap();
                                        sp.put("productId", packageMap.get("PRODUCT_ID"));
                                        sp.put("packageId", packageMap.get("PACKAGE_ID"));
                                        sp.put("partyId", partyId);
                                        sp.put("spId", spId);
                                        sp.put("spProductId", spProductId);
                                        sp.put("spServiceId", packageMap.get("ELEMENT_ID"));
                                        spList.add(sp);
                                    }
                                }
                            }
                        }
                    }
                }
                if ("0".equals(productMode)) {
                    System.out.println("===========附加产品处理");
                    Map productTpye = new HashMap();
                    Map product = new HashMap();

                    String commodityId = productId;
                    String addProductId = "";
                    String strBrandCode = "";
                    String strProductTypeCode = "";
                    String strProductMode = "";

                    Map addproparam = new HashMap();
                    addproparam.put("PROVINCE_CODE", provinceCode);
                    addproparam.put("COMMODITY_ID", commodityId);
                    addproparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                    addproparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                    List<Map> addproductInfoResult = dao.queryAddProductInfo(addproparam);
                    if (addproductInfoResult == null || addproductInfoResult.size() == 0) {
                        throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品信息");
                    }
                    // 校验速率
                    addproductInfoResult = TradeManagerUtils.chooseSpeed(addproductInfoResult,
                            msg.get("phoneSpeedLevel") + "");
                    addProductId = String.valueOf(addproductInfoResult.get(0).get("PRODUCT_ID"));
                    String isFinalCode = TradeManagerUtils.getEndDate(addProductId);
                    if ("N".equals(isFinalCode) || "X".equals(isFinalCode)) {
                        Map productDate = getproductDate(productId);
                        addProStartDay = (String) productDate.get("monthFirstDay");
                        addProEndDay = (String) productDate.get("monthLasttDay");
                    }
                    else if ("Y".equals(isFinalCode)) {
                        if (StringUtils.isEmpty(activityEndDay)) {
                            throw new EcAopServerBizException("9999", "所选附加产品" + productId + "生失效时间应和合约保持一致，请选择合约");
                        }
                        addProStartDay = activityStartDay;
                        addProEndDay = activityEndDay;
                    }
                    for (int j = 0; j < addproductInfoResult.size(); j++) {
                        if ("D".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map dis = new HashMap();
                            String elementId = String.valueOf(addproductInfoResult.get(j).get("ELEMENT_ID"));
                            dis.put("productId", addproductInfoResult.get(j).get("PRODUCT_ID"));
                            dis.put("packageId", addproductInfoResult.get(j).get("PACKAGE_ID"));
                            dis.put("discntCode", elementId);
                            if (!"Y".equals(isFinalCode)) {
                                Map discntDateMap = TradeManagerUtils.getDiscntEffectTime(elementId, addProStartDay,
                                        addProEndDay);
                                dis.put("monthFirstDay", discntDateMap.get("monthFirstDay"));
                                dis.put("monthLasttDay", discntDateMap.get("monthLasttDay"));
                            }
                            else {
                                dis.put("monthFirstDay", addProStartDay);
                                dis.put("monthLasttDay", addProEndDay);
                            }

                            discntList.add(dis);
                        }
                        if ("S".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map svc = new HashMap();
                            svc.put("serviceId", addproductInfoResult.get(j).get("ELEMENT_ID"));
                            svc.put("productId", addproductInfoResult.get(j).get("PRODUCT_ID"));
                            svc.put("packageId", addproductInfoResult.get(j).get("PACKAGE_ID"));
                            svc.put("monthFirstDay", addProStartDay);
                            svc.put("monthLasttDay", addProEndDay);
                            svcList.add(svc);
                        }
                        if ("X".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            String spId = "-1";
                            String partyId = "-1";
                            String spProductId = "-1";
                            Map spItemParam = new HashMap();
                            spItemParam.put("PTYPE", "X");
                            spItemParam.put("COMMODITY_ID", commodityId);
                            spItemParam.put("PROVINCE_CODE", provinceCode);
                            spItemParam.put("PID", addproductInfoResult.get(j).get("ELEMENT_ID"));
                            List<Map> spItemInfoResult = dao.queryProductItemInfo(spItemParam);
                            if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                        + addproductInfoResult.get(j).get("ELEMENT_ID") + "】的产品属性信息");
                            }
                            for (int l = 0; l < spItemInfoResult.size(); l++) {
                                Map spItemInfo = spItemInfoResult.get(l);
                                if ("SP_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                    spId = (String) (spItemInfo.get("ATTR_VALUE"));
                                }
                                if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                    partyId = (String) (spItemInfo.get("ATTR_VALUE"));
                                }
                                if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                    spProductId = (String) (spItemInfo.get("ATTR_VALUE"));
                                }
                            }
                            Map sp = new HashMap();
                            sp.put("productId", addproductInfoResult.get(j).get("PRODUCT_ID"));
                            sp.put("packageId", addproductInfoResult.get(j).get("PACKAGE_ID"));
                            sp.put("partyId", partyId);
                            sp.put("spId", spId);
                            sp.put("spProductId", spProductId);
                            sp.put("spServiceId", addproductInfoResult.get(j).get("ELEMENT_ID"));
                            sp.put("monthFirstDay", addProStartDay);
                            sp.put("monthLasttDay", addProEndDay);
                            spList.add(sp);
                        }

                    }

                    Map additparam = new HashMap();
                    additparam.put("PROVINCE_CODE", provinceCode);
                    additparam.put("PID", addProductId);
                    additparam.put("COMMODITY_ID", commodityId);
                    additparam.put("PTYPE", "U");
                    List<Map> addProductItemInfoResult = dao.queryProductItemInfo(additparam);
                    if (addProductItemInfoResult == null || addProductItemInfoResult.size() == 0) {
                        throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品属性信息");
                    }

                    strProductMode = String.valueOf(addproductInfoResult.get(0).get("PRODUCT_MODE"));
                    strBrandCode = getValueFromItem("BRAND_CODE", addProductItemInfoResult);
                    strProductTypeCode = getValueFromItem("PRODUCT_TYPE_CODE", addProductItemInfoResult);

                    productTpye.put("productMode", strProductMode);
                    productTpye.put("productId", addProductId);
                    productTpye.put("productTypeCode", strProductTypeCode);
                    productTpye.put("monthFirstDay", addProStartDay);
                    productTpye.put("monthLasttDay", addProEndDay);
                    product.put("monthFirstDay", addProStartDay);
                    product.put("monthLasttDay", addProEndDay);
                    product.put("brandCode", strBrandCode);
                    product.put("productId", addProductId);
                    product.put("productMode", strProductMode);

                    productTypeList.add(productTpye);
                    productList.add(product);
                    // 附加包
                    if (packageElement != null && packageElement.size() > 0) {
                        for (int n = 0; n < packageElement.size(); n++) {
                            Map peparam = new HashMap();
                            peparam.put("PROVINCE_CODE", provinceCode);
                            peparam.put("COMMODITY_ID", productId);
                            peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                            peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                            peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                            List<Map> packageElementInfo = dao.queryPackageElementInfo(peparam);
                            if (packageElementInfo != null && packageElementInfo.size() > 0) {
                                for (Map packageMap : packageElementInfo) {
                                    if ("D".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                        Map dis = new HashMap();
                                        String elementId = String.valueOf(packageMap.get("ELEMENT_ID"));
                                        dis.put("productId", packageMap.get("PRODUCT_ID"));
                                        dis.put("packageId", packageMap.get("PACKAGE_ID"));
                                        dis.put("discntCode", elementId);
                                        if (!"Y".equals(isFinalCode)) {
                                            Map discntDateMap = TradeManagerUtils.getDiscntEffectTime(elementId,
                                                    addProStartDay, addProEndDay);
                                            dis.put("monthFirstDay", discntDateMap.get("monthFirstDay"));
                                            dis.put("monthLasttDay", discntDateMap.get("monthLasttDay"));
                                        }
                                        else {
                                            dis.put("monthFirstDay", addProStartDay);
                                            dis.put("monthLasttDay", addProEndDay);
                                        }
                                        discntList.add(dis);
                                    }
                                    if ("S".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                        Map svc = new HashMap();
                                        svc.put("serviceId", packageMap.get("ELEMENT_ID"));
                                        svc.put("productId", packageMap.get("PRODUCT_ID"));
                                        svc.put("packageId", packageMap.get("PACKAGE_ID"));
                                        svc.put("monthFirstDay", addProStartDay);
                                        svc.put("monthLasttDay", addProEndDay);
                                        svcList.add(svc);
                                    }
                                    if ("X".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                        String spId = "-1";
                                        String partyId = "-1";
                                        String spProductId = "-1";
                                        Map spItemParam = new HashMap();
                                        spItemParam.put("PTYPE", "X");
                                        spItemParam.put("COMMODITY_ID", productId);
                                        spItemParam.put("PROVINCE_CODE", provinceCode);
                                        spItemParam.put("PID", packageMap.get("ELEMENT_ID"));
                                        List<Map> spItemInfoResult = dao.queryProductItemInfo(spItemParam);
                                        if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                            throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                                    + packageMap.get("ELEMENT_ID") + "】的产品属性信息");
                                        }
                                        for (int j = 0; j < spItemInfoResult.size(); j++) {
                                            Map spItemInfo = spItemInfoResult.get(j);
                                            if ("SP_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                                spId = (String) (spItemInfo.get("ATTR_VALUE"));
                                            }
                                            else if ("PARTY_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                                partyId = (String) (spItemInfo.get("ATTR_VALUE"));
                                            }
                                            else if ("SP_PRODUCT_ID".equals(spItemInfo.get("ATTR_CODE"))) {
                                                spProductId = (String) (spItemInfo.get("ATTR_VALUE"));
                                            }
                                        }
                                        Map sp = new HashMap();
                                        sp.put("productId", packageMap.get("PRODUCT_ID"));
                                        sp.put("packageId", packageMap.get("PACKAGE_ID"));
                                        sp.put("partyId", partyId);
                                        sp.put("spId", spId);
                                        sp.put("spProductId", spProductId);
                                        sp.put("spServiceId", packageMap.get("ELEMENT_ID"));
                                        sp.put("monthFirstDay", addProStartDay);
                                        sp.put("monthLasttDay", addProEndDay);
                                        spList.add(sp);
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
        dealRepeat(discntList);
        dealRepeat(svcList);
        dealRepeat(spList);
        dealRepeat(productTypeList);
        dealRepeat(productList);
        msg.put("discntList", discntList);
        msg.put("svcList", svcList);
        msg.put("spList", spList);
        msg.put("productTypeList", productTypeList);
        msg.put("productList", productList);

    }

    public Map preResFeeInfo(Map msg) {
        String resourceFee = (String) msg.get("resourceFee");
        Map tradeFeeItemList = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        if (StringUtils.isNotEmpty(resourceFee) && !"0".equals(resourceFee)) {
            // 传入单位为厘
            int fee = Integer.parseInt(resourceFee) / 10;
            Map tradeFeeItem = new HashMap();
            tradeFeeItem.put("feeMode", "0");
            tradeFeeItem.put("feeTypeCode", "4310");
            tradeFeeItem.put("oldFee", String.valueOf(fee));
            tradeFeeItem.put("fee", String.valueOf(fee));
            tradeFeeItem.put("chargeSourceCode", "1");
            tradeFeeItem.put("apprStaffId", msg.get("operatorId"));
            tradeFeeItem.put("calculateId", msg.get("tradeId"));
            tradeFeeItem.put("calculateDate", GetDateUtils.getNextMonthFirstDayFormat());
            tradeFeeItem.put("staffId", msg.get("operatorId"));
            tradeFeeItem.put("calculateTag", "0");
            tradeFeeItem.put("payTag", "0");
            tradeFeeItem.put("xDatatype", "NULL");
            itemList.add(tradeFeeItem);
        }
        tradeFeeItemList.put("item", itemList);
        return tradeFeeItemList;
    }

    private Map preTradePurchase(Map msg) {
        Map tradePurchaseMap = (Map) msg.get("tradePurchase");
        Map tradePurchase = new HashMap();
        if (tradePurchaseMap != null && tradePurchaseMap.size() > 0) {
            List<Map> item = new ArrayList<Map>();
            item.add(tradePurchaseMap);
            tradePurchase.put("item", item);
        }
        return tradePurchase;
    }

    /**
     * 根据ATTR_CODE取出相应的ATTR_VALUE
     * 
     * @param attrCode
     * @param infoList
     * @return
     */
    public static String getValueFromItem(String attrCode, List<Map> infoList) {
        String attrValue = "";
        for (int i = 0; i < infoList.size(); i++) {
            Map productItemInfo = infoList.get(i);
            if ("U".equals(productItemInfo.get("PTYPE"))) {
                if (attrValue == "" && attrCode.equals(productItemInfo.get("ATTR_CODE"))) {
                    attrValue = (String) productItemInfo.get("ATTR_VALUE");
                }
            }
        }
        return attrValue;
    }

    private static List<Map> dealRepeat(List<Map> listMap) {
        List<Map> listTemp = new ArrayList<Map>();
        Iterator<Map> it = listMap.iterator();
        while (it.hasNext()) {
            Map a = it.next();
            if (listTemp.contains(a)) {
                it.remove();
            }
            else {
                listTemp.add(a);
            }
        }
        return listMap;

    }

    /**
     * 资源类型
     * 输出到北六编码 描述
     * 01 3G手机终端
     * 02 2G手机终端
     * 03 固网话终端（有串号）
     * 04 宽带终端（有串号）
     * 05 上网终端(卡)
     * 06 上网终端(本)
     * 07 其它
     * 08 固话终端（无串号）
     * 09 宽带终端（无串号）
     * 13 互联网增值终端(无串码)
     * 14 互联网增值终端
     * 开户申请传入终端类别编码：
     * Iphone：IP
     * 乐phone：LP
     * 智能终端：PP
     * 普通定制终端：01
     * 上网卡：04
     * 上网本：05
     * 
     * @param terminalType
     * @return
     */
    private String decodeTerminalType(String terminalType) {
        if ("04".equals(terminalType))
            return "05";
        if ("05".equals(terminalType))
            return "06";
        return "01";
    }

    /**
     * 处理节点的start_date都为14位
     * 
     * @param msg
     * @return
     * @throws Exception
     */
    public Map changeStateDateTo14(Map map) throws Exception {
        List<Map> items = (List<Map>) map.get("item");
        if (items != null) {
            for (Map newMap : items) {
                String date = (String) newMap.get("startDate");
                if (date != null && date.length() > 0) {
                    newMap.put("startDate", GetDateUtils.transDate(date, 14));
                }
            }
        }
        return map;
    }

    public Map createTradeSubItemFor6(Object key, Object value, Object itemId) {
        return MapUtils.asMap("attrTypeCode", "6", "attrCode", key, "attrValue", value, "itemId",
                itemId, "startDate", key, "endDate", key);
    }

    public static Map createTradeSubItemG(String key, String value, String itemId) {
        return MapUtils.asMap("attrTypeCode", "6", "attrCode", key, "attrValue", value, "itemId",
                itemId, "startDate", GetDateUtils.getNextMonthFirstDayFormat(), "endDate", "20501231235959");
    }

}
