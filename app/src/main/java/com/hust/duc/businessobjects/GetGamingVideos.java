package com.hust.duc.businessobjects;

import android.util.Log;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;

import java.io.IOException;
import java.util.List;

/**
 * Created by Admin on 21/05/2017.
 */

public class GetGamingVideos extends GetYouTubeVideos {

    protected YouTube.Videos.List videosList = null;

    private static final String	TAG = GetGamingVideos.class.getSimpleName();

    @Override
    public void init() throws IOException {
        videosList = YouTubeAPI.create().videos().list("snippet, statistics, contentDetails");
        videosList.setFields("items(id, snippet/defaultAudioLanguage, snippet/defaultLanguage, snippet/publishedAt, snippet/title, snippet/channelId, snippet/channelTitle," +
                "snippet/thumbnails/high, contentDetails/duration, statistics)," +
                "nextPageToken");
        videosList.setKey(YouTubeAPIKey.get().getYouTubeAPIKey());
        videosList.setChart("mostPopular");
        videosList.setVideoCategoryId("20");
        videosList.setRegionCode(getPreferredRegion());
        videosList.setMaxResults(getMaxResults());
        nextPageToken = null;
    }

    @Override
    public List<YouTubeVideo> getNextVideos() {
        List<Video> searchResultList = null;

        if (!noMoreVideoPages()) {
            try {
                // set the page token/id to retrieve
                videosList.setPageToken(nextPageToken);

                // communicate with YouTube
                VideoListResponse response = videosList.execute();

                // get videos
                searchResultList = response.getItems();

                // set the next page token
                nextPageToken = response.getNextPageToken();

                // if nextPageToken is null, it means that there are no more videos
                if (nextPageToken == null)
                    noMoreVideoPages = true;
            } catch (IOException e) {
                Log.e(TAG, "Error has occurred while getting Gaming Videos.", e);
            }
        }

        return toYouTubeVideoList(searchResultList);
    }

    @Override
    public boolean noMoreVideoPages() {
        return noMoreVideoPages;
    }

    protected Long getMaxResults() {
        return 50L;
    }
}
