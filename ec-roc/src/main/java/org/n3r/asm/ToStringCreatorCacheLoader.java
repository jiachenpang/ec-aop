package org.n3r.asm;

import java.lang.reflect.Field;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.springframework.util.ClassUtils;

import com.google.common.cache.CacheLoader;

public class ToStringCreatorCacheLoader extends CacheLoader<Class, ToStringCreator> implements Opcodes {

    @Override
    public ToStringCreator load(Class clazz) throws Exception {
        ClassWriter cw = generateClassWrite(clazz);

        generateConstructor(cw);

        generateToStrMethod(clazz, cw);

        byte[] code = cw.toByteArray();

        MyClassLoader loader = new MyClassLoader();
        Class personClass = loader.defineClass(clazz.getCanonicalName() + "ToStr", code);

        ToStringCreator ret = null;
        try {
            ret = (ToStringCreator) personClass.newInstance();
        }
        catch (InstantiationException e) {
            e.printStackTrace();
        }
        catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return ret;
    }

    private void generateConstructor(ClassWriter cw) {
        // creates a MethodWriter for the (implicit) constructor
        MethodVisitor mw = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null,
                null);
        // pushes the 'this' variable
        mw.visitVarInsn(ALOAD, 0);
        // invokes the super class constructor
        mw.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(Object.class), "<init>", "()V");
        mw.visitInsn(RETURN);
        // this code uses a maximum of one stack element and one local variable
        mw.visitMaxs(1, 1);
        mw.visitEnd();

    }

    private void generateToStrMethod(Class clazz, ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "toStr", "(Ljava/lang/Object;)Ljava/lang/String;",
                null,
                null);

        castParamAndNewStringBuilder(clazz, mv);

        Field[] fields = clazz.getDeclaredFields();
        boolean isFirstLoop = true;
        for (Field field : fields) {
            String fieldName = field.getName();
            isFirstLoop = popFieldNameAndInvokeStringBuilder(mv, isFirstLoop, fieldName);

            invokeFieldGetMethodAndStringBuilderAppand(clazz, mv, field, fieldName);
        }

        mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(StringBuilder.class), "toString",
                "()Ljava/lang/String;");
        mv.visitInsn(ARETURN);

        mv.visitMaxs(3, 3);
        mv.visitEnd();
    }

    private void invokeFieldGetMethodAndStringBuilderAppand(Class clazz, MethodVisitor mv, Field field,
            String fieldName) {
        mv.visitVarInsn(ALOAD, 2);
        Class<?> type = field.getType();
        StringBuilder classInvokeDesc = new StringBuilder();
        StringBuilder stringBuilderInvokeDesc = new StringBuilder();
        createInvokeDesc(type, classInvokeDesc, stringBuilderInvokeDesc);

        mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(clazz), fieldMethodName(clazz, fieldName),
                classInvokeDesc.toString());
        mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(StringBuilder.class), "append",
                stringBuilderInvokeDesc.toString());
    }

    private String fieldMethodName(Class clazz, String fieldName) {

        final String upcaseFieldName = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        return ClassUtils.hasMethod(clazz, "get" + upcaseFieldName, new Class[] {}) ? "get"
                + upcaseFieldName : "is" + upcaseFieldName;
    }

    private void createInvokeDesc(Class<?> type, StringBuilder classInvokeDesc,
            StringBuilder stringBuilderInvokeDesc) {
        if (type == String.class) {
            classInvokeDesc.append("()Ljava/lang/String;");
            stringBuilderInvokeDesc.append("(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        }
        else if (type == int.class || type == Integer.class) {
            classInvokeDesc.append("()I");
            stringBuilderInvokeDesc.append("(I)Ljava/lang/StringBuilder;");
        }
        else if (type == boolean.class || type == Boolean.class) {
            classInvokeDesc.append("()Z");
            stringBuilderInvokeDesc.append("(Z)Ljava/lang/StringBuilder;");
        }
        else if(type == long.class || type == Long.class) {
            classInvokeDesc.append("()J");
            stringBuilderInvokeDesc.append("(J)Ljava/lang/StringBuilder;");
        }
        else if(type == double.class || type == Double.class) {
            classInvokeDesc.append("()D");
            stringBuilderInvokeDesc.append("(D)Ljava/lang/StringBuilder;");
        }
        else if(type == char.class || type == Character.class) {
            classInvokeDesc.append("()C");
            stringBuilderInvokeDesc.append("(C)Ljava/lang/StringBuilder;");
        }
        else if(type == short.class || type == Short.class) {
            classInvokeDesc.append("()S");
            stringBuilderInvokeDesc.append("(I)Ljava/lang/StringBuilder;");
        }
        else if(type == float.class || type == Float.class) {
            classInvokeDesc.append("()F");
            stringBuilderInvokeDesc.append("(F)Ljava/lang/StringBuilder;");
        }
        else {
            classInvokeDesc.append("()").append(Type.getDescriptor(type));
            stringBuilderInvokeDesc.append("(Ljava/lang/Object;)Ljava/lang/StringBuilder;");
        }
    }

    private boolean popFieldNameAndInvokeStringBuilder(MethodVisitor mv, boolean isFirstLoop,
            String fieldName) {
        if (isFirstLoop) {
            mv.visitLdcInsn(fieldName + ":");
            mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(StringBuilder.class), "<init>",
                    "(Ljava/lang/String;)V");
            return false;
        }

        mv.visitLdcInsn("," + fieldName + ":");
        mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(StringBuilder.class), "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        return false;
    }

    private void castParamAndNewStringBuilder(Class clazz, MethodVisitor mv) {
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, Type.getInternalName(clazz));
        mv.visitVarInsn(ASTORE, 2);

        mv.visitTypeInsn(NEW, Type.getInternalName(StringBuilder.class));
        mv.visitInsn(DUP);
    }

    private ClassWriter generateClassWrite(Class clazz) {
        ClassWriter cw = new ClassWriter(0);
        String name = clazz.getCanonicalName().replace(".", "/") + "ToStr";
        cw.visit(V1_5, ACC_PUBLIC, name, null, "java/lang/Object",
                new String[] { "org/n3r/asm/ToStringCreator" });
        return cw;
    }
}
