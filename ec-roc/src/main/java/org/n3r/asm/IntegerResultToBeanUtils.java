package org.n3r.asm;

public class IntegerResultToBeanUtils implements ResultToBeanUtils {

    @Override
    public String mapUtilsInvokeMethodName() {
        return "getInteger";
    }

    @Override
    public String mapUtilsInvokeMethodDesc() {
        return "(Ljava/util/Map;Ljava/lang/Object;)Ljava/lang/Integer;";
    }

    @Override
    public String classSetMethodDesc() {
        return "(I)V";
    }

    @Override
    public String initValueMethodOwner() {
        return "java/lang/Integer";
    }

    @Override
    public String initValueMethodName() {
        return "intValue";
    }

    @Override
    public String initValueMethodDesc() {
        return "()I";
    }

    @Override
    public String checkcastObject() {
        return null;
    }

}
