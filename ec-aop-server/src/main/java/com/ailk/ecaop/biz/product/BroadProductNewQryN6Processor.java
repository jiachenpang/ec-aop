package com.ailk.ecaop.biz.product;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.lang.RDate;
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
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;

@EcRocTag("broadProductNewQryN6")
public class BroadProductNewQryN6Processor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.trade.optq.BroadProductQryParamMapping",
            "ecaop.trade.n6.checkUserParametersMapping.91" };
    protected final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[2];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Object serialNumber = msg.get("serialNumber");
        // 调用三户接口
        Map threePartMap = MapUtils.asMap("serviceClassCode", "0040", "tradeTypeCode", "0127", "getMode",
                "101001101010001001000000000000", "serialNumber", serialNumber);
        MapUtils.arrayPut(threePartMap, msg, MagicNumber.COPYARRAY);
        Exchange threePartExchage = ExchangeUtils.ofCopy(exchange, threePartMap);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[1], threePartExchage);
        CallEngine.wsCall(threePartExchage, "ecaop.comm.conf.url.UsrForNorthSer" + "." + msg.get("province"));
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchage);
        Map qruUserProductInfo = threePartExchage.getOut().getBody(Map.class);
        Map brdQryMsg = preData4ProductChg(msg, qruUserProductInfo);
        body.put("msg", brdQryMsg);
        exchange.getIn().setBody(body);
        Map brdQryResult = callBroadProductQry(exchange);
        Map response = dealReturn(brdQryResult, msg, qruUserProductInfo);
        exchange.getOut().setBody(response);
    }

    public Map callBroadProductQry(Exchange exchange) throws Exception {
        LanUtils lan = new LanUtils();
        lan.preData(pmp[0], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.BroadbandUsrSer");
        lan.xml2Json("ecaop.trades.optq.template", exchange);
        return exchange.getOut().getBody(Map.class);
    }

    public Map dealReturn(Map brdQryResult, Map msg, Map qruUserProductInfo) throws Exception {
        Map returnMap = new HashMap();
        // 可变更产品信息
        returnMap.put("chgProductInfo", dealChgProduct((List<Map>) brdQryResult.get("productInfo"), msg));

        // 用户欠费信息
        returnMap.put("arrearageNess", dealUserMktCamp((List<Map>) brdQryResult.get("arrearageMess")));

        // 用户当前使用产品信息处理
        returnMap.put("productInfo",
                dealOrgProduct((List<Map>) brdQryResult.get("nowProductInfo"), msg, qruUserProductInfo));
        return returnMap;
    }

    public List<Map> dealChgProduct(List<Map> productInfo, Map msg) {
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
                if ("17".equals(msg.get("province"))) {
                    dis.put("brandSpeed", new ChangeCodeUtils().changeSpeedForSD(dis.get("brandSpeed")));
                }
                else {
                    dis.put("brandSpeed", new ChangeCodeUtils().changeSpeed(dis.get("brandSpeed")));
                }
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

    public List<Map> dealOrgProduct(List<Map> nowProductInfo, Map msg, Map qruUserProductInfo) throws Exception {
        List<Map> productInfoListRsp = new ArrayList<Map>();
        Map orgProductInfoRsp = new HashMap();

        List<Map> paraList = (List<Map>) qruUserProductInfo.get("para");
        if (IsEmptyUtils.isEmpty(paraList)) {
            throw new EcAopServerBizException("9999", "北六ESS未返回PARA信息");
        }
        Object prodCycle = "";// 当前主产品周期
        Object prodTariffCode = "";// 当前主产品资费编码
        String province = (String) msg.get("province");
        for (Map para : paraList) {
            if ("PROD_CYCLE".equals(para.get("paraId"))) {
                prodCycle = para.get("paraValue");
            }
            else if ("PROD_TARIFF_CODE".equals(para.get("paraId"))) {
                prodTariffCode = para.get("paraValue");
            }
        }
        List<Map> userInfoList = (List<Map>) qruUserProductInfo.get("userInfo");
        if (IsEmptyUtils.isEmpty(userInfoList)) {
            throw new EcAopServerBizException("9999", "省份未返回USER_INFO信息");
        }
        for (Map user : userInfoList) {
            List<Map> discntInfo = (List<Map>) user.get("discntInfo");
            if (IsEmptyUtils.isEmpty(discntInfo)) {
                continue;
            }
            for (Map discnt : discntInfo) {
                if (!prodTariffCode.equals(discnt.get("discntCode"))) {
                    continue;
                }
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
                List<Map> attrInfo = (ArrayList<Map>) discnt.get("attrInfo");
                if (!IsEmptyUtils.isEmpty(attrInfo)) {
                    for (Map attr : attrInfo) {
                        if ("fixedHire".equals(attr.get("attrCode"))) {
                            prodCycle = attr.get("attrValue");
                        }
                    }
                }
                String startDate = discnt.get("startDate").toString();
                String substring = startDate.substring(6, 8);
                orgProductInfoRsp.put("startDate", startDate);
                String endDate = sdf
                        .format(RDate.addMonths(sdf.parse(startDate), Integer.valueOf(prodCycle.toString())));
                endDate = endDate.substring(0, 8) + "000000";
                endDate = DateUtils.addSeconds(endDate, -1);
                if ("97|76".contains(province)) {
                    endDate = DateUtils.theMonthLastTime(DateUtils.addMonths(DateUtils.addSeconds(endDate, 1), "-1"));
                }
                if ("11".equals(province)) {
                    String nextDiscntDate = GetDateUtils.getNextDiscntDate(startDate);
                    endDate = sdf
                            .format(RDate.addMonths(sdf.parse(nextDiscntDate), Integer.valueOf(prodCycle.toString())));
                    endDate = endDate.substring(0, 8) + "000000";
                    endDate = DateUtils.addSeconds(endDate, -1);
                }
                if ("18".equals(province)) {
                    if (!"01".equals(substring)) {
                        endDate = GetDateUtils.getMonthLastDayFormat1(endDate);
                    }
                }
                orgProductInfoRsp.put("endDate", endDate);
            }
        }

        if (!IsEmptyUtils.isEmpty(nowProductInfo)) {
            for (Map product : nowProductInfo) {
                orgProductInfoRsp.put("productId", product.get("productId"));
                orgProductInfoRsp.put("productName", product.get("productName"));
                orgProductInfoRsp.put("productDesc", product.get("productDesc"));
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
        List<Map> paraList = (List<Map>) threePartInfo.get("para");
        if (IsEmptyUtils.isEmpty(paraList)) {
            throw new EcAopServerBizException("9999", "北六ESS未返回PARA信息");
        }
        Object userId = "";
        Object serialNumber = "";
        Object productId = "";
        Object prodTariffCode = "";// 当前主产品资费编码
        for (Map para : paraList) {
            if ("PROD_TARIFF_CODE".equals(para.get("paraId"))) {
                prodTariffCode = para.get("paraValue");
            }
        }
        List<Map> userInfoList = (List<Map>) threePartInfo.get("userInfo");
        if (IsEmptyUtils.isEmpty(userInfoList)) {
            throw new EcAopServerBizException("9999", "省份未返回USER_INFO信息");
        }
        List<Map> discntInfoList = new ArrayList<Map>();
        for (Map user : userInfoList) {
            userId = user.get("userId");
            serialNumber = orgMsg.get("serialNumber");
            productId = user.get("productId");
            List<Map> discntInfo = (List<Map>) user.get("discntInfo");
            if (IsEmptyUtils.isEmpty(discntInfo)) {
                continue;
            }
            for (Map discnt : discntInfo) {
                if (!prodTariffCode.equals(discnt.get("discntCode"))) {
                    continue;
                }
                /*if ("18".equals(orgMsg.get("province"))) {
                    List<Map> attrInfo = (ArrayList<Map>) discnt.get("attrInfo");
                    if (!IsEmptyUtils.isEmpty(attrInfo)) {
                        for (Map attr : attrInfo) {
                            if (!"expireDealMode".equals(attr.get("attrCode"))) {
                                throw new EcAopServerBizException("1624", "您的宽带为包月用户，系统暂不支持办理包年产品，请选择“普通交费”办理缴费!");
                            }
                        }
                    }
                }*/
                discntInfoList.add(discnt);
            }
        }
        Map brdQryMsg = new HashMap(orgMsg);
        brdQryMsg.put("userId", userId);
        brdQryMsg.put("serialNumber", serialNumber);
        brdQryMsg.put("productId", productId);
        brdQryMsg.put("discntInfo", discntInfoList);

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
