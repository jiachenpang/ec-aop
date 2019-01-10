package com.ailk.ecaop.biz.sub;

/**
 * Created by Liu JiaDi on 2016/5/19.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.product.DateUtils;
import com.ailk.ecaop.biz.product.DealNewCbssProduct;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;

/**
 * 该方法用于订购和退订sp之类的服务
 * 封装三户、预提交、提交接口，目前只针对CBSS老用户业务
 * 预提交时只下发产品元素属性，不下发产品属性
 * 获取三户返回的产品判断主产品是啥
 * 产品生效时间根据enableTag判断 1 立即 2次月
 * 产品失效时间现根据产品计算 如果没有配置失效偏移 则写死2050年
 * @auther Liu JiaDi
 * @create 2016_05_19_10:57
 */
@EcRocTag("cbssSpServerOrder")
public class CbssSpServerOrderProcessor extends BaseAopProcessor {

    LanOpenApp4GDao dao = new LanOpenApp4GDao();
    DealNewCbssProduct n25Dao = new DealNewCbssProduct();
    LanUtils lan = new LanUtils();

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        // 为预提交准备参数
        preDataForPreCommit(exchange, body, msg);
        // 预提交接口
        lan.preData("ecaop.mvoa.preSub.ParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.trades.mvoa.preSub.template", exchange);

        Map retMap = exchange.getOut().getBody(Map.class);
        List<Map> rspInfoList = (ArrayList<Map>) retMap.get("rspInfo");
        if (null == rspInfoList || 0 == rspInfoList.size()) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "核心系统未返回订单信息.");
        }
        // 这两个信息要最终返回给接入系统，如不在此设置，可能会被后面的返回覆盖
        Map rspInfo = rspInfoList.get(0);
        String provOrderId = rspInfo.get("provOrderId").toString();
        String bssOrderId = rspInfo.get("bssOrderId").toString();
        // 调用cBSS的提交接口
        Map orderMap = preOrderSubParam(rspInfo);
        orderMap.put("orderNo", provOrderId);
        orderMap.put("provOrderId", bssOrderId);
        msg.putAll(orderMap);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        lan.preData("ecaop.trades.sccc.cancel.paramtersmapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
        lan.xml2Json("ecaop.trades.sccc.cancel.template", exchange);

        Map outMap = new HashMap();
        Map orderOutMap = exchange.getOut().getBody(Map.class);
        String taxNo = (String) orderOutMap.get("taxNo");
        if (StringUtils.isNotEmpty(taxNo)) {
            // 税控码
            outMap.put("taxNo", taxNo);
        }
        // 受理成功时，返回总部和省份订单
        outMap.put("provOrderId", provOrderId);
        outMap.put("bssOrderId", bssOrderId);
        exchange.getOut().setBody(outMap);
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
        retMap.put("reliefFee", inputMap.get("maxDerateFee"));
        retMap.put("realFee", inputMap.get("oldFee"));
        return retMap;
    }

    /**
     * 准备预提交参数
     */
    private void preDataForPreCommit(Exchange exchange, Map body, Map msg) {
        Map ext = new HashMap();
        try {
            msg.put("operTypeCode", "0");
            // msg.put("serviceClassCode", "00CP");// FIXME
            // 转换地市编码
            String provinceCode = "00" + msg.get("province");
            msg.put("provinceCode", provinceCode);
            msg.put("eparchyCode", ChangeCodeUtils.changeEparchy(msg));
            msg.put("date", GetDateUtils.getSysdateFormat());
            // 三户信息准备
            threepartCheck(exchange, body, msg);
            // 生成cb订单号
            String orderid = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_trade_id");
            msg.put("tradeId", orderid);
            msg.put("ordersId", orderid);
            // 生成ItemId号
            String ItemId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_item_id");
            msg.put("itemId", ItemId);
            // 准备base参数
            msg.put("base", preBaseData(msg, exchange.getAppCode()));
            // 准备产品信息
            preProductInfo(msg);
            System.out.println(msg);
            // 查询cycId
            List<Map> cycIdresult = dao.querycycId(msg);
            String cycId = (String) (cycIdresult.get(0).get("CYCLE_ID"));
            msg.put("cycId", cycId);
            // ext.put("tradeSubItem", preDataForTradeSubItem(msg));
            ext.put("tradeItem", preDataForTradeItem(msg, exchange.getAppCode()));
            // ext.put("tradeDiscnt", preDiscntData(msg));
            // ext.put("tradeSvc", preTradeSvcData(msg));
            ext.put("tradeSp", preTradeSpData(msg));
            msg.put("ext", ext);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", e.getMessage());
        }

        body.put("msg", msg);
        exchange.getIn().setBody(body);
    }

    /**
     * 拼装tradeSp节点
     */
    public Map preTradeSpData(Map msg) {
        try {
            Map tardeSp = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            List<Map> sp = (List<Map>) msg.get("spList");
            List<Map> productInfo = (List<Map>) msg.get("productInfo");
            String optType = (String) productInfo.get(0).get("optType");
            String enableTag = (String) productInfo.get(0).get("enableTag");
            for (int i = 0; i < sp.size(); i++) {
                Map item = new HashMap();
                // item.put("userId", msg.get("userId"));
                // item.put("serialNumber", msg.get("serialNumber"));
                item.put("productId", sp.get(i).get("productId"));
                item.put("packageId", sp.get(i).get("packageId"));
                item.put("partyId", sp.get(i).get("partyId"));
                item.put("spId", sp.get(i).get("spId"));
                item.put("spProductId", sp.get(i).get("spProductId"));
                item.put("firstBuyTime", GetDateUtils.getDate());
                item.put("paySerialNumber", msg.get("serialNumber"));

                item.put("startDate", sp.get(i).get("activityStarTime"));
                item.put("endDate", sp.get(i).get("activityTime"));
                System.out.println(msg.get("optType") + "jfjjklsdl555" + msg.get("enableTag"));
                if ("01".equals(optType) && "2".equals(enableTag)) {
                    item.put("endDate", DateUtils.getThisMonthLastDay());
                }

                // item.put("updateTime", msg.get("date"));
                item.put("remark", "");
                if ("0".equals(sp.get(i).get("optType"))) {
                    item.put("modifyTag", "0");
                }
                else {
                    item.put("modifyTag", "1");
                }
                item.put("payUserId", msg.get("userId"));
                item.put("spServiceId", sp.get(i).get("spServiceId"));
                item.put("itemId", "-1");
                item.put("userIdA", msg.get("userId"));
                // item.put("xDatatype", "NULL");
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
                if ("0".equals(svc.get(i).get("optType"))) {
                    item.put("modifyTag", "0");
                }
                else {
                    item.put("modifyTag", "1");
                }
                item.put("startDate", svc.get(i).get("activityStarTime"));
                item.put("endDate", svc.get(i).get("activityTime"));
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

    /**
     * 拼装tradediscnt节点
     */
    public Map preDiscntData(Map msg) {
        try {
            Map tradeDis = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            List<Map> discnt = (List<Map>) msg.get("discntList");
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
                item.put("startDate", discnt.get(i).get("activityStarTime"));
                item.put("endDate", discnt.get(i).get("activityTime"));
                if ("0".equals(discnt.get(i).get("optType"))) {
                    item.put("modifyTag", "0");
                }
                else {
                    item.put("modifyTag", "1");
                }

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

    // 拼装TRADE_ITEM
    private Map preDataForTradeItem(Map msg, String appCode) {
        try {
            List<Map> Item = new ArrayList<Map>();
            Map tradeItem = new HashMap();
            if (new ChangeCodeUtils().isWOPre(appCode)) {
                Item.add(LanUtils.createTradeItem("WORK_TRADE_ID", msg.get("ordersId")));
            }
            Item.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", "0371"));
            // Item.add(lan.createTradeItem("NOTE", "spServerOrder"));
            // Item.add(lan.createTradeItem("BLACK_USER_TAG", "0"));
            tradeItem.put("item", Item);
            return tradeItem;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_ITEM节点报错" + e.getMessage());
        }
    }

    // 拼装TRADE_SUB_ITEM
    private Map preDataForTradeSubItem(Map msg) {
        try {
            List<Map> Item = new ArrayList<Map>();
            Map tradeSubItem = new HashMap();
            String itemId = (String) msg.get("userId");
            Item.add(lan.createTradeSubItemE("LINK_NAME", msg.get("custName"), itemId));
            Item.add(lan.createTradeSubItemE("LINK_PHONE", msg.get("serialNumber"), itemId));
            String personId = (String) msg.get("recomPersonId");
            String personName = (String) msg.get("recomPersonName");
            if (StringUtils.isNotEmpty(personId) && StringUtils.isNotEmpty(personName)) {
                Item.add(lan.createTradeSubItemE("RECOMMENDED_PERSON_ID", personId, itemId));
                Item.add(lan.createTradeSubItemE("RECOMMENDED_PERSON", personName, itemId));
            }
            tradeSubItem.put("item", Item);
            return tradeSubItem;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_SUB_ITEM节点报错" + e.getMessage());
        }
    }

    /**
     * 获取三户信息
     */
    private void threepartCheck(Exchange exchange, Map body, Map msg) {
        try {
            msg.put("tradeTypeCode", "9999");
            msg.put("getMode", "1111111111100013010000000100001");
            exchange.getIn().setBody(body);
            lan.preData("ecaop.trade.cbss.checkUserParametersMapping", exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
            lan.xml2Json("ecaop.trades.cbss.threePart.template", exchange);

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
            List<Map> oldProductInfo = (List<Map>) userInfo.get(0).get("productInfo");
            if (null == oldProductInfo || 0 == oldProductInfo.size()) {
                throw new EcAopServerBizException("9999", "三户接口未返回产品信息");
            }
            for (int i = 0; i < oldProductInfo.size(); i++) {
                if ("00".equals(oldProductInfo.get(i).get("productMode"))) {
                    msg.put("mainProduct", oldProductInfo.get(i).get("productId"));
                }
            }
            msg.put("userId", userInfo.get(0).get("userId"));
            List<Map> acctInfo = (List<Map>) checkUserMap.get("acctInfo");
            if (null == acctInfo || 0 == acctInfo.size()) {
                throw new EcAopServerBizException("9999", "账户信息未返回");
            }
            msg.put("acctId", acctInfo.get(0).get("acctId"));
            body.put("msg", msg);
            exchange.getIn().setBody(body);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "获取三户信息失败" + e.getMessage());
        }
    }

    // 准备base参数
    private Map preBaseData(Map msg, String appCode) {
        try {
            String date = (String) msg.get("date");
            Map base = new HashMap();
            base.put("subscribeId", msg.get("tradeId"));
            base.put("tradeId", msg.get("tradeId"));
            base.put("startDate", date);
            base.put("tradeTypeCode", "0381");
            base.put("nextDealTag", "Z");
            base.put("olcomTag", "0");
            base.put("areaCode", msg.get("eparchyCode"));
            base.put("foregift", "0");
            base.put("execTime", date);
            base.put("acceptDate", date);
            base.put("chkTag", "0");
            base.put("operFee", "0");
            base.put("cancelTag", "0");
            base.put("endAcycId", "203701");
            base.put("startAcycId", msg.get("cycId"));
            base.put("acceptMonth", RDate.currentTimeStr("MM"));
            base.put("netTypeCode", "50");
            base.put("advancePay", "0");
            base.put("inModeCode", new ChangeCodeUtils().getInModeCode(appCode));
            base.put("custId", msg.get("custId"));
            base.put("custName", msg.get("custName"));
            base.put("acctId", msg.get("acctId"));
            base.put("serinalNamber", msg.get("serialNumber"));
            base.put("productId", msg.get("mainProduct"));
            base.put("tradeStaffId", msg.get("operatorId"));
            base.put("userDiffCode", "00");
            base.put("brandCode", "4G00");
            base.put("usecustId", msg.get("custId"));
            base.put("checktypeCode", "8");
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
     * 处理产品信息
     */
    public void preProductInfo(Map msg) {

        String provinceCode = (String) msg.get("provinceCode");
        List<Map> productInfo = (List<Map>) msg.get("productInfo");

        List<Map> discntList = new ArrayList<Map>();
        List<Map> svcList = new ArrayList<Map>();
        List<Map> spList = new ArrayList<Map>();
        String monthLasttDay = "";
        String monthFirstDay = "";
        // 处理产品信息
        // try {
        if (productInfo != null && productInfo.size() > 0) {
            for (int i = 0; i < productInfo.size(); i++) {
                List<Map> packageElement = (List<Map>) productInfo.get(i).get("packageElement");
                String enableTag = String.valueOf(productInfo.get(i).get("enableTag"));
                String optType = String.valueOf(productInfo.get(i).get("optType"));
                if ("00".equals(optType)) {
                    optType = "0";
                }
                else {
                    optType = "1";
                }

                if ("1".equals(enableTag)) {
                    monthFirstDay = GetDateUtils.getSysdateFormat();
                    monthLasttDay = "20501231235959";
                    // 根据cbss要求，退订时结束时间要和开始时间一样。。 by shenzy 2017-01-17
                    if ("1".equals(optType)) {
                        monthLasttDay = monthFirstDay;
                    }
                }
                else {
                    monthFirstDay = GetDateUtils.getNextMonthFirstDayFormat();
                    monthLasttDay = "20501231235959";
                    if ("1".equals(optType)) {
                        monthLasttDay = monthFirstDay;
                    }
                }
                String productId = String.valueOf(productInfo.get(i).get("productId"));
                // Map productDate = getproductDate(productId);

                // 附加包
                if (packageElement != null && packageElement.size() > 0) {
                    for (int n = 0; n < packageElement.size(); n++) {

                        String packageId = (String) packageElement.get(n).get("packageId");
                        String elementId = (String) packageElement.get(n).get("elementId");
                        String spId = "-1";
                        String partyId = "-1";
                        String spProductId = "-1";
                        Map spItemParam = new HashMap();
                        spItemParam.put("PTYPE", "X");

                        spItemParam.put("PRODUCT_ID", productId);
                        spItemParam.put("PROVINCE_CODE", provinceCode);
                        spItemParam.put("SPSERVICEID", elementId);
                        List<Map> spItemInfoResult = new ArrayList<Map>();
                        if ("-1".equals(productId)) {// 套外的sp
                            spItemInfoResult = n25Dao.queryProductItemInfoForSp(spItemParam);
                            System.out.println("新数据查询结果：" + spItemInfoResult);
                            if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                throw new EcAopServerBizException("9999", "在产品映射表中未查询到资费ID【" + elementId + "】的属性信息");
                            }
                        }
                        else {// 套内的sp
                            spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                            System.out.println("新数据查询结果：" + spItemInfoResult);
                            if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                throw new EcAopServerBizException("9999", "在产品映射表产品" + productId + "下未查询到资费ID【"
                                        + elementId + "】的属性信息");
                            }
                        }
                        for (int j = 0; j < spItemInfoResult.size(); j++) {
                            Map spItemInfo = spItemInfoResult.get(j);
                            spId = String.valueOf(spItemInfo.get("SP_ID"));
                            partyId = String.valueOf(spItemInfo.get("PARTY_ID"));
                            spProductId = String.valueOf(spItemInfo.get("SP_PRODUCT_ID"));
                        }
                        Map sp = new HashMap();
                        sp.put("productId", productId);
                        sp.put("packageId", packageId);
                        sp.put("partyId", partyId);
                        sp.put("spId", spId);
                        sp.put("spProductId", spProductId);
                        sp.put("spServiceId", elementId);
                        sp.put("activityStarTime", monthFirstDay);
                        sp.put("activityTime", monthLasttDay);
                        sp.put("optType", optType);
                        if ("01".equals(optType) && "2".equals(enableTag)) {
                            sp.put("activityTime", DateUtils.getThisMonthLastDay());
                        }
                        spList.add(sp);
                    }
                }
            }
        }
        msg.put("spList", spList);
    }

    /**
     * 获取产品或者活动的有效期
     */
    public Map getproductDate(String actProductId) {
        String acceptDate = GetDateUtils.getDate();
        String monthLasttDay = "2050-12-31 59:59:59";
        Map actProParam = new HashMap();
        actProParam.put("PRODUCT_ID", actProductId);
        List<Map> actProductInfo = dao.queryActProductInfo(actProParam);
        if (actProductInfo == null || actProductInfo.size() == 0) {
            throw new EcAopServerBizException("9999", "在产品表或者产品属性表中未查询到产品ID【" + actProductId + "】的产品信息");
        }
        Map detailProduct = actProductInfo.get(0);
        String endOffSet = String.valueOf(detailProduct.get("END_OFFSET"));
        String enableTag = (String) (detailProduct.get("ENABLE_TAG"));// 0-绝对时间（看开始时间） 1-相对时间
        String endUnit = (String) (null == detailProduct.get("END_UNIT") ? "0" : detailProduct.get("END_UNIT"));
        String startOffset = String.valueOf(detailProduct.get("START_OFFSET"));// 生效偏移时间
        if ("null".equals(enableTag) || "null".equals(startOffset) || "null".equals(endOffSet)
                || "null".equals(endUnit)) {
            actProParam.put("monthLasttDay", monthLasttDay);
            return actProParam;
        }
        String newAcceptDate = null;
        try {
            newAcceptDate = GetDateUtils.transDate(acceptDate, 19);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        monthLasttDay = GetDateUtils.getSpecifyDateTime(Integer.parseInt(enableTag), Integer.parseInt(endUnit),
                newAcceptDate, Integer.parseInt(endOffSet) + Integer.parseInt(startOffset));
        monthLasttDay = GetDateUtils.getSpecifyDateTime(1, 6, monthLasttDay, -1); // 结束月最后一天
        monthLasttDay = GetDateUtils.TransDate(monthLasttDay, "yyyy-MM-dd HH:mm:ss");
        actProParam.put("monthLasttDay", monthLasttDay);

        return actProParam;
    }

}
