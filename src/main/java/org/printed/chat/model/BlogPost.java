package org.printed.chat.model;

import java.time.LocalDate;

public class BlogPost {
    private String id;
    private String title;
    private LocalDate publishDate;
    private String preview;
    private String content;

    // Constructor, getters y setters

    public BlogPost(String id, String title, LocalDate publishDate, String preview, String content) {
        this.id = id;
        this.title = title;
        this.publishDate = publishDate;
        this.preview = preview;
        this.content = content;
    }

    // Getters y setters
    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getPublishDate() {
        return publishDate;
    }

    public String getPreview() {
        return preview;
    }

    public String getContent() {
        return content;
    }
}