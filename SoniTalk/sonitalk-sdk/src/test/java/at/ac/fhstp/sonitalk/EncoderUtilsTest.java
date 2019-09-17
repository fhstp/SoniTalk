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

import android.util.Log;

import org.apache.log4j.jmx.LoggerDynamicMBean;
import org.junit.Test;

import at.ac.fhstp.sonitalk.utils.DecoderUtils;
import at.ac.fhstp.sonitalk.utils.EncoderUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EncoderUtilsTest {

    @Test
    public void nMessageBlock() throws Exception {
        assertEquals(20, EncoderUtils.calculateNumberOfMessageBlocks(8, 18));
        assertEquals(10, EncoderUtils.calculateNumberOfMessageBlocks(16, 18));
        assertEquals(7, EncoderUtils.calculateNumberOfMessageBlocks(24, 18));
        assertEquals(5, EncoderUtils.calculateNumberOfMessageBlocks(32, 18));
        assertEquals(4, EncoderUtils.calculateNumberOfMessageBlocks(40, 18));
        assertEquals(4, EncoderUtils.calculateNumberOfMessageBlocks(48, 18));
    }

    @Test
    public void methodsCompatibility() throws  Exception {
        int nFrequencies = 16;
        int messageLengthInBytes = 18;
        int calculatedNbMsgBlock = EncoderUtils.calculateNumberOfMessageBlocks(nFrequencies, messageLengthInBytes);
        int calculatedMaxChar = EncoderUtils.getMaxChars(calculatedNbMsgBlock, nFrequencies);
        assertTrue(calculatedMaxChar >= messageLengthInBytes);

        nFrequencies = 8;
        calculatedNbMsgBlock = EncoderUtils.calculateNumberOfMessageBlocks(nFrequencies, messageLengthInBytes);
        calculatedMaxChar = EncoderUtils.getMaxChars(calculatedNbMsgBlock, nFrequencies);
        assertTrue(calculatedMaxChar >= messageLengthInBytes);

        nFrequencies = 24;
        calculatedNbMsgBlock = EncoderUtils.calculateNumberOfMessageBlocks(nFrequencies, messageLengthInBytes);
        calculatedMaxChar = EncoderUtils.getMaxChars(calculatedNbMsgBlock, nFrequencies);
        assertTrue(calculatedMaxChar >= messageLengthInBytes);

        nFrequencies = 32;
        calculatedNbMsgBlock = EncoderUtils.calculateNumberOfMessageBlocks(nFrequencies, messageLengthInBytes);
        calculatedMaxChar = EncoderUtils.getMaxChars(calculatedNbMsgBlock, nFrequencies);
        assertTrue(calculatedMaxChar >= messageLengthInBytes);

        nFrequencies = 48;
        calculatedNbMsgBlock = EncoderUtils.calculateNumberOfMessageBlocks(nFrequencies, messageLengthInBytes);
        calculatedMaxChar = EncoderUtils.getMaxChars(calculatedNbMsgBlock, nFrequencies);
        assertTrue(calculatedMaxChar >= messageLengthInBytes);

        nFrequencies = 16;
        messageLengthInBytes = 1;
        calculatedNbMsgBlock = EncoderUtils.calculateNumberOfMessageBlocks(nFrequencies, messageLengthInBytes);
        calculatedMaxChar = EncoderUtils.getMaxChars(calculatedNbMsgBlock, nFrequencies);
        assertTrue(calculatedMaxChar >= messageLengthInBytes);

        messageLengthInBytes = 13;
        calculatedNbMsgBlock = EncoderUtils.calculateNumberOfMessageBlocks(nFrequencies, messageLengthInBytes);
        calculatedMaxChar = EncoderUtils.getMaxChars(calculatedNbMsgBlock, nFrequencies);
        assertTrue(calculatedMaxChar >= messageLengthInBytes);

        messageLengthInBytes = 17;
        calculatedNbMsgBlock = EncoderUtils.calculateNumberOfMessageBlocks(nFrequencies, messageLengthInBytes);
        calculatedMaxChar = EncoderUtils.getMaxChars(calculatedNbMsgBlock, nFrequencies);
        assertTrue(calculatedMaxChar >= messageLengthInBytes);
    }
    /*
    @Test
    public void nextPowerOfTwo() throws Exception {
        assertEquals(4, DecoderUtils.nextPowerOfTwo(3));
        assertEquals(8, DecoderUtils.nextPowerOfTwo(7));
        assertEquals(16, DecoderUtils.nextPowerOfTwo(9));
        assertEquals(64, DecoderUtils.nextPowerOfTwo(64));
        assertEquals(4096, DecoderUtils.nextPowerOfTwo(2205));
    }
    */
}
