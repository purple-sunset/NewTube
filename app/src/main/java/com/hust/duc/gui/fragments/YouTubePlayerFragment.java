package com.hust.duc.gui.fragments;

import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.bumptech.glide.Glide;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

import com.hust.duc.R;
import com.hust.duc.businessobjects.AsyncTaskParallel;
import com.hust.duc.businessobjects.GetVideoDescription;
import com.hust.duc.businessobjects.GetVideosDetailsByIDs;
import com.hust.duc.businessobjects.VideoStream.StreamMetaData;
import com.hust.duc.businessobjects.VideoStream.StreamMetaDataList;
import com.hust.duc.businessobjects.YouTubeChannel;
import com.hust.duc.businessobjects.YouTubeVideo;
import com.hust.duc.businessobjects.db.BookmarksDb;
import com.hust.duc.businessobjects.db.CheckIfUserSubbedToChannelTask;
import com.hust.duc.businessobjects.db.SubscribeToChannelTask;
import com.hust.duc.gui.activities.FragmentHolderActivity;
import com.hust.duc.gui.app.NewTubeApp;
import com.hust.duc.gui.businessobjects.CommentsAdapter;
import com.hust.duc.gui.businessobjects.FragmentEx;
import com.hust.duc.gui.businessobjects.MediaControllerEx;
import com.hust.duc.gui.businessobjects.SubscribeButton;
import hollowsoft.slidingdrawer.OnDrawerOpenListener;
import hollowsoft.slidingdrawer.SlidingDrawer;

import static android.content.Context.DOWNLOAD_SERVICE;

/**
 * A fragment that holds a standalone YouTube player.
 */
public class YouTubePlayerFragment extends FragmentEx implements MediaPlayer.OnPreparedListener {

	public static final String YOUTUBE_VIDEO_OBJ = "YouTubePlayerFragment.yt_video_obj";

	private YouTubeVideo		youTubeVideo = null;
	private YouTubeChannel		youTubeChannel = null;

	private VideoView			videoView = null;
	/** The current video position (i.e. play time). */
	private int					videoCurrentPosition = 0;
	private MediaControllerEx	mediaController = null;

	private TextView			videoDescTitleTextView = null;
	private ImageView			videoDescChannelThumbnailImageView = null;
	private TextView			videoDescChannelTextView = null;
	private SubscribeButton		videoDescSubscribeButton = null;
	private TextView			videoDescViewsTextView = null;
	private ProgressBar			videoDescLikesBar = null;
	private TextView			videoDescLikesTextView = null;
	private TextView			videoDescDislikesTextView = null;
	private View                videoDescRatingsDisabledTextView = null;
	private TextView			videoDescPublishDateTextView = null;
	private TextView			videoDescriptionTextView = null;
	private View				voidView = null;
	private View				loadingVideoView = null;

	private SlidingDrawer		videoDescriptionDrawer = null;
	private SlidingDrawer		commentsDrawer = null;
	private View				commentsProgressBar = null,
								noVideoCommentsView = null;
	private CommentsAdapter		commentsAdapter = null;
	private ExpandableListView	commentsExpandableListView = null;

	private Menu                menu = null;

	private Handler				timerHandler = null;

	StreamMetaDataList downloadList;
	private DownloadManager downloadManager;

	/** Timeout (in milliseconds) before the HUD (i.e. media controller + action/title bar) is hidden */
	private static final int HUD_VISIBILITY_TIMEOUT = 5000;
	private static final String VIDEO_CURRENT_POSITION = "YouTubePlayerFragment.VideoCurrentPosition";
	private static final String TAG = YouTubePlayerFragment.class.getSimpleName();


	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		// inflate the layout for this fragment
		View view = inflater.inflate(R.layout.fragment_youtube_player, container, false);

		// indicate that this fragment has an action bar menu
		setHasOptionsMenu(true);

		if (savedInstanceState != null)
			videoCurrentPosition = savedInstanceState.getInt(VIDEO_CURRENT_POSITION, 0);

		if (youTubeVideo == null) {
			loadingVideoView = view.findViewById(R.id.loadingVideoView);

			videoView = (VideoView) view.findViewById(R.id.video_view);
			// play the video once its loaded
			videoView.setOnPreparedListener(this);

			// setup the media controller (will control the video playing/pausing)
			mediaController = new MediaControllerEx(getActivity(), videoView);

			voidView = view.findViewById(R.id.void_view);
			voidView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showOrHideHud();
				}
			});

			videoDescriptionDrawer = (SlidingDrawer) view.findViewById(R.id.des_drawer);
			videoDescTitleTextView = (TextView) view.findViewById(R.id.video_desc_title);
			videoDescChannelThumbnailImageView = (ImageView) view.findViewById(R.id.video_desc_channel_thumbnail_image_view);
			videoDescChannelThumbnailImageView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (youTubeChannel != null) {
						Intent i = new Intent(getActivity(), FragmentHolderActivity.class);
						i.putExtra(FragmentHolderActivity.FRAGMENT_HOLDER_CHANNEL_BROWSER, true);
						i.putExtra(ChannelBrowserFragment.CHANNEL_OBJ, youTubeChannel);
						startActivity(i);
					}
				}
			});
			videoDescChannelTextView = (TextView) view.findViewById(R.id.video_desc_channel);
			videoDescViewsTextView = (TextView) view.findViewById(R.id.video_desc_views);
			videoDescLikesTextView = (TextView) view.findViewById(R.id.video_desc_likes);
			videoDescDislikesTextView = (TextView) view.findViewById(R.id.video_desc_dislikes);
			videoDescRatingsDisabledTextView = view.findViewById(R.id.video_desc_ratings_disabled);
			videoDescPublishDateTextView = (TextView) view.findViewById(R.id.video_desc_publish_date);
			videoDescriptionTextView = (TextView) view.findViewById(R.id.video_desc_description);
			videoDescLikesBar = (ProgressBar) view.findViewById(R.id.video_desc_likes_bar);
			videoDescSubscribeButton = (SubscribeButton) view.findViewById(R.id.video_desc_subscribe_button);
			videoDescSubscribeButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					// subscribe / unsubscribe to this video's channel
					new SubscribeToChannelTask(videoDescSubscribeButton, youTubeChannel).execute();
				}
			});

			commentsExpandableListView = (ExpandableListView) view.findViewById(R.id.commentsExpandableListView);
			commentsProgressBar = view.findViewById(R.id.comments_progress_bar);
			noVideoCommentsView = view.findViewById(R.id.no_video_comments_text_view);
			commentsDrawer = (SlidingDrawer) view.findViewById(R.id.comments_drawer);
			commentsDrawer.setOnDrawerOpenListener(new OnDrawerOpenListener() {
				@Override
				public void onDrawerOpened() {
					if (commentsAdapter == null) {
						commentsAdapter = new CommentsAdapter(getActivity(), youTubeVideo.getId(), commentsExpandableListView, commentsProgressBar, noVideoCommentsView);
					}
				}
			});

			// hide action bar
			getSupportActionBar().hide();

			// get which video we need to play...
			Bundle bundle = getActivity().getIntent().getExtras();
			if (bundle != null  &&  bundle.getSerializable(YOUTUBE_VIDEO_OBJ) != null) {
				// ... either the video details are passed through the previous activity
				youTubeVideo = (YouTubeVideo) bundle.getSerializable(YOUTUBE_VIDEO_OBJ);
				setUpHUDAndPlayVideo();

				getVideoInfoTasks();
			} else {
				// ... or the video URL is passed to SkyTube via another Android app
				new GetVideoDetailsTask().executeInParallel();
			}
		}

		downloadManager = (DownloadManager) getContext().getSystemService(DOWNLOAD_SERVICE);
		return view;
	}


	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(VIDEO_CURRENT_POSITION, videoCurrentPosition);
	}


	/**
	 * Will asynchronously retrieve additional video information such as channgel avatar ...etc
	 */
	private void getVideoInfoTasks() {
		// get Channel info (e.g. avatar...etc) task
		new GetYouTubeChannelInfoTask().executeInParallel(youTubeVideo.getChannelId());

		// check if the user has subscribed to a channel... if he has, then change the state of
		// the subscribe button
		new CheckIfUserSubbedToChannelTask(videoDescSubscribeButton, youTubeVideo.getChannelId()).execute();
	}


	/**
	 * Will setup the HUD's details according to the contents of {@link #youTubeVideo}.  Then it
	 * will try to load and play the video.
	 */
	private void setUpHUDAndPlayVideo() {
		videoDescTitleTextView.setText(youTubeVideo.getTitle());
		videoDescChannelTextView.setText(youTubeVideo.getChannelName());
		videoDescViewsTextView.setText(youTubeVideo.getViewsCount());
		videoDescPublishDateTextView.setText(youTubeVideo.getPublishDatePretty());

		if (youTubeVideo.isThumbsUpPercentageSet()) {
			videoDescLikesTextView.setText(youTubeVideo.getLikeCount());
			videoDescDislikesTextView.setText(youTubeVideo.getDislikeCount());
			videoDescLikesBar.setProgress(youTubeVideo.getThumbsUpPercentage());
		} else {
			videoDescLikesTextView.setVisibility(View.GONE);
			videoDescDislikesTextView.setVisibility(View.GONE);
			videoDescLikesBar.setVisibility(View.GONE);
			videoDescRatingsDisabledTextView.setVisibility(View.VISIBLE);
		}

		// load the video
		loadVideo();
	}



	@Override
	public void onPrepared(MediaPlayer mediaPlayer) {
		loadingVideoView.setVisibility(View.GONE);
		videoView.seekTo(videoCurrentPosition);
		videoView.start();
		showHud();
	}



	@Override
	public void onPause() {
		if (videoView != null && videoView.isPlaying()) {
			videoCurrentPosition = videoView.getCurrentPosition();
		}

		super.onPause();
	}



	/**
	 * @return True if the HUD is visible (provided that this Fragment is also visible).
	 */
	private boolean isHudVisible() {
		return isVisible()  &&  (mediaController.isShowing()  ||  getSupportActionBar().isShowing());
	}



	/**
	 * Hide or display the HUD depending if the HUD is currently visible or not.
	 */
	private void showOrHideHud() {
		if (isHudVisible())
			hideHud();
		else
			showHud();
	}



	/**
	 * Show the HUD (head-up display), i.e. the Action Bar and Media Controller.
	 */
	private void showHud() {
		if (!isHudVisible()) {
			getSupportActionBar().show();
			getSupportActionBar().setTitle(youTubeVideo != null ? youTubeVideo.getTitle() : "");
			mediaController.show(0);

			videoDescriptionDrawer.close();
			videoDescriptionDrawer.setVisibility(View.INVISIBLE);
			commentsDrawer.close();
			commentsDrawer.setVisibility(View.INVISIBLE);

			// hide UI after a certain timeout (defined in HUD_VISIBILITY_TIMEOUT)
			timerHandler = new Handler();
			timerHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					hideHud();
					timerHandler = null;
				}
			}, HUD_VISIBILITY_TIMEOUT);
		}
	}



	/**
	 * Hide the HUD.
	 */
	private void hideHud() {
		if (isHudVisible()) {
			getSupportActionBar().hide();
			mediaController.hideController();

			videoDescriptionDrawer.setVisibility(View.VISIBLE);
			commentsDrawer.setVisibility(View.VISIBLE);

			// If there is a timerHandler running, then cancel it (stop if from running).  This way,
			// if the HUD was hidden on the 5th second, and the user reopens the HUD, this code will
			// prevent the HUD to re-disappear 2 seconds after it was displayed (assuming that
			// HUD_VISIBILITY_TIMEOUT = 5 seconds).
			if (timerHandler != null) {
				timerHandler.removeCallbacksAndMessages(null);
				timerHandler = null;
			}
		}
	}


	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.menu_youtube_player, menu);

		this.menu = menu;

		// Will now check if the video is bookmarked or not (and then update the menu accordingly).
		//
		// youTubeVideo might be null if we have only passed the video URL to this fragment (i.e.
		// the app is still trying to construct youTubeVideo in the background).
		if (youTubeVideo != null)
			new IsVideoBookmarkedTask().executeInParallel();
	}


	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_reload_video:
				// get a new video steam (as the current one might be performing poorly)
				new GetStreamTask(youTubeVideo, true).executeInParallel();
				return true;

			case R.id.menu_open_video_with:
				playVideoExternally();
				videoView.pause();
				return true;

			case R.id.share:
				Intent intent = new Intent(android.content.Intent.ACTION_SEND);
				intent.setType("text/plain");
				intent.putExtra(android.content.Intent.EXTRA_TEXT, youTubeVideo.getVideoUrl());
				startActivity(Intent.createChooser(intent, getString(R.string.share_via)));
				return true;

			case R.id.copyurl:
				ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
				ClipData clip = ClipData.newPlainText("Video URL", youTubeVideo.getVideoUrl());
				clipboard.setPrimaryClip(clip);
				Toast.makeText(getContext(), R.string.url_copied_to_clipboard, Toast.LENGTH_SHORT).show();
				return true;

			case R.id.bookmark_video:
				boolean successBookmark = BookmarksDb.getBookmarksDb().add(youTubeVideo);
				Toast.makeText(getContext(),
						successBookmark  ?  R.string.video_bookmarked  :  R.string.video_bookmarked_error,
						Toast.LENGTH_LONG).show();

				if (successBookmark) {
					menu.findItem(R.id.bookmark_video).setVisible(false);
					menu.findItem(R.id.unbookmark_video).setVisible(true);
				}
				return true;

			case R.id.unbookmark_video:
				boolean successUnbookmark = BookmarksDb.getBookmarksDb().remove(youTubeVideo);
				Toast.makeText(getContext(),
						successUnbookmark  ?  R.string.video_unbookmarked  :  R.string.video_unbookmarked_error,
						Toast.LENGTH_LONG).show();

				if (successUnbookmark) {
					menu.findItem(R.id.bookmark_video).setVisible(true);
					menu.findItem(R.id.unbookmark_video).setVisible(false);
				}
				return true;

			case R.id.download_video:
				final ArrayList mSelectedItems = new ArrayList();  // Where we track the selected items
				final String[] list = downloadList.getDownloadList();

				final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
				// Set the dialog title
				builder.setTitle(R.string.choose_resolution)
						// Specify the list array, the items to be selected by default (null for none),
						// and the listener through which to receive callbacks when items are selected
						.setMultiChoiceItems(list, null,
								new DialogInterface.OnMultiChoiceClickListener() {
									@Override
									public void onClick(DialogInterface dialog, int which,
														boolean isChecked) {
										if (isChecked) {
											// If the user checked the item, add it to the selected items
											mSelectedItems.add(which);
										} else if (mSelectedItems.contains(which)) {
											// Else, if the item is already in the array, remove it
											mSelectedItems.remove(Integer.valueOf(which));
										}
									}
								})
						// Set the action buttons
						.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {

								for (Object i: mSelectedItems
									 ) {
									String name = youTubeVideo.getTitle() + " " + downloadList.get(((int) i)).getResolution().toString();
									switch (downloadList.get(((int) i)).getFormat()) {
										case MPEG_4:
											name+=".mp4";
											break;
										case WEBM:
											name+=".webm";
											break;
										case V3GPP:
											name+=".3gp";
											break;
										default:
											name+=".mp4";
											break;
									}

									DownloadManager.Request request = new DownloadManager.Request(downloadList.get(((int) i)).getUri());
									request.setDescription("Video " + downloadList.get(((int) i)).getResolution().toString())
											.setTitle(name);

									request.setDestinationInExternalFilesDir(getContext(), Environment.DIRECTORY_MOVIES, name);
									request.setVisibleInDownloadsUi(true);
									request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI| DownloadManager.Request.NETWORK_MOBILE);
									downloadManager.enqueue(request);

									Toast.makeText(getContext(),
											"Download" + name,
											Toast.LENGTH_LONG).show();
								}
								//downloadManager.notifyAll();
								dialog.dismiss();

							}
						})
						.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								dialog.dismiss();
							}
						});

				builder.show();
				return true;

			default:
				return super.onOptionsItemSelected(item);
		}
	}


	/**
	 * Play the video using an external app
	 */
	private void playVideoExternally() {
		Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(youTubeVideo.getVideoUrl()));
		startActivity(browserIntent);
	}


	/**
	 * Loads the video specified in {@link #youTubeVideo}.
	 */
	private void loadVideo() {
		// if the video is NOT live
		if (!youTubeVideo.isLiveStream()) {
			// get the video's steam
			new GetStreamTask(youTubeVideo).executeInParallel();
			// get the video description
			new GetVideoDescriptionTask().executeInParallel();
		} else {
			// video is live:  ask the user if he wants to play the video using an other app
			new AlertDialog.Builder(getContext())
					.setMessage(R.string.warning_live_video)
					.setTitle(R.string.error_video_play)
					.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							closeActivity();
						}
					})
					.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							playVideoExternally();
							closeActivity();
						}
					})
					.show();
		}
	}


	////////////////////////////////////////////////////////////////////////////////////////////////


	/**
	 * Given a YouTubeVideo, it will asynchronously get a list of streams (supplied by YouTube) and
	 * then it asks the videoView to start playing a stream.
	 */
	private class GetStreamTask extends AsyncTaskParallel<Void, Exception, StreamMetaDataList> {

		/** YouTube Video */
		private YouTubeVideo	youTubeVideo;


		/**
		 * Returns a stream for the given video.
		 *
		 * @param youTubeVideo  YouTube video.
		 */
		public GetStreamTask(YouTubeVideo youTubeVideo) {
			this(youTubeVideo, false);
		}


		/**
		 * Returns a stream for the given video.  If getNewStream is set to true, then it will stop
		 * the current video, get a NEW stream and then resume playing.
		 *
		 * @param youTubeVideo	YouTube video.
		 * @param getNewStream	Set to true to stop the current video from playing and get a new
		 *                      video stream.
		 */
		public GetStreamTask(YouTubeVideo youTubeVideo, boolean getNewStream) {
			this.youTubeVideo = youTubeVideo;

			if (getNewStream) {
				boolean isVideoPlaying = videoView.isPlaying();

				videoView.pause();
				videoCurrentPosition = isVideoPlaying ? videoView.getCurrentPosition() : 0;
				videoView.stopPlayback();
				loadingVideoView.setVisibility(View.VISIBLE);
			}
		}


		@Override
		protected StreamMetaDataList doInBackground(Void... param) {
			return youTubeVideo.getVideoStreamList();
		}


		@Override
		protected void onPostExecute(StreamMetaDataList streamMetaDataList) {
			String errorMessage = null;

			if (streamMetaDataList.getErrorMessage() != null) {
				// if the stream list is null, then it means an error has occurred
				errorMessage = streamMetaDataList.getErrorMessage();
			} else if (streamMetaDataList.size() <= 0) {
				// if steam list if empty, then it means something went wrong...
				errorMessage = String.format(getActivity().getString(R.string.error_video_streams_empty), youTubeVideo.getId());
			} else {
				Log.i(TAG, streamMetaDataList.toString());

				// get the desired stream based on user preferences
				StreamMetaData desiredStream = streamMetaDataList.getDesiredStream();

				// play the video
				Log.i(TAG, ">> PLAYING: " + desiredStream);
				videoView.setVideoURI(desiredStream.getUri());

				//get download list
				downloadList = streamMetaDataList;
			}

			if (errorMessage != null) {
				new AlertDialog.Builder(getContext())
					.setMessage(errorMessage)
					.setTitle(R.string.error_video_play)
					.setCancelable(false)
					.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							closeActivity();
						}
					})
					.show();
			}
		}
	}

	////////////////////////////////////////////////////////////////////////////////////////////////


	/**
	 * Get the video's description and set the appropriate text view.
	 */
	private class GetVideoDescriptionTask extends AsyncTaskParallel<Void, Void, String> {

		@Override
		protected String doInBackground(Void... params) {
			GetVideoDescription getVideoDescription = new GetVideoDescription();
			String description = NewTubeApp.getStr(R.string.error_get_video_desc);

			try {
				getVideoDescription.init(youTubeVideo.getId());
				List<YouTubeVideo> list = getVideoDescription.getNextVideos();

				if (list.size() > 0) {
					description = list.get(0).getDescription();
				}
			} catch (IOException e) {
				Log.e(TAG, description + " - id=" + youTubeVideo.getId(), e);
			}

			return description;
		}

		@Override
		protected void onPostExecute(String description) {
			videoDescriptionTextView.setText(description);
		}

	}


	////////////////////////////////////////////////////////////////////////////////////////////////


	/**
	 * This task will, from the given video URL, get the details of the video (e.g. video name,
	 * likes ...etc).
	 */
	private class GetVideoDetailsTask extends AsyncTaskParallel<Void, Void, YouTubeVideo> {

		private String videoUrl = null;


		@Override
		protected void onPreExecute() {
			String url = getUrlFromIntent(getActivity().getIntent());

			try {
				// YouTube sends subscriptions updates email in which its videos' URL are encoded...
				// Hence we need to decode them first...
				videoUrl = URLDecoder.decode(url, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				Log.e(TAG, "UnsupportedEncodingException on " + videoUrl + " encoding = UTF-8", e);
				videoUrl = url;
			}
		}


		/**
		 * Returns an instance of {@link YouTubeVideo} from the given {@link #videoUrl}.
		 *
		 * @return {@link YouTubeVideo}; null if an error has occurred.
		 */
		@Override
		protected YouTubeVideo doInBackground(Void... params) {
			String videoId = YouTubeVideo.getYouTubeIdFromUrl(videoUrl);
			YouTubeVideo youTubeVideo = null;

			if (videoId != null) {
				try {
					GetVideosDetailsByIDs getVideo = new GetVideosDetailsByIDs();
					getVideo.init(videoId);
					List<YouTubeVideo> youTubeVideos = getVideo.getNextVideos();

					if (youTubeVideos.size() > 0)
						youTubeVideo = youTubeVideos.get(0);
				} catch (IOException ex) {
					Log.e(TAG, "Unable to get video details, where id="+videoId, ex);
				}
			}

			return youTubeVideo;
		}


		@Override
		protected void onPostExecute(YouTubeVideo youTubeVideo) {
			if (youTubeVideo == null) {
				// invalid URL error (i.e. we are unable to decode the URL)
				String err = String.format(getString(R.string.error_invalid_url), videoUrl);
				Toast.makeText(getActivity(), err, Toast.LENGTH_LONG).show();

				// log error
				Log.e(TAG, err);

				// close the video player activity
				closeActivity();
			} else {
				YouTubePlayerFragment.this.youTubeVideo = youTubeVideo;

				// setup the HUD and play the video
				setUpHUDAndPlayVideo();

				getVideoInfoTasks();

				// will now check if the video is bookmarked or not (and then update the menu
				// accordingly)
				new IsVideoBookmarkedTask().executeInParallel();
			}
		}


		/**
		 * The video URL is passed to SkyTube via another Android app (i.e. via an intent).
		 *
		 * @return The URL of the YouTube video the user wants to play.
		 */
		private String getUrlFromIntent(final Intent intent) {
			String url = null;

			if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
				url = intent.getData().toString();
			}

			return url;
		}

	}


	////////////////////////////////////////////////////////////////////////////////////////////////


	private class GetYouTubeChannelInfoTask extends AsyncTaskParallel<String, Void, YouTubeChannel> {

		private final String TAG = GetYouTubeChannelInfoTask.class.getSimpleName();

		@Override
		protected YouTubeChannel doInBackground(String... channelId) {
			YouTubeChannel chn = new YouTubeChannel();

			try {
				chn.init(channelId[0]);
			} catch (IOException e) {
				Log.e(TAG, "Unable to get channel info.  ChannelID=" + channelId[0], e);
				chn = null;
			}

			return chn;
		}

		@Override
		protected void onPostExecute(YouTubeChannel youTubeChannel) {
			YouTubePlayerFragment.this.youTubeChannel = youTubeChannel;

			if (youTubeChannel != null) {
				Glide.with(getActivity())
						.load(youTubeChannel.getThumbnailNormalUrl())
						.placeholder(R.drawable.channel_thumbnail_default)
						.into(videoDescChannelThumbnailImageView);
			}
		}

	}



	////////////////////////////////////////////////////////////////////////////////////////////////


	/**
	 * A task that checks if this video is bookmarked or not.  If it is bookmarked, then it will hide
	 * the menu option to bookmark the video; otherwise it will hide the option to unbookmark the
	 * video.
	 */
	private class IsVideoBookmarkedTask extends AsyncTaskParallel<Void, Void, Boolean> {

		@Override
		protected Boolean doInBackground(Void... params) {
			return BookmarksDb.getBookmarksDb().isBookmarked(youTubeVideo);
		}

		@Override
		protected void onPostExecute(Boolean videoIsBookmarked) {
			// if this video has been bookmarked, hide the bookmark option and show the unbookmark option.
			menu.findItem(R.id.bookmark_video).setVisible(!videoIsBookmarked);
			menu.findItem(R.id.unbookmark_video).setVisible(videoIsBookmarked);
		}

	}


	////////////////////////////////////////////////////////////////////////////////////////////////

}
