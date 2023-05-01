package com.server;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class WarningMessage {
    private String nickname;
    private double latitude;
    private double longitude;
    private String dangertype;
    private LocalDateTime sent;
    private String areacode;
    private String phonenumber;

    public WarningMessage(){
    }

    public WarningMessage(String nickname, double latitude, double longitude, String dangertype, LocalDateTime sent, String areacode, String phonenumber){
        this.nickname = nickname;
        this.latitude = latitude;
        this.longitude = longitude;
        this.dangertype = dangertype;
        this.sent = sent;
        this.areacode = areacode;
        this.phonenumber = phonenumber;
    }

    public long dateAsInt(){
        // Change time format to long, used for storing datetime in the database
        return sent.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    public void setSent(long epoch){
        // Change sent to correct time format
        this.sent = LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneOffset.UTC); 
    }

    // Getters
    public String getNickname(){
        return this.nickname;
    }

    public double getLatitude(){
        return this.latitude;
    }

    public double getLongitude(){
        return this.longitude;
    }

    public String getDangertype(){
        return this.dangertype;
    }

    public LocalDateTime getSent(){
        return this.sent;
    }

    public String getAreacode(){
        return this.areacode;
    }

    public String getPhonenumber(){
        return this.phonenumber;
    }
}
