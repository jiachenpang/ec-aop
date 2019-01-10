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

@EcRocTag("broadProductNewQryN25")
public class BroadProductNewQryN25Processor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.trade.optq.BroadProductQryParamMapping",
    "ecaop.trade.cbpq.QryUserProInfoParametersMapping" };
    protected final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[2];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Object apptx = body.get("apptx");
        Map msg = (Map) body.get("msg");
        String areaCode = ChangeCodeUtils.changeCityCode(msg);
        Object serialNumber = msg.get("serialNumber");
        LanUtils lan = new LanUtils();
        // 调用已订购产品查询接口
        Map qryUserProInfo = MapUtils.asMap("serviceClassCode", "0200", "areaCode", areaCode,
                "serialNumber", serialNumber, "tradeTypeCode", "0090");
        MapUtils.arrayPut(qryUserProInfo, msg, MagicNumber.COPYARRAY);
        Exchange qryUserProInfoExchage = ExchangeUtils.ofCopy(exchange, qryUserProInfo);
        lan.preData(pmp[1], qryUserProInfoExchage);
        CallEngine.wsCall(qryUserProInfoExchage, "ecaop.comm.conf.url.osn.services.usrser");
        lan.xml2Json("ecaop.trade.cbpq.qryUserProInfo.template", qryUserProInfoExchage);

        Map qruUserProductInfo = qryUserProInfoExchage.getOut().getBody(Map.class);
        Map brdQryMsg = preData4ProductChg(msg, qruUserProductInfo, apptx);
        body.put("msg", brdQryMsg);
        exchange.getIn().setBody(body);
        Map brdQryResult = callBroadProductQry(exchange);
        Map response = dealReturn(brdQryResult);
        exchange.getOut().setBody(response);
    }

    public Map dealReturn(Map brdQryResult) throws Exception {
        Map returnMap = new HashMap();
        // 可变更产品信息
        returnMap.put("chgProductInfo", dealChgProduct((List<Map>) brdQryResult.get("productInfo")));

        // 用户欠费信息
        List<Map> arrearageMess = (List<Map>) brdQryResult.get("arrearageMess");
        if (!IsEmptyUtils.isEmpty(arrearageMess)) {
            returnMap.put("arrearageNess", dealUserMktCamp(arrearageMess));
        }
        // 用户当前使用产品信息处理
        List<Map> nowProductInfo = (List<Map>) brdQryResult.get("nowProductInfo");
        if (!IsEmptyUtils.isEmpty(nowProductInfo)) {
            returnMap.put("productInfo", dealOrgProduct(nowProductInfo));
        }
        return returnMap;
    }

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
                dis.put("brandSpeed", dis.get("brandSpeed"));
                Object startEnable = dis.get("startEnable");
                dis.put("startEnable", "0".equals(startEnable) ? "1" : "1".equals(startEnable) ? "2" : "0");
            }
        }
        return productInfo;
    }

    public List<Map> dealUserMktCamp(List<Map> arrearageMess) throws Exception {
        List<Map> retList = new ArrayList<Map>();
        Map arrearageFeeInfo = arrearageMess.get(0);
        Map arrearageInfo = new HashMap();
        arrearageInfo.put("arrearageNumber", arrearageFeeInfo.get("serialNumber"));
        arrearageInfo.put("areaCode", arrearageFeeInfo.get("areaCode"));
        arrearageInfo.put("numKindCode", arrearageFeeInfo.get("numKindCode"));
        arrearageInfo.put("arrearageUserName", arrearageFeeInfo.get("arrearageUserName"));
        retList.add(arrearageInfo);
        return retList;
    }

    public List<Map> dealOrgProduct(List<Map> nowProductInfo) {
        List<Map> productInfoListRsp = new ArrayList<Map>();
        Map orgProductInfoRsp = new HashMap();
        for (Map product : nowProductInfo) {
            orgProductInfoRsp.put("productId", product.get("productId"));
            orgProductInfoRsp.put("productName", product.get("productName"));
            orgProductInfoRsp.put("productDesc", product.get("productDesc"));
            orgProductInfoRsp.put("startDate", product.get("nowStartTime"));
            orgProductInfoRsp.put("endDate", product.get("nowEndTime"));
            orgProductInfoRsp.put("nextStartDate", product.get("startTime"));
            orgProductInfoRsp.put("nextEndDate", product.get("endTime"));
        }
        // 当前产品下主资费信息处理
        List<Map> orgDistInfoListRsp = dealOrgDiscntInfo(nowProductInfo);
        orgProductInfoRsp.put("discntInfo", orgDistInfoListRsp);

        productInfoListRsp.add(orgProductInfoRsp);
        return productInfoListRsp;
    }

    public List<Map> dealOrgDiscntInfo(List<Map> nowProductInfo) {
        Map orgDistInfoRsp = new HashMap();
        List<Map> orgDistInfoListRsp = new ArrayList<Map>();
        if (!IsEmptyUtils.isEmpty(nowProductInfo)) {
            for (Map product : nowProductInfo) {
                List<Map> discntInfo = (List<Map>) product.get("discntInfo");
                for (Map discnt : discntInfo) {
                    orgDistInfoRsp.put("discntCode", discnt.get("discntCode"));
                    orgDistInfoRsp.put("discntName", discnt.get("discntName"));
                    orgDistInfoRsp.put("brandSpeed", discnt.get("brandSpeed"));
                    orgDistInfoRsp.put("brandNumber", discnt.get("brandNumber"));
                }
            }
        }
        orgDistInfoListRsp.add(orgDistInfoRsp);
        return orgDistInfoListRsp;
    }

    public Map callBroadProductQry(Exchange exchange) throws Exception {
        LanUtils lan = new LanUtils();
        lan.preData(pmp[0], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.BroadbandUsrSer");
        lan.xml2Json("ecaop.trades.optq.template", exchange);
        return exchange.getOut().getBody(Map.class);
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

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
