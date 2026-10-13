package org.jobits.ottos.apps;

import org.jobits.ottos.apps.domain.AppRelease;
import org.jobits.ottos.apps.domain.AppReleaseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of {@link AppVersionService}. Releases are rows that a data script inserts now and then
 * (scripts/data/app-releases), so they are kept in memory and reread at most once a minute: the minimum build is
 * checked on every mobile request without touching the database each time, and a new release shows within a minute
 * without a restart. {@link #reload()} rereads them at once (tests insert releases directly).
 */
@Service
public class AppVersionServiceImpl implements AppVersionService {

    private static final Duration REFRESH = Duration.ofMinutes(1);

    private final AppReleaseRepository releases;
    private final Map<Platform, String> downloadUrls = new EnumMap<>(Platform.class);
    private final Clock clock;
    private volatile Map<Platform, AppVersionView> current;
    private volatile Instant loadedAt = Instant.MIN;

    AppVersionServiceImpl(AppReleaseRepository releases, Clock clock,
                          @Value("${ottos.apps.android-download-url:}") String androidDownloadUrl,
                          @Value("${ottos.apps.ios-download-url:}") String iosDownloadUrl) {
        this.releases = releases;
        this.clock = clock;
        downloadUrls.put(Platform.ANDROID, androidDownloadUrl);
        downloadUrls.put(Platform.IOS, iosDownloadUrl);
    }

    @Override
    public AppVersionView current(Platform platform) {
        return views().get(platform);
    }

    @Override
    public int minimumBuild(Platform platform) {
        return current(platform).minimumBuild();
    }

    /** Rereads the releases from the database. */
    public void reload() {
        current = null;
    }

    private Map<Platform, AppVersionView> views() {
        Map<Platform, AppVersionView> views = current;
        Instant now = clock.instant();
        if (views == null || loadedAt.isBefore(now.minus(REFRESH))) {
            List<AppRelease> all = releases.findAll();
            views = new EnumMap<>(Platform.class);
            for (Platform platform : Platform.values()) {
                views.put(platform, view(platform, all));
            }
            current = views;
            loadedAt = now;
        }
        return views;
    }

    private AppVersionView view(Platform platform, List<AppRelease> all) {
        List<AppRelease> own = all.stream().filter(r -> r.getPlatform() == platform).toList();
        Optional<AppRelease> latest = own.stream().max(Comparator.comparingInt(AppRelease::getBuild));
        int minimum = own.stream().filter(AppRelease::isMandatory).mapToInt(AppRelease::getBuild).max().orElse(0);
        String version = latest.map(AppRelease::getVersionName).orElse(null);
        return new AppVersionView(platform, latest.map(AppRelease::getBuild).orElse(0), version, minimum,
                downloadUrl(platform, version), latest.map(AppRelease::getNotes).orElse(null));
    }

    /** The configured base, with {version} replaced by the version name (the TestFlight link has none). */
    private String downloadUrl(Platform platform, String version) {
        String base = downloadUrls.get(platform);
        if (base == null || base.isBlank()) {
            return null;
        }
        return version == null ? base.replace("{version}", "") : base.replace("{version}", version);
    }
}
