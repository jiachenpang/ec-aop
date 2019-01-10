package com.ailk.ecaop.biz.cust;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.config.Config;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.sys.EcAopServerSysException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.FlowProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("CheckMaxFiveNumberTest")
public class CheckMaxFiveNumberTestProcessor extends BaseAopProcessor implements ParamsAppliable {
    /**
     * 客户资料校验接口中增加到大数据平台的接口调用，查询一证五户资料。
     * 身份证及户口本下帐号数
     * ecaop.trades.query.comm.cust.check及ecaop.trades.query.comm.cust.mcheck(客户资料校验-返回多个)调用本processor
     * */
    private static final String[] PARAM_ARRAY = { "ecaop.trades.cuck.CheckMaxFiveNumber.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void process(Exchange exchange) throws Exception {

        Map inBody = exchange.getIn().getBody(Map.class);
        Map msg = inBody.get("msg") instanceof String ? JSON.parseObject((String) inBody.get("msg")) : (Map) inBody
                .get("msg");

        String certType = (String) msg.get("certType");
        String customerName = (String) msg.get("certName");
        String certNum = (String) msg.get("certNum");

        boolean flag = false;//是否调一证五户开关 false为去，true为不去；
        //校验类型：0：证件校验 1：号码校验    号码校验时不调一证五户
        if ("1".equals((String) msg.get("checkType")))
        {
            flag = true;
            //资料有一个为空时，不调一证五户
        }
        else if (stringIsEmpty(customerName) || stringIsEmpty(certNum) || stringIsEmpty(certType))
        {
            flag = true;
            //证件为15位18位身份证及户口本，才调一证五户
        }
        else if (!"01|02|12".contains(certType))
        {
            flag = true;
        }

        //调客户资料校验原有流程
        try
        {
            FlowProcessor flow = new FlowProcessor();
            //客户资料校验-返回多个cmck
            if ("cmck".equals(exchange.getMethodCode()))
            {
                flow.applyParams(new String[] { "ecaop.core.method.cmck.flowConfig" });
            }
            //客户资料校验-cuck
            else
            {
                flow.applyParams(new String[] { "ecaop.core.method.cuck4mall.flowConfig" });
            }
            flow.process(exchange);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        //如果不调一证五户，直接返回
        if (flag)
            return;

        //以下一证五户流程

        /**
         * 下发的证件类型：
         * 属性值编码    属性值名称
        01  18位身份证
        02  护照
        05  军官证
        06  营业执照
        08  武警身份证
        09  户口本
        10  港澳居民来往内地通行证
        11  台湾居民来往大陆通行证
        12  15位身份证
        13  其他
        14  组织机构代码证
        15  事业单位法人证书
        16  介绍信
        17  测试号码证件
        18  社会团体法人登记证书
        19  照会
        20  小微企业客户证件
        21  民办非企业单位登记证书（包括法人、合伙、个体三种类型）
        22  统一社会信用代码证
         * */
        /**
        * AOP编码   说明
                01  15位身份证
                02  18位身份证
                03  驾驶证
                04  军官证
                05  教师证
                06  学生证
                07  营业执照
                08  护照
                09  武警身份证
                10  港澳居民来往内地通行证
                11  台湾居民来往大陆通行证
                12  户口本
                13  组织机构代码证
                14  事业单位法人证书
                15  介绍信
                16  测试号码证件
                17  社会团体法人登记证书
                18  照会
                21  民办非企业单位登记证书
                22  统一社会信用代码证书
                99  其他
                 * */

        Exchange tempExchange = ExchangeUtils.ofCopy(exchange, msg);

        //证件类型转换
        Map tempInbody = new HashMap(inBody);
        Map tempMsg = new HashMap(msg);
        //下发报文参数CHECK_TYPE 查询方式        0：业务办理校验        1：用户查询
        tempMsg.put("checkType", "1");
        tempMsg.put("customerName", customerName);
        tempMsg.put("certNum", certNum);
        tempMsg.put("certType", CertTypeChangeUtils.certTypeMall2Fbs(certType));
        tempInbody.put("msg", tempMsg);
        tempExchange.getIn().setBody(tempInbody);

        System.out.println("CheckMaxFiveNumber_178_msg:" + tempMsg);

        LanUtils lan = new LanUtils();
        lan.preData(pmp[0], tempExchange);
        Map result = new HashMap();
        boolean isError = false;
        try
        {
            CallEngine.wsCall(tempExchange, "ecaop.comm.conf.url.osn.services.OneCardFiveUserSer");//URL未知
            lan.xml2JsonNoError("ecaop.trades.cuck.CheckMaxFiveNumber.template", tempExchange);
        }
        catch (EcAopServerBizException e)
        {
            isError = true;
            result.put("ERROR", "调一证五户异常：code:[" + e.getCode() + "];detail：[" + e.getDetail() + "]");
            e.printStackTrace();
        }
        catch (EcAopServerSysException e)
        {
            isError = true;
            result.put("ERROR", "调一证五户异常：code:[" + e.getCode() + "];detail：[" + e.getDetail() + "]");
            e.printStackTrace();
        }
        //调全业务一证五户出错不处理
        catch (Exception e)
        {
            isError = true;
            result.put("ERROR", "调一证五户异常：code:[9999];detail：[" + e.getMessage() + "]");
            e.printStackTrace();
        }
        //处理返回
        dealReturn(tempExchange, exchange, result, isError);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void dealReturn(Exchange tempExchange, Exchange exchange, Map result, boolean isError) {
        Map outBody = exchange.getOut().getBody(Map.class);
        if (null == outBody || outBody.size() < 1)
        {
            outBody = JSON.parseObject(exchange.getOut().getBody(String.class));
        }
        if (!isError)
        {
            Map out = tempExchange.getOut().getBody(Map.class);
            String code = (String) out.get("code");
            //调一证五户报错处理
            if (!"0000".equals(code))
            {
                result.put("ERROR", "调一证五户返回：code:[" + code + "];detail：[" + out.get("detail") + "]");
            }
            else
            {
                List<Map> respInfo = (List) out.get("respInfo");
                String userAmount = "0";
                if (null != respInfo && respInfo.size() > 0)
                {
                    Map info = respInfo.get(0);
                    userAmount = String.valueOf(info.get("userAmount"));
                    List numberInfo = (List) info.get("numberInfo");
                    if (null != numberInfo && numberInfo.size() > 0)
                    {
                        result.put("numberInfo", numberInfo);
                    }
                    List dataInfo = (List) info.get("dataInfo");
                    if (null != dataInfo && dataInfo.size() > 0)
                    {
                        result.put("dataInfo", dataInfo);
                    }
                }
                result.put("userAmount", userAmount);
            }
        }
        outBody.putAll(result);
        exchange.getOut().setBody(outBody);
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++)
        {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

    private boolean stringIsEmpty(String str) {
        if (null == str || "".equals(str))
        {
            return true;
        }
        return false;
    }
}
