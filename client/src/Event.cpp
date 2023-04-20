#include "../include/Event.h"
#include "../include/json.hpp"
#include <iostream>
#include <fstream>
#include <string>
#include <map>
#include <vector>
#include <sstream>
using json = nlohmann::json;

Event::Event(std::string team_a_name, std::string team_b_name, std::string name, int time,
             std::map<std::string, std::string> game_updates, std::map<std::string, std::string> team_a_updates,
             std::map<std::string, std::string> team_b_updates, std::string discription, std::string username)
    : team_a_name(team_a_name), team_b_name(team_b_name), name(name),
      time(time), game_updates(game_updates), team_a_updates(team_a_updates),
      team_b_updates(team_b_updates), description(discription), username(username)
{
}

Event::~Event()
{
}

Event::Event() : team_a_name(""), team_b_name(""), name(""), time(0), game_updates{}, team_a_updates{},
                 team_b_updates{}, description(""), username("")
{
}

Event::Event(std::string team_a, std::string team_b) : team_a_name(team_a), team_b_name(team_b), name(""), time(0), game_updates{}, team_a_updates{},
                 team_b_updates{}, description(""), username("")
{
}

const std::string &Event::get_team_a_name() const
{
    return this->team_a_name;
}

const std::string &Event::get_team_b_name() const
{
    return this->team_b_name;
}

const std::string &Event::get_name() const
{
    return this->name;
}

int Event::get_time() const
{
    return this->time;
}

const std::map<std::string, std::string> &Event::get_game_updates() const
{
    return this->game_updates;
}

const std::map<std::string, std::string> &Event::get_team_a_updates() const
{
    return this->team_a_updates;
}

const std::map<std::string, std::string> &Event::get_team_b_updates() const
{
    return this->team_b_updates;
}

const std::string &Event::get_discription() const
{
    return this->description;
}

// added methods
const std::string &Event::get_username() const
{
    return this->username;
}

void Event::update(Event &toAdd) {
    for (const auto& pair : toAdd.get_game_updates())
    {
        this->game_updates[pair.first] = pair.second;
    }
    for (const auto& pair : toAdd.get_team_a_updates())
    {
        this->team_a_updates[pair.first] = pair.second;
    }
    for (const auto& pair : toAdd.get_team_b_updates())
    {
        this->team_b_updates[pair.first] = pair.second;
    }
    this->description += std::to_string(toAdd.get_time()) + " - " + toAdd.get_name() + ":" + "\n\n" + toAdd.get_discription() + "\n\n\n";
}

Event::Event(const std::string &frame_body) : team_a_name(""), team_b_name(""), name(""), time(0), game_updates(), team_a_updates(), team_b_updates(), description(""), username("")
{
    std::istringstream iss(frame_body);
    std::string line;

    while (std::getline(iss, line, '\n'))
    {
        if (line == "MESSAGE" || line == "") continue;
        size_t pos = line.find_first_of(':');
        if (pos != std::string::npos)
        {
            std::string left = line.substr(0,pos);
            if (left == "subscription" || left == "message-id"|| left == "destination") continue;
            std::string val;
            if (left == "user")
            {
                val = line.substr(pos+2);
                this->username = val;
            }
            if (left == "team a")
            {
                val = line.substr(pos+2);
                this->team_a_name = val;
            }
            else if (left == "team b")
            {
                val = line.substr(pos+2);
                this->team_b_name = val;
            }
            else if (left == "event name")
            {
                val = line.substr(pos+2);
                this->name = val;
            }
            else if (left == "time")
            {
                val = line.substr(pos+2);
                this->time = stoi(val);
            }
            else if (left == "general game updates")
            {
                while (std::getline(iss, line, '\n') && line.substr(0,line.find_first_of(':')) != "team a updates")
                {
                    pos = line.find_first_of(':');
                    if (pos != std::string::npos)
                    {
                        left = line.substr(0,pos);
                        size_t pos_t = left.find_first_of('\t');
                        if (pos_t != std::string::npos)
                        {
                            left = left.substr(pos_t+1,pos);
                        }
                        val = line.substr(pos+2);
                        this->game_updates.emplace(left,val);
                    }
                }

                if (line.substr(0,line.find_first_of(':')) == "team a updates")
                {
                    while (std::getline(iss, line, '\n') && line.substr(0,line.find_first_of(':')) != "team b updates")
                    {
                        pos = line.find_first_of(':');
                        if (pos != std::string::npos)
                        {
                            left = line.substr(0,pos);
                            size_t pos_t = left.find_first_of('\t');
                            if (pos_t != std::string::npos)
                            {
                                left = left.substr(pos_t+1,pos);
                            }
                            val = line.substr(pos+2);
                            this->team_a_updates.emplace(left,val);
                        }
                    }
                }
                if (line.substr(0,line.find_first_of(':')) == "team b updates")
                {
                    while (std::getline(iss, line, '\n') && line.substr(0,line.find_first_of(':')) != "description")
                    {
                        pos = line.find_first_of(':');
                        if (pos != std::string::npos)
                        {
                            left = line.substr(0,pos);
                            size_t pos_t = left.find_first_of('\t');
                            if (pos_t != std::string::npos)
                            {
                                left = left.substr(pos_t+1,pos);
                            }
                            val = line.substr(pos+2);
                            this->team_b_updates.emplace(left,val);
                        }
                    }
                }
                if (line.substr(0,line.find_first_of(':')) == "description")
                {
                while (std::getline(iss, line, '\n'))
                {
                    this->description += line;
                } 
                }
            } else {
                ; // std::cout << "not supposed to be here !!!" << std::endl;
            }

        }
    }

}

names_and_events parseEventsFile(std::string json_path)
{
    std::ifstream f(json_path);
    json data = json::parse(f);

    std::string team_a_name = data["team a"];
    std::string team_b_name = data["team b"];

    // run over all the events and convert them to Event objects
    std::vector<Event> events;
    for (auto &event : data["events"])
    {
        std::string name = event["event name"];
        int time = event["time"];
        std::string description = event["description"];
        std::map<std::string, std::string> game_updates;
        std::map<std::string, std::string> team_a_updates;
        std::map<std::string, std::string> team_b_updates;
        for (auto &update : event["general game updates"].items())
        {
            if (update.value().is_string())
                game_updates[update.key()] = update.value();
            else
                game_updates[update.key()] = update.value().dump();
        }

        for (auto &update : event["team a updates"].items())
        {
            if (update.value().is_string())
                team_a_updates[update.key()] = update.value();
            else
                team_a_updates[update.key()] = update.value().dump();
        }

        for (auto &update : event["team b updates"].items())
        {
            if (update.value().is_string())
                team_b_updates[update.key()] = update.value();
            else
                team_b_updates[update.key()] = update.value().dump();
        }
        
        events.push_back(Event(team_a_name, team_b_name, name, time, game_updates, team_a_updates, team_b_updates, description, "event without a user"));
    }
    names_and_events events_and_names{team_a_name, team_b_name, events};

    return events_and_names;
}