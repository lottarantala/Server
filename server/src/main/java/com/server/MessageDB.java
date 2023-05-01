package com.server;
import java.io.File;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;

import org.apache.commons.codec.digest.Crypt;

import java.time.Instant;

public class MessageDB {
    private static MessageDB instance = null;
    Connection sqlConnection = null;
    SecureRandom secureRandom = new SecureRandom();

    private MessageDB(){
    }

    // Database is a singleton
    public static synchronized MessageDB getInstance(){
        if(instance == null){
            instance = new MessageDB();
        }
        return instance;
    }

    public void open(String dbName) throws SQLException{
        File dbFile = new File(dbName);
        boolean exists = dbFile.exists() && !dbFile.isDirectory();

        // If database exists, open it
        if(exists){
            System.out.println("Database found, opening database");
            String jdbcAddress = "jdbc:sqlite:" + dbName;
            sqlConnection = DriverManager.getConnection(jdbcAddress);
        }
        // If database doesn't exist, create one
        else{
            System.out.println("Database not found, creating database...");
            initializeDatabase(dbName);
        }
    }

    private boolean initializeDatabase(String dbName) throws SQLException{
        String jdbcAddress = "jdbc:sqlite:" + dbName;
        sqlConnection = DriverManager.getConnection(jdbcAddress);

        if(sqlConnection != null){
            // Table for user information
            String createUserString = "CREATE TABLE users ("
                + "username VARCHAR(100) NOT NULL PRIMARY KEY, "
                + "password VARCHAR(100) NOT NULL, "
                + "email VARCHAR(100) NOT NULL)";

            // Table for warning messages, uses username as a foreign key to reference to the users table
            String createMsgString = "CREATE TABLE messages ("
                + "nickname VARCHAR(100) NOT NULL, "
                + "dangertype VARCHAR(100) NOT NULL, "
                + "latitude DOUBLE NOT NULL, "
                + "longitude DOUBLE NOT NULL, "
                + "sent INTEGER NOT NULL, "
                + "areacode VARCHAR(50), "
                + "phonenumber VARCHAR(100), "
                + "PRIMARY KEY (nickname, sent), "
                + "FOREIGN KEY (nickname) REFERENCES users(username))";
            
            Statement createStatement = sqlConnection.createStatement();
            createStatement.executeUpdate(createUserString);
            createStatement.executeUpdate(createMsgString);
            createStatement.close();

            System.out.println("Database created");
            return true;
        }
        System.out.println("Couldn't create database");
        return false;
    }

    public void close() throws SQLException{
        if(sqlConnection != null){
            System.out.println("Closing database connection");
            sqlConnection.close();
            sqlConnection = null;
        }
    }

    public boolean addUser(User user) throws SQLException{
        // Can't add two users with the same username
        if(checkUserExists(user.getUsername())){
            return false;
        }

        // Create the bytes of salt for the hashed password
        byte bytes[] = new byte[13];
        secureRandom.nextBytes(bytes);

        // Encrypt the password with salt
        String saltBytes = new String(Base64.getEncoder().encode(bytes));
        String salt = "$6$" + saltBytes;
        String hashedPassword = Crypt.crypt(user.getPassword(), salt);

        String query = "INSERT INTO users(username, password, email) VALUES('"
            + user.getUsername() + "', '"
            + hashedPassword + "', '"
            + user.getEmail() + "')";

        // Add user to database
        Statement statement = sqlConnection.createStatement();
        statement.executeUpdate(query);
        statement.close();
        return true;
    }

    public boolean checkUserExists(String username) throws SQLException{
        System.out.println("Checking user...");
        String query = "SELECT username FROM users WHERE username = '" + username + "'";

        Statement statement = sqlConnection.createStatement();
        ResultSet output = statement.executeQuery(query);

        if(output.next()){
            System.out.println("User exists");
            return true;
        }
        System.out.println("User does not exist");
        return false;
    }

    public boolean authenticateUser(String username, String password) throws SQLException{
        System.out.println("Finding user...");
        String query = "SELECT username, password FROM users WHERE username = '" + username + "'";

        Statement statement = sqlConnection.createStatement();
        ResultSet output = statement.executeQuery(query);

        if(output.next() == false){
            System.out.println("Cannot find user");
            return false;
        } else{
            // Compare given password to the hashed password in database
            String hashedPassword = output.getString("password");
            if(hashedPassword.equals(Crypt.crypt(password, hashedPassword))){
                System.out.println("User found");
                return true;
            } else{
                System.out.println("Wrong password");
                return false;
            }
        }
    }

    public boolean addMessage(WarningMessage message) throws SQLException{
        try{
            String query = "INSERT INTO messages(nickname, dangertype, latitude, longitude, sent, areacode, phonenumber) VALUES('"
                + message.getNickname() + "', '"
                + message.getDangertype() + "', "
                + message.getLatitude() + ", "
                + message.getLongitude() + ", "
                + message.dateAsInt() + ", '"
                + message.getAreacode() + "', '"
                + message.getPhonenumber() + "')";

            Statement statement = sqlConnection.createStatement();
            statement.executeUpdate(query);
            statement.close();
            return true;
        } catch(SQLException e){
            System.out.println("Couldn't add message");
            return false;
        }
    }

    public ArrayList<WarningMessage> getMessages() throws SQLException{
        ArrayList<WarningMessage> messages = new ArrayList<WarningMessage>();
        String query = "SELECT * FROM messages";

        try{
            Statement statement = sqlConnection.createStatement();
            ResultSet output = statement.executeQuery(query);

            while(output.next()){
                // Change sent to correct format and add message to arraylist
                LocalDateTime sent = LocalDateTime.ofInstant(Instant.ofEpochMilli(output.getLong("sent")), ZoneOffset.UTC);
                WarningMessage msg = new WarningMessage(output.getString("nickname"), output.getDouble("latitude"), output.getDouble("longitude"), output.getString("dangertype"), sent, output.getString("areacode"), output.getString("phonenumber"));
                messages.add(msg);
            }
            statement.close();
        } catch (SQLException e) {
            System.out.println("SQL exception");
        }
        
        return messages;
    }

    public ArrayList<WarningMessage> getWithCoords(double uplong, double downlong, double uplat, double downlat) throws SQLException{
        ArrayList<WarningMessage> messages = new ArrayList<WarningMessage>();

        // Query finds messages where downlat < latitude < uplat and downlong < longitude < uplong
        String query = "SELECT * FROM messages WHERE (longitude BETWEEN " + uplong + " AND " + downlong + ") AND (latitude BETWEEN " + downlat + " AND " + uplat + ")";

        try{
            Statement statement = sqlConnection.createStatement();
            ResultSet output = statement.executeQuery(query);

            while(output.next()){
                // Change sent to correct format and add message to arraylist
                LocalDateTime sent = LocalDateTime.ofInstant(Instant.ofEpochMilli(output.getLong("sent")), ZoneOffset.UTC);
                WarningMessage msg = new WarningMessage(output.getString("nickname"), output.getDouble("latitude"), output.getDouble("longitude"), output.getString("dangertype"), sent, output.getString("areacode"), output.getString("phonenumber"));
                messages.add(msg);
            }
            statement.close();
        } catch (SQLException e){
            System.out.println("SQL Exception");
        }

        return messages;
    }

    public ArrayList<WarningMessage> getWithTime(long start, long end) throws SQLException{
        ArrayList<WarningMessage> messages = new ArrayList<WarningMessage>();

        // Query finds messages where start < sent < end
        String query = "SELECT * FROM messages WHERE (sent BETWEEN " + start + " AND " + end + ")";

        try{
            Statement statement = sqlConnection.createStatement();
            ResultSet output = statement.executeQuery(query);

            while(output.next()){
                // Change sent to correct format and add message to arraylist
                LocalDateTime sent = LocalDateTime.ofInstant(Instant.ofEpochMilli(output.getLong("sent")), ZoneOffset.UTC);
                WarningMessage msg = new WarningMessage(output.getString("nickname"), output.getDouble("latitude"), output.getDouble("longitude"), output.getString("dangertype"), sent, output.getString("areacode"), output.getString("phonenumber"));
                messages.add(msg);
            }
            statement.close();
        } catch (SQLException e){
            System.out.println("SQL Exception");
        }

        return messages;
    }

    public ArrayList<WarningMessage> getWithNick(String nick) throws SQLException{
        ArrayList<WarningMessage> messages = new ArrayList<WarningMessage>();

        // Query finds messages where nickname = nick
        String query = "SELECT * FROM messages WHERE (nickname = '" + nick + "')";

        try{
            Statement statement = sqlConnection.createStatement();
            ResultSet output = statement.executeQuery(query);

            while(output.next()){
                // Change sent to correct format and add message to arraylist
                LocalDateTime sent = LocalDateTime.ofInstant(Instant.ofEpochMilli(output.getLong("sent")), ZoneOffset.UTC);
                WarningMessage msg = new WarningMessage(output.getString("nickname"), output.getDouble("latitude"), output.getDouble("longitude"), output.getString("dangertype"), sent, output.getString("areacode"), output.getString("phonenumber"));
                messages.add(msg);
            }
            statement.close();
        } catch (SQLException e){
            System.out.println("SQL Exception");
        }

        return messages;
    }
}
