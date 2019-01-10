package org.n3r.freemarker;

import org.n3r.core.lang.RDate;

@FreemarkerFunctionsTag("roc")
public class FreemarkerFunctions {

    public static String now(String... format) {
        return format.length == 0 ? "" + RDate.getTime() : RDate.currentTimeStr(format[0]);
    }

}
