package com.ailk.ecaop.biz.sub.olduser;

/**
 * Created by Liu JiaDi on 2016/3/8.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.product.DealNewCbssProduct;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.NewProductUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.google.common.collect.Maps;

/**
 * 续约处理申请
 * 
 * @auther Fan Xuemin
 * @create 2016_03_08_13:56
 *         需要外围系统把终端详细信息传下来即resourcesInfo
 *         产品不变更不下发产品信息，不能退订活动
 *         原有产品生效时间需要获取三户返回的生效时间
 *         不管用户合约是否到期，只要办理的目标套餐和原套装一致，optType就传03。
 *         不管用户合约是否到期，只要办理的目标套餐和原套装不一致，optType就新的传00旧的传01。
 */
@EcRocTag("oldUserRenActivityCbss")
public class OldUserRenActivityCbss extends BaseAopProcessor implements ParamsAppliable {

    DealNewCbssProduct n25Dao = new DealNewCbssProduct();
    LanUtils lan = new LanUtils();
    String ItemId;
    private static final String[] PARAM_ARRAY = {"ecaop.mvoa.preSub.ParametersMapping",
            "ecaop.trade.cbss.checkUserParametersMapping",
            "ecaop.trades.qrychk.ParametersMapping"};
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[3];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        preDataForPreCommit(exchange);
        lan.preData(pmp[0], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
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
            retFee.put("maxRelief", Integer.parseInt(maxRelief));
            retFee.put("origFee", Integer.parseInt(origFee));
            retFeeList.add(retFee);
        }
        return retFeeList;
    }

    private void preDataForPreCommit(Exchange exchange) {
        try {
            Map body = exchange.getIn().getBody(Map.class);
            Map msg = (Map) body.get("msg");
            msg.put("operTypeCode", "0");
            // msg.put("serviceClassCode", "00CP");// FIXME
            // 转换地市编码
            String city = (String) msg.get("city");
            String provinceCode = "00" + msg.get("province");
            msg.put("provinceCode", provinceCode);
            String eparchyCode = ChangeCodeUtils.changeCityCode(msg);
            msg.put("eparchyCode", eparchyCode);
            // 生成cb订单号
            String orderid = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_trade_id");
            // String orderid = "1111111111111111";
            // 针对北分卖场，传入appcode
            msg.put("otoOrdersId", msg.get("ordersId"));// 避免与下发给cb的ordersId冲突
            msg.put("appCode", exchange.getAppCode());

            msg.put("tradeId", orderid);
            msg.put("ordersId", orderid);
            // 生成ItemId号
            ItemId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_item_id");
            // String ItemId = "22222222222222";
            msg.put("itemId", ItemId);
            // 地市编码转换
            // ChangeCodeUtils ChangeCodeUtils = new ChangeCodeUtils();
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
            preProductInfo(exchange, eparchyCode, provinceCode, productInfo, msg, city);
            // 准备base参数
            msg.put("base", preBaseData(msg, exchange.getAppCode()));
            msg.put("ext", preExtDataforItem(msg));
            body.put("msg", msg);
            exchange.getIn().setBody(body);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", e.getMessage());
        }
    }

    // 拼装ext节点

    /**
     * @param msg
     * @return
     */
    private Map preExtDataforItem(Map msg) {
        Map ext = new HashMap();
        ext.put("tradeProductType", preProductTpyeListData(msg));
        ext.put("tradeProduct", preProductData(msg));
        ext.put("tradeDiscnt", preDiscntData(msg));
        ext.put("tradeSvc", preTradeSvcData(msg));
        ext.put("tradeSp", preTradeSpData(msg));
        ext.put("tradeElement", preTradeElementData(msg));
        ext.put("tradePurchase", preTradePurchase(msg));
        ext.put("tradefeeSub", preResFeeInfo(msg));
        ext.put("tradeItem", preTradeItem(msg));
        ext.put("tradeUser", preDataForTradeUser(msg));
        ext.put("tradeSubItem", preDataForTradeSubItem(msg));
        ext.put("tradeOther", preTradeOther(msg));
        // ext.put("tradeSubItem", preTradeSubItem(msg));
        return ext;
    }

    // 准备base参数
    private Map preBaseData(Map msg, String appCode) {
        try {
            Map base = new HashMap();
            String startDate = (String) msg.get("activityStartTime");
            base.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
            base.put("startAcycId", RDate.currentTimeStr("yyyyMM"));
            if (StringUtils.isNotEmpty(startDate)) {
                base.put("startAcycId", startDate.substring(0, 6));
                base.put("startDate", startDate);
            }
            base.put("subscribeId", msg.get("tradeId"));
            base.put("tradeId", msg.get("tradeId"));
            base.put("tradeTypeCode", "0120");
            base.put("nextDealTag", "Z");
            base.put("olcomTag", "0");
            base.put("areaCode", msg.get("eparchyCode"));
            base.put("foregift", "0");
            base.put("execTime", msg.get("activityStartTime"));
            base.put("acceptDate", GetDateUtils.getDate());
            base.put("chkTag", "0");
            base.put("operFee", "0");
            base.put("cancelTag", "0");
            base.put("endAcycId", "203701");
            base.put("acceptMonth", RDate.currentTimeStr("MM"));
            base.put("netTypeCode", "50");
            base.put("advancePay", "0");
            base.put("inModeCode", new ChangeCodeUtils().getInModeCode(appCode));
            base.put("custId", msg.get("custId"));
            base.put("acctId", msg.get("acctId"));
            base.put("serinalNamber", msg.get("serialNumber"));
            base.put("productId", msg.get("mainProduct"));
            base.put("tradeStaffId", msg.get("operatorId"));
            base.put("userDiffCode", "00");
            base.put("brandCode", "4G00");
            base.put("usecustId", msg.get("custId"));
            base.put("checktypeCode", "0");
            base.put("userId", msg.get("userId"));
            base.put("termIp", "132.35.81.217");
            base.put("eparchyCode", msg.get("eparchyCode"));
            base.put("cityCode", msg.get("district"));
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
        msg.put("tradeTypeCode", "9999");
        msg.put("getMode", "1111111111100013110000000100001");
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        try {
            lan.preData(pmp[1], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
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
        int flag = 0;
        List addProduct = new ArrayList();
        List groupProduct = new ArrayList();
        for (int i = 0; i < oldProductInfo.size(); i++) {
            String productInactiveTime = (String) oldProductInfo.get(i).get("productInactiveTime");
            String productMode = (String) oldProductInfo.get(i).get("productMode");
            if (0 < date.compareTo(productInactiveTime) || "50".equals(productMode) || "01".equals(productMode)) {
                tempList.add(oldProductInfo.get(i));
            }
            if (0 > date.compareTo(productInactiveTime) && "50".equals(productMode)) {
                msg.put("activityStartTime", getActivityTime(productInactiveTime));
                msg.put("productInactiveTime", productInactiveTime);
                flag = 1;
            }
            if ("0".equals(msg.get("noChange")) && "00".equals(productMode) && 0 < productInactiveTime.compareTo(date)) {
                msg.put("mainProduct", oldProductInfo.get(i).get("productId"));
            }
            // zzc,主产品变更时，附加产品不能退订（这里只是取出，没处理，分支在下面加）
            if (0 < productInactiveTime.compareTo(date) && "01".equals(productMode)) {
                addProduct.add(oldProductInfo.get(i));
            }
            // cuij 主产品变更时，集团产品不能退订（这里只是取出，没处理，分支在下面加）
            if (0 < productInactiveTime.compareTo(date) && "20".equals(productMode)) {
                groupProduct.add(oldProductInfo.get(i));
            }
        }
        if (flag == 0)
        {
            throw new EcAopServerBizException("9999", "该用户不存在生效合约，请使用老用户优惠购机接口进行操作");
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
            if (EcAopConfigLoader.getStr("ecaop.global.param.oldproduct.special.deal").contains(exchange.getAppCode())) {
                oldProductInfo.removeAll(addProduct);
                oldProductInfo.removeAll(groupProduct);
            }
            // 老产品要求下发三户信息返回的生效时间
            msg.put("oldProductInfo", oldProductInfo);
        }
        body.put("msg", msg);
        exchange.getIn().setBody(body);
    }

    // 拼装TRADE_SUB_ITEM
    private Map preDataForTradeSubItem(Map msg) {
        try {
            LanUtils lan = new LanUtils();
            String userId = (String) msg.get("userId");
            Map tradeSubItem = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            if (msg.get("tradeSubItem") != null)
            {
                // List<Map> itemList = new ArrayList<Map>();
                itemList = (List<Map>) msg.get("tradeSubItem");

            }
            itemList.add(lan.createTradeSubItemF("LINK_NAME", userId));
            itemList.add(lan.createTradeSubItemF("LINK_PHONE", userId));
            itemList.add(lan.createTradeSubItemC(ItemId, "tradeId", msg.get("tradeId"),
                    msg.get("actMonthFirstDay").toString(), msg.get("actMonthLasttDay").toString()));
            String recomPersonId = (String) msg.get("recomPersonId");
            if (StringUtils.isNotEmpty(recomPersonId)) {
                itemList.add(lan.createTradeSubItem1(ItemId, "developerStaffId", recomPersonId,
                        GetDateUtils.getNextMonthFirstDayFormat()));
            }
            String recomDepartId = (String) msg.get("recomDepartId");
            if (StringUtils.isNotEmpty(recomDepartId)) {
                itemList.add(lan.createTradeSubItem1(ItemId, "developDepartId", recomDepartId,
                        GetDateUtils.getNextMonthFirstDayFormat()));
            }
            String recomPersonName = (String) msg.get("recomPersonName");
            if (StringUtils.isNotEmpty(recomPersonName)) {
                itemList.add(lan.createTradeSubItem1(ItemId, "developerStaffName", recomPersonName,
                        GetDateUtils.getNextMonthFirstDayFormat()));
            }
            tradeSubItem.put("item", itemList);
            return tradeSubItem;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TradeSubItem节点报错" + e.getMessage());
        }
    }

    private Map preTradeItem(Map msg) {
        Map tradeItem = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        // List<Map> itemList = (List<Map>) msg.get("tradeItem");
        itemList.add(LanUtils.createTradeItem("USERID_BAK", msg.get("userId").toString()));
        itemList.add(LanUtils.createTradeItem("IPHONE4PRODUCTID",
                (String) msg.get("mainProduct")));
        itemList.add(LanUtils.createTradeItem("B_SYNC_PAYTAG", "Y"));
        itemList.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", "0371"));
        String recomPersonId = (String) msg.get("recomPersonId");
        if (StringUtils.isNotEmpty(recomPersonId)) {
            itemList.add(LanUtils.createTradeItem("DEVELOP_STAFF_ID", recomPersonId));
        }
        String recomDepartId = (String) msg.get("recomDepartId");
        if (StringUtils.isNotEmpty(recomDepartId)) {
            itemList.add(LanUtils.createTradeItem("DEVELOP_DEPART_ID", recomDepartId));
        }
        // 针对北分卖场下发相应字段
        if ("mabc".equals(msg.get("appCode")) && !"".equals(msg.get("netType"))) {
            String netType = changeNetType((String) msg.get("netType"));
            if (!"BJMC".equals(netType)) {
                itemList.add(LanUtils.createTradeItem("E_IN_MODE", netType));
                itemList.add(LanUtils.createTradeItem("OTO_ORDER_ID", msg.get("otoOrdersId")));
            }
            itemList.add(LanUtils.createTradeItem("ORDER_SOURCE", "OTO11"));// 目前只有北京用，所有OTO11
        }
        tradeItem.put("item", itemList);
        return tradeItem;
    }

    private Map preTradeElementData(Map msg) {
        Map elementMap = (Map) msg.get("elementMap");
        Map tradeElement = new HashMap();
        if (elementMap != null && elementMap.size() > 0) {

            Map item = new HashMap();
            item.putAll(elementMap);
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
            String date = (String) msg.get("activityStartTime");
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
                    item.put("endDate", msg.get("productInactiveTime"));
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
            String startDate = (String) msg.get("activityStartTime");
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
                // item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                item.put("startDate", sp.get(i).get("monthFirstDay"));
                item.put("endDate", sp.get(i).get("monthLasttDay"));
                // item.put("endDate", "20501231000000");
                // item.put("startDate", startDate);
                // String productMode = (String) sp.get(i).get("productMode");
                // if (StringUtils.isNotEmpty(startDate) && "50".equals(productMode)) {
                // item.put("endDate", msg.get("activityTime"));
                //
                // }
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
            String startDate = (String) msg.get("activityStartTime");
            for (int i = 0; i < discnt.size(); i++) {
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
                item.put("startDate", discnt.get(i).get("monthFirstDay"));
                item.put("endDate", discnt.get(i).get("monthLasttDay"));
                // item.put("startDate", startDate);
                // item.put("endDate", "20501231000000");
                // String productMode = (String) discnt.get(i).get("productMode");
                // if (StringUtils.isNotEmpty(startDate) && "50".equals(productMode)) {
                // item.put("endDate", msg.get("activityTime"));
                // }
                item.put("modifyTag", "0");
                item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
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
            String startDate = (String) msg.get("activityStartTime");
            for (int i = 0; i < svc.size(); i++) {
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("userId", msg.get("userId"));
                item.put("serviceId", svc.get(i).get("serviceId"));
                item.put("modifyTag", "0");
                item.put("startDate", svc.get(i).get("monthFirstDay"));
                item.put("endDate", svc.get(i).get("monthLasttDay"));
                // item.put("endDate", "20501231000000");
                // item.put("startDate", startDate);
                // String productMode = (String) svc.get(i).get("productMode");
                // if (StringUtils.isNotEmpty(startDate) && "50".equals(productMode)) {
                // item.put("endDate", msg.get("activityTime"));
                // }
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
        String startDate = (String) msg.get("activityStartTime");
        Map tradeProduct = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> product = (List<Map>) msg.get("productList");
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
                item.put("endDate", msg.get("productInactiveTime"));
                item.put("userIdA", MagicNumber.DEFAULT_NO_VALUE);
                itemList.add(item);
            }
        }
        for (int i = 0; i < product.size(); i++) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", msg.get("userId"));
            item.put("productMode", product.get(i).get("productMode"));
            item.put("productId", product.get(i).get("productId"));
            item.put("brandCode", product.get(i).get("brandCode"));
            item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
            item.put("modifyTag", "0");
            item.put("endDate", product.get(i).get("monthLasttDay"));
            item.put("startDate", product.get(i).get("monthFirstDay"));
            // item.put("endDate", "20501231000000");
            // item.put("startDate", startDate);
            // if (StringUtils.isNotEmpty(startDate) && "50".equals(product.get(i).get("productMode"))) {
            // item.put("endDate", msg.get("activityTime"));
            // }
            item.put("userIdA", MagicNumber.DEFAULT_NO_VALUE);
            itemList.add(item);

        }
        tradeProduct.put("item", itemList);
        return tradeProduct;
    }

    public Map preProductTpyeListData(Map msg) {
        Map tradeProductType = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        String startDate = (String) msg.get("activityStartTime");
        List<Map> productTpye = (List<Map>) msg.get("productTypeList");
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
                String productInactiveTime = (String) oldProductInfo.get(i).get("productInactiveTime");
                item.put("endDate", msg.get("productInactiveTime"));
                itemList.add(item);
            }
        }
        for (int i = 0; i < productTpye.size(); i++) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", msg.get("userId"));
            item.put("productMode", productTpye.get(i).get("productMode"));
            item.put("productId", productTpye.get(i).get("productId"));
            item.put("productTypeCode", productTpye.get(i).get("productTypeCode"));
            item.put("modifyTag", "0");
            item.put("endDate", productTpye.get(i).get("monthLasttDay"));
            item.put("startDate", productTpye.get(i).get("monthFirstDay"));
            // item.put("endDate", "20501231000000");
            // item.put("startDate", startDate);
            // if (StringUtils.isNotEmpty(startDate) && "50".equals(productTpye.get(i).get("productMode"))) {
            // item.put("endDate", msg.get("activityTime"));
            // }
            itemList.add(item);
        }

        tradeProductType.put("item", itemList);
        return tradeProductType;

    }

    /**
     * 处理产品、活动、终端、费用项信息
     */
    public void preProductInfo(Exchange exchange, String eparchyCode, String provinceCode, List<Map> productInfo,
            Map msg, String city) {
        List<Map> productTypeList = new ArrayList<Map>();
        List<Map> productList = new ArrayList<Map>();
        List<Map> resourcesInfo = (List<Map>) msg.get("resourcesInfo");
        List<Map> discntList = new ArrayList<Map>();
        List<Map> svcList = new ArrayList<Map>();
        List<Map> spList = new ArrayList<Map>();
        String monthLasttDay = null;
        String resourcesCode = null;
        String resourcesType = null;
        String itemId = (String) msg.get("itemId");
        String userId = (String) msg.get("userId");
        String startDate = (String) msg.get("activityStartTime");
        Map ext = new HashMap();
        // String acceptDate = GetDateUtils.getDate();
        List<Map> activityInfo = (List<Map>) msg.get("activityInfo");
        if (activityInfo != null && activityInfo.size() > 0) {
            for (int i = 0; i < activityInfo.size(); i++) {
                List<Map> packageElement = (List<Map>) activityInfo.get(i).get("packageElement");
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
                if (activityMap.containsKey("resourcesType")) {
                    resourcesType = (String) (activityMap.get("resourcesType"));
                }
                String resourceFee = (String) (activityMap.get("resourcesFee"));
                if (StringUtils.isNotEmpty(resourceFee)) {
                    msg.put("resourceFee", resourceFee);
                }
                // String monthFirstDay = GetDateUtils.getDate();
                // monthLasttDay = "20501231235959";

                Map actparam = new HashMap();
                actparam.put("PROVINCE_CODE", provinceCode);
                actparam.put("PRODUCT_MODE", "50");
                actparam.put("PRODUCT_ID", actPlanId);
                actparam.put("EPARCHY_CODE", eparchyCode);
                actparam.put("FIRST_MON_BILL_MODE", null);
                List<Map> actInfoResult = n25Dao.qryDefaultPackageElement(actparam);
                if (actInfoResult == null || actInfoResult.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + actPlanId + "】的产品信息");
                }
                String actProductId = String.valueOf(actInfoResult.get(0).get("PRODUCT_ID"));
                Map productDate = getproductDate(actProductId, msg);
                String monthFirstDay = (String) productDate.get("monthFirstDay");
                monthLasttDay = (String) productDate.get("monthLasttDay");
                msg.put("actMonthFirstDay", monthFirstDay);
                msg.put("actMonthLasttDay", monthLasttDay);
                // 附加包处理
                boolean isElementInfoEmpty = false;// 是否能查到外围传的元素的标记by:cuij
                if (packageElement != null && packageElement.size() > 0) {
                    for (int n = 0; n < packageElement.size(); n++) {
                        Map peparam = new HashMap();
                        peparam.put("PROVINCE_CODE", provinceCode);
                        peparam.put("PRODUCT_ID", actPlanId);
                        peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                        peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                        peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                        List<Map> packageElementInfo = n25Dao.queryPackageElementInfo(peparam);
                        if (packageElementInfo != null && packageElementInfo.size() > 0) {
                            isElementInfoEmpty = true;// 外围传的元素与默认元素不相同，正常处理附加包元素
                            for (Map packageMap : packageElementInfo) {

                                if ("D".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    Map dis = new HashMap();
                                    dis.put("productId", packageMap.get("PRODUCT_ID"));
                                    dis.put("productMode", packageMap.get("PRODUCT_MODE"));
                                    dis.put("packageId", packageMap.get("PACKAGE_ID"));
                                    dis.put("discntCode", packageMap.get("ELEMENT_ID"));
                                    dis.put("monthFirstDay", monthFirstDay);
                                    dis.put("monthLasttDay", monthLasttDay);
                                    discntList.add(dis);
                                }
                                if ("S".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    Map svc = new HashMap();
                                    svc.put("serviceId", packageMap.get("ELEMENT_ID"));
                                    svc.put("productId", packageMap.get("PRODUCT_ID"));
                                    svc.put("packageId", packageMap.get("PACKAGE_ID"));
                                    svc.put("monthFirstDay", monthFirstDay);
                                    svc.put("monthLasttDay", monthLasttDay);
                                    svcList.add(svc);
                                }
                            }
                        }
                    }
                }
                if (actInfoResult != null)
                {
                    for (int j = 0; j < actInfoResult.size(); j++) {
                        // 针对北分卖场，流量，短信，通话三选一操作by cuij 20161214
                        // 如果是北分卖场&&传的是非默认的元素，过滤掉该包下其素（所传元素已在附加包处理）
                        if (packageElement != null && packageElement.size() > 0 && isElementInfoEmpty) {
                            Object appCode = exchange.getAppCode();
                            String PackageIdFromJSON = (String) packageElement.get(0).get("packageId");
                            String PackageIdFromDB = actInfoResult.get(j).get("PACKAGE_ID") + "";
                            boolean isTure = "mabc".equals(appCode);
                            boolean isSamePackage = PackageIdFromJSON.equals(PackageIdFromDB);
                            if (isTure && isSamePackage) {
                                continue;
                            }
                        }
                        if ("D".equals(actInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map dis = new HashMap();
                            dis.put("productId", actInfoResult.get(j).get("PRODUCT_ID"));
                            dis.put("packageId", actInfoResult.get(j).get("PACKAGE_ID"));
                            dis.put("discntCode", actInfoResult.get(j).get("ELEMENT_ID"));
                            dis.put("monthFirstDay", monthFirstDay);
                            dis.put("monthLasttDay", monthLasttDay);
                            dis.put("productMode", "50");
                            discntList.add(dis);
                        }
                        if ("S".equals(actInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map svc = new HashMap();
                            svc.put("serviceId", actInfoResult.get(j).get("ELEMENT_ID"));
                            svc.put("productId", actInfoResult.get(j).get("PRODUCT_ID"));
                            svc.put("packageId", actInfoResult.get(j).get("PACKAGE_ID"));
                            svc.put("monthFirstDay", monthFirstDay);
                            svc.put("monthLasttDay", monthLasttDay);
                            svc.put("productMode", "50");
                            svcList.add(svc);
                        }
                        if ("X".equals(actInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            String spId = "-1";
                            String partyId = "-1";
                            String spProductId = "-1";
                            Map spItemParam = new HashMap();
                            spItemParam.put("PTYPE", "X");
                            spItemParam.put("PRODUCT_ID", actPlanId);
                            spItemParam.put("PROVINCE_CODE", provinceCode);
                            spItemParam.put("SPSERVICEID", actInfoResult.get(j).get("ELEMENT_ID"));
                            List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                            if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                        + actInfoResult.get(j).get("ELEMENT_ID") + "】的产品属性信息");
                            }
                            for (int l = 0; l < spItemInfoResult.size(); l++) {
                                Map spItemInfo = spItemInfoResult.get(l);
                                spId = (String) spItemInfo.get("SP_ID");
                                partyId = (String) spItemInfo.get("PARTY_ID");
                                spProductId = (String) spItemInfo.get("SP_PRODUCT_ID");
                            }
                            Map sp = new HashMap();
                            sp.put("productId", actInfoResult.get(j).get("PRODUCT_ID"));
                            sp.put("packageId", actInfoResult.get(j).get("PACKAGE_ID"));
                            sp.put("partyId", partyId);
                            sp.put("spId", spId);
                            sp.put("spProductId", spProductId);
                            sp.put("spServiceId", actInfoResult.get(j).get("ELEMENT_ID"));
                            sp.put("productMode", "50");
                            sp.put("monthFirstDay", monthFirstDay);
                            sp.put("monthLasttDay", monthLasttDay);
                            spList.add(sp);
                        }

                    }
                }
                // appendMap.put("PART_ACTIVE_PRODUCT", actProductId);

                Map actProParam = new HashMap();
                actProParam.put("PRODUCT_ID", actProductId);
                List<Map> actProductInfo = n25Dao.queryProductAndPtypeProduct(actProParam);
                if (actProductInfo == null || actProductInfo.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品表或者产品属性表中未查询到产品ID【" + actProductId + "】的产品信息");
                }
                Map detailProduct = actProductInfo.get(0);
                String resActivityper = String.valueOf(detailProduct.get("END_OFFSET"));
                String subProductMode = (String) (detailProduct.get("PRODUCT_MODE"));
                String subProductTypeCode = (String) (detailProduct.get("PRODUCT_TYPE_CODE"));
                // 要入订单表，然后正式提交的时候需要
                msg.put("ACTIVITY_TYPE", subProductTypeCode);
                String enableTag = (String) (detailProduct.get("ENABLE_TAG"));// 0-绝对时间（看开始时间） 1-相对时间
                String strStartUnit = (String) (detailProduct.get("START_UNIT"));// '0:天 1:自然天 2:月 3:自然月 4:年 5:自然年';
                String endUnit = (String) (null == detailProduct.get("END_UNIT") ? "0" : detailProduct.get("END_UNIT"));
                String startOffset = String.valueOf(detailProduct.get("START_OFFSET"));// 生效偏移时间
                String strBrandcode = (String) (detailProduct.get("BRAND_CODE"));

                if (StringUtils.isEmpty(enableTag) || StringUtils.isEmpty(startOffset)
                        || StringUtils.isEmpty(strStartUnit)) {
                    enableTag = "0";
                }

                // String newAcceptDate = null;
                // try {
                // newAcceptDate = GetDateUtils.transDate(acceptDate, 19);
                // }
                // catch (Exception e) {
                // e.printStackTrace();
                // }

                // monthFirstDay = GetDateUtils.getSpecifyDateTime(Integer.parseInt(enableTag),
                // Integer.parseInt(strStartUnit), startDate, Integer.parseInt(startOffset));
                // monthFirstDay = GetDateUtils.TransDate(monthFirstDay, "yyyy-MM-dd HH:mm:ss");
                // monthLasttDay = GetDateUtils.getSpecifyDateTime(Integer.parseInt(enableTag),
                // Integer.parseInt(endUnit),
                // startDate, Integer.parseInt(resActivityper) + Integer.parseInt(startOffset));
                // monthLasttDay = GetDateUtils.getSpecifyDateTime(1, 6, monthLasttDay, -1); // 结束月最后一天
                // monthLasttDay = GetDateUtils.TransDate(monthLasttDay, "yyyy-MM-dd HH:mm:ss");
                Map productTpye = new HashMap();
                Map product = new HashMap();
                if (!"0".equals(actPlanId)) {

                    productTpye.put("productMode", subProductMode);
                    productTpye.put("productId", actProductId);
                    productTpye.put("productTypeCode", subProductTypeCode);
                    productTpye.put("monthFirstDay", monthFirstDay);
                    productTpye.put("monthLasttDay", monthLasttDay);
                    product.put("brandCode", strBrandcode);
                    product.put("productId", actProductId);
                    product.put("productMode", subProductMode);
                    product.put("monthFirstDay", monthFirstDay);
                    product.put("monthLasttDay", monthLasttDay);
                    productTypeList.add(productTpye);
                    productList.add(product);

                }

                for (int j = 0; j < actInfoResult.size(); j++) {
                    Map subProductInfo = actInfoResult.get(j);
                    if ("A".equals(subProductInfo.get("ELEMENT_TYPE_CODE"))) {
                        String packageId = String.valueOf(subProductInfo.get("PACKAGE_ID"));
                        String elementDiscntId = String.valueOf(subProductInfo.get("ELEMENT_ID"));
                        // 活动方案ID
                        if (!"0".equals(actPlanId)) {
                            if ((resourcesInfo == null || resourcesInfo.size() == 0) && resourcesCode != null)
                            {
                                Map body = exchange.getIn().getBody(Map.class);
                                ArrayList<Map> resourceInfo = new ArrayList<Map>();
                                Map res = new HashMap();
                                res.put("resourcesType", resourcesType.toString());
                                res.put("resourcesCode", resourcesCode.toString());
                                res.put("operType", "0");
                                res.put("useType", "0");
                                res.put("occupiedFlag", "0");
                                resourceInfo.add(res);
                                msg.put("accessType", "01");
                                msg.put("city", city);
                                msg.put("resourcesInfo", resourceInfo);
                                body.put("msg", msg);
                                LanUtils lan = new LanUtils();
                                System.out.println("开始查询终端信息！！！！！！！！！！！");
                                try {
                                    lan.preData(pmp[2], exchange);
                                    CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.n6res");
                                    lan.xml2Json4Res("ecaop.trades.qrychk.template", exchange);
                                }
                                catch (Exception e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                }
                                Map retMap = exchange.getOut().getBody(Map.class);
                                // ChangeCodeUtils ChangeCodeUtils = new ChangeCodeUtils();
                                msg.put("city", ChangeCodeUtils.changeEparchy(msg));
                                if (200 != (Integer) exchange.getProperty(Exchange.HTTP_STATUSCODE)) {
                                    throw new EcAopServerBizException(retMap.get("code").toString(), retMap.get(
                                            "detail").toString());
                                }
                                ArrayList<Map> resp = (ArrayList<Map>) retMap.get("resourcesRsp");

                                if (resp != null && resp.size() > 0) {
                                    for (Map resMap : resp) {
                                        String rscStateCode = resMap.get("rscStateCode").toString();
                                        if (!"0000".equals(rscStateCode)) {
                                            throw new EcAopServerBizException(rscStateCode, resMap.get("rscStateDesc")
                                                    .toString());
                                        }
                                    }
                                    resourcesInfo = resp;
                                }
                                else
                                {
                                    throw new EcAopServerBizException("9999", "未查询到终端【" + resourcesCode + "】的信息");
                                }
                            }
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
                                    elemntItem1.put("startDate", monthFirstDay);
                                    // if (StringUtils.isNotEmpty(startDate)) {
                                    // elemntItem1.put("startDate", startDate);
                                    // }
                                    elemntItem1.put("endDate", monthLasttDay);
                                    elemntItem1.put("modifyTag", "0");
                                    elemntItem1
                                            .put("itemId", TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID"));
                                    elemntItem1.put("userIdA", "-1");
                                    elemntItem1.put("xDatatype", "NULL");
                                    msg.put("elementMap", elemntItem1);
                                }
                                List<Map> tradeSubItem = new ArrayList<Map>();
                                tradeSubItem.add(LanUtils.createTradeSubItemH("deviceType",
                                        decodeTerminalType(terminalType), itemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("deviceno", machineTypeCode, itemId));
                                tradeSubItem.add(LanUtils
                                        .createTradeSubItemH("devicebrand", orgdeviceBrandCode, itemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("deviceintag", resBrandName, itemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("mobilecost",
                                        String.valueOf(Integer.parseInt(cost) / 100), itemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("mobileinfo", machineTypeName, itemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("mobilesaleprice",
                                        String.valueOf(Integer.parseInt(salePrice) / 100), itemId));
                                tradeSubItem
                                        .add(LanUtils.createTradeSubItemH("resActivityper", resActivityper, itemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("partActiveProduct",
                                        (String) msg.get("mainProduct"), itemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("resourcesBrandCode", resBrandCode,
                                        itemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("resourcesBrandName", resBrandName,
                                        itemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("resourcesModelCode", resModelCode,
                                        itemId));
                                tradeSubItem.add(LanUtils
                                        .createTradeSubItemH("resourcesModelName", resModeName, itemId));

                                if (StringUtils.isNotEmpty(terminalSubtype)) // 有时为空
                                {
                                    tradeSubItem.add(LanUtils.createTradeSubItemH("terminalTSubType", terminalSubtype,
                                            itemId));
                                }
                                tradeSubItem.add(LanUtils.createTradeSubItemH("terminalType", terminalType, itemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("isOwnerPhone", "0", itemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("isPartActive", "0", itemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("holdUnitType", "01", itemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("resourcesType", "07", itemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("packageType", "10", itemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("itemid", itemId, itemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("SALE_PRODUCT_LIST", subProductTypeCode,
                                        itemId));

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
                                tradePurchase.put("startDate", monthFirstDay);
                                if (StringUtils.isNotEmpty(startDate)) {
                                    tradePurchase.put("startDate", startDate);
                                }
                                tradePurchase.put("endDate", monthLasttDay);
                                tradePurchase.put("remark", "");
                                tradePurchase.put("itemId", itemId);
                                tradePurchase.put("xDatatype", "NULL");
                                msg.put("tradePurchase", tradePurchase);
                            }
                        } // end
                    } // END IF A
                      // else
                      // {
                      // List<Map> tradeSubItem = new ArrayList<Map>();
                      // // tradeSubItem.add(LanUtils.createTradeSubItemG("USERID_BAK", userId, itemId));
                      // // tradeSubItem.add(LanUtils.createTradeSubItemG("IPHONE4PRODUCTID",
                      // // (String) msg.get("mainProduct"), itemId));
                      // // tradeSubItem.add(LanUtils.createTradeSubItemG("B_SYNC_PAYTAG", "Y", itemId));
                      // // List<Map> tradeItem = new ArrayList<Map>();
                      // // tradeItem.add(LanUtils.createTradeItem("USERID_BAK", userId));
                      // // tradeItem.add(LanUtils.createTradeItem("IPHONE4PRODUCTID",
                      // // (String) msg.get("mainProduct")));
                      // // tradeItem.add(LanUtils.createTradeItem("B_SYNC_PAYTAG", "Y"));
                      // // msg.put("tradeItem", tradeItem);
                      // msg.put("tradeSubItem", tradeSubItem);
                      // }
                }

            }
        }
        // 处理产品信息
        if (productInfo != null && productInfo.size() > 0) {
            for (int i = 0; i < productInfo.size(); i++) {
                List<Map> packageElement = (List<Map>) productInfo.get(i).get("packageElement");
                String productMode = String.valueOf(productInfo.get(i).get("productMode"));
                String firstMonBillMode = "02";
                String productId = String.valueOf(productInfo.get(i).get("productId"));
                String isFinalCode = TradeManagerUtils.getEndDate(productId);
                Map productDate = getproductDate(productId, msg);
                String monthFirstDay = (String) productDate.get("monthFirstDay");
                if ("N".equals(isFinalCode)) {
                    monthLasttDay = (String) productDate.get("monthLasttDay");
                }

                // 附加包
                if (packageElement != null && packageElement.size() > 0) {
                    for (int n = 0; n < packageElement.size(); n++) {
                        Map peparam = new HashMap();
                        peparam.put("PROVINCE_CODE", provinceCode);
                        peparam.put("PRODUCT_ID", productId);
                        peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                        peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                        peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                        List<Map> packageElementInfo = n25Dao.queryPackageElementInfo(peparam);
                        System.out.println("走进来了么？？？？？？？" + packageElementInfo);
                        if (packageElementInfo != null && packageElementInfo.size() > 0) {
                            for (Map packageMap : packageElementInfo) {
                                if ("D".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    Map dis = new HashMap();
                                    dis.put("productId", packageMap.get("PRODUCT_ID"));
                                    dis.put("packageId", packageMap.get("PACKAGE_ID"));
                                    dis.put("discntCode", packageMap.get("ELEMENT_ID"));
                                    dis.put("monthFirstDay", monthFirstDay);
                                    dis.put("monthLasttDay", monthLasttDay);
                                    discntList.add(dis);
                                }
                                if ("S".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    Map svc = new HashMap();
                                    svc.put("serviceId", packageMap.get("ELEMENT_ID"));
                                    svc.put("productId", packageMap.get("PRODUCT_ID"));
                                    svc.put("packageId", packageMap.get("PACKAGE_ID"));
                                    svc.put("monthFirstDay", monthFirstDay);
                                    svc.put("monthLasttDay", monthLasttDay);
                                    svcList.add(svc);
                                }
                                if ("X".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    String spId = "-1";
                                    String partyId = "-1";
                                    String spProductId = "-1";
                                    Map spItemParam = new HashMap();
                                    spItemParam.put("PTYPE", "X");
                                    spItemParam.put("PRODUCT_ID", productId);
                                    spItemParam.put("PROVINCE_CODE", provinceCode);
                                    spItemParam.put("SPSERVICEID", packageMap.get("ELEMENT_ID"));
                                    List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                                    if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                        throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                                + packageMap.get("ELEMENT_ID") + "】的产品属性信息");
                                    }
                                    for (int j = 0; j < spItemInfoResult.size(); j++) {
                                        Map spItemInfo = spItemInfoResult.get(j);
                                        spId = (String) spItemInfo.get("SP_ID");
                                        partyId = (String) spItemInfo.get("PARTY_ID");
                                        spProductId = (String) spItemInfo.get("SP_PRODUCT_ID");
                                    }
                                    Map sp = new HashMap();
                                    sp.put("productId", packageMap.get("PRODUCT_ID"));
                                    sp.put("packageId", packageMap.get("PACKAGE_ID"));
                                    sp.put("partyId", partyId);
                                    sp.put("spId", spId);
                                    sp.put("spProductId", spProductId);
                                    sp.put("spServiceId", packageMap.get("ELEMENT_ID"));
                                    sp.put("monthFirstDay", monthFirstDay);
                                    sp.put("monthLasttDay", monthLasttDay);
                                    spList.add(sp);
                                }
                            }
                        }
                    }
                }
                if ("1".equals(productMode)) {
                    System.out.println("===========主产品产品处理");

                    monthLasttDay = "20501231000000";
                    Map productTpye = new HashMap();
                    Map product = new HashMap();

                    String commodityId = productId;
                    String strBrandCode = "";
                    String strProductTypeCode = "";
                    String strProductMode = "";

                    Map proparam = new HashMap();
                    proparam.put("PROVINCE_CODE", provinceCode);
                    proparam.put("PRODUCT_ID", commodityId);
                    proparam.put("EPARCHY_CODE", eparchyCode);
                    proparam.put("PRODUCT_MODE", "00");
                    proparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                    List<Map> productInfoResult = n25Dao.qryDefaultPackageElement(proparam);
                    if (productInfoResult == null || productInfoResult.size() == 0) {
                        throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品信息");
                    }
                    // 选速率,默认42M
                    // productInfoResult = TradeManagerUtils.chooseSpeed(productInfoResult, msg.get("phoneSpeedLevel")
                    // + "");
                    // 新的速率处理(提示：此处phoneSpeedLevle是在line:160) TODO 速率处理
                    productInfoResult = new NewProductUtils().chooseSpeed(productInfoResult,
                            msg.get("phoneSpeedLevel") + "");
                    productId = String.valueOf(productInfoResult.get(0).get("PRODUCT_ID"));
                    if (productId.equals("-1")) {
                        productId = String.valueOf(productInfoResult.get(1).get("PRODUCT_ID"));
                    }
                    Map itparam = new HashMap();
                    itparam.put("PROVINCE_CODE", provinceCode);
                    itparam.put("PRODUCT_ID", commodityId);
                    List<Map> productItemInfoResult = n25Dao.queryProductAndPtypeProduct(itparam);
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
                            dis.put("monthFirstDay", monthFirstDay);
                            dis.put("monthLasttDay", monthLasttDay);
                            discntList.add(dis);

                        }
                        if ("S".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map svc = new HashMap();
                            svc.put("serviceId", productInfoResult.get(j).get("ELEMENT_ID"));
                            svc.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                            svc.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                            svc.put("monthFirstDay", monthFirstDay);
                            svc.put("monthLasttDay", monthLasttDay);
                            svcList.add(svc);
                        }
                        if ("X".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            String spId = "-1";
                            String partyId = "-1";
                            String spProductId = "-1";
                            Map spItemParam = new HashMap();
                            spItemParam.put("PROVINCE_CODE", provinceCode);
                            spItemParam.put("SPSERVICEID", productInfoResult.get(j).get("ELEMENT_ID"));
                            List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                            if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                        + productInfoResult.get(j).get("ELEMENT_ID") + "】的产品属性信息");
                            }

                            for (int l = 0; l < spItemInfoResult.size(); l++) {
                                Map spItemInfo = spItemInfoResult.get(l);
                                spId = (String) spItemInfo.get("SP_ID");
                                partyId = (String) spItemInfo.get("PARTY_ID");
                                spProductId = (String) spItemInfo.get("SP_PRODUCT_ID");
                            }

                            Map sp = new HashMap();
                            sp.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                            sp.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                            sp.put("partyId", partyId);
                            sp.put("spId", spId);
                            sp.put("spProductId", spProductId);
                            sp.put("spServiceId", productInfoResult.get(j).get("ELEMENT_ID"));
                            sp.put("monthFirstDay", monthFirstDay);
                            sp.put("monthLasttDay", monthLasttDay);

                            spList.add(sp);
                        }

                    }

                    strProductMode = String.valueOf(productInfoResult.get(0).get("PRODUCT_MODE"));
                    strBrandCode = (String) productInfoResult.get(0).get("BRAND_CODE");
                    strProductTypeCode = (String) productInfoResult.get(0).get("PRODUCT_TYPE_CODE");

                    productTpye.put("productMode", strProductMode);
                    productTpye.put("productId", productId);
                    productTpye.put("productTypeCode", strProductTypeCode);
                    productTpye.put("monthFirstDay", monthFirstDay);
                    productTpye.put("monthLasttDay", monthLasttDay);
                    product.put("brandCode", strBrandCode);
                    product.put("productId", productId);
                    product.put("productMode", strProductMode);
                    product.put("monthFirstDay", monthFirstDay);
                    product.put("monthLasttDay", monthLasttDay);

                    productTypeList.add(productTpye);
                    productList.add(product);

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
                    addproparam.put("PRODUCT_ID", commodityId);
                    addproparam.put("EPARCHY_CODE", eparchyCode);
                    addproparam.put("PRODUCT_MODE", "01");
                    addproparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                    List<Map> addproductInfoResult = n25Dao.qryDefaultPackageElement(addproparam);

                    if (addproductInfoResult == null || addproductInfoResult.size() == 0) {
                        // 未查询到附加产品的默认资费或服务,不报错,去TD_B_PRODUCT表查询,产品不存在就抛错,存在继续执行 by wangmc 20170302
                        List<Map> addproductInfoList = n25Dao.qryProductInfoByProductTable(addproparam);
                        if (!IsEmptyUtils.isEmpty(addproductInfoList)) {
                            addProductId = String.valueOf(addproductInfoList.get(0).get("PRODUCT_ID"));
                            strProductMode = String.valueOf(addproductInfoList.get(0).get("PRODUCT_MODE"));
                        }
                        else {
                            throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + commodityId + "】的产品信息");
                        }
                    }
                    else {
                        addProductId = String.valueOf(addproductInfoResult.get(0).get("PRODUCT_ID"));
                        strProductMode = String.valueOf(addproductInfoResult.get(0).get("PRODUCT_MODE"));
                    }
                    List<Map> tempList = new ArrayList<Map>();
                    // 选速率,默认42M
                    // addproductInfoResult = TradeManagerUtils.chooseSpeed(addproductInfoResult,
                    // msg.get("phoneSpeedLevel") + "");
                    // 新的速率处理(提示：此处phoneSpeedLevle是在line:160) TODO 速率处理
                    addproductInfoResult = new NewProductUtils().chooseSpeed(addproductInfoResult,
                            msg.get("phoneSpeedLevel") + "");

                    for (int j = 0; j < addproductInfoResult.size(); j++) {

                        if ("D".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map dis = new HashMap();
                            dis.put("productId", addproductInfoResult.get(j).get("PRODUCT_ID"));
                            dis.put("packageId", addproductInfoResult.get(j).get("PACKAGE_ID"));
                            dis.put("discntCode", addproductInfoResult.get(j).get("ELEMENT_ID"));
                            dis.put("monthFirstDay", monthFirstDay);
                            dis.put("monthLasttDay", monthLasttDay);
                            discntList.add(dis);
                        }
                        if ("S".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map svc = new HashMap();
                            svc.put("serviceId", addproductInfoResult.get(j).get("ELEMENT_ID"));
                            svc.put("productId", addproductInfoResult.get(j).get("PRODUCT_ID"));
                            svc.put("packageId", addproductInfoResult.get(j).get("PACKAGE_ID"));
                            svc.put("monthFirstDay", monthFirstDay);
                            svc.put("monthLasttDay", monthLasttDay);
                            svcList.add(svc);
                        }
                        if ("X".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            String spId = "-1";
                            String partyId = "-1";
                            String spProductId = "-1";
                            Map spItemParam = new HashMap();
                            spItemParam.put("PROVINCE_CODE", provinceCode);
                            spItemParam.put("SPSERVICEID", addproductInfoResult.get(j).get("ELEMENT_ID"));
                            List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                            if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                        + addproductInfoResult.get(j).get("ELEMENT_ID") + "】的产品属性信息");
                            }
                            for (int l = 0; l < spItemInfoResult.size(); l++) {
                                Map spItemInfo = spItemInfoResult.get(l);
                                spId = (String) spItemInfo.get("SP_ID");
                                partyId = (String) spItemInfo.get("PARTY_ID");
                                spProductId = (String) spItemInfo.get("SP_PRODUCT_ID");
                            }
                            Map sp = new HashMap();
                            sp.put("productId", addproductInfoResult.get(j).get("PRODUCT_ID"));
                            sp.put("packageId", addproductInfoResult.get(j).get("PACKAGE_ID"));
                            sp.put("partyId", partyId);
                            sp.put("spId", spId);
                            sp.put("spProductId", spProductId);
                            sp.put("spServiceId", addproductInfoResult.get(j).get("ELEMENT_ID"));
                            sp.put("monthFirstDay", monthFirstDay);
                            sp.put("monthLasttDay", monthLasttDay);
                            spList.add(sp);
                        }

                    }

                    Map additparam = new HashMap();
                    additparam.put("PROVINCE_CODE", provinceCode);
                    additparam.put("PRODUCT_ID", commodityId);
                    List<Map> addProductItemInfoResult = n25Dao.queryProductAndPtypeProduct(additparam);
                    if (addProductItemInfoResult == null || addProductItemInfoResult.size() == 0) {
                        throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品属性信息");
                    }

                    strBrandCode = (String) addProductItemInfoResult.get(0).get("BRAND_CODE");
                    strProductTypeCode = (String) addProductItemInfoResult.get(0).get("PRODUCT_TYPE_CODE");

                    productTpye.put("productMode", strProductMode);
                    productTpye.put("productId", addProductId);
                    productTpye.put("productTypeCode", strProductTypeCode);
                    productTpye.put("monthFirstDay", monthFirstDay);
                    productTpye.put("monthLasttDay", monthLasttDay);
                    product.put("brandCode", strBrandCode);
                    product.put("productId", addProductId);
                    product.put("productMode", strProductMode);
                    product.put("monthFirstDay", monthFirstDay);
                    product.put("monthLasttDay", monthLasttDay);

                    productTypeList.add(productTpye);
                    productList.add(product);
                }
            }
        }
        System.out.println("svcList=======================" + svcList);
        dealRepeat(discntList);
        dealRepeat(svcList);
        dealRepeat(spList);
        dealRepeat(productTypeList);
        dealRepeat(productList);
        // 增加活动结束时间
        msg.put("activityTime", monthLasttDay);
        msg.put("discntList", discntList);
        msg.put("svcList", svcList);
        msg.put("spList", spList);
        msg.put("productTypeList", productTypeList);
        msg.put("productList", productList);

    }

    public Map preResFeeInfo(Map msg) {
        String date = (String) msg.get("activityStartTime");
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
            if (StringUtils.isNotEmpty(date)) {
                tradeFeeItem.put("calculateDate", date);
            }
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
            Map item = new HashMap();
            item.putAll(tradePurchaseMap);
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

    private String getActivityTime(String activityTime) {
        String date = null;
        int year = Integer.parseInt(activityTime.substring(0, 4));
        int mouth = Integer.parseInt(activityTime.substring(4, 6));
        if (mouth > 11) {
            year += 1;
            mouth = 1;
        }
        else {
            mouth = mouth + 1;
        }
        if (mouth < 10) {
            date = year + "0" + mouth + "01000000";
        }
        else {
            date = year + "" + mouth + "01000000";
        }
        return date;
    }

    /**
     * 获取产品或者活动的有效期
     */
    public Map getproductDate(String actProductId, Map msg) {
        String acceptDate = (String) msg.get("productInactiveTime");
        System.out.println(acceptDate);
        String monthLasttDay;
        String monthFirstDay;
        Map actProParam = new HashMap();
        // actProParam.put("PRODUCT_ID", actProductId);
        actProParam.put("PROVINCE_CODE", msg.get("provinceCode"));
        actProParam.put("PRODUCT_MODE", "50");
        actProParam.put("PRODUCT_ID", actProductId);
        actProParam.put("EPARCHY_CODE", msg.get("eparchyCode"));
        actProParam.put("FIRST_MON_BILL_MODE", null);
        List<Map> actProductInfo = n25Dao.queryProductAndPtypeProduct(actProParam);
        if (actProductInfo == null || actProductInfo.size() == 0) {
            throw new EcAopServerBizException("9999", "在产品表或者产品属性表中未查询到产品ID【" + actProductId + "】的产品信息");
        }
        Map detailProduct = actProductInfo.get(0);
        String subProductMode = (String) (detailProduct.get("PRODUCT_MODE"));
        String subProductTypeCode = (String) (detailProduct.get("PRODUCT_TYPE_CODE"));
        String strBrandcode = (String) (detailProduct.get("BRAND_CODE"));
        String endOffSet = String.valueOf(detailProduct.get("END_OFFSET"));
        String enableTag = (String) (detailProduct.get("ENABLE_TAG"));// 0-绝对时间（看开始时间） 1-相对时间
        String strStartUnit = (String) (detailProduct.get("START_UNIT"));// '0:天 1:自然天 2:月 3:自然月 4:年 5:自然年';
        String endUnit = (String) (null == detailProduct.get("END_UNIT") ? "0" : detailProduct.get("END_UNIT"));
        String startOffset = String.valueOf(detailProduct.get("START_OFFSET"));// 生效偏移时间
        if (StringUtils.isEmpty(endOffSet) || "null".equals(endOffSet)) {
            actProParam.put("monthLasttDay", "20501231235959");
            actProParam.put("monthFirstDay", msg.get("activityStartTime"));
            return actProParam;
        }
        if (StringUtils.isEmpty(enableTag) || StringUtils.isEmpty(startOffset)
                || StringUtils.isEmpty(strStartUnit)) {
            enableTag = "0";
        }
        String newAcceptDate = null;
        try {
            newAcceptDate = GetDateUtils.transDate(acceptDate, 19);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        monthFirstDay = GetDateUtils.getSpecifyDateTime(Integer.parseInt(enableTag),
                Integer.parseInt(strStartUnit), newAcceptDate, Integer.parseInt(startOffset));
        monthFirstDay = GetDateUtils.TransDate(monthFirstDay, "yyyy-MM-dd HH:mm:ss");
        monthLasttDay = GetDateUtils.getSpecifyDateTime(Integer.parseInt(enableTag), Integer.parseInt(endUnit),
                newAcceptDate, Integer.parseInt(endOffSet) + Integer.parseInt(startOffset));
        monthLasttDay = GetDateUtils.getSpecifyDateTime(1, 6, monthLasttDay, -1); // 结束月最后一天
        monthLasttDay = GetDateUtils.TransDate(monthLasttDay, "yyyy-MM-dd HH:mm:ss");
        actProParam.put("PRODUCT_MODE", subProductMode);
        actProParam.put("PRODUCT_TYPE_CODE", subProductTypeCode);
        actProParam.put("BRAND_CODE", strBrandcode);
        actProParam.put("monthLasttDay", monthLasttDay);
        actProParam.put("monthFirstDay", monthFirstDay);
        actProParam.put("END_OFFSET", endOffSet);
        return actProParam;
    }
    private String changeNetType(String netType) {
        if ("00".equals(netType)) {
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


    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[]{PARAM_ARRAY[i]});
        }
    }
}
