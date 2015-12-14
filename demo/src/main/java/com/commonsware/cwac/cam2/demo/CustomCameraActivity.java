/***
  Copyright (c) 2013 CommonsWare, LLC

  Licensed under the Apache License, Version 2.0 (the "License"); you may
  not use this file except in compliance with the License. You may obtain
  a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package com.commonsware.cwac.cam2.demo;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import com.commonsware.cwac.cam2.AbstractCameraActivity;
import com.commonsware.cwac.cam2.CameraController;
import com.commonsware.cwac.cam2.CameraEngine;
import com.commonsware.cwac.cam2.CameraFragment;
import com.commonsware.cwac.cam2.CameraSelectionCriteria;
import com.commonsware.cwac.cam2.ConfirmationFragment;
import com.commonsware.cwac.cam2.FlashMode;
import com.commonsware.cwac.cam2.ImageContext;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import de.greenrobot.event.EventBus;

/**
 * Stock activity for taking pictures. Supports the same
 * protocol, in terms of extras and return data, as does
 * ACTION_IMAGE_CAPTURE.
 */
public class CustomCameraActivity extends Activity
        implements ConfirmationFragment.Contract {

  /**
   * Extra name for indicating whether a confirmation screen
   * should appear after taking the picture, or whether taking
   * the picture should immediately return said picture. Defaults
   * to true, meaning that the user should confirm the picture.
   */
  public static final String EXTRA_CONFIRM="cwac_cam2_confirm";

  /**
   * Extra name for whether a preview frame should be saved
   * to getExternalCacheDir() at the point when a picture
   * is taken. This is for debugging purposes, to compare
   * the preview frame with both the taken picture and what
   * you see on the activity's preview. It is very unlikely
   * that you will want this enabled in a production app.
   * Defaults to false.
   */
  public static final String EXTRA_DEBUG_SAVE_PREVIEW_FRAME=
          "cwac_cam2_save_preview";

  private static final String TAG_CONFIRM=ConfirmationFragment.class.getCanonicalName();
  private static final String[] PERMS={Manifest.permission.CAMERA};
  private ConfirmationFragment confirmFrag;
  private boolean needsThumbnail=false;




  @SuppressWarnings("unused")
  public void onEventMainThread(CameraEngine.PictureTakenEvent event) {
    if (getIntent().getBooleanExtra(EXTRA_CONFIRM, true)) {
      confirmFrag.setImage(event.getImageContext());

      getFragmentManager()
              .beginTransaction()
              .hide(cameraFrag)
              .show(confirmFrag)
              .commit();
    }
    else {
      completeRequest(event.getImageContext(), true);
    }
  }

  @Override
  public void retakePicture() {
    getFragmentManager()
            .beginTransaction()
            .hide(confirmFrag)
            .show(cameraFrag)
            .commit();
  }

  @Override
  public void completeRequest(ImageContext imageContext, boolean isOK) {
    if (!isOK) {
      setResult(RESULT_CANCELED);
      finish();
    }
    else {
      if (needsThumbnail) {
        final Intent result=new Intent();

        result.putExtra("data", imageContext.buildResultThumbnail());

        findViewById(android.R.id.content).post(new Runnable() {
          @Override
          public void run() {
            setResult(RESULT_OK, result);
            removeFragments();
          }
        });
      }
      else {
        findViewById(android.R.id.content).post(new Runnable() {
          @Override
          public void run() {
            setResult(RESULT_OK, new Intent().setData(getOutputUri()));
            removeFragments();
          }
        });
      }
    }
  }

  protected String[] getNeededPermissions() {
    return(PERMS);
  }

  protected boolean needsOverlay() {
    return(true);
  }


  protected boolean needsActionBar() {
    return(true);
  }


  protected boolean isVideo() {
    return(false);
  }


  protected void configEngine(CameraEngine engine) {
    if (getIntent()
            .getBooleanExtra(EXTRA_DEBUG_SAVE_PREVIEW_FRAME, false)) {
      engine
              .setDebugSavePreviewFile(new File(getExternalCacheDir(),
                      "cam2-preview.jpg"));
    }
  }


  protected CustomCameraFragment buildFragment() {
    return(CustomCameraFragment.newPictureInstance(getOutputUri(),
            getIntent().getBooleanExtra(EXTRA_UPDATE_MEDIA_STORE, false)));
  }

  private void removeFragments() {
    getFragmentManager()
            .beginTransaction()
            .remove(confirmFrag)
            .remove(cameraFrag)
            .commit();
  }



  /**
   * Extra name for indicating what facing rule for the
   * camera you wish to use. The value should be a
   * CameraSelectionCriteria.Facing instance.
   */
  public static final String EXTRA_FACING="cwac_cam2_facing";

  /**
   * Extra name for indicating that the requested facing
   * must be an exact match, without gracefully degrading to
   * whatever camera happens to be available. If set to true,
   * requests to take a picture, for which the desired camera
   * is not available, will be cancelled. Defaults to false.
   */
  public static final String EXTRA_FACING_EXACT_MATCH=
          "cwac_cam2_facing_exact_match";

  /**
   * Extra name for indicating whether extra diagnostic
   * information should be reported, particularly for errors.
   * Default is false.
   */
  public static final String EXTRA_DEBUG_ENABLED="cwac_cam2_debug";

  /**
   * Extra name for indicating if MediaStore should be updated
   * to reflect a newly-taken picture. Only relevant if
   * a file:// Uri is used. Default to false.
   */
  public static final String EXTRA_UPDATE_MEDIA_STORE=
          "cwac_cam2_update_media_store";

  /**
   * If set to true, forces the use of the ClassicCameraEngine
   * on Android 5.0+ devices. Has no net effect on Android 4.x
   * devices. Defaults to false.
   */
  public static final String EXTRA_FORCE_CLASSIC="cwac_cam2_force_classic";

  /**
   * If set to true, horizontally flips or mirrors the preview.
   * Does not change the picture or video output. Used mostly for FFC,
   * though will be honored for any camera. Defaults to false.
   */
  public static final String EXTRA_MIRROR_PREVIEW="cwac_cam2_mirror_preview";

  /**
   * Extra name for focus mode to apply. Value should be one of the
   * AbstractCameraActivity.FocusMode enum values. Default is CONTINUOUS.
   * If the desired focus mode is not available, the device default
   * focus mode is used.
   */
  public static final String EXTRA_FOCUS_MODE="cwac_cam2_focus_mode";

  protected static final String TAG_CAMERA=CameraFragment.class.getCanonicalName();
  private static final int REQUEST_PERMS=13401;
  protected CustomCameraFragment cameraFrag;

  /**
   * Standard lifecycle method, serving as the main entry
   * point of the activity.
   *
   * @param savedInstanceState the state of a previous instance
   */
  @TargetApi(23)
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    hideActionBar();
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    init();
  }

  public void hideActionBar()
  {
    if (needsOverlay()) {
      getWindow().requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);

      // the following is nasty stuff to get rid of the action
      // bar drop shadow, which still exists on some devices
      // despite going into overlay mode (Samsung Galaxy S3, I'm
      // looking at you)

      if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP) {
        ActionBar ab=getActionBar();

        if (ab!=null) {
          getActionBar().setElevation(0);
        }
      }
      else {
        View v=((ViewGroup)getWindow().getDecorView()).getChildAt(0);

        if (v!=null) {
          v.setWillNotDraw(true);
        }
      }

    }
    else if (!needsActionBar()) {
      ActionBar ab=getActionBar();

      if (ab!=null) {
        ab.hide();
      }
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode,
                                         String[] permissions,
                                         int[] grantResults) {
    String[] perms=netPermissions(getNeededPermissions());

    if (perms.length==0) {
      init();
    }
    else {
      setResult(RESULT_CANCELED);
      finish();
    }
  }

  /**
   * Standard lifecycle method, for when the fragment moves into
   * the started state. Passed along to the CameraController.
   */
  @Override
  public void onStart() {
    super.onStart();

    EventBus.getDefault().register(this);
  }

  /**
   * Standard lifecycle method, for when the fragment moves into
   * the stopped state. Passed along to the CameraController.
   */
  @Override
  public void onStop() {
    EventBus.getDefault().unregister(this);

    super.onStop();
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    if (keyCode==KeyEvent.KEYCODE_CAMERA) {
      //cameraFrag.performCameraAction();

      return(true);
    }

    return(super.onKeyUp(keyCode, event));
  }

  @SuppressWarnings("unused")
  public void onEventMainThread(CameraController.NoSuchCameraEvent event) {
    finish();
  }

  @SuppressWarnings("unused")
  public void onEventMainThread(CameraController.ControllerDestroyedEvent event) {
    finish();
  }

  protected Uri getOutputUri() {
    Uri output=null;

    if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.JELLY_BEAN_MR1) {
      ClipData clipData=getIntent().getClipData();

      if (clipData!=null && clipData.getItemCount() > 0) {
        output=clipData.getItemAt(0).getUri();
      }
    }

    if (output==null) {
      output=getIntent().getParcelableExtra(MediaStore.EXTRA_OUTPUT);
    }

    return(output);
  }

  protected void init() {
    cameraFrag=(CustomCameraFragment)getFragmentManager().findFragmentByTag(TAG_CAMERA);

    if (cameraFrag==null) {
      cameraFrag=buildFragment();

      AbstractCameraActivity.FocusMode focusMode= AbstractCameraActivity.FocusMode.OFF;

      List<FlashMode> flashModes=new ArrayList<FlashMode>();
      flashModes.add(FlashMode.AUTO);

      CameraController ctrl=new CameraController(focusMode,flashModes, false);

      cameraFrag.setController(ctrl);
      cameraFrag
              .setMirrorPreview(getIntent()
                      .getBooleanExtra(EXTRA_MIRROR_PREVIEW, false));

      AbstractCameraActivity.Facing facing=
              (AbstractCameraActivity.Facing)getIntent().getSerializableExtra(EXTRA_FACING);

      if (facing==null) {
        facing= AbstractCameraActivity.Facing.BACK;
      }

      boolean match=getIntent()
              .getBooleanExtra(EXTRA_FACING_EXACT_MATCH, false);
      CameraSelectionCriteria criteria=
              new CameraSelectionCriteria.Builder()
                      .facing(facing)
                      .facingExactMatch(match)
                      .build();

      // TODO Aymen : change forceClassic of being always true
      ctrl.setEngine(CameraEngine.buildInstance(this, true), criteria);
      ctrl.getEngine().setDebug(getIntent().getBooleanExtra(EXTRA_DEBUG_ENABLED, false));
      configEngine(ctrl.getEngine());

      getFragmentManager()
              .beginTransaction()
              .add(R.id.camera_container, cameraFrag, TAG_CAMERA)
              .commit();
    }

    confirmFrag=(ConfirmationFragment)getFragmentManager().findFragmentByTag(TAG_CONFIRM);

    Uri output=getOutputUri();

    needsThumbnail=(output==null);

    if (confirmFrag==null) {
      confirmFrag= ConfirmationFragment.newInstance();
      getFragmentManager()
              .beginTransaction()
              .add(R.id.document_container, confirmFrag, TAG_CONFIRM)
              .commit();
    }

    if (!cameraFrag.isVisible() && !confirmFrag.isVisible()) {
      getFragmentManager()
              .beginTransaction()
              .hide(confirmFrag)
              .show(cameraFrag)
              .commit();
    }
  }

  @TargetApi(23)
  private boolean hasPermission(String perm) {
    if (useRuntimePermissions()) {
      return(checkSelfPermission(perm)== PackageManager.PERMISSION_GRANTED);
    }

    return(true);
  }

  private boolean useRuntimePermissions() {
    return(Build.VERSION.SDK_INT>Build.VERSION_CODES.LOLLIPOP_MR1);
  }

  private String[] netPermissions(String[] wanted) {
    ArrayList<String> result=new ArrayList<String>();

    for (String perm : wanted) {
      if (!hasPermission(perm)) {
        result.add(perm);
      }
    }

    return(result.toArray(new String[result.size()]));
  }

}

