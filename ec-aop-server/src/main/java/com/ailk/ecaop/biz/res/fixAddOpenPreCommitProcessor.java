package com.ailk.ecaop.biz.res;

import java.util.ArrayList;
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
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.MixOpenUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.alibaba.fastjson.JSON;

/**
 * 固话加装预提交
 * 
 * @auther Zhao Zhengchang
 * @create 2016_03_22
 */
@EcRocTag("fixAddOpenPreCommit")
public class fixAddOpenPreCommitProcessor extends BaseAopProcessor implements ParamsAppliable {

    private final String startDate = GetDateUtils.getDate();
    private final String endDate = MagicNumber.CBSS_DEFAULT_EXPIRE_TIME;
    // 代码优化
    private static final String[] PARAM_ARRAY = { "ecaop.masb.faoa.sUniTradeParametersMapping",
            "ecaop.masb.faoa.N6.sUniTradeParametersMapping",
            "ecaop.trade.cbss.checkUserParametersMapping",
            "ecaop.trade.n6.checkUserParametersMapping",
            "ecaop.masb.chph.gifa.ParametersMapping",
            "ecaop.trades.seqid.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[6];

    @Override
    public void process(Exchange exchange) throws Exception {

        // 设置北六和CB标志
        String flag = setFlag(exchange);

        // 调三户接口(要区分CB跟N6)
        Exchange threePartExchange = threePart(exchange, flag);

        // 处理三户返回信息
        Map preParams = dealInparams(threePartExchange, flag);

        // 调用预提交接口
        callPreCommit(preParams, exchange);

        // 处理返回
        dealReturn(exchange);

    }

    /**
     * 处理返回
     * 
     * @param exchange
     */
    private void dealReturn(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        // 获取msg信息
        Object msgObject = body.get("msg");
        Map msg = null;
        if (msgObject instanceof String) {
            msg = JSON.parseObject(msgObject.toString());
        }
        else {
            msg = (Map) msgObject;
        }
        Map message = exchange.getOut().getBody(Map.class);
        if (null != message.get("rspInfo")) {
            List<Map> rspInfoList = (List<Map>) message.get("rspInfo");
            Map rspInfo = rspInfoList.get(0);
            // 处理返回参数
            Map result = dealReturn(rspInfo, msg);
            if (null == result.get("userName")) {
                String userName = (String) ((Map) msg.get("newUserInfo")).get("certName");
                result.put("userName", userName);
            }
            exchange.getOut().setBody(result);
        }
    }

    /**
     * 调预提交
     * 
     * @param preParams
     * @param exchange
     * @throws Exception
     */
    private void callPreCommit(Map preParams, Exchange exchange) throws Exception {
        LanUtils lan = new LanUtils();
        // 获取msg信息
        Map body = exchange.getIn().getBody(Map.class);
        Object msgObject = body.get("msg");
        Map msg = null;
        if (msgObject instanceof String) {
            msg = JSON.parseObject(msgObject.toString());
        }
        else {
            msg = (Map) msgObject;
        }
        Map ext = (Map) preParams.get("ext");
        Map base = (Map) preParams.get("base");
        String opeSysType = (String) msg.get("opeSysType");
        String province = (String) msg.get("province");
        msg.put("base", base);
        msg.put("ext", ext);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        // 调用预提交接口
        exchange.setMethodCode("faoa");
        if ("2".equals(opeSysType)) {
            lan.preData(PARAM_ARRAY[0], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        }
        else {
            lan.preData(PARAM_ARRAY[1], exchange);
            if ("17|18|11|76|91|97".contains(province)) {
                CallEngine.wsCall(exchange,
                        "ecaop.comm.conf.url.n6ess.services.AOPOrdForNorthSer" + "." + msg.get("province"));
            }
            else {
                lan.preData(PARAM_ARRAY[0], exchange);
                CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
            }
        }

        lan.xml2Json("ecaop.trades.faoa.sUniTrade.template", exchange);
        exchange.getIn().setBody(body);
    }

    /**
     * 设置标志
     * 
     * @param exchange
     * @return
     */
    private String setFlag(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        String opeSysType = (String) msg.get("opeSysType");
        String province = (String) msg.get("province");
        String flag = "";
        boolean is3G = (opeSysType == null) || ("1".equals(opeSysType));
        if (is3G && "17|18|11|76|91|97".contains(province)) {
            flag = "n6";
        }
        else {
            flag = "cb";
        }
        return flag;
    }

    /**
     * 调三户接口
     * 
     * @param exchange
     * @return
     * @throws Exception
     */
    private Exchange threePart(Exchange exchange, String flag) throws Exception {
        LanUtils lan = new LanUtils();
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        msg.put("areaCodeNew", msg.get("areaCode"));
        msg.remove("areaCode");
        body.put("msg", msg);
        Exchange threePartExchange = null;
        if ("cb".equals(flag)) {
            // 调用三户接口
            String[] copyArray = { "operatorId", "province", "channelId", "city", "district", "channelType" };
            Map threePartMap = MapUtils.asMap("getMode", "101001101010001001", "serialNumber",
                    msg.get("shareSerialNumber"),
                    "tradeTypeCode", "9999");
            MapUtils.arrayPut(threePartMap, msg, copyArray);
            threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
            lan.preData(PARAM_ARRAY[2], threePartExchange);
            CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
            lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
            threePartExchange.getIn().setBody(body);
        }
        if ("n6".equals(flag)) {
            if (msg.get("shareSerialNumber") == null) {
                throw new EcAopServerBizException("9999", "共线宽带号码没传入！");
            }
            // 调用三户接口
            String[] copyArray = { "operatorId", "province", "channelId", "city", "district", "channelType" };
            Map threePartMap = MapUtils.asMap("getMode", "101001101010001001", "serialNumber",
                    msg.get("shareSerialNumber"),
                    "tradeTypeCode", "9999");
            MapUtils.arrayPut(threePartMap, msg, copyArray);
            threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
            lan.preData(PARAM_ARRAY[3], threePartExchange);
            CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.UsrForNorthSer" + "." + msg.get("province"));
            lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
            threePartExchange.getIn().setBody(body);

        }
        return threePartExchange;
    }

    /**
     * 处理返回信息
     */
    private Map dealReturn(Map rspInfo, Map msg) {
        String serialNumber = (String) msg.get("serialNumber");// 固话号码
        String userPasswd = serialNumber.substring(serialNumber.length() - 6, serialNumber.length());
        Map ret = new HashMap();
        ret.put("provOrderId", isNull(rspInfo.get("bssOrderId").toString()));
        ret.put("userPasswd", userPasswd);
        if (null != rspInfo.get("provinceOrderInfo")) {
            List<Map> provinceOrderInfoList = (List<Map>) rspInfo.get("provinceOrderInfo");
            for (Map provinceOrderInfo : provinceOrderInfoList) {
                ret.put("debutyAreaCode", provinceOrderInfo.get("shareAreaCode"));
                if (null != provinceOrderInfo.get("preFeeInfoRsp")) {
                    List<Map> feeInfo = (List<Map>) provinceOrderInfo.get("preFeeInfoRsp");
                    List<Map> newFeeInfo = new ArrayList<Map>();
                    if (0 != feeInfo.size()) {
                        for (Map fee : feeInfo) {
                            Map newFee = dealFee(fee);
                            newFeeInfo.add(newFee);
                        }
                        ret.put("feeInfo", newFeeInfo);
                    }
                }
                String totalFee = (String) provinceOrderInfo.get("totalFee");
                ret.put("totalFee", totalFee);
            }
        }
        ret.put("userName", msg.get("userName"));
        return ret;
    }

    /**
     * 费用的处理
     */
    private Map dealFee(Map oldFeeInfo) {
        Map feeInfo = new HashMap();
        feeInfo.put("feeId", isNull((String) oldFeeInfo.get("feeTypeCode")));
        feeInfo.put("feeCategory", isNull((String) oldFeeInfo.get("feeMode")));
        feeInfo.put("feeDes", isNull((String) oldFeeInfo.get("feeTypeName")));
        feeInfo.put("maxRelief", isNull((String) oldFeeInfo.get("maxDerateFee")));
        feeInfo.put("origFee", isNull(oldFeeInfo.get("fee").toString()));
        return feeInfo;
    }

    /**
     * 处理数据
     * 
     * @param exchange
     * @param flag
     * @return
     * @throws Exception
     */
    private Map dealInparams(Exchange exchange, String flag) throws Exception {

        // 获取msg信息
        Map body = exchange.getIn().getBody(Map.class);
        Object msgObject = body.get("msg");
        Map msg = null;
        if (msgObject instanceof String) {
            msg = JSON.parseObject(msgObject.toString());
        }
        else {
            msg = (Map) msgObject;
        }

        // 获取三户返回的结果
        List<Map> userInfoList = (List<Map>) exchange.getOut().getBody(Map.class).get("userInfo");
        List<Map> custInfoList = (List<Map>) exchange.getOut().getBody(Map.class).get("custInfo");
        List<Map> acctInfoList = (List<Map>) exchange.getOut().getBody(Map.class).get("acctInfo");
        Map userInfo = userInfoList.get(0);
        Map custInfo = custInfoList.get(0);
        Map acctInfo = acctInfoList.get(0);
        if (null == userInfo || 0 == userInfo.size()) {
            throw new EcAopServerBizException("9999", "三户接口未返回用户信息");
        }
        if (null == custInfo || 0 == custInfo.size()) {
            throw new EcAopServerBizException("9999", "三户接口未返回客户信息");
        }
        if (null == acctInfo || 0 == acctInfo.size()) {
            throw new EcAopServerBizException("9999", "三户接口未返回账户信息");
        }
        // 号码类型判断--用于区分宽带号码和固话号码
        String serviceClassCode = (String) userInfo.get("serviceClassCode");
        if (!"0040".equals(serviceClassCode)) {
            throw new EcAopServerBizException("9999", "传入的宽带号码错误！");
        }

        Map newUserInfo = (Map) msg.get("newUserInfo");
        String createOrExtendsAcct = (String) newUserInfo.get("createOrExtendsAcct");
        String eparchyCode = (String) custInfo.get("eparchyCode");
        String cityCode = (String) msg.get("city");

        // 新建账户时创建新账户
        String acctId = (String) acctInfo.get("acctId");
        if ("0".equals(createOrExtendsAcct)) {
            if ("n6".equals(flag)) {
                acctId = GetSeqUtil.getSeqFromN6ess(pmp[5], exchange, "ACCT_ID", eparchyCode);// 北六获取客户ID
                exchange.getIn().setBody(body);
                createAcct(acctInfo, msg, exchange, acctId);
            }
            else {
                acctId = (String) GetSeqUtil.getSeqFromCb(pmp[4], exchange, "seq_trade_id", 1).get(0);
                exchange.getIn().setBody(body);
                createAcct(acctInfo, msg, exchange, acctId);
            }
        }

        String xDatatype = "NULL";
        // 准备活动信息
        List<Map> activityInfos = (List<Map>) newUserInfo.get("activityInfo");
        // 准备产品信息
        List<Map> productInfos = (List<Map>) newUserInfo.get("productInfo");
        Map productInfo = productInfos.get(0);
        String productId = "";
        String productMode = "";
        if (productInfo != null) {
            productId = (String) productInfo.get("productId");
            productMode = (String) productInfo.get("productMode");
            if ("0".equals(productMode)) {
                productInfo.put("productMode", "1");
            }
            if ("1".equals(productMode)) {
                productInfo.put("productMode", "0");
            }
            productInfos.add(productInfo);
        }
        String provinceCode = "00" + (String) msg.get("province");
        // 准备用户标示
        String id = (String) userInfo.get("userId");
        msg.put("userId", id);

        Map EXT = new HashMap();
        // 处理产品信息
        if (productInfos != null) {
            EXT = TradeManagerUtils.preProductInfo(productInfos, provinceCode, msg);// 没调接口
            body.put("msg", msg);
            exchange.getIn().setBody(body);
        }
        // 获取产品信息
        Map tradeProduct = (Map) EXT.get("tradeProduct");
        List<Map> item = (List) tradeProduct.get("item");
        String brandCode = null;
        for (Map m : item) {
            if (m.get("productId") != null) {
                productId = (String) m.get("productId");
            }
            if (m.get("brandCode") != null) {
                brandCode = (String) m.get("brandCode");
            }
        }
        Map tradeProductType = (Map) EXT.get("tradeProductType");
        List<Map> itemT = (List) tradeProductType.get("item");
        String productTypeCode = null;
        for (Map m : itemT) {
            if (m.get("productTypeCode") != null) {
                productTypeCode = (String) m.get("productTypeCode");

            }
        }
        // brandCode = (String) EXT.get("brandCode");
        // String productTypeCode = (String) EXT.get("productTypeCode");
        // productId = (String) EXT.get("mproductId");
        System.out.println("==========" + brandCode + "====" + productTypeCode + "=======");
        // 处理活动信息
        ActivityInfoProcessor activiInfo = new ActivityInfoProcessor();
        EXT = activiInfo.process(exchange);
        exchange.getIn().setBody(body);
        // 生成itemId
        String subscribeId = "";
        String itemId = "";
        String userId = "";
        if ("cb".equals(flag)) {
            subscribeId = (String) GetSeqUtil.getSeqFromCb(pmp[4], exchange, "seq_trade_id", 1).get(0);
            exchange.getIn().setBody(body);
            itemId = (String) GetSeqUtil.getSeqFromCb(pmp[4], exchange, "seq_trade_id", 1).get(0);
            exchange.getIn().setBody(body);
            userId = (String) GetSeqUtil.getSeqFromCb(pmp[4], exchange, "seq_trade_id", 1).get(0);
            exchange.getIn().setBody(body);
        }
        else {
            subscribeId = GetSeqUtil.getSeqFromN6ess(pmp[5], exchange, "TRADE_ID", eparchyCode);
            exchange.getIn().setBody(body);
            itemId = GetSeqUtil.getSeqFromN6ess(pmp[5], exchange, "ITEM_ID", eparchyCode);
            exchange.getIn().setBody(body);
            userId = GetSeqUtil.getSeqFromN6ess(pmp[5], exchange, "USER_ID", eparchyCode);
            exchange.getIn().setBody(body);
        }

        // base之前的
        msg.put("ordersId", msg.get("orderNo"));// 总部订单ID
        msg.put("operTypeCode", "0");// 操作类型：, 0-订单受理, 1-业务返销, 2-撤单, 3-改单
        // 准备base节点
        Map base = paraBase(brandCode, exchange, custInfo, userInfo, flag, productId, subscribeId, acctId, userId);

        // 准备tradeAcct节点
        if ("n6".equals(flag)) {
            Map item0 = paraTradeAcct(flag, acctInfo, acctId, cityCode);
            Map TRADE_ACCT = new HashMap();
            TRADE_ACCT.put("item", item0);
            EXT.put("tradeAcct", TRADE_ACCT);
        }

        // 准备TradeUser节点
        Map item1 = paraTradeUser(productTypeCode, brandCode, flag, msg, custInfo);
        Map TRADE_USER = new HashMap();
        TRADE_USER.put("item", item1);
        EXT.put("tradeUser", TRADE_USER);

        // 准备tradePayrelation
        Map item3 = paraTradePayrelation(exchange, flag, userInfo, acctId, eparchyCode, userId);
        Map TRADE_PAYRELATION = new HashMap();
        TRADE_PAYRELATION.put("item", item3);
        EXT.put("tradePayrelation", TRADE_PAYRELATION);

        // 准备TradeItem节点
        List items = paraTradeItem(itemId, flag, msg, exchange.getAppCode());
        Map TRADE_ITEM = new HashMap();
        TRADE_ITEM.put("item", items);
        EXT.put("tradeItem", TRADE_ITEM);

        // 准备tradeOther节点
        if ("n6".equals(flag)) {
            Map TRADE_OTHER = new HashMap();
            Map item4 = new HashMap();
            String endDate = MagicNumber.CBSS_DEFAULT_EXPIRE_TIME;
            item4.put("xDatatype", xDatatype);
            item4.put("rsrvValueCode", "ZZFS");// 预留资料编码
            item4.put("rsrvValue", "0");// 预留资料
            item4.put("rsrvStr1", "131102198612051074");
            item4.put("rsrvStr2", "10");
            item4.put("rsrvStr3", "-9");
            item4.put("rsrvStr6", "1");
            item4.put("rsrvStr7", "0");
            item4.put("modifyTag", "0");// 状态属性：0－增加 1－删除 2－修改
            item4.put("startDate", startDate);
            item4.put("endDate", endDate);
            TRADE_OTHER.put("item", item4);
            EXT.put("tradeOther", TRADE_OTHER);
        }

        // 准备TradeSubItem节点
        List<Map> subitems = paraTradeSubItem(itemId, msg, flag, eparchyCode, cityCode);
        Map TRADE_SUB_ITEM = new HashMap();
        TRADE_SUB_ITEM.put("item", subitems);
        EXT.put("tradeSubItem", TRADE_SUB_ITEM);

        // 准备TradeRes节点
        preTradeRes(msg, EXT);

        Map TRADE = new HashMap();
        TRADE.put("base", base);
        TRADE.put("ext", EXT);

        body.put("msg", msg);
        exchange.getIn().setBody(body);

        return TRADE;
    }

    /**
     * 准备tradeTypeCode等
     */
    private Map preCheckData(Map msg) {
        msg.put("tradeTypeCode", "0340");
        msg.put("getMode", "101001000000000000000000000000");
        Map<String, Object> cardNumberInfo = new HashMap();
        cardNumberInfo.put("serialNumber", msg.get("serialNumber"));
        msg.put("cardNumberInfo", cardNumberInfo);
        return msg;
    }

    /**
     * 创建帐户信息
     */
    private void createAcct(Map acctInfo, Map msg, Exchange exchange, String acctId) {
        acctInfo.put("isNew", "0");
        Map newAcctInfo = new HashMap();
        Map newUserInfo = (Map) msg.get("newUserInfo");
        newAcctInfo.put("acctId", acctId);
        newAcctInfo.put("payName", newUserInfo.get("certName"));
        newAcctInfo.put("payModeCode", "0");
        newAcctInfo.put("payPasswd", "000000");
        newAcctInfo.put("payAddress", newUserInfo.get("certAddress"));
        newAcctInfo.put("payPostCode", "000000");
        newAcctInfo.put("payContact", msg.get("contactPerson"));
        newAcctInfo.put("payContactPhone", msg.get("contactPhone"));
        acctInfo.put("newAcctInfo", newAcctInfo);
    }

    /**
     * 准备base节点
     */
    private Map paraBase(String brandCode, Exchange exchange, Map custInfo, Map userInfo, String flag,
            String productId,
            String subscribeId, String acctId, String userId) {

        Map body = exchange.getIn().getBody(Map.class);
        Object msgObject = body.get("msg");
        Map msg = null;
        if (msgObject instanceof String) {
            msg = JSON.parseObject(msgObject.toString());
        }
        else {
            msg = (Map) msgObject;
        }
        String cityCode = (String) userInfo.get("cityCode");
        String eparchyCode = (String) custInfo.get("eparchyCode");
        Map newUserInfo = (Map) msg.get("newUserInfo");
        String usecustId = (String) custInfo.get("custId");
        String custId = (String) custInfo.get("custId");
        String acceptDate = GetDateUtils.getDate();
        String custName = (String) newUserInfo.get("certName");
        String execTime = GetDateUtils.getDate();
        String productSpec = null;
        Map base = new HashMap();
        String termIp = "";

        if ("cb".equals(flag)) {
            base.put("cityCode", userInfo.get("cityCode"));
            termIp = "132.35.87.198"; // 受理终端IP写死
        }
        else {
            base.put("mainDiscntCode", "");// 主资费标识
            termIp = "10.124.0.11";
        }
        base.put("subscribeId", subscribeId);
        base.put("tradeId", subscribeId);
        base.put("startDate", startDate);
        base.put("endDate", endDate);
        base.put("acceptDate", acceptDate);
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        base.put("brandCode", brandCode);
        System.out.println("*********** brandCode :" + brandCode);
        base.put("inModeCode", new ChangeCodeUtils().getInModeCode(exchange.getAppCode()));
        base.put("tradeTypeCode", "0010");
        base.put("productId", productId);
        base.put("userId", userId);
        base.put("custId", custId);
        base.put("usecustId", usecustId);
        base.put("acctId", acctId);
        base.put("userDiffCode", userInfo.get("userDiffCode"));// 用户类型用户类型00:普通用户01:亲友电话虚用户02:对讲机虚用户03:两地同虚用户04:两地通虚用户05:组合产品用户
        base.put("netTypeCode", "0030");// 0030表示固话业务
        base.put("serinalNamber", msg.get("serialNumber"));
        base.put("custName", custName);
        base.put("termIp", termIp);
        base.put("eparchyCode", eparchyCode);
        base.put("execTime", execTime);
        base.put("operFee", "0");
        base.put("foregift", "0");
        base.put("advancePay", "0");
        base.put("feeState", "");// 收费标志：0-未收费，1-已收费
        base.put("feeStaffId", "");
        base.put("cancelTag", "0");
        base.put("checktypeCode", "0");// 身份验证方式
        base.put("checkTypeCode", "0");
        base.put("chkTag", "0");
        base.put("actorName", "");// 担保人名称
        base.put("actorCertTypeId", "");// 经办人证件类型
        base.put("actorPhone", "");// 担保人证件号码
        base.put("actorCertNum", "");// 担保人证件号码
        base.put("contact", msg.get("contactPerson"));// 联系人
        base.put("contactPhone", msg.get("contactPhone"));
        base.put("contactAddress", msg.get("contactAddress") == null ? "" : msg.get("contactAddress"));
        base.put("remark", msg.get("orderRemarks"));
        base.put("productSpec", productSpec);
        return base;
    }

    /**
     * 准备 tradeSubItem节点
     * 
     * @throws Exception
     */
    private List<Map> paraTradeSubItem(String itemId, Map msg, String flag, String eparchyCode, String cityCode)
            throws Exception {
        List<Map> subitems = new ArrayList<Map>();
        LanUtils lan = new LanUtils();
        // 在北六库里查cbaccessType
        MixOpenUtils dao = new MixOpenUtils();
        Map inMap = new HashMap();
        Map newUserInfo = (Map) msg.get("newUserInfo");
        String accessType = (String) newUserInfo.get("accessMode");
        String cbAccessType = null;
        if ("cb".equals(flag)) {
            cbAccessType = (String) newUserInfo.get("cbssAccessMode");
            inMap.put("ACCESS_TYPE", accessType);
            inMap.put("CB_ACCESS_TYPE", cbAccessType);
            inMap.put("EPARCHY_CODE", eparchyCode);
            inMap.put("PROVINCE_CODE", msg.get("province"));
            inMap.put("NET_TYPE_CODE", "30");
            Map resultSet = dao.checkAccessCode(inMap);
            accessType = (String) resultSet.get("accessMode");
            cbAccessType = (String) resultSet.get("cbssAccessMode");
        }

        String moduleExchId = (String) newUserInfo.get("moduleExchId");
        if (moduleExchId == null) {
            moduleExchId = "";
        }
        String addressCode = (String) newUserInfo.get("addressCode");
        if (addressCode == null) {
            addressCode = "";
        }
        String accessMode = (String) newUserInfo.get("accessMode");
        if (accessMode == null) {
            accessMode = "";
        }
        String exchCode = (String) newUserInfo.get("exchCode");
        if (exchCode == null) {
            exchCode = "";
        }

        String userType = (String) newUserInfo.get("userType");
        if (userType == null) {
            userType = "1";
        }

        String machineBrandCode = null;
        String machineType = null;
        String machineProvide = null;
        String machineModelCode = null;
        List machineInfos = (List) msg.get("machineInfo");
        if (machineInfos != null) {
            Map machineInfo = (Map) machineInfos.get(0);
            machineBrandCode = (String) machineInfo.get("machineBrandCode");
            machineType = (String) machineInfo.get("machineType");
            machineProvide = (String) machineInfo.get("machineProvide");
            machineModelCode = (String) machineInfo.get("machineModelCode");
        }

        if (machineProvide == null) {
            machineProvide = "1";
        }
        if (machineType == null) {
            machineType = "1";
        }

        if ("n6".equals(flag)) {
            subitems.add(lan.createTradeSubItem("PREPAY_TAG", "0", itemId));
            subitems.add(lan.createTradeSubItem("ADDRESS_ID", addressCode, itemId));
            subitems.add(lan.createTradeSubItem("MS_AREA_ID", "", itemId));
            subitems.add(lan.createTradeSubItem("FLAG_114", "Y", itemId));
            subitems.add(lan.createTradeSubItem("SHARE_NBR", msg.get("shareSerialNumber"), itemId));
            subitems.add(lan.createTradeSubItem("GLYY", "-1", itemId));
            subitems.add(lan.createTradeSubItem("BOOK_FLAG", "0", itemId));
            subitems.add(lan.createTradeSubItem("USER_PASSWD", msg.get("userPasswd"), itemId));
            subitems.add(lan.createTradeSubItem("MOFFICE_ID", exchCode, itemId));
            subitems.add(lan.createTradeSubItem("MES_NOTICE_NUMBER", "", itemId));
            subitems.add(lan.createTradeSubItem("DIREC_FLAG", "Y", itemId));
            subitems.add(lan.createTradeSubItem("C_2111", "802009", itemId));
            subitems.add(lan.createTradeSubItem("DEPT_MOFFICE", "182018", itemId));
            subitems.add(lan.createTradeSubItem("USER_CALLING_AREA", cityCode, itemId));
            subitems.add(lan.createTradeSubItem("C_1", "1001", itemId));
            subitems.add(lan.createTradeSubItem("USER_TYPE_CODE", "2", itemId));
            subitems.add(lan.createTradeSubItem("QZLX_PTDH", "X3", itemId));
            subitems.add(lan.createTradeSubItem("C_2", "2001", itemId));
            subitems.add(lan.createTradeSubItem("C_2332", "802910", itemId));
            subitems.add(lan.createTradeSubItem("MAINT_AREA_ID", "", itemId));
            subitems.add(lan.createTradeSubItem("PRE_START_TIME", "", itemId));
            subitems.add(lan.createTradeSubItem("IS_FATE", "0", itemId));
            subitems.add(lan.createTradeSubItem("SCHE_ID", "1234567890", itemId));
            subitems.add(lan.createTradeSubItem("DETAIL_INSTALL_ADDRESS", newUserInfo.get("installAddress"), itemId));
            subitems.add(lan.createTradeSubItem("C_2337", "802917", itemId));
            subitems.add(lan.createTradeSubItem("BUSI_CODE", cityCode, itemId));
            subitems.add(lan.createTradeSubItem("GXLX_2060", "ADS0:0:40", itemId));
            subitems.add(lan.createTradeSubItem("C_2202", "", itemId));
            subitems.add(lan.createTradeSubItem("C_2091", "801844", itemId));
            subitems.add(lan.createTradeSubItem("SERV_DEPT_ID", "182018", itemId));
            subitems.add(lan.createTradeSubItem("NO_BOOK_REASON", "", itemId));
            subitems.add(lan.createTradeSubItem("C_2092", "", itemId));
            subitems.add(lan.createTradeSubItem("INSTALL_ADDRESS", "", itemId));
            subitems.add(lan.createTradeSubItem("C_2093", "", itemId));
            subitems.add(lan.createTradeSubItem("C_773", "773001", itemId));
            subitems.add(lan.createTradeSubItem("POST", "", itemId));
            subitems.add(lan.createTradeSubItem("SEL_LOCAL_NET_ID", "0311", itemId));
            subitems.add(lan.createTradeSubItem("GLHM_NBR", "", itemId));
            subitems.add(lan.createTradeSubItem("ENTRY_ADDR", "", itemId));
            subitems.add(lan.createTradeSubItem("INIT_PASSWD", "0", itemId));
            subitems.add(lan.createTradeSubItem("PUBLISH_NAME", "", itemId));
            subitems.add(lan.createTradeSubItem("SFGX_2060", "Y", itemId));// 是否共线 N 否, Y 是
            subitems.add(lan.createTradeSubItem("BUSI_FLAG", "", itemId));
            subitems.add(lan.createTradeSubItemFive("BUSI_CODE", cityCode, itemId));
            subitems.add(lan.createTradeSubItemE("CONTACT_INFO", "243567876543", itemId));
            subitems.add(lan.createTradeSubItemE("CONTACT_NAME", "", itemId));
        }
        else {
            subitems.add(lan.createTradeSubItem("PREPAY_TAG", "0", itemId));// 付费模式 0 后付费
            subitems.add(lan.createTradeSubItem("ADDRESS_ID", addressCode, itemId));
            subitems.add(lan.createTradeSubItem("SHARE_NBR", msg.get("shareSerialNumber"), itemId));// 共线号码
            subitems.add(lan.createTradeSubItem("USETYPE", userType, itemId));// 使用性质 1 个人,2 企业, 3 学生,4 政府机关,5 公司自用, 6
                                                                              // 测试, 99 其他
            subitems.add(lan.createTradeSubItem("MODULE_EXCH_ID", moduleExchId, itemId));
            subitems.add(lan.createTradeSubItem("ACCESS_TYPE", accessType, itemId));// B侧;接入方式 A1 PSTN, A2 NGN, A3 ISDN,
                                                                                    // A4 FTTH
            subitems.add(lan.createTradeSubItem("AREA_CODE", cityCode, itemId));
            subitems.add(lan.createTradeSubItem("POINT_EXCH_ID", "3110008", itemId));
            subitems.add(lan.createTradeSubItem("USER_PASSWD", msg.get("userPasswd"), itemId));// msg中并无userpasswd ?
            subitems.add(lan.createTradeSubItem("TERMINAL_SN", "", itemId));
            subitems.add(lan.createTradeSubItem("ISFLAG114", "0", itemId));// 登114 0 否, 1 是
            subitems.add(lan.createTradeSubItem("TERMINAL_MAC", "", itemId));
            subitems.add(lan.createTradeSubItem("SWITCH_EXCH_ID", "", itemId));
            subitems.add(lan.createTradeSubItem("TERMINALSRC_MODE", "A001", itemId));
            subitems.add(lan.createTradeSubItem("AREA_EXCH_ID", "31101", itemId));
            subitems.add(lan.createTradeSubItem("MOFFICE_ID", exchCode, itemId));
            subitems.add(lan.createTradeSubItem("COMMUNIT_ID", "", itemId));
            subitems.add(lan.createTradeSubItem("USER_CALLING_AREA", cityCode, itemId));
            subitems.add(lan.createTradeSubItem("USER_TYPE_CODE", "0", itemId));// 用户类型 0 普通, 1 公免,T 测试
            subitems.add(lan.createTradeSubItem("DIRECFLAG", "0", itemId));// 登号簿 0 否, 1 是
            subitems.add(lan.createTradeSubItem("CB_ACCESS_TYPE", cbAccessType, itemId));// CB侧
            subitems.add(lan.createTradeSubItem("COMMPANY_NBR", "", itemId));// 企业短信号码
            subitems.add(lan.createTradeSubItem("DETAIL_INSTALL_ADDRESS", newUserInfo.get("installAddress"), itemId));
            subitems.add(lan.createTradeSubItem("TOWN_FLAG", "C", itemId));// 地域标识 C 城市客户, T 农村客户
            subitems.add(lan.createTradeSubItem("POSITION_XY", "", itemId));
            subitems.add(lan.createTradeSubItem("TERMINAL_MODEL", machineModelCode, itemId));
            subitems.add(lan.createTradeSubItem("LOCAL_NET_CODE", eparchyCode, itemId));
            subitems.add(lan.createTradeSubItem("INSTALL_ADDRESS", newUserInfo.get("installAddress"), itemId));
            subitems.add(lan.createTradeSubItem("TERMINAL_TYPE", machineType, itemId));// 终端提供方式 0 自备, 1 租用,2 赠送
            subitems.add(lan.createTradeSubItem("TERMINAL_BRAND", machineBrandCode, itemId));
            subitems.add(lan.createTradeSubItem("COMMUNIT_NAME", "", itemId));
            subitems.add(lan.createTradeSubItem("INIT_PASSWD", "0", itemId));
            subitems.add(lan.createTradeSubItem("COLLINEISFLAG114AR_TYPE", "X3", itemId));// 共线类型 X3：固话、宽带共线
            subitems.add(lan.createTradeSubItemE("LINK_NAME", "&#x738B;&#x6D69;", itemId));// 联系人
            subitems.add(lan.createTradeSubItemE("OTHERCONTACT", "", itemId));// 微信号或QQ
            subitems.add(lan.createTradeSubItemE("LINK_PHONE", "18531152138", itemId));// 联系人电话
        }
        return subitems;
    }

    /**
     * 准备 TradeItem节点
     */
    private List<Map> paraTradeItem(String itemId, String flag, Map msg, String appCode)
    {
        LanUtils lan = new LanUtils();
        String developStaffId = (String) msg.get("recomPersonId");
        String developDepartId = (String) msg.get("recomPersonChannelId");// 发展人渠道
        List<Map> items = new ArrayList<Map>();
        String channelType = (String) msg.get("channelType");
        // 将每一组参数放入
        if ("cb".equals(flag)) {
            if (new ChangeCodeUtils().isWOPre(appCode)) {
                items.add(lan.createAttrInfoNoTime("WORK_TRADE_ID", msg.get("orderNo")));
            }
            items.add(lan.createAttrInfoNoTime("DEVELOP_STAFF_ID", developStaffId));
            items.add(lan.createAttrInfoNoTime("DEVELOP_DEPART_ID", developDepartId));
            items.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", channelType));
            items.add(LanUtils.createTradeItem("EXTRA_INFO", ""));
            items.add(LanUtils.createTradeItem("PH_NUM", ""));
            items.add(LanUtils.createTradeItem("WORK_STAFF_ID", ""));
            items.add(LanUtils.createTradeItem("WORK_TRADE_ID", ""));
            items.add(LanUtils.createTradeItem("NO_BOOK_REASON", ""));
            items.add(LanUtils.createTradeItem("BOOK_FLAG", "0"));
            items.add(LanUtils.createTradeItem("USER_PASSWD", msg.get("userPasswd")));
            items.add(LanUtils.createTradeItem("EXISTS_ACCT", "1"));
            items.add(LanUtils.createTradeItem("SUB_TYPE", "0"));
            items.add(LanUtils.createTradeItem("WORK_DEPART_ID", ""));
            items.add(LanUtils.createTradeItem("REOPEN_TAG", "2"));
            items.add(LanUtils.createTradeItem("NEW_PASSWD", msg.get("userPasswd")));
            items.add(LanUtils.createTradeItem("SFGX_2060", "Y"));
            items.add(LanUtils.createTradeItem("PRE_START_TIME", ""));
            items.add(LanUtils.createTradeItem("GXLX_TANGSZ", "GZKD:0:40"));// 共线操作 GZKD:0:40 加装到公众宽带 ,GZDH:1:40 同装到公众宽带
        }
        else {
            items.add(lan.createAttrInfoNoTime("MARKETING_MODE", ""));// 营销方式
            items.add(lan.createAttrInfoNoTime("DEVELOP_STAFF_ID", developStaffId));// 发展人
            items.add(lan.createAttrInfoNoTime("DEVELOP_DEPART_ID", developDepartId));// 发展渠道
            items.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", channelType));
            items.add(LanUtils.createTradeItem("PH_NUM", "031184880948"));
            items.add(LanUtils.createTradeItem("PRINT_REMARKS", ""));
            items.add(LanUtils.createTradeItem("APPL_REASON", ""));
            items.add(LanUtils.createTradeItem("REF_INFO_NBR", ""));
            items.add(LanUtils.createTradeItem("PRIORITY", "1"));
            items.add(LanUtils.createTradeItem("OTHER_REASON", ""));
            items.add(LanUtils.createTradeItem("USER_PASSWD", msg.get("userPasswd")));
            items.add(LanUtils.createTradeItem("IS_IMMEDIACY", "0"));
            items.add(LanUtils.createTradeItem("EXISTS_ACCT", "0"));
            items.add(LanUtils.createTradeItem("PRE_TRADE_ID_NEW", ""));
            items.add(LanUtils.createTradeItem("C_1024", ""));
            items.add(LanUtils.createTradeItem("IMMEDIACY_INFO", "0"));
            items.add(LanUtils.createTradeItem("WAIT_TYPE", "3"));
            items.add(LanUtils.createTradeItem("REOPEN_TAG", "2"));
            items.add(LanUtils.createTradeItem("NEW_PASSWD", msg.get("userPasswd")));
        }
        return items;
    }

    /**
     * 准备 tradeUser节点
     */
    private Map paraTradeUser(String productTypeCode, String brandCode, String flag, Map msg, Map custInfo)
    {
        String developStaffId = (String) msg.get("recomPersonId");
        String developDepartId = (String) msg.get("recomPersonChannelId");// 发展人渠道
        String developDate = GetDateUtils.getDate();
        String userPasswd = isNull((String) msg.get("userPasswd"));

        String developCityCode = (String) msg.get("recomPersonCityCode");
        String usecustId = (String) custInfo.get("custId");

        Map item1 = new HashMap();
        if ("cb".equals(flag)) {
            item1.put("userTypeCode", "0");

        }
        else {
            item1.put("userTypeCode", "2");
        }
        item1.put("xDataType", "NULL");
        item1.put("usecustId", usecustId);
        item1.put("userPasswd", userPasswd);
        item1.put("scoreValue", "0");
        item1.put("creditClass", "0");
        item1.put("basicCreditValue", "0");
        item1.put("creditValue", "0");
        item1.put("acctTag", "0");
        item1.put("prepayTag", "0");
        item1.put("productTypeCode", productTypeCode);
        item1.put("brandCode", brandCode);
        item1.put("inDate", GetDateUtils.getDate());
        item1.put("openDate", GetDateUtils.getDate());
        item1.put("openMode", "0");
        item1.put("openDepartId", msg.get("channelId"));// 下面四个不写死
        item1.put("openStaffId", msg.get("operatorId"));
        item1.put("inDepartId", msg.get("channelId"));
        item1.put("inStaffId", msg.get("operatorId"));
        item1.put("removeTag", "0");
        item1.put("cityCode", msg.get("city"));
        item1.put("userStateCodeset", "0");
        item1.put("mputeMonthFee", "0");
        item1.put("developStaffId", developStaffId);
        item1.put("developDate", developDate);
        item1.put("developEparchyCode", "0311");// 写死
        item1.put("developCityCode", developCityCode);
        item1.put("developDepartId", developDepartId);
        item1.put("inNetMode", "E");
        return item1;
    }

    /**
     * 准备 TradeAcct节点
     */
    private Map paraTradeAcct(String flag, Map acctInfo, String acctId, String cityCode) {

        String payName = (String) acctInfo.get("payName");
        Map item0 = new HashMap();
        item0.put("xDataType", null);
        item0.put("acctId", acctId);
        item0.put("payName", payName);
        item0.put("payModeCode", acctInfo.get("payModeCode"));
        item0.put("scoreValue", "0");
        item0.put("creditClassId", "0");
        item0.put("basicCreditValue", "0");
        item0.put("creditValue", "0");
        item0.put("cityCode", cityCode);
        item0.put("creditControlId", "0");
        item0.put("debutyUserId", acctInfo.get("debutyUserId"));
        item0.put("debutyCode", acctInfo.get("debutyCode"));
        item0.put("removeTag", "0");
        item0.put("openDate", GetDateUtils.getDate());
        return item0;
    }

    /**
     * 准备TradeRes节点
     */
    //
    private void preTradeRes(Map msg, Map EXT) {
        String serialNumber = (String) msg.get("serialNumber");
        String cityArea = (String) msg.get("cityArea");
        Map tradeRes = new HashMap();
        String resCode = serialNumber;
        List<Map> itemList = new ArrayList<Map>();
        Map item8 = new HashMap();
        Map item9 = new HashMap();

        item8.put("xDatatype", "NULL");
        item8.put("reTypeCode", "0");
        item8.put("resCode", resCode);
        item8.put("modifyTag", "0");
        item8.put("startDate", GetDateUtils.getDate());
        item8.put("endDate", "20501231122359");
        item9.put("xDatatype", "NULL");
        item9.put("reTypeCode", "1");
        item9.put("resCode", resCode);
        item9.put("modifyTag", "0");
        item9.put("resInfo1", resCode);

        itemList.add(item8);
        itemList.add(item9);
        tradeRes.put("item", itemList);
        EXT.put("tradeRes", tradeRes);
    }

    /**
     * 准备 TradePayrelation节点
     */
    private Map paraTradePayrelation(Exchange exchange, String flag, Map userInfo, String acctId, String eparchyCode,
            String userId) {
        Map item3 = new HashMap();
        Map body = exchange.getIn().getBody(Map.class);
        if ("cb".equals(flag)) {
            item3.put("payrelationId", GetSeqUtil.getSeqFromCb(pmp[4], exchange, "seq_trade_id", 1).get(0));
            exchange.getIn().setBody(body);
        }
        else {
            item3.put("payrelationId", GetSeqUtil.getSeqFromN6ess(pmp[5], exchange, "TRADE_ID", eparchyCode));
            exchange.getIn().setBody(body);
        }
        item3.put("userId", userId);
        item3.put("acctId", acctId);
        item3.put("payitemCode", "-1");
        item3.put("acctPriority", "0");
        item3.put("userPriority", "0");
        item3.put("bindType", "1");
        item3.put("defaultTag", "1");
        item3.put("limitType", "0");
        item3.put("limit", "0");
        item3.put("complementTag", "0");
        item3.put("addupMonths", "0");
        item3.put("addupMethod", "0");
        item3.put("actTag", "1");
        return item3;
    }

    /**
     * 判断是否为空
     */
    private String isNull(String string) {
        return null == string ? "" : string;
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
}
