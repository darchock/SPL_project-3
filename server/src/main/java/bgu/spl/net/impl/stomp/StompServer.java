package bgu.spl.net.impl.stomp;

import bgu.spl.net.srv.Server;

public class StompServer {

    public static void main(String[] args) {
        // TODO: implement this

        /*
         * mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.stomp.StompServer" -Dexec.args="7777 reactor"
         */

        if (args.length == 0) {
            args = new String[]{"7777","tpc"};
        }

        if (args.length < 2) {
            System.out.println("please if you're trying to provide args, provide it according to the format");
        }

        if (args[1].equals("tpc")) {
            try {
                    Server.threadPerClient(
                        Integer.parseUnsignedInt(args[0]), //port
                        () -> new StompMessagingProtocolImpl<String>(), //protocol factory
                        StompMessageEncoderDecoder::new //message encoder decoder factory
                    ).serve();
            } catch (Exception e) {}

        }
        else if (args[1].equals("reactor")) {

            try {
                    Server.reactor(
                    Runtime.getRuntime().availableProcessors(),
                    Integer.parseUnsignedInt(args[0]), //port
                    () -> new StompMessagingProtocolImpl<String>(), //protocol factory
                    StompMessageEncoderDecoder::new //message encoder decoder factory
                ).serve();
            } catch (Exception e) {}
        }
    
    }
}
