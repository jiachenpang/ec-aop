package com.ailk.ecaop.biz.sub.lan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.dao.base.DaoEngine;
import org.n3r.esql.Esql;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;

@EcRocTag("g4OpenBroadBandPreCommit")
public class G4OpenBroadBandPreCommitProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map userInfo = (Map) msg.get("newUserInfo");
        String orderNo = msg.get("orderNo").toString();
        if (null == userInfo) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "请填写用户信息");
        }
        List<Map> productInfo = (List<Map>) userInfo.get("productInfo");
        String mainProductCode = null;
        for (int i = 0; i < productInfo.size(); i++) {
            if ("1".equals(productInfo.get(i).get("productMode"))) {
                mainProductCode = (String) productInfo.get(i).get("productId");
            }
        }
        if (null == mainProductCode) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "未选择主产品");
        }
        LanUtils lan = new LanUtils();

        lan.preData("ecaop.trade.cbss.getCodePasswdParametersMapping", exchange);
        // CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.number4GSer");
        String str1 =
                "<soapenv:Envelope xmlns:soapenv='http://schemas.xmlsoap.org/soap/envelope/'><soapenv:Body><ns30:GET_BROADBAND_ACCT_INFO4G_OUTPUT xmlns:ns30='http://ws.chinaunicom.cn/Number4GSer/unibssBody'><ns1:UNI_BSS_HEAD xmlns:ns1='http://ws.chinaunicom.cn/unibssHead'><ns1:ORIG_DOMAIN>ULTE</ns1:ORIG_DOMAIN><ns1:SERVICE_NAME>Number4GSer</ns1:SERVICE_NAME><ns1:OPERATE_NAME>getBroadbandAcctInfo4G</ns1:OPERATE_NAME><ns1:ACTION_CODE>1</ns1:ACTION_CODE><ns1:ACTION_RELATION>0</ns1:ACTION_RELATION><ns1:ROUTING><ns1:ROUTE_TYPE>00</ns1:ROUTE_TYPE><ns1:ROUTE_VALUE>11</ns1:ROUTE_VALUE></ns1:ROUTING><ns1:PROC_ID>2014100806401724</ns1:PROC_ID><ns1:TRANS_IDO>2014100809551971</ns1:TRANS_IDO><ns1:TRANS_IDH></ns1:TRANS_IDH><ns1:PROCESS_TIME>20141008091715</ns1:PROCESS_TIME><ns1:RESPONSE><ns1:RSP_TYPE>0</ns1:RSP_TYPE><ns1:RSP_CODE>0000</ns1:RSP_CODE><ns1:RSP_DESC>成功</ns1:RSP_DESC></ns1:RESPONSE><ns1:COM_BUS_INFO><ns1:OPER_ID>bjsc-guzw1</ns1:OPER_ID><ns1:PROVINCE_CODE>11</ns1:PROVINCE_CODE><ns1:EPARCHY_CODE>110</ns1:EPARCHY_CODE><ns1:CITY_CODE>11a01p</ns1:CITY_CODE><ns1:CHANNEL_ID>11a4369</ns1:CHANNEL_ID><ns1:CHANNEL_TYPE>2010100</ns1:CHANNEL_TYPE><ns1:ACCESS_TYPE>01</ns1:ACCESS_TYPE><ns1:ORDER_TYPE>01</ns1:ORDER_TYPE></ns1:COM_BUS_INFO><ns1:SP_RESERVE><ns1:TRANS_IDC>201410080937562456761109707011</ns1:TRANS_IDC><ns1:CUTOFFDAY>20141008</ns1:CUTOFFDAY><ns1:OSNDUNS>9900</ns1:OSNDUNS><ns1:HSNDUNS>1100</ns1:HSNDUNS><ns1:CONV_ID>201410080955197120141008093756359</ns1:CONV_ID></ns1:SP_RESERVE><ns1:TEST_FLAG>0</ns1:TEST_FLAG><ns1:MSG_SENDER>CBSS</ns1:MSG_SENDER><ns1:MSG_RECEIVER>0003</ns1:MSG_RECEIVER></ns1:UNI_BSS_HEAD><ns30:UNI_BSS_BODY><ns23:GET_BROADBAND_ACCT_INFO4G_RSP xmlns:ns23='http://ws.chinaunicom.cn/Number4GSer/unibssBody/getBroadbandAcctInfo4GRsp'><ns23:RESP_CODE>0000</ns23:RESP_CODE><ns23:RESP_DESC>成功</ns23:RESP_DESC><ns23:ACCT_INFO><ns23:SERIAL_NUMBER>01010007776</ns23:SERIAL_NUMBER><ns23:AUTH_ACCT_ID></ns23:AUTH_ACCT_ID><ns23:AUTH_PASSWD></ns23:AUTH_PASSWD></ns23:ACCT_INFO></ns23:GET_BROADBAND_ACCT_INFO4G_RSP></ns30:UNI_BSS_BODY><ns2:UNI_BSS_ATTACHED xmlns:ns2='http://ws.chinaunicom.cn/unibssAttached' /></ns30:GET_BROADBAND_ACCT_INFO4G_OUTPUT></soapenv:Body></soapenv:Envelope>";
        DefaultMessage out = new DefaultMessage();
        out.setBody(str1);
        exchange.setOut(out);
        lan.xml2Json("ecaop.trade.cbss.getCodePasswdTemplate", exchange);
        List<Map> acctInfo = (ArrayList<Map>) exchange.getOut().getBody(Map.class).get("acctInfo");
        if (null == acctInfo || 0 == acctInfo.size()) {
            throw new EcAopServerBizException("0001", "无宽带账号");
        }
        Map acctMap = acctInfo.get(0);// 包含3个可空节点，SERIAL_NUMBER、AUTH_ACCT_ID、AUTH_PASSWD
        Object serialNumber = acctMap.get("serialNumber");
        if (null == serialNumber || "".equals(serialNumber)) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "宽带统一编码为空");
        }
        Object authAcctId = acctMap.get("authAcctId");
        if (null == authAcctId || "".equals(authAcctId)) {
            authAcctId = "1230";
            // throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "宽带认证账号为空");
        }
        Object authPasswd = acctMap.get("authPasswd");
        msg.put("authAcctId", authAcctId);
        msg.put("serialNumber", serialNumber);
        Object city = msg.get("city");
        msg.put("city", ChangeCodeUtils.changeEparchy(msg));
        body.put("msg", msg);
        exchange.getIn().setBody(body);

        // 宽带唯一性校验，调用CBSS
        lan.preData("ecaop.trade.cbss.checkCodePasswdParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.number4GSer");
        lan.xml2Json("ecaop.trade.cbss.checkCodePasswdTemplate", exchange);

        // 宽带唯一性校验，调用BSS
        msg.put("city", city);
        body.put("msg", msg);
        body.remove("route");
        exchange.getIn().setBody(body);
        lan.preData("ecaop.trade.bss.checkCodePasswdParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.number4GSer");
        lan.xml2Json("ecaop.trade.cbss.checkCodePasswdTemplate", exchange);

        // 预提交
        Map inMap = MapUtils.asMap("province", msg.get("province"), "eparchy", msg.get("city"), "product",
                mainProductCode);
        Esql dao = DaoEngine.getMySqlDao("/com/ailk/ecaop/sql/prd/LanProductInfoQuery.esql");
        ArrayList<Map> mainProductList = dao.id("qryMainProductInfo").params(inMap).execute();
        if (null == mainProductList || 0 == mainProductList.size()) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "查无此产品信息：" + mainProductCode);
        }
        msg.put("mainPrdInfo", mainProductList.get(0));
        ArrayList<Map> itemList = dao.id("qryProcuctAttr").params(inMap).execute();
        msg.put("itemList", itemList);
        String startDate = GetDateUtils.getDate();
        prepareDate4PreCommit(msg, startDate);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        lan.preData("ecaop.trades.sccc.cancelPre.paramtersmapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
        lan.xml2Json("ecaop.trades.sccc.cancelPre.template", exchange);
        // 处理返回信息
        Map outMap = exchange.getOut().getBody(Map.class);
        Map retMap = new HashMap();
        if (null == outMap || outMap.isEmpty()) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "CBSS返回预提交接口返回报文信息为空");
        }
        List<Map> rspInfo = (ArrayList<Map>) outMap.get("rspInfo");
        Map rspMap = null;
        for (int i = 0; i < rspInfo.size(); i++) {
            if (orderNo.equals(rspInfo.get(i).get("provOrderId"))) {
                rspMap = rspInfo.get(i);
                break;
            }
        }
        if (null == rspMap) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "业务处理失败，未返回相应订单信息");
        }
        retMap.put("provOrderId", outMap.get("bssOrderId"));
        retMap.put("userName", serialNumber);
        if (null != authPasswd && !"".equals(authPasswd)) {
            retMap.put("userPasswd", authPasswd);
        }
        dealFeeInfo(retMap, rspMap);
        if (null == retMap.get("totalFee")) {
            retMap.put("totalFee", "0");
        }
        exchange.getOut().setBody(retMap);
    }

    private void prepareDate4PreCommit(Map msg, String startDate) {
        preCommonInfo(msg);
        preBase(msg, startDate);
        preExt(msg, startDate);
    }

    private void preCommonInfo(Map msg) {
        msg.put("ordersId", msg.get("orderNo"));
        msg.put("operTypeCode", MagicNumber.OPER_TYPE_ORDER_COMMIT);
    }

    private void preBase(Map msg, String startDate) {
        Map base = new HashMap();
        base.put("subscribeId", msg.get("orderNo"));
        base.put("tradeId", msg.get("orderNo"));
        base.put("tradeTypeCode", MagicNumber.BROAD_BAND_OPEN);
        base.put("execTime", startDate);
        base.put("cancelTag", MagicNumber.TO_BE_CANCEL);
        msg.put("base", base);
    }

    private void preExt(Map msg, String startDate) {
        Map ext = new HashMap();
        ext.putAll(preDiscnt(msg, startDate));
        ext.putAll(preUser(msg, startDate));
        ext.putAll(preItem(msg));
        ext.putAll(preProduct(msg));
        ext.putAll(preRes(msg));
        ext.putAll(preAcct());
        ext.putAll(prePayRelation());
        ext.putAll(preProductType(msg));
        msg.put("ext", ext);
    }

    private Map preDiscnt(Map msg, String startDate) {
        List<Map> elementList = (List<Map>) msg.get("itemList");
        if (null == elementList || 0 == elementList.size()) {
            return new HashMap();
        }
        List<Map> item = new ArrayList<Map>();
        for (Map element : elementList) {
            Map discnt = new HashMap();
            discnt.put("modifyTag", "0");
            discnt.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            discnt.put("startDate", startDate);
            discnt.put("specTag", "0");
            discnt.put("discntCode", element.get("elementId"));
            discnt.put("packageId", element.get("packageId"));
            discnt.put("productId", element.get("commodity"));
            discnt.put("idType", "1");
            discnt.put("id", MagicNumber.DEFAULT_NO_VALUE);
            discnt.put("xDatatype", MagicNumber.STRING_OF_NULL);
            item.add(discnt);
        }
        Map tradeDiscnt = MapUtils.asMap("item", item);
        return MapUtils.asMap("tradeDiscnt", tradeDiscnt);
    }

    private Map preUser(Map msg, String startDate) {
        List<Map> item = new ArrayList<Map>();
        Map user = new HashMap();
        Map mainPrdInfo = (Map) msg.get("mainPrdInfo");
        user.put("prepayTag", mainPrdInfo.get("prePayTag"));
        user.put("productId", mainPrdInfo.get("productId"));
        user.put("eparchyCode", msg.get("city"));
        user.put("cityCode", msg.get("district"));
        user.put("userPasswd", msg.get("userPasswd"));
        user.put("userTypeCode", "0");
        user.put("serialNumber", msg.get("serialNumber"));
        user.put("netTypeCode", "0400");
        user.put("openMode", "0");
        user.put("developStaffId", msg.get("recomPersonId"));
        user.put("developDate", startDate);
        user.put("developEparchyCode", msg.get("recomPersonCityCode"));
        user.put("developCityCode", msg.get("recomPersonDistrict"));
        user.put("developDepartId", msg.get("recomPersonChannelId"));
        item.add(user);
        Map tradeUser = MapUtils.asMap("item", item);
        return MapUtils.asMap("tradeUser", tradeUser);
    }

    private Map preItem(Map msg) {
        List<Map> item = new ArrayList<Map>();
        item.add(createItem("LINK_PHONE", (String) msg.get("contactPhone")));
        item.add(createItem("LINK_NAME", (String) msg.get("contactPerson")));
        Map tradeItem = MapUtils.asMap("item", item);
        return MapUtils.asMap("tradeItem", tradeItem);
    }

    private Map preProduct(Map msg) {
        List<Map> item = new ArrayList<Map>();
        Map product = new HashMap();
        product.put("productId", ((Map) msg.get("mainPrdInfo")).get("productId"));
        product.put("modifyTag", "0");// 0:增加
        product.put("startDate", GetDateUtils.getDate());
        product.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
        product.put("productMode", ((Map) msg.get("mainPrdInfo")).get("productMode"));
        item.add(product);
        Map tradeProduct = MapUtils.asMap("item", item);
        return MapUtils.asMap("tradeProduct", tradeProduct);
    }

    private Map preRes(Map msg) {
        List<Map> item = new ArrayList<Map>();
        Map resMap = new HashMap();
        resMap.put("startDate", GetDateUtils.getDate());
        resMap.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
        resMap.put("reTypeCode", "0");
        resMap.put("modifyTag", "0");
        resMap.put("resCode", msg.get("serialNumber"));
        item.add(resMap);
        Map tradeRes = MapUtils.asMap("item", item);
        return MapUtils.asMap("tradeRes", tradeRes);

    }

    private Map preAcct() {
        List<Map> item = new ArrayList<Map>();
        Map acctMap = new HashMap();
        acctMap.put("openDate", GetDateUtils.getDate());
        item.add(acctMap);
        Map tradeAcct = MapUtils.asMap("item", item);
        return MapUtils.asMap("tradeAcct", tradeAcct);

    }

    private Map prePayRelation() {
        List<Map> item = new ArrayList<Map>();
        Map payrelation = new HashMap();
        payrelation.put("payitemCode", "-1");
        payrelation.put("acctPriority", "1");
        payrelation.put("userPriority", "1");
        payrelation.put("bindType", "1");
        payrelation.put("defaultTag", "1");
        payrelation.put("limitType", "0");
        payrelation.put("limit", "0");
        payrelation.put("complementTag", "0");
        item.add(payrelation);
        Map tradePayrelation = MapUtils.asMap("item", item);
        return MapUtils.asMap("tradePayrelation", tradePayrelation);
    }

    private Map preProductType(Map msg) {
        List<Map> item = new ArrayList<Map>();
        Map productType = new HashMap();
        productType.put("productMode", ((Map) msg.get("mainPrdInfo")).get("productMode"));
        productType.put("productId", ((Map) msg.get("mainPrdInfo")).get("productId"));
        productType.put("modifyTag", "0");
        productType.put("startDate", GetDateUtils.getDate());
        productType.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
        item.add(productType);
        Map tradeProductType = MapUtils.asMap("item", item);
        return MapUtils.asMap("tradeProductType", tradeProductType);
    }

    private Map createItem(String key, String value) {
        return createItem(key, value, null);
    }

    private Map createItem(String key, String value, String Type) {
        return MapUtils.asMap("attrCode", key, "attrValue", value, "xDatatype", Type);
    }

    private void dealFeeInfo(Map retMap, Map rspMap) {
        List<Map> feeList = (ArrayList<Map>) rspMap.get("preFeeInfoRsp");
        if (null == feeList || 0 == feeList.size()) {
            return;
        }
        List<Map> retFeeList = new ArrayList<Map>();
        for (Map fee : feeList) {
            Map feeMap = new HashMap();
            feeMap.put("feeId", fee.get("feeMode"));
            feeMap.put("feeCategory", fee.get("feeTypeCode"));
            feeMap.put("feeDes", fee.get("feeTypeName"));
            feeMap.put("maxRelief", fee.get("maxDerateFee") + "0");
            feeMap.put("origFee", fee.get("fee") + "0");
            retFeeList.add(feeMap);
        }
        Object totalFee = rspMap.get("totalFee");
        retMap.put("totalFee", "0".equals(totalFee) ? "0" : totalFee + "0");// 单位从分转为厘
        retMap.put("feeInfo", retFeeList);
    }
}
