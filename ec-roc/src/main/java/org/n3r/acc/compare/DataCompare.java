package org.n3r.acc.compare;

import static org.n3r.acc.utils.AccUtils.*;
import org.n3r.config.Config;
import org.n3r.config.Configable;

public abstract class DataCompare {
    public abstract void compare();

    public static DataCompare fromSpec(String specName, CompareContext compareContext) {
        Configable config = Config.subset("DataCompare." + specName);
        String compareImpl = config.getStr("compareImpl", "@Outside");


        return parseSpec(compareImpl, DataCompare.class, config, compareContext.getContext()) ;
    }
}
