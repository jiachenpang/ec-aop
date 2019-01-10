package stevenTest;

public class Count {

    public static void main(String[] str) {
        final TestPublic thread = new TestPublic();
        new Thread(new Runnable() {

            @Override
            public void run() {
                for (int i = 0; i < 10; i++) {
                    thread.sub(i);
                }
            }
        }).start();
        new Thread(new Runnable() {

            @Override
            public void run() {
                for (int i = 0; i < 10; i++) {
                    thread.main(i);
                }
            }
        }).start();
    }
}
