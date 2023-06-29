import java.io.*;
import java.util.*;

public class MaekawaMutex extends Process implements Lock {
    int myts;
    LamportClock c = new LamportClock();
    boolean reply = false;
    public int R[]; 
    int numOkay = 0;
    IntLinkedList failed_list = new IntLinkedList();
    SortedSet<Pair> LRQ = new TreeSet<Pair>();
    int last_granted;
    int last_ts;
    boolean failed = false;

    public MaekawaMutex(Linker initComm, String kvorum) {
        super(initComm);
        String[] kvorum_ = kvorum.split(",");
        R = new int[kvorum_.length];
        for (int i = 0; i < kvorum_.length; i++) {
            R[i] = Integer.valueOf(kvorum_[i]);
        }
        myts = Symbols.Infinity;
    }
 
    public synchronized void requestCS() {
        failed = false;
        c.tick();
        myts = c.getValue();
        numOkay = 0;
        for(int i = 0; i < R.length; i++){
            if(R[i]!=myId){ sendMsg(R[i], "request", myts);}
        }
        //sam sebe ispituje
        selfRequest();
        while(numOkay < R.length){myWait();}
    }

    public synchronized void selfRequest(){
        //da si dopustenje
        if(!reply){
            numOkay++;
            reply = true;
            last_granted = myId;
            last_ts = myts;
        }
        //nekome je dao vec dopustenje
        else{
            Pair pair = new Pair(myts, myId);
            LRQ.add(pair);
            //ima slabiji prioritet od onoga kojemu je dao dopustenje
            if((myts > last_ts)|| ((myts == last_ts) && (myId > last_granted))){
                failed = true;
                failed_list.add(myId);
            }
            //ima jaci prioritet
            else{
                if(last_granted!=myId){sendMsg(last_granted, "inquire", c.getValue());}
            }
        }

    }

    public synchronized void releaseCS() {
        myts = Symbols.Infinity;
        reply = false;
        for(int i = 0; i < R.length; i++){
            if(R[i]!=myId){sendMsg(R[i], "release", c.getValue());}
        }
    }

    public synchronized void handleMsg(Msg m, int src, String tag) {
        int timeStamp = m.getMessageInt();
        c.receiveAction(src, timeStamp);
        if (tag.equals("request")) {
            //nikome nije dao dopustenje sve ok
            if(!reply){
                reply = true;
                last_granted = src;
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
                    if(last_granted!=myId){sendMsg(last_granted, "inquire", c.getValue());}
                    //sebi sam dao pristup i sada ga vracam
                    else{
                        //zeznuh se, vracam si dopustenje
                        if(!failed_list.isEmpty()){
                            numOkay--;
                            Pair pair_ = new Pair(last_ts, myId);
                            LRQ.add(pair_);
                            if(LRQ.first().getY() != myId){sendMsg(LRQ.first().getY(), "reply", c.getValue());}
                            else{
                                numOkay++;
                                if (numOkay == R.length){ notify();}
                                if(failed_list.contains(myId)){
                                    failed_list.removeObject(myId);
                                    if(failed_list.isEmpty()) {failed = false;}
                                }
                            }
                            LRQ.remove(LRQ.first());
                            last_granted = LRQ.first().getY();
                            last_ts = LRQ.first().getX();
                        }                       
                    }
                }
                //pendingQ.add(src);
            }  
        }

        else if (tag.equals("reply")) {
            numOkay++;
            if (numOkay == R.length){ notify();} 
            if (failed_list.contains(src)){
                failed_list.removeObject(src);
                if(failed_list.isEmpty()) {failed = false;}
            }  
        }

        else if (tag.equals("release")) {
            if(LRQ.isEmpty()){reply = false;}
            else{
                //int pid = pendingQ.removeHead();
                if(LRQ.first().getY() != myId){sendMsg(LRQ.first().getY(), "reply", c.getValue());}
                else{
                    numOkay++;
                }
                last_granted = LRQ.first().getY();
                last_ts = LRQ.first().getX();
                LRQ.remove(LRQ.first());
            }   
        }

        else if (tag.equals("inquire")) {
            if(failed){
                sendMsg(src, "yield", c.getValue());
                numOkay--;
            }
        }

        else if (tag.equals("yield")) {
            Pair pair = new Pair(last_ts, src);
            LRQ.add(pair);
            if(LRQ.first().getY() != myId){sendMsg(LRQ.first().getY(), "reply", c.getValue());}
            else{
                numOkay++;
            }
            last_granted = LRQ.first().getY();
            last_ts = LRQ.first().getX();
            LRQ.remove(LRQ.first());         
        }

        else if (tag.equals("failed")) {
            failed = true;
            failed_list.add(src);
        }
    }
}
