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

import java.util.regex.*;
import java.util.*;

public class ParseTime {
	public static String ones = "(one|two|three|four|five|six|seven|eight|nine)";
	public static String teens = "(ten|eleven|twelve|(thir|four|fif|six|seven|eigh|nine)teen)";
	public static String tens = "(twenty|thirty|fou?rty|fifty|sixty|seventy|eighty|ninety)[- ]?";
	public static String words = "(a (few|couple)( of)?)";
	public static String numberWords = "("+tens+"|("+tens+")?"+ones+"|"+teens+"|"+words+")";

	public static String months = "((jan(uary)?|mar(ch)?|apr(il)?|may|june?|july?|aug(ust)?|sep(t(ember)?)?|oct(ober)?|nov(ember)?|dec(ember)?)\\.?)";
	public static String days = "((sun|mon|tues?|wed(nes)?|thu(r(s)?)?|fri|sat(ur)?)(day)?)";
	public static String dateNumber = "([0-9]?(1(st)?|2(nd)?|3(rd)?|[0-9&&[^1-3]](th)?))";

	public static String time = "([0-2]?[0-9](:[0-9]{2})?)";
	public static String ampm = "(( ?(?<![a-zA-Z])([ap]m?)?| in the morning?| at ni(ght|te)| in the evening?)\\b)?";
	public static String counter = "([0-9]+|an?|"+numberWords+")";
	public static String secondUnit = "(?<![a-zA-Z])s(ec(ond)?s?)?\\b";
	public static String minuteUnit = "(?<![a-zA-Z])m(in(ute)?s?)?\\b";
	public static String hourUnit = "(?<![a-zA-Z])h((ou)?rs?)?\\b";
	public static String dayUnit = "(?<![a-zA-Z])d(ays?)?\\b";
	public static String weekUnit = "(?<![a-zA-Z])w(ee)?ks?\\b";
	public static String monthUnit = "(?<![a-zA-Z])mo(nth)?s?\\b";
	public static String yearUnit = "(?<![a-zA-Z])y(ea)?rs?\\b";
	public static String duration = "("+counter+" ?("+secondUnit+"|"+minuteUnit+"|"+hourUnit+"|"+dayUnit+"|"+weekUnit+"|"+monthUnit+"|"+yearUnit+")|[0-9]+)";
	public static String date = "("+days+"|(the )?"+dateNumber+" of "+months+"|("+months+"( the)?|the) "+dateNumber+")"; //Wed|the 4th of july|Jul 4th|the 4th
	public static String onReturn = "(when (I'm back|\\w+ ((gets?|comes?) (in|here|back|on(line)?)|returns?)))";

	public static String timeExpressionPattern = "\\b(at (ab(ou)?t )?"+time+ampm+"|in (ab(ou)?t )?"+duration+"|on "+date+"|"+onReturn+")\\b";
	//Add within, sometime, after, before, later
	//Add 'general' specifics like tonight, tomorrow, next week, a while
	//Add remind me about

	private String originalTimeExpression = null;
	private String timeExpression = null;
	
	//These are not portable outside SephiaBot
	private String iregexFind(String pattern, String string) {
		return SephiaBotData.iregexFind(pattern, string);
	}
	private boolean iregex(String pattern, String string) {
		return SephiaBotData.iregex(pattern, string);
	}
	private boolean iequals(String str1, String str2) {
		return SephiaBotData.iequals(str1, str2);
	}
	
	public String getTimeExpression() {return timeExpression;}
	public String getOriginalTimeExpression() {return originalTimeExpression;}
	
	/**
	 * Finds a time expression in a String and returns a time in millis.
	 * Sets timeExpression with the expression that was found, for later retrieval with getTimeExpression().
	 */
	public long textToTime(String text) throws WTFException, 
		   NumberFormatException {
		Pattern when = Pattern.compile(timeExpressionPattern, Pattern.CASE_INSENSITIVE);
		Matcher matcher = when.matcher(text);
		if (!matcher.find())
			throw new WTFException("I can't figure out when to send that.");
		originalTimeExpression = matcher.group();
		timeExpression = originalTimeExpression.replaceAll("about ", "");

		// In some duration
		if (iregex("^in ", timeExpression)) {
			long unit = 0;
			String unitWord = "";
			if (iregex(secondUnit, timeExpression)) {
				unit = 1000;
				unitWord = "second";
			} else if (iregex(minuteUnit, timeExpression)) {
				unit = 1000 * 60;
				unitWord = "minute";
			} else if (iregex(hourUnit, timeExpression)) {
				unit = 1000 * 60 * 60;
				unitWord = "hour";
			} else if (iregex(dayUnit, timeExpression)) {
				unit = 1000 * 60 * 60 * 24;
				unitWord = "day";
			} else if (iregex(weekUnit, timeExpression)) {
				unit = 1000 * 60 * 60 * 24 * 7;
				unitWord = "week";
			} else if (iregex(monthUnit, timeExpression)) {
				unit = 1000 * 60 * 60 * 24 * 30;
				unitWord = "month";
			} else if (iregex(yearUnit, timeExpression)) {
				unit = 1000 * 60 * 60 * 24 * 365;
				unitWord = "year";
			} else { //default to minutes
				unit = 1000 * 60;
				unitWord = "minute";
			}

			String duration = iregexFind(counter, timeExpression);
			int dur = wordToInt(duration);

			if (dur != 1)
				unitWord += "s";
			timeExpression = "in " + duration + " " + unitWord;

			return System.currentTimeMillis() + unit * dur;

		// When he gets back
		} else if (iregex("^"+onReturn+"$", timeExpression)) {
			// Should be caught and never seen
			throw new WTFException("when j00 get back");
		}

		try {
			//The rest of these will use a calendar object
			GregorianCalendar now = new GregorianCalendar();
			GregorianCalendar cal = new GregorianCalendar();
			if (iregex("^at ", timeExpression)) {
				String strAmpm = iregexFind(ampm, timeExpression);
				String strTime = iregexFind(time, timeExpression);
				String strHrs = iregexFind("[^:]+", strTime);
				int intHrs = Integer.parseInt(strHrs);
				String strMins = iregexFind("(?<=:)\\d+", strTime);
				int intMins;
				if (strMins != null && strMins.length() > 0)
					intMins = Integer.parseInt(strMins);
				else
					intMins = 0; 
				cal.set(Calendar.HOUR, intHrs);
				cal.set(Calendar.MINUTE, intMins);

				int increment = 6;
				if (strAmpm.length() > 0) {
					increment = 12;
					if (iregex("\\bam?|in the morning?\\b", strAmpm))
						cal.set(Calendar.AM_PM, Calendar.AM);
					else if (iregex("\\b(pm?|in the evening?|at ni(ght|te))\\b", strAmpm))
						cal.set(Calendar.AM_PM, Calendar.PM);
				}

				while (cal.before(now))
					cal.add(Calendar.HOUR, increment);
				int newHour = cal.get(Calendar.HOUR);
				if (newHour == 0) newHour += 12;
				timeExpression = "" + newHour + ":" + cal.get(Calendar.MINUTE)+
					(cal.get(Calendar.AM_PM)==Calendar.AM ? "a" : "p");
				return cal.getTimeInMillis();
			}
			//TODO: do other date operations if there are multiple expressions
		} catch (NumberFormatException nfe) {
			throw new WTFException("Woah - some weird time I couldn't understand. WTF!");
		}
		throw new WTFException("I recognize '" + this.originalTimeExpression + "', but I haven't learned what it means yet.");
	}

	public int wordToInt(String word) throws NumberFormatException {
		int num = 0;
		if (!iregex(counter, word))
			throw new NumberFormatException("Can't convert '"+word+"' to a number.");
		if (iregex("an?", word))
			return 1;
		if (iregex(numberWords, word))
			throw new NumberFormatException("Use real numbers for now.");
		return Integer.parseInt(word);
	}
}
