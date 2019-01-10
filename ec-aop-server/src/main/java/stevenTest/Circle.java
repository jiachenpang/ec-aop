package stevenTest;

public class Circle {

    int index;
    int currentIndex;

    Circle() {
        this.index = 0;
        this.currentIndex = 0;
    }

    Circle(int index) {
        this.index = index;
        this.currentIndex = index;
    }

    Circle(int index, int currentIndex) {
        this.index = index;
        this.currentIndex = currentIndex;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int currentIndex) {
        this.currentIndex = currentIndex;
    }
}
