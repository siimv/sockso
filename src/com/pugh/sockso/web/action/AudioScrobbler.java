
package com.pugh.sockso.web.action;

import com.pugh.sockso.web.ObjectCache;
import com.pugh.sockso.Utils;
import com.pugh.sockso.db.Database;
import com.pugh.sockso.web.BadRequestException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import org.apache.log4j.Logger;

public class AudioScrobbler {

    private final int ONE_HOUR_IN_SECONDS = 60 * 60;
    private final int CACHE_TIMEOUT_IN_SECONDS = ONE_HOUR_IN_SECONDS;

    private final Logger log = Logger.getLogger( AudioScrobbler.class );

    private Database db;

    private ObjectCache cache;

    /**
     *  Creates a new AudioScrobbler object
     *
     *  @param db
     *
     */

    public AudioScrobbler( final Database db, final ObjectCache cache ) {
        
        this.db = db;
        this.cache = cache;

    }

    /**
     *  Fetches related artist info
     *
     *  @param artistId
     *
     *  @return
     *
     *  @throws IOException
     *  @throws SQLException
     *  @throws BadRequestException
     *
     */

    public String[] getSimilarArtists( final int artistId ) throws IOException, SQLException, BadRequestException {

        ResultSet rs = null;
        PreparedStatement st = null;
        BufferedReader in = null;

        try {
            
            final String sql = " select name " +
                               " from artists " +
                               " where id = ? ";
            
            st = db.prepare( sql );
            st.setInt( 1, artistId );
            rs = st.executeQuery();
            
            if ( !rs.next() )
                throw new BadRequestException( "unknown artist", 404 );

            final String artistName = rs.getString( "name" );

            if ( !cache.isCached(artistName) ) {

                log.debug( "Fetching similar artists for: " +artistName );

                final String url = "http://ws.audioscrobbler.com/1.0/artist/" +Utils.URLEncode(artistName)+ "/similar.txt";
                final HttpURLConnection cnn = getHttpURLConnection( url );
                final ArrayList<String> artists = new ArrayList<String>();

                String s = "";

                in = new BufferedReader(new InputStreamReader(cnn.getInputStream()) );

                while ( (s = in.readLine()) != null ) {
                    final String[] info = s.split( "," );
                    artists.add( info[2] );
                }

                log.debug( "Writing cache for similar artists on: " +artistName );

                cache.write(
                    artistName,
                    artists.toArray( new String[] {} ),
                    CACHE_TIMEOUT_IN_SECONDS
                );

            }

            return (String[]) cache.read( artistName );

        }
        
        finally {
            Utils.close( rs );
            Utils.close( st );
            Utils.close( in );
        }

    }

    /**
     *  Returns a connection object for the specified URL (seam for testing)
     *
     *  @param url
     *
     *  @return
     *
     *  @throws IOException
     *
     */

    protected HttpURLConnection getHttpURLConnection( final String url ) throws IOException {

        return (HttpURLConnection) new URL( url ).openConnection();

    }

}