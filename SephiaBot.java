/*
TODO: Reminders
TODO: Better handling of dropped connections, etc.
TODO: On JOINs, channel is prefixed with a :. Make sure this is accounted for.
TODO: Track nick changes so who is here works.
*/
import java.io.*;
import java.util.*;
import java.util.regex.*;

class SephiaBot implements IRCListener {

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
	private int historySize;
	private String historyNick[];
	private String historyText[];
	private String lastRepeat;
	private String config;

	private Message firstMessage = null;

	private User vino;
	private User users[];

	private IRCIO ircio;
	private BufferedWriter logOut[];
	private String syslogBuffer;
	private BufferedWriter syslog;
	private IRCServer server;

	private String dataFileName;
	private String usersFileName;

	private long nextWho;
	private long nextHi;

	private boolean freenode() { return iregex("freenode", network); }
	private boolean gamesurge() { return iregex("gamesurge", network); }

	public static void main(String args[]) {
		String cfgPath = "sephiabot.cfg";
		final String usage = "\nUsage: sephiabot [-c config file]\n" +
			" Default is to search for sephiabot.cfg in the current directory.";

		if (args != null && args.length > 0) {
			for (int i = 0; i < args.length; i++) {
				if (args[i].equals("--help")) {
					System.out.println(usage);
					System.exit(0);
				}
			}
			if (args[0].equals("-c"))
				if (args.length > 1)
					cfgPath = args[1];
				else {
					System.out.println("You must specify the path of your config file with -c" + usage);
					System.exit(0);
				}
			else {
				System.out.println("Invalid arguments." + usage);
				System.exit(0);
			}
		}

		SephiaBot sephiaBot = new SephiaBot(cfgPath);
		sephiaBot.connect();

		while (true) {
			sephiaBot.poll();
		}
	}

	public SephiaBot(String config) {
		//Set Defaults
		this.config = config;
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
		this.historySize = 3;
		this.historyNick = new String[this.historySize];
		this.historyText = new String[this.historySize];

		this.nextWho = 0;
		this.nextHi = 0;

		this.vino = new User("Vino", "xxxxx", User.USER_ADMIN, new String[10], null, 0);
		
		log("----------------------------------------------------------------------------\nSephiaBot Started!");
		parseConfig(config);

		try {
			syslog = new BufferedWriter(new FileWriter(new File(logdir, "syslog.txt"), true));
		} catch (IOException ioe) {
			logerror("Couldn't open syslog file:\n" + ioe.getMessage());
		}

		log("Network: " + network + " " + port + " : " + name);

		try {
			logOut = new BufferedWriter[channels.length];
			for (int i = 0; i < channels.length; i++) {
				logOut[i] = new BufferedWriter(new FileWriter("/home/vino/sephiabot/log-"+channels[i]+".txt", true));
			}
		} catch (IOException ioe) {
			logerror("Couldn't open log file.");
		}
	}

	String makeTime(long formerTime) {
		long time, elapsed = System.currentTimeMillis() - formerTime;
		int second = 1000;
		String away;
		if (elapsed < second*60) {
			time = elapsed/1000;
			away = "about " + time + " second" + ((time!=1)?"s":"");
		} else if (elapsed < second*60*60) {
			time = elapsed/1000/60;
			away = "about " + time + " minute" + ((time!=1)?"s":"");
		} else if (elapsed < second*60*60*24) {
			time = elapsed/1000/60/60;
			away = "about " + time + " hour" + ((time!=1)?"s":"");
		} else if (elapsed < second*60*60*24*7) {
			time = elapsed/1000/60/60/24;
			away = "about " + time + " day" + ((time!=1)?"s":"");
		} else {
			away = "more then a week";
		}
		return away;
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
			firstMessage = null;		//Keep messages already in memory from persisting.
			while (dataFileReader.ready()) {
				String line = dataFileReader.readLine();
				StringTokenizer tok = new StringTokenizer(line, " ");
				if (line.startsWith("//") || !tok.hasMoreElements())
					continue;
				String command = tok.nextToken();
				if (command.equals("vinohost")) {
					int hostsLoaded = 0;
					while (tok.hasMoreElements() && hostsLoaded < 10)
						this.vino.hosts[hostsLoaded++] = tok.nextToken(" ").trim();
					log("Loaded " + hostsLoaded + " vinohosts");
				} else if (command.equals("vinoaway")) {
					this.vino.away = tok.nextToken("").trim();
					log("Loaded vinoaway " + this.vino.away);
				} else if (command.equals("vinoleavetime")) {
					this.vino.leavetime = Long.parseLong(tok.nextToken("").trim());
					log("Loaded vinoleavetime " + this.vino.leavetime);
				} else if (command.equals("userdata")) {
					String userName = tok.nextToken().trim();
					User user = getUserByName(userName);
					if (user == null) {
						logerror("Tried to load userdata for nonexistent user " + userName + ".");
						continue;
					}
					String subCommand = tok.nextToken();
					if (subCommand.equals("hosts")) {
						int hostsLoaded = 0;
						while (tok.hasMoreElements() && hostsLoaded < 10)
							user.hosts[hostsLoaded++] = tok.nextToken(" ").trim();
						log("Loaded " + hostsLoaded + " hosts for user " + user.userName);
					} else if (subCommand.equals("away")) {
						user.away = tok.nextToken("").trim();
						log("Loaded away for user " + user.userName + ": " + user.away);
					} else if (subCommand.equals("leavetime")) {
						user.leavetime = Long.parseLong(tok.nextToken("").trim());
						log("Loaded leavetime for user " + user.userName + ": " + user.leavetime);
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
				}
			}
			log("Messages loaded: " + messagesLoaded);
			log("Messages thrown out: " + messagesThrownOut);
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
		
			buffer = "vinohost";
			boolean hostFound = false;
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
					if (!foundDuplicate)
						buffer += " " + this.vino.hosts[i];
					hostFound = true;
				}
			}
			buffer += "\n";
			if (hostFound)
				dataFileWriter.write(buffer, 0, buffer.length());

			if (this.vino.away != null) {
				buffer = "vinoaway " + this.vino.away + "\n";
				dataFileWriter.write(buffer, 0, buffer.length());

				buffer = "vinoleavetime " + this.vino.leavetime + "\n";
				dataFileWriter.write(buffer, 0, buffer.length());
			}

			for (int i = 0; i < users.length; i++) {
				User user = users[i];
				String userBuffer = "userdata " + user.userName;
				buffer = userBuffer + " hosts";
				hostFound = false;
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
						if (!foundDuplicate)
							buffer += " " + user.hosts[j];
						hostFound = true;
					}
				}
				buffer += "\n";
				if (hostFound)
					dataFileWriter.write(buffer, 0, buffer.length());

				if (user.away != null) {
					buffer = userBuffer;
					buffer += " away " + user.away + "\n";
					dataFileWriter.write(buffer, 0, buffer.length());

					buffer = userBuffer;
					buffer += " leavetime " + user.leavetime + "\n";
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

	void loadUsers() {
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
	
	void parseConfig(String filename) {
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
					log("name changed to " + this.name);
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
/*				} else if (command.equals("spell")) {
					StringBuffer greeting = new StringBuffer(tok.nextToken("").trim());
					greeting = replaceKeywords(greeting);
					this.spell = greeting.toString();
					log("spell changed to " + this.spell);*/
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
				}
			}
		} catch (IOException ioe) {
			logerror("Couldn't read cfg file " + filename + ".");
		}
		
		loadUsers();
		loadData();
	}

	StringBuffer replaceKeywords(StringBuffer greeting) {
		int keyindex = greeting.indexOf("%n");

		if (keyindex > -1) {
			greeting.delete(keyindex, keyindex+2);
			greeting.insert(keyindex, name);
		}

		return greeting;
	}

	void connect() {

		ircio = new IRCIO(this, network, port);
		ircio.login(channels, name);
		server = new IRCServer(network, port, channels);

	}

	void poll() {
		ircio.poll();

		try {
			Thread.sleep(100);
		} catch (InterruptedException ie) {}

	}

	//Performs a case-insensitive regexp match of string against pattern.
	private boolean iregex(String pattern, String string) {
		Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(string);
		return m.find();
	}
	
	//Performs a case-insensitive string comparison.
	private boolean iequals(String str1, String str2) {
		if (str1 == null && str2 == null) {
			return true;
		} else if (str1 == null || str2 == null) {
			return false;
		} else {
			return str1.toLowerCase().equals(str2.toLowerCase());
		}
	}

	//Get a user by his username only.
	public User getUserByName(String name) {
		if (iequals(vino.userName, name.trim()))
			return vino;
		for (int i = 0; i < users.length; i++)
			if (iequals(users[i].userName, name.trim()))
				return users[i];
		return null;
	}
	
	//Find a user by his IRC nick. If an IRC nick is found matching the specified nick, any users logged in
	// with that host are returned. Otherwise, any user names matching the specified nick are returned. Note
	// that if someone's occupies a nick but is not logged in as that nick, this function will return null.
	public User getUserByNick(String name) {
		for (int i = 0; i < server.channels.length; i++) {
			for (IRCUser curr = server.channels[i].users; curr != null; curr = curr.next) {
				if (curr.name.equals(name)) {
					return getUserByHost(curr.host);
				}
			}
		}
		return getUserByName(name);
	}
	
	//Guarunteed the person is logged in. Gets a User from a host.
	public User getUserByHost(String host) {
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
	
	public void checkForMessages(String nick, String host, String recipient) {
		//Check if this person has any messages.
		Message currMsg = firstMessage;
		Message lastMsg = null;
		int numberSent = 0;
		User user = getUserByHost(host);
		while (currMsg != null) {
			if (iequals(currMsg.target, nick) || (user != null && user.userName.equalsIgnoreCase(currMsg.target))) {
				if (numberSent == 0) {
					ircio.privmsg(recipient, nick + ", you have messages!");
				} else if (numberSent >= 5) {
					ircio.privmsg(recipient, "You have more messages.");
					break;
				}
				ircio.privmsg(recipient, "Message from " + currMsg.sender + " [" + makeTime(currMsg.time) + " ago]:" + currMsg.message);
				numberSent++;
				if (lastMsg == null)
					firstMessage = currMsg.next;
				else
					lastMsg.next = currMsg.next;
				writeData();
			} else {
				lastMsg = currMsg;
			}
			currMsg = currMsg.next;
		}
	}

	public void messagePrivEmote(String nick, String host, String recipient, String msg) {
		String log;
		
		log = "* " + nick + " " + msg;

		logfile(recipient, log);
		
		msg = msg.trim();

		checkForMessages(nick, host, recipient);

		checkForBlacklist(nick, recipient);
		
		if (System.currentTimeMillis() > nextWho) { //!spam
			nextWho = System.currentTimeMillis() + 5000;
						
			if (iregex("hugs " + name, msg)) {
				if (isVino(host))
					ircio.privemote(recipient, "hugs Vino!");
				else
					ircio.privmsg(recipient, "Get the fuck off.");
			} else if (iregex("pets " + name, msg)) {
				ircio.privemote(recipient, "purrs.");
			}
	
			return;
		}
	}

	public void updateHistory (String nick, String msg) {
		for (int i = this.historySize - 1; i > 0; i--) {
			historyNick[i] = historyNick[i-1];
			historyText[i] = historyText[i-1];
		}
		historyNick[0] = nick;
		historyText[0] = msg;
	}

	public void messagePrivMsg(String nick, String host, String recipient, String msg) {
		boolean pm = false;
		String log;

		if (iequals(recipient, name)) {
			recipient = nick;
			pm = true;
		}

		log = "<" + nick + "> ";
		log += msg.substring(0, msg.length());

		logfile(recipient, log);

		msg = msg.trim();

		checkForMessages(nick, host, recipient);

		checkForBlacklist(nick, recipient);

		updateHistory(nick, msg);
		
		//Say hello!
		int nameEnd = name.length() < 4 ? name.length() : 4;
		if (iregex(name.substring(0, nameEnd), msg)) {
			for (int i = 0; i < hellos.length; i++) {
				if (iregex("^"+hellos[i], msg)) {
					if (System.currentTimeMillis() > nextHi) {	//!spam
						ircio.privmsg(recipient, helloreplies[new Random().nextInt(helloreplies.length)]);
						nextHi = System.currentTimeMillis() + 500;
						return;
					}
				}
			}
		}

		StringTokenizer tok = new StringTokenizer(msg, ",: ");
		String botname;
		if (!pm && tok.hasMoreElements()) {
			botname = tok.nextToken();
		} else {
			botname = "";
		}

		if (pm || talkingToMe(msg)) {

			//Remove the bot's name
			if (!pm)
				msg = msg.substring(msg.indexOf(" ")+1);

			if (iregex("bring out the strapon", msg)) {
				ircio.privemote(recipient, "steps forward with a large strapon and begins mashing potatoes.");
				return;
			}

			//Remove punctuation from the end
			while (msg.endsWith(".") || msg.endsWith("?") || msg.endsWith("!")){
				msg = msg.substring(0, msg.length()-1);
			}

			//BEGIN COLLOQUIAL COMMANDS
			//These commands can be used anywhere if the bot's name is spoken first.
			if (iequals("who are you", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					ircio.privmsg(recipient, "I am an advanced SephiaBot channel bot.");
					ircio.privmsg(recipient, "I'll kick your ass in days that end in 'y'.");
					ircio.privmsg(recipient, "I was written by Vino. Vino rocks.");
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (gamesurge() && iregex("what does marsellus wallace look like", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					ircio.privmsg(recipient, "He's black.");
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("who wrote you", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					ircio.privmsg(recipient, "I was written by Vino. Vino rocks.");
					ircio.privmsg(recipient, "Nilbus helped too.");
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("who is here", msg) && nick.equals("Nilbus")) {
				if (System.currentTimeMillis() > nextWho) {	//!spam

					int channum = channelNumber(recipient);
					if (channum == -1 || pm) {
						ircio.privmsg(recipient, "It's just you and me in a PM, buddy.");
						nextWho = System.currentTimeMillis() + 5000;
						return;
					} else {
						StringBuffer buf = new StringBuffer("Users in this channel:");
						IRCUser current = server.channels[channum].users;
						for (int i = 0; i < server.channels[channum].numusers; i++) {
							buf.append(" " + current.name);
							current = current.next;
						}
						//ircio.privmsg(recipient, buf.toString());
						ircio.privmsg("Nilbus", buf.toString()); //XXX: debug
						nextWho = System.currentTimeMillis() + 5000;
						return;
					}
				}
			} else if (iregex("who is vino", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					ircio.privmsg(recipient, "A dirty cuban.");
					ircio.privmsg(recipient, "And my daddy.");
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("who is remy", msg)
					|| iregex("who is luckyremy",msg)) {
				if (System.currentTimeMillis() > nextWho) {     //!spam
					ircio.privmsg(recipient, "Father of the Black Sheep.");
					ircio.privmsg(recipient, "Harbinger of Doom.");
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("who is", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					User target = getUserByNick(msg.substring(msg.lastIndexOf(' ')+1, msg.length()));
					if (target == null)
						ircio.privmsg(recipient, "Nobody important.");
					else
						ircio.privmsg(recipient, "He's a cool guy.");
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (gamesurge() && iregex("are you (sexy|hot)", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					ircio.privmsg(recipient, "Fuck yes.");
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (gamesurge() && iregex("want to cyber", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					if (!isVino(host)) {
						ircio.privmsg(recipient, "Fuck no.");
					} else {
						ircio.privmsg(recipient, "Take me, " + nick + "!");
					}
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("wh?[aeu]re?('?[sz]| i[sz]| si| be?)( m(a[ih]|y))? vino", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					if (vino.away == null) {
						ircio.privmsg(recipient, "If he's not here, I dunno. He hasn't told me he's gone.");
					} else {
						ircio.privmsg(recipient, "He's " + vino.away + ". He's been gone for " + makeTime(vino.leavetime) + ".");
					}
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("wh?[aeu]re?('?[sz]| i[sz]| si| be?)( m(a[ih]|y))?", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					String targetName = msg.substring(msg.lastIndexOf(' ')+1, msg.length());
					while (targetName.endsWith("!") || targetName.endsWith("?") || targetName.endsWith(","))
						targetName = targetName.substring(0, targetName.length()-1);
					boolean foundAway = false;
					if (iregex("eve?ry(b(o|ud)dy|(1|one?))", targetName)) {
						//Find out where everybody is and tell the channel.
						for (int i = 0; i < users.length; i++) {
							User user = users[i];
							if (user.away != null) {
								ircio.privmsg(recipient, user.userName + " has been " + user.away + " for " + makeTime(user.leavetime) + ".");
								foundAway = true;
							}
						}
						if (vino.away != null) {
							ircio.privmsg(recipient, vino.userName + " has been " + vino.away + " for " + makeTime(vino.leavetime) + ".");
							foundAway = true;
						}
						if (!foundAway)
							ircio.privmsg(recipient, "Everyone is present and accounted for.");
						return;
					}
					if (iequals(targetName, botname))
						return;
					User target = getUserByNick(targetName);
					if (target == null) {
						ircio.privmsg(recipient, "Like I know.");
						return;
					} else
						targetName = target.userName; //use correct caps
					if (target.away == null) {
						ircio.privmsg(recipient, "I don't know.");
					} else {
						ircio.privmsg(recipient, targetName + " is " + target.away + ".  " + targetName + " has been gone for " + makeTime(target.leavetime) + ".");
					}
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("who am i", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					User target = getUserByHost(host);
					if (isVino(host)) {
						ircio.privmsg(recipient, "Daddy!");
						ircio.privemote(recipient, "hugs " + nick + ".");
					} else if (target != null) {
						ircio.privmsg(recipient, "Someone I know.");
					} else {
						ircio.privmsg(recipient, "Nobody important.");
					}
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("who('| i)?s your daddy", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					ircio.privmsg(recipient, "Vino's my daddy, ugh! Spank me again Vino!");
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("knock knock", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					ircio.privmsg(recipient, "Who's there?");
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (gamesurge() && iregex("i suck dick", msg)) {
				if (System.currentTimeMillis() > nextWho) { //!spam
					ircio.privmsg(recipient, "Yeah, we know you do.");
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (gamesurge() && iregex("words of wisdom", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					String phrase = randomPhrase(new File(sephiadir, "wordsofwisdom.txt"));
					if (phrase != null)
						ircio.privmsg(recipient, phrase);
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("roll (the )?dice", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					Random rand = new Random();
					int dice = rand.nextInt(5)+2;
					int sides = rand.nextInt(5)+6;
					ircio.privemote(recipient, "rolls " + dice + "d" + sides + " dice and gets " + (dice*sides+1) + ".");
					if (!isVino(host)) {
						ircio.privemote(recipient, "kills " + nick + ".");
					} else {
						ircio.privemote(recipient, "hugs " + nick + ".");
					}
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("do a little dance", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					ircio.privemote(recipient, "makes a little love.");
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("excuse", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					String excuse = randomPhrase(new File(sephiadir, "excuses.txt"));
					if (excuse != null)
						ircio.privmsg(recipient, "Your excuse is: " + excuse);
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
				
			//BEGIN COMMAND SECTION.
			//These are one-word commands only. The command is the first thing you say after the bot name. botname, command arguments.
			} else if (tok.hasMoreElements()) {
				String cmd = tok.nextToken(" ");
				if (tok.hasMoreElements() && (cmd.startsWith(",") || cmd.startsWith(":"))) { 
					cmd = tok.nextToken(" ");
				}
				if (iequals(cmd, "kill")) {
					if (!tok.hasMoreElements()) {
						ircio.privmsg(recipient, "KILL! KILL! KILL!");
						return;
					}
					String killed = tok.nextToken(" ");
					User killerUser = getUserByHost(host);
					User killedUser = getUserByNick(killed);
					if ((killerUser == null || (killedUser != null && killedUser.memberType > killerUser.memberType)) && !isVino(host)) {
						ircio.privemote(recipient, "giggles at " + nick);
					} else if (iequals(killed, botname)) {
						ircio.privmsg(recipient, ":(");
					} else {
						if (isVino(host))
							killerUser = vino;
						int killedAccess = getAccess(killed, channelNumber(recipient));
						if (killedAccess != -1) {
							ircio.privmsg(recipient, "It would be my pleasure.");
							ircio.kick(recipient, killed, "This kick was compliments of " + killerUser.userName + ". Have a nice day.");
						} else if (killed.equalsIgnoreCase("message")) {
							if (!tok.hasMoreElements()) {
								ircio.privmsg(recipient, "Which message? I need a number");
								return;
							}
							try {
								int msgIndex = Integer.parseInt(tok.nextToken());
								int i = 1;
								Message last = null;
								User user = getUserByHost(host);
								for (Message curr = firstMessage; curr != null; curr = curr.next) {
									if (curr.sender.equals(nick) || (user != null && curr.sender.equals(user.userName))) {
										if (i++ == msgIndex) {
											if (last == null)
												firstMessage = curr.next;
											else
												last.next = curr.next;
											ircio.privmsg(recipient, "Message removed from " + curr.sender + " to " + curr.target + " " + makeTime(curr.time) + " ago:" + curr.message);
											writeData();
											return;
										}
									}
									last = curr;
								}
								ircio.privmsg(recipient, "What message?");
							} catch (NumberFormatException nfe) {
								ircio.privmsg(recipient, "...if you can call that a number.");
							}
							return;
						} else
							ircio.privmsg(recipient, "Kill who what now?"); 
						return;
					}
				} else if (iequals(cmd, "tell")) {
					if (!tok.hasMoreElements()) {
						ircio.privmsg(recipient, "Tell who what?");
						return;
					}
					String target = tok.nextToken(" ");
					while (target.endsWith(".") || target.endsWith("!") || target.endsWith(","))
						target = target.substring(0, target.length()-1);
					//If the target is logged in, send the message to his username instead so he will always get it if he is logged in.
					User user = getUserByNick(target);
					if (user != null)
						target = user.userName;
					if (!tok.hasMoreElements()) {
						ircio.privmsg(recipient, "Tell " + target + " what?");
						return;
					}
					String message = tok.nextToken("");
					//If the target was "everybody" or "everyone" then send the message to every user.
					if (getUserByHost(host) != null && (iequals(target, "everybody") || iequals(target, "everyone"))) {
						for (int i = 0; i < users.length; i++) {
							if (firstMessage == null) {
								firstMessage = new Message(users[i].userName, message, nick);
							} else {
								Message currMsg = firstMessage;
								while (currMsg.next != null)
									currMsg = currMsg.next;
								currMsg.next = new Message(users[i].userName, message, nick);
							}
						}
						//Fall through here! Send one last message to Vino!
						target = "Vino";
					}
					if (firstMessage == null) {
						firstMessage = new Message(target, message, nick);
					} else {
						Message currMsg = firstMessage;
						while (currMsg.next != null)
							currMsg = currMsg.next;
						currMsg.next = new Message(target, message, nick);
					}
					writeData();
					ircio.privmsg(recipient, "OK, I'll make sure to let them know.");
				} else if (iregex("^(butt?)?se(x|cks)$", cmd)) {
					if (!tok.hasMoreElements()) {
						ircio.privemote(recipient, "anally rapes " + nick + ".");
						return;
					}
					String sexed = tok.nextToken(" ");
					if (iregex("^vino", sexed)) {
						ircio.privemote(recipient, "screams as Vino penetrates every orifice of her body!");
					} else if (iequals(botname, sexed)) {
						ircio.privemote(recipient, "furiously works the potato masher!");
					} else {
						int sexedAccess = getAccess(sexed, channelNumber(recipient));
						if (sexedAccess == -1) {
							sexed = nick;
						}
						ircio.privemote(recipient, "anally rapes " + sexed + ".");
					}
				} else if (iequals(cmd, "reboot")) {
					if (isAdmin(host)) {
						ircio.privmsg(recipient, "Be right back.");
						System.exit(1);
					} else
						ircio.privmsg(recipient, "No.");
					return;
				} else if (iequals(cmd, "shutdown")) {
					if (isVino(host)) {
						ircio.privmsg(recipient, "Goodbye everybody!");
						System.exit(0);
					} else {
						ircio.privmsg(recipient, "No.");
					}
					return;
				} else if (iequals(cmd, "reload")) {
					if (isAdmin(host)) {
						parseConfig(config);
						ircio.privmsg(recipient, "Done.");
					} else {
						ircio.privmsg(recipient, "No.");
					}
					return;
				} else if (iequals(cmd, "save")) {
					if (isAdmin(host)) {
						writeData();
						ircio.privmsg(recipient, "Done.");
					} else {
						ircio.privmsg(recipient, "No.");
					}
					return;
				} else if (iequals(cmd, "listhosts")) {
					User user = getUserByHost(host);
					if (user == null)
						return;
					String buffer = "Hosts logged in as " + user.userName + ":";
					for (int i = 0; i < 10; i++)
						if (user.hosts[i] != null && user.hosts[i].length() > 0)
							buffer += " " + (i+1) + ": " + user.hosts[i];
					ircio.privmsg(nick, buffer);
				} else if (iequals(cmd, "logout")) {
					User user = getUserByHost(host);
					if (user == null)
						return;
					if (!tok.hasMoreElements()) {
						for (int i = 0; i < 10; i++) {
							if (user.hosts[i] != null && user.hosts[i].equals(host)) {
								user.hosts[i] = null;
								ircio.privmsg(nick, "It's too bad things couldn't work out.");
								writeData();
								return;
							}
						}
						ircio.privmsg(nick, "Your host is not logged in.");
						return;
					}
					try {
						int i = Integer.parseInt(tok.nextToken("").trim())-1;
						if (i < 0 || i >= 10)
							return;
						if (user.hosts[i] == null || user.hosts[i].length() <= 0)
							ircio.privmsg(nick, "That host is not logged in.");
						else {
							ircio.privmsg(nick, "It's too bad things couldn't work out.");
							user.hosts[i] = null;
						}
					} catch (NumberFormatException nfe) {
					}
					writeData();
				} else if (iequals(cmd, "login")) {
					if (tok.countTokens() < 2) {
						ircio.privmsg(nick, "Yeah. Sure. Whatever.");
						return;
					}
					String login = tok.nextToken(" ").trim();
					String passwd = tok.nextToken("").trim();
					if (login.trim().equals("vino") && passwd.trim().equals("xxxxx")) {
						boolean foundSpot = false;
						int i;
						for (i = 0; i < 10; i++) {
							if (vino.hosts[i] != null && vino.hosts[i].equals(host)) {
								ircio.privmsg(nick, "Silly you. You're already logged in.");
								return;
							}
						}
						for (i = 0; i < 10; i++) {
							if (vino.hosts[i] == null || vino.hosts[i].length() <= 0) {
								vino.hosts[i] = host;
								foundSpot = true;
								break;
							}
						}
						if (!foundSpot)
							ircio.privmsg(nick, "No spots left.");
						else if (iequals(nick, "nilbus"))
							ircio.privmsg(nick, "What's up Nilbus?");
						else
							ircio.privmsg(nick, "Hi, daddy! :D");
						writeData();
					} else if (validateLogin(login, passwd)) {
						boolean foundSpot = false;
						int i, userID = -1;
						for (i = 0; i < users.length; i++)
							if (users[i].userName.equalsIgnoreCase(login))
								userID = i;
						if (userID == -1) {	//WTFException
							ircio.privmsg(nick, "WTF? Tell Vino you saw this.");
							return;
						}
						for (i = 0; i < 10; i++) {
							if (users[userID].hosts[i] != null && users[userID].hosts[i].equals(host)) {
								ircio.privmsg(nick, "Silly you. You're already logged in.");
								return;
							}
						}
						for (i = 0; i < 10; i++) {
							if (users[userID].hosts[i] == null || users[userID].hosts[i].length() <= 0) {
								users[userID].hosts[i] = host;
								foundSpot = true;
								break;
							}
						}
						if (!foundSpot)
							ircio.privmsg(nick, "No spots left.");
						else
							ircio.privmsg(nick, "What's up " + users[userID].userName + "?");
						writeData();
					} else {
						ircio.privmsg(nick, "No cigar.");
						log("Failed login attempt by " + nick + "!" + host + " with " + login + "/" + passwd + ".");
					}
				} else if (iequals(cmd, "i'm")) {
					if (!tok.hasMoreElements()) {
						ircio.privmsg(recipient, "You're what?");
						return;
					}
					User user = getUserByHost(host);
					if (user == null) {
						ircio.privmsg(recipient, "I don't care.");
						return;
					}
					String location = tok.nextToken("").trim();
					if (iregex("^back", location)) {
						if (user.away == null) {
							ircio.privmsg(recipient, "Of course you are honey.");
						} else {
							ircio.privmsg(recipient, "Welcome back! You've been away for " + makeTime(user.leavetime) + ".");
							user.away = null;
						}
					} else {
						ircio.privmsg(recipient, "Have fun!");
						//Remove punctuation from the end
						while (location.endsWith(".") || location.endsWith("!") || location.endsWith(","))
							location = location.substring(0, location.length()-1);
						user.away = location.replaceAll("\"", "'");
						user.leavetime = System.currentTimeMillis();
					}
					writeData();
					return;
				} else if (iequals(cmd, "messages")) {
					User user = getUserByHost(host);
					boolean foundMessage = false;
					int i = 1, lastIndex, firstIndex = 1;
					if (tok.hasMoreElements()) {
						try {
							firstIndex = Integer.parseInt(tok.nextToken());
							if (firstIndex < 1)
								firstIndex = 1;
						} catch (NumberFormatException nfe) {
						}
					}
					lastIndex = firstIndex + 5;
					for (Message curr = firstMessage; curr != null; curr = curr.next) {
						if (curr.sender.equals(nick) || (user != null && curr.sender.equals(user.userName))) {
							if (i >= firstIndex) {
								if (!foundMessage) {
									ircio.privmsg(recipient, "You have sent the following messages:");
									foundMessage = true;
								}
								ircio.privmsg(recipient, "Message " + i + ": To " + curr.target + " " + makeTime(curr.time) + " ago:" + curr.message);
							}
							i++;
						}
						if (i >= lastIndex)
							break;
					}
					if (!foundMessage)
						ircio.privmsg(recipient, "You havent sent any messages.");
				} else if (iequals(cmd, "say")) {
					if (!isAdmin(host)) {
						ircio.privmsg(recipient, "No.");
						return;
					}
					if (!tok.hasMoreElements()) {
						ircio.privmsg(recipient, "Say what?!?");
						return;
					}
					String inchannel = recipient;
					if (iequals("in", tok.nextToken())) {
						inchannel = tok.nextToken();
					}
					ircio.privmsg(inchannel, tok.nextToken("").substring(1));
					return;
				//TODO: Make mode setting colloquial
				} else if (iequals(cmd, "mode")) {
					if (!isAdmin(host)) {
						ircio.privmsg(recipient, "No.");
						return;
					}
					String inchannel = recipient;
					if (!tok.hasMoreElements()) {
						ircio.privmsg(recipient, "What channel?");
						return;
					}
					inchannel = tok.nextToken();
					if (!tok.hasMoreElements()) {
						ircio.privmsg(recipient, "Who?");
						return;
					}
					String who = tok.nextToken();
					String mode;
					if (!tok.hasMoreElements()) {
						ircio.privmsg(recipient, "What mode?");
						return;
					} else mode = tok.nextToken();
					System.out.println("MODE " + inchannel + " " + mode + " " + who + "\n");
					ircio.setMode(who, inchannel, mode);
					return;
				}
			}
		} else if (spelledMyNameWrong(botname)) {
//			ircio.privmsg(recipient, nick + ", " + spell);
		} else {
			//Act like a parrot only if the message isn't a command
			//Only repeat when 2 people said the same thing, but not the 3rd time.
			if (iequals(historyText[0], historyText[1]) && !iequals(historyText[0], this.lastRepeat) && !iequals(historyNick[0], historyNick[1]) && !historyText[0].trim().startsWith("!")) {
				this.lastRepeat = historyText[0];
				ircio.privmsg(recipient, historyText[0]);
			}
		}
		
		//Bot has been mentioned?
		if (iregex(name, msg)) {
			if (gamesurge()) {
				if (iregex("fuck you", msg)) {
					if (System.currentTimeMillis() > nextWho) {	//!spam
						ircio.privmsg(recipient, "Fuck you too, buddy.");
						nextWho = System.currentTimeMillis() + 5000;
						return;
					}
				} else if (iregex("screw you", msg)) {
					if (System.currentTimeMillis() > nextWho) {	//!spam
						ircio.privmsg(recipient, "Screw you too, buddy.");
						nextWho = System.currentTimeMillis() + 5000;
						return;
					}
				} else if (iregex("you suck", msg)) {
					if (System.currentTimeMillis() > nextWho) {	//!spam
						ircio.privmsg(recipient, "I suck, but you swallow, bitch.");
						nextWho = System.currentTimeMillis() + 5000;
						return;
					}
				}
			}
			if (iregex("(thank( ?(yo)?u|[sz])\\b|\\bt(y|hn?x)\\b)", msg)) {
				if (System.currentTimeMillis() > nextWho) { //!spam
					ircio.privmsg(recipient, "No problem.");
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			}
		}

	}

	private boolean validateLogin(String login, String password) {
		for (int i = 0; i < users.length; i++)
			if (users[i].userName.equalsIgnoreCase(login) && users[i].password.equals(password))
				return true;
		return false;
	}
	
	private String randomPhrase(File file) {
		try {
			Vector phrases = new Vector();
			BufferedReader in = new BufferedReader(new FileReader(file));
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

	public boolean isAdmin(String host) {
		User user = getUserByHost(host);
		if (user != null && user.memberType == User.USER_ADMIN)
			return true;
		return false;
	}
	
	public boolean isVino(String host) {
		for (int i = 0; i < 10; i++) {
			if (vino.hosts[i] != null && iequals(host, vino.hosts[i]))
				return true;
		}
		return false;
	}

	public boolean talkingToMe(String msg) {
		int nameEnd = name.length() < 4 ? name.length() : 4;
		return iregex("^"+name.substring(0, nameEnd), msg);
	}

	public boolean spelledMyNameWrong(String msg) {
		int offset = name.compareToIgnoreCase(msg);

		//These are common values. replacing a with 4 gives you -16, etc.
		//63 -16 45
		return (offset == 63 || offset == -16 || offset == 45 || offset == 50 || offset == -19);
	}

	public int getAccess(String user, int channum) {
		if (channum == -1) {
//			log ("chan -1");                      //XXX: debug
			return -1;
		}
		IRCUser current = server.channels[channum].users;
		for (int i = 0; i < server.channels[channum].numusers; i++) {
			if (iequals(user, current.name)) {
//				log (current.name + " access " + current.access); //XXX: debug
				return current.access;
			}
			current = current.next;
		}
//		log(user + " access -1");  //XXX: debug
		return -1;
	}

	public void checkForBlacklist(String nick, String channel) {
		//Check for blacklisted nicks.
		for (int i = 0; i < blacklist.length; i++) {
			if (iequals(nick, blacklist[i])) {
				ircio.privmsg(channel, "!kb " + nick + " You have been blacklisted. Please never return to this channel.");
				return;
			}
		}
	}

	public void messageChannelJoin(String nick, String host, String channel) {

		String log;

		log = "--> " + nick + " has joined " + channel;

		logfile(channel, log);

		//Say something as you enter the channel!
		if (iequals(nick, name)) {
			ircio.privmsg(channel, greeting);
		}

		if (iequals(nick, "metapod\\")) {
			ircio.privmsg(channel, "Heya meta.");
		}

		if (iequals(nick, "luckyremy")) {
			ircio.privemote(channel, "salutes as Remy enters.");
		}

		checkForBlacklist(nick, channel);
		
		int channum = channelNumber(channel);

		if (channum > -1) {
			server.channels[channelNumber(channel)].addUser(nick, host, IRCServer.ACCESS_NONE);
		}

	}

	public void messageChannelPart(String nick, String host, String channel, String message) {

		String log;

		log = "<-- " + nick + " has left " + channel;
		if (message != null) {
			log += " (" + message + ")";
		}

		logfile(channel, log);

	}

	public void messageNickChange(String nick, String host, String newname) {

		String log;

		log = "--- " + nick + " changed his name to " + newname;

		logfile(null, log);

	}

	public void messageModeChange(String nick, String host, String channel, String mode, String recipient) {

		String log;

		log = "--- " + nick + " set mode " + mode + " on " + recipient;

		logfile(channel, log);

		int channum = channelNumber(channel);
		int access;
		if (mode.equalsIgnoreCase("-v") || mode.equalsIgnoreCase("-o")) {
			access = IRCServer.ACCESS_NONE;
			server.channels[channum].addUser(recipient, host, access);
		} else if (mode.equalsIgnoreCase("+o")) {
			access = IRCServer.ACCESS_OP;
			server.channels[channum].addUser(recipient, host, access);
		} else if (mode.equalsIgnoreCase("+v")) {
			access = IRCServer.ACCESS_VOICE;
			server.channels[channum].addUser(recipient, host, access);
		}

	}

	public void messageQuit(String nick, String host, String msg) {

		String log;

		log = "<-- " + nick + " has quit ";
		if (msg != null) {
			log += " (" + msg + ")";
		}

		logfile(null, log);

	}

	public void messageChanList(String channel, String list) {

		int channum = channelNumber(channel);

		StringTokenizer tok = new StringTokenizer(list, " ");
//		int usersInWhois = 0;
//		String userhostString = "";
		
		ircio.who(channel);
		
		while (tok.hasMoreElements()) {
			String user = tok.nextToken();
			int access;
			if (user.startsWith("@")) {
				access = IRCServer.ACCESS_OP;
			} else if (user.startsWith("%")) {
				access = IRCServer.ACCESS_HALFOP;
			} else if (user.startsWith("+")) {
				access = IRCServer.ACCESS_VOICE;
			} else {
				access = IRCServer.ACCESS_NONE;
			}
			if (access > 0) {
				user = user.substring(1);
			}
			server.channels[channum].addUser(user, "", access);
			
//			userhostString += " " + user;
//			if (++usersInWhois == 5) {
//				ircio.userhost(userhostString);
//				usersInWhois = 0;
//				userhostString = "";
//			}
		}
//		if (usersInWhois > 0)
//			ircio.userhost(userhostString);

	}

	public void messageUserHosts(String users) {
		StringTokenizer tok = new StringTokenizer(users, " =");

hostFinder:
		while (tok.hasMoreElements()) {
			String name = tok.nextToken();

			//If no more elements, throw WTFException
			if (!tok.hasMoreElements())
				return;

			String host = tok.nextToken();
			host = host.substring(1, host.length());

			//For every channel, find users that fit this username and assign this host to them.
			for (int i = 0; i < server.channels.length; i++) {
				for (IRCUser curr = server.channels[i].users; curr != null; curr = curr.next) {
					if (curr.name.equals(name)) {
						curr.host = host;
						continue hostFinder;
					}
				}
			}
		}
	}

	public void messageWho(String userchannel, String usernick, String username, String host, String realname) {
		for (int i = 0; i < server.channels.length; i++) {
			if (server.channels[i].name.equalsIgnoreCase(userchannel)) {
				for (IRCUser curr = server.channels[i].users; curr != null; curr = curr.next) {
					if (curr.name.equalsIgnoreCase(usernick)) {
						curr.host = username + "@" + host;
						return;
					}
				}
				return;
			}
		}
	}
	
	public void messageReceived(String msg) {

	}

	public int channelNumber(String channel) {
		for (int channum = 0; channum < channels.length; channum++) {
			if (channel.equalsIgnoreCase(channels[channum])) {
				return channum;
			}
		}
		return -1;
	}

	//Bot system log
	public void log(String log) {
		String orig = log;
		if (syslog == null) { //If the log file hasn't been opened yet
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

	//Channel log
	public void logfile(String recipient, String msg) {
		try {
			Calendar now = Calendar.getInstance();
			int hour = now.get(Calendar.HOUR_OF_DAY);
			int minute = now.get(Calendar.MINUTE);
			int second = now.get(Calendar.SECOND);
			msg = (hour<10?"0":"") + hour + ":" + (minute<10?"0":"") + minute + "." + (second<10?"0":"") + second + " " + msg;

			for (int i = 0; i < channels.length; i++) {
				if (recipient != null && !recipient.equalsIgnoreCase(channels[i])) {
					continue;
				}
				logOut[i].write(msg, 0, msg.length());
				logOut[i].newLine();
				logOut[i].flush();
			}
		} catch (IOException ioe) {
			logerror("Couldn't log to file!");
		}
	}

}

interface IRCListener {

	public void messageReceived(String msg);
	public void messageModeChange(String nick, String host, String channel, String mode, String recipient);
	public void messageNickChange(String nick, String host, String newname);
	public void messageChannelJoin(String nick, String host, String channel);
	public void messageChannelPart(String nick, String host, String channel, String message);
	public void messagePrivMsg(String nick, String host, String recipient, String msg);
	public void messagePrivEmote(String nick, String host, String recipient, String msg);
	public void messageQuit(String nick, String host, String message);

	public void messageChanList(String channel, String list);
	public void messageUserHosts(String users);
	public void messageWho(String userchannel, String usernick, String username, String host, String realname);

	public void logfile(String recipient, String msg);
	public void log(String msg);
	public void logerror(String msg);

}
