package org.n3r.esql.config;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.n3r.config.Config;
import org.n3r.config.Configable;
import org.n3r.esql.ex.EsqlConfigException;

import java.util.concurrent.ExecutionException;

public class EsqlConfigManager {
    private static LoadingCache<Object, EsqlConfigable> esqlConfigableCache =
            CacheBuilder.newBuilder().build(
                    new CacheLoader<Object, EsqlConfigable>() {
                        @Override
                        public EsqlConfigable load(Object connectionNameOrConfigable) throws Exception {
                            Configable config = null;
                            if (connectionNameOrConfigable instanceof String) {
                                config = Config.subset("connectionName." + connectionNameOrConfigable);
                            }
                            else if (connectionNameOrConfigable instanceof Configable) {
                                config = (Configable) connectionNameOrConfigable;
                            }

                            if (config == null || config.getProperties().size() == 0) return null;

                            return config.exists("jndiName")
                                    ? createDsConfig(config) : createSimpleConfig(config);
                        }
                    }
            );

    private static EsqlConfigable createDsConfig(Configable connConfig) {
        EsqlDsConfig dsConfig = new EsqlDsConfig();
        dsConfig.setJndiName(connConfig.getStr("jndiName"));
        dsConfig.setInitial(connConfig.getStr("java.naming.factory.initial", ""));
        dsConfig.setUrl(connConfig.getStr("java.naming.provider.url", ""));
        dsConfig.setTransactionType(connConfig.getStr("transactionType"));

        return dsConfig;
    }

    private static EsqlConfigable createSimpleConfig(Configable connConfig) {
        EsqlSimpleConfig simpleConfig = new EsqlSimpleConfig();
        simpleConfig.setDriver(connConfig.getStr("driver"));
        simpleConfig.setUrl(connConfig.getStr("url"));
        simpleConfig.setUser(connConfig.getStr("user"));
        simpleConfig.setPass(connConfig.getStr("password"));
        simpleConfig.setTransactionType(connConfig.getStr("transactionType"));

        return simpleConfig;
    }

    public static EsqlConfigable getConfig(Object connectionNameOrConfigable) {
        try {
            return esqlConfigableCache.get(connectionNameOrConfigable);
        } catch (ExecutionException e) {
            throw new EsqlConfigException(
                    "esql connection name " + connectionNameOrConfigable + " is not properly configed.", e.getCause());
        }


    }
}
