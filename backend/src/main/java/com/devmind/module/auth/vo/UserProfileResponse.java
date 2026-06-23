package com.devmind.module.auth.vo;

public class UserProfileResponse {

    private Long id;
    private String username;
    private String nickname;
    private String email;

    public UserProfileResponse() {
    }

    public UserProfileResponse(Long id, String username, String nickname, String email) {
        this.id = id;
        this.username = username;
        this.nickname = nickname;
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
