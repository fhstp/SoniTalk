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

import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;

import at.ac.fhstp.sonitalk.utils.CRC;
import at.ac.fhstp.sonitalk.utils.ConfigConstants;
import at.ac.fhstp.sonitalk.utils.EncoderUtils;
import at.ac.fhstp.sonitalk.utils.SignalGenerator;
import at.ac.fhstp.sonitalk.utils.SignalType;


/**
 * Encodes the forwarded byte array and uses a SignalGenerator to get the raw
 * audio data. This audio data is then concatenated to have the right
 * shape for creating an audio track and sending it.
 */
public class SoniTalkEncoder {
    private final SoniTalkContext soniTalkContext;

    private int Fs;
    private SoniTalkConfig config;
    private SignalGenerator signalGen;

    private EncoderUtils encoderUtils;
    private CRC crc;

    /**
     * Default constructor using a 44100Hz sample rate (works on all devices)
     * @param config
     */
    /*package private*/SoniTalkEncoder(SoniTalkContext soniTalkContext, SoniTalkConfig config){
        this(soniTalkContext, 44100, config);
    }

    /*package private*/SoniTalkEncoder(SoniTalkContext soniTalkContext, int sampleRate, SoniTalkConfig config){
        this.soniTalkContext = soniTalkContext;
        this.Fs = sampleRate;
        this.config = config;

        int f0 = config.getFrequencyZero();
        if ((f0*2) > Fs) {
            throw new IllegalArgumentException("Sample rate cannot be lower than two times the frequency zero. Please try a sample rate of 44100Hz and f0 under 22050Hz");
        }

        signalGen = new SignalGenerator(Fs, config);
        encoderUtils = new EncoderUtils();
        //storageUtils = new StorageUtils();

        crc = new CRC();
    }

    /**
     * Encodes a byte array of data using the configuration specified in the constructor.
     * The SoniTalkMessage returned can then be send via a SoniTalkSender object.
     * @param data to be encoded
     * @return a SoniTalkMessage containing the encoded data to be sent via a SoniTalkSender
     */
    public SoniTalkMessage generateMessage(byte[] data) {
        SoniTalkMessage message = new SoniTalkMessage(data);
        short[] generatedSignal = encode(data);
        message.setRawAudio(generatedSignal);

        //setDecoderState(STATE_GENERATED);

        return message;
    }

    /**
     * Takes a byte array and encodes it to a bit sequence. Adds CRC bit sequence for error checking.
     * Creates an inversed version of that bit sequence. Creates a short array with signal data depending
     * on the bit sequence and number of frequencies.
     * @param data to be encoded
     * @return a short array with signal data
     */
    private short[] encode(byte[] data){
        short[] encodedMessage = null;
        String bitOfText = encoderUtils.getStringOfEncodedBits(data, config);
        boolean doubleInverted = true;

        int nMessageBlocks = config.getnMessageBlocks();
        int numberOfFrequencies = config.getnFrequencies();
        int maxBytes = nMessageBlocks*(numberOfFrequencies/8) - (ConfigConstants.GENERATOR_POLYNOM.length-1) / 8;

        String[] bitStringArray = createStringArrayWithParityOfBitText(bitOfText, numberOfFrequencies, maxBytes, ConfigConstants.GENERATOR_POLYNOM);

        int messageLength = bitStringArray.length;
        double mesLengthDividedNumFreq = Math.round(messageLength/numberOfFrequencies);
        if(mesLengthDividedNumFreq<((float)messageLength/numberOfFrequencies)){
            mesLengthDividedNumFreq++;
        }else{
            //Log.d("MessageLength","Fits");
        }
        String[] bitStringArrayInverted = createInvertedStringArray(bitStringArray, messageLength/*, numberOfFrequencies, mesLengthDividedNumFreq*/);

        encodedMessage = generateContainerArraysAndFillWithSignalData(bitStringArray, bitStringArrayInverted, mesLengthDividedNumFreq, numberOfFrequencies, doubleInverted, messageLength);

        return encodedMessage;
    }

    /**
     * Controls the number of message blocks depending on the message length and the number of frequencies used.
     * If there are gaps at the end, filling characters are added. Adds error detection bit sequences.
     * Splits the message string into a string array.
     * @param bitOfText The message after which the error detection code is generated.
     * @param numberOfFrequencies How many frequencies the message should use
     * @param maxBytes the maximum of bytes the message should consist of
     * @param generatorPolynom the bit sequence for generating error detection code
     * @return the message with error detection bit sequence as string array
     */
    private String[] createStringArrayWithParityOfBitText(String bitOfText, int numberOfFrequencies, int maxBytes, byte[] generatorPolynom){
        int parityLength = generatorPolynom.length - 1;
        int restModulo = (bitOfText.length() + parityLength)%numberOfFrequencies;

        while(restModulo!=0 || (bitOfText.length()/8) < maxBytes){
            bitOfText = bitOfText + ConfigConstants.CONTROL_FILLING_CHARACTER;
            restModulo = (bitOfText.length() + parityLength)%numberOfFrequencies;
        }

        bitOfText = bitOfText + crc.parityBit(bitOfText/*, generatorPolynom*/);

        String[] bitStringArray = bitOfText.split("");
        bitStringArray = Arrays.copyOfRange(bitStringArray, 1, bitStringArray.length);

        return bitStringArray;
    }

    /**
     * Creates a inverted version of the message.
     * @param bitStringArray the array which will get inverted
     * @param messageLength length of the message
     * @return
     */
    private String[] createInvertedStringArray(String[] bitStringArray, int messageLength/*, int numberOfFrequencies, double mesLengthDividedNumFreq*/){
        String[] bitStringArrayInverted = new String[messageLength];
        for(int i = 0; i< messageLength; i++){
            if(bitStringArray[i].equals("0")){
                bitStringArrayInverted[i] = "1";
            }else if(bitStringArray[i].equals("1")){
                bitStringArrayInverted[i] = "0";
            }
        }
        return bitStringArrayInverted;
    }

    /**
     * Creates two-dimensional string arrays for the message and the inverted version of it and fills them with data from
     * the message arrays. String arrays for start-block and end-block get created and filled with audio data.
     * Iteration through the two-dimensional arrays and fills them with audio data.
     * @param bitStringArray normal message
     * @param bitStringArrayInverted inverted version of message
     * @param mesLengthDividedNumFreq number of blocks
     * @param numberOfFrequencies number of frequencies
     * @param doubleInverted boolean if inverting is used
     * @param messageLength
     * @return short array with whole encoded message
     */
    private short[] generateContainerArraysAndFillWithSignalData(String[] bitStringArray, String[] bitStringArrayInverted, double mesLengthDividedNumFreq, int numberOfFrequencies, boolean doubleInverted, int messageLength){
        short[] encodedMessage = null;

        // --- Creates binary message block arrays ---
        String[][] messageSplitted = new String[(int)mesLengthDividedNumFreq][numberOfFrequencies];
        String[][] messageSplittedInverted = new String[(int)mesLengthDividedNumFreq][numberOfFrequencies];

        for(int j = 0; j<mesLengthDividedNumFreq; j++) {
            if ((j + 1) != mesLengthDividedNumFreq) {
                for (int i = 0; i < numberOfFrequencies; i++) {
                    messageSplitted[j][i] = bitStringArray[i+(numberOfFrequencies*j)];
                    messageSplittedInverted[j][i] = bitStringArrayInverted[i+(numberOfFrequencies*j)];
                }
            }else if(messageLength%numberOfFrequencies==0){
                for (int i = 0; i < numberOfFrequencies; i++) {
                    messageSplitted[j][i] = bitStringArray[i+(numberOfFrequencies*j)];
                    messageSplittedInverted[j][i] = bitStringArrayInverted[i+(numberOfFrequencies*j)];
                }
            }else {
                int rest = messageLength % numberOfFrequencies;
                for (int i = 0; i < rest; i++) {
                    messageSplitted[j][i] = bitStringArray[i+(numberOfFrequencies*j)];
                    messageSplittedInverted[j][i] = bitStringArrayInverted[i+(numberOfFrequencies*j)];
                }
            }
        }
        // --- ----------------------------------- ---

        short[][] frequencyZeroTrack = new short[(int) mesLengthDividedNumFreq][];
        short[][] frequencyZeroTrackInverted = new short[(int) mesLengthDividedNumFreq][];

        // --- Create start and end blocks signals ---
        String[] protoArrayStart = new String[numberOfFrequencies];
        Arrays.fill(protoArrayStart, 0, (numberOfFrequencies/2)-1, "0");
        Arrays.fill(protoArrayStart, numberOfFrequencies/2, numberOfFrequencies, "1");
        short[] protoTrackStart = signalGen.getSignalBlock(SignalType.PLAYCONFIG, protoArrayStart);
        String[] protoArrayEnd = new String[numberOfFrequencies];
        Arrays.fill(protoArrayEnd, 0, numberOfFrequencies/2, "1");
        Arrays.fill(protoArrayEnd, numberOfFrequencies/2, numberOfFrequencies, "0");
        short[] protoTrackEnd = signalGen.getSignalBlock(SignalType.PLAYCONFIG, protoArrayEnd);
        // --- ----------------------------------- ---
        int pauseduration = config.getPauseperiod();
        short[] pauseTrack = null;
        if(pauseduration != 0) {
            // --- Create the pause signal to be reused---
            String[] pauseArray = new String[numberOfFrequencies];
            Arrays.fill(pauseArray, "0");
            pauseTrack = signalGen.getSignalBlock(SignalType.PAUSECONFIG, pauseArray);
            // --- ----------------------------------- ---
        }


        // --- Create the message block signals ---
        for(int k=0;k<mesLengthDividedNumFreq;k++) {
            frequencyZeroTrack[k] = signalGen.getSignalBlock(SignalType.PLAYCONFIG, messageSplitted[k]);
            frequencyZeroTrackInverted[k] = signalGen.getSignalBlock(SignalType.PLAYCONFIG, messageSplittedInverted[k]);
        }
        // --- ----------------------------------- ---

        encodedMessage = concatenateSignalBlocks(frequencyZeroTrack, frequencyZeroTrackInverted, protoTrackStart, protoTrackEnd, pauseTrack, pauseduration, doubleInverted);

        return encodedMessage;
    }

    /**
     * Takes all blocks and concatenates them in the right order.
     * @param frequencyZeroTrack normal message array with audio data
     * @param frequencyZeroTrackInverted inverted message array with audio data
     * @param protoTrackStart start block of the message
     * @param protoTrackEnd end block of the message
     * @param pauseTrack pause block
     * @param pauseduration length of the pause between message blocks
     * @param doubleInverted boolean if inverting is used
     * @return concatenated message short array
     */
    private short[] concatenateSignalBlocks(short[][] frequencyZeroTrack, short[][] frequencyZeroTrackInverted, short[] protoTrackStart, short[] protoTrackEnd, short[] pauseTrack, int pauseduration, boolean doubleInverted){
        short[] encodedMessage = null;

        for(int i=0; i<frequencyZeroTrack.length; i++){
            if(i==0){
                encodedMessage = ArrayUtils.addAll(encodedMessage,protoTrackStart);
                if(pauseduration != 0) {
                    encodedMessage = ArrayUtils.addAll(encodedMessage, pauseTrack);
                }
            }
            encodedMessage = ArrayUtils.addAll(encodedMessage,frequencyZeroTrack[i]);
            if(doubleInverted) {
                encodedMessage = ArrayUtils.addAll(encodedMessage, pauseTrack);
                encodedMessage = ArrayUtils.addAll(encodedMessage, frequencyZeroTrackInverted[i]);
            }
            if(i+1!=frequencyZeroTrack.length){
                if(pauseduration != 0) {
                    encodedMessage = ArrayUtils.addAll(encodedMessage, pauseTrack);
                }
            }
            if((i+1)==frequencyZeroTrack.length){
                if(pauseduration != 0) {
                    encodedMessage = ArrayUtils.addAll(encodedMessage, pauseTrack);
                }
                encodedMessage = ArrayUtils.addAll(encodedMessage,protoTrackEnd);
            }
        }

        return encodedMessage;
    }
}
