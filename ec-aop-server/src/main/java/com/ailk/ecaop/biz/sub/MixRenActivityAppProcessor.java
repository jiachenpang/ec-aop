package com.ailk.ecaop.biz.sub;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.product.OneNumWithMoreCardProduct;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.NewProductUtils;
import com.ailk.ecaop.common.utils.NumCenterUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;

/**
 * 融合共享续约申请
 * @author shenzy 20170410
 */
@EcRocTag("mixRenActivityApp")
public class MixRenActivityAppProcessor extends BaseAopProcessor implements ParamsAppliable {

    OneNumWithMoreCardProduct product = new OneNumWithMoreCardProduct();
    NumCenterUtils nc = new NumCenterUtils();
    LanOpenApp4GDao dao = new LanOpenApp4GDao();
    LanUtils lan = new LanUtils();
    private static final String[] PARAM_ARRAY = { "ecaop.trade.cbss.checkUserParametersMapping",
            "ecaop.masb.chph.gifa.ParametersMapping", "ecaop.masb.sbac.sglUniTradeParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[3];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        preDataForPreCommit(exchange, msg);
        // 调预提交接口
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        lan.preData(pmp[2], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.masb.sbac.sglUniTradeTemplate", exchange);
        // 处理返回信息
        dealReturn(exchange);

    }

    /**
     * 处理cb的返回信息 by wangmc 20170627
     * @param exchange
     */
    private void dealReturn(Exchange exchange) {
        Map recvMap = exchange.getOut().getBody(Map.class);
        if (IsEmptyUtils.isEmpty(recvMap.get("rspInfo"))
                || IsEmptyUtils.isEmpty(((List<Map>) recvMap.get("rspInfo")).get(0))) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "调用cbss预提交未返回信息");
        }
        List<Map> rspInfo = ((List<Map>) recvMap.get("rspInfo"));
        Map retMap = new HashMap();// 返回的body信息
        retMap.put("provOrderId", rspInfo.get(0).get("provOrderId"));
        retMap.put("cbssOrderId", rspInfo.get(0).get("bssOrderId"));// cb返回的只有两条订单值
        retMap.put("totalFee", 0);// 默认总费用为0,如果cb返回则取cb的返回
        // 获取下游返回的订单费用信息
        List<Map> provinceOrderInfo = (List<Map>) rspInfo.get(0).get("provinceOrderInfo");
        if (!IsEmptyUtils.isEmpty(provinceOrderInfo)) {
            if (!IsEmptyUtils.isEmpty(provinceOrderInfo.get(0).get("totalFee"))) {
                retMap.put("totalFee", provinceOrderInfo.get(0).get("totalFee"));
            }
            // 处理费用
            List<Map> preFeeInfoRsp = (List<Map>) provinceOrderInfo.get(0).get("preFeeInfoRsp");
            if (!IsEmptyUtils.isEmpty(preFeeInfoRsp)) {
                List<Map> feeInfos = new ArrayList<Map>();
                for (Map feeInfo : preFeeInfoRsp) {
                    Map fee = new HashMap();
                    fee.put("feeId", feeInfo.get("feeTypeCode"));
                    fee.put("feeCategory", feeInfo.get("feeMode"));
                    fee.put("feeDes", feeInfo.get("feeTypeName"));
                    fee.put("maxRelief", feeInfo.get("maxDerateFee"));
                    fee.put("origFee", feeInfo.get("fee"));
                    feeInfos.add(fee);
                }
                if (!IsEmptyUtils.isEmpty(feeInfos)) {
                    retMap.put("feeInfo", feeInfos);
                }
            }
        }
        // 备用字段处理
        if (!IsEmptyUtils.isEmpty((List<Map>) recvMap.get("para"))) {
            retMap.put("para", recvMap.get("para"));
        }
        exchange.getOut().setBody(retMap);
    }

    /**
     * 准备调用预提交的参数
     * @param exchange
     * @param msg
     * @throws ParseException
     */
    private void preDataForPreCommit(Exchange exchange, Map msg) throws ParseException {
        // 1,调用三户接口获取用户的原有活动
        threepartCheck(ExchangeUtils.ofCopy(exchange, msg), msg);
        // 2,处理活动的时间
        checkActInfo(msg);
        preBaseData(exchange, msg);
        preExtData(msg);
    }

    /**
     * 处理活动时间
     * 1,续约活动限制:
     * 若用户原有活动即将到期(3个月以内)或已到期,可选择其他活动续约,否则只能续约原活动
     * 2,续约时间处理:
     * 用户有未失效合约时,续约活动的开始时间为原合约的到期月的次月一日,用户有已失效合约或无合约时,生效时间为下月一日,续约时间为1年
     * @param msg
     * @throws ParseException
     */
    private void checkActInfo(Map msg) throws ParseException {
        List<Map> oldActivityInfos = (List<Map>) msg.get("activityInfos");
        List<Map> newActivityInfos = (List<Map>) msg.get("activityInfo");
        List<String> dateList = new ArrayList<String>();
        Map activityInfo = new HashMap();
        // 若有老活动
        if (oldActivityInfos != null && oldActivityInfos.size() > 0) {
            // 因为三户返回信息可能有多个合约,需要取出当前或最近过期的合约
            for (Map ac : oldActivityInfos)
                dateList.add((String) ac.get("productInactiveTime"));
            Collections.sort(dateList);
            for (Map ac : oldActivityInfos) {
                if (ac.get("productInactiveTime").equals(dateList.get(dateList.size() - 1))) {
                    activityInfo = ac;
                }
            }
            Map newActivityInfo = newActivityInfos.get(0);
            String oldActivityId = (String) activityInfo.get("productId");
            String activityInactiveTime = (String) activityInfo.get("productInactiveTime");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            Date endDate = sdf.parse(activityInactiveTime);
            Date sysDate = sdf.parse(GetDateUtils.getDate());
            // 原活动到期后以及到期前3个月以内,可以选择与原活动不一样的活动续约
            if (!oldActivityId.equals(newActivityInfo.get("actPlanId"))) {
                // 获取当前时间3个月之前的时间
                Date threeMonbeforeSys = sdf.parse(GetDateUtils.rollDateStr(sdf.format(sysDate), "yyyyMMddHHmmss",
                        Calendar.MONTH, -3));
                if (threeMonbeforeSys.getTime() < endDate.getTime()) {
                    throw new EcAopServerBizException("9999", "活动结束时间离当前时间超过3个月,无法改变当前活动!");
                }
            }
            // 若活动还未到期
            if (sysDate.getTime() < endDate.getTime()) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(endDate);
                calendar.add(Calendar.MONTH, 1);
                calendar.set(Calendar.DAY_OF_MONTH, 1);
                msg.put("startDate", sdf.format(calendar.getTime()));
                msg.put("endDate",
                        GetDateUtils.rollDateStr(sdf.format(calendar.getTime()), "yyyyMMddHHmmss", Calendar.YEAR, 1)
                                .substring(0, 8) + "235959");
            }
            else {// 若活动已到期
                msg.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                msg.put("endDate",
                        GetDateUtils.rollDateStr(GetDateUtils.getNextMonthFirstDayFormat(), "yyyyMMddHHmmss",
                                Calendar.YEAR, 1).substring(0, 8)
                                + "235959");
            }
        }
        else { // 若没老活动,则开始时间为次月一号,结束时间为一年的前一天
            msg.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
            msg.put("endDate",
                    GetDateUtils.rollDateStr(
                            GetDateUtils.rollDateStr(GetDateUtils.getNextMonthFirstDayFormat(), "yyyyMMddHHmmss",
                                    Calendar.YEAR, 1), "yyyyMMddHHmmss", Calendar.DATE, -1).substring(0, 8)
                            + "235959");
        }
    }

    public static void main(String[] args) throws Exception {
        Map msg = new HashMap();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        Date endDate = sdf.parse("20170531125959");
        Date sysDate = sdf.parse(GetDateUtils.getDate());
        System.out.println("sysDate:" + sysDate.getTime());
        System.out.println("endDate:" + endDate.getTime());
        if (sysDate.getTime() < endDate.getTime()) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(endDate);
            calendar.add(Calendar.MONTH, 1);
            calendar.set(Calendar.DAY_OF_MONTH, 1);
            msg.put("startDate", sdf.format(calendar.getTime()));
            msg.put("endDate",
                    GetDateUtils.rollDateStr(sdf.format(calendar.getTime()), "yyyyMMddHHmmss", Calendar.YEAR, 1)
                            .substring(0, 8) + "235959");
        }
        else {// 若活动已到期
            msg.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
            msg.put("endDate",
                    GetDateUtils.rollDateStr(GetDateUtils.getNextMonthFirstDayFormat(), "yyyyMMddHHmmss",
                            Calendar.YEAR, 1).substring(0, 8)
                            + "235959");
        }
        System.out.println(msg);
        List<String> dateList = new ArrayList<String>();
        dateList.add("20170531125959");
        dateList.add("20180531125959");
        dateList.add("20190531125959");
        dateList.add("20160531125959");
        dateList.add("20140531125959");
        dateList.add("20150531125959");
        Collections.sort(dateList);
        System.out.println(dateList);
    }

    private void preExtData(Map msg) {
        TradeManagerUtils.preActivityInfo(msg);
        Map ext = new HashMap();
        ext.put("tradeItem", preDataForTradeItem(msg));
        ext.put("tradeProductType", preProductTpyeListData(msg));
        ext.put("tradeProduct", preProductData(msg));
        ext.put("tradeDiscnt", preDiscntData(msg));
        msg.put("ext", ext);
    }

    private Map preProductData(Map msg) {
        try {
            Map tradeProduct = new HashMap();
            List<Map> itemList = (List<Map>) msg.get("productList");
            Map item = itemList.get(0);
            item.put("xDatatype", "NULL");
            item.put("userId", msg.get("userId"));
            item.put("productMode", "50");
            item.put("productId", item.get("productId"));
            item.put("brandCode", item.get("brandCode"));
            item.put("itemId", msg.get("itemId"));
            item.put("modifyTag", "0");
            item.put("startDate", msg.get("startDate"));
            item.put("endDate", msg.get("endDate"));
            item.put("userIdA", MagicNumber.DEFAULT_NO_VALUE);
            tradeProduct.put("item", itemList);
            return tradeProduct;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_PRODUCT节点报错" + e.getMessage());
        }
    }

    private Map preDiscntData(Map msg) {
        try {
            Map tradeDis = new HashMap();
            List<Map> itemList = NewProductUtils.newDealRepeat((List<Map>) msg.get("discntList"), "discntList");
            List<Map> dis = new ArrayList<Map>();
            for (int i = 0; i < itemList.size(); i++) {
                Map item = new HashMap(itemList.get(i));
                item.put("xDatatype", "NULL");
                item.put("id", msg.get("userId"));
                item.put("idType", "1");
                item.put("userIdA", "-1");
                item.put("productId", item.get("productId"));
                item.put("packageId", item.get("packageId"));
                item.put("discntCode", item.get("discntCode"));
                item.put("specTag", "1");
                item.put("relationTypeCode", "");
                item.put("startDate", msg.get("startDate"));
                item.put("endDate", msg.get("endDate"));
                item.put("modifyTag", "0");
                item.put("itemId", msg.get("itemId"));
                dis.add(item);
            }
            tradeDis.put("item", dis);
            return tradeDis;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_DISCNT节点报错" + e.getMessage());
        }
    }

    private Map preProductTpyeListData(Map msg) {
        try {
            Map tradeProductType = new HashMap();
            List<Map> itemList = (List<Map>) msg.get("productTypeList");
            Map item = itemList.get(0);
            item.put("xDatatype", "NULL");
            item.put("userId", msg.get("userId"));
            item.put("productMode", "50");
            item.put("productId", item.get("productId"));
            item.put("productTypeCode", item.get("productTypeCode"));
            item.put("modifyTag", "0");
            item.put("startDate", msg.get("startDate"));
            item.put("endDate", msg.get("endDate"));
            tradeProductType.put("item", itemList);
            return tradeProductType;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_PRODUCT_TYPE节点报错" + e.getMessage());
        }
    }

    private Map preDataForTradeItem(Map msg) {
        try {
            Map tradeItem = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            itemList.add(lan.createAttrInfoNoTime("DEVELOP_STAFF_ID", msg.get("recomPersonId")));
            itemList.add(lan.createAttrInfoNoTime("DEVELOP_DEPART_ID", msg.get("channelId")));
            itemList.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", msg.get("eparchyCode")));
            itemList.add(LanUtils.createTradeItem("MAIN_USER_TAG", ""));
            tradeItem.put("item", itemList);
            return tradeItem;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_ITEM节点报错" + e.getMessage());
        }
    }

    /**
     * 从三户获取用户的活动信息
     * @param exchange
     * @param msg
     */
    private void threepartCheck(Exchange exchange, Map msg) {
        msg.put("serialNumber", msg.get("serialNumber"));
        msg.put("tradeTypeCode", "9999");
        msg.put("serviceClassCode", "0000");
        msg.put("getMode", "1111111111100013010000000100001");
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, msg);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[0], threePartExchange);
        try {
            CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
            lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "调用cbss三户接口出错:" + e.getMessage());
        }

        Map result = threePartExchange.getOut().getBody(Map.class);
        List<Map> custInfoList = (List<Map>) result.get("custInfo");
        List<Map> userInfoList = (List<Map>) result.get("userInfo");
        List<Map> acctInfoList = (List<Map>) result.get("acctInfo");
        // 需要通过userInfo的返回,拼装老产品信息
        Map custInfo = custInfoList.get(0);
        Map userInfo = userInfoList.get(0);
        Map acctInfo = acctInfoList.get(0);
        String eparchyCode = ChangeCodeUtils.changeCityCode(msg);
        String itemId = (String) GetSeqUtil.getSeqFromCb(pmp[1], ExchangeUtils.ofCopy(exchange, msg), "seq_item_id", 1)
                .get(0);
        String tradeId = (String) GetSeqUtil.getSeqFromCb(pmp[1], ExchangeUtils.ofCopy(exchange, msg), "seq_trade_id",
                1).get(0);
        List<Map> avtivityInfos = new ArrayList<Map>();
        for (Map product : (List<Map>) userInfo.get("productInfo")) {
            if ("50".equals(product.get("productMode"))) {
                avtivityInfos.add(product);
            }
        }
        msg.putAll(MapUtils.asMap("custInfo", custInfo, "userInfo", userInfo, "acctInfo", acctInfo, "userId",
                userInfo.get("userId"), "custId", custInfo.get("custId"), "acctId", acctInfo.get("acctId"), "custName",
                custInfo.get("custName"), "brandCode", userInfo.get("brandCode"), "productId",
                userInfo.get("productId"), "productName", userInfo.get("productName"), "productTypeCode",
                userInfo.get("productTypeCode"), "itemId", itemId, "eparchyCode", eparchyCode, "tradeId", tradeId,
                "startDate", GetDateUtils.getDate(), "userDiffCode", userInfo.get("userDiffCode"), "contact",
                custInfo.get("contact"), "contactPhone", custInfo.get("contactPhone"), "activityInfos", avtivityInfos,
                "operTypeCode", "0"));

    }

    private void preBaseData(Exchange exchange, Map msg) {
        try {
            String date = (String) msg.get("startDate");
            Map base = new HashMap();
            base.put("subscribeId", msg.get("tradeId"));
            base.put("tradeId", msg.get("tradeId"));
            base.put("startDate", date);
            base.put("tradeTypeCode", "0065");
            base.put("nextDealTag", "Z");
            base.put("olcomTag", "0");
            base.put("areaCode", msg.get("eparchyCode"));
            base.put("foregift", "0");
            base.put("execTime", date);
            base.put("acceptDate", date);
            base.put("chkTag", "0");
            base.put("operFee", "0");
            base.put("cancelTag", "0");
            base.put("netTypeCode", "00CP");
            base.put("advancePay", "0");
            String inModeCode = (String) new ChangeCodeUtils().getInModeCode(exchange.getAppCode());
            base.put("inModeCode", inModeCode);
            // 预受理系统要在tradeitem节点下下发WORK_TRADE_ID，所以放到msg里面用来判断
            msg.put("inModeCode", inModeCode);
            base.put("custId", msg.get("custId"));
            base.put("custName", msg.get("custName"));
            base.put("acctId", msg.get("acctId"));
            base.put("serinalNamber", msg.get("serialNumber"));
            base.put("productId", msg.get("productId"));
            base.put("tradeStaffId", msg.get("operatorId"));
            base.put("userDiffCode", "8800");
            base.put("brandCode", msg.get("brandCode"));
            base.put("usecustId", msg.get("custId"));
            base.put("feeState", "0");
            base.put("checktypeCode", "8");
            base.put("userId", msg.get("userId"));
            base.put("termIp", "132.35.81.217");
            base.put("eparchyCode", msg.get("eparchyCode"));
            base.put("cityCode", msg.get("district"));
            base.put("remark", "");
            msg.put("base", base);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装base节点报错");
        }
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
