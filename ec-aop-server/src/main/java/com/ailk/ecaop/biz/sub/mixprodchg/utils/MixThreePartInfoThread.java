package com.ailk.ecaop.biz.sub.mixprodchg.utils;

import java.util.Map;
import java.util.concurrent.Callable;

import org.n3r.ecaop.core.exception.EcAopServerException;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.sys.EcAopServerSysException;

import com.ailk.ecaop.biz.sub.mixprodchg.entry.MixProductChgSuperUser;

public class MixThreePartInfoThread implements Callable {

    private MixProductChgSuperUser user;

    public MixThreePartInfoThread(MixProductChgSuperUser user) {
        this.user = user;
    }

    @Override
    public Map call() throws Exception {

        try {
            return user.callThreePart();
        }
        catch (EcAopServerBizException e) {
            throw new EcAopServerBizException(e.getCode(), "号码:" + user.getSerialNumber() + "获取三户返回报错." + e.getDetail());
        }
        catch (EcAopServerSysException e) {
            throw new EcAopServerSysException(e.getCode(), "号码:" + user.getSerialNumber() + "获取三户返回报错." + e.getDetail());
        }
        catch (Exception e) {
            throw new EcAopServerException("号码:" + user.getSerialNumber() + "获取三户返回报错." + e.getMessage());
        }
    }

}
