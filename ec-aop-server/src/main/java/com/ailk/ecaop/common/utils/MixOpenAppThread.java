package com.ailk.ecaop.common.utils;

import java.util.Map;
import java.util.concurrent.Callable;

import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.EcAopServerException;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.sys.EcAopServerSysException;

import com.ailk.ecaop.base.CallEngine;

public class MixOpenAppThread implements Callable {

    private final Exchange exchange;
    private final Object serialNumber;

    public MixOpenAppThread(Exchange exchange, Object serialNumber) {
        this.exchange = exchange;
        this.serialNumber = serialNumber;
    }

    @Override
    public Map call() {
        try {
            new LanUtils().preData("ecaop.trades.sccc.cancelPre.paramtersmapping", exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
            new LanUtils().xml2Json("ecaop.trades.sccc.cancelPre.template", exchange);
            return exchange.getOut().getBody(Map.class);
        }
        catch (EcAopServerBizException e) {
            throw new EcAopServerBizException(e.getCode(), "号码:" + serialNumber + "预提交报错." + e.getDetail());
        }
        catch (EcAopServerSysException e) {
            throw new EcAopServerSysException(e.getCode(), "号码:" + serialNumber + "预提报错." + e.getDetail());
        }
        catch (Exception e) {
            throw new EcAopServerException("号码:" + serialNumber + "预提交报错." + e.getMessage());
        }
    }
}
