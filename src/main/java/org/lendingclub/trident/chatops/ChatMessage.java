package org.lendingclub.trident.chatops;

import java.util.Optional;

import com.google.common.base.MoreObjects;

public class ChatMessage {

	ChatOpsManager manager;
	String message;
	String room;
	
	ChatMessage(ChatOpsManager m) {
		this.manager = m;
	}
	public String getMessage() {
		return message;
	}
	public Optional<String> getChannel() {
		return getRoom();
	}
	public Optional<String> getRoom() {
		return Optional.ofNullable(room);
	}
	public ChatMessage withChannel(String channel) {
		return withRoom(channel);
	}
	public ChatMessage withRoom(String room) {
		this.room = room;
		return this;
	}
	
	public ChatMessage withMessage(String msg) {
		this.message = msg;
		return this;
	}
	public void send() {
		manager.send(this);
	}
	public String toString() {
		return MoreObjects.toStringHelper(this).add("room", room).add("message", message).toString();
	}
}
