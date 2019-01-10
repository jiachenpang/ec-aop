/**
 * 针对CBSS卡数据同步失败场景
 * 返销终端以及号码信息
 */
package com.ailk.ecaop.biz.res;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.log.domain.EcAopReqLog;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;
import org.n3r.ecaop.core.processor.TransReqParamsMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.dao.essbusiness.EssBusinessDao;
import com.alibaba.fastjson.JSON;

@EcRocTag("cancelCbssResources")
public class CancelCbssResourcesProcessor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.gdjk.numcheck.ParametersMapping",
            "ecaop.trades.T4000003.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[PARAM_ARRAY.length];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map head = (Map) exchange.getIn().getHeaders().get("strParams");
        Map msg = JSON.parseObject(head.get("msg").toString());
        if (!"2".equals(msg.get("opeSysType"))) {
            return;
        }
        if ("200".equals(String.valueOf(exchange.getProperty("HTTP_STATUSCODE")))) {
            return;
        }
        try {
            List<Map> dataBaseResourcesInfo = new EssBusinessDao().qryResourceInfo4RollBack(msg);
            if (1 != dataBaseResourcesInfo.size()) {
                throw new EcAopServerBizException("9999", "获取订单信息失败");
            }
            Map resourceMap = dataBaseResourcesInfo.get(0);
            Object serialNumber = resourceMap.get("SERIAL_NUMBER");
            if (!serialNumber.equals(msg.get("numId"))) {
                throw new EcAopServerBizException("9999", "号码传递有误,预期:" + serialNumber + "实际:" + msg.get("serialNumber"));
            }
            Map serialReleaseMap = new HashMap();
            MapUtils.arrayPut(serialReleaseMap, msg, MagicNumber.COPYARRAY);
            Map resource = new HashMap();
            resource.put("proKeyMode", "0");// 0 随机码
            resource.put("proKey", resourceMap.get("PRO_KEY"));
            resource.put("resourcesType", "02");// 后付费资源
            resource.put("resourcesCode", msg.get("numId"));
            String province = msg.get("province").toString();
            boolean isN6 = "11|17|18|76|91".contains(province);
            resource.put("occupiedFlag", isN6 ? "5" : "4");// 释放资源
            resource.put("snChangeTag", "0");// 0 不变更号码
            List<Map> resourcesInfo = new ArrayList<Map>();
            resourcesInfo.add(resource);
            serialReleaseMap.put("resourcesInfo", resourcesInfo);
            LanUtils lan = new LanUtils();
            if (isN6) {
                Exchange serialNumberExchange = ExchangeUtils.ofCopy(exchange, serialReleaseMap);
                serialNumberExchange.setMethodCode("qcsc");// 号码状态变更标准版
                EcAopReqLog reqLog = (EcAopReqLog) exchange.getProperty(Exchange.REQLOG);
                reqLog.setMethodCode("qcsc");
                serialNumberExchange.setProperty(Exchange.REQLOG, reqLog);
                serialNumberExchange.setMethod("ecaop.trades.query.comm.snres.chg");
                new TransReqParamsMappingProcessor().process(serialNumberExchange);
                CallEngine.aopCall(serialNumberExchange, "ecaop.comm.conf.url.ec-aop.rest");
            }
            // 北六总部管控号码，也要到3GE释放
            Exchange serialNumberExchange = ExchangeUtils.ofCopy(exchange, serialReleaseMap);
            lan.preData(pmp[0], serialNumberExchange);
            CallEngine.aopCall(serialNumberExchange, "ecaop.comm.conf.url.esshttp.newsub");
            lan.xml2Json4Res("ecaop.gdjk.numcheck.template", serialNumberExchange);

            // 参数拼装,准备终端释放
            if (!IsEmptyUtils.isEmpty(resourceMap.get("TERMINAL_ID"))) {
                Map terminalMap = new HashMap();
                MapUtils.arrayPut(terminalMap, msg, MagicNumber.COPYARRAY);
                terminalMap.put("operType", "03");// 03对预占终端和已售终端都会释放
                terminalMap.put("resourcesType", "07");// 类型为其他,ESS不校验,会统一处理
                terminalMap.put("resourcesCode", resourceMap.get("RESOURCE_CODE"));
                terminalMap.put("subscribeID", "8" + msg.get("provOrderId"));// 在原有订单上添加8,生成新订单
                terminalMap.put("oldSubscribeID", msg.get("provOrderId"));
                Exchange terminalExchange = ExchangeUtils.ofCopy(exchange, terminalMap);
                lan.preData(pmp[1], terminalExchange);
                CallEngine.aopCall(terminalExchange, "ecaop.comm.conf.url.esshttp.cbss");
                lan.xml2Json4ONS("ecaop.trades.T4000003.template", terminalExchange);
            }// 终端释放结束
        }
        catch (Exception e) {
        }
        return;
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
