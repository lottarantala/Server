package com.server;

import java.sql.SQLException;
import com.sun.net.httpserver.*;

public class UserAuthenticator extends BasicAuthenticator{
    private MessageDB db = null;

    public UserAuthenticator() {
        super("warning");
        db = MessageDB.getInstance();
    }

    @Override
    public boolean checkCredentials(String username, String password) {
        System.out.println("Checking user: " + username + " " + password);
        boolean validUser;

        // Check user from database
        try{
            validUser = db.authenticateUser(username, password);
        } catch(SQLException e){
            e.printStackTrace();
            return false;
        }

        return validUser;
    }

    public boolean addUser(String username, String password, String email) throws SQLException{
        // Add user to database
        boolean added = db.addUser(new User(username, password, email));
        if(added){
            System.out.println("User registered: " + username);
            return true;
        }
        System.out.println("Couldn't register user");
        return false;
    }
}
