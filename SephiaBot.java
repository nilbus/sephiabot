/*
TODO: Reminders
TODO: Allow multiple hosts to log in
TODO: Better handling of dropped connections, etc.
TODO: On JOINs, channel is prefixed with a :. Make sure this is accounted for.
TODO: Track nick changes so who is here works.
*/
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

class Message {
	String target;
	String sender;
	String message;
	long time;
	Message next;

	Message(String target, String message, String sender) {
		this.target = target;
		this.message = message;
		this.sender = sender;
		time = System.currentTimeMillis();
		next = null;
	}

	Message(String target, String message, String sender, long time) {
		this.target = target;
		this.message = message;
		this.sender = sender;
		this.time = time;
		next = null;
	}
}

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

	private Message firstMessage = null;

	private String vinohost;
	private String vinoaway;
	private long vinoleavetime;

	private IRCIO ircio;
	private BufferedWriter logOut[];
	private String syslogBuffer;
	private BufferedWriter syslog;
	private IRCServer server;

	private String dataFileName = "sephiabot.dat";

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
		this.name = "SephiaBot";
		this.network = "irc.us.freenode.net";
		this.port = 6667;
		this.channels = new String[] {"#sephiabot"};
		this.greeting = "Hello, I am %n, the channel bot. You all suck.";
		this.hellos = new String[] {"hello","hi","yo","hey","greetings","konichiwa","hola","sup"};
		this.helloreplies = new String[] {"yo"};
		this.logdir = "/var/log/sephiabot"; //not a very good default unless documented, incase we actually released this someday (haha)
		this.sephiadir = "/var/lib/sephiabot"; //ditto

		this.nextWho = 0;
		this.nextHi = 0;
		
		log("----------------------------------------------------------------------------\nSephiaBot Started!");
		parseConfig(config);
		loadData(dataFileName);

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
		long elapsed = System.currentTimeMillis() - formerTime;
		int second = 1000;
		String away;
		if (elapsed < second*60) {
			away = "about " + elapsed/1000 + " seconds";
		} else if (elapsed < second*60*60) {
			away = "about " + elapsed/1000/60 + " minutes";
		} else if (elapsed < second*60*60*24) {
			away = "about " + elapsed/1000/60/60 + " hours";
		} else if (elapsed < second*60*60*24*7) {
			away = "about " + elapsed/1000/60/60/24 + " days";
		} else {
			away = "more then a week";
		}
		return away;
	}

	void loadData(String filename) {
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
			while (dataFileReader.ready()) {
				String line = dataFileReader.readLine();
				StringTokenizer tok = new StringTokenizer(line, " ");
				if (line.startsWith("//") || !tok.hasMoreElements())
					continue;
				String command = tok.nextToken();
				if (command.equals("vinohost")) {
					this.vinohost = tok.nextToken("").trim();
					log("Loaded vinohost " + this.vinohost);
				} else if (command.equals("vinoaway")) {
					this.vinoaway = tok.nextToken("").trim();
					log("Loaded vinoaway " + this.vinoaway);
				} else if (command.equals("vinoleavetime")) {
					this.vinoleavetime = Long.parseLong(tok.nextToken("").trim());
					log("Loaded vinoleavetime " + this.vinoleavetime);
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
			log(messagesLoaded + " messages loaded, " + messagesThrownOut + " messages thrown out.");
		} catch (IOException ioe) {
			logerror("Couldn't read data file " + filename + ".");
		}
	}

	void writeData(String filename) {
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
		
			if (this.vinohost != null) {
				buffer = "vinohost " + this.vinohost + "\n";
				dataFileWriter.write(buffer, 0, buffer.length());
			}

			if (this.vinoaway != null) {
				buffer = "vinoaway " + this.vinoaway + "\n";
				dataFileWriter.write(buffer, 0, buffer.length());

				buffer = "vinoleavetime " + this.vinoleavetime + "\n";
				dataFileWriter.write(buffer, 0, buffer.length());
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
				} else if (command.equals("logdir")) {
					this.logdir = tok.nextToken("").trim();
					log("logdir changed to " + this.logdir);
				} else if (command.equals("sephiadir")) {
					this.sephiadir = tok.nextToken("").trim();
					log("sephiadir changed to " + this.sephiadir);
				}
			}
		} catch (IOException ioe) {
			logerror("Couldn't read cfg file " + filename + ".");
		}
		
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

	public void messagePrivEmote(String nick, String host, String recipient, String msg) {
		String log;
		
		log = "* " + nick + " " + msg;

		logfile(recipient, log);
		
		msg = msg.trim();

		if (System.currentTimeMillis() > nextWho) { //!spam
			nextWho = System.currentTimeMillis() + 5000;
						
			if (iregex("hugs kali", msg)) {
				if (isVino(host))
					ircio.privemote(recipient, "hugs Vino!");
				else
					ircio.privmsg(recipient, "Get the fuck off.");
			} else if (iregex("pets kali", msg)) {
				ircio.privemote(recipient, "purrs.");
			}
	
			return;
		}
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

		//Check if this person has any messages.
		Message currMsg = firstMessage;
		Message lastMsg = null;
		int numberSent = 0;
		while (currMsg != null) {
			if (iequals(currMsg.target, nick)) {
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
				writeData(dataFileName);
			} else {
				lastMsg = currMsg;
			}
			currMsg = currMsg.next;
		}

		//Bot has been mentioned?
		if (iregex(name, msg) && gamesurge()) {
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
		if (tok.hasMoreElements()) {
			botname = tok.nextToken();
		} else {
			botname = "";
		}

		if (pm || talkingToMe(msg)) {

			//Remove the bot's name
			if (!pm) {
				msg = msg.substring(msg.indexOf(" ")+1);

				if (iregex("bring out the strapon", msg)) {
					ircio.privemote(recipient, "steps forward with a large strapon and begins mashing potatoes.");
					return;
				}
			}

			//Remove punctuation from the end
			while (msg.endsWith(".") || msg.endsWith("?") || msg.endsWith("!")){
				msg = msg.substring(0, msg.length()-1);
			}

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
					ircio.privmsg(recipient, "Nobody important.");
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
			} else if (iregex("where is vino", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					if (vinoaway == null) {
						ircio.privmsg(recipient, "If he's not here, I dunno. He hasn't told me he's gone.");
					} else {
						ircio.privmsg(recipient, "He's " + vinoaway + ". He's been gone for " + makeTime(vinoleavetime) + ".");
					}
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("who am i", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					if (!isVino(host)) {
						ircio.privmsg(recipient, "Nobody important.");
					} else {
						ircio.privmsg(recipient, "Daddy!");
						ircio.privemote(recipient, "hugs " + nick + ".");
					}
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("who('| i)s your daddy", msg)) {
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
					int killerAccess = getAccess(nick, channelNumber(recipient));
					String killed = tok.nextToken(" ");
					int killedAccess = getAccess(killed, channelNumber(recipient));
					if ((killerAccess <= killedAccess || killerAccess == IRCServer.ACCESS_VOICE) && !isVino(host)) {
						ircio.privemote(recipient, "giggles at " + nick);
						return;
					} else if (killerAccess == -1 || killedAccess == -1) {
						ircio.privemote(recipient, "laughs, yeah right.");
						return;
					} else {
						ircio.privmsg(recipient, "It would be my pleasure.");
						ircio.kick(recipient, killed, "You have been bitched by " + name + ". Have a nice day.");
						return;
					}
				} else if (iequals(cmd, "tell")) {
					if (!tok.hasMoreElements()) {
						ircio.privmsg(recipient, "Tell who what?");
						return;
					}
					String target = tok.nextToken(" ");
					if (!tok.hasMoreElements()) {
						ircio.privmsg(recipient, "Tell " + target + " what?");
						return;
					}
					String message = tok.nextToken("");
					if (firstMessage == null) {
						firstMessage = new Message(target, message, nick);
					} else {
						currMsg = firstMessage;
						while (currMsg.next != null)
							currMsg = currMsg.next;
						currMsg.next = new Message(target, message, nick);
					}
					writeData(dataFileName);
					ircio.privmsg(recipient, "OK, I'll make sure to let them know.");
				} else if (iregex("^(butt?)?se(x|cks)$", cmd)) {
					if (!tok.hasMoreElements()) {
						ircio.privemote(recipient, "anally rapes " + nick + ".");
						return;
					}
					String sexed = tok.nextToken(" ");
					if (iequals("vino", sexed)) {
						ircio.privemote(recipient, "screams as Vino penetrates every orifice of her body!");
					} else {
						int sexedAccess = getAccess(sexed, channelNumber(recipient));
						if (sexedAccess == -1) {
							ircio.privemote(recipient, "I'd love to, but who the hell is that?");
						} else {
							ircio.privemote(recipient, "anally rapes " + sexed + ".");
						}
					}
				} else if (iequals(cmd, "reboot")) {
					if (isVino(host)) {
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
				} else if (iequals(cmd, "login")) {
					if (tok.countTokens() < 1) {
						ircio.privmsg(nick, "Yeah. Sure. Whatever.");
						return;
					}
					String passwd = tok.nextToken("");
					if (passwd.trim().equals("xxxxx")) {
						ircio.privmsg(nick, "Hi, daddy! :D");
						vinohost = host;
						writeData(dataFileName);
					} else {
						ircio.privmsg(nick, "You aint fuckin Vino, prick.");
						return;
					}
				} else if (iequals(cmd, "i'm")) {
					if (!tok.hasMoreElements()) {
						ircio.privmsg(recipient, "You're what?");
						return;
					}
					if (!isVino(host)) {
						ircio.privmsg(recipient, "I don't care.");
						return;
					}
					String location = tok.nextToken("").trim();
					if (iequals(location, "back")) {
						if (vinoaway == null) {
							ircio.privmsg(recipient, "Of course you are honey.");
						} else {
							ircio.privmsg(recipient, "Welcome back! You've been away for " + makeTime(vinoleavetime) + ".");
							vinoaway = null;
						}
					} else {
						ircio.privmsg(recipient, "Have fun!");
						vinoaway = location;
						vinoleavetime = System.currentTimeMillis();
					}
					writeData(dataFileName);
					return;
				} else if (iequals(cmd, "say")) {
					if (!isVino(host)) {
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
					if (!isVino(host)) {
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
				} else if (iregex("excuse", msg)) {
					if (System.currentTimeMillis() > nextWho) {	//!spam
						String excuse = randomPhrase(new File(sephiadir, "excuses.txt"));
						if (excuse != null)
							ircio.privmsg(recipient, "Your excuse is: " + excuse);
						nextWho = System.currentTimeMillis() + 5000;
						return;
					}
				}
			}
		} else if (spelledMyNameWrong(botname)) {
//			ircio.privmsg(recipient, nick + ", " + spell);
		}

	}

	private String randomPhrase(File file) {
		try {
			Vector phrases = new Vector();
			BufferedReader in = new BufferedReader(new FileReader(file));
			String line;
			while ((line = in.readLine()) != null)
				phrases.add(line);
			in.close();
			if (phrases.size() == 0)
				return null;
			Random rnd = new Random();
			return (String)phrases.get(rnd.nextInt(phrases.size()));
		} catch (IOException ioe) {
			logerror("Couldn't open excuse file: " + ioe.getMessage());
			return null;
		}
	}

	public boolean isVino(String host) {
		return (iequals(host, vinohost));
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
			log ("chan -1");                      //XXX: debug
			return -1;
		}
		IRCUser current = server.channels[channum].users;
		for (int i = 0; i < server.channels[channum].numusers; i++) {
			if (iequals(user, current.name)) {
				log (current.name + " access " + current.access); //XXX: debug
				return current.access;
			}
			current = current.next;
		}
		log(user + " access -1");  //XXX: debug
		return -1;
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

		int channum = channelNumber(channel);

		if (channum > -1) {
			server.channels[channelNumber(channel)].addUser(nick, IRCServer.ACCESS_NONE);
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
			server.channels[channum].addUser(recipient, access);
		} else if (mode.equalsIgnoreCase("+o")) {
			access = IRCServer.ACCESS_OP;
			server.channels[channum].addUser(recipient, access);
		} else if (mode.equalsIgnoreCase("+v")) {
			access = IRCServer.ACCESS_VOICE;
			server.channels[channum].addUser(recipient, access);
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
			server.channels[channum].addUser(user, access);
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

class IRCIO {

	private Socket socket;
	private IRCListener listener;
	private String network;
	private String name;

	private BufferedReader in;
	private BufferedWriter out;

	private boolean registered = false;

	public IRCIO(IRCListener listener, String network, int port) {

		this.listener = listener;
		this.network = network;

		try {
			socket = new Socket(network, port);
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
		} catch (UnknownHostException uhe) {
			listener.logerror("Unknown host.");
			System.exit(1);
		} catch (IOException ioe) {
			listener.logerror("IO Exception trying to connect to server.");
			System.exit(1);
		}


	}

	public void login(String[] channels, String name) {

		this.name = name;

		try {

			String msg = "NICK " + name + "\n";
			out.write(msg, 0, msg.length());
			listener.log(msg);

			msg = "USER sephiabot localhost " + network + " :" + name + "\n";
			out.write(msg, 0, msg.length());
			listener.log(msg);

			out.flush();

			while (!registered) {
				poll();
			}

			msg = "PRIVMSG AuthServ@Services.GameSurge.net :auth Sephia xxxxx\n";
			out.write(msg, 0, msg.length());
			listener.log(msg);

//			msg = "MODE " + name + " +x\n";
//			out.write(msg, 0, msg.length());
//			listener.log(msg);

			for (int i = 0; i < channels.length; i++) {
				msg = "JOIN " + channels[i] + "\n";
				out.write(msg, 0, msg.length());
				listener.log(msg);
			}

			out.flush();

		} catch (IOException ioe) {
			listener.logerror("Couldn't write out.");
		}
	}

	void poll() {
		String msg;
		try {
			while (in.ready()) {
				msg = in.readLine();
				if (!pong(msg)) {
					decipherMessage(msg);
				}
				if (msg.indexOf("001") != -1) {
					registered = true;
				}
			}
		} catch (IOException ioe) {
			listener.logerror("Couldn't poll for input.");
		}
	}

	boolean pong(String msg) {
		if (msg.indexOf("PING") == 0) {
			String outmsg = "PONG :" + msg.substring(msg.indexOf(":")+1) + "\n";
			try {
				out.write(outmsg);
				out.flush();
			} catch (IOException ioe) {
				listener.logerror("Couldn't respond to a ping.");
			}
			return true;
		} else {
			return false;
		}
	}

	void decipherMessage(String msg) {

		listener.log(msg);

		String nick;
		String host;
		String recipient;

		StringTokenizer tok = new StringTokenizer(msg, " ");

		String buf = tok.nextToken();
		if (buf.indexOf("!") != -1) {
			nick = buf.substring(1, buf.indexOf("!"));
			host = buf.substring(buf.indexOf("!")+1);
		} else {
			host = buf;
			nick = "";
		}

		buf = tok.nextToken();

		try {
			int command = Integer.parseInt(buf);
			switch (command) {
			case 353:
//:Extremity.CA.US.GamesNET.net 353 Robyn = #1973 :Robyn Max\DAd @Tempyst LittleMe @MechanicalGhost Mez` @Vino sada^game GOaT @Weine|Away @ChanServ shinobiwan @Yukie WillSchnevel +Feixeno

				String channel = msg.substring(msg.indexOf("=") + 2, msg.indexOf(" ", msg.indexOf("=") + 2));
				String list = msg.substring(msg.indexOf(":", 1)+1);
				listener.messageChanList(channel, list);
				return;

			default:
			}
			
		} catch (NumberFormatException nfe) {
			
		}

		if (buf.equals("PRIVMSG")) {
			recipient = tok.nextToken();
			String chat = tok.nextToken("");
			chat = chat.substring(2);
			if (chat.startsWith("\u0001ACTION ") && chat.endsWith("\u0001"))
				listener.messagePrivEmote(nick, host, recipient, chat.substring(8, chat.length()-1));
			else
				listener.messagePrivMsg(nick, host, recipient, chat);
			return;
		} else if (buf.equals("NICK")) {
			String newname = tok.nextToken("");
			newname = newname.substring(2);
			listener.messageNickChange(nick, host, newname);
			return;
		} else if (buf.equals("MODE")) {
			recipient = tok.nextToken();
			String mode = tok.nextToken().trim();
			String victim;
			if (tok.hasMoreElements()) {
				victim = tok.nextToken("").trim();
			} else {
				victim = null;
			}
			if (mode.startsWith(":")) {
				mode = mode.substring(1);
			}
			listener.messageModeChange(nick, host, recipient, mode, victim);
			return;
		} else if (buf.equals("JOIN")) {
			String channel = tok.nextToken("");
			channel = channel.substring(1);
			if (channel.startsWith(":"))
				channel = channel.substring(1);
			listener.messageChannelJoin(nick, host, channel);
			return;
		} else if (buf.equals("PART")) {
			String channel = tok.nextToken();
			String message;
			if (tok.hasMoreTokens()) {
				message = tok.nextToken("");
				message = message.substring(2);
			} else {
				message = null;
			}
			listener.messageChannelPart(nick, host, channel, message);
			return;
		} else if (buf.equals("QUIT")) {
			String message = tok.nextToken("");
			message = message.substring(2);
			listener.messageQuit(nick, host, message);
			return;
		}

		//Still can't figure out what it is.
		listener.messageReceived(msg);
	}

	public void kick(String recipient, String user, String msg) {
		
		String buf = "KICK " + recipient + " " + user;
		if (msg != null) {
			buf += " :" + msg;
		}
		buf += "\n";

		try {
			out.write(buf, 0, buf.length());
			out.flush();
		} catch (IOException ioe) {
		}

	}

	public void privmsg(String recipient, String msg) {
		String buf = "PRIVMSG " + recipient + " :" + msg + "\n";

		try {
			out.write(buf, 0, buf.length());
			out.flush();
		} catch (IOException ioe) {
		}

		System.out.println(buf);

		String log;
		if (msg.indexOf("ACTION") == 1) {
			log = "* " + name + " ";
			log += msg.substring(8, msg.length()-1);
		} else {
			log = "<" + name + "> ";
			log += msg.substring(0, msg.length());
		}

		listener.logfile(recipient, log);
		
	}
	
	public void privemote(String recipient, String msg) {
		String buf = "PRIVMSG " + recipient + " :\u0001ACTION " + msg + "\u0001\n";

		try {
			out.write(buf, 0, buf.length());
			out.flush();
		} catch (IOException ioe) {
		}

		System.out.println(buf);

		String log;
		log = "* " + name + " " + msg;

		listener.logfile(recipient, log);
		
	}

	public void setMode(String recipient, String inchannel, String mode) {
		try {
	String msg = "MODE " + inchannel + " " + mode + " " + recipient + "\n";
	out.write(msg, 0, msg.length());
				out.flush();
			System.out.println(msg);
		} catch (IOException e) {System.out.println(e.getMessage());}
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

	public void logfile(String recipient, String msg);
	public void log(String msg);
	public void logerror(String msg);

}
