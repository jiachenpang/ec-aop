package com.ailk.ecaop.common.utils;

import net.sf.json.JSONObject;

/**
 * 此方法调用HTTPS请求用的
 * @author YangZG
 */

public class HttpUtil {

    // // 线程安全的多线程管理器
    // private static HttpClient httpClient;
    // private static int httpSocketTimeoutSeconds = 30;
    // private static int httpConnectTimeoutSeconds = 30;
    // private static HttpClientParams params;
    // static {
    // PoolingClientConnectionManager connectionManager = new PoolingClientConnectionManager();
    // connectionManager.setMaxTotal(256);
    // connectionManager.setDefaultMaxPerRoute(32);
    //
    // HttpParams params = new BasicHttpParams();
    // HttpConnectionParams.setConnectionTimeout(params, httpConnectTimeoutSeconds * 1000);
    // HttpConnectionParams.setSoTimeout(params, httpSocketTimeoutSeconds * 1000);
    //
    // // 默认2
    // httpClient = new SSLClient(params, connectionManager);
    // }

    public static JSONObject doPostStr(String url, String json, Object apptx) {
        // MultiThreadedHttpConnectionManager n = new MultiThreadedHttpConnectionManager();
        // HttpPost httpPost = null;
        String result = null;
        JSONObject resultJson = null;
        // HttpResponse response = null;
        // HttpEntity resEntity = null;
        // ELKAopLogger.logStr("请求-- 调用下游地址: " + url + ", appTx: " + apptx + ", 报文: " + json);
        // try {
        // httpPost = new HttpPost(url);
        // httpPost.setEntity((HttpEntity) new StringEntity(json, Charset.forName("UTF-8")));
        // response = httpClient.execute(httpPost);
        // if (response != null) {
        // resEntity = response.getEntity();
        // if (resEntity != null) {
        // result = EntityUtils.toString(resEntity, "UTF-8");
        // }
        // }
        // } catch (Exception e) {
        // throw new EcAopServerBizException("9999", "调用能力平台异常" + e.getMessage());
        // } finally {
        // httpPost.releaseConnection();
        // }
        // ELKAopLogger.logStr("返回-- 调用下游地址: " + "下游返回内容：" + ", appTx: " + apptx + ", 报文: " + result);
        resultJson = JSONObject.fromObject(result);
        return resultJson;
    }
}
