package org.n3r.asm;

public class StringResultToBeanUtils implements ResultToBeanUtils {

    @Override
    public String mapUtilsInvokeMethodName() {
        return "getString";
    }

    @Override
    public String mapUtilsInvokeMethodDesc() {
        return "(Ljava/util/Map;Ljava/lang/Object;)Ljava/lang/String;";
    }

    @Override
    public String classSetMethodDesc() {
        return "(Ljava/lang/String;)V";
    }

    @Override
    public String initValueMethodOwner() {
        return null;
    }

    @Override
    public String initValueMethodName() {
        return null;
    }

    @Override
    public String initValueMethodDesc() {
        return null;
    }

    @Override
    public String checkcastObject() {
        return null;
    }

}
