package com.ailk.ecaop.biz.sub.mixprodchg.utils;

import java.util.Map;
import java.util.concurrent.Callable;

import org.n3r.ecaop.core.exception.EcAopServerException;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.sys.EcAopServerSysException;

import com.ailk.ecaop.biz.sub.mixprodchg.entry.MixProductChgSuperUser;

/**
 * 融合套餐变更的线程任务类
 * @author wangmc
 * @date 2018-08-06
 */
public class MixProductChgThread implements Callable {

    private MixProductChgSuperUser user;

    public MixProductChgThread(MixProductChgSuperUser user) {
        this.user = user;
    }

    @Override
    public Map call() {
        try {
            return user.flowControl();
        }
        catch (EcAopServerBizException e) {
            throw new EcAopServerBizException(e.getCode(), "号码:" + user.getSerialNumber() + "处理报错." + e.getDetail());
        }
        catch (EcAopServerSysException e) {
            throw new EcAopServerSysException(e.getCode(), "号码:" + user.getSerialNumber() + "处理报错." + e.getDetail());
        }
        catch (Exception e) {
            throw new EcAopServerException("号码:" + user.getSerialNumber() + "处理报错." + e.getMessage());
        }
    }
}
