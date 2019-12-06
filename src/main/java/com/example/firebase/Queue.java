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


package com.example.firebase;

import com.google.inject.assistedinject.Assisted;
import com.sun.identity.shared.debug.Debug;
// if using 6.0 then ... import org.forgerock.guava.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.*;
import org.forgerock.openam.core.CoreWrapper;
import org.forgerock.util.i18n.PreferredLocales;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

import static java.time.LocalDateTime.now;
import static org.forgerock.openam.auth.node.api.Action.send;

@Node.Metadata(outcomeProvider = Queue.MyOutcomeProvider.class, configClass = Queue.Config.class)

// this class reads from a queue (serverAddress + '/' + (username).json
// it writes the result to a session obj called config.AttributeName to be read downstream
//todo transient
//todo bundle

public class Queue implements Node {

    private final Config config;
    private final CoreWrapper coreWrapper;
    private final static String DEBUG_FILE = "QueueReader";
    protected Debug debug = Debug.getInstance(DEBUG_FILE);
    private String guuid;
    private final Logger logger = LoggerFactory.getLogger(Queue.class);

    @Override
    public Action process(TreeContext context) throws NodeProcessException {
        Action.ActionBuilder action;
        JsonValue context_json = context.sharedState.copy(); //todo transient
        String contents;
        Long minutes;
        String address = config.serverAddress();

        try { //1.0 fieldname below will need 2 change if we ever go back to 'pre-headless' mode
            String fieldname = config.attributeName(); // 6.24.19 queue should have an entry of 'headless' for 'touch and go' rf id scanning
            Reader qreader = new Reader(address, fieldname); // q server address + name of topic (name of user)
            contents = qreader.getValue(); // values read out of json db
            minutes = Long.valueOf(config.expirationValue());
        } catch (Exception e) {
            log("   error" + e.toString());
            throw new NodeProcessException(e);
        }

        if (contents.isEmpty() || contents.equals("")) { // Q for that name could not be found in firebase
            log("     q has NO VALUE (do you have a Q in firebase for this user?)");
            return goTo(MyOutcome.EMPTY).build();

        } else if ( ! hasNotExpired(contents, minutes)) {
            log("     q value is too old (timestamp - expiration)");
            return goTo(MyOutcome.EXPIRED).build();

        } else {
            log("     q got a usr value=" + contents); //add the content.getKey(serial#) to the context so node can pick it up
            return goTo(MyOutcome.TRUE).replaceSharedState(context.sharedState
                    .put("q_val", guuid.trim())) // first param used to be config.attributeName()
                    .build();
        }
    }

    public boolean hasNotExpired(String payload, Long expires) {
        Long elapsed = null ;
        boolean is_expired = false;
        try {
            String values[] = payload.split(Pattern.quote("^")); // strip out just the date
            guuid = values[0]; // this init's this class's instance, and contains the value read off the q
            LocalDateTime event_time = LocalDateTime.parse(values[1].trim(), DateTimeFormatter.ofPattern("MM/dd/yyyy h:mm:ss a"));
            LocalDateTime current_time = now(ZoneId.of("GMT"));
            elapsed = Duration.between(event_time, current_time).toMinutes();
            log("elapsed time: " + elapsed.toString());
            if (elapsed <= expires)
                is_expired = true;
        } catch (Exception e) {
            log("queueReader.hasnotexpired ERROR: " + e) ;
            throw new NodeProcessException(e);

        } finally {
            return is_expired;
        }
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
             return "https://forgerock-51592.firebaseio.com/";
        }

        @Attribute(order = 200)
        default String expirationValue() {
             return "5";
        }

        @Attribute(order = 300)
        default String attributeName() {
            return "headless";
        } //sunIdentityMSISDNNumber

    }

    @Inject
    public Queue(@Assisted Config config, CoreWrapper coreWrapper) throws NodeProcessException {
        this.config = config;
        this.coreWrapper = coreWrapper;
    }

    public void log(String str) {
        debug.message("msg:" + str + "\r\n");
        System.out.println("+++    q node:" + str);
    }

}