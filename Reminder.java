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

class Reminder {
	String target;
	String sender;
	String message;
	long timeSent;
	long timeToArrive;
	long timeNotified;
	String timeExpression;
	String originalTimeExpression;
	Reminder next;
	boolean notified;

	//This constructor will figure out the timeToArrive itself, or throw a WTFException
	Reminder(String target, String message, String sender) throws WTFException {
		System.out.println("Message: '"+message+"'\n");
		this.target = target;
		this.sender = sender;
		this.timeSent = System.currentTimeMillis();
		this.notified = false;
		ParseTime pt = new ParseTime();
		this.timeToArrive = pt.textToTime(message);
		this.timeExpression = pt.getTimeExpression();
		this.originalTimeExpression = pt.getOriginalTimeExpression();
		this.message = message.replaceAll(this.originalTimeExpression, "").trim().
				replaceAll("^(that|to)", "").trim();
		this.next = null;
	}

	Reminder(String target, String message, String sender, long timeToArrive) {
		this.target = target;
		this.message = message;
		this.sender = sender;
		this.timeSent = System.currentTimeMillis();
		this.timeToArrive = timeToArrive;
		this.notified = false;
		this.next = null;
	}

	Reminder(String target, String message, String sender, boolean notified, long timeToArrive, long timeSent) {
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
