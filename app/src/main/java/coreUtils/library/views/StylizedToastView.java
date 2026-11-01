package coreUtils.library.views;

import static android.content.res.ColorStateList.valueOf;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;

import com.nextgen.R;

import coreUtils.base.BaseActivity;
import coreUtils.library.networks.URLUtility;

/**
 * Custom Toast implementation that provides stylized, themable toast messages
 * with support for icons, custom colors, typefaces, and positioning. This class
 * extends the standard Android Toast and adds rich customization options for
 * consistent, branded user feedback across the application.
 *
 * <p><strong>Core features:</strong>
 * <ul>
 * <li>Custom layout with message text and optional icon.</li>
 * <li>Method chaining for fluent configuration (Builder-style pattern).</li>
 * <li>Support for custom text colors, sizes, and typefaces.</li>
 * <li>Icon tinting with PorterDuff.Mode.SRC_IN for color theming.</li>
 * <li>Static factory methods for success, error, warning, and info toasts.</li>
 * </ul>
 *
 * <p>Usage example: StylizedToastView.create(activity, "Success!")
 *     .setIcon(R.drawable.ic_check).show();
 *
 * @see Toast
 * @see #create(BaseActivity, CharSequence)
 */
@SuppressWarnings("ALL")
public class StylizedToastView extends Toast {

    private final View rootView;
    private final TextView textView;
    private final ImageView imageView;

    /**
     * Constructs a new StylizedToastView instance with the specified context and
     * custom root view. This constructor initializes the internal references to
     * the TextView (for message) and ImageView (for icon) by finding them in the
     * provided view hierarchy.
     *
     * <p>The view is expected to have:
     * <ul>
     * <li>A TextView with ID {@link R.id#txLabel} for the message text.</li>
     * <li>An ImageView with ID {@link R.id#imgIcon} for the optional icon.</li>
     * </ul>
     *
     * <p>The provided view is set as the toast's content view via
     * {@link #setView(View)}.
     *
     * @param context The context used to create the toast. Must not be null.
     * @param view    The custom layout view for this toast. Must not be null and
     *                should contain the expected TextView and ImageView.
     */
    public StylizedToastView(@NonNull Context context, @NonNull View view) {
        super(context);
        this.rootView = view;
        this.textView = view.findViewById(R.id.txLabel);
        this.imageView = view.findViewById(R.id.imgIcon);
        setView(view);
    }

    /**
     * Sets the toast icon using a drawable resource ID. The icon becomes visible
     * if it was previously hidden. This method automatically sets the ImageView's
     * visibility to {@link View#VISIBLE} before applying the drawable.
     *
     * <p>If the internal ImageView reference is null, this method does nothing
     * and returns the current instance without making any changes.
     *
     * @param iconResId The resource ID of the drawable to use as the icon.
     * @return This StylizedToastView instance for method chaining.
     * @see #setIcon(Drawable)
     * @see #hideIcon()
     */
    public StylizedToastView setIcon(@DrawableRes int iconResId) {
        if (imageView != null) {
            imageView.setVisibility(View.VISIBLE);
            imageView.setImageResource(iconResId);
        }
        return this;
    }

    /**
     * Sets the toast icon using a Drawable object. The icon becomes visible if
     * it was previously hidden. This method automatically sets the ImageView's
     * visibility to {@link View#VISIBLE} before applying the drawable.
     *
     * <p>If the internal ImageView reference is null, this method does nothing
     * and returns the current instance without making any changes.
     *
     * @param drawable The Drawable to use as the icon, or null to clear the icon.
     * @return This StylizedToastView instance for method chaining.
     * @see #setIcon(int)
     * @see #hideIcon()
     */
    public StylizedToastView setIcon(@Nullable Drawable drawable) {
        if (imageView != null) {
            imageView.setVisibility(View.VISIBLE);
            imageView.setImageDrawable(drawable);
        }
        return this;
    }

    /**
     * Hides the icon in the toast by setting its visibility to {@link View#GONE}.
     * This method is useful when the toast should display only text without any
     * accompanying icon, such as for neutral informational messages.
     *
     * <p>If the internal ImageView reference is null (e.g., if the view has not
     * been inflated yet), this method does nothing and returns the current
     * instance without making any changes.
     *
     * @return This StylizedToastView instance for method chaining.
     * @see #setIcon(int)
     * @see ImageView#setVisibility(int)
     */
    public StylizedToastView hideIcon() {
        if (imageView != null) {
            imageView.setVisibility(View.GONE);
        }
        return this;
    }

    /**
     * Applies a tint color to the toast icon. This method sets both the
     * {@link ImageView#setImageTintList(android.content.res.ColorStateList)}
     * and a color filter on the icon, ensuring the tint is applied consistently
     * across different Android versions.
     *
     * <p>If the internal ImageView reference is null, this method does nothing
     * and returns the current instance without making any changes.
     *
     * @param color The ARGB color integer to tint the icon (e.g., from {@link Context#getColor(int)}).
     * @return This StylizedToastView instance for method chaining.
     * @see ImageView#setImageTintList(android.content.res.ColorStateList)
     * @see ImageView#setColorFilter(int, PorterDuff.Mode)
     */
    public StylizedToastView setIconTint(@ColorInt int color) {
        if (imageView != null) {
            imageView.setImageTintList(valueOf(color));
            imageView.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        }
        return this;
    }

    /**
     * Sets the message text to be displayed in the toast. This method updates the
     * content of the TextView with the provided CharSequence.
     *
     * <p>If the internal TextView reference is null (e.g., if the view has not
     * been inflated yet), this method does nothing and returns the current
     * instance without making any changes.
     *
     * @param message The text to display in the toast. Can be a String,
     *                SpannableString, or any other CharSequence implementation.
     * @return This StylizedToastView instance for method chaining.
     * @see TextView#setText(CharSequence)
     */
    public StylizedToastView setMessage(CharSequence message) {
        if (textView != null) {
            textView.setText(message);
        }
        return this;
    }

    /**
     * Sets the toast message text from a string resource ID. This method retrieves
     * the string from the resources using the root view's context and delegates
     * to {@link #setMessage(CharSequence)} to update the displayed message.
     *
     * <p>This method implements the TextView contract for setting text content.
     *
     * @param resId The resource ID of the string to display.
     * @see TextView#setText(int)
     * @see #setMessage(CharSequence)
     */
    @Override
    public void setText(int resId) {
        setMessage(rootView.getContext().getText(resId));
    }

    /**
     * Sets the toast message text from a CharSequence. This method delegates
     * directly to {@link #setMessage(CharSequence)} to update the displayed message.
     *
     * <p>This method implements the TextView contract for setting text content.
     *
     * @param charSequence The text to display in the toast.
     * @see TextView#setText(CharSequence)
     * @see #setMessage(CharSequence)
     */
    @Override
    public void setText(CharSequence charSequence) {
        setMessage(charSequence);
    }

    /**
     * Sets the color of the toast message text. This method allows customization
     * of the text appearance to match different toast types (success, error,
     * warning, info) or application branding.
     *
     * <p>If the internal TextView reference is null (e.g., if the view has not
     * been inflated yet), this method does nothing and returns the current
     * instance without making any changes.
     *
     * @param color The ARGB color integer (e.g., from {@link Context#getColor(int)}
     *              or {@link android.graphics.Color#parseColor(String)}).
     * @return This StylizedToastView instance for method chaining.
     * @see TextView#setTextColor(int)
     */
    public StylizedToastView setTextColor(@ColorInt int color) {
        if (textView != null) {
            textView.setTextColor(color);
        }
        return this;
    }

    /**
     * Sets the text size of the toast message. This method allows customization
     * of the font size in scale-independent pixels (sp) to respect user's font
     * size preferences.
     *
     * <p>If the internal TextView reference is null (e.g., if the view has not
     * been inflated yet), this method does nothing and returns the current
     * instance without making any changes.
     *
     * @param sizeSp The text size in scale-independent pixels (sp). Typical values
     *               range from 12sp to 18sp for toast messages.
     * @return This StylizedToastView instance for method chaining.
     */
    public StylizedToastView setTextSize(float sizeSp) {
        if (textView != null) {
            textView.setTextSize(sizeSp);
        }
        return this;
    }

    /**
     * Sets the typeface (font family and style) for the toast message text.
     * This method allows customization of the font appearance to match the
     * application's branding or design system.
     *
     * <p>If the internal TextView reference is null (e.g., if the view has not
     * been inflated yet), this method does nothing and returns the current
     * instance without making any changes.
     *
     * @param typeface The Typeface to apply to the toast message text. Common
     *                 values include {@link Typeface#DEFAULT}, {@link Typeface#SANS_SERIF},
     *                 {@link Typeface#SERIF}, or custom loaded typefaces.
     * @return This StylizedToastView instance for method chaining.
     */
    public StylizedToastView setTypeface(Typeface typeface) {
        if (textView != null) {
            textView.setTypeface(typeface);
        }
        return this;
    }

    /**
     * Sets the position on screen where the toast should appear. This method
     * delegates to the underlying Toast's {@link #setGravity(int, int, int)}
     * method, allowing precise placement of the toast on the screen.
     *
     * <p>Common gravity values include:
     * <ul>
     * <li>{@link android.view.Gravity#TOP} - Top of screen.</li>
     * <li>{@link android.view.Gravity#CENTER} - Center of screen.</li>
     * <li>{@link android.view.Gravity#BOTTOM} - Bottom of screen (default).</li>
     * </ul>
     * </p>
     *
     * @param gravity The gravity constant specifying the anchor position.
     * @param xOffset Horizontal offset in pixels from the gravity anchor.
     * @param yOffset Vertical offset in pixels from the gravity anchor.
     * @return This StylizedToastView instance for method chaining.
     * @see Toast#setGravity(int, int, int)
     */
    public StylizedToastView setToastGravity(int gravity,
                                             int xOffset, int yOffset) {
        setGravity(gravity, xOffset, yOffset);
        return this;
    }

    /**
     * Sets the duration for which the toast should be displayed on screen.
     * This method allows overriding the default duration set during toast creation.
     *
     * @param duration The duration to show the toast. Can be {@link #LENGTH_SHORT}
     *                 or {@link #LENGTH_LONG}, or a custom millisecond value.
     * @return This StylizedToastView instance for method chaining.
     * @see Toast#setDuration(int)
     */
    public StylizedToastView setToastDuration(int duration) {
        setDuration(duration);
        return this;
    }

    /**
     * Displays the toast on screen. This method is a convenience wrapper around
     * {@link #show()} that provides a more semantic name for toast display.
     * All configuration methods (icon, iconTint, textColor, duration) should be
     * called before invoking this method.
     */
    public void showToast() {
        show();
    }

    /**
     * Creates a new StylizedToastView instance with the specified activity context
     * and message. This method performs validation on the message and returns null
     * if the message is invalid (null, empty, or a valid URL).
     *
     * <p><strong>Validation rules:</strong>
     * <ul>
     * <li>If message is null → returns null.</li>
     * <li>If message is empty after trimming → returns null.</li>
     * <li>If message is a valid URL → returns null (URLs are not displayed as toasts).</li>
     * </ul>
     * </p>
     *
     * <p>The toast uses a themed context with {@link R.style#style_application}
     * and inflates the layout {@link R.layout#layout_stylized_toast_1}.
     * Default duration is set to {@link #LENGTH_LONG}.
     *
     * @param activity The base activity context used for theming. Must not be null.
     * @param message  The message to display. May be null or empty.
     * @return A configured StylizedToastView instance, or null if message is invalid.
     * @see #setMessage(CharSequence)
     * @see URLUtility#isValidURL(String)
     */
    public static StylizedToastView create(@NonNull BaseActivity<?> activity,
                                           @Nullable CharSequence message) {

        if (message == null) return null;
        String msg = message.toString().trim();

        if (msg.isEmpty()) return null;
        if (URLUtility.isValidURL(msg)) return null;

        ContextThemeWrapper themedContext =
                new ContextThemeWrapper(activity, R.style.style_application);

        LayoutInflater inflater = LayoutInflater.from(themedContext);
        View view = inflater.inflate(R.layout.layout_stylized_toast_1, null, false);

        Context applicationContext = activity.getApplicationContext();
        StylizedToastView toast = new StylizedToastView(applicationContext, view);
        toast.setMessage(message);
        toast.setDuration(LENGTH_LONG);

        return toast;
    }

    /**
     * Displays a neutral toast message with default styling (no icon, default colors).
     * This method creates a toast using the standard configuration without any
     * additional icon or color customization. If toast creation fails, the method
     * returns silently without showing anything.
     *
     * @param activity The base activity context used to create the toast. Must not be null.
     * @param message  The message to display. Must not be null.
     * @see #create(BaseActivity, CharSequence)
     */
    public static void show(@NonNull BaseActivity<?> activity,
                            @NonNull CharSequence message) {
        StylizedToastView toast = create(activity, message);
        if (toast != null) {
            toast.show();
        }
    }

    /**
     * Displays a success toast message with a check circle icon and success-themed
     * colors. The toast uses the success icon ({@link R.drawable#ic_check_circle}),
     * success icon tint ({@link R.color#style_color_success}), and success text color
     * ({@link R.color#style_color_success_text}).
     *
     * <p>This method is ideal for confirming successful operations such as form
     * submission, file uploads, or settings changes.
     *
     * @param activity The base activity context used to create the toast. Must not be null.
     * @param message  The success message to display. Must not be null.
     * @see #create(BaseActivity, CharSequence)
     * @see #showError(BaseActivity, CharSequence)
     * @see #showWarning(BaseActivity, CharSequence)
     */
    public static void showSuccess(@NonNull BaseActivity<?> activity,
                                   @NonNull CharSequence message) {
        StylizedToastView toast = create(activity, message);
        if (toast == null) return;

        toast.setIcon(R.drawable.ic_check_circle)
                .setIconTint(activity.getColor(R.color.style_color_success))
                .setTextColor(activity.getColor(R.color.style_color_success_text))
                .show();
    }

    /**
     * Displays an error toast message with a cancel circle icon and error-themed
     * colors. The toast uses the error icon ({@link R.drawable#ic_cancel_circle}),
     * error icon tint ({@link R.color#style_color_error}), and error text color
     * ({@link R.color#style_color_error_text}).
     *
     * <p>This method is ideal for notifying users about failed operations, invalid
     * input, network errors, or other exceptional conditions.
     *
     * @param activity The base activity context used to create the toast. Must not be null.
     * @param message  The error message to display. Must not be null.
     * @see #create(BaseActivity, CharSequence)
     * @see #showSuccess(BaseActivity, CharSequence)
     * @see #showWarning(BaseActivity, CharSequence)
     */
    public static void showError(@NonNull BaseActivity<?> activity,
                                 @NonNull CharSequence message) {
        StylizedToastView toast = create(activity, message);
        if (toast == null) return;

        toast.setIcon(R.drawable.ic_cancel_circle)
                .setIconTint(activity.getColor(R.color.style_color_error))
                .setTextColor(activity.getColor(R.color.style_color_error_text))
                .show();
    }


    /**
     * Displays a warning toast message with a standardized warning icon and colors.
     * The toast uses the warning icon ({@link R.drawable#ic_information}), a
     * warning-specific icon tint ({@link R.color#style_color_warning}), and
     * warning-specific text color ({@link R.color#style_color_warning_text}).
     *
     * <p>This method is a convenience wrapper around {@link #create(BaseActivity, CharSequence)}
     * that applies the warning styling automatically. If toast creation fails,
     * the method returns silently without showing anything.
     *
     * @param activity The base activity context used to create the toast. Must not be null.
     * @param message  The warning message to display. Must not be null.
     * @see #create(BaseActivity, CharSequence)
     * @see #showCustom(BaseActivity, CharSequence, int, int, int)
     */
    public static void showWarning(@NonNull BaseActivity<?> activity,
                                   @NonNull CharSequence message) {
        StylizedToastView toast = create(activity, message);
        if (toast == null) return;
        toast.setIcon(R.drawable.ic_information)
                .setIconTint(activity.getColor(R.color.style_color_warning))
                .setTextColor(R.color.style_color_warning_text)
                .show();
    }

    /**
     * Displays a fully customizable toast message with user-specified icon, icon
     * tint color, and text color. This method provides maximum flexibility for
     * scenarios where the standard success, error, warning, or info styles are
     * not sufficient.
     *
     * <p>If toast creation fails, the method returns silently without showing
     * anything. All color parameters should be provided as color integers
     * (e.g., from {@link android.content.Context#getColor(int)} or direct ARGB values).
     *
     * @param activity  The base activity context used to create the toast. Must not be null.
     * @param message   The message to display. Must not be null.
     * @param icon      The drawable resource ID for the toast icon.
     * @param iconTint  The color integer used to tint the icon.
     * @param textColor The color integer for the message text.
     * @see #create(BaseActivity, CharSequence)
     * @see #showSuccess(BaseActivity, CharSequence)
     * @see #showError(BaseActivity, CharSequence)
     * @see #showWarning(BaseActivity, CharSequence)
     */
    public static void showCustom(@NonNull BaseActivity<?> activity,
                                  @NonNull CharSequence message,
                                  @DrawableRes int icon,
                                  @ColorInt int iconTint,
                                  @ColorInt int textColor) {
        StylizedToastView toast = create(activity, message);
        if (toast == null) return;

        toast.setIcon(icon)
                .setIconTint(iconTint)
                .setTextColor(textColor)
                .show();
    }
}