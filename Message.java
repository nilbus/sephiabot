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

class Message {
	String target;
	String sender;
	String message;
	long timeSent;
	long timeToArrive;
	long timeNotified;
	String timeExpression;
	String originalTimeExpression;
	Message next;
	boolean notified;

	//This constructor will figure out the timeToArrive itself, or throw a WTFException
	Message(String target, String message, String sender) {
		System.out.println("Message: '"+message+"'\n");
		this.target = target;
		this.sender = sender;
		this.message = message;
		this.timeSent = System.currentTimeMillis();
		this.notified = false;
		ParseTime pt = new ParseTime();
		try {
			this.timeToArrive = pt.textToTime(message);
			this.timeExpression = pt.getTimeExpression();
			this.originalTimeExpression = pt.getOriginalTimeExpression();
		} catch (WTFException e) {
			// This is a message with no time expression
			this.timeToArrive = 0;
			if (SephiaBotData.iequals(target, sender))
				this.timeExpression = "when you get back";
			else
				this.timeExpression = "when they get back";
			// No need to strip out a time expression, unless we got here because of the "when I get back" case
			if (pt.getOriginalTimeExpression() != null &&
					SephiaBotData.iregex(pt.onReturn, pt.getOriginalTimeExpression()))
				this.originalTimeExpression = pt.getOriginalTimeExpression();
			else
				this.originalTimeExpression = null;
			this.notified = true;
		}
		this.message = message.trim().replaceFirst("^ *(that|to|about) ", "").trim();
		if (this.originalTimeExpression != null)
			this.message = message.replaceAll(this.originalTimeExpression, "");
		this.next = null;
	}

	Message(String target, String message, String sender, long timeToArrive) {
		this.target = target;
		this.message = message;
		this.sender = sender;
		this.timeSent = System.currentTimeMillis();
		this.timeToArrive = timeToArrive;
		this.notified = false;
		this.next = null;
	}

	Message(String target, String message, String sender, boolean notified, long timeToArrive, long timeSent) {
		this.target = target;
		this.message = message;
		this.sender = sender;
		this.timeSent = timeSent;
		this.timeToArrive = timeToArrive;
		this.notified = notified;
		this.next = null;
	}

	public String getOriginalTimeExpression() {
		return originalTimeExpression;
	}

	public String getTimeExpression() {
		return timeExpression;
	}
}
