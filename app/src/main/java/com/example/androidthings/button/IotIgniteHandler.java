/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.androidthings.button;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.ardic.android.iotignite.callbacks.ConnectionCallback;
import com.ardic.android.iotignite.enumerations.NodeType;
import com.ardic.android.iotignite.enumerations.ThingCategory;
import com.ardic.android.iotignite.enumerations.ThingDataType;
import com.ardic.android.iotignite.exceptions.UnsupportedVersionException;
import com.ardic.android.iotignite.exceptions.UnsupportedVersionExceptionType;
import com.ardic.android.iotignite.listeners.NodeListener;
import com.ardic.android.iotignite.listeners.ThingListener;
import com.ardic.android.iotignite.nodes.IotIgniteManager;
import com.ardic.android.iotignite.nodes.Node;
import com.ardic.android.iotignite.things.Thing;
import com.ardic.android.iotignite.things.ThingActionData;
import com.ardic.android.iotignite.things.ThingConfiguration;
import com.ardic.android.iotignite.things.ThingData;
import com.ardic.android.iotignite.things.ThingType;


public class IotIgniteHandler implements ConnectionCallback {

    private static final String TAG = IotIgniteHandler.class.getSimpleName();

    private Context applicatonContext;
    private ButtonActivity buttonActivity;

    private static IotIgniteHandler mIotIgniteHandler;
    IotIgniteManager mIotIgniteManager;

    String nodeName;
    String ledThingID, buttonThingID;

    private Node myNode;

    private Thing ledThing, buttonThing;
    ArrayList<Thing> thingList = new ArrayList<>();

    private ThingType myThingType, myThingType2;
    private ThingDataHandler mThingDataHandler;

    private static final long IGNITE_TIMER_PERIOD = 5000L;
    private Timer igniteTimer = new Timer();
    private IgniteTimerControl igniteTimerControl = new IgniteTimerControl();

    private boolean igniteConnected = false;


    private static final int NUMBER_OF_THREADS_IN_EXECUTOR = 2;
    private static final long EXECUTOR_START_DELAY = 100L;
    private static volatile ScheduledExecutorService mExecutor;
    private Hashtable<String, ScheduledFuture<?>> tasks = new Hashtable<>();



    // Handle ignite connection with timer task
    private class IgniteTimerControl extends TimerTask {

        @Override
        public void run() {
            if(!igniteConnected) {
                Log.i(TAG, "Rebuild Ignite...");
                start();
            }
        }
    }


    private IotIgniteHandler(ButtonActivity activity, Context appContext) {
        this.applicatonContext = appContext;
        this.buttonActivity =  activity;
    }


    // Method is defined as "synchronized" to support multi-thread cases
    public static synchronized IotIgniteHandler getInstance(ButtonActivity activity, Context appContext){


            if (mIotIgniteHandler == null) {
                mIotIgniteHandler = new IotIgniteHandler(activity, appContext);
            }

        return mIotIgniteHandler;
    }


    public void start() {
        // Build Ignite Manager

        try {
            mIotIgniteManager = new IotIgniteManager.Builder()
                    .setContext(applicatonContext)
                    .setConnectionListener(this)
                    .build();
        }catch (UnsupportedVersionException e) {
            Log.e(TAG,"UNSUPPORTED_IOTIGNITE_VERSION");
            if (UnsupportedVersionExceptionType.UNSUPPORTED_IOTIGNITE_AGENT_VERSION.toString().equals(e.getMessage())) {
                Log.e(TAG, "UNSUPPORTED_IOTIGNITE_AGENT_VERSION");
            } else {
                Log.e(TAG, "UNSUPPORTED_IOTIGNITE_SDK_VERSION");
            }
        }

        cancelAndScheduleIgniteTimer();
    }

    /**
     * LED Thing Listener
     * Receives configuration and action
     */
    private ThingListener ledThingListener = new ThingListener() {
        @Override
        public void onConfigurationReceived(Thing thing) {
            Log.i(TAG, "Config arrived for " + thing.getThingID());
            applyConfiguration(thing);
        }
        @Override
        public void onActionReceived(String s, String s1, ThingActionData thingActionData) {
            if (s != null && s1 != null && thingActionData != null) {
                Log.d(TAG, "Action received" );
                String action_message = thingActionData.getMessage();
                action_message = action_message.replace("\"","");
                switch (action_message){
                    case "Turn on":
                        // Turn on the LED
                        buttonActivity.setLedValue(true);
                        mySendData(ledThingID);
                        break;
                    case "Turn off":
                        // Turn off the LED
                        buttonActivity.setLedValue(false);
                        mySendData(ledThingID);
                        break;
                    default:
                        break;
                }
            }
        }
        @Override
        public void onThingUnregistered(String s, String s1) {
            Log.i(TAG, "Thing unregister operation can be implemented");
        }
    };


    /**
     * Button Thing Listener
     * Receives configuration and action
     */
    private ThingListener buttonThingListener = new ThingListener() {
        @Override
        public void onConfigurationReceived(Thing thing) {
            Log.i(TAG, "Config arrived for " + thing.getThingID());
            applyConfiguration(thing);
        }
        @Override
        public void onActionReceived(String s, String s1, ThingActionData thingActionData) {
            Log.d(TAG, "Action received2" );
        }
        @Override
        public void onThingUnregistered(String s, String s1) {
            Log.i(TAG, "Thing unregister operation can be implemented");
        }
    };




    public void createNodeAndThings(){

        // Create Node
        nodeName = "AndroidThings Virtual Node";
        myNode = IotIgniteManager.NodeFactory.createNode(nodeName, nodeName, NodeType.GENERIC, nodeName, new NodeListener() {

            @Override
            public void onNodeUnregistered(String s) {
                Log.i(TAG, "Security Node unregistered!");

            }
        });

        // Register node if not registered and set connection.
        if (myNode.register() && !myNode.isRegistered()) {
            myNode.setConnected(true, nodeName + " is online");
            Log.i(TAG, myNode.getNodeID() + " is successfully registered!");
        } else {
            myNode.setConnected(true, nodeName + " is online");
            Log.i(TAG, myNode.getNodeID() + " is already registered!");
        }


        // Create Thing
        ledThingID = "AndroidThings LED Actuator";
        buttonThingID = "AndroidThings Button Sensor";

        myThingType = new ThingType(ledThingID, "IoT Ignite Devzone", ThingDataType.INTEGER);
        myThingType2 = new ThingType(buttonThingID, "IoT Ignite Devzone", ThingDataType.INTEGER);

        if (myNode.isRegistered()) {

            ledThing = myNode.createThing(ledThingID, myThingType, ThingCategory.EXTERNAL, true, ledThingListener,null);
            registerThingIfNotRegistered(ledThing);


            buttonThing = myNode.createThing(buttonThingID, myThingType2, ThingCategory.EXTERNAL, false, buttonThingListener,null);
            registerThingIfNotRegistered(buttonThing);

        }

        else{
            Log.i(TAG,"Node is not registered");
        }
    }


    // // Register thing if not registered and set connection.
    private void registerThingIfNotRegistered(Thing t) {
        if (!t.isRegistered() && t.register()) {
            t.setConnected(true, t.getThingID() + " connected");
            Log.i(TAG, t.getThingID() + " is successfully registered!");
            thingList.add(t);
        } else {
            t.setConnected(true, t.getThingID() + " connected");
            Log.i(TAG, t.getThingID() + " is already registered!");
        }
        applyConfiguration(t);
    }



    // Get thing values from sensor activity
    // Then send these values to IotIgnite
    private class ThingDataHandler implements Runnable {

        Thing mThing2;

        ThingDataHandler(Thing thing) {
            mThing2 = thing;
        }


        @Override
        public void run() {
            ThingData mThingData2 = new ThingData();
            if(mThing2.equals(ledThing)) {
                mThingData2.addData(buttonActivity.ledLastState);
            }
            else if(mThing2.equals(buttonThing)) {
                mThingData2.addData(buttonActivity.buttonLastState);
            }

            if(mThing2.sendData(mThingData2)){
                Log.i(TAG, "DATA SENT SUCCESSFULLY: " + mThingData2 + " FOR " +mThing2);
            }else{
                Log.i(TAG, "DATA SENT FAILURE:" + mThingData2 + " FOR " +mThing2);
            }
        }
    }


    // Checks that Thing's reading frequency is READING_WHEN_ARRIVE or not
    private boolean isConfigReadWhenArrive(Thing mThing) {
        if (mThing.getThingConfiguration().getDataReadingFrequency() == ThingConfiguration.READING_WHEN_ARRIVE) {
            return true;
        }
        return false;
    }

    // Sends data to IotIgnite immediately
    // If reading frequency is READING_WHEN_ARRIVE in thing configuration
    public void mySendData(String thingId) {
        if(igniteConnected) {

            Thing mThing = myNode.getThingByID(thingId);
            if ( isConfigReadWhenArrive(mThing) && mThing != null) {
                ThingData mthingData = new ThingData();
                if (mThing.equals(ledThing)) {
                    mthingData.addData(buttonActivity.ledLastState);
                } else if (mThing.equals(buttonThing)) {
                    mthingData.addData(buttonActivity.buttonLastState);
                }

                if (mThing.sendData(mthingData)) {
                    Log.i(TAG, "DATA SENT SUCCESSFULLY: " + mthingData + " FOR " + mThing);
                } else {
                    Log.i(TAG, "DATA SENT FAILURE:" + mthingData + " FOR " + mThing);
                }
            } else {
                Log.i(TAG, thingId + " is not registered");
            }

        }

        else {
            Log.i(TAG, "Ignite Disconnected!");
        }
    }


    // Schedule data readers for things
    private void applyConfiguration(Thing thing) {
        String key = thing.getNodeID() + "|" + thing.getThingID();
        stopReadDataTask(key);


        if (thing.getThingConfiguration().getDataReadingFrequency() > 0) {
            mThingDataHandler = new ThingDataHandler(thing);

            mExecutor = Executors.newScheduledThreadPool(NUMBER_OF_THREADS_IN_EXECUTOR);

            ScheduledFuture<?> sf = mExecutor.scheduleAtFixedRate(mThingDataHandler, EXECUTOR_START_DELAY, thing.getThingConfiguration().getDataReadingFrequency(), TimeUnit.MILLISECONDS);
            tasks.put(key, sf);
        }
    }


    // Stop task which reads data from thing
    public void stopReadDataTask(String key) {
        if (tasks.containsKey(key)) {
            try {
                tasks.get(key).cancel(true);
                tasks.remove(key);
            } catch (Exception e) {
                Log.d(TAG, "Could not stop schedule send data task" + e);
            }
        }
    }


    @Override
    public void onConnected() {

        Log.i(TAG, "Ignite Connected!");
        igniteConnected = true;
        createNodeAndThings();
        cancelAndScheduleIgniteTimer();
    }

    @Override
    public void onDisconnected() {

        Log.i(TAG, "Ignite Disconnected!");
        igniteConnected = false;
        cancelAndScheduleIgniteTimer();

    }

    private void reconnect() {
        cancelAndScheduleIgniteTimer();
    }

    private void cancelAndScheduleIgniteTimer() {
        igniteTimer.cancel();
        igniteTimerControl.cancel();
        igniteTimerControl = new IgniteTimerControl();
        igniteTimer = new Timer();
        igniteTimer.schedule(igniteTimerControl, IGNITE_TIMER_PERIOD);
    }


    public void stop() {
        if (igniteConnected) {
            ledThing.setConnected(false, "Application Destroyed");
            buttonThing.setConnected(false, "Application Destroyed");
            myNode.setConnected(false, "ApplicationDestroyed");
        }
        if(mExecutor != null) {
            mExecutor.shutdown();
        }
    }
}