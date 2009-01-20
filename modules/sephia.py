#!/usr/bin/env python
# coding=utf-8
"""
sephia.py - SephaiBot Module
Copyright 2009, Ed Anderson
Licensed under the Eiffel Forum License 2.

http://inamidst.com/phenny/

TODO:
.hugs
.p[ea]ts
.pokes
.tickles
.(slaps|smacks|hits|punches|kicks)
."botsnack
"kill <who>
"(thank( ?(yo)?u|[sz])\\b|\\bt(y|hn?x)\\b)
"bounc[ye]
"right?
FOTD
Excuses
Timed messages
Greetings for certain people
"""

import re
import random

def whoareyou(phenny, context): 
	"""State your identity!"""

	phenny.msg(context.sender, "I am an advanced SephiaBot channel bot.")
	phenny.msg(context.sender, "I'll kick your booty in days that end in 'y'.")
	phenny.msg(context.sender, "Vino is my daddy. Vino rocks.")

whoareyou.name = 'who are you?'
whoareyou.rule = ('$nick', 'who are you\??$')
whoareyou.priority = 'low'

def ping(phenny, context): 
	"""Who made this mess?"""

	phenny.msg(context.sender, context.nick + ", pong")

ping.name = "ping"
ping.rule = ('$nick', "ping")
ping.priority = 'low'
