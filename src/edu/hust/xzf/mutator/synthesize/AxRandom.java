/**
 * MIT License
 * <p>
 * Copyright (c) 2021 Cong Li (congli@smail.nju.edu.cn, cong.li@inf.ethz.ch)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package edu.hust.xzf.mutator.synthesize;


import java.util.Random;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public class AxRandom {
    private static AxRandom sInstance;
    private final Random mRandom;

    private AxRandom() {
        mRandom = new Random();
    }

    public static AxRandom getInstance() {
        if (sInstance == null) {
            sInstance = new AxRandom();
        }
        return sInstance;
    }

    public void setSeed(long seed) {
        mRandom.setSeed(seed);
    }

    public void nextBytes(byte[] bytes) {
        mRandom.nextBytes(bytes);
    }

    public int nextInt() {
        return mRandom.nextInt();
    }

    public int nextInt(int bound) {
        return mRandom.nextInt(bound);
    }

    /**
     * Return next int between min and max
     */
    public int nextInt(int min, int max) {
        AxChecker.check(max > min, "Max bound must be greater than min bound");
        return mRandom.nextInt(max - min) + min;
    }

    public long nextLong() {
        return mRandom.nextLong();
    }

    public boolean nextBoolean() {
        return mRandom.nextBoolean();
    }

    public float nextFloat() {
        return mRandom.nextFloat();
    }

    public double nextDouble() {
        return mRandom.nextDouble();
    }

    public double nextGaussian() {
        return mRandom.nextGaussian();
    }

    /**
     * Return next index from an array of probabilities. For example, nextIndex([0.1, 0.4, 0.5])
     * returns - 0 with probability 0.1 - 1 with probability 0.4 - 2 with probability 0.5.
     */
    public int nextIndex(float[] probs) {
        AxChecker.check(probs.length >= 1, "Input probabilities are not sufficient");
        float[] newProbs = new float[probs.length + 1];
        newProbs[0] = 0f;
        for (int i = 1; i < newProbs.length; i++) {
            newProbs[i] = newProbs[i - 1] + probs[i - 1];
        }
        AxChecker.check(Math.abs(1.0f - newProbs[newProbs.length - 1]) <= 1e-5,
                "Input probabilities does not sum to 1.0");
        float x = nextFloat();
        for (int i = 0; i < newProbs.length; i++) {
            if (newProbs[i] < x && x <= newProbs[i + 1]) {
                return i;
            }
        }
        return probs.length - 1;
    }

    public IntStream ints(long streamSize) {
        return mRandom.ints(streamSize);
    }

    public IntStream ints() {
        return mRandom.ints();
    }

    public IntStream ints(long streamSize, int randomNumberOrigin, int randomNumberBound) {
        return mRandom.ints(streamSize, randomNumberOrigin, randomNumberBound);
    }

    public IntStream ints(int randomNumberOrigin, int randomNumberBound) {
        return mRandom.ints(randomNumberOrigin, randomNumberBound);
    }

    public LongStream longs(long streamSize) {
        return mRandom.longs(streamSize);
    }

    public LongStream longs() {
        return mRandom.longs();
    }

    public LongStream longs(long streamSize, long randomNumberOrigin, long randomNumberBound) {
        return mRandom.longs(streamSize, randomNumberOrigin, randomNumberBound);
    }

    public LongStream longs(long randomNumberOrigin, long randomNumberBound) {
        return mRandom.longs(randomNumberOrigin, randomNumberBound);
    }

    public DoubleStream doubles(long streamSize) {
        return mRandom.doubles(streamSize);
    }

    public DoubleStream doubles() {
        return mRandom.doubles();
    }

    public DoubleStream doubles(long streamSize, double randomNumberOrigin,
                                double randomNumberBound) {
        return mRandom.doubles(streamSize, randomNumberOrigin, randomNumberBound);
    }

    public DoubleStream doubles(double randomNumberOrigin, double randomNumberBound) {
        return mRandom.doubles(randomNumberOrigin, randomNumberBound);
    }
}
