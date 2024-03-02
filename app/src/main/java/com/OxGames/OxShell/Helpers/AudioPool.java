package com.OxGames.OxShell.Helpers;

import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.OxGames.OxShell.Data.DataLocation;
import com.OxGames.OxShell.Data.DataRef;
import com.OxGames.OxShell.OxShellApp;
import com.OxGames.OxShell.Views.DebugView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class AudioPool {
    //private static final int COMPLETE_MILLIS = 265; // Through multiple tests, these values seemed to work well to remove the weird clicks/gaps between audio playback
    //private static final int STOP_MILLIS = 100;
    //private static final int COMPLETE_MILLIS = 340; // for some reason, sometimes when playing a track, the position does not pass duration - aSmallMs. It never reaches the full duration and so my custom complete and loop don't work
    //private static final int STOP_MILLIS = 175;
    private DataRef dataRef;
    private final List<MediaPlayer> beingPrepped;
    private final Queue<MediaPlayer> unusedPlayers;
    private final LinkedList<MPR> playingPlayers;

    private final List<Runnable> completedListeners;
    private final List<Runnable> onLoopListeners;

    private final Handler completionHandler;
    //private Runnable currentRunnable;

    private float volume;
    private boolean looping;

    private class MPR {
        MediaPlayer player;
        Runnable runnable;
        boolean firedCompleted;
        MPR(MediaPlayer player) {
            this.player = player;
        }
    }

    private AudioPool() {
        beingPrepped = new ArrayList<>();
        unusedPlayers = new ArrayDeque<>();
        playingPlayers = new LinkedList<>();

        completedListeners = new ArrayList<>();
        onLoopListeners = new ArrayList<>();

        completionHandler = new Handler(Looper.getMainLooper());

        volume = 1;
    }

    private void setDataRef(DataRef dataRef) {
        this.dataRef = dataRef;
    }
    public DataRef getDataRef() {
        return dataRef;
    }

    public void addOnCompletedListener(Runnable listener) {
        completedListeners.add(listener);
    }
    public void removeOnCompletedListener(Runnable listener) {
        completedListeners.remove(listener);
    }
    public void clearOnCompletedListeners() {
        completedListeners.clear();
    }
    public void addOnLoopListener(Runnable listener) {
        onLoopListeners.add(listener);
    }
    public void removeOnLoopListener(Runnable listener) {
        onLoopListeners.remove(listener);
    }
    public void clearOnLoopListeners() {
        onLoopListeners.clear();
    }

    public boolean isPlayerAvailable() {
        return !unusedPlayers.isEmpty();
    }
    public void getDuration(Consumer<Integer> onValueReceived) {
        actionOnAnyPlayer(player -> onValueReceived.accept(player.getDuration()));
    }
    public void getCurrentPosition(Consumer<Integer> onValueReceived) {
        actionOnPlayingPlayer(player -> onValueReceived.accept(player.getCurrentPosition()));
    }
    private void actionOnAnyPlayer(Consumer<MediaPlayer> action) {
        Runnable achieveAction = () -> {
            if (isPlayerAvailable())
                action.accept(unusedPlayers.peek());
            else if (playingPlayers.size() > 0)
                action.accept(playingPlayers.getLast().player);
        };
        if (!isPlayerAvailable() && playingPlayers.size() <= 0) {
            setPoolSize(getPoolSize() + 5);
            onUnusedPopulated(achieveAction);
        } else
            achieveAction.run();
    }
    private void actionOnPlayingPlayer(Consumer<MediaPlayer> action) {
        Runnable achieveAction = () -> action.accept(playingPlayers.getLast().player);
        if (playingPlayers.size() > 0)
            achieveAction.run();
        else
            onPlayerPlaying(achieveAction);
    }

    public void setVolume(float volume) {
        this.volume = volume;
        float log = linearToLogVolume(volume);
        //Log.d("AudioPool", volume + " -> " + log);
        for (int i = 0; i < playingPlayers.size(); i++) {
            MPR mpr = playingPlayers.get(i);
            if (mpr != null && mpr.player != null)
                mpr.player.setVolume(log, log);
        }
    }
    private static float linearToLogVolume(float linearValue) {
        //AudioManager audioManager = (AudioManager)OxShellApp.getContext().getSystemService(Context.AUDIO_SERVICE);
        //int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float maxVolume = 100;
        return Math.max(0, Math.min(1, (float)(1 - Math.log(maxVolume - (maxVolume * linearValue)) / Math.log(maxVolume))));
    }

    public void playNew(boolean loop) {
        this.looping = loop;
        Runnable play = () -> {
            //Log.d("AudioPool", "Playing new MediaPlayer for " + dataRef.getLoc());
            //MediaPlayer player = unusedPlayers.poll();
            MPR mpr = new MPR(unusedPlayers.poll());
            playingPlayers.add(mpr);

            //final int duration = mpr.player.getDuration();
            //if (duration <= COMPLETE_MILLIS) {
                // if duration of the audio is short, then use the ordinary method of checking completion
                mpr.player.setOnCompletionListener(mp -> {
                    for (Runnable listener : completedListeners)
                        if (listener != null)
                            listener.run();
                    if (playingPlayers.contains(mpr)) {
                        playingPlayers.remove(mpr);
                        unusedPlayers.add(mpr.player);
                    }
                    mpr.player.setOnCompletionListener(null);
                });
                mpr.player.setLooping(AudioPool.this.looping);
            //}

            float log = linearToLogVolume(volume);
            //Log.d("AudioPool", volume + " -> " + log);
            mpr.player.setVolume(log, log);
            mpr.player.start();

            // removing due to inconsistent results
//            if (duration > COMPLETE_MILLIS) {
//                // if the duration of the audio is long enough, then use the better way of checking for completion
//                //final Handler completionHandler = new Handler(Looper.getMainLooper());
//                mpr.player.setLooping(false);
//                mpr.runnable = new Runnable() {
//                    @Override
//                    public void run() {
//                        if (playingPlayers.contains(mpr)) {
//                            int position = mpr.player.getCurrentPosition();
//                            //Log.d("AudioPool", position + " >= " + duration + " - " + COMPLETE_MILLIS);
//                            //Log.d("AudioPool", "isMusicActive: " + OxShellApp.getAudioManager().isMusicActive());
//                            if (position >= (duration - COMPLETE_MILLIS)) {
//                                if (position >= (duration - STOP_MILLIS)) {
//                                    //player.stop(); // can't play again after stop
//                                    Log.d("AudioPool", "Pausing and seeking to start due to completion " + dataRef.getLoc());
//                                    playingPlayers.remove(mpr);
//                                    try {
//                                        if (mpr.player.isPlaying())
//                                            mpr.player.pause();
//                                        mpr.player.seekTo(0);
//                                    } catch(Exception e) {
//                                        Log.e("AudioPool", "Failed to stop on completion: " + e);
//                                    }
//                                    //if (!AudioPool.this.looping) {
//                                        unusedPlayers.add(mpr.player);
////                                        for (Runnable listener : completedListeners)
////                                            if (listener != null)
////                                                listener.run();
//                                    //}
////                                    else {
////                                        for (Runnable listener : onLoopListeners)
////                                            if (listener != null)
////                                                listener.run();
////                                        completionHandler.postDelayed(this, MathHelpers.calculateMillisForFps(60));
////                                    }
//                                } else {
//                                    Log.d("AudioPool", "Reached completion pre-stop of " + dataRef.getLoc());
//                                    if (!AudioPool.this.looping) {//playingPlayers.contains(mpr)) {
//                                        // ready for loop
//                                        if (!mpr.firedCompleted) {
//                                            Log.d("AudioPool", "Firing on complete for " + dataRef.getLoc());
//                                            mpr.firedCompleted = true;
//                                            for (Runnable listener : completedListeners)
//                                                if (listener != null)
//                                                    listener.run();
//                                            //playingPlayers.remove(mpr);
//                                        }
//                                    } else {
//                                        mpr.player.seekTo(0);
//                                        for (Runnable listener : onLoopListeners)
//                                            if (listener != null)
//                                                listener.run();
//                                    }
//                                    completionHandler.postDelayed(this, MathHelpers.calculateMillisForFps(60));
//                                }
//                            } else if (playingPlayers.contains(mpr)) {
//                                Log.d("AudioPool", position + " >= " + duration + " - " + COMPLETE_MILLIS);
//                                completionHandler.postDelayed(this, MathHelpers.calculateMillisForFps(60));
//                            }
//                        }
//                    }
//                };
//                completionHandler.post(mpr.runnable);
//            }
        };

        if (!isPlayerAvailable()) {
            if (beingPrepped.size() <= 0)
                setPoolSize(getPoolSize() + 5);
            onUnusedPopulated(play);
        } else
            play.run();
    }
    private void onUnusedPopulated(Runnable onPrepared) {
        new Thread(() -> {
            try {
                long startTime = SystemClock.uptimeMillis();
                while (!isPlayerAvailable()) {
                    if ((SystemClock.uptimeMillis() - startTime) / 1000f > 10)
                        throw new TimeoutException("Failed to play, no audio prepared within 10s");
                    Thread.sleep(10);
                }
                onPrepared.run();
            } catch (Exception e) {
                Log.e("AudioPool", "Failed to play audio " + dataRef.getLoc() + ": " + e);
            }
        }).start();
    }
    private void onPlayerPlaying(Runnable onPlayed) {
        new Thread(() -> {
            try {
                long startTime = SystemClock.uptimeMillis();
                while (playingPlayers.size() <= 0) {
                    if ((SystemClock.uptimeMillis() - startTime) / 1000f > 10)
                        throw new TimeoutException("Failed to play, no audio prepared within 10s");
                    Thread.sleep(10);
                }
                onPlayed.run();
            } catch (Exception e) {
                Log.e("AudioPool", "Failed to play audio " + dataRef.getLoc() + ": " + e);
            }
        }).start();
    }

    public void setLooping(boolean onOff) {
        looping = onOff;
    }

    public boolean isAnyPlaying() {
        boolean isPlaying = false;
        for (MPR mpr : playingPlayers) {
            if (mpr != null && mpr.player != null && mpr.player.isPlaying()) {
                isPlaying = true;
                break;
            }
        }
        return isPlaying;
    }
    public int getActiveCount() {
        return playingPlayers.size();
    }
    public void pauseActive() {
        //Log.d("AudioPool", "Pausing active for " + dataRef.getLoc());
        for (MPR mpr : playingPlayers)
            mpr.player.pause();
    }
    public void resumeActive() {
        //Log.d("AudioPool", "Resuming active for " + dataRef.getLoc());
        for (MPR mpr : playingPlayers) {
            mpr.player.start();
            completionHandler.post(mpr.runnable);
        }
    }
    public void stopActive() {
        //Log.d("AudioPool", "Stopping active for " + dataRef.getLoc());
        for (int i = 0; i < playingPlayers.size(); i++) {
            MPR mpr = playingPlayers.removeLast();
            if (!mpr.firedCompleted) {
                // only stop if its not already about to stop
                try {
                    if (mpr.player.isPlaying())
                        mpr.player.pause();
                    mpr.player.seekTo(0);
                } catch (Exception e) {
                    Log.e("AudioPool", "Failed to stop current active: " + e);
                }
            }
            unusedPlayers.add(mpr.player);
        }
    }
    public void seekTo(int ms) {
        //Log.d("AudioPool", "Seeking to " + ms + " for " + dataRef.getLoc());
        if (playingPlayers.size() > 0) {
            try {
                playingPlayers.getLast().player.seekTo(ms);
            } catch (Exception e) {
                Log.e("AudioPool", "Failed to seek to " + ms + " ms: " + e);
            }
        } else
            Log.e("AudioPool", "Failed to seek to " + ms + " ms, no active players");
    }

    public int getPoolSize() {
        return beingPrepped.size() + unusedPlayers.size() + playingPlayers.size();
    }
    public void setPoolSize(int size) {
        //Log.d("AudioPool", "Pool size set to " + size + " for " + dataRef.getLoc());
        DebugView.print(dataRef.getLoc().toString(), dataRef.getLoc() + ": " + size, size <= 0 ? 3 : DebugView.TTL_INDEFINITE);
        int diff = size - getPoolSize();
        if (diff > 0) {
            // add media players
            new Thread(() -> {
                try {
                    AssetFileDescriptor afd = null;
                    if (dataRef.getLocType() == DataLocation.asset)
                        afd = OxShellApp.getContext().getAssets().openFd((String)dataRef.getLoc());
                    for (int i = 0; i < diff; i++) {
                        MediaPlayer player = new MediaPlayer();
                        player.setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build());
                        try {
                            if (dataRef.getLocType() == DataLocation.asset)
                                player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                            else if (dataRef.getLocType() == DataLocation.file)
                                player.setDataSource((String)dataRef.getLoc());
                            else if (dataRef.getLocType() == DataLocation.resolverUri)
                                player.setDataSource(OxShellApp.getContext(), (Uri)dataRef.getLoc());
                            else
                                throw new UnsupportedOperationException("Cannot load data type: " + dataRef.getLocType());
                            beingPrepped.add(player);
                            new Thread(() -> {
                                try {
                                    //Log.d("AudioPool", "Preparing player for " + dataRef.getLoc());
                                    player.prepare();
                                    if (beingPrepped.contains(player)) {
                                        //Log.d("AudioPool", "Player prepared, placed into ready for " + dataRef.getLoc());
                                        // if its not in beingPrepped, that means its been 'cancelled'
                                        beingPrepped.remove(player);
                                        unusedPlayers.add(player);
                                    } else {
                                        //Log.d("AudioPool", "Player prepared, cancelled, already expected to be released for " + dataRef.getLoc());
                                        player.release();
                                    }
                                } catch (Exception e) {
                                    Log.e("AudioHelper", "Failed to prepare MediaPlayer: " + e);
                                }
                            }).start();
                        } catch (Exception e) {
                            Log.e("AudioHelper", "Failed to load asset into MediaPlayer: " + e);
                        }
                    }
                    if (afd != null)
                        afd.close();
                } catch (Exception e) {
                    Log.e("AudioHelper", "Failed to read asset: " + e);
                }
            }).start();
        } else if (diff < 0) {
            // remove media players
            int removeCount = Math.abs(diff);
            int removed = 0;
            if (beingPrepped.size() > 0) {
                for (int i = 0; i < Math.min(beingPrepped.size(), removeCount); i++) {
                    int lastIndex = (beingPrepped.size() - 1) - i;
                    MediaPlayer player = beingPrepped.get(lastIndex);
                    player.reset();
                    player.release();
                    beingPrepped.remove(lastIndex);
                    removed++;
                }
            }
            if (removed < removeCount && unusedPlayers.size() > 0) {
                for (int i = 0; i < Math.min(unusedPlayers.size(), removeCount); i++) {
                    MediaPlayer player = unusedPlayers.poll();
                    player.reset();
                    player.release();
                    removed++;
                }
            }
            if (removed < removeCount && playingPlayers.size() > 0) {
                for (int i = 0; i < Math.min(playingPlayers.size(), removeCount); i++) {
                    MPR mpr = playingPlayers.poll();
                    mpr.player.reset();
                    mpr.player.release();
                    removed++;
                }
            }
        }
    }
    public static AudioPool fromAsset(String assetLoc, int poolSize) {
        return from(DataRef.from(assetLoc, DataLocation.asset), poolSize);
    }
    public static AudioPool fromFile(String filePath, int poolSize) {
        return from(DataRef.from(filePath, DataLocation.file), poolSize);
    }
    public static AudioPool fromUri(Uri uri, int poolSize) {
        return from(DataRef.from(uri, DataLocation.resolverUri), poolSize);
    }
    public static AudioPool from(DataRef loc, int poolSize) {
        AudioPool pool = new AudioPool();
        pool.setDataRef(loc);
        pool.setPoolSize(poolSize);
        return pool;
    }
}
