package nachos.threads;

import java.util.LinkedList;
import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {

    private LinkedList<KThread> lkKthreads;
    private LinkedList<Long> lTimeTicks;
    private static int contador = 0;

    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
        lkKthreads = new LinkedList<KThread>();
        lTimeTicks = new LinkedList<Long>();

        System.out.println("*** alamr " + Machine.timer().getTime());
        Machine.timer().setInterruptHandler(new Runnable() {
            public void run() {
                timerInterrupt();
            }
        });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread that
     * should be run.
     */
    public void timerInterrupt() {
        //System.out.println("Contador " + contador++);

        boolean intStatus = Machine.interrupt().disable();

        int i = lTimeTicks.size() - 1;

        while (i >= 0) {
            //System.out.println("  val  " + i);
            KThread tempThread = lkKthreads.get(i);
            Long tempTime = lTimeTicks.get(i);

            if (tempTime.longValue() <= Machine.timer().getTime()) {
                // En caso el timer del thread , ya termino. 

                //System.out.println("AQUI CUMPLIO " + tempTime.longValue() + " - " + Machine.timer().getTime());
                tempThread.ready();
                lkKthreads.remove(i);
                lTimeTicks.remove(i);

            }
            i--;
        }

        Machine.interrupt().restore(intStatus);
        KThread.yield();

    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
     * in the timer interrupt handler. The thread must be woken up (placed in
     * the scheduler ready set) during the first timer interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param x the minimum number of clock ticks to wait.
     *
     * @see nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
        // for now, cheat just to get something working (busy waiting is bad)
        boolean inicialSt = Machine.interrupt().disable();
        long wakeTime = Machine.timer().getTime() + x;
        if (KThread.currentThread() != null)
        {
        lkKthreads.addFirst(KThread.currentThread());
        lTimeTicks.addFirst(wakeTime);
        KThread.currentThread().sleep();
        Machine.interrupt().restore(inicialSt);
        }

    }
}