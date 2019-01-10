package org.n3r.asm;

public class LongResultToBeanUtils implements ResultToBeanUtils {

    @Override
    public String mapUtilsInvokeMethodName() {
        return "getLong";
    }

    @Override
    public String mapUtilsInvokeMethodDesc() {
        return "(Ljava/util/Map;Ljava/lang/Object;)Ljava/lang/Long;";
    }

    @Override
    public String classSetMethodDesc() {
        return "(J)V";
    }

    @Override
    public String initValueMethodOwner() {
        return "java/lang/Long";
    }

    @Override
    public String initValueMethodName() {
        return "longValue";
    }

    @Override
    public String initValueMethodDesc() {
        return "()J";
    }

    @Override
    public String checkcastObject() {
        // TODO Auto-generated method stub
        return null;
    }

}
