package com.ailk.ecaop.biz.sub;

import java.io.IOException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Map;

import javax.servlet.http.HttpServlet;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.n3r.config.Config;
import org.n3r.ecaop.core.Exchange;

import com.alibaba.fastjson.JSONObject;

@SuppressWarnings("null")
public class HttpClientNumCenter extends HttpServlet {

    private static final long serialVersionUID = 3803792818152637845L;

    private static final HttpClient httpClient = new HttpClient(new HttpClientParams(),
            new MultiThreadedHttpConnectionManager());

    public static void main(String[] args) {

    }

    public static String testConnType(Exchange exchange, String json) {

        String result;

        Map body = (Map) exchange.getIn().getBody();
        Object msgObject = body.get("msg");
        Map msg = null;
        if (msgObject instanceof String) {
            msg = JSONObject.parseObject(msgObject.toString());
        }
        else {
            msg = (Map) msgObject;
        }
        String url = Config.getStr("ecaop.comm.conf.url.aop", "appId");
        PostMethod method = new PostMethod(url);
        method.setRequestHeader("Content-type", "application/x-www-form-urlencoded; charset=UTF-8");
        method.setRequestHeader("Accept-Language", "us");
        method.setRequestHeader("CONN_type", "SSL");
        httpClient.getParams().setParameter(HttpMethodParams.HTTP_CONTENT_CHARSET, "UTF-8");
        method.setParameter(HttpMethodParams.HTTP_CONTENT_CHARSET, "UTF-8");
        httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(40000);
        httpClient.getHttpConnectionManager().getParams().setSoTimeout(40000);
        method.addParameter("appkey", exchange.getAppkey());
        method.addParameter("method", "ecaop.trades.query.comm.simpsnres.qry");
        method.addParameter("apptx", "48424545565656");
        method.addParameter("timestamp", getTime());
        method.addParameter("bizkey", "TS-3G-012");
        method.addParameter("msg", json);
        try {
            httpClient.executeMethod(method);
            result = URLDecoder.decode(method.getResponseBodyAsString(), "UTF-8");
            System.out.println(result);
            return result;
        }
        catch (HttpException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            method.releaseConnection();
        }
        return null;

    }

    public static String getTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Calendar calendar = Calendar.getInstance();
        System.out.println(sdf.format(calendar.getTime()));
        return sdf.format(calendar.getTime());
    }
}
