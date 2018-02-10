package org.abazilev;

/**
 * @author abazilev
 *
 * Simple test.
 */
public class SimpleTest {

    final static SimplePoolImpl<String> pool = new SimplePoolImpl<>();

    public static void main(String[] args) throws PoolIsClosedException, InterruptedException {

        pool.add("Hello");

        pool.open();

        String hello = pool.acquire();

        Thread thread1 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //String hello2 = pool.acquire();
                    //pool.close();
                    boolean wasRemoved = pool.remove("Hello");
                    System.out.println("Removed: "+wasRemoved);
                    //pool.release("Hello");
                    String acquired = pool.acquire();
                    System.out.println("Acquired: "+acquired);
                    pool.close();
                    System.out.println("Opened: "+pool.isOpen());
                } catch (Exception e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            }
        });

        thread1.start();

        System.out.println("Before sleep");
        Thread.sleep(5000);
        System.out.println("Release: "+hello);
        pool.release(hello);

        Thread.sleep(5000);
        pool.add("Preved");

        Thread.sleep(5000);

        pool.release("Preved");
        Thread.sleep(5000);
        System.exit(0);

    }
}
