package com.ailk.ecaop.biz.sub.gdjk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.AopCall;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.cust.CertCheckTypeProcessor;
import com.ailk.ecaop.biz.cust.FormatDateAndDealException4Gzt;
import com.ailk.ecaop.biz.user.CheckUserInfoProcessor;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.processor.Xml2Json4FbsNoErrorMappingProcessor;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.alibaba.fastjson.JSON;

/**
 * 23转4转出校验
 * 
 * @author GaoLei
 */
@EcRocTag("TransTo4GCheck")
public class TransTo4GCheckProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        // 23转4校验
        Map body = exchange.getIn().getBody(Map.class);
        Map tempbody = new HashMap(body);
        // 检验客户是23G用户还是4G用户
        Map checkRsp = check4GCustByNum(exchange, body);
        if (checkRsp.size() > 0) {
            exchange.setProperty(Exchange.HTTP_STATUSCODE, 200);
            exchange.getOut().setBody(checkRsp);
        }
        checkRsp = new HashMap();
        // 调全业务接口到各省分做转出校验
        Map tempbody1 = new HashMap(tempbody);// 为调下一个接口备份参数
        exchange.getIn().setBody(tempbody);
        // 转出校验，无报错
        Map result = check23GTransfer(exchange, false);
        exchange.getIn().setBody(tempbody1);
        dealReurnInfo(exchange, result, checkRsp);
    }

    public Map check23GTransfer(Exchange exchange, boolean needException) throws Exception {
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.23To4.check.ParametersMapping", exchange);
        // 直连全业务
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.UserTransferSer");
        if (!needException) {// 不抛异常
            Xml2Json4FbsNoErrorMappingProcessor proc = new Xml2Json4FbsNoErrorMappingProcessor();
            proc.applyParams(new String[] { "ecaop.23To4.check.template" });
            proc.process(exchange);
        }
        else {
            lan.xml2Json("ecaop.23To4.check.template", exchange);
        }
        return exchange.getOut().getBody(Map.class);
    }

    /**
     * 检验客户是否为4G客户，如果是则直接返回客户的活动信息
     * 
     * @param exchange
     * @param serialNumber 号码
     * @param province 省分
     * @return
     * @throws Exception
     */
    private Map check4GCustByNum(Exchange exchange, Map body) throws Exception {
        String methodCode = exchange.getMethodCode();
        Map checkRsp = new HashMap();
        Map msg = JSON.parseObject((String) body.get("msg"));
        msg.put("serialNumber", msg.get("numId"));
        msg.put("infoList", "CUST|USER|ACCT");
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        exchange.setMethodCode("qctc");
        CheckUserInfoProcessor checkUser = new CheckUserInfoProcessor();
        checkUser.process(exchange);
        Map result = exchange.getOut().getBody(Map.class);
        String sysType = (String) result.get("sysType");
        if (sysType.equals(MagicNumber.SYS_TYPE_ESS)) {// 23G用户
            return checkRsp;
        }
        // 4G用户
        Map userInfo = (Map) result.get("userInfo");
        Map custInfo = (Map) result.get("custInfo");
        List<Map> actInfoList = new ArrayList<Map>();
        checkRsp.put("certNum", custInfo.get("certCode"));
        checkRsp.put("certType", custInfo.get("certTypeCode"));
        checkRsp.put("certAdress", custInfo.get("certAddr"));
        checkRsp.put("customerName", custInfo.get("custName"));
        List<Map> productInfo = (List<Map>) userInfo.get("productInfo");
        if (!CollectionUtils.isEmpty(productInfo)) {
            for (int i = 0; i < productInfo.size(); i++) {
                Map pro = productInfo.get(i);
                if ("50".equals(pro.get("productMode"))) {
                    Map actInfo = new HashMap();
                    actInfo.put("productId", userInfo.get("productId"));
                    actInfo.put("activityId", pro.get("productId"));
                    actInfo.put("productName", pro.get("productName"));
                    actInfo.put("startTime", pro.get("productActiveTime"));
                    actInfo.put("endTime", pro.get("productInactiveTime"));
                    actInfoList.add(actInfo);
                }
            }
            checkRsp.put("activityInfo", actInfoList);
        }
        Map certInfo = new HashMap();
        certInfo.put("customerId", custInfo.get("custId"));
        certInfo.put("customerName", custInfo.get("custName"));
        certInfo.put("customerAddr", custInfo.get("certAddr"));
        checkRsp.put("certInfo", certInfo);

        // 将原methodCode塞回来
        exchange.setMethodCode(methodCode);
        return checkRsp;
    }

    /**
     * 处理返回信息
     * 
     * @param exchange
     * @param result
     * @param checkRsp
     * @throws Exception
     */
    private void dealReurnInfo(Exchange exchange, Map result, Map checkRsp) throws Exception {
        int count = 0;
        Map custInfo = (Map) result.get("custInfo");
        // 除非号码不存在，否则必返证件信息
        if (null != custInfo && custInfo.size() > 0 && "1204".equals(result.get("code"))) {
            checkRsp.put("certNum", custInfo.get("certCode"));
            checkRsp.put("certType", CertTypeChangeUtils.certTypeFbs2Mall((String) custInfo.get("certTypeCode")));
            checkRsp.put("certAddress", custInfo.get("certAddr"));
            checkRsp.put("customerName", custInfo.get("custName"));
            checkRsp.put("contactPerson", custInfo.get("contact"));
            checkRsp.put("contactName", custInfo.get("contactPhone"));
            checkRsp.put("contactAddress", custInfo.get("postAddress"));
        }
        // 23转4校验返回的ResponseInfo
        List<Map> requestInfoList = (List<Map>) result.get("requestInfo");
        List<Map> responseInfoList = new ArrayList<Map>();
        if (!CollectionUtils.isEmpty(requestInfoList)) {
            boolean flag = false;
            for (int i = 0; i < requestInfoList.size(); i++) {
                Map requestInfo = requestInfoList.get(i);
                Map responseInfo = new HashMap();
                responseInfo.put("respCode", requestInfo.get("requestCode"));
                responseInfo.put("respDesc", requestInfo.get("requestDesc"));
                responseInfoList.add(responseInfo);
                if ("1204|1203".contains((String) requestInfo.get("requestCode"))) {
                    flag = true;
                }
                count++;
            }
            checkRsp.put("responseInfo", responseInfoList);
            if (flag) {
                return;
            }
        }
        Map userInfo = (Map) result.get("userInfo");
        // 调接口之前先备份参数
        Map tempbody = new HashMap(exchange.getIn().getBody(Map.class));
        Map tempbody1 = new HashMap(tempbody);

        if (null != userInfo && userInfo.size() > 0) {
            count++;
            if (CollectionUtils.isEmpty(requestInfoList)) {
                // 如果是集团客户，不允许办理融合业务
                if ("1".equals(custInfo.get("custType")) || "01".equals(custInfo.get("custType"))) {
                    // checkGroupTransfer(exchange, userInfo);
                    // TODO 集团客户，不允许办理融合业务
                }
                // 如果证件类型为身份证，就进行国政通实名制验证
                if ("01|12".contains((String) custInfo.get("certTypeCode"))
                        && !"3|4".contains((String) custInfo.get("idMark"))) {
                    // 国政通校验 USE TEMPBODY
                    Map msg = (Map) tempbody.get("msg");
                    msg.put("certId", custInfo.get("certCode"));
                    msg.put("certName", custInfo.get("custName"));
                    msg.put("certType", CertTypeChangeUtils.certTypeFbs2Mall((String) custInfo.get("certTypeCode")));
                    exchange.getIn().setBody(tempbody);
                    certCheck(exchange);
                }

                // 运行至此，说明前面的校验全部通过，记录号码和相关信息
                Map param = new HashMap();
                Map msg = (Map) tempbody1.get("msg");
                String num = (String) msg.get("serialNumber");
                String serCode = (String) (null == msg.get("serviceClassCode") ? "000" : msg.get("serviceClassCode"));
                param.put("serialNumber", num);
                param.put("netTypeCode", serCode);
                param.put("opeId", msg.get("operatorId"));
                param.put("provinceCode", "00" + (String) msg.get("province"));
                LanOpenApp4GDao dao = new LanOpenApp4GDao();
                dao.insertOperInfo(param);
            }

            Map superNumInfo = (Map) userInfo.get("superNumber");// 靓号信息
            List<Map> contractInfoList = (List<Map>) userInfo.get("contractInfo");// 合约信息
            List<Map> activityInfoList = new ArrayList<Map>();// 活动信息
            if (!CollectionUtils.isEmpty(contractInfoList)) {
                for (int i = 0; i < contractInfoList.size(); i++) {
                    Map contract = contractInfoList.get(i);
                    Map act = new HashMap();
                    act.put("productFee", userInfo.get("comboFee"));
                    act.put("productName", userInfo.get("comboName"));
                    act.put("activityId", contract.get("productId"));
                    act.put("activityType", contract.get("productType"));
                    act.put("startTime", contract.get("startTime"));
                    act.put("endTime", contract.get("endTime"));
                    activityInfoList.add(act);
                }
                checkRsp.put("activityInfo", activityInfoList);

                // 有靓号信息时，合约套餐费与靓号最低消费值比较，返回较大为月最低消费金额，单位是分
                result.put("minConsume", userInfo.get("comboFee"));
                if (null != superNumInfo && superNumInfo.size() > 0) {
                    int supMonthMin = Integer.parseInt((String) superNumInfo.get("monthMinConsume"));
                    if (supMonthMin > Integer.parseInt((String) userInfo.get("comboFee"))) {
                        checkRsp.put("minConsume", superNumInfo.get("monthMinConsume"));
                    }
                }
            }
            else {// 无合约信息
                Map actInfo = new HashMap();
                actInfo.put("productFee", userInfo.get("comboFee"));
                actInfo.put("productName", userInfo.get("comboName"));
                activityInfoList.add(actInfo);
                checkRsp.put("activityInfo", activityInfoList);

                // 没有合约，有靓号信息，返回靓号月最低消费值
                if (null != superNumInfo && superNumInfo.size() > 0) {
                    checkRsp.put("minConsume", superNumInfo.get("monthMinConsume"));
                }
            }

            // 证件信息,CBSS没有这个cust_id,CertInfo就不传
            Map certInfo = new HashMap();
            String cerType = (String) custInfo.get("certTypeCode");
            String certCode = (String) custInfo.get("certCode");
            Map msg = (Map) tempbody1.get("msg");
            msg.put("certTypeCode", cerType);
            msg.put("certCode", certCode);
            msg.put("operateType", "1");

            // FIXME 根据证件去CBSS查客户资料 USE　TEMPBODY1
            exchange.getIn().setBody(tempbody1);
            Map outBody = TradeManagerUtils.custCheck4G(exchange);
            String code = (String) outBody.get("code");
            if ("0000".equals(code)) {
                Map cust = (Map) outBody.get("custInfo");
                certInfo.put("customerId", cust.get("custId"));
                certInfo.put("customerName", custInfo.get("custName"));
                certInfo.put("customerAddr", custInfo.get("certAddr"));
                checkRsp.put("certInfo", certInfo);
            }
        }
        if (!"0000".equals(result.get("code")) && count == 0) {
            throw new EcAopServerBizException("9999", (String) result.get("detail"));
        }

    }

    /**
     * 集团客户转出校验
     * 
     * @param exchange
     * @param userInfo
     * @throws Exception
     */
    private void checkGroupTransfer(Exchange exchange, Map userInfo) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        msg.put("eparchyCode", ChangeCodeUtils.changeEparchy(msg));
        msg.put("provinceCode", msg.get("province"));
        msg.put("provinceUserId", userInfo.get("userId"));
        msg.put("optType", "2");
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.23To4.grpcheck.ParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.UserChgCheck4GSer");
        lan.xml2Json("ecaop.23To4.grpcheck.template", exchange);
    }

    /**
     * 国政通校验
     * 
     * @param exchange
     * @param body
     * @throws Exception
     */
    private void certCheck(Exchange exchange) throws Exception {
        String methodCode = exchange.getMethodCode();
        exchange.setMethodCode("crck");
        CertCheckTypeProcessor chkProc = new CertCheckTypeProcessor();
        chkProc.process(exchange);
        AopCall call = new AopCall();
        LanUtils lan = new LanUtils();
        lan.preData("method.crck.params.mapping", exchange);
        call.applyParams(new String[] { "ecaop.comm.conf.url.phw-eop", "60"
        });
        call.process(exchange);
        FormatDateAndDealException4Gzt deal = new FormatDateAndDealException4Gzt();
        deal.process(exchange);
        // 恢复原来的methodCode
        exchange.setMethodCode(methodCode);
        // JSONPathMappingProcessor jsonProc = new JSONPathMappingProcessor();
        // jsonProc.applyParams(new String[] { "ecaop.crck.params.json.mapping"
        // });
        // jsonProc.process(exchange);
        // RemoveEmptyProcessor revProc = new RemoveEmptyProcessor();
        // revProc.process(exchange);
    }
}
