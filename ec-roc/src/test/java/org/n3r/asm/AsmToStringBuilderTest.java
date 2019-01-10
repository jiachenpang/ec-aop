package org.n3r.asm;

import org.junit.Assert;
import org.junit.Test;

public class AsmToStringBuilderTest {

    public static class SoYoung {
        private String so;
        private int young;

        public String getSo() {
            return so;
        }

        public void setSo(String so) {
            this.so = so;
        }

        public int getYoung() {
            return young;
        }

        public void setYoung(int young) {
            this.young = young;
        }

        @Override
        public String toString() {
            return "SoYoung [so=" + so + ", young=" + young + "]";
        }

    }

    public static class Person {
        private String name;
        private int age;
        private boolean automatic;
        private long time;
        private double doubled;
        private char chard;
        private short shorted;
        private float floated;
        private SoYoung soYoung;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public boolean isAutomatic() {
            return automatic;
        }

        public void setAutomatic(boolean automatic) {
            this.automatic = automatic;
        }

        public long getTime() {
            return time;
        }

        public void setTime(long time) {
            this.time = time;
        }

        public double getDoubled() {
            return doubled;
        }

        public void setDoubled(double doubled) {
            this.doubled = doubled;
        }

        public char getChard() {
            return chard;
        }

        public void setChard(char chard) {
            this.chard = chard;
        }

        public short getShorted() {
            return shorted;
        }

        public void setShorted(short shorted) {
            this.shorted = shorted;
        }

        public float getFloated() {
            return floated;
        }

        public void setFloated(float floated) {
            this.floated = floated;
        }

        public SoYoung getSoYoung() {
            return soYoung;
        }

        public void setSoYoung(SoYoung soYoung) {
            this.soYoung = soYoung;
        }
    }

    @Test
    public void test1() throws Exception {
        Person person = new Person();
        person.setName("huangjb");
        person.setAge(100);
        person.setAutomatic(false);
        person.setChard('a');
        person.setDoubled(20.09d);
        person.setFloated(1.02f);
        person.setTime(10000l);
        person.setShorted((short)1);
        SoYoung soYoung = new SoYoung();
        soYoung.setSo("so");
        soYoung.setYoung(100);
        person.setSoYoung(soYoung);

//        System.out.println(AsmToStringBuilder.toStr(person));

        String excepted = "name:huangjb,age:100,automatic:false,time:10000,doubled:20.09,chard:a,shorted:1,floated:1.02,soYoung:SoYoung [so=so, young=100]";
        Assert.assertEquals(excepted, AsmToStringBuilder.toStr(person));
        //        long start = System.currentTimeMillis();
        //        for (int i = 0; i < 100000000; i++) {
        //            AsmToStringBuilder.toStr(person);
        //        }
        //        long end1 = System.currentTimeMillis();
        //        for (int i = 0; i < 100000000; i++) {
        //            ReflectionToStringBuilder.toString(person);
        //        }
        //        long end2 = System.currentTimeMillis();
        //        System.out.println("Asm调用1亿时间:" + ((end1 - start) /1000) + "s" );
        //        System.out.println("反射调用1亿时间:" + ((end2 - end1)/1000) + "s");
    }

}
