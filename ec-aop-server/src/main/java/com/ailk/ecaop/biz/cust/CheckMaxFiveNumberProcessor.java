package com.ailk.ecaop.biz.cust;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.user.NaturalPersonCallprocessor;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.alibaba.fastjson.JSON;
import org.apache.commons.lang.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.sys.EcAopServerSysException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.FlowProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EcRocTag("CheckMaxFiveNumber")
public class CheckMaxFiveNumberProcessor extends BaseAopProcessor implements ParamsAppliable {
    /**
     * 客户资料校验接口中增加到大数据平台的接口调用，查询一证五户资料。
     * checkType不为1（仅0和1两个值）；证件类型、号码、姓名不为空；配置了开关的情况下，都会去查一证五户
     * ecaop.trades.query.comm.cust.check及ecaop.trades.query.comm.cust.mcheck(客户资料校验-返回多个)调用本processor
     * 在原有客户资料校验报错的情况下，也需要返回一证五户信息
     * checkType为0，证件类型、号码、姓名不为空，开关已配的情况下，没有返回一证五户信息时，为调一证五户报错
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
        // 调客户资料校验原有流程，在原有客户资料校验报错的情况下，也需要返回一证五户信息
        try {
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
        catch (EcAopServerSysException e) {
            exchange.getOut().setBody(MapUtils.asMap("code", e.getCode(), "detail", e.getDetail()));
            // 报错不抛出，并设置http返回码560，需为Integer类型
            exchange.setProperty("HTTP_STATUSCODE", 600);
            e.printStackTrace();
        }
        catch (EcAopServerBizException e1) {
            exchange.getOut().setBody(MapUtils.asMap("code", e1.getCode(), "detail", e1.getDetail()));
            // 报错不抛出，并设置http返回码560，需为Integer类型
            exchange.setProperty("HTTP_STATUSCODE", 560);
            e1.printStackTrace();
        }
        catch (Exception e2)
        {
            exchange.getOut().setBody(MapUtils.asMap("code", "9999", "detail", e2.getMessage()));
            // 报错不抛出，并设置http返回码560，需为Integer类型
            exchange.setProperty("HTTP_STATUSCODE", 560);
            e2.printStackTrace();
        }


        // 以下一证五户流程

        /**
         * 下发的证件类型：
         * 属性值编码 属性值名称
         * 01 18位身份证
         * 02 护照
         * 05 军官证
         * 06 营业执照
         * 08 武警身份证
         * 09 户口本
         * 10 港澳居民来往内地通行证
         * 11 台湾居民来往大陆通行证
         * 12 15位身份证
         * 13 其他
         * 14 组织机构代码证
         * 15 事业单位法人证书
         * 16 介绍信
         * 17 测试号码证件
         * 18 社会团体法人登记证书
         * 19 照会
         * 20 小微企业客户证件
         * 21 民办非企业单位登记证书（包括法人、合伙、个体三种类型）
         * 22 统一社会信用代码证
         */
        /**
         * AOP编码 说明
         * 01 15位身份证
         * 02 18位身份证
         * 03 驾驶证
         * 04 军官证
         * 05 教师证
         * 06 学生证
         * 07 营业执照
         * 08 护照
         * 09 武警身份证
         * 10 港澳居民来往内地通行证
         * 11 台湾居民来往大陆通行证
         * 12 户口本
         * 13 组织机构代码证
         * 14 事业单位法人证书
         * 15 介绍信
         * 16 测试号码证件
         * 17 社会团体法人登记证书
         * 18 照会
         * 21 民办非企业单位登记证书
         * 22 统一社会信用代码证书
         * 99 其他
         */


        Exchange tempExchange = ExchangeUtils.ofCopy(exchange, msg);

        //自然人改造需求--start
        new NaturalPersonCallprocessor().process(tempExchange);
        String userAmount = (String) tempExchange.getProperty("userAmount");
        if (StringUtils.isNotEmpty(userAmount) && !"00".equals(userAmount)) {
            Map outBody = exchange.getOut().getBody(Map.class);
            if (null == outBody || outBody.size() < 1) {
                outBody = JSON.parseObject(exchange.getOut().getBody(String.class));
            }
            outBody.put("userAmount", userAmount);
            exchange.getOut().setBody(outBody);
            return;
        }
        //自然人改造需求--end

        /**取值和调一证五户判断逻辑要在前面，因为后面走校验接口会对参数做变动*/
        boolean flag = false;//是否调一证五户开关 false为去，true为不去；
        //校验类型：0：证件校验 1：号码校验    号码校验时不调一证五户
        if ("1".equals((String) msg.get("checkType"))) {
            flag = true;
            // 资料有一个为空时，不调一证五户
        } else if (stringIsEmpty(customerName) || stringIsEmpty(certNum) || stringIsEmpty(certType)) {
            flag = true;
        }

        // AppCode校验开关
        // 客户资料校验-返回多个cmck
        if ("cmck".equals(exchange.getMethodCode())) {
            if (!EcAopConfigLoader.getStr("ecaop.global.param.cmck.config.appCode").contains(exchange.getAppCode()))
                flag = true;
        } else if (!EcAopConfigLoader.getStr("ecaop.global.param.cuck.config.appCode").contains(exchange.getAppCode())) {
            flag = true;
        }
        // 如果不调一证五户，直接返回
        if (flag)
            return;
        // 证件类型转换
        Map tempInbody = new HashMap(inBody);
        Map tempMsg = new HashMap(msg);
        //下发报文参数CHECK_TYPE 查询方式        0：业务办理校验        1：用户查询
        tempMsg.put("checkType", "1");
        tempMsg.put("customerName", customerName);
        tempMsg.put("certNum", certNum);
        tempMsg.put("certType", CertTypeChangeUtils.certTypeMall2Fbs(certType));
        tempInbody.put("msg", tempMsg);
        tempExchange.getIn().setBody(tempInbody);

        LanUtils lan = new LanUtils();
        lan.preData(pmp[0], tempExchange);
        try
        {
            CallEngine.wsCall(tempExchange, "ecaop.comm.conf.url.osn.services.OneCardFiveUserSer");//URL未知
            lan.xml2JsonNoError("ecaop.trades.cuck.CheckMaxFiveNumber.template", tempExchange);
        }
        //调全业务一证五户出错不处理
        catch (Exception e)
        {
            e.printStackTrace();
            return;
        }
        //处理返回
        dealReturn(tempExchange, exchange);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void dealReturn(Exchange tempExchange, Exchange exchange) {
        Map outBody = exchange.getOut().getBody(Map.class);
        if (null == outBody || outBody.size() < 1)
        {
            outBody = JSON.parseObject(exchange.getOut().getBody(String.class));
        }
        Map out = tempExchange.getOut().getBody(Map.class);
        if ("0000".equals((String) out.get("code")))
        {
            Map result = new HashMap();
            List<Map> respInfo = (List) out.get("respInfo");
            String userAmount = "0";
            if (null != respInfo && respInfo.size() > 0)
            {
                Map info = respInfo.get(0);
                userAmount = (String) info.get("userAmount");
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
            outBody.putAll(result);
            exchange.getOut().setBody(outBody);
        }
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
