package com.ailk.ecaop.biz.acti;

import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.LanUtils;

/**
 * 用户可办理活动查询<br/>
 * TODO cy: #2017-01-09 下午15:08:23 基本功能已完成，没有配置appkey，没有下游地址，待测试中<br/>
 * #2017-03-09，配置下游地址
 *
 * @author: crane[yuanxingnepu@gmail.com]
 * @date: 2017-01-09 上午10:05:39
 */
@EcRocTag("QueryActivity")
public class QueryActivityProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.trade.qcua.ParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.QueryActivity");
        lan.xml2Json("ecaop.trade.qcua.template", exchange);

        Map rspMap = exchange.getOut().getBody(Map.class);
        if (null != rspMap.get("respInfo")) {
            List<Map> rspInfoList = (List<Map>) rspMap.get("respInfo");
            String code = (String) rspMap.get("code");
            if ("0000".equals(code)) {
                exchange.getOut().setBody(rspInfoList);
            }
            else if ("1204".equals(code)) {
                throw new EcAopServerBizException("9999", "号码不存在");
            }
            else if ("8888".equals(code)) {
                throw new EcAopServerBizException("9999", "其他错误");
            }
        }
        else {
            throw new EcAopServerBizException("9999", "返回应答信息为空");
        }
    }

}
