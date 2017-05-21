package org.schabi.newpipe.extractor.services.youtube;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptableObject;
import org.schabi.newpipe.extractor.AbstractStreamInfo;
import org.schabi.newpipe.extractor.Downloader;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Parser;
import org.schabi.newpipe.extractor.UrlIdHandler;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.stream_info.AudioStream;
import org.schabi.newpipe.extractor.stream_info.StreamExtractor;
import org.schabi.newpipe.extractor.stream_info.StreamInfo;
import org.schabi.newpipe.extractor.stream_info.StreamInfoItemCollector;
import org.schabi.newpipe.extractor.stream_info.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream_info.VideoStream;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Christian Schabesberger on 06.08.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * YoutubeStreamExtractor.java is part of NewPipe.
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

public class YoutubeStreamExtractor extends StreamExtractor {
    public static final String URL_ENCODED_FMT_STREAM_MAP = "url_encoded_fmt_stream_map";
    public static final String HTTPS = "https:";
    public static final String CONTENT = "content";
    public static final String REGEX_INT = "[^\\d]";

    // exceptions

    public class DecryptException extends ParsingException {
        DecryptException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // special content not available exceptions

    public class GemaException extends ContentNotAvailableException {
        GemaException(String message) {
            super(message);
        }
    }

    public class LiveStreamException extends ContentNotAvailableException {
        LiveStreamException(String message) {
            super(message);
        }
    }

    // ----------------

    // Sometimes if the html page of youtube is already downloaded, youtube web page will internally
    // download the /get_video_info page. Since a certain date dashmpd url is only available over
    // this /get_video_info page, so we always need to download this one to.
    // %%video_id%% will be replaced by the actual video id
    // $$el_type$$ will be replaced by the actual el_type (se the declarations below)
    private static final String GET_VIDEO_INFO_URL =
            "https://www.youtube.com/get_video_info?video_id=%%video_id%%$$el_type$$&ps=default&eurl=&gl=US&hl=en";
    // eltype is necessary for the url above
    private static final String EL_INFO = "el=info";

    public enum ItagType {
        AUDIO,
        VIDEO,
        VIDEO_ONLY
    }

    private static class ItagItem {
        public ItagItem(int id, ItagType type, MediaFormat format, String res, int fps) {
            this.id = id;
            this.itagType = type;
            this.mediaFormatId = format.id;
            this.resolutionString = res;
            this.fps = fps;
        }

        public ItagItem(int id, ItagType type, MediaFormat format, int samplingRate, int bandWidth) {
            this(id, type, format, 0, samplingRate, bandWidth);
        }

        public ItagItem(int id, ItagType type, MediaFormat format, int avgBitrate, int samplingRate, int bandWidth) {
            this.id = id;
            this.itagType = type;
            this.mediaFormatId = format.id;
            this.avgBitrate = avgBitrate;
            this.samplingRate = samplingRate;
            this.bandWidth = bandWidth;
        }

        public int id;
        public ItagType itagType;
        public int mediaFormatId;
        public String resolutionString;
        public int fps = -1;
        public int avgBitrate = -1;
        public int samplingRate = -1;
        public int bandWidth = -1;
    }

    private static final ItagItem[] itagList = {
            //////////////////////////////////////////////////////////////////////////
            // VIDEO     ID     ItagType        Format          Resolution   FPS  ///
            ////////////////////////////////////////////////////////////////////////
            new ItagItem(17, ItagType.VIDEO, MediaFormat.v3GPP,  "144p"     , 12),
            new ItagItem(18, ItagType.VIDEO, MediaFormat.MPEG_4, "360p"     , 24),
            new ItagItem(22, ItagType.VIDEO, MediaFormat.MPEG_4, "720p"     , 24),
            new ItagItem(36, ItagType.VIDEO, MediaFormat.v3GPP,  "240p"     , 24),
            new ItagItem(37, ItagType.VIDEO, MediaFormat.MPEG_4, "1080p"    , 24),
            new ItagItem(38, ItagType.VIDEO, MediaFormat.MPEG_4, "1080p"    , 24),
            new ItagItem(43, ItagType.VIDEO, MediaFormat.WEBM,   "360p"     , 24),
            new ItagItem(44, ItagType.VIDEO, MediaFormat.WEBM,   "480p"     , 24),
            new ItagItem(45, ItagType.VIDEO, MediaFormat.WEBM,   "720p"     , 24),
            new ItagItem(46, ItagType.VIDEO, MediaFormat.WEBM,   "1080p"    , 24),

            //////////////////////////////////////////////////////////////////////////////////////////
            // AUDIO     ID      ItagType          Format        Bitrate    SamplingR  Bandwidth  ///
            ////////////////////////////////////////////////////////////////////////////////////////
            // Disable Opus codec as it's not well supported in older devices
//          new ItagItem(249, ItagType.AUDIO, MediaFormat.WEBMA,    50,        0,          0),
//          new ItagItem(250, ItagType.AUDIO, MediaFormat.WEBMA,    70,        0,          0),
//          new ItagItem(251, ItagType.AUDIO, MediaFormat.WEBMA,    160,       0,          0),
            new ItagItem(171, ItagType.AUDIO, MediaFormat.WEBMA,    128,       0,          0),
            new ItagItem(172, ItagType.AUDIO, MediaFormat.WEBMA,    256,       0,          0),
            new ItagItem(140, ItagType.AUDIO, MediaFormat.M4A,      128,       0,          0),
            new ItagItem(141, ItagType.AUDIO, MediaFormat.M4A,      256,       0,          0),

            /// VIDEO ONLY ///////////////////////////////////////////////////////////////////
            //           ID         ItagType             Format       Resolution     FPS  ///
            ////////////////////////////////////////////////////////////////////////////////
            // Don't add VideoOnly streams that have normal variants
//          new ItagItem(160, ItagType.VIDEO_ONLY, MediaFormat.MPEG_4,  "144p"      , 24),
//          new ItagItem(133, ItagType.VIDEO_ONLY, MediaFormat.MPEG_4,  "240p"      , 24),
//          new ItagItem(134, ItagType.VIDEO_ONLY, MediaFormat.MPEG_4,  "360p"      , 24),
            new ItagItem(135, ItagType.VIDEO_ONLY, MediaFormat.MPEG_4,  "480p"      , 30),
//          new ItagItem(136, ItagType.VIDEO_ONLY, MediaFormat.MPEG_4,  "720p"      , 30),
            new ItagItem(298, ItagType.VIDEO_ONLY, MediaFormat.MPEG_4,  "720p60"    , 60),
            new ItagItem(137, ItagType.VIDEO_ONLY, MediaFormat.MPEG_4,  "1080p"     , 30),
            new ItagItem(299, ItagType.VIDEO_ONLY, MediaFormat.MPEG_4,  "1080p60"   , 60),
            new ItagItem(266, ItagType.VIDEO_ONLY, MediaFormat.MPEG_4,  "2160p"     , 30),

//          new ItagItem(243, ItagType.VIDEO_ONLY, MediaFormat.WEBM,    "360p"      , 30),
            new ItagItem(244, ItagType.VIDEO_ONLY, MediaFormat.WEBM,    "480p"      , 30),
            new ItagItem(245, ItagType.VIDEO_ONLY, MediaFormat.WEBM,    "480p"      , 30),
            new ItagItem(246, ItagType.VIDEO_ONLY, MediaFormat.WEBM,    "480p"      , 30),
            new ItagItem(247, ItagType.VIDEO_ONLY, MediaFormat.WEBM,    "720p"      , 30),
            new ItagItem(248, ItagType.VIDEO_ONLY, MediaFormat.WEBM,    "1080p"     , 30),
            new ItagItem(271, ItagType.VIDEO_ONLY, MediaFormat.WEBM,    "1440p"     , 30),
            // #272 is either 3840x2160 (e.g. RtoitU2A-3E) or 7680x4320 (sLprVF6d7Ug)
            new ItagItem(272, ItagType.VIDEO_ONLY, MediaFormat.WEBM,    "2160p"     , 30),
            new ItagItem(302, ItagType.VIDEO_ONLY, MediaFormat.WEBM,    "720p60"    , 60),
            new ItagItem(303, ItagType.VIDEO_ONLY, MediaFormat.WEBM,    "1080p60"   , 60),
            new ItagItem(308, ItagType.VIDEO_ONLY, MediaFormat.WEBM,    "1440p60"   , 60),
            new ItagItem(313, ItagType.VIDEO_ONLY, MediaFormat.WEBM,    "2160p"     , 30),
            new ItagItem(315, ItagType.VIDEO_ONLY, MediaFormat.WEBM,    "2160p60"   , 60)
    };

    /**These lists only contain itag formats that are supported by the common Android Video player.
     However if you are looking for a list showing all itag formats, look at
     https://github.com/rg3/youtube-dl/issues/1687 */

    public static boolean itagIsSupported(int itag) {
        for(ItagItem item : itagList) {
            if(itag == item.id) {
                return true;
            }
        }
        return false;
    }

    public static ItagItem getItagItem(int itag) throws ParsingException {
        for(ItagItem item : itagList) {
            if(itag == item.id) {
                return item;
            }
        }
        throw new ParsingException("itag=" + Integer.toString(itag) + " not supported");
    }

    private static final String TAG = YoutubeStreamExtractor.class.toString();
    private final Document doc;
    private JSONObject playerArgs;
    private boolean isAgeRestricted;
    private Map<String, String> videoInfoPage;

    // static values
    private static final String DECRYPTION_FUNC_NAME="decrypt";

    // cached values
    private static volatile String decryptionCode = "";

    UrlIdHandler urlidhandler = YoutubeStreamUrlIdHandler.getInstance();
    String pageUrl = "";

    public YoutubeStreamExtractor(UrlIdHandler urlIdHandler, String pageUrl, int serviceId)
            throws ExtractionException, IOException {
        super(urlIdHandler, pageUrl, serviceId);
        //most common videoInfo fields are now set in our superclass, for all services
        this.pageUrl = pageUrl;
        Downloader downloader = NewPipe.getDownloader();
        String pageContent = downloader.download(urlidhandler.cleanUrl(pageUrl));
        doc = Jsoup.parse(pageContent, pageUrl);
        JSONObject ytPlayerConfig;
        String playerUrl;

        // Check if the video is age restricted
        if (pageContent.contains("<meta property=\"og:restrictions:age")) {
            String videoInfoUrl = GET_VIDEO_INFO_URL.replace("%%video_id%%",
                    urlidhandler.getId(pageUrl)).replace("$$el_type$$", "&" + EL_INFO);
            String videoInfoPageString = downloader.download(videoInfoUrl);
            videoInfoPage = Parser.compatParseMap(videoInfoPageString);
            playerUrl = getPlayerUrlFromRestrictedVideo(pageUrl);
            isAgeRestricted = true;
        } else {
            ytPlayerConfig = getPlayerConfig(pageContent);
            playerArgs = getPlayerArgs(ytPlayerConfig);
            playerUrl = getPlayerUrl(ytPlayerConfig);
            isAgeRestricted = false;
        }

        if(decryptionCode.isEmpty()) {
            decryptionCode = loadDecryptionCode(playerUrl);
        }
    }

    private JSONObject getPlayerConfig(String pageContent) throws ParsingException {
        try {
            String ytPlayerConfigRaw =
                    Parser.matchGroup1("ytplayer.config\\s*=\\s*(\\{.*?\\});", pageContent);
            return new JSONObject(ytPlayerConfigRaw);
        } catch (Parser.RegexException e) {
            String errorReason = findErrorReason(doc);
            switch(errorReason) {
                case "GEMA":
                    throw new GemaException(errorReason);
                case "":
                    throw new ContentNotAvailableException("Content not available: player config empty", e);
                default:
                    throw new ContentNotAvailableException("Content not available", e);
            }
        } catch (JSONException e) {
            throw new ParsingException("Could not parse yt player config", e);
        }
    }

    private JSONObject getPlayerArgs(JSONObject playerConfig) throws ParsingException {
        JSONObject playerArgs;

        //attempt to load the youtube js player JSON arguments
        boolean isLiveStream = false; //used to determine if this is a livestream or not
        try {
            playerArgs = playerConfig.getJSONObject("args");

            // check if we have a live stream. We need to filter it, since its not yet supported.
            if((playerArgs.has("ps") && playerArgs.get("ps").toString().equals("live"))
                    || (playerArgs.get(URL_ENCODED_FMT_STREAM_MAP).toString().isEmpty())) {
                isLiveStream = true;
            }
        }  catch (JSONException e) {
            throw new ParsingException("Could not parse yt player config", e);
        }
        if (isLiveStream) {
            throw new LiveStreamException("This is a Life stream. Can't use those right now.");
        }

        return playerArgs;
    }

    private String getPlayerUrl(JSONObject playerConfig) throws ParsingException {
        try {
            // The Youtube service needs to be initialized by downloading the
            // js-Youtube-player. This is done in order to get the algorithm
            // for decrypting cryptic signatures inside certain stream urls.
            String playerUrl = "";

            JSONObject ytAssets = playerConfig.getJSONObject("assets");
            playerUrl = ytAssets.getString("js");

            if (playerUrl.startsWith("//")) {
                playerUrl = HTTPS + playerUrl;
            }
            return playerUrl;
        } catch (JSONException e) {
            throw new ParsingException(
                    "Could not load decryption code for the Youtube service.", e);
        }
    }

    private String getPlayerUrlFromRestrictedVideo(String pageUrl) throws ParsingException, ReCaptchaException {
        try {
            Downloader downloader = NewPipe.getDownloader();
            String playerUrl = "";
            String videoId = urlidhandler.getId(pageUrl);
            String embedUrl = "https://www.youtube.com/embed/" + videoId;
            String embedPageContent = downloader.download(embedUrl);
            //todo: find out if this can be reapaced by Parser.matchGroup1()
            Pattern assetsPattern = Pattern.compile("\"assets\":.+?\"js\":\\s*(\"[^\"]+\")");
            Matcher patternMatcher = assetsPattern.matcher(embedPageContent);
            while (patternMatcher.find()) {
                playerUrl = patternMatcher.group(1);
            }
            playerUrl = playerUrl.replace("\\", "").replace("\"", "");

            if (playerUrl.startsWith("//")) {
                playerUrl = HTTPS + playerUrl;
            }
            return playerUrl;
        } catch (IOException e) {
            throw new ParsingException(
                    "Could load decryption code form restricted video for the Youtube service.", e);
        } catch (ReCaptchaException e) {
            throw new ReCaptchaException("reCaptcha Challenge requested");
        }
    }

    @Override
    public String getTitle() throws ParsingException {
        try {
            if (playerArgs == null) {
                return videoInfoPage.get("title");
            }
            //json player args method
            return playerArgs.getString("title");
        } catch(JSONException je) {//html <meta> method
            je.printStackTrace();
            System.err.println("failed to load title from JSON args; trying to extract it from HTML");
            try { // fall through to fall-back
                return doc.select("meta[name=title]").attr(CONTENT);
            } catch (Exception e) {
                throw new ParsingException("failed permanently to load title.", e);
            }
        }
    }

    @Override
    public String getDescription() throws ParsingException {
        try {
            return doc.select("p[id=\"eow-description\"]").first().html();
        } catch (Exception e) {//todo: add fallback method <-- there is no ... as long as i know
            throw new ParsingException("failed to load description.", e);
        }
    }

    @Override
    public String getUploader() throws ParsingException {
        try {
            if (playerArgs == null) {
                return videoInfoPage.get("author");
            }
            //json player args method
            return playerArgs.getString("author");
        } catch(JSONException je) {
            je.printStackTrace();
            System.err.println(
                    "failed to load uploader name from JSON args; trying to extract it from HTML");
        } try {//fall through to fallback HTML method
            return doc.select("div.yt-user-info").first().text();
        } catch (Exception e) {
            throw new ParsingException("failed permanently to load uploader name.", e);
        }
    }

    @Override
    public int getLength() throws ParsingException {
        try {
            if (playerArgs == null) {
                return Integer.valueOf(videoInfoPage.get("length_seconds"));
            }
            return playerArgs.getInt("length_seconds");
        } catch (JSONException e) {//todo: find fallback method
            throw new ParsingException("failed to load video duration from JSON args", e);
        }
    }

    @Override
    public long getViewCount() throws ParsingException {
        try {
            String viewCountString = doc.select("meta[itemprop=interactionCount]").attr(CONTENT);
            return Long.parseLong(viewCountString);
        } catch (Exception e) {//todo: find fallback method
            throw new ParsingException("failed to get number of views", e);
        }
    }

    @Override
    public String getUploadDate() throws ParsingException {
        try {
            return doc.select("meta[itemprop=datePublished]").attr(CONTENT);
        } catch (Exception e) {//todo: add fallback method
            throw new ParsingException("failed to get upload date.", e);
        }
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        //first attempt getting a small image version
        //in the html extracting part we try to get a thumbnail with a higher resolution
        // Try to get high resolution thumbnail if it fails use low res from the player instead
        try {
            return doc.select("link[itemprop=\"thumbnailUrl\"]").first().attr("abs:href");
        } catch(Exception e) {
            System.err.println("Could not find high res Thumbnail. Using low res instead");
        }
        try { //fall through to fallback
            return playerArgs.getString("thumbnail_url");
        } catch (JSONException je) {
            throw new ParsingException(
                    "failed to extract thumbnail URL from JSON args; trying to extract it from HTML", je);
        } catch (NullPointerException ne) {
            // Get from the video info page instead
            return videoInfoPage.get("thumbnail_url");
        }
    }

    @Override
    public String getUploaderThumbnailUrl() throws ParsingException {
        try {
            return doc.select("a[class*=\"yt-user-photo\"]").first()
                    .select("img").first()
                    .attr("abs:data-thumb");
        } catch (Exception e) {//todo: add fallback method
            throw new ParsingException("failed to get uploader thumbnail URL.", e);
        }
    }

    @Override
    public String getDashMpdUrl() throws ParsingException {
        try {
            String dashManifestUrl = "";
            if(videoInfoPage != null && videoInfoPage.containsKey("dashmpd")) {
                dashManifestUrl = videoInfoPage.get("dashmpd");
            } else if (playerArgs.has("dashmpd")) {
                dashManifestUrl = playerArgs.getString("dashmpd");
            } else {
                return "";
            }
            if(!dashManifestUrl.contains("/signature/")) {
                String encryptedSig = Parser.matchGroup1("/s/([a-fA-F0-9\\.]+)", dashManifestUrl);
                String decryptedSig;

                decryptedSig = decryptSignature(encryptedSig, decryptionCode);
                dashManifestUrl = dashManifestUrl.replace("/s/" + encryptedSig, "/signature/" + decryptedSig);
            }
            return dashManifestUrl;
        } catch (Exception e) {
            throw new ParsingException(
                    "Could not get \"dashmpd\" maybe VideoInfoPage is broken.", e);
        }
    }


    @Override
    public List<AudioStream> getAudioStreams() throws ParsingException {
        Vector<AudioStream> audioStreams = new Vector<>();
        try{
            String encodedUrlMap;
            // playerArgs could be null if the video is age restricted
            if (playerArgs == null) {
                if(videoInfoPage.containsKey("adaptive_fmts")) {
                    encodedUrlMap = videoInfoPage.get("adaptive_fmts");
                } else {
                    return null;
                }
            } else {
                if(playerArgs.has("adaptive_fmts")) {
                    encodedUrlMap = playerArgs.getString("adaptive_fmts");
                } else {
                    return null;
                }
            }
            for(String url_data_str : encodedUrlMap.split(",")) {
                // This loop iterates through multiple streams, therefor tags
                // is related to one and the same stream at a time.
                Map<String, String> tags = Parser.compatParseMap(
                        org.jsoup.parser.Parser.unescapeEntities(url_data_str, true));

                int itag = Integer.parseInt(tags.get("itag"));

                if (itagIsSupported(itag)) {
                    ItagItem itagItem = getItagItem(itag);
                    if (itagItem.itagType == ItagType.AUDIO) {
                        String streamUrl = tags.get("url");
                        // if video has a signature: decrypt it and add it to the url
                        if (tags.get("s") != null) {
                            streamUrl = streamUrl + "&signature="
                                    + decryptSignature(tags.get("s"), decryptionCode);
                        }

                        audioStreams.add(new AudioStream(streamUrl,
                                itagItem.mediaFormatId,
                                itagItem.avgBitrate,
                                itagItem.bandWidth,
                                itagItem.samplingRate));
                    }
                }
            }
        } catch (Exception e) {
            throw new ParsingException("Could not get audiostreams", e);
        }
        return audioStreams;
    }

    @Override
    public List<VideoStream> getVideoStreams() throws ParsingException {
        Vector<VideoStream> videoStreams = new Vector<>();

        try{
            String encodedUrlMap;
            // playerArgs could be null if the video is age restricted
            if (playerArgs == null) {
                encodedUrlMap = videoInfoPage.get(URL_ENCODED_FMT_STREAM_MAP);
            } else {
                encodedUrlMap = playerArgs.getString(URL_ENCODED_FMT_STREAM_MAP);
            }
            for(String url_data_str : encodedUrlMap.split(",")) {
                try {
                    // This loop iterates through multiple streams, therefor tags
                    // is related to one and the same stream at a time.
                    Map<String, String> tags = Parser.compatParseMap(
                            org.jsoup.parser.Parser.unescapeEntities(url_data_str, true));

                    int itag = Integer.parseInt(tags.get("itag"));

                    if (itagIsSupported(itag)) {
                        ItagItem itagItem = getItagItem(itag);
                        if(itagItem.itagType == ItagType.VIDEO) {
                            String streamUrl = tags.get("url");
                            // if video has a signature: decrypt it and add it to the url
                            if (tags.get("s") != null) {
                                streamUrl = streamUrl + "&signature="
                                        + decryptSignature(tags.get("s"), decryptionCode);
                            }
                            videoStreams.add(new VideoStream(
                                    streamUrl,
                                    itagItem.mediaFormatId,
                                    itagItem.resolutionString));
                        }
                    }
                } catch (Exception e) {
                    //todo: dont log throw an error
                    System.err.println("Could not get Video stream.");
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            throw new ParsingException("Failed to get video streams", e);
        }

        if(videoStreams.isEmpty()) {
            throw new ParsingException("Failed to get any video stream");
        }
        return videoStreams;
    }

    @Override
    public List<VideoStream> getVideoOnlyStreams() throws ParsingException {
        Vector<VideoStream> videoOnlyStreams = new Vector<>();

        try {
            String encodedUrlMap;
            // playerArgs could be null if the video is age restricted
            if (playerArgs == null) {
                if (videoInfoPage.containsKey("adaptive_fmts")) {
                    encodedUrlMap = videoInfoPage.get("adaptive_fmts");
                } else {
                    return null;
                }
            } else {
                if (playerArgs.has("adaptive_fmts")) {
                    encodedUrlMap = playerArgs.getString("adaptive_fmts");
                } else {
                    return null;
                }
            }
            for (String url_data_str : encodedUrlMap.split(",")) {
                // This loop iterates through multiple streams, therefor tags
                // is related to one and the same stream at a time.
                Map<String, String> tags = Parser.compatParseMap(
                        org.jsoup.parser.Parser.unescapeEntities(url_data_str, true));

                int itag = Integer.parseInt(tags.get("itag"));

                if (itagIsSupported(itag)) {
                    ItagItem itagItem = getItagItem(itag);
                    if (itagItem.itagType == ItagType.VIDEO_ONLY) {
                        String streamUrl = tags.get("url");
                        // if video has a signature: decrypt it and add it to the url
                        if (tags.get("s") != null) {
                            streamUrl = streamUrl + "&signature="
                                    + decryptSignature(tags.get("s"), decryptionCode);
                        }

                        videoOnlyStreams.add(new VideoStream(
                                true, //isVideoOnly
                                streamUrl,
                                itagItem.mediaFormatId,
                                itagItem.resolutionString));
                    }
                }
            }
        } catch (Exception e) {
            throw new ParsingException("Failed to get video only streams", e);
        }

        if (videoOnlyStreams.isEmpty()) {
            throw new ParsingException("Failed to get any video only stream");
        }
        return videoOnlyStreams;
    }

    /**Attempts to parse (and return) the offset to start playing the video from.
     * @return the offset (in seconds), or 0 if no timestamp is found.*/
    @Override
    public int getTimeStamp() throws ParsingException {
        String timeStamp;
        try {
            timeStamp = Parser.matchGroup1("((#|&|\\?)t=\\d{0,3}h?\\d{0,3}m?\\d{1,3}s?)", pageUrl);
        } catch (Parser.RegexException e) {
            // catch this instantly since an url does not necessarily have to have a time stamp

            // -2 because well the testing system will then know its the regex that failed :/
            // not good i know
            return -2;
        }

        if(!timeStamp.isEmpty()) {
            try {
                String secondsString = "";
                String minutesString = "";
                String hoursString = "";
                try {
                    secondsString = Parser.matchGroup1("(\\d{1,3})s", timeStamp);
                    minutesString = Parser.matchGroup1("(\\d{1,3})m", timeStamp);
                    hoursString = Parser.matchGroup1("(\\d{1,3})h", timeStamp);
                } catch (Exception e) {
                    //it could be that time is given in another method
                    if (secondsString.isEmpty() //if nothing was got,
                            && minutesString.isEmpty()//treat as unlabelled seconds
                            && hoursString.isEmpty()) {
                        secondsString = Parser.matchGroup1("t=(\\d+)", timeStamp);
                    }
                }

                int seconds = secondsString.isEmpty() ? 0 : Integer.parseInt(secondsString);
                int minutes = minutesString.isEmpty() ? 0 : Integer.parseInt(minutesString);
                int hours = hoursString.isEmpty() ? 0 : Integer.parseInt(hoursString);

                //don't trust BODMAS!
                return seconds + (60 * minutes) + (3600 * hours);
                //Log.d(TAG, "derived timestamp value:"+ret);
                //the ordering varies internationally
            } catch (ParsingException e) {
                throw new ParsingException("Could not get timestamp.", e);
            }
        } else {
            return 0;
        }
    }

    @Override
    public int getAgeLimit() throws ParsingException {
        if (!isAgeRestricted) {
            return 0;
        }
        try {
            return Integer.valueOf(doc.head()
                    .getElementsByAttributeValue("property", "og:restrictions:age")
                    .attr(CONTENT).replace("+", ""));
        } catch (Exception e) {
            throw new ParsingException("Could not get age restriction");
        }
    }

    @Override
    public String getAverageRating() throws ParsingException {
        try {
            if (playerArgs == null) {
                return videoInfoPage.get("avg_rating");
            }
            return playerArgs.getString("avg_rating");
        } catch (JSONException e) {
            throw new ParsingException("Could not get Average rating", e);
        }
    }

    @Override
    public int getLikeCount() throws ParsingException {
        String likesString = "";
        try {

            Element button = doc.select("button.like-button-renderer-like-button").first();
            try {
                likesString = button.select("span.yt-uix-button-content").first().text();
            } catch (NullPointerException e) {
                //if this ckicks in our button has no content and thefore likes/dislikes are disabled
                return -1;
            }
            return Integer.parseInt(likesString.replaceAll(REGEX_INT, ""));
        } catch (NumberFormatException nfe) {
            throw new ParsingException(
                    "failed to parse likesString \"" + likesString + "\" as integers", nfe);
        } catch (Exception e) {
            throw new ParsingException("Could not get like count", e);
        }
    }

    @Override
    public int getDislikeCount() throws ParsingException {
        String dislikesString = "";
        try {
            Element button = doc.select("button.like-button-renderer-dislike-button").first();
            try {
                dislikesString = button.select("span.yt-uix-button-content").first().text();
            } catch (NullPointerException e) {
                //if this kicks in our button has no content and therefore likes/dislikes are disabled
                return -1;
            }
            return Integer.parseInt(dislikesString.replaceAll(REGEX_INT, ""));
        } catch(NumberFormatException nfe) {
            throw new ParsingException(
                    "failed to parse dislikesString \"" + dislikesString + "\" as integers", nfe);
        } catch(Exception e) {
            throw new ParsingException("Could not get dislike count", e);
        }
    }

    @Override
    public StreamInfoItemExtractor getNextVideo() throws ParsingException {
        try {
            return extractVideoPreviewInfo(doc.select("div[class=\"watch-sidebar-section\"]").first()
                    .select("li").first());
        } catch(Exception e) {
            throw new ParsingException("Could not get next video", e);
        }
    }

    @Override
    public StreamInfoItemCollector getRelatedVideos() throws ParsingException {
        try {
            StreamInfoItemCollector collector = getStreamPreviewInfoCollector();
            Element ul = doc.select("ul[id=\"watch-related\"]").first();
            if(ul != null) {
                for (Element li : ul.children()) {
                    // first check if we have a playlist. If so leave them out
                    if (li.select("a[class*=\"content-link\"]").first() != null) {
                        collector.commit(extractVideoPreviewInfo(li));
                    }
                }
            }
            return collector;
        } catch(Exception e) {
            throw new ParsingException("Could not get related videos", e);
        }
    }

    @Override
    public String getPageUrl() {
        return pageUrl;
    }

    @Override
    public String getChannelUrl() throws ParsingException {
        try {
            return doc.select("div[class=\"yt-user-info\"]").first().children()
                    .select("a").first().attr("abs:href");
        } catch(Exception e) {
            throw new ParsingException("Could not get channel link", e);
        }
    }

    @Override
    public StreamInfo.StreamType getStreamType() throws ParsingException {
        //todo: if implementing livestream support this value should be generated dynamically
        return StreamInfo.StreamType.VIDEO_STREAM;
    }

    /**Provides information about links to other videos on the video page, such as related videos.
     * This is encapsulated in a StreamInfoItem object,
     * which is a subset of the fields in a full StreamInfo.*/
    private StreamInfoItemExtractor extractVideoPreviewInfo(final Element li) {
        return new StreamInfoItemExtractor() {
            @Override
            public AbstractStreamInfo.StreamType getStreamType() throws ParsingException {
                return AbstractStreamInfo.StreamType.VIDEO_STREAM;
            }

            @Override
            public boolean isAd() throws ParsingException {
                return !li.select("span[class*=\"icon-not-available\"]").isEmpty();
            }

            @Override
            public String getWebPageUrl() throws ParsingException {
                return li.select("a.content-link").first().attr("abs:href");
            }

            @Override
            public String getTitle() throws ParsingException {
                //todo: check NullPointerException causing
                return li.select("span.title").first().text();
                //this page causes the NullPointerException, after finding it by searching for "tjvg":
                //https://www.youtube.com/watch?v=Uqg0aEhLFAg
            }

            @Override
            public int getDuration() throws ParsingException {
                return YoutubeParsingHelper.parseDurationString(
                        li.select("span.video-time").first().text());
            }

            @Override
            public String getUploader() throws ParsingException {
                return li.select("span.g-hovercard").first().text();
            }

            @Override
            public String getUploadDate() throws ParsingException {
                return null;
            }

            @Override
            public long getViewCount() throws ParsingException {
                //this line is unused
                //String views = li.select("span.view-count").first().text();

                //Log.i(TAG, "title:"+info.title);
                //Log.i(TAG, "view count:"+views);

                try {
                    return Long.parseLong(li.select("span.view-count")
                            .first().text().replaceAll(REGEX_INT, ""));
                } catch (Exception e) {
                    //related videos sometimes have no view count
                    return 0;
                }
            }

            @Override
            public String getThumbnailUrl() throws ParsingException {
                Element img = li.select("img").first();
                String thumbnailUrl = img.attr("abs:src");
                // Sometimes youtube sends links to gif files which somehow seem to not exist
                // anymore. Items with such gif also offer a secondary image source. So we are going
                // to use that if we caught such an item.
                if (thumbnailUrl.contains(".gif")) {
                    thumbnailUrl = img.attr("data-thumb");
                }
                if (thumbnailUrl.startsWith("//")) {
                    thumbnailUrl = HTTPS + thumbnailUrl;
                }
                return thumbnailUrl;
            }
        };
    }


    private String loadDecryptionCode(String playerUrl) throws DecryptException {
        String decryptionFuncName;
        String decryptionFunc;
        String helperObjectName;
        String helperObject;
        String callerFunc = "function " + DECRYPTION_FUNC_NAME + "(a){return %%(a);}";
        String decryptionCode;

        try {
            Downloader downloader = NewPipe.getDownloader();
            if(!playerUrl.contains("https://youtube.com")) {
                //sometimes the https://youtube.com part does not get send with
                //than we have to add it by hand
                playerUrl = "https://youtube.com" + playerUrl;
            }
            String playerCode = downloader.download(playerUrl);

            decryptionFuncName =
                    Parser.matchGroup("([\"\\'])signature\\1\\s*,\\s*([a-zA-Z0-9$]+)\\(", playerCode, 2);

            String functionPattern = "("
                    + decryptionFuncName.replace("$", "\\$")
                    + "=function\\([a-zA-Z0-9_]+\\)\\{.+?\\})";
            decryptionFunc = "var " + Parser.matchGroup1(functionPattern, playerCode) + ";";

            helperObjectName = Parser
                    .matchGroup1(";([A-Za-z0-9_\\$]{2})\\...\\(", decryptionFunc);

            String helperPattern = "(var "
                    + helperObjectName.replace("$", "\\$") + "=\\{.+?\\}\\};)";
            helperObject = Parser.matchGroup1(helperPattern, playerCode);


            callerFunc = callerFunc.replace("%%", decryptionFuncName);
            decryptionCode = helperObject + decryptionFunc + callerFunc;
        } catch(IOException ioe) {
            throw new DecryptException("Could not load decrypt function", ioe);
        } catch(Exception e) {
            throw new DecryptException("Could not parse decrypt function ", e);
        }

        return decryptionCode;
    }

    private String decryptSignature(String encryptedSig, String decryptionCode)
            throws DecryptException{
        Context context = Context.enter();
        context.setOptimizationLevel(-1);
        Object result = null;
        try {
            ScriptableObject scope = context.initStandardObjects();
            context.evaluateString(scope, decryptionCode, "decryptionCode", 1, null);
            Function decryptionFunc = (Function) scope.get("decrypt", scope);
            result = decryptionFunc.call(context, scope, scope, new Object[]{encryptedSig});
        } catch (Exception e) {
            throw new DecryptException("could not get decrypt signature", e);
        } finally {
            Context.exit();
        }
        return result == null ? "" : result.toString();
    }

    private String findErrorReason(Document doc) {
        String errorMessage = doc.select("h1[id=\"unavailable-message\"]").first().text();
        if(errorMessage.contains("GEMA")) {
            // Gema sometimes blocks youtube music content in germany:
            // https://www.gema.de/en/
            // Detailed description:
            // https://en.wikipedia.org/wiki/GEMA_%28German_organization%29
            return "GEMA";
        }
        return "";
    }
}
