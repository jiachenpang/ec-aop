package com.ailk.ecaop.biz.query;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.utils.CallGZTSystemUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.HttpUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.alibaba.fastjson.JSON;
import net.sf.json.JSONObject;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.sys.EcAopServerSysException;
import org.n3r.ecaop.core.log.elk.ELKAopLogger;
import org.n3r.ecaop.core.processor.AopHandler;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.esql.Esql;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @Description: 人脸识别校验
 * @author Zeng
 * @date 2017-3-31
 */
@EcRocTag("faceCheck")
@SuppressWarnings({ "unchecked", "rawtypes", "resource" })
public class FaceCheckProcessor extends BaseAopProcessor {

    private final Logger log = LoggerFactory.getLogger(FaceCheckProcessor.class);
    @Override
    public void process(Exchange exchange) throws Exception {
        Map inBody = exchange.getIn().getBody(Map.class);
        Map msg = inBody.get("msg") instanceof String ? JSON.parseObject((String) inBody.get("msg")) : (Map) inBody.get("msg");
        if (!"01".equals(msg.get("system"))) {
            throw new EcAopServerBizException("9999", "服务提供方目前只支持国政通系统。");
        }
        /**
         * 对比类型 01:生活照与公安部照片的对比 02:生活照与身份证照片（人像）对比
         */
        String scene = (String) msg.get("scene");
        String certId = (String) msg.get("certId");
        String certName = (String) msg.get("certName");
        // RHQ2017033100036-关于明确实体渠道人脸识别技术试点省分的需求
        String province = (String) msg.get("province");
        String city = (String) msg.get("city");
        String appkey = (String) inBody.get("appkey");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String apptx = (String) inBody.get("apptx");
        ELKAopLogger.logStr("自然人请求开始：" + ", appTx: " + apptx);
        msg.put("apptx", apptx);
        String timestamp = sdf.format(new Date());
        Map faceInfo = new HashMap();
        faceInfo.put("province", province);
        faceInfo.put("city", city);
        faceInfo.put("appkey", appkey);
        faceInfo.put("apptx", apptx);
        faceInfo.put("timestamp", timestamp);
        boolean isGZT = "01".equals(scene);// 01时去国政通
        if (isGZT && (null == certId || "".equals(certId) || null == certName || "".equals(certName))) {
            throw new EcAopServerBizException("9999", "对比类型为01时,身份证编码和姓名必传，请核实。");
        }
        // 01时去能力平台或者国政通获取baseImg和baseImgType
        String baseImg = (String) msg.get("baseImg");
        if ("02".equals(scene) && (null == baseImg || "".equals(baseImg))) {
            throw new EcAopServerBizException("9999", "对比类型为02时,baseImg必传。");
            // 01时去国政通
        }
        String baseImgType = "1";// 1、身份证照片（正面）,5、公安部照片
        ELKAopLogger.logStr("获取自然人开关前" + ", appTx: " + apptx);
        String apiConfig = EcAopConfigLoader.getStr("ecaop.global.param.face.aip.check");// Config.getStr("ecaop
        // .global.param.face.aip.check")
        ELKAopLogger.logStr("获取自然人开关后" + ", appTx: " + apptx);
        if (isGZT) {
            if ("2".equals(apiConfig)) {// 新流程:baseImg和baseImgType从能力平台获取，RHQ2017111600049-关于国政通身份信息比对服务调整的系统改造需求
                Map aipResult = callAipIdentityCheck(msg, baseImgType);
                baseImg = (String) aipResult.get("baseImg");
                baseImgType = (String) aipResult.get("baseImgType");
            } else {// 老流程:baseImg和baseImgType从国政通获取
                Exchange tempExchange = ExchangeUtils.ofCopy(exchange, msg);
                msg.put("certType", "01");// certType: 01是组合认证,02是公安认证
                inBody.put("msg", msg);
                tempExchange.getIn().setBody(inBody);
                AopHandler handler = new AopHandler();
                try {
                    handler.applyParams(new String[] { "ecaop.proc.method.qcfc.processors" });
                    handler.process(tempExchange);
                } catch (EcAopServerSysException e) {
                    throw new EcAopServerSysException(e.getCode(), e.getMessage());
                } catch (EcAopServerBizException e) {
                    throw new EcAopServerBizException(e.getCode(), e.getMessage());
                } catch (Exception e) {
                    throw new EcAopServerSysException("9999", "调国政通报错： " + e.getMessage());
                }
                if (null == tempExchange.getOut()) {
                    throw new EcAopServerBizException("9999", "调国政通报错： 返回内容为空！");
                }
                Map out = tempExchange.getOut().getBody(Map.class);
                if (null == out || out.size() < 1) {
                    out = (Map) JSON.parse(tempExchange.getOut().getBody(String.class));
                }
                if (null == out || out.size() < 1) {
                    throw new EcAopServerBizException("9999", "调国政通报错： 返回内容为空！");
                }
                if (!"200".equals(String.valueOf(tempExchange.getProperty("HTTP_STATUSCODE")))) {
                    throw new EcAopServerBizException("9999", "调国政通报错：" + out.get("detail"));
                }
                baseImg = (String) out.get("photo");
                if (null == baseImg || "".equals(baseImg) || "null".equalsIgnoreCase(baseImg))
                    throw new EcAopServerBizException("0006", "国政通无此用户照片信息。");
                // certTypeR本次认证类型: 01 本地认证 02 公安认证 ; imgType照片来源: 01 二代证 02 国政通
                if ("02".equals(out.get("imgType") + ""))
                    baseImgType = "5";// 1、身份证照片（正面）,5、公安部照片
            }
        }
        // 拼请求参数
        exchange.getIn().setBody(creatSentMap(msg, baseImg, baseImgType));
        // 暂用调号卡中心的numCenterCall
        try {
            CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.gzt.facecheck");
        } catch (EcAopServerSysException e) {
            throw new EcAopServerSysException(e.getCode(), e.getMessage());
        } catch (EcAopServerBizException e) {
            throw new EcAopServerBizException(e.getCode(), e.getMessage());
        } catch (Exception e) {
            throw new EcAopServerSysException("9999", "调国政通报错： " + e.getMessage());
        }
        // 记录调用国政通人脸识别数据到数据库
        String result = new CallGZTSystemUtils().faceCheckResult(exchange, "人像比对");
        String resultInfo = "人脸识别失败";
        if ("0".equals(result)) {
            resultInfo = "人脸识别成功";
        }
        faceInfo.put("resultInfo", resultInfo);
        try {
            insertFaceCheckInfo(faceInfo);
        } catch (Exception e) {
            e.printStackTrace();
        }
        dealGZTReturn(exchange, baseImg, isGZT);
    }

    private Map creatSentMap(Map msg, String baseImg, String baseImgType) {
        Map sentMap = CallGZTSystemUtils.createPublicParamForGZT();
        sentMap.put("faceImg", msg.get("faceImg"));
        sentMap.put("faceImgType", "3");// 3、手持身份证的照片（取生活照片）
        sentMap.put("baseImg", baseImg);
        sentMap.put("baseImgType", baseImgType);
        sentMap.put("trueNegativeRate", "99.9");
        return sentMap;
    }

    private void dealGZTReturn(Exchange exchange, String baseImg, boolean isGZT) {
        Map out = new CallGZTSystemUtils().dealGZTReturn(exchange, "人像比对");
        Map outMap = new HashMap();
        if (isGZT)
            outMap.put("photo", baseImg);
        String verify_result = (String) out.get("verify_result");
        if (null != verify_result && !"".equals(verify_result))
            outMap.put("result", verify_result);
        String verify_similarity = (String) out.get("verify_similarity");
        if (null != verify_similarity && !"".equals(verify_similarity)) {
            try {
                outMap.put("similarity", (int) Math.round(Double.parseDouble(verify_similarity)) + "");
            } catch (Exception e) {
                throw new EcAopServerBizException("9999", "国政通人像比对接口返回值verify_similarity【" + verify_similarity + "】转换为整数异常！" + e.getMessage());
                }
        }
        exchange.getOut().setBody(outMap);
    }

    /**
     * 该方法用于记录
     *
     * @param 省份：province
     *            城市：city 接入方编码：appkey
     */
    public static void insertFaceCheckInfo(Map msg) {
        new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/aop/common.esql").id("insertFaceCheckInfo").params(msg).execute();
    }

    /**
     * 调能力平台身份证认证接口
     * 
     * @return
     * 
     */
    public Map callAipIdentityCheck(Map msg, String baseImgType) throws Exception {
        Map preDataMap = NumFaceHeadHelper.creatHead();
        Map faceInfoReq = new HashMap();
        Map aipResult = new HashMap();
        String certId = (String) msg.get("certId");
        String idType = "3";// 默认18位身份证
        if ("15".equals(certId.length())) {
            idType = "1";
        } else if ("17".equals(certId.length())) {
            idType = "2";
        }
        faceInfoReq.put("SYS_CODE", "5600");// 操作系统编码(能力共享平台)
        faceInfoReq.put("PROVINCE_CODE", msg.get("province"));
        faceInfoReq.put("EPARCHY_CODE", msg.get("city"));
        faceInfoReq.put("OPERATOR_ID", msg.get("operatorId"));
        faceInfoReq.put("CHANNEL_ID", msg.get("channelId"));
        faceInfoReq.put("CHANNEL_TYPE", msg.get("channelType"));
        faceInfoReq.put("ID_TYPE", idType);// 1、15位身份证；2、17位身份证；3、18位身份证
        faceInfoReq.put("CERT_NUM", certId);
        faceInfoReq.put("CERT_NAME", msg.get("certName"));
        faceInfoReq.put("CERT_TYPE", "01");// 写死，01：组合认证，02：公安认证
        preDataMap.put("UNI_BSS_BODY", MapUtils.asMap("IDENTITY_CHECK_REQ", faceInfoReq));
        JSON preDataJson = (JSON) JSON.toJSON(preDataMap);
        String preDataString = preDataJson.toJSONString();
        ELKAopLogger.logStr("获取自然人地址前" + ", appTx: " + msg.get("apptx"));
        String url = EcAopConfigLoader.getStr("ecaop.comm.conf.url.aip.identityCheck");
        ELKAopLogger.logStr("获取自然人地址后" + ", appTx: " + msg.get("apptx"));
        JSONObject result = null;
        try {
            result = HttpUtil.doPostStr(url, preDataString, msg.get("apptx"));
        } catch (EcAopServerSysException e) {
            throw new EcAopServerSysException(e.getCode(), e.getMessage());
        } catch (EcAopServerBizException e) {
            throw new EcAopServerBizException(e.getCode(), e.getMessage());
        } catch (Exception e) {
            throw new EcAopServerSysException("9999", "调能力平台报错： " + e.getMessage());
        }
        Map resultMap = JSONObject.fromObject(result);
        if (IsEmptyUtils.isEmpty(resultMap)) {
            throw new EcAopServerBizException("9999", "能力平台未返回信息");
        }
        Map UNI_BSS_BODY = (Map) resultMap.get("UNI_BSS_BODY");
        if (null == UNI_BSS_BODY || UNI_BSS_BODY.isEmpty()) {
            throw new EcAopServerBizException("9999", "调用能力平台报错");
        }
        Map IDENTITY_CHECK_RSP = (Map) UNI_BSS_BODY.get("IDENTITY_CHECK_RSP");
        if (!"0000".equals(IDENTITY_CHECK_RSP.get("RESP_CODE"))) {
            throw new EcAopServerBizException((String) IDENTITY_CHECK_RSP.get("RESP_CODE"), "调用能力平台报错:" + (String) IDENTITY_CHECK_RSP.get("RESP_DESC"));
        }
        String certPhoto = (String) IDENTITY_CHECK_RSP.get("CERT_PHOTO");
        String photoType = (String) IDENTITY_CHECK_RSP.get("PHOTO_TYPE");
        if (IsEmptyUtils.isEmpty(certPhoto)) {
            throw new EcAopServerBizException("9999", "自然人未查询到证件照！");
            }
        if ("02".equals(photoType)) {
            baseImgType = "5";
            }
        aipResult.put("baseImg", certPhoto);
        aipResult.put("baseImgType", baseImgType);
        return aipResult;
    }

    private String[] str2Arr(String str) {
        String[] in = new String[1];
        in[0] = str;
        return in;
    }
}
