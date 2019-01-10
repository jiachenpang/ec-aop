package com.ailk.ecaop.biz.sim;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.log.domain.EcAopReqLog;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

/**
 * 此processor目前只针对白卡自动调拨接口，北六调拨走老接口
 * ecaop.trades.sell.mob.comm.carddate.qry=cdqy
 */
@EcRocTag("n6ChangeMethodProcessor")
public class N6ChangeMethodProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        if ("cdaq".equals(exchange.getMethodCode())) {
            EcAopReqLog reqLog = (EcAopReqLog) exchange.getProperty(Exchange.REQLOG);
            reqLog.setMethodCode("cdqy");
            exchange.setMethod("ecaop.trades.sell.mob.comm.carddate.qry");
        }

    }

}
