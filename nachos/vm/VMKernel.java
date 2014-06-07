

package nachos.vm;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import nachos.machine.*;
import nachos.userprog.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
    
    
    private SwapFile swap = new SwapFile();
	private PhysicalMemory memoryPhysicalInstance = new PhysicalMemory();
	public boolean randomPageReplacement = false;
	private int pageFaults = 0;
	private static VMProcess dummy1 = null;
	
    
	
        
        
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
	}
	
	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
	}
	
	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		boolean pass = true;
		System.out.println("---Testing VMKernel---");
		
		if(pass){
			System.out.println("->All tests completed successfully!");
		}else{
			System.err.println("Some tests failed.");
		}
		
		VMProcess.selfTest();
		

		// performance comparison of page replacement.
		System.out.println("---Page Replacement Performance Comparison---");
		
		// firstly lets cause alot of page faults
		VMProcess process;

		int clockPageFaults = pageFaults;
		for(int i=0; i<4; i++) {
			process = new VMProcess();		
			process.execute("matmult.coff", new String[]{});
			process = null;
		}
		clockPageFaults = pageFaults - clockPageFaults;
		
		VMKernel.getKernel().randomPageReplacement = true;
		
		int randomPageFaults = pageFaults;
		for(int i=0; i<4; i++) {
			process = new VMProcess();		
			process.execute("matmult.coff", new String[]{});
			process = null;
		}
		randomPageFaults = pageFaults - randomPageFaults;		
		
		System.out.println("Matmult performance comparison: " + clockPageFaults + " . " + randomPageFaults + " using random replacement.");
		
	}
	
	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}
	
	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		swap.delete();
		super.terminate();
	}
	
        /*  
         *  Retorna el valor 
         *  @name VMKernel retorna el UserKernel actual.
         */
        
	static public VMKernel getKernel() {
            return (VMKernel) UserKernel.getKernel();
	}
	
        
        
        
        
        
	public void discard(VMProcess process) {
		swap.discard(process);
						
		for(Control_Process_Page r : find_process_on_hash(process, memoryPhysicalInstance.getControl_Process_Pages())) {
			memoryPhysicalInstance.removePage(r);
		}
	}
        
	
	public TranslationEntry readyPage(Control_Process_Page pid) {
            /*
             * Verifica si hay alguna pagina disponible para poder ser entregada 
             * si hay alguna returna la pagina que esta lista. 
             */
            
            
		TranslationEntry page = null;
		
		page = memoryPhysicalInstance.getPage(pid);
                // Verifica si esta en la memoria fisica.
		if(page != null)
                    return page;
		
                // Va servir para la cantidad del random
		pageFaults++;
		
                // Verifica si existe en el swap
		if(swap.swapIn(pid)) {
                    return memoryPhysicalInstance.getPage(pid);
		}
		
                // Sino hay ninguna va a generar una nueva TranslationEntry
		page = paginas_liberadas();
		
                // En caso no se ha creado ninguna
		if(page == null) {
                    return null;
		}
                
                
		page.vpn = pid.getVPN();
		
		if(pid.getProcess().readyPage(page)) {
			memoryPhysicalInstance.addPage(pid, page);
			return page;
		}		
		return null;
	}
	
	public TranslationEntry readyPage(VMProcess p, int vpn) {
		return readyPage(new Control_Process_Page(p, vpn));
	}
	
	public TranslationEntry paginas_liberadas() {
            int page_selected = -1;
            
            // Verificar en la memoria fisica si hay alguna disponible. 
            for(int p=0; p<Machine.processor().getNumPhysPages(); p++) {
                if(!memoryPhysicalInstance.hasPage(p)) {
                    // Si existe en el coreMap, entonces esa sera la pagina a ser libre
                    page_selected = p;
                    break;
                }
            }

            if(page_selected < 0) {
                // Sino encontro ninguna, tendra que realizarlo via randon. 
                
                if( randomPageReplacement ) {
                    
                    page_selected = new Random().nextInt(Machine.processor().getNumPhysPages());
                    // Remueve la pagina, de lo contrario
                    
                    if(!swap.swapOut( memoryPhysicalInstance.getControl_Process_Page(page_selected) ) ) {
                        // Se sale, ya que no pudo remover del swap. 
                        return null;
                    }
                } else {
                    // Sino hay ninguna pagina para hacer el random
                    Control_Process_Page pid = memoryPhysicalInstance.clock();
                    page_selected = memoryPhysicalInstance.getPage(pid).ppn;   
                    
                    
                    if(!swap.swapOut(pid)) {
                        return null;
                    }
                }
            }

            return new TranslationEntry(-1, page_selected, true, false, false, false);
	}
	
	public void updateFromTLB() {
            /*
             * Metodo para actualizar la tabla de procesos. 
             */ 
            
            
            Processor p = Machine.processor();

            for(int i=0; i < p.getTLBSize() ; i++) {
                TranslationEntry page = p.readTLBEntry(i);
                TranslationEntry memory_phisical_page = memoryPhysicalInstance.getPage(page.ppn);

                if(page != null && memory_phisical_page != null && page.valid && memory_phisical_page.valid) {
                    // Verifica si la tabla esta en uso o esta dirty. 
                    if(page.used)
                        memory_phisical_page.used = true;
                    if(page.dirty)
                        memory_phisical_page.dirty = true;
                }

            }
	}
	
        
	public void updateTLB() {
            
            /*
             *  Actualiza la TLB
             */
            
            Processor p = Machine.processor(); 

            for(int i=0 ;  i < p.getTLBSize() ; i++) {
                TranslationEntry page = p.readTLBEntry(i);
                TranslationEntry memory_phisical_page = memoryPhysicalInstance.getPage(page.ppn);

                if(memory_phisical_page != null) {
                    p.writeTLBEntry(i, memory_phisical_page);
                }			
            }
	}
	
	public void tran_input_toTLB_entry(int t, TranslationEntry tran_entry_input) {
            /*
             * Utiliza el TLB para alguna entrada
             */
            
            Processor p = Machine.processor();

            TranslationEntry old = p.readTLBEntry(t);		
            if(old.valid) {
                TranslationEntry page = memoryPhysicalInstance.getPage(old.ppn);

                if(page != null) {
                    if(old.used)
                        page.used = true;
                    if(old.dirty)
                        page.dirty = true;
                }		
            }

            if(tran_entry_input == null) {
                tran_entry_input = new TranslationEntry(-1,-1,false,false,false,false);
            }

            p.writeTLBEntry(t,tran_entry_input);
	}
	public void tran_input_toTLB_entry(TranslationEntry tran_entry_input) {
            
            /*
             *  
             */
            
            Processor p = Machine.processor();

            int tlbs = p.getTLBSize();	
            for(int t = 0 ; t < tlbs ; t++) {
                TranslationEntry te = p.readTLBEntry(t);
                if(!te.valid) {
                    p.writeTLBEntry(t, tran_entry_input);
                    return;
                }
            }

            int t = new Random().nextInt(tlbs);
            tran_input_toTLB_entry(t,tran_entry_input);		
	}
	
	public void invalidateTLB() {
            Processor p = Machine.processor();		
            for(int t=0; t<p.getTLBSize(); t++) {
                tran_input_toTLB_entry(t, null);
            }
	}
	
	private class Control_Process_Page {
            /*
             * Clase que se encarga de tener el control de la pagina. 
             * 
             */
            
            private VMProcess process = null;
            private int vpn = -1;
            
            public Control_Process_Page(VMProcess p, int vpn) {
                    this.process = p;
                    this.vpn = vpn;
            }
		
            public int hashCode() {
                    return process.hashCode() + 7*vpn;
            }
            
            public boolean equals(Object other) {
                if(other instanceof Control_Process_Page) {
                        Control_Process_Page opid = (Control_Process_Page)other;
                        return process == opid.process && vpn == opid.vpn; 
                }
                return false;
            }

            public VMProcess getProcess() {
                    return process;
            }
            
            public int getVPN() {
                    return vpn;
            }

            
	}
	
	static Set<Control_Process_Page> find_process_on_hash(VMProcess p, Set<Control_Process_Page> ids) {
            Set<Control_Process_Page> found_hash_info = new HashSet<Control_Process_Page>();

            for(Control_Process_Page pid : ids) {
                if(pid.getProcess() == p)
                    // Para todos los procesos, que el process es igual al VMProcess agregue.
                    found_hash_info.add(pid);
            }
            return found_hash_info;
	}
	
	private class SwapFile {	
		
            
        /*
         * SWAP
         *      Archivo que nos permite escribir y tambien leer las paginas para saber donde estan. 
         *      Se utiliza hash
         *      
         *      Se van a almacenar valores correspondientes a la posicion en el file file, van a estar en
         *      el swap table. 
         * 
         *      Encontramos la posicion por el algoritmo solo se debe de multiplicar por el page size. 
         *      
         */
            
            
            private Map<Control_Process_Page, Integer> swap_hash_table = new HashMap<Control_Process_Page, Integer>();
            private Queue<Integer> list_linked_deleted = new LinkedList<Integer>();
            private int size = 0;

            private OpenFile file = null;
            private String fileName = "nachos.swp";     // Archivo de intercambio . 

            public SwapFile(String filename) {
                this.fileName = filename;
            }
            public SwapFile() {
            }

            public void discard(VMProcess p ){ 
                /*
                 * Ya no toma en cuenta del hash, y lo mete en una lista temporal
                 */
                
                for(Control_Process_Page pid : find_process_on_hash(p, swap_hash_table.keySet())) {
                    list_linked_deleted.add(swap_hash_table.remove(pid));
                }
            }
            
            public void delete() {
                /*
                 * Elimina el archivo que se utilizo en el file y vuelve a tener todo como el inicio. 
                 */
                
                if(file != null)
                    file.close();
                
                fileSystem.remove(fileName);
                
            }
            
            public boolean swapIn(Control_Process_Page pid) {
                
                /*
                 * Encargado de meter la informacion en el swap
                 */
                
                // Validaciones, en caso ya no hay que meter la informacion. 
                if(memoryPhysicalInstance.hasPage(pid))
                    return true;

                // En caso del hash este la info. 
                if(!swap_hash_table.containsKey(pid))
                    return false;

                TranslationEntry page = paginas_liberadas();
                if(page == null)
                    return false;
                
                page.vpn = pid.getVPN();

                int indice_swap_hash = swap_hash_table.get(pid);

                Processor processor = Machine.processor();
                byte[] memoria_physical_instance = processor.getMemory();
                int paddr = Processor.makeAddress(page.ppn, 0);

                int bytes = file.read(indice_swap_hash, memoria_physical_instance, paddr, 1024);

                if(bytes == 1024) {
                    memoryPhysicalInstance.addPage(pid, page);
                }
                return bytes == 1024; 
            }

            public boolean swapOut(Control_Process_Page pid) {
                
                /*
                 * SwapOut es el proceso que esta encargado de sacar la info. 
                 */
                
                
                if(!memoryPhysicalInstance.hasPage(pid))
                    return true;

                TranslationEntry page = memoryPhysicalInstance.getPage(pid);
                if(page == null)
                    return false;

                // Va remover la informacion del TLB
                
                for(int t = 0 ; t < Machine.processor().getTLBSize(); t++) {
                    TranslationEntry indice_trans = Machine.processor().readTLBEntry(t);
                    if(indice_trans.vpn == page.vpn) { 
                        tran_input_toTLB_entry(t, null);
                    }
                }

                boolean ok = true;
                if(!page.readOnly && page.dirty) {	
                        Integer indice_swap_hash = swap_hash_table.get(pid);
                        if(indice_swap_hash == null) {
                                indice_swap_hash = list_linked_deleted.poll();
                        }			
                        if(indice_swap_hash == null) {
                                indice_swap_hash = size;				
                                size += Processor.pageSize;
                        }

                        if(file == null) {
                                file = fileSystem.open(fileName, true);
                                
                        }

                        int bytes = 0;
                        if(file != null) {
                                Processor processor = Machine.processor();
                                byte[] memoria_physical_instance = processor.getMemory();
                                int paddr = Processor.makeAddress(page.ppn, 0);
                                bytes = file.write(indice_swap_hash, memoria_physical_instance, paddr, Processor.pageSize);
                        }

                        if(bytes != Processor.pageSize) {
                                ok = false;
                                list_linked_deleted.add(indice_swap_hash);
                        } else {
                                swap_hash_table.put(pid, indice_swap_hash);				
                        }
                }	
                memoryPhysicalInstance.removePage(pid);
                return ok;
            }
		
	}
	
	private class PhysicalMemory {
            
            /*
             * Aqui esta todo el control de la memoryPhysicalInstanceoria
             * CoreMap = Tamano del numero de paginas fisica mapea una porcion de informacion, proceso y pagina que utilizan. 
             *           Lleva un seguimiento que contiene el translatyion entry. 
             * Clock    = Contiene todas las page id que tienen todo. 
             *            Algoritmo de seleccion, toma la descision de verificar si la pagina esta disponible. 
             * Inverted Page Table = Es una tabla que contiene la pagina de los procesos, y su traduccion fisica. 
             * 
             * 
             */
            
		private Map<Control_Process_Page, TranslationEntry> invertedPageTable = new HashMap<Control_Process_Page,TranslationEntry>();
		private Map<Integer, Control_Process_Page> coreMap = new HashMap<Integer, Control_Process_Page>();
		
		private ArrayList<Control_Process_Page> clock = new ArrayList<Control_Process_Page>();
		private int clockPos = 0;
		
		public Control_Process_Page clock() {
			if(clock.size() <= 0)
				return null;
			
			updateFromTLB();
			
			while(true) {
				Control_Process_Page pid = clock.get(clockPos);
				TranslationEntry page = getPage(pid);				
				
				if(!page.used) {
					updateTLB();
					return pid;
				} else {
					page.used = false;
				}
				
				clockPos = (clockPos+1) % clock.size();
			}			
		}
		
		public Control_Process_Page getControl_Process_Page(int ppn) {
			return coreMap.get(ppn);
		}
		public Set<Control_Process_Page> getControl_Process_Pages() {
			return invertedPageTable.keySet();
		}
                
		public Set<TranslationEntry> getPages() {
			return new HashSet<TranslationEntry>(invertedPageTable.values());
		}
		
		public boolean hasPage(int ppn) {
			return getControl_Process_Page(ppn) != null;
		}
                
		public boolean hasPage(Control_Process_Page id) {
			return invertedPageTable.containsKey(id);
		}

		public TranslationEntry getPage(Control_Process_Page id) {
			return invertedPageTable.get(id);
		}
		public TranslationEntry getPage(int ppn) {
			return getPage(getControl_Process_Page(ppn));
		}
		
		public TranslationEntry removePage(Control_Process_Page id) {
                    
                    // Se encarga de remover la pagina del coreMap, para ya no tenerlo seteado. 
                    
			TranslationEntry page = getPage(id);
			coreMap.remove(page.ppn);
			invertedPageTable.remove(id);
			page.ppn = -1;
			page.valid = false;
			
			clock.remove(id);
			if(clock.size() > 1)
                            clockPos = clockPos % clock.size();
			
			return page;
		}
		
		public void addPage(Control_Process_Page pid, TranslationEntry page) {
                    
                    // Se encarga de agregar una pagina a el coreMap. 
                    
			coreMap.put(page.ppn, pid);
			invertedPageTable.put(pid, page);
			
			if(clock.size() > 0)
				clock.add((clockPos -1 + clock.size()) % clock.size(), pid);
			else {
				clock.add(pid);
				clockPos = 0;
			}
			
			page.valid = true;
		}
	}
	
	
}