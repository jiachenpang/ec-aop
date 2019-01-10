package com.ailk.ecaop.biz.product;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.NewProductUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;

/**
 * 4G套餐变更新产品逻辑
 * @author wangmc 2017-03-30
 */
@EcRocTag("newChangeProduct4CBProcessor")
public class NewChangeProduct4CBProcessor extends BaseAopProcessor implements ParamsAppliable {

    private String ERROR_CODE = "9999";
    private String THE_END_DATE = "2050-12-31 23:59:59";

    private String BOOK = "00";// 订购
    private String UNBOOK = "01";// 退订
    private String CHANGE = "02"; // 变更

    private String MAIN = "00";// 主产品
    private String SUB = "01";// 附加产品

    private DealNewCbssProduct n25Dao = new DealNewCbssProduct();
    LanUtils lan = new LanUtils();
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[4];
    private static final String[] PARAM_ARRAY = { "ecaop.trade.cbss.checkUserParametersMapping",
            "ecaop.ecsb.oupc.ParametersMapping", "ecaop.masb.chph.gifa.ParametersMapping",
            "ecaop.trades.sccc.cancelPre.paramtersmapping" }; // 三户接口,员工校验,流水号,预提交

    // 31省IN_MODE_CODE赋值公用数组
    private static ArrayList<String> modecodes = new ArrayList<String>(Arrays.asList("nmpr", "bjsb", "tjpr", "sdpr",
            "hpsb", "sxpr", "ahpre", "shpre", "jspr", "zjpre", "fjpre", "hipre", "gdps", "ussb", "qhpr", "hupr",
            "hnpr", "jxpre", "hapr", "xzpre", "scpr", "cqps", "snpre", "gzpre", "ynpre", "gspr", "nxpr", "xjpr",
            "jlpr", "mppln", "hlpr"));

    @Override
    public void process(Exchange exchange) throws Exception {
        System.out.println("======新的套餐变更流程");
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        msg.put("magsMethod", exchange.getMethodCode());

        // 校验员工信息
        // checkOperatorMsg(exchange, msg);
        // 查询三户
        Map threePartMap = checkThreePartMsg(exchange, msg);

        // 处理产品节点
        Exchange exchangeCopy = ExchangeUtils.ofCopy(exchange, msg);
        msg = dealProductInfo(exchangeCopy, msg, threePartMap);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        // 套餐变更合约续约需要增加活动节点 by zhaok
        if ("mags".equals(exchange.getMethodCode())) {
            return;
        }
        // 调用预提交接口
        lan.preData(pmp[3], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.trades.mmvc.sUniTrade.template", exchange);

        // 处理返回
        dealReturn(exchange, msg);
    }

    /**
     * 校验员工信息
     * @param exchange
     * @param body
     * @param msg
     * @throws Exception
     */
    public void checkOperatorMsg(Exchange exchange, Map msg) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        msg.put("checkMode", "1");
        msg.put("appCode", exchange.getAppCode());
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        Exchange exchangeCopy = ExchangeUtils.ofCopy(exchange, msg);
        lan.preData(pmp[1], exchangeCopy);// ecaop.ecsb.oupc.ParametersMapping
        CallEngine.wsCall(exchangeCopy, "ecaop.comm.conf.url.cbss.services.StaffQrySer");
        lan.xml2JsonNoError("ecaop.ecsb.oupc.template", exchangeCopy);
        Map retStaffinfo = exchangeCopy.getOut().getBody(Map.class);
        if (null == retStaffinfo || retStaffinfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "调用cb员工校验接口:未返回departId.");
        }
        msg.put("departId", retStaffinfo.get("departId"));
    }

    /**
     * 查询三户
     * @param exchange
     * @param msg
     * @return
     * @throws Exception
     */
    public Map checkThreePartMsg(Exchange exchange, Map msg) throws Exception {
        // 按照原北六逻辑,只传号码,tradeTypeCode传0120就是按cb默认的配置获取三户信息,与原流程保持一致(cb前台也是这个逻辑)
        Map threePartMap = MapUtils.asMap("serialNumber", msg.get("serialNumber"), "tradeTypeCode", "0120");
        String[] copyArray = { "operatorId", "province", "channelId", "city", "district", "channelType" };
        MapUtils.arrayPut(threePartMap, msg, copyArray);

        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
        lan.preData(pmp[0], threePartExchange);// ecaop.trade.cbss.checkUserParametersMapping
        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);

        Map out = threePartExchange.getOut().getBody(Map.class);

        // 校验是否没有返回三户信息
        String[] infoKeys = new String[] { "userInfo", "custInfo", "acctInfo" };
        Map errorDetail = MapUtils.asMap("userInfo", "用户信息", "custInfo", "客户信息", "acctInfo", "账户信息");
        Map temp = new HashMap();
        for (String infoKey : infoKeys) {
            if (IsEmptyUtils.isEmpty((List<Map>) out.get(infoKey))) {
                throw new EcAopServerBizException(ERROR_CODE, "调三户未返回" + errorDetail.get(infoKey));
            }
            temp.put(infoKey, ((List<Map>) out.get(infoKey)).get(0));
        }
        // 校验是否没返回老产品信息
        if (IsEmptyUtils.isEmpty(((Map) temp.get("userInfo")).get("productInfo"))) {
            throw new EcAopServerBizException("9999", "调三户未返回产品信息");
        }
        temp.put("productInfo", ((Map) temp.get("userInfo")).get("productInfo"));
        return temp;
    }

    /**
     * 处理产品信息
     * @param msg
     * @param threePartMap 三户返回信息
     * @return
     * @throws Exception
     */
    private Map dealProductInfo(Exchange exchange, Map msg, Map threePartMap) throws Exception {

        // 获取三户返回的信息
        Map userInfo = (Map) threePartMap.get("userInfo");
        Map custInfo = (Map) threePartMap.get("custInfo");
        Map acctInfo = (Map) threePartMap.get("acctInfo");
        List<Map> oldProducts = (List<Map>) threePartMap.get("productInfo");

        // 处理外围传进来的产品信息
        List<Map> productInfoList = (List<Map>) msg.get("productInfo");
        if (IsEmptyUtils.isEmpty(productInfoList)) {
            throw new EcAopServerBizException(ERROR_CODE, "业务异常:发起方未下发产品信息,无法变更套餐");
        }

        Map body = exchange.getIn().getBody(Map.class);
        String tradeId = "";
        exchange.getIn().setBody(body);
        // 套餐变更+合约接口需要用传入的订单号，是为了返销和对账，自助保证这个订单号和CB的订单号不会重复
        if ("mags".equals(msg.get("magsMethod"))) {
            msg.put("ordersId", msg.get("ordersId"));
            tradeId = (String) msg.get("ordersId");
        }
        else {
            tradeId = (String) GetSeqUtil.getSeqFromCb(pmp[2], exchange, "seq_trade_id", 1).get(0);
            msg.put("ordersId", tradeId);
        }
        String acceptDate = GetDateUtils.transDate(GetDateUtils.getDate(), 19);
        String nextMon1stDay = GetDateUtils.getSpecifyDateTime(1, 3, acceptDate, 1);
        String userId = String.valueOf(userInfo.get("userId"));
        msg.put("userId", userId);

        String brandCode = String.valueOf(userInfo.get("brandCode"));
        // 地市从三户中取的
        String eparchyCode = String.valueOf(userInfo.get("eparchyCode"));
        String newProductId = String.valueOf(userInfo.get("productId"));

        String provinceCode = "00" + msg.get("province");
        msg.put("eparchyCode", eparchyCode);
        msg.put("provinceCode", provinceCode);
        String serialNumber = (String) msg.get("serialNumber");
        /**
         * TODO:2-处理退订的产品:生效时间取原产品的生效时间,失效时间为本月底,并添加tradeOrter节点,放退订的产品
         */
        Map ext = new HashMap();
        Map base = new HashMap();
        List<Map> productTypeList = new ArrayList<Map>();
        List<Map> productList = new ArrayList<Map>();
        List<Map> tradeSubItemList = new ArrayList<Map>(); // RSD2017032200028-关于实现医院小区流量订购的需求
        List<Map> discntList = new ArrayList<Map>();
        List<Map> svcList = new ArrayList<Map>();
        List<Map> spList = new ArrayList<Map>();

        List<Map> tradeOtherList = new ArrayList<Map>();
        List<Map> tradeUserList = new ArrayList<Map>();

        for (int i = 0; i < productInfoList.size(); i++) {
            Map productInfo = productInfoList.get(i);
            String optType = (String) productInfo.get("optType");// 00-订购,01-退订,02-变更
            String productId = (String) productInfo.get("productId");
            if (!UNBOOK.equals(optType) && !BOOK.equals(optType) && !CHANGE.equals(optType)) {
                throw new EcAopServerBizException(ERROR_CODE, "产品[" + productId + "]的操作标识[" + optType
                        + "]不在取值范围[00,01,02]中,请确认");
            }
            String firstMonBillMode = "02";// 订购时默认首月全量全价
            String productMode = (String) productInfo.get("productMode");
            List<Map> packageElement = (List<Map>) productInfo.get("packageElement");

            if (!"0,1".contains(productMode)) {
                throw new EcAopServerBizException(ERROR_CODE, "发起方输入的产品 [" + productId + "] 的产品编码有误,产品编码:["
                        + productMode + "]");
            }
            productMode = "1".equals(productMode) ? "00" : "01";// 1-主产品,0-附加产品
            // 由于可以订购活动,所以需要先查询一下非主产品的真正的产品模式 by wangmc 20170728
            if (SUB.equals(productMode)) {
                Map tempMap = MapUtils.asMap("PRODUCT_ID", productId, "PROVINCE_CODE", provinceCode);
                productMode = (String) n25Dao.queryProductModeByCommodityId(tempMap);
            }
            if (UNBOOK.equals(optType)) {// 退订
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
                // brandCode = unBookBrandCode;
                // 退订的产品生效时间取原生效时间,失效时间为本月底
                String productStartDate = acceptDate;
                String productEndDate = GetDateUtils.getMonthLastDay();

                for (int m = 0; m < oldProducts.size(); m++) {
                    Map pInfo = oldProducts.get(m);
                    if (unBookProductId.equals(pInfo.get("productId"))) {
                        productStartDate = (String) pInfo.get("productActiveTime");
                        productStartDate = GetDateUtils.transDate(productStartDate, 19);
                    }
                    // 处理有合约的订购产品开始时间
                    if ("mags".equals(msg.get("magsMethod"))) {
                        // 原合约的失效时间
                        String productInactiveTime = productEndDate;
                        if ("50".equals(pInfo.get("productMode"))) {
                            String productActive = (String) pInfo.get("productActiveTime");
                            String productInactive = (String) pInfo.get("productInactiveTime");
                            String sysDate = GetDateUtils.getDate();
                            // 只有当前合约在有效期内算是续约，否则为新办结束时间为月底
                            if (productInactive.compareTo(sysDate) > 0 && productActive.compareTo(sysDate) < 0) {
                                productInactiveTime = GetDateUtils.transDate(productInactive, 19); // 原合约结束时间
                                msg.put("isRenew", "isRenew");
                            }
                        }
                        productEndDate = productInactiveTime;
                        // 后面订购产品时有用
                        msg.put("productInactiveTime", productInactiveTime);
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
                paraMap.put("eparchyCode", eparchyCode);
                paraMap.put("userId", userId);
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

                // 处理产品别名
                preTradeOther(productInfo, tradeOtherList, false);

            }
            if (BOOK.equals(optType)) { // 订购
                // 3-处理订购的产品:主产品的生效时间为下月1号,失效时间为50年;附加产品立即生效,失效时间要进行计算
                String isFinalCode = "";
                String productStartDate = GetDateUtils.getNextMonthFirstDayFormat();
                String productEndDate = "2050-12-31 23:59:59";
                if (!MAIN.equals(productMode)) { // 附加产品处理生效失效时间
                    productStartDate = acceptDate;// 附加产品默认立即生效
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
                // 合约的新产品开始时间为老产品失效时间的下一秒
                if ("mags".equals(msg.get("magsMethod"))) {
                    String productInactiveTime = (String) msg.get("productInactiveTime");
                    productStartDate = GetDateUtils.getSpecifyDateTime(1, 6, productInactiveTime, 1);
                }
                String bookProductId = "";
                String bookProductMode = "";
                String bookBrandCode = "";
                String bookProductTypeCode = "";
                // 查询产品的默认属性信息
                Map temp = new HashMap();
                temp.put("PROVINCE_CODE", provinceCode);
                temp.put("PRODUCT_ID", productId);
                temp.put("EPARCHY_CODE", eparchyCode);
                temp.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                temp.put("PRODUCT_MODE", productMode);
                List<Map> productInfoResult = n25Dao.qryDefaultPackageElement(temp);

                // 未查到产品下的默认元素
                if (IsEmptyUtils.isEmpty(productInfoResult)) {
                    if (MAIN.equals(productMode)) { // 主产品下没有默认元素报错 FIXME
                        throw new EcAopServerBizException(ERROR_CODE, "根据产品Id [" + productId + "] 未查询到产品信息");
                    }
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
                if (MAIN.equals(productMode)) { // 订购的是主产品需要修改用户使用的主套餐
                    newProductId = bookProductId;
                    brandCode = bookBrandCode;
                }
                List<Map> allProductInfo = new ArrayList<Map>();
                if (!IsEmptyUtils.isEmpty(productInfoResult)) {
                    // 选择速率
                    productInfoResult = chooseSpeedByUser(productInfoResult, (List<Map>) userInfo.get("svcInfo"));
                    // 处理国际漫游服务和要继承的资费
                    productInfoResult = preDealProductInfo(productInfoResult, bookProductId, userInfo, msg);
                    // RHQ2017052300058-TDM卡(2I卡)老用户升级包继承处理.订购主产品时才需要处理 by wangmc 20170621
                    if (MAIN.equals(productMode)) {
                        deal2IKeepDiscnt(productInfoResult, bookProductId, userInfo, provinceCode, productInfoList);
                        // 退订特定的主产品时,退订其特定的附加产品或资费
                        specialDealByMainProcduct(productInfoResult, userInfo, provinceCode, productInfoList,
                                bookProductId);
                    }
                    // 拼装产品默认属性的节点,放到下面统一处理
                    allProductInfo.addAll(productInfoResult);
                }
                // 有附加包
                if (!IsEmptyUtils.isEmpty(packageElement)) {
                    List<Map> packageElementInfo = new ArrayList<Map>();
                    for (Map elementMap : packageElement) {
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
                        else {
                            throw new EcAopServerBizException(ERROR_CODE, "订购的元素[" + elementMap.get("elementId")
                                    + "]在产品[" + bookProductId + "]下未查询到,请重试");
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
                // 此时的资费、服务和sp节点中都还只有产品,包和元素 ,已经全处理了,20170401

                Map paraMap = new HashMap();
                paraMap.put("productMode", bookProductMode);
                paraMap.put("productId", bookProductId);
                paraMap.put("productTypeCode", bookProductTypeCode);
                paraMap.put("brandCode", bookBrandCode);
                paraMap.put("modifyTag", "0"); // 订购
                paraMap.put("productStartDate", productStartDate);
                paraMap.put("productEndDate", productEndDate);
                paraMap.put("userId", userId);
                paraMap.put("eparchyCode", eparchyCode);
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
                    tradeUserList.add(userItem);
                }
                // 处理产品别名
                preTradeOther(productInfo, tradeOtherList, true);

            }
            if (CHANGE.equals(optType)) { // 产品内变更
                // 4-处理产品内变更的元素
                newProductId = productId;
                // 如果有附加包
                if (!IsEmptyUtils.isEmpty(packageElement)) {
                    List<String> deleteEle = new ArrayList<String>();
                    List<Map> addElementInfo = new ArrayList<Map>();
                    for (Map elementMap : packageElement) {
                        String modType = (String) elementMap.get("modType");
                        if ("1".equals(modType)) { // 退订
                            deleteEle.add((String) elementMap.get("elementId"));
                        }
                        else if ("0".equals(modType)) {// 订购
                            Map peparam = new HashMap();
                            peparam.put("PROVINCE_CODE", provinceCode);
                            peparam.put("PRODUCT_ID", newProductId);
                            peparam.put("EPARCHY_CODE", eparchyCode);
                            peparam.put("PACKAGE_ID", elementMap.get("packageId"));
                            peparam.put("ELEMENT_ID", elementMap.get("elementId"));
                            List<Map> packageElementInfo = n25Dao.queryPackageElementInfo(peparam);
                            if (!IsEmptyUtils.isEmpty(packageElementInfo)) {
                                addElementInfo.addAll(packageElementInfo);
                            }
                            else {
                                throw new EcAopServerBizException(ERROR_CODE, "订购的元素[" + elementMap.get("elementId")
                                        + "]在产品[" + newProductId + "]下未查询到,请重试");
                            }
                        }
                    }
                    // 处理退订的资费
                    preDeleteInfo(deleteEle, discntList, svcList, spList, userInfo, msg);
                    // 处理订购的资费
                    String modifyTag = "0";
                    String isFinalCode = "N";// 订购的元素默认都要计算偏移
                    preProductInfo(addElementInfo, discntList, svcList, spList, modifyTag, isFinalCode, acceptDate,
                            THE_END_DATE, msg);
                }
            }// END OF 产品内变更
        }
        // 处理base节点
        base = dealBase(msg, custInfo, acctInfo, userInfo, tradeId, acceptDate, newProductId, brandCode, nextMon1stDay);
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
        ext.put("tradeUser", preDataUtil(tradeUserList));
        ext.put("tradeProductType", preDataUtil(productTypeList));
        ext.put("tradeProduct", preDataUtil(productList));
        ext.put("tradeDiscnt", preDataUtil(discntList));
        ext.put("tradeSvc", preDataUtil(svcList));
        ext.put("tradeSp", preDataUtil(spList));
        ext.put("tradeOther", preDataUtil(tradeOtherList));
        // -------------------------RSD2017032200028-关于实现医院小区流量订购的需求(start)---------------------------
        for (int i = 0; i < productInfoList.size(); i++) {
            List<Map> pElements = (List<Map>) productInfoList.get(i).get("packageElement");
            if (!IsEmptyUtils.isEmpty(pElements)) {
                for (Map pEle : pElements) {
                    String elementId = (String) pEle.get("elementId");
                    String itemId = null;
                    String svcItemId = null;// 服务itemId
                    String elementType = (String) pEle.get("elementType");
                    List<Map> serviceAttrs = (List<Map>) pEle.get("serviceAttr");
                    if (!IsEmptyUtils.isEmpty(serviceAttrs)) {
                        if (!IsEmptyUtils.isEmpty(pElements)) {
                            for (int j = 0; j < discntList.size(); j++) {
                                if (elementId.equals(discntList.get(i).get("discntCode"))) {
                                    itemId = (String) discntList.get(i).get("itemId");
                                }
                            }
                            for (Map svc : svcList) {
                                if (elementId.equals(svc.get("serviceId").toString())) {
                                    svcItemId = (String) svc.get("itemId");
                                }
                            }

                        }
                        for (Map attr : serviceAttrs) {
                            String code = (String) attr.get("code");
                            String value = (String) attr.get("value");
                            if ("S".equals(elementType)) { // RSH2017072800004-关于AOP接口-产品变更接口支撑服务属性变更的需求
                                if (null != code && null != value && null != svcItemId) {
                                    tradeSubItemList.add(lan.createTradeSubItem2(code, value, svcItemId));
                                }
                            }
                            else {
                                if (null != code && null != value && null != itemId) {
                                    tradeSubItemList.add(lan.createTradeSubItem3(code, value, itemId));
                                }
                            }
                        }
                        ext.put("tradeSubItem", preDataUtil(tradeSubItemList));
                    }
                }
            }
        }
        // -------------------------RSD2017032200028-关于实现医院小区流量订购的需求(end)-----------------------------
        ext.put("tradeItem", pretradeItem(msg));// 准备tradeItem节点
        msg.put("base", base);
        msg.put("ext", ext);
        msg.put("operTypeCode", "0");
        // 处理返回记录订单表时使用
        msg.put("mProductId", newProductId);
        msg.put("mBrandCode", brandCode);
        msg.put("userId", userId);
        msg.put("custId", custInfo.get("custId"));
        msg.put("acctId", acctInfo.get("acctId"));
        msg.put("eparchyCode", eparchyCode);
        return msg;
    }

    /**
     * 退订特定的主产品时,退订其特定的附加产品或/资费 by wangmc 20180329
     * @param productInfoResult
     * @param userInfo
     * @param provinceCode
     * @param productInfoList
     * @param bookProductId
     */
    private void specialDealByMainProcduct(List<Map> productInfoResult, Map userInfo, String provinceCode,
            List<Map> productInfoList, String bookProductId) {
        // 1.获取需要处理的退订的主产品
        List<Map> dealProducts = n25Dao.querySpecialDealPriduct(MapUtils.asMap("PROVINCE_CODE", provinceCode,
                "SELECT_FLAG", "SPECIAL_UNSUB_PRODUCT", "UN_PRODUCT_ID", userInfo.get("productId"), "PRODUCT_ID",
                bookProductId));
        // 2.处理同套系产品的附加产品
        String specialProducts = dealSameTypeProd(dealProducts, userInfo.get("productId"), bookProductId);
        if (IsEmptyUtils.isEmpty(specialProducts)) {
            return;
        }
        List<Map> productInfo = (List<Map>) userInfo.get("productInfo");
        String nextMonthFirstDay = GetDateUtils.getNextMonthFirstDayFormat();
        // 3.遍历用户原有产品,若含有次月有效的需退订产品,则拼装退订处理节点
        for (Map product : productInfo) {
            if ("01".equals(product.get("productMode"))
                    && specialProducts.contains(String.valueOf(product.get("productId")))
                    && nextMonthFirstDay.compareTo(product.get("productInactiveTime") + "") < 0) {
                productInfoList.add(MapUtils.asMap("productId", product.get("productId"), "productMode", "0",
                        "optType", "01"));
            }
        }
    }

    /**
     * 处理同套系2I产品退订附加产品的关系,原产品下的优惠附加产品在转为同套系的主产品时,不退订,转为其他主产品时退订
     * @param dealProducts
     * @param unBookProductId
     * @param bookProductId
     * @return
     */
    private static String dealSameTypeProd(List<Map> dealProducts, Object unBookProductId, String bookProductId) {
        if (IsEmptyUtils.isEmpty(dealProducts)) {
            return null;
        }
        String specialProducts = "";// 原产品可以使用的附加产品,退订该产品时,需退订这些附加产品
        String bookCanUseAddProduct = "";// 订购的目标产品可以使用的附加产品
        for (Map dealProd : dealProducts) {
            if (unBookProductId.equals(dealProd.get("PRODUCT_ID"))) {
                specialProducts = String.valueOf(dealProd.get("ATTR_VALUE"));
            }
            else if (bookProductId.equals(dealProd.get("PRODUCT_ID"))) {
                bookCanUseAddProduct = String.valueOf(dealProd.get("ATTR_VALUE"));
            }
        }
        // 目标产品下也有可以用的优惠时,在原产品需要退订的附加产品中去掉这些附加产品
        if (!IsEmptyUtils.isEmpty(bookCanUseAddProduct)) {
            String[] productIds = bookCanUseAddProduct.split("\\|");
            for (String productId : productIds) {
                specialProducts = specialProducts.replaceAll(productId, "");
            }
        }
        return specialProducts;
    }

    /**
     * RHQ2017052300058-TDM卡(2I卡)老用户升级包继承处理.
     * 用户原套餐包含有升级包(非默认)并变更主套餐时,需要给用户订购新套餐的升级包(非默认),并剔除新套餐的老资费包(默认)
     * 目前的针对的目标主套餐为:D卡（滴滴大王卡90065147、滴滴小王卡90065148）,T卡（腾讯天王卡）90155946
     * 场景包含:滴滴大小王卡互转,腾讯大王卡(90063345)转腾讯天王卡
     * 新增场景:附加产品的映射继承关系 20171127
     * @author wangmc 20170621
     * @param productInfoResult
     * @param bookProductId
     * @param userInfo
     * @param provinceCode
     * @param productInfoList:外围传进来的产品节点
     */
    private void deal2IKeepDiscnt(List<Map> productInfoResult, String bookProductId, Map userInfo, String provinceCode,
            List<Map> productInfoList) {
        // 1.用户的目标套餐为滴滴大小王卡或者腾讯天王卡时继续执行,目标套餐配置在TD_B_COMMODITY_TRANS_ITEM表中
        String targetProducts = n25Dao.queryNeedKeepDisProduct(MapUtils.asMap("PROVINCE_CODE", provinceCode));
        if (!targetProducts.contains(bookProductId)) {
            return;
        }
        // 2.老用户为升级过后的套餐时继续执行,先去库里获取原套餐下的升级资费
        Object oldProductId = userInfo.get("productId");// 用户原产品
        Map sqlMap = MapUtils.asMap("PRODUCT_ID", oldProductId, "PROVINCE_CODE", provinceCode);
        List<Map> needKeepDis = n25Dao.queryNeedKeepDiscnt(sqlMap);
        if (IsEmptyUtils.isEmpty(needKeepDis)) {
            return;
        }

        // 3.根据用户原有资费/附加产品,判断是否需要继承,并获取订购主产品下对应要继承的元素或附加产品数据
        Map dataMap = getDealData(userInfo, needKeepDis, sqlMap, bookProductId);
        List<Map> needDealDis = (List<Map>) dataMap.get("needDealDis");
        if (IsEmptyUtils.isEmpty(needDealDis)) {
            return;
        }
        boolean hasKeep = false; // 用于判断是否含有对应档位的升级资费
        List<Map> needRemove = new ArrayList<Map>();
        for (Map dealDis : needDealDis) {
            if (String.valueOf(dealDis.get("ATTR_CODE")).contains("2I_DIS_KEEP")) {// 升级后的资费
                hasKeep = true;
                if ("P".equals(dealDis.get("ELEMENT_TYPE_CODE"))) {// 如果是附加产品的继承
                    String attrValue = (String) dealDis.get("ELEMENT_ID");
                    List<Map> packageElement = new ArrayList<Map>();
                    if (!IsEmptyUtils.isEmpty(attrValue)) {// 此处配置为:包ID,元素类型,元素ID|包ID,元素类型,元素ID
                        String[] packEleIds = attrValue.split("\\|");
                        for (String packEleId : packEleIds) {
                            String[] packEles = packEleId.split(",");
                            packageElement.add(MapUtils.asMap("packageId", packEles[0], "elementId", packEles[1],
                                    "elementType", packEles[2], "modType", "0"));
                        }
                    }
                    // 将需要订购的附加产品添加的外围传进来的产品列表中
                    productInfoList.add(MapUtils.asMap("productId", String.valueOf(dealDis.get("PACKAGE_ID")),
                            "productMode", "0", "optType", "00", "packageElement", packageElement));
                    continue;
                }
                productInfoResult.add(dealDis);
            }
            else if (String.valueOf(dealDis.get("ATTR_CODE")).contains("2I_DIS_REMOVE")) {// 原资费
                for (Map element : productInfoResult) {
                    String dealProductId = String.valueOf(dealDis.get("PRODUCT_ID"));
                    String dealPackageId = String.valueOf(dealDis.get("PACKAGE_ID"));
                    String dealDiscnt = String.valueOf(dealDis.get("ELEMENT_ID"));

                    String productId = String.valueOf(element.get("PRODUCT_ID"));
                    String packageId = String.valueOf(element.get("PACKAGE_ID"));
                    String discnt = String.valueOf(element.get("ELEMENT_ID"));

                    if (dealProductId.equals(productId) && dealPackageId.equals(packageId) && dealDiscnt.equals(discnt)) {
                        needRemove.add(element);
                        break;
                    }
                }
            }
        }
        // 含有对应档位的升级资费时,才会删除新产品下的原有资费
        if (hasKeep && !IsEmptyUtils.isEmpty(needRemove)) {
            productInfoResult.removeAll(needRemove);
        }
        // 把退订的产品添加到外围传进来的产品节点中,会在原代码处理
        if (!IsEmptyUtils.isEmpty(dataMap.get("unBookAddProduct"))) {
            productInfoList.add(MapUtils.asMap("productId", dataMap.get("unBookAddProduct"), "productMode", "0",
                    "optType", "01"));
        }

    }

    /**
     * 根据用户原有资费/附加产品,判断是否需要继承,并获取订购主产品下对应要继承的元素或附加产品
     * @param userInfo
     * @param needKeepDis
     * @param sqlMap
     * @param bookProductId
     * @return
     */
    private Map getDealData(Map userInfo, List<Map> needKeepDis, Map sqlMap, Object bookProductId) {
        // 用户的原有资费
        List<Map> discntInfo = (List<Map>) userInfo.get("discntInfo");
        List<Map> productInfo = (List<Map>) userInfo.get("productInfo");
        // 继承支持多档位同时存在的场景 20180801
        List<String> keepDisLevelList = new ArrayList<String>(); // 资费继承等级集合
        List<String> getRemoveDisLevelList = new ArrayList<String>(); // 获取去除资费等级的集合
        List<String> keepProdLevelList = new ArrayList<String>();// 附加产品继承等级集合
        List<String> getRemoveProdLevelList = new ArrayList<String>();// 获取去除附加产品等级的集合
        String unBookAddProduct = "";// 要退订的附加产品ID
        String nextMonthFirstDay = GetDateUtils.getNextMonthFirstDayFormat();
        for (Map keepDis : needKeepDis) {
            String elementTypeCode = String.valueOf(keepDis.get("ELEMENT_TYPE_CODE"));
            String keepPackageId = String.valueOf(keepDis.get("PACKAGE_ID"));
            // P表示为附加产品的继承关系,此时PACKAGE_ID中为附加产品ID,ELEMENT_ID中为包ID和元素ID
            if ("P".equals(elementTypeCode)) {
                for (Map product : productInfo) {
                    if ("01".equals(product.get("productMode")) && keepPackageId.equals(product.get("productId"))
                            && nextMonthFirstDay.compareTo(product.get("productInactiveTime") + "") < 0) {
                        keepProdLevelList.add((String) keepDis.get("ATTR_CODE"));
                        getRemoveProdLevelList.add("%" + keepDis.get("ATTR_CODE") + "|%");
                        unBookAddProduct = (String) product.get("productId");
                    }
                }
            }
            else {
                for (Map dis : discntInfo) {
                    if (keepDis.get("PRODUCT_ID").equals(dis.get("productId"))
                            && keepPackageId.equals(dis.get("packageId"))
                            && keepDis.get("ELEMENT_ID").equals(dis.get("discntCode"))
                            && nextMonthFirstDay.compareTo(dis.get("endDate") + "") < 0) {
                        // 次月存在升级后的资费,即需要继承
                        keepDisLevelList.add((String) keepDis.get("ATTR_CODE"));
                        getRemoveDisLevelList.add("%" + keepDis.get("ATTR_CODE") + "|%");
                    }
                }
            }
        }
        // 不存在升级后的资费,结束操作
        if (IsEmptyUtils.isEmpty(keepDisLevelList) && IsEmptyUtils.isEmpty(keepProdLevelList)) {
            return new HashMap();
        }
        // 3.获取目标产品下的升级资费包和原资费包,并将原资费包剔除,添加升级资费包
        sqlMap.put("PRODUCT_ID", bookProductId);
        sqlMap.put("keepLevels", keepDisLevelList);
        sqlMap.put("getRemoves", getRemoveDisLevelList);
        sqlMap.put("KEEP_TYPE", "D");
        List<Map> needDealDis = n25Dao.query2INeedDealDiscnt(sqlMap);
        if (!IsEmptyUtils.isEmpty(keepProdLevelList)) {
            sqlMap.put("keepLevels", keepProdLevelList);
            sqlMap.put("getRemoves", getRemoveProdLevelList);
            sqlMap.put("KEEP_TYPE", "P");
            needDealDis.addAll(n25Dao.query2INeedDealDiscnt(sqlMap));
        }
        Map dataMap = new HashMap();
        dataMap.put("needDealDis", needDealDis);
        if (!IsEmptyUtils.isEmpty(unBookAddProduct)) {
            dataMap.put("unBookAddProduct", unBookAddProduct);
        }
        return dataMap;
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
        productItem.put("itemId", TradeManagerUtils.getSequence(paraMap.get("eparchyCode"), "SEQ_ITEM_ID"));
        // productItem.put("itemId", getItemId());// TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID"));
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
                return chooseSpeed(productInfoResult, speed);
            }
        }
        // 默认选择300M的速率
        return chooseSpeed(productInfoResult, "300");
    }

    /**
     * 选择速率
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
     * @param productInfoResult
     * @param productId
     * @param userInfo
     * @param userInfo
     * @param msg
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
                // FIXME 主产品下的所有资费全按照偏移计算
                // 订购主产品下资费全部按照偏移计算
                String productMode = String.valueOf(productInfoResult.get(j).get("PRODUCT_MODE"));
                if (("00".equals(productMode) && "0".equals(modifyTag)) || StringUtils.isNotEmpty(isFinalCode)
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
                // 合约的资费开始、结束时间与产品保持一致
                if ("mags".equals(msg.get("magsMethod"))) {
                    dis.put("startDate", startDate);
                    dis.put("endDate", endDate);
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
                // svc.put("itemId", getItemId());// TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID"));
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
                sp.put("endDate", endDate);
                sp.put("updateTime", startDate);
                sp.put("remark", "");
                sp.put("modifyTag", modifyTag);
                sp.put("payUserId", userId);
                sp.put("itemId", TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID"));
                // sp.put("itemId", getItemId());// TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID"));
                sp.put("userIdA", "-1");
                sp.put("xDatatype", "NULL");
                spList.add(sp);
            }
        }
    }

    /**
     * 处理退订的资费
     * @param deleteEle
     * @param discntList
     * @param svcList
     * @param spList
     * @param userInfo
     * @throws Exception
     */
    private void preDeleteInfo(List<String> deleteEle, List<Map> discntList, List<Map> svcList, List<Map> spList,
            Map userInfo, Map msg) throws Exception {
        if (IsEmptyUtils.isEmpty(deleteEle)) {
            return;
        }
        List<Map> discntInfo = (List<Map>) userInfo.get("discntInfo");
        List<Map> spInfo = (List<Map>) userInfo.get("spInfo");
        List<Map> svcInfo = (List<Map>) userInfo.get("svcInfo");
        String acceptDate = GetDateUtils.transDate(GetDateUtils.getDate(), 19); // 当前时间
        String nextMon1stDay = GetDateUtils.getNextMonthFirstDayFormat();// yyyyMMddHHmmss
        String endDate = GetDateUtils.getSpecifyDateTime(1, 7, nextMon1stDay, -1);// yyyyMMddHHmmss
        String modifyTag = "1";// 退订
        String isFinalCode = "";

        for (String delEle : deleteEle) {
            if (!IsEmptyUtils.isEmpty(discntInfo)) {
                for (Map dis : discntInfo) {
                    if (delEle.equals(dis.get("discntCode") + "")) {
                        Map discntItem = new HashMap();
                        Map discnt = n25Dao.qryDiscntAttr(MapUtils.asMap("DISCNT_CODE", dis.get("discntCode")));
                        String endmode = "" + discnt.get("END_MODE");
                        if ("0".equals(endmode)) {// 立即结束
                            endDate = acceptDate;
                        }
                        else if ("2".equals(endmode)) { // 2的时候取当天23:59:59
                            endDate = GetDateUtils.getSpecifyDateTime(1, 1, acceptDate, 1);
                            endDate = GetDateUtils.getSpecifyDateTime(1, 6, endDate, -1);
                        }
                        discntItem.put("PRODUCT_ID", dis.get("productId"));
                        discntItem.put("PACKAGE_ID", dis.get("packageId"));
                        discntItem.put("ELEMENT_ID", dis.get("discntCode"));
                        discntItem.put("ELEMENT_TYPE_CODE", "D");
                        List<Map> elementInfo = new ArrayList<Map>();
                        elementInfo.add(discntItem);
                        preProductInfo(elementInfo, discntList, svcList, spList, modifyTag, isFinalCode,
                                GetDateUtils.transDate("" + dis.get("startDate"), 19), endDate, msg);
                        break;
                    }
                }
            }
            if (!IsEmptyUtils.isEmpty(svcInfo)) {
                for (Map svc : svcInfo) {
                    if (delEle.equals(svc.get("serviceId") + "")) {
                        Map serviceItem = new HashMap();
                        serviceItem.put("PRODUCT_ID", svc.get("productId"));
                        serviceItem.put("PACKAGE_ID", svc.get("packageId"));
                        serviceItem.put("ELEMENT_ID", svc.get("serviceId"));
                        serviceItem.put("ELEMENT_TYPE_CODE", "S");

                        List<Map> elementInfo = new ArrayList<Map>();
                        elementInfo.add(serviceItem);
                        preProductInfo(elementInfo, discntList, svcList, spList, modifyTag, isFinalCode,
                                (String) svc.get("startDate"), acceptDate, msg); // 退订服务是立即生效
                        break;
                    }
                }
            }
            if (!IsEmptyUtils.isEmpty(spInfo)) {
                for (Map sp : spInfo) {
                    if (delEle.equals(sp.get("spServiceId") + "")) {
                        Map spItem = new HashMap();
                        spItem.put("PRODUCT_ID", sp.get("productId"));
                        spItem.put("PACKAGE_ID", sp.get("packageId"));
                        spItem.put("ELEMENT_ID", sp.get("spServiceId"));
                        spItem.put("ELEMENT_TYPE_CODE", "X");

                        List<Map> elementInfo = new ArrayList<Map>();
                        elementInfo.add(spItem);
                        preProductInfo(elementInfo, discntList, svcList, spList, modifyTag, isFinalCode,
                                GetDateUtils.transDate((String) sp.get("startDate"), 19), endDate, msg);
                        break;
                    }
                }
            }
        }
    }

    /**
     * 拼装base节点
     * @param msg
     * @param custInfo
     * @param acctInfo
     * @param userInfo
     * @param tradeId
     * @param acceptDate
     * @param newProductId
     * @param brandCode
     * @param realStartDate
     * @return
     */
    private Map dealBase(Map msg, Map custInfo, Map acctInfo, Map userInfo, String tradeId, String acceptDate,
            String newProductId, String brandCode, String realStartDate) {
        Map base = new HashMap();

        base.put("subscribeId", tradeId);
        base.put("tradeId", tradeId);
        base.put("acceptDate", acceptDate);
        base.put("tradeDepartId", msg.get("departId"));
        base.put("cityCode", userInfo.get("cityCode"));
        base.put("areaCode", msg.get("eparchyCode"));
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        // 添加strInModeCode赋值X、E
        String strInModeCode = "";
        if ((modecodes).contains("" + msg.get("appCode"))) {
            strInModeCode = "X";
        }
        else {
            strInModeCode = "E";
        }
        base.put("inModeCode", strInModeCode);
        base.put("tradeStaffId", msg.get("operatorId"));
        base.put("tradeTypeCode", "001".equals(msg.get("serType") + "") ? "0110" : "0120");
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
        base.put("execTime", realStartDate);
        base.put("operFee", "0"); // 营业费用
        base.put("foregift", "0"); // 押金
        base.put("advancePay", "0"); // 预存
        base.put("feeState", "");
        base.put("feeStaffId", "");
        base.put("cancelTag", "0");
        base.put("checktypeCode", "8");
        base.put("chkTag", "0");
        base.put("actorName", "");
        base.put("actorCertTypeId", "");
        base.put("actorPhone", "");
        base.put("actorCertNum", "");
        base.put("contact", custInfo.get("contact"));
        base.put("contactAddress", custInfo.get("certAddr"));
        base.put("contactPhone", custInfo.get("contactPhone"));
        base.put("remark", "");

        return base;
    }

    /**
     * 准备tradeItem节点
     * @param msg
     * @return
     */
    private Map pretradeItem(Map msg) {
        Map tradeItem = new HashMap();
        List<Map> tradeItemList = new ArrayList<Map>();
        tradeItemList.add(new LanUtils().createAttrInfoNoTime("DEVELOP_STAFF_ID", msg.get("recomPersonId")));
        tradeItemList.add(new LanUtils().createAttrInfoNoTime("DEVELOP_DEPART_ID", msg.get("channelId")));
        if ("0".equals(msg.get("deductionTag"))) {
            tradeItemList.add(LanUtils.createTradeItem("FEE_TYPE", "1"));// RHA2017102300039-关于支撑通过标记控制代理商办理业务时是否扣周转金的需求
        }
        tradeItem.put("item", tradeItemList);
        return tradeItem;
    }

    /**
     * 准备tradeOther节点
     * @param msg
     * @return
     */
    private void preTradeOther(Map productInfo, List<Map> tradeOtherList, boolean flag) {

        // 针对退订订购场景
        // RHQ2017081500065-中小企业新增产品别名需求 by maly --start
        String companyId = (String) productInfo.get("companyId");// 产品企业编码
        String productNameX = (String) productInfo.get("productNameX");// 产品别名

        if (IsEmptyUtils.isEmpty(companyId) && IsEmptyUtils.isEmpty(productNameX)) {
            return;
        }
        if (IsEmptyUtils.isEmpty(companyId) && !IsEmptyUtils.isEmpty(productNameX)) {
            throw new EcAopServerBizException(ERROR_CODE, "产品业务编码和产品别名填写不完整");
        }
        else if (!IsEmptyUtils.isEmpty(companyId) && IsEmptyUtils.isEmpty(companyId)) {
            throw new EcAopServerBizException(ERROR_CODE, "产品业务编码和产品别名填写不完整");
        }
        else if (!IsEmptyUtils.isEmpty(companyId) && !IsEmptyUtils.isEmpty(productNameX)) {
            Map tradeOther = new HashMap();
            tradeOther.put("xDatatype", "NULL");
            tradeOther.put("modifyTag", flag == true ? "0" : "1");
            tradeOther.put("rsrvStr1", productNameX);
            tradeOther.put("rsrvStr2", productInfo.get("productId"));
            tradeOther.put("rsrvValueCode", "TIBM"); //
            tradeOther.put("rsrvValue", companyId); // USER_ID
            if (flag) {
                tradeOther.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
            }
            else {
                tradeOther.put("startDate", GetDateUtils.getDate());
            }
            tradeOther.put("endDate", flag == true ? THE_END_DATE : GetDateUtils.getMonthLastDayFormat());
            tradeOtherList.add(tradeOther);
        }
    }

    public static void main(String[] args) throws Exception {
        String acceptDate = GetDateUtils.transDate(GetDateUtils.getDate(), 19); // 当前时间
        String nextMon1stDay = GetDateUtils.getNextMonthFirstDayFormat();// yyyyMMddHHmmss
        String endDate = GetDateUtils.getSpecifyDateTime(1, 7, nextMon1stDay, -1);// yyyyMMddHHmmss
        // String acceptDate = GetDateUtils.getDate();// 操作日期,当前时间
        // String nextMon1stDay = GetDateUtils.getNextMonthFirstDayFormat();// 订购生效时间,次月1日
        // String nowMonLastDay = GetDateUtils.getMonthLastDayFormat();// 退订失效时间,当月月底
        System.out.println(acceptDate + "," + nextMon1stDay + "," + endDate);
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
     * 处理返回...搬的老曾的..
     * @param exchange
     * @param msg
     */
    private void dealReturn(Exchange exchange, Map msg) {
        Map out = exchange.getOut().getBody(Map.class);
        List<Map> rspInfo = (List) out.get("rspInfo");
        if (null == rspInfo || rspInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "cBSS统一预提交未返回应答信息");
        }
        // Map rspMap = rspInfo.get(0);
        Map realOut = new HashMap();
        realOut.put("bssOrderId", rspInfo.get(0).get("bssOrderId"));
        Integer totalFee = 0;
        List feeList = new ArrayList();
        for (Map rspMap : rspInfo) {
            List<Map> provinceOrderInfo = (List) rspMap.get("provinceOrderInfo");
            if (null != provinceOrderInfo && provinceOrderInfo.size() > 0) {
                // 费用计算
                for (Map provinceOrder : provinceOrderInfo) {
                    totalFee = totalFee + Integer.valueOf(changeFenToLi(provinceOrder.get("totalFee")));
                    List<Map> preFeeInfoRsp = (List<Map>) provinceOrder.get("preFeeInfoRsp");
                    if (null == preFeeInfoRsp || preFeeInfoRsp.isEmpty()) {
                        continue;
                    }
                    feeList.addAll(dealFee(preFeeInfoRsp));
                }

            }
        }
        if (!IsEmptyUtils.isEmpty(feeList)) {
            realOut.put("feeInfo", feeList);
            // 记录trade表 正式提交的时候需要IS_REMOTE为3修改payTag 为1
            msg.put("ordersId", rspInfo.get(0).get("provOrderId"));
            insert2CBSSTradeChangePro(msg);
        }
        realOut.put("totalFee", totalFee + "");
        exchange.getOut().setBody(realOut);
    }

    /**
     * msg中包含province,SUBSCRIBE_ID
     * @param msg
     */
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
            }
            else {
                tradeParam.put("IN_MODE_CODE", msg.get("eModeCode"));
            }
            tradeParam.put("SOP_STATE", "0");
            tradeParam.put("PRODUCT_ID", msg.get("mProductId"));
            tradeParam.put("BRAND_CODE", msg.get("mBrandCode"));
            tradeParam.put("USER_ID", msg.get("userId"));
            tradeParam.put("ACCT_ID", msg.get("acctId"));
            tradeParam.put("CUST_ID", msg.get("custId"));
            tradeParam.put("ACCEPT_DATE", GetDateUtils.getDate());
            tradeParam.put("TRADE_STAFF_ID", msg.get("operatorId"));
            tradeParam.put("TRADE_DEPART_ID", msg.get("channelId"));
            // 数据库中该字段为非空,23转4接口规范中区县为非必传,不传时插表会报错,增加默认值处理 by wangmc see 20170510版本技术文档
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
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "预提交完成后记录订单信息失败" + e.getMessage());
        }
    }

    /**
     * 搬的老曾的..
     * @param feeList
     * @return
     */
    private List<Map> dealFee(List<Map> feeList) {
        List<Map> retFeeList = new ArrayList<Map>();
        for (Map fee : feeList) {
            Map retFee = new HashMap();
            retFee.put("feeId", fee.get("feeTypeCode") + "");// feeTypeCode
            retFee.put("feeCategory", fee.get("feeMode") + "");// feeMode
            retFee.put("feeDes", fee.get("feeTypeName") + "");// feeTypeName
            if (null != fee.get("maxDerateFee"))
                retFee.put("maxRelief", changeFenToLi(fee.get("maxDerateFee")));// 非必返maxDerateFee
            retFee.put("origFee", changeFenToLi(fee.get("fee")));// fee
            retFeeList.add(retFee);
        }
        return retFeeList;
    }

    /**
     * 搬的老曾的..
     * @param fee
     * @return
     */
    private String changeFenToLi(Object fee) {
        return null == fee || "".equals(fee + "") || "0".equals(fee + "") || "null".equals(fee + "") ? "0" : fee + "0";

    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
