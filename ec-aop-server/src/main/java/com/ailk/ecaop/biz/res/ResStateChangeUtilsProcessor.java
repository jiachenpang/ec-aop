package com.ailk.ecaop.biz.res;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.DealResOccupyAndRlsUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.dao.essbusiness.EssBusinessDao;
import com.alibaba.fastjson.JSON;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.common.utils.IsEmptyUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 该类用于处理终端改造存在 全部是配置文件需要添加新零售分支的原子能力
 */
@EcRocTag("resStateChangeUtilsProcessor")
public class ResStateChangeUtilsProcessor extends BaseAopProcessor implements ParamsAppliable {
    private static final String[] PARAM_ARRAY = {"ecaop.param.mapping.seft", "ecaop.bsschg.checkres.ParametersMapping",
            "ecaop.trades.oata.ParametersMapping", "ecaop.masb.mold.ParametersMapping",
    "ecaop.trades.orta.ParametersMapping","ecaop.masb.modr.ParametersMapping"};
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[6];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        String methodCode = exchange.getMethodCode();
        boolean isString = body.get("msg") instanceof String;
        Map msg = isString ? JSON.parseObject((String) body.get("msg")) : (Map) body.get("msg");
        LanUtils lan = new LanUtils();
        //是否割接到新零售中心 是为ture
        boolean newResCenterOpen = EcAopConfigLoader.getStr("ecaop.global.param.resources.aop.province").contains((String) msg.get("province"));
        //终端销售信息校验
        if ("seft".equals(methodCode)) {
            if (newResCenterOpen) {
                ResourceStateChangeUtils resourceStateChangeUtils = new ResourceStateChangeUtils();
                resourceStateChangeUtils.saleTerminfoCheck(exchange);
            } else {
                lan.preData(pmp[0], exchange);
                CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.cbss");
                lan.xml2Json4ONS("ecaop.template.sale.terminfo", exchange);
            }
        } else if ("qrtb".equals(methodCode)) {
            //终端状态查询变更-BSS用
            if (newResCenterOpen) {
                ResourceStateChangeUtils resourceStateChangeUtils = new ResourceStateChangeUtils();
                List<Map> resourceInfo = (List<Map>) msg.get("resourcesInfo");
                if ("1".equals(resourceInfo.get(0).get("isSelf"))) {
                    //自备机逻辑
                    resourceStateChangeUtils.getSelfResourceInfo(exchange);
                } else {
                    String occupiedFlag = (String) resourceInfo.get(0).get("occupiedFlag");
                    if ("0,1".contains(occupiedFlag)) {
                        resourceStateChangeUtils.getResourceInfo(exchange);
                    } else if ("2".equals(occupiedFlag)) {
                        //终端销售
                        String tradeId = (String) resourceInfo.get(0).get("tradeId");
                        msg.put("ordersId", tradeId);
                        msg.put("resourcesCode", resourceInfo.get(0).get("resourcesCode"));
                        msg.put("number", resourceInfo.get(0).get("number"));
                        msg.put("activeType", resourceInfo.get(0).get("activeType"));
                        msg.put("tradeType", resourceInfo.get(0).get("tradeType"));
                        msg.put("resourcesType", resourceInfo.get(0).get("resourcesType"));
                        body.put("msg", msg);
                        exchange.getIn().setBody(body);
                        resourceStateChangeUtils.realOccupiedResourceInfo(exchange);
                    } else if ("3".equals(occupiedFlag)) {
                        //终端释放
                        resourceStateChangeUtils.releaseResourceInfo(exchange);
                    } else if ("4".equals(occupiedFlag)) {
                        //终端故障改空闲
                        throw new EcAopServerBizException("9999", "新资源中心不支持故障改空闲操作");
                    } else if ("5".equals(occupiedFlag)) {
                        // 终端换机,对应资源中心操作类型是03
                        if (IsEmptyUtils.isEmpty(resourceInfo.get(0).get("oldResourcesCode"))) {
                            throw new EcAopServerBizException("9999", "换机操作时,旧终端串号必填");
                        }
                        String tradeId = (String) resourceInfo.get(0).get("tradeId");
                        msg.put("ordersId", tradeId);
                        msg.put("resourcesCode", resourceInfo.get(0).get("resourcesCode"));
                        msg.put("oldResourcesCode", resourceInfo.get(0).get("oldResourcesCode"));
                        msg.put("checkId", resourceInfo.get(0).get("checkId"));
                        msg.put("number", resourceInfo.get(0).get("number"));
                        msg.put("RES_OPER_TYPE_FLAG", "03");
                        body.put("msg", msg);
                        exchange.getIn().setBody(body);
                        resourceStateChangeUtils.realOccupiedResourceInfo(exchange);
                    } else if ("6".equals(occupiedFlag)) {
                        // 终端退机,对应资源中心操作类型是02
                        msg.put("RES_OPER_TYPE_FLAG", "02");
                        String tradeId = (String) resourceInfo.get(0).get("tradeId");
                        msg.put("ordersId", tradeId);
                        msg.put("resourcesCode", resourceInfo.get(0).get("resourcesCode"));
                        body.put("msg", msg);
                        exchange.getIn().setBody(body);
                        resourceStateChangeUtils.realOccupiedResourceInfo(exchange);
                    }
                }
            } else {
                lan.preData(pmp[1], exchange);
                CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
                lan.xml2Json4Res("ecaop.gdjk.checkres.template", exchange);
            }
        } else if ("oata".equals(methodCode)) {
            //老用户优惠购机申请南25逻辑
            if (newResCenterOpen) {
                ResourceStateChangeUtils resourceStateChangeUtils = new ResourceStateChangeUtils();
                msg.put("resCenterSwitch", "1");
                msg.putAll(resourceStateChangeUtils.checkRcToProAct(exchange));
            }
            body.put("msg", msg);
            exchange.getIn().setBody(body);
            lan.preData(pmp[2], exchange);
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
            lan.xml2Json4ONS("ecaop.trades.oata.template", exchange);
            Map out = exchange.getOut().getBody(Map.class);
            if (newResCenterOpen) {
                Map inMap = new HashMap();
                inMap.put("province", msg.get("province"));
                inMap.put("subscribeId", out.get("essSubscribeId"));
                inMap.put("sysCode", exchange.getAppCode());
                inMap.put("serialNumber", msg.get("serialNumber"));
                List<Map> activityInfo = (List<Map>) msg.get("activityInfo");
                if (!IsEmptyUtils.isEmpty(activityInfo)) {
                    inMap.put("terminalId", activityInfo.get(0).get("resourcesCode"));
                    inMap.put("activityId", activityInfo.get(0).get("actPlanId"));// 活动id入库
                }
                inMap.put("methodCode", "oata");
                try {
                    new EssBusinessDao().insertResourceInfo(inMap);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new EcAopServerBizException("9999", "记录终端信息失败:" + e.getMessage());
                }

            }
        } else if ("mold".equals(methodCode)) {
            //老用户优惠购机提交南25逻辑
            lan.preData(pmp[3], exchange);
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
            lan.xml2Json4ONS("ecaop.masb.mold.template", exchange);
            Map Out = exchange.getOut().getBody(Map.class);
            if (newResCenterOpen) {
                try {
                    Map sqlMap = MapUtils.asMap("provOrderId", msg.get("essSubscribeId"), "province", msg.get("province"));
                    List<Map> preSubMitDataList = new EssBusinessDao().qryResourceInfo4RollBack(sqlMap);
                    if (IsEmptyUtils.isEmpty(preSubMitDataList)) {
                        throw new EcAopServerBizException("9999", "查询不到已经开户提交的订单");
                    }
                    // 获取开户的终端串码
                    //msg.put("ordersId", msg.get("essSubscribeId"));
                    msg.put("resourcesCode", preSubMitDataList.get(0).get("TERMINAL_ID"));
                    msg.put("number", preSubMitDataList.get(0).get("SERIAL_NUMBER"));
                    msg.put("activeType", "03");
                    body.put("msg", msg);
                    exchange.getIn().setBody(body);
                    ResourceStateChangeUtils resourceStateChangeUtils = new ResourceStateChangeUtils();
                    resourceStateChangeUtils.realOccupiedResourceInfo(exchange);

                } catch (Exception e) {
                    e.printStackTrace();
                    throw new EcAopServerBizException("9999", "终端销售失败:" + e.getMessage());
                }
            }
            exchange.getOut().setBody(Out);
        } else if ("orta".equals(methodCode)) {
            //老用户续约申请南25逻辑
            if (newResCenterOpen) {
                ResourceStateChangeUtils resourceStateChangeUtils = new ResourceStateChangeUtils();
                msg.put("resCenterSwitch", "1");
                msg.putAll(resourceStateChangeUtils.checkRcToProAct(exchange));
            }
            System.out.println(body.get("apptx") + "msg的内容:" + msg);
            body.put("msg", msg);
            exchange.getIn().setBody(body);
            lan.preData(pmp[4], exchange);
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
            lan.xml2Json4ONS("ecaop.trades.orta.template", exchange);
            Map out = exchange.getOut().getBody(Map.class);
            if (newResCenterOpen) {
                Map inMap = new HashMap();
                inMap.put("province", msg.get("province"));
                inMap.put("subscribeId", out.get("essSubscribeId"));
                inMap.put("sysCode", exchange.getAppCode());
                inMap.put("serialNumber", msg.get("serialNumber"));
                List<Map> activityInfo = (List<Map>) msg.get("activityInfo");
                if (!IsEmptyUtils.isEmpty(activityInfo)) {
                    inMap.put("terminalId", activityInfo.get(0).get("resourcesCode"));
                    inMap.put("activityId", activityInfo.get(0).get("actPlanId"));// 活动id入库
                }
                inMap.put("methodCode", "oata");
                try {
                    new EssBusinessDao().insertResourceInfo(inMap);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new EcAopServerBizException("9999", "记录终端信息失败:" + e.getMessage());
                }

            }
        } else if ("mold".equals(methodCode)) {
            //老用户续约提交南25逻辑
            lan.preData(pmp[5], exchange);
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
            lan.xml2Json4ONS("ecaop.masb.modr.template", exchange);
            Map Out = exchange.getOut().getBody(Map.class);
            if (newResCenterOpen) {
                try {
                    Map sqlMap = MapUtils.asMap("provOrderId", msg.get("essSubscribeId"), "province", msg.get("province"));
                    List<Map> preSubMitDataList = new EssBusinessDao().qryResourceInfo4RollBack(sqlMap);
                    if (IsEmptyUtils.isEmpty(preSubMitDataList)) {
                        throw new EcAopServerBizException("9999", "查询不到已经开户提交的订单");
                    }
                    // 获取开户的终端串码
                    //msg.put("ordersId", msg.get("essSubscribeId"));
                    msg.put("resourcesCode", preSubMitDataList.get(0).get("TERMINAL_ID"));
                    msg.put("number", preSubMitDataList.get(0).get("SERIAL_NUMBER"));
                    msg.put("activeType", "03");
                    body.put("msg", msg);
                    exchange.getIn().setBody(body);
                    ResourceStateChangeUtils resourceStateChangeUtils = new ResourceStateChangeUtils();
                    resourceStateChangeUtils.realOccupiedResourceInfo(exchange);

                } catch (Exception e) {
                    e.printStackTrace();
                    throw new EcAopServerBizException("9999", "终端销售失败:" + e.getMessage());
                }
            }
            exchange.getOut().setBody(Out);
        }

    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[]{PARAM_ARRAY[i]});
        }
    }
}
