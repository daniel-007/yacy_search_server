// yacysearch.java
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.
//
// You must compile this file with
// javac -classpath .:../classes yacysearch.java
// if the shell's current path is HTROOT

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeSet;

import de.anomic.htmlFilter.htmlFilterImageEntry;
import de.anomic.http.httpHeader;
import de.anomic.kelondro.kelondroMSetTools;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.plasma.plasmaSearchImages;
import de.anomic.plasma.plasmaSearchRankingProfile;
import de.anomic.plasma.plasmaSearchTimingProfile;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSearchPreOrder;
import de.anomic.server.serverCore;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;

public class yacysearch {

    public static final int MAX_TOPWORDS = 24;

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;

        boolean authenticated = sb.adminAuthenticated(header) >= 2;
        int display = ((post == null) || (!authenticated)) ? 0 : post.getInt("display", 0);
        String promoteSearchPageGreeting = env.getConfig("promoteSearchPageGreeting", "");
        if (promoteSearchPageGreeting.length() == 0) promoteSearchPageGreeting = "P2P WEB SEARCH";

        // case if no values are requested
        final String referer = (String) header.get("Referer");
        if (post == null || env == null) {

            // save referrer
            // System.out.println("HEADER=" + header.toString());
            if (referer != null) {
                URL url;
                try { url = new URL(referer); } catch (MalformedURLException e) { url = null; }
                if ((url != null) && (serverCore.isNotLocal(url))) {
                    final HashMap referrerprop = new HashMap();
                    referrerprop.put("count", "1");
                    referrerprop.put("clientip", header.get("CLIENTIP"));
                    referrerprop.put("useragent", header.get("User-Agent"));
                    referrerprop.put("date", (new serverDate()).toShortString(false));
                    if (sb.facilityDB != null) try { sb.facilityDB.update("backlinks", referer, referrerprop); } catch (IOException e) {}
                }
            }

            // we create empty entries for template strings
            final serverObjects prop = new serverObjects();
            prop.put("promoteSearchPageGreeting", promoteSearchPageGreeting);
            prop.put("former", "");
            prop.put("count", 10);
            prop.put("order", plasmaSearchPreOrder.canUseYBR() ? "YBR-Date-Quality" : "Date-Quality-YBR");
            prop.put("resource", "global");
            prop.put("time", 6);
            prop.put("urlmaskfilter", ".*");
            prop.put("prefermaskfilter", "");
            prop.put("cat", "href");
            prop.put("depth", "0");
            prop.put("type", 0);
            prop.put("type_excluded", 0);
            prop.put("type_num-results", 0);
            prop.put("type_combine", 0);
            prop.put("type_resultbottomline", 0);
            prop.put("type_results", "");
            prop.put("display", display);
            return prop;
        }

        // SEARCH
        // process search words
        int maxDistance = Integer.MAX_VALUE;
        String querystring = post.get("search", "").trim();
        if ((querystring.length() > 2) && (querystring.charAt(0) == '"') && (querystring.charAt(querystring.length() - 1) == '"')) {
            querystring = querystring.substring(1, querystring.length() - 1).trim();
            maxDistance = 1;
        }
        if (sb.facilityDB != null) try { sb.facilityDB.update("zeitgeist", querystring, post); } catch (Exception e) {}
        
        final int count = Integer.parseInt(post.get("count", "10"));
        final String order = post.get("order", plasmaSearchPreOrder.canUseYBR() ? "YBR-Date-Quality" : "Date-Quality-YBR");
        boolean global = (post == null) ? true : post.get("resource", "global").equals("global");
        final boolean indexDistributeGranted = sb.getConfig("allowDistributeIndex", "true").equals("true");
        final boolean indexReceiveGranted = sb.getConfig("allowReceiveIndex", "true").equals("true");
        if (!indexDistributeGranted || !indexReceiveGranted) { global = false; }
        final long searchtime = 1000 * Long.parseLong(post.get("time", "10"));
        String urlmask = "";
        if (post.containsKey("urlmask") && post.get("urlmask").equals("no")) {
            urlmask = ".*";
        } else {
            urlmask = (post.containsKey("urlmaskfilter")) ? (String) post.get("urlmaskfilter") : ".*";
        }
        String prefermask = post.get("prefermaskfilter", "");
        if ((prefermask.length() > 0) && (prefermask.indexOf(".*") < 0)) prefermask = ".*" + prefermask + ".*";

        serverObjects prop = new serverObjects();
        
        if (post.get("cat", "href").equals("href")) {
            
            final TreeSet query = plasmaSearchQuery.cleanQuery(querystring);
            // filter out stopwords
            final TreeSet filtered = kelondroMSetTools.joinConstructive(query,
                    plasmaSwitchboard.stopwords);
            if (filtered.size() > 0) {
                kelondroMSetTools.excludeDestructive(query, plasmaSwitchboard.stopwords);
            }

            // if a minus-button was hit, remove a special reference first
            if (post.containsKey("deleteref")) {
                if (!sb.verifyAuthentication(header, true)) {
                    prop.put("AUTHENTICATE", "admin log-in"); // force log-in
                    return prop;
                }
                final String delHash = post.get("deleteref", "");
                sb.removeReferences(delHash, query);
            }

            // prepare search order
            
            final boolean yacyonline = ((yacyCore.seedDB != null) && (yacyCore.seedDB.mySeed != null) && (yacyCore.seedDB.mySeed.getAddress() != null));

            String order1 = plasmaSearchRankingProfile.ORDER_DATE;
            String order2 = plasmaSearchRankingProfile.ORDER_YBR;
            String order3 = plasmaSearchRankingProfile.ORDER_QUALITY;
            if (order.startsWith("YBR")) order1 = plasmaSearchRankingProfile.ORDER_YBR;
            if (order.startsWith("Date")) order1 = plasmaSearchRankingProfile.ORDER_DATE;
            if (order.startsWith("Quality")) order1 = plasmaSearchRankingProfile.ORDER_QUALITY;
            if (order.indexOf("-YBR-") > 0) order2 = plasmaSearchRankingProfile.ORDER_YBR;
            if (order.indexOf("-Date-") > 0) order2 = plasmaSearchRankingProfile.ORDER_DATE;
            if (order.indexOf("-Quality-") > 0) order2 = plasmaSearchRankingProfile.ORDER_QUALITY;
            if (order.endsWith("YBR")) order3 = plasmaSearchRankingProfile.ORDER_YBR;
            if (order.endsWith("Date")) order3 = plasmaSearchRankingProfile.ORDER_DATE;
            if (order.endsWith("Quality")) order3 = plasmaSearchRankingProfile.ORDER_QUALITY;
            
            // do the search
            plasmaSearchQuery thisSearch = new plasmaSearchQuery(
                    query,
                    maxDistance,
                    prefermask,
                    count,
                    searchtime,
                    urlmask,
                    ((global) && (yacyonline) && (!(env.getConfig(
                            "last-search", "").equals(querystring)))) ? plasmaSearchQuery.SEARCHDOM_GLOBALDHT
                            : plasmaSearchQuery.SEARCHDOM_LOCAL, "", 20);
            plasmaSearchRankingProfile ranking = new plasmaSearchRankingProfile( new String[] { order1, order2, order3 });
            plasmaSearchTimingProfile localTiming = new plasmaSearchTimingProfile(4 * thisSearch.maximumTime / 10, thisSearch.wantedResults);
            plasmaSearchTimingProfile remoteTiming = new plasmaSearchTimingProfile(6 * thisSearch.maximumTime / 10, thisSearch.wantedResults);
            prop = sb.searchFromLocal(thisSearch, ranking, localTiming, remoteTiming);

            /*
             * final serverObjects prop = sb.searchFromLocal(query, order1,
             * order2, count, ((global) && (yacyonline) &&
             * (!(env.getConfig("last-search","").equals(querystring)))),
             * searchtime, urlmask);
             */
            // remember the last search expression
            env.setConfig("last-search", querystring);

            // process result of search
            prop.put("type_resultbottomline", 0);
            if (filtered.size() > 0) {
                prop.put("type_excluded", 1);
                prop.put("type_excluded_stopwords", filtered.toString());
            } else {
                prop.put("type_excluded", 0);
            }

            if (prop == null || prop.size() == 0) {
                if (post.get("search", "").length() < 3) {
                    prop.put("type_num-results", 2); // no results - at least 3 chars
                } else {
                    prop.put("type_num-results", 1); // no results
                }
            } else {
                final int linkcount = Integer.parseInt(prop.get("type_linkcount", "0"));
                final int orderedcount = Integer.parseInt(prop.get("type_orderedcount", "0"));
                final int totalcount = Integer.parseInt(prop.get("type_totalcount", "0"));
                if (totalcount > 10) {
                    final Object[] references = (Object[]) prop.get( "type_references", new String[0]);
                    prop.put("type_num-results", 4);
                    prop.put("type_num-results_linkcount", linkcount);
                    prop.put("type_num-results_orderedcount", orderedcount);
                    prop.put("type_num-results_totalcount", totalcount);
                    int hintcount = references.length;
                    if (hintcount > 0) {

                        prop.put("type_combine", 1);

                        // get the topwords
                        final TreeSet topwords = new TreeSet(kelondroNaturalOrder.naturalOrder);
                        String tmp = "";
                        for (int i = 0; i < hintcount; i++) {
                            tmp = (String) references[i];
                            if (!tmp.matches("[0-9]+")) {
                                topwords.add(tmp);
                            } // omit in the production ?
                        }

                        // filter out the badwords
                        final TreeSet filteredtopwords = kelondroMSetTools.joinConstructive(topwords, plasmaSwitchboard.badwords);
                        if (filteredtopwords.size() > 0) {
                            kelondroMSetTools.excludeDestructive(topwords, plasmaSwitchboard.badwords);
                        }

                        String word;
                        hintcount = 0;
                        final Iterator iter = topwords.iterator();
                        while (iter.hasNext()) {
                            word = (String) iter.next();
                            if (word != null) {
                                prop.put("type_combine_words_" + hintcount + "_word", word);
                                prop.put("type_combine_words_" + hintcount + "_newsearch", post.get("search", "").replace(' ', '+') + "+" + word);
                                prop.put("type_combine_words_" + hintcount + "_count", count);
                                prop.put("type_combine_words_" + hintcount + "_order", order);
                                prop.put("type_combine_words_" + hintcount + "_resource", ((global) ? "global" : "local"));
                                prop.put("type_combine_words_" + hintcount + "_time", (searchtime / 1000));
                            }
                            prop.put("type_combine_words", hintcount);
                            if (hintcount++ > MAX_TOPWORDS) {
                                break;
                            }
                        }
                    }
                } else {
                    if (totalcount == 0) {
                        prop.put("type_num-results", 3); // long
                    } else {
                        prop.put("type_num-results", 4);
                        prop.put("type_num-results_linkcount", linkcount);
                        prop.put("type_num-results_orderedcount", orderedcount);
                        prop.put("type_num-results_totalcount", totalcount);
                    }
                }
            }

            if (yacyonline) {
                if (global) {
                    prop.put("type_resultbottomline", 1);
                    prop.put("type_resultbottomline_globalresults", prop.get("type_globalresults", "0"));
                } else {
                    prop.put("type_resultbottomline", 2);
                }
            } else {
                if (global) {
                    prop.put("type_resultbottomline", 3);
                } else {
                    prop.put("type_resultbottomline", 4);
                }
            }

            prop.put("type", "0");
            prop.put("cat", "href");
            prop.put("depth", "0");

            // adding some additional properties needed for the rss feed
            String hostName = (String) header.get("Host", "localhost");
            if (hostName.indexOf(":") == -1) hostName += ":" + serverCore.getPortNr(env.getConfig("port", "8080"));
            prop.put("rssYacyImageURL", "http://" + hostName + "/env/grafics/yacy.gif");

        }
        
        if (post.get("cat", "href").equals("image")) {
            
            int depth = post.getInt("depth", 0);
            int columns = post.getInt("columns", 6);
            URL url = null;
            try {url = new URL(post.get("url", ""));} catch (MalformedURLException e) {}
            plasmaSearchImages si = new plasmaSearchImages(sb.snippetCache, 6000, url, depth);
            Iterator i = si.entries();
            htmlFilterImageEntry ie;
            int line = 0;
            while (i.hasNext()) {
                int col = 0;
                for (col = 0; col < columns; col++) {
                    if (!i.hasNext()) break;
                    ie = (htmlFilterImageEntry) i.next();
                    String urls = ie.url().toString();
                    String name = "";
                    int p = urls.lastIndexOf('/');
                    if (p > 0) name = urls.substring(p + 1);
                    prop.put("type_results_" + line + "_line_" + col + "_url", urls);
                    prop.put("type_results_" + line + "_line_" + col + "_name", name);
                }
                prop.put("type_results_" + line + "_line", col);
                line++;
            }
            prop.put("type_results", line);
            
            prop.put("type", 1); // set type of result: image list
            prop.put("cat", "href");
            prop.put("depth", depth);
        }
        
        prop.put("promoteSearchPageGreeting", promoteSearchPageGreeting);
        prop.put("former", post.get("search", ""));
        prop.put("count", count);
        prop.put("order", order);
        prop.put("resource", (global) ? "global" : "local");
        prop.put("time", searchtime / 1000);
        prop.put("urlmaskfilter", urlmask);
        prop.put("prefermaskfilter", prefermask);
        prop.put("display", display);
        
        // return rewrite properties
        return prop;
    }

}
