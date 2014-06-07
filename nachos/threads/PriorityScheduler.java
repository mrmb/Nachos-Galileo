package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the thread
 * that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has the
 * potential to starve a thread if there's always a thread waiting with higher
 * priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {

    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
    }

    /**
     * Allocate a new priority thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should transfer
     * priority from waiting threads to the owning thread.
     * @return	a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
        return new PriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());

        return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());
        int j =getThreadState(thread).getEffectivePriority();
        return j;
    }

    public void setPriority(KThread thread, int priority) {
        Lib.assertTrue(Machine.interrupt().disabled());

        Lib.assertTrue(priority >= priorityMinimum
                && priority <= priorityMaximum);

        getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
        boolean intStatus = Machine.interrupt().disable();

        KThread thread = KThread.currentThread();

        int priority = getPriority(thread);
        if (priority == priorityMaximum) {
            return false;
        }

        setPriority(thread, priority + 1);

        Machine.interrupt().restore(intStatus);
        return true;
    }

    public boolean decreasePriority() {
        boolean intStatus = Machine.interrupt().disable();

        KThread thread = KThread.currentThread();

        int priority = getPriority(thread);
        if (priority == priorityMinimum) {
            return false;
        }

        setPriority(thread, priority - 1);

        Machine.interrupt().restore(intStatus);
        return true;
    }
    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param	thread	the thread whose scheduling state to return.
     * @return	the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
        if (thread.schedulingState == null) {
            thread.schedulingState = new ThreadState(thread);
        }

        return (ThreadState) thread.schedulingState;
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue {

        PriorityQueue(boolean transferPriority) {
            this.transferPriority = transferPriority;
            //Inicializamos Variables
            //this.transferPriority = true;
            PriorityKTheads = new LinkedList<KThread>();
        }

        public void waitForAccess(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());
            getThreadState(thread).waitForAccess(this);
        }

        public void acquire(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());
            getThreadState(thread).acquire(this);
        }

        public KThread nextThread() {
            Lib.assertTrue(Machine.interrupt().disabled());
            ThreadState ktemp = pickNextThread();
            if (ktemp == null) {
                return null;
            }
            return ktemp.thread;
        }

        /**
         * Return the next thread that <tt>nextThread()</tt> would return,
         * without modifying the state of this queue.
         *
         * @return	the next thread that <tt>nextThread()</tt> would return.
         */
        protected ThreadState pickNextThread() {
            //Variable de control de Scheduler del Thread
            ThreadState ktemp = null;
            // si la lista no esta vacia tomo el primero
            if (PriorityKTheads != null) {
                ktemp = (PriorityScheduler.ThreadState) PriorityKTheads.pollFirst();
            }
            //si el kthead no tiene lock 
            //mientras el tamaño de la estructura de prestamos
            //si no es cierto entonces le quito prioridad
            if (lockHolder != null) {
                while (lockHolder.prestamos.size() != 0) {
                    lockHolder.effectivePriority -= (Integer) lockHolder.prestamos.pollFirst();
                }
            }
            //el lockhoder es ahora el ktemp
            lockHolder = ktemp;
            //se retorna ktemp;
            return ktemp;
        }

        public void print() {
            Lib.assertTrue(Machine.interrupt().disabled());
            // implement me (if you want)
        }
        /**
         * <tt>true</tt> if this queue should transfer priority from waiting
         * threads to the owning thread.
         */
        //
        private LinkedList PriorityKTheads;
        private boolean trasnferirPrioridad;
        public boolean transferPriority;
        //KThread que tiene el lock
        protected ThreadState lockHolder;
    }

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue it's
     * waiting for, if any.
     *
     * @see	nachos.threads.KThread#schedulingState
     */
    protected class ThreadState {

        /**
         * Allocate a new <tt>ThreadState</tt> object and associate it with the
         * specified thread.
         *
         * @param	thread	the thread this state belongs to.
         */
        public ThreadState(KThread thread) {
            this.thread = thread;
            prestamos = new LinkedList();
            setPriority(priorityDefault);
        }

        /**
         * Return the priority of the associated thread.
         *
         * @return	the priority of the associated thread.
         */
        public int getPriority() {
            return priority;
        }

        /**
         * Return the effective priority of the associated thread.
         *
         * @return	the effective priority of the associated thread.
         */
        public int getEffectivePriority() {
            // implement me
            //al prioridad efectiva sera la prioridad mas la suma de las prioridades prestadas para el thread
            effectivePriority = priority;
            for (int i = 0; i < prestamos.size(); i++) {
                effectivePriority += (Integer) prestamos.get(i);
            }
            return effectivePriority;
        }

        /**
         * Set the priority of the associated thread to the specified value.
         *
         * @param	priority	the new priority.
         */
        public void setPriority(int priority) {
            if (this.priority == priority) {
                return;
            }

            this.priority = priority;

            // implement me
        }

        /**
         * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
         * the associated thread) is invoked on the specified priority queue.
         * The associated thread is therefore waiting for access to the resource
         * guarded by <tt>waitQueue</tt>. This method is only called if the
         * associated thread cannot immediately obtain access.
         *
         * @param	waitQueue	the queue that the associated thread is now waiting
         * on.
         *
         * @see	nachos.threads.ThreadQueue#waitForAccess
         */
        public void waitForAccess(PriorityQueue waitQueue) {
            if (waitQueue.lockHolder != null) {
                if (waitQueue.transferPriority) {
                    if (waitQueue.lockHolder.getPriority() < this.priority) {
                        waitQueue.lockHolder.prestamos.add(this.priority);
                    }
                }
            }
            waitQueue.PriorityKTheads.add(this);
            bubbleSort(waitQueue);
        }

        public void bubbleSort(PriorityQueue waitQueue) {

            
            ThreadState kaux;
            for (int i = 0; i < waitQueue.PriorityKTheads.size() - 1; i++) {
                for (int j = i + 1; j < waitQueue.PriorityKTheads.size(); j++) {
                    if (((ThreadState) waitQueue.PriorityKTheads.get(i)).getEffectivePriority()
                            <= ((ThreadState) waitQueue.PriorityKTheads.get(j)).getEffectivePriority()) {
                        kaux = (ThreadState) waitQueue.PriorityKTheads.get(j);
                        waitQueue.PriorityKTheads.remove(j);
                        waitQueue.PriorityKTheads.add(j, waitQueue.PriorityKTheads.get(i));
                        waitQueue.PriorityKTheads.remove(i);
                        waitQueue.PriorityKTheads.add(i, kaux);
                    }
                }
            }
        }

        /**
         * Called when the associated thread has acquired access to whatever is
         * guarded by <tt>waitQueue</tt>. This can occur either as a result of
         * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
         * <tt>thread</tt> is the associated thread), or as a result of
         * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
         *
         * @see	nachos.threads.ThreadQueue#acquire
         * @see	nachos.threads.ThreadQueue#nextThread
         */
        public void acquire(PriorityQueue waitQueue) {
            // implement me
            waitQueue.lockHolder = this;
        }
        /**
         * The thread with which this object is associated.
         */
        protected KThread thread;
        /**
         * The priority of the associated thread.
         */
        protected int priority;
        /**
         * guarda el valor de la prioridad efectiva, con suma de donaciones
         */
        public int effectivePriority;
        /**
         * se guarda el timer cuando entra a cola
         */
        protected long tiempo;
        /**
         * se guardan los prestamos hechos
         */
        public LinkedList prestamos;
    }
}
