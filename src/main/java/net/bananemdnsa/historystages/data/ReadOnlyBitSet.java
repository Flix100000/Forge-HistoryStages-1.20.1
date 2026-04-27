package net.bananemdnsa.historystages.data;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.BitSet;

public class ReadOnlyBitSet extends BitSet {

    @Override
    public void flip(int bitIndex) {
        THROW_UNSUPPORTED();
    }

    @Override
    public void flip(int fromIndex, int toIndex) {
        THROW_UNSUPPORTED();
    }

    @Override
    public void set(int bitIndex) {
        THROW_UNSUPPORTED();
    }

    public void set(int bitIndex, boolean value) {
        THROW_UNSUPPORTED();
    }

    @Override
    public void set(int fromIndex, int toIndex) {
        THROW_UNSUPPORTED();
    }

    @Override
    public void set(int fromIndex, int toIndex, boolean value) {
        THROW_UNSUPPORTED();
    }

    @Override
    public void and(BitSet set) {
        THROW_UNSUPPORTED();
    }

    @Override
    public void or(BitSet set) {
        THROW_UNSUPPORTED();
    }

    @Override
    public void xor(BitSet set) {
        THROW_UNSUPPORTED();
    }

    @Override
    public void andNot(BitSet set) {
        THROW_UNSUPPORTED();
    }

    private void THROW_UNSUPPORTED() {
        throw new UnsupportedOperationException("Modification of read-only bitset is not supported");
    }

}
