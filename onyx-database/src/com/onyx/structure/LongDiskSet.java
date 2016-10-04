package com.onyx.structure;

import com.onyx.structure.base.AbstractLongIterableSet;
import com.onyx.structure.node.BitMapNode;
import com.onyx.structure.node.Header;
import com.onyx.structure.node.LongRecordReference;
import com.onyx.structure.serializer.ObjectBuffer;
import com.onyx.structure.serializer.ObjectSerializable;
import com.onyx.structure.store.Store;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by tosborn1 on 9/9/16.
 */
public class LongDiskSet<E> extends AbstractLongIterableSet<E> implements ObjectSerializable {

    public LongDiskSet()
    {
        this(null, null);
    }
    /**
     * Constructor.
     *
     * @param fileStore
     * @param header
     */
    public LongDiskSet(Store fileStore, Header header) {
        super(fileStore, header);
    }

    @Override
    public int size() {
        return (int)header.recordCount.get();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(Object o) {
        int hash = hash(o);
        final int[] hashDigits = getHashDigits(hash);

        int hashDigit = hashDigits[BITMAP_ITERATIONS];

        readWriteLock.lockWriteLevel(hashDigits[1]);

        try {

            final BitMapNode node = this.seek(hash, false, hashDigits);
            if (node != null && node.next[hashDigit] > 0) {
                final LongRecordReference[] references = this.getLongRecordReferences(node, (long)o, hashDigits);
                if (references != null && references[1] != null) {
                    return true;
                }
            }

        } finally {
            readWriteLock.unlockWriteLevel(hashDigits[1]);
        }

        return false;
    }

    @Override
    public Object[] toArray() {
        final Object[] array = new Object[this.size()];
        final AtomicInteger i = new AtomicInteger(0);
        forEach(e ->
        {
            int index = i.getAndAdd(1);
            if (index < size()) {
                array[index] = e;
            }
        });
        return array;
    }

    @Override
    public <T> T[] toArray(T[] a) {
        final T[] array = (T[])java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size());
        final AtomicInteger i = new AtomicInteger(0);
        forEach(e ->
        {
            int index = i.getAndAdd(1);
            if (index < size()) {
                array[index] = (T)e;
            }
        });
        return array;
    }

    @Override
    public boolean add(E e) {

        // Convert the hash number to digits with leading 0s
        int hash = hash(e);
        final int[] hashDigits = getHashDigits(hash);

        int hashDigit = hashDigits[BITMAP_ITERATIONS];

        readWriteLock.lockWriteLevel(hashDigits[1]);

        long value = (Long)e;
        try
        {
            final BitMapNode node = this.seek(hash, true, hashDigits);

            if (node != null && node.next[hashDigit] > 0)
            {
                final LongRecordReference[] references = this.getLongRecordReferences(node, value, hashDigits);

                if (!(references != null && references[1] != null))
                {
                    insert(references[0], node, value, hashDigits);
                    return true;
                }
            } else
            {
                insert(null, node, value, hashDigits);
                return true;
            }
        } finally
        {
            readWriteLock.unlockWriteLevel(hashDigits[1]);
        }

        return false;
    }

    @Override
    public boolean remove(Object o) {
        int hash = hash(o);
        final int[] hashDigits = getHashDigits(hash);

        int hashDigit = hashDigits[BITMAP_ITERATIONS];

        readWriteLock.lockWriteLevel(hashDigits[1]);

        try {

            final BitMapNode node = this.seek(hash, true, hashDigits);
            if (node != null && node.next[hashDigit] > 0) {
                final LongRecordReference[] references = this.getLongRecordReferences(node, (long)o, hashDigits);
                if (references != null && references[1] != null) {
                    delete(node, references[0], references[1], hashDigits, (long) o);
                    return true;
                }
            }

        } finally {
            readWriteLock.unlockWriteLevel(hashDigits[1]);
        }

        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        Iterator iterator = c.iterator();
        while (iterator.hasNext())
        {
            add((E)iterator.next());
        }
        return true;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        this.clear();
        this.addAll((Collection<E>)c);
        return true;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        Iterator iterator = c.iterator();
        while (iterator.hasNext())
        {
            remove(iterator.next());
        }
        return true;
    }

    @Override
    public void clear() {
        readWriteLock.writeLock().lock();

        try
        {
            header.firstNode = 0;
            header.recordCount.set(0);
            updateHeaderRecordCount();

            fileStore.write(header, header.position);

        } finally
        {
            readWriteLock.writeLock().unlock();
        }
    }

    /**
     * Write object to a storage stream.  This is so that it may serialize and deserialize so the disk set
     * can be stored as a reference rather than its contents.
     *
     * @param buffer Object buffer to write to
     * @throws IOException Generic write exception
     */
    @Override
    public void writeObject(ObjectBuffer buffer) throws IOException {
        buffer.writeObject(header);
    }

    /**
     * Read Object from an object buffer.  This reads its reference and hydrates it based on the header information.
     *
     * @param buffer Object buffer to read from
     * @throws IOException General exception ocurred when reading object
     */
    @Override
    public void readObject(ObjectBuffer buffer) throws IOException {
        header = (Header)buffer.readObject();
    }

    /**
     * The purpose of this is to attach the data structure to the storage mechanism.  If the data structure gets
     * serialized and de-serialized, it may not have reference to its underlying file storage mechanism.
     *
     * @param mapBuilder Map builder that contains reference to its storage
     */
    public void attachStorage(MapBuilder mapBuilder)
    {
        if(fileStore == null)
        {
            fileStore = mapBuilder.getStore();
        }
    }
}
