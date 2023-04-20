#pragma once

#include "../include/ConnectionHandler.h"
#include <unordered_map>
#include <mutex>
#include <condition_variable>
#include "Event.h"

using std::string;
using std::unordered_map;

// TODO: implement the STOMP protocol
class StompProtocol
{
private:
    // private fields
    int subscriber_id_generator;
    int receipt_id_generator;
    unordered_map<string,string> channelToSubId;
    string username;
    unordered_map<string, unordered_map<string, Event>> channel_to_username_to_event;


public:
    StompProtocol(std::string _host, short _port); // protocol CTR
    virtual ~StompProtocol(); // protocol decstructor

    // public fields
    ConnectionHandler handler;

    // public methods
    bool isConnected();
    void runForKeyboard();
    void runForSocket();
    void addChannel(string channel, string subId);
    void removeChannel(string channel);
    void setUser(string _username);
    void update_summary(string eventName, string username, Event toAdd);
    void reportEvent(Event &event, string &game_name);
    
};
