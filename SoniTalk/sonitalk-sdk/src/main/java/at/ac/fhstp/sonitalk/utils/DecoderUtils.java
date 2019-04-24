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

import android.util.Log;

//import org.apache.commons.lang3.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Provides functions for removing filling characters and the crc-bits and for
 * casting bytes back to utf-8 characters. Furthermore, methods like calculating
 * the median and mean are included.
 */
public final class DecoderUtils {

    // Utility class, does not need to be instantiated.
    private DecoderUtils() {
    }

    public static String decodeBitToText(String bitOfText){
        byte[] parsedBytes = binaryToBytes(bitOfText);
        String readable = byteToUTF8(parsedBytes);
        return readable;
    }

    /**
     * Takes a String containing a bit sequence and return the corresponding byte array
     * @param input A String containing bits
     * @return A byte array corresponding to the input bit sequence
     */
    public static byte[] binaryToBytes(String input) {
        if (input == null)
            return null;
        else if (input.length() % 8 != 0) {
            Log.w("binaryToBytes", "got an input with a length not dividable by 8.");
            // raise an exception ? This case should never happen, and should not crash the decoding.
        }

        byte[] output = new byte[input.length() / 8];
        int counter = 0;
        for(int i = 0; i <= input.length() - 8; i+=8)
        {
            if((i+32) <= input.length() && input.substring(i, i+3).equals("1111")) {
                int k = Integer.parseInt(input.substring(i, i + 32), 2);
                output[counter++] = (byte)k;
                i+=24;
            }else if((i+24) <= input.length() && input.substring(i, i+2).equals("111")) {
                int k = Integer.parseInt(input.substring(i, i + 24), 2);
                output[counter++] = (byte)k;
                i+=16;
            }else if((i+16) <= input.length() && input.substring(i, i+1).equals("11")) {
                int k = Integer.parseInt(input.substring(i, i + 16), 2);
                output[counter++] = (byte)k;
                i+=8;
            }else {
                int k = Integer.parseInt(input.substring(i, i + 8), 2);
                output[counter++] = (byte)k;
            }

            /*int k = Integer.parseInt(input.substring(i, i+8), 2);
            Log.d("DecoderUtils", String.valueOf(k));
            output[counter++] = (byte) k;*/
        }
        return output;
    }

    public static String byteToUTF8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Checks for filling characters and error correction bits and deletes them.
     * @param bitMessage message to check
     * @param generatorPolynomLength length of crc generator polynom
     * @return the clean message
     */
    public static String removeFillingCharsAndCRCChars(String bitMessage, int generatorPolynomLength){
        String realMessage = null;
        String controlFillCharacter = "00011001";

        //To improve: only make substring for the message it self
        String crcString =  bitMessage.substring((bitMessage.length()-(generatorPolynomLength-1)), bitMessage.length());

        bitMessage = bitMessage.replace(crcString, "");

        for(int i = 0; i <= bitMessage.length() - 8; i+=8){
            if(bitMessage.substring(i, i+8).equals(controlFillCharacter)){
                char[] helpArray = bitMessage.toCharArray();
                for(int j = 0; j < 8; j++){
                    helpArray[i+j] = ' ';
                }
                bitMessage = String.valueOf(helpArray);
            }
        }
        bitMessage = bitMessage.replace(" ", "");
        realMessage = bitMessage;

        return realMessage;
    }


    /**
     * Helper to get maximum value out of double array
     * @param values array to get the max of
     * @return the highest value of the array passed
     */
    public static double max(double[] values) {
        double max = 0;
        double helper;
        for(int i = 0; i < values.length; i++){
            helper = values[i];
            if(helper > max){
                max = helper;
            }
        }
        return max;
    }

    /**
     * the array double[] m MUST BE SORTED
     * @param m
     * @return the mean of the array passed
     */
    public static double mean(double[] m) {
        double sum = 0;
        for (int i = 0; i < m.length; i++) {
            sum += m[i];
        }
        return sum / m.length;
    }

    /**
     * the array double[] m MUST BE SORTED
     * @param m
     * @return the median of the array passed
     */
    public static double median(double[] m) {
        int middle = m.length/2;
        if (m.length%2 == 1) {
            return m[middle];
        } else {
            return (m[middle-1] + m[middle]) / 2.0;
        }
    }

    public static float getRelativeIndexPosition(float value, float minValue, float maxValue) {
        //Log.d("getrelatvieIndex", String.valueOf((value-minValue)/(maxValue-minValue)));
        return (value-minValue)/(maxValue-minValue);
    }

    /**
     * Get the index of a specific frequency depending on sample rate and window length
     * @param freq frequency to get the index of
     * @param fs sample rate
     * @param winlen window length
     * @return the index of the frequency passed
     */
    public static float freq2idx(int freq, int fs, int winlen){
        float freqIdx;
        freqIdx = Math.round((float)freq/(float)fs*(float)winlen)+1;
        return freqIdx;
    }

    /**
     * Calculates power of two values
     * @param n initial value
     * @return the next power of two value of an integer passed
     */
    public static int nextPowerOfTwo(int n) {
        return (int) Math.pow(2.0, Math.ceil(Math.log(n)/Math.log(2)));
    }

    /**
     * Calculates an absolute value of a complex number
     * @param real
     * @param imaginary
     * @return the absolute value of the real and imaginary parts passed
     */
    public static double getComplexAbsolute(double real, double imaginary) {
        return Math.sqrt(real*real + (imaginary*imaginary));
    }


}
