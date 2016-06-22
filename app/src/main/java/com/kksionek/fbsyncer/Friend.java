package com.kksionek.fbsyncer;

import org.json.JSONException;
import org.json.JSONObject;

public class Friend implements Comparable<Friend> {
    private String mId;
    private String mName;
    private String mPhoto;


    public Friend(String id, String name) {
        mId = id;
        mName = name;
        mPhoto = null;
    }

    public Friend(String id, String name, String photo) {
        mId = id;
        mName = name;
        mPhoto = photo;
    }

    public Friend(JSONObject jsonObject) {
        try {
            mId = jsonObject.getString("id");
            mName = jsonObject.getString("name");
            mPhoto = jsonObject.getJSONObject("picture").getJSONObject("data").getString("url");
        } catch (JSONException | NullPointerException e) {
            e.printStackTrace();
            mId = "";
            mPhoto = "";
            mName = "";
        }
    }

    @Override
    public int compareTo(Friend another) {
        return mName.compareTo(another.getName());
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Friend))
            return false;
        return mName.equals(((Friend) o).getName());
    }

    public String getName() {
        return mName;
    }

    public String getPhoto() {
        return mPhoto;
    }

    public String getId() {
        return mId;
    }
}
