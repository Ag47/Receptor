/*
 * Copyright (c) 2011-2013 Madhav Vaidyanathan
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
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;

import java.util.ArrayList;

/**
 * @class ChooseSongActivity
 * The ChooseSongActivity class is a view for choosing a song to play.
 */
public class ChooseSongActivity extends ListActivity {

    static ChooseSongActivity globalActivity;

    ArrayList<FileUri> songlist;

    /**
     * Textbox to filter the songs by name
     */
    EditText filterText;

    /**
     * Task to scan for midi files
     */
    ScanMidiFiles scanner;

    IconArrayAdapter<FileUri> adapter;

    @Override
    public void onCreate(Bundle state) {
        globalActivity = this;
        super.onCreate(state);

        setContentView(R.layout.choose_song);
        setTitle("MidiSheetMusic: Choose Song");


        songlist = (ArrayList<FileUri>) getLastNonConfigurationInstance();
        if (songlist != null) {
            adapter = new IconArrayAdapter<FileUri>(this, android.R.layout.simple_list_item_1, songlist);
            this.setListAdapter(adapter);
        }

    }

    public static void openFile(FileUri file) {
        globalActivity.doOpenFile(file);
    }

    public void doOpenFile(FileUri file) {
        byte[] data = file.getData(this);
        if (data == null || data.length <= 6 || !MidiFile.hasMidiHeader(data)) {
            ChooseSongActivity.showErrorDialog("Error: Unable to open song: " + file.toString(), this);
            return;
        }
        //updateRecentFile(file);
        Intent intent = new Intent(Intent.ACTION_VIEW, file.getUri(), this, SheetMusicActivity.class);
        intent.putExtra(SheetMusicActivity.MidiTitleID, file.toString());
        startActivity(intent);
    }


    /**
     * Show an error dialog with the given message
     */
    public static void showErrorDialog(String message, Activity activity) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setMessage(message);
        builder.setCancelable(false);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

}