package org.abazilev;

/**
 * @author abazilev
 *
 * Object pool interface. Resource class R has to have proper implementation of
 * equals and hashcode - because Set is used internally. In case of closeNow and removeNow methods
 * IllegalStateException can be throwed when pool tries to return removed entity - however
 * contract is not broken in any cases.
 */
public interface Pool<R> {

    /**
     * Opens the pool.
     */
    void open();

    /**
     *
     * @return the open status of a pool
     */
    boolean isOpen();

    /**
     * Closes the pool. Will wait for all acquired resources to be released.
     */
    void close();

    /**
     * Will wait if there are no available resources.
     *
     * @return acquired resource or null if thread was interrupted
     * @throws PoolIsClosedException if pool is closed while executing acquire (before
     * acquiring)
     */
    R acquire() throws PoolIsClosedException;

    /**
     * Will wait for available resources. If timeout is big,
     * then Long.MAX_VALUE will be used.
     *
     * @param timeout timeout to wait for available resources
     * @param timeUnit usually nano seconds
     * @return acquired resource or null if there is no resource available after timeout
     * or thread was interrupted
     * @throws PoolIsClosedException if pool is closed while executing acquire
     * @throws IllegalArgumentException if timeout is 0 or less than 0 or timeUnit is null
     */
    R acquire(long timeout, java.util.concurrent.TimeUnit timeUnit)
            throws PoolIsClosedException;

    /**
     * Releases resource.No exception if resource was not previously in pool.
     *
     * @param resource cannot be null
     * @throws NullPointerException if resource is null
     */
    void release(R resource);

    /**
     * Adds resource to pool.
     *
     * @param resource res to be added
     * @return true if resource was added
     * @throws NullPointerException if resource is null
     */
    boolean add(R resource);

    /**
     * Will wait for resource to be released previously.
     *
     * @param resource
     * @return true if resource was removed
     */
    boolean remove(R resource);

    /**
     * Unsafe method. Will remove release even if it is acquired. Doesn't force close()
     * method to notify because it doesn't release the resource.
     *
     * @param resource to be removed from pool
     * @return true if resource was removed.
     */
    boolean removeNow(R resource);

    /**
     * Unsafe method. Doesn't release resources.
     */
    void closeNow();

}
