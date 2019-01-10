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
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;

@EcRocTag("broadProductNewQryCBSS")
public class BroadProductNewQryCBSSProcessor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.trade.cbss.checkUserParametersMapping",
            "ecaop.trade.optq.BroadProductQryParametersMapping" };
    protected final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[2];

    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");

        // 调用三户接口
        Map threePartMap = MapUtils.asMap("serviceClassCode", "0040", "tradeTypeCode", "9999", "getMode",
                "101001101010001001000000000000", "serialNumber", msg.get("serialNumber"));
        Map para = MapUtils.asMap("paraId", "OPERATE_FLAG", "paraValue", "AOP_KD_DISCNT");
        List<Map> paraList = new ArrayList<Map>();
        paraList.add(para);
        threePartMap.putAll(MapUtils.asMap("para", paraList));
        MapUtils.arrayPut(threePartMap, msg, MagicNumber.COPYARRAY);
        Exchange threePartExchage = ExchangeUtils.ofCopy(exchange, threePartMap);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[0], threePartExchage);
        CallEngine.wsCall(threePartExchage, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchage);

        Map qruUserProductInfo = threePartExchage.getOut().getBody(Map.class);
        Map brdQryMsg = preData4ProductChg(msg, qruUserProductInfo);
        body.put("msg", brdQryMsg);
        exchange.getIn().setBody(body);
        Map brdQryResult = callBroadProductQry(exchange);
        Map response = dealReturn(brdQryResult);
        exchange.getOut().setBody(response);
    }

    public Map callBroadProductQry(Exchange exchange) throws Exception {
        LanUtils lan = new LanUtils();
        lan.preData(pmp[1], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.newcbss.services.BroadbandUsrSer");
        lan.xml2Json("ecaop.trade.optq.template", exchange);
        return exchange.getOut().getBody(Map.class);
    }

    public Map dealReturn(Map brdQryResult) throws Exception {
        Map returnMap = new HashMap();
        // 可变更产品信息
        returnMap.put("chgProductInfo", dealChgProduct((List<Map>) brdQryResult.get("productInfo")));

        // 用户欠费信息
        returnMap.put("arrearageNess", dealUserMktCamp((List<Map>) brdQryResult.get("arrearageMess")));

        // 用户当前使用产品信息处理
        returnMap.put("productInfo", dealOrgProduct((List<Map>) brdQryResult.get("nowProductInfo")));
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
        if (null != arrearageMess && arrearageMess.size() > 0) {
            Map arrearageFeeInfo = arrearageMess.get(0);
            Map arrearageInfo = new HashMap();
            arrearageInfo.put("serialNumber", arrearageFeeInfo.get("serialNumber"));
            arrearageInfo.put("areaCode", arrearageFeeInfo.get("areaCode"));
            arrearageInfo.put("numKindCode", arrearageFeeInfo.get("numKindCode"));
            arrearageInfo.put("arrearageUserName", arrearageFeeInfo.get("arrearageUserName"));
            retList.add(arrearageInfo);
        }
        return retList;
    }

    public List<Map> dealOrgProduct(List<Map> nowProductInfo) {
        List<Map> productInfoListRsp = new ArrayList<Map>();
        Map orgProductInfoRsp = new HashMap();
        if (!IsEmptyUtils.isEmpty(nowProductInfo)) {
            for (Map product : nowProductInfo) {
                orgProductInfoRsp.put("productId", product.get("productId"));
                orgProductInfoRsp.put("productName", product.get("productName"));
                orgProductInfoRsp.put("productDesc", product.get("productDesc"));
                orgProductInfoRsp.put("startDate", product.get("nowStartTime"));
                orgProductInfoRsp.put("endDate", product.get("nowEndTime"));
                orgProductInfoRsp.put("nextStartDate", product.get("startTime"));
                orgProductInfoRsp.put("nextEndDate", product.get("endTime"));
            }
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

    public Map preData4ProductChg(Map orgMsg, Map threePartInfo) {
        Map userInfo = ((List<Map>) threePartInfo.get("userInfo")).get(0);
        // 为调用可变更产品查询接口准备参数
        Object userId = userInfo.get("userId");
        Object serialNumber = orgMsg.get("serialNumber");
        List<Map> discntInfoList = new ArrayList<Map>();
        Object prodTariffCode = "";// 当前主产品资费编码
        Object bDiscntCode = "";// 资费编码
        List<Map> paramList = (List<Map>) threePartInfo.get("para");
        if (IsEmptyUtils.isEmpty(paramList)) {
            throw new EcAopServerBizException("9999", "CBSS未返回PARA信息");
        }
        for (Map param : paramList) {
            if ("PROD_TARIFF_CODE".equals(param.get("paraId"))) {
                prodTariffCode = param.get("paraValue");
            }
        }
        for (Map discntInfo : (List<Map>) userInfo.get("discntInfo")) {
            // 主产品id对应的资费
            bDiscntCode = discntInfo.get("discntCode");
            if (!prodTariffCode.equals(bDiscntCode)) {
                continue;
            }
            discntInfoList.add(discntInfo);
        }

        Map brdQryMsg = new HashMap(orgMsg);
        brdQryMsg.put("userId", userId);
        brdQryMsg.put("serialNumber", serialNumber);
        brdQryMsg.put("productId", userInfo.get("productId"));
        brdQryMsg.put("discntInfo", discntInfoList);

        return brdQryMsg;
    }

    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
