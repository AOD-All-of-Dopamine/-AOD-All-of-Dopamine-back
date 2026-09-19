package com.example.AOD.recommend.reaction;

public class ContentNotFoundException extends RuntimeException {
    public ContentNotFoundException(Long contentId) {
        super("Content not found: " + contentId);
    }
}
