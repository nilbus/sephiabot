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

// This needs a better name
interface IRCConnectionListener {

	public void messageReceived(IRCConnection con, String msg);
	public void messageModeChange(IRCConnection con, String nick, String host, String channelName, String mode, String recipient);
	public void messageNickChange(IRCConnection con, String nick, String host, String newname);
	public void messageChannelJoin(IRCConnection con, String nick, String host, String channelName);
	public void messageChannelPart(IRCConnection con, String nick, String host, String channelName, String message, boolean kick);
	public void messagePrivMsg(IRCConnection con, String nick, String host, String recipient, String msg);
	public void messagePrivEmote(IRCConnection con, String nick, String host, String recipient, String msg);
	public void messageQuit(IRCConnection con, String nick, String host, String message);

	public void messageChanList(IRCConnection con, String channelName, String list);
	public void messageUserHosts(IRCConnection con, String users);
	public void messageWho(IRCConnection con, String userchannel, String usernick, String username, String host, String realname);

	public void log(String msg);
	public void logerror(String msg);

	public String getLogdir();
}
