package com.ailk.ecaop.common.utils;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import org.apache.axis.utils.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import java.util.List;
import java.util.Map;

/**
 * Created by Liu JiaDi on 2017/5/23.
 * 23转4开户提交会用到
 */
@EcRocTag("terminalSale")
public class TerminalSaleUtilsProcessor extends BaseAopProcessor implements ParamsAppliable {
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];
    private static final String[] PARAM_ARRAY = {"ecaop.masb.mold.sale.ParametersMapping"};

    //活动类型转换
    private String changeActiTypeCode(String activityType) {
        if (activityType.startsWith("YCMP")) {
            if (activityType.startsWith("YCMP003")) {
                activityType = "04";
            } else {
                activityType = "01";
            }
        }
        // 03合约购机 02 购手机送话费 01预存话费送手机
        else if (activityType.startsWith("GJRM")) {
            activityType = "02";
        } else if (activityType.startsWith("HYSJ") || activityType.startsWith("HYHJ") || activityType.startsWith("HYGJ")) {
            activityType = "03";
        } else {
            activityType = "";
        }
        return activityType;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        LanUtils lan = new LanUtils();
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        List<Map> tradeInfo = dao.query2324ResInfoByTradeId(msg);
        if (null != tradeInfo && tradeInfo.size() > 0) {
            String resourceCode = (String) tradeInfo.get(0).get("RESOURCE_CODE");
            if (StringUtils.isEmpty(resourceCode)) {
                return;
            }
            String activityType = (String) tradeInfo.get(0).get("ACTIVITY_TYPE");
            if (StringUtils.isEmpty(activityType)) {
                throw new EcAopServerBizException("9999", "根据订单获取活动信息失败，请检查输入的订单号");
            }
            String SERIAL_NUMBER = (String) tradeInfo.get(0).get("SERIAL_NUMBER");
            if (StringUtils.isEmpty(SERIAL_NUMBER)) {
                throw new EcAopServerBizException("9999", "根据订单获取号码信息失败，请检查输入的订单号");
            }
            msg.put("resourcesCode", resourceCode);
            msg.put("number", SERIAL_NUMBER);
            msg.put("activeType", changeActiTypeCode(activityType));
            if (!IsEmptyUtils.isEmpty(tradeInfo.get(0).get("SUBSCRIBE_ID"))) {
                msg.put("ordersId", tradeInfo.get(0).get("SUBSCRIBE_ID"));
            }
        } else {
            return;
        }

        msg.put("tradeType", "11");
        msg.put("operType", "06");
        try {
            lan.preData(pmp[0], exchange);
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.cbss");
            lan.xml2Json4ONS("ecaop.trades.mold.sale.template", exchange);
        } catch (Exception e) {
            throw new EcAopServerBizException("9999", e.getMessage());
        }
        body.put("msg", msg);
        exchange.getIn().setBody(body);
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[]{PARAM_ARRAY[i]});
        }

    }
}
