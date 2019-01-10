package com.ailk.ecaop.biz.product;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.NewProductUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.alibaba.fastjson.JSON;

@EcRocTag("changeProduct4GReplaceN6")
public class ChangeProduct4GReplaceN6Processor extends BaseAopProcessor implements ParamsAppliable {

    LanUtils lan = new LanUtils();
    LanOpenApp4GDao dao = new LanOpenApp4GDao();
    DealNewCbssProduct n25Dao = new DealNewCbssProduct();
    private static final String[] PARAM_ARRAY = { "ecaop.trade.cbss.checkUserParametersMapping",
            "ecaop.param.mapping.sfck", "ecaop.masb.chph.gifa.ParametersMapping",
            "ecaop.trades.sccc.cancelPre.paramtersmapping" };
    // 31省IN_MODE_CODE赋值公用数组
    private static ArrayList<String> modecodes = new ArrayList<String>(Arrays.asList("nmpr", "bjsb", "tjpr", "sdpr",
            "hpsb", "sxpr", "ahpre", "shpre", "jspr", "zjpre", "fjpre", "hipre", "gdps", "ussb", "qhpr", "hupr",
            "hnpr", "jxpre", "hapr", "xzpre", "scpr", "cqps", "snpre", "gzpre", "ynpre", "gspr", "nxpr", "xjpr",
            "jlpr", "mppln", "hlpr"));
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[4];

    private static String END_OF_WORLD = "2050-12-31 23:59:59";

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = body.get("msg") instanceof String ? JSON.parseObject((String) body.get("msg")) : (Map) body
                .get("msg");

        //查三户
        Map threePartMsg = checkThreePartMsg(exchange, msg);

        //校验员工信息
        checkOperatorMsg(exchange, body, msg);

        //处理产品逻辑
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        dealProduct(exchange, msg, threePartMsg);
        System.out.println("abdsfsdchange_product_msg:" + msg);
        // 调预提交接口
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        lan.preData(pmp[3], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.trades.mmvc.sUniTrade.template", exchange);

        //处理返回
        dealReturn(exchange, msg);
    }

    /**
     *  校验并获取三户返回的结果
     * @throws Exception 
     * */
    @SuppressWarnings("rawtypes")
    public Map checkThreePartMsg(Exchange exchange, Map msg) throws Exception {
        String[] copyArray = { "operatorId", "province", "channelId", "city", "district", "channelType" };
        Map threePartMap = MapUtils.asMap("getMode", "1111111111100013110000000100001", "serialNumber",
                msg.get("serialNumber"), "tradeTypeCode", "9999");
        MapUtils.arrayPut(threePartMap, msg, copyArray);
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
        lan.preData(pmp[0], threePartExchange);//ecaop.trade.cbss.checkUserParametersMapping
        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);// 报下游返回格式有误--已改
        return dealThreePartReturn(threePartExchange, msg);
    }

    /**
     *  校验并获取三户返回的结果
     * */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Map dealThreePartReturn(Exchange exchange, Map msg) throws Exception {
        Map out = exchange.getOut().getBody(Map.class);
        List<Map> userInfoList = (List<Map>) out.get("userInfo");
        List<Map> custInfoList = (List<Map>) out.get("custInfo");
        List<Map> acctInfoList = (List<Map>) out.get("acctInfo");
        if (null == custInfoList || custInfoList.isEmpty())
        {
            throw new EcAopServerBizException("9999", "调三户未返回客户信息。");
        }
        if (null == userInfoList || userInfoList.isEmpty())
        {
            throw new EcAopServerBizException("9999", "调三户未返回用户信息。");
        }
        if (null == acctInfoList || acctInfoList.isEmpty())
        {
            throw new EcAopServerBizException("9999", "调三户未返回帐户信息。");
        }
        List<Map> userProductList = (List<Map>) userInfoList.get(0).get("productInfo");
        if (null == userProductList || userProductList.isEmpty())
        {
            throw new EcAopServerBizException("9999", "调三户未返回产品信息。");
        }
        Map temp = new HashMap();
        temp.put("acctInfo", acctInfoList.get(0));
        temp.put("userInfo", userInfoList.get(0));
        temp.put("custInfo", custInfoList.get(0));
        return temp;
    }

    /**
     *  校验并获取员工信息
     * @throws Exception 
     * */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void checkOperatorMsg(Exchange exchange, Map body, Map msg) throws Exception {
        msg.put("checkMode", "1");
        msg.put("appCode", exchange.getAppCode());
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        lan.preData(pmp[1], exchange);
        CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
        lan.xml2Json4ONS("ecaop.template.3g.staffcheck", exchange);
        Map retStaffinfo = exchange.getOut().getBody(Map.class);
        if (null == retStaffinfo || retStaffinfo.isEmpty())
        {
            throw new EcAopServerBizException("9999", "员工校验未返回departId。");
        }
        msg.put("departId", retStaffinfo.get("departId"));
    }

    /**
     *  获取CB流水号
     * @throws Exception 
     * */
    /*@SuppressWarnings({ "rawtypes" })
    private String getCBTradeId(Exchange exchange, Map msg) throws Exception {
        String tradeId;
        try
        {
            tradeId = GetSeqUtil.getSeqFromCb(pmp[2], exchange, "seq_trade_id", 1).get(0).toString();//ecaop.masb.chph.gifa.ParametersMapping
        }
        catch (Exception e)
        {
            throw new EcAopServerBizException("9999", "获取CB侧流水失败" + e.getMessage());
        }
        return tradeId;
    }*/

    /**
     * 产品处理
     * @throws Exception 
     * */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void dealProduct(Exchange exchange, Map msg, Map threePartMsg) throws Exception {
        String provinceCode = "00" + msg.get("province");// 新库也要加00
        String serialNumber = "" + msg.get("serialNumber");
        //String orderId = "" + msg.get("ordersId");
        String tradeStaffId = "" + msg.get("operatorId");
        Map acctInfo = (Map) threePartMsg.get("acctInfo");
        Map userInfo = (Map) threePartMsg.get("userInfo");
        Map custInfo = (Map) threePartMsg.get("custInfo");
        List<Map> oldProducts = (List<Map>) userInfo.get("productInfo");
        Map extObject = new HashMap();
        //获取CB流水号
        //String tradeId = getCBTradeId(exchange, msg);

        String departId = "" + msg.get("departId");
        //获取yyyy-MM-dd HH:mm:ss格式当前时间
        String acceptDate = GetDateUtils.transDate(GetDateUtils.getDate(), 19);
        //下月第一天格式yyyyMMddHHmmss
        String nextMon1stDay = GetDateUtils.getNextMonthFirstDayFormat();
        String userId = "" + userInfo.get("userId");
        String custId = "" + custInfo.get("custId");
        String acctId = "" + acctInfo.get("acctId");
        String useCustId = "" + userInfo.get("usecustId");

        String brandCode = "" + userInfo.get("brandCode");
        String userDiffCode = "" + userInfo.get("userDiffCode");
        String custName = "" + custInfo.get("custName");
        String linkAddress = "" + custInfo.get("certAddr");
        String linkPhone = "" + custInfo.get("contactPhone");
        //地市从三户中取的
        String eparchyCode = "" + userInfo.get("eparchyCode");
        String cityCode = "" + userInfo.get("cityCode");
        String newProductId = "" + userInfo.get("productId");

        List<Map> productInfos = (List<Map>) msg.get("productInfo");
        if (null != productInfos && productInfos.size() > 0)
        {
            for (int i = 0; i < productInfos.size(); i++)
            {
                String lengthType = "N";
                Map productInfo = productInfos.get(i);
                String strBrandCode = "";
                String strProductTypeCode = "";
                String strProductMode = "";
                String productId = "";
                String commodityId = "";
                //01时表示首月按量计费（包外标准资费），02时表示首月全量全价，03表示首月套餐减半
                String firstMonBillMode = "02";
                //开户里的String firstMonBillMode = null == userInfo.get("firstMonBillMode") ? "02" : (String) userInfo
                //        .get("firstMonBillMode");
                String optType = "" + productInfo.get("optType");
                String monthFirstDay = "";
                String monthLasttDay = "";
                boolean isMainPro = false;
                if ("1".equals(productInfo.get("productMode")))//0：可选产品；1：主产品
                {
                    isMainPro = true;
                }
                else if (!"0".equals(productInfo.get("productMode")))
                {
                    throw new EcAopServerBizException("9999", "productMode只能为0或1。");
                }
                if ("01".equals(optType)) // 退订产品
                {
                    // --------------------------------------产品信息--------------------------------------
                    List<Map> productInfoSet = null;
                    commodityId = "" + productInfo.get("productId"); // 用老的产品id
                    if (isMainPro)
                    {
                        /*Map quryMap_1 = new HashMap();
                        quryMap_1.put("PROVINCE_CODE", provinceCode);
                        quryMap_1.put("COMMODITY_ID", commodityId);
                        quryMap_1.put("EPARCHY_CODE", eparchyCode);
                        quryMap_1.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                        List<Map> subProductInfoSet = dao.queryProductInfo(quryMap_1);
                        A.COMMODITY_ID,
                        A.COMMODITY_TYPE,
                        A.PRODUCT_MODE,******
                        A.PRODUCT_ID,******
                        A.PACKAGE_ID,******
                        A.ELEMENT_TYPE_CODE,******
                        A.ELEMENT_ID,******
                        A.START_DATE,
                        A.END_DATE,
                        A.REMARK,
                        A.PROVINCE_CODE,
                        A.EFFECTIVE_MODE,
                        A.EFFECTIVE_EXCURSION,
                        A.FAIL_MODE,
                        A.FAIL_EXCURSION
                        */

                        Map proparam = new HashMap();
                        proparam.put("PROVINCE_CODE", provinceCode);
                        proparam.put("PRODUCT_ID", commodityId);
                        proparam.put("EPARCHY_CODE", eparchyCode);
                        proparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                        proparam.put("PRODUCT_MODE", "00");
                        productInfoSet = n25Dao.qryDefaultPackageElement(proparam);

                        //productInfoSet = qryDefaultPackageElement(provinceCode, commodityId, eparchyCode,
                        //        firstMonBillMode, "00");
                        //仅用于278行一行
                        /**
                         * A.PRODUCT_ID,******
                         * A.BRAND_CODE,
                         * A.NET_TYPE_CODE,
                         * A.PRODUCT_MODE,******
                         * B.PRODUCT_TYPE_CODE,
                         * D.PACKAGE_ID,******
                         * E.ELEMENT_TYPE_CODE,******
                         * E.ELEMENT_ID******
                         * */

                        if (productInfoSet == null || productInfoSet.size() == 0)
                        {
                            throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId
                                    + "】的产品信息qryDefault");
                        }
                    }
                    else
                    {
                        /*Map quryMap_1 = new HashMap();
                        quryMap_1.put("PROVINCE_CODE", provinceCode);
                        quryMap_1.put("COMMODITY_ID", commodityId);
                        quryMap_1.put("EPARCHY_CODE", eparchyCode);
                        quryMap_1.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                        productInfoSet = dao.queryProductInfoWithLimit(quryMap_1);//SQL与之前比多了一个A.DEFAULT_TAG = '0'的条件
                        */
                        /* 开户里的Map addproparam = new HashMap();
                         addproparam.put("PROVINCE_CODE", provinceCode);
                         addproparam.put("PRODUCT_ID", commodityId);
                         addproparam.put("EPARCHY_CODE", eparchyCode);
                         addproparam.put("PRODUCT_MODE", "01");
                         addproparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                         List<Map> addproductInfoResult = n25Dao.qryDefaultPackageElement(addproparam);*/
                        productInfoSet = queryProductInfoWithLimit(provinceCode, commodityId, eparchyCode,
                                firstMonBillMode, "01");
                        //仅取PRODUCT_ID和PRODUCT_MODE及判空
                        if (productInfoSet == null || productInfoSet.size() == 0)
                        {
                            throw new EcAopServerBizException("9999", "在产品映射表中未查询到附加产品ID【" + commodityId
                                    + "】的产品信息WithLimit");
                        }
                    }

                    String subId = String.valueOf(((Map) productInfoSet.get(0)).get("PRODUCT_ID"));
                    String subProductMode = String.valueOf(((Map) productInfoSet.get(0)).get("PRODUCT_MODE"));
                    /*Map quryMap_2 = new HashMap();
                    quryMap_2.put("PROVINCE_CODE", provinceCode);
                    quryMap_2.put("COMMODITY_ID", commodityId);
                    quryMap_2.put("PRODUCT_ID", subId);
                    List<Map> subproductItemSet = dao.queryProductItemInfoNoPtype(quryMap_2);*/
                    /* 开户里的Map itparam = new HashMap();
                     itparam.put("PROVINCE_CODE", provinceCode);
                     itparam.put("PRODUCT_ID", productId);
                     List<Map> productItemInfoResult = n25Dao.queryProductAndPtypeProduct(itparam);*/
                    List<Map> subproductItemSet = queryProductItemInfoNoPtype(subId);//queryProductItemInfoNoPtype(provinceCode, commodityId, subId);
                    //仅取PRODUCT_TYPE_CODE和BRAND_CODE及判空
                    if (subproductItemSet == null || subproductItemSet.size() == 0)
                    {
                        throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品属性表【" + commodityId + "】的产品信息NoPtype");
                    }
                    String subProductTypeCode = String.valueOf(subproductItemSet.get(0).get("PRODUCT_TYPE_CODE"));//getValueFromItem("PRODUCT_TYPE_CODE", subproductItemSet); //
                    String subBrandCode = String.valueOf(subproductItemSet.get(0).get("BRAND_CODE"));//getValueFromItem("BRAND_CODE", subproductItemSet); //

                    monthFirstDay = acceptDate;
                    brandCode = subBrandCode;
                    monthLasttDay = GetDateUtils.getMonthLastDay();

                    // 取原产品的生效时间
                    for (int m = 0; m < oldProducts.size(); m++)
                    {
                        Map pInfo = oldProducts.get(m);
                        if (subId.equals(pInfo.get("productId")))
                        {
                            monthFirstDay = "" + pInfo.get("productActiveTime");
                            monthFirstDay = GetDateUtils.transDate(monthFirstDay, 19);
                        }
                    }
                    // 附属产品台帐
                    Map subProductTemp = new HashMap();
                    subProductTemp.put("userId", userId);
                    subProductTemp.put("productMode", subProductMode);
                    subProductTemp.put("productId", subId);
                    subProductTemp.put("brandCode", subBrandCode);
                    //先记为"need"后面统一从CB获取
                    subProductTemp.put("itemId", "need");
                    subProductTemp.put("modifyTag", "1");
                    subProductTemp.put("startDate", monthFirstDay);
                    subProductTemp.put("endDate", monthLasttDay);
                    subProductTemp.put("userIdA", "-1");
                    subProductTemp.put("xDatatype", "NULL");
                    putItemsForExtByName("tradeProduct", extObject, subProductTemp);
                    //tradeProductList.add(subProductTemp);
                    // 活动台帐
                    Map productTypeTemp = new HashMap();
                    productTypeTemp.put("userId", userId);
                    productTypeTemp.put("productMode", subProductMode);
                    productTypeTemp.put("productId", subId);
                    productTypeTemp.put("productTypeCode", subProductTypeCode);
                    productTypeTemp.put("modifyTag", "1");
                    productTypeTemp.put("startDate", monthFirstDay);
                    productTypeTemp.put("endDate", monthLasttDay);
                    productTypeTemp.put("xDatatype", "NULL");
                    putItemsForExtByName("tradeProductType", extObject, productTypeTemp);
                    //tradeProductTypeList.add(productTypeTemp);

                    // TRADE_OTHER
                    Map tradeOtherTemp = new HashMap();
                    tradeOtherTemp.put("modifyTag", "1");
                    tradeOtherTemp.put("rsrvStr1", subId);
                    tradeOtherTemp.put("rsrvStr2", subProductMode);
                    tradeOtherTemp.put("rsrvStr3", "-9");
                    tradeOtherTemp.put("rsrvStr4", subProductTypeCode);
                    tradeOtherTemp.put("rsrvStr5", subProductTypeCode);
                    tradeOtherTemp.put("rsrvStr6", "-1");
                    tradeOtherTemp.put("rsrvStr7", "0");
                    tradeOtherTemp.put("rsrvStr8", "");
                    tradeOtherTemp.put("rsrvStr9", subBrandCode);// BRAND code
                    tradeOtherTemp.put("rsrvStr10", serialNumber);// 号码
                    tradeOtherTemp.put("rsrvStr11", "");
                    tradeOtherTemp.put("rsrvValueCode", "NEXP");
                    tradeOtherTemp.put("rsrvValue", userId); // USER_ID
                    tradeOtherTemp.put("xDatatype", "NULL");
                    tradeOtherTemp.put("startDate", monthFirstDay);
                    tradeOtherTemp.put("endDate", monthLasttDay);
                    putItemsForExtByName("tradeOther", extObject, tradeOtherTemp);
                    //tradeOtherList.add(tradeOtherTemp);
                }
                else if ("00".equals(optType)) // 新增产品
                {
                    String startDate = nextMon1stDay;
                    commodityId = "" + productInfo.get("productId");
                    List<Map> productInfoSet = null;
                    if (isMainPro)
                    {
                        startDate = nextMon1stDay; // 主产品下月生效
                        monthLasttDay = END_OF_WORLD;
                        /*Map quryMap_1 = new HashMap();
                        quryMap_1.put("PROVINCE_CODE", provinceCode);
                        quryMap_1.put("COMMODITY_ID", commodityId);
                        quryMap_1.put("EPARCHY_CODE", eparchyCode);
                        quryMap_1.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                        productInfoSet = dao.queryProductInfo(quryMap_1);*/
                        /*Map proparam = new HashMap();
                        proparam.put("PROVINCE_CODE", provinceCode);
                        proparam.put("PRODUCT_ID", commodityId);
                        proparam.put("EPARCHY_CODE", eparchyCode);
                        proparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                        proparam.put("PRODUCT_MODE", "00");
                        productInfoSet = n25Dao.qryDefaultPackageElement(proparam);*/
                        productInfoSet = qryDefaultPackageElement(provinceCode, commodityId, eparchyCode,
                                firstMonBillMode, "00");
                        //PRODUCT_MODE和PRODUCT_ID及判空,有后续处理
                        if (productInfoSet == null || productInfoSet.size() == 0)
                        {
                            throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品信息");
                        }
                        newProductId = commodityId;
                        strProductMode = String.valueOf(((Map) productInfoSet.get(0)).get("PRODUCT_MODE"));
                        productId = String.valueOf(((Map) productInfoSet.get(0)).get("PRODUCT_ID"));
                    }
                    else
                    {
                        startDate = acceptDate; // 附加产品立即效
                        monthLasttDay = END_OF_WORLD;
                        //dao.queryAddProductEndDate不作改变
                        //附加产品时查lengthType
                        lengthType = TradeManagerUtils.getEndDate(commodityId);

                        /*List<Map> endDateFlagSet = dao
                                .queryAddProductEndDate(MapUtils.asMap("PRODUCT_ID", commodityId));
                        if (endDateFlagSet == null || endDateFlagSet.size() == 0)
                        {
                            lengthType = "X";
                        }
                        else
                        {
                            lengthType = String.valueOf(((Map) endDateFlagSet.get(0)).get("LENGTH_TYPE"));
                        }*/
                        if ("N,X".contains(lengthType))
                        {
                            //List<Map> ProductInfoSet = dao.queryProductAndPtypeProduct(MapUtils.asMap("PRODUCT_ID",
                            //        commodityId));
                            List<Map> ProductInfoSet = queryProductAndPtypeProduct(commodityId);
                            //判空,有后续处理
                            /*A.PRODUCT_ID,
                            A.PRODUCT_MODE,
                            A.NET_TYPE_CODE,
                            A.END_UNIT,*******
                            A.BRAND_CODE,
                            A.PREPAY_TAG,
                            A.END_ABSOLUTE_DATE,
                            A.END_ENABLE_TAG,*******
                            A.END_OFFSET,*********
                            B.PRODUCT_TYPE_CODE,
                            A.START_UNIT,******
                            A.ENABLE_TAG,**********
                            A.BRAND_CODE,
                            A.START_OFFSET,********
                            A.SERVICE_ID*/
                            if (ProductInfoSet == null || ProductInfoSet.size() == 0)
                            {
                                throw new EcAopServerBizException("9999", "在产品表或者产品属性表中未查询到产品ID【" + commodityId
                                        + "】的产品信息");
                            }
                            //Map detailProduct = (Map) ProductInfoSet.get(0);
                            countStartDateAndEndDate((Map) ProductInfoSet.get(0), startDate, acceptDate, monthLasttDay,
                                    END_OF_WORLD);
                            /*String endOffSet = String.valueOf(detailProduct.get("END_OFFSET"));
                            String enableTag = (String) (detailProduct.get("ENABLE_TAG"));// 0-绝对时间（看开始时间） 1-相对时间
                            String endEnableTag = (String) (detailProduct.get("END_ENABLE_TAG"));// 0-绝对时间（看开始时间） 1-相对时间
                            String strStartUnit = (String) (detailProduct.get("START_UNIT"));// '0:天 1:自然天 2:月 3:自然月 4:年
                                                                                             // 5:自然年';
                            String endUnit = (String) (null == detailProduct.get("END_UNIT") ? "0" : detailProduct
                                    .get("END_UNIT"));
                            String startOffset = String.valueOf(detailProduct.get("START_OFFSET"));// 生效偏移时间
                            if (StringUtils.isEmpty(enableTag) || "null".equals(startOffset)
                                    || StringUtils.isEmpty(startOffset) || "null".equals(strStartUnit)
                                    || StringUtils.isEmpty(strStartUnit))
                            {
                                startDate = acceptDate;
                            }
                            else
                            {
                                startDate = GetDateUtils.getSpecifyDateTime(Integer.parseInt(enableTag),
                                        Integer.parseInt(strStartUnit), acceptDate, Integer.parseInt(startOffset));
                            }

                            if (StringUtils.isEmpty(enableTag) || "0".equals(enableTag)
                                    || StringUtils.isEmpty(strStartUnit) || "0".equals(strStartUnit)
                                    || "null".equals(startOffset) || StringUtils.isEmpty(startOffset))
                            {//避免下面startOffset转换报错
                                startOffset = "0";
                            }

                            if (StringUtils.isEmpty(endEnableTag) || StringUtils.isEmpty(endOffSet)
                                    || "null".equals(endOffSet) || StringUtils.isEmpty(endUnit)
                                    || "null".equals(endUnit) || "0".equals(endEnableTag))
                            {
                                monthLasttDay = END_OF_WORLD;
                            }
                            else
                            {
                                monthLasttDay = GetDateUtils.getSpecifyDateTime(Integer.parseInt(endEnableTag),
                                        Integer.parseInt(endUnit), acceptDate,
                                        Integer.parseInt(endOffSet) + Integer.parseInt(startOffset));
                                monthLasttDay = GetDateUtils.getSpecifyDateTime(1, 6, monthLasttDay, -1); // 结束月最后一天
                            }*/
                            // 结束
                        }
                        /*Map quryMap_1 = new HashMap();
                        quryMap_1.put("PROVINCE_CODE", provinceCode);
                        quryMap_1.put("COMMODITY_ID", commodityId);
                        quryMap_1.put("EPARCHY_CODE", eparchyCode);
                        quryMap_1.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                        productInfoSet = dao.queryProductInfoWithLimit(quryMap_1);//SQL与之前比多了一个A.DEFAULT_TAG = '0'的条件
                        */
                        //(String provinceCode, String commodityId, String eparchyCode,
                         //       String firstMonBillMode, String productMode) 
                        
                        productInfoSet = queryProductInfoWithLimit(provinceCode, commodityId, eparchyCode,
                                firstMonBillMode, "01");

                        if (productInfoSet == null || productInfoSet.size() == 0)
                        {
                            throw new EcAopServerBizException("9999", "在产品映射表中未查询到附加产品ID【" + commodityId + "】的产品信息");
                        }
                        strProductMode = String.valueOf(((Map) productInfoSet.get(0)).get("PRODUCT_MODE"));
                        productId = String.valueOf(((Map) productInfoSet.get(0)).get("PRODUCT_ID"));

                        /*Map quryMap_2 = new HashMap();
                        quryMap_2.put("PROVINCE_CODE", provinceCode);
                        quryMap_2.put("COMMODITY_ID", commodityId);
                        quryMap_2.put("EPARCHY_CODE", eparchyCode);
                        quryMap_2.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                        productInfoSet = dao.queryProductInfo(quryMap_2);*/
                        /*Map addproparam = new HashMap();
                        addproparam.put("PROVINCE_CODE", provinceCode);
                        addproparam.put("PRODUCT_ID", commodityId);
                        addproparam.put("EPARCHY_CODE", eparchyCode);
                        addproparam.put("PRODUCT_MODE", "01");
                        addproparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                        productInfoSet = n25Dao.qryDefaultPackageElement(addproparam);*/
                        productInfoSet = qryDefaultPackageElement(provinceCode, commodityId, eparchyCode,
                                firstMonBillMode, "01");
                        //有后续处理
                    }
                    if (null != productInfoSet && productInfoSet.size() > 0)
                    {
                        // 100M 与42M选择
                        productInfoSet = chooseSpeedByUser(productInfoSet, (List<Map>) userInfo.get("svcInfo"));
                        // 预处理一部分产品信息
                        productInfoSet = preDealProductInfo(productInfoSet, productId, provinceCode, userInfo);
                        // 拼装产品默认属性
                        preDealProductItemInfo(exchange, msg, productInfoSet, eparchyCode, provinceCode, userId,
                                startDate, monthLasttDay, serialNumber, extObject, null);
                    }
                    // 获取Td_b_Commodity_Trans_Item产品属性信息
                    /*Map ItemParam = new HashMap();
                    ItemParam.put("PTYPE", "U");
                    ItemParam.put("COMMODITY_ID", commodityId);
                    ItemParam.put("PROVINCE_CODE", provinceCode);
                    ItemParam.put("PID", productId);
                    List<Map> productItemSet = dao.queryProductItemInfo(ItemParam);*/
                    List<Map> productItemSet = queryProductAndPtypeProduct(commodityId);
                    if (productItemSet == null || productItemSet.size() == 0)
                    {
                        throw new EcAopServerBizException("9999", "在产品映射属性表中未查询到产品ID【" + productId + "】的产品属性信息");
                    }
                    strBrandCode = productItemSet.get(0).get("BRAND_CODE") + "";
                    strProductTypeCode = productItemSet.get(0).get("PRODUCT_TYPE_CODE") + "";
                    if (strBrandCode == "" || strProductTypeCode == "")
                    {
                        throw new EcAopServerBizException("9999", "在产品映射属性表中未查询到产品ID【" + productId + "】的产品品牌或者活动类型信息");
                    }
                    brandCode = strBrandCode;
                    // 如果有附加包
                    List<Map> packageElement = (List<Map>) productInfo.get("packageElement");//packageElement
                    System.out.println("adfadfasdfchangepro_packageElement:" + packageElement);
                    if (packageElement != null && packageElement.size() > 0)
                    {
                        ArrayList<String> eleList = new ArrayList<String>();
                        for (int k = 0; k < packageElement.size(); k++)
                        {
                            Map peBuf = packageElement.get(k);
                            String elmId = "" + peBuf.get("elementId");
                            eleList.add(elmId);
                        }
                        String eleIdString = "";
                        for (int k = 0; k < eleList.size(); k++)
                        {
                            String elmId = eleList.get(k);
                            if (k == 0)
                                eleIdString = "'" + elmId;
                            else
                                eleIdString = eleIdString + "','" + elmId;
                        }
                        eleIdString = eleIdString + "'";
                        List<Map> appendProduct = queryPackageElementInfoByList(provinceCode, newProductId,
                                eparchyCode, eleIdString);
                        /*Map param = new HashMap();
                        param.put("COMMODITY_ID", newProductId);
                        param.put("PRODUCT_MODE", "01");
                        param.put("PROVINCE_CODE", provinceCode);
                        param.put("EPARCHY_CODE", eparchyCode);
                        param.put("eles", eleIdString);
                        List<Map> appendProduct = n25Dao.queryAppProductByEleList(param);// 改
                        */if (appendProduct != null && appendProduct.size() > 0)
                            preDealProductItemInfo(exchange, msg, appendProduct, eparchyCode, provinceCode, userId,
                                    startDate, monthLasttDay, serialNumber, extObject, lengthType);
                    }
                    // 产品台帐
                    Map productItem = new HashMap();
                    productItem.put("userId", userId);
                    productItem.put("productMode", strProductMode);
                    productItem.put("productId", productId);
                    //productItem.put("PRODUCT_TYPE_CODE", strProductTypeCode);
                    productItem.put("brandCode", strBrandCode);
                    //先记为"need"后面统一从CB获取
                    productItem.put("itemId", "need");
                    productItem.put("modifyTag", "0");
                    productItem.put("startDate", startDate);
                    productItem.put("endDate", monthLasttDay);
                    productItem.put("userIdA", "-1");
                    productItem.put("xDatatype", "NULL");
                    putItemsForExtByName("tradeProduct", extObject, productItem);
                    //tradeProductList.add(productItem);

                    if (isMainPro)
                    {
                        Map userItem = new HashMap();
                        userItem.put("userId", userId);
                        userItem.put("productId", productId);
                        userItem.put("brandCode", strBrandCode);
                        userItem.put("netTypeCode", "0050");
                        userItem.put("xDatatype", "NULL");
                        if (StringUtils.isNotEmpty((String) msg.get("recomPersonId")))
                        {
                            userItem.put("developStaffId", "" + msg.get("recomPersonId"));
                            userItem.put("developDepartId", "" + msg.get("channelId"));
                        }
                        putItemsForExtByName("tradeUser", extObject, userItem);
                        //tradeUserList.add(userItem);
                    }
                    // 活动台帐
                    Map productTypeTemp = new HashMap();
                    productTypeTemp.put("userId", userId);
                    productTypeTemp.put("productMode", strProductMode);
                    productTypeTemp.put("productId", productId);
                    productTypeTemp.put("productTypeCode", strProductTypeCode);
                    productTypeTemp.put("modifyTag", "0");
                    productTypeTemp.put("startDate", startDate);
                    productTypeTemp.put("endDate", monthLasttDay);
                    productTypeTemp.put("xDatatype", "NULL");
                    putItemsForExtByName("tradeProductType", extObject, productTypeTemp);
                    //tradeProductTypeList.add(productTypeTemp);
                }
                if ("02".equals(optType)) // 变更产品
                {
                    newProductId = "" + productInfo.get("productId");
                    // 如果有附加包
                    if (productInfo.get("packageElement") != null
                            && ((List<Map>) productInfo.get("packageElement")).size() > 0)
                    {
                        ArrayList<String> eleList = new ArrayList<String>();
                        ArrayList<String> dEle = new ArrayList<String>();
                        ArrayList<String> addEle = new ArrayList<String>();

                        for (int k = 0; k < ((List<Map>) productInfo.get("packageElement")).size(); k++)
                        {
                            Map pe = ((List<Map>) productInfo.get("packageElement")).get(k);
                            String elmId = "" + pe.get("elementId");
                            eleList.add(elmId);
                            if ("0".equals(pe.get("modType")))
                            {
                                addEle.add(elmId);
                            }
                            if ("1".equals(pe.get("modType")))
                            {
                                dEle.add(elmId);
                            }
                        }
                        // 删除元素
                        preDelInfo(dEle, msg, ExchangeUtils.ofCopy(exchange, msg), userInfo, extObject);
                        // 新增元素
                        if (addEle.size() != 0 && addEle != null)
                        {
                            String eleIdString = "";
                            for (int k = 0; k < addEle.size(); k++)
                            {
                                String elmId = addEle.get(k);
                                if (k == 0)
                                    eleIdString = "'" + elmId;
                                else
                                    eleIdString = eleIdString + "','" + elmId;
                            }
                            eleIdString = eleIdString + "'";
                            List<Map> addPeInfo = queryPackageElementInfoByList(provinceCode, newProductId,
                                    eparchyCode, eleIdString);

                            if (addPeInfo != null && addPeInfo.size() > 0)
                            {
                                preDealProductItemInfo(exchange, msg, addPeInfo, eparchyCode, provinceCode, userId,
                                        acceptDate, END_OF_WORLD, serialNumber, extObject, lengthType);

                            }
                            else
                            {
                                throw new EcAopServerBizException("9999", "在产品映射属性表中未查询到产品ID【" + newProductId
                                        + "】的产品附加包信息");
                            }
                        }
                    }
                }
            }
        }
        Object serType = msg.get("serType");
        Map baseObject = dealBase(exchange, msg, serialNumber, tradeStaffId, "need", departId, acceptDate,
                nextMon1stDay, userId, custId, acctId, useCustId, brandCode, userDiffCode, custName, linkAddress,
                linkPhone, eparchyCode, cityCode, newProductId, serType);
        // 解决发展人信息未写入cb的tf_b_trade_develop表的问题
        if (StringUtils.isNotEmpty((String) msg.get("recomPersonId")))
        {
            Map objItemOne = new HashMap();
            objItemOne.put("attrCode", "DEVELOP_STAFF_ID");
            objItemOne.put("attrValue", "" + msg.get("recomPersonId"));
            putItemsForExtByName("tradeItem", extObject, objItemOne);
            Map objItemTwo = new HashMap();
            objItemTwo.put("attrCode", "DEVELOP_DEPART_ID");
            objItemTwo.put("attrValue", "" + msg.get("channelId"));
            putItemsForExtByName("tradeItem", extObject, objItemTwo);
        }

        // /拼装SUB_ITEM TRADE_ITEM 台账
        Map<String, String> busiMap = new HashMap(baseObject);
        //busiMap.put("PROVINCE_CODE", provinceCode);
        //busiMap.put("SUBSYS_CODE", "MALL");
        busiMap.put("TRADE_TYPE_CODE", busiMap.get("tradeTypeCode").substring(1));
        Map appendData = new HashMap();
        appendData.put("E_IN_MODE", "" + msg.get("eModeCode"));
        msg.put("APPEND_MAP", appendData);
        dealItemInfo(msg, busiMap, extObject);
        // /拼装SUB_ITEM 台账 结束
        //统一调获取CB流水itemId
        System.out.println("changeproduct_extObject_b:" + extObject);
        System.out.println("changeproduct_baseObject_b:" + baseObject);
        getCBItemIdByList(exchange, msg, extObject, baseObject);
        System.out.println("changeproduct_extObject:" + extObject);
        System.out.println("changeproduct_baseObject:" + baseObject);
        msg.put("ext", extObject);
        msg.put("base", baseObject);
        //msg.put("ordersId", tradeId);
        msg.put("serinalNamber", msg.get("serinalNamber"));
        msg.put("operTypeCode", "0");
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++)
        {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

    /***
     * 根据用户原来的速率选择 速率 默认100M
     * 
     * @param productInfoSet
     * @param svcInfoList
     * @return
     */
    @SuppressWarnings({ "rawtypes" })
    public List<Map> chooseSpeedByUser(List<Map> productInfoSet, List<Map> svcInfoList) {
        String WCDMAService = "50105"; // CBSS 3G 42M seviceId
        String LTEService = "50103";// CBSS LTE 100 seviceId
        String plus4G = "50107"; // CBSS 4G+ 300 seviceId
        for (int i = 0; i < svcInfoList.size(); i++)
        {
            Map svc = svcInfoList.get(i);
            if (WCDMAService.equals(svc.get("serviceId")))
            {
                // return chooseSpeed(productInfoSet, "42");
                return new NewProductUtils().chooseSpeed(productInfoSet, "42");
            }
            if (LTEService.equals(svc.get("serviceId")))
            {
                // return chooseSpeed(productInfoSet, "100");
                return new NewProductUtils().chooseSpeed(productInfoSet, "100");
            }
            if (plus4G.equals(svc.get("serviceId")))
            {
                // return chooseSpeed(productInfoSet, "300");
                return new NewProductUtils().chooseSpeed(productInfoSet, "300");
            }
        }
        // 默认选300
        // return chooseSpeed(productInfoSet, "300");
        return new NewProductUtils().chooseSpeed(productInfoSet, "300");
    }

    /**
     * 根据商城传入的带宽选 择速率
     * 
     * @param productInfoResult
     * @param speed
     * @return
     */
    @SuppressWarnings("rawtypes")
    private List<Map> chooseSpeed(List<Map> productInfoResult, String speed) {
        String LTE = "100";
        String plus4G = "300";

        // 默认下发42M速率，支持传100M和300M两种速率 2016-4-14 lixl
        // 50103(LTE100M) 50105(42M) 50107(4G+ 300M)
        String kick1st = "50103";
        String kick2nd = "50107";
        if (LTE.equals(speed))
        {
            kick1st = "50105";
            kick2nd = "50107";
        }
        else if (plus4G.equals(speed))
        {
            kick1st = "50103";
            kick2nd = "50105";
        }
        List<Map> newProductInfo = new ArrayList<Map>();
        for (int m = 0; m < productInfoResult.size(); m++)
        {
            if (kick1st.equals(String.valueOf(productInfoResult.get(m).get("ELEMENT_ID")))
                    || kick2nd.equals(String.valueOf(productInfoResult.get(m).get("ELEMENT_ID"))))
            {
                newProductInfo.add(productInfoResult.get(m));
            }
        }
        productInfoResult.removeAll(newProductInfo);
        return productInfoResult;
    }

    /***
     * 将数据库里查出短信网龄的默认下移一个月去掉
     * 处理 国际业务
     * 
     * @param productInfoSet
     * @param mainProductId
     * @param provinceCode
     * @param userRsp
     * @return
     * @throws Exception
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public List<Map> preDealProductInfo(List<Map> productInfoSet, Object mainProductId, String provinceCode,
            Map userInfo) throws Exception {
        // 将数据库里查出短信网龄的默认下移一个月去掉
        for (int i = 0; i < productInfoSet.size(); i++)
        {
            Map info = productInfoSet.get(i);
            if ("5702000".equals(info.get("ELEMENT_ID")))
            {
                info.put("EFFECTIVE_MODE", "");
                info.put("EFFECTIVE_EXCURSION", "");
            }
        }
        List<Map> svcInfo = (List<Map>) userInfo.get("svcInfo");
        List<Map> discntInfo = (List<Map>) userInfo.get("discntInfo");

        // 下面特殊处理 国际业务
        // 特服属性信息继承 下面代码主要继承 国际业务
        if (svcInfo != null && svcInfo.size() > 0)
        {
            for (int k = 0; k < svcInfo.size(); k++)
            {
                Map sv = svcInfo.get(k);
                String serviceId = "" + sv.get("serviceId");
                if ("50015".equals(serviceId))// 50015国际漫游
                {
                    List<Map> svcInfoSet = queryPackageIdByNoDefaultElementId(provinceCode, "" + mainProductId,
                            serviceId);

                    if (svcInfoSet != null && svcInfoSet.size() > 0)
                    {
                        productInfoSet.add(svcInfoSet.get(0));
                        // 剔除国内漫游50014
                        for (int n = 0; n < productInfoSet.size(); n++)
                        {
                            Map d = productInfoSet.get(n);
                            if ("50014".equals(d.get("ELEMENT_ID")))
                            {
                                productInfoSet.remove(n);
                                //break;
                            }
                        }
                    }
                }

                if ("50011".equals(serviceId))// 50011国际长途
                {/* Map param = new HashMap();
                    param.put("PRODUCT_ID", mainProductId);
                    param.put("COMMODITY_ID", mainProductId);
                    param.put("PROVINCE_CODE", provinceCode);
                    param.put("ELEMENT_ID", serviceId);
                    List<Map> svcInfoSet = dao.queryPackageBySvcId(param);// 根据product_id、service_id查找package_id
                    */
                    List<Map> svcInfoSet = queryPackageIdByNoDefaultElementId(provinceCode, "" + mainProductId,
                            serviceId);

                    if (svcInfoSet != null && svcInfoSet.size() > 0)
                    {
                        productInfoSet.add(svcInfoSet.get(0));
                        // 剔除国内长途 50010
                        for (int n = 0; n < productInfoSet.size(); n++)
                        {
                            Map d = productInfoSet.get(n);
                            if ("50010".equals(d.get("ELEMENT_ID")))
                            {
                                productInfoSet.remove(n);
                                //break;
                            }
                        }
                    }
                }
            }
        }
        // end 特殊处理 国际业务

        // 继承部分资费
        // 获得继承资费列表（配表里）
        /*Map<String, Object> param_0 = new HashMap<String, Object>();
        param_0.put("SUBSYS_CODE", "CSM");
        param_0.put("PARAM_ATTR", "8541");
        param_0.put("PARA_CODE1", "ZZZZ");
        param_0.put("PARA_CODE2", "ZZZZ");
        param_0.put("PROVINCE_CODE", "ZZZZ");
        param_0.put("EPARCHY_CODE", provinceCode);*/
        List<Map> keepDiscnt = queryCommParaSopN6AOP(provinceCode);
        if (discntInfo != null && keepDiscnt != null)
        {
            for (int l = 0; l < keepDiscnt.size(); l++)
            {
                String keepDis = "" + keepDiscnt.get(l).get("PARA_CODE3");
                for (int k = 0; k < discntInfo.size(); k++)
                {
                    if (discntInfo.get(k).get("discntCode").equals(keepDis))
                    {
                        /* Map param = new HashMap();
                         param.put("PRODUCT_ID", mainProductId);
                         param.put("COMMODITY_ID", mainProductId);
                         param.put("PROVINCE_CODE", provinceCode);
                         param.put("ELEMENT_ID", keepDis);
                         List<Map> keepDisSet = dao.queryPackageBySvcId(param);// 根据product_id、service_id查找package_id
                        */
                        List<Map> keepDisSet = queryPackageIdByNoDefaultElementId(provinceCode, "" + mainProductId,
                                keepDis);

                        if (keepDisSet != null && keepDisSet.size() > 0)
                        {
                            ((Map) keepDisSet.get(0)).put("KEEP_END_DATE", discntInfo.get(k).get("endDate"));
                            productInfoSet.add(keepDisSet.get(0));
                        }
                    }
                }
            }
        }
        return productInfoSet;
    }

    /**
     * CBSS 处理产品默认属性Td_b_Commodity_Trans_Item ITEM_ID取值 方式修改
     * 
     * @param productInfoSet
     * @param eparchyCode
     * @param startDate
     * @param endDate
     * @param serialNumber
     * @param nonFetchStr
     * @param ext
     * @throws Exception
     */
    //preDealProductItemInfo(productInfoSet, eparchyCode, provinceCode, userId, startDate,
    //monthLasttDay, serialNumber, null, extObject);
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void preDealProductItemInfo(Exchange exchange, Map msg, List<Map> productInfoSet, String eparchyCode,
            String provinceCode, String userId, String startDate, String endDate, String serialNumber, Map extObject,
            String isFinalCode) throws Exception {
        String commodityId = String.valueOf(((Map) productInfoSet.get(0)).get("PRODUCT_ID"));

        for (int i = 0; i < productInfoSet.size(); i++)
        {
            Map productInfo = productInfoSet.get(i);
            String realStartDate = startDate;
            String realEndDate = endDate;
            countStartDateAndEndDate(productInfo, realStartDate, startDate, realEndDate, endDate);
            /*
            
            String realStartDate = startDate;
            String realEndDate = endDate;
            if (StringUtils.isNotEmpty((String) productInfo.get("START_UNIT"))
                    && StringUtils.isNotEmpty((String) productInfo.get("START_OFFSET")))
            {
                int mode = Integer.parseInt((String) productInfo.get("START_UNIT"));
                int amount = Integer.parseInt((String) productInfo.get("START_OFFSET"));
                realStartDate = GetDateUtils.getSpecifyDateTime(1, mode, startDate, amount);
            }
            if (StringUtils.isNotEmpty((String) productInfo.get("END_UNIT"))
                    && StringUtils.isNotEmpty((String) productInfo.get("END_OFFSET")))
            {
                int mode = Integer.parseInt((String) productInfo.get("END_UNIT"));
                int amount = Integer.parseInt((String) productInfo.get("END_OFFSET"));
                realEndDate = GetDateUtils.getSpecifyDateTime(1, mode, startDate, amount);
                realEndDate = GetDateUtils.getSpecifyDateTime(1, 6, realEndDate, -1);
            }
            if (StringUtils.isNotEmpty((String) productInfo.get("END_ABSOLUTE_DATE")))
            {
                realEndDate = (String) productInfo.get("END_ABSOLUTE_DATE");
            }*/
            ///---------------
            /*Map detailProduct = actProductInfo.get(0);
            String subProductMode = (String) (detailProduct.get("PRODUCT_MODE"));
            String subProductTypeCode = (String) (detailProduct.get("PRODUCT_TYPE_CODE"));
            String strBrandcode = (String) (detailProduct.get("BRAND_CODE"));
            String endOffSet = String.valueOf(detailProduct.get("END_OFFSET"));
            String enableTag = (String) (detailProduct.get("ENABLE_TAG"));// 0-绝对时间（看开始时间） 1-相对时间
            String strStartUnit = (String) (detailProduct.get("START_UNIT"));// '0:天 1:自然天 2:月 3:自然月 4:年 5:自然年';
            String endUnit = (String) (null == detailProduct.get("END_UNIT") ? "0" : detailProduct.get("END_UNIT"));
            String startOffset = String.valueOf(detailProduct.get("START_OFFSET"));// 生效偏移时间
            String endEnableTag = String.valueOf(detailProduct.get("END_ENABLE_TAG"));// 0-绝对时间（看开始时间） 1-相对时间
            String endDate = String.valueOf(detailProduct.get("END_ABSOLUTE_DATE"));// END_ENABLE_TAG为1时，需要才发此结束时间

            // 如果值为空则进行默认处理
            if (!"null".equals(enableTag) && !"null".equals(startOffset)
                    && !"null".equals(strStartUnit)) {
                monthFirstDay = GetDateUtils.getSpecifyDateTime(
                        Integer.parseInt(enableTag),
                        Integer.parseInt(strStartUnit), GetDateUtils.getSysdateFormat(),
                        Integer.parseInt(startOffset));
            }
            if (!"null".equals(enableTag) && "0".equals(enableTag)) {
                startOffset = "0";
            }
            // 如果值为空则进行默认处理
            if (!"null".equals(endEnableTag) && "0".equals(endEnableTag) && !"null".equals(endDate)) {
                monthLasttDay = endDate;
            }
            if (!"null".equals(endOffSet) && !"null".equals(endUnit) && !"null".equals(endEnableTag)
                    && "1".equals(endEnableTag)) {
                monthLastDay = GetDateUtils.getSpecifyDateTime(
                        Integer.parseInt(endEnableTag),
                        Integer.parseInt(endUnit),
                        GetDateUtils.getSysdateFormat(),
                        Integer.parseInt(endOffSet)
                                + Integer.parseInt(startOffset));
                // 结束月最后一天
                monthLasttDay = GetDateUtils.getSpecifyDateTime(1, 6, monthLastDay,
                        -1);
                monthLasttDay = GetDateUtils.transDate(monthLasttDay, 14);

            }
            if (monthLasttDay.length() > 19) {
                monthLasttDay = monthLasttDay.substring(0, 19);
            }*/
            ///---------------
            /* else
             {
                 //param.put("realEndDate", realEndDate);
             }
             param.put("startDate", startDate);
             param.put("commodityId", commodityId);
             param.put("serialNumber", serialNumber);
             param.put("isFinalCode", isFinalCode);
             dealProductItemInfoBody(productInfo, param, extObject);
            */

            String productId = productInfo.get("PRODUCT_ID") + "";
            try
            {
                // 服务属性
                if ("S".equals("" + productInfo.get("ELEMENT_TYPE_CODE")))
                {
                    Map serviceItem = new HashMap();
                    //因为这个id要传入productItemTrade，先取
                    String itemId = GetSeqUtil.getSeqFromCb(pmp[2], ExchangeUtils.ofCopy(exchange, msg), "seq_item_id",
                            1).get(0)
                            + "";
                    serviceItem.put("userId", userId);
                    serviceItem.put("productId", productId);
                    serviceItem.put("packageId", "" + productInfo.get("PACKAGE_ID"));
                    serviceItem.put("serviceId", "" + productInfo.get("ELEMENT_ID"));
                    serviceItem.put("itemId", itemId);
                    serviceItem.put("modifyTag", "0");
                    serviceItem.put("startDate", realStartDate);
                    serviceItem.put("endDate", realEndDate);
                    serviceItem.put("userIdA", "-1");
                    serviceItem.put("xDatatype", "NULL");
                    putItemsForExtByName("tradeSvc", extObject, serviceItem);
                    //S类型因为有老数据，就从原来表里查吧
                    List<Map> serviceItemSet = queryProductItemInfo(commodityId, "" + productInfo.get("ELEMENT_ID"),
                            "S", provinceCode);
                    if (serviceItemSet != null && serviceItemSet.size() > 0)
                    {
                        for (int j = 0; j < serviceItemSet.size(); j++)
                        {
                            Map serviceItemInfo = (Map) serviceItemSet.get(j);
                            productItemTrade(extObject, serviceItemInfo, itemId, "2", realStartDate, true);
                        }

                    }
                }
                // 资费属性
                else if ("D".equals(String.valueOf(productInfo.get("ELEMENT_TYPE_CODE"))))
                {
                    Map discntItem = new HashMap();
                    //因为这个id要传入productItemTrade，先取
                    String itemId = GetSeqUtil.getSeqFromCb(pmp[2], ExchangeUtils.ofCopy(exchange, msg), "seq_item_id",
                            1).get(0)
                            + "";
                    String elementId = String.valueOf(productInfo.get("ELEMENT_ID"));
                    discntItem.put("id", userId);
                    discntItem.put("idType", "1");
                    discntItem.put("productId", productId);
                    discntItem.put("packageId", "" + productInfo.get("PACKAGE_ID"));
                    discntItem.put("discntCode", elementId);
                    discntItem.put("specTag", "1");
                    discntItem.put("relationTypeCode", "");
                    discntItem.put("itemId", itemId);
                    discntItem.put("modifyTag", "0");
                    if (StringUtils.isNotEmpty(isFinalCode) && !"Y".equals(isFinalCode))
                    {
                        Map discntDateMap = TradeManagerUtils
                                .getDiscntEffectTime(elementId, realStartDate, realEndDate);
                        discntItem.put("startDate", discntDateMap.get("monthFirstDay"));
                        discntItem.put("endDate", discntDateMap.get("monthLasttDay"));
                    }
                    else
                    {
                        discntItem.put("startDate", realStartDate);
                        discntItem.put("endDate", realEndDate);
                    }
                    if ("5702000".equals(elementId))
                    {
                        discntItem.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                    }
                    discntItem.put("userIdA", "-1");
                    discntItem.put("xDatatype", "NULL");

                    putItemsForExtByName("tradeDiscnt", extObject, discntItem);
                    //D类型也从原来表里查吧，一堆未知的东西
                    List<Map> discntItemSet = queryProductItemInfo(commodityId, "" + productInfo.get("ELEMENT_ID"),
                            "D", provinceCode);
                    if (discntItemSet != null && discntItemSet.size() > 0)
                    {
                        for (int j = 0; j < discntItemSet.size(); j++)
                        {
                            Map discntItemInfo = (Map) discntItemSet.get(j);
                            //无realStartDate
                            productItemTrade(extObject, discntItemInfo, itemId, "3", null, false);
                        }
                    }
                }
                // SP信息
                else if ("X".equals(String.valueOf(productInfo.get("ELEMENT_TYPE_CODE"))))
                {
                    String spId = "-1";
                    String partyId = "-1";
                    String spProductId = "-1";

                    /*
                    Map spItemParam = new HashMap();
                    spItemParam.put("PID", (String) productInfo.get("ELEMENT_ID"));
                    spItemParam.put("PROVINCE_CODE", provinceCode);
                    spItemParam.put("COMMODITY_ID", commodityId);
                    spItemParam.put("PTYPE", "X");
                    List<Map> spItemSet = dao.queryProductItemInfo(spItemParam);//TODO 改

                    
                    
                    if (spItemSet != null && spItemSet.size() > 0)
                    {
                        for (int j = 0; j < spItemSet.size(); j++)
                        {
                            Map spItemInfo = (Map) spItemSet.get(j);
                            if ("SP_ID".equals("" + spItemInfo.get("ATTR_CODE")))
                            {
                                spId = (String) spItemInfo.get("ATTR_VALUE");
                            }
                            else if ("PARTY_ID".equals((String) spItemInfo.get("ATTR_CODE")))
                            {
                                partyId = (String) spItemInfo.get("ATTR_VALUE");
                            }
                            else if ("SP_PRODUCT_ID".equals((String) spItemInfo.get("ATTR_CODE")))
                            {
                                spProductId = (String) spItemInfo.get("ATTR_VALUE");
                            }
                        }
                    }*/
                    //X类型从别的表里查
                    Map spItemParam = new HashMap();
                    spItemParam.put("PROVINCE_CODE", provinceCode);
                    spItemParam.put("SPSERVICEID", "" + productInfo.get("ELEMENT_ID"));
                    List<Map> spItemSet = n25Dao.querySPServiceAttr(spItemParam);
                    if (spItemSet.size() == 0 || spItemSet == null)
                    {
                        throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + productInfo.get("ELEMENT_ID")
                                + "】的产品属性信息");
                    }
                    for (int l = 0; l < spItemSet.size(); l++)
                    {
                        Map spItemInfo = spItemSet.get(l);
                        spId = "" + spItemInfo.get("SP_ID");
                        partyId = "" + spItemInfo.get("PARTY_ID");
                        spProductId = "" + spItemInfo.get("SP_PRODUCT_ID");
                    }

                    /*
                    List<Map> spItemSet = queryProductItemInfoByTypeX(provinceCode, commodityId,
                            (String) productInfo.get("ELEMENT_ID"));
                    if (spItemSet != null && spItemSet.size() > 0)
                    {
                        Map spItemInfo = (Map) spItemSet.get(0);
                        spId = (String) spItemInfo.get("SP_ID");
                        partyId = (String) spItemInfo.get("PARTY_ID");
                        spProductId = (String) spItemInfo.get("SP_PRODUCT_ID");
                    }*/
                    Map spItem = new HashMap();
                    //String itemId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_item_id");
                    spItem.put("userId", userId);
                    spItem.put("serialNumber", serialNumber);
                    spItem.put("productId", productId);
                    spItem.put("packageId", "" + productInfo.get("PACKAGE_ID"));
                    spItem.put("partyId", partyId);
                    spItem.put("spId", spId);
                    spItem.put("spProductId", spProductId);
                    spItem.put("firstBuyTime", startDate);
                    spItem.put("paySerialNumber", serialNumber);
                    spItem.put("startDate", startDate);
                    spItem.put("enddate", END_OF_WORLD);
                    spItem.put("updateTime", startDate);
                    spItem.put("remark", "");
                    spItem.put("modifyTag", "0");
                    spItem.put("payUserId", userId);
                    spItem.put("spServiceId", "" + productInfo.get("ELEMENT_ID"));
                    //先记为"need"后面统一从CB获取
                    spItem.put("itemId", "need");
                    spItem.put("userIdA", "-1");
                    spItem.put("xDatatype", "NULL");
                    putItemsForExtByName("tradeSp", extObject, spItem);
                }
                // 用户属性(固化在产品上的属性)
                //P类查原表
                else if ("P".equals("" + productInfo.get("ELEMENT_TYPE_CODE")))
                {
                    /*Map userItemParam = new HashMap();
                    userItemParam.put("PID", productId);
                    userItemParam.put("PROVINCE_CODE", provinceCode);
                    userItemParam.put("COMMODITY_ID", commodityId);
                    userItemParam.put("PTYPE", "P");
                    List<Map> userItemSet = dao.queryProductItemInfo(userItemParam);//TODO 改
                    *///D类型也从原来表里查吧，一堆未知的东西
                    List<Map> userItemSet = queryProductItemInfo(commodityId, productId, "P", provinceCode);

                    if (userItemSet != null && userItemSet.size() > 0)
                    {
                        for (int j = 0; j < userItemSet.size(); j++)
                        {
                            productItemTrade(extObject, (Map) userItemSet.get(j), userId, "0", null, false);
                        }
                    }

                }
            }
            catch (Exception ex)
            {
                throw ex;
            }
        }
    }

    /* *//**
                       * 根据ATTR_CODE取出相应的ATTR_VALUE
                       * @param attrCode
                       * @param infoList
                       * @return
                       */
    /*
    @SuppressWarnings({ "rawtypes" })
    private String getValueFromItem(String attrCode, List<Map> infoList) {
     String attrValue = "";
     for (int i = 0; i < infoList.size(); i++)
     {
         Map productItemInfo = infoList.get(i);
         if ("U".equals(productItemInfo.get("PTYPE")))
         {
             if (attrValue == "" && attrCode.equals(productItemInfo.get("ATTR_CODE")))
             {
                 attrValue = "" + productItemInfo.get("ATTR_VALUE");
             }
         }
     }
     return attrValue;
    }*/

    /***
     * 根据用户信息生成删除节点
     * 
     * @param dEle
     * @param msg(含userid,serialNumber,userDiscntInfo,userSpInfo,userSvcInfo)
     * @param exchange
     * @throws Exception
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void preDelInfo(ArrayList<String> dEle, Map msg, Exchange exchange, Map userInfo, Map extObject)
            throws Exception {
        String userId = "" + userInfo.get("userId");
        List<Map> discntInfo = (List<Map>) userInfo.get("discntInfo");
        List<Map> spInfo = (List<Map>) userInfo.get("spInfo");
        List<Map> svcInfo = (List<Map>) userInfo.get("svcInfo");
        String acceptDate = GetDateUtils.transDate(GetDateUtils.getDate(), 19);
        String nextMon1stDay = GetDateUtils.getNextMonthFirstDayFormat();//yyyyMMddHHmmss
        String enddate = GetDateUtils.getSpecifyDateTime(1, 7, nextMon1stDay, -1);//yyyyMMddHHmmss

        for (int i = 0; i < dEle.size(); i++)
        {
            if (null != discntInfo && discntInfo.size() > 0)
            {
                for (int j = 0; j < discntInfo.size(); j++)
                {
                    if (dEle.get(i).equals(discntInfo.get(j).get("discntCode")))
                    {
                        Map discntItem = new HashMap();
                        Map dis = discntInfo.get(j);
                        Map<String, Object> param = new HashMap<String, Object>();
                        param.put("DISCNT_CODE", dis.get("discntCode"));
                        Map discnt = (Map) dao.queryDiscntDate(param).get(0);//FIXME 需要改SQL，没有END_MODE返回
                        String endmode = "" + discnt.get("END_MODE");
                        if ("0".equals(endmode))
                        {
                            enddate = acceptDate;
                        }
                        else if ("2".equals(endmode))
                        {
                            enddate = GetDateUtils.getSpecifyDateTime(1, 1, acceptDate, 1);
                            enddate = GetDateUtils.getSpecifyDateTime(1, 6, enddate, -1);
                        }
                        //String itemId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_item_id");
                        discntItem.put("id", userId);
                        discntItem.put("idType", "1");
                        discntItem.put("productId", dis.get("productId"));
                        discntItem.put("packageId", dis.get("packageId"));
                        discntItem.put("discntCode", dis.get("discntCode"));
                        discntItem.put("specTag", "1");
                        discntItem.put("relationTypeCode", "");
                        //先记为"need"后面统一从CB获取
                        discntItem.put("itemId", "need");
                        discntItem.put("modifyTag", "1");
                        discntItem.put("startDate", GetDateUtils.transDate("" + dis.get("startDate"), 19));
                        discntItem.put("endDate", enddate);
                        discntItem.put("userIdA", "-1");
                        discntItem.put("xDatatype", "NULL");
                        putItemsForExtByName("tradeDiscnt", extObject, discntItem);
                        //discntList.add(discntItem);
                    }
                }
            }
            if (null != svcInfo && svcInfo.size() > 0)
            {
                for (int j = 0; j < svcInfo.size(); j++)
                {
                    if (dEle.get(i).equals(svcInfo.get(j).get("serviceId")))
                    {
                        Map serviceItem = new HashMap();
                        Map svc = svcInfo.get(j);
                        //String itemId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_item_id");
                        serviceItem.put("userId", userId);
                        serviceItem.put("idType", "1");
                        serviceItem.put("productId", svc.get("productId"));
                        serviceItem.put("packageId", svc.get("packageId"));
                        serviceItem.put("serviceId", svc.get("discntCode"));
                        //先记为"need"后面统一从CB获取
                        serviceItem.put("itemId", "need");
                        serviceItem.put("modifyTag", "1");
                        serviceItem.put("startDate", svc.get("startDate"));
                        serviceItem.put("endDate", enddate);
                        serviceItem.put("userIdA", "-1");
                        serviceItem.put("xDatatype", "NULL");
                        putItemsForExtByName("tradeSvc", extObject, serviceItem);
                        //svcList.add(serviceItem);
                    }
                }
            }

            if (null != spInfo && spInfo.size() > 0)
            {
                for (int j = 0; j < spInfo.size(); j++)
                {
                    if (dEle.get(i).equals(spInfo.get(j).get("spServiceId")))
                    {
                        String spId = "-1";
                        String partyId = "-1";
                        String spProductId = "-1";
                        Map sp = spInfo.get(j);

                        //X类型从别的表里查
                        Map spItemParam = new HashMap();
                        spItemParam.put("PROVINCE_CODE", msg.get("eparchyCode"));
                        spItemParam.put("SPSERVICEID", sp.get("spServiceId"));
                        List<Map> spItemSet = n25Dao.querySPServiceAttr(spItemParam);
                        if (spItemSet.size() == 0 || spItemSet == null)
                        {
                            throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + sp.get("spServiceId")
                                    + "】的产品属性信息");
                        }
                        for (int l = 0; l < spItemSet.size(); l++)
                        {
                            Map spItemInfo = spItemSet.get(l);
                            spId = "" + spItemInfo.get("SP_ID");
                            partyId = "" + spItemInfo.get("PARTY_ID");
                            spProductId = "" + spItemInfo.get("SP_PRODUCT_ID");
                        }

                        /*Map spItemParam = new HashMap();
                        spItemParam.put("PTYPE", "X");
                        spItemParam.put("COMMODITY_ID", sp.get("productId"));
                        spItemParam.put("PROVINCE_CODE", msg.get("eparchyCode"));
                        spItemParam.put("PRODUCT_CODE", "NULL");
                        spItemParam.put("PID", sp.get("spServiceId"));
                        List<Map> spItemInfoResult = dao.queryProductItemInfo(spItemParam);//TODO 改

                        if (spItemInfoResult.size() == 0 || spItemInfoResult == null)
                        {
                            throw new EcAopServerBizException("9999", "在SP表中未查询到【" + sp.get("spServiceId") + "】的元素属性信息");
                        }
                        for (int l = 0; l < spItemInfoResult.size(); l++)
                        {
                            Map spItemInfo = spItemInfoResult.get(l);
                            if ("SP_ID".equals((String) spItemInfo.get("ATTR_CODE")))
                            {
                                spId = (String) spItemInfo.get("ATTR_VALUE");
                            }
                            else if ("PARTY_ID".equals((String) spItemInfo.get("ATTR_CODE")))
                            {
                                partyId = (String) spItemInfo.get("ATTR_VALUE");
                            }
                            else if ("SP_PRODUCT_ID".equals((String) spItemInfo.get("ATTR_CODE")))
                            {
                                spProductId = (String) spItemInfo.get("ATTR_VALUE");
                            }
                        }*/

                        Map spItem = new HashMap();
                        //String itemId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_item_id");
                        spItem.put("userId", userId);
                        spItem.put("serialNumber", msg.get("serialNumber"));
                        spItem.put("productId", sp.get("productId"));
                        spItem.put("packageId", sp.get("packageId"));
                        spItem.put("partyId", partyId);
                        spItem.put("spId", spId);
                        spItem.put("spProductId", spProductId);
                        spItem.put("firstBuyTime", sp.get("firstBuyTime"));
                        spItem.put("paySerialNumber", sp.get("paySerialNumber"));
                        spItem.put("startDate", GetDateUtils.transDate("" + sp.get("startDate"), 19));
                        spItem.put("enddate", enddate);
                        spItem.put("updateTime", sp.get("updateTime"));
                        spItem.put("remark", "");
                        spItem.put("modifyTag", "1");
                        spItem.put("payUserId", userId);
                        spItem.put("spServiceId", sp.get("spServiceId"));
                        //先记为"need"后面统一从CB获取
                        spItem.put("itemId", "need");
                        spItem.put("userIdA", "-1");
                        spItem.put("xDatatype", "NULL");
                        putItemsForExtByName("tradeSp", extObject, spItem);
                        //spList.add(spItem);
                    }
                }
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Map dealBase(Exchange exchange, Map msg, String serialNumber, String tradeStaffId, String tradeId,
            String departId, String acceptDate, String realStartDate, String userId, String custId, String acctId,
            String useCustId, String brandCode, String userDiffCode, String custName, String linkAddress,
            String linkPhone, String eparchyCode, String cityCode, String newProductId, Object serType) {
        Map baseObject = new HashMap();

        baseObject.put("subscribeId", tradeId);
        baseObject.put("tradeId", tradeId);
        baseObject.put("acceptDate", acceptDate);
        baseObject.put("tradeDepartId", departId);
        baseObject.put("cityCode", cityCode);
        baseObject.put("areaCode", eparchyCode);
        baseObject.put("nextDealTag", "Z");
        baseObject.put("olcomTag", "0");
        // 添加strInModeCode赋值X、E
        String strInModeCode = "";
        if ((modecodes).contains("" + msg.get("appCode")))
        {
            strInModeCode = "X";
        }
        else
        {
            strInModeCode = "E";
        }
        baseObject.put("inModeCode", strInModeCode);
        baseObject.put("tradeStaffId", tradeStaffId);
        baseObject.put("tradeTypeCode", "001".equals(serType) ? "0110" : "0120");
        baseObject.put("productId", newProductId);
        baseObject.put("brandCode", brandCode);
        baseObject.put("userId", userId);
        baseObject.put("custId", custId);
        baseObject.put("usecustId", useCustId);
        baseObject.put("acctId", acctId);
        baseObject.put("userDiffCode", userDiffCode);
        baseObject.put("netTypeCode", "50");
        baseObject.put("serinalNamber", serialNumber);
        baseObject.put("custName", custName);
        baseObject.put("termIp", "0.0.0.0");
        baseObject.put("eparchyCode", eparchyCode);
        baseObject.put("cityCode", cityCode);
        baseObject.put("execTime", realStartDate);
        baseObject.put("operFee", "0"); // 营业费用
        baseObject.put("foregift", "0"); // 押金
        baseObject.put("advancePay", "0"); // 预存
        baseObject.put("feeState", "");
        baseObject.put("feeStaffId", "");
        baseObject.put("cancelTag", "0");
        baseObject.put("checktypeCode", "8");
        baseObject.put("chkTag", "0");
        baseObject.put("actorName", "");
        baseObject.put("actorCertTypeId", "");
        baseObject.put("actorPhone", "");
        baseObject.put("actorCertNum", "");
        baseObject.put("contact", custName);
        baseObject.put("contactAddress", linkAddress);
        baseObject.put("contactPhone", linkPhone);
        baseObject.put("remark", "");

        return baseObject;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void putItemsForExtByName(String name, Map extObject, Map item) {
        List<Map> array = null;
        Map map = null;
        if (null != extObject.get(name) && ((Map) extObject.get(name)).size() > 0)
        {
            map = (Map) extObject.get(name);
            array = (List) map.get("item");
        }
        else
        {
            map = new HashMap();
            array = new ArrayList<Map>();
        }
        array.add(item);
        map.put("item", array);
        extObject.put(name, map);
    }

    /**
     * 处理产品默认属性Td_b_Commodity_Trans_Item
     * 传入时间
     *第二个参数productItemInfo里取ATTR_CODE及ATTR_VALUE
     * @param extObject
     * @param productItemInfo
     * @param userId
     * @throws Exception
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void productItemTrade(Map extObject, Map productItemInfo, String itemId, String attrTypeCode,
            String realStartDate, boolean isNeedStartDate) throws Exception {
        Map userSubItems = new HashMap();
        userSubItems.put("attrTypeCode", attrTypeCode);
        userSubItems.put("attrCode", "" + productItemInfo.get("ATTR_CODE"));
        userSubItems.put("itemId", itemId);
        userSubItems.put("attrValue", "" + productItemInfo.get("ATTR_VALUE"));
        userSubItems.put("xDatatype", "NULL");
        if (null != extObject.get("ITEM_START_DATE") && !"".equals("" + extObject.get("ITEM_START_DATE"))
                && StringUtils.isNotEmpty("" + extObject.get("ITEM_START_DATE")))
        {
            userSubItems.put("startDate", "" + extObject.get("ITEM_START_DATE"));
        }
        if (isNeedStartDate)
        {
            userSubItems.put("startDate", realStartDate);
        }
        if (null != extObject.get("ITEM_END_DATE") && !"".equals("" + extObject.get("ITEM_END_DATE"))
                && StringUtils.isNotEmpty("" + extObject.get("ITEM_END_DATE")))
        {
            userSubItems.put("endDate", "" + extObject.get("ITEM_END_DATE"));
        }
        putItemsForExtByName("tradeSubItem", extObject, userSubItems);
    }

    @SuppressWarnings({ "rawtypes" })
    private void dealItemInfo(Map msg, Map busiMap, Map extObject) throws Exception {
        Map<String, Object> param = new HashMap<String, Object>();
        param.put("TRADE_TYPE_CODE", busiMap.get("TRADE_TYPE_CODE"));
        param.put("NET_TYPE_CODE", busiMap.get("netTypeCode"));
        param.put("BRAND_CODE", busiMap.get("brandCode"));
        param.put("PRODUCT_ID", busiMap.get("productId"));
        param.put("EPARCHY_CODE", busiMap.get("eparchyCode"));
        //param.put("PROVINCE_CODE", busiMap.get("PROVINCE_CODE"));
        //param.put("SUBSYS_CODE", busiMap.get("SUBSYS_CODE"));
        //不用改，查的TD_S_ITEM_SOP表
        List result = dao.queryAppendParam(param);
        for (int i = 0; i < result.size(); i++)
        {
            Map resultMap = (Map) result.get(i);
            String ruleCode = "";
            String attrTypeCode = "";
            String attrCode = "";
            String attrValue = "";
            if (resultMap.get("RULE_CODE") != null)
            {
                ruleCode = resultMap.get("RULE_CODE").toString();
            }
            if (resultMap.get("ATTR_TYPE_CODE") != null)
            {
                attrTypeCode = resultMap.get("ATTR_TYPE_CODE").toString();
            }
            if (resultMap.get("ATTR_CODE") != null)
            {
                attrCode = resultMap.get("ATTR_CODE").toString();
            }
            if (resultMap.get("ATTR_VALUE") != null)
            {
                attrValue = resultMap.get("ATTR_VALUE").toString();
            }
            if (!ruleCode.equals("4") && !ruleCode.equals(""))
            {
                dealItemValue(ruleCode, attrTypeCode, attrCode, attrValue, msg, busiMap, extObject);
            }
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void dealItemValue(String ruleCode, String attrTypeCode, String attrCode, String attrValue, Map msg,
            Map busiMap, Map extObject) throws Exception {
        if (ruleCode.equals("0"))
        {
            addItemTab(attrTypeCode, attrCode, busiMap.get("USER_ID").toString(), attrValue, extObject);
        }
        else if (ruleCode.equals("1"))
        {
            boolean flag = false;
            Map<String, Object> tempMap = new HashMap(msg);
            // 取key_value
            if (attrValue.contains("|") && attrValue.contains(",") && !attrValue.endsWith(","))
            {
                String[] str = attrValue.split("\\|")[0].split("/");
                String key = attrValue.split("\\|")[1].split(",")[0];
                String valueId = attrValue.split("\\|")[1].split(",")[1];
                for (int i = 0; i < str.length; i++)
                {
                    Object obj = tempMap.get(str[i]);
                    if (obj instanceof List)
                    {
                        if (i != str.length - 2)
                        {
                            tempMap = (Map) ((List) obj).get(0);
                        }
                        else
                        {
                            for (int j = 0; j < ((List) obj).size(); j++)
                            {
                                Map kvMap = (Map) ((List) obj).get(j);
                                if (kvMap.get(str[i + 1]).toString().equals(key))
                                {
                                    flag = true;
                                    attrValue = kvMap.get(valueId) != null ? kvMap.get(valueId).toString() : "";
                                    break;
                                }
                            }
                        }
                    }
                    else if (obj instanceof Map)
                    {
                        tempMap = (Map) obj;
                    }
                }
            }
            else
            {
                String[] str = attrValue.split("/");
                for (int i = 0; i < str.length; i++)
                {
                    Object obj = tempMap.get(str[i]);
                    if (obj instanceof List)
                    {
                        tempMap = (Map) ((List) obj).get(0);
                    }
                    else if (obj instanceof Map)
                    {
                        tempMap = (Map) obj;
                    }
                    else if (obj instanceof String)
                    {
                        flag = true;
                        attrValue = "" + obj;
                    }
                }
            }
            if (flag)
            {
                addItemTab(attrTypeCode, attrCode, busiMap.get("userId").toString(), attrValue, extObject);
            }

        }
        else if (ruleCode.equals("2"))
        {
            /* Interpreter bsh = new Interpreter();
             try
             {
                 bsh.set("attrTypeCode", attrTypeCode);
                 bsh.set("attrCode", attrCode);
                 bsh.set("map", msg);
                 bsh.set("busiMap", busiMap);
                 attrValue = (String) bsh.eval(attrValue);
             }
             catch (bsh.EvalError e)
             {
                 throw new EcAopServerBizException("9999", "执行脚本异常!");
             }
             addItemTab(attrTypeCode, attrCode, busiMap.get("userId").toString(), attrValue, extObject);
            */
            throw new EcAopServerBizException("9999", "套餐变更bsh执行脚本异常!");
        }
        else if (ruleCode.equals("3"))
        {

            String className = attrValue.split("@")[0];
            String methodName = attrValue.split("@")[1];
            invoke(className, methodName, attrTypeCode, attrCode, msg, busiMap, extObject);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void addItemTab(String attrTypeCode, String attrCode, String itemId, String attrValue, Map extObject)
            throws Exception {
        Map item = new HashMap();
        if (attrTypeCode.equals("I"))
        {
            item.put("attrCode", attrCode);
            item.put("attrValue", attrValue);
            item.put("xDatatype", "NULL");
            if (null != extObject.get("ITEM_START_DATE")
                    && StringUtils.isNotEmpty("" + extObject.get("ITEM_START_DATE")))
            {
                item.put("startDate", "" + extObject.get("ITEM_START_DATE"));
            }
            if (null != extObject.get("ITEM_END_DATE") && StringUtils.isNotEmpty("" + extObject.get("ITEM_END_DATE")))
            {
                item.put("endDate", "" + extObject.get("ITEM_END_DATE"));
            }
            putItemsForExtByName("tradeItem", extObject, item);
        }
        else
        {
            item.put("attrTypeCode", attrTypeCode);
            item.put("attrCode", attrCode);
            item.put("itemId", itemId);
            item.put("attrValue", attrValue);
            item.put("xDatatype", "NULL");
            if (null != extObject.get("ITEM_START_DATE")
                    && StringUtils.isNotEmpty("" + extObject.get("ITEM_START_DATE")))
            {
                item.put("startDate", "" + extObject.get("ITEM_START_DATE"));
            }
            if (null != extObject.get("ITEM_END_DATE") && StringUtils.isNotEmpty("" + extObject.get("ITEM_END_DATE")))
            {
                item.put("endDate", "" + extObject.get("ITEM_END_DATE"));
            }
            putItemsForExtByName("tradeSubItem", extObject, item);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void invoke(String className, String methodName, String attrTypeCode, String attrCode, Map map,
            Map busiMap, Map extObject) throws Exception {
        try
        {
            Class cls = Class.forName(className);
            Object instance = cls.newInstance();
            Method method = cls.getMethod(methodName, String.class, String.class, Map.class, Map.class, Map.class);
            method.invoke(instance, attrTypeCode, attrCode, map, busiMap, extObject);
        }
        catch (Exception e)
        {
            throw new EcAopServerBizException("9999", "execute " + methodName + " exception!");
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void dealReturn(Exchange exchange, Map msg) {
        Map out = exchange.getOut().getBody(Map.class);
        List<Map> rspInfo = (List) out.get("rspInfo");
        if (null == rspInfo || rspInfo.isEmpty())
        {
            throw new EcAopServerBizException("9999", "cBSS统一预提交未返回应答信息");
        }
        // Map rspMap = rspInfo.get(0);
        Map realOut = new HashMap();
        realOut.put("bssOrderId", rspInfo.get(0).get("bssOrderId"));
        Integer totalFee = 0;
        List feeList = new ArrayList();
        for (Map rspMap : rspInfo)
        {
            List<Map> provinceOrderInfo = (List) rspMap.get("provinceOrderInfo");
            if (null != provinceOrderInfo && provinceOrderInfo.size() > 0)
            {
                // 费用计算
                for (Map provinceOrder : provinceOrderInfo)
                {
                    totalFee = totalFee + Integer.valueOf(changeFenToLi(provinceOrder.get("totalFee")));
                    List<Map> preFeeInfoRsp = (List<Map>) provinceOrder.get("preFeeInfoRsp");
                    if (null == preFeeInfoRsp || preFeeInfoRsp.isEmpty())
                    {
                        continue;
                    }
                    feeList.addAll(dealFee(preFeeInfoRsp));
                }

            }
        }
        if (null != feeList && feeList.size() > 0)
            realOut.put("feeInfo", feeList);
        realOut.put("totalFee", totalFee + "");
        exchange.getOut().setBody(realOut);
    }

    private String changeFenToLi(Object fee) {
        return null == fee || "".equals(fee + "") || "0".equals(fee + "") || "null".equals(fee + "") ? "0" : fee + "0";

    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private List<Map> dealFee(List<Map> feeList) {
        List<Map> retFeeList = new ArrayList<Map>();
        for (Map fee : feeList)
        {
            Map retFee = new HashMap();
            retFee.put("feeId", fee.get("feeTypeCode") + "");// feeTypeCode
            retFee.put("feeCategory", fee.get("feeMode") + "");// feeMode
            retFee.put("feeDes", fee.get("feeTypeName") + "");// feeTypeName
            if (null != fee.get("maxDerateFee"))
                retFee.put("maxRelief", changeFenToLi(fee.get("maxDerateFee")));// 非必返maxDerateFee
            retFee.put("origFee", changeFenToLi(fee.get("fee")));// fee
            retFeeList.add(fee);
        }
        return retFeeList;
    }

    /**
     * @param provinceCode;省份编码
     * @param commodityId;产品ID
     * @param eparchyCode;地市编码
     * @param firstMonBillMode;首月付费模式
     * @param productMode;产品类型00主产品；01附加产品；04活动
     * */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public List<Map> qryDefaultPackageElement(String provinceCode, String commodityId, String eparchyCode,
            String firstMonBillMode, String productMode) {
        Map param = new HashMap();
        param.put("PROVINCE_CODE", provinceCode);
        param.put("PRODUCT_ID", commodityId);
        param.put("EPARCHY_CODE", eparchyCode);
        param.put("FIRST_MON_BILL_MODE", firstMonBillMode);
        param.put("PRODUCT_MODE", productMode);
        return n25Dao.qryDefaultPackageElement(param);
        /**输出参数
         * A.PRODUCT_ID,******
         * A.BRAND_CODE,
         * A.NET_TYPE_CODE,
         * A.PRODUCT_MODE,******
         * B.PRODUCT_TYPE_CODE,
         * D.PACKAGE_ID,******
         * E.ELEMENT_TYPE_CODE,******
         * E.ELEMENT_ID******
         * */
        //--------------------------------------
        /*Map quryMap_1 = new HashMap();
        quryMap_1.put("PROVINCE_CODE", provinceCode);
        quryMap_1.put("COMMODITY_ID", commodityId);
        quryMap_1.put("EPARCHY_CODE", eparchyCode);
        quryMap_1.put("FIRST_MON_BILL_MODE", firstMonBillMode);
        List<Map> subProductInfoSet = dao.queryProductInfo(quryMap_1);
        A.COMMODITY_ID,
        A.COMMODITY_TYPE,
        A.PRODUCT_MODE,******
        A.PRODUCT_ID,******
        A.PACKAGE_ID,******
        A.ELEMENT_TYPE_CODE,******
        A.ELEMENT_ID,******
        A.START_DATE,
        A.END_DATE,
        A.REMARK,
        A.PROVINCE_CODE,
        A.EFFECTIVE_MODE,
        A.EFFECTIVE_EXCURSION,
        A.FAIL_MODE,
        A.FAIL_EXCURSION
        */
    }

    /**
     * @param provinceCode;省份编码
     * @param commodityId;产品ID
     * @param eparchyCode;地市编码
     * @param firstMonBillMode;首月付费模式
     * @param productMode;产品类型00主产品；01附加产品；04活动
     * */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public List<Map> queryProductInfoWithLimit(String provinceCode, String commodityId, String eparchyCode,
            String firstMonBillMode, String productMode) {
        // 需要新增Limit表，新写SQL 暂用qryDefaultPackageElement
        Map param = new HashMap();
        param.put("PROVINCE_CODE", provinceCode);
        param.put("PRODUCT_ID", commodityId);
        param.put("EPARCHY_CODE", eparchyCode);
        param.put("FIRST_MON_BILL_MODE", firstMonBillMode);
        param.put("PRODUCT_MODE", productMode);
        return n25Dao.queryProductInfoWithLimit(param);
    }

    /**
     * @param provinceCode;省份编码++++
     * @param commodityId;产品ID
     * @param eparchyCode;地市编码
     * @param firstMonBillMode;首月付费模式
     * @param productMode;产品类型00主产品；01附加产品；04活动
     * */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public List<Map> queryProductItemInfoNoPtype(String subId) {
        /*Map quryMap_2 = new HashMap();String provinceCode, String commodityId, 
        quryMap_2.put("PROVINCE_CODE", provinceCode);
        quryMap_2.put("COMMODITY_ID", commodityId);
        quryMap_2.put("PRODUCT_ID", subId);
        List<Map> subproductItemSet = dao.queryProductItemInfoNoPtype(quryMap_2);*/
        Map param = new HashMap();
        //param.put("PROVINCE_CODE", provinceCode);
        //param.put("COMMODITY_ID", commodityId);
        param.put("PRODUCT_ID", subId);
        return n25Dao.queryProductAndPtypeProduct(param);
    }

    /**
     * @param commodityId;产品ID+++
     * */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public List<Map> queryProductAndPtypeProduct(String commodityId) {
        /*
         * Map quryMap_2 = new HashMap();
         * quryMap_2.put("PROVINCE_CODE", provinceCode);
         * quryMap_2.put("COMMODITY_ID", commodityId);
         * quryMap_2.put("PRODUCT_ID", subId);
         * List<Map> subproductItemSet = dao.queryProductItemInfoNoPtype(quryMap_2);
         */
        Map param = new HashMap();
        param.put("PRODUCT_ID", commodityId);
        return n25Dao.queryProductAndPtypeProduct(param);
    }

    /**
     * @param provinceCode;省份编码++
     * @param productId;产品ID
     * @param eparchyCode;地市编码
     * @param eleIdString;元素集合
     * */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public List<Map> queryPackageElementInfoByList(String provinceCode, String productId, String eparchyCode,
            String eleIdString) {
        Map param = new HashMap();
        param.put("PRODUCT_ID", productId);
        param.put("PRODUCT_MODE", "01");
        param.put("PROVINCE_CODE", provinceCode);
        param.put("EPARCHY_CODE", eparchyCode);
        param.put("ELEMENT_LIST", eleIdString);
        return n25Dao.queryAppProductByEleList(param);
    }

    /**
     * @param provinceCode;省份编码++
     * 获得继承资费列表（配表里）
     * */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private List<Map> queryCommParaSopN6AOP(String provinceCode) {
        Map param = new HashMap();
        param.put("SUBSYS_CODE", "CSM");
        param.put("PARAM_ATTR", "8541");
        param.put("PARA_CODE1", "ZZZZ");
        param.put("PARA_CODE2", "ZZZZ");
        param.put("PROVINCE_CODE", "ZZZZ");
        param.put("EPARCHY_CODE", provinceCode);
        return dao.queryCommParaSopN6AOP(param);
    }

    /**
     * @param provinceCode;省份编码++
     * @param productId;产品ID
     * @param elementId;元素id
     * */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private List<Map> queryPackageIdByNoDefaultElementId(String provinceCode, String productId, String elementId) {
        Map param = new HashMap();
        param.put("PRODUCT_ID", productId);
        param.put("PROVINCE_CODE", provinceCode);
        param.put("ELEMENT_ID", elementId);
        return n25Dao.queryPackageIdByNoDefaultElementId(param);
    }

    /**
     * @param commodityId;产品ID++
     * @param pid;
     * @param ptype;
     * @param provinceCode;省份编码
     * */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private List<Map> queryProductItemInfo(String commodityId, String pid, String ptype, String provinceCode) {
        Map serviceItemParam = new HashMap();
        serviceItemParam.put("PID", pid);
        serviceItemParam.put("PROVINCE_CODE", provinceCode);
        serviceItemParam.put("COMMODITY_ID", commodityId);
        serviceItemParam.put("PTYPE", ptype);
        return dao.queryProductItemInfo(serviceItemParam);
    }

    @SuppressWarnings("rawtypes")
    private void countStartDateAndEndDate(Map detailProduct, String startDate, String oldStartDate, String endDate,
            String oldEndDate) {
        try
        {

            String endOffSet = String.valueOf(detailProduct.get("END_OFFSET"));
            String enableTag = String.valueOf(detailProduct.get("ENABLE_TAG"));// 0-绝对时间（看开始时间） 1-相对时间
            String endEnableTag = String.valueOf(detailProduct.get("END_ENABLE_TAG"));// 0-绝对时间（看开始时间） 1-相对时间
            String strStartUnit = String.valueOf(detailProduct.get("START_UNIT"));// '0:天 1:自然天 2:月 3:自然月 4:年
            // 5:自然年';
            String endUnit = String.valueOf(detailProduct.get("END_UNIT"));
            endUnit = "null".equals(endUnit) ? "0" : endUnit;
            String startOffset = String.valueOf(detailProduct.get("START_OFFSET"));// 生效偏移时间
            if ("null".equals(enableTag) || "null".equals(startOffset) || "null".equals(strStartUnit))
            {
                startDate = oldStartDate;
            }
            else
            {
                startDate = GetDateUtils.getSpecifyDateTime(Integer.parseInt(enableTag),
                        Integer.parseInt(strStartUnit), oldStartDate, Integer.parseInt(startOffset));
            }

            if ("null".equals(enableTag) || "null".equals(strStartUnit) || "null".equals(startOffset))
            {//避免下面startOffset转换报错
                startOffset = "0";
            }

            if ("null".equals(endEnableTag) || "null".equals(endOffSet) || "null".equals(endUnit)
                    || "0".equals(endEnableTag))
            {
                endDate = oldEndDate;
            }
            else
            {
                endDate = GetDateUtils.getSpecifyDateTime(Integer.parseInt(endEnableTag), Integer.parseInt(endUnit),
                        oldStartDate, Integer.parseInt(endOffSet) + Integer.parseInt(startOffset));
                endDate = GetDateUtils.getSpecifyDateTime(1, 6, endDate, -1); // 结束月最后一天
            }
        }
        catch (Exception e)
        {
            throw new EcAopServerBizException("9999", "元素时间处理出错：" + e.getMessage());
        }
    }

    /**
     * tradeProduct
     * tradeSvc
     * tradeSp
     * tradeDiscnt
     * tradeSubItem
     * getSeqFromCb
     * */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void getCBItemIdByList(Exchange exchange, Map msg, Map extObject, Map baseObject) {
        Map tradeProduct = (Map) extObject.get("tradeProduct");
        Map tradeSvc = (Map) extObject.get("tradeSvc");
        Map tradeSp = (Map) extObject.get("tradeSp");
        Map tradeDiscnt = (Map) extObject.get("tradeDiscnt");
        Map tradeSubItem = (Map) extObject.get("tradeSubItem");
        int i = 1;
        i = i + getItemIdNumber(tradeProduct) + getItemIdNumber(tradeSvc) + getItemIdNumber(tradeSp)
                + getItemIdNumber(tradeDiscnt) + getItemIdNumber(tradeSubItem);
        System.out.println("change_product_itemId_i:" + i);
        // 防止对List操作时报UnsupportedOperationException的错
        List itemList = new ArrayList(GetSeqUtil.getSeqFromCb(pmp[2], ExchangeUtils.ofCopy(exchange, msg),
                "seq_item_id", i));
        System.out.println("change_product_itemIdList:" + itemList);
        String tradeId = itemList.get(0) + "";
        baseObject.put("tradeId", tradeId);
        baseObject.put("subscribeId", tradeId);
        msg.put("ordersId", tradeId);
        itemList.remove(tradeId);
        putItemIdIntoTrade(tradeProduct, itemList);
        putItemIdIntoTrade(tradeSvc, itemList);
        putItemIdIntoTrade(tradeSp, itemList);
        putItemIdIntoTrade(tradeDiscnt, itemList);
        putItemIdIntoTrade(tradeSubItem, itemList);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void putItemIdIntoTrade(Map trade, List idList) {
        if (null == trade || trade.size() == 0)
            return;
        if (null == idList || idList.size() == 0)
            return;
        List<Map> itemList = (List) trade.get("item");
        if (null == itemList || itemList.size() == 0)
            return;
        for (Map m : itemList)
        {
            if ("need".equals(m.get("itemId") + ""))
            {
                String id = idList.get(0) + "";
                m.put("itemId", id);
                idList.remove(id);
            }
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private int getItemIdNumber(Map trade) {
        int i = 0;
        if (null == trade || trade.size() == 0)
            return i;
        List<Map> itemList = (List) trade.get("item");
        if (null == itemList || itemList.size() == 0)
            return i;
        for (Map m : itemList)
        {
            if ("need".equals(m.get("itemId") + ""))
            {
                i++;
            }
        }
        return i;
    }

    /**
     * 根据ATTR_CODE取出相应的ATTR_VALUE
     * @param attrCode
     * @param infoList
     * @return
     */
    @SuppressWarnings({ "rawtypes" })
    private String getValueFromItem(String attrCode, List<Map> infoList) {
        String attrValue = "";
        for (int i = 0; i < infoList.size(); i++)
        {
            Map productItemInfo = infoList.get(i);
            if ("U".equals(productItemInfo.get("PTYPE")))
            {
                if (attrValue == "" && attrCode.equals(productItemInfo.get("ATTR_CODE")))
                {
                    attrValue = (String) productItemInfo.get("ATTR_VALUE");
                }
            }
        }
        return attrValue;
    }
}
