package org.n3r.asm;

public class DoubleResultToBeanUtils implements ResultToBeanUtils {

    @Override
    public String mapUtilsInvokeMethodName() {
        return "getDouble";
    }

    @Override
    public String mapUtilsInvokeMethodDesc() {
        return "(Ljava/util/Map;Ljava/lang/Object;)Ljava/lang/Double;";
    }

    @Override
    public String classSetMethodDesc() {
        return "(D)V";
    }

    @Override
    public String initValueMethodOwner() {
        return "java/lang/Double";
    }

    @Override
    public String initValueMethodName() {
        return "doubleValue";
    }

    @Override
    public String initValueMethodDesc() {
        return "()D";
    }

    @Override
    public String checkcastObject() {
        // TODO Auto-generated method stub
        return null;
    }

}
