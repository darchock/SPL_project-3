package bgu.spl.net.impl.echo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class EchoClient {

    public static void main(String[] args) throws IOException {

        if (args.length == 0) {
            args = new String[]{"localhost", "hello"};
        }

        if (args.length < 2) {
            System.out.println("you must supply two arguments: host, message");
            System.exit(1);
        }

        //BufferedReader and BufferedWriter automatically using UTF-8 encoding
        try (Socket sock = new Socket(args[0], 7777);
                BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream()))) {

            // System.out.println("sending message to server");
            // out.write(args[1]);
            // out.newLine();
            // out.flush();

            // System.out.println("awaiting response");
            // String line = in.readLine();
            // System.out.println("message from server: " + line);

            String[] msgs = new String[5];

            msgs[0] = "CONNECT\naccept-version:1.2 \nhost:stomp.cs.bgu.ac.il \nlogin:meni \npasscode:films \n\n\u0000";
            msgs[1] = "SUBSCRIBE\ndestination:/japan_germany \nid:17 \nreceipt:73 \n\n\u0000";
            msgs[2] = "SEND\ndestination:/japan_germany \n\nExciting game we have tonight\nhere at the iconic stadium of Istanbul\n\u0000";
            msgs[3] = "UNSUBSCRIBE\nid:17 \nreceipt:82 \n\n\u0000";
            msgs[4] = "DISCONNECT\nreceipt:113\n\n\u0000";

            /*  
                ----- tests for causing ERROR through CONNECT frame -----

                test1 - inserting wrong passcode    
                    msgs[1] = "CONNECT\naccept-version:1.2 \nhost:stomp.cs.bgu.ac.il \nlogin:meni \npasscode:incorrect \n\n\u0000";
                test2 - trying to connect through different user whilst there is already a connected user to th e client
                    msgs[1] = "CONNECT\naccept-version:1.2 \nhost:stomp.cs.bgu.ac.il \nlogin:marina \npasscode:spl \n\n\u0000";
            */

            for (int i = 0; i < msgs.length; ++i) {
                System.out.println("sending message to server");
                System.out.println(msgs[i]);
                out.write(msgs[i]);
                out.newLine();
                out.flush();
            }
            


            System.out.println("awaiting responses");
            System.out.println("message from server:");
            String line = "";
            while (line != null) {
                line = in.readLine();
                // if (line == null) {
                //     System.out.println("^@");
                //     break;
                // } else
                if (line == null)
                    break;
                System.out.println(line);
            }
            


        }
    }
}
