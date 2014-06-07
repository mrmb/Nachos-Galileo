package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {

    /**
     * The program being run by this process.
     */
    protected Coff coff;
    /**
     * This process's page table.
     */
    TranslationEntry[] pageTable;
    /**
     * The number of contiguous pages occupied by the program.
     */
    protected int numPages;
    protected final int stackPages = 8;
    private int initialPC, initialSP;
    private int argc, argv;
    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
    protected OpenFile[] fileTable = new OpenFile[16];
    private static final int maxSyscallArgLength = 256;
    protected LinkedList<String> openFilesList = new LinkedList<String>();
    
    private boolean exited = false;
    private Lock joinLock = new Lock();
    private Condition waitingToJoin = new Condition(joinLock);
    
    private static Lock sharedStateLock = new Lock();
 
    private static int runningProcesses = 0;
 
    private static int nextPID = 0;
    protected int PID;
    protected UserProcess parent;
    private HashMap<Integer, ChildProcess> children = new HashMap<Integer, ChildProcess>();
    
    
    /**
     * Allocate a new process.
     */
    public UserProcess() {
        
        
        int numPhysPages = Machine.processor().getNumPhysPages();
        pageTable = new TranslationEntry[numPhysPages];
        for (int i = 0; i < numPhysPages; i++) {
            pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
        }
        
        
        // stdin/stdout
        fileTable[0] = UserKernel.console.openForReading();
        FileRef.referenceFile(fileTable[0].getName());
        fileTable[1] = UserKernel.console.openForWriting();
        FileRef.referenceFile(fileTable[1].getName());
    }

    /**
     * Allocate and return a new process of the correct class. The class name is
     * specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return	a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
        return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
        if (!load(name, args)) {
            return false;
        }

        new UThread(this).setName(name).fork();

        return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
        Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read at
     * most <tt>maxLength + 1</tt> bytes from the specified address, search for
     * the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param	vaddr	the starting virtual address of the null-terminated string.
     * @param	maxLength	the maximum number of characters in the string, not
     * including the null terminator.
     * @return	the string read, or <tt>null</tt> if no null terminator was
     * found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
        Lib.assertTrue(maxLength >= 0);

        byte[] bytes = new byte[maxLength + 1];

        int bytesRead = readVirtualMemory(vaddr, bytes);

        for (int length = 0; length < bytesRead; length++) {
            if (bytes[length] == 0) {
                return new String(bytes, 0, length);
            }
        }

        return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
        return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no data
     * could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @param	offset	the first byte to write in the array.
     * @param	length	the number of bytes to transfer from virtual memory to the
     * array.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int svaddr, byte[] data, int offset, int length) {
        
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

        int vaddr = svaddr;
        byte[] memory = Machine.processor().getMemory() ;   // pageSize * numPages

        while (length != 0) {
            int paddr = translateVirtualAddress(vaddr);
            if (paddr < 0 || paddr >= memory.length) {
                handleException(Processor.exceptionAddressError);
            }

            
            int amount = Math.min(length, Processor.makeAddress( Processor.pageFromAddress(paddr) + 1 , 0) - paddr);
            System.arraycopy(memory, paddr, data, offset, amount);

            vaddr += amount;
            offset += amount;
            length -= amount;
        }

        return vaddr - svaddr;
    }
    
    
    
    public int readVirtualMemory2(int svaddr, byte[] data, int offset, int length) {
    Lib.assertTrue(offset >= 0 && length >= 0&& offset + length <= data.length);

    byte[] memory = Machine.processor().getMemory();

    int vpn = Processor.pageFromAddress(svaddr);
       int voffset = Processor.offsetFromAddress(svaddr);
        int amount = 0;

        while (length > 0)
        {
            int curlength = Math.min(length, Processor.pageSize - voffset);
            if (pageTable[vpn].valid == false){
            }
            int paddr = Processor.makeAddress(pageTable[vpn].ppn, voffset);
            pageTable[vpn].used = true; //頁面存取後 Used Bit 設定成True
            System.arraycopy(memory, paddr, data, offset + amount, curlength);

            amount += curlength;
            length -= curlength;
            voffset = 0;
            vpn++;
        }
        return amount;
    }
    
    

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
        return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no data
     * could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @param	offset	the first byte to transfer from the array.
     * @param	length	the number of bytes to transfer from the array to virtual
     * memory.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int svaddr, byte[] data, int offset, int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
        int vaddr = svaddr;
        byte[] memory = Machine.processor().getMemory();

        while (length != 0) {
            int paddr = translateVirtualAddress(vaddr);
            if (paddr < 0 || paddr >= memory.length) {
                handleException(Processor.exceptionAddressError);
            }

            int amount = Math.min(length, Processor.makeAddress(Processor.pageFromAddress(paddr) + 1, 0) - paddr);
            System.arraycopy(data, offset, memory, paddr, amount);

            vaddr += amount;
            offset += amount;
            length -= amount;
        }

        return vaddr - svaddr;
    }

    
    
    
    
    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
        Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

        OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
        if (executable == null) {
            Lib.debug(dbgProcess, "\topen failed");
            return false;
        }

        try {
            coff = new Coff(executable);
        } catch (EOFException e) {
            executable.close();
            Lib.debug(dbgProcess, "\tcoff load failed");
            return false;
        }

        // make sure the sections are contiguous and start at page 0
        numPages = 0;
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            if (section.getFirstVPN() != numPages) {
                coff.close();
                Lib.debug(dbgProcess, "\tfragmented executable");
                return false;
            }
            numPages += section.getLength();
        }

        // make sure the argv array will fit in one page
        byte[][] argv = new byte[args.length][];
        int argsSize = 0;
        for (int i = 0; i < args.length; i++) {
            argv[i] = args[i].getBytes();
            // 4 bytes for argv[] pointer; then string plus one for null byte
            argsSize += 4 + argv[i].length + 1;
        }
        if (argsSize > pageSize) {
            coff.close();
            Lib.debug(dbgProcess, "\targuments too long");
            return false;
        }

        // program counter initially points at the program entry point
        initialPC = coff.getEntryPoint();

        // next comes the stack; stack pointer initially points to top of it
        numPages += stackPages;
        initialSP = numPages * pageSize;

        // and finally reserve 1 page for arguments
        numPages++;

        if (!loadSections()) {
            return false;
        }

        // store arguments in last page
        int entryOffset = (numPages - 1) * pageSize;
        int stringOffset = entryOffset + args.length * 4;

        this.argc = args.length;
        this.argv = entryOffset;

        for (int i = 0; i < argv.length; i++) {
            byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
            Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
            entryOffset += 4;
            Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i])
                    == argv[i].length);
            stringOffset += argv[i].length;
            Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[]{0}) == 1);
            stringOffset += 1;
        }

        return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be run
     * (this is the last step in process initialization that can fail).
     *
     * @return	<tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
        
        /*
         *  Solo se agregaron unas validaciones 
         *  Si el pageTable que retorna es null [ No hay espacio para dar paginas requeridas]
         *      return false 
         *  Dar un number al vpn ya que no fue realizado en el UserKernel
         */
        
        pageTable = ((UserKernel)(Kernel.kernel)).getPhysicalPages(numPages);
        
        if( pageTable == null ) {
            coff.close();
            Lib.debug(dbgProcess, "\tinsufficient physical memory");
            return false;
        }
    
        // Setear el vpn para cada page
        for( int i = 0 ; i < pageTable.length ; i++ ) pageTable[i].vpn = i ;
        
        
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);

            Lib.debug(dbgProcess, "\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");

            for (int i = 0; i < section.getLength(); i++) {
                int vpn = section.getFirstVPN() + i;
                // for now, just assume virtual addresses=physical addresses
                section.loadPage(i, pageTable[vpn].ppn);
            }
        }
        
        return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
        
        if(pageTable!=null){
            ((UserKernel)(UserKernel.kernel)).setFreePhysicalPages(pageTable);
        }
    }

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of the
     * stack, set the A0 and A1 registers to argc and argv, respectively, and
     * initialize all other registers to 0.
     */
    public void initRegisters() {
        Processor processor = Machine.processor();

        // by default, everything's 0
        for (int i = 0; i < processor.numUserRegisters; i++) {
            processor.writeRegister(i, 0);
        }

        // initialize PC and SP according
        processor.writeRegister(Processor.regPC, initialPC);
        processor.writeRegister(Processor.regSP, initialSP);

        // initialize the first two argument registers to argc and argv
        processor.writeRegister(Processor.regA0, argc);
        processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call.
     */
    private int handleHalt() {
        
        /*
         * Algoritmo Implementado
         *  Input
         *      
         *  Output
         *      Retorna 0 si todo esta bien, o -1 si encuentra un error. 
         *  
         *  Algoritmo implementado
         *  1. Verificar si es llamado por el root proces
         *  2. Hacer el machine halt
         *  3. Si todo esta bien, retorna 0
         * 
         */
        
        
        // Just add if PID != 0 , ya que no es el root process. 
        

        if( PID != 0 ) return -1 ; 
        Machine.halt();

        Lib.assertNotReached("Machine.halt() did not halt machine!");
        return 0;
    }
    private static final int syscallHalt = 0,
            syscallExit = 1,
            syscallExec = 2,
            syscallJoin = 3,
            syscallCreate = 4,
            syscallOpen = 5,
            syscallRead = 6,
            syscallWrite = 7,
            syscallClose = 8,
            syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int exec(char *name, int argc, char **argv);
     * </tt></td></tr>
     * <tr><td>3</td><td><tt>int join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int read(int fd, char *buffer, int size);
     * </tt></td></tr>
     * <tr><td>7</td><td><tt>int write(int fd, char *buffer, int size);
     * </tt></td></tr>
     * <tr><td>8</td><td><tt>int close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int unlink(char *name);</tt></td></tr>
     * </table>
     *
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
        switch (syscall) {
            case syscallHalt:
                return handleHalt();

            case syscallExit:
                return handleExit(a0);
            case syscallExec:
                return handleExec(a0, a1, a2);
            case syscallJoin:
                return handleJoin(a0, a1);

            case syscallCreate:
                return handleCreate(a0);
            case syscallOpen:
                return handleOpen(a0);
            case syscallRead:
                return handleRead(a0, a1, a2);
            case syscallWrite:
                return handleWrite(a0, a1, a2);
            case syscallClose:
                return handleClose(a0);
            case syscallUnlink:
                return handleUnlink(a0);
            default:
                handleExit(1);
                //Lib.debug(dbgProcess, "Unknown syscall " + syscall);
                //Lib.assertNotReached("Unknown system call!");
        }
        return 0;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    public void handleException(int cause) {
        Processor processor = Machine.processor();

        switch (cause) {
            case Processor.exceptionSyscall:
                int result = handleSyscall(processor.readRegister(Processor.regV0),
                        processor.readRegister(Processor.regA0),
                        processor.readRegister(Processor.regA1),
                        processor.readRegister(Processor.regA2),
                        processor.readRegister(Processor.regA3));
                processor.writeRegister(Processor.regV0, result);
                processor.advancePC();
                break;

            default:
                Lib.debug(dbgProcess, "Unexpected exception: "
                        + Processor.exceptionNames[cause]);
                Lib.assertNotReached("Unexpected exception");
        }
    }
    

    private int handleExit(Integer status) {
        joinLock.acquire();

        if (parent != null) {
            parent.notifyChildExitStatus(PID, status);
        }

        for (ChildProcess child : children.values()) {
            if (child.process != null) {
                child.process.disown();
            }
        }
        
        children = null;

        for (int fileDesc = 0; fileDesc < fileTable.length; fileDesc++) {
            if (validFileDescriptor(fileDesc)) {
                handleClose(fileDesc);
            }
        }


        unloadSections();

        exited = true;
        waitingToJoin.wakeAll();
        joinLock.release();

        sharedStateLock.acquire();
        if (--runningProcesses == 0) {
            Kernel.kernel.terminate();
        }
        sharedStateLock.release();

        KThread.finish();

        return 0;
    }

    protected void notifyChildExitStatus(int childPID, Integer childStatus) {
        ChildProcess child = children.get(childPID);
        if (child == null) {
            return;
        }

        child.process = null;

        child.returnValue = childStatus;
    }

    protected void disown() {
        parent = null;
    }

    private int handleJoin(int pid, int statusPtr) {
        if (!validAddress(statusPtr)) {
            return terminate();
        }

        ChildProcess child = children.get(pid);

        if (child == null) {
            return -1;
        }


        if (child.process != null) {
            child.process.joinProcess();
        }

        children.remove(pid);
        
        if (child.returnValue == null) {
            return 0;
        }

        writeVirtualMemory(statusPtr, Lib.bytesFromInt(child.returnValue));

        return 1;
    }
    
    private void joinProcess() {
        joinLock.acquire();
        while (!exited) {
            waitingToJoin.sleep();
        }
        joinLock.release();
    }
    
    private int handleExec(int fileNamePtr, int argc, int argvPtr) {
        
        if (!validAddress(fileNamePtr) || !validAddress(argv)) {
            return terminate();
        }

        String fileName = readVirtualMemoryString(fileNamePtr, maxSyscallArgLength);
        if (fileName == null || !fileName.endsWith(".coff")) {
            return -1;
        }

        String arguments[] = new String[argc];

        int argvLen = argc * 4; // Number of bytes in the array
        byte argvArray[] = new byte[argvLen];
        if (argvLen != readVirtualMemory(argvPtr, argvArray)) {
            return -1;
        }


        for (int i = 0; i < argc; i++) {
            int pointer = Lib.bytesToInt(argvArray, i * 4);

            if (!validAddress(pointer)) {
                return -1;
            }

            arguments[i] = readVirtualMemoryString(pointer, maxSyscallArgLength);
        }

        UserProcess newChild = newUserProcess();
        newChild.parent = this;

        children.put(newChild.PID, new ChildProcess(newChild));

        newChild.execute(fileName, arguments);
        return newChild.PID;
    }
    
    

    private int handleCreate(int fileNamePtr) {
        return openFile(fileNamePtr, true);
    }

    private int handleOpen(int fileNamePtr) {
        return openFile(fileNamePtr, false);
    }

    private int handleRead(int fileDesc, int bufferPtr, int size) {
        
        /*
         * 
         *  Input
         *      fileDesc =  el id que ya esta en nuestra tabla
         *      bufferPtr=  El buffer de destino
         *      size     =  Cantidad de bits que estan en el fileDesc
         *  Output
         *      Retorna numeros de bits leidos
         *  
         *  Algoritmo implementado
         *  1. Validar direccion
         *  2. Validar que el indice sea el correcto
         *  3. Crear un buffer para capturar la data
         *  4. Read de todos archivos [ Ya lo permite hacer el OpenFile ]
         *  5. Verifica si lo leido se ha copiado correctamente
         *  6. Retorna numeros de bits leidos
         * 
         */
        
        if (!validAddress(bufferPtr)) {
            return terminate();
        }
        
        if (!validFileDescriptor(fileDesc)) {
            return -1;
        }

        byte buffer[] = new byte[size];
        
        int bytesRead = fileTable[fileDesc].read(buffer, 0, size);

        if (bytesRead == -1) {
            return -1;
        }        
        
        int successBytesWritten = writeVirtualMemory(bufferPtr, buffer, 0, bytesRead);
        if (successBytesWritten != bytesRead) {
            return -1;  
        }

        return bytesRead;
    }

    private int handleWrite(int fileDesc, int bufferPtr, int size) {
        
        /*
         * Algoritmo Implementado
         *  Input
         *      fileDesc =  el id que ya esta en nuestra tabla
         *      bufferPtr=  El buffer de destino
         *      size     =  Cantidad de bits que estan en el fileDesc
         *  Output
         *      Retorna numeros de bits leidos
         *  
         *  Algoritmo implementado
         *  1. New buffer
         *  2. Guardar en el buffer lo que se encuentra en la direccion
         *  3. Este se manda a cada file segun el id que ingresaron, para guardar la data en el file
         *  4. Validar que todo se ha guardado correctamente (bytes)
         *  4. Retorna el numero de bytes escritos.
         */
        
        
        if (!validAddress(bufferPtr)) {
            return terminate();
        }
        
        if (!validFileDescriptor(fileDesc)) {
            return -1;
        }
        
        byte buffer[] = new byte[size];
        int bytesRead = readVirtualMemory(bufferPtr, buffer);
        int successBytesWritten = fileTable[fileDesc].write(buffer, 0, bytesRead);

        if (successBytesWritten != bytesRead) {
            return -1;  
        }
        
        return successBytesWritten;
    }

    private int handleClose(int fileDesc) {
        
        /*
         * Algoritmo Implementado
         *  Input
         *      fileDesc =  el id que ya esta en nuestra tabla
         *  Output
         *      Retorna 0 si el archivo se ha cerrado
         *  
         *  Algoritmo Implementado
         *  1. Cerrar el archivo que se encuentra en la tabla
         *  2. Dejar el espacio valido. 
         */
        
        
        
        if (!validFileDescriptor(fileDesc)) {
            return -1;
        }
        
        // String fileName = fileTable[fileDesc].getName();

        fileTable[fileDesc].close();
        fileTable[fileDesc] = null;

        //return FileRef.unreferenceFile(fileName);
        return 0 ;
    }
    
    private int handleUnlink(int fileNamePtr) {
        
        /*
         * Algoritmo Implementado
         *  Input
         *      fileNamePtr =  FIle name to be delete
         *  Output
         *      Retorna 0 si el archivo se ha eliminado
         *  
         *  Algoritmo Implementado
         *  1. Validar que la direccion del nombre sea correcta.
         *  2. Encontrar el nombre del archivo
         *  3. Verificar que el nombre no este null 
         *  4. Remover el archivo
         *  5. Si el archivo no se elimina, retornar -1
         *  2. Retorna 0 si todo ha sido exitoso. 
         */
        
        if (!validAddress(fileNamePtr)) {
            return terminate();
        }

        String fileName = readVirtualMemoryString(fileNamePtr, maxSyscallArgLength);
        if(fileName == null) return -1 ;
        if( !UserKernel.fileSystem.remove(fileName) ) return -1;
        return 0;
        
        
        
        
    }

    private int openFile(int puntero_archivo, boolean create) {
        
        /*
         *  Args and output
         *  Input
         *      puntero_archivo = puntero del archivo 
         *      create = [true] create , [false] open
         *  Output
         *      int number con la referencia de la tabla    
         * 
         *  Algoritmo implementado 
         *  1. Verificar si la direccion es correcta
         *  2. Validamos si existe espacio entre los 16 posibles del fileTable. 
         *  3. Se manda a pedir el archivo de la memoria virtual
         *  4. Se verifica si el archivo se puede utilizar o no 
         *  5. Se utiliza el fs ya implementado para cargar el archivo. 
         *      5.1 Validacion solo por seguridad que el fs retorne bien el file
         *  6. Se ingresa en la tabla de contenidos para el control 
         *  7. Regresar el indice que se encuentra en la tabla. 
         */
        
        if (!validAddress(puntero_archivo)) return terminate();
        
        int fileDesc = getFileDescriptor(); 
        if (fileDesc == -1) return -1;
        
        String fileName = readVirtualMemoryString(puntero_archivo, maxSyscallArgLength);
        
        if(!create && isUsed(fileName)) return -1; 
        
        //if (!FileRef.referenceFile(fileName)) return -1;	
        
        OpenFile file = UserKernel.fileSystem.open(fileName, create);
        
        if (file == null) {
            //FileRef.unreferenceFile(fileName);
            return -1;
        }
        
        if(!create) openFilesList.add(fileName); 
        fileTable[fileDesc] = file;
        return fileDesc;
    }

    protected boolean isUsed(String file) {
        if ( openFilesList.indexOf(file) >= 0 )
            return true;
        return false ;
    }
    
    protected boolean validAddress(int vaddr) {
        int vpn = Processor.pageFromAddress(vaddr);
        return vpn < numPages && vpn >= 0;
    }

    private int terminate() {
        handleExit(null);
        return -1;
    }

    protected int getFileDescriptor() {
        for (int i = 0; i < fileTable.length; i++) {
            if (fileTable[i] == null) {
                return i;
            }   
        }
        return -1;
    }

    private boolean validFileDescriptor(int fileDesc) {

        if (fileDesc < 0 || fileDesc >= fileTable.length) {
            return false;
        }

        return fileTable[fileDesc] != null;
    }

    
    private int translateVirtualAddress(int address) {
        
        
        /*
         *  Input
         *      address = Virtual Address
         *  Output
         *      Physical Address
         * 
         * Retorna 0 si el archivo se ha cerrado
         * Transforma de una direccion a una direccion fija. 
         * 
         * 1.   Obtener la page y el offset ( 32 bits )
         * 2.   Verificar si los valores obtenidos son correctos
         * 3.   Obtenemos la pagina fisica 
         * 4.   Retornamos la direccion 
         */
        
        int vpage  = Processor.pageFromAddress(address);
        int offset = Processor.offsetFromAddress(address);

        if (vpage >= pageTable.length)  return -1;
        TranslationEntry entry = pageTable[vpage];
        if (! entry.valid) return -1;
        int ppage = pageTable[vpage].ppn;
        
        return Processor.makeAddress(ppage, offset);
    }
    
    
    private static class ChildProcess {

        public Integer returnValue;
        public UserProcess process;

        ChildProcess(UserProcess child) {
            process = child;
            returnValue = null;
        }
    }
    
    
    
    
    
}
