package org.n3r.asm;

public class FloatResultToBeanUtils implements ResultToBeanUtils {

    @Override
    public String mapUtilsInvokeMethodName() {
        return "getFloat";
    }

    @Override
    public String mapUtilsInvokeMethodDesc() {
        return "(Ljava/util/Map;Ljava/lang/Object;)Ljava/lang/Float;";
    }

    @Override
    public String classSetMethodDesc() {
        return "(F)V";
    }

    @Override
    public String initValueMethodOwner() {
        return "java/lang/Float";
    }

    @Override
    public String initValueMethodName() {
        return "floatValue";
    }

    @Override
    public String initValueMethodDesc() {
        return "()F";
    }

    @Override
    public String checkcastObject() {
        return null;
    }

}
