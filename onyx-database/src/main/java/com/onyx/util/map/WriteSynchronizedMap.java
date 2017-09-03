package com.onyx.util.map;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.*;
import java.util.function.*;
import java.util.stream.Stream;

/**
 * Created by tosborn1 on 3/24/17.
 *
 * This map only synchronizes on write access.  It is non blocking for read access.  It allows
 * Onyx database to be free from read constraints.
 */
public class WriteSynchronizedMap<K,V> implements CompatMap<K,V> {

    private final CompatMap<K, V> m;     // Backing Map
    private final Object mutex;        // Object on which to synchronize

    /**
     * Constructor without parameters
     *
     * @since 1.2.2
     */
    @SuppressWarnings("unused")
    public WriteSynchronizedMap() {
        this(new CompatHashMap<>());
    }

    /**
     * Constructor with base map
     *
     * @param m base map
     * @since 1.2.2
     */
    public WriteSynchronizedMap(CompatMap<K, V> m) {
        this.m = Objects.requireNonNull(m);
        mutex = this;
    }

    /**
     * Size
     * @return get size of base map
     * @since 1.2.2
     */
    public int size() {
        return m.size();
    }

    /**
     * Map is Empty
     * @return size == 0
     * @since 1.2.2
     */
    public boolean isEmpty() {
        return m.isEmpty();
    }

    /**
     * Contains Key
     * @param key Map key
     * @return whether that object exist within the map
     * @since 1.2.2
     */
    public boolean containsKey(Object key) {
        return m.containsKey(key);
    }

    /**
     * Contains Value
     * @param value value to search for
     * @return Whether that value exist within the map
     * @since 1.2.2
     */
    public boolean containsValue(Object value) {
        return m.containsValue(value);
    }

    public V get(Object key) {
        return m.get(key);
    }

    public V put(K key, V value) {
        synchronized (mutex) {
            return m.put(key, value);
        }
    }

    public V remove(Object key) {
        synchronized (mutex) {
            return m.remove(key);
        }
    }

    public void putAll(Map<? extends K, ? extends V> map) {
        synchronized (mutex) {
            m.putAll(map);
        }
    }

    public void clear() {
        synchronized (mutex) {
            m.clear();
        }
    }

    private transient Set<K> keySet;
    private transient Set<Map.Entry<K, V>> entrySet;
    private transient Collection<V> values;

    public Set<K> keySet() {
        if (keySet == null)
            keySet = new WriteSynchronizedMap.SynchronizedSet<>(m.keySet(), mutex);
        return keySet;
    }

    public Set<Map.Entry<K, V>> entrySet() {
        if (entrySet == null)
            entrySet = new WriteSynchronizedMap.SynchronizedSet<>(m.entrySet(), mutex);
        return entrySet;
    }

    public Collection<V> values() {
        synchronized (mutex) {
            if (values == null)
                values = new WriteSynchronizedMap.SynchronizedCollection<>(m.values(), mutex);
            return values;
        }
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    public boolean equals(Object o) {
        return this == o || m.equals(o);
    }

    public int hashCode() {
        return m.hashCode();
    }

    public String toString() {
        return m.toString();
    }

    // Override default methods in Map
    @Override
    public V getOrDefault(Object k, V defaultValue) {
        synchronized (mutex) {
            return m.getOrDefault(k, defaultValue);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void forEach(BiConsumer<? super K, ? super V> action) {
        Set<Map.Entry<K,V>> entrySet = m.entrySet();
        for(Map.Entry entry : entrySet) {
            action.accept((K)entry.getKey(), (V)entry.getValue());
        }
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        synchronized (mutex) {
            m.replaceAll(function);
        }
    }

    @Override
    public V putIfAbsent(K key, V value) {
        synchronized (mutex) {
            return m.putIfAbsent(key, value);
        }
    }

    @Override
    public boolean remove(Object key, Object value) {
        synchronized (mutex) {
            return m.remove(key, value);
        }
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        synchronized (mutex) {
            return m.replace(key, oldValue, newValue);
        }
    }

    @Override
    public V replace(K key, V value) {
        synchronized (mutex) {
            return m.replace(key, value);
        }
    }

    @Override
    public V computeIfAbsent(K key,
                             Function<? super K, ? extends V> mappingFunction) {
        synchronized (mutex) {
            return m.computeIfAbsent(key, mappingFunction);
        }
    }

    @Override
    public V computeIfPresent(K key,
                              BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        synchronized (mutex) {
            return m.computeIfPresent(key, remappingFunction);
        }
    }

    @Override
    public V compute(K key,
                     BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        synchronized (mutex) {
            return m.compute(key, remappingFunction);
        }
    }

    @Override
    public V merge(K key, V value,
                   BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        synchronized (mutex) {
            return m.merge(key, value, remappingFunction);
        }
    }

    @SuppressWarnings("unused")
    private void writeObject(ObjectOutputStream s) throws IOException {
        synchronized (mutex) {
            s.defaultWriteObject();
        }
    }

    /**
     * @serial include
     */
    private static class SynchronizedSet<E>
            extends WriteSynchronizedMap.SynchronizedCollection<E>
            implements Set<E> {
        private static final long serialVersionUID = 487447009682186044L;

        SynchronizedSet(Set<E> s, Object mutex) {
            super(s, mutex);
        }

        @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
        public boolean equals(Object o) {
            if (this == o)
                return true;
            synchronized (mutex) {
                return c.equals(o);
            }
        }

        public int hashCode() {
            synchronized (mutex) {
                return c.hashCode();
            }
        }
    }

    /**
     * @serial include
     */
    static class SynchronizedCollection<E> implements Collection<E>, Serializable {
        private static final long serialVersionUID = 3053995032091335093L;

        final Collection<E> c;  // Backing Collection
        final Object mutex;     // Object on which to synchronize

        SynchronizedCollection(Collection<E> c, Object mutex) {
            this.c = Objects.requireNonNull(c);
            this.mutex = Objects.requireNonNull(mutex);
        }

        public int size() {
            synchronized (mutex) {
                return c.size();
            }
        }

        public boolean isEmpty() {
            synchronized (mutex) {
                return c.isEmpty();
            }
        }

        public boolean contains(Object o) {
            synchronized (mutex) {
                return c.contains(o);
            }
        }

        public Object[] toArray() {
            synchronized (mutex) {
                return c.toArray();
            }
        }

        @SuppressWarnings("SuspiciousToArrayCall")
        public <T> T[] toArray(T[] a) {
            synchronized (mutex) {
                return c.toArray(a);
            }
        }

        public Iterator<E> iterator() {
            return c.iterator(); // Must be manually synched by user!
        }

        public boolean add(E e) {
            synchronized (mutex) {
                return c.add(e);
            }
        }

        public boolean remove(Object o) {
            synchronized (mutex) {
                return c.remove(o);
            }
        }

        public boolean containsAll(Collection<?> coll) {
            synchronized (mutex) {
                return c.containsAll(coll);
            }
        }

        public boolean addAll(Collection<? extends E> coll) {
            synchronized (mutex) {
                return c.addAll(coll);
            }
        }

        public boolean removeAll(Collection<?> coll) {
            synchronized (mutex) {
                return c.removeAll(coll);
            }
        }

        public boolean retainAll(Collection<?> coll) {
            synchronized (mutex) {
                return c.retainAll(coll);
            }
        }

        public void clear() {
            synchronized (mutex) {
                c.clear();
            }
        }

        public String toString() {
            synchronized (mutex) {
                return c.toString();
            }
        }

        // Override default methods in Collection
        @Override
        public void forEach(Consumer<? super E> consumer) {
            synchronized (mutex) {
                c.forEach(consumer);
            }
        }

        @Override
        public boolean removeIf(Predicate<? super E> filter) {
            synchronized (mutex) {
                return c.removeIf(filter);
            }
        }

        @Override
        public Spliterator<E> spliterator() {
            return c.spliterator(); // Must be manually synched by user!
        }

        @Override
        public Stream<E> stream() {
            return c.stream(); // Must be manually synched by user!
        }

        @Override
        public Stream<E> parallelStream() {
            return c.parallelStream(); // Must be manually synched by user!
        }

        private void writeObject(ObjectOutputStream s) throws IOException {
            synchronized (mutex) {
                s.defaultWriteObject();
            }
        }
    }
}
