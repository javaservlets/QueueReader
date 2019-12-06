package com.example.firebase;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import static com.sun.identity.idm.AMIdentityRepository.debug;

public class Reader {
    String server = null; // address to message queue (firebase instance)
    String key = null; // name of queue topic to read from

    public Reader(String server, String key) { // setting vals n via the constructor to make scratch and testing as 'real' as possible
        this.server = server;
        this.key = key + ".json";
    }

    public String getValue() {
        URL url = null;
        StringBuilder resp = new StringBuilder(""); //'resp'onse is a bdage ID
        try {
            url = new URL(this.server + "/" + this.key);
        } catch (Exception e) {
            log("q:reader:getValue can't connect to server? " + e);
            log(e.toString());
        }

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
        } catch (IOException e) {
            e.printStackTrace();
        }
        conn.setDoInput(true);
        conn.setDoOutput(true);

        try {        //Check response is 200
            if (conn.getResponseCode() == 200) {
                InputStreamReader in = new InputStreamReader(conn.getInputStream());
                BufferedReader br = new BufferedReader(in);
                String text;
                while ((text = br.readLine()) != null) {
                    resp.append(text);
                }
                resp = new StringBuilder(resp.toString().replace("\"", ""));
            }
        } catch (IOException e) {
            log("q:reader:getValue can't read stream? " + e);
            e.printStackTrace();
        }

        //if (resp == null || resp.toString().isEmpty()) {
        if (resp.toString().equalsIgnoreCase("null")) { // no message in q so exit
            return "";
        } else if (resp.toString().contains("DOCTYPE html")) { // this is an error condition on the q (perhaps when topic <> exist
            return "";
        } else if (resp.toString().startsWith("\"")) { // get rid of quotes if they got this far
            resp.toString().substring(1, resp.length() - 1); // get rid of double quotes
        }
        return resp.toString();
    }

    public static void log(String str) {
        debug.message("q: reader:" + str + "\r\n");
        System.out.println("+++   q reader msg:" + str);
    }
}