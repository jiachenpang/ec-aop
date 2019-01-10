package com.ailk.ecaop.biz.res;

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
import com.ailk.ecaop.biz.product.DealNewCbssProduct;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.dao.essbusiness.EssBusinessDao;
import com.alibaba.fastjson.JSON;

/**
 * 北六3G正式提交之后使用的终端实占接口
 * @author wangmc
 */
@EcRocTag("TermSaleProcessor")
public class TermSaleProcessor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.masb.opsb.sale.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map head = (Map) exchange.getIn().getHeaders().get("strParams");
        Map msg = JSON.parseObject(head.get("msg").toString());
        Object routecode = exchange.getProperty("routecode");
        // 北六3G正式提交成功,才调用终端实占接口
        if ("2".equals(msg.get("opeSysType")) || "N25".equals(routecode)) {
            return;
        }
        if (!"200".equals(String.valueOf(exchange.getProperty("HTTP_STATUSCODE")))) {
            return;
        }
        // 准备参数
        if (preTermSaleParam(msg)) {
            return;
        }
        Map tempBody = (Map) exchange.getIn().getBody();
        tempBody.put("msg", msg);
        exchange.getIn().setBody(tempBody);
        LanUtils lan = new LanUtils();
        // 终端实占
        try {
            lan.preData(pmp[0], exchange);
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.n6res");
            lan.xml2Json4ONS("ecaop.trades.chph.sale.template", exchange);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", e.getMessage());
        }
    }

    /**
     * 终端实占参数准备
     * @param msg
     */
    private boolean preTermSaleParam(Map msg) {
        // 从库里查终端串码
        Map inMap = new HashMap();
        inMap.put("provOrderId", msg.get("provOrderId"));
        inMap.put("province", msg.get("province"));
        List<Map> resourceInfoList = new EssBusinessDao().qryResourceInfo4RollBack(inMap);
        if (IsEmptyUtils.isEmpty(resourceInfoList)) {
            return true;
        }
        Object resourceCode = resourceInfoList.get(0).get("TERMINAL_ID");
        Object number = resourceInfoList.get(0).get("SERIAL_NUMBER");
        Object activityId = resourceInfoList.get(0).get("ACTIVITY_ID");
        Map tempMap = new HashMap();
        tempMap.put("PRODUCT_ID", activityId);
        // 活动id查询活动类型
        List<Map> activityTypeList = new DealNewCbssProduct().queryProductAndPtypeProduct(tempMap);
        if (IsEmptyUtils.isEmpty(activityTypeList)) {
            throw new EcAopServerBizException("9999", "根据活动ID未获取到活动类型! activityId:" + activityId);
        }
        msg.put("activeType", changeActiTypeCode((String) activityTypeList.get(0).get("PRODUCT_TYPE_CODE")));
        msg.put("resourcesCode", resourceCode);
        msg.put("number", number);
        msg.put("tradeType", "30");
        msg.put("operType", "01");
        return false;
    }

    /**
     * 活动类型转换
     * @param activityType
     * @return
     */
    private String changeActiTypeCode(String activityType) {
        if (activityType.startsWith("YCMP")) {
            if (activityType.startsWith("YCMP003")) {
                activityType = "04";
            }
            else {
                activityType = "01";
            }
        }
        // 03合约购机 02 购手机送话费
        else if (activityType.startsWith("GJRM") || activityType.startsWith("ZBJM")) {
            activityType = "02";
        }
        else if (activityType.startsWith("HYSJ") || activityType.startsWith("HYHJ") || activityType.startsWith("HYGJ")
                || activityType.startsWith("ZBJA") || activityType.startsWith("ZBJB")
                || activityType.startsWith("ZBJC")) {
            activityType = "03";
        }
        return activityType;
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
}
