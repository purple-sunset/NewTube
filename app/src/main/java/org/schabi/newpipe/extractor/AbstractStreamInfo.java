package org.schabi.newpipe.extractor;

/**
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * AbstractStreamInfo.java is part of NewPipe.
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

import java.io.Serializable;

/**Common properties between StreamInfo and StreamInfoItem.*/
public abstract class AbstractStreamInfo implements Serializable{
    public enum StreamType {
        NONE,   // placeholder to check if stream type was checked or not
        VIDEO_STREAM,
        AUDIO_STREAM,
        LIVE_STREAM,
        AUDIO_LIVE_STREAM,
        FILE
    }

    public StreamType stream_type;
    public int service_id = -1;
    public String id = "";
    public String title = "";
    public String uploader = "";
    public String thumbnail_url = "";
    public String webpage_url = "";
    public String upload_date = "";
    public long view_count = -1;
}
