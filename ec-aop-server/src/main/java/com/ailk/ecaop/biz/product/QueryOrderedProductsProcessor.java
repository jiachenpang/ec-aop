package com.ailk.ecaop.biz.product;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import org.n3r.ecaop.core.log.domain.EcAopReqLog;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EcRocTag("QueryOrderedProducts")
public class QueryOrderedProductsProcessor extends BaseAopProcessor implements ParamsAppliable {

    static char packageEnd = (char) 0x1a;// 包结束
    static char spFiled = (char) 0x09;// tab键
    static String recordSpilt = "\r\n";// 回车键

    private String url;
    private int port;
    private final Logger log = LoggerFactory.getLogger(QueryOrderedProductsProcessor.class);

    @Override
    public void process(Exchange exchange) throws Exception {

        Map<String, Object> body = exchange.getIn().getBody(Map.class);
        String apptx = (String) body.get("apptx");
        Map mapMsg = (Map) body.get("msg");
        mapMsg.put("channelId", "Z000KF");
        Socket socket = null;
        try {
            socket = new Socket(url, port);
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream os = new BufferedOutputStream(socket.getOutputStream());
            mapMsg.put("busiType", "Z");
            String servceName = String.format("%-12s", "GAUSINGPROD");
            mapMsg.put("servceName", servceName);
            String headMsg = TransReqMsg.getReqMsg(mapMsg);
            StringBuffer bodyMsg = new StringBuffer();
            bodyMsg.append(mapMsg.get("qryType")).append(spFiled);
            String reqMsg = headMsg + bodyMsg.toString() + packageEnd;
            byte[] real = reqMsg.getBytes("UTF-8");

            log.info(apptx + "请求报文:" + reqMsg);
            os.write(real);
            os.flush();

            String rsp = getRspMsg(in);
            System.out.println("maly" + rsp);
            String rspMsgs = getRspMsg(in).substring(102);
            log.info(apptx + "返回报文:" + rspMsgs);
            EcAopReqLog reqLog = exchange.getProperty(Exchange.REQLOG, EcAopReqLog.class);
            log.info("H2报文返回处理后 -- reqTs: " + reqLog.getReqTs() + ", appTx: " + reqLog.getApptx() + ", 报文: " + rspMsgs);

            if (rspMsgs != null) {
                Pattern p = Pattern.compile(String.valueOf(packageEnd));
                Matcher m = p.matcher(rspMsgs);
                rspMsgs = m.replaceAll("");
                String[] rspRecordMsg = rspMsgs.split(recordSpilt);
                System.out.println("下游返回为" + rspMsgs + "13132");
                List outlist = new ArrayList<Map<String, String>>();
                String[] keys = new String[] { "productId", "productName", "productDesc", "productType", "startTime",
                        "endTime", "productGrpBand", "groupBandDesc", "provinceBand", "provinceBandDesc", "packageId",
                        "packageName", "packageStartTime", "packageEndTime", "elementId", "elementName", "elementType",
                        "elementStartTime", "elementEndTime", "elementPropertyId", "elementPropertyName",
                        "elementPropertyValue" };
                for (int i = 0; i < rspRecordMsg.length; i++) {
                    String[] rspMsg = rspRecordMsg[i].split(String.valueOf(spFiled));
                    Map outMap = new HashMap();
                    for (int j = 0; j < rspMsg.length; j++) {
                        // 产品
                        if ("P".equals(mapMsg.get("qryType")) && j > 9) {
                            break;
                        }
                        // 包
                        if ("K".equals(mapMsg.get("qryType")) && j > 13) {
                            break;
                        }
                        // 所有信息
                        if ("A".equals(mapMsg.get("qryType")) && j > 18) {
                            break;
                        }
                        outMap.put(keys[j], rspMsg[j]);
                    }
                    outlist.add(outMap);
                }
                Message out = new DefaultMessage();
                out.setBody(outlist);
                exchange.setOut(out);
            }
            else {
                throw new EcAopServerBizException("9999", "下游返回信息为空!");
            }
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
                e.printStackTrace();
            }
        }
    }

    @Override
    public void applyParams(String[] params) {
        url = Config.getStr(ParamsUtils.getStr(params, 0, ""));
        port = Integer.parseInt(Config.getStr(ParamsUtils.getStr(params, 1, "")));
    }

    public static String getRspMsg(InputStream in) throws Exception {
        byte[] head = new byte[7];
        int readed = 0;
        readed += in.read(head, readed, 7 - readed);

        byte[] lengthArr = new byte[5];
        for (int j = 0; j < 5; j++) {
            lengthArr[j] = head[j + 2];
        }
        String lenStr = new String(lengthArr);
        int len = Integer.valueOf(lenStr.trim()).intValue();
        byte[] content = new byte[len];
        for (int j = 0; j < 7; j++) {
            content[j] = head[j];
        }
        in.read(content, readed, len - readed);
        return new String(content, "GB2312");
    }
}
