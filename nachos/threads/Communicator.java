package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    
    private Lock lock;
    private Condition speaker;
    private Condition listener;

    private int return_word = 0;
    private boolean mensaje_transmitido = false;
    private int cant_listeners = 0;
    
    
    public Communicator() {
        lock = new Lock();
        listener = new Condition(lock);
        speaker  = new Condition(lock);
        
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {
        
        lock.acquire();
		// Si no hay listeners, 
		while (cant_listeners == 0 || mensaje_transmitido) 
			speaker.sleep();

		// Para tener multiples datos guardados
		mensaje_transmitido = true;
		return_word = word;

		// El listener ya puede recibir el mensaje
		listener.wake();
	lock.release();
        
        
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
	lock.acquire();
		// Indica al speaker que hay listeners. 
		cant_listeners++;
		speaker.wake();
		listener.sleep();

		// Vars para que se cumpla la condicion del while
		
		mensaje_transmitido = false;
		cant_listeners--;

		// Despierta nuevamente, ya que la iteracion lo mando a sleep nuevamente.  
		speaker.wake();
	lock.release();

	return return_word;
    }
    private static class Speaker implements Runnable {
    String m_nombre;
    Communicator m_c;

    public Speaker (String m_nombre, Communicator m_c) {
        this.m_c = m_c;
        this.m_nombre = m_nombre;
    }

    public void run () {
        // Se llama 5 veces

        for (int i=1; i<=3; i++) {
          m_c.speak(i);
        }
    }
    }

    private static class Listener implements Runnable {
    String m_nombre;
    Communicator m_c;

    public Listener (String m_nombre, Communicator m_c) {
        this.m_c = m_c;
        this.m_nombre = m_nombre;
    }

    public void run () {
        // Se llama 5 veces

        for (int i=1; i<=3; i++) {
            m_c.listen();
         }
    }
    }

public static void selfTest() {
        Communicator m_c = new Communicator();
        

        KThread m_l1 = new KThread(new Listener("Maria",m_c)).setName("Maria");
//        ThreadState TS_m11= new ThreadState(m_11);
       //  TS_m11.setPriority(7);
        m_l1.fork();
        KThread m_l2 = new KThread(new Listener("Carla",m_c)).setName("Carla");
       //  ThreadState TS_m12= new ThreadState(m_12);
       //  TS_m12.setPriority(6);
        m_l2.fork();
        KThread m_l3 = new KThread(new Listener("Juana",m_c)).setName("Juana");
        // ThreadState TS_m13= new ThreadState(m_13);
        // TS_m13.setPriority(5);
        m_l3.fork();
        KThread m_l4 = new KThread(new Listener("Julia",m_c)).setName("Julia");
        // ThreadState TS_m14= new ThreadState(m_14);
        // TS_m14.setPriority(4);
        m_l4.fork();
        KThread m_s1 = new KThread(new Speaker("Mario ",m_c)).setName("Mario ");
        // ThreadState TS_ms1= new ThreadState(m_s1);
        // TS_ms1.setPriority(3);
        m_s1.fork();
        KThread m_s2 = new KThread(new Speaker("Carlos",m_c)).setName("Carlos");
        // ThreadState TS_ms2= new ThreadState(m_s2);
        // TS_ms2.setPriority(2);
        m_s2.fork();
        KThread m_s3 = new KThread(new Speaker("Juan ",m_c)).setName("Juan  ");
        // ThreadState TS_ms3= new ThreadState(m_s3);
        // TS_ms3.setPriority(2);
        m_s3.fork();
        KThread m_s4 = new KThread(new Speaker("Julio ",m_c)).setName("Julio ");
        // ThreadState TS_ms4= new ThreadState(m_s4);
        // TS_ms4.setPriority(1);
        m_s4.fork();
    }

}
