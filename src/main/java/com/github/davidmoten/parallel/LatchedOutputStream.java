package com.github.davidmoten.parallel;

import java.io.IOException;
import java.io.OutputStream;

import edu.emory.mathcs.backport.java.util.concurrent.CountDownLatch;

final class LatchedOutputStream extends OutputStream {

    private final OutputStream out;
    private final CountDownLatch latch;

    LatchedOutputStream(OutputStream out, CountDownLatch latch) {
        super();
        this.out = out;
        this.latch = latch;
    }

    public void write(int b) throws IOException {
        out.write(b);
    }

    public int hashCode() {
        return out.hashCode();
    }

    public void write(byte[] b) throws IOException {
        out.write(b);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

    public boolean equals(Object obj) {
        return out.equals(obj);
    }

    public void flush() throws IOException {
        out.flush();
    }

    public void close() throws IOException {
        latch.countDown();
        out.close();
    }

    public String toString() {
        return out.toString();
    }
    
    public CountDownLatch latch() {
        return latch;
    }
}