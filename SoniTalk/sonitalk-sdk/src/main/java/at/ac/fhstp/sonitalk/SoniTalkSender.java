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


import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.audiofx.LoudnessEnhancer;
import android.os.Build;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Handles the sendjob and playing of the actual audiotrack.
 */
public class SoniTalkSender {
    private final String TAG = this.getClass().getSimpleName();
    private final SoniTalkContext soniTalkContext;

    // Define the list of accepted constants for SenderState annotation
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({STATE_IDLE, STATE_SENDING/*, STATE_PAUSED, STATE_STOPPED*/})
            /*package-private*/ @interface SenderState {}

    // SenderState constants
    /*package-private*/ static final int STATE_IDLE = 0;
    /*package-private*/ static final int STATE_SENDING = 1;
    //public static final int STATE_PAUSED = 2;
    //public static final int STATE_STOPPED = 3;

    private int senderState = STATE_IDLE;
    private int Fs;
    private SoniTalkMessage currentMessage;
    private AudioTrack currentAudioTrack;
    private Future<?> currentFuture;
    private int maxRunCount = -1;
    private int runCount = 0;
    private final ScheduledExecutorService executorService;

    private int currentRequestCode;

    /**
     * Default constructor using a 44100Hz sample rate (works on all devices)
     */
    /*package private*/SoniTalkSender(SoniTalkContext soniTalkContext) {
        this(soniTalkContext, 44100);
    }

    /*package private*/SoniTalkSender(SoniTalkContext soniTalkContext, int fs) {
        this.soniTalkContext = soniTalkContext;
        Fs = fs;
        executorService = Executors.newScheduledThreadPool(1);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            ((ScheduledThreadPoolExecutor) executorService).setRemoveOnCancelPolicy(true);
        }
    }

    /**
     * Sends a SoniTalkMessage once. Should not be called when the state is already STATE_SENDING.
     * @param message
     */
    public void send(@NonNull SoniTalkMessage message, int requestCode) {
        send(message, 1, 0, TimeUnit.MILLISECONDS, requestCode);
    }

    /**
     * Sends a SoniTalkMessage nTimes at a fixed delay. Should not be called when the state is
     * already STATE_SENDING.
     * @param message the message to be sent (generated via SoniTalkEncoder)
     * @param nTimes the amount of times the message should be sent
     * @param interval delay between messages (fixed delay between the end of one message and the start of the next)
     * @param timeUnit the time unit of the interval parameter
     */
    public void send(@NonNull final SoniTalkMessage message, final int nTimes, final long interval, @NonNull final TimeUnit timeUnit, final int requestCode) {
        this.currentRequestCode = requestCode;

        if (nTimes < 1) {
            throw new IllegalArgumentException("You cannot send a message less than one time.");
        }
        if (getSenderState() == STATE_SENDING) {
            throw new IllegalStateException("send() called on SoniTalkSender already sending.");
        }

        if (currentMessage != null && !currentMessage.equals(message)) {// if SoniTalkMessage is new, release the old audiotrack
            //Log.d("SoniTalkSender", "currentAudioTrack release");
            releaseSenderResources();
        }
        //else sender is reused
        Future job = executorService.submit(new Runnable() {
            @Override
            public void run() {
                if (! soniTalkContext.checkSelfPermission(requestCode)) {
                    // Make a SoniTalkException out of this ? (currently send a callback to the developer)
                    Log.w(TAG, "SoniTalkSender requires a permission from SoniTalkContext.");
                    return;//throw new SecurityException("SoniTalkDecoder requires a permission from SoniTalkContext. Use SoniTalkContext.checkSelfPermission() to make sure that you have the right permission.");
                }

                runCount = 0;
                maxRunCount = nTimes;
                int winLenSamples = message.getRawAudio().length;
                if(winLenSamples%2 == 1){
                    winLenSamples+=1; //if the windowSamples are odd, we have to add 1 sample because audiotrack later needs an even buffersize
                }
                // If we have a new message, we create an AudioTrack
                if (currentMessage == null || !currentMessage.equals(message)) {
                    currentAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, Fs, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, (winLenSamples/*+(winLenSamples/65)*/) * 2, AudioTrack.MODE_STATIC); //creating the audiotrack player with winLenSamples*2 as the buffersize because the constructor wants bytes
                }
                currentMessage = message;

                currentAudioTrack.setNotificationMarkerPosition(winLenSamples);

                currentAudioTrack.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener(){
                    @Override
                    public void onMarkerReached(AudioTrack arg0) {
                        soniTalkContext.cancelNotificationSending();
                        // TODO: Callback to signal message sent ? (different from sending job end). Would we then also need a callback when we start sending the next one ?

                        runCount++;
                        if (maxRunCount <= runCount) {
                            cancel();
                        } else {
                            currentAudioTrack.stop();
                            currentAudioTrack.flush();
                            currentAudioTrack.reloadStaticData();
                            // For fine grained state, put IDLE here and SENDING after the play(). This may lead to overlaps.
                            Future job = executorService.schedule(new Runnable() {
                                @Override
                                public void run() {
                                    soniTalkContext.showNotificationSending();
                                    currentAudioTrack.play();
                                }
                            }, interval, timeUnit);
                            currentFuture = job;
                        }
                    }
                    @Override
                    public void onPeriodicNotification(AudioTrack arg0) {}
                });
                // Increase the loudness of our signal
                LoudnessEnhancer enhancer = new LoudnessEnhancer(currentAudioTrack.getAudioSessionId());
                enhancer.setTargetGain(700);
                enhancer.setEnabled(true);

                /* Do we want to handle the audio volume here ? Then pass context ?
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                audioManager.setStreamVolume(3, (int) Math.round((audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * volume/100.0D)), 0);
                */
                // Should we handle potential errors (negative results)
                int result = currentAudioTrack.write(message.getRawAudio(), 0, (winLenSamples/*+(winLenSamples/65)*/)); //put the whiteNoise shortarray into the player, buffersize winLenSamples are Shorts here

                soniTalkContext.showNotificationSending();
                setSenderState(STATE_SENDING);
                currentAudioTrack.play();
            }
        });
        currentFuture = job;


        //return job;
    }

    /**
     * Cancels the current send job if there was one running.
     * @return true when the state allowed to cancel.
     */
    public boolean cancel() {
        if (getSenderState() != STATE_SENDING) {
            Log.w(TAG, "cancel() called on unstarted SoniTalkSender.");
            return false;
        }
        else {
            if (currentFuture != null) {
                currentFuture.cancel(false);
            }
            if (currentAudioTrack != null) {
                currentAudioTrack.stop();
                currentAudioTrack.flush();
                currentAudioTrack.reloadStaticData();
            }

            soniTalkContext.cancelNotificationSending();
            setSenderState(STATE_IDLE);

            soniTalkContext.sendJobFinished(currentRequestCode);
            maxRunCount = -1;
            runCount = 0;
            return true;
        }
    }

    /* Do we need pause and resume ?
    public void pause() {
        if (getSenderState() != STATE_SENDING) {
            throw new IllegalStateException("pause() called on unstarted SoniTalkSender.");
        }
        currentFuture.cancel(false);
        currentAudioTrack.stop();
        currentAudioTrack.flush();
        currentAudioTrack.reloadStaticData();
        // Change state to IDLE ?
        setSenderState(STATE_PAUSED);
    }

    public void resume() {
        if (getSenderState() != STATE_PAUSED) {
            throw new IllegalStateException("resume() called on unpaused SoniTalkSender.");
        }
        // Make a function that only do the needed parts ?
        //send(currentMessage, ?);
    }*/


    @SenderState
    private synchronized int getSenderState() {
        return senderState;
    }

    private synchronized void setSenderState(@SenderState int senderState) {
        this.senderState = senderState;
    }

    /**
     * Release the AudioTrack object. This method should be called when you are done with the SoniTalkSender, e.g. in onStop().
     */
    public void releaseSenderResources() {
        cancel();
        if (currentAudioTrack != null){
            currentAudioTrack.release();
            currentAudioTrack = null;
            currentMessage = null;
        }
    }
}
