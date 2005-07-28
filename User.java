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

class User {
	String userName;
	String aliases[];
	String hosts[];
	long lastSeenTimes[];
	String away;
	String password;
	String description;
	long leaveTime;
	long lastTalked;
	IRCChannel lastChannel = null;

	int memberType = USER_NOBODY;

	static final int USER_NOBODY = 0;
	static final int USER_MEMBER = 1;
	static final int USER_ADMIN = 2;
	
	User(String userName, String password, int memberType) {
		this.userName = userName;
		this.password = password;
		this.memberType = memberType;
		this.hosts = new String[10];
		this.lastSeenTimes = new long[10];
		this.description = "A person.";
		this.aliases = new String[0];
	}
}
