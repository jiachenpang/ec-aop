package org.n3r.core.tag;

/**
 * 描述一种可能在括弧中带有选项的参数定义。
 * 比如Number(1,2), Regex[](\d)
 * @author Bingoo
 *
 */
public class ParamDef {
    private String name;
    private String params[];
    private boolean array;
    private String def;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String[] getParams() {
        return params;
    }

    public void setParams(String params[]) {
        this.params = params;
    }

    public boolean isArray() {
        return array;
    }

    public void setArray(boolean array) {
        this.array = array;
    }

    public String getDef() {
        return def;
    }

    public void setDef(String def) {
        this.def = def;
    }
}
