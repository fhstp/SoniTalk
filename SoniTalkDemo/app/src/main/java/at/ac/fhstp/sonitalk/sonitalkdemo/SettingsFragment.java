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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.preference.PreferenceFragment;
import android.preference.*;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;

import at.ac.fhstp.sonitalk.SoniTalkConfig;
import at.ac.fhstp.sonitalk.exceptions.ConfigException;
import at.ac.fhstp.sonitalk.utils.ConfigFactory;

public class SettingsFragment extends PreferenceFragment {

    private EditTextPreference etFrequencyZero;
    private EditTextPreference etBitperiod;
    private EditTextPreference etPauseperiod;
    private EditTextPreference etFrequencyspace;
    private EditTextPreference etNMaxCharacters;
    private ListPreference lpNumberOfFrequencies;
    private EditTextPreference sbprefLoudness;
    private Preference prefPresets;

    AlertDialog alertReset = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);

        etFrequencyZero = (EditTextPreference) findPreference(ConfigConstants.FREQUENCY_ZERO);
        etBitperiod = (EditTextPreference) findPreference(ConfigConstants.BIT_PERIOD);
        etPauseperiod = (EditTextPreference) findPreference(ConfigConstants.PAUSE_PERIOD);
        etFrequencyspace = (EditTextPreference) findPreference(ConfigConstants.SPACE_BETWEEN_FREQUENCIES);
        etNMaxCharacters = (EditTextPreference) findPreference(ConfigConstants.NUMBER_OF_BYTES);
        lpNumberOfFrequencies = (ListPreference) findPreference(ConfigConstants.NUMBER_OF_FREQUENCIES);
        sbprefLoudness = (EditTextPreference) findPreference(ConfigConstants.LOUDNESS);
        prefPresets  = findPreference(ConfigConstants.PREFERENCE_PRESET_PREFERENCES);

        setPreferenceValues();

        final EditTextPreference prefBitperiod = (EditTextPreference)findPreference(ConfigConstants.BIT_PERIOD);
        prefBitperiod.setOnPreferenceClickListener(new EditTextPreference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                return onEditTextPreferenceClick(prefBitperiod, R.string.settings_bitperiod_text, ConfigConstants.SETTING_BIT_PERIOD_LOWER_LIMIT, ConfigConstants.SETTING_BIT_PERIOD_UPPER_LIMIT);
            }
        });
        prefBitperiod.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if(newValue.toString().trim().equals("")){
                    showToastNoCharactersEntered();
                    return false;
                }
                if (!newValue.toString().matches("[0-9]+")){
                    showToastNoNumericCharacterEntered();
                    return false;
                }
                if (Integer.valueOf(newValue.toString())<ConfigConstants.SETTING_BIT_PERIOD_LOWER_LIMIT){
                    showToastLowerLimitExceeded(ConfigConstants.SETTING_BIT_PERIOD_LOWER_LIMIT);
                    return false;
                }
                if (Integer.valueOf(newValue.toString())> ConfigConstants.SETTING_BIT_PERIOD_UPPER_LIMIT){
                    showToastUpperLimitExceeded( ConfigConstants.SETTING_BIT_PERIOD_UPPER_LIMIT);
                    return false;
                }
                String prefBitperiodStr = String.format(getString(R.string.settings_bitperiod_text), String.valueOf(newValue));
                prefBitperiod.setTitle(prefBitperiodStr);
                setUndefinedPresetName();
                return true;
            }
        });

        final EditTextPreference prefPauseperiod = (EditTextPreference)findPreference(ConfigConstants.PAUSE_PERIOD);
        prefPauseperiod.setOnPreferenceClickListener(new EditTextPreference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                return onEditTextPreferenceClick(prefPauseperiod, R.string.settings_pauseperiod_text, ConfigConstants.SETTING_PAUSE_PERIOD_LOWER_LIMIT, ConfigConstants.SETTING_PAUSE_PERIOD_UPPER_LIMIT);
            }
        });
        prefPauseperiod.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if(newValue.toString().trim().equals("")){
                    showToastNoCharactersEntered();
                    return false;
                }
                if (!newValue.toString().matches("[0-9]+")){
                    showToastNoNumericCharacterEntered();
                    return false;
                }
                if (Integer.valueOf(newValue.toString())< ConfigConstants.SETTING_PAUSE_PERIOD_LOWER_LIMIT){
                    showToastLowerLimitExceeded( ConfigConstants.SETTING_PAUSE_PERIOD_LOWER_LIMIT);
                    return false;
                }
                if (Integer.valueOf(newValue.toString())>ConfigConstants.SETTING_PAUSE_PERIOD_UPPER_LIMIT){
                    showToastUpperLimitExceeded(ConfigConstants.SETTING_PAUSE_PERIOD_UPPER_LIMIT);
                    return false;
                }
                String prefPauseperiodStr = String.format(getString(R.string.settings_pauseperiod_text), String.valueOf(newValue));
                prefPauseperiod.setTitle(prefPauseperiodStr);
                setUndefinedPresetName();
                return true;
            }
        });

        final EditTextPreference prefFrequencyZero = (EditTextPreference)findPreference(ConfigConstants.FREQUENCY_ZERO);
        prefFrequencyZero.setOnPreferenceClickListener(new EditTextPreference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                return onEditTextPreferenceClick(prefFrequencyZero, R.string.settings_frequency0_text, ConfigConstants.SETTING_FREQUENCY_ZERO_LOWER_LIMIT, ConfigConstants.SETTING_FREQUENCY_ZERO_UPPER_LIMIT);
            }
        });
        prefFrequencyZero.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if(newValue.toString().trim().equals("")){
                    showToastNoCharactersEntered();
                    return false;
                }
                if (!newValue.toString().matches("[0-9]+")){
                    showToastNoNumericCharacterEntered();
                    return false;
                }
                if (Integer.valueOf(newValue.toString())<ConfigConstants.SETTING_FREQUENCY_ZERO_LOWER_LIMIT) {
                    showToastLowerLimitExceeded(ConfigConstants.SETTING_FREQUENCY_ZERO_LOWER_LIMIT);
                    return false;
                }
                if (Integer.valueOf(newValue.toString())>ConfigConstants.SETTING_FREQUENCY_ZERO_UPPER_LIMIT) {
                    showToastUpperLimitExceeded(ConfigConstants.SETTING_FREQUENCY_ZERO_UPPER_LIMIT);
                    return false;
                }
                String prefFrequencyZeroStr = String.format(getString(R.string.settings_frequency0_text), String.valueOf(newValue));
                prefFrequencyZero.setTitle(prefFrequencyZeroStr);
                setUndefinedPresetName();
                return true;
            }
        });

        final EditTextPreference prefFrequencyspace = (EditTextPreference)findPreference(ConfigConstants.SPACE_BETWEEN_FREQUENCIES);
        prefFrequencyspace.setOnPreferenceClickListener(new EditTextPreference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                return onEditTextPreferenceClick(prefFrequencyspace, R.string.settings_frequencyspace_text, ConfigConstants.SETTING_SPACE_BETWEEN_FREQUENCIES_LOWER_LIMIT, ConfigConstants.SETTING_SPACE_BETWEEN_FREQUENCIES_UPPER_LIMIT);
            }
        });
        prefFrequencyspace.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if(newValue.toString().trim().equals("")){
                    showToastNoCharactersEntered();
                    return false;
                }
                if (!newValue.toString().matches("[0-9]+")){
                    showToastNoNumericCharacterEntered();
                    return false;
                }
                if (Integer.valueOf(newValue.toString())<ConfigConstants.SETTING_SPACE_BETWEEN_FREQUENCIES_LOWER_LIMIT) {
                    showToastLowerLimitExceeded(ConfigConstants.SETTING_SPACE_BETWEEN_FREQUENCIES_LOWER_LIMIT);
                    return false;
                }
                if (Integer.valueOf(newValue.toString())>ConfigConstants.SETTING_SPACE_BETWEEN_FREQUENCIES_UPPER_LIMIT) {
                    showToastUpperLimitExceeded(ConfigConstants.SETTING_SPACE_BETWEEN_FREQUENCIES_UPPER_LIMIT);
                    return false;
                }
                String prefFrequencyspaceStr = String.format(getString(R.string.settings_frequencyspace_text), String.valueOf(newValue));
                prefFrequencyspace.setTitle(prefFrequencyspaceStr);
                setUndefinedPresetName();
                return true;
            }
        });

        final EditTextPreference prefNMaxCharacters = (EditTextPreference)findPreference(ConfigConstants.NUMBER_OF_BYTES);
        prefNMaxCharacters.setOnPreferenceClickListener(new EditTextPreference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                return onEditTextPreferenceClick(prefNMaxCharacters, R.string.settings_maxbytes_text, ConfigConstants.SETTING_NUMBER_OF_BYTES_LOWER_LIMIT, ConfigConstants.SETTING_NUMBER_OF_BYTES_UPPER_LIMIT);
            }
        });
        prefNMaxCharacters.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if(newValue.toString().trim().equals("")){
                    showToastNoCharactersEntered();
                    return false;
                }
                if (!newValue.toString().matches("[0-9]+")){
                    showToastNoNumericCharacterEntered();
                    return false;
                }
                if (newValue.toString().length()<ConfigConstants.SETTING_NUMBER_OF_BYTES_LOWER_LIMIT){
                    showToastLowerLimitExceeded(ConfigConstants.SETTING_NUMBER_OF_BYTES_LOWER_LIMIT);
                    return false;
                }
                if (newValue.toString().length()>ConfigConstants.SETTING_NUMBER_OF_BYTES_UPPER_LIMIT){
                    showToastUpperLimitExceeded(ConfigConstants.SETTING_NUMBER_OF_BYTES_UPPER_LIMIT);
                    return false;
                }
                String prefNMaxCharactersStr = String.format(getString(R.string.settings_maxbytes_text), String.valueOf(newValue));
                prefNMaxCharacters.setTitle(prefNMaxCharactersStr);
                setUndefinedPresetName();
                return true;
            }
        });

        final Preference prefNumberOfFrequencies = findPreference(ConfigConstants.NUMBER_OF_FREQUENCIES);
        prefNumberOfFrequencies.setOnPreferenceClickListener(new EditTextPreference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                ListPreference prefEdit = (ListPreference) preference;
                Dialog prefDialog = prefEdit.getDialog();
                String prefBitperiodStr = getString(R.string.settings_numberoffrequencies_text_dialog);
                prefDialog.setTitle(prefBitperiodStr);
                return true;
            }
        });
        prefNumberOfFrequencies.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if(newValue.toString().trim().equals("")){
                    showToastNoCharactersEntered();
                    return false;
                }
                if (!newValue.toString().matches("[0-9]+")){
                    showToastNoNumericCharacterEntered();
                    return false;
                }
                String prefNumberOfFrequenciesStr = String.format(getString(R.string.settings_numberoffrequencies_text), String.valueOf(newValue));
                prefNumberOfFrequencies.setTitle(prefNumberOfFrequenciesStr);
                setUndefinedPresetName();
                return true;
            }
        });

        final EditTextPreference prefLoudness = (EditTextPreference) findPreference(ConfigConstants.LOUDNESS);
        prefLoudness.setOnPreferenceClickListener(new EditTextPreference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                return onEditTextPreferenceClick(prefLoudness, R.string.settings_loudness_text, ConfigConstants.SETTING_LOUDNESS_LOWER_LIMIT, ConfigConstants.SETTING_LOUDNESS_UPPER_LIMIT);
            }
        });
        prefLoudness.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if(newValue.toString().trim().equals("")){
                    showToastNoCharactersEntered();
                    return false;
                }else if (!newValue.toString().matches("[0-9]+")){
                    showToastNoNumericCharacterEntered();
                    return false;
                }else if(Integer.valueOf(newValue.toString())==ConfigConstants.SETTING_LOUDNESS_LOWER_LIMIT){
                    showToastLowerLimitExceeded(ConfigConstants.SETTING_LOUDNESS_LOWER_LIMIT);
                    return false;
                }
                if(Integer.valueOf(newValue.toString())>ConfigConstants.SETTING_LOUDNESS_UPPER_LIMIT){
                    showToastUpperLimitExceeded(ConfigConstants.SETTING_LOUDNESS_UPPER_LIMIT);
                    return false;
                }
                String prefLoudnessStr = String.format(getString(R.string.settings_loudness_text), String.valueOf(newValue));
                prefLoudness.setTitle(prefLoudnessStr);
                setUndefinedPresetName();
                return true;
            }
        });

        final Preference prefReset = findPreference(ConfigConstants.PREFERENCE_RESET_PREFERENCES);
        prefReset.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final AlertDialog.Builder resetSettingsDialog = new AlertDialog.Builder(getActivity());
                resetSettingsDialog.setTitle(R.string.action_reset_settings_title)
                        .setMessage(R.string.action_reset_settings_message)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                resetSettings();
                            }
                        })
                        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                alertReset.cancel();
                            }
                        })
                        .setIcon(android.R.drawable.ic_dialog_alert);
                alertReset = resetSettingsDialog.show();
                return false;
            }
        });

        final Preference presets = findPreference(ConfigConstants.PREFERENCE_PRESET_PREFERENCES);
        presets.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                //String[] rawlist = null;
                //rawlist = ArrayUtils.addAll(rawlist, getRawList(getContext()));
                //rawlist = ArrayUtils.addAll(rawlist, ConfigFactory.getConfigList(getContext()));
                //final String[] configList = rawlist;
                final String[] configList = ConfigFactory.getConfigList(getContext());

                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle(getString(R.string.settings_configuration_alert_title));
                builder.setItems(configList, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SoniTalkConfig config = null;
                        String configName = null;
                        try{
                            config = ConfigFactory.loadFromJson(configList[which], getContext());
                            configName = configList[which];
                        }catch (ConfigException ce){
                            ce.printStackTrace();
                        }catch (IOException ioe){
                            ioe.printStackTrace();
                        }

                        if(config != null){
                            setToConfig(config, configName);
                            String presetsStr = String.format(getString(R.string.settings_preset_title), String.valueOf(configName));
                            presets.setTitle(presetsStr);
                        }
                    }
                });
                builder.show();

                return false;
            }
        });
    }


    /*private String[] getRawList(Context context){
        String[] list = null;
        try {
            list = context.getAssets().list("configs");
        } catch (IOException e) {
            e.printStackTrace();
        }

        return list;

        Field[] fields = R.raw.class.getFields();
        String[] rawlist = new String[fields.length];
        for(int count=0; count < fields.length; count++){
            Log.i("Raw: ", fields[count].getName());
            rawlist[count] = fields[count].getName();
        }
        //SoniTalkConfig config = ConfigFactory.loadFromJson("lowFrequenciesConfig.json", this.getApplicationContext());
        return rawlist;
    }*/

    private void resetSettings() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = preferences.edit();
        SoniTalkConfig config = getDefaultSettings();

        editor.putString(ConfigConstants.FREQUENCY_ZERO, String.valueOf(config.getFrequencyZero()));
        editor.putString(ConfigConstants.BIT_PERIOD, String.valueOf(config.getBitperiod()));
        editor.putString(ConfigConstants.PAUSE_PERIOD, String.valueOf(config.getPauseperiod()));
        editor.putString(ConfigConstants.SPACE_BETWEEN_FREQUENCIES, String.valueOf(config.getFrequencySpace()));
        editor.putString(ConfigConstants.NUMBER_OF_BYTES, String.valueOf((config.getnMessageBlocks()*2-2)));
        editor.putString(ConfigConstants.LOUDNESS, ConfigConstants.SETTING_LOUDNESS_DEFAULT);
        editor.putString(ConfigConstants.PRESET, String.valueOf("default_config.json"));

        /*editor.putString(ConfigConstants.FREQUENCY_ZERO, ConfigConstants.SETTING_FREQUENCY_ZERO_DEFAULT);
        editor.putString(ConfigConstants.BIT_PERIOD, ConfigConstants.SETTING_BIT_PERIOD_DEFAULT);
        editor.putString(ConfigConstants.PAUSE_PERIOD, ConfigConstants.SETTING_PAUSE_PERIOD_DEFAULT);
        editor.putString(ConfigConstants.SPACE_BETWEEN_FREQUENCIES, ConfigConstants.SETTING_SPACE_BETWEEN_FREQUENCIES_DEFAULT);
        editor.putString(ConfigConstants.NUMBER_OF_BYTES, ConfigConstants.SETTING_NUMBER_OF_BYTES_DEFAULT);
        editor.putInt(ConfigConstants.LOUDNESS, ConfigConstants.SETTING_LOUDNESS_DEFAULT);*/
        editor.apply();
        editor.commit();

        etFrequencyZero.setText(String.valueOf(config.getFrequencyZero()));
        etBitperiod.setText(String.valueOf(config.getBitperiod()));
        etPauseperiod.setText(String.valueOf(config.getPauseperiod()));
        etFrequencyspace.setText(String.valueOf(config.getFrequencySpace()));
        etNMaxCharacters.setText(String.valueOf(config.getnMessageBlocks()*2-2));
        lpNumberOfFrequencies.setValueIndex(1);

        String presetsStr = String.format(getString(R.string.settings_preset_title), String.valueOf("default_config.json"));
        prefPresets.setTitle(presetsStr);

        setPreferenceValues();
    }

    private void setPreferenceValues(){
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());

        String frequencyZero = sp.getString(etFrequencyZero.getKey(), ConfigConstants.SETTING_FREQUENCY_ZERO_DEFAULT);
        String prefFrequencyZero = String.format(getString(R.string.settings_frequency0_text), String.valueOf(frequencyZero));
        etFrequencyZero.setTitle(prefFrequencyZero);

        String bitperiod = sp.getString(etBitperiod.getKey(), ConfigConstants.SETTING_BIT_PERIOD_DEFAULT);
        String prefBitperiode = String.format(getString(R.string.settings_bitperiod_text), String.valueOf(bitperiod));
        etBitperiod.setTitle(prefBitperiode);

        String pauseperiod = sp.getString(etPauseperiod.getKey(), ConfigConstants.SETTING_PAUSE_PERIOD_DEFAULT);
        String prefPauseperiod = String.format(getString(R.string.settings_pauseperiod_text), String.valueOf(pauseperiod));
        etPauseperiod.setTitle(prefPauseperiod);

        String frequencyspace = sp.getString(etFrequencyspace.getKey(), ConfigConstants.SETTING_SPACE_BETWEEN_FREQUENCIES_DEFAULT);
        String prefFrequencyspace = String.format(getString(R.string.settings_frequencyspace_text), String.valueOf(frequencyspace));
        etFrequencyspace.setTitle(prefFrequencyspace);

        String nMaxCharacters = sp.getString(etNMaxCharacters.getKey(), ConfigConstants.SETTING_NUMBER_OF_BYTES_DEFAULT);
        String prefNMaxCharacters = String.format(getString(R.string.settings_maxbytes_text), String.valueOf(nMaxCharacters));
        etNMaxCharacters.setTitle(prefNMaxCharacters);

        String numberOfFrequencies = sp.getString(lpNumberOfFrequencies.getKey(), ConfigConstants.SETTING_FREQUENCY_ZERO_DEFAULT);
        String prefNumberOfFrequencies = String.format(getString(R.string.settings_numberoffrequencies_text), String.valueOf(numberOfFrequencies));
        lpNumberOfFrequencies.setTitle(prefNumberOfFrequencies);
        lpNumberOfFrequencies.setValueIndex(checkFrequencyListViewIndex(numberOfFrequencies));

        String loudness = sp.getString(sbprefLoudness.getKey(), ConfigConstants.SETTING_LOUDNESS_DEFAULT);
        String prefloudness = String.format(getString(R.string.settings_loudness_text), String.valueOf(loudness));
        sbprefLoudness.setTitle(prefloudness);//setValue(loudness);

        String presets = sp.getString(prefPresets.getKey(), getString(R.string.preset_undefined));
        String prPresets = String.format(getString(R.string.settings_preset_title), String.valueOf(presets));
        prefPresets.setTitle(prPresets);
    }

    private SoniTalkConfig getDefaultSettings(){
        SoniTalkConfig config = null;
        try {
            config = ConfigFactory.getDefaultConfig(getContext());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ConfigException e) {
            e.printStackTrace();
        }
        return config;
    }

    private void setToConfig(SoniTalkConfig config, String configName){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = preferences.edit();

        editor.putString(ConfigConstants.FREQUENCY_ZERO, String.valueOf(config.getFrequencyZero()));
        editor.putString(ConfigConstants.BIT_PERIOD, String.valueOf(config.getBitperiod()));
        editor.putString(ConfigConstants.PAUSE_PERIOD, String.valueOf(config.getPauseperiod()));
        editor.putString(ConfigConstants.SPACE_BETWEEN_FREQUENCIES, String.valueOf(config.getFrequencySpace()));
        editor.putString(ConfigConstants.NUMBER_OF_BYTES, String.valueOf((config.getnMessageBlocks()*2-2)));
        editor.putString(ConfigConstants.LOUDNESS, ConfigConstants.SETTING_LOUDNESS_DEFAULT);
        editor.putString(ConfigConstants.PRESET, configName);

        editor.apply();
        editor.commit();

        etFrequencyZero.setText(String.valueOf(config.getFrequencyZero()));
        etBitperiod.setText(String.valueOf(config.getBitperiod()));
        etPauseperiod.setText(String.valueOf(config.getPauseperiod()));
        etFrequencyspace.setText(String.valueOf(config.getFrequencySpace()));
        etNMaxCharacters.setText(String.valueOf(config.getnMessageBlocks()*2-2));

        lpNumberOfFrequencies.setValueIndex(checkFrequencyListViewIndex(String.valueOf(config.getnFrequencies())));

        setPreferenceValues();
    }

    private int checkFrequencyListViewIndex(String numberOfFrequencies){
        CharSequence[] helparry = lpNumberOfFrequencies.getEntryValues();

        int listViewFrequencyIndex = 1;
        for(int i=0;i<helparry.length;i++){
            if(helparry[i].equals(numberOfFrequencies)){
                listViewFrequencyIndex = i;
            }
        }
        Log.d("SettingsFragmentIndList", String.valueOf(listViewFrequencyIndex));
        return listViewFrequencyIndex;
    }

    private void setUndefinedPresetName(){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(ConfigConstants.PRESET, getString(R.string.preset_undefined));

        editor.apply();
        editor.commit();
        String prPresets = String.format(getString(R.string.settings_preset_title), getString(R.string.preset_undefined));
        prefPresets.setTitle(prPresets);
    }

    public void showToastNoCharactersEntered(){
        Toast.makeText(getContext(), getString(R.string.show_toast_no_characters_entered), Toast.LENGTH_SHORT).show();
    }

    public void showToastNoNumericCharacterEntered(){
        Toast.makeText(getContext(), getString(R.string.show_toast_no_numeric_character_entered), Toast.LENGTH_SHORT).show();
    }

    public void showToastLowerLimitExceeded(int limit){
        Toast.makeText(getContext(), String.format(getString(R.string.show_toast_lower_limit_exceeded), limit), Toast.LENGTH_SHORT).show();
    }

    public void showToastUpperLimitExceeded(int limit){
        Toast.makeText(getContext(), String.format(getString(R.string.show_toast_higher_limit_exceeded), limit), Toast.LENGTH_SHORT).show();
    }

    private boolean onEditTextPreferenceClick(final EditTextPreference editTextPreference, int settingsText, final int settingLowerLimit, final int settingUpperLimit) {
        EditTextPreference prefEdit = editTextPreference;
        Dialog prefDialog = prefEdit.getDialog();
        String prefBitperiodStr = String.format(getString(settingsText), String.valueOf(prefEdit.getText()));
        prefDialog.setTitle(prefBitperiodStr + " ["+settingLowerLimit+";"+settingUpperLimit+"]");
        editTextPreference.getEditText().setTextColor(Color.BLACK);
        prefDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    String text = editTextPreference.getEditText().getText().toString();
                    if (!"".equals(text) && (Integer.valueOf(text) < settingLowerLimit || Integer.valueOf(text) > settingUpperLimit)) {
                        if (dialog instanceof AlertDialog) {
                            AlertDialog alertDlg = (AlertDialog) dialog;
                            Button btn = alertDlg.getButton(AlertDialog.BUTTON_POSITIVE);
                            btn.setEnabled(false);
                            editTextPreference.getEditText().setTextColor(Color.RED);
                        }
                        return false;
                    } else if("".equals(text)){
                        if (dialog instanceof AlertDialog) {
                            AlertDialog alertDlg = (AlertDialog) dialog;
                            Button btn = alertDlg.getButton(AlertDialog.BUTTON_POSITIVE);
                            btn.setEnabled(false);
                            editTextPreference.getEditText().setTextColor(Color.RED);
                        }
                        return false;
                    }else{
                        if (dialog instanceof AlertDialog) {
                            AlertDialog alertDlg = (AlertDialog) dialog;
                            Button btn = alertDlg.getButton(AlertDialog.BUTTON_POSITIVE);
                            btn.setEnabled(true);
                            editTextPreference.getEditText().setTextColor(Color.BLACK);
                        }
                        return false;
                    }
                }
                return false;
            }
        });
        return true;
    }
}
