package com.ailk.ecaop.common.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;

public class DividePic2ParaUtil {

    String picStr, paraId = "PHOTO";
    int count = 10;

    public List<Map> divide(String pic) throws Exception {
        picStr = pic;
        if (null == picStr) {
            throw new EcAopServerBizException("9999", "照片信息不能为空！");
        }
        //替换base64码中的+
        picStr = picStr.replace(" ", "+");

        int picLen = picStr.length();
        List<Map> para = new ArrayList<Map>();

        for (int i = 0; i < count - 1; i++) {
            para.add(MapUtils.asMap(
                    "paraId",
                    paraId,
                    "paraValue",
                    picStr.substring(i * picLen / count, (i + 1) * picLen
                            / count)));
        }
        para.add(MapUtils.asMap("paraId", paraId, "paraValue",
                picStr.substring(picLen * (count - 1) / count, picLen)));

        return para;
    }

}
