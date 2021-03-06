package com.kyleh.exquisite.business;

import java.io.Serializable;

/**
 * Created by kylehebert on 4/28/15.
 * Objects of this class will represent the result of
 * a lyrics search, and store those results as a Bean for display
 * by results.jsp
 */
public class SearchResult implements Serializable {
    private String artist;
    private String track;
    private String snippet;
    private String resultID;


    public SearchResult() {
        artist = "";
        track = "";
        snippet = "";
        resultID = "";
    }

    public SearchResult(String resultID, String artist, String track, String snippet) {
        this.resultID = resultID;
        this.artist = artist;
        this.track = track;
        this.snippet = snippet;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getTrack() {
        return track;
    }

    public void setTrack(String track) {
        this.track = track;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public String getResultID() {
        return resultID;
    }

    public void setResultID(String resultID) {
        this.resultID = resultID;
    }
}
