package org.n3r.spel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public class SpELResolver {
    private static final Logger logger = LoggerFactory.getLogger(SpELResolver.class);
    private static ExpressionParser parser = new SpelExpressionParser();

    private static final LoadingCache<String, Expression> expCache = CacheBuilder.newBuilder().build(
            new CacheLoader<String, Expression>() {
                @Override
                public Expression load(String key) {
                    return parser.parseExpression(key);
                }
            });

    public static boolean compile(String expression) {
        Expression exp = null;
        try {
            exp = expCache.getUnchecked(expression);
        } catch(ParseException e) {
            logger.error("spel {} compile error: {}", expression, e.getMessage());
        }
        return exp != null;
    }

    public static String getValue(Object context, String expression) {
        if (context == null) {
            return null;
        }
        Expression exp = expCache.getUnchecked(expression);
        return exp.getValue(context, String.class);
    }

    public static void setValue(Object context, String expStr, Object value) {
        Expression expression = expCache.getUnchecked(expStr);
        expression.setValue(context, value);
    }

}
