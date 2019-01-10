package com.ailk.ecaop.common.processor;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

/**
 * @author cuij
 * @version 创建时间：2017-3-14 下午4:20:08
 *          程序的简单说明
 */
@EcRocTag("GateToNewAndOld234oProduct")
public class GateToNewAndOld234oProduct extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        if ((EcAopConfigLoader.getStr("ecaop.global.param.product.23t4.appcode") + "").contains(exchange
                .getAppCode()))
        {
            exchange.setProperty("proGate", "2");
        }
        else {
            exchange.setProperty("proGate", "1");
        }

    }
}
