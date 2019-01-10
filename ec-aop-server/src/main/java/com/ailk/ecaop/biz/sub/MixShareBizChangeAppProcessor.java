package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.log.elk.ELKAopLogger;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
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

/**
 * @author zhaok
 * @date 2017年08月18日
 */
@EcRocTag("MixShareBizChangeApp")
public class MixShareBizChangeAppProcessor extends BaseAopProcessor implements ParamsAppliable {

    private String ERROR_CODE = "9999";

    private DealNewCbssProduct n25Dao = new DealNewCbssProduct();
    LanUtils lan = new LanUtils();

    private String BOOK = "00";// 订购
    private String UNBOOK = "01";// 退订
    private String MAIN = "00";// 主产品
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[3];
    // 三户接口,流水号,预提交
    private static final String[] PARAM_ARRAY = {"ecaop.trade.cbss.checkUserParametersMapping",
            "ecaop.masb.chph.gifa.ParametersMapping", "ecaop.trades.sccc.cancelPre.paramtersmapping"};

    @Override
    public void process(Exchange exchange) throws Exception {

        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");

        // 查询三户
        checkThreeInfo(exchange, msg);
        String developStaffId = (String) (msg.get("recomPersonId") == null ?
                msg.get("operatorId") : msg.get("recomPersonId"));
        msg.put("developStaffId", developStaffId);
        String optstartDate = checkstartDate(msg);
        msg.put("optstartDate", optstartDate);
        // 融合变更预提交
        Map mixMap = preMixChangeInfo(exchange, msg);
        // 宽带提速预提交
        Map boandMap = preBoandSpeedUpInfo(exchange, msg);

        // 返回融合预提交订单号
        dealReturn(exchange, mixMap, msg);
    }

    /**
     * 通外围传的startType字段判断开始时间生效方式
     * by zhoushaoqiang
     */

    private String checkstartDate(Map inMap) {
        String startType = inMap.get("startType") + "";
        if ("1".equals(startType)) {
            //立即生效
            startType = GetDateUtils.getDate();
        }
        else if ("2".equals(startType)) {
            // 次月生效
            startType = GetDateUtils.getNextMonthFirstDayFormat();
        }
        else {
            startType = GetDateUtils.getNextMonthFirstDayFormat();
        }
        return startType;
    }

    /**
     * 处理返回
     * 
     * @param mixMap
     * @param mixMap2
     */
    private void dealReturn(Exchange exchange, Map mixMap, Map msg) {

        if ("13".equals(String.valueOf(msg.get("province")))) {
            Message out = new DefaultMessage();
            // 拼装返回参数
            Object provOrderId = ((ArrayList<Map>) mixMap.get("rspInfo")).get(0).get("bssOrderId");
            out.setBody(MapUtils.asMap("provOrderId", provOrderId, "bssOrderId", msg.get("ordersId")));
            exchange.getOut().setBody(out);
        }
        else {
            Message out = new DefaultMessage();
            Map outMap = new HashMap();
            // 拼装返回参数
            Object provOrderId = ((ArrayList<Map>) mixMap.get("rspInfo")).get(0).get("bssOrderId");
            outMap.put("provOrderId", provOrderId);
            outMap.put("bssOrderId", msg.get("ordersId"));
            out.setBody(outMap);
            exchange.setOut(out);
        }

    }

    /**
     * 宽带提速预提交
     * 
     * @param exchange
     * @param msg
     * @return
     * @throws Exception
     */
    private Map preBoandSpeedUpInfo(Exchange exchange, Map msg) throws Exception {
        // 先判断是否做宽带提速
        String band_PackageMode = String.valueOf(msg.get("packageMode"));
        if (!(band_PackageMode != null && band_PackageMode.contains("5"))) {
            return null;
        }
        Map body = exchange.getIn().getBody(Map.class);
        List<Map> productInfoList = (List<Map>) msg.get("productInfo");

        Map base = new HashMap();
        Map ext = new HashMap();
        List<Map> addInfo = new ArrayList<Map>();// 宽带订购信息
        List<Map> delInfo = new ArrayList<Map>();// 宽带退订信息

        // 根据宽带号码获取宽带三户信息
        Map checkBandInfo = checkBroadBand(exchange, msg);
        Map bandUserInfo = (Map) checkBandInfo.get("userInfo");
        msg.put("bandUserId", bandUserInfo.get("userId"));

        // 遍历产品信息
        for (Map productInfo : productInfoList) {
            String productId = (String) productInfo.get("productId");
            String productMode = (String) productInfo.get("productMode");
            productMode = "1".equals(productMode) ? "00" : "01";// 1-主产品,2-附加产品
            msg.put("brandProductMode", productMode);
            String speed = (String) productInfo.get("speed");

            // 获取包元素节点
            List<Map> packageElementList = (List<Map>) productInfo.get("packageElement");
            for (Map packageElement : packageElementList) {
                String packageMode = (String) packageElement.get("packageMode");
                String optType = (String) packageElement.get("packageOptType");

                // 是否宽带提速
                if ("5".equals(packageMode)) {
                    if (IsEmptyUtils.isEmpty(speed)) {
                        throw new EcAopServerBizException(ERROR_CODE, "宽带提速时，请传入变更的宽带速率！");
                    }
                    // 订购
                    if ("00".equals(optType)) {
                        // 获取订购宽带产品ID
                        String bandProductId = productId;
                        msg.put("bandProductId", bandProductId); // 将需要订购的宽带产品ID放入msg
                        msg.put("speed", speed); // 将需要订购的速率放入msg

                        Map map = new HashMap();
                        map.put("PROVINCE_CODE", msg.get("provinceCode"));
                        map.put("PRODUCT_ID", bandProductId);
                        map.put("EPARCHY_CODE", msg.get("eparchyCode"));
                        map.put("PACKAGE_ID", packageElement.get("packageId"));
                        map.put("ELEMENT_ID", packageElement.get("elementId"));
                        addInfo = n25Dao.queryPackageElementInfo(map);
                        if (IsEmptyUtils.isEmpty(addInfo)) {
                            throw new EcAopServerBizException(ERROR_CODE, "订购的元素[" + packageElement.get("elementId")
                                    + "]在产品[" + bandProductId + "]下未查询到,请更换！");
                        }

                    } // 退订
                    else if ("01".equals(optType)) {
                        String bandProductId = productId;
                        List<Map> discntInfo = (List<Map>) bandUserInfo.get("discntInfo");
                        if (!IsEmptyUtils.isEmpty(discntInfo)) {
                            for (Map dis : discntInfo) {
                                String un_ProductId = (String) dis.get("productId");
                                if (bandProductId.equals(un_ProductId)) {
                                    delInfo.add(dis);
                                    productInfoResult(un_ProductId, msg);
                                }
                            }
                        }
                    }
                }
            }
        }
        ELKAopLogger.logStr(body.get("apptx") + "订购:" + addInfo + "，退订" + delInfo);
        // 拼装base节点
        base = preBaseData(msg, false);
        // 拼装TRADE_PRODUCT_TYPE节点
        ext.put("tradeProductType", preTradeProductType(msg, addInfo, delInfo, bandUserInfo));
        // 拼装TRADE_PRODUCT节点
        ext.put("tradeProduct", preTradeProduct(msg, addInfo, delInfo));
        // 拼装 TRADE_DISCNT节点
        ext.put("tradeDiscnt", preTradeDiscnt(msg, addInfo));
        // 拼装 TRADE_SVC节点
        ext.put("tradeSvc", preTradeSvc(msg, addInfo));
        // 拼装TRADE_OTHER节点
        ext.put("tradeOther", preTradeOther(msg, delInfo));
        // 拼装TRADE_OTHER节点
        ext.put("tradeItem", preTradeItem(msg, addInfo));
        // 拼装TRADE_SUB_ITEM节点
        ext.put("tradeSubItem", preTradeSubItem(msg));

        msg.put("ext", ext);
        msg.put("base", base);

        // 拼装msg
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        Exchange copyExchange = ExchangeUtils.ofCopy(exchange, msg);

        // 融合变更预提交
        lan.preData(pmp[2], copyExchange);
        CallEngine.wsCall(copyExchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.trades.mmvc.sUniTrade.template", copyExchange);

        Map retMap = copyExchange.getOut().getBody(Map.class);

        List<Map> rspInfoList = (ArrayList<Map>) retMap.get("rspInfo");
        if (IsEmptyUtils.isEmpty(rspInfoList)) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "核心系统未返回订单信息.");
        }
        return retMap;
    }

    /**
     * 拼装TRADE_SUB_ITEM节点
     * 
     * @param msg
     * @return
     */
    private Map preTradeSubItem(Map msg) {
        Map tradeSubItem = new HashMap();
        List<Map> item = new ArrayList<Map>();

        String itemId = TradeManagerUtils.getSequence((String) msg.get("eparchyCode"), "SEQ_ITEM_ID");
        item.add(lan.createTradeSubItemAll("0", "LINK_NAME", msg.get("custName"), itemId, msg.get("optstartDate"),
                MagicNumber.CBSS_DEFAULT_EXPIRE_TIME));
        item.add(lan.createTradeSubItemAll("0", "SPEED", msg.get("speed"), itemId, msg.get("optstartDate"),
                MagicNumber.CBSS_DEFAULT_EXPIRE_TIME));
        item.add(lan.createTradeSubItemAll("0", "LINK_PHONE", msg.get("serialNumber"), itemId, msg.get("optstartDate"),
                MagicNumber.CBSS_DEFAULT_EXPIRE_TIME));
        tradeSubItem.put("item", item);

        return tradeSubItem;
    }

    /**
     * 拼装TRADE_ITEM节点
     * 
     * @param msg
     * @param bandInfoList
     * @return
     */
    private Map preTradeItem(Map msg, List<Map> addInfo) {
        List<Map> item = new ArrayList<Map>();

        // 虚拟产品ID
        String mixProduct = (String) msg.get("mixProduct");
        String productTypeCode = "";
        String brandCode = "";
        String addproductId = "";
        try {
            // 宽带USER_ID
            String bandUserId = String.valueOf(msg.get("bandUserId"));
            if (!IsEmptyUtils.isEmpty(addInfo)) {
                for (Map bandInfo : addInfo) {
                    // 订购产品ID
                    addproductId = String.valueOf(bandInfo.get("PRODUCT_ID"));
                    // 当前活动 PRODUCT_TYPE_CODE
                    productTypeCode = String.valueOf(bandInfo.get("PRODUCT_TYPE_CODE"));
                    brandCode = String.valueOf(bandInfo.get("BRAND_CODE"));
                }
            }
            item.add(new LanUtils().createAttrInfoNoTime("COMP_DEAL_STATE", "4"));
            item.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", "1"));
            item.add(LanUtils.createTradeItem("OPER_CODE", "3"));
            item.add(LanUtils.createTradeItem("NEW_PRODUCT_ID", addproductId));
            item.add(LanUtils.createTradeItem("WORK_STAFF_ID", ""));
            item.add(LanUtils.createTradeItem("WORK_TRADE_ID", ""));
            item.add(LanUtils.createTradeItem("NO_BOOK_REASON", "null"));
            item.add(LanUtils.createTradeItem("WORK_TRADE_ID_SWITCH", "null"));
            item.add(LanUtils.createTradeItem("BOOK_FLAG", "0"));
            item.add(LanUtils.createTradeItem("NET_TYPE_CODE", "40"));
            item.add(LanUtils.createTradeItem("SUB_TYPE", "0"));
            item.add(LanUtils.createTradeItem("IS_SAME_CUST", msg.get("custId")));
            item.add(LanUtils.createTradeItem("MAIN_USER_TAG", ""));
            item.add(LanUtils.createTradeItem("WORK_DEPART_ID", ""));
            item.add(LanUtils.createTradeItem("PRE_START_HRS", ""));
            item.add(LanUtils.createTradeItem("PRODUCT_TYPE_CODE", productTypeCode));
            item.add(LanUtils.createTradeItem("NEW_BRAND_CODE", brandCode));
            item.add(LanUtils.createTradeItem("PRE_START_TIME", ""));
            item.add(LanUtils.createTradeItem("REL_COMP_PROD_ID", mixProduct));
            item.add(new LanUtils().createAttrInfoNoTime("ALONE_TCS_COMP_INDEX", "1"));

            Map tradeMap = new HashMap();
            tradeMap.put("item", item);
            return tradeMap;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_OTHER节点报错" + e.getMessage());
        }
    }

    /**
     * 拼装TRADE_OTHER节点
     * 
     * @param msg
     * @param delInfo
     * @return
     */
    private Map preTradeOther(Map msg, List<Map> delInfo) {
        Map tradeOther = new HashMap();
        List<Map> tradeOtherList = new ArrayList<Map>();
        try {
            if (!IsEmptyUtils.isEmpty(delInfo)) {
                for (Map bandInfo : delInfo) {

                    // 退订产品
                    Map item = new HashMap();
                    item.put("xDatatype", "NULL");
                    item.put("rsrvValueCode", "NEXP");
                    item.put("rsrvValue", msg.get("bandUserId"));
                    item.put("rsrvStr1", bandInfo.get("productId"));
                    item.put("rsrvStr2", "00");
                    item.put("rsrvStr3", "-9");
                    item.put("rsrvStr4", msg.get("unBookProductTypeCode"));
                    item.put("rsrvStr5", msg.get("unBookProductTypeCode"));
                    item.put("rsrvStr6", "-1");
                    item.put("rsrvStr7", "0");
                    item.put("rsrvStr8", "");
                    item.put("rsrvStr9", msg.get("unBookBrandCode"));
                    item.put("rsrvStr10", msg.get("serialNumberB"));// 宽带号码
                    item.put("modifyTag", "1");
                    item.put("startDate", msg.get("productActiveTime"));// 活动开始时间
                    item.put("endDate", GetDateUtils.getMonthLastDayFormat()); // 当月最后一天
                    tradeOtherList.add(item);
                }
            }
            tradeOther.put("item", tradeOtherList);
            return tradeOther;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_OTHER节点报错" + e.getMessage());
        }
    }

    /**
     * 拼装 TRADE_SVC节点
     * 
     * @param msg
     * @param addInfo
     * @return
     */
    private Map preTradeSvc(Map msg, List<Map> addInfo) {
        Map tradeSvc = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        try {
            if (!IsEmptyUtils.isEmpty(addInfo)) {
                for (Map bandInfo : addInfo) {
                    // 订购产品
                    Map item = new HashMap();
                    item.put("xDatatype", "NULL");
                    item.put("serviceId", "40000");
                    item.put("modifyTag", "0");
                    item.put("startDate", msg.get("optstartDate"));// 下个月第一天
                    item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                    item.put("productId", msg.get("bandProductId"));
                    item.put("packgaeId", bandInfo.get("PACKAGE_ID"));
                    item.put("itemId", "");
                    item.put("userIdA", "-1");
                    itemList.add(item);
                }
            }
            tradeSvc.put("item", itemList);
            return tradeSvc;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_SVC节点报错" + e.getMessage());
        }
    }

    /**
     * 拼装 TRADE_DISCNT节点
     * @param msg
     * @param addInfo
     * @return
     */
    private Map preTradeDiscnt(Map msg, List<Map> addInfo) {
        Map tradeDiscnt = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        try {
            if (!IsEmptyUtils.isEmpty(addInfo)) {
                for (Map bandInfo : addInfo) {
                    // 订购产品
                    Map item = new HashMap();
                    item.put("xDatatype", "NULL");
                    item.put("id", msg.get("bandUserId"));
                    item.put("idType", "1");
                    item.put("userIdA", "-1");
                    item.put("productId", bandInfo.get("PRODUCT_ID"));
                    item.put("packgaeId", bandInfo.get("PACKAGE_ID"));
                    item.put("discntCode", bandInfo.get("ELEMENT_ID"));
                    item.put("specTag", "0");
                    item.put("relationTypeCode", "");
                    item.put("startDate", msg.get("optstartDate"));// 下月第一天0时0分0秒
                    item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                    item.put("modifyTag", "0");
                    item.put("itemId", "");
                    itemList.add(item);
                }
            }
            tradeDiscnt.put("item", itemList);
            return tradeDiscnt;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_DISCNT节点报错" + e.getMessage());
        }
    }

    /**
     * 拼装TRADE_PRODUCT节点
     * @param msg
     * @param addInfo
     * @param delInfo
     * @return
     */
    private Map preTradeProduct(Map msg, List<Map> addInfo, List<Map> delInfo) {
        try {
            Map tradeProduct = new HashMap();
            List<Map> itemList = new ArrayList<Map>();

            String productActiveTime = String.valueOf(msg.get("productActiveTime"));
            if (!IsEmptyUtils.isEmpty(addInfo)) {
                for (Map bandInfo : addInfo) {
                    Map item = new HashMap();
                    item.put("xDatatype", "NULL");
                    item.put("productMode", "00");
                    item.put("brandCode", bandInfo.get("BRAND_CODE"));
                    item.put("userIdA", "-1");
                    item.put("productId", bandInfo.get("PRODUCT_ID"));
                    item.put("itemId", "");
                    item.put("modifyTag", "0");
                    item.put("startDate", msg.get("optstartDate"));// 下个月第一天
                    item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                    itemList.add(item);
                }
            }
            if (!IsEmptyUtils.isEmpty(delInfo)) {
                for (Map bandInfo : delInfo) {
                    Map item = new HashMap();
                    item.put("xDatatype", "NULL");
                    item.put("productMode", "00");
                    item.put("brandCode", msg.get("unBookBrandCode"));
                    item.put("userIdA", "-1");
                    item.put("productId", bandInfo.get("productId"));
                    item.put("modifyTag", "1");
                    item.put("startDate", productActiveTime); // 产品的活动时间
                    item.put("endDate", GetDateUtils.getMonthLastDay()); // 获取当月最后一天
                    item.put("itemId", TradeManagerUtils.getSequence((String) msg.get("eparchyCode"), "SEQ_ITEM_ID"));
                    itemList.add(item);
                }
            }
            tradeProduct.put("item", itemList);

            return tradeProduct;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_PRODUCT节点报错" + e.getMessage());
        }
    }

    private void productInfoResult(String productId, Map msg) {
        Map inMap = new HashMap();
        inMap.put("PRODUCT_ID", productId);
        inMap.put("PRODUCT_MODE", msg.get("brandProductMode"));
        inMap.put("PROVINCE_CODE", msg.get("provinceCode"));
        inMap.put("EPARCHY_CODE", msg.get("eparchyCode"));
        Map productInfoResult = n25Dao.queryProductInfoByProductId(inMap);

        String unBookBrandCode = String.valueOf(productInfoResult.get("BRAND_CODE"));
        String unBookProductTypeCode = String.valueOf(productInfoResult.get("PRODUCT_TYPE_CODE"));
        msg.put("unBookBrandCode", unBookBrandCode);
        msg.put("unBookProductTypeCode", unBookProductTypeCode);
    }

    /**
     * 拼装TRADE_PRODUCT_TYPE节点
     * @param msg
     * @param addInfo
     * @param delInfo
     * @param bandUserInfo
     * @return
     */
    private Map preTradeProductType(Map msg, List<Map> addInfo, List<Map> delInfo, Map bandUserInfo) {

        Map tradeProductType = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        try {
            List<Map> productInfoList = (List<Map>) bandUserInfo.get("productInfo");
            msg.put("productTypeCode", bandUserInfo.get("productTypeCode"));

            if (!IsEmptyUtils.isEmpty(addInfo)) {
                for (Map bandInfo : addInfo) {
                    Map item = new HashMap();
                    item.put("xDatatype", "NULL");
                    item.put("userId", msg.get("bandUserId"));
                    item.put("productMode", "00");
                    item.put("productId", bandInfo.get("PRODUCT_ID"));
                    item.put("productTypeCode", bandInfo.get("PRODUCT_TYPE_CODE"));
                    item.put("modifyTag", "0");
                    item.put("startDate", msg.get("optstartDate"));// 下个月第一天
                    item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                    itemList.add(item);
                }
            }
            if (!IsEmptyUtils.isEmpty(delInfo)) {
                for (Map bandInfo : delInfo) {
                    Map item = new HashMap();
                    String unBook_ProductId = String.valueOf(bandInfo.get("productId"));

                    item.put("xDatatype", "NULL");
                    item.put("userId", msg.get("bandUserId"));
                    item.put("productMode", "00");
                    item.put("productId", unBook_ProductId);
                    item.put("productTypeCode", msg.get("unBookProductTypeCode"));
                    item.put("modifyTag", "1");
                    item.put("startDate", product_ActiveTime(msg, productInfoList, unBook_ProductId));
                    item.put("endDate", GetDateUtils.getMonthLastDayFormat());// 退订是当月最后一天
                    itemList.add(item);
                }
            }
            tradeProductType.put("item", itemList);

            return tradeProductType;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_PRODUCT_TYPE节点报错" + e.getMessage());
        }
    }

    private Object product_ActiveTime(Map msg, List<Map> productInfoList, String unBook_ProductId) {
        // 宽带原产品活动时间
        String productActiveTime = "";
        for (Map productInfo : productInfoList) {
            if (unBook_ProductId.equals(productInfo.get("productId"))) {
                productActiveTime = String.valueOf(productInfo.get("productActiveTime"));
                msg.put("productActiveTime", productActiveTime);
            }
        }
        return productActiveTime;
    }

    /**
     * 获取宽带信息
     * 
     * @param exchange
     * @param msg
     * @return
     * @throws Exception
     */
    private Map checkBroadBand(Exchange exchange, Map msg) throws Exception {
        // 获取宽带统一编码
        String serialNumberB = (String) msg.get("serialNumberB");
        if (IsEmptyUtils.isEmpty(serialNumberB)) {
            throw new EcAopServerBizException(ERROR_CODE, "业务异常:办理宽带提速时，需要下发【宽带统一编码】！");
        }
        Map checkBroadBand = MapUtils.asMap("serialNumber", serialNumberB, "tradeTypeCode", "0340",
                "getMode", "1111111111100013010000000100001", "serviceClassCode", "0000");
        String[] copyArray = {"operatorId", "province", "channelId", "city", "district", "channelType"};
        MapUtils.arrayPut(checkBroadBand, msg, copyArray);

        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, checkBroadBand);
        lan.preData(pmp[0], threePartExchange);// ecaop.trade.cbss.checkUserParametersMapping
        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);

        Map out = threePartExchange.getOut().getBody(Map.class);
        // 校验是否没有返回三户信息
        String[] infoKeys = new String[]{"userInfo", "custInfo", "acctInfo"};
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
     * 融合变更预提交
     * 
     * @param msg
     * @throws Exception
     */
    private Map preMixChangeInfo(Exchange exchange, Map msg) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);

        Map ext = new HashMap();
        Map base = new HashMap();

        // 拼装base节点
        base = preBaseData(msg, true);
        // 拼装TRADE_USER节点
        ext.put("tradeUser", preDataForTradeUser(msg));
        // 拼装 TRADE_DISCNT节点
        ext.put("tradeDiscnt", preDataForTradeDiscnt(msg));
        // 拼装TRADE_ITEM
        ext.put("tradeItem", preDataTradeItem(msg));
        // 拼装TRADE_SUB_ITEM
        ext.put("tradeSubItem", preDataTradeSubItem(msg));
        // 处理附加产品及默认附加包订购和退订
        dealProductInfo(msg, ext);

        msg.put("base", base);
        msg.put("ext", ext);

        // 拼装msg
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        Exchange copyExchange = ExchangeUtils.ofCopy(exchange, msg);

        // 融合变更预提交
        lan.preData(pmp[2], copyExchange);
        CallEngine.wsCall(copyExchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.trades.mmvc.sUniTrade.template", copyExchange);

        Map retMap = copyExchange.getOut().getBody(Map.class);

        List<Map> rspInfoList = (ArrayList<Map>) retMap.get("rspInfo");
        if (IsEmptyUtils.isEmpty(rspInfoList)) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "核心系统未返回订单信息.");
        }
        return retMap;
    }

    /**
     * 处理附加产品及默认附加包订购和退订
     * @param msg
     * @param ext 
     * @throws Exception 
     */
    private void dealProductInfo(Map msg, Map ext) throws Exception {

        List<Map> productTypeList = new ArrayList<Map>();
        List<Map> productList = new ArrayList<Map>();
        List<Map> discntList = new ArrayList<Map>();
        List<Map> svcList = new ArrayList<Map>();
        List<Map> spList = new ArrayList<Map>();

        String provinceCode = (String) msg.get("provinceCode");
        String eparchyCode = (String) msg.get("eparchyCode");
        String userId = (String) msg.get("userId");
        String acceptDate = GetDateUtils.transDate(GetDateUtils.getDate(), 19);

        List<Map> productInfoList = (List<Map>) msg.get("productInfo");
        for (Map productInfo : productInfoList) {
            String productMode = String.valueOf(productInfo.get("productMode")); //1：主产品 2：附加产品
            String optType = String.valueOf(productInfo.get("optType")); //00：订购；01：退订
            if (IsEmptyUtils.isEmpty(optType)) {
                return;
            }
            String productId = (String) productInfo.get("productId");
            if (!"2".equals(productMode)) {
                throw new EcAopServerBizException(ERROR_CODE, "目前只支持附加产品的订购和退订！");
            }

            String firstMonBillMode = "02";// 订购时默认首月全量全价
            if (!"1,2".contains(productMode)) {
                throw new EcAopServerBizException(ERROR_CODE, "产品 " + productId + "的产品编码有误");
            }
            productMode = "1".equals(productMode) ? "00" : "01";// 1-主产品,0-附加产品

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
                String brandCode = unBookBrandCode;

                Map paraMap = new HashMap();
                paraMap.put("productMode", unBookProductMode);
                paraMap.put("productId", unBookProductId);
                paraMap.put("productTypeCode", unBookProductTypeCode);
                paraMap.put("brandCode", unBookBrandCode);
                paraMap.put("modifyTag", "1"); // 退订
                paraMap.put("productStartDate", productStartDate(unBookProductId, msg));
                paraMap.put("productEndDate", GetDateUtils.getMonthLastDay());
                paraMap.put("eparchyCode", eparchyCode);
                paraMap.put("userId", userId);
                // 拼装退订的产品节点

                preProductItem(productList, productTypeList, paraMap);
            }

            if (BOOK.equals(optType)) { // 订购
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
                    List<Map> allProductInfo = new ArrayList<Map>();
                    if (!IsEmptyUtils.isEmpty(productInfoResult)) {
                        // 拼装产品默认属性的节点,放到下面统一处理
                        allProductInfo.addAll(productInfoResult);
                    }

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

                    if (!IsEmptyUtils.isEmpty(allProductInfo)) {
                        preProductInfo(allProductInfo, discntList, svcList, spList, "0", isFinalCode, productStartDate,
                                productEndDate, msg);
                    }

                }
            }
            discntList = NewProductUtils.newDealRepeat(discntList, new String[]{"productId", "packageId", "discntCode",
                    "modifyTag"});
            svcList = NewProductUtils.newDealRepeat(svcList, new String[]{"productId", "packageId", "serviceId",
                    "modifyTag"});
            spList = NewProductUtils.newDealRepeat(spList, new String[]{"productId", "packageId", "spServiceId",
                    "modifyTag"});
            productTypeList = NewProductUtils.newDealRepeat(productTypeList, new String[]{"productId", "productMode",
                    "productTypeCode", "modifyTag"});
            productList = NewProductUtils.newDealRepeat(productList, new String[]{"productId", "productMode",
                    "brandCode", "modifyTag"});

            ext.put("tradeProductType", preDataUtil(productTypeList));
            ext.put("tradeProduct", preDataUtil(productList));
            ext.put("tradeSp", preDataUtil(spList));
            ext.put("tradeSvc", preDataUtil(svcList));
            preDataForDiscnt(discntList, ext);
        }
    }

    private void preDataForDiscnt(List<Map> discntList, Map ext) {
        if (discntList != null) {
            Map tradeDiscntMap = (Map) ext.get("tradeDiscnt");
            List<Map> tradeDiscntList = (List<Map>) tradeDiscntMap.get("item");
            tradeDiscntList.addAll(discntList);
            ext.put("tradeDiscnt", MapUtils.asMap("item", tradeDiscntList));
        }
    }

    private Object preDataUtil(List<Map> dataList) {
        Map dataMap = new HashMap();
        dataMap.put("item", dataList);
        return dataMap;
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
                sp.put("endDate", "2050-12-31 23:59:59");
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

    private String productStartDate(String unBookProductId, Map msg) throws Exception {
        List<Map> oldproductInfo = (List<Map>) msg.get("oldproductInfo");
        for (Map oldpro : oldproductInfo) {
            String productId = String.valueOf(oldpro.get("productId"));
            if (unBookProductId.equals(productId)) {
                String productActiveTime = (String) oldpro.get("productActiveTime");
                return GetDateUtils.transDate(productActiveTime, 19);
            }
        }
        return GetDateUtils.getSysdateFormat();
    }

    /**
     * 拼装product和productType节点
     * @param productList
     * @param productTypeList
     * @param paraMap
     */
    private void preProductItem(List<Map> productList, List<Map> productTypeList, Map paraMap) {
        // 拼装产品节点
        Map productItem = new HashMap();
        productItem.put("userId", paraMap.get("userId"));
        productItem.put("productMode", paraMap.get("productMode"));
        productItem.put("productId", paraMap.get("productId"));
        productItem.put("productTypeCode", paraMap.get("productTypeCode"));
        productItem.put("brandCode", paraMap.get("brandCode"));
        productItem.put("itemId", TradeManagerUtils.getSequence((String) paraMap.get("eparchyCode"), "SEQ_ITEM_ID"));
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
     * 拼装tradeSubItem
     * 
     * @param msg
     * @return
     */
    private Map preDataTradeSubItem(Map msg) {
        try {
            List<Map> itemList = new ArrayList<Map>();
            Map tradeSubItem = new HashMap();
            String itemId = (String) msg.get("mixUserId");
            itemList.add(lan.createTradeSubItemB2(itemId, "LINK_NAME", msg.get("custName")));
            itemList.add(lan.createTradeSubItemB2(itemId, "LINK_PHONE", msg.get("serialNumber")));

            tradeSubItem.put("item", itemList);
            return tradeSubItem;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException(ERROR_CODE, "拼装TRADE_SUB_ITEM节点报错" + e.getMessage());
        }
    }

    /**
     * 拼装tradeItem
     * 
     * @param msg
     * @return
     */
    private Map preDataTradeItem(Map msg) {
        try {
            List<Map> item = new ArrayList<Map>();

            item.add(new LanUtils().createAttrInfoNoTime("DEVELOP_DEPART_ID", msg.get("channelId")));
            item.add(new LanUtils().createAttrInfoNoTime("DEVELOP_STAFF_ID", msg.get("developStaffId")));
            item.add(new LanUtils().createAttrInfoNoTime("ACTOR_ADDRESS", ""));
            item.add(new LanUtils().createAttrInfoNoTime("ALONE_TCS_COMP_INDEX", "2"));

            item.add(lan.createTradeItem("STANDARD_KIND_CODE", "1"));
            return MapUtils.asMap("item", item);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException(ERROR_CODE, "拼装TRADE_ITEM节点报错" + e.getMessage());
        }
    }

    /**
     * 拼装 tradeDiscnt节点
     * 循环产品信息，再遍历出单个节点，有退订和新增两种
     * 退订的资费与三户中的相同给予退订，订购的通过查库能查到就订购
     * 当包类型有5(宽带提速)，需要给cb单独传一个宽带业务的预提交报文。
     * 
     * @param msg
     * @return
     */
    private Map preDataForTradeDiscnt(Map msg) {
        try {

            Map tradeDiscnt = new HashMap();
            List<Map> preForDiscnt = new ArrayList<Map>();
            // 退订资费ID
            List<String> deleteEle = new ArrayList<String>();
            // 订购资费ID
            List<Map> addElementInfo = new ArrayList<Map>();
            // 外围传入产品信息
            List<Map> newProductInfoList = (List<Map>) msg.get("productInfo");
            if (IsEmptyUtils.isEmpty(newProductInfoList)) {
                throw new EcAopServerBizException(ERROR_CODE, "业务异常:发起方未下发产品信息,无法变更套餐");
            }
            // 主产品ID
            String mianProductId = "";
            Map userInfo = (Map) msg.get("userInfo");

            // 遍历产品信息
            for (Map newProductInfo : newProductInfoList) {
                // 产品模式 1 主产品 2 附加产品
                String productMode = (String) newProductInfo.get("productMode");
                String productId = (String) newProductInfo.get("productId");

                // 获取产品下附加包及包元素
                List<Map> newPackageElementList = (List<Map>) newProductInfo.get("packageElement");
                if (!IsEmptyUtils.isEmpty(newPackageElementList)) {
                    // 遍历包元素
                    for (Map newPackageElement : newPackageElementList) {
                        // 包类型
                        String packageMode = (String) newPackageElement.get("packageMode");
                        // 在宽带提速的时候做判断
                        msg.put("packageMode", packageMode);

                        if (!"12345".contains(packageMode)) {
                            throw new EcAopServerBizException(ERROR_CODE, "业务异常:【包类型下发错误】目前只支持D，而传进来的是：" + packageMode);
                        }
                        // 如果是宽带提交直接返回
                        if ("5".equals(packageMode)) {
                            continue;
                        }
                        // 退订类型 00订购|01退订
                        String optType = (String) newPackageElement.get("packageOptType");
                        // 包编号
                        String packageId = (String) newPackageElement.get("packageId");
                        // 元素编号
                        String elementId = (String) newPackageElement.get("elementId");
                        // 元素类型
                        String elementType = (String) newPackageElement.get("elementType");
                        if (!"D".equals(elementType)) {
                            throw new EcAopServerBizException(ERROR_CODE, "业务异常:【目前只支持资费类型,而下发的元素类型为：】"
                                    + elementType);
                        }

                        // 包类型为1-4时拼装融合变更，准备预提交
                        if ("01".equals(optType)) { // 退订
                            deleteEle.add(elementId);
                            continue;
                        }
                        else if ("00".equals(optType)) { // 订购
                            Map elem = new HashMap();
                            elem.put("PROVINCE_CODE", msg.get("provinceCode"));
                            elem.put("PRODUCT_ID", productId);
                            elem.put("EPARCHY_CODE", msg.get("eparchyCode"));
                            elem.put("PACKAGE_ID", packageId);
                            elem.put("ELEMENT_ID", elementId);
                            // 传入包ID查询包信息
                            List<Map> packageInfo = n25Dao.queryPackageElementInfo(elem);
                            if (!IsEmptyUtils.isEmpty(packageInfo)) {
                                if ("2".equals(productMode)) {
                                    // 如果是附加产品根据偏移量计算出中结束时间
                                    dealSubElementEndTime(packageInfo, elementId);
                                }
                                addElementInfo.addAll(packageInfo);
                            }
                            else {
                                throw new EcAopServerBizException(ERROR_CODE, "订购的元素[" + elementId
                                        + "]在产品[" + productId + "]下未查询到,请重试");
                            }
                        }
                    }
                }
            }
            // 处理退订资费
            preDeleteElementInfo(deleteEle, msg, userInfo, preForDiscnt);
            // 处理订购资费
            orderElementInfo(addElementInfo, msg, userInfo, preForDiscnt);
            // 去重
            preForDiscnt = NewProductUtils.newDealRepeat(preForDiscnt, new String[]{"productId", "packageId",
                    "discntCode", "modifyTag"});
            tradeDiscnt.put("item", preForDiscnt);
            return tradeDiscnt;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException(ERROR_CODE, "拼装TRADE_DISCNT节点报错" + e.getMessage());
        }
    }

    /**
     *
     * 处理附加产品中资费的结束时间
     * @param packageInfo
     * @param elementId 
     */
    private void dealSubElementEndTime(List<Map> packageInfo, String elementId) {
        TradeManagerUtils tradeManagerUtils = new TradeManagerUtils();
        Map discntDateMap = tradeManagerUtils.getDiscntEffectTime(elementId, GetDateUtils.getDate(),
                MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
        for (Map packageMap : packageInfo) {
            packageMap.put("activityTime", discntDateMap.get("monthLasttDay"));
            ELKAopLogger.logStr("附加产品的资费" + elementId + "的结束时间为：" + discntDateMap.get("monthLasttDay"));
        }
    }

    /**
     * 处理订购资费
     * 
     * @param addElementInfo
     * @param msg
     * @param userInfo
     */
    private void orderElementInfo(List<Map> addElementInfo, Map msg, Map userInfo, List<Map> preForDiscnt) {
        try {
            Map orderInfo = new HashMap();

            for (Map addElement : addElementInfo) {
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("id", msg.get("mixUserId"));
                item.put("idType", "1");
                item.put("userIdA", "-1");
                item.put("productId", addElement.get("PRODUCT_ID"));// 数据库获取
                item.put("packageId", addElement.get("PACKAGE_ID")); // 数据库获取
                item.put("discntCode", addElement.get("ELEMENT_ID"));// 数据库获取
                item.put("specTag", "0");
                item.put("relationTypeCode", "");
                item.put("startDate", msg.get("optstartDate")); //
                if (!IsEmptyUtils.isEmpty(addElement.get("activityTime"))) {
                    item.put("endDate", addElement.get("activityTime"));
                }
                else {
                    item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                }
                item.put("modifyTag", "0");
                preForDiscnt.add(item);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException(ERROR_CODE, "拼装处理订购资费节点报错:" + addElementInfo);
        }

    }

    /**
     * 处理退订资费
     * 
     * @param deleteEle
     * @param msg
     * @param userInfo
     * @return
     */
    private void preDeleteElementInfo(List<String> deleteEle, Map msg, Map userInfo, List<Map> preForDiscnt) {

        if (IsEmptyUtils.isEmpty(deleteEle)) {
            return;
        }
        try {
            // 获取用户原有资费信息
            List<Map> discntInfo = (List<Map>) userInfo.get("discntInfo");

            for (String delEle : deleteEle) {
                if (!IsEmptyUtils.isEmpty(discntInfo)) {
                    for (Map discnt : discntInfo) {
                        String discntCode = (String) discnt.get("discntCode");
                        // 判断资费ID不为空且与退订ID相同，给予退订
                        if (!IsEmptyUtils.isEmpty(discntCode) && delEle.equals(discntCode)) {
                            Map item = new HashMap();
                            item.put("xDatatype", "NULL");
                            item.put("id", msg.get("mixUserId"));
                            item.put("idType", "1");
                            item.put("userIdA", "-1");
                            item.put("productId", discnt.get("productId"));
                            item.put("packageId", discnt.get("packageId"));
                            item.put("discntCode", discnt.get("discntCode"));
                            item.put("specTag", "0");
                            item.put("relationTypeCode", "");
                            item.put("startDate", discnt.get("startDate"));
                            item.put("endDate", GetDateUtils.getDate());// TODO 根据报文样例下发
                            item.put("modifyTag", "1");
                            item.put("itemId",
                                    TradeManagerUtils.getSequence((String) msg.get("eparchyCode"), "SEQ_ITEM_ID"));
                            preForDiscnt.add(item);
                        }
                    }
                }
                else {
                    throw new EcAopServerBizException(ERROR_CODE, "处理退订资费失败，请核对退订的资费：" + deleteEle);
                }
            }

        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException(ERROR_CODE, "拼装处理退订资费节点报错" + e.getMessage());
        }
    }

    /**
     * 拼装tradeUser节点
     * 
     * @param msg
     * @return
     */
    private Map preDataForTradeUser(Map msg) {
        try {
            Map tradeUser = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userPasswd", "123456");
            item.put("userTypeCode", "0");
            item.put("scoreValue", "0");
            item.put("basicCreditValue", "0");
            item.put("creditValue", "0");
            item.put("acctTag", "0");
            item.put("prepayTag", "0");
            item.put("inDate", msg.get("optstartDate"));
            item.put("openDate", msg.get("optstartDate"));
            item.put("openMode", "0");
            item.put("openDepartId", msg.get("channelId"));
            item.put("openStaffId", msg.get("operatorId"));
            item.put("inDepartId", msg.get("channelId"));
            item.put("inStaffId", msg.get("operatorId"));
            item.put("removeTag", "0");
            item.put("userStateCodeset", "0");
            item.put("mputeMonthFee", "0");
            item.put("developDate", msg.get("optstartDate"));
            item.put("developStaffId", msg.get("developStaffId"));// 发展员工
            item.put("developDepartId", msg.get("channelId"));// 发展渠道
            item.put("inNetMode", "0");
            item.put("productTypeCode", "COMP");
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
     * BASE节点拼装
     * 
     * @param msg
     * @return
     */
    private Map preBaseData(Map msg, Boolean isSpeedUp) {
        try {
            Map base = new HashMap();
            String tradeId = (String) msg.get("tradeId");
            String sysDate = (String) msg.get("sysDate");

            base.put("subscribeId", tradeId);
            if (isSpeedUp) {
                base.put("tradeId", tradeId);
                base.put("tradeTypeCode", "0110");
                base.put("productId", msg.get("mixProduct"));
                base.put("brandCode", "COMP");
                base.put("userId", msg.get("mixUserId"));
                base.put("netTypeCode", "00CP");
                base.put("serinalNamber", msg.get("mixNumber"));
                base.put("userDiffCode", "8800");
            }
            else {
                base.put("tradeId", TradeManagerUtils.getSequence((String) msg.get("eparchyCode"), "SEQ_ITEM_ID"));
                base.put("tradeTypeCode", "0340");
                base.put("productId", msg.get("bandProductId"));// 需要订购的宽带产品ID
                base.put("brandCode", "GZKD");
                base.put("userId", msg.get("bandUserId"));// 宽带userId与移网的不同
                base.put("netTypeCode", "0040");
                base.put("serinalNamber", msg.get("serialNumberB"));// 宽带号码
                base.put("userDiffCode", "00");
            }
            base.put("startDate", sysDate);
            base.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            base.put("acceptDate", sysDate);
            base.put("nextDealTag", "Z");
            base.put("olcomTag", "0");
            base.put("inModeCode", "0");
            base.put("custId", msg.get("custId"));
            base.put("usecustId", msg.get("usecustId"));
            base.put("acctId", msg.get("acctId"));
            base.put("custName", msg.get("custName"));
            base.put("termIp", "10.124.0.6");
            base.put("eparchyCode", msg.get("eparchyCode"));
            base.put("cityCode", msg.get("district"));
            base.put("execTime", sysDate);
            base.put("operFee", "0");
            base.put("foregift", "0");
            base.put("advancePay", "0");
            base.put("feeState", "");
            base.put("feeStaffId", "");
            base.put("cancelTag", "0");
            base.put("checktypeCode", "8");
            base.put("chkTag", "0");
            base.put("actorName", "");
            base.put("actorCertTypeId", "");
            base.put("actorPhone", "");
            base.put("actorCertNum", "");
            base.put("contact", "");
            base.put("contactPhone", "");
            base.put("contactAddress", "");
            base.put("remark", "");

            return base;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException(ERROR_CODE, "拼装BASE节点报错" + e.getMessage());
        }
    }

    /**
     * 查询三户信息
     * 
     * @param exchange
     * @param msg
     * @return
     * @throws Exception
     */
    public void checkThreeInfo(Exchange exchange, Map msg) throws Exception {

        Map threePartMap = MapUtils.asMap("serialNumber", msg.get("serialNumber"), "tradeTypeCode", "9999",
                "getMode", "1111111111100013010000000100001", "serviceClassCode", "0000");
        String[] copyArray = {"operatorId", "province", "channelId", "city", "district", "channelType"};
        MapUtils.arrayPut(threePartMap, msg, copyArray);

        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
        lan.preData(pmp[0], threePartExchange);
        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);

        Map checkUserInfo = threePartExchange.getOut().getBody(Map.class);

        // 校验是否没有返回三户信息
        String[] infoKeys = new String[]{"userInfo", "custInfo", "acctInfo"};
        Map errorDetail = MapUtils.asMap("userInfo", "用户信息", "custInfo", "客户信息", "acctInfo", "账户信息");
        for (String infoKey : infoKeys) {
            if (IsEmptyUtils.isEmpty(checkUserInfo.get(infoKey))) {
                throw new EcAopServerBizException(ERROR_CODE, "调三户未返回" + errorDetail.get(infoKey));
            }
            msg.put(infoKey, ((List<Map>) checkUserInfo.get(infoKey)).get(0));
        }
        // 校验是否没返回老产品信息
        Map userInfo = (Map) msg.get("userInfo");
        Map custInfo = (Map) msg.get("custInfo");
        Map acctInfo = (Map) msg.get("acctInfo");
        // 原有产品
        List<Map> oldproductInfo = (List<Map>) userInfo.get("productInfo");
        if (IsEmptyUtils.isEmpty(oldproductInfo)) {
            throw new EcAopServerBizException("9999", "调三户未返回产品信息");
        }

        List<Map> uuInfos = (List<Map>) userInfo.get("uuInfo");
        if (IsEmptyUtils.isEmpty(uuInfos)) {
            throw new EcAopServerBizException(ERROR_CODE, "三户接口未返回uuInfo信息");
        }
        for (Map uuInfo : uuInfos) {
            String relationTypeCode = (String) uuInfo.get("relationTypeCode");
            String endDate = (String) uuInfo.get("endDate");
            // 判断只有89或88开头的时候，获取虚拟产品ID、虚拟号码、虚拟用户ID
            if ((relationTypeCode.startsWith("89") || relationTypeCode.startsWith("88"))
                    && 0 < endDate.compareTo(GetDateUtils.getNextMonthFirstDayFormat())) {
                // 虚拟号码
                msg.put("mixNumber", uuInfo.get("serialNumberA"));
                msg.put("mixUserId", uuInfo.get("userIdA"));
                msg.put("mixProduct", uuInfo.get("productIdA"));
                msg.put("relationTypeCode", relationTypeCode);
            }
        }

        String tradeId = (String) GetSeqUtil.getSeqFromCb(pmp[1], exchange, "seq_trade_id", 1).get(0);
        String userId = (String) userInfo.get("userId");
        String productId = (String) userInfo.get("productId");
        String usecustId = (String) userInfo.get("usecustId");
        String eparchyCode = (String) userInfo.get("eparchyCode");
        String custId = (String) custInfo.get("custId");
        String custName = (String) custInfo.get("custName");
        String acctId = (String) acctInfo.get("acctId");
        String provinceCode = "00" + msg.get("province");

        String sysDate = GetDateUtils.getDate();

        msg.putAll(MapUtils.asMap("userId", userId, "usecustId", usecustId, "acctId", acctId, "custId",
                custId, "custName", custName, "eparchyCode", eparchyCode, "tradeId", tradeId,
                "ordersId", msg.get("ordersId"), "operTypeCode", "0", "sysDate", sysDate,
                "provinceCode", provinceCode, "productId", productId, "oldproductInfo", oldproductInfo));
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[]{PARAM_ARRAY[i]});
        }
    }
}
