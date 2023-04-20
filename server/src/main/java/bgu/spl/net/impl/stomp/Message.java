package bgu.spl.net.impl.stomp;

import java.util.HashMap;
import java.util.Map;

public class Message {

    // fields
    protected String command;
    protected HashMap<String, String> headers;
    protected String body;

    // CTR
    public Message(String command, HashMap<String, String> headers, String body){
        this.command = command;
        this.headers = headers;
        this.body = body;
    }

    public String getCommand(){
        return command;
    }

    public boolean addReciept(){
        return headers.containsKey("receipt");
    }
    
    /**
     * @return Message toString without "\u0000"
     */
    public String toString() {
        String output = "";
        output += command + "\n";
        for (Map.Entry<String,String> entry : headers.entrySet())
            output += entry.getKey() + ":" + entry.getValue() + "\n";
        // output += "\n";
        if (body.length() != 0)
            output += body; // + "\n";
        return output;
    }

    // for debugging
    public void printMessage() {
        System.out.println("COMMAND : " + command);
        for (Map.Entry<String,String> entry : headers.entrySet())
            System.out.println("<header_name> : " + entry.getKey() + " <header_value> : " + entry.getValue());
        System.out.println("body : " + body);
    }
}
