package nachos.threads;

import nachos.machine.*;

/**
 * A KThread is a thread that can be used to execute Nachos kernel code. Nachos
 * allows multiple threads to run concurrently.
 *
 * To create a new thread of execution, first declare a class that implements
 * the <tt>Runnable</tt> interface. That class then implements the <tt>run</tt>
 * method. An instance of the class can then be allocated, passed as an argument
 * when creating <tt>KThread</tt>, and forked. For example, a thread that
 * computes pi could be written as follows:
 *
 * <p><blockquote><pre>
 * class PiRun implements Runnable {
 *     public void run() {
 *         // compute pi
 *         ...
 *     }
 * }
 * </pre></blockquote>
 * <p>The following code would then create a thread and start it running:
 *
 * <p><blockquote><pre>
 * PiRun p = new PiRun();
 * new KThread(p).fork();
 * </pre></blockquote>
 */
public class KThread {

    /**
     * Get the current thread.
     *
     * @return	the current thread.
     */
    public static KThread currentThread() {
        Lib.assertTrue(currentThread != null);
        return currentThread;
    }

    /**
     * Allocate a new <tt>KThread</tt>. If this is the first <tt>KThread</tt>,
     * create an idle thread as well.
     */
    public KThread() {
        if (currentThread != null) {
            tcb = new TCB();
        } else {
            readyQueue = ThreadedKernel.scheduler.newThreadQueue(false);
            readyQueue.acquire(this);

            currentThread = this;
            tcb = TCB.currentTCB();
            name = "main";
            restoreState();

            createIdleThread();
        }
    }

    /**
     * Allocate a new KThread.
     *
     * @param	target	the object whose <tt>run</tt> method is called.
     */
    public KThread(Runnable target) {
        this();
        this.target = target;
        joinStack = ThreadedKernel.scheduler.newThreadQueue(false);
    }

    /**
     * Set the target of this thread.
     *
     * @param	target	the object whose <tt>run</tt> method is called.
     * @return	this thread.
     */
    public KThread setTarget(Runnable target) {
        Lib.assertTrue(status == statusNew);

        this.target = target;
        return this;
    }

    /**
     * Set the name of this thread. This name is used for debugging purposes
     * only.
     *
     * @param	name	the name to give to this thread.
     * @return	this thread.
     */
    public KThread setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Get the name of this thread. This name is used for debugging purposes
     * only.
     *
     * @return	the name given to this thread.
     */
    public String getName() {
        return name;
    }

    /**
     * Get the full name of this thread. This includes its name along with its
     * numerical ID. This name is used for debugging purposes only.
     *
     * @return	the full name given to this thread.
     */
    public String toString() {
        return (name + " (#" + id + ")");
    }

    /**
     * Deterministically and consistently compare this thread to another thread.
     */
    public int compareTo(Object o) {
        KThread thread = (KThread) o;

        if (id < thread.id) {
            return -1;
        } else if (id > thread.id) {
            return 1;
        } else {
            return 0;
        }
    }

    /**
     * Causes this thread to begin execution. The result is that two threads are
     * running concurrently: the current thread (which returns from the call to
     * the <tt>fork</tt> method) and the other thread (which executes its
     * target's <tt>run</tt> method).
     */
    public void fork() {
        Lib.assertTrue(status == statusNew);
        Lib.assertTrue(target != null);

        Lib.debug(dbgThread,
                "Forking thread: " + toString() + " Runnable: " + target);

        boolean intStatus = Machine.interrupt().disable();

        tcb.start(new Runnable() {
            public void run() {
                runThread();
            }
        });

        ready();

        Machine.interrupt().restore(intStatus);
    }

    private void runThread() {
        begin();
        target.run();
        finish();
    }

    private void begin() {
        Lib.debug(dbgThread, "Beginning thread: " + toString());

        Lib.assertTrue(this == currentThread);

        restoreState();

        Machine.interrupt().enable();
    }

    /**
     * Finish the current thread and schedule it to be destroyed when it is safe
     * to do so. This method is automatically called when a thread's
     * <tt>run</tt> method returns, but it may also be called directly.
     *
     * The current thread cannot be immediately destroyed because its stack and
     * other execution state are still in use. Instead, this thread will be
     * destroyed automatically by the next thread to run, when it is safe to
     * delete this thread.
     */
    public static void finish() {
        Lib.debug(dbgThread, "Finishing thread: " + currentThread.toString());

        Machine.interrupt().disable();
        KThread tempthread;
        
        Machine.autoGrader().finishingCurrentThread();
        
        while ( (tempthread = joinStack.nextThread()) != null ) {
            tempthread.ready();
            tempthread = currentThread.joinStack.nextThread();
        }

        Lib.assertTrue(toBeDestroyed == null);
        toBeDestroyed = currentThread;
        currentThread.status = statusFinished;

        sleep();
    }

    /**
     * Relinquish the CPU if any other thread is ready to run. If so, put the
     * current thread on the ready queue, so that it will eventually be
     * rescheuled.
     *
     * <p>
     * Returns immediately if no other thread is ready to run. Otherwise returns
     * when the current thread is chosen to run again by
     * <tt>readyQueue.nextThread()</tt>.
     *
     * <p>
     * Interrupts are disabled, so that the current thread can atomically add
     * itself to the ready queue and switch to the next thread. On return,
     * restores interrupts to the previous state, in case <tt>yield()</tt> was
     * called with interrupts disabled.
     */
    public static void yield() {
        Lib.debug(dbgThread, "Yielding thread: " + currentThread.toString());

        Lib.assertTrue(currentThread.status == statusRunning);

        boolean intStatus = Machine.interrupt().disable();

        currentThread.ready();

        runNextThread();

        Machine.interrupt().restore(intStatus);
    }

    /**
     * Relinquish the CPU, because the current thread has either finished or it
     * is blocked. This thread must be the current thread.
     *
     * <p>
     * If the current thread is blocked (on a synchronization primitive, i.e. a
     * <tt>Semaphore</tt>, <tt>Lock</tt>, or <tt>Condition</tt>), eventually
     * some thread will wake this thread up, putting it back on the ready queue
     * so that it can be rescheduled. Otherwise, <tt>finish()</tt> should have
     * scheduled this thread to be destroyed by the next thread to run.
     */
    public static void sleep() {
        Lib.debug(dbgThread, "Sleeping thread: " + currentThread.toString());

        Lib.assertTrue(Machine.interrupt().disabled());

        if (currentThread.status != statusFinished) {
            currentThread.status = statusBlocked;
        }

        runNextThread();
    }

    /**
     * Moves this thread to the ready state and adds this to the scheduler's
     * ready queue.
     */
    public void ready() {
        Lib.debug(dbgThread, "Ready thread: " + toString());

        Lib.assertTrue(Machine.interrupt().disabled());
        Lib.assertTrue(status != statusReady);

        status = statusReady;
        if (this != idleThread) {
            readyQueue.waitForAccess(this);
        }

        Machine.autoGrader().readyThread(this);
    }

    /**
     * Waits for this thread to finish. If this thread is already finished,
     * return immediately. This method must only be called once; the second call
     * is not guaranteed to return. This thread must not be the current thread.
     */
    public void join() {
        Lib.debug(dbgThread, "Joining to thread: " + toString());

        Lib.assertTrue(this != currentThread);
        boolean flag = Machine.interrupt().disable();
        if (this.status != statusFinished) {
            joinStack.waitForAccess(currentThread);
            currentThread.sleep();

        }
        Machine.interrupt().restore(flag);



    }

    /**
     * Create the idle thread. Whenever there are no threads ready to be run,
     * and <tt>runNextThread()</tt> is called, it will run the idle thread. The
     * idle thread must never block, and it will only be allowed to run when all
     * other threads are blocked.
     *
     * <p>
     * Note that <tt>ready()</tt> never adds the idle thread to the ready set.
     */
    private static void createIdleThread() {
        Lib.assertTrue(idleThread == null);

        idleThread = new KThread(new Runnable() {
            public void run() {
                while (true) {
                    yield();
                }
            }
        });
        idleThread.setName("idle");

        Machine.autoGrader().setIdleThread(idleThread);

        idleThread.fork();
    }

    /**
     * Determine the next thread to run, then dispatch the CPU to the thread
     * using <tt>run()</tt>.
     */
    private static void runNextThread() {
        KThread nextThread = readyQueue.nextThread();
        if (nextThread == null) {
            nextThread = idleThread;
        }

        nextThread.run();
    }

    /**
     * Dispatch the CPU to this thread. Save the state of the current thread,
     * switch to the new thread by calling <tt>TCB.contextSwitch()</tt>, and
     * load the state of the new thread. The new thread becomes the current
     * thread.
     *
     * <p>
     * If the new thread and the old thread are the same, this method must still
     * call <tt>saveState()</tt>, <tt>contextSwitch()</tt>, and
     * <tt>restoreState()</tt>.
     *
     * <p>
     * The state of the previously running thread must already have been changed
     * from running to blocked or ready (depending on whether the thread is
     * sleeping or yielding).
     *
     * @param	finishing	<tt>true</tt> if the current thread is finished, and
     * should be destroyed by the new thread.
     */
    private void run() {
        Lib.assertTrue(Machine.interrupt().disabled());

        Machine.yield();

        currentThread.saveState();

        Lib.debug(dbgThread, "Switching from: " + currentThread.toString()
                + " to: " + toString());

        currentThread = this;

        tcb.contextSwitch();

        currentThread.restoreState();
    }

    /**
     * Prepare this thread to be run. Set <tt>status</tt> to
     * <tt>statusRunning</tt> and check <tt>toBeDestroyed</tt>.
     */
    protected void restoreState() {
        Lib.debug(dbgThread, "Running thread: " + currentThread.toString());

        Lib.assertTrue(Machine.interrupt().disabled());
        Lib.assertTrue(this == currentThread);
        Lib.assertTrue(tcb == TCB.currentTCB());

        Machine.autoGrader().runningThread(this);

        status = statusRunning;

        if (toBeDestroyed != null) {
            toBeDestroyed.tcb.destroy();
            toBeDestroyed.tcb = null;
            toBeDestroyed = null;
        }
    }

    /**
     * Prepare this thread to give up the processor. Kernel threads do not need
     * to do anything here.
     */
    protected void saveState() {
        Lib.assertTrue(Machine.interrupt().disabled());
        Lib.assertTrue(this == currentThread);
    }

    //Patrocinado por
    //: Guisela Illescas) 
    private static class PingTest implements Runnable {

        PingTest(int which) {
            this.which = which;
        }

        public void run() {
            //System.out.println("Pruebas Join");
            for (int i = 1; i < 11; i++) {

                System.out.println("*** thread " + which + " looped " + i + " times, Tick:" + Machine.timer().getTime());
                if ((which == 1) && (i == 2)) {
                    ThreadedKernel.alarm.waitUntil(1000);
                }

                /*if ((which == 0) && (i == 2)) {
                 dos.join();
                 }
                 if ((which == 2) && (i == 3)) {
                 tres.join();
                 }
                 if ((which == 1) && (i == 3)) {
                 dos.join();
                 }*/
                currentThread.yield();
            }
        }
        private int which;
    }
    /**
     * Tests whether this module is working.
     */
    /*public static void selfTest() {
     Lib.debug(dbgThread, "Enter KThread.selfTest");
            

     uno = new KThread(new PingTest(1)).setName("uno");
     dos = new KThread(new PingTest(2)).setName("dos");
     tres = new KThread(new PingTest(3)).setName("tres");
            
     uno.fork();
     dos.fork();
     tres.fork();
     //new KThread(new PingTest(1)).setName("forked thread").fork();

     new PingTest(0).run();
     }
     */
    private static final char dbgCommunicator = 'c'; // Flag to enable Communicator debug output

    /**
     * Tests whether this module is working.
     */
    public static void selfTestRun(KThread t1, int t1p, KThread t2, int t2p) {

        boolean int_state;

        int_state = Machine.interrupt().disable();
        ThreadedKernel.scheduler.setPriority(t1, t1p);
        ThreadedKernel.scheduler.setPriority(t2, t2p);
        Machine.interrupt().restore(int_state);

        t1.setName("a").fork();
        t2.setName("b").fork();
        t1.join();
        t2.join();

    }

    public static void selfTestRun(KThread t1, int t1p, KThread t2, int t2p, KThread t3, int t3p) {

        boolean int_state;

        int_state = Machine.interrupt().disable();
        ThreadedKernel.scheduler.setPriority(t1, t1p);
        ThreadedKernel.scheduler.setPriority(t2, t2p);
        ThreadedKernel.scheduler.setPriority(t3, t3p);
        Machine.interrupt().restore(int_state);

        t1.setName("a").fork();
        t2.setName("b").fork();
        t3.setName("c").fork();
        t1.join();
        t2.join();
        t3.join();

    }

    static class Test1 {

        public static void run() {
            System.out.println("Testing basic scheduling:");
            KThread threads[] = new KThread[4];

            for (int i = 0; i < threads.length; i++) {
                threads[i] = new KThread(new Thread(i));
                threads[i].fork();
            }

            for (int i = 0; i < threads.length; i++) {
                threads[i].join();
            }
        }

        private static class Thread implements Runnable {

            private int num;

            public Thread(int n) {
                num = n;
            }

            public void run() {
                for (int i = 1; i < 3; i++) {
                    System.out.println("Thread: " + num + " looping");
                    KThread.yield();
                }
            }
        }
    }

    /**
     * Tests basic scheduling with priorities involved.
     */
    static class Test2 {

        public static void run() {
            PriorityScheduler sched = (PriorityScheduler) ThreadedKernel.scheduler;

            System.out.println("Testing priority scheduling:");
            KThread threads[] = new KThread[4];

            for (int i = 0; i < threads.length; i++) {
                threads[i] = new KThread(new Thread(3 - i));
                boolean intStatus = Machine.interrupt().disable();
                sched.setPriority(threads[i], 7 - i);
                Machine.interrupt().restore(intStatus);
                threads[i].fork();
            }

            for (int i = 0; i < threads.length; i++) {
                threads[i].join();
            }
        }

        private static class Thread implements Runnable {

            private int num;

            public Thread(int n) {
                num = n;
            }

            public void run() {
                for (int i = 1; i < 3; i++) {
                    System.out.println("Priority: " + num + " looping");
                    KThread.yield();
                }
            }
        }
    }

    /**
     * Tests priority donation by running 4 threads, 2 with equal priority and
     * the other with higher and lower priority. The high priority thread then
     * waits on the low priority one and we see how long it takes to get
     * scheduled.
     */
    static class Test3 {

        static boolean high_run = false;

        public static void run() {
            Lock l = new Lock();
            PriorityScheduler sched = (PriorityScheduler) ThreadedKernel.scheduler;

            System.out.println("Testing basic priority inversion:");

            KThread low = new KThread(new Low(l));
            KThread med1 = new KThread(new Med(1));
            KThread med2 = new KThread(new Med(2));
            KThread high = new KThread(new High(l));

            boolean intStatus = Machine.interrupt().disable();
            sched.setPriority(high, 4);
            sched.setPriority(med1, 3);
            sched.setPriority(med2, 3);
            sched.setPriority(low, 1);
            Machine.interrupt().restore(intStatus);

            low.fork();
            KThread.yield();
            med1.fork();
            high.fork();
            med2.fork();
            KThread.yield();

            /* Make sure its all finished before quitting */
            low.join();
            med2.join();
            med1.join();
            high.join();
        }

        private static class High implements Runnable {

            private Lock lock;

            public High(Lock l) {
                lock = l;
            }

            public void run() {
                System.out.println("High priority thread sleeping");
                lock.acquire();
                Test3.high_run = true;
                System.out.println("High priority thread woken");
                lock.release();
            }
        }

        private static class Med implements Runnable {

            int num;

            public Med(int n) {
                num = n;
            }

            public void run() {
                for (int i = 1; i < 3; i++) {
                    KThread.yield();
                }

                if (Test3.high_run) {
                    System.out.println("High thread finished before thread " + num + ".");
                } else {
                    System.out.println("Error, meduim priority thread finished"
                            + " before high priority one!");
                }
            }
        }

        private static class Low implements Runnable {

            private Lock lock;

            public Low(Lock l) {
                lock = l;
            }

            public void run() {
                System.out.println("Low priority thread running");
                lock.acquire();
                KThread.yield();
                System.out.println("Low priority thread finishing");
                lock.release();
            }
        }
    }

    /**
     * A more advanced priority inversion test.
     */
    static class Test4 {

        boolean high_run = false;

        public static void run() {
            Lock l1 = new Lock();
            Lock l2 = new Lock();
            Lock l3 = new Lock();
            PriorityScheduler sched = (PriorityScheduler) ThreadedKernel.scheduler;

            System.out.println("Testing complex priority inversion:");

            KThread t1 = new KThread(new Thread(l1, 1));
            KThread t2 = new KThread(new Thread(l2, l1, 2));
            KThread t3 = new KThread(new Thread(l3, l2, 3));
            KThread t4 = new KThread(new Thread(l3, 4));

            t1.fork();
            t2.fork();
            t3.fork();
            t4.fork();

            KThread.yield();

            boolean intStatus = Machine.interrupt().disable();
            sched.setPriority(t4, 3);
            int k = sched.getEffectivePriority(t1);
            if (k != 3) {
                System.out.println("Priority not correctly donated.");
            } else {
                System.out.println("Priority correctly donated.");
            }
            Machine.interrupt().restore(intStatus);

            KThread.yield();

            intStatus = Machine.interrupt().disable();
            k = sched.getEffectivePriority(t1);
            if (k != (int) 1) {
                System.out.println("Priority donation not revoked.");
            } else {
                System.out.println("Priority donation correctly revoked.");
            }
            Machine.interrupt().restore(intStatus);


            /* Make sure its all finished before quitting */
            t1.join();
            t2.join();
            t3.join();
            t4.join();
        }

        private static class Thread implements Runnable {

            private Lock lock;
            private Lock altLock;
            private int num;

            public Thread(Lock l, int n) {
                lock = l;
                num = n;
                altLock = null;
            }

            public Thread(Lock l, Lock a, int n) {
                lock = l;
                num = n;
                altLock = a;
            }

            public void run() {
                System.out.println("Thread: " + num + " sleeping");
                lock.acquire();
                if (altLock != null) {
                    altLock.acquire();
                }

                KThread.yield();

                System.out.println("Thread: " + num + " woken");
                if (altLock != null) {
                    altLock.release();
                }
                lock.release();
            }
        }
    }

    public static void selfTest2() {

        Lib.debug(dbgThread, "Enter KThread.selfTest");

        cero = new KThread(new PingTest(0)).setName("forked thread0");
        cero.fork();
        uno = new KThread(new PingTest(1)).setName("forked thread1");
        uno.fork();
        dos = new KThread(new PingTest(2)).setName("forked thread2");
        dos.fork();
        tres = new KThread(new PingTest(3)).setName("forked thread3");
        tres.fork();

    }

    public static void selfTest() {
        KThread t1, t2, t3;
        final Lock lock;
        final Condition2 condition;

        /* 
         * Case 1: Tests priority scheduler without donation 
         * 
         * This runs t1 with priority 7, and t2 with priority 4. 
         * 
         */

        System.out.println("Case 1:");

        t1 = new KThread(new Runnable() {
            public void run() {
                System.out.println(KThread.currentThread().getName() + " started working");
                for (int i = 0; i < 10; ++i) {
                    System.out.println(KThread.currentThread().getName() + " working " + i);
                    KThread.yield();
                }
                System.out.println(KThread.currentThread().getName() + " finished working");
            }
        });

        t2 = new KThread(new Runnable() {
            public void run() {
                System.out.println(KThread.currentThread().getName() + " started working");
                for (int i = 0; i < 11; ++i) {
                    System.out.println(KThread.currentThread().getName() + " working " + i);
                    KThread.yield();
                }
                System.out.println(KThread.currentThread().getName() + " finished working");
            }
        });

        selfTestRun(t1, 7, t2, 4);

        /* 
         * Case 2: Tests priority scheduler without donation, altering 
         * priorities of threads after they've started running 
         * 
         * This runs t1 with priority 7, and t2 with priority 4, but 
         * half-way through t1's process its priority is lowered to 2. 
         * 
         */

        System.out.println("Case 2:");

        t1 = new KThread(new Runnable() {
            public void run() {
                System.out.println(KThread.currentThread().getName() + " started working");
                for (int i = 0; i < 10; ++i) {
                    System.out.println(KThread.currentThread().getName() + " working " + i);
                    KThread.yield();
                    if (i == 4) {
                        System.out.println(KThread.currentThread().getName() + " reached 1/2 way, changing priority");
                        boolean int_state = Machine.interrupt().disable();
                        ThreadedKernel.scheduler.setPriority(2);
                        Machine.interrupt().restore(int_state);
                    }
                }
                System.out.println(KThread.currentThread().getName() + " finished working");
            }
        });

        t2 = new KThread(new Runnable() {
            public void run() {
                System.out.println(KThread.currentThread().getName() + " started working");
                for (int i = 0; i < 10; ++i) {
                    System.out.println(KThread.currentThread().getName() + " working " + i);
                    KThread.yield();
                }
                System.out.println(KThread.currentThread().getName() + " finished working");
            }
        });

        selfTestRun(t1, 7, t2, 4);

        /* 
         * Case 3: Tests priority donation 
         * 
         * This runs t1 with priority 7, t2 with priority 6 and t3 with 
         * priority 4. t1 will wait on a lock, and while t2 would normally 
         * then steal all available CPU, priority donation will ensure that 
         * t3 is given control in order to help unlock t1. 
         * 
         */

        System.out.println("Case 3:");

        lock = new Lock();
        condition = new Condition2(lock);

        t1 = new KThread(new Runnable() {
            public void run() {
                lock.acquire();
                System.out.println(KThread.currentThread().getName() + " active");
                lock.release();
            }
        });

        t2 = new KThread(new Runnable() {
            public void run() {
                System.out.println(KThread.currentThread().getName() + " started working");
                for (int i = 0; i < 3; ++i) {
                    System.out.println(KThread.currentThread().getName() + " working " + i);
                    KThread.yield();
                }
                System.out.println(KThread.currentThread().getName() + " finished working");
            }
        });

        t3 = new KThread(new Runnable() {
            public void run() {
                lock.acquire();

                boolean int_state = Machine.interrupt().disable();
                ThreadedKernel.scheduler.setPriority(2);
                Machine.interrupt().restore(int_state);

                KThread.yield();

// t1.acquire() will now have to realise that t3 owns the lock it wants to obtain 
// so program execution will continue here. 

                System.out.println(KThread.currentThread().getName() + " active ('a' wants its lock back so we are here)");
                lock.release();
                KThread.yield();
                lock.acquire();
                System.out.println(KThread.currentThread().getName() + " active-again (should be after 'a' and 'b' done)");
                lock.release();

            }
        });

        selfTestRun(t1, 6, t2, 4, t3, 7);




    }

    /**
     * General Communicator test class Allows subclasses to set the current
     * state of a test and to update a running tally of all received words,
     * which are used by selfTest to verify that communication occurred
     * properly.
     */
    private abstract static class TestChattyThread implements Runnable {

        static Communicator comm = new Communicator();
        static Lock lock = new Lock();

        static enum TESTSTATE {

            NONE, SPEAKING, LISTENING
        };
        static TESTSTATE state = TESTSTATE.NONE;
        static int received = 0;

        public static void reset() {
            lock.acquire();
            state = TESTSTATE.NONE;
            received = 0;
            lock.release();
        }

        static void setState(TESTSTATE s) {
            lock.acquire();
            state = s;
            lock.release();
        }

        public static void checkState(TESTSTATE s, String msg) {
            lock.acquire();
            //Lib.debug(dbgCommunicator, (state == s ? "[PASS]: " : "[FAIL]: ") + msg);
            lock.release();
        }

        static void updateReceived(int word) {
            received += word;
        }

        public static int getReceived() {
            return received;
        }
    }

    private static class TestSpeakerThread extends TestChattyThread {

        int word;

        TestSpeakerThread(int word) {
            this.word = word;
        }

        public void run() {
            setState(TESTSTATE.SPEAKING);
            comm.speak(word);
        }
    }

    private static class TestListenerThread extends TestChattyThread {

        public void run() {
            setState(TESTSTATE.LISTENING);
            updateReceived(comm.listen());
        }
    }

    /**
     * General Communicator test class Allows subclasses to set the current
     * state of a test and to update a running tally of all received words,
     * which are used by selfTest to verify that communication occurred
     * properly.
     */
    /**
     * For testing: Thread which immediately sleeps and keeps a static record of
     * the order in which it and its siblings wake up
     */
    private static class TestSeqThread implements Runnable {

        char myName;
        long mySleepTicks;
        static String wakeSequence = "";
        static Lock lock = new Lock();

        public TestSeqThread(char name, long sleepTicks) {
            myName = name;
            mySleepTicks = sleepTicks;
        }

        public void run() {
            ThreadedKernel.alarm.waitUntil(mySleepTicks);
            lock.acquire();
            wakeSequence = wakeSequence + myName;
            lock.release();
        }
    }
    private static final char dbgThread = 't';
    /**
     * Additional state used by schedulers.
     *
     * @see	nachos.threads.PriorityScheduler.ThreadState
     */
    public Object schedulingState = null;
    private static final int statusNew = 0;
    private static final int statusReady = 1;
    private static final int statusRunning = 2;
    private static final int statusBlocked = 3;
    private static final int statusFinished = 4;
    /**
     * The status of this thread. A thread can either be new (not yet forked),
     * ready (on the ready queue but not running), running, or blocked (not on
     * the ready queue and not running).
     */
    private int status = statusNew;
    private String name = "(unnamed thread)";
    private Runnable target;
    private TCB tcb;
    /**
     * Unique identifer for this thread. Used to deterministically compare
     * threads.
     */
    private int id = numCreated++;
    /**
     * Number of times the KThread constructor was called.
     */
    private static int numCreated = 0;
    private static ThreadQueue readyQueue = null;
    private static KThread currentThread = null;
    private static KThread toBeDestroyed = null;
    private static KThread idleThread = null;
    private static ThreadQueue joinStack = null;
    private static KThread uno;
    private static KThread dos;
    private static KThread tres;
    public static KThread cero = null;
}