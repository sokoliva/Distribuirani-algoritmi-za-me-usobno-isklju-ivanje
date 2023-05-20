import java.util.*; 

public class SinghalMutex extends Process implements Lock{
    int myts;
    LamportClock c = new LamportClock();

    Set<Integer> request_set = new HashSet<Integer>(); 
    Set<Integer> inform_set = new HashSet<Integer>(); 
    
    boolean requesting;
    boolean executing;


    public SinghalMutex(Linker initComm) {
        super(initComm);
        //inicijalizacija request skupa
        for (int i = 0; i < myId; i++){
            request_set.add(i);
        }
        requesting = false;
    }
    
    public synchronized void requestCS(){
        requesting = true;
        c.tick();
        myts = c.getValue();
        for(Integer id : request_set){
            sendMsg(id, "request", myts);
        } 
        while(!(request_set.isEmpty())){
            myWait();
        }
        requesting = false;
        executing = true;
    }

    public synchronized void releaseCS(){
        executing = false;
        for(Integer id : inform_set){
            sendMsg(id, "okay", c.getValue());
            request_set.add(id);
        }
        inform_set.clear();
    }

    public synchronized void handleMsg(Msg m, int src, String tag){
        int timeStamp = m.getMessageInt();
        c.receiveAction(src, timeStamp);

        if (tag.equals("request")){
            if(requesting){
                if(myts < timeStamp || ((timeStamp == myts) && (src > myId))){
                    inform_set.add(src);
                }
                 
                else{
                    sendMsg(src, "okay", c.getValue());
                    if(!request_set.contains(src)){
                        request_set.add(src);
                        sendMsg(src, "request", c.getValue());
                    }
                }
            }

            else if(executing){
                inform_set.add(src);
            }

            else{
                request_set.add(src);   
                sendMsg(src, "okay", c.getValue());
            }
        }

        else if (tag.equals("okay")){
            request_set.remove(src);
            if(request_set.isEmpty()) notify();
        }
    }
}