package com.server;

import java.io.*;
import com.sun.net.httpserver.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class HandleWarnings implements HttpHandler{
    private String response = "";
    private MessageDB db =  MessageDB.getInstance();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        System.out.println("Handling warnings");
        int code = 200;
        try{
            // Handle POST method
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                System.out.println("POST detected");
                code = handlePostRequest(exchange);
            }
            // Handle GET method
            else if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                System.out.println("GET detected");
                code = handleGETRequest(exchange);
            }
            // Client tried some other method than POST or GET
            else {
                code = 400;
                response = "Not supported";
                System.out.println("Method not supported");
            }
        }
        // Catch I/O Exceptions
        catch(IOException e){
            code = 412;
            response = "IOException: " + e.getMessage();
            System.out.println("IOException: " + e.getMessage());
        }
        // Catch possible other exceptions
        catch(Exception e){
            code = 413;
            response = "Server error: " + e.getMessage();
            System.out.println("Error: " + e.getMessage());
        }

        // If error occurred, send error message
        if(code >= 400){
            byte[] bytes = response.getBytes("UTF-8");
            exchange.sendResponseHeaders(code, bytes.length);
            OutputStream stream = exchange.getResponseBody();
            stream.write(response.getBytes());
            stream.close();
        }
    }

    private int handlePostRequest(HttpExchange exchange) throws IOException{
        String contentType = "";
        String nickname = "";
        String dangertype = "";
        String sent = "";
        String areacode = "";
        String phonenumber = "";
        String query = "";
        double longitude;
        double latitude;
        int contentLength = 0;
        int code = 200;

        // Get headers
        Headers headers = exchange.getRequestHeaders();

        // Headers must include Content-Type
        if(headers.containsKey("Content-Type")){
            contentType = headers.get("Content-Type").get(0);
        } else{
            code = 411;
            response = "No content type in request";
            System.out.println("No content type");
            return code;
        }

        // Headers must include Content-Length
        if(headers.containsKey("Content-Length")){
            contentLength = Integer.parseInt(headers.get("Content-Length").get(0));
        } else{
            code = 411;
            response = "No content length in request";
            System.out.println("No content length");
            return code;
        }

        // Content-Type must be application/json
        if(contentType.equalsIgnoreCase("application/json")){
            System.out.println("Content type is application/json");

            // Get POST info from request body
            InputStream input = exchange.getRequestBody();
            String msg = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
            input.close();

            // Check that request body actually containded something
            if(msg == null || msg.length() == 0){
                code = 403;
                response = "Nothing in the message";
                return code;
            } else{
                try{
                    JSONObject newMsg = new JSONObject(msg);

                    // Check for query first
                    try{
                        query = newMsg.getString("query");
                        System.out.println("Query found");
                        // If query equals location, time or user send message with specified query
                        if(query.equals("location") || query.equals("time") || query.equals("user")){
                            code = sendMsgWithQuery(exchange, newMsg);
                            return code;
                        }
                    } catch (Exception e){
                        // Do nothing if message didn't have query, it probably has a warning message
                    }

                    // Get mandatory fields
                    nickname = newMsg.getString("nickname");
                    dangertype = newMsg.getString("dangertype");
                    sent = newMsg.getString("sent");
                    longitude = newMsg.getDouble("longitude");
                    latitude = newMsg.getDouble("latitude");

                    // Dangertype must be either Deer, Reindeer, Moose or Other
                    if(!dangertype.toLowerCase().equals("deer") && !dangertype.toLowerCase().equals("reindeer") && !dangertype.toLowerCase().equals("moose") && !dangertype.toLowerCase().equals("other")){
                        code = 411;
                        response = "Danger type not supported";
                        System.out.println("Danger type not supported");
                        return code;
                    }
                    
                    // Check if client gave an areacode
                    try{
                        areacode = newMsg.getString("areacode");
                    } catch(JSONException e) {
                        // Field wasn't given, fill with 'nodata'
                        areacode = "nodata";
                    }

                    // Check if client gave a phonenumber
                    try{
                        phonenumber = newMsg.getString("phonenumber");
                    } catch(JSONException e) {
                        // Field wasn't given, fill with 'nodata'
                        phonenumber = "nodata";
                    }
                    
                    // Check that string values had some content
                    if(nickname.length() == 0 || dangertype.length() == 0 || sent.length() == 0){
                        code = 403;
                        response = "Something from the warning message is missing";
                    } else{
                        try{
                            System.out.println("Creating message object");

                            // Change time to LocalDateTime
                            OffsetDateTime otd = OffsetDateTime.parse(sent);
                            LocalDateTime ltd = otd.toLocalDateTime();

                            // If areacode or phonenumber didn't have any content, fill with 'nodata'
                            if(areacode.length() == 0){
                                areacode = "nodata";
                            }
                            if(phonenumber.length() == 0){
                                phonenumber = "nodata";
                            }

                            // Create a new message object and add it to the database
                            WarningMessage message = new WarningMessage(nickname, latitude, longitude, dangertype, ltd, areacode, phonenumber);
                            db.addMessage(message);
                            System.out.println("Message added successfully, writing response");
                            exchange.sendResponseHeaders(code, -1);
                            
                        }
                        // Catch if couldn't add message
                        catch(Exception e){
                            System.out.println("Couldn't add message: " + e.getMessage());
                            code = 405;
                            response = "Something went wrong: " + e.getMessage();
                        }
                    }
                }
                // Catch if client didn't give something mandatory
                catch(JSONException e){
                    code = 407;
                    response = "JSON exception, faulty post JSON";
                    System.out.println("JSON error " + e.getMessage());
                }
            }
        }
        // Content-Type was not application/json
        else{
            code = 401;
            response = "Content type is not application/json";
            System.out.println("Content type is not application/json");
        }
        return code;
    }

    private int handleGETRequest(HttpExchange exchange) throws IOException{
        int code = 200;

        ArrayList<WarningMessage> warningMessages = null;
        JSONArray responseMessages = new JSONArray();
        
        // Get messages from the database
        try{
            warningMessages = db.getMessages();
        } catch(SQLException e){
            code = 500;
            response = "Couldn't fetch messages from database";
            System.out.println("SQLExeption: " + e.getMessage());
            return code;
        }

        // If database is empty, no messages to send
        if(warningMessages.isEmpty()){
            System.out.println("No messages");
            code = 204;
            exchange.sendResponseHeaders(code, -1);
            return code;
        }
        else{
            System.out.println("Messages found");

            // Formatter for date
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");
            
            System.out.println("Going through messages...");

            for(int i = 0; i < warningMessages.size(); i++){
                WarningMessage msg = warningMessages.get(i);
                JSONObject obj = new JSONObject();

                // Convert sent date from LocalDateTime to ZonedDateTime
                ZonedDateTime zdt = msg.getSent().atZone(ZoneId.of("UTC"));
                String sent = zdt.format(formatter);

                // Put mandatory fields to JSON object
                obj.put("nickname", msg.getNickname());
                obj.put("latitude", msg.getLatitude());
                obj.put("longitude", msg.getLongitude());
                obj.put("dangertype", msg.getDangertype());
                obj.put("sent", sent);

                // If message has additional information, add to the JSON object
                if(!(msg.getAreacode().equals("nodata"))){
                    obj.put("areacode", msg.getAreacode());
                }
                if(!(msg.getPhonenumber().equals("nodata"))){
                    obj.put("phonenumber", msg.getPhonenumber());
                }

                responseMessages.put(obj);
            }
            System.out.println("Messages added succesfully, sending response");

            // Respond with code 200 and Content-Length and Content-Type
            byte [] bytes;
            bytes = responseMessages.toString().getBytes("UTF-8");
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, bytes.length);
            OutputStream stream = exchange.getResponseBody();
            stream.write(bytes);
            stream.close();
            return code;
        }
    }

    // Send messages that are specified with a query
    private int sendMsgWithQuery(HttpExchange exchange, JSONObject object) throws IOException, SQLException{
        ArrayList<WarningMessage> warningMessages = null;
        JSONArray responseMessages = new JSONArray();
        int code = 200;

        // Get messages according to query value
        if((object.getString("query")).equals("location")){
            warningMessages = db.getWithCoords(object.getDouble("uplongitude"), object.getDouble("downlongitude"), object.getDouble("uplatitude"), object.getDouble("downlatitude"));
        } else if((object.getString("query")).equals("time")){
            // Parse datetimes and convert them to milliseconds to compare
            OffsetDateTime otdStart = OffsetDateTime.parse(object.getString("timestart"));
            LocalDateTime ltdStart = otdStart.toLocalDateTime();
            long start = ltdStart.toInstant(ZoneOffset.UTC).toEpochMilli();
            
            OffsetDateTime otdEnd = OffsetDateTime.parse(object.getString("timeend"));
            LocalDateTime ltdEnd = otdEnd.toLocalDateTime();
            long end = ltdEnd.toInstant(ZoneOffset.UTC).toEpochMilli();

            warningMessages = db.getWithTime(start, end);
        } else if((object.getString("query")).equals("user")){
            warningMessages = db.getWithNick(object.getString("nickname"));
        }

        // If database returns empty list, no messages to send
        if(warningMessages.isEmpty()){
            System.out.println("No messages");
            code = 204;
            exchange.sendResponseHeaders(code, -1);
            return code;
        }
        else{
            System.out.println("Messages found");

            // Formatter for date
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");
            
            System.out.println("Going through messages...");

            for(int i = 0; i < warningMessages.size(); i++){
                WarningMessage msg = warningMessages.get(i);
                JSONObject obj = new JSONObject();

                // Convert sent date from LocalDateTime to ZonedDateTime
                ZonedDateTime zdt = msg.getSent().atZone(ZoneId.of("UTC"));
                String sent = zdt.format(formatter);

                // Put mandatory fields to JSON object
                obj.put("nickname", msg.getNickname());
                obj.put("latitude", msg.getLatitude());
                obj.put("longitude", msg.getLongitude());
                obj.put("dangertype", msg.getDangertype());
                obj.put("sent", sent);

                // If message has additional information, add to the JSON object
                if(!(msg.getAreacode().equals("nodata"))){
                    obj.put("areacode", msg.getAreacode());
                }
                if(!(msg.getPhonenumber().equals("nodata"))){
                    obj.put("phonenumber", msg.getPhonenumber());
                }

                responseMessages.put(obj);
            }
            System.out.println("Messages added succesfully, sending response");

            // Respond with code 200 and Content-Length and Content-Type
            byte [] bytes;
            bytes = responseMessages.toString().getBytes("UTF-8");
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, bytes.length);
            OutputStream stream = exchange.getResponseBody();
            stream.write(bytes);
            stream.close();
            return code;
        }
    }
}