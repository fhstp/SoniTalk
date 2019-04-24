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


import java.util.Arrays;

/**
 * The CRC class adds and also checks the parity bits.
 * The main part of both functions is a xor_logic_gate.
 * Beside that it has a helper function for counting occurrences.
 */
public class CRC {
    private static int n = 0;
    private static byte[] generatorPolynom;

    public CRC(){
        this(ConfigConstants.GENERATOR_POLYNOM);
    }

    public CRC(byte[] generatorPolynom){
        if((generatorPolynom.length-1)%8==0){
            this.generatorPolynom = generatorPolynom;
        }else{
            throw new IllegalArgumentException("The CRC generator polynom length minus one must be dividable by eight. Please try the default CRC");
            //this.generatorPolynom = ConfigConstants.GENERATOR_POLYNOM;
        }
        //Log.d("CRC", String.valueOf((generatorPolynom.length-1)%8==0));
    }

    /**
     * Checks if a message was received correctly. Returns 0 if the CRC is correct.
     * @param messageDecoded bit sequence containing the message to be checked
     * @return 0 if the CRC is correct otherwise a positive integer
     */
    public int checkMessageCRC(int[] messageDecoded){
        String[] bitArray = new String[messageDecoded.length];

        for(int j = 0; j<messageDecoded.length; j++){
            bitArray[j] = String.valueOf(messageDecoded[j]);
        }

        byte[] byteMessage = new byte[bitArray.length];

        for (int i = 0; i < bitArray.length; i++) {
            byteMessage[i] = Byte.parseByte(bitArray[i]);
        }

        int countMatches = checkCRC(byteMessage);

        return countMatches;
    }

    /**
     * checkCRC is using the method xorArray, which is modifying byteMessage in place
     * @param byteMessage the byte array to check
     * @return 0 if the CRC is correct otherwise a positive integer
     */
    private int checkCRC(byte[] byteMessage){

        n = 0;
        byte[] helpBitArray = new byte[generatorPolynom.length];
        xorArray(byteMessage, helpBitArray);
        int countMatches = countOccurrences(Arrays.toString(byteMessage),'1');
        return countMatches;
    }

    /**
     * Calls xorArray to get a remainder bit sequence depending on the generator polynomal.
     * returns this bit sequence as error detection for the message
     * @param bitOfText the message after which the error detection bit sequence is generated
     * @return error detection bit sequence
     */
    public String parityBit(String bitOfText){
        String[] helpArray = bitOfText.split("");
        String[] bitArray = Arrays.copyOfRange(helpArray, 1, helpArray.length);

        byte[] byteMessage = new byte[bitArray.length+(generatorPolynom.length-1)];

        for (int i = 0; i < bitArray.length; i++) {
            byteMessage[i] = Byte.parseByte(bitArray[i]);
        }

        n = 0;
        byte[] helpBitArray = new byte[generatorPolynom.length-1];

        xorArray(byteMessage, helpBitArray);

        String helpString = "";
        for (byte b : helpBitArray) {
            helpString = helpString + b;
        }

        return helpString;
    }

    /**
     * xorArray is a logical xor gate. It modifies the byteMessage by using a generatorPolynom.
     * It does a recursion until the first char in byteMessage, with the length of the generatorPolynom
     * viewed backwards, is 0.
     * @param byteMessage byte array which will put through the logical xor gate
     * @param helpBitArray array to store intermediate values
     */
    private void xorArray(byte[] byteMessage, byte[] helpBitArray){
        int j = 0;
        int m = checkZeroMessage(byteMessage,j);
        if(byteMessage.length-m<generatorPolynom.length){
            int p = 0;
            for(int o = byteMessage.length-(generatorPolynom.length-1); o<byteMessage.length;o++){
                helpBitArray[p] = byteMessage[o];
                p++;
            }
        }else{
            for(j = 0; j<generatorPolynom.length; j++){
                int xor = byteMessage[m] ^ generatorPolynom[j];
                byteMessage[m] = (byte)(0xff & xor);
                m++;
            }
            xorArray(byteMessage, helpBitArray);
        }
    }

    /**
     * Checks how many 0s at the beginning of a byte array are there and returns this int value.
     * @param byteMessage the array to be checked
     * @param index the current position in the checked message
     * @return number of zeros
     */
    private int checkZeroMessage(byte[] byteMessage, int index){
        if(n<byteMessage.length) {
            if (byteMessage[index] == 0) {
                n++;
                checkZeroMessage(byteMessage, n);
            } else if (byteMessage[index] == 1) {
                return n;
            }
        }
        return n;
    }

    /**
     * Returns the number of occurrences of searchedChar in input.
     * Source : https://stackoverflow.com/a/275969/5232306
     * @param input
     * @param searchedChar
     * @return How many occurrences of searchedChar are in input.
     */
    public static int countOccurrences(String input, char searchedChar)
    {
        int count = 0;
        for (int i=0; i < input.length(); i++)
        {
            if (input.charAt(i) == searchedChar)
            {
                count++;
            }
        }
        return count;
    }
}
