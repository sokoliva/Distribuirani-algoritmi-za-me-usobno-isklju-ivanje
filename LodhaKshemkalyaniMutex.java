import java.util.*; 

public class LodhaKshemkalyaniMutex extends Process implements Lock{
    int myts;
    LamportClock c = new LamportClock();
    public int RV[] = new int[N]; 
    SortedSet<Pair> LRQ = new TreeSet<Pair>();

    public LodhaKshemkalyaniMutex(Linker initComm) {
        super(initComm);
        Arrays.fill(RV, 0);
        myts = Symbols.Infinity;   
    }
    
    public synchronized void requestCS(){
        c.tick();
        myts = c.getValue();
        Pair R = new Pair(myts, myId);
        LRQ.add(R);  
        Arrays.fill(RV, 0); 
        RV[myId] = 1;
        broadcastMsg("request", myts);
        while (!CheckExecuteCS()) {myWait();}
    }

    public synchronized void releaseCS(){  
        if(LRQ.first().getY() == myId){LRQ.remove(LRQ.first());}   
        if(!LRQ.isEmpty()){
            sendMsg(LRQ.first().getY(), "flush", myts);
            LRQ.clear();
        }
        for(int i = 0; i < N; i++){
            if(RV[i] == 2) sendMsg(i, "reply", myts);
        }
    }

    public synchronized boolean CheckExecuteCS(){
        if(LRQ.first().getY() != myId){ return false;}
        for(int i = 0; i < N; i++){
            if(RV[i] == 0) return false;          
        }
        notify();
        return true;
    }

    public synchronized void handleMsg(Msg m, int src, String tag){
        int SN = m.getMessageInt(); 
        c.receiveAction(src, SN); 
        Pair R2 = new Pair(SN, src);

        if (tag.equals("request")){
            if(LRQ.isEmpty()){
                sendMsg(src, "reply", myts);
            }  
            else{
                if(RV[src] == 0){
                    LRQ.add(R2);
                    RV[src]=1;
                    CheckExecuteCS();
                }
                else RV[src] = 2;
            }  
        }

        else if (tag.equals("reply")){
            //salje se procesima koji cekaju red i nisu konkurentni
            RV[src] = 1;
            while((LRQ.first()).compareTo(R2) < 1){
                LRQ.remove(LRQ.first());
            }
            CheckExecuteCS();
        } 
        
        else if (tag.equals("flush")){
            //salje se jednom konkurentom procesu poslije CS
            RV[src] = 1;
            while((LRQ.first()).compareTo(R2) < 1){
                LRQ.remove(LRQ.first());
            }
            CheckExecuteCS();
        } 
    }
}