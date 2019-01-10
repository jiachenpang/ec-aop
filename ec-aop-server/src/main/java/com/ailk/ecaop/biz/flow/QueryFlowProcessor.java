package com.ailk.ecaop.biz.flow;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.n3r.config.Config;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.core.util.ParamsUtils;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.utils.TransReqMsg;
import com.ailk.ecaop.common.utils.TransRspMsg;

/**
 * ClassName: QueryFlowProcessor
 * 
 * @Description: 流量查询
 * @author cuiminyi
 * @date 2016-7-29
 */
@EcRocTag("QueryFlow")
public class QueryFlowProcessor extends BaseAopProcessor implements ParamsAppliable {

    static char packageEnd = (char) 0x1a;
    static int no = 1;
    static char spFiled = (char) 0x09;// tab键
    private String url;
    private int port;

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map mapMsg = (Map) body.get("msg");
        mapMsg.put("channelId", "Z00AUDI");
        // 写死channelID的值Z00AUDI
        Socket socket = null;
        try {
            System.out.println("get socket..");
            socket = new Socket(url, port);
            System.out.println("--get socket..");
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream os = new BufferedOutputStream(socket.getOutputStream());
            String busiType = mapMsg.get("busiType").toString().substring(1);
            mapMsg.put("busiType", busiType);
            String servceName = String.format("%-12s", "GFLUX");
            mapMsg.put("servceName", servceName);
            String headMsg = TransReqMsg.getReqMsg(mapMsg);
            StringBuffer bodyMsg = new StringBuffer();
            bodyMsg.append(mapMsg.get("qryMonth")).append(spFiled).append(mapMsg.get("busiType")).append(spFiled);
            String reqMsg = headMsg + bodyMsg.toString() + packageEnd;
            byte[] real = reqMsg.getBytes("UTF-8");
            os.write(real);
            os.flush();
            String rspMsgs = TransRspMsg.getRspMsg(in).substring(102);
            if (rspMsgs != null) {
                Pattern p = Pattern.compile(String.valueOf(packageEnd));
                Matcher m = p.matcher(rspMsgs);
                rspMsgs = m.replaceAll("");
            }
            String[] rspMsg = rspMsgs.split(String.valueOf(spFiled));

            // 流量包查询
            servceName = String.format("%-12s", "GFLUXDISCNT");
            headMsg = TransReqMsg.getReqMsg(mapMsg);
            mapMsg.put("servceName", servceName);
            StringBuffer otherBodyMsg = new StringBuffer();
            otherBodyMsg.append(mapMsg.get("qryMonth")).append(spFiled);
            String otherReqMsg = headMsg + otherBodyMsg.toString() + packageEnd;
            System.out.println("流量包查询请求报文:" + otherReqMsg);
            byte[] otherReal = otherReqMsg.getBytes("UTF-8");
            os.write(otherReal);
            os.flush();
            String otherRspMsgs = TransRspMsg.getRspMsg(in).substring(102);
            if ("".equals(otherRspMsgs) || null == otherRspMsgs) {
                throw new EcAopServerBizException("9999", "CBSS接口未返回信息");
            }
            System.out.println("流量包查询返回报文:" + otherRspMsgs);
            if (otherRspMsgs != null) {
                Pattern p = Pattern.compile(String.valueOf(packageEnd));
                Matcher m = p.matcher(otherRspMsgs);
                otherRspMsgs = m.replaceAll("");
            }
            String[] otherRspMsg = otherRspMsgs.split(String.valueOf(spFiled));
            Map outMap = new HashMap();

            // 流量查询
            outMap.put("otherFlow", otherRspMsg[0]);
            outMap.put("otherUseFlow", otherRspMsg[1]);
            outMap.put("otherRemFlow", otherRspMsg[2]);
            outMap.put("overtopFlow", otherRspMsg[3]);
            outMap.put("totalFlow", rspMsg[0]);
            outMap.put("chargeFlow", rspMsg[1]);
            outMap.put("setTotalFlow", rspMsg[2]);
            outMap.put("setTotalUseFlag", rspMsg[3]);
            outMap.put("setTotalUseFlow", rspMsg[4]);
            outMap.put("setTotalRemainFlow", rspMsg[5]);

            // 流量包使用
            Message out = new DefaultMessage();
            out.setBody(outMap);
            exchange.setOut(out);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            try {
                if (socket != null)
                    socket.close();
            }
            catch (IOException e) {
                throw e;
            }
        }
    }

    @Override
    public void applyParams(String[] params) {
        url = Config.getStr(ParamsUtils.getStr(params, 0, ""));
        port = Integer.parseInt(Config.getStr(ParamsUtils.getStr(params, 1, "")));
    }
}
