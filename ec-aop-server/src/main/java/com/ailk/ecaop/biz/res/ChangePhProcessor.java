package com.ailk.ecaop.biz.res;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.alibaba.fastjson.JSON;

/**
 * 换机
 * @author lxl
 */
@EcRocTag("ChangePhProcessor")
public class ChangePhProcessor extends BaseAopProcessor implements ParamsAppliable {

    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[5];
    // 终端校验接口(3GE为cBSS提供的),cBSS三户,cBSS预提交,3GE终端销售,cBSS正式提交
    private static final String[] PARAM_ARRAY = { "ecaop.masb.chph.check.ParametersMapping",
            "ecaop.trade.cbss.checkUserParametersMapping", "ecaop.masb.chph.preSub.ParametersMapping",
            "ecaop.masb.chph.sale.ParametersMapping", "ecaop.masb.chph.sub.ParametersMapping" };

    // 访问三户返回的老终端信息中没有用的
    static final String delOld = "deviceType|deviceno|distributionTag|feeItemPreSore"
            + "|imei|mobilecost|mobileinfo|mobilesaleprice|resourcesBrandCode|resourcesBrandName"
            + "|resourcesModelName|resourcesModelCode|terminalType|STAFF_ID|DEPART_ID|TRADE_ID|terminalSubType";

    private final LanUtils lan = new LanUtils();

    @Override
    public void process(Exchange exchange) throws Exception {
        Map msg = (Map) exchange.getIn().getBody(Map.class).get("msg");

        // 判断是否对接资源中心
        boolean isResCenter = "1".equals(msg.get("resCenterSwitch"));

        Map resInfo = new HashMap();
        List<Map> resInfos = new ArrayList<Map>();
        resInfo = preResourceInfo(resInfo, msg);
        resInfos.add(resInfo);
        msg.put("resourcesInfo", resInfos);
        // 缓存调用查询校验之前的in中的msg
        Map oldMsg = new HashMap();
        oldMsg.putAll(msg);
        Map in = new HashMap(exchange.getIn().getBody(Map.class));

        Object newTradeId = GetSeqUtil.getSeqFromCb(exchange, "seq_trade_id", 1).get(0);// 调用cb序列生成id
        exchange.getIn().setBody(in);
        Object newItemId = GetSeqUtil.getSeqFromCb(exchange, "seq_item_id", 1).get(0);// 调用cb序列生成id

        exchange.setProperty("newItemId", newItemId);
        exchange.setProperty("newTradeId", newTradeId);

        exchange.getIn().setBody(in);

        Map resRsp;
        if (isResCenter) {
            // 对接资源中心时,调用资源中心做终端校验
            resRsp = checkResFromResCenter(exchange);
        }
        else {
            // 调用3GESS为cBSS提供的查询校验接口
            lan.preData(pmp[0], exchange);// ecaop.masb.chph.check.ParametersMapping
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.cbss");
            lan.xml2Json4Res("ecaop.masb.chph.check.template", exchange);
            resRsp = qryChkRes(exchange);
        }

        // 调三户查询接口，获取purchase节点下面的信息
        msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
        msg.put("tradeTypeCode", "2802");
        msg.put("getMode", "1111110010000000000000000000001");
        msg.put("serialNumber", msg.get("number"));
        msg.put("serviceClassCode", "0000");

        threePartPurchaseInfo(exchange);
        List<Map> purchase = (List<Map>) exchange.getOut().getBody(Map.class).get("purchase");
        if (oldMsg.get("oldResourcesCode").equals(oldMsg.get("newResourcesCode"))) {
            throw new EcAopServerBizException("9999", "新终端串码和老终端串码相同！");
        }
        if (!oldMsg.get("oldResourcesCode").equals(purchase.get(0).get("imei"))) {
            throw new EcAopServerBizException("9999", "老终端串码和三户返回终端串码不一致！");
        }

        Map base = preBase(oldMsg, exchange);
        Map ext = preExt(exchange, resRsp, oldMsg, in);

        Map preSubIn = new HashMap(in);
        Map inMsg = JSON.parseObject((String) preSubIn.get("msg"));
        inMsg.put("ordersId", newTradeId);
        inMsg.put("operTypeCode", "0");
        inMsg.put("base", base);
        inMsg.put("ext", ext);
        preSubIn.put("msg", JSON.toJSON(inMsg));

        preSub(exchange, preSubIn);

        // 终端销售
        if (isResCenter) {
            // 调用资源中心做换机销售
            saleResFromResCenter(exchange);
        }
        else {
            // 调用3GE终端销售借口
            terSale(exchange, in);
        }
        sub(in, newTradeId, exchange);

        Map preSubBack = exchange.getProperty("preSubBack", Map.class);
        List<Map> rspInfo = (List<Map>) preSubBack.get("rspInfo");
        String provOrderId = (String) rspInfo.get(0).get("provOrderId");
        Map parameters = MapUtils.asMap("paraId", "", "paraValue", "");
        List<Map> para = new ArrayList<Map>();
        para.add(parameters);
        Map out = MapUtils.asMap("provOrderId", newTradeId, "bssOrderId", provOrderId, "para", para);
        exchange.getOut().setBody(out);
    }

    /**
     * FIXME 调用资源中心,做换机销售 by wangmc 20180622
     * @param exchange
     * @throws Exception
     */
    private void saleResFromResCenter(Exchange exchange) throws Exception {
        Map msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
        // 复制请求信息用于调用资源中心终端换机接口
        Map saleMsg = new HashMap(msg);
        saleMsg.put("resourcesCode", saleMsg.get("newResourcesCode"));
        saleMsg.put("RES_OPER_TYPE_FLAG", "03");
        Exchange saleExchange = ExchangeUtils.ofCopy(exchange, saleMsg);
        // 调用资源中心终端销售接口
        new ResourceStateChangeUtils().getResourceInfo(saleExchange);
    }

    /**
     * FIXME 调用资源中心校验终端信息 by wangmc 20180622
     * @param exchange
     * @throws Exception
     */
    private Map checkResFromResCenter(Exchange exchange) throws Exception {
        Map msg = (Map) exchange.getIn().getBody(Map.class).get("msg");

        // 复制请求信息用于调用资源中心终端校验接口
        Map checkMsg = new HashMap(msg);
        // 准备调用校验终端信息的公共方法的请求参数
        Map resourcesInfo = new HashMap();
        // 资源中心终端类型:00－移动电话、01－上网卡、02－上网本
        resourcesInfo.put("RESOURCES_TYPE", "00");
        resourcesInfo.put("RESOURCES_CODE", checkMsg.get("newResourcesCode"));// 新终端串号
        resourcesInfo.put("TRADE_TYPE", "03");// 业务类型:03-换机
        resourcesInfo.put("oldResourcesCode", checkMsg.get("oldResourcesCode"));// 旧终端串号
        resourcesInfo.put("occupiedFlag", "1");// 预占标识只有传预占时,资源中心才会返回终端信息
        List<Map> resourcesInfoList = new ArrayList<Map>();
        resourcesInfoList.add(resourcesInfo);
        checkMsg.put("resourcesInfo", resourcesInfoList);

        Exchange checkExchange = ExchangeUtils.ofCopy(exchange, checkMsg);
        // 调用资源中心终端校验接口
        new ResourceStateChangeUtils().getResourceInfo(checkExchange);
        return qryChkRes(checkExchange);
    }

    private void sub(Map in, Object newTradeId, Exchange exchange) {
        Map preSubBack = exchange.getProperty("preSubBack", Map.class);
        List<Map> rspInfo = (List<Map>) preSubBack.get("rspInfo");
        String provOrderId = (String) rspInfo.get(0).get("provOrderId");

        Map subIn = new HashMap(in);
        Map msg = JSON.parseObject((String) subIn.get("msg"));
        msg.put("cancleTotalFee", "0");
        msg.put("orderNo", newTradeId);
        msg.put("noteFlag", "1");
        msg.put("origTotalFee", "0");
        msg.put("noteType", "1");
        List<Map> subOrderSubReq = new ArrayList<Map>();
        Map subOrderSub = MapUtils.asMap("subOrderId", newTradeId, "subProvinceOrderId", provOrderId);
        subOrderSubReq.add(subOrderSub);
        msg.put("subOrderSubReq", subOrderSubReq);
        // msg.put("operationType", "01");
        msg.put("sendTypeCode", "");
        msg.put("noteNo", "11111111111111");
        msg.put("provOrderId", provOrderId);
        subIn.put("msg", JSON.toJSON(msg));
        exchange.getIn().setBody(subIn);
        try {
            lan.preData(pmp[4], exchange);// ecaop.masb.chph.sub.ParametersMapping
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
            lan.xml2Json("ecaop.trades.chph.sub.template", exchange);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", e.getMessage());
        }
    }

    private void terSale(Exchange exchange, Map in) {
        Map saleIn = new HashMap(in);
        Map inMsg = JSON.parseObject((String) saleIn.get("msg"));
        inMsg.put("tradeType", "30");
        inMsg.put("operType", "03");
        inMsg.put("resourcesCode", inMsg.get("newResourcesCode"));
        saleIn.put("msg", JSON.toJSON(inMsg));
        exchange.getIn().setBody(saleIn);
        try {
            lan.preData(pmp[3], exchange);// ecaop.masb.chph.sale.ParametersMapping
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.cbss");
            lan.xml2Json4ONS("ecaop.trades.chph.sale.template", exchange);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", e.getMessage());
        }
    }

    /**
     * 调用cBSS预提交
     * @param exchange
     * @param in
     */
    private void preSub(Exchange exchange, Map in) {
        exchange.getIn().setBody(in);
        try {
            lan.preData(pmp[2], exchange);// ecaop.masb.chph.preSub.ParametersMapping
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
            lan.xml2Json("ecaop.trades.chph.preSub.template", exchange);
            Map preSubBack = (Map) exchange.getOut().getBody();
            exchange.setProperty("preSubBack", preSubBack);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", e.getMessage());
        }
    }

    private Map qryChkRes(Exchange exchange) {
        List<Map> resRsps = (List) exchange.getOut().getBody(Map.class).get("resourcesRsp");
        Map resRsp = resRsps.get(0);
        return resRsp;
    }

    /**
     * 调用cBSS三户接口
     * @param exchange
     */
    private void threePartPurchaseInfo(Exchange exchange) {
        try {
            lan.preData(pmp[1], exchange);// ecaop.trade.cbss.checkUserParametersMapping
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
            lan.xml2Json("ecaop.trades.cbss.threePart.template", exchange);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", e.getMessage());
        }
    }

    private Map preExt(Exchange exchange, Map resRsp, Map oldMsg, Map in) {
        Map ext = new HashMap();
        // 获取调用三户接口返回的purchase节点下的信息
        List<Map> purchase = (List<Map>) exchange.getOut().getBody(Map.class).get("purchase");
        ext.put("tradeItem", preTradeItem(purchase, oldMsg, exchange.getAppCode()));
        ext.put("tradePurchase", preTradePurchase(purchase, resRsp, oldMsg, exchange));
        ext.put("tradeSubItem", preTradeSubItem(purchase, resRsp, oldMsg, exchange, in));
        return ext;
    }

    // tradeItem节点下的itemId是三户返回的
    private Map preTradeItem(List<Map> purchase, Map oldMsg, String appCode) {
        List<Map> item = (List<Map>) purchase.get(0).get("attrInfo");
        List<Map> myItem = new ArrayList<Map>();
        for (Map m : item) {
            Map map = new HashMap(m);
            map.remove("startDate");
            map.remove("endDate");
            myItem.add(map);
        }
        Map tradeItem = new HashMap();

        Map eInMode = lan.createAttrInfoNoTime("E_IN_MODE", "A");
        eInMode.put("xDatatype", "NULL");

        Map saleDesc = lan.createAttrInfoNoTime("SALE_DESC", "终端详细信息");
        saleDesc.put("xDatatype", "NULL");

        Map staffId = lan.createAttrInfoNoTime("STAFF_ID", oldMsg.get("operatorId"));
        staffId.put("xDatatype", "NULL");

        Map departId = lan.createAttrInfoNoTime("DEPART_ID", oldMsg.get("channelId"));
        departId.put("xDatatype", "NULL");

        Map standardKindCode = lan.createAttrInfoNoTime("STANDARD_KIND_CODE", "1010400");
        standardKindCode.put("xDatatype", "NULL");

        Map specTag = lan.createAttrInfoNoTime("SPEC_TAG", "0");
        specTag.put("xDatatype", "NULL");

        Map map = new HashMap();
        for (int i = 0; i < myItem.size(); i++) {
            map = myItem.get(i);
            if ("SALE_DESC|STAFF_ID|DEPART_ID|STANDARD_KIND_CODE|SPEC_TAG|feeItemPreSore".contains(map.get("attrCode")
                    .toString())) {
                myItem.remove(i);
                i--;
            }
            map.put("xDatatype", "NULL");
        }
        if (new ChangeCodeUtils().isWOPre(appCode)) {
            myItem.add(LanUtils.createTradeItem("WORK_TRADE_ID", oldMsg.get("ordersId")));
        }
        myItem.add(eInMode);
        myItem.add(saleDesc);
        myItem.add(staffId);
        myItem.add(departId);
        myItem.add(standardKindCode);
        myItem.add(specTag);

        tradeItem.put("item", myItem);

        return tradeItem;
    }

    private Map preTradeSubItem(List<Map> purchase, Map resRsp, Map oldMsg, Exchange exchange, Map in) {
        exchange.getIn().setBody(in);
        List<Map> userInfos = (List<Map>) exchange.getOut().getBody(Map.class).get("userInfo");
        Map userInfo = userInfos.get(0);
        List<Map> item = (List<Map>) purchase.get(0).get("attrInfo");
        List<Map> traSubItem = new ArrayList<Map>();
        for (Map m : item) {
            Map map = new HashMap(m);
            map.remove("startDate");
            map.remove("endDate");
            traSubItem.add(map);
        }
        Map tradeSubItem = new HashMap();

        String id = exchange.getProperty("newItemId").toString();
        Map staffId = lan.createAttrInfoWithId("STAFF_ID", oldMsg.get("operatorId"), id);
        Map departId = lan.createAttrInfoWithId("DEPART_ID", oldMsg.get("channelId"), id);
        Map deviceType = lan.createAttrInfoWithId("deviceType", resRsp.get("resourcesType"), id);
        Map imei = lan.createAttrInfoWithId("imei", resRsp.get("resourcesCode"), id);
        Map mobilesaleprice = lan.createAttrInfoWithId("mobilesaleprice",
                Integer.parseInt((String) resRsp.get("salePrice")) / 1000, id);
        Map mobilecost = lan.createAttrInfoWithId("mobilecost", Integer.parseInt((String) resRsp.get("cost")) / 1000,
                id);
        Map resourcesBrandCode = lan.createAttrInfoWithId("resourcesBrandCode", resRsp.get("resourcesBrandCode"), id);
        Map resourcesBrandName = lan.createAttrInfoWithId("resourcesBrandName", resRsp.get("resourcesBrandName"), id);
        Map resourcesModelCode = lan.createAttrInfoWithId("resourcesModelCode", resRsp.get("resourcesModelCode"), id);
        Map resourcesModelName = lan.createAttrInfoWithId("resourcesModelName", resRsp.get("resourcesModelName"), id);
        Map mobileinfo = lan.createAttrInfoWithId("mobileinfo", resRsp.get("resourcesColor"), id);
        Map deviceno = lan.createAttrInfoWithId("deviceno", resRsp.get("machineTypeCode"), id);
        Map distributionTag = lan.createAttrInfoWithId("distributionTag", resRsp.get("distributionTag"), id);
        Map terminalType = lan.createAttrInfoWithId("terminalType", resRsp.get("terminalType"), id);
        Map terminalTSubType = lan.createAttrInfoWithId("terminalSubType", resRsp.get("terminalTSubType"), id);
        Map tradeId = lan.createAttrInfoWithId("TRADE_ID", exchange.getProperty("newTradeId"), id);
        Map errorOrderId = lan.createAttrInfoWithId("ERROR_ORDER_ID", oldMsg.get("checkId"), userInfo.get("userId")
                .toString());

        Map map = new HashMap();

        for (int i = 0; i < traSubItem.size(); i++) {
            map = traSubItem.get(i);
            if (delOld.contains(map.get("attrCode").toString())) {
                traSubItem.remove(i);
                i--;
            }
            map.put("itemId", id);
        }

        traSubItem.add(staffId);
        traSubItem.add(departId);
        traSubItem.add(deviceType);
        traSubItem.add(imei);
        traSubItem.add(mobilesaleprice);
        traSubItem.add(mobilecost);
        traSubItem.add(resourcesBrandCode);
        traSubItem.add(resourcesBrandName);
        traSubItem.add(resourcesModelCode);
        traSubItem.add(resourcesModelName);
        if (!(resRsp.get("resourcesColor") == null || "".equals(resRsp.get("resourcesColor")))) {
            traSubItem.add(mobileinfo);
        }
        traSubItem.add(deviceno);
        traSubItem.add(distributionTag);
        traSubItem.add(terminalType);
        if (!(resRsp.get("terminalTSubType") == null || "".equals(resRsp.get("terminalTSubType")))) {
            traSubItem.add(terminalTSubType);
        }
        traSubItem.add(tradeId);

        for (Map m : traSubItem) {
            m.put("xDatatype", "NULL");
            m.put("attrTypeCode", "6");
        }

        errorOrderId.put("xDatatype", "NULL");
        errorOrderId.put("attrTypeCode", "0");
        traSubItem.add(errorOrderId);

        tradeSubItem.put("item", traSubItem);
        return tradeSubItem;
    }

    private Map preTradePurchase(List<Map> purchase, Map resRsp, Map oldMsg, Exchange exchange) {
        Map tradePurchase = new HashMap();
        List<Map> items = new ArrayList<Map>();
        // 从三户中查询到的老终端信息
        Map item = new HashMap(purchase.get(0));
        Map myResRsp = new HashMap(resRsp);
        Map newItem = new HashMap(item);

        item.put("foregiftBankmod", item.get("fordgiftBackmode"));
        myResRsp.put("deviceName", myResRsp.get("machineTypeName"));
        myResRsp.put("moblieCost", Integer.parseInt((String) myResRsp.get("cost")) / 10);
        myResRsp.put("itemId", exchange.getProperty("newItemId"));
        myResRsp.put("departId", oldMsg.get("channelId"));
        myResRsp.put("staffId", oldMsg.get("operatorId"));
        myResRsp.put("imei", myResRsp.get("resourcesCode"));
        myResRsp.put("deviceBrand", myResRsp.get("resourcesBrandCode"));
        myResRsp.put("packgaeId", item.get("packageId"));
        myResRsp.put("deviceType", myResRsp.get("machineTypeCode"));

        item.putAll(myResRsp);
        item.remove("attrInfo");

        items.add(item);

        newItem.put("foregiftBankmod", newItem.get("fordgiftBackmode"));
        newItem.put("endDate", GetDateUtils.getDate());
        newItem.put("moblieCost", newItem.get("mobileCost"));
        newItem.put("departId", oldMsg.get("channelId"));
        newItem.put("staffId", oldMsg.get("operatorId"));
        newItem.put("packgaeId", newItem.get("packageId"));
        newItem.remove("attrInfo");

        items.add(newItem);
        tradePurchase.put("item", items);
        return tradePurchase;
    }

    private Map preBase(Map oldMsg, Exchange exchange) {
        List<Map> userInfos = (List<Map>) exchange.getOut().getBody(Map.class).get("userInfo");
        Map userInfo = userInfos.get(0);
        List<Map> custInfos = (List<Map>) exchange.getOut().getBody(Map.class).get("custInfo");
        Map custInfo = custInfos.get(0);
        List<Map> acctInfos = (List<Map>) exchange.getOut().getBody(Map.class).get("acctInfo");
        Map acctInfo = acctInfos.get(0);

        Map base = new HashMap();
        base.put("termIp", exchange.getProperty(exchange.HTTP_REMOTE_ADDR));
        base.put("tradeTypeCode", "2802");
        base.put("cityCode", oldMsg.get("district"));
        base.put("userDiffCode", userInfo.get("userDiffCode"));
        base.put("inModeCode", "E");
        base.put("productId", userInfo.get("productId"));
        base.put("userId", userInfo.get("userId"));
        base.put("serinalNamber", oldMsg.get("number"));
        base.put("usecustId", userInfo.get("usecustId"));
        base.put("remark", userInfo.get("remark"));
        base.put("contactPhone", custInfo.get("contactPhone"));

        String eparchy = ChangeCodeUtils.changeEparchy(MapUtils.asMap("province", oldMsg.get("province"), "city",
                oldMsg.get("city")));
        base.put("eparchyCode", eparchy);
        base.put("netTypeCode", "50");
        base.put("contact", custInfo.get("contact"));
        base.put("contactAddress", "");
        base.put("custName", custInfo.get("contact"));
        base.put("acctId", acctInfo.get("acctId"));
        base.put("custId", custInfo.get("custId"));

        base.put("subscribeId", exchange.getProperty("newTradeId"));
        base.put("brandCode", userInfo.get("brandCode"));
        base.put("tradeId", exchange.getProperty("newTradeId"));
        // base.put("cancelTag", "0");

        return base;
    }

    private Map preResourceInfo(Map resInfo, Map msg) {
        resInfo.put("resourcesType", "07");
        resInfo.put("resourcesCode", msg.get("newResourcesCode"));
        resInfo.put("oldResourcesCode", msg.get("oldResourcesCode"));
        resInfo.put("operType", "1");
        resInfo.put("useType", "0");
        resInfo.put("occupiedFlag", "1");
        resInfo.put("tradeType", "04");
        return resInfo;
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
}
