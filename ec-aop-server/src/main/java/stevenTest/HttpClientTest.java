package stevenTest;

import java.io.IOException;
import java.net.URLDecoder;

import javax.servlet.http.HttpServlet;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.httpclient.params.HttpMethodParams;

@SuppressWarnings("null")
public class HttpClientTest extends HttpServlet {

    private static final long serialVersionUID = 3803792818152637845L;

    private static final HttpClient httpClient = new HttpClient(new HttpClientParams(),
            new MultiThreadedHttpConnectionManager());

    public static void main(String[] args) {
        testConnType();
    }

    public static void testConnType() {
        String result;
        PostMethod method = new PostMethod("HTTP://132.35.81.217:8000/aop/test");
        method.setRequestHeader("Content-type", "application/x-www-form-urlencoded; charset=UTF-8");
        method.setRequestHeader("Accept-Language", "us");
        method.setRequestHeader("CONN_type", "SSL");
        httpClient.getParams().setParameter(HttpMethodParams.HTTP_CONTENT_CHARSET, "UTF-8");
        method.setParameter(HttpMethodParams.HTTP_CONTENT_CHARSET, "UTF-8");
        httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(40000);
        httpClient.getHttpConnectionManager().getParams().setSoTimeout(40000);
        NameValuePair[] data = {
                new NameValuePair("appkey", "gdjk.sub"),
                new NameValuePair("method", "ecaop.trades.query.comm.simpsnres.chg"),
                new NameValuePair("apptx", "124354"),
                new NameValuePair("timestamp", "2014-12-10 12:00:00"),
                new NameValuePair(
                        "msg",
                        "{\"channelId\":\"59b05el\",\"channelType\":\"2010200\",\"city\":\"591\","
                                + "\"district\":\"592003\",\"operatorId\":\"WBRA03\",\"para\":[],\"province\":\"59\"," +
                                "\"resourcesInfo\":[{\"occupiedFlag\":\"0\",\"occupiedTime\":\"\"," +
                                "\"resourcesCode\":\"18688888888\",\"resourcesType\":\"02\"}],\"serType\":\"1\"}") };
        method.setRequestBody(data);

        try {
            System.out.println(httpClient.executeMethod(method));
            result = URLDecoder.decode(method.getResponseBodyAsString(), "UTF-8");
            System.out.println(result);
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

    }
}
