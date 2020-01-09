package com.example.xyzreader.ui;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Objects;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.ShareCompat;
import android.support.v4.content.Loader;
import android.text.Html;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.github.florent37.picassopalette.PicassoPalette;
import com.squareup.picasso.Picasso;

/**
 * A fragment representing a single Article detail screen. This fragment is
 * either contained in a {@link ArticleListActivity} in two-pane mode (on
 * tablets) or a {@link ArticleDetailActivity} on handsets.
 */
public class ArticleDetailFragment extends Fragment implements
		LoaderManager.LoaderCallbacks<Cursor> {

	public static final String ARG_ITEM_ID = "item_id";
	private static final float PARALLAX_FACTOR = 1.25f;
	private static final String TAG = "ArticleDetailFragment";

	static float constrain(float val, float min, float max) {
		if (val < min) {
			return min;
		} else if (val > max) {
			return max;
		} else {
			return val;
		}
	}

	public static ArticleDetailFragment newInstance(long itemId) {
		Bundle arguments = new Bundle();
		arguments.putLong(ARG_ITEM_ID, itemId);
		ArticleDetailFragment fragment = new ArticleDetailFragment();
		fragment.setArguments(arguments);
		return fragment;
	}

	static float progress(float v, float min, float max) {
		return constrain((v - min) / (max - min), 0, 1);
	}

	// Most time functions can only handle 1902 - 2037
	private GregorianCalendar START_OF_EPOCH = new GregorianCalendar(2, 1, 1);
	private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss");
	private Cursor mCursor;
	private DrawInsetsFrameLayout mDrawInsetsFrameLayout;
	private boolean mIsCard = false;
	private long mItemId;
	private View mMetaBar;
	private int mMutedColor = 0xFF333333;
	private View mPhotoContainerView;
	private ImageView mPhotoView;
	private View mRootView;
	private ObservableScrollView mScrollView;
	private int mScrollY;
	private ColorDrawable mStatusBarColorDrawable;
	private int mStatusBarFullOpacityBottom;
	private int mTopInset;
	// Use default locale format
	private SimpleDateFormat outputFormat = new SimpleDateFormat();

	/**
	 * Mandatory empty constructor for the fragment manager to instantiate the
	 * fragment (e.g. upon screen orientation changes).
	 */
	public ArticleDetailFragment() {
	}

	private void bindViews() {
		if (mRootView == null) {
			return;
		}

		TextView titleView = mRootView.findViewById(R.id.article_title);
		TextView bylineView = mRootView.findViewById(R.id.article_byline);
		bylineView.setMovementMethod(new LinkMovementMethod());
		TextView bodyView = mRootView.findViewById(R.id.article_body);

		bodyView.setTypeface(Typeface.createFromAsset(getResources().getAssets(), "Rosario-Regular.ttf"));

		if (mCursor != null) {
			mRootView.setAlpha(0);
			mRootView.setVisibility(View.VISIBLE);
			mRootView.animate().alpha(1);
			titleView.setText(mCursor.getString(ArticleLoader.Query.TITLE));
			Date publishedDate = parsePublishedDate();
			if (!publishedDate.before(START_OF_EPOCH.getTime())) {
				bylineView.setText(Html.fromHtml(
						DateUtils.getRelativeTimeSpanString(
								publishedDate.getTime(),
								System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
								DateUtils.FORMAT_ABBREV_ALL).toString()
								+ " by <font color='#ffffff'>"
								+ mCursor.getString(ArticleLoader.Query.AUTHOR)
								+ "</font>"));

			} else {
				// If date is before 1902, just show the string
				bylineView.setText(Html.fromHtml(
						outputFormat.format(publishedDate) + " by <font color='#ffffff'>"
								+ mCursor.getString(ArticleLoader.Query.AUTHOR)
								+ "</font>"));

			}
			bodyView.setText(Html.fromHtml(mCursor.getString(ArticleLoader.Query.BODY).replaceAll("(\r\n|\n)", "<br />")));
			String url = mCursor.getString(ArticleLoader.Query.PHOTO_URL);
			Picasso.with(getActivity()).load(url).into(mPhotoView,
					PicassoPalette.with(url, mPhotoView)
							.use(PicassoPalette.Profile.MUTED_DARK)
							.intoBackground(mMetaBar)
							.intoTextColor(titleView, PicassoPalette.Swatch.TITLE_TEXT_COLOR)
			);
		} else {
			mRootView.setVisibility(View.GONE);
			titleView.setText("N/A");
			bylineView.setText("N/A");
			bodyView.setText("N/A");
		}
	}

	public ArticleDetailActivity getActivityCast() {
		return (ArticleDetailActivity) getActivity();
	}

	public int getUpButtonFloor() {
		if (mPhotoContainerView == null || mPhotoView.getHeight() == 0) {
			return Integer.MAX_VALUE;
		}

		// account for parallax
		return mIsCard
				? (int) mPhotoContainerView.getTranslationY() + mPhotoView.getHeight() - mScrollY
				: mPhotoView.getHeight() - mScrollY;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		// In support library r8, calling initLoader for a fragment in a FragmentPagerAdapter in
		// the fragment's onCreate may cause the same LoaderManager to be dealt to multiple
		// fragments because their mIndex is -1 (haven't been added to the activity yet). Thus,
		// we do this in onActivityCreated.
		getLoaderManager().initLoader(0, null, this);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (getArguments().containsKey(ARG_ITEM_ID)) {
			mItemId = getArguments().getLong(ARG_ITEM_ID);
		}

		mIsCard = getResources().getBoolean(R.bool.detail_is_card);
		mStatusBarFullOpacityBottom = getResources().getDimensionPixelSize(
				R.dimen.detail_card_top_margin);
		setHasOptionsMenu(true);
	}

	@Override
	public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
		return ArticleLoader.newInstanceForItemId(getActivity(), mItemId);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		mRootView = inflater.inflate(R.layout.fragment_article_detail, container, false);
		mDrawInsetsFrameLayout = mRootView.findViewById(R.id.draw_insets_frame_layout);
		mDrawInsetsFrameLayout.setOnInsetsCallback(new DrawInsetsFrameLayout.OnInsetsCallback() {
			@Override
			public void onInsetsChanged(Rect insets) {
				mTopInset = insets.top;
			}
		});

		mScrollView = mRootView.findViewById(R.id.scrollview);
		mScrollView.setCallbacks(new ObservableScrollView.Callbacks() {
			@Override
			public void onScrollChanged() {
				mScrollY = mScrollView.getScrollY();
				getActivityCast().onUpButtonFloorChanged(mItemId, ArticleDetailFragment.this);
				mPhotoContainerView.setTranslationY((int) (mScrollY - mScrollY / PARALLAX_FACTOR));
				updateStatusBar();
			}
		});
		mPhotoView = mRootView.findViewById(R.id.photo);
		mPhotoContainerView = mRootView.findViewById(R.id.photo_container);
		mStatusBarColorDrawable = new ColorDrawable(0);
		mRootView.findViewById(R.id.share_fab).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				startActivity(Intent.createChooser(ShareCompat.IntentBuilder.from(getActivity())
						.setType("text/plain")
						.setText("Hey! I'm reading this book: " + getTitleString())
						.getIntent(), getString(R.string.action_share)));
			}

			private String getTitleString() {
				return ((TextView) mRootView.findViewById(R.id.article_title)).getText().toString();
			}
		});

		mMetaBar = mRootView.findViewById(R.id.meta_bar);
		bindViews();
		updateStatusBar();
		return mRootView;
	}

	@Override
	public void onLoadFinished(android.support.v4.content.Loader<Cursor> cursorLoader, Cursor cursor) {
		if (!isAdded()) {
			if (cursor != null) {
				cursor.close();
			}
			return;
		}

		mCursor = cursor;
		if (mCursor != null && !mCursor.moveToFirst()) {
			Log.e(TAG, "Error reading item detail cursor");
			mCursor.close();
			mCursor = null;
		}

		bindViews();
	}

	@Override
	public void onLoaderReset(android.support.v4.content.Loader<Cursor> cursorLoader) {
		mCursor = null;
		bindViews();
	}

	private Date parsePublishedDate() {
		try {
			String date = mCursor.getString(ArticleLoader.Query.PUBLISHED_DATE);
			return dateFormat.parse(date);
		} catch (ParseException ex) {
			Log.e(TAG, Objects.requireNonNull(ex.getMessage()));
			Log.i(TAG, "passing today's date");
			return new Date();
		}
	}

	private void updateStatusBar() {
		int color = 0;
		if (mPhotoView != null && mTopInset != 0 && mScrollY > 0) {
			float f = progress(mScrollY,
					mStatusBarFullOpacityBottom - mTopInset * 3,
					mStatusBarFullOpacityBottom - mTopInset);
			color = Color.argb((int) (255 * f),
					(int) (Color.red(mMutedColor) * 0.9),
					(int) (Color.green(mMutedColor) * 0.9),
					(int) (Color.blue(mMutedColor) * 0.9));
		}
		mStatusBarColorDrawable.setColor(color);
		mDrawInsetsFrameLayout.setInsetBackground(mStatusBarColorDrawable);
	}
}
