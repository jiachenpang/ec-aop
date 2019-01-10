package com.ailk.ecaop.common.utils;

import java.util.Map;

import org.n3r.ecaop.core.Exchange;

public interface YearPayEcsCheck {

    // 用于宽带包年的校验
    Map yearPayEcsCheck(Exchange exchange, Map msg) throws Exception;

    // 用户宽带包年校验的提交
    Map yearPayEcsSub(Exchange exchange, Map preSubmit, Map msg) throws Exception;
}
