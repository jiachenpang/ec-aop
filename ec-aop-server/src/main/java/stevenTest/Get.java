package stevenTest;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpClientParams;

import com.alibaba.fastjson.JSONObject;

public class Get {

    private static final HttpClient httpClient = new HttpClient(new HttpClientParams(),
            new MultiThreadedHttpConnectionManager());
    static String appkey = "tsst.sub";
    static String methodCode = "ecaop.trades.query.comm.cust.mcheck";
    static final String AOP_URL = "http://132.35.81.217:8000/aop/test";

    public static void main(String[] str) throws Exception, IOException {
        GetMethod method = new GetMethod(AOP_URL);
        method.setURI(new URI(method.getURI().toString() + "?" + preparam()));
        httpClient.executeMethod(method);
        System.out.println(method.getStatusCode());
        System.out.println(method.getResponseBodyAsString());
    }

    static String getDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date now = new Date();
        return sdf.format(now);
    }

    static String preparam() {
        return preHeader() + "&msg=" + preBody();
    }

    static String preHeader() {
        return "appkey=" + appkey + "&method=" + methodCode + "&bizkey=" + "CS-010" + "&timestamp="
                + getDate() + "&apptx=" + "123456";
    }

    static String preBody() {
        JSONObject jo = new JSONObject();
        jo.put("channelId", "31a0263");
        jo.put("channelType", "2020200");
        jo.put("city", "310");
        jo.put("district", "312532");
        jo.put("operatorId", "SHDL00");
        jo.put("province", "31");
        jo.put("serviceClassCode", "0000");
        jo.put("certType", "02");
        jo.put("certNum", "36220219900608665X");
        jo.put("serType", "1");
        jo.put("checkType", "0");
        return jo.toJSONString();
    }
}
