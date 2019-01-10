package com.ailk.ecaop.biz.fee;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.dao.essbusiness.EssBusinessDao;
import com.alibaba.fastjson.JSON;

/**
 * 该类用于校验折扣销售时,外围传入的费用项中是否含有100009类型
 * @author wangmc
 */
@EcRocTag("discountSaleCheckProcessor")
public class DiscountSaleCheckProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = null;
        if (body.get("msg") instanceof String) {
            msg = JSON.parseObject(body.get("msg") + "");
        }
        else {
            msg = (Map) body.get("msg");
        }
        Map paraMap = new HashMap();
        paraMap.put("subscribeId", msg.get("provOrderId"));
        paraMap.put("province", msg.get("province"));
        paraMap.put("methodCode", "opap,mvoa,smnp"); // 仅移网单开,融合新装,主副卡开户需求,已在sql中写死
        List<String> discountInfo = new EssBusinessDao().qryDiscountFlagByProvOrderId(paraMap);
        if (IsEmptyUtils.isEmpty(discountInfo) || "0".equals(discountInfo.get(0))) {
            return;
        }
        if (!(msg.get("feeInfo") instanceof List)) {
            throw new EcAopServerBizException("9999", "AOP接口提示:请求参数feeInfo节点类型传递错误,请确认");
        }
        List<Map> feeInfo = (List<Map>) msg.get("feeInfo");
        boolean flag = true;
        for (int i = 0; i < feeInfo.size(); i++) {
            Map fee = feeInfo.get(i);
            if ("100009".equals(fee.get("feeId"))) {
                flag = false;
                break;
            }
        }
        if (flag) {
            throw new EcAopServerBizException("9999", "折扣销售需要下发100009的费用项!");
        }
    }
}
