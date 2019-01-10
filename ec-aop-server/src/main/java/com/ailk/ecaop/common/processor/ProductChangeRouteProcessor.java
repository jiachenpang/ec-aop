package com.ailk.ecaop.common.processor;

import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.request.EcAopRequestBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.utils.IsEmptyUtils;

/**
 * 产品变更提交接口路由类,根据opeSysType-办理业务系统和
 * @author Administrator
 */
@EcRocTag("productChangeRoute")
public class ProductChangeRouteProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        // 获取参数
        if (!("1".equals(String.valueOf(msg.get("opeSysType"))) || "2".equals(String.valueOf(msg.get("opeSysType"))))) {
            throw new EcAopRequestBizException("参数[办理业务系统]传值有误,请确认.opeSysType:" + msg.get("opeSysType"));
        }
        // 设置根据opeSysType跳转流程
        exchange.setProperty("opeSysType", msg.get("opeSysType"));
        List<Map> userInfos = (List<Map>) msg.get("userInfo");
        if (IsEmptyUtils.isEmpty(userInfos)) {
            throw new EcAopRequestBizException("未下发用户信息,无法办理业务");
        }
        Map userInfo = userInfos.get(0);
        String isTransystem = (String) userInfo.get("isTransystem");
        if ("0".equals(isTransystem)) {
            exchange.setProperty("isTransystem", "0");
        }
        else if ("1".equals(isTransystem)) {
            exchange.setProperty("isTransystem", "1");
        }
        else {
            throw new EcAopRequestBizException("未下发是否夸系统办理标识,请确认");
        }
    }
}
