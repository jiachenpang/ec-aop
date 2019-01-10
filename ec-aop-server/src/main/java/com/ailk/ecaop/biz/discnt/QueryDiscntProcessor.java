package com.ailk.ecaop.biz.discnt;

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

/**
 * 各类优惠查询<br/>
 * TODO cy: 必须在2016-12-16之前完成 <br/>
 * #2016/12/09, 基本功能实现了，代码待测试.<br/>
 * #2016/12/13, 修改报文返回格式，从Map改成List.<br/>
 * #2016/12/22, 修复请求参数转换成H2协议的bug.<br/>
 * #2016/12/26, 修改返回报文的转载方式，应该只返回一条，先按一条返回处理，之后有时间写个通用的返回工具类.<br/>
 * #2017/01/16, 处理返回报文乱码问题，修改放回报文的编码格式，在本Processor中单独用gb2312编码处理返回报文，原有的公共方法不变。但是H2协议中规定的是utf-8，暂时就先这样了。<br/>
 * #2017/01/19, 修复了多条数据返回的bug，现在可以正常接收多条记录的返回.<br/>
 * 
 * @author: crane[yuanxingnepu@gmail.com]
 * @date: 2016-12-9 下午02:38:42
 */
@EcRocTag("QueryDiscnt")
public class QueryDiscntProcessor extends BaseAopProcessor implements ParamsAppliable {

    static char packageEnd = (char) 0x1a;
    static int no = 1;
    static char spFiled = (char) 0x09;// tab键
    static String recordSpilt = "\r\n";// 回车键
    private String url;
    private int port;

    @Override
    public void process(Exchange exchange) throws Exception {
        Map<?, ?> body = exchange.getIn().getBody(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, String> mapMsg = (Map<String, String>) body.get("msg");

        // 请求参数转换成符合H2协议头的参数格式
        mapMsg.put("number", mapMsg.get("numId"));
        mapMsg.put("channelId", "Z000KF");

        Socket socket = null;

        try {
            socket = new Socket(url, port);
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream os = new BufferedOutputStream(socket.getOutputStream());
            mapMsg.put("busiType", "T");
            String servceName = String.format("%-12s", "GDISCNTINFO");
            mapMsg.put("servceName", servceName);
            String headMsg = TransReqMsg.getReqMsg(mapMsg);
            StringBuffer bodyMsg = new StringBuffer();
            bodyMsg.append(mapMsg.get("qryType")).append(spFiled);
            String reqMsg = headMsg + bodyMsg.toString() + packageEnd;
            byte[] real = reqMsg.getBytes("UTF-8");

            os.write(real);
            os.flush();

            String rspMsgs = getRspMsg(in).substring(102);
            // 返回给上游的信息.
            Message out = new DefaultMessage();
            if (rspMsgs != null && !rspMsgs.equals(packageEnd)) {
                // 装载返回信息.
                Map outMessage = new HashMap();
                List outlist = new ArrayList<Map<String, String>>();

                rspMsgs.replace(String.valueOf(packageEnd), "");
                String[] rspRecordMsg = rspMsgs.split(recordSpilt);

                for (int i = 0; i < rspRecordMsg.length; i++) {
                    String[] rspMsg = rspRecordMsg[i].split(String.valueOf(spFiled));
                    // 装载返回信息.
                    Map outMap = new HashMap();
                    outMap.put("date", rspMsg[0]);
                    outMap.put("productId", rspMsg[1]);
                    outMap.put("productName", rspMsg[2]);
                    outMap.put("discntType", rspMsg[3]);
                    outMap.put("discntTypeName", rspMsg[4]);
                    outMap.put("discntTotal", rspMsg[5]);
                    outMap.put("discntUse", rspMsg[6]);
                    outMap.put("discntOver", rspMsg[7]);
                    outMap.put("discntLeft", rspMsg[8]);
                    outMap.put("discntId", rspMsg[9]);
                    outMap.put("discntName", rspMsg[10]);
                    outMap.put("discntUnitDesc", rspMsg[11]);
                    outMap.put("discntUnit", rspMsg[12]);
                    outlist.add(outMap);
                }
                outMessage.put("discntInfo", outlist);
                out.setBody(outMessage);
                exchange.setOut(out);
            }
            else {
                throw new EcAopServerBizException("9999", "下游返回信息为空!");
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "下游返回信息为空!");
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

    private static String getRspMsg(InputStream in) throws Exception {
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
        return new String(content, "gb2312");
    }

}
