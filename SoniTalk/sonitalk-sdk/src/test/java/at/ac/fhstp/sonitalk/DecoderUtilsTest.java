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

import org.junit.Test;
import static org.junit.Assert.assertEquals;

import at.ac.fhstp.sonitalk.utils.DecoderUtils;

public class DecoderUtilsTest {

    @Test
    public void getMinBufferSizeDividableByEight() throws Exception {
        int usualValue = 4410;
        //SoniTalkDecoder audioSmartMessageDecoder = new SoniTalkDecoder(44100, MockContext);
        //
    }

    @Test
    public void nextPowerOfTwo() throws Exception {
        assertEquals(4, DecoderUtils.nextPowerOfTwo(3));
        assertEquals(8, DecoderUtils.nextPowerOfTwo(7));
        assertEquals(16, DecoderUtils.nextPowerOfTwo(9));
        assertEquals(64, DecoderUtils.nextPowerOfTwo(64));
        assertEquals(4096, DecoderUtils.nextPowerOfTwo(2205));
    }

    @Test
    public void bandpassWidth() throws Exception {
        assertEquals(750, DecoderUtils.getBandpassWidth(16, 100));
        assertEquals(350, DecoderUtils.getBandpassWidth(8, 100));
        assertEquals(375, DecoderUtils.getBandpassWidth(16, 50));
    }
}
