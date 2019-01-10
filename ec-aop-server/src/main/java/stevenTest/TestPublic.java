package stevenTest;

public class TestPublic {

    private boolean bool = true;

    public synchronized void sub(int i) {
        if (!bool) {
            try {
                this.wait();
            }
            catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        for (int j = 0; j < 10; j++) {
            System.out.println("sub thread =" + j + "count=" + i);

        }
        bool = false;
        this.notify();
    }

    public synchronized void main(int i) {
        if (bool) {
            try {
                this.wait();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        for (int j = 0; j < 10; j++) {
            System.out.println("main thread =" + j + "count=" + i);

        }
        bool = true;
        this.notify();
    }

}
