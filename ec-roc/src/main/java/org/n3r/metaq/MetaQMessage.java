package org.n3r.metaq;

import org.n3r.core.lang.RIP;


public class MetaQMessage {
    private String topic;
    private long sts = System.currentTimeMillis();
    private int exp;
    private Object msg;
    private String jsonMsg;
    private String clazz;
    private String ip = RIP.getIp();

    @Override
    public String toString() {
        return "MetaQMessage [topic=" + topic + ", sts=" + sts + ", exp=" + exp +  ", jsonMsg="
                    + jsonMsg + ", msg=" + msg + ", clazz=" + clazz + ", ip=" + ip + "]";
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public long getSts() {
        return sts;
    }

    public void setSts(long sts) {
        this.sts = sts;
    }

    public int getExp() {
        return exp;
    }

    public void setExp(int exp) {
        this.exp = exp;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getJsonMsg() {
        return jsonMsg;
    }

    public void setJsonMsg(String jsonMsg) {
        this.jsonMsg = jsonMsg;
    }

    public String getClazz() {
        return clazz;
    }

    public void setClazz(String clazz) {
        this.clazz = clazz;
    }

    public Object getMsg() {
        return msg;
    }

    public void setMsg(Object msg) {
        this.msg = msg;
    }

}
