package com.example.streamsenderdemo;

class AuthInfo {

    static AuthInfo of(String accountUserName, String accountPassKey) {
        AuthInfo authInfo = new AuthInfo();
        authInfo.setAccountUserName(accountUserName);
        authInfo.setAccountPassKey(accountPassKey);
        return authInfo;
    }

    private String accountUserName;
    private String accountPassKey;

    public String getAccountUserName() {
        return accountUserName;
    }

    public void setAccountUserName(String accountUserName) {
        this.accountUserName = accountUserName;
    }

    public String getAccountPassKey() {
        return accountPassKey;
    }

    public void setAccountPassKey(String accountPassKey) {
        this.accountPassKey = accountPassKey;
    }
}