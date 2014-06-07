package nachos.threads;

import nachos.ag.BoatGrader;

public class Boat {

    static BoatGrader bg;

    public static void selfTest() {
        BoatGrader b = new BoatGrader();

        System.out.println("\n ***Testing Boats with only 2 children***");
        begin(0, 2, b);

	System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
  	begin(1, 2, b);

 	System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
  	begin(3, 3, b);
    }

    public static void begin(int adults, int children, BoatGrader b) {
        // Store the externally generated autograder in a class
        // variable to be accessible by children.
        bg = b;
        // Instantiate global variables here
        lock = new Lock();
        Adulto = new Condition2(lock);
        rideNinoMo = new Condition2(lock);
        rowNinoOahu = new Condition2(lock);
        ubicacionBote = oahu; //el bote empieza en Oahu
        chofer = false; //nadie me lleva a Molokai
        terminaNi = false;
        ninosOahu = children;
        adultosOahu = adults;
        ninoRegresa = true;
        // Create threads here. See section 3.4 of the Nachos for Java
        // Walkthrough linked from the projects page.

        for (int i = 0; i < children; i++) {
            Runnable runa = new Runnable() {
                public void run() {
                    ChildItinerary();
                }
            };
            new KThread(runa).fork();
        }
  
        for (int i = 0; i < adults; i++) {
            Runnable runa = new Runnable() {
                public void run() {
                    AdultItinerary();
                }
            };
            new KThread(runa).fork();
        }
    }

    static void AdultItinerary() {
        /* This is where you should put your solutions. Make calls
         to the BoatGrader to show that it is synchronized. For
         example:
         bg.AdultRowToMolokai();
         indicates that an adult has rowed the boat across to Molokai
         */
        lock.acquire();
        int locacion = oahu;
        if (locacion == oahu) {
            while (ubicacionBote != oahu || ninosOahu != 1) {
                Adulto.sleep();
            }
            bg.AdultRowToMolokai();
            //adulto llega a molokai
            locacion = molokai;
            ubicacionBote = molokai;
            adultosOahu--;
            System.out.println("Oahu: ");
            System.out.println("Adultos: " + adultosOahu + " / niños: " + ninosOahu);
        }
        rowNinoOahu.wakeAll();
        lock.release();
    }

    static void ChildItinerary() {
        lock.acquire();
        int locacion = oahu;
        while (!terminaNi) {
            if (locacion == oahu && ubicacionBote == oahu) {
                if (!chofer) {
                    chofer = true;
                    bg.ChildRowToMolokai();
                    //niño llega a Molokai
                    locacion = molokai;
                    ninosOahu--;
                    rowNinoOahu.sleep();
                } else {
                    locacion = molokai;
                    bg.ChildRideToMolokai();
                    //niño llega a Molokai
                    ubicacionBote = molokai;
                    ninosOahu--;
                    chofer = false;
                    if (adultosOahu == 0 && ninosOahu == 0) {
                        ninoRegresa = false;
                    }
                    if (ninoRegresa) {
                        rowNinoOahu.wakeAll();
                    } else {
                        terminaNi = true;
                    }
                    terminaNi = true;
                    rowNinoOahu.sleep();
                    
                }
            }
        }
        lock.release();

    }

    static void SampleItinerary() {
        // Please note that this isn't a valid solution (you can't fit
        // all of them on the boat). Please also note that you may not
        // have a single thread calculate a solution and then just play
        // it back at the autograder -- you will be caught.
        System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
        bg.AdultRowToMolokai();
        bg.ChildRideToMolokai();
        bg.AdultRideToMolokai();
        bg.ChildRideToMolokai();
    }
    static final int oahu = 0;
    static final int molokai = 1;
    static int ninosOahu;
    static int adultosOahu;
    static int ubicacionBote;
    public static Lock lock;
    public static Condition2 Adulto; //adulto se puede ir
    public static Condition2 rideNinoMo; //niño pasajero
    public static Condition2 rowNinoOahu; //niño no regresa solz
    static boolean chofer; // indica si el chofer esta listo o no
    /* Thread del niño debe ya no hace el ciclo,se termina
     */
    static boolean terminaNi;
    static boolean ninoRegresa; //si niño debe regresar por adulto
}
