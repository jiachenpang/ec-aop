// package newTest.serviceImpl;
//
// import java.io.File;
// import java.io.FileInputStream;
// import java.util.ArrayList;
// import java.util.List;
//
// import newTest.Entry.Bizkey;
// import newTest.Entry.Method;
// import newTest.Entry.SVNResult;
// import newTest.Entry.SVNUtils;
// import newTest.Entry.SomePath;
//
// import org.apache.poi.ss.usermodel.Cell;
// import org.apache.poi.ss.usermodel.Row;
// import org.apache.poi.ss.usermodel.Sheet;
// import org.apache.poi.xssf.usermodel.XSSFWorkbook;
// import org.n3r.config.Config;
//
// import com.ailk.ecaop.common.utils.IsEmptyUtils;
//
// /**
// * 通过method名获取其所在的所有bizkey,并返回
// * @author AllenWang
// */
// public class FindBizkeyByMethod {
//
// /**
// * 通过method名获取其所在的所有bizkey,并返回
// * @param methodstr-接口名列表
// * @return
// * @throws Exception
// */
// public SVNResult findBizKey(String methodstr) {
// if (IsEmptyUtils.isEmpty(methodstr)) {
// return null;
// }
// List<String> exception = new ArrayList<String>();
// try {
// String[] methods = methodstr.trim().split(",");
// System.out.println("正在获取bizkey路径...");
// String excelPath = getBizkeyExcelPath();
// System.out.println("获取到的bizkey路径为:" + excelPath + "\n正在获取bizkey的信息...");
// List<Bizkey> bizkeys = getAllBizkey(excelPath);
// System.out.println("获取到的bizkey信息为:" + bizkeys);
// List<List<Bizkey>> retList = new ArrayList<List<Bizkey>>();
//
// for (int i = 0; i < methods.length; i++) {
// String method = methods[i];
// for (Bizkey bizkey : bizkeys) {
// List<Method> methodList = bizkey.getAllowMethods();
// for (Method bizMethod : methodList) {
// if (method.equals(bizMethod.getMethod())) {
// List<Bizkey> retBizkey = (IsEmptyUtils.isEmpty(retList) || retList.get(i) == null) ? new ArrayList<Bizkey>()
// : retList.get(i);
// retBizkey.add(bizkey);
// retList.add(retBizkey);
// break;
// }
// }
// }
//
// }
// System.out.println("对应的bizkey为:" + retList);
// return new SVNResult(SVNResult.SUCCESS_CODE, "查询成功", retList, null, "");
// }
// catch (Exception e) {
// e.printStackTrace();
// exception.add(e.getMessage());
// }
// return new SVNResult(SVNResult.ERROR_CODE, "查询异常", exception, null, "");
// }
//
// /**
// * 1,获取最新的bizkey关系文件路径
// * @return
// * @throws Exception
// */
// private String getBizkeyExcelPath() throws Exception {
// SVNUtils.doUpdate(new ArrayList<String>(), SVNUtils.getSVNClientManager(), SomePath.bizKeyExcel);
// File file = new File(SomePath.bizKeyExcel);
// File[] files = null;
// if (file.isDirectory()) {
// files = file.listFiles();
// }
// if (null == files) {
// return "";
// }
// String lastFileName = "19930315";
// String fileName = "业务流程梳理关系.xlsx";
// for (File tempFile : files) {
// if (tempFile.getName().indexOf("-") < 0) {
// continue;
// }
// String FileTime = tempFile.getName().substring(tempFile.getName().indexOf("-") + 1,
// tempFile.getName().indexOf(".xlsx"));
// if (lastFileName.compareTo(FileTime) < 0) {
// fileName = tempFile.getName();
// }
// }
// return SomePath.bizKeyExcel + fileName;
// }
//
// /**
// * 2,获取到所有的bizkey
// * @param bizkeyPath-业务流程梳理关系路径
// * @return
// * @throws Exception
// */
// private List<Bizkey> getAllBizkey(String bizkeyPath) throws Exception {
// XSSFWorkbook work = new XSSFWorkbook(new FileInputStream(bizkeyPath));
// Sheet sheet = work.getSheetAt(0);
// List<Bizkey> bizkeys = new ArrayList<Bizkey>();
// Bizkey bizkey = null;
//
// String lastBizkey = "begin";
// int rowNum = -1;
//
// for (Row row : sheet) {
// try {
// if (IsEmptyUtils.isEmpty(row)) {
// continue;
// }
// rowNum = row.getRowNum();
// if (rowNum == 0) { // 第一行为标题栏,不读取
// continue;
// }
// String biz = getCellString(row.getCell(1));
// if (!IsEmptyUtils.isEmpty(biz) && !biz.equals(lastBizkey)) {// 如果是回一个新的bizkey则要获取其固定的值
// if (!IsEmptyUtils.isEmpty(bizkey)) {// 将上一个bizkey的实例放入集合中
// bizkeys.add(bizkey);
// }
// // 开始放新的bizkey
// bizkey = new Bizkey();
// bizkey.setBizkey(biz);
// bizkey.setBizName(getCellString(row.getCell(2)));
// bizkey.setExplain(getCellString(row.getCell(3)));
// bizkey.setAllowMethodCodes(Config.getStr("ecaop.core.biz." + bizkey.getBizkey() + ".def"));
// // 新的方式,只获取bizkey下面的method短名来判断接口所属的bizkey
// lastBizkey = biz;
// }
// // 获取这一行的method参数,放到bizkey的method成员变量中
// Method method = new Method();
// method.setMethod(getCellString(row.getCell(4)));
// if (IsEmptyUtils.isEmpty(method.getMethod())) {// 空行略过
// continue;
// }
// method.setMethodName(getCellString(row.getCell(5)));
// method.setMethodCode(Config.getStr("ecaop.core.method.map." + method.getMethod()));
// List<Method> methods = null;
// if (IsEmptyUtils.isEmpty(bizkey.getAllowMethods())) {
// methods = new ArrayList<Method>();
// }
// else {
// methods = bizkey.getAllowMethods();
// }
// methods.add(method);
// bizkey.setAllowMethods(methods);
// }
// catch (Exception e) {
// if (e.getMessage().contains("END")) {
// bizkeys.add(bizkey);
// }
// else {
// System.out.println(bizkeys);
// throw new Exception("第" + (rowNum + 1) + "中有" + e.getMessage());
// }
// }
// }
// // System.out.println(bizkeys);
// return bizkeys;
// }
//
// /**
// * 获取格子中的内容
// * @param cell
// * @return
// * @throws Exception
// */
// private String getCellString(Cell cell) throws Exception {
// if (IsEmptyUtils.isEmpty(cell)) {// 如果cell为空证明已读取结束
// throw new Exception("END");
// }
// cell.setCellType(Cell.CELL_TYPE_STRING);
// return cell.getStringCellValue();
// }
//
// public static void main(String[] args) throws Exception {
// SVNResult result = new FindBizkeyByMethod().findBizKey("ecaop.trades.syn.orderinfo.sub");
// System.out.println("结果为" + result.getData());
// // System.out.println(Config.getStr("ecaop.core.method.map." + "ecaop.trades.query.comm.user.activity.qry"));
// }
// }
