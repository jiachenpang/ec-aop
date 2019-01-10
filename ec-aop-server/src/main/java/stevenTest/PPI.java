package stevenTest;

public class PPI {

    public static void main(String[] str) {
        for (int i = 1; i < 1000; i++) {
            int number = i * 11;
            if (number % 3 != 2)
                continue;
            if (number % 5 != 4)
                continue;
            if (number % 7 != 6)
                continue;
            if (number % 9 != 8)
                continue;
            System.out.println(number);
            break;
        }
    }
}
