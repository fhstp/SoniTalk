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

/**
 * Configuration, or profile, used to transmit data. The emitter and receiver of a message must use
 * the same configuration. A crucial use case will be transmitting with several profiles simultaneously.
 * This will allow for faster communication within one app and simultaneous communication of several apps.
 */
public class SoniTalkConfig {
    private int frequencyZero;// = 18000; (Hz)
    private int bitperiod;// = 100; (ms)
    private int pauseperiod;// = 0; (ms)
    private int nMaxCharacters;// = 18; // Remove ? Not used anymore. or Rename to payload ? or nBytesMessage ? nBytesContent ?
    private int nParityBytes; //  = 2 Parity bytes (actually 16 bits)
    private int nMessageBlocks;
    private int nFrequencies;// = 16;
    private int frequencySpace;// = 100; (Hz)

    public SoniTalkConfig(int frequencyZero, int bitperiod, int pauseperiod, int nMessageBlocks, int nFrequencies, int frequencySpace) {
        this.frequencyZero = frequencyZero;
        this.bitperiod = bitperiod;
        this.pauseperiod = pauseperiod;
        this.nMessageBlocks = nMessageBlocks;
        this.nFrequencies = nFrequencies;
        this.frequencySpace = frequencySpace;
    }

    public int getFrequencyZero() {
        return frequencyZero;
    }

    public void setFrequencyZero(int frequencyZero) {
        this.frequencyZero = frequencyZero;
    }

    public int getBitperiod() {
        return bitperiod;
    }

    public void setBitperiod(int bitperiod) {
        this.bitperiod = bitperiod;
    }

    public int getPauseperiod() {
        return pauseperiod;
    }

    public void setPauseperiod(int pauseperiod) {
        this.pauseperiod = pauseperiod;
    }

    public int getnMessageBlocks() {
        return nMessageBlocks;
    }

    public void setnMessageBlocks(int nMessageBlocks) {
        this.nMessageBlocks = nMessageBlocks;
    }

    public int getnFrequencies() {
        return nFrequencies;
    }

    public void setnFrequencies(int nFrequencies) {
        this.nFrequencies = nFrequencies;
    }

    public int getFrequencySpace() {
        return frequencySpace;
    }

    public void setFrequencySpace(int frequencySpace) {
        this.frequencySpace = frequencySpace;
    }
}
