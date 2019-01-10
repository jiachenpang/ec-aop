package org.n3r.spel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.n3r.core.collection.RMap;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import com.google.common.collect.Lists;

public class SpELTest {

    private ExpressionParser parser = new SpelExpressionParser();

    class People {
        private String name;

        public People(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isMale(int sex) {
            return sex == 0 ? false : true;
        }

    }

    @Test
    public void testPlainText() {
        Expression exp = parser.parseExpression("'Hello World'");
        String message = (String) exp.getValue();
        Assert.assertEquals(message, "Hello World");
    }

    @Test
    public void testMethodInvocation() {
        Expression exp = parser.parseExpression("'Hello World'.concat('!')");
        String message = (String) exp.getValue();
        Assert.assertEquals(message, "Hello World!");

        Expression exp2 = parser.parseExpression("isMale(1)");
        People people = new People("Garfield");
        Boolean isMale = exp2.getValue(people, Boolean.class);
        Assert.assertTrue(isMale);
    }

    @Test
    public void testOperator() {
        Expression relationalExp = parser.parseExpression("2 == 2");
        Boolean result = relationalExp.getValue(Boolean.class);
        Assert.assertTrue(result);

        Expression logicalExp = parser.parseExpression("true or false");
        result = logicalExp.getValue(Boolean.class);
        Assert.assertTrue(result);

        Expression mathematicalExp = parser.parseExpression("1 + 1");
        int value = mathematicalExp.getValue(Integer.class);
        Assert.assertEquals(value, 2);

        People people = new People(null);
        Expression elvis = parser.parseExpression("name?:'unknow'");
        String name = elvis.getValue(people, String.class);
        Assert.assertEquals(name, "unknow");

        People people2 = new People("sweety");
        Expression ternary = parser.parseExpression("name == 'sweety' ? 'honey' : 'stranger'");
        String nickName = ternary.getValue(people2, String.class);
        Assert.assertEquals(nickName, "honey");
    }

    @Test
    public void testEvaluatedFormBean() {
        Expression exp = parser.parseExpression("name");

        People summer = new People("summer");

        EvaluationContext context = new StandardEvaluationContext(summer);
        String name = (String) exp.getValue(context);
        Assert.assertEquals(name, "summer");

        summer.setName("holiday");
        name = (String) exp.getValue(summer);
        Assert.assertEquals(name, "holiday");

        Expression exp2 = parser.parseExpression("name == 'holiday'");
        boolean result = exp2.getValue(summer, Boolean.class);
        Assert.assertTrue(result);
    }

    @Test
    public void testEvaluatedFormMap() {
        Expression exp = parser.parseExpression("['key1']");

        Map map = RMap.of("key1", "value1", "key2", "value2", "key3", "value3");
        String value = (String) exp.getValue(map);
        Assert.assertEquals(value, "value1");

        // Nested Map
        Expression exp2 = parser.parseExpression("['map2']['map3']['map3key1']");
        Map map2 = RMap.of("map2key1", "map2value1");
        Map map3 = RMap.of("map3key1", "map3value1");
        map2.put("map3", map3);
        map.put("map2", map2);
        value = (String) exp2.getValue(map);
        Assert.assertEquals(value, "map3value1");

        Expression exp3 = parser.parseExpression("['map2']['list'][1]");
        List list = new ArrayList();
        list.add("element1");
        list.add("element2");
        map2.put("list", list);
        value = (String) exp3.getValue(map);
        Assert.assertEquals(value, "element2");
    }

    @Test
    public void testEvaluatedFormList() {
        Expression exp = parser.parseExpression("[1]");

        List<String> list = Lists.newArrayList("1", "2", "3", "4");
        String value = (String) exp.getValue(list);
        Assert.assertEquals(value, "2");

        Expression exp2 = parser.parseExpression("[1].name");
        People summer1 = new People("tom");
        People summer2 = new People("jetty");
        People summer3 = new People("kitty");
        List<People> list2 = Lists.newArrayList(summer1, summer2, summer3);
        String value2 = (String) exp2.getValue(list2);
        Assert.assertEquals(value2, "jetty");
    }

    @Test
    public void testAssignment() {
        People people = new People("poker");
        Expression exp = parser.parseExpression("name");
        exp.setValue(people, "face");
        Assert.assertEquals(people.getName(), "face");
    }

    @Test
    public void testVariables() {
        People people = new People("poker");

        StandardEvaluationContext context = new StandardEvaluationContext(people);
        context.setVariable("newName", "face");
        Expression exp = parser.parseExpression("name=#newName");
        exp.getValue(context);

        Assert.assertEquals(people.getName(), "face");
    }

    @Test
    public void testSharpSymbol() {
        List<Integer> primes = new ArrayList<Integer>();
        primes.addAll(Arrays.asList(2, 3, 5, 7, 11, 13, 17));

        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("primes", primes);

        @SuppressWarnings("unchecked" )
        List<Integer> primesGreaterThanTen = (List<Integer>) parser.parseExpression("#primes.?[#this > 10]").getValue(
                context);

        Assert.assertTrue(primesGreaterThanTen.size() == 3);
    }

    static class StringUtils {
        public static String reverseString(String input) {
            StringBuilder backwards = new StringBuilder();
            for (int i = 0; i < input.length(); i++)
                backwards.append(input.charAt(input.length() - 1 - i));
            return backwards.toString();
        }
    }

    @Test
    public void testRegisterFunction() throws SecurityException, NoSuchMethodException {
        StandardEvaluationContext context = new StandardEvaluationContext();

        context.registerFunction("reverseString",
                StringUtils.class.getDeclaredMethod("reverseString",
                        new Class[] { String.class }));

        String helloWorldReversed =
                parser.parseExpression("#reverseString('hello')").getValue(context, String.class);

        Assert.assertEquals("olleh", helloWorldReversed);
    }

}
