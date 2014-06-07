/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package nachos.userprog;

import java.util.HashMap;
import nachos.machine.Lib;
import nachos.threads.Lock;

/**
 *
 * @author Mrm
 */


public class FileRef {

    int references;
    boolean delete;

        /**
         * Increment the number of active references there are to a file
         * @return False if the file has been marked for deletion
         */
    
    public static boolean referenceFile(String fileName) {
        FileRef ref = updateFileReference(fileName);
        boolean canReference = !ref.delete;
        if (canReference) {
            ref.references++;
        }
        finishUpdateFileReference();
        return canReference;
    }

        /**
         * Decrement the number of active references there are to a file Delete
         * the file if necessary
         * @return 0 on success, -1 on failure
         */
    
        public static int unreferenceFile(String fileName) {
            FileRef ref = updateFileReference(fileName);
            ref.references--;
            Lib.assertTrue(ref.references >= 0);
            int ret = removeIfNecessary(fileName, ref);
            finishUpdateFileReference();
            return ret;
        }

        /**
         * Mark a file as pending deletion, and delete the file if no active
         * references
         * @return 0 on success, -1 on failure
         */
        
        public static int deleteFile(String fileName) {
            FileRef ref = updateFileReference(fileName);
            ref.delete = true;
            int ret = removeIfNecessary(fileName, ref);
            finishUpdateFileReference();
            return ret;
        }

        /**
         * Remove a file if marked for deletion and has no active references
         * Remove the file from the reference table if no active references THIS
         * FUNCTION MUST BE CALLED WITHIN AN UPDATEFILEREFERENCE LOCK!
         * @return 0 on success, -1 on failure to remove file
         */
        
        
        private static int removeIfNecessary(String fileName, FileRef ref) {
            if (ref.references <= 0) {
                globalFileReferences.remove(fileName);
                if (ref.delete == true) {
                    if (!UserKernel.fileSystem.remove(fileName)) {
                        return -1;
                    }
                }
            }
            return 0;
        }

        
        /**
         * Lock the global file reference table and return a file reference for
         * modification. If the reference doesn't already exist, create it.
         * finishUpdateFileReference() must be called to unlock the table again!
         * @param fileName File we with to reference
         * @return FileRef object
         */
        
        private static FileRef updateFileReference(String fileName) {
            globalFileReferencesLock.acquire();
            FileRef ref = globalFileReferences.get(fileName);
            if (ref == null) {
                ref = new FileRef();
                globalFileReferences.put(fileName, ref);
            }

            return ref;
        }

        /**
         * Release the lock on the global file reference table
         */
        
        private static void finishUpdateFileReference() {
            globalFileReferencesLock.release();
        }
        
        /**
         * Global file reference tracker & lock
         */
        
        private static HashMap<String, FileRef> globalFileReferences = new HashMap<String, FileRef>();
        private static Lock globalFileReferencesLock = new Lock();
    }
