package com.OxGames.OxShell;

import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import com.OxGames.OxShell.Data.KeyComboAction;
import com.OxGames.OxShell.Data.SettingsKeeper;
import com.OxGames.OxShell.Helpers.InputHandler;
import com.OxGames.OxShell.Views.HomeView;
import com.OxGames.OxShell.Views.PromptView;
import com.OxGames.OxShell.Views.XMBView;

import java.io.File;
import java.util.Arrays;
import java.util.function.Consumer;

public class HomeActivity extends PagedActivity {
    //private static final String XMB_INPUT = "XMB_INPUT";
    View homeView;
    private Consumer<Boolean> onDynamicInputShown = (onOff) -> homeView.setVisibility(onOff ? View.GONE : View.VISIBLE);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d("HomeActivity", "external-path " + Environment.getExternalStorageDirectory());
        Log.d("HomeActivity", "external-files-path " + getExternalFilesDir(null));
        Log.d("HomeActivity", "cache-path " + getCacheDir());
        Log.d("HomeActivity", "external-cache-path " + getExternalCacheDir());
        Log.d("HomeActivity", "files-path " + getFilesDir());
        File[] extMediaDirs = getExternalMediaDirs();
        for (int i = 0; i < extMediaDirs.length; i++)
            Log.d("HomeActivity", "external-media-path_" + i + ": " + extMediaDirs[i]);
        // Log.d("HomeActivity", ShellCommander.run("mount"));
//        ExplorerBehaviour beh = new ExplorerBehaviour();
//        beh.setDirectory(getFilesDir().getPath());
//        for (File subPath : beh.listContents())
//            Log.d("HomeActivity", "DataContents: " + subPath);

        setContentView(R.layout.activity_home);
        homeView = findViewById(R.id.home_view);
        //refreshXMBInput();
        //InputHandler.setTagEnabled(XMB_INPUT, true);

        //HomeManager.init();
        //Log.d("HomeActivity", "onCreate");
    }

//    public void refreshXMBInput() {
//        InputHandler.clearKeyComboActions(XMB_INPUT);
//        InputHandler.addKeyComboActions(XMB_INPUT, Arrays.stream(SettingsKeeper.getPrimaryInput()).map(combo -> new KeyComboAction(combo, () -> ((XMBView)homeView).affirmativeAction())).toArray(KeyComboAction[]::new));
//        InputHandler.addKeyComboActions(XMB_INPUT, Arrays.stream(SettingsKeeper.getSecondaryInput()).map(combo -> new KeyComboAction(combo, () -> ((XMBView)homeView).secondaryAction())).toArray(KeyComboAction[]::new));
//        InputHandler.addKeyComboActions(XMB_INPUT, Arrays.stream(SettingsKeeper.getCancelInput()).map(combo -> new KeyComboAction(combo, () -> ((XMBView)homeView).cancelAction())).toArray(KeyComboAction[]::new));
//        InputHandler.addKeyComboActions(XMB_INPUT, Arrays.stream(SettingsKeeper.getNavigateUp()).map(combo -> new KeyComboAction(combo, () -> ((XMBView)homeView).selectUpperItem())).toArray(KeyComboAction[]::new));
//        InputHandler.addKeyComboActions(XMB_INPUT, Arrays.stream(SettingsKeeper.getNavigateDown()).map(combo -> new KeyComboAction(combo, () -> ((XMBView)homeView).selectLowerItem())).toArray(KeyComboAction[]::new));
//        InputHandler.addKeyComboActions(XMB_INPUT, Arrays.stream(SettingsKeeper.getNavigateLeft()).map(combo -> new KeyComboAction(combo, () -> ((XMBView)homeView).selectLeftItem())).toArray(KeyComboAction[]::new));
//        InputHandler.addKeyComboActions(XMB_INPUT, Arrays.stream(SettingsKeeper.getNavigateRight()).map(combo -> new KeyComboAction(combo, () -> ((XMBView)homeView).selectRightItem())).toArray(KeyComboAction[]::new));
//    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        //setMarginsFor(R.id.packages_list, R.id.settings_view, R.id.customize_home_view, R.id.assoc_list_view, R.id.selectdirs_view, R.id.shortcuts_view);
//        showAnnoyingDialog();
    }

    @Override
    protected void onResume() {
        super.onResume();
        getDynamicInput().addShownListener(onDynamicInputShown);
        //refreshXMBInput();
        //InputHandler.setTagEnabled(XMB_INPUT, true);
        ((HomeView)homeView).onResume();
        //showAnnoyingDialog();
        //Log.d("HomeActivity", "onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        //InputHandler.setTagEnabled(XMB_INPUT, false);
        ((HomeView)homeView).onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getDynamicInput().removeShownListener(onDynamicInputShown);
        //InputHandler.removeTagFromHistory(XMB_INPUT);
        //InputHandler.clearKeyComboActions(XMB_INPUT);
        //InputHandler.setTagEnabled(XMB_INPUT, false);
        ((HomeView)homeView).onDestroy();
        homeView = null;
        //Log.d("HomeActivity", "onDestroy");
    }

//    private void showAnnoyingDialog() {
//        if (!BuildConfig.GOLD) {
//            Log.i("HomeActivity", "Not running in gold");
//            PromptView prompt = getPrompt();
//            prompt.setCenterOfScreen();
//            prompt.setMessage("Thank you for using Ox Shell, please consider supporting us by purchasing the app from the store");
//            prompt.setMiddleBtn("Got it", () -> { prompt.setShown(false); }, SettingsKeeper.getSuperPrimaryInput());
//            prompt.setShown(true);
//        } else {
//            Log.i("HomeActivity", "Running in gold");
//        }
//    }

//    public void getOverlayPermissionBtn(View view) {
//        // Check if Android M or higher
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            // Show alert dialog to the user saying a separate permission is needed
//            // Launch the settings activity if the user prefers
//            Intent myIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
//            startActivity(myIntent);
//        }
//    }
}