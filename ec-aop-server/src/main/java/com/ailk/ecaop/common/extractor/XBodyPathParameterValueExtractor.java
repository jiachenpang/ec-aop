package com.ailk.ecaop.common.extractor;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Node;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.ParameterValueExtractor;

@EcRocTag("XBodyPath")
public class XBodyPathParameterValueExtractor implements ParamsAppliable, ParameterValueExtractor {

    private String xpathExpression;

    @Override
    public Object extract(Exchange exchange) {

        Document document;
        String extractValue = null;
        try {
            String text = exchange.getIn().getBody(String.class);
            document = DocumentHelper.parseText(text);
            text = document.getRootElement().element("SvcCont").getText();
            document = DocumentHelper.parseText(text);
            Node selectSingleNode;
            if (null != document.selectSingleNode(xpathExpression)) {
                selectSingleNode = document.selectSingleNode(xpathExpression);
                extractValue = selectSingleNode.getStringValue();
            }
        }
        catch (DocumentException e) {
            exchange.setException(e);
        }

        return extractValue;
    }

    @Override
    public void applyParams(String[] params) {
        this.xpathExpression = params[0];
    }

}
