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

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

public class Custard {
	private static int month = -1;
	private static String[] flavor = new String[32];
	
	// Updates the cache of this month's flavors
	private static void update() {
		// Initialize
		month = new GregorianCalendar().get(Calendar.MONTH);
		for (int i = 1; i < 32; i++)
			flavor[i] = "It's a secret.";
		try {
			String line;
			int start = -1, end = -1;
			boolean foundSomething = false;
			BufferedReader buf = new BufferedReader(new InputStreamReader(new URL("http://www.goodberrys.com").openConnection().getInputStream()));

			Pattern p = Pattern.compile("msg\\[(.*)\\] = \"(.*)\";");
			do {
				line = buf.readLine();
				if (line == null)
					break; //no more lines

				Matcher m = p.matcher(line);
				try {
					if (!m.matches())
						continue;
					foundSomething = true;
					int index = Integer.parseInt(m.group(1));
					flavor[index] = m.group(2);
				} catch (Exception e) {}

			} while (true); //break when line == null
			if (!foundSomething)
				throw new IOException("reset month hack");
		} catch (IOException e) {
			month = -1;
		}
	}

	public static String flavorOfTheDay(int offset) {
		GregorianCalendar cal = new GregorianCalendar();
		if (month != cal.get(Calendar.MONTH))
			update();
		cal.add(Calendar.DATE, offset);
		int date = cal.get(Calendar.DAY_OF_MONTH) + offset;

		String day = "";
		if (offset == 0)
			day = "Today";
		else if (offset == 1)
			day = "Tomorrow";
		else
			day = "Unsupported offset"; // ;-)

		String probably = "";
		if (month != cal.get(GregorianCalendar.MONTH)) // offset changed month
			probably = "probably ";
		return day + "'s flavor is " + probably + flavor[date];
	}
}
