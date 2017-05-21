/*
 * SkyTube
 * Copyright (C) 2016  Ramon Mifsud
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation (version 3 of the License).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.hust.duc.gui.activities;

import android.app.Activity;
import android.os.Bundle;

import com.hust.duc.R;
import com.hust.duc.gui.businessobjects.BackButtonActivity;

/**
 * An {@link Activity} that contains an instance of
 * {@link com.hust.duc.gui.fragments.YouTubePlayerFragment}.
 */
public class YouTubePlayerActivity extends BackButtonActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_video_player);
	}

}
