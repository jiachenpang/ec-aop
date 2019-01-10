package com.ailk.ecaop.biz.order;

import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.log.elk.ELKAopLogger;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.dao.essbusiness.EssBusinessDao;

/**
 * 用于判断开户正式提交是否走全业务
 * @author zhaok
 *
 */
@EcRocTag("CheckActivityProdchgSub")
public class CheckActivityProdchgSubProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        try {
            EssBusinessDao essDao = new EssBusinessDao();
            Map body = exchange.getIn().getBody(Map.class);
            Map msg = (Map) body.get("msg");

            List<String> data = essDao.selSysCodeByOrdersId(MapUtils.asMap("subscribeId", msg.get("provOrderId")));
            ELKAopLogger.logStr("tetst001sysCode的值" + data);
            ELKAopLogger.logStr("tetst005sysCode的值" + data.get(0));

            if (!IsEmptyUtils.isEmpty(data)) {
                String sysCode = data.get(0);
                if ("01".equals(sysCode)) {
                    exchange.setProperty("prodchgSubCode", sysCode);
                    ELKAopLogger.logStr("tetst002sysCode的值已经放入exchange" + sysCode);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
