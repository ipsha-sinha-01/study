package com.springBootBasics.demo;

public class Book {

    private long id;

    public Book(long id, String name) {
        this.id = id;
        this.name = name;
    }

    private String name;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
