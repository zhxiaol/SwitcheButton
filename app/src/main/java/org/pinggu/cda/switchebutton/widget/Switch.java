package org.pinggu.cda.switchebutton.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.Checkable;

import org.pinggu.cda.switchebutton.R;

//开关按钮
public class Switch extends View implements Checkable, ThemeManager.OnThemeChangedListener {
	public static final long FRAME_DURATION = 1000 / 60;
    protected int mStyleId;
    protected int mCurrentStyle = ThemeManager.THEME_UNDEFINED;

	private boolean mRunning = false;
	
	private Paint mPaint;
	private RectF mDrawRect;
	private RectF mTempRect;
	private Path mTrackPath;
	
	private int mTrackSize = -1;
	private ColorStateList mTrackColors;
	private Paint.Cap mTrackCap = Paint.Cap.ROUND;
	private int mThumbRadius = -1;
	private ColorStateList mThumbColors;
	private float mThumbPosition;
	private int mMaxAnimDuration = -1;
	private Interpolator mInterpolator;	
	private int mGravity = Gravity.CENTER_VERTICAL;

	private boolean mChecked = false;
	private float mMemoX;
	
	private float mStartX;
	private float mFlingVelocity;
	
	private long mStartTime;
	private int mAnimDuration;
	private float mStartPosition;
	
	private int[] mTempStates = new int[2];

    private int mShadowSize = -1;
    private int mShadowOffset = -1;
    private Path mShadowPath;
    private Paint mShadowPaint;

    private static final int COLOR_SHADOW_START = 0x4C000000;
    private static final int COLOR_SHADOW_END = 0x00000000;

    private boolean mIsRtl = false;

    /**
     * Interface definition for a callback to be invoked when the checked state is changed.
     */
    public interface OnCheckedChangeListener{
        /**
         * Called when the checked state is changed.
         * @param view The Switch view.
         * @param checked The checked state.
         */
        public void onCheckedChanged(Switch view, boolean checked);
    }

    private OnCheckedChangeListener mOnCheckedChangeListener;

    public Switch(Context context) {
        super(context);

        init(context, null, 0, 0);
    }

    public Switch(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context, attrs, 0, 0);
    }

	public Switch(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		
		init(context, attrs, defStyleAttr, 0);
	}

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public Switch(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        init(context, attrs, defStyleAttr, defStyleRes);
    }
	
	protected void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes){
		mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		
		mDrawRect = new RectF();
		mTempRect = new RectF();
		mTrackPath = new Path();

        mFlingVelocity = ViewConfiguration.get(context).getScaledMinimumFlingVelocity();

        applyStyle(context, attrs, defStyleAttr, defStyleRes);

        if(!isInEditMode())
            mStyleId = ThemeManager.getStyleId(context, attrs, defStyleAttr, defStyleRes);
	}

    public void applyStyle(int resId){
        applyStyle(getContext(), null, 0, resId);
    }

    protected void applyStyle(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes){

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.Switch, defStyleAttr, defStyleRes);

        for(int i = 0, count = a.getIndexCount(); i < count; i++){
            int attr = a.getIndex(i);
            if(attr == R.styleable.Switch_sw_trackSize)
                mTrackSize = a.getDimensionPixelSize(attr, 0);
            else if(attr == R.styleable.Switch_sw_trackColor)
                mTrackColors = a.getColorStateList(attr);
            else if(attr == R.styleable.Switch_sw_trackCap){
                int cap = a.getInteger(attr, 0);
                if(cap == 0)
                    mTrackCap = Paint.Cap.BUTT;
                else if(cap == 1)
                    mTrackCap = Paint.Cap.ROUND;
                else
                    mTrackCap = Paint.Cap.SQUARE;
            }
            else if(attr == R.styleable.Switch_sw_thumbColor)
                mThumbColors = a.getColorStateList(attr);
            else if(attr == R.styleable.Switch_sw_thumbRadius)
                mThumbRadius = a.getDimensionPixelSize(attr, 0);
            else if(attr == R.styleable.Switch_sw_thumbElevation) {
                mShadowSize = a.getDimensionPixelSize(attr, 0);
                mShadowOffset = mShadowSize / 2;
            }
            else if(attr == R.styleable.Switch_sw_animDuration)
                mMaxAnimDuration = a.getInt(attr, 0);
            else if(attr == R.styleable.Switch_android_gravity)
                mGravity = a.getInt(attr, 0);
            else if(attr == R.styleable.Switch_android_checked)
                setCheckedImmediately(a.getBoolean(attr, mChecked));
            else if(attr == R.styleable.Switch_sw_interpolator){
                int resId = a.getResourceId(R.styleable.Switch_sw_interpolator, 0);
                if(resId != 0)
                    mInterpolator = AnimationUtils.loadInterpolator(context, resId);
            }
        }

        a.recycle();

        if(mTrackSize < 0)
            mTrackSize = dpToPx(context, 2);

        if(mThumbRadius < 0)
            mThumbRadius = dpToPx(context, 8);

        if(mShadowSize < 0) {
            mShadowSize = dpToPx(context, 2);
            mShadowOffset = mShadowSize / 2;
        }

        if(mMaxAnimDuration < 0)
            mMaxAnimDuration = context.getResources().getInteger(android.R.integer.config_mediumAnimTime);

        if(mInterpolator == null)
            mInterpolator = new DecelerateInterpolator();

        if(mTrackColors == null){
            int[][] states = new int[][]{
                    new int[]{-android.R.attr.state_checked},
                    new int[]{android.R.attr.state_checked},
            };
            int[] colors = new int[]{
                    getColor(colorControlNormal(context, 0xFF000000), 0.5f),
                    getColor(colorControlActivated(context, 0xFF000000), 0.5f),
            };

            mTrackColors = new ColorStateList(states, colors);
        }

        if(mThumbColors == null){
            int[][] states = new int[][]{
                    new int[]{-android.R.attr.state_checked},
                    new int[]{android.R.attr.state_checked},
            };
            int[] colors = new int[]{
                    0xFAFAFA,
                    colorControlActivated(context, 0xFF000000),
            };

            mThumbColors = new ColorStateList(states, colors);
        }

        mPaint.setStrokeCap(mTrackCap);
        buildShadow();
        invalidate();
    }

    @Override
    public void onThemeChanged(ThemeManager.OnThemeChangedEvent event) {
        int style = ThemeManager.getInstance().getCurrentStyle(mStyleId);
        if(mCurrentStyle != style){
            mCurrentStyle = style;
            applyStyle(mCurrentStyle);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if(mStyleId != 0) {
            ThemeManager.getInstance().registerOnThemeChangedListener(this);
            onThemeChanged(null);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if(mStyleId != 0)
            ThemeManager.getInstance().unregisterOnThemeChangedListener(this);
    }







    /**
     * Set a listener will be called when the checked state is changed.
     * @param listener The {@link OnCheckedChangeListener} will be called.
     */
    public void setOnCheckedChangeListener(OnCheckedChangeListener listener){
        mOnCheckedChangeListener = listener;
    }

	@Override
	public void setChecked(boolean checked) {		
		if(mChecked != checked) {
            mChecked = checked;
            if(mOnCheckedChangeListener != null)
                mOnCheckedChangeListener.onCheckedChanged(this, mChecked);
        }
		
		float desPos = mChecked ? 1f : 0f;
		
		if(mThumbPosition != desPos)
			startAnimation();
	}

    /**
     * Change the checked state of this Switch immediately without showing animation.
     * @param checked The checked state.
     */
    public void setCheckedImmediately(boolean checked){
        if(mChecked != checked) {
            mChecked = checked;
            if(mOnCheckedChangeListener != null)
                mOnCheckedChangeListener.onCheckedChanged(this, mChecked);
        }
        mThumbPosition = mChecked ? 1f : 0f;
        invalidate();
    }

	@Override
	public boolean isChecked() {
		return mChecked;
	}

	@Override
	public void toggle() {	
		if(isEnabled())
			setChecked(!mChecked);
	}

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        boolean rtl = layoutDirection == LAYOUT_DIRECTION_RTL;
        if(mIsRtl != rtl) {
            mIsRtl = rtl;
            invalidate();
        }
    }

	@Override
	public boolean onTouchEvent(@NonNull MotionEvent event) {
		super.onTouchEvent(event);

        float x = event.getX();
        if(mIsRtl)
            x = 2 * mDrawRect.centerX() - x;

		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
                if(getParent() != null)
                    getParent().requestDisallowInterceptTouchEvent(true);
				mMemoX = x;
				mStartX = mMemoX;
				mStartTime = SystemClock.uptimeMillis();
				break;
			case MotionEvent.ACTION_MOVE:
				float offset = (x - mMemoX) / (mDrawRect.width() - mThumbRadius * 2);
				mThumbPosition = Math.min(1f, Math.max(0f, mThumbPosition + offset));
				mMemoX = x;
				invalidate();
				break;
			case MotionEvent.ACTION_UP:
                if(getParent() != null)
                    getParent().requestDisallowInterceptTouchEvent(false);

				float velocity = (x - mStartX) / (SystemClock.uptimeMillis() - mStartTime) * 1000;
				if(Math.abs(velocity) >= mFlingVelocity)
					setChecked(velocity > 0);
				else if((!mChecked && mThumbPosition < 0.1f) || (mChecked && mThumbPosition > 0.9f))
					toggle();
				else					
					setChecked(mThumbPosition > 0.5f);				
				break;
			case MotionEvent.ACTION_CANCEL:
                if(getParent() != null)
                    getParent().requestDisallowInterceptTouchEvent(false);

				setChecked(mThumbPosition > 0.5f);
				break;
		}
		
		return true;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int widthSize = MeasureSpec.getSize(widthMeasureSpec);
		int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		
		int heightSize = MeasureSpec.getSize(heightMeasureSpec);
		int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		
		switch (widthMode) {
			case MeasureSpec.UNSPECIFIED:
				widthSize = getSuggestedMinimumWidth();
				break;
			case MeasureSpec.AT_MOST:
				widthSize = Math.min(widthSize, getSuggestedMinimumWidth());
				break;
		}
				
		switch (heightMode) {
			case MeasureSpec.UNSPECIFIED:
				heightSize = getSuggestedMinimumHeight();
				break;
			case MeasureSpec.AT_MOST:
				heightSize = Math.min(heightSize, getSuggestedMinimumHeight());
				break;
		}
		
		setMeasuredDimension(widthSize, heightSize);
	}

	@Override
	public int getSuggestedMinimumWidth() {
		return mThumbRadius * 4 + Math.max(mShadowSize, getPaddingLeft()) + Math.max(mShadowSize, getPaddingRight());
	}

	@Override
	public int getSuggestedMinimumHeight() {
		return mThumbRadius * 2 + Math.max(mShadowSize - mShadowOffset, getPaddingTop()) + Math.max(mShadowSize + mShadowOffset, getPaddingBottom());
	}
		
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		mDrawRect.left = Math.max(mShadowSize, getPaddingLeft());
		mDrawRect.right = w - Math.max(mShadowSize, getPaddingRight());
		
		int height = mThumbRadius * 2;
		int align = mGravity & Gravity.VERTICAL_GRAVITY_MASK;
								
		switch (align) {
			case Gravity.TOP:
				mDrawRect.top = Math.max(mShadowSize - mShadowOffset, getPaddingTop());
				mDrawRect.bottom = mDrawRect.top + height;
				break;
			case Gravity.BOTTOM:
				mDrawRect.bottom = h - Math.max(mShadowSize + mShadowOffset, getPaddingBottom());
				mDrawRect.top = mDrawRect.bottom - height;
				break;
			default:
				mDrawRect.top = (h - height) / 2f;
				mDrawRect.bottom = mDrawRect.top + height;
				break;
		}		
	}

    private int getTrackColor(boolean checked){
		mTempStates[0] = isEnabled() ? android.R.attr.state_enabled : -android.R.attr.state_enabled;
		mTempStates[1] = checked ? android.R.attr.state_checked : -android.R.attr.state_checked;
		
		return mTrackColors.getColorForState(mTempStates, 0);
	}
	
	private int getThumbColor(boolean checked){
		mTempStates[0] = isEnabled() ? android.R.attr.state_enabled : -android.R.attr.state_enabled;
		mTempStates[1] = checked ? android.R.attr.state_checked : -android.R.attr.state_checked;
		
		return mThumbColors.getColorForState(mTempStates, 0);
	}

    private void buildShadow(){
        if(mShadowSize <= 0)
            return;

        if(mShadowPaint == null){
            mShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            mShadowPaint.setStyle(Paint.Style.FILL);
            mShadowPaint.setDither(true);
        }
        float startRatio = (float)mThumbRadius / (mThumbRadius + mShadowSize + mShadowOffset);
        mShadowPaint.setShader(new RadialGradient(0, 0, mThumbRadius + mShadowSize,
                new int[]{COLOR_SHADOW_START, COLOR_SHADOW_START, COLOR_SHADOW_END},
                new float[]{0f, startRatio, 1f}
                , Shader.TileMode.CLAMP));

        if(mShadowPath == null){
            mShadowPath = new Path();
            mShadowPath.setFillType(Path.FillType.EVEN_ODD);
        }
        else
            mShadowPath.reset();
        float radius = mThumbRadius + mShadowSize;
        mTempRect.set(-radius, -radius, radius, radius);
        mShadowPath.addOval(mTempRect, Path.Direction.CW);
        radius = mThumbRadius - 1;
        mTempRect.set(-radius, -radius - mShadowOffset, radius, radius - mShadowOffset);
        mShadowPath.addOval(mTempRect, Path.Direction.CW);
    }

	private void getTrackPath(float x, float y, float radius){
		float halfStroke = mTrackSize / 2f;
		
		mTrackPath.reset();
		
		if(mTrackCap != Paint.Cap.ROUND){
			mTempRect.set(x - radius + 1f, y - radius + 1f, x + radius - 1f, y + radius - 1f);
			float angle = (float)(Math.asin(halfStroke / (radius - 1f)) / Math.PI * 180);
			
			if(x - radius > mDrawRect.left){			
				mTrackPath.moveTo(mDrawRect.left, y - halfStroke);
				mTrackPath.arcTo(mTempRect, 180 + angle, -angle * 2);
				mTrackPath.lineTo(mDrawRect.left, y + halfStroke);
				mTrackPath.close();
			}
			
			if(x + radius < mDrawRect.right){
				mTrackPath.moveTo(mDrawRect.right, y - halfStroke);
				mTrackPath.arcTo(mTempRect, -angle, angle * 2);
				mTrackPath.lineTo(mDrawRect.right, y + halfStroke);
				mTrackPath.close();
			}
		}
		else{
			float angle = (float)(Math.asin(halfStroke / (radius - 1f)) / Math.PI * 180);
			
			if(x - radius > mDrawRect.left){					
				float angle2 = (float)(Math.acos(Math.max(0f, (mDrawRect.left + halfStroke - x + radius) / halfStroke)) / Math.PI * 180);
				
				mTempRect.set(mDrawRect.left, y - halfStroke, mDrawRect.left + mTrackSize, y + halfStroke);
				mTrackPath.arcTo(mTempRect, 180 - angle2, angle2 * 2);
				
				mTempRect.set(x - radius + 1f, y - radius + 1f, x + radius - 1f, y + radius - 1f);
				mTrackPath.arcTo(mTempRect, 180 + angle, -angle * 2);
				mTrackPath.close();
			}
			
			if(x + radius < mDrawRect.right){
				float angle2 = (float)Math.acos(Math.max(0f, (x + radius - mDrawRect.right + halfStroke) / halfStroke));
				mTrackPath.moveTo((float) (mDrawRect.right - halfStroke + Math.cos(angle2) * halfStroke), (float) (y + Math.sin(angle2) * halfStroke));
				
				angle2 = (float)(angle2 / Math.PI * 180);				
				mTempRect.set(mDrawRect.right - mTrackSize, y - halfStroke, mDrawRect.right, y + halfStroke);
				mTrackPath.arcTo(mTempRect, angle2, -angle2 * 2);
				
				mTempRect.set(x - radius + 1f, y - radius + 1f, x + radius - 1f, y + radius - 1f);
				mTrackPath.arcTo(mTempRect, -angle, angle * 2);
				mTrackPath.close();
			}
		}		
	}

	@Override
	public void draw(@NonNull Canvas canvas) {
		super.draw(canvas);

		float x = (mDrawRect.width() - mThumbRadius * 2) * mThumbPosition + mDrawRect.left + mThumbRadius;
        if(mIsRtl)
            x = 2 * mDrawRect.centerX() - x;
		float y = mDrawRect.centerY();
				
		getTrackPath(x, y, mThumbRadius);
		mPaint.setColor(getMiddleColor(getTrackColor(false), getTrackColor(true), mThumbPosition));
		mPaint.setStyle(Paint.Style.FILL);		
		canvas.drawPath(mTrackPath, mPaint);

        if(mShadowSize > 0){
            int saveCount = canvas.save();
            canvas.translate(x, y + mShadowOffset);
            canvas.drawPath(mShadowPath, mShadowPaint);
            canvas.restoreToCount(saveCount);
        }

        mPaint.setColor(getMiddleColor(getThumbColor(false), getThumbColor(true), mThumbPosition));
        mPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(x, y, mThumbRadius, mPaint);
	}

	private void resetAnimation(){	
		mStartTime = SystemClock.uptimeMillis();
		mStartPosition = mThumbPosition;
		mAnimDuration = (int)(mMaxAnimDuration * (mChecked ? (1f - mStartPosition) : mStartPosition));
	}
		
	private void startAnimation() {
		if(getHandler() != null){
			resetAnimation();		
			mRunning = true;
			getHandler().postAtTime(mUpdater, SystemClock.uptimeMillis() + FRAME_DURATION);
		}
		else
			mThumbPosition = mChecked ? 1f : 0f;		
	    invalidate();  	    
	}
	
	private void stopAnimation() {			
		mRunning = false;
        mThumbPosition = mChecked ? 1f : 0f;
        if(getHandler() != null)
		    getHandler().removeCallbacks(mUpdater);
		invalidate();
	}
	
	private final Runnable mUpdater = new Runnable() {

	    @Override
	    public void run() {
	    	update();
	    }
		    
	};
		
	private void update(){
		long curTime = SystemClock.uptimeMillis();	
		float progress = Math.min(1f, (float)(curTime - mStartTime) / mAnimDuration);
		float value = mInterpolator.getInterpolation(progress);
		
		mThumbPosition = mChecked ? (mStartPosition * (1 - value) + value) : (mStartPosition * (1 - value));
		
		if(progress == 1f)
			stopAnimation();
				
    	if(mRunning) {
            if(getHandler() != null)
                getHandler().postAtTime(mUpdater, SystemClock.uptimeMillis() + FRAME_DURATION);
            else
                stopAnimation();
        }
    	
    	invalidate();
	}

	@Override
	protected Parcelable onSaveInstanceState() {
		Parcelable superState = super.onSaveInstanceState();

        SavedState ss = new SavedState(superState);

        ss.checked = isChecked();
        return ss;
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		SavedState ss = (SavedState) state;
		  
        super.onRestoreInstanceState(ss.getSuperState());
        setChecked(ss.checked);
        requestLayout();
	}
	
	static class SavedState extends BaseSavedState {
        boolean checked;

        /**
         * Constructor called from {@link Switch#onSaveInstanceState()}
         */
        SavedState(Parcelable superState) {
            super(superState);
        }
        
        /**
         * Constructor called from {@link #CREATOR}
         */
        private SavedState(Parcel in) {
            super(in);
            checked = (Boolean)in.readValue(null);
        }

        @Override
        public void writeToParcel(@NonNull Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeValue(checked);
        }

        @Override
        public String toString() {
            return "Switch.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + " checked=" + checked + "}";
        }

        public static final Creator<SavedState> CREATOR
                = new Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

	private static int getMiddleValue(int prev, int next, float factor){
		return Math.round(prev + (next - prev) * factor);
	}

	public static int getMiddleColor(int prevColor, int curColor, float factor){
		if(prevColor == curColor)
			return curColor;

		if(factor == 0f)
			return prevColor;
		else if(factor == 1f)
			return curColor;

		int a = getMiddleValue(Color.alpha(prevColor), Color.alpha(curColor), factor);
		int r = getMiddleValue(Color.red(prevColor), Color.red(curColor), factor);
		int g = getMiddleValue(Color.green(prevColor), Color.green(curColor), factor);
		int b = getMiddleValue(Color.blue(prevColor), Color.blue(curColor), factor);

		return Color.argb(a, r, g, b);
	}

	public static int getColor(int baseColor, float alphaPercent){
		int alpha = Math.round(Color.alpha(baseColor) * alphaPercent);

		return (baseColor & 0x00FFFFFF) | (alpha << 24);
	}

	private static TypedValue value;

	public static int dpToPx(Context context, int dp){
		return (int)(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics()) + 0.5f);
	}
	private static int getColor(Context context, int id, int defaultValue){
		if(value == null)
			value = new TypedValue();

		try{
			Resources.Theme theme = context.getTheme();
			if(theme != null && theme.resolveAttribute(id, value, true)){
				if (value.type >= TypedValue.TYPE_FIRST_INT && value.type <= TypedValue.TYPE_LAST_INT)
					return value.data;
				else if (value.type == TypedValue.TYPE_STRING)
					return context.getResources().getColor(value.resourceId);
			}
		}
		catch(Exception ex){}

		return defaultValue;
	}
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public static int colorControlNormal(Context context, int defaultValue){
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
			return getColor(context, android.R.attr.colorControlNormal, defaultValue);

		return getColor(context, R.attr.colorControlNormal, defaultValue);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public static int colorControlActivated(Context context, int defaultValue){
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
			return getColor(context, android.R.attr.colorControlActivated, defaultValue);

		return getColor(context, R.attr.colorControlActivated, defaultValue);
	}
	
}
