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

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.receptor.camera.CameraSourcePreview;
import com.receptor.camera.GraphicOverlay;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.zip.CRC32;

/**
 * @class SheetMusicActivity
 * <p/>
 * The SheetMusicActivity is the main activity. The main components are:
 * - MidiPlayer : The buttons and speed bar at the top.
 * - Piano : For highlighting the piano notes during playback.
 * - SheetMusic : For highlighting the sheet music notes during playback.
 */
public class SheetMusicActivity extends Activity {

    public static final String MidiTitleID = "MidiTitleID";
    public static final int settingsRequestCode = 1;

    private MidiPlayer player;   /* The play/stop/rewind toolbar */
    private Piano piano;         /* The piano at the top */
    private SheetMusic sheet;    /* The sheet music */
    private LinearLayout layout; /* THe layout */
    private MidiFile midifile;   /* The midi file to play */
    private MidiOptions options; /* The options for sheet music and sound */
    private long midiCRC;      /* CRC of the midi bytes */

    private ImageView drag_image;
    private ViewGroup pageView;
    private float _x;
    private float _y;

    private ImageView play_button;
    private RelativeLayout panel_2D;

    private ImageView switch_camera;
    private RelativeLayout camera_view;

    private static final String TAG = "FaceTracker";

    private CameraSource mCameraSource = null;

    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;

    private static final int RC_HANDLE_GMS = 9001;
    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    /**
     * 0 for stopped / not playing, 1 for playing
     **/
    public static int playing_state = 0;

    /**
     * 1 for 2D panel, 2 for face detection
     **/
    private int emotion_mode = 1;


    /**
     * Create this SheetMusicActivity.
     * The Intent should have two parameters:
     * - data: The uri of the midi file to open.
     * - MidiTitleID: The title of the song (String)
     */
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_full_player);

        ClefSymbol.LoadImages(this);
        TimeSigSymbol.LoadImages(this);
        MidiPlayer.LoadImages(this);

        // Parse the MidiFile from the raw bytes
        Uri uri = this.getIntent().getData();
        String title = this.getIntent().getStringExtra(MidiTitleID);
        if (title == null) {
            title = uri.getLastPathSegment();
        }
        FileUri file = new FileUri(uri, title);
        this.setTitle("MidiSheetMusic: " + title);
        byte[] data;
        try {
            data = file.getData(this);
            midifile = new MidiFile(data, title);
        } catch (MidiFileException e) {
            this.finish();
            return;
        }

        // Initialize the settings (MidiOptions).
        // If previous settings have been saved, used those
        options = new MidiOptions(midifile);
        CRC32 crc = new CRC32();
        crc.update(data);
        midiCRC = crc.getValue();
        SharedPreferences settings = getPreferences(0);
        options.scrollVert = settings.getBoolean("scrollVert", false);
        options.shade1Color = settings.getInt("shade1Color", options.shade1Color);
        options.shade2Color = settings.getInt("shade2Color", options.shade2Color);
        options.showPiano = settings.getBoolean("showPiano", true);
        String json = settings.getString("" + midiCRC, null);
        MidiOptions savedOptions = MidiOptions.fromJson(json);
        if (savedOptions != null) {
            options.merge(savedOptions);
        }
//        createView();
//        createSheetMusic(options);


        pageView = (ViewGroup) findViewById(R.id.pageView);
        drag_image = (ImageView) findViewById(R.id.drag_button);
        panel_2D = (RelativeLayout) findViewById(R.id.panel2D);


        drag_image.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        _x = view.getX() - event.getRawX();
                        _y = view.getY() - event.getRawY();
                        break;
                    case MotionEvent.ACTION_MOVE:

                        if (event.getRawX() + _x > 0 && event.getRawY() + _y > 0 && event.getRawX() + _x + drag_image.getWidth() < getScreenWidth() && event.getRawY() + _y + drag_image.getHeight() < panel_2D.getHeight()) {
                            view.animate().x(event.getRawX() + _x).y(event.getRawY() + _y).setDuration(0).start();
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        int x;
                        int y;
                        x = (view.getX() < 647 / 2) ? 0 : 1;

                        // TODO fine tune formula
                        y = (int) (-107.0 / 718.0 * view.getY() + 127.0);

                        player.update(x, y);

                        break;
                    default:
                        return false;
                }
                return true;
            }
        });

        player = new MidiPlayer(this);
        Log.wtf("playing state original: ", Integer.toString(player.playstate));
        player.SetMidiFile(midifile, options, sheet);

        play_button = (ImageView) findViewById(R.id.play_button);
        play_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (playing_state == 1) {
                    play_button.setImageDrawable(getResources().getDrawable(R.drawable.ic_play_arrow_white_48dp));
                    playing_state = 0;
//                    player.Pause();
                    player.DoStop();
                    Log.wtf("playing stateAA: ", Integer.toString(player.playstate));
                } else {
                    play_button.setImageDrawable(getResources().getDrawable(R.drawable.ic_stop_white_48dp));
                    Log.wtf("playing stateSDJKFHB: ", Integer.toString(player.playstate));
                    playing_state = 1;
                    player.Play();
                    Log.wtf("playing state: ", Integer.toString(player.playstate));
                }
            }
        });

        camera_view = (RelativeLayout) findViewById(R.id.camera_view);
        switch_camera = (ImageView) findViewById(R.id.switch_face);
        switch_camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                mPreview = (CameraSourcePreview) findViewById(R.id.preview);
                mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);

                // Check for the camera permission before accessing the camera.  If the
                // permission is not granted yet, request permission.
                int rc = ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA);

                if (emotion_mode == 1) {
                    camera_view.setVisibility(View.VISIBLE);
                    panel_2D.setVisibility(View.GONE);
                    if (rc == PackageManager.PERMISSION_GRANTED) {
                        createCameraSource();
                        startCameraSource();
                        switch_camera.setImageDrawable(getResources().getDrawable(R.drawable.ic_aspect_ratio_white_48dp));
                        emotion_mode = 2;
                    } else {
                        requestCameraPermission();
                    }
                } else{
                    panel_2D.setVisibility(View.VISIBLE);
                    mPreview.stop();
                    switch_camera.setImageDrawable(getResources().getDrawable(R.drawable.ic_face_white_48dp));
                    mCameraSource.release();
                    emotion_mode = 1;
                    camera_view.setVisibility(View.GONE);
                }
            }
        });
    }

    /* Create the MidiPlayer and Piano views */
    void createView() {
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        player = new MidiPlayer(this);
        piano = new Piano(this);
        layout.addView(player);
        layout.addView(piano);
        setContentView(layout);
        player.SetPiano(piano);
        layout.requestLayout();
    }

    /**
     * Create the SheetMusic view with the given options
     */
    private void
    createSheetMusic(MidiOptions options) {
        if (sheet != null) {
            layout.removeView(sheet);
        }
        if (!options.showPiano) {
            piano.setVisibility(View.GONE);
        } else {
            piano.setVisibility(View.VISIBLE);
        }
        sheet = new SheetMusic(this);
        sheet.init(midifile, options);
        sheet.setPlayer(player);
        layout.addView(sheet);
        piano.SetMidiFile(midifile, options, player);
        piano.SetShadeColors(options.shade1Color, options.shade2Color);
        player.SetMidiFile(midifile, options, sheet);
        layout.requestLayout();
        sheet.callOnDraw();
    }


    /**
     * Always display this activity in landscape mode.
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    /**
     * When the menu button is pressed, initialize the menus.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (player != null) {
            player.Pause();
        }
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.sheet_menu, menu);
        return true;
    }

    /**
     * Callback when a menu item is selected.
     * - Choose Song : Choose a new song
     * - Song Settings : Adjust the sheet music and sound options
     * - Save As Images: Save the sheet music as PNG images
     * - Help : Display the HTML help screen
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.choose_song:
                chooseSong();
                return true;
            case R.id.song_settings:
                changeSettings();
                return true;
            case R.id.save_images:
                showSaveImagesDialog();
                return true;
            case R.id.help:
                showHelp();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * To choose a new song, simply finish this activity.
     * The previous activity is always the ChooseSongActivity.
     */
    private void chooseSong() {
        this.finish();
    }

    /**
     * To change the sheet music options, start the SettingsActivity.
     * Pass the current MidiOptions as a parameter to the Intent.
     * Also pass the 'default' MidiOptions as a parameter to the Intent.
     * When the SettingsActivity has finished, the onActivityResult()
     * method will be called.
     */
    private void changeSettings() {
        MidiOptions defaultOptions = new MidiOptions(midifile);
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra(SettingsActivity.settingsID, options);
        intent.putExtra(SettingsActivity.defaultSettingsID, defaultOptions);
        startActivityForResult(intent, settingsRequestCode);
    }


    /* Show the "Save As Images" dialog */
    private void showSaveImagesDialog() {
        LayoutInflater inflator = LayoutInflater.from(this);
        final View dialogView = inflator.inflate(R.layout.save_images_dialog, null);
        final EditText filenameView = (EditText) dialogView.findViewById(R.id.save_images_filename);
        filenameView.setText(midifile.getFileName().replace("_", " "));
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.save_images_str);
        builder.setView(dialogView);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface builder, int whichButton) {
                saveAsImages(filenameView.getText().toString());
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface builder, int whichButton) {
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }


    /* Save the current sheet music as PNG images. */
    private void saveAsImages(String name) {
        String filename = name;
        try {
            filename = URLEncoder.encode(name, "utf-8");
        } catch (UnsupportedEncodingException e) {
        }
        if (!options.scrollVert) {
            options.scrollVert = true;
//            createSheetMusic(options);
        }
        try {
            int numpages = sheet.GetTotalPages();
            for (int page = 1; page <= numpages; page++) {
                Bitmap image = Bitmap.createBitmap(SheetMusic.PageWidth + 40, SheetMusic.PageHeight + 40, Bitmap.Config.ARGB_8888);
                Canvas imageCanvas = new Canvas(image);
                sheet.DrawPage(imageCanvas, page);
                File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES + "/MidiSheetMusic");
                File file = new File(path, "" + filename + page + ".png");
                path.mkdirs();
                OutputStream stream = new FileOutputStream(file);
                image.compress(Bitmap.CompressFormat.PNG, 0, stream);
                image = null;
                stream.close();

                // Inform the media scanner about the file
                MediaScannerConnection.scanFile(this, new String[]{file.toString()}, null, null);
            }
        } catch (IOException e) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Error saving image to file " + Environment.DIRECTORY_PICTURES + "/MidiSheetMusic/" + filename + ".png");
            builder.setCancelable(false);
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                }
            });
            AlertDialog alert = builder.create();
            alert.show();
        } catch (NullPointerException e) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Ran out of memory while saving image to file " + Environment.DIRECTORY_PICTURES + "/MidiSheetMusic/" + filename + ".png");
            builder.setCancelable(false);
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                }
            });
            AlertDialog alert = builder.create();
            alert.show();
        }
    }


    /**
     * Show the HTML help screen.
     */
    private void showHelp() {
        Intent intent = new Intent(this, HelpActivity.class);
        startActivity(intent);
    }

    /**
     * This is the callback when the SettingsActivity is finished.
     * Get the modified MidiOptions (passed as a parameter in the Intent).
     * Save the MidiOptions.  The key is the CRC checksum of the midi data,
     * and the value is a JSON dump of the MidiOptions.
     * Finally, re-create the SheetMusic View with the new options.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode != settingsRequestCode) {
            return;
        }
        options = (MidiOptions)
                intent.getSerializableExtra(SettingsActivity.settingsID);

        // Check whether the default instruments have changed.
        for (int i = 0; i < options.instruments.length; i++) {
            if (options.instruments[i] !=
                    midifile.getTracks().get(i).getInstrument()) {
                options.useDefaultInstruments = false;
            }
        }
        // Save the options. 
        SharedPreferences.Editor editor = getPreferences(0).edit();
        editor.putBoolean("scrollVert", options.scrollVert);
        editor.putInt("shade1Color", options.shade1Color);
        editor.putInt("shade2Color", options.shade2Color);
        editor.putBoolean("showPiano", options.showPiano);
        String json = options.toJson();
        if (json != null) {
            editor.putString("" + midiCRC, json);
        }
        editor.commit();

        // Recreate the sheet music with the new options
//        createSheetMusic(options);
    }

    /**
     * When this activity resumes, redraw all the views
     */
    @Override
    protected void onResume() {
        super.onResume();
        startCameraSource();
//        layout.requestLayout();
//        player.invalidate();
//        piano.invalidate();
//        if (sheet != null) {
//            sheet.invalidate();
//        }
//        layout.requestLayout();
    }

    /**
     * When this activity pauses, stop the music
     */
    @Override
    protected void onPause() {
        if (player != null) {
            player.Pause();
            player.DoStop();
        }
        if (mPreview != null)
            mPreview.stop();

        super.onPause();
    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraSource != null) {
            mCameraSource.release();
        }
    }

    public static int getScreenWidth() {
        return Resources.getSystem().getDisplayMetrics().widthPixels;
    }

    public static int getScreenHeight() {
        return Resources.getSystem().getDisplayMetrics().heightPixels;
    }

    /**
     * Handles the requesting of the camera permission.  This includes
     * showing a "Snackbar" message of why the permission is needed then
     * sending the request.
     */
    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions,
                        RC_HANDLE_CAMERA_PERM);
            }
        };

        Snackbar.make(mGraphicOverlay, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     */
    private void createCameraSource() {

        Context context = getApplicationContext();
        FaceDetector detector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .build();

        detector.setProcessor(
                new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory())
                        .build());

        if (!detector.isOperational()) {
            // Note: The first time that an app using face API is installed on a device, GMS will
            // download a native library to the device in order to do detection.  Usually this
            // completes before the app is run for the first time.  But if that download has not yet
            // completed, then the above call will not detect any faces.
            //
            // isOperational() can be used to check if the required native library is currently
            // available.  The detector will automatically become operational once the library
            // download completes on device.
            Log.w(TAG, "Face detector dependencies are not yet available.");
        }

        mCameraSource = new CameraSource.Builder(context, detector)
                .setRequestedPreviewSize(640, 480)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(30.0f)
                .build();
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // we have permission, so create the camerasource
            createCameraSource();
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Face Tracker sample")
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(R.string.ok, listener)
                .show();
    }

    //==============================================================================================
    // Camera Source Preview
    //==============================================================================================

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {

        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    //==============================================================================================
    // Graphic Face Tracker
    //==============================================================================================

    /**
     * Factory for creating a face tracker to be associated with a new face.  The multiprocessor
     * uses this factory to create face trackers as needed -- one for each individual.
     */
    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new GraphicFaceTracker(mGraphicOverlay);
        }
    }

    /**
     * Face tracker for each detected individual. This maintains a face graphic within the app's
     * associated face overlay.
     */
    private class GraphicFaceTracker extends Tracker<Face> {
        private GraphicOverlay mOverlay;
        private FaceGraphic mFaceGraphic;

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay, player);

        }

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        @Override
        public void onNewItem(int faceId, Face item) {
            mFaceGraphic.setId(faceId);
        }

        /**
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            mOverlay.remove(mFaceGraphic);
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {
            mOverlay.remove(mFaceGraphic);
        }
    }
}