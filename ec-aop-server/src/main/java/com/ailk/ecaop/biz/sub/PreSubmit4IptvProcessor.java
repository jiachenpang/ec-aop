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
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.helper.cbss.TradeSubItemFactory_0;
import com.ailk.ecaop.common.helper.cbss.TradeSubItemFactory_2;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;

@EcRocTag("preSumitIPTV")
public class PreSubmit4IptvProcessor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.trade.cbss.checkUserParametersMapping",
            "ecaop.masb.chph.gifa.ParametersMapping",
            "ecaop.trades.sccc.cancelPre.paramtersmapping",
            "ecaop.trades.sccc.cancel.paramtersmapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[PARAM_ARRAY.length];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Object shareSerialNumber = msg.get("shareSerialNumber");
        if (IsEmptyUtils.isEmpty(shareSerialNumber)) {
            throw new EcAopServerBizException("9999", "未下发共线号码信息" + appendApptx(body.get("apptx")));
        }
        Object newUserInfo = msg.get("newUserInfo");
        if (IsEmptyUtils.isEmpty(newUserInfo)) {
            throw new EcAopServerBizException("9999", "未下发用户信息" + appendApptx(body.get("apptx")));
        }
        // 调用三户接口,为预提交接口准备信息
        Map threePart = new HashMap();
        threePart.put("serialNumber", msg.get("shareSerialNumber"));
        threePart.put("areaCode", msg.get("shareAreaCode"));
        threePart.put("serviceClassCode", "0030");
        threePart.put("tradeTypeCode", "9999");
        threePart.put("getMode", "");
        MapUtils.arrayPut(threePart, msg, MagicNumber.COPYARRAY);
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePart);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[0], threePartExchange);
        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.UsrForNorthSer." + msg.get("province"));
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        Map threePartRet = threePartExchange.getOut().getBody(Map.class);

        // 预提交
        Map preSubmit = new HashMap();
        MapUtils.arrayPut(preSubmit, msg, MagicNumber.COPYARRAY);
        Map tradeMap = new HashMap();
        MapUtils.arrayPut(tradeMap, msg, MagicNumber.COPYARRAY);
        Object ordersId = GetSeqUtil.getSeqFromCb(pmp[1], ExchangeUtils.ofCopy(exchange, tradeMap), "seq_trade_id", 1)
                .get(0);
        preSubmit.put("ordersId", ordersId);
        preSubmit.put("serinalNamber", msg.get("serialNumber"));
        preSubmit.put("serviceClassCode", "0040");
        preSubmit.put("operTypeCode", "0");
        Object acctId = getAcctId(exchange, threePartRet);
        msg.put("ordersId", ordersId);
        msg.put("acctId", acctId);
        preSubmit.put("ext", preExt(threePartRet, msg, exchange));
        preSubmit.put("base", preBase(threePartRet, msg, exchange.getAppCode()));
        Exchange preSubmitExchange = ExchangeUtils.ofCopy(exchange, preSubmit);
        lan.preData(pmp[2], preSubmitExchange);
        CallEngine.wsCall(preSubmitExchange, "ecaop.comm.conf.url.UsrForNorthSer." + msg.get("province"));
        lan.xml2Json("ecaop.trades.sccc.cancelPre.template", preSubmitExchange);

        // 返回报文整理
        Map preSubmitRet = preSubmitExchange.getOut().getBody(Map.class);
        Map rspInfo = ((List<Map>) preSubmitRet.get("rspInfo")).get(0);
        Message out = new DefaultMessage();
        Map retMap = MapUtils.asMap("provOrderId", rspInfo.get("provOrderId"));
        List<Map> provinceOrderInfo = (List<Map>) rspInfo.get("provinceOrderInfo");
        retMap.put("feeInfo", provinceOrderInfo.get(0).get("feeInfo"));
        retMap.put("totalFee", provinceOrderInfo.get(0).get("totalFee"));
        out.setBody(retMap);
        exchange.setOut(out);
    }

    private Map preExt(Map threePartRet, Map msg, Exchange exchange) {
        Map ext = new HashMap();
        // 工具类中0是主产品
        List<Map> productInfo = (List<Map>) ((Map) msg.get("newUserInfo")).get("productInfo");
        if (IsEmptyUtils.isEmpty(productInfo)) {
            throw new EcAopServerBizException("9999", "未下发产品信息");
        }
        for (Map product : productInfo) {
            if ("1".equals(product.get("productMode"))) {
                msg.put("mainProduct", product.get("productId"));
                product.put("productMode", "0");
            }
            else {
                product.put("productMode", "1");
            }
        }
        Map ret = TradeManagerUtils.preProductInfo(productInfo, msg.get("province"), msg);
        ext.put("tradeUser", tradeUser(threePartRet, msg, ret));
        ext.put("tradeProductType", tradeProductType(msg));
        ext.put("tradeDiscnt", tradeDiscnt(msg));
        ext.put("tradeSubItem", tradeSubItem(exchange, msg));
        ext.put("tradeProduct", tradeProduct(msg));
        ext.put("tradeSvc", tradeSvc(msg));
        ext.put("tradeSp", tradeSp(msg));
        ext.put("tradeItem", tradeItem(msg));
        ext.put("tradePayrelation", tradePayrelation(threePartRet, msg, exchange));
        return ext;
    }

    /**
     * 拼装tradediscnt节点
     */
    public Map tradeDiscnt(Map msg) {
        try {
            Map tradeDis = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            List<Map> discnt = (List<Map>) msg.get("discntList");
            for (int i = 0; i < discnt.size(); i++) {
                String packageId = String.valueOf(discnt.get(i).get("packageId"));
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("id", msg.get("userId"));
                item.put("idType", "1");
                item.put("userIdA", "-1");
                item.put("productId", discnt.get(i).get("productId"));
                item.put("packageId", packageId);
                item.put("discntCode", discnt.get(i).get("discntCode"));
                item.put("specTag", "1");
                item.put("relationTypeCode", "");
                item.put("startDate", msg.get("date"));
                item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                // 副卡月功能费 要求下月生效 需要特殊处理
                if ("51184968".equals(packageId)) {
                    item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                }
                else {
                    if (null != discnt.get(i).get("activityStarTime")) {
                        item.put("startDate", discnt.get(i).get("activityStarTime"));
                    }
                    if (null != discnt.get(i).get("activityTime")) {
                        item.put("endDate", discnt.get(i).get("activityTime"));
                    }
                }
                item.put("modifyTag", "0");
                item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
                itemList.add(item);

            }
            Map numberDiscnt = (Map) msg.get("numberDiscnt");
            if (null != numberDiscnt && !numberDiscnt.isEmpty()) {
                Map item = new HashMap();
                item.putAll(numberDiscnt);
                itemList.add(item);
            }
            Map numberDis = (Map) msg.get("numberDis");
            if (null != numberDis && !numberDis.isEmpty()) {
                Map item1 = new HashMap();
                item1.putAll(numberDiscnt);
                itemList.add(item1);

            }

            tradeDis.put("item", itemList);
            return tradeDis;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_DISCNT节点报错" + e.getMessage());
        }
    }

    /**
     * 拼装tradeSp节点
     */
    public Map tradeSp(Map msg) {
        try {
            Map tardeSp = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            List<Map> sp = (List<Map>) msg.get("spList");
            for (int i = 0; i < sp.size(); i++) {
                Map item = new HashMap();
                item.put("userId", msg.get("userId"));
                item.put("serialNumber", msg.get("serialNumber"));
                item.put("productId", sp.get(i).get("productId"));
                item.put("packageId", sp.get(i).get("packageId"));
                item.put("partyId", sp.get(i).get("partyId"));
                item.put("spId", sp.get(i).get("spId"));
                item.put("spProductId", sp.get(i).get("spProductId"));
                item.put("firstBuyTime", GetDateUtils.getDate());
                item.put("paySerialNumber", msg.get("serialNumber"));
                item.put("startDate", msg.get("date"));
                item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                if (null != sp.get(i).get("activityStarTime")) {
                    item.put("startDate", sp.get(i).get("activityStarTime"));
                }
                if (null != sp.get(i).get("activityTime")) {
                    item.put("endDate", sp.get(i).get("activityTime"));
                }
                item.put("updateTime", msg.get("date"));
                item.put("remark", "");
                item.put("modifyTag", "0");
                item.put("payUserId", msg.get("userId"));
                item.put("spServiceId", sp.get(i).get("spServiceId"));
                item.put("itemId", msg.get("itemId"));
                item.put("userIdA", msg.get("userId"));
                item.put("xDatatype", "NULL");
                itemList.add(item);
            }
            tardeSp.put("item", itemList);
            return tardeSp;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_SP节点报错" + e.getMessage());
        }
    }

    /**
     * 拼装TRADE_PRODUCT_TYPE节点
     */
    public Map tradeProductType(Map msg) {
        try {
            Map tradeProductType = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            List<Map> productTpye = (List<Map>) msg.get("productTypeList");
            for (int i = 0; i < productTpye.size(); i++) {
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("userId", msg.get("userId"));
                String productMode = (String) productTpye.get(i).get("productMode");
                item.put("productMode", productMode);
                item.put("productId", productTpye.get(i).get("productId"));
                item.put("productTypeCode", productTpye.get(i).get("productTypeCode"));
                item.put("modifyTag", "0");
                item.put("startDate", msg.get("date"));
                item.put("endDate", "20501231235959");
                if (null != productTpye.get(i).get("activityStarTime")) {
                    item.put("startDate", productTpye.get(i).get("activityStarTime"));
                }
                if (null != productTpye.get(i).get("activityTime")) {
                    item.put("endDate", productTpye.get(i).get("activityTime"));
                }
                itemList.add(item);
            }
            tradeProductType.put("item", itemList);
            return tradeProductType;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_PRODUCT_TYPE节点报错" + e.getMessage());
        }
    }

    /**
     * 用来拼装tradeProduct节点
     */
    public Map tradeProduct(Map msg) {
        try {
            Map tradeProduct = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            List<Map> product = (List<Map>) msg.get("productList");
            for (int i = 0; i < product.size(); i++) {
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("userId", msg.get("userId"));
                String productMode = (String) product.get(i).get("productMode");
                item.put("productMode", productMode);
                item.put("productId", product.get(i).get("productId"));
                item.put("brandCode", product.get(i).get("brandCode"));
                item.put("itemId", msg.get("itemId"));
                item.put("modifyTag", "0");
                item.put("startDate", msg.get("date"));
                item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                if (null != product.get(i).get("activityStarTime")) {
                    item.put("startDate", product.get(i).get("activityStarTime"));
                }
                if (null != product.get(i).get("activityTime")) {
                    item.put("endDate", product.get(i).get("activityTime"));
                }
                item.put("userIdA", MagicNumber.DEFAULT_NO_VALUE);
                itemList.add(item);
            }

            tradeProduct.put("item", itemList);
            return tradeProduct;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_PRODUCT节点报错" + e.getMessage());
        }
    }

    private Map tradeSvc(Map msg) {
        try {
            Map svcList = new HashMap();
            List<Map> svc = new ArrayList<Map>();
            svc = (List<Map>) msg.get("svcList");
            List svList = new ArrayList();
            for (int i = 0; i < svc.size(); i++) {
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("userId", msg.get("userId"));
                item.put("serviceId", svc.get(i).get("serviceId"));
                item.put("modifyTag", "0");
                item.put("startDate", msg.get("date"));
                item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                if (null != svc.get(i).get("activityStarTime")) {
                    item.put("startDate", svc.get(i).get("activityStarTime"));
                }
                if (null != svc.get(i).get("activityTime")) {
                    item.put("endDate", svc.get(i).get("activityTime"));
                }
                item.put("productId", svc.get(i).get("productId"));
                item.put("packageId", svc.get(i).get("packageId"));
                item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
                item.put("userIdA", "-1");
                svList.add(item);
            }
            svcList.put("item", svList);
            return svcList;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_SVC节点报错" + e.getMessage());
        }
    }

    /**
     * 准备用户信息节点
     * 
     * @param threePartRet
     * @param msg
     * @return
     */
    private Map tradeUser(Map threePartRet, Map msg, Map ret) {
        Map tradeUser = new HashMap();
        Map item = new HashMap();
        item.put("xDatatype", MagicNumber.STRING_OF_NULL);
        item.put("userId", ((List<Map>) threePartRet.get("userInfo")).get(0).get("userId"));
        item.put("userId", ((List<Map>) threePartRet.get("userInfo")).get(0).get("userId"));
        item.put("custId", ((List<Map>) threePartRet.get("custInfo")).get(0).get("custId"));
        item.put("brandCode", ret.get("brandCode"));
        item.put("productId", msg.get("mainProduct"));
        item.put("eparchyCode", msg.get("city"));
        item.put("cityCode", msg.get("district"));
        item.put("removeTag", "0");
        if (!IsEmptyUtils.isEmpty(msg.get("userPasswd"))) {
            item.put("userPasswd", msg.get("userPasswd"));
        }
        dealDevelopInfo(item, msg);
        item.put("openDepartId", msg.get("channelId"));
        item.put("inDepartId", msg.get("channelId"));
        item.put("inDate", GetDateUtils.getDate());
        item.put("openStaffId", msg.get("operatorId"));
        item.put("inStaffId", msg.get("operatorId"));
        item.put("prepayTag", "0");
        item.put("acctTag", "0");
        item.put("productTypeCode", ret.get("productTypeCode"));
        item.put("userTypeCode", "2");// TODO 暂时写死
        item.put("serialNumber", msg.get("serialNumber"));
        item.put("netTypeCode", "0060");
        List<Map> itemList = new ArrayList<Map>();
        itemList.add(item);
        tradeUser.put("item", itemList);
        return tradeUser;
    }

    /**
     * 处理TRADE_USER下的发展人信息
     * 
     * @param item
     * @param msg
     */
    private void dealDevelopInfo(Map item, Map msg) {
        if (!IsEmptyUtils.isEmpty(msg.get("recomPersonChannelId"))) {
            item.put("developDepartId", msg.get("recomPersonChannelId"));
            item.put("developDate", GetDateUtils.getDate());
        }
        if (!IsEmptyUtils.isEmpty(msg.get("recomPersonId"))) {
            item.put("developStaffId", msg.get("recomPersonId"));
        }
        if (!IsEmptyUtils.isEmpty(msg.get("recomPersonCityCode"))) {
            Map param = new HashMap();
            param.put("city", msg.get("recomPersonCityCode"));
            param.put("province", msg.get("province"));
            item.put("developEparchyCode", new ChangeCodeUtils().changeEparchyUnStatic(param));
        }
        if (!IsEmptyUtils.isEmpty(msg.get("recomPersonDistrict"))) {
            item.put("developCityCode", msg.get("recomPersonDistrict"));
        }
    }

    /**
     * 准备TRADE_SUB_ITEM信息
     * 
     * @param exchange
     * @param msg
     * @return
     */
    private Map tradeSubItem(Exchange exchange, Map msg) {
        Map tradeSubItem = new HashMap();
        List<Map> item = new ArrayList<Map>();
        Map itemMap = new HashMap();
        MapUtils.arrayPut(itemMap, msg, MagicNumber.COPYARRAY);
        Exchange getItemIdExchange = ExchangeUtils.ofCopy(exchange, itemMap);
        List<String> itemIdList = GetSeqUtil.getSeqFromCb(pmp[0], getItemIdExchange, "seq_trade_id", 7);
        item.add(new TradeSubItemFactory_2().createItem(itemIdList.get(2), "s2338", "31313455"));
        Map newUserInfo = (Map) msg.get("newUserInfo");
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "ADDRESS_ID", newUserInfo.get("addressCode")));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "SHARE_NBR", msg.get("shareSerialNumber")));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "CUST_MANAGER_ID"));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "USERREMARK"));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "machineKVID"));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "AREA_CODE"));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "Regional", "5"));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "USER_PASSWD"));// TODO
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "CITY_CODE_O"));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "MOFFICE_ID", "0152785"));// TODO 暂时写死
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "COMMUNIT_ID", "0"));// TODO 暂时写死
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "UserNature", "103"));// TODO 暂时写死
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "USER_CONTACT_PHONE",
                msg.get("contactPhone")));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "NGN_MARK", "0"));// TODO 暂时写死
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "USER_CALLING_AREA", msg.get("district")));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "TerminalSource", "1"));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "DealLevel", "1"));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "USER_TYPE_CODE", "2"));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "CUST_MANAGER_NAME"));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "ChargeType", "1"));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "DETAIL_INSTALL_ADDRESS",
                newUserInfo.get("installAddress")));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "EquipmentType"));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "ACCESS_DESCR"));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "LOCAL_NET_CODE"));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "INSTALL_ADDRESS",
                newUserInfo.get("installAddress")));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "SaleRegion"));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "tvMacNetPass", "846212"));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "Speed"));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "USER_NAME", newUserInfo.get("certName")));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "SERIAL_NUMBER_ID", "0"));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "COMMUNIT_NAME"));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "INIT_PASSWD", "0"));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "LINK_NAME", msg.get("contactPerson")));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "EPG", "5136"));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "InternetConnection", "1"));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "MOFFICE_NAME"));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "TerminalManufacturer"));
        item.add(new TradeSubItemFactory_0().createItem(itemIdList.get(0), "IPTVSpeed", "4"));
        tradeSubItem.put("item", item);
        return tradeSubItem;
    }

    private Map tradeItem(Map msg) {
        Map tradeItem = new HashMap();
        List<Map> item = new ArrayList<Map>();
        item.add(LanUtils.createTradeItem("MARKETING_MODE", ""));
        if (!IsEmptyUtils.isEmpty(msg.get("recomPersonId"))) {
            item.add(LanUtils.createTradeItem("DEVELOP_STAFF_ID", msg.get("recomPersonId").toString()));
        }
        if (!IsEmptyUtils.isEmpty(msg.get("recomPersonChannelId"))) {
            item.add(LanUtils.createTradeItem("DEVELOP_DEPART_ID", msg.get("recomPersonChannelId").toString()));
        }
        item.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", msg.get("channelType").toString()));
        Map userInfo = (Map) msg.get("newUserInfo");
        item.add(LanUtils.createTradeItem("MaxNum", ""));
        item.add(LanUtils.createTradeItem("PH_NUM", ""));
        item.add(LanUtils.createTradeItem("DepartId", ""));
        item.add(LanUtils.createTradeItem("StaffId", ""));
        if (!IsEmptyUtils.isEmpty(userInfo.get("hopeDate"))) {
            item.add(LanUtils.createTradeItem("BOOK_FLAG", "1"));
            item.add(LanUtils.createTradeItem("BookDateDesc", userInfo.get("hopeDate")));
            item.add(LanUtils.createTradeItem("BookContactPhone", msg.get("contactPerson")));
        }
        else {
            item.add(LanUtils.createTradeItem("BOOK_FLAG", "0"));
        }
        item.add(LanUtils.createTradeItem("DepartName", ""));
        item.add(LanUtils.createTradeItem("NO_BOOK_REASON", ""));
        if (!IsEmptyUtils.isEmpty(msg.get("userPasswd"))) {
            item.add(LanUtils.createTradeItem("USER_PASSWD", msg.get("userPasswd").toString()));
        }
        if (IsEmptyUtils.isEmpty(msg.get("debutySerialNumber"))) {
            item.add(LanUtils.createTradeItem("EXISTS_ACCT", "0"));
        }
        else {
            item.add(LanUtils.createTradeItem("EXISTS_ACCT", "1"));
        }
        item.add(LanUtils.createTradeItem("StaffName", ""));
        item.add(LanUtils.createTradeItem("CurrNum", ""));
        item.add(LanUtils.createTradeItem("Date", ""));
        item.add(LanUtils.createTradeItem("REOPEN_TAG", ""));
        item.add(LanUtils.createTradeItem("PRE_END_TIME", ""));
        item.add(LanUtils.createTradeItem("NEW_PASSWD", ""));
        item.add(LanUtils.createTradeItem("SFGX_2060", "Y"));
        item.add(LanUtils.createTradeItem("PRE_START_TIME", ""));
        item.add(LanUtils.createTradeItem("GXLX_TANGSZ", "JTBJ:0:40"));
        item.add(LanUtils.createTradeItem("SCHE_ID", ""));
        tradeItem.put("item", item);
        return tradeItem;
    }

    private Map tradePayrelation(Map threePartRet, Map msg, Exchange exchange) {
        Map tradePayrelation = new HashMap();
        Map item = new HashMap();
        item.put("payitemCode", MagicNumber.DEFAULT_NO_VALUE);
        item.put("acctId", msg.get("acctId"));
        item.put("defaultTag", "1");
        item.put("userId", ((List<Map>) threePartRet.get("userInfo")).get(0).get("userId"));
        item.put("limit", "0");
        Map tradeMap = new HashMap();
        MapUtils.arrayPut(tradeMap, msg, MagicNumber.COPYARRAY);
        item.put("payrelationId",
                GetSeqUtil.getSeqFromCb(pmp[1], ExchangeUtils.ofCopy(exchange, tradeMap), "SEQ_PAYRELA_ID", 1).get(0));
        item.put("complementTag", "0");
        item.put("userPriority", "0");
        item.put("addupMonths", "0");
        item.put("bindType", "1");
        item.put("limitType", "0");
        item.put("addupMethod", "0");
        item.put("actTag", "1");
        item.put("acctPriority", "0");
        return tradePayrelation;
    }

    private Map preBase(Map threePartRet, Map msg, String appCode) {
        Map base = new HashMap();
        base.put("subscribeId", msg.get("ordersId"));
        base.put("tradeId", msg.get("ordersId"));
        base.put("startDate", GetDateUtils.getDate());
        base.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
        base.put("acceptDate", base.get("startDate"));
        base.put("nextDealTag", "Z");
        base.put("inModeCode", new ChangeCodeUtils().getInModeCode(appCode));
        base.put("tradeTypeCode", "0010");
        base.put("productId", msg.get("mainProduct"));
        base.put("brandCode", "IPTV");
        base.put("userId", ((List<Map>) threePartRet.get("userInfo")).get(0).get("userId"));
        base.put("custId", ((List<Map>) threePartRet.get("custInfo")).get(0).get("custId"));
        base.put("usecustId", base.get("custId"));
        base.put("acctId", msg.get("acctId"));
        base.put("userDiffCode", "00");
        base.put("netTypeCode", "0060");
        base.put("serialNumber", msg.get("serialNumber"));
        base.put("custName", ((List<Map>) threePartRet.get("custInfo")).get(0).get("custName"));
        return base;
    }

    /**
     * 获取账户ID
     * 不合账-生成新的ID
     * 合账-号码同共线号码,取三户的返回
     * 合账-号码与共线号码不同,调三户获取
     */
    private Object getAcctId(Exchange exchange, Map threePartRet) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Object acctId = null;
        Object debutySerialNumber = msg.get("debutySerialNumber");
        List<Map> acctInfo = null;
        if (msg.get("shareSerialNumber").equals(debutySerialNumber)) {
            acctInfo = (List<Map>) threePartRet.get("acctInfo");
        }
        else {
            Map acctThreePart = new HashMap();
            acctThreePart.put("serialNumber", msg.get("shareSerialNumber"));
            acctThreePart.put("areaCode", msg.get("shareAreaCode"));
            acctThreePart.put("serviceClassCode", "0030");
            acctThreePart.put("tradeTypeCode", "9999");
            acctThreePart.put("getMode", "");
            MapUtils.arrayPut(acctThreePart, msg, MagicNumber.COPYARRAY);
            Exchange acctThreePartExchange = ExchangeUtils.ofCopy(exchange, acctThreePart);
            LanUtils lan = new LanUtils();
            lan.preData(pmp[0], acctThreePartExchange);
            CallEngine.wsCall(acctThreePartExchange, "ecaop.comm.conf.url.UsrForNorthSer." + msg.get("province"));
            lan.xml2Json("", acctThreePartExchange);
            Map acctThreePartRet = acctThreePartExchange.getOut().getBody(Map.class);
            acctInfo = (List<Map>) acctThreePartRet.get("acctInfo");
        }
        if (IsEmptyUtils.isEmpty(acctInfo)) {
            throw new EcAopServerBizException("9999", "通过合账号码[" + debutySerialNumber + "]未获取到账户信息"
                    + appendApptx(body.get("apptx")));
        }
        acctId = acctInfo.get(0).get("acctId");
        return acctId;
    }

    private String appendApptx(Object apptx) {
        if (IsEmptyUtils.isEmpty(apptx)) {
            return "";
        }
        return ",apptx=" + apptx;
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
