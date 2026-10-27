package com.campusoverflow.discovery.domain.notification;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MentionParser {

    private static final Pattern MENTION =
            Pattern.compile("(?<![\\p{L}\\p{N}_])@([^\\s@，。！？：；,.!?;:]{2,20})");
    public static final int MAX_MENTIONS = 5;

    private MentionParser() {
    }

    public static Set<String> extract(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> mentions = new LinkedHashSet<>();
        Matcher matcher = MENTION.matcher(text);
        while (matcher.find() && mentions.size() < MAX_MENTIONS) {
            mentions.add(matcher.group(1));
        }
        return mentions;
    }
}
