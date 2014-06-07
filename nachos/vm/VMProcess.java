package nachos.vm;

import java.util.Arrays;
import java.util.Random;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
    
    /*
     * Variables
     * 
     * 
     */
    
    private static Lock pageLock = new Lock();	
    private static final int pageSize = Processor.pageSize;
    
    private static final char dbgProcess = 'a';
    private static final char dbgVM = 'v';
    
    private static final int syscallHalt = 0, syscallExit = 1;
        
    
	public VMProcess() {
		super();
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
		VMKernel.getKernel().invalidateTLB();
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		return true;
	}
		
	static private void patternArray(byte[] array, int start, int end, int offset) {
            int d = offset;
            int[] pattern = {0xEF, 0xBE, 0xAD, 0xDE};

            while(start < end) {
                    array[start] = (byte)pattern[d];
                    start++;

                    d = (d+1)%pattern.length;
            }
	}
        
	static private void patternArray(byte[] array, int start, int end) {
            patternArray(array,start,end,0);
	}
	
	static public void selfTest() {
		boolean pass = true;
		System.out.println("---Testing VMProcess---");
		
		VMProcess var_d_input_w_r = new VMProcess();
		var_d_input_w_r.numPages = var_d_input_w_r.stackPages; // don't load from coff at all.
		
		byte[][] testData = new byte[4][Processor.pageSize];
		byte[] tmpData = new byte[Processor.pageSize];
		
		for(int i=0; i<testData.length; i++) {
                    patternArray(testData[i], 0, Processor.pageSize, i);
		}
		
		int addr[] = new int[4];
		for(int i=0; i<addr.length; i++) {
			addr[i] = Processor.makeAddress(i, 0);
		}
		
		String postfix = "";
		for(int i=0; i<4; i++) {
                    int p = i%2;
                    var_d_input_w_r.writeVirtualMemory(addr[p], testData[p]);
                    var_d_input_w_r.readVirtualMemory(addr[p], tmpData);

                    if(!Arrays.equals(testData[p], tmpData)) {
                        pass = false;
                    }

                    if(i == 1) {
                        VMKernel.getKernel().invalidateTLB();	
                    }

		}
		
		// writing to the next two pages should swap out these older two.
		for(int i=2; i<4; i++) {
			var_d_input_w_r.writeVirtualMemory(addr[i], testData[i]);
		}
		for(int i=0; i<2; i++) {
			var_d_input_w_r.readVirtualMemory(addr[i], tmpData);

			if(!Arrays.equals(testData[i], tmpData)) {
				System.err.println("FAIL: Read from virtual memory failed on page "+i+" swapped from disk.");
				pass = false;
			}
			
		}
		
		if(pass){
			System.out.println("->All tests completed successfully!");
		}else{
			System.err.println("Some tests failed.");
		}
	}
	
	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
            pageLock.acquire();
            VMKernel.getKernel().discard(this);
            pageLock.release();

            coff.close();
	}
	
	public boolean readyPage(TranslationEntry te) {
            if(te == null)
                return false;

            // Kernel y su vpn
            if(te.vpn >= numPages - stackPages -1) {
                // Pagina que se encuentra en un stakc
                byte[] memory = Machine.processor().getMemory();
                int paddr = Processor.makeAddress(te.ppn, 0);
                int end = paddr + Processor.pageSize;

                patternArray(memory, paddr, end);
                
            } else {
                // El caso del coff
                te.valid = false;
                if(coff != null )
                for (int s = 0; s < coff.getNumSections(); s++) {
                    CoffSection section = coff.getSection(s);
                    if(section.getFirstVPN() <= te.vpn && te.vpn < section.getFirstVPN() + section.getLength()) {
                        // Cargamos la pagina
                        int cvpn = te.vpn - section.getFirstVPN();
                        section.loadPage(cvpn, te.ppn);
                        te.valid = true;
                        te.readOnly = section.isReadOnly();
                        break;
                    }
                }				
            }	

            return true;
	}
	
	private void checkPageFault(int vpn) {
            if(vpn < 0 || vpn >= numPages) {
                handleSyscall(syscallExit, 3, 0, 0, 0);
            }		
	}
	
	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>.
	 * The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause
	 *            the user exception that occurred.
	 */
	public void handleException(int cause) {
            Processor processor = Machine.processor();
            VMKernel kernel = VMKernel.getKernel();

            switch (cause) {
            case Processor.exceptionPageFault:{
                int vaddr = processor.readRegister(Processor.regBadVAddr);
                int vpn = Processor.pageFromAddress(vaddr);

                checkPageFault(vpn);		

                pageLock.acquire();
                if(kernel.readyPage(this, vpn) == null) {
                        pageLock.release();
                        checkPageFault(-1);
                }
                pageLock.release();
            } break;
            case Processor.exceptionTLBMiss:
                int vaddr = processor.readRegister(Processor.regBadVAddr);
                int vpn = Processor.pageFromAddress(vaddr);

                checkPageFault(vpn);

                pageLock.acquire();
                TranslationEntry page = kernel.readyPage(this, vpn);

                if(page == null) {
                        pageLock.release();
                        checkPageFault(-1);
                }
                kernel.tran_input_toTLB_entry(page);
                pageLock.release();
            break;

            default:
                super.handleException(cause);
                break;
            }
	}

	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		return super.handleSyscall(syscall, a0, a1, a2, a3);
	}
	
	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must <i>not</i>
	 * destroy the current process if an error occurs, but instead should return
	 * the number of bytes successfully copied (or zero if no data could be
	 * copied).
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to read.
	 * @param data
	 *            the array where the data will be stored.
	 * @param offset
	 *            the first byte to write in the array.
	 * @param length
	 *            the number of bytes to transfer from virtual memory to the
	 *            array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {

		byte[] memory = Machine.processor().getMemory();
		
		int vpn = Processor.pageFromAddress(vaddr);
		
		int voffset = Processor.offsetFromAddress(vaddr);
		int ioffset = offset;		
		
		while(length > 0) {
			int copy = Math.min(length, pageSize - voffset);
			pageLock.acquire();
			TranslationEntry page = VMKernel.getKernel().readyPage(this, vpn);
			if(page == null) {
                            pageLock.release();
                            checkPageFault(-1);
			}
			int paddr = Processor.makeAddress(page.ppn, voffset);
			System.arraycopy(memory, paddr, data, offset, copy);
			page.used = true;
			pageLock.release();
			
			vpn++;
			voffset = 0;
			length -= copy;
			offset += copy;
		}
		return offset - ioffset;
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must <i>not</i>
	 * destroy the current process if an error occurs, but instead should return
	 * the number of bytes successfully copied (or zero if no data could be
	 * copied).
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to write.
	 * @param data
	 *            the array containing the data to transfer.
	 * @param offset
	 *            the first byte to transfer from the array.
	 * @param length
	 *            the number of bytes to transfer from the array to virtual
	 *            memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		

		byte[] memory = Machine.processor().getMemory();
		
		int vpn = Processor.pageFromAddress(vaddr);
		
		int voffset = Processor.offsetFromAddress(vaddr);
		int ioffset = offset;		
		
		while(length > 0) {
                    int copy = Math.min(length, pageSize - voffset);

                    pageLock.acquire();

                    TranslationEntry page = VMKernel.getKernel().readyPage(this, vpn);
                    if(page == null) {
                            pageLock.release();
                            checkPageFault(-1);
                    }
                    int paddr = Processor.makeAddress(page.ppn, voffset);

                    System.arraycopy(data, offset, memory, paddr, copy);
                    Lib.debug(dbgVM, "---WRITE: " + page.ppn + " vpn: " + vpn);		

                    page.dirty = true;
                    page.used = true;

                    pageLock.release();

                    vpn++;
                    voffset = 0;
                    length -= copy;
                    offset += copy;
		}
		return offset - ioffset;
	}

	
}