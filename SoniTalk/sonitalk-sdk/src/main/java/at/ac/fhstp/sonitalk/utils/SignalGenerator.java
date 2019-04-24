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

package at.ac.fhstp.sonitalk.utils;

import java.util.Random;

import at.ac.fhstp.sonitalk.SoniTalkConfig;
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;

/**
 * The SignalGenerator handles the creation of the audio message.
 * It creates the blocks depending on the message forwarded. The generator
 * includes indexing the frequencies, fft, normalization, fade-in/fade-out
 * and casting to the right format.
 */
public class SignalGenerator {
    private SoniTalkConfig config;

    private int fs;
    private int winLen = 500;
    private int winLenSamples;
    private double max = 0;

    private double[][] whiteNoiseBands;
    private double[] cutoffFreqDownIdx;
    private double[] cutoffFreqUpIdx;

    private short[] whiteNoise;

    private Random randomGen = new Random(42);

    private double bandWidth; //the bandwith for every specified frequencyband


    /**
     * Default constructor using a 44100Hz sample rate (works on all devices)
     * @param config
     */
    public SignalGenerator(SoniTalkConfig config) {
        this(44100, config);
    }

    public SignalGenerator(int sampleRate, SoniTalkConfig config){
        this.config = config;
        this.fs = sampleRate;

        int f0 = config.getFrequencyZero();
        if ((f0*2) > fs) {
            throw new IllegalArgumentException("Sample rate cannot be lower than two times the frequency zero. Please try a sample rate of 44100Hz and f0 under 22050Hz");
        }
    }

    /**
     * Starts generating an audio message block.
     * @param signalType type of specification for getting frequency bands
     * @param bitStringArray message to transform into frequency bands
     * @return one short array audio message block
     */
    public short[] getSignalBlock(final SignalType signalType, String[] bitStringArray){
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); //set the handler thread to background

        return transformDoubleArrayIntoShortArray(signalType, bitStringArray);
    }

    /**
     * Calculates the samples depending on the window length. Takes the frequency bands and
     * check their order with a bubble sort. Calculates the indices in the samples depending
     * on the frequency bands. Fills an array, with the size of the samples, with random values.
     * Calls the fft with those array.
     * @param signalType type of specification for getting frequency bands
     * @param bitStringArray message to transform into frequency bands
     * @return a complex array of audio data
     */
    private double[] generateSignalBlock(SignalType signalType, String[] bitStringArray){
        if(signalType.equals(SignalType.PLAYCONFIG)) {
            winLen = config.getBitperiod();
        }else if(signalType.equals(SignalType.PAUSECONFIG)) {
            winLen = config.getPauseperiod();
        }
        whiteNoiseBands = useSignalConfig(signalType, bitStringArray); //import the frequencies
        if (winLen==0){winLen= 30;}
        winLenSamples = winLen*fs/1000;

        if(winLenSamples%2 == 1){
            winLenSamples+=1; //if the windowSamples are odd, we have to add 1 sample because audiotrack later needs an even buffersize
        }

        //sort the bands with a bubblesort
        if(whiteNoiseBands.length>1) {
            double[][] temp = new double[1][2];
            for (int i = 1; i < whiteNoiseBands.length; i++) {
                for (int j = 0; j < whiteNoiseBands.length - i; j++) {
                    if (whiteNoiseBands[j][0] > whiteNoiseBands[j + 1][0]) {
                        temp[0][0] = whiteNoiseBands[j][0];
                        temp[0][1] = whiteNoiseBands[j][1];
                        whiteNoiseBands[j][0] = whiteNoiseBands[j + 1][0];
                        whiteNoiseBands[j][1] = whiteNoiseBands[j + 1][1];
                        whiteNoiseBands[j + 1][0] = temp[0][0];
                        whiteNoiseBands[j + 1][1] = temp[0][1];
                    }
                }
            }
        }

        cutoffFreqUpIdx = new double[whiteNoiseBands.length]; //initializing the array for all upper bandfrequencies
        cutoffFreqDownIdx = new double[whiteNoiseBands.length]; //initializing the array for all lower bandfrequencies

        for(int i = 0; i<whiteNoiseBands.length; i++) { //filling the arrays with the bandfrequencyindexes
            double freqDown = whiteNoiseBands[i][0]; //lower frequency
            double freqUp = whiteNoiseBands[i][1]; //higher frequency
            cutoffFreqDownIdx[i] = Math.round(freqDown / (fs / 2) * (winLen * fs / 1000) + 1); //calculate the index of the lower frequency
            cutoffFreqUpIdx[i] = Math.round(freqUp / (fs / 2) * (winLen * fs / 1000) + 1); //calculate the index of the higher frequency
        }

        double[] signal = new double[winLenSamples]; //initializing the double array for the signal

        randomGen = new Random(42);
        for (int j = 0; j < winLenSamples; j++) {
            signal[j] = randomGen.nextDouble(); //generate random double values and store it in the signal array
        }

        double[] complexWhiteNoise = doFFT(winLenSamples, signal); //execute the fft method for creating whitenoisebands

        return complexWhiteNoise;
    }

    /**
     * Normalizes the audio signal by dividing with the max value of the signal and only saves the real values.
     * @param signalType type of specification for getting frequency bands
     * @param bitStringArray message to transform into frequency bands
     * @return a normalized audio array
     */
    private double[] normalizeWhitenoiseSignal(SignalType signalType, String[] bitStringArray){
        double[] complexWhiteNoise = generateSignalBlock(signalType, bitStringArray);
        for (int i = 0; i < (winLenSamples * 2); i++) {
            if (Math.abs(complexWhiteNoise[i]) > max) {
                max = Math.abs(complexWhiteNoise[i]); //searching for the maximum value of the whitenoisesignal
            }
        }

        double[] helpNoise = new double[winLenSamples]; //creating a help array for the real values of the generated noise
        int noiseCounter = 0; //helpvariable for counting the real values
        for (int l = 0; l < (winLenSamples * 2); l++) {
            if (l % 2 == 0) { //every array row with no remain has real values
                helpNoise[noiseCounter] = (complexWhiteNoise[l] / max); //divide the complexNoise by the maximum value and then store it in the realNoise array
                noiseCounter++; //counter plus one for every real value
            }
        }

        return helpNoise;
    }

    /**
     * Performs a fade-in and fade-out on the signal to avoid cracking noises.
     * @param signalType type of specification for getting frequency bands
     * @param bitStringArray message to transform into frequency bands
     * @return a loudness adjusted audio signal array
     */
    private double[] makeFadeInAndFadeOut(SignalType signalType, String[] bitStringArray){
        double[] helpNoiseFull = normalizeWhitenoiseSignal(signalType, bitStringArray);
        if(signalType.equals(SignalType.PLAYCONFIG)) {
            winLen = config.getBitperiod();
        }else if(signalType.equals(SignalType.PAUSECONFIG)) {
            winLen= config.getPauseperiod();
        }
        winLenSamples = winLen*fs/1000;
        if(winLenSamples%2 == 1){
            winLenSamples+=1; //if the windowSamples are odd, we have to add 1 sample because audiotrack later needs an even buffersize
        }
        double[] helpNoise = new double[winLenSamples];
        for(int m = 0; m < winLenSamples; m++) {
            helpNoise[m] = helpNoiseFull[m];
        }

        int fadeAmount = 3;//Integer.valueOf(sp.getString(ConfigConstants.FADE,"2"));
        int fadeSamples = Math.round(helpNoise.length/fadeAmount); //value for the length of the fade in/fade out
        for (int i = 0; i < fadeSamples; i++) { //fade in
            helpNoise[i] = (helpNoise[i] * ((double) i / (double) fadeSamples));
        }

        for (int i = 0; i < fadeSamples; i++) { //fade out
            helpNoise[helpNoise.length - (fadeSamples - (fadeSamples - i)) - 1] = (helpNoise[helpNoise.length - (fadeSamples - (fadeSamples - i)) - 1] * ((double) i / (double) fadeSamples));
        }

        return helpNoise;
    }

    /**
     * Checks if all values are in the range of -1 to 1.
     * Casts the message to the range of short (-32760 to 32760)
     * @param signalType type of specification for getting frequency bands
     * @param bitStringArray message to transform into frequency bands
     * @return casted short array
     */
    private short[] transformDoubleArrayIntoShortArray(SignalType signalType, String[] bitStringArray){
        double[] helpNoise = makeFadeInAndFadeOut(signalType, bitStringArray);

        for (int i = 0; i < winLenSamples; i++) {
            if(helpNoise[i] > 1){ //if new value higher than 1
                helpNoise[i] = 1; //change it to 1
            }
            if(helpNoise[i] < -1){ //if new value lower than -1
                helpNoise[i] = -1; //chang it to -1
            }
        }

        whiteNoise = new short[winLenSamples]; //short array for the whitenoise

        for (int i = 0; i < winLenSamples; i++) {
            whiteNoise[i] = (short) (helpNoise[i] * 32760); //scale the double values up to short by multiplying with 32760
            if(whiteNoise[i] > 32760){ //if new value higher than 1
                whiteNoise[i] = 32760; //change it to 1
            }
            if(whiteNoise[i] < -32760){ //if new value lower than -1
                whiteNoise[i] = -32760; //chang it to -1
            }
        }

        return whiteNoise;
    }

    /**
     * Performs the fft to get a frequency-referenced signal and sets everything to zero where no audio signal should be in the message.
     * Executes the complex inverse to get a time-referenced signal again.
     * @param fftSize size for fft
     * @param inputSignal the signal to be transformed
     * @return
     */
    private double[] doFFT(int fftSize, double[] inputSignal) {

        DoubleFFT_1D mFFT = new DoubleFFT_1D(fftSize); //creating a new fft object

        double[] complexSignal = new double[winLenSamples * 2]; //creating and initializing a new double array for the complex numbers

        System.arraycopy(inputSignal, 0, complexSignal, 0, winLenSamples); //copy the array with the random numbers into the new one

        mFFT.realForwardFull(complexSignal); //make the fft on the complex signal

        double minFreq = cutoffFreqDownIdx[0]; //get the lowest frequency after the sort
        double maxFreq = cutoffFreqUpIdx[whiteNoiseBands.length-1]; //get the highest frequency after the sort

        for (double j = 0; j < minFreq; j++) {
            complexSignal[(int)j] = 0.0f; //set all values up to the lowest frequency to 0
        }

        double helpWinLenSamples = winLenSamples * 2;
        for (double j = (helpWinLenSamples - (minFreq-1)); j < helpWinLenSamples; j++) {
            complexSignal[(int)j] = 0.0f; //set all values up to the lowest frequency to 0 mirrored to the doubled winLenSamples size
        }

        double helpUpSamples = winLenSamples - (maxFreq+1);
        for (double j = winLenSamples - helpUpSamples; j < winLenSamples + helpUpSamples; j++) {
            complexSignal[(int)j] = 0.0f; //set all frequencies from the highest frequency up to the mirrored frequency of the doubled winLenSamples size
        }

        if(whiteNoiseBands.length>1) {
            for (int k = 0; k < whiteNoiseBands.length-1; k++) {
                for (double l = cutoffFreqUpIdx[k]+1; l < cutoffFreqDownIdx[k+1]; l++) {
                    complexSignal[(int)l] = 0.0f; //set all frequencies between the higher frequency of one band to the lower frequency of the next band to 0
                }
                int helpSamples = winLenSamples * 2;
                for (double l = helpSamples-cutoffFreqDownIdx[k+1]+1; l < helpSamples-cutoffFreqUpIdx[k]; l++) {
                    complexSignal[(int)l] = 0.0f; //set all frequencies between the higher frequency of one band to the lower frequency of the next band to 0 mirrored to the doubled winLenSamples size
                }
            }

            for (int k = 0; k < whiteNoiseBands.length; k++) {
                for (double l = cutoffFreqDownIdx[k]; l <= cutoffFreqUpIdx[k]; l++) {
                    complexSignal[(int)l] = 1000;
                }
                int helpSamples = winLenSamples * 2;

                for (double l = helpSamples-cutoffFreqUpIdx[k]; l <= helpSamples-cutoffFreqDownIdx[k]; l++) {
                    complexSignal[(int)l] = 1000;
                }
            }
        }

        mFFT.complexInverse(complexSignal,false);

        return complexSignal; //return the signal with the complex values

    }


    /**
     * Takes the message array with zeros and ones to calculate frequency bands.
     * @param signalTypeConfig type of specification for getting frequency bands
     * @param bitStringArray message to transform into frequency bands
     * @returna frequency bands as two-dimensional array depending on the signal type and the message array.
     */
    private double[][] useSignalConfig(SignalType signalTypeConfig, String[] bitStringArray) {
        double[][] frequencyBands; //helparray for storing the frequencybands of the technologies
        bandWidth = 1;

        int numberOfFrequencies = config.getnFrequencies();

        String[] mLine = new String[numberOfFrequencies];
        int freqCounter = 0;

        switch (signalTypeConfig) {
            case PLAYCONFIG:
                int frequencyZero = config.getFrequencyZero();
                int spaceBetweenFrequencies = config.getFrequencySpace();

                for(int i = 0; i<numberOfFrequencies; i++) {
                    if(bitStringArray[i]!=null) {
                        if (bitStringArray[i].equals("0")) {
                            //mLine[i] = "0";
                        } else if (bitStringArray[i].equals("1")) {
                            mLine[freqCounter] = String.valueOf((frequencyZero + (i * spaceBetweenFrequencies)));
                            freqCounter++;
                        }
                    }
                }


                break;
            case PAUSECONFIG:
                int frequencyPause = config.getFrequencyZero();
                int spaceBetweenFrequencies_ = config.getFrequencySpace();

                for(int i = 0; i<numberOfFrequencies; i++) {
                    if(bitStringArray[i]!=null) {
                        if (bitStringArray[i].equals("0")) {
                            mLine[i] = "0";
                            freqCounter++;
                        } else if (bitStringArray[i].equals("1")) {
                            mLine[freqCounter] = String.valueOf((frequencyPause + (i * spaceBetweenFrequencies_)));
                            freqCounter++;
                        }
                    }
                }
                break;
        }

         frequencyBands = new double[freqCounter][2];
         for(int j = 0; j<freqCounter; j++) {
            if(mLine[j].equals("0")){
                frequencyBands[j][0] = 0;
                frequencyBands[j][1] = 0;
            }else {
                frequencyBands[j][0] = (Integer.parseInt(mLine[j]) - (bandWidth / 2)); //lower frequencyparts of one band are stored in the first place of each arrayrow
                frequencyBands[j][1] = (Integer.parseInt(mLine[j]) + (bandWidth / 2)); //higher frequencyparts of one band are stored in the second place of each arrayrow
            }
        }

        return frequencyBands; //the array with the frequencybands will be returned

    }

}
