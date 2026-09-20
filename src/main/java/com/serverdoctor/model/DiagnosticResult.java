package com.serverdoctor.model;

public class DiagnosticResult {

    private final String level;
    private final String module;
    private final String title;
    private final String detail;
    private final String suggestion;

    public DiagnosticResult(String level,
                            String module,
                            String title,
                            String detail,
                            String suggestion) {
        this.level = level;
        this.module = module;
        this.title = title;
        this.detail = detail;
        this.suggestion = suggestion;
    }

    public String getLevel() {
        return level;
    }

    public String getModule() {
        return module;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    public String getSuggestion() {
        return suggestion;
    }
}
