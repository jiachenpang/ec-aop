package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;

/**
 * 融合业务--开户提交（写卡前置）
 * @author Lixl
 */
@EcRocTag("mixOrderSubCardData")
public class MixOrderSubCardDataProcessor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.trades.mpdsb.ParametersMapping",
            "ecaop.trades.mpdsb.okopen.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[2];

    @Override
    public void process(Exchange exchange) throws Exception {

        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Object provOrderId = msg.get("provOrderId");
        LanUtils lan = new LanUtils();
        msg.put("orderNo", provOrderId);
        msg.put("noteNo", msg.get("invoiceNo"));
        msg.put("sendTypeCode", "0");
        List<Map> subOrderInfo = (List<Map>) msg.get("subOrderInfo");
        List<Map> subOrderSubReq = new ArrayList<Map>();
        String[] copyStr = { "feeCategory", "feeId", "feeDes", "origFee", "reliefFee", "reliefResult", "realFee" };
        for (Map subOrder : subOrderInfo) {
            Map tempMap = new HashMap();
            tempMap.put("subProvinceOrderId", subOrder.get("subOrderId"));
            tempMap.put("subOrderId", subOrder.get("subOrderId"));
            List<Map> fee = (List<Map>) subOrder.get("feeInfo");
            int payMoney = 0;
            if (null != fee && 0 != fee.size()) {
                List<Map> fList = new ArrayList<Map>();
                for (Map f : fee) {
                    if (f.isEmpty()) {
                        continue;
                    }
                    if ("99".equals(f.get("feeId"))) {
                        f.put("feeId", "100000");
                    }
                    if ("4".equals(f.get("feeCategory"))) {
                        f.put("feeCategory", "2");
                    }
                    payMoney += Integer.parseInt(f.get("realFee").toString());
                    fList.add(dealFee(f, copyStr));
                }
                if (0 != fList.size()) {
                    tempMap.put("feeInfo", fList);
                }
            }
            tempMap.put("payMoney", payMoney);
            subOrderSubReq.add(tempMap);
        }
        Map payInfoMap = (Map) msg.get("payInfo");
        Object payType = ChangeCodeUtils.changePayType(payInfoMap.get("payType"));
        List<Map> payInfo = new ArrayList<Map>();
        for (Map sub : subOrderSubReq) {
            Map patInfoTemp = new HashMap();
            patInfoTemp.put("payMoney", sub.get("payMoney"));
            patInfoTemp.put("subProvinceOrderId", sub.get("subOrderId"));
            patInfoTemp.put("payType", payType);
            payInfo.add(patInfoTemp);
        }

        msg.put("payInfo", payInfo);
        msg.put("subOrderSubReq", subOrderSubReq);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        lan.preData(pmp[0], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
        lan.xml2Json("ecaop.trades.mpdsb.template", exchange);
        Map retMap = exchange.getOut().getBody(Map.class);
        retMap.put("provOrderId", provOrderId);

        // 调用资源占用接口
        Map orgMsg = exchange.getIn().getHeader("strParams", Map.class);
        Map msgTemp = new HashMap();
        String subOrderId = String.valueOf(subOrderInfo.get(0).get("subOrderId"));
        for (Object id : selectTradeIds(subOrderId)) {
            orgMsg.put("tradeId", String.valueOf(id));
            msgTemp.put("msg", orgMsg);
            exchange.getIn().setBody(msgTemp);
            lan.preData(pmp[1], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.AOPOneKeyOpenSer");
            lan.xml2Json("ecaop.trades.mpdsb.okopen.template", exchange);
        }

        exchange.getOut().setBody(retMap);
    }

    private Map dealFee(Map fee, String[] copyStr) {
        Map temp = new HashMap();
        MapUtils.arrayPut(temp, fee, copyStr);
        temp.put("calculateTag", "N");
        temp.put("payTag", "1");
        temp.put("calculateId", GetSeqUtil.getSeqFromCb());
        temp.put("calculateDate", GetDateUtils.getDate());
        return temp;
    }

    private List<String> selectTradeIds(String subOrderId) {
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        Map tradeParam = new HashMap();
        tradeParam.put("subOrderId", subOrderId);
        return dao.selectTradeIds(tradeParam);
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }

    }
}
