package com.onyx.map;

import com.onyx.map.node.Header;
import com.onyx.map.serializer.ObjectBuffer;
import com.onyx.map.serializer.ObjectSerializable;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Created by tosborn1 on 8/31/16.
 *
 * This class is a HashSet that interacts with disk rather than in memory.
 * This is just an empty shell that uses a DiskMap to drive the persistence
 * of the data.
 */
public class DefaultDiskSet<E> implements ObjectSerializable, Set<E> {

    // Disk Map the HashSet is based on.  The value just defaults to null
    // The only overhead is 1 byte per record.
    protected DiskMap underlyingDiskMap = null;

    // Location to where the disk map is located.  This is to be used as
    // a reference marker so, a serialized disk map can be referenced
    // within another data structure
    protected Header header;

    /**
     * Constructor with no parameters
     */
    @SuppressWarnings("unused")
    public DefaultDiskSet()
    {

    }

    /**
     * Default Constructor
     *
     * @param diskMap Underlying Disk map
     * @param header Header reference
     * @since 1.0.2
     */
    public DefaultDiskSet(DiskMap diskMap, Header header) {
        this.underlyingDiskMap = diskMap;
        this.header = header;
    }

    /**
     * Amount of unique records within the Set
     * @return Size of Set
     * @since 1.0.2
     */
    @Override
    public int size() {
        return this.underlyingDiskMap.size();
    }

    /**
     * Whether the disk set is empty or not
     * @return size == 0
     * @since 1.0.2
     */
    @Override
    public boolean isEmpty() {
        return this.underlyingDiskMap.isEmpty();
    }

    /**
     * Whether the object parameter exist within the data structure.  For this to be true
     * it must have a matching hash code and return true from .equals method.
     *
     * @param o Object to compare
     * @return Whether the object exist within the data structure
     * @since 1.0.2
     */
    @Override
    public boolean contains(Object o) {
        return this.underlyingDiskMap.containsKey(o);
    }

    /**
     * Returns an iterator over elements of type {@code T}.
     * This is not mutable safe.  If you mutate the set while iterating, you may encounter issues
     *
     * @return an Iterator.
     * @since 1.0.2
     */
    @Override
    public Iterator<E> iterator() {
        return this.underlyingDiskMap.keySet().iterator();
    }

    /**
     * Performs the given action for each element of the {@code Iterable}
     * until all elements have been processed or the action throws an
     * exception.  Unless otherwise specified by the implementing class,
     * actions are performed in the order of iteration (if an iteration order
     * is specified).  Exceptions thrown by the action are relayed to the
     * caller.
     *
     * This is not mutable safe.  If you mutate the set while iterating, you may encounter issues.  You can use the ObservableDiskSet
     * if you require it to be mutable safe.
     *
     * @implSpec
     * <p>The default implementation behaves as if:
     * <pre>{@code
     *     for (T t : this)
     *         action.accept(t);
     * }</pre>
     *
     * @param action The action to be performed for each element
     * @throws NullPointerException if the specified action is null
     * @since 1.0.2
     */
    @Override
    public void forEach(Consumer<? super E> action) {
        Iterator iterator = this.underlyingDiskMap.keySet().iterator();
        while (iterator.hasNext()) {
            E key = (E) iterator.next();
            action.accept(key);
        }
    }

    /**
     * Returns an array containing all of the elements in this set.
     * If this set makes any guarantees as to what order its elements
     * are returned by its iterator, this method must return the
     * elements in the same order.
     *
     * <p>The returned array will be "safe" in that no references to it
     * are maintained by this set.  (In other words, this method must
     * allocate a new array even if this set is backed by an array).
     * The caller is thus free to modify the returned array.
     *
     * <p>This method acts as bridge between array-based and collection-based
     * APIs.
     *
     * @since 1.0.2
     * @return an array containing all the elements in this set
     */
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

    /**
     * Returns an array containing all of the elements in this set; the
     * runtime type of the returned array is that of the specified array.
     * If the set fits in the specified array, it is returned therein.
     * Otherwise, a new array is allocated with the runtime type of the
     * specified array and the size of this set.
     *
     * <p>If this set fits in the specified array with room to spare
     * (i.e., the array has more elements than this set), the element in
     * the array immediately following the end of the set is set to
     * <tt>null</tt>.  (This is useful in determining the length of this
     * set <i>only</i> if the caller knows that this set does not contain
     * any null elements.)
     *
     * <p>If this set makes any guarantees as to what order its elements
     * are returned by its iterator, this method must return the elements
     * in the same order.
     *
     * <p>Like the {@link #toArray()} method, this method acts as bridge between
     * array-based and collection-based APIs.  Further, this method allows
     * precise control over the runtime type of the output array, and may,
     * under certain circumstances, be used to save allocation costs.
     *
     * <p>Suppose <tt>x</tt> is a set known to contain only strings.
     * The following code can be used to dump the set into a newly allocated
     * array of <tt>String</tt>:
     *
     * <pre>
     *     String[] y = x.toArray(new String[0]);</pre>
     *
     * Note that <tt>toArray(new Object[0])</tt> is identical in function to
     * <tt>toArray()</tt>.
     *
     * @param a the array into which the elements of this set are to be
     *        stored, if it is big enough; otherwise, a new array of the same
     *        runtime type is allocated for this purpose.
     * @return an array containing all the elements in this set
     * @throws ArrayStoreException if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in this
     *         set
     * @since 1.0.2
     * @throws NullPointerException if the specified array is null
     */
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

    /**
     * Adds the specified element to this set if it is not already present
     * (optional operation).  More formally, adds the specified element
     * <tt>e</tt> to this set if the set contains no element <tt>e2</tt>
     * such that
     * <tt>(e==null&nbsp;?&nbsp;e2==null&nbsp;:&nbsp;e.equals(e2))</tt>.
     * If this set already contains the element, the call leaves the set
     * unchanged and returns <tt>false</tt>.  In combination with the
     * restriction on constructors, this ensures that sets never contain
     * duplicate elements.
     *
     * <p>The stipulation above does not imply that sets must accept all
     * elements; sets may refuse to add any particular element, including
     * <tt>null</tt>, and throw an exception, as described in the
     * specification for {@link Collection#add Collection.add}.
     * Individual set implementations should clearly document any
     * restrictions on the elements that they may contain.
     *
     * @param e element to be added to this set
     * @return <tt>true</tt> if this set did not already contain the specified
     *         element
     * @throws NullPointerException if the specified element is null and this
     *         set does not permit null elements
     * @since 1.0.3
     */
    @Override
    public boolean add(E e) {
        this.underlyingDiskMap.put(e, null);
        return true;
    }

    /**
     * Removes the specified element from this set if it is present
     * (optional operation).  More formally, removes an element <tt>e</tt>
     * such that
     * <tt>(o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e))</tt>, if
     * this set contains such an element.  Returns <tt>true</tt> if this set
     * contained the element (or equivalently, if this set changed as a
     * result of the call).  (This set will not contain the element once the
     * call returns.)
     *
     * @param o object to be removed from this set, if present
     * @return <tt>true</tt> if this set contained the specified element
     * @throws NullPointerException if the specified element is null and this
     *         set does not permit null elements
     */
    @Override
    public boolean remove(Object o) {
        Object object = this.underlyingDiskMap.remove(o);
        return (object != null);
    }

    /**
     * Returns <tt>true</tt> if this set contains all of the elements of the
     * specified collection.  If the specified collection is also a set, this
     * method returns <tt>true</tt> if it is a <i>subset</i> of this set.
     *
     * @param  c collection to be checked for containment in this set
     * @return <tt>true</tt> if this set contains all of the elements of the
     *         specified collection
     * @throws ClassCastException if the types of one or more elements
     *         in the specified collection are incompatible with this
     *         set
     * @since 1.0.2
     * (<a href="Collection.html#optional-restrictions">optional</a>)
     * @throws NullPointerException if the specified collection contains one
     *         or more null elements and this set does not permit null
     *         elements
     * (<a href="Collection.html#optional-restrictions">optional</a>),
     *         or if the specified collection is null
     * @see    #contains(Object)
     * @since 1.0.2
     */
    @Override
    public boolean containsAll(Collection<?> c) {
        Iterator iterator = c.iterator();
        while (iterator.hasNext()) {
            Object key = iterator.next();
            if (!this.underlyingDiskMap.containsKey(key))
                return false;
        }
        return true;
    }

    /**
     * Adds all of the elements in the specified collection to this set if
     * they're not already present (optional operation).  If the specified
     * collection is also a set, the <tt>addAll</tt> operation effectively
     * modifies this set so that its value is the <i>union</i> of the two
     * sets.  The behavior of this operation is undefined if the specified
     * collection is modified while the operation is in progress.
     *
     * @param  c collection containing elements to be added to this set
     * @return <tt>true</tt> if this set changed as a result of the call
     *
     * @throws UnsupportedOperationException if the <tt>addAll</tt> operation
     *         is not supported by this set
     * @throws ClassCastException if the class of an element of the
     *         specified collection prevents it from being added to this set
     * @throws NullPointerException if the specified collection contains one
     *         or more null elements and this set does not permit null
     *         elements, or if the specified collection is null
     * @throws IllegalArgumentException if some property of an element of the
     *         specified collection prevents it from being added to this set
     * @see #add(Object)
     */
    @Override
    public boolean addAll(Collection<? extends E> c) {
        Iterator iterator = c.iterator();
        while (iterator.hasNext()) {
            Object key = iterator.next();
            this.underlyingDiskMap.put(key, null);
        }
        return true;
    }

    /**
     * Retains only the elements in this set that are contained in the
     * specified collection (optional operation).  In other words, removes
     * from this set all of its elements that are not contained in the
     * specified collection.  If the specified collection is also a set, this
     * operation effectively modifies this set so that its value is the
     * <i>intersection</i> of the two sets.
     *
     * @param  c collection containing elements to be retained in this set
     * @return <tt>true</tt> if this set changed as a result of the call
     * @throws NullPointerException if this set contains a null element and the
     *         specified collection does not permit null elements
     *         (<a href="Collection.html#optional-restrictions">optional</a>),
     *         or if the specified collection is null
     * @see #remove(Object)
     */
    @Override
    public boolean retainAll(Collection<?> c) {
        this.clear();
        addAll((Collection<E>)c);
        return true;
    }

    /**
     * Removes from this set all of its elements that are contained in the
     * specified collection (optional operation).  If the specified
     * collection is also a set, this operation effectively modifies this
     * set so that its value is the <i>asymmetric set difference</i> of
     * the two sets.
     *
     * @param  c collection containing elements to be removed from this set
     * @return <tt>true</tt> if this set changed as a result of the call
     * @throws NullPointerException if this set contains a null element and the
     *         specified collection does not permit null elements
     *         (<a href="Collection.html#optional-restrictions">optional</a>),
     *         or if the specified collection is null
     * @see #remove(Object)
     * @see #contains(Object)
     * @since 1.0.2
     */
    @Override
    public boolean removeAll(Collection<?> c) {
        Iterator iterator = c.iterator();
        while (iterator.hasNext()) {
            Object key = iterator.next();
            this.underlyingDiskMap.remove(key);
        }
        return true;
    }

    /**
     * Removes all of the elements from this collection (optional operation).
     * The collection will be empty after this method returns.
     *
     * @since 1.0.2
     */
    @Override
    public void clear() {
        this.underlyingDiskMap.clear();
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
        buffer.writeObject(this.underlyingDiskMap.getFileStore().getFilePath());
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
        String path = (String)buffer.readObject();
        final MapBuilder mapBuilder = DefaultMapBuilder.getMapBuilder(path);
        underlyingDiskMap = (DiskMap)mapBuilder.getDiskMap(header);
    }
}
