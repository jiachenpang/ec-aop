package stevenTest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;

public class HttpClientGet {

    static final String URL = "/aop/test";
    static final String urlParams = "appkey=mall.sub&method=ecaop.trades.query.comm.cert.check&apptx=dfdfd&" +
            "msg={\"certId\":\"120105197609200624\",\"certName\":\"李梅\",\"city\":\"901\",\"province\":\"90\"}" +
            "&timestamp=2015-01-29 21:20:00";

    public static void main(String[] str) throws Exception, Exception {
        HttpClient client = new HttpClient();
        client.getHostConfiguration().setHost("132.35.81.217", 8000, "http");
        HttpMethod method = new GetMethod(URLEncoder.encode(URL + "?" + urlParams, "utf-8"));
        client.executeMethod(method);
        System.out.println(method.getStatusLine());
        System.out.println(method.getResponseBodyAsString());
        method.releaseConnection();
        // getMsg();

    }

    static String getMsg() {
        try {
            String line = "";
            URL ulr = new URL(URL + "?" + urlParams);
            HttpURLConnection connection = (HttpURLConnection) ulr.openConnection();
            connection.setDoOutput(true);
            connection.setRequestProperty("connection", "Keep-Alive");
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.connect();
            System.out.println(connection.getContent());
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            while ((line = reader.readLine()) != null) {
                line += line;
            }
            System.out.println(line);
        }

        catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }
}
