
#include <iostream>
#include <thread>
#include "../include/StompProtocol.h"
#include <stdlib.h>
#include <cstring>
#include "../include/Event.h"

#include <sstream>
#include <fstream>
#include <iostream>
#include <vector>
#include <string>

using std::string;
using std::cin;
using std::vector;

int main(int argc, char *argv[]) {
	// TODO: implement the STOMP client
	
	vector<string> strings;
	while (1) { // while loop - waiting for first user to login
		// allocate buffer
        const short bufsize = 1024;
        char buf[bufsize];
            
        cin.getline(buf, bufsize);  // getting input from user (from the termianl) as byte[]
        string line(buf); 			// truning byte[] to string

		std::istringstream iss;
		iss.str(line);
		for (string str ; iss >> str ;)
		{
			strings.push_back(str);
		}

		if (strings.at(0) == "login") {
			break;
		} else {
			std::cout << "\033[1;32mplease login first, before trying to " << strings.at(0) << "\033[0m" <<  std::endl;
			strings.clear();
		}
	}

	// extract host and port in order to connect for server
	string host = strings.at(1).substr(0,strings.at(1).find_first_of(':'));
	short port = (short) stoi(strings.at(1).substr(strings.at(1).find_first_of(':')+1));

	// intializing protocol in which we initialize connection handler with the given host:port
	StompProtocol protocol(host,port);

	if (!protocol.handler.connect()) {
		std::cerr << "\033[1;32mCould not connect to Server - " << host << ":" << port << "\033[0m" << std::endl;
		return 1;
	}

	std::thread t1(&StompProtocol::runForSocket, &protocol);
	protocol.handler.sendFrameAscii("CONNECT\naccept-version:1.2\nhost:stomp.cs.bgu.ac.il\nlogin:" + strings.at(2) + "\npasscode:" + strings.at(3) + "\n\n\0",'\0'); // send CONNECT frame

	while (1) {
		if (protocol.isConnected()) {
			protocol.setUser(strings.at(2));
			break;
		} else
			continue;
	}

	// std::cout << "\033[1;32mConnected in class.stompClient - first connection of this client to server !\033[0m" << std::endl; // from now on the client is connected to the server

	std::thread t2(&StompProtocol::runForKeyboard, &protocol);

	t1.join();
	t2.join();
	

	return 0;
}






