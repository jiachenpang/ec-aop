package org.n3r.spel;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.n3r.core.collection.RMap;

public class SpELResolverTest {

    @Test
    public void testGetValue() {
        String expression = "['key1']";
        boolean compileResult = SpELResolver.compile(expression);
        Assert.assertTrue(compileResult);

        Map<String, String> context = RMap.of("key1", "value1", "key2", "value2");
        String value = SpELResolver.getValue(context, expression);
        Assert.assertEquals(value, "value1");
    }

}
