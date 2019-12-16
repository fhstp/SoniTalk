/*
 * Copyright (c) 2019. Alexis Ringot, Florian Taurer, Matthias Zeppelzauer.
 *
 * This file is part of SoniTalk Demo app.
 *
 * SoniTalk Demo app is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SoniTalk Demo app is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SoniTalk Demo app.  If not, see <http://www.gnu.org/licenses/>.
 */

package at.ac.fhstp.sonitalk.sonitalkdemo;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import at.ac.fhstp.sonitalk.SoniTalkConfig;
import at.ac.fhstp.sonitalk.SoniTalkContext;
import at.ac.fhstp.sonitalk.SoniTalkDecoder;
import at.ac.fhstp.sonitalk.SoniTalkEncoder;
import at.ac.fhstp.sonitalk.SoniTalkMessage;
import at.ac.fhstp.sonitalk.SoniTalkPermissionsResultReceiver;
import at.ac.fhstp.sonitalk.SoniTalkSender;
import at.ac.fhstp.sonitalk.exceptions.ConfigException;
import at.ac.fhstp.sonitalk.exceptions.DecoderStateException;
import at.ac.fhstp.sonitalk.utils.ConfigFactory;
import at.ac.fhstp.sonitalk.utils.DecoderUtils;
import at.ac.fhstp.sonitalk.utils.EncoderUtils;

import static android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS;
import static at.ac.fhstp.sonitalk.utils.EncoderUtils.calculateNumberOfMessageBlocks;


public class MainActivity extends BaseActivity implements SoniTalkDecoder.MessageListener, SoniTalkPermissionsResultReceiver.Receiver {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 42;
    private String [] PERMISSIONS = {Manifest.permission.RECORD_AUDIO};

    // These request codes are used to know which action was accepted / denied by the user.
    public static final int ON_SENDING_REQUEST_CODE = 2001;
    public static final int ON_RECEIVING_REQUEST_CODE = 2002;

    private SoniTalkPermissionsResultReceiver soniTalkPermissionsResultReceiver;
    private SoniTalkContext soniTalkContext;
    private SoniTalkDecoder soniTalkDecoder;
    private SoniTalkEncoder soniTalkEncoder;
    private SoniTalkMessage currentMessage;
    private SoniTalkSender soniTalkSender;

    private ImageButton btnPlay;
    private EditText edtSignalText;

    private boolean isFirstPlay;
    private int volume = 70;

    private static int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();
    public static final ScheduledExecutorService threadPool = Executors.newScheduledThreadPool(NUMBER_OF_CORES + 1);

    private SharedPreferences sp;

    private AudioTrack playerFrequency;

    private ViewGroup rootViewGroup;

    private ImageButton btnListen;
    private int samplingRate = 44100;
    private int fftResolution = 4410;
    private TextView txtDecodedText;

    boolean silentMode;
    private Toast currentToast;

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.setClassLoader(SoniTalkPermissionsResultReceiver.class.getClassLoader());
        outState.putParcelable(SoniTalkPermissionsResultReceiver.soniTalkPermissionsResultReceiverTag, soniTalkPermissionsResultReceiver);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        rootViewGroup = (ViewGroup) ((ViewGroup) this
                .findViewById(android.R.id.content)).getChildAt(0);

//        if (savedInstanceState != null) {
//            soniTalkPermissionsResultReceiver = savedInstanceState.getParcelable(SoniTalkPermissionsResultReceiver.soniTalkPermissionsResultReceiverTag);
//        }
//        else {
        soniTalkPermissionsResultReceiver = new SoniTalkPermissionsResultReceiver(new Handler());
        //}
        soniTalkPermissionsResultReceiver.setReceiver(this);

        isFirstPlay = true;
        if (soniTalkContext == null) {
            soniTalkContext = SoniTalkContext.getInstance(this, soniTalkPermissionsResultReceiver);
        }

        btnPlay = (ImageButton) findViewById(R.id.btnPlay);
        edtSignalText = (EditText) findViewById(R.id.edtSignalText);

        btnListen = (ImageButton) findViewById(R.id.btnListen);

        txtDecodedText = findViewById(R.id.txtDecodedText);

        btnListen.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                boolean listenButtonState = sp.getBoolean(ConfigConstants.LISTEN_BUTTON_ENABLED,true);
                if(listenButtonState){
                    onButtonStart();
                    //saveButtonState(ConfigConstants.LISTEN_BUTTON_ENABLED,false);
                }else {
                    onButtonStopListening();
                    saveButtonState(ConfigConstants.LISTEN_BUTTON_ENABLED,true);
                }
                //saveButtonStates();
            }
        });

        btnPlay.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                currentMessage = null;
                sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                boolean sendButtonState = sp.getBoolean(ConfigConstants.SEND_BUTTON_ENABLED,true);
                if(sendButtonState){
                    generateMessage();
                    saveButtonState(ConfigConstants.SEND_BUTTON_ENABLED,false);
                }else{
                    stopSending();
                    saveButtonState(ConfigConstants.SEND_BUTTON_ENABLED,true);
                }
                //saveButtonStates();
            }
        });



        sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor ed = sp.edit();
        if(!sp.contains(ConfigConstants.SEND_BUTTON_ENABLED)) {
            sp.edit().putBoolean(ConfigConstants.SEND_BUTTON_ENABLED, true).apply();
        }
        if(!sp.contains(ConfigConstants.LISTEN_BUTTON_ENABLED)) {
            sp.edit().putBoolean(ConfigConstants.LISTEN_BUTTON_ENABLED,true).apply();
        }
        /*if(!sp.contains(ConfigConstants.STOP_LISTEN_BUTTON_ENABLED)){
            sp.edit().putBoolean(ConfigConstants.STOP_LISTEN_BUTTON_ENABLED, false).apply();
        }*/
    }

    public void stopSending(){
        if (soniTalkSender != null) {
            soniTalkSender.cancel();
        }
        saveButtonState(ConfigConstants.SEND_BUTTON_ENABLED,true);
        btnPlay.setImageResource(R.drawable.ic_volume_up_grey_48dp);
    }

    public void releaseSender() {
        if(soniTalkSender != null) {
            soniTalkSender.releaseSenderResources();
        }
        saveButtonState(ConfigConstants.SEND_BUTTON_ENABLED,true);
        btnPlay.setImageResource(R.drawable.ic_volume_up_grey_48dp);
    }

    public void sendMessage(){
        //Log.d("PlayClick","I got clicked");

        if (currentMessage != null) {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            int currentVolume = audioManager.getStreamVolume(3);
            sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            SharedPreferences.Editor ed = sp.edit();
            ed.putInt(ConfigConstants.CURRENT_VOLUME, currentVolume);
            ed.apply();

            int volume = Integer.valueOf(sp.getString(ConfigConstants.LOUDNESS, ConfigConstants.SETTING_LOUDNESS_DEFAULT));
            audioManager.setStreamVolume(3, (int) Math.round((audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * volume/100.0D)), 0);

            if (soniTalkContext == null) {
                soniTalkContext = SoniTalkContext.getInstance(MainActivity.this, soniTalkPermissionsResultReceiver);
            }
            if (soniTalkSender == null) {
                soniTalkSender = soniTalkContext.getSender();
            }
            soniTalkSender.send(currentMessage, ON_SENDING_REQUEST_CODE);

        } else{
            Toast.makeText(getApplicationContext(), getString(R.string.signal_generator_not_created),Toast.LENGTH_LONG).show();
        }
    }

    public void generateMessage(){
        if(playerFrequency!=null){
            playerFrequency.stop();
            playerFrequency.flush();
            playerFrequency.release();
            playerFrequency = null;
            //Log.d("Releaseaudio","playerFrequency releaese");
        }
        //Log.d("GenerateClick","I got clicked");


        sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        int bitperiod = Integer.valueOf(sp.getString(ConfigConstants.BIT_PERIOD, ConfigConstants.SETTING_BIT_PERIOD_DEFAULT));
        int pauseperiod = Integer.valueOf(sp.getString(ConfigConstants.PAUSE_PERIOD, ConfigConstants.SETTING_PAUSE_PERIOD_DEFAULT));
        int f0 = Integer.valueOf(sp.getString(ConfigConstants.FREQUENCY_ZERO, ConfigConstants.SETTING_FREQUENCY_ZERO_DEFAULT));
        int nFrequencies = Integer.valueOf(sp.getString(ConfigConstants.NUMBER_OF_FREQUENCIES, ConfigConstants.SETTING_NUMBER_OF_FREQUENCIES_DEFAULT));
        int frequencySpace = Integer.valueOf(sp.getString(ConfigConstants.SPACE_BETWEEN_FREQUENCIES, ConfigConstants.SETTING_SPACE_BETWEEN_FREQUENCIES_DEFAULT));
        int nMaxBytes = Integer.valueOf(sp.getString(ConfigConstants.NUMBER_OF_BYTES, ConfigConstants.SETTING_NUMBER_OF_BYTES_DEFAULT));

        int nMessageBlocks = calculateNumberOfMessageBlocks(nFrequencies, nMaxBytes); // We want 10 message blocks by default
        SoniTalkConfig config = new SoniTalkConfig(f0, bitperiod, pauseperiod, nMessageBlocks, nFrequencies, frequencySpace);
        if (soniTalkContext == null) {
            soniTalkContext = SoniTalkContext.getInstance(MainActivity.this, soniTalkPermissionsResultReceiver);
        }
        soniTalkEncoder = soniTalkContext.getEncoder(config);

        //final String textToSend = sp.getString(ConfigConstants.SIGNAL_TEXT,"Hallo Sonitalk");
        final String textToSend = edtSignalText.getText().toString();
        //Log.d("changeToBitStringUTF8",String.valueOf(isUTF8MisInterpreted(textToSend, "Windows-1252")));

        sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if(!sp.contains(ConfigConstants.TEXT_TO_SEND)){
            SharedPreferences.Editor ed = sp.edit();
            ed.putString(ConfigConstants.TEXT_TO_SEND, textToSend);
            ed.apply();
        }

        if(sp.getString(ConfigConstants.TEXT_TO_SEND,"Hallo SoniTalk").equals(textToSend) && currentMessage != null){
            sendMessage();
        }else {
            SharedPreferences.Editor ed = sp.edit();
            ed.putString(ConfigConstants.TEXT_TO_SEND, textToSend);
            ed.apply();

            final byte[] bytes = textToSend.getBytes(StandardCharsets.UTF_8);

            if (textToSend.length() > nMaxBytes) {
                Toast.makeText(getApplicationContext(), getString(R.string.encoder_exception_text_too_long), Toast.LENGTH_LONG).show();
            } else if(!EncoderUtils.isAllowedByteArraySize(bytes, config)){
                Toast.makeText(getApplicationContext(), getString(R.string.encoder_exception_text_too_long), Toast.LENGTH_LONG).show();
            } else {
                // Move the background execution handling away from the Activity (in Encoder or Service or AsyncTask). Creating Runnables here may leak the Activity
                threadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        currentMessage = soniTalkEncoder.generateMessage(bytes);
                        sendMessage();
                    }
                });
            }
        }
    }

    public static boolean isUTF8MisInterpreted( String input, String encoding) {

        CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
        CharsetEncoder encoder = Charset.forName(encoding).newEncoder();
        ByteBuffer tmp;

        try {
            tmp = encoder.encode(CharBuffer.wrap(input));
        }

        catch(CharacterCodingException e) {
            //Log.d("isUTF8Mis", e.toString());
            return false;
        }

        try {
            decoder.decode(tmp);
            return true;
        }
        catch(CharacterCodingException e){
            //Log.d("isUTF8Mis", e.toString());
            return false;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        getAndReinitilizeButtonStates();
        soniTalkPermissionsResultReceiver.setReceiver(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkFirstRunForWelcomeShowing();
        getAndReinitilizeButtonStates();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopDecoder();
        releaseSender();
        setReceivedText("");

        if (currentToast != null) {
            currentToast.cancel();
        }
        currentToast = Toast.makeText(MainActivity.this, "Demo stops.", Toast.LENGTH_SHORT);
        currentToast.show();

        //saveButtonStates();
        soniTalkPermissionsResultReceiver.setReceiver(null);
    }

    @Override
    protected void onPause(){
        super.onPause();
        //saveButtonStates();
    }

    private void onButtonStart(){
        if(!hasPermissions(MainActivity.this, PERMISSIONS)){
            //Log.d(TAG, "Clicked on start, asking for audio permission");
            requestAudioPermission();
        }
        else {
            //Log.d(TAG, "Clicked on start, audio permission already granted");
            startDecoder();
        }
    }

    private void startDecoder() {
        int frequencyOffsetForSpectrogram = 50;
        int stepFactor = 8;

        sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        int bitperiod = Integer.valueOf(sp.getString(ConfigConstants.BIT_PERIOD, ConfigConstants.SETTING_BIT_PERIOD_DEFAULT));
        int pauseperiod = Integer.valueOf(sp.getString(ConfigConstants.PAUSE_PERIOD, ConfigConstants.SETTING_PAUSE_PERIOD_DEFAULT));
        int f0 = Integer.valueOf(sp.getString(ConfigConstants.FREQUENCY_ZERO, ConfigConstants.SETTING_FREQUENCY_ZERO_DEFAULT));
        int nFrequencies = Integer.valueOf(sp.getString(ConfigConstants.NUMBER_OF_FREQUENCIES, ConfigConstants.SETTING_NUMBER_OF_BYTES_DEFAULT));
        int frequencySpace = Integer.valueOf(sp.getString(ConfigConstants.SPACE_BETWEEN_FREQUENCIES, ConfigConstants.SETTING_SPACE_BETWEEN_FREQUENCIES_DEFAULT));
        int nMaxBytes = Integer.valueOf(sp.getString(ConfigConstants.NUMBER_OF_BYTES, ConfigConstants.SETTING_NUMBER_OF_BYTES_DEFAULT));

        try {
            SoniTalkConfig config = ConfigFactory.getDefaultConfig(this.getApplicationContext());
            // Note: here for debugging purpose we allow to change almost all the settings of the protocol.
            config.setFrequencyZero(f0);
            config.setBitperiod(bitperiod);
            config.setPauseperiod(pauseperiod);
            int nMessageBlocks = calculateNumberOfMessageBlocks(nFrequencies, nMaxBytes);// Default is 10 (transmitting 20 bytes with 16 frequencies)
            config.setnMessageBlocks(nMessageBlocks);
            config.setnFrequencies(nFrequencies);
            config.setFrequencySpace(frequencySpace);

            // Testing usage of a config file placed in the final-app asset folder.
            // SoniTalkConfig config = ConfigFactory.loadFromJson("lowFrequenciesConfig.json", this.getApplicationContext());

            if (soniTalkContext == null) {
                soniTalkContext = SoniTalkContext.getInstance(this, soniTalkPermissionsResultReceiver);
            }
            soniTalkDecoder = soniTalkContext.getDecoder(samplingRate, config); //, stepFactor, frequencyOffsetForSpectrogram, silentMode);
            soniTalkDecoder.addMessageListener(this); // MainActivity will be notified of messages received (calls onMessageReceived)
            //soniTalkDecoder.addSpectrumListener(this); // Can be used to receive the spectrum when a message is decoded.

            // Should not throw the DecoderStateException as we just initialized the Decoder
            soniTalkDecoder.receiveBackground(ON_RECEIVING_REQUEST_CODE);

        } catch (DecoderStateException e) {
            setReceivedText(getString(R.string.decoder_exception_state) + e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, getString(R.string.decoder_exception_io) + e.getMessage());
        } catch (ConfigException e) {
            Log.e(TAG, getString(R.string.decoder_exception_config) + e.getMessage());
        }

    }

    public void onButtonStopListening(){
        stopDecoder();
        setReceivedText("");
    }

    private void stopDecoder() {
        saveButtonState(ConfigConstants.LISTEN_BUTTON_ENABLED,true);
        btnListen.setImageResource(R.drawable.baseline_hearing_grey_48);

        if (soniTalkDecoder != null) {
            soniTalkDecoder.stopReceiving();
        }
        soniTalkDecoder = null;
    }

    public void setReceivedText(String decodedText){
        txtDecodedText.setText(decodedText);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 0) {
            //we will show an explanation next time the user click on start
            showRequestPermissionExplanation(R.string.permissionRequestExplanation);
        }
        switch (requestCode){
            case REQUEST_RECORD_AUDIO_PERMISSION:
                if(grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startDecoder();
                }
                else if(grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    showRequestPermissionExplanation(R.string.permissionRequestExplanation);
                }
                break;
        }
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    private void showRequestPermissionExplanation(int messageId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage(messageId);
        builder.setPositiveButton(R.string.permission_request_explanation_positive,new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent();
                        intent.setAction(ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                        intent.setData(uri);
                        startActivity(intent);
                    }
                }
        );
        builder.setNegativeButton(R.string.permission_request_explanation_negative, null);
        builder.show();
    }

    public void requestAudioPermission() {
        Log.i(TAG, "Audio permission has NOT been granted. Requesting permission.");
        // If an explanation is needed
        if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                Manifest.permission.RECORD_AUDIO)) {
            Log.i(TAG,"Displaying audio permission rationale to provide additional context.");
            Snackbar.make(rootViewGroup, R.string.permissionRequestExplanation,
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction("OK", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.RECORD_AUDIO},
                                    REQUEST_RECORD_AUDIO_PERMISSION);
                        }
                    })
                    .show();
        } else {
            // First time, no explanation needed, we can request the permission.
            ActivityCompat.requestPermissions(MainActivity.this, PERMISSIONS, REQUEST_RECORD_AUDIO_PERMISSION);
        }
    }

    @Override
    public void onMessageReceived(final SoniTalkMessage receivedMessage) {
        /*
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setReceivedText(DecoderUtils.byteToUTF8(receivedMessage));
            }
        });
        */

        if(receivedMessage.isCrcCorrect()){
            //Log.d("ParityCheck", "The message was correctly received");
            final String textReceived = DecoderUtils.byteToUTF8(receivedMessage.getMessage());
            //Log.d("Received message", textReceived);

            // We stop when CRC is correct and we are not in silent mode
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // Update text displayed
                    setReceivedText(textReceived + " (" + String.valueOf(receivedMessage.getDecodingTimeNanosecond() / 1000000) + "ms)");

                    if (currentToast != null) {
                        currentToast.cancel(); // NOTE: Cancel so fast that only the last one in a series really is displayed.
                    }
                    // Stops recording if needed and shows a Toast
                    if (!silentMode) {
                        // STOP everything.
                        stopDecoder();
//                        currentToast = Toast.makeText(MainActivity.this, "Correctly received a message. Stopped.", Toast.LENGTH_SHORT);
//                        currentToast.show();
                    } else {
//                        currentToast = Toast.makeText(MainActivity.this, "Correctly received a message. Keep listening.", Toast.LENGTH_SHORT);
//                        currentToast.show();
                    }
                }
            });
        } else {
            //Log.d("ParityCheck", "The message was NOT correctly received");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //main.setReceivedText("Please try again, could not detect or decode the message!");

                    if (currentToast != null) {
                        currentToast.cancel(); // NOTE: Cancel so fast that only the last one in a series really is displayed.
                    }
                    if (!silentMode) {
                        setReceivedText(getString(R.string.detection_crc_incorrect));
//                        currentToast = Toast.makeText(MainActivity.this, getString(R.string.detection_crc_incorrect_toast_message), Toast.LENGTH_LONG);
//                        currentToast.show();
                    }
                    else {
                        setReceivedText(getString(R.string.detection_crc_incorrect_keep_listening));
//                        currentToast = Toast.makeText(MainActivity.this, getString(R.string.detection_crc_incorrect_keep_listening_toast_message), Toast.LENGTH_LONG);
//                        currentToast.show();
                    }
                }
            });
        }
    }

    @Override
    public void onDecoderError(final String errorMessage) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                // STOP everything.
                stopDecoder();
                setReceivedText(errorMessage);
            }
        });
    }

    @Override
    public void onSoniTalkPermissionResult(int resultCode, Bundle resultData) {
        int actionCode = 0;
        if(resultData != null){
            actionCode = resultData.getInt(getString(R.string.bundleRequestCode_key));
        }
        switch (resultCode) {
            case SoniTalkContext.ON_PERMISSION_LEVEL_DECLINED:
                //Log.d(TAG, "onSoniTalkPermissionResult ON_PERMISSION_LEVEL_DECLINED");
                //Log.d(TAG, String.valueOf(resultData.getInt(getString(R.string.bundleRequestCode_key), 0)));

                if (currentToast != null) {
                    currentToast.cancel();
                }
                switch (actionCode) {
                    case ON_RECEIVING_REQUEST_CODE:
                        currentToast = Toast.makeText(MainActivity.this, getString(R.string.on_receiving_listening_permission_required), Toast.LENGTH_SHORT);
                        currentToast.show();
                        //onButtonStopListening();

                        // Set buttons in the state NOT RECEIVING
                        saveButtonState(ConfigConstants.LISTEN_BUTTON_ENABLED,true);
                        btnListen.setImageResource(R.drawable.baseline_hearing_grey_48);

                        break;
                    case ON_SENDING_REQUEST_CODE:
                        currentToast = Toast.makeText(MainActivity.this, getString(R.string.on_sending_sending_permission_required), Toast.LENGTH_SHORT);
                        currentToast.show();

                        // Set buttons in the state NOT RECEIVING
                        saveButtonState(ConfigConstants.SEND_BUTTON_ENABLED,true);
                        btnPlay.setImageResource(R.drawable.ic_volume_up_grey_48dp);

                        break;
                }


                break;
            case SoniTalkContext.ON_REQUEST_GRANTED:
                //Log.d(TAG, "ON_REQUEST_GRANTED");
                //Log.d(TAG, String.valueOf(resultData.getInt(getString(R.string.bundleRequestCode_key), 0)));

                switch (actionCode){
                    case ON_RECEIVING_REQUEST_CODE:
                        // Set buttons in the state RECEIVING
                        saveButtonState(ConfigConstants.LISTEN_BUTTON_ENABLED,false);
                        btnListen.setImageResource(R.drawable.baseline_hearing_orange_48);

                        setReceivedText(getString(R.string.decoder_start_text));
                        break;

                    case ON_SENDING_REQUEST_CODE:
                        saveButtonState(ConfigConstants.SEND_BUTTON_ENABLED,false);
                        btnPlay.setImageResource(R.drawable.ic_volume_up_orange_48dp);

                        //saveButtonStates();
                        break;
                }

                break;
            case SoniTalkContext.ON_REQUEST_DENIED:
                //Log.d(TAG, "ON_REQUEST_DENIED");
                //Log.d(TAG, String.valueOf(resultData.getInt(getString(R.string.bundleRequestCode_key), 0)));

                if (currentToast != null) {
                    currentToast.cancel();
                }

                // Checks the requestCode to adapt the UI depending on the action type (receiving or sending)
                switch (actionCode) {
                    case ON_RECEIVING_REQUEST_CODE:
                        //showRequestPermissionExplanation(R.string.on_receiving_listening_permission_required);
                        // Set buttons in the state NOT RECEIVING
                        currentToast = Toast.makeText(MainActivity.this, getString(R.string.on_receiving_listening_permission_required), Toast.LENGTH_SHORT);
                        currentToast.show();
                        saveButtonState(ConfigConstants.LISTEN_BUTTON_ENABLED,true);
                        btnListen.setImageResource(R.drawable.baseline_hearing_grey_48);

                        break;
                    case ON_SENDING_REQUEST_CODE:
                        //showRequestPermissionExplanation(R.string.on_sending_sending_permission_required);
                        currentToast = Toast.makeText(MainActivity.this, getString(R.string.on_sending_sending_permission_required), Toast.LENGTH_SHORT);
                        currentToast.show();
                        saveButtonState(ConfigConstants.SEND_BUTTON_ENABLED,true);
                        btnPlay.setImageResource(R.drawable.ic_volume_up_grey_48dp);

                }
                break;

            case SoniTalkContext.ON_SEND_JOB_FINISHED:
                saveButtonState(ConfigConstants.SEND_BUTTON_ENABLED,true);
                btnPlay.setImageResource(R.drawable.ic_volume_up_grey_48dp);
                sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                int currentVolume = sp.getInt(ConfigConstants.CURRENT_VOLUME, ConfigConstants.CURRENT_VOLUME_DEFAULT);
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                audioManager.setStreamVolume(3, currentVolume, 0);

                break;

            case SoniTalkContext.ON_SHOULD_SHOW_RATIONALE_FOR_ALLOW_ALWAYS:
                /*if (currentToast != null) {
                    currentToast.cancel();
                }
                currentToast = Toast.makeText(MainActivity.this, "Choosing Allow always requires you to accept the Android permission", Toast.LENGTH_LONG);
                currentToast.show();*/
                break;

            case SoniTalkContext.ON_REQUEST_L0_DENIED:
                //Log.d(TAG, "ON_REQUEST_L0_DENIED");
                switch (actionCode) {
                    case ON_RECEIVING_REQUEST_CODE:
                        showRequestPermissionExplanation(R.string.on_receiving_listening_permission_required);
                        saveButtonState(ConfigConstants.LISTEN_BUTTON_ENABLED,true);
                        btnListen.setImageResource(R.drawable.baseline_hearing_grey_48);

                        break;
                    case ON_SENDING_REQUEST_CODE:
                        showRequestPermissionExplanation(R.string.on_sending_sending_permission_required);
                        saveButtonState(ConfigConstants.SEND_BUTTON_ENABLED,true);
                        btnPlay.setImageResource(R.drawable.ic_volume_up_grey_48dp);

                }
                break;

            default:
                Log.w(TAG, "onSoniTalkPermissionResult unknown resultCode: " + resultCode);
                break;

        }
    }

    public void saveButtonState(String button, boolean state){
        sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor ed = sp.edit();
        ed.putBoolean(button,state);
        ed.apply();
    }


    public void getAndReinitilizeButtonStates(){
        sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean sendButtonState = sp.getBoolean(ConfigConstants.SEND_BUTTON_ENABLED,true);
        boolean listenButtonState = sp.getBoolean(ConfigConstants.LISTEN_BUTTON_ENABLED,true);
        if(listenButtonState) {
            btnListen.setImageResource(R.drawable.baseline_hearing_grey_48);
        }else{
            btnListen.setImageResource(R.drawable.baseline_hearing_orange_48);
        }
        if(sendButtonState) {
            btnPlay.setImageResource(R.drawable.ic_volume_up_grey_48dp);
        }else{
            btnPlay.setImageResource(R.drawable.ic_volume_up_orange_48dp);
        }
    }

    public void onFirstOpeningShowWelcome(){
        String instructionsText = String.format(getApplicationContext().getString(R.string.instructions_text), getApplicationContext().getString(R.string.detection_crc_incorrect));
        new android.app.AlertDialog.Builder(this).setTitle(R.string.instructions_title).setMessage(instructionsText).setPositiveButton("OK", null).show();
    }

    public void checkFirstRunForWelcomeShowing() {
        sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean isFirstRun = sp.getBoolean("isFirstRun", true);
        if (isFirstRun){
            onFirstOpeningShowWelcome();
            sp
                    .edit()
                    .putBoolean("isFirstRun", false)
                    .apply();
        }
    }
}
