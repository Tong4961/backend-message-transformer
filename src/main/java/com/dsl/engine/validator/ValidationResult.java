package com.dsl.engine.validator;

import java.util.ArrayList;
import java.util.List;

public class ValidationResult {
    private boolean success = true;
    private Summary summary = new Summary();
    private List<ValidationMessage> results = new ArrayList<>();

    public static class Summary {
        private int pass;
        private int warning;
        private int error;

        public int getPass() { return pass; }
        public void setPass(int pass) { this.pass = pass; }
        public int getWarning() { return warning; }
        public void setWarning(int warning) { this.warning = warning; }
        public int getError() { return error; }
        public void setError(int error) { this.error = error; }
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public Summary getSummary() { return summary; }
    public void setSummary(Summary summary) { this.summary = summary; }
    public List<ValidationMessage> getResults() { return results; }
    public void setResults(List<ValidationMessage> results) { this.results = results; }

    public void addError(String path, String rule, String message) {
        addError(path, rule, message, null, null);
    }

    public void addError(String path, String rule, String message, String expected, String actual) {
        ValidationMessage msg = new ValidationMessage(path, "ERROR", rule, message, expected, actual);
        results.add(msg);
        summary.setError(summary.getError() + 1);
        success = false;
    }

    public void addWarning(String path, String rule, String message) {
        addWarning(path, rule, message, null, null);
    }

    public void addWarning(String path, String rule, String message, String expected, String actual) {
        ValidationMessage msg = new ValidationMessage(path, "WARNING", rule, message, expected, actual);
        results.add(msg);
        summary.setWarning(summary.getWarning() + 1);
    }

    public void addPass() {
        summary.setPass(summary.getPass() + 1);
    }

    public void addPasses(int count) {
        summary.setPass(summary.getPass() + count);
    }

    public boolean hasErrors() {
        return summary.getError() > 0;
    }
}
