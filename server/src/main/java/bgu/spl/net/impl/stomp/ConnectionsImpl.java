package bgu.spl.net.impl.stomp;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Connections;
import java.util.*;

public class ConnectionsImpl<T>  implements Connections<T>{
    
    // fields
    private ConcurrentHashMap<Integer, ConnectionHandler<T>> clientsToCH; // mapping between clients currently on server and their connection handler
    private ConcurrentHashMap<String,UserData> userToData; // mapping usernames who logged in at some point in the program with their UserData
    private ConcurrentHashMap<String,Channel> chanNameToChanData; // mapping between existing channel names and their Channel instance
    private ConcurrentHashMap<Integer,String> connToName; // mapping between existing clients and the user's using it
    private AtomicInteger messageId;

    // CTR
    public ConnectionsImpl()
    {
        clientsToCH = new ConcurrentHashMap<>();
        userToData = new ConcurrentHashMap<>();
        chanNameToChanData = new ConcurrentHashMap<>();
        connToName = new ConcurrentHashMap<>();
        messageId = new AtomicInteger(0);
    }


    // ConnectionsImpl will be a singelton throughout the program
    private static class ConnectionsHolder {
        private static final ConnectionsImpl instance = new ConnectionsImpl<>();
    }

    public static ConnectionsImpl getInstance()
    {
        return ConnectionsHolder.instance;
    }


    // methods

    // interface methods
    public boolean send(int connectionId, T msg) {

        // debugging
        // System.out.println("server is sending client #" + connectionId + " the message:");
        // System.out.println(msg);
        // System.out.println("is clientsToCH contains the key connectionId #" + connectionId + " ? " + clientsToCH.containsKey(connectionId));

        //TODO
        if (clientsToCH.containsKey(connectionId)) {
            clientsToCH.get(connectionId).send(msg);
            //System.out.println("Im in !");
            return true;
        }
        return false;
    }

    public void send(String channel, T msg)
    {
        //TODO
        if (chanNameToChanData.get(channel) != null) {
            for (Integer subscriber : chanNameToChanData.get(channel).getSubscribers()) 
                send(subscriber, msg);
        }
    }

    public void send(String channel, Message m)
    {
        if (chanNameToChanData.get(channel) != null) {
            for (Integer connectionId : chanNameToChanData.get(channel).getSubscribers()) {
                String subId = getUser(getName(connectionId)).getSubId(channel);

                // update MESSAGE frame according to the user's data
                m.headers.replace("subscription",subId);
                m.headers.replace("message-id","" + getMessageIdAndIncrement());
                // send updated MESSAGE frame to each client
                send(connectionId, (T) m.toString());
            }
        }
    }


    /**
     * Clearing data on client from ConnectionsImpl:
     *      Client's connection_handler
     *      Client's current user
     *      Removing client from all relevant channels in channelToSubsribers
     */
    public void disconnect(int connectionId)
    {
        // Remove user's subscriptions from all channels
        String username = connToName.get(connectionId);
        UserData data = userToData.get(username);
        for (Map.Entry<String, String> entry : data.getSubIdTochannels().entrySet()){
            chanNameToChanData.get(entry.getValue()).removeSubscriber(connectionId); // with the channel name take his instance Channel and remove his subscription
        }
        // Remove CH from currently logged-in clients' Map
        if (clientsToCH.containsKey(connectionId))
            clientsToCH.remove(connectionId);
        // Remove user from being logged-in on CH
        connToName.remove((Object) connectionId);
    }

    // ------- added methods -------

        // ------- clientToConnectionHandler map methods -------

    /**
     * add new client to existing client's list 
     * @param _connectionHandler - clients connection handler with all his information: client socket, encoder_decoder, protocol
     */
    public void addClient(int connectionId, ConnectionHandler<T> _connectionHandler)
    {
        clientsToCH.put(connectionId, _connectionHandler);
    }

        // ------- channelToSubscribers map methods -------

    /**
     * @param channel
     * @return TRUE if there is already channel with this name FALSE otherwise
     */
    public boolean isChannel(String channel) {
        //System.out.println("channel name in isChannel(): " + channel);
        return chanNameToChanData.containsKey(channel);
    }

    /**
     * create new channel and add the first subsriber
     * @param channel
     * @param connectionId
     */
    public void createChannelAndSubscribe(String channel, int connectionId) {
        // System.out.println("channel name in createChannelAndSubscribe(): " + channel);
        chanNameToChanData.put(channel, new Channel(channel));
        chanNameToChanData.get(channel).addSubscriber(connectionId);
    }

    /**
     * add new subsriber to an existing channel
     * @param channel
     * @param connectionId
     */
    public void addSubscriber(String channel, int connectionId) {
        chanNameToChanData.get(channel).addSubscriber(connectionId);
    }

    /**
     * remove subscriber from channel list
     * @INV channelToSubsribers.get(channel).contains(connectionId)
     * @param channel
     * @param connectionId
     */
    public void removeSubscriber(String channel, int connectionId) {
        if (chanNameToChanData.get(channel).nSubscribers() == 1)
            chanNameToChanData.remove((Object) channel);
        else
            chanNameToChanData.get(channel).removeSubscriber(connectionId);
    }

    /**
     * @param channel
     * @param connectionId
     * @return TRUE if client is subscribed to channel
     */
    public boolean isSubscribed(String channel, int connectionId) {
        return chanNameToChanData.get(channel).isSubscribed(connectionId);
    }

    /**
     * @param channel
     * @return TRUE if the game has already started FALSE otherwise
     */
    public boolean isGameStarted(String channel) {
        return chanNameToChanData.get(channel).isStarted();
    }

    public List<Integer> getChannelSubscribers(String channel) {
        return chanNameToChanData.get(channel).getSubscribers();
    }

        // ------- usernameToUserData map methods -------

    /**
     * @param username
     * @return TRUE if user is in users data base FALSE if isn't
     */
    public boolean isUser(String username) {
        return userToData.containsKey(username);
    }

    /**
     * @INV username is in data base
     * @param username
     * @param password
     * @return TRUE if password supplied matches the password saved in data base FALSE password supplied isn't matching  
     */
    public boolean isCorrectPassword(String username, String password) {
        return userToData.get(username).getPassword().equals(password);
    }

    /**
     * @param username
     * @return TRUE if user is already logged on other client FALSE if isn't
     */
    public boolean isUserLoggedIn(String username) {
        return userToData.get(username).isLoggedIn();
    }

    /**
     * add new user to our user's data base
     * @param username
     * @param _data
     */
    public void addUser(String username, UserData _data) {
        userToData.put(username, _data);
    }

    /**
     * update user is logged in && update the clientId that user is on currently
     * @param username
     */
    public void logUser(String username, int connectionId) {
        userToData.get(username).logIn();
        userToData.get(username).setClientId(connectionId);
     }

    /**
     * @param username
     * @return username data
     */
    public UserData getUser(String username) { return userToData.get(username); }


        // ------- connectionIdToUsername map methods -------

    /**
     * @param connectionId
     * @return username that logged currently on client
     */
    public String getName(int connectionId) {
        return connToName.get(connectionId);
    }

    /**
     * @param username
     * @param connectionId
     * When there is a connection between user-client we update our mapping between the two
     */
    public void addUserName(int connectionId, String username) { connToName.put(connectionId, username); }


    // ------- message-id -------
    public int getMessageIdAndIncrement() {
        return messageId.getAndIncrement();
    }
}
