package cgeo.geocaching.twitter;

import cgeo.geocaching.CgeoApplication;
import cgeo.geocaching.DataStore;
import cgeo.geocaching.Geocache;
import cgeo.geocaching.LogEntry;
import cgeo.geocaching.Trackable;
import cgeo.geocaching.enumerations.LoadFlags;
import cgeo.geocaching.geopoint.Geopoint;
import cgeo.geocaching.geopoint.GeopointFormatter.Format;
import cgeo.geocaching.network.Network;
import cgeo.geocaching.network.OAuth;
import cgeo.geocaching.network.Parameters;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.utils.Log;
import cgeo.geocaching.utils.LogTemplateProvider;
import cgeo.geocaching.utils.LogTemplateProvider.LogContext;

import ch.boye.httpclientandroidlib.HttpResponse;

import org.apache.commons.lang3.StringUtils;

public final class Twitter {
    private static final String HASH_PREFIX_WITH_BLANK = " #";
    private static final int MAX_TWEET_SIZE = 140;

    public static void postTweetCache(String geocode, LogEntry logEntry) {
        final Geocache cache = DataStore.loadCache(geocode, LoadFlags.LOAD_CACHE_OR_DB);
        postTweet(CgeoApplication.getInstance(), getStatusMessage(cache, logEntry), null);
    }

    public static void postTweetTrackable(String geocode) {
        final Trackable trackable = DataStore.loadTrackable(geocode);
        postTweet(CgeoApplication.getInstance(), getStatusMessage(trackable), null);
    }

    private static void postTweet(final CgeoApplication app, final String statusIn, final Geopoint coords) {
        if (app == null || !Settings.isUseTwitter() || !Settings.isTwitterLoginValid()) {
            return;
        }

        try {
            final String status = shortenToMaxSize(statusIn);
            Parameters parameters = new Parameters("status", status);
            if (coords != null) {
                parameters.put(
                        "lat", coords.format(Format.LAT_DECDEGREE_RAW),
                        "long", coords.format(Format.LON_DECDEGREE_RAW),
                        "display_coordinates", "true");
            }

            OAuth.signOAuth("api.twitter.com", "/1.1/statuses/update.json", "POST", false, parameters, Settings.getTokenPublic(), Settings.getTokenSecret(), Settings.getKeyConsumerPublic(), Settings.getKeyConsumerSecret());
            final HttpResponse httpResponse = Network.postRequest("http://api.twitter.com/1.1/statuses/update.json", parameters);
            if (httpResponse != null) {
                if (httpResponse.getStatusLine().getStatusCode() == 200) {
                    Log.i("Tweet posted");
                } else {
                    Log.e("Tweet could not be posted. Reason: " + httpResponse.toString());
                }
            } else {
                Log.e("Tweet could not be posted. Reason: httpResponse Object is null");
            }
        } catch (Exception e) {
            Log.e("Twitter.postTweet", e);
        }
    }

    private static String shortenToMaxSize(final String status) {
        String result = StringUtils.trim(status);
        if (StringUtils.length(result) > MAX_TWEET_SIZE) {
            return StringUtils.substring(result, 0, MAX_TWEET_SIZE - 1) + '…';
        }
        return result;
    }

    private static void appendHashTag(final StringBuilder status, final String tag) {
        if (status.length() + HASH_PREFIX_WITH_BLANK.length() + tag.length() <= MAX_TWEET_SIZE) {
            final String tagWithPrefix = HASH_PREFIX_WITH_BLANK + tag;
            if (!StringUtils.contains(status, tagWithPrefix)) {
                status.append(tagWithPrefix);
            }
        }
    }

    static String getStatusMessage(Geocache cache, LogEntry logEntry) {
        return appendHashTags(LogTemplateProvider.applyTemplates(Settings.getCacheTwitterMessage(), new LogContext(cache, logEntry)));
    }

    static String getStatusMessage(Trackable trackable) {
        return appendHashTags(LogTemplateProvider.applyTemplates(Settings.getTrackableTwitterMessage(), new LogContext(trackable)));
    }

    private static String appendHashTags(final String status) {
        StringBuilder builder = new StringBuilder(status);
        appendHashTag(builder, "cgeo");
        appendHashTag(builder, "geocaching");
        return builder.toString();
    }
}
