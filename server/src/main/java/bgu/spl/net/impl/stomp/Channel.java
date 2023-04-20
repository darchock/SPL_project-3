package bgu.spl.net.impl.stomp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class Channel {

    // fields
    public List<Integer> subscribers;
    public String channel_name;
    private AtomicBoolean isStarted;

    // CTR
    public Channel(String _name) {
        channel_name = _name;
        subscribers = Collections.synchronizedList(new ArrayList<>());
        isStarted = new AtomicBoolean(false);
    }


            // methods

    public boolean isStarted() {
        return isStarted.get();
    } 

    
    public void setStarted() {
        isStarted.set(true);
    }

    public void addSubscriber(int connectionId) {
        subscribers.add(connectionId);
    }

    public void removeSubscriber(int connectionId) {
        subscribers.remove((Object) connectionId);
    }

    public boolean isSubscribed(int connectionId) {
        return subscribers.contains(connectionId);
    }

    public int nSubscribers() {
        return subscribers.size();
    }

    public List<Integer> getSubscribers() {
        return subscribers;
    }
    
}
