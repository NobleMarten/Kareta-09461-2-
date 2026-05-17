package ru.gr0946x.net;

public record Message(int id, int senderId, Integer receiverId, String content, String sentAt) {}
