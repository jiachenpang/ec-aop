package com.ailk.ecaop.biz.brd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.sub.lan.YearPayEcs4N6Sub;
import com.ailk.ecaop.biz.year.YearForBJ;
import com.ailk.ecaop.biz.year.YearForHB;
import com.ailk.ecaop.biz.year.YearForHLJ;
import com.ailk.ecaop.biz.year.YearForHN;
import com.ailk.ecaop.biz.year.YearForLN;
import com.ailk.ecaop.biz.year.YearForSD;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;

@EcRocTag("yearPaySubN6")
public class YearPaySubN6processor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.trades.spec.sUniTrade.paramtersmapping",
            "ecaop.trade.n6.checkUserParametersMapping.91", "ecaop.trades.spec.queryVagueInfoParametersMapping",
            "ecaop.trade.n6.checkUserParametersMapping", "ecaop.masb.odsb.ActivityAryParametersMapping",
            "ecaop.trades.spec.qryRelationInfoParametersMapping", "ecaop.masb.chph.gifa.ParametersMapping" };
    protected final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[7];
	@Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        String province = msg.get("province").toString();
        Map threePartInfo = callThreePart(exchange, msg);
        if (!IsEmptyUtils.isEmpty(msg.get("relationTypeCode"))) {
            if ("18".equals(province)) {
                new YearForHB(pmp).process(exchange, msg, threePartInfo);
            }
            else if ("17".equals(province)) {
                new YearForSD(pmp).process(exchange, msg, threePartInfo);
            }
            else if ("11".equals(province)) {
                new YearForBJ(pmp).process(exchange, msg, threePartInfo);
            }
            else if ("91".equals(province)) {
                new YearForLN(pmp).process(exchange, msg, threePartInfo);
            }
            else if ("97".equals(province)) {
                new YearForHLJ(pmp).process(exchange, msg, threePartInfo);
            }
            else {
                new YearForHN(pmp).process(exchange, msg, threePartInfo);
            }
        }
        else {
            Map preSubmitReturnMap = new HashMap();
            YearPayEcs4N6Sub sub = new YearPayEcs4N6Sub(pmp);
            preSubmitReturnMap = sub.yearPayEcsCheck(exchange, msg);
            Message out = new DefaultMessage();
            Map outMap = new HashMap();
            // 宽带趸交流程，沿用现有流程
            if ("0".equals(preSubmitReturnMap.get("isNoChangeProduct"))) {
                preSubmitReturnMap.remove("isNoChangeProduct");
                outMap = preSubmitReturnMap;
            }
            else {
                outMap = sub.yearPayEcsSub(exchange, preSubmitReturnMap, msg);
            }
            out.setBody(outMap);
            exchange.setOut(out);
        }
    }

    /**
     * 调用三户接口,获取预提交的必要信息
     *
     * @param exchange
     * @param msg
     * @return
     * @throws Exception
     */
    public Map callThreePart(Exchange exchange, Map msg) throws Exception {
        Map threePartMap = MapUtils.asMap("getMode", "1111111111100013111100001100001", "serialNumber",
                msg.get("serialNumber"), "tradeTypeCode", "9999", "serviceClassCode", "0040");
        MapUtils.arrayPut(threePartMap, msg, MagicNumber.COPYARRAY);
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[1], threePartExchange);
        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.UsrForNorthSer." + msg.get("province"));
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        Map retMap = threePartExchange.getOut().getBody(Map.class);
        List<Map> custInfoList = (List<Map>) retMap.get("custInfo");
        List<Map> userInfoList = (List<Map>) retMap.get("userInfo");
        List<Map> acctInfoList = (List<Map>) retMap.get("acctInfo");
        // 需要通过userInfo的返回,拼装老产品信息
        Map custInfo = custInfoList.get(0);
        Map userInfo = userInfoList.get(0);
        Map acctInfo = acctInfoList.get(0);
        String eparchyCode = ChangeCodeUtils.changeCityCode(msg);
        List<Map> uuInfo = (List<Map>) userInfo.get("uuInfo");
        String relationTypeCode = "";
        if (!IsEmptyUtils.isEmpty(uuInfo)) {
            for (Map temMap : uuInfo) {
                relationTypeCode = (String) temMap.get("relationTypeCode");
                if (relationTypeCode.startsWith("8")) {
                    msg.put("mixUserId", temMap.get("userIdA"));
                    msg.put("mixNumber", temMap.get("serialNumberA"));
                    msg.put("relationTypeCode", relationTypeCode);
                    msg.put("mixProduct", temMap.get("productIdA"));
                }
            }
        }
        Map arrearageFeeInfo = (Map) ((List<Map>) retMap.get("userInfo")).get(0).get("arrearageFeeInfo");
        List<Map> arrearageMess = new ArrayList<Map>();
        if (!IsEmptyUtils.isEmpty(arrearageFeeInfo)) {
            if (Integer.valueOf(arrearageFeeInfo.get("depositMoney").toString()) < 0) {
                Map arrearageInfo = new HashMap();
                arrearageInfo.put("arrearageNumber", msg.get("serialNumber"));
                arrearageInfo.put("areaCode", msg.get("areaCode"));
                arrearageInfo.put("arrearageType", "0");
                arrearageMess.add(arrearageInfo);
            }
        }
        if (arrearageMess.size() > 0) {
            msg.put("arrearageMess", arrearageMess);
        }
        List<Map> uattrInfo = (List<Map>) userInfo.get("attrInfo");
        if (!IsEmptyUtils.isEmpty(uattrInfo) && "1".equals(msg.get("changeTag"))) {
            for (Map attr : uattrInfo) {
                if ("SPEED".equals(attr.get("attrCode"))) {
                    msg.put("speedLevel", attr.get("attrValue"));
                }
            }
        }
        String tradeId = (String) GetSeqUtil.getSeqFromCb(pmp[6], ExchangeUtils.ofCopy(exchange, msg),
                "seq_trade_id", 1).get(0);
        String orderId = (String) GetSeqUtil.getSeqFromCb(pmp[6], ExchangeUtils.ofCopy(exchange, msg),
                "seq_trade_id", 1).get(0);
        msg.putAll(MapUtils.asMap("tradeId", tradeId, "orderId", orderId, "userId", userInfo.get("userId"), "custId",
                custInfo.get("custId"), "acctId", acctInfo.get("acctId"), "productName", userInfo.get("productName"),
                "custName", custInfo.get("custName"), "contactPerson", custInfo.get("contactPerson"), "brandCode",
                userInfo.get("brandCode"), "productTypeCode", userInfo.get("productTypeCode"),
                "productId", userInfo.get("productId"), "eparchyCode", eparchyCode,
                "customerName", custInfo.get("customerName"), "tradeMethod", exchange.getMethodCode()));
        return retMap;
    }
	@Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
