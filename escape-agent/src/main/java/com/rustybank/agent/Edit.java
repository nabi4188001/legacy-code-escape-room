package com.rustybank.agent;

/** One change the model asked for: a whole new file, or a deletion (content is null). */
public record Edit(String path, String content) {

    public boolean isDelete() {
        return content == null;
    }
}
