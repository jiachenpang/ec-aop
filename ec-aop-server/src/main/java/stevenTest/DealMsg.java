package stevenTest;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import com.alibaba.fastjson.JSON;

public class DealMsg {

    public static void main(String[] str) throws Exception {
        String appkey = "appkey=";
        String bizkey = "&bizkey=";
        String method = "&method=";
        SimpleDateFormat formatApptx = new SimpleDateFormat("yyyyMMddHHmmss");
        String apptx = "&apptx=" + formatApptx.format(new Date());
        String msg = "&msg=";
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String timestamp = "&timestamp=" + format.format(new Date());
        int len = str.length;
        if (0 == len) {
            throw new Exception("参数为空");
        }
        String faceInfo = str[len - 1];
        faceInfo = faceInfo.replace("{", "{\"");
        faceInfo = faceInfo.replace(":", "\":\"");
        faceInfo = faceInfo.replace("}", "\"}");
        faceInfo = faceInfo.replace(",", "\",\"");
        faceInfo = faceInfo.replace("]\"", "]");
        faceInfo = faceInfo.replace("\"[", "[");
        faceInfo = faceInfo.replace("\"{", "{");
        faceInfo = faceInfo.replace("}\"", "}");
        faceInfo = faceInfo.replace("\"\"", "\"");
        faceInfo = faceInfo.replace(":\",", ":\"\",");
        faceInfo = faceInfo.replace(":\"]", ":\"\"]");
        faceInfo = faceInfo.replace(":\"}", ":\"\"}");
        Map reqMsg = (Map) JSON.parseObject(faceInfo).get("reqMsg");
        appkey += reqMsg.get("appkey");
        bizkey += reqMsg.get("bizkey");
        method += reqMsg.get("method");
        msg += reqMsg.get("msg");
        String url = appkey + bizkey + method + apptx + msg + timestamp;
        String 本机环境 = "http://127.0.0.1:7001/aop/aopservlet?" + url;
        String 本地测试 = "http://132.35.81.218:8000/aop/test?" + url;
        String 集成测试 = "http://132.35.81.218:8000/aop/aopservlet?" + url;
        String 生产环境 = "http://132.35.88.104/aop/aopservlet?" + url;
        System.out.println("本机环境:" + 本机环境);
        System.out.println("本地测试:" + 本地测试);
        System.out.println("集成测试:" + 集成测试);
        System.out.println("生产环境:" + 生产环境);
    }
}
