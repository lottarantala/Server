package com.server;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.sql.SQLException;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;


public class Server{
    private Server() {
    }

    private static SSLContext serverSSLContext(String keystore, String password) throws Exception{
        char[] passphrase = password.toCharArray();
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream(keystore), passphrase);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);

        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return ssl;
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("Starting server...");
        try{
            HttpsServer server = HttpsServer.create(new InetSocketAddress(8001),0);
            
            // Create SSL contect
            SSLContext sslContext = serverSSLContext(args[0], args[1]);

            // Configure server to use created SSL context
            server.setHttpsConfigurator(new HttpsConfigurator(sslContext){
                public void configure(HttpsParameters params){
                    InetSocketAddress remote = params.getClientAddress();
                    SSLContext c = getSSLContext();
                    SSLParameters sslparams = c.getDefaultSSLParameters();
                    params.setSSLParameters(sslparams);
                }
            });

            // Get database
            MessageDB database = MessageDB.getInstance();
            //database.open("database.db");
            database.open(args[3]);
            
            // Create an authenticator
            UserAuthenticator auth = new UserAuthenticator();

            // realm for messages with warning handler
            HttpContext context_warning = server.createContext("/warning", new HandleWarnings());
            context_warning.setAuthenticator(auth);

            // realm for registration with registration handler
            server.createContext("/registration", new HandleRegistration(auth));

            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            System.out.println("Server started succesfully");
        } catch (FileNotFoundException e){
            System.out.println("Certificate not found! ");
            e.printStackTrace();
        } catch(SQLException e){
            System.out.println("Couldn't open database! ");
            e.printStackTrace();
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
