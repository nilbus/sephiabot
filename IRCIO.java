import java.net.*;
import java.io.*;
import java.util.*;

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

			case 302:
//:Prothid.CA.US.GameSurge.net 302 Kali :Kali=+~sephiabot@adsl-221-75-195.rmo.bellsouth.net bigguy=+~bigguy@rdu163-63-180.nc.rr.com Thearc=+Sakura_Mus@047.162-78-65.ftth.swbr.surewest.net Vino=+~Vino@adsl-221-75-195.rmo.bellsouth.net Eke=+way2ez48bi@Ek3.user.gamesurge
				String users = msg.substring(msg.indexOf(":", 1)+1);
				listener.messageUserHosts(users);
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

		listener.log(buf);

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

		listener.log(buf);

		String log;
		log = "* " + name + " " + msg;

		listener.logfile(recipient, log);
		
	}

	public void userhost(String target) {
		String buf = "USERHOST " + target + "\n";
		
		try {
			out.write(buf, 0, buf.length());
			out.flush();
		} catch (IOException ioe) {
		}

		listener.log(buf);
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
