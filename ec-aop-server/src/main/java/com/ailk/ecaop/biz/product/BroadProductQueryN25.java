package com.ailk.ecaop.biz.product;

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
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;

/**
 * 南25省可变更产品查询接口
 * 
 * @author Steven
 */
@EcRocTag("broadProductQryN25")
public class BroadProductQueryN25 extends BaseAopProcessor implements ParamsAppliable {

    // 依次为获取宽带账号接口、三户信息查询接口、已订购产品查询接口、可变更产品查询接口
    private static final String[] PARAM_ARRAY = { "ecaop.trade.cbpq.getBroadbandAcctInfoParametersMapping",
            "ecaop.masb.chku.checkUserParametersMapping",
            "ecaop.trade.cbpq.QryUserProInfoParametersMapping",
            "ecaop.trade.cbpq.BroadProductQryParametersMapping" };
    private static final String[] URL_ARRAY = { "ecaop.comm.conf.url.osn.services.numberser",
            "ecaop.comm.conf.url.osn.services.usrser", "ecaop.comm.conf.url.osn.services.usrser",
            "ecaop.comm.conf.url.osn.services.BroadbandUsrSer" };
    private static final String[] TEMPLATE_ARRAY = { "ecaop.trade.cbpq.getBroadbandAcctInfo.template",
            "ecaop.masb.chku.checkUserTemplate", "ecaop.trade.cbpq.qryUserProInfo.template",
            "ecaop.trade.cbpq.template" };
    private final ParametersMappingProcessor[] PMP = new ParametersMappingProcessor[PARAM_ARRAY.length];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Object apptx = body.get("apptx");
        Map msg = (Map) body.get("msg");

        // 调用获取宽带账号接口
        Map getBroadbandMap = new HashMap();
        if ("1".equals(msg.get("queryType"))) {
            getBroadbandMap.put("authAcctId", msg.get("serialNumber"));
        }
        else {
            getBroadbandMap.put("serialNumber", msg.get("serialNumber"));
        }
        getBroadbandMap.put("qryType", msg.get("queryType"));
        getBroadbandMap.put("serviceClassCode", "0200");
        getBroadbandMap.put("areaCode", msg.get("areaCode"));
        MapUtils.arrayPut(getBroadbandMap, msg, MagicNumber.COPYARRAY);
        Exchange getBroadbandExchange = ExchangeUtils.ofCopy(exchange, getBroadbandMap);
        LanUtils lan = new LanUtils();
        lan.preData(PMP[0], getBroadbandExchange);
        CallEngine.wsCall(getBroadbandExchange, URL_ARRAY[0]);
        lan.xml2Json(TEMPLATE_ARRAY[0], getBroadbandExchange);
        Map getBroadbandRet = getBroadbandExchange.getOut().getBody(Map.class);
        Map acctInfo = (Map) getBroadbandRet.get("acctInfo");
        Object serialNumber = msg.get("serialNumber");
        if (!IsEmptyUtils.isEmpty(acctInfo)) {
            serialNumber = acctInfo.get("serialNumber");
        }

        // 调用三户接口
        Map threePartMap = new HashMap();
        threePartMap.put("serviceClassCode", "0200");
        threePartMap.put("tradeTypeCode", "0021");
        threePartMap.put("getMode", "101001101010001001000000000000");
        threePartMap.put("areaCode", msg.get("areaCode"));
        threePartMap.put("serialNumber", serialNumber);
        MapUtils.arrayPut(threePartMap, msg, MagicNumber.COPYARRAY);
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
        lan.preData(PMP[1], threePartExchange);
        CallEngine.wsCall(threePartExchange, URL_ARRAY[1]);
        lan.xml2Json(TEMPLATE_ARRAY[1], threePartExchange);
        Map out = threePartExchange.getOut().getBody(Map.class);
        Map userInfo = (Map) out.get("userInfo");
        if (IsEmptyUtils.isEmpty(userInfo)) {
            throw new EcAopServerBizException("9999", "省份三户接口未返回用户信息,请求apptx:" + apptx);
        }
        Map arrearageFeeInfo = (Map) userInfo.get("arrearageFeeInfo");
        if (!IsEmptyUtils.isEmpty(arrearageFeeInfo) && arrearageFeeInfo.get("depositMoney").toString().startsWith("-")) {
            throw new EcAopServerBizException("0100", "欠费号码");
        }

        // 调用已订购产品查询接口
        Map qryUserProInfo = MapUtils.asMap("serviceClassCode", "0200", "areaCode", msg.get("areaCode"),
                "serialNumber", serialNumber, "tradeTypeCode", "0090");
        // 目前省份需要区号，另：号码节点也需要区号
        MapUtils.arrayPut(qryUserProInfo, msg, MagicNumber.COPYARRAY);
        Exchange qryUserProInfoExchage = ExchangeUtils.ofCopy(exchange, qryUserProInfo);
        lan.preData(PMP[2], qryUserProInfoExchage);
        CallEngine.wsCall(qryUserProInfoExchage, URL_ARRAY[2]);
        lan.xml2Json(TEMPLATE_ARRAY[2], qryUserProInfoExchage);

        Map qruUserProductInfo = qryUserProInfoExchage.getOut().getBody(Map.class);
        msg.put("serialNumber", serialNumber);
        Map brdQryMsg = preData4ProductChg(msg, qruUserProductInfo, apptx);
        body.put("msg", brdQryMsg);
        exchange.getIn().setBody(body);
        Map brdQryResult = callBroadProductQry(exchange);
        Map response = dealReturn(brdQryResult, qruUserProductInfo, apptx);
        exchange.getOut().setBody(response);
    }

    public Map dealReturn(Map brdQryResult, Map threePartInfo, Object apptx) throws Exception {
        Map returnMap = new HashMap();
        // 可变更产品信息
        returnMap.put("chgProductInfo", dealChgProduct((List<Map>) brdQryResult.get("productInfo")));

        // 用户当前活动信息
        returnMap.put("userMktCamp", dealUserMktCamp(threePartInfo, apptx));

        // 用户当前使用产品信息处理
        returnMap.put("productInfo", dealOrgProduct(threePartInfo, apptx));
        return returnMap;
    }

    /**
     * 处理可变更产品查询信息
     * 
     * @param productInfo
     * @return
     */
    public List<Map> dealChgProduct(List<Map> productInfo) {
        if (IsEmptyUtils.isEmpty(productInfo)) {
            return productInfo;
        }
        for (Map prod : productInfo) {
            List<Map> chgDiscntInfo = (List<Map>) prod.get("chgDiscntInfo");
            if (IsEmptyUtils.isEmpty(chgDiscntInfo)) {
                continue;
            }
            for (Map dis : chgDiscntInfo) {
                if (null == dis.get("discntDesc") || "".equals(dis.get("discntDesc"))) {
                    dis.put("discntDesc", dis.get("discntName"));
                }
                dis.put("brandSpeed", new ChangeCodeUtils().changeSpeed(dis.get("brandSpeed")));
                Object startEnable = dis.get("startEnable");
                dis.put("startEnable", "0".equals(startEnable) ? "1" : "1".equals(startEnable) ? "2" : "0");
            }
        }
        return productInfo;
    }

    // 为可变更产品查询接口准备入参
    public Map preData4ProductChg(Map orgMsg, Map threePartInfo, Object apptx) {
        List<Map> userInfoList = (List<Map>) threePartInfo.get("userInfo");
        if (IsEmptyUtils.isEmpty(userInfoList)) {
            throw new EcAopServerBizException("9999", "省份未返回USER_INFO信息,请求apptx:" + apptx);
        }
        List<Map> discntInfoList = new ArrayList<Map>();
        Map brdQryMsg = new HashMap(orgMsg);
        for (Map userInfo : userInfoList) {
            List<Map> productInfo = (List<Map>) userInfo.get("productInfo");
            if (IsEmptyUtils.isEmpty(productInfo)) {
                throw new EcAopServerBizException("9999", "省份未返回PRODUCT_INFO信息,请求apptx:" + apptx);
            }
            for (Map product : productInfo) {
                if (!"01".equals(product.get("productMode"))) {
                    continue;
                }
                brdQryMsg.put("productId", product.get("productId"));
                List<Map> productPackageList = (List<Map>) product.get("productPackage");
                if (IsEmptyUtils.isEmpty(productPackageList)) {
                    continue;
                }
                for (Map productPackage : productPackageList) {
                    List<Map> custDiscntList = (List<Map>) productPackage.get("custDiscnt");
                    if (IsEmptyUtils.isEmpty(custDiscntList)) {
                        continue;
                    }
                    for (Map custDiscnt : custDiscntList) {
                        discntInfoList.add(MapUtils.asMap("discntCode", custDiscnt.get("discntCode")));
                    }
                }
            }
            brdQryMsg.put("userId", userInfo.get("userId"));
            brdQryMsg.put("serialNumber", orgMsg.get("serialNumber"));
        }
        brdQryMsg.put("discntInfo", discntInfoList);

        // 为调用可变更产品查询接口准备参数
        if (discntInfoList.isEmpty()) {
            throw new EcAopServerBizException("9999", "省份bss三户资料查询未返回当前资费ID!,请求apptx:" + apptx);
        }
        return brdQryMsg;
    }

    /**
     * 处理用户当前产品信息
     * 
     * @param threePartInfo
     * @return
     */
    public List<Map> dealOrgProduct(Map threePartInfo, Object apptx) {
        List<Map> productInfoListRsp = new ArrayList<Map>();
        Map orgProductInfoRsp = new HashMap();
        List<Map> userInfoList = (List<Map>) threePartInfo.get("userInfo");
        if (IsEmptyUtils.isEmpty(userInfoList)) {
            throw new EcAopServerBizException("9999", "省份未返回USER_INFO信息,请求apptx:" + apptx);
        }
        List<Map> discntInfo = new ArrayList<Map>();
        Object brandNumber = null;
        for (Map userInfo : userInfoList) {
            Map brandInfo = (Map) userInfo.get("brandInfo");
            if (!IsEmptyUtils.isEmpty(brandInfo)) {
                brandNumber = brandInfo.get("brandNumber");
            }
            if (null == brandNumber) {
                List<Map> userMktCampList = (List<Map>) userInfo.get("userMktCamp");
                if (!IsEmptyUtils.isEmpty(userMktCampList)) {
                    Map userMktCamp = userMktCampList.get(0);
                    try {
                        brandNumber = DateUtils.diffMonths(userMktCamp.get("endDate").toString(),
                                userMktCamp.get("startDate").toString());
                    }
                    catch (Exception e) {
                        throw new EcAopServerBizException("9999", "省份未返回时间不合法:" + e.getMessage() + ",请求apptx:" + apptx);
                    }
                }
            }
            List<Map> productInfo = (List<Map>) userInfo.get("productInfo");
            if (IsEmptyUtils.isEmpty(productInfo)) {
                throw new EcAopServerBizException("9999", "省份未返回PRODUCT_INFO信息,请求apptx:" + apptx);
            }
            for (Map product : productInfo) {
                if (!"01".equals(product.get("productMode"))) {
                    continue;
                }
                Object speed = product.get("brandSpeed");
                if (IsEmptyUtils.isEmpty(speed)) {
                    throw new EcAopServerBizException("9999", "省份未返回速率信息,请求apptx:" + apptx);
                }
                Object brandSpeed = new ChangeCodeUtils().changeSpeed(speed);
                orgProductInfoRsp.put("productId", product.get("productId"));
                orgProductInfoRsp.put("productName", product.get("productName"));
                orgProductInfoRsp.put("productDesc", product.get("productDesc"));
                orgProductInfoRsp.put("startDate", product.get("startDate"));
                orgProductInfoRsp.put("endDate", product.get("endDate"));
                List<Map> productPackageList = (List<Map>) product.get("productPackage");
                if (IsEmptyUtils.isEmpty(productPackageList)) {
                    continue;
                }
                for (Map productPackage : productPackageList) {
                    List<Map> custDiscntList = (List<Map>) productPackage.get("custDiscnt");
                    if (IsEmptyUtils.isEmpty(custDiscntList)) {
                        continue;
                    }
                    for (Map custDiscnt : custDiscntList) {
                        Map temp = new HashMap();
                        Object discntCode = custDiscnt.get("discntCode");
                        temp.put("discntCode", discntCode);
                        temp.put("discntName", discntCode);
                        temp.put("brandSpeed", brandSpeed);
                        temp.put("discntDesc", discntCode);
                        temp.put("brandNumber", brandNumber);
                        discntInfo.add(temp);
                    }
                }
            }
        }

        // 当前产品下主资费信息处理
        orgProductInfoRsp.put("discntInfo", discntInfo);
        productInfoListRsp.add(orgProductInfoRsp);
        return productInfoListRsp;
    }

    public Map callBroadProductQry(Exchange exchange) throws Exception {
        LanUtils lan = new LanUtils();
        lan.preData(PMP[3], exchange);
        CallEngine.wsCall(exchange, URL_ARRAY[3]);
        lan.xml2Json(TEMPLATE_ARRAY[3], exchange);
        return exchange.getOut().getBody(Map.class);
    }

    public List<Map> dealUserMktCamp(Map threePartInfo, Object apptx) throws Exception {
        List<Map> retList = new ArrayList<Map>();
        List<Map> userInfoList = (List<Map>) threePartInfo.get("userInfo");
        if (IsEmptyUtils.isEmpty(userInfoList)) {
            throw new EcAopServerBizException("9999", "省份未返回USER_INFO信息,请求apptx:" + apptx);
        }
        String[] copyArray = { "mktCampId", "mktCampName", "startDate", "endDate" };
        for (Map user : userInfoList) {
            List<Map> userMktCampList = (List<Map>) user.get("userMktCamp");
            if (IsEmptyUtils.isEmpty(userMktCampList)) {
                continue;
            }
            for (Map userMktCamp : userMktCampList) {
                Map temp = new HashMap();
                MapUtils.arrayPut(temp, userMktCamp, copyArray);
                retList.add(temp);
            }
        }
        return retList;
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            PMP[i] = new ParametersMappingProcessor();
            PMP[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
}
