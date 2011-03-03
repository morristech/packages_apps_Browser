/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.browser.view;

import com.android.browser.R;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

public class PieMenu extends FrameLayout {

    private static final int MAX_LEVELS = 5;

    public interface PieController {
        /**
         * called before menu opens to customize menu
         * returns if pie state has been changed
         */
        public boolean onOpen();
    }

    /**
     * A view like object that lives off of the pie menu
     */
    public interface PieView {

        public interface OnLayoutListener {
            public void onLayout(int ax, int ay, boolean left);
        }

        public void setLayoutListener(OnLayoutListener l);

        public void layout(int anchorX, int anchorY, boolean onleft);

        public void draw(Canvas c);

        public boolean onTouchEvent(MotionEvent evt);

    }

    private Point mCenter;
    private int mRadius;
    private int mRadiusInc;
    private int mSlop;

    private boolean mOpen;
    private PieController mController;

    private List<PieItem> mItems;
    private int mLevels;
    private int[] mCounts;
    private PieView mPieView = null;

    private Drawable mBackground;

    // touch handling
    PieItem mCurrentItem;

    /**
     * @param context
     * @param attrs
     * @param defStyle
     */
    public PieMenu(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    /**
     * @param context
     * @param attrs
     */
    public PieMenu(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    /**
     * @param context
     */
    public PieMenu(Context context) {
        super(context);
        init(context);
    }

    private void init(Context ctx) {
        mItems = new ArrayList<PieItem>();
        mLevels = 0;
        mCounts = new int[MAX_LEVELS];
        Resources res = ctx.getResources();
        mRadius = (int) res.getDimension(R.dimen.qc_radius_start);
        mRadiusInc = (int) res.getDimension(R.dimen.qc_radius_increment);
        mSlop = (int) res.getDimension(R.dimen.qc_slop);
        mOpen = false;
        setWillNotDraw(false);
        setDrawingCacheEnabled(false);
        mCenter = new Point(0,0);
        mBackground = res.getDrawable(R.drawable.qc_background_normal);
    }

    public void setController(PieController ctl) {
        mController = ctl;
    }

    public void addItem(PieItem item) {
        // add the item to the pie itself
        mItems.add(item);
        int l = item.getLevel();
        mLevels = Math.max(mLevels, l);
        mCounts[l]++;
    }

    public void removeItem(PieItem item) {
        mItems.remove(item);
    }

    public void clearItems() {
        mItems.clear();
    }

    private boolean onTheLeft() {
        return mCenter.x < mSlop;
    }

    /**
     * guaranteed has center set
     * @param show
     */
    private void show(boolean show) {
        mOpen = show;
        if (mOpen) {
            if (mController != null) {
                boolean changed = mController.onOpen();
            }
            layoutPie();
        }
        if (!show) {
            mCurrentItem = null;
            mPieView = null;
        }
        invalidate();
    }

    private void setCenter(int x, int y) {
        if (x < mSlop) {
            mCenter.x = 0;
        } else {
            mCenter.x = getWidth();
        }
        mCenter.y = y;
    }

    private void layoutPie() {
        int inner = mRadius;
        int outer = mRadius + mRadiusInc;
        int radius = mRadius;
        for (int i = 0; i < mLevels; i++) {
            int level = i + 1;
            float sweep = (float) Math.PI / (mCounts[level] + 1);
            float angle = sweep;
            for (PieItem item : mItems) {
                if (item.getLevel() == level) {
                    View view = item.getView();
                    view.measure(view.getLayoutParams().width,
                            view.getLayoutParams().height);
                    int w = view.getMeasuredWidth();
                    int h = view.getMeasuredHeight();
                    int x = (int) (outer * Math.sin(angle));
                    int y = mCenter.y - (int) (outer * Math.cos(angle)) - h / 2;
                    if (onTheLeft()) {
                        x = mCenter.x + x - w;
                    } else {
                        x = mCenter.x - x;
                    }
                    view.layout(x, y, x + w, y + h);
                    float itemstart = angle - sweep / 2;
                    item.setGeometry(itemstart, sweep, inner, outer);
                    angle += sweep;
                }
            }
            inner += mRadiusInc;
            outer += mRadiusInc;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mOpen) {
            int w = mBackground.getIntrinsicWidth();
            int h = mBackground.getIntrinsicHeight();
            int left = mCenter.x - w;
            int top = mCenter.y - h / 2;
            mBackground.setBounds(left, top, left + w, top + h);
            int state = canvas.save();
            if (onTheLeft()) {
                canvas.scale(-1, 1);
            }
            mBackground.draw(canvas);
            canvas.restoreToCount(state);
            for (PieItem item : mItems) {
                drawItem(canvas, item);
            }
            if (mPieView != null) {
                mPieView.draw(canvas);
            }
        }
    }

    private void drawItem(Canvas canvas, PieItem item) {
        int outer = item.getOuterRadius();
        int left = mCenter.x - outer;
        int top = mCenter.y - outer;
        // draw the item view
        View view = item.getView();
        int state = canvas.save();
        canvas.translate(view.getX(), view.getY());
        view.draw(canvas);
        canvas.restoreToCount(state);
    }

    // touch handling for pie

    @Override
    public boolean onTouchEvent(MotionEvent evt) {
        float x = evt.getX();
        float y = evt.getY();
        int action = evt.getActionMasked();
        int edges = evt.getEdgeFlags();
        if (MotionEvent.ACTION_DOWN == action) {
            if ((x > getWidth() - mSlop) || (x < mSlop)) {
                setCenter((int) x, (int) y);
                show(true);
                return true;
            }
        } else if (MotionEvent.ACTION_UP == action) {
            if (mOpen) {
                boolean handled = false;
                if (mPieView != null) {
                    handled = mPieView.onTouchEvent(evt);
                }
                PieItem item = mCurrentItem;
                deselect();
                show(false);
                if (!handled && (item != null)) {
                    item.getView().performClick();
                }
                return true;
            }
        } else if (MotionEvent.ACTION_CANCEL == action) {
            if (mOpen) {
                show(false);
            }
            deselect();
            return false;
        } else if (MotionEvent.ACTION_MOVE == action) {
            boolean handled = false;
            PointF polar = getPolar(x, y);
            int maxr = mRadius + mLevels * mRadiusInc + 50;
            if (mPieView != null) {
                handled = mPieView.onTouchEvent(evt);
            }
            if (handled) {
                invalidate();
                return false;
            }
            if (polar.y > maxr) {
                deselect();
                show(false);
                evt.setAction(MotionEvent.ACTION_DOWN);
                if (getParent() != null) {
                    ((ViewGroup) getParent()).dispatchTouchEvent(evt);
                }
                return false;
            }
            PieItem item = findItem(polar);
            if (mCurrentItem != item) {
                onEnter(item);
                if ((item != null) && item.isPieView()) {
                    int cx = item.getView().getLeft() + (onTheLeft()
                            ? item.getView().getWidth() : 0);
                    int cy = item.getView().getTop();
                    mPieView = item.getPieView();
                    layoutPieView(mPieView, cx, cy);
                }
                invalidate();
            }
        }
        // always re-dispatch event
        return false;
    }

    private void layoutPieView(PieView pv, int x, int y) {
        pv.layout(x, y, onTheLeft());
    }

    /**
     * enter a slice for a view
     * updates model only
     * @param item
     */
    private void onEnter(PieItem item) {
        // deselect
        if (mCurrentItem != null) {
            mCurrentItem.setSelected(false);
        }
        if (item != null) {
            // clear up stack
            playSoundEffect(SoundEffectConstants.CLICK);
            item.setSelected(true);
            mPieView = null;
        }
        mCurrentItem = item;
    }

    private void deselect() {
        if (mCurrentItem != null) {
            mCurrentItem.setSelected(false);
        }
        mCurrentItem = null;
        mPieView = null;
    }

    private PointF getPolar(float x, float y) {
        PointF res = new PointF();
        // get angle and radius from x/y
        res.x = (float) Math.PI / 2;
        x = mCenter.x - x;
        if (mCenter.x < mSlop) {
            x = -x;
        }
        y = mCenter.y - y;
        res.y = (float) Math.sqrt(x * x + y * y);
        if (y > 0) {
            res.x = (float) Math.asin(x / res.y);
        } else if (y < 0) {
            res.x = (float) (Math.PI - Math.asin(x / res.y ));
        }
        return res;
    }

    /**
     *
     * @param polar x: angle, y: dist
     * @return the item at angle/dist or null
     */
    private PieItem findItem(PointF polar) {
        // find the matching item:
        for (PieItem item : mItems) {
            if ((item.getInnerRadius() < polar.y)
                    && (item.getOuterRadius() > polar.y)
                    && (item.getStartAngle() < polar.x)
                    && (item.getStartAngle() + item.getSweep() > polar.x)) {
                return item;
            }
        }
        return null;
    }

}
