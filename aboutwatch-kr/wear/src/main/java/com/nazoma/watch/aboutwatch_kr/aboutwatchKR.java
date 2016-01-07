package com.nazoma.watch.aboutwatch_kr;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class aboutwatchKR extends CanvasWatchFaceService {
    private static final long INTERACTIVE_UPDATE_RATE_MS2 = TimeUnit.MINUTES.toMillis(1);

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        static final int MSG_UPDATE_TIME = 0;

        // 非同期処理　－　Handler
        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        invalidate();  // 再描画
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis(); // 現在時間のunixtimestamp
                            long delayMs = INTERACTIVE_UPDATE_RATE_MS2 - (timeMs % INTERACTIVE_UPDATE_RATE_MS2); // 次再描画するまでの時間を算出
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs); // delayMS秒後にhandleMessageを送る。←つまりこれがタイマー
                        }
                        break;
                }
            }
        };

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        boolean mRegisteredTimeZoneReceiver = false;
        Paint mTextPaint;
        boolean mAmbient;
        Time mTime;
        float mXOffset;
        float mYOffset;
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(aboutwatchKR.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            Resources resources = aboutwatchKR.this.getResources();
            mTextPaint = new Paint();
            mTextPaint.setColor(resources.getColor(R.color.digital_text));
            // ビルドは問題ないけど実行時にエラー出て落ちる。Font asset not found assets/PixelMplus12-Regular.ttf
            // mTextPaint.setTypeface(Typeface.createFromAsset(aboutwatchKR.this.getBaseContext().getAssets(), "assets/PixelMplus12-Regular.ttf"));
            mTextPaint.setAntiAlias(true);
            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        // homescreenのアクティブ状態。裏に行ったときにどう処理するかでバッテリーの持ちに影響
        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            aboutwatchKR.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            aboutwatchKR.this.unregisterReceiver(mTimeZoneReceiver);
        }

        // watchの形状の判断（丸か四角か）
        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = aboutwatchKR.this.getResources();
            boolean isRound = insets.isRound();
            float textSize = resources.getDimension(isRound ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            mTextPaint.setTextSize(textSize);
        }

        // watchがスリープ中にどうなるか
        /**
         Interactive mode  通常の描画、秒針などアニメーション付き
         Ambient mode      スリープ状態、1分に一度の描画更新
         Protect mode      Ambient mode 時に描画面積を最小に  <-
         LowBit mode       Ambient mode 時に白黒 2階調  <-
         Protect / LowBitは機種依存なので、onPropertiesChangedで取得する
         */
        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        // AmbientModeの時に動く。1分毎。
        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        // AmbientModeに切り替わったら呼ばれる。
        // なるべく白っぽい面積を減らすようにする。
        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            super.onDraw(canvas, bounds);

            // 背景色
            canvas.drawColor(Color.BLACK);

            // 表示用テキスト作成
            mTime.setToNow();
            String text = getAboutTimeText(mTime);

            // 表示位置算出(中央に配置)
            int textWidth = (int) Math.ceil(mTextPaint.measureText(text));
            Paint.FontMetrics fontMetrics = mTextPaint.getFontMetrics();
            int textHeight = (int) Math.ceil(Math.abs(fontMetrics.ascent) + Math.abs(fontMetrics.descent) + Math.abs(fontMetrics.leading));
            mXOffset = (bounds.width() / 2) - (textWidth / 2);
            mYOffset = (bounds.height() / 2) + (textHeight / 2);

            // 表示
            canvas.drawText(text, mXOffset, mYOffset, mTextPaint);
        }

        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        private boolean shouldTimerBeRunning() { return isVisible() && !isInAmbientMode(); }

        private String getAboutTimeText(Time _time) {
            String text = "";
            int minute = _time.minute;
            if (minute == 0) {
                text = String.format("%d ", _time.hour) + "정각";
            } else if (minute < 5 && minute > 0) {
                text = String.format("%d ", _time.hour) + "전후";
            } else if (minute >= 5 && minute < 25) {
                text = String.format("%d ", _time.hour) + "전반";
            } else if (minute >= 25 && minute < 35) {
                text = String.format("%d ", _time.hour) + "반쯤";
            } else if (minute >= 35 && minute < 55) {
                text = String.format("%d ", _time.hour) + "후반";
            } else if (minute >= 55 && minute <= 59) {
                int hour = _time.hour;
                if (hour == 23) hour = 0;
                else hour += 1;
                text = String.format("%d ", hour) + "전후";
            }
            return text;
        }
    }
}