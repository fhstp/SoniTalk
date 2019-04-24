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

/**
 * Class handling a circular array of the incoming audio data to reduce memory allocation.
 * This class is NOT thread safe.
 */
public class CircularArray {
    private float circularArray[];
    private int index;
    private int analysisIndex;
    private int size;

    public CircularArray(int size) {
        this.circularArray = new float[size]; // Java arrays get default values on initialization, here 0.0f
        this.index = 0;
        this.analysisIndex = 0;
        this.size = size;
    }

    public void add(float[] values) {
        int spaceLeft = size-index;
        if(values.length > spaceLeft) {
            System.arraycopy(values, 0, circularArray, index, spaceLeft);
            System.arraycopy(values, spaceLeft, circularArray, 0, values.length-spaceLeft);
        }
        else {
            System.arraycopy(values, 0, circularArray, index, values.length);
        }
        index = (index + values.length) % size;
    }

    public float[] getArray() {
        if(index == 0) {
            return circularArray.clone();
        }
        else {
            float array[] = new float[size];
            int nbElemToEnd = size - index;
            System.arraycopy(circularArray, index, array, 0, nbElemToEnd);
            System.arraycopy(circularArray, 0, array, nbElemToEnd, index);
            return array;
        }
    }

    public int size() {
        return size;
    }

    public float[] getFirstWindow(int windowLength) {
        float array[] = new float[windowLength];
        int nbElemToEnd = size - index;
        if(windowLength <= nbElemToEnd) {
            System.arraycopy(circularArray, index, array, 0, windowLength);
        }
        else {
            System.arraycopy(circularArray, index, array, 0, nbElemToEnd);
            System.arraycopy(circularArray, 0, array, nbElemToEnd, windowLength-nbElemToEnd);
        }
        return array;
    }

    public float[] getLastWindow(int windowLength) {
        float array[] = new float[windowLength];
        if(windowLength <= index) {
            System.arraycopy(circularArray, index-windowLength, array, 0, windowLength);
        }
        else {
            System.arraycopy(circularArray, size-(windowLength-index), array, 0, windowLength-index);
            System.arraycopy(circularArray, 0, array, windowLength-index, index);
        }
        return array;
    }

    public void incrementAnalysisIndex(int incrementSize) {
        analysisIndex = (analysisIndex + incrementSize) % this.size;
    }
}
