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
import com.google.common.collect.Maps;

@EcRocTag("trafficSub")
public class TrafficSubProcessors extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] EXT_TRAFFIC = { "tradeProductType", "tradeProduct", "tradeDiscnt", "tradeSvc" };
    private static final String[] PARAM_ARRAY = { "ecaop.trade.cbss.checkUserParametersMapping",
            "ecaop.masb.chph.gifa.ParametersMapping", "ecaop.trades.sccc.cancelPre.paramtersmapping",
            "ecaop.trades.sccc.cancel.paramtersmapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[4];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        List<Map> productInfo = (List<Map>) msg.get("productInfo");
        if (IsEmptyUtils.isEmpty(productInfo)) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "业务异常:发起方未下发产品信息,无法进行流量包订购");
        }
        // 调用三户接口,校验号码合法性,获取三户资料
        Map threePartMap = MapUtils.asMap("tradeTypeCode", "0120", "getMode", "101001101010001001", "serialNumber",
                msg.get("serialNumber"));
        MapUtils.arrayPut(threePartMap, msg, MagicNumber.COPYARRAY);
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
        LanUtils lan = new LanUtils();
        try {
            lan.preData(pmp[0], threePartExchange);
            CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
            lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", e.getMessage());
        }

        // 准备预提交参数,调用预提交接口
        Map threePartInfo = threePartExchange.getOut().getBody(Map.class);
        Map tradeMap = Maps.newHashMap();
        MapUtils.arrayPut(tradeMap, msg, MagicNumber.COPYARRAY);
        Exchange tradeExchange = ExchangeUtils.ofCopy(exchange, tradeMap);
        Object tradeId = GetSeqUtil.getSeqFromCb(pmp[1], tradeExchange, "seq_trade_id", 1).get(0);
        Map preSubmitMap = MapUtils.asMap("ordersId", tradeId, "operTypeCode", MagicNumber.OPER_TYPE_ORDER_COMMIT);
        msg.put("tradeId", tradeId);
        msg.put("cbssEparchyCode", new ChangeCodeUtils().changeEparchyUnStatic(msg));
        preSubmitMap.put("base", preBase(msg, threePartInfo, exchange.getAppCode(), exchange));
        List itemList = GetSeqUtil.getSeqFromCb(pmp[1], exchange, "seq_item_id", 2);
        msg.put("itemId", itemList.get(0));
        msg.put("svcItemId", itemList.get(1));
        preSubmitMap.put("ext", preExt(msg, threePartInfo));
        MapUtils.arrayPut(preSubmitMap, msg, MagicNumber.COPYARRAY);
        Exchange preSubmitExchange = ExchangeUtils.ofCopy(exchange, preSubmitMap);
        lan.preData(pmp[2], preSubmitExchange);
        CallEngine.wsCall(preSubmitExchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.trades.sccc.cancelPre.template", preSubmitExchange);

        // 准备正式提交参数,调用正式提交接口
        Map preSubmitRetMap = preSubmitExchange.getOut().getBody(Map.class);
        Map submitMap = Maps.newHashMap();
        MapUtils.arrayPut(submitMap, msg, MagicNumber.COPYARRAY);
        preOrderSubMessage(submitMap, preSubmitRetMap, msg);
        Exchange submitExchange = ExchangeUtils.ofCopy(exchange, submitMap);
        lan.preData(pmp[3], submitExchange);
        CallEngine.wsCall(submitExchange, "ecaop.comm.conf.url.cbss.services.orderSub");
        lan.xml2Json("ecaop.trades.sccc.cancel.template", submitExchange);

        // 拼装返回参数
        Object provOrderId = ((ArrayList<Map>) preSubmitRetMap.get("rspInfo")).get(0).get("bssOrderId");
        Message out = new DefaultMessage();
        out.setBody(MapUtils.asMap("provOrderId", provOrderId));
        exchange.setOut(out);
    }

    /**
     * 准备BASE节点
     * 默认主产品不变更,客户、用户、账户ID从三户接口中取
     * @param inMap
     * @param threePartInfo
     * @return
     */
    private Map preBase(Map inMap, Map threePartInfo, String appCode, Exchange ex) {
        Map base = new HashMap();
        Object tradeId = inMap.get("tradeId");
        String acceptDate = GetDateUtils.getDate();
        base.put("subscribeId", tradeId);
        base.put("tradeId", tradeId);
        base.put("acceptDate", acceptDate);
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        base.put("inModeCode", new ChangeCodeUtils().getInModeCode(appCode));
        base.put("tradeTypeCode", "0120");
        Object serialNumber = inMap.get("serialNumber");
        Map userInfo = getValueFromThreePart(threePartInfo, "userInfo", serialNumber);
        MapUtils.arrayPut(base, userInfo, new String[] { "productId", "brandCode", "userId", "userDiffCode" });
        inMap.put("mainProductId", userInfo.get("productId"));
        inMap.put("userId", userInfo.get("userId"));
        base.put("netTypeCode", userInfo.get("serviceClassCode"));
        base.put("eparchyCode", inMap.get("cbssEparchyCode"));
        List<Map> productInfo = (List<Map>) userInfo.get("productInfo");
        if (IsEmptyUtils.isEmpty(productInfo)) {
            throw new EcAopServerBizException("9999", "通过号码[" + serialNumber + "]在CBSS未获取到产品信息!");
        }
        for (Map prod : productInfo) {
            inMap.put(prod.get("productId"), prod.get("productId"));
        }
        Map custInfo = getValueFromThreePart(threePartInfo, "custInfo", serialNumber);
        MapUtils.arrayPut(base, custInfo, new String[] { "custId", "custName" });
        Map acctInfo = getValueFromThreePart(threePartInfo, "acctInfo", serialNumber);
        base.put("acctId", acctInfo.get("acctId"));
        base.put("serinalNamber", serialNumber);
        base.put("cityCode", inMap.get("district"));
        if ("hlperser.sub".equals(ex.getAppkey())) {
            base.put("cityCode", "0000");
        }

        base.put("execTime", acceptDate);
        MapUtils.arrayPutFix("0", base, new String[] { "operFee", "foregift", "advancePay", "cancelTag", "chkTag" });
        List<Map> paraInfo = (List<Map>) threePartInfo.get("para");
        if (IsEmptyUtils.isEmpty(paraInfo)) {
            return base;
        }
        for (Map para : paraInfo) {
            if ("CERT_TAG".equals(para.get("paraId"))) {
                base.put("checktypeCode", para.get("paraValue"));
                break;
            }
        }
        return base;
    }

    private Map getValueFromThreePart(Map threePartInfo, Object key, Object serialNumber) {
        List<Map> result = (List<Map>) threePartInfo.get(key);
        if (IsEmptyUtils.isEmpty(result)) {
            throw new EcAopServerBizException("9999", "通过号码[" + serialNumber + "]在CBSS未获取到" + key + "信息!");
        }
        return result.get(0);
    }

    /**
     * 准备EXT节点
     * 主要涉及4个大节点
     * tradeProductType
     * tradeProduct
     * tradeDiscnt
     * tradeItem
     * @param msg
     * @return
     * @throws Exception
     */
    private Map preExt(Map msg, Map threePartInfo) throws Exception {
        Map<String, Map<String, List>> ext = Maps.newHashMap();
        List<Map> productList = (List<Map>) msg.get("productInfo");
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
        List<Map> tradeItem = new ArrayList();
        tradeItem.add(preTradeItem(msg));
        ext.put("tradeItem", MapUtils.asMap("item", tradeItem));
        return ext;
    }

    /**
     * 针对单个产品进行信息准备
     * @param msg
     * @param product
     * @return
     * @throws Exception
     */
    private Map<String, Map<String, List>> preSingleProduct(Map msg, Map product, Map threePartInfo) throws Exception {
        Map singleProduct = Maps.newHashMap();
        Object optType = product.get("optType");
        if (IsEmptyUtils.isEmpty(optType)) {// 默认为订购
            optType = "00";
        }
        Object productId = product.get("productId");
        List<Map> packageElement = (List<Map>) product.get("packageElement");
        List<Map> discntList = new ArrayList<Map>();
        DealNewCbssProduct dao = new DealNewCbssProduct();

        // 校验产品是否存在、获取开始结束时间偏移
        Object province = msg.get("province");
        Map productInfo = dao.qryProductInfo(MapUtils
                .asMap("COMMODITY_ID", productId, "PROVINCE_CODE", "00" + province));
        String productStartDate = dealProductStartDate(optType, product.get("enableTag"));
        String productEndDate = dealProductEndDate(productInfo, optType, productStartDate);
        List<Map> serviceList = new ArrayList<Map>();
        Map inMap = new HashMap();
        boolean noPacEle = false;
        if (IsEmptyUtils.isEmpty(packageElement)) {
            packageElement = preNoPackageElement(msg, productId, dao, productInfo.get("PRODUCT_MODE"));
            if (IsEmptyUtils.isEmpty(packageElement)) {
                throw new EcAopServerBizException("9999", "根据产品[" + productId + "]未获取到附加包及包元素信息!");
            }
            noPacEle = true;
        }
        String[] copyFromMsg = { "itemId", "mainProductId", "userId" };
        for (Map element : packageElement) {
            Map temp = new HashMap();
            temp.put("optType", optType);
            temp.put("PROVINCE_CODE", "00" + province);// 新产品库省份编码为两位,以及ZZZZ by wangmc
            // temp.put("COMMODITY_ID", productId);
            temp.put("PRODUCT_ID", productId);
            temp.put("EPARCHY_CODE", msg.get("cbssEparchyCode"));
            temp.put("PACKAGE_ID", element.get(noPacEle ? "PACKAGE_ID" : "packageId"));
            temp.put("ELEMENT_ID", element.get(noPacEle ? "ELEMENT_ID" : "elementId"));
            temp.put("enableTag", product.get("enableTag"));
            MapUtils.arrayPut(temp, msg, copyFromMsg);
            discntList.add(qryProductSingleElementInfo(temp, dao, productStartDate, productEndDate, threePartInfo));
            // 判断该资费是否有关联的服务
            inMap.put("ELEMENT_ID", element.get(noPacEle ? "ELEMENT_ID" : "elementId").toString());
            List serviceIdList = dao.selectServiceTraffic(inMap);
            if (!IsEmptyUtils.isEmpty(serviceIdList)) {
                String serviceId = serviceIdList.get(0).toString();
                inMap.put("productId", productId);
                inMap.put("serviceId", serviceId);
                inMap.put("svcItemId", msg.get("svcItemId"));
                inMap.put("PROVINCE_CODE", "00" + msg.get("province"));
                inMap.put("EPARCHY_CODE", msg.get("cbssEparchyCode"));
                serviceList.add(preServiceItem(inMap, dao, msg.get("userId")));
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
        // List<Map> userInfo = (List<Map>) threePartInfo.get("userInfo");
        // boolean isHaveThisProduct = false;
        // for (Map user : userInfo) {
        // List<Map> productInfoList = (List<Map>) user.get("productInfo");
        // if (IsEmptyUtils.isEmpty(productInfoList)) {
        // continue;
        // }
        // for (Map prod : productInfoList) {
        // if (productId.equals(prod.get("productId"))) {
        // isHaveThisProduct = true;
        // break;
        // }
        // }
        // }
        //
        for (Map dis : discntList) {
            if (!"00".equals(dis.get("productMode"))) {
                List<Map> tradeProduct = new ArrayList<Map>();

                // change by wangmc 20170418 FIXME
                String startDate = productStartTime;
                String endDate = productEndTime;
                if ("01".equals(dis.get("optType"))) {
                    // 退订时开始时间为订购该产品的时间 by wangmc 20170417
                    startDate = getStartDateByUser(threePartInfo, dis.get("productId"), "productInfo");
                    endDate = GetDateUtils.getDate();
                    if ("2".equals(product.get("enableTag"))) {
                        endDate = GetDateUtils.getMonthLastDayFormat();
                    }
                }

                tradeProduct.add(preTradeProductItem(dis, startDate, endDate));
                singleProduct.putAll(MapUtils.asMap("tradeProduct", MapUtils.asMap("item", tradeProduct)));
                List<Map> tradeProductType = new ArrayList<Map>();
                Map productType = preTradeProductTypeItem(dis, startDate, endDate);
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
     * @param inMap
     * @param productId
     * @param dao
     * @param productMode
     * @return
     */
    private List<Map> preNoPackageElement(Map inMap, Object productId, DealNewCbssProduct dao, Object productMode) {
        Map param = new HashMap();
        param.put("PROVINCE_CODE", "00" + inMap.get("province"));
        param.put("PRODUCT_ID", productId);
        param.put("PRODUCT_MODE", productMode);
        param.put("EPARCHY_CODE", inMap.get("cbssEparchyCode"));
        return dao.qryDefaultPackageElement(param);
    }

    /**
     * 校验单个产品的单个元素
     * 针对单个元素拼装节点，放在MAP，由调用者进行组装
     * @param inMap
     * @param dao
     * @return
     * @throws Exception
     */
    private Map qryProductSingleElementInfo(Map inMap, DealNewCbssProduct dao, String productStartDate,
            String productEndDate, Map threePartInfo) throws Exception {// 将各个属性放在一个MAP，后期处理
        Object productMode = dao.queryProductModeByCommodityId(inMap);
        Object productId = inMap.get("PRODUCT_ID");// 欲变更的产品
        Object mainProductId = inMap.get("mainProductId");// 当前主产品
        inMap.put("productMode", productMode);
        if ("00".equals(productMode)) {
            if (!mainProductId.equals(productId)) {
                throw new EcAopServerBizException("9999", "流量包订购业务中,不允许变更主套餐!原主套餐编码[" + mainProductId + "],新主套餐编码["
                        + productId + "]");
            }
            return preDiscntItem(inMap, dao, productStartDate, productEndDate, threePartInfo);
        }
        else if ("01".equals(productMode) || "20".equals(productMode)) {
            if (null == inMap.get("PRODUCT_ID")) {
                if ("01".equals(inMap.get("optType"))) {
                    throw new EcAopServerBizException("9999", "产品[" + productId + "]在CBSS没有订购记录,无法退订!");
                }
                return preDiscntItem(inMap, dao, productStartDate, productEndDate, threePartInfo);
            }
            return preDiscntItem(inMap, dao, productStartDate, productEndDate, threePartInfo);
        }
        else {
            throw new EcAopServerBizException("9999", "产品模式有误,[" + productId + "]预期为[00,01,20]" + ",实际为[" + productMode
                    + "]!");
        }
    }

    /**
     * 处理DISCNT_ITEM返回ITEM
     * @param inMap
     * @param element
     * @param dao
     * @param productMode
     * @return
     * @throws Exception
     */
    private Map preDiscntItem(Map inMap, DealNewCbssProduct dao, String productStartDate, String productEndDate,
            Map threePartInfo) throws Exception {
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("id", inMap.get("userId"));
        item.put("idType", "1");
        item.put("userIdA", "-1");
        item.put("productId", inMap.get("PRODUCT_ID"));
        item.put("packageId", inMap.get("PACKAGE_ID"));
        item.put("discntCode", inMap.get("ELEMENT_ID"));
        item.put("productMode", inMap.get("productMode"));
        item.put("specTag", "0");// 0-正常产品优惠
        String startDate = DateUtils.getDate();
        String endDate = MagicNumber.CBSS_DEFAULT_EXPIRE_TIME;
        Object enableTag = inMap.get("enableTag");
        if ("2".equals(enableTag)) {
            startDate = DateUtils.getNextMonthFirstDay();
        }
        Object optType = inMap.get("optType");
        inMap.put("DISCNT_CODE", inMap.get("ELEMENT_ID"));
        inMap.putAll(dao.qryDiscntAttr(inMap));
        startDate = dealDiscntStartDate(inMap, startDate, optType, enableTag);
        endDate = dealDiscntEndDate(inMap, startDate, optType);
        item.put("startDate", DateUtils.max(startDate, productStartDate));
        item.put("endDate", DateUtils.min(endDate, productEndDate));
        // change by wangmc 20170418 FIXME
        if ("01".equals(optType)) {// 退订的资费,开始时间取三户返回的资费生效时间,结束时间根据enableTag计算
            item.put("startDate", getStartDateByUser(threePartInfo, inMap.get("ELEMENT_ID"), "discntInfo"));
            item.put("endDate", DateUtils.getDate());
            if ("2".equals(enableTag)) {
                item.put("endDate", GetDateUtils.getMonthLastDayFormat());
            }
        }

        item.put("modifyTag", "00".equals(optType) ? "0" : "1");
        item.put("optType", optType);
        item.put("enableTag", inMap.get("enableTag"));
        return item;
    }

    /**
     * 处理SERVICE_ITEM返回ITEM
     * @param inMap
     * @param dao
     * @return
     * @throws Exception
     */
    private Map preServiceItem(Map inMap, DealNewCbssProduct dao, Object userId) throws Exception {
        List svcList = dao.queryProductSvcInfoByElementIdProductId(inMap);
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
     * @param inMap
     * @param startDate
     * @param optType 00：订购；01：退订
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
            }
            catch (Exception e) {
                throw new EcAopServerBizException("9999", "处理资费[" + inMap.get("DISCNT_CODE") + "]的结束时间失败,原因:"
                        + e.getMessage());
            }
        }
        return endDate;
    }

    /**
     * 处理资费的开始时间
     * @param inMap
     * @param startDate
     * @param optType 00：订购；01：退订
     * @param enableTag 1:立即生效 2：次月生效
     * @return
     * @throws Exception
     */
    private String dealDiscntStartDate(Map inMap, String startDate, Object optType, Object enableTag) throws Exception {
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
     * 处理TRADE_ITEM返回ITEM
     * @param inMap
     * @return
     */
    private Map preTradeItem(Map inMap) {
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("attrCode", "STANDARD_KIND_CODE");
        item.put("attrValue", inMap.get("cbssEparchyCode"));
        return item;
    }

    /**
     * 处理TRADE_PRODUCT返回ITEM
     * @param inMap
     * @return
     */
    private Map preTradeProductItem(Map inMap, String productStartTime, String productEndTime) {
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("productMode", inMap.get("productMode"));
        item.put("brandCode", "4G00");// 此处暂时写死4G00,因目前只支撑CB业务
        item.put("productId", inMap.get("productId"));
        item.put("modifyTag", "00".equals(inMap.get("optType")) ? "0" : "1");// 使用处理资费节点时的字段
        item.put("startDate", productStartTime);
        item.put("endDate", productEndTime);
        item.put("userIdA", "-1");
        return item;
    }

    /**
     * 处理TRADE_PRODUCT_TYPE返回ITEM
     * @param inMap
     * @param productStartTime
     * @param productEndTime
     * @return
     */
    private Map preTradeProductTypeItem(Map inMap, String productStartTime, String productEndTime) {
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("productMode", inMap.get("productMode"));
        item.put("productId", inMap.get("productId"));
        item.put("productTypeCode", "4G000001");
        item.put("modifyTag", "00".equals(inMap.get("optType")) ? "0" : "1");// 使用处理资费节点时的字段
        item.put("startDate", productStartTime);
        item.put("endDate", productEndTime);
        return item;
    }

    /**
     * 拼装正式提交参数
     * @param outMap
     * @param inMap
     * @param msg
     */
    private void preOrderSubMessage(Map outMap, Map inMap, Map msg) {
        List<Map> subOrderSubReq = new ArrayList<Map>();
        List<Map> rspInfo = (ArrayList<Map>) inMap.get("rspInfo");
        outMap.put("provOrderId", rspInfo.get(0).get("bssOrderId"));
        outMap.put("orderNo", rspInfo.get(0).get("provOrderId"));
        List<Map> provinceOrderInfo = (ArrayList<Map>) rspInfo.get(0).get("provinceOrderInfo");
        Map subOrderSubMap = new HashMap();
        int totalFee = 0;
        if (null != provinceOrderInfo && 0 != provinceOrderInfo.size()) {
            List<Map> feeInfo = (ArrayList<Map>) provinceOrderInfo.get(0).get("preFeeInfoRsp");
            totalFee = Integer.valueOf(provinceOrderInfo.get(0).get("totalFee").toString());
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
        outMap.put("origTotalFee", totalFee);
        subOrderSubMap.put("subProvinceOrderId", outMap.get("provOrderId"));
        subOrderSubMap.put("subOrderId", outMap.get("orderNo"));
        subOrderSubReq.add(subOrderSubMap);
        outMap.put("subOrderSubReq", subOrderSubReq);
    }

    /**
     * 处理产品开始时间
     * @param productInfo
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
     * 获取用户下的产品信息节点 by wangmc 20170412
     * @param threePartInfo -三户返回信息
     * @param getTimeId 取值为:productId-获取产品开始时间,disCode-获取该资费的开始时间
     * @param getTimeKey 取值:productInfo-对应产品,discntInfo-对应资费
     * @return
     */
    private String getStartDateByUser(Map threePartInfo, Object getTimeId, String getTimeKey) {
        List<Map> userInfos = (List<Map>) threePartInfo.get("userInfo");
        if (IsEmptyUtils.isEmpty(userInfos)) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "三户接口未返回用户信息");
        }
        Map userInfo = userInfos.get(0);
        if (IsEmptyUtils.isEmpty(userInfo.get(getTimeKey))) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "三户接口未返回产品信息");
        }
        List<Map> dataInfos = (List<Map>) userInfo.get(getTimeKey);
        String idKey = "productInfo".equals(getTimeKey) ? "productId" : "discntCode";
        String timeKey = "productInfo".equals(getTimeKey) ? "productActiveTime" : "startDate";
        for (Map dataInfo : dataInfos) {
            if (getTimeId.equals(dataInfo.get(idKey))) {
                return (String) dataInfo.get(timeKey);
            }
        }
        String error = "productInfo".equals(getTimeKey) ? "产品" : "资费";
        throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "根据三户返回的信息,未查询到要退订的" + error + "!");
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
}
