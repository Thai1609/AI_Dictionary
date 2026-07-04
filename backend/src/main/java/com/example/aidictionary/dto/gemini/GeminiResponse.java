package com.example.aidictionary.dto.gemini;

import java.util.List;

public class GeminiResponse {

    private List<Candidate> candidates;

    public List<Candidate> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<Candidate> candidates) {
        this.candidates = candidates;
    }

    public String getFirstText() {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        Candidate candidate = candidates.get(0);

        if (candidate.getContent() == null ||
                candidate.getContent().getParts() == null ||
                candidate.getContent().getParts().isEmpty()) {
            return null;
        }

        return candidate.getContent().getParts().get(0).getText();
    }

    public static class Candidate {
        private Content content;

        public Content getContent() {
            return content;
        }

        public void setContent(Content content) {
            this.content = content;
        }
    }

    public static class Content {
        private List<Part> parts;

        public List<Part> getParts() {
            return parts;
        }

        public void setParts(List<Part> parts) {
            this.parts = parts;
        }
    }

    public static class Part {
        private String text;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }
}