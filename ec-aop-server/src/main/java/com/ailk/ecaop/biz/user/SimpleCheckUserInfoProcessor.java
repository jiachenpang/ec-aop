package com.ailk.ecaop.biz.user;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.esql.Esql;

import java.util.List;
import java.util.Map;

@EcRocTag("simpleCheckUserInfo")
public class SimpleCheckUserInfoProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
        if (isCallSimpleCheckUser(msg)) {
            List<Map> resourcesInfo = (List<Map>) msg.get("resourcesInfo");
            LanUtils lan = new LanUtils();
            for (Map res : resourcesInfo) {
                msg.put("ResourcesCode", res.get("resourcesCode"));
                String methodCode = exchange.getMethodCode();
                if ("0".equals(res.get("occupiedFlag")) && "nboc".equals(methodCode)) {
                    continue;
                }
                if ("0|1|4".contains(res.get("occupiedFlag").toString()) && "qcsc".equals(methodCode)) {
                    continue;
                }
                Exchange tempExhcange = ExchangeUtils.ofCopy(exchange, msg);
                lan.preData("ecaop.trades.simpleCheckUserInfo.ParametersMapping", tempExhcange);
                CallEngine.wsCall(tempExhcange, "ecaop.comm.conf.url.osn.services.usrser");
                lan.xml2JsonNoError("ecaop.trades.simpleCheckUserInfo.template", tempExhcange);
                Map retMap = tempExhcange.getOut().getBody(Map.class);
                String code = retMap.get("code").toString();
                if ("0000".equals(code)) {

                    if (retMap.get("USER_INFO") == null) {
                        throw new EcAopServerBizException("9999", "用户简单查询接口没有返回客户信息");
                    }
                    Map userInfo = (Map) retMap.get("USER_INFO"); // 已存在的用户信息
                    String openDate = (String) userInfo.get("OpenDate"); // 入网时间

                    String errMsg = "您选择的号码" + msg.get("ResourcesCode") + "已经于" + openDate.substring(0, 4)
                            + "年" + openDate.substring(4, 6) + "月" + openDate.substring(6, 8) + "日被占用，请重新选择!";
                    throw new EcAopServerBizException("9999", errMsg);
                }
                if (!"1204".equals(code)) {
                    throw new EcAopServerBizException(code, (String) retMap.get("detail"));
                }
            }
        }
    }

    private boolean isCallSimpleCheckUser(Map msg) {
        List<Map> resourcesInfo = (List<Map>) msg.get("resourcesInfo");
        if (IsEmptyUtils.isEmpty(resourcesInfo)) {
            return false;
        }
        List<String> result = new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/aop/common.esql").id("qrySimpleCheckUserInfoTag").params(msg).execute();

        if (IsEmptyUtils.isEmpty(result)) {
            return false;
        }
        return "1".equals(result.get(0)) ? true : false;
    }
}
