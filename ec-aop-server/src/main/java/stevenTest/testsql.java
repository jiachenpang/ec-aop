package stevenTest;

import com.ailk.ecaop.common.utils.CheckCertCodeUtils;

/**
 * @author May
 * @version 1.0.1
 * @since 2013-05-09 19:54
 *        宽带产品信息的处理
 *        报错的版本
 */
public class testsql {

    public static void main(String[] args) {
        String certCode = "37148119900904601x";
        CheckCertCodeUtils cccu = new CheckCertCodeUtils();
        cccu.CheckCertCode(certCode);
    }
}
