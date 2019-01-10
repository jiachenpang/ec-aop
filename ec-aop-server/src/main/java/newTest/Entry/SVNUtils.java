// package newTest.Entry;
//
// import java.io.ByteArrayOutputStream;
// import java.io.File;
// import java.nio.charset.Charset;
// import java.text.ParseException;
// import java.text.SimpleDateFormat;
// import java.util.ArrayList;
// import java.util.Calendar;
// import java.util.Date;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;
//
// import org.apache.commons.httpclient.HttpClient;
// import org.apache.commons.httpclient.methods.GetMethod;
// import org.apache.poi.ss.usermodel.Cell;
// import org.apache.poi.ss.usermodel.Row;
// import org.apache.poi.ss.usermodel.Sheet;
// import org.tmatesoft.svn.core.SVNDepth;
// import org.tmatesoft.svn.core.SVNException;
// import org.tmatesoft.svn.core.SVNNodeKind;
// import org.tmatesoft.svn.core.SVNProperties;
// import org.tmatesoft.svn.core.SVNProperty;
// import org.tmatesoft.svn.core.SVNURL;
// import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
// import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
// import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
// import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
// import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
// import org.tmatesoft.svn.core.io.SVNRepository;
// import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
// import org.tmatesoft.svn.core.wc.ISVNOptions;
// import org.tmatesoft.svn.core.wc.SVNClientManager;
// import org.tmatesoft.svn.core.wc.SVNRevision;
// import org.tmatesoft.svn.core.wc.SVNStatus;
// import org.tmatesoft.svn.core.wc.SVNStatusType;
// import org.tmatesoft.svn.core.wc.SVNUpdateClient;
// import org.tmatesoft.svn.core.wc.SVNWCUtil;
//
// import com.ailk.ecaop.common.utils.GetDateUtils;
// import com.ailk.ecaop.common.utils.IsEmptyUtils;
// import com.alibaba.fastjson.JSON;
//
// /**
// * 版本自动赋权所需工具
// * @author AllenWang
// */
// public class SVNUtils {
//
// static int MAX_LINE_COUNT = 120; // 120个字符换行
// static int account = 0;
//
// // 变更内容对应的key...
// private static String CHANGE_CONTENT = "接口赋权";
//
// /**
// * 0.获取SVN管理
// * @return
// */
// public static SVNClientManager getSVNClientManager() {
// SVNRepositoryFactoryImpl.setup();
// ISVNOptions options = SVNWCUtil.createDefaultOptions(true);
// // 实例化客户端管理类
// return SVNClientManager.newInstance((DefaultSVNOptions) options, SomePath.userName, SomePath.password);
// }
//
// /**
// * 1.自动获取到版本文件的路径
// * @param versonDate 用于手动选择赋权日期
// * @return
// */
// public static String getFilePathUtil(String versonDate) {
// Calendar calendar = Calendar.getInstance();
// int month = calendar.get(Calendar.MONTH) + 1;
// int day = calendar.get(Calendar.DAY_OF_MONTH);
// int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 5;// 默认每周四
// day = (dayOfWeek < 0) ? (day - dayOfWeek - 7) : (day - dayOfWeek);
// if (day <= 0) {
// month--;
// calendar.set(Calendar.MONTH, month);
// day = calendar.getActualMaximum(Calendar.DAY_OF_MONTH) - day;
// }
// String year = calendar.get(Calendar.YEAR) + "";
//
// // 增加用于手动选择赋权日期的方法
// if (!IsEmptyUtils.isEmpty(versonDate)) {
// System.out.println("手动选择的版本日期为:" + versonDate);
// year = versonDate.substring(0, 4);
// month = Integer.parseInt(versonDate.substring(4, 6));
// day = Integer.parseInt(versonDate.substring(6));
// }
//
// String quarter = "";
// switch ((int) (month / 3.1)) {
// case 0:
// quarter = "第一季度";
// break;
// case 1:
// quarter = "第二季度";
// break;
// case 2:
// quarter = "第三季度";
// break;
// case 3:
// quarter = "第四季度";
// break;
// }
// return year + " " + quarter + File.separator + month + "月" + day + "日aop上线内容.xlsx";
// // return "第四季度" + File.separator + 12 + "月" + 22 + "日aop上线内容.xlsx";
// }
//
// /**
// * 2. 获取版本Excel中的appkey与method的对应关系,并将需要赋权的接口以Map<appkey,methodList>形式返回
// * @param sheet
// * @return
// * @throws Exception
// */
// public static Map getVersonContent(Sheet sheet) throws Exception {
// // System.out.println("<---版本文件解析步骤进行中--->\n");
// int appkeyCol = -1;
// int methodCol = -1;
// int addCol = -1;
// int dealTypeCol = -1;
// String lastAppkey = null;
// Map<String, List> contentMap = new HashMap<String, List>();
// List<String> methodList = new ArrayList<String>();
// for (int j = 0; j <= sheet.getLastRowNum(); j++) {
// Row row = sheet.getRow(j);
// // Excel第一行为目录行,获取到appkey和method所在的列号
// if (j == 0) {
// for (int i = 0; i < row.getLastCellNum(); i++) {
// Cell cell = row.getCell(i);
// cell.setCellType(Cell.CELL_TYPE_STRING);
// String cellName = cell.getStringCellValue();
// if ("接入方编码".equals(cellName)) {
// appkeyCol = i;
// }
// else if ("接口名".equals(cellName)) {
// methodCol = i;
// }
// else if ("变更内容".equals(cellName)) {
// addCol = i;
// }
// else if ("分类".equals(cellName)) {
// dealTypeCol = i;
// }
// }
// continue;
// }
// // 获取类别,只处理需求类型
// String dealType = getCellStringValue(row, dealTypeCol);
// if (!IsEmptyUtils.isEmpty(dealType) && "bug|优化".contains(dealType)) {
// break;
// }
// // 获取appkey
// String appkey = getCellStringValue(row, appkeyCol);
// // 获取method
// String method = getCellStringValue(row, methodCol);
// if (method.contains("（")) {
// method = method.substring(0, method.indexOf("（"));
// }
// // 获取变更状态
// String add = getCellStringValue(row, addCol);
// if (IsEmptyUtils.isEmpty(add)) {
// throw new Exception("第" + (j + 1) + "行[变更内容]为空,请检查版本文件!");
// }
// if (j == 1) {
// lastAppkey = appkey;
// if (CHANGE_CONTENT.equals(add))
// methodList.add(method);
// continue;
// }
// // 如果不是新的appkey,将method放入list
// if (IsEmptyUtils.isEmpty(appkey)) {
// if (IsEmptyUtils.isEmpty(method)) {
// continue;
// }
// if (CHANGE_CONTENT.equals(add)) {
// methodList.add(method);
// }
// continue;
// }
// else if (appkey.equals(lastAppkey)) {// 如果这个appkey是上一个
// if (CHANGE_CONTENT.equals(add)) {
// methodList.add(method);
// }
// }
// else if (!IsEmptyUtils.isEmpty(contentMap.get(appkey))) {// 或者之前有
// if (!IsEmptyUtils.isEmpty(methodList)) {
// contentMap.put(lastAppkey, methodList);
// }
// methodList = contentMap.get(appkey);
// contentMap.remove(appkey);
// if (CHANGE_CONTENT.equals(add)) {
// methodList.add(method);
// }
// lastAppkey = appkey;
// }
// else {
// // 如果是新的appkey,将之前的appkey和methodList放入Map,然后清空methodList
// if (!IsEmptyUtils.isEmpty(methodList)) {
// contentMap.put(lastAppkey, methodList);
// }
// methodList = new ArrayList<String>();
// if (CHANGE_CONTENT.equals(add)) {
// methodList.add(method);
// }
// lastAppkey = appkey;
// }
// }
// if (methodList.size() > 0) {
// List<String> methods = contentMap.get(lastAppkey);
// if (!IsEmptyUtils.isEmpty(methods)) {
// methodList.addAll(methods);
// }
// // // 如果有同一个key的情况,保证不覆盖
// // if (!IsEmptyUtils.isEmpty(contentMap.get(lastAppkey))) {
// // methodList.addAll(contentMap.get(lastAppkey));
// // }
// contentMap.put(lastAppkey, methodList);
// // System.out.println("版本文件中appkey:" + lastAppkey + "下的method:" + methodList + "需要新增赋权");
// }
// System.err.println("读取版本文件结束,需要赋权的接口method以及对应的appkey如下:");
// for (String key : contentMap.keySet()) {
// System.out.println(key + ":" + contentMap.get(key));
// }
// System.out.println();
// return contentMap;
// }
//
// /**
// * 3.更新本地SVN
// * @param clientManager
// * @param updateFilePath
// */
// public static void doUpdate(List<String> exceptions, SVNClientManager clientManager, String updateFilePath) {
// // System.err.println("\n<---SVN更新操作进行中--->\n更新路径为:" + updateFilePath + "\n");
// // 初始化支持svn://协议的库。 必须先执行此操作。
// long start = System.currentTimeMillis();
// SVNRepositoryFactoryImpl.setup();
// System.out.println("time1:" + (System.currentTimeMillis() - start));
// File updateFile = new File(updateFilePath);
// // 获得updateClient的实例
// SVNUpdateClient updateClient = clientManager.getUpdateClient();
// updateClient.setIgnoreExternals(false);
// System.out.println("time2:" + (System.currentTimeMillis() - start));
// // 执行更新操作
// // long versionNum = 0;
// try {
// // versionNum =
// updateClient.doUpdate(updateFile, SVNRevision.HEAD, SVNDepth.INFINITY, false, false);
// }
// catch (SVNException e) {
// e.printStackTrace();
// exceptions.add("更新文件出错:" + e.getMessage());
// }
// System.out.println("time3:" + (System.currentTimeMillis() - start));
// System.out.println("工作副本更新后的版本：");
// }
//
// /**
// * 调用aop获取配置的接口
// * @param getKey
// * @return
// * @throws Exception
// */
// public static List<Map> getConfigByAop(String getKey) throws Exception {
// String result = "";
// GetMethod postMethod = null;
// String postUrl = "";
// String apptx = "AOPTEST" + getSysDate();
// String timestamp = getSysDateFormat();
// Object msg = "{\"dealParas\":[{\"dealType\":\"GET_REDIS_CONTENT\",\"dealKey\":\"" + getKey + "\"}]}";
// try {
// postUrl = "http://132.35.81.217:8000/aop/test?apptx=" + apptx
// + "&appkey=wmctest.sub&method=ecaop.trades.query.ecaop.everything.qry&msg=" + msg + "&timestamp="
// + timestamp;
// postMethod = new GetMethod(postUrl);
// HttpClient client = new HttpClient();
// // client.getHttpConnectionManager().getParams().setConnectionTimeout(50000);// 设置连接时间
// client.executeMethod(postMethod);
// result = postMethod.getResponseBodyAsString();
// Map ret = JSON.parseObject(result);
// if (!"9999".equals(ret.get("code"))) {
// return (List<Map>) ret.get("data");
// }
// }
// catch (Exception e) {
// System.err.println("发送GET请求出现异常！" + e);
// e.printStackTrace();
// throw new Exception("获取" + getKey + "的值出错!apptx=" + apptx + "rsp:" + result);
// }
// // 使用finally块来关闭连接
// finally {
// postMethod.releaseConnection();
// }
// return new ArrayList<Map>();
// }
//
// /**
// * 4.对需赋权接口进行赋权
// * @param appkey
// * @param methods
// * @param SVNcontent
// * @return
// * @throws Exception
// */
// public static Map<String, Object> Assign(String appkey, List<String> methods, String SVNcontent) throws Exception {
// // System.err.println("\n<---接口赋权操作进行中--->\n");
// String methodCodes = "";// 赋权的接口短名
// List<String> assignMethods = new ArrayList<String>();
//
// String appCodekey = "ecaop.core.app.map." + appkey;
// String appCode = (String) getConfigByAop(appCodekey).get(0).get(appCodekey);
// String getMethodFlag = "appkey:\"" + appCode + "\",allow:\"";
// List<String> exceptions = new ArrayList<String>();
// if (!SVNcontent.contains(getMethodFlag)) {
// throw new Exception("appkey:" + appkey + "文件的内容格式有误,不包含[" + getMethodFlag + "],请检查!");
// }
// String methodCode;
// List<String> dealRet = new ArrayList<String>();
//
// int begin = SVNcontent.indexOf(getMethodFlag) + getMethodFlag.length();
// String startSVNcontent = SVNcontent.substring(0, begin);
// SVNcontent = SVNcontent.substring(begin);
// int end = SVNcontent.indexOf("\"");
// String endSVNcontent = SVNcontent.substring(end);
// SVNcontent = SVNcontent.substring(0, end);
// SVNcontent = SVNcontent.replaceAll("[\\\\\\s]", "");
// List<String> urls = new ArrayList<String>();
// int i = 0;
//
// for (String method : methods) {
// String methodCodeKey = "ecaop.core.method.map." + method;
// methodCode = (String) getConfigByAop(methodCodeKey).get(0).get(methodCodeKey);
// if (null == methodCode) {
// dealRet.add("Method:[" + method + "]不存在或未定义,请进行检查.");
// exceptions.add(appkey + "下的Method:[" + method + "]不存在或未定义,请进行检查.");
// continue;
// }
// if (SVNcontent.contains("," + methodCode) || SVNcontent.contains(methodCode + ",")) {
// dealRet.add("Method:[" + method + "]已存在权限,未进行重复分配.");
// }
// else {
// SVNcontent += "".equals(SVNcontent) ? methodCode : "," + methodCode;
// dealRet.add("Method:[" + method + "]不存在权限,已经分配.短名为:" + methodCode + ".");
// methodCodes += methodCode + ",";
// assignMethods.add(method);
// }
//
// // 拼装请求
// String urlStr = "http://132.35.88.104/aop/aopservlet?timestamp=" + GetDateUtils.getNextMonthLastDayFormat()
// + "&apptx=" + GetDateUtils.getDate() + "00" + i++ + "&appkey=" + appkey + "&method=" + method
// + "&msg={}";
// urls.add(urlStr);
// }
// System.out.println("===Method权限校验结果如下===");
// for (String s : dealRet) {
// System.out.println(s);
// }
// // System.out.println("经过赋权之后" + appkey + "拥有的权限如下:");
// int length = SVNcontent.length();
// int index = 0;
// if (length > MAX_LINE_COUNT - 50) {
// index = SVNcontent.substring(0, MAX_LINE_COUNT - 50).lastIndexOf(",");
// startSVNcontent += SVNcontent.substring(0, index + 1) + "\\\n";
// SVNcontent = SVNcontent.substring(index + 1, length);
// length -= index + 1;
// }
// while (length > MAX_LINE_COUNT) {
// index = SVNcontent.substring(0, MAX_LINE_COUNT).lastIndexOf(",");
// startSVNcontent += SVNcontent.substring(0, index + 1) + "\\\n";
// SVNcontent = SVNcontent.substring(index + 1, length);
// length -= index + 1;
// }
// startSVNcontent += SVNcontent;
// // System.err.println(startSVNcontent + endSVNcontent);
//
// Map<String, Object> retMap = new HashMap<String, Object>();
// retMap.put("SVNcontent", startSVNcontent + endSVNcontent);
// retMap.put("methodCodes", methodCodes);
// retMap.put("assignMethods", assignMethods);
// retMap.put("urls", urls);
// if (!IsEmptyUtils.isEmpty(exceptions)) {
// retMap.put("exception", exceptions);
// }
// return retMap;
// }
//
// /**
// * 5.读取SVN上的appkey下的内容并返回
// * @param url
// * @param name
// * @param password
// * @param filePath
// * @param exceptions
// * @return
// */
// public static String getSVNCofig(String url, String name, String password, String filePath, List<String> exceptions)
// {
// // System.err.println("\n<---SVN文件读取操作进行中--->\n");
// // 初始化库。 必须先执行此操作。具体操作封装在setupLibrary方法中。
// setupLibrary();
// // 定义svn版本库的URL。
// SVNURL repositoryURL = null;
// // 定义版本库。
// SVNRepository repository = null;
// try {
// // 获取SVN的URL。
// repositoryURL = SVNURL.parseURIEncoded(url);
// // 根据URL实例化SVN版本库。
// repository = SVNRepositoryFactory.create(repositoryURL);
// }
// catch (SVNException svne) {
// /* 打印版本库实例创建失败的异常。 */
// String exception = "创建版本库实例时失败，版本库的URL是 '" + url + "': " + svne.getMessage();
// System.err.println(exception);
// exceptions.add(exception);
// // System.exit(1);
// }
// /* 对版本库设置认证信息。 */
// @SuppressWarnings("deprecation")
// ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(name, password);
// repository.setAuthenticationManager(authManager);
//
// // 此变量用来存放要查看的文件的属性名/属性值列表。
// SVNProperties fileProperties = new SVNProperties();
// // 此输出流用来存放要查看的文件的内容。
// ByteArrayOutputStream baos = new ByteArrayOutputStream();
// try {
// // 获得版本库中文件的类型状态（是否存在、是目录还是文件），参数-1表示是版本库中的最新版本。
// SVNNodeKind nodeKind = repository.checkPath(filePath, -1);
// if (nodeKind == SVNNodeKind.NONE) {
// String exception = "要查看的文件在 '" + url + "'中不存在.";
// System.err.println(exception);
// exceptions.add(exception);
// // System.exit(1);
// }
// else if (nodeKind == SVNNodeKind.DIR) {
// String exception = "要查看对应版本的条目在 '" + url + "'中是一个目录.";
// System.err.println(exception);
// exceptions.add(exception);
// // System.exit(1);
// }
// // 获取要查看文件的内容和属性，结果保存在baos和fileProperties变量中。
// repository.getFile(filePath, -1, fileProperties, baos);
// }
// catch (SVNException svne) {
// String exception = "在获取文件内容和属性时发生错误: " + svne.getMessage();
// System.err.println(exception);
// exceptions.add(exception);
// // System.exit(1);
// }
//
// // 获取文件的mime-type
// String mimeType = fileProperties.getStringValue(SVNProperty.MIME_TYPE);
// // 判断此文件是否是文本文件
// boolean isTextType = SVNProperty.isTextMimeType(mimeType);
// /* 如果文件是文本类型，则把文件的内容显示到控制台。 */
// String content = "";
// if (isTextType) {
// try {
// content = new String(baos.toByteArray(), Charset.forName("utf-8"));// baos.toString("utf-8");
// }
// catch (Exception ioe) {
// ioe.printStackTrace();
// }
// }
// else {
// String exception = "因为文件不是文本文件，无法显示！";
// System.err.println(exception);
// exceptions.add(exception);
// }
// return content;
// }
//
// /* 初始化库 */
// private static void setupLibrary() {
// /* For using over http:// and https:// */
// DAVRepositoryFactory.setup();
// /* For using over svn:// and svn+xxx:// */
// SVNRepositoryFactoryImpl.setup();
// /* For using over file:/// */
// FSRepositoryFactory.setup();
// }
//
// /**
// * 6.将本地文件提交至SVN
// * @param clientManager
// * @param commitFilePath
// * @param commitLog
// */
// public static void doCommit(List<String> exceptions, SVNClientManager clientManager, String commitFilePath,
// String commitLog) {
// System.out.println("\n<---提交SVN操作进行中--->\n");
// // 初始化支持svn://协议的库
// SVNRepositoryFactoryImpl.setup();
// // 要提交的文件
// File commitFile = new File(commitFilePath);
// // 获取此文件的状态（是文件做了修改还是新添加的文件？）
// SVNStatus status = null;
// try {
// status = clientManager.getStatusClient().doStatus(commitFile, true);
// // 如果此文件是新增加的则先把此文件添加到版本库，然后提交。
// if (status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED) {
// // 把此文件增加到版本库中
// clientManager.getWCClient().doAdd(commitFile, false, false, false, SVNDepth.INFINITY, false, false);
// // 提交此文件
// clientManager.getCommitClient().doCommit(new File[] { commitFile }, true, "", null, null, true, false,
// SVNDepth.INFINITY);
// System.out.println("提交步骤-->该文件为新增文件,已新增并提交");
// }
// // 如果此文件不是新增加的，直接提交。
// else {
// clientManager.getCommitClient().doCommit(new File[] { commitFile }, true, commitLog, null, null, true,
// false, SVNDepth.INFINITY);
// System.out.println("提交步骤-->文件已提交");
// }
// }
// catch (SVNException e) {
// e.printStackTrace();
// exceptions.add(commitFilePath.substring(commitFilePath.lastIndexOf("\\") + 1) + "文件提交失败,请查看,异常信息:"
// + e.getMessage());
// }
// System.out.println("提交步骤-->提交文件的状态为:" + status.getContentsStatus());
// }
//
// /**
// * 7.校验该接口的props是否上传到了生产环境
// * @param methods
// * @param path
// * @return
// */
// public static Map<String, List<String>> methodChecker(List<String> methods) {
// String path =
// "E:\\ESS-AOP\\trunk\\Code\\ESS-AOP\\N3Prod\\config\\ec-aop-server\\production\\proconfig\\ecaop\\trades\\method";
// String methodNames = getAllMethodName(new File(path));
// String needUpload = "false";
// String needConfigFlag = "configFalse";
// Map<String, List<String>> retMap = new HashMap<String, List<String>>();
// List<String> methodList = new ArrayList<String>();
// List<String> needConfig = new ArrayList<String>();
// for (String method : methods) {
// method = method.replaceAll("ecaop.", "");
// method = method.replaceAll("trades.", "");
// if (!methodNames.contains(method) && (!"query.resi.term.chg".equals(method))) {
// needUpload = "true";
// methodList.add(method);
// }
// // 人脸识别和国政通校验需要增加配置
// if ("query.comm.cert.check".equals(method) || "query.comm.face.check".equals(method)) {
// needConfigFlag = "configTrue";
// needConfig.add(method);
// }
// }
// retMap.put(needUpload, methodList);
// retMap.put(needConfigFlag, needConfig);
// return retMap;
// }
//
// /**
// * 获取目录下的所有method
// * @param method
// * @param path
// * @return
// */
// public static String getAllMethodName(File file) {
// String fileName = "";
// if (file.isDirectory()) {
// File[] files = file.listFiles();
// for (File listFile : files) {
// fileName += getAllMethodName(listFile);
// }
// }
// else {
// fileName += file.getName() + ",";
// account++;
// }
// return fileName;
// }
//
// /**
// * 确定一个URL在SVN上是否存在
// * @param url
// * @return
// */
// @SuppressWarnings("deprecation")
// public static boolean isURLExist(SVNURL url, String username, String password) {
// try {
// SVNRepository svnRepository = SVNRepositoryFactory.create(url);
// ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(username, password);
// svnRepository.setAuthenticationManager(authManager);
// SVNNodeKind nodeKind = svnRepository.checkPath("", -1);
// return nodeKind == SVNNodeKind.NONE ? false : true;
// }
// catch (SVNException e) {
// e.printStackTrace();
// }
// return false;
// }
//
// /**
// * 获取方格的字符串内容
// * @param row
// * @param cellNum
// * @return
// */
// private static String getCellStringValue(Row row, int cellNum) {
// Cell appCell = row.getCell(cellNum);
// appCell.setCellType(Cell.CELL_TYPE_STRING);
// return appCell.getStringCellValue().trim();
// }
//
// /**
// * 获取当前时间并格式化(yyyy-MM-dd HH:mm:ss)
// * @return
// */
// public static String getSysDateFormat() {
// SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
// return format.format(new Date());
// }
//
// /**
// * 获取当前时间
// * @return
// */
// public static String getSysDate() {
// SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmssSSS");
// return format.format(new Date());
// }
//
// /**
// * recursively checks out a working copy from url into wcDir
// * @param clientManager
// * @param url
// * a repository location from where a Working Copy will be checked out
// * @param revision
// * the desired revision of the Working Copy to be checked out
// * @param destPath
// * the local path where the Working Copy will be placed
// * @param depth
// * checkout的深度，目录、子目录、文件
// * @return
// * @throws SVNException
// */
// public static void checkout(SVNClientManager clientManager, String url, String workPath) throws SVNException {
// // 初始化支持svn://协议的库。 必须先执行此操作。
// SVNRepositoryFactoryImpl.setup();
// // 相关变量赋值
// SVNURL repositoryURL = null;
// try {
// repositoryURL = SVNURL.parseURIEncoded(url);
// }
// catch (SVNException e) {
// System.out.println("无法连接");
// }
// // 实例化客户端管理类
// // 要把版本库的内容check out到的目录
// File wcDir = new File(workPath);
// // 通过客户端管理类获得updateClient类的实例。
// SVNUpdateClient updateClient = clientManager.getUpdateClient();
// // sets externals not to be ignored during the checkout
// updateClient.setIgnoreExternals(false);
// // 执行check out 操作，返回工作副本的版本号。
// long workingVersion = updateClient.doCheckout(repositoryURL, wcDir, SVNRevision.HEAD, SVNRevision.HEAD,
// SVNDepth.INFINITY, false);
// System.out.println("把版本：" + workingVersion + " check out 到目录：" + wcDir + "中。");
// }
//
// /**
// * 转换日期格式
// * @param date
// * @return
// * @throws ParseException
// */
// public static String transDate(String date) throws ParseException {
// if (null == date || "".equals(date)) {
// return date;
// }
// SimpleDateFormat oldSdf = new SimpleDateFormat("yyyyMMdd");
// SimpleDateFormat newSdf = new SimpleDateFormat("yyyy-MM-dd");
// if (date.contains("-")) {
// oldSdf = new SimpleDateFormat("yyyy-MM-dd");
// newSdf = new SimpleDateFormat("yyyyMMdd");
// }
// return newSdf.format(oldSdf.parse(date));
// }
// }
