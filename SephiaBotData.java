/* SephiaBot
 * Copyright (C) 2005 Jorge Rodriguez and Ed Anderson
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA
 */

/*
 * The purpose of this class is to keep track of all of SephiaBot's data, abstracting it away from the main part of the program.
 * This keeps it nice and neat and what have you.
 */

import java.io.*;
import java.util.*;
import java.util.regex.*;

class SephiaBotData {

	private String spell;
	private String hellos[];
	private String helloreplies[];
	private String logdir;
	private String sephiadir; //Location of sephiabot. Quote, data files here
	private String blacklist[];
	private String config;

	private ServerConfig servers[];
	private GlobalConfig globals;

	private XMLParser parser;

	private long nextMessage;
	private Message firstMessage = null;

	private User users[];

	private String syslogBuffer;
	private BufferedWriter syslog;

	private String dataFileName;
	private String announceFileName;
	private String usersFileName;
	
	public static final int MESSAGE_STALE_DUR = 10 * 60 * 1000; //10 min

	private SephiaBotData() { }
	
	SephiaBotData(String config) {
		this.globals = new GlobalConfig();
		this.config = config;
		this.parser = new XMLParser(this);
		setDefaults();
	}

	//Performs a case-insensitive regexp match of string against pattern.
	public static String iregexFind(String pattern, String string) {
		Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(string);
		if (m.find())
			return m.group();
		else
			return null;
	}
	
	//Performs a case-insensitive regexp match of string against pattern.
	public static boolean iregex(String pattern, String string) {
		Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(string);
		return m.find();
	}
	
	//Performs a case-insensitive string comparison.
	public static boolean iequals(String str1, String str2) {
		if (str1 == null && str2 == null) {
			return true;
		} else if (str1 == null || str2 == null) {
			return false;
		} else {
			return str1.toLowerCase().equals(str2.toLowerCase());
		}
	}

	private void setDefaults() {
		//Set Defaults
		globals.greeting = "Hello, I am %n, the channel bot. You all suck.";
		globals.censor = new Boolean(true);
		this.servers = new ServerConfig[0];
		this.hellos = new String[] {"hi[hy2]?", "yo[^u]", "hey", "greetings\\b", "kon{1,2}ichiwa", "hola\\b", "sup", "morning\\b", "(y\\s)?h[aeu]l{1,2}o"};
		this.helloreplies = new String[] {"Yo."};
		this.logdir = "/var/log/sephiabot"; //not a very good default unless documented, incase we actually released this someday (haha)
		this.sephiadir = "/var/lib/sephiabot"; //ditto
		this.dataFileName = "sephiabot.dat";
		this.announceFileName = "announce.dat";
		this.usersFileName = "users.xml";
		this.blacklist = new String[] {};
		this.nextMessage = 0;
	}

	void checkAnnouncements(IRCConnection[] connections) {
		String filename = announceFileName;
		BufferedReader announceReader;
		LinkedList recipientList = new LinkedList();
		LinkedList messageList = new LinkedList();

		try {
			announceReader = new BufferedReader(new FileReader(new File(sephiadir, filename)));
		} catch (IOException ioe) {
			return;	//Assume no announce file has been created if it doesn't exist
		}
		try {
			while (announceReader.ready()) {
				String line = announceReader.readLine();
				StringTokenizer tok = new StringTokenizer(line, " ");
				if (!tok.hasMoreElements())
					continue;
				String channel = tok.nextToken().trim();
				if (!iregex("^#", channel)) {
					logerror("Invalid target channel: " + channel);
					continue;
				}
				if (!tok.hasMoreElements()) {
					logerror("Announcement to " + channel + " in " + filename +
							"had an blank message.");
					continue;
				}
				recipientList.add(channel);
				messageList.add(tok.nextToken("").trim());
			}
			announceReader.close();
		} catch (IOException ioe) {
			logerror("Couldn't read announce file " + filename + ".");
		}
		try {
			// Clear the file
			new FileWriter(new File(sephiadir, filename)).close();
		} catch (IOException ioe) {
			logerror("Couldn't read announce file " + filename + ".");
		}

		while (recipientList.size() > 0 && messageList.size() > 0) {
			String targetChannel = (String) recipientList.removeFirst();
			String message = (String) messageList.removeFirst();
			IRCConnection con = null;
			for (int i = 0; i < connections.length; i++) {
				if (connections[i].getServer().findChannel(targetChannel) != null) {
					con = connections[i];
					break;
				}
			}
			if (con != null) {
				if (message.length() > 512)
					message = message.substring(0,512);
				con.getIRCIO().privmsg(targetChannel, message);
			} else {
				logerror("I'm not in the channel " + targetChannel);
			}
		}
	}
	
	void loadData() {
		String filename = dataFileName;
		log("Loading " + filename);

		BufferedReader dataFileReader;

		try {
			dataFileReader = new BufferedReader(new FileReader(new File(sephiadir, filename)));
		} catch (IOException ioe) {
			return;  //Assume no datafile has been created if it doesn't exist
		}

		try {
			int messagesThrownOut = 0;
			int messagesLoaded = 0;
			int hostsLoadedTotal = 0;
			int hostsThrownOut = 0;
			int awayMsgLoaded = 0;
			int awayMsgThrownOut = 0;
			firstMessage = null;		//Keep messages already in memory from persisting.
			//Log all users out and use only persistent login information.
			for (int i = 0; i < users.length; i++)
				logout(users[i], -1);
lineLoop:
			while (dataFileReader.ready()) {
				String line = dataFileReader.readLine();
				StringTokenizer tok = new StringTokenizer(line, " ");
				if (line.startsWith("//") || !tok.hasMoreElements())
					continue;
				String command = tok.nextToken();
				if (command.equals("userdata")) {
					String userName = tok.nextToken().trim();
					User user = getUserByName(userName);
					if (user == null) {
						logerror("Tried to load userdata for nonexistent user " + userName + ".");
						continue;
					}
					String subCommand = tok.nextToken();
					if (subCommand.equals("hosts")) {
						int hostsLoaded = 0;
						while (tok.hasMoreElements() && hostsLoaded < 10) {
							user.lastSeenTimes[hostsLoaded] = System.currentTimeMillis();	//When converting from old "hosts", default to current time.
							user.hosts[hostsLoaded++] = tok.nextToken(" ").trim();
						}
						log("Loaded " + hostsLoaded + " hosts for user " + user.userName);
					} else if (subCommand.equals("host")) {
						String host = tok.nextToken();
						long time = Long.parseLong(tok.nextToken().trim());
						if (timeInWeeks(time, System.currentTimeMillis()) >= 1) {
							hostsThrownOut++;
							continue;
						}
						for (int i = 0; i < 10; i++) {
							if (user.hosts[i] == null || user.hosts[i].length() == 0) {
								user.hosts[i] = host.trim();
								user.lastSeenTimes[i] = time;
								hostsLoadedTotal++;
								continue lineLoop;
							}
						}
						//Execution should only get here if someone manually modified the data file and included 11 hosts for some reason.
						log("Ran out of userhost slots for user " + user.userName);
						hostsThrownOut++;
					} else if (subCommand.equals("away")) {
						user.away = tok.nextToken("").trim();
						awayMsgLoaded++;
					} else if (subCommand.equals("leavetime")) {
						user.leaveTime = Long.parseLong(tok.nextToken("").trim());
					} else if (subCommand.equals("lasttalked")) {
						user.lastTalked = Long.parseLong(tok.nextToken("").trim());
					}
				// Include for backwards compatability. Messages are called 'reminder' now in the data file.
				} else if (command.equals("message")) {
					String nick = tok.nextToken(" ").trim();
					String target = tok.nextToken(" ").trim();

					long timeSent = Long.parseLong(tok.nextToken(" ").trim());
					long timeToArrive = 0;
					boolean notified = true;
					String message = " " + tok.nextToken("").trim();

					// Throw out if more then a week old, unless to/from a user.
					if (getUserByName(nick) != null && getUserByName(target) != null && timeInWeeks(timeSent, System.currentTimeMillis()) >= 1) {
						messagesThrownOut++;
						continue;
					} else {
						messagesLoaded++;
					}
					if (firstMessage == null) {
						firstMessage = new Message(target, message, nick, notified, timeToArrive, timeSent);
					} else {
						Message currMessage = firstMessage;
						while (currMessage.next != null)
							currMessage = currMessage.next;
						currMessage.next = new Message(target, message, nick, notified, timeToArrive, timeSent);
					}
				//These are messages, and were previously called reminders.
				} else if (command.equals("reminder")) {
					String nick = tok.nextToken(" ").trim();
					String target = tok.nextToken(" ").trim();
					long timeSent = Long.parseLong(tok.nextToken(" ").trim());
					long timeToArrive = Long.parseLong(tok.nextToken(" ").trim());
					boolean notified = stringToBoolean(tok.nextToken(" ").trim());
					String message = tok.nextToken("").trim();

					// Throw out if more then a week old, unless to/from a user.
					if (getUserByName(nick) != null && getUserByName(target) != null && timeInWeeks(timeSent, System.currentTimeMillis()) >= 1) {
						messagesThrownOut++;
						continue;
					} else {
						messagesLoaded++;
					}
					if (firstMessage == null) {
						firstMessage = new Message(target, message, nick, notified, timeToArrive, timeSent);
					} else {
						Message currRem = firstMessage;
						while (currRem.next != null)
							currRem = currRem.next;
						currRem.next = new Message(target, message, nick, notified, timeToArrive, timeSent);
					}
				}
			}

			findNextMessageTime();
			
			log("Messages loaded: " + messagesLoaded);
			log("Messages thrown out: " + messagesThrownOut);
			log("Hosts loaded: " + hostsLoadedTotal);
			log("Hosts thrown out: " + hostsThrownOut);
			log("Away messages loaded: " + awayMsgLoaded);
			log("Away messages thrown out: " + awayMsgThrownOut);
		} catch (IOException ioe) {
			logerror("Couldn't read data file " + filename + ".");
		}
	}

	void writeData() {
		String filename = dataFileName;
		BufferedWriter dataFileWriter;

		try {
			dataFileWriter = new BufferedWriter(new FileWriter(new File(sephiadir, filename), false));
		} catch (IOException ioe) {
			logerror("Couldn't open data file " + filename + " for writing:\n" + ioe.getMessage());
			return;
		}

		try {
			String buffer, userBuffer;

			buffer = "// This file is read on boot. Do not modify unless you know what you are doing.\n";
			dataFileWriter.write(buffer, 0, buffer.length());
		
			for (int i = 0; i < users.length; i++) {
				User user = users[i];
				userBuffer = "userdata " + user.userName;
				for (int j = 0; j < 10; j++) {
					if (user.hosts[j] != null && user.hosts[j].length() > 0) {
						//Check the array up until now for duplicate entries.
						boolean foundDuplicate = false;
						for (int k = 0; k < j; k++) {
							if (user.hosts[k].equals(user.hosts[j])) {
								foundDuplicate = true;
								break;
							}
						}
						if (!foundDuplicate) {
							buffer = userBuffer + " host " + user.hosts[j] + " " + user.lastSeenTimes[j] + "\n";
							dataFileWriter.write(buffer, 0, buffer.length());
						}
					}
				}

				if (user.away != null) {
					buffer = userBuffer;
					buffer += " away " + user.away + "\n";
					dataFileWriter.write(buffer, 0, buffer.length());

					buffer = userBuffer;
					buffer += " leavetime " + user.leaveTime + "\n";
					dataFileWriter.write(buffer, 0, buffer.length());
				}

				if (user.lastTalked > 0) {
					buffer = userBuffer;
					buffer += " lasttalked " + user.lastTalked + "\n";
					dataFileWriter.write(buffer, 0, buffer.length());
				}
			}
			
			if (this.firstMessage != null) {
				Message currMessage = this.firstMessage;
				do {
					buffer = "reminder " + currMessage.sender + " " + currMessage.target + " " + currMessage.timeSent + " " + currMessage.timeToArrive + " " + currMessage.notified + " " + currMessage.message + "\n";
					dataFileWriter.write(buffer, 0, buffer.length());
					currMessage = currMessage.next;
				} while (currMessage != null);
			}

		} catch (IOException ioe) {
			logerror("Couldn't write to data file " + filename + ".");
			return;
		}
		
		try {
			dataFileWriter.close();
		} catch (IOException ioe) {
			logerror("Couldn't save data file " + filename + ".");
			return;
		}
		
		log("Wrote data file.");
	}

	private void loadUsers() {
		String filename = usersFileName;
		log("Loading " + filename);

		if (filename.startsWith("/"))
			users = parser.parseUsers("/", filename);
		else
			users = parser.parseUsers(sephiadir, filename);

		//This function should not return if users is null.
		if (users == null)
			users = new User[0];
		
		log(users.length + " users loaded.");
	}
	
	void parseConfig() {
		String filename = config;
		log("Parsing " + filename);

		parser.parseConfig("", filename);

		loadUsers();
		loadData();
	}

	//"false" "no" and "0" and empty/null strings return false. All else is true.
	boolean stringToBoolean(String string) {
		if (string == null)
			return false;
		if (string.length() <= 0)
			return false;
		if (iequals(string, "false") || iequals(string, "no") || iequals(string, "0"))
			return false;
		return true;
	}
	
	long stringToNumber(String string) {
		try {
			long i = Long.parseLong(string);
			return i;
		} catch (NumberFormatException nfe) {
			//etc etc.
			if (iequals("zero", string)) {
				return 0;
			} else if (iequals("one", string)) {
				return 1;
			}
		}
		return 0;
	}
	
	long stringToTime(String string) {
		if (iregex("^s", string)) {
			return 1000;
		} else if (iregex("^m", string)) {
			return 1000*60;
		} else if (iregex("^h", string)) {
			return 1000*60*60;
		} else if (iregex("^d", string)) {
			return 1000*60*60*24;
		} else if (iregex("^w", string)) {
			return 1000*60*60*24*7;
		}
		return 0;
	}
	
	private StringBuffer replaceKeywords(StringBuffer string, String find, String replace) {
		int keyindex = string.indexOf(find);
	
		if (keyindex > -1) {
			string.delete(keyindex, keyindex+find.length());
			string.insert(keyindex, replace);
		}
		
		return string;
	}
	
	String removePunctuation(String msg, String remove) {
		while (iregex("[" + remove + "]$", msg))
			msg = msg.substring(0, msg.length()-1);
		return msg;
	}
	
	//Get a user by his username or alias only.
	User getUserByName(String name) {
		for (int i = 0; i < users.length; i++) {
			if (iequals(users[i].userName, name.trim()))
				return users[i];
			for (int j = 0; j < users[i].aliases.length; j++)
				if (iequals(users[i].aliases[j], name.trim()))
					return users[i];
		}
		return null;
	}
	
	//Guarantees the person is logged in. Gets a User from a host.
	User getUserByHost(String host) {
		for (int i = 0; i < users.length; i++) {
			for (int j = 0; j < 10; j++)
				if (users[i].hosts[j] != null && iequals(users[i].hosts[j], host.trim()))
					return users[i];
		}
		return null;
	}
	
	//Search for a user by an IRC nick.
	//If the nick is found in a channel, search for a user with that host;
	//   if the host doesn't match a logged in user, return null.
	//   otherwise return the logged in user.
	//If the nick doesn't exist (offline), search by username/alias
	public User getUserByNick(IRCConnection[] connections, String name) {
		boolean foundSquatter = false; //found the nick, but isn't logged in
		for (int j = 0; j < connections.length; j++) {
			IRCConnection con = connections[j];
			for (int i = 0; i < con.getServer().channels.length; i++) {
				for (IRCUser curr = con.getServer().channels[i].users; curr != null; curr = curr.next) {
					// A user is in a channel w/ the searched nick
					if (iequals(curr.name, name)) {
						User result = getUserByHost(curr.host);
						if (result != null)
							return result;
						else
							//Keep searching in case the host is different on
							// a different server.
							foundSquatter = true;
					}
				}
			}
		}
		if (foundSquatter)
			return null;

		//No one is online with this nick; search by username/alias
		return getUserByName(name);
	}
	
	User getUser(int i) {
		return users[i];
	}

	int getNumUsers() {
		return users.length;
	}
	
	void updateUserTimes(String nick, String host, IRCServer server, String channel) {
		User user = getUserByHost(host);
		if (user == null)
			return;
		user.lastTalked = System.currentTimeMillis();

		IRCChannel lastChannel = server.findChannel(channel);
		if (lastChannel != null)
			user.lastChannel = lastChannel;

		for (int i = 0; i < 10; i++) {
			if (iequals(user.hosts[i], host)) {
				user.lastSeenTimes[i] = System.currentTimeMillis();
				break;
			}
		}
	}

	//Called ten times a second, so must be relatively fast.
	//This method returns pending messages that are ready for notification.
	Message[] getPendingMessages() {
		if (System.currentTimeMillis() < nextMessage || nextMessage == 0)
			return new Message[0];
		Vector messages = new Vector(10);
		for (Message curr = firstMessage; curr != null; curr = curr.next) {
			if (curr.timeToArrive < System.currentTimeMillis() && !curr.notified) {
				messages.add(curr);
			}
		}
		if (messages.size() <= 0) {
			//If no messages were found, then we must be somewhat close, assuming nextMessage was close. Try again soon.
			nextMessage = System.currentTimeMillis() + 10;
			return new Message[0];
		}
		return (Message[])messages.toArray(new Message[messages.size()]);
	}

	//Removes messages for a nick that are past their notification time,
	//Check against the User's userName too, in case reciever is an alias
	void removeRecentMessages(String receiver, User user) {
		for (Message curr = firstMessage; curr != null; curr = curr.next)
			if (iequals(curr.target, receiver) || (user != null && iequals(user.userName, curr.target)))
				if (System.currentTimeMillis() > curr.timeToArrive)
					if (System.currentTimeMillis() < curr.timeNotified + MESSAGE_STALE_DUR)
						removeMessage(curr);
	}
	
	Message[] getMessagesByReceiver(String receiver, User user, boolean activeOnly) {
		if (receiver == null)
			return new Message[0];
		Vector messages = new Vector(10);
		for (Message curr = firstMessage; curr != null; curr = curr.next) {
			if (iequals(curr.target, receiver) || (user != null && iequals(user.userName, curr.target))) {
				if (!activeOnly || curr.timeToArrive < System.currentTimeMillis())
					messages.add(curr);
			}
		}
		return (Message[])messages.toArray(new Message[messages.size()]);
	}
	
	Message[] getMessagesBySender(String sender, User user) {
		if (sender == null)
			return new Message[0];
		Vector messages = new Vector(10);
		for (Message curr = firstMessage; curr != null; curr = curr.next) {
			if (iequals(curr.sender, sender) || (user != null && iequals(curr.sender, user.userName))) {
				messages.add(curr);
			}
		}
		return (Message[])messages.toArray(new Message[messages.size()]);
	}
	
	boolean removeMessage(Message kill) {
		Message curr = firstMessage;
		Message last = null;

		while (curr != null) {
			if (curr == kill) {
				if (last == null)
					firstMessage = curr.next;
				else
					last.next = curr.next;
				writeData();
				findNextMessageTime();
				return true;
			} else {
				last = curr;
			}
			curr = curr.next;
		}
		return false;
	}
	
	//Adds a message to the list, and returns a "pointer" to it.
	Message addMessage(String target, String message, String nick) throws NumberFormatException {
		Message last;
		if (firstMessage == null) {
			last = firstMessage = new Message(target, message, nick);
		} else {
			Message curr = firstMessage;
			while (curr.next != null)
				curr = curr.next;
			last = curr.next = new Message(target, message, nick);
		}
		findNextMessageTime();
		writeData();
		return last;
	}
	
	void findNextMessageTime() {
		long earliestTime = 0;
		for (Message curr = firstMessage; curr != null; curr = curr.next) {
			if (earliestTime == 0 && curr.notified == false)
				earliestTime = curr.timeToArrive;
			else if (curr.timeToArrive < earliestTime && curr.notified == false)
				earliestTime = curr.timeToArrive;
		}
		nextMessage = earliestTime;
	}
	
	boolean validateLogin(String login, String password) {
		for (int i = 0; i < users.length; i++)
			if (iequals(users[i].userName, login) && users[i].password.equals(password))
				return true;
		return false;
	}
	
	boolean loginUser(User user, String host) {
		for (int i = 0; i < 10; i++) {
			if (user.hosts[i] == null || user.hosts[i].length() <= 0) {
				user.hosts[i] = host;
				user.lastSeenTimes[i] = System.currentTimeMillis();
				return true;
			}
		}
		logoutOldest(user);
		// I hope this never causes an infinite loop.
		return loginUser(user, host);
	}
	
	void logoutOldest(User user) {
		float oldest = System.currentTimeMillis();
		int oldestID = -1;
		for (int i = 0; i < 10; i++) {
			if (user.lastSeenTimes[i] < oldest) {
				oldest = user.lastSeenTimes[i];
				oldestID = i;
			}
		}
		// I don't know why this would happen, but...
		if (oldestID == -1)
			return;
		logout(user, oldestID);
	}

	void logout(User user, int i) {
		if (i == -1) {
			for (int j = 0; j < 10; j++)
				user.hosts[j] = null;
			return;
		}
		user.hosts[i] = null;
		writeData();
	}
	
	String randomPhrase(String filename) {
		try {
			Vector phrases = new Vector();
			BufferedReader in = new BufferedReader(new FileReader(new File(sephiadir, filename)));
			String line;
			while ((line = in.readLine()) != null)
				phrases.add(line);
			in.close();
			if (phrases.size() == 1)
				return null;
			Random rnd = new Random();
			return (String)phrases.get(rnd.nextInt(phrases.size()));
		} catch (IOException ioe) {
			return null;
		}
	}

	boolean isAdmin(String host) {
		User user = getUserByHost(host);
		if (user != null && user.memberType == User.USER_ADMIN)
			return true;
		return false;
	}
	
	boolean isVino(String host) {
		return false;
	}

	boolean isVino(User user) {
		return false;
	}
	
	boolean checkForBlacklist(String nick) {
		//Check for blacklisted nicks.
		for (int i = 0; i < blacklist.length; i++) {
			if (iregex(blacklist[i], nick)) {
				return true;
			}
		}
		return false;
	}

	void addToBlacklist(String nick) {
		log("added to blacklist: " + nick);
		String newBlacklist[] = new String[blacklist.length+1];
		newBlacklist[0] = nick;
		for (int i = 1; i < newBlacklist.length; i++) {
			newBlacklist[i] = blacklist[i-1];
		}
		this.blacklist = newBlacklist;
	}
	
	boolean matchHellos(String msg) {
		for (int i = 0; i < hellos.length; i++) {
			if (iregex(hellos[i], msg)) {
				return true;
			}
		}
		return false;
	}

	//Bot system log
	public void log(String log) {
						
		String orig = log;
		//If the log file hasn't been opened yet, save up the log entries until it opens.
		if (syslog == null) {
			if (syslogBuffer == null)
				syslogBuffer = "";
			syslogBuffer += log;
			if (!log.endsWith("\n"))
				syslogBuffer += "\n";
		} else {
			if (syslogBuffer != null && syslogBuffer.length() > 0) {
				log = syslogBuffer + log;
				syslogBuffer = null;
			}
			try {
				syslog.write(log, 0, log.length());
				if (!log.endsWith("\n")) {
					syslog.newLine();
				}
				syslog.flush();
			} catch (IOException ioe) {
				System.out.println("Warning: Couldn't write to syslog!");
			}
		}
		System.out.println(orig);
	}

	//Error log
	public void logerror (String log) {
		log("SYSERR: " + log);
	}

	long timeInSeconds(long from, long to) {
		return (to - from)/1000;
	}
	
	long timeInMinutes(long from, long to) {
		return (to - from)/1000/60;
	}
	
	long timeInHours(long from, long to) {
		return (to - from)/1000/60/60;
	}
	
	long timeInDays(long from, long to) {
		return (to - from)/1000/60/60/24;
	}
	
	long timeInWeeks(long from, long to) {
		return (to - from)/1000/60/60/24/7;
	}
	
	String getName(int server) {
		return servers[server].name;
	}

	int getNumNetworks() {
		return servers.length;
	}
	
	String getNetwork(int server) {
		return servers[server].network;
	}

	int getPort(int server) {
		return servers[server].port;
	}
	
	String getGreeting(int serverID, int channelID) {
		String greeting = "";
		if (serverID < 0 || serverID >= servers.length)
			// Can't return greeting, because we don't know which server we want the greeting for!
			return globals.greeting;
		else {
			if (channelID < 0 || channelID >= servers[serverID].channels.length) {
				// Why would you request a server greeting but not a channel one? NO REASON!
				greeting = globals.greeting;
			} else {
				if (servers[serverID].channels[channelID].globals.greeting != null)
					greeting = servers[serverID].channels[channelID].globals.greeting;
				else if (servers[serverID].globals.greeting != null)
					greeting = servers[serverID].globals.greeting;
				else
					greeting = globals.greeting;
			}
			return new String(replaceKeywords(new StringBuffer(greeting), "%n", servers[serverID].name));
		}
	}

	void setGreeting(String greeting, int serverID, int channelID) {
		if (serverID < 0 || serverID >= servers.length)
			globals.greeting = greeting;
		else if (channelID < 0 || channelID >= servers[serverID].channels.length)
			servers[serverID].globals.greeting = greeting;
		else 
			servers[serverID].channels[channelID].globals.greeting = greeting;
	}
	
	int getNumChannels(int serverID) {
		return servers[serverID].channels.length;
	}

	String getChannel(int serverID, int i) {
		return servers[serverID].channels[i].name;
	}

	String getRandomHelloReply() {
		return getHelloReply(new Random().nextInt(getNumHelloReplies()));
	}
	
	int getNumHelloReplies() {
		return helloreplies.length;
	}

	String getHelloReply(int i) {
		return helloreplies[i];
	}

	void setHelloReplies(String helloreplies, int server, int channel) {
		if (server != -1 || channel != -1)  //TODO: Set channel/server-specifc values
			return;
		log("helloreplies changed to " + helloreplies);
		StringTokenizer tok = new StringTokenizer(helloreplies, " ");
		int tokens = tok.countTokens();
		this.helloreplies = new String[tokens];
		for (int i = 0; i < tokens; i++) {
			this.helloreplies[i] = tok.nextToken();
		}
	}
	
	boolean getCensor(int serverID, int channelID) {
		if (serverID < 0 || serverID >= servers.length)
			return globals.censor.booleanValue();
		else {
			if (channelID < 0 || channelID >= servers[serverID].channels.length) {
				if (servers[serverID].globals.censor != null)
					return servers[serverID].globals.censor.booleanValue();
				else
					return globals.censor.booleanValue();
			} else {
				if (servers[serverID].channels[channelID].globals.censor != null)
					return servers[serverID].channels[channelID].globals.censor.booleanValue();
				else if (servers[serverID].globals.censor != null)
					return servers[serverID].globals.censor.booleanValue();
				else
					return globals.censor.booleanValue();
			}
		}
	}

	void setCensor(boolean censor, int serverID, int channelID) {
		if (serverID < 0 || serverID >= servers.length)
			globals.censor = new Boolean(censor);
		else if (channelID < 0 || channelID >= servers[serverID].channels.length)
			servers[serverID].globals.censor = new Boolean(censor);
		else 
			servers[serverID].channels[channelID].globals.censor = new Boolean(censor);
		log("censor changed to " + censor);
	}

	String getLogdir() {
		return logdir;
	}

	void setGlobals(String sephiadir, String logdir, String dataFileName, String usersFileName) {
		if (sephiadir.trim().length() > 0)
			this.sephiadir = sephiadir;
		if (logdir.trim().length() > 0)
			this.logdir = logdir;
		if (dataFileName.trim().length() > 0)
			this.dataFileName = dataFileName;
		if (usersFileName.trim().length() > 0)
			this.usersFileName = usersFileName;
		log("sephiadir changed to " + this.sephiadir);
		log("logdir changed to " + this.logdir);
		log("dataFileName changed to " + this.dataFileName);
		log("usersFileName changed to " + this.usersFileName);
	}

	void setHellos(String hellos, int server, int channel) {
		if (server != -1 || channel != -1)  //TODO: Set channel/server-specifc values
			return;
		log("hellos changed to " + hellos);
		StringTokenizer tok = new StringTokenizer(hellos, " ");
		int tokens = tok.countTokens();
		this.hellos = new String[tokens];
		for (int i = 0; i < tokens; i++) {
			this.hellos[i] = tok.nextToken();
		}
	}

	void setChannelInfo(int serverID, int channelID, String name) {
		if (serverID < 0 || serverID >= servers.length)
			return;
		if (channelID < 0 || channelID >= servers[serverID].channels.length)
			return;
		log("channel added: " + name);
		servers[serverID].channels[channelID].name = name;
	}
	
	int addServer() {
		ServerConfig servers[] = this.servers;
		this.servers = new ServerConfig[servers.length+1];
		for (int i = 0; i < servers.length; i++) {
			this.servers[i] = servers[i];
		}
		this.servers[this.servers.length-1] = new ServerConfig();
		return this.servers.length-1;
	}
	
	int addChannel(int serverID) {
		ServerConfig server = servers[serverID];
		ChannelConfig channels[] = server.channels;
		server.channels = new ChannelConfig[channels.length+1];
		for (int i = 0; i < channels.length; i++) {
			server.channels[i] = channels[i];
		}
		server.channels[server.channels.length-1] = new ChannelConfig();
		return server.channels.length-1;
	}
	
	void setServerInfo(int serverID, String host, String port, String nick) {
		ServerConfig server = servers[serverID];
		if (host.trim().length() > 0)
			server.network = host;
		if (nick.trim().length() > 0)
			server.name = nick;
		if (port.trim().length() > 0)
			server.port = Integer.parseInt(port);
		log("network changed to " + server.network);
		log("nick changed to " + server.name);
		log("port changed to " + server.port);
	}
}

class GlobalConfig {
	//Don't use basic data types here (like boolean or int) because null means that this particular option is not set.
	//Also be sure to default them all in setDefaults()
	Boolean censor = null;
	String greeting = null;
}

class ServerConfig {
	String network;
	String name;
	int port;
	
	ChannelConfig channels[];
	GlobalConfig globals;

	ServerConfig( ) {
		globals = new GlobalConfig();
		channels = new ChannelConfig[0];
		name = "SephiaBot";
		network = "irc.us.freenode.net";
		port = 6667;
	}
}

class ChannelConfig {
	String name;
	GlobalConfig globals = new GlobalConfig();
}
