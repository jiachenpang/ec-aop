package org.n3r.asm;

import java.lang.reflect.Field;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.ClassUtils;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import com.google.common.cache.CacheLoader;

public class ResultToBeanCacheLoader extends CacheLoader<Class, ResultToBeanCreator> implements Opcodes {

    int astoreIndex = 3;

    @Override
    public ResultToBeanCreator load(Class clazz) throws Exception {
        ClassWriter cw = generateClassWrite(clazz);

        generateConstructor(cw);

        generateToStrMethod(clazz, cw);

        byte[] code = cw.toByteArray();

        //        String path = "e:/workspaces10/ec-roc/class2/CarResultToBean.class";
        //        System.Out.Println(path);
        //
        //        File file = new File(path);
        //        try {
        //            file.createNewFile();
        //            FileOutputStream fos;
        //
        //            fos = new FileOutputStream(file);
        //
        //            fos.write(code);
        //            fos.close();
        //        }
        //        catch (Exception e1) {
        //            e1.printStackTrace();
        //        }

        MyClassLoader loader = new MyClassLoader();
        Class newClass = loader.defineClass(clazz.getCanonicalName() + "ResultToBean", code);

        ResultToBeanCreator ret = null;
        try {
            ret = (ResultToBeanCreator) newClass.newInstance();
        }
        catch (InstantiationException e) {
            e.printStackTrace();
        }
        catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return ret;
    }

    private void generateToStrMethod(Class clazz, ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "resultToBean", "(Ljava/util/Map;)Ljava/lang/Object;",
                null, null);

        newClassAndStore(clazz, mv);

        generateSetFieldValueMethod(clazz, mv);

        generateReturn(mv);
    }

    private void generateReturn(MethodVisitor mv) {
        mv.visitVarInsn(ALOAD, 2);
        mv.visitInsn(ARETURN);

        mv.visitMaxs(3, astoreIndex);
        mv.visitEnd();
    }

    private void generateSetFieldValueMethod(Class clazz, MethodVisitor mv) {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            mv.visitVarInsn(ALOAD, 1);
            final String fieldName = field.getName();
            mv.visitLdcInsn(fieldName);
            Class<?> fieldType = field.getType();
            //            StringBuilder mapUtilsInvokeMethodName = new StringBuilder();
            //            StringBuilder mapUtilsInvokeMethodDesc = new StringBuilder();
            //            StringBuilder classSetMethodName = new StringBuilder();
            //            StringBuilder intValueMethodName = new StringBuilder();

            ResultToBeanUtils createInvokeUtils = createInvokeDesc(fieldType);
            mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(MapUtils.class),
                    createInvokeUtils.mapUtilsInvokeMethodName(),
                    createInvokeUtils.mapUtilsInvokeMethodDesc());
            if (isFieldTypeCharOrObject(fieldType)) {
                mv.visitTypeInsn(CHECKCAST, createInvokeUtils.checkcastObject());
            }
            mv.visitVarInsn(ASTORE, astoreIndex);

            mv.visitVarInsn(ALOAD, 2);
            mv.visitVarInsn(ALOAD, astoreIndex);
            if (ClassUtils.isPrimitiveOrWrapper(fieldType)) {
                //                 Iterable<String> split = Splitter.on("^_^").omitEmptyStrings().trimResults().split(intValueMethodName.toString()) ;
                //                 String[] strArray = Iterables.toArray(split, String.class);
                mv.visitMethodInsn(INVOKEVIRTUAL, createInvokeUtils.initValueMethodOwner(),
                        createInvokeUtils.initValueMethodName(), createInvokeUtils.initValueMethodDesc());
            }
            mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(clazz), fieldMethodName(fieldName),
                    createInvokeUtils.classSetMethodDesc());

            astoreIndex++;
        }
    }

    private boolean isFieldTypeCharOrObject(Class<?> fieldType) {
        return isChar(fieldType) || isObjectExceptString(fieldType);
    }

    private boolean isObjectExceptString(Class<?> fieldType) {
        return !ClassUtils.isPrimitiveOrWrapper(fieldType) && fieldType != String.class;
    }

    private boolean isChar(Class<?> fieldType) {
        return fieldType == char.class || fieldType == Character.class;
    }

    private String fieldMethodName(String fieldName) {
        final String upcaseFieldName = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        return "set" + upcaseFieldName;
    }

    private ResultToBeanUtils createInvokeDesc(Class<?> fieldType) {
        ResultToBeanUtils resultToBeanUtils = null;
        if (fieldType == String.class) {
            resultToBeanUtils = new StringResultToBeanUtils();
        }
        else if (fieldType == int.class || fieldType == Integer.class) {
            resultToBeanUtils = new IntegerResultToBeanUtils();
        }
        else if (fieldType == boolean.class || fieldType == Boolean.class) {
            resultToBeanUtils = new BooleanResultToBeanUtils();
        }
        else if(fieldType == long.class || fieldType == Long.class) {
            resultToBeanUtils = new LongResultToBeanUtils();
        }
        else if(fieldType == double.class || fieldType == Double.class) {
            resultToBeanUtils = new DoubleResultToBeanUtils();
        }
        else if(fieldType == char.class || fieldType == Character.class) {
            resultToBeanUtils = new CharResultToBeanUtils();
        }
        else if(fieldType == short.class || fieldType == Short.class) {
            resultToBeanUtils = new ShortResultToBeanUtils();
        }
        else if(fieldType == float.class || fieldType == Float.class) {
            resultToBeanUtils = new FloatResultToBeanUtils();
        }
        else {
            resultToBeanUtils = new ObjectResultToBeanUtils(fieldType);
        }

        return resultToBeanUtils;
    }

    private void newClassAndStore(Class clazz, MethodVisitor mv) {
        mv.visitTypeInsn(NEW, Type.getInternalName(clazz));
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(clazz), "<init>", "()V");
        mv.visitVarInsn(ASTORE, 2);
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

    private ClassWriter generateClassWrite(Class clazz) {
        ClassWriter cw = new ClassWriter(0);
        String name = clazz.getCanonicalName().replace(".", "/") + "ResultToBean";
        cw.visit(V1_5, ACC_PUBLIC, name, null, "java/lang/Object",
                new String[] { "org/n3r/asm/ResultToBeanCreator" });
        return cw;
    }

}
