package com.ailk.ecaop.biz.sub.lan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.product.DealNewCbssProduct;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.NewProductUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.google.common.collect.Maps;

/**
 * 固网光改移机
 * 
 * @author Yangzg
 *
 */
@EcRocTag("BroadChangeApp")
@SuppressWarnings(value = { "unchecked", "rawtypes", "static-access" })
public class BroadChangeAppProcessor extends BaseAopProcessor implements ParamsAppliable {

    private final LanUtils lan = new LanUtils();

    private static final String[] PARAM_ARRAY = { "ecaop.trade.cbss.checkUserParametersMapping", "ecaop.trades.city.cancelPre.paramtersmapping" };

    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[2];

    private String ERROR_CODE = "9999";
    private String THE_END_DATE = "2050-12-31 23:59:59";

    private String BOOK = "0";// 订购
    private String UNBOOK = "1";// 退订
    private String CHANGE = "2"; // 变更

    private String MAIN = "00";// 主产品
    private String SUB = "01";// 附加产品

    private DealNewCbssProduct n25Dao = new DealNewCbssProduct();

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        // 调用三户接口
        msg.put("tradeType", "1");
        msg.put("tradeTypeCode", "9999");
        msg.put("serviceClassCode", "0040");
        msg.put("netTypeCode", "0040");
        msg.put("getMode", "1111111111100013010000000100001");
        if ("03".equals(msg.get("netType"))) {
            msg.put("serviceClassCode", "0030");
            msg.put("netTypeCode", "0030");
        }
        try {
            lan.preData(pmp[0], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
            lan.xml2Json("ecaop.trades.cbss.threePart.template", exchange);
        } catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用三户接口失败！" + e.getMessage());
        }
        Map threepartMap = exchange.getOut().getBody(Map.class);
        List<Map> threeList = new ArrayList<Map>();
        Map<String, Object> threeMap = new HashMap<String, Object>();
        List<Map> userInfoList = (ArrayList<Map>) threepartMap.get("userInfo");
        List<Map> custInfoList = (ArrayList<Map>) threepartMap.get("custInfo");
        List<Map> acctInfoList = (ArrayList<Map>) threepartMap.get("acctInfo");
        // 校验是否没有返回三户信息
        String[] infoKeys = new String[] { "userInfo", "custInfo", "acctInfo" };
        Map errorDetail = MapUtils.asMap("userInfo", "用户信息", "custInfo", "客户信息", "acctInfo", "账户信息");
        for (String infoKey : infoKeys) {
            if (IsEmptyUtils.isEmpty((List<Map>) threepartMap.get(infoKey))) {
                throw new EcAopServerBizException("9999", "调三户未返回" + errorDetail.get(infoKey));
            }
            threeMap.put(infoKey, ((List<Map>) threepartMap.get(infoKey)).get(0));
        }

        if (userInfoList.size() != 0) {
            threeList.add(userInfoList.get(0));
        }
        if (custInfoList.size() != 0) {
            threeList.add(custInfoList.get(0));
        }
        if (acctInfoList.size() != 0) {
            threeList.add(acctInfoList.get(0));
        }
        msg.put("eparchyCode", new ChangeCodeUtils().changeEparchyUnStatic(msg));// 转换成cb地市

        msg.put("userId", userInfoList.get(0).get("userId"));
        msg.put("userInfoList", userInfoList);// 三户返回的userInfo

        // 准备预提交数据+调用预提交
        msg.put("operTypeCode", "0");
        String[] copyArray = { "operatorId", "province", "channelId", "city", "district", "channelType" };
        Map preSubmitMap = Maps.newHashMap();
        preSubmitMap.put("inModeCode", new ChangeCodeUtils().getInModeCode(exchange.getAppCode()));
        MapUtils.arrayPut(preSubmitMap, msg, copyArray);
        // 获取CB流水号
        String tradeId;
        try {
            tradeId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, preSubmitMap), "seq_trade_id");
            msg.put("tradeId", tradeId);// base节点里的tradeId是外围传入的ordersId还是获取CB的流水？
        } catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "获取CB侧流水失败" + e.getMessage());
        }
        String eparchyCode = ChangeCodeUtils.changeEparchy(msg);
        msg.put("eparchyCode", eparchyCode);
        msg.put("ordersId", tradeId);
        msg.put("startDate", GetDateUtils.getDate());
        msg.put("endDate", "20501231235959");

        // 处理产品节点
        Exchange exchangeCopy = ExchangeUtils.ofCopy(exchange, msg);
        msg = dealProductInfo(exchangeCopy, msg, threeMap);

        body.put("msg", msg);
        exchange.getIn().setBody(body);

        try {
            lan.preData(pmp[1], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
            lan.xml2Json("ecaop.trades.city.cancelPre.template", exchange);
        } catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "调用预提交失败" + e.getMessage());
        }
        dealReturn(exchange);// 处理返回
    }

    /**
     * 处理产品
     * 
     * @param exchange
     * @param msg
     * @param threeMap
     * @return
     * @throws Exception
     */
    private Map dealProductInfo(Exchange exchange, Map msg, Map<String, Object> threePartMap) throws Exception {
        // 获取三户返回的信息
        Map userInfo = (Map) threePartMap.get("userInfo");
        List<Map> oldProducts = (List<Map>) userInfo.get("productInfo");// 三户返回的产品信息
        String newProductId = "";
        String boradDiscntId = "";

        // 获取外围传进来userInfo
        List<Map> userInfoList = (List<Map>) msg.get("userInfo");
        if (!IsEmptyUtils.isEmpty(userInfoList)) {
            Map<String, Object> userInfoMsg = userInfoList.get(0);

            String acceptDate = GetDateUtils.transDate(GetDateUtils.getDate(), 19);
            // String nextMon1stDay = GetDateUtils.getSpecifyDateTime(1, 3, acceptDate, 1);
            String userId = (String) msg.get("userId");

            String brandCode = String.valueOf(userInfo.get("brandCode"));
            // 地市从三户中取的
            String eparchyCode = String.valueOf(userInfo.get("eparchyCode"));
            newProductId = String.valueOf(userInfo.get("productId"));

            String provinceCode = "00" + msg.get("province");
            msg.put("eparchyCode", eparchyCode);
            msg.put("provinceCode", provinceCode);
            String serialNumber = (String) msg.get("serialNumber");
            //获取外围传来的同移宽带号码
            String brandNumber = (String) msg.get("brandNumber");


            /**
             * 处理退订的产品:生效时间取原产品的生效时间,失效时间为本月底,并添加tradeOrter节点,放退订的产品
             */
            Map ext = new HashMap();
            List<Map> productTypeList = new ArrayList<Map>();
            List<Map> productList = new ArrayList<Map>();
            List<Map> discntList = new ArrayList<Map>();
            List<Map> svcList = new ArrayList<Map>();
            List<Map> spList = new ArrayList<Map>();
            List<Map> tradeOtherList = new ArrayList<Map>();
            List<Map> tradeUserList = new ArrayList<Map>();

            // 处理外围传进来的产品信息
            List<Map> productInfoList = (List<Map>) userInfoMsg.get("productInfo");
            if (!IsEmptyUtils.isEmpty(productInfoList)) {
                for (int i = 0; i < productInfoList.size(); i++) {
                    Map productInfo = productInfoList.get(i);
                    String optType = (String) productInfo.get("optType");// 0-订购,1-退订,2-变更
                    String productIdMsg = (String) productInfo.get("oldProductId");
                    if (!UNBOOK.equals(optType) && !BOOK.equals(optType) && !CHANGE.equals(optType)) {
                        throw new EcAopServerBizException(ERROR_CODE, "产品[" + productIdMsg + "]的操作标识[" + optType + "]不在取值范围[0,1,2]中,请确认");
                    }
                    String firstMonBillMode = "02";// 订购时默认首月全量全价
                    String productMode = (String) productInfo.get("productMode");
                    List<Map> packageElement = (List<Map>) productInfo.get("packageElement");

                    if (!"0,1".contains(productMode)) {
                        throw new EcAopServerBizException(ERROR_CODE, "发起方输入的产品 [" + productIdMsg + "] 的产品编码有误,产品编码:[" + productMode + "]");
                    }
                    productMode = "1".equals(productMode) ? "00" : "01";// 1-主产品,0-附加产品
                    // 由于可以订购活动,所以需要先查询一下非主产品的真正的产品模式
                    if (SUB.equals(productMode)) {
                        Map tempMap = MapUtils.asMap("PRODUCT_ID", productIdMsg, "PROVINCE_CODE", provinceCode);
                        productMode = (String) n25Dao.queryProductModeByCommodityId(tempMap);
                    }
                    if (UNBOOK.equals(optType)) {// 退订
                        // 查询产品的属性信息
                        Map inMap = new HashMap();
                        inMap.put("PRODUCT_ID", productIdMsg);
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
                            tradeOther.put("rsrvValueCode", "NEXP");
                            tradeOther.put("rsrvValue", userId); // USER_ID
                            tradeOther.put("startDate", productStartDate);
                            tradeOther.put("endDate", productEndDate);

                            tradeOtherList.add(tradeOther);

                        }
                    }
                    if (BOOK.equals(optType)) { // 订购
                        // 3-处理订购的产品:主产品的生效时间为下月1号,失效时间为50年;附加产品立即生效,失效时间要进行计算
                        String isFinalCode = "";
                        String productStartDate = GetDateUtils.getNextMonthFirstDayFormat();
                        String productEndDate = "2050-12-31 23:59:59";
                        if (!MAIN.equals(productMode)) { // 附加产品处理生效失效时间
                            productStartDate = acceptDate;// 附加产品默认立即生效
                            isFinalCode = "N";
                            String endDateType = NewProductUtils.getEndDateType(productIdMsg);
                            if (IsEmptyUtils.isEmpty(endDateType)) {
                                isFinalCode = "X";
                            } else {
                                isFinalCode = endDateType;
                            }
                            if ("N,X".contains(isFinalCode)) {// 附加产品有效期按偏移计算
                                Map dateMap = TradeManagerUtils.getEffectTime(productIdMsg, productStartDate, productEndDate);
                                productStartDate = (String) dateMap.get("monthFirstDay");// 附加产品的生效时间
                                productEndDate = (String) dateMap.get("monthLasttDay");// 附加产品的失效时间
                            }
                        }

                        String bookProductId = "";
                        String bookProductMode = "";
                        String bookBrandCode = "";
                        String bookProductTypeCode = "";
                        // 查询产品的默认属性信息
                        Map temp = new HashMap();
                        temp.put("PROVINCE_CODE", provinceCode);
                        temp.put("PRODUCT_ID", productIdMsg);
                        temp.put("EPARCHY_CODE", eparchyCode);
                        temp.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                        temp.put("PRODUCT_MODE", productMode);
                        List<Map> productInfoResult = n25Dao.qryDefaultPackageElement(temp);

                        // 未查到产品下的默认元素
                        if (IsEmptyUtils.isEmpty(productInfoResult)) {
                            if (MAIN.equals(productMode)) { // 主产品下没有默认元素报错
                                throw new EcAopServerBizException(ERROR_CODE, "根据产品Id [" + productIdMsg + "] 未查询到产品信息");
                            }
                            // 未查询到附加产品的默认资费或服务,不报错,去TD_B_PRODUCT表查询,产品不存在就抛错,存在继续执行
                            Map productInfos = n25Dao.queryProductInfoByProductId(temp);
                            if (!IsEmptyUtils.isEmpty(productInfos)) {
                                bookProductId = String.valueOf(productInfos.get("PRODUCT_ID"));
                                bookProductMode = (String) productInfos.get("PRODUCT_MODE");
                                bookBrandCode = (String) productInfos.get("BRAND_CODE");
                                bookProductTypeCode = (String) productInfos.get("PRODUCT_TYPE_CODE");
                            } else {
                                throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + productIdMsg + "】的产品信息");
                            }
                        } else {
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
                        // 有附加包
                        if (!IsEmptyUtils.isEmpty(packageElement)) {
                            List<Map> packageElementInfo = new ArrayList<Map>();
                            for (Map elementMap : packageElement) {
                                Map peparam = new HashMap();
                                peparam.put("PROVINCE_CODE", provinceCode);
                                peparam.put("PRODUCT_ID", productIdMsg);
                                peparam.put("EPARCHY_CODE", eparchyCode);
                                peparam.put("PACKAGE_ID", elementMap.get("packageId"));
                                peparam.put("ELEMENT_ID", elementMap.get("elementId"));
                                List<Map> packEleInfo = n25Dao.queryPackageElementInfo(peparam);
                                if (!IsEmptyUtils.isEmpty(packEleInfo)) {
                                    packageElementInfo.addAll(packEleInfo);
                                } else {
                                    throw new EcAopServerBizException(ERROR_CODE, "订购的元素[" + elementMap.get("elementId") + "]在产品[" + bookProductId + "]下未查询到,请重试");
                                }
                            }
                            if (!IsEmptyUtils.isEmpty(packageElementInfo)) {
                                // 拼装附加包的属性信息
                                allProductInfo.addAll(packageElementInfo);
                            }
                        }

                        if (!IsEmptyUtils.isEmpty(allProductInfo)) {
                            preProductInfo(allProductInfo, discntList, svcList, spList, "0", isFinalCode, productStartDate, productEndDate, msg);
                        }
                        // 此时的资费、服务和sp节点中都还只有产品,包和元素 ,已经全处理了
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
                    }
                    if (CHANGE.equals(optType)) { // 产品内变更
                        // 4-处理产品内变更的元素
                        newProductId = productIdMsg;
                        // 如果有附加包
                        if (!IsEmptyUtils.isEmpty(packageElement)) {
                            List<String> deleteEle = new ArrayList<String>();
                            List<Map> addElementInfo = new ArrayList<Map>();
                            for (Map elementMap : packageElement) {
                                String modType = (String) elementMap.get("modType");
                                if ("1".equals(modType)) { // 退订
                                    deleteEle.add((String) elementMap.get("elementId"));
                                } else if ("0".equals(modType)) {// 订购
                                    Map peparam = new HashMap();
                                    peparam.put("PROVINCE_CODE", provinceCode);
                                    peparam.put("PRODUCT_ID", newProductId);
                                    peparam.put("EPARCHY_CODE", eparchyCode);
                                    peparam.put("PACKAGE_ID", elementMap.get("packageId"));
                                    peparam.put("ELEMENT_ID", elementMap.get("elementId"));
                                    List<Map> packageElementInfo = n25Dao.queryPackageElementInfo(peparam);
                                    if (!IsEmptyUtils.isEmpty(packageElementInfo)) {
                                        addElementInfo.addAll(packageElementInfo);
                                    } else {
                                        throw new EcAopServerBizException(ERROR_CODE, "订购的元素[" + elementMap.get("elementId") + "]在产品[" + newProductId + "]下未查询到,请重试");
                                    }
                                }
                            }
                            // 处理退订的资费
                            preDeleteInfo(deleteEle, discntList, svcList, spList, userInfo, msg);
                            // 处理订购的资费
                            String modifyTag = "0";
                            String isFinalCode = "N";// 订购的元素默认都要计算偏移
                            preProductInfo(addElementInfo, discntList, svcList, spList, modifyTag, isFinalCode, acceptDate, THE_END_DATE, msg);
                        }
                    }

                }
            }

            // 处理宽带主资费
            List<Map> boradDiscntInfos = (List<Map>) userInfoMsg.get("boradDiscntInfo");
            if (!IsEmptyUtils.isEmpty(boradDiscntInfos)) {
                for (Map map : boradDiscntInfos) {
                    boradDiscntId = (String) map.get("boradDiscntId");
                    if (StringUtils.isEmpty(boradDiscntId)) {
                        throw new EcAopServerBizException("9999", "选择的主资费编码boradDiscntId为空，请检查");
                    }
                    // sql中的参数是ELEMENT_ID 但是Esql框架会对参数处理,若根据sql参数取不到值,则会将参数转为驼峰型,再取值
                    msg.put("elementId", boradDiscntId);
                    msg.put("PROVINCE_CODE", provinceCode);
                    msg.put("EPARCHY_CODE", ChangeCodeUtils.changeEparchy(msg));
                    // 改成调用去重的方法
                    discntList.addAll(TradeManagerUtils.preDistProductInfoByElementIdNew(msg, productInfoList));
                }
            }

            // 去重
            discntList = NewProductUtils.newDealRepeat(discntList, new String[] { "productId", "packageId", "discntCode", "modifyTag" });
            svcList = NewProductUtils.newDealRepeat(svcList, new String[] { "productId", "packageId", "serviceId", "modifyTag" });
            spList = NewProductUtils.newDealRepeat(spList, new String[] { "productId", "packageId", "spServiceId", "modifyTag" });
            productTypeList = NewProductUtils.newDealRepeat(productTypeList, new String[] { "productId", "productMode", "productTypeCode", "modifyTag" });
            productList = NewProductUtils.newDealRepeat(productList, new String[] { "productId", "productMode", "brandCode", "modifyTag" });
            tradeOtherList = NewProductUtils.newDealRepeat(tradeOtherList, new String[] { "rsrvStr1", "rsrvStr2", "rsrvStr4", "rsrvStr9", "modifyTag" });
            ext.put("tradeUser", preDataUtil(tradeUserList));
            ext.put("tradeProductType", preDataUtil(productTypeList));
            ext.put("tradeProduct", preDataUtil(productList));
            ext.put("tradeDiscnt", preDataUtil(discntList));
            ext.put("tradeSvc", preDataUtil(svcList));
            ext.put("tradeSp", preDataUtil(spList));

            //判断是否为同移
            if(!IsEmptyUtils.isEmpty(brandNumber)){
                //宽带同移,添加新的item节点
                //区号
                String district = (String) msg.get("district");
                String areaCode = (String) msg.get("eparchyCode");
                Map tradeOtherMobile = new HashMap();
                tradeOtherMobile.put("xDatatype", "NULL");
                tradeOtherMobile.put("rsrvValueCode","CLOR");
                tradeOtherMobile.put("rsrvValue",userId);
                tradeOtherMobile.put("modifyTag","0");
                tradeOtherMobile.put("rsrvStr1","track");
                tradeOtherMobile.put("rsrvStr2","1");
                tradeOtherMobile.put("rsrvStr3","关联定单撤单，全撤");
                tradeOtherMobile.put("rsrvStr4","30");
                tradeOtherMobile.put("rsrvStr5","2");
                tradeOtherMobile.put("rsrvStr6",areaCode);
                tradeOtherMobile.put("rsrvStr7","DECIDE_IOM");
                tradeOtherMobile.put("rsrvStr8","true");
                tradeOtherMobile.put("rsrvStr9","");
                tradeOtherMobile.put("rsrvStr11","Y");
                tradeOtherMobile.put("rsrvStr12","1");
                tradeOtherMobile.put("rsrvStr13",serialNumber);
                tradeOtherMobile.put("rsrvStr14","0");
                tradeOtherMobile.put("rsrvStr15","19a0rq");
                tradeOtherMobile.put("rsrvStr20","269");
                tradeOtherMobile.put("rsrvStr21",userId);
                tradeOtherMobile.put("rsrvStr22","0");
                tradeOtherMobile.put("rsrvStr23",district);

                Map tradeOtherBand = new HashMap();
                tradeOtherBand.put("xDatatype", "NULL");
                tradeOtherBand.put("rsrvValueCode","CLOR");
                tradeOtherBand.put("rsrvValue",userId);
                tradeOtherBand.put("modifyTag","0");
                tradeOtherBand.put("rsrvStr1","track");
                tradeOtherBand.put("rsrvStr2","1");
                tradeOtherBand.put("rsrvStr3","关联定单撤单，全撤");
                tradeOtherBand.put("rsrvStr4","40");
                tradeOtherBand.put("rsrvStr5","2");
                tradeOtherBand.put("rsrvStr6",areaCode);
                tradeOtherBand.put("rsrvStr7","DECIDE_IOM");
                tradeOtherBand.put("rsrvStr8","true");
                tradeOtherBand.put("rsrvStr9","");
                tradeOtherBand.put("rsrvStr11","Y");
                tradeOtherBand.put("rsrvStr12","1");
                tradeOtherBand.put("rsrvStr13",brandNumber);
                tradeOtherBand.put("rsrvStr14","0");
                tradeOtherBand.put("rsrvStr15","19a0rq");
                tradeOtherBand.put("rsrvStr20","269");
                tradeOtherBand.put("rsrvStr21",userId);
                tradeOtherBand.put("rsrvStr22","0");
                tradeOtherBand.put("rsrvStr23",district);
                tradeOtherList.add(tradeOtherMobile);
                tradeOtherList.add(tradeOtherBand);
            }

            ext.put("tradeOther", preDataUtil(tradeOtherList));

            // 准备base参数
            msg.put("base", preBaseData(msg, threePartMap, exchange.getAppCode(), brandCode, newProductId));
            // 准备ext参数
            ext.put("tradeItem", preTradeItem(msg));
            ext.put("tradeSubItem", preTradeSubItem(msg));
            ext.put("tradeUser", preTradeUser(msg));
            msg.put("ext", ext);
            msg.put("operTypeCode", "0");
        }

        return msg;
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
    private void preProductInfo(List<Map> productInfoResult, List<Map> discntList, List<Map> svcList, List<Map> spList, String modifyTag, String isFinalCode, String startDate,
            String endDate, Map msg) {
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
                if (("00".equals(productMode) && "0".equals(modifyTag)) || StringUtils.isNotEmpty(isFinalCode) && !"Y".equals(isFinalCode)) {
                    Map discntDateMap = NewProductUtils.getMainProDiscntEffectTime4ChangePro(elementId, startDate, endDate);
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
                    throw new EcAopServerBizException("9999", "在SP表中未查询到【" + productInfoResult.get(j).get("ELEMENT_ID") + "】的元素属性信息");
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
                sp.put("userIdA", "-1");
                sp.put("xDatatype", "NULL");
                spList.add(sp);
            }
        }
    }

    /**
     * 
     * @param 处理返回
     */
    private void dealReturn(Exchange exchange) {
        Map out = exchange.getOut().getBody(Map.class);
        List<Map> rspInfo = (List) out.get("rspInfo");
        if (null == rspInfo || rspInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "cBSS统一预提交未返回应答信息");
        }
        Map realOut = new HashMap();
        realOut.put("provOrderId", (String) rspInfo.get(0).get("provOrderId"));
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
        realOut.put("feeInfo", feeList);
        realOut.put("totalFee", totalFee + "");
        exchange.getOut().setBody(realOut);
    }

    /**
     * 拼装base节点
     * 
     */
    private Map preBaseData(Map msg, Map<String, Object> threePartMap, String appCode, String brandCode, String newProductId) {
        try {
            Map base = new HashMap();
            base.putAll((Map) threePartMap.get("userInfo"));
            base.putAll((Map) threePartMap.get("custInfo"));
            base.putAll((Map) threePartMap.get("acctInfo"));
            base.put("areaCode", msg.get("eparchyCode"));
            base.put("tradeCityCode", msg.get("district"));
            base.put("subscribeId", msg.get("tradeId"));
            base.put("tradeLcuName", "TCS_ChangeResourceReg");
            base.put("infoTag", "11111100001000000000100000");
            base.put("inModeCode", new ChangeCodeUtils().getInModeCode(appCode));
            base.put("tradeId", msg.get("tradeId"));
            base.put("tradeTypeCode", "0269");
            base.put("execTime", msg.get("startDate"));
            base.put("nextDealTag", "Z");
            base.put("olcomTag", "0");
            base.put("foregift", "0");
            base.put("productId", newProductId);
            base.put("rightCode", "csBuysimcardHKTrade");
            base.put("chkTag", "0");
            base.put("cancelTag", "0");
            base.put("operFee", "0");
            base.put("brandCode", brandCode);
            base.put("tradeTagSet", "00120000000000000000");
            base.put("blackUserTag", "0");
            base.put("netTypeCode", msg.get("netTypeCode"));
            base.put("tradeDepartId", msg.get("channelId"));
            base.put("acceptDate", msg.get("startDate"));
            base.put("acceptMonth", RDate.currentTimeStr("MM"));
            base.put("startAcycId", RDate.currentTimeStr("yyyyMM"));
            base.put("termIp", RDate.currentTimeStr("127.0.0.1"));
            base.put("tradeJudgeOweTag", "2");
            base.put("tradeStatus", "2");
            base.put("attrTypeCode", "0");
            base.put("tradeAttr", "1");
            base.put("tradeStaffId", msg.get("operatorId"));
            base.put("serinalNamber", msg.get("serialNumber"));
            base.put("routeEparchy", ((Map) threePartMap.get("userInfo")).get("xEparchyCode"));
            return base;
        } catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装base节点报错");
        }
    }

    /**
     * 
     * @param 准备TRADE_SUB_ITEM
     * @return
     */
    private Map preTradeSubItem(Map msg) {
        Map tradeSubItem = new HashMap();
        List<Map> item = new ArrayList<Map>();
        Map userInfo = null;
        List<Map> userInfoMsg = (List<Map>) msg.get("userInfo");// 省份请求中的userInfo
        if (!IsEmptyUtils.isEmpty(userInfoMsg)) {
            userInfo = userInfoMsg.get(0);
        }
        String userId = (String) msg.get("userId");
        item.add(lan.createTradeSubItemBroad1("ACCEPT_DATE", msg.get("startDate"), userId, (String) msg.get("startDate")));
        item.add(lan.createTradeSubItemBroad1("ADDRESS_ID", userInfo.get("addressCode"), userId, (String) msg.get("startDate")));
        item.add(lan.createTradeSubItemBroad1("INSTALL_ADDRESS", userInfo.get("addressName"), userId, (String) msg.get("startDate")));
        // item.add(lan.createTradeSubItemBroad1("EXPECT_RATE", msg.get("expectRate"), userId, (String) msg.get("startDate")));// 期望速率
        item.add(lan.createTradeSubItemBroad1("DETAIL_INSTALL_ADDRESS", userInfo.get("installAddress"), userId, (String) msg.get("startDate")));// 详细地址
        item.add(lan.createTradeSubItemBroad1("ACCESS_TYPE", userInfo.get("accessMode"), userId, (String) msg.get("startDate")));// 接入方式
        item.add(lan.createTradeSubItemBroad1("CB_ACCESS_TYPE", userInfo.get("cbssAccessMode"), userId, (String) msg.get("startDate")));
        item.add(lan.createTradeSubItemBroad1("IS_GIS_ADDR", "0", userId, (String) msg.get("startDate")));
        item.add(lan.createTradeSubItemBroad1("AREA_CODE", msg.get("district"), userId, (String) msg.get("startDate")));// 服务区
        item.add(lan.createTradeSubItemBroad1("POINT_EXCH_ID", msg.get("city"), userId, (String) msg.get("startDate")));
        item.add(lan.createTradeSubItemBroad1("LOCAL_NET_CODE", msg.get("eparchyCode"), userId, (String) msg.get("startDate")));// 本地网编码
        item.add(lan.createTradeSubItemBroad1("ISWAIL", "0", userId, (String) msg.get("startDate")));
        item.add(lan.createTradeSubItemBroad1("AREA_EXCH_ID", "2", userId, (String) msg.get("startDate")));// 局向（交换区）
        item.add(lan.createTradeSubItemBroad1("MOFFICE_ID", msg.get("city"), userId, (String) msg.get("startDate")));
        // item.add(lan.createTradeSubItemBroad1("COMMUNIT_NAME", msg.get("community"), userId, (String) msg.get("startDate")));// 社区名称
        item.add(lan.createTradeSubItemBroad1("PREPAY_TAG", "0", userId, (String) msg.get("startDate")));
        item.add(lan.createTradeSubItemBroad1("TOWN_FLAG", "C", userId, (String) msg.get("startDate")));
        item.add(lan.createTradeSubItemBroad1("USETYPE", "0", userId, (String) msg.get("startDate")));
        item.add(lan.createTradeSubItemBroad1("TIME_LIMIT_ID", "2", userId, (String) msg.get("startDate")));
        item.add(lan.createTradeSubItemBroad1("SPEED", userInfo.get("speedLevel"), userId, (String) msg.get("startDate")));
        item.add(lan.createTradeSubItemBroad1("CONNECTNETMODE", "1", userId, (String) msg.get("startDate")));
        item.add(lan.createTradeSubItemBroad1("ISWOPAY", "0", userId, (String) msg.get("startDate")));
        item.add(lan.createTradeSubItemBroad1("KDLX_2061", "N", userId, (String) msg.get("startDate")));
        item.add(lan.createTradeSubItemBroad1("TERMINALSRC_MODE", "A0010", userId, (String) msg.get("startDate")));
        item.add(lan.createTradeSubItemBroad1("TERMINAL_TYPE", "2", userId, (String) msg.get("startDate")));
        item.add(lan.createTradeSubItemBroad1("USER_TYPE_CODE", "0", userId, (String) msg.get("startDate")));
        item.add(lan.createTradeSubItemB2(userId, "ischeckallmove", "0"));
        item.add(lan.createTradeSubItemB2(userId, "PHONE_VERIFIED", "0"));
        item.add(lan.createTradeSubItemB2(userId, "LINK_NAME", msg.get("contactPerson")));
        item.add(lan.createTradeSubItemB2(userId, "LINK_PHONE", msg.get("contactPhone")));
        item.add(lan.createTradeSubItemB2(userId, "SUB_TYPE", "0"));
        tradeSubItem.put("item", item);
        return tradeSubItem;
    }

    /**
     * @param 准备TRADE_ITEM
     */
    private Map preTradeItem(Map msg) {
        Map tradeItem = new HashMap();
        List<Map> item = new ArrayList<Map>();
        item.add(lan.createTradeItem("OLD_AREA_CODE", msg.get("district")));
        item.add(lan.createTradeItem("OLD_MOFFICE_ID", msg.get("city")));
        item.add(lan.createTradeItem("IsChangeSn", "IsChangeSn"));
        item.add(lan.createTradeItem("STANDARD_KIND_CODE", "0371"));// 比如0371
        item.add(lan.createTradeItem("GGGJCHLKEY", "5"));
        item.add(lan.createTradeItem("BOOK_FLAG", "0"));
        item.add(lan.createTradeItem("IS_ORDER_TRACK_UU", "18122230857254"));
        item.add(lan.createTradeItem("CANCELTRACK_REASON", ""));
        tradeItem.put("item", item);
        return tradeItem;
    }

    /**
     * tradeUser
     */
    private Map preTradeUser(Map msg) {
        Map tradeUser = new HashMap();
        List<Map> items = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("userId", msg.get("userId"));
        item.put("userTypeCode", "0");
        tradeUser.put("item", items);
        return tradeUser;
    }

    private String changeFenToLi(Object fee) {
        return null == fee || "".equals(fee + "") || "0".equals(fee + "") || "null".equals(fee + "") ? "0" : fee + "0";
    }

    private List<Map> dealFee(List<Map> feeList) {
        List<Map> retFeeList = new ArrayList<Map>();
        for (Map fee : feeList) {
            Map retFee = new HashMap();
            retFee.put("feeId", fee.get("feeTypeCode") + "");
            retFee.put("feeCategory", fee.get("feeMode") + "");
            retFee.put("feeDes", fee.get("feeTypeName") + "");
            if (null != fee.get("maxDerateFee"))
                retFee.put("maxRelief", changeFenToLi(fee.get("maxDerateFee")));// 非必返maxDerateFee
            retFee.put("origFee", changeFenToLi(fee.get("fee")));
            retFeeList.add(retFee);
        }
        return retFeeList;
    }

    /**
     * 拼装product和productType节点
     * 
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
     * 处理退订的资费
     * 
     * @param deleteEle
     * @param discntList
     * @param svcList
     * @param spList
     * @param userInfo
     * @throws Exception
     */
    private void preDeleteInfo(List<String> deleteEle, List<Map> discntList, List<Map> svcList, List<Map> spList, Map userInfo, Map msg) throws Exception {
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
                        } else if ("2".equals(endmode)) { // 2的时候取当天23:59:59
                            endDate = GetDateUtils.getSpecifyDateTime(1, 1, acceptDate, 1);
                            endDate = GetDateUtils.getSpecifyDateTime(1, 6, endDate, -1);
                        }
                        discntItem.put("PRODUCT_ID", dis.get("productId"));
                        discntItem.put("PACKAGE_ID", dis.get("packageId"));
                        discntItem.put("ELEMENT_ID", dis.get("discntCode"));
                        discntItem.put("ELEMENT_TYPE_CODE", "D");
                        List<Map> elementInfo = new ArrayList<Map>();
                        elementInfo.add(discntItem);
                        preProductInfo(elementInfo, discntList, svcList, spList, modifyTag, isFinalCode, GetDateUtils.transDate("" + dis.get("startDate"), 19), endDate, msg);
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
                        preProductInfo(elementInfo, discntList, svcList, spList, modifyTag, isFinalCode, (String) svc.get("startDate"), acceptDate, msg); // 退订服务是立即生效
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
                        preProductInfo(elementInfo, discntList, svcList, spList, modifyTag, isFinalCode, GetDateUtils.transDate((String) sp.get("startDate"), 19), endDate, msg);
                        break;
                    }
                }
            }
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
