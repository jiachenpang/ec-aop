package com.ailk.ecaop.biz.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.LanUtils;

@EcRocTag("checkUserTransferFixedAll")
public class CheckUserTransferFixedAllProcessor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.params.23l4.ParametersMapping" };

    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[4];

    @Override
    public void process(Exchange exchange) throws Exception {

        LanUtils lan = new LanUtils();
        // 数据备份
        Map bodyOrg = (Map) exchange.getIn().getBody();
        Map msgOrg = (Map) bodyOrg.get("msg");
        Exchange exchangebac = ExchangeUtils.ofCopy(exchange, msgOrg);
        // 调用BSS

        try {
            lan.preData(pmp[0], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.UserTransferSer");
            lan.xml2Json("ecaop.trades.cbss.23To4.template", exchange);
            Map body = exchange.getOut().getBody(Map.class);

            String respCode = (String) body.get("code");
            List respInfos = new ArrayList();
            if ("0000".equals(respCode))
            {
                List<Map> custInfos = (List) body.get("custInfo");
                if (custInfos != null && custInfos.size() != 0)
                {
                    Map custInfo = custInfos.get(0);
                    String certTypeCode = (String) custInfo.get("certTypeCode");
                    List<Map> requestInfos = (List) body.get("requestInfo");

                    // 若certTypecode不等于07.13.14.15.17.18时，不需要调集客，直接返回
                    if (!("07,13,14,15,17,18,21".contains(certTypeCode))) {
                        for (Map respInfo : requestInfos) {
                            Map m = new HashMap();
                            m.put("respCode", respInfo.get("requestCode"));
                            m.put("respDesc", respInfo.get("requestDesc"));
                            respInfos.add(m);
                        }
                        body.remove("requestInfo");
                        body.put("responseInfo", respInfos);
                        exchange.getOut().setBody(body);
                        return;
                    }
                }
            }
            else
            {
                List<Map> requestInfos = (List) body.get("requestInfo");
                for (Map respInfo : requestInfos) {
                    Map m = new HashMap();
                    m.put("respCode", respInfo.get("requestCode"));
                    m.put("respDesc", respInfo.get("requestDesc"));
                    respInfos.add(m);
                }
                body.remove("requestInfo");
                body.put("responseInfo", respInfos);
                exchange.getOut().setBody(body);
                return;
            }

        }
        catch (EcAopServerBizException e) {
            throw new EcAopServerBizException("9999", e.getMessage());
        }
        try {
            Map body = exchange.getOut().getBody(Map.class);
            List<Map> userInfos = (List) body.get("userInfo");
            Map userInfo = userInfos.get(0);
            String userId = (String) userInfo.get("userId");

            // 因为userid不是必返，若user为空，抛异常
            if (userId != null) {
                Map bodyIn = exchangebac.getIn().getBody((Map.class));
                Map msg = (Map) bodyIn.get("msg");
                // 准备userId,放入in，然后调用集客
                msg.put("provinceUserId", userId);
                msg.put("eparchyCode", ChangeCodeUtils.changeEparchy(msg));
                msg.put("serialNumber", msg.get("numId"));
                msg.put("optType", "2");
                bodyIn.put("msg", msg);
                exchange.getIn().setBody(bodyIn);
                lan.preData("ecaop.trade.cbss.CheckUserTransferParametersMapping", exchange);
                CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.userChgCheck4GSer");
                lan.xml2Json("ecaop.trades.cbss.gpct.template", exchange);
                // 若code=0000或0001 表示该用户可以进行23G转4G
                String code = (String) ((Map) exchange.getOut().getBody()).get("code");
                if ("0000".equals(code) || "0001".equals(code)) {
                    List<Map> responseInfo = (List) body.get("requestInfo");
                    List<Map> responseInfos = new ArrayList<Map>();
                    for (Map responseIn : responseInfo) {
                        Map m = new HashMap();
                        m.put("respCode", responseIn.get("requestCode"));
                        m.put("respDesc", responseIn.get("requestDesc"));
                        responseInfos.add(m);
                    }
                    Map requestInfos = new HashMap();
                    requestInfos.put("responseInfo", responseInfos);
                    exchange.getOut().setBody(requestInfos);

                }
            }

        }
        catch (EcAopServerBizException e) {
            throw new EcAopServerBizException("9999", e.getMessage());
        }

    }

    @Override
    public void applyParams(String[] params) {
        // TODO Auto-generated method stub
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
}
