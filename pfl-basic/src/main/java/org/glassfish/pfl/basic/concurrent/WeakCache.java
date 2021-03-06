/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.glassfish.pfl.basic.concurrent;

import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** A simple cache with weak keys.  get may be called safely with good
 * concurrency by multiple threads.  In order to use this, some reasonable
 * properties are expected:
 * <ul>
 * <li>The value is a function of only the key, so it may be safely cached.
 * <li>get operations are very common on the same key.
 * <li>Values may occasionally disappear from the cache, in which case
 * they will just be recomputed on the next get() call.
 * </ul>
 *
 * @author ken_admin
 */
public abstract class WeakCache<K,V> {
    private final ReadWriteLock lock ;
    private final Map<K,V> map ;

    public WeakCache() {
        lock = new ReentrantReadWriteLock() ;
        map = new WeakHashMapSafeReadLock<K,V>() ;
    }

    /** Must be implemented in a subclass.  Must compute a
     * value corresponding to a key.  The computation may be fairly
     * expensive.  Note that no lock is held during this computation.
     *
     * @param key Key value for which a value must be computed.
     * @return The resulting value.
     */
    protected abstract V lookup( K key ) ;

    /** Remove any value associated with the key.
     * 
     * @param key Key to value that may be in cache.
     * @return value from the cache, or null if none.
     */
    public V remove( K key ) {
        lock.writeLock().lock();
        try {
            return map.remove( key ) ;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Return the value (if any) associated with key.
     * If the value is already in the cache, only a read lock is held,
     * so many threads can concurrently call get.  If no value is in
     * the cache corresponding to key, a new value will be computed and
     * cached, in which case a write lock is held long enough to update
     * the map.  Note that the write lock is NOT held while the value
     * is computed by calling the lookup method.  Because of this, it
     * is possible for redundant computation to occur when two or more
     * thread concurrently call get on the same key which is not (yet) in
     * the cache.
     *
     * @param key
     * @return Value associated with the key.
     */
    public V get( K key ) {
        lock.readLock().lock() ;
        boolean readLocked = true ;
        try {
            V value = map.get( key ) ;
            if (value == null) {
                readLocked = false ;
                lock.readLock().unlock();

                value = lookup(key) ;

                lock.writeLock().lock();
                try {
                    V current = map.get( key ) ;
                    if (current == null) {
                        // Only put if this is the first time
                        map.put( key, value ) ;
                    } else {
                        value = current ;
                    }
                } finally {
                    lock.writeLock().unlock();
                }
            }

            return value ;
        } finally {
            if (readLocked) {
                lock.readLock().unlock();
            }
        }
    }

    /** Remove all entries from the cache.
     *
     */
    public void clear() {
        map.clear();
    }
}
