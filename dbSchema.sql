drop table if exists sephia_config;
create table sephia_config (
    id int not null auto_increment primary key,
    customConfigID int default 1,
    sephiadir tinytext,
    datafilename tinytext,
    usersfilename tinytext,
    logdir tinytext);

drop table if exists sephia_customConfig;
create table sephia_customConfig (
    id int not null auto_increment primary key,
    hello tinytext,
    censor bit,
    greeting tinytext,
    helloreplies tinytext);
    
drop table if exists sephia_server;
create table sephia_server (
    id int not null auto_increment primary key,
    host tinytext not null,
    port int,
    nick tinytext not null,
    customConfigID int);
    
drop table if exists sephia_channel;
create table sephia_channel (
    id int not null auto_increment primary key,
    serverID int not null,
    name tinytext not null,
    greeting tinytext,
    customConfigID int);

drop table if exists sephia_blacklist;
create table sephia_blacklist (
    id int not null auto_increment primary key,
    channelID int,
    serverID int,
    nickRegex tinytext not null);

drop table if exists sephia_group;
create table sephia_group (
    id int not null auto_increment primary key,
    name tinytext not null);

drop table if exists sephia_groupPermission;
create table sephia_groupPermission (
    id int not null auto_increment primary key,
    groupID int not null,
    name varchar(32) not null);

drop table if exists sephia_groupMembership;
create table sephia_groupMembership (
    id int not null auto_increment primary key,
    groupID int not null,
    userID int not null);

drop table if exists sephia_user;
create table sephia_user (
    id int not null auto_increment primary key,
    nick tinytext not null,
    password tinytext,
    description tinytext);
    
drop table if exists sephia_alias;
create table sephia_alias (
    id int not null auto_increment primary key,
    alias tinytext not null,
    userID int not null);
        
drop table if exists sephia_host;
create table sephia_host (
    id int not null auto_increment primary key,
    userID int not null,
    hostname tinytext not null);

drop table if exists sephia_message;
create table sephia_message (
    id int not null auto_increment primary key,
    target tinytext not null,
    sender tinytext not null,
    message tinytext not null,
    timeSent int not null,
    timeToArrive int not null,
    notified bit default 0);

drop table if exists sephia_paste;
create table sephia_paste (
    id int not null auto_increment primary key,
    channelID int,
    nick tinytext,
    summary tinytext,
    contents mediumtext,
    submittime datetime,
    IP tinytext);

drop table if exists sephia_alert;
create table sephia_alert (
    id int not null auto_increment primary key,
    channelID int,
    message tinytext,
    sent dateTime not null);
