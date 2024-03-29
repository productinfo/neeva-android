// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.contentfilter

import com.neeva.app.R

/*
 * This list is copied from:
 * https://github.com/neevaco/neeva-ios/blob/main/Client/Frontend/Overlay/Content/Menu/TrackingProtection/TrackingEntity.swift
 */
enum class TrackingEntity(val description: String, val imageId: Int) {
    GOOGLE("Google", R.drawable.google_image),
    FACEBOOK("Facebook", R.drawable.facebook),
    TWITTER("Twitter", R.drawable.twitter),
    AMAZON("Amazon", R.drawable.amazon),
    OUTBRAIN("Outbrain", R.drawable.outbrain),
    CRITEO("Criteo", R.drawable.criteo),
    ADOBE("Adobe", R.drawable.adobe),
    ORACLE("Oracle", R.drawable.oracle),
    WARNERMEDIA("WarnerMedia", R.drawable.warnermedia),
    IAS("IAS", R.drawable.ias),
    PINTEREST("Pinterest", R.drawable.pinterest),
    VERIZONMEDIA("VerizonMedia", R.drawable.verizonmedia);

    companion object {
        fun trackingEntityForHost(host: String?): TrackingEntity? {
            TrackingEntity.values().forEach {
                if (trackingEntityMap[it]?.contains(host) == true) {
                    return it
                }
            }
            return null
        }
    }
}

val trackingEntityMap = mapOf(
    TrackingEntity.GOOGLE to setOf(
        "1emn.com",
        "2mdn.net",
        "admeld.com",
        "admob.com",
        "app-measurement.com",
        "apture.com",
        "asp-cc.com",
        "blogger.com",
        "cc-dt.com",
        "crashlytics.com",
        "dartsearch.net",
        "dmtry.com",
        "doubleclick.com",
        "doubleclick.net",
        "firebaseio.com",
        "gmodules.com",
        "google-analytics.com",
        "googleadservices.com",
        "googleadsserving.cn",
        "googlegroups.com",
        "googlesyndication.com",
        "googletagmanager.com",
        "googletagservices.com",
        "googleusercontent.com",
        "gstatic.com",
        "invitemedia.com",
        "page.link",
        "urchin.com",
        "waze.com",
        "youtube.com"
    ),
    TrackingEntity.FACEBOOK to setOf(
        "accountkit.com",
        "atdmt.com",
        "atlassbx.com",
        "atlassolutions.com",
        "facebook.com",
        "fbsbx.com",
        "liverail.com",
        "whatsapp.net",
    ),
    TrackingEntity.TWITTER to setOf(
        "ads-twitter.com",
        "mopub.com",
        "twitter.com",
        "twttr.com",
    ),
    TrackingEntity.AMAZON to setOf(
        "alexa.com",
        "alexametrics.com",
        "amazon-adsystem.com",
        "assoc-amazon.com",
        "assoc-amazon.jp",
        "graphiq.com",
        "media-imdb.com",
        "peer39.com",
        "peer39.net",
        "serving-sys.com",
        "sizmek.com",
        "twitch.tv",
        "wfm.com",
    ),
    TrackingEntity.OUTBRAIN to setOf(
        "ligatus.com",
        "outbrain.com",
        "veeseo.com",
        "zemanta.com",
    ),
    TrackingEntity.CRITEO to setOf(
        "criteo.com",
        "criteo.net",
        "emailretargeting.com",
        "hlserve.com",
        "manage.com",
    ),
    TrackingEntity.ADOBE to setOf(
        "2o7.net",
        "adobe.com",
        "adobetag.com",
        "auditude.com",
        "bizible.com",
        "businesscatalyst.com",
        "demdex.net",
        "everestads.net",
        "everestjs.net",
        "everesttech.net",
        "fyre.co",
        "hitbox.com",
        "livefyre.com",
        "marketo.com",
        "marketo.net",
        "mktoresp.com",
        "nedstat.net",
        "omniture.com",
        "omtrdc.net",
        "sitestat.com",
        "tubemogul.com",
    ),
    TrackingEntity.ORACLE to setOf(
        "sekindo.com",
        "addthis.com",
        "addthiscdn.com",
        "addthisedge.com",
        "atgsvcs.com",
        "bkrtx.com",
        "bluekai.com",
        "bm23.com",
        "compendium.com",
        "en25.com",
        "grapeshot.co.uk",
        "maxymiser.net",
        "moat.com",
        "moatads.com",
        "moatpixel.com",
        "nexac.com",
        "responsys.net",
    ),
    TrackingEntity.WARNERMEDIA to setOf(
        "247realmedia.com",
        "adnxs.com",
        "adultswim.com",
        "cartoonnetwork.com",
        "cnn.com",
        "ncaa.com",
        "realmedia.com",
        "tbs.com",
        "tmz.com",
        "trutv.com",
        "turner.com",
        "ugdturner.com",
        "warnerbros.com",
        "yieldoptimizer.com",
    ),
    TrackingEntity.IAS to setOf(
        "adsafeprotected.com",
        "iasds01.com",
    ),
    TrackingEntity.PINTEREST to setOf(
        "pinterest.com"
    ),
    TrackingEntity.VERIZONMEDIA to setOf(
        "adap.tv",
        "adsonar.com",
        "adtech.de",
        "adtechjp.com",
        "adtechus.com",
        "advertising.com",
        "aol.co.uk",
        "aol.com",
        "aol.fr",
        "aolp.jp",
        "atwola.com",
        "bluelithium.com",
        "brightroll.com",
        "btrll.com",
        "convertro.com",
        "engadget.com",
        "flurry.com",
        "hostingprod.com",
        "lexity.com",
        "mybloglog.com",
        "nexage.com",
        "overture.com",
        "pictela.net",
        "pulsemgr.com",
        "rmxads.com",
        "vidible.tv",
        "wretch.cc",
        "yahoo.com",
        "yahoo.net",
        "yahoodns.net",
        "yieldmanager.com",
        "yieldmanager.net",
        "yimg.com",
    )
)
