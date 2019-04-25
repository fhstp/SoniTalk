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


/*package-private*/ enum SignalType {
    /***
     * WARNING: Changing the String values would make the app incompatible with previous version.
     * (The Strings are used to persist values in SharedPreferences)
     */

    /* Unused code calling main ?
    UNKNOWN("Unknown"),
     */
    PLAYCONFIG("Playconfig"),
    PAUSECONFIG("Pauseconfig");

    private String stringValue;

    private SignalType(String toString){
        stringValue = toString;
    }

    public String toString(){
        return stringValue;
    }

    public static SignalType fromString(String text) throws IllegalArgumentException {
        for (SignalType t : SignalType.values()) {
            if (t.stringValue.equalsIgnoreCase(text)) {
                return t;
            }
        }
        throw new IllegalArgumentException("No SignalType with text " + text + " found");
    }
}