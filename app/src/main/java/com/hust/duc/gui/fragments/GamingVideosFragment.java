package com.hust.duc.gui.fragments;

import com.hust.duc.R;
import com.hust.duc.businessobjects.VideoCategory;
import com.hust.duc.gui.app.NewTubeApp;

/**
 * Created by Admin on 21/05/2017.
 */

public class GamingVideosFragment extends VideosGridFragment {
    @Override
    protected VideoCategory getVideoCategory() {
        return VideoCategory.GAMING;
    }

    @Override
    protected String getFragmentName() {
        return NewTubeApp.getStr(R.string.gaming);
    }
}
