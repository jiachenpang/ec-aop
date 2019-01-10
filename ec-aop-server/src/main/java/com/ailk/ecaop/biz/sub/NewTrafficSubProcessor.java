package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

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
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.google.common.collect.Maps;

@EcRocTag("newTrafficSub")
public class NewTrafficSubProcessor extends BaseAopProcessor {

    private static final String[] EXT_TRAFFIC = { "tradeProductType", "tradeProduct", "tradeDiscnt", "tradeSvc" };

    @Override
    public void process(Exchange exchange) throws Exception {
        System.out.println("进入TrafficSubProcessors.processor 时间:" + new Date());
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Object district = msg.get("district");
        if (IsEmptyUtils.isEmpty(district)) {
            msg.put("district", "00000000");
        }
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
            lan.preData("ecaop.trade.cbss.checkUserParametersMapping", threePartExchange);
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
        String tradeId = GetSeqUtil.getSeqFromCb(tradeExchange, "seq_trade_id");
        Map preSubmitMap = MapUtils.asMap("ordersId", tradeId, "operTypeCode", MagicNumber.OPER_TYPE_ORDER_COMMIT);
        msg.put("tradeId", tradeId);
        msg.put("cbssEparchyCode", new ChangeCodeUtils().changeEparchyUnStatic(msg));
        preSubmitMap.put("base", preBase(msg, threePartInfo, exchange.getAppCode()));
        List itemList = GetSeqUtil.getSeqFromCb(exchange, "seq_item_id", 2);
        msg.put("itemId", itemList.get(0));
        msg.put("svcItemId", itemList.get(1));
        preSubmitMap.put("ext", preExt(msg));
        MapUtils.arrayPut(preSubmitMap, msg, MagicNumber.COPYARRAY);
        Exchange preSubmitExchange = ExchangeUtils.ofCopy(exchange, preSubmitMap);
        lan.preData("ecaop.trades.sccc.cancelPre.paramtersmapping", preSubmitExchange);
        CallEngine.wsCall(preSubmitExchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.trades.sccc.cancelPre.template", preSubmitExchange);

        // 准备正式提交参数,调用正式提交接口
        Map preSubmitRetMap = preSubmitExchange.getOut().getBody(Map.class);
        Map submitMap = Maps.newHashMap();
        MapUtils.arrayPut(submitMap, msg, MagicNumber.COPYARRAY);
        preOrderSubMessage(submitMap, preSubmitRetMap, msg);
        Exchange submitExchange = ExchangeUtils.ofCopy(exchange, submitMap);
        lan.preData("ecaop.trades.sccc.cancel.paramtersmapping", submitExchange);
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
     * 
     * @param inMap
     * @param threePartInfo
     * @return
     */
    private Map preBase(Map inMap, Map threePartInfo, String appCode) {
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
     * 
     * @param msg
     * @return
     * @throws Exception
     */
    private Map preExt(Map msg) throws Exception {
        Map<String, Map<String, List>> ext = Maps.newHashMap();
        List<Map> productList = (List<Map>) msg.get("productInfo");
        for (Map product : productList) {
            Map singleProduct = preSingleProduct(msg, product);
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
     * 
     * @param msg
     * @param product
     * @return
     * @throws Exception
     */
    private Map<String, Map<String, List>> preSingleProduct(Map msg, Map product) throws Exception {
        Map singleProduct = Maps.newHashMap();
        Object optType = product.get("optType");
        if (IsEmptyUtils.isEmpty(optType)) {// 默认为订购
            optType = "00";
        }
        Object productId = product.get("productId");
        List<Map> packageElement = (List<Map>) product.get("packageElement");
        List<Map> discntList = new ArrayList<Map>();
        DealNewCbssProduct dao = new DealNewCbssProduct();
        List<Map> serviceList = new ArrayList<Map>();
        Object productMode = getProductMode(productId, msg, dao);
        if (IsEmptyUtils.isEmpty(packageElement)) {
            packageElement = preNoPackageElement(msg, productId, productMode, dao);
            if (IsEmptyUtils.isEmpty(packageElement)) {
                throw new EcAopServerBizException("9999", "根据产品[" + productId + "]未获取到附加包及包元素信息!");
            }
        }
        String[] copyFromMsg = { "itemId", "mainProductId", "userId" };
        // Map inMap = new HashMap();
        // LanOpenApp4GDao lanDao = new LanOpenApp4GDao();
        for (Map element : packageElement) {
            Map temp = new HashMap();
            temp.put("optType", optType);
            temp.put("PROVINCE_CODE", "00" + msg.get("province"));
            temp.put("PRODUCT_ID", productId);
            temp.put("PRODUCT_MODE", productMode);
            temp.put("EPARCHY_CODE", msg.get("cbssEparchyCode"));
            temp.put("PACKAGE_ID", element.get("packageId"));
            temp.put("ELEMENT_ID", element.get("elementId"));
            temp.put("enableTag", product.get("enableTag"));
            MapUtils.arrayPut(temp, msg, copyFromMsg);
            discntList.add(qryProductSingleElementInfo(temp, dao));
            // 判断该资费是否有关联的服务
            // inMap.put("ELEMENT_ID", element.get("elementId").toString());
            // List serviceIdList = lanDao.selectServiceTraffic(inMap);
            // if (!IsEmptyUtils.isEmpty(serviceIdList)) {
            // String serviceId = ((Map) serviceIdList.get(0)).get("ELEMENT_ID_B").toString();
            // inMap.put("productId", productId);
            // inMap.put("serviceId", serviceId);
            // inMap.put("svcItemId", msg.get("svcItemId"));
            // inMap.put("PROVINCE_CODE", "00" + msg.get("province"));
            // inMap.put("EPARCHY_CODE", msg.get("cbssEparchyCode"));
            // serviceList.add(preServiceItem(inMap, lanDao, msg.get("userId")));
            // }
        }
        singleProduct.putAll(MapUtils.asMap("tradeDiscnt", MapUtils.asMap("item", discntList)));
        for (Map dis : discntList) {
            if ("01".equals(dis.get("productMode"))) {
                List<Map> tradeProduct = new ArrayList<Map>();
                tradeProduct.add(preTradeProductItem(dis));
                singleProduct.putAll(MapUtils.asMap("tradeProduct", MapUtils.asMap("item", tradeProduct)));
                List<Map> tradeProductType = new ArrayList<Map>();
                Map productType = preTradeProductTypeItem(dis);
                productType.put("userId", msg.get("userId"));
                tradeProductType.add(productType);
                singleProduct.putAll(MapUtils.asMap("tradeProductType", MapUtils.asMap("item", tradeProductType)));
            }
        }
        singleProduct.putAll(MapUtils.asMap("tradeSvc", MapUtils.asMap("item", serviceList)));
        return singleProduct;
    }

    private Object getProductMode(Object productId, Map msg, DealNewCbssProduct dao) {
        Map para = new HashMap();
        para.put("PRODUCT_ID", productId);
        para.put("PROVINCE_CODE", msg.get("province"));// 新产品库省份编码为两位,以及ZZZZ by wangmc
        Object productMode = dao.queryProductModeByCommodityId(para);
        return productMode;
    }

    /**
     * 根据产品获取默认资费属性,外围系统不下发资费时调用
     * 
     * @param inMap
     * @param productId
     * @param dao
     * @return
     */
    private List<Map> preNoPackageElement(Map inMap, Object productId, Object productMode, DealNewCbssProduct dao) {
        Map param = new HashMap();
        param.put("PROVINCE_CODE", "00" + inMap.get("province"));
        param.put("PRODUCT_ID", productId);
        param.put("EPARCHY_CODE", inMap.get("cbssEparchyCode"));
        param.put("PRODUCT_MODE", productMode);
        return dao.qryDefaultPackageElement(param);
    }

    /**
     * 校验单个产品的单个元素
     * 针对单个元素拼装节点，放在MAP，由调用者进行组装
     * 
     * @param inMap
     * @param dao
     * @return
     * @throws Exception
     */
    private Map qryProductSingleElementInfo(Map inMap, DealNewCbssProduct dao) throws Exception {// 将各个属性放在一个MAP，后期处理
        Object productMode = inMap.get("PRODUCT_MODE");
        Object productId = inMap.get("PRODUCT_ID");// 欲变更的产品
        Object mainProductId = inMap.get("mainProductId");// 当前主产品
        if ("00".equals(productMode)) {
            if (!mainProductId.equals(productId)) {
                throw new EcAopServerBizException("9999", "流量包订购业务中,不允许变更主套餐!原主套餐编码[" + mainProductId + "],新主套餐编码["
                        + productId + "]");
            }
            return preDiscntItem(inMap, dao, productMode);
        }
        else if ("01".equals(productMode)) {
            if (null == inMap.get("PRODUCT_ID")) {
                if ("01".equals(inMap.get("optType"))) {
                    throw new EcAopServerBizException("9999", "产品[" + productId + "]在CBSS没有订购记录,无法退订!");
                }
                return preDiscntItem(inMap, dao, productMode);
            }
            return preDiscntItem(inMap, dao, productMode);
        }
        else {
            throw new EcAopServerBizException("9999", "产品模式有误,[" + productId + "]预期为[00,01]" + ",实际为[" + productMode
                    + "]!");
        }
    }

    /**
     * 处理DISCNT_ITEM返回ITEM
     * 
     * @param inMap
     * @param element
     * @param dao
     * @param productMode
     * @return
     * @throws Exception
     */
    private Map preDiscntItem(Map inMap, DealNewCbssProduct dao, Object productMode) throws Exception {
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("id", inMap.get("userId"));
        item.put("idType", "1");
        item.put("userIdA", "-1");
        item.put("productId", inMap.get("PRODUCT_ID"));
        item.put("packageId", inMap.get("PACKAGE_ID"));
        item.put("discntCode", inMap.get("ELEMENT_ID"));
        item.put("productMode", productMode);
        item.put("specTag", "0");// 0-正常产品优惠
        String startDate = DateUtils.getDate();
        String endDate = MagicNumber.CBSS_DEFAULT_EXPIRE_TIME;
        if ("2".equals(inMap.get("enableTag"))) {
            startDate = DateUtils.getNextMonthFirstDay();
        }
        Object optType = inMap.get("optType");
        Object enableTag = inMap.get("enableTag");
        inMap.put("DISCNT_CODE", inMap.get("ELEMENT_ID"));
        inMap.putAll(dao.qryDiscntAttr(inMap));
        startDate = dealDiscntStartDate(inMap, startDate, optType, enableTag);
        endDate = dealDiscntEndDate(inMap, startDate, optType);

        item.put("startDate", startDate);
        item.put("endDate", endDate);
        item.put("modifyTag", "00".equals(optType) ? "0" : "1");
        item.put("optType", optType);
        item.put("enableTag", inMap.get("enableTag"));
        return item;
    }

    /**
     * 处理SERVICE_ITEM返回ITEM
     * 
     * @param inMap
     * @param dao
     * @return
     * @throws Exception
     */
    private Map preServiceItem(Map inMap, LanOpenApp4GDao dao, Object userId) throws Exception {
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
     * 
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
        String endDate = endAbsoluteDate.toString();
        if ("0".equals(enableTag)) {
            startOffset = "0";
        }
        if (!"null".equals(endOffset) && !"null".equals(endUnit) && "1".equals(endEnableTag)) {
            startDate = GetDateUtils.transDate(startDate, 10);
            endDate = GetDateUtils.getSpecifyDateTime(
                    Integer.parseInt(endEnableTag),
                    Integer.parseInt(endUnit),
                    GetDateUtils.getSysdateFormat(),
                    Integer.parseInt(endOffset) + Integer.parseInt(startOffset));
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
     * 
     * @param inMap
     * @param startDate
     * @param optType 00：订购；01：退订
     * @param enableTag 1:立即生效 2：次月生效
     * @return
     * @throws Exception
     */
    private String dealDiscntStartDate(Map inMap, String startDate, Object optType, Object enableTag)
            throws Exception {
        if (!"00".equals(optType)) {// 产品退订，开始时间是次月，结束时间是当前
            return DateUtils.getNextMonthFirstDay();
        }
        Object enableTagDB = inMap.get("ENABLE_TAG") + "";// 针对开始时间 0-绝对时间（看开始时间） 1-相对时间
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
     * 
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
     * 
     * @param inMap
     * @return
     */
    private Map preTradeProductItem(Map inMap) {
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("productMode", "01");// 此处只能是01,不支持主套餐变更
        item.put("brandCode", "4G00");// 此处暂时写死4G00,因目前只支撑CB业务
        item.put("productId", inMap.get("productId"));

        if ("00".equals(inMap.get("optType"))) {
            item.put("modifyTag", "0");
            String startDate = DateUtils.getDate();
            if ("2".equals(inMap.get("enableTag"))) {
                startDate = DateUtils.getNextMonthFirstDay();
            }
            item.put("startDate", startDate);
            item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
        }
        else {
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
     * @param element
     * @param optType
     * @return
     */
    private Map preTradeProductTypeItem(Map inMap) {
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("productMode", "01");// 此处只能是01,不支持主套餐变更
        item.put("productId", inMap.get("productId"));
        item.put("productTypeCode", "4G000001");
        if ("00".equals(inMap.get("optType"))) {
            item.put("modifyTag", "0");
            String startDate = DateUtils.getDate();
            if ("2".equals(inMap.get("enableTag"))) {
                startDate = DateUtils.getNextMonthFirstDay();
            }
            item.put("startDate", startDate);
            item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
        }
        else {
            item.put("modifyTag", "1");
            item.put("startDate", DateUtils.getNextMonthFirstDay());
            item.put("endDate", DateUtils.getDate());
        }
        return item;
    }

    /**
     * 拼装正式提交参数
     * 
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

}
