/* 
 * Copyright (c) 2008-2010, Hazel Ltd. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hazelcast.client;

import com.hazelcast.core.EntryListener;
import com.hazelcast.core.IMap;
import com.hazelcast.core.MapEntry;
import com.hazelcast.impl.CMap.CMapEntry;
import com.hazelcast.impl.ClusterOperation;
import com.hazelcast.monitor.LocalMapStats;
import com.hazelcast.query.Predicate;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.hazelcast.client.ProxyHelper.check;
import static com.hazelcast.client.Serializer.toByte;

public class MapClientProxy<K, V> implements IMap<K, V>, EntryHolder {
    final ProxyHelper proxyHelper;
    final private String name;

    public MapClientProxy(HazelcastClient client, String name) {
        this.name = name;
        this.proxyHelper = new ProxyHelper(name, client);
    }

    public void addEntryListener(EntryListener<K, V> listener, boolean includeValue) {
        addEntryListener(listener, null, includeValue);
    }

    public void addEntryListener(EntryListener<K, V> listener, K key, boolean includeValue) {
        check(listener);
        if (proxyHelper.getHazelcastClient().listenerManager.entryListenerManager.noEntryListenerRegistered(key, name)) {
            Packet request = proxyHelper.createRequestPacket(ClusterOperation.ADD_LISTENER, toByte(key), null);
            request.setLongValue(includeValue ? 1 : 0);
            Call c = proxyHelper.createCall(request);
            proxyHelper.getHazelcastClient().listenerManager.addListenerCall(c);
            proxyHelper.doCall(c);
        }
        proxyHelper.getHazelcastClient().listenerManager.entryListenerManager.registerEntryListener(name, key, listener);
    }

    public void removeEntryListener(EntryListener<K, V> listener) {
        check(listener);
        proxyHelper.doOp(ClusterOperation.REMOVE_LISTENER, null, null);
        proxyHelper.getHazelcastClient().listenerManager.entryListenerManager.removeEntryListener(name, null, listener);
    }

    public void removeEntryListener(EntryListener<K, V> listener, K key) {
        check(listener);
        check(key);
        proxyHelper.doOp(ClusterOperation.REMOVE_LISTENER, key, null);
        proxyHelper.getHazelcastClient().listenerManager.entryListenerManager.removeEntryListener(name, key, listener);
    }

    public Set<java.util.Map.Entry<K, V>> entrySet(Predicate predicate) {
        Set<K> keySet = null;
        if (predicate == null) {
            keySet = keySet();
        } else {
            keySet = keySet(predicate);
        }
        return new LightEntrySet<K, V>(keySet, this, getInstanceType());
    }

    public boolean evict(Object key) {
        ProxyHelper.check(key);
        return (Boolean) proxyHelper.doOp(ClusterOperation.CONCURRENT_MAP_EVICT, key, null);
    }

    public MapEntry<K, V> getMapEntry(K key) {
        ProxyHelper.check(key);
        CMapEntry cMapEntry = (CMapEntry) proxyHelper.doOp(ClusterOperation.CONCURRENT_MAP_GET_MAP_ENTRY, key, null);
        if (cMapEntry == null) {
            return null;
        }
        MapEntry<K, V> mapEntry = new ClientMapEntry(cMapEntry, key, this);
        return mapEntry;
    }

    public Set<K> keySet(Predicate predicate) {
        final Collection<K> collection = proxyHelper.keys(predicate);
        LightKeySet<K> set = new LightKeySet<K>(this, new HashSet<K>(collection));
        return set;
    }

    public boolean lockMap(long time, TimeUnit timeunit) {
        ProxyHelper.checkTime(time, timeunit);
        return (Boolean) doLock(ClusterOperation.CONCURRENT_MAP_LOCK_MAP, null, time, timeunit);
    }

    public void unlockMap() {
        doLock(ClusterOperation.CONCURRENT_MAP_UNLOCK_MAP, null, -1, null);
    }

    public void lock(K key) {
        ProxyHelper.check(key);
        doLock(ClusterOperation.CONCURRENT_MAP_LOCK, key, -1, null);
    }

    public boolean tryLock(K key) {
        check(key);
        return (Boolean) doLock(ClusterOperation.CONCURRENT_MAP_LOCK, key, 0, null);
    }

    public boolean tryLock(K key, long time, TimeUnit timeunit) {
        check(key);
        ProxyHelper.checkTime(time, timeunit);
        return (Boolean) doLock(ClusterOperation.CONCURRENT_MAP_LOCK, key, time, timeunit);
    }

    public void unlock(K key) {
        check(key);
        proxyHelper.doOp(ClusterOperation.CONCURRENT_MAP_UNLOCK, key, null);
    }

    public Collection<V> values(Predicate predicate) {
        Set<Entry<K, V>> set = entrySet(predicate);
        return new ValueCollection<K, V>(this, set);
    }

    public V putIfAbsent(K key, V value, long ttl, TimeUnit timeunit) {
        check(key);
        check(value);
        ProxyHelper.checkTime(ttl, timeunit);
        long micros = timeunit.toMillis(ttl);
        Packet request = proxyHelper.prepareRequest(ClusterOperation.CONCURRENT_MAP_PUT_IF_ABSENT, key, value);

        request.setTimeout(micros);
        Packet response = proxyHelper.callAndGetResult(request);
        return (V)proxyHelper.getValue(response);

    }

    public V putIfAbsent(K key, V value) {
        return (V) proxyHelper.doOp(ClusterOperation.CONCURRENT_MAP_PUT_IF_ABSENT, key, value);
    }

    public boolean remove(Object arg0, Object arg1) {
        check(arg0);
        check(arg1);
        return (Boolean) proxyHelper.doOp(ClusterOperation.CONCURRENT_MAP_REMOVE_IF_SAME, arg0, arg1);
    }

    public V replace(K arg0, V arg1) {
        check(arg0);
        check(arg1);
        return (V) proxyHelper.doOp(ClusterOperation.CONCURRENT_MAP_REPLACE_IF_NOT_NULL, arg0, arg1);
    }

    public boolean replace(K arg0, V arg1, V arg2) {
        check(arg0);
        check(arg1);
        check(arg2);
        Object[] arr = new Object[2];
        arr[0] = arg1;
        arr[1] = arg2;
        return (Boolean) proxyHelper.doOp(ClusterOperation.CONCURRENT_MAP_REPLACE_IF_SAME, arg0, arr);
    }

    public void clear() {
        Set keys = keySet();
        for (Object key : keys) {
            remove(key);
        }
    }

    public boolean containsKey(Object arg0) {
        check(arg0);
        return (Boolean) proxyHelper.doOp(ClusterOperation.CONCURRENT_MAP_CONTAINS, arg0, null);
    }

    public boolean containsValue(Object arg0) {
        check(arg0);
        return (Boolean) proxyHelper.doOp(ClusterOperation.CONCURRENT_MAP_CONTAINS_VALUE, null, arg0);
    }

    public Set<java.util.Map.Entry<K, V>> entrySet() {
        return entrySet(null);
    }

    public V get(Object key) {
        check(key);
        return (V) proxyHelper.doOp(ClusterOperation.CONCURRENT_MAP_GET, (K) key, null);
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public Set<K> localKeySet() {
        throw new UnsupportedOperationException();
    }

    public Set<K> localKeySet(Predicate predicate) {
        throw new UnsupportedOperationException();
    }

    public LocalMapStats getLocalMapStats() {
        throw new UnsupportedOperationException();
    }

    public Set<K> keySet() {
        return keySet(null);
    }

    public V put(K key, V value) {
        check(key);
        check(value);
        return (V) proxyHelper.doOp(ClusterOperation.CONCURRENT_MAP_PUT, key, value);
    }

    public V put(K key, V value, long ttl, TimeUnit timeunit) {
        throw new UnsupportedOperationException();
    }

    public boolean tryPut(K key, V value, long timeout, TimeUnit timeunit) {
        throw new UnsupportedOperationException();
    }

    public void putAll(final Map<? extends K, ? extends V> map) {
        ExecutorService es = Executors.newFixedThreadPool(50);
        List<Future> lsFutures = new ArrayList(map.size());
        for (Iterator<? extends K> it = map.keySet().iterator(); it.hasNext();) {
            final K key = (K) it.next();
            final V value = map.get(key);
            lsFutures.add(es.submit(new Runnable() {
                public void run() {
                    put(key, value);
                }
            }));
        }
        for (Future future : lsFutures) {
            try {
                future.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        es.shutdown();
    }

    public V remove(Object arg0) {
        check(arg0);
        return (V) proxyHelper.doOp(ClusterOperation.CONCURRENT_MAP_REMOVE, arg0, null);
    }

    private Object doLock(ClusterOperation operation, Object key, long timeout, TimeUnit timeUnit) {
        Packet request = proxyHelper.prepareRequest(operation, key, timeUnit);
        request.setTimeout(timeout);
        Packet response = proxyHelper.callAndGetResult(request);
        return proxyHelper.getValue(response);
    }

    public int size() {
        return (Integer) proxyHelper.doOp(ClusterOperation.CONCURRENT_MAP_SIZE, null, null);
    }

    public Collection<V> values() {
        return values(null);
    }

    public Object getId() {
        return name;
    }

    public InstanceType getInstanceType() {
        return InstanceType.MAP;
    }

    public void addIndex(String attribute, boolean ordered) {
        proxyHelper.doOp(ClusterOperation.ADD_INDEX, attribute, ordered);
    }

    public String getName() {
        return name.substring(2);
    }

    public void destroy() {
        proxyHelper.destroy();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof IMap && o != null) {
            return getName().equals(((IMap) o).getName());
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }
}
