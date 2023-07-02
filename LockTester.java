public class LockTester {
    public static void main(String[] args) throws Exception {
        Linker comm = null;
        try {
            String baseName = args[0];
            int myId = Integer.parseInt(args[1]);
            int numProc = Integer.parseInt(args[2]);
            comm = new Linker(baseName, myId, numProc);
            Lock lock = null;
            if (args[3].equals("Singhal"))
                lock = new SinghalMutex(comm);
            if (args[3].equals("LodhaKshemkalyani"))
                lock = new LodhaKshemkalyaniMutex(comm);
            if (args[3].equals("Maekawa")){
                String kvorum = args[4];
                lock = new MaekawaMutex(comm, kvorum);}
            for (int i = 0; i < numProc; i++)
               if (i != myId)
                  (new ListenerThread(i, (MsgHandler)lock)).start();
            while (true) {
                System.out.println(myId + " is not in CS");
                Util.mySleep(12000);
                lock.requestCS();
                System.out.println(myId + " is in CS *****");
                Util.mySleep(12000);
                lock.releaseCS();
            }
        }
        catch (InterruptedException e) {
            if (comm != null) comm.close();
        }
        catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        }
    }
}
