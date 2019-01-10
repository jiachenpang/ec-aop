package com.ailk.ecaop.biz.user;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

@EcRocTag("ContractInfoProcessor")
public class ContractInfoProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        setRspMessage(exchange);
    }

    private void setRspMessage(Exchange exchange) throws Exception {
        Map<String, Object> body = exchange.getOut().getBody(Map.class);
        Map userInfo = (Map) body.get("userInfo");
        if (null != userInfo) {
            List<Map> activityInfo = (List<Map>) userInfo.get("activityInfo");
            // 有活动信息
            if (null != activityInfo) {
                for (Map m : activityInfo) {
                    String activityTotalTime = calculateTotalTime((String) m.get("activityActiveTime"),
                            (String) m.get("activityInactiveTime"));
                    m.put("activityTotalTime", activityTotalTime);
                }
                List<Map> para = (List<Map>) body.get("para");
                Map outMap = new HashMap();
                outMap.put("activityInfo", activityInfo);
                outMap.put("para", para);
                exchange.getOut().setBody(outMap);
            }
            // 无活动信息
            else {
                throw new EcAopServerBizException("0001", "无活动信息");
            }
        }
        else {
            throw new EcAopServerBizException("0001", "无活动信息");
        }
    }

    private String calculateTotalTime(String t1, String t2) throws Exception {
        Date date1 = new SimpleDateFormat("yyyyMMddHHmmss").parse(t1);
        Date date2 = new SimpleDateFormat("yyyyMMddHHmmss").parse(t2);
        int result = RDate.diffMonths(date2, date1);
        return result + "";
    }

}
