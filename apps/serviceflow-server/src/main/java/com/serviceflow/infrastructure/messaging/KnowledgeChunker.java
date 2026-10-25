package com.serviceflow.infrastructure.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Keep FAQ sections separate; long prose and PDFs still use overlapping windows. */
public final class KnowledgeChunker {
    private static final int MAX_CHARS = 2_000;
    private static final int OVERLAP = 320;
    private static final Pattern HEADING = Pattern.compile("(?m)^##\\s+");

    private KnowledgeChunker() {}

    public static List<String> split(String text) {
        List<String> result = new ArrayList<>();
        var matcher = HEADING.matcher(text);
        List<Integer> starts = new ArrayList<>();
        while (matcher.find()) starts.add(matcher.start());
        if (starts.isEmpty()) {
            windows(text, "", result);
        } else {
            String introduction = text.substring(0, starts.getFirst()).trim();
            String prefix = introduction.substring(0, Math.min(introduction.length(), 400)) + "\n\n";
            for (int i = 0; i < starts.size(); i++) {
                int end = i + 1 < starts.size() ? starts.get(i + 1) : text.length();
                windows(text.substring(starts.get(i), end), prefix, result);
            }
        }
        return List.copyOf(result);
    }

    private static void windows(String text, String prefix, List<String> result) {
        int capacity = MAX_CHARS - prefix.length();
        for (int start = 0; start < text.length(); start += capacity - OVERLAP) {
            int end = Math.min(text.length(), start + capacity);
            String content = text.substring(start, end).trim();
            if (!content.isEmpty()) result.add(prefix + content);
            if (end == text.length()) break;
        }
    }
}
