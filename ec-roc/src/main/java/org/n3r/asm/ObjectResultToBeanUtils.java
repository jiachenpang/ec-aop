package org.n3r.asm;

import org.objectweb.asm.Type;

public class ObjectResultToBeanUtils implements ResultToBeanUtils {

    private String internalName;
    private String descriptor;

    public ObjectResultToBeanUtils(Class clazz) {
        this.internalName = Type.getInternalName(clazz);
        this.descriptor = Type.getDescriptor(clazz);
    }

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
        return "(" + descriptor + ")V";
    }

    @Override
    public String initValueMethodOwner() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String initValueMethodName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String initValueMethodDesc() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String checkcastObject() {
        return internalName;
    }

}
