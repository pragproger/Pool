package org.abazilev;

/**
 * @author abazilev
 *
 * Is thrown when pool is closed and some operation is executed.
 */
public class PoolIsClosedException extends Exception {

    public PoolIsClosedException() {
    }
}
