package com.onyx.client.auth;

/**
 * Socket authentication POJO
 */
public class AuthData {

    public final String username;
    public final String password;

    /**
     * Constructor with username and password
     *
     * @param username Database user name.
     * @param password Database Password.
     */
    public AuthData(String username, String password) {
        if (username == null) {
            throw new NullPointerException("username");
        }
        if (password == null) {
            throw new NullPointerException("password");
        }

        this.username = username;
        this.password = password;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("AuthData [");
        builder.append("login=").append(username);
        builder.append(", password=").append(password);
        builder.append("]");
        return builder.toString();
    }
}
