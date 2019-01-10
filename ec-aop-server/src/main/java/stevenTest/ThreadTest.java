package stevenTest;

public class ThreadTest implements Runnable {

    public static int count = 0;

    @Override
    public void run() {
        inc();
    }

    public synchronized void inc() {
        try {
            Thread.sleep(1);
        }
        catch (Exception e) {
        }
        count++;

    }
}
