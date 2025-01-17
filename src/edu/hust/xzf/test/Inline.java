package edu.hust.xzf.test;

public class Inline {


    public static int printSum() {
        int a = 0;
        a += sum(1, 1);
        a += sum(2, 2);
        a += sum(5, 5);
        return a;
    }

    public static int sum(int a, int b) {
        return a + b;
    }

    public static void main(String[] args) {
        for (int i = 0; i < 1000000; i++) {
            printSum();
        }
    }

}
