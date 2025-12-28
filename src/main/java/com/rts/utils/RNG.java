package com.rts.utils;

public class RNG {

    private long seed;

    private static final long MAX_VALUE = 0xFFFFFFFFL;
    private static final long MIN_VALUE = -MAX_VALUE;

    public RNG(long seed) {
        this.seed = seed & MAX_VALUE;
        if( this.seed ==0){
            this.seed = 123456789L;
        }
    }

    public RNG() {
        this(System.currentTimeMillis());
    }

    public long next(){
        long x = seed;
        x ^= x<< 13;
        x ^= x>>> 17;
        x ^= x<< 5;
        seed = x &MAX_VALUE;
        return seed;

    }

    public double nextDouble(){
        return (double) next() / (double) MAX_VALUE;
    }

    public float nextFloat(){
        return ( float) nextDouble();
    }

    public int nextInt(int min, int max) {
        if( min  > max){
            throw new IllegalArgumentException("min must be <= max");
        }
        if( min==max){
            return min;
        }
        return min +(int) Math.floor(nextDouble() * (max - min + 1));
    }

    public int nextInt(int bound){
        if ( bound <=0){
            throw new IllegalArgumentException("bound must be >= 0");
        }
        return (int) Math.floor(nextDouble() *bound);
    }

    public boolean nextBoolean(){
        return nextDouble() < 0.5;
    }
}
