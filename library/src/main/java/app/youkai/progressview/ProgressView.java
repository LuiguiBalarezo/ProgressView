package app.youkai.progressview;

import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;

/**
 * A simple View for number selection.
 */
@SuppressWarnings("FieldCanBeLocal")
public class ProgressView extends LinearLayout {
    private ImageView decrement;
    private EditText progressView;
    private TextView maxView;
    private ImageView increment;

    private int progress;
    private Integer max;

    private OnProgressChangedListener listener;
    private LongTouchHandler decrementHandler;
    private LongTouchHandler incrementHandler;

    private boolean fromButton;

    public ProgressView(Context context) {
        this(context, null);
    }

    public ProgressView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ProgressView(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs, defStyle, 0);
    }

    public ProgressView(Context context, AttributeSet attrs, int defStyle, int defStyleRes) {
        super(context, attrs);
        init(attrs, defStyle, defStyleRes);
    }

    private void init(AttributeSet attrs, int defStyle, int defStyleRes) {
        /* Setup our root LinearLayout */
        LayoutInflater inflater = (LayoutInflater) getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.view_progressview, this, true);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        /* Make our root clickable, to prevent children from receiving click events from the outside */
        setClickable(true);

        /* Set android:animateLayoutChanges="true" */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            setLayoutTransition(new LayoutTransition());
        }

        /* Obtain references to our views */
        decrement = (ImageView) findViewById(R.id.decrement);
        progressView = (EditText) findViewById(R.id.progress);
        maxView = (TextView) findViewById(R.id.max);
        increment = (ImageView) findViewById(R.id.increment);

        /* Set touch listeners for long click */
        decrementHandler = new LongTouchHandler(new IncrementListener() {
            @Override
            public void increment() {
                decrement.post(new Runnable() {
                    @Override
                    public void run() {
                        decrementProgress();
                    }
                });
            }

            @Override
            public void incrementBy(final int changeValue) {
                decrement.post(new Runnable() {
                    @Override
                    public void run() {
                        addToProgress(changeValue);
                    }
                });
            }
        }, -1);
        decrement.setOnTouchListener(decrementHandler);

        incrementHandler = new LongTouchHandler(new IncrementListener() {
            @Override
            public void increment() {
                increment.post(new Runnable() {
                    @Override
                    public void run() {
                        incrementProgress();
                    }
                });
            }

            @Override
            public void incrementBy(final int changeValue) {
                increment.post(new Runnable() {
                    @Override
                    public void run() {
                        addToProgress(changeValue);
                    }
                });
            }
        }, 1);
        increment.setOnTouchListener(incrementHandler);

        /* Set text change watcher */
        progressView.addTextChangedListener(new TextWatcher() {
            Timer timer = new Timer();

            String lastText = "";

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                /* do nothing */
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                /* do nothing */
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (fromButton) {
                    fromButton = false;
                    return;
                }

                /* Get that progress text */
                final String text = progressView.getText().toString().trim();

                /* Don't schedule a timer if there isn't any real change */
                if (lastText.equals(text)) return;
                lastText = text;

                timer.cancel();
                timer = new Timer();
                timer.schedule(
                        new TimerTask() {
                            int newProgress;

                            @Override
                            public void run() {
                                /* Parse the progress string and use zero instead if it's empty */
                                newProgress = Integer.parseInt(text.length() != 0 ? text : "0");

                                /* Make sure it's not negative */
                                newProgress = Math.max(newProgress, 0);

                                /* Make sure it's not greater that the maximum */
                                if (!progressIsAllowed(newProgress) && newProgress > max) newProgress = max;

                                /* Set the new progress (on the UI thread) */
                                progressView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        setProgress(newProgress);
                                    }
                                });
                            }
                        },
                        500
                );
            }
        });

        /* Read view attributes */
        TypedArray a = getContext().getTheme().obtainStyledAttributes(
                attrs, R.styleable.ProgressView, defStyle, defStyleRes
        );

        boolean showMax;
        try {
            showMax = a.getBoolean(R.styleable.ProgressView_pv_showMax, false);
        } finally {
            a.recycle();
        }

        /* Apply read values */
        showMax(showMax);

        /* Set initial progress */
        setProgress(0);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState savedState = new SavedState(superState);
        savedState.progress = progress;
        if (max != null) savedState.max = max;
        return savedState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());

        if (max != null) setMax(max);
        setProgress(savedState.progress);
    }

    public void setListener(OnProgressChangedListener listener) {
        this.listener = listener;
    }

    public void setProgress(final int progress) {
        if (progress < 0) throw new IllegalArgumentException("Pro-gress cannot be negative. That's con-gress!");
        if (!progressIsAllowed(progress)) throw new IllegalArgumentException("Progress cannot be greater than the maximum.");

        this.progress = progress;
        progressView.setText(String.valueOf(progress));
        progressView.setSelection(progressView.getText().length());

        // Disabled buttons if at max/min.
        if (progress == 0) {
            decrement.setClickable(false);
            decrement.setPressed(false);
            decrementHandler.cancelPress();

            increment.setClickable(true);
        } else if (max != null && progress == max) {
            increment.setClickable(false);
            increment.setPressed(false);
            incrementHandler.cancelPress();

            decrement.setClickable(true);
        } else {
            decrement.setClickable(true);
            increment.setClickable(true);
        }

        notifyProgressChanged();
    }

    public int getProgress() {
        return progress;
    }

    public void decrementProgress() {
        setProgress(progress - 1);
    }

    public void incrementProgress() {
        setProgress(progress + 1);
    }

    private void addToProgress(int change) {
        setProgress(progress + change);
    }

    private boolean progressIsAllowed(int progress) {
        // if there is no maximum, the progress is always allowed
        return max == null || progress <= max;
    }

    @SuppressLint("SetTextI18n")
    public void setMax(int max) {
        if (max <= 0)
            throw new IllegalArgumentException("Maximum cannot be zero or negative. Use removeMax() to remove the maximum.");

        this.max = max;
        fromButton = true;
        maxView.setText("/ " + max);

        /* If the new maximum is less than the current progress, set the progress to the max. */
        if (max < progress) setProgress(max);
    }

    public void removeMax() {
        max = null;
        maxView.setText(null);
        showMax(false);
    }

    public Integer getMax() {
        return max;
    }

    public void showMax(boolean show) {
        maxView.setVisibility(show ? VISIBLE : GONE);
    }

    private void notifyProgressChanged() {
        if (listener != null) {
            listener.onProgressChanged(progress);
        }
    }

    public interface OnProgressChangedListener {
        void onProgressChanged(int progress);
    }

    private static class SavedState extends BaseSavedState {
        int progress;
        int max;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            this.progress = in.readInt();
            this.max = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(progress);
            out.writeInt(max);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }
}
