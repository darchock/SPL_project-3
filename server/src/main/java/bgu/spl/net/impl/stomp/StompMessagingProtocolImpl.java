package bgu.spl.net.impl.stomp;

import java.util.*;
import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.srv.Connections;

public class StompMessagingProtocolImpl<T> implements StompMessagingProtocol<T> {
    
    // fields
    private int connectionId; // client's id
    private ConnectionsImpl<String> connections; // connections singelton instance
    private HashMap<Integer,String> subscriptions; // current subscription of user currently logged on client
    private UserData currUser;
    private volatile boolean loggedIn; // if there is a user loggen on client
    private boolean shouldTerminate = false;
    
    // private enum COMMANDS {
    //     CONNECT,
    //     SEND,
    //     SUBSCRIBE,
    //     UNSUBSCRIBE,
    //     DISCONNECT
    // };

    // CTR
    public StompMessagingProtocolImpl()
    {
        subscriptions = new HashMap<>();
        currUser = null;
        loggedIn = false;
    }

                                                    // methods

    /**
    * Initializing the client's protocol 
    * @param connectionId - client's id
    * @param connections - singelton instance of connections
    */
    public void start(int connectionId, Connections<T> connections){
        this.connectionId = connectionId;
        this.connections = (ConnectionsImpl<String>) connections;
    }
    
    public void process(T message){
        //TODO

        // for debugging
        // System.out.println("client #" + connectionId + " has sent the following message:");
        // System.out.println(message);


        String msg = (String) message;
        Message m = extract(msg);

        String c = m.getCommand().trim();

        if (!loggedIn && !c.equals("CONNECT"))
            ERROR(m, "User is not connected", "Please log-in before trying to do anything else.");

        switch (c)
        {
            case ("CONNECT"):
                exectueCONNECT(m); // execute how to deal with CONNECT frame, sends ERROR OR CONNECTED
                break;
            case ("SEND"):
                executeSEND(m); // execute how to deal with SEND frame, sends ERROR OR MESSAGE
                break;
            case ("SUBSCRIBE"):
                exectueSUBSCRIBE(m); // execute how to deal with SUBSCRIBE frame, sends ERROR OR RECEIPT
                break;
            case ("UNSUBSCRIBE"):
                executeUNSUBSCRIBE(m); // execute how to deal with UNSUBSCRIBE frame, sends ERROR OR (RECEIPT to the original client && UBSUBSCRIBE to each client in channel)
                break;
            case ("DISCONNECT"):
                exectueDISCONNECT(m); // execute how to deal with DISCONNECT frame, sends ERROR OR RECEIPT
                break;
            default:
                ERROR(m, "Unsupported command", "You've tried to insert an unsupported command. Please try again");
            
        }

        // end of process()
    }
	
	/**
     * @return true if the connection should be terminated
     */
    public boolean shouldTerminate(){
        //TODO
        return shouldTerminate;
    }

            // ----- Added Methods -----
    
    /**
     * This methods is being called when we need to close the connection
     */
     public void terminate(){
        shouldTerminate = true;
     }

     /**
      * client disconnects - called when frame DISCONNECT is being proccessed OR when ERROR occurs
      */
     public void disconnect() {
        if (currUser != null)
        {
            currUser.disconnect(); // reseting user's data
            connections.disconnect(connectionId); // clearing data on client from ConnectionsImpl: client's connection_handler, client's current user
        }    
    }

    /**
     * This methods is being called after validating paramteres to be OK
     * add new subscription to client's subscription's list
     * @param subId - subscription Id
     * @param channel - channel (topic) Client is subscribing to
     */
    public void addSubscription(int subId, String channel)
    {
        subscriptions.put(subId, channel);
    }

    /**
     * @INV assums that bodyMessage in frame format is one-liner
     * @param message
     * @return new Message which its fields 
     */
    public Message extract(String msg) {

        /* 
        // for debugging
        // System.out.println("in protocol.extract(String msg) of client #" + connectionId + " printing msg:");
        // System.out.println(msg);
        */
        String body = "";

        // checking for bugs - if the first element isn't going to be a COMMAND
        while (msg.charAt(0) == '\n') msg = msg.substring(1);
        String[] splits = msg.split("\n");
        /*
        // printing for debugging purposes
        System.out.println(splits.length);
        System.out.println(Arrays.toString(splits));
        */

        String command = splits[0];
        int headerIndex = 1;
        HashMap<String,String> headers = new HashMap<>();
        while (headerIndex < splits.length && splits[headerIndex].trim().length() != 0)
        {
            //System.out.println("in extract, printing splits[headerIndex]:(" + splits[headerIndex] +")");
            String[] currHeader = splits[headerIndex].split(":");
            String header = currHeader[0];
            String value = currHeader[1];
            headers.put(header.trim(), value.trim());

            headerIndex++;
        }

        while (headerIndex < splits.length) 
        {
            body += splits[headerIndex] + "\n";
            headerIndex++;
        } 
        
        Message newM = new Message(command, headers, body);
        // newM.printMessage();
        return newM;
    }

    // ---------- executers for frames ----------

    /*
     * CONNECT
     * accept-version:1.2
     * host:stomp.cs.bgu.ac.il
     * login:meni
     * passcode:films
     * 
     * ^@
     */
    private void exectueCONNECT(Message m) {

        if (m != null) {

            // it's the first time the client is logged in
            if (checkCONNECT(m.headers)) {
            // if (!loggedIn && checkCONNECT(m.headers)) {
                loggedIn = true;
                
                // -- Authenticate information --
                String username = m.headers.get("login");
                String passcode = m.headers.get("passcode");
                // User exists
                if (connections.isUser(username)) { 
                    // Correct pascode
                    if (connections.isCorrectPassword(username, passcode)) {
                        // User is already connected
                        if (connections.isUserLoggedIn(username)) {
                            String message_header = "User is already logged in";
                            String explenation = "User is already logged in on this computer or on another one. Please log out before trying again";
                            ERROR(m, message_header, explenation);
                        }
                        
                        else { // User is currently not connected
                            currUser = connections.getUser(username);
                            // update ConnectionsImpl
                            connections.logUser(username, connectionId);
                            connections.addUserName(connectionId, username);
                            CONNECTED(m);
                        }
                    }
                    // Wrong password
                    else {
                        String message_header = "Wrong Passcode";
                        String explenation = "The passcode you entered doesn't match the username. Please try again to enter correct passcode";
                        ERROR(m, message_header, explenation);
                    }
                } else if (currUser != null && currUser.isLoggedIn())  { // User trying to log in doesn't exist yet in the User DB but this client is already connected to other user
                    ERROR(m, "this client is already connected to other user", "can't connect to other user while still connected to other user");
                } else { // User doesn't exist and it's the first CONNECT of this client - add it to the system
                    currUser = new UserData(passcode, connectionId);
                    connections.addUser(username, currUser);
                    // update ConnectionsImpl
                    connections.addUserName(connectionId, username);
                    CONNECTED(m);
                }              
            } 
            else {
                ERROR(m, "frame CONNECT is not in format", "please stick to the right format when sending frames");
                ; // not suppose to get here, login attemp when already loggen in will be handled by the client itself 
            }
        }   
    }

    /*
     * SUBSCRIBE
     * destination:/germany_spain
     * id:17
     * receipt:73
     * 
     * ^@
     */
    private void exectueSUBSCRIBE(Message m) {

        // NOTE: need to handle the case that game started and it's not possible to subscribe, ERROR should be sent

        if (m != null) {
            if (checkSUBSCRIBE(m.headers)) {
                String channel = m.headers.get("destination");
                String subscriptionId = m.headers.get("id");

                if (!connections.isChannel(channel)) { 
                    // System.out.println("adding new channel");
                    connections.createChannelAndSubscribe(channel, connectionId); 
                    currUser.addChannel(subscriptionId, channel); // add channel to user's subscription's list with it's own subscriptionId
                } else { // channel exists
                    if (!connections.isSubscribed(channel,connectionId)) {
                        if (!connections.isGameStarted(channel)) {
                            connections.addSubscriber(channel, connectionId);
                            currUser.addChannel(subscriptionId, channel); // add channel to user's subscription's list with it's own subscriptionId
                        } else { // game has already started thus can't SUBSCRIBE
                            ERROR(m, "game has already started, can't SUBSCRIBE to it in the middle of the game","please SUBSCRIBE before kick off");
                        }
                    } else
                        return; // already subscribed to this channel - do nothing
                }

                SUBSCRIBED(m);
            } else {
                ERROR(m, "frame SUBSCRIBE is not in format", "please stick to the right format when sending frames");
            }
        }
    }

    /*
     * UNSUBSCRIBE
     * id:17
     * receipt:82
     * 
     * ^@
     */
    private void executeUNSUBSCRIBE(Message m) {
        if (m != null){
            if (checkUNSUBSCRIBE(m.headers)) {
                String subscriptionId = m.headers.get("id");
                String channel = currUser.getChannel(subscriptionId);
                // Legal unsubscription
                if (channel != null && connections.isSubscribed(channel ,connectionId)){
                    connections.removeSubscriber(channel, connectionId);
                    currUser.removeChannel(subscriptionId);
                    
                    UNSUBSCRIBED(m, channel);
                }
                // Attempt to unsubscribe from an inexsistent channel of from unsubscribed channel
                else{
                    String message_header = "Illegal Attemp to unsubscribe";
                    String explenation = "Can't ubsubscribe from an inexsistent channel of from an unsubscribed one";
                    ERROR(m, message_header, explenation);
                }
            } else {
                ERROR(m, "frame UNSUBSCRIBE is not in format", "please stick to the right format when sending frames");
            }
        }
    }
    
    /*
     * SEND
     * 
     */
    private void executeSEND(Message m) {
        if (m != null) {
            if (checkSEND(m.headers)) {
                String channel = m.headers.get("destination");
                if (connections.isChannel(channel)) {
                    if (connections.isSubscribed(channel, connectionId)) {
                        SEND(m);
                    } else { // user (represented by this client) isn't subscribed to this channel
                        ERROR(m, "can't transmit a MESSAGE to a channel you'r not subscribed to","please SUBSCRIBE to a channel before writing to it");
                    }

                } else { // there is no such Channel
                    ERROR(m, "channel doesn't exist","can't transmit a MESSAGE to a non-existing channel");
                }  
            } else {
                ERROR(m, "frame SEND is not in format", "please stick to the right format when sending frames");
            }
        }
    }

    /*
     * DISCONNECT
     */
    private void exectueDISCONNECT(Message m){
        if (m != null) {
            if (checkDISCONNECT(m.headers)) {
                //user->subscriptionID->channel->remove
                for (Map.Entry<String,String> entry : currUser.getSubIdTochannels().entrySet()) {
                    connections.removeSubscriber(entry.getValue(), connectionId);
                }
                
                DISCONNECT(m); // send RECIEPT
                disconnect();
                terminate();
            } else {
                ERROR(m, "frame DISCONNECT is not in format", "please stick to the right format when sending frames");
            }
        }
    }

    // ---------- handlers of response frames ----------

    private void CONNECTED(Message m) {
        String msg = "CONNECTED\n" + "version:" + m.headers.get("accept-version") + "\n\n\u0000";
        // msg += "\n\n\u0000";
        connections.send(connectionId, msg);
        if(m.addReciept()) 
            RECEIPT(m.headers.get("receipt"));
    }

    private void DISCONNECT(Message m) {
        if(m.addReciept()) 
            RECEIPT(m.headers.get("receipt"));
    }

    private void SUBSCRIBED(Message m) {
        if(m.addReciept())  {
            // System.out.println("seding receipt");
            RECEIPT(m.headers.get("receipt"));
        }
    }

    private void UNSUBSCRIBED(Message m, String channel){
        String msg = "UNSUBSCRIBE\n" + "id:" + m.headers.get("id") + "\n" + "receipt-id:" + m.headers.get("receipt") + "\n\n\u0000";
        connections.send(channel, msg);    // send UNSUBSRIBE to the whole channel
        if(m.addReciept()) 
            RECEIPT(m.headers.get("receipt")); // send RECIEPT back to the client
    }

    private void SEND(Message m) {
        // TODO
        String channel = m.headers.get("destination");
        m.headers.put("subscription", "");
        m.headers.put("message-id", "");
        m.command = "MESSAGE";
        
        // sending MESSAGE frame to everyone in 'channel'
        connections.send(channel, m);

        // add reciept if needed
        if(m.addReciept()) 
            RECEIPT(m.headers.get("receipt"));
    }

    private void ERROR(Message malformed, String message_header, String error_explained) {
        String msg = "ERROR\n";
        // add receipt header if in malformed.headers
        if (malformed.addReciept())
            msg += "receipt-id:" + malformed.headers.get("receipt") + "\n";
        msg += "message: " +  message_header + "\n";
        msg += "The message:\n-----\n" ;
        msg += malformed.toString() + "\n-----\n";
        msg += error_explained + "\n\u0000";

        connections.send(connectionId, msg);

        // Handle connectionHandler's disconnection
        disconnect();
        terminate();
    }

    private void RECEIPT(String id) {
        String msg = "RECEIPT\nreceipt-id:" + id + "\n\n\u0000";
        // System.out.println("sending receipt from server to client #" + connectionId);
        connections.send(connectionId, msg);
    }


        // --------- checking headers of received frames ---------
        
    private boolean checkCONNECT(HashMap<String,String> headers) {
        // headers of CONNECT frame consists exactly the four headers and nothing else\more:
        return headers != null && !headers.isEmpty() && headers.containsKey("accept-version") && headers.containsKey("host") &&
                headers.containsKey("login") && headers.containsKey("passcode") &&
                (headers.size() == 4 || (headers.size() == 5 && headers.containsKey("receipt")));
    }

    private boolean checkSUBSCRIBE(HashMap<String,String> headers) {
        // headers of SUBSCRIBE frame consists exactly the three headers and nothing else\more:
        return headers != null && !headers.isEmpty() && headers.containsKey("destination") && headers.containsKey("id") &&
                (headers.size() == 2 || (headers.size() == 3 && headers.containsKey("receipt")));
    }

    private boolean checkUNSUBSCRIBE(HashMap<String,String> headers) {
        // headers of UNSUBSCRIBE frame consists exactly the two headers and nothing else\more:
        return headers != null && !headers.isEmpty() && headers.containsKey("id") && headers.containsKey("receipt") &&
                headers.size() == 2;
    }

    private boolean checkSEND(HashMap<String,String> headers) {
        //TODO
        return headers != null && !headers.isEmpty() && headers.containsKey("destination") && (headers.size() == 1 || headers.containsKey("receipt") &&
                headers.size() == 2); // include checking if "receipt" is needed
    }

    private boolean checkDISCONNECT(HashMap<String,String> headers) {
        // headers of DISCONNECT frame consists exactly the one header and nothing else\more:
        return headers != null && !headers.isEmpty() && headers.containsKey("receipt") && headers.size() == 1;
    }


}
