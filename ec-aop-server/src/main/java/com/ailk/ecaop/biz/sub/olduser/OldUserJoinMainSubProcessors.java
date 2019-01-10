package com.ailk.ecaop.biz.sub.olduser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.config.Config;
import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.log.elk.ELKAopLogger;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.product.DealNewCbssProduct;
import com.ailk.ecaop.biz.res.QryChkTermN6Processor;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.NewProductUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.alibaba.fastjson.JSON;

@EcRocTag("OldUserJoinMainSub")
@SuppressWarnings({ "unchecked", "rawtypes" })
public class OldUserJoinMainSubProcessors extends BaseAopProcessor implements ParamsAppliable {

    LanUtils lan = new LanUtils();
    private static final String[] PARAM_ARRAY = {"ecaop.trade.cbss.checkUserParametersMapping",
            "ecaop.trade.mmvc.sUniTradeParametersMapping", "ecaop.masb.chph.gifa.ParametersMapping"};
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[3];

    @Override
    public void process(Exchange exchange) throws Exception {

        Map body = exchange.getIn().getBody(Map.class);
        Boolean isString = body.get("msg") instanceof String;
        Map msg = isString ? JSON.parseObject((String) body.get("msg")) : (Map) body.get("msg");

        // 校验数据
        String serType = msg.get("serType") + "";
        String orderType = msg.get("orderType") + "";
        boolean isSub = "1".equals(serType);// serType 业务标示 0:加入主卡 1:加入副卡
        boolean isQuit = "1".equals(orderType);// orderType 订购标示 0:加入 1:退出
        checkParams(msg, isSub);

        // 调用三户信息
        String[] copyArray = {"operatorId", "province", "channelId", "city", "district", "channelType"};
        Map threePartMap = MapUtils.asMap("getMode", "1111111111100013110000000100001", "serialNumber",
                msg.get("mainNumber"),
                "tradeTypeCode", "9999");
        MapUtils.arrayPut(threePartMap, msg, copyArray);

        if (isSub) {// 副卡业务查询主卡信息
            Exchange threePartExchangeSub = ExchangeUtils.ofCopy(exchange, threePartMap);
            lan.preData(pmp[0], threePartExchangeSub);// ecaop.trade.cbss.checkUserParametersMapping
            CallEngine.wsCall(threePartExchangeSub, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
            lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchangeSub);// 报下游返回格式有误--已改
            dealMainNumberParam(threePartExchangeSub, msg);
        }

        // 查询serialNumber三户
        threePartMap.put("serialNumber", msg.get("serialNumber"));
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
        lan.preData(pmp[0], threePartExchange);// ecaop.trade.cbss.checkUserParametersMapping
        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);// 报下游返回格式有误--已改

        threePartExchange.getIn().setBody(body);

        // 查询产品并准备接口所需数据
        // 获取CB流水号
        String tradeId;
        try {
            tradeId = GetSeqUtil.getSeqFromCb(pmp[2], exchange, "seq_trade_id", 1).get(0).toString();
            msg.put("cbTradeId", tradeId);
        } catch (Exception e) {
            throw new EcAopServerBizException("9999", "获取CB侧流水失败" + e.getMessage());
        }

        // 副卡加入时需获取CB新帐户号
        if (isSub && !isQuit) {
            try {
                String payRelId = (String) GetSeqUtil.getSeqFromCb(pmp[2], ExchangeUtils.ofCopy(exchange, msg),
                        "SEQ_PAYRELA_ID", 1).get(0);
                msg.put("payRelId", payRelId);
            } catch (Exception e) {
                e.printStackTrace();
                throw new EcAopServerBizException("9999", "获取CB侧payRelId失败" + e.getMessage());
            }
        }
        // 准备各节点参数
        Map preParams = dealParam(threePartExchange, msg, isSub, isQuit);
        Map base = (Map) preParams.get("base");
        Map ext = (Map) preParams.get("ext");
        msg.put("ordersId", tradeId);
        msg.put("operTypeCode", "0");
        msg.put("base", base);
        msg.put("ext", ext);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        // 调用预提交接口
        exchange.setMethodCode("mmvc");
        lan.preData(pmp[1], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.trades.mmvc.sUniTrade.template", exchange);
        Map message = exchange.getOut().getBody(Map.class);
        // 处理返回参数
        if (null != message.get("rspInfo")) {
            List<Map> para = (List<Map>) message.get("para");
            List<Map> rspInfo = (List<Map>) message.get("rspInfo");
            Map result = dealReturn(rspInfo.get(0), msg);
            if (null != para && !para.isEmpty()) {
                result.put("para", para);
            }
            exchange.getOut().setBody(result);
        }
    }

    private void dealMainNumberParam(Exchange exchange, Map msg) throws Exception {
        // 获取三户返回的结果
        Map out = exchange.getOut().getBody(Map.class);
        List<Map> userInfoList = (List<Map>) out.get("userInfo");
        List<Map> custInfoList = (List<Map>) out.get("custInfo");
        List<Map> acctInfoList = (List<Map>) out.get("acctInfo");
        if (null == custInfoList || custInfoList.isEmpty()) {
            throw new EcAopServerBizException("9999", "调三户未返回客户信息。");
        }
        if (null == userInfoList || userInfoList.isEmpty()) {
            throw new EcAopServerBizException("9999", "调三户未返回用户信息。");
        }
        if (null == acctInfoList || acctInfoList.isEmpty()) {
            throw new EcAopServerBizException("9999", "调三户未返回帐户信息。");
        }
        List<Map> uuInfoList = (List<Map>) userInfoList.get(0).get("uuInfo");
        if (null == uuInfoList || uuInfoList.isEmpty()) {
            throw new EcAopServerBizException("9999", "调主卡三户未返回uuInfo信息。");
        }
        String userIdA = null;
        for (Map uu : uuInfoList) {
            String relationTypeCode = (String) uu.get("relationTypeCode");
            if (!"ZF".equals(relationTypeCode)) {
                continue;
            }
            String roleCodeB = (String) uu.get("roleCodeB");
            if ("1".equals(roleCodeB)) {// 1为主卡角色
                userIdA = (String) uu.get("userIdA");
            }
        }
        if (userIdA == null || "".equals(userIdA)) {
            throw new EcAopServerBizException("9999", "调主卡三户未返回主卡IdA信息。");
        }
        msg.put("mainAcctId", acctInfoList.get(0).get("acctId"));
        msg.put("mainCustId", custInfoList.get(0).get("custId"));
        msg.put("mainUserIdA", userIdA);
    }

    private void checkParams(Map msg, Boolean isSub) {

        String mainNumber = (String) msg.get("mainNumber");
        if (isSub && (null == mainNumber || "".equals(mainNumber))) {
            throw new EcAopServerBizException("9999", "办理副卡业务主卡号码不能为空。");
        }
        List<Map> productInfo;
        boolean isList = msg.get("productInfo") instanceof List;
        if (isList) {
            productInfo = (List<Map>) msg.get("productInfo");
        } else {
            productInfo = new ArrayList();
            productInfo.add((Map) msg.get("productInfo"));
        }
        if (productInfo == null || productInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "产品信息不能为空！");
        }
    }

    private Object isNull(String string) {
        return null == string ? "" : string;
    }

    private Map dealParam(Exchange exchange, Map msg, Boolean isSub, Boolean isQuit) throws Exception {
        // 获取三户返回的结果
        Map out = exchange.getOut().getBody(Map.class);
        List<Map> userInfoList = (List<Map>) out.get("userInfo");
        List<Map> custInfoList = (List<Map>) out.get("custInfo");
        List<Map> acctInfoList = (List<Map>) out.get("acctInfo");
        if (null == custInfoList || custInfoList.isEmpty()) {
            throw new EcAopServerBizException("9999", "调三户未返回客户信息。");
        }
        if (null == userInfoList || userInfoList.isEmpty()) {
            throw new EcAopServerBizException("9999", "调三户未返回用户信息。");
        }
        if (null == acctInfoList || acctInfoList.isEmpty()) {
            throw new EcAopServerBizException("9999", "调三户未返回帐户信息。");
        }
        Map acctInfo = acctInfoList.get(0);
        Map userInfo = userInfoList.get(0);
        Map custInfo = custInfoList.get(0);
        List<Map> productOlds = (List<Map>) userInfo.get("productInfo");
        if (IsEmptyUtils.isEmpty(productOlds)) {
            throw new EcAopServerBizException("9999", "调三户未返回产品信息。");
        }
        for (Map oldProMap : productOlds) {
            if ("00".equals(oldProMap.get("productMode")) && userInfo.get("productId").equals(oldProMap.get("productId"))) {
                userInfo.put("oldProductActiveTime", oldProMap.get("productActiveTime"));
                break;
            }
        }
        // 准备产品信息
        if (null == msg.get("productInfo")) {
            throw new EcAopServerBizException("9999", "产品信息未传。");
        }
        List<Map> productInfos = (List<Map>) msg.get("productInfo");
        Map productInfo = productInfos.get(0);
        boolean isChangeMainPro = false;
        for (Map productMap : productInfos) {
            if ("1".equals(productMap.get("productMode")) && !isSub && !isQuit) {
                //用来判断是否加入主卡且变更主套餐
                isChangeMainPro = true;
                productInfo = productMap;
                break;
            }
        }
        if (!isSub && isQuit) {// 退出主卡，取出三户里的主副卡产品信息
            getSubProInfoForMainQuit(productInfo, userInfo);
        }
        // 准备用户标示调用产品处理方法
        String userId = (String) userInfo.get("userId");
        String acctId = (String) acctInfo.get("acctId");
        String custId = (String) custInfo.get("custId");
        String brandCode = (String) userInfo.get("brandCode");
        String productId = (String) userInfo.get("productId");
        msg.put("userId", userId);
        msg.put("acctId", acctId);
        msg.put("custId", custId);
        msg.put("productId", productId);
        msg.put("brandCode", brandCode);
        Object inModeCode = new ChangeCodeUtils().getInModeCode(exchange.getAppCode());
        msg.put("inModeCode", inModeCode);

        // BASE节点
        Map base = preBaseData(msg, acctInfo, userInfo, custInfo, productInfo, isSub, isQuit);
        Map EXT = new HashMap();
        // Map temp = new HashMap();
        String userProStartDate = null;
        if (productInfos != null && productInfos.size() != 0) {
            for (Map pro : productInfos) {
                String id = pro.get("productId") + "";
                if (!"".equals(id) && id.equals(userInfo.get("productId"))) {
                    userProStartDate = (String) pro.get("productActiveTime");
                }
            }
            preNewProductInfo(productInfos, msg);
        }
        userProStartDate = userProStartDate == null ? (String) userInfo.get("openDate") : userProStartDate;
        msg.put("userProStartDate", userProStartDate);
        // TRADE_RELATION
        EXT.put("tradeRelation", preDataForTradeRelation(msg, userInfo, productInfo, isSub, isQuit));

        // TRADE_ITEM
        EXT.put("tradeItem", preDataForTradeItem(msg, isSub, isQuit));

        // TRADE_PRODUCT
        EXT.put("tradeProduct", preDataForTradeProduct(msg, productInfo, userInfo, isSub, isQuit));

        // TRADE_PRODUCT_TYPE
        EXT.put("tradeProductType", preDataForTradeProductType(msg, productInfo, userInfo, isSub, isQuit));

        // TRADE_SUB_ITEM
        EXT.put("tradeSubItem", preDataForTradeSubItem(msg, isSub, isQuit));

        // TRADE_OTHER
        if (isSub || isQuit) {// 主卡加入时不用此节点
            EXT.put("tradeOther", preDataForTradeOther(userInfo, msg, productInfo, isSub, isQuit));
        }

        // TRADE_DISCNT
        if (isSub || !isQuit||isChangeMainPro) {// 主卡退出时不用此节点
            EXT.put("tradeDiscnt", preDataForTradeDiscnt(userId, (List<Map>) msg.get("discntList")));
        }

        if (isSub||isChangeMainPro) {
            // TRADE_USER
            EXT.put("tradeUser", preDataForTradeUser(userId, productInfo));

            // TRADE_SP
            EXT.put("tradeSp", preDataForTradeSp((List<Map>) msg.get("spList"), (String) productInfo.get("productId")));

            // TRADE_SVC
            EXT.put("tradeSvc", preDataForTradeSvc((List<Map>) msg.get("svcList")));

            // TradePayrelation
            if (!isQuit) {
                EXT.put("tradePayrelation", preDataForTradePayrelation(userInfo, acctInfo, msg));
            }

        }
        // 黑龙江老用户加入主副卡
        if (isChangeMainPro) {
            //EXT.put("tradeDiscnt", preDataTradeDiscnt(userId, (List<Map>) msg.get("discntList")));
            EXT.put("tradeOther", preDataTradeOther(userInfo, msg));
            //EXT.put("tradeUser", preDataTradeUser(userId, productInfo));
            EXT.put("tradeProduct", preDataTradeProduct(msg, userInfo));
            EXT.put("tradeProductType", preDataTradeProductType(msg, userInfo));
            //EXT.put("tradeSp", preDataTradeSp((List<Map>) msg.get("spList"), (String) productInfo.get("productId")));
            //EXT.put("tradeSvc", preDataTradeSvc((List<Map>) msg.get("svcList")));
        }
        // 广东主卡加入下发多个附加产品
        if (!isSub && !isQuit) {
            EXT.put("tradeProduct", preDataTradeAddProduct(msg, userInfo));
            EXT.put("tradeProductType", preDataTradeAddProductType(msg, userInfo));
        }

        // 准备活动信息 create by zhaok  增加合约机活动节点
        preActivityInfo(exchange, EXT, msg, userInfo);

        // 去重
        dealRepeat(EXT);
        Map TRADE = new HashMap();
        TRADE.put("base", base);
        TRADE.put("ext", EXT);
        return TRADE;

    }

    /**
     * 处理活动节点
     * @param msg
     * @param discntList
     * @param svcList
     * @param spList
     * @param productTypeList
     * @param productList
     */
    private void preActiveityInfoData(Map msg, List<Map> discntList, List<Map> svcList, List<Map> spList,
            List<Map> productTypeList, List<Map> productList, String provinceCode, Object eparchyCode,
            String monthLasttDay, String monthFirstDay) {

        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        Map activityTimeMap = new HashMap();
        // 处理活动
        List<Map> activityInfo = (List<Map>) msg.get("activityInfo");
        if (null != activityInfo && activityInfo.size() > 0) {
            for (int i = 0; i < activityInfo.size(); i++) {
                List<Map> packageElement = (List<Map>) activityInfo.get(i).get("packageElement");
                String actPlanId = String.valueOf(activityInfo.get(i).get("actPlanId"));
                Map proparam = new HashMap();
                List<Map> packageElementInfo = new ArrayList<Map>();
                proparam.put("PROVINCE_CODE", provinceCode);
                proparam.put("PRODUCT_ID", actPlanId);
                String newActPlanId = n25Dao.queryActivityByCommodityId(proparam).toString();
                activityTimeMap = TradeManagerUtils.getEffectTime(newActPlanId, monthFirstDay, monthLasttDay);
                String actMonthFirstDay = (String) activityTimeMap.get("monthFirstDay");
                String actMonthLasttDay = (String) activityTimeMap.get("monthLasttDay");
                String actMonthLasttDayForMat = "";
                try {
                    actMonthLasttDayForMat = GetDateUtils.transDate(actMonthLasttDay, 14);
                }
                catch (Exception e) {
                    e.printStackTrace();
                    throw new EcAopServerBizException("9999", "时间格式转换失败");
                }
                msg.put("resActivityper", activityTimeMap.get("resActivityper"));

                // 附加包处理
                if (packageElement != null && packageElement.size() > 0) {
                    for (int n = 0; n < packageElement.size(); n++) {
                        Map peparam = new HashMap();
                        peparam.put("PROVINCE_CODE", provinceCode);
                        peparam.put("PRODUCT_ID", actPlanId);
                        peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                        peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                        peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                        packageElementInfo = n25Dao.queryPackageElementInfo(peparam);
                        if (packageElementInfo != null && packageElementInfo.size() > 0) {
                            for (Map packageMap : packageElementInfo) {
                                // 处理活动的生效失效时间
                                if ("D".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    Map dis = new HashMap();
                                    dis.put("productId", packageMap.get("PRODUCT_ID"));
                                    dis.put("productMode", packageMap.get("PRODUCT_MODE"));
                                    dis.put("packageId", packageMap.get("PACKAGE_ID"));
                                    dis.put("discntCode", packageMap.get("ELEMENT_ID"));
                                    dis.put("activityStarTime", actMonthFirstDay);
                                    dis.put("activityTime", actMonthLasttDayForMat);
                                    discntList.add(dis);
                                }
                                if ("S".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    Map svc = new HashMap();
                                    svc.put("serviceId", packageMap.get("ELEMENT_ID"));
                                    svc.put("productId", packageMap.get("PRODUCT_ID"));
                                    svc.put("packageId", packageMap.get("PACKAGE_ID"));
                                    svc.put("activityStarTime", actMonthFirstDay);
                                    svc.put("activityTime", actMonthLasttDayForMat);
                                    svcList.add(svc);
                                }
                                if ("X".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                    String spId = "-1";
                                    String partyId = "-1";
                                    String spProductId = "-1";
                                    Map spItemParam = new HashMap();
                                    spItemParam.put("PTYPE", "X");
                                    spItemParam.put("PRODUCT_ID", actPlanId);
                                    spItemParam.put("PROVINCE_CODE", provinceCode);
                                    spItemParam.put("SPSERVICEID", packageMap.get("ELEMENT_ID"));
                                    List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                                    if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                        throw new EcAopServerBizException("9999", "在SP表中未查询到【"
                                                + packageMap.get("ELEMENT_ID") + "】的元素属性信息");
                                    }
                                    for (int l = 0; l < spItemInfoResult.size(); l++) {
                                        Map spItemInfo = spItemInfoResult.get(l);
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
                                    sp.put("activityStarTime", actMonthFirstDay);
                                    sp.put("activityTime", actMonthLasttDayForMat);
                                    spList.add(sp);
                                }
                            }
                        }
                    }
                }

                // 处理活动产品的订购
                Map productTpye = new HashMap();
                Map product = new HashMap();

                String addProductId = "";
                String strBrandCode = "";
                String strProductTypeCode = "";
                String strProductMode = "";

                Map addproparam = new HashMap();
                addproparam.put("PROVINCE_CODE", provinceCode);
                addproparam.put("PRODUCT_MODE", "50");
                addproparam.put("PRODUCT_ID", actPlanId);
                addproparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                addproparam.put("FIRST_MON_BILL_MODE", null);
                List<Map> addproductInfoResult = n25Dao.qryDefaultPackageElement(addproparam);
                // 原有方法是从查询默认产品的结果中取的，如果该产品不存在默认的包和元素（即addproductInfoResult为空）则会出现数组越界，
                // 现在改为优先取默认查询的结果，如果不存在默认的包和元素则去取附加元素处理时查询的结果。
                if (addproductInfoResult == null || addproductInfoResult.size() == 0) {
                    List<Map> activityInfoList = n25Dao.qryProductInfoByProductTable(addproparam);
                    if (!IsEmptyUtils.isEmpty(activityInfoList)) {
                        if (packageElementInfo != null && packageElementInfo.size() > 0) {
                            strBrandCode = String.valueOf(packageElementInfo.get(0).get("BRAND_CODE"));
                            strProductTypeCode = String.valueOf(packageElementInfo.get(0).get("PRODUCT_TYPE_CODE"));
                        }
                        else {
                            throw new EcAopServerBizException("9999", "未查询到附加包信息");
                        }
                    }
                    else {
                        throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + actPlanId + "】的产品信息");
                    }
                }
                else {
                    strBrandCode = (String) addproductInfoResult.get(0).get("BRAND_CODE");
                    strProductTypeCode = (String) addproductInfoResult.get(0).get("PRODUCT_TYPE_CODE");
                }

                // 用于主副卡处理元素编码是A的问题
                msg.put("activityproductInfoResult", addproductInfoResult);
                ELKAopLogger.logStr("处理活动产品activityproductInfoResult:" + addproductInfoResult);
                if (null != addproductInfoResult && addproductInfoResult.size() > 0) {
                    for (int j = 0; j < addproductInfoResult.size(); j++) {

                        if ("D".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map dis = new HashMap();
                            dis.put("productId", addproductInfoResult.get(j).get("PRODUCT_ID"));
                            dis.put("productMode", addproductInfoResult.get(j).get("PRODUCT_MODE"));
                            dis.put("packageId", addproductInfoResult.get(j).get("PACKAGE_ID"));
                            dis.put("discntCode", addproductInfoResult.get(j).get("ELEMENT_ID"));
                            // 算出活动的开始结束时间，预提交下发
                            dis.put("activityStarTime", actMonthFirstDay);
                            dis.put("activityTime", actMonthLasttDayForMat);
                            discntList.add(dis);
                        }
                        if ("S".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map svc = new HashMap();
                            svc.put("serviceId", addproductInfoResult.get(j).get("ELEMENT_ID"));
                            svc.put("productId", addproductInfoResult.get(j).get("PRODUCT_ID"));
                            svc.put("packageId", addproductInfoResult.get(j).get("PACKAGE_ID"));
                            // 算出活动的开始结束时间，预提交下发
                            svc.put("activityStarTime", actMonthFirstDay);
                            svc.put("activityTime", actMonthLasttDayForMat);
                            svcList.add(svc);
                        }
                        if ("X".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            String spId = "-1";
                            String partyId = "-1";
                            String spProductId = "-1";
                            Map spItemParam = new HashMap();
                            spItemParam.put("PTYPE", "X");
                            spItemParam.put("PRODUCT_ID", actPlanId);
                            spItemParam.put("PROVINCE_CODE", provinceCode);
                            spItemParam.put("SPSERVICEID", addproductInfoResult.get(j).get("ELEMENT_ID"));
                            List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                            if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                throw new EcAopServerBizException("9999", "在SP表中未查询到【"
                                        + addproductInfoResult.get(j).get("ELEMENT_ID") + "】的元素属性信息");
                            }
                            for (int k = 0; k < spItemInfoResult.size(); k++) {
                                Map spItemInfo = spItemInfoResult.get(k);
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
                            sp.put("activityStarTime", actMonthFirstDay);
                            sp.put("activityTime", actMonthLasttDayForMat);
                            spList.add(sp);
                        }
                    }
                }

                strProductMode = "50";

                productTpye.put("productMode", strProductMode);
                productTpye.put("productId", newActPlanId);
                productTpye.put("productTypeCode", strProductTypeCode);
                // 算出活动的开始结束时间，预提交下发
                productTpye.put("activityStarTime", actMonthFirstDay);
                productTpye.put("activityTime", actMonthLasttDayForMat);

                product.put("brandCode", strBrandCode);
                product.put("productId", newActPlanId);
                product.put("productMode", strProductMode);

                productTypeList.add(productTpye);
                productList.add(product);
            }
        }
    }

    /**
     * 增加合约机活动节点
     * @param ext
     * @param msg
     */
    private void preActivityInfo(Exchange exchange, Map ext, Map msg, Map userInfo) {
        List<Map> activityInfo = (List<Map>) msg.get("activityInfo");
        if (IsEmptyUtils.isEmpty(activityInfo)) {
            return;
        }
        String provinceCode = (String) msg.get("provinceCode");
        //获取终端信息
        getResourceInfo(exchange);

        // 拼装各节点参数
        String staffId = (String) msg.get("operatorId");
        String tradeId = (String) msg.get("cbTradeId");
        String eparchyCode = (String) msg.get("eparchyCode");
        String acceptDate = RDate.currentTimeStr("yyyyMMddHHmmss");
        String resourcesCode;
        String resourceFee = "";
        String actProductId = "";
        String packageId = "";
        String elementDiscntId = "";
        String resActivityper = (String) msg.get("resActivityper");
        String monthFirstDay = GetDateUtils.getNextMonthFirstDayFormat();
        String monthLasttDay = "20501231235959";
        String userId = (String) msg.get("userId");
        List<Map> tradeFeeItemList = new ArrayList<Map>();
        List<Map> subItemList = new ArrayList<Map>();// 注意ext中有subItemList参数
        List<Map> activityproductInfoResult = (List<Map>) msg.get("activityproductInfoResult");
        List<Map> product = (List<Map>) msg.get("discntList");
        monthFirstDay = (String) product.get(0).get("activityStarTime");
        monthLasttDay = (String) product.get(0).get("activityTime");
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
                        List<Map> elementList = new ArrayList<Map>();
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
                        elemntItem1.put("itemId", TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID"));
                        elemntItem1.put("userIdA", "-1");
                        elemntItem1.put("xDatatype", "NULL");
                        elementList.add(elemntItem1);
                        msg.put("elementMap", elementList);

                        List<Map> resourcesInfo = (List<Map>) msg.get("resourcesInfo");
                        if (resourcesInfo != null && resourcesInfo.size() > 0) {
                            for (Map rescMap : resourcesInfo) {
                                resourcesCode = (String) rescMap.get("resourcesCode");
                                String salePrice = ""; // 销售价格（单位：厘）
                                if (StringUtils.isNotEmpty((String) rescMap.get("salePrice"))) {
                                    salePrice = (String) rescMap.get("salePrice");
                                    salePrice = String.valueOf(Integer.parseInt(salePrice) / 10);
                                }
                                String resCode = resourcesCode;// 资源唯一标识
                                String resBrandCode = (String) rescMap.get("resourcesBrandCode"); // 品牌
                                String resBrandName = ""; // 终端品牌名称
                                if (StringUtils.isEmpty((String) rescMap.get("resourcesBrandName"))) {
                                    resBrandName = "无说明";
                                }
                                else {
                                    resBrandName = (String) rescMap.get("resourcesBrandName");
                                }
                                String resModelCode = (String) rescMap.get("resourcesModelCode"); // 型号
                                String resModeName = ""; // 终端型号名称
                                if (StringUtils.isEmpty((String) rescMap.get("resourcesModelName"))) {
                                    resModeName = "无说明";
                                }
                                else {
                                    resModeName = (String) rescMap.get("resourcesModelName");
                                }
                                String machineTypeCode = "";// 终端机型编码
                                if (StringUtils.isEmpty((String) rescMap.get("machineTypeCode"))) {
                                    machineTypeCode = "无说明";
                                }
                                else {
                                    machineTypeCode = (String) rescMap.get("machineTypeCode");
                                }
                                String orgdeviceBrandCode = "";
                                if (StringUtils.isNotEmpty((String) rescMap.get("orgDeviceBrandCode"))) {
                                    orgdeviceBrandCode = (String) rescMap.get("orgDeviceBrandCode");// 3GESS维护品牌，当iphone时品牌与上面的一致
                                }
                                String cost = ""; // 成本价格（单位：厘）
                                if (StringUtils.isNotEmpty((String) rescMap.get("cost"))) {
                                    cost = (String) rescMap.get("cost");
                                    cost = String.valueOf(Integer.parseInt(cost) / 10);
                                }
                                String machineTypeName = ""; // 终端机型名称
                                if (StringUtils.isEmpty((String) rescMap.get("machineTypeName"))) {
                                    machineTypeName = "无说明";
                                }
                                else {
                                    machineTypeName = (String) rescMap.get("machineTypeName");
                                }
                                String terminalSubtype = "";
                                if (StringUtils.isNotEmpty((String) rescMap.get("terminalTSubType"))) {
                                    terminalSubtype = (String) rescMap.get("terminalTSubType");
                                }
                                String terminalType = (String) rescMap.get("terminalType");// 终端类别编码
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
                                // 拼装SUB_ITEM结束

                                // tf_b_trade_purchase表的台账
                                List<Map> tradePurchaseList = new ArrayList<Map>();
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
                                tradePurchaseList.add(tradePurchase);
                                msg.put("tradePurchase", tradePurchaseList);

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
        if (null != msg.get("tradePurchase")) {
            ext.put("tradePurchase", preTradePurchase(msg));
        }
        // 取出原有ext中tradeSubItem的值，再将活动的subItemList放入ext中
        ext.put("tradeSubItem", preAllForTradeSubItem(subItemList, ext));
        ext.put("tradeElement", preTradeElementData(msg));
        ext.put("tradefeeSub", preTradeElementData(msg));

    }

    /**
     * 取出原有ext中tradeSubItem的值，再将活动的subItemList放入ext中
     * @param subItemList
     * @param ext
     * @return
     */
    private List preAllForTradeSubItem(List<Map> subItemList, Map ext) {

        List<Map> tradeSubItemList = new ArrayList<Map>();
        tradeSubItemList = (List<Map>) ext.get("tradeSubItemList");

        if (IsEmptyUtils.isEmpty(subItemList)) {
            return tradeSubItemList;
        }
        else {
            Map tradeSubItem = new HashMap();

            tradeSubItem = tradeSubItemList.get(0);
            List<Map> subitems = (List<Map>) tradeSubItem.get("item");
            subItemList.addAll(subitems);

            tradeSubItem.put("item", subItemList);
            tradeSubItemList.add(tradeSubItem);
            return tradeSubItemList;
        }

    }

    /**
     * 拼装TRADE_PURCHASE
     * 此节点下面存放终端信息
     */

    private Map preTradePurchase(Map msg) {
        try {
            List<Map> tradePurchaseList = (List<Map>) msg.get("tradePurchase");
            Map tradePurchase = new HashMap();
            tradePurchase.put("item", tradePurchaseList);
            return tradePurchase;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_PURCHASE节点报错" + e.getMessage());
        }
    }

    /**
     * 拼装tradeElement节点
     * @param msg
     * @return
     */
    private Map preTradeElementData(Map msg) {
        List<Map> elementMap = (List<Map>) msg.get("elementMap");
        Map tradeElement = new HashMap();
        tradeElement.put("item", elementMap);
        return tradeElement;
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

    /**
     * 
     *获取终端信息
     * @param msg
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
                ELKAopLogger.logStr("===获取活动终端信息：" + msg.get("resourcesInfo"));
                body.put("msg", msg);
                exchange.getIn().setBody(body);

            }
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "获取终端信息失败");
        }
    }

    private List<Map> preDataTradeProductType(Map msg, Map userInfo) {
        Map tradeProductType = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> productTpye = (List<Map>) msg.get("productTypeList");
        for (int i = 0; i < productTpye.size(); i++) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", userInfo.get("userId"));
            item.put("productMode", productTpye.get(i).get("productMode"));
            item.put("productId", productTpye.get(i).get("productId"));
            item.put("productTypeCode", productTpye.get(i).get("productTypeCode"));
            item.put("modifyTag", "0");
            if (null != productTpye.get(i).get("activityStarTime")) {
                item.put("startDate", productTpye.get(i).get("activityStarTime"));
            } else {
                item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
            }
            if (null != productTpye.get(i).get("activityTime")) {
                item.put("endDate", productTpye.get(i).get("activityTime"));
            } else {
                item.put("endDate", "20501231235959");
            }
            itemList.add(item);
        }
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("userId", userInfo.get("userId"));
        item.put("productMode", "00");
        item.put("productId", userInfo.get("productId"));
        item.put("productTypeCode", "4G000001");
        item.put("modifyTag", "1");
        item.put("startDate", userInfo.get("oldProductActiveTime"));
        item.put("endDate", GetDateUtils.getMonthLastDayFormat());
        itemList.add(item);

        tradeProductType.put("item", itemList);
        List<Map> tradeProductTypeList = new ArrayList<Map>();
        tradeProductTypeList.add(tradeProductType);
        return tradeProductTypeList;

    }

    private List<Map> preDataTradeAddProductType(Map msg, Map userInfo) {
        Map tradeProductType = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> productTpye = (List<Map>) msg.get("productTypeList");
        for (int i = 0; i < productTpye.size(); i++) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", userInfo.get("userId"));
            item.put("productMode", productTpye.get(i).get("productMode"));
            item.put("productId", productTpye.get(i).get("productId"));
            item.put("productTypeCode", productTpye.get(i).get("productTypeCode"));
            item.put("modifyTag", "0");
            if (null != productTpye.get(i).get("activityStarTime")) {
                item.put("startDate", productTpye.get(i).get("activityStarTime"));
            } else {
                item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
            }
            if (null != productTpye.get(i).get("activityTime")) {
                item.put("endDate", productTpye.get(i).get("activityTime"));
            } else {
                item.put("endDate", "20501231235959");
            }
            itemList.add(item);
        }

        tradeProductType.put("item", itemList);
        List<Map> tradeProductTypeList = new ArrayList<Map>();
        tradeProductTypeList.add(tradeProductType);
        return tradeProductTypeList;

    }

    private List<Map> preDataTradeProduct(Map msg, Map userInfo) {
        Map tradeProduct = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> product = (List<Map>) msg.get("productList");
        for (int i = 0; i < product.size(); i++) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", userInfo.get("userId"));
            item.put("productMode", product.get(i).get("productMode"));
            item.put("productId", product.get(i).get("productId"));
            item.put("brandCode", product.get(i).get("brandCode"));
            item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
            item.put("modifyTag", "0");
            if (null != product.get(i).get("activityStarTime")) {
                item.put("startDate", product.get(i).get("activityStarTime"));
            } else {
                item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
            }
            if (null != product.get(i).get("activityTime")) {
                item.put("endDate", product.get(i).get("activityTime"));
            } else {
                item.put("endDate", "20501231235959");
            }
            item.put("userIdA", MagicNumber.DEFAULT_NO_VALUE);
            itemList.add(item);
        }

        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("userId", userInfo.get("userId"));
        item.put("productMode", "00");
        item.put("productId", userInfo.get("productId"));
        item.put("brandCode", "4G00");
        item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
        item.put("modifyTag", "1");
        item.put("startDate", userInfo.get("oldProductActiveTime"));
        item.put("endDate", GetDateUtils.getMonthLastDayFormat());
        item.put("userIdA", MagicNumber.DEFAULT_NO_VALUE);
        itemList.add(item);
        tradeProduct.put("item", itemList);
        List<Map> tradeProductList = new ArrayList<Map>();
        tradeProductList.add(tradeProduct);
        return tradeProductList;
    }

    private List<Map> preDataTradeAddProduct(Map msg, Map userInfo) {
        Map tradeProduct = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> product = (List<Map>) msg.get("productList");
        for (int i = 0; i < product.size(); i++) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", userInfo.get("userId"));
            item.put("productMode", product.get(i).get("productMode"));
            item.put("productId", product.get(i).get("productId"));
            item.put("brandCode", product.get(i).get("brandCode"));
            item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
            item.put("modifyTag", "0");
            if (null != product.get(i).get("activityStarTime")) {
                item.put("startDate", product.get(i).get("activityStarTime"));
            } else {
                item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
            }
            if (null != product.get(i).get("activityTime")) {
                item.put("endDate", product.get(i).get("activityTime"));
            } else {
                item.put("endDate", "20501231235959");
            }
            item.put("userIdA", MagicNumber.DEFAULT_NO_VALUE);
            itemList.add(item);
        }

        tradeProduct.put("item", itemList);
        List<Map> tradeProductList = new ArrayList<Map>();
        tradeProductList.add(tradeProduct);
        return tradeProductList;
    }

    private List<Map> preDataTradeOther(Map userInfo, Map msg) {
        Map tradeOther = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap(); item.put("xDatatype", "NULL");
        item.put("rsrvValueCode", "NEXP");
        item.put("rsrvValue", msg.get("userId"));
        item.put("rsrvStr1", userInfo.get("productId"));
        item.put("modifyTag", "1");
        item.put("rsrvStr2", "00");
        item.put("rsrvStr3", "-9");
        item.put("rsrvStr4", "4G000001");
        item.put("rsrvStr5", "4G000001");
        item.put("rsrvStr6", "-1");
        item.put("rsrvStr7", "0");
        item.put("rsrvStr9", "4G00");
        item.put("rsrvStr10", msg.get("serialNumber"));
        item.put("startDate", userInfo.get("oldProductActiveTime"));
        item.put("endDate", GetDateUtils.getMonthLastDayFormat());
        itemList.add(item);
        tradeOther.put("item", itemList);
        List<Map> tradeOtherList = new ArrayList<Map>();
        tradeOtherList.add(tradeOther);
        return tradeOtherList;
    }

    /**
     * 调用公共的去重方法 by wangmc 20170410
     *
     * @param ext
     */
    private void dealRepeat(Map ext) {
        String[] nodeKeys = new String[]{"tradeProduct", "tradeProductType", "tradeDiscnt", "tradeSp", "tradeSvc"};
        String[] dealKeys = new String[]{"productList", "productTypeList", "discntList", "spList", "svcList"};
        for (int i = 0; i < nodeKeys.length; i++) {
            List<Map> nodeList = (List<Map>) ext.get(nodeKeys[i]);
            if (!IsEmptyUtils.isEmpty(nodeList)) {
                if (!IsEmptyUtils.isEmpty(nodeList.get(0))) {
                    nodeList = (List<Map>) nodeList.get(0).get("item");
                    if (!IsEmptyUtils.isEmpty(nodeList)) {
                        nodeList = NewProductUtils.newDealRepeat(nodeList, dealKeys[i]);
                        List<Map> tradeList = new ArrayList<Map>();
                        tradeList.add(MapUtils.asMap("item", nodeList));
                        ext.put(nodeKeys[i], tradeList);
                    }
                }
            }
        }
    }

    private Map preBaseData(Map msg, Map acctInfo, Map userInfo, Map custInfo, Map productInfo, Boolean isSub, Boolean isQuit) {
        Map base = new HashMap();
        base.put("advancePay", "0");// 预付话费
        base.put("userDiffCode", userInfo.get("userDiffCode"));// USER_DIFF_CODE
        base.put("acceptDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("inModeCode", msg.get("inModeCode"));// 接入方式
        base.put("productId", productInfo.get("productId"));// PRODUCT_ID
        base.put("serinalNamber", msg.get("serialNumber"));// SERIAL_NUMBER 模板里是serinalNamber不是serialNumber
        base.put("usecustId", userInfo.get("usecustId"));// USECUST_ID
        base.put("actorCertNum", "");// 无
        base.put("remark", "");// 无
        base.put("feeState", "");// 收费标志：0-未收费，1-已收费
        base.put("contactPhone", MapUtils.getDefault(msg, "contactPhone", ""));// CONTACT_PHONE
        base.put("nextDealTag", "Z");// 后续处理状态
        base.put("contactAddress", "");// 无
        base.put("olcomTag", "0");// 指令标志
        base.put("custId", custInfo.get("custId"));// CUST_ID
        if (isSub && !isQuit) {// 加入副卡时写入主卡帐号
            base.put("acctId", msg.get("mainAcctId"));// ACCT_ID
        } else {
            base.put("acctId", acctInfo.get("acctId"));// ACCT_ID
        }
        base.put("foregift", "0");// 押金金额
        if (isSub) {
            base.put("execTime", RDate.toStr(RDate.getFirstDayOfNextMonth(new Date()), "yyyyMMddHHmmss"));
        } else {
            base.put("execTime", RDate.currentTimeStr("yyyyMMddHHmmss"));
        }
        String termIp = Config.getStr("ecaop.mmvc.params.config.termIp");
        base.put("termIp", termIp);// 受理终端IP地址10.124.0.5
        base.put("actorCertTypeId", "");// 无actorCertTypeId
        base.put("chkTag", "0");// <!--审核标志：0-未审核，1-审核通过，2-审核未通过。 -->
        base.put("tradeId", msg.get("cbTradeId"));
        base.put("actorPhone", "");// 无
        base.put("operFee", "0");// 营业费用
        base.put("tradeTypeCode", "0120");// 业务类型编码
        base.put("cancelTag", "0");
        String district = (String) msg.get("district");
        base.put("cityCode", district.length() < 7 ? district : district.substring(0, 6));// CITY_CODE
        base.put("userId", userInfo.get("userId"));// USER_ID
        // 转换地市编码
        String provinceCode = "00" + msg.get("province");
        msg.put("provinceCode", provinceCode);
        String eparchyCode = ChangeCodeUtils.changeEparchy(msg);
        base.put("eparchyCode", eparchyCode);// EPARCHY_CODE
        msg.put("eparchyCode", eparchyCode);
        base.put("netTypeCode", "0050");// 网别
        base.put("contact", "");// 无
        base.put("custName", custInfo.get("custName"));// CUST_NAME
        base.put("feeStaffId", "");// 无
        base.put("checktypeCode", custInfo.get("certTypeCode"));// ????certTypeCode
        base.put("subscribeId", msg.get("cbTradeId"));
        base.put("brandCode", "4G00");
        base.put("actorName", "");// 无
        return base;
    }

    private Map dealReturn(Map rspInfo, Map msg) {
        Map ret = new HashMap();
        ret.put("orderId", isNull((String) rspInfo.get("bssOrderId")));
        if (null != rspInfo.get("provinceOrderInfo")) {
            List<Map> provinceOrderInfoList = (List<Map>) rspInfo.get("provinceOrderInfo");
            for (Map provinceOrderInfo : provinceOrderInfoList) {
                if (null != provinceOrderInfo.get("preFeeInfoRsp")) {
                    List<Map> feeInfo = (List<Map>) provinceOrderInfo.get("preFeeInfoRsp");
                    List<Map> newFeeInfo = new ArrayList<Map>();
                    for (Map fee : feeInfo) {
                        Map newFee = dealFee(fee);
                        newFeeInfo.add(newFee);
                    }
                    if (null != newFeeInfo && newFeeInfo.size() != 0) {
                        ret.put("feeInfo", newFeeInfo);
                        // 有费用时需要在北六库记录订单表,正式提交下发需要下发PAY_TAG为1
                        msg.put("ordersId", rspInfo.get("provOrderId"));
                        insert2CBSSTradeChangePro(msg);
                    }
                }
                String totalFee = (String) provinceOrderInfo.get("totalFee");
                ret.put("totalFee", null == totalFee || "0".equals(totalFee) ? "0" : totalFee + "0");
            }
        }
        return ret;
    }

    public void insert2CBSSTradeChangePro(Map msg) {
        try {
            LanOpenApp4GDao dao = new LanOpenApp4GDao();
            Map tradeParam = new HashMap();
            tradeParam.put("SUBSCRIBE_ID", msg.get("ordersId"));
            tradeParam.put("SUBSCRIBE_STATE", "0");
            tradeParam.put("NEXT_DEAL_TAG", "9");
            tradeParam.put("TRADE_TYPE_CODE", "120");
            tradeParam.put("OUT_ORDER_ID", msg.get("ordersId"));
            tradeParam.put("PROVINCE_CODE", "00" + msg.get("province"));
            tradeParam.put("SUBSYS_CODE", "MALL");// 先随便给个值吧
            tradeParam.put("IS_REMOTE", "3");// 1:成卡、2：白卡、3：北六正式提交接口时会根据该字段判断是否将payTag字段设定为1
            tradeParam.put("NET_TYPE_CODE", "50");
            tradeParam.put("CANCEL_TAG", "0");
            tradeParam.put("BUSI_SPECIAL_FLAG", "0");
            if (null == msg.get("eModeCode") || "".equals(msg.get("eModeCode"))) {
                tradeParam.put("IN_MODE_CODE", "E");
            } else {
                tradeParam.put("IN_MODE_CODE", msg.get("eModeCode"));
            }
            tradeParam.put("SOP_STATE", "0");
            tradeParam.put("PRODUCT_ID", msg.get("productId"));
            tradeParam.put("BRAND_CODE", "4G00");
            tradeParam.put("USER_ID", msg.get("userId"));
            tradeParam.put("ACCT_ID", msg.get("acctId"));
            tradeParam.put("CUST_ID", msg.get("custId"));
            tradeParam.put("ACCEPT_DATE", GetDateUtils.getDate());
            tradeParam.put("TRADE_STAFF_ID", msg.get("operatorId"));
            tradeParam.put("TRADE_DEPART_ID", msg.get("channelId"));
            // 数据库中该字段为非空,23转4接口规范中区县为非必传,不传时插表会报错,增加默认值处理 by wangmc see
            // 20170510版本技术文档
            tradeParam.put("TRADE_CITY_CODE",
                    IsEmptyUtils.isEmpty(msg.get("district")) ? "000000" : msg.get("district"));
            tradeParam.put("CITY_CODE", msg.get("district"));
            tradeParam.put("TRADE_EPARCHY_CODE", msg.get("eparchyCode"));
            tradeParam.put("EPARCHY_CODE", msg.get("eparchyCode"));
            tradeParam.put("SERIAL_NUMBER", msg.get("serialNumber"));
            tradeParam.put("OPER_FEE", "0");
            tradeParam.put("FOREGIFT", "0");
            tradeParam.put("ADVANCE_PAY", "0");
            tradeParam.put("OLCOM_TAG", "0");
            tradeParam.put("REMARK", msg.get("remark"));
            tradeParam.put("RESOURCE_CODE", msg.get("resourcesCode"));
            tradeParam.put("ACTIVITY_TYPE", msg.get("ACTIVITY_TYPE"));
            dao.insertTrade(tradeParam);
        } catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "预提交完成后记录订单信息失败" + e.getMessage());
        }
    }

    private Map dealFee(Map fee) {
        Map feeInfo = new HashMap();
        feeInfo.put("feeId", isNull((String) fee.get("feeTypeCode")));
        feeInfo.put("feeCategory", isNull((String) fee.get("feeMode")));
        feeInfo.put("feeDes", isNull((String) fee.get("feeTypeName")));
        if (null != fee.get("maxDerateFee") && !"".equals(fee.get("maxDerateFee"))) {
            feeInfo.put("maxRelief", "0".equals(fee.get("maxDerateFee")) ? "0" : fee.get("maxDerateFee") + "0");
        }
        feeInfo.put("origFee", null == fee.get("fee") || "0".equals(fee.get("fee")) ? "0" : fee.get("fee") + "0");
        return feeInfo;
    }

    // TRADE_RELATION//productInfo, userInfo, mainNumberUserInfo, flag
    private List preDataForTradeRelation(Map msg, Map userInfo, Map productInfo, Boolean isSub, Boolean isQuit) {
        Map tradeRelation = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("serialNumberB", msg.get("serialNumber"));
        if (isQuit) {// 退出主副卡关系
            item.put("modifyTag", "1");// <!--修改标志 -->
            item.put("startDate", isSub ? msg.get("userProStartDate") : productInfo.get("subProductActiveTime"));
            item.put("endDate", GetDateUtils.getMonthLastDayFormat());
        } else {
            item.put("modifyTag", "0");
            item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
            item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
        }
        item.put("relationTypeCode", "ZF");// relationTypeCode
        item.put("remark", "");
        item.put("roleCodeA", "0");
        item.put("orderno", "-1");// ???
        item.put("idB", userInfo.get("userId"));// userId
        item.put("relationAttr", "0");
        if (isSub) {
            item.put("serialNumberA", msg.get("mainNumber"));
            item.put("itemId", msg.get("mainCustId"));// ????
            item.put("idA", msg.get("mainUserIdA"));// userId
            item.put("roleCodeB", "0");
        } else {
            item.put("serialNumberA", msg.get("serialNumber"));
            item.put("itemId", userInfo.get("custId"));// ????
            item.put("idA", userInfo.get("userId"));// userId
            item.put("roleCodeB", "1");
        }
        itemList.add(item);
        tradeRelation.put("item", itemList);
        List<Map> tradeRelationList = new ArrayList<Map>();
        tradeRelationList.add(tradeRelation);
        return tradeRelationList;
    }

    // TRADE_USER
    private List preDataForTradeUser(String userId, Map productInfo) {
        Map tradeUser = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("brandCode", "4G00");
        item.put("netTypeCode", "0050");
        item.put("userId", userId);
        item.put("productId", productInfo.get("productId"));// 要新用的主产品
        itemList.add(item);
        tradeUser.put("item", itemList);
        List<Map> list = new ArrayList<Map>();
        list.add(tradeUser);
        return list;
    }

    // TRADE_OTHER 退订的主产品信息
    private List preDataForTradeOther(Map userInfo, Map msg, Map productInfo, Boolean isSub, Boolean isQuit) {
        Map tradeOther = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();
        if (isSub) {
            item.put("rsrvStr1", userInfo.get("productId"));// 要取消的产品Id
            item.put("startDate", msg.get("userProStartDate"));// 传了入网时间
        } else {
            item.put("rsrvStr1", productInfo.get("productId"));// 要取消的主产品Id
            item.put("startDate", productInfo.get("subProductActiveTime"));// 传了入网时间
        }
        item.put("rsrvStr2", isSub ? "00" : "01");// 主卡01副卡00
        item.put("modifyTag", "1");// 状态属性：0－增加 1－删除 2－修改
        item.put("rsrvStr4", "4G000001");// ???
        item.put("rsrvStr3", "-9");
        item.put("rsrvStr6", "-1");
        item.put("endDate", isSub ? GetDateUtils.getMonthLastDayFormat() : GetDateUtils.getDate());
        item.put("rsrvStr5", isSub ? "4G000001" : "undefined");
        item.put("xDatatype", "NULL");
        item.put("rsrvStr7", "0");
        item.put("rsrvStr8", "");
        item.put("rsrvStr9", "4G00");
        item.put("rsrvStr10", msg.get("serialNumber"));// 号码
        item.put("rsrvValue", userInfo.get("userId"));// 传UserId
        item.put("rsrvValueCode", "NEXP");// 预留资料编码
        itemList.add(item);
        tradeOther.put("item", itemList);
        List<Map> tradeOtherList = new ArrayList<Map>();
        tradeOtherList.add(tradeOther);
        return tradeOtherList;
    }

    // TRADE_ITEM
    private List preDataForTradeItem(Map msg, Boolean isSub, Boolean isQuit) {
        Map tradeItem = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        if (!isSub || !isQuit) {// 副卡退出时不用
            Map item1 = new HashMap();
            item1.put("attrValue", "0");
            if (isSub) {// 副卡加入
                item1.put("attrCode", "DEPUTY_CARD_TAG");
            } else if (isQuit) {// 主卡退出
                item1.put("attrCode", "mainCardEffective120");
            } else {// 主卡加入
                item1.put("attrCode", "MAIN_CARD_TAG");
            }
            itemList.add(item1);
        }

        Map item2 = new HashMap();
        item2.put("attrValue", "0371");// ???//areaCode0371 msg.get("areaCode")
        item2.put("attrCode", "STANDARD_KIND_CODE");
        item2.put("xDatatype", "NULL");
        itemList.add(item2);

        //黑龙江行销APP支撑冰激凌融合及主副卡需求 by maly--start
        String markingTag = (String) msg.get("markingTag");
        if (StringUtils.isNotEmpty(markingTag) && "1".equals(markingTag)) {
            itemList.add(LanUtils.createTradeItem("MARKING_APP", "1"));
        }

        String saleModType = (String) msg.get("saleModType");
        if (StringUtils.isNotEmpty(saleModType)) {//销售模式类型
            String marketingChannelType = "0".equals(saleModType) ? "01" : "02";
            itemList.add(LanUtils.createTradeItem("MarketingChannelType", marketingChannelType));
        }
        //黑龙江行销APP支撑冰激凌融合及主副卡需求 by maly--end
        if ("0".equals(msg.get("deductionTag"))) {
            itemList.add(LanUtils.createTradeItem("FEE_TYPE", "1"));// RHA2017102300039-关于支撑通过标记控制代理商办理业务时是否扣周转金的需求
        }
        tradeItem.put("item", itemList);
        List<Map> tradeItemList = new ArrayList<Map>();
        tradeItemList.add(tradeItem);
        return tradeItemList;
    }

    // TRADE_SUB_ITEM
    private List preDataForTradeSubItem(Map msg, Boolean isSub, Boolean isQuit) {
        Map tradeSubItem = new HashMap();
        String[] codes = {"LINK_NAME", "LINK_PHONE"};
        List<Map> subitems = new ArrayList<Map>();
        if (isSub || !isQuit) {// 主卡退出不进
            Map item1 = new HashMap();
            item1.put("startDate", isQuit ? msg.get("userProStartDate") : GetDateUtils.getNextMonthFirstDayFormat());
            item1.put("attrValue", "0");
            item1.put("attrTypeCode", "0");
            item1.put("itemId", msg.get("userId"));
            item1.put("attrCode", isSub ? "DEPUTY_CARD_TAG" : "MAIN_CARD_TAG");
            if (isSub && isQuit) {// 副卡退出多一个endDate及MAINDEPUTY_CARD_DATE子项
                item1.put("endDate", GetDateUtils.getMonthLastDayFormat());
                Map item2 = new HashMap();
                item2.put("startDate", msg.get("userProStartDate"));
                item2.put("endDate", GetDateUtils.getDate());
                item2.put("attrValue", "0");
                item2.put("attrTypeCode", "0");
                item2.put("itemId", msg.get("userId"));
                item2.put("attrCode", "MAINDEPUTY_CARD_DATE");
                subitems.add(item2);
            }
            subitems.add(item1);
        }

        for (String code : codes) {
            Map item = new HashMap();
            if (code.equals("LINK_NAME")) {
                item.put("attrValue", msg.get("contactPerson"));// ???
            } else {
                item.put("attrValue", msg.get("contactPhone"));// ???
            }
            item.put("attrTypeCode", "0");
            item.put("attrCode", code);
            item.put("xDatatype", "NULL");
            item.put("itemId", msg.get("userId"));
            subitems.add(item);
        }

        tradeSubItem.put("item", subitems);
        List<Map> tradeSubItemList = new ArrayList<Map>();
        tradeSubItemList.add(tradeSubItem);
        return tradeSubItemList;
    }

    // TRADE_PRODUCT_TYPE------------
    private List preDataForTradeProductType(Map msg, Map productInfo, Map userInfo, Boolean isSub, Boolean isQuit) {
        Map tradeProductType = new HashMap();
        List<Map> items = new ArrayList<Map>();
        Map itemNew = new HashMap();
        if (isSub) {
            Map itemOld = new HashMap();
            itemOld.put("startDate", msg.get("userProStartDate"));
            itemOld.put("userId", userInfo.get("userId"));// ????
            itemOld.put("productId", userInfo.get("productId"));
            itemOld.put("modifyTag", "1");// 状态属性：0－增加 1－删除 2－修改
            itemOld.put("productMode", "00");
            itemOld.put("productTypeCode", "4G000001");
            itemOld.put("endDate", GetDateUtils.getMonthLastDayFormat());
            itemOld.put("xDatatype", "NULL");
            items.add(itemOld);
            itemNew.put("productMode", "00");
        } else {
            itemNew.put("productMode", "01");
        }
        if (!isSub && isQuit) {// 主卡退出时为1，产品id取三户
            itemNew.put("modifyTag", "1");// 状态属性：0－增加 1－删除 2－修改
            itemNew.put("productId", productInfo.get("productId"));// ???
            itemNew.put("startDate", productInfo.get("subProductActiveTime"));
            itemNew.put("endDate", GetDateUtils.getDate());// 结束为当前时间
        } else {// 其它为0，产品id取新传的值
            itemNew.put("modifyTag", "0");// 状态属性：0－增加 1－删除 2－修改
            itemNew.put("productId", productInfo.get("productId"));// ???
            itemNew.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
            itemNew.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
        }
        itemNew.put("userId", userInfo.get("userId"));
        itemNew.put("productTypeCode", "4G000001");
        itemNew.put("xDatatype", "NULL");
        items.add(itemNew);
        tradeProductType.put("item", items);
        List<Map> tradeProductTypeList = new ArrayList<Map>();
        tradeProductTypeList.add(tradeProductType);
        return tradeProductTypeList;
    }

    // TRADE_PRODUCT----------
    private List preDataForTradeProduct(Map msg, Map productInfo, Map userInfo, Boolean isSub, Boolean isQuit) {
        Map tradeProduct = new HashMap();
        List<Map> items = new ArrayList<Map>();
        Map itemNew = new HashMap();
        if (isSub) {// 副卡-删除产品
            Map itemOld = new HashMap();
            itemOld.put("startDate", msg.get("userProStartDate"));
            itemOld.put("productId", userInfo.get("productId"));
            itemOld.put("modifyTag", "1");// 状态属性：0－增加 1－删除 2－修改
            itemOld.put("productMode", "00");
            itemOld.put("endDate", GetDateUtils.getMonthLastDayFormat());// 结束为当月最后一天
            itemOld.put("brandCode", "4G00");
            itemOld.put("xDatatype", "NULL");
            itemOld.put("itemId", userInfo.get("custId"));// ????
            itemOld.put("userIdA", "-1");
            items.add(itemOld);
            itemNew.put("productMode", "00");// 副卡00
        } else {// 主卡都是01
            itemNew.put("productMode", "01");// 副卡01
        }
        if (!isSub && isQuit) {// 主卡退出时为1，产品id取三户
            itemNew.put("modifyTag", "1");// 状态属性：0－增加 1－删除 2－修改
            itemNew.put("productId", productInfo.get("productId"));// ???
            itemNew.put("startDate", productInfo.get("subProductActiveTime"));
            itemNew.put("endDate", GetDateUtils.getDate());// 结束为当前时间
        } else {// 其它为0，产品id取新传的值
            itemNew.put("modifyTag", "0");// 状态属性：0－增加 1－删除 2－修改
            itemNew.put("productId", productInfo.get("productId"));// ???
            itemNew.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
            itemNew.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
        }
        itemNew.put("brandCode", "4G00");
        itemNew.put("xDatatype", "NULL");
        itemNew.put("itemId", "");
        itemNew.put("userIdA", "-1");
        items.add(itemNew);
        tradeProduct.put("item", items);
        List<Map> tradeProductList = new ArrayList<Map>();
        tradeProductList.add(tradeProduct);
        return tradeProductList;
    }

    // TRADE_SP------------
    private List preDataForTradeSp(List<Map> tradeSpList, String productId) {
        Map tradeItem = null;
        List<Map> itemList = new ArrayList<Map>();
        if (null != tradeSpList && tradeSpList.size() != 0) {
            for (Map tradeSp : tradeSpList) {
                Map item = new HashMap();
                item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                item.put("spId", tradeSp.get("spId"));
                item.put("spProductId", tradeSp.get("spProductId"));
                item.put("productId", productId);
                item.put("modifyTag", "0");// 状态属性：0－增加 1－删除 2－修改
                item.put("partyId", tradeSp.get("partyId"));
                item.put("firstBuyTime", GetDateUtils.getNextMonthFirstDayFormat());
                item.put("endDate", MapUtils.getDefault(tradeSp, "activityTime", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME));
                item.put("xDatatype", "NULL");
                item.put("remark", "");
                item.put("packageId", tradeSp.get("packageId"));
                item.put("itemId", "");
                item.put("spServiceId", tradeSp.get("spServiceId"));
                itemList.add(item);
            }
            tradeItem = new HashMap();
            tradeItem.put("item", itemList);
        }
        List<Map> list = new ArrayList<Map>();
        list.add(tradeItem);
        return list;
    }

    // TRADE_DISCNT------------
    private List preDataForTradeDiscnt(String userId, List<Map> tradeDiscntList) {
        Map tradeItem = null;
        List<Map> itemList = new ArrayList<Map>();
        if (null != tradeDiscntList && tradeDiscntList.size() != 0) {
            for (Map discnt : tradeDiscntList) {
                Map item = new HashMap();
                item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                item.put("discntCode", discnt.get("discntCode"));
                item.put("productId", discnt.get("productId"));
                item.put("modifyTag", "0");// 状态属性：0－增加 1－删除 2－修改
                item.put("endDate", MapUtils.getDefault(discnt, "activityTime", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME));
                item.put("id", userId);
                item.put("relationTypeCode", "");
                item.put("xDatatype", "NULL");
                item.put("idType", "1");// ???
                item.put("packageId", discnt.get("packageId"));
                item.put("itemId", MapUtils.getDefault(discnt, "itemId", ""));
                item.put("specTag", "0");// 0
                item.put("userIdA", "-1");
                itemList.add(item);
            }
            tradeItem = new HashMap();
            tradeItem.put("item", itemList);
        }
        List<Map> tradeItemList = new ArrayList<Map>();
        tradeItemList.add(tradeItem);
        return tradeItemList;
    }

    // TRADE_SVC------------
    private List preDataForTradeSvc(List<Map> tradeSvcList) {
        Map tradeItem = null;
        List<Map> itemList = new ArrayList<Map>();
        if (null != tradeSvcList && tradeSvcList.size() != 0) {
            for (Map tradeSvc : tradeSvcList) {
                Map item = new HashMap();
                item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                item.put("modifyTag", "0");// 状态属性：0－增加 1－删除 2－修改
                item.put("productId", tradeSvc.get("productId"));
                item.put("endDate", MapUtils.getDefault(tradeSvc, "activityTime", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME));
                item.put("xDatatype", "NULL");
                item.put("packageId", tradeSvc.get("packageId"));
                item.put("itemId", "");
                item.put("serviceId", tradeSvc.get("serviceId"));// ???
                item.put("userIdA", "-1");
                itemList.add(item);
            }
            tradeItem = new HashMap();
            tradeItem.put("item", itemList);
        }
        List<Map> list = new ArrayList<Map>();
        list.add(tradeItem);
        return list;

    }

    // 拼装TradePayrelation tradePayrelation
    private List preDataForTradePayrelation(Map userInfo, Map acctInfo, Map msg) {
        Map tradeItem = new HashMap();
        ;
        List<Map> itemList = new ArrayList<Map>();
        // 结果副卡帐户
        Map oldItem = new HashMap();
        oldItem.put("xDatatype", "NULL");
        oldItem.put("userId", userInfo.get("userId"));
        oldItem.put("acctId", acctInfo.get("acctId"));
        oldItem.put("payitemCode", "-1");
        oldItem.put("acctPriority", "0");
        oldItem.put("userPriority", "0");
        oldItem.put("bindType", "1");
        oldItem.put("startAcycId", acctInfo.get("startAcycId"));
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat sf = new SimpleDateFormat("yyyyMM");
        calendar.setTime(new Date());
        oldItem.put("endAcycId", sf.format(calendar.getTime()));
        oldItem.put("defaultTag", "1");
        oldItem.put("limitType", "0");
        oldItem.put("limit", "0");
        oldItem.put("complementTag", "0");
        oldItem.put("addupMonths", "0");
        oldItem.put("addupMethod", "0");
        // oldItem.put("payrelationId", payRelId);
        oldItem.put("actTag", "1");
        itemList.add(oldItem);
        // 新增合帐
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("userId", userInfo.get("userId"));
        item.put("acctId", msg.get("mainAcctId"));
        item.put("payitemCode", "-1");
        item.put("acctPriority", "0");
        item.put("userPriority", "0");
        item.put("bindType", "1");
        calendar.add(Calendar.MONTH, 1);
        item.put("startAcycId", sf.format(calendar.getTime()));
        // item.put("endAcycId", mainNumberUserInfo.get("endAcycId"));203712
        item.put("endAcycId", "203712");
        item.put("defaultTag", "1");
        item.put("limitType", "0");
        item.put("limit", "0");
        item.put("complementTag", "0");
        item.put("addupMonths", "0");
        item.put("addupMethod", "0");
        item.put("payrelationId", msg.get("payRelId"));
        item.put("actTag", "1");
        itemList.add(item);
        tradeItem.put("item", itemList);
        List<Map> list = new ArrayList<Map>();
        list.add(tradeItem);
        return list;
    }

    private void preNewProductInfo(List<Map> productInfo, Map msg) {

        String provinceCode = "00" + (String) msg.get("province");
        List<Map> productTypeList = new ArrayList<Map>();
        List<Map> productList = new ArrayList<Map>();

        List<Map> discntList = new ArrayList<Map>();
        List<Map> svcList = new ArrayList<Map>();
        List<Map> spList = new ArrayList<Map>();
        String monthLasttDay = "20501231235959";
        String monthFirstDay = GetDateUtils.getNextMonthFirstDayFormat();

        for (int i = 0; i < productInfo.size(); i++) {
            List<Map> packageElement = (List<Map>) productInfo.get(i).get("packageElement");
            String productId = String.valueOf(productInfo.get(i).get("productId"));
            String firstMonBillMode = String.valueOf(productInfo.get(i).get("firstMonBillMode"));
            Map productTpye = new HashMap();
            Map product = new HashMap();

            String commodityId = productId;
            String strBrandCode = "";
            String strProductTypeCode = "";
            String strProductMode = "";

            Map proparam = new HashMap();
            proparam.put("PROVINCE_CODE", provinceCode);
            proparam.put("PRODUCT_ID", productId);
            proparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
            if ("1".equals(productInfo.get(i).get("productMode"))) {
                proparam.put("PRODUCT_MODE", "00");
                proparam.put("FIRST_MON_BILL_MODE", "02");
            } else {
                proparam.put("PRODUCT_MODE", "01");
                proparam.put("FIRST_MON_BILL_MODE", null);
            }
            if(StringUtils.isNotEmpty(firstMonBillMode)){
                proparam.put("FIRST_MON_BILL_MODE",firstMonBillMode );
            }
            DealNewCbssProduct n25Dao = new DealNewCbssProduct();
            List<Map> productInfoResult = n25Dao.qryDefaultPackageElement(proparam);
            if (productInfoResult == null || productInfoResult.size() == 0) {
                throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品信息");
            }
            productId = String.valueOf(productInfoResult.get(0).get("PRODUCT_ID"));
            if ("-1".equals(productId)) {
                productId = String.valueOf(productInfoResult.get(1).get("PRODUCT_ID"));
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
                    spList.add(sp);
                }
            }

            strProductMode = String.valueOf(productInfoResult.get(0).get("PRODUCT_MODE"));
            strBrandCode = (String) productInfoResult.get(0).get("BRAND_CODE");
            strProductTypeCode = (String) productInfoResult.get(0).get("PRODUCT_TYPE_CODE");
            //strBrandCode = getValueFromItem("BRAND_CODE", productInfoResult);
            //strProductTypeCode = getValueFromItem("PRODUCT_TYPE_CODE", productInfoResult);

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
                    peparam.put("PRODUCT_ID", productId);
                    peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                    peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                    peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                    List<Map> packageElementInfo = n25Dao.queryPackageElementInfo(peparam);
                    if (packageElementInfo != null && packageElementInfo.size() > 0) {
                        for (Map packageMap : packageElementInfo) {
                            if ("D".equals(packageMap.get("ELEMENT_TYPE_CODE"))) {
                                String elementId = String.valueOf(packageMap.get("ELEMENT_ID"));
                                Map dis = new HashMap();
                                dis.put("productId", packageMap.get("PRODUCT_ID"));
                                dis.put("packageId", packageMap.get("PACKAGE_ID"));
                                dis.put("discntCode", elementId);
                                dis.put("productMode", packageMap.get("PRODUCT_MODE"));
                                Map discntDateMap = getDiscntEffectTime(elementId, monthFirstDay, monthLasttDay);
                                dis.put("activityStarTime", discntDateMap.get("monthFirstDay"));
                                dis.put("activityTime", discntDateMap.get("monthLasttDay"));
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
                                spItemParam.put("SPSERVICEID", packageMap.get("ELEMENT_ID"));
                                List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                                if (spItemInfoResult.size() == 0 || spItemInfoResult == null) {
                                    throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【"
                                            + packageMap.get("ELEMENT_ID") + "】的产品属性信息");
                                }
                                for (int l = 0; l < spItemInfoResult.size(); l++) {
                                    Map spItemInfo = spItemInfoResult.get(l);
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
                                spList.add(sp);
                            }
                        }
                    } else {
                        throw new EcAopServerBizException("9999", "在产品映射表中未查询到元素ID【"
                                + packageElement.get(n).get("elementId") + "】的属性信息");
                    }
                }
            }
        }
        // 选速率,默认42M

        List<Map> paraList = (List<Map>) msg.get("para");// 处理速率和remark
        String speed = "42";
        if (null != paraList && !paraList.isEmpty()) {
            for (Map para : paraList) {
                if ("SPEED".equals(para.get("paraId"))) {
                    speed = (String) para.get("paraValue");
                }
            }
        }

        // 处理活动节点
        preActiveityInfoData(msg, discntList, svcList, spList, productTypeList,
                productList, provinceCode, msg.get("eparchyCode"), monthLasttDay, monthFirstDay);
        // 新速率处理
        new NewProductUtils().chooseSpeed(svcList, speed);
        discntList = NewProductUtils.newDealRepeat(discntList, "discntList");
        svcList = NewProductUtils.newDealRepeat(svcList, "svcList");
        spList = NewProductUtils.newDealRepeat(spList, "spList");
        productTypeList = NewProductUtils.newDealRepeat(productTypeList, "productTypeList");
        productList = NewProductUtils.newDealRepeat(productList, "productList");
        msg.put("discntList", discntList);
        msg.put("svcList", svcList);
        msg.put("spList", spList);
        msg.put("productTypeList", productTypeList);
        msg.put("productList", productList);
    }
    /**
     * 处理附加产品资费的生失效时间
     * 可以手动传入产品或活动的生失效时间
     */
    private Map getDiscntEffectTime(String discntId, String monthFirstDay, String monthLasttDay) {

        String monthLastDay = "";
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        Map actiPeparam = new HashMap();
        actiPeparam.put("DISCNT_CODE", discntId);
        List<Map> activityList = n25Dao.queryDiscntData(actiPeparam);
        Map activityListMap;
        if (activityList != null && activityList.size() > 0) {
            activityListMap = activityList.get(0);
        } else {
            throw new EcAopServerBizException("9999", "在资费信息表中未查询到ID为【" + discntId + "】的资费信息");
        }
        try {
            String resActivityper = String.valueOf(activityListMap.get("END_OFFSET"));// 失效效偏移时间
            String endUnit = String.valueOf(activityListMap.get("END_UNIT"));// '0:天 1:自然天 2:月 3:自然月 4:年
            // 5:自然年';
            String endEnableTag = String.valueOf(activityListMap.get("END_ENABLE_TAG"));// 针对结束时间 0-绝对时间（看开始时间） 1-相对时间
            if (!"null".equals(resActivityper) && !"null".equals(endUnit) && !"null".equals(endEnableTag)
                    && "1".equals(endEnableTag)) {
                monthLastDay = GetDateUtils.getSpecifyDateTime(Integer.parseInt(endEnableTag),
                        Integer.parseInt(endUnit), GetDateUtils.transDate(monthFirstDay, 19), Integer.parseInt(resActivityper)
                );

                // 结束月最后一天
                monthLasttDay = GetDateUtils.getSpecifyDateTime(1, 6, monthLastDay, -1);
                monthLasttDay = GetDateUtils.transDate(monthLasttDay, 14);
            }
            if (monthLasttDay.length() > 19) {
                monthLasttDay = monthLasttDay.substring(0, 19);
            }
            actiPeparam.put("monthLasttDay", monthLasttDay);
            actiPeparam.put("monthFirstDay", monthFirstDay);
            return actiPeparam;
        } catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "获取资费信息生失效时间失败，资费是：" + discntId);
        }
    }

    private void getSubProInfoForMainQuit(Map productInfo, Map userInfo) {
        List<Map> discntInfoList = (List) userInfo.get("discntInfo");
        if (null == discntInfoList || discntInfoList.size() < 1) {
            throw new EcAopServerBizException("9999", "调主卡三户未返回主卡discntInfo点节信息。");
        }
        for (Map discntInfo : discntInfoList) {
            String productId = (String) discntInfo.get("productId");
            if (null == productId || "".equals(productId) || !productId.equals(productInfo.get("productId"))) {
                continue;
            }
            productInfo.put("subProductActiveTime", discntInfo.get("startDate"));
            return;
        }
        throw new EcAopServerBizException("9999", "主卡三户discntInfo点节下未找到主副卡产品ID【" + productInfo.get("productId")
                + "】的信息。");
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[]{PARAM_ARRAY[i]});
        }
    }

    private String getValueFromItem(String attrCode, List<Map> infoList) {
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
}
