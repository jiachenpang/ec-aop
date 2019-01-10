package com.ailk.ecaop.biz.sub.gdjk;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

@EcRocTag("NumResQryParamsCheckProcessor")
public class NumResQryParamsCheckProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map<String, Object> body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        List<Map> queryParas = (List<Map>) msg.get("queryParas");
        if (null != queryParas) {
            Map<String, Integer> countMap = new HashMap<String, Integer>();

            for (Map paramMap : queryParas) {
                if ("01".equals(paramMap.get("queryType"))) {
                    if (null != countMap.get("01")) {
                        throw new EcAopServerBizException("0001", "条件非法！同一选号条件出现多次！");
                    }
                    countMap.put("01", 1);
                }
                else if ("02".equals(paramMap.get("queryType"))) {
                    if (null == paramMap.get("queryPara")) {
                        throw new EcAopServerBizException("0001", "条件非法!选号参数值为空。");
                    }
                    if (null != countMap.get("02")) {
                        throw new EcAopServerBizException("0001", "条件非法！同一选号条件出现多次！");
                    }
                    countMap.put("02", 1);
                }
                else if ("03".equals(paramMap.get("queryType"))) {
                    if (null == paramMap.get("queryPara")) {
                        throw new EcAopServerBizException("0001", "条件非法!选号参数值为空。");
                    }
                    if (null != countMap.get("03")) {
                        throw new EcAopServerBizException("0001", "条件非法！同一选号条件出现多次！");
                    }
                    countMap.put("03", 1);
                }
                else if ("04".equals(paramMap.get("queryType"))) {
                    if (null == paramMap.get("queryPara")) {
                        throw new EcAopServerBizException("0001", "条件非法!选号参数值为空。");
                    }
                    String queryPara = (String) paramMap.get("queryPara");
                    if (1 != queryPara.length() || !"0123456".contains(queryPara)) {
                        throw new EcAopServerBizException("0001", "条件非法！靓号等级参数错误！");
                    }
                    if (null != countMap.get("04")) {
                        throw new EcAopServerBizException("0001", "条件非法！同一选号条件出现多次！");
                    }
                    countMap.put("04", 1);
                }
            }
        }
    }
}
