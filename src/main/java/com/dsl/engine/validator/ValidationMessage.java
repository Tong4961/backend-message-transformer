package com.dsl.engine.validator;

public class ValidationMessage {
    private String path;
    private String severity;
    private String rule;
    private String message;
    private String expected;
    private String actual;
    private int lineNumber;

    public ValidationMessage() {}

    public ValidationMessage(String path, String severity, String rule, String message) {
        this.path = path;
        this.severity = severity;
        this.rule = rule;
        this.message = message;
    }

    public ValidationMessage(String path, String severity, String rule, String message, String expected, String actual) {
        this.path = path;
        this.severity = severity;
        this.rule = rule;
        this.message = message;
        this.expected = expected;
        this.actual = actual;
    }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getRule() { return rule; }
    public void setRule(String rule) { this.rule = rule; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getExpected() { return expected; }
    public void setExpected(String expected) { this.expected = expected; }
    public String getActual() { return actual; }
    public void setActual(String actual) { this.actual = actual; }
    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }
}
