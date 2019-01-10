package com.ailk.ecaop.biz.sub.gdjk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.product.DealNewCbssProduct;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.NewProductUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.google.common.collect.Maps;

/**
 * 融合移网成员纳入组合版产品处理逻辑
 * @author wangmc
 *         2017-04-06
 */
public class MixChangeProduct4GProcessor {

    // 00：订购；01：退订
    private static String BOOK = "00";
    private static String UNBOOK = "01";
    // 0：可选产品；1：主产品
    private static String MAIN = "00";
    private static String SUB = "01";

    private static String END_OF_WORLD = "2050-12-31 23:59:59";
    // private Exchange copyExchange = new DefaultExchange(); // 用于生成itemId
    private ParametersMappingProcessor[] pmp = null;

    /**
     * @param exchange
     * @param pmp
     * @return
     * @throws Exception
     */
    public Map newDealProduct(Exchange exchange, ParametersMappingProcessor[] pmp) throws Exception {
        this.pmp = pmp;
        // copyExchange = exchange;

        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");

        Map msgContainer = Maps.newHashMap();
        msgContainer.putAll(msg);

        Map mixTemplate = (Map) msg.get("mixTemplate");
        Map phoneTemplate = (Map) msg.get("phoneTemplate");
        String combAdvance = null;
        String addType = (String) mixTemplate.get("addType");
        String mainNumberTag = (String) phoneTemplate.get("mainNumberTag");
        msg.put("addType", mixTemplate.get("addType"));
        if ("3".equals(addType) && "0".equals(mainNumberTag)) {
            combAdvance = "1";
        }
        // LanOpenApp4GDao dao = new LanOpenApp4GDao();

        String provinceCode = "00" + msg.get("province");
        String serialNumber = (String) phoneTemplate.get("serialNumber");
        String tradeStaffId = (String) msg.get("operatorId");
        // 三户查询
        Map userRsp = getInfoByNum4G(exchange, serialNumber, pmp);

        List<Map> custInfoList = (List<Map>) userRsp.get("custInfo");
        List<Map> userInfoList = (List<Map>) userRsp.get("userInfo");
        List<Map> acctInfoList = (List<Map>) userRsp.get("acctInfo");
        // 需要通过userInfo的返回,拼装老产品信息
        Map custInfo = custInfoList.get(0);
        Map userInfo = userInfoList.get(0);
        Map acctInfo = acctInfoList.get(0);

        // 优先取到地市编码
        String eparchyCode = (String) userInfo.get("eparchyCode");
        msg.put("eparchyCode", eparchyCode);

        Object oldProduct = userInfo.get("productId");
        List<Map> oldProducts = (List<Map>) userInfo.get("productInfo");
        List<Map> packageElement = new ArrayList<Map>();
        List<Map> discntInfo = (List<Map>) userInfo.get("discntInfo");
        for (Map discnt : discntInfo) {
            if (oldProduct.equals(discnt.get("productId"))) {
                Map element = new HashMap();
                element.put("packageId", discnt.get("packageId"));
                element.put("elementId", discnt.get("discntCode"));
                element.put("elementType", "D");
                element.put("modType", "1");
                packageElement.add(element);
            }
        }
        String mainCardTag = (String) phoneTemplate.get("mainCardTag");
        if (StringUtils.isEmpty(mainCardTag)) {
            mainCardTag = "1";// 非加入主卡 0加入主卡
            msg.put("mainCardTag", mainCardTag);
        }
        List<Map> uuInfo = (ArrayList<Map>) userInfo.get("uuInfo");
        if (!IsEmptyUtils.isEmpty(uuInfo)) {
            for (Map temp : uuInfo) {
                if ("ZF".equals(temp.get("relationTypeCode"))) {
                    if ("0".equals(mainCardTag) && "3".equals(addType)) {
                        throw new EcAopServerBizException("9999", "号码" + serialNumber + "已经存在主副卡关系，不允许重复办理");
                    }
                    if (temp.get("serialNumberA").equals(temp.get("serialNumberB"))) {
                        msg.put("combAdvance", "1");
                    }
                }
            }
        }
        if ("0".equals(mainCardTag) && "3".equals(addType)) {
            combAdvance = "1";
        }
        // 收入集团归集处理
        if ("1".equals(phoneTemplate.get("groupFlag"))) {// FIXME 收入集团归集
            Map jtcpMap = new HashMap();
            jtcpMap.put("province", "00" + msg.get("province"));
            jtcpMap.put("groupId", phoneTemplate.get("groupId"));
            jtcpMap.put("operatorId", msg.get("operatorId"));
            jtcpMap.put("city", msg.get("eparchyCode"));
            jtcpMap.put("district", msg.get("district"));
            jtcpMap.put("channelId", msg.get("channelId"));
            jtcpMap.put("channelType", msg.get("channelType"));
            dealJtcp(exchange, jtcpMap, msg);
        }
        Map oldProdMap = new HashMap();
        oldProdMap.put("productId", oldProduct);
        oldProdMap.put("productMode", "1");
        oldProdMap.put("optType", "01");
        oldProdMap.put("packageElement", packageElement);
        List<Map> oldProductInfo = new ArrayList<Map>();
        oldProductInfo.add(oldProdMap);
        // 账户资料校验 begin
        Map baseObject = new HashMap();
        // BASE数据组织开始
        exchange.getIn().setBody(body);
        String payRelId = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, msg),
                "SEQ_PAYRELA_ID", 1).get(0);
        msg.put("payRelId", payRelId);

        String departId = (String) msg.get("channelId");
        String acceptDate = GetDateUtils.transDate(GetDateUtils.getDate(), 19);
        // String acceptDate = GetDateUtils.getDate();
        String nextMon1stDay = GetDateUtils.getNextMonthDate();
        String userId = (String) userInfo.get("userId");
        String custId = (String) custInfo.get("custId");
        String acctId = (String) msg.get("acctId");
        String useCustId = (String) userInfo.get("usecustId");
        String cycleId = TradeManagerUtils.getCycleId(GetDateUtils.transDate(acceptDate, 19));

        String brandCode = (String) userInfo.get("brandCode");
        String userDiffCode = (String) userInfo.get("userDiffCode");
        String custName = (String) custInfo.get("custName");
        // 联系人
        String linkName = (null == custInfo.get("contact") || "".equals(custInfo.get("contact")) ? custName
                : (String) custInfo.get("contact"));
        String linkAddress = (String) custInfo.get("certAddr");
        String linkPhone = (String) custInfo.get("contactPhone");
        String cityCode = (String) userInfo.get("cityCode");
        String newProductId = (String) userInfo.get("productId");

        List<Map> productInfoList = (List<Map>) ((Map) msg.get("phoneTemplate")).get("phoneProduct");

        if (CollectionUtils.isEmpty(productInfoList)) {
            throw new EcAopServerBizException("9999", "产品信息不能为空");
        }
        Object newProduct = "";
        for (Map product : productInfoList) {
            product.put("optType", "00");// 订购新产品
            if ("1".equals(product.get("productMode"))) {
                newProduct = product.get("productId");
            }
        }

        List<Map> productList = new ArrayList<Map>();
        List<Map> productTypeList = new ArrayList<Map>();
        List<Map> tradeOtherList = new ArrayList<Map>();
        Map tradeUser = Maps.newHashMap();
        List<Map> discntList = new ArrayList<Map>();
        List<Map> svcList = new ArrayList<Map>();
        List<Map> spList = new ArrayList<Map>();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> subItemList = new ArrayList<Map>();
        Map tradePayrelation = Maps.newHashMap();
        productInfoList.addAll(oldProductInfo);
        // 是否变更产品 true-变更,false-不变更
        boolean isChgProduct = !oldProduct.equals(newProduct);

        DealNewCbssProduct n25Dao = new DealNewCbssProduct();

        for (int i = 0; i < productInfoList.size(); i++) {
            Map productInfo = productInfoList.get(i);
            String optType = (String) productInfo.get("optType");// 00-订购,01-退订,02-变更
            // String oldProductId = (String) productInfo.get("productId");
            String productId = (String) productInfo.get("productId");
            String firstMonBillMode = "02";// 订购时默认首月全量全价
            String productMode = (String) productInfo.get("productMode");
            List<Map> packageElementList = (List<Map>) productInfo.get("packageElement");

            if (!"1,2".contains(productMode)) {
                throw new EcAopServerBizException("9999", "发起方输入的产品 [" + productId + "] 的产品编码有误,产品编码:[" + productMode
                        + "]");
            }
            productMode = "1".equals(productMode) ? "00" : "01";// 1-主产品,0-附加产品

            if (UNBOOK.equals(optType) && isChgProduct) {// 退订

                // 退订的主产品查询融合的东西 combAdvance
                // 查询产品的属性信息
                Map inMap = new HashMap();
                inMap.put("PRODUCT_ID", productId);
                inMap.put("PRODUCT_MODE", productMode);
                inMap.put("PROVINCE_CODE", provinceCode);
                inMap.put("EPARCHY_CODE", eparchyCode);
                Map productInfoResult = n25Dao.queryProductInfoByProductId(inMap);

                String unBookProductId = String.valueOf(productInfoResult.get("PRODUCT_ID")); // 数据库值为数字类型
                String unBookProductMode = (String) productInfoResult.get("PRODUCT_MODE");
                String unBookBrandCode = (String) productInfoResult.get("BRAND_CODE");
                String unBookProductTypeCode = (String) productInfoResult.get("PRODUCT_TYPE_CODE");
                brandCode = unBookBrandCode;

                // 退订的产品生效时间取原生效时间,失效时间为本月底
                String productStartDate = "";
                String productEndDate = GetDateUtils.getMonthLastDay();
                for (int m = 0; m < oldProducts.size(); m++) {
                    Map pInfo = oldProducts.get(m);
                    if (unBookProductId.equals(pInfo.get("productId"))) {
                        productStartDate = (String) pInfo.get("productActiveTime");
                        productStartDate = GetDateUtils.transDate(productStartDate, 19);
                        break;
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
                paraMap.put("userId", userId);
                paraMap.put("eparchyCode", eparchyCode);
                // 拼装退订的产品节点
                preProductItem(productList, productTypeList, paraMap);

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
                tradeOther.put("rsrvStr10", serialNumber);// 号码
                tradeOther.put("rsrvValueCode", "NEXP"); //
                tradeOther.put("rsrvValue", userId); // USER_ID
                tradeOther.put("startDate", productStartDate);
                tradeOther.put("endDate", productEndDate);
                tradeOtherList.add(tradeOther);

            }
            if (BOOK.equals(optType)) // 新增产品
            {
                String isFinalCode = "";
                String productStartDate = GetDateUtils.getNextMonthFirstDayFormat();
                String productEndDate = "2050-12-31 23:59:59";
                if (SUB.equals(productMode)) { // 附加产品处理生效失效时间
                    isFinalCode = "N";
                    String endDateType = NewProductUtils.getEndDateType(productId);
                    if (IsEmptyUtils.isEmpty(endDateType)) {
                        isFinalCode = "X";
                    }
                    else {
                        isFinalCode = endDateType;
                    }
                    if ("N,X".contains(isFinalCode)) {// 附加产品有效期按偏移计算
                        Map dateMap = TradeManagerUtils.getEffectTime(productId, productStartDate, productEndDate);
                        productStartDate = (String) dateMap.get("monthFirstDay");// 附加产品的生效时间
                        productEndDate = (String) dateMap.get("monthLasttDay");// 附加产品的失效时间
                    }
                }

                String bookProductId = "";
                String bookProductMode = "";
                String bookBrandCode = "";
                String bookProductTypeCode = "";
                List<Map> productInfoResult = null;
                Map temp = new HashMap();
                temp.put("PROVINCE_CODE", provinceCode);
                temp.put("PRODUCT_ID", productId);
                temp.put("EPARCHY_CODE", eparchyCode);
                temp.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                temp.put("PRODUCT_MODE", productMode);
                if ("1".equals(combAdvance)) {
                    temp.put("IS_MIX_PRODUCT", "0"); // FIXME 融合产品需要多下发的资费,配置在中间表里
                }
                // 查询产品的默认属性信息,主产品变更套餐或者附加产品时,需要查询
                if ((MAIN.equals(productMode) && isChgProduct) || SUB.equals(productMode)) {
                    productInfoResult = n25Dao.qryDefaultPackageElement(temp);
                }

                // 未查到产品下的默认元素
                if (IsEmptyUtils.isEmpty(productInfoResult)) {
                    // if (MAIN.equals(productMode)) { // 主产品下没有默认元素报错 FIXME
                    // throw new EcAopServerBizException("9999", "根据产品Id [" + productId + "] 未查询到产品信息");
                    // }
                    // 未查询到附加产品的默认资费或服务,不报错,去TD_B_PRODUCT表查询,产品不存在就抛错,存在继续执行 by wangmc 20170331
                    Map productInfos = n25Dao.queryProductInfoByProductId(temp);
                    if (!IsEmptyUtils.isEmpty(productInfos)) {
                        bookProductId = String.valueOf(productInfos.get("PRODUCT_ID"));
                        bookProductMode = (String) productInfos.get("PRODUCT_MODE");
                        bookBrandCode = (String) productInfos.get("BRAND_CODE");
                        bookProductTypeCode = (String) productInfos.get("PRODUCT_TYPE_CODE");
                    }
                    else {
                        throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + productId + "】的产品信息");
                    }
                }
                else {
                    bookProductId = String.valueOf(productInfoResult.get(0).get("PRODUCT_ID"));
                    bookProductMode = (String) productInfoResult.get(0).get("PRODUCT_MODE");
                    bookBrandCode = (String) productInfoResult.get(0).get("BRAND_CODE");
                    bookProductTypeCode = (String) productInfoResult.get(0).get("PRODUCT_TYPE_CODE");
                }
                brandCode = bookBrandCode;
                if (MAIN.equals(productMode)) { // 订购的是主产品需要修改用户使用的主套餐
                    newProductId = bookProductId;
                }
                List<Map> allProductInfo = new ArrayList<Map>();
                if (!IsEmptyUtils.isEmpty(productInfoResult)) {
                    // 选择速率
                    productInfoResult = chooseSpeedByUser(productInfoResult, (List<Map>) userInfo.get("svcInfo"));
                    Map paraMap = MapUtils.asMap("provinceCode", provinceCode, "eparchyCode", eparchyCode);
                    // 处理国际漫游服务和要继承的资费
                    productInfoResult = preDealProductInfo(productInfoResult, bookProductId, userInfo, paraMap);
                    // 拼装产品默认属性的节点,放到下面统一处理
                    allProductInfo.addAll(productInfoResult);
                }

                // 有附加包
                if (!IsEmptyUtils.isEmpty(packageElementList)) {
                    List<Map> packageElementInfo = new ArrayList<Map>();
                    for (Map elementMap : packageElementList) {
                        Map peparam = new HashMap();
                        peparam.put("PROVINCE_CODE", provinceCode);
                        peparam.put("PRODUCT_ID", productId);
                        peparam.put("EPARCHY_CODE", eparchyCode);
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
                        // preProductInfo(packageElementInfo, bookProductId, discntList, svcList, spList, "0",
                        // isFinalCode, productStartDate, productEndDate);
                    }
                }
                if (!IsEmptyUtils.isEmpty(allProductInfo)) {
                    Map paraMap = MapUtils.asMap("userId", userId, "provinceCode", provinceCode, "eparchyCode",
                            eparchyCode, "serialNumber", serialNumber);
                    preProductInfo(allProductInfo, discntList, svcList, spList, "0", isFinalCode, productStartDate,
                            productEndDate, paraMap);
                }

                // 主产品变更套餐时或者附加产品时,需要传产品节点
                if ((MAIN.equals(productMode) && isChgProduct) || SUB.equals(productMode)) {
                    Map paraMap = new HashMap();
                    paraMap.put("productMode", bookProductMode);
                    paraMap.put("productId", bookProductId);
                    paraMap.put("productTypeCode", bookProductTypeCode);
                    paraMap.put("brandCode", bookBrandCode);
                    paraMap.put("modifyTag", "0"); // 订购
                    paraMap.put("productStartDate", productStartDate);
                    paraMap.put("productEndDate", productEndDate);
                    paraMap.put("userId", userId);
                    // 拼装订购产品节点
                    preProductItem(productList, productTypeList, paraMap);

                    if (MAIN.equals(productMode)) {// 主产品需要tradeUser节点
                        Map userItem = new HashMap();
                        userItem.put("userId", userId);
                        userItem.put("productId", bookProductId);
                        userItem.put("brandCode", bookBrandCode);
                        userItem.put("netTypeCode", "0050");
                        userItem.put("xDatatype", "NULL");
                        if (StringUtils.isNotEmpty((String) msg.get("recomPersonId"))) {
                            userItem.put("developStaffId", "" + msg.get("recomPersonId"));
                            userItem.put("developDepartId", "" + msg.get("channelId"));
                        }
                        tradeUser.put("item", userItem);
                    }
                }
            }
        }
        msg.put("acceptDate", acceptDate);
        msg.put("nextMon1stDay", nextMon1stDay);
        msg.put("userId", userId);
        msg.put("custId", custId);
        msg.put("acctId", acctId);
        msg.put("useCustId", useCustId);
        msg.put("cycleId", cycleId);
        msg.put("brandCode", brandCode);
        msg.put("userDiffCode", userDiffCode);
        msg.put("custName", custName);
        msg.put("linkName", linkName);
        msg.put("linkPhone", linkPhone);
        msg.put("linkAddress", linkAddress);
        msg.put("eparchyCode", eparchyCode);
        msg.put("cityCode", cityCode);
        msg.put("newProductId", newProductId);
        msg.put("provinceCode", provinceCode);
        msg.put("tradeStaffId", tradeStaffId);
        msg.put("departId", departId);
        msg.put("serialNumber", serialNumber);

        msgContainer.putAll(msg);

        // appendMap相关
        Map<String, String> appendMap = Maps.newHashMap();
        appendMap.put("E_IN_MODE", (String) msg.get("eModeCode"));
        appendMap.put("ALONE_TCS_COMP_INDEX", String.valueOf(msg.get("aloneTcsCompIndex")));
        appendMap.put("COMP_DEAL_STATE", String.valueOf(msg.get("compDealState")));
        Map phoneRecomInfo = (Map) phoneTemplate.get("phoneRecomInfo");
        if (null != phoneRecomInfo && phoneRecomInfo.size() > 0) {
            appendMap.put("recomPerName", (String) phoneRecomInfo.get("recomPersonName"));
            appendMap.put("DEVELOP_STAFF_ID", (String) phoneRecomInfo.get("recomPersonId"));
            appendMap.put("DEVELOP_DEPART_ID", (String) phoneRecomInfo.get("recomDepartId"));
        }
        // 行销标示处理
        System.out.println("移网纳入行销标示" + msg.get("markingTag"));
        if ("1".equals(msg.get("markingTag"))) {
            appendMap.put("MARKING_APP", "1");
        }

        Map appParam = new HashMap();
        appParam.put("TRADE_TYPE_CODE", "10");// 融合业务，纳入
        appParam.put("NET_TYPE_CODE", "ZZ");// 默认ZZ
        appParam.put("BRAND_CODE", "ZZZZ");// 默认ZZZZ
        appParam.put("PRODUCT_ID", "-1");// 默认-1
        appParam.put("EPARCHY_CODE", "ZZZZ");// 默认ZZZZ
        appParam.put("PROVINCE_CODE", "ZZZZ");// 默认ZZZZ

        List<Map> appendMapResult = new LanOpenApp4GDao().queryAppendParam(appParam);
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
                    item.put("itemId", msgContainer.get("userId"));
                    item.put("attrValue", appendMap.get(key));
                    item.put("attrCode", attrcode);
                    subItemList.add(item);
                }
            }
        }

        // 去重
        discntList = NewProductUtils.newDealRepeat(discntList, new String[] { "productId", "packageId", "discntCode",
                "modifyTag" });
        svcList = NewProductUtils.newDealRepeat(svcList, new String[] { "productId", "packageId", "serviceId",
                "modifyTag" });
        spList = NewProductUtils.newDealRepeat(spList, new String[] { "productId", "packageId", "spServiceId",
                "modifyTag" });
        productTypeList = NewProductUtils.newDealRepeat(productTypeList, new String[] { "productId", "productMode",
                "productTypeCode", "modifyTag" });
        productList = NewProductUtils.newDealRepeat(productList, new String[] { "productId", "productMode",
                "brandCode", "modifyTag" });
        msg.put("subItemList", subItemList);
        msg.put("discntList", discntList);
        msg.put("svcList", svcList);
        msg.put("spList", spList);
        msg.put("itemList", itemList);
        msg.put("tradeUser", tradeUser);
        msg.put("productList", productList);
        msg.put("productTypeList", productTypeList);
        msg.put("tradeOtherList", tradeOtherList);
        // msg.put("tradePayrelation", tradePayrelation);
        Map ext = new HashMap();
        // ext.put("tradeUser", tradeUser);
        ext.put("tradeProductType", preDataUtil(productTypeList));
        ext.put("tradeProduct", preDataUtil(productList));
        ext.put("tradeDiscnt", preDataUtil(discntList));
        ext.put("tradeSvc", preDataUtil(svcList));
        ext.put("tradeSp", preDataUtil(spList));
        ext.put("tradeOther", preDataUtil(tradeOtherList));
        ext.put("tradePayrelation", preTradePayRelData(msg));
        ext.put("tradeItem", preTradeItem(msg));
        ext.put("tradeSubItem", preTradeSubItem(msg));
        if ("0".equals(msg.get("mainCardTag")) && "3".equals(msg.get("addType"))) {
            ext.put("tradeRelation", preDataForTradeRelation(msg));
        }
        // ext.put("tradeItem", pretradeItem(msg));// 准备tradeItem节点
        // 拼装Base
        msg.put("base", dealBase(msg, custInfo, acctInfo, userInfo, acceptDate, newProductId, brandCode, nextMon1stDay));
        msg.put("ext", ext);
        msg.put("operTypeCode", "0");
        // 调下游预提交的报文要用生成的订单号,与虚拟预提交和移网新装保持一致
        msg.put("ordersId", msg.get("subscribeId"));
        msgContainer.putAll(msg);

        return msgContainer;
    }

    /**
     * 拼装product和productType节点
     * @param productList
     * @param productTypeList
     * @param paraMap
     */
    private void preProductItem(List<Map> productList, List<Map> productTypeList, Map<String, String> paraMap) {
        // 拼装产品节点
        Map productItem = new HashMap();
        productItem.put("userId", paraMap.get("userId"));
        productItem.put("productMode", paraMap.get("productMode"));
        productItem.put("productId", paraMap.get("productId"));
        productItem.put("productTypeCode", paraMap.get("productTypeCode"));
        productItem.put("brandCode", paraMap.get("brandCode"));
        // productItem.put("itemId", getItemId());// TradeManagerUtils.getSequence(paraMap.get("eparchyCode"),
        // "SEQ_ITEM_ID"));
        productItem.put("itemId", TradeManagerUtils.getSequence(paraMap.get("eparchyCode"), "SEQ_ITEM_ID"));
        productItem.put("modifyTag", paraMap.get("modifyTag"));
        productItem.put("startDate", paraMap.get("productStartDate"));
        productItem.put("endDate", paraMap.get("productEndDate"));
        productItem.put("userIdA", "-1");
        productItem.put("xDatatype", "NULL");
        productList.add(productItem);

        Map productTypeItem = new HashMap();
        productTypeItem.put("userId", paraMap.get("userId"));
        productTypeItem.put("productMode", paraMap.get("productMode"));
        productTypeItem.put("productId", paraMap.get("productId"));
        productTypeItem.put("productTypeCode", paraMap.get("productTypeCode"));
        productTypeItem.put("modifyTag", paraMap.get("modifyTag"));
        productTypeItem.put("startDate", paraMap.get("productStartDate"));
        productTypeItem.put("endDate", paraMap.get("productEndDate"));
        productTypeItem.put("xDatatype", "NULL");
        productTypeList.add(productTypeItem);

    }

    /**
     * 根据用户的原有速率选择新产品的速率
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
                return new NewProductUtils().chooseSpeed(productInfoResult, speed);
            }
        }
        // 默认选择300M的速率
        return new NewProductUtils().chooseSpeed(productInfoResult, "300");
    }

    /**
     * 需要预处理的国际服务和需要继承的资费
     * @param productInfoResult
     * @param mProductId
     * @param provinceCode
     * @param eparchyCode
     * @param userInfo
     * @return
     */
    private List<Map> preDealProductInfo(List<Map> productInfoResult, String productId, Map userInfo, Map paraMap) {
        // 原代码中要处理的网龄升级计划时间,改到拼装discnt节点中处理
        List<Map> svcInfo = (List<Map>) userInfo.get("svcInfo");
        List<Map> discntInfo = (List<Map>) userInfo.get("discntInfo");
        String provinceCode = (String) paraMap.get("provinceCode");
        String eparchyCode = (String) paraMap.get("eparchyCode");

        // 处理国际业务
        if (!IsEmptyUtils.isEmpty(svcInfo)) {
            List<Map> removeList = new ArrayList<Map>();
            for (Map svc : svcInfo) {
                String svcId = (String) svc.get("serviceId");
                if ("50015,50011".contains(svcId)) {// 国际长途,国际漫游
                    Map inMap = new HashMap();
                    inMap.put("PRODUCT_ID", productId);
                    inMap.put("ELEMENT_ID", svcId);
                    inMap.put("PROVINCE_CODE", provinceCode);
                    inMap.put("EPARCHY_CODE", eparchyCode);
                    List<Map> addSvcList = new DealNewCbssProduct().qryPackageElementByElement(inMap);

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
                    inMap.put("PROVINCE_CODE", provinceCode);
                    inMap.put("EPARCHY_CODE", eparchyCode);
                    List<Map> keepDis = new DealNewCbssProduct().qryPackageElementByElement(inMap);
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

    /**
     * 根据查询到的元素属性,拼装节点信息 discntList,svcList,spList
     * @param productInfoResult
     * @param productId
     * @param discntList
     * @param svcList
     * @param spList
     * @param modifyTag 0-订购,1-退订
     * @param isFinalCode N,X-生效失效时间按配置计算 Y-用传进来的时间(主产品的时候无值)
     * @param startDate 产品的生效时间
     * @param endDate 产品的失效时间
     * @param paraMap-userId,provinceCode,eparchyCode,serialNumber
     */
    private void preProductInfo(List<Map> productInfoResult, List<Map> discntList, List<Map> svcList, List<Map> spList,
            String modifyTag, String isFinalCode, String startDate, String endDate, Map paraMap) {
        for (int j = 0; j < productInfoResult.size(); j++) {
            if ("D".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                String elementId = String.valueOf(productInfoResult.get(j).get("ELEMENT_ID"));
                Map dis = new HashMap();
                dis.put("id", paraMap.get("userId"));
                dis.put("idType", "1");
                dis.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                dis.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                dis.put("discntCode", elementId);
                dis.put("specTag", "1");
                dis.put("relationTypeCode", "");
                // dis.put("itemId", getItemId());// TradeManagerUtils.getSequence((String) paraMap.get("eparchyCode"),
                // // "SEQ_ITEM_ID"));
                dis.put("itemId", TradeManagerUtils.getSequence((String) paraMap.get("eparchyCode"), "SEQ_ITEM_ID"));
                dis.put("modifyTag", modifyTag);
                if (StringUtils.isNotEmpty(isFinalCode) && !"Y".equals(isFinalCode)) {
                    Map discntDateMap = TradeManagerUtils.getDiscntEffectTime(elementId, startDate, endDate);
                    dis.put("startDate", discntDateMap.get("monthFirstDay"));
                    dis.put("endDate", discntDateMap.get("monthLasttDay"));
                }
                else {
                    dis.put("startDate", startDate);
                    dis.put("endDate", endDate);
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
                svc.put("userId", paraMap.get("userId"));
                svc.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                svc.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                svc.put("serviceId", productInfoResult.get(j).get("ELEMENT_ID"));
                // svc.put("itemId", getItemId());// TradeManagerUtils.getSequence((String) paraMap.get("eparchyCode"),
                // // "SEQ_ITEM_ID"));
                svc.put("itemId", TradeManagerUtils.getSequence((String) paraMap.get("eparchyCode"), "SEQ_ITEM_ID"));
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
                spItemParam.put("PROVINCE_CODE", paraMap.get("provinceCode"));
                spItemParam.put("SPSERVICEID", productInfoResult.get(j).get("ELEMENT_ID"));
                List<Map> spItemInfoResult = new DealNewCbssProduct().querySPServiceAttr(spItemParam);
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
                sp.put("userId", paraMap.get("userId"));
                sp.put("serialNumber", paraMap.get("serialNumber"));
                sp.put("firstBuyTime", startDate);
                sp.put("paySerialNumber", paraMap.get("serialNumber"));
                sp.put("startDate", startDate);
                sp.put("enddate", END_OF_WORLD);
                sp.put("updateTime", startDate);
                sp.put("remark", "");
                sp.put("modifyTag", modifyTag);
                sp.put("payUserId", paraMap.get("userId"));
                // sp.put("itemId", getItemId());// TradeManagerUtils.getSequence((String) paraMap.get("eparchyCode"),
                // // "SEQ_ITEM_ID"));
                sp.put("itemId", TradeManagerUtils.getSequence((String) paraMap.get("eparchyCode"), "SEQ_ITEM_ID"));
                sp.put("userIdA", "-1");
                sp.put("xDatatype", "NULL");
                spList.add(sp);
            }
        }
    }

    /**
     * 拼装base节点
     * @param msg
     * @param custInfo
     * @param acctInfo
     * @param userInfo
     * @param acceptDate
     * @param newProductId
     * @param brandCode
     * @param realStartDate
     * @return
     */
    private Map dealBase(Map msg, Map custInfo, Map acctInfo, Map userInfo, String acceptDate, String newProductId,
            String brandCode, String realStartDate) {
        Map base = new HashMap();

        base.put("subscribeId", msg.get("subscribeId"));
        base.put("tradeId", msg.get("tradeId"));
        base.put("acceptDate", acceptDate);
        base.put("tradeDepartId", msg.get("departId"));
        base.put("cityCode", userInfo.get("cityCode"));
        base.put("areaCode", msg.get("eparchyCode"));
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        // 添加strInModeCode赋值X、E
        base.put("inModeCode", msg.get("eModeCode"));
        base.put("tradeStaffId", msg.get("operatorId"));
        base.put("tradeTypeCode", "0340"); // 融合纳入为0340
        base.put("productId", newProductId);
        base.put("brandCode", brandCode);
        base.put("userId", msg.get("userId"));
        base.put("custId", custInfo.get("custId"));
        base.put("usecustId", userInfo.get("usecustId"));
        base.put("acctId", acctInfo.get("acctId"));
        base.put("userDiffCode", userInfo.get("userDiffCode"));
        base.put("netTypeCode", "50");
        base.put("serinalNamber", msg.get("serialNumber"));
        base.put("custName", custInfo.get("custName"));
        base.put("termIp", "0.0.0.0");
        base.put("eparchyCode", msg.get("eparchyCode"));
        base.put("cityCode", userInfo.get("cityCode"));
        base.put("execTime", GetDateUtils.getDate());// 这个应该是当前时间
        base.put("operFee", "0"); // 营业费用
        base.put("foregift", "0"); // 押金
        base.put("advancePay", "0"); // 预存
        base.put("feeState", "0");
        base.put("feeStaffId", "");
        base.put("cancelTag", "0");
        base.put("checktypeCode", "8");
        base.put("chkTag", "0");
        base.put("actorName", "");
        base.put("actorCertTypeId", "");
        base.put("actorPhone", "");
        base.put("actorCertNum", "");
        base.put("contact", custInfo.get("custName"));
        base.put("contactAddress", custInfo.get("certAddr"));
        base.put("contactPhone", custInfo.get("contactPhone"));
        if (null != msg.get("remark")) {
            base.put("remark", msg.get("remark"));
        }
        else {
            base.put("remark", "");
        }

        return base;
    }

    /**
     * 根据号码获取CBSS处三户信息
     * @param exchange
     * @param serialNumber 号码
     * @return
     * @throws Exception
     */
    private Map getInfoByNum4G(Exchange exchange, String serialNumber, ParametersMappingProcessor[] pmp)
            throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        msg.put("serialNumber", serialNumber);
        msg.put("tradeTypeCode", "9999");
        msg.put("serviceClassCode", "0000");
        msg.put("getMode", "1111111111100013010000000100001");
        body.put("msg", msg);
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, msg);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[1], threePartExchange);
        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        Map result = threePartExchange.getOut().getBody(Map.class);
        return result;
    }

    private void dealJtcp(Exchange exchange, Map jtcpMap, Map msg) {
        Exchange jtcpExchange = ExchangeUtils.ofCopy(exchange, jtcpMap);
        LanUtils lan = new LanUtils();
        try {
            lan.preData("ecaop.trades.sell.mob.jtcp.ParametersMapping", jtcpExchange);
            CallEngine.wsCall(jtcpExchange, "ecaop.comm.conf.url.cbss.services.OrdForAopthSer");// TODO:地址
            lan.xml2Json("ecaop.trades.sell.mob.jtcp.template", jtcpExchange);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "集团信息查询失败" + e.getMessage());
        }
        Map retMap = jtcpExchange.getOut().getBody(Map.class);
        List<Map> userList = (List<Map>) retMap.get("useList");
        if (null == userList || userList.isEmpty()) {
            throw new EcAopServerBizException("9999", "未获取到CBSS集团客户下的用户信息");
        }
        String serialNumberA = (String) userList.get(0).get("serialNumber");
        String userIdA = (String) userList.get(0).get("userId");
        if (StringUtils.isEmpty(serialNumberA) || StringUtils.isEmpty(userIdA)) {
            throw new EcAopServerBizException("9999", "未获取到CBSS集团客户下的用户信息");
        }
        Map relaItem = new HashMap();
        List<Map> tradeRelation = new ArrayList<Map>();
        relaItem.put("xDatatype", "NULL");
        relaItem.put("relationAttr", "");
        relaItem.put("relationTypeCode", "2222");
        relaItem.put("idA", userIdA);
        relaItem.put("idB", msg.get("userId"));
        relaItem.put("roleCodeA", "0");
        relaItem.put("roleCodeB", "0");
        relaItem.put("orderno", "");
        relaItem.put("shortCode", "");
        relaItem.put("startDate", GetDateUtils.getDate());
        relaItem.put("endDate", "2050-12-31 23:59:59");
        relaItem.put("modifyTag", "0");
        relaItem.put("remark", "");
        relaItem.put("serialNumberA", serialNumberA);
        relaItem.put("serialNumberB", msg.get("serialNumber"));
        // relaItem.put("itemId", getItemId());// TradeManagerUtils.getSequence((String) msg.get("eparchyCode"),
        // // "SEQ_ITEM_ID"));
        relaItem.put("itemId", TradeManagerUtils.getSequence((String) msg.get("eparchyCode"), "SEQ_ITEM_ID"));
        tradeRelation.add(relaItem);
        msg.put("tradeRelation", tradeRelation);
    }

    /**
     * 将节点放入item中返回
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
     * 准备payRelation节点
     * @param msg
     * @return
     */
    private Map preTradePayRelData(Map msg) {
        Map payRelItem = new HashMap();
        payRelItem.put("acctId", msg.get("acctId"));
        payRelItem.put("userId", msg.get("userId"));
        payRelItem.put("payrelationId", msg.get("payRelId"));
        payRelItem.put("payitemCode", "-1");
        payRelItem.put("acctPriority", "0");
        payRelItem.put("userPriority", "0");
        payRelItem.put("bindType", "1");
        payRelItem.put("defaultTag", "1");
        payRelItem.put("startAcycId", msg.get("cycleId"));
        payRelItem.put("endAcycId", "205001");
        payRelItem.put("actTag", "1");
        payRelItem.put("limitType", "0");
        payRelItem.put("limit", "0");
        payRelItem.put("complementTag", "0");
        payRelItem.put("addupMethod", "0");
        payRelItem.put("addupMonths", "0");
        payRelItem.put("xDatatype", "NULL");
        Map tradePayrelation = Maps.newHashMap();
        tradePayrelation.put("item", payRelItem);
        return tradePayrelation;
    }

    private Map preTradeItem(Map msg) {
        List<Map> itemList = (List<Map>) msg.get("itemList");
        if ("0".equals(msg.get("mainCardTag")) && "3".equals(msg.get("addType"))) {
            itemList.add(MapUtils.asMap("xDatatype", "NULL", "attrCode", "MAIN_CARD_TAG", "attrValue", "0"));
        }
        Map tradeItem = new HashMap();
        tradeItem.put("item", itemList);
        return tradeItem;
    }

    private Map preTradeSubItem(Map msg) {
        List<Map> subItemList = (List<Map>) msg.get("subItemList");
        Map tradeSubItem = new HashMap();
        if ("0".equals(msg.get("mainCardTag")) && "3".equals(msg.get("addType"))) {
            subItemList.add(MapUtils.asMap("xDatatype", "NULL", "attrTypeCode", "0", "attrCode", "MAIN_CARD_TAG",
                    "attrValue", "0", "itemId", msg.get("userId"), "startDate", msg.get("nextMon1stDay"), "endDate",
                    MagicNumber.CBSS_DEFAULT_EXPIRE_TIME));
        }
        tradeSubItem.put("item", subItemList);
        return tradeSubItem;
    }

    // 拼装TRADE_RELATION
    private Map preDataForTradeRelation(Map msg) {
        try {
            Map tradeRelation = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            Map item = new HashMap();
            // 主卡开户流程准备参数
            item.put("idA", msg.get("userId"));
            item.put("serialNumberA", msg.get("serialNumber"));
            item.put("roleCodeB", "1");
            item.put("relationAttr", "0");
            item.put("xDatatype", "NULL");
            item.put("relationTypeCode", "ZF");
            item.put("serialNumberB", msg.get("serialNumber"));
            item.put("idB", msg.get("userId"));
            item.put("roleCodeA", "0");
            item.put("modifyTag", "0");
            item.put("startDate", GetDateUtils.getNextMonthDate());
            item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            itemList.add(item);

            Map item2 = new HashMap();
            // 主卡开户流程准备参数
            item2.put("idA", msg.get("XNUserId"));
            item2.put("serialNumberA", msg.get("virtualNumber"));
            item2.put("roleCodeB", "1");
            // item2.put("relationAttr", "0");
            item2.put("xDatatype", "NULL");
            item2.put("relationTypeCode", "8900");
            item2.put("serialNumberB", msg.get("serialNumber"));
            item2.put("idB", msg.get("userId"));
            item2.put("roleCodeA", "0");
            item2.put("modifyTag", "0");
            item2.put("startDate", GetDateUtils.getNextMonthDate());
            item2.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            itemList.add(item2);
            tradeRelation.put("item", itemList);
            return tradeRelation;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_RELATION节点报错" + e.getMessage());
        }
    }

}
