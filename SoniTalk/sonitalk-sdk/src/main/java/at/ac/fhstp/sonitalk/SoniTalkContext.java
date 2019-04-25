/*
 * Copyright (c) 2019. Alexis Ringot, Florian Taurer, Matthias Zeppelzauer.
 *
 * This file is part of SoniTalk Android SDK.
 *
 * SoniTalk Android SDK is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SoniTalk Android SDK is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with SoniTalk Android SDK.  If not, see <http://www.gnu.org/licenses/>.
 */

package at.ac.fhstp.sonitalk;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ResultReceiver;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;

import java.util.EnumSet;

/**
 * Allows to create objects of the Encoder, Decoder and Sender. It handles
 * the creation, initialization, updated and cancelation of notifications. Beside that the
 * SoniTalkContext contains the request codes for the permission levels, granting and denying
 * permission requests and checking if a send job is finished.
 */
public class SoniTalkContext {
    /**
     * Will be sent to the SoniTalkPermissionsResultReceiver when the user chose to "Decline" when
     * asked for the privacy level.
     */
    public static final int ON_PERMISSION_LEVEL_DECLINED = 1002;
    /**
     * Will be sent to the SoniTalkPermissionsResultReceiver when a request is granted, i.e. when
     * the user gave permission to start receiving/sending (potentially sending several occurrences).
     */
    public static final int ON_REQUEST_GRANTED = 1003;
    /**
     * Will be sent to the SoniTalkPermissionsResultReceiver when the user denied a permission request
     * from the custom SoniTalk dialogs (L1/L2). At least this callback (and
     * ON_REQUEST_L0_DENIED) or ON_SHOULD_SHOW_RATIONALE_FOR_ALLOW_ALWAYS should be
     * used to show some rationale to the user.
     */
    public static final int ON_REQUEST_DENIED = 1004;
    /**
     * Will be sent to the SoniTalkPermissionsResultReceiver when a "send job" is finished, i.e. if
     * a message was to be sent several times, after the last occurrence has been emitted.
     */
    public static final int ON_SEND_JOB_FINISHED = 1005;
    /**
     * Will be sent to the SoniTalkPermissionsResultReceiver when the user should show a rationale
     * for the "Allow Always" (L0) SoniTalk permission. At least this callback or
     * ON_REQUEST_DENIED should be used to show some rationale to the user.
     */
    public static final int ON_SHOULD_SHOW_RATIONALE_FOR_ALLOW_ALWAYS = 1006;
    /**
     * Will be sent to the SoniTalkPermissionsResultReceiver when the user denied a permission request
     * from the Android permission system (L0). At least this callback (and
     * ON_REQUEST_DENIED) or ON_SHOULD_SHOW_RATIONALE_FOR_ALLOW_ALWAYS should be
     * used to show some rationale to the user.
     */
    public static final int ON_REQUEST_L0_DENIED = 1007;

    private SoniTalkPermissionManager permissionManager;
    private Context appContext;

    /*package private*/enum State {
        IDLE,
        SENDING,
        RECEIVING,
        //SENDING_AND_RECEIVING // Using EnumSet instead
    }
    private EnumSet<State> states;

    /**
     * Instantiate a SoniTalkContext, allowing to create Encoder, Decoder and Sender objects.
     * @param context is used to get an application context.
     * @param sdkListener ResultReceiver which will receive callbacks from SoniTalk. Please set
     *                    the handler according to which thread should execute the callbacks (often
     *                    the UI Thread if it handles UI components). Please call setReceiver and
     *                    pass an object that implements SoniTalkPermissionsResultReceiver.Receiver
     *                    interface in order to receive the callbacks (it can be an Activity, but
     *                    then do not forget to set it back to null in onStop or onPause to avoid
     *                    leaking your Activity on configuration changes).
     * @return a SoniTalkContext, allowing to create Encoder, Decoder and Sender objects.
     */
    public static SoniTalkContext getInstance(Context context, SoniTalkPermissionsResultReceiver sdkListener) {
        return new SoniTalkContext(context, sdkListener);
    }

    private SoniTalkContext(Context context, SoniTalkPermissionsResultReceiver sdkListener) {
        this.appContext = context.getApplicationContext(); // Never store an Activity Context in a long lived class.
        states = EnumSet.of(State.IDLE);
        permissionManager = new SoniTalkPermissionManager(sdkListener);
    }

    // Add a parameter to check for specific permissions ? (receiving/sending/...)
    // NOT public as it cannot work together with L2 runtime permission request.
    /*package-private*/boolean checkSelfPermission(int requestCode) {
        return permissionManager.checkSelfPermission(appContext, requestCode);
    }

    /*package-private*/boolean checkMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        else
            return true;
    }

    /**
     * Signals that a send job is finished. Will trigger the ON_SEND_JOB_FINISHED callback.
     * @param requestCode Integer passed by the developer to be able to identify a "send job".
     */
    /*package-private*/void sendJobFinished(int requestCode){
        permissionManager.sendJobFinished(appContext, requestCode);
    }

    /**
     * @param sampleRate is used to start the decoder with the correct sample rate
     * @param config can be generated with the utility class ConfigFactory and
     *               holds configurations for the decoder
     * @return a new SoniTalkDecoder
     */
    public SoniTalkDecoder getDecoder(int sampleRate, SoniTalkConfig config) {
        return new SoniTalkDecoder(this, sampleRate, config);
    }

    //TODO: If people ask for it, provide a constructor with stepFactor, bandPassFilterOrder, and/or startFactor/endFactor ?

    /**
     * @param sampleRate is used to start the decoder with the correct sample rate
     * @param config can be generated with the utility class ConfigFactory and
     *               holds configurations for the decoder
     * @param stepFactor
     * @param frequencyOffsetForSpectrogram
     * @param silentMode
     * @return a new SoniTalkDecoder
     */
    private SoniTalkDecoder getDecoder(int sampleRate, SoniTalkConfig config, int stepFactor, int frequencyOffsetForSpectrogram, boolean silentMode) {
        return new SoniTalkDecoder(this, sampleRate, config, stepFactor, frequencyOffsetForSpectrogram, silentMode);
    }

    /**
     * @param sampleRate is used to start the decoder with the correct sample rate
     * @param config can be generated with the utility class ConfigFactory and
     *               holds configurations for the decoder
     * @param stepFactor the bigger this factor is, the smaller the step at which we analyze audio
     *                   to look for start/end messages (meaning more analysis and a better chance
     *                   to detect a message).
     * @param frequencyOffsetForSpectrogram 50 is a good default value.
     * @param silentMode
     * @param bandPassFilterOrder
     * @param startFactor value for checking the start block of a message
     * @param endFactor value for checking the end block of a message
     * @return a new SoniTalkDecoder
     */
    private SoniTalkDecoder getDecoder(int sampleRate, SoniTalkConfig config, int stepFactor, int frequencyOffsetForSpectrogram, boolean silentMode, int bandPassFilterOrder, double startFactor, double endFactor) {
        return new SoniTalkDecoder(this, sampleRate, config, stepFactor, frequencyOffsetForSpectrogram, silentMode, bandPassFilterOrder, startFactor, endFactor);
    }

    /**
     * @param config can be generated with the utility class ConfigFactory and
     *               holds configurations for the encoder
     * @return a new SoniTalkEncoder
     */
    public SoniTalkEncoder getEncoder(SoniTalkConfig config){
        return new SoniTalkEncoder(this, config);
    }

    /**
     * @param sampleRate is used to start the encoder with the correct sample rate
     * @param config can be generated with the utility class ConfigFactory and
     *               holds configurations for the encoder
     *
     * @return a new SoniTalkEncoder
     */
    public SoniTalkEncoder getEncoder(int sampleRate, SoniTalkConfig config){
        return new SoniTalkEncoder(this, sampleRate, config);
    }

    /**
     * @return a sender object to transmit the encoded message of the encoder
     */
    public SoniTalkSender getSender(){
        return new SoniTalkSender(this);
    }

    /**
     * @param sampleRate is used to get a sender with the correct sample rate
     * @return a new SoniTalkSender to transmit the encoded message of the encoder
     */
    public SoniTalkSender getSender(int sampleRate){
        return new SoniTalkSender(this, sampleRate);
    }

    /*package-private*/synchronized void showNotificationReceiving() {
        states.remove(State.IDLE);
        states.add(State.RECEIVING);
        NotificationHelper.updateStatusNotification(appContext, states);
    }

    /*package-private*/synchronized void showNotificationSending() {
        states.remove(State.IDLE);
        states.add(State.SENDING);
        NotificationHelper.updateStatusNotification(appContext, states);
    }

    /*package-private*/void cancelNotificationReceiving() {
        states.remove(State.RECEIVING);
        if (states.isEmpty())
            states.add(State.IDLE); // Is the state IDLE needed or should we just leave it empty ?

        NotificationHelper.updateStatusNotification(appContext, states);
    }

    /*package-private*/void cancelNotificationSending() {
        states.remove(State.SENDING);
        if (states.isEmpty())
            states.add(State.IDLE); // Is the state IDLE needed or should we just leave it empty ?

        NotificationHelper.updateStatusNotification(appContext, states);
    }

    /*package-private*/synchronized void initStateAndNotification() {
        // Is it misleading that this also changes the state ?
        states = EnumSet.of(State.IDLE);
        NotificationHelper.updateStatusNotification(appContext, states); //EnumSet.of(State.IDLE));
    }

    /**
     * Created by aringot on 06.02.2018.
     */

    /*package private*/static class NotificationHelper {
        private static final int NOTIFICATION_STATUS_REQUEST_CODE = 2;

        public static final int NOTIFICATION_STATUS_ID = 2;

        public static final String NOTIFICATION_STATUS_CHANNEL_ID = "2";

        private static NotificationCompat.Builder statusBuilder;

        private static Notification notificationStatus;

        private static boolean statusNotitificationFirstBuild = true;

        // TODO: Should our notification open something ?
        /*
        private static PendingIntent getPendingIntentStatusFlagUpdateCurrent(Context context) {
            Intent resultIntent = new Intent(context, MainActivity.class); //the intent is still the main-activity
            resultIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

            return PendingIntent.getActivity(
                    context,
                    NOTIFICATION_STATUS_REQUEST_CODE,
                    resultIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
        }
    */
        /*
         * Used either to cancel the pending intent or to check if it is currently pending.
         * Should always match the intent from {@link #getPendingIntentStatusFlagUpdateCurrent(Context)}
         * (same operation, same Intent action, data, categories, and components, and same flags)
         * See : <a href="https://developer.android.com/reference/android/app/PendingIntent.html">Pending Intent documentation</a>
         * @param context
         * @return StatusPendingIntent if it is pending, null otherwise
         */
        /*
        public static PendingIntent getPendingIntentStatusNoCreate(Context context) {
            Intent resultIntent = new Intent(context, MainActivity.class); //the intent is still the main-activity
            resultIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            return PendingIntent.getActivity(
                    context,
                    NOTIFICATION_STATUS_REQUEST_CODE,
                    resultIntent,
                    PendingIntent.FLAG_NO_CREATE);
        }
    */

        /**
         * Creates the status notification channel for Android O and above. Ignored on older versions.
         * @param context
         */
        private static void createStatusNotificationChannel(Context context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager notificationManagerOreoAbove = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                int channelImportance = NotificationManager.IMPORTANCE_LOW;
                NotificationChannel statusChannel = new NotificationChannel(NOTIFICATION_STATUS_CHANNEL_ID, context.getString(R.string.statusChannelName), channelImportance);
                notificationManagerOreoAbove.createNotificationChannel(statusChannel);
            }
        }

        private static void initStatusNotification(Context context, EnumSet<State> states){
            createStatusNotificationChannel(context);

            String title;
            String content;
            int icon;

            if (states.containsAll(EnumSet.of(State.RECEIVING, State.SENDING))) {
                title = context.getString(R.string.sendingAndReceivingStatusNotificationTitle);
                content = context.getString(R.string.sendingAndReceivingStatusNotificationMessage);
                icon = R.drawable.st_send_white_receive_white;
            }
            else if (states.contains(State.RECEIVING)) {
                title = context.getString(R.string.receivingStatusNotificationTitle);
                content = context.getString(R.string.receivingStatusNotificationMessage);
                icon = R.drawable.st_send_grey_receive_white;
            }
            else if (states.contains(State.SENDING)) {
                title = context.getString(R.string.sendingStatusNotificationTitle);
                content = context.getString(R.string.sendingStatusNotificationMessage);
                icon = R.drawable.st_send_white_receive_grey;
            }
            else {
                title = context.getString(R.string.defaultStatusNotificationTitle);
                content = context.getString(R.string.defaultStatusNotificationMessage);
                icon = R.drawable.st_send_grey_receive_grey;
            }

            statusBuilder = //create a builder for the detection notification
                    new NotificationCompat.Builder(context, NOTIFICATION_STATUS_CHANNEL_ID)
                            .setSmallIcon(icon) //adding the icon
                            .setContentTitle(title) //adding the title
                            .setContentText(content) //adding the text
                            .setPriority(NotificationCompat.PRIORITY_LOW) // Required for Android 7.1 and lower
                            //Requires API 21 .setCategory(Notification.CATEGORY_STATUS)
                            .setOngoing(true); //it's canceled when tapped on it

            PendingIntent resultPendingIntent = null; //TODO: Uncomment to have a clickable notification: getPendingIntentStatusFlagUpdateCurrent(context);

            statusBuilder.setContentIntent(resultPendingIntent);

            notificationStatus = statusBuilder.build(); //build the notification
        }

        /**
         * Update the content of the status notification or remove it depending on the states.
         * For now removes the notification when the states parameter is empty or equal to IDLE.
         * @param context a Context to get access to the Notification Service
         * @param states EnumSet of SoniTalkContext.State
         */
        public static void updateStatusNotification(Context context, EnumSet<State> states){
            // Do we want to show something on IDLE state ?
            if (states.isEmpty() || states.equals(EnumSet.of(State.IDLE))) {
                cancelStatusNotification(context);
            }
            else {
                initStatusNotification(context, states); //initialize the notification
                getNotificationManager(context).notify(NOTIFICATION_STATUS_ID, notificationStatus); //activate the notification with the notification itself and its id
            }
        }

        public static void cancelStatusNotification(Context context){
            getNotificationManager(context).cancel(NOTIFICATION_STATUS_ID); //Cancel the notification with the help of the id
        }

        private static NotificationManagerCompat getNotificationManager(Context context) {
            return NotificationManagerCompat.from(context.getApplicationContext());
        }
    }
}
