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
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.support.annotation.IntDef;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import at.ac.fhstp.sonitalk.exceptions.DecoderStateException;
import at.ac.fhstp.sonitalk.utils.CRC;
import at.ac.fhstp.sonitalk.utils.CircularArray;
import at.ac.fhstp.sonitalk.utils.ConfigConstants;
import at.ac.fhstp.sonitalk.utils.DecoderUtils;
import at.ac.fhstp.sonitalk.utils.HammingWindow;
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;
import marytts.util.math.ComplexArray;
import marytts.util.math.Hilbert;
import uk.me.berndporr.iirj.Butterworth;

/**
 * Handles the capture of audio, the detection of messages and their decoding. The receiveBackground
 * functions execute in a worker Thread and need to be stopped when your application stops. Please
 * call stopReceiving() when you are done with receiving to release the resources (e.g. microphone access)
 */
public class SoniTalkDecoder {
    private static final String TAG = SoniTalkDecoder.class.getSimpleName();
    private final SoniTalkContext soniTalkContext;

    // Define the list of accepted constants for DecoderState annotation
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({STATE_INITIALIZED, STATE_LISTENING, STATE_CANCELLED, STATE_STOPPED})
    public @interface DecoderState {}

    public interface MessageListener {
        void onMessageReceived(SoniTalkMessage receivedMessage);
        void onDecoderError(String errorMessage);
    }
    public interface SpectrumListener {
        void onSpectrum(float[][] spectrum, boolean crcIsCorrect);
    }

    // DecoderState constants
    public static final int STATE_INITIALIZED = 0;
    public static final int STATE_LISTENING = 1;
    public static final int STATE_CANCELLED = 2;
    public static final int STATE_STOPPED = 3;

    private List<MessageListener> messageListeners = new ArrayList<>();
    private List<SpectrumListener> spectrumListeners = new ArrayList<>();

    private AudioRecord audioRecorder;

    // Needed for the encoder part
    private int bitperiodInSamples;
    private int pauseperiodInSamples;

    private boolean silentMode = false;// Skips the viz ?
    private boolean returnRawAudio = false;

    // AudioRecord doc says: "The sample rate expressed in Hertz. 44100Hz is currently the only rate that is guaranteed to work on all devices"
    private int Fs; // Should always be larger than two times the f0

    // Profile
    private SoniTalkConfig config;

    // Recognition parameter
    private double startFactor;// = 2.0;
    private double endFactor;// = 2.0;
    private int bandPassFilterOrder;// = 8;
    private int stepFactor;// = 8;

    int nNeighborsFreqUpDown = 1;
    int nNeighborsTimeLeftRight = 1;
    String aggFcn = "median";

    private int requestCode;


    /**
     * nBlocks refers to the previous naming. It corresponds to 2 + (nMessageBlocks * 2)
     * The current specification groups the two "blocks" of each bit (hence the     * 2).
     */
    private int nBlocks;
    private int frequencies[];
    private int bandpassWidth;// = frequencySpace*(nFrequencies/2);
    private int winLenForSpectrogram;
    private int winLenForSpectrogramInSamples ;
    private int frequencyOffsetForSpectrogram;// = 50;

    private int analysisWinLen;
    private int analysisWinStep;
    private int nAnalysisWindowsPerBit;
    private int nAnalysisWindowsPerPause;
    private int historyBufferSize;
    private int audioRecorderBufferSize;
    private int minBufferSize;
    private int addedLen;

    private final CircularArray historyBuffer;

    private boolean loopStopped = false;
    private Handler delayhandler = new Handler();
    private ExecutorService threadExecutor = Executors.newSingleThreadExecutor();
    private int decoderState = STATE_INITIALIZED;

    private long readTimestamp;

    private CRC crc;

    /*package private*/SoniTalkDecoder(SoniTalkContext soniTalkContext, int sampleRate, SoniTalkConfig config) {
        this(soniTalkContext, sampleRate, config, 8, 50, false);
    }

    /*package private*/SoniTalkDecoder(SoniTalkContext soniTalkContext, int sampleRate, SoniTalkConfig config, int stepFactor, int frequencyOffsetForSpectrogram, boolean silentMode) {
        this(soniTalkContext, sampleRate, config, stepFactor, frequencyOffsetForSpectrogram, silentMode, 8, 2.0, 2.0);
    }

    /*package private*/SoniTalkDecoder(SoniTalkContext soniTalkContext, int sampleRate, SoniTalkConfig config, int stepFactor, int frequencyOffsetForSpectrogram, boolean silentMode, int bandPassFilterOrder, double startFactor, double endFactor) {
        this.soniTalkContext = soniTalkContext;

        this.Fs = sampleRate;
        this.config = config;
        this.crc = new CRC();

        int f0 = config.getFrequencyZero();
        if ((f0*2) > Fs) {
            throw new IllegalArgumentException("Sample rate cannot be lower than two times the frequency zero. Please try a sample rate of 44100Hz and f0 under 22050Hz");
        }
        int bitperiod = config.getBitperiod();
        int pauseperiod = config.getPauseperiod();
        int nMessageBlocks = config.getnMessageBlocks();
        int nFrequencies = config.getnFrequencies();
        int frequencySpace = config.getFrequencySpace();

        this.silentMode = silentMode;
        this.frequencyOffsetForSpectrogram = frequencyOffsetForSpectrogram;
        this.stepFactor = stepFactor;
        this.bandPassFilterOrder = bandPassFilterOrder;
        this.startFactor = startFactor;
        this.endFactor = endFactor;


        //Log.d("AllSettings", "silentmode: " + String.valueOf(silentMode) + " f0: " + f0 + " bitperiod: " + bitperiod + " pauseperiod: " + pauseperiod + " maxChar: " + nMaxCharacters + " nFreq: " + nFrequencies + " freqSpacing: " + frequencySpace + " freqOffSpec: " + frequencyOffsetForSpectrogram + " stepFactor: " + stepFactor);
        /*Log.d("AllSettings", "silentmode: " + String.valueOf(this.silentMode));
        Log.d("AllSettings", "f0: " + f0);
        Log.d("AllSettings", "bitperiod: " + bitperiod);
        Log.d("AllSettings", "pauseperiod: " + pauseperiod);
        Log.d("AllSettings", "maxChar: " + nMaxCharacters);
        Log.d("AllSettings", "nFreq: " + nFrequencies);
        Log.d("AllSettings", "freqSpacing: " + frequencySpace);
        Log.d("AllSettings", "freqOffSpec: " + this.frequencyOffsetForSpectrogram);
        Log.d("AllSettings", "stepFactor: " + this.stepFactor);
        */

        bandpassWidth = frequencySpace *(nFrequencies /2);

        winLenForSpectrogram = bitperiod;
        winLenForSpectrogramInSamples = Math.round(Fs * (float) winLenForSpectrogram/1000);
        if (winLenForSpectrogramInSamples % 2 != 0) {
            winLenForSpectrogramInSamples ++; // Make sure winLenForSpectrogramInSamples is even
        }
        //Log.d("AllSettings", "SpectLen in Samples: " + String.valueOf(winLenForSpectrogramInSamples));

        frequencies = new int[nFrequencies];
        for(int i = 0; i < nFrequencies; i++){
            frequencies[i] = f0 + frequencySpace *i;
        }
        this.nBlocks = (int)Math.ceil(nMessageBlocks*2)+2;
        this.bitperiodInSamples = (int)Math.round(bitperiod * (float)sampleRate/1000);
        this.pauseperiodInSamples = (int)Math.round(pauseperiod * (float)sampleRate/1000);

        //analysisWinLen = getMinWinLenDividableByStepFactor((int)Math.ceil(bitperiodInSamples), stepFactor);
        analysisWinLen = (int)Math.round((float) bitperiodInSamples / 2 );
        analysisWinStep = (int)Math.round((float) analysisWinLen/ this.stepFactor);

        nAnalysisWindowsPerBit =  Math.round((bitperiodInSamples+pauseperiodInSamples)/(float)analysisWinStep); //number of analysis windows of bit+pause
        nAnalysisWindowsPerPause =  Math.round(pauseperiodInSamples/(float)analysisWinStep) ; //number of analysis windows during a pause

        addedLen = analysisWinLen; // Needed for stepping analysis
        historyBufferSize = ((bitperiodInSamples*nBlocks+pauseperiodInSamples*(nBlocks-1)));
        //Log.d("HistoryBufferSize", historyBufferSize +"");
        //Log.d("nBlocks", nBlocks +"");
        historyBuffer = new CircularArray(historyBufferSize);
        //analysisWinBuffer = new float[analysisWinLen];
        //historyBuffer1D = new float[analysisWinLen*10];
        //Log.d(TAG, "analysiswinlen: " + this.analysisWinLen);
        //Log.d(TAG, "analysiswinstep: " + this.analysisWinStep);
        //Log.d(TAG, "historybuffer1d: " + this.historyBuffer1D.length);

        audioRecorder = getInitializedAudioRecorder();
        //Log.d(TAG, "Decoder default priority: " + String.valueOf(this.getPriority()));
        //this.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
        //Log.d(TAG, "Decoder now in background priority: " + String.valueOf(this.getPriority()));
    }

    /**
     * Checks the microphone permission and the data-over-sound permission before it
     * starts the audiorecording. Every chunk of audio data will be added to the
     * historyBuffer. While the loop is running and it is not stopped it records data.
     * As soon as the historyBuffer is full every, it will be analyzed every loop run.
     */
    private void startDecoding() {
        if (! soniTalkContext.checkMicrophonePermission()) {
            throw new SecurityException("Does not have android.permission.RECORD_AUDIO.");
        }
        if ( ! soniTalkContext.checkSelfPermission(requestCode)) {
            // Make a SoniTalkException out of this ? (currently send a callback to the developer)
            Log.w(TAG, "SoniTalkDecoder requires a permission from SoniTalkContext.");
            return;//throw new SecurityException("SoniTalkDecoder requires a permission from SoniTalkContext. Use SoniTalkContext.checkSelfPermission() to make sure that you have the right permission.");
        }
        soniTalkContext.showNotificationReceiving();

        int readBytes = 0;
        int neededBytes = analysisWinStep;
        int counter = 1;
        int analysisCounter = 0;

        short tempBuffer[] = new short[neededBytes];
        float currentData[] = new float[neededBytes];

        // If the audio recorder couldn't be initialized
        if (audioRecorder == null) {
            // Generate a new Audio Decoder
            audioRecorder = getInitializedAudioRecorder();
        }

        try {
            audioRecorder.startRecording();

            // Wait until the audio recorder records ...
            if (audioRecorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                try {
                    Log.e("AudiorecorderState", "Not recording, calling thread.sleep");
                    Thread.sleep(10);
                    if (audioRecorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                        notifyMessageListenersOfError("The microphone is not available.");
                        if (audioRecorder.getState() == AudioRecord.STATE_INITIALIZED) {
                            audioRecorder.stop();
                        }
                        audioRecorder.release(); //release the recorder resources
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();  // set interrupt flag
                    notifyMessageListenersOfError("Audio error, could not start recording.");
                    if (audioRecorder.getState() == AudioRecord.STATE_INITIALIZED) {
                        audioRecorder.stop();
                    }
                    audioRecorder.release(); //release the recorder resources
                    return;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not start recording. Error: " + e.getMessage());
            notifyMessageListenersOfError("Audio error, could not start recording.");
            if (audioRecorder.getState() == AudioRecord.STATE_INITIALIZED) {
                audioRecorder.stop();
            }
            audioRecorder.release(); //release the recorder resources
            return;
        }

        setDecoderState(STATE_LISTENING);

        while (!isLoopStopped()) {
            //for(int audioIndex = 0; audioIndex < stepFactor && !loopStopped; audioIndex++) { // NOTE: This for loop was used to know when a full winLen had been read, currently not used.
            // ACTUAL AUDIO READ
            readBytes = audioRecorder.read(tempBuffer, 0, neededBytes);

            readTimestamp = System.nanoTime();
            if (readBytes != neededBytes) {
                //Log.e(TAG, "ERROR " + readBytes);
            } else {
                //Log.e(TAG, "ReadBytes " + readBytes);
                convertShortToFloat(tempBuffer, currentData, readBytes);

                synchronized (historyBuffer) {
                    historyBuffer.add(currentData);
                }
                //if (counter < (historyBuffer.size() / neededBytes)) { //Note: Differs from Octave version
                if (counter < (nBlocks*nAnalysisWindowsPerBit-nAnalysisWindowsPerPause)) { // Looks more like Octave version
                    //Log.e("HistoryBuffer", "I am not full");
                    //Log.d("HisoryBuffercounter", "Counter " + counter);
                    //Log.d("HisoryBuffersize", "Size " + historyBuffer.size());
                } else { // At this point the buffer is very close to be full
                    analyzeHistoryBuffer();
                }
                counter++; // Octave version put it at the end
            }
            //}

            //counter++;

        } // THREAD-LOOP ENDS HERE

        setDecoderState(STATE_STOPPED);

        if (audioRecorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
            audioRecorder.stop();
        }

        audioRecorder.release();
        audioRecorder = null;
        //analysisHistoryFillingBuffer = 0;
        //Log.d(TAG, "Message Decoder Thread stopped.");
    }

    /**
     * Converts an input array from short to [-1.0;1.0] float, result is put into the (pre-allocated) output array
     * @param input
     * @param output Should be allocated beforehand
     * @param arrayLength
     */
    private static void convertShortToFloat(short[] input, float[] output, int arrayLength) {
        for (int i = 0; i < arrayLength; i++) {
            // Do we actually need float anywhere ? Switch to double ?
            output[i] = ((float) input[i]) / Short.MAX_VALUE;
        }
    }

    /**
     *
     * Converts an input array from [-1.0;1.0] float to short full range and returns it
     * @param input
     * @return a short array containing short values with a distribution similar to the input one
     */
    private static short [] convertFloatToShort(float[] input) {
        short[] output = new short[input.length];
        for (int i = 0; i < input.length; i++) {
            // Do we actually need float anywhere ? Switch to double ?
            output[i] = (short) (input[i] * Short.MAX_VALUE);
        }
        return output;
    }

    private AudioRecord getInitializedAudioRecorder() {
        minBufferSize = AudioRecord.getMinBufferSize(Fs,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

        if(minBufferSize < 0) {
            Log.e(TAG, "Error getting the minimal buffer size: " + minBufferSize);
            return null;
        }

        try {

            audioRecorderBufferSize = analysisWinLen*10; // Empirically decided

            // Initialize the buffer size such that at least the minimum size is buffered
            if (audioRecorderBufferSize < minBufferSize) {
                audioRecorderBufferSize = minBufferSize;
                //Log.d(TAG, "Minimum buffersize will be used: " + audioRecorderBufferSize);
            }
            /*else {
                audioRecorderBufferSize = audioRecorderBufferSize; // * 2;
                //Log.d(TAG, "buffersize-else: " + audioRecorderBufferSize);
            }*/

            //audioRecorderBufferSize = audioRecorderBufferSize*10;
            audioRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    Fs, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, audioRecorderBufferSize);

            if (audioRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Could not open the audio recorder, initialization failed !");
                return null;
            }
        } catch(IllegalArgumentException e) {
            //e.printStackTrace();
            Log.e(TAG, "Audio Recorder was not initialized because of an IllegalArgumentException. Error message: " + e.getMessage());
            return null;
        }

        return audioRecorder;
    }

    //ublic float[] getHistoryBuffer(){ synchronized (historyBuffer) {return historyBuffer.getArray();} }


    private void analyzeHistoryBuffer(){
        /* Will try with saving the whole buffer directly
        float firstWindow[];
        float lastWindow[];
        synchronized (historyBuffer) {
            //Log.e("HistoryBuffer", "I am full");
            firstWindow = historyBuffer.getFirstWindow(analysisWinLen);
            lastWindow = historyBuffer.getLastWindow(analysisWinLen);
        }
        */

        float analysisHistoryBuffer[];
        synchronized (historyBuffer) {
            analysisHistoryBuffer = historyBuffer.getArray();
        }
        float firstWindow[] = new float[analysisWinLen];
        float lastWindow[] = new float[analysisWinLen];
        System.arraycopy(analysisHistoryBuffer, 0, firstWindow, 0, analysisWinLen);
        System.arraycopy(analysisHistoryBuffer, analysisHistoryBuffer.length - analysisWinLen, lastWindow, 0, analysisWinLen);


        float[] startResponseUpper = firstWindow.clone();
        float[] startResponseLower = firstWindow.clone();

        int nextPowerOfTwo = DecoderUtils.nextPowerOfTwo(analysisWinLen);
        ////Log.d("nextPowerOfTwo", String.valueOf(nextPowerOfTwo));

        double[] startResponseUpperDouble = new double[nextPowerOfTwo];
        double[] startResponseLowerDouble = new double[nextPowerOfTwo];

        int centerFrequencyBandPassDown = config.getFrequencyZero() + (bandpassWidth/2);
        int centerFrequencyBandPassUp = config.getFrequencyZero() + bandpassWidth + (bandpassWidth/2);

/*        // Only checking 2 frequencies for each band
        int centerFrequencyBandPassDown = f0+450;//+(bandpassWidth/2);
        int centerFrequencyBandPassUp = 18850;//f0 + bandpassWidth+(bandpassWidth/2);
        bandpassWidth = 150;
*/

        Butterworth butterworthDown = new Butterworth();
        butterworthDown.bandPass(bandPassFilterOrder,Fs,centerFrequencyBandPassDown,bandpassWidth);
        Butterworth butterworthUp = new Butterworth();
        butterworthUp.bandPass(bandPassFilterOrder,Fs,centerFrequencyBandPassUp,bandpassWidth);
        for(int i = 0; i<startResponseLower.length; i++) {
            startResponseUpperDouble[i] = butterworthUp.filter(startResponseUpper[i]);
            startResponseLowerDouble[i] = butterworthDown.filter(startResponseLower[i]);
        }

        ComplexArray complexArrayStartResponseUpper = Hilbert.transform(startResponseUpperDouble);
        ComplexArray complexArrayStartResponseLower = Hilbert.transform(startResponseLowerDouble);

        double sumAbsStartResponseUpper = 0;
        double sumAbsStartResponseLower = 0;
        for(int i = 0; i<complexArrayStartResponseUpper.real.length; i++){
            sumAbsStartResponseUpper += DecoderUtils.getComplexAbsolute(complexArrayStartResponseUpper.real[i], complexArrayStartResponseUpper.imag[i]);
            sumAbsStartResponseLower += DecoderUtils.getComplexAbsolute(complexArrayStartResponseLower.real[i], complexArrayStartResponseLower.imag[i]);
        }

/* Without Hilbert
        double sumAbsStartResponseUpper = 0;
        double sumAbsStartResponseLower = 0;
        for(int i = 0; i<startResponseUpperDouble.length; i++){
            sumAbsStartResponseUpper += Math.abs(startResponseUpperDouble[i]);
            sumAbsStartResponseLower += Math.abs(startResponseLowerDouble[i]);
        }
        */

/* With individual frequencies
        int frequencies[] = new int[nFrequencies];
        for (int f = f0, i = 0; f < f0 + nFrequencies*frequencySpace; f += frequencySpace, i++) {
            frequencies[i] = f;
        }

        double sumAbsStartResponseUpper = 0;
        double sumAbsStartResponseLower = 0;
        for(int fIndex = 0; fIndex < nFrequencies/2; fIndex++) {
            int freqBandpassWidth = 50;
            int centerFrequencyBandPassDown = frequencies[fIndex];
            int centerFrequencyBandPassUp = frequencies[nFrequencies-1-fIndex];
            Butterworth butterworthDown = new Butterworth();
            butterworthDown.bandPass(bandPassFilterOrder,Fs,centerFrequencyBandPassDown,freqBandpassWidth);

            Butterworth butterworthUp = new Butterworth();
            butterworthUp.bandPass(bandPassFilterOrder,Fs,centerFrequencyBandPassUp,freqBandpassWidth);

            for(int i = 0; i<startResponseLower.length; i++) {
                startResponseUpperDouble[i] = butterworthUp.filter(startResponseUpper[i]);
                startResponseLowerDouble[i] = butterworthDown.filter(startResponseLower[i]);
            }

            ComplexArray complexArrayStartResponseUpper = Hilbert.transform(startResponseUpperDouble);
            ComplexArray complexArrayStartResponseLower = Hilbert.transform(startResponseLowerDouble);

            for(int i = 0; i<complexArrayStartResponseUpper.real.length; i++){
                sumAbsStartResponseUpper += getComplexAbsolute(complexArrayStartResponseUpper.real[i], complexArrayStartResponseUpper.imag[i]);
                sumAbsStartResponseLower += getComplexAbsolute(complexArrayStartResponseLower.real[i], complexArrayStartResponseLower.imag[i]);
            }
        }*/

        long startMessageTimestamp = System.nanoTime();
        //Log.v("Timing", "From read to start message detection: " + String.valueOf((startMessageTimestamp-readTimestamp)/1000000) + "ms");

        //Log.e("StartResponseAvgBefore", "detection with factor: " + sumAbsStartResponseUpper/sumAbsStartResponseLower);
        if(sumAbsStartResponseUpper > startFactor * sumAbsStartResponseLower){
            // IF THIS IS TRUE, WE HAVE A START BLOCK!
            //Log.d("StartResponseAvg", "message start detected with factor: " + sumAbsStartResponseUpper/sumAbsStartResponseLower);
            float[] endResponseUpper = lastWindow.clone();
            float[] endResponseLower = lastWindow.clone();

            double[] endResponseUpperDouble = new double[nextPowerOfTwo];
            double[] endResponseLowerDouble = new double[nextPowerOfTwo];


            /* With individual frequencies
            double sumAbsEndResponseUpper = 0;
            double sumAbsEndResponseLower = 0;
            for(int fIndex = 0; fIndex < nFrequencies/2; fIndex++) {
                int freqBandpassWidth = 50;
                int centerFrequencyBandPassDown = frequencies[fIndex];
                int centerFrequencyBandPassUp = frequencies[nFrequencies-1-fIndex];
                Butterworth butterworthDownEnd = new Butterworth();
                butterworthDownEnd.bandPass(bandPassFilterOrder,Fs,centerFrequencyBandPassDown,freqBandpassWidth);

                Butterworth butterworthUpEnd = new Butterworth();
                butterworthUpEnd.bandPass(bandPassFilterOrder,Fs,centerFrequencyBandPassUp,freqBandpassWidth);

                for(int i = 0; i<endResponseUpper.length; i++) {
                    endResponseUpperDouble[i] = butterworthUpEnd.filter(endResponseUpper[i]);
                    endResponseLowerDouble[i] = butterworthDownEnd.filter(endResponseLower[i]);
                }

                ComplexArray complexArrayEndResponseUpper = Hilbert.transform(endResponseUpperDouble);
                ComplexArray complexArrayEndResponseLower = Hilbert.transform(endResponseLowerDouble);

                for(int i = 0; i<complexArrayEndResponseUpper.real.length; i++){
                    sumAbsEndResponseUpper += getComplexAbsolute(complexArrayEndResponseUpper.real[i], complexArrayEndResponseUpper.imag[i]);
                    sumAbsEndResponseLower += getComplexAbsolute(complexArrayEndResponseLower.real[i], complexArrayEndResponseLower.imag[i]);
                }
            }*/

            Butterworth butterworthDownEnd = new Butterworth();
            butterworthDownEnd.bandPass(bandPassFilterOrder,Fs,centerFrequencyBandPassDown,bandpassWidth);
            Butterworth butterworthUpEnd = new Butterworth();
            butterworthUpEnd.bandPass(bandPassFilterOrder,Fs,centerFrequencyBandPassUp,bandpassWidth);

            for(int i = 0; i<endResponseLower.length; i++) {
                endResponseUpperDouble[i] = butterworthUpEnd.filter(endResponseUpper[i]);
                endResponseLowerDouble[i] = butterworthDownEnd.filter(endResponseLower[i]);
            }

            ComplexArray complexArrayEndResponseUpper = Hilbert.transform(endResponseUpperDouble);
            ComplexArray complexArrayEndResponseLower = Hilbert.transform(endResponseLowerDouble);

            double sumAbsEndResponseUpper = 0;
            double sumAbsEndResponseLower = 0;
            for(int i = 0; i<complexArrayEndResponseUpper.real.length; i++){
                sumAbsEndResponseUpper += DecoderUtils.getComplexAbsolute(complexArrayEndResponseUpper.real[i], complexArrayEndResponseUpper.imag[i]);
                sumAbsEndResponseLower += DecoderUtils.getComplexAbsolute(complexArrayEndResponseLower.real[i], complexArrayEndResponseLower.imag[i]);
            }


/* Without Hilbert
            double sumAbsEndResponseUpper = 0;
            double sumAbsEndResponseLower = 0;
            for(int i = 0; i<endResponseUpperDouble.length; i++){
                sumAbsEndResponseUpper += Math.abs(endResponseUpperDouble[i]);
                sumAbsEndResponseLower += Math.abs(endResponseLowerDouble[i]);
            }
*/

            long endMessageTimestamp = System.nanoTime();
            ////Log.d("Timing", "From read to before end message detection: " + String.valueOf((endMessageTimestamp-readTimestamp)/1000000) + "ms");

            //Log.d("EndResponseAvgBefore", "end factor: " + sumAbsEndResponseLower/sumAbsEndResponseUpper);
            if(sumAbsEndResponseLower > endFactor * sumAbsEndResponseUpper) {
                // THIS IS TRUE IN CASE WE FOUND AN END FRAME NOW ITS TIME TO DECODE THE MESSAGE IN BETWEEN
                //Log.d("EndResponseAvg", "detection with factor: " + sumAbsEndResponseLower / sumAbsEndResponseUpper + " and " + sumAbsStartResponseUpper/sumAbsStartResponseLower);

                analyzeMessage(analysisHistoryBuffer);

            }

        }

        synchronized (historyBuffer) {
            historyBuffer.incrementAnalysisIndex(analysisWinStep);
        }
    }

    private void analyzeMessage(float[] analysisHistoryBuffer) {
        int overlapForSpectrogramInSamples = winLenForSpectrogramInSamples - analysisWinStep;
        //int overlapForSpectrogramInSamples = Math.round(winLenForSpectrogramInSamples * 0.875f);

        // High overlap (8) makes the visualization more accurate but is quite slow. Low overlap (2) is a minimum to see something
        int overlapFactor = Math.round((float) winLenForSpectrogramInSamples / (winLenForSpectrogramInSamples - overlapForSpectrogramInSamples));
        // Make overlapFactor a parameter (overriding it is nice for demo purposes)
        //overlapFactor = 8;
        int nbWinLenForSpectrogram = Math.round(overlapFactor * (float) historyBufferSize / (float) winLenForSpectrogramInSamples);

        //Log.d("nbWinLenForSpectrogram",String.valueOf(nbWinLenForSpectrogram));
        //Log.d("overlapFactor",String.valueOf(overlapFactor));



        /* Now passed as parameter to be sure we work on the right piece of data
        float analysisHistoryBuffer[];
        synchronized (historyBuffer) {
            analysisHistoryBuffer = historyBuffer.getArray();
        }
        */

        // Should we access the historyBuffer directly ?
        double[][] historyBufferDouble = new double[nbWinLenForSpectrogram][winLenForSpectrogramInSamples];
        for(int j = 0; j<historyBufferDouble.length;j++ ) {
            int helpArrayCounter = 0;
            //Log.d("ForLoopVal", String.valueOf(j*analysisWinLen));
            for (int i = (j/overlapFactor)*winLenForSpectrogramInSamples + ((j%overlapFactor) * winLenForSpectrogramInSamples/overlapFactor); i < analysisHistoryBuffer.length && i < (1 + j/overlapFactor)*winLenForSpectrogramInSamples + ((j%overlapFactor) * winLenForSpectrogramInSamples/overlapFactor); i++) {
                historyBufferDouble[j][helpArrayCounter] = (double) analysisHistoryBuffer[i];
                helpArrayCounter++;
            }
        }
        //double[][] historyBufferDoubleComplex = new double[nbWinLenForSpectrogram][winLenForSpectrogramInSamples];//[winLenForSpectrogramInSamples*2];
        HammingWindow hammWin = new HammingWindow(winLenForSpectrogramInSamples);
        DoubleFFT_1D mFFT = new DoubleFFT_1D(winLenForSpectrogramInSamples);
        double[][] historyBufferDoubleAbsolute = new double[nbWinLenForSpectrogram][winLenForSpectrogramInSamples / 2];
        float[][] historyBufferFloatNormalized = new float[nbWinLenForSpectrogram][historyBufferDoubleAbsolute[0].length];
        double fftSum = 0;
        int helpCounter;
        for(int j = 0; j<historyBufferDoubleAbsolute.length;j++ ) {
            // n is even [DONE on winLenForSpectrogramInSamples]
            //double[] complexSignal = new double[winLenForSpectrogramInSamples];//[winLenForSpectrogramInSamples * 2];
            hammWin.applyWindow(historyBufferDouble[j]);
            //System.arraycopy(historyBufferDouble[j], 0, complexSignal, 0, winLenForSpectrogramInSamples);

            mFFT.realForward(historyBufferDouble[j]);

            // Get absolute value of the complex FFT result
            helpCounter = 0;
            //fftSum = 0;
            for (int l = 0; l < historyBufferDouble[j].length ; l++) {
                if (l % 2 == 0) { //Modulo 2 is used to get only the real (every second) value
                    double absolute = DecoderUtils.getComplexAbsolute(historyBufferDouble[j][l],historyBufferDouble[j][l+1]);
                    historyBufferDoubleAbsolute[j][helpCounter] = absolute;
                    fftSum += absolute;
                    helpCounter++;
                }
            }
/*
            // Normalize over one column [NOTE: It looks like results are better with fftSum over the whole spectrum, maybe because of the overlap]
            for (int l = 0; l < historyBufferDoubleAbsolute[j].length ; l++) {
                float normalized = 0.0001F;
                if(fftSum != 0 && historyBufferDoubleAbsolute[j][l] != 0) {
                    normalized = (float) (historyBufferDoubleAbsolute[j][l]/fftSum);
                } // Else all the values are 0 so we do not really care ?
                historyBufferFloatNormalized[j][l] = normalized;
            }
*/
        }

        //Log.d("NbValuesAbsFFT",String.valueOf(historyBufferDoubleAbsolute[0].length));

        for(int j = 0; j<historyBufferFloatNormalized.length;j++ ) {
            for (int i = 0; i < historyBufferDoubleAbsolute[0].length; i++) {
                //historyBufferFloatNormalized[j][i] = (float) historyBufferDouble[j][i];
                //if(historyBufferDoubleAbs[j][i] == 0) {
                //    historyBufferFloatNormalized[j][i] = (float) Math.log(0.0000001);
                //}else{
                float normalized = 0.0001F;
                if(fftSum != 0) {
                    //  Normalize over one block at a time and check if it improves the visualization [NOTE: It looks like results are better with fftSum over the whole spectrum, maybe because of the overlap]
                    normalized = (float) (historyBufferDoubleAbsolute[j][i]/fftSum);
                } // Else all the values are 0 so we do not really care
                //if (historyBufferDoubleAbsolute[j][i] != 0) {
                //    normalized = (float) historyBufferDoubleAbsolute[j][i];
                //}
                historyBufferFloatNormalized[j][i] = normalized;
                //}
            }
        }


        int lowerCutoffFrequency = frequencies[0]-frequencyOffsetForSpectrogram;
        int upperCutoffFrequency = frequencies[frequencies.length-1]+frequencyOffsetForSpectrogram;
        int lowerCutoffFrequencyIdx = (int)((float)lowerCutoffFrequency/(float)Fs*(float)winLenForSpectrogramInSamples) + 1;
        int upperCutoffFrequencyIdx = (int)((float)upperCutoffFrequency/(float)Fs*(float)winLenForSpectrogramInSamples) + 1;


        // Check if the normalization on a column instead on all the whole message really improved the detection.
        // Cut away unimportant frequencies, logarithmize and then normalize
        double[][] P = new double[nbWinLenForSpectrogram][upperCutoffFrequencyIdx-lowerCutoffFrequencyIdx + 1];
        double[][] input = new double[nbWinLenForSpectrogram][upperCutoffFrequencyIdx-lowerCutoffFrequencyIdx + 1];
        int arrayCounter;
        double logSum;
        for(int j = 0; j<historyBufferDoubleAbsolute.length; j++) {
            arrayCounter = 0;
            logSum = 0;
            for(int i = lowerCutoffFrequencyIdx; i <= upperCutoffFrequencyIdx;i++) {
                if(historyBufferDoubleAbsolute[j][i]==0){
                    P[j][arrayCounter] = 0.0000001;
                }else {
                    P[j][arrayCounter] = historyBufferDoubleAbsolute[j][i];
                }
                P[j][arrayCounter] = Math.log(P[j][arrayCounter]);
                logSum += P[j][arrayCounter];
                arrayCounter++;
            }

            // Normalization
            for(int i = 0; i <= upperCutoffFrequencyIdx-lowerCutoffFrequencyIdx; i++) {
                input[j][i] = (float) (P[j][i] / logSum);
            }
        }


        int step = winLenForSpectrogramInSamples-overlapForSpectrogramInSamples;
        int nVectorsPerBlock =  overlapFactor;//Math.round(bitperiodInSamples/step); // Isn't nVectorsPerBlock equal to overlapFactor ?! Not in matlab.
        int nVectorsPerPause =   Math.round((float) pauseperiodInSamples/step);
        int[] blockCenters = new int[nBlocks]; //Math.round(nVectorsPerBlock/2);
        int[] pauseCenters  = new int[nBlocks - 1]; //Math.round(nVectorsPerBlock+(nVectorsPerPause)/2);

        // Compute block centers
        // [CHECKED] blockCenters are equivalent to matlab indices
        blockCenters[0] = Math.round((float) nVectorsPerBlock/2) - 1; //We substract one compared to Octave because indexes start at 0 in Java
        pauseCenters[0] =  Math.round(nVectorsPerBlock+((float) nVectorsPerPause)/2); // No need to substract one again (it is based on blockCenters).
        for(int i=1; i < nBlocks; i++) {
            blockCenters[i] = blockCenters[i - 1] + nVectorsPerBlock + nVectorsPerPause;
            if(i<nBlocks-1) pauseCenters[i] = pauseCenters[i - 1] + nVectorsPerBlock + nVectorsPerPause;
            //Log.d("Block center ", i + " --> " + blockCenters[i]);
        }

        // [CHECKED] frequencyCenterIndices are equivalent to matlab indices (one below, but it starts at 0 in Java, at 1 in matlab)
        float[][] frequencyCenterIndices = new float[nBlocks][config.getnFrequencies()];
        // TODO: First loop not really needed
        for(int k = 0; k<nBlocks; k++) {
            for (int idxFrequencies = 0; idxFrequencies < config.getnFrequencies(); idxFrequencies++) {
                // TODO: Upper and Lower inverted (hence the need for the loop going reverse a dozen lines below)
                frequencyCenterIndices[k][idxFrequencies] = findClosestValueIn1DArray(frequencies[idxFrequencies], winLenForSpectrogramInSamples, input[k].length, upperCutoffFrequencyIdx, lowerCutoffFrequencyIdx); //computation is different than in Matlab
            }
        }

        //decode using spectrogram
        int[] messageDecodedBySpec = new int[(nBlocks-2)/2 * config.getnFrequencies()];
        arrayCounter = 0;
        // Go through all message blocks, skipping start and end block with a stepsize of 2 (because we always have a normal block and an inverted block)
        for(int j = 1; j<nBlocks-1; j=j+2){
            for(int m = frequencyCenterIndices[j].length-1; m>=0; m--){
                int currentCenterFreqIdx = (int)frequencyCenterIndices[j][m];

                // Matlab values range between 0 and -20 or so, always negative and not so small
                // Android values do not seem to have a clear range, sometimes positive sometimes negative, often close to 0
                double currentBit = getPointAndNeighborsAggreagate(input, currentCenterFreqIdx, blockCenters[j], nNeighborsFreqUpDown, nNeighborsTimeLeftRight, aggFcn);
                double currentBitInv = getPointAndNeighborsAggreagate(input, currentCenterFreqIdx, blockCenters[j + 1], nNeighborsFreqUpDown, nNeighborsTimeLeftRight, aggFcn);

                // Check why we had to change > to <
                if (currentBit < currentBitInv) {
                    messageDecodedBySpec[arrayCounter] = 1;
                }
                else{
                    messageDecodedBySpec[arrayCounter] = 0;
                }
                arrayCounter++;
            }
            //Log.d("arraycounter", String.valueOf(arrayCounter));
        }
        //Log.d("Decoded bit sequence", Arrays.toString(messageDecodedBySpec));

        int parityCheckResult = crc.checkMessageCRC(messageDecodedBySpec/*, ConfigConstants.GENERATOR_POLYNOM*/);

        if (!silentMode && parityCheckResult == 0) {
            setLoopStopped(true);
        }
        notifySpectrumListeners(historyBufferFloatNormalized, parityCheckResult == 0);

        // Decode message to UTF8
        String decodedBitSequence = Arrays.toString(messageDecodedBySpec).replace(", ", "").replace("[","").replace("]","");
        String bitSequenceWithoutFillingAndCRC = DecoderUtils.removeFillingCharsAndCRCChars(decodedBitSequence, ConfigConstants.GENERATOR_POLYNOM.length);
        final byte[] receivedMessage = DecoderUtils.binaryToBytes(bitSequenceWithoutFillingAndCRC);

        final long decodingTimeNanosecond = System.nanoTime()-readTimestamp;
        //Log.d("Timing", "From read to received message: " + String.valueOf((decodingTimeNanosecond)/1000000) + "ms. CRC: " + String.valueOf(parityCheckResult));

        SoniTalkMessage message = new SoniTalkMessage(receivedMessage, parityCheckResult == 0, decodingTimeNanosecond);
        if (returnsRawAudio()) {
            message.setRawAudio(convertFloatToShort(analysisHistoryBuffer));
        }

        notifyMessageListeners(message);

        //Original Bitsequence for the text "Hello Sonitalk" from SoniTalk Encoder 0100100001100001011011000110110001101111001000000101001101101111011011100110100101110100011000010110110001101011000110010001100100011001000110010001110010010100
    }


    private int getMinWinLenDividableByStepFactor(int requestedSize, int stepFactor) {
        //Log.i(TAG, "Check dividability by stepFactor = " + stepFactor + " for requestSize = " + requestedSize);
        minBufferSize = AudioRecord.getMinBufferSize(Fs,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if(minBufferSize < 0) {
            Log.e(TAG, "Error getting the minimal buffer size: " + minBufferSize);
            return -1;
        }
        while (requestedSize < minBufferSize || requestedSize % stepFactor != 0) {
            requestedSize++;
        }
        Log.i(TAG, "requestedSize returned: " + requestedSize);
        return requestedSize;
    }


    private float findClosestValueIn1DArray(int value, int winlen, int arraylength, int upperIdx, int lowerIdx){
        float arrayIndexRelative;
        float arrayIndex;

        float frequencyIndex = DecoderUtils.freq2idx(value, Fs, winlen);
        arrayIndexRelative = DecoderUtils.getRelativeIndexPosition(frequencyIndex, upperIdx, lowerIdx);
        arrayIndex = ((float)arraylength*arrayIndexRelative);
        //Log.d("findClosest", "arrayIndex: " + arrayIndex);
        //arrayIndex = frequencyIndex;
        return arrayIndex;
    }

    /**
     * Returns an aggregation (e.g. mean) of the values contained in the cell(s) around the one at [row][col] position
     * Row and column are "reversed" compared to the matlab prototype
     * @param data
     * @param row Frequency center index
     * @param col Block center index
     * @param nRowsNeighborsLeftRight How many frequency-index rows to include (on the left AND right side)
     * @param nColsNeighborsLeftRight How many block-index columns to include (on the left AND right side)
     * @param aggFunction
     * @return
     */
    private double getPointAndNeighborsAggreagate(double[][] data,int row,int col,int nRowsNeighborsLeftRight, int nColsNeighborsLeftRight, String aggFunction){
        double val = -1;
        int valuesRange = (nRowsNeighborsLeftRight+nColsNeighborsLeftRight+1)*(nRowsNeighborsLeftRight+nColsNeighborsLeftRight+1);//1+nRowsNeighborsLeftRight*2+nColsNeighborsLeftRight*2;
        double[] values = new double[valuesRange];
        int valuecounter = 0;

        for(int i = nRowsNeighborsLeftRight*(-1); i <= nRowsNeighborsLeftRight; i++){
            for(int j = nColsNeighborsLeftRight*(-1); j <= nColsNeighborsLeftRight; j++){
                if(i!=0 || j!=0){ //[0,0] is done lower
                    values[valuecounter] = data[col+i][row+i];
                    valuecounter++;

                }
            }
        }
        // Note: Values are extremely similar on the same row (same frequency), but different for the frequencies above and under.
        // Handling the [0,0] case
        values[valuecounter] = data[col][row];

        switch(aggFunction){
            case "mean":
                val = DecoderUtils.mean(values);
                break;
            case "max":
                val = DecoderUtils.max(values);
                break;
            case "median":
                Arrays.sort(values);
                val = DecoderUtils.median(values);
                break;
        }
        //Log.d("ValuesAgg", Arrays.toString(values));
        //Log.d("ValAgg", val + "");

        return val;
    }

    /**
     * Called from receiveBackground(long delayMilliseconds) to cancel the job after delayMilliseconds
     *
     */
    private void cancelBackgroundReceiving() {
        //if (!isLoopStopped()) //Should stop anyways right ?
        setLoopStopped(true);
        setDecoderState(STATE_CANCELLED);
        soniTalkContext.cancelNotificationReceiving();
    }

    /**
     * Captures audio and tries decoding for delayMilliseconds ms. Audio processing occurs in a separate Thread.
     * Detected messages will be notified to listeners via the onMessageReceived callback.
     * Cancellation will be called on the Thread currently calling this method.
     * @param delayMilliseconds Duration you want to try and receive a message before cancelling.
     * @throws DecoderStateException
     */
    public void receiveBackground(long delayMilliseconds, int requestCode) throws DecoderStateException {
        /*
         * Inspired from https://stackoverflow.com/q/7882739/5232306.
         */
        receiveBackground(requestCode);
        delayhandler.postDelayed(new Runnable()
        {
            @Override
            public void run() {
                cancelBackgroundReceiving();
            }
        }, delayMilliseconds );
    }

    /**
     * Captures audio and tries detecting/decoding messages. Audio processing occurs in a separate Thread.
     * Detected messages will be notified to listeners via the onMessageReceived callback.
     * It is possible to cancel using stopReceiving().
     * @throws DecoderStateException
     */
    public void receiveBackground(int requestCode) throws DecoderStateException {
        this.requestCode = requestCode;
        // Do we really want to be able to restart a stopped Decoder ?
        if (getDecoderState() == STATE_INITIALIZED || getDecoderState() == STATE_STOPPED) {
            threadExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    startDecoding();
                }
            });
        }
        else if (getDecoderState() == STATE_CANCELLED) {
            throw new DecoderStateException("Cannot start a Decoder after it was cancelled.");
        }
        else if (getDecoderState() == STATE_LISTENING) {
            throw new DecoderStateException("Cannot start a Decoder already listening.");
        }
        else {
            throw new DecoderStateException("Cannot start the Decoder, unexpected state.");
        }
    }

    /**
     * Stops the capturing and decoding process. This must be called at the latest in your app
     * onStop() or in your Service onDestroy() to release resources. Please do not keep the microphone
     * access when you do not need it anymore.
     */
    public void stopReceiving() {
        //Log.d(TAG, "Stop receiving.");
        setLoopStopped(true);

        soniTalkContext.cancelNotificationReceiving();

        delayhandler.removeCallbacksAndMessages(null); // Consider doing it more fine grained
        List<Runnable> cancelledRunnables = threadExecutor.shutdownNow();
        if (!cancelledRunnables.isEmpty())
            Log.d(TAG, "Cancelled " + cancelledRunnables.size() + " tasks.");
    }

    /**
     * Pauses the current capturing/decoding process without cancelling the potential timers.
     */
    public void pause() {
        //Log.d(TAG, "Pause receiving.");
        setLoopStopped(true);
        soniTalkContext.cancelNotificationReceiving();
    }

    /**
     * Resumes audio capturing and detecting/decoding messages. Audio processing occurs in a separate Thread.
     * Detected messages will be notified to listeners via the onMessageReceived callback.
     * It is possible to cancel using stopReceiving().
     * @throws DecoderStateException
     */
    public void resume() throws DecoderStateException {
        //Log.d(TAG, "Resume receiving.");
        receiveBackground(this.requestCode);
    }


    private synchronized boolean isLoopStopped() {
        return loopStopped;
    }

    private synchronized void setLoopStopped(boolean loopStopped) {// Consider using an internal object for the synchronization
        this.loopStopped = loopStopped;
    }

    public void addMessageListener(MessageListener listener) {
        this.messageListeners.add(listener);
    }

    public boolean removeMessageListener(MessageListener listener) {
        return this.messageListeners.remove(listener);
    }

    private void notifyMessageListeners(SoniTalkMessage decodedMessage) {
        for(MessageListener listener: messageListeners) {
            listener.onMessageReceived(decodedMessage);
        }
    }

    private void notifyMessageListenersOfError(String errorMessage) {
        for(MessageListener listener: messageListeners) {
            listener.onDecoderError(errorMessage);
        }
    }

    public void addSpectrumListener(SpectrumListener listener) {
        this.spectrumListeners.add(listener);
    }

    public boolean removeSpectrumListener(SpectrumListener listener) {
        return this.spectrumListeners.remove(listener);
    }

    private void notifySpectrumListeners(float[][] spectrum, boolean crcIsCorrect) {
        for(SpectrumListener listener: spectrumListeners) {
            listener.onSpectrum(spectrum, crcIsCorrect);
        }
    }

    @DecoderState
    private synchronized int getDecoderState() {
        return decoderState;
    }

    private synchronized void setDecoderState(@DecoderState int state) {
        this.decoderState = state;
    }

    /**
     * Returns true if detected messages will be returned with the original audio.
     * @return true if detected messages will be returned with the original audio
     */
    public synchronized boolean returnsRawAudio() {
        return returnRawAudio;
    }

    /**
     * Decides if detected messages will be returned with the original audio or not. Useful for
     * debugging or replaying a message.
     * @param returnRawAudio
     */
    public synchronized void setReturnRawAudio(boolean returnRawAudio) {
        this.returnRawAudio = returnRawAudio;
    }
}

