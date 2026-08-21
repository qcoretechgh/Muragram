package com.dycrd.muragram;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

public class PhotoViewerActivity extends Activity {
    private static final String TAG = "PhotoViewer";
    private final Matrix matrix = new Matrix();
    private final Matrix savedMatrix = new Matrix();
    
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;
    private int mode = NONE;
    
    private final PointF start = new PointF();
    private final PointF mid = new PointF();
    private float oldDist = 1f;

    private ImageView imageView;
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    private Bitmap currentBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            LegacyHelper.applyLocale(this);
            boolean isDark = getSharedPreferences(NetworkUtils.PREFS_NAME, 0).getBoolean(NetworkUtils.KEY_DARK_THEME, false);
            setTheme(isDark ? R.style.Theme_Muragram_Dark : R.style.Theme_Muragram);
            
            setContentView(R.layout.activity_photo_viewer);

            imageView = (ImageView) findViewById(R.id.fullImageView);
            final ProgressBar progress = (ProgressBar) findViewById(R.id.photoProgress);

            String url = getIntent().getStringExtra("url");
            if (url != null && imageView != null) {
                if (progress != null) progress.setVisibility(View.VISIBLE);
                imageView.setTag(url);
                new NetworkUtils.ImageTask(imageView, new NetworkUtils.ImageCallback() {
                    @Override
                    public void onBitmap(final Bitmap bitmap) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                if (isFinishing()) return;
                                if (progress != null) progress.setVisibility(View.GONE);
                                if (bitmap == null) {
                                    Toast.makeText(PhotoViewerActivity.this, R.string.failed_load_image, Toast.LENGTH_SHORT).show();
                                } else if (imageView != null) {
                                    currentBitmap = bitmap;
                                    imageView.setScaleType(ImageView.ScaleType.MATRIX);
                                    centerImage();
                                }
                            }
                        });
                    }
                }).start(url);
            }

            if (imageView != null) {
                scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());
                gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
                    @Override public boolean onDoubleTap(MotionEvent e) {
                        centerImage();
                        return true;
                    }
                });

                imageView.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        if (gestureDetector != null) gestureDetector.onTouchEvent(event);
                        if (scaleGestureDetector != null) scaleGestureDetector.onTouchEvent(event);
                        
                        switch (event.getAction() & MotionEvent.ACTION_MASK) {
                            case MotionEvent.ACTION_DOWN:
                                savedMatrix.set(matrix);
                                start.set(event.getX(), event.getY());
                                mode = DRAG;
                                break;
                            case MotionEvent.ACTION_POINTER_DOWN:
                                oldDist = spacing(event);
                                if (oldDist > 10f) {
                                    savedMatrix.set(matrix);
                                    midPoint(mid, event);
                                    mode = ZOOM;
                                }
                                break;
                            case MotionEvent.ACTION_UP:
                            case MotionEvent.ACTION_POINTER_UP:
                                mode = NONE;
                                break;
                            case MotionEvent.ACTION_MOVE:
                                if (mode == DRAG) {
                                    matrix.set(savedMatrix);
                                    matrix.postTranslate(event.getX() - start.x, event.getY() - start.y);
                                } else if (mode == ZOOM) {
                                    float newDist = spacing(event);
                                    if (newDist > 10f) {
                                        matrix.set(savedMatrix);
                                        float scale = newDist / oldDist;
                                        matrix.postScale(scale, scale, mid.x, mid.y);
                                    }
                                }
                                break;
                        }
                        
                        if (imageView != null && currentBitmap != null) {
                            fixBounds();
                            imageView.setImageMatrix(matrix);
                        }
                        return true;
                    }
                });
            }

            LegacyHelper.setupActionBar(this);
        } catch (Exception e) {
            Log.e(TAG, "onCreate failed", e);
        }
    }

    private void centerImage() {
        if (imageView == null || currentBitmap == null) return;
        float viewWidth = imageView.getWidth();
        float viewHeight = imageView.getHeight();
        if (viewWidth == 0 || viewHeight == 0) {
            imageView.post(new Runnable() {
                @Override public void run() { centerImage(); }
            });
            return;
        }
        
        float bmWidth = currentBitmap.getWidth();
        float bmHeight = currentBitmap.getHeight();
        float scale = Math.min(viewWidth / bmWidth, viewHeight / bmHeight);
        matrix.setScale(scale, scale);
        matrix.postTranslate((viewWidth - bmWidth * scale) / 2f, (viewHeight - bmHeight * scale) / 2f);
        imageView.setImageMatrix(matrix);
    }

    private void fixBounds() {
        if (imageView == null || currentBitmap == null) return;
        RectF rect = new RectF(0, 0, currentBitmap.getWidth(), currentBitmap.getHeight());
        matrix.mapRect(rect);
        
        float viewWidth = imageView.getWidth();
        float viewHeight = imageView.getHeight();
        float deltaX = 0, deltaY = 0;
        
        if (rect.width() < viewWidth) deltaX = (viewWidth - rect.width()) / 2f - rect.left;
        else if (rect.left > 0) deltaX = -rect.left;
        else if (rect.right < viewWidth) deltaX = viewWidth - rect.right;
        
        if (rect.height() < viewHeight) deltaY = (viewHeight - rect.height()) / 2f - rect.top;
        else if (rect.top > 0) deltaY = -rect.top;
        else if (rect.bottom < viewHeight) deltaY = viewHeight - rect.bottom;
        
        matrix.postTranslate(deltaX, deltaY);
    }

    private float spacing(MotionEvent event) {
        if (event.getPointerCount() < 2) return 0f;
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private void midPoint(PointF point, MotionEvent event) {
        if (event.getPointerCount() < 2) return;
        point.set((event.getX(0) + event.getX(1)) / 2f, (event.getY(0) + event.getY(1)) / 2f);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            matrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
            return true;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (menu == null) return false;
        menu.add(0, 1, 0, R.string.download).setIcon(android.R.drawable.ic_menu_save);
        menu.add(0, 2, 0, R.string.share_image_title).setIcon(android.R.drawable.ic_menu_share);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item == null) return false;
        if (item.getItemId() == 1) saveToGallery();
        else if (item.getItemId() == 2) shareImage();
        else if (item.getItemId() == 16908332) finish();
        return super.onOptionsItemSelected(item);
    }

    private void saveToGallery() {
        if (currentBitmap == null) return;
        try {
            String url = android.provider.MediaStore.Images.Media.insertImage(getContentResolver(), currentBitmap, getString(R.string.save_prefix) + System.currentTimeMillis(), getString(R.string.saved_from_app));
            if (url != null) Toast.makeText(this, R.string.saved_to_gallery, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {}
    }

    private void shareImage() {
        if (currentBitmap == null) return;
        try {
            String path = android.provider.MediaStore.Images.Media.insertImage(getContentResolver(), currentBitmap, getString(R.string.share_prefix), null);
            if (path == null) return;
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("image/jpeg");
            i.putExtra(Intent.EXTRA_STREAM, Uri.parse(path));
            startActivity(Intent.createChooser(i, getString(R.string.share_image_title)));
        } catch (Exception e) {}
    }
}
