package com.ailk.ecaop.biz.sub;

/**
 * Created by Liu JiaDi on 2015/12/1.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.res.QryChkTermN6Processor;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.DealCertTypeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.NumCenterUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;

/**
 * 该接口是用来处理主副卡开户的业务逻辑
 * 
 * @auther Liu JiaDi
 * @create 2015_12_01_11:56
 */
@EcRocTag("mainViceOpenProcessor")
@SuppressWarnings({ "rawtypes", "unchecked", "static-access" })
public class MainViceOpenProcessor extends BaseAopProcessor implements ParamsAppliable {

    LanOpenApp4GDao dao = new LanOpenApp4GDao();
    NumCenterUtils nc = new NumCenterUtils();
    LanUtils lan = new LanUtils();
    private static final String[] PARAM_ARRAY = { "ecaop.trade.cbss.checkUserParametersMapping",
            "ecaop.masb.chph.gifa.ParametersMapping",
            "ecaop.mvoa.preSub.ParametersMapping", "ecaop.mvoa.queryMainAndVice.ParametersMapping", "ecaop.cust.cbss.check.ParametersMapping"};
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[5];

    @Override
    public void process(Exchange exchange) throws Exception {

        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        msg.put("msgTradeItem", msg.get("tradeItem"));// 字段重复，会覆盖，用这个替代下
        msg.put("appCode0404", exchange.getAppCode()); // RSD2018040400074 关于AOP支持山东沃受理非同客户新装4G主副卡的需求
        // 针对关于AOP主副卡接口支持备注字段录入功能需求修改remark字段
        msg.put("oldRemark", msg.get("remark"));
        msg.put("methodCode", exchange.getMethodCode());
        // 针对号卡中心的号码要做预占
      String numSwitch = (String) exchange.getProperty("numSwitch");
        Exchange exchangeNum = ExchangeUtils.ofCopy(exchange, msg);
        occupyNumCenter(exchangeNum, numSwitch);
        List<Map> simCardNoList = (List<Map>) msg.get("simCardNo");
        msg.put("IS_REMOTE", "2");// 白卡
        String simId = "";
        if (null != simCardNoList && !simCardNoList.isEmpty()) {
            simId = (String) simCardNoList.get(0).get("simId");
            msg.put("simId", simId);
        }
        if (StringUtils.isNotEmpty(simId)) {
            Map cardCenterRet = qryCardInfo(msg, simId, exchange);
            if (null == cardCenterRet || cardCenterRet.isEmpty()) {
                throw new EcAopServerBizException("9999", "号卡中心未返回卡信息");
            }
            Map uniBssBody = (Map) cardCenterRet.get("UNI_BSS_BODY");
            Map qryCardInfoRsp = (Map) uniBssBody.get("QRY_CARD_INFO_RSP");
            if (!"0000".equals(qryCardInfoRsp.get("RESP_CODE"))) {
                throw new EcAopServerBizException("9999", qryCardInfoRsp.get("RESP_DESC").toString());
            }
            List<Map> cardInfoList = (List<Map>) qryCardInfoRsp.get("INFO");
            if (null == cardInfoList || cardInfoList.isEmpty()) {
                throw new EcAopServerBizException("9999", "号卡中心卡信息未返回");
            }
            msg.put("cardInfo", cardInfoList.get(0));
            msg.put("IS_REMOTE", "1");// 成卡
        }
        if (!IsEmptyUtils.isEmpty(msg.get("groupFlag"))) {
            String groupFlag = (String) msg.get("groupFlag");
            msg.put("groupFlag", groupFlag);
            // 收入归集集团处理
            if ("1".equals(groupFlag)) {// 收入集团归集
                Map jtcpMap = new HashMap();
                jtcpMap.put("province", "00" + msg.get("province"));
                jtcpMap.put("groupId", msg.get("groupId"));
                jtcpMap.put("operatorId", msg.get("operatorId"));
                jtcpMap.put("city", ChangeCodeUtils.changeEparchy(msg));
                jtcpMap.put("district", msg.get("district"));
                jtcpMap.put("channelId", msg.get("channelId"));
                jtcpMap.put("channelType", msg.get("channelType"));
                msg = dealJtcp(exchange, jtcpMap, msg);
            }
        }

        // 为预提交准备参数
        preDataForPreCommit(exchange, body, msg);
        lan.preData(pmp[2], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.trades.mvoa.preSub.template", exchange);

        Map retMap = exchange.getOut().getBody(Map.class);
        List<Map> rspInfoList = (ArrayList<Map>) retMap.get("rspInfo");
        if (null == rspInfoList || 0 == rspInfoList.size()) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "核心系统未返回订单信息.");
        }
        //记录订单信息，目前不支持成卡开户 1成卡 2 白卡
        // msg.put("IS_REMOTE", "2");
        TradeManagerUtils.insert2CBSSTrade(msg);
        // 处理费用项
        Map feeInfo = dealCbssPreSubmitRet(rspInfoList);
        feeInfo.put("provOrderId", rspInfoList.get(0).get("bssOrderId"));
        exchange.getOut().setBody(feeInfo);

    }

    private Map dealJtcp(Exchange exchange, Map jtcpMap, Map msg) {
        String cBSSGroupId = (String) msg.get("cBSSGroupId");
        Exchange jtcpExchange = ExchangeUtils.ofCopy(exchange, jtcpMap);
        LanUtils lan = new LanUtils();
        try {
            lan.preData("ecaop.trades.sell.mob.jtcp.ParametersMapping", jtcpExchange);
            CallEngine.wsCall(jtcpExchange, "ecaop.comm.conf.url.cbss.services.OrdForAopthSer");// :地址
            lan.xml2Json("ecaop.trades.sell.mob.jtcp.template", jtcpExchange);
        } catch (Exception e) {
            throw new EcAopServerBizException("9999", "集团信息查询失败" + e.getMessage());
        }
        Map retMap = jtcpExchange.getOut().getBody(Map.class);
        List<Map> userList = (List<Map>) retMap.get("useList");
        if (null == userList || userList.isEmpty()) {
            throw new EcAopServerBizException("9999", "未获取到CBSS集团客户下的用户信息");
        }
        String serialNumberA = (String) userList.get(0).get("serialNumber");
        String userIdA = (String) userList.get(0).get("userId");
        // 若外围传了cBSSGroupId 则取用groupId查询出来的结果中与cBSSGroupId一致的信息
        // 否则默认取第一条
        if (null != cBSSGroupId && !"".equals(cBSSGroupId)) {
            for (Map item : userList) {
                if (cBSSGroupId.equals(item.get("userId"))) {
                    userIdA = cBSSGroupId;
                    serialNumberA = (String) item.get("serialNumber");
                }
            }
        }
        if (StringUtils.isEmpty(serialNumberA) || StringUtils.isEmpty(userIdA)) {
            throw new EcAopServerBizException("9999", "未获取到CBSS集团客户下的用户信息");
        }
        msg.put("groupUserIdA", userIdA);
        msg.put("groupSerialNumberA", serialNumberA);
        return msg;
    }

    private Map qryCardInfo(Map msg, String simId, Exchange exchange) throws Exception {
        Map preDataMap = NumFaceHeadHelper.creatHead(exchange.getAppkey());
        Map qryCardInfoReq = new HashMap();
        String sysCode = new NumCenterUtils().changeSysCode(exchange);
        qryCardInfoReq.put("STAFF_ID", msg.get("operatorId"));
        qryCardInfoReq.put("PROVINCE_CODE", msg.get("province"));
        qryCardInfoReq.put("CITY_CODE", msg.get("city"));
        qryCardInfoReq.put("DISTRICT_CODE", msg.get("district"));
        qryCardInfoReq.put("CHANNEL_ID", msg.get("channelId"));
        qryCardInfoReq.put("CHANNEL_TYPE", msg.get("channelType"));
        qryCardInfoReq.put("SYS_CODE", sysCode);// 操作系统编码
        qryCardInfoReq.put("CARD_TYPE", "");
        qryCardInfoReq.put("ICCID_START", simId);
        qryCardInfoReq.put("ICCID_END", simId);
        preDataMap.put("UNI_BSS_BODY", MapUtils.asMap("QRY_CARD_INFO_REQ", qryCardInfoReq));
        Exchange qryExchange = ExchangeUtils.ofCopy(preDataMap, exchange);
        CallEngine.numCenterCall(qryExchange, "ecaop.comm.conf.url.numbercenter.qryCardInfo");
        Map retCardInfo = (Map) JSON.parse(qryExchange.getOut().getBody().toString());
        return retCardInfo;
    }
    private void occupyNumCenter(Exchange exchange, String numSwitch) {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        if ("1".equals(numSwitch)) {
            return;
        }
        if ("1".equals(getFromPara(msg, "isOccupy"))) {
            return;
        }
        dealReqPreemted(exchange);
        try {
            CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.numbercenter.preemptedNum");
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "调用号卡中心失败" + e.getMessage());
        }
        nc.dealReturnHead(exchange);
        dealReturnPreemptedNum(exchange, msg);
    }

    private void dealReqPreemted(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map requestBody = new HashMap();
        Map requestTempBody = new HashMap();
        requestTempBody.putAll(nc.changeCommonImplement(msg));
        try {
            requestBody.putAll(NumFaceHeadHelper.creatHead(exchange.getAppkey()));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        requestTempBody.put("SYS_CODE", nc.changeSysCode(exchange));
        requestTempBody.put("SERIAL_NUMBER", msg.get("serialNumber"));
        Map resultMap = new HashMap();
        resultMap.put("PREEMPTED_NUM_REQ", requestTempBody);
        requestBody.put("UNI_BSS_BODY", resultMap);
        exchange.getIn().setBody(JSON.toJSON(requestBody));
    }

    private void dealReturnPreemptedNum(Exchange exchange, Map msg) {
        Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
        Map bodyMap = (Map) outMap.get("UNI_BSS_BODY");
        Map resultMap = (Map) bodyMap.get("PREEMPTED_NUM_RSP");
        if (IsEmptyUtils.isEmpty(bodyMap) || IsEmptyUtils.isEmpty(resultMap)) {
            throw new EcAopServerBizException("9999", "号码状态查询接口号卡中心返回结果为空");
        }
        if ("0000".equals(resultMap.get("RESP_CODE"))) {
            try {
                msg.put("numState", "2");
                msg.put("sysCode", exchange.getAppCode());
                nc.InsertTradeInfo(msg);
            } catch (Exception e) {
               return;
            }
        } else {
			throw new EcAopServerBizException("9999", "号卡中心返回其他错误," + resultMap.get("RESP_DESC") + "");
        }
    }

    private Map dealCbssPreSubmitRet(List<Map> rspInfoList) {
        List<Map> retFeeList = new ArrayList<Map>();
        // 总费用
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
        feeInfo.put("totalFee", totalFee.toString());
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
            Map retFee = new HashMap();
            retFee.put("feeId", fee.get("feeTypeCode"));
            retFee.put("feeCategory", fee.get("feeMode"));
            retFee.put("feeDes", fee.get("feeTypeName"));
            retFee.put("maxRelief", fee.get("maxDerateFee"));
            retFee.put("origFee", fee.get("fee"));
            retFeeList.add(retFee);
        }
        return retFeeList;
    }

    private void preDataForPreCommit(Exchange exchange, Map body, Map msg) {
        Map ext = new HashMap();
        try {
            msg.put("operTypeCode", "0");
            // msg.put("serviceClassCode", "00CP");
            // 转换地市编码
            String provinceCode = "00" + msg.get("province");
            msg.put("provinceCode", provinceCode);
            String eparchyCode = ChangeCodeUtils.changeEparchy(msg);
            msg.put("eparchyCode", eparchyCode);
            msg.put("date", GetDateUtils.getDate());

            // 三户信息准备
            threepartCheck(exchange, body, msg);
            // 获取主产品信息
            List<Map> productInfo = (List<Map>) msg.get("productInfo");
            for (Map product : productInfo) {
                if ("1".equals(product.get("productMode"))) {
                    msg.put("mainProduct", product.get("productId"));
                    product.put("productMode", "0");
                }
                else {
                    product.put("productMode", "1");
                }
            }
            // 因为使用共用方法，必须把phoneSpeedLevel转换成100/42/300形式下发
            msg.put("phoneSpeedLevel", changeSpeed(msg));

            // 获取终端信息
            getResourceInfo(exchange);
            // 生成cb订单号
            // Exchange tradeExchange = ExchangeUtils.ofCopy(exchange, msg);
            String orderid = (String) GetSeqUtil.getSeqFromCb(pmp[1], ExchangeUtils.ofCopy(exchange, msg),
                    "seq_trade_id", 1).get(0);
            msg.put("tradeId", orderid);
            msg.put("ordersId", orderid);
            // 生成ItemId号
            String ItemId = (String) GetSeqUtil.getSeqFromCb(pmp[1], ExchangeUtils.ofCopy(exchange, msg),
                    "seq_item_id", 1).get(0);
            msg.put("itemId", ItemId);
            // 地市编码转换
            // msg.put("city", ChangeCodeUtils.changeEparchy(msg));
            // 准备base参数
            msg.put("base", preBaseData(msg, exchange.getAppCode()));
            // 准备产品信息
            // 用于获取数据库中此接口主产品下资费要处理的偏移配置 by wangmc 20181102
            msg.put("GET_DEAL_DIS_METHOD", exchange.getMethodCode());
            TradeManagerUtils.newPreProductInfo(productInfo, provinceCode, msg);
            // 账户信息梳理
            List<Map> acctInfo = (List<Map>) msg.get("acctInfo");
            if (null != acctInfo && acctInfo.size() > 0) {
                // 账户付费方式
                String accountPayType = (String) (acctInfo.get(0).get("accountPayType"));
                msg.put("accountPayType", accountPayType);
            }
            // 获取nbrSrc
            String nbrSrc = getNumberSrc(provinceCode, msg.get("serialNumber").toString());
            msg.put("nbrSrc", nbrSrc);
            // 查询cycId
            List<Map> cycIdresult = dao.querycycId(msg);
            String cycId = (String) (cycIdresult.get(0).get("CYCLE_ID"));
            msg.put("cycId", cycId);

            String payRelId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "SEQ_PAYRELA_ID");
            msg.put("payRelId", payRelId);
            // 准备活动信息
            preActivityInfo(msg);
            if (null != msg.get("tradePurchase")) {
                ext.put("tradePurchase", preTradePurchase(msg));
            }
            // CHANGE_2 北分放入appCode by wangmc 20180706
            msg.put("appCode", exchange.getAppCode());
            ext.put("tradeRelation", preDataForTradeRelation(msg));
            msg.remove("appCode");

            if ("1".equals(msg.get("groupFlag"))) {
                ext.put("tradeRelation", preJtcpForTradeRelation(msg, ext));
            }
            String custType = getFromPara(msg, "custType");
            boolean flag = "1".equals(custType);// 为1是老客户，非1新客户 RSD2018040400074如果外围传入了custId就用外围传入的，针对山东改造
            ext.put("tradeSubItem", preDataForTradeSubItem(msg));
            ext.put("tradeUser", preDataForTradeUser(msg));
            ext.put("tradeItem", preDataForTradeItem(msg, exchange.getAppCode()));
            ext.put("tradeRes", preDataForTradeRes(msg));
            ext.put("tradePayrelation", preTradePayRelData(msg));
            ext.put("tradeAcct", preDataForTradeAcct(msg));
            ext.put("tradeProductType", preProductTpyeListData(msg));
            ext.put("tradeProduct", preProductData(msg));
            ext.put("tradeDiscnt", preDiscntData(msg, exchange));
            ext.put("tradeSvc", preTradeSvcData(msg));
            ext.put("tradeSp", preTradeSpData(msg));
            ext.put("tradefeeSub", preTradeFeeSub(msg));
            if (!(flag && "sdpr|sdapp".contains(msg.get("appCode0404") + "")) || ("0".equals(msg.get("mainNumberTag")))) {// 山东+副卡+老客户的时候不下发tradeCustomer/tradeCustPerson
                ext.put("tradeCustPerson", preCustPerData(msg));// RSD2018040400074如果外围传入了custId就用外围传入的，针对山东改造
                ext.put("tradeCustomer", preCustomerData(msg));
            }
            ext.put("tradeElement", preTradeElementData(msg));
            // RHQ2018082800066-增加“港澳台居民居住证”证件类型的需求 by wangmc 20180914
            msg.put("apptx", body.get("appxt"));
            DealCertTypeUtils.dealCertType(msg, (Map) msg.get("custInfo"), ext);
            msg.put("ext", ext);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", e.getMessage());
        }
        Map base = (Map) msg.get("base");
        msg.put("mProductId", base.get("productId"));
        msg.put("mBrandCode", base.get("brandCode"));
        body.put("msg", msg);
        exchange.getIn().setBody(body);
    }

    /**
     * 获取收入归集集团信息
     */
    private Map preJtcpForTradeRelation(Map msg, Map ext) {
        List<Map> relaItem = new ArrayList<Map>();
        if (!IsEmptyUtils.isEmpty(ext.get("tradeRelation"))) {
            relaItem = (List<Map>) ((Map) ext.get("tradeRelation")).get("item");
        }
        Map item = new HashMap();
        Map tradeRelation = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("relationAttr", "");
        item.put("relationTypeCode", "2222");
        item.put("idA", msg.get("groupUserIdA"));
        item.put("idB", msg.get("userId"));
        item.put("roleCodeA", "0");
        item.put("roleCodeB", "0");
        item.put("orderno", "");
        item.put("shortCode", "");
        item.put("startDate", GetDateUtils.getDate());
        item.put("endDate", "2050-12-31 23:59:59");
        item.put("modifyTag", "0");
        item.put("remark", "");
        item.put("serialNumberA", msg.get("groupSerialNumberA"));
        item.put("serialNumberB", msg.get("serialNumber"));
        item.put("itemId", TradeManagerUtils.getSequence((String) msg.get("eparchyCode"), "SEQ_ITEM_ID"));
        relaItem.add(item);
        tradeRelation.put("item", relaItem);
        return tradeRelation;
    }

    private String changeSpeed(Map msg) {
        String phoneSpeedLevel = (String) msg.get("phoneSpeedLevel") + "";
        if ("0".equals(phoneSpeedLevel)) {
            phoneSpeedLevel = "100";
        }
        else if ("3".equals(phoneSpeedLevel)) {
            phoneSpeedLevel = "300";
        }
        else {
            phoneSpeedLevel = "42";
        }
        return phoneSpeedLevel;
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

    /**
     * 获取终端信息
     */
    private void getResourceInfo(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map map = new HashMap();
        List<Map> userInfo = new ArrayList<Map>();
        List<Map> activityInfo = (List<Map>) msg.get("activityInfo");
        if (null != activityInfo) {
            for (Map activityMap : activityInfo) {
                if (null != activityMap.get("resourcesCode")) {
                    activityMap.put("resourcesType", "03");
                    userInfo.add(activityMap);
                }
            }
        }
        else {
            return;
        }
        try {
            if (null != userInfo && 0 != userInfo.size()) {
                List<Map> tempList = new ArrayList<Map>();
                map.put("activityInfo", userInfo);
                tempList.add(map);
                msg.put("userInfo", tempList);
                body.put("msg", msg);
                exchange.getIn().setBody(body);
                new QryChkTermN6Processor().process(exchange);
                msg.put("resourcesInfo",
                        exchange.getOut().getBody(Map.class).get("resourcesRsp"));
                body.put("msg", msg);
                exchange.getIn().setBody(body);

            }
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "获取终端信息失败");
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
                item.put("startDate", msg.get("date"));
                item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                if (null != sp.get(i).get("activityStarTime")) {
                    item.put("startDate", sp.get(i).get("activityStarTime"));
                }
                if (null != sp.get(i).get("activityTime")) {
                    item.put("endDate", sp.get(i).get("activityTime"));
                }
                item.put("updateTime", msg.get("date"));
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
     * 用来拼装tradeProduct节点
     */
    public Map preProductData(Map msg) {
        try {
            Map tradeProduct = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            List<Map> product = (List<Map>) msg.get("productList");
            for (int i = 0; i < product.size(); i++) {
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("userId", msg.get("userId"));
                String productMode = (String) product.get(i).get("productMode");
                item.put("productMode", productMode);
                item.put("productId", product.get(i).get("productId"));
                item.put("brandCode", product.get(i).get("brandCode"));
                item.put("itemId", msg.get("itemId"));
                item.put("modifyTag", "0");
                item.put("startDate", msg.get("date"));
                item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                if (null != product.get(i).get("activityStarTime")) {
                    item.put("startDate", product.get(i).get("activityStarTime"));
                }
                if (null != product.get(i).get("activityTime")) {
                    item.put("endDate", product.get(i).get("activityTime"));
                }
                item.put("userIdA", MagicNumber.DEFAULT_NO_VALUE);
                itemList.add(item);
            }

            tradeProduct.put("item", itemList);
            return tradeProduct;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_PRODUCT节点报错" + e.getMessage());
        }
    }

    /**
     * 该方法用来处理终端 活动 还有终端费用项问题
     */
    private void preActivityInfo(Map msg) {
        try {
            String staffId = (String) msg.get("operatorId");
            String tradeId = (String) msg.get("tradeId");
            String eparchyCode = (String) msg.get("eparchyCode");
            String acceptDate = (String) msg.get("date");
            String resourcesCode;
            String resourceFee = "";
            String actProductId = "";
            String mainProduct = "";
            String packageId = "";
            String elementDiscntId = "";
            String resActivityper = (String) msg.get("resActivityper");
            String monthFirstDay = GetDateUtils.getNextMonthFirstDayFormat();
            String monthLasttDay = "20501231235959";
            String userId = (String) msg.get("userId");
            List<Map> tradeFeeItemList = new ArrayList<Map>();
            List<Map> tradeItem = new ArrayList<Map>();
            List<Map> subItemList = new ArrayList<Map>();
            List<Map> activityproductInfoResult = (List<Map>) msg.get("activityproductInfoResult");
            List<Map> product = (List<Map>) msg.get("discntList");
            monthFirstDay = (String) product.get(0).get("activityStarTime");
            monthLasttDay = (String) product.get(0).get("activityTime");
            List<Map> activityInfo = (List<Map>) msg.get("activityInfo");
            if (null != activityInfo && !activityInfo.isEmpty()) {
                resourceFee = activityInfo.get(0).get("resourcesFee") + "";
            }
            if (null != activityproductInfoResult && activityproductInfoResult.size() > 0) {
                for (int i = 0; i < activityproductInfoResult.size(); i++) {
                    if ("A".equals(activityproductInfoResult.get(i).get("ELEMENT_TYPE_CODE"))) {
                        packageId = String.valueOf(activityproductInfoResult.get(i).get("PACKAGE_ID"));
                        elementDiscntId = String.valueOf(activityproductInfoResult.get(i).get("ELEMENT_ID"));
                        actProductId = String.valueOf(activityproductInfoResult.get(i).get("PRODUCT_ID"));
                        if (!"0".equals(actProductId)) {
                            Map elemntItem1 = new HashMap();
                            elemntItem1.put("userId", userId);
                            elemntItem1.put("productId", actProductId);
                            elemntItem1.put("packageId", packageId);
                            elemntItem1.put("idType", "C");
                            elemntItem1.put("id", elementDiscntId);
                            elemntItem1.put("modifyTag", "0");
                            elemntItem1.put("startDate", monthFirstDay);
                            elemntItem1.put("endDate", monthLasttDay);
                            elemntItem1.put("modifyTag", "0");
                            elemntItem1
                                    .put("itemId", TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID"));
                            elemntItem1.put("userIdA", "-1");
                            elemntItem1.put("xDatatype", "NULL");
                            msg.put("elementMap", elemntItem1);

                            List<Map> resourcesInfo = (List<Map>) msg.get("resourcesInfo");
                            if (resourcesInfo != null && resourcesInfo.size() > 0) {
                                for (Map rescMap : resourcesInfo) {
                                    resourcesCode = (String) (rescMap.get("resourcesCode"));
                                    // resourceFee = (String) (rescMap.get("resourcesFee"));
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
                                    String purchaseItemId = TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID");
                                    subItemList.add(lan.createTradeSubItemH("deviceType",
                                            decodeTerminalType(terminalType), purchaseItemId));
                                    subItemList.add(lan
                                            .createTradeSubItemH("deviceno", machineTypeCode, purchaseItemId));
                                    subItemList.add(lan.createTradeSubItemH("devicebrand", orgdeviceBrandCode,
                                            purchaseItemId));
                                    subItemList.add(lan
                                            .createTradeSubItemH("deviceintag", resBrandName, purchaseItemId));
                                    subItemList.add(lan.createTradeSubItemH("mobilecost",
                                            String.valueOf(Integer.parseInt(cost) / 100), purchaseItemId));
                                    subItemList.add(lan.createTradeSubItemH("mobileinfo", machineTypeName,
                                            purchaseItemId));
                                    subItemList.add(lan.createTradeSubItemH("mobilesaleprice",
                                            String.valueOf(Integer.parseInt(salePrice) / 100), purchaseItemId));
                                    subItemList.add(lan.createTradeSubItemH("resourcesBrandCode", resBrandCode,
                                            purchaseItemId));
                                    subItemList.add(lan.createTradeSubItemH("resourcesBrandName", resBrandName,
                                            purchaseItemId));
                                    subItemList.add(lan.createTradeSubItemH("resourcesModelCode", resModelCode,
                                            purchaseItemId));
                                    subItemList.add(lan.createTradeSubItemH("resourcesModelName", resModeName,
                                            purchaseItemId));

                                    if (StringUtils.isNotEmpty(terminalSubtype)) // 有时为空
                                    {
                                        subItemList.add(lan.createTradeSubItemH("terminalTSubType", terminalSubtype,
                                                purchaseItemId));
                                    }
                                    subItemList.add(lan.createTradeSubItemH("terminalType", terminalType,
                                            purchaseItemId));
                                    subItemList.add(lan.createTradeSubItemH("isOwnerPhone", "0", purchaseItemId));
                                    subItemList.add(lan.createTradeSubItemH("isPartActive", "0", purchaseItemId));
                                    subItemList.add(lan.createTradeSubItemH("holdUnitType", "01", purchaseItemId));
                                    subItemList.add(lan.createTradeSubItemH("resourcesType", "07", purchaseItemId));
                                    subItemList.add(lan.createTradeSubItemH("packageType", "10", purchaseItemId));
                                    subItemList.add(lan.createTradeSubItemH("itemid", purchaseItemId, purchaseItemId));
                                    subItemList.add(lan.createTradeSubItemH("resActivityper", resActivityper,
                                            purchaseItemId));
                                    subItemList.add(lan.createTradeSubItemH("partActiveProduct",
                                            String.valueOf(msg.get("mainProduct")), purchaseItemId));

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
                                    tradePurchase.put("staffId", staffId);
                                    tradePurchase.put("departId", msg.get("channelId"));
                                    tradePurchase.put("startDate", monthFirstDay);
                                    tradePurchase.put("endDate", monthLasttDay);
                                    tradePurchase.put("remark", "");
                                    tradePurchase.put("itemId", purchaseItemId);
                                    tradePurchase.put("xDatatype", "NULL");
                                    msg.put("tradePurchase", tradePurchase);
                                    // 处理终端费用问题
                                    if (StringUtils.isNotEmpty(resourceFee) && !"0".equals(resourceFee)) {
                                        // 传入单位为厘
                                        int fee = Integer.parseInt(resourceFee);
                                        Map tradeFeeItem = new HashMap();
                                        tradeFeeItem.put("feeMode", "0");
                                        tradeFeeItem.put("feeTypeCode", "4310");
                                        tradeFeeItem.put("oldFee", String.valueOf(fee));
                                        tradeFeeItem.put("fee", String.valueOf(fee));
                                        tradeFeeItem.put("chargeSourceCode", "1");
                                        tradeFeeItem.put("apprStaffId", staffId);
                                        tradeFeeItem.put("calculateId", tradeId);
                                        tradeFeeItem.put("calculateDate", GetDateUtils.getDate());
                                        tradeFeeItem.put("staffId", staffId);
                                        tradeFeeItem.put("calculateTag", "0");
                                        tradeFeeItem.put("payTag", "0");
                                        tradeFeeItem.put("xDatatype", "NULL");
                                        tradeFeeItemList.add(tradeFeeItem);

                                    }
                                }
                            }
                        }
                    }
                }
            }
            msg.put("tradeItem", tradeItem);

            // 处理靓号信息
            Map niceinfo = (Map) msg.get("niceNumberInfo");
            String niceItemId = TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID");
            // 处理靓号信息
            if (niceinfo != null && niceinfo.size() > 0) {
                String feeTypeCode = "101117"; // 普号预存话费
                String endDate = "2050-12-31 23:59:59";
                String classId = (String) niceinfo.get("classId");
                String lowCostPro = (String) niceinfo.get("lowFee");
                String months = (String) niceinfo.get("protocolTime");
                //2017-3-9 生产靓号有效期为24时却下发120的BUG处理 by Zeng-start
                //不传默认为最长120个月？
                // 靓号有效期最多120个月
                try
                {
                    months = null == months || "".equals(months) || "null".equals(months) || "00000".equals(months)
                            || "9999".equals(months) ? "120" : months;
                    months = Integer.parseInt(months) > 120 ? "120" : months;
                }
                catch (Exception e)
                {
                    throw new EcAopServerBizException("9999", "协议时长timeDurPro校验时转换为Int异常：" + e.getMessage());
                }
                //2017-3-9 生产靓号有效期为24时却下发120的BUG处理 by Zeng-end
                endDate = GetDateUtils.getSpecifyDateTime(1, 2, GetDateUtils.getMonthLastDay(), Integer.valueOf(months));
                // 如果是靓号，费用项编码为123457
                if (StringUtils.isNotEmpty(classId) && !"9".equals(classId)) {
                    feeTypeCode = "123457";// 20170203 靓号资费修改
                }
                // 号码台账费用
                String numFee = (String) niceinfo.get("advancePay");
                if (StringUtils.isNotEmpty(numFee) && !"0".equals(numFee)) {
                    int fee = Integer.parseInt(numFee);// 传入单位为分
                    Map tradeFeeItem1 = new HashMap();
                    tradeFeeItem1.put("feeMode", "2");
                    tradeFeeItem1.put("feeTypeCode", feeTypeCode);
                    tradeFeeItem1.put("oldFee", String.valueOf(fee));
                    tradeFeeItem1.put("fee", String.valueOf(fee));
                    tradeFeeItem1.put("chargeSourceCode", "1");
                    tradeFeeItem1.put("apprStaffId", staffId);
                    tradeFeeItem1.put("calculateId", tradeId);
                    tradeFeeItem1.put("calculateDate", GetDateUtils.getNextMonthFirstDayFormat());
                    tradeFeeItem1.put("staffId", staffId);
                    tradeFeeItem1.put("calculateTag", "0");
                    tradeFeeItem1.put("payTag", "0");
                    tradeFeeItem1.put("xDatatype", "NULL");
                    tradeFeeItemList.add(tradeFeeItem1);

                }

                //
                if ("1|2|3|4|5".contains(classId) || "6".equals(classId)
                        && (StringUtils.isNotBlank(numFee) && !"0".equals(numFee)
                        || StringUtils.isNotBlank(lowCostPro) && !"0".equals(lowCostPro)
                        || StringUtils.isNotBlank(months) && !"0".equals(months))) {
                    // 如果是靓号，需要传88888888 资费

                    Map discntItem = new HashMap();
                    discntItem.put("id", userId);
                    discntItem.put("idType", "1");
                    discntItem.put("productId", "-1");
                    discntItem.put("packageId", "-1");
                    discntItem.put("discntCode", "88888888");
                    discntItem.put("specTag", "0");
                    discntItem.put("relationTypeCode", "");
                    discntItem.put("itemId", niceItemId);
                    discntItem.put("modifyTag", "0");
                    discntItem.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                    discntItem.put("endDate", endDate);
                    discntItem.put("userIdA", "-1");
                    discntItem.put("xDatatype", "NULL");
                    msg.put("numberDiscnt", discntItem);

                    // 低消
                    if (StringUtils.isNotEmpty(lowCostPro) && !"0".equals(lowCostPro)) {
                        Map item = new HashMap();
                        item.put("attrTypeCode", "3");
                        item.put("attrCode", "lowCost");
                        item.put("itemId", niceItemId);
                        item.put("attrValue", Integer.parseInt(lowCostPro) / 10);
                        item.put("xDatatype", "NULL");
                        item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                        item.put("endDate", endDate);
                        subItemList.add(item);
                    }
                    // 在网时长
                    if (StringUtils.isNotBlank(months)) {
                        Map item = new HashMap();
                        item.put("attrTypeCode", "3");
                        item.put("attrCode", "leaseLength");
                        item.put("itemId", niceItemId);
                        item.put("attrValue", months);
                        item.put("xDatatype", "NULL");
                        item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                        item.put("endDate", endDate);
                        subItemList.add(item);
                    }
                    // 靓号号码标识,值为1（默认值） --标识是新靓号
                    Map item = new HashMap();
                    item.put("attrTypeCode", "3");
                    item.put("attrCode", "NewNumTag");
                    item.put("itemId", niceItemId);
                    item.put("attrValue", "1");
                    item.put("xDatatype", "NULL");
                    item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                    item.put("endDate", endDate);
                    subItemList.add(item);
                }

            }
            if (subItemList != null && subItemList.size() > 0) {
                msg.put("subItemList", subItemList);
            }
            if (tradeFeeItemList != null && tradeFeeItemList.size() > 0) {
                msg.put("tradeFeeItemList", tradeFeeItemList);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "处理终端活动靓号信息报错" + e.getMessage());
        }
    }

    /**
     * 拼装TRADE_PURCHASE
     * 此节点下面存放终端信息
     */

    private Map preTradePurchase(Map msg) {
        try {
            Map tradePurchaseMap = (Map) msg.get("tradePurchase");
            Map tradePurchase = new HashMap();
            if (tradePurchaseMap != null && tradePurchaseMap.size() > 0) {
                Map item = new HashMap();
                item.putAll(tradePurchaseMap);
                tradePurchase.put("item", item);
            }
            return tradePurchase;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_PURCHASE节点报错" + e.getMessage());
        }
    }

    /**
     * 拼装TRADE_PRODUCT_TYPE节点
     */
    public Map preProductTpyeListData(Map msg) {
        try {
            Map tradeProductType = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            List<Map> productTpye = (List<Map>) msg.get("productTypeList");
            for (int i = 0; i < productTpye.size(); i++) {
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("userId", msg.get("userId"));
                String productMode = (String) productTpye.get(i).get("productMode");
                item.put("productMode", productMode);
                item.put("productId", productTpye.get(i).get("productId"));
                item.put("productTypeCode", productTpye.get(i).get("productTypeCode"));
                item.put("modifyTag", "0");
                item.put("startDate", msg.get("date"));
                item.put("endDate", "20501231235959");
                if (null != productTpye.get(i).get("activityStarTime")) {
                    item.put("startDate", productTpye.get(i).get("activityStarTime"));
                }
                if (null != productTpye.get(i).get("activityTime")) {
                    item.put("endDate", productTpye.get(i).get("activityTime"));
                }
                itemList.add(item);
            }
            tradeProductType.put("item", itemList);
            return tradeProductType;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_PRODUCT_TYPE节点报错" + e.getMessage());
        }
    }

    /**
     * 拼装TRADE_SVC节点
     */
    public Map preTradeSvcData(Map msg) {
        try {
            Map svcList = new HashMap();
            List<Map> svc = new ArrayList<Map>();
            svc = (List<Map>) msg.get("svcList");
            List svList = new ArrayList();
            for (int i = 0; i < svc.size(); i++) {
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("userId", msg.get("userId"));
                item.put("serviceId", svc.get(i).get("serviceId"));
                item.put("modifyTag", "0");
                item.put("startDate", msg.get("date"));
                item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                if (null != svc.get(i).get("activityStarTime")) {
                    item.put("startDate", svc.get(i).get("activityStarTime"));
                }
                if (null != svc.get(i).get("activityTime")) {
                    item.put("endDate", svc.get(i).get("activityTime"));
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

    /**
     * 拼装tradediscnt节点
     */
    public Map preDiscntData(Map msg, Exchange exchange) {
        try {
            Map tradeDis = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            List<Map> discnt = (List<Map>) msg.get("discntList");
            for (int i = 0; i < discnt.size(); i++) {
                String packageId = String.valueOf(discnt.get(i).get("packageId"));
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("id", msg.get("userId"));
                item.put("idType", "1");
                item.put("userIdA", "-1");
                item.put("productId", discnt.get(i).get("productId"));
                item.put("packageId", packageId);
                item.put("discntCode", discnt.get(i).get("discntCode"));
                item.put("specTag", "1");//
                // CHANGE_1 北分系统做业务时,该字段下发0 特殊优惠标记：0-正常产品优惠，1-特殊优惠，2-关联优惠。 by wangmc 20180706
                if ("mabc|bjaop".contains(exchange.getAppCode())) {
                    item.put("specTag", "0");
                }
                item.put("relationTypeCode", "");
                item.put("startDate", msg.get("date"));
                item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                if (null != discnt.get(i).get("activityStarTime")) {
                    item.put("startDate", discnt.get(i).get("activityStarTime"));
                }
                if (null != discnt.get(i).get("activityTime")) {
                    item.put("endDate", discnt.get(i).get("activityTime"));
                }
                item.put("modifyTag", "0");
                item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
                itemList.add(item);

            }
            Map numberDiscnt = (Map) msg.get("numberDiscnt");
            if (null != numberDiscnt && !numberDiscnt.isEmpty()) {
                Map item = new HashMap();
                item.putAll(numberDiscnt);
                itemList.add(item);
            }
            Map numberDis = (Map) msg.get("numberDis");
            if (null != numberDis && !numberDis.isEmpty()) {
                Map item1 = new HashMap();
                item1.putAll(numberDiscnt);
                itemList.add(item1);

            }

            tradeDis.put("item", itemList);
            return tradeDis;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_DISCNT节点报错" + e.getMessage());
        }
    }

    // 拼装TRADE_PAYRELATION
    private Map preTradePayRelData(Map msg) {
        try {
            Map tradePayRel = new HashMap();
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", msg.get("userId"));
            item.put("acctId", msg.get("acctId"));
            item.put("payitemCode", "-1");
            item.put("acctPriority", "0");
            item.put("userPriority", "0");
            item.put("bindType", "1");
            item.put("startAcycId", msg.get("cycId"));
            item.put("endAcycId", "203712");
            item.put("defaultTag", "1");
            item.put("limitType", "0");
            item.put("limit", "0");
            item.put("complementTag", "0");
            item.put("addupMonths", "0");
            item.put("addupMethod", "0");
            item.put("payrelationId", msg.get("payRelId"));
            item.put("actTag", "1");
            tradePayRel.put("item", item);
            return tradePayRel;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_PAYRELATION节点报错" + e.getMessage());
        }
    }

    // 拼装TRADE_RELATION
    private Map preDataForTradeRelation(Map msg) {
        try {
            Map tradeRelation = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            Map item = new HashMap();
            // CHANGE_3 北分系统放入关系类型字段 默认为0 by wangmc 20180760 关系属性：0－用户和用户 1-客户和客户 2-客户和用户
            if ("mabc|bjaop".contains(String.valueOf(msg.get("appCode")))) {
                item.put("relationAttr", "0");
            }
            // 主卡开户流程准备参数
            if ("0".equals(msg.get("mainNumberTag"))) {
                item.put("idA", msg.get("userId"));
                item.put("serialNumberA", msg.get("serialNumber"));
                item.put("roleCodeB", "1");
            }
            else {
                // 副卡开户流程准备参数
                item.put("idA", msg.get("idA"));
                item.put("serialNumberA", msg.get("mainNumber"));
                //写死只传0 by maly  171117
                item.put("roleCodeB", "0");
            }
            item.put("xDatatype", "NULL");
            item.put("relationTypeCode", "ZF");
            item.put("serialNumberB", msg.get("serialNumber"));
            item.put("idB", msg.get("userId"));
            item.put("roleCodeA", "0");
            item.put("modifyTag", "0");
            item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
            item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            itemList.add(item);
            tradeRelation.put("item", itemList);
            return tradeRelation;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_RELATION节点报错" + e.getMessage());
        }

    }

    // 拼装TRADE_SUB_ITEM
    private Map preDataForTradeSubItem(Map msg) {
        try {
            List<Map> Item = new ArrayList<Map>();
            Map tradeSubItem = new HashMap();
            Map tradeid = new HashMap();
            String itemId = (String) msg.get("userId");
            tradeid.put("startDate", msg.get("date"));
            tradeid.put("attrValue", msg.get("tradeId"));
            tradeid.put("attrCode", "tradeId");
            tradeid.put("attrTypeCode", "3");
            tradeid.put("itemId", itemId);
            tradeid.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            Item.add(tradeid);

            Map netCode = new HashMap();
            netCode.put("attrValue", "50");
            netCode.put("attrCode", "NET_CODE");
            netCode.put("attrTypeCode", "0");
            netCode.put("itemId", itemId);
            Item.add(netCode);

            Map userCall = new HashMap();
            userCall.put("attrValue", msg.get("channelId"));
            userCall.put("attrCode", "USER_CALLING_AREA");
            userCall.put("attrTypeCode", "0");
            userCall.put("itemId", itemId);
            Item.add(userCall);

            Map initPasswd = new HashMap();
            initPasswd.put("attrValue", "0");
            initPasswd.put("attrCode", "INIT_PASSWD");
            initPasswd.put("attrTypeCode", "0");
            initPasswd.put("itemId", itemId);
            Item.add(initPasswd);

            Map userPasswd = new HashMap();
            userPasswd.put("attrValue", msg.get("serialNumber").toString().substring(5, 11));
            userPasswd.put("attrCode", "USER_PASSWD");
            userPasswd.put("attrTypeCode", "0");
            userPasswd.put("itemId", itemId);
            Item.add(userPasswd);

            if ("0".equals(msg.get("deductionTag"))) {
                List<Map> activityInfo = (List<Map>) msg.get("activityInfo");
                if (!IsEmptyUtils.isEmpty(activityInfo)
                        && !IsEmptyUtils.isEmpty(activityInfo.get(0).get("resourcesCode"))) {
                    Map purchaseFeeType = new HashMap();
                    purchaseFeeType.put("startDate", msg.get("date"));
                    purchaseFeeType.put("attrValue", "1");
                    purchaseFeeType.put("attrCode", "PURCHASE_FEE_TYPE");
                    purchaseFeeType.put("attrTypeCode", "6");
                    purchaseFeeType.put("itemId", itemId);
                    Item.add(purchaseFeeType);
                }
            }

            Map mainCardTag = new HashMap();
            // 主卡开户流程准备参数
            if ("0".equals(msg.get("mainNumberTag"))) {
                mainCardTag.put("startDate", msg.get("date"));
                mainCardTag.put("attrValue", "0");
                mainCardTag.put("attrCode", "MAIN_CARD_TAG");
                mainCardTag.put("attrTypeCode", "0");
                mainCardTag.put("itemId", itemId);
                Item.add(mainCardTag);
            }
            else {
                mainCardTag.put("startDate", msg.get("date"));
                mainCardTag.put("attrValue", "0");
                mainCardTag.put("attrCode", "MAINDEPUTY_CARD_DATE");
                mainCardTag.put("attrTypeCode", "0");
                mainCardTag.put("itemId", itemId);
                Item.add(mainCardTag);

                Map viceCardTag = new HashMap();
                viceCardTag.put("startDate", msg.get("date"));
                viceCardTag.put("attrValue", "0");
                viceCardTag.put("attrCode", "DEPUTY_CARD_TAG");
                viceCardTag.put("attrTypeCode", "0");
                viceCardTag.put("itemId", itemId);
                Item.add(viceCardTag);
            }
            if (null != msg.get("subItemList")) {
                Item.addAll((List<Map>) msg.get("subItemList"));
            }
            Item.add(lan.createTradeSubItemE("LOCAL_NET_CODE", msg.get("eparchyCode"), itemId));
            Item.add(lan.createTradeSubItemE("BUSI_CODE", msg.get("eparchyCode"), itemId));
            Item.add(lan.createTradeSubItemE("CUSTOMER_GROUP", "0", itemId));
            Item.add(lan.createTradeSubItemE("groupFlag", "0", itemId));
            // 判断是否对接号卡中心，调cb2.0
            String numSwitch = (String) msg.get("numSwitch");
            if ("0".equals(numSwitch)) {
                Item.add(lan.createTradeSubItemE("NUMERICAL_SELECTION", "2", itemId));
            }
            Map recomInfo = (Map) msg.get("recomInfo");
            if (null != recomInfo && recomInfo.size() > 0) {
                Item.add(lan.createTradeSubItemE("RECOMMENDED_PERSON_ID", msg.get("recomPersonId"), itemId));
                Item.add(lan.createTradeSubItemE("RECOMMENDED_PERSON", msg.get("recomPersonName"), itemId));
            }
            // 新增非必传项realPersonTag,实人认证标识
            String realPersonTag = (String) msg.get("realPersonTag");
            if (StringUtils.isNotEmpty(realPersonTag)) {
                Map custInfo = (Map) msg.get("custInfo");
                String certType = (String) custInfo.get("certType");
                if ("07,13,14,15,17,18,21,20,33".contains(certType)) {
                    Item.add(LanUtils.createTradeSubItem4("REAL_PERSON_TAG", "0", realPersonTag, (String) msg.get("userId")));
                } else {
                    Item.add(LanUtils.createTradeSubItem4("REAL_PERSON_TAG", "4", realPersonTag, (String) msg.get("userId")));
                    Item.add(LanUtils.createTradeSubItem4("REAL_PERSON_TAG", "0", realPersonTag, (String) msg.get("userId")));
                }
            }
            Map cardInfo = (Map) msg.get("cardInfo");
            if(!IsEmptyUtils.isEmpty(cardInfo)){
                Map item = new HashMap();
                item.put("attrValue", cardInfo.get("KI"));
                item.put("attrCode", "NEW_CARD_DATA");
                item.put("attrTypeCode", "0");
                item.put("itemId", (String) msg.get("userId"));
                Item.add(item);
            }
            tradeSubItem.put("item", Item);
            return tradeSubItem;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_SUB_ITEM节点报错" + e.getMessage());
        }
    }

    // 拼装TRADE_ITEM
    private Map preDataForTradeItem(Map msg, String appCode) {
        try {
            List<Map> Item = new ArrayList<Map>();
            Map tradeItem = new HashMap();
            Map recomInfo = (Map) msg.get("recomInfo");//发展人属性下发
            if (null != recomInfo && !recomInfo.isEmpty()) {
                String recomDepartId = (String) recomInfo.get("recomDepartId");
                Map developStaff = new HashMap();
                developStaff.put("xDatatype", "NULL");
                developStaff.put("attrValue", recomInfo.get("recomPersonId"));
                developStaff.put("attrCode", "DEVELOP_STAFF_ID");
                Item.add(developStaff);
                if (StringUtils.isNotEmpty(recomDepartId)) {
                    Map developDepart = new HashMap();
                    developDepart.put("xDatatype", "NULL");
                    developDepart.put("attrValue", recomDepartId);
                    developDepart.put("attrCode", "DEVELOP_DEPART_ID");
                    Item.add(developDepart);
                }
            }
            if (new ChangeCodeUtils().isWOPre(appCode)) {
                Map workTradeItem = new HashMap();
                workTradeItem.put("attrValue", "WORK_TRADE_ID");
                workTradeItem.put("attrCode", msg.get("ordersId"));
                Item.add(workTradeItem);
            }
            Map userCall = new HashMap();
            userCall.put("attrValue", msg.get("nbrSrc"));
            userCall.put("attrCode", "nbrSrc");
            Item.add(userCall);

            Map numberType = new HashMap();
            numberType.put("attrValue", msg.get("serialNumber").toString().substring(0, 3));
            numberType.put("attrCode", "NumberType");
            Item.add(numberType);

            Map numThawPro = new HashMap();
            numThawPro.put("attrValue", "0");
            numThawPro.put("attrCode", "NumThawPro");
            Item.add(numThawPro);

            Map userPasswd = new HashMap();
            userPasswd.put("attrValue", msg.get("serialNumber").toString().substring(5, 11));
            userPasswd.put("attrCode", "USER_PASSWD");
            userPasswd.put("xDatatype", "NULL");
            Item.add(userPasswd);

            Map newPasswd = new HashMap();
            newPasswd.put("attrValue", msg.get("serialNumber").toString().substring(5, 11));
            newPasswd.put("attrCode", "NEW_PASSWD");
            newPasswd.put("xDatatype", "NULL");
            Item.add(newPasswd);

            Map reopenTag = new HashMap();
            reopenTag.put("attrValue", "2");
            reopenTag.put("attrCode", "REOPEN_TAG");
            reopenTag.put("xDatatype", "NULL");
            Item.add(reopenTag);

            if ("0".equals(msg.get("deductionTag"))) {
                Map feeType = new HashMap();
                feeType.put("attrValue", "1");
                feeType.put("attrCode", "FEE_TYPE");
                feeType.put("xDatatype", "NULL");
                Item.add(feeType);
            }

            Map standardKindCode = new HashMap();
            standardKindCode.put("attrValue", "0371");
            standardKindCode.put("attrCode", "STANDARD_KIND_CODE");
            standardKindCode.put("xDatatype", "NULL");
            Item.add(standardKindCode);

            Map mainCardTag = new HashMap();
            Map viceCardTag = new HashMap();
            // 主卡开户流程准备参数
            if ("0".equals(msg.get("mainNumberTag"))) {
                viceCardTag.put("attrValue", "0");
                mainCardTag.put("attrCode", "MAIN_CARD_TAG");
            }
            else {
                viceCardTag.put("attrValue", "1");
                mainCardTag.put("attrCode", "DEPUTY_CARD_TAG");
            }
            mainCardTag.put("attrValue", "0");
            Item.add(mainCardTag);

            viceCardTag.put("attrCode", "EXISTS_ACCT");
            viceCardTag.put("xDatatype", "NULL");
            Item.add(viceCardTag);
            String delayTag = (String) msg.get("delayTag");
            if (StringUtils.isNotEmpty(delayTag) && "1".equals(delayTag)) {
                Item.add(LanUtils.createTradeItem("E_DELAY_TIME_CEL", "1"));
            }
            if (null != msg.get("msgTradeItem")) {// 字段重复，会覆盖，用这个替代下
                Item.addAll((List<Map>) msg.get("msgTradeItem"));
            }

            if ("X".equals(msg.get("inModeCode"))) {
                Item.add(LanUtils.createTradeItem("WORK_TRADE_ID", msg.get("tradeId").toString()));
            }

            //黑龙江行销APP支撑冰激凌融合及主副卡需求 by maly--start
            String markingTag = (String) msg.get("markingTag");
            if (StringUtils.isNotEmpty(markingTag) && "1".equals(markingTag)) {
                Item.add(LanUtils.createTradeItem("MARKING_APP", "1"));
            }

            String saleModType = (String) msg.get("saleModType");
            if (StringUtils.isNotEmpty(saleModType)) {//销售模式类型
                String marketingChannelType = "0".equals(saleModType) ? "01" : "02";
                Item.add(LanUtils.createTradeItem("MarketingChannelType", marketingChannelType));
            }
            //黑龙江行销APP支撑冰激凌融合及主副卡需求 by maly--end

            // 实人认证新增非必传项photoTag
            String photoTag = (String) msg.get("photoTag");
            if (StringUtils.isNotEmpty(photoTag)) {
                Item.add(LanUtils.createTradeItem("PHOTO_TAG", photoTag));
            }
            List<Map> activityInfo = (List<Map>) msg.get("activityInfo");
            if (!IsEmptyUtils.isEmpty(activityInfo)) {
                for (Map activity : activityInfo) {
                    if ("0".equals(activity.get("isTest"))) {
                        Item.add(LanUtils.createTradeItem("GDTEST", "1"));
                    }
            }
            }
            if ("1".equals(msg.get("isAfterActivation"))) {
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("attrCode", "IS_AFTER_ACTIVATION");
                item.put("attrValue", "1");
                Item.add(item);
            }
            if("1".equals(msg.get("IS_REMOTE"))){
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("attrCode", "NOT_REMOTE");
                item.put("attrValue", "1");
                Item.add(item);
            }
            List<Map> actorInfoList = (List) msg.get("actorInfo");
            Map actorInfo = null;
            if (actorInfoList != null && actorInfoList.size() > 0) {
                actorInfo = actorInfoList.get(0);
                Item.add(LanUtils.createTradeItem("ACTOR_ADDRESS", actorInfo.get("actorAddress")));
            }
            tradeItem.put("item", Item);
            return tradeItem;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_ITEM节点报错" + e.getMessage());
        }
    }

    // 拼装TRADE_USER
    private Map preDataForTradeUser(Map msg) {
        try {
            Map tradeUser = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", msg.get("userId"));
            item.put("custId", msg.get("custId"));
            item.put("usecustId", msg.get("custId"));
            // item.put("brandCode", msg.get("mBrandCode"));//
            item.put("productId", msg.get("mainProduct"));//
            item.put("eparchyCode", msg.get("eparchyCode"));
            item.put("cityCode", msg.get("district"));
            // 活动类型
            item.put("productTypeCode", msg.get("mainProductTypeCode"));
            item.put("userPasswd",
                    msg.get("serialNumber").toString().substring(msg.get("serialNumber").toString().length() - 6));
            item.put("userTypeCode", "0");
            item.put("serialNumber", msg.get("serialNumber"));
            item.put("netTypeCode", "50");
            item.put("scoreValue", "0");
            item.put("creditClass", "0");
            item.put("basicCreditValue", "0");
            item.put("creditValue", "0");
            item.put("acctTag", "0");
            item.put("prepayTag", "0");//
            item.put("inDate", msg.get("date"));
            item.put("openDate", msg.get("date"));
            item.put("openMode", "0");
            item.put("openDepartId", msg.get("channelId"));
            item.put("openStaffId", msg.get("operatorId"));
            item.put("inDepartId", msg.get("channelId"));
            item.put("inStaffId", msg.get("operatorId"));
            item.put("removeTag", "0");
            item.put("userStateCodeset", "0");
            item.put("userDiffCode", "00");
            item.put("mputeMonthFee", "0");
            Map recomInfo = (Map) msg.get("recomInfo");
            if (null != recomInfo) {
                item.put("developDate", GetDateUtils.getDate());
                item.put("developStaffId", recomInfo.get("recomPersonId"));
                item.put("developDepartId", recomInfo.get("recomDepartId"));
                // CHANGE_4 给北分新增 这个不加分支了 by wangmc 20180717
                item.put("developEparchyCode", ChangeCodeUtils.changeCityCode(msg));
                item.put("developCityCode", recomInfo.get("recomCity"));
            }
            item.put("inNetMode", "0");
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
     * 拼装TRADE_ACCT
     */
    private Map preDataForTradeAcct(Map msg) {
        try {
            Map item = new HashMap();
            Map tradeAcct = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("acctId", msg.get("acctId"));
            item.put("eparchyCode", msg.get("eparchyCode"));
            item.put("cityCode", msg.get("district"));
            item.put("custId", msg.get("custId"));
            item.put("payName", msg.get("customerName"));
            item.put("payModeCode", getPayModeCode(msg));
            item.put("scoreValue", "0");
            item.put("creditClassId", "0");
            item.put("basicCreditValue", "0");
            item.put("creditValue", "0");
            item.put("creditControlId", "0");
            item.put("removeTag", "0");
            item.put("debutyUserId", msg.get("userId"));
            item.put("debutyCode", msg.get("serialNumber"));
            // item.put("removeDate", GetDateUtils.getDate());
            item.put("acctPasswd", "0");
            item.put("contractNo", "0");
            item.put("openDate", msg.get("date"));
            tradeAcct.put("item", item);
            return tradeAcct;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_ACCT节点报错" + e.getMessage());
        }
    }

    /**
     * 拼装 TRADEFEE_SUB
     * 此节点下面存放各种费用项信息
     * 包括终端还有靓号费用
     */
    private Map preTradeFeeSub(Map msg) {
        try {
            List<Map> tradeFeeItemList = (List<Map>) msg.get("tradeFeeItemList");
            Map tradeFeeSubList = new HashMap();
            List tradeFeeList = new ArrayList();
            if (tradeFeeItemList != null && tradeFeeItemList.size() > 0) {
                for (int i = 0; i < tradeFeeItemList.size(); i++) {
                    Map tradeFeeMap = tradeFeeItemList.get(i);
                    Map item = new HashMap();
                    item.putAll(tradeFeeMap);
                    tradeFeeList.add(item);
                }
                tradeFeeSubList.put("item", tradeFeeList);
            }
            return tradeFeeSubList;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADEFEE_SUB节点报错" + e.getMessage());
        }
    }

    // 获取账户付费方式编码
    private String getPayModeCode(Map msg) {
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        Map param = new HashMap();
        param.put("EPARCHY_CODE", msg.get("eparchyCode"));
        param.put("PARAM_CODE", msg.get("accountPayType"));
        param.put("PROVINCE_CODE", msg.get("provinceCode"));
        List<Map> payModeCoderesult = dao.queryPayModeCode(param);
        if (payModeCoderesult.size() > 0) {
            return payModeCoderesult.get(0).get("PARA_CODE1").toString();
        }
        return "0";
    }

    /**
     * 拼装TRADE_RES
     * 存放号码和卡信息
     */
    private Map preDataForTradeRes(Map msg) {
        try {
            Map tradeRes = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("reTypeCode", "0");
            item.put("resCode", msg.get("serialNumber"));
            item.put("modifyTag", "0");
            item.put("startDate", msg.get("date"));
            item.put("endDate", "20501231235959");
            itemList.add(item);
            String simId = (String) msg.get("simId");
            if (StringUtils.isNotEmpty(simId)) {
                Map cardInfo = (Map) msg.get("cardInfo");
                Map item2 = new HashMap();
                item2.put("xDatatype", "NULL");
                item2.put("modifyTag", "0");
                item2.put("reTypeCode", "1");
                item2.put("resCode", simId);
                item2.put("resInfo1", cardInfo.get("IMSI"));
                item2.put("resInfo2", "notRemote");
                item2.put("resInfo4", "1000101");
                item2.put("resInfo5", ChangeCodeUtils.changeCardType(cardInfo.get("MATERIAL_CODE")));// :号卡中心卡类型转换
                item2.put("resInfo7", "");
                item2.put("resInfo8", "");// PIN2-PUK2
                item2.put("resInfo6", "1234-12345678");// CB说写死
                item2.put("startDate", GetDateUtils.getDate());
                item2.put("endDate", "20501231235959");
                itemList.add(item2);
            }
            else {
                Map item1 = new HashMap();
                item1.put("xDatatype", "NULL");
                item1.put("reTypeCode", "1");
                item1.put("resCode", "89860");
                item1.put("modifyTag", "0");
                item1.put("startDate", msg.get("date"));
                item1.put("endDate", "20501231235959");
                itemList.add(item1);
            }
            tradeRes.put("item", itemList);
            return tradeRes;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_RES节点报错" + e.getMessage());
        }
    }

    private Map preCustomerData(Map msg) {
        try {
            Map tradeCustomer = new HashMap();
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("sustId", msg.get("custId"));
            item.put("custName", msg.get("custName"));
            item.put("custType", "0");
            item.put("custState", "0");
            if ("0".equals(msg.get("mainNumberTag"))) {
                item.put("psptTypeCode", CertTypeChangeUtils.certTypeMall2Cbss((String) msg.get("certType")));
            }
            else {
                item.put("psptTypeCode", msg.get("certType"));
            }
            item.put("psptId", msg.get("certNum"));
            item.put("openLimit", "0");
            item.put("eparchyCode", msg.get("eparchyCode"));
            item.put("cityCode", msg.get("district"));
            item.put("developDepartId", msg.get("recomDepartId"));
            item.put("inDate", msg.get("date"));
            item.put("removeTag", "0");
            item.put("rsrvTag1", decodeCheckType4G(msg.get("checkType")));
            tradeCustomer.put("item", item);
            return tradeCustomer;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_CUSTOMER节点报错" + e.getMessage());
        }
    }

    // 转换cbcheckType
    public String decodeCheckType4G(Object checkType) {
        if ("01".equals(checkType) || "02".equals(checkType)) {
            return "3";
        }
        return "03".equals(checkType) ? "4" : "2";
    }

    private Map preCustPerData(Map msg) {
        try {
            Map tradeCustPerson = new HashMap();
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("custId", msg.get("custId"));
            if ("0".equals(msg.get("mainNumberTag"))) {
                item.put("psptTypeCode", CertTypeChangeUtils.certTypeMall2Cbss((String) msg.get("certType")));
            }
            else {
                item.put("psptTypeCode", msg.get("certType"));
            }
            item.put("psptId", msg.get("certNum"));
            item.put("psptEndDate", msg.get("certExpireDate"));
            item.put("psptAddr", msg.get("certAdress"));
            item.put("custName", msg.get("custName"));
            item.put("sex", msg.get("sex"));
            item.put("eparchyCode", msg.get("eparchyCode"));
            item.put("cityCode", msg.get("district"));
            item.put("postAddress", msg.get("contactAddress"));
            item.put("phone", msg.get("contactPhone"));
            item.put("contact", msg.get("contactPerson"));
            item.put("contactPhone", msg.get("contactPhone"));
            item.put("removeTag", "0");
            tradeCustPerson.put("item", item);
            return tradeCustPerson;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_CUST_PERSON节点报错" + e.getMessage());
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
            base.put("tradeTypeCode", "0010");
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
            base.put("startAcycId", RDate.currentTimeStr("yyyyMM"));
            base.put("acceptMonth", RDate.currentTimeStr("MM"));
            base.put("netTypeCode", "50");
            base.put("advancePay", "0");
            String inModeCode = (String) new ChangeCodeUtils().getInModeCode(appCode);
            base.put("inModeCode", inModeCode);
            // 预受理系统要在tradeitem节点下下发WORK_TRADE_ID，所以放到msg里面用来判断
            msg.put("inModeCode", inModeCode);
            base.put("custId", msg.get("custId"));
            base.put("custName", msg.get("customerName"));
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
            base.put("remark", new PreDataProcessor().getRemark(msg));
            // 针对关于AOP主副卡接口支持备注字段录入功能需求修改remark字段
            String oldRemark = (String) msg.get("oldRemark");
            if (StringUtils.isNotEmpty(oldRemark)) {
                base.put("remark", oldRemark);
            }
            // 处理大王卡超级会员 create by zhaok Date 18/1/22
            String kingVipMonth = getFromPara(msg, "NEWKSVIP");
            if (!"".equals(kingVipMonth)) {
                base.put("auditTag", kingVipMonth);
            }
            List<Map> actorInfos = (List<Map>) msg.get("actorInfo");
            if (!IsEmptyUtils.isEmpty(actorInfos)) {
                Map actorInfoMap = actorInfos.get(0);
                base.put("actorName", actorInfoMap.get("actorName"));
                String actorCertTypeId = (String) actorInfoMap.get("actorCertTypeId");
                if (IsEmptyUtils.isEmpty(actorCertTypeId)) {
                    throw new EcAopServerBizException("9999", "代办人证件类型不能为空！");
                }
                base.put("actorCertTypeId", CertTypeChangeUtils.certTypeMall2Cbss(actorCertTypeId));
                base.put("actorPhone", actorInfoMap.get("actorPhone"));
                base.put("actorCertNum", actorInfoMap.get("actorCertNum"));
            }
            return base;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装base节点报错");
        }
    }

    /**
     * 调用三户接口查询三户信息
     * 针对主副卡开户分开处理参数
     * 三户接口返回和请求参数字段名称不一致
     * 
     * @throws Exception
     */
    private void threepartCheck(Exchange exchange, Map body, Map msg) throws Exception {
        String[] copyArray = { "operatorId", "province", "channelId", "city", "district", "channelType" };
        Map preSubmitMap = Maps.newHashMap();
        MapUtils.arrayPut(preSubmitMap, msg, copyArray);
        // Exchange tradeEchange = ExchangeUtils.ofCopy(exchange, preSubmitMap);
        msg.put("userId",
                GetSeqUtil.getSeqFromCb(pmp[1], ExchangeUtils.ofCopy(exchange, preSubmitMap), "seq_user_id", 1).get(0));
        // 主卡开户流程准备参数
        if ("0".equals(msg.get("mainNumberTag"))) {
            // Map body1 = (Map) tradeEchange.getIn().getBody();

            msg.put("acctId",
                    GetSeqUtil.getSeqFromCb(pmp[1], ExchangeUtils.ofCopy(exchange, preSubmitMap), "seq_acct_id", 1)
                            .get(0));
            Map custInfo = (Map) msg.get("custInfo");
            // 什么亲情卡的测试,非要让少一个....by wangmc 20180427
            if (9 > custInfo.size()) {
                throw new EcAopServerBizException("9999", "主卡开户客户信息必须填全");
            }
            iteraterMap(msg, custInfo);
            // RSD2018040400074如果外围传入了custId就用外围传入的，针对山东改造---主卡
            String custType = getFromPara(msg, "custType");
            if ("1".equals(custType)) {// 开放所有省份，只要是老客户、传了custId就不再新生成了
                String sdcustId = (String) (StringUtils.isEmpty((String) custInfo.get("custId"))
                        ? GetSeqUtil.getSeqFromCb(pmp[1], ExchangeUtils.ofCopy(exchange, preSubmitMap), "seq_cust_id", 1).get(0)
                        : (String) custInfo.get("custId"));
                msg.put("custId", sdcustId);
            } else {
                msg.put("custId", GetSeqUtil.getSeqFromCb(pmp[1], ExchangeUtils.ofCopy(exchange, preSubmitMap), "seq_cust_id", 1).get(0));
            }
            msg.put("custName", msg.get("customerName"));
        }
        // 副卡开户准备参数
        else if ("1".equals(msg.get("mainNumberTag"))) {
            String custType = getFromPara(msg, "custType");
            boolean flag = "1".equals(custType);
            String serialNumber = (String) msg.get("serialNumber");
            String custId = "";
            msg.put("tradeTypeCode", "9999");
            msg.put("getMode", "1111111111100013111100000100001");
            // msg.put("serviceClassCode", "00CP");
            msg.put("serialNumber", msg.get("mainNumber"));
            List<Map> simCardNoList = (List<Map>) msg.get("simCardNo");
            if (!IsEmptyUtils.isEmpty(simCardNoList)) {
                msg.put("simCardNo", simCardNoList.get(0).get("simId"));// 这个接口的规范里这个字段和三户请求报文中映射名称冲突了，故调完三户再塞回去2018-07-04
            }
            body.put("msg", msg);
            exchange.getIn().setBody(body);
            try {
                new LanUtils().preData(pmp[0], exchange);
                CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
                new LanUtils().xml2Json("ecaop.trades.cbss.threePart.template", exchange);
                Map checkUserMap = exchange.getOut().getBody(Map.class);
                msg.put("simCardNo", simCardNoList);// 调完三户再塞回去2018-07-04
                List<Map> custInfo = (List<Map>) checkUserMap.get("custInfo");
                if (null == custInfo || 0 == custInfo.size()) {
                    throw new EcAopServerBizException("9999", "客户信息未返回");
                }
                transMap(msg, custInfo.get(0));
                List<Map> userInfo = (List<Map>) checkUserMap.get("userInfo");
                if (null == userInfo || 0 == userInfo.size()) {
                    throw new EcAopServerBizException("9999", "用户信息未返回");
                }
                List<Map> acctInfo = (List<Map>) msg.get("acctInfo");
                if (null == acctInfo || 0 == acctInfo.size()) {
                    throw new EcAopServerBizException("9999", "副卡开户账户信息必传");
                }
                List<Map> uuInfo = (List<Map>) userInfo.get(0).get("uuInfo");
                if (null == uuInfo || 0 == uuInfo.size()) {
                    throw new EcAopServerBizException("9999", "三户接口未返回uuInfo信息");
                }
                Map tempMap = new HashMap();
                for (Map temMap : uuInfo) {
                    String relationTypeCode = (String) temMap.get("relationTypeCode");
                    String roleCodeB = (String) temMap.get("roleCodeB");
                    if ("ZF".equals(relationTypeCode) && "1".equals(roleCodeB)) {
                        tempMap.put("idA", temMap.get("userIdA"));
                        tempMap.put("startDate", temMap.get("startDate"));
                    }
                }
                if (null != tempMap && tempMap.size() > 0) {
                    msg.putAll(tempMap);
                }
                else {
                    throw new EcAopServerBizException("9999", "cbss未返回主副卡uuInfo信息");
                }
                List<Map> acctInfoList = (List<Map>) checkUserMap.get("acctInfo");
                if (IsEmptyUtils.isEmpty(acctInfo.get(0).get("debutySn"))) {
                    if (IsEmptyUtils.isEmpty(acctInfoList)) {
                        throw new EcAopServerBizException("9999", "cbss未返回主副卡acctInfo信息");
                    } else {
                        msg.put("acctId", acctInfoList.get(0).get("acctId"));
                        }
                } else {
                    msg.put("acctId", checkNull(acctInfo.get(0), "debutySn"));
                }
                if (!flag) {
                    msg.put("custId", checkNull((Map) msg.get("custInfo"), "custId"));
                }

            } catch (EcAopServerBizException e) {
                if (e.getMessage().contains("根据用户号码获取用户资料无数据")) {
                    msg.put("provinceCode", msg.get("province"));
                    Exchange queryExchange = ExchangeUtils.ofCopy(exchange, msg);
                    try {
                        lan.preData(pmp[3], queryExchange);
                        CallEngine.wsCall(queryExchange, "ecaop.comm.conf.url.cbss.services.VoyungoForAopSer");
                        lan.xml2Json("ecaop.trades.mvoa.queryMainAndVice.template", queryExchange);
                    } catch (EcAopServerBizException ex) {
                        ex.printStackTrace();
                        if (ex.getMessage().contains("号码非0H压单的主卡")) {
                            throw new EcAopServerBizException("9999", "主卡号码未竣工或号码非0H压单的主卡！");
                        } else {
                            throw new EcAopServerBizException(ex.getCode(), ex.getMessage());
                        }
                    }
                    Map checkVice = queryExchange.getOut().getBody(Map.class);
                    Map userInfo = (Map) checkVice.get("userInfo");
                    if (IsEmptyUtils.isEmpty(userInfo)) {
                        throw new EcAopServerBizException("9999", "cbss未返回主副卡userInfo信息");
                        }
                    custId = "1".equals(msg.get("delayTag")) ? (String) userInfo.get("custId") : (String) msg.get("custId");
                    msg.put("acctId", userInfo.get("acctId"));
                    msg.put("custId", custId);
                    msg.put("idA", userInfo.get("userId"));
                    Map custInfo = (Map) msg.get("custInfo");
                    msg.put("certAdress", custInfo.get("certAdress"));
                    msg.put("customerName", custInfo.get("customerName"));
                    msg.put("contactPerson", custInfo.get("contactPerson"));
                    msg.put("contactAddress", custInfo.get("contactAddress"));
                    msg.put("sex", custInfo.get("sex"));
                    msg.put("certType", CertTypeChangeUtils.certTypeMall2Cbss((String) custInfo.get("certType")));
                    msg.put("certNum", custInfo.get("certNum"));
                    msg.put("certExpireDate", custInfo.get("certExpireDate"));
                        }
                else {
                    throw new EcAopServerBizException(e.getCode(), e.getDetail());
                    }
            }
            msg.put("checkType", checkNull((Map) msg.get("custInfo"), "checkType"));
            msg.put("serialNumber", serialNumber);
            // 新加商城改造，18-01-27，zzc

            if (flag) {
                getCustInfo4CB(exchange, msg);
            }
            // 改造结束
            body.put("msg", msg);
            exchange.getIn().setBody(body);
        }
    }

    /**
     * 遍历Map 封装参数到msg
     */
    private void transMap(Map msg, Map infoMap) {
        Map map = new HashMap();
        map.put("certExpireDate", (String) infoMap.get("certEndDate"));
        String[] inInfo = new String[] { "sex", "certType", "certNum", "certAdress", "customerName",
                "contactPerson", "contactAddress" };
        String[] custInfo = new String[] { "sex", "certTypeCode", "certCode", "certAddr", "custName", 
                "contact", "postAddress" };
        for (int i = 0; i < 7; i++) {
            if (null != infoMap.get(custInfo[i])) {
                map.put(inInfo[i], infoMap.get(custInfo[i]));
            }
            else {
                throw new EcAopServerBizException("9999", "三户接口返回节点" + custInfo[i] + "不能为空");
            }
        }
        msg.putAll(map);
    }

    /**
     * 遍历Map 封装参数到msg
     */
    private static void iteraterMap(Map msg, Map infoMap) {
        try {
            Set<Map.Entry<String, String>> set = infoMap.entrySet();
            for (Map.Entry entry : set) {
                msg.put(entry.getKey(), entry.getValue());
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", e.getMessage());
        }

    }

    /**
     * 校验参数是否为空,如果为空返回异常
     * 只针对三户信息校验
     */
    private Object checkNull(Map map, String key) {
        if (null == map) {
            throw new EcAopServerBizException("9999", map + "不能为空");
        }
        if (null == map.get(key)) {
            throw new EcAopServerBizException("9999", key + "不能为空");
        }
        return map.get(key);
    }

    /**
     * 获取蛋疼的NUMBER_SRC CBSS无此字段无法完工
     * nbrsrc 1.总部号码 2.南25省号码 3.北六号码
     * 
     * @param provinceCode
     * @param serialNumber
     * @return
     */
    private String getNumberSrc(String provinceCode, String serialNumber) {
        // 1 -> 176号段为 总部号码
        if (serialNumber.startsWith("175") || serialNumber.startsWith("176") || serialNumber.startsWith("185")) {
            return "1";
        }
        // 如果是北六省份号码 NUMBER_SRC 为3
        if ("0011|0076|0017|0018|0097|0091".contains(provinceCode)) {
            return "3";
        }
        return "2";
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
        if ("04".equals(terminalType)) {
            return "05";
        }
        if ("05".equals(terminalType)) {
            return "06";
        }
        return "01";
    }
    public String getFromPara(Map msg, String paraId) {
        if (paraId == null || paraId.length() == 0) {
            return "";
        }
        boolean p1 = msg.get("para") instanceof List;
        List<Map> para = (List<Map>) msg.get("para");
        String value = "";
        if (para != null && para.size() > 0) {// 循环查找，没有则返回""
            for (Map temp : para) {
                String str1 = temp.get("paraId") + "";
                if (paraId.equals(str1)) {
                    value = temp.get("paraValue") + "";
                }
            }
        }
        return value;
    }

	/**
	 * 调用CBSS客户资料校验接口查询客户信息
	 * 
	 * @param exchange
	 * @param msg
	 * @return
	 */
	private void getCustInfo4CB(Exchange exchange, Map msg) {
		Map custInfo = new HashMap();
		Map custInfoMap = new HashMap();
		String certType = ((Map) msg.get("custInfo")).get("certType") + "";
		custInfo.put("operateType", "1");// 非集团用户
		certType = CertTypeChangeUtils.certTypeMall2Cbss(certType);
		custInfo.put("certCode", ((Map) msg.get("custInfo")).get("certNum"));
		custInfo.put("certTypeCode", certType);
		MapUtils.arrayPut(custInfo, msg, MagicNumber.COPYARRAY);// 员工等信息
		Exchange exchange4CustInfo = ExchangeUtils.ofCopy(exchange, custInfo);
		try {
            lan.preData(pmp[4], exchange4CustInfo);
			CallEngine.wsCall(exchange4CustInfo, "ecaop.comm.conf.url.cbss.services.CustSer");
			lan.xml2JsonNoError("ecaop.cust.cbss.check.template", exchange4CustInfo);
		} catch (Exception e) {
			throw new EcAopServerBizException("9999", "调用CBSS客户资料检验失败！" + e.getMessage());
		}
		Map retCustInfo = exchange4CustInfo.getOut().getBody(Map.class);
		List<Map> custInfoList = (List<Map>) retCustInfo.get("custInfo");

		// 三户没有客户信息时(返回编码非0000时新建客户)--新生成
		if (!"0000".equals(retCustInfo.get("code") + "")) {
			String[] copyArray = { "operatorId", "province", "channelId", "city", "district", "channelType" };
			Map tempMap = Maps.newHashMap();
			MapUtils.arrayPut(tempMap, msg, copyArray);
			msg.put("custId",
					GetSeqUtil.getSeqFromCb(pmp[1], ExchangeUtils.ofCopy(exchange, tempMap), "seq_cust_id", 1).get(0));
		}
		// 三户有客户时--取第一个
		else if (custInfoList != null && custInfoList.size() > 0) {
			custInfoMap = custInfoList.get(0);
			msg.put("custId", custInfoMap.get("custId"));
		}

		// 下边这些参数都用外围传过来的，不用三户里的
		msg.put("sex", ((Map) msg.get("custInfo")).get("sex"));
		msg.put("certType", CertTypeChangeUtils.certTypeMall2Cbss(((Map) msg.get("custInfo")).get("certType") + ""));
		msg.put("certNum", ((Map) msg.get("custInfo")).get("certNum"));
		msg.put("certAdress", ((Map) msg.get("custInfo")).get("certAdress"));
		msg.put("customerName", ((Map) msg.get("custInfo")).get("customerName"));
		msg.put("contactPerson", ((Map) msg.get("custInfo")).get("contactPerson"));
		msg.put("contactAddress", ((Map) msg.get("custInfo")).get("contactAddress"));

	}
    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
}
