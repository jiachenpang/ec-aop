package org.n3r.asm;

public class ShortResultToBeanUtils implements ResultToBeanUtils {

    @Override
    public String mapUtilsInvokeMethodName() {
        return "getShort";
    }

    @Override
    public String mapUtilsInvokeMethodDesc() {
        return "(Ljava/util/Map;Ljava/lang/Object;)Ljava/lang/Short;";
    }

    @Override
    public String classSetMethodDesc() {
        return "(S)V";
    }

    @Override
    public String initValueMethodOwner() {
        return "java/lang/Short";
    }

    @Override
    public String initValueMethodName() {
        return "shortValue";
    }

    @Override
    public String initValueMethodDesc() {
        return "()S";
    }

    @Override
    public String checkcastObject() {
        // TODO Auto-generated method stub
        return null;
    }

}
