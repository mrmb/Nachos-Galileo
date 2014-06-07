package nachos.userprog;

import java.util.LinkedList;
import nachos.machine.*;
import nachos.threads.*;


/*
 * Campos Utilizados 
 *      [ LinkedList<TranslationEntry> , freePages ] Tener un lugar donde guardar las paginas disponibles para el OS 
 * 
 * Observaciones
 *      Utilizaremos Locks cuando queramos acceder a la linkedlist. [synchronization]
 * 
 *      1.  Tener una linked list que contenga todas las paginas disponibles [ Machine.processor().getNumPhysPages() ]
 *      2.  Poder dar paginas que todavia no esten en uso a los procesos
 *      3.  Alguna forma de liberar las mismas paginas que han sido asignadas. 
 *      
 */



public class UserKernel extends ThreadedKernel {
    
    private static LinkedList<TranslationEntry> freePages = new LinkedList<TranslationEntry>();
    private static Lock freePagesLock ;
    
    
    /**
     * Allocate a new user kernel.
     */
    public UserKernel() {
	super();
    }

    /**
     * Initialize this kernel. Creates a synchronized console and sets the
     * processor's exception handler.
     */
    public void initialize(String[] args) {
	super.initialize(args);

	console = new SynchConsole(Machine.console());
	
	Machine.processor().setExceptionHandler(new Runnable() {
		public void run() { exceptionHandler(); }
	    });
        
        
        // Instancias y cant de pages disponibles
        
        freePagesLock = new Lock();
        for(int i = 0 ; i < Machine.processor().getNumPhysPages() ; i++)
            freePages.add( new TranslationEntry(0, i, false, false, false, false));
        
    }

    /**
     * Test the console device.
     */	
    public void selfTest() {
	//super.selfTest();

	/*System.out.println("Testing the console device. Typed characters");
	System.out.println("will be echoed until q is typed.");

	char c;

	do {
	    c = (char) console.readByte(true);
	    console.writeByte(c);
	}
	while (c != 'q');

	System.out.println("");*/
    }

    
    public static UserProcess currentProcess() {
	if (!(KThread.currentThread() instanceof UThread))
	    return null;
	
	return ((UThread) KThread.currentThread()).process;
    }

    /**
     * The exception handler. This handler is called by the processor whenever
     * a user instruction causes a processor exception.
     *
     * <p>
     * When the exception handler is invoked, interrupts are enabled, and the
     * processor's cause register contains an integer identifying the cause of
     * the exception (see the <tt>exceptionZZZ</tt> constants in the
     * <tt>Processor</tt> class). If the exception involves a bad virtual
     * address (e.g. page fault, TLB miss, read-only, bus error, or address
     * error), the processor's BadVAddr register identifies the virtual address
     * that caused the exception.
     */
    public void exceptionHandler() {
	Lib.assertTrue(KThread.currentThread() instanceof UThread);

	UserProcess process = ((UThread) KThread.currentThread()).process;
	int cause = Machine.processor().readRegister(Processor.regCause);
	process.handleException(cause);
    }

    /**
     * Start running user programs, by creating a process and running a shell
     * program in it. The name of the shell program it must run is returned by
     * <tt>Machine.getShellProgramName()</tt>.
     *
     * @see	nachos.machine.Machine#getShellProgramName
     */
    public void run() {
	super.run();

	UserProcess process = UserProcess.newUserProcess();
	
	String shellProgram = Machine.getShellProgramName();	
	Lib.assertTrue(process.execute(shellProgram, new String[] { }));

	KThread.currentThread().finish();
    }
    
    /**
     * Acquire the requested number of pages from the free pages in
     * <tt>this</tt>.
     *
     * @param numPages
     * @return
     * @throws InadequatePagesException
     */
    
    
    public static TranslationEntry[] getPhysicalPages(int numPages)  {
        
        /*
         * TranslationEntry
         * Object:
         *  Se encarga de verificar si hay espacio disponible para poder dar paginas, y si hay espacio
         *  este lo elimina de la estructura de datos utilizada para las paginas y lo setea como una 
         *  pagina valida. 
         * 
         * Return 
         *  Si hay paginas 
         *      Regresa un arreglo con las paginas que se dieron a ese proceso
         *  Si no hay paginas disponibles
         *      Regresa un null
         * 
         */
            
        TranslationEntry[] returnPages = null;

        freePagesLock.acquire();

            if (!freePages.isEmpty() && freePages.size() >= numPages) {
                returnPages = new TranslationEntry[numPages];
                for (int i = 0; i < numPages; ++i) {
                    returnPages[i] = freePages.remove();
                    returnPages[i].valid = true;
                }
            }

        freePagesLock.release();
        return returnPages;
	
    }

	
    public static void setFreePhysicalPages(TranslationEntry[] pageTable) {
        
        /*
         *  Metodo encargado de lo contrario al anterior. 
         *  Ya que un proceso ya no desea utilizar las paginas que se les fueron asignadas, se procede a 
         *  agregarlas nuevamente a la lista que tiene la cantidad de paginas disponibles y se le asigna como
         *  una pagina invalida, y asi lo pueda utilizar algun otro proceso. 
         */
        
        freePagesLock.acquire();

        for (TranslationEntry te : pageTable) {
                freePages.add(te);
                te.valid = false;
        }

        freePagesLock.release();
    } 
    
    
    
    

    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
	super.terminate();
    }
    
    
    public static UserKernel getKernel() {
        if(kernel instanceof UserKernel) return (UserKernel)kernel;
        return null;
    }
    
    

    /** Globally accessible reference to the synchronized console. */
    public static SynchConsole console;

    // dummy variables to make javac smarter
    private static Coff dummy1 = null;
    
    
    protected static Lock lock = null;
    // dummy variables to make javac smarter
    
    private static LinkedList<Integer> freePhysicalPages = null;

}
