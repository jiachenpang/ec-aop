package com.ailk.ecaop.biz.sub;
/**
 * Created by Liu JiaDi on 2016/10/9.
 */

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.product.DateUtils;
import com.ailk.ecaop.biz.product.DealCbssProduct;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.*;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用于处理融合用户服务变更
 * 三户接口 虚拟用户、成员用户预提交、正式提交
 *
 * @auther Liu JiaDi
 * @create 2016_10_09_15:28
 */
@EcRocTag("mixProductChg")
public class MixProductChgProcessors extends BaseAopProcessor implements ParamsAppliable {
    LanOpenApp4GDao lanDao = new LanOpenApp4GDao();
    DealCbssProduct dao = new DealCbssProduct();
    LanUtils lan = new LanUtils();
    private static final String[] EXT_TRAFFIC = {"tradeProductType", "tradeProduct", "tradeDiscnt", "tradeSvc"};
    private static final String[] PARAM_ARRAY = {
            "ecaop.masb.chph.gifa.ParametersMapping",
            "ecaop.trade.cbss.checkUserParametersMapping",
            "ecaop.mxcg.preSub.ParametersMapping",
            "ecaop.trades.sccc.cancel.paramtersmapping"};
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[4];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map param = new HashMap();
        param.put("province", (msg.get("province")));
        param.put("city", msg.get("city"));
        String provinceCode = "00" + msg.get("province");
        msg.put("provinceCode", provinceCode);
        param.put("provinceCode", provinceCode);
        List<Map> eparchyCoderesult = lanDao.queryEparchyCode(param);
        if (eparchyCoderesult == null || eparchyCoderesult.size() == 0) {
            throw new EcAopServerBizException("9999", "地市转换失败");
        }
        String eparchyCode = (String) (eparchyCoderesult.get(0).get("PARAM_CODE"));
        msg.put("eparchyCode", eparchyCode);
        msg.put("date", GetDateUtils.getDate());
        String orderid = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, msg),
                "seq_trade_id", 1).get(0);
        msg.put("tradeId", orderid);
        msg.put("operTypeCode", "0");
        //调用三户接口
        getThreePartInfo(exchange);
        String recomPersonId = (String) msg.get("recomPersonId");
        if (StringUtils.isEmpty(recomPersonId)) {
            msg.put("recomPersonId", msg.get("operatorId"));
        }
        String recomPersonName = (String) msg.get("recomPersonName");
        if (StringUtils.isEmpty(recomPersonName)) {
            msg.put("recomPersonName", msg.get("custName"));
        }
        //虚拟用户预提交
        Map mixMap = preMixNumberInfo(exchange, msg);
        //成员用户预提交
        Map phoneMap = prePhoneNumberInfo(exchange, msg);
        //正式提交接口
        orderSub(mixMap, phoneMap, exchange);
    }

    private void orderSub(Map mixMap, Map phoneMap, Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        //准备参数
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
     * 虚拟用户申请
     */
    private Map preMixNumberInfo(Exchange exchange, Map msg) throws Exception {
        Exchange copyExchange = ExchangeUtils.ofCopy(exchange, msg);
        Map body = copyExchange.getIn().getBody(Map.class);
        Map copymMsg = (Map) body.get("msg");
        Map ext = new HashMap();
        copymMsg.put("base", preBaseData(msg, exchange.getAppCode(), true));
        ext.put("tradeUser", preDataForTradeUser(msg));
        ext.put("tradeItem", preDataForTradeItem(msg, true));
        ext.put("tradeSubItem", preDataForTradeSubItem(msg, true));
        msg.put("ext", ext);
        lan.preData(pmp[2], copyExchange);
        CallEngine.wsCall(copyExchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.trades.mxcg.preSub.template", copyExchange);

        Map retMap = copyExchange.getOut().getBody(Map.class);
        List<Map> rspInfoList = (ArrayList<Map>) retMap.get("rspInfo");
        if (IsEmptyUtils.isEmpty(rspInfoList)) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "核心系统未返回订单信息.");
        }
        return retMap;
    }

    /**
     * 成员用户申请
     */
    private Map prePhoneNumberInfo(Exchange exchange, Map msg) throws Exception {
        Exchange copyExchange = ExchangeUtils.ofCopy(exchange, msg);
        Map body = copyExchange.getIn().getBody(Map.class);
        Map copymMsg = (Map) body.get("msg");
        Map ext = new HashMap();
        copymMsg.put("base", preBaseData(msg, exchange.getAppCode(), false));
        ext.put("tradeItem", preDataForTradeItem(msg, false));
        ext.put("tradeSubItem", preDataForTradeSubItem(msg, false));
        List itemList = GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, msg), "seq_item_id", 2);
        copymMsg.put("itemId", itemList.get(0));
        copymMsg.put("svcItemId", itemList.get(1));
        try {
            ext.putAll(preExt(copymMsg));
        } catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", e.getMessage());
        }
        copymMsg.put("ext", ext);
        lan.preData(pmp[2], copyExchange);
        CallEngine.wsCall(copyExchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.trades.mxcg.preSub.template", copyExchange);

        Map retMap = copyExchange.getOut().getBody(Map.class);
        List<Map> rspInfoList = (ArrayList<Map>) retMap.get("rspInfo");
        if (IsEmptyUtils.isEmpty(rspInfoList)) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "核心系统未返回订单信息.");
        }
        return retMap;
    }

    /**
     * 准备EXT节点
     * 主要涉及4个大节点
     * tradeProductType
     * tradeProduct
     * tradeDiscnt
     * tradeItem
     *
     * @param msg
     * @return
     * @throws Exception
     */
    private Map preExt(Map msg) throws Exception {
        Map<String, Map<String, List>> ext = Maps.newHashMap();
        List<Map> productList = (List<Map>) msg.get("productInfo");
        List<Map> threePartInfo = (List<Map>) msg.get("exitProduct");
        for (Map product : productList) {
            Map singleProduct = preSingleProduct(msg, product, threePartInfo);
            for (String key : EXT_TRAFFIC) {
                Map temp = (Map) singleProduct.get(key);
                if (IsEmptyUtils.isEmpty(temp)) {
                    continue;
                }
                List itemList = (ArrayList<Map>) temp.get("item");
                if (IsEmptyUtils.isEmpty(itemList)) {
                    continue;
                }
                Object extItem = ext.get(key);
                List extItemList = null == extItem ? new ArrayList() : (ArrayList) ((Map) extItem).get("item");
                extItemList.addAll(itemList);
                ext.put(key, MapUtils.asMap("item", extItemList));
            }
        }
        return ext;
    }

    /**
     * 针对单个产品进行信息准备
     *
     * @param msg
     * @param product
     * @return
     * @throws Exception
     */
    private Map<String, Map<String, List>> preSingleProduct(Map msg, Map product, List<Map> threePartInfo) throws Exception {
        Map singleProduct = Maps.newHashMap();
        Object optType = product.get("optType");
        if (IsEmptyUtils.isEmpty(optType)) {// 默认为订购
            optType = "00";
        }
        Object productId = product.get("productId");

        List<Map> packageElement = (List<Map>) product.get("packageElement");
        List<Map> discntList = new ArrayList<Map>();

        // 校验产品是否存在、获取开始结束时间偏移
        Object province = msg.get("province");

        List<Map> productInfoList = dao.qryNoErrProductInfo(MapUtils.asMap("COMMODITY_ID", productId, "PROVINCE_CODE", province));
        if (IsEmptyUtils.isEmpty(productInfoList)) {
            productInfoList = dao.qryNoErrProductInfo(MapUtils.asMap("COMMODITY_ID", productId, "PROVINCE_CODE", "00" + province));
            if (IsEmptyUtils.isEmpty(productInfoList)) {
                throw new EcAopServerBizException("9999", "根据产品编码" + productId + "未查询到相应产品信息");
            }
        }
        Map productInfo = productInfoList.get(0);
        Object productMode = productInfo.get("PRODUCT_MODE");
        msg.put("PRODUCT_MODE",productMode);
        String productStartDate = dealProductStartDate(optType, product.get("enableTag"));
        String productEndDate = dealProductEndDate(productInfo, optType, productStartDate);
        List<Map> serviceList = new ArrayList<Map>();
        Map inMap = new HashMap();
        if (IsEmptyUtils.isEmpty(packageElement)) {
            packageElement = preNoPackageElement(msg, productId);
            if (IsEmptyUtils.isEmpty(packageElement)) {
                throw new EcAopServerBizException("9999", "根据产品[" + productId + "]未获取到附加包及包元素信息!");
            }
        }
        String[] copyFromMsg = {"itemId", "mainProduct", "userId"};
        for (Map element : packageElement) {
          /*  String elementId = (String) element.get("elementId");
            if (StringUtils.isNotEmpty(elementId)) {
                element.put("ELEMENT_ID", elementId);
            }
            String packageId = (String) element.get("packageId");
            if (StringUtils.isNotEmpty(packageId)) {
                element.put("PACKAGE_ID", packageId);
            }*/
            Map temp = new HashMap();
            temp.put("optType", optType);
            temp.put("PROVINCE_CODE", "00" + province);
            temp.put("COMMODITY_ID", productId);
            temp.put("EPARCHY_CODE", msg.get("eparchyCode"));
            temp.put("PACKAGE_ID", element.get("packageId"));
            temp.put("ELEMENT_ID", element.get("elementId"));
            temp.put("enableTag", product.get("enableTag"));
            temp.put("productMode", productMode);
            MapUtils.arrayPut(temp, msg, copyFromMsg);
            discntList.add(qryProductSingleElementInfo(temp, productStartDate, productEndDate));
            // 判断该资费是否有关联的服务
            inMap.put("ELEMENT_ID", element.get("elementId").toString());
            List serviceIdList = lanDao.selectServiceTraffic(inMap);
            if (!IsEmptyUtils.isEmpty(serviceIdList)) {
                String serviceId = serviceIdList.get(0).toString();
                inMap.put("PRODUCT_ID", productId);
                inMap.put("ELEMENT_ID", serviceId);
                inMap.put("PACKAGE_ID", element.get("packageId"));
                inMap.put("svcItemId", msg.get("svcItemId"));
                inMap.put("PROVINCE_CODE", "00" + msg.get("province"));
                inMap.put("EPARCHY_CODE", msg.get("eparchyCode"));
                serviceList.add(preServiceItem(inMap, msg.get("userId")));
            }
        }
        singleProduct.putAll(MapUtils.asMap("tradeDiscnt", MapUtils.asMap("item", discntList)));
        // 如果存在多个资费,开始时间应该取最早的,结束时间应该取最晚的
        String productStartTime = discntList.get(0).get("startDate").toString();
        String productEndTime = discntList.get(0).get("endDate").toString();
        for (Map dis : discntList) {
            productStartTime = DateUtils.min(productStartTime, dis.get("startDate").toString());
            productEndTime = DateUtils.max(productEndTime, dis.get("endDate").toString());
        }

        boolean isHaveThisProduct = false;
        if (!IsEmptyUtils.isEmpty(threePartInfo)) {
            for (Map prod : threePartInfo) {
                if (productId.equals(prod.get("productId"))) {
                    isHaveThisProduct = true;
                    break;
                }
            }
        }
        for (Map dis : discntList) {
            if ("01,20".contains(dis.get("productMode")+"") && !isHaveThisProduct) {
                List<Map> tradeProduct = new ArrayList<Map>();
                tradeProduct.add(preTradeProductItem(dis, productStartTime, productEndTime));
                singleProduct.putAll(MapUtils.asMap("tradeProduct", MapUtils.asMap("item", tradeProduct)));
                List<Map> tradeProductType = new ArrayList<Map>();
                Map productType = preTradeProductTypeItem(dis, productStartTime, productEndTime);
                productType.put("userId", msg.get("userId"));
                tradeProductType.add(productType);
                singleProduct.putAll(MapUtils.asMap("tradeProductType", MapUtils.asMap("item", tradeProductType)));
            }
        }
        singleProduct.putAll(MapUtils.asMap("tradeSvc", MapUtils.asMap("item", serviceList)));
        return singleProduct;
    }

    /**
     * 根据产品获取默认资费属性,外围系统不下发资费时调用
     *
     * @param inMap
     * @param productId
     * @return
     */
    private List<Map> preNoPackageElement(Map inMap, Object productId) {
        Map param = new HashMap();
        param.put("PROVINCE_CODE", "00" + inMap.get("province"));
        param.put("COMMODITY_ID", productId);
        param.put("EPARCHY_CODE", inMap.get("eparchyCode"));
        return dao.qryDefaultPackageElement(param);
    }

    /**
     * 校验单个产品的单个元素
     * 针对单个元素拼装节点，放在MAP，由调用者进行组装
     *
     * @param inMap
     * @return
     * @throws Exception
     */
    private Map qryProductSingleElementInfo(Map inMap, String productStartDate,
                                            String productEndDate) throws Exception {// 将各个属性放在一个MAP，后期处理
        Object productId = inMap.get("COMMODITY_ID");// 欲变更的产品
        Object mainProduct = inMap.get("mainProduct");// 当前主产品
        Object productMode = inMap.get("productMode");// 当前产品类型
        if ("00".equals(productMode)) {
            if (!mainProduct.equals(productId)) {
                throw new EcAopServerBizException("9999", "流量包订购业务中,不允许变更主套餐!原主套餐编码[" + mainProduct + "],新主套餐编码["
                        + productId + "]");
            }
            return preDiscntItem(inMap, productStartDate, productEndDate);
        } else if ("01，20".contains(productMode+"")) {
            if (null == inMap.get("COMMODITY_ID")) {
                if ("01".equals(inMap.get("optType"))) {
                    throw new EcAopServerBizException("9999", "产品[" + productId + "]在CBSS没有订购记录,无法退订!");
                }
                return preDiscntItem(inMap, productStartDate, productEndDate);
            }
            return preDiscntItem(inMap, productStartDate, productEndDate);
        } else {
            throw new EcAopServerBizException("9999", "产品模式有误,[" + productId + "]预期为[00,01,20]" + ",实际为[" + productMode
                    + "]!");
        }
    }

    /**
     * 处理DISCNT_ITEM返回ITEM
     *
     * @param inMap
     * @return
     * @throws Exception
     */
    private Map preDiscntItem(Map inMap, String productStartDate, String productEndDate)
            throws Exception {
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("id", inMap.get("userId"));
        item.put("idType", "1");
        item.put("userIdA", "-1");
        item.put("productId", inMap.get("COMMODITY_ID"));
        item.put("packageId", inMap.get("PACKAGE_ID"));
        item.put("discntCode", inMap.get("ELEMENT_ID"));
        item.put("productMode", inMap.get("productMode"));
        item.put("specTag", "0");// 0-正常产品优惠
        String startDate = DateUtils.getDate();
        Object enableTag = inMap.get("enableTag");
        if ("2".equals(enableTag)) {
            startDate = DateUtils.getNextMonthFirstDay();
        }
        Object optType = inMap.get("optType");
        inMap.put("DISCNT_CODE", inMap.get("ELEMENT_ID"));
        inMap.putAll(dao.qryDiscntAttr(inMap));
        startDate = dealDiscntStartDate(inMap, startDate, optType, enableTag);
        String endDate = dealDiscntEndDate(inMap, startDate, optType);
        item.put("startDate", DateUtils.max(startDate, productStartDate));
        item.put("endDate", DateUtils.min(endDate, productEndDate));
        item.put("modifyTag", "00".equals(optType) ? "0" : "1");
        item.put("optType", optType);
        item.put("enableTag", inMap.get("enableTag"));
        return item;
    }

    /**
     * 处理SERVICE_ITEM返回ITEM
     *
     * @param inMap
     * @return
     * @throws Exception
     */
    private Map preServiceItem(Map inMap, Object userId) throws Exception {
        List svcList = lanDao.queryProductSvcInfoByElementIdProductId(inMap);
        Map svcMap = (Map) svcList.get(0);
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("userId", userId);
        item.put("serviceId", svcMap.get("ELEMENT_ID"));
        item.put("modifyTag", "0");
        item.put("startDate", GetDateUtils.getDate());
        item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
        item.put("itemId", inMap.get("svcItemId"));
        item.put("productId", svcMap.get("PRODUCT_ID"));
        item.put("packageId", svcMap.get("PACKAGE_ID"));
        item.put("userIdA", "-1");

        return item;
    }

    /**
     * 处理资费的结束时间
     *
     * @param inMap
     * @param startDate
     * @param optType   00：订购；01：退订
     * @return
     * @throws Exception
     */
    private String dealDiscntEndDate(Map inMap, String startDate, Object optType) throws Exception {
        // TradeManagerUtils.getDiscntEffectTime
        if (!"00".equals(optType)) {// 产品退订，开始时间是次月，结束时间是当前
            return DateUtils.getDate();
        }
        String endOffset = inMap.get("END_OFFSET") + "";// 失效效偏移时间
        String endUnit = inMap.get("END_UNIT") + "";// '0:天 1:自然天 2:月 3:自然月 4:年 // 5:自然年'
        String endEnableTag = inMap.get("END_ENABLE_TAG") + "";// 针对结束时间 0-绝对时间（看开始时间） 1-相对时间
        String enableTag = inMap.get("ENABLE_TAG") + "";
        String endAbsoluteDate = inMap.get("END_ABSOLUTE_DATE") + "";// END_ENABLE_TAG为1时，需要才发此结束时间
        String startOffset = inMap.get("START_OFFSET") + "";// 生效偏移时间
        if ("0".equals(enableTag)) {
            startOffset = "0";
        }
        if (!"null".equals(endEnableTag) && "0".equals(endEnableTag) && !"null".equals(endAbsoluteDate)) {
            return endAbsoluteDate;
        }
        String endDate = MagicNumber.CBSS_DEFAULT_EXPIRE_TIME;
        if (!"null".equals(endOffset) && !"null".equals(endUnit) && "1".equals(endEnableTag)) {
            startDate = GetDateUtils.transDate(startDate, 19);
            endDate = GetDateUtils.getSpecifyDateTime(Integer.parseInt(endEnableTag), Integer.parseInt(endUnit),
                    startDate, Integer.parseInt(endOffset) + Integer.parseInt(startOffset));
            // 结束月最后一天
            endDate = GetDateUtils.getSpecifyDateTime(1, 6, endDate, -1);
            try {
                endDate = GetDateUtils.transDate(endDate, 14);
            } catch (Exception e) {
                throw new EcAopServerBizException("9999", "处理资费[" + inMap.get("DISCNT_CODE") + "]的结束时间失败,原因:"
                        + e.getMessage());
            }
        }
        return endDate;
    }

    /**
     * 处理资费的开始时间
     *
     * @param inMap
     * @param startDate
     * @param optType   00：订购；01：退订
     * @param enableTag 1:立即生效 2：次月生效
     * @return
     * @throws Exception
     */
    private String dealDiscntStartDate(Map inMap, String startDate, Object optType, Object enableTag)
            throws Exception {
        if (!"00".equals(optType)) {// 产品退订，开始时间是次月，结束时间是当前
            return DateUtils.getNextMonthFirstDay();
        }
        Object enableTagDB = inMap.get("ENABLE_TAG") + "";// 针对开始时间 0-绝对时间（看开始时间） 1-相对时间(看偏移量)
        if ("0".equals(enableTagDB)) {
            Object startAbsoluteDate = inMap.get("START_ABSOLUTE_DATE");
            if (IsEmptyUtils.isEmpty(startAbsoluteDate)) {
                throw new EcAopServerBizException("9999", "资费[" + inMap.get("ELEMENT_ID") + "]绝对时间(开始)为空,请检查");
            }
            if ("1".equals(enableTag)) {
                return DateUtils.max(startAbsoluteDate.toString(), DateUtils.getDate());
            }
            return DateUtils.max(startAbsoluteDate.toString(), DateUtils.getNextMonthFirstDay());
        }
        if ("1".equals(enableTag)) {
            return DateUtils.getDate();
        }
        return DateUtils.getNextMonthFirstDay();
    }


    /**
     * 处理TRADE_PRODUCT返回ITEM
     *
     * @param inMap
     * @return
     */
    private Map preTradeProductItem(Map inMap, String productStartTime, String productEndTime) {
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("productMode", inMap.get("productMode"));
        item.put("brandCode", "4G00");// 此处暂时写死4G00,因目前只支撑CB业务
        item.put("productId", inMap.get("productId"));

        if ("00".equals(inMap.get("optType"))) {
            item.put("modifyTag", "0");
            item.put("startDate", productStartTime);
            item.put("endDate", productEndTime);
        } else {
            item.put("modifyTag", "1");
            item.put("startDate", DateUtils.getNextMonthFirstDay());
            item.put("endDate", DateUtils.getDate());
        }
        item.put("userIdA", "-1");
        return item;
    }

    /**
     * 处理TRADE_PRODUCT_TYPE返回ITEM
     *
     * @param inMap
     * @return
     */
    private Map preTradeProductTypeItem(Map inMap, String productStartTime, String productEndTime) {
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("productMode", inMap.get("productMode"));
        item.put("productId", inMap.get("productId"));
        item.put("productTypeCode", "4G000001");
        if ("00".equals(inMap.get("optType"))) {
            item.put("modifyTag", "0");
            item.put("startDate", productStartTime);
            item.put("endDate", productEndTime);
        } else {
            item.put("modifyTag", "1");
            item.put("startDate", DateUtils.getNextMonthFirstDay());
            item.put("endDate", DateUtils.getDate());
        }
        return item;
    }

    /**
     * 拼装正式提交参数
     *
     */
    private Map preOrderSubMessage(Map mixMap, Map phoneMap) {
        Map outMap=new HashMap();
        List<Map> subOrderSubReq = new ArrayList<Map>();
        int totalFee = 0;
        List<Map> rspInfo = (ArrayList<Map>) mixMap.get("rspInfo");
        outMap.put("provOrderId", rspInfo.get(0).get("bssOrderId"));
        outMap.put("orderNo", rspInfo.get(0).get("provOrderId"));
        List<Map> provinceOrderInfo = (ArrayList<Map>) rspInfo.get(0).get("provinceOrderInfo");
        if(!IsEmptyUtils.isEmpty(provinceOrderInfo)){
            totalFee = Integer.valueOf(provinceOrderInfo.get(0).get("totalFee").toString());
       }
        subOrderSubReq.add(dealFeelInfo(rspInfo));
        //移网订单信息
        List<Map> phoneRspInfo = (ArrayList<Map>) phoneMap.get("rspInfo");
        List<Map> phoneProvinceOrderInfo = (ArrayList<Map>) phoneRspInfo.get(0).get("provinceOrderInfo");
        if(!IsEmptyUtils.isEmpty(phoneProvinceOrderInfo)){
            totalFee = totalFee+Integer.valueOf(phoneProvinceOrderInfo.get(0).get("totalFee").toString());
        }
        subOrderSubReq.add(dealFeelInfo(phoneRspInfo));
        outMap.put("origTotalFee", totalFee);
        outMap.put("subOrderSubReq", subOrderSubReq);
        return outMap;
    }

    private Map dealFeelInfo(List<Map> rspInfo){
        Map subOrderSubMap = new HashMap();
        List<Map> provinceOrderInfo = (ArrayList<Map>) rspInfo.get(0).get("provinceOrderInfo");
        if(!IsEmptyUtils.isEmpty(provinceOrderInfo)){
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
    /**
     * 处理产品开始时间
     *
     * @param optType
     * @param enableTag
     * @return
     */
    private String dealProductStartDate(Object optType, Object enableTag) {
        if ("01".equals(optType) || "2".equals(enableTag)) {// enableTag-2:次月生效 optType-01：退订
            return GetDateUtils.getNextMonthFirstDayFormat();
        }
        return GetDateUtils.getDate();
    }

    /**
     * 处理产品结束时间
     */
    private String dealProductEndDate(Map productInfo, Object optType, String startDate) throws Exception {
        if ("01".equals(optType)) {
            return GetDateUtils.getMonthLastDayFormat();
        }
        String endOffset = productInfo.get("END_OFFSET") + "";// 失效效偏移时间
        String endUnit = productInfo.get("END_UNIT") + "";// '0:天 1:自然天 2:月 3:自然月 4:年 // 5:自然年'
        String endEnableTag = productInfo.get("END_ENABLE_TAG") + "";// 针对结束时间 0-绝对时间（看开始时间） 1-相对时间
        String endAbsoluteDate = productInfo.get("END_ABSOLUTE_DATE") + "";// END_ENABLE_TAG为1时，需要才发此结束时间
        // String enableTag = productInfo.get("ENABLE_TAG") + "";
        // String startOffset = productInfo.get("START_OFFSET") + "";// 生效偏移时间
        // if ("0".equals(enableTag)) {
        // startOffset = "0";
        // }
        if (!"null".equals(endEnableTag) && "0".equals(endEnableTag) && !"null".equals(endAbsoluteDate)) {
            return endAbsoluteDate;
        }
        String endDate = MagicNumber.CBSS_DEFAULT_EXPIRE_TIME;
        if (!"null".equals(endOffset) && !"null".equals(endUnit) && "1".equals(endEnableTag)) {
            startDate = GetDateUtils.transDate(startDate, 19);
            endDate = GetDateUtils.getSpecifyDateTime(Integer.parseInt(endEnableTag), Integer.parseInt(endUnit),
                    startDate, Integer.parseInt(endOffset));
            // 结束月最后一天
            endDate = GetDateUtils.getSpecifyDateTime(1, 6, endDate, -1);
            try {
                endDate = GetDateUtils.transDate(endDate, 14);
            }
            catch (Exception e) {
                throw new EcAopServerBizException("9999", "处理产品[" + productInfo.get("COMMODITY_ID") + "]的结束时间失败,原因:"
                        + e.getMessage());
            }
        }
        return endDate;
    }

    /**
     * 三户信息查询
     */
    private void getThreePartInfo(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        Map copymMsg = (Map) body.get("msg");
        copymMsg.put("tradeTypeCode", "9999");
        copymMsg.put("getMode", "1111111111100013111100000100001");
        body.put("msg", copymMsg);
        exchange.getIn().setBody(body);
        new LanUtils().preData(pmp[1], exchange);
        try {
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
            new LanUtils().xml2Json("ecaop.trades.cbss.threePart.template", exchange);
        } catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "调用三户信息失败" + e.getMessage());
        }

        Map checkUserMap = exchange.getOut().getBody(Map.class);
        List<Map> custInfo = (List<Map>) checkUserMap.get("custInfo");
        if (IsEmptyUtils.isEmpty(custInfo)) {
            throw new EcAopServerBizException("9999", "客户信息未返回");
        }
        List<Map> userInfo = (List<Map>) checkUserMap.get("userInfo");
        if (IsEmptyUtils.isEmpty(userInfo)) {
            throw new EcAopServerBizException("9999", "用户信息未返回");
        }
        List<Map> uuInfo = (List<Map>) userInfo.get(0).get("uuInfo");
        if (IsEmptyUtils.isEmpty(uuInfo)) {
            throw new EcAopServerBizException("9999", "三户接口未返回uuInfo信息");
        }
        List<Map> acctInfo = (List<Map>) checkUserMap.get("acctInfo");
        if (IsEmptyUtils.isEmpty(acctInfo)) {
            throw new EcAopServerBizException("9999", "账户信息为空");
        }
        List<Map> oldProductInfo = (List<Map>) userInfo.get(0).get("productInfo");
        if (null == oldProductInfo || 0 == oldProductInfo.size()) {
            throw new EcAopServerBizException("9999", "三户接口未返回产品信息");
        }
        for (Map temMap : uuInfo) {
            String relationTypeCode = (String) temMap.get("relationTypeCode");
            String endDate = (String) temMap.get("endDate");
            if ((relationTypeCode.startsWith("89") || relationTypeCode.startsWith("88")) &&
                    0 < endDate.compareTo(GetDateUtils.getNextMonthFirstDayFormat())) {
                copymMsg.put("mixUserId", temMap.get("userIdA"));
                copymMsg.put("mixNumber", temMap.get("serialNumberA"));
                copymMsg.put("mixProduct", temMap.get("productIdA"));
                copymMsg.put("userDiffCode", relationTypeCode);
            }
        }
        List<Map> exitProduct = new ArrayList<Map>();
        for (int i = 0; i < oldProductInfo.size(); i++) {
            String productInactiveTime = (String) oldProductInfo.get(i).get("productInactiveTime");
            String productMode = (String) oldProductInfo.get(i).get("productMode");
            String product = (String) oldProductInfo.get(i).get("productId");
            if ("00".equals(productMode) && 0 < productInactiveTime.compareTo(GetDateUtils.getDate())) {
                copymMsg.put("mainProduct", product);
            }
            if (0 < productInactiveTime.compareTo(GetDateUtils.getDate())) {

            }
            if (0 < productInactiveTime.compareTo(GetDateUtils.getDate())) {
                exitProduct.add(oldProductInfo.get(0));
            }

        }

        copymMsg.put("exitProduct", exitProduct);
        copymMsg.put("acctId", acctInfo.get(0).get("acctId"));
        copymMsg.put("custId", custInfo.get(0).get("custId"));
        copymMsg.put("custName", custInfo.get(0).get("custName"));
        copymMsg.put("userId", userInfo.get(0).get("userId"));
        body.put("msg", copymMsg);
        exchange.getIn().setBody(body);
    }


    //准备base参数
    private Map preBaseData(Map msg, String appCode, Boolean isMixNum) {
        try {
            String date = (String) msg.get("date");
            Map base = new HashMap();
            base.put("startDate", date);
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
            base.put("advancePay", "0");
            base.put("inModeCode", new ChangeCodeUtils().getInModeCode(appCode));
            base.put("custId", msg.get("custId"));
            base.put("custName", msg.get("customerName"));
            base.put("acctId", msg.get("acctId"));
            base.put("tradeStaffId", msg.get("operatorId"));
            base.put("usecustId", msg.get("custId"));
            base.put("checktypeCode", "0");
            base.put("termIp", "132.35.81.217");
            base.put("eparchyCode", msg.get("eparchyCode"));
            base.put("cityCode", msg.get("district"));
            base.put("remark", new PreDataProcessor().getRemark(msg));
            if (isMixNum) {
                base.put("subscribeId", msg.get("ordersId"));
                base.put("tradeId", msg.get("ordersId"));
                base.put("userDiffCode", msg.get("userDiffCode"));
                base.put("brandCode", "COMP");
                base.put("tradeTypeCode", "0110");
                base.put("netTypeCode", "00CP");
                base.put("userId", msg.get("mixUserId"));
                base.put("productId", msg.get("mixProduct"));
                base.put("serinalNamber", msg.get("mixNumber"));
            } else {
                base.put("subscribeId", msg.get("ordersId"));
                base.put("tradeId", msg.get("tradeId"));
                base.put("userDiffCode", "00");
                base.put("brandCode", "4G00");
                base.put("tradeTypeCode", "0340");
                base.put("netTypeCode", "50");
                base.put("userId", msg.get("userId"));
                base.put("productId", msg.get("mainProduct"));
                base.put("serinalNamber", msg.get("serialNumber"));
            }
            base.put("feeState", "0"); // FIXME:cb说这个字段不能为空,默认下发未收费
            return base;
        } catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装base节点报错");
        }
    }

    //拼装TRADE_USER
    private Map preDataForTradeUser(Map msg) {
        try {
            Map tradeUser = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", msg.get("mixUserId"));
            item.put("custId", msg.get("custId"));
            item.put("usecustId", msg.get("custId"));
            item.put("eparchyCode", msg.get("eparchyCode"));
            item.put("cityCode", msg.get("district"));
            //活动类型
            item.put("productTypeCode", "COMP");
            item.put("userPasswd", "123456");
            item.put("userTypeCode", "0");
            item.put("scoreValue", "0");
            item.put("creditClass", "0");
            item.put("basicCreditValue", "0");
            item.put("creditValue", "0");
            item.put("acctTag", "0");
            item.put("prepayTag", "0");// FIXME
            item.put("inDate", msg.get("date"));
            item.put("openDate", msg.get("date"));
            item.put("openMode", "0");
            item.put("openDepartId", msg.get("channelId"));
            item.put("openStaffId", msg.get("operatorId"));
            item.put("inDepartId", msg.get("channelId"));
            item.put("inStaffId", msg.get("operatorId"));
            item.put("removeTag", "0");
            item.put("userStateCodeset", "0");
            item.put("mputeMonthFee", "0");
            item.put("developDate", GetDateUtils.getDate());
            item.put("developStaffId", msg.get("recomPersonId"));
            item.put("developDepartId", msg.get("channelId"));
            item.put("inNetMode", "0");
            itemList.add(item);
            tradeUser.put("item", itemList);
            return tradeUser;
        } catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_USER节点报错" + e.getMessage());
        }
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[]{PARAM_ARRAY[i]});
        }
    }

    //拼装TRADE_ITEM
    private Map preDataForTradeItem(Map msg, Boolean isMixNum) {
        try {
            List<Map> Item = new ArrayList<Map>();
            Map tradeItem = new HashMap();
            Item.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", "1"));

            if (isMixNum) {
                Item.add(LanUtils.createTradeItem("ALONE_TCS_COMP_INDEX", "2"));
                Item.add(LanUtils.createTradeItem("DEVELOP_STAFF_ID", (String) msg.get("recomPersonId")));
                Item.add(LanUtils.createTradeItem("DEVELOP_DEPART_ID", (String) msg.get("channelId")));
            } else {
                Item.add(LanUtils.createTradeItem("COMP_DEAL_STATE", "4"));
                Item.add(LanUtils.createTradeItem("MAIN_USER_TAG", ""));
                Item.add(LanUtils.createTradeItem("OPER_CODE", "3"));
                Item.add(LanUtils.createTradeItem("PRE_START_HRS", ""));
                Item.add(LanUtils.createTradeItem("NO_BOOK_REASON", ""));
                Item.add(LanUtils.createTradeItem("BOOK_FLAG", "0"));
                Item.add(LanUtils.createTradeItem("PRE_START_TIME", ""));
                Item.add(LanUtils.createTradeItem("SUB_TYPE", "0"));
                Item.add(LanUtils.createTradeItem("REL_COMP_PROD_ID", (String) msg.get("mixProduct")));
                Item.add(LanUtils.createTradeItem("ALONE_TCS_COMP_INDEX", "1"));
            }
            tradeItem.put("item", Item);
            return tradeItem;
        } catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_ITEM节点报错" + e.getMessage());
        }
    }

    //拼装TRADE_SUB_ITEM
    private Map preDataForTradeSubItem(Map msg, Boolean isMixNum) {
        try {
            List<Map> Item = new ArrayList<Map>();
            Map tradeSubItem = new HashMap();
            String itemId = "";
            if (isMixNum) {
                itemId = (String) msg.get("mixUserId");
            } else {
                itemId = (String) msg.get("userId");
            }
            Item.add(lan.createTradeSubItemE("LINK_NAME", msg.get("custName"), itemId));
            Item.add(lan.createTradeSubItemE("LINK_PHONE", msg.get("serialNumber"), itemId));
            tradeSubItem.put("item", Item);
            return tradeSubItem;
        } catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_SUB_ITEM节点报错" + e.getMessage());
        }
    }
}
