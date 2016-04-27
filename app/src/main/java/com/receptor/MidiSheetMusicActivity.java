/*
 * Copyright (c) 2011-2012 Madhav Vaidyanathan
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 */

package com.receptor;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;

import java.io.File;

/**
 * @class MidiSheetMusicActivity
 * This is the launch activity for MidiSheetMusic.
 * It simply displays the splash screen, and a button to choose a song.
 */

public class MidiSheetMusicActivity extends Activity {

    SharedPreferences user;
    public static float userPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        user = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        userPref = user.getFloat("happiness",-1);

        loadImages();
        setContentView(R.layout.main);
        Button button = (Button) findViewById(R.id.choose_song);
        button.setOnClickListener(
                new View.OnClickListener() {
                    public void onClick(View v) {
                        if (userPref != -1)
                            chooseSong();
                        else
                            captureImage();
                    }
                }
        );
    }

    /**
     * Start the ChooseSongActivity when the "Choose Song" button is clicked
     */
    private void chooseSong() {
        Intent intent = new Intent(this, AllSongsActivity.class);
        startActivity(intent);
    }

    /**
     * Load all the resource images
     */
    private void loadImages() {
        ClefSymbol.LoadImages(this);
        TimeSigSymbol.LoadImages(this);
        MidiPlayer.LoadImages(this);
    }

    /**
     * Always use landscape mode for this activity.
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private void captureImage() {
        Intent intent = new Intent(this, CaptureUser.class);
        startActivity(intent);
    }

}