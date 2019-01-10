package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
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
import com.alibaba.fastjson.JSON;

@EcRocTag("PreSubmitN6Iptv")
public class PreSubmitN6IptvProcessor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.trade.cbss.checkUserParametersMapping",
            "ecaop.masb.chph.gifa.ParametersMapping",
            "ecaop.masb.isoa.sProductChgParametersMapping",
            "ecaop.trades.sccc.cancel.paramtersmapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[PARAM_ARRAY.length];
    LanUtils lan = new LanUtils();

    @Override
    public void process(Exchange exchange) throws Exception {
        // 获取msg
        Map msg = getMsg(exchange);

        // 地市转唤
        msg.put("eparchyCode", new ChangeCodeUtils().changeEparchyUnStatic(msg));// 转换成cb地市

        // 校验信息
        checkInfo(msg);

        // 获取三户信息
        Map threePartRet = getThreePartInfo(exchange, msg);

        // 预提交
        Exchange preSubmitExchange = preSubmit(threePartRet, msg, exchange);

        // 返回报文整理
        Map returnMap = dealReturn(exchange, preSubmitExchange, msg);
        Message out = new DefaultMessage();
        out.setBody(returnMap);
        exchange.setOut(out);
    }

    private Map preExt(Object ordersId, Map threePartRet, Map msg, Exchange exchange) {
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

        List<Map> serviceAttr = new ArrayList();
        Integer mouthNum = 0;
        for (int i = 0; i < productInfo.size(); i++) {
            List<Map> packageElement = (List<Map>) productInfo.get(i).get("packageElement");
            for (int j = 0; j < packageElement.size(); j++) {
                serviceAttr = (List) packageElement.get(j).get("serviceAttr");
                if (serviceAttr != null && serviceAttr.size() > 0) {
                    for (Map ser : serviceAttr) {
                        if ("monthnum".equals(ser.get("code"))) {
                            mouthNum = Integer.valueOf((String) ser.get("value"));
                        }
                    }
                }
            }
        }

        Map ret = TradeManagerUtils.preProductInfo(productInfo, "00" + msg.get("province"), msg);
        ext.put("tradeUser", tradeUser(ordersId, msg, ret));
        ext.put("tradeRes", tradeRes(msg));
        ext.put("tradeProductType", tradeProductType(msg));
        ext.put("tradeDiscnt", tradeDiscnt(msg, productInfo, mouthNum));
        ext.put("tradeProduct", tradeProduct(msg));
        ext.put("tradeSvc", tradeSvc(msg, productInfo));
        ext.put("tradeSp", tradeSp(msg));
        ext.put("tradeItem", tradeItem(msg, threePartRet));
        ext.put("tradeSubItem", tradeSubItem(exchange, msg, mouthNum));
        ext.put("tradePayrelation", tradePayrelation(msg, exchange));
        return ext;
    }

    /**
     * 拼装traderes节点
     */
    public Map tradeRes(Map msg) {
        try {
            Map tradeRes = new HashMap();
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("reTypeCode", "0");
            item.put("resCode", msg.get("iptvNumber"));
            item.put("modifyTag", "0");
            item.put("startDate", GetDateUtils.getDate());
            item.put("endDate", "20501231235959");
            tradeRes.put("item", item);
            return tradeRes;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_RES节点报错" + e.getMessage());
        }
    }

    /**
     * 拼装tradediscnt节点
     */
    public Map tradeDiscnt(Map msg, List<Map> productInfo, int mouthNum) {
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
                if (mouthNum == 0) {
                    item.put("endDate", "20501231235959");//包月
                }
                else {
                    item.put("endDate", GetDateUtils.getEndDateFormat(mouthNum));//根据传入属性判断   包年
                }
                item.put("modifyTag", "0");
                item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
                itemList.add(item);

            }

            for (int i = 0; i < productInfo.size(); i++) {
                List<Map> packageElement = (List<Map>) productInfo.get(i).get("packageElement");
                for (int j = 0; j < packageElement.size(); j++) {
                    List<Map> serviceAttr = (List) packageElement.get(j).get("serviceAttr");
                    if (serviceAttr != null && serviceAttr.size() > 0) {
                        for (Map ser : serviceAttr) {
                            if ("biaozhuncode".equals(ser.get("code"))) {
                                Map item = new HashMap();
                                item.put("xDatatype", "NULL");
                                item.put("id", msg.get("userId"));
                                item.put("idType", "1");
                                item.put("userIdA", "-1");
                                item.put("productId", productInfo.get(i).get("productId"));
                                item.put("packageId", packageElement.get(j).get("packageId"));
                                item.put("discntCode", ser.get("value"));
                                item.put("specTag", "1");
                                item.put("relationTypeCode", "");
                                item.put("startDate", msg.get("date"));
                                item.put("endDate", "20500101235959");
                                item.put("modifyTag", "0");
                                item.put("itemId",
                                        TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
                                itemList.add(item);

                            }
                        }
                    }
                }

            }

            tradeDis.put("item", itemList);
            msg.put("disList", itemList);//放入msg中，用于下面取出资费属性
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

    private Map tradeSvc(Map msg, List<Map> productInfo) {
        try {
            Map svcList = new HashMap();
            List svList = new ArrayList();
            for (int i = 0; i < productInfo.size(); i++) {
                List<Map> packageElement = (List<Map>) productInfo.get(i).get("packageElement");
                for (int j = 0; j < packageElement.size(); j++) {
                    String elementType = (String) packageElement.get(j).get("elementType");
                    if ("S".equals(elementType)) {
                        Map item = new HashMap();
                        item.put("xDatatype", "NULL");
                        item.put("userId", msg.get("userId"));
                        item.put("serviceId", packageElement.get(j).get("elementId"));
                        item.put("modifyTag", "0");
                        item.put("startDate", msg.get("date"));
                        item.put("endDate", "20501231235959");
                        item.put("productId", productInfo.get(i).get("productId"));
                        item.put("packageId", packageElement.get(j).get("packageId"));
                        item.put("itemId",
                                TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
                        item.put("userIdA", "-1");
                        svList.add(item);
                    }
                }
            }

            List<Map> svcList2 = (List<Map>) msg.get("svcList");
            for (Map svc : svcList2) {
                if ("60000".equals(svc.get("serviceId") + "")) {//取出默认60000的服务下发
                    Map item2 = new HashMap();
                    item2.put("xDatatype", "NULL");
                    item2.put("userId", msg.get("userId"));
                    item2.put("serviceId", svc.get("serviceId"));
                    item2.put("modifyTag", "0");
                    item2.put("startDate", msg.get("date"));
                    item2.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
                    item2.put("productId", svc.get("productId"));
                    item2.put("packageId", svc.get("packageId"));
                    item2.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
                    item2.put("userIdA", "-1");
                    svList.add(item2);
                }
            }
            svcList.put("item", svList);
            msg.put("svList", svList);// 此处放入msg，后边处理服务的属性时用

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
    private Map tradeUser(Object ordersId, Map msg, Map ret) {
        Map tradeUser = new HashMap();
        Map item = new HashMap();
        item.put("xDatatype", MagicNumber.STRING_OF_NULL);
        item.put("userId", msg.get("userId"));
        item.put("usecustId", msg.get("custId"));
        item.put("custId", msg.get("custId"));
        item.put("brandCode", ret.get("brandCode"));
        item.put("productId", msg.get("mainProduct"));
        item.put("eparchyCode", ChangeCodeUtils.changeEparchy(msg));
        item.put("cityCode", msg.get("district"));
        item.put("removeTag", "0");
        if (!IsEmptyUtils.isEmpty(msg.get("userPasswd"))) {
            item.put("userPasswd", msg.get("userPasswd"));
        }
        dealDevelopInfo(item, msg);
        item.put("openDepartId", msg.get("channelId"));
        item.put("inDepartId", msg.get("channelId"));
        item.put("inDate", GetDateUtils.getDate());
        item.put("openDate", GetDateUtils.getDate());
        item.put("openStaffId", msg.get("operatorId"));
        item.put("inStaffId", msg.get("operatorId"));
        item.put("prepayTag", "0");
        item.put("acctTag", "0");
        item.put("productTypeCode", ret.get("productTypeCode"));
        item.put("userTypeCode", "2");
        item.put("serialNumber", msg.get("iptvNumber"));
        item.put("netTypeCode", "60");
        item.put("acceptMonth", ordersId.toString().substring(4, 6));
        item.put("openMode", "0");
        item.put("userStateCodeset", "0");
        item.put("scoreValue", "0");
        item.put("basicCreditValue", "0");
        item.put("creditValue", "0");
        item.put("mputeMonthFee", "0");

        // 山东要求下发该字段 by wangmc 20170925
        if (!IsEmptyUtils.isEmpty(msg.get("orderRemarks"))) {
            item.put("remark", msg.get("orderRemarks"));
        }

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
    private Map tradeSubItem(Exchange exchange, Map msg, int mouthNum) {
        Map tradeSubItem = new HashMap();
        List<Map> item = new ArrayList<Map>();
        List<Map> removeList = new ArrayList<Map>();
        Map itemMap = new HashMap();
        MapUtils.arrayPut(itemMap, msg, MagicNumber.COPYARRAY);
        Map newUserInfo = (Map) msg.get("newUserInfo");
        List<Map> productInfo = (List<Map>) newUserInfo.get("productInfo");
        List<Map> serviceAttr = new ArrayList();

        String code = "";
        String value = "";
        String elementId = "";
        for (int i = 0; i < productInfo.size(); i++) {
            List<Map> packageElement = (List<Map>) productInfo.get(i).get("packageElement");
            for (int j = 0; j < packageElement.size(); j++) {
                elementId = (String) packageElement.get(j).get("elementId");
                String elementType = (String) packageElement.get(j).get("elementType");
                serviceAttr = (List) packageElement.get(j).get("serviceAttr");
                if ("D".equals(elementType)) {
                    List<Map> discntList = (List<Map>) msg.get("disList");
                    if (discntList != null && discntList.size() > 0) {
                        Map discntInfo = getInfoforDis(discntList, elementId, msg);
                        if (serviceAttr != null && serviceAttr.size() > 0) {
                            for (Map ser : serviceAttr) {
                                if (!"biaozhuncode".equals(ser.get("code"))) {
                                    item.add(new LanUtils().createTradeSubItem3((String) discntInfo.get("itemId"),
                                            (String) ser.get("code"), (String) ser.get("value"),
                                            (String) discntInfo.get("endDate"), GetDateUtils.getDate()));
                                }

                            }
                        }
                    }
                }
                else {
                    List<Map> svcList = (List<Map>) msg.get("svList");
                    if (svcList != null && svcList.size() > 0) {
                        String getItemIdFromSvc = getItemIdforSvc(svcList, elementId);
                        if (serviceAttr != null && serviceAttr.size() > 0) {
                            for (Map ser : serviceAttr) {
                                code = String.valueOf(ser.get("code"));
                                value = String.valueOf(ser.get("value"));
                                item.add(new TradeSubItemFactory_2().createItem(getItemIdFromSvc, code, value));
                            }
                        }
                    }

                }
            }
        }
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "ADDRESS_ID", newUserInfo.get("addressCode")));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "SHARE_NBR", msg.get("shareSerialNumber")));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "CUST_MANAGER_ID"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "USERREMARK"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "machineKVID"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "AREA_CODE"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "Regional", "5"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "USER_PASSWD"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "CITY_CODE_O"));
        //山东要求MOFFICE_ID属性传局向编码字段  by maly 171123
        String exchCode = (String) newUserInfo.get("exchCode");
        if (!IsEmptyUtils.isEmpty(exchCode)) {
            item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "MOFFICE_ID", exchCode));
        }
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "COMMUNIT_ID", "0"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "UserNature", "100"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "USER_CONTACT_PHONE",
                msg.get("contactPhone")));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "NGN_MARK", "0"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "USER_CALLING_AREA", msg.get("district")));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "TerminalSource", "1"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "DealLevel", "1"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "USER_TYPE_CODE", "2"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "CUST_MANAGER_NAME"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "ChargeType", "1"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "DETAIL_INSTALL_ADDRESS",
                newUserInfo.get("installAddress")));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "EquipmentType"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "ACCESS_DESCR"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "LOCAL_NET_CODE"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "INSTALL_ADDRESS",
                newUserInfo.get("installAddress")));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "SaleRegion"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "tvMacNetPass", "846212"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "Speed"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "USER_NAME", newUserInfo.get("certName")));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "SERIAL_NUMBER_ID", "0"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "COMMUNIT_NAME"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "INIT_PASSWD", "0"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "LINK_NAME", msg.get("contactPerson")));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "EPG", "5136"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "InternetConnection", "1"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "MOFFICE_NAME"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "TerminalManufacturer"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "IPTVSpeed", "4"));
        item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), "SettopBoxIntAcct", msg.get("iptvid")));
        if (mouthNum != 0) {//包年的时候传
            item.add(new LanUtils().createTradeSubItem3((String) msg.get("disItemId"), "Startdate",
                    GetDateUtils.getDate(), GetDateUtils.getEndDateFormat(mouthNum), GetDateUtils.getDate()));
            item.add(new LanUtils().createTradeSubItem3((String) msg.get("disItemId"), "Enddate", msg.get("endDate"),
                    GetDateUtils.getEndDateFormat(mouthNum), GetDateUtils.getDate()));
        }

        String itemId = TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID");
        //item.add(new TradeSubItemFactory_0().createItem(itemId, "sharenumber", msg.get("authAcctId")));
        //RSD2017121300049-AOP的IPTV预提交接口增加节点及目前问题修复申请
        String userKey = "";
        String userValue = "";
        if (!IsEmptyUtils.isEmpty((List<Map>) ((Map) msg.get("newUserInfo")).get("userExchInfo"))) {
            List<Map> userExchInfo = (List<Map>) ((Map) msg.get("newUserInfo")).get("userExchInfo");
            for (Map userinfo : userExchInfo) {
                userKey = (String) userinfo.get("key");
                userValue = (String) userinfo.get("value");
                for (Map trsubitem : item) {
                    if (userKey.equals(trsubitem.get("attrCode"))) {
                        removeList.add(trsubitem);
                    }
                }
                item.removeAll(removeList);
                item.add(new TradeSubItemFactory_0().createItem(msg.get("userId"), userKey, userValue));
            }
        }
        tradeSubItem.put("item", item);
        return tradeSubItem;
    }

    private Map tradeItem(Map msg, Map threePartRet) {
        Map tradeItem = new HashMap();
        List<Map> item = new ArrayList<Map>();
        List<Map> removeList = new ArrayList<Map>();
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
            String hopeDate = (String) userInfo.get("hopeDate");
            //格式转换
            hopeDate = GetDateUtils.TransDateFormat(hopeDate, "yyyyMMddHHmmss");
            item.add(LanUtils.createTradeItem("Date", hopeDate));
            item.add(LanUtils.createTradeItem("BOOK_FLAG", "0"));
            item.add(LanUtils.createTradeItem("BookDateDesc", userInfo.get("hopeDate")));
            item.add(LanUtils.createTradeItem("BookContactPhone", msg.get("contactPerson")));
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

        item.add(LanUtils.createTradeItem("REOPEN_TAG", ""));
        item.add(LanUtils.createTradeItem("PRE_END_TIME", ""));
        item.add(LanUtils.createTradeItem("NEW_PASSWD", ""));
        item.add(LanUtils.createTradeItem("SFGX_2060", "Y"));
        item.add(LanUtils.createTradeItem("PRE_START_TIME", ""));
        String serviceClassCode = (String) ((List<Map>) threePartRet.get("userInfo")).get(0).get("serviceClassCode");
        String code = serviceClassCode.substring(2);
        String brandCode = (String) ((List<Map>) threePartRet.get("userInfo")).get(0).get("brandCode");
        item.add(LanUtils.createTradeItem("GXLX_TANGSZ", brandCode + ":0:" + code));
        item.add(LanUtils.createTradeItem("SCHE_ID", ""));
        //RSD2017121300049-AOP的IPTV预提交接口增加节点及目前问题修复申请
        String woKey = "";
        String woValue = "";
        if (!IsEmptyUtils.isEmpty((List<Map>) ((Map) msg.get("newUserInfo")).get("woExchInfo"))) {
            List<Map> woExchInfo = (List<Map>) ((Map) msg.get("newUserInfo")).get("woExchInfo");
            for (Map woinfo : woExchInfo) {
                woKey = (String) woinfo.get("key");
                woValue = (String) woinfo.get("value");
                for (Map tritem : item) {
                    if (woKey.equals(tritem.get("attrCode"))) {
                        removeList.add(tritem);
                    }
                }
                item.removeAll(removeList);
                item.add(LanUtils.createTradeItem(woKey, woValue));
            }
        }
        tradeItem.put("item", item);
        return tradeItem;
    }

    private Map tradePayrelation(Map msg, Exchange exchange) {
        Map tradePayrelation = new HashMap();
        Map item = new HashMap();
        item.put("payitemCode", MagicNumber.DEFAULT_NO_VALUE);
        item.put("acctId", msg.get("acctId"));
        item.put("defaultTag", "1");
        item.put("userId", msg.get("userId"));
        item.put("limit", "0");
        Map tradeMap = new HashMap();
        MapUtils.arrayPut(tradeMap, msg, MagicNumber.COPYARRAY);
        item.put(
                "payrelationId",
                GetSeqUtil.getSeqFromN6ess(ExchangeUtils.ofCopy(exchange, tradeMap), "ITEM_ID",
                        (String) msg.get("eparchyCode")));
        item.put("complementTag", "0");
        item.put("userPriority", "0");
        item.put("addupMonths", "0");
        item.put("bindType", "1");
        item.put("limitType", "0");
        item.put("addupMethod", "0");
        item.put("actTag", "1");
        item.put("acctPriority", "0");
        tradePayrelation.put("item", item);
        return tradePayrelation;
    }

    private Map preBase(Object ordersId, Map threePartRet, Map msg, String appCode) {
        Map base = new HashMap();
        // base.put("tradeStaffId", msg.get("operatorId"));
        // base.put("tradeDepartId", msg.get("channelId"));
        base.put("subscribeId", ordersId);
        base.put("tradeId", ordersId);
        base.put("startDate", GetDateUtils.getDate());
        base.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);// 时间好像应该是带-的
        base.put("acceptDate", base.get("startDate"));
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        base.put("inModeCode", new ChangeCodeUtils().getInModeCode(appCode));
        base.put("tradeTypeCode", "0010");
        base.put("productId", msg.get("mainProduct"));
        base.put("brandCode", "IPTV");
        base.put("cancelTag", "0");
        base.put("operFee", "0");
        base.put("foregift", "0");
        base.put("advancePay", "0");
        base.put("feeState", "0");
        base.put("chkTag", "0");
        base.put("execTime", base.get("startDate"));
        base.put("userId", msg.get("userId"));
        base.put("custId", ((List<Map>) threePartRet.get("custInfo")).get(0).get("custId"));
        base.put("usecustId", base.get("custId"));
        base.put("acctId", msg.get("acctId"));
        base.put("userDiffCode", "00");
        base.put("netTypeCode", "60");
        base.put("serialNumber", msg.get("iptvNumber"));
        base.put("custName", ((List<Map>) threePartRet.get("custInfo")).get(0).get("custName"));
        base.put("termIp", "132.35.87.198");
        base.put("eparchyCode", ChangeCodeUtils.changeEparchy(msg));
        base.put("execTime", GetDateUtils.getDate());
        base.put("mainDiscntCode", "");
        base.put("cityCode", msg.get("district"));
        // 山东要求下发该字段 by wangmc 20170925
        if (!IsEmptyUtils.isEmpty(msg.get("orderRemarks"))) {
            base.put("remark", msg.get("orderRemarks"));
        }

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
        Map newUserInfo = (Map) msg.get("newUserInfo");
        String debutySerialNumber = (String) newUserInfo.get("debutySerialNumber");
        String shareSerialNumber = (String) msg.get("shareSerialNumber");
        List<Map> acctInfo = null;
        if (StringUtils.isNotEmpty(shareSerialNumber) && StringUtils.isNotEmpty(debutySerialNumber)) {
            if (msg.get("shareSerialNumber").equals(debutySerialNumber)) {
                acctInfo = (List<Map>) threePartRet.get("acctInfo");
            }
            else {
                Map acctThreePart = new HashMap();
                acctThreePart.put("serialNumber", debutySerialNumber);
                acctThreePart.put("areaCode", newUserInfo.get("debutyAreaCode"));
                acctThreePart.put("serviceClassCode", "0030");
                acctThreePart.put("tradeTypeCode", "9999");
                acctThreePart.put("getMode", "1111111111100013111100001100001");
                MapUtils.arrayPut(acctThreePart, msg, MagicNumber.COPYARRAY);
                Exchange acctThreePartExchange = ExchangeUtils.ofCopy(exchange, acctThreePart);
                LanUtils lan = new LanUtils();
                lan.preData(pmp[0], acctThreePartExchange);
                CallEngine.wsCall(acctThreePartExchange, "ecaop.comm.conf.url.UsrForNorthSer." + msg.get("province"));
                lan.xml2Json("ecaop.trades.cbss.threePart.template", acctThreePartExchange);
                Map acctThreePartRet = acctThreePartExchange.getOut().getBody(Map.class);
                acctInfo = (List<Map>) acctThreePartRet.get("acctInfo");
            }
            if (IsEmptyUtils.isEmpty(acctInfo)) {
                throw new EcAopServerBizException("9999", "通过合账号码[" + debutySerialNumber + "]未获取到账户信息"
                        + appendApptx(body.get("apptx")));
            }
            acctId = acctInfo.get(0).get("acctId");
        }

        if (StringUtils.isEmpty(debutySerialNumber)) {
            Exchange acctThreePartExchange = ExchangeUtils.ofCopy(exchange, msg);
            acctId = GetSeqUtil.getSeqFromN6ess(acctThreePartExchange, "ACCT_ID", (String) msg.get("eparchyCode"));
        }
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

    /**
     * 取出传入服务的itemId
     * 
     * @param svcMap
     * @param serviceId
     * @return
     */
    public String getItemIdforSvc(List<Map> svcList, String serviceId) {
        String itemId = null;
        if (svcList != null && svcList.size() > 0) {
            for (Map scvMap : svcList) {
                if (serviceId != null && serviceId.equals(scvMap.get("serviceId") + "")) {
                    itemId = (String) scvMap.get("itemId");
                    break;
                }
            }
        }
        return itemId;
    }

    /**
     * 取出传入资费的itemId
     * 
     * @param svcMap
     * @param serviceId
     * @return
     */
    public Map getInfoforDis(List<Map> svcList, String serviceId, Map msg) {
        Map disInfo = new HashMap();
        if (svcList != null && svcList.size() > 0) {
            for (Map scvMap : svcList) {
                if (serviceId != null && serviceId.equals(scvMap.get("discntCode") + "")) {
                    disInfo.put("itemId", scvMap.get("itemId"));
                    disInfo.put("startDate", scvMap.get("startDate"));
                    disInfo.put("endDate", scvMap.get("endDate"));
                    msg.put("endDate", disInfo.get("endDate"));
                    msg.put("disItemId", disInfo.get("itemId"));
                    break;
                }
            }
        }
        return disInfo;
    }

    /**
     * 调三户获取信息
     * 
     * @param exchange
     * @param msg
     * @return
     * @throws Exception
     */
    public Map getThreePartInfo(Exchange exchange, Map msg) throws Exception {
        // 调用三户接口,为预提交接口准备信息
        Map threePart = new HashMap();
        threePart.put("serialNumber", msg.get("shareSerialNumber"));
        threePart.put("areaCode", msg.get("shareAreaCode"));
        threePart.put("serviceClassCode", "0030");
        threePart.put("tradeTypeCode", "9999");
        threePart.put("getMode", "1111111111100013111100001100001");
        MapUtils.arrayPut(threePart, msg, MagicNumber.COPYARRAY);
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePart);
        lan.preData(pmp[0], threePartExchange);
        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.UsrForNorthSer." + msg.get("province"));
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        Map threePartRet = threePartExchange.getOut().getBody(Map.class);
        // 校验是否没有返回三户信息
        String[] infoKeys = new String[] { "userInfo", "custInfo", "acctInfo" };
        Map errorDetail = MapUtils.asMap("userInfo", "用户信息", "custInfo", "客户信息", "acctInfo", "账户信息");
        for (String infoKey : infoKeys) {
            if (IsEmptyUtils.isEmpty(threePartRet.get(infoKey))) {
                throw new EcAopServerBizException("9999", "调三户未返回" + errorDetail.get(infoKey));
            }

        }
        return threePartRet;
    }

    /**
     * 获取msg
     * 
     * @param exchange
     * @return
     */
    public Map getMsg(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        // 获取msg信息
        Object msgObject = body.get("msg");
        Map msg = null;
        if (msgObject instanceof String) {
            msg = JSON.parseObject(msgObject.toString());
        }
        else {
            msg = (Map) msgObject;
        }
        return msg;
    }

    /**
     * 信息校验
     * 
     * @param msg
     */
    public void checkInfo(Map msg) {
        Object shareSerialNumber = msg.get("shareSerialNumber");
        if (IsEmptyUtils.isEmpty(shareSerialNumber)) {
            throw new EcAopServerBizException("9999", "未下发共线号码信息");
        }
        Object newUserInfo = msg.get("newUserInfo");
        if (IsEmptyUtils.isEmpty(newUserInfo)) {
            throw new EcAopServerBizException("9999", "未下发用户信息");
        }
    }

    /**
     * 调北六预提交
     * 
     * @param threePartRet
     * @param msg
     * @param exchange
     * @return
     * @throws Exception
     */
    public Exchange preSubmit(Map threePartRet, Map msg, Exchange exchange) throws Exception {
        Map preSubmit = new HashMap();
        MapUtils.arrayPut(preSubmit, msg, MagicNumber.COPYARRAY);
        Map tradeMap = new HashMap();
        MapUtils.arrayPut(tradeMap, msg, MagicNumber.COPYARRAY);
        Object ordersId = GetSeqUtil.getSeqFromN6ess(ExchangeUtils.ofCopy(exchange, tradeMap), "TRADE_ID",
                (String) msg.get("eparchyCode"));
        preSubmit.put("ordersId", ordersId);
        // preSubmit.put("serinalNamber", msg.get("serialNumber"));
        // preSubmit.put("serviceClassCode", "0040");
        preSubmit.put("operTypeCode", "0");
        Object acctId = getAcctId(exchange, threePartRet);
        msg.put("ordersId", ordersId);
        msg.put("acctId", acctId);
        if ("17".contains((String) msg.get("province"))) {
            Object userId = GetSeqUtil.getSeqFromN6ess(ExchangeUtils.ofCopy(exchange, tradeMap), "USER_ID",
                    (String) msg.get("eparchyCode"));
            msg.put("userId", userId);
        }
        else {
            msg.put("userId", ((List<Map>) threePartRet.get("userInfo")).get(0).get("userId"));
        }
        msg.put("custId", ((List<Map>) threePartRet.get("custInfo")).get(0).get("custId"));
        msg.put("date", GetDateUtils.getDate());
        preSubmit.putAll(msg);
        preSubmit.put("ext", preExt(ordersId, threePartRet, msg, exchange));
        preSubmit.put("base", preBase(ordersId, threePartRet, msg, exchange.getAppCode()));
        preSubmit.remove("areaCode");
        Exchange preSubmitExchange = ExchangeUtils.ofCopy(exchange, preSubmit);
        lan.preData(pmp[2], preSubmitExchange);
        CallEngine.wsCall(preSubmitExchange,
                "ecaop.comm.conf.url.n6ess.services.AOPOrdForNorthSer" + "." + msg.get("province"));
        lan.xml2Json("ecaop.trades.sccc.cancelPre.template", preSubmitExchange);
        return preSubmitExchange;
    }

    /**
     * 返回处理
     * 
     * @param exchange
     * @param preSubmitExchange
     * @return 
     * @throws Exception
     */
    public Map dealReturn(Exchange exchange, Exchange preSubmitExchange, Map msg) throws Exception {
        Map body = (Map) preSubmitExchange.getOut().getBody();
        List rspInfos = (List) body.get("rspInfo");
        Map rspInfo = (Map) rspInfos.get(0);
        String provOrderId = (String) rspInfo.get("bssOrderId");
        List<Map> provinceOrderInfos = (List) rspInfo.get("provinceOrderInfo");
        List<Map> feeInfos = new ArrayList();
        Map retMap = new HashMap();
        for (Map provinceOrderInfo : provinceOrderInfos) {
            List<Map> m = (List) provinceOrderInfo.get("preFeeInfoRsp");
            for (Map feeInfo : m) {
                Map m1 = new HashMap();
                m1.put("feeId", feeInfo.get("feeTypeCode"));
                m1.put("feeCategory", feeInfo.get("feeMode"));
                m1.put("feeDes", feeInfo.get("feeTypeName"));
                m1.put("maxRelief", feeInfo.get("maxDerateFee"));
                m1.put("origFee", feeInfo.get("fee"));
                feeInfos.add(m1);
            }

        }
        double totalFee = 0;
        for (Map feeInfo : feeInfos) {
            String origFee = (String) feeInfo.get("origFee");
            double origFee1 = Double.parseDouble(origFee);
            totalFee += origFee1;
        }
        retMap.put("provOrderId", provOrderId);
        if (!IsEmptyUtils.isEmpty(msg.get("userPasswd"))) {
            retMap.put("userName", msg.get("userName"));
        }
        if (!IsEmptyUtils.isEmpty(msg.get("userPasswd"))) {
            retMap.put("userPasswd", msg.get("userPasswd"));
        }
        retMap.put("feeInfo", feeInfos);
        retMap.put("totalFee", totalFee);

        return retMap;
    }

}
