// package newTest.Entry;
//
// import java.util.List;
//
// /**
// * 赋权的返回结果实体
// * @author Administrator
// */
// public class SVNResult {
//
// public static int SUCCESS_CODE = 0;// 成功状态码
// public static int ERROR_CODE = 1; // 异常状态码
//
// private int code; // 状态码 0-成功,1-异常
// private String detial; // 返回详细描述
// private Object data; // 返回的数据
// private List<String> exception; // 返回报错信息
// private String svnDate; // 赋权的日期
//
// public SVNResult() {
//
// }
//
// public SVNResult(int code, String detial, Object data, List<String> exception, String svnDate) {
// super();
// this.code = code;
// this.detial = detial;
// this.data = data;
// this.exception = exception;
// this.svnDate = svnDate;
// }
//
// public int getCode() {
// return code;
// }
//
// public void setCode(int code) {
// this.code = code;
// }
//
// public String getDetial() {
// return detial;
// }
//
// public void setDetial(String detial) {
// this.detial = detial;
// }
//
// public Object getData() {
// return data;
// }
//
// public void setData(Object data) {
// this.data = data;
// }
//
// public List<String> getException() {
// return exception;
// }
//
// public void setException(List<String> exception) {
// this.exception = exception;
// }
//
// public String getSvnDate() {
// return svnDate;
// }
//
// public void setSvnDate(String svnDate) {
// this.svnDate = svnDate;
// }
//
// @Override
// public String toString() {
// return "SVNResult [code=" + code + ", detial=" + detial + ", data=" + data + ", exception=" + exception
// + ", svnDate=" + svnDate + "]";
// }
//
// }
