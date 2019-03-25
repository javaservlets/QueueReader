/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2018 ForgeRock AS.
 */


package com.example.queue;

import com.google.inject.assistedinject.Assisted;
import com.sun.identity.shared.debug.Debug;
import org.forgerock.guava.common.collect.ImmutableList;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.*;
import org.forgerock.openam.core.CoreWrapper;
import org.forgerock.util.i18n.PreferredLocales;

import javax.inject.Inject;
import javax.security.auth.callback.TextOutputCallback;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Pattern;

import static org.forgerock.openam.auth.node.api.Action.send;

@Node.Metadata(outcomeProvider = Queue.MyOutcomeProvider.class, configClass = Queue.Config.class)

// this class reads from a queue (serverAddress + '/' + username.json
// it always writes the result to a session obj called 'q_val'

public class Queue implements Node {

    private final Config config;
    private final CoreWrapper coreWrapper;
    private final static String DEBUG_FILE = "queueNode";
    protected Debug debug = Debug.getInstance(DEBUG_FILE);
    private String guuid;

    @Override
    public Action process(TreeContext context) throws NodeProcessException {
        Action.ActionBuilder action;
        JsonValue context_json = context.sharedState.copy();
        String contents;
        Long minutes;
        String address = config.serverAddress();

        try {
            String usr = context_json.get("username").asString();
            Reader qreader = new Reader(address, usr); // name of topic: url/use
            contents = qreader.getValue(); // cont
            minutes = Long.valueOf(config.expirationValue()); //todo error check

        } catch (Exception e) {
            log("   uhh..." + e.toString());
            throw new NodeProcessException(e);
        }

        if (contents.isEmpty()) {
            log("     q has NO VALUE");
            return goTo(MyOutcome.EMPTY).build();

        } else if ( ! hasNotExpired(contents, minutes)) {
            log("     q value is too old (timestamp - expiration)");
            return goTo(MyOutcome.EXPIRED).build();

        } else {
            log("     q got a usr value=" + contents); //add the content.getKey(serial#) to the context so node can pick it up
            return goTo(MyOutcome.TRUE).replaceSharedState(context.sharedState
                    .put("q_val", guuid.trim())) //todo name of key could b n config file
                    .build();
        }

    }

    private boolean hasNotExpired(String time, Long expires) {
        String values[] = time.split(Pattern.quote("^"));
        guuid = values[0]; //todo error chex
        String timestamp = values[1];

        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy h:mm:ss a");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        Timestamp currenttime = new Timestamp(System.currentTimeMillis());
        Date currentdate = new Date(currenttime.getTime());

        Date previoustime = new java.util.Date(timestamp);
        Timestamp timestamp2 = new Timestamp(previoustime.getTime());

        Long elapsed = (currentdate.getTime() - timestamp2.getTime()) / 60000;
        log((" total elapsed time: " + elapsed.toString()) + " ( " + currentdate + " - " + timestamp2.toString() + " vs ");

        if (elapsed <= expires)
            return true;
        //shortcircut
        return false;
    }

    public static class MyOutcomeProvider implements org.forgerock.openam.auth.node.api.OutcomeProvider {
        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
            return ImmutableList.of(
                    new Outcome(MyOutcome.TRUE.name(), "true"),
                    new Outcome(MyOutcome.EXPIRED.name(), "expired"),
                    new Outcome(MyOutcome.EMPTY.name(), "empty"));
        }
    }

    private Action.ActionBuilder goTo(MyOutcome outcome) {
        return Action.goTo(outcome.name());
    }

    public enum MyOutcome {
        /**
         * Successful authentication.
         */
        TRUE,
        /**
         * Authentication failed.
         */
        EXPIRED,
        /**
         * The ldap user account has been locked.
         */
        LOCKED,
        /**
         * The ldap user's password has expired.
         */
        EMPTY
    }


    public interface Config {
        @Attribute(order = 100)
        default String serverAddress() {
             return "Server Address";
            //return "http://robbie.freng.org:8080";
        }

        @Attribute(order = 200)
        default String credentials() {
            return "Credentials";
            //sunIdentityMSISDNNumber
        }

        @Attribute(order = 300)
        default String queueName() {
            return "Queue Name";
            //sunIdentityMSISDNNumber
        }

        @Attribute(order = 400)
        default String expirationValue() {
             return "Minutes till expiry";
            //return "2";
        }
    }

    @Inject
    public Queue(@Assisted Config config, CoreWrapper coreWrapper) throws NodeProcessException {
        this.config = config;
        this.coreWrapper = coreWrapper;
    }

    public void log(String str) {
        //debug.message("\r\n           msg:" + str + "\r\n"); //todo log to msg, not error
        debug.error("\r\n           msg:" + str + "\r\n");
        //System.out.println("\n" + str);
    }

}