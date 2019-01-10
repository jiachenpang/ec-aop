package stevenTest;

import java.util.ArrayList;
import java.util.List;

public class Test500 {

    final static int PEOPLE_NUMBER = 500;

    public static void main(String[] str) {
        List<Circle> circle = new ArrayList<Circle>();
        for (int i = 0; i < PEOPLE_NUMBER; i++) {
            Circle temp = new Circle(i + 1);
            circle.add(temp);
        }
        int size = circle.size() % 3;
        while (circle.size() > 1) {
            for (int i = 0; i < circle.size(); i++) {
                if (circle.get(i).getCurrentIndex() % 3 == 0) {
                    circle.remove(i);
                }
            }
            for (int i = 0; i < circle.size(); i++) {
                circle.get(i).setCurrentIndex(size + i + 1);
            }
            size += circle.size() % 3;
        }
        System.out.println(circle.get(0).getIndex());
    }
}
