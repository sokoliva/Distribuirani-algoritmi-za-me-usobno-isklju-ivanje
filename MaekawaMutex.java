import java.util.*;

public class MaekawaMutex extends Process implements Lock {
    int myts;
    LamportClock c = new LamportClock();
    boolean reply = false;
    public int R[]; 
    public boolean L[];
    public boolean Y[];
    int numOkay = 0;
    SortedSet<Pair> LRQ = new TreeSet<>();
    SortedSet<Pair> temp = new TreeSet<>();
    int last_granted;
    int last_ts;
    boolean failed = false;
    boolean Yfailed = false;
    int svojReq;

    public MaekawaMutex(Linker initComm, String kvorum) {
        super(initComm);
        String[] kvorum_ = kvorum.split(",");
        R = new int[kvorum_.length];
        for (int i = 0; i < kvorum_.length; i++) {
            R[i] = Integer.parseInt(kvorum_[i]);
        }
        L = new boolean[N];
        Y = new boolean[N];
        myts = Symbols.Infinity;
        last_granted = myId;
        last_ts = Symbols.Infinity;
    }
 
    @Override
    public synchronized void requestCS() {
        failed = false;
        Yfailed = true;
        c.tick();
        myts = c.getValue();
        numOkay = 0;
        for(int i = 0; i < R.length; i++){
            if(R[i]!=myId){ sendMsg(R[i], "request", myts);}
            Y[R[i]] = true;
        }
        //sam sebe ispituje
        selfRequest();
        Y[myId] = true;
        while(numOkay < R.length){myWait();}
    }

    public synchronized void selfRequest(){
        //System.out.println("self request ");
        svojReq = myts;
        //da si dopustenje
        if(!reply){
            numOkay++;
            reply = true;
            last_granted = myId;
            //System.out.println("ima me " + last_granted);
            last_ts = myts;
            L[myId] = true;
            Y[myId] = false;
            Yfailed = false;
            for(int i = 0; i < N; i++){
                if(Y[i] == true){
                    Yfailed = true;
                    break;
                }
            }
            if (numOkay == R.length){
                Yfailed = false;
                failed = false;
                reply = true;
                notify();
                for(int i = 0; i < N; i++){
                    L[i] = false;
                }
            }  
        }
        else{
            Pair pair = new Pair(myts, myId);
            LRQ.add(pair);
            if((myts > last_ts)|| ((myts == last_ts) && (myId > last_granted))){
                failed = true;
            }
            else{
                sendMsg(last_granted, "inquire", c.getValue());
            }
        }
    }
    
    public synchronized void selfRelease(){
        //System.out.println("self release ");
        if(LRQ.isEmpty()){
            reply = false;
        }
        else{
            if(LRQ.first().getY() != myId){
                sendMsg(LRQ.first().getY(), "reply", c.getValue());
            }
            else{
                numOkay++;
                L[myId] = true;
                Y[myId] = false;
                Yfailed = false;
                for(int i = 0; i < N; i++){
                    if(Y[i] == true){
                        Yfailed = true;
                        break;
                    }
                }
                if (numOkay == R.length){
                    Yfailed = false;
                    failed = false;
                    reply = true;
                    notify();
                    for(int i = 0; i < N; i++){
                        L[i] = false;
                    }
                    return;
                }  
            }
            last_granted = LRQ.first().getY();
            //System.out.println("ima me " + last_granted);
            last_ts = LRQ.first().getX();
            LRQ.remove(LRQ.first());
            while(!LRQ.isEmpty()){
                temp.add(LRQ.first());
                LRQ.remove(LRQ.first());
            }
            while(!temp.isEmpty()){
                if(temp.first().getY() != myId){
                    LRQ.add(temp.first());
                }
                temp.remove(temp.first());
            }
            reply = true;
        }
    }

    @Override
    public synchronized void releaseCS() {
        myts = Symbols.Infinity;
        reply = false;
        for(int i = 0; i < R.length; i++){
            if(R[i]!=myId){
                sendMsg(R[i], "release", c.getValue());
            }
        }
        selfRelease();
    }

    @Override
    public synchronized void handleMsg(Msg m, int src, String tag) {
        int timeStamp = m.getMessageInt();
        c.receiveAction(src, timeStamp);
        switch (tag) {
            case "request":
                //System.out.println(reply);
                //nikome nije dao dopustenje sve ok
                if(!reply){
                    reply = true;
                    last_granted = src;
                    //System.out.println("ima me " + last_granted);
                    last_ts = timeStamp;
                    sendMsg(src, "reply", c.getValue());
                }
                //nekome je dao dopustenje
                else{
                    Pair pair = new Pair(timeStamp, src);
                    LRQ.add(pair);
                    //taj netko ima veci prioritet
                    if((timeStamp > last_ts)|| ((timeStamp == last_ts) && (src > last_granted))){
                        sendMsg(src, "failed", c.getValue());
                    }
                    //taj netko ima slabiji prioritet
                    else{
                        //taj netko nisam ja pa samo posaljem inquire
                        if(last_granted!=myId){
                            sendMsg(last_granted, "inquire", c.getValue());
                        }
                        //sebi sam dao pristup i sada ga vracam
                        else{
                            //zeznuh se, vracam si dopustenje
                            if(failed || Yfailed){
                                numOkay--;
                                sendMsg(LRQ.first().getY(), "reply", c.getValue());
                                last_granted = LRQ.first().getY();
                                //System.out.println("ima me " + last_granted);
                                L[myId] = false;
                                LRQ.remove(LRQ.first());
                                Pair pair2 = new Pair(last_ts, myId);
                                LRQ.add(pair2);
                                last_ts = LRQ.first().getX();
                            }
                        }
                    }
                }   break;
            case "reply":
                Y[src] = false;
                Yfailed = false;
                for(int i = 0; i < N; i++){
                    if(Y[i] == true){
                        Yfailed = true;
                        break;
                    }
                }
                L[src] = true;
                numOkay++;
                if (numOkay == R.length){
                    Yfailed = false;
                    failed = false;
                    reply = true;
                    notify();
                    for(int i = 0; i < N; i++){
                        L[i] = false;
                    }
                }  
                break;
            case "release":
                if(last_granted == src){
                    if(LRQ.isEmpty()){reply = false;}
                    else{
                        if(LRQ.first().getY() != myId){
                            sendMsg(LRQ.first().getY(), "reply", c.getValue());
                        }
                        else{
                            numOkay++;
                            L[myId] = true;
                            Y[myId] = false;
                            Yfailed = false;
                            for(int i = 0; i < N; i++){
                                if(Y[i] == true){
                                    Yfailed = true;
                                    break;
                                }
                            }
                            if (numOkay == R.length){
                                Yfailed = false;
                                failed = false;
                                reply = true;
                                notify();
                                for(int i = 0; i < N; i++){
                                    L[i] = false;
                                return;
                                }
                            }
                        }
                    last_granted = LRQ.first().getY();
                    //System.out.println("ima me " + last_granted);
                    last_ts = LRQ.first().getX();
                    LRQ.remove(LRQ.first()); 
                    }
                }   
                break;
            case "inquire":
                if(failed || Yfailed){
                    L[src] = false;
                    sendMsg(src, "yield", c.getValue());
                    Yfailed = true;
                    Y[src] = true;
                    numOkay--;
                }   break;
            case "yield":
                Pair pair = new Pair(last_ts, src);
                LRQ.add(pair);
                if(LRQ.first().getY() != myId){
                    sendMsg(LRQ.first().getY(), "reply", c.getValue());}
                else{
                    numOkay++;
                    L[myId] = true;
                }
                last_granted = LRQ.first().getY();
                //System.out.println("ima me " + last_granted);
                last_ts = LRQ.first().getX();
                LRQ.remove(LRQ.first());
                break;
            case "failed":
                //numOkay = 0;
                failed = true;
                
                
                /*
                for(int i = 0; i < N; i++){
                    if(L[i] == true){
                        L[i] = false;
                        if(i != myId){
                            sendMsg(i, "yield", c.getValue());  
                            numOkay--;
                        }
                        else{
                            Pair pair3 = new Pair(svojReq, last_granted);
                            LRQ.add(pair3);
                            last_granted = LRQ.first().getY();
                            //System.out.println("ima me " + last_granted);
                            last_ts = LRQ.first().getX();
                            if(last_granted == myId){
                                L[myId] = true;
                                numOkay++;
                            }
                            else{
                                sendMsg(LRQ.first().getY(), "reply", c.getValue());
                            }
                            LRQ.remove(LRQ.first());
                            
                            
                            while(!LRQ.isEmpty()){
                                temp.add(LRQ.first());
                                if(myId != LRQ.first().getY()){
                                    sendMsg(LRQ.first().getY(), "failed", c.getValue());
                                    }
                                else{
                                    failed = true;
                                }
                                LRQ.remove(LRQ.first());
                            }
                            while(!LRQ.isEmpty()){
                                LRQ.add(temp.first());
                                temp.remove(LRQ.first());
                            } 
                            
                        }
                    }
                    
                }
                */
                
                
                break;
            default:
                break;
        }
    }
}
