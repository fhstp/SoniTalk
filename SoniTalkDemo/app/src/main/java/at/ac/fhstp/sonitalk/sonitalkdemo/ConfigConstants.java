/*
 * Copyright (c) 2019. Alexis Ringot, Florian Taurer, Matthias Zeppelzauer.
 *
 * This file is part of SoniTalk Demo app.
 *
 * SoniTalk Demo app is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SoniTalk Demo app is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SoniTalk Demo app.  If not, see <http://www.gnu.org/licenses/>.
 */

package at.ac.fhstp.sonitalk.sonitalkdemo;

public class ConfigConstants {

    public static final String FREQUENCY_ZERO = "etprefFrequencyZero";
    public static final String BIT_PERIOD = "etprefBitperiod";
    public static final String PAUSE_PERIOD = "etprefPauseperiod";
    public static final String NUMBER_OF_FREQUENCIES = "lpprefNFrequencies";
    public static final String SPACE_BETWEEN_FREQUENCIES = "etprefFrequencyspace";
    public static final String NUMBER_OF_BYTES = "etprefNMaxBytes";
    public static final String LOUDNESS = "sbprefLoudness";
    public static final String PRESET = "setPresetPreferences";

    public static final String SETTING_FREQUENCY_ZERO_DEFAULT = "18000";
    public static final String SETTING_BIT_PERIOD_DEFAULT = "100";
    public static final String SETTING_PAUSE_PERIOD_DEFAULT = "0";
    public static final String SETTING_NUMBER_OF_FREQUENCIES_DEFAULT = "16";
    public static final String SETTING_SPACE_BETWEEN_FREQUENCIES_DEFAULT = "100";
    public static final String SETTING_NUMBER_OF_BYTES_DEFAULT = "18";
    public static final int SETTING_LOUDNESS_DEFAULT = 70;

    public static final String PREFERENCE_RESET_PREFERENCES = "resetPreferences";
    public static final String PREFERENCE_PRESET_PREFERENCES = "setPresetPreferences";

    public static final String CURRENT_VOLUME = "current-volume";
    public static final int CURRENT_VOLUME_DEFAULT = 70;

    public static final String SEND_BUTTON_ENABLED = "send-button-enabled";
    public static final String LISTEN_BUTTON_ENABLED = "listen-button-enabled";
    public static final String STOP_LISTEN_BUTTON_ENABLED = "stop-listen-button-enabled";

    public static final String TEXT_TO_SEND = "text-to-send";
}
