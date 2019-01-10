package com.ailk.ecaop.biz.sub;

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
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;

/**
 * 一号多卡，空中卡数据同步
 * 
 * @author shenzy 20170307
 */
@EcRocTag("rioOtaOpencarddate4N25")
public class RioOtaOpencarddate4N25Processors extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.trades.srosPre.ParametersMapping",
            "ecaop.trades.srosSub.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[2];
    private final LanUtils lan = new LanUtils();

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = (Map) exchange.getIn().getBody();
        Map msg = (Map) body.get("msg");
        Map temp = new HashMap(msg);
        Exchange exchangeSub = ExchangeUtils.ofCopy(exchange, temp);
        dealOpenCardPreParams(msg);
        // 调bss卡数据同步接口
        try {
            lan.preData(pmp[0], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.SimSer");
            lan.xml2Json("ecaop.trades.srosPre.template", exchange);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "卡数据同步出错:" + e.getMessage());
        }
        Map resMap = (Map) exchange.getOut().getBody();
        dealOpenCardSubParams(exchangeSub, resMap);
        // 调bss卡处理结果提交接口
        try {
            lan.preData(pmp[1], exchangeSub);
            CallEngine.wsCall(exchangeSub, "ecaop.comm.conf.url.osn.services.SimSer");
            lan.xml2Json("ecaop.trades.srosSub.template", exchangeSub);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "卡结果处理提交出错:" + e.getMessage());
        }
        body = (Map) exchangeSub.getOut().getBody();
        Map ordersubInfo = (Map) body.get("ordersubInfo");
        Map retMap = new HashMap();
        if (null != ordersubInfo)
            retMap.put("taxNo", ordersubInfo.get("taxInvoiceno"));
        exchangeSub.getOut().setBody(retMap);
    }

    private void dealOpenCardSubParams(Exchange exchangeSub, Map resMap) {
        Map body = (Map) exchangeSub.getIn().getBody();
        Map msg = (Map) body.get("msg");
        List<Map> subOrderSubReq = new ArrayList<Map>();
        List<Map> payInfo = new ArrayList<Map>();
        Map resultInfo = (Map) resMap.get("resultInfo");
        List<Map> subOrdersubInfos = (List<Map>) resultInfo.get("subOrdersubInfo");
        Map subOrdersubInfo = subOrdersubInfos.get(0);
        payInfo.add(MapUtils.asMap("payType", "10"));
        subOrderSubReq.add(MapUtils.asMap("subOrderId", msg.get("ordersId"), "subProvinceOrderId",
                msg.get("provOrderId"), "feeInfo", dealFeeInfo(resMap)));
        msg.putAll(MapUtils.asMap("provinceOrderId", msg.get("provOrderId"), "orderId", msg.get("ordersId"),
                "city",
                msg.get("city"),
                "noteType", "1", "subOrderSubReq", subOrderSubReq, "totalFee", subOrdersubInfo.get("totalFee"),
                "payInfo",
                payInfo));
        body.put("msg", msg);
        exchangeSub.getIn().setBody(body);
    }

    /**
     * 处理费用信息
     * 
     * @param resMap
     * @return
     */
    private List dealFeeInfo(Map resMap) {
        List<Map> retFeeInfo = new ArrayList<Map>();
        Map resultInfo = (Map) resMap.get("resultInfo");
        List<Map> subOrdersubInfo = (List<Map>) resultInfo.get("subOrdersubInfo");
        if (null == subOrdersubInfo || subOrdersubInfo.size() == 0)
            return null;
        for (Map subOrdersubInfoo : subOrdersubInfo) {
            List<Map> feeInfo = (List<Map>) subOrdersubInfoo.get("preFeeInfo");
            if (null == feeInfo || feeInfo.size() == 0)
                return null;
            for (Map feeInfoo : feeInfo) {
                Map item = new HashMap();
                item.put("feeTypeMode", feeInfoo.get("feeTypeCode"));
                item.put("derateFee", feeInfoo.get("maxDerateFee"));
                item.put("fee", feeInfoo.get("oldfee"));
                if (null != feeInfoo.get("operateType")) {
                    item.put("isPay", "1");
                }
                retFeeInfo.add(item);
            }
        }
        return retFeeInfo;
    }

    /**
     * 卡数据同步参数
     * 
     * @param msg
     */
    private void dealOpenCardPreParams(Map msg) {
        List<Map> simCardNo = new ArrayList<Map>((List<Map>) msg.get("simCardNo"));
        if (simCardNo.size() == 0)
            return;
        for (Map simCardNoo : simCardNo) {
            simCardNoo.put("serviceClassCode", "0000");
            simCardNoo.put("city", msg.get("city"));
            simCardNoo.put("numId", msg.get("numId"));
            simCardNoo.put("subProvOrderId", msg.get("provOrderId"));
            simCardNoo.put("subOrderId", msg.get("ordersId"));
        }
        msg.remove("simCardNo");
        msg.put("subOrdersubInfo", simCardNo);
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }

    }
}
