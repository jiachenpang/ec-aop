package com.ailk.ecaop.common.utils;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.AopCall;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;
import org.n3r.ecaop.core.processor.TransReqParamsMappingProcessor;
import org.n3r.ecaop.core.processor.WsCall;

import com.ailk.ecaop.common.processor.Xml2Json4FbsMappingProcessor;
import com.ailk.ecaop.common.processor.Xml2JsonMappingProcessor;
import com.alibaba.fastjson.JSON;

/**
 * 为了满足在一个Processor中 使用其他的Processor
 * 尤其是在一个Processor中调用多个接口的时候使用
 * 
 * @author Administrator
 */
public class CallProcessorUtils {

    private Exchange exchange;
    private String methodCode;

    private CallProcessorUtils(Exchange exchange) {
        this.exchange = exchange;
    }

    public static CallProcessorUtils newCallIFaceUtils(Exchange exchange) {
        return new CallProcessorUtils(exchange);
    }

    public CallProcessorUtils setMethodCode(String methodCode) {
        this.methodCode = exchange.getMethodCode();
        exchange.setMethodCode(methodCode);
        return this;
    }

    public CallProcessorUtils restoreMethodCode() {
        if (StringUtils.isEmpty(methodCode))
            return this;
        exchange.setMethodCode(methodCode);
        this.methodCode = null;
        return this;
    }

    /**
     * 准备透传的请求。
     * 
     * @return this
     */
    public CallProcessorUtils preDataForPassThrough() {
        TransReqParamsMappingProcessor processor = new TransReqParamsMappingProcessor();
        processor.process(exchange);
        return this;
    }

    /**
     * 为发送到全业务生成请求报文，为调用webservice接口做准备
     * 
     * @param paramPath 接口配置文件中，生成请求报文对应的key。例：ecaop.ecsb.mcba.ParametersMapping
     * @param exchange
     */
    public CallProcessorUtils preData(String paramPath) {
        Map body = exchange.getIn().getBody(Map.class);
        Object msg = body.get("msg");
        if (msg instanceof String) {
            Map msgMap = JSON.parseObject((String) body.get("msg"));
            body.put("msg", msgMap);
        }
        ParametersMappingProcessor pmp = new ParametersMappingProcessor();
        String[] parametersMapper = { paramPath };
        pmp.applyParams(parametersMapper);
        pmp.process(exchange);
        return this;
    }

    /**
     * 调用webservice接口
     * 
     * @param urlKey
     * @return this
     * @throws Exception
     */
    public CallProcessorUtils wsCall(String urlKey) throws Exception {
        WsCall call = new WsCall();
        call.applyParams(str2Arr(urlKey));
        call.process(exchange);
        return this;
    }

    /**
     * 调用aop接口
     * 
     * @param urlKey
     * @return this
     * @throws Exception
     */
    public CallProcessorUtils aopCall(String urlKey) throws Exception {
        AopCall call = new AopCall();
        call.applyParams(str2Arr(urlKey));
        call.process(exchange);
        return this;
    }

    /**
     * 处理全业务平台返回的xml，生成json
     * 
     * @param xmlPath 配置文件中，处理请求和返回的xml模板 对应的key
     * @throws Exception
     */
    public CallProcessorUtils xml2Json(String xmlPath) throws Exception {
        Xml2Json4FbsMappingProcessor X2J = new Xml2Json4FbsMappingProcessor();
        String[] chkUsrStr = { xmlPath };
        X2J.applyParams(chkUsrStr);
        X2J.process(exchange);
        return this;
    }

    /**
     * 处理枢纽平台返回的xml，生成json
     * 
     * @param xmlPath 配置文件中，处理请求和返回的xml模板 对应的key
     * @throws Exception
     */
    public CallProcessorUtils xml2Json4ONS(String xmlPath) throws Exception {
        Xml2JsonMappingProcessor X2J = new Xml2JsonMappingProcessor();
        String[] chkUsrStr = { xmlPath };
        X2J.applyParams(chkUsrStr);
        X2J.process(exchange);
        return this;
    }

    /**
     * 用来调用其他的Processor类
     * 会根据参数param依次调用applyParams和pocess方法
     * 
     * @param processor 将要调用的Processor对象
     * @param param 调用applyParams方法时的参数
     * @return this
     * @throws Exception 会将Processor类中抛出的异常进行封装，不适用于可能抛出异常的Processor
     */
    public CallProcessorUtils executeProcessor(Class clazz, String param) throws Exception {
        Object processor = clazz.newInstance();

        if (param != null && processor instanceof ParamsAppliable)
            ((ParamsAppliable) processor).applyParams(str2Arr(param));

        if (processor instanceof BaseAopProcessor)
            ((BaseAopProcessor) processor).process(exchange);
        else
            throw new EcAopServerBizException("使用的类：" + clazz + "不属于BaseAopProcessor");
        return this;
    }

    /**
     * 用来调用其他的Processor类
     * 只会调用pocess方法
     * 
     * @param processor 将要调用的Processor对象
     * @return this
     * @throws Exception 会将Processor类中抛出的异常进行封装，不适用于可能抛出异常的Processor
     */
    public CallProcessorUtils executeProcessor(Class clazz) throws Exception {
        Object processor = clazz.newInstance();

        if (processor instanceof BaseAopProcessor)
            ((BaseAopProcessor) processor).process(exchange);
        else
            throw new EcAopServerBizException("使用的类：" + clazz + "不属于BaseAopProcessor");
        return this;
    }

    private String[] str2Arr(String str) {
        String[] in = new String[1];
        in[0] = str;
        return in;
    }

}
