package com.karan.intellijplatformplugin.model;

public class FieldMeta {

    private final String name;
    private final String type;

    public FieldMeta(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }
}
