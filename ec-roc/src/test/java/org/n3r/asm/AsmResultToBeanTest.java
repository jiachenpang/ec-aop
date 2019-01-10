package org.n3r.asm;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.apache.commons.lang3.ClassUtils;
import org.junit.Test;
import org.n3r.asm.AsmToStringBuilderTest.Person;
import org.objectweb.asm.Type;

import com.google.common.collect.Maps;

public class AsmResultToBeanTest {

    public static class Car {

        private String brand;

        private int price;

        private boolean automatic;

        private long time;

        private double doubled;

        private char chard;

        private short shorted;

        private float floated;

        private Person person;


        public String getBrand() {
            return brand;
        }

        public void setBrand(String brand) {
            this.brand = brand;
        }

        public int getPrice() {
            return price;
        }

        public void setPrice(int price) {
            this.price = price;
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

        public Person getPerson() {
            return person;
        }

        public void setPerson(Person person) {
            this.person = person;
        }

        public char getChard() {
            return chard;
        }

        public void setChard(char chard) {
            this.chard = chard;
        }

        @Override
        public String toString() {
            return "Car [brand=" + brand + ", price=" + price + ", automatic=" + automatic + ", time=" + time
                    + ", doubled=" + doubled + ", chard=" + chard + ", shorted=" + shorted + ", floated=" + floated
                    + ", person=" + person + "]";
        }



    }


    @Test
    public void test1() throws Exception {
        Map<String, Object> in = Maps.newHashMap();
        in.put("brand", "Audi");
        in.put("price", 1700000);
        in.put("automatic", true);
        in.put("time", 10000L);
        in.put("doubled", 200000.0d);
        in.put("shorted", (short)1);
        in.put("chard", 'a');
        in.put("floated", 1.5f);
        Person person = new Person();
        person.setAge(18);
        person.setName("bingoo huang");
        in.put("person", person);

        Car resultToBean = AsmToBeanBuilder.resultToBean(Car.class, in);
        System.out.println(resultToBean);
        assertEquals("brand:Audi,price:1700000,automatic:true", AsmToStringBuilder.toStr(resultToBean));
    }

    @Test
    public void test2() {
        System.out.println(Type.getInternalName(Car.class));
        System.out.println(Type.getDescriptor(Car.class));
        System.out.println(Type.getType(String.class).getInternalName());
        System.out.println(Type.getDescriptor(int.class));
        System.out.println(Type.INT_TYPE.getDescriptor());
        System.out.println(Type.getArgumentTypes("(I)V"));
        System.out.println(long.class.isPrimitive());
        System.out.println(ClassUtils.isPrimitiveOrWrapper(Integer.class));
    }

}
