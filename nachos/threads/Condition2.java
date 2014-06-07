package nachos.threads;

import nachos.machine.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see	nachos.threads.Condition
 */
public class Condition2 {

    /**
     * Allocate a new condition variable.
     *
     * @param	conditionLock	the lock associated with this condition variable.
     * The current thread must hold this lock whenever it uses <tt>sleep()</tt>,
     * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
     */
    public Condition2(Lock conditionLock) {
        this.conditionLock = conditionLock;
        sleepKTheads = ThreadedKernel.scheduler.newThreadQueue(false);
    }

    /**
     * Atomically release the associated lock and go to sleep on this condition
     * variable until another thread wakes it using <tt>wake()</tt>. The current
     * thread must hold the associated lock. The thread will automatically
     * reacquire the lock before <tt>sleep()</tt> returns.
     */
    public void sleep() {
        Lib.assertTrue(conditionLock.isHeldByCurrentThread());
        Machine.interrupt().disable();
        sleepKTheads.waitForAccess(KThread.currentThread());// especificacmos que este thead esta esperando acceso
        conditionLock.release();//libero lock
        KThread.currentThread().sleep(); //mandamos a dormir
        conditionLock.acquire();
        Machine.interrupt().enable();
    }

    /**
     * Wake up at most one thread sleeping on this condition variable. The
     * current thread must hold the associated lock.
     */
    public void wake() {
        Lib.assertTrue(conditionLock.isHeldByCurrentThread());
        Machine.interrupt().disable();
        KThread tempKthead = sleepKTheads.nextThread(); // tolo el siguiente trhead de la lista
        if (tempKthead != null) {// si existe lo pongo ready
            tempKthead.ready();
        }
        Machine.interrupt().enable(); //habilito enturrucciones
    }

    /**
     * Wake up all threads sleeping on this condition variable. The current
     * thread must hold the associated lock.
     */
    public void wakeAll() {
        Lib.assertTrue(conditionLock.isHeldByCurrentThread());
        Machine.interrupt().disable();
        KThread tempKthead = sleepKTheads.nextThread(); //tomo el siguiiente thread y 
        //mientrans quende en la cola los voy poniendo reayd
        while (tempKthead != null) {
            tempKthead.ready();
            tempKthead = sleepKTheads.nextThread();
        }
        Machine.interrupt().enable();

    }
    private Lock conditionLock; //lock para las condiciones
    private ThreadQueue sleepKTheads; // cola de thread a dormir
}
