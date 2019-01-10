package org.n3r.asm;

public class BooleanResultToBeanUtils implements ResultToBeanUtils {

    @Override
    public String mapUtilsInvokeMethodName() {
        return "getBoolean";
    }

    @Override
    public String mapUtilsInvokeMethodDesc() {
        return "(Ljava/util/Map;Ljava/lang/Object;)Ljava/lang/Boolean;";
    }

    @Override
    public String classSetMethodDesc() {
        return "(Z)V";
    }

    @Override
    public String initValueMethodOwner() {
        return "java/lang/Boolean";
    }

    @Override
    public String initValueMethodName() {
        return "booleanValue";
    }

    @Override
    public String initValueMethodDesc() {
        return "()Z";
    }

    @Override
    public String checkcastObject() {
        return null;
    }

}
