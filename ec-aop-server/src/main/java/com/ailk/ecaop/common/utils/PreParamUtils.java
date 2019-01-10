package com.ailk.ecaop.common.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.lang.RDate;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.impl.DefaultMessage;

import com.ailk.ecaop.biz.sub.MixOpenAppProcessor;
import com.alibaba.fastjson.JSON;

/**
 * 用于调用接口整合参数列表
 */
public class PreParamUtils {

    private boolean isOld;

    public Exchange preSub4CBSS(Exchange exchange, Exchange threePartExchange) {
        if (null != threePartExchange) {
            isOld = true;
        }
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map base = preBase4CBSS(exchange, threePartExchange);
        Map ext = new HashMap();
        if (null != msg.get("mixTemplate")) {
            ext = preMixExt4CBSS(exchange, threePartExchange);
        }
        else if (null != msg.get("broadBandTemplate"))
        {
            ext = preBroadBandExt4CBSS(exchange, threePartExchange);
        }
        msg = new HashMap();
        Message message = new DefaultMessage();
        msg.put("ordersId", "1715081429932293");
        msg.put("serviceClassCode", "00CP");
        msg.put("operTypeCode", "0");
        msg.put("base", base);
        msg.put("ext", ext);
        message.setBody(MapUtils.asMap("msg", JSON.toJSON(msg)));
        exchange.setIn(message);
        return exchange;
    }

    /**
     * 为CB统一预提交接口BASE节点提供参数
     */
    public Map preBase4CBSS(Exchange exchange, Exchange threePartExchange) {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map template = new HashMap();
        Map custInfo = new HashMap();
        Map product = new HashMap();
        if (null != msg.get("mixTemplate")) {
            template = (Map) msg.get("mixTemplate");
            custInfo = (Map) template.get("mixCustInfo");
            List<Map> productList = (List<Map>) template.get("productInfo");
            product = productList.get(0);
        }
        else
        {
            template = (Map) msg.get("broadBandTemplate");
            custInfo = (Map) template.get("broadBandCustInfo");
            product = (Map) template.get("broadBandProduct");
        }
        // Map broadBandTemplate = (Map) msg.get("broadBandTemplate");
        Map custCheckRetMap = (Map) msg.get("custCheckRetMap");
        Map base = new HashMap();
        base.put("subscribeId", exchange.getProperty("tradeId"));
        base.put("tradeId", exchange.getProperty("tradeId"));
        base.put("startDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("endDate", "20491231235959");
        base.put("acceptDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("nextDealTag", "Z");
        // base.put("olcomTag", "");
        // base.put("inModeCode", "");
        base.put("userDiffCode", "00");
        base.put("productId", product.get("productId"));
        base.put("custId", custCheckRetMap.get("custId"));
        base.put("usecustId", custCheckRetMap.get("custId"));
        base.put("custName", custCheckRetMap.get("custName"));
        base.put("serinalNamber", template.get("acctSerialNumber"));
        base.put("eparchyCode", msg.get("district"));
        base.put("cityCode", msg.get("city"));
        base.put("execTime", RDate.currentTimeStr("yyyyMMddHHmmss"));
        // base.put("termIp", RDate.currentTimeStr("127.0.0.1"));
        // base.put("operFee", "0");
        // base.put("foregift", "0");
        // base.put("advancePay", "");
        base.put("feeState", "");
        base.put("feeStaffId", "");
        base.put("cancelTag", "0");
        base.put("checktypeCode", custInfo.get("checkType"));
        base.put("chkTag", "0");
        base.put("actorName", "");
        base.put("actorCertTypeId", "");
        base.put("actorPhone", "");
        base.put("actorCertNum", "");
        base.put("contact", custInfo.get("contactPerson"));
        base.put("contactPhone", custInfo.get("contactPhone"));
        base.put("contactAddress", custInfo.get("contactAddress"));
        base.put("remark", "");
        if ("1".equals(custInfo.get("custType")))
        {
            Map threePartBody = threePartExchange.getOut().getBody(Map.class);
            Map threePartMap = new HashMap();
            List<Map> userInfoList = (List<Map>) threePartBody.get("userInfo");
            List<Map> custInfoList = (List<Map>) threePartBody.get("custInfo");
            List<Map> acctInfoList = (List<Map>) threePartBody.get("acctInfo");
            if (userInfoList.size() != 0) {
                threePartMap.putAll(userInfoList.get(0));
            }
            if (custInfoList.size() != 0) {
                threePartMap.putAll(custInfoList.get(0));
            }
            if (acctInfoList.size() != 0) {
                threePartMap.putAll(acctInfoList.get(0));
            }
            base.put("tradeTypeCode", "0031");
            base.put("userId", threePartMap.get("userId"));
            base.put("acctId", threePartMap.get("acctId"));
            base.put("userDiffCode", threePartMap.get("userDiffCode"));
        }
        else
        {
            base.put("tradeTypeCode", "0010");
        }
        return base;
    }

    /**
     * 为CB固网统一预提交接口EXT节点提供参数
     */
    public Map preBroadBandExt4CBSS(Exchange exchange, Exchange threePartExchange) {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        // Map template = null != msg.get("mixTemplate") ? (Map) msg.get("mixTemplate") : (Map) msg
        // .get("broadBandTemplate");
        // List<Map> broadBandProductList = (List<Map>) template.get("broadBandProduct");
        // Map broadBandProduct = broadBandProductList.get(0);
        Map template = (Map) msg.get("broadBandTemplate");
        // Map custInfo = (Map) template.get("broadBandCustInfo");
        List<Map> productList = (List<Map>) template.get("broadBandProduct");
        Map product = productList.get(0);
        List<Map> packageElementList = (List<Map>) product.get("packageElement");
        Map ext = new HashMap();
        Map tradeProductType = new HashMap();
        Map tradeUser = preBroadBandTradeUsr(exchange, threePartExchange);
        Map tradeProduct = new HashMap();
        Map tradeDiscnt = new HashMap();
        Map tradeSvc = new HashMap();
        Map tradeRes = new HashMap();
        Map tradePayrelation = new HashMap();
        Map tradeItem = preBroadBandTradeItem(exchange, threePartExchange);
        Map tradeSubItem = preBroadBandTradeSubItem(exchange, threePartExchange);
        // tradeProductType节点
        Map tradeProductTypeItem = new HashMap();
        List<Map> tradeProductTypeItemList = new ArrayList<Map>();
        tradeProductTypeItem.put("xDatatype", null);
        if (!isOld)
        {
            tradeProductTypeItem.put("userId", exchange.getProperty("itemId"));
        }
        tradeProductTypeItem.put("productMode", "0" + product.get("productMode"));
        tradeProductTypeItem.put("productId", product.get("productId"));
        tradeProductTypeItem.put("productTypeCode", "GZKD002");
        tradeProductTypeItem.put("modifyTag", "0");
        tradeProductTypeItem.put("startDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        tradeProductTypeItem.put("endDate", "20501231000000");
        tradeProductTypeItemList.add(tradeProductTypeItem);
        tradeProductType.put("item", tradeProductTypeItemList);

        // tradeProduct节点
        Map tradeProductItem = new HashMap();
        List<Map> tradeProductItemList = new ArrayList<Map>();
        tradeProductItem.put("xDatatype", null);
        if (!isOld)
        {
            tradeProductItem.put("userId", exchange.getProperty("itemId"));
        }
        tradeProductItem.put("productMode", "0" + product.get("productMode"));
        tradeProductItem.put("productId", product.get("productId"));
        tradeProductItem.put("brandCode", "GZKD");
        tradeProductItem.put("itemId", "");
        tradeProductItem.put("modifyTag", "0");
        tradeProductItem.put("startDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        tradeProductItem.put("endDate", "20501231000000");
        tradeProductItem.put("userIdA", "-1");
        tradeProductItemList.add(tradeProductItem);
        tradeProduct.put("item", tradeProductItemList);

        // tradeDiscnt节点
        List<Map> tradeDiscntItemList = new ArrayList<Map>();
        for (Map packageElement : packageElementList)
        {
            Map tradeDiscntItem = new HashMap();
            tradeDiscntItem.put("xDatatype", null);
            tradeDiscntItem.put("id", exchange.getProperty("itemId"));
            tradeDiscntItem.put("idType", "1");
            tradeDiscntItem.put("userIdA", "-1");
            tradeDiscntItem.put("productId", product.get("productId"));
            tradeDiscntItem.put("packageId", packageElement.get("packageId"));
            tradeDiscntItem.put("discntCode", "7111005");
            tradeDiscntItem.put("specTag", "0");
            tradeDiscntItem.put("relationTypeCode", "");
            tradeDiscntItem.put("startDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
            tradeDiscntItem.put("endDate", "20501231000000");
            tradeDiscntItem.put("modifyTag", "0");
            tradeDiscntItem.put("itemId", "");
            tradeDiscntItemList.add(tradeProductItem);
        }
        tradeDiscnt.put("item", tradeDiscntItemList);

        // tradeSvc节点
        List<Map> tradeSvcItemList = new ArrayList<Map>();
        if (!isOld)
        {
            Map tradeSvcItem = new HashMap();
            tradeSvcItem.put("xDatatype", null);
            tradeSvcItem.put("serviceId", "40000");
            tradeSvcItem.put("modifyTag", "0");
            tradeSvcItem.put("startDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
            tradeSvcItem.put("endDate", "20501231000000");
            tradeSvcItem.put("productId", product.get("productId"));
            tradeSvcItem.put("packageId", packageElementList.get(0).get("packageId"));
            tradeSvcItem.put("itemId", "");
            tradeSvcItem.put("userIdA", "-1");
            tradeSvcItemList.add(tradeSvcItem);
        }
        else
        {
            Map tradeSvcItemOne = new HashMap();
            tradeSvcItemOne.put("xDatatype", null);
            tradeSvcItemOne.put("serviceId", "50000");
            tradeSvcItemOne.put("modifyTag", "0");
            tradeSvcItemOne.put("startDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
            tradeSvcItemOne.put("endDate", "20501231000000");
            tradeSvcItemOne.put("productId", product.get("productId"));
            tradeSvcItemOne.put("packageId", packageElementList.get(0).get("packageId"));
            tradeSvcItemOne.put("itemId", "");
            tradeSvcItemOne.put("userIdA", "-1");
            tradeSvcItemList.add(tradeSvcItemOne);
            Map tradeSvcItemTwo = new HashMap();
            tradeSvcItemTwo.put("xDatatype", null);
            tradeSvcItemTwo.put("serviceId", "50010");
            tradeSvcItemTwo.put("modifyTag", "0");
            tradeSvcItemTwo.put("startDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
            tradeSvcItemTwo.put("endDate", "20501231000000");
            tradeSvcItemTwo.put("productId", product.get("productId"));
            tradeSvcItemTwo.put("packageId", packageElementList.get(0).get("packageId"));
            tradeSvcItemTwo.put("itemId", "");
            tradeSvcItemTwo.put("userIdA", "-1");
            tradeSvcItemList.add(tradeSvcItemTwo);
            Map tradeSvcItemThree = new HashMap();
            tradeSvcItemThree.put("xDatatype", null);
            tradeSvcItemThree.put("serviceId", "50014");
            tradeSvcItemThree.put("modifyTag", "0");
            tradeSvcItemThree.put("startDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
            tradeSvcItemThree.put("endDate", "20501231000000");
            tradeSvcItemThree.put("productId", product.get("productId"));
            tradeSvcItemThree.put("packageId", packageElementList.get(0).get("packageId"));
            tradeSvcItemThree.put("itemId", "");
            tradeSvcItemThree.put("userIdA", "-1");
            tradeSvcItemList.add(tradeSvcItemThree);
        }
        tradeSvc.put("item", tradeSvcItemList);
        // tradeRes节点
        Map tradeResItem = new HashMap();
        List<Map> tradeResItemList = new ArrayList<Map>();
        tradeResItem.put("xDatatype", null);
        tradeResItem.put("reTypeCode", "0");
        tradeResItem.put("resCode", "053100346935");
        tradeResItem.put("modifyTag", "0");
        tradeResItem.put("startDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        tradeResItem.put("endDate", "20501231000000");
        tradeResItemList.add(tradeResItem);
        tradeRes.put("item", tradeResItemList);
        // tradePayrelation节点
        Map tradePayrelationItem = new HashMap();
        List<Map> tradePayrelationItemList = new ArrayList<Map>();
        tradePayrelationItem.put("payitemCode", "-1");
        tradePayrelationItem.put("acctPriority", "0");
        tradePayrelationItem.put("userPriority", "0");
        tradePayrelationItem.put("bindType", "1");
        tradePayrelationItem.put("defaultTag", "0");
        tradePayrelationItem.put("limitType", "0");
        tradePayrelationItem.put("limit", "0");
        tradePayrelationItem.put("complementTag", "0");
        tradePayrelationItem.put("addupMonths", "0");
        tradePayrelationItem.put("addupMethod", "0");
        // tradePayrelationItem.put("payrelationId", "1715081423856470");
        tradePayrelationItem.put("actTag", "1");
        tradePayrelationItemList.add(tradePayrelationItem);
        tradePayrelation.put("item", tradePayrelationItemList);

        ext.put("tradeUser", tradeUser);
        ext.put("tradeProductType", tradeProductType);
        ext.put("tradeProduct", tradeProduct);
        ext.put("tradeDiscnt", tradeDiscnt);
        ext.put("tradeSvc", tradeSvc);
        ext.put("tradeRes", tradeRes);
        ext.put("tradePayrelation", tradePayrelation);
        ext.put("tradeItem", tradeItem);
        ext.put("tradeSubItem", tradeSubItem);
        return ext;
    }

    /**
     * 为CB虚拟用户统一预提交接口EXT节点提供参数
     */
    public Map preMixExt4CBSS(Exchange exchange, Exchange threePartExchange) {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");

        Map template = (Map) msg.get("mixTemplate");
        List<Map> productList = (List<Map>) template.get("productInfo");
        Map ext = new HashMap();
        ext.putAll(TradeManagerUtils.preProductInfo(productList, "0018", msg));
        Map tradeUser = preBroadBandTradeUsr(exchange, threePartExchange);
        Map tradePayrelation = new HashMap();
        Map tradeItem = preBroadBandTradeItem(exchange, threePartExchange);
        Map tradeSubItem = preBroadBandTradeSubItem(exchange, threePartExchange);

        // tradePayrelation节点
        Map tradePayrelationItem = new HashMap();
        List<Map> tradePayrelationItemList = new ArrayList<Map>();
        tradePayrelationItem.put("payitemCode", "-1");
        tradePayrelationItem.put("acctPriority", "0");
        tradePayrelationItem.put("userPriority", "0");
        tradePayrelationItem.put("bindType", "1");
        tradePayrelationItem.put("defaultTag", "0");
        tradePayrelationItem.put("limitType", "0");
        tradePayrelationItem.put("limit", "0");
        tradePayrelationItem.put("complementTag", "0");
        tradePayrelationItem.put("addupMonths", "0");
        tradePayrelationItem.put("addupMethod", "0");
        tradePayrelationItem.put("payrelationId", "1715081423856470");
        tradePayrelationItem.put("actTag", "1");
        tradePayrelationItemList.add(tradePayrelationItem);
        tradePayrelation.put("item", tradePayrelationItemList);

        ext.put("tradeUser", tradeUser);
        ext.put("tradePayrelation", tradePayrelation);
        ext.put("tradeItem", tradeItem);
        ext.put("tradeSubItem", tradeSubItem);
        return ext;
    }

    public Map preBroadBandTradeUsr(Exchange exchange, Exchange threePartExchange) {
        Map msg = (Map) exchange.getIn().getBody(Map.class).get("msg");

        Map tradeUsr = new HashMap();
        List items = new ArrayList();
        Map item = new HashMap();

        item.put("xDatatype", "NULL");
        item.put("cityCode", msg.get("district"));
        item.put("inDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        item.put("openDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        item.put("openMode", "0");

        if (isOld) {
            Map threePartRet = threePartExchange.getOut().getBody(Map.class);
            Map userInfo = (Map) ((List) threePartRet.get("userInfo")).get(0);
            item.put("userPasswd", userInfo.get("userPasswd"));
            item.put("userTypeCode", userInfo.get("userTypeCode"));
            item.put("scoreValue", userInfo.get("scoreValue"));
            item.put("basicCreditValue", userInfo.get("basicCreditValue"));
            item.put("creditValue", userInfo.get("creditValue"));
            item.put("acctTag", userInfo.get("acctTag"));
            item.put("prepayTag", userInfo.get("prepayTag"));
            item.put("openDepartId", userInfo.get("openDepartId"));
            item.put("openStaffId", userInfo.get("openStaffId"));
            item.put("inDepartId", userInfo.get("inDepartId"));
            item.put("inStaffId", userInfo.get("inStaffId"));
            item.put("removeTag", userInfo.get("removeTag"));
            item.put("userStateCodeset", userInfo.get("userStateCodeset"));
            item.put("mputeMonthFee", userInfo.get("mputeMonthFee"));
            item.put("developStaffId", userInfo.get("developStaffId"));
            item.put("developDate", userInfo.get("developDate"));
            item.put("developDepartId", userInfo.get("developDepartId"));
            item.put("inNetMode", userInfo.get("inNetMode"));
            item.put("productTypeCode", userInfo.get("productTypeCode"));
        }

        items.add(item);
        tradeUsr.put("item", items);
        return tradeUsr;
    }

    public Map preBroadBandTradeItem(Exchange exchange, Exchange threePartExchange) {
        LanUtils lan = new LanUtils();

        Map relCompProdId = lan.createAttrInfoNoTime("REL_COMP_PROD_ID", "89017300");
        Map compDealState = lan.createAttrInfoNoTime("COMP_DEAL_STATE", "0");
        Map roleCodeB = lan.createAttrInfoNoTime("ROLE_CODE_B", "4");

        Map standardKindCode = lan.createAttrInfoNoTime("STANDARD_KIND_CODE", "1");
        standardKindCode.put("xDatatype", "NULL");

        Map phNum = lan.createAttrInfoNoTime("PH_NUM", "");
        standardKindCode.put("xDatatype", "NULL");

        Map extraInfo = lan.createAttrInfoNoTime("EXTRA_INFO", "");
        standardKindCode.put("xDatatype", "NULL");

        Map operCode = lan.createAttrInfoNoTime("OPER_CODE", "1");
        standardKindCode.put("xDatatype", "NULL");

        Map userPwd = lan.createAttrInfoNoTime("USER_PASSWD", "123456");
        standardKindCode.put("xDatatype", "NULL");

        Map reopenTag = lan.createAttrInfoNoTime("REOPEN_TAG", "2");
        standardKindCode.put("xDatatype", "NULL");

        Map newPwd = lan.createAttrInfoNoTime("NEW_PASSWD", "123456");
        standardKindCode.put("xDatatype", "NULL");

        Map existsAcct = lan.createAttrInfoNoTime("EXISTS_ACCT", "1");
        standardKindCode.put("xDatatype", "NULL");

        Map sfgx2060 = lan.createAttrInfoNoTime("SFGX_2060", "N");
        standardKindCode.put("xDatatype", "NULL");

        Map gxlxTansz = lan.createAttrInfoNoTime("GXLX_TANGSZ", "");
        standardKindCode.put("xDatatype", "NULL");

        Map aloneTcsCompIndex = lan.createAttrInfoNoTime("ALONE_TCS_COMP_INDEX", "1");

        List items = new ArrayList();
        items.add(relCompProdId);
        items.add(compDealState);
        items.add(roleCodeB);
        items.add(standardKindCode);
        items.add(phNum);
        items.add(extraInfo);
        items.add(operCode);
        items.add(userPwd);
        items.add(reopenTag);
        items.add(newPwd);
        items.add(existsAcct);
        items.add(sfgx2060);
        items.add(gxlxTansz);
        items.add(aloneTcsCompIndex);

        Map tradeItem = new HashMap();
        tradeItem.put("item", items);
        return tradeItem;
    }

    public Map preBroadBandTradeSubItem(Exchange exchange, Exchange threePartExchange) {
        LanUtils lan = new LanUtils();
        Map tradeSubItem = new HashMap();
        List items = new ArrayList();

        Map msg = (Map) exchange.getIn().getBody(Map.class).get("msg");

        String id = exchange.getProperty("itemId", String.class);
        Map template = new HashMap();
        Map serialNumber = new HashMap();
        if (null != msg.get("mixTemplate")) {
            template = (Map) msg.get("mixTemplate");
            serialNumber = lan.createTradeSubItem("serialNumber", "1231231231231231", id);
        }
        else if (null != msg.get("broadBandTemplate"))
        {
            template = (Map) msg.get("broadBandTemplate");
            serialNumber = lan.createTradeSubItem("serialNumber", template.get("acctSerialNumber"), id);
        }
        Map addressId = lan.createTradeSubItem("ADDRESS_ID", "01000105424828278", id);
        Map shareNbr = lan.createTradeSubItem("SHARE_NBR", "", id);
        Map useType = lan.createTradeSubItem("USETYPE", "1", id);
        Map muduleExchId = lan.createTradeSubItem("MODULE_EXCH_ID", "", id);
        Map wopayMoney = lan.createTradeSubItem("WOPAY_MONEY", "", id);
        Map areaCode = lan.createTradeSubItem("AREA_CODE", "", id);
        Map accessType = lan.createTradeSubItem("ACCESS_TYPE", "B31", id);
        Map timeLimitId = lan.createTradeSubItem("TIME_LIMIT_ID", "2", id);
        Map pointExchId = lan.createTradeSubItem("POINT_EXCH_ID", "01795", id);
        Map userPwd = lan.createTradeSubItem("USER_PASSWD", "123456", id);
        Map connectNetMode = lan.createTradeSubItem("CONNECTNETMODE", "1", id);
        Map isWall = lan.createTradeSubItem("ISWAIL", "0", id);
        Map tmlSn = lan.createTradeSubItem("TERMINAL_SN", "", id);
        Map tmlMac = lan.createTradeSubItem("TERMINAL_MAC", "", id);
        Map switchExchId = lan.createTradeSubItem("SWITCH_EXCH_ID", "", id);
        Map isWopay = lan.createTradeSubItem("ISWOPAY", "0", id);
        Map tmlSrcMode = lan.createTradeSubItem("TERMINALSRC_MODE", "A003", id);
        Map areaExchId = lan.createTradeSubItem("AREA_EXCH_ID", "CLJ01000105", id);
        Map mofficeId = lan.createTradeSubItem("MOFFICE_ID", "1", id);
        Map communitId = lan.createTradeSubItem("COMMUNIT_ID", "0", id);
        Map usrTypeCode = lan.createTradeSubItem("USER_TYPE_CODE", "0", id);
        Map cbAccessType = lan.createTradeSubItem("CB_ACCESS_TYPE", "A11", id);
        Map acctNbr = lan.createTradeSubItem("ACCT_NBR", "", id);
        Map commpanyNbr = lan.createTradeSubItem("COMMPANY_NBR", "", id);
        Map detailInstallAddress = lan.createTradeSubItem("DETAIL_INSTALL_ADDRESS", "", id);
        Map townFlag = lan.createTradeSubItem("TOWN_FLAG", "C", id);
        Map tmlModel = lan.createTradeSubItem("TERMINAL_MODEL", "", id);
        Map speed = lan.createTradeSubItem("SPEED", "4", id);
        Map locNetCode = lan.createTradeSubItem("LOCAL_NET_CODE", "0531", id);
        Map exceptRate = lan.createTradeSubItem("EXPECT_RATE", "", id);
        Map installAddr = lan.createTradeSubItem("INSTALL_ADDRESS", "", id);
        Map tmlType = lan.createTradeSubItem("TERMINAL_TYPE", "0", id);
        Map tmlBrand = lan.createTradeSubItem("TERMINAL_BRAND", "", id);
        Map communitName = lan.createTradeSubItem("COMMUNIT_NAME", "", id);
        Map initPwd = lan.createTradeSubItem("INIT_PASSWD", "1", id);
        Map collType = lan.createTradeSubItem("COLLINEAR_TYPE", "X3", id);
        Map acctPwd = lan.createTradeSubItem("ACCT_PASSWD", "123456", id);

        items.add(addressId);
        items.add(shareNbr);
        items.add(useType);
        items.add(muduleExchId);
        items.add(wopayMoney);
        items.add(areaCode);
        items.add(serialNumber);
        items.add(accessType);
        items.add(timeLimitId);
        items.add(pointExchId);
        items.add(userPwd);
        items.add(connectNetMode);
        items.add(isWall);
        items.add(tmlSn);
        items.add(tmlMac);
        items.add(switchExchId);
        items.add(isWopay);
        items.add(tmlSrcMode);
        items.add(areaExchId);
        items.add(mofficeId);
        items.add(communitId);
        items.add(usrTypeCode);
        items.add(cbAccessType);
        items.add(acctNbr);
        items.add(commpanyNbr);
        items.add(detailInstallAddress);
        items.add(townFlag);
        items.add(tmlModel);
        items.add(speed);
        items.add(locNetCode);
        items.add(exceptRate);
        items.add(installAddr);
        items.add(tmlType);
        items.add(tmlBrand);
        items.add(communitName);
        items.add(initPwd);
        items.add(collType);
        items.add(acctPwd);
        tradeSubItem.put("item", items);
        return tradeSubItem;
    }

    private Map preProductChange(Exchange exchange, List<Map> newPhoneProduct, Map inMap) {
        for (Map phone : newPhoneProduct) {
            phone.put("productMode", "1".equals(phone.get("productMode")) ? "1" : "0");
            phone.put("optType", "00");
        }
        Map retMap = MapUtils.asMap("serialNumber", inMap.get("serialNumber"), "productId");
        retMap.putAll((Map) inMap.get("phoneRecomInfo"));
        inMap.put("tradeTypeCode", "0031");
        inMap.put("getMode", "1111110010000000000000000000001");
        inMap.put("serviceClassCode", "0000");
        Exchange threePartExchange = new MixOpenAppProcessor().getThreeInfo(ExchangeUtils.ofCopy(exchange, inMap));
        Map userInfo = threePartExchange.getOut().getBody(Map.class);
        Map oldPrd = MapUtils.asMap("productId", userInfo.get("nextProductId"), "productMode", "1", "optType", "01");
        newPhoneProduct.add(oldPrd);
        retMap.put("productInfo", newPhoneProduct);
        return retMap;
    }

    private Map pre23To4() {
        return new HashMap();
    }

    public Map prePhoneTemplate(Exchange exchange, List<Map> newPhoneProduct, Map inMap) {
        Object installMode = inMap.get("installMode");
        if ("0".equals(installMode)) {
            return pre23To4();
        }
        if ("1".equals(installMode)) {
            return preProductChange(exchange, newPhoneProduct, inMap);
        }
        return pre23To4();
    }
}
