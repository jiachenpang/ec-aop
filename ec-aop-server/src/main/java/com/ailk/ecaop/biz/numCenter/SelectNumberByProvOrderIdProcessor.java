package com.ailk.ecaop.biz.numCenter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.dao.essbusiness.EssBusinessDao;
import com.alibaba.fastjson.JSON;

@EcRocTag("SelectNumberByProvOrderId")
public class SelectNumberByProvOrderIdProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        EssBusinessDao essDao = new EssBusinessDao();
        Map body = exchange.getIn().getBody(Map.class);
        // 增加号卡中心逻辑
        Map msg = null;
        if (body.get("msg") instanceof Map) {
            msg = (Map) body.get("msg");
        }
        else {
            msg = JSON.parseObject(body.get("msg").toString());
        }
        String provOrderId = (String) msg.get("provOrderId");
        Map inMap = new HashMap();
        inMap.put("subscribeId", provOrderId);
        List<String> numberList = essDao.qryNumberByProvOrderId(inMap);
        if (!IsEmptyUtils.isEmpty(numberList)) {
            msg.put("serialNumber", numberList.get(0));
        }
        body.put("msg", msg);
        exchange.getIn().setBody(body);
    }

}
