package org.schabi.newpipe.extractor.stream_info;

/**
 * Created by Christian Schabesberger on 10.08.15.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * StreamExtractor.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

import org.schabi.newpipe.extractor.UrlIdHandler;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;

import java.util.List;

/**Scrapes information from a video streaming service (eg, YouTube).*/


@SuppressWarnings("ALL")
public abstract class StreamExtractor {

    private int serviceId;
    private String url;
    private UrlIdHandler urlIdHandler;
    private StreamInfoItemCollector previewInfoCollector;

    public class ExtractorInitException extends ExtractionException {
        public ExtractorInitException(String message) {
            super(message);
        }
        public ExtractorInitException(Throwable cause) {
            super(cause);
        }
        public ExtractorInitException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public class ContentNotAvailableException extends ParsingException {
        public ContentNotAvailableException(String message) {
            super(message);
        }
        public ContentNotAvailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public StreamExtractor(UrlIdHandler urlIdHandler, String url, int serviceId) {
        this.serviceId = serviceId;
        this.urlIdHandler = urlIdHandler;
        previewInfoCollector = new StreamInfoItemCollector(urlIdHandler, serviceId);
    }

    protected StreamInfoItemCollector getStreamPreviewInfoCollector() {
        return previewInfoCollector;
    }

    public String getUrl() {
        return url;
    }

    public UrlIdHandler getUrlIdHandler() {
        return urlIdHandler;
    }

    public abstract int getTimeStamp() throws ParsingException;
    public abstract String getTitle() throws ParsingException;
    public abstract String getDescription() throws ParsingException;
    public abstract String getUploader() throws ParsingException;
    public abstract String getChannelUrl() throws ParsingException;
    public abstract int getLength() throws ParsingException;
    public abstract long getViewCount() throws ParsingException;
    public abstract String getUploadDate() throws ParsingException;
    public abstract String getThumbnailUrl() throws ParsingException;
    public abstract String getUploaderThumbnailUrl() throws ParsingException;
    public abstract List<AudioStream> getAudioStreams() throws ParsingException;
    public abstract List<VideoStream> getVideoStreams() throws ParsingException;
    public abstract List<VideoStream> getVideoOnlyStreams() throws ParsingException;
    public abstract String getDashMpdUrl() throws ParsingException;
    public abstract int getAgeLimit() throws ParsingException;
    public abstract String getAverageRating() throws ParsingException;
    public abstract int getLikeCount() throws ParsingException;
    public abstract int getDislikeCount() throws ParsingException;
    public abstract StreamInfoItemExtractor getNextVideo() throws ParsingException;
    public abstract StreamInfoItemCollector getRelatedVideos() throws ParsingException;
    public abstract String getPageUrl();
    public abstract StreamInfo.StreamType getStreamType() throws ParsingException;
    public int getServiceId() {
        return serviceId;
    }
}
