package org.abazilev;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author abazilev
 *         <p/>
 *         A thread-safe object pool implementation.
 *         <p/>
 *         Usual pattern of usage is:
 *         <code>SimplePoolImpl<Integer> pool = new SimplePoolImpl<Integer>();
 *         <p/>
 *         pool.add(10);
 *         pool.add(11);
 *         <p/>
 *         pool.open();
 *         <p/>
 *         try {
 *         Integer obj = pool.acquire();
 *         pool.release(obj);
 *         } catch (PoolIsClosedException e) {
 *         e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
 *         }
 *         <p/>
 *         pool.close();
 *         <p/>
 *         Please try avoid using removeNow and closeNow methods.
 *         </code>
 */
public class SimplePoolImpl<R> implements Pool<R> {

    /**
     * The internal representation of resource entry. This class has it's own contract.
     *
     * @param <R> the type of target resource
     */
    private static class Entry<R> {
        private final R resource;

        private boolean isUsed = false;

        private volatile boolean isAvailable = true;

        public Entry(R resource) {
            this.resource = resource;
        }

        public R getResource() {
            return resource;
        }

        /**
         * @return true if entry with resource is free for acquiring
         */
        public synchronized boolean isFree() {
            return !isUsed && isAvailable;
        }

        /**
         * @return true if entry is available and is in use -
         *         resource was previously acquired
         */
        public synchronized boolean isOccupied() {
            return isUsed && isAvailable;
        }

        public synchronized void free() {
            isUsed = false;
            notifyAll();
        }

        public synchronized R occupy(CloseManager closeManager)
                throws PoolIsClosedException {
            if(isFree()){
                closeManager.inc();
                isUsed = true;
                return resource;
            }

            return null;
        }

        public synchronized void invalidate() {
            isAvailable = false;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Entry)) return false;

            Entry entry = (Entry) o;

            if (!resource.equals(entry.resource)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return resource.hashCode();
        }
    }

    //stores the set of resources in pool
    private final Set<Entry<R>> resources = new HashSet<>();

    private final ReentrantReadWriteLock resourceRWLock = new ReentrantReadWriteLock();
    private Condition noResourcesToAcquire = resourceRWLock.writeLock().newCondition();

    /**
     * This class manages the state of open-close cycle of pool.
     */
    private static class CloseManager {

        private int counter;

        private boolean isClosed = true;

        public synchronized void inc() throws PoolIsClosedException {
            if (isClosed) {
                throw new PoolIsClosedException();
            }

            counter++;
        }

        public synchronized void dec() {
            if (counter > 0) {
                counter--;
            }

            if (counter == 0) {
                notifyAll();
            }
        }

        public synchronized void waitForClose() {
            while (counter > 0 && (!Thread.currentThread().isInterrupted())) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            if (counter == 0) {
                isClosed = true;
            }
        }

        public synchronized void close() {
            isClosed = true;
        }

        public synchronized void open() {
            isClosed = false;
        }

        public synchronized boolean isOpen() {
            return !isClosed;
        }
    }

    private final CloseManager closeManager = new CloseManager();

    @Override
    public void open() {
        closeManager.open();
    }

    @Override
    public boolean isOpen() {
        return closeManager.isOpen();
    }

    @Override
    public void close() {
        closeManager.waitForClose();
    }

    /**
     * Acquires a resource. Will wait for resource being available.
     *
     * @return acquired resource or null if thread was interrupted
     * @throws PoolIsClosedException if pool is closed during execution
     */
    @Override
    public R acquire() throws PoolIsClosedException {
        return doAcquire(-1);
    }

    private R doAcquire(long timeout) throws PoolIsClosedException {
        if (!isOpen()) {
            throw new PoolIsClosedException();
        }

        Entry<R> availableEntry;
        R resource = null;

        final long startTime = System.currentTimeMillis();

        while (resource == null && (!Thread.currentThread().isInterrupted()) &&
                shouldRepeat(startTime, timeout)) {
            //first call - just a small optimization
            resourceRWLock.readLock().lock();
            try {
                availableEntry = getAvailableResource();
            } finally {
                resourceRWLock.readLock().unlock();
            }

            if (availableEntry == null) {
                resourceRWLock.writeLock().lock();
                try {
                    while ((availableEntry = getAvailableResource()) == null &&
                            (!Thread.currentThread().isInterrupted()) && shouldRepeat(startTime, timeout)) {
                        try {
                            if (timeout <= 0) {
                                noResourcesToAcquire.await();
                            } else {
                                noResourcesToAcquire.awaitNanos(getNanoseconds(timeout));
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                } finally {
                    resourceRWLock.writeLock().unlock();
                }
            }

            if (availableEntry != null) {
                resource = availableEntry.occupy(closeManager);
            }
        }

        return resource;
    }

    private long getNanoseconds(long milliSeconds) {
        long nanoSeconds = milliSeconds * 1000_000;
        if(nanoSeconds < 0) {
            //overflow
            nanoSeconds = Long.MAX_VALUE;
        }

        return nanoSeconds;
    }

    private boolean shouldRepeat(long startTime, long timeout) {
        if (timeout <= 0) {
            return true;
        }

        long now = System.currentTimeMillis();
        return (now - startTime) <= timeout;
    }

    private Entry<R> getAvailableResource() {
        for (Entry<R> entry : resources) {
            if (entry.isFree()) {
                return entry;
            }
        }

        return null;
    }

    @Override
    public R acquire(long timeout, TimeUnit timeUnit) throws PoolIsClosedException {
        if (timeout <= 0 || timeUnit == null) {
            throw new IllegalArgumentException("Timeout is: " + timeout +
                    ", timeUnit: " + timeUnit);
        }

        return doAcquire(timeUnit.toMillis(timeout));
    }

    @Override
    public void release(R resource) {
        if (resource == null) {
            throw new NullPointerException("Resource cannot be null!");
        }

        Entry<R> entry = findEntry(resource);

        if (entry != null) {
            boolean shouldNotify = false;

            synchronized (entry) {
                if (entry.isOccupied()) {
                    entry.free();
                    closeManager.dec();
                    shouldNotify = true;
                }
            }

            //prevent deadlock
            if (shouldNotify) {
                resourceRWLock.writeLock().lock();
                try {
                    noResourcesToAcquire.signalAll();
                } finally {
                    resourceRWLock.writeLock().unlock();
                }
            }
        }
    }

    @Override
    public boolean add(R resource) {
        if (resource == null) {
            throw new NullPointerException("Resource cannot be null!");
        }

        boolean isAdded = false;

        resourceRWLock.writeLock().lock();
        try {
            if(isAdded = resources.add(new Entry<>(resource))) {
                noResourcesToAcquire.signalAll();
            }
        }finally {
            resourceRWLock.writeLock().unlock();
        }

        return isAdded;
    }

    @Override
    public boolean remove(R resource) {
        if (resource == null) {
            throw new NullPointerException("Resource cannot be null!");
        }

        Entry<R> foundEntry = findEntry(resource);

        if (foundEntry != null) {
            boolean shouldRemove = false;

            synchronized (foundEntry) {
                while (foundEntry.isOccupied() && (!Thread.currentThread().isInterrupted())) {
                    try {
                        foundEntry.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                if (foundEntry.isFree()) {
                    foundEntry.invalidate();
                    shouldRemove = true;
                }
            }
            //deadlock prevention
            if(shouldRemove) {
                return removeEntry(foundEntry);
            }
        }

        return false;
    }

    private Entry<R> findEntry(R resource) {
        resourceRWLock.readLock().lock();
        try {
            for (Entry<R> entry : resources) {
                if (entry.getResource().equals(resource)) {
                    return entry;
                }
            }
        }finally {
            resourceRWLock.readLock().unlock();
        }

        return null;
    }

    /**
     * Unsafe method - can lead to IllegalState exception!
     *
     * @param resource resource to be removed
     * @return boolean if resource was removed
     */
    @Override
    public boolean removeNow(R resource) {
        if (resource == null) {
            throw new NullPointerException("Resource cannot be null!");
        }

        Entry<R> foundEntry = findEntry(resource);

        if (foundEntry != null) {
            foundEntry.invalidate();
            return removeEntry(foundEntry);
        }

        return false;
    }

    private boolean removeEntry(Entry<R> entry) {
        resourceRWLock.writeLock().lock();
        try {
            return resources.remove(entry);
        }finally {
            resourceRWLock.writeLock().unlock();
        }
    }

    /**
     * Unsafe method - may lead to PoolIsClosedExceptions, please try to use close().
     */
    @Override
    public void closeNow() {
        closeManager.close();
    }
}
