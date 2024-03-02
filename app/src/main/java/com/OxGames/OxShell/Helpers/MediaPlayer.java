package com.OxGames.OxShell.Helpers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.SurfaceView;

import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.OxGames.OxShell.BuildConfig;
import com.OxGames.OxShell.Data.DataLocation;
import com.OxGames.OxShell.Data.DataRef;
import com.OxGames.OxShell.Data.Metadata;
import com.OxGames.OxShell.Data.SettingsKeeper;
import com.OxGames.OxShell.MediaPlayerActivity;
import com.OxGames.OxShell.OxShellApp;
import com.OxGames.OxShell.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class MediaPlayer extends MediaSessionService {
    private static final String DEBUG_TAG = "MediaPlayer";
    public static final String PREV_INTENT = "ACTION_SKIP_TO_PREVIOUS";
    public static final String NEXT_INTENT = "ACTION_SKIP_TO_NEXT";
    public static final String PLAY_INTENT = "ACTION_PLAY";
    public static final String PAUSE_INTENT = "ACTION_PAUSE";
    public static final String STOP_INTENT = "ACTION_STOP";

    private static DataRef currentTrackDataRef;
    private static Metadata currentTrackData;
    private static int trackIndex = 0;
    private static MediaSessionCompat session = null;
    private static DataRef[] refs = null;
    private static Notification notification = null;

    private static final Player.Listener exoListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            Player.Listener.super.onPlaybackStateChanged(playbackState);
            Log.d(DEBUG_TAG, "Exo playback state changed: " + playbackState);
            //setTrackIndex(exo.getCurrentMediaItemIndex());
            //trackIndex = exo.getCurrentMediaItemIndex();
            refreshMetadata();
            refreshNotificationAndSession(exo.isPlaying());
            fireIsPlayingEvent(exo.isPlaying());
        }
        @Override
        public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            Player.Listener.super.onMediaItemTransition(mediaItem, reason);
            Log.d(DEBUG_TAG, "Exo media item transitioned: " + reason);
            if (exo.getPlaybackState() == Player.STATE_READY)
                setTrackIndex(exo.getCurrentMediaItemIndex());
            //trackIndex = exo.getCurrentMediaItemIndex();
            refreshMetadata();
            refreshNotificationAndSession(exo.isPlaying());
            fireMediaItemChangedEvent(exo.getCurrentMediaItemIndex());
        }
    };
    private static AudioFocusRequest audioFocusRequest = null;

    private static final int NOTIFICATION_ID = 12345;
    private static final String CHANNEL_ID = "54321";
    private static ExoPlayer exo = null;
    private static MediaPlayer instance = null;
    private static boolean startingService = false;
    private static Handler exoWaitHandler;

    private static final List<Consumer<Boolean>> isPlayingToggledListeners = new ArrayList<>();
    private static final List<Consumer<Integer>> mediaItemChangedListeners = new ArrayList<>();
    private static final List<Consumer<Long>> seekEventListeners = new ArrayList<>();
    private static final List<Runnable> mediaPlayerPreparingListeners = new ArrayList<>();

    public static void addIsPlayingListener(Consumer<Boolean> isPlayingToggledListener) {
        isPlayingToggledListeners.add(isPlayingToggledListener);
    }
    public static void removeIsPlayingListener(Consumer<Boolean> isPlayingToggledListener) {
        isPlayingToggledListeners.remove(isPlayingToggledListener);
    }
    public static void clearIsPlayingListeners() {
        isPlayingToggledListeners.clear();
    }
    private static void fireIsPlayingEvent(boolean value) {
        for (Consumer<Boolean> isPlayingToggledListener : isPlayingToggledListeners)
            isPlayingToggledListener.accept(value);
    }
    public static void addMediaItemChangedListener(Consumer<Integer> mediaItemChangedListener) {
        mediaItemChangedListeners.add(mediaItemChangedListener);
    }
    public static void removeMediaItemChangedListener(Consumer<Integer> mediaItemChangedListener) {
        mediaItemChangedListeners.remove(mediaItemChangedListener);
    }
    public static void clearMediaItemChangedListeners() {
        mediaItemChangedListeners.clear();
    }
    private static void fireMediaItemChangedEvent(int value) {
        for (Consumer<Integer> mediaItemChangedListener : mediaItemChangedListeners)
            mediaItemChangedListener.accept(value);
    }
    public static void addSeekEventListener(Consumer<Long> seekEventListener) {
        seekEventListeners.add(seekEventListener);
    }
    public static void removeSeekEventListener(Consumer<Long> seekEventListener) {
        seekEventListeners.remove(seekEventListener);
    }
    public static void clearSeekEventListeners() {
        seekEventListeners.clear();
    }
    private static void fireSeekEventEvent(long value) {
        for (Consumer<Long> seekEventListener : seekEventListeners)
            seekEventListener.accept(value);
    }
    public static void addMediaPlayerPreparingListener(Runnable mediaPlayerPreparingListener) {
        mediaPlayerPreparingListeners.add(mediaPlayerPreparingListener);
    }
    public static void removeMediaPlayerPreparingListener(Runnable mediaPlayerPreparingListener) {
        mediaPlayerPreparingListeners.remove(mediaPlayerPreparingListener);
    }
    public static void clearMediaPlayerPreparingListeners() {
        mediaPlayerPreparingListeners.clear();
    }
    private static void fireMediaPlayerPreparingEvent(int value) {
        for (Runnable mediaPlayerPreparingListener : mediaPlayerPreparingListeners)
            mediaPlayerPreparingListener.run();
    }

    private static PowerManager.WakeLock wakeLock;
    private static final String WAKE_LOG_TAG = BuildConfig.APPLICATION_ID + ":WakeLock";

    @Override
    public void onCreate() {
        Log.d(DEBUG_TAG, "onCreate");
        super.onCreate();
        instance = this;
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOG_TAG);
        refreshNotificationAndSession(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else
            startForeground(NOTIFICATION_ID, notification);
        startingService = false;
    }
    @Override
    public void onDestroy() {
        Log.d(DEBUG_TAG, "onDestroy");
        super.onDestroy();
        if (exo != null) {
            exo.stop();
            exo.clearMediaItems();
            exo.removeListener(exoListener);
            exo.release();
            exo = null;
        }
        currentTrackDataRef = null;
        currentTrackData = null;
        refs = null;

        if (wakeLock.isHeld())
            wakeLock.release();
        else
            Log.w(DEBUG_TAG, "Failed to release wake lock since it was not held");
        abandonAudioFocus();
        hideNotification();
        releaseSession();

        instance = null;
        startingService = false;
    }

    public static void setPlaylist(DataRef... trackLocs) {
        setPlaylist(0, trackLocs);
    }
    public static void setPlaylist(int startPos, DataRef... trackLocs) {
        Log.d(DEBUG_TAG, "Setting playlist");
        boolean hasTracks = trackLocs != null && trackLocs.length > 0;
        if (hasTracks) {
            boolean samePlaylist = Arrays.equals(refs, trackLocs);
            refs = trackLocs;
            setTrackIndex(startPos);

            if (!samePlaylist && !startingService) {
                if (instance == null) {
                    //Log.d(DEBUG_TAG, "Service not started, starting...");
                    startingService = true;
                    OxShellApp.getContext().startForegroundService(new Intent(OxShellApp.getContext(), MediaPlayer.class));
                }
                refreshPlaylist();
            }
        } else
            clearPlaylist();
    }
    private static void refreshPlaylist() {
        //Log.d(DEBUG_TAG, "Refreshing playlist");
        runWhenServiceReady(() -> {
            //Log.d(DEBUG_TAG, "Actually refreshing playlist");
            if (exo != null) {
                exo.stop();
                exo.clearMediaItems();
            } else {
                exo = new ExoPlayer.Builder(instance).build();
                exo.addListener(exoListener);
            }

            for (DataRef trackLoc : refs)
                if (trackLoc.getLocType() == DataLocation.file)
                    exo.addMediaItem(MediaItem.fromUri((String)trackLoc.getLoc()));
                else if (trackLoc.getLocType() == DataLocation.resolverUri)
                    exo.addMediaItem(MediaItem.fromUri((Uri)trackLoc.getLoc()));
            exo.prepare();
            seekTo(trackIndex, 0);
            //exo.seekTo(trackIndex, 0);
            //Log.d(DEBUG_TAG, "Setting playlist with " + trackLocs.length + " item(s), setting pos as " + trackIndex);
            refreshMetadata();
            refreshNotificationAndSession(exo.isPlaying());
        });
    }
    public static void clearPlaylist() {
        Log.d(DEBUG_TAG, "Clearing playlist");
        if (instance != null)
            instance.stopSelf();
        else
            Log.w(DEBUG_TAG, "Failed to clear playlist, service instance is null");
    }
    public static void setPlayerView(PlayerView playerView) {
        runWhenExoReady(() -> playerView.setPlayer(exo));
    }

    private static void runWhenServiceReady(Runnable action) {
        runWhenServiceReady(action, 3);
    }
    private static void runWhenServiceReady(Runnable action, float ttl) {
        if (instance == null) {
            Handler waitHandler = new Handler(Looper.getMainLooper());
            waitHandler.post(new Runnable() {
                long startTime = SystemClock.uptimeMillis();

                @Override
                public void run() {
                    if (instance == null) {
                        if (SystemClock.uptimeMillis() - startTime <= ttl * 1000)
                            waitHandler.postDelayed(this, MathHelpers.calculateMillisForFps(30));
                        else
                            Log.w(DEBUG_TAG, "Failed to run music player action, service instance was null for more than " + ttl + " second(s)");
                    } else
                        action.run();
                }
            });
        } else
            action.run();
    }
    private static void runWhenExoReady(Runnable action) {
        runWhenExoReady(action, 3);
    }
    private static void runWhenExoReady(Runnable action, float ttl) {
        if (exo == null) {
            if (exoWaitHandler == null)
                exoWaitHandler = new Handler(Looper.getMainLooper());
            exoWaitHandler.post(new Runnable() {
                long startTime = SystemClock.uptimeMillis();

                @Override
                public void run() {
                    if (exo == null || exo.getPlaybackState() != ExoPlayer.STATE_READY) {
                        if (SystemClock.uptimeMillis() - startTime <= ttl * 1000)
                            exoWaitHandler.postDelayed(this, MathHelpers.calculateMillisForFps(30));
                        else
                            Log.w(DEBUG_TAG, "Failed to run music player action, exo was null for more than " + ttl + " second(s)");
                    } else
                        action.run();
                }
            });
        } else
            action.run();
    }
    public static void togglePlay() {
        if (isPlaying())
            pause();
        else
            play();
    }
    public static void play() {
        play(trackIndex);
    }
    public static void play(int index) {
        runWhenExoReady(() -> {
            Log.d(DEBUG_TAG, "Play track " + index);
            if (index >= 0 && index < exo.getMediaItemCount()) {
                requestAudioFocus();

                setTrackIndex(index);
                setVolume(SettingsKeeper.getMusicVolume()); // TODO: change to a more appropriate place
                if (trackIndex != exo.getCurrentMediaItemIndex())
                    seekTo(trackIndex, 0);
                if (!exo.isPlaying()) {
                    exo.play();
                    fireIsPlayingEvent(true);
                }

                refreshMetadata();
                refreshNotificationAndSession(true);
            } else
                Log.w(DEBUG_TAG, "Failed to play, attempted play " + index + " when exoplayer has " + exo.getMediaItemCount() + " item(s)");
        });
    }
    public static boolean isPlaying() {
        return exo != null && exo.getPlaybackState() == Player.STATE_READY && exo.isPlaying();
    }
    public static void pause() {
        runWhenExoReady(() -> {
            //if (exo != null) {
            Log.d(DEBUG_TAG, "pause");
            exo.pause();
            abandonAudioFocus();
            refreshNotificationAndSession(false);
            fireIsPlayingEvent(false);
            //} else
            //    Log.w(DEBUG_TAG, "Failed to pause, exoplayer is null");
        });
    }
    public static void stop() {
        runWhenExoReady(() -> {
            //if (exo != null) {
            Log.d(DEBUG_TAG, "stop");
            boolean wasPlaying = exo.isPlaying();
            clearPlaylist();
            if (wasPlaying)
                fireIsPlayingEvent(false);
            fireMediaItemChangedEvent(-1);
            //} else
            //    Log.w(DEBUG_TAG, "Failed to stop, exoplayer is null");
        });
    }
    public static void seekToNext() {
        runWhenExoReady(() -> {
            //if (exo != null) {
            exo.seekToNext();

            //trackIndex = exo.getCurrentMediaItemIndex();
            setTrackIndex(exo.getCurrentMediaItemIndex());
            refreshMetadata();
            refreshNotificationAndSession(exo.isPlaying());
            fireMediaItemChangedEvent(exo.getCurrentMediaItemIndex());
            //} else
            //    Log.w(DEBUG_TAG, "Failed to seek to next, exoplayer is null");
        });
    }
    public static void seekToPrev() {
        runWhenExoReady(() -> {
            //if (exo != null) {
            int prevIndex = exo.getCurrentMediaItemIndex();
            exo.seekToPrevious();

            //trackIndex = exo.getCurrentMediaItemIndex();
            setTrackIndex(exo.getCurrentMediaItemIndex());
            refreshMetadata();
            refreshNotificationAndSession(exo.isPlaying());
            if (prevIndex != exo.getCurrentMediaItemIndex())
                fireMediaItemChangedEvent(exo.getCurrentMediaItemIndex());
            else
                fireSeekEventEvent(getCurrentPosition());
            //} else
            //    Log.w(DEBUG_TAG, "Failed to seek to previous, exoplayer is null");
        });
    }
    public static void seekTo(long ms) {
        runWhenExoReady(() -> {
            //if (exo != null) {
            exo.seekTo(ms);
            refreshNotificationAndSession(isPlaying());
            //} else
            //    Log.w(DEBUG_TAG, "Failed to seek, exoplayer is null");
        });
    }
    public static void seekTo(int trackIndex, long ms) {
        runWhenExoReady(() -> {
            Log.d(DEBUG_TAG, "Seeking to track " + trackIndex);
            int prevIndex = exo.getCurrentMediaItemIndex();
            exo.seekTo(trackIndex, ms);

            //trackIndex = exo.getCurrentMediaItemIndex();
            setTrackIndex(exo.getCurrentMediaItemIndex());
            refreshMetadata();
            refreshNotificationAndSession(exo.isPlaying());
            if (prevIndex != exo.getCurrentMediaItemIndex())
                fireMediaItemChangedEvent(exo.getCurrentMediaItemIndex());
            else
                fireSeekEventEvent(getCurrentPosition());
        });
    }
    public static void seekForward() {
        runWhenExoReady(() -> {
            //if (exo != null) {
            exo.seekForward();
            refreshNotificationAndSession(isPlaying());
            fireSeekEventEvent(getCurrentPosition());
            //} else
            //    Log.w(DEBUG_TAG, "Failed to seek forward, exoplayer is null");
        });
    }
    public static void seekBack() {
        runWhenExoReady(() -> {
            //if (exo != null) {
            exo.seekBack();
            refreshNotificationAndSession(isPlaying());
            fireSeekEventEvent(getCurrentPosition());
            //} else
            //    Log.w(DEBUG_TAG, "Failed to seek back, exoplayer is null");
        });
    }
    public static void setVolume(float value) {
        runWhenExoReady(() -> {
            //if (exo != null)
            exo.setVolume(value);
            //else
            //    Log.w(DEBUG_TAG, "Failed to set volume, exoplayer is null");
        });
    }
    public static long getCurrentPosition() {
        if (exo != null && (exo.getPlaybackState() == ExoPlayer.STATE_READY || exo.getPlaybackState() == Player.STATE_BUFFERING))
            return exo.getCurrentPosition();
        else
            return PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN;
    }

    private static void requestAudioFocus() {
        AudioManager audioManager = OxShellApp.getAudioManager();
        AudioFocusRequest.Builder requestBuilder = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN);
        AudioAttributes.Builder attribBuilder = new AudioAttributes.Builder();
        attribBuilder.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC);
        requestBuilder.setAudioAttributes(attribBuilder.build());
        audioManager.requestAudioFocus(audioFocusRequest = requestBuilder.build());
    }
    private static void abandonAudioFocus() {
        if (audioFocusRequest != null) {
            OxShellApp.getAudioManager().abandonAudioFocusRequest(audioFocusRequest);
            audioFocusRequest = null;
        }
    }

    public static DataRef getCurrentTrack() {
        return refs != null && refs.length > 0 && trackIndex >= 0 && trackIndex < refs.length ? refs[trackIndex] : null;
    }
    private static void setTrackIndex(int index) {
        Log.w(DEBUG_TAG, "Attempting to set track index from " + trackIndex + " to " + index);
        if (refs == null || refs.length <= 0)
            trackIndex = -1;
        else
            trackIndex = MathHelpers.clamp(index, 0, refs.length - 1);
    }
    private static DataRef getCurrentDataRef() {
        if (exo == null)
            return null;
        Log.d("MediaPlayer", "trackIndex: " + trackIndex + " refsNull: " + (refs == null) + " refsLength: " + (refs != null ? refs.length : 0));
        //return refs[MathHelpers.clamp(exo.getCurrentMediaItemIndex(), 0, refs.length - 1)];
        return refs[MathHelpers.clamp(trackIndex, 0, refs.length - 1)];
    }
    private static void refreshMetadata() {
        if (exo == null)
            currentTrackData = null;
        else {
            // so we don't keep loading the metadata when its already loaded
            DataRef current = getCurrentDataRef();
            if (current != null && !current.equals(currentTrackDataRef)) {
                currentTrackDataRef = current;
                currentTrackData = Metadata.getMediaMetadata(current);
            }
        }
    }
    public static String getCurrentTitle() {
        if (exo == null)
            return null;
        if (currentTrackData == null)
            refreshMetadata();

        String title = null;
        if (currentTrackData != null) {
            title = currentTrackData.getTitle();
            if (title == null || title.isEmpty()) {
                DataRef dataRef = getCurrentDataRef();
                if (dataRef.getLocType() == DataLocation.file)
                    title = AndroidHelpers.removeExtension((new File((String) dataRef.getLoc())).getName());
                else if (dataRef.getLocType() == DataLocation.resolverUri)
                    title = AndroidHelpers.removeExtension(AndroidHelpers.getFileNameFromUri((Uri) dataRef.getLoc()));
                else
                    title = "?";
            }
        } else
            Log.w("MediaPlayer", "Attempting to retrieve metadata when none exist");
        return title;
    }
    public static String getCurrentArtist() {
        if (exo == null)
            return null;
        if (currentTrackData == null)
            refreshMetadata();
        String artist = null;
        if (currentTrackData != null) {
            artist = currentTrackData.getArtist();
            if (artist == null || artist.isEmpty())
                artist = "Various Artists";
        } else
            Log.w("MediaPlayer", "Attempting to retrieve metadata when none exist");
        return artist;
    }
    public static String getCurrentAlbum() {
        if (exo == null)
            return null;
        if (currentTrackData == null)
            refreshMetadata();
        String album = null;
        if (currentTrackData != null) {
            album = currentTrackData.getAlbum();
            if (album == null || album.isEmpty())
                album = "Other";
        } else
            Log.w("MediaPlayer", "Attempting to retrieve metadata when none exist");
        return album;
    }
    public static Bitmap getCurrentAlbumArt() {
        if (exo == null)
            return null;
        if (currentTrackData == null)
            refreshMetadata();
        return currentTrackData != null ? currentTrackData.getAlbumArt() : null;
    }
    public static long getCurrentDuration() {
        if (exo == null)
            return 0;
        if (currentTrackData == null)
            refreshMetadata();
        long duration = 0;
        try {
            duration = Long.valueOf(currentTrackData.getDuration());
        } catch (Exception e) {
            Log.w(DEBUG_TAG, "Failed to retrieve track duration from metadata");
        }
        return duration;
    }

    private static void refreshNotificationAndSession(boolean isPlaying) {
        setSessionState(isPlaying);
        showNotification(isPlaying);
    }
    private static void hideNotification() {
        NotificationManager notificationManager = instance.getSystemService(NotificationManager.class);
        notificationManager.cancel(NOTIFICATION_ID);
    }
    private static void showNotification(boolean isPlaying) {
        // Create the notification channel.
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Music Player",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Music Player Notification Channel");
        NotificationManager notificationManager = instance.getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);

        Intent openPlayerIntent = new Intent(instance, MediaPlayerActivity.class);
        openPlayerIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPlayerPendingIntent = PendingIntent.getActivity(instance, 0, openPlayerIntent, PendingIntent.FLAG_MUTABLE);

        Intent stopIntent = new Intent(STOP_INTENT);
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(instance, 0, stopIntent, PendingIntent.FLAG_MUTABLE);
        Intent prevIntent = new Intent(PREV_INTENT);
        PendingIntent prevPendingIntent = PendingIntent.getBroadcast(instance, 0, prevIntent, PendingIntent.FLAG_MUTABLE);
        Intent pauseIntent = new Intent(PAUSE_INTENT);
        PendingIntent pausePendingIntent = PendingIntent.getBroadcast(instance, 0, pauseIntent, PendingIntent.FLAG_MUTABLE);
        Intent playIntent = new Intent(PLAY_INTENT);
        PendingIntent playPendingIntent = PendingIntent.getBroadcast(instance, 0, playIntent, PendingIntent.FLAG_MUTABLE);
        Intent nextIntent = new Intent(NEXT_INTENT);
        PendingIntent nextPendingIntent = PendingIntent.getBroadcast(instance, 0, nextIntent, PendingIntent.FLAG_MUTABLE);

        // Create a NotificationCompat.MediaStyle object and set its properties
        androidx.media.app.NotificationCompat.MediaStyle mediaStyle = new androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(session.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2) // Show prev, play, and next buttons in compact view
                .setShowCancelButton(true)
                .setCancelButtonIntent(stopPendingIntent);

        // Create the notification using the builder.
        NotificationCompat.Builder builder = new NotificationCompat.Builder(instance, CHANNEL_ID)
                .setSmallIcon(R.drawable.ox_white)
                .setLargeIcon(getCurrentAlbumArt())
                .setContentTitle(getCurrentTitle())
                .setContentText(getCurrentArtist() + " - " + getCurrentAlbum())
                .setContentIntent(openPlayerPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSilent(true)
                .setStyle(mediaStyle)
                .addAction(R.drawable.baseline_skip_previous_24, "Prev", prevPendingIntent)
                .addAction(isPlaying ? R.drawable.baseline_pause_24 : R.drawable.baseline_play_arrow_24, isPlaying ? "Pause" : "Play", isPlaying ? pausePendingIntent : playPendingIntent)
                .addAction(R.drawable.baseline_skip_next_24, "Next", nextPendingIntent)
                .addAction(R.drawable.baseline_close_24, "Stop", stopPendingIntent)
                .setOngoing(true);

        notificationManager.notify(NOTIFICATION_ID, notification = builder.build());
    }

    private static void releaseSession() {
        if (session != null) {
            session.release();
            session = null;
        }
    }
    private static void setSessionState(boolean isPlaying) {
        if (session == null)
            prepareSession();
        // source: https://android-developers.googleblog.com/2020/08/playing-nicely-with-media-controls.html
        session.setMetadata(new MediaMetadataCompat.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, getCurrentTitle())
                .putString(MediaMetadata.METADATA_KEY_ARTIST, getCurrentArtist())
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, getCurrentAlbumArt())
                .putLong(MediaMetadata.METADATA_KEY_DURATION, getCurrentDuration())
                .build());
        session.setPlaybackState(new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_STOP | PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_SEEK_TO)
                .setState(isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED, getCurrentPosition(), 1.0f)
                .build());
    }
    private static void prepareSession() {
        session = new MediaSessionCompat(instance, BuildConfig.APP_LABEL);
        session.setCallback(new MediaSessionCompat.Callback() {
//            @Override
//            public boolean onMediaButtonEvent(@NonNull Intent mediaButtonIntent) {
//                Log.d(DEBUG_TAG, mediaButtonIntent + ", " + (mediaButtonIntent.getExtras() != null ? mediaButtonIntent.getExtras().toString() : "null"));
//                return super.onMediaButtonEvent(mediaButtonIntent);
//            }

            @Override
            public void onPlay() {
                super.onPlay();
                //Log.d(DEBUG_TAG, "onPlay");
                play();
            }

            @Override
            public void onSkipToQueueItem(long id) {
                super.onSkipToQueueItem(id);
                //Log.d(DEBUG_TAG, "onSkipToQueueItem " + id);
                play((int)id);
            }

            @Override
            public void onPause() {
                super.onPause();
                //Log.d(DEBUG_TAG, "onPause");
                pause();
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                //Log.d(DEBUG_TAG, "onSkipToNext");
                seekToNext();
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
                //Log.d(DEBUG_TAG, "onSkipToPrevious");
                seekToPrev();
            }

            @Override
            public void onFastForward() {
                super.onFastForward();
                //Log.d(DEBUG_TAG, "onFastForward");
                seekForward();
            }

            @Override
            public void onRewind() {
                super.onRewind();
                //Log.d(DEBUG_TAG, "onRewind");
                seekBack();
            }

            @Override
            public void onStop() {
                super.onStop();
                //Log.d(DEBUG_TAG, "onStop");
                stop();
            }

            @Override
            public void onSeekTo(long pos) {
                super.onSeekTo(pos);
                //Log.d(DEBUG_TAG, "onSeekTo " + pos);
                seekTo((int)pos);
                refreshNotificationAndSession(isPlaying());
            }
        });
        session.setActive(true);
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return null;
    }
}
