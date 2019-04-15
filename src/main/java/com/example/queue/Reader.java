package com.example.queue;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import static com.sun.identity.idm.AMIdentityRepository.debug;

public class Reader {
    String server = null;
    String key = null; // "601KPZK0343037" if iotDna; "00 00 00 00 50 72 9D 62 " if rfIdeas

    public Reader(String server, String key) { // I m only setting vals n via the constructor to make scratch testing as 'real' as possible
        this.server = server;
        this.key = key + ".json";
    }

    public String getValue() {
        URL url = null;
        StringBuilder resp = null;

        try {
            url = new URL(this.server + "/" + this.key);
        } catch (Exception e) {
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
                resp = new StringBuilder(); // exit if ""
                InputStreamReader in = new InputStreamReader(conn.getInputStream());
                BufferedReader br = new BufferedReader(in);
                String text;
                while ((text = br.readLine()) != null) {
                    resp.append(text);
                }
                resp = new StringBuilder(resp.toString().replace("\"", ""));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (resp == null) {
            return "";
        } else if (resp.toString().contains("DOCTYPE html")) { //rj? error condition when topic <> exist
            return "";
        } else if (resp.toString().startsWith("\"")) {
            resp.toString().substring(1, resp.length() - 1); // get rid of double quotes
        }
        return resp.toString();
    }


    public static void log(String str) {
         debug.message("+++ " + str);
    }
}