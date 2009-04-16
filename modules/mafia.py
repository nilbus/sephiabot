#!/usr/bin/env python
# coding=utf-8
"""
mafia.py - Mafia Game Module
Copyright 2008, Jorge Rodriguez
Licensed under the Eiffel Forum License 2.

http://inamidst.com/phenny/
"""

import re
import random

class MafiaPlayer:
	def __init__(self, game, nick):
		self.game = game
		self.nick = nick
		self.role = ''
		self.team = ''

class MafiaAction:
	def __init__(self, player, target, action):
		self.player = player
		self.target = target
		self.action = action;

class MafiaGame:
	def __init__(self, phenny, channel):
		self.phenny = phenny
		self.state = 'ins'
		self.channel = channel
		self.players = []
		self.actions = []

	def playerin(self, nick):
		if self.state != 'ins': return

		for player in self.players:
			if player.nick == nick:
				return;

		self.players.append(MafiaPlayer(self, nick)) 

		self.phenny.msg(self.channel, nick + " is in, %d players." % (self.numplayers(),))

		if self.numplayers() < 4:
			self.phenny.msg(self.channel, "%d more needed to start." % (4-self.numplayers(),))
		elif self.numplayers() == 4:
			self.phenny.msg(self.channel, "Player count satisfied! Tell me 'ready' to begin.")

	def startgame(self):
		if self.state != 'ins': return False

		if self.numplayers() < 4:
			self.phenny.msg(self.channel, "We need at least 4 players to start.")
			return False

		# Prevent people from saying /in while the PMs are sent
		self.state = 'insover'

		self.phenny.msg(self.channel, "Starting the game with %d players." % self.numplayers())

		# Assign roles
		nummafia = (self.numplayers()+1)/4

		playerindexes = range(self.numplayers())
		scrambled = []
		for i in range(self.numplayers()):
			element = random.choice(playerindexes)
			playerindexes.remove(element)
			scrambled.append(element)

		numcops = random.randint(self.numplayers()/10, self.numplayers()/10+1)
		numdocs = random.randint(self.numplayers()/10, self.numplayers()/10+1)

		roles = []
		for i in range(numcops):
			roles.append('cop')
		for i in range(numdocs):
			roles.append('doc')

		# Randomize the order that roles are handed out.
		townroles = []
		for i in range(len(roles)):
			element = random.choice(roles)
			roles.remove(element)
			townroles.append(element)

		nexttownrole = 0

		for i,index in enumerate(scrambled):
			if i < nummafia:
				self.players[index].team = 'mafia'
				if i == 0:
					self.players[index].role = 'godfather'
				else:
					self.players[index].role = 'goon'
			else:
				self.players[index].team = 'town'
				if nexttownrole < len(townroles):
					self.players[index].role = townroles[nexttownrole]
					nexttownrole += 1
				else:
					self.players[index].role = 'member'

		for player in self.players:
			if player.team.startswith('mafia'):
				if player.role == 'goon':
					self.phenny.msg(player.nick, "You are a mafia goon. Your goal is to eliminate pro-town players by voting during the day.")
				elif player.role == 'godfather':
					self.phenny.msg(player.nick, "You are the mafia godfather. Your goal is to eliminate pro-town players by voting during the day and night killing at night.")
				if nummafia > 1:
					partners = ''
					for other in self.players:
						if other.team == player.team and other != player:
							partners += ' ' + other.nick + ' (' + other.role + ')'
					self.phenny.msg(player.nick, "Your mafia partners are:" + partners)
				self.phenny.msg(player.nick, "You may NOT talk to other mafia during the day phase.")
				self.phenny.msg(player.nick, "You win when only your mafia teammates remain.")
			elif player.team == 'town':
				if player.role == 'member':
					self.phenny.msg(player.nick, "You are a townie. You have no special powers. Your goal is to vote people you think are mafia.")
				elif player.role == 'cop':
					self.phenny.msg(player.nick, "You are a cop. Once per night you may investigate other players to see their alignment.")
				elif player.role == 'doc':
					self.phenny.msg(player.nick, "You are a doctor. Once per night you may protect one other player from being killed by the mafia.")
				self.phenny.msg(player.nick, "You win when only pro-town players remain.")

		self.movetonight()

		return True

	def movetonight(self):
		self.state = 'night'
		self.actions = []
		self.phenny.msg(self.channel, "Now moving to night phase. Discussion is now prohibited. Send all night actions to me in a PM.")
		playersalive = ''
		for player in self.players:
			playersalive += ' ' + player.nick
		self.phenny.msg(self.channel, "Players alive:" + playersalive)

		# Inform players of their night abilities.
		for player in self.players:
			if player.team.startswith('mafia'):
				self.phenny.msg(player.nick, "You now may talk to other team members in PMs.")
				self.phenny.msg(player.nick, "Discuss with the other mafia to decide whom you would like to kill.")
				if player.role == 'godfather':
					self.phenny.msg(player.nick, "When you have chosen someone to night kill, say 'kill (name)' to me in this PM, or 'pass' to kill nobody.")
			elif player.team == 'town':
				if player.role == 'doc':
					self.phenny.msg(player.nick, "You may now choose someone to protect from being killed. Say 'protect (name)' to do this or 'pass' to protect nobody.")
				elif player.role == 'cop':
					self.phenny.msg(player.nick, "You may now choose someone to investigate. Say 'investigate (name)' to do this or 'pass' to protect nobody.")

	def movetoday(self):
		self.state = 'day'
		self.actions = []
		self.phenny.msg(self.channel, "Now moving to day phase. Discussion may resume. All votes have been reset.")
		self.phenny.msg(self.channel, "To vote for a player to lynch, say 'vote (name)' or 'pass' to vote to lynch nobody.")
		playersalive = ''
		for player in self.players:
			playersalive += ' ' + player.nick
		self.phenny.msg(self.channel, "Players alive:" + playersalive)
		self.phenny.msg(self.channel, "With %d alive, it takes %d to lynch." % (self.numplayers(), self.numplayers()/2+1))

	def vote(self, context):
		if not context.sender.startswith('#'):
			return

		voter = self.playerbynick(context.nick)
		victim = self.playerbynick(context.group(1))

		if not voter: return

		if not victim:
			self.phenny.msg(self.channel, "Who? Make sure you typed the name right.")
			return

		if self.state == 'day':
			self.removeactionsby(voter)
			self.addaction(voter, victim, 'vote')

			self.votecount()

	def unvote(self, context):
		if not context.sender.startswith('#'):
			return

		voter = self.playerbynick(context.nick)

		if not voter: return

		if self.state == 'day':
			self.removeactionsby(voter)
			self.votecount()

	def kill(self, context):
		killer = self.playerbynick(context.nick)
		victim = self.playerbynick(context.group(1))

		if not killer: return

		if not victim:
			self.phenny.msg(killer.nick, "Who? Use their full name.")
			return

		if killer.team.startswith('mafia'):
			if self.state == 'night':
				if killer.role == 'godfather':
					self.removeactionsby(killer)
					self.addaction(killer, victim, 'kill')
					self.phenny.msg(killer.nick, "Killing " + victim.nick + ". Waiting for other players to submit their night choices...")
				else:
					self.phenny.msg(killer.nick, "Only your godfather can nightkill.")
			else:
				self.phenny.msg(killer.nick, "You may only kill at night.")

	def passaction(self, context):
		player = self.playerbynick(context.nick)

		if not player: return

		if self.state == 'night':
			if player.team.startswith('mafia'):
				if player.role == 'godfather':
					self.removeactionsby(player)
					self.addaction(player, None, 'pass')
					self.phenny.msg(player.nick, "You have chosen to take no action in this phase.")
			elif player.team == 'town':
				if player.role == 'doc' or player.role == 'cop':
					self.removeactionsby(player)
					self.addaction(player, None, 'pass')
					self.phenny.msg(player.nick, "You have chosen to take no action in this phase.")
		elif self.state == 'day':
			self.removeactionsby(player)
			self.addaction(player, None, 'pass')
			self.votecount()

	def specialaction(self, context):
		player = self.playerbynick(context.nick)
		target = self.playerbynick(context.group(2))

		action = context.group(1)

		if not player: return

		if player.role == 'doc' and action == 'protect':
			if self.state == 'night':
				if not target:
					self.phenny.msg(player.nick, "Who? Use their full name.")
					return
				if player == target:
					self.phenny.msg(player.nick, "You may not protect yourself.")
					return
				self.removeactionsby(player)
				self.addaction(player, target, 'protect')
				self.phenny.msg(player.nick, "Protecting " + target.nick + ". Waiting for other players to submit their night choices...")
			else:
				self.phenny.msg(player.nick, "You can only protect at night.")
		elif player.role == 'cop' and action == 'investigate':
			if self.state == 'night':
				if not target:
					self.phenny.msg(player.nick, "Who? Use their full name.")
					return
				if player == target:
					self.phenny.msg(player.nick, "You may not investigate yourself.")
					return
				self.removeactionsby(player)
				self.addaction(player, target, 'investigate')
				self.phenny.msg(player.nick, "Investigating " + target.nick + ". Waiting for other players to submit their night choices...")
			else:
				self.phenny.msg(player.nick, "You can only investigate at night.")

	def addaction(self, player, target, action):
		self.actions.append(MafiaAction(player, target, action))
		self.checkphaseconditions()

	def findactionby(self, player):
		for action in self.actions:
			if action.player == player:
				return action

		return None

	def removeactionsby(self, player):
		for action in self.actions[:]: #create a copy
			if action.player == player:
				self.actions.remove(action)

	def actionisblocked(self, action):
		if not action:
			return False

		for otheraction in self.actions:
			if otheraction.target == action.target and action.action == 'kill' and otheraction.action == 'protect':
				return True

		return False

	def executeactions(self):
		for action in self.actions:
			if action.action == 'pass':
				continue;
			elif action.action == 'vote':
				continue;
			elif action.action == 'kill':
				self.killplayer(action.target, action.player, action)
				continue;
			elif action.action == 'investigate':
				self.investigateplayer(action.player, action.target, action)
				continue;

	def checkphaseconditions(self):
		satisfied = True
		if self.state == 'night':
			for player in self.players:
				action = self.findactionby(player)

				if player.team == 'town':
					if player.role == 'doc' and not action:
						satisfied = False
					elif player.role == 'cop' and not action:
						satisfied = False

				elif player.team.startswith('mafia'):
					if player.role == 'godfather' and not action:
						satisfied = False;

		elif self.state == 'day':
			satisfied = False

			nolynch = 0
			votes = []
			# Why doesn't zeros() work?
			for i in range(len(self.players)):
				votes.append(0)

			for action in self.actions:
				if action.action == 'vote':
					votes[self.players.index(action.target)] += 1
				elif action.action == 'pass':
					nolynch += 1

			for i,player in enumerate(self.players):
				if votes[i] > self.numplayers()/2:
					satisfied = True
					self.phenny.msg(self.channel, "The town has spoken! " + player.nick + " is to be hung by the neck until dead.")
					self.killplayer(player, None, None)
					break
				elif nolynch > self.numplayers()/2:
					satisfied = True
					self.phenny.msg(self.channel, "The town has decided not to lynch anybody.")
					break

		self.checkwinconditions()

		if self.state != 'over' and satisfied:
			self.executeactions()

			self.checkwinconditions()

			if self.state == 'night':
				self.movetoday()
			elif self.state == 'day':
				self.movetonight()
			elif self.state == 'over':
				return
			else:
				self.phenny.msg(self.channel, "BUG: I'm not in day or night, but I'm supposed to change game state!")

	def checkwinconditions(self):
		teamplayers = {'town': 0, 'mafia': 0}

		for player in self.players:
			teamplayers[player.team] += 1

		otherteamsalive = False
		for team,players in teamplayers.iteritems():
			if team != 'town':
				if players:
					otherteamsalive = True
   				if players >= teamplayers['town']:
					self.teamwin(team)
					return

		if not otherteamsalive:
			self.teamwin('town')

	def teamwin(self, team):
		self.phenny.msg(self.channel, "GAME OVER " + team + " win.")
		players = ''
		for player in self.players:
			players += ' ' + player.nick + ' (' + player.team + ' ' + player.role + ')'
		self.phenny.msg(self.channel, "Remaining players are:" + players)
		self.state = 'over'

	def killplayer(self, player, killer, action):
		if self.actionisblocked(action):
			pass
		else:
			self.phenny.msg(self.channel, player.nick + " is DEAD!!! He was a " + player.team + " " + player.role + ".")
			self.players.remove(player)

	def investigateplayer(self, player, target, action):
		if self.actionisblocked(action):
			self.phenny.msg(player.nick, "Your investigation was blocked.")
		else:
			result = ''
			if target.team.startswith("mafia"):
				result = 'GUILTY'
			else:
				result = 'INNOCENT'
			self.phenny.msg(action.player.nick, "The results of your investigation are that " + target.nick + " is " + result + "!!!")

	def votecount(self):
		if self.state != 'day':
			return;

		nolynch = 0
		votes = []
		# Why doesn't zeros() work?
		for i in range(len(self.players)):
			votes.append(0)

		for action in self.actions:
			if action.action == 'vote':
				votes[self.players.index(action.target)] += 1
			elif action.action == 'pass':
				nolynch += 1

		output = ''
		for i,player in enumerate(self.players):
			if votes[i]:
				output += '  ' + player.nick + ': ' + "%d" % votes[i]

		if nolynch:
			output += '  No lynch: %d' % nolynch

		self.phenny.msg(self.channel, "Official vote count:" + output)

	def queryalive(self):
		output = ''
		for player in self.players:
			output += ' ' + player.nick

		self.phenny.msg(self.channel, "Players alive:" + output)

	def playerbynick(self, nick):
		if not nick:
			return None

		for player in self.players:
			if player.nick.lower() == nick.lower():
				return player

		return None

	def numplayers(self):
		return len(self.players)

mafiagames = []

def findmafia(channel):
	if channel.startswith('#'):
		for game in mafiagames:
			if game.channel == channel:
				return game

	else:
		for game in mafiagames:
			for player in game.players:
				if player.nick == channel:
					return game

	return None

def playmafia(phenny, context): 
	"""Start a game of mafia"""

	if not context.sender.startswith('#'): return

	game = findmafia(context.sender)
	if game:
		if game.state == 'over':
			mafiagames.remove(game)
		else:
			return

	mafiagames.append(MafiaGame(phenny, context.sender))

	phenny.msg(context.sender, "Now playing a game of mafia! Type '.in' to join!")

	# Auto-in whoever starts the game
	playerin(phenny, context)

playmafia.rule = ('$nick', '(?!stop|quit|end).*(play|start).+(mafia).*$')
playmafia.priority = 'low'

def playerin(phenny, context):
	"""Join a game of mafia"""

	if not context.sender.startswith('#'): return

	game = findmafia(context.sender)
	if not game: return

	game.playerin(context.nick)

playerin.rule = r'^[\/\.]in$'
playerin.priority = 'low'

def startgame(phenny, context):
	"""Start the game"""

	if not context.sender.startswith('#'): return

	game = findmafia(context.sender)
	if not game: return

	game.startgame()

startgame.rule = ('$nick', '.*(ready).*$')
startgame.priority = 'low'

def mafiavote(phenny, context):
	"""Vote another player"""

	game = findmafia(context.sender)
	if not game: return

	game.vote(context)

mafiavote.rule = r'^[Vv]ote ([^ ]*) *$'
mafiavote.priority = 'low'

def mafiaunvote(phenny, context):
	"""Unvote"""

	game = findmafia(context.sender)
	if not game: return

	game.unvote(context)

mafiaunvote.rule = r'^[Uu]nvote$'
mafiaunvote.priority = 'low'

def mafiakill(phenny, context):
	"""Kill another player"""

	game = findmafia(context.sender)
	if not game: return

	game.kill(context)

mafiakill.rule = r'^kill ([^ ]*) *$'
mafiakill.priority = 'low'

def mafiapass(phenny, context):
	"""Pass on night actions"""

	game = findmafia(context.sender)
	if not game: return

	game.passaction(context)

mafiapass.rule = r'^pass$'
mafiapass.priority = 'low'

def mafiaspecial(phenny, context):
	"""Use a special power"""

	game = findmafia(context.sender)
	if not game: return

	game.specialaction(context)

mafiaspecial.rule = r'^(protect|investigate) ?([^ ]*) *$'
mafiaspecial.priority = 'low'

def mafiaalive(phenny, context):
	"""Query the bot to see who is alive"""

	game = findmafia(context.sender)
	if not game: return

	game.queryalive()

mafiaalive.rule = ('$nick', '.*(alive).*$')
mafiaalive.priority = 'low'

def quitmafia(phenny, context): 
	"""Quit a game of mafia"""

	if not context.admin: return
	if not context.sender.startswith('#'): return

	game = findmafia(context.sender)
	if not game: return

	mafiagames.remove(game)

	phenny.msg(context.sender, "GAME OVER! Everybody loses.")

quitmafia.rule = ('$nick', '.*(quit|stop|end).+(mafia)?.*$')
quitmafia.priority = 'low'
