package com.ailk.ecaop.biz.sim;

import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.alibaba.fastjson.JSON;

/**
 * 通过全业务接口调用号卡中心,实占卡,其中ProcID要使用传入的procId,该值为写卡数据查询时返回,号卡中心需要保持一致
 * @author wangmc
 * @date 2017-11-21
 */
@EcRocTag("callNotifyResultProcessor")
public class CallNotifyResultProcessor extends BaseAopProcessor implements ParamsAppliable {

    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[2];
    // cbss写卡数据查询,全业务实占卡接口
    private static final String[] PARAM_ARRAY = { "ecaop.masb.smosa.qryRemoteCardInfo.ParametersMapping",
            "ecaop.trades.smcc.ess.notify.paramtersmapping" };
    private LanUtils lan = new LanUtils();
    private Logger log = LoggerFactory.getLogger(CallNotifyResultProcessor.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        Object apptx = "";
        try {
            // 之前业务均成功,才需要调该接口
            if (!"200".equals(String.valueOf(exchange.getProperty("HTTP_STATUSCODE")))) {
                return;
            }

            // 获取外围传进来的请求参数
            Map headers = exchange.getIn().getHeader("strParams", Map.class);
            Map msg = JSON.parseObject(String.valueOf(headers.get("msg")));
            apptx = headers.get("apptx");
            // cBSS系统调用的补换卡业务不做卡占用接口的调用,因为cBSS会自己调
            if ("cbsb".equals(exchange.getAppCode()) && "smcc|smoss".equals(exchange.getMethodCode())) {
                return;
            }

            // 南25的3G业务不需要调这个接口,在3GE代码中有处理
            if (!"2".equals(msg.get("opeSysType")) && !"18|17|76|11|97|91".contains((String) msg.get("province"))) {
                return;
            }

            // 分步补换卡业务时,需要调用cb查询写卡数据接口获取写卡数据查询时调用号卡的ProcId
            if ("smoss".equals(exchange.getMethodCode())) {
                String iccid = (String) msg.get("iccid");
                msg.put("emptyCardId", !IsEmptyUtils.isEmpty(iccid) && iccid.length() > 19 ? iccid.substring(0, 19)
                        : iccid);
                Exchange qryExchange = ExchangeUtils.ofCopy(exchange, msg);
                lan.preData(pmp[0], qryExchange);
                CallEngine.wsCall(qryExchange, "ecaop.comm.conf.url.cbss.services.SimForNorthSer");
                lan.xml2Json("ecaop.trades.smosa.qryRemoteCardInfo.template", qryExchange);
                Map retMap = qryExchange.getOut().getBody(Map.class);
                Map remoteCardInfo = (Map) retMap.get("remoteCardInfo");
                msg.put("imsi", remoteCardInfo.get("imsi"));
                // 调用610接口使用的ProcId需要与写卡数据查询接口里使用的一样,该字段为写卡数据查询时获取到的
                msg.put("procId", remoteCardInfo.get("rsrvStr1"));
            }
            // 使用复制的exchange调用下游,调用成功后,aop接口依然返回之前操作的返回内容
            Exchange submitExchange = ExchangeUtils.ofCopy(exchange, msg);
            lan.preData(pmp[1], submitExchange);
            CallEngine.aopCall(submitExchange, "ecaop.comm.conf.url.osn.syncreceive.9900");// 调用9900接口,路由值改为A1
            lan.xml2JsonNoBody("ecaop.trades.smcc.ess.notify.template", submitExchange);
        }
        catch (Exception e) {
            e.printStackTrace();
            log.info(apptx + ",仅输出:调用全业务T2000610接口出错:" + e.getMessage());
        }
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
