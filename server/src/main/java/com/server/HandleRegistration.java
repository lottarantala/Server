package com.server;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.json.JSONException;
import org.json.JSONObject;
import com.sun.net.httpserver.*;

public class HandleRegistration implements HttpHandler{
    private UserAuthenticator auth = null;
    StringBuilder postRequests = new StringBuilder();

    public HandleRegistration(UserAuthenticator auth){
        this.auth = auth;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        int code = 200;
        String response = "";
        String username = "";
        String password = "";
        String email = "";
        
        System.out.println("Handling registration");
        if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            System.out.println("POST detected");
            Headers headers = exchange.getRequestHeaders();
            String contentType = "";
            int contentLength = 0;

            // Headers must include Content-Type
            if(headers.containsKey("Content-Type")){
                contentType = headers.get("Content-Type").get(0);
            } else{
                code = 411;
                response = "No content type in request";
                System.out.println("No content type");
            }

            // Headers must include Content-Length
            if(headers.containsKey("Content-Length")){
                contentLength = Integer.parseInt(headers.get("Content-Length").get(0));
            } else{
                code = 411;
                response = "No content length in request";
                System.out.println("No content length");
            }

            // Content-Type must be application/json
            if(contentType.equalsIgnoreCase("application/json")){
                System.out.println("Content type is application/json");

                // Get user info from request body
                InputStream input = exchange.getRequestBody();
                String text = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
                input.close();

                // Check that requestbody had some content
                if(text == null || text.length() == 0){
                    code = 403;
                    response = "Not proper user credentials";
                } else{
                    try{
                        // Get reqistration info from request body
                        JSONObject newUser = new JSONObject(text);
                        username = newUser.getString("username");
                        password = newUser.getString("password");
                        email = newUser.getString("email");
                        
                        // Check that every field had content
                        if(username.length() == 0 || password.length() == 0 || email.length() == 0){
                            code = 403;
                            response = "Not proper user credentials";
                        } else{
                            // Add user to database
                            boolean added = auth.addUser(username, password, email);
                            if(added){
                                System.out.println("Registration successful, writing response");
                                exchange.sendResponseHeaders(200, -1);
                            } else{
                                code = 405;
                                response = "User already exists";
                            }
                        }
                    }
                    // Catch if client didn't give correct user info
                    catch(JSONException e){
                        code = 407;
                        response = "JSON exception, faulty user JSON";
                        System.out.println("JSON error");
                    } 
                    // Catch possible other errors
                    catch(Exception e){
                        code = 500;
                        response = "Server error";
                        System.out.println("ERROR: " + e.getStackTrace());
                    }
                }
            }
            // Content-Type was not application/json
            else{
                code = 401;
                response = "Content type is not application/json";
                System.out.println("Content type is not application/json");
            }
        }
        // Client tried some other method than POST
        else{
            code = 400;
            response = "Not supported";
            System.out.println("Method not supported");
        }

        // If registration wasn't succesful, send error message
        if(code >= 400){
            byte[] bytes = response.getBytes("UTF-8");
            exchange.sendResponseHeaders(code, bytes.length);
            OutputStream stream = exchange.getResponseBody();
            stream.write(bytes);
            stream.close();
        }
    }
}
