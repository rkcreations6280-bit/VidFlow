package userInterface.languagePicker;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import coreUtils.library.process.LoggerUtils;

/**
 * ViewModel for the language selection screen. This class is responsible for
 * preparing and managing the list of available languages for the UI layer
 * (typically {@link LanguageActivity}). It separates language data loading
 * logic from the Activity, ensuring data survives configuration changes
 * such as screen rotations.
 *
 * <p><strong>Core responsibilities:</strong>
 * <ul>
 * <li>Loads the list of supported languages via {@link #loadLanguages()}.</li>
 * <li>Exposes the language list as a {@link LiveData} for UI observation.</li>
 * <li>Maintains the language data across Activity lifecycle events.</li>
 * </ul>
 *
 * <p>The ViewModel is scoped to the Activity's lifecycle and is typically
 * obtained using {@link androidx.lifecycle.ViewModelProvider}. The language
 * list is loaded once during ViewModel construction and retained until the
 * associated Activity is permanently destroyed.
 *
 * @see ViewModel
 * @see LanguageItem
 * @see #getLanguages()
 * @see #loadLanguages()
 */
public class LanguageViewModel extends ViewModel {

    private final LoggerUtils logger = LoggerUtils.from(getClass());
    private final MutableLiveData<List<LanguageItem>> languages = new MutableLiveData<>();

    /**
     * Constructs a new LanguageViewModel instance. Upon creation, this constructor
     * immediately triggers the loading of available language data by calling
     * {@link #loadLanguages()}. The ViewModel is typically instantiated via
     * {@link androidx.lifecycle.ViewModelProvider} in the associated Activity.
     *
     * <p>The loaded language list is stored internally as a {@code MutableLiveData}
     * and exposed via {@link #getLanguages()} for observation by the UI layer.
     *
     * @see #loadLanguages()
     * @see #getLanguages()
     */
    public LanguageViewModel() {
        loadLanguages();
    }

    /**
     * Returns a LiveData object that holds the list of available languages for
     * selection. The UI layer (typically {@link LanguageActivity}) observes this
     * LiveData to receive updates when the language list is loaded or modified.
     *
     * <p>The returned LiveData is immutable from the observer's perspective,
     * ensuring that the UI can only read the data while the ViewModel retains
     * control over updates. Language items are populated asynchronously via
     * {@link #loadLanguages()}, and the LiveData automatically notifies
     * observers when the data becomes available.
     *
     * @return A LiveData containing the current list of {@link LanguageItem} objects.
     * Never returns {@code null}, though the list may be empty during
     * initial loading.
     * @see #loadLanguages()
     * @see LanguageItem
     */
    public LiveData<List<LanguageItem>> getLanguages() {
        return languages;
    }

    /**
     * Loads the complete list of supported languages into the LiveData container.
     * This method creates a collection of 15 {@link LanguageItem} objects,
     * including English and 14 major Indian languages, then posts the list to
     * the {@code languages} MutableLiveData for observation by the UI layer.
     *
     * <p><strong>Languages included:</strong>
     * English, Hindi, Tamil, Telugu, Punjabi, Marathi, Gujarati, Malayalam,
     * Bengali, Odia, Kannada, Assamese, Bhojpuri, Haryanvi, Rajasthani.
     *
     * <p>This method is called once during ViewModel initialization. The list
     * is immutable after creation and is not modified during the ViewModel's
     * lifecycle. Observers receive this list via {@link #getLanguages()}.
     *
     * @see LanguageItem
     * @see #languages
     * @see #getLanguages()
     */
    private void loadLanguages() {
        List<LanguageItem> list = new ArrayList<>();

        list.add(new LanguageItem("English (Default)", "en"));
        list.add(new LanguageItem("हिन्दी (Hindi)", "hi"));
        list.add(new LanguageItem("தமிழ் (Tamil)", "ta"));
        list.add(new LanguageItem("తెలుగు (Telugu)", "te"));
        list.add(new LanguageItem("ਪੰਜਾਬੀ (Punjabi)", "pa"));
        list.add(new LanguageItem("मराठी (Marathi)", "mr"));
        list.add(new LanguageItem("ગુજરાતી (Gujarati)", "gu"));
        list.add(new LanguageItem("മലയാളം (Malayalam)", "ml"));
        list.add(new LanguageItem("বাংলা (Bengali)", "bn"));
        list.add(new LanguageItem("ଓଡ଼ିଆ (Odia)", "or"));
        list.add(new LanguageItem("ಕನ್ನಡ (Kannada)", "kn"));
        list.add(new LanguageItem("অসমীয়া (Assamese)", "as"));
        list.add(new LanguageItem("भोजपुरी (Bhojpuri)", "bho"));
        list.add(new LanguageItem("हरियाणवी (Haryanvi)", "hry"));
        list.add(new LanguageItem("राजस्थानी (Rajasthani)", "raj"));

        languages.setValue(list);
    }
}