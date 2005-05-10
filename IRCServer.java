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

class IRCServer {

	String network;
	int port;
	IRCChannel channels[];
	IRCConnection myConnection = null;

	public static int ACCESS_UNKNOWN = -1;
	public static int ACCESS_NONE = 0;
	public static int ACCESS_VOICE = 1;
	public static int ACCESS_HALFOP = 2;
	public static int ACCESS_OP = 3;

	IRCServer(String network, int port, String[] channels, IRCConnection con) {
	this.myConnection = con;
		this.network = network;
		this.port = port;
		this.channels = new IRCChannel[channels.length];
		// It is important to maintain the order of the channels here,
		// because the positions of the channels are also their index when
		// calling into SephiaBotData (if that made any sense.)
		for (int i = 0; i < channels.length; i++) {
			this.channels[i] = new IRCChannel(channels[i]);
		this.channels[i].myServer = this;
		}
	}

	int getChannelIndex(String channel) {
		for (int i = 0; i < channels.length; i++)
			if (SephiaBotData.iequals(channels[i].name, channel))
				return i;
		return -1;
	}

	IRCChannel findChannel(String channel) {
		if (!channel.startsWith("#"))
			channel = "#" + channel;
		for (int i = 0; i < channels.length; i++)
			if (SephiaBotData.iequals(channels[i].name, channel))
				return channels[i];
		return null;
	}
}
