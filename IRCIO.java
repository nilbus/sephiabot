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

import java.net.*;
import java.io.*;
import java.util.*;

class IRCIO {

	private Socket socket;
	private IRCListener listener; // IRCConnection
	private String network;
	private int port;
	private String channels[];
	private String name;

	private BufferedReader in;
	private BufferedWriter out;

	private static final long TIMEOUT = 5*60*1000;	//5 minutes

	private boolean connected = false;
	private boolean registered = false;
	private long lastMessage = 0;
	private long lastPing = 0;
	
	public String getName() {return name;}
	public void setName(String newName) {name = newName;}

	public String getNetwork() {return network;}
	public String[] getChannels() {return channels;}

	public IRCIO(IRCListener listener, String network, int port) throws IOException {

		this.listener = listener;
		this.network = network;
		this.port = port;

		connect();
	}

	public void disconnect() {
		try {
			socket.close();
		} catch (IOException ioe) {}
		try {
			in.close();
		} catch (IOException ioe) {}
		try {
			out.close();
		} catch (IOException ioe) {}
		connected = false;
	}

	public void quit(String quitMessage) {
		if (!connected) return;
		
		try {
			String msg = "QUIT";
			if (quitMessage != null)
				msg += " :" + quitMessage;
			msg += "\n";
			out.write(msg, 0, msg.length());
			out.flush();
			System.out.println(msg);
			disconnect();
		} catch (IOException e) {System.out.println(e.getMessage());}
	}
	
	public boolean isConnected() {
		return connected;
	}
	
	void connect() throws IOException {
		connected = false;
		registered = false;
		listener.log("Connecting to " + network + " on port " + port);
		if (socket != null)
			socket.close();
		socket = new Socket(network, port);
		if (in != null)
			in.close();
		in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		if (out != null)
			out.close();
		out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
		connected = true;
		lastMessage = 0;
		lastPing = 0;
		listener.log("Connection complete.");
	}

	public void login(String[] channels, String name) throws IOException {
		if (!connected) return;
		
		this.name = name;
		this.channels = channels;
		login();
	}

	void login() throws IOException {
		if (!connected) return;
		
		String msg = "NICK " + name + "\n";
		out.write(msg, 0, msg.length());
		listener.log(msg);
		poll(); //Make sure we got a nick
		
		msg = "USER sephiabot localhost " + network + " :" + name + "\n";
		out.write(msg, 0, msg.length());
		listener.log(msg);
		out.flush();
		
		for (int i = 0; i < 100; i++) {   // wait at least 10 sec before giving up 
			if (registered)
				break;
			poll();
			try {
				Thread.sleep(100);
			} catch (InterruptedException ie) {}
		}
		if (!registered)
			throw new SocketTimeoutException();
		
		//msg = "PRIVMSG AuthServ@Services.GameSurge.net :auth Sephia xxxxx\n";
		msg = "PRIVMSG nickserv :id mnms\n";
		out.write(msg, 0, msg.length());
		listener.log(msg);
		
		//msg = "MODE " + name + " +x\n";
		//out.write(msg, 0, msg.length());
		//listener.log(msg);
		
		for (int i = 0; i < channels.length; i++) {
			msg = "JOIN " + channels[i] + "\n";
			out.write(msg, 0, msg.length());
			listener.log(msg);
		}
		
		out.flush();
	}

	void poll() throws IOException {
		if (!connected) return;
		
		String msg;
		while (in.ready()) {
			msg = in.readLine();
			lastMessage = System.currentTimeMillis();
			lastPing = 0;
			if (!pong(msg)) {
				decipherMessage(msg);
			}
			if (msg.indexOf("001") != -1) {
				registered = true;
			}
		}
		if (lastPing != 0 && System.currentTimeMillis() - lastPing > TIMEOUT) {
			listener.logerror("PING timeout.");
			throw new SocketTimeoutException("Ping Timeout");
		} else if (registered && lastPing == 0 && System.currentTimeMillis() - lastMessage > TIMEOUT) {
			lastPing = System.currentTimeMillis();
			String outmsg = "PING " + lastPing + "\n";
			out.write(outmsg);
			out.flush();
		}

    listener.server.timedEvents();
	}

	boolean pong(String msg) throws IOException {
		if (!connected) return false;
		
		if (msg.indexOf("PING") == 0) {
			String outmsg = "PONG :" + msg.substring(msg.indexOf(":")+1) + "\n";
			out.write(outmsg);
			out.flush();
			return true;
		} else {
			return false;
		}
	}

	void decipherMessage(String msg) throws IOException {
		if (!connected) return;
		
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

		buf = tok.nextToken(); //COMMAND

		try {
			int command = Integer.parseInt(buf);
			switch (command) {

			case 302:
//:Prothid.CA.US.GameSurge.net 302 Kali :Kali=+~sephiabot@adsl-221-75-195.rmo.bellsouth.net bigguy=+~bigguy@rdu163-63-180.nc.rr.com Thearc=+Sakura_Mus@047.162-78-65.ftth.swbr.surewest.net Vino=+~Vino@adsl-221-75-195.rmo.bellsouth.net Eke=+way2ez48bi@Ek3.user.gamesurge
				String users = msg.substring(msg.indexOf(":", 1)+1);
				listener.messageUserHosts(users);
				return;



//:ClanShells.DE.EU.GameSurge.net 352 Vino #tsbeta ~Vino adsl-221-57-217.rmo.bellsouth.net *.GameSurge.net Vino H :0 Jorge L. Rodriguez
			case 352:
				tok.nextToken();							//Bot's name
				String userchannel = tok.nextToken();		//Channel
				String username = tok.nextToken();			//User name
				String userhost = tok.nextToken();			//User host
				tok.nextToken();							//*.gamesurge.net ?
				String usernick = tok.nextToken();			//User nick
				tok.nextToken();							//H !
				tok.nextToken();							//: and then server hop level
				String realname = tok.nextToken("").trim();	//User's real name
				listener.messageWho(userchannel, usernick, username, userhost, realname);
				return;
				
			case 353:
//:Extremity.CA.US.GamesNET.net 353 Robyn = #1973 :Robyn Max\DAd @Tempyst LittleMe @MechanicalGhost Mez` @Vino sada^game GOaT @Weine|Away @ChanServ shinobiwan @Yukie WillSchnevel +Feixeno
				int secondColon = msg.indexOf(':', 1);
				String channel = msg.substring(msg.lastIndexOf(' ', secondColon-2)+1, secondColon-1);
				String list = msg.substring(secondColon+1);
				listener.messageChanList(channel, list);
				return;
				
			case 433:
//:NetFire.TX.US.GameSurge.net 433 * Kali :Nickname is already in use.
				tok.nextToken(); //discard my current nick
				String takenName = tok.nextToken();
				name = takenName + "-";
				msg = "NICK " + name + "\n";
				out.write(msg, 0, msg.length());
				listener.log(msg);
				out.flush();
				poll(); //Try again
				return;
				
			default:
			}
			
		} catch (NumberFormatException nfe) {
			
		}

//:david_J!n=david@cpe-70-123-166-72.hot.res.rr.com PRIVMSG #ubuntu :+marnanel: odd.. 
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
		} else if (buf.equals("KICK")) {
			String channel = tok.nextToken();
			recipient = tok.nextToken();
			String message;
			if (tok.hasMoreTokens()) {
				message = tok.nextToken("");
				message = message.substring(2);
			} else {
				message = null;
			}
			listener.messageChannelPart(recipient, host, channel, message);
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
		} else if (buf.equals("PONG")) {
			lastPing = 0;
			return;
		}

		//Still can't figure out what it is.
		listener.messageReceived(msg);
	}

	public void kick(String recipient, String user, String msg) {
		if (!connected) return;
		
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

	public void ban(String recipient, String nick, String host) {
		if (!connected) return;
		
		setMode(nick + " *!*@" + host, recipient, "-o+b");
	}
	
	public void privmsg(String recipient, String msg) {
		if (!connected) return;
		
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
		if (!connected) return;
		
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
		if (!connected) return;
		
		String buf = "USERHOST " + target + "\n";
		
		try {
			out.write(buf, 0, buf.length());
			out.flush();
		} catch (IOException ioe) {
		}

		listener.log(buf);
	}

	public void who(String target) {
		if (!connected) return;
		
		String buf = "WHO " + target + "\n";
		
		try {
			out.write(buf, 0, buf.length());
			out.flush();
		} catch (IOException ioe) {
		}

		listener.log(buf);
	}
	
	public void setMode(String recipient, String inchannel, String mode) {
		if (!connected) return;
		
		try {
			String msg = "MODE " + inchannel + " " + mode + " " + recipient + "\n";
			out.write(msg, 0, msg.length());
			out.flush();
			System.out.println(msg);
		} catch (IOException e) {System.out.println(e.getMessage());}
	}

	public void changeNick(String name) throws IOException {
		String msg = "NICK " + name + "\n";
		out.write(msg, 0, msg.length());
		out.flush();
		listener.log(msg);
	}

}
