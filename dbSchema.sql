drop table if not exists config;
create table config (
    id int not null auto_increment primary key,
    customConfigID default 1;
    sephiadir tinytext,
    datafilename tinytext,
    usersfilename tinytext,
    logdir tinytext,

drop table if not exists customconfig;
create table customconfig (
    id int not null auto_increment primary key,
    hello tinytext;
    censor boolean;
    greeting tinytext;
    helloreplies tinytext;
    
drop table if not exists server;
create table server (
    id int not null auto_increment primary key,
    host tinytext not null,
    port int,
    nick tinytext not null);
    
drop table if not exists channel;
create table channel (
    id int not null auto_increment primary key,
    serverID int not null,
    name tinytext not null,
    greeting tinytext);

drop table if not exists blacklist;
create table blacklist (
    id int not null auto_increment primary key,
    regex tinytext not null);

drop table if not exists group;
create table group (
    id int not null auto_increment primary key,
    name tinytext not null);

drop table if not exists user;
create table groupPermission (
    id int not null auto_increment primary key,
    groupID int not null,
    varchar(32) name not null);
        
drop table if not exists host;
create table host (
    id int not null auto_increment primary key,
    userID int not null,
    hostname tinytext not null);

drop table if not exists message;
create table message (
    id int not null auto_increment primary key,
    target tinytext not null,
    sender tinytext not null,
    message tinytext not null,
    timeSent long not null,
    timeToArrive long not null,
    notified boolean default 0);

drop table if not exists paste;
create table paste (
    id int not null auto_increment primary key,
    channelID int,
    nick tinytext,
    summary tinytext,
    contents mediumtext,
    submittime datetime,
    IP tinytext);

drop table if not exists alert;
create table alert (
    id int not null auto_increment primary key,
    channelID int,
    message tinytext,
    sent dateTime not null);
