package com.ailk.ecaop.common.processor;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

/**
 * 判断套餐变更走新老流程的逻辑
 * proGate 为1时,走原有逻辑,即北六老代码
 * 为2时,走新逻辑,即aopserver新代码
 *
 * @author Administrator
 */
@EcRocTag("GateToNewAndOldProductDatabase")
public class GateToNewAndOldProductDatabase extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        String useOldApp = EcAopConfigLoader.getStr("ecaop.global.param.product.database.appcode");
        if ("ZZZZ".equals(useOldApp) || useOldApp.contains(exchange.getAppCode())) {
            exchange.setProperty("proGate", "1");
        }
        else {
            exchange.setProperty("proGate", "2");
        }
    }
}
