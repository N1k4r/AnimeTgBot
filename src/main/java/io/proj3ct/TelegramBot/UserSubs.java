package io.proj3ct.TelegramBot;

import java.util.*;

public class UserSubs {
    private Map<String, String> request = new HashMap<>();
    private Queue<String> personalSubs = new PriorityQueue<>();

    public boolean setSub(String request) {
        if (this.request.containsKey(request))
            return false;
        this.request.put(request, "");
        return true;
    }

    public void setLastUrlPic(String request, String url){
        this.request.put(request, url);
    }

    public boolean deleteSub(String request){
        return this.request.remove(request, this.request.get(request));
    }

    public int listSizeSub(){
        return request.size();
    }

    public boolean keyExist(String request){
        return this.request.containsKey(request);
    }

    public String getValue(String key){
        return request.get(key);
    }

    public Set<String> getKey(){
        return request.keySet();
    }

    public void setPersonalSubs(Queue<String> personalSubs) {
        this.personalSubs = personalSubs;
    }

    public String pollQueue(){
        return  personalSubs.poll();
    }

    public boolean emptyQueue(){
        return personalSubs.isEmpty();
    }

}
