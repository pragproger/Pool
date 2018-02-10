package org.abazilev;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;


public class Tests {

    private Pool<String> pool;

    @Before
    public void init() {
       pool = new SimplePoolImpl<>();
    }

    @Test
    public void shouldRemoveResource() throws Exception {
        pool.open();
        String resource = "Some resource";
        pool.add(resource);
        assertTrue(pool.remove(resource));
        pool.add("Pool");
        assertThat(pool.acquire(), is("Pool"));
        pool.closeNow();
        assertFalse(pool.isOpen());
    }

    @Test(expected = PoolIsClosedException.class)
    public void testAcquireClosed() throws PoolIsClosedException {
        //prepare data
        pool.add("Hello");
        //make test
        pool.acquire();
    }

    @Test
    public void testAcquire() throws PoolIsClosedException {
       //prepare data
        pool.add("Hello");
        pool.add("Preved");
        pool.open();
        //perform test
        String str1 = pool.acquire();
        String str2 = pool.acquire();
        //check results
        assertNotNull(str1);
        assertNotNull(str2);
        assertThat(str1.equals("Hello") || str1.equals("Preved"), is(true));
        //continue tests
        pool.release("Hello");
        pool.release("Preved");
        assertTrue(pool.remove("Hello"));
        assertFalse(pool.remove("Privet"));
    }

    @Test
    public void testOpenClose() {
        //prepare tests
        assertFalse(pool.isOpen());
        pool.open();
        assertTrue(pool.isOpen());
        //some additional check
        pool.release("Hello111");
        pool.close();
        assertFalse(pool.isOpen());
        pool.open();
        assertTrue(pool.isOpen());
        pool.closeNow();
        assertFalse(pool.isOpen());
    }

    @Test
    public void removeNow() throws PoolIsClosedException {
       //prepare
        pool.add("Hello");
        pool.open();
        assertThat(pool.acquire(), is("Hello"));
        assertTrue(pool.removeNow("Hello"));
        assertFalse(pool.remove("Hello"));
    }

    @Test
    public void acquireTimeout() throws PoolIsClosedException {
        //prepare
        pool.open();
        assertNull(pool.acquire(2000, TimeUnit.MILLISECONDS));
        pool.close();
    }

    @Test
    public void acquireReleaseRemove() throws PoolIsClosedException {
        //prepare
        pool.add("loop");
        pool.open();
        assertEquals(pool.acquire(), "loop");
        pool.release("loop");
        assertTrue(pool.remove("loop"));
        assertNull(pool.acquire(2000, TimeUnit.MILLISECONDS));
        pool.close();
        assertFalse(pool.isOpen());
    }

    @Test
    public void testCloseNow() throws PoolIsClosedException {
        //prepare
        pool.add("Hello");
        pool.open();
        assertThat(pool.acquire(), is("Hello"));
        pool.closeNow();
        assertFalse(pool.isOpen());
    }

}
