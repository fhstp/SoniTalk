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

import at.ac.fhstp.sonitalk.utils.CircularArray;

import static org.junit.Assert.assertEquals;

public class CircularArrayTest {

    @Test
    public void normalAdd() throws Exception {
        int historyBufferSize = 100;
        int analysisWinLen = 10;
        CircularArray array = new CircularArray(historyBufferSize);
        for (int loop = 1; loop <= 10; loop++) {
            float currentData[] = new float[analysisWinLen];
            for (int i = 0; i < analysisWinLen; i++) {
                currentData[i] = i * loop;
            }
            array.add(currentData);
        }

        assertEquals(90.0f, array.getArray()[array.size()-1], 0.0001);
    }

    @Test
    public void overflowingAdd() throws Exception {
        int historyBufferSize = 95;
        int analysisWinLen = 10;
        int nbLoop = 10;
        CircularArray array = new CircularArray(historyBufferSize);
        for (int loop = 1; loop <= nbLoop; loop++) {
            float currentData[] = new float[analysisWinLen];
            for (int i = 0; i < analysisWinLen; i++) {
                currentData[i] = i * loop;
            }
            array.add(currentData);
        }

        assertEquals(90.0f, array.getArray()[historyBufferSize-1], 0.0001); // the last entry should be the last created
        assertEquals(5.0f, array.getArray()[0], 0.0001); // the first entry should be the element totalElem%size (here 100%95 is the fifth)
    }

    @Test
    public void lastWindowEasy() throws Exception {
        int historyBufferSize = 100;
        int analysisWinLen = 10;
        CircularArray array = new CircularArray(historyBufferSize);
        for (int loop = 1; loop <= 10; loop++) {
            float currentData[] = new float[analysisWinLen];
            for (int i = 0; i < analysisWinLen; i++) {
                currentData[i] = i * loop;
            }
            array.add(currentData);
        }

        assertEquals(90.0f, array.getLastWindow(analysisWinLen)[analysisWinLen-1], 0.0001);
        assertEquals(0.0f, array.getLastWindow(analysisWinLen)[0], 0.0001);
        //assertEquals(90.0f, array.getArray()[array.size()-1], 0.0001);
    }

    @Test
    public void lastWindowOverflow() throws Exception {
        int historyBufferSize = 95;
        int analysisWinLen = 10;
        int nbLoop = 10;
        CircularArray array = new CircularArray(historyBufferSize);
        for (int loop = 1; loop <= nbLoop; loop++) {
            float currentData[] = new float[analysisWinLen];
            for (int i = 0; i < analysisWinLen; i++) {
                currentData[i] = i * loop;
            }
            array.add(currentData);
        }

        assertEquals(90.0f, array.getLastWindow(analysisWinLen)[analysisWinLen-1], 0.0001);
        assertEquals(0.0f, array.getLastWindow(analysisWinLen)[0], 0.0001);
    }

    @Test
    public void firstWindowOverflow() throws Exception {
        int historyBufferSize = 95;
        int analysisWinLen = 10;
        int nbLoop = 10;
        CircularArray array = new CircularArray(historyBufferSize);
        for (int loop = 1; loop <= nbLoop; loop++) {
            float currentData[] = new float[analysisWinLen];
            for (int i = 0; i < analysisWinLen; i++) {
                currentData[i] = i * loop;
            }
            array.add(currentData);
        }

        assertEquals(8.0f, array.getFirstWindow(analysisWinLen)[analysisWinLen-1], 0.0001); //10th element of the first buffer after 5 overflow is 4*2
        assertEquals(5.0f, array.getFirstWindow(analysisWinLen)[0], 0.0001); //1st element after 5 overflow is 1*5
    }
}
