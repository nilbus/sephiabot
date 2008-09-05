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

class IRCChannel {

	private int historySize;
	private String historyNick[];
	private String historyText[];
	private String lastRepeat;
  private long lastActivity;
  private final long BORED_DELAY = 4 * 1000 * 60 * 60; // 4 hours
  //private final long BORED_DELAY = 1000 * 5; // 5s

	String name;
	IRCUser users;
	IRCServer myServer;

	IRCChannel(String name) {
		this.historySize = 3;
		this.historyNick = new String[this.historySize];
		this.historyText = new String[this.historySize];

		this.name = name;
	}

	void addUser(String user, String host, int access) {

		if (users == null) {
			users = new IRCUser(user, host, access);
			return;
		}

		IRCUser curr = users;
		while (true) {
			if (SephiaBotData.iequals(curr.name, user)) {
				curr.access = access;
				return;
			}
			if (curr.next == null) {
				curr.next = new IRCUser(user, host, access);
				return;
			}
			curr = curr.next;
		}
	}

	//do nothing if user was not found
	void deleteUser(String user) {

		if (users == null) {
			return;
		}

		if (users.next == null) {
			if (SephiaBotData.iequals(users.name, user)) {
				users = null;
			}
			return;
		}

		IRCUser curr = users;
		IRCUser last = null;
		while (curr != null) {
			if (SephiaBotData.iequals(curr.name, user)) {
				if (last == null) //first node
					users = curr.next;
				else
					last.next = curr.next;
				return;
			}
			last = curr;
			curr = curr.next;
		}
	}

	IRCUser getUser(String name) {
		for (IRCUser curr = users; curr != null; curr = curr.next)
			if (SephiaBotData.iequals(name, curr.name))
				return curr;
		return null;
	}
	
	void updateUser(String oldname, String newname, String host, int access) {
		IRCUser user = getUser(oldname);
		if (user != null) {
			if (newname != null)
				user.name = newname;
			if (host != null)
				user.host = host;
			if (access != IRCServer.ACCESS_UNKNOWN)
				user.access = access;
		}
	}

	//Check if the nick or user or its aliases are in this channel
	boolean userInChannel(User user, String nick) {
		nick = nick.trim();
		for (IRCUser ircUser = users; ircUser != null; ircUser = ircUser.next){
			if (SephiaBotData.iequals(ircUser.name, nick))
				return true;
			if (user != null)
				for (int j = 0; j < user.aliases.length; j++)
					if (SephiaBotData.iequals(user.aliases[j], nick))
						return true;
		}
		return false;
	}

	public boolean parrotOK() {
		//Act like a parrot only if the message isn't a command
		//Only repeat when 2 people said the same thing, but not the 3rd time.
		if (SephiaBotData.iequals(historyText[0], historyText[1])
			&& !SephiaBotData.iequals(historyText[0], this.lastRepeat)
			&& !SephiaBotData.iequals(historyNick[0], historyNick[1])
			&& !historyText[0].trim().startsWith("!"))
			return true;
		else
			return false;
	}

	public void updateHistory (String nick, String msg) {
		for (int i = this.historySize - 1; i > 0; i--) {
			historyNick[i] = historyNick[i-1];
			historyText[i] = historyText[i-1];
		}
		historyNick[0] = nick;
		historyText[0] = msg;
	}

	public String getHistory(int i) {
		return historyText[i];
	}

	public void setLastRepeat(String lastRepeat) {
		this.lastRepeat = lastRepeat;
	}

  public void timedEvents() {
    if (lastActivity == 0)
      lastActivity = System.currentTimeMillis();
    else {
      long now = System.currentTimeMillis();
      if (now > lastActivity + BORED_DELAY) {
        lastActivity = now;
        IRCIO io = myServer.myConnection.getIRCIO();
        SephiaBotData data = ((SephiaBot)myServer.myConnection.getListener()).getData();
        String icebreaker = data.randomPhrase("icebreakers.txt");
        if (icebreaker != null) {
          char action = icebreaker.charAt(0);
          icebreaker = icebreaker.substring(2);
          if (action == '"')
            io.privmsg(name, icebreaker);
          else if (action == '.')
            io.privemote(name, icebreaker);
          else
            data.log("Invalid entry in icebreakers: " + icebreaker);
        } else {
          data.log("Could not open icebreakers.txt");
        }
      }
    }
  }
}
