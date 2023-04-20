package bgu.spl.net.impl.stomp;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class UserData {
    
    // fields
    //private String username; // user's username that he first joined with
    private String password; // user's password that he first joined with - unique to this username
    private int clientId; // the connectionId belongs to the client that user's is on
    private ConcurrentHashMap<String,String> subIdTochannels; // mapping between user's subscriptionId to name of Channel
    private ConcurrentHashMap<String,String> channelsToSubId; // mapping between name of Channel to user's subscriptionId
    private AtomicBoolean loggedIn = new AtomicBoolean(false);

    // CTR
    public UserData(String _password, int _connectionId) {
        password = _password;
        clientId = _connectionId;
        subIdTochannels = new ConcurrentHashMap<>();
        channelsToSubId = new ConcurrentHashMap<>();
        loggedIn.set(true);
    }

    // methods
    public String getPassword() {
        return password;
    }

    public boolean isLoggedIn() {
        return loggedIn.get();
    }

    public void logIn() {
        loggedIn.set(true);
    }

    /**
     * Reseting user's data: channel list, loggedIn boolean to false, clientId to -1
     */
    public void disconnect() {
        loggedIn.set(false);
        //channels.clear(); // to delete
        subIdTochannels.clear();
        channelsToSubId.clear();
        clientId = -1;
    }

    public void addChannel(String subscriptionId, String channel) {
        subIdTochannels.put(subscriptionId, channel);
        channelsToSubId.put(channel, subscriptionId);
    }

    public void removeChannel(String subscriptionId) {
        String channel = subIdTochannels.remove(subscriptionId);
        channelsToSubId.remove(channel);

    }

    public ConcurrentHashMap<String,String> getSubIdTochannels(){
        return subIdTochannels;
    }

    public ConcurrentHashMap<String,String> getChannelsToSubId(){
        return channelsToSubId;
    }

    public String getChannel(String subscriptionId) {
        return subIdTochannels.get(subscriptionId);
    }

    public String getSubId(String channel) {
        return channelsToSubId.get(channel);
    }

    public void setClientId(int connectionId) {clientId = connectionId;}

}
