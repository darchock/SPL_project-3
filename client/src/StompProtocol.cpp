#include "../include/StompProtocol.h"
#include "../include/Event.h"
#include "algorithm"
#include <map>
#include <fstream>

using std::string;
using std::cin;
using std::cout;
using std::cerr;
using std::vector;
using std::map;

// protocol CTR
StompProtocol::StompProtocol(string _host, short _port) : subscriber_id_generator(0), receipt_id_generator(0),  channelToSubId{}, username("") , channel_to_username_to_event{} ,handler(_host, _port) {}

StompProtocol::~StompProtocol()
{
}

// declare on a conditional global variable
std::condition_variable waitForCONNECTED;
bool loggedIn = true;
bool connected = false;
bool shut = false;
std::mutex connected_lock;
int receipt_unsubscribe = -1;


// protocol methods
void StompProtocol::runForKeyboard() {

    while (1) { // client lives forever

        // allocate buffer
        const short bufsize = 1024;
        char buf[bufsize];
            
        cin.getline(buf, bufsize); // getting input from user (from the termianl) as byte[]
        string line(buf); // truning byte[] to string
        
        vector<string> strings;
        std::istringstream iss;
		iss.str(line);
		for (string str ; iss >> str ;){
			strings.push_back(str);
		}
		string command = strings.at(0);
        
        // new login flow
        if (!loggedIn){
            try{
                if (command == "login"){
                    // reconnect handler
                    if (!handler.connect()) {
                        // std::cerr << "\033[1;32mCould not connect to Server\033[0m" << std::endl;
                        std::cerr << "Could not connect to Server" << std::endl;
                    }
                    // notify socket thread
                    loggedIn = true;
                    waitForCONNECTED.notify_all();
                    
                    // send "CONNECT" to server
                    string _username = strings.at(2);
                    string passcode = strings.at(3);
                    string msg = "CONNECT\naccept-version:1.2\nhost:stomp.cs.bgu.ac.il\nlogin:" + _username + "\npasscode:" + passcode + "\n\n\0";
                    // std::cout << "\033[1;31m" << msg << "\033[0m" << std::endl;
                    // std::cout <<  msg << std::endl;
                    handler.sendFrameAscii(msg,'\0'); // send it to the connection handler to be handled to a frame and to be sent from there

                    // wait for CONNECTED frame to be proccessed by StompProtocol::runForSocket()
                    std::unique_lock<std::mutex> lk(connected_lock);
                    waitForCONNECTED.wait(lk, [] {return connected;});
                    lk.unlock();

                    shut = false;
                    this->username = _username;

                }
                else{
                    // std::cerr << "\033[1;32mplease login first, instead you declared command: " << command << "as initial command\033[0m" << std::endl << std::endl;
                    std::cerr << "please login first, instead you declared command: " << command << "as initial command" << std::endl << std::endl;
                }
            } catch (const std::exception& e) {
                std::cerr << "ERROR in login: " << e.what() << std::endl << std::endl;
            }
        }
        else{
            if (command == "login"){
                // std::cerr << "\033[1;32mThe client is already logged in, log out before trying again\033[0m" << std::endl;
                std::cerr << "The client is already logged in, log out before trying again" << std::endl;
            } 
            else if (command == "join") { // send SUBSCRIBE frame
                try{
                    string channel = strings.at(1); // extract data
                    std::transform(channel.begin(), channel.end(), channel.begin(), ::tolower);
                    string subId = std::to_string(subscriber_id_generator);
                    string receiptId = std::to_string(receipt_id_generator);
                    addChannel(channel, subId); // update map of channels -> subId
                    
                    string msg = "SUBSCRIBE\ndestination:" + channel + "\nid:" + subId + "\nreceipt:" + receiptId + "\n\n\0"; // msg to be converted into frame
                    subscriber_id_generator += 1;
                    receipt_id_generator += 1;

                    // std::cout << "\033[1;31m" << msg << "\033[0m" << std::endl;
                    // std::cout <<  msg << std::endl;
                    handler.sendFrameAscii(msg,'\0'); // send it to the connection handler to be handled to a frame and to be sent from there
                    std::cout << "Joined channel " << channel << std::endl;
                } catch (const std::exception& e) {
                std::cerr << "ERROR in join: " << e.what() << std::endl << std::endl;
                }
            } 
            else if (command == "exit") { // send UBSUBSCRIBE frame
                try{
                    string channel = strings.at(1); // extract data
                    string subId = channelToSubId.at(channel); // get the subscription id connected to the game
                    string receiptId = std::to_string(receipt_id_generator);
                    receipt_unsubscribe = receipt_id_generator;
                    receipt_id_generator += 1;

                    string msg = "UNSUBSCRIBE\nid:" + subId + "\nreceipt:" + receiptId + "\n\n\0";

                    // std::cout << "\033[1;31m" << msg << "\033[0m" << std::endl;
                    // std::cout << msg << std::endl;
                    handler.sendFrameAscii(msg,'\0'); // send it to the connection handler to be handled to a frame and to be sent from there

                } catch (const std::exception& e) {
                    std::cerr << "ERROR in exit: " << e.what() << std::endl << std::endl;
                }
            }
            else if (command == "report") { // send SEND frame
                try{
                    string file = strings.at(1);
                    names_and_events nae = parseEventsFile(file); //?
                    string gameName = nae.team_a_name + "_" + nae.team_b_name;
                    std::transform(gameName.begin(), gameName.end(), gameName.begin(), ::tolower);

                    for (Event event : nae.events)
                    {
                        reportEvent(event, gameName);
                    }
                } catch (const std::exception& e) {
                    std::cerr << "ERROR in report: " << e.what() << std::endl << std::endl;
                }
            } 
            else if (command == "summary") {
                try{
                    string gameName = strings.at(1);
                    string username = strings.at(2);
                    string fileToWriteOn = strings.at(3);
                    if (channel_to_username_to_event[gameName].count(username) > 0)
                    {
                        Event summary = channel_to_username_to_event[gameName][username];
                        // write summary to fileToWriteOn
                        string textToFile = summary.get_team_a_name() + " vs " + summary.get_team_b_name() + "\n";
                        textToFile += "Game stats:\nGeneral stats:\n";
                        for (const auto& pair : summary.get_game_updates())
                        {
                            textToFile += pair.first + ": " + pair.second + "\n";
                        }
                        textToFile += summary.get_team_a_name() + " stats:\n";
                        for (const auto& pair : summary.get_team_a_updates())
                        {
                            textToFile += pair.first + ": " + pair.second + "\n";
                        }
                        textToFile += summary.get_team_b_name() + " stats:\n";
                        for (const auto& pair : summary.get_team_b_updates())
                        {
                            textToFile += pair.first + ": " + pair.second + "\n";
                        }
                        textToFile += "Game event report:\n";
                        textToFile += summary.get_discription();

                        std::ofstream outfile;
                        outfile.open(fileToWriteOn,std::ios::out);

                        outfile << textToFile;

                        outfile.close();
                    }
                    else
                    {
                        // std::cerr << "\033[1;32mReports from this user do not exist\033[0m" << std::endl;
                        std::cerr << "Reports from this user do not exist" << std::endl;
                    }
                } catch (const std::exception& e) {
                    std::cerr << "ERROR in summary: " << e.what() << std::endl << std::endl;
                }
            } 
            else if (command == "logout") {
                try{
                    string receiptId = std::to_string(receipt_id_generator);
                    string msg = "DISCONNECT\nreceipt:" + receiptId + "\n\n\0";
                    
                    // disconnect user
                    shut = true;

                    handler.sendFrameAscii(msg,'\0');
                } catch (const std::exception& e) {
                    std::cerr << "ERROR in summary: " << e.what() << std::endl << std::endl;
                }
            } 
            else { // an unknown command - error
                //std::cout << "\033[1;32mSorry, " << command << " is an unknown commad\033[0m" << std::endl;
                std::cout << "Sorry, " << command << " is an unknown commad" << std::endl;
            }
        }
    }
}

void StompProtocol::runForSocket() {
    while (1) {
        try{
            string frame_msg;
            handler.getFrameAscii(frame_msg,'\0'); // now in 'frame_msg' we have a string in the format of a frame
        
            while (frame_msg != "") { // we got an actual frame from the server
                // std::cout << "\033[34mmessage send from server and proccessed by client: \n" << frame_msg << "\033[0m" << std::endl; // for now we're just printing the frame we got from the server

                vector<string> strings;
                std::istringstream iss;
                iss.str(frame_msg);
                for (string str ; iss >> str ;)
                {
                    strings.push_back(str);
                }
                string command = strings.at(0);

                if (command == "CONNECTED") {
                    try{
                        connected = true;
                        waitForCONNECTED.notify_all();
                        // std::cout << "\033[1;32m ----- Login Successful ----- \033[0m" << std::endl;
                        std::cout << "----- Login Successful -----" << std::endl;
                    } catch (const std::exception& e) {
                        std::cerr << "ERROR in CONNECTED: " << e.what() << std::endl << std::endl;
                    }
                } else if (command == "MESSAGE") {
                    try{
                        std::cout << frame_msg << std::endl;
                        Event recieved = Event(frame_msg);
                        string gameName = recieved.get_team_a_name() + "_" + recieved.get_team_b_name();
                        string userName = recieved.get_username();
                        update_summary(gameName, userName, recieved);
                    } catch (const std::exception& e) {
                        std::cerr << "ERROR in MESSAGE: " << e.what() << std::endl << std::endl;
                    }
                } else if (command == "RECEIPT") {
                    try{
                        if (shut)
                        {
                            frame_msg = "";
                            break;
                        }
                        if (receipt_unsubscribe == stoi(strings.at(1).substr(strings.at(1).find_first_of(':')+1))) // it's a RECEIPT for DISCONNECT frame sent by keyboard
                        {
                            auto it = channelToSubId.find((strings.at(1).substr(strings.at(1).find_first_of(':')+1)));
                            string channel;
                            if (it != channelToSubId.end()) channel = it->first;
                            // std::cout << "\033[1;32m ----- Exited channel " << channel << "----- \033[0m" << std::endl;
                            std::cout << "----- Exited channel " << channel << " -----" << std::endl;
                            removeChannel(channel);
                            receipt_unsubscribe = -1;
                        }
                        
                    } catch (const std::exception& e) {
                        std::cerr << "ERROR in RECEIPT: " << e.what() << std::endl << std::endl;
                    }
                } else if(command == "ERROR"){
                    try{
                        // print body frame
                        // std::cout << "\033[1;31m" << frame_msg << "\033[0m" << std::endl;
                        // std::cout << "\033[1;32myou got disconnected please login again\033[0m" << std::endl;
                        std::cout << frame_msg << std::endl;
                        std::cout << "you got disconnected please login again" << std::endl;
                        // disconnect user
                        shut = true;
                    } catch (const std::exception& e) {
                        std::cerr << "ERROR in ERROR: " << e.what() << std::endl << std::endl;
                    }
                } else if (command == "UNSUBSCRIBE") {
                    // std::cout << "\033[1;32m ----- someone has Unsubscribed from a channel you belong to ----- \n" << frame_msg << "\033[0m" << std::endl;
                    std::cout << "----- someone has Unsubscribed from a channel you belong to ----- \n" << frame_msg << std::endl;
                } else { // an unknown frame command - error
                    // std::cout << "\033[1;32mSorry, " << command << " is an unknown commad\033[0m" << std::endl;
                    std::cout << "Sorry, " << command << " is an unknown commad" << std::endl;
                }
                frame_msg = "";
            } // end of while

            if (shut) {
                handler.close();
                loggedIn = false;
                username = "";
                channelToSubId.clear();
                channel_to_username_to_event.clear();
                subscriber_id_generator = 0;
                receipt_id_generator = 0;
                
                // lock until login
                std::unique_lock<std::mutex> lk(connected_lock);
                waitForCONNECTED.wait(lk, [] {return loggedIn;});
                lk.unlock();
            }
        } catch (const std::exception& e) {
            std::cerr << "ERROR in runForSocket: " << e.what() << std::endl << std::endl;
        }
    }
}

void StompProtocol::addChannel(string channel, string subId)
{
    channelToSubId[channel] = subId;
}

void StompProtocol::removeChannel(string channel) 
{
    channelToSubId.erase(channel);
}

bool StompProtocol::isConnected()
{
    return connected;
}

void StompProtocol::setUser(string _username)
{
    this->username = _username;
}

void StompProtocol::update_summary(string gameName, string username, Event toAdd)
{

    if (channel_to_username_to_event.count(gameName) > 0)
    {
        if (channel_to_username_to_event[gameName].count(username) > 0)
        {
            channel_to_username_to_event[gameName][username].update(toAdd);
        }
        else
        {
            Event empty(toAdd.get_team_a_name(),toAdd.get_team_b_name());
            empty.update(toAdd);
            channel_to_username_to_event[gameName].emplace(username, empty);
        }    
    }
    else
    {
        Event empty(toAdd.get_team_a_name(),toAdd.get_team_b_name());
        empty.update(toAdd);
        unordered_map<string,Event> userSummary;
        userSummary[username] = empty;
        channel_to_username_to_event.emplace(gameName,userSummary);
    }

}

void StompProtocol::reportEvent(Event &event, string &game_name)
{
    string msg = "SEND\ndestination:" + game_name + "\n\nuser: " + this->username + "\n";
    string team_a = event.get_team_a_name();
    string team_b = event.get_team_b_name();
    std::transform(team_a.begin(), team_a.end(), team_a.begin(), ::tolower);
    std::transform(team_b.begin(), team_b.end(), team_b.begin(), ::tolower);
    msg += "team a: " + team_a + "\n";
    msg += "team b: " + team_b + "\n";
    msg += "event name: " + event.get_name() + "\n";
    msg += "time: " + std::to_string(event.get_time()) + "\n";
    msg += "general game updates: \n";
    for (const auto &pair : event.get_game_updates()) {
        msg += "\t" + pair.first + ": " + pair.second + "\n";
    }
    msg += "team a updates: \n";
    for (const auto &pair : event.get_team_a_updates()) {
        msg += "\t" + pair.first + ": " + pair.second + "\n";
    }
    msg += "team b updates: \n";
    for (const auto &pair : event.get_team_b_updates()) {
        msg += "\t" + pair.first + ": " + pair.second + "\n";
    }
    msg += "description:\n" + event.get_discription() + "\n\0";
    // end of formating SEND frame

    this->handler.sendFrameAscii(msg,'\0'); // send it to the connection handler to be handled to a frame and to be sent from there
}