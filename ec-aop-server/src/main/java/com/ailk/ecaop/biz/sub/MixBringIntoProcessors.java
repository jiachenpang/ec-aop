package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.DefaultExchange;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.product.DateUtils;
import com.ailk.ecaop.biz.product.DealNewCbssProduct;
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

/**
 * 用于处理融合纳入
 * 三户接口 虚拟用户、成员用户预提交、正式提交
 * 新产品逻辑
 *
 * @auther zhousq
 * @create 2017_08_09_11:28
 */
@EcRocTag("MixBringInto")
public class MixBringIntoProcessors extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.masb.chph.gifa.ParametersMapping",
            "ecaop.trade.cbss.checkUserParametersMapping",
            "ecaop.smcom.preSub.ParametersMapping",
            "ecaop.trades.sccc.cancel.paramtersmapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[5];
    private final DealNewCbssProduct n25Dao = new DealNewCbssProduct();
    private final Exchange copyExchange = new DefaultExchange(); // 用于生成itemId
    LanUtils lan = new LanUtils();

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        // 调用主号码三户信息
        MixNumberthreepart(ExchangeUtils.ofCopy(exchange, msg), msg);
        // 调用融合号码三户信息
        PhoneNumberthreepart(ExchangeUtils.ofCopy(exchange, msg), msg);
        // 虚拟用户预提交
        Map mixMap = preMixNumberInfo(exchange, msg);
        // 成员用户预提交
        Map phoneMap = prePhoneNumberInfo(exchange, msg);
        // 正式提交接口
        orderSub(mixMap, phoneMap, exchange);
    }

    private void orderSub(Map mixMap, Map phoneMap, Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        // 准备参数
        msg.putAll(preOrderSubMessage(mixMap, phoneMap));
        lan.preData(pmp[3], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
        lan.xml2Json("ecaop.trades.sccc.cancel.template", exchange);

        // 拼装返回参数
        Object provOrderId = ((ArrayList<Map>) mixMap.get("rspInfo")).get(0).get("bssOrderId");
        Message out = new DefaultMessage();
        out.setBody(MapUtils.asMap("provOrderId", provOrderId));
        exchange.setOut(out);

    }

    /**
     * 拼装正式提交参数
     */
    private Map preOrderSubMessage(Map mixMap, Map phoneMap) {
        Map outMap = new HashMap();
        List<Map> subOrderSubReq = new ArrayList<Map>();
        int totalFee = 0;
        List<Map> rspInfo = (ArrayList<Map>) mixMap.get("rspInfo");
        outMap.put("provOrderId", rspInfo.get(0).get("bssOrderId"));
        outMap.put("orderNo", rspInfo.get(0).get("provOrderId"));
        List<Map> provinceOrderInfo = (ArrayList<Map>) rspInfo.get(0).get("provinceOrderInfo");
        if (!IsEmptyUtils.isEmpty(provinceOrderInfo)) {
            totalFee = Integer.valueOf(provinceOrderInfo.get(0).get("totalFee").toString());
        }
        subOrderSubReq.add(dealFeelInfo(rspInfo));
        // 移网订单信息
        List<Map> phoneRspInfo = (ArrayList<Map>) phoneMap.get("rspInfo");
        List<Map> phoneProvinceOrderInfo = (ArrayList<Map>) phoneRspInfo.get(0).get("provinceOrderInfo");
        if (!IsEmptyUtils.isEmpty(phoneProvinceOrderInfo)) {
            totalFee = totalFee + Integer.valueOf(phoneProvinceOrderInfo.get(0).get("totalFee").toString());
        }
        subOrderSubReq.add(dealFeelInfo(phoneRspInfo));
        outMap.put("origTotalFee", totalFee);
        outMap.put("subOrderSubReq", subOrderSubReq);
        return outMap;
    }

    private Map dealFeelInfo(List<Map> rspInfo) {
        Map subOrderSubMap = new HashMap();
        List<Map> provinceOrderInfo = (ArrayList<Map>) rspInfo.get(0).get("provinceOrderInfo");
        if (!IsEmptyUtils.isEmpty(provinceOrderInfo)) {
            List<Map> feeInfo = (ArrayList<Map>) provinceOrderInfo.get(0).get("preFeeInfoRsp");
            if (null != feeInfo && 0 != feeInfo.size()) {
                for (Map fee : feeInfo) {
                    fee.put("feeCategory", fee.get("feeMode"));
                    fee.put("feeId", fee.get("feeTypeCode"));
                    fee.put("feeDes", fee.get("feeTypeName"));
                    fee.put("origFee", fee.get("oldFee"));
                    fee.put("isPay", "0");
                    fee.put("calculateTag", "N");
                    fee.put("payTag", "1");
                    fee.put("calculateId", GetSeqUtil.getSeqFromCb());
                    fee.put("calculateDate", DateUtils.getDate());
                }
            }
            subOrderSubMap.put("feeInfo", feeInfo);
        }
        subOrderSubMap.put("subProvinceOrderId", rspInfo.get(0).get("bssOrderId"));
        subOrderSubMap.put("subOrderId", rspInfo.get(0).get("provOrderId"));

        return subOrderSubMap;
    }

    private Map prePhoneNumberInfo(Exchange exchange, Map msg) throws Exception {
        Exchange copyExchange = ExchangeUtils.ofCopy(exchange, msg);
        Map userInfo = (Map) msg.get("userInfo");
        Object apptx = exchange.getIn().getBody(Map.class).get("apptx");
        List<Map> oldProducts = (List<Map>) msg.get("oldProductInfo");
        List<Map> productType = new ArrayList<Map>();
        List<Map> product = new ArrayList<Map>();
        List<Map> packageElement = new ArrayList<Map>();
        List<Map> discntList = new ArrayList<Map>();
        List<Map> svcList = new ArrayList<Map>();
        List<Map> spList = new ArrayList<Map>();
        List<Map> tradeOtherList = new ArrayList<Map>();
        List<Map> tradeUserList = new ArrayList<Map>();
        Map ext = new HashMap();
        List<Map> productList = (List<Map>) msg.get("productInfo");
        if (IsEmptyUtils.isEmpty(productList)) {
            String orderid = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, msg),
                    "seq_trade_id",
                    1).get(0);
            msg.put("tradeId", orderid);
            ext.put("tradePayrelation", preDataForTradePayrelation(msg));
            ext.put("tradeItem", preDataForTradeItem(msg, false, true));
            ext.put("tradeSubItem", preDataForTradeSubItem(msg, false));
            msg.put("ext", ext);
            Map base = preBaseData(msg, exchange.getAppCode(), false);
            msg.put("base", base);
        }
        else {
            for (int i = 0; i < productList.size(); i++) {
                Map productInfo = productList.get(i);
                String newProductId = (String) productInfo.get("newProductId");
                String oldProductId = (String) productInfo.get("oldProductId");
                String firstMonBillMode = "02";// 订购时默认首月全量全价
                String productMode = "00";
                String acceptDate = GetDateUtils.transDate(GetDateUtils.getDate(), 19);
                // String brandCode = String.valueOf(msg.get("brandCode"));
                String provinceCode = "00" + msg.get("province");
                msg.put("newProductId", newProductId);
                if (oldProductId.equals(msg.get("productId"))) {
                    // 查询产品的属性信息
                    Map inMap = new HashMap();
                    inMap.put("PRODUCT_ID", oldProductId);
                    inMap.put("PRODUCT_MODE", productMode);
                    inMap.put("PROVINCE_CODE", provinceCode);
                    inMap.put("EPARCHY_CODE", msg.get("eparchyCode"));
                    Map productInfoResult = n25Dao.queryProductInfoByProductId(inMap);
                    System.out.println("zsqtest9" + apptx + productInfoResult);
                    String unBookProductId = String.valueOf(productInfoResult.get("PRODUCT_ID")); // 数据库值为数字类型
                    String unBookProductMode = (String) productInfoResult.get("PRODUCT_MODE");
                    String unBookBrandCode = (String) productInfoResult.get("BRAND_CODE");
                    String unBookProductTypeCode = (String) productInfoResult.get("PRODUCT_TYPE_CODE");
                    // 退订的产品生效时间取原生效时间,失效时间为本月底
                    String productStartDate = acceptDate;
                    String productEndDate = GetDateUtils.getMonthLastDay();
                    for (int m = 0; m < oldProducts.size(); m++) {
                        Map pInfo = oldProducts.get(m);
                        if (unBookProductId.equals(pInfo.get("productId"))) {
                            productStartDate = (String) pInfo.get("productActiveTime");
                            productStartDate = GetDateUtils.transDate(productStartDate, 19);
                        }
                    }
                    Map paraMap = new HashMap();
                    paraMap.put("productMode", unBookProductMode);
                    paraMap.put("productId", unBookProductId);
                    paraMap.put("productTypeCode", unBookProductTypeCode);
                    paraMap.put("brandCode", unBookBrandCode);
                    paraMap.put("modifyTag", "1"); // 退订
                    paraMap.put("productStartDate", productStartDate);
                    paraMap.put("productEndDate", productEndDate);
                    // 拼装退订的产品节点
                    preProductItem(product, productType, paraMap, msg);

                    Map tradeOther = new HashMap();
                    tradeOther.put("xDatatype", "NULL");
                    tradeOther.put("modifyTag", "1");
                    tradeOther.put("rsrvStr1", unBookProductId);
                    tradeOther.put("rsrvStr2", unBookProductMode);
                    tradeOther.put("rsrvStr3", "-9");
                    tradeOther.put("rsrvStr4", unBookProductTypeCode);
                    tradeOther.put("rsrvStr5", unBookProductTypeCode);
                    tradeOther.put("rsrvStr6", "-1");
                    tradeOther.put("rsrvStr7", "0");
                    tradeOther.put("rsrvStr8", "");
                    tradeOther.put("rsrvStr9", unBookBrandCode);// BRAND code
                    tradeOther.put("rsrvStr10", msg.get("serialNumber"));// 号码
                    tradeOther.put("rsrvValueCode", "NEXP"); //
                    tradeOther.put("rsrvValue", msg.get("userId")); // USER_ID
                    tradeOther.put("startDate", productStartDate);
                    tradeOther.put("endDate", productEndDate);
                    tradeOtherList.add(tradeOther);
                    System.out.println("zsqtest12" + apptx + tradeOtherList);

                }
                if (!newProductId.equals(msg.get("productId"))) {
                    String isFinalCode = "";
                    String productStartDate = GetDateUtils.getNextMonthFirstDayFormat();
                    String productEndDate = "2050-12-31 23:59:59";
                    String bookProductId = "";
                    String bookProductMode = "";
                    String bookBrandCode = "";
                    String bookProductTypeCode = "";
                    // 查询产品的默认属性信息
                    Map temp = new HashMap();
                    temp.put("PROVINCE_CODE", provinceCode);
                    temp.put("PRODUCT_ID", newProductId);
                    temp.put("EPARCHY_CODE", msg.get("eparchyCode"));
                    temp.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                    temp.put("PRODUCT_MODE", productMode);
                    List<Map> productInfoResult = n25Dao.qryDefaultPackageElement(temp);
                    // 未查到产品下的默认元素
                    if (IsEmptyUtils.isEmpty(productInfoResult)) {
                        if ("00".equals(productMode)) { // 主产品下没有默认元素报错
                            throw new EcAopServerBizException("9999", "根据产品Id [" + newProductId + "] 未查询到产品信息");
                        }
                        // 未查询到附加产品的默认资费或服务,不报错,去TD_B_PRODUCT表查询,产品不存在就抛错,存在继续执行
                        // by wangmc 20170331
                        Map productInfos = n25Dao.queryProductInfoByProductId(temp);
                        if (!IsEmptyUtils.isEmpty(productInfos)) {
                            bookProductId = String.valueOf(productInfos.get("PRODUCT_ID"));
                            bookProductMode = (String) productInfos.get("PRODUCT_MODE");
                            bookBrandCode = (String) productInfos.get("BRAND_CODE");
                            bookProductTypeCode = (String) productInfos.get("PRODUCT_TYPE_CODE");
                        }
                        else {
                            throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + newProductId + "】的产品信息");
                        }
                    }
                    else {
                        bookProductId = String.valueOf(productInfoResult.get(0).get("PRODUCT_ID"));
                        bookProductMode = (String) productInfoResult.get(0).get("PRODUCT_MODE");
                        bookBrandCode = (String) productInfoResult.get(0).get("BRAND_CODE");
                        bookProductTypeCode = (String) productInfoResult.get(0).get("PRODUCT_TYPE_CODE");
                    }
                    if ("00".equals(productMode)) { // 订购的是主产品需要修改用户使用的主套餐
                        newProductId = bookProductId;
                    }
                    List<Map> allProductInfo = new ArrayList<Map>();
                    if (!IsEmptyUtils.isEmpty(productInfoResult)) {
                        // 选择速率
                        productInfoResult = chooseSpeedByUser(productInfoResult, (List<Map>) userInfo.get("svcInfo"));
                        // 处理国际漫游服务和要继承的资费
                        productInfoResult = preDealProductInfo(productInfoResult, bookProductId, userInfo, msg);
                        // 拼装产品默认属性的节点,放到下面统一处理
                        allProductInfo.addAll(productInfoResult);
                    }
                    // 有附加包
                    if (!IsEmptyUtils.isEmpty(packageElement)) {
                        List<Map> packageElementInfo = new ArrayList<Map>();
                        for (Map elementMap : packageElement) {
                            Map peparam = new HashMap();
                            peparam.put("PROVINCE_CODE", provinceCode);
                            peparam.put("PRODUCT_ID", newProductId);
                            peparam.put("EPARCHY_CODE", msg.get("eparchyCode"));
                            peparam.put("PACKAGE_ID", elementMap.get("packageId"));
                            peparam.put("ELEMENT_ID", elementMap.get("elementId"));
                            List<Map> packEleInfo = n25Dao.queryPackageElementInfo(peparam);
                            if (!IsEmptyUtils.isEmpty(packEleInfo)) {
                                packageElementInfo.addAll(packEleInfo);
                            }
                        }
                        if (!IsEmptyUtils.isEmpty(packageElementInfo)) {
                            // 拼装附加包的属性信息
                            allProductInfo.addAll(packageElementInfo);
                        }
                    }
                    if (!IsEmptyUtils.isEmpty(allProductInfo)) {
                        preProductInfo(allProductInfo, discntList, svcList, spList, "0", isFinalCode, productStartDate,
                                productEndDate, msg);
                    }
                    // 此时的资费、服务和sp节点中都还只有产品,包和元素 ,已经全处理了,20170401FIXME
                    Map paraMap = new HashMap();
                    paraMap.put("productMode", bookProductMode);
                    paraMap.put("productId", bookProductId);
                    paraMap.put("productTypeCode", bookProductTypeCode);
                    paraMap.put("brandCode", bookBrandCode);
                    paraMap.put("modifyTag", "0"); // 订购
                    paraMap.put("productStartDate", productStartDate);
                    paraMap.put("productEndDate", productEndDate);
                    paraMap.put("userId", msg.get("userId"));
                    paraMap.put("eparchyCode", msg.get("eparchyCode"));
                    // 拼装订购产品节点
                    preProductItem(product, productType, paraMap, msg);
                    if ("00".equals(productMode)) {// 主产品需要tradeUser节点
                        Map userItem = new HashMap();
                        userItem.put("userId", msg.get("userId"));
                        userItem.put("productId", bookProductId);
                        userItem.put("brandCode", bookBrandCode);
                        userItem.put("netTypeCode", "0050");
                        userItem.put("xDatatype", "NULL");
                        if (StringUtils.isNotEmpty((String) msg.get("recomPersonId"))) {
                            userItem.put("developStaffId", "" + msg.get("recomPersonId"));
                            userItem.put("developDepartId", "" + msg.get("channelId"));
                        }
                        tradeUserList.add(userItem);
                    }
                }
            }
            // 去重
            discntList = NewProductUtils.newDealRepeat(discntList, new String[] { "productId", "packageId",
                    "discntCode",
            "modifyTag" });
            svcList = NewProductUtils.newDealRepeat(svcList, new String[] { "productId", "packageId", "serviceId",
            "modifyTag" });
            spList = NewProductUtils.newDealRepeat(spList, new String[] { "productId", "packageId", "spServiceId",
            "modifyTag" });
            productType = NewProductUtils.newDealRepeat(productType, new String[] { "productId", "productMode",
                    "productTypeCode", "modifyTag" });
            product = NewProductUtils.newDealRepeat(productList, new String[] { "productId", "productMode",
                    "brandCode", "modifyTag" });
            ext.put("tradeUser", preDataUtil(tradeUserList));
            ext.put("tradeProductType", preDataUtil(productType));
            ext.put("tradeProduct", preDataUtil(product));
            ext.put("tradeDiscnt", preDataUtil(discntList));
            ext.put("tradeSvc", preDataUtil(svcList));
            ext.put("tradeSp", preDataUtil(spList));
            ext.put("tradeOther", preDataUtil(tradeOtherList));
            ext.put("tradePayrelation", preDataForTradePayrelation(msg));
            ext.put("tradeItem", preDataForTradeItem(msg, true, true));
            ext.put("tradeSubItem", preDataForTradeSubItem(msg, false));
            msg.put("ext", ext);
            String orderid = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, msg),
                    "seq_trade_id",
                    1).get(0);
            msg.put("tradeId", orderid);
            Map base = preBaseData(msg, exchange.getAppCode(), false);
            List<Map> tradeProductItem = (List<Map>) ((Map) ext.get("tradeProduct")).get("item");
            for (Map item : tradeProductItem) {
                if ("00".equals(item.get("productMode"))) {
                    base.put("productId", item.get("productId"));
                }
            }
            msg.put("base", base);
        }
        msg.put("serviceClassCode", "00CP");
        msg.put("operTypeCode", "0");
        msg.put("ordersId", msg.get("ordersId"));
        lan.preData(pmp[2], copyExchange);
        CallEngine.wsCall(copyExchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.trades.smcom.preSub.template", copyExchange);

        Map retMap = copyExchange.getOut().getBody(Map.class);
        List<Map> rspInfoList = (ArrayList<Map>) retMap.get("rspInfo");
        if (IsEmptyUtils.isEmpty(rspInfoList)) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "核心系统未返回订单信息.");
        }
        return retMap;
    }

    /**
     * 将节点放入item中返回
     *
     * @param dataList
     * @param dataKey
     * @return
     */
    private Map preDataUtil(List<Map> dataList) {
        Map dataMap = new HashMap();
        dataMap.put("item", dataList);
        return dataMap;
    }

    /**
     * 根据查询到的元素属性,拼装节点信息 discntList,svcList,spList
     *
     * @param productInfoResult
     * @param productId
     * @param discntList
     * @param svcList
     * @param spList
     * @param modifyTag
     *            0-订购,1-退订
     * @param isFinalCode
     *            N,X-生效失效时间按配置计算 Y-用传进来的时间(主产品的时候无值)
     * @param startDate
     *            产品的生效时间
     * @param endDate
     *            产品的失效时间
     */
    private void preProductInfo(List<Map> productInfoResult, List<Map> discntList, List<Map> svcList, List<Map> spList,
            String modifyTag, String isFinalCode, String startDate, String endDate, Map msg) {
        String eparchyCode = (String) msg.get("eparchyCode");
        String userId = (String) msg.get("userId");
        for (int j = 0; j < productInfoResult.size(); j++) {
            if ("D".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                String elementId = String.valueOf(productInfoResult.get(j).get("ELEMENT_ID"));
                Map dis = new HashMap();
                dis.put("id", userId);
                dis.put("idType", "1");
                dis.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                dis.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                dis.put("discntCode", elementId);
                dis.put("specTag", "1");
                dis.put("relationTypeCode", "");
                dis.put("itemId", TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID"));
                dis.put("modifyTag", modifyTag);
                dis.put("startDate", startDate);
                dis.put("endDate", endDate);
                // 主产品下的所有资费全按照偏移计算
                // 订购主产品下资费全部按照偏移计算
                String productMode = String.valueOf(productInfoResult.get(j).get("PRODUCT_MODE"));
                if ("00".equals(productMode) && "0".equals(modifyTag) || StringUtils.isNotEmpty(isFinalCode)
                        && !"Y".equals(isFinalCode)) {
                    Map discntDateMap = NewProductUtils.getMainProDiscntEffectTime4ChangePro(elementId, startDate,
                            endDate);
                    if ("false".equals(discntDateMap.get("discntEndTag"))) {// 如果资费没失效,则按照偏移计算
                        dis.put("startDate", discntDateMap.get("monthFirstDay"));
                        dis.put("endDate", discntDateMap.get("monthLasttDay"));
                    }
                }
                if ("5702000".equals(elementId)) {
                    dis.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                }
                dis.put("userIdA", "-1");
                dis.put("xDatatype", "NULL");
                discntList.add(dis);
            }
            if ("S".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                Map svc = new HashMap();
                svc.put("userId", userId);
                svc.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                svc.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                svc.put("serviceId", productInfoResult.get(j).get("ELEMENT_ID"));
                svc.put("itemId", TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID"));
                svc.put("modifyTag", modifyTag);
                svc.put("startDate", startDate);
                svc.put("endDate", endDate);
                svc.put("userIdA", "-1");
                svc.put("xDatatype", "NULL");
                svcList.add(svc);
            }
            if ("X".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                String spId = "-1";
                String partyId = "-1";
                String spProductId = "-1";
                Map spItemParam = new HashMap();
                spItemParam.put("PTYPE", "X");
                spItemParam.put("PROVINCE_CODE", msg.get("provinceCode"));
                spItemParam.put("SPSERVICEID", productInfoResult.get(j).get("ELEMENT_ID"));
                List<Map> spItemInfoResult = n25Dao.newQuerySPServiceAttr(spItemParam);
                if (IsEmptyUtils.isEmpty(spItemInfoResult)) {
                    throw new EcAopServerBizException("9999", "在SP表中未查询到【" + productInfoResult.get(j).get("ELEMENT_ID")
                            + "】的元素属性信息");
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
                sp.put("userId", userId);
                sp.put("serialNumber", msg.get("serialNumber"));
                sp.put("firstBuyTime", startDate);
                sp.put("paySerialNumber", msg.get("serialNumber"));
                sp.put("startDate", startDate);
                sp.put("enddate", "2050-12-31 23:59:59");
                sp.put("updateTime", startDate);
                sp.put("remark", "");
                sp.put("modifyTag", modifyTag);
                sp.put("payUserId", userId);
                sp.put("itemId", TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID"));
                sp.put("userIdA", "-1");
                sp.put("xDatatype", "NULL");
                spList.add(sp);
            }
        }
    }

    private Map preMixNumberInfo(Exchange exchange, Map msg) throws Exception {
        Exchange copyExchange = ExchangeUtils.ofCopy(exchange, msg);
        Map ext = new HashMap();
        String orderId = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, msg),
                "seq_trade_id", 1).get(0);
        msg.put("tradeId", orderId);
        ext.put("tradeRelation", preDataForTradeRelation(msg));
        ext.put("tradeItem", preDataForTradeItem(msg, false, false));
        ext.put("tradeSubItem", preDataForTradeSubItem(msg, true));
        msg.put("ext", ext);
        Map base = preBaseData(msg, exchange.getAppCode(), true);
        msg.put("base", base);
        msg.put("serviceClassCode", "00CP");
        msg.put("operTypeCode", "0");
        msg.put("ordersId", msg.get("ordersId"));
        lan.preData(pmp[2], copyExchange);
        CallEngine.wsCall(copyExchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.trades.smcom.preSub.template", copyExchange);

        Map retMap = copyExchange.getOut().getBody(Map.class);
        List<Map> rspInfoList = (ArrayList<Map>) retMap.get("rspInfo");
        if (IsEmptyUtils.isEmpty(rspInfoList)) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "核心系统未返回订单信息.");
        }
        return retMap;
    }

    /**
     * 拼装product和productType节点
     *
     * @param productList
     * @param productTypeList
     * @param paraMap
     */
    private void preProductItem(List<Map> product, List<Map> productType, Map<String, String> paraMap, Map msg) {
        // 拼装产品节点
        Map productItem = new HashMap();
        productItem.put("userId", msg.get("userId"));
        productItem.put("productMode", paraMap.get("productMode"));
        productItem.put("productId", paraMap.get("productId"));
        productItem.put("productTypeCode", paraMap.get("productTypeCode"));
        productItem.put("brandCode", paraMap.get("brandCode"));
        productItem.put("itemId", getItemId());
        productItem.put("modifyTag", paraMap.get("modifyTag"));
        productItem.put("startDate", paraMap.get("productStartDate"));
        productItem.put("endDate", paraMap.get("productEndDate"));
        productItem.put("userIdA", "-1");
        productItem.put("xDatatype", "NULL");
        product.add(productItem);
        System.out.println("zsqtest10" + product);

        Map productTypeItem = new HashMap();
        productTypeItem.put("userId", msg.get("userId"));
        productTypeItem.put("productMode", paraMap.get("productMode"));
        productTypeItem.put("productId", paraMap.get("productId"));
        productTypeItem.put("productTypeCode", paraMap.get("productTypeCode"));
        productTypeItem.put("modifyTag", paraMap.get("modifyTag"));
        productTypeItem.put("startDate", paraMap.get("productStartDate"));
        productTypeItem.put("endDate", paraMap.get("productEndDate"));
        productTypeItem.put("xDatatype", "NULL");
        productType.add(productTypeItem);
        System.out.println("zsqtest11" + productType);

    }

    /**
     * 根据用户的原有速率选择新产品的速率
     *
     * @param productInfoList
     * @param svcInfoList
     * @return
     */
    private List<Map> chooseSpeedByUser(List<Map> productInfoResult, List<Map> svcInfoList) {
        for (Map svcInfo : svcInfoList) {
            String speedId = (String) svcInfo.get("serviceId");
            if ("50103,50105,50107".contains(speedId)) {
                // HSPA+(42M上网)-50105，(LTE)100M-50103,(4G+)300M-50107
                String speed = "50105".equals(speedId) ? "42" : "50103".equals(speedId) ? "100" : "300";
                return chooseSpeed(productInfoResult, speed);
            }
        }
        // 默认选择300M的速率
        return chooseSpeed(productInfoResult, "300");
    }

    /**
     * 选择速率
     *
     * @param productInfoResult
     * @param speed
     * @return
     */
    private List<Map> chooseSpeed(List<Map> productInfoResult, String speed) {
        // 如果当前速率为空，则直接下发productInfoResult里的速率
        if (IsEmptyUtils.isEmpty(speed)) {
            return productInfoResult;
        }
        // HSPA+(42M上网)-50105，(LTE)100M-50103,(4G+)300M-50107
        String speedId = "42".equals(speed) ? "50105" : "100".equals(speed) ? "50103" : "50107";
        // 取当前产品的默认速率

        for (int i = 0; i < productInfoResult.size(); i++) {
            String elementId = String.valueOf(productInfoResult.get(i).get("ELEMENT_ID"));
            if ("50103,50105,50107".contains(elementId)) {
                if (elementId.equals(speedId)) {
                    break;
                }
                Map speedParam = new HashMap();
                speedParam.put("PRODUCT_ID", productInfoResult.get(i).get("PRODUCT_ID"));
                speedParam.put("ELEMENT_ID", speedId);// 此处为外围传入的速率
                Map speedMap = new HashMap();
                try {
                    speedMap = new DealNewCbssProduct().qryNewProductSpeed(speedParam);
                }
                catch (Exception e) {
                    return productInfoResult;
                }
                productInfoResult.remove(productInfoResult.get(i));
                productInfoResult.add(speedMap);
            }
        }
        return productInfoResult;
    }

    /**
     * 需要预处理的国际服务和需要继承的资费
     *
     * @param productInfoResult
     * @param userInfo
     * @return
     */
    private List<Map> preDealProductInfo(List<Map> productInfoResult, String productId, Map userInfo, Map msg) {
        // 原代码中要处理的网龄升级计划时间,改到拼装discnt节点中处理
        List<Map> svcInfo = (List<Map>) userInfo.get("svcInfo");
        List<Map> discntInfo = (List<Map>) userInfo.get("discntInfo");

        // 处理国际业务
        if (!IsEmptyUtils.isEmpty(svcInfo)) {
            List<Map> removeList = new ArrayList<Map>();
            for (Map svc : svcInfo) {
                String svcId = (String) svc.get("serviceId");
                if ("50015,50011".contains(svcId)) {// 国际长途,国际漫游
                    Map inMap = new HashMap();
                    inMap.put("PRODUCT_ID", productId);
                    inMap.put("ELEMENT_ID", svcId);
                    inMap.put("PROVINCE_CODE", msg.get("provinceCode"));
                    inMap.put("EPARCHY_CODE", msg.get("eparchyCode"));
                    List<Map> addSvcList = n25Dao.qryPackageElementByElement(inMap);

                    if (!IsEmptyUtils.isEmpty(addSvcList)) {
                        // 将原有国际业务继承,剔除与之互斥的国内业务
                        productInfoResult.add(addSvcList.get(0));
                        String removeSvcId = "50015".equals(svcId) ? "50014" : "50010";
                        for (Map productInfo : productInfoResult) {
                            if (removeSvcId.equals(productInfo.get("ELEMENT_ID") + "")) {
                                removeList.add(productInfo);
                            }
                        }
                    }
                }
            }
            productInfoResult.removeAll(removeList);
        }
        // 把需要继承的资费写在配置里,若有需要,加入库中
        String keepDiscnts = EcAopConfigLoader.getStr("ecaop.global.param.change.product.keepDiscnt");
        if (!IsEmptyUtils.isEmpty(keepDiscnts) && !IsEmptyUtils.isEmpty(discntInfo)) {
            for (Map disMap : discntInfo) {
                String disEndDate = (String) disMap.get("endDate");
                if (keepDiscnts.contains((String) disMap.get("discntCode"))
                        && GetDateUtils.getMonthLastDayFormat().compareTo(disEndDate) < 0) {
                    Map inMap = new HashMap();
                    inMap.put("PRODUCT_ID", productId);
                    inMap.put("ELEMENT_ID", disMap.get("discntCode"));
                    inMap.put("PROVINCE_CODE", msg.get("provinceCode"));
                    inMap.put("EPARCHY_CODE", msg.get("eparchyCode"));
                    List<Map> keepDis = n25Dao.qryPackageElementByElement(inMap);
                    if (!IsEmptyUtils.isEmpty(keepDis)) {
                        // 继承的资费，需要保持原有时间
                        keepDis.get(0).put("KEEP_END_DATE", disEndDate);
                        productInfoResult.add(keepDis.get(0));
                    }
                }
            }
        }
        return productInfoResult;
    }

    private Map preDataForTradeItem(Map msg, Boolean isChgProduct, Boolean isMixNum) throws Exception {
        List<Map> Item = new ArrayList<Map>();
        Map tradeItem = new HashMap();
        Item.add(LanUtils.createTradeItem("COMP_DEAL_STATE", "1"));
        Item.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", "1"));
        Item.add(LanUtils.createTradeItem("OPER_CODE", "2"));
        Item.add(LanUtils.createTradeItem("NO_BOOK_REASON", ""));
        Item.add(LanUtils.createTradeItem("BOOK_FLAG", "0"));
        Item.add(LanUtils.createTradeItem("SUB_TYPE", "0"));
        Item.add(LanUtils.createTradeItem("IS_CHANGE_NET", "1"));
        Item.add(LanUtils.createTradeItem("PRE_START_TIME", ""));
        Item.add(LanUtils.createTradeItem("MAIN_USER_TAG", ""));
        Item.add(LanUtils.createTradeItem("PRE_START_HRS", ""));
        if (isMixNum) {
            Item.add(LanUtils.createTradeItem("IS_SAME_CUST", msg.get("PhoneCustId")));
        }
        else {
            Item.add(LanUtils.createTradeItem("IS_SAME_CUST", msg.get("custId")));
        }
        if (isChgProduct) {
            Item.add(LanUtils.createTradeItem("ALONE_TCS_COMP_INDEX", "2"));
            Item.add(LanUtils.createTradeItem("NEW_PRODUCT_ID", msg.get("newProductId")));
            Item.add(LanUtils.createTradeItem("OLD_NET_TYPE_CODE", "50"));
            Item.add(LanUtils.createTradeItem("PRODUCT_TYPE_CODE", "4G000001"));
            Item.add(LanUtils.createTradeItem("NEW_BRAND_CODE", "50"));
        }
        else {
            Item.add(LanUtils.createTradeItem("ALONE_TCS_COMP_INDEX", "1"));
        }
        tradeItem.put("item", Item);
        return tradeItem;
    }

    private Map preDataForTradeSubItem(Map msg, Boolean isMixNum) {
        List<Map> Item = new ArrayList<Map>();
        Map tradeSubItem = new HashMap();
        String itemId = "";
        if (isMixNum) {
            itemId = (String) msg.get("mixUserId");
        }
        else {
            itemId = (String) msg.get("PhoneUserId");
        }
        Item.add(lan.createTradeSubItemE("LINK_NAME", msg.get("contactPerson"), itemId));
        Item.add(lan.createTradeSubItemE("LINK_PHONE", msg.get("contactPhone"), itemId));
        tradeSubItem.put("item", Item);
        return tradeSubItem;
    }

    private Map preDataForTradeRelation(Map msg) {
        Map tradeRelation = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("xDatatype", "null");
        item.put("relationTypeCode", "8900");
        item.put("idA", msg.get("mixUserId"));
        item.put("idB", msg.get("PhoneUserId"));
        item.put("roleCodeA", "0");
        item.put("roleCodeB", "1");
        item.put("startDate", msg.get("startDate"));
        item.put("endDate", "20501231122359");
        item.put("modifyTag", "0");
        item.put("serialNumberA", msg.get("mixNumber"));
        item.put("serialNumberB", msg.get("mixSerialNumber"));
        item.put("itemId", "-1");
        itemList.add(item);
        tradeRelation.put("item", itemList);
        return tradeRelation;
    }

    private Map preDataForTradePayrelation(Map msg) {
        Map tradePayrelation = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("payitemCode", "-1");
        item.put("acctPriority", "0");
        item.put("userPriority", "0");
        item.put("bindType", "1");
        item.put("defaultTag", "1");
        item.put("limitType", "0");
        item.put("limit", "0");
        item.put("complementTag", "0");
        item.put("startAcycId", RDate.currentTimeStr("yyyyMM"));
        item.put("acceptMonth", RDate.currentTimeStr("MM"));
        item.put("addupMonths", "0");
        item.put("addupMethod", "0");
        item.put("acctId", msg.get("PhoneAcctId"));
        item.put("payrelationId", msg.get("payRelationId"));
        item.put("actTag", "1");
        itemList.add(item);
        tradePayrelation.put("item", itemList);
        return tradePayrelation;
    }

    private Map preBaseData(Map msg, String appCode, Boolean isMixNum) {
        String date = (String) msg.get("startDate");
        Map base = new HashMap();
        base.put("startDate", date);
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
        base.put("advancePay", "0");
        base.put("inModeCode", new ChangeCodeUtils().getInModeCode(appCode));
        base.put("tradeStaffId", msg.get("operatorId"));
        base.put("checktypeCode", "0");
        base.put("termIp", "10.124.0.48");
        base.put("eparchyCode", msg.get("eparchyCode"));
        base.put("cityCode", msg.get("district"));
        base.put("remark", new PreDataProcessor().getRemark(msg));
        base.put("subscribeId", msg.get("ordersId"));
        base.put("tradeDepartId", msg.get("departId"));
        if (isMixNum) {
            base.put("tradeId", msg.get("ordersId"));
            base.put("userDiffCode", "8900");
            base.put("brandCode", "COMP");
            base.put("tradeTypeCode", "0110");
            base.put("netTypeCode", "00CP");
            base.put("userId", msg.get("mixUserId"));
            base.put("productId", msg.get("mainProductId"));
            base.put("custId", msg.get("custId"));
            base.put("usecustId", msg.get("custId"));
            base.put("custName", msg.get("custName"));
            base.put("acctId", msg.get("acctId"));
            base.put("serinalNamber", msg.get("mixNumber"));
            base.put("nextDealTag", "P");
        }
        else {
            base.put("tradeId", msg.get("tradeId"));
            base.put("userDiffCode", "00");
            base.put("brandCode", "4G00");
            base.put("tradeTypeCode", "0340");
            base.put("netTypeCode", "50");
            base.put("userId", msg.get("PhoneUserId"));
            base.put("custId", msg.get("PhoneCustId"));
            base.put("custName", msg.get("PhoneCustName"));
            base.put("acctId", msg.get("PhoneAcctId"));
            base.put("usecustId", msg.get("PhoneCustId"));
            base.put("productId", msg.get("PhoneProductId"));
            base.put("serinalNamber", msg.get("mixSerialNumber"));
            base.put("nextDealTag", "Y");
        }
        return base;
    }

    private void MixNumberthreepart(Exchange exchange, Map msg) {
        msg.put("serialNumber", msg.get("serialNumber"));
        msg.put("tradeTypeCode", "9999");
        msg.put("getMode", "1111111111100013010000000100001");
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, msg);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[1], threePartExchange);
        try {
            CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
            lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "调用cbss三户接口出错:" + e.getMessage());
        }

        Map checkUserMap = threePartExchange.getOut().getBody(Map.class);
        List<Map> custInfo = (List<Map>) checkUserMap.get("custInfo");
        if (IsEmptyUtils.isEmpty(custInfo)) {
            throw new EcAopServerBizException("9999", "客户信息未返回");
        }
        List<Map> userInfo = (List<Map>) checkUserMap.get("userInfo");
        if (IsEmptyUtils.isEmpty(userInfo)) {
            throw new EcAopServerBizException("9999", "用户信息未返回");
        }
        List<Map> acctInfo = (List<Map>) checkUserMap.get("acctInfo");
        if (IsEmptyUtils.isEmpty(acctInfo)) {
            throw new EcAopServerBizException("9999", "账户信息为空");
        }
        List<Map> uuInfo = (List<Map>) userInfo.get(0).get("uuInfo");
        if (IsEmptyUtils.isEmpty(uuInfo)) {
            throw new EcAopServerBizException("9999", "三户接口未返回uuInfo信息");
        }
        for (Map temMap : uuInfo) {
            String relationTypeCode = (String) temMap.get("relationTypeCode");
            String endDate = (String) temMap.get("endDate");
            if ((relationTypeCode.startsWith("89") || relationTypeCode.startsWith("88"))
                    && 0 < endDate.compareTo(GetDateUtils.getNextMonthFirstDayFormat())) {
                msg.put("mixUserId", temMap.get("userIdA"));
                msg.put("mixNumber", temMap.get("serialNumberA"));
            }
        }
        String eparchyCode = ChangeCodeUtils.changeCityCode(msg);
        String itemId = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, msg),
                "seq_item_id", 1).get(0);

        msg.put("userId", userInfo.get(0).get("userId"));
        msg.put("startDate", GetDateUtils.getDate());
        msg.put("itemId", itemId);
        msg.put("mainProductId", userInfo.get(0).get("productId"));
        msg.put("eparchyCode", eparchyCode);
        msg.put("acctId", acctInfo.get(0).get("acctId"));
        msg.put("custId", custInfo.get(0).get("custId"));
        msg.put("custName", custInfo.get(0).get("custName"));// custName
    }

    private void PhoneNumberthreepart(Exchange exchange, Map msg) {
        msg.put("serialNumber", msg.get("mixSerialNumber"));
        msg.put("tradeTypeCode", "9999");
        msg.put("getMode", "1111111111100013010000000100001");
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, msg);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[1], threePartExchange);
        try {
            CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
            lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "调用cbss三户接口出错:" + e.getMessage());
        }

        Map checkUserMap = threePartExchange.getOut().getBody(Map.class);
        List<Map> custInfo = (List<Map>) checkUserMap.get("custInfo");
        if (IsEmptyUtils.isEmpty(custInfo)) {
            throw new EcAopServerBizException("9999", "客户信息未返回");
        }
        List<Map> userInfo = (List<Map>) checkUserMap.get("userInfo");
        if (IsEmptyUtils.isEmpty(userInfo)) {
            throw new EcAopServerBizException("9999", "用户信息未返回");
        }
        List<Map> acctInfo = (List<Map>) checkUserMap.get("acctInfo");
        if (IsEmptyUtils.isEmpty(acctInfo)) {
            throw new EcAopServerBizException("9999", "账户信息为空");
        }
        // 校验是否没返回老产品信息
        List<Map> oldProductInfo = (List<Map>) userInfo.get(0).get("productInfo");
        if (IsEmptyUtils.isEmpty(oldProductInfo)) {
            throw new EcAopServerBizException("9999", "调三户未返回产品信息");
        }
        msg.put("oldProductInfo", oldProductInfo);
        String eparchyCode = ChangeCodeUtils.changeCityCode(msg);
        String payRelationId = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, msg),
                "SEQ_PAYRELA_ID", 1).get(0);

        msg.put("PhoneUserId", userInfo.get(0).get("userId"));
        System.out.println("zsqtest11111111111111" + msg);
        msg.put("startDate", GetDateUtils.getDate());
        msg.put("PhoneProductId", userInfo.get(0).get("productId"));
        msg.put("payRelationId", payRelationId);
        msg.put("eparchyCode", eparchyCode);
        msg.put("PhoneAcctId", acctInfo.get(0).get("acctId"));
        msg.put("PhoneCustId", custInfo.get(0).get("custId"));
        msg.put("PhoneCustName", custInfo.get(0).get("custName"));// custName
    }

    /**
     * 生成itemId
     *
     * @return
     */
    private String getItemId() {
        Map body = copyExchange.getIn().getBody(Map.class);
        return (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(copyExchange, body.get("msg")),
                "seq_item_id", 1).get(0);
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
