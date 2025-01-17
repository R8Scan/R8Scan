package edu.hust.xzf.test;

public class uncommontrap {

    public static int printSum() {
        return Inline.printSum();
    }

    public static void main(String[] args) {
        for (int i = 0; i < 100_000; i++) {
            if (i == 99_999) {
                System.out.println(2);
            }
        }
    }
}
