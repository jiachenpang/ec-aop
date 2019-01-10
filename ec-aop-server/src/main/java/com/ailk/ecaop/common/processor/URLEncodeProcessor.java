package com.ailk.ecaop.common.processor;

import java.net.URLEncoder;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.exception.request.EcAopRequestBizException;


@EcRocTag("urlEncode")
public class URLEncodeProcessor extends URLCodeProcessor {

    @Override
    public String doCode(String con) {

        try {
            return URLEncoder.encode(con, "UTF-8");
        } catch (Exception e) {
            throw new EcAopRequestBizException("Can't parse Encode str", e);
        }

    }
}
