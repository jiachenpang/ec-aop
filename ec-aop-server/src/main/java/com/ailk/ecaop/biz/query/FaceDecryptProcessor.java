package com.ailk.ecaop.biz.query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.CallGZTSystemUtils;
import com.alibaba.fastjson.JSON;

/**
 * @Description: 活体检测 SDK 解密
 * @author Zeng
 * @date 2017-5-2
 */
@EcRocTag("faceDecrypt")
public class FaceDecryptProcessor extends BaseAopProcessor {

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void process(Exchange exchange) throws Exception {
        Map inBody = exchange.getIn().getBody(Map.class);
        Map msg = inBody.get("msg") instanceof String ? JSON.parseObject((String) inBody.get("msg")) : (Map) inBody
                .get("msg");
        if (!"01".equals(msg.get("businessSystem")))
        {
            throw new EcAopServerBizException("9999", "服务提供方目前只支持国政通系统。");
        }
        //必传参数校验之第二次校验（第一次是props设置）
        String dataPackage = (String) msg.get("dataPackage");
        if (null == dataPackage || "".equals(dataPackage))
        {
            throw new EcAopServerBizException("9999", "活体SDK捕获的数据包dataPackage未传,请检查参数！");
        }
        String imageOpt = (String) msg.get("imageOpt");
        if (null == imageOpt || "".equals(imageOpt))
        {
            throw new EcAopServerBizException("9999", "必传参数imageOpt未传,请检查参数！");
        }
        // 拼请求参数
        Map sentMap = CallGZTSystemUtils.createPublicParamForGZT();
        sentMap.put("dataPackage", dataPackage);
        sentMap.put("imageOpt", imageOpt);
        //checkSamePerson 默认传1
        sentMap.put("checkSamePerson", "1");

        exchange.getIn().setBody(sentMap);
        // 暂用调号卡中心的numCenterCall
        CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.gzt.faceDecrypt");
        Map out = new CallGZTSystemUtils().dealGZTReturn(exchange, "活体检测SDK解密");
        Map outMap = new HashMap();

        String isSamePerson = (String) out.get("is_same_person");
        if (null != isSamePerson && !"".equals(isSamePerson))
            outMap.put("isSamePerson", out.get("is_same_person"));
        List package_images = out.get("package_images") instanceof String ? (List) JSON.parse((String) out
                .get("package_images")) : (List) out.get("package_images");
        if (null != package_images && package_images.size() > 0)
        {
            //请求参数里选择返回第几张图片，AOP将返回参数名为image+序号 的图片
            if (!"0".equals(imageOpt))
            {
                outMap.put("image" + imageOpt, package_images.get(0));
            }
            else
            {
                for (int i = 0; i < package_images.size() && i < 3; i++)
                {
                    outMap.put("image" + (i + 1), package_images.get(i));
                }
            }
        }
        exchange.getOut().setBody(outMap);
    }
}
