// package newTest.serviceImpl;
//
// import java.io.BufferedReader;
// import java.io.File;
// import java.io.FileReader;
// import java.io.IOException;
// import java.util.ArrayList;
// import java.util.List;
//
// import org.n3r.config.Config;
//
// /**
// * 通过接口名获取使用了该接口的系统appkey
// * @author Administrator
// */
// public class FindAppkeByMethod {
//
// // 生产地址
// private static String proPath =
// "E:\\ESS-AOP\\trunk\\Code\\ESS-AOP\\N3Prod\\config\\ec-aop-server\\production\\proconfig\\ecaop\\trades\\app";
//
// // 测试地址
// private static String path =
// "E:\\ESS-AOP\\branches\\Code\\ESS-AOP\\N3\\ec-aop-server\\src\\main\\resources\\devconfig\\ecaop\\trades\\app";
//
// public static void main(String[] args) {
// // System.out.println(Config.getProperties());
// String[] methods = new String[] { "ecaop.trades.query.comm.purchase" };
// List<List<String>> appkeyList = new ArrayList<List<String>>();
// for (String method : methods) {
// List<String> appkeys = getAllMethodName(new File(path), method);
// appkeyList.add(appkeys);
// System.err.println(appkeys);
// }
// // for (int i = 0; i < appkeyList.size(); i++) {
// // List<String> appkeys = appkeyList.get(i);
// // String appkey = appkeys.get(i);
// // }
//
// }
//
// /**
// * 获取目录下的所有method
// * @param method
// * @param path
// * @return
// */
// public static List<String> getAllMethodName(File file, String method) {
// File[] files = file.listFiles();
// List<String> appkeys = new ArrayList<String>();
// String methodCode = Config.getStr("ecaop.core.method.map." + method);
// for (File readFile : files) {
// if (readFile.isFile()) {
// BufferedReader reader = null;
// try {
// reader = new BufferedReader(new FileReader(readFile));
// String tempString = null;
// String str = "";
// while ((tempString = reader.readLine()) != null) {
// str += tempString;
// }
// reader.close();
// if (str.contains(methodCode)) {
// System.out.println("使用了该接口的appkey的内容:" + str);
// appkeys.add(readFile.getName().replaceAll(".props", ""));
// }
// }
// catch (IOException e) {
// e.printStackTrace();
// }
// finally {
// if (reader != null) {
// try {
// reader.close();
// }
// catch (IOException e1) {
// }
// }
// }
// }
// }
// return appkeys;
// }
// }
