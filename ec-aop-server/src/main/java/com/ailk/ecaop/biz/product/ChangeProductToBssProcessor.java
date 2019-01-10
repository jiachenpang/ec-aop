package com.ailk.ecaop.biz.product;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.LanUtils;

/**
 * 产品变更提交接口2、3G互转流程,直接通过劝业调用身份BSS
 * @author wangmc 2017-08-14
 */
@EcRocTag("changeProductToBssProcessor")
public class ChangeProductToBssProcessor extends BaseAopProcessor implements ParamsAppliable {

    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];
    private static final String[] PARAM_ARRAY = { "ecaop.ecsb.mcps.ParametersMapping" };

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Object apptx = body.get("apptx");
        System.out.println(apptx + ",产品变更流程---> 调用省份流程--------");

        List<Map> userInfos = (List<Map>) msg.get("userInfo");
        Map userInfo = userInfos.get(0);

        // 拼装调用省份的参数
        List<Map> subproductInfo = new ArrayList<Map>();
        Map productInfo = (Map) userInfo.get("productInfo");
        List<Map> packageInfos = (List<Map>) productInfo.get("packageInfo");
        for (int i = 0; i < packageInfos.size(); i++) {
            List<Map> elementInfos = (List<Map>) packageInfos.get(i).get("elementInfo");
            Map productitem = new HashMap();
            List<Map> feeInfo = new ArrayList<Map>();
            for (int j = 0; j < elementInfos.size(); j++) {
                Map item = new HashMap();
                item.put("startDate", elementInfos.get(j).get("startDate"));
                item.put("feeCode", elementInfos.get(j).get("elementCode"));
                item.put("feeName", elementInfos.get(j).get("elementName"));
                item.put("endDate", elementInfos.get(j).get("endDate"));
                feeInfo.add(item);
            }
            productitem.put("feeInfo", feeInfo);
            productitem.put("startDate", packageInfos.get(i).get("startDate"));
            productitem.put("packageType", packageInfos.get(i).get("packageType"));
            productitem.put("endDate", packageInfos.get(i).get("endDate"));
            productitem.put("packageId", packageInfos.get(i).get("packageId"));
            productitem.put("packageName", packageInfos.get(i).get("packageId"));
            subproductInfo.add(productitem);
        }
        msg.put("subproductInfo", subproductInfo);
        msg.put("priproductId", productInfo.get("priproductId"));
        msg.put("tarproductId", productInfo.get("tarproductId"));
        msg.put("orderId", msg.get("ordersId"));
        msg.put("endsystemId", "0" + msg.get("opeSysType"));
        msg.put("transresourcesType", userInfo.get("transresourcesType"));

        body.put("msg", msg);
        exchange.getIn().setBody(body);
        // 调用省份接口
        LanUtils lan = new LanUtils();
        lan.preData(pmp[0], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.productchgser");
        lan.xml2Json("ecaop.trads.mcps.template", exchange);
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
