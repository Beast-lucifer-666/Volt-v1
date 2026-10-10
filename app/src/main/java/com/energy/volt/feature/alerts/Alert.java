package com.energy.volt.feature.alerts;

public class Alert {
    public enum Type { CRITICAL, WARNING, INFO }

    private final String title;
    private final String message;
    private final String time;
    private final Type type;
    private final String actionText;

    public Alert(String title, String message, String time, Type type, String actionText) {
        this.title = title;
        this.message = message;
        this.time = time;
        this.type = type;
        this.actionText = actionText;
    }

    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getTime() { return time; }
    public Type getType() { return type; }
    public String getActionText() { return actionText; }
}
