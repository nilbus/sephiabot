/*
 * The purpose of this class is to keep track of all of SephiaBot's data, abstracting it away from the main part of the program.
 * This keeps it nice and neat and what have you.
 */

import java.io.*;
import java.util.*;
import java.util.regex.*;

class SephiaBotData {

	private String network;
	private int port;
	private String name;
	private String channels[];
	private String greeting;
	private String spell;
	private String hellos[];
	private String helloreplies[];
	private String logdir;
	private String sephiadir; //Location of sephiabot. Quote, data files here
	private String blacklist[];
	private String config;
	private boolean censor;

	private Message firstMessage = null;

	private User vino;
	private User users[];

	private String syslogBuffer;
	private BufferedWriter syslog;

	private String dataFileName;
	private String usersFileName;
	
	public static final int USER_VINO = 0;

	private SephiaBotData() {
	}
	
	SephiaBotData(String config) {
		this.config = config;
		setDefaults();
	}

	private void setDefaults() {
		//Set Defaults
		this.name = "SephiaBot";
		this.network = "irc.us.freenode.net";
		this.port = 6667;
		this.channels = new String[] {"#sephiabot"};
		this.greeting = "Hello, I am %n, the channel bot. You all suck.";
		this.hellos = new String[] {"hi[hy2]?", "yo[^u]", "hey", "greetings\\b", "kon{1,2}ichiwa", "hola\\b", "sup", "morning\\b", "(y\\s)?h[aeu]l{1,2}o"};
		this.helloreplies = new String[] {"Yo."};
		this.logdir = "/var/log/sephiabot"; //not a very good default unless documented, incase we actually released this someday (haha)
		this.sephiadir = "/var/lib/sephiabot"; //ditto
		this.dataFileName = "sephiabot.dat";
		this.usersFileName = "users.cfg";
		this.blacklist = new String[] {};
		this.censor = true;
		this.vino = new User("Vino", "xxxxx", User.USER_ADMIN);
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
			int messagesLoaded = 0;
			int messagesThrownOut = 0;
			int hostsLoadedTotal = 0;
			int hostsThrownOut = 0;
			int awayMsgLoaded = 0;
			int awayMsgThrownOut = 0;
			firstMessage = null;		//Keep messages already in memory from persisting.
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
						if (time < System.currentTimeMillis() - 1000*60*60*24*7) {
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
				} else if (command.equals("message")) {
					String nick = tok.nextToken(" ").trim();
					String target = tok.nextToken(" ").trim();
					long time = Long.parseLong(tok.nextToken(" ").trim());
					String message = " " + tok.nextToken("").trim();

					//Throw out if more then a week old.
					if (time < System.currentTimeMillis() - 1000*60*60*24*7) {
						messagesThrownOut++;
						continue;
					} else
						messagesLoaded++;
					if (firstMessage == null) {
						firstMessage = new Message(target, message, nick, time);
					} else {
						Message currMsg = firstMessage;
						while (currMsg.next != null)
							currMsg = currMsg.next;
						currMsg.next = new Message(target, message, nick, time);
					}
				//vinohost, vinoaway, and vinoleavetime should not be necessary anymore. Vino's info is loaded like any other user.
				// It's left in here for backwards compatibility.
				} else if (command.equals("vinohost")) {
					int hostsLoaded = 0;
					while (tok.hasMoreElements() && hostsLoaded < 10) {
						this.vino.lastSeenTimes[hostsLoaded] = System.currentTimeMillis();   //When converting from old "hosts", default to current time.
						this.vino.hosts[hostsLoaded++] = tok.nextToken(" ").trim();
					}
					log("Loaded " + hostsLoaded + " vinohosts");
				} else if (command.equals("vinoaway")) {
					this.vino.away = tok.nextToken("").trim();
					log("Loaded vinoaway " + this.vino.away);
				} else if (command.equals("vinoleavetime")) {
					this.vino.leaveTime = Long.parseLong(tok.nextToken("").trim());
					log("Loaded vinoleavetime " + this.vino.leaveTime);
				}
			}

			//TODO: Cycle through away messages and throw out old ones.
			
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
			String buffer;

			buffer = "// This file is read on boot. Do not modify unless you know what you are doing.\n";
			dataFileWriter.write(buffer, 0, buffer.length());
		
			String userBuffer = "userdata " + vino.userName;
			for (int i = 0; i < 10; i++) {
				if (vino.hosts[i] != null && vino.hosts[i].length() > 0) {
					//Check the array up until now for duplicate entries.
					boolean foundDuplicate = false;
					for (int j = 0; j < i; j++) {
						if (vino.hosts[j] != null && vino.hosts[j].equals(vino.hosts[i])) {
							foundDuplicate = true;
							break;
						}
					}
					if (!foundDuplicate) {
						buffer = userBuffer + " host " + vino.hosts[i] + " " + vino.lastSeenTimes[i] + "\n";
						dataFileWriter.write(buffer, 0, buffer.length());
					}
				}
			}

			if (this.vino.away != null) {
				buffer = userBuffer;
				buffer += " away " + this.vino.away + "\n";
				dataFileWriter.write(buffer, 0, buffer.length());

				buffer = userBuffer;
				buffer += " leavetime " + this.vino.leaveTime + "\n";
				dataFileWriter.write(buffer, 0, buffer.length());
			}
			
			if (vino.lastTalked > 0) {
				buffer = userBuffer;
				buffer += " lasttalked " + this.vino.lastTalked + "\n";
				dataFileWriter.write(buffer, 0, buffer.length());
			}

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
					//If the message is more then a week old, do not store it.
					if (currMessage.time > System.currentTimeMillis() - 1000*60*60*24*7) {
						buffer = "message " + currMessage.sender + " " + currMessage.target + " " + currMessage.time + currMessage.message + "\n";
						dataFileWriter.write(buffer, 0, buffer.length());
					}
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
			
		//This function should not return if users is null.
		users = new User[0];

		BufferedReader dataFileReader;

		try {
			dataFileReader = new BufferedReader(new FileReader(new File(sephiadir, filename)));
		} catch (IOException ioe) {
			logerror("Couldn't find users file: " + sephiadir + "/" + filename + " no users loaded.");
			return;  //Assume no datafile has been created if it doesn't exist
		}

		try {
			Vector newUserList = new Vector();
			while (dataFileReader.ready()) {
				String line = dataFileReader.readLine();
				StringTokenizer tok = new StringTokenizer(line, " ");
				if (line.startsWith("//") || !tok.hasMoreElements())
					continue;
				String userName = tok.nextToken(" ");
				//If there are no elements, throw a WTFException and continue blindly.
				if (!tok.hasMoreElements())
					continue;
				String password = tok.nextToken(" ");
				//If there are no elements, throw a WTFException and continue blindly.
				if (!tok.hasMoreElements())
					continue;
				String memberTypeString = tok.nextToken("").trim();
				int memberType = User.USER_MEMBER;
				if (memberTypeString.equalsIgnoreCase("admin"))
					memberType = User.USER_ADMIN;
				User newUser = new User(userName, password, memberType);
				newUserList.add(newUser);
			}
			users = new User[newUserList.size()];
			users = (User[])newUserList.toArray(users);
			log(users.length + " users loaded.");
		} catch (IOException ioe) {
			logerror("Couldn't read data file " + filename + ".");
		}
	}
	
	void parseConfig() {
		String filename = config;
		log("Parsing " + filename);

		BufferedReader configIn;

		try {
			configIn = new BufferedReader(new FileReader(filename));
		} catch (IOException ioe) {
			logerror("Couldn't open cfg file " + filename + ".");
			return;
		}

		try {
			while(configIn.ready()) {
				String line = configIn.readLine();
				StringTokenizer tok = new StringTokenizer(line, " ");
				if (line.startsWith("//") || !tok.hasMoreElements()) {
					continue;
				}
				String command = tok.nextToken();
				if (command.equals("name")) {
					this.name = tok.nextToken("").trim();
					log("name changed to " + name);
				} else if (command.equals("server")) {
					this.network = tok.nextToken("").trim();
					log("network changed to " + this.network);
				} else if (command.equals("port")) {
					this.port = Integer.parseInt(tok.nextToken("").trim());
					log("port changed to " + this.port);
				} else if (command.equals("greeting")) {
					StringBuffer greeting = new StringBuffer(tok.nextToken("").trim());
					greeting = replaceKeywords(greeting);
					this.greeting = greeting.toString();
					log("greeting changed to " + this.greeting);
				} else if (command.equals("channels")) {
					StringBuffer buf = new StringBuffer("");
					int tokens = tok.countTokens();
					this.channels = new String[tokens];
					for (int i = 0; i < tokens; i++) {
						this.channels[i] = tok.nextToken();
						buf.append(this.channels[i] + " ");
					}
					log("channels changed to " + buf);
				} else if (command.equals("hello")) {
					StringBuffer buf = new StringBuffer("");
					int tokens = tok.countTokens();
					this.hellos = new String[tokens];
					for (int i = 0; i < tokens; i++) {
						this.hellos[i] = tok.nextToken();
						buf.append(this.hellos[i] + " ");
					}
					log("hello changed to " + buf);
				} else if (command.equals("helloreplies")) {
					StringBuffer buf = new StringBuffer("");
					int tokens = tok.countTokens();
					this.helloreplies = new String[tokens];
					for (int i = 0; i < tokens; i++) {
						this.helloreplies[i] = tok.nextToken();
						buf.append(this.helloreplies[i] + " ");
					}
					log("helloreplies changed to " + buf);
				} else if (command.equals("blacklist")) {
					StringBuffer buf = new StringBuffer("");
					int tokens = tok.countTokens();
					this.blacklist = new String[tokens];
					for (int i = 0; i < tokens; i++) {
						this.blacklist[i] = tok.nextToken();
						buf.append(this.blacklist[i] + " ");
					}
					log("blacklist changed to " + buf);
				} else if (command.equals("logdir")) {
					this.logdir = tok.nextToken("").trim();
					log("logdir changed to " + this.logdir);
				} else if (command.equals("datafilename")) {
					this.dataFileName = tok.nextToken("").trim();
					log("datafilename changed to " + this.dataFileName);
				} else if (command.equals("usersfilename")) {
					this.dataFileName = tok.nextToken("").trim();
					log("usersfilename changed to " + this.dataFileName);
				} else if (command.equals("sephiadir")) {
					this.sephiadir = tok.nextToken("").trim();
					log("sephiadir changed to " + this.sephiadir);
				} else if (command.equals("censor")) {
					this.censor = stringToBoolean(tok.nextToken().trim());
					log("censor changed to " + this.censor);
				}
			}
		} catch (IOException ioe) {
			logerror("Couldn't read cfg file " + filename + ".");
		}
		
		try {
			syslog = new BufferedWriter(new FileWriter(new File(logdir, "syslog.txt"), true));
		} catch (IOException ioe) {
			logerror("Couldn't open syslog file:\n" + ioe.getMessage());
		}

		loadUsers();
		loadData();
	}

	//"false" "no" and "0" and empty/null strings return false. All else is true.
	private boolean stringToBoolean(String string) {
		if (string == null)
			return false;
		if (string.length() <= 0)
			return false;
		if (iequals(string, "false") || iequals(string, "no") || iequals(string, "0"))
			return false;
		return true;
	}
	
	private StringBuffer replaceKeywords(StringBuffer greeting) {
		int keyindex = greeting.indexOf("%n");
	
		if (keyindex > -1) {
			greeting.delete(keyindex, keyindex+2);
			greeting.insert(keyindex, name);
		}
		
		return greeting;
	}
	
	//Performs a case-insensitive regexp match of string against pattern.
	boolean iregex(String pattern, String string) {
		Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(string);
		return m.find();
	}
	
	//Performs a case-insensitive string comparison.
	boolean iequals(String str1, String str2) {
		if (str1 == null && str2 == null) {
			return true;
		} else if (str1 == null || str2 == null) {
			return false;
		} else {
			return str1.toLowerCase().equals(str2.toLowerCase());
		}
	}

	//Get a user by his username only.
	User getUserByName(String name) {
		if (iequals(vino.userName, name.trim()))
			return vino;
		for (int i = 0; i < users.length; i++)
			if (iequals(users[i].userName, name.trim()))
				return users[i];
		return null;
	}
	
	//Guarunteed the person is logged in. Gets a User from a host.
	User getUserByHost(String host) {
		//First check for Vino.
		if (isVino(host))
			return vino;
		for (int i = 0; i < users.length; i++) {
			for (int j = 0; j < 10; j++)
				if (users[i].hosts[j] != null && users[i].hosts[j].equalsIgnoreCase(host.trim()))
					return users[i];
		}
		return null;
	}
	
	User getUser(int i) {
		if (i == 0)
			return vino;
		else
			return users[i-1];
	}

	int getNumUsers() {
		return users.length + 1;
	}
	
	void updateUserTimes(String nick, String host) {
		User user = getUserByHost(host);
		if (user == null)
			return;
		user.lastTalked = System.currentTimeMillis();
		for (int i = 0; i < 10; i++) {
			if (iequals(user.hosts[i], host)) {
				user.lastSeenTimes[i] = System.currentTimeMillis();
				break;
			}
		}
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

	Message[] getMessagesByReceiver(String receiver, User user) {
		if (receiver == null)
			return new Message[0];
		Vector messages = new Vector(10);
		Message currMsg = firstMessage;
		while (currMsg != null) {
			if (iequals(currMsg.target, receiver) || (user != null && iequals(user.userName, currMsg.target))) {
				messages.add(currMsg);
			}
			currMsg = currMsg.next;
		}
		return (Message[])messages.toArray(new Message[messages.size()]);
	}
	
	boolean removeMessage(Message kill) {
		Message currMsg = firstMessage;
		Message lastMsg = null;

		while (currMsg != null) {
			if (currMsg == kill) {
				if (lastMsg == null)
					firstMessage = currMsg.next;
				else
					lastMsg.next = currMsg.next;
				writeData();
				return true;
			} else {
				lastMsg = currMsg;
			}
			currMsg = currMsg.next;
		}
		return false;
	}
	
	void addMessage(String target, String message, String nick) {
		if (firstMessage == null) {
			firstMessage = new Message(target, message, nick);
		} else {
			Message currMsg = firstMessage;
			while (currMsg.next != null)
				currMsg = currMsg.next;
			currMsg.next = new Message(target, message, nick);
		}
	}
	
	void sendMessageToAllUsers(String message, String nick) {
		for (int i = 0; i < users.length; i++)
			addMessage(users[i].userName, message, nick);
		addMessage("Vino", message, nick);
	}
	
	boolean validateLogin(String login, String password) {
		if (iequals(login, "vino") && password.equals("xxxxx"))
			return true;
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
		return false;
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
			logerror("Couldn't open excuse file: " + ioe.getMessage());
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
		for (int i = 0; i < 10; i++) {
			if (vino.hosts[i] != null && iequals(host, vino.hosts[i]))
				return true;
		}
		return false;
	}

	boolean isVino(User user) {
		if (user == vino)
			return true;
		else
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

	boolean matchHellos(String msg) {
		for (int i = 0; i < hellos.length; i++) {
			if (iregex("^"+hellos[i], msg)) {
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

	String getName() {
		return name;
	}

	String getNetwork() {
		return network;
	}

	int getPort() {
		return port;
	}
	
	String getGreeting() {
		return greeting;
	}
	
	int getNumChannels() {
		return channels.length;
	}

	String getChannel(int i) {
		return channels[i];
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

	boolean getCensor() {
		return censor;
	}
}
