package com.kyleh.exquisite.controllers;

import com.kyleh.exquisite.business.Corpse;
import com.kyleh.exquisite.business.CorpseLyric;
import com.kyleh.exquisite.business.SearchResult;
import com.kyleh.exquisite.business.SharedCorpse;

import com.kyleh.exquisite.utility.CorpseID;
import com.kyleh.exquisite.utility.ShareCorpse;
import com.kyleh.exquisite.utility.ShareCorpseMessage;
import com.kyleh.exquisite.utility.ExquisiteConstants;

import org.jmusixmatch.MusixMatch;
import org.jmusixmatch.MusixMatchException;
import org.jmusixmatch.entity.track.Track;
import org.jmusixmatch.entity.track.TrackData;
import org.jmusixmatch.snippet.Snippet;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;


import com.googlecode.objectify.ObjectifyService;



/**
 * Created by kylehebert on 4/24/15.
 * Servlet responsible for searching for lyrics ,adding and removing lyrics
 * to Corpse object and Sharing a Corpse object.
 */
public class CorpseController extends HttpServlet {

    TrackData trackData;
    Track track;
    Snippet snippet;

    MusixMatch musixMatch = new MusixMatch(getMusixMatchAPIKey());

    //register class for Objectify
    static {
        ObjectifyService.register(SharedCorpse.class);
    }


    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        String url = ExquisiteConstants.INDEX_URL;
        ServletContext servletContext = getServletContext();

        //get current action from the .jsp forms
        String action = request.getParameter("action");
        if (action == null) {
            action = ExquisiteConstants.SEARCH; //default action
        }

        //perform action and set URL to appropriate page
        if (action.equals(ExquisiteConstants.SEARCH)) {
            url = ExquisiteConstants.INDEX_URL; //the search page
        }


        if (action.equals(ExquisiteConstants.RESET)) {
            //remove the old corpse session, so new ones can be created
            HttpSession session = request.getSession();
            session.removeAttribute(ExquisiteConstants.CORPSE_ATT);
            url = ExquisiteConstants.INDEX_URL;
        }

        //search for and display the lyric snippet at result.jsp
        else if (action.equals(ExquisiteConstants.RESULT)) {

            //get the search parameters and store them in a SnippetSearch object
            String artistSearch = request.getParameter(ExquisiteConstants.ARTIST);
            String trackSearch = request.getParameter(ExquisiteConstants.TRACK);

            //validate the search parameters
            String message = "";
            if (artistSearch == null || trackSearch == null || artistSearch.isEmpty() || trackSearch.isEmpty()) {
                message = "Please enter both a track and artist";
                url = ExquisiteConstants.INDEX_URL;
            }
            else {
                //Fuzzy Search
                try {
                    track = musixMatch.getMatchingTrack(trackSearch, artistSearch);

                } catch (MusixMatchException e) {
                    //TODO Display lyric not found instead of error_java.jsp
                    System.out.println("Artist or track not found");
                    //e.printStackTrace();

                }

                trackData = track.getTrack();

                String artist = trackData.getArtistName();
                String track = trackData.getTrackName();


                //snippet search
                int trackID = trackData.getTrackId();
                String resultID = Integer.toString(trackID);

                try {
                    snippet = musixMatch.getSnippet(trackID);
                } catch (MusixMatchException e) {
                    e.printStackTrace();
                }
                String lyricSnippet = snippet.getSnippetBody();

                //store results in SearchResult object and create a session
                HttpSession session = request.getSession();
                SearchResult searchResult = new SearchResult(resultID,artist,track,lyricSnippet);

                session.setAttribute(ExquisiteConstants.RESULT, searchResult);

                //set SearchResult Object in request object and set URL
                request.setAttribute(ExquisiteConstants.RESULT_ATT, searchResult);
                url = ExquisiteConstants.RESULT_URL; //the results page

            }

            request.setAttribute(ExquisiteConstants.MESSAGE_ATT, message);
        }

        //convert result to CorpseLyric object and add to Corpse object
        else if (action.equals(ExquisiteConstants.ADD)) {

            //create a a new corpse and new corpse session if needed
            HttpSession session = request.getSession();
            Corpse corpse = (Corpse) session.getAttribute(ExquisiteConstants.CORPSE_ATT);
            if (corpse == null) {
                corpse = new Corpse();
            }

            SearchResult searchResult = (SearchResult) session.getAttribute(ExquisiteConstants.RESULT);

            CorpseLyric corpseLyric = new CorpseLyric(searchResult.getResultID(),searchResult.getSnippet());

            //set the lyrics' session ID to the snippet ID
            //this will be useful for removing lyrics later
            session.setAttribute(corpseLyric.getSnippetID(),corpseLyric);

            corpse.addLyricSnippet(corpseLyric);

            session.setAttribute(ExquisiteConstants.CORPSE_ATT, corpse);

            //once a lyric gets added to a corpse, remove that lyrics' session
            //TODO A search that should throw an error still returns the previous result instead
            session.removeAttribute(ExquisiteConstants.RESULT);

            url = "/corpse.jsp";
        }

        else if (action.equals(ExquisiteConstants.REMOVE)) {
            HttpSession session = request.getSession();
            Corpse corpse = (Corpse) session.getAttribute(ExquisiteConstants.CORPSE_ATT);
            String snippetID = request.getParameter(ExquisiteConstants.SNIPPET_ID);
            CorpseLyric corpseLyric = (CorpseLyric) session.getAttribute(snippetID);
            corpse.removeLyricSnippet(corpseLyric);

            url = ExquisiteConstants.CORPSE_URL;

        }

        else if (action.equals(ExquisiteConstants.SHARE)) {
            HttpSession session = request.getSession();
            Corpse corpse = (Corpse) session.getAttribute(ExquisiteConstants.CORPSE_ATT);

            ArrayList<CorpseLyric> corpseLyrics = corpse.getCorpseLyrics();
            CorpseID corpseID = new CorpseID();

            //create a SharedCorpse and add it to the Data Store
            SharedCorpse sharedCorpse = new SharedCorpse(corpseLyrics, corpseID.getCorpseID());
            ObjectifyService.ofy().save().entity(sharedCorpse).now();

            //Create the share message for Twitter
            ShareCorpseMessage message = new ShareCorpseMessage(corpseID.getCorpseID());

            //Share the URL on Twitter
            ShareCorpse shareCorpse = new ShareCorpse();
            shareCorpse.shareCorpseOnTwitter(message);

            //create the share URL for the Success page
            String sharedURL = sharedCorpseURL(corpseID);
            request.setAttribute(ExquisiteConstants.SHARED_ATT, sharedURL);

            url = ExquisiteConstants.SUCCESS_URL;
        }

        //forward request and response objects to specified URL
        servletContext.getRequestDispatcher(url).forward(request,response);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doPost(request, response);

    }

    protected String sharedCorpseURL(CorpseID corpseID){
        return ExquisiteConstants.SHARED_URL_STRING + corpseID.getCorpseID();
    }

    protected String getMusixMatchAPIKey() {
        //used to read in the musixmatch API key from a file
        String apiKey = "";
        try {
            FileReader fileReader = new FileReader("mmapikey.txt");
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            apiKey = bufferedReader.readLine();
            bufferedReader.close();
            fileReader.close();


        }
        catch (IOException ioe) {
            System.out.println("Could not open or read mmapikey.txt");
            System.out.println(ioe.toString());
        }
        return apiKey;
    }

}
