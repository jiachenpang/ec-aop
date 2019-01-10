package org.n3r.asm;

public class CharResultToBeanUtils implements ResultToBeanUtils {

    @Override
    public String mapUtilsInvokeMethodName() {
        return "getObject";
    }

    @Override
    public String mapUtilsInvokeMethodDesc() {
        return "(Ljava/util/Map;Ljava/lang/Object;)Ljava/lang/Object;";
    }

    @Override
    public String classSetMethodDesc() {
        return "(C)V";
    }

    @Override
    public String initValueMethodOwner() {
        return "java/lang/Character";
    }

    @Override
    public String initValueMethodName() {
        return "charValue";
    }

    @Override
    public String initValueMethodDesc() {
        return "()C";
    }

    @Override
    public String checkcastObject() {
        return "java/lang/Character";
    }

}
