package com.ailk.ecaop.biz.sub.lan;

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

import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.YearPayEcsCheck;
import com.ailk.ecaop.common.utils.YearPayUtils;

@EcRocTag("yearPayEcsCheck")
public class YearPayEcsCheckProcessor extends BaseAopProcessor implements ParamsAppliable {
    //北六3G(预提交，辽宁三户，宽带号码信息查询,三户，正式提交，共线号码查询）
    private static final String[] PARAM_ARRAY = { "ecaop.trades.spec.sUniTrade.paramtersmapping",
        "ecaop.trade.n6.checkUserParametersMapping.91", "ecaop.trades.spec.queryVagueInfoParametersMapping",
        "ecaop.trade.n6.checkUserParametersMapping", "ecaop.masb.odsb.ActivityAryParametersMapping",
    "ecaop.trades.spec.qryRelationInfoParametersMapping" };
    protected final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[6];

    YearPayUtils qwe = new YearPayUtils();
    // 北六3G(预提交，辽宁三户，宽带号码信息查询,三户，正式提交，共线号码查询）
    private static final String[] PARAM_ARRAY_4N25 = { "ecaop.masb.spec.sProductChgParametersMapping",
            "ecaop.masb.odsb.ActivityAryParametersMapping" };
    protected final ParametersMappingProcessor[] pmp4N25 = new ParametersMappingProcessor[6];

    @Override
    public void process(Exchange exchange) throws Exception {
        System.out.println("zsqtest " + pmp + " ");
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Object opeSysType = msg.get("opeSysType");
        checkInputParam(msg, opeSysType);
        String province = msg.get("province").toString();
        Map preSubmitReturnMap = new HashMap();
        YearPayEcsCheck check = "2".equals(opeSysType) ? new YearPayEcs4CBSSCheck()
                : "11|17|18|76|91|97".contains(province) ? new YearPayEcs4N6Check(pmp) : new YearPayEcs4South25Check(
                        pmp4N25);
        preSubmitReturnMap = check.yearPayEcsCheck(exchange, msg);
        Map outMap = new HashMap();
        Message out = new DefaultMessage();
        if ("0".equals(preSubmitReturnMap.get("isNoChangeProduct"))) {// 宽带趸交流程，沿用现有流程，不需要调用订单提交接口回滚
            preSubmitReturnMap.remove("isNoChangeProduct");
            outMap = preSubmitReturnMap;
        }
        else {
            outMap = check.yearPayEcsSub(exchange, preSubmitReturnMap, msg);
            if (!"2".equals(opeSysType) && !"11|17|18|76|91|97".contains(province)) {
                outMap.put("custName", preSubmitReturnMap.get("custName"));
                outMap.put("productType", preSubmitReturnMap.get("productType"));
                outMap.put("productName", preSubmitReturnMap.get("productName"));
                outMap.put("discntName", preSubmitReturnMap.get("discntName"));
                outMap.put("startDate", preSubmitReturnMap.get("startDate"));
                outMap.put("endDate", preSubmitReturnMap.get("endDate"));
            }
        }
        out.setBody(outMap);
        exchange.setOut(out);
    }

    protected void checkInputParam(Map inMap, Object opeSysType) {
        String queryType = inMap.get("queryType").toString();
        if (1 != queryType.length() || !"0123".contains(queryType)) {
            throw new EcAopServerBizException(MagicNumber.FBS_ERROR_CODE,
                    "目前支持通过[0：统一宽带编码 1：宽带账号 2：共线固话 3：与宽带绑定的手机号码]进行号码查询,暂不支持" + queryType);
        }
        Object changeTag = inMap.get("changeTag");
        if (!"2".equals(changeTag)) {
            return;
        }
        checkProductInfo(inMap, changeTag);
        checkbroadDiscntInfo(inMap, changeTag, opeSysType);
    }

    /**
     * 校验产品信息及速率
     *
     * @param inMap
     * @param changeTag
     */
    private void checkProductInfo(Map inMap, Object changeTag) {
        if (!"2".equals(changeTag)) {
            return;
        }
        Object speedLevel = inMap.get("speedLevel");
        if (null == speedLevel || "".equals(speedLevel)) {
            throw new EcAopServerBizException(MagicNumber.FBS_ERROR_CODE, "变更主产品,未下发速率");
        }
        List<Map> productInfo = (List<Map>) inMap.get("productInfo");
        if (null == productInfo || 0 == productInfo.size()) {
            throw new EcAopServerBizException(MagicNumber.FBS_ERROR_CODE, "变更主产品,未下发产品信息");
        }
        for (Map product : productInfo) {
            if ("0".equals(product.get("productMode")) && "0".equals(product.get("optType"))) {
                return;
            }
        }
        throw new EcAopServerBizException(MagicNumber.FBS_ERROR_CODE, "变更主产品,未下发订购的主产品信息");
    }

    /**
     * 校验资费信息
     * 
     * @param inMap
     * @param changeTag
     */
    private void checkbroadDiscntInfo(Map inMap, Object changeTag, Object opeSysType) {
        inMap.put("delayDiscntType", "0");
        if (!"2".equals(changeTag)) {
            return;
        }
        List<Map> broadDiscntInfo = (List<Map>) inMap.get("broadDiscntInfo");
        if (IsEmptyUtils.isEmpty(broadDiscntInfo)) {
            if ("2".equals(opeSysType)) {
                throw new EcAopServerBizException(MagicNumber.FBS_ERROR_CODE, "变更主产品,未下发资费信息");
            }
            return;
        }
        if (1 != broadDiscntInfo.size()) {
            throw new EcAopServerBizException(MagicNumber.FBS_ERROR_CODE, "下发资费信息过多,请检查");
        }
        // for (Map boradDiscnt : boradDiscntInfo) {
        Map broadDiscntAttr = (Map) broadDiscntInfo.get(0).get("broadDiscntAttr");
        if (IsEmptyUtils.isEmpty(broadDiscntAttr)) {
            return;
        }
        Object delayType = broadDiscntAttr.get("delayType");
        if (!IsEmptyUtils.isEmpty(delayType)) {
            if (!"1|2|3".contains(delayType.toString())) {
                throw new EcAopServerBizException(MagicNumber.FBS_ERROR_CODE, "到期资费方式[" + delayType
                        + "]不在[1.续约包年;2.续约标准资费;3.到期停机]范围内");
            }
        }
        Object delayDiscntType = broadDiscntAttr.get("delayDiscntType");
        if (IsEmptyUtils.isEmpty(delayDiscntType)) {
            if (!"0|1".contains(delayDiscntType.toString())) {
                throw new EcAopServerBizException(MagicNumber.FBS_ERROR_CODE, "生效方式方式[" + delayDiscntType
                        + "]不在[0.立即;1.次月;2.到期]范围内");
            }
        }
        inMap.put("delayDiscntType", delayDiscntType);
        // }
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
        for (int i = 0; i < PARAM_ARRAY_4N25.length; i++) {
            pmp4N25[i] = new ParametersMappingProcessor();
            pmp4N25[i].applyParams(new String[] { PARAM_ARRAY_4N25[i] });
        }
    }

}
