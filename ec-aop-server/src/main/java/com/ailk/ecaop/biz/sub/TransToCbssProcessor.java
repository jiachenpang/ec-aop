package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.sys.EcAopServerSysException;
import org.n3r.ecaop.core.log.elk.ELKAopLogger;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.N25ChgToCbssProduct;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.alibaba.fastjson.JSON;

/**
 * 移网23转4新流程
 * @author Administrator
 */
@EcRocTag("TransToCBSSProcessor")
public class TransToCbssProcessor extends BaseAopProcessor implements ParamsAppliable {

    LanOpenApp4GDao dao = new LanOpenApp4GDao();
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[4];
    // 调CB获取ID,预提交,合约转换,23转4校验
    private static final String[] PARAM_ARRAY = { "ecaop.masb.chph.gifa.ParametersMapping",
            "ecaop.trades.sccc.cancelPre.paramtersmapping", "ecaop.trades.contractTransFer.ParametersMapping",
            "ecaop.23To4.check.ParametersMapping" };
    private static ArrayList<String> modecodes = new ArrayList<String>(Arrays.asList("nmpr", "bjsb", "tjpr", "sdpr",
            "hpsb", "sxpr", "ahpre", "shpre", "jspr", "zjpre", "fjpre", "hipre", "gdps", "ussb", "qhpr", "hupr",
            "hnpr", "jxpre", "hapr", "xzpre", "scpr", "cqps", "snpre", "gzpre", "ynpre", "gspr", "nxpr", "xjpr",
            "jlpr", "mppln", "hlpr"));

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        msg.put("apptx", body.get("apptx"));

        String method = exchange.getMethodCode();
        msg.put("23to4methodcode", method);// 接口短名
        msg.put("eparchyCode", new ChangeCodeUtils().changeEparchyUnStatic(msg));// 转换成cb地市
        // 从CB获取purchaseItemId
        msg.put("purchaseItemId", GetSeqUtil
                .getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, msg), "seq_item_id", 1).get(0) + "");
        String serviceClassCode = getNetTypeCode(msg);
        // 掉省份23转4接口查询相关信息并塞入msg
        check23To4(msg, exchange, serviceClassCode);
        // cb合约转换接口
        qryCbssContractTrans(msg, exchange);
        long start = System.currentTimeMillis();
        preData(msg, exchange);
        Map ext = preData4Ext(msg);
        Map base = preData4Base(msg);
        Map preDataMap = preData4PreSub(ext, base, msg);
        MapUtils.arrayPut(preDataMap, msg, MagicNumber.COPYARRAY);
        preDataMap.put("city", msg.get("eparchyCode"));
        body.put("msg", preDataMap);
        exchange.getIn().setBody(body);
        System.out.println(body.get("apptx") + "time,23转4准备预提交参数用时:" + (System.currentTimeMillis() - start));
        long useTime = System.currentTimeMillis() - start;
        if (useTime >= 2000) {
            ELKAopLogger.logStr("wangmccheck23to4:" + body.get("apptx") + ",useTime:" + useTime);
        }
        start = System.currentTimeMillis();
        try {
            LanUtils lan = new LanUtils();
            lan.preData(pmp[1], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
            lan.xml2Json("ecaop.trades.sccc.cancelPre.template", exchange);
        }
        catch (Exception e) {
            System.out.println(body.get("apptx") + "time,调用cb23转4预提交用时:" + (System.currentTimeMillis() - start));
            throw new EcAopServerBizException("9999", "调用预提交失败！" + e.getMessage());
        }
        System.out.println(body.get("apptx") + "time,调用cb23转4预提交用时:" + (System.currentTimeMillis() - start));
        // 处理返回
        dealReturn(exchange, msg);

    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void dealReturn(Exchange exchange, Map msg) {
        Map out = exchange.getOut().getBody(Map.class);
        Map realOut = new HashMap();
        List<Map> rspInfo = (List<Map>) out.get("rspInfo");
        if (null == rspInfo || rspInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "cBSS统一预提交未返回应答信息");
        }
        // 记录trade表 正式提交的时候需要IS_REMOTE为3修改payTag 为1
        msg.put("tradeId", rspInfo.get(0).get("provOrderId"));
        msg.put("IS_REMOTE", "3");
        msg.put("remark", "3");// 存在终端时 正式提交需要用到
        new TradeManagerUtils().insert2CBSSTrade2324(msg);
        realOut.put("bssOrderId", rspInfo.get(0).get("bssOrderId"));
        realOut.put("provOrderId", rspInfo.get(0).get("provOrderId"));
        Integer totalFee = 0;
        for (Map rspMap : rspInfo) {
            List<Map> provinceOrderInfo = (List<Map>) rspMap.get("provinceOrderInfo");
            if (null == provinceOrderInfo || provinceOrderInfo.isEmpty()) {
                continue;
            }
            // 费用计算
            for (Map provinceOrder : provinceOrderInfo) {
                totalFee = totalFee + Integer.valueOf(provinceOrder.get("totalFee").toString()) * 10;
                List<Map> preFeeInfoRsp = (List<Map>) provinceOrder.get("preFeeInfoRsp");
                if (null == preFeeInfoRsp || preFeeInfoRsp.isEmpty()) {
                    continue;
                }
                List<Map> feeList = dealFee(preFeeInfoRsp);
                realOut.put("feeInfo", feeList);
            }
            realOut.put("totalFee", totalFee + "");
        }
        System.out.println("流量包订购CB返回处理结果realOut：" + realOut.toString());
        exchange.getOut().setBody(realOut);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
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
            retFee.put("maxRelief", Integer.valueOf("" + fee.get("maxDerateFee")) * 10 + "");
            retFee.put("origFee", Integer.valueOf("" + fee.get("fee")) * 10 + "");
            retFeeList.add(retFee);
        }
        return retFeeList;
    }

    /**
     * CBSS合约转换接口
     * @param msg
     * @param exchange
     */
    private void qryCbssContractTrans(Map msg, Exchange exchange) {
        long start = System.currentTimeMillis();
        Map oldUser = (Map) msg.get("oldUser");
        Map contractInfo = (Map) oldUser.get("contractInfo");// 23转4的合约信息
        if (null == contractInfo || contractInfo.isEmpty()) {
            return;
        }
        String endTime = (String) contractInfo.get("endTime");// 原合约结束时间
        if (IsEmptyUtils.isEmpty(endTime)) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "23转4校验未返回合约的结束时间!");
        }
        if (endTime.compareTo(GetDateUtils.getNextMonthFirstDayFormat()) <= 0) {
            return;
        }
        List<Map> userContractInfo = new ArrayList<Map>();
        Map userContractMap = new HashMap();
        // 准备cb合约转换接口参数
        userContractMap.put("originalComboFee", contractInfo.get("originalComboFee"));
        userContractMap.put("comboFee", contractInfo.get("comboFee"));
        userContractMap.put("productType", contractInfo.get("productType"));
        userContractMap.put("presentNum", contractInfo.get("presentNum"));
        userContractMap.put("assureTypeCode", contractInfo.get("assureTypeCode"));
        userContractMap.put("assureId", contractInfo.get("assureId"));
        userContractMap.put("assureName", contractInfo.get("assureName"));
        userContractMap.put("assureDescribe", contractInfo.get("assureDescribe"));
        userContractMap.put("startTime", contractInfo.get("startTime"));
        userContractMap.put("endTime", contractInfo.get("endTime"));
        userContractMap.put("contractDescribe", contractInfo.get("contractDescribe"));
        Map equipmentInfo = (Map) contractInfo.get("equipmentInfo");
        if ("1|2".contains((String) contractInfo.get("productType"))
                && (null == equipmentInfo || equipmentInfo.isEmpty())) {
            throw new EcAopServerBizException("9999", "BSS 23转4校验接口未返回设备信息");
        }
        Map convertInfo = (Map) contractInfo.get("convertInfo");
        Map contractDeveloperInfo = (Map) contractInfo.get("contractDeveloperInfo");
        userContractMap.put("equipmentInfo", equipmentInfo);
        userContractMap.put("convertInfo", convertInfo);
        userContractMap.put("contractDeveloperInfo", contractDeveloperInfo);
        userContractInfo.add(userContractMap);
        Map preData4Contract = new HashMap();
        MapUtils.arrayPut(preData4Contract, msg, MagicNumber.COPYARRAY);
        preData4Contract.put("contractInfo", userContractInfo);
        Exchange contractTransExchange = ExchangeUtils.ofCopy(exchange, preData4Contract);
        try {
            new LanUtils().preData(pmp[2], contractTransExchange);
            CallEngine.wsCall(contractTransExchange,
                    "ecaop.comm.conf.url.cbss.services.CheckUserContractTransferAopSer");// cb合约转换地址
            new LanUtils().xml2Json("ecaop.trades.contractTransFer.template", contractTransExchange);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用CBSS合约转换接口失败！" + e.getMessage());
        }
        Map retTransFer = contractTransExchange.getOut().getBody(Map.class);
        List<Map> hyContractInfo = (List<Map>) retTransFer.get("hyContractInfo");
        if (null == hyContractInfo || hyContractInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "CBSS合约转换接口未返回信息！");
        }
        System.out.println(msg.get("apptx") + "time,23转4调用CBSS合约转换接口用时:" + (System.currentTimeMillis() - start));
        msg.put("hyContractInfo", hyContractInfo);

    }

    private Map preData4PreSub(Map ext, Map base, Map msg) {
        Map preDataMap = new HashMap();
        preDataMap.put("base", base);
        preDataMap.put("ext", ext);
        preDataMap.put("ordersId", msg.get("tradeId"));
        preDataMap.put("serinalNamber", msg.get("serinalNamber"));
        preDataMap.put("operTypeCode", "0");
        return preDataMap;
    }

    /**
     * 整合参数
     * @param msg
     * @param exchange
     */
    private void preData(Map msg, Exchange exchange) {
        String appCode = exchange.getAppCode();
        Map copyMap = new HashMap();
        MapUtils.arrayPut(copyMap, msg, MagicNumber.COPYARRAY);
        boolean isExistCust = false;
        String oldCustId = "";
        List<Map> customerInfo = (List<Map>) msg.get("customerInfo");
        if (null == customerInfo || customerInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "未传客户信息");
        }
        if (StringUtils.isNotEmpty((String) customerInfo.get(0).get("custId"))) {
            isExistCust = true;
            oldCustId = (String) customerInfo.get(0).get("custId");
        }
        String tradeId = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, copyMap),
                "seq_trade_id", 1).get(0);
        String payRelId = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, copyMap),
                "SEQ_PAYRELA_ID", 1).get(0);
        String custId = isExistCust ? oldCustId : (String) GetSeqUtil.getSeqFromCb(pmp[0],
                ExchangeUtils.ofCopy(exchange, copyMap), "seq_cust_id", 1).get(0);
        String userId = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, copyMap),
                "seq_user_id", 1).get(0);
        String acctId = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, copyMap),
                "seq_acct_id", 1).get(0);
        String cycId = GetDateUtils.getNextMonthFirstDayFormat().substring(0, 6);
        String realStartDate = GetDateUtils.getNextMonthDate();// 19位
        Map idMap = MapUtils.asMap("tradeId", tradeId, "payRelId", payRelId, "custId", custId, "userId", userId,
                "acctId", acctId, "cycId", cycId, "appCode", appCode, "realStartDate", realStartDate);
        msg.putAll(idMap);
    }

    private Map preData4Base(Map msg) {
        Map base = new HashMap();
        String inModeCode = "";
        // 区县不为空且大于6位时截取。
        String cityCode = (String) msg.get("district");
        if (null != cityCode && cityCode.length() > 6) {
            cityCode = cityCode.substring(0, 6);
        }
        if (modecodes.contains(msg.get("appCode"))) {
            inModeCode = "X";
        }
        else {
            inModeCode = "E";
        }
        base.put("inModeCode", inModeCode);
        base.put("tradeTypeCode", "0440");
        base.put("eparchyCode", msg.get("eparchyCode"));
        base.put("areaCode", msg.get("eparchyCode"));
        base.put("tradeStaffId", msg.get("operatorId"));
        base.put("tradeDepartId", msg.get("channelId"));
        base.put("subscribeId", msg.get("tradeId"));
        base.put("tradeId", msg.get("tradeId"));
        base.put("userDiffCode", "00");
        base.put("brandCode", msg.get("mBrandCode"));// TODO:
        base.put("netTypeCode", "50");
        base.put("serinalNamber", msg.get("serialNumber"));// 50行，将号码塞入msg
        base.put("productId", msg.get("mProductId"));// TODO:
        base.put("custId", msg.get("custId"));
        base.put("usecustId", msg.get("custId"));
        base.put("userId", msg.get("userId"));
        base.put("custName", msg.get("custName"));// 从23转4获取
        base.put("acctId", msg.get("acctId"));
        base.put("acceptDate", GetDateUtils.getDate());
        base.put("cityCode", cityCode);// tradeCityCode和cityCode映射一致，存在问题
        base.put("feeState", "0");
        base.put("contact", msg.get("custName"));// 从23转4获取
        base.put("contactPhone", msg.get("serialNumber"));// 从23转4获取
        base.put("contactAddress", msg.get("linkAddress"));// 从23转4获取
        base.put("termIp", "0.0.0.0");
        base.put("oldFee", "");
        base.put("fee", "");
        base.put("mainDiscntCode", "");
        base.put("nextDealTag", "Z");
        base.put("standardKindCode", "");

        return base;
    }

    private Map preData4Ext(Map msg) throws Exception {
        long start = System.currentTimeMillis();
        System.out.println("maly122接口短名为" + msg.get("23to4methodcode"));
        // 处理产品
        // new ChangeToCbssProduct().getProductInfo(msg);
        new N25ChgToCbssProduct().getProductInfo(msg);
        System.out.println(msg.get("apptx") + "time,23转4准备产品时间用时:" + (System.currentTimeMillis() - start));
        start = System.currentTimeMillis();
        // 处理终端费用、号码以及各种属性
        dealTmlAndNumFee(msg);
        System.out.println(msg.get("apptx") + "time,23转4处理终端号码等属性信息用时:" + (System.currentTimeMillis() - start));
        start = System.currentTimeMillis();
        // 处理从cbss返回的合约信息
        dealContractInfo(msg);
        System.out.println(msg.get("apptx") + "time,23转4处理cbss返回的合约用时:" + (System.currentTimeMillis() - start));
        // 23转4，针对国际长途，漫游用户，不收取押金需求
        start = System.currentTimeMillis();
        InternationalNoDeposit(msg);
        System.out.println(msg.get("apptx") + "time,23转4国际长途漫游用户不收取押金用时:" + (System.currentTimeMillis() - start));
        start = System.currentTimeMillis();
        Map ext = preData4Item(msg);
        System.out.println(msg.get("apptx") + "time,23转4拼装ext节点用时:" + (System.currentTimeMillis() - start));
        return ext;
    }

    private void dealContractInfo(Map msg) throws Exception {
        List<Map> hyContractInfo = (List<Map>) msg.get("hyContractInfo");
        if (null == hyContractInfo || hyContractInfo.isEmpty()) {
            return;
        }
        List<Map> hyProductInfo = (List<Map>) hyContractInfo.get(0).get("hyProductInfo");
        if (null == hyProductInfo || hyProductInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "CBSS合约转换接口未返回产品信息");
        }
        List<Map> hyTradeItemInfo = (List<Map>) hyContractInfo.get(0).get("hyTradeItemInfo");
        if (null == hyTradeItemInfo || hyTradeItemInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "CBSS合约转换接口未返回tradeItem信息");
        }
        List<Map> hyPurchaseInfo = (List<Map>) hyContractInfo.get(0).get("hyPurchaseInfo");
        if (null == hyPurchaseInfo || hyPurchaseInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "CBSS合约转换接口未返回purchase信息");
        }
        List<Map> productTypeList = (List<Map>) msg.get("productTypeList");
        Map productType = new HashMap();
        productType.put("productMode", "50");
        productType.put("productId", hyProductInfo.get(0).get("hyProductId"));
        productType.put("productTypeCode", hyProductInfo.get(0).get("hyProductType"));
        productType.put("hyStartDate", hyProductInfo.get(0).get("startDate"));
        productType.put("hyEndDate", hyProductInfo.get(0).get("endDate"));
        productTypeList.add(productType);
        msg.put("productTypeList", productTypeList);
        List<Map> productList = (List<Map>) msg.get("productList");
        Map product = new HashMap();
        product.put("productMode", "50");
        product.put("productId", hyProductInfo.get(0).get("hyProductId"));
        product.put("hyStartDate", hyProductInfo.get(0).get("startDate"));
        product.put("hyEndDate", hyProductInfo.get(0).get("endDate"));
        productList.add(product);
        msg.put("productList", productList);
        List<Map> discntList = (List<Map>) msg.get("discntList");
        Map discnt = new HashMap();
        discnt.put("productId", hyProductInfo.get(0).get("hyProductId"));
        discnt.put("packageId", hyProductInfo.get(0).get("hyPackageId"));
        discnt.put("discntCode", hyProductInfo.get(0).get("hyDiscntCode"));
        discnt.put("hyStartDate", hyProductInfo.get(0).get("startDate"));
        discnt.put("hyEndDate", hyProductInfo.get(0).get("endDate"));
        discntList.add(discnt);
        msg.put("discntList", discntList);

        // tradeOther
        Map oldUser = (Map) msg.get("oldUser");// 省份23转4校验返回老用户信息
        List<Map> tradeOtherList = new ArrayList<Map>();
        Map tradeOtherMap = new HashMap();
        tradeOtherMap.put("xDatatype", "NULL");
        tradeOtherMap.put("rsrvValueCode", "SFHY");
        tradeOtherMap.put("rsrvValue", msg.get("province"));
        tradeOtherMap.put("rsrvStr1", hyProductInfo.get(0).get("startDate").toString().substring(0, 6));
        tradeOtherMap.put("rsrvStr2", hyProductInfo.get(0).get("endDate").toString().substring(0, 6));
        Map contractMap = (Map) oldUser.get("contractInfo");
        if (null != contractMap && !contractMap.isEmpty()) {
            Map convertInfo = (Map) contractMap.get("convertInfo");
            if (null != convertInfo && !convertInfo.isEmpty()) {
                tradeOtherMap.put("rsrvStr3", convertInfo.get("freezeSurplusFee"));
                tradeOtherMap.put("rsrvStr5", convertInfo.get("surplusMonths"));
            }
            tradeOtherMap.put("rsrvStr4", contractMap.get("presentNum"));
            tradeOtherMap.put("rsrvStr8", contractMap.get("productId"));
            tradeOtherMap.put("rsrvStr9", contractMap.get("contractDescribe"));
            tradeOtherMap.put("rsrvStr10", contractMap.get("assureTypeCode"));
            tradeOtherMap.put("startDate", contractMap.get("startTime"));
            tradeOtherMap.put("endDate", contractMap.get("endTime"));
        }
        tradeOtherMap.put("rsrvStr6", "0");
        tradeOtherMap.put("rsrvStr7", "0");
        tradeOtherMap.put("modifyTag", "0");
        tradeOtherMap.put("rsrvStr11", "");
        tradeOtherMap.put("rsrvStr12", "");
        tradeOtherMap.put("rsrvStr13", "");
        tradeOtherMap.put("rsrvStr16", oldUser.get("comboFee"));
        tradeOtherMap.put("rsrvStr17", hyProductInfo.get(0).get("hyProductType"));
        tradeOtherMap.put("rsrvStr18", "");
        tradeOtherMap.put("rsrvStr19", "");
        tradeOtherMap
                .put("rsrvStr20",
                        GetDateUtils.diffMonths((String) contractMap.get("endTime"),
                                (String) contractMap.get("startTime") + 1));
        tradeOtherMap.put("rsrvStr22", oldUser.get("comboFee"));
        tradeOtherMap.put("rsrvStr23", hyProductInfo.get(0).get("hyDiscntCode"));
        tradeOtherList.add(tradeOtherMap);
        msg.put("tradeOtherList", tradeOtherList);
        // tradeItem属性下发
        List<Map> itemList = (List<Map>) msg.get("itemList");
        // tradeSubItem属性下发
        List<Map> subItemList = (List<Map>) msg.get("subItemList");
        Map appParam = new HashMap();
        Map<String, Object> appendMap = new HashMap<String, Object>();
        appendMap.put("NEW_PRESENT_RULE", hyTradeItemInfo.get(0).get("newPresentRule"));
        appendMap.put("deviceImei", hyPurchaseInfo.get(0).get("deviceimei"));
        appendMap.put("devicebrand", hyPurchaseInfo.get(0).get("devicebrand"));
        appendMap.put("deviceno", hyPurchaseInfo.get(0).get("deviceno"));
        appendMap.put("ASSURE_TYPE", hyPurchaseInfo.get(0).get("assureIype"));
        appendMap.put("SALE_PRODUCT_LIST", hyPurchaseInfo.get(0).get("saleProductList"));
        appendMap.put("holdUnitType", hyPurchaseInfo.get(0).get("holdunittype"));
        appendMap.put("IS_SFHY_DEVICE", "1");

        appParam.put("TRADE_TYPE_CODE", "440");
        appParam.put("NET_TYPE_CODE", "ZZ");// 默认ZZ
        appParam.put("BRAND_CODE", "ZZZZ");// 默认ZZZZ
        appParam.put("PRODUCT_ID", "-1");// 默认-1
        appParam.put("EPARCHY_CODE", "ZZZZ");// 默认ZZZZ
        appParam.put("PROVINCE_CODE", "ZZZZ");// 默认ZZZZ
        List<Map> appendMapResult = dao.queryAppendParam(appParam);
        for (int n = 0; n < appendMapResult.size(); n++) {
            String attrTypeCode = String.valueOf(appendMapResult.get(n).get("ATTR_TYPE_CODE"));
            String attrValue = String.valueOf(appendMapResult.get(n).get("ATTR_VALUE"));
            String attrcode = String.valueOf(appendMapResult.get(n).get("ATTR_CODE"));
            for (String key : appendMap.keySet()) {
                if (attrValue.endsWith(key) && "I".equals(attrTypeCode)) {
                    Map item = new HashMap();
                    item.put("xDatatype", "NULL");
                    item.put("attrValue", appendMap.get(key));
                    item.put("attrCode", attrcode);
                    itemList.add(item);
                }
                if (attrValue.endsWith(key) && !"I".equals(attrTypeCode)) {
                    Map item = new HashMap();
                    item.put("attrTypeCode", attrTypeCode);
                    item.put("xDatatype", "NULL");
                    item.put("itemId", msg.get("userId"));
                    item.put("attrValue", appendMap.get(key));
                    item.put("attrCode", attrcode);
                    if ("234o".equals(msg.get("23to4methodcode"))) {// 针对23转4开户信息预提交接口添加开始和结束时间 by maly 171102
                        item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                        item.put("endDate", "20501231235959");
                    }
                    subItemList.add(item);
                }
            }
        }
        msg.put("itemList", itemList);
        msg.put("subItemList", subItemList);

    }

    /**
     * 处理号码和终端费用以及属性
     * @param msg
     * @throws Exception
     */

    private void dealTmlAndNumFee(Map msg) throws Exception {
        Map oldAcct = (Map) msg.get("oldAcct");
        Map oldUser = (Map) msg.get("oldUser");
        Map oldCust = (Map) msg.get("oldCust");
        List<Map> userInfo = (List<Map>) msg.get("userInfo");
        List<Map> subItemList = new ArrayList<Map>();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> resourceItemInfo = (List<Map>) msg.get("subItemList");
        // 带终端的合约开户时，处理终端属性
        if (null != resourceItemInfo && !resourceItemInfo.isEmpty()) {
            subItemList.addAll(resourceItemInfo);
        }
        // 下发终端费用
        List<Map> tradeFeeItemList = new ArrayList<Map>();
        String resourceFee = "";
        if (null != userInfo && !userInfo.isEmpty()) {
            List<Map> activityInfo = (List<Map>) userInfo.get(0).get("activityInfo");
            if (null != activityInfo && !activityInfo.isEmpty()) {
                resourceFee = (String) activityInfo.get(0).get("resourcesFee");
            }
            if (StringUtils.isNotEmpty(resourceFee)) {
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
                tradeFeeItemList.add(tradeFeeItem);
            }
        }
        // 从23转4获取靓号信息，处理下发cb
        String niceItemId = TradeManagerUtils.getSequence(msg.get("eparchyCode") + "", "SEQ_ITEM_ID");
        if (null != oldUser && !oldUser.isEmpty()) {
            List<Map> superNumber = (List<Map>) oldUser.get("superNumber");
            if (null != superNumber && !superNumber.isEmpty()) {
                Map discntItem = new HashMap();
                discntItem.put("id", msg.get("userId"));
                discntItem.put("idType", "1");
                discntItem.put("productId", "-1");
                discntItem.put("packageId", "-1");
                discntItem.put("discntCode", "88888888");
                discntItem.put("specTag", "0");
                discntItem.put("relationTypeCode", "");
                discntItem.put("itemId", niceItemId);
                discntItem.put("modifyTag", "0");
                discntItem.put("startDate", GetDateUtils.transDate((String) superNumber.get(0).get("startDate"), 19));
                discntItem.put("endDate", GetDateUtils.transDate((String) superNumber.get(0).get("finishDate"), 19));
                discntItem.put("userIdA", "-1");
                discntItem.put("xDatatype", "NULL");
                msg.put("numberDiscnt", discntItem);
                // 低消属性下发
                Map lowCostItem = new HashMap();
                lowCostItem.put("attrTypeCode", "3");
                lowCostItem.put("attrCode", "lowCost");
                lowCostItem.put("itemId", niceItemId);
                lowCostItem.put("attrValue", superNumber.get(0).get("monthMinConsume"));
                lowCostItem.put("xDatatype", "NULL");
                lowCostItem.put("startDate", GetDateUtils.transDate((String) superNumber.get(0).get("startDate"), 19));
                lowCostItem.put("endDate", GetDateUtils.transDate((String) superNumber.get(0).get("finishDate"), 19));
                subItemList.add(lowCostItem);
            }

        }
        // 处理subitem和item节点属性，属性值key、value在表中配置
        Map appParam = new HashMap();
        Map<String, Object> appendMap = new HashMap<String, Object>();
        appParam.put("TRADE_TYPE_CODE", "440");
        appParam.put("NET_TYPE_CODE", "ZZ");// 默认ZZ
        appParam.put("BRAND_CODE", "ZZZZ");// 默认ZZZZ
        appParam.put("PRODUCT_ID", "-1");// 默认-1
        appParam.put("EPARCHY_CODE", "ZZZZ");// 默认ZZZZ
        appParam.put("PROVINCE_CODE", "ZZZZ");// 默认ZZZZ
        String cityCode = (String) msg.get("district");
        if (null != cityCode && cityCode.length() > 6) {
            cityCode = cityCode.substring(0, 6);
        }
        // 密码
        String newUserPwd = "";
        if (null != userInfo && !userInfo.isEmpty()) {
            newUserPwd = (String) userInfo.get(0).get("userPwd");
        }
        if (StringUtils.isEmpty(newUserPwd)) {
            newUserPwd = String.valueOf((int) ((Math.random() * 9 + 1) * 100000));
        }
        String userPwd = ((String) msg.get("serialNumber")).substring(((String) msg.get("serialNumber")).length() - 6);
        if (StringUtils.isNotEmpty(newUserPwd)) {
            userPwd = newUserPwd;
        }
        appendMap.put("CITY_CODE", cityCode);
        appendMap.put("EPARCHY_CODE", msg.get("eparchyCode"));
        appendMap.put("USER_PASSWD", userPwd);
        appendMap.put("OLD_ACCT_ID_SF", oldAcct.get("acctId"));// 省份帐户
        appendMap.put("OLD_COMBO_FEE", oldUser.get("comboFee"));// 省份产品抵消值(分)
        appendMap.put("OLD_COMBO_NAME", oldUser.get("comboName"));// 省份产品名称
        appendMap.put("OLD_CUST_ID_SF", oldCust.get("custId"));// 省份客户ID
        appendMap.put("OLD_NET_TYPE_CODE_SF", oldUser.get("serviceClassCode"));// 原网别
        appendMap.put("OLD_USER_ID_SF", oldUser.get("userId"));// 省份实例ID
        appendMap.put("IS_TRANSMIT", "1");// 1
        appendMap.put("DEVELOP_STAFF_ID", oldUser.get("developStaffId"));// 发展员工
        appendMap.put("DEVELOP_DEPART_ID", oldUser.get("developDepartId"));// 发展部门
        appendMap.put("MAIN_PRODUCT_ID", msg.get("mProductId"));// 主产品ID
        appendMap.put("E_IN_MODE", msg.get("eModeCode"));
        // 23转4添加使用人信息
        if ("1".equals(oldCust.get("custType")) || "01".equals(oldCust.get("custType"))) {
            List<Map> paraInfo = (List<Map>) msg.get("oldPara");
            if (null != paraInfo && !paraInfo.isEmpty()) {
                for (Map para : paraInfo) {
                    if ("USE_CUST_NAME|USE_CUST_PSPT_TYPE|USE_CUST_PSPT_CODE|USE_CUST_ADDRESS|USE_CUST_MARK"
                            .contains((String) para.get("paraId"))) {
                        appendMap.put(para.get("paraId").toString(), para.get("paraValue"));
                    }
                }
            }
        }
        List<Map> appendMapResult = dao.queryAppendParam(appParam);
        for (int n = 0; n < appendMapResult.size(); n++) {
            String attrTypeCode = String.valueOf(appendMapResult.get(n).get("ATTR_TYPE_CODE"));
            String attrValue = String.valueOf(appendMapResult.get(n).get("ATTR_VALUE"));
            String attrcode = String.valueOf(appendMapResult.get(n).get("ATTR_CODE"));
            for (String key : appendMap.keySet()) {
                if (attrValue.endsWith(key) && "I".equals(attrTypeCode)) {
                    Map item = new HashMap();
                    item.put("xDatatype", "NULL");
                    item.put("attrValue", appendMap.get(key));
                    item.put("attrCode", attrcode);
                    itemList.add(item);
                }
                if (attrValue.endsWith(key) && !"I".equals(attrTypeCode)) {// attrTypeCode为0 使用userId
                    Map item = new HashMap();
                    item.put("attrTypeCode", attrTypeCode);
                    item.put("xDatatype", "NULL");
                    item.put("itemId", msg.get("userId"));
                    item.put("attrValue", appendMap.get(key));
                    item.put("attrCode", attrcode);
                    if ("234o".equals(msg.get("23to4methodcode"))) {// 针对23转4开户信息预提交接口添加开始和结束时间 by maly 171102
                        item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                        item.put("endDate", "20501231235959");
                    }
                    subItemList.add(item);
                }
            }
        }
        msg.put("subItemList", subItemList);
        msg.put("itemList", itemList);
        msg.put("tradeFeeItemList", tradeFeeItemList);

    }

    private Map preData4Item(Map msg) throws Exception {
        List<Map> customerInfo = (List<Map>) msg.get("customerInfo");
        String custId = (String) customerInfo.get(0).get("custId");
        Map ext = new HashMap();
        System.out.println("此时此刻的msg" + msg);
        // 客户id不存在时，创建客户信息
        if (StringUtils.isEmpty(custId)) {
            ext.put("tradeCustomer", preCustomerData(msg));
            ext.put("tradeCustPerson", preCustPerData(msg));
        }
        ext.put("tradeUser", preUserData(msg));
        ext.put("tradeProductType", preProductTpyeListData(msg));
        ext.put("tradeProduct", preProductData(msg));
        ext.put("tradeDiscnt", preDiscntData(msg));
        ext.put("tradeSvc", preTradeSvcData(msg));
        ext.put("tradeSp", preTradeSpData(msg));
        ext.put("tradeElement", preDataUtil(msg, "elementList"));
        ext.put("tradeAcctConsign", preTradeAcctConsignData(msg));
        ext.put("tradeAcct", preTradeAcctData(msg));
        ext.put("tradePayrelation", preTradePayRelData(msg));
        ext.put("tradePost", preTradePostItem(msg));
        ext.put("tradeItem", preDataUtil(msg, "itemList"));
        ext.put("tradeSubItem", preDataUtil(msg, "subItemList"));
        ext.put("tradefeeSub", preDataUtil(msg, "tradeFeeItemList"));
        ext.put("tradeOther", preDataUtil(msg, "tradeOtherList"));
        ext.put("tradePurchase", preDataUtil(msg, "tradePurchaseList"));
        System.out.println("拼接后的报文" + ext);
        return ext;
    }

    private Map preDataUtil(Map msg, String tradeinfo) {
        List<Map> tradeinfoList = (List<Map>) msg.get(tradeinfo);
        Map tradeMap = new HashMap();
        if (!IsEmptyUtils.isEmpty(tradeinfoList)) {
            tradeMap.put("item", tradeinfoList);
        }
        return tradeMap;
    }

    /**
     * 23转4针对国际长途，漫游，不收取押金需求
     * @param msg
     * @param tradeinfoList
     */
    private void InternationalNoDeposit(Map msg) {
        // 取出服务50011的itemId
        List<Map> svcList = (List<Map>) msg.get("svcList");
        String itemId = String.valueOf(msg.get("userId"));
        if (!IsEmptyUtils.isEmpty(svcList)) {
            for (Map svc : svcList) {
                if ("50011".equals(svc.get("serviceId")) && !IsEmptyUtils.isEmpty(svc.get("serviceId"))) {
                    itemId = (String) svc.get("serviceId");
                }
            }
        }
        LanUtils lan = new LanUtils();
        List<Map> tradeinfoList = (List<Map>) msg.get("subItemList");
        Map oldUser = (Map) msg.get("oldUser");
        String prepayTag = (String) oldUser.get("subscrbType");
        List<Map> subServInfos = (List<Map>) msg.get("subServInfos");
        // 当预付费标识为0，1时，继承服务，判断是否收取押金，否则不继承
        if ("0,1".contains(prepayTag) && null != subServInfos && subServInfos.size() > 0) {
            for (Map ss : subServInfos) {
                List<Map> subServInfo = (List<Map>) ss.get("subServInfo");
                for (Map s : subServInfo) {
                    List<Map> servPrptyInfos = (List<Map>) s.get("servPrptyInfos");
                    if (null != servPrptyInfos && servPrptyInfos.size() > 0) {
                        for (Map sp : servPrptyInfos) {
                            List<Map> servPrptyInfo = (List<Map>) sp.get("servPrptyInfo");
                            for (Map spi : servPrptyInfo) {
                                // 当特服信息下的prptyValueCode为ISGM并且prptyValue为1时，收取押金，下发tradeSubitem下面isHerited为2
                                if ("ISGM".equals(spi.get("prptyValueCode")) && "1".equals(spi.get("prptyValue"))) {
                                    tradeinfoList.add(lan.createTradeSubItemB(itemId, "isHerited", "2",
                                            (String) msg.get("realStartDate"), "2050-12-31 23:59:59"));

                                }
                                // 否则下发1
                                else if ("ISGM".equals(spi.get("prptyValueCode")) && "0".equals(spi.get("prptyValue"))) {
                                    tradeinfoList.add(lan.createTradeSubItemB(itemId, "isHerited", "1",
                                            (String) msg.get("realStartDate"), "2050-12-31 23:59:59"));
                                }
                            }
                        }
                    }
                }
            }
        }
        msg.put("subItemList", tradeinfoList);
    }

    private Map preTradePostItem(Map msg) {
        Map tradePost = new HashMap();
        Map item = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map postInfo = (Map) msg.get("postInfo");
        if (null != postInfo && !postInfo.isEmpty() && "1".equals(postInfo.get("postTag"))) {
            item.put("id", msg.get("userId"));
            item.put("idType", postInfo.get("idType"));
            item.put("postName", postInfo.get("postName"));
            item.put("postTag", postInfo.get("postTag"));
            item.put("postContent", postInfo.get("postContent"));
            item.put("postTypeset", postInfo.get("postTypeset"));
            item.put("postCyc", postInfo.get("postCyc"));
            item.put("postAddress", postInfo.get("postAddress"));
            item.put("postCode", postInfo.get("postCode"));
            item.put("email", postInfo.get("email"));
            item.put("faxNbr", postInfo.get("faxNbr"));
            item.put("startDate", msg.get("realStartDate"));
            item.put("endDate", "2050-12-31 23:59:59");
            item.put("xDatatype", "NULL");
            itemList.add(item);
            tradePost.put("item", itemList);
        }
        return tradePost;
    }

    private Map preTradePayRelData(Map msg) {
        Map tradePayRel = new HashMap();
        Map item = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        item.put("xDatatype", "NULL");
        item.put("userId", msg.get("userId"));
        item.put("acctId", msg.get("acctId"));
        item.put("payrelationId", msg.get("payRelId"));
        item.put("startAcycId", msg.get("cycId"));
        item.put("payitemCode", "-1");
        item.put("acctPriority", "0");
        item.put("userPriority", "0");
        item.put("bindType", "1");
        item.put("endAcycId", "203712");
        item.put("defaultTag", "1");
        item.put("actTag", "1");
        item.put("limitType", "0");
        item.put("limit", "0");
        item.put("complementTag", "0");
        item.put("addupMonths", "0");
        item.put("addupMethod", "0");
        itemList.add(item);
        tradePayRel.put("item", itemList);
        return tradePayRel;
    }

    private Map preTradeAcctData(Map msg) {
        // 截取地市信息
        String cityCode = (String) msg.get("district");
        if (null != cityCode && cityCode.length() > 6) {
            cityCode = cityCode.substring(0, 6);
        }
        // 付费方式省份 23转4检验省份未返回托收信息则默认为0
        Map oldAcctMap = (Map) msg.get("oldAcct");
        List<Map> consignInfo = (List<Map>) oldAcctMap.get("consign");
        String payModeCode = "0";
        if (null != consignInfo && !consignInfo.isEmpty()) {
            payModeCode = (String) oldAcctMap.get("payModeCode");
        }
        Map tradeAcctMap = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("acctId", msg.get("acctId"));
        item.put("payName", msg.get("custName"));
        item.put("custId", msg.get("custId"));
        item.put("acctPasswd", "");
        item.put("payModeCode", payModeCode);
        item.put("scoreValue", "0");
        item.put("creditClassId", "0");
        item.put("basicCreditValue", "0");
        item.put("creditValue", "0");
        item.put("creditControlId", "0");
        item.put("openDate", GetDateUtils.getDate());
        item.put("eparchyCode", msg.get("eparchyCode"));
        item.put("cityCode", cityCode);
        item.put("removeTag", "0");
        item.put("xDatatype", "NULL");
        itemList.add(item);
        tradeAcctMap.put("item", itemList);
        return tradeAcctMap;
    }

    private Map preTradeAcctConsignData(Map msg) {
        Map oldAcctMap = (Map) msg.get("oldAcct");
        Map tradeAcctConsign = new HashMap();
        Map item = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> consignInfo = (List<Map>) oldAcctMap.get("consign");
        if (null != consignInfo && !consignInfo.isEmpty()) {
            Map consignMap = consignInfo.get(0);
            if (StringUtils.isNotEmpty((String) consignMap.get("bankCode"))
                    && StringUtils.isNotBlank((String) consignMap.get("bankCode"))
                    && StringUtils.isNotEmpty((String) consignMap.get("bankAcctNo"))
                    && StringUtils.isNotBlank((String) consignMap.get("bankAcctNo"))) {
                item.put("acctId", msg.get("acctId"));
                item.put("payModeCode", oldAcctMap.get("payModeCode"));
                item.put("consignMode", consignMap.get("consignMode"));
                item.put("bankAcctNo", consignMap.get("bankAcctNo"));
                item.put("bankAcctName", consignMap.get("bankAcctName"));
                item.put("superBankCode", consignMap.get("superBankCode"));
                item.put("agreementNo", consignMap.get("agreementNo"));
                item.put("acctBalanceID", consignMap.get("acctBalanceId"));
                item.put("bankCode", consignMap.get("bankCode"));
                item.put("assistantTag", "0");
                item.put("startAcycId", msg.get("cycId"));
                item.put("xDatatype", "NULL");
                List<Map> accountInfo = (List<Map>) oldAcctMap.get("accountInfo");
                if (accountInfo != null && accountInfo.size() > 0) {
                    item.put("bankBusiKind", accountInfo.get(0).get("bankCardType"));// 银行账号类型
                    item.put("bankAcctNo", accountInfo.get(0).get("accountLastFour"));// 银行卡号后四位
                    item.put("bankAcctName", accountInfo.get(0).get("bankAccountName"));// 银行账户户名
                }
            }
            itemList.add(item);
            tradeAcctConsign.put("item", itemList);
        }
        return tradeAcctConsign;
    }

    private Map preTradeSpData(Map msg) {
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
            item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);

            if (null != sp.get(i).get("activityStarTime")) {
                item.put("startDate", sp.get(i).get("activityStarTime"));
            }
            else {
                item.put("startDate", msg.get("realStartDate"));
            }
            if (null != sp.get(i).get("activityTime")) {
                item.put("endDate", sp.get(i).get("activityTime"));
            }
            else {
                item.put("endDate", "20501231235959");
            }
            item.put("itemId", TradeManagerUtils.getSequence((String) msg.get("eparchyCode"), "SEQ_ITEM_ID"));

            item.put("updateTime", GetDateUtils.getDate());
            item.put("remark", "");
            item.put("modifyTag", "0");
            item.put("payUserId", msg.get("userId"));
            item.put("spServiceId", sp.get(i).get("spServiceId"));
            item.put("userIdA", msg.get("userId"));
            item.put("xDatatype", "NULL");
            itemList.add(item);
        }
        tardeSp.put("item", itemList);
        return tardeSp;
    }

    private Map preTradeSvcData(Map msg) {
        Map svcList = new HashMap();
        List<Map> svc = new ArrayList<Map>();
        svc = (List<Map>) msg.get("svcList");
        List<Map> svList = new ArrayList();
        for (int i = 0; i < svc.size(); i++) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", msg.get("userId"));
            item.put("serviceId", svc.get(i).get("serviceId"));
            item.put("modifyTag", "0");

            // 下面特殊处理 国际业务
            Map sv = svc.get(i);
            String serviceId = "" + sv.get("serviceId");
            if ("50015".equals(serviceId) || "50011".equals(serviceId))// 国际漫游
            {
                for (int j = 0; j < svc.size(); j++) {
                    Map svj = svc.get(j);
                    String servicejId = "" + svj.get("serviceId");
                    if ("50014".equals(servicejId)) {
                        svc.remove(j);
                    }
                    if ("50010".equals(servicejId)) {
                        svc.remove(j);
                    }

                }

            }
            if (null != svc.get(i).get("activityStarTime")) {
                item.put("startDate", svc.get(i).get("activityStarTime"));
            }
            else {
                item.put("startDate", msg.get("realStartDate"));
            }
            if (null != svc.get(i).get("activityTime")) {
                item.put("endDate", svc.get(i).get("activityTime"));
            }
            else {
                item.put("endDate", "20501231235959");
            }
            item.put("itemId", TradeManagerUtils.getSequence((String) msg.get("eparchyCode"), "SEQ_ITEM_ID"));

            item.put("productId", svc.get(i).get("productId"));
            item.put("packageId", svc.get(i).get("packageId"));
            item.put("userIdA", "-1");
            svList.add(item);
        }
        svcList.put("item", svList);
        return svcList;
    }

    private Map preDiscntData(Map msg) {
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
            item.put("specTag", "0");
            item.put("relationTypeCode", "");
            if (null != discnt.get(i).get("activityStarTime")) {
                item.put("startDate", discnt.get(i).get("activityStarTime"));
            }
            else if (null != discnt.get(i).get("hyStartDate")) {
                item.put("startDate", discnt.get(i).get("hyStartDate"));
            }
            else {
                item.put("startDate", msg.get("realStartDate"));
            }
            if (null != discnt.get(i).get("activityTime")) {
                item.put("endDate", discnt.get(i).get("activityTime"));
            }
            else if (null != discnt.get(i).get("hyEndDate")) {
                item.put("endDate", discnt.get(i).get("hyEndDate"));
            }
            else {
                item.put("endDate", "20501231235959");
            }
            item.put("itemId", TradeManagerUtils.getSequence((String) msg.get("eparchyCode"), "SEQ_ITEM_ID"));
            // item.put("itemId", msg.get("subscribeId"));
            item.put("modifyTag", "0");
            itemList.add(item);
        }
        // 靓号资费
        Map numberDiscnt = (Map) msg.get("numberDiscnt");
        if (null != numberDiscnt && !numberDiscnt.isEmpty()) {
            Map item1 = new HashMap();
            item1.putAll(numberDiscnt);
            itemList.add(item1);
        }
        tradeDis.put("item", itemList);
        return tradeDis;
    }

    private Map preProductData(Map msg) throws Exception {
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
            item.put("itemId", TradeManagerUtils.getSequence((String) msg.get("eparchyCode"), "SEQ_ITEM_ID"));
            // item.put("itemId", "");
            item.put("modifyTag", "0");
            if ("50".equals(product.get(i).get("productMode"))) {
                if (null != product.get(i).get("activityStarTime")) {
                    item.put("startDate", GetDateUtils.transDate((String) product.get(i).get("activityStarTime"), 14));
                }
                else if (null != product.get(i).get("hyStartDate")) {
                    item.put("startDate", product.get(i).get("hyStartDate"));
                }
                else {
                    item.put("startDate", msg.get("realStartDate"));
                }
                if (null != product.get(i).get("activityTime")) {
                    item.put("endDate", GetDateUtils.transDate((String) product.get(i).get("activityTime"), 14));
                }
                else if (null != product.get(i).get("hyEndDate")) {
                    item.put("endDate", product.get(i).get("hyEndDate"));
                }
                else {
                    item.put("endDate", "20501231235959");
                }
            }
            else if ("01".equals(product.get(i).get("productMode"))) {
                if (null != product.get(i).get("activityStarTime")) {
                    item.put("startDate", GetDateUtils.transDate((String) product.get(i).get("activityStarTime"), 14));
                }
                else {
                    item.put("startDate", msg.get("realStartDate"));
                }
                if (null != product.get(i).get("activityTime")) {
                    item.put("endDate", GetDateUtils.transDate((String) product.get(i).get("activityTime"), 14));
                }
                else {
                    item.put("endDate", "20501231235959");
                }
            }
            else {
                item.put("startDate", msg.get("realStartDate"));
                item.put("endDate", "20501231235959");
            }
            item.put("userIdA", MagicNumber.DEFAULT_NO_VALUE);
            itemList.add(item);
        }

        tradeProduct.put("item", itemList);
        return tradeProduct;
    }

    private Map preProductTpyeListData(Map msg) throws Exception {
        Map tradeProductType = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> productTpye = (List<Map>) msg.get("productTypeList");
        for (int i = 0; i < productTpye.size(); i++) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", msg.get("userId"));
            item.put("productMode", productTpye.get(i).get("productMode"));
            item.put("productId", productTpye.get(i).get("productId"));
            item.put("productTypeCode", productTpye.get(i).get("productTypeCode"));
            item.put("modifyTag", "0");
            if ("50".equals(productTpye.get(i).get("productMode"))) {
                if (null != productTpye.get(i).get("activityStarTime")) {
                    item.put("startDate",
                            GetDateUtils.transDate((String) productTpye.get(i).get("activityStarTime"), 14));
                }
                else if (null != productTpye.get(i).get("hyStartDate")) {// cbss合约转换接口返回的合约
                    item.put("startDate", productTpye.get(i).get("hyStartDate"));
                }
                else {
                    item.put("startDate", msg.get("realStartDate"));
                }
                if (null != productTpye.get(i).get("activityTime")) {
                    item.put("endDate", GetDateUtils.transDate((String) productTpye.get(i).get("activityTime"), 14));
                }
                else if (null != productTpye.get(i).get("hyEndDate")) {
                    item.put("endDate", productTpye.get(i).get("hyEndDate"));
                }
                else {
                    item.put("endDate", "20501231235959");
                }
            }
            else if ("01".equals(productTpye.get(i).get("productMode"))) {
                if (null != productTpye.get(i).get("activityStarTime")) {
                    item.put("startDate",
                            GetDateUtils.transDate((String) productTpye.get(i).get("activityStarTime"), 14));
                }
                else {
                    item.put("startDate", msg.get("realStartDate"));
                }
                if (null != productTpye.get(i).get("activityTime")) {
                    item.put("endDate", GetDateUtils.transDate((String) productTpye.get(i).get("activityTime"), 14));
                }
                else {
                    item.put("endDate", "20501231235959");
                }
            }
            else {
                item.put("startDate", msg.get("realStartDate"));
                item.put("endDate", "20501231235959");
            }
            itemList.add(item);
        }
        tradeProductType.put("item", itemList);
        return tradeProductType;

    }

    private Map preUserData(Map msg) {

        List<Map> userInfo = (List<Map>) msg.get("userInfo");
        String serType = "";
        if (null != userInfo && !userInfo.isEmpty()) {
            serType = (String) userInfo.get(0).get("serType");
        }
        Map tradeUser = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("custId", msg.get("custId"));
        item.put("usecustId", msg.get("custId"));
        item.put("userId", msg.get("userId"));
        item.put("brandCode", msg.get("mBrandCode"));
        item.put("productId", msg.get("mProductId"));
        item.put("productTypeCode", msg.get("tradeUserProductTypeCode"));
        item.put("eparchyCode", msg.get("eparchyCode"));
        item.put("cityCode", msg.get("userCityCode"));
        item.put("userDiffCode", "00");
        item.put("userTypeCode", "0");
        item.put("serialNumber", msg.get("serialNumber"));
        item.put("netTypeCode", "50");
        item.put("scoreValue", "0");
        item.put("creditClass", msg.get("creditClass"));
        item.put("basicCreditValue", "0");
        item.put("creditValue", "0");
        item.put("acctTag", "0");
        item.put("prepayTag", "0");// 预付费标志写死后付费
        item.put("inDate", msg.get("oldInDate"));
        item.put("openDate", msg.get("oldInDate"));
        item.put("openMode", "0");
        item.put("openDepartId", msg.get("openDepartId"));
        item.put("developDepartId", msg.get("developDepartId"));
        item.put("inDepartId", msg.get("channelId"));
        // 推荐人编码
        if (StringUtils.isNotEmpty((String) msg.get("developStaffId"))) {
            item.put("developStaffId", msg.get("developStaffId"));
            item.put("inStaffId", msg.get("developStaffId"));
        }
        else {
            item.put("developStaffId", msg.get("operatorId"));
            item.put("inStaffId", msg.get("operatorId"));
        }
        item.put("openStaffId", msg.get("openStaffId"));
        item.put("removeTag", "0");
        item.put("userStateCodeset", "0");
        item.put("mputeMonthFee", "0");
        item.put("userPasswd",
                msg.get("serialNumber").toString().substring(msg.get("serialNumber").toString().length() - 6));
        item.put("xDatatype", "NULL");
        item.put("xDatatype", "NULL");
        itemList.add(item);
        tradeUser.put("item", itemList);
        return tradeUser;
    }

    private Map preCustPerData(Map msg) {
        String cityCode = (String) msg.get("district");
        if (null != cityCode && cityCode.length() > 6) {
            cityCode = cityCode.substring(0, 6);
        }
        Map tradeCustPerson = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("custId", msg.get("custId"));
        item.put("custName", msg.get("custName"));
        item.put("psptTypeCode", msg.get("certType"));
        item.put("psptId", msg.get("certCode"));
        item.put("psptEndDate", msg.get("certEndDate"));
        item.put("postCode", "");
        item.put("psptAddr", msg.get("certAddress"));
        item.put("postAddress", msg.get("linkAddress"));
        item.put("phone", msg.get("serialNumber"));
        item.put("homeAddress", "");
        item.put("email", "");
        item.put("sex", msg.get("sex"));
        item.put("contact", msg.get("custName"));
        item.put("contactPhone", msg.get("serialNumber"));
        item.put("birthday", "");
        item.put("job", "");
        item.put("workName", "");
        item.put("marriage", "");
        item.put("eparchyCode", msg.get("eparchyCode"));
        item.put("cityCode", cityCode);
        item.put("removeTag", "0");
        itemList.add(item);
        tradeCustPerson.put("item", itemList);
        return tradeCustPerson;
    }

    private Map preCustomerData(Map msg) {
        String cityCode = (String) msg.get("district");
        if (null != cityCode && cityCode.length() > 6) {
            cityCode = cityCode.substring(0, 6);
        }
        Map tradeCustomer = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        // custId在报文模板中是的key是sustId,经cb确认暂时不改 see 20170510版本技术文档 by wangmc
        // item.put("sustId", msg.get("custId"));
        item.put("custId", msg.get("custId"));
        item.put("custName", msg.get("custName"));
        item.put("custType", "0");
        item.put("custPasswd", "123456");
        item.put("developDepartId", msg.get("recomDepartId"));// 从23转4获取
        item.put("custState", "0");
        item.put("psptTypeCode", msg.get("certType"));// 从23转4获取
        item.put("psptId", msg.get("certCode"));// 从23转4获取
        item.put("openLimit", "0");
        item.put("eparchyCode", msg.get("eparchyCode"));
        item.put("cityCode", cityCode);
        item.put("inDate", GetDateUtils.getDate());
        item.put("removeTag", "0");
        item.put("creditValue", "0");
        item.put("rsrvTag1", msg.get("idMark"));// 从23转4获取 2：实名-系统、3：实名-公安、4：实名-二代
        itemList.add(item);
        tradeCustomer.put("item", itemList);
        return tradeCustomer;
    }

    private void check23To4(Map msg, Exchange exchange, String serviceClassCode) {
        msg.put("tradeTypeCode", "0440");
        msg.put("serviceClassCode", serviceClassCode);
        // 湖北预受理系统的23转4校验不能下发CITY_CODE字段,对应msg中的areaCode,是在转换eparchyCode的时候放进msg中的,在此判断去除
        String appCode = exchange.getAppCode();
        if ("hupr".equals(appCode) && !IsEmptyUtils.isEmpty(msg.get("areaCode"))) {
            msg.remove("areaCode");
            System.out.println("111111111111111111111");
        }
        /**
         * create by zhaok 7/26
         * 贵州预受理系统的23转4校验不能下发CITY_CODE字段,对应msg中的areaCode,
         * 是在转换eparchyCode的时候放进msg中的,在此判断去除
         * 新增很多省份去除areaCode
         */
        String provinceCode = (String) msg.get("province");
        if ("85|34|88|87|71|10|36".contains(provinceCode) && !IsEmptyUtils.isEmpty(msg.get("areaCode"))) {
            msg.remove("areaCode");
        }

        Exchange transExchange = ExchangeUtils.ofCopy(exchange, msg);
        // 将23转4校验tiqu在本类中 打桩by wangmc 20170814 FIXME
        // TransTo4GCheckProcessor to4Chk = new TransTo4GCheckProcessor();
        Map checkResult = new HashMap();
        long start = System.currentTimeMillis();
        System.out.println(exchange.getIn().getBody(Map.class).get("apptx") + ",调用23转4校验之前的msg:"
                + exchange.getIn().getBody(Map.class).get("msg"));
        checkResult = check23GTransfer(transExchange);
        System.out.println(exchange.getIn().getBody(Map.class).get("apptx") + "time,调用省份23转4校验用时:"
                + (System.currentTimeMillis() - start));
        // 修改史工遗留的数组越界问题--start
        Map oldCust, oldAcct, oldUser;
        List<Map> custList = (List<Map>) checkResult.get("custInfo");
        List<Map> userList = (List<Map>) checkResult.get("userInfo");
        List<Map> acctList = (List<Map>) checkResult.get("acctInfo");
        List<Map> oldPara = (List<Map>) checkResult.get("para");
        List<Map> subServInfos = (List<Map>) checkResult.get("subServInfos");// 特服信息，如果有需要继承
        if (IsEmptyUtils.isEmpty(acctList)) {
            throw new EcAopServerBizException("9999", "23转4转出校验未返回账户信息");
        }
        if (IsEmptyUtils.isEmpty(custList)) {
            throw new EcAopServerBizException("9999", "23转4转出校验未返回客户信息");
        }
        if (IsEmptyUtils.isEmpty(userList)) {
            throw new EcAopServerBizException("9999", "23转4转出校验未返回用户信息");
        }
        oldCust = custList.get(0);
        oldAcct = acctList.get(0);
        oldUser = userList.get(0);
        // 修改史工遗留的数组越界问题--end
        Map postInfo = (Map) checkResult.get("postInfo");// 邮寄信息，如果有下发cb
        String openDepartId = (String) oldUser.get("openDepartId");
        String openStaffId = (String) oldUser.get("openStaffId");
        String developDepartId = (String) oldUser.get("developDepartId");
        String developStaffId = (String) oldUser.get("developStaffId");
        // 修改为:从全业务规范转换为cb规范 by wangmc 20170510
        // String certType = CertTypeChangeUtils.certTypeMall2Cbss((String) oldCust.get("certTypeCode"));
        String certType = CertTypeChangeUtils.certTypeFbs2Cbss((String) oldCust.get("certTypeCode"));
        String certCode = (String) oldCust.get("certCode");
        String custName = (String) oldCust.get("custName");
        String linkAddress = (String) oldCust.get("postAddress");
        String certAddress = (String) oldCust.get("certAddr");
        String custId = (String) oldCust.get("custId");
        String certEndDate = "";
        if (StringUtils.isNotEmpty((String) oldCust.get("certEndDate"))) {
            certEndDate = (String) oldCust.get("certEndDate");
        }
        String cityCode = (String) msg.get("district");
        if (null != cityCode && cityCode.length() > 6) {
            cityCode = cityCode.substring(0, 6);
        }
        String userCityCode = StringUtils.isEmpty((String) oldUser.get("cityCode")) ? cityCode : (String) oldUser
                .get("cityCode");
        String oldInDate = (String) oldUser.get("openDate");
        String creditClass = decodeCreditClass((String) oldCust.get("creditClass"));
        String sex = oldCust.get("sex") == null ? "2" : "1".equals(oldCust.get("sex")) == true ? "F" : "M";
        String idMark = (String) oldCust.get("idMark");
        Map oldCustInfo = MapUtils.asMap("postInfo", postInfo, "certType", certType, "certCode", certCode, "custName",
                custName, "linkAddress", linkAddress, "certAddress", certAddress, "certEndDate", certEndDate,
                "oldInDate", oldInDate, "creditClass", creditClass, "sex", sex, "idMark", idMark, "userCityCode",
                userCityCode, "openDepartId", openDepartId, "developDepartId", developDepartId, "developStaffId",
                developStaffId, "openStaffId", openStaffId, "oldAcct", oldAcct, "oldUser", oldUser, "subServInfos",
                subServInfos, "oldPara", oldPara, "custId", custId, "oldCust", oldCust);
        msg.putAll(oldCustInfo);

    }

    /**
     * 转换信用等级
     * @param creditClass
     * @return
     */
    private String decodeCreditClass(String creditClass) {
        String credit = "67";
        if ("A".equals(creditClass)) {
            credit = "65";
        }
        else if ("B".equals(creditClass)) {
            credit = "66";
        }
        else if ("C".equals(creditClass)) {
            credit = "67";
        }

        return credit;
    }

    /**
     * 获取号码网别，并将号码放入msg
     * @param msg
     * @return
     */
    private String getNetTypeCode(Map msg) {
        long start = System.currentTimeMillis();
        String serviceClassCode = "0033"; // 给个默认值,参照北六代码 by wangmc 20170420
        List<Map> numList = (List<Map>) msg.get("numId");
        String serialNumber = (String) numList.get(0).get("serialNumber");
        msg.put("serialNumber", serialNumber);// 把号码信息放入msg
        Map num = dao.selectOperInfo(msg);
        if (null != num && !num.isEmpty()) {
            serviceClassCode = (String) num.get("netTypeCode");
        }
        else {
            /**
             * create by zhaok 7/26
             * 产品变更是必传的 ，但是23转4不是 必传 ，
             * 套餐变更时没有在库里查到数据就用传进来的，否则用库里数据；
             * 23转4还是走之前的逻辑
             */
            String Code = (String) msg.get("serviceClassCode");
            if (!IsEmptyUtils.isEmpty(Code)) {
                serviceClassCode = Code;
            }
        }
        System.out.println(msg.get("apptx") + "time,23转4根据号码获取ServiceClassCode用时:"
                + (System.currentTimeMillis() - start));
        return serviceClassCode;
    }

    /**
     * 调用省份23转4校验接口,校验号码是否可以转出
     * @param exchange
     * @param needException
     * @return
     * @throws Exception
     */
    public Map check23GTransfer(Exchange exchange) {
        LanUtils lan = new LanUtils();
        try {
            // 直连全业务
            lan.preData(pmp[3], exchange);// ecaop.23To4.check.ParametersMapping
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.app.UserTransferSer");
            lan.xml2Json("ecaop.23To4.check.template", exchange);
        }
        catch (EcAopServerSysException e) {
            e.printStackTrace();
            throw new EcAopServerBizException(e.getCode(), e.getMessage());
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "23转4转出校验失败 [ " + e.getMessage() + " ]");
        }
        return exchange.getOut().getBody(Map.class);
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }

    }

}
