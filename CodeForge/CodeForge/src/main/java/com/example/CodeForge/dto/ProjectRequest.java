package com.example.CodeForge.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public class ProjectRequest {
    @JsonAlias("appName")
    private String name;

    @JsonAlias("appType")
    private String type;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
