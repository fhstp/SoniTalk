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
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class BaseActivity extends AppCompatActivity {

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // action with ID action_refresh was selected
            case R.id.help:
                openHelp();
                break;
            case R.id.settings:
                openSettings();
                break;
            // action with ID action_settings was selected
            case R.id.open_about_us:
                openAboutUs();
                break;
            case R.id.open_github:
                openGithub();
                break;
            case R.id.open_privacy_policy:
                openPrivacyPolicy();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }

        return true;
    }

    public void openSettings(){
        Intent myIntent = new Intent(this.getApplicationContext(), SettingsActivity.class);
        startActivityForResult(myIntent, 0);
    }

    private void openPrivacyPolicy() {
        Intent myIntent = new Intent(this.getApplicationContext(), PrivacyPolicyActivity.class);
        startActivityForResult(myIntent, 0);
    }

    public void openHelp(){
        String instructionsText = String.format(getApplicationContext().getString(R.string.instructions_text), getApplicationContext().getString(R.string.detection_crc_incorrect));
        new AlertDialog.Builder(this).setTitle(R.string.instructions_title).setMessage(instructionsText).setPositiveButton("OK", null).show();
    }

    public void openGithub(){
        /*Uri uri = Uri.parse("https://sonitalk.fhstp.ac.at/.../sonitalk_user_doc.pdf");
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(intent);*/
    }

    public void openAboutUs(){
        Intent myIntent = new Intent(this.getApplicationContext(), AboutUsActivity.class);
        startActivityForResult(myIntent, 0);
    }
}
