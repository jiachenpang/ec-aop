package com.ailk.ecaop.biz.res;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.base.IReleaseRes;
import com.ailk.ecaop.common.utils.LanUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("terminalRelease")
public class ReleaseTermialProcessor extends BaseAopProcessor implements IReleaseRes {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg;
        if (body.get("msg") instanceof String) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        String methodCode = exchange.getMethodCode();
        List<Map> resourcesInfo = preParams(methodCode, msg);
        if (0 == resourcesInfo.size()) {
            return;
        }
        msg.put("resourcesInfo", resourcesInfo);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.trades.checkres.ParametersMapping", exchange);
        CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
        lan.xml2Json4ONS("ecaop.trades.checkres.template", exchange);
        Map retMap = exchange.getOut().getBody(Map.class);
        dealReturn(retMap);
    }

    /**
     * 准备开户处理提交接口数据
     * 
     * @param inMap
     * @return
     */
    private List<Map> preParams4Opsb(Map inMap) {
        List<Map> paraList = (List<Map>) inMap.get("para");
        Map resourcesMap;
        List<Map> resourcesInfo = new ArrayList<Map>();
        if (paraList == null || 0 == paraList.size()) {
            return resourcesInfo;
        }
        for (Map para : paraList) {
            if ("resourcesCode".equals(para.get("paraId")) && !"".equals(para.get("paraValue"))) {
                resourcesMap = new HashMap();
                resourcesMap.put("resourcesCode", para.get("paraValue"));
                resourcesMap.put("resourcesType", "01");
                resourcesMap.put("resCodeType", "01");
                resourcesMap.put("occupiedFlag", "3");
                resourcesInfo.add(resourcesMap);
            }
        }
        return resourcesInfo;
    }

    /**
     * 准备开户处理申请接口数据
     * 
     * @param map
     * @return
     */
    private List<Map> preParams4Opnc(Map map) {
        List<Map> resourcesInfo = new ArrayList<Map>();
        List<Map> userInfo = (List<Map>) map.get("userInfo");
        for (Map userMap : userInfo) {
            List<Map> activityInfo = (List<Map>) userMap.get("activityInfo");
            if (null != activityInfo && 0 != activityInfo.size()) {
                for (Map activityMap : activityInfo) {
                    String resourcesType = (String) activityMap.get("resourcesType");
                    String resourcesCode = (String) activityMap.get("resourcesCode");
                    Map resourcesInfoTemp = new HashMap();
                    resourcesInfoTemp.put("resourcesType", dealResourceType(resourcesType));
                    resourcesInfoTemp.put("resourcesCode", resourcesCode);
                    resourcesInfoTemp.put("occupiedFlag", "3");
                    resourcesInfo.add(resourcesInfoTemp);
                }
            }
        }
        return resourcesInfo;
    }

    /**
     * 判断资源释放接口返回是否成功
     * 
     * @param inPutMap
     */
    @Override
    public void dealReturn(Map inPutMap) {
        Object code = inPutMap.get("code");
        if (null == code || "0000".equals(code.toString())) {
            return;
        }
        if ("0001".equals(code.toString())) {
            ArrayList<Map> resourcesRsp = (ArrayList<Map>) inPutMap.get("resourcesRsp");
            for (Map resource : resourcesRsp) {
                String rscStateCode = resource.get("rscStateCode").toString();
                if (!"0000".equals(rscStateCode) || !"0003".equals(rscStateCode)) {
                    throw new EcAopServerBizException(rscStateCode, "[销售资源状态变更接口]返回:" + resource.get("rscStateDesc"));
                }
            }
        }
        else {
            throw new EcAopServerBizException(code.toString(), "[销售资源状态变更接口]返回:" + inPutMap.get("detail"));
        }
    }

    /**
     * 资源类型转码
     * 
     * @param value
     * @return
     */
    @Override
    public String dealResourceType(String resType) {
        if ("03".equals(resType)) {
            return "01";
        }
        if ("04".equals(resType)) {
            return "02";
        }
        return "03";
    }

    /**
     * 准备接口下发数据
     */
    private List<Map> preParams(String methodCode, Map inMap) {
        List<Map> resourcesInfo = new ArrayList<Map>();
        if ("opsb".equals(methodCode)) {
            resourcesInfo = preParams4Opsb(inMap);
        }
        else if ("opnc".equals(methodCode)) {
            resourcesInfo = preParams4Opnc(inMap);
        }
        return resourcesInfo;
    }
}
