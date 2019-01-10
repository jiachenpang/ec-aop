package com.ailk.ecaop.biz.res;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.log.elk.ELKAopLogger;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.dao.essbusiness.EssBusinessDao;
import com.alibaba.fastjson.JSON;

@EcRocTag("RecodeResourceInfo")
public class RecodeResourceInfoProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        long start = System.currentTimeMillis();
        Map head = (Map) exchange.getIn().getHeaders().get("strParams");
        Map msg = JSON.parseObject(head.get("msg").toString());
        if (!"2".equals(msg.get("opeSysType"))) {
            // TODO:允许南25省3G开户调用 by wangmc 20180601
            if ("1".equals(msg.get("opeSysType")) && "17|18|11|76|91|97".contains(String.valueOf(msg.get("province")))) {
                return;
            }
        }
        if (!"200".equals(String.valueOf(exchange.getProperty("HTTP_STATUSCODE")))) {
            return;
        }
        String method = exchange.getMethodCode();
        msg.put("methodCode", method);
        msg.put("sysCode", exchange.getAppCode());
        Object outRet = exchange.getOut().getBody();
        Map out = new HashMap();
        if (outRet instanceof String) {
            out = JSON.parseObject(outRet.toString());
        }
        else {
            out = (Map) outRet;
        }
        if ("opap|opnc|mnsa".contains(method)) {
            try {
                recodeResourceInfoByOpap(msg, out);
            }
            catch (Exception e) {
            }
        }
        ELKAopLogger.logStr("RecodeResourceInfo in:" + GetDateUtils.getDate() + ",cost:"
                + (System.currentTimeMillis() - start) + "ms,apptx:" + head.get("apptx"));
    }

    /**
     * 记录开户处理申请资源信息
     */
    private void recodeResourceInfoByOpap(Map msg, Map out) {
        Map inMap = new HashMap();
        inMap.put("province", msg.get("province"));
        inMap.put("subscribeId", out.get("provOrderId"));
        inMap.put("sysCode", msg.get("sysCode"));
        List<Map> numId = (List<Map>) msg.get("numId");
        if (!IsEmptyUtils.isEmpty(numId)) {
            inMap.put("serialNumber", numId.get(0).get("serialNumber"));
            inMap.put("proKey", numId.get(0).get("proKey"));
        }
        List<Map> userInfo = (List<Map>) msg.get("userInfo");
        List<Map> activityInfo = (List<Map>) userInfo.get(0).get("activityInfo");
        if (!IsEmptyUtils.isEmpty(activityInfo)) {
            inMap.put("terminalId", activityInfo.get(0).get("resourcesCode"));
            inMap.put("activityId", activityInfo.get(0).get("actPlanId"));// 活动id入库
        }
        inMap.put("discountFlag", "0"); // 不是折扣销售
        if (!IsEmptyUtils.isEmpty(msg.get("discountFee"))) {
            inMap.put("discountFlag", "1"); // 是折扣销售
        }
        inMap.put("methodCode", msg.get("methodCode"));
        new EssBusinessDao().insertResourceInfo(inMap);
    }
}
