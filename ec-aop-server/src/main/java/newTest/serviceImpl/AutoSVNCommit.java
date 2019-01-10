// package newTest.serviceImpl;
//
// import java.io.File;
// import java.io.FileInputStream;
// import java.io.PrintWriter;
// import java.util.ArrayList;
// import java.util.HashMap;
// import java.util.Iterator;
// import java.util.List;
// import java.util.Map;
// import java.util.Set;
//
// import newTest.SVNDao;
// import newTest.Entry.SVNResult;
// import newTest.Entry.SVNUtils;
// import newTest.Entry.SomePath;
//
// import org.apache.poi.ss.usermodel.Sheet;
// import org.apache.poi.xssf.usermodel.XSSFWorkbook;
// import org.tmatesoft.svn.core.wc.SVNClientManager;
//
// import com.ailk.ecaop.common.utils.IsEmptyUtils;
//
// /**
// * 自动进行版本文件新增赋权工作
// * @author Administrator
// */
// public class AutoSVNCommit {
//
// public static void main(String[] args) throws Exception {
// final String dataType = "0";// 是否为测试 1-非测试 , 0-测试
// String svnDate = "20170111";
// String versonDate = "20170111";
// autoCommit(dataType, svnDate, versonDate);
// }
//
// /**
// * 用于赋权的方法
// * @param dataType
// * @param svnDate 入库数据的索引日期
// * @param versonDate 用于手动选择版本日期
// * @return
// */
// public static SVNResult autoCommit(String dataType, String svnDate, String versonDate) {
// List<String> postLog = new ArrayList<String>();// 邮件内容
// List<String> exceptions = new ArrayList<String>();
// List<Map<String, String>> resultList = new ArrayList<Map<String, String>>();
// try {
// List<String> notHasList = new ArrayList<String>();
//
// SVNClientManager clientManager = SVNUtils.getSVNClientManager();
// // SVNUtils.doUpdate(exceptions, clientManager, SomePath.versonPath);
// // SVNUtils.doUpdate(exceptions, clientManager, SomePath.codePath);
// // 1.版本Excel路径
// String versonExcel = SomePath.versonPath + SVNUtils.getFilePathUtil(versonDate);
// System.out.println("更新版本的路径为:" + versonExcel);
// // 用于提交更新版本的日志
// String commitLog = versonExcel.substring(versonExcel.lastIndexOf(File.separator) + 1,
// versonExcel.indexOf(".xlsx"));
// XSSFWorkbook work = new XSSFWorkbook(new FileInputStream(versonExcel));
// Sheet sheet = work.getSheetAt(0);
// Map<String, List> contentMap = SVNUtils.getVersonContent(sheet);
// // 拼装的请求
// List<String> urls = new ArrayList<String>();
//
// // 2.获取到要读取的appkey以及method
// Set<String> appkeys = contentMap.keySet();
// for (String appkey : appkeys) {
// if (IsEmptyUtils.isEmpty(appkey)) {
// throw new Exception("版本文件有未填写的appkey,请检查版本文件!" + appkeys);
// }
// String appFileName = SomePath.appPathPro + appkey + ".props"; // 写入生产本地
// System.err.println(appkey + "赋权中");
// // 更新SVN在本地的props文件
// SVNUtils.doUpdate(exceptions, clientManager, appFileName);
// List methods = contentMap.get(appkey);
// Map<String, List<String>> retMap = SVNUtils.methodChecker(methods);
// if (retMap.containsKey("true")) {
// notHasList.addAll(retMap.get("true"));
// }
// if (retMap.containsKey("configTrue")) {
// exceptions.add("警告: 系统[" + appkey + "]下的赋权接口" + retMap.get("configTrue") + "需要增加配置,请注意!");
// }
// // 从生产环境上读取props内容
// String appkeyContent = SVNUtils.getSVNCofig(SomePath.proSvnUrl, SomePath.userName, SomePath.password,
// appkey + ".props", exceptions);
//
// // 对需赋权接口进行赋权,并获取到赋权后props的全部内容
// Map<String, Object> assignRetMap = SVNUtils.Assign(appkey, methods, appkeyContent); // 赋权后appkey下有的接口短名
// String newContent = assignRetMap.get("SVNcontent") + "";
// // 报错信息
// if (!IsEmptyUtils.isEmpty((List) assignRetMap.get("exception"))) {
// exceptions.addAll((List<String>) assignRetMap.get("exception"));
// }
// // 请求列表
// if (!IsEmptyUtils.isEmpty((List<String>) assignRetMap.get("urls"))) {
// urls.addAll((List<String>) assignRetMap.get("urls"));
// }
//
// Map<String, String> sqlData = new HashMap<String, String>();
// sqlData.put("dataId", svnDate + appkey);// 数据主键
// sqlData.put("appkey", appkey);
// sqlData.put("methods", methods.toString());
// sqlData.put("content", appkeyContent);
// sqlData.put("newContent", newContent);
// sqlData.put("methodCodes", assignRetMap.get("methodCodes") + "");
// sqlData.put("dataType", dataType);
// sqlData.put("assignMethods", assignRetMap.get("assignMethods") + "");
// SVNDao.insertSVNDate(sqlData);
// resultList.add(sqlData);
// // 将本地内容提交至SVN
// if (!newContent.equals(appkeyContent)) {// 接口赋权有更新
// PrintWriter pw = new PrintWriter(appFileName, "utf-8");
// pw.println(newContent);
// pw.flush();
// pw.close();
// if ("1".equals(dataType)) {
// SVNUtils.doCommit(exceptions, clientManager, appFileName, commitLog + "," + appkey);
// }
// postLog.add(appFileName.substring(appFileName.indexOf("config")));// 用于发送版本文件
// }
// else {
// System.out.println("appkey:" + appkey + "本次赋权无变更");
// }
// }
//
// if (!IsEmptyUtils.isEmpty(notHasList)) {
// System.err.println("下列接口配置文件未上传至生产config路径下,请确认!\n" + dealRepeat(notHasList));
// exceptions.add("下列接口配置文件未上传至生产config路径下,请确认!\n" + dealRepeat(notHasList));
// }
// if (!IsEmptyUtils.isEmpty(postLog)) {
// Map<String, String> sqlData = new HashMap<String, String>();
// sqlData.put("versonDate", svnDate);
// sqlData.put("postLog", postLog.toString());
// sqlData.put("dataType", dataType);
// sqlData.put("checkUrls", urls.toString());
// SVNDao.insertEmailData(sqlData);
// System.out.println("邮件内容:" + postLog);
// }
// System.err.println("1".equals(dataType) ? "此次为正式赋权,接口赋权内容已提交至SVN" : "此次为测试赋权,未将结果写入以及提交");
// System.out.println("====================生产验证的请求======================");
// System.out.println(urls);
// System.out.println("====================生产验证的请求======================");
// if (!IsEmptyUtils.isEmpty(exceptions)) {
// System.out.println(exceptions.toString());
// return new SVNResult(1, "赋权异常", resultList, exceptions, svnDate);
// }
// return new SVNResult(0, "赋权完成", resultList, exceptions, svnDate);
// }
// catch (Exception e) {
// exceptions.add(e.getMessage());
// e.printStackTrace();
// return new SVNResult(1, "赋权异常", resultList, exceptions, svnDate);
// }
// }
//
// /**
// * 2.去重
// * @param notHasList
// */
// public static List<String> dealRepeat(List<String> notHasList) {
// List<String> tempList = new ArrayList<String>();
// Iterator<String> it = notHasList.iterator();
// while (it.hasNext()) {
// String str = it.next();
// if (tempList.contains(str)) {
// it.remove();
// }
// else {
// tempList.add(str);
// }
// }
// return notHasList;
// }
//
// }
