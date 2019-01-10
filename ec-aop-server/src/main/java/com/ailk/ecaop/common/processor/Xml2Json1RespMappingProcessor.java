package com.ailk.ecaop.common.processor;

import java.net.URLDecoder;
import java.util.Map;

import org.n3r.ecaop.core.DefaultExchange;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.sys.EcAopServerSysException;
import org.n3r.ecaop.core.impl.DefaultMessage;

import com.ailk.ecaop.common.utils.DealInterfaceUtils;

public class Xml2Json1RespMappingProcessor extends BaseXml2JsonMapping {

    String[] params = { in };

    @Override
    public void process(Exchange exchange) throws Exception {
        Throwable ex = exchange.getException();
        if (ex != null) {
            ex.printStackTrace();
            throw new EcAopServerBizException("9999", "调用接口异常:" + ex.getMessage());
        }
        String body = exchange.getOut().getBody().toString();
        body = URLDecoder.decode(body);
        Map headMap = dealHeader(body);
        DealInterfaceUtils dif = new DealInterfaceUtils();
        String code = dif.dealTimeOutCode(headMap.get("headCode").toString());
        if (!"0000".equals(code) && !"9999".equals(code)) {
            throw new EcAopServerSysException(code, dif.dealRespDesc(headMap));
        }
        Map bodyMap = dealBody(body);
        code = bodyMap.get("code").toString();
        if (!"1".equals(code)) {
            if (null == bodyMap.get("detail")) {
                String actCode = bodyMap.get("actCode").toString();
                bodyMap.put("detail", dif.dealDetail(actCode, code));
            }

            throw new EcAopServerBizException("9999", dif.dealRespDesc(bodyMap));
        }
        dif.removeCodeDetail(bodyMap);
        exchange.getOut().setBody(bodyMap);
    }

    public static void main(String[] args) throws Exception {
        String body = "%3C%3Fxml+version%3D%221.0%22+encoding%3D%22UTF-8%22%3F%3E%0A%3CUniBSS%3E%3COrigDomain%3EULTE%3C%2FOrigDomain%3E%3CHomeDomain%3EUCRM%3C%2FHomeDomain%3E%3CBIPCode%3EBIP2G018%3C%2FBIPCode%3E%3CBIPVer%3E0100%3C%2FBIPVer%3E%3CActivityCode%3ET2000618%3C%2FActivityCode%3E%3CActionCode%3E1%3C%2FActionCode%3E%3CActionRelation%3E0%3C%2FActionRelation%3E%3CRouting%3E%3CRouteType%3E00%3C%2FRouteType%3E%3CRouteValue%3EA1%3C%2FRouteValue%3E%3C%2FRouting%3E%3CProcID%3E3168060605104175%3C%2FProcID%3E%3CTransIDO%3E3168060605104175%3C%2FTransIDO%3E%3CTransIDH%3E20180606051041%3C%2FTransIDH%3E%3CProcessTime%3E20180606051041%3C%2FProcessTime%3E%3CResponse%3E%3CRspType%3E0%3C%2FRspType%3E%3CRspCode%3E0000%3C%2FRspCode%3E%3CRspDesc%3E%E6%93%8D%E4%BD%9C%E6%88%90%E5%8A%9F%EF%BC%81%3C%2FRspDesc%3E%3C%2FResponse%3E%3CSPReserve%3E%3CTransIDC%3E201806060510410887351303408101%3C%2FTransIDC%3E%3CCutOffDay%3E20180606%3C%2FCutOffDay%3E%3COSNDUNS%3E9900%3C%2FOSNDUNS%3E%3CHSNDUNS%3EA100%3C%2FHSNDUNS%3E%3CConvID%3E316806060510417520180606051041351%3C%2FConvID%3E%3C%2FSPReserve%3E%3CTestFlag%3E0%3C%2FTestFlag%3E%3CMsgSender%3E9801%3C%2FMsgSender%3E%3CMsgReceiver%3E9801%3C%2FMsgReceiver%3E%3CSvcContVer%3E0100%3C%2FSvcContVer%3E%3CSvcCont%3E%3C%21%5BCDATA%5B%3C%3Fxml+version%3D%271.0%27+encoding%3D%27UTF-8%27%3F%3E%3CReturnSaleRsp%3E%3CReservStatus%3E1%3C%2FReservStatus%3E%3C%2FReturnSaleRsp%3E%5D%5D%3E%3C%2FSvcCont%3E%3C%2FUniBSS%3E";
        Exchange exchange = new DefaultExchange();
        Message out = new DefaultMessage();
        out.setBody(body);
        exchange.setOut(out);

        Xml2Json1RespMappingProcessor xml = new Xml2Json1RespMappingProcessor();
        xml.applyParams(new String[] { "ecaop.trades.sccc.cancel.crm.template" });
        xml.process(exchange);
    }
}
