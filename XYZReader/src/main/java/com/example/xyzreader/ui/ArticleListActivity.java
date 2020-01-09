package com.example.xyzreader.ui;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Objects;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.ItemsContract;
import com.example.xyzreader.data.UpdaterService;

/**
 * An activity representing a list of Articles. This activity has different presentations for
 * handset and tablet-size devices. On handsets, the activity presents a list of items, which when
 * touched, lead to a {@link ArticleDetailActivity} representing item details. On tablets, the
 * activity presents a grid of items as cards.
 */
public class ArticleListActivity extends AppCompatActivity implements
        LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = ArticleListActivity.class.toString();
    private Toolbar mToolbar;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mRecyclerView;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss");
    // Use default locale format
    private SimpleDateFormat outputFormat = new SimpleDateFormat();
    // Most time functions can only handle 1902 - 2037
    private GregorianCalendar START_OF_EPOCH = new GregorianCalendar(2,1,1);

	private class ArticleListAdapter extends RecyclerView.Adapter<ArticleViewHolder> {

		private Cursor mCursor;

		ArticleListAdapter(Cursor cursor) {
			mCursor = cursor;
		}

		@Override
		public int getItemCount() {
			return mCursor.getCount();
		}

		@Override
		public long getItemId(int position) {
			mCursor.moveToPosition(position);
			return mCursor.getLong(ArticleLoader.Query._ID);
		}

		@Override
		public void onBindViewHolder(ArticleViewHolder holder, int position) {
			mCursor.moveToPosition(position);
			holder.titleView.setText(mCursor.getString(ArticleLoader.Query.TITLE));
			Date publishedDate = parsePublishedDate();
			if (!publishedDate.before(START_OF_EPOCH.getTime())) {
				holder.subtitleView.setText(Html.fromHtml(
						DateUtils.getRelativeTimeSpanString(
								publishedDate.getTime(),
								System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
								DateUtils.FORMAT_ABBREV_ALL).toString()
								+ "<br/>" + " by "
								+ mCursor.getString(ArticleLoader.Query.AUTHOR)));
			} else {
				holder.subtitleView.setText(Html.fromHtml(
						outputFormat.format(publishedDate)
								+ "<br/>" + " by "
								+ mCursor.getString(ArticleLoader.Query.AUTHOR)));
			}
			holder.thumbnailView.setImageUrl(
					mCursor.getString(ArticleLoader.Query.THUMB_URL),
					ImageLoaderHelper.getInstance(ArticleListActivity.this).getImageLoader());
			holder.thumbnailView.setAspectRatio(mCursor.getFloat(ArticleLoader.Query.ASPECT_RATIO));
		}

		@Override
		public ArticleViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			View view = getLayoutInflater().inflate(R.layout.list_item_article, parent, false);
			final ArticleViewHolder holder = new ArticleViewHolder(view);
			view.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					startActivity(new Intent(Intent.ACTION_VIEW,
							ItemsContract.Items.buildItemUri(getItemId(holder.getAdapterPosition()))));
				}
			});
			return holder;
		}

		private Date parsePublishedDate() {
			try {
				String date = mCursor.getString(ArticleLoader.Query.PUBLISHED_DATE);
				return dateFormat.parse(date);
			} catch (ParseException ex) {
				Log.e(TAG, ex.getMessage());
				Log.i(TAG, "passing today's date");
				return new Date();
			}
		}
	}

	static class ArticleViewHolder extends RecyclerView.ViewHolder {

		TextView subtitleView;
		DynamicHeightNetworkImageView thumbnailView;
		TextView titleView;

		ArticleViewHolder(View view) {
			super(view);
			thumbnailView = view.findViewById(R.id.thumbnail);
			titleView = view.findViewById(R.id.article_title);
			subtitleView = view.findViewById(R.id.article_subtitle);
		}
	}

	private BroadcastReceiver mRefreshingReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (UpdaterService.BROADCAST_ACTION_STATE_CHANGE.equals(intent.getAction())) {
				mIsRefreshing = intent.getBooleanExtra(UpdaterService.EXTRA_REFRESHING, false);
				updateSwipeRefreshLayout();
			}
		}

		private void updateSwipeRefreshLayout() {
			mSwipeRefreshLayout.setRefreshing(mIsRefreshing);
		}
	};

	boolean isNetworkConnected() {
		ConnectivityManager connectivityManager = (ConnectivityManager)
				getBaseContext().getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = Objects.requireNonNull(connectivityManager).getActiveNetworkInfo();
		return networkInfo != null && networkInfo.isConnected();
	}

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mRefreshingReceiver,
                new IntentFilter(UpdaterService.BROADCAST_ACTION_STATE_CHANGE));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mRefreshingReceiver);
    }

    private boolean mIsRefreshing = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_article_list);
		mToolbar = (Toolbar) findViewById(R.id.toolbar);
		mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
		mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
		getSupportLoaderManager().initLoader(0, null, this);

		if (savedInstanceState == null) {
			refresh();
        }
    }

	@Override
	public android.support.v4.content.Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
		return ArticleLoader.newAllArticlesInstance(this);
	}

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
			ArticleListAdapter adapter = new ArticleListAdapter(cursor);
        adapter.setHasStableIds(true);
        mRecyclerView.setAdapter(adapter);
			mRecyclerView.setHasFixedSize(true);
        int columnCount = getResources().getInteger(R.integer.list_column_count);
			mRecyclerView.setLayoutManager(new GridLayoutManager(getBaseContext(), columnCount));
    }

	@Override
	public void onLoaderReset(android.support.v4.content.Loader<Cursor> loader) {
		mRecyclerView.setAdapter(null);
    }

	@Override
	protected void onResume() {
		super.onResume();
		if (!isNetworkConnected()) {
			Snackbar.make(findViewById(R.id.swipe_refresh_layout), "Please check your network connection",
					Snackbar.LENGTH_LONG).show();
		}
	}

	private void refresh() {
		startService(new Intent(this, UpdaterService.class));
    }
}
