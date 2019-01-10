// package newTest.main;
//
// import java.io.IOException;
// import java.io.PrintWriter;
// import java.text.ParseException;
// import java.util.ArrayList;
// import java.util.List;
//
// import javax.servlet.http.HttpServlet;
// import javax.servlet.http.HttpServletRequest;
// import javax.servlet.http.HttpServletResponse;
//
// import newTest.Entry.SVNResult;
// import newTest.Entry.SVNUtils;
// import newTest.Entry.SomePath;
// import newTest.serviceImpl.AutoSVNCommit;
// import newTest.serviceImpl.FindBizkeyByMethod;
//
// import com.alibaba.fastjson.JSONObject;
//
// /**
// * 处理一些需要在server工程中的处理的需求
// * @author wangmc
// */
// public class SvnServlet extends HttpServlet {
//
// /**
// *
// */
// private static final long serialVersionUID = 1L;
// static {
// List<String> exceptions = new ArrayList<String>();
// SVNUtils.doUpdate(exceptions, SVNUtils.getSVNClientManager(), SomePath.versonPath);
// if (exceptions.size() > 0) {
// System.err.println("更新svn内容出错!");
// }
// }
//
// @Override
// protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
// request.setCharacterEncoding("utf-8");
// response.setContentType("text/json;charset=utf-8");
// System.out.println("赋权");
//
// String uri = request.getRequestURI().trim().replaceAll("/aop/SvnCommit/", "");
// String dataType = request.getParameter("dataType");
// String svnDate = request.getParameter("svnDate");
// String versonDate = request.getParameter("versonDate");
// String methods = request.getParameter("methods");
// SVNResult result = null;
//
// try {
// svnDate = SVNUtils.transDate(svnDate);
// versonDate = SVNUtils.transDate(versonDate);
// }
// catch (ParseException e) {
// e.printStackTrace();
// }
// System.out.println("uri:" + uri + ",dataType:" + dataType + ",svnDate:" + svnDate + ",versonDate:" + versonDate
// + ",methods:" + methods);
// if ("svnCommit".equals(uri)) {// 赋权
// result = AutoSVNCommit.autoCommit(dataType, svnDate, versonDate);
// System.out.println("赋权内容为:" + result);
// }
// else if ("selectBizKey".equals(uri)) { // 查找bizkey
// result = new FindBizkeyByMethod().findBizKey(methods);
// }
//
// JSONObject json = new JSONObject();
// json.put("result", result);
// PrintWriter pw = response.getWriter();
// pw.println(json);
// pw.flush();
// pw.close();
// }
// }
